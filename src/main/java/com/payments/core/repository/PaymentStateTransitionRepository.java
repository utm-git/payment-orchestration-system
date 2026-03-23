package com.payments.core.repository;
import com.payments.core.model.PaymentStateTransition;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentStateTransitionRepository extends JpaRepository<PaymentStateTransition, String> {}
