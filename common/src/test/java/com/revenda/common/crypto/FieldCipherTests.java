package com.revenda.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FieldCipherTests {

    private static final String CPF = "52998224725";

    private final InMemoryDataKeys keys = new InMemoryDataKeys();
    private final FieldCipher cipher = new FieldCipher(keys);

    @Test
    void fieldComesBackOnlyInTheSamePlace() {
        FieldRef cpf = new FieldRef("customer", "cpf_enc", UUID.randomUUID());
        byte[] sealed = cipher.encrypt(cpf, CPF);

        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain(CPF).doesNotContain("982247");
        assertThat(cipher.decrypt(cpf, sealed)).isEqualTo(CPF);
        assertThat(cipher.encrypt(cpf, CPF)).isNotEqualTo(sealed);

        assertRefused(new FieldRef("customer", "cpf_enc", UUID.randomUUID()), sealed);
        assertRefused(new FieldRef("customer", "phone_enc", cpf.rowId()), sealed);
        assertRefused(new FieldRef("purchase", "cpf_enc", cpf.rowId()), sealed);
    }

    @Test
    void storedValueCarriesTheFormatTheKeyVersionAndTheNonce() {
        FieldRef phone = new FieldRef("customer", "phone_enc", UUID.randomUUID());
        String plaintext = "+5511988887777";

        ByteBuffer stored = ByteBuffer.wrap(cipher.encrypt(phone, plaintext));

        assertThat(stored.get()).isEqualTo(FieldCipher.FORMAT_VERSION);
        assertThat(stored.getShort()).isEqualTo((short) keys.active(DataKeyPurpose.FIELD).version());
        assertThat(stored.capacity()).isEqualTo(FieldCipher.HEADER_BYTES
                + plaintext.getBytes(StandardCharsets.UTF_8).length + 16);
    }

    @Test
    void tamperedOrUnknownBytesAreRefusedWithoutTheValue() {
        FieldRef cpf = new FieldRef("customer", "cpf_enc", UUID.randomUUID());
        byte[] sealed = cipher.encrypt(cpf, CPF);
        byte[] tampered = sealed.clone();
        tampered[tampered.length - 1] ^= 1;
        byte[] otherFormat = sealed.clone();
        otherFormat[0] = 2;

        assertRefused(cpf, tampered);
        assertRefused(cpf, otherFormat);
        assertRefused(cpf, new byte[FieldCipher.HEADER_BYTES]);
    }

    @Test
    void afterRotationNewValuesUseTheNewKeyAndOldOnesStillRead() {
        FieldRef cpf = new FieldRef("customer", "cpf_enc", UUID.randomUUID());
        byte[] before = cipher.encrypt(cpf, CPF);

        keys.rotate(DataKeyPurpose.FIELD);
        byte[] after = cipher.encrypt(cpf, CPF);

        assertThat(ByteBuffer.wrap(before, 1, 2).getShort()).isEqualTo((short) 1);
        assertThat(ByteBuffer.wrap(after, 1, 2).getShort()).isEqualTo((short) 2);
        assertThat(cipher.decrypt(cpf, before)).isEqualTo(CPF);
        assertThat(cipher.decrypt(cpf, after)).isEqualTo(CPF);
    }

    @Test
    void valueWrittenWithAKeyThatIsGoneIsRefused() {
        FieldRef cpf = new FieldRef("customer", "cpf_enc", UUID.randomUUID());
        byte[] before = cipher.encrypt(cpf, CPF);
        keys.rotate(DataKeyPurpose.FIELD);
        keys.forgetAllBut(DataKeyPurpose.FIELD, 2);

        assertRefused(cpf, before);
    }

    @Test
    void nothingToCipherStaysNull() {
        FieldRef address = new FieldRef("customer", "address_enc", UUID.randomUUID());

        assertThat(cipher.encrypt(address, null)).isNull();
        assertThat(cipher.decrypt(address, null)).isNull();
        assertThat(cipher.decrypt(address, cipher.encrypt(address, ""))).isEmpty();
    }

    @Test
    void fieldNeedsTableColumnAndRow() {
        assertThatThrownBy(() -> new FieldRef("customer", "cpf_enc", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldRef(" ", "cpf_enc", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FieldRef("customer", null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRefused(FieldRef field, byte[] sealed) {
        assertThatThrownBy(() -> cipher.decrypt(field, sealed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Campo cifrado não pôde ser lido: " + field.table() + "." + field.column())
                .hasMessageNotContaining(CPF);
    }
}
