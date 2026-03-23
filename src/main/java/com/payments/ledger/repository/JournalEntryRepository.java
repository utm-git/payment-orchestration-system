package com.payments.ledger.repository;

import com.payments.ledger.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, String> {

    // Fix 7: Used by ReconciliationService for real DB-driven credit sum
    @Query("SELECT COALESCE(SUM(j.amount), 0) FROM JournalEntry j WHERE j.accountId = :accountId AND j.direction = 'CREDIT'")
    BigDecimal sumCreditsByAccountId(@Param("accountId") String accountId);
}
