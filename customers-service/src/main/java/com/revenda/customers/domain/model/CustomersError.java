package com.revenda.customers.domain.model;

import com.revenda.common.error.ErrorCode;
import com.revenda.common.error.ErrorKind;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CustomersError implements ErrorCode {
    CUSTOMER_ALREADY_REGISTERED(ErrorKind.CONFLICT,
            "Esta conta já tem cadastro de comprador. Para mudar seus dados, edite o cadastro existente."),
    CPF_ALREADY_REGISTERED(ErrorKind.CONFLICT,
            "Este CPF já tem cadastro em outra conta. Entre com a conta do cadastro ou fale com a revenda."),
    EMAIL_NOT_VERIFIED(ErrorKind.UNPROCESSABLE,
            "Confirme o e-mail da sua conta antes de se cadastrar: ele vai no cadastro e no documento da compra."),
    CUSTOMER_HAS_RETAINED_DATA(ErrorKind.CONFLICT,
            "Há venda no prazo em que a lei pede guardar os dados: o pedido de exclusão ficou registrado e o cadastro "
                    + "é anonimizado no fim desse prazo.");

    private final ErrorKind kind;
    private final String message;
}
