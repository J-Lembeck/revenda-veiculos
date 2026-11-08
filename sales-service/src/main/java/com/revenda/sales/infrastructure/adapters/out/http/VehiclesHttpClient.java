package com.revenda.sales.infrastructure.adapters.out.http;

import com.revenda.common.http.ServiceCalls;
import com.revenda.common.money.Money;
import com.revenda.common.error.DependencyUnavailableException;
import com.revenda.sales.domain.model.TransferVehicle;
import com.revenda.sales.domain.model.VehicleReservation;
import com.revenda.sales.domain.ports.out.LoadTransferVehiclePort;
import com.revenda.sales.domain.ports.out.ReserveVehiclePort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * O estoque, com o token de serviço: a reserva (scope {@code vehicles.reserve}) e a leitura do veículo para o documento
 * de transferência (scope {@code vehicles.read}).
 */
@Slf4j
@Component
class VehiclesHttpClient implements ReserveVehiclePort, LoadTransferVehiclePort {

    private static final String NEIGHBOR = "estoque";

    private final RestClient rest;

    VehiclesHttpClient(@Qualifier("vehiclesRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Override
    public VehicleReservation reserve(UUID vehicleId, UUID purchaseId) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            ReservationResponse reserved = rest.post()
                    .uri("/internal/vehicles/{id}/reservations", vehicleId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReservationRequest(purchaseId))
                    .retrieve()
                    .body(ReservationResponse.class);
            return new VehicleReservation.Reserved(Money.ofCents(reserved.priceCents()));
        } catch (HttpClientErrorException.Conflict unavailable) {
            return new VehicleReservation.Unavailable();
        } catch (RuntimeException failure) {
            log.warn("Reserva do veículo {} para a compra {} sem resposta: {}", vehicleId, purchaseId,
                    ServiceCalls.describe(failure));
            return new VehicleReservation.Unknown();
        }
    }

    @Override
    public TransferVehicle load(UUID vehicleId) {
        ServiceCalls.requireNoTransaction(NEIGHBOR);
        try {
            VehicleResponse vehicle = rest.get()
                    .uri("/internal/vehicles/{id}", vehicleId)
                    .retrieve()
                    .body(VehicleResponse.class);
            return new TransferVehicle(vehicle.id(), vehicle.brand(), vehicle.model(), vehicle.trimName(),
                    vehicle.manufactureYear(), vehicle.modelYear(), vehicle.color(), vehicle.mileageKm(),
                    vehicle.plate(), vehicle.vin(), vehicle.renavam());
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException(NEIGHBOR, ServiceCalls.describe(failure));
        }
    }

    record ReservationRequest(UUID purchaseId) {
    }

    record ReservationResponse(long priceCents) {
    }

    record VehicleResponse(UUID id, String brand, String model, String trimName, int manufactureYear, int modelYear,
            String color, int mileageKm, String plate, String vin, String renavam) {
    }
}
