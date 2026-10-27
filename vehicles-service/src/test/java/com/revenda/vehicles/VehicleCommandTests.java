package com.revenda.vehicles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import com.revenda.common.time.SqlTime;
import com.revenda.vehicles.domain.ports.in.DeliverVehicleCommand;
import com.revenda.vehicles.domain.ports.in.DeliverVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldCommand;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldUseCase;
import com.revenda.vehicles.domain.ports.in.ReleaseVehicleCommand;
import com.revenda.vehicles.domain.ports.in.ReleaseVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.VehicleCommandOutcome;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Os três comandos da compra aplicados pelas portas, como o consumidor da fila os chama: aplicado, repetido, já em
 * vigor, ainda não aplicável e contraditório.
 */
class VehicleCommandTests extends VehiclesApiTest {

    private static final Instant PAID_AT = Instant.parse("2026-10-27T13:05:00Z");
    private static final Instant PICKED_UP_AT = Instant.parse("2026-10-29T10:30:00Z");
    private static final long RESERVATION_PRICE = 8_990_000L;

    @Autowired
    private ReleaseVehicleUseCase releaseVehicle;

    @Autowired
    private MarkVehicleSoldUseCase markVehicleSold;

    @Autowired
    private DeliverVehicleUseCase deliverVehicle;

    @Test
    void releasingAReservedVehiclePutsItBackForSale() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        assertThat(release(UUID.randomUUID(), vehicle, purchase)).isEqualTo(VehicleCommandOutcome.APPLIED);

        assertThat(row(vehicle)).containsEntry("status", "AVAILABLE").containsEntry("purchase_id", null)
                .containsEntry("version", 2);
        assertThat(api().get("/vehicles/" + vehicle).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void releasingASoldVehicleUndoesTheSale() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        sell(UUID.randomUUID(), vehicle, purchase);

        assertThat(release(UUID.randomUUID(), vehicle, purchase)).isEqualTo(VehicleCommandOutcome.APPLIED);

        assertThat(row(vehicle)).containsEntry("status", "AVAILABLE").containsEntry("purchase_id", null)
                .containsEntry("sold_price_cents", null).containsEntry("sold_at", null).containsEntry("version", 3);
    }

    @Test
    void theReleaseIsAuditedAsTheSystemWithThePurchaseAndTheMessage() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        release(message, vehicle, purchase);

