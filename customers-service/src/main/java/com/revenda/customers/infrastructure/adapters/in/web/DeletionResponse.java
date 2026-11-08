package com.revenda.customers.infrastructure.adapters.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "O cadastro foi anonimizado: o dado pessoal e o vínculo com a conta foram apagados.")
record DeletionResponse(@Schema(example = "ANONYMIZED") String status, Instant anonymizedAt) {

    static DeletionResponse anonymizedAt(Instant at) {
        return new DeletionResponse("ANONYMIZED", at);
    }
}
