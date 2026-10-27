package com.revenda.vehicles.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VehicleTest {

    private static final Instant CREATED = Instant.parse("2026-10-20T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final UUID PURCHASE = UUID.randomUUID();

    @Test
    void editingAnAvailableVehicleTakesTheNewDetailsInTheNextVersion() {
        Vehicle vehicle = vehicle(VehicleStatus.AVAILABLE, 3);
        VehicleDetails newDetails = details("Grafite", 9_490_000);

        Vehicle edited = vehicle.edit(newDetails, 3, NOW);

        assertThat(edited.details()).isEqualTo(newDetails);
        assertThat(edited.version()).isEqualTo(4);
        assertThat(edited.status()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(edited.id()).isEqualTo(vehicle.id());
        assertThat(edited.createdAt()).isEqualTo(CREATED);
        assertThat(edited.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void editingAReservedVehicleKeepsTheStatusAndThePurchase() {
        Vehicle vehicle = vehicle(VehicleStatus.RESERVED, 1);

        Vehicle edited = vehicle.edit(details("Prata", 7_990_000), 1, NOW);

        assertThat(edited.status()).isEqualTo(VehicleStatus.RESERVED);
        assertThat(edited.purchaseId()).isEqualTo(PURCHASE);
        assertThat(edited.details().price()).isEqualTo(Money.ofCents(7_990_000));
    }

    @ParameterizedTest
    @EnumSource(value = VehicleStatus.class, names = {"SOLD", "DELIVERED"})
    void aSoldOrDeliveredVehicleIsNotEditable(VehicleStatus status) {
        Vehicle vehicle = vehicle(status, 2);

        assertThatThrownBy(() -> vehicle.edit(details("Prata", 8_990_000), 2, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("VEHICLE_NOT_EDITABLE");
    }

    @Test
    void anOldVersionIsAConflict() {
        Vehicle vehicle = vehicle(VehicleStatus.AVAILABLE, 5);

        assertThatThrownBy(() -> vehicle.edit(details("Prata", 8_990_000), 4, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void theNewDetailsFollowRn01() {
        Vehicle vehicle = vehicle(VehicleStatus.AVAILABLE, 0);

        assertThatThrownBy(() -> vehicle.edit(details("Prata", 0), 0, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void onlyAReservationOfThisPurchaseCountsAsReservedForIt() {
        assertThat(vehicle(VehicleStatus.RESERVED, 1).isReservedFor(PURCHASE)).isTrue();
        assertThat(vehicle(VehicleStatus.RESERVED, 1).isReservedFor(UUID.randomUUID())).isFalse();
        assertThat(vehicle(VehicleStatus.SOLD, 2).isReservedFor(PURCHASE)).isFalse();
        assertThat(vehicle(VehicleStatus.AVAILABLE, 0).isReservedFor(PURCHASE)).isFalse();
    }

    @Test
    void soldToThisPurchaseIncludesTheDeliveredVehicle() {
        assertThat(vehicle(VehicleStatus.SOLD, 2).isSoldTo(PURCHASE)).isTrue();
        assertThat(vehicle(VehicleStatus.DELIVERED, 3).isSoldTo(PURCHASE)).isTrue();
        assertThat(vehicle(VehicleStatus.SOLD, 2).isSoldTo(UUID.randomUUID())).isFalse();
        assertThat(vehicle(VehicleStatus.RESERVED, 1).isSoldTo(PURCHASE)).isFalse();
        assertThat(vehicle(VehicleStatus.AVAILABLE, 0).isSoldTo(PURCHASE)).isFalse();
    }

    @Test
    void onlyTheDeliveryToThisPurchaseCountsAsDeliveredToIt() {
        assertThat(vehicle(VehicleStatus.DELIVERED, 3).isDeliveredTo(PURCHASE)).isTrue();
        assertThat(vehicle(VehicleStatus.DELIVERED, 3).isDeliveredTo(UUID.randomUUID())).isFalse();
        assertThat(vehicle(VehicleStatus.SOLD, 2).isDeliveredTo(PURCHASE)).isFalse();
    }

    private static Vehicle vehicle(VehicleStatus status, int version) {
        boolean withPurchase = status != VehicleStatus.AVAILABLE;
        boolean sold = status == VehicleStatus.SOLD || status == VehicleStatus.DELIVERED;
        return new Vehicle(UUID.randomUUID(), details("Prata", 8_990_000), status, withPurchase ? PURCHASE : null,
                sold ? Money.ofCents(8_990_000) : null, sold ? CREATED : null,
                status == VehicleStatus.DELIVERED ? CREATED : null, version, CREATED, CREATED);
    }

    private static VehicleDetails details(String color, long priceCents) {
        return new VehicleDetails("Chevrolet", "Onix", "1.0 Turbo LT", 2023, 2024, color, 18_500, "BRA2E19",
                "9BGEA48A0PG000001", "00639884962", Money.ofCents(priceCents));
    }
}
