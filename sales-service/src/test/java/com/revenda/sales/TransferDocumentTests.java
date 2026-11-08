package com.revenda.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.test.SyntheticData;
import com.revenda.sales.FakeNeighbors.Call;
import com.revenda.sales.FakeNeighbors.Reply;
import com.revenda.sales.domain.ports.in.GenerateTransferDocumentUseCase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/** O passo 7 da saga: o documento de transferência sai pelo outbox depois da retirada e só repete até dar certo. */
@ExtendWith(OutputCaptureExtension.class)
class TransferDocumentTests extends SalesApiTest {

    @Autowired
    private GenerateTransferDocumentUseCase generateDocument;

    @Test
    void afterThePickupTheDocumentGoesToThePrivateBucketEncryptedWithTheDocumentsKey() {
        UUID id = completedPurchase(newCustomer());

        dispatch();

        String key = (String) purchaseRow(id).get("document_key");
        assertThat(key).isEqualTo("purchases/" + id + "/transfer-document.pdf");
        HeadObjectResponse stored = SalesTestS3.S3.headObject(request -> request.bucket(SalesTestS3.BUCKET).key(key));
        assertThat(stored.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
        assertThat(stored.ssekmsKeyId()).contains(SalesTestS3.KMS_KEY_ID);
        assertThat(stored.contentType()).isEqualTo("application/pdf");
        assertThat(timelineOf(id)).last().isEqualTo("COMPLETED/PAID/DOCUMENT_ISSUED");
        assertThat(outboxRowsOf(id).getLast()).containsEntry("type", "GenerateTransferDocument")
                .containsEntry("status", "DONE");
    }

    @Test
    void thePdfHasTheBuyerTheVehicleAndTheSaleAsTheAtpvAsks() {
        UUID id = completedPurchase(newCustomer());
        dispatch();

        String text = pdfText(storedPdf(id)).replaceAll("\\s+", " ");

        assertThat(text).contains("Documento de transferência de veículo", "Não substitui o documento emitido pelo Detran",
                "REVENDA SIMULADA", FakeNeighbors.BUYER_NAME, SyntheticData.formatCpf(FakeNeighbors.BUYER_CPF),
                FakeNeighbors.BUYER_EMAIL, "Praça da Sé, 100, apto 1", "São Paulo/SP", "CEP 01001-000",
                "Chevrolet Onix 1.0 Turbo LT", "2023 / 2024", "Prata", "18.500 km", FakeNeighbors.PLATE,
                FakeNeighbors.VIN, FakeNeighbors.RENAVAM, "89.900,00", id.toString());
    }

    @Test
    void theBuyerIsReadWithThePurchaseAndTheVehicleFromTheStockWithTheServiceToken() {
        UUID id = completedPurchase(newCustomer());
        Map<String, Object> row = purchaseRow(id);

        dispatch();

        Call buyer = NEIGHBORS.callsTo("/internal/customers/" + row.get("customer_id") + "/transfer-data").getLast();
        assertThat(buyer.purchaseId()).isEqualTo(id.toString());
        assertThat(buyer.authorization()).startsWith("Bearer token-de-servico-");
        Call vehicle = NEIGHBORS.callsTo("/internal/vehicles/" + row.get("vehicle_id")).stream()
                .filter(call -> "GET".equals(call.method())).toList().getLast();
        assertThat(vehicle.authorization()).startsWith("Bearer token-de-servico-");
    }

    @Test
    void repeatingTheCommandIssuesNoOtherDocument() {
        UUID id = completedPurchase(newCustomer());
        dispatch();
        int version = (int) purchaseRow(id).get("version");
        int reads = transferDataReads(id);

        generateDocument.generate(id);

        assertThat(purchaseRow(id).get("version")).isEqualTo(version);
        assertThat(timelineOf(id)).filteredOn(step -> step.endsWith("DOCUMENT_ISSUED")).hasSize(1);
        assertThat(transferDataReads(id)).as("nem lê o comprador de novo").isEqualTo(reads);
    }

    @Test
    void withTheCadastreDownTheDocumentWaitsAndComesOutWhenItAnswers() {
        UUID id = completedPurchase(newCustomer());
        NEIGHBORS.transferDataAnswers(Reply.status(500));

        dispatcher.dispatchDue();

        assertThat(purchaseRow(id).get("document_key")).isNull();
        assertThat(outboxRowsOf(id).getLast()).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        NEIGHBORS.transferDataAnswers(null);
        awaitDocument(id);
        assertThat(timelineOf(id)).last().isEqualTo("COMPLETED/PAID/DOCUMENT_ISSUED");
    }

    @Test
    void withTheStockDownTheDocumentWaitsToo() {
        UUID id = completedPurchase(newCustomer());
        NEIGHBORS.vehicleReadAnswers(Reply.status(503));

        dispatcher.dispatchDue();

        assertThat(purchaseRow(id).get("document_key")).isNull();
        assertThat(outboxRowsOf(id).getLast()).containsEntry("status", "PENDING");
        NEIGHBORS.vehicleReadAnswers(null);
        awaitDocument(id);
    }

    @Test
    void nothingPersonalGoesToTheLogTheOutboxOrTheBank(CapturedOutput output) {
        UUID id = completedPurchase(newCustomer());
        NEIGHBORS.transferDataAnswers(Reply.status(500));
        dispatcher.dispatchDue();
        NEIGHBORS.transferDataAnswers(null);
        awaitDocument(id);

        List<String> personal = List.of(FakeNeighbors.BUYER_CPF, SyntheticData.formatCpf(FakeNeighbors.BUYER_CPF),
                FakeNeighbors.BUYER_EMAIL, FakeNeighbors.BUYER_NAME, "Praça da Sé");
        personal.forEach(value -> assertThat(output.getAll()).doesNotContain(value));
        String outbox = outboxRowsOf(id).toString();
        String purchase = purchaseRow(id).toString();
        personal.forEach(value -> assertThat(outbox + purchase).doesNotContain(value));
    }

    private void awaitDocument(UUID id) {
        Awaitility.await().atMost(PATIENCE).until(() -> {
            dispatcher.dispatchDue();
            return purchaseRow(id).get("document_key") != null;
        });
    }

    private byte[] storedPdf(UUID id) {
        return SalesTestS3.S3.getObjectAsBytes(request -> request.bucket(SalesTestS3.BUCKET)
                .key((String) purchaseRow(id).get("document_key"))).asByteArray();
    }

    private static int transferDataReads(UUID purchaseId) {
        return (int) NEIGHBORS.calls().stream()
                .filter(call -> call.path().endsWith("/transfer-data") && purchaseId.toString().equals(call.purchaseId()))
                .count();
    }
}
