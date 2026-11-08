package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.sales.domain.model.DocumentLink;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;

@Schema(description = "O link temporário do documento de transferência: baixa o PDF até `expiresAt`, e depois não.")
record DocumentLinkResponse(
        @Schema(description = "Endereço assinado do PDF no bucket privado; não leva dado pessoal",
                example = "https://transfer-documents.s3.sa-east-1.amazonaws.com/purchases/5b7d2c1e-8a4f-4d3b-9e6a-"
                        + "1c2b3d4e5f60/transfer-document.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=300")
        URI url,
        @Schema(description = "Até quando o link vale: 5 minutos depois do pedido", example = "2026-11-08T14:37:00Z")
        Instant expiresAt) {

    static DocumentLinkResponse of(DocumentLink link) {
        return new DocumentLinkResponse(link.url(), link.expiresAt());
    }

    @Override
    public String toString() {
        return "DocumentLinkResponse[expiresAt=" + expiresAt + ", endereço omitido]";
    }
}
