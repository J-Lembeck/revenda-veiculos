package com.revenda.customers.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Tudo o que o cadastro guarda sobre o titular (LGPD, art. 18, acesso e portabilidade): o cadastro, o pedido de
 * exclusão, se houver, e o histórico de quem acessou ou mudou o dado.
 */
public record CustomerExport(Customer customer, Instant deletionRequestedAt, List<CustomerAccess> history,
        Instant exportedAt) {

    public CustomerExport {
        history = List.copyOf(history);
    }

    @Override
    public String toString() {
        return "CustomerExport[customer=" + customer + ", history=" + history.size() + "]";
    }
}
