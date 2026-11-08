package com.revenda.sales.infrastructure.adapters.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

record RetentionResponse(
        @Schema(description = "Até quando os dados do cliente precisam ficar guardados; nulo se nada pede guardar",
                nullable = true)
        Instant retainUntil) {
}
