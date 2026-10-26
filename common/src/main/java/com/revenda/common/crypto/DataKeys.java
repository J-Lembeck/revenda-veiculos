package com.revenda.common.crypto;

import java.util.List;
import java.util.Map;

/** As chaves de dados do serviço, já desembrulhadas, por finalidade. */
public interface DataKeys {

    /** A chave com que se cifra agora. */
    DataKey active(DataKeyPurpose purpose);

    /** A chave de uma versão, para ler o que foi gravado com ela. */
    DataKey ofVersion(DataKeyPurpose purpose, int version);

    /** As chaves que ainda leem, da mais nova para a mais antiga. */
    List<DataKey> usable(DataKeyPurpose purpose);

    /** O contexto que amarra a chave embrulhada à tabela, à finalidade e à versão dela. */
    static Map<String, String> wrapContext(DataKeyPurpose purpose, int version) {
        return Map.of("table", "data_key", "purpose", purpose.name(), "version", String.valueOf(version));
    }

    record DataKey(int version, byte[] material) {
    }
}
