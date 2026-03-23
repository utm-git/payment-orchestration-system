package com.payments.ledger.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "journal_entries")
@Data
@NoArgsConstructor
public class JournalEntry {
    @Id
    private String id;
    private String transactionId;
    private String accountId;
    private String direction;
    private Long amount;
}
