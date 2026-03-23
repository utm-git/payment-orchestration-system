package com.payments.reconciliation;

import com.payments.core.model.PaymentStatus;
import com.payments.core.repository.PaymentRepository;
import com.payments.core.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    // End of Week batch payout
    @Scheduled(cron = "0 0 12 * * FRI")
    public void processPayouts() {
        log.info("Starting Weekly Merchant Payout Batching...");
        List<com.payments.core.model.Payment> settledPayments =
                paymentRepository.findAllByStatus(PaymentStatus.SETTLED);

        for (com.payments.core.model.Payment p : settledPayments) {
            // Fix 5: BigDecimal arithmetic — no floating-point rounding on fees
            BigDecimal grossAmount = BigDecimal.valueOf(p.getAmount());
            BigDecimal platformFeeRate = new BigDecimal("0.029");
            BigDecimal platformFee = grossAmount.multiply(platformFeeRate)
                    .setScale(4, java.math.RoundingMode.HALF_UP);
            BigDecimal netPayout = grossAmount.subtract(platformFee);

            log.info("Initiating ACH Wire for merchant {}. Gross: ${}, Fee: ${}, Net: ${}",
                    p.getCustomerId(),
                    grossAmount.divide(BigDecimal.valueOf(100)),
                    platformFee.divide(BigDecimal.valueOf(100)),
                    netPayout.divide(BigDecimal.valueOf(100)));

            // Transition to PAID_OUT — state machine allowlist enforces this is only legal from SETTLED
            paymentService.updatePaymentState(p, PaymentStatus.PAID_OUT, "Weekly ACH Payout Executed");
        }

        log.info("Batch Payout completed. Processed {} payments.", settledPayments.size());
    }
}
