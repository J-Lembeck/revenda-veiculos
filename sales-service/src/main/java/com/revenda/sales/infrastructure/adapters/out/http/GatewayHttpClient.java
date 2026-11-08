package com.revenda.sales.infrastructure.adapters.out.http;

import com.revenda.common.http.ServiceCalls;
import com.revenda.common.money.Money;
import com.revenda.common.outbox.OutboxDestination;
import com.revenda.common.outbox.OutboxMessage;
import com.revenda.common.outbox.OutboxSender;
import com.revenda.sales.domain.model.CommandDestination;
import com.revenda.common.error.DependencyUnavailableException;
import com.revenda.sales.domain.model.IssuedCharge;
import com.revenda.sales.domain.model.Payer;
import com.revenda.sales.domain.model.SagaCommand.CancelCharge;
import com.revenda.sales.domain.model.SagaCommand.RefundCharge;
import com.revenda.sales.domain.ports.out.IssueChargePort;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * O gateway de pagamento, pela chave de API: emite a cobrança no caminho síncrono e, pelo outbox, cancela e estorna.
 * Cancelar e estornar repetidos não fazem nada no gateway; o cancelamento sem o id da cobrança a procura pela compra,
 * e não achar é não ter o que cancelar. Estorno de cobrança que o gateway não conhece é falha: volta para a fila e,
 * esgotadas as tentativas, fica para intervenção.
 */
@Slf4j
@Component
class GatewayHttpClient implements IssueChargePort, OutboxSender {

    private static final String NEIGHBOR = "gateway de pagamento";

    private final RestClient rest;
    private final ObjectMapper json;

    GatewayHttpClient(@Qualifier("gatewayRestClient") RestClient rest, ObjectMapper json) {
        this.rest = rest;
        this.json = json;
    }

    @Override
    public IssuedCharge issue(UUID purchaseId, Money amount, Instant dueAt, Payer payer) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            CreatedCharge created = rest.post()
                    .uri("/charges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ChargeRequest.of(purchaseId, amount, dueAt, payer))
                    .retrieve()
                    .body(CreatedCharge.class);
            return new IssuedCharge(created.chargeId(), created.pixCode(), created.dueAt());
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(failure));
        }
    }

    @Override
    public OutboxDestination destination() {
        return CommandDestination.PAYMENT_GATEWAY::name;
    }

    @Override
    public void send(OutboxMessage message) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        ChargeCommand command = json.readValue(message.payload(), ChargeCommand.class);
        switch (message.type()) {
            case CancelCharge.TYPE -> cancel(command);
            case RefundCharge.TYPE -> refund(command);
            default -> throw new IllegalArgumentException("Comando desconhecido para o gateway: " + message.type());
        }
    }

    private void cancel(ChargeCommand command) {
        String chargeId = command.chargeId() != null ? command.chargeId() : findByReference(command.purchaseId());
        if (chargeId == null) {
            log.info("Compra {} sem cobrança no gateway: nada a cancelar", command.purchaseId());
            return;
        }
        try {
            rest.post().uri("/charges/{id}/cancel", chargeId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound unknown) {
            log.info("Cobrança {} da compra {} não existe no gateway: nada a cancelar", chargeId,
                    command.purchaseId());
        }
    }

    private void refund(ChargeCommand command) {
        rest.post().uri("/charges/{id}/refund", command.chargeId()).retrieve().toBodilessEntity();
    }

    private String findByReference(UUID purchaseId) {
        try {
            return rest.get()
                    .uri(uri -> uri.path("/charges").queryParam("referenceId", purchaseId).build())
                    .retrieve()
                    .body(FoundCharge.class)
                    .chargeId();
        } catch (HttpClientErrorException.NotFound none) {
            return null;
        }
    }

    record ChargeCommand(UUID purchaseId, String chargeId) {
    }

    record ChargeRequest(String referenceId, long amountCents, Instant dueAt, PayerBody payer) {

        static ChargeRequest of(UUID purchaseId, Money amount, Instant dueAt, Payer payer) {
            Payer.Address address = payer.address();
            return new ChargeRequest(purchaseId.toString(), amount.toCents(), dueAt, new PayerBody(payer.fullName(),
                    payer.cpf(), new AddressBody(address.zipCode(), address.street(), address.number(),
                    address.complement(), address.district(), address.city(), address.state())));
        }

        @Override
        public String toString() {
            return "ChargeRequest[referenceId=" + referenceId + ", amountCents=" + amountCents + "]";
        }
    }

    record PayerBody(String name, String cpf, AddressBody address) {

        @Override
        public String toString() {
            return "PayerBody[dados pessoais omitidos]";
        }
    }

    record AddressBody(String zipCode, String street, String number, String complement, String district,
            String city, String state) {

        @Override
        public String toString() {
            return "AddressBody[dados pessoais omitidos]";
        }
    }

    record CreatedCharge(String chargeId, String pixCode, Instant dueAt) {
    }

    record FoundCharge(String chargeId) {
    }
}
