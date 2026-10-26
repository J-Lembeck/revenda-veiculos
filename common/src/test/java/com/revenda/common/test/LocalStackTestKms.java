package com.revenda.common.test;

import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * Um LocalStack só para a suíte inteira, na mesma imagem do ambiente local, com o KMS. Sobe no bloco
 * estático e nunca é parado, como o {@link PostgresTestDatabase}.
 */
public final class LocalStackTestKms {

    private static final LocalStackContainer LOCALSTACK;

    static {
        LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
                .withServices("kms");
        LOCALSTACK.start();
    }

    private LocalStackTestKms() {
    }

    public static KmsClient client() {
        return KmsClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();
    }

    /** Uma chave-mestra nova, para o teste não depender das chaves de outro. */
    public static String createKey(KmsClient kms) {
        return kms.createKey(request -> request.description("chave de teste")).keyMetadata().keyId();
    }
}
