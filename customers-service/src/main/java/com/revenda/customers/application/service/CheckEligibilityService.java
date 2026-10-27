package com.revenda.customers.application.service;

import com.revenda.customers.domain.model.Eligibility;
import com.revenda.customers.domain.ports.in.CheckEligibilityUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A elegibilidade não devolve dado pessoal nem é auditada: só diz se a conta pode comprar, e por que não. */
@Service
@RequiredArgsConstructor
public class CheckEligibilityService implements CheckEligibilityUseCase {

    private final LoadCustomerPort customers;
    private final CustomersProperties properties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Eligibility check(String identitySubject) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), properties.zone());
        return customers.findActiveFor(identitySubject)
                .map(customer -> Eligibility.of(customer, today))
                .orElseGet(Eligibility::missingProfile);
    }
}
