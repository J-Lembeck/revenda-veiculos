package com.revenda.vehicles.domain.model;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record Vehicle(
        UUID id,
        VehicleDetails details,
        VehicleStatus status,
        UUID purchaseId,
        Money soldPrice,
        Instant soldAt,
        Instant deliveredAt,
        int version,
        Instant createdAt,
        Instant updatedAt) {

    public static final String ENTITY_TYPE = "VEHICLE";

    /** Veículo novo no estoque: à venda, sem compra, na versão 0. */
    public static Vehicle register(UUID id, VehicleDetails details, Instant now) {
        details.requireValid(now.atZone(ZoneOffset.UTC).getYear());
        return new Vehicle(id, details, VehicleStatus.AVAILABLE, null, null, null, null, 0, now, now);
    }

    /** À venda: o único estado que o público vê (RN-03). */
    public boolean isForSale() {
        return status == VehicleStatus.AVAILABLE;
    }

    /** Reservado para esta compra: a reserva repetida por ela é sucesso sem efeito. */
    public boolean isReservedFor(UUID purchase) {
        return status == VehicleStatus.RESERVED && purchase.equals(purchaseId);
    }

    /**
     * Os dados novos na versão seguinte, sem tocar no status nem na compra (RN-02): só à venda ou reservado, e só a
     * partir da versão que está no estoque.
     */
    public Vehicle edit(VehicleDetails newDetails, int readVersion, Instant now) {
        newDetails.requireValid(now.atZone(ZoneOffset.UTC).getYear());
        if (status != VehicleStatus.AVAILABLE && status != VehicleStatus.RESERVED) {
            throw BusinessException.of(VehiclesError.VEHICLE_NOT_EDITABLE);
        }
        if (readVersion != version) {
            throw BusinessException.of(VehiclesError.VERSION_CONFLICT);
        }
        return new Vehicle(id, newDetails, status, purchaseId, soldPrice, soldAt, deliveredAt, version + 1, createdAt,
                now);
    }
}
