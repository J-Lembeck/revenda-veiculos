package com.revenda.sales;

import com.revenda.common.test.LocalStackTestContainer;
import com.revenda.common.test.LocalStackTestKms;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * O bucket dos documentos no LocalStack de teste, só para a suíte do {@code sales}: privado, cifrado por padrão com uma
 * chave do KMS própria (SSE-KMS), como o do ambiente local. Todo contexto Spring do serviço usa estes clientes.
 */
class SalesTestS3 {

    static final S3Client S3 = S3Client.builder()
            .httpClient(UrlConnectionHttpClient.create())
            .endpointOverride(LocalStackTestContainer.endpoint())
            .region(LocalStackTestContainer.region())
            .credentialsProvider(LocalStackTestContainer.credentials())
            .forcePathStyle(true)
            .build();
    static final String KMS_KEY_ID = createKey();
    static final String BUCKET = createBucket();

    static void register(DynamicPropertyRegistry registry) {
        registry.add("revenda.documents.bucket", () -> BUCKET);
        registry.add("revenda.documents.kms-key-id", () -> KMS_KEY_ID);
    }

    @Bean(destroyMethod = "")
    S3Client s3Client() {
        return S3;
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(LocalStackTestContainer.endpoint())
                .region(LocalStackTestContainer.region())
                .credentialsProvider(LocalStackTestContainer.credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static String createKey() {
        try (KmsClient kms = LocalStackTestKms.client()) {
            return LocalStackTestKms.createKey(kms);
        }
    }

    private static String createBucket() {
        String bucket = "transfer-documents-" + UUID.randomUUID().toString().substring(0, 8);
        S3.createBucket(request -> request.bucket(bucket));
        S3.putPublicAccessBlock(request -> request.bucket(bucket).publicAccessBlockConfiguration(block -> block
                .blockPublicAcls(true).ignorePublicAcls(true).blockPublicPolicy(true).restrictPublicBuckets(true)));
        S3.putBucketEncryption(request -> request.bucket(bucket).serverSideEncryptionConfiguration(encryption ->
                encryption.rules(rule -> rule.applyServerSideEncryptionByDefault(defaults -> defaults
                        .sseAlgorithm(ServerSideEncryption.AWS_KMS).kmsMasterKeyID(KMS_KEY_ID)))));
        return bucket;
    }
}
