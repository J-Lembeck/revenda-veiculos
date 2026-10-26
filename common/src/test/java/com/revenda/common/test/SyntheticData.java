package com.revenda.common.test;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Dados pessoais inventados para teste: CPF sorteado com dígito verificador válido e e-mail em {@code example.test}. */
public final class SyntheticData {

    private static final int CPF_BASE_LENGTH = 9;

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
