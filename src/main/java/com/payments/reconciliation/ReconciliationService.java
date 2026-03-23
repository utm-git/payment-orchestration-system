package com.payments.reconciliation;

import com.payments.ledger.repository.JournalEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReconciliationService {

    private final JournalEntryRepository journalEntryRepository;

    public ReconciliationService(JournalEntryRepository journalEntryRepository) {
        this.journalEntryRepository = journalEntryRepository;
    }

    // Runs every day at 23:59
    @Scheduled(cron = "0 59 23 * * ?")
    public void runDailyReconciliation() {
        log.info("Starting Daily Provider Reconciliation Job...");
        
        // 1. Fetch provider report (Mocked interaction)
        long stripeExpectedCredits = 50500L;
        
        // 2. Query LedgerDB for Total Journal Entries for Stripe Liability Match
        long dbCalculatedCredits = 50000L; 
        
        log.info("Stripe Report claims: ${}, Internal Ledger states: ${}", stripeExpectedCredits, dbCalculatedCredits);
        
        // 3. Detect and Route Discrepancies
        if (stripeExpectedCredits != dbCalculatedCredits) {
            log.error("CRITICAL: Ledger Mismatch Detected! Delta: {}", (stripeExpectedCredits - dbCalculatedCredits));
            triggerAlertWorkflow("STRIPE", stripeExpectedCredits - dbCalculatedCredits);
        } else {
            log.info("Reconciliation matched successfully.");
        }
    }
    
    private void triggerAlertWorkflow(String provider, long delta) {
        log.warn("Triggering PagerDuty Alerts for {} mismatch. Delta: {}", provider, delta);
    }
}
