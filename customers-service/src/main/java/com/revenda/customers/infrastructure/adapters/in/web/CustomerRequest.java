package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.CustomerDetails;
import com.revenda.customers.domain.model.Phone;
import com.revenda.customers.domain.ports.in.RegisterCustomerCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "O cadastro do comprador. O e-mail não vai no corpo: vem da conta, pelo token.")
record CustomerRequest(
        @Schema(example = "Maria Exemplo da Silva")
        @NotBlank @Size(max = CustomerDetails.FULL_NAME_LIMIT) String fullName,
        @Schema(description = "11 dígitos, só números ou com pontos e traço; os dois últimos conferem os anteriores",
                example = "52998224725")
        @NotBlank String cpf,
        @Schema(description = "De 1900 até ontem. Menor de idade se cadastra; a compra é que exige 18 anos (RN-05)",
                example = "1990-05-17")
        @NotNull LocalDate birthDate,
        @Schema(description = "+55, o DDD e o número, sem espaços", example = "+5511999990000")
        @NotBlank @Pattern(regexp = Phone.FORMAT, message = PHONE_FORMAT_MESSAGE) String phone,
        @Valid @NotNull AddressBody address) {

    static final String PHONE_FORMAT_MESSAGE = "Use +55, o DDD e o número, só com dígitos: +5511999990000.";

    RegisterCustomerCommand toCommand(String identitySubject, String email, boolean emailVerified) {
        return new RegisterCustomerCommand(identitySubject, email, emailVerified, fullName, cpf.strip(), birthDate, phone,
                address.toAddress());
    }

    @Override
    public String toString() {
        return "CustomerRequest[dados pessoais omitidos]";
    }
}
