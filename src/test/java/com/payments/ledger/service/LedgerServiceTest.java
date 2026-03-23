package com.payments.ledger.service;

import com.payments.ledger.model.Account;
import com.payments.ledger.model.JournalEntry;
import com.payments.ledger.model.Transaction;
import com.payments.ledger.repository.AccountRepository;
import com.payments.ledger.repository.JournalEntryRepository;
import com.payments.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private JournalEntryRepository journalEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    public void testRecordPaymentCaptured() {
        when(accountRepository.findById(anyString())).thenReturn(Optional.empty());

        ledgerService.recordPaymentCaptured("pay_123", new java.math.BigDecimal("1000"), "USD", "merch_1", "prov_1");

        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(journalEntryRepository, times(2)).save(any(JournalEntry.class));
        verify(accountRepository, times(2)).save(any(Account.class));
    }
}
