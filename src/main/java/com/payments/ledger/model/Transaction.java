package com.payments.ledger.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "transactions",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_transaction_payment_ref",
        columnNames = "payment_ref_id" // Fix 2: Prevents double-write if consumer re-processes same event
    )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "payment_ref_id", updatable = false, nullable = false)
    private String paymentRefId;

    // Fix 5: BigDecimal throughout all monetary fields
    @Column(updatable = false, nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(updatable = false, nullable = false)
    private String currency;

    @Column(updatable = false, nullable = false)
    private LocalDateTime timestamp;
}
