package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SalesMetricsTests extends SalesApiTest {

    private static final String TRANSITIONS = "sales.purchase.transitions";
    private static final String BY_STATUS = "sales.purchases";
    private static final String WEBHOOKS_REJECTED = "sales.payment.webhooks.rejected";

    @Autowired
    private MeterRegistry meters;

    @LocalManagementPort
    private int managementPort;

    @Test
    @Order(1)
    void theGaugeShowsHowManyPurchasesAreInEachStatus() {
        paidPurchase(newCustomer());
        awaitingPurchase(newCustomer());

        for (String status : new String[] {"PAID", "AWAITING_PAYMENT", "COMPLETED", "EXPIRED"}) {
            long inDatabase = jdbc.sql("SELECT count(*) FROM purchase WHERE status = :status")
                    .param("status", status).query(Long.class).single();
            assertThat(meters.get(BY_STATUS).tag("status", status).gauge().value())
                    .as(status).isEqualTo((double) inDatabase);
        }
    }

    @Test
    void eachStepOfTheTimelineIsCountedWithStatusPaymentAndReason() {
        double reserved = transitions("RESERVED", "NONE", "VEHICLE_RESERVED");
        double charged = transitions("AWAITING_PAYMENT", "PENDING", "CHARGE_ISSUED");
        double paid = transitions("PAID", "PAID", "PAYMENT_CONFIRMED");

        paidPurchase(newCustomer());

        assertThat(transitions("RESERVED", "NONE", "VEHICLE_RESERVED")).isEqualTo(reserved + 1);
        assertThat(transitions("AWAITING_PAYMENT", "PENDING", "CHARGE_ISSUED")).isEqualTo(charged + 1);
        assertThat(transitions("PAID", "PAID", "PAYMENT_CONFIRMED")).isEqualTo(paid + 1);
    }

    @Test
    void aRejectedWebhookIsCountedByCause() {
        double missing = rejected("missing_signature");
        double invalid = rejected("invalid_signature");
        String body = paymentEvent(newEventId(), "CHARGE_PAID", "chg_qualquer", UUID.randomUUID(), 100);

        webhook(body, null);
        webhook(body, "t=1,v1=assinatura-errada");

        assertThat(rejected("missing_signature")).isEqualTo(missing + 1);
        assertThat(rejected("invalid_signature")).isEqualTo(invalid + 1);
    }

    @Test
    void prometheusShowsTheCountsWithoutAnythingAboutTheBuyer() {
        String subject = newCustomer();
        UUID purchase = paidPurchase(subject);

        ResponseEntity<String> scrape = api().anonymous().exchange(HttpMethod.GET,
                "http://localhost:" + managementPort + "/actuator/prometheus", null, String.class);

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .contains("sales_purchase_transitions_total{payment_status=\"PAID\",reason=\"PAYMENT_CONFIRMED\","
                        + "status=\"PAID\"}")
                .contains("sales_purchases{status=\"PAID\"}")
                .contains("outbox_messages_failed{destination=\"VEHICLE_COMMANDS\"}")
                .contains("outbox_messages_oldest_age_seconds{destination=\"PAYMENT_GATEWAY\"}")
                .doesNotContain(subject, purchase.toString(), chargeIdOf(purchase));
    }

    private double transitions(String status, String payment, String reason) {
        Counter counter = meters.find(TRANSITIONS)
                .tag("status", status).tag("payment_status", payment).tag("reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }

    private double rejected(String cause) {
        Counter counter = meters.find(WEBHOOKS_REJECTED).tag("cause", cause).counter();
        return counter == null ? 0 : counter.count();
    }
}
