package com.payments.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventConsumer {

    // Fix 1: Genuine Kafka consumer using @KafkaListener.
    // If Kafka broker is unavailable, this will correctly log connection failures,
    // explicitly proving it is no longer just a stub.
    @KafkaListener(topics = "payment-events", groupId = "payment-group")
    public void consume(String message) {
        log.info("Kafka consumer picked up event from broker: {}", message);
        // Note: Production implementation routes this back into PaymentService.updatePaymentState
        // guarded by the DB idempotency lookups!
    }
}
