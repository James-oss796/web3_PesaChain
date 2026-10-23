# PesaChain free-tier deployment guide

**Last reviewed: 22 October 2026.** This is a deployment plan for the existing React/Vite + Spring Boot + PostgreSQL system. It excludes Render as requested. Provider free-tier terms can change; review the linked official plan pages before entering payment details. “Free tier” does not guarantee a zero bill.

## Recommended academic-demo topology

| Component | Suggested service | Role and limitation |
| --- | --- | --- |
| React/Vite frontend | Cloudflare Pages (Vercel is an alternative) | Static `dist/` hosting. Configure a same-origin `/api/*` proxy so browser session cookies and CSRF continue to work. |
| Spring Boot API | Google Cloud Run | Container/source deployment with a usage-based free allowance. Billing setup is required and usage beyond the free allowance is chargeable. Scale-to-zero can add a cold start. |
| PostgreSQL | Supabase Free | Managed PostgreSQL suitable for an academic demo. Free projects have a 500 MB database limit, no downloadable backups, and may pause after a week of inactivity. Export backups independently. |
| Blockchain | A test network and its RPC endpoint | The current Hardhat localhost node and local contract address are not public. Deploy the approved contract to a test network (for example, Sepolia), then configure the backend with that network's chain ID and deployed address. Use valueless test tokens only. |

The application uses server-side sessions and CSRF. It does not currently configure cross-origin credentialed CORS. Keep the browser API same-origin through a reverse proxy; do not set `VITE_API_BASE_URL` to a separate API origin unless the backend is deliberately updated and reviewed for credentialed CORS and cross-site cookie settings.

Cloud Run's free allowance is usage-based and aggregated by billing account. Its background outbox worker also needs runtime time to poll. A scale-to-zero API can delay blockchain delivery while idle; a continuously warm worker may exceed free usage. Treat blockchain delivery as asynchronous and inspect its persisted status after waking the service. Never promise continuous free operation.

## 1. Prepare a separate hosted environment

1. Create a fresh hosted database. Do not copy or expose the existing local Compose database credentials or data.
2. Keep production/demo secrets out of the repository, frontend variables, build logs, and deployment documentation. Store them in the backend provider's secret manager.
3. Generate a unique random `PESACHAIN_SIMULATION_HMAC_SECRET` of at least 32 bytes.
4. Configure a one-time Administrator bootstrap credential using the supported backend variables. Do not use a publicly known/default password. Complete first-login password rotation and remove the bootstrap credential from the runtime environment after provisioning if the bootstrap mechanism permits it.
5. Build/deploy a test-chain contract using the repository's approved Hardhat workflow. Save the resulting contract address and chain ID in the backend secret/configuration store. The local reference address `0x5fbdb2315678afecb367f032d93f642f64180aa3` is only valid for the local Hardhat chain that deployed it.
6. Use test RPC credentials and a dedicated server-managed signer funded only with the test network's valueless tokens. Never place the signer key in Vite variables or the browser bundle.

## 2. Configure PostgreSQL

Create a PostgreSQL project in Supabase (or another PostgreSQL-compatible managed service). Copy the connection host, port, database, username, and password from that project's connection settings. Use TLS (`sslmode=require`) and an application-specific database role with only the permissions needed for the application and Flyway migrations.

Set these backend variables in the hosting provider's secret/environment manager:

```text
PESACHAIN_DB_URL=jdbc:postgresql://<host>:5432/<database>?sslmode=require
PESACHAIN_DB_USERNAME=<application-role>
PESACHAIN_DB_PASSWORD=<managed-secret>
PESACHAIN_COOKIE_SECURE=true
PESACHAIN_SIMULATION_HMAC_SECRET=<random-secret-at-least-32-bytes>
```

The backend runs Flyway migrations on startup. The current repository migration history is additive; let Flyway apply it to the new database. Never point an unreviewed deployment at a production database or reset an existing database to resolve a migration issue. Confirm a successful readiness/health check and inspect Flyway startup logs without printing secret-bearing environment values.

Supabase Free has limited storage and no included downloadable backups. Set a regular `pg_dump` backup procedure before storing any data that matters. Do not store real borrower financial information in a public academic demonstration.

## 3. Deploy the Spring Boot API to Cloud Run

1. Create a Google Cloud project, enable billing, Cloud Run, and the source-build service required by the current Google setup wizard. Set a budget alert before deployment.
2. From the repository root, deploy the backend source directory using the Java build configuration supported by Cloud Run. The backend listens on port 8080; configure the service to pass the provider's `PORT` if required by the selected runtime. If the build system cannot detect the Maven wrapper/source layout, add and review a small backend Dockerfile before deployment rather than changing the application stack.
3. Configure the variables above plus the approved blockchain settings:

```text
PESACHAIN_RPC_URL=<test-network-rpc-url>
PESACHAIN_CHAIN_ID=<test-network-chain-id>
PESACHAIN_CONTRACT_ADDRESS=<deployed-approved-contract-address>
PESACHAIN_BLOCKCHAIN_SIGNER_PRIVATE_KEY=<secret-manager-value>
PESACHAIN_BLOCKCHAIN_CONFIRMATIONS=1
PESACHAIN_BLOCKCHAIN_WORKER_ENABLED=true
```

The signer key must be stored as a backend secret, never as a build argument or frontend value. If the test RPC provider requires an API key, keep it inside the server-side RPC URL secret. Allow public ingress only because the frontend must call the API; application endpoints remain protected by the existing session and role checks.

