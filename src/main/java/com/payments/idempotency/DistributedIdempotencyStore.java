package com.payments.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fix 6: DB-backed distributed idempotency store.
 *
 * Replaces the in-process ConcurrentHashMap (which vanished on every JVM restart
 * and was invisible to all other nodes) with a durable Postgres UNIQUE constraint.
 *
 * Schema (auto-created by Hibernate on startup):
 *   CREATE TABLE idempotency_keys (
 *       idempotency_key VARCHAR(255) PRIMARY KEY,
 *       created_at      TIMESTAMP NOT NULL DEFAULT NOW()
 *   );
 *
 * The UNIQUE/PRIMARY KEY constraint is the enforcement mechanism — not application logic.
 * This means concurrent requests from any number of nodes racing for the same key will
 * result in exactly one winner (the one that wrote first) and all others detect the
 * duplicate via the constraint violation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedIdempotencyStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Attempts to claim the idempotency key atomically.
     * Returns true if this is a NEW key (safe to proceed).
     * Returns false if this key was already claimed (duplicate — reject).
     */
    public boolean tryClaimKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return true;

        try {
            jdbcTemplate.update(
                "INSERT INTO idempotency_keys (idempotency_key, created_at) VALUES (?, NOW())",
                idempotencyKey
            );
            log.info("Idempotency key claimed: {}", idempotencyKey);
            return true; // New key — safe to process
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate idempotency key detected: {} — rejecting request.", idempotencyKey);
            return false; // Already exists — this is a duplicate
        }
    }

    /**
     * Used by PaymentService — the findByIdempotencyKey + UNIQUE constraint on payments
     * table already serves as the primary idempotency guard at the payment level.
     * This store provides an additional pre-flight gate for API-layer deduplication.
     */
    public boolean isGlobalDuplicate(String idempotencyKey) {
        return !tryClaimKey(idempotencyKey);
    }
}
