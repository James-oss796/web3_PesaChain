# PesaChain frontend integration map

Reviewed: 2026-10-26

## Existing frontend

`src/App.tsx` mounts `src/app/IntegratedApp.tsx`, a session-aware state-driven frontend (without
URL routing). `IntegratedApp.tsx` contains the shared fields, buttons, status badges, panels, and
role-aware shells for borrowers, Loan Officers, and Administrators. The integrated application uses
the backend API for its business records; the old prototype's fabricated people, loans, payments,
statistics, dates, and public role switch are not used in normal workflows. The supplied logo and 31
SVG wireframes (`PesaChain_Figma_Import_SVG/screens/00-design-system.svg` through
`30-loan-completion-success.svg`) remain preserved design references/assets. Password recovery is
not offered because the backend has no recovery API.

The integrated application currently has these page groups:

- Public: landing, login, borrower registration.
- Borrower: dashboard, profile, application form/list/detail, loan detail, repayment simulation and
  history, and own-loan blockchain status.
- Loan Officer: dashboard, application queue/detail/decision, loan portfolio/full disbursement,
  repayment-event verification/recording, and blockchain status.
- Administrator: dashboard, staff-account provisioning/management, privacy-minimized activity log,
  and blockchain event/reconciliation monitor.

## Actual API and page mapping

The backend API base is `/api/v1`. Identifiers are UUIDs. Monetary DTO fields are `BigDecimal`,
but `ExactDecimalJsonConfiguration` serializes monetary values as decimal strings and parses
decimal-string requests precisely (also accepts numeric JSON tokens for transition compatibility).
Frontend money values must remain strings/decimal-safe until formatted; do not coerce to JS `number`
for calculations.

| Workflow/page | Actual endpoints | Access and notes |
|---|---|---|
| Session bootstrap / current user | `GET /auth/csrf`, `GET /auth/me` | CSRF is public; `/me` authenticated. Account response fields: `id,name,email,phone,role,passwordChangeRequired`. |
| Borrower registration | `POST /auth/register` | Public; body `{fullName,email,phone,password}`; creates and logs in a BORROWER. |
| Login/logout | `POST /auth/login`, `POST /auth/logout` | Login uses email/password. Logout returns 204 and requires CSRF. |
| Required password rotation | `POST /auth/change-password` | Authenticated staff with `passwordChangeRequired`; CSRF required. API role gate permits only the change-password route while role authority is restricted. |
| Borrower profile | `GET/PATCH /borrowers/me/profile` | BORROWER; PATCH `{fullName,phone}`. Email is not editable. |
| Borrower applications | `POST /applications`, `GET /applications/mine?page&size`, `GET /applications/{uuid}` | BORROWER, ownership checked. Submit body `{requestedAmount,purpose,requestedTermMonths}` and a required UUID `Idempotency-Key` header. Repeating the same key and payload returns the original application; reusing it with changed details returns 409. |
| Loan Officer application work | `GET /officer/applications?page&size`, `GET /officer/applications/{uuid}`, `POST /officer/applications/{uuid}/decision` | LOAN_OFFICER. Decision body includes decision, approved principal/term/rate, rationale, rejection reason as appropriate. |
| Borrower loans | `GET /loans/mine?page&size`, `GET /loans/{uuid}` | BORROWER, ownership checked; includes lifecycle history and financial terms. |
| Full disbursement | `POST /officer/loans/{uuid}/disbursement` | LOAN_OFFICER; amount must equal approved principal. |
| Loan Officer loan portfolio | `GET /loans?page&size` | LOAN_OFFICER only, matching the implementation guide's documented `GET /loans`; borrower `/loans/mine` and `/loans/{uuid}` remain borrower-only. |
| Borrower repayment simulation/history | `POST/GET /loans/{uuid}/simulated-repayments`, `GET /loans/{uuid}/repayments` | BORROWER and ownership checked. Initiation requires UUID `Idempotency-Key`; event initiation does not reduce balance. |
| Loan Officer repayment queue | `GET /officer/repayment-events?status=PENDING|VERIFIED&page&size`, `GET /officer/repayment-events/{uuid}`, `POST .../{uuid}/verify`, `POST .../{uuid}/record` | LOAN_OFFICER only. Official repayment is recorded after verification. |
| Blockchain status | `GET /loans/{uuid}/blockchain-events`, `GET /officer/blockchain-events`, `GET /admin/blockchain-events?status&page&size` | Borrower events restricted to own loan; officer and admin endpoints role-specific. Statuses `PENDING`, `PROCESSING`, `SUBMITTED`, `RECORDED`, `FAILED`; only `RECORDED` proves configured confirmation was met. |
| Administrator blockchain health and retry | `GET /admin/blockchain-health`, `POST /admin/blockchain-events/{uuid}/retry` | ADMINISTRATOR only. Health checks RPC, expected chain, contract interface, signer authorization, block height, and outbox counts. Retry requires a healthy check, only queues a failed event, records an audit action, and does not alter the operational loan/repayment. |
| Administrator reconciliation | `GET /admin/blockchain-reconciliation/findings?limit`, `POST /admin/blockchain-reconciliation/run` | ADMINISTRATOR; monitoring only. No officer rights implied. |
| Staff account management | `GET/POST /admin/users`, `PATCH /admin/users/{uuid}` | ADMINISTRATOR. List currently includes staff accounts only; create accepts staff email/password/role. |
| Officer application search | `GET /officer/applications?page&size&q` | LOAN_OFFICER; optional case-insensitive borrower-name prefix (2–80 characters), bounded page size. The UI debounces typing and the database has a functional prefix index. |
| Administrator activity filters | `GET /admin/activity?page&size&q&actorRole&result` | ADMINISTRATOR; `q` matches action/target-type prefix or exact target UUID; optional role/result filters; response remains privacy-minimized. |
| Administrator operational report | `GET /admin/reports/overview?months=6` | ADMINISTRATOR; months range 3–12. Returns aggregate application, loan, repayment, audit, blockchain-outbox counts/amounts and monthly trends; no borrower identifiers or personal fields. |

