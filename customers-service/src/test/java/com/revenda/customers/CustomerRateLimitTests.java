package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** O limite de taxa ligado, como fora dos testes: o cadastro aceita 5 chamadas por minuto da mesma rede. */
@TestPropertySource(properties = "revenda.security.rate-limit.enabled=true")
class CustomerRateLimitTests extends CustomersApiTest {

    @Test
    void theSixthRegistrationInAMinuteFromTheSameNetworkWaits() {
        for (int call = 0; call < 5; call++) {
            assertThat(as(newAccount()).post(CUSTOMERS, Map.of()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        ResponseEntity<JsonNode> sixth = as(newAccount()).post(CUSTOMERS, customerBody());

        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixth.getBody().get("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(sixth.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }
}
