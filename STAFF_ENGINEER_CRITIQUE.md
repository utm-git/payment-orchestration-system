# Staff Engineer Critique: Global Payments Platform

> Evaluation from the perspective of a Staff+ Engineer / Hiring Committee member at a top-tier distributed systems company (Stripe, Google Pay, Uber).
> Assumes: millions of transactions/day, real-world network failures, multi-region deployments.

---

## 1. ARCHITECTURE GAPS

**Missing components that would cause immediate rejection:**

- **No API Gateway auth.** There is zero discussion of AuthN/AuthZ. Who calls `POST /v1/payments`? Merchants must be authenticated via versioned API keys, JWTs, or mTLS. The design has none of it.
- **No authorization on the Ledger Service.** Any internal service can write journal entries directly. A bug in the Routing Service could create phantom ledger entries. Ledger writes must flow through a single, tightly controlled write path only.
- **Routing metrics stored in Redis with no persistence.** If Redis restarts, `successRate` and `latencyMs` vanish. The engine falls back blindly to Stripe even if Stripe was at 80% failure rate. No circuit breaker TTL, no grace period, no warm-up.
- **No Saga / distributed transaction coordinator.** The flow is: save payment → call provider → write ledger. Three separate operations with no rollback coordination. If the ledger write fails after the provider charges the card, the money is gone with no record.
- **DLQ is documented but never processed.** Missed events silently disappear.
- **Over-engineered routing formula with no operational leverage.** Hardcoded weights with no configuration endpoints, no A/B testing hooks, no shadow mode.

---

## 2. FAILURE MODE ANALYSIS

### Failure 1: Payment marked FAILED but Stripe actually charged the card
- **Today:** State machine moves to `FAILED`. When the webhook arrives later, `updatePaymentState` throws `IllegalStateException` because `FAILED` is a terminal state. The webhook is permanently dropped. **Customer is charged. DB says failed. No refund triggered.**
- **Fix:** `FAILED` must be a soft terminal only for provider-driven transitions. Webhook-driven corrections from `FAILED → CAPTURED` must be an explicitly modeled legal transition.

### Failure 2: Kafka consumer crashes mid-processing
- **Today:** Kafka auto-commits offsets. Consumer updates PaymentDB, crashes before writing to LedgerDB. Offset is committed, event is lost, ledger entry is missing with no alarm.
- **Fix:** Manual offset commits only, flushed *after* both DB writes succeed atomically. Use the Transactional Outbox pattern, not direct Kafka publishing.

### Failure 3: Redis unavailable during idempotency check
- **Today:** `DistributedIdempotencyStore` is a JVM `ConcurrentHashMap`. No fallback if Redis is down. During a Redis failure + traffic spike, result is duplicate charges.
- **Fix:** Degrade to DB-backed idempotency check. The `idempotency_key` column must have a DB-level `UNIQUE` constraint as the final safety net.

### Failure 4: Payout batch fails at item 500 of 10,000
- **Today:** `PayoutService` loops through all `SETTLED` payments, marking them `PAID_OUT` one by one. Failure at item 500 leaves items 1-499 marked `PAID_OUT` incorrectly, item 500 permanently skipped on re-run.
- **Fix:** Each payout needs its own state machine (`PENDING → SUBMITTED → CONFIRMED → FAILED`). Cursor-based resumable batches. Bank ACH APIs return async confirmations—never assume synchronous success.

### Failure 5: Reconciliation triggers automated financial repair
- **Today:** On mismatch, `ReconSvc` fires a Kafka event for auto-repair. Auto-repairing financial records without human approval is a regulatory and audit violation.
- **Fix:** Mismatches create `discrepancy_report` DB records and trigger human review workflows (PagerDuty, JIRA). Only provably safe cases (missing credit where provider confirms charge) are auto-resolved.

---

## 3. CONSISTENCY & DATA CORRECTNESS

**Where double charges can happen today:**

1. **Client retry with new idempotency key.** If request 1 is in-flight (`PROCESSING`), request 2 with the same key gets a 409. Client generates a *new* key and retries. Double charge.
2. **No optimistic locking on Payment entity.** Two concurrent webhooks for the same `payment_id` can both read `status = AUTHORIZED`, both validate, both write `status = CAPTURED`. Two ledger entries created. `@Transactional` does not prevent this without `SELECT FOR UPDATE` or `@Version`.
3. **`findByIdempotencyKey` + `save` is not atomic.** Two simultaneous requests both pass `isPresent()` before either writes, causing a `DataIntegrityViolationException`. No handler in `processPayment` — propagates as an unhandled 500.

---

## 4. LEDGER SYSTEM DEEP DIVE

