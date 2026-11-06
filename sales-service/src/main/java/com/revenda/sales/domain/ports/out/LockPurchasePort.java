package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.Purchase;
import java.util.Optional;
import java.util.UUID;

public interface LockPurchasePort {

    /** A compra com a linha travada até o fim da transação, que já precisa estar aberta. */
    Optional<Purchase> lock(UUID id);
}
