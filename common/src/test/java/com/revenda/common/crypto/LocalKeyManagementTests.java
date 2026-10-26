package com.revenda.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.crypto.KeyManagementPort.GeneratedKey;
import com.revenda.common.test.LocalKeyManagement;
import org.junit.jupiter.api.Test;

class LocalKeyManagementTests {

    private final LocalKeyManagement keyManagement = new LocalKeyManagement();

    @Test
    void generatedKeyOpensOnlyWithItsContextAndItsMasterKey() {
        GeneratedKey key = keyManagement.generateDataKey(DataKeys.wrapContext(DataKeyPurpose.FIELD, 1));

        assertThat(key.plaintext()).hasSize(32);
        assertThat(key.encrypted()).isNotEqualTo(key.plaintext());
        assertThat(keyManagement.decrypt(key.encrypted(), DataKeys.wrapContext(DataKeyPurpose.FIELD, 1)))
                .isEqualTo(key.plaintext());
        assertThatThrownBy(() -> keyManagement.decrypt(key.encrypted(), DataKeys.wrapContext(DataKeyPurpose.FIELD, 2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LocalKeyManagement().decrypt(key.encrypted(),
                DataKeys.wrapContext(DataKeyPurpose.FIELD, 1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
