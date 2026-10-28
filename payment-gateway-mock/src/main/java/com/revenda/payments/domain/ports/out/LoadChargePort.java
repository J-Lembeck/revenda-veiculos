package com.revenda.payments.domain.ports.out;

import com.revenda.payments.domain.model.Charge;
import java.util.Optional;

public interface LoadChargePort {

    Optional<Charge> findById(String chargeId);

    Optional<Charge> findByReference(String referenceId);
}
