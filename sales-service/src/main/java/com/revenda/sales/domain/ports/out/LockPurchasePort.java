package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.Purchase;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LockPurchasePort {

    /** A compra com a linha travada até o fim da transação, que já precisa estar aberta. */
    Optional<Purchase> lock(UUID id);

    /**
     * A compra aguardando pagamento com o prazo vencido antes de {@code now}, a mais vencida, travada; a que outra
     * transação já travou fica de fora.
     */
    Optional<Purchase> lockNextOverdue(Instant now);

    /**
     * A compra parada em {@code PENDING} ou {@code RESERVED} desde antes de {@code updatedBefore}, a mais antiga,
     * travada; a que outra transação já travou fica de fora.
     */
    Optional<Purchase> lockNextStalled(Instant updatedBefore);
}
