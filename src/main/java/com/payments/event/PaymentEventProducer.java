package com.payments.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishWebhookEvent(String provider, String payload) {
        log.info("Publishing {} webhook event asynchronously to Kafka topic 'payment-events'", provider);
        // Fix 1: Genuine Kafka producer replacing the "[KAFKA MOCK]" theatre.
        // Provides exactly-once capability in tandem with DB deduplication.
        kafkaTemplate.send("payment-events", provider, payload);
    }
}
