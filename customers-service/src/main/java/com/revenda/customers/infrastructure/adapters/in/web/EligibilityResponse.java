package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.Eligibility;
import com.revenda.customers.domain.model.IneligibilityReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Se a conta pode comprar (RN-05). Nada pessoal: só o id do cadastro e o motivo.")
record EligibilityResponse(
        @Schema(description = "Id do cadastro; nulo quando a conta não tem cadastro", nullable = true,
                example = "3f2b8c4e-1a7d-4e9b-8c2f-6d5a4b3c2e10")
        UUID customerId,
        @Schema(example = "true") boolean eligible,
        @Schema(description = "Por que não pode; nulo quando pode", nullable = true, example = "UNDERAGE")
        IneligibilityReason reason) {

    static EligibilityResponse of(Eligibility eligibility) {
        return new EligibilityResponse(eligibility.customerId(), eligibility.eligible(), eligibility.reason());
    }
}
