package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.revenda.common.audit.AuditRecorder;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;

/** A edição e a auditoria dela são uma transação só: a falha depois da auditoria desfaz as duas. */
class CustomerUpdateRollbackTests extends CustomersApiTest {

    @MockitoSpyBean
    private AuditRecorder audit;

    @Test
    void aFailureAfterTheAuditUndoesTheEditAndTheAuditTogether() {
        Account account = newAccount();
        UUID id = register(account, customerBody());
        Map<String, Object> before = rowOf(id);
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("falha simulada depois da auditoria");
        }).when(audit).record(any());

        ResponseEntity<JsonNode> response = as(account).patch(ME, Map.of("phone", "+5511988887777"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(changedColumns(before, rowOf(id))).isEmpty();
        assertThat(audited("CUSTOMER_UPDATED", id)).isZero();
    }
}
