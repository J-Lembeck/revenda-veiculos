package com.revenda.common.test;

import com.revenda.common.idempotency.IdempotencyFilter;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * Chama a API do serviço pelo HTTP, como um cliente de verdade: em JSON, com o token de quem chama e uma
 * {@code Idempotency-Key} nova em todo {@code POST}. Cada {@code as…}/{@code with…} devolve outro condutor; o
 * original não muda.
 */
public final class ApiDriver {

    private final TestRestTemplate rest;
    private final String token;
    private final HttpHeaders headers;
    private final Supplier<String> idempotencyKey;

    public ApiDriver(TestRestTemplate rest) {
        this(rest, null, new HttpHeaders(), () -> UUID.randomUUID().toString());
    }

    private ApiDriver(TestRestTemplate rest, String token, HttpHeaders headers, Supplier<String> idempotencyKey) {
        this.rest = rest;
        this.token = token;
        this.headers = headers;
        this.idempotencyKey = idempotencyKey;
    }

    public ApiDriver as(String token) {
        return new ApiDriver(rest, token, headers, idempotencyKey);
    }

    public ApiDriver anonymous() {
        return as(null);
    }

    public ApiDriver asCustomer(String subject) {
        return as(TestTokens.customer(subject));
    }

    public ApiDriver asSeller(String subject) {
        return as(TestTokens.seller(subject));
    }

    public ApiDriver asAdmin(String subject) {
        return as(TestTokens.admin(subject));
    }

    public ApiDriver asService(String clientId, String... scopes) {
        return as(TestTokens.service(clientId, scopes));
    }

    public ApiDriver withHeader(String name, String value) {
        HttpHeaders more = new HttpHeaders();
        more.addAll(headers);
        more.set(name, value);
        return new ApiDriver(rest, token, more, idempotencyKey);
    }

    /** A mesma chave em todo {@code POST} deste condutor, para repetir um pedido. */
    public ApiDriver withIdempotencyKey(String key) {
        return new ApiDriver(rest, token, headers, () -> key);
    }

    public ApiDriver withoutIdempotencyKey() {
        return new ApiDriver(rest, token, headers, () -> null);
    }

    public ResponseEntity<JsonNode> get(String path) {
        return exchange(HttpMethod.GET, path, null, JsonNode.class);
    }

    public ResponseEntity<JsonNode> post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, JsonNode.class);
    }

    public ResponseEntity<JsonNode> put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body, JsonNode.class);
    }

    public ResponseEntity<JsonNode> patch(String path, Object body) {
        return exchange(HttpMethod.PATCH, path, body, JsonNode.class);
    }

    public ResponseEntity<JsonNode> delete(String path) {
        return exchange(HttpMethod.DELETE, path, null, JsonNode.class);
    }

    public <T> ResponseEntity<T> exchange(HttpMethod method, String path, Object body, Class<T> responseType) {
        HttpHeaders request = new HttpHeaders();
        request.addAll(headers);
        if (token != null) {
            request.setBearerAuth(token);
        }
        if (body != null) {
            request.setContentType(MediaType.APPLICATION_JSON);
        }
        String key = HttpMethod.POST.equals(method) ? idempotencyKey.get() : null;
        if (key != null && !request.containsHeader(IdempotencyFilter.HEADER)) {
            request.set(IdempotencyFilter.HEADER, key);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, request), responseType);
    }
}
