package com.revenda.customers.domain.ports.out;

import com.revenda.customers.domain.model.CustomerAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** O que a LGPD pede ler do cadastro além dele mesmo: o histórico e o pedido de exclusão. */
public interface LoadCustomerPrivacyPort {

    /** A auditoria sobre o cadastro, do mais antigo ao mais recente; {@code owner} é o {@code sub} do titular. */
    List<CustomerAccess> historyOf(UUID customerId, String owner);

    /** Quando o titular pediu a exclusão do cadastro ativo; vazio se não pediu. */
    Optional<Instant> deletionRequestedAt(UUID customerId);

    /** Os cadastros ativos com exclusão pedida, do pedido mais antigo ao mais recente. */
    List<UUID> deletionRequested();
}
