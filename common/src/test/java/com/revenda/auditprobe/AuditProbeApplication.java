package com.revenda.auditprobe;

import com.revenda.common.audit.AuditAction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
class AuditProbeApplication {

    static final Instant NOW = Instant.parse("2026-10-26T14:32:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    enum ProbeAuditAction implements AuditAction {
        VEHICLE_CREATED,
        VEHICLE_RESERVED,
        PAYMENT_WEBHOOK_REJECTED
    }
}
