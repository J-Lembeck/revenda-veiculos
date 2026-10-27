package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.RegisterCustomerUseCase;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Clientes", description = "O cadastro do comprador, feito por ele mesmo (DP-05).")
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
class CustomerController {

    private static final String EMAIL_CLAIM = "email";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final RegisterCustomerUseCase registerCustomer;

    @Operation(summary = "Cadastrar-se como comprador", description = "UC-06. Uma conta tem no máximo um cadastro e um "
            + "CPF, um cadastro ativo (RN-06). O e-mail vem da conta, pelo token — o corpo não tem e-mail. CPF, e-mail, "
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
                token.getClaimAsString(EMAIL_CLAIM), Boolean.TRUE.equals(token.getClaimAsBoolean(EMAIL_VERIFIED_CLAIM))));
        return ResponseEntity.created(URI.create("/customers/me")).body(CustomerResponse.of(customer));
    }
}
