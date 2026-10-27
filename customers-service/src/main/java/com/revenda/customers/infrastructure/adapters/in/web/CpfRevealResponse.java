package com.revenda.customers.infrastructure.adapters.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "O CPF completo, só com os dígitos.")
record CpfRevealResponse(@Schema(example = "52998224725") String cpf) {

    @Override
    public String toString() {
        return "CpfRevealResponse[dados pessoais omitidos]";
    }
}
