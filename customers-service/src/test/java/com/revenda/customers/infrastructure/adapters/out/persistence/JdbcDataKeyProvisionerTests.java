package com.revenda.customers.infrastructure.adapters.out.persistence;

import static com.revenda.common.crypto.DataKeyPurpose.BLIND_INDEX;
import static com.revenda.common.crypto.DataKeyPurpose.FIELD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.revenda.common.crypto.BlindIndexer;
import com.revenda.common.crypto.DataKeyPurpose;
import com.revenda.common.crypto.DataKeys;
import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.crypto.FieldRef;
import com.revenda.common.crypto.JdbcDataKeys;
import com.revenda.common.crypto.KeyManagementPort;
import com.revenda.common.crypto.KmsKeyManagement;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.time.SqlTime;
import com.revenda.customers.CustomersTestKms;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.kms.model.KmsException;

/**
 * A criação da chave de dados contra o KMS do LocalStack. Cada teste começa com a {@code data_key} vazia, como na
 * primeira subida, e devolve no fim as chaves que os outros contextos da suíte deixaram lá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CustomersTestKms.class)
@ExtendWith(OutputCaptureExtension.class)
class JdbcDataKeyProvisionerTests {

    private static final OffsetDateTime EARLIER = SqlTime.utc(Instant.parse("2026-10-27T09:00:00Z"));

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private KeyManagementPort keyManagement;

    @Autowired
    private JdbcDataKeyProvisioner provisioner;

    @Autowired
    private Clock clock;

    @Autowired
    private TransactionTemplate transaction;

    private List<StoredKey> keysOfTheSuite;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "customers");
    }

    @BeforeEach
    void startWithoutKeys() {
        keysOfTheSuite = storedKeys();
        jdbc.sql("DELETE FROM data_key").update();
    }

    @AfterEach
    void giveTheKeysBack() {
        jdbc.sql("DELETE FROM data_key").update();
        keysOfTheSuite.forEach(this::insert);
    }

    @Test
    void theServiceStartsWithTheKmsAndOneActiveKeyPerPurpose() {
        assertThat(keyManagement).isInstanceOf(KmsKeyManagement.class);
        assertThat(keysOfTheSuite).filteredOn(StoredKey::active)
                .extracting(StoredKey::purpose)
                .containsExactlyInAnyOrder(FIELD.name(), BLIND_INDEX.name());
        keysOfTheSuite.forEach(key -> assertThat(keyManagement.decrypt(key.encryptedKey(),
                DataKeys.wrapContext(DataKeyPurpose.valueOf(key.purpose()), key.version()))).hasSize(32));
    }

    @Test
    void theFirstStartCreatesBothKeysAndTheNextStartCreatesNone() {
        WatchedKeyManagement kms = new WatchedKeyManagement(keyManagement, () -> { });

        new JdbcDataKeyProvisioner(jdbc, kms, clock).afterSingletonsInstantiated();
        List<StoredKey> afterFirstStart = storedKeys();
        new JdbcDataKeyProvisioner(jdbc, kms, clock).afterSingletonsInstantiated();

        assertThat(afterFirstStart).extracting(StoredKey::purpose, StoredKey::version, StoredKey::active)
                .containsExactly(tuple(BLIND_INDEX.name(), 1, true), tuple(FIELD.name(), 1, true));
        assertThat(storedKeys()).usingRecursiveComparison().isEqualTo(afterFirstStart);
        assertThat(kms.generated()).isEqualTo(2);
    }

    @RepeatedTest(5)
    void twoContainersStartingTogetherLeaveOneActiveKeyPerPurposeAndNeitherFails() throws Exception {
        CyclicBarrier bothGenerated = new CyclicBarrier(2);
        WatchedKeyManagement kms = new WatchedKeyManagement(keyManagement, () -> await(bothGenerated));
        ExecutorService containers = Executors.newFixedThreadPool(2);
        try {
            Future<List<DataKeyPurpose>> first = containers.submit(
                    () -> new JdbcDataKeyProvisioner(jdbc, kms, clock).provisionMissing());
            Future<List<DataKeyPurpose>> second = containers.submit(
                    () -> new JdbcDataKeyProvisioner(jdbc, kms, clock).provisionMissing());

            List<DataKeyPurpose> createdByFirst = first.get(30, TimeUnit.SECONDS);
            List<DataKeyPurpose> createdBySecond = second.get(30, TimeUnit.SECONDS);

            assertThat(kms.generated()).isEqualTo(4);
            assertThat(concat(createdByFirst, createdBySecond)).containsExactlyInAnyOrder(FIELD, BLIND_INDEX);
        } finally {
            containers.shutdownNow();
        }
        assertThat(storedKeys()).extracting(StoredKey::purpose, StoredKey::version, StoredKey::active)
                .containsExactly(tuple(BLIND_INDEX.name(), 1, true), tuple(FIELD.name(), 1, true));
        JdbcDataKeys keys = new JdbcDataKeys(jdbc, keyManagement);
        assertThat(keys.active(FIELD).material()).hasSize(32);
        assertThat(keys.active(BLIND_INDEX).material()).hasSize(32);
    }

    @Test
    void aKeyActivatedMeanwhileByAnotherContainerWinsWithoutError() {
        WatchedKeyManagement kms = new WatchedKeyManagement(keyManagement, () -> {
            if (storedKeys().isEmpty()) {
                insert(new StoredKey(FIELD.name(), 2, keyManagement.generateDataKey(
                        DataKeys.wrapContext(FIELD, 2)).encrypted(), true, EARLIER));
            }
        });

        List<DataKeyPurpose> created = new JdbcDataKeyProvisioner(jdbc, kms, clock).provisionMissing();

        assertThat(created).containsExactly(BLIND_INDEX);
        assertThat(storedKeys()).extracting(StoredKey::purpose, StoredKey::version, StoredKey::active)
                .containsExactly(tuple(BLIND_INDEX.name(), 1, true), tuple(FIELD.name(), 2, true));
    }

    @Test
    void theStoredKeyIsOnlyTheWrappedOneAndTheCipherAndIndexWorkWithIt(CapturedOutput output) {
        provisioner.afterSingletonsInstantiated();

        byte[] fieldKey = plainKeyOf(FIELD);
        byte[] indexKey = plainKeyOf(BLIND_INDEX);
        JdbcDataKeys keys = new JdbcDataKeys(jdbc, keyManagement);
        JdbcDataKeys otherContainer = new JdbcDataKeys(jdbc, keyManagement);
        String cpf = SyntheticData.cpf();
        FieldRef cpfColumn = new FieldRef("customer", "cpf_enc", UUID.randomUUID());

        assertThat(keys.active(FIELD).material()).isEqualTo(fieldKey);
        assertThat(keys.active(BLIND_INDEX).material()).isEqualTo(indexKey);
        assertThat(new FieldCipher(otherContainer).decrypt(cpfColumn, new FieldCipher(keys).encrypt(cpfColumn, cpf)))
                .isEqualTo(cpf);
        assertThat(new BlindIndexer(keys).index("cpf", cpf))
                .isEqualTo(new BlindIndexer(otherContainer).index("cpf", cpf))
                .isNotEqualTo(new BlindIndexer(keys).index("cpf", SyntheticData.cpf()));
        assertThatThrownBy(() -> keyManagement.decrypt(stored(FIELD).encryptedKey(), DataKeys.wrapContext(BLIND_INDEX, 1)))
                .isInstanceOf(KmsException.class);
        assertThat(output)
                .contains("Chave de dados FIELD criada na versão 1", "Chave de dados BLIND_INDEX criada na versão 1")
                .doesNotContain(Base64.getEncoder().encodeToString(fieldKey), HexFormat.of().formatHex(fieldKey))
                .doesNotContain(Base64.getEncoder().encodeToString(indexKey), HexFormat.of().formatHex(indexKey));
    }

    @Test
    void aPurposeWithVersionsButNoneActiveStopsTheStartWithoutCallingTheKms() {
        insert(new StoredKey(FIELD.name(), 1, keyManagement.generateDataKey(DataKeys.wrapContext(FIELD, 1)).encrypted(),
                false, EARLIER));
        WatchedKeyManagement kms = new WatchedKeyManagement(keyManagement, () -> { });

        assertThatThrownBy(() -> new JdbcDataKeyProvisioner(jdbc, kms, clock).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Chave de dados FIELD sem versão ativa em data_key");
        assertThat(kms.generated()).isZero();
        assertThat(storedKeys()).hasSize(1);
    }

    @Test
    void insideAnOpenTransactionNothingIsCreated() {
        WatchedKeyManagement kms = new WatchedKeyManagement(keyManagement, () -> { });
        JdbcDataKeyProvisioner container = new JdbcDataKeyProvisioner(jdbc, kms, clock);

        transaction.executeWithoutResult(status -> assertThatThrownBy(container::provisionMissing)
                .isInstanceOf(IllegalTransactionStateException.class));

        assertThat(kms.generated()).isZero();
        assertThat(storedKeys()).isEmpty();
    }

    private byte[] plainKeyOf(DataKeyPurpose purpose) {
        StoredKey stored = stored(purpose);
        byte[] plain = keyManagement.decrypt(stored.encryptedKey(), DataKeys.wrapContext(purpose, stored.version()));
        assertThat(plain).hasSize(32);
        assertThat(stored.encryptedKey()).isNotEqualTo(plain);
        assertThat(new String(stored.encryptedKey(), StandardCharsets.ISO_8859_1))
                .doesNotContain(new String(plain, StandardCharsets.ISO_8859_1));
        return plain;
    }

    private StoredKey stored(DataKeyPurpose purpose) {
        return storedKeys().stream().filter(key -> key.purpose().equals(purpose.name())).findFirst().orElseThrow();
    }

    private List<StoredKey> storedKeys() {
        return jdbc.sql("SELECT purpose, version, encrypted_key, active, created_at FROM data_key ORDER BY purpose, version")
                .query((row, index) -> new StoredKey(row.getString("purpose"), row.getInt("version"),
                        row.getBytes("encrypted_key"), row.getBoolean("active"),
                        row.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    private void insert(StoredKey key) {
        jdbc.sql("""
                        INSERT INTO data_key (purpose, version, encrypted_key, active, created_at)
                        VALUES (:purpose, :version, :encryptedKey, :active, :createdAt)""")
                .param("purpose", key.purpose())
                .param("version", key.version())
                .param("encryptedKey", key.encryptedKey())
                .param("active", key.active())
                .param("createdAt", key.createdAt())
                .update();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception interrupted) {
            throw new IllegalStateException("O outro contêiner não chegou à geração da chave", interrupted);
        }
    }

    private static List<DataKeyPurpose> concat(List<DataKeyPurpose> first, List<DataKeyPurpose> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private record StoredKey(String purpose, int version, byte[] encryptedKey, boolean active, OffsetDateTime createdAt) {
    }

    /** O KMS da suíte, contando as chaves geradas, com um passo entre gerar a chave e devolvê-la. */
    private static final class WatchedKeyManagement implements KeyManagementPort {

        private final KeyManagementPort kms;
        private final Runnable afterGenerate;
        private final AtomicInteger generated = new AtomicInteger();

        WatchedKeyManagement(KeyManagementPort kms, Runnable afterGenerate) {
            this.kms = kms;
            this.afterGenerate = afterGenerate;
        }

        @Override
        public GeneratedKey generateDataKey(Map<String, String> context) {
            GeneratedKey key = kms.generateDataKey(context);
            generated.incrementAndGet();
            afterGenerate.run();
            return key;
        }

        @Override
        public byte[] decrypt(byte[] encryptedKey, Map<String, String> context) {
            return kms.decrypt(encryptedKey, context);
        }

        int generated() {
            return generated.get();
        }
    }
}
