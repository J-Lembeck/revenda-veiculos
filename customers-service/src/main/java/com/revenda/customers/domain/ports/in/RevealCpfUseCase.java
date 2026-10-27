package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Cpf;

public interface RevealCpfUseCase {

    /** O CPF completo do cliente, registrado na auditoria com quem pediu e o motivo. */
    Cpf reveal(RevealCpfCommand command);
}
