package com.payments.routing.provider;

import com.payments.core.dto.PaymentRequest;
import com.payments.core.dto.PaymentResponse;

public interface PaymentProvider {
    PaymentResponse charge(PaymentRequest request);
    String getProviderName();
}
