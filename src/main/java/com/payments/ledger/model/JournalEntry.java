package com.payments.ledger.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "journal_entries")
@Getter             // ONLY getters — no setters. Prevents ORM dirty-check mutations.
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {
    @Id
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "transaction_id", updatable = false, nullable = false)
    private String transactionId;

    @Column(name = "account_id", updatable = false, nullable = false)
    private String accountId;

    // CREDIT or DEBIT — immutable once written
    @Column(updatable = false, nullable = false)
    private String direction;

    // Fix 5: BigDecimal for all monetary arithmetic — no floating-point rounding errors
    @Column(updatable = false, nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
}
