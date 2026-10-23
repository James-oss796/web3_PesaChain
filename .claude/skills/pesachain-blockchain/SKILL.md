---
name: pesachain-blockchain
description: Implements or reviews PesaChain Solidity event evidence, local deployment, backend adapter, outbox worker, retries, receipt verification, and blockchain failure handling.
---

# PesaChain blockchain

1. Read `docs/claude/02-BLOCKCHAIN.md`.
2. Keep the contract evidence-only and backend-recorder-only.
3. Reject duplicate stable event IDs.
4. Verify no PII or free text reaches calldata, storage, emitted events, logs, or transaction
   metadata.
5. Implement submit, receipt confirmation, uncertain-outcome reconciliation, bounded retries, and
   explicit verification-unavailable behavior.
6. Prove RPC failure does not undo or block PostgreSQL operations.
7. Run contract, adapter, and failure-simulation tests before claiming completion.
