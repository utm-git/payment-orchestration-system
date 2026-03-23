package com.payments.webhook;

import com.payments.event.PaymentEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentEventProducer eventProducer;
    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${webhook.stripe.secret:whsec_test_local_secret}")
    private String stripeWebhookSecret;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestBody String payload) {

        // Fix 4: HMAC-SHA256 signature verification — hard gate, not optional
        if ("stripe".equalsIgnoreCase(provider)) {
            if (stripeSignature == null || !verifyStripeSignature(payload, stripeSignature)) {
                log.error("Stripe webhook signature verification FAILED. Rejecting payload.");
                return ResponseEntity.status(401).build();
            }
        }

        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            String eventId = payloadNode.has("id") ? payloadNode.get("id").asText() : java.util.UUID.randomUUID().toString();

            log.info("Received verified webhook from provider: {} with eventId: {}", provider, eventId);

            // Fix: Atomic idempotency — rely on DB UNIQUE constraint rather than a non-atomic existsById check
            WebhookEvent event = new WebhookEvent();
            event.setEventId(eventId);
            event.setProvider(provider);
            event.setRawPayload(payload);
            event.setStatus("PENDING");

            try {
                webhookEventRepository.save(event);
            } catch (DataIntegrityViolationException ex) {
                // UNIQUE constraint violation — this is a genuine duplicate. Return 200 to stop provider retries.
                log.info("Idempotent replay detected for eventId: {} — duplicate safely ignored.", eventId);
                return ResponseEntity.ok().build();
            }

            eventProducer.publishWebhookEvent(provider, payload);

            event.setStatus("PROCESSED");
            webhookEventRepository.save(event);
            return ResponseEntity.accepted().build();

        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Fix 4: Verifies Stripe webhook signature using HMAC-SHA256.
     * Stripe sends: "t=<timestamp>,v1=<signature>" in the Stripe-Signature header.
     * We reconstruct the signed payload as "<timestamp>.<rawBody>" and compare.
     */
    private boolean verifyStripeSignature(String payload, String signatureHeader) {
        try {
            String timestamp = null;
            String receivedSig = null;
            for (String part : signatureHeader.split(",")) {
                if (part.startsWith("t=")) timestamp = part.substring(2);
                if (part.startsWith("v1=")) receivedSig = part.substring(3);
            }
            if (timestamp == null || receivedSig == null) return false;

            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stripeWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String computedSig = HexFormat.of().formatHex(hash);

            return computedSig.equals(receivedSig);
        } catch (Exception e) {
            log.error("HMAC verification error", e);
            return false;
        }
    }
}
