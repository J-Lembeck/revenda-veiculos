package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.ExportOwnDataUseCase;
import com.revenda.customers.domain.ports.in.RegisterCustomerUseCase;
import com.revenda.customers.domain.ports.in.RequestDeletionUseCase;
import com.revenda.customers.domain.ports.in.UpdateOwnCustomerUseCase;
import com.revenda.customers.domain.ports.in.ViewOwnCustomerUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Clientes", description = "O cadastro do comprador, feito por ele mesmo.")
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
class CustomerController {

    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final RegisterCustomerUseCase registerCustomer;
    private final ViewOwnCustomerUseCase viewOwnCustomer;
    private final UpdateOwnCustomerUseCase updateOwnCustomer;
    private final ExportOwnDataUseCase exportOwnData;
    private final RequestDeletionUseCase requestDeletion;

    @Operation(summary = "Cadastrar-se como comprador", description = "Uma conta tem no máximo um cadastro e um "
            + "CPF, um cadastro ativo. O e-mail vem da conta, pelo token — o corpo não tem e-mail. CPF, e-mail, "
            + "telefone, nascimento e endereço são gravados cifrados; a resposta traz CPF, e-mail e telefone "
            + "mascarados. Grava a auditoria `CUSTOMER_REGISTERED`, sem dado pessoal.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true,
                    description = "Chave do pedido, de 16 a 80 letras, números, `-` ou `_`: gere um UUID novo para o "
                            + "cadastro. Repetir a chave com o mesmo corpo devolve a mesma resposta sem cadastrar de "
                            + "novo; com outro corpo, `422`.",
                    schema = @Schema(type = "string", pattern = "^[A-Za-z0-9_-]{16,80}$")))
    @ApiResponse(responseCode = "201", description = "Cadastrado.",
            headers = @Header(name = "Location", description = "`/customers/me`, onde o titular vê o cadastro"))
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED`, com cada campo em `fields`: `REQUIRED`, "
            + "`SIZE_INVALID`, `FORMAT_INVALID` (`phone`, `address.zipCode`, `address.state`), `TYPE_INVALID` "
            + "(`birthDate` que não é data), `CPF_INVALID` (dígito verificador) e `DATE_INVALID` (`birthDate` fora de "
            + "1900 até ontem). `Idempotency-Key` fora do formato vem em `fields` com `FORMAT_INVALID`.")
    @ApiResponse(responseCode = "409", description = "`CUSTOMER_ALREADY_REGISTERED` — esta conta já tem cadastro; "
            + "`CPF_ALREADY_REGISTERED` — o CPF já tem cadastro ativo em outra conta.")
    @ApiResponse(responseCode = "422", description = "`EMAIL_NOT_VERIFIED` — o token não tem e-mail ou, com "
            + "`CUSTOMERS_REQUIRE_VERIFIED_EMAIL` ligado, o e-mail não foi verificado pelo provedor; "
            + "`IDEMPOTENCY_KEY_REUSED` — a mesma chave com outro corpo.")
    @ApiResponse(responseCode = "428", description = "`IDEMPOTENCY_KEY_REQUIRED` — sem `Idempotency-Key`; nada é "
            + "cadastrado.")
    @PostMapping
    ResponseEntity<CustomerResponse> register(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody CustomerRequest request) {
        Customer customer = registerCustomer.register(request.toCommand(token.getSubject(),
                token.getClaimAsString(EMAIL_CLAIM), emailVerified(token)));
        return ResponseEntity.created(URI.create("/customers/me")).body(CustomerResponse.of(customer));
    }

    @Operation(summary = "Ver o próprio cadastro", description = "O cadastro da conta do token, no mesmo formato "
            + "da resposta do cadastro: CPF, e-mail e telefone mascarados, também para o titular; nome, nascimento e "
            + "endereço em claro. Cada conta só enxerga o próprio cadastro. Leitura sem auditoria: o titular vê o "
            + "próprio dado, com o CPF mascarado.")
    @ApiResponse(responseCode = "200", description = "O próprio cadastro.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — esta conta ainda não tem cadastro.")
    @GetMapping("/me")
    CustomerResponse view(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return CustomerResponse.of(viewOwnCustomer.view(token.getSubject()));
    }

    @Operation(summary = "Atualizar o próprio cadastro", description = "Muda nome, telefone e endereço: o "
            + "campo ausente ou nulo fica como está, e o endereço, se vier, vem inteiro, com as regras do cadastro. CPF, "
            + "nascimento e e-mail não mudam por aqui; o e-mail acompanha o da conta — se o token traz outro (e, com "
            + "`CUSTOMERS_REQUIRE_VERIFIED_EMAIL` ligado, verificado), a edição grava o novo. Só o que mudou é cifrado "
            + "de novo; a versão sobe e grava a auditoria `CUSTOMER_UPDATED` com os campos que mudaram, sem o valor "
            + "deles. Sem mudança nenhuma, devolve o cadastro sem gravar. Não cria nada: sem `Idempotency-Key`.")
    @ApiResponse(responseCode = "200", description = "O cadastro como ficou.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED`, com cada campo em `fields`: `REQUIRED` (campo "
            + "em branco, ou do endereço), `SIZE_INVALID`, `FORMAT_INVALID` (`phone`, `address.zipCode`, "
            + "`address.state`), `TYPE_INVALID` e `NOT_EDITABLE` (`cpf`, `birthDate` ou `email` no corpo). Nada muda.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — esta conta ainda não tem cadastro.")
    @PatchMapping("/me")
    CustomerResponse update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody CustomerChangesRequest request) {
        return CustomerResponse.of(updateOwnCustomer.update(request.toCommand(token.getSubject(),
                token.getClaimAsString(EMAIL_CLAIM), emailVerified(token))));
    }

    @Operation(summary = "Exportar os próprios dados", description = "Acesso e portabilidade (LGPD, art. 18). "
            + "Tudo o que o cadastro guarda sobre a conta do token: nome, e-mail, telefone, nascimento e endereço em "
            + "claro, o CPF mascarado (como no `GET /customers/me`), o pedido de exclusão, se houver, e o histórico da "
            + "auditoria sobre o cadastro — quem leu ou mudou o dado (o titular, a equipe com o papel, o serviço de "
            + "vendas com a compra), quando e por quê, sem a identidade de quem fez. As compras ficam no `GET "
            + "/purchases` do serviço de vendas. Grava a auditoria `CUSTOMER_EXPORTED`. A resposta não vai para cache.")
    @ApiResponse(responseCode = "200", description = "Os dados do titular.",
            headers = @Header(name = "Cache-Control", description = "`no-store`"))
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — esta conta não tem cadastro ativo.")
    @GetMapping("/me/export")
    ResponseEntity<CustomerExportResponse> export(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(CustomerExportResponse.of(exportOwnData.export(token.getSubject())));
    }

    @Operation(summary = "Pedir a exclusão do próprio cadastro", description = "Eliminação (LGPD, art. 18). "
            + "Pergunta ao serviço de vendas até quando a lei pede guardar os dados. Sem venda concluída nem compra "
            + "aberta no prazo (`RETENTION_AFTER_SALE`, 5 anos depois da venda), o cadastro é anonimizado na hora: o "
            + "nome, o CPF, o e-mail, o telefone, o nascimento, o endereço e o vínculo com a conta são apagados; o id "
            + "fica, porque as compras apontam para ele. Com venda no prazo, o pedido fica registrado e o job de "
            + "retenção anonimiza no fim dele — a resposta é `409`, com a data. Grava a auditoria "
            + "`CUSTOMER_DELETION_REQUESTED` e, ao anonimizar, `CUSTOMER_ANONYMIZED`. Depois de anonimizado, a conta "
            + "pode se cadastrar de novo. Não cria nada: sem `Idempotency-Key`.")
    @ApiResponse(responseCode = "200", description = "Anonimizado agora.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — esta conta não tem cadastro ativo (ou já foi "
            + "anonimizado).")
    @ApiResponse(responseCode = "409", description = "`CUSTOMER_HAS_RETAINED_DATA` — há venda no prazo de guarda: o "
            + "pedido ficou registrado e a `message` diz a data da anonimização. Repetir o pedido não muda nada.")
    @ApiResponse(responseCode = "503", description = "`DEPENDENCY_UNAVAILABLE` — o serviço de vendas não respondeu; nada "
            + "mudou.")
    @PostMapping("/me/deletion")
    DeletionResponse requestDeletion(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token) {
        return DeletionResponse.anonymizedAt(requestDeletion.request(token.getSubject()));
    }

    private static boolean emailVerified(Jwt token) {
        return Boolean.TRUE.equals(token.getClaimAsBoolean(EMAIL_VERIFIED_CLAIM));
    }
}
