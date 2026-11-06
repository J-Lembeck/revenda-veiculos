package com.revenda.sales.domain.ports.out;

import com.revenda.common.money.Money;
import com.revenda.sales.domain.model.IssuedCharge;
import com.revenda.sales.domain.model.Payer;
import java.time.Instant;
import java.util.UUID;

/**
 * Pede ao gateway a cobrança da compra, com a compra como referência (passo 3 da saga). Sem resposta,
 * {@code DependencyUnavailableException}: a cobrança talvez exista, e a compensação a procura pela referência.
 */
public interface IssueChargePort {

    IssuedCharge issue(UUID purchaseId, Money amount, Instant dueAt, Payer payer);
}
