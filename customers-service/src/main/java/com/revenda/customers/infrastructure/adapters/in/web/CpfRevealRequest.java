package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.ports.in.RevealCpfCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Por que o CPF completo é preciso. Vai para a auditoria, com quem pediu.")
record CpfRevealRequest(
        @Schema(description = "De 10 a 300 caracteres; não escreva dado pessoal aqui",
                example = "Conferência do documento do comprador na retirada")
        @NotBlank @Size(min = RevealCpfCommand.REASON_MIN, max = RevealCpfCommand.REASON_MAX,
                message = "Escreva o motivo com 10 a 300 caracteres.")
        String reason) {

    RevealCpfCommand toCommand(UUID customerId) {
        return new RevealCpfCommand(customerId, reason.strip());
    }
}
