package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * O processo inteiro do enunciado, do pedido ao documento baixado: reservar, receber o código, pagar pelo webhook
 * assinado, vender no estoque pela fila, retirar com o CPF conferido, dar baixa no estoque pela fila e baixar o PDF do
 * bucket. Os comandos do estoque são lidos na fila FIFO de verdade, com o envelope que o consumidor do estoque aplica.
 */
class PurchaseEndToEndTests extends SalesApiTest {

    @Test
    void fromTheOrderToTheDocumentWithTheVehicleSoldAndDeliveredByTheQueue() {
        String subject = newCustomer();
        UUID vehicleId = UUID.randomUUID();

        ResponseEntity<JsonNode> created = buy(subject, vehicleId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(created.getBody().get("id").asString());
        assertThat(created.getBody().get("status").asString()).isEqualTo("AWAITING_PAYMENT");

        assertThat(pay(id).getStatusCode()).isEqualTo(HttpStatus.OK);
        dispatch();
        assertThat(statusOf(id)).isEqualTo("PAID");

        ResponseEntity<JsonNode> picked = pickup(id, FakeNeighbors.BUYER_CPF);
        assertThat(picked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(picked.getBody().get("status").asString()).isEqualTo("COMPLETED");
        dispatch();

        List<QueuedCommand> commands = queuedFor(vehicleId, 2);
        assertThat(commands).extracting(QueuedCommand::type).containsExactly("MarkVehicleSold", "DeliverVehicle");
        QueuedCommand delivery = commands.getLast();
        assertThat(delivery.groupId()).isEqualTo(vehicleId.toString());
        assertThat(delivery.envelope().at("/payload/vehicleId").asString()).isEqualTo(vehicleId.toString());
        assertThat(delivery.envelope().at("/payload/purchaseId").asString()).isEqualTo(id.toString());
        assertThat(delivery.envelope().at("/payload/deliveredAt").asString()).isNotBlank();
        assertThat(delivery.deduplicationId()).isEqualTo(delivery.envelope().get("id").asString());

        ResponseEntity<JsonNode> document = documentOf(customer(subject), id);
        assertThat(document.getStatusCode()).isEqualTo(HttpStatus.OK);
        byte[] pdf = download(URI.create(document.getBody().get("url").asString())).body();
        assertThat(pdfText(pdf)).contains(FakeNeighbors.BUYER_NAME, FakeNeighbors.PLATE, "89.900,00");

        JsonNode detail = customer(subject).get(PURCHASES + "/" + id).getBody();
        assertThat(detail.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(timelineOf(id)).containsExactly(
                "PENDING/NONE/PURCHASE_REQUESTED",
                "RESERVED/NONE/VEHICLE_RESERVED",
                "AWAITING_PAYMENT/PENDING/CHARGE_ISSUED",
                "PAID/PAID/PAYMENT_CONFIRMED",
                "COMPLETED/PAID/PICKUP_CONFIRMED",
                "COMPLETED/PAID/DOCUMENT_ISSUED");
        assertThat(outboxRowsOf(id)).allSatisfy(row -> assertThat(row).containsEntry("status", "DONE"));
    }
}
