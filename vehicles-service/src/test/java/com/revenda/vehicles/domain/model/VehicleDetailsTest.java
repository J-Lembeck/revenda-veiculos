package com.revenda.vehicles.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.FieldViolation;
import com.revenda.common.money.Money;
import com.revenda.common.test.SyntheticData;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VehicleDetailsTest {

    private static final int CURRENT_YEAR = 2026;

    @Test
    void aVehicleWithinTheRulesPasses() {
        assertThatCode(() -> details().requireValid(CURRENT_YEAR)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC1234", "BRA2E19", "XYZ9Z99"})
    void acceptsOldAndMercosulPlates(String plate) {
        assertThat(violations(with(plate, null, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC-1234", "abc1d23", "AB12345", "ABC12345", "ABCD123", "ABC1D2", " ABC1234"})
    void refusesPlatesOutsideTheTwoPatterns(String plate) {
        assertThat(violations(with(plate, null, null))).containsExactly("plate PLATE_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9BGEA48A0PG00000", "9BGEA48A0PG0000011", "9BGEA48I0PG000001", "9BGEA48O0PG000001",
            "9BGEA48Q0PG000001", "9bgea48a0pg000001"})
    void refusesVinsThatAreNot17CharactersWithoutIOQ(String vin) {
        assertThat(violations(with(null, vin, null))).containsExactly("vin VIN_INVALID");
    }

    @Test
    void acceptsRenavamWithTheKnownCheckDigits() {
        assertThat(violations(with(null, null, "00639884962"))).isEmpty();
        assertThat(violations(with(null, null, "00000000019"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00639884961", "00000000001", "0063988496", "006398849620", "0063988496A"})
    void refusesRenavamWithAWrongCheckDigitOrLength(String renavam) {
        assertThat(violations(with(null, null, renavam))).containsExactly("renavam RENAVAM_INVALID");
    }

    @RepeatedTest(100)
    void acceptsEverySyntheticRenavam() {
        assertThat(violations(with(SyntheticData.plate(), SyntheticData.vin(), SyntheticData.renavam()))).isEmpty();
    }

    @Test
    void manufactureYearGoesFrom1900ToNextYear() {
        assertThat(violations(years(1900, 1900))).isEmpty();
        assertThat(violations(years(CURRENT_YEAR + 1, CURRENT_YEAR + 1))).isEmpty();
        assertThat(violations(years(1899, 1899))).containsExactly("manufactureYear YEAR_INVALID");
        assertThat(violations(years(CURRENT_YEAR + 2, CURRENT_YEAR + 2))).containsExactly("manufactureYear YEAR_INVALID");
    }

    @Test
    void modelYearIsTheManufactureYearOrTheNextOne() {
        assertThat(violations(years(2023, 2023))).isEmpty();
        assertThat(violations(years(2023, 2024))).isEmpty();
        assertThat(violations(years(2023, 2025))).containsExactly("modelYear YEAR_INVALID");
        assertThat(violations(years(2023, 2022))).containsExactly("modelYear YEAR_INVALID");
    }

    @Test
    void priceIsAboveZeroAndUpToTenMillion() {
        assertThat(violations(price(1))).isEmpty();
        assertThat(violations(price(1_000_000_000L))).isEmpty();
        assertThat(violations(price(0))).containsExactly("priceCents PRICE_INVALID");
        assertThat(violations(price(-100))).containsExactly("priceCents PRICE_INVALID");
        assertThat(violations(price(1_000_000_001L))).containsExactly("priceCents PRICE_INVALID");
    }

    @Test
    void mileageIsNeverNegative() {
        assertThat(violations(mileage(0))).isEmpty();
        assertThat(violations(mileage(-1))).containsExactly("mileageKm OUT_OF_RANGE");
    }

    @Test
    void everyBrokenFieldIsReportedAtOnce() {
        VehicleDetails broken = new VehicleDetails("Fiat", "Argo", null, 1899, 1899, "Branco", -5, "ABC-1234",
                "SHORT", "123", Money.ZERO);

        assertThat(violations(broken)).containsExactly("plate PLATE_INVALID", "vin VIN_INVALID",
                "renavam RENAVAM_INVALID", "manufactureYear YEAR_INVALID", "mileageKm OUT_OF_RANGE",
                "priceCents PRICE_INVALID");
    }

    @Test
    void registeringStartsAvailableWithoutPurchaseAtVersionZero() {
        Instant now = Instant.parse("2026-10-26T12:00:00Z");
        VehicleDetails details = details();

        Vehicle vehicle = Vehicle.register(UUID.randomUUID(), details, now);

        assertThat(vehicle.status()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(vehicle.purchaseId()).isNull();
        assertThat(vehicle.soldPrice()).isNull();
        assertThat(vehicle.version()).isZero();
        assertThat(vehicle.createdAt()).isEqualTo(now).isEqualTo(vehicle.updatedAt());
        assertThat(vehicle.details()).isEqualTo(details);
    }

    @Test
    void registeringUsesTheYearOfTheInstantForTheLimit() {
        VehicleDetails nextYear = years(2028, 2028);

        assertThatThrownBy(() -> Vehicle.register(UUID.randomUUID(), nextYear, Instant.parse("2026-12-31T23:00:00Z")))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> Vehicle.register(UUID.randomUUID(), nextYear, Instant.parse("2027-01-01T00:00:00Z")))
                .doesNotThrowAnyException();
    }

    private static List<String> violations(VehicleDetails details) {
        try {
            details.requireValid(CURRENT_YEAR);
            return List.of();
        } catch (BusinessException e) {
            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
            return e.getFields().stream().map(FieldViolation::field).map(field -> field + " " + code(e, field)).toList();
        }
    }

    private static String code(BusinessException e, String field) {
        return e.getFields().stream().filter(f -> f.field().equals(field)).findFirst().orElseThrow().code();
    }

    private static VehicleDetails with(String plate, String vin, String renavam) {
        VehicleDetails base = details();
        return new VehicleDetails(base.brand(), base.model(), base.trimName(), base.manufactureYear(), base.modelYear(),
                base.color(), base.mileageKm(), plate == null ? base.plate() : plate, vin == null ? base.vin() : vin,
                renavam == null ? base.renavam() : renavam, base.price());
    }

    private static VehicleDetails years(int manufacture, int model) {
        VehicleDetails base = details();
        return new VehicleDetails(base.brand(), base.model(), base.trimName(), manufacture, model, base.color(),
                base.mileageKm(), base.plate(), base.vin(), base.renavam(), base.price());
    }

    private static VehicleDetails price(long cents) {
        VehicleDetails base = details();
        return new VehicleDetails(base.brand(), base.model(), base.trimName(), base.manufactureYear(), base.modelYear(),
                base.color(), base.mileageKm(), base.plate(), base.vin(), base.renavam(), Money.ofCents(cents));
    }

    private static VehicleDetails mileage(int km) {
        VehicleDetails base = details();
        return new VehicleDetails(base.brand(), base.model(), base.trimName(), base.manufactureYear(), base.modelYear(),
                base.color(), km, base.plate(), base.vin(), base.renavam(), base.price());
    }

    private static VehicleDetails details() {
        return new VehicleDetails("Chevrolet", "Onix", "1.0 Turbo LT", 2023, 2024, "Prata", 18_500, "BRA2E19",
                "9BGEA48A0PG000001", "00639884962", Money.ofCents(8_990_000));
    }
}
