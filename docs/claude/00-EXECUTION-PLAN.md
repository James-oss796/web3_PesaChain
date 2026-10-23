# PesaChain execution plan

This is the orchestration guide for implementing the complete system. Complete phases in order
unless an earlier dependency is already present and verified.

## 1. Target architecture

```text
React/Vite frontend
        |
        | HTTPS JSON API
        v
Spring Boot API ─────────────── PostgreSQL
        |                         - operational records
        |                         - audit log
        |                         - blockchain outbox/status
        |
        | JSON-RPC through backend signer only
        v
PesaChainEvents Solidity contract
        - opaque aggregate key
        - event ID
        - event type
        - event timestamp
```

The browser never connects directly to a wallet, RPC node, contract, or database.

## 2. Phase sequence

### Phase 0 — Baseline and decisions

1. Inspect the current frontend, scripts, and Git status.
2. Record Java, Spring Boot, Node, Solidity toolchain, authentication strategy, local chain, and
   package-manager versions in `docs/DECISIONS.md`.
3. Add `docs/IMPLEMENTATION_STATUS.md` with every phase and requirement as a checkbox.
4. Run the existing frontend build before changing architecture.
5. Create `.env.example` with placeholder variable names only.

Exit gate: baseline builds, decisions are explicit, and no secrets are present.

### Phase 1 — Backend foundation

1. Generate `backend/` with Java 21, current stable Spring Boot 3.x, Maven wrapper, and packages by
   feature rather than one global controller/service/repository hierarchy.
2. Add PostgreSQL, Flyway, validation, security, Actuator, OpenAPI, and test dependencies.
3. Add Docker Compose for PostgreSQL.
4. Add migrations for users, profiles, applications, loans, disbursements, repayments,
   blockchain events/outbox, and audit records.
5. Add RFC 7807-style safe error responses and correlation IDs.

Exit gate: backend starts against PostgreSQL, migrations run from empty, health check passes, and
container-backed integration test succeeds.

### Phase 2 — Authentication and RBAC

1. Implement borrower registration.
2. Implement login and authenticated session/token handling using the strategy in
   `docs/DECISIONS.md`.
3. Implement ownership checks and role guards.
4. Implement administrator user/role/status management without Loan Officer inheritance.
5. Add generic invalid-login messages, password hashing, validation, and audit entries.

Exit gate: role matrix and IDOR integration tests pass.

### Phase 3 — Operational loan lifecycle

Implement complete vertical slices in this order:

1. Borrower profile view/update.
2. Application submit/list/detail.
3. Officer pending queue/detail.
4. Approve/reject decision.
5. Disbursement.
6. Repayment and balance calculation.
7. Automatic completion at zero balance.
8. Administrator activity monitoring.

Each command must validate role, ownership, current state, input, idempotency, and audit behavior.

Exit gate: all domain transition and concurrency tests pass without blockchain enabled.

### Phase 4 — Smart contract and adapter

1. Implement and test the minimal event contract.
2. Deploy it to a local development chain.
3. Add backend Web3 adapter configuration.
4. Add transactional outbox worker, receipt confirmation, safe retries, and reconciliation.
5. Add event-history APIs and explicit unavailable states.

Exit gate: business operations continue when RPC is down; successful events can be verified; retry
does not create a duplicate logical event.

### Phase 5 — Frontend integration and design refinement

1. Extract the prototype into reusable routing, layouts, components, services, and feature modules.
2. Replace mock interactions with typed API clients and authenticated route guards.
3. Preserve every role flow and all non-happy-path states.
4. Apply `docs/claude/03-UI-UX.md`.
5. Make the operational/blockchain distinction obvious on every relevant result page.

Exit gate: keyboard-accessible desktop, tablet, and mobile experiences pass component and
Playwright tests.

### Phase 6 — System verification

1. Run backend unit, integration, migration, authorization, and failure tests.
2. Run contract unit and invariant tests.
3. Run frontend build, type checks, lint/format, and component tests.
4. Run Playwright role journeys and failure simulations.
5. Run dependency and secret scans.
6. Produce `docs/DEMO.md`, `docs/API.md`, and `docs/DEPLOYMENT.md`.

Exit gate: a fresh clone can start the full local system by following documented commands.

## 3. Required vertical-slice definition of done

A slice is complete only when it includes:

- database migration or confirmed existing schema;
- domain invariant and transaction boundary;
- repository and service logic;
- authorized API endpoint with validation;
- safe errors and audit event;
- frontend loading, empty, success, validation, forbidden, and server-error behavior;
- unit and integration tests;
- Playwright coverage for user-visible critical paths;
- documentation and status checklist update.

## 4. Decisions Claude must not silently make

Record these before implementation:

- exact Spring Boot and Java versions;
- JWT versus secure server session, expiry, and revocation approach;
- exact local chain and Solidity toolchain;
- blockchain confirmation count;
- retry limits/backoff;
- application decision-note and rejection-reason requirements;
- whether admin may view redacted event details;
- production hosting and secret-management mechanism.

Choose conservative defaults for a local academic prototype, explain them, and continue. Ask the
user only when a decision creates irreversible risk.
