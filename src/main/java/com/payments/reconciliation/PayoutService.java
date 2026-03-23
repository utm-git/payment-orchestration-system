package com.payments.reconciliation;

import com.payments.core.model.PaymentStatus;
import com.payments.core.repository.PaymentRepository;
import com.payments.core.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    // End of Week batch payout utilizing external Wire/ACH mechanisms
    @Scheduled(cron = "0 0 12 * * FRI")
    public void processPayouts() {
        log.info("Starting Weekly Merchant Payout Batching...");
        List<com.payments.core.model.Payment> settledPayments = paymentRepository.findAllByStatus(PaymentStatus.SETTLED);
        
        long totalAmount = 0;
        for (com.payments.core.model.Payment p : settledPayments) {
            long platformFee = (long) (p.getAmount() * 0.029); // Platform scaling 2.9% fee
            long netPayout = p.getAmount() - platformFee;
            totalAmount += netPayout;
            
            // Execute simulated external ACH Wire transfer payload
            log.info("Initiating ACH Wire for Merchant {} globally. Net Amount: ${}", p.getCustomerId(), netPayout/100.0);
            
            // Advance strict state machine out of SETTLED to PAID_OUT
            paymentService.updatePaymentState(p, PaymentStatus.PAID_OUT, "Weekly ACH Payout Executed");
        }
        
        log.info("Batch Payout loop completed flawlessly. Wired total net: ${}", totalAmount/100.0);
    }
}
