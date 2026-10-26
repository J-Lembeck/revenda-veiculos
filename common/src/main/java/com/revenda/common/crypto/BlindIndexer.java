package com.revenda.common.crypto;

import com.revenda.common.crypto.DataKeys.DataKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;

/**
 * Índice cego: acha uma linha por um valor cifrado (o CPF) sem decifrar. É
 * {@code HMAC-SHA256(chave do domínio, valor)}, com a chave do domínio derivada da chave
 * {@code BLIND_INDEX} por HKDF-SHA256 ({@code info = "blind-index:" + domínio}). O valor já chega
 * normalizado (CPF só com dígitos). Grava-se com {@link #index}; busca-se com {@link #candidates}, que cobre
 * também as chaves anteriores enquanto uma rotação não termina.
 */
@RequiredArgsConstructor
public class BlindIndexer {

    private static final String HMAC = "HmacSHA256";
    private static final int KEY_BYTES = 32;

    private final DataKeys keys;

    public byte[] index(String domain, String value) {
        return value == null ? null : index(keys.active(DataKeyPurpose.BLIND_INDEX), domain, value);
    }

    public List<byte[]> candidates(String domain, String value) {
        return keys.usable(DataKeyPurpose.BLIND_INDEX).stream().map(key -> index(key, domain, value)).toList();
    }

    private static byte[] index(DataKey key, String domain, String value) {
        byte[] domainKey = hkdf(key.material(), new byte[0],
                ("blind-index:" + domain).getBytes(StandardCharsets.UTF_8), KEY_BYTES);
        return hmac(domainKey, value.getBytes(StandardCharsets.UTF_8));
    }

    /** HKDF-SHA256 (RFC 5869): extrai com o sal e expande até {@code length} bytes. */
    static byte[] hkdf(byte[] keyMaterial, byte[] salt, byte[] info, int length) {
        byte[] pseudoRandomKey = hmac(salt.length == 0 ? new byte[KEY_BYTES] : salt, keyMaterial);
        byte[] output = new byte[length];
        byte[] block = new byte[0];
        int written = 0;
        for (int counter = 1; written < length; counter++) {
            byte[] input = new byte[block.length + info.length + 1];
            System.arraycopy(block, 0, input, 0, block.length);
            System.arraycopy(info, 0, input, block.length, info.length);
            input[input.length - 1] = (byte) counter;
            block = hmac(pseudoRandomKey, input);
            int take = Math.min(block.length, length - written);
            System.arraycopy(block, 0, output, written, take);
            written += take;
        }
        return output;
    }

    static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(data);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Falha no índice cego", failure);
        }
    }
}
