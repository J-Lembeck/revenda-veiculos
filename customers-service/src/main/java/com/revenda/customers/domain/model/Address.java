package com.revenda.customers.domain.model;

/** O endereço do comprador, para a cobrança e o documento; o complemento é opcional. */
public record Address(
        String zipCode,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state) {

    public static final String ZIP_CODE_FORMAT = "[0-9]{8}";
    public static final String STATE_FORMAT =
            "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO";
    public static final int STREET_LIMIT = 120;
    public static final int NUMBER_LIMIT = 10;
    public static final int COMPLEMENT_LIMIT = 60;
    public static final int DISTRICT_LIMIT = 60;
    public static final int CITY_LIMIT = 60;

    @Override
    public String toString() {
        return "Address[dados pessoais omitidos]";
    }
}
