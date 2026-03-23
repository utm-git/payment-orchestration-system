package com.payments.core.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
public class Payment {
    
    @Id
    @Column(nullable = false, unique = true)
    private String id;
    
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    
    @Column(nullable = false)
    private Long amount;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    
    @Column(name = "routed_provider")
    private String routedProvider;
    
    @Column(name = "provider_ref_id")
    private String providerRefId;
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
