package com.payments.core.service;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.Payment;
import com.payments.core.model.PaymentStatus;
import com.payments.core.repository.PaymentRepository;
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
    private final RoutingEngine routingEngine;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // 1. Check idempotency
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existingPayment.isPresent()) {
            log.info("Returning existing payment for idempotency key: {}", request.getIdempotencyKey());
            return buildResponse(existingPayment.get());
        }

        // 2. Create Payment Record (INITIATED)
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setCustomerId(request.getCustomerId());
        payment.setStatus(PaymentStatus.INITIATED);
        payment = paymentRepository.save(payment);

        // 3. Route to Provider
        try {
            PaymentResponse providerResponse = routingEngine.routeAndProcess(payment, request);
            
            // 4. Update Payment Record
            payment.setStatus(providerResponse.getStatus());
            payment.setRoutedProvider(providerResponse.getProvider());
            payment.setProviderRefId(providerResponse.getPaymentId());
            paymentRepository.save(payment);
            
            return buildResponse(payment);
        } catch (Exception e) {
            log.error("Payment processing failed", e);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return buildResponse(payment);
        }
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
