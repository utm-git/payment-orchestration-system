-- Fix 6: Idempotency keys table — DB-backed durable idempotency store
-- Replaces the in-process ConcurrentHashMap that vanished on every JVM restart
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
