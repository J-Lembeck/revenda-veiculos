package com.revenda.sales;

import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.ResourceServerContract;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import(SalesTestSqs.class)
class SalesFoundationTests extends ResourceServerContract {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "sales");
    }
}
