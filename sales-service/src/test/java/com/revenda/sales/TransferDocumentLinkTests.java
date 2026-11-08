package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.revenda.common.test.SyntheticData;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** O documento de transferência por link temporário: o dono e a equipe baixam; cada link entregue é auditado. */
class TransferDocumentLinkTests extends SalesApiTest {

    @Test
    void theOwnerGetsALinkOfFiveMinutesThatDownloadsThePdf() {
        String subject = newCustomer();
        UUID id = documented(subject);

        ResponseEntity<JsonNode> response = documentOf(customer(subject), id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        URI url = URI.create(response.getBody().get("url").asString());
        Instant expiresAt = Instant.parse(response.getBody().get("expiresAt").asString());
        assertThat(expiresAt).isCloseTo(clock.instant().plusSeconds(300), within(30, ChronoUnit.SECONDS));
        assertThat(url.getRawQuery()).contains("X-Amz-Expires=300");
        HttpResponse<byte[]> pdf = download(url);
        assertThat(pdf.statusCode()).isEqualTo(200);
        assertThat(pdf.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(new String(pdf.body(), 0, 5)).isEqualTo("%PDF-");
        assertThat(pdfText(pdf.body())).contains(FakeNeighbors.PLATE);
    }

    @Test
    void theLinkCarriesNothingOfTheBuyer() {
        String subject = newCustomer();
        UUID id = documented(subject);

        String url = documentOf(customer(subject), id).getBody().get("url").asString();

        assertThat(url).contains(id.toString()).doesNotContain(subject).doesNotContain(FakeNeighbors.BUYER_CPF)
                .doesNotContain(SyntheticData.formatCpf(FakeNeighbors.BUYER_CPF)).doesNotContain("maria");
    }

    @Test
    void theStaffAlsoGetsTheLinkAndEachLinkIsAuditedWithWhoAsked() {
        String subject = newCustomer();
        UUID id = documented(subject);

        documentOf(customer(subject), id);
        ResponseEntity<JsonNode> staff = documentOf(seller(), id);

        assertThat(staff.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.sql("""
                        SELECT actor_id FROM audit_log
                         WHERE action = 'PURCHASE_DOCUMENT_DOWNLOADED' AND entity_id = :id ORDER BY id""")
                .param("id", id).query(String.class).list()).containsExactly(subject, SELLER);
        Map<String, Object> audit = jdbc.sql("""
                        SELECT summary, data::text AS data FROM audit_log
                         WHERE action = 'PURCHASE_DOCUMENT_DOWNLOADED' AND entity_id = :id LIMIT 1""")
                .param("id", id).query().singleRow();
        assertThat((String) audit.get("data")).contains("expiresAt").doesNotContain("X-Amz");
    }

    @Test
    void anotherCustomersDocumentIsNotFound() {
        UUID id = documented(newCustomer());

        ResponseEntity<JsonNode> response = documentOf(customer(newCustomer()), id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(audited("PURCHASE_DOCUMENT_DOWNLOADED", id)).isZero();
    }

    @Test
    void anUnknownPurchaseIsNotFound() {
        assertThat(documentOf(seller(), UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void beforeThePickupThereIsNoDocument() {
        String subject = newCustomer();
        UUID id = paidPurchase(subject);

        ResponseEntity<JsonNode> response = documentOf(customer(subject), id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code").asString()).isEqualTo("DOCUMENT_NOT_READY");
        assertThat(audited("PURCHASE_DOCUMENT_DOWNLOADED", id)).isZero();
    }

    @Test
    void rightAfterThePickupTheDocumentIsStillOnItsWay() {
        String subject = newCustomer();
        UUID id = completedPurchase(subject);

        ResponseEntity<JsonNode> early = documentOf(customer(subject), id);
        dispatch();
        ResponseEntity<JsonNode> ready = documentOf(customer(subject), id);

        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(early.getBody().get("code").asString()).isEqualTo("DOCUMENT_NOT_READY");
        assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void servicesAndVisitorsDoNotGetTheDocument() {
        UUID id = documented(newCustomer());
        String path = PURCHASES + "/" + id + "/document";

        assertThat(api().asService("customers-service", "sales.read-retention").get(path).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(api().get(path).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(api().asAdmin("9a3c2f81-administrador").get(path).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anIdThatIsNotAUuidIsRefused() {
        ResponseEntity<JsonNode> response = seller().get(PURCHASES + "/compra/document");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().at("/fields/0/field").asString()).isEqualTo("id");
    }

    private UUID documented(String subject) {
        UUID id = completedPurchase(subject);
        dispatch();
        return id;
    }
}
