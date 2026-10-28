package com.revenda.payments.infrastructure.adapters.out.persistence;

import com.revenda.payments.domain.model.Charge;
import com.revenda.payments.domain.model.ChargeChange;
import com.revenda.payments.domain.ports.out.LoadChargePort;
import com.revenda.payments.domain.ports.out.SaveChargePort;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Repository;

/**
 * As cobranças em memória, por id e por referência; somem quando o gateway para. A referência ganha uma cobrança só,
 * mesmo com pedidos simultâneos, e cada mudança de uma cobrança é aplicada de uma vez.
 */
@Repository
class InMemoryChargeStore implements LoadChargePort, SaveChargePort {

    private final ConcurrentMap<String, Charge> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByReference = new ConcurrentHashMap<>();

    @Override
    public Optional<Charge> findById(String chargeId) {
        return Optional.ofNullable(byId.get(chargeId));
    }

    @Override
    public Optional<Charge> findByReference(String referenceId) {
        return Optional.ofNullable(idByReference.get(referenceId)).map(byId::get);
    }

    @Override
    public Charge addIfAbsent(Charge charge) {
        String id = idByReference.computeIfAbsent(charge.referenceId(), reference -> {
            byId.put(charge.id(), charge);
            return charge.id();
        });
        return byId.get(id);
    }

    @Override
    public Optional<ChargeChange> change(String chargeId, UnaryOperator<Charge> change) {
        AtomicReference<Charge> before = new AtomicReference<>();
        Charge after = byId.computeIfPresent(chargeId, (id, current) -> {
            before.set(current);
            return change.apply(current);
        });
        return after == null ? Optional.empty() : Optional.of(new ChargeChange(before.get(), after));
    }
}
