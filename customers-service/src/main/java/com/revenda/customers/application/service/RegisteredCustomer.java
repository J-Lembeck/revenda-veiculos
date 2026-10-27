package com.revenda.customers.application.service;

import java.util.UUID;

/** O que a idempotência guarda do cadastro: só o id; a resposta é refeita a partir dele. */
public record RegisteredCustomer(UUID id) {
}
