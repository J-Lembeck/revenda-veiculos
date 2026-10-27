package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "O próprio cadastro. CPF, e-mail e telefone vêm sempre mascarados, também para o titular.")
record CustomerResponse(
        UUID id,
        @Schema(example = "Maria Exemplo da Silva") String fullName,
        @Schema(description = "Só os seis dígitos do meio", example = "***.982.247-**") String cpfMasked,
        @Schema(description = "O e-mail da conta: a primeira letra e o domínio", example = "m***@example.test")
        String emailMasked,
        @Schema(description = "O DDD e os quatro últimos dígitos", example = "(11) *****-0000") String phoneMasked,
        @Schema(example = "1990-05-17") LocalDate birthDate,
        AddressBody address,
        Instant createdAt,
        Instant updatedAt) {

    static CustomerResponse of(Customer customer) {
        CustomerDetails details = customer.details();
        return new CustomerResponse(customer.id(), details.fullName(), details.cpf().masked(),
                customer.email().masked(), details.phone().masked(), details.birthDate(),
                AddressBody.of(details.address()), customer.createdAt(), customer.updatedAt());
    }

    @Override
    public String toString() {
        return "CustomerResponse[id=" + id + "]";
    }
}
