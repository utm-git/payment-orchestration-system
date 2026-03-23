package com.payments.core.repository;

import com.payments.core.model.Payment;
import com.payments.core.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findAllByStatus(PaymentStatus status);
}
