package com.revenda.customers.application.service;

import com.revenda.customers.domain.model.EmailAddress;
import java.util.Optional;

/**
 * O e-mail da conta, vindo do token, quando ele vale para o cadastro: tem usuário e domínio e, com a conferência
 * ligada, foi verificado pelo provedor de identidade (D-28).
 */
final class AccountEmail {

    private AccountEmail() {
    }

    static Optional<EmailAddress> accepted(String email, boolean verified, CustomersProperties properties) {
        boolean usable = EmailAddress.isUsable(email) && (verified || !properties.requireVerifiedEmail());
        return usable ? Optional.of(new EmailAddress(email)) : Optional.empty();
    }
}
