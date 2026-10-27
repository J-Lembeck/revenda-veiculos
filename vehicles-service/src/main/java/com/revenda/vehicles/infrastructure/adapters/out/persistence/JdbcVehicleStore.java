package com.revenda.vehicles.infrastructure.adapters.out.persistence;

import com.revenda.common.error.BusinessException;
import com.revenda.common.time.SqlTime;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcVehicleStore implements SaveVehiclePort {

    private static final Map<String, VehiclesError> UNIQUE_KEYS = Map.of(
            "vehicle_plate_key", VehiclesError.PLATE_ALREADY_REGISTERED,
            "vehicle_vin_key", VehiclesError.VIN_ALREADY_REGISTERED,
            "vehicle_renavam_key", VehiclesError.RENAVAM_ALREADY_REGISTERED);

    private final JdbcClient jdbc;

    @Override
    public void insert(Vehicle vehicle) {
        VehicleDetails details = vehicle.details();
        try {
            jdbc.sql("""
                            INSERT INTO vehicle (id, brand, model, trim_name, manufacture_year, model_year, color,
                                                 mileage_km, plate, vin, renavam, price_cents, status, version,
                                                 created_at, updated_at)
                            VALUES (:id, :brand, :model, :trimName, :manufactureYear, :modelYear, :color,
                                    :mileageKm, :plate, :vin, :renavam, :priceCents, :status, :version,
                                    :createdAt, :updatedAt)""")
                    .param("id", vehicle.id())
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
                    .param("priceCents", details.price().toCents())
                    .param("status", vehicle.status().name())
                    .param("version", vehicle.version())
                    .param("createdAt", SqlTime.utc(vehicle.createdAt()))
                    .param("updatedAt", SqlTime.utc(vehicle.updatedAt()))
                    .update();
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
