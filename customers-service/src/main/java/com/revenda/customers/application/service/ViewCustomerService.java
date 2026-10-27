package com.revenda.customers.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.ports.in.ViewCustomerUseCase;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViewCustomerService implements ViewCustomerUseCase {

    private final LoadCustomerPort customers;

    @Override
    @Transactional(readOnly = true)
    public Customer view(UUID id) {
        return customers.findActive(id).orElseThrow(BusinessException::notFound);
    }
}
