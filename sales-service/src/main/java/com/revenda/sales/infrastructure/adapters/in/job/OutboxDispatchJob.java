package com.revenda.sales.infrastructure.adapters.in.job;

import com.revenda.common.outbox.OutboxDispatcher;
import com.revenda.common.outbox.OutboxDispatcher.DispatchRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** O despachante do outbox, a cada {@code OUTBOX_POLL_INTERVAL}: manda os comandos para a fila e para o gateway. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "revenda.sales.jobs-enabled", havingValue = "true")
class OutboxDispatchJob {

    private final OutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${revenda.outbox.poll-interval}")
    void dispatch() {
        DispatchRun run = dispatcher.dispatchDue();
        if (run.sent() + run.retried() + run.failed() > 0) {
            log.info("Outbox: {} enviadas, {} para tentar de novo, {} FAILED", run.sent(), run.retried(),
                    run.failed());
        }
    }
}
