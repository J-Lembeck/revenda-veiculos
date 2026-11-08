package com.revenda.sales.infrastructure.adapters.out.http;

import com.revenda.common.http.ServiceCalls;
import com.revenda.common.error.BusinessException;
import com.revenda.common.error.FieldViolation;
import com.revenda.common.error.DependencyUnavailableException;
import com.revenda.sales.domain.model.Eligibility;
import com.revenda.sales.domain.model.Payer;
import com.revenda.sales.domain.model.TransferBuyer;
import com.revenda.sales.domain.ports.out.CheckBuyerIdentityPort;
import com.revenda.sales.domain.ports.out.CheckEligibilityPort;
import com.revenda.sales.domain.ports.out.LoadPayerPort;
import com.revenda.sales.domain.ports.out.LoadTransferBuyerPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * O cadastro, com o token de serviço de scope {@code customers.read-payer}: a elegibilidade, o pagador, o comprador do
 * documento e a conferência do CPF na retirada. O que volta de pessoal fica só em memória, para a cobrança ou o PDF:
 * nada vai para o log. O CPF da conferência vai no corpo, nunca na URL.
 */
@Component
class CustomersHttpClient implements CheckEligibilityPort, LoadPayerPort, LoadTransferBuyerPort,
        CheckBuyerIdentityPort {

    private static final String NEIGHBOR = "cadastro";
    private static final String PURCHASE_ID = "X-Purchase-Id";

    private final RestClient rest;

    CustomersHttpClient(@Qualifier("customersRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Eligibility check(String customerSubject) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            EligibilityResponse answer = rest.get()
                    .uri("/internal/customers/by-subject/{sub}/eligibility", customerSubject)
                    .retrieve()
                    .body(EligibilityResponse.class);
            return new Eligibility(answer.customerId(), answer.eligible(), answer.reason());
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(failure));
        }
    }

    @Override
    public Payer load(UUID customerId, UUID purchaseId) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            PayerResponse payer = rest.get()
                    .uri("/internal/customers/{id}/payer", customerId)
                    .header(PURCHASE_ID, purchaseId.toString())
                    .retrieve()
                    .body(PayerResponse.class);
            return new Payer(payer.customerId(), payer.fullName(), payer.cpf(), payer.address().toAddress());
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(failure));
        }
    }

    @Override
    public TransferBuyer loadBuyer(UUID customerId, UUID purchaseId) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            TransferDataResponse buyer = rest.get()
                    .uri("/internal/customers/{id}/transfer-data", customerId)
                    .header(PURCHASE_ID, purchaseId.toString())
                    .retrieve()
                    .body(TransferDataResponse.class);
            return new TransferBuyer(buyer.customerId(), buyer.fullName(), buyer.cpf(), buyer.email(),
                    buyer.address().toAddress());
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(failure));
        }
    }

    @Override
    public boolean matches(UUID customerId, UUID purchaseId, String cpf) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            return rest.post()
                    .uri("/internal/customers/{id}/cpf-check", customerId)
                    .header(PURCHASE_ID, purchaseId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CpfCheckRequest(cpf))
                    .retrieve()
                    .body(CpfCheckResponse.class)
                    .matches();
        } catch (HttpClientErrorException.BadRequest refused) {
            throw invalidCpf(refused);
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(failure));
        }
    }

    /** O cadastro recusou o CPF apresentado: o erro dele vai ao vendedor como erro do campo {@code cpf}. */
    private static BusinessException invalidCpf(HttpClientErrorException.BadRequest refused) {
        List<FieldViolation> fields = new ArrayList<>();
        ErrorResponse error = refused.getResponseBodyAs(ErrorResponse.class);
        if (error != null && error.fields() != null) {
            error.fields().stream()
                    .filter(field -> "cpf".equals(field.field()))
                    .forEach(field -> fields.add(new FieldViolation("cpf", field.code(), field.message())));
        }
        if (fields.isEmpty()) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(refused));
        }
        return BusinessException.invalid(fields.toArray(FieldViolation[]::new));
    }

    record EligibilityResponse(UUID customerId, boolean eligible, Eligibility.Reason reason) {
    }

    record PayerResponse(UUID customerId, String fullName, String cpf, AddressResponse address) {

        @Override
        public String toString() {
            return "PayerResponse[customerId=" + customerId + "]";
        }
    }

    record TransferDataResponse(UUID customerId, String fullName, String cpf, String email, AddressResponse address) {

        @Override
        public String toString() {
            return "TransferDataResponse[customerId=" + customerId + "]";
        }
    }

    record AddressResponse(String zipCode, String street, String number, String complement, String district,
            String city, String state) {

        Payer.Address toAddress() {
            return new Payer.Address(zipCode, street, number, complement, district, city, state);
        }

        @Override
        public String toString() {
            return "AddressResponse[dados pessoais omitidos]";
        }
    }

    record CpfCheckRequest(String cpf) {

        @Override
        public String toString() {
            return "CpfCheckRequest[dados pessoais omitidos]";
        }
    }

    record CpfCheckResponse(boolean matches) {
    }

    record ErrorResponse(String code, List<FieldError> fields) {
    }

    record FieldError(String field, String code, String message) {
    }
}
