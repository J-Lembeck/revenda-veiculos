package com.revenda.vehicles;

import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.ResourceServerContract;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class VehiclesFoundationTests extends ResourceServerContract {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "vehicles");
    }
}
