package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** O limite de taxa ligado, como fora dos testes: o pedido de compra aceita 10 chamadas por minuto da mesma rede. */
@TestPropertySource(properties = "revenda.security.rate-limit.enabled=true")
class PurchaseRateLimitTests extends SalesApiTest {

    @Test
    void theEleventhPurchaseInAMinuteFromTheSameNetworkWaits() {
        for (int call = 0; call < 10; call++) {
            assertThat(customer(newCustomer()).post(PURCHASES, Map.of()).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        ResponseEntity<JsonNode> eleventh = customer(newCustomer()).post(PURCHASES, Map.of());

        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(eleventh.getBody().get("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(eleventh.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }
}
