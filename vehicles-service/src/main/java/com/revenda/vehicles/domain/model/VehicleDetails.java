package com.revenda.vehicles.domain.model;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.FieldViolation;
import com.revenda.common.money.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** O que a equipe informa de um veículo, no cadastro e na edição (RN-01). */
public record VehicleDetails(
        String brand,
        String model,
        String trimName,
        int manufactureYear,
        int modelYear,
        String color,
        int mileageKm,
        String plate,
        String vin,
        String renavam,
        Money price) {

    public static final int BRAND_LIMIT = 60;
    public static final int MODEL_LIMIT = 60;
    public static final int TRIM_NAME_LIMIT = 80;
    public static final int COLOR_LIMIT = 30;
    public static final int FIRST_YEAR = 1900;
    public static final Money MAX_PRICE = Money.ofCents(1_000_000_000L);

    private static final Pattern PLATE = Pattern.compile("[A-Z]{3}[0-9][A-Z0-9][0-9]{2}");
    private static final Pattern VIN = Pattern.compile("[A-HJ-NPR-Z0-9]{17}");
    private static final Pattern RENAVAM = Pattern.compile("[0-9]{11}");
    private static final int[] RENAVAM_WEIGHTS = {3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    /** Recusa, de uma vez, todo campo fora da RN-01; a unicidade de placa, chassi e RENAVAM fica com o estoque. */
    public void requireValid(int currentYear) {
        List<FieldViolation> violations = new ArrayList<>();
        if (plate == null || !PLATE.matcher(plate).matches()) {
            violations.add(new FieldViolation("plate", "PLATE_INVALID",
                    "Use a placa no padrão antigo (ABC1234) ou Mercosul (ABC1D23), sem hífen e em maiúsculas."));
        }
        if (vin == null || !VIN.matcher(vin).matches()) {
            violations.add(new FieldViolation("vin", "VIN_INVALID",
                    "O chassi tem 17 letras maiúsculas e números, sem I, O nem Q."));
        }
        if (!validRenavam(renavam)) {
            violations.add(new FieldViolation("renavam", "RENAVAM_INVALID",
                    "O RENAVAM tem 11 dígitos e o último confere os anteriores. Confira no documento do veículo."));
        }
        if (manufactureYear < FIRST_YEAR || manufactureYear > currentYear + 1) {
            violations.add(new FieldViolation("manufactureYear", "YEAR_INVALID",
                    "O ano de fabricação vai de " + FIRST_YEAR + " a " + (currentYear + 1) + "."));
        } else if (modelYear != manufactureYear && modelYear != manufactureYear + 1) {
            violations.add(new FieldViolation("modelYear", "YEAR_INVALID",
                    "O ano do modelo é o de fabricação ou o seguinte."));
        }
        if (mileageKm < 0) {
            violations.add(new FieldViolation("mileageKm", "OUT_OF_RANGE", "A quilometragem não pode ser negativa."));
        }
        if (price == null || !price.isPositive() || price.isGreaterThan(MAX_PRICE)) {
            violations.add(new FieldViolation("priceCents", "PRICE_INVALID",
                    "O preço é maior que zero e vai até R$ 10.000.000,00."));
        }
        if (!violations.isEmpty()) {
            throw BusinessException.invalid(violations.toArray(FieldViolation[]::new));
        }
    }

    private static boolean validRenavam(String value) {
        if (value == null || !RENAVAM.matcher(value).matches()) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < RENAVAM_WEIGHTS.length; i++) {
            sum += (value.charAt(i) - '0') * RENAVAM_WEIGHTS[i];
        }
        return sum * 10 % 11 % 10 == value.charAt(10) - '0';
    }
}
