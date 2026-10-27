package com.revenda.errorprobe;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.error.FieldViolation;
import com.revenda.common.error.web.ApiError;
import com.revenda.common.observability.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(classes = ErrorProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class WebErrorHandlingTests {

    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void businessErrorAnswersItsKindCodeAndMessage() {
        ResponseEntity<ApiError> response = post("/probe/vehicles/7/reservations", null, MediaType.APPLICATION_JSON);

        assertError(response, HttpStatus.CONFLICT, "VEHICLE_UNAVAILABLE");
        assertThat(response.getBody().message())
                .isEqualTo("Este veículo acabou de ser reservado por outra pessoa. Escolha outro ou volte mais tarde.");
        assertThat(response.getBody().fields()).isEmpty();
    }

    @Test
    void notFoundUsesTheCommonMessage() {
        ResponseEntity<ApiError> response = rest.getForEntity("/probe/vehicles/7", ApiError.class);

        assertError(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Não encontramos o que você procurou.");
    }

    @Test
    void domainFieldErrorIsAValidationFailureWithTheField() {
        ResponseEntity<ApiError> response = post("/probe/plates", null, MediaType.APPLICATION_JSON);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().fields())
                .containsExactly(new FieldViolation("plate", "PLATE_INVALID", "Placa inválida."));
    }

    @Test
    void beanValidationListsEveryFieldInPortugueseWhateverTheClientLanguage() {
        HttpHeaders headers = jsonHeaders();
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
        String body = """
                {"brand": " ", "priceCents": 0, "year": 1800}""";

        ResponseEntity<ApiError> response =
                rest.exchange("/probe/vehicles", HttpMethod.POST, new HttpEntity<>(body, headers), ApiError.class);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().fields()).containsExactlyInAnyOrder(
                new FieldViolation("brand", "REQUIRED", "não deve estar em branco"),
                new FieldViolation("priceCents", "OUT_OF_RANGE", "deve ser maior que 0"),
                new FieldViolation("year", "OUT_OF_RANGE", "deve ser maior que ou igual à 1900"));
    }

    @Test
    void wrongTypeInTheBodyPointsToTheField() {
        String body = """
                {"brand": "Fiat", "priceCents": "muito", "year": 2020}""";

        ResponseEntity<ApiError> response = post("/probe/vehicles", body, MediaType.APPLICATION_JSON);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().fields())
                .containsExactly(new FieldViolation("priceCents", "TYPE_INVALID", "Valor em formato inválido."));
    }

    @Test
    void aDateThatDoesNotExistPointsToTheField() {
        String body = """
                {"brand": "Fiat", "priceCents": 1, "year": 2020, "soldOn": "2020-13-40"}""";

        ResponseEntity<ApiError> response = post("/probe/vehicles", body, MediaType.APPLICATION_JSON);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().fields())
                .containsExactly(new FieldViolation("soldOn", "TYPE_INVALID", "Valor em formato inválido."));
    }

    @Test
    void malformedBodyIsAValidationFailureWithoutFields() {
        ResponseEntity<ApiError> response = post("/probe/vehicles", "{", MediaType.APPLICATION_JSON);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().message()).isEqualTo("O corpo da requisição não é um JSON válido.");
        assertThat(response.getBody().fields()).isEmpty();
    }

    @Test
    void wrongTypeInTheQueryPointsToTheParameter() {
        ResponseEntity<ApiError> response = rest.getForEntity("/probe/vehicles?size=muitos", ApiError.class);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().fields())
                .containsExactly(new FieldViolation("size", "TYPE_INVALID", "Valor em formato inválido."));
    }

    @Test
    void constraintOnAQueryParameterPointsToTheParameter() {
        ResponseEntity<ApiError> response = rest.getForEntity("/probe/vehicles?size=500", ApiError.class);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().fields())
                .containsExactly(new FieldViolation("size", "OUT_OF_RANGE", "deve ser menor que ou igual à 100"));
    }

    @Test
    void unknownRouteIsNotFound() {
        ResponseEntity<ApiError> response = rest.getForEntity("/probe/nao-existe", ApiError.class);

        assertError(response, HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void wrongMethodIsNotSupportedAndSaysWhichOnesAre() {
        ResponseEntity<ApiError> response =
                rest.exchange("/probe/vehicles/7", HttpMethod.DELETE, null, ApiError.class);

        assertError(response, HttpStatus.METHOD_NOT_ALLOWED, "REQUEST_NOT_SUPPORTED");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
    }

    @Test
    void wrongContentTypeIsNotSupported() {
        ResponseEntity<ApiError> response = post("/probe/vehicles", "Fiat", MediaType.TEXT_PLAIN);

        assertError(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "REQUEST_NOT_SUPPORTED");
    }

    @Test
    void unexpectedErrorHidesItsDetails() {
        ResponseEntity<String> response = rest.getForEntity("/probe/failure", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("\"code\":\"INTERNAL_ERROR\"")
                .doesNotContain(ErrorProbeApplication.LEAKED_DETAIL, "IllegalStateException", "trace");
    }

    @Test
    void errorOutsideTheControllersKeepsTheFormat() {
        ResponseEntity<String> raw = rest.getForEntity("/probe/filter-failure", String.class);
        ResponseEntity<ApiError> response = rest.getForEntity("/probe/filter-failure", ApiError.class);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(raw.getBody()).doesNotContain(ErrorProbeApplication.LEAKED_DETAIL, "IllegalStateException",
                "timestamp", "trace");
    }

    @Test
    void safeRequestIdFromTheClientIsKept() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestIdFilter.HEADER, "gw-2f6c1a9e-0001");

        ResponseEntity<ApiError> response =
                rest.exchange("/probe/vehicles/7", HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getHeaders().getFirst(RequestIdFilter.HEADER)).isEqualTo("gw-2f6c1a9e-0001");
        assertThat(response.getBody().requestId()).isEqualTo("gw-2f6c1a9e-0001");
    }

    @Test
    void unsafeRequestIdFromTheClientIsReplaced() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestIdFilter.HEADER, "<script>alert(1)</script>");

        ResponseEntity<ApiError> response =
                rest.exchange("/probe/vehicles/7", HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getBody().requestId()).matches(UUID);
        assertThat(response.getHeaders().getFirst(RequestIdFilter.HEADER)).isEqualTo(response.getBody().requestId());
    }

    private ResponseEntity<ApiError> post(String path, String body, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), ApiError.class);
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static void assertError(ResponseEntity<ApiError> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().requestId())
                .isNotBlank()
                .isEqualTo(response.getHeaders().getFirst(RequestIdFilter.HEADER));
    }
}
