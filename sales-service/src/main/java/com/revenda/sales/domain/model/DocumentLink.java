package com.revenda.sales.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** O endereço temporário do documento no bucket: quem tem o link baixa até {@code expiresAt}, e depois não. */
public record DocumentLink(URI url, Instant expiresAt) {

    public DocumentLink {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "DocumentLink[expiresAt=" + expiresAt + ", endereço omitido]";
    }
}
