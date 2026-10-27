# PesaChain blockchain operations

> **Operating principle** · PostgreSQL commits the loan and repayment record. The blockchain
> outbox publishes selected evidence separately. A healthy chain is required for evidence to reach
> `RECORDED`; it is not a prerequisite for a simulated operational workflow to commit.

This guide explains the `CONTRACT_CODE_UNAVAILABLE` warning, how to restore a local Hardhat ledger,
how to work failed outbox events, and what changes when PesaChain is hosted. It does not authorize a
real-money transfer, a mainnet deployment, or a change to the approved contract.

## What this warning means

The backend reached the RPC check but could not confirm deployed bytecode at the contract address it
is configured to use. In `Web3jBlockchainClient.runtime()`, `CONTRACT_CODE_UNAVAILABLE` is returned
when `eth_getCode` errors, returns no code, or returns `0x`. The common local cause is that Hardhat
was restarted: a new Hardhat node starts with an empty in-memory chain, so the previous deployment
no longer exists even if its address is still configured.

For the reported incident, a read-only probe of `http://127.0.0.1:8545` returned chain ID `0x7a69`
(31337), and `eth_getCode` returned no bytecode at the default backend address
`0x5fbdb2315678afecb367f032d93f642f64180aa3`. No transaction was sent. This confirms that the
currently reachable local node has no contract at that default address. If the backend process has
an address override, compare that exact address using the same read-only check below.

The warning does **not** mean PostgreSQL lost or rejected a loan/repayment. It means its separate
blockchain evidence cannot currently be checked or delivered. The Admin **Blockchain monitoring**
page shows connection status, expected chain, latest block when available, outbox totals, and a safe
reason. It deliberately does not show the RPC URL or signing key.

Other useful health codes:

| Health code | Meaning | First check |
|---|---|---|
| `RPC_UNAVAILABLE` | RPC could not be reached. | Is the node/service running at the configured host and port? |
| `RPC_CHAIN_ID_MISMATCH` | The RPC answers for a different chain. | Compare the configured chain ID with the RPC's `eth_chainId`. |
| `CONTRACT_CODE_UNAVAILABLE` | No usable code was returned for the configured address. | Deploy to this exact chain and configure the address printed by that deployment. |
| `CONTRACT_ABI_MISMATCH` | Code exists, but required ledger functions do not match. | Confirm the deployed contract is this repository's `PesaChainEventLedger` build. |
| `SIGNER_NOT_AUTHORIZED` | Contract is present, but the backend signer is not an allowed recorder. | Make sure deployment and backend use the same authorized local test signer, or grant the intended recorder through the approved contract process. |
| `BLOCKCHAIN_NOT_CONFIGURED` | Required backend blockchain settings are blank. | Check the backend process environment / host secret settings. |

## Restore the local Hardhat chain

Use this for local development and demonstrations where the API and Hardhat node run on the same
computer. Keep the node terminal open. The node and its chain are ephemeral; stopping it clears the
local chain and contract deployment.

### 1. Start the node

In PowerShell terminal 1:

```powershell
Set-Location C:\Users\Admin\Downloads\PesaChain\blockchain
corepack pnpm@10.34.3 run node
```

### 2. Confirm the node responds

In PowerShell terminal 2:

```powershell
Test-NetConnection 127.0.0.1 -Port 8545

$body = '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}'
Invoke-RestMethod -Uri http://127.0.0.1:8545 -Method Post `
  -ContentType 'application/json' -Body $body
