package com.revenda.cryptoprobe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.revenda.common.crypto.BlindIndexer;
import com.revenda.common.crypto.DataKeyPurpose;
import com.revenda.common.crypto.DataKeys;
import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.crypto.FieldRef;
import com.revenda.common.crypto.JdbcDataKeys;
import com.revenda.common.crypto.KeyManagementPort;
import com.revenda.common.crypto.KeyManagementPort.GeneratedKey;
import com.revenda.common.crypto.KmsKeyManagement;
import com.revenda.common.test.LocalStackTestKms;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.time.SqlTime;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsException;

@SpringBootTest(classes = CryptoProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:crypto-probe-schema.sql"})
class KmsCryptoTests {

    private static final String CPF = "52998224725";

    @Autowired
    private KeyManagementPort keyManagement;

    @Autowired
    private KmsClient kms;

    @Autowired
    private JdbcClient database;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
        try (KmsClient kms = LocalStackTestKms.client()) {
            String keyId = LocalStackTestKms.createKey(kms);
            registry.add("revenda.crypto.kms-key-id", () -> keyId);
        }
    }

    @BeforeEach
    void startWithoutKeys() {
        database.sql("DELETE FROM probe_customer").update();
        database.sql("DELETE FROM data_key").update();
    }

    @Test
    void serviceWithTheKmsKeyGetsTheKmsAdapter() {
        assertThat(keyManagement).isInstanceOf(KmsKeyManagement.class);
    }

    @Test
    void kmsGeneratesTheDataKeyAndOpensItOnlyWithTheSameContext() {
        Map<String, String> context = DataKeys.wrapContext(DataKeyPurpose.FIELD, 1);

        GeneratedKey key = keyManagement.generateDataKey(context);

        assertThat(key.plaintext()).hasSize(32);
        assertThat(key.encrypted()).isNotEqualTo(key.plaintext());
        assertThat(keyManagement.decrypt(key.encrypted(), context)).isEqualTo(key.plaintext());
        assertThatThrownBy(() -> keyManagement.decrypt(key.encrypted(), DataKeys.wrapContext(DataKeyPurpose.FIELD, 2)))
                .isInstanceOf(KmsException.class);
        assertThatThrownBy(() -> keyManagement.decrypt(key.encrypted(),
                DataKeys.wrapContext(DataKeyPurpose.BLIND_INDEX, 1)))
                .isInstanceOf(KmsException.class);
    }

    @Test
    void cpfIsStoredCipheredAndIndexedAndReadBackThroughTheKms() {
        provision(DataKeyPurpose.FIELD, 1, true);
        provision(DataKeyPurpose.BLIND_INDEX, 1, true);
        JdbcDataKeys keys = new JdbcDataKeys(database, keyManagement);
        FieldCipher cipher = new FieldCipher(keys);
        BlindIndexer indexer = new BlindIndexer(keys);
        UUID customerId = UUID.randomUUID();
        FieldRef cpf = new FieldRef("probe_customer", "cpf_enc", customerId);

        database.sql("INSERT INTO probe_customer (id, cpf_enc, cpf_index) VALUES (:id, :cpf, :index)")
                .param("id", customerId)
                .param("cpf", cipher.encrypt(cpf, CPF))
                .param("index", indexer.index("cpf", CPF))
                .update();

        Map<String, Object> raw = database.sql("""
                        SELECT encode(cpf_enc, 'escape') AS cpf, encode(cpf_index, 'hex') AS cpf_index
                          FROM probe_customer WHERE id = :id
                        """)
                .param("id", customerId).query().singleRow();
        assertThat((String) raw.get("cpf")).doesNotContain(CPF).doesNotContain("982247");
        assertThat((String) raw.get("cpf_index")).doesNotContain(CPF);
        byte[] stored = database.sql("SELECT cpf_enc FROM probe_customer WHERE cpf_index = :index")
                .param("index", indexer.index("cpf", CPF)).query(byte[].class).single();
        assertThat(new JdbcDataKeys(database, keyManagement).active(DataKeyPurpose.FIELD).material())
                .isEqualTo(keys.active(DataKeyPurpose.FIELD).material());
        assertThat(new FieldCipher(new JdbcDataKeys(database, keyManagement)).decrypt(cpf, stored)).isEqualTo(CPF);
    }

    @Test
    void keyStoredInTheDatabaseIsNeverThePlainKey() {
        GeneratedKey key = provision(DataKeyPurpose.FIELD, 1, true);

        byte[] stored = database.sql("SELECT encrypted_key FROM data_key WHERE purpose = 'FIELD' AND version = 1")
                .query(byte[].class).single();

        assertThat(stored).isNotEqualTo(key.plaintext());
        assertThat(new String(stored, StandardCharsets.ISO_8859_1))
                .doesNotContain(new String(key.plaintext(), StandardCharsets.ISO_8859_1));
    }

    @Test
    void versionRotatedByAnotherContainerIsLoadedWhenFirstSeen() {
        provision(DataKeyPurpose.FIELD, 1, true);
        JdbcDataKeys keys = new JdbcDataKeys(database, keyManagement);
        FieldRef cpf = new FieldRef("probe_customer", "cpf_enc", UUID.randomUUID());
        byte[] withVersionOne = new FieldCipher(keys).encrypt(cpf, CPF);

        database.sql("UPDATE data_key SET active = false WHERE purpose = 'FIELD'").update();
        provision(DataKeyPurpose.FIELD, 2, true);
        JdbcDataKeys otherContainer = new JdbcDataKeys(database, keyManagement);
        byte[] withVersionTwo = new FieldCipher(otherContainer).encrypt(cpf, CPF);

        assertThat(new FieldCipher(keys).decrypt(cpf, withVersionTwo)).isEqualTo(CPF);
        assertThat(new FieldCipher(otherContainer).decrypt(cpf, withVersionOne)).isEqualTo(CPF);
        assertThat(keys.active(DataKeyPurpose.FIELD).version()).isEqualTo(2);
    }

    @Test
    void withoutAnActiveKeyNothingIsCiphered() {
        provision(DataKeyPurpose.FIELD, 1, false);
        FieldCipher cipher = new FieldCipher(new JdbcDataKeys(database, keyManagement));

        assertThatThrownBy(() -> cipher.encrypt(new FieldRef("probe_customer", "cpf_enc", UUID.randomUUID()), CPF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Sem chave de dados FIELD ativa em data_key");
    }

    @Test
    void keyWrappedUnderAnotherMasterKeyIsRefused() {
        String otherKeyId = LocalStackTestKms.createKey(kms);
        GeneratedKey foreign = new KmsKeyManagement(kms, otherKeyId)
                .generateDataKey(DataKeys.wrapContext(DataKeyPurpose.FIELD, 1));
        insertKey(DataKeyPurpose.FIELD, 1, foreign.encrypted(), true);

        assertThatThrownBy(() -> new JdbcDataKeys(database, keyManagement).active(DataKeyPurpose.FIELD))
                .isInstanceOf(KmsException.class);
    }

    private GeneratedKey provision(DataKeyPurpose purpose, int version, boolean active) {
        GeneratedKey key = keyManagement.generateDataKey(DataKeys.wrapContext(purpose, version));
        insertKey(purpose, version, key.encrypted(), active);
        return key;
    }

    private void insertKey(DataKeyPurpose purpose, int version, byte[] encrypted, boolean active) {
        database.sql("""
                        INSERT INTO data_key (purpose, version, encrypted_key, active, created_at)
                        VALUES (:purpose, :version, :key, :active, :now)
                        """)
                .param("purpose", purpose.name())
                .param("version", version)
                .param("key", encrypted)
                .param("active", active)
                .param("now", SqlTime.utc(Instant.parse("2026-10-26T14:32:00Z")))
                .update();
    }
}
