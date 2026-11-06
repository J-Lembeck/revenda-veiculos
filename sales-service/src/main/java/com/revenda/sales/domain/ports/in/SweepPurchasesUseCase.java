package com.revenda.sales.domain.ports.in;

/**
 * UC-10: a varredura dos prazos da saga, uma compra por transação, pulando a linha que outro já travou. Cada passada
 * devolve quantas compras mudou.
 */
public interface SweepPurchasesUseCase {

    /** Vencido o prazo sem pagamento (RN-11): a compra expira, a cobrança é cancelada e o veículo liberado. */
    int expireOverdue();

    /** Parada no meio do caminho síncrono além do limite: a compra falha, e a reserva e a cobrança são desfeitas. */
    int recoverStalled();
}
