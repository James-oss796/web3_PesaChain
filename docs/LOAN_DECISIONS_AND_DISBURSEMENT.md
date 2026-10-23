# Loan decisions and disbursement API

All endpoints use the existing server-side session and CSRF protections. Amounts are KES decimal
values; do not send JSON floating-point values from clients when exact decimal serialization is
available.

## Borrower application

`POST /api/v1/applications` accepts a positive requested amount, purpose (up to 1000 characters),
and positive `requestedTermMonths`. The original requested amount and term remain on the application.
Every submission includes a UUID `Idempotency-Key` header. A retry with the same borrower, key, and
request values returns the original application and does not add a second audit entry. Reusing that
key with different request values returns HTTP 409. A new intentional application uses a new key.

## Officer review and decision

- `GET /api/v1/officer/applications?page=0&size=20` lists pending applications.
- `GET /api/v1/officer/applications/{applicationId}` reads an application for review.
- `POST /api/v1/officer/applications/{applicationId}/decision` records one decision.
- `GET /api/v1/officer/borrowers/{borrowerId}/history` returns a read-only summary of prior
  applications, loans, amount-based repayment progress, official repayment history, lifecycle
  entries, and associated blockchain delivery status. It is available to an enabled Loan Officer
  after any required password rotation; borrowers and Administrators are denied. It omits borrower
  contact details, application free text, and decision notes. Amount progress is descriptive and is
  not a credit score, delinquency rate, or lending recommendation.

Approval body (decimal values are JSON strings):

```json
{
  "decision": "APPROVE",
  "approvedPrincipal": "12000.00",
  "approvedTermMonths": 6,
  "flatRatePercent": "10.00",
  "rationale": "Approved at an affordable amount"
}
```

The approved principal may equal or differ from the request; the requested values are not
overwritten. Approved terms and calculation results are stored on the application and copied into a
new loan record in the same PostgreSQL transaction. Flat interest is
`approvedPrincipal * flatRatePercent / 100`, rounded to two decimal places using half-up rounding;
total repayable is principal plus that interest. A zero rate is valid. No fees or other interest
models are applied.

Rejection body:

```json
{
  "decision": "REJECT",
  "rationale": "The current assessment does not support approval",
  "rejectionReason": "Please provide a clearer repayment plan before reapplying."
}
```

Rationale is required for both decisions; rejection reason is required for rejection. Only a Loan
Officer may decide a pending application. Repeated decisions return `409 Conflict`.

## Full disbursement

- `GET /api/v1/loans/mine` lists the authenticated borrower's persisted loans.
- `GET /api/v1/loans/{loanId}` returns a loan only to its borrower.
- `POST /api/v1/officer/loans/{loanId}/disbursement` accepts `{ "amount": "12000.00" }` and records
  one full disbursement by an authorized Loan Officer. Partial amounts and duplicate disbursements
  are rejected.

The disbursement is an operational academic record; this API does not transfer money. On successful
disbursement the loan enters ACTIVE servicing immediately. The persisted status history includes
both `APPROVED -> DISBURSED` and `DISBURSED -> ACTIVE` transitions, so DISBURSED is visible in the
loan response. Repayment recording is implemented separately from blockchain evidence publication;
the database outbox records official repayment/completion events after their operational transaction
commits. Blockchain publication may remain pending or failed while the official loan state remains
successful.

## Simulated repayments

Repayments in this prototype are payment simulations, not transfers. The backend requires
`PESACHAIN_SIMULATION_HMAC_SECRET` to contain at least 32 UTF-8 bytes before it enables the
simulation/verification commands. Keep this key in the local ignored environment file or a secret
manager; never put it in browser code or commit it. The event signature is an HMAC-SHA-256 over the
event UUID, loan UUID, borrower UUID, two-decimal amount, and UTC creation timestamp. The officer
verification endpoint checks this signature using constant-time comparison.

Borrowers submit a positive KES amount against their own `ACTIVE` loan, with a UUID
`Idempotency-Key` header. Repeating the same key and same loan/amount returns the original event;
reusing it with different values conflicts. The backend returns an event in `PENDING` status and
does not change the outstanding balance. Officers can review pending events, verify one event, then
record it as a separate official repayment. Each event can be verified and recorded only once. The
official repayment stores the amount applied and resulting balance, while the event stores the
simulation and verification evidence.

Routes:

- `POST /api/v1/loans/{loanId}/simulated-repayments` — borrower initiation; JSON `{ "amount": "25.00" }`
  and `Idempotency-Key: <UUID>`.
- `GET /api/v1/loans/{loanId}/simulated-repayments` — the borrower's own simulated events.
- `GET /api/v1/loans/{loanId}/repayments` — the borrower's own official repayment history.
- `GET /api/v1/officer/repayment-events?status=PENDING&page=0&size=20` — officer review queue
  (`PENDING` or `VERIFIED`).
- `GET /api/v1/officer/repayment-events/{eventId}` — officer event detail.
- `POST /api/v1/officer/repayment-events/{eventId}/verify` — verifies one pending signed event.
- `POST /api/v1/officer/repayment-events/{eventId}/record` — records one verified event as an
  official repayment.

Repayment recording locks the event and loan rows, rechecks the active status and current balance,
and atomically inserts the official repayment, marks the event `RECORDED`, updates the outstanding
balance, adds an audit entry, and adds `ACTIVE -> COMPLETED` history only when the resulting balance
is exactly KES 0.00. A simulation exceeding the initial balance is rejected; an event that becomes
too large after another repayment is rejected at recording time. Database uniqueness and row locks
protect against event replay and concurrent overpayment.
