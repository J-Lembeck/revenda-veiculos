package com.revenda.customers.domain.ports.in;

import com.revenda.customers.domain.model.CustomerExport;

/** UC-15: o titular leva tudo o que o cadastro guarda sobre ele; auditado ({@code CUSTOMER_EXPORTED}). */
public interface ExportOwnDataUseCase {

    CustomerExport export(String identitySubject);
}
