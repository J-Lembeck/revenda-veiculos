package com.revenda.customers.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.ViewOwnCustomerUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViewOwnCustomerService implements ViewOwnCustomerUseCase {

    private final LoadCustomerPort customers;

    @Override
    @Transactional(readOnly = true)
    public Customer view(String identitySubject) {
        return customers.findActiveFor(identitySubject).orElseThrow(BusinessException::notFound);
    }
}
