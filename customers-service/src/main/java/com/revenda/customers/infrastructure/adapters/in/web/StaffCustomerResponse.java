package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "O cliente visto pela equipe: o mínimo para atender. CPF, e-mail e telefone mascarados; do endereço, "
        + "só cidade e UF; sem data de nascimento.")
record StaffCustomerResponse(
        UUID id,
        @Schema(example = "Maria Exemplo da Silva") String fullName,
        @Schema(description = "Só os seis dígitos do meio", example = "***.982.247-**") String cpfMasked,
        @Schema(description = "A primeira letra e o domínio", example = "m***@example.test") String emailMasked,
        @Schema(description = "O DDD e os quatro últimos dígitos", example = "(11) *****-0000") String phoneMasked,
        @Schema(example = "São Paulo") String city,
        @Schema(example = "SP") String state,
        @Schema(example = "ACTIVE") String status,
        Instant createdAt,
        Instant updatedAt) {

    static StaffCustomerResponse of(Customer customer) {
        CustomerDetails details = customer.details();
        return new StaffCustomerResponse(customer.id(), details.fullName(), details.cpf().masked(),
                customer.email().masked(), details.phone().masked(), details.address().city(),
                details.address().state(), customer.status().name(), customer.createdAt(), customer.updatedAt());
    }

    @Override
    public String toString() {
        return "StaffCustomerResponse[id=" + id + "]";
    }
}
