package com.revenda.sales.domain.ports.out;

import com.revenda.common.paging.Page;
import com.revenda.common.paging.Paging;
import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.PurchaseFilter;
import com.revenda.sales.domain.model.PurchaseHistory;
import com.revenda.sales.domain.model.PurchaseTransition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadPurchasePort {

    Optional<Purchase> load(UUID id);

    /** A linha do tempo da compra, na ordem em que foi gravada; vazia se a compra não existe. */
    List<PurchaseTransition> loadTransitions(UUID purchaseId);

    /** Se o cliente tem compra em andamento ({@code PENDING}, {@code RESERVED}, {@code AWAITING_PAYMENT}). */
    boolean existsActiveFor(UUID customerId);

    /** A venda concluída mais recente do cliente e se ele tem compra aberta; {@code NONE} se não tem compra. */
    PurchaseHistory historyOf(UUID customerId);

    /** As compras do filtro, a mais recente primeiro, com o {@code id} como desempate. */
    Page<Purchase> findPage(PurchaseFilter filter, Paging paging);
}