- **Not truly immutable.** The `journal_entries` table has no DB-level `INSERT ONLY` guarantee. Any code with write access can run `UPDATE` or `DELETE`. Immutability is a convention, not an enforcement.
- **Cannot scale to millions TPS.** `saveAll(entries)` writes to a single table. No materialized balance cache — computing merchant balance requires a full aggregate scan. At millions of entries this is a multi-second query.
- **Replay is broken.** Kafka Compact topics deduplicate by key, not by ordering. Replaying compact topics does not guarantee chronological order — you can derive a wrong final balance. Ledger reconstruction requires WAL-based total ordering.

---

## 5. DISTRIBUTED SYSTEM RISKS

- **Redis CRDTs cannot guarantee financial uniqueness.** LWW register propagation between US-East and EU-West can have 100-200ms delay. The same idempotency key can be accepted in both regions during this window. CRDTs are for counters and tombstones, not linearizable constraints.
- **Kafka MirrorMaker2 + region-local `processed_events` table = duplicate ledger entries.** If `payment.captured` is mirrored and replayed in EU-West, the consumer writes a ledger entry that already exists. The deduplication table is region-local and has no cross-region coverage.
- **`enable.idempotence=true` is misunderstood.** This prevents duplicate messages *from that producer to that broker only*. It does nothing for consumer-side deduplication.

---

## 6. SETTLEMENT & MONEY FLOW

- **T+2 settlement is not modeled.** When does `CAPTURED` become `SETTLED`? Based on what signal? Real settlement requires receiving a bank settlement file (ISO 20022), matching against internal charge IDs, then crediting the merchant.
- **Currency is ignored.** `amount` is a plain `BIGINT` with a `currency` string never factored into payout calculations. FX conversion is missing entirely.
- **Fee hardcoded at 2.9%.** Wrong in every jurisdiction that isn't US consumer cards. Varies by card type (Visa vs Amex), region (EU interchange caps), and transaction type.
- **ACH confirmation is faked.** The payout marks `PAID_OUT` before confirmation. In production, 1-3% of ACH transfers fail with return codes like `R02 - Account Closed`. Your merchant is told they've been paid when they haven't.

---

## 7. PERFORMANCE & SCALABILITY

What breaks first at 10x scale:

1. **`findByIdempotencyKey`** on every request — uncached, 10x reads on the payments table.
2. **`payment_state_transitions`** grows unboundedly — no partitioning by `created_at`, no archival policy.
3. **`ReconciliationService` runs inside the same JVM** as PaymentService — CRON job competes with live threads for HikariCP connections.
4. **Routing Engine makes a synchronous Redis call per payment** — a 50ms Redis p99 spike = 50ms added to every payment response.
5. **HikariCP default of 10 connections.** Exhausted instantly at real traffic. No read replicas configured.

---

## 8. SECURITY & FRAUD

- **No webhook signature verification.** The `WebhookController` has no HMAC-SHA256 check for the `Stripe-Signature` header. Anyone can POST a fabricated payload and trigger state transitions.
- **Fraud scoring is a single amount threshold.** `if amount > 500000, return false`. A fraudster sending 4,999 transactions at $49.99 each passes every check.
- **No inter-service auth.** Any service can write arbitrary ledger entries without tokens or mTLS.
- **H2 console is enabled in production** (visible in startup logs). Direct DB access via web UI in production is a critical vulnerability.

---

## 9. TRADEOFFS

| Decision Made | Problem | Better Alternative |
|---|---|---|
| Spring `@Scheduled` Reconciliation | Competes with request threads | Dedicated Kubernetes CronJob or Temporal workflow |
| Redis CRDT for global idempotency | Eventually consistent, wrong tool | CockroachDB serializable txn or etcd distributed lock |
| Kafka for Ledger writes (async) | Ledger lags behind Payment state | Transactional Outbox — synchronous ledger write in same DB txn |
| Hash sharding by `merchant_account_id` | Hot shards for large merchants | Consistent hashing with virtual nodes + shard rebalancing |
| Mocked Kafka | Nothing tested under failure | Embedded Kafka / Testcontainers in integration tests |
| Monorepo for all services | All services restart together | Separate deployable units per service boundary |

---

## 10. FINAL VERDICT

| Level | Verdict | Justification |
|---|---|---|
| **SDE3** | ✅ Hire | Solid conceptual grasp of distributed systems. Knows the right vocabulary, can model failure scenarios, understands double-entry accounting. |
| **Staff** | ❌ No Hire | CRDT misuse, silent Kafka offset commit failure, un-atomic idempotency check, and fake fraud detection show the design was built to *look* correct, not *be* correct. |
| **Senior Staff** | ❌ Hard No | No knowledge of real bank file formats, no regulatory awareness (PCI-DSS, PSD2 SCA, AML). Running a payment platform without these is illegal in most jurisdictions. |

---

---

# Staff Engineer Reading Plan

> Structured by gap severity. Every resource below maps directly to a failure identified in the critique above.

## Stage 1 — Foundation Gaps (Weeks 1–2)