        assertThat(auditOf("VEHICLE_RELEASED", vehicle)).containsEntry("actor_type", "SYSTEM")
                .containsEntry("actor_id", null).containsEntry("summary", "Veículo liberado da compra " + purchase)
                .containsEntry("purchase", purchase.toString()).containsEntry("message", message.toString());
    }

    @Test
    void aReleaseFromAnotherPurchaseDoesNotRelease() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        assertThat(release(UUID.randomUUID(), vehicle, UUID.randomUUID())).isEqualTo(VehicleCommandOutcome.NO_EFFECT);

        assertThat(row(vehicle)).containsEntry("status", "RESERVED").containsEntry("purchase_id", purchase)
                .containsEntry("version", 1);
        assertThat(audited("VEHICLE_RELEASED", vehicle)).isZero();
    }

    @Test
    void aReleaseOfAVehicleAlreadyForSaleHasNoEffect() {
        UUID vehicle = register().id();

        assertThat(release(UUID.randomUUID(), vehicle, UUID.randomUUID())).isEqualTo(VehicleCommandOutcome.NO_EFFECT);
        assertThat(release(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isEqualTo(VehicleCommandOutcome.NO_EFFECT);

        assertThat(row(vehicle)).containsEntry("status", "AVAILABLE").containsEntry("version", 0);
    }

    @Test
    void aReleaseAfterTheDeliveryContradictsTheStockAndIsNotRecorded() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        sell(UUID.randomUUID(), vehicle, purchase);
        deliver(UUID.randomUUID(), vehicle, purchase);
        UUID message = UUID.randomUUID();

        assertThatThrownBy(() -> release(message, vehicle, purchase))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("VEHICLE_COMMAND_CONTRADICTORY"));

        assertThat(row(vehicle)).containsEntry("status", "DELIVERED").containsEntry("purchase_id", purchase);
        assertThat(processed(message)).isFalse();
    }

    @Test
    void theSaleKeepsThePriceOfTheReservationAndTheMomentOfThePayment() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        jdbc.sql("UPDATE vehicle SET price_cents = 9500000 WHERE id = :id").param("id", vehicle).update();

        assertThat(sell(UUID.randomUUID(), vehicle, purchase)).isEqualTo(VehicleCommandOutcome.APPLIED);

        assertThat(row(vehicle)).containsEntry("status", "SOLD").containsEntry("purchase_id", purchase)
                .containsEntry("sold_price_cents", RESERVATION_PRICE).containsEntry("version", 2);
        assertThat(instant(vehicle, "sold_at")).isEqualTo(PAID_AT);
        assertThat(api().get("/vehicles/" + vehicle).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void theSaleIsAuditedAsTheSystemWithThePrice() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        sell(UUID.randomUUID(), vehicle, purchase);

        assertThat(auditOf("VEHICLE_SOLD", vehicle)).containsEntry("actor_type", "SYSTEM")
                .containsEntry("summary", "Veículo vendido na compra " + purchase)
                .containsEntry("purchase", purchase.toString())
                .containsEntry("price", Long.toString(RESERVATION_PRICE));
    }

    @Test
    void theSameMessageTwiceIsAppliedOnceAndAuditedOnce() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        assertThat(sell(message, vehicle, purchase)).isEqualTo(VehicleCommandOutcome.APPLIED);
        assertThat(sell(message, vehicle, purchase)).isEqualTo(VehicleCommandOutcome.DUPLICATE);

        assertThat(row(vehicle)).containsEntry("status", "SOLD").containsEntry("version", 2);
        assertThat(audited("VEHICLE_SOLD", vehicle)).isEqualTo(1);
    }

    @Test
    void aRepeatedReleaseIsAppliedOnceEvenAfterTheVehicleIsReservedAgain() {
        UUID vehicle = register().id();
        UUID first = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        reserveFor(vehicle, first);
        release(message, vehicle, first);
        UUID second = UUID.randomUUID();
        reserveFor(vehicle, second);

        assertThat(release(message, vehicle, second)).isEqualTo(VehicleCommandOutcome.DUPLICATE);

        assertThat(row(vehicle)).containsEntry("status", "RESERVED").containsEntry("purchase_id", second);
        assertThat(audited("VEHICLE_RELEASED", vehicle)).isEqualTo(1);
    }

    @Test
    void aSaleAlreadyInEffectIsConfirmedWithoutAnotherEffect() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        sell(UUID.randomUUID(), vehicle, purchase);

        assertThat(sell(UUID.randomUUID(), vehicle, purchase)).isEqualTo(VehicleCommandOutcome.NO_EFFECT);
        deliver(UUID.randomUUID(), vehicle, purchase);
        assertThat(sell(UUID.randomUUID(), vehicle, purchase)).isEqualTo(VehicleCommandOutcome.NO_EFFECT);

        assertThat(audited("VEHICLE_SOLD", vehicle)).isEqualTo(1);
    }

    @Test
    void aSaleForAnotherPurchaseContradictsTheStock() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        UUID message = UUID.randomUUID();

        assertContradictory(() -> sell(message, vehicle, UUID.randomUUID()));

        assertThat(row(vehicle)).containsEntry("status", "RESERVED").containsEntry("purchase_id", purchase)
                .containsEntry("version", 1);
        assertThat(processed(message)).isFalse();
        assertThat(audited("VEHICLE_SOLD", vehicle)).isZero();
    }

    @Test
    void aSaleOfAVehicleThatWasNeverReservedContradictsTheStock() {
        UUID vehicle = register().id();

        assertContradictory(() -> sell(UUID.randomUUID(), vehicle, UUID.randomUUID()));
        assertContradictory(() -> sell(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertThat(row(vehicle)).containsEntry("status", "AVAILABLE").containsEntry("version", 0);
    }

    @Test
    void theDeliveryWritesOffTheSoldVehicle() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        sell(UUID.randomUUID(), vehicle, purchase);

        assertThat(deliver(UUID.randomUUID(), vehicle, purchase)).isEqualTo(VehicleCommandOutcome.APPLIED);

        assertThat(row(vehicle)).containsEntry("status", "DELIVERED").containsEntry("purchase_id", purchase)
                .containsEntry("sold_price_cents", RESERVATION_PRICE).containsEntry("version", 3);
        assertThat(instant(vehicle, "delivered_at")).isEqualTo(PICKED_UP_AT);
        assertThat(auditOf("VEHICLE_DELIVERED", vehicle)).containsEntry("actor_type", "SYSTEM")
                .containsEntry("summary", "Veículo entregue na compra " + purchase);
    }

    @Test
    void aDeliveryBeforeTheSaleIsNotYetApplicableAndCanBeRetriedLater() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        reserveFor(vehicle, purchase);

        assertThatThrownBy(() -> deliver(message, vehicle, purchase))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo("VEHICLE_COMMAND_NOT_YET_APPLICABLE"));
        assertThat(row(vehicle)).containsEntry("status", "RESERVED").containsEntry("version", 1);
        assertThat(processed(message)).isFalse();

        sell(UUID.randomUUID(), vehicle, purchase);

        assertThat(deliver(message, vehicle, purchase)).isEqualTo(VehicleCommandOutcome.APPLIED);
        assertThat(row(vehicle)).containsEntry("status", "DELIVERED");
    }

    @Test
    void aDeliveryAlreadyInEffectIsConfirmedAndARepeatedOneIsIgnored() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        sell(UUID.randomUUID(), vehicle, purchase);
        deliver(message, vehicle, purchase);

        assertThat(deliver(message, vehicle, purchase)).isEqualTo(VehicleCommandOutcome.DUPLICATE);
        assertThat(deliver(UUID.randomUUID(), vehicle, purchase)).isEqualTo(VehicleCommandOutcome.NO_EFFECT);

        assertThat(row(vehicle)).containsEntry("version", 3);
        assertThat(audited("VEHICLE_DELIVERED", vehicle)).isEqualTo(1);
    }

    @Test
    void aDeliveryForAnotherPurchaseOrAnUnsoldVehicleContradictsTheStock() {
        UUID vehicle = register().id();
        UUID purchase = UUID.randomUUID();
        reserveFor(vehicle, purchase);
        sell(UUID.randomUUID(), vehicle, purchase);

        assertContradictory(() -> deliver(UUID.randomUUID(), vehicle, UUID.randomUUID()));
        assertContradictory(() -> deliver(UUID.randomUUID(), register().id(), UUID.randomUUID()));
        assertContradictory(() -> deliver(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        assertThat(row(vehicle)).containsEntry("status", "SOLD").containsEntry("version", 2);
    }

    private VehicleCommandOutcome release(UUID message, UUID vehicle, UUID purchase) {
        return releaseVehicle.release(new ReleaseVehicleCommand(message, vehicle, purchase));
    }

    private VehicleCommandOutcome sell(UUID message, UUID vehicle, UUID purchase) {
        return markVehicleSold.markSold(new MarkVehicleSoldCommand(message, vehicle, purchase,
                Money.ofCents(RESERVATION_PRICE), PAID_AT));
    }

    private VehicleCommandOutcome deliver(UUID message, UUID vehicle, UUID purchase) {
        return deliverVehicle.deliver(new DeliverVehicleCommand(message, vehicle, purchase, PICKED_UP_AT));
    }

    private static void assertContradictory(Runnable command) {
        assertThatThrownBy(command::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo("VEHICLE_COMMAND_CONTRADICTORY"));
    }

    private boolean processed(UUID message) {
        return jdbc.sql("SELECT count(*) FROM processed_message WHERE message_id = :id")
                .param("id", message.toString()).query(Integer.class).single() == 1;
    }

    private Instant instant(UUID vehicle, String column) {
        return jdbc.sql("SELECT " + column + " FROM vehicle WHERE id = :id").param("id", vehicle)
                .query((row, n) -> SqlTime.instantOrNull(row, column)).single();
    }

    private Map<String, Object> auditOf(String action, UUID vehicle) {
        return jdbc.sql("""
                        SELECT actor_type, actor_id, summary, data -> 'extra' ->> 'purchaseId' AS purchase,
                               data -> 'extra' ->> 'messageId' AS message,
                               data -> 'extra' ->> 'soldPriceCents' AS price
                          FROM audit_log WHERE action = :action AND entity_id = :id""")
                .param("action", action)
                .param("id", vehicle)
                .query()
                .singleRow();
    }
}
