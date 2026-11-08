package com.revenda.sales.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.sales.infrastructure.adapters.out.document.DocumentsProperties;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** O link assinado leva o endereço que o navegador alcança, que pode não ser o mesmo em que o serviço grava. */
class DocumentLinkEndpointTests {

    private static final URI INSIDE = URI.create("http://localstack:4566");
    private static final URI OUTSIDE = URI.create("http://localhost:4566");

    @BeforeEach
    void credentials() {
        System.setProperty("aws.region", "sa-east-1");
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @AfterEach
    void clear() {
        System.clearProperty("aws.region");
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    @Test
    void withoutALinkAddressTheLinkUsesTheServiceAddress() {
        DocumentsProperties documents = properties(INSIDE, null);

        assertThat(documents.linkEndpoint()).isEqualTo(INSIDE);
        assertThat(link(documents)).hasHost("localstack").hasPort(4566).hasPath("/transfer-documents/purchases/a.pdf");
    }

    @Test
    void theLinkAddressGoesInTheLinkAndTheBucketStaysInThePath() {
        DocumentsProperties documents = properties(INSIDE, OUTSIDE);

        assertThat(link(documents)).hasHost("localhost").hasPort(4566).hasPath("/transfer-documents/purchases/a.pdf");
    }

    @Test
    void inTheCloudBothAreEmpty() {
        DocumentsProperties documents = properties(URI.create(""), URI.create(""));

        assertThat(documents.endpoint()).isNull();
        assertThat(documents.linkEndpoint()).isNull();
    }

    private static DocumentsProperties properties(URI endpoint, URI linkEndpoint) {
        return new DocumentsProperties("transfer-documents", "alias/documents", Duration.ofMinutes(5),
                ZoneId.of("America/Sao_Paulo"), endpoint, linkEndpoint);
    }

    private static URL link(DocumentsProperties documents) {
        try (S3Presigner presigner = new SalesConfiguration().s3Presigner(documents)) {
            return presigner.presignGetObject(request -> request
                    .signatureDuration(Duration.ofMinutes(5))
                    .getObjectRequest(get -> get.bucket("transfer-documents").key("purchases/a.pdf")))
                    .url();
        }
    }
}
