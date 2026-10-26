package com.revenda.common.crypto;

import java.util.Map;

/**
 * A chave-mestra que embrulha as chaves de dados (envelope). O contexto entra na cifra da chave e precisa
 * ser o mesmo na volta.
 */
public interface KeyManagementPort {

    GeneratedKey generateDataKey(Map<String, String> context);

    byte[] decrypt(byte[] encryptedKey, Map<String, String> context);

    /** A chave de dados de 256 bits em claro, só para a memória, e a mesma chave embrulhada, para o banco. */
    record GeneratedKey(byte[] plaintext, byte[] encrypted) {
    }
}
