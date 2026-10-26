package com.revenda.common.crypto;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DataKeySpec;
import software.amazon.awssdk.services.kms.model.GenerateDataKeyResponse;

@RequiredArgsConstructor
public class KmsKeyManagement implements KeyManagementPort {

    private final KmsClient kms;
    private final String keyId;

    @Override
    public GeneratedKey generateDataKey(Map<String, String> context) {
        GenerateDataKeyResponse response = kms.generateDataKey(request -> request
                .keyId(keyId)
                .keySpec(DataKeySpec.AES_256)
                .encryptionContext(context));
        return new GeneratedKey(response.plaintext().asByteArray(), response.ciphertextBlob().asByteArray());
    }

    @Override
    public byte[] decrypt(byte[] encryptedKey, Map<String, String> context) {
        return kms.decrypt(request -> request
                        .keyId(keyId)
                        .ciphertextBlob(SdkBytes.fromByteArray(encryptedKey))
                        .encryptionContext(context))
                .plaintext()
                .asByteArray();
    }
}
