package com.revenda.sales.domain.model;

import java.util.List;

/** A compra com a linha do tempo dela, na ordem em que cada mudança foi gravada. */
public record PurchaseDetails(Purchase purchase, List<PurchaseTransition> timeline) {

    public PurchaseDetails {
        timeline = List.copyOf(timeline);
    }
}
