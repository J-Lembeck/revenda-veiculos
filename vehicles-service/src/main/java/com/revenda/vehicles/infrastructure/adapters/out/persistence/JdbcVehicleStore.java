package com.revenda.vehicles.infrastructure.adapters.out.persistence;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import com.revenda.common.time.SqlTime;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import com.revenda.vehicles.domain.model.VehicleStatus;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcVehicleStore implements SaveVehiclePort, LoadVehiclePort {

    private static final Map<String, VehiclesError> UNIQUE_KEYS = Map.of(
            "vehicle_plate_key", VehiclesError.PLATE_ALREADY_REGISTERED,
            "vehicle_vin_key", VehiclesError.VIN_ALREADY_REGISTERED,
            "vehicle_renavam_key", VehiclesError.RENAVAM_ALREADY_REGISTERED);

    private final JdbcClient jdbc;

    @Override
    public void insert(Vehicle vehicle) {
        StatementSpec insert = jdbc.sql("""
                        INSERT INTO vehicle (id, brand, model, trim_name, manufacture_year, model_year, color,
                                             mileage_km, plate, vin, renavam, price_cents, status, version,
                                             created_at, updated_at)
                        VALUES (:id, :brand, :model, :trimName, :manufactureYear, :modelYear, :color,
                                :mileageKm, :plate, :vin, :renavam, :priceCents, :status, :version,
                                :createdAt, :updatedAt)""")
                .param("id", vehicle.id())
                .param("status", vehicle.status().name())
                .param("version", vehicle.version())
                .param("createdAt", SqlTime.utc(vehicle.createdAt()))
                .param("updatedAt", SqlTime.utc(vehicle.updatedAt()));
        refusingDuplicates(() -> withDetails(insert, vehicle.details()).update());
    }

    @Override
    public boolean updateDetails(Vehicle edited) {
        StatementSpec update = jdbc.sql("""
                        UPDATE vehicle
                           SET brand = :brand, model = :model, trim_name = :trimName,
                               manufacture_year = :manufactureYear, model_year = :modelYear, color = :color,
                               mileage_km = :mileageKm, plate = :plate, vin = :vin, renavam = :renavam,
                               price_cents = :priceCents, version = version + 1, updated_at = :now
                         WHERE id = :id AND version = :readVersion AND status IN ('AVAILABLE', 'RESERVED')""")
                .param("id", edited.id())
                .param("readVersion", edited.version() - 1)
                .param("now", SqlTime.utc(edited.updatedAt()));
        return refusingDuplicates(() -> withDetails(update, edited.details()).update()) == 1;
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return jdbc.sql("""
                        SELECT id, brand, model, trim_name, manufacture_year, model_year, color, mileage_km, plate,
                               vin, renavam, price_cents, status, purchase_id, sold_price_cents, sold_at,
                               delivered_at, version, created_at, updated_at
                          FROM vehicle
                         WHERE id = :id""")
                .param("id", id)
                .query((row, n) -> vehicle(row))
                .optional();
    }

    private static StatementSpec withDetails(StatementSpec statement, VehicleDetails details) {
        return statement
                .param("brand", details.brand())
                .param("model", details.model())
                .param("trimName", details.trimName())
                .param("manufactureYear", details.manufactureYear())
                .param("modelYear", details.modelYear())
                .param("color", details.color())
                .param("mileageKm", details.mileageKm())
                .param("plate", details.plate())
                .param("vin", details.vin())
                .param("renavam", details.renavam())
                .param("priceCents", details.price().toCents());
    }

    private static Vehicle vehicle(ResultSet row) throws SQLException {
        VehicleDetails details = new VehicleDetails(row.getString("brand"), row.getString("model"),
                row.getString("trim_name"), row.getInt("manufacture_year"), row.getInt("model_year"),
                row.getString("color"), row.getInt("mileage_km"), row.getString("plate"), row.getString("vin"),
                row.getString("renavam"), Money.ofCents(row.getLong("price_cents")));
        long soldPriceCents = row.getLong("sold_price_cents");
        Money soldPrice = row.wasNull() ? null : Money.ofCents(soldPriceCents);
        return new Vehicle(row.getObject("id", UUID.class), details, VehicleStatus.valueOf(row.getString("status")),
                row.getObject("purchase_id", UUID.class), soldPrice, SqlTime.instantOrNull(row, "sold_at"),
                SqlTime.instantOrNull(row, "delivered_at"), row.getInt("version"),
                SqlTime.instantOrNull(row, "created_at"), SqlTime.instantOrNull(row, "updated_at"));
    }

    private static <T> T refusingDuplicates(Supplier<T> statement) {
        try {
            return statement.get();
        } catch (DuplicateKeyException e) {
            VehiclesError duplicate = UNIQUE_KEYS.get(constraintOf(e));
            if (duplicate == null) {
                throw e;
            }
            throw BusinessException.of(duplicate);
        }
    }

    private static String constraintOf(DuplicateKeyException e) {
        if (e.getMostSpecificCause() instanceof PSQLException psql) {
            ServerErrorMessage server = psql.getServerErrorMessage();
            return server == null ? null : server.getConstraint();
        }
        return null;
    }
}
