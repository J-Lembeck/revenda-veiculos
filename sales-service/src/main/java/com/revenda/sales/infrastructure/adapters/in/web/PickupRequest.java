package com.revenda.sales.infrastructure.adapters.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "O CPF do documento que a pessoa apresentou na loja, para conferir com o do comprador.")
record PickupRequest(
        @Schema(description = "Só com números ou com pontos e traço", example = "529.982.247-25")
        @NotBlank String cpf) {

    String presentedCpf() {
        return cpf.strip();
    }

    @Override
    public String toString() {
        return "PickupRequest[dados pessoais omitidos]";
    }
}
