package com.revenda.payments.domain.model;

import com.revenda.common.error.ErrorCode;
import com.revenda.common.error.ErrorKind;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GatewayError implements ErrorCode {
    CHARGE_NOT_PAYABLE(ErrorKind.CONFLICT, "Esta cobrança já foi paga ou estornada e não se paga de novo."),
    CHARGE_REFERENCE_CONFLICT(ErrorKind.CONFLICT,
            "Já existe cobrança para esta referência com outro valor. Use a mesma referência só para repetir o pedido.");

    private final ErrorKind kind;
    private final String message;
}
