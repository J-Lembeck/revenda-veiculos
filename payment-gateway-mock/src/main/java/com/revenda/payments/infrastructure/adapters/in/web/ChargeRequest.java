package com.revenda.payments.infrastructure.adapters.in.web;

import com.revenda.common.money.Money;
import com.revenda.common.text.Texts;
import com.revenda.payments.domain.model.Payer;
import com.revenda.payments.domain.model.PayerAddress;
import com.revenda.payments.domain.ports.in.CreateChargeCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** O pedido de cobrança do {@code sales}: a compra como referência, o valor em centavos, o vencimento e o pagador. */
record ChargeRequest(
        @NotBlank @Size(max = CreateChargeCommand.REFERENCE_LIMIT) String referenceId,
        @NotNull @Positive Long amountCents,
        @NotNull Instant dueAt,
        @NotNull @Valid PayerBody payer) {

    CreateChargeCommand toCommand() {
        return new CreateChargeCommand(referenceId.strip(), Money.ofCents(amountCents), dueAt, payer.toPayer());
    }

    @Override
    public String toString() {
        return "ChargeRequest[referenceId=" + referenceId + ", amountCents=" + amountCents + "]";
    }

    record PayerBody(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Pattern(regexp = "[0-9]{11}", message = "O CPF do pagador tem 11 dígitos, sem pontos nem traço.")
            String cpf,
            @NotNull @Valid AddressBody address) {

        Payer toPayer() {
            return new Payer(name.strip(), cpf, address.toAddress());
        }

        @Override
        public String toString() {
            return "PayerBody[dados pessoais omitidos]";
        }
    }

    record AddressBody(
            @NotBlank @Pattern(regexp = "[0-9]{8}", message = "O CEP tem 8 dígitos, sem traço.") String zipCode,
            @NotBlank @Size(max = 120) String street,
            @NotBlank @Size(max = 10) String number,
            @Size(max = 60) String complement,
            @NotBlank @Size(max = 60) String district,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "Use a sigla da UF em maiúsculas, como SP.") String state) {

        PayerAddress toAddress() {
            return new PayerAddress(zipCode, street.strip(), number.strip(), Texts.blankToNull(complement),
                    district.strip(), city.strip(), state);
        }

        @Override
        public String toString() {
            return "AddressBody[dados pessoais omitidos]";
        }
    }
}
