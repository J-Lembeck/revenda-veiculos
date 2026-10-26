package com.revenda.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Vetores públicos: AES-256-GCM (caso 14 de McGrew e Viega), HMAC-SHA256 (RFC 4231) e HKDF (RFC 5869). */
class CryptoVectorsTests {

    private static final HexFormat HEX = HexFormat.of();

    @Test
    void aes256GcmMatchesThePublishedVector() throws GeneralSecurityException {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] plaintext = new byte[16];

        byte[] sealed = FieldCipher.seal(key, nonce, new byte[0], plaintext);

        assertThat(HEX.formatHex(sealed))
                .isEqualTo("cea7403d4d606b6e074ec5d3baf39d18" + "d0d1c8a799996bf0265b98b5d48ab919");
        assertThat(FieldCipher.open(key, nonce, new byte[0], sealed)).isEqualTo(plaintext);
    }

    @Test
    void anyChangeInTheAuthenticatedDataOrTheCipherIsRefused() throws GeneralSecurityException {
        byte[] key = new byte[32];
        byte[] nonce = new byte[12];
        byte[] sealed = FieldCipher.seal(key, nonce, "a|b".getBytes(StandardCharsets.UTF_8), new byte[16]);
        byte[] flipped = sealed.clone();
        flipped[0] ^= 1;

        assertThatThrownBy(() -> FieldCipher.open(key, nonce, "a|c".getBytes(StandardCharsets.UTF_8), sealed))
                .isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> FieldCipher.open(key, nonce, "a|b".getBytes(StandardCharsets.UTF_8), flipped))
                .isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void hmacSha256MatchesRfc4231() {
        byte[] mac = BlindIndexer.hmac("Jefe".getBytes(StandardCharsets.US_ASCII),
                "what do ya want for nothing?".getBytes(StandardCharsets.US_ASCII));

        assertThat(HEX.formatHex(mac))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void hkdfSha256MatchesRfc5869() {
        byte[] withSalt = BlindIndexer.hkdf(
                HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
                HEX.parseHex("000102030405060708090a0b0c"),
                HEX.parseHex("f0f1f2f3f4f5f6f7f8f9"),
                42);
        byte[] withoutSaltAndInfo = BlindIndexer.hkdf(
                HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"), new byte[0], new byte[0], 42);

        assertThat(HEX.formatHex(withSalt)).isEqualTo(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
        assertThat(HEX.formatHex(withoutSaltAndInfo)).isEqualTo(
                "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8");
    }
}
