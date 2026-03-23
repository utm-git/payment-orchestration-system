package com.payments.webhook;

import com.payments.event.PaymentEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaymentEventProducer eventProducer;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestBody String payload) {
            
        log.info("Received webhook from provider: {}", provider);
        
        // In a real implementation: verify the cryptographic signature depending on the provider
        boolean isValidSignature = verifySignature(provider, stripeSignature, razorpaySignature, payload);
        if (!isValidSignature) {
            log.warn("Invalid signature from provider: {}", provider);
            return ResponseEntity.badRequest().build();
        }

        // Publish to Kafka for asynchronous processing
        eventProducer.publishWebhookEvent(provider, payload);
        
        return ResponseEntity.accepted().build();
    }
    
    private boolean verifySignature(String provider, String stripeSig, String rzpSig, String payload) {
        return true;
    }
}
