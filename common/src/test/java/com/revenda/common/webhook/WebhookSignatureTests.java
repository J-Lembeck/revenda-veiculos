package com.revenda.common.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookSignatureTests {

    private static final String SECRET = "segredo-de-teste-do-webhook";
    private static final Instant SIGNED_AT = Instant.ofEpochSecond(1_792_680_000L);
    private static final byte[] BODY = "{\"eventId\":\"evt_1\",\"type\":\"CHARGE_PAID\"}".getBytes(StandardCharsets.UTF_8);
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final WebhookSignature signature = new WebhookSignature(SECRET);

    @Test
    void theHeaderIsTheTimestampAndTheHmacOfTimestampDotBody() {
        assertThat(signature.sign(BODY, SIGNED_AT))
                .isEqualTo("t=1792680000,v1=23f179143c1719dd3ffd2d312f0150557b031e2164cccb07ab7c662808d5df29");
    }

    @Test
    void theSignedBodyIsAcceptedInsideTheWindowBothWays() {
        String header = signature.sign(BODY, SIGNED_AT);

        assertThat(signature.verify(header, BODY, SIGNED_AT, WINDOW)).isTrue();
        assertThat(signature.verify(header, BODY, SIGNED_AT.plus(WINDOW), WINDOW)).isTrue();
        assertThat(signature.verify(header, BODY, SIGNED_AT.minus(WINDOW), WINDOW)).isTrue();
    }

    @Test
    void anOldOrFutureTimestampIsRefusedEvenWithTheRightHmac() {
        String header = signature.sign(BODY, SIGNED_AT);

        assertThat(signature.verify(header, BODY, SIGNED_AT.plus(WINDOW).plusSeconds(1), WINDOW)).isFalse();
        assertThat(signature.verify(header, BODY, SIGNED_AT.minus(WINDOW).minusSeconds(1), WINDOW)).isFalse();
    }

    @Test
    void anotherBodyAnotherSecretOrAnotherTimestampDoNotMatch() {
        String header = signature.sign(BODY, SIGNED_AT);
        byte[] tampered = "{\"eventId\":\"evt_1\",\"type\":\"CHARGE_REFUNDED\"}".getBytes(StandardCharsets.UTF_8);
        String movedTimestamp = header.replace("t=1792680000", "t=1792680001");

        assertThat(signature.verify(header, tampered, SIGNED_AT, WINDOW)).isFalse();
        assertThat(new WebhookSignature("outro-segredo").verify(header, BODY, SIGNED_AT, WINDOW)).isFalse();
        assertThat(signature.verify(movedTimestamp, BODY, SIGNED_AT, WINDOW)).isFalse();
    }

    @Test
    void oneMatchingSignatureAmongSeveralIsEnough() {
        String header = signature.sign(BODY, SIGNED_AT);
        String rotated = header.replace(",v1=", ",v1=" + "00".repeat(32) + ",v1=");

        assertThat(signature.verify(rotated, BODY, SIGNED_AT, WINDOW)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "v1=abc", "t=abc,v1=00", "t=1792680000", "t=1792680000,v1=nao-e-hex",
            "t=1792680000,v1=00"})
    void aMissingOrMalformedHeaderDoesNotMatch(String header) {
        assertThat(signature.verify(header, BODY, SIGNED_AT, WINDOW)).isFalse();
    }

    @Test
    void theSecretIsRequiredAndNeverShown() {
        assertThatThrownBy(() -> new WebhookSignature(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(signature.toString()).doesNotContain(SECRET);
    }
}