## Authentication, CSRF, and frontend configuration

Authentication uses Spring Security HTTP sessions, not JWTs. Session cookie is HttpOnly,
SameSite=Lax, 30-minute idle timeout; HTTPS environments set Secure. Cookie-backed mutation requests
must include the CSRF header name and token returned by `GET /api/v1/auth/csrf`; Spring also writes
the CSRF cookie. Frontend requests must use `credentials: "include"`. Registration/login create a
session, `/auth/me` restores the current session, and suspended users are invalidated by the backend
filter. Handle 401 by clearing client session state and returning to login; handle 403 distinctly.
Temporary staff credentials require the password-change screen before role workflows.

The local PostgreSQL Compose service is healthy on loopback port 5432 and retains its named volume.
The React/Vite dev server is configured for port 8443 and already proxies `/api` to the backend on
port 8080 (override with `PESACHAIN_API_PROXY_TARGET`). The backend on port 8080 and local Hardhat
node on 8545 were listening during this review; neither service was restarted. Same-origin proxying
carries the session and CSRF cookies without permissive cross-origin configuration. Production
hosting must route `/api` to the backend or supply a same-site API base; do not put secrets in
frontend environment variables.

## Discrepancies and backend gaps

1. The API DOCX has been updated with an implementation contract that supersedes stale conceptual
   examples. It now documents server-side sessions, CSRF, `ADMINISTRATOR`, actual registration and
   session payloads, decision/disbursement routes, persistent `DISBURSED`, and the admin activity
   response. Decimal-string money examples remain consistent with `BigDecimal` parsing.
2. The SRS requires administrators to monitor relevant system and loan activities. Existing audit
   records support a read-only `/admin/activity` endpoint without a schema migration. It exposes
   action, actor role, target type/opaque target ID, result, and timestamp; it excludes actor IDs,
   metadata, borrower contact details, decision notes, and internal diagnostics. Backend role
   authorization protects the endpoint; the frontend renders actual audit records and honest empty
   states.
3. The backend now implements the implementation guide's `GET /loans` Loan Officer portfolio route.
   It returns paginated loan lifecycle/financial data without broadening borrower profile access.
   There is no separate officer loan detail endpoint; the portfolio response provides the loan
   fields needed for full disbursement. Loan decisions and repayments remain
   restricted to the documented Loan Officer actions.
4. Administrator `/admin/users` currently lists only staff accounts and has no `GET /admin/users/{id}`;
   the prototype suggests all-user browse/details, which are unsupported. Do not expose borrower PII
   by broadening the existing endpoint without an approved access contract.
5. There is no password recovery endpoint, so no recovery flow is presented. No separate “last
   activity” endpoint exists; the activity view displays the actual audit history instead.
