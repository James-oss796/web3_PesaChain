---
name: pesachain-backend
description: Implements or reviews PesaChain Spring Boot, PostgreSQL, authentication, RBAC, loan lifecycle, repayment, audit, and transactional-outbox logic.
---

# PesaChain backend

1. Read `docs/claude/01-BACKEND-DOMAIN.md`.
2. Confirm migrations and state invariants before adding endpoints.
3. Implement commands in this order: authorization, locked aggregate read, validation, operational
   mutation, audit row, outbox row, commit.
4. Keep blockchain/RPC calls outside operational transactions.
5. Return operation and blockchain statuses separately.
6. Enforce role and ownership server-side.
7. Add unit, PostgreSQL integration, concurrency, idempotency, and IDOR tests.
8. Run `./mvnw verify` and report failures accurately.
