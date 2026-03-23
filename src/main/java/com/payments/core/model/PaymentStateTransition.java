package com.payments.core.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_state_transitions")
@Data
@NoArgsConstructor
public class PaymentStateTransition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "payment_id", nullable = false)
    private String paymentId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state")
    private PaymentStatus previousState;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", nullable = false)
    private PaymentStatus newState;
    
    private String reason;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public PaymentStateTransition(String paymentId, PaymentStatus previousState, PaymentStatus newState, String reason) {
        this.paymentId = paymentId;
        this.previousState = previousState;
        this.newState = newState;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }
}
