package com.revenda.common.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RateLimitRuleTests {

    @Test
    void refusesARuleWithoutMethodOrWithEmptyQuota() {
        assertThatThrownBy(() -> new RateLimitRule(null, "/purchases", 10, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule(HttpMethod.POST, "/purchases", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitRule(HttpMethod.POST, "/purchases", 10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
