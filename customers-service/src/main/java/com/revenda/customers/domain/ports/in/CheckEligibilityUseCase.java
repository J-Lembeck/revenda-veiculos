package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.Eligibility;

public interface CheckEligibilityUseCase {

    /** Se a conta do provedor de identidade pode comprar hoje, na data civil do fuso da revenda (RN-05). */
    Eligibility check(String identitySubject);
}
