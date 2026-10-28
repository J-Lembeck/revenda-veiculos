package com.revenda.payments.domain.model;

/** Quem paga a cobrança. Fica só em memória: nunca vai para o log, para a resposta nem para o webhook. */
public record Payer(String name, String cpf, PayerAddress address) {

    @Override
    public String toString() {
        return "Payer[dados pessoais omitidos]";
    }
}
