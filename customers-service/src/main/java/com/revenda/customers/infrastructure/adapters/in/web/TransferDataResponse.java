package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "O comprador no documento de transferência, completo. Quem recebe usa na hora e não grava.")
record TransferDataResponse(
        UUID customerId,
        @Schema(example = "Maria Exemplo da Silva") String fullName,
        @Schema(description = "Só os dígitos", example = "52998224725") String cpf,
        @Schema(description = "O e-mail da conta", example = "maria.exemplo@example.test") String email,
        AddressBody address) {

    static TransferDataResponse of(Customer customer) {
        CustomerDetails details = customer.details();
        return new TransferDataResponse(customer.id(), details.fullName(), details.cpf().digits(),
                customer.email().address(), AddressBody.of(details.address()));
    }

    @Override
    public String toString() {
        return "TransferDataResponse[customerId=" + customerId + "]";
    }
}
