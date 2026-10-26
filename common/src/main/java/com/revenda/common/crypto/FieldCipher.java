package com.revenda.common.crypto;

import com.revenda.common.crypto.DataKeys.DataKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;

/**
 * Cifra de campo: AES-256-GCM com a chave de dados ativa, nonce aleatório de 96 bits e, como dado
 * autenticado, {@code tabela|coluna|id da linha}. O que vai para a coluna {@code bytea} é
 * {@code 0x01 ‖ versão da chave (2 bytes) ‖ nonce (12 bytes) ‖ cifra + tag}. Usada só nos adapters de
 * persistência; nulo entra e sai nulo.
 */
@RequiredArgsConstructor
public class FieldCipher {

    static final byte FORMAT_VERSION = 1;
    static final int NONCE_BYTES = 12;
    static final int HEADER_BYTES = 1 + Short.BYTES + NONCE_BYTES;
    private static final int TAG_BITS = 128;

    private final DataKeys keys;
    private final SecureRandom random = new SecureRandom();

    public byte[] encrypt(FieldRef field, String plaintext) {
        if (plaintext == null) {
            return null;
        }
        DataKey key = keys.active(DataKeyPurpose.FIELD);
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] sealed;
        try {
            sealed = seal(key.material(), nonce, aad(field), plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Falha na cifra de campo " + field.table() + "." + field.column(),
                    failure);
        }
        return ByteBuffer.allocate(HEADER_BYTES + sealed.length)
                .put(FORMAT_VERSION)
                .putShort((short) key.version())
                .put(nonce)
                .put(sealed)
                .array();
    }

    public String decrypt(FieldRef field, byte[] ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
            if (buffer.remaining() <= HEADER_BYTES || buffer.get() != FORMAT_VERSION) {
                throw new IllegalStateException("Formato de cifra desconhecido");
            }
            DataKey key = keys.ofVersion(DataKeyPurpose.FIELD, buffer.getShort());
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] sealed = new byte[buffer.remaining()];
            buffer.get(sealed);
            return new String(open(key.material(), nonce, aad(field), sealed), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException failure) {
            throw new IllegalStateException(
                    "Campo cifrado não pôde ser lido: " + field.table() + "." + field.column(), failure);
        }
    }

    static byte[] seal(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) throws GeneralSecurityException {
        return run(Cipher.ENCRYPT_MODE, key, nonce, aad, plaintext);
    }

    static byte[] open(byte[] key, byte[] nonce, byte[] aad, byte[] sealed) throws GeneralSecurityException {
        return run(Cipher.DECRYPT_MODE, key, nonce, aad, sealed);
    }

    private static byte[] run(int mode, byte[] key, byte[] nonce, byte[] aad, byte[] input)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private static byte[] aad(FieldRef field) {
        return (field.table() + "|" + field.column() + "|" + field.rowId()).getBytes(StandardCharsets.UTF_8);
    }
}
