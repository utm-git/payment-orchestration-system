package com.payments.routing.provider;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;
import com.payments.core.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@Slf4j
public class RazorpayAdapter implements PaymentProvider {

    @Override
    public PaymentResponse charge(PaymentRequest request) {
        log.info("Calling Razorpay API for amount: {}", request.getAmount());
        
        return PaymentResponse.builder()
                .paymentId("pay_" + System.currentTimeMillis())
                .provider(getProviderName())
                .status(PaymentStatus.CAPTURED)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    public String getProviderName() {
        return "RAZORPAY";
    }
}
