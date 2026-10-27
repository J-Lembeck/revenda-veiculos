package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.customers.domain.model.Cpf;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.CheckCpfCommand;
import com.revenda.customers.domain.ports.in.CheckCpfUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A conferência da retirada: compara o índice cego do CPF apresentado com o do cadastro, sem decifrar nada, e grava
 * {@code CUSTOMER_CPF_CHECKED} com a compra e o resultado. O CPF que não confere não chega a ser comparado.
 */
@Service
@RequiredArgsConstructor
public class CheckCpfService implements CheckCpfUseCase {

    private final LoadCustomerPort customers;
    private final AuditRecorder audit;

    @Override
    @Transactional
    public boolean check(CheckCpfCommand command) {
        Cpf presented = Cpf.parse(command.cpf())
                .orElseThrow(() -> BusinessException.invalid(Cpf.invalidAt("cpf")));
        boolean matches = customers.cpfMatches(command.customerId(), presented)
                .orElseThrow(BusinessException::notFound);
        audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_CPF_CHECKED, Customer.ENTITY_TYPE,
                        command.customerId(), "CPF conferido na retirada da compra " + command.purchaseId())
                .withExtra(new CpfCheck(command.purchaseId(), matches)));
        return matches;
    }

    /** A compra e se o CPF bateu; nunca o CPF. */
    record CpfCheck(UUID purchaseId, boolean matches) {
    }
}
