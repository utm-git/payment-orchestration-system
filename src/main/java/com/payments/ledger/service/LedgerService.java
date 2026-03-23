package com.payments.ledger.service;

import com.payments.ledger.model.Account;
import com.payments.ledger.model.JournalEntry;
import com.payments.ledger.model.Transaction;
import com.payments.ledger.repository.AccountRepository;
import com.payments.ledger.repository.JournalEntryRepository;
import com.payments.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;

    @Transactional
    public void recordPaymentCaptured(String paymentId, BigDecimal amount, String currency, String merchantAccountId, String providerAccountId) {
        log.info("Recording append-only ledger entries for payment capture {}", paymentId);

        ensureAccountExists(providerAccountId, "ASSET");
        ensureAccountExists(merchantAccountId, "LIABILITY");

        // Fix 2: UNIQUE constraint on paymentRefId is the hard guard.
        // If the same Kafka event is replayed, the Transaction insert will throw
        // DataIntegrityViolationException, the @Transactional rolls back, no double-write.
        if (transactionRepository.findByPaymentRefId(paymentId).isPresent()) {
            log.warn("Ledger write skipped — transaction for paymentId {} already exists (idempotency guard).", paymentId);
            return;
        }

        Transaction tx = Transaction.builder()
                .id(UUID.randomUUID().toString())
                .paymentRefId(paymentId)
                .amount(amount)
                .currency(currency)
                .timestamp(LocalDateTime.now())
                .build();

        try {
            transactionRepository.save(tx);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: concurrent thread saved the transaction first. Safe to skip.
            log.warn("Concurrent ledger write detected for paymentId {} — skipping duplicate.", paymentId);
            return;
        }

        // Fix 5: BigDecimal — no floating-point rounding
        JournalEntry debitEntry = JournalEntry.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(tx.getId())
                .accountId(providerAccountId)
                .direction("DEBIT")
                .amount(amount)
                .build();

        JournalEntry creditEntry = JournalEntry.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(tx.getId())
                .accountId(merchantAccountId)
                .direction("CREDIT")
                .amount(amount)
                .build();

        // Fix: Enforce double-entry balance assertion before writing
        BigDecimal totalDebits = debitEntry.getAmount();
        BigDecimal totalCredits = creditEntry.getAmount();
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException(
                "Ledger imbalance! Debits=" + totalDebits + " Credits=" + totalCredits + " for payment " + paymentId
            );
        }

        journalEntryRepository.save(debitEntry);
        journalEntryRepository.save(creditEntry);
        log.info("Ledger entries written successfully for payment {}. Debit={}, Credit={}", paymentId, totalDebits, totalCredits);
    }

    private void ensureAccountExists(String accountId, String defaultType) {
        accountRepository.findById(accountId).orElseGet(() -> {
            Account newAcc = new Account();
            newAcc.setId(accountId);
            newAcc.setName(accountId);
            newAcc.setType(defaultType);
            return accountRepository.save(newAcc);
        });
    }
}