4. Verify `/actuator/health`, database readiness, session login, CSRF-protected mutations, and one test-chain event using isolated demo accounts before connecting the public frontend.
5. Monitor Cloud Run requests, cold starts, CPU/memory use, outbox retries, and free-tier usage. Scale-to-zero may pause the scheduled outbox worker; do not interpret a pending event as recorded. A persistent minimum instance may incur charges.

Official references: [Cloud Run pricing and free allowance](https://cloud.google.com/run/pricing), [Cloud Run source deployment](https://cloud.google.com/run/docs/deploying-source-code), [Supabase Free plan](https://supabase.com/pricing), [Supabase production checklist](https://supabase.com/docs/guides/deployment/going-into-prod).

## 4. Deploy the frontend to Cloudflare Pages

1. Connect the repository to Cloudflare Pages.
2. Select the React/Vite preset, set the project root to the repository root, build command to `corepack pnpm@10.34.3 run build`, and output directory to `dist`.
3. Set the supported Node/pnpm toolchain from `.mise.toml` in the build environment. Do not upgrade dependencies during deployment.
4. Leave `VITE_API_BASE_URL` unset/empty so the client uses same-origin `/api/v1/...` URLs.
5. Add a Pages Function proxy for `/api/*` that forwards the request path, query string, method, body, `Cookie`, and CSRF header to the Cloud Run origin. Store the fixed API origin as a Pages environment value, not in user-controlled query data. Forward `Set-Cookie` response headers. Verify cookies remain host-only for the frontend origin and have `Secure`, `HttpOnly`, and the configured `SameSite=Lax` attributes. Do not cache `/api/*` responses.
6. Check that the existing service worker only caches versioned static assets and bypasses API requests. Verify manifest, icons, SPA route fallback, and HTTPS on the deployed site.

Cloudflare Pages currently documents `dist` as the Vite output directory. Pages Functions execute on the Workers platform and have plan limits; the static assets are separate from API proxy requests. Review [Pages build configuration](https://developers.cloudflare.com/pages/configuration/build-configuration/) and [Pages Functions pricing/limits](https://developers.cloudflare.com/pages/functions/pricing/) before enabling the proxy.

### Vercel alternative

Vercel can host the same static Vite build with `corepack pnpm@10.34.3 run build` and output `dist`. Configure an SPA fallback to `index.html` and a same-origin `/api/*` rewrite/proxy to the API. Do not point the frontend directly to Cloud Run from the browser without first implementing and testing credentialed CORS, secure cross-site cookies, and CSRF behavior in Spring Boot. Vercel's static hosting does not run this Java Spring Boot backend as a Vercel Function; keep the API on a compatible Java host. See [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite) and [SPA rewrites](https://vercel.com/kb/guide/why-is-my-deployed-project-giving-404).

## 5. Blockchain and financial safety checks

- Do not use the local Hardhat RPC URL (`127.0.0.1:8545`) in a remote backend. In a cloud container, localhost refers to that container.
- Confirm test chain ID, contract address, ABI, backend signer role, and required confirmation count before sending any test event.
- Use a test-chain deployment and test tokens only. No M-Pesa or real-money payment is connected.
- Keep personal information, application purpose, decision rationale, credentials, and detailed repayment data in PostgreSQL, never in the contract or event payload.
- A blockchain event is evidence that the contract recorded a selected event, not proof money moved.
- Confirm the database operation succeeds even if the RPC is unavailable, then verify the outbox reports `PENDING`/`FAILED` until the configured chain confirmation condition is reached.

## 6. Final deployment checklist

- [ ] Separate hosted database created, backups planned, and Flyway startup verified.
- [ ] Backend secrets set in a secret manager; no secrets in frontend variables or Git.
- [ ] Bootstrap Administrator uses a unique password and completes forced password change.
- [ ] `PESACHAIN_COOKIE_SECURE=true` in hosted HTTPS environment.
- [ ] Same-origin API proxy verified for session cookie, CSRF cookie/header, `Set-Cookie`, and no API caching.
- [ ] Backend routes verified for Borrower, Loan Officer, and Administrator with real role-specific test accounts.
- [ ] Admin/Officer permissions remain server-enforced; Administrators do not inherit Loan Officer authority.
- [ ] Test-chain ID, contract address, ABI, signer role, and confirmation behavior verified.
- [ ] Outbox behavior, retry status, and blockchain outage handling tested after deployment.
- [ ] Budget alerts and provider free-tier limits reviewed; no guarantee of zero cost or uninterrupted availability is presented.
- [ ] Public pages contain the supplied contact information and a clear simulated-payment disclosure.

## Known deployment prerequisites

- This repository does not currently contain a production Cloud Run Dockerfile, Cloudflare Pages API proxy function, or hosted environment configuration. Add and verify those deployment-specific pieces before publishing; they are intentionally not fabricated by this guide.
- The existing backend does not configure cross-origin credentialed CORS. Use a same-origin proxy or implement a separately reviewed CORS/session configuration before choosing a cross-origin frontend/API setup.
- The current local Hardhat contract deployment cannot be reached from public hosting and must not be represented as deployed in the cloud.
- Free hosting is suitable for a class demonstration, not a promise of production uptime, recovery, privacy compliance, or uninterrupted background event processing.
