package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.sales.domain.ports.in.GetCustomerRetentionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Interno", description = "Rotas que só o serviço de cadastro chama, com token de serviço; não são "
        + "publicadas no API Gateway.")
@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
class InternalRetentionController {

    private final GetCustomerRetentionUseCase retention;

    @Operation(summary = "Até quando guardar os dados do cliente", description = "O cadastro pergunta antes de "
            + "anonimizar alguém (LGPD). Com venda concluída, os dados ficam `RETENTION_AFTER_SALE` (5 anos por padrão) "
            + "depois da mais recente; com compra aberta (`PENDING` a `PAID`), o mesmo prazo contado de agora, porque "
            + "ela ainda pode virar venda. Sem venda nem compra aberta — ou cliente que o serviço não conhece —, "
            + "`retainUntil` vem nulo. Nada pessoal na resposta, e a consulta não é auditada.")
    @ApiResponse(responseCode = "200", description = "O fim do prazo, ou nulo.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `id` `TYPE_INVALID`.")
    @GetMapping("/{id}/retention")
    RetentionResponse retention(@Parameter(description = "Id do cliente") @PathVariable UUID id) {
        return new RetentionResponse(retention.retainUntil(id).orElse(null));
    }
}
