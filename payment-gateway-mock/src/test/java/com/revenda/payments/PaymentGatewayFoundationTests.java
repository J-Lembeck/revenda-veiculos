package com.revenda.payments;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.ServiceFoundationContract;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        PaymentGatewayApiTest.API_KEY_PROPERTY,
        PaymentGatewayApiTest.WEBHOOK_SECRET_PROPERTY,
        PaymentGatewayApiTest.RETRY_DELAYS_PROPERTY})
class PaymentGatewayFoundationTests extends ServiceFoundationContract {

    @DynamicPropertySource
    static void webhook(DynamicPropertyRegistry registry) {
        PaymentGatewayApiTest.registerWebhook(registry);
    }

    @Override
    protected ApiDriver api() {
        return anonymous().withHeader("X-Api-Key", PaymentGatewayApiTest.API_KEY);
    }
}
