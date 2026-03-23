package com.payments.core.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.Map;

@Data
public class PaymentRequest {
    @NotBlank
    private String idempotencyKey;
    
    @NotNull
    @Min(1)
    private Long amount;
    
    @NotBlank
    private String currency;
    
    @NotBlank
    private String customerId;
    
    @NotNull
    private PaymentMethod paymentMethod;
    
    private Map<String, String> metadata;
}
