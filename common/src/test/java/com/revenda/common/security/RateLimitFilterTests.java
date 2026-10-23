package com.revenda.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTests {

    private static final int LIMIT = 10;
    private static final String ADDRESS = "203.0.113.10";
    private static final String OTHER_ADDRESS = "203.0.113.11";

    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-22T15:00:00Z"));
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RateLimitFilter filter = filterWith(true);

    @Test
    void callBeyondTheLimitOfTheSameAddressIsRefused() throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            assertThat(call(post("/purchases", ADDRESS)).getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse refused = call(post("/purchases", ADDRESS));

        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(Integer.parseInt(refused.getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1, 60);
        assertThat(objectMapper.readValue(refused.getContentAsString(), Map.class))
                .containsEntry("code", "RATE_LIMITED")
                .containsEntry("message", "Muitas tentativas seguidas. Aguarde um pouco e tente de novo.");
        assertThat(refused.getContentAsString()).doesNotContain(ADDRESS);
        assertThat(call(post("/purchases", OTHER_ADDRESS)).getStatus()).isEqualTo(200);
    }

    @Test
    void eachRuleCountsApartAndOtherRoutesAreNotCounted() throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            call(post("/purchases", ADDRESS));
        }

        assertThat(call(post("/customers", ADDRESS)).getStatus()).isEqualTo(200);
        assertThat(call(request("GET", "/purchases", ADDRESS)).getStatus()).isEqualTo(200);
        assertThat(call(post("/vehicles", ADDRESS)).getStatus()).isEqualTo(200);
    }

    @Test
    void limitComesBackWhenTheWindowEnds() throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            call(post("/purchases", ADDRESS));
        }
        assertThat(call(post("/purchases", ADDRESS)).getStatus()).isEqualTo(429);

        clock.advance(Duration.ofSeconds(59));
        MockHttpServletResponse stillRefused = call(post("/purchases", ADDRESS));
        assertThat(stillRefused.getStatus()).isEqualTo(429);
        assertThat(stillRefused.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");

        clock.advance(Duration.ofSeconds(1));

        for (int attempt = 0; attempt < LIMIT; attempt++) {
            assertThat(call(post("/purchases", ADDRESS)).getStatus()).isEqualTo(200);
        }
        assertThat(call(post("/purchases", ADDRESS)).getStatus()).isEqualTo(429);
    }

    @Test
    void onlyTheConnectionAddressCountsNeverAClientHeader() throws Exception {
        for (int attempt = 0; attempt <= LIMIT; attempt++) {
            MockHttpServletRequest request = post("/purchases", ADDRESS);
            request.addHeader("X-Forwarded-For", "198.51.100." + attempt);
            request.addHeader("X-Real-IP", "192.0.2." + attempt);
            assertThat(call(request).getStatus()).isEqualTo(attempt < LIMIT ? 200 : 429);
        }
    }

    @Test
    void addressesOfTheSameIpv6NetworkShareTheLimit() throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            call(post("/purchases", "2001:db8:0:1::" + Integer.toHexString(attempt + 1)));
        }

        assertThat(call(post("/purchases", "2001:db8:0:1:ffff::9")).getStatus()).isEqualTo(429);
        assertThat(call(post("/purchases", "2001:db8:0:2::1")).getStatus()).isEqualTo(200);
    }

    @Test
    void switchedOffNothingIsRefused() throws Exception {
        RateLimitFilter off = filterWith(false);
        for (int attempt = 0; attempt < LIMIT * 3; attempt++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            off.doFilter(post("/purchases", ADDRESS), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private RateLimitFilter filterWith(boolean enabled) {
        List<RateLimitRule> rules = List.of(
                new RateLimitRule(HttpMethod.POST, "/purchases", LIMIT, Duration.ofMinutes(1)),
                new RateLimitRule(HttpMethod.POST, "/customers", 5, Duration.ofMinutes(1)));
        return new RateLimitFilter(new RateLimiter(enabled, clock), rules, PathPatternRequestMatcher.withDefaults(),
                new ApiErrorWriter(objectMapper));
    }

    private MockHttpServletResponse call(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest post(String path, String address) {
        return request("POST", path, address);
    }

    private static MockHttpServletRequest request(String method, String path, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(address);
        return request;
    }

    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
