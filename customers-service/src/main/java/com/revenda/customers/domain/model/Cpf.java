package com.revenda.customers.domain.model;

import com.revenda.common.error.FieldViolation;
import java.util.Optional;
import java.util.regex.Pattern;

/** O CPF só com os 11 dígitos, já conferidos (RN-06). Fora do cadastro ele aparece mascarado. */
public record Cpf(String digits) {

    public static final String INVALID = "CPF_INVALID";
    public static final String INVALID_MESSAGE =
            "O CPF tem 11 dígitos e os dois últimos conferem os anteriores. Confira o número.";

    private static final Pattern DIGITS = Pattern.compile("[0-9]{11}");
    private static final Pattern PUNCTUATED = Pattern.compile("[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}");

    public Cpf {
        if (!isValid(digits)) {
            throw new IllegalArgumentException("CPF sem 11 dígitos ou com dígito verificador errado");
        }
    }

    /** O CPF como a pessoa digitou, só dígitos ou com pontos e traço; vazio se não confere. */
    public static Optional<Cpf> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String digits = PUNCTUATED.matcher(text).matches() ? text.replace(".", "").replace("-", "") : text;
        return isValid(digits) ? Optional.of(new Cpf(digits)) : Optional.empty();
    }

    /** O CPF que não confere, no campo {@code field} do pedido. */
    public static FieldViolation invalidAt(String field) {
        return new FieldViolation(field, INVALID, INVALID_MESSAGE);
    }

    /** {@code ***.982.247-**}: só os seis dígitos do meio. */
    public String masked() {
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    @Override
    public String toString() {
        return masked();
    }

    private static boolean isValid(String digits) {
        if (digits == null || !DIGITS.matcher(digits).matches() || digits.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(digits, 9) == digits.charAt(9) - '0' && checkDigit(digits, 10) == digits.charAt(10) - '0';
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int rest = sum * 10 % 11;
        return rest == 10 ? 0 : rest;
    }
}
