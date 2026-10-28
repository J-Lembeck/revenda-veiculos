package com.revenda.payments.application.service;

import com.revenda.common.error.BusinessException;
import com.revenda.payments.domain.model.Charge;
import com.revenda.payments.domain.ports.in.FindChargeUseCase;
import com.revenda.payments.domain.ports.out.LoadChargePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FindChargeService implements FindChargeUseCase {

    private final LoadChargePort charges;

    @Override
    public Charge findByReference(String referenceId) {
        return charges.findByReference(referenceId).orElseThrow(BusinessException::notFound);
    }
}
