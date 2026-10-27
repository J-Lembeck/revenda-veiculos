package com.revenda.common.test;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dados inventados para teste: CPF sorteado com dígito verificador válido, e-mail em {@code example.test} e
 * placa, chassi e RENAVAM no formato do estoque.
 */
public final class SyntheticData {

    private static final int CPF_BASE_LENGTH = 9;
    private static final int RENAVAM_BASE_LENGTH = 10;
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String VIN_CHARACTERS = "ABCDEFGHJKLMNPRSTUVWXYZ" + DIGITS;
    private static final int VIN_LENGTH = 17;

    private SyntheticData() {
    }

    /** Onze dígitos, sem pontuação, nunca com todos os dígitos iguais. */
    public static String cpf() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder base = new StringBuilder(CPF_BASE_LENGTH);
        while (base.isEmpty() || base.chars().distinct().count() == 1) {
            base.setLength(0);
            for (int i = 0; i < CPF_BASE_LENGTH; i++) {
                base.append((char) ('0' + random.nextInt(10)));
            }
        }
        return cpfFromBase(base.toString());
    }

    /** Completa nove dígitos com os dois verificadores. */
    public static String cpfFromBase(String base) {
        if (!base.matches("\\d{" + CPF_BASE_LENGTH + "}")) {
            throw new IllegalArgumentException("A base do CPF tem nove dígitos");
        }
        String withFirst = base + checkDigit(base);
        return withFirst + checkDigit(withFirst);
    }

    /** {@code 000.000.000-00}. */
    public static String formatCpf(String cpf) {
        return cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
    }

    public static String email() {
        return "pessoa." + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.test";
    }

    /** Padrão Mercosul ({@code ABC1D23}) ou antigo ({@code ABC1234}), sorteado. */
    public static String plate() {
        return random(LETTERS, 3) + random(DIGITS, 1) + random(LETTERS + DIGITS, 1) + random(DIGITS, 2);
    }

    /** Dezessete letras e números, sem {@code I}, {@code O} nem {@code Q}. */
    public static String vin() {
        return random(VIN_CHARACTERS, VIN_LENGTH);
    }

    /** Onze dígitos, o último verificador dos dez primeiros. */
    public static String renavam() {
        return renavamFromBase(random(DIGITS, RENAVAM_BASE_LENGTH));
    }

    /** Completa dez dígitos com o verificador do RENAVAM. */
    public static String renavamFromBase(String base) {
        if (!base.matches("\\d{" + RENAVAM_BASE_LENGTH + "}")) {
            throw new IllegalArgumentException("A base do RENAVAM tem dez dígitos");
        }
        String reversed = new StringBuilder(base).reverse().toString();
        int sum = 0;
        for (int i = 0; i < RENAVAM_BASE_LENGTH; i++) {
            sum += (reversed.charAt(i) - '0') * (i % 8 + 2);
        }
        int digit = 11 - sum % 11;
        return base + (digit >= 10 ? 0 : digit);
    }

    private static String random(String alphabet, int length) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }

    private static int checkDigit(String digits) {
        int sum = 0;
        int weight = digits.length() + 1;
        for (char digit : digits.toCharArray()) {
            sum += (digit - '0') * weight--;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
