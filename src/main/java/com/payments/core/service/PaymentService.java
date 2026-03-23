package com.payments.core.service;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.Payment;
import com.payments.core.model.PaymentStatus;
import com.payments.core.model.PaymentStateTransition;
import com.payments.core.repository.PaymentRepository;
import com.payments.core.repository.PaymentStateTransitionRepository;
import com.payments.routing.RoutingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final RoutingEngine routingEngine;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // 1. Check idempotency
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPayment.isPresent()) {
            return buildResponse(existingPayment.get());
        }

        // 2. Create Payment Record
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerId(request.getCustomerId());
        
        payment = paymentRepository.save(payment);
        updatePaymentState(payment, PaymentStatus.CREATED, "Initial request received");

        // 3. Route to Provider
        try {
            PaymentResponse providerResponse = routingEngine.routeAndProcess(payment, request);
            payment.setRoutedProvider(providerResponse.getProvider());
            payment.setProviderRefId(providerResponse.getPaymentId());
            updatePaymentState(payment, providerResponse.getStatus(), "Provider processed");
            return buildResponse(payment);
        } catch (Exception e) {
            log.error("Payment processing failed", e);
            updatePaymentState(payment, PaymentStatus.FAILED, e.getMessage());
            return buildResponse(payment);
        }
    }
    
    @Transactional
    public void updatePaymentState(Payment payment, PaymentStatus newState, String reason) {
        PaymentStatus oldState = payment.getStatus();
        
        // Basic State Machine Validation
        if (oldState == PaymentStatus.FAILED || oldState == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Cannot transition from terminal state " + oldState);
        }
        
        payment.setStatus(newState);
        paymentRepository.save(payment);
        
        PaymentStateTransition transition = new PaymentStateTransition(
            payment.getId(), oldState, newState, reason
        );
        transitionRepository.save(transition);
        log.info("Payment {} transitioned: {} -> {}", payment.getId(), oldState, newState);
    }

    private PaymentResponse buildResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .provider(payment.getRoutedProvider())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
