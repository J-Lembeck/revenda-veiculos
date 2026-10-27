package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.ports.in.CheckCpfCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Schema(description = "O CPF apresentado na retirada, para conferir com o do cadastro.")
record CpfCheckRequest(
        @Schema(description = "Só com números ou com pontos e traço", example = "529.982.247-25")
        @NotBlank String cpf) {

    CheckCpfCommand toCommand(UUID customerId, UUID purchaseId) {
        return new CheckCpfCommand(customerId, purchaseId, cpf.strip());
    }

    @Override
    public String toString() {
        return "CpfCheckRequest[dados pessoais omitidos]";
    }
}
