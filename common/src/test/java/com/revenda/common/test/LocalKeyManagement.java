package com.revenda.common.test;

import com.revenda.common.crypto.KeyManagementPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Substituto do KMS só para teste: a chave-mestra é sorteada na criação e vive só em memória. O contexto
 * entra como dado autenticado, como no KMS.
 */
public final class LocalKeyManagement implements KeyManagementPort {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec masterKey;

    public LocalKeyManagement() {
        byte[] key = new byte[KEY_BYTES];
        random.nextBytes(key);
        this.masterKey = new SecretKeySpec(key, "AES");
    }

    @Override
    public GeneratedKey generateDataKey(Map<String, String> context) {
        byte[] plaintext = new byte[KEY_BYTES];
        random.nextBytes(plaintext);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] sealed = run(Cipher.ENCRYPT_MODE, nonce, plaintext, context);
        return new GeneratedKey(plaintext, ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed)
                .array());
    }

    @Override
    public byte[] decrypt(byte[] encryptedKey, Map<String, String> context) {
        ByteBuffer buffer = ByteBuffer.wrap(encryptedKey);
        byte[] nonce = new byte[NONCE_BYTES];
        buffer.get(nonce);
        byte[] sealed = new byte[buffer.remaining()];
        buffer.get(sealed);
        return run(Cipher.DECRYPT_MODE, nonce, sealed, context);
    }

    private byte[] run(int mode, byte[] nonce, byte[] input, Map<String, String> context) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(canonical(context));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Chave de dados não pôde ser aberta com este contexto", failure);
        }
    }

    private static byte[] canonical(Map<String, String> context) {
        StringBuilder aad = new StringBuilder();
        new TreeMap<>(context).forEach((name, value) -> aad.append(name).append('=').append(value).append('\n'));
        return aad.toString().getBytes(StandardCharsets.UTF_8);
    }
}
