package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.Eligibility;

/** O cadastro diz se a conta pode comprar (RN-05). Sem resposta, {@code DependencyUnavailableException}. */
public interface CheckEligibilityPort {

    Eligibility check(String customerSubject);
}
