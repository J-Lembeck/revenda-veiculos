package com.revenda.customers.domain.model;

/** O e-mail da conta no provedor de identidade; nunca vem do corpo do pedido. */
public record EmailAddress(String address) {

    public EmailAddress {
        if (!isUsable(address)) {
            throw new IllegalArgumentException("E-mail sem usuário ou sem domínio");
        }
    }

    /** Tem usuário e domínio dos dois lados da arroba. */
    public static boolean isUsable(String address) {
        if (address == null) {
            return false;
        }
        int at = address.indexOf('@');
        return at > 0 && at == address.lastIndexOf('@') && at < address.length() - 1;
    }

    /** {@code m***@example.test}: a primeira letra e o domínio. */
    public String masked() {
        return address.charAt(0) + "***" + address.substring(address.indexOf('@'));
    }

    @Override
    public String toString() {
        return masked();
    }
}
