package com.payments.ledger.repository;
import com.payments.ledger.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountRepository extends JpaRepository<Account, String> {}