```

For this local network the expected result is chain ID `0x7a69` (decimal `31337`).

### 3. Compile and deploy the ledger to this running node

The deployment account must be a funded local Hardhat test account. Hardhat's displayed development
keys are public test credentials: use them only on the isolated local chain, never on a public
network. The deployment account becomes the initial authorized recorder. The same local-only signer
must be available to the backend process. Do not print, commit, or put its key in frontend settings.

Set `PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY` in the current process environment using your local
secret-handling method. The Hardhat project and Spring Boot read process environment variables; a
root `.env` file is not automatically loaded by Spring Boot. Do not use a real wallet key here.

```powershell
Set-Location C:\Users\Admin\Downloads\PesaChain\blockchain
$env:PESACHAIN_RPC_URL = 'http://127.0.0.1:8545'
if ([string]::IsNullOrWhiteSpace($env:PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY)) {
  throw 'Set the local-only signer in this process through your secure local setup; its value is not displayed.'
}
corepack pnpm@10.34.3 run compile
corepack pnpm@10.34.3 run deploy:local
```

Copy the **contract address printed by this deployment**. Do not assume a remembered address is
still deployed. Confirm code at the printed address without sending a transaction:

```powershell
$address = '<address printed by deploy:local>'
$body = @{ jsonrpc = '2.0'; method = 'eth_getCode'; params = @($address, 'latest'); id = 1 } |
  ConvertTo-Json -Compress
$response = Invoke-RestMethod -Uri $env:PESACHAIN_RPC_URL -Method Post `
  -ContentType 'application/json' -Body $body
if ($response.result -and $response.result -ne '0x') {
  'Contract bytecode is present at the supplied address.'
} else {
  throw 'No contract bytecode at this address on this RPC.'
}
```

### 4. Start/restart the backend with matching configuration

In PowerShell terminal 3, set non-secret values for this backend process and ensure the same local
signer key is inherited or securely supplied to it:

```powershell
Set-Location C:\Users\Admin\Downloads\PesaChain\backend
$env:PESACHAIN_RPC_URL = 'http://127.0.0.1:8545'
$env:PESACHAIN_CHAIN_ID = '31337'
$env:PESACHAIN_CONTRACT_ADDRESS = '<address printed by deploy:local>'
$env:PESACHAIN_BLOCKCHAIN_WORKER_ENABLED = 'true'
$env:PESACHAIN_BLOCKCHAIN_CONFIRMATIONS = '1'
if ([string]::IsNullOrWhiteSpace($env:PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY)) {
  throw 'Set the same local-only signer in this process through your secure local setup; its value is not displayed.'
}
.\mvnw.cmd spring-boot:run
```

Set the backend signer in the process environment or an approved local secret manager; it must be
the same authorized local test account used for deployment. `PESACHAIN_CONTRACT_ADDRESS` is the
actual Spring property override name. Do not rely on a shell variable in a different terminal.

After startup, open the Admin **Blockchain monitoring** page and select **Recheck connection**. A
successful check should show `AVAILABLE`, chain `31337`, and a latest block. This checks RPC,
contract interface, and signer authorization, not just whether port 8545 is open.

> Restarting only the backend does not erase the node's current chain. Restarting the Hardhat node
> does erase its chain; compile/deploy again and update the backend address before expecting health.

## What happens to events during an outage

1. The loan or repayment operation commits to PostgreSQL with its outbox event in the same database
   transaction. A blockchain outage does not claim that money moved and does not undo the operation.
2. The worker attempts delivery in the background. Pending events are picked up automatically;
   transient failures use bounded backoff and a maximum attempt count.
3. A successful send is `SUBMITTED`, not `RECORDED`. The worker checks the contract receipt and
   waits for the configured confirmation count before writing `RECORDED`.
4. A failed event that remains retryable is retried automatically. After attempts are exhausted, an
   Administrator checks the live health panel and uses **Retry delivery** for that event. The API
   repeats the health check, records the administrative retry action, then queues the existing event
   ID; it does not create a second event or alter the loan record.
5. If the chain already accepted an event but the backend missed the response, the worker checks the
   stable event ID on-chain before resubmitting. This avoids duplicate evidence. Reconciliation
   reports mismatches; it never changes the authoritative loan balance to match the chain.

