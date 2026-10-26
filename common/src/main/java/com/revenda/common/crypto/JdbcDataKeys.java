package com.revenda.common.crypto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Lê {@code data_key} e desembrulha cada chave pela {@link KeyManagementPort} uma vez por contêiner; a
 * chave em claro fica só em memória. Versão que não está na memória (gravada por outro contêiner depois
 * de uma rotação) faz reler a tabela uma vez.
 */
@RequiredArgsConstructor
public class JdbcDataKeys implements DataKeys {

    private final JdbcClient jdbcClient;
    private final KeyManagementPort keyManagement;
    private final Map<DataKeyPurpose, Loaded> loaded = new ConcurrentHashMap<>();

    @Override
    public DataKey active(DataKeyPurpose purpose) {
        return current(purpose).active();
    }

    @Override
    public DataKey ofVersion(DataKeyPurpose purpose, int version) {
        return current(purpose).find(version)
                .or(() -> loaded.compute(purpose, (key, previous) -> load(key)).find(version))
                .orElseThrow(() -> new IllegalStateException(
                        "Chave de dados " + purpose + " indisponível na versão " + version));
    }

    @Override
    public List<DataKey> usable(DataKeyPurpose purpose) {
        return current(purpose).usable();
    }

    private Loaded current(DataKeyPurpose purpose) {
        return loaded.computeIfAbsent(purpose, this::load);
    }

    private Loaded load(DataKeyPurpose purpose) {
        List<Stored> stored = jdbcClient.sql("""
                        SELECT version, encrypted_key, active
                          FROM data_key
                         WHERE purpose = :purpose
                         ORDER BY version DESC
                        """)
                .param("purpose", purpose.name())
                .query((row, index) -> new Stored(row.getInt("version"), row.getBytes("encrypted_key"),
                        row.getBoolean("active")))
                .list();
        DataKey active = null;
        List<DataKey> usable = new ArrayList<>();
        for (Stored key : stored) {
            DataKey unwrapped = new DataKey(key.version(),
                    keyManagement.decrypt(key.encrypted(), DataKeys.wrapContext(purpose, key.version())));
            usable.add(unwrapped);
            if (key.active()) {
                active = unwrapped;
            }
        }
        if (active == null) {
            throw new IllegalStateException("Sem chave de dados " + purpose + " ativa em data_key");
        }
        return new Loaded(active, List.copyOf(usable));
    }

    private record Loaded(DataKey active, List<DataKey> usable) {

        Optional<DataKey> find(int version) {
            return usable.stream().filter(key -> key.version() == version).findFirst();
        }
    }

    private record Stored(int version, byte[] encrypted, boolean active) {
    }
}
