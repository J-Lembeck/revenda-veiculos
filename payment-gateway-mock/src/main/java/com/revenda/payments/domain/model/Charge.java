package com.revenda.payments.domain.model;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import java.time.Instant;

/**
 * Uma cobrança Pix de uma compra ({@code referenceId}). Só a transição válida muda o estado: cancelar uma pendente,
 * estornar uma paga, pagar uma pendente ou cancelada (o pagamento atrasado). Cancelar ou estornar fora disso não muda
 * nada, para a compensação repetida nunca falhar; pagar de novo a paga ou a estornada é recusado.
 */
public record Charge(
        String id,
        String referenceId,
        Money amount,
        Instant dueAt,
        Payer payer,
        String pixCode,
        ChargeStatus status,
        Money paidAmount,
        Instant createdAt,
        Instant updatedAt) {

    /** A cobrança nova, pendente, com o código Pix; o vencimento é depois de {@code now}. */
    public static Charge open(String id, String referenceId, Money amount, Instant dueAt, Payer payer, Instant now) {
        if (!dueAt.isAfter(now)) {
            throw BusinessException.invalidField("dueAt", "DATE_INVALID", "O vencimento é depois de agora.");
        }
        return new Charge(id, referenceId, amount, dueAt, payer, PixCode.of(id, amount), ChargeStatus.PENDING, null,
                now, now);
    }

    /** Se o pedido de outra cobrança com a mesma referência é a repetição deste: o mesmo valor. */
    public boolean repeats(Charge other) {
        return amount.equals(other.amount);
    }

    public Charge cancel(Instant now) {
        return status == ChargeStatus.PENDING ? with(ChargeStatus.CANCELLED, paidAmount, now) : this;
    }

    public Charge refund(Instant now) {
        return status == ChargeStatus.PAID ? with(ChargeStatus.REFUNDED, paidAmount, now) : this;
    }

    public Charge pay(Money paid, Instant now) {
        if (status == ChargeStatus.PAID || status == ChargeStatus.REFUNDED) {
            throw BusinessException.of(GatewayError.CHARGE_NOT_PAYABLE);
        }
        return with(ChargeStatus.PAID, paid, now);
    }

    private Charge with(ChargeStatus newStatus, Money newPaidAmount, Instant now) {
        return new Charge(id, referenceId, amount, dueAt, payer, pixCode, newStatus, newPaidAmount, createdAt, now);
    }

    @Override
    public String toString() {
        return "Charge[id=" + id + ", referenceId=" + referenceId + ", status=" + status + "]";
    }
}
