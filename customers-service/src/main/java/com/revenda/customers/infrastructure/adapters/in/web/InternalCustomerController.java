package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.ports.in.CheckCpfUseCase;
import com.revenda.customers.domain.ports.in.CheckEligibilityUseCase;
import com.revenda.customers.domain.ports.in.ReadCustomerDataCommand;
import com.revenda.customers.domain.ports.in.ReadPayerDataUseCase;
import com.revenda.customers.domain.ports.in.ReadTransferDataUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O que só o {@code sales} chama, com token de serviço: a elegibilidade, os dados da cobrança e do documento e a
 * conferência do CPF na retirada.
 */
@Tag(name = "Interno", description = "Rotas que só o serviço de vendas chama, com token de serviço; não são "
        + "publicadas no API Gateway.")
@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
class InternalCustomerController {

    static final String PURCHASE_ID = "X-Purchase-Id";
    private static final String PURCHASE_ID_REQUIRED = "Informe o id da compra no cabeçalho X-Purchase-Id.";
    private static final String PURCHASE_ID_DESCRIPTION = "Id da compra para a qual o dado é lido; vai para a auditoria";
    private static final String PURCHASE_ID_ERRORS = "`VALIDATION_FAILED` com `X-Purchase-Id` `REQUIRED` (sem o "
            + "cabeçalho) ou `TYPE_INVALID` (não é um UUID), ou `id` `TYPE_INVALID`";

    private final CheckEligibilityUseCase checkEligibility;
    private final ReadPayerDataUseCase readPayer;
    private final ReadTransferDataUseCase readTransferData;
    private final CheckCpfUseCase checkCpf;

    @Operation(summary = "Elegibilidade da conta para comprar", description = "Primeiro passo da compra, antes de ela "
            + "existir (RN-05): cadastro ativo e 18 anos ou mais na data da compra, contada no fuso da revenda "
            + "(`America/Sao_Paulo`) — a maioridade começa no dia do aniversário. Conta sem cadastro → `eligible: false` "
            + "com `PROFILE_MISSING`; menor de idade → `UNDERAGE`. O `sub` é o pseudônimo do provedor de identidade. "
            + "Nada pessoal na resposta, e a consulta não é auditada.")
    @ApiResponse(responseCode = "200", description = "Se pode comprar e, se não, o motivo.")
    @GetMapping("/by-subject/{sub}/eligibility")
    EligibilityResponse eligibility(
            @Parameter(description = "O `sub` da conta no provedor de identidade") @PathVariable String sub) {
        return EligibilityResponse.of(checkEligibility.check(sub));
    }

    @Operation(summary = "Dados do pagador", description = "Passo 3 da compra: nome, CPF e endereço para emitir a "
            + "cobrança. O `sales` usa na hora e não grava. Cada leitura grava a auditoria `CUSTOMER_PAYER_DATA_READ` "
            + "com o serviço e a compra do `X-Purchase-Id`, sem dado pessoal. A resposta não vai para cache.")
    @ApiResponse(responseCode = "200", description = "O pagador, completo.",
            headers = @Header(name = "Cache-Control", description = "`no-store`"))
    @ApiResponse(responseCode = "400", description = PURCHASE_ID_ERRORS + ". Nada é lido nem auditado.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não há cliente ativo com esse id; nada é auditado.")
    @GetMapping("/{id}/payer")
    ResponseEntity<PayerResponse> payer(@Parameter(description = "Id do cliente") @PathVariable UUID id,
            @Parameter(description = PURCHASE_ID_DESCRIPTION, required = true)
            @RequestHeader(name = PURCHASE_ID, required = false) @NotNull(message = PURCHASE_ID_REQUIRED)
            UUID purchaseId) {
        return noStore(PayerResponse.of(readPayer.readPayer(new ReadCustomerDataCommand(id, purchaseId))));
    }

    @Operation(summary = "Dados do documento de transferência", description = "Passo 7 da compra: nome, CPF, e-mail e "
            + "endereço do comprador para o documento. O `sales` usa na hora e não grava. Cada leitura grava a "
            + "auditoria `CUSTOMER_TRANSFER_DATA_READ` com o serviço e a compra do `X-Purchase-Id`, sem dado pessoal. A "
            + "resposta não vai para cache.")
    @ApiResponse(responseCode = "200", description = "O comprador, completo.",
            headers = @Header(name = "Cache-Control", description = "`no-store`"))
    @ApiResponse(responseCode = "400", description = PURCHASE_ID_ERRORS + ". Nada é lido nem auditado.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não há cliente ativo com esse id; nada é auditado.")
    @GetMapping("/{id}/transfer-data")
    ResponseEntity<TransferDataResponse> transferData(@Parameter(description = "Id do cliente") @PathVariable UUID id,
            @Parameter(description = PURCHASE_ID_DESCRIPTION, required = true)
            @RequestHeader(name = PURCHASE_ID, required = false) @NotNull(message = PURCHASE_ID_REQUIRED)
            UUID purchaseId) {
        return noStore(TransferDataResponse.of(
                readTransferData.readTransferData(new ReadCustomerDataCommand(id, purchaseId))));
    }

    @Operation(summary = "Conferir o CPF na retirada", description = "Passo 5 da compra: o CPF que o comprador "
            + "apresenta na loja bate com o do cadastro? A conferência é pelo *blind index* (com as chaves anteriores, "
            + "durante uma rotação): nada é decifrado, e o CPF do cadastro nunca sai. Aceita só números ou com pontos e "
            + "traço. Cada conferência grava a auditoria `CUSTOMER_CPF_CHECKED` com o serviço, a compra do "
            + "`X-Purchase-Id` e o resultado, nunca o CPF. Não cria nada: sem `Idempotency-Key`.")
    @ApiResponse(responseCode = "200", description = "Se o CPF bate.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `cpf` `REQUIRED` (sem CPF) ou "
            + "`CPF_INVALID` (dígitos que não conferem), ou " + PURCHASE_ID_ERRORS + ". Nada é conferido nem auditado.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não há cliente ativo com esse id; nada é auditado.")
    @PostMapping("/{id}/cpf-check")
    CpfCheckResponse cpfCheck(@Parameter(description = "Id do cliente") @PathVariable UUID id,
            @Parameter(description = PURCHASE_ID_DESCRIPTION, required = true)
            @RequestHeader(name = PURCHASE_ID, required = false) @NotNull(message = PURCHASE_ID_REQUIRED)
            UUID purchaseId,
            @Valid @RequestBody CpfCheckRequest request) {
        return new CpfCheckResponse(checkCpf.check(request.toCommand(id, purchaseId)));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
