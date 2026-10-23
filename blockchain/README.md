# PesaChain local blockchain

This Hardhat 3 project contains the PesaChain append-only event ledger, Solidity contract tests, an
ephemeral local JSON-RPC node, deployment script, and ABI export. It has no borrower wallet or payment
processing functionality. See [`docs/claude/02-BLOCKCHAIN.md`](../docs/claude/02-BLOCKCHAIN.md) for
the event model, backend environment, outbox behavior, and verification procedure.

## Prerequisites

- Node.js 22 or newer (Hardhat 3 supported runtime)
- Corepack and pnpm 10.34.3

## Compile and test

```powershell
corepack pnpm@10.34.3 install --frozen-lockfile
corepack pnpm@10.34.3 run compile
corepack pnpm@10.34.3 test
corepack pnpm@10.34.3 run typecheck
corepack pnpm@10.34.3 run export:abi
```

## Run a local chain

In a terminal, run `corepack pnpm@10.34.3 run node` from this directory. In another terminal,
set `PESACHAIN_RPC_URL=http://127.0.0.1:8545` and
`PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY` to a funded Hardhat test account key, then run
`corepack pnpm@10.34.3 run deploy:local`. Hardhat uses chain ID 31337 by default. Its displayed
development keys are publicly known and are unsafe on live networks; never commit them or use them
outside an isolated local test environment.

Each node run starts a fresh in-memory chain. Record the printed contract address in local ignored
backend configuration and redeploy when the node restarts. The backend is the only contract writer;
frontend code does not connect to this RPC endpoint.
