package com.revenda.customers.domain.ports.in;

public interface CheckCpfUseCase {

    /**
     * Se o CPF bate com o do cadastro ativo, pelo índice cego, sem decifrar nem revelar; registrado na auditoria. CPF
     * que não confere sai como {@code CPF_INVALID}; sem cadastro ativo, {@code NOT_FOUND}.
     */
    boolean check(CheckCpfCommand command);
}
