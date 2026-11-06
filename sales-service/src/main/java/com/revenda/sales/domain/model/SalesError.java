package com.revenda.sales.domain.model;

import com.revenda.common.error.ErrorCode;
import com.revenda.common.error.ErrorKind;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SalesError implements ErrorCode {
    PURCHASE_IN_PROGRESS(ErrorKind.CONFLICT,
            "A compra ainda está reservando o veículo ou emitindo a cobrança. Tente de novo em instantes."),
    PURCHASE_NOT_CANCELLABLE(ErrorKind.CONFLICT,
            "Esta compra já foi encerrada ou retirada e não pode mais ser cancelada."),
    PURCHASE_NOT_PAID(ErrorKind.CONFLICT,
            "A retirada só se confirma em compra paga e ainda não retirada."),
    PAYMENT_EVENT_NOT_YET_APPLICABLE(ErrorKind.CONFLICT,
            "A compra ainda não chegou ao ponto deste evento de pagamento; o gateway entrega de novo depois."),
    PAYMENT_EVENT_CONTRADICTORY(ErrorKind.CONFLICT,
            "O evento de pagamento contradiz a compra e não foi aplicado.");

    private final ErrorKind kind;
    private final String message;
}
