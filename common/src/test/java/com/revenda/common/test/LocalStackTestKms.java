package com.revenda.common.test;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;

/** O KMS do {@link LocalStackTestContainer}. */
public final class LocalStackTestKms {

    private LocalStackTestKms() {
    }

    public static KmsClient client() {
        return KmsClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(LocalStackTestContainer.endpoint())
                .region(LocalStackTestContainer.region())
                .credentialsProvider(LocalStackTestContainer.credentials())
                .build();
    }

    /** Uma chave-mestra nova, para o teste não depender das chaves de outro. */
    public static String createKey(KmsClient kms) {
        return kms.createKey(request -> request.description("chave de teste")).keyMetadata().keyId();
    }
}
