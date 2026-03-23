package com.payments.ledger.repository;

import com.payments.ledger.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    // Fix 2: Used by LedgerService idempotency guard to detect existing transaction before writing
    Optional<Transaction> findByPaymentRefId(String paymentRefId);
}
