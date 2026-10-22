package com.revenda.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.error.web.ApiError;
import com.revenda.common.observability.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class HealthEndpointContract {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthRespondsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void healthHidesComponentDetails() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getBody()).doesNotContain("components", "details");
    }

    @Test
    void unknownRouteAnswersTheCommonErrorFormat() {
        ResponseEntity<ApiError> response = rest.getForEntity("/rota-que-nao-existe", ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().requestId())
                .isNotBlank()
                .isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump"})
    void otherActuatorEndpointsAreNotExposed(String path) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
