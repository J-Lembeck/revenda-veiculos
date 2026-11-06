package com.revenda.vehicles.application.service;

/** A origem gravada em {@code processed_message} pelos comandos que a compra manda ao estoque pela fila. */
final class VehicleCommandSource {

    static final String VEHICLE_COMMANDS = "vehicle-commands";

    private VehicleCommandSource() {
    }
}
