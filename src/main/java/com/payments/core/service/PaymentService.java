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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateTransitionRepository transitionRepository;
    private final RoutingEngine routingEngine;

    /**
     * Fix 8: Explicit forward-only transition allowlist.
     * Only these (fromState -> toStates) pairs are legal.
     * Any other transition throws IllegalStateException immediately.
     * Fix 3: FAILED -> CAPTURED is legal to allow webhook-driven auto-healing.
     */
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = Map.of(
        PaymentStatus.CREATED,    Set.of(PaymentStatus.AUTHORIZED, PaymentStatus.FAILED),
        PaymentStatus.AUTHORIZED, Set.of(PaymentStatus.CAPTURED, PaymentStatus.FAILED),
        PaymentStatus.CAPTURED,   Set.of(PaymentStatus.SETTLED, PaymentStatus.REFUNDED, PaymentStatus.FAILED),
        PaymentStatus.FAILED,     Set.of(PaymentStatus.CAPTURED), // Fix 3: webhook healing from FAILED -> CAPTURED
        PaymentStatus.SETTLED,    Set.of(PaymentStatus.PAID_OUT),
        PaymentStatus.REFUNDED,   Set.of(),
        PaymentStatus.PAID_OUT,   Set.of()
    );

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // Idempotency: attempt DB insert, handle UNIQUE constraint violation cleanly
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPayment.isPresent()) {
            log.info("Idempotent request detected for key {}. Returning cached response.", request.getIdempotencyKey());
            return buildResponse(existingPayment.get());
        }

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerId(request.getCustomerId());

        try {
            payment = updatePaymentState(payment, PaymentStatus.CREATED, "Initial request received");
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request with same idempotency key won the DB race — return their result
            return buildResponse(paymentRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElseThrow());
        }

        try {
            PaymentResponse providerResponse = routingEngine.routeAndProcess(payment, request);
            payment.setRoutedProvider(providerResponse.getProvider());
            payment.setProviderRefId(providerResponse.getPaymentId());
            payment = updatePaymentState(payment, providerResponse.getStatus(), "Provider processed");
            return buildResponse(payment);
        } catch (Exception e) {
            log.error("Payment processing failed", e);
            payment = updatePaymentState(payment, PaymentStatus.FAILED, e.getMessage());
            return buildResponse(payment);
        }
    }

    @Transactional
    public Payment updatePaymentState(Payment payment, PaymentStatus newState, String reason) {
        PaymentStatus oldState = payment.getStatus();

        // Fix 8: Validate against explicit allowlist — backward transitions are impossible
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(oldState, Set.of());
        if (!allowed.contains(newState)) {
            throw new IllegalStateException(
                String.format("Invalid state transition: %s -> %s is not permitted.", oldState, newState)
            );
        }

        payment.setStatus(newState);
        payment = paymentRepository.save(payment);

        PaymentStateTransition transition = new PaymentStateTransition(
            payment.getId(), oldState, newState, reason
        );
        transitionRepository.save(transition);
        log.info("Payment {} transitioned: {} -> {}", payment.getId(), oldState, newState);
        return payment;
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
