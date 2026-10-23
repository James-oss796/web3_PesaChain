# Testing, Playwright, and security guide

## 1. Test pyramid

### Backend unit tests

Test domain behavior without Spring where possible:

- every legal and illegal state transition;
- balance arithmetic with decimal-safe money;
- completion at exactly zero;
- no overpayment;
- decision made once;
- ID and opaque-key generation;
- retry/backoff classification;
- safe error mapping.

### Backend integration tests

Use real PostgreSQL through Testcontainers:

- Flyway migration from empty database;
- unique and check constraints;
- repository locking/concurrency;
- authentication and role matrix;
- borrower ownership/IDOR;
- idempotent commands;
- transactional outbox written with operational change;
- audit records and PII redaction.

Do not substitute H2 for PostgreSQL behavior.

### Contract tests

Run all tests specified in `02-BLOCKCHAIN.md`, including authorization, duplicate prevention,
event payload, and invariants.

### Adapter/system integration tests

- local-chain happy path;
- RPC unavailable before submit;
- submit timeout with uncertain outcome;
- revert/failed receipt;
- receipt pending then confirmed;
- duplicate retry reconciliation;
- history read unavailable;
- no PII in calldata, events, logs, or API output.

## 2. Playwright setup

Add Playwright only during the implementation phase and pin its version. Put tests in `e2e/` with:

```text
e2e/
  fixtures/             role sessions, seeded IDs, API helpers
  borrower.spec.ts
  officer.spec.ts
  admin.spec.ts
  blockchain-failure.spec.ts
  responsive.spec.ts
  accessibility.spec.ts
```

Use stable accessible locators:

1. `getByRole`
2. `getByLabel`
3. `getByText` for stable user copy
4. `data-testid` only for otherwise ambiguous dynamic structures

Never use brittle generated CSS selectors or arbitrary sleeps. Wait on visible outcomes and API
responses. Seed known fictional data for each test and isolate tests from one another.

## 3. Required Playwright journeys

### Borrower

1. Register with valid data and see success.
2. Validate duplicate/invalid registration safely.
3. Log in.
4. View/update permitted profile data.
5. Submit amount and purpose, review, confirm, and receive `PENDING`.
6. View own application history/detail.
7. View active loan, outstanding balance, repayments, and event history.
8. Confirm another borrower's object is not accessible.

### Loan Officer

1. Log in and open pending queue.
2. Review and approve exactly one application.
3. See operational `APPROVED` with chain `PENDING`, then `RECORDED`.
4. Reject another application and verify only that application is rejected.
5. Disburse an approved loan.
6. Record partial repayment and verify new balance.
7. Attempt overpayment and see validation without data change.
8. Record final repayment and see `COMPLETED`.

### Administrator

1. Log in and manage account status/role.
2. Verify no loan-decision controls are present or authorized.
3. Review sanitized system activity.
4. Inspect recorded, pending, and failed event statuses.
5. Retry only if retry permission is part of the documented policy.

### Failure journeys

1. Approve while RPC is unavailable.
2. Verify UI says loan approved and recording failed/pending.
3. Verify ordinary loan reads still work.
4. Restore RPC and trigger/wait for reconciliation.
5. Verify one logical event becomes `RECORDED`.
6. Disable verification reads and verify “unavailable,” not “no history.”

## 4. Responsive and visual checks

Run critical screens at representative widths:

- mobile: 360 × 800;
- tablet: 768 × 1024;
- desktop: 1440 × 900.

Check dashboard, application form, application detail, loan detail, tables/cards, modal, and event
history. Use screenshots for regression evidence, not as a substitute for behavioral assertions.
Mask timestamps or generated IDs where visual snapshots would be unstable.

## 5. Accessibility checks

Use automated checks where available, then manually test:

- tab order and skip/navigation behavior;
- focus trap and return focus in dialogs;
- labels and described errors;
- headings and landmarks;
- status announcements;
- no color-only status;
- reduced motion;
- zoom and mobile reflow.

Automated accessibility tools do not replace keyboard and screen-reader-oriented review.

## 6. Security verification

Test at minimum:

- password hashing and generic login failures;
- suspended-account behavior;
- role escalation and admin self-lockout policy;
- IDOR across every detail/history endpoint;
- CSRF if cookie sessions are used;
- token expiry/revocation if tokens are used;
- request size, type, numeric, date, and enum validation;
- SQL injection resistance;
- XSS-safe rendering of purpose and notes;
- rate limiting for authentication and sensitive commands;
- secrets absent from repository/frontend bundle;
- logs, audit metadata, API errors, and chain payloads contain no unnecessary PII;
- CORS and security headers for deployed environments;
- dependency and container scans.

## 7. Standard verification commands

Claude must use commands actually defined by the repository after setup. Recommended targets:

```bash
# Frontend
pnpm format
pnpm build
pnpm test
pnpm test:e2e

# Backend
cd backend
./mvnw verify

# Contracts — use the chosen toolchain, not both
cd contracts
pnpm test
# or: forge test

# Full local smoke test
docker compose up -d --build
docker compose ps
```

If scripts differ, update this document and use the real scripts. Never report a command as passing
unless it was run successfully.
