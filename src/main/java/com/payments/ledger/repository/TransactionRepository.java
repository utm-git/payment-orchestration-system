package com.payments.ledger.repository;
import com.payments.ledger.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TransactionRepository extends JpaRepository<Transaction, String> {}
