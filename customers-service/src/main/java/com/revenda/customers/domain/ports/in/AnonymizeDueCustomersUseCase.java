package com.revenda.customers.domain.ports.in;

/**
 * O job de retenção: anonimiza os cadastros com exclusão pedida cujo prazo de guarda já acabou. Devolve quantos
 * anonimizou; com o {@code sales} fora do ar, para e tenta na próxima passada.
 */
public interface AnonymizeDueCustomersUseCase {

    int anonymizeDue();
}
