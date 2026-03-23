package com.payments.routing;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.Payment;
import com.payments.routing.provider.PaymentProvider;
import com.payments.routing.provider.RazorpayAdapter;
import com.payments.routing.provider.StripeAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoutingEngine {
    
    private final StripeAdapter stripeAdapter;
    private final RazorpayAdapter razorpayAdapter;
    
    // In-memory Mock metrics for providers mimicking Redis
    private final Map<String, ProviderMetrics> providerMetrics = Map.of(
        "STRIPE", new ProviderMetrics(0.99, 120.0, 0.029), // 99% success, 120ms latency, 2.9% cost
        "RAZORPAY", new ProviderMetrics(0.95, 200.0, 0.020) // 95% success, 200ms latency, 2.0% cost
    );

    public PaymentResponse routeAndProcess(Payment payment, PaymentRequest request) {
        log.info("Calculating optimal provider route for payment {} using weights (Success, Latency, Cost)", payment.getId());
        
        List<PaymentProvider> providers = List.of(stripeAdapter, razorpayAdapter);
        
        PaymentProvider optimalProvider = providers.stream()
            .max(Comparator.comparingDouble(p -> calculateProviderScore(p.getProviderName())))
            .orElse(stripeAdapter);
            
        log.info("Optimal provider selected dynamically: {}", optimalProvider.getProviderName());
        
        try {
            return optimalProvider.charge(request);
        } catch (Exception e) {
            log.warn("Primary provider {} failed. Falling back structurally...", optimalProvider.getProviderName());
            PaymentProvider fallback = optimalProvider == stripeAdapter ? razorpayAdapter : stripeAdapter;
            return fallback.charge(request);
        }
    }
    
    private double calculateProviderScore(String providerName) {
        ProviderMetrics metrics = providerMetrics.get(providerName);
        if (metrics == null) return 0;
        
        // Normalizing heuristic algorithm for scoring route efficiency
        double successScore = metrics.successRate * 100 * 0.5; // Weight 50%
        double latencyPenalty = metrics.latencyMs * 0.2; // Penalty Weight 20%
        double costPenalty = metrics.costBasis * 1000 * 0.3; // Penalty Weight 30%
        
        return successScore - latencyPenalty - costPenalty;
    }
    
    private record ProviderMetrics(double successRate, double latencyMs, double costBasis) {}
}
