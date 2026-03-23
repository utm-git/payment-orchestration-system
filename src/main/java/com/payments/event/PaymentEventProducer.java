package com.payments.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishWebhookEvent(String provider, String payload) {
        log.info("Publishing webhook event for provider: {}", provider);
        kafkaTemplate.send("webhook-events", provider, payload);
    }
    
    public void publishPaymentEvent(String eventType, Object eventPayload) {
        log.info("Publishing payment event: {}", eventType);
        kafkaTemplate.send("payment-events", eventType, eventPayload);
    }
}
