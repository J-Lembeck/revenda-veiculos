package com.revenda.sales.domain.ports.in;

/**
 * Confere que o aviso veio do gateway: a assinatura HMAC do corpo bruto com o segredo do webhook e o carimbo de tempo
 * dentro da janela (anti-replay). Não confere → grava {@code PAYMENT_WEBHOOK_REJECTED} e sai como
 * {@code INVALID_SIGNATURE}.
 */
public interface VerifyPaymentWebhookUseCase {

    void verify(String signature, byte[] body);
}
