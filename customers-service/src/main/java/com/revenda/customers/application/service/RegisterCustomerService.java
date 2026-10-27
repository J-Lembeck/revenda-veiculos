package com.revenda.customers.application.service;

import com.revenda.common.audit.AuditEntry;
import com.revenda.common.audit.AuditRecorder;
import com.revenda.common.error.BusinessException;
import com.revenda.common.idempotency.IdempotencyStore;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerDetails;
import com.revenda.customers.domain.model.CustomersError;
import com.revenda.customers.domain.model.EmailAddress;
import com.revenda.customers.domain.ports.in.RegisterCustomerCommand;
import com.revenda.customers.domain.ports.in.RegisterCustomerUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import com.revenda.customers.domain.ports.out.SaveCustomerPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterCustomerService implements RegisterCustomerUseCase {

    private final SaveCustomerPort savedCustomers;
    private final LoadCustomerPort customers;
    private final IdempotencyStore idempotency;
    private final AuditRecorder audit;
    private final CustomersProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public Customer register(RegisterCustomerCommand command) {
        EmailAddress email = AccountEmail.accepted(command.email(), command.emailVerified(), properties)
                .orElseThrow(() -> BusinessException.of(CustomersError.EMAIL_NOT_VERIFIED));
        Instant now = clock.instant();
        CustomerDetails details = CustomerDetails.of(command.fullName(), command.cpf(), command.birthDate(),
                command.phone(), command.address(), LocalDate.ofInstant(now, ZoneOffset.UTC));
        UUID id = idempotency.once(RegisteredCustomer.class, () -> {
            if (customers.existsActiveFor(command.identitySubject())) {
                throw BusinessException.of(CustomersError.CUSTOMER_ALREADY_REGISTERED);
            }
            Customer customer = Customer.register(UUID.randomUUID(), command.identitySubject(), email, details, now);
            savedCustomers.insert(customer);
            audit.record(AuditEntry.of(CustomersAuditAction.CUSTOMER_REGISTERED, Customer.ENTITY_TYPE, customer.id(),
                            "Cliente cadastrado")
                    .withChange(null, CustomerSnapshot.of(customer)));
            return new RegisteredCustomer(customer.id());
        }).result().id();
        return customers.findActive(id).orElseThrow(BusinessException::notFound);
    }
}
