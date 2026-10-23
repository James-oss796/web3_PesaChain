# PesaChain decisions and open policies

This is the shared decision record for the PesaChain repository. Recommendations remain pending
until approved. The SRS governs academic requirements; later user-approved decisions clarify areas
the SRS leaves open. Technical guides apply where consistent with both.

## Approved

- **Application submission idempotency:** A borrower application POST requires a UUID
  `Idempotency-Key`. A borrower-scoped database uniqueness constraint makes concurrent and repeated
  requests safe. An exact retry returns the original application; the same key with different request
  values is rejected with HTTP 409. A subsequent intentional application uses a fresh key.

- **Scope and requirement authority:** The SRS is authoritative for academic requirements and scope.
  Approved user decisions clarify, but do not silently override, explicit SRS requirements.
- **Application stack:** React/Vite frontend, Spring Boot backend, PostgreSQL operational database,
  database migrations, and a minimal access-controlled Solidity contract integrated by the backend.
- **Backend compatibility baseline:** Spring Boot 3.5.16 with Java 21 bytecode/release target and
  Maven 3.9.13 wrapper. The installed Java 25 runtime is within Spring Boot 3.5's documented
  supported range; the Java 21 target follows the repository execution plan.
- **Authentication implementation:** Use Spring Security server-side HTTP sessions rather than
  browser-held bearer tokens; a 30-minute idle timeout, HttpOnly/SameSite=Lax session cookie,
  explicit logout invalidation, and CSRF cookie/header protection suit this browser-based local
  prototype. Set `PESACHAIN_COOKIE_SECURE=true` when serving over HTTPS. Hash passwords with BCrypt
  strength 12; require at least 12 characters and limit UTF-8 input to BCrypt's 72-byte boundary.
- **Administrator activity monitoring:** The SRS's requirement to monitor relevant system and loan
  activity is implemented as a read-only, paginated view over the existing audit table. Return only
  action, actor role, opaque target type/ID, result, and timestamp; omit actor IDs and JSON metadata
  to avoid exposing internal details. Administrator access does not grant Loan Officer permissions.
- **Frontend visual direction:** For the UI polish work, Palette B (Emerald Ink `#064E3B` and
  Champagne `#F8E7C9`) is used as the single brand palette because it matches the supplied
  green/gold PesaChain mark and keeps dense financial screens readable.
- **Application submission fields:** Follow the SRS/API minimum of positive requested amount and
  purpose and requested term in months. Store KES amounts as `NUMERIC(30,2)` (a technical precision
  boundary, not a lending cap) and purpose text off-chain with a 1000-character validation limit.
- **Requested and approved term:** Borrowers submit a requested month term; a Loan Officer may
  approve it or adjust it at approval. Persist requested and approved terms separately. Approved
  term and approved flat rate govern interest and total repayable.
- **Zero interest:** A 0% flat rate is valid. Interest is calculated against original principal for
  the full approved term and rounded to two decimal places using half-up rounding.
- **Local database version:** PostgreSQL 17.11 via the Docker Official Image, pinned to the Alpine
  3.24 tag. It is a supported major version and its patch release is fixed for reproducible local
  setup; update to a current supported minor through a deliberate version change.
- **Local PostgreSQL setup:** Run PostgreSQL with the repository's Docker Compose service, bind the
  host port to loopback, and persist data in its named volume. Keep the generated local password in
  ignored `.env`; keep `.env.example` limited to variable names.
- **Operational and chain responsibilities:** PostgreSQL is the operational source of truth.
  Blockchain records selected evidence; chain failure must not erase a successful operational
  action. Keep operational and blockchain status separate.
- **Blockchain event scope:** Follow the SRS selected-event set: loan approval, rejection,
  disbursement, repayment, and completion. The contract stores only an opaque aggregate key, event
  type, UTC occurrence time, recorder, and the approved disbursement/verified repayment amount in
  minor units; approval/rejection/completion events carry zero amount. No application or identity
  details are published.
- **Local EVM and confirmation:** Use the pinned Hardhat 3 local EVM, chain ID 31337, with a
  configurable confirmation count defaulting to one for this academic local environment. This is
  not a production finality guarantee and does not authorize changing any existing deployment.
- **Blockchain delivery:** Write stable event UUIDs to an additive PostgreSQL transactional outbox
  inside the business-operation transaction. A separately scheduled backend worker claims events
  using leases and `FOR UPDATE SKIP LOCKED`, submits with the backend-only signer, checks receipt
  evidence, and retries bounded transient failures. `RECORDED` requires the configured confirmation
  count. Ambiguous submissions are looked up by stable event ID before retry. Reconciliation records
  findings and never rewrites authoritative loan state.
