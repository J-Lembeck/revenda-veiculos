package com.revenda.sales.infrastructure.adapters.in.messaging;

import com.revenda.common.error.BusinessException;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.webhook.WebhookSignature;
import com.revenda.sales.domain.model.SalesError;
import com.revenda.sales.domain.ports.in.PaymentEventCommand;
import com.revenda.sales.domain.ports.in.ReceivePaymentEventUseCase;
import com.revenda.sales.domain.ports.in.VerifyPaymentWebhookUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * O webhook do gateway: rota pública, autenticada pela assinatura do corpo bruto. Conferida a assinatura, quem age é o
 * sistema. Responde {@code 200} só depois do commit; o evento que ainda não se aplica ou contradiz a compra sai com
 * {@code 409} sem corpo — o código fica só no log — e o gateway entrega de novo.
 */
@Slf4j
@Tag(name = "Webhook de pagamento", description = "O aviso do gateway de pagamento, assinado com HMAC.")
@RestController
@RequiredArgsConstructor
class PaymentWebhookController {

    private static final Set<String> NOT_APPLIED = Set.of(SalesError.PAYMENT_EVENT_NOT_YET_APPLICABLE.name(),
            SalesError.PAYMENT_EVENT_CONTRADICTORY.name());

    private final VerifyPaymentWebhookUseCase verifyWebhook;
    private final ReceivePaymentEventUseCase receivePaymentEvent;
    private final ObjectMapper json;

    @Operation(summary = "Aviso de pagamento do gateway", description = "UC-09 (RN-12). O pagamento só se confirma "
            + "por aqui, nunca por chamada do cliente. `X-Signature: t=<segundos>,v1=<hex>`, com "
            + "`v1 = HMAC-SHA256(segredo, t + \".\" + corpo bruto)` e o carimbo a no máximo 5 minutos do relógio; "
            + "a recusa grava `PAYMENT_WEBHOOK_REJECTED`. O `eventId` repetido responde `200` sem efeito. "
            + "`CHARGE_PAID` com o valor cobrado → compra `PAID` e o veículo vendido; com outro valor → `FAILED` "
            + "(`PAYMENT_MISMATCH`), estorno e liberação; depois de encerrada → estorno, e a compra não revive. "
            + "`CHARGE_REFUNDED` confirma o estorno pedido.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = WebhookSignature.HEADER, required = true,
                    description = "`t=<segundos desde a época>,v1=<HMAC-SHA256 em hexadecimal>`",
                    schema = @Schema(type = "string")),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PaymentWebhookBody.class))))
    @ApiResponse(responseCode = "200", description = "Aplicado, ou repetido e descartado.", content = @Content)
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` — corpo assinado, mas sem um campo "
            + "obrigatório, com `type` desconhecido ou valor não positivo.")
    @ApiResponse(responseCode = "401", description = "`INVALID_SIGNATURE` — sem assinatura, assinatura que não confere "
            + "ou carimbo fora da janela de 5 minutos.")
    @ApiResponse(responseCode = "409", description = "Sem corpo: o evento chegou antes de a compra aguardar o pagamento "
            + "ou contradiz a compra (outra cobrança, estorno não pedido); nada foi gravado e o gateway entrega de "
            + "novo.", content = @Content)
    @PostMapping(path = "/webhooks/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> receive(
            @Parameter(hidden = true) @RequestHeader(name = WebhookSignature.HEADER, required = false) String signature,
            @Parameter(hidden = true) @RequestBody byte[] body) {
        return ActorContextHolder.callAs(ActorContextHolder.get().asSystem(), () -> {
            verifyWebhook.verify(signature, body);
            PaymentEventCommand event = read(body).toCommand();
            try {
                ReceivePaymentEventUseCase.Outcome outcome = receivePaymentEvent.receive(event);
                log.info("Evento {} ({}) da compra {}: {}", event.eventId(), event.type(), event.referenceId(),
                        outcome);
                return ResponseEntity.ok().build();
            } catch (BusinessException notApplied) {
                if (!NOT_APPLIED.contains(notApplied.getCode())) {
                    throw notApplied;
                }
                log.warn("Evento {} ({}) da compra {} não aplicado: {}", event.eventId(), event.type(),
                        event.referenceId(), notApplied.getCode());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
        });
    }

    private PaymentWebhookBody read(byte[] body) {
        try {
            return json.readValue(body, PaymentWebhookBody.class);
        } catch (JacksonException unreadable) {
            throw BusinessException.invalid();
        }
    }
}
