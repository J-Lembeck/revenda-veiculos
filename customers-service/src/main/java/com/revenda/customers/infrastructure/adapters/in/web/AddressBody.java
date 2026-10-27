package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.common.text.Texts;
import com.revenda.customers.domain.model.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Endereço do comprador, para a cobrança e o documento.")
record AddressBody(
        @Schema(description = "CEP, 8 dígitos sem traço", example = "01001000")
        @NotBlank @Pattern(regexp = Address.ZIP_CODE_FORMAT, message = "O CEP tem 8 dígitos, sem traço.")
        String zipCode,
        @Schema(example = "Praça da Sé") @NotBlank @Size(max = Address.STREET_LIMIT) String street,
        @Schema(example = "100") @NotBlank @Size(max = Address.NUMBER_LIMIT) String number,
        @Schema(description = "Opcional", example = "apto 1", nullable = true)
        @Size(max = Address.COMPLEMENT_LIMIT) String complement,
        @Schema(example = "Sé") @NotBlank @Size(max = Address.DISTRICT_LIMIT) String district,
        @Schema(example = "São Paulo") @NotBlank @Size(max = Address.CITY_LIMIT) String city,
        @Schema(description = "Sigla da UF em maiúsculas", example = "SP")
        @NotBlank @Pattern(regexp = Address.STATE_FORMAT, message = "Use a sigla da UF em maiúsculas, como SP.")
        String state) {

    static AddressBody of(Address address) {
        return new AddressBody(address.zipCode(), address.street(), address.number(), address.complement(),
                address.district(), address.city(), address.state());
    }

    Address toAddress() {
        return new Address(zipCode, street.strip(), number.strip(), Texts.blankToNull(complement), district.strip(),
                city.strip(), state);
    }

    @Override
    public String toString() {
        return "AddressBody[dados pessoais omitidos]";
    }
}