- **Loan interest:** Flat interest is calculated on original principal for the entire agreed loan
  term, at the rate fixed when approved. Do not add compounding, reducing-balance interest,
  penalties, or other interest models.
- **On-chain amounts and privacy:** Record disbursement amounts and verified repayment amounts
  on-chain. Keep personal data, application details, schedules, free text, credentials, secrets,
  and detailed operational records off-chain. Use opaque identifiers/commitments for linkage.
- **Payments:** Payments are simulated for the academic prototype. Do not represent a blockchain
  transaction as proof that real-world money moved. Define and implement simulated-payment
  verification before a repayment is treated as verified.
- **Monetary JSON wire format:** Use decimal strings for all monetary and percentage values in API
  requests and responses (`"12500.00"`, `"10.00"`). This follows the draft API's precision-preserving
  convention and avoids JSON/JavaScript binary floating-point loss. Keep `BigDecimal` internally;
  retain exact parsing of numeric JSON tokens for request compatibility during transition, but
  clients should send strings and responses always use strings. Amounts use two fractional digits;
  approved rate values retain their supported scale. This was settled before frontend integration.
- **API draft reconciliation:** The API specification draft describes repayments as already-received
  out-of-system payments and says not to initiate payment; it also lists principal-only repayment as
  a provisional assumption. The SRS excludes real-money transfers but requires repayment recording.
  The later approved local simulation flow creates only an in-system signed simulation event, never
  initiates a real transfer, and requires an officer to verify and record it. The approved flat
  interest policy means the operational outstanding balance is the already-calculated contractual
  total (approved principal plus approved flat interest), not principal-only. The API draft's
  provisional repayment assumptions therefore do not govern this implementation. The approved
  simulated-repayment endpoints and flow are documented in `docs/LOAN_DECISIONS_AND_DISBURSEMENT.md`.
- **Simulated repayment verification:** Borrowers initiate clearly labeled local simulations; the
  backend generates, signs, and verifies unique payment events; a Loan Officer verifies an event
  and records the official repayment. Reject duplicate event use and duplicate repayment recording.
  A simulation is not a real-money transfer.
- **Loan terms:** Use KES and month-based terms. Calculate flat interest on original principal for
  the entire approved term and round monetary results to two decimal places using half-up rounding.
  Do not add fees, penalties, compounding, reducing-balance interest, or another interest model.
  Require positive principal and numeric validation; do not invent a business loan cap.
- **Disbursement:** Permit one full disbursement equal to approved principal; no partial
  disbursement or tranches.
- **Borrower data:** Registration requires name, email, phone, and password. National ID and
  residential address are not required. Collect only loan-process information needed for
  applications.
- **Loan decisions:** Record officer, timestamp, and rationale for every decision. Require a
  borrower-readable rejection reason. Keep notes off-chain. A Loan Officer may adjust the requested
  principal and/or term when approving; preserve original requested amount and term separately.
  Approved principal, term, and flat rate are authoritative for loan calculations.
- **Loan lifecycle:** Persist and display `DISBURSED` as a distinct status; do not skip lifecycle
  states.
- **Blockchain event statuses:** Use `PENDING`, `PROCESSING`, `SUBMITTED`, `RECORDED`, and `FAILED`.
  `RECORDED` means the required confirmation condition has been met. Keep chain status separate
  from operational loan status.
- **Role matrix:** Borrowers access only their own records. Loan Officers decide applications,
  disburse, and record official repayments. Administrators manage accounts and monitor the system,
  without inherited Loan Officer permissions.
- **Signing and wallets:** Follow the documented backend-managed signer. Do not introduce borrower
  wallets or direct browser-to-RPC/contract access without resolving a documented conflict and
  receiving explicit approval.
- **MetaMask:** No user-wallet role is approved. Omit MetaMask while the backend-only signer remains
  the documented transaction writer.
- **Frontend:** Evolve the existing React/Vite application and preserve supplied design assets,
  wireframes, diagrams, and useful visual decisions. Remove fictional financial/person records from
  normal workflows; use persisted data or honest empty states.
- **Roles:** Borrower, Loan Officer, and Administrator are distinct roles. Only authorized Loan
  Officers decide applications, disburse loans, and record official repayments. Administrators do
  not automatically inherit Loan Officer permissions.
- **Local services:** Use only local/test chain environments and simulated payments. No real-money
  transfer integration, M-Pesa, cryptocurrency product, tokens, DeFi, automated credit scoring, or
  smart-contract lending decisions.
