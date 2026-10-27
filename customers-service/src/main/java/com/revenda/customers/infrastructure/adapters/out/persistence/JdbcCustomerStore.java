package com.revenda.customers.infrastructure.adapters.out.persistence;

import com.revenda.common.crypto.BlindIndexer;
import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.crypto.FieldRef;
import com.revenda.common.jdbc.UniqueKeys;
import com.revenda.common.time.SqlTime;
import com.revenda.customers.domain.model.Address;
import com.revenda.customers.domain.model.Cpf;
import com.revenda.customers.domain.model.Customer;
import com.revenda.customers.domain.model.CustomerDetails;
import com.revenda.customers.domain.model.CustomerStatus;
import com.revenda.customers.domain.model.CustomersError;
import com.revenda.customers.domain.model.EmailAddress;
import com.revenda.customers.domain.model.Phone;
import com.revenda.customers.domain.ports.out.LoadCustomerPort;
import com.revenda.customers.domain.ports.out.SaveCustomerPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * O cadastro na tabela {@code customer}: CPF, e-mail, telefone, nascimento e endereço cifrados aqui, cada um amarrado
 * à própria coluna e linha; o CPF também vira o índice cego da unicidade. O resto do serviço só vê o valor em claro.
 */
@Repository
@RequiredArgsConstructor
class JdbcCustomerStore implements SaveCustomerPort, LoadCustomerPort {

    private static final String TABLE = "customer";
    private static final String CPF_INDEX_DOMAIN = "cpf";
    private static final Map<String, CustomersError> UNIQUE_KEYS = Map.of(
            "customer_subject_key", CustomersError.CUSTOMER_ALREADY_REGISTERED,
            "customer_cpf_index_key", CustomersError.CPF_ALREADY_REGISTERED);

    private final JdbcClient jdbc;
    private final FieldCipher cipher;
    private final BlindIndexer indexer;
    private final ObjectMapper json;

    @Override
    public void insert(Customer customer) {
        UUID id = customer.id();
        CustomerDetails details = customer.details();
        String cpf = details.cpf().digits();
        UniqueKeys.translating(UNIQUE_KEYS, () -> jdbc.sql("""
                        INSERT INTO customer (id, identity_subject, full_name, cpf_enc, cpf_index, email_enc, phone_enc,
                                              birth_date_enc, address_enc, status, version, created_at, updated_at)
                        VALUES (:id, :identitySubject, :fullName, :cpfEnc, :cpfIndex, :emailEnc, :phoneEnc,
                                :birthDateEnc, :addressEnc, :status, :version, :createdAt, :updatedAt)""")
                .param("id", id)
                .param("identitySubject", customer.identitySubject())
                .param("fullName", details.fullName())
                .param("cpfEnc", cipher.encrypt(field("cpf_enc", id), cpf))
                .param("cpfIndex", indexer.index(CPF_INDEX_DOMAIN, cpf))
                .param("emailEnc", cipher.encrypt(field("email_enc", id), customer.email().address()))
                .param("phoneEnc", cipher.encrypt(field("phone_enc", id), details.phone().number()))
                .param("birthDateEnc", cipher.encrypt(field("birth_date_enc", id), details.birthDate().toString()))
                .param("addressEnc", cipher.encrypt(field("address_enc", id),
                        json.writeValueAsString(StoredAddress.of(details.address()))))
                .param("status", customer.status().name())
                .param("version", customer.version())
                .param("createdAt", SqlTime.utc(customer.createdAt()))
                .param("updatedAt", SqlTime.utc(customer.updatedAt()))
                .update());
    }

    @Override
    public Optional<Customer> findActive(UUID id) {
        return jdbc.sql("""
                        SELECT id, identity_subject, full_name, cpf_enc, email_enc, phone_enc, birth_date_enc,
                               address_enc, status, version, created_at, updated_at
                          FROM customer
                         WHERE id = :id AND status = 'ACTIVE'""")
                .param("id", id)
                .query((row, index) -> customer(row))
                .optional();
    }

    @Override
    public boolean existsActiveFor(String identitySubject) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM customer WHERE identity_subject = :subject AND status = 'ACTIVE')")
                .param("subject", identitySubject)
                .query(Boolean.class)
                .single();
    }

    private Customer customer(ResultSet row) throws SQLException {
        UUID id = row.getObject("id", UUID.class);
        CustomerDetails details = new CustomerDetails(
                row.getString("full_name"),
                new Cpf(decrypt(row, "cpf_enc", id)),
                LocalDate.parse(decrypt(row, "birth_date_enc", id)),
                new Phone(decrypt(row, "phone_enc", id)),
                json.readValue(decrypt(row, "address_enc", id), StoredAddress.class).toAddress());
        return new Customer(id, row.getString("identity_subject"), new EmailAddress(decrypt(row, "email_enc", id)),
                details, CustomerStatus.valueOf(row.getString("status")), row.getInt("version"),
                SqlTime.instantOrNull(row, "created_at"), SqlTime.instantOrNull(row, "updated_at"));
    }

    private String decrypt(ResultSet row, String column, UUID id) throws SQLException {
        return cipher.decrypt(field(column, id), row.getBytes(column));
    }

    private static FieldRef field(String column, UUID id) {
        return new FieldRef(TABLE, column, id);
    }

    /** O endereço como JSON dentro da coluna cifrada; o formato gravado não depende dos nomes do domínio. */
    private record StoredAddress(String zipCode, String street, String number, String complement, String district,
            String city, String state) {

        static StoredAddress of(Address address) {
            return new StoredAddress(address.zipCode(), address.street(), address.number(), address.complement(),
                    address.district(), address.city(), address.state());
        }

        Address toAddress() {
            return new Address(zipCode, street, number, complement, district, city, state);
        }

        @Override
        public String toString() {
            return "StoredAddress[dados pessoais omitidos]";
        }
    }
}
