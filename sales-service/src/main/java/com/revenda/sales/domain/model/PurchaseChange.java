package com.revenda.sales.domain.model;

/** A compra na versão nova e a transição que a levou até ela, gravadas juntas. */
public record PurchaseChange(Purchase purchase, PurchaseTransition transition) {
}
