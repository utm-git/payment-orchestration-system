package com.payments.core.dto;

import com.payments.core.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private String paymentId;
    private PaymentStatus status;
    private String provider;
    private Long amount;
    private String currency;
    private LocalDateTime createdAt;
}
