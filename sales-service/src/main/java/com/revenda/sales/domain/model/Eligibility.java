package com.revenda.sales.domain.model;

import com.revenda.common.error.BusinessException;
import java.util.UUID;

/**
 * Se a conta pode comprar (RN-05), como o cadastro respondeu: o id do cadastro e, se não pode, o motivo. Nada
 * pessoal.
 */
public record Eligibility(UUID customerId, boolean eligible, Reason reason) {

    public enum Reason {
        PROFILE_MISSING,
        UNDERAGE
    }

    /** O id do cadastro de quem pode comprar; quem não pode recebe o código da API pelo motivo. */
    public UUID requireEligible() {
        if (eligible && customerId != null) {
            return customerId;
        }
        throw BusinessException.of(reason == Reason.UNDERAGE
                ? SalesError.CUSTOMER_UNDERAGE
                : SalesError.CUSTOMER_PROFILE_REQUIRED);
    }
}
