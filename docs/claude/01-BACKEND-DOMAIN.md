# Backend and domain implementation guide

## 1. Recommended modules

Organize `backend/src/main/java/.../pesachain/` by feature:

```text
auth/               registration, login, principal/session
users/              user accounts, role/status administration
borrowers/          borrower profile and ownership
applications/       submission, queue, approval, rejection
loans/              loan state, disbursement, balance
repayments/         repayment commands and history
blockchain/         outbox, adapter, reconciliation, history
audit/              sanitized accountability records
shared/             errors, IDs, clocks, pagination, security helpers
```

Keep controllers thin. Domain/application services own transactions and state transitions.
Repositories do persistence only. Use an injectable `Clock` and deterministic ID generator in
tests.

## 2. Core data model

Use UUID primary keys internally and separate human-readable IDs such as `APP-2026-00124` and
`LN-2026-00481`. Add version columns for optimistic locking where concurrent commands matter.

### `users`

- `id`, `public_id`
- `email` and/or `username`, normalized and unique
- `password_hash`
- `role`: `BORROWER | LOAN_OFFICER | ADMINISTRATOR`
- `status`: `ACTIVE | SUSPENDED`
- timestamps and optimistic-lock version

### `borrower_profiles`

- `user_id` unique foreign key
- full name, phone, national ID, address
- created/updated timestamps

Protect national ID at rest according to the documented prototype decision. Never return it
unmasked outside authorized profile/review responses.

### `loan_applications`

- `id`, `application_number`, `borrower_id`
- positive `requested_amount` as `NUMERIC`, never floating point
- `purpose_category`, `purpose`
- `status`: `PENDING | APPROVED | REJECTED`
- submitted timestamp
- `decision_by`, decision timestamp, optional decision note/reason
- optimistic-lock version

### `loans`

- `id`, `loan_number`
- unique `application_id`, `borrower_id`
- positive principal
- `status`: `APPROVED | DISBURSED | ACTIVE | COMPLETED`
- approved/disbursed/activated/completed timestamps
- outstanding balance, or compute from principal minus valid repayments under one documented rule
- optimistic-lock version

### `disbursements`

- unique `loan_id`
- positive amount, date, institution reference
- `recorded_by`, recorded timestamp

### `repayments`

- `id`, `repayment_number`, `loan_id`
- positive amount, date, institution reference
- `recorded_by`, recorded timestamp
- unique idempotency key

### Blockchain outbox and reconciliation records

The additive V8 migration implements `blockchain_outbox`, `blockchain_event_attempts`, and
`blockchain_reconciliation_findings` rather than a pre-chain `blockchain_events` table. The outbox
stores the stable event UUID, source and aggregate identifiers, opaque on-chain key, event type,
approved amount in minor units, business timestamp, status (`PENDING | PROCESSING | SUBMITTED |
RECORDED | FAILED`), retry/lease fields, and confirmed transaction/block data. Attempt history
retains safe outcome codes. Reconciliation findings retain repeatable missing, mismatched, duplicate,
unexpected, and local/chain mismatch evidence. See `docs/claude/02-BLOCKCHAIN.md` for delivery and
verification semantics.

### `audit_log`

- actor ID and role, action, target type and opaque target ID
- timestamp, result, correlation ID
- sanitized metadata only

## 3. State invariants

### Applications

```text
PENDING -> APPROVED
PENDING -> REJECTED
```

- A decision is legal only from `PENDING`.
- Only a Loan Officer can decide.
- The decision actor and timestamp are mandatory.
- Approval creates exactly one loan with status `APPROVED` in the same database transaction.
- A duplicate or concurrent second decision returns `409 Conflict`, not silent success.
- Rejection applies to the application, not the borrower account.

### Loans

```text
APPROVED -> DISBURSED -> ACTIVE -> COMPLETED
```

- Only an approved loan may be disbursed.
- A loan has at most one disbursement.
- Record the `DISBURSED` milestone durably. If the system activates servicing immediately after
  disbursement, record both transitions in the same command/audit history and return the final
  servicing state explicitly; do not invent a manual activation role action.
