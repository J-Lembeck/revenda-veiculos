package com.revenda.vehicles.domain.model;

import com.revenda.common.error.ErrorCode;
import com.revenda.common.error.ErrorKind;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VehiclesError implements ErrorCode {
    PLATE_ALREADY_REGISTERED(ErrorKind.CONFLICT,
            "Já existe um veículo com esta placa no estoque. Confira a placa ou edite o veículo existente."),
    VIN_ALREADY_REGISTERED(ErrorKind.CONFLICT,
            "Já existe um veículo com este chassi no estoque. Confira o chassi ou edite o veículo existente."),
    RENAVAM_ALREADY_REGISTERED(ErrorKind.CONFLICT,
            "Já existe um veículo com este RENAVAM no estoque. Confira o RENAVAM ou edite o veículo existente."),
    VEHICLE_NOT_EDITABLE(ErrorKind.CONFLICT,
            "Este veículo já foi vendido e não pode mais ser editado."),
    VERSION_CONFLICT(ErrorKind.CONFLICT,
            "Este veículo foi alterado por outra pessoa depois que você o abriu. Abra de novo e refaça a edição.");

    private final ErrorKind kind;
    private final String message;
}
