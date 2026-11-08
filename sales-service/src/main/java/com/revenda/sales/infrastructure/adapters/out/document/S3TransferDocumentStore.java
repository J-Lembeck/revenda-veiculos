package com.revenda.sales.infrastructure.adapters.out.document;

import com.revenda.sales.domain.model.DocumentLink;
import com.revenda.sales.domain.ports.out.LinkTransferDocumentPort;
import com.revenda.sales.domain.ports.out.SaveTransferDocumentPort;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

/**
 * O documento no bucket privado, cifrado com a chave dos documentos (SSE-KMS, pedido em cada gravação, além do padrão
 * do bucket). O link é assinado aqui mesmo, sem chamar a AWS, e vale {@code linkTtl}; o nome do arquivo baixado não
 * leva nada do comprador.
 */
@Component
@RequiredArgsConstructor
class S3TransferDocumentStore implements SaveTransferDocumentPort, LinkTransferDocumentPort {

    private static final String PDF = "application/pdf";
    private static final String DOWNLOAD_NAME = "attachment; filename=\"documento-de-transferencia.pdf\"";

    private final S3Client s3;
    private final S3Presigner presigner;
    private final DocumentsProperties properties;

    @Override
    public void save(String key, byte[] pdf) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Gravação no bucket com transação aberta");
        }
        s3.putObject(request -> request
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(PDF)
                        .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                        .ssekmsKeyId(properties.kmsKeyId()),
                RequestBody.fromBytes(pdf));
    }

    @Override
    public DocumentLink link(String key) {
        PresignedGetObjectRequest signed = presigner.presignGetObject(request -> request
                .signatureDuration(properties.linkTtl())
                .getObjectRequest(get -> get
                        .bucket(properties.bucket())
                        .key(key)
                        .responseContentType(PDF)
                        .responseContentDisposition(DOWNLOAD_NAME)));
        return new DocumentLink(URI.create(signed.url().toString()), signed.expiration());
    }
}
