package com.revenda.customers.application.service;

import com.revenda.customers.domain.model.CustomerField;
import java.util.List;
import java.util.Set;

/** Na auditoria da edição, quais campos mudaram — nunca o valor deles. */
record CustomerChanges(List<CustomerField> changedFields) {

    static CustomerChanges of(Set<CustomerField> changed) {
        return new CustomerChanges(List.copyOf(changed));
    }
}
