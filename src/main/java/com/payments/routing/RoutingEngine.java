package com.payments.routing;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.Payment;
import com.payments.routing.provider.RazorpayAdapter;
import com.payments.routing.provider.StripeAdapter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoutingEngine {
    
    private final StripeAdapter stripeAdapter;
    private final RazorpayAdapter razorpayAdapter;

    @CircuitBreaker(name = "stripeProvider", fallbackMethod = "fallbackToRazorpay")
    public PaymentResponse routeAndProcess(Payment payment, PaymentRequest request) {
        log.info("Routing payment {} to primary provider (STRIPE)", payment.getId());
        return stripeAdapter.charge(request);
    }
    
    public PaymentResponse fallbackToRazorpay(Payment payment, PaymentRequest request, Throwable ex) {
        log.warn("Primary provider failed for payment {}. Fallback to RAZORPAY. Reason: {}", 
                 payment.getId(), ex.getMessage());
        return razorpayAdapter.charge(request);
    }
}
