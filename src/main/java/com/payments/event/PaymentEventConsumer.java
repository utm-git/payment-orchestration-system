package com.payments.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventConsumer {
    // Mocked consumer block. No @KafkaListener so Spring doesn't crash polling localhost:9092
    public void consume(String message) {
        log.info("[KAFKA MOCK] Consumed payment event: {}", message);
    }
}