| Topic | Resource | Gap It Fixes |
|---|---|---|
| Saga Pattern | [Martin Fowler — Saga Pattern](https://martinfowler.com/articles/patterns-of-distributed-systems/saga.html) | Manual try/catch for distributed coordination |
| Transactional Outbox | [Microservices.io — Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html) | Kafka + DB atomicity gap |
| Idempotency in APIs | [Stripe Engineering Blog — Idempotency](https://stripe.com/blog/idempotency) | `findBy + save` race condition |
| Money Pattern | [Martin Fowler — Money Pattern](https://martinfowler.com/eaaCatalog/money.html) | `BIGINT + VARCHAR` currency storage |

## Stage 2 — Consistency & Correctness (Weeks 3–5)

| Topic | Resource | Gap It Fixes |
|---|---|---|
| Consistency Models | [Jepsen — Consistency Map](https://jepsen.io/consistency) | CRDT misuse for idempotency |
| Optimistic Locking | [Vlad Mihalcea — JPA `@Version`](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/) | Concurrent webhook race condition |
| CRDTs | [Shapiro et al. — CRDTs (2011)](https://hal.inria.fr/inria-00555588) | Understanding what CRDTs actually guarantee |
| Kafka Exactly-Once | [Confluent — EOS Deep Dive](https://www.confluent.io/blog/exactly-once-semantics-are-possible-heres-how-apache-kafka-does-it/) | `enable.idempotence` misunderstanding |

## Stage 3 — Real-World Money Movement (Weeks 6–8)

| Topic | Resource | Gap It Fixes |
|---|---|---|
| ACH Return Codes | [NACHA — Return Code Reference](https://www.nacha.org/rules/understanding-return-codes) | Faked ACH payout confirmation |
| ISO 20022 | [SWIFT — ISO 20022 Overview](https://www.swift.com/our-solutions/messages-and-standards/iso-20022) | T+2 settlement file format |
| Card Payouts | [Stripe — How Payouts Work](https://stripe.com/docs/payouts) | Settlement lifecycle gaps |
| Currency Handling | [Martin Fowler — Money Pattern](https://martinfowler.com/eaaCatalog/money.html) | FX conversion missing entirely |

## Stage 4 — Scalability & Production Systems (Weeks 9–12)

| Topic | Resource | Gap It Fixes |
|---|---|---|
| **DDIA Chapters 5, 6, 7, 9** | Kleppmann — Designing Data-Intensive Applications | Replication, partitioning, transactions, consensus — all ledger gaps |
| Database Internals | Alex Petrov — Chapters 1–4 | WAL, MVCC — ledger immutability at DB level |
| Citus Sharding | [Citus Docs — Data Modeling](https://docs.citusdata.com/en/latest/sharding/data_modeling.html) | Hot-shard problem in ledger |
| Temporal.io | [Temporal — Core Concepts](https://docs.temporal.io/concepts) | Replaces manual saga/state machine |

## Stage 5 — Security & Compliance (Weeks 13–14)

| Topic | Resource | Gap It Fixes |
|---|---|---|
| PCI-DSS | [PCI SSC — Quick Reference Guide](https://www.pcisecuritystandards.org/documents/PCI_DSS-QRG-v3_2_1.pdf) | H2 console exposed, no inter-service auth |
| PSD2 & SCA | [EBA — PSD2 Technical Standards](https://www.eba.europa.eu/regulation-and-policy/payment-services-and-electronic-money) | 3DS exemption logic missing |
| Webhook Signature Verification | [Stripe — Verifying Webhooks](https://stripe.com/docs/webhooks/signatures) | HMAC-SHA256 missing from `WebhookController` |

## Stage 6 — Engineering Blog Deep Dives (Ongoing)

- **[Stripe Engineering Blog](https://stripe.com/blog/engineering)** — Every post tagged "payments", "idempotency", "ledger"
- **[Cloudflare Blog — Distributed Systems](https://blog.cloudflare.com/tag/distributed-systems/)** — Real failure modes at scale
- **[Uber Engineering — Money Platform](https://www.uber.com/en-US/newsroom/money-platform/)** — Ledger rebuild at scale
- **[Square Engineering Blog](https://developer.squareup.com/blog/)** — Card-present flows, bank integrations
- **[Martin Kleppmann's Blog](https://martin.kleppmann.com/)** — Goes deeper than DDIA

## Recommended Weekly Schedule

```
Week 1–2:   Stage 1 (Foundation)
Week 3–5:   Stage 2 (Consistency) + DDIA Ch. 7 & 9 in parallel
Week 6–8:   Stage 3 (Money Movement)
Week 9–12:  Stage 4 (Scalability) + Rewrite PayoutService using Temporal as a side project
Week 13–14: Stage 5 (Compliance)
Ongoing:    Stage 6 (2 blog posts/week)
```

> **Highest leverage action this week:** Read DDIA Chapters 7 and 9. Every other gap in the critique flows from a weak understanding of transactions and consistency models.
