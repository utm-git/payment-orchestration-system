package com.payments.ledger.service;

import com.payments.ledger.model.Account;
import com.payments.ledger.model.JournalEntry;
import com.payments.ledger.model.Transaction;
import com.payments.ledger.repository.AccountRepository;
import com.payments.ledger.repository.JournalEntryRepository;
import com.payments.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public void recordPaymentCaptured(String paymentId, Long amount, String currency, String merchantAccountId, String providerAccountId) {
        log.info("Recording append-only ledger entries for successful payment capture {}", paymentId);
        
        ensureAccountExists(providerAccountId, "ASSET");
        ensureAccountExists(merchantAccountId, "LIABILITY");
        
        // 1. Transaction
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID().toString());
        tx.setPaymentRefId(paymentId);
        tx.setAmount(amount);
        tx.setCurrency(currency);
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);

        // 2. Journal Entry - DEBIT Provider Account (Asset)
        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setId(UUID.randomUUID().toString());
        debitEntry.setTransactionId(tx.getId());
        debitEntry.setAccountId(providerAccountId);
        debitEntry.setDirection("DEBIT");
        debitEntry.setAmount(amount);
        journalEntryRepository.save(debitEntry);

        // 3. Journal Entry - CREDIT Merchant Account (Liability)
        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setId(UUID.randomUUID().toString());
        creditEntry.setTransactionId(tx.getId());
        creditEntry.setAccountId(merchantAccountId);
        creditEntry.setDirection("CREDIT");
        creditEntry.setAmount(amount);
        journalEntryRepository.save(creditEntry);
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
