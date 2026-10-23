# Blockchain contract and integration guide

## Purpose and boundary

PostgreSQL is authoritative for applications, loan decisions, balances, repayments, and operational
loan state. The backend submits a limited evidence record to the contract after the associated
database transaction commits. An RPC failure leaves the operational operation successful and its
outbox event retryable. No loan or payment decision is made on-chain.

The SRS explicitly selects approval, rejection, disbursement, repayment, and completion events.
Approval and rejection reasons, borrower identity, application content, schedules, and detailed
records remain off-chain. The contract stores a one-way hash of the UUID event identifier, a random
opaque 32-byte aggregate key, event enum, UTC business timestamp, authorized recorder, and an amount
only for a full disbursement or an officially recorded verified repayment. Monetary amounts are
integer KES minor units; approval, rejection, and completion events carry zero.

## Contract

`blockchain/contracts/PesaChainEventLedger.sol` has no payable operations, asset custody, borrower
wallet behavior, update/delete operation, or business decision logic. An owner can administer
recorder addresses; only a recorder may append evidence. Event IDs are unique. Zero identifiers and
timestamps are rejected. The amount must be positive for `LOAN_DISBURSED` and `REPAYMENT_RECORDED`
and zero for the other event types.

Compile, test, typecheck, and export the ABI from `blockchain/`:

```powershell
corepack pnpm@10.34.3 install --frozen-lockfile
corepack pnpm@10.34.3 run compile
corepack pnpm@10.34.3 test
corepack pnpm@10.34.3 run typecheck
corepack pnpm@10.34.3 run export:abi
```

The exported ABI is `backend/src/main/resources/contracts/PesaChainEventLedger.json` and is derived
from the compiled contract artifact. Do not hand-edit that generated ABI.

## Local Hardhat 3 environment

Start the local JSON-RPC node in one terminal:

```powershell
cd blockchain
corepack pnpm@10.34.3 run node
```

It serves chain ID `31337` at `http://127.0.0.1:8545`. Hardhat displays funded deterministic accounts
and their publicly known keys. They are unsafe test credentials and must never be used on a live
network or committed as production secrets. Set a test signer only in the local process environment,
then deploy in a second terminal:

```powershell
$env:PESACHAIN_RPC_URL = "http://127.0.0.1:8545"
$env:PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY = "<funded Hardhat local test key>"
corepack pnpm@10.34.3 run deploy:local
```

The deployment command prints the ephemeral local contract address and initial recorder account.
Configure the backend process with `PESACHAIN_RPC_URL`, `PESACHAIN_CHAIN_ID=31337`,
`PESACHAIN_CONTRACT_ADDRESS`, `PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY`, and
`PESACHAIN_BLOCKCHAIN_CONFIRMATIONS` (default `1`). Never put a private key in frontend source,
`.env.example`, committed config, or logs. The hardhat network state and deployment address are lost
when the local node stops; deploy again for a new local node and update only local ignored config.

## Transactional outbox

Flyway V8 adds `blockchain_outbox`, `blockchain_event_attempts`, and
`blockchain_reconciliation_findings`, and a random opaque aggregate key on the application. V1–V7
remain unchanged. The existing approval/rejection, full disbursement, official repayment, and
completion database transactions append their permitted outbox event atomically. A simulation event
alone does not change the balance and does not publish a repayment event.

The worker claims rows in a short DB transaction with a lease and `FOR UPDATE SKIP LOCKED`, commits
that claim, then performs RPC calls without holding database locks. Event statuses are `PENDING`,
`PROCESSING`, `SUBMITTED`, `RECORDED`, and `FAILED`. A stable event ID is reused on retry. Before
submitting a row without a known transaction hash, the worker checks the contract by that ID to
recover a transaction accepted before a prior process interruption. The receipt must contain the
matching contract event and payload. The worker stores `RECORDED` only once the configured chain
confirmation count is met (default one for local Hardhat). Transient failures receive bounded
exponential retry delay with jitter and a maximum-attempt limit; permanent failures remain visible
for investigation. RPC work never rolls back a committed loan operation.

## Reconciliation and monitoring API

Reconciliation compares outbox evidence, contract getters, and logs. It records repeatable findings
for missing records, payload disagreement, duplicate chain events, unexpected events, and local
status disagreement. It never modifies loan state or fabricates transactions. An administrator can
inspect findings or trigger a reconciliation run:

- `GET /api/v1/admin/blockchain-events`
- `GET /api/v1/admin/blockchain-reconciliation/findings`
- `POST /api/v1/admin/blockchain-reconciliation/run`

Loan Officers can inspect event status at `GET /api/v1/officer/blockchain-events`. Borrowers can
inspect events only for a loan they own at `GET /api/v1/loans/{loanId}/blockchain-events`. The
administrator role does not inherit loan-officer actions. Responses expose KES-formatted amounts,
safe error codes, and transaction evidence, not signer details or provider diagnostics.

## Verification

Hardhat Solidity tests cover recorder access, event payloads and amount rules, duplicate IDs, invalid
inputs, and immutable evidence. Backend tests cover transactional outbox writes, rollback, retries,
ambiguous submission recovery, confirmation gating, reconciliation findings, endpoint authorization,
and PostgreSQL migration compatibility. The opt-in
`LocalHardhatBlockchainIntegrationTest` exercises backend signing, real RPC submission, confirmation,
and chain readback against a local deployment. To run it, start and deploy the local node as above,
configure the backend blockchain environment variables, set
`PESACHAIN_RUN_LOCAL_CHAIN_TESTS=true`, then run:

```powershell
cd backend
.\mvnw.cmd -Dtest=LocalHardhatBlockchainIntegrationTest test
```

The normal Maven verification suite skips this environment-dependent local chain test unless that
flag is explicitly set.
