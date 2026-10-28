package com.revenda.payments.domain.ports.out;

import com.revenda.payments.domain.model.Charge;
import com.revenda.payments.domain.model.ChargeChange;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface SaveChargePort {

    /** Guarda a cobrança, a não ser que a referência já tenha uma: devolve a que ficou guardada, de uma vez só. */
    Charge addIfAbsent(Charge charge);

    /**
     * Aplica a mudança à cobrança, de uma vez só entre pedidos simultâneos; vazio se ela não existe. Exceção da mudança
     * deixa a cobrança como estava.
     */
    Optional<ChargeChange> change(String chargeId, UnaryOperator<Charge> change);
}
