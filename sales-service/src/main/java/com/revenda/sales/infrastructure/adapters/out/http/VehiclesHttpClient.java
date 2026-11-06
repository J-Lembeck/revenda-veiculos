package com.revenda.sales.infrastructure.adapters.out.http;

import com.revenda.common.money.Money;
import com.revenda.sales.domain.model.VehicleReservation;
import com.revenda.sales.domain.ports.out.ReserveVehiclePort;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** A reserva no estoque, com o token de serviço de scope {@code vehicles.reserve}. */
@Slf4j
@Component
class VehiclesHttpClient implements ReserveVehiclePort {

    private static final String NEIGHBOR = "estoque";

    private final RestClient rest;

    VehiclesHttpClient(@Qualifier("vehiclesRestClient") RestClient rest) {
        this.rest = rest;
    }

    @Override
    public VehicleReservation reserve(UUID vehicleId, UUID purchaseId) {
        NeighborCalls.requireNoTransaction(NEIGHBOR);
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
                    NeighborCalls.describe(failure));
            return new VehicleReservation.Unknown();
        }
    }

    record ReservationRequest(UUID purchaseId) {
    }

    record ReservationResponse(long priceCents) {
    }
}
