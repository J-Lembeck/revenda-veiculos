package com.revenda.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.revenda.common.crypto.BlindIndexer;
import com.revenda.common.crypto.DataKeyPurpose;
import com.revenda.common.crypto.DataKeys;
import com.revenda.common.crypto.JdbcDataKeys;
import com.revenda.common.crypto.KeyManagementPort;
import com.revenda.common.crypto.KeyManagementPort.GeneratedKey;
import com.revenda.common.test.LocalKeyManagement;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.time.SqlTime;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CustomersSchemaTests {

    private static final OffsetDateTime NOW = SqlTime.utc(Instant.parse("2026-10-27T12:00:00Z"));
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final List<String> PERSONAL_COLUMNS = List.of("identity_subject", "full_name", "cpf_enc",
            "cpf_index", "email_enc", "phone_enc", "birth_date_enc", "address_enc");
    private static final Set<String> PERSONAL_IN_CLEAR = Set.of("identity_subject", "full_name");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "customers");
    }

    @Test
    void theApplicationConnectsWithTheAppRole() {
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo("customers_app");
    }

    @Test
    void theMigrationRanWithTheOwnerRole() throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAs("customers_owner", "customers");
                Statement statement = owner.createStatement()) {
            assertThat(single(statement, "SELECT installed_by FROM flyway_schema_history WHERE version = '1'"))
                    .isEqualTo("customers_owner");
            assertThat(single(statement, "SELECT tableowner FROM pg_tables WHERE tablename = 'customer'"))
                    .isEqualTo("customers_owner");
            assertThat(single(statement, "SELECT tableowner FROM pg_tables WHERE tablename = 'data_key'"))
                    .isEqualTo("customers_owner");
        }
    }

    @Test
    void theAppRoleWritesAndReadsTheCustomers() {
        UUID id = insert(Map.of());

        String status = jdbc.sql("SELECT status FROM customer WHERE id = :id").param("id", id).query(String.class).single();

        assertThat(status).isEqualTo("ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE audit_log SET summary = 'alterado'",
            "DELETE FROM audit_log",
            "TRUNCATE audit_log",
            "ALTER TABLE customer ADD COLUMN extra integer",
            "DROP TABLE data_key"})
    void theAppRoleCannotRewriteTheAuditNorChangeTheSchema(String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update())
                .rootCause()
                .isInstanceOf(SQLException.class)
                .extracting(cause -> ((SQLException) cause).getSQLState())
                .isEqualTo(INSUFFICIENT_PRIVILEGE);
    }

    @Test
    void theAppRoleAppendsToTheAuditLog() {
        int inserted = jdbc.sql("""
                        INSERT INTO audit_log (occurred_at, actor_type, action, entity_type, summary)
                        VALUES (:at, 'SYSTEM', 'CUSTOMER_REGISTERED', 'CUSTOMER', 'Cliente cadastrado')""")
                .param("at", NOW)
                .update();

        assertThat(inserted).isEqualTo(1);
    }

    static Stream<Arguments> inconsistentCustomers() {
        Stream<Arguments> incomplete = PERSONAL_COLUMNS.stream()
                .map(column -> arguments("customer_active_is_complete", with(column, null)));
        Stream<Arguments> others = Stream.of(
                arguments("customer_status_valid", Map.of("status", "DELETED")),
                arguments("customer_anonymized_is_empty", Map.of("status", "ANONYMIZED", "anonymized_at", NOW)),
                arguments("customer_anonymized_is_empty", anonymized(Map.of("anonymized_at", NOW, "cpf_index", bytes()))),
                arguments("customer_anonymized_is_empty", with(anonymized(Map.of()), "anonymized_at", null)));
        return Stream.concat(incomplete, others);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("inconsistentCustomers")
    void theDatabaseRefusesAnInconsistentCustomer(String constraint, Map<String, Object> overrides) {
        assertThatThrownBy(() -> insert(overrides))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    void anAnonymizedCustomerKeepsOnlyTheId() {
        UUID id = insert(anonymized(Map.of("anonymized_at", NOW, "deletion_requested_at", NOW)));

        assertThat(jdbc.sql("SELECT status FROM customer WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("ANONYMIZED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"identity_subject", "cpf_index"})
    void subjectAndCpfIndexAreUniqueAmongActiveCustomers(String column) {
        Map<String, Object> first = customer(Map.of());
        insertRow(first);

        assertThatThrownBy(() -> insert(Map.of(column, first.get(column))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(column.equals("cpf_index") ? "customer_cpf_index_key" : "customer_subject_key");
    }

    @Test
    void theSameCpfGivesTheSameBlindIndexAndTheSecondCustomerIsRefused() {
        BlindIndexer indexer = new BlindIndexer(keysWrittenInTheTable(new LocalKeyManagement()));
        String cpf = SyntheticData.cpf();
        insert(Map.of("cpf_index", indexer.index("cpf", cpf)));
        insert(Map.of("cpf_index", indexer.index("cpf", SyntheticData.cpf())));

        assertThatThrownBy(() -> insert(Map.of("cpf_index", indexer.index("cpf", cpf))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("customer_cpf_index_key");
    }

    @Test
    void theDataKeysStoredWrappedAreTheOnesTheCipherReads() {
        KeyManagementPort keyManagement = new LocalKeyManagement();
        jdbc.sql("DELETE FROM data_key").update();
        GeneratedKey old = keyManagement.generateDataKey(DataKeys.wrapContext(DataKeyPurpose.FIELD, 1));
        GeneratedKey current = keyManagement.generateDataKey(DataKeys.wrapContext(DataKeyPurpose.FIELD, 2));
        insertKey(DataKeyPurpose.FIELD.name(), 1, old.encrypted(), false);
        insertKey(DataKeyPurpose.FIELD.name(), 2, current.encrypted(), true);

        DataKeys keys = new JdbcDataKeys(jdbc, keyManagement);

        assertThat(keys.active(DataKeyPurpose.FIELD).version()).isEqualTo(2);
        assertThat(keys.active(DataKeyPurpose.FIELD).material()).isEqualTo(current.plaintext());
        assertThat(keys.ofVersion(DataKeyPurpose.FIELD, 1).material()).isEqualTo(old.plaintext());
        assertThat(jdbc.sql("SELECT encrypted_key FROM data_key WHERE version = 2").query(byte[].class).single())
                .isNotEqualTo(current.plaintext());
    }

    @Test
    void eachPurposeHasOneActiveKey() {
        jdbc.sql("DELETE FROM data_key").update();
        insertKey("FIELD", 1, bytes(), true);
        insertKey("BLIND_INDEX", 1, bytes(), true);

        assertThatThrownBy(() -> insertKey("FIELD", 2, bytes(), true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("data_key_one_active");
    }

    @Test
    void eachVersionOfAPurposeIsOneRow() {
        jdbc.sql("DELETE FROM data_key").update();
        insertKey("FIELD", 1, bytes(), false);

        assertThatThrownBy(() -> insertKey("FIELD", 1, bytes(), true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("data_key_pkey");
    }

    @Test
    void aDataKeyHasAKnownPurpose() {
        assertThatThrownBy(() -> insertKey("OTHER", 1, bytes(), true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("data_key_purpose_check");
    }

    @Test
    void everyPersonalColumnIsMarkedAndOnlyTheNameAndSubjectAreInClear() {
        List<Column> columns = jdbc.sql("""
                        SELECT a.attname AS name, format_type(a.atttypid, a.atttypmod) AS type,
                               col_description(a.attrelid, a.attnum) AS comment
                          FROM pg_attribute a
                         WHERE a.attrelid = 'customer'::regclass AND a.attnum > 0 AND NOT a.attisdropped""")
                .query(Column.class)
                .list();

        assertThat(columns).filteredOn(column -> column.comment() != null && column.comment().startsWith("[pessoal]"))
                .extracting(Column::name)
                .containsExactlyInAnyOrderElementsOf(PERSONAL_COLUMNS);
        assertThat(columns).filteredOn(column -> PERSONAL_COLUMNS.contains(column.name())
                        && !PERSONAL_IN_CLEAR.contains(column.name()))
                .extracting(Column::type)
                .containsOnly("bytea");
        assertThat(jdbc.sql("SELECT col_description('data_key'::regclass, 3)").query(String.class).single())
                .startsWith("[segredo]");
    }

    private UUID insert(Map<String, Object> overrides) {
        Map<String, Object> row = customer(overrides);
        insertRow(row);
        return (UUID) row.get("id");
    }

    private void insertRow(Map<String, Object> row) {
        String columns = String.join(", ", row.keySet());
        String values = ":" + String.join(", :", row.keySet());
        jdbc.sql("INSERT INTO customer (" + columns + ") VALUES (" + values + ")").params(row).update();
    }

    private void insertKey(String purpose, int version, byte[] encryptedKey, boolean active) {
        jdbc.sql("""
                        INSERT INTO data_key (purpose, version, encrypted_key, active, created_at)
                        VALUES (:purpose, :version, :key, :active, :at)""")
                .param("purpose", purpose)
                .param("version", version)
                .param("key", encryptedKey)
                .param("active", active)
                .param("at", NOW)
                .update();
    }

    private DataKeys keysWrittenInTheTable(KeyManagementPort keyManagement) {
        jdbc.sql("DELETE FROM data_key").update();
        for (DataKeyPurpose purpose : DataKeyPurpose.values()) {
            insertKey(purpose.name(), 1, keyManagement.generateDataKey(DataKeys.wrapContext(purpose, 1)).encrypted(), true);
        }
        return new JdbcDataKeys(jdbc, keyManagement);
    }

    private static Map<String, Object> customer(Map<String, Object> overrides) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("identity_subject", UUID.randomUUID().toString());
        row.put("full_name", "Cliente Sintético");
        row.put("cpf_enc", bytes());
        row.put("cpf_index", bytes());
        row.put("email_enc", bytes());
        row.put("phone_enc", bytes());
        row.put("birth_date_enc", bytes());
        row.put("address_enc", bytes());
        row.put("created_at", NOW);
        row.put("updated_at", NOW);
        row.putAll(overrides);
        return row;
    }

    private static Map<String, Object> anonymized(Map<String, Object> overrides) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", "ANONYMIZED");
        PERSONAL_COLUMNS.forEach(column -> row.put(column, null));
        row.putAll(overrides);
        return row;
    }

    private static Map<String, Object> with(String column, Object value) {
        return with(Map.of(), column, value);
    }

    private static Map<String, Object> with(Map<String, Object> base, String column, Object value) {
        Map<String, Object> row = new HashMap<>(base);
        row.put(column, value);
        return row;
    }

    private static byte[] bytes() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return value;
    }

    private static String single(Statement statement, String sql) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private record Column(String name, String type, String comment) {
    }
}
