package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "O pagador da cobrança, completo. Quem recebe usa na hora e não grava.")
record PayerResponse(
        UUID customerId,
        @Schema(example = "Maria Exemplo da Silva") String fullName,
        @Schema(description = "Só os dígitos", example = "52998224725") String cpf,
        AddressBody address) {

    static PayerResponse of(Customer customer) {
        CustomerDetails details = customer.details();
        return new PayerResponse(customer.id(), details.fullName(), details.cpf().digits(),
                AddressBody.of(details.address()));
    }

    @Override
    public String toString() {
        return "PayerResponse[customerId=" + customerId + "]";
    }
}
