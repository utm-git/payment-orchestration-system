package com.payments.event;

import com.payments.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {
    
    private final LedgerService ledgerService;

    @KafkaListener(topics = "payment-events", groupId = "payment-group")
    public void consumePaymentEvent(String eventPayload) {
        log.info("Received payment event: {}", eventPayload);
        // Real implementation would parse event JSON to trigger ledgerService...
    }
}
