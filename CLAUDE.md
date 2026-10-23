# PesaChain implementation instructions

PesaChain is a microfinance loan-management system with selective blockchain-backed event
verification. The current repository contains a React/Vite prototype. The implementation target is
a working full-stack academic system, not a cryptocurrency product.

## Required reading

Before changing code, read these documents in order:

1. `docs/claude/00-EXECUTION-PLAN.md`
2. `docs/claude/01-BACKEND-DOMAIN.md`
3. `docs/claude/02-BLOCKCHAIN.md`
4. `docs/claude/03-UI-UX.md`
5. `docs/claude/04-TESTING-SECURITY.md`

Use `PROMPT_FOR_CLAUDE.md` as the task prompt when starting a full implementation session.

## Non-negotiable product boundaries

- Roles are `BORROWER`, `LOAN_OFFICER`, and `ADMINISTRATOR`.
- Administrators do not inherit Loan Officer permissions.
- A Loan Officer, not a smart contract or scoring algorithm, decides applications.
- Application states are `PENDING`, `APPROVED`, or `REJECTED`.
- Loan states are `APPROVED`, `DISBURSED`, `ACTIVE`, and `COMPLETED`.
- Selected event types are `LOAN_APPROVED`, `LOAN_REJECTED`, `LOAN_DISBURSED`,
  `REPAYMENT_RECORDED`, and `LOAN_COMPLETED`.
- PostgreSQL is the operational source of truth.
- Blockchain recording is asynchronous and separately reported as `PENDING`, `PROCESSING`,
  `RECORDED`, or `FAILED`.
- A blockchain failure must never roll back or misrepresent a successful operational action.
- Never put names, emails, phone numbers, national IDs, addresses, free-text purposes, decision
  notes, or repayment references on-chain.
- Do not add wallets, tokens, trading, NFTs, DeFi, staking, AI scoring, social features, chat,
  M-Pesa, or unsupported financial products.

## Repository target

Keep the existing frontend at the repository root unless a migration is clearly necessary. Add:

```text
backend/                 Spring Boot application
contracts/               Solidity contract and contract tests
docs/                    Architecture, API, and operational documentation
docker-compose.yml       PostgreSQL and local blockchain development services
.env.example             Names only; never real secrets
```

Prefer incremental, reviewable phases. Do not replace the prototype with a generic template.

## Capability and skill policy

At the start of a session, inspect available Claude Code skills and MCP servers. If these optional
capabilities are genuinely installed, use them where relevant:

- `ui-ux-pro-max`
- `awesome-design` or `frontend-design`
- `21st-dev` / 21st.dev component MCP
- `playwright`
- `motion` / animation guidance

Do not claim to use an unavailable skill and do not install unknown packages merely because a skill
name appears in this file. The project-local skills in `.claude/skills/` are always the fallback.
Third-party generated components must be adapted to the PesaChain design tokens, accessibility
rules, and product boundaries.

## Working rules

- Inspect existing code before editing.
- Preserve working user changes.
- Implement the smallest complete vertical slice before broadening.
- Add database migrations; never rely on automatic destructive schema generation.
- Enforce authorization and state transitions on the server, not only in the UI.
- Use DTO allowlists and never expose persistence entities directly.
- Never commit secrets, private keys, tokens, or real personal data.
- Use fictional fixtures.
- Keep an implementation checklist in `docs/IMPLEMENTATION_STATUS.md`.
- At every phase gate, run the checks prescribed in `docs/claude/04-TESTING-SECURITY.md`.
- Do not claim a workflow is complete until its automated tests pass.