An event can remain `FAILED` even after the node comes back if its automatic attempts were exhausted.
Health must be `AVAILABLE` before the Administrator's manual retry is accepted. `PENDING`, `SUBMITTED`,
and `FAILED` must not be described as confirmed evidence.

## Choosing a blockchain when hosting PesaChain

| Setup | Good for | Important boundary |
|---|---|---|
| Local Hardhat node | Development, isolated demos, contract/backend testing. | Ephemeral, private to the machine/network, resets when stopped. Do not publish port 8545 or use its public test keys with real assets. |
| Hosted API + local Hardhat | A private, temporary demo where the API can securely reach the same machine over a private network/VPN. | `127.0.0.1` from a cloud backend means the cloud container itself, not your laptop. Do not expose Hardhat's unlocked accounts on the public internet. |
| Hosted API + public testnet | A hosted academic/demo site that needs externally reachable, durable shared chain evidence. | This project currently configures Hardhat's `localhost` network only. Adding a public test network (for example Sepolia), deployment configuration, explorer/readback checks, and a dedicated test signer must be implemented and tested before use. Use test assets only. |
| Production network | A real service with production operational and security controls. | Not currently approved or production-ready. Requires a reviewed chain, key management, contract review, monitoring, incident response, privacy/legal assessment, and explicit product/security approval. |

**Recommendation:** keep Hardhat for local development. If you publish the site for an academic
demonstration and need chain evidence visible to other people, plan a separate public **testnet**
deployment after adding and verifying the required Hardhat network configuration. Do not point a
hosted demo backend at your laptop's `localhost`, and do not use mainnet for this prototype.

The frontend can be hosted as static Vite assets on Cloudflare Pages or Vercel, but it still needs a
separately hosted Spring Boot API and PostgreSQL database. Preserve same-origin session-cookie and
CSRF behavior through a same-origin API proxy. Keep the blockchain signer only in the backend's
secret store. A backend platform that sleeps or scales to zero may delay the scheduled outbox worker
until it wakes. Vercel Hobby cron runs at most daily, so it cannot replace PesaChain's short-interval
backend worker. Confirm provider limits before choosing a free tier; they change over time. See the
[free deployment guide](FREE_DEPLOYMENT_GUIDE.md), [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/),
[Vercel Functions limits](https://vercel.com/docs/functions/limitations), and [Vercel cron usage limits](https://vercel.com/docs/cron-jobs/usage-and-pricing).

Ethereum.org currently recommends Sepolia for application developers; that is a possible future
demo target, not a network already configured or deployed by this repository. Review the
[Ethereum network guide](https://ethereum.org/developers/docs/networks/) before deciding. No real
funds or real payment rails are part of PesaChain's repayment simulation.

## Safe operator checklist

- [ ] RPC host/port is reachable from the **backend runtime**, not just your browser.
- [ ] `eth_chainId` equals the configured `PESACHAIN_CHAIN_ID`.
- [ ] `eth_getCode` at the configured contract address returns bytecode.
- [ ] Admin health says `AVAILABLE`; the contract interface and backend signer check also pass.
- [ ] Worker is enabled and the backend process has the intended environment/secrets.
- [ ] Outbox counts and each event status are reviewed separately from operational loan status.
- [ ] Retry only exhausted `FAILED` records after health is available; investigate permanent contract/config errors first.
- [ ] Never reset PostgreSQL to repair chain delivery. Never publish local RPC or signer secrets.
- [ ] For hosted demos, use an explicitly configured test network and test-only signer; do not use mainnet.

### Sources checked 2026-10-26

- [Hardhat 3 deployment guide](https://hardhat.org/ignition/docs/getting-started)
- [Ethereum networks and testnet selection](https://ethereum.org/developers/docs/networks/)
- [Cloudflare Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
- [Vercel Functions limits](https://vercel.com/docs/functions/limitations)
- [Vercel Cron usage and pricing limits](https://vercel.com/docs/cron-jobs/usage-and-pricing)
