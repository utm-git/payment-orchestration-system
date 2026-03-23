package com.payments.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventProducer {
    public void publishWebhookEvent(String provider, String payload) {
        log.info("[KAFKA MOCK] Publishing webhook event to asynchronous topic for provider {}: {}", provider, payload);
    }
}
