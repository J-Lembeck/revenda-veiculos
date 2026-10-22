package com.revenda.common.test;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.revenda.common.error.web.ApiError;
import com.revenda.common.observability.RequestIdFilter;
import com.revenda.common.privacy.MaskingAppender;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public abstract class ServiceFoundationContract {

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

    @Test
    void everyLogOutputGoesThroughTheMasker() {
        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
        List<Appender<ILoggingEvent>> outputs = new ArrayList<>();
        root.iteratorForAppenders().forEachRemaining(outputs::add);

        assertThat(outputs).singleElement().isInstanceOf(MaskingAppender.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump"})
    void otherActuatorEndpointsAreNotExposed(String path) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