- Only an active loan accepts repayments.
- Repayment must be greater than zero and no greater than the locked current outstanding balance.
- A repayment and balance update occur atomically.
- When balance reaches exactly zero, transition to `COMPLETED` in that transaction.
- A completed loan cannot accept another repayment.
- Use row locking or optimistic locking so simultaneous repayments cannot overpay.

## 4. Transaction and event rule

Every critical operational command follows this order:

1. Authorize role and ownership.
2. Load the aggregate with appropriate concurrency control.
3. Validate input and current state.
4. Update PostgreSQL.
5. Write sanitized audit entry.
6. Write the blockchain outbox/event row with `PENDING`.
7. Commit one database transaction.
8. Return operational success plus separate blockchain status.

Never call the blockchain inside the operational database transaction.

Example response:

```json
{
  "operationStatus": "SUCCEEDED",
  "applicationStatus": "APPROVED",
  "loanId": "LN-2026-00481",
  "blockchainStatus": "PENDING",
  "eventId": "EVT-2026-0464"
}
```

If recording later fails, the operation remains successful:

```json
{
  "operationStatus": "SUCCEEDED",
  "loanStatus": "ACTIVE",
  "blockchainStatus": "FAILED",
  "retryAvailable": true
}
```

## 5. API surface

Use versioned routes such as `/api/v1`. Exact payloads belong in OpenAPI.

### Public/authentication

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/logout`
- password recovery only if the chosen delivery mechanism is genuinely implemented; otherwise
  document it as prototype-only rather than pretending email was sent

### Borrower

- `GET/PATCH /borrowers/me/profile`
- `POST /applications`
- `GET /applications/mine`
- `GET /applications/{id}` with ownership enforcement
- `GET /loans/mine`
- `GET /loans/{id}` with ownership enforcement
- `GET /loans/{id}/repayments`
- `GET /loans/{id}/blockchain-events`

### Loan Officer

- `GET /officer/applications`
- `GET /officer/applications/{id}`
- `POST /officer/applications/{id}/approve`
- `POST /officer/applications/{id}/reject`
- `GET /loans`
- `POST /loans/{id}/disbursements`
- `POST /loans/{id}/repayments`

### Administrator

- `GET /admin/users`
- `GET /admin/users/{id}`
- `PATCH /admin/users/{id}`
- `GET /admin/activity`
- `GET /admin/blockchain-events`
- `GET /officer/blockchain-events`
- `GET /admin/blockchain-reconciliation/findings`
- `POST /admin/blockchain-reconciliation/run`
- restricted retry endpoint only if administrator retry is an approved policy

Use pagination with bounded page size for list endpoints. Use allowlisted sort fields.

## 6. Error contract

Return safe problem details with stable codes:

- `VALIDATION_FAILED` — 400
- `AUTHENTICATION_FAILED` — 401 with generic text
- `ACCESS_DENIED` — 403
- `RESOURCE_NOT_FOUND` — 404 without revealing another user's object
- `INVALID_STATE_TRANSITION` — 409
- `DUPLICATE_OPERATION` — 409 or the original idempotent result
- `BLOCKCHAIN_VERIFICATION_UNAVAILABLE` — 503 only for verification requests, never for an already
  completed operational command

Do not expose SQL, stack traces, RPC URLs, private keys, raw node errors, or PII.

## 7. Idempotency and concurrency

- Accept an `Idempotency-Key` for approve, reject, disburse, and repayment commands.
- Persist the key, actor, command type, request fingerprint, and result.
- Same key and same request returns the original result.
- Same key and different request returns conflict.
- Enforce unique loan per approved application, disbursement per loan, transaction hash, logical
  event ID, application number, loan number, and repayment number at the database level.
- Test simultaneous approval, disbursement, and repayment requests.

## 8. Authorization matrix

| Capability | Borrower | Loan Officer | Administrator |
|---|---:|---:|---:|
| Register borrower account | Yes | No | No |
| View/update own profile | Yes | No | No |
| Submit/view own application | Yes | No | No |
| Review/decide applications | No | Yes | No |
| Disburse/record repayment | No | Yes | No |
| View own loan/history | Yes | No | No |
| View operational loan queue | No | Yes | No |
| Manage users/roles | No | No | Yes |
| Monitor system/event status | No | Scoped | Yes |

Hiding a button is not authorization. Every endpoint must enforce this matrix.
