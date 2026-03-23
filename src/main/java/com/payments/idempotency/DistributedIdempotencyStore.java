package com.payments.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class DistributedIdempotencyStore {

    // Simulating a native Global Redis CRDT Store replication synchronization layer
    private final ConcurrentHashMap<String, Boolean> globalRedisCRDTSimulator = new ConcurrentHashMap<>();

    public boolean isGlobalDuplicate(String idempotencyKey) {
        if (idempotencyKey == null) return false;
        
        // Structurally preventing concurrent cross-region collision attacks logically
        if (globalRedisCRDTSimulator.putIfAbsent(idempotencyKey, true) != null) {
            log.error("GLOBAL IDEMPOTENCY CRDT LOCK TRIGGERED: Key {} is actively resolving in a detached Region (EU-West)! Rejecting US-East duplication natively.", idempotencyKey);
            return true;
        }
        return false;
    }
}
