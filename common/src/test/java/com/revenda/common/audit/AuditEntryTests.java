package com.revenda.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryTests {

    private static final UUID CUSTOMER_ID = UUID.fromString("0f8e2c6a-3b1d-4e7f-9a5c-2d4b6e8f0a1c");

    private enum Action implements AuditAction {
        CUSTOMER_CPF_REVEALED,
        ACTION_WITH_A_NAME_FAR_TOO_LONG_FOR_THE_AUDIT_LOG_ACTION_COLUMN_X
    }

    @Test
    void summaryAndReasonGoThroughTheMasker() {
        AuditEntry entry = AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, "CUSTOMER", CUSTOMER_ID,
                        "Revelou o CPF 529.982.247-25 do cliente")
                .withReason("Pedido do titular por cliente.teste@example.test");

        assertThat(entry.summary()).doesNotContain("529.982.247-25").startsWith("Revelou o CPF");
        assertThat(entry.reason()).doesNotContain("cliente.teste@example.test").startsWith("Pedido do titular");
    }

    @Test
    void textsAreTrimmedAndKeptWhenClean() {
        AuditEntry entry = AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, "CUSTOMER", CUSTOMER_ID, "  Revelou o CPF  ")
                .withReason(" Conferência de cadastro ");

        assertThat(entry.summary()).isEqualTo("Revelou o CPF");
        assertThat(entry.reason()).isEqualTo("Conferência de cadastro");
    }

    @Test
    void refusesWhatTheTableCannotHold() {
        String tooLong = "a".repeat(AuditEntry.TEXT_LIMIT + 1);

        assertThatThrownBy(() -> AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, "CUSTOMER", CUSTOMER_ID, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, "CUSTOMER", CUSTOMER_ID, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, "CUSTOMER", CUSTOMER_ID, "Revelou")
                .withReason(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, "CUSTOMER", CUSTOMER_ID, "Revelou")
                .withReason(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEntry.of(Action.CUSTOMER_CPF_REVEALED,
                "C".repeat(AuditEntry.ENTITY_TYPE_LIMIT + 1), CUSTOMER_ID, "Revelou"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditEntry.of(Action.ACTION_WITH_A_NAME_FAR_TOO_LONG_FOR_THE_AUDIT_LOG_ACTION_COLUMN_X,
                "CUSTOMER", CUSTOMER_ID, "Revelou"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actionEntityTypeAndSummaryAreRequired() {
        assertThatThrownBy(() -> AuditEntry.of(null, "CUSTOMER", CUSTOMER_ID, "Revelou"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, null, CUSTOMER_ID, "Revelou"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AuditEntry.of(Action.CUSTOMER_CPF_REVEALED, "CUSTOMER", CUSTOMER_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
