package com.revenda.customers;

import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.ResourceServerContract;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class CustomersFoundationTests extends ResourceServerContract {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "customers");
    }
}
