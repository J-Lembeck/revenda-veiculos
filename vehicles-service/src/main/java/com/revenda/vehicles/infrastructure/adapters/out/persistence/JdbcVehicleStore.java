package com.revenda.vehicles.infrastructure.adapters.out.persistence;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import com.revenda.common.time.SqlTime;
import com.revenda.vehicles.domain.model.Page;
import com.revenda.vehicles.domain.model.Paging;
import com.revenda.vehicles.domain.model.ShowcaseFilter;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.model.VehicleDetails;
import com.revenda.vehicles.domain.model.VehicleStatus;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.out.LoadVehiclePort;
import com.revenda.vehicles.domain.ports.out.SaveVehiclePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private static final String SELECT_VEHICLE = """
            SELECT id, brand, model, trim_name, manufacture_year, model_year, color, mileage_km, plate, vin, renavam,
                   price_cents, status, purchase_id, sold_price_cents, sold_at, delivered_at, version, created_at,
                   updated_at
              FROM vehicle""";

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
    public Optional<Money> reserveIfAvailable(UUID id, UUID purchaseId, Instant now) {
        return jdbc.sql("""
                        UPDATE vehicle
                           SET status = 'RESERVED', purchase_id = :purchaseId, version = version + 1, updated_at = :now
                         WHERE id = :id AND status = 'AVAILABLE'
                        RETURNING price_cents""")
                .param("id", id)
                .param("purchaseId", purchaseId)
                .param("now", SqlTime.utc(now))
                .query(Long.class)
                .optional()
                .map(Money::ofCents);
    }

    @Override
    public boolean releaseIfHeldBy(UUID id, UUID purchaseId, Instant now) {
        return jdbc.sql("""
                        UPDATE vehicle
                           SET status = 'AVAILABLE', purchase_id = NULL, sold_price_cents = NULL, sold_at = NULL,
                               version = version + 1, updated_at = :now
                         WHERE id = :id AND purchase_id = :purchaseId AND status IN ('RESERVED', 'SOLD')""")
                .param("id", id)
                .param("purchaseId", purchaseId)
                .param("now", SqlTime.utc(now))
                .update() == 1;
    }

    @Override
    public boolean markSoldIfReservedBy(UUID id, UUID purchaseId, Money soldPrice, Instant soldAt, Instant now) {
        return jdbc.sql("""
                        UPDATE vehicle
                           SET status = 'SOLD', sold_price_cents = :soldPriceCents, sold_at = :soldAt,
                               version = version + 1, updated_at = :now
                         WHERE id = :id AND purchase_id = :purchaseId AND status = 'RESERVED'""")
                .param("id", id)
                .param("purchaseId", purchaseId)
                .param("soldPriceCents", soldPrice.toCents())
                .param("soldAt", SqlTime.utc(soldAt))
                .param("now", SqlTime.utc(now))
                .update() == 1;
    }

    @Override
    public boolean markDeliveredIfSoldTo(UUID id, UUID purchaseId, Instant deliveredAt, Instant now) {
        return jdbc.sql("""
                        UPDATE vehicle
                           SET status = 'DELIVERED', delivered_at = :deliveredAt, version = version + 1, updated_at = :now
                         WHERE id = :id AND purchase_id = :purchaseId AND status = 'SOLD'""")
                .param("id", id)
                .param("purchaseId", purchaseId)
                .param("deliveredAt", SqlTime.utc(deliveredAt))
                .param("now", SqlTime.utc(now))
                .update() == 1;
    }

    @Override
    public Optional<Vehicle> findById(UUID id) {
        return jdbc.sql(SELECT_VEHICLE + " WHERE id = :id")
                .param("id", id)
                .query((row, n) -> vehicle(row))
                .optional();
    }

    @Override
    public Page<Vehicle> findForSale(ShowcaseFilter filter, Paging paging) {
        List<String> where = new ArrayList<>(List.of("status = 'AVAILABLE'"));
        Map<String, Object> params = new HashMap<>();
        if (filter.brand() != null) {
            where.add("lower(brand) = lower(:brand)");
            params.put("brand", filter.brand());
        }
        if (filter.model() != null) {
            where.add("lower(model) = lower(:model)");
            params.put("model", filter.model());
        }
        if (filter.minPrice() != null) {
            where.add("price_cents >= :minPriceCents");
            params.put("minPriceCents", filter.minPrice().toCents());
        }
        if (filter.maxPrice() != null) {
            where.add("price_cents <= :maxPriceCents");
            params.put("maxPriceCents", filter.maxPrice().toCents());
        }
        if (filter.minYear() != null) {
            where.add("model_year >= :minYear");
            params.put("minYear", filter.minYear());
        }
        if (filter.maxYear() != null) {
            where.add("model_year <= :maxYear");
            params.put("maxYear", filter.maxYear());
        }
        return page(" WHERE " + String.join(" AND ", where), "price_cents, id", params, paging);
    }

    @Override
    public Page<Vehicle> findSold(Paging paging) {
        return page(" WHERE status IN ('SOLD', 'DELIVERED')", "sold_price_cents, id", Map.of(), paging);
    }

    private Page<Vehicle> page(String where, String orderBy, Map<String, Object> params, Paging paging) {
        List<Vehicle> items = jdbc.sql(SELECT_VEHICLE + where + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset")
                .params(params)
                .param("limit", paging.size())
                .param("offset", paging.offset())
                .query((row, n) -> vehicle(row))
                .list();
        long total = jdbc.sql("SELECT count(*) FROM vehicle" + where)
                .params(params)
                .query(Long.class)
                .single();
        return Page.of(items, paging, total);
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
