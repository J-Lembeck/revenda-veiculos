package com.revenda.customers.domain.ports.in;

import java.time.Instant;

/**
 * UC-15: o titular pede a exclusão. Sem venda no prazo de guarda, o cadastro é anonimizado na hora e volta o instante;
 * com venda, o pedido fica registrado, o job de retenção anonimiza no fim do prazo e a resposta é
 * {@code CUSTOMER_HAS_RETAINED_DATA}, com a data.
 */
public interface RequestDeletionUseCase {

    Instant request(String identitySubject);
}
