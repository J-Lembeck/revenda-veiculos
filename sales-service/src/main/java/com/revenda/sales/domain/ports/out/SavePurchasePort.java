package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.PurchaseChange;

/** Grava a compra e a linha da linha do tempo juntas, na transação da mudança, com o ator dela. */
public interface SavePurchasePort {

    /** A compra que acabou de ser pedida. */
    void create(PurchaseChange requested);

    /** A versão seguinte da compra, que veio da anterior travada. */
    void update(PurchaseChange change);
}