6. For integration verification, a separate API instance was started on port 8082 against a newly
   created database inside the disposable `pesachain-ui-test-postgres` container. The existing
   Compose database and its volume were not used by that instance. Browser automation is unavailable
   in the current Work settings; HTTP journeys were exercised directly instead.

The remaining gaps are explicit, not frontend-only issues. UI actions requiring missing endpoints
will not be simulated. They should be exposed as unavailable or omitted until the backend/API
contract is extended under the existing SRS and role matrix.

## Role access map

- **Borrower:** own account/profile, applications, loans, repayments, simulated events, and own-loan
  blockchain evidence. No officer queue, decision, verification, recording, staff, or global event data.
- **Loan Officer:** application queue/detail/decision, full disbursement, repayment event review,
  verification and official recording, officer event monitor. No borrower self-service role change.
- **Administrator:** staff account provisioning/status/role, privacy-minimized system activity,
  blockchain events and reconciliation. No loan decision, disbursement, or official repayment
  actions. Current account APIs do not support general borrower PII management.

Every permission must continue to be enforced server-side. Frontend guards are presentation only.

## Integration coverage

- The typed API/session client uses cookie credentials, CSRF bootstrap/refresh, safe problem mapping,
  and the existing same-origin Vite proxy. Public role switching is replaced with register/login,
  session restoration/logout, and required password rotation.
- Borrower profile, application, loan, repayment, and own-loan blockchain status views use backend
  records and provide loading, empty, validation, service error, and session-expired feedback.
- Loan Officer application review/decision, loan portfolio/full disbursement, repayment verification
  and recording, event monitoring, and the privacy-minimized factual borrower history endpoint use
  the role-protected API. Historical payment progress is descriptive only; the Officer makes the
  lending decision.
- Administrator staff management, privacy-minimized activity, and blockchain reconciliation use
  Administrator-only endpoints; no Loan Officer permissions are implied.
- Administrator reporting uses server-side aggregates from operational tables and blockchain outbox
  state; it does not infer schedules, overdue rates, creditworthiness, or real-money transfers.
- Officer application search is server-side, prefix-only, paginated, and debounced by the UI. Audit
  search is likewise paginated and limited to action/target prefixes or an exact opaque target ID.
- Official repayment PDF exports contain only the authorized repayment record and a simulated-payment
  disclosure. Branded PDFs use vector artwork and formatted text, but are not cryptographically signed
  or impossible to alter; official verification remains in the authenticated application and its
  persisted audit/evidence. Report and receipt PDFs include the project contact details supplied by
  the owner.
- Isolated HTTP journeys have been exercised against disposable PostgreSQL. Browser E2E and rendered
  visual, responsive, and accessibility review remain pending because local browser access is disabled
  in the current Work settings.

## Baseline

- `corepack pnpm@10.34.3 exec tsc --noEmit`: passed after integration and visual updates.
- `corepack pnpm@10.34.3 run build`: passed after integration and visual updates.
- `backend/./mvnw.cmd verify -q`: latest run passed, 42 tests, 0 failures, 0 errors, 1 skipped; details are in
  `docs/IMPLEMENTATION_STATUS.md`.
- PostgreSQL Compose service: running and healthy; existing persistent volume retained.
- Backend API health at port 8080 returned 200 during the 2026-10-22 product quality pass. Port 8081
  was not part of this pass.
- Git metadata: absent per existing implementation status; no initialization performed.

## Public site and install support (2026-10-22)

The public landing page is rendered by `LandingPage` in `src/app/IntegratedApp.tsx`. Its section
navigation targets `#workflow`, `#roles`, `#evidence`, and `#questions`; account actions use the
existing in-app registration and sign-in state transitions. The lifecycle artwork is explicitly
labeled as an illustrative product preview and contains no user or transaction data. Role descriptions
follow the backend permission split. The page explains that simulations are not real transfers and
that on-chain event evidence is separate from operational loan status.

The root `public/manifest.webmanifest`, logo-derived icons, `public/sw.js`, and
`src/registerServiceWorker.ts` provide production PWA metadata and conservative asset caching. The
service worker is not registered in development, bypasses all `/api/` requests and all non-GET
requests, and does not cache documents or authenticated data. See `docs/PWA_AND_FRONTEND_QA.md` for
the offline and browser-verification boundary.
