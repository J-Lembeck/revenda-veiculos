package com.revenda.customers.domain.ports.out;

import java.time.Instant;
import java.util.UUID;

/** O pedido de exclusão e a anonimização do cadastro ativo, sobre a linha já travada. */
public interface SaveCustomerPrivacyPort {

    /** Registra o pedido de exclusão; o pedido anterior fica como está. Diz se registrou agora. */
    boolean requestDeletion(UUID customerId, Instant at);

    /**
     * Apaga o dado pessoal e a conta do cadastro e o marca {@code ANONYMIZED}; o {@code id} fica, porque as compras
     * apontam para ele. Diz se anonimizou.
     */
    boolean anonymize(UUID customerId, Instant at);
}
