---
name: pesachain-playwright
description: Creates and runs reliable PesaChain Playwright tests for borrower, Loan Officer, Administrator, responsive, accessibility, and blockchain-failure workflows.
---

# PesaChain Playwright

1. Read the Playwright sections in `docs/claude/04-TESTING-SECURITY.md`.
2. Use isolated fictional seed data and reusable role fixtures.
3. Prefer accessible locators; never use arbitrary sleeps.
4. Cover all three role journeys and the blockchain-failure/recovery journey.
5. Test critical pages at mobile, tablet, and desktop widths.
6. Add accessibility assertions and manual keyboard-review notes.
7. Capture traces/screenshots only on failure or for stable visual regression evidence.
8. Run the real repository E2E command and report its exact result.
