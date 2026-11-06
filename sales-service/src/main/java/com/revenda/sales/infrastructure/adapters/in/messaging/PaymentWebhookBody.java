package com.revenda.sales.infrastructure.adapters.in.messaging;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.FieldViolation;
import com.revenda.common.messaging.ProcessedMessages;
import com.revenda.common.money.Money;
import com.revenda.sales.domain.model.PaymentEventType;
import com.revenda.sales.domain.ports.in.PaymentEventCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "O aviso do gateway: só ids, o valor em centavos e o instante; nunca o pagador.")
record PaymentWebhookBody(
        @Schema(description = "Id do evento; a repetição é descartada por ele", example = "evt_9f2c4b1a7e3d4c5b8a6f1e2d3c4b5a69")
        String eventId,
        @Schema(description = "`CHARGE_PAID` ou `CHARGE_REFUNDED`", example = "CHARGE_PAID") String type,
        @Schema(example = "chg_3e1d2c4b5a6f7e8d9c0b1a2f3e4d5c6b") String chargeId,
        @Schema(description = "Id da compra", example = "5b7d2c1e-8a4f-4d3b-9e6a-1c2b3d4e5f60") String referenceId,
        @Schema(description = "Valor pago (ou estornado), em centavos", example = "8990000") Long amountCents,
        @Schema(example = "2026-11-06T14:10:00Z") Instant occurredAt) {

    private static final int CHARGE_ID_LIMIT = 64;
    private static final String REQUIRED = "REQUIRED";

    PaymentEventCommand toCommand() {
        List<FieldViolation> problems = new ArrayList<>();
        require(problems, "eventId", eventId, ProcessedMessages.MESSAGE_ID_LIMIT);
        require(problems, "chargeId", chargeId, CHARGE_ID_LIMIT);
        require(problems, "referenceId", referenceId, CHARGE_ID_LIMIT);
        PaymentEventType eventType = typeOrNull(type);
        if (eventType == null) {
            problems.add(new FieldViolation("type", type == null ? REQUIRED : "INVALID",
                    "Use CHARGE_PAID ou CHARGE_REFUNDED."));
        }
        if (amountCents == null || amountCents <= 0) {
            problems.add(new FieldViolation("amountCents", amountCents == null ? REQUIRED : "OUT_OF_RANGE",
                    "Informe o valor em centavos, maior que zero."));
        }
        if (occurredAt == null) {
            problems.add(new FieldViolation("occurredAt", REQUIRED, "Informe quando o evento aconteceu."));
        }
        if (!problems.isEmpty()) {
            throw BusinessException.invalid(problems.toArray(FieldViolation[]::new));
        }
        return new PaymentEventCommand(eventId, eventType, chargeId, referenceId, Money.ofCents(amountCents),
                occurredAt);
    }

    private static void require(List<FieldViolation> problems, String field, String value, int limit) {
        if (value == null || value.isBlank()) {
            problems.add(new FieldViolation(field, REQUIRED, "Campo obrigatório."));
        } else if (value.length() > limit) {
            problems.add(new FieldViolation(field, "SIZE_INVALID", "No máximo " + limit + " caracteres."));
        }
    }

    private static PaymentEventType typeOrNull(String type) {
        for (PaymentEventType known : PaymentEventType.values()) {
            if (known.name().equals(type)) {
                return known;
            }
        }
        return null;
    }
}
