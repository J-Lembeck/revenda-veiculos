package com.revenda.sales.domain.service;

import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseChange;
import com.revenda.sales.domain.model.PurchaseStatus;
import com.revenda.sales.domain.model.SagaCommand;
import com.revenda.sales.domain.model.SagaCommand.CancelCharge;
import com.revenda.sales.domain.model.SagaCommand.DeliverVehicle;
import com.revenda.sales.domain.model.SagaCommand.GenerateTransferDocument;
import com.revenda.sales.domain.model.SagaCommand.MarkVehicleSold;
import com.revenda.sales.domain.model.SagaCommand.RefundCharge;
import com.revenda.sales.domain.model.SagaCommand.ReleaseVehicle;
import java.util.List;

/**
 * O que cada mudança da compra pede aos outros participantes: o passo seguinte da saga ou a compensação do que ficou
 * pela metade. Toda compensação é idempotente do outro lado, então liberar o que talvez não esteja preso e cancelar a
 * cobrança que talvez não exista são seguros.
 */
public final class SagaCommands {

    private SagaCommands() {
    }

    public static List<SagaCommand> after(PurchaseChange change) {
        Purchase purchase = change.purchase();
        return switch (change.transition().reason()) {
            case PURCHASE_REQUESTED, VEHICLE_RESERVED, CHARGE_ISSUED, VEHICLE_UNAVAILABLE, REFUND_CONFIRMED, DOCUMENT_ISSUED ->
                    List.of();
            case RESERVATION_FAILED -> List.of(release(purchase));
            case CHARGE_FAILED, RECOVERY_TIMEOUT -> List.of(release(purchase), cancelCharge(purchase));
            case PAYMENT_CONFIRMED -> List.of(new MarkVehicleSold(purchase.id(), purchase.vehicleId(),
                    purchase.price(), purchase.paidAt()));
            case PAYMENT_MISMATCH -> List.of(refund(purchase), cancelCharge(purchase), release(purchase));
            case LATE_PAYMENT -> List.of(refund(purchase));
            case PAYMENT_EXPIRED -> List.of(cancelCharge(purchase), release(purchase));
            case CUSTOMER_GAVE_UP -> change.transition().from() == PurchaseStatus.PAID
                    ? List.of(refund(purchase), release(purchase))
                    : List.of(cancelCharge(purchase), release(purchase));
            case PICKUP_CONFIRMED -> List.of(new DeliverVehicle(purchase.id(), purchase.vehicleId(),
                    purchase.completedAt()), new GenerateTransferDocument(purchase.id()));
        };
    }

    /** Libera a reserva desta compra, se houver; liberar o veículo de outra compra não faz nada. */
    public static ReleaseVehicle release(Purchase purchase) {
        return new ReleaseVehicle(purchase.id(), purchase.vehicleId());
    }

    public static CancelCharge cancelCharge(Purchase purchase) {
        return new CancelCharge(purchase.id(), purchase.chargeId());
    }

    private static RefundCharge refund(Purchase purchase) {
        return new RefundCharge(purchase.id(), purchase.chargeId());
    }
}
