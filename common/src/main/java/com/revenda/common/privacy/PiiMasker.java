package com.revenda.common.privacy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Última barreira antes de um texto sair para o log: troca o que tem cara de token, e-mail, CPF, CEP ou
 * telefone por um marcador. Não substitui a regra de não logar dado pessoal — pega o que escapou, como a
 * mensagem de um erro do banco. Identificadores (UUID, {@code requestId}) e valores em centavos ficam
 * intactos: um número só é mascarado quando está solto e tem de 10 a 13 dígitos.
 */
public final class PiiMasker {

    private static final Pattern AUTHORIZATION = Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[\\w.~+/-]+=*");
    private static final Pattern JWT = Pattern.compile("\\beyJ[\\w-]*\\.[\\w-]+\\.[\\w-]*");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern CPF = Pattern.compile("(?<![\\w.-])\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}(?![\\w-])");
    private static final Pattern CEP = Pattern.compile("(?<![\\w-])\\d{5}-\\d{3}(?![\\w-])");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("\\+\\d{6,11}(\\d{4})(?!\\d)");
    private static final Pattern FORMATTED_PHONE =
            Pattern.compile("(?<![\\w-])\\(?\\d{2}\\)?[ ]?9?\\d{4}[- ](\\d{4})(?![\\w-])");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![\\w.,-])\\d{10,13}(?![\\w-])");

    private PiiMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = AUTHORIZATION.matcher(text).replaceAll("$1 [token]");
        masked = JWT.matcher(masked).replaceAll("[token]");
        masked = EMAIL.matcher(masked).replaceAll("[e-mail]");
        masked = CPF.matcher(masked).replaceAll(match -> punctuated(match.group()) ? "[cpf]" : match.group());
        masked = CEP.matcher(masked).replaceAll("[cep]");
        masked = INTERNATIONAL_PHONE.matcher(masked).replaceAll(match -> phone(match.group(1)));
        masked = FORMATTED_PHONE.matcher(masked).replaceAll(match -> phone(match.group(1)));
        return LONG_NUMBER.matcher(masked).replaceAll("[número]");
    }

    private static boolean punctuated(String cpf) {
        return cpf.indexOf('.') >= 0 || cpf.indexOf('-') >= 0;
    }

    private static String phone(String lastFour) {
        return Matcher.quoteReplacement("[telefone •••• " + lastFour + "]");
    }
}
