package com.revenda.common.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A assinatura do webhook de pagamento, no cabeçalho {@value #HEADER}: {@code t=<segundos desde a época>,v1=<hex>},
 * com {@code v1 = HMAC-SHA256(segredo, t + "." + corpo bruto)}. Quem envia assina cada tentativa com o carimbo do
 * momento; quem recebe confere a assinatura em tempo constante e recusa o carimbo fora da janela (anti-replay).
 */
public final class WebhookSignature {

    public static final String HEADER = "X-Signature";

    private static final String HMAC = "HmacSHA256";
    private static final String TIMESTAMP = "t=";
    private static final String SIGNATURE = "v1=";

    private final byte[] secret;

    public WebhookSignature(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Segredo do webhook vazio");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** O valor do cabeçalho para o corpo, assinado no instante {@code at}. */
    public String sign(byte[] body, Instant at) {
        long timestamp = at.getEpochSecond();
        return TIMESTAMP + timestamp + "," + SIGNATURE + HexFormat.of().formatHex(hmac(timestamp, body));
    }

    /**
     * Se o cabeçalho assina o corpo com este segredo e o carimbo está a no máximo {@code tolerance} de {@code now},
     * para trás ou para a frente. Cabeçalho ausente ou fora do formato não confere. Aceita mais de um {@code v1}
     * (troca do segredo): basta um bater.
     */
    public boolean verify(String header, byte[] body, Instant now, Duration tolerance) {
        Objects.requireNonNull(body, "body");
        if (header == null) {
            return false;
        }
        Long timestamp = null;
        boolean matches = false;
        for (String part : header.split(",")) {
            String item = part.strip();
            if (item.startsWith(TIMESTAMP) && timestamp == null) {
                timestamp = epochSeconds(item.substring(TIMESTAMP.length()));
            }
        }
        if (timestamp == null) {
            return false;
        }
        Duration age = Duration.between(Instant.ofEpochSecond(timestamp), now).abs();
        if (age.compareTo(tolerance) > 0) {
            return false;
        }
        byte[] expected = hmac(timestamp, body);
        for (String part : header.split(",")) {
            String item = part.strip();
            if (item.startsWith(SIGNATURE)) {
                byte[] presented = hex(item.substring(SIGNATURE.length()));
                matches |= presented != null && MessageDigest.isEqual(expected, presented);
            }
        }
        return matches;
    }

    private byte[] hmac(long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(body);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Falha na assinatura do webhook", failure);
        }
    }

    private static Long epochSeconds(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static byte[] hex(String text) {
        try {
            return HexFormat.of().parseHex(text);
        } catch (IllegalArgumentException notHex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "WebhookSignature[segredo omitido]";
    }
}
