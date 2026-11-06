package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseTransition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadPurchasePort {

    Optional<Purchase> load(UUID id);

    /** A linha do tempo da compra, na ordem em que foi gravada; vazia se a compra não existe. */
    List<PurchaseTransition> loadTransitions(UUID purchaseId);
}
