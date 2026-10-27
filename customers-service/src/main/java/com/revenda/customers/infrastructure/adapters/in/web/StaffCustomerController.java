package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.ports.in.RevealCpfUseCase;
import com.revenda.customers.domain.ports.in.ViewCustomerUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Clientes pela equipe", description = "O cliente visto pela equipe: mascarado por padrão; o CPF completo só "
        + "para o administrador, com motivo e auditado.")
@RestController
@RequestMapping("/customers/{id}")
@RequiredArgsConstructor
class StaffCustomerController {

    private final ViewCustomerUseCase viewCustomer;
    private final RevealCpfUseCase revealCpf;

    @Operation(summary = "Ver o cliente", description = "UC-14. O mínimo para atender: nome, CPF, e-mail e telefone "
            + "mascarados, cidade e UF; sem nascimento nem o resto do endereço. Leitura sem auditoria, porque nada sai "
            + "completo — o CPF completo é a revelação.")
    @ApiResponse(responseCode = "200", description = "O cliente, mascarado.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `id` `TYPE_INVALID` — o id não é um UUID.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não há cliente ativo com esse id.")
    @GetMapping
    StaffCustomerResponse view(@Parameter(description = "Id do cliente") @PathVariable UUID id) {
        return StaffCustomerResponse.of(viewCustomer.view(id));
    }

    @Operation(summary = "Revelar o CPF completo", description = "UC-14, regra do CPF mascarado por padrão. Só o "
            + "administrador, com o motivo escrito; cada revelação grava a auditoria `CUSTOMER_CPF_REVEALED` com quem "
            + "pediu e o motivo, e conta na métrica `customers.cpf.reveals`, a do alarme de volume. A resposta não vai "
            + "para cache. Não cria nada: sem `Idempotency-Key`.")
    @ApiResponse(responseCode = "200", description = "O CPF completo.",
            headers = @Header(name = "Cache-Control", description = "`no-store`"))
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED`, com `reason` `REQUIRED` (sem motivo) ou "
            + "`SIZE_INVALID` (fora de 10 a 300 caracteres), ou `id` `TYPE_INVALID`. Nada é revelado nem auditado.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não há cliente ativo com esse id; nada é auditado.")
    @PostMapping("/cpf-reveal")
    ResponseEntity<CpfRevealResponse> revealCpf(@Parameter(description = "Id do cliente") @PathVariable UUID id,
            @Valid @RequestBody CpfRevealRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CpfRevealResponse(revealCpf.reveal(request.toCommand(id)).digits()));
    }
}
