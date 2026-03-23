package com.payments.webhook;

import com.payments.event.PaymentEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentEventProducer eventProducer;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestBody String payload) {
            
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            String eventId = payloadNode.has("id") ? payloadNode.get("id").asText() : java.util.UUID.randomUUID().toString();
            
            log.info("Received webhook from provider: {} with eventId: {}", provider, eventId);
            
            if (webhookEventRepository.existsById(eventId)) {
                log.info("Idempotent replay detected for eventId: {}. Skipping.", eventId);
                return ResponseEntity.ok().build();
            }

            // Save raw payload idempotently
            WebhookEvent event = new WebhookEvent();
            event.setEventId(eventId);
            event.setProvider(provider);
            event.setRawPayload(payload);
            event.setStatus("PENDING");
            webhookEventRepository.save(event);

            // Publish to Kafka for asynchronous processing accurately
            eventProducer.publishWebhookEvent(provider, payload);
            
            event.setStatus("PROCESSED");
            webhookEventRepository.save(event);
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            log.error("Failed to parse webhook", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
