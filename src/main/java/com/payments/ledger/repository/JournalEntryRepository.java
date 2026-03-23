package com.payments.ledger.repository;
import com.payments.ledger.model.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JournalEntryRepository extends JpaRepository<JournalEntry, String> {}
