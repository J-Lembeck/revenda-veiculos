package com.revenda.sales.infrastructure.adapters.out.http;

import com.revenda.sales.domain.model.DependencyUnavailableException;
import com.revenda.sales.domain.model.Eligibility;
import com.revenda.sales.domain.model.Payer;
import com.revenda.sales.domain.ports.out.CheckEligibilityPort;
import com.revenda.sales.domain.ports.out.LoadPayerPort;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * A elegibilidade e o pagador no cadastro, com o token de serviço de scope {@code customers.read-payer}. O pagador
 * fica só em memória, para a cobrança: nada dele vai para o log.
 */
@Component
class CustomersHttpClient implements CheckEligibilityPort, LoadPayerPort {

    private static final String NEIGHBOR = "cadastro";
    private static final String PURCHASE_ID = "X-Purchase-Id";

    private final RestClient rest;

    CustomersHttpClient(@Qualifier("customersRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Eligibility check(String customerSubject) {
        NeighborCalls.requireNoTransaction(NEIGHBOR);
        try {
            EligibilityResponse answer = rest.get()
                    .uri("/internal/customers/by-subject/{sub}/eligibility", customerSubject)
                    .retrieve()
                    .body(EligibilityResponse.class);
            return new Eligibility(answer.customerId(), answer.eligible(), answer.reason());
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, NeighborCalls.describe(failure));
        }
    }

    @Override
    public Payer load(UUID customerId, UUID purchaseId) {
        NeighborCalls.requireNoTransaction(NEIGHBOR);
        try {
            PayerResponse payer = rest.get()
                    .uri("/internal/customers/{id}/payer", customerId)
                    .header(PURCHASE_ID, purchaseId.toString())
                    .retrieve()
                    .body(PayerResponse.class);
            AddressResponse address = payer.address();
            return new Payer(payer.customerId(), payer.fullName(), payer.cpf(), new Payer.Address(address.zipCode(),
                    address.street(), address.number(), address.complement(), address.district(), address.city(),
                    address.state()));
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, NeighborCalls.describe(failure));
        }
    }

    record EligibilityResponse(UUID customerId, boolean eligible, Eligibility.Reason reason) {
    }

    record PayerResponse(UUID customerId, String fullName, String cpf, AddressResponse address) {

        @Override
        public String toString() {
            return "PayerResponse[customerId=" + customerId + "]";
        }
    }

    record AddressResponse(String zipCode, String street, String number, String complement, String district,
            String city, String state) {

        @Override
        public String toString() {
            return "AddressResponse[dados pessoais omitidos]";
        }
    }
}
