package com.payments.routing.provider;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@Slf4j
public class StripeAdapter implements PaymentProvider {

    @Override
    public PaymentResponse charge(PaymentRequest request) {
        log.info("Calling Stripe API for amount: {}", request.getAmount());
        
        // Mock Stripe API call - force failure if amount > 10000
        if (request.getAmount() > 10000) {
            log.error("Stripe API Timeout simulation");
            throw new RuntimeException("Stripe API Timeout");
        }
        
        return PaymentResponse.builder()
                .paymentId("ch_" + System.currentTimeMillis())
                .provider(getProviderName())
                .status(PaymentStatus.CAPTURED)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    public String getProviderName() {
        return "STRIPE";
    }
}
