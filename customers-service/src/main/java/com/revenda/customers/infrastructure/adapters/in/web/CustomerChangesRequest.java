package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.common.validation.NullOrNotBlank;
import com.revenda.customers.domain.model.CustomerDetails;
import com.revenda.customers.domain.model.Phone;
import com.revenda.customers.domain.ports.in.UpdateOwnCustomerCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "O que muda no próprio cadastro: o campo ausente (ou nulo) fica como está, e o endereço, se vier, "
        + "vem inteiro. CPF, nascimento e e-mail não mudam por aqui (RN-07).")
record CustomerChangesRequest(
        @Schema(example = "Maria Exemplo Souza", nullable = true)
        @NullOrNotBlank @Size(max = CustomerDetails.FULL_NAME_LIMIT) String fullName,
        @Schema(description = "+55, o DDD e o número, sem espaços", example = "+5511988887777", nullable = true)
        @NullOrNotBlank @Pattern(regexp = Phone.FORMAT, message = CustomerRequest.PHONE_FORMAT_MESSAGE) String phone,
        @Schema(description = "O endereço inteiro, com as regras do cadastro; o complemento que não vier fica vazio",
                nullable = true)
        @Valid AddressBody address,
        @Schema(hidden = true)
        @Null(message = "O CPF não muda pelo autoatendimento. Para corrigir, fale com a revenda.") Object cpf,
        @Schema(hidden = true)
        @Null(message = "A data de nascimento não muda pelo autoatendimento. Para corrigir, fale com a revenda.")
        Object birthDate,
        @Schema(hidden = true)
        @Null(message = "O e-mail é o da sua conta: mude-o na conta, e o cadastro acompanha na próxima edição.")
        Object email) {

    UpdateOwnCustomerCommand toCommand(String identitySubject, String accountEmail, boolean emailVerified) {
        return new UpdateOwnCustomerCommand(identitySubject, accountEmail, emailVerified, fullName, phone,
                address == null ? null : address.toAddress());
    }

    @Override
    public String toString() {
        return "CustomerChangesRequest[dados pessoais omitidos]";
    }
}
