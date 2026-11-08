package com.revenda.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.PurchasePaths;
import com.revenda.sales.domain.model.PurchasePaths.Path;
import com.revenda.sales.domain.model.SagaCommand;
import com.revenda.sales.domain.model.SagaCommand.CancelCharge;
import com.revenda.sales.domain.model.SagaCommand.MarkVehicleSold;
import com.revenda.sales.domain.model.SagaCommand.RefundCharge;
import com.revenda.sales.domain.model.SagaCommand.ReleaseVehicle;
import com.revenda.sales.domain.model.TransitionReason;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Cada mudança da compra e o que ela pede aos outros participantes. */
class SagaCommandsTest {

    @Test
    void theStepsForwardAskNothingButTheSaleTheDeliveryAndTheDocument() {
        assertThat(types(PurchasePaths.pending())).isEmpty();
        assertThat(types(PurchasePaths.reserved())).isEmpty();
        assertThat(types(PurchasePaths.awaiting())).isEmpty();
        assertThat(types(PurchasePaths.rejected())).as("nada a desfazer").isEmpty();
        assertThat(types(PurchasePaths.paid())).containsExactly("MarkVehicleSold");
        assertThat(types(PurchasePaths.completed())).containsExactly("DeliverVehicle", "GenerateTransferDocument");
        assertThat(types(PurchasePaths.documented())).as("o documento é o último passo").isEmpty();
        assertThat(types(PurchasePaths.refunded(PurchasePaths.paidWrongAmount()))).isEmpty();
    }

    @Test
    void eachFailureAsksForItsCompensation() {
        assertThat(types(PurchasePaths.reservationFailed())).containsExactly("ReleaseVehicle");
        assertThat(types(PurchasePaths.chargeFailed())).containsExactly("ReleaseVehicle", "CancelCharge");
        assertThat(types(PurchasePaths.recoveredWhilePending())).containsExactly("ReleaseVehicle", "CancelCharge");
        assertThat(types(PurchasePaths.recoveredWhileReserved())).containsExactly("ReleaseVehicle", "CancelCharge");
        assertThat(types(PurchasePaths.expired())).containsExactly("CancelCharge", "ReleaseVehicle");
        assertThat(types(PurchasePaths.cancelledBeforePaying())).containsExactly("CancelCharge", "ReleaseVehicle");
        assertThat(types(PurchasePaths.cancelledAfterPaying())).containsExactly("RefundCharge", "ReleaseVehicle");
        assertThat(types(PurchasePaths.paidWrongAmount())).containsExactly("RefundCharge", "CancelCharge",
                "ReleaseVehicle");
        PurchasePaths.closedUnpaid().forEach(closed ->
                assertThat(types(PurchasePaths.paidLate(closed))).as(closed.name()).containsExactly("RefundCharge"));
    }

    @Test
    void everyReasonIsCovered() {
        Set<TransitionReason> reached = PurchasePaths.all().stream()
                .map(path -> path.changes().getLast().transition().reason())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TransitionReason.class)));

        assertThat(reached).containsExactlyInAnyOrder(TransitionReason.values());
    }

    @Test
    void theStockCommandsGoInTheVehicleGroupAndTheGatewayOnesInThePurchaseGroup() {
        Path paidWrong = PurchasePaths.paidWrongAmount();
        List<SagaCommand> commands = SagaCommands.after(paidWrong.changes().getLast());

        for (SagaCommand command : commands) {
            String expected = command instanceof ReleaseVehicle || command instanceof MarkVehicleSold
                    ? paidWrong.purchase().vehicleId().toString()
                    : paidWrong.purchase().id().toString();
            assertThat(command.groupKey()).as(command.type()).isEqualTo(expected);
        }
        assertThat(commands).filteredOn(RefundCharge.class::isInstance).singleElement()
                .extracting(command -> ((RefundCharge) command).chargeId())
                .isEqualTo(paidWrong.purchase().chargeId());
    }

    @Test
    void theSaleCarriesThePriceOfTheReservationAndTheInstantOfThePayment() {
        Path paid = PurchasePaths.paid();

        MarkVehicleSold sale = (MarkVehicleSold) SagaCommands.after(paid.changes().getLast()).getFirst();

        assertThat(sale.soldPrice()).isEqualTo(PurchasePaths.PRICE);
        assertThat(sale.soldAt()).isEqualTo(paid.purchase().paidAt());
        assertThat(sale.purchaseId()).isEqualTo(paid.purchase().id());
    }

    @Test
    void theRecoveryOfAPurchaseWithoutChargeCancelsByThePurchase() {
        CancelCharge cancel = (CancelCharge) SagaCommands.after(PurchasePaths.recoveredWhileReserved().changes()
                .getLast()).getLast();

        assertThat(cancel.chargeId()).isNull();
    }

    private static List<String> types(Path path) {
        PurchaseChange last = path.changes().getLast();
        return SagaCommands.after(last).stream().map(SagaCommand::type).toList();
    }
}
