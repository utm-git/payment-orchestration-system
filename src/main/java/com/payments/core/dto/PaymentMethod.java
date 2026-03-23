package com.payments.core.dto;

import lombok.Data;

@Data
public class PaymentMethod {
    private String type; // e.g., "card"
    private String token; // e.g., "tok_xyz"
}