- **Staff provisioning:** Public registration assigns BORROWER only. Administrators provision
  Administrator and Loan Officer accounts; newly provisioned staff must rotate their temporary
  password. Privileged creation, role changes, deactivation/reactivation, and password rotation are
  audited. No default administrator credentials are supplied; initial bootstrap requires explicit
  process environment credentials.
- **Simulated event signature and replay handling:** Implemented backend HMAC-SHA-256 using the
  environment secret `PESACHAIN_SIMULATION_HMAC_SECRET` (minimum 32 UTF-8 bytes). Sign the event ID,
  loan ID, borrower ID, fixed two-decimal amount, and UTC creation timestamp; verify with a
  constant-time comparison before officer verification and recording. Do not expose the secret to
  the frontend. Use a borrower-scoped UUID `Idempotency-Key` to make retries of the same simulation
  request return the original event; conflicting reuse returns a conflict. Event status transitions
  `PENDING -> VERIFIED -> RECORDED` and a unique official-repayment event reference prevent replay.
  Simulated events do not expire in this academic local workflow; their single-use status is the
  replay control. The local secret must be configured for simulation to be available.

## Recommended, awaiting approval
- **Academic demo:** Add a clearly labeled, opt-in demo seed environment only if needed for a
  repeatable demonstration. Normal workflows remain empty when no persisted records exist.
- **Responsive verification:** Use the guide's 360x800 mobile, 768x1024 tablet, and 1440x900 desktop
  sizes, with a current Chromium-based browser, unless supervisor requirements specify otherwise.

## Unresolved

- Flat-rate percentage bounds and required term limits. Enforce
  positive principal and numeric validation; do not invent a business cap. 0% is explicitly valid.
- Any supervisor rubric, deadline, mandatory deployment host, or required browser/device matrix;
  none was found in the supplied materials.

## Rejected

- Real payment transfers or claims that simulated payments are real.
- Borrower wallets, cryptocurrency payments, tokens, NFTs, DeFi, M-Pesa/bank integration, or
  unsupported financial products.
- PII, free text, credentials, secrets, or internal diagnostics on-chain.
- Automated credit scoring or contract-based loan approval.
- Fabricated records or dashboard metrics in normal workflows.
- Direct browser-to-RPC or browser-to-contract operations without an approved architecture change.

## Follow-up clarification — theme and Officer navigation

- The previously introduced dark theme is deferred and has been removed from the current application at the user's request. The approved Emerald Ink and Champagne palette remains.
- The public page retains its existing content structure. Its light neutral fixed backdrop now contrasts with opaque scrolling surfaces; the navigation is detached and sticky in flow, hides during active scrolling, and reappears after scroll input settles.
- The existing Officer application detail and disbursement API routes remain unchanged. The approved-application page now navigates to loan servicing. A reported Officer 403 requires the authenticated response/account status to distinguish role, suspension/password rotation, session, or CSRF causes; server authorization must remain authoritative.

## Approved clarification — servicing visibility and scope (2026-10-22)

- Keep automated credit scoring and automated approval excluded, consistent with the SRS. Officer
  views may present factual repayment history and amount-based progress; lending decisions remain
  human decisions. Do not label amount progress as on-time repayment performance.
- Do not introduce repayment installments, due dates, overdue status, or due-date reminders until a
  schedule policy is specified and approved. The current backend has no schedule or due-date model.
- Preserve the current Administrator boundary: staff account management, audit activity, and system
  monitoring. Do not add a borrower directory or broader borrower-record visibility without an
  approved privacy and authorization scope.
- Registration validates name, email format, phone format, and the 12-character password minimum
  in the browser before submission. Email duplication is determined by the backend on submit. Phone
  uniqueness is not a current backend rule or database constraint; do not claim a duplicate phone
  check or add a uniqueness constraint without approval and existing-data review.

## Approved clarification — operational reports and role-scoped search (2026-10-23)

- Administrator portfolio reporting may show aggregated application, loan, repayment, audit, and
  blockchain-delivery metrics. It must not expose borrower directories, identities, decision notes,
  or other personal data and must not infer on-time/overdue performance without an approved schedule.
- Search is limited to task-focused queues: Loan Officers can prefix-search pending applications by
  borrower name; Administrators can filter the privacy-minimized audit history by action/target,
  actor role, and result. Search remains paginated, bounded, and backend-authorized.
- Operational report downloads and repayment PDF copies must use persisted API data. Branded vector
  PDF output may include system identity and supplied contact details, but is not called immutable or
  digitally signed without a signing certificate and verifier. Simulated repayment records must
  plainly state that no money moved.
