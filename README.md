# Global-Scale Payment Orchestration Platform

A highly scalable, fault-tolerant, and dynamic Fintech backend processing network architected in Spring Boot 3. Built replicating the advanced system design concepts powering modern payment gateways like Stripe, Adyen, and Razorpay.

## 🚀 Key Architectural Features
- **Dynamic Routing Engine**: Mathematically routing payment payloads across multiple mocked providers (Stripe, Razorpay) leveraging real-time cost, latency, and success-rate heuristics.
- **Double-Entry Immutable Ledger**: Strictly enforcing the fundamental accounting law (Debits = Credits). Account balances are never directly updated; computations are purely chronological append-only aggregates.
- **Global Distributed Idempotency**: Edge API boundaries natively track `idempotencyKeys` and automatically reject duplicate cross-regional `POST` loop payloads logically mimicking Redis CRDT token-buckets.
- **Fraud & Velocity ML Scoring**: Dedicated models scoring transaction velocity recursively against global thresholds—dynamically injecting `3D-Secure` friction upon high-risk profiles dynamically.
- **Robust Payment State Machine**: Persisting terminal configurations tightly bounded by explicit Database Constraints mapping strict lifecycles (`CREATED` -> `AUTHORIZED` -> `CAPTURED` -> `SETTLED` -> `PAID_OUT`).
- **Settlement & Wire Payout Services**: Background asynchronous CRON architectures aggregating raw `SETTLED` ledger properties into decoupled batch ACH/Wire pipeline outputs seamlessly.

## 🛠️ Tech Stack
- **Java 21** & **Spring Boot 3** 
- **Database**: H2 In-Memory (Configured simulating relational Spanner schemas)
- **Event Mesh**: `spring-kafka` event abstraction models. 
- **Resilience**: `Resilience4j` implementations shielding internal JVM components locally.
- **Build Core**: Apache Maven (`mvn clean install`)

## 🚦 Quick Start

Spin up the entire internal Tomcat ecosystem listening immediately on port `8080`:
```bash
mvn clean spring-boot:run
```

Once running successfully, trigger a mocked transactional pipeline test using standard `curl`:

```bash
curl -X POST http://localhost:8080/v1/payments \
     -H "Content-Type: application/json" \
     -d '{
           "customerId": "cust_12345",
           "amount": 10000,
           "currency": "USD",
           "idempotencyKey": "trans_abc_123",
           "paymentMethod": {
               "type": "card",
               "token": "tok_mastercard"
           }
         }'
```

The underlying mathematical state engines will autonomously process the payload and natively return an `HTTP 201 Created` string representing the final routed Payment states. 

If you attempt the exact `curl` structure above back-to-back, the Idempotency Layer will intercept it safely without double charging!

### 📚 Further Design Documentation
To read a deep dive into the overarching distributed systems concepts dictating this implementation, please read the internal diagram mapping at [SYSTEM_DESIGN.md](./SYSTEM_DESIGN.md)!
