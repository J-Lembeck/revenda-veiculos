package com.revenda.idempotencyprobe;

import com.revenda.common.test.PostgresTestDatabase;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = IdempotencyProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:idempotency-probe-schema.sql"})
@AutoConfigureTestRestTemplate
abstract class IdempotencyProbeSupport {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }
}
