package com.revenda.common.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTests {

    @Test
    void masksEmailCpfAndCep() {
        assertThat(PiiMasker.mask("Chave (email)=(ana.cliente+compra@example.test) já existe"))
                .isEqualTo("Chave (email)=([e-mail]) já existe");
        assertThat(PiiMasker.mask("cpf 123.456.789-09 do comprador")).isEqualTo("cpf [cpf] do comprador");
        assertThat(PiiMasker.mask("cpf 123456789-09 na retirada")).isEqualTo("cpf [cpf] na retirada");
        assertThat(PiiMasker.mask("entrega no 01310-100, bairro Bela Vista"))
                .isEqualTo("entrega no [cep], bairro Bela Vista");
    }

    @Test
    void masksCpfWithoutPunctuationAndOtherLongNumbersAsNumbers() {
        assertThat(PiiMasker.mask("cpf 12345678909")).isEqualTo("cpf [número]");
        assertThat(PiiMasker.mask("phone=11988887777")).isEqualTo("phone=[número]");
    }

    @Test
    void masksPhonesKeepingOnlyTheLastFourDigits() {
        assertThat(PiiMasker.mask("comprador +5511988887777 ligou")).isEqualTo("comprador [telefone •••• 7777] ligou");
        assertThat(PiiMasker.mask("fone (11) 98888-7777")).isEqualTo("fone [telefone •••• 7777]");
        assertThat(PiiMasker.mask("fone 11 98888-7777.")).isEqualTo("fone [telefone •••• 7777].");
        assertThat(PiiMasker.mask("fone 11 3333-4444")).isEqualTo("fone [telefone •••• 4444]");
    }

    @Test
    void masksTokensAndAuthorizationHeaders() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJjNzU0M2YxNiJ9.c2lnbmF0dXJhLWRlLXRlc3Rl";

        assertThat(PiiMasker.mask("token " + jwt + " recusado")).isEqualTo("token [token] recusado");
        assertThat(PiiMasker.mask("Authorization: Bearer " + jwt)).isEqualTo("Authorization: Bearer [token]");
        assertThat(PiiMasker.mask("authorization=bearer opaque-token-de-teste"))
                .isEqualTo("authorization=bearer [token]");
        assertThat(PiiMasker.mask("Authorization: Basic c2FsZXMtc2VydmljZTpkZXYtb25seQ==")).isEqualTo("Authorization: Basic [token]");
    }

    @Test
    void leavesIdentifiersMoneyAndInstantsAlone() {
        String line = "Compra 6f1d2c3b-0000-4000-8000-446655440000 (RESERVED) do veículo ABC1D23, requestId "
                + "gw-2f6c1a9e-0001, priceCents 8990000 em 2026-10-22T14:32:00.904-03:00, 1284 ms, porta 8083, "
                + "versão v2026.10.3";

        assertThat(PiiMasker.mask(line)).isEqualTo(line);
        assertThat(PiiMasker.mask("Reserva: 3 tentativa(s) em 2 s")).isEqualTo("Reserva: 3 tentativa(s) em 2 s");
        assertThat(PiiMasker.mask("")).isEmpty();
        assertThat(PiiMasker.mask(null)).isNull();
    }
}
