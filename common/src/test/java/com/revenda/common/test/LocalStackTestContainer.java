package com.revenda.common.test;

import java.net.URI;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * Um LocalStack só para a suíte inteira, na mesma imagem do ambiente local, com o KMS e o SQS. Sobe no bloco
 * estático e nunca é parado, como o {@link PostgresTestDatabase}; o {@link LocalStackTestKms} e o
 * {@link LocalStackTestSqs} montam os clientes dele.
 */
public final class LocalStackTestContainer {

    private static final LocalStackContainer LOCALSTACK;

    static {
        LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
                .withServices("kms", "sqs");
        LOCALSTACK.start();
    }

    private LocalStackTestContainer() {
    }

    public static URI endpoint() {
        return LOCALSTACK.getEndpoint();
    }

    public static Region region() {
        return Region.of(LOCALSTACK.getRegion());
    }

    public static AwsCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
    }
}
