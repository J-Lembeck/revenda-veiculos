package com.revenda.customers.infrastructure.adapters.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Se o CPF apresentado é o do cadastro. O CPF do cadastro nunca sai.")
record CpfCheckResponse(@Schema(example = "true") boolean matches) {
}
