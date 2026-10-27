package com.revenda.customers.infrastructure.adapters.out.persistence;

import com.revenda.common.crypto.DataKeyPurpose;
import com.revenda.common.crypto.DataKeys;
import com.revenda.common.crypto.KeyManagementPort;
import com.revenda.common.crypto.KeyManagementPort.GeneratedKey;
import com.revenda.common.time.SqlTime;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cria na subida, antes de o serviço atender, a chave de dados de cada finalidade que ainda não tem versão nenhuma: o
 * KMS gera a chave e só a forma embrulhada vai para {@code data_key}. Dois contêineres subindo juntos geram uma chave
 * cada, mas só um {@code INSERT} entra; o outro descarta a sua e segue com a que ficou.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class JdbcDataKeyProvisioner implements SmartInitializingSingleton {

    static final int FIRST_VERSION = 1;

    private final JdbcClient jdbc;
    private final KeyManagementPort keyManagement;
    private final Clock clock;

    @Override
    public void afterSingletonsInstantiated() {
        provisionMissing();
    }

    /** As finalidades cuja chave esta chamada gravou. */
    List<DataKeyPurpose> provisionMissing() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "A chave de dados é criada sem transação aberta: a chamada ao KMS não segura transação");
        }
        List<DataKeyPurpose> created = new ArrayList<>();
        for (DataKeyPurpose purpose : DataKeyPurpose.values()) {
            Versions versions = versionsOf(purpose);
            if (versions.active() > 0) {
                continue;
            }
            if (versions.total() > 0) {
                throw new IllegalStateException("Chave de dados " + purpose
                        + " sem versão ativa em data_key: ativar uma versão é rotação, não a primeira subida");
            }
            if (insertFirstVersion(purpose)) {
                created.add(purpose);
                log.info("Chave de dados {} criada na versão {}", purpose, FIRST_VERSION);
            } else {
                log.info("Chave de dados {} criada por outro contêiner; a gerada aqui foi descartada", purpose);
            }
        }
        return created;
    }

    private Versions versionsOf(DataKeyPurpose purpose) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE active) AS active, count(*) AS total
                          FROM data_key
                         WHERE purpose = :purpose""")
                .param("purpose", purpose.name())
                .query((row, index) -> new Versions(row.getLong("active"), row.getLong("total")))
                .single();
    }

    private boolean insertFirstVersion(DataKeyPurpose purpose) {
        GeneratedKey key = keyManagement.generateDataKey(DataKeys.wrapContext(purpose, FIRST_VERSION));
        Arrays.fill(key.plaintext(), (byte) 0);
        return jdbc.sql("""
                        INSERT INTO data_key (purpose, version, encrypted_key, active, created_at)
                        VALUES (:purpose, :version, :encryptedKey, true, :now)
                        ON CONFLICT DO NOTHING""")
                .param("purpose", purpose.name())
                .param("version", FIRST_VERSION)
                .param("encryptedKey", key.encrypted())
                .param("now", SqlTime.utc(clock.instant()))
                .update() == 1;
    }

    private record Versions(long active, long total) {
    }
}
