package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerField;
import com.revenda.customers.domain.model.EmailAddress;
import com.revenda.customers.domain.model.Phone;
import com.revenda.customers.domain.ports.in.UpdateOwnCustomerCommand;
import com.revenda.customers.domain.ports.in.UpdateOwnCustomerUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import com.revenda.customers.domain.ports.out.LockCustomerPort;
import com.revenda.customers.domain.ports.out.SaveCustomerPort;
import java.time.Clock;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateOwnCustomerService implements UpdateOwnCustomerUseCase {

    private final LockCustomerPort lockedCustomers;
    private final SaveCustomerPort savedCustomers;
    private final LoadCustomerPort customers;
    private final AuditRecorder audit;
    private final CustomersProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public Customer update(UpdateOwnCustomerCommand command) {
        Customer current = lockedCustomers.lockActiveFor(command.identitySubject())
                .orElseThrow(BusinessException::notFound);
        Phone phone = command.phone() == null ? null : new Phone(command.phone());
        EmailAddress accountEmail = AccountEmail.accepted(command.email(), command.emailVerified(), properties)
                .orElse(null);
        Customer edited = current.edit(command.fullName(), phone, command.address(), accountEmail, clock.instant());
        Set<CustomerField> changed = current.changesTo(edited);
        if (changed.isEmpty()) {
            return current;
        }
        savedCustomers.update(edited, changed);
        audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_UPDATED, Customer.ENTITY_TYPE, edited.id(),
                        "Cadastro atualizado pelo titular")
                .withChange(CustomerSnapshot.of(current), CustomerSnapshot.of(edited))
                .withExtra(CustomerChanges.of(changed)));
        return customers.findActive(edited.id()).orElseThrow(BusinessException::notFound);
    }
}
