package com.revenda.sales.infrastructure.adapters.out.document;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * O bucket privado dos documentos de transferência e a chave do KMS que os cifra (SSE-KMS); {@code linkTtl} é a
 * validade do link de download (5 minutos, R16); {@code zone} é o fuso das datas impressas no documento;
 * {@code endpoint} é o {@code AWS_ENDPOINT_URL} do LocalStack, vazio na nuvem — com ele, o endereço do bucket vai no
 * caminho ({@code host/bucket/chave}), como o LocalStack pede. {@code linkEndpoint} é o endereço que vai no link
 * assinado, o que o navegador alcança; vazio, é o {@code endpoint} (no compose, o serviço grava em
 * {@code localstack:4566} e o link sai com {@code localhost:4566}).
 */
@Validated
@ConfigurationProperties("revenda.documents")
public record DocumentsProperties(
        @NotBlank String bucket,
        @NotBlank String kmsKeyId,
        @DefaultValue("PT5M") Duration linkTtl,
        @DefaultValue("America/Sao_Paulo") ZoneId zone,
        URI endpoint,
        URI linkEndpoint) {

    public DocumentsProperties {
        if (linkTtl.isNegative() || linkTtl.isZero() || linkTtl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("O link do documento vale mais que zero e no máximo 5 minutos");
        }
        if (endpoint != null && endpoint.toString().isBlank()) {
            endpoint = null;
        }
        if (linkEndpoint == null || linkEndpoint.toString().isBlank()) {
            linkEndpoint = endpoint;
        }
    }
}
