package com.revenda.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

class BusinessExceptionTests {

    @Getter
    @RequiredArgsConstructor
    enum StockError implements ErrorCode {
        VEHICLE_NOT_EDITABLE(ErrorKind.CONFLICT, "Veículo vendido ou entregue não pode ser editado.");

        private final ErrorKind kind;
        private final String message;
    }

    static class VehicleNotEditableException extends BusinessException {

        VehicleNotEditableException() {
            super(StockError.VEHICLE_NOT_EDITABLE, StockError.VEHICLE_NOT_EDITABLE.getMessage(), List.of());
        }
    }

    @Test
    void carriesTheCodeKindAndMessageOfItsError() {
        BusinessException ex = BusinessException.of(StockError.VEHICLE_NOT_EDITABLE);

        assertThat(ex.getCode()).isEqualTo("VEHICLE_NOT_EDITABLE");
        assertThat(ex.getKind()).isEqualTo(ErrorKind.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("Veículo vendido ou entregue não pode ser editado.");
        assertThat(ex.getFields()).isEmpty();
    }

    @Test
    void acceptsAMoreSpecificMessage() {
        BusinessException ex = BusinessException.of(StockError.VEHICLE_NOT_EDITABLE, "Este veículo já foi entregue.");

        assertThat(ex.getCode()).isEqualTo("VEHICLE_NOT_EDITABLE");
        assertThat(ex.getMessage()).isEqualTo("Este veículo já foi entregue.");
    }

    @Test
    void invalidFieldIsAValidationFailureWithTheField() {
        BusinessException ex = BusinessException.invalidField("plate", "PLATE_INVALID", "Placa inválida.");

        assertThat(ex.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(ex.getKind()).isEqualTo(ErrorKind.INVALID);
        assertThat(ex.getFields()).containsExactly(new FieldViolation("plate", "PLATE_INVALID", "Placa inválida."));
        assertThatThrownBy(() -> ex.getFields().add(new FieldViolation("vin", "VIN_INVALID", "Chassi inválido.")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void notFoundSaysNothingAboutWhoOwnsTheResource() {
        BusinessException ex = BusinessException.notFound();

        assertThat(ex.getCode()).isEqualTo("NOT_FOUND");
        assertThat(ex.getKind()).isEqualTo(ErrorKind.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Não encontramos o que você procurou.");
    }

    @Test
    void servicesExtendItWithTheirOwnCodes() {
        assertThat(new VehicleNotEditableException())
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException::getCode)
                .isEqualTo("VEHICLE_NOT_EDITABLE");
    }
}
