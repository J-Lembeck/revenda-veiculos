package com.revenda.sales.application.service;

import java.util.UUID;

/** O que a {@code Idempotency-Key} do pedido de compra guarda: só o id, e a resposta sai da compra relida. */
public record RequestedPurchase(UUID id) {
}
