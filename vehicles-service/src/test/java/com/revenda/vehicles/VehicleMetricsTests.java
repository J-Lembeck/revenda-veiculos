package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.TestTokens;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class VehicleMetricsTests extends VehiclesApiTest {

    private static final String RESERVATIONS = "vehicles.reservations";

    @Autowired
    private MeterRegistry meters;

    @LocalManagementPort
    private int managementPort;

    @Test
    void eachReservationRequestIsCountedByOutcome() {
        double reserved = reservations("reserved");
        double repeated = reservations("repeated");
        double rejected = reservations("rejected");
        Registered vehicle = register();
        UUID purchase = UUID.randomUUID();

        sales().post(reservationsOf(vehicle), Map.of("purchaseId", purchase));
        sales().post(reservationsOf(vehicle), Map.of("purchaseId", purchase));
        sales().post(reservationsOf(vehicle), Map.of("purchaseId", UUID.randomUUID()));
        sales().post("/internal/vehicles/" + UUID.randomUUID() + "/reservations", Map.of("purchaseId", purchase));

        assertThat(reservations("reserved")).isEqualTo(reserved + 1);
        assertThat(reservations("repeated")).isEqualTo(repeated + 1);
        assertThat(reservations("rejected")).isEqualTo(rejected + 2);
    }

    @Test
    void aRefusedRequestIsNotAReservationAttempt() {
        double total = reservations("reserved") + reservations("repeated") + reservations("rejected");
        Registered vehicle = register();

        api().asService(TestTokens.SALES_CLIENT, "outro.scope")
                .post(reservationsOf(vehicle), Map.of("purchaseId", UUID.randomUUID()));
        sales().post(reservationsOf(vehicle), Map.of());

        assertThat(reservations("reserved") + reservations("repeated") + reservations("rejected")).isEqualTo(total);
    }

    @Test
    void theCountGoesToPrometheusWithTheOutcomeOnly() {
        Registered vehicle = register();
        sales().post(reservationsOf(vehicle), Map.of("purchaseId", UUID.randomUUID()));

        ResponseEntity<String> scrape = anonymousManagement("/actuator/prometheus");

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody()).containsPattern("vehicles_reservations_total\\{outcome=\"reserved\"\\} \\d");
        assertThat(serviceLines(scrape.getBody())).doesNotContain(vehicle.id().toString());
    }

    /** O que o serviço mede: sem as linhas do cliente HTTP do próprio teste, que roda na mesma JVM. */
    private static String serviceLines(String scrape) {
        return scrape.lines().filter(line -> !line.startsWith("http_client_requests"))
                .collect(Collectors.joining("\n"));
    }

    private double reservations(String outcome) {
        var counter = meters.find(RESERVATIONS).tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private ResponseEntity<String> anonymousManagement(String path) {
        return api().anonymous()
                .exchange(HttpMethod.GET, "http://localhost:" + managementPort + path, null, String.class);
    }

    private ApiDriver sales() {
        return api().asService(TestTokens.SALES_CLIENT, "vehicles.reserve");
    }

    private static String reservationsOf(Registered vehicle) {
        return "/internal/vehicles/" + vehicle.id() + "/reservations";
    }
}
