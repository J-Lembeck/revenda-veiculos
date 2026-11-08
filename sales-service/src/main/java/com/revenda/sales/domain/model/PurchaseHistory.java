package com.revenda.sales.domain.model;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * O que as compras de um cliente dizem sobre guardar os dados dele: a venda concluída mais recente
 * ({@code lastCompletedAt}, nula se nenhuma) e se há compra aberta ({@code PENDING} a {@code PAID}).
 */
public record PurchaseHistory(Instant lastCompletedAt, boolean open) {

    public static final PurchaseHistory NONE = new PurchaseHistory(null, false);

    /**
     * Até quando a lei pede guardar: o prazo depois da última venda concluída; com compra aberta, o prazo contado de
     * agora, porque ela ainda pode virar venda. Vazio quando não há o que guardar.
     */
    public Optional<Instant> retainUntil(Period afterSale, Instant now) {
        Instant fromOpen = open ? plus(now, afterSale) : null;
        Instant fromSale = lastCompletedAt == null ? null : plus(lastCompletedAt, afterSale);
        if (fromOpen == null || (fromSale != null && fromSale.isAfter(fromOpen))) {
            return Optional.ofNullable(fromSale);
        }
        return Optional.of(fromOpen);
    }

    private static Instant plus(Instant instant, Period period) {
        return instant.atOffset(ZoneOffset.UTC).plus(period).toInstant();
    }
}
