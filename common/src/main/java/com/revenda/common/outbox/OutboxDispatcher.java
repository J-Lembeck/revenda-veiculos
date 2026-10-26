package com.revenda.common.outbox;

import com.revenda.common.error.BusinessException;
import com.revenda.common.outbox.OutboxQueue.Claimed;
import com.revenda.common.outbox.OutboxQueue.Expired;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Uma passada do despachante, chamada pelo job do serviço. Reivindica as mensagens devidas numa transação
 * curta ({@code FOR UPDATE SKIP LOCKED}, com prazo em {@code locked_until}), envia cada uma sem transação
 * aberta e baixa em outra transação curta. Envio que falha volta para a fila com espera crescente; depois de
 * {@code revenda.outbox.max-attempts} tentativas, fica {@code FAILED} e segura o grupo dela.
 */
@Slf4j
public class OutboxDispatcher {

    public static final String NO_SENDER = "NO_SENDER";
    public static final Duration LEASE = Duration.ofMinutes(1);
    public static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);
    public static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    static final int BATCH_SIZE = 50;
    static final int MAX_BATCHES_PER_RUN = 10;
    private static final int ERROR_LIMIT = 500;

    private final OutboxQueue queue;
    private final Map<String, OutboxSender> senders = new HashMap<>();
    private final TransactionTemplate transaction;
    private final int maxAttempts;
    private final Clock clock;

    OutboxDispatcher(OutboxQueue queue, List<OutboxSender> senders, TransactionTemplate transaction,
            int maxAttempts, Clock clock) {
        this.queue = queue;
        this.transaction = transaction;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
        for (OutboxSender sender : senders) {
            OutboxSender previous = this.senders.put(sender.destination().name(), sender);
            if (previous != null) {
                throw new IllegalStateException("Dois envios para o destino " + sender.destination().name() + ": "
                        + previous.getClass().getName() + " e " + sender.getClass().getName());
            }
        }
    }

    public static Duration backoff(int attempts) {
        Duration wait = FIRST_BACKOFF.multipliedBy(1L << Math.min(attempts, 20));
        return wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait;
    }

    public DispatchRun dispatchDue() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "O outbox é despachado sem transação aberta: o envio não segura transação nem lock");
        }
        DispatchRun total = releaseExpired();
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            Instant now = now();
            List<Claimed> claimed = transaction.execute(status -> queue.claim(now, now.plus(LEASE), BATCH_SIZE));
            for (Claimed message : claimed) {
                total = total.plus(send(message));
            }
            if (claimed.size() < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    private DispatchRun releaseExpired() {
        List<Expired> expired = transaction.execute(status -> queue.releaseExpired(now(), maxAttempts));
        DispatchRun run = DispatchRun.empty();
        for (Expired message : expired) {
            if (message.failed()) {
                log.error("Mensagem {} ({} para {}) teve o prazo de envio vencido na tentativa {} e ficou FAILED",
                        message.id(), message.type(), message.destination(), message.attempts());
                run = run.plus(Outcome.FAILED);
            }
        }
        return run;
    }

    private Outcome send(Claimed claimed) {
        OutboxSender sender = senders.get(claimed.message().destination());
        if (sender == null) {
            return recordFailure(claimed, NO_SENDER, null);
        }
        try {
            sender.send(claimed.message());
        } catch (RuntimeException failure) {
            return recordFailure(claimed, describe(failure), failure);
        }
        return marked(transaction.execute(status -> queue.markSent(claimed, now()))) ? Outcome.SENT : Outcome.LOST;
    }

    private Outcome recordFailure(Claimed claimed, String error, RuntimeException failure) {
        OutboxMessage message = claimed.message();
        int attempts = message.attempts() + 1;
        if (attempts >= maxAttempts) {
            if (!marked(transaction.execute(status -> queue.markFailed(claimed, attempts, error)))) {
                return Outcome.LOST;
            }
            log.error("Mensagem {} ({} para {}) falhou pela última vez e ficou FAILED: {}", message.id(),
                    message.type(), message.destination(), error, failure);
            return Outcome.FAILED;
        }
        Instant availableAt = now().plus(backoff(attempts));
        if (!marked(transaction.execute(status -> queue.reschedule(claimed, attempts, availableAt, error)))) {
            return Outcome.LOST;
        }
        log.warn("Mensagem {} ({} para {}) falhou na tentativa {} e volta para a fila: {}", message.id(),
                message.type(), message.destination(), attempts, error, failure);
        return Outcome.RETRIED;
    }

    private static boolean marked(Boolean result) {
        return Boolean.TRUE.equals(result);
    }

    private static String describe(RuntimeException failure) {
        String kind = failure.getClass().getSimpleName();
        String error = failure instanceof BusinessException business ? kind + ": " + business.getCode() : kind;
        return error.length() > ERROR_LIMIT ? error.substring(0, ERROR_LIMIT) : error;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private enum Outcome { SENT, RETRIED, FAILED, LOST }

    public record DispatchRun(int sent, int retried, int failed) {

        static DispatchRun empty() {
            return new DispatchRun(0, 0, 0);
        }

        DispatchRun plus(Outcome outcome) {
            return switch (outcome) {
                case SENT -> new DispatchRun(sent + 1, retried, failed);
                case RETRIED -> new DispatchRun(sent, retried + 1, failed);
                case FAILED -> new DispatchRun(sent, retried, failed + 1);
                case LOST -> this;
            };
        }
    }
}
