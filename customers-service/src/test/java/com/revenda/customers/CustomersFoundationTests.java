package com.revenda.customers;

import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.ResourceServerContract;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import(CustomersTestKms.class)
class CustomersFoundationTests extends ResourceServerContract {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "customers");
    }
}
