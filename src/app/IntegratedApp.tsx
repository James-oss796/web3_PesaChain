import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { api, ApiError, clearCsrf, idempotencyKey, setUnauthorizedHandler, type Account } from "../services/api";
import pesachainMark from "../assets/pesachain-symbol.svg";
import { downloadPesaChainPdf } from "../services/pdf";

type Application = {
  id: string; requestedAmount: string; currency: string; purpose: string; status: string; submittedAt: string;
  decisionAt?: string; rejectionReason?: string; requestedTermMonths: number; approvedPrincipal?: string;
  approvedTermMonths?: number; approvedFlatRatePercent?: string; calculatedInterest?: string; totalRepayable?: string; loanStatus?: string;
};
type OfficerApplication = Application & { borrowerId: string; borrowerName: string; decisionRationale?: string };
type Page<T> = { items: T[]; page: number; size: number; totalItems: number };
type Loan = { id: string; applicationId: string; principal: string; flatRatePercent: string; termMonths: number; interestAmount: string; totalRepayable: string; outstandingBalance: string; status: string; approvedAt: string; disbursedAt?: string; activatedAt?: string; history: { fromStatus: string; toStatus: string; actorId: string; changedAt: string }[] };
type RepaymentEvent = { id: string; loanId: string; amount: string; status: string; createdAt: string; sourceVerifiedAt?: string; verifiedAt?: string; recordedAt?: string };
type Repayment = { id: string; eventId: string; loanId: string; amountApplied: string; outstandingAfter: string; recordedAt: string; recordedBy: string };
type ChainEvent = { eventId: string; eventType: string; amount?: string; status: string; transactionHash?: string; blockNumber?: string; confirmations: number; attemptCount: number; lastErrorCode?: string; occurredAt: string; recordedAt?: string };
type Staff = { id: string; email: string; role: string; status: string; createdAt: string };
type Activity = { id: string; actorRole?: string; action: string; targetType: string; targetId?: string; result: string; occurredAt: string };
type AdminReport = {
  generatedAt: string; months: number;
  applications: { total: number; pending: number; approved: number; rejected: number };
  loans: { total: number; approved: number; active: number; completed: number; approvedPrincipal: string; contractualTotal: string; outstandingBalance: string; repaymentCount: number; amountRepaid: string };
  activity: { last30Days: number; failedLast30Days: number };
  blockchainEvents: Record<string, number>;
  monthlyTrend: { month: string; applications: number; decisions: number; disbursements: number; disbursedAmount: string; repayments: number; repaidAmount: string }[];
};
type BorrowerHistory = {
  applicationCount: number; loanCount: number; activeLoanCount: number; amountRepaid: string; outstandingBalance: string;
  loans: { loanId: string; applicationId: string; principal: string; totalRepayable: string; amountRepaid: string; outstandingBalance: string; status: string; approvedAt: string; disbursedAt?: string }[];
  repayments: { repaymentId: string; loanId: string; amountApplied: string; outstandingAfter: string; recordedAt: string; blockchainStatus?: string; transactionHash?: string; blockNumber?: string; confirmations: number }[];
  lifecycle: { loanId: string; fromStatus?: string; toStatus: string; changedAt: string }[];
};
type SubmissionOutcome = { title: string; message: string; path: string; data: unknown };
type Screen = "home" | "dashboard" | "profile" | "applications" | "apply" | "application" | "loans" | "loan" | "repayments" | "officer-applications" | "officer-application" | "officer-repayments" | "officer-events" | "officer-loans" | "users" | "admin-chain" | "admin-activity" | "admin-reports" | "password";

const money = (value?: string | null) => {
  if (value == null || !/^\d+(\.\d{1,2})?$/.test(value)) return "—";
  const [whole = "0", fraction = "00"] = value.split(".");
  return `KES ${BigInt(whole).toLocaleString("en-KE")}.${fraction.padEnd(2, "0")}`;
};
const moneyCents = (value: string) => {
  if (!/^\d+(?:\.\d{1,2})?$/.test(value)) return null;
  const [whole = "0", fraction = ""] = value.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
};
const repaymentPercent = (total: string, outstanding: string) => {
  const totalCents = moneyCents(total);
  const outstandingCents = moneyCents(outstanding);
  if (totalCents === null || outstandingCents === null || totalCents === 0n) return 0;
  const paid = totalCents > outstandingCents ? totalCents - outstandingCents : 0n;
  return Number((paid * 10000n) / totalCents) / 100;
};
const date = (value?: string) => value ? new Intl.DateTimeFormat("en-KE", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "—";
const roleLabel = (role: Account["role"]) => role === "LOAN_OFFICER" ? "Loan Officer" : role === "ADMINISTRATOR" ? "Administrator" : "Borrower";
const firstName = (account: Account) => account.name?.trim().split(/\s+/)[0] || account.email.split("@")[0] || "there";
const badgeTone = (status: string) => /FAILED|REJECTED/.test(status) ? "danger" : /PENDING|PROCESSING|SUBMITTED|DISBURSED/.test(status) ? "warning" : status === "ACTIVE" ? "active" : status === "APPROVED" ? "approved" : /RECORDED|COMPLETED|VERIFIED/.test(status) ? "success" : "info";

function Logo() { return <div className="logo"><img src={pesachainMark} alt="" /><strong>PesaChain</strong></div>; }
function Badge({ children }: { children: string }) { return <span className={`badge badge-${badgeTone(children)}`}><span className="badge-dot" />{children.replace(/_/g, " ")}</span>; }
function Button({ children, onClick, type = "button", variant = "primary", disabled = false, loading = false, className = "" }: { children: ReactNode; onClick?: () => void; type?: "button" | "submit"; variant?: "primary" | "secondary" | "ghost" | "danger"; disabled?: boolean; loading?: boolean; className?: string }) { return <button className={`btn btn-${variant} ${className}`.trim()} type={type} onClick={onClick} disabled={disabled || loading} aria-busy={loading || undefined}>{loading && <TwinDotSpinner />} {children}</button>; }
function Field({ label, value, onChange, type = "text", required = false, hint, min, max, step, disabled = false }: { label: string; value: string; onChange: (value: string) => void; type?: string; required?: boolean; hint?: string; min?: string; max?: string; step?: string; disabled?: boolean }) { const id = label.replace(/\s/g, "-"); return <label className="field"><span>{label}{required && <b> *</b>}</span><input value={value} onChange={(event) => onChange(event.target.value)} type={type} required={required} min={min} max={max} step={step} disabled={disabled} aria-describedby={hint ? `${id}-hint` : undefined} />{hint && <small id={`${id}-hint`}>{hint}</small>}</label>; }
function Area({ label, value, onChange, required = false, hint }: { label: string; value: string; onChange: (value: string) => void; required?: boolean; hint?: string }) { return <label className="field field-wide"><span>{label}{required && <b> *</b>}</span><textarea value={value} onChange={(event) => onChange(event.target.value)} rows={4} required={required} />{hint && <small>{hint}</small>}</label>; }
function Panel({ children, className = "" }: { children: ReactNode; className?: string }) { return <section className={`card ${className}`}>{children}</section>; }
function Header({ title, description, actions }: { title: ReactNode; description?: string; actions?: ReactNode }) { return <header className="page-header"><div><span className="eyebrow">PesaChain workspace</span><h1>{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className="header-actions">{actions}</div>}</header>; }
function WelcomeTitle({ account }: { account: Account }) { return <span className="welcome-title">Welcome back, <span className="welcome-name">{firstName(account)}</span></span>; }
function Empty({ title, detail, action }: { title: string; detail: string; action?: ReactNode }) { return <Panel><div className="empty-state"><h2>{title}</h2><p>{detail}</p>{action}</div></Panel>; }
function SubmissionConfirmation({ outcome, role, go, setSelectedId }: { outcome: SubmissionOutcome; role: Account["role"]; go: (screen: Screen) => void; setSelectedId: (value: string) => void }) {
  const data = outcome.data && typeof outcome.data === "object" ? outcome.data as Record<string, unknown> : {};
  const details: [string, string][] = [];
  for (const [key, label] of [["status", "Current status"], ["amount", "Amount"], ["amountApplied", "Amount applied"], ["outstandingAfter", "Outstanding after"], ["principal", "Approved principal"], ["totalRepayable", "Total repayable"], ["id", "Reference"]] as const) {
    const value = data[key];
    if (typeof value === "string" && value && (key !== "status" || !/^(PENDING|PROCESSING|SUBMITTED|RECORDED|FAILED|VERIFIED|APPROVED|REJECTED|ACTIVE|COMPLETED|DISBURSED)$/.test(value))) details.push([label, key.toLowerCase().includes("amount") || key === "principal" || key === "totalRepayable" || key === "outstandingAfter" ? money(value) : value]);
    else if (typeof value === "string" && key === "status") details.push([label, value.replace(/_/g, " ")]);
  }
  const loanId = typeof data.loanId === "string" ? data.loanId : "";
  const destination: Screen | null = outcome.path.includes("simulated-repayments") ? "loan" : outcome.path.endsWith("/verify") ? "officer-repayments" : outcome.path.includes("/decision") ? "officer-applications" : outcome.path.includes("disbursement") || outcome.path.endsWith("/record") ? role === "LOAN_OFFICER" ? "officer-loans" : null : outcome.path.startsWith("/admin/users") ? "users" : outcome.path.includes("reconciliation/run") ? "admin-chain" : null;
  return <section className="submission-confirmation" role="status" aria-live="polite">
    <div className="submission-confirmation-mark" aria-hidden="true">✓</div>
    <div className="submission-confirmation-copy"><span className="eyebrow">Saved successfully</span><h2>{outcome.title}</h2><p>{outcome.message}</p>
      {details.length > 0 && <dl className="submission-confirmation-details">{details.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>}
      {destination && <div className="submission-confirmation-actions"><Button variant="secondary" onClick={() => { if (destination === "loan" && loanId) setSelectedId(loanId); go(destination); }}>{destination === "loan" ? "View loan details" : destination === "officer-loans" ? "Open loan servicing" : destination === "officer-repayments" ? "Continue to repayment queue" : destination === "officer-applications" ? "Back to application queue" : destination === "admin-chain" ? "View blockchain monitoring" : "Manage staff accounts"}</Button></div>}
    </div>
  </section>;
}
function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) { return <div className="form-error" role="alert"><strong>Unable to complete request</strong><p>{message}</p>{onRetry && <Button variant="secondary" onClick={onRetry}>Try again</Button>}</div>; }
function CriticalErrorDialog({ message, onClose }: { message: string; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement | null>(null);
  useEffect(() => { if (dialog.current && !dialog.current.open) dialog.current.showModal(); }, []);
  const configurationHelp = message.includes("Simulated repayment verification is not configured");
  return <dialog ref={dialog} className="critical-error-dialog" aria-labelledby="critical-error-title" aria-describedby="critical-error-description" onCancel={(event) => event.preventDefault()}>
    <span className="critical-error-mark" aria-hidden="true">!</span><span className="eyebrow">Action needs attention</span><h2 id="critical-error-title">Unable to complete request</h2><p id="critical-error-description">{message}</p>
    {configurationHelp && <div className="critical-error-guidance"><strong>Repayment simulation is unavailable</strong><span>A system operator must configure <code>PESACHAIN_SIMULATION_HMAC_SECRET</code> with at least 32 UTF-8 bytes and restart the backend. No repayment was submitted or recorded.</span></div>}
    <div className="critical-error-actions"><Button variant="secondary" onClick={onClose}>Cancel</Button></div>
  </dialog>;
}
function TwinDotSpinner() { return <span className="twin-dot-spinner" aria-hidden="true"><i /><i /></span>; }
function Loading() { return <div className="loading-state" role="status" aria-live="polite"><span className="visually-hidden">Loading workspace records</span><div className="skeleton-heading"><i /><i /></div><div className="skeleton-stats" aria-hidden="true"><i /><i /><i /></div><div className="skeleton-table" aria-hidden="true"><i /><i /><i /><i /></div></div>; }

const borrowerNav: [Screen, string][] = [["dashboard", "Overview"], ["applications", "My applications"], ["apply", "Apply for a loan"], ["loans", "My loans"], ["repayments", "Repayments"], ["profile", "Profile"]];
const officerNav: [Screen, string][] = [["dashboard", "Overview"], ["officer-applications", "Application queue"], ["officer-loans", "Loan servicing"], ["officer-repayments", "Repayment events"], ["officer-events", "Blockchain status"]];
const adminNav: [Screen, string][] = [["dashboard", "Overview"], ["admin-reports", "Reports"], ["users", "Staff accounts"], ["admin-activity", "System activity"], ["admin-chain", "Blockchain monitoring"]];

function ThemeToggle({ theme, setTheme }: { theme: "light" | "dark"; setTheme: (value: "light" | "dark") => void }) {
  const next = theme === "dark" ? "light" : "dark";
  return <button type="button" className="theme-toggle" onClick={() => setTheme(next)} aria-label={`Switch to ${next} mode`} title={`Switch to ${next} mode`} aria-pressed={theme === "dark"}>
    <svg viewBox="0 0 24 24" aria-hidden="true">{theme === "dark" ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42" /></> : <path d="M20.4 15.2A8.5 8.5 0 0 1 8.8 3.6 8.6 8.6 0 1 0 20.4 15.2Z" />}</svg>
  </button>;
}

function Shell({ account, screen, go, logout, processing = false, children, theme, setTheme }: { account: Account; screen: Screen; go: (s: Screen) => void; logout: () => void; processing?: boolean; children: ReactNode; theme: "light" | "dark"; setTheme: (value: "light" | "dark") => void }) {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const nav = account.role === "BORROWER" ? borrowerNav : account.role === "LOAN_OFFICER" ? officerNav : adminNav;
  const displayName = firstName(account);
  useEffect(() => {
    if (!mobileNavOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === "Escape") setMobileNavOpen(false); };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [mobileNavOpen]);
  return <div className="app-shell" data-theme={theme}><a className="skip-link" href="#main-content">Skip to content</a><aside className={`sidebar${mobileNavOpen ? " sidebar-open" : ""}`}><div className="side-brand"><Logo /><button className="mobile-close" aria-label="Close navigation" onClick={() => setMobileNavOpen(false)}>×</button></div><div className="workspace-label">{roleLabel(account.role)}</div><nav id="workspace-navigation" aria-label="Main navigation">{nav.map(([target, label]) => <button key={target} className={screen === target ? "active" : ""} onClick={() => { setMobileNavOpen(false); go(target); }}>{label}</button>)}</nav><div className="side-profile"><div className="avatar">{displayName.slice(0, 1).toUpperCase()}</div><div><strong>{displayName}</strong><span>{account.email}</span></div></div></aside>{mobileNavOpen && <button className="mobile-nav-scrim" aria-label="Close navigation" onClick={() => setMobileNavOpen(false)} />}<div className="shell-main"><header className="topbar"><button className="menu-button" aria-label={mobileNavOpen ? "Close navigation" : "Open navigation"} aria-expanded={mobileNavOpen} aria-controls="workspace-navigation" onClick={() => setMobileNavOpen((open) => !open)}>☰</button><div className="mobile-logo"><Logo /></div><div className="topbar-meta"><span>{roleLabel(account.role)}</span><ThemeToggle theme={theme} setTheme={setTheme} /><button className="profile-settings-link" onClick={() => go("profile")}>Profile &amp; settings</button></div></header><main id="main-content" tabIndex={-1} className="content page-enter" aria-busy={processing || undefined}>{processing && <div className="request-processing" role="status" aria-live="polite"><TwinDotSpinner /> Processing your request…</div>}<div inert={processing}>{children}</div></main></div></div>;
}

function Auth({ mode, submit, error, loading, go }: { mode: "login" | "register"; submit: (body: Record<string, string>) => Promise<void>; error?: string; loading: boolean; go: (s: Screen | "register" | "login") => void }) {
  const [name, setName] = useState(""); const [email, setEmail] = useState(""); const [phone, setPhone] = useState(""); const [password, setPassword] = useState("");
  const [localError, setLocalError] = useState("");
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (mode === "register") {
      if (name.trim().length < 2) { setLocalError("Enter your name using at least 2 characters."); return; }
      if (!/^\+?[0-9][0-9 ()-]{5,30}$/.test(phone.trim())) { setLocalError("Enter a valid phone number using digits, spaces, parentheses, hyphens, and an optional leading +."); return; }
      if (new TextEncoder().encode(password).length < 12) { setLocalError("Your password must contain at least 12 characters."); return; }
    }
    setLocalError("");
    void submit(mode === "register" ? { fullName: name.trim(), email: email.trim(), phone: phone.trim(), password } : { email: email.trim(), password });
  };
  return <div className="auth-layout"><a className="skip-link" href="#auth-main">Skip to sign in</a><section className="auth-aside"><Logo /><div><span className="eyebrow">Loan management and transparency</span><h2>Clear records through every step of the loan journey.</h2><p>Borrower information stays in the operational system. Selected events have separate blockchain recording status.</p></div><small>Human-reviewed lending • Simulated repayment workflow</small></section><main id="auth-main" tabIndex={-1} className="auth-main"><Button variant="secondary" className="back-link" onClick={() => go("home")}>← Back to home</Button><div className="auth-mobile-brand"><Logo /></div><Panel className="auth-card"><span className="eyebrow">Secure account access</span><h1>{mode === "register" ? "Create borrower account" : "Welcome back"}</h1><p>{mode === "register" ? "Registration is for borrowers. Staff accounts are provisioned by an Administrator." : "Sign in with your registered email and password."}</p><form onSubmit={onSubmit} className="form-grid">{mode === "register" && <><Field label="Full name" value={name} onChange={setName} required /><Field label="Phone" value={phone} onChange={setPhone} type="tel" required hint="Include country code when needed, for example +254 7xx xxx xxx." /></>}<Field label="Email" value={email} onChange={setEmail} type="email" required /><Field label="Password" value={password} onChange={setPassword} type="password" required hint={mode === "register" ? "Use at least 12 characters. Email availability is confirmed when you submit." : undefined} />{(localError || error) && <div className="field-wide"><ErrorBox message={localError || error || "Check the highlighted information and try again."} /></div>}<div className="form-footer field-wide"><Button type="submit" loading={loading}>{loading ? "Please wait…" : mode === "register" ? "Create account" : "Sign in"}</Button></div></form><p className="auth-switch">{mode === "register" ? "Already registered?" : "New borrower?"} <button onClick={() => go(mode === "register" ? "login" : "register")}>{mode === "register" ? "Sign in" : "Create an account"}</button></p></Panel></main></div>;
}

function useScrollReveal<T extends HTMLElement>() {
  const ref = useRef<T | null>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    if (!("IntersectionObserver" in window)) { element.classList.add("is-visible"); return; }
    element.classList.add("reveal-ready");
    const observer = new IntersectionObserver(([entry]) => {
      element.classList.toggle("is-visible", Boolean(entry?.isIntersecting));
    }, { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return ref;
}

function JourneyPreviewContent() {
  return <div className="journey-preview"><div className="preview-heading"><div><span className="eyebrow">THE LOAN JOURNEY</span><h2>One record, each step visible.</h2></div><span className="preview-label">Product preview</span></div>
    <ol className="preview-steps">
      <li><span className="preview-step-index">01</span><div><strong>Application submitted</strong><small>Borrower request · private record</small></div><span className="preview-state">PENDING</span></li>
      <li><span className="preview-step-index">02</span><div><strong>Officer reviews</strong><small>Human decision · rationale retained</small></div><span className="preview-state preview-state-neutral">REVIEW</span></li>
      <li><span className="preview-step-index">03</span><div><strong>Loan serviced</strong><small>Full disbursement · repayment history</small></div><span className="preview-state preview-state-neutral">TRACKED</span></li>
      <li><span className="preview-step-index preview-step-chain">04</span><div><strong>Selected event recorded</strong><small>Separate blockchain evidence status</small></div><span className="preview-state preview-state-chain">SEPARATE</span></li>
    </ol>
    <p className="preview-footnote">Illustrative workflow only. No customer or transaction data shown.</p>
  </div>;
}

function JourneyPreviewDialog({ onClose }: { onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement | null>(null);
  useEffect(() => { if (dialog.current && !dialog.current.open) dialog.current.showModal(); }, []);
  return <dialog ref={dialog} className="journey-dialog" aria-labelledby="journey-dialog-title" onClose={onClose} onClick={(event) => { if (event.target === event.currentTarget) dialog.current?.close(); }}>
    <div className="journey-dialog-top"><span className="eyebrow" id="journey-dialog-title">PesaChain · Product preview</span><button type="button" className="journey-dialog-close" aria-label="Close loan journey preview" autoFocus onClick={() => dialog.current?.close()}>×</button></div>
    <JourneyPreviewContent />
  </dialog>;
}

function LandingPage({ go }: { go: (screen: Screen | "home" | "login" | "register") => void }) {
  const rolesReveal = useScrollReveal<HTMLElement>();
  const evidenceReveal = useScrollReveal<HTMLElement>();
  const [journeyOpen, setJourneyOpen] = useState(false);
  const [navVisible, setNavVisible] = useState(true);
  useEffect(() => {
    let idleTimer = 0;
    const onScroll = () => {
      if (window.scrollY < 24) { setNavVisible(true); return; }
      setNavVisible(false);
      window.clearTimeout(idleTimer);
      idleTimer = window.setTimeout(() => setNavVisible(true), 240);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => { window.removeEventListener("scroll", onScroll); window.clearTimeout(idleTimer); };
  }, []);
  const links = [
    ["#workflow", "How it works"],
    ["#roles", "For each role"],
    ["#evidence", "Data and evidence"],
    ["#questions", "FAQs"],
  ];
  return <div className="landing"><a className="skip-link" href="#main-content">Skip to content</a>
    <header className={`public-nav${navVisible ? "" : " public-nav-hidden"}`}>
      <a className="brand-link" href="#main-content" aria-label="PesaChain home"><Logo /></a>
      <nav aria-label="Landing page navigation">{links.map(([href, label]) => <a key={href} href={href}>{label}</a>)}</nav>
      <details className="landing-menu"><summary aria-label="Open page navigation">Explore</summary><div>{links.map(([href, label]) => <a key={href} href={href} onClick={(event) => event.currentTarget.closest("details")?.removeAttribute("open")}>{label}</a>)}<a href="#sign-in" onClick={(event) => { event.preventDefault(); event.currentTarget.closest("details")?.removeAttribute("open"); go("login"); }}>Sign in</a></div></details>
      <div className="landing-actions"><Button variant="ghost" onClick={() => go("login")}>Sign in</Button><Button onClick={() => go("register")}>Create account</Button></div>
    </header>
    <main id="main-content" tabIndex={-1}>
      <section className="hero">
        <div className="hero-copy">
          <div className="trust-pill">Loan management · Verifiable event records</div>
          <h1>A clearer way to manage every step of a <em>loan.</em></h1>
          <p>Bring applications, human review, disbursement, and repayments into one structured workflow. Selected financial events can be independently checked without putting borrower details on-chain.</p>
          <div className="hero-actions"><Button onClick={() => go("register")}>Create borrower account</Button><Button variant="secondary" onClick={() => go("login")}>Sign in</Button><Button variant="secondary" className="journey-preview-trigger" onClick={() => setJourneyOpen(true)}>Preview the loan journey <span aria-hidden="true">↗</span></Button></div>
          <div className="hero-proof"><span>Loan Officers make lending decisions</span><span>Simulated repayments · No money transferred</span></div>
        </div>
        <div className="hero-visual" aria-label="Illustrative PesaChain loan lifecycle preview"><JourneyPreviewContent /></div>
      </section>
      {journeyOpen && <JourneyPreviewDialog onClose={() => setJourneyOpen(false)} />}

      <section className="how landing-section" id="workflow" aria-labelledby="workflow-title">
        <div className="section-heading"><span className="eyebrow">A considered process</span><h2 id="workflow-title">From request to repayment, with clear ownership at each step.</h2><p>People make lending decisions. PesaChain records the operational workflow and separately tracks selected event evidence.</p></div>
        <ol className="how-grid">{[["01", "Apply", "A borrower submits a requested KES amount, purpose, and term."], ["02", "Review", "A Loan Officer reviews the application and records a reasoned decision."], ["03", "Disburse", "An approved loan is disbursed once, in full, at its approved principal."], ["04", "Repay and verify", "A simulated repayment is reviewed by an Officer before the official balance changes."]].map(([n, title, text]) => <li className="workflow-step" key={n}><span>{n}</span><h3>{title}</h3><p>{text}</p></li>)}</ol>
      </section>

      <section ref={rolesReveal} className="roles-section landing-section scroll-reveal" id="roles" aria-labelledby="roles-title">
        <div className="roles-intro"><span className="eyebrow">Built around real responsibilities</span><h2 id="roles-title">A clear workspace for each part of the process.</h2><p>Access follows the work people are authorized to do. Administrators manage accounts and monitor activity; lending decisions stay with Loan Officers.</p></div>
        <div className="role-list">
          <article><span className="role-number">01</span><div><h3>Borrowers</h3><p>Submit an application, follow its decision, review loan terms, and view your own repayment and event records.</p></div><Button variant="secondary" className="role-action" onClick={() => go("register")}>Create an account</Button></article>
          <article><span className="role-number">02</span><div><h3>Loan Officers</h3><p>Review applications, record decisions, disburse approved loans in full, and verify and record simulated repayments.</p></div><span className="role-note">Staff account provisioned by an Administrator</span></article>
          <article><span className="role-number">03</span><div><h3>Administrators</h3><p>Provision and manage staff access, review permitted audit activity, and monitor blockchain event delivery.</p></div><span className="role-note">Administrative access does not grant lending authority</span></article>
        </div>
      </section>

      <section ref={evidenceReveal} className="evidence-section landing-section scroll-reveal" id="evidence" aria-labelledby="evidence-title">
        <div className="evidence-copy"><span className="eyebrow">Operational record + selected evidence</span><h2 id="evidence-title">Blockchain is a record layer. It is not a payment rail.</h2><p>PostgreSQL remains the source of truth for applications, borrower information, loan balances, and repayment details. Selected financial events are submitted separately for confirmation.</p><p>A confirmed blockchain event shows that an operation was recorded under the contract rules. It does not prove that real-world money moved. Repayment events are simulations; PesaChain does not move funds.</p><a className="text-link" href="#questions">Read common questions <span aria-hidden="true">→</span></a></div>
        <div className="evidence-visual"><div><span className="evidence-tag">OPERATIONAL DATABASE</span><strong>Private loan records</strong><p>Applications · terms · balances · repayment history</p></div><span className="evidence-connector" aria-hidden="true">→</span><div className="evidence-chain"><span className="evidence-tag">SEPARATE EVENT RECORD</span><strong>Selected evidence</strong><p>Opaque reference · event type · approved amount</p></div><small>Personal details and free-text notes stay off-chain.</small></div>
      </section>

      <section className="faq-section landing-section" id="questions" aria-labelledby="questions-title"><div className="section-heading"><span className="eyebrow">Useful to know</span><h2 id="questions-title">Questions, answered plainly.</h2></div><div className="faq-list">
        <details><summary>Who reviews loan applications?</summary><p>An authorized Loan Officer reviews each application and records the decision and rationale. PesaChain does not make automated credit decisions.</p></details>
        <details><summary>How are repayments handled?</summary><p>The borrower initiates a clearly labeled local simulation. A Loan Officer verifies the event and records the official repayment. Creating a simulation alone does not change the loan balance.</p></details>
        <details><summary>What information is recorded on the blockchain?</summary><p>Only selected event evidence is recorded. Borrower identity, application details, decision notes, schedules, and detailed operational records remain in the database.</p></details>
        <details><summary>Does a blockchain record mean money was transferred?</summary><p>No. Repayment events in this system are simulations. A blockchain confirmation proves the on-chain event was recorded under the contract rules, not that funds moved in the real world.</p></details>
        <details><summary>Why do loan and blockchain statuses differ?</summary><p>Loan status describes the operational lifecycle. Blockchain status describes the separate delivery and confirmation of selected evidence. A pending or failed blockchain event does not undo a committed operational action.</p></details>
        <details><summary>How do staff access PesaChain?</summary><p>Borrowers can register publicly. Administrators provision Loan Officer and Administrator accounts; staff must use their provisioned credentials.</p></details>
      </div></section>

      <section className="closing-cta"><div><span className="eyebrow">A structured loan journey</span><h2>Start with a clearer view of the process.</h2><p>Create a borrower account to submit and follow your application.</p></div><div className="hero-actions"><Button onClick={() => go("register")}>Create borrower account</Button><Button variant="secondary" onClick={() => go("login")}>Sign in</Button></div></section>
      <section className="contact-section landing-section" id="contact" aria-labelledby="contact-title"><div><span className="eyebrow">Contact PesaChain</span><h2 id="contact-title">Questions about the platform?</h2><p>Reach the project contact directly for support or a demonstration inquiry.</p></div><div className="contact-options"><a href="mailto:jamesbriandungu@gmail.com"><span>Email</span><strong>jamesbriandungu@gmail.com</strong></a><a href="tel:+254794523302"><span>Phone</span><strong>0794523302</strong></a><div><span>Location</span><strong>Njoro, Nakuru County</strong></div></div></section>
    </main>
    <footer className="landing-footer"><div className="footer-brand"><Logo /><p>Loan applications and servicing with selected, separately verifiable event records.</p></div><div className="footer-links"><a href="#workflow">How it works</a><a href="#roles">For each role</a><a href="#evidence">Data and evidence</a><a href="#questions">FAQs</a><a href="#contact">Contact</a><button onClick={() => go("login")}>Sign in</button></div><div className="footer-note"><span>Human-reviewed lending · Simulated repayment workflow</span><span>© {new Date().getFullYear()} PesaChain</span></div></footer>
  </div>;
}

function NotFoundPage() {
  return <main className="not-found-page"><div className="not-found-brand"><Logo /></div><section className="not-found-content"><div className="not-found-art" aria-hidden="true"><span className="not-found-orbit orbit-a" /><span className="not-found-orbit orbit-b" /><div className="not-found-ledger"><i /><i /><i /></div><span className="not-found-node node-a" /><span className="not-found-node node-b" /><span className="not-found-node node-c" /></div><span className="eyebrow">404 · Record not found</span><h1>This page isn’t in the loan ledger.</h1><p>The address may have changed, or this record may not exist. PesaChain keeps loan activity traceable, but this route isn’t part of the current application.</p><a className="btn btn-primary" href={import.meta.env.BASE_URL}>Return to PesaChain</a></section><footer>Loan management and transparency · PesaChain</footer></main>;
}

const errorDetail = (reason: unknown) => reason instanceof Error ? reason.message : "The service could not return this information. Try again.";

export default function IntegratedApp() {
  const [account, setAccount] = useState<Account | null>(null); const [screen, setScreen] = useState<Screen | "home" | "login" | "register">("home");
  const [theme, setThemeState] = useState<"light" | "dark">(() => { try { return localStorage.getItem("pesachain.dashboard-theme") === "dark" ? "dark" : "light"; } catch { return "light"; } });
  const setTheme = useCallback((value: "light" | "dark") => { setThemeState(value); try { localStorage.setItem("pesachain.dashboard-theme", value); } catch { /* Theme remains available for this session. */ } }, []);
  const [selectedId, setSelectedId] = useState(""); const [payload, setPayload] = useState<unknown>(null); const [loading, setLoading] = useState(false); const [processing, setProcessing] = useState(false); const [authLoading, setAuthLoading] = useState(false); const [error, setError] = useState(""); const [criticalError, setCriticalError] = useState(""); const [refresh, setRefresh] = useState(0); const [activityPage, setActivityPage] = useState(0); const [activitySearchText, setActivitySearchText] = useState(""); const [activitySearch, setActivitySearch] = useState(""); const [activityActorRole, setActivityActorRole] = useState(""); const [activityResult, setActivityResult] = useState(""); const [officerSearchText, setOfficerSearchText] = useState(""); const [officerSearch, setOfficerSearch] = useState(""); const [toast, setToast] = useState(""); const [lastOutcome, setLastOutcome] = useState<SubmissionOutcome | null>(null);
  const mutationInFlight = useRef(false);
  useEffect(() => {
    document.title = "PesaChain — Loan management and event transparency";
    document.querySelector('meta[name="description"]')?.setAttribute("content", "Manage loan applications, human-reviewed decisions, disbursement, and simulated repayments in one structured workflow. Selected financial events have separate blockchain records; borrower information stays in the operational database.");
  }, []);
  useEffect(() => { if (!toast) return; const timer = window.setTimeout(() => setToast(""), 4200); return () => window.clearTimeout(timer); }, [toast]);
  useEffect(() => { const timer = window.setTimeout(() => { const value = officerSearchText.trim(); setOfficerSearch(value.length >= 2 ? value : ""); }, 300); return () => window.clearTimeout(timer); }, [officerSearchText]);
  useEffect(() => { const timer = window.setTimeout(() => { const value = activitySearchText.trim(); setActivitySearch(value.length >= 2 ? value : ""); }, 300); return () => window.clearTimeout(timer); }, [activitySearchText]);
  const go = useCallback((next: Screen | "home" | "login" | "register") => { if (!["application", "officer-application", "loan", "repayments"].includes(next)) setSelectedId(""); setLastOutcome((previous) => previous?.path === "/applications" && next === "application" ? previous : null); setScreen(next); setPayload(null); setError(""); window.scrollTo({ top: 0, behavior: "smooth" }); }, []);
  useEffect(() => { setUnauthorizedHandler(() => { setAccount(null); setScreen("login"); setPayload(null); }); return () => setUnauthorizedHandler(); }, []);
  useEffect(() => { let alive = true; api<Account>("/auth/me").then((me) => { if (alive) { setAccount(me); setScreen(me.passwordChangeRequired ? "password" : "dashboard"); } }).catch((err) => { if (alive && (!(err instanceof ApiError) || err.problem.status !== 401)) setError(err instanceof Error ? err.message : "Session could not be checked."); }); return () => { alive = false; }; }, []);
  const expectedPath = new URL(import.meta.env.BASE_URL, window.location.origin).pathname.replace(/\/+$/, "") || "/";
  const currentPath = window.location.pathname.replace(/\/+$/, "") || "/";

  const load = useCallback(async (path: string) => { setLoading(true); setError(""); try { const result = await api<unknown>(path); setPayload(result); } catch (err) { setPayload(null); setError(err instanceof Error ? err.message : "The request could not be completed."); } finally { setLoading(false); } }, []);
  useEffect(() => {
    if (!account || screen === "home" || screen === "login" || screen === "register" || screen === "apply" || screen === "password") return;
    const task = async () => {
      setLoading(true); setError(""); setPayload(null);
      try {
        let result: unknown;
        switch (screen) {
          case "dashboard":
            if (account.role === "BORROWER") { const [apps, loans] = await Promise.all([api<Page<Application>>("/applications/mine"), api<Page<Loan>>("/loans/mine")]); result = { apps, loans }; }
            else if (account.role === "LOAN_OFFICER") {
              const [applications, repayments] = await Promise.allSettled([
                api<Page<OfficerApplication>>("/officer/applications"),
                api<RepaymentEvent[]>("/officer/repayment-events?status=PENDING"),
              ]);
              result = {
                applications: applications.status === "fulfilled" ? applications.value : null,
                repayments: repayments.status === "fulfilled" ? repayments.value : null,
                applicationsError: applications.status === "rejected" ? errorDetail(applications.reason) : null,
                repaymentsError: repayments.status === "rejected" ? errorDetail(repayments.reason) : null,
              };
            }
            else {
              const [events, activity, staff, reports] = await Promise.allSettled([
                api<Page<ChainEvent>>("/admin/blockchain-events"),
                api<Page<Activity>>("/admin/activity"),
                api<Staff[]>("/admin/users"),
                api<AdminReport>("/admin/reports/overview"),
              ]);
              result = {
                events: events.status === "fulfilled" ? events.value : null,
                activity: activity.status === "fulfilled" ? activity.value : null,
                staff: staff.status === "fulfilled" ? staff.value : null,
                reports: reports.status === "fulfilled" ? reports.value : null,
                eventsError: events.status === "rejected" ? errorDetail(events.reason) : null,
                activityError: activity.status === "rejected" ? errorDetail(activity.reason) : null,
                staffError: staff.status === "rejected" ? errorDetail(staff.reason) : null,
                reportsError: reports.status === "rejected" ? errorDetail(reports.reason) : null,
              };
            }
            break;
          case "profile": result = await api(account.role === "BORROWER" ? "/borrowers/me/profile" : "/auth/me"); break;
          case "applications": {
            const [applications, loans] = await Promise.all([api<Page<Application>>("/applications/mine"), api<Page<Loan>>("/loans/mine")]);
            const lifecycleByApplication = new Map(loans.items.map((loan) => [loan.applicationId, loan.status]));
            result = { ...applications, items: applications.items.map((application) => ({ ...application, loanStatus: lifecycleByApplication.get(application.id) })) };
            break;
          }
          case "application": result = await api(account.role === "LOAN_OFFICER" ? `/officer/applications/${selectedId}` : `/applications/${selectedId}`); break;
           case "officer-application": {
             const application = await api<OfficerApplication>(`/officer/applications/${selectedId}`);
             const history = await api<BorrowerHistory>(`/officer/borrowers/${application.borrowerId}/history`).then((value) => ({ value, error: null as string | null })).catch((reason: unknown) => ({ value: null, error: errorDetail(reason) }));
             result = { application, history: history.value, historyError: history.error }; break;
           }
          case "loans": result = await api<Page<Loan>>("/loans/mine"); break;
          case "loan": {
            const id = selectedId || (await api<Page<Loan>>("/loans/mine")).items[0]?.id;
            if (!id) { result = null; break; }
            setSelectedId(id); const [loan, events, repayments] = await Promise.all([api<Loan>(`/loans/${id}`), api<ChainEvent[]>(`/loans/${id}/blockchain-events`), api<Repayment[]>(`/loans/${id}/repayments`)]); result = { loan, events, repayments }; break;
          }
          case "repayments": {
            const loansPage = await api<Page<Loan>>("/loans/mine");
            const selectedLoan = loansPage.items.find((loan) => loan.id === selectedId);
            const chosenLoan = selectedLoan ?? loansPage.items.find((item) => item.status === "ACTIVE") ?? loansPage.items.find((item) => item.status !== "COMPLETED") ?? loansPage.items[0];
            if (!chosenLoan) { result = { loan: null, loans: loansPage.items, events: [], repayments: [], simulations: [] }; break; }
            const id = chosenLoan.id;
            setSelectedId(id); const [loan, events, repayments, simulations] = await Promise.all([api<Loan>(`/loans/${id}`), api<ChainEvent[]>(`/loans/${id}/blockchain-events`), api<Repayment[]>(`/loans/${id}/repayments`), api<RepaymentEvent[]>(`/loans/${id}/simulated-repayments`)]); result = { loan, events, repayments, simulations }; break;
          }
          case "officer-applications": { const query = officerSearch ? `?q=${encodeURIComponent(officerSearch)}` : ""; result = await api<Page<OfficerApplication>>(`/officer/applications${query}`); break; }
          case "officer-loans": result = await api<Page<Loan>>("/loans"); break;
          case "officer-repayments": { const [pending, verified] = await Promise.all([api<RepaymentEvent[]>("/officer/repayment-events?status=PENDING"), api<RepaymentEvent[]>("/officer/repayment-events?status=VERIFIED")]); result = [...pending, ...verified]; break; }
          case "officer-events": result = await api<Page<ChainEvent>>("/officer/blockchain-events"); break;
          case "users": result = await api<Staff[]>("/admin/users"); break;
          case "admin-chain": { const [events, findings] = await Promise.allSettled([api<Page<ChainEvent>>("/admin/blockchain-events"), api<unknown[]>("/admin/blockchain-reconciliation/findings")]); result = { events: events.status === "fulfilled" ? events.value : null, findings: findings.status === "fulfilled" ? findings.value : null, eventsError: events.status === "rejected" ? errorDetail(events.reason) : null, findingsError: findings.status === "rejected" ? errorDetail(findings.reason) : null }; break; }
           case "admin-activity": { const query = new URLSearchParams({ page: String(activityPage), size: "20" }); if (activitySearch) query.set("q", activitySearch); if (activityActorRole) query.set("actorRole", activityActorRole); if (activityResult) query.set("result", activityResult); result = await api<Page<Activity>>(`/admin/activity?${query}`); break; }
          case "admin-reports": result = await api<AdminReport>("/admin/reports/overview?months=6"); break;
          default: result = null;
        }
        setPayload(result);
      } catch (err) { setError(err instanceof Error ? err.message : "The request could not be completed."); }
      finally { setLoading(false); }
    };
    void task();
  }, [account, screen, selectedId, refresh, activityPage, activitySearch, activityActorRole, activityResult, officerSearch]);

  const authSubmit = async (body: Record<string, string>) => {
    setAuthLoading(true); setError(""); try { const result = await api<Account>(screen === "register" ? "/auth/register" : "/auth/login", { method: "POST", body: JSON.stringify(body) }); clearCsrf(); setAccount(result); setScreen(result.passwordChangeRequired ? "password" : "dashboard"); } catch (err) { setError(err instanceof Error ? err.message : "Sign-in could not be completed."); } finally { setAuthLoading(false); }
  };
  const mutate = async <T,>(path: string, method: string, body?: unknown, extraHeaders?: Record<string, string>) => { if (mutationInFlight.current) throw new Error("A request is already being processed. Please wait."); mutationInFlight.current = true; setProcessing(true); setError(""); try { const result = await api<T>(path, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }), headers: extraHeaders }); setRefresh((value) => value + 1); const success = path.includes("/decision") ? "Application decision saved. The application below is refreshed with the recorded terms." : path.includes("/disbursement") ? "Full disbursement recorded. The loan is now active and ready for borrower repayments." : path.includes("simulated-repayments") ? "Repayment simulation submitted. The official balance stays unchanged until an Officer verifies and records it." : path.includes("repayment-events") && path.endsWith("/verify") ? "Simulation verified. It is now ready for official repayment recording." : path.includes("repayment-events") && path.endsWith("/record") ? "Official repayment recorded. The loan balance and lifecycle have been updated." : path === "/admin/users" && method.toUpperCase() === "POST" ? "Staff account created." : path.startsWith("/admin/users/") ? "Staff account changes saved." : path.includes("reconciliation/run") ? "Blockchain reconciliation completed." : path.includes("profile") ? "Profile changes saved." : "Changes saved."; const outcomeTitle = path.includes("/decision") ? "Application decision recorded" : path.includes("/disbursement") ? "Loan fully disbursed" : path.includes("simulated-repayments") ? "Repayment simulation created" : path.endsWith("/verify") ? "Simulation verified" : path.endsWith("/record") ? "Official repayment recorded" : path === "/admin/users" ? "Staff account created" : path.startsWith("/admin/users/") ? "Staff account updated" : path.includes("reconciliation/run") ? "Reconciliation requested" : path.includes("profile") ? "Profile updated" : "Changes saved"; setLastOutcome({ title: outcomeTitle, message: success, path, data: result }); setToast(success); return result; } catch (err) { const message = err instanceof Error ? err.message : "The request could not be completed."; setError(message); setCriticalError(message); throw err; } finally { mutationInFlight.current = false; setProcessing(false); } };
  const logout = async () => { try { await api<void>("/auth/logout", { method: "POST" }); } catch { /* Local session state is cleared even if it already expired. */ } clearCsrf(); setAccount(null); go("home"); };

  if (currentPath !== expectedPath) return <NotFoundPage />;
  if (screen === "home") return <LandingPage go={go} />;
  if (screen === "login" || screen === "register") return <Auth mode={screen} submit={authSubmit} error={error} loading={authLoading} go={go} />;
  if (!account) return <div className="auth-main"><Panel><h1>Sign in required</h1><p>{error || "Please sign in to access PesaChain."}</p><Button onClick={() => go("login")}>Sign in</Button></Panel></div>;
  if (account.passwordChangeRequired || screen === "password") return <Shell account={account} screen="password" go={go} logout={logout} processing={processing} theme={theme} setTheme={setTheme}><PasswordChange save={async (current, next) => { const updated = await mutate<Account>("/auth/change-password", "POST", { currentPassword: current, newPassword: next }); setAccount(updated); go("dashboard"); }} error={error} loading={loading || processing} /></Shell>;

  const content = <>{lastOutcome && <SubmissionConfirmation outcome={lastOutcome} role={account.role} go={go} setSelectedId={setSelectedId} />}{error && <ErrorBox message={error} onRetry={() => setRefresh((v) => v + 1)} />}{loading ? <Loading /> : error && payload === null ? null : renderPage({ account, screen, payload, selectedId, setSelectedId, go, mutate, refreshNow: () => setRefresh((v) => v + 1), setActivityPage, activitySearchText, setActivitySearchText, activitySearch, activityActorRole, setActivityActorRole, activityResult, setActivityResult, officerSearchText, setOfficerSearchText, officerSearch, setOfficerSearch, error, theme, setTheme, logout })}</>;
  return <Shell account={account} screen={screen as Screen} go={(s) => { if ((account.role === "BORROWER" && ["officer-applications", "officer-application", "officer-repayments", "officer-events", "officer-loans", "users", "admin-chain", "admin-activity", "admin-reports"].includes(s)) || (account.role === "LOAN_OFFICER" && ["users", "admin-chain", "admin-activity", "admin-reports"].includes(s)) || (account.role === "ADMINISTRATOR" && ["applications", "apply", "application", "loans", "loan", "repayments", "officer-applications", "officer-application", "officer-repayments", "officer-events", "officer-loans"].includes(s))) return; go(s); }} logout={logout} processing={processing} theme={theme} setTheme={setTheme}>{criticalError && <CriticalErrorDialog message={criticalError} onClose={() => setCriticalError("")} />}{toast && <div className="toast-stack"><div className="action-toast" role="status" aria-live="polite"><span className="toast-check" aria-hidden="true">✓</span><span>{toast}</span><button type="button" aria-label="Dismiss notification" onClick={() => setToast("")}>×</button></div></div>}{content}</Shell>;
}

function MiniBarChart({ title, items }: { title: string; items: { label: string; value: number; tone?: string }[] }) {
  const max = Math.max(1, ...items.map((item) => item.value));
  return <section className="report-bars" aria-label={title}><h3>{title}</h3>{items.map((item) => <div className="report-bar-row" key={item.label}><div><span>{item.label}</span><strong>{item.value.toLocaleString("en-KE")}</strong></div><div className="report-bar-track"><span className={item.tone ?? ""} style={{ width: `${Math.max(item.value > 0 ? 2 : 0, item.value / max * 100)}%` }} /></div></div>)}</section>;
}

function TrendChart({ report }: { report: AdminReport }) {
  const max = Math.max(1, ...report.monthlyTrend.flatMap((point) => [point.applications, point.repayments]));
  return <div className="trend-chart-scroll"><svg className="trend-chart" viewBox="0 0 720 250" role="img" aria-label="Applications and official repayments by month">
    <line x1="40" y1="205" x2="700" y2="205" className="chart-axis" />
    {report.monthlyTrend.map((point, index) => { const group = 55 + index * 106; const a = point.applications / max * 145; const r = point.repayments / max * 145; const month = new Date(`${point.month}T00:00:00`).toLocaleDateString("en-KE", { month: "short" }); return <g key={point.month}><rect x={group} y={205 - a} width="27" height={a} rx="6" className="chart-applications" /><rect x={group + 32} y={205 - r} width="27" height={r} rx="6" className="chart-repayments" /><text x={group + 29} y="229" textAnchor="middle" className="chart-label">{month}</text></g>; })}
    </svg><div className="chart-legend"><span><i className="chart-applications" />Applications submitted</span><span><i className="chart-repayments" />Official repayments recorded</span></div></div>;
}

function exportAdminReport(report: AdminReport) {
  downloadPesaChainPdf({ title: "Operational portfolio report", category: "Administrator report · Persisted aggregate data", reference: `REPORT-${new Date(report.generatedAt).toISOString().slice(0, 10)}`, issuedAt: date(report.generatedAt), filename: `pesachain-report-${new Date(report.generatedAt).toISOString().slice(0, 10)}.pdf`, note: "Aggregated operational figures only. No borrower details or schedule-based repayment conclusions are included.", groups: [
    { title: `Applications · ${report.months}-month window`, rows: [{ label: "Total applications", value: String(report.applications.total) }, { label: "Awaiting review", value: String(report.applications.pending) }, { label: "Approved", value: String(report.applications.approved) }, { label: "Rejected", value: String(report.applications.rejected) }] },
    { title: "Loan portfolio and repayments", rows: [{ label: "Loan records", value: String(report.loans.total) }, { label: "Approved · awaiting disbursement", value: String(report.loans.approved) }, { label: "Active", value: String(report.loans.active) }, { label: "Completed", value: String(report.loans.completed) }, { label: "Approved principal", value: money(report.loans.approvedPrincipal) }, { label: "Contractual total", value: money(report.loans.contractualTotal) }, { label: "Current outstanding", value: money(report.loans.outstandingBalance) }, { label: "Official repayment count", value: String(report.loans.repaymentCount) }, { label: "Amount officially recorded", value: money(report.loans.amountRepaid) }] },
    { title: "Audit and blockchain delivery", rows: [{ label: "Audit events · last 30 days", value: String(report.activity.last30Days) }, { label: "Failed audit events · last 30 days", value: String(report.activity.failedLast30Days) }, ...Object.entries(report.blockchainEvents).map(([status, count]) => ({ label: `Blockchain ${status.toLowerCase()}`, value: String(count) }))] },
    { title: "Monthly operational trend", rows: report.monthlyTrend.map((item) => ({ label: item.month, value: `${item.applications} applications · ${item.decisions} decisions · ${item.disbursements} disbursements · ${item.repayments} repayments` })) },
  ] });
}

function downloadRepaymentReceipt(repayment: { repaymentId?: string; id?: string; eventId?: string; loanId: string; amountApplied: string; outstandingAfter: string; recordedAt: string }) {
  const ref = repayment.repaymentId ?? repayment.id ?? "Unavailable";
  downloadPesaChainPdf({ title: "Repayment record", category: "Official PesaChain entry · Simulated payment", reference: ref, issuedAt: date(repayment.recordedAt), filename: `pesachain-repayment-${ref}.pdf`, note: "This confirms an official repayment entry in PesaChain. It is not proof of a real-world transfer; no funds moved. Not digitally signed.", groups: [
    { title: "Recorded repayment", rows: [{ label: "Repayment reference", value: ref }, { label: "Simulation event reference", value: repayment.eventId ?? "Unavailable" }, { label: "Loan reference", value: repayment.loanId }, { label: "Amount officially applied", value: money(repayment.amountApplied) }, { label: "Outstanding after recording", value: money(repayment.outstandingAfter) }, { label: "Recorded at", value: date(repayment.recordedAt) }] },
  ] });
}

function AdminReportsPage({ report, error, retry, go }: { report: AdminReport | null; error?: string; retry: () => void; go: PageContext["go"] }) {
  if (error) return <><Header title="Operational reports" description="Aggregated system and loan activity from persisted records." /><ErrorBox message={error} onRetry={retry} /></>;
  if (!report) return <><Header title="Operational reports" description="Aggregated system and loan activity from persisted records." /><Loading /></>;
  const loanStatuses = [
    { label: "Approved · awaiting disbursement", value: report.loans.approved, tone: "bar-champagne" },
    { label: "Active", value: report.loans.active, tone: "bar-emerald" },
    { label: "Completed", value: report.loans.completed, tone: "bar-muted" },
  ];
  const chainStatuses = Object.entries(report.blockchainEvents).map(([label, value]) => ({ label: label.replace(/_/g, " "), value, tone: label === "FAILED" ? "bar-danger" : label === "RECORDED" ? "bar-emerald" : "bar-champagne" }));
  return <><Header title="Operational reports" description="Aggregated activity from persisted records. No borrower identities or personal details are included." actions={<Button variant="secondary" onClick={() => exportAdminReport(report)}>Download PDF report</Button>} />
    <div className="report-metrics">
      <Panel className="report-metric"><span>Applications</span><strong>{report.applications.total}</strong><small>{report.applications.pending} pending review</small></Panel>
      <Panel className="report-metric"><span>Loans in portfolio</span><strong>{report.loans.total}</strong><small>{report.loans.active} active · {report.loans.completed} completed</small></Panel>
      <Panel className="report-metric"><span>Outstanding balance</span><strong>{money(report.loans.outstandingBalance)}</strong><small>Operational balance across recorded loans</small></Panel>
      <Panel className="report-metric"><span>Officially repaid</span><strong>{money(report.loans.amountRepaid)}</strong><small>{report.loans.repaymentCount} recorded repayments · simulated workflow</small></Panel>
    </div>
    <Panel className="report-panel"><div className="records-intro"><div><span className="eyebrow">Portfolio movement</span><h2>Applications and recorded repayments</h2></div><span>Last {report.months} months</span></div><TrendChart report={report} /><p className="report-footnote">Counts reflect persisted records by calendar month. Repayments are simulated and do not represent money transfers.</p></Panel>
    <div className="report-grid"><Panel className="report-panel"><div className="records-intro"><div><span className="eyebrow">Loan lifecycle</span><h2>Current loan states</h2></div><strong>{report.loans.total} total</strong></div><MiniBarChart title="Loans by operational status" items={loanStatuses} /><div className="report-total-row"><span>Approved principal volume</span><strong>{money(report.loans.approvedPrincipal)}</strong></div><div className="report-total-row"><span>Contractual total repayable</span><strong>{money(report.loans.contractualTotal)}</strong></div></Panel>
      <Panel className="report-panel"><div className="records-intro"><div><span className="eyebrow">Event delivery</span><h2>Blockchain recording state</h2></div></div><MiniBarChart title="Outbox events by delivery status" items={chainStatuses} /><div className="report-total-row"><span>Recorded with required confirmation</span><strong>{report.blockchainEvents.RECORDED ?? 0}</strong></div><div className="report-total-row"><span>Delivery needs attention</span><strong>{report.blockchainEvents.FAILED ?? 0}</strong></div></Panel></div>
    <Panel className="report-panel"><div className="records-intro"><div><span className="eyebrow">System operations</span><h2>Activity and review outcomes</h2></div><Button variant="secondary" onClick={() => go("admin-activity")}>Open audit activity</Button></div><div className="report-health"><div><span>Audit events · 30 days</span><strong>{report.activity.last30Days}</strong></div><div><span>Failed audit events · 30 days</span><strong>{report.activity.failedLast30Days}</strong></div><div><span>Approved applications</span><strong>{report.applications.approved}</strong></div><div><span>Rejected applications</span><strong>{report.applications.rejected}</strong></div></div><p className="report-footnote">This report contains aggregate counts and amounts only. It does not calculate credit scores, overdue rates, or on-time repayment performance.</p></Panel>
    <p className="report-generated">Report data generated {date(report.generatedAt)}.</p>
  </>;
}

function PasswordChange({ save, error, loading }: { save: (current: string, next: string) => Promise<void>; error: string; loading: boolean }) {
  const [current, setCurrent] = useState(""); const [next, setNext] = useState(""); const [confirm, setConfirm] = useState(""); const [localError, setLocalError] = useState("");
  const submit = (e: FormEvent) => { e.preventDefault(); if (next !== confirm) { setLocalError("The new passwords do not match."); return; } if (new TextEncoder().encode(next).length < 12) { setLocalError("Use at least 12 characters."); return; } setLocalError(""); void save(current, next); };
  return <><Header title="Change your temporary password" description="Choose a new password before accessing staff tools." /><Panel><form className="form-grid" onSubmit={submit}><Field label="Current password" type="password" value={current} onChange={setCurrent} required /><Field label="New password" type="password" value={next} onChange={setNext} required /><Field label="Confirm new password" type="password" value={confirm} onChange={setConfirm} required />{(error || localError) && <p role="alert" className="field-error field-wide">{localError || error}</p>}<div className="field-wide"><Button type="submit" loading={loading}>{loading ? "Saving…" : "Change password"}</Button></div></form></Panel></>;
}

type PageContext = { account: Account; screen: Screen | "password"; payload: unknown; selectedId: string; setSelectedId: (id: string) => void; go: (s: Screen | "home" | "login" | "register") => void; mutate: <T,>(path: string, method: string, body?: unknown, headers?: Record<string, string>) => Promise<T>; refreshNow: () => void; setActivityPage: React.Dispatch<React.SetStateAction<number>>; activitySearchText: string; setActivitySearchText: (value: string) => void; activitySearch: string; activityActorRole: string; setActivityActorRole: (value: string) => void; activityResult: string; setActivityResult: (value: string) => void; officerSearchText: string; setOfficerSearchText: (value: string) => void; officerSearch: string; setOfficerSearch: (value: string) => void; error: string; theme: "light" | "dark"; setTheme: (value: "light" | "dark") => void; logout: () => void };

function renderPage(ctx: PageContext): ReactNode {
  const { account, screen, payload, selectedId, setSelectedId, go, mutate, refreshNow, setActivityPage, activitySearchText, setActivitySearchText, activitySearch, activityActorRole, setActivityActorRole, activityResult, setActivityResult, officerSearchText, setOfficerSearchText, officerSearch, setOfficerSearch } = ctx;
  if (screen === "dashboard" && account.role === "BORROWER") {
    const data = payload as { apps: Page<Application>; loans: Page<Loan> } | null;
    if (!data) return <><Header title={<WelcomeTitle account={account} />} description="Your current PesaChain records will appear here." />{ctx.error ? <ErrorBox message={ctx.error} onRetry={refreshNow} /> : <Empty title="No application or loan records yet" detail="When you submit an application, its status and any decision will appear here." action={<Button onClick={() => go("apply")}>Start an application</Button>} />}</>;
    const active = data.loans.items.find((loan) => loan.status === "ACTIVE") ?? data.loans.items.find((loan) => loan.status === "APPROVED") ?? data.loans.items[0];
    return <><Header title={<WelcomeTitle account={account} />} description="A current view of your persisted applications and loan records." actions={<Button onClick={() => go("apply")}>Apply for a loan</Button>} /><div className="stats"><Panel className="live-stat"><div><span>Applications</span><small>Persisted records</small></div><strong>{data.apps.totalItems}</strong></Panel><Panel className="live-stat"><div><span>Loans</span><small>Persisted records</small></div><strong>{data.loans.totalItems}</strong></Panel><Panel className="live-stat"><div><span>Loan status</span><small>{active && active.status !== "COMPLETED" ? `Outstanding ${money(active.outstandingBalance)}` : "No active balance"}</small></div><strong>{active?.status ?? "None"}</strong></Panel></div>{active ? <Panel className="borrower-loan-summary"><div className="card-heading"><div><span className="eyebrow">{active.status === "ACTIVE" ? "Your active loan" : active.status === "APPROVED" ? "Approved loan" : "Latest loan"}</span><h2>{money(active.principal)} <span>principal</span></h2></div><Badge>{active.status}</Badge></div><div className="loan-summary-line"><span>{active.termMonths} month term</span><span>Total repayable <strong>{money(active.totalRepayable)}</strong></span><span>Outstanding <strong>{money(active.outstandingBalance)}</strong></span></div><div className="inline-actions"><Button onClick={() => { setSelectedId(active.id); go("loan"); }}>View loan details</Button>{active.status === "ACTIVE" ? <Button variant="secondary" onClick={() => { setSelectedId(active.id); go("repayments"); }}>Make a repayment</Button> : active.status === "APPROVED" ? <p className="status-explainer">Repayment becomes available after a Loan Officer records the full disbursement.</p> : <Button variant="secondary" onClick={() => { setSelectedId(active.id); go("repayments"); }}>View repayment history</Button>}</div></Panel> : <Empty title="No loan records yet" detail="When a Loan Officer approves an application, your final terms will appear here." action={<Button onClick={() => go("applications")}>View applications</Button>} />}</>;
  }
  if (screen === "profile") return <Profile payload={payload as { name: string; email: string; phone: string } | null} onSave={(body) => mutate("/borrowers/me/profile", "PATCH", body)} loading={false} staff={account.role !== "BORROWER"} changePassword={() => go("password")} logout={ctx.logout} theme={ctx.theme} setTheme={ctx.setTheme} />;
  if (screen === "apply") return <ApplicationForm submit={(body, key) => mutate<Application>("/applications", "POST", body, { "Idempotency-Key": key }).then((app) => { setSelectedId(app.id); go("application"); return app; })} error={ctx.error} />;
  if (screen === "applications") {
    const page = payload as Page<Application> | null; return <><Header title="My applications" description="Only applications submitted from your account are shown. Approved requests also show the live loan lifecycle." actions={<Button onClick={() => go("apply")}>New application</Button>} />{!page?.items.length ? <Empty title="No applications found" detail="Your submitted loan requests will appear here." action={<Button onClick={() => go("apply")}>Start an application</Button>} /> : <Panel className="records-panel"><div className="records-intro"><div><span className="eyebrow">Your requests</span><h2>Application history</h2></div><span>{page.totalItems} {page.totalItems === 1 ? "application" : "applications"}</span></div><div className="application-record-grid">{page.items.map((app, index) => <article className="application-record-card" key={app.id}><div className="application-record-top"><span className="record-index">{String(index + 1).padStart(2, "0")}</span><span className="eyebrow">Submitted {date(app.submittedAt)}</span></div><div className="application-record-amount"><span>Requested amount</span><strong>{money(app.requestedAmount)}</strong></div><div className="application-record-facts"><div><span>Request status</span><strong><Badge>{app.status}</Badge></strong></div><div><span>Requested term</span><strong>{app.requestedTermMonths} months</strong></div>{app.loanStatus && <div className="application-loan-lifecycle"><span>Loan lifecycle</span><strong><Badge>{app.loanStatus}</Badge></strong></div>}{app.approvedPrincipal && <div><span>Approved amount</span><strong>{money(app.approvedPrincipal)}</strong></div>}{app.approvedTermMonths && <div><span>Approved term</span><strong>{app.approvedTermMonths} months</strong></div>}</div>{app.rejectionReason && <p className="application-rejection-note"><strong>Decision:</strong> {app.rejectionReason}</p>}<Button variant="secondary" className="application-record-action" onClick={() => { setSelectedId(app.id); go("application"); }}>View application details</Button></article>)}</div></Panel>}</>;
  }
  if (screen === "application") return <ApplicationDetail app={payload as Application | OfficerApplication | null} officer={account.role === "LOAN_OFFICER"} mutate={mutate} go={go} />;
  if (screen === "loans") { const page = payload as Page<Loan> | null; return <><Header title="My loans" description="Loan status and balances come from the operational database." />{!page?.items.length ? <Empty title="No loan records" detail="A loan will appear after an application is approved." action={<Button onClick={() => go("applications")}>View applications</Button>} /> : <div className="loan-cards-grid">{page.items.map((loan) => <Panel className="loan-record-card" key={loan.id}><div className="card-heading"><div><span className="eyebrow">Loan record</span><h2>{money(loan.principal)}</h2><span className="record-caption">Approved principal</span></div><Badge>{loan.status}</Badge></div><div className="loan-record-facts"><div><span>Outstanding</span><strong>{money(loan.outstandingBalance)}</strong></div><div><span>Total repayable</span><strong>{money(loan.totalRepayable)}</strong></div><div><span>Approved term</span><strong>{loan.termMonths} months</strong></div><div><span>Flat interest</span><strong>{loan.flatRatePercent}%</strong></div></div><div className="loan-record-actions"><Button onClick={() => { setSelectedId(loan.id); go("loan"); }}>View loan details</Button>{loan.status === "ACTIVE" && <Button variant="secondary" onClick={() => { setSelectedId(loan.id); go("repayments"); }}>Make a repayment</Button>}</div></Panel>)}</div>}</>; }
  if (screen === "loan") { const data = payload as { loan: Loan; events: ChainEvent[]; repayments: Repayment[] } | null; if (!data) return <><Header title="Loan details" />{ctx.error ? <ErrorBox message={ctx.error} onRetry={refreshNow} /> : <Empty title="No loan is available" detail="Approved loans will appear here." />}</>; return <LoanDetail data={data} go={go} setSelectedId={setSelectedId} />; }
  if (screen === "repayments") { const data = payload as { loan: Loan | null; loans?: Loan[]; events: ChainEvent[]; repayments: Repayment[]; simulations: RepaymentEvent[] } | null; return <Repayments data={data} error={ctx.error} retry={refreshNow} go={go} create={(loanId, amount, key) => mutate<RepaymentEvent>(`/loans/${loanId}/simulated-repayments`, "POST", { amount }, { "Idempotency-Key": key })} />; }
  if (screen === "officer-applications") { const page = payload as Page<OfficerApplication> | null; return <><Header title="Application queue" description="Pending applications submitted by borrowers. Decisions remain with an authorized Loan Officer." /><div className="records-toolbar"><label className="search-field"><span>Find a borrower</span><input value={officerSearchText} onChange={(event) => setOfficerSearchText(event.target.value)} placeholder="Type at least 2 letters" aria-label="Search pending applications by borrower name" /><small>Search is delayed briefly and matches the beginning of a borrower name.</small></label>{officerSearchText && <Button variant="ghost" onClick={() => { setOfficerSearchText(""); setOfficerSearch(""); }}>Clear search</Button>}</div>{ctx.error ? <ErrorBox message={ctx.error} onRetry={refreshNow} /> : !page?.items.length ? <Empty title={officerSearch ? "No matching pending applications" : "No pending applications"} detail={officerSearch ? "Try a different borrower name." : "New borrower submissions will appear here when they are ready for review."} /> : <Panel className="records-panel"><div className="records-intro"><div><span className="eyebrow">Officer review</span><h2>Requests waiting for a decision</h2></div><span>{page.totalItems} pending</span></div><div className="data-table queue-table"><div className="table-head"><span>Borrower</span><span>Requested amount</span><span>Requested term</span><span>Submitted</span><span>Status</span><span>Action</span></div>{page.items.map((app, index) => <div className="table-row queue-table-row" key={app.id}><span data-label="Borrower"><span className="queue-borrower"><span className="queue-index">{String(index + 1).padStart(2, "0")}</span><strong>{app.borrowerName}</strong></span></span><span data-label="Requested amount">{money(app.requestedAmount)}</span><span data-label="Requested term">{app.requestedTermMonths} months</span><span data-label="Submitted">{date(app.submittedAt)}</span><span data-label="Status"><Badge>{app.status}</Badge></span><span data-label="Review action"><Button variant="secondary" className="btn-compact" onClick={() => { setSelectedId(app.id); go("officer-application"); }}>Review application</Button></span></div>)}</div></Panel>}</>; }
  if (screen === "officer-application") { const data = payload as { application: OfficerApplication; history: BorrowerHistory | null; historyError: string | null } | null; return <ApplicationDetail app={data?.application ?? null} history={data?.history} historyError={data?.historyError ?? undefined} error={ctx.error} retry={refreshNow} officer mutate={mutate} go={go} />; }
  if (screen === "officer-repayments") {
    const items = payload as RepaymentEvent[] | null;
    const pending = items?.filter((event) => event.status === "PENDING") ?? [];
    const verified = items?.filter((event) => event.status === "VERIFIED") ?? [];
    return <>
      <Header title="Simulated repayment events" description="Review borrower-started payment simulations. A simulation never changes the official balance until you verify it and record the repayment." />
      {!items?.length ? <Empty title="No repayment events need review" detail="Borrower-initiated simulations will appear here after they are submitted." /> : <div className="repayment-review-sections">
        {pending.length > 0 && <section className="repayment-review-group" aria-labelledby="pending-events-heading">
          <div className="repayment-review-heading"><div><span className="eyebrow">Step 1 · Officer review</span><h2 id="pending-events-heading">Awaiting verification</h2><p>Check the submitted simulation before it can become an official repayment.</p></div><span className="review-count">{pending.length} waiting</span></div>
          <div className="repayment-review-grid">{pending.map((event) => <RepaymentReviewCard key={event.id} event={event} mutate={mutate} />)}</div>
        </section>}
        {verified.length > 0 && <section className="repayment-review-group verified-group" aria-labelledby="verified-events-heading">
          <div className="repayment-review-heading"><div><span className="eyebrow">Step 2 · Complete the record</span><h2 id="verified-events-heading">Verified · ready to record</h2><p>Verification passed. Recording now applies this amount to the loan balance.</p></div><span className="review-count">{verified.length} ready</span></div>
          <div className="repayment-review-grid">{verified.map((event) => <RepaymentReviewCard key={event.id} event={event} mutate={mutate} />)}</div>
        </section>}
      </div>}
    </>;
  }
  if (screen === "officer-events") return <ChainEvents page={payload as Page<ChainEvent> | null} title="Blockchain event status" />;
  if (screen === "officer-loans") { const page = payload as Page<Loan> | null; return <><Header title="Loan servicing" description="Review approved loans and complete the single full disbursement that activates repayment." />{ctx.error ? <ErrorBox message={ctx.error} onRetry={refreshNow} /> : !page?.items.length ? <Empty title="No loans to service" detail="Loans appear here after an officer decision has created them." /> : <Panel className="servicing-panel"><div className="servicing-intro"><div><span className="eyebrow">Officer-controlled servicing</span><h2>{page.totalItems} loan records</h2><p>A disbursement must equal the approved principal. Once recorded, the loan becomes active and the borrower can begin the simulated repayment workflow.</p></div></div><div className="servicing-loan-grid">{page.items.map((loan) => <OfficerLoanRow key={loan.id} loan={loan} mutate={mutate} />)}</div></Panel>}</>; }
  if (screen === "users") return <StaffAccounts data={payload as Staff[] | null} mutate={mutate} refresh={refreshNow} />;
  if (screen === "admin-chain") {
    const data = payload as { events: Page<ChainEvent> | null; findings: unknown[] | null; eventsError?: string | null; findingsError?: string | null } | null;
    return <><ChainEvents page={data?.events ?? null} title="Blockchain monitoring" error={data?.eventsError ?? null} retry={refreshNow} /><Panel><div className="card-heading"><h2>Reconciliation findings</h2><Button variant="secondary" onClick={() => void mutate("/admin/blockchain-reconciliation/run", "POST")}>Run reconciliation</Button></div>{data?.findingsError ? <ErrorBox message={data.findingsError} onRetry={refreshNow} /> : data?.findings?.length ? <pre className="api-json">{JSON.stringify(data.findings, null, 2)}</pre> : <p>No reconciliation findings are currently stored.</p>}</Panel></>;
  }
  if (screen === "admin-reports") { const data = payload as AdminReport | null; return <AdminReportsPage report={data} error={ctx.error} retry={refreshNow} go={go} />; }
  if (screen === "admin-activity") { const page = payload as Page<Activity> | null; return <><Header title="System activity" description="Read-only audit history for operational events. Personal details and internal notes are not shown." /><div className="records-toolbar"><label className="search-field"><span>Search action or target</span><input value={activitySearchText} onChange={(event) => { setActivitySearchText(event.target.value); setActivityPage(0); }} placeholder="e.g. application or event ID" aria-label="Search audit activity" /><small>Search by action/target prefix or exact target ID.</small></label><label className="filter-field"><span>Actor role</span><select value={activityActorRole} onChange={(event) => { setActivityActorRole(event.target.value); setActivityPage(0); }}><option value="">All roles</option><option value="BORROWER">Borrower</option><option value="LOAN_OFFICER">Loan Officer</option><option value="ADMINISTRATOR">Administrator</option></select></label><label className="filter-field"><span>Result</span><select value={activityResult} onChange={(event) => { setActivityResult(event.target.value); setActivityPage(0); }}><option value="">All results</option><option value="SUCCEEDED">Succeeded</option><option value="FAILED">Failed</option></select></label></div>{ctx.error ? <ErrorBox message={ctx.error} onRetry={refreshNow} /> : !page?.totalItems ? <Empty title={activitySearch || activityActorRole || activityResult ? "No matching activity" : "No audit activity"} detail={activitySearch || activityActorRole || activityResult ? "Change or clear the filters to see more records." : "System and loan actions will appear here as they are recorded."} /> : <Panel className="records-panel"><div className="records-intro"><div><span className="eyebrow">Audit trail</span><h2>Recorded system activity</h2></div><span>{page.totalItems} {page.totalItems === 1 ? "event" : "events"}</span></div><div className="audit-record-list">{page.items.map((item, index) => <article className="audit-record-card" key={item.id}><header><span className="audit-record-index">{String(page.page * page.size + index + 1).padStart(2, "0")}</span><div><span className="eyebrow">{item.action.replace(/_/g, " ")}</span><time>{date(item.occurredAt)}</time></div><Badge>{item.result}</Badge></header><dl><div><dt>Actor</dt><dd>{item.actorRole?.replace(/_/g, " ") ?? "System initiated"}</dd></div><div><dt>Target record</dt><dd>{item.targetType.replace(/_/g, " ")}{item.targetId && <code title={item.targetId}>{item.targetId}</code>}</dd></div></dl></article>)}</div><div className="pagination-bar"><span>Page {page.page + 1} of {Math.max(1, Math.ceil(page.totalItems / page.size))}</span><div><Button variant="secondary" className="btn-compact" disabled={page.page === 0} onClick={() => setActivityPage((current) => Math.max(0, current - 1))}>Previous</Button><Button variant="secondary" className="btn-compact" disabled={(page.page + 1) * page.size >= page.totalItems} onClick={() => setActivityPage((current) => current + 1)}>Next</Button></div></div></Panel>}</>; }
  if (screen === "dashboard" && account.role === "LOAN_OFFICER") {
    const data = payload as { applications: Page<OfficerApplication> | null; repayments: RepaymentEvent[] | null; applicationsError?: string | null; repaymentsError?: string | null } | null;
    const applications = data?.applications;
    return <><Header title={<WelcomeTitle account={account} />} description="Your review queue and repayment events that need attention." />
      <div className="dashboard-metrics">
        <Panel className="dashboard-metric metric-emerald"><span>Applications awaiting review</span><strong>{applications?.totalItems ?? "—"}</strong><small>Human review required</small><Button variant="secondary" onClick={() => go("officer-applications")}>Open application queue</Button></Panel>
        <Panel className="dashboard-metric metric-champagne"><span>Repayment simulations</span><strong>{data?.repayments?.length ?? "—"}</strong><small>Awaiting Officer verification</small><Button variant="secondary" onClick={() => go("officer-repayments")}>Review repayments</Button></Panel>
      </div>
      <Panel className="queue-panel"><div className="card-heading"><div><span className="eyebrow">Next actions</span><h2>Application queue</h2><p className="card-subtitle">Open a request to review its terms and borrower context.</p></div><Button onClick={() => go("officer-applications")}>Open full queue</Button></div>
        {data?.applicationsError ? <ErrorBox message={data.applicationsError} onRetry={refreshNow} /> : !applications?.items.length ? <Empty title="No applications waiting" detail="New borrower submissions will appear here when they are ready for review." /> : <div className="officer-queue-list">{applications.items.slice(0, 5).map((app, index) => <article className="officer-queue-item" key={app.id}><span className="queue-index">{String(index + 1).padStart(2, "0")}</span><div className="queue-person"><strong>{app.borrowerName}</strong><small>Submitted {date(app.submittedAt)}</small></div><div className="queue-request"><span>Requested amount</span><strong>{money(app.requestedAmount)}</strong></div><div className="queue-request"><span>Requested term</span><strong>{app.requestedTermMonths} months</strong></div><Badge>{app.status}</Badge><Button variant="secondary" className="btn-compact" onClick={() => { setSelectedId(app.id); go("officer-application"); }}>Review application</Button></article>)}</div>}
      </Panel>
      <Panel className="next-step-panel"><div><span className="eyebrow">Repayment verification</span><h2>Review simulated repayments</h2><p>Confirm a borrower simulation before recording the official repayment. A simulation alone does not change the loan balance.</p>{data?.repaymentsError ? <small className="inline-error" role="alert">{data.repaymentsError}</small> : <Badge>{data?.repayments ? `${data.repayments.length} pending` : "Status unavailable"}</Badge>}</div><Button onClick={() => go("officer-repayments")}>Open repayment events</Button></Panel>
    </>;
  }
  if (screen === "dashboard" && account.role === "ADMINISTRATOR") {
    const data = payload as { events: Page<ChainEvent> | null; activity: Page<Activity> | null; staff: Staff[] | null; reports: AdminReport | null; eventsError?: string | null; activityError?: string | null; staffError?: string | null; reportsError?: string | null } | null;
    return <><Header title={<WelcomeTitle account={account} />} description="A clear view of staff access, audit activity, and blockchain event delivery." /><div className="dashboard-metrics admin-metrics">
      <Panel className="dashboard-metric metric-emerald"><span>Staff accounts</span><strong>{data?.staff?.length ?? "—"}</strong><small>Administrator and Loan Officer access</small><Button variant="secondary" onClick={() => go("users")}>Manage staff accounts</Button>{data?.staffError && <small className="inline-error" role="alert">{data.staffError}</small>}</Panel>
      <Panel className="dashboard-metric metric-champagne"><span>Blockchain events</span><strong>{data?.events?.totalItems ?? "—"}</strong><small>Persisted delivery records</small><Button variant="secondary" onClick={() => go("admin-chain")}>Open blockchain monitor</Button>{data?.eventsError && <small className="inline-error" role="alert">{data.eventsError}</small>}</Panel>
      <Panel className="dashboard-metric metric-neutral"><span>Audit activity</span><strong>{data?.activity?.totalItems ?? "—"}</strong><small>Persisted audit entries</small><Button variant="secondary" onClick={() => go("admin-activity")}>Review system activity</Button>{data?.activityError && <small className="inline-error" role="alert">{data.activityError}</small>}</Panel>
      <Panel className="dashboard-metric metric-emerald"><span>Outstanding portfolio</span><strong>{data?.reports ? money(data.reports.loans.outstandingBalance) : "—"}</strong><small>Current persisted balances</small><Button variant="secondary" onClick={() => go("admin-reports")}>Open portfolio report</Button>{data?.reportsError && <small className="inline-error" role="alert">{data.reportsError}</small>}</Panel>
    </div>{data?.reports && <div className="admin-overview-grid"><Panel className="report-panel"><div className="records-intro"><div><span className="eyebrow">Portfolio activity</span><h2>Recent loan activity</h2></div><Button variant="secondary" onClick={() => go("admin-reports")}>View reports</Button></div><TrendChart report={data.reports} /></Panel><Panel className="report-panel"><div className="records-intro"><div><span className="eyebrow">Operational snapshot</span><h2>Loan lifecycle</h2></div></div><MiniBarChart title="Current persisted loan records" items={[{ label: "Approved · awaiting disbursement", value: data.reports.loans.approved, tone: "bar-champagne" }, { label: "Active", value: data.reports.loans.active, tone: "bar-emerald" }, { label: "Completed", value: data.reports.loans.completed, tone: "bar-muted" }]} /><div className="report-total-row"><span>Applications awaiting review</span><strong>{data.reports.applications.pending}</strong></div></Panel></div>}<Panel className="admin-guidance"><span className="eyebrow">Administrator workspace</span><h2>Monitor and manage system access</h2><p>Account administration and monitoring are available here. Loan decisions, disbursement, and official repayment recording remain Loan Officer responsibilities.</p><div className="inline-actions"><Button onClick={() => go("users")}>Manage staff access</Button><Button variant="secondary" onClick={() => go("admin-activity")}>Open audit activity</Button><Button variant="secondary" onClick={() => go("admin-reports")}>View operational reports</Button></div></Panel></>;
  }
  return <><Header title="PesaChain" description="This workflow is not available for the current role or backend API." /><Empty title="No supported view" detail="Return to a supported workspace page." /></>;
}

function Profile({ payload, onSave, loading, staff = false, changePassword, logout, theme, setTheme }: { payload: { name: string; email: string; phone: string } | null; onSave: (body: unknown) => Promise<unknown>; loading: boolean; staff?: boolean; changePassword: () => void; logout: () => void; theme: "light" | "dark"; setTheme: (value: "light" | "dark") => void }) {
  const [name, setName] = useState(payload?.name ?? ""); const [phone, setPhone] = useState(payload?.phone ?? ""); const [message, setMessage] = useState("");
  useEffect(() => { if (payload) { setName(payload.name); setPhone(payload.phone); } }, [payload]);
  return <><Header title={staff ? "Account" : "My profile"} description={staff ? "Staff account details are read-only in this workspace." : "Only your own profile details are available here."} /><Panel>{staff ? <dl className="detail-grid"><div><dt>Name</dt><dd>{payload?.name ?? "—"}</dd></div><div><dt>Email</dt><dd>{payload?.email ?? "—"}</dd></div><div><dt>Phone</dt><dd>{payload?.phone ?? "—"}</dd></div></dl> : <form className="form-grid" onSubmit={(e) => { e.preventDefault(); void onSave({ fullName: name, phone }).then(() => setMessage("Profile updated.")).catch(() => {}); }}><Field label="Full name" value={name} onChange={setName} required /><Field label="Phone" value={phone} onChange={setPhone} required /><Field label="Email" value={payload?.email ?? ""} onChange={() => {}} required disabled hint="Email changes are not supported." />{message && <p role="status" className="field-wide">{message}</p>}<div className="field-wide"><Button type="submit" disabled={loading}>Save profile</Button></div></form>}</Panel><Panel className="account-settings-panel"><div className="records-intro"><div><span className="eyebrow">Preferences and security</span><h2>Settings</h2><p>Manage your workspace appearance and account access.</p></div></div><div className="account-setting-row"><div><strong>Dashboard appearance</strong><span>Choose a comfortable theme for authenticated workspaces. Public pages remain in the PesaChain light theme.</span></div><ThemeToggle theme={theme} setTheme={setTheme} /></div><div className="account-setting-actions"><Button variant="secondary" onClick={changePassword}>Change password</Button><Button variant="danger" onClick={logout}>Sign out</Button></div></Panel></>;
}

type ApplicationRequest = { requestedAmount: string; purpose: string; requestedTermMonths: number };
async function applicationSubmissionKey(body: ApplicationRequest) {
  const normalized = JSON.stringify({ amount: Number(body.requestedAmount).toFixed(2), purpose: body.purpose.trim(), term: body.requestedTermMonths });
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(normalized));
  const fingerprint = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  const storageKey = "pesachain.application-submission.v1";
  try {
    const existing = JSON.parse(sessionStorage.getItem(storageKey) || "null") as { fingerprint?: string; key?: string } | null;
    if (existing?.fingerprint === fingerprint && existing.key) return { key: existing.key, storageKey };
    const key = idempotencyKey();
    sessionStorage.setItem(storageKey, JSON.stringify({ fingerprint, key }));
    return { key, storageKey };
  } catch {
    return { key: idempotencyKey(), storageKey: "" };
  }
}

function ApplicationForm({ submit, error }: { submit: (body: ApplicationRequest, key: string) => Promise<unknown>; error: string }) {
  const [amount, setAmount] = useState(""); const [term, setTerm] = useState(""); const [purpose, setPurpose] = useState(""); const [submitting, setSubmitting] = useState(false); const [localError, setLocalError] = useState("");
  const onSubmit = async (event: FormEvent) => {
    event.preventDefault(); if (submitting) return;
    setSubmitting(true); setLocalError("");
    const body = { requestedAmount: amount, requestedTermMonths: Number(term), purpose };
    try {
      const attempt = await applicationSubmissionKey(body);
      await submit(body, attempt.key);
      if (attempt.storageKey) sessionStorage.removeItem(attempt.storageKey);
    } catch {
      setLocalError("The application was not confirmed. Retry with the same details so PesaChain can safely return the original submission if it already committed.");
    } finally { setSubmitting(false); }
  };
  return <><Header title="Apply for a loan" description="Enter the amount and term you are requesting. No application is approved automatically." /><Panel><form className="form-grid" onSubmit={(event) => void onSubmit(event)}><Field label="Requested amount (KES)" value={amount} onChange={setAmount} type="number" min="0.01" step="0.01" required /><Field label="Requested term (months)" value={term} onChange={setTerm} type="number" min="1" step="1" required /><Area label="Purpose" value={purpose} onChange={setPurpose} required hint="Up to 1000 characters. Keep personal or identity details out of this description." />{(error || localError) && <p role="alert" className="field-wide field-error">{localError || error}</p>}<div className="field-wide"><p>Loan decisions are made by an authorized Loan Officer. The approved principal, term and flat rate may differ from your request and will be shown separately.</p><Button type="submit" disabled={submitting}>{submitting ? "Submitting…" : "Submit application"}</Button></div></form></Panel></>;
}

function ApplicationDetail({ app, officer = false, mutate, go, error, retry, history, historyError }: { app: Application | OfficerApplication | null; officer?: boolean; mutate: PageContext["mutate"]; go: PageContext["go"]; error?: string; retry?: () => void; history?: BorrowerHistory | null; historyError?: string }) {
  const [decision, setDecision] = useState("APPROVE");
  const [principal, setPrincipal] = useState("");
  const [term, setTerm] = useState("");
  const [rate, setRate] = useState("");
  const [rationale, setRationale] = useState("");
  const [reason, setReason] = useState("");
  const [feedback, setFeedback] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => { if (app) { setPrincipal(app.requestedAmount); setTerm(String(app.requestedTermMonths)); } }, [app]);
  if (!app) return <><Header title="Application details" description="Review the request and record a reasoned decision." />{error ? <ErrorBox message={error} onRetry={retry} /> : officer && <Empty title="Select an application" detail="Open a pending application from the queue." action={<Button variant="secondary" onClick={() => go("officer-applications")}>Return to application queue</Button>} />}</>;
  const officerApp = app as OfficerApplication;
  const submit = async (e: FormEvent) => {
    e.preventDefault(); setFeedback(""); setSaving(true);
    try {
      await mutate(`/officer/applications/${app.id}/decision`, "POST", decision === "APPROVE"
        ? { decision, approvedPrincipal: principal, approvedTermMonths: Number(term), flatRatePercent: rate, rationale }
        : { decision, rationale, rejectionReason: reason });
      setFeedback("Decision saved in the operational database.");
    } catch { /* Shared request feedback is rendered by the workspace. */ }
    finally { setSaving(false); }
  };
  return <>
    <Header title="Application details" description={officer ? `Borrower: ${officerApp.borrowerName}` : "Your original request and final decision are shown separately."} actions={officer ? <Button variant="secondary" onClick={() => go("officer-applications")}>Back to queue</Button> : undefined} />
    <Panel><dl className="detail-grid"><div><dt>Requested amount</dt><dd>{money(app.requestedAmount)}</dd></div><div><dt>Requested term</dt><dd>{app.requestedTermMonths} months</dd></div><div><dt>Status</dt><dd><Badge>{app.status}</Badge></dd></div><div><dt>Submitted</dt><dd>{date(app.submittedAt)}</dd></div><div className="field-wide"><dt>Purpose</dt><dd>{app.purpose}</dd></div>{app.approvedPrincipal && <><div><dt>Approved principal</dt><dd>{money(app.approvedPrincipal)}</dd></div><div><dt>Approved term</dt><dd>{app.approvedTermMonths} months</dd></div><div><dt>Flat rate</dt><dd>{app.approvedFlatRatePercent}%</dd></div><div><dt>Interest</dt><dd>{money(app.calculatedInterest)}</dd></div><div><dt>Total repayable</dt><dd>{money(app.totalRepayable)}</dd></div></>}{app.rejectionReason && <div className="field-wide"><dt>Decision reason</dt><dd>{app.rejectionReason}</dd></div>}{officerApp.decisionRationale && <div className="field-wide"><dt>Internal decision rationale</dt><dd>{officerApp.decisionRationale}</dd></div>}</dl></Panel>
    {officer && <BorrowerHistoryPanel history={history ?? null} error={historyError} />}
    {officer && app.status === "PENDING" && <Panel><h2>Record decision</h2><form className="form-grid" onSubmit={(e) => void submit(e)}><label className="field"><span>Decision</span><select value={decision} onChange={(e) => setDecision(e.target.value)}><option value="APPROVE">Approve</option><option value="REJECT">Reject</option></select></label><Area label="Decision rationale" value={rationale} onChange={setRationale} required hint="Internal note. Kept off-chain." />{decision === "APPROVE" ? <><Field label="Approved principal (KES)" value={principal} onChange={setPrincipal} type="number" min="0.01" step="0.01" required /><Field label="Approved term (months)" value={term} onChange={setTerm} type="number" min="1" step="1" required /><Field label="Flat interest rate (%)" value={rate} onChange={setRate} type="number" min="0" step="0.0000000001" required hint="0% is permitted." /></> : <Area label="Borrower-readable rejection reason" value={reason} onChange={setReason} required />}{feedback && <p role="status" className="field-wide">{feedback}</p>}<div className="field-wide"><Button type="submit" loading={saving}>{saving ? "Saving decision…" : "Save decision"}</Button></div></form></Panel>}
    {officer && app.status === "APPROVED" && <Panel><h2>Disbursement</h2><p>The approved loan is ready for its single full disbursement. Open loan servicing to record it.</p><Button onClick={() => go("officer-loans")}>Open loan servicing</Button></Panel>}
  </>;
}

function BorrowerHistoryPanel({ history, error }: { history: BorrowerHistory | null; error?: string }) {
  if (error) return <Panel><div className="card-heading"><div><span className="eyebrow">Borrower context</span><h2>Repayment history</h2></div></div><p>This read-only performance history could not be loaded.</p><small role="alert">{error}</small></Panel>;
  if (!history) return <Panel><div className="skeleton-heading" aria-label="Loading borrower history"><i /><i /></div><div className="skeleton-stats" aria-hidden="true"><i /><i /><i /></div></Panel>;
  const totalDue = history.loans.reduce((sum, loan) => sum + (moneyCents(loan.totalRepayable) ?? 0n), 0n);
  const repaid = moneyCents(history.amountRepaid) ?? 0n;
  const progress = totalDue > 0n ? Number((repaid * 10000n) / totalDue) / 100 : 0;
  return <Panel className="borrower-history-panel">
    <div className="card-heading"><div><span className="eyebrow">Historical facts · no automated score</span><h2>Borrower repayment history</h2></div><span className="history-progress-value">{progress.toFixed(0)}%<small>of contracted total recorded</small></span></div>
    <p className="status-explainer">This is a factual summary of recorded account activity. It does not predict repayment or recommend approval; lending decisions remain with the Loan Officer.</p>
    <div className="stats history-stats"><div><span>Applications</span><strong>{history.applicationCount}</strong></div><div><span>Loans</span><strong>{history.loanCount}</strong></div><div><span>Active</span><strong>{history.activeLoanCount}</strong></div><div><span>Recorded repayments</span><strong>{money(history.amountRepaid)}</strong></div><div><span>Current total outstanding</span><strong>{money(history.outstandingBalance)}</strong></div></div>
    {!history.loans.length ? <p>No prior approved loans are recorded for this borrower.</p> : <div className="borrower-history-loans">{history.loans.map((loan) => <article key={loan.loanId}><div className="card-heading"><div><span className="eyebrow">Loan record · {date(loan.approvedAt)}</span><strong>{money(loan.principal)} principal</strong></div><Badge>{loan.status}</Badge></div><div className="loan-summary-line"><span>Contract total<strong>{money(loan.totalRepayable)}</strong></span><span>Officially repaid<strong>{money(loan.amountRepaid)}</strong></span><span>Outstanding<strong>{money(loan.outstandingBalance)}</strong></span><span>Amount progress<strong>{repaymentPercent(loan.totalRepayable, loan.outstandingBalance).toFixed(0)}%</strong></span></div></article>)}</div>}
    <h3>Official repayment and evidence history</h3>
    {!history.repayments.length ? <p>No official repayments are recorded in prior loans.</p> : <div className="repayment-history">{history.repayments.map((item) => <article key={item.repaymentId}><div><span className="eyebrow">{date(item.recordedAt)} · {money(item.amountApplied)}</span><strong>Balance after {money(item.outstandingAfter)}</strong></div><div>{item.blockchainStatus ? <><Badge>{item.blockchainStatus}</Badge><small>{item.blockchainStatus === "RECORDED" ? `Confirmed event evidence · ${item.confirmations} confirmation(s)${item.blockNumber ? ` · block ${item.blockNumber}` : ""}${item.transactionHash ? ` · ${item.transactionHash.slice(0, 10)}…${item.transactionHash.slice(-8)}` : ""}` : "Chain delivery status is separate from this official database repayment."}</small></> : <small>No blockchain evidence status returned for this repayment.</small>}<Button variant="secondary" onClick={() => downloadRepaymentReceipt(item)}>Download PDF record</Button></div></article>)}</div>}
    {history.lifecycle.length > 0 && <><h3>Loan lifecycle audit</h3><ol className="audit-timeline">{history.lifecycle.map((entry, index) => <li key={`${entry.loanId}-${entry.toStatus}-${index}`}><span className="audit-timeline-index">{String(index + 1).padStart(2, "0")}</span><div><strong>{entry.fromStatus ? `${entry.fromStatus.replace(/_/g, " ")} → ${entry.toStatus.replace(/_/g, " ")}` : entry.toStatus.replace(/_/g, " ")}</strong><small>Loan {entry.loanId.slice(0, 8)}… · {date(entry.changedAt)}</small></div><Badge>{entry.toStatus}</Badge></li>)}</ol></>}
  </Panel>;
}

function LoanDetail({ data, go, setSelectedId }: { data: { loan: Loan; events: ChainEvent[]; repayments: Repayment[] }; go: PageContext["go"]; setSelectedId: (id: string) => void }) {
  const { loan, events, repayments } = data;
  return <><Header title="Loan details" description="Your approved terms and repayment history, with blockchain evidence shown separately." /><Panel className="loan-detail-hero"><div className="card-heading"><div><span className="eyebrow">Outstanding balance</span><h2>{money(loan.outstandingBalance)}</h2></div><Badge>{loan.status}</Badge></div><div className="loan-summary-line"><span>Approved principal <strong>{money(loan.principal)}</strong></span><span>Total repayable <strong>{money(loan.totalRepayable)}</strong></span><span>Term <strong>{loan.termMonths} months</strong></span><span>Flat interest <strong>{loan.flatRatePercent}%</strong></span></div>{loan.status === "ACTIVE" && <Button onClick={() => { setSelectedId(loan.id); go("repayments"); }}>Go to repayments</Button>}{loan.status === "APPROVED" && <p className="status-explainer">Repayment simulation will be available after a Loan Officer records the full disbursement.</p>}{loan.status === "COMPLETED" && <p className="status-explainer">Loan repaid in full. Keep this history for your records.</p>}</Panel><LoanJourney history={loan.history} /><Panel><h2>Official repayments</h2>{!repayments.length ? <Empty title="No repayments recorded yet" detail={loan.status === "APPROVED" ? "Your loan must be fully disbursed before simulated repayment can begin." : "Official repayments appear after a Loan Officer verifies and records a simulation."} /> : <div className="repayment-history">{repayments.map((r) => <article key={r.id}><div><span className="eyebrow">Recorded {date(r.recordedAt)}</span><strong>{money(r.amountApplied)}</strong></div><div><span>Remaining after this repayment <b>{money(r.outstandingAfter)}</b></span><Button variant="secondary" onClick={() => downloadRepaymentReceipt(r)}>Download PDF record</Button></div></article>)}</div>}</Panel><Panel><h2>Blockchain event evidence</h2><p>Blockchain delivery is independent of your operational loan status.</p><ChainList items={events} /></Panel></>;
}

function LoanJourney({ history }: { history: Loan["history"] }) {
  const labels: Record<string, { title: string; detail: string }> = {
    APPROVED: { title: "Loan approved", detail: "The Officer recorded the approved terms." },
    DISBURSED: { title: "Full disbursement recorded", detail: "The approved principal was disbursed in one operation." },
    ACTIVE: { title: "Loan active", detail: "The borrower can now start a simulated repayment." },
    COMPLETED: { title: "Loan completed", detail: "The official outstanding balance reached KES 0.00." },
  };
  return <Panel className="loan-journey-panel"><div className="records-intro"><div><span className="eyebrow">Operational timeline</span><h2>Loan journey</h2></div><span>{history.length} {history.length === 1 ? "milestone" : "milestones"}</span></div>{history.length ? <ol className="loan-journey-timeline">{history.map((entry, index) => { const copy = labels[entry.toStatus] ?? { title: entry.toStatus.replace(/_/g, " "), detail: "A loan lifecycle change was recorded." }; return <li className={`journey-milestone milestone-${entry.toStatus.toLowerCase()}`} key={`${entry.toStatus}-${index}`}><span className="milestone-number">{String(index + 1).padStart(2, "0")}</span><div className="milestone-copy"><div><strong>{copy.title}</strong><Badge>{entry.toStatus}</Badge></div><p>{copy.detail}</p><time>{date(entry.changedAt)}</time></div></li>; })}</ol> : <p className="status-explainer">Lifecycle milestones will appear here as your loan progresses.</p>}</Panel>;
}

function Repayments({ data, error, retry, go, create }: { data: { loan: Loan | null; loans?: Loan[]; events: ChainEvent[]; repayments: Repayment[]; simulations: RepaymentEvent[] } | null; error: string; retry: () => void; go: PageContext["go"]; create: (loanId: string, amount: string, key: string) => Promise<RepaymentEvent> }) {
  const [amount, setAmount] = useState(""); const [notice, setNotice] = useState(""); const [busy, setBusy] = useState(false); const [attemptKey, setAttemptKey] = useState(idempotencyKey());
  if (!data) return <><Header title="Repayments" description="Simulated repayment events are not money transfers." />{error ? <ErrorBox message={error} onRetry={retry} /> : <Loading />}</>;
  if (!data.loan) return <><Header title="Repayment history" description="Your repayment records and next steps." /><Empty title="No loan to repay yet" detail="A repayment simulation becomes available after your application is approved and a Loan Officer records the full disbursement." action={<Button variant="secondary" onClick={() => go("applications")}>View my applications</Button>} /></>;
  const { loan } = data;
  const progress = repaymentPercent(loan.totalRepayable, loan.outstandingBalance);
  const submit = (e: FormEvent) => { e.preventDefault(); const requestedCents = moneyCents(amount); const balanceCents = moneyCents(loan.outstandingBalance); if (requestedCents === null || requestedCents <= 0n) { setNotice("Enter a positive amount with no more than two decimal places."); return; } if (balanceCents !== null && requestedCents > balanceCents) { setNotice("The amount is higher than your current outstanding balance."); return; } setBusy(true); setNotice(""); void create(loan.id, amount, attemptKey).then(() => { setNotice("Simulation created. It is awaiting Loan Officer verification; your official balance has not changed."); setAmount(""); setAttemptKey(idempotencyKey()); }).catch(() => {}).finally(() => setBusy(false)); };
  return <><Header title="Repayments" description="Track your loan progress. Simulated payments are reviewed before your official balance changes." /><Panel className="repayment-overview"><div className="card-heading"><div><span className="eyebrow">Repayment progress</span><h2>{progress.toFixed(0)}% <span>recorded</span></h2></div><Badge>{loan.status}</Badge></div><div className="repayment-progress" role="progressbar" aria-label="Official repayment progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress)}><span style={{ width: `${progress}%` }} /></div><div className="loan-summary-line"><span>Outstanding <strong>{money(loan.outstandingBalance)}</strong></span><span>Total repayable <strong>{money(loan.totalRepayable)}</strong></span></div>{loan.status === "ACTIVE" ? <><div className="repayment-steps" aria-label="How simulated repayments are recorded"><div><span>01</span><strong>You start a simulation</strong><small>No money is transferred and the balance stays the same.</small></div><div><span>02</span><strong>Loan Officer verifies</strong><small>The event is checked before it can be recorded.</small></div><div><span>03</span><strong>Official balance updates</strong><small>Only after the Officer records the repayment.</small></div></div><p className="status-explainer">Your loan is active. Enter an amount up to the outstanding balance to begin.</p><form className="form-grid repayment-form" onSubmit={submit}><Field label="Simulated repayment amount (KES)" value={amount} onChange={(value) => { setAmount(value); setNotice(""); }} type="number" min="0.01" max={loan.outstandingBalance} step="0.01" required hint={`Maximum ${money(loan.outstandingBalance)}. This simulation does not transfer money or immediately reduce your balance.`} /><div><Button type="submit" loading={busy}>{busy ? "Creating simulation…" : "Start simulated repayment"}</Button></div></form></> : loan.status === "APPROVED" ? <div className="repayment-next-step"><p className="status-explainer">Your loan is approved. A Loan Officer must record the full disbursement before you can start a repayment simulation. No repayment has been taken or recorded yet.</p><Button variant="secondary" onClick={() => go("loan")}>View loan activity</Button></div> : loan.status === "COMPLETED" ? <p className="status-explainer status-good">Your loan is fully repaid. Your official repayment history remains available below.</p> : <p className="status-explainer">Repayment is unavailable while this loan is in {loan.status.toLowerCase().replace(/_/g, " ")} status.</p>}{notice && <p role="status" className="inline-notice">{notice}</p>}</Panel><Panel><h2>Payment simulations</h2><p>These events are simulations. They are not official repayments until a Loan Officer verifies and records them.</p>{data.simulations.length ? <div className="repayment-history">{data.simulations.map((event) => <article key={event.id}><div><span className="eyebrow">Started {date(event.createdAt)}</span><strong>{money(event.amount)}</strong></div><div><Badge>{event.status}</Badge><small>{event.status === "PENDING" ? "Waiting for Officer verification" : event.status === "VERIFIED" ? "Verified; awaiting official recording" : event.status === "RECORDED" ? "Official repayment recorded" : "Simulation status"}</small></div></article>)}</div> : <p>No payment simulations have been started.</p>}</Panel><Panel><h2>Official repayment history</h2>{data.repayments.length ? <div className="repayment-history">{data.repayments.map((r) => <article key={r.id}><div><span className="eyebrow">Recorded {date(r.recordedAt)}</span><strong>{money(r.amountApplied)}</strong></div><div><span>Outstanding after <b>{money(r.outstandingAfter)}</b></span><Button variant="secondary" onClick={() => downloadRepaymentReceipt(r)}>Download PDF record</Button></div></article>)}</div> : <p>No official repayments have been recorded yet.</p>}</Panel><Panel><h2>Blockchain evidence for this loan</h2><p>Blockchain status tracks event delivery separately. It does not indicate whether a payment moved in the real world.</p><ChainList items={data.events} /></Panel></>;
}

function RepaymentReviewCard({ event, mutate }: { event: RepaymentEvent; mutate: PageContext["mutate"] }) {
  const pending = event.status === "PENDING";
  return <article className={`repayment-review-card ${pending ? "review-pending" : "review-verified"}`}>
    <header><div><span className="eyebrow">{pending ? "Borrower simulation" : "Verification complete"}</span><Badge>{event.status}</Badge></div><strong className="review-amount">{money(event.amount)}</strong></header>
    <dl className="repayment-review-facts"><div><dt>Loan reference</dt><dd><code>{event.loanId}</code></dd></div><div><dt>Submitted</dt><dd>{date(event.createdAt)}</dd></div></dl>
    <div className="repayment-review-guidance"><strong>{pending ? "Next: verify this simulation" : "Next: record the official repayment"}</strong><span>{pending ? "Verification checks the simulated event. It does not reduce the borrower's balance." : "Recording applies the amount to the operational loan balance and creates the official repayment record."}</span></div>
    <footer><span className="review-event-id">Event <code title={event.id}>{event.id}</code></span><EventActions event={event} mutate={mutate} /></footer>
  </article>;
}

function EventActions({ event, mutate }: { event: RepaymentEvent; mutate: PageContext["mutate"] }) { const [error, setError] = useState(""); const run = (action: "verify" | "record") => { setError(""); void mutate(`/officer/repayment-events/${event.id}/${action}`, "POST").catch((e) => setError(e instanceof Error ? e.message : "Action failed.")); }; return <><Button variant="secondary" onClick={() => run(event.status === "PENDING" ? "verify" : "record")}>{event.status === "PENDING" ? "Verify simulation" : "Record official repayment"}</Button>{error && <small role="alert">{error}</small>}</>; }
function ChainEvents({ page, title, error, retry }: { page: Page<ChainEvent> | null; title: string; error?: string | null; retry?: () => void }) { return <><Header title={title} description="Blockchain delivery status does not change the operational loan status." />{error ? <ErrorBox message={error} onRetry={retry} /> : !page?.items.length ? <Empty title="No blockchain events" detail="Selected event records will appear after the related operational action commits." /> : <Panel><ChainList items={page.items} /></Panel>}</>; }
function chainStatusDescription(status: string) {
  switch (status) {
    case "PENDING": return "Waiting to submit";
    case "PROCESSING": return "Submission in progress";
    case "SUBMITTED": return "Submitted; awaiting confirmation";
    case "RECORDED": return "Required confirmation reached";
    case "FAILED": return "Delivery failed; follow-up required";
    default: return "Status supplied by the backend";
  }
}
function ChainList({ items }: { items: ChainEvent[] }) { return items.length ? <div className="chain-event-list">{items.map((event) => <article className="chain-event-card" key={event.eventId}><header><div><span className="eyebrow">{event.eventType.replace(/_/g, " ")}</span><code title={event.eventId}>{event.eventId}</code></div><Badge>{event.status}</Badge></header><p className="chain-event-explainer">{chainStatusDescription(event.status)}</p><dl className="chain-event-facts">{event.amount != null && <div><dt>Recorded amount</dt><dd>{money(event.amount)}</dd></div>}<div><dt>Confirmations</dt><dd>{event.confirmations}</dd></div><div><dt>Submission attempts</dt><dd>{event.attemptCount}</dd></div><div><dt>Event time</dt><dd>{date(event.occurredAt)}</dd></div>{event.recordedAt && <div><dt>Confirmed at</dt><dd>{date(event.recordedAt)}</dd></div>}{event.blockNumber && <div><dt>Block number</dt><dd>{event.blockNumber}</dd></div>}</dl><div className={`chain-evidence ${event.status === "RECORDED" ? "evidence-recorded" : event.status === "FAILED" ? "evidence-failed" : "evidence-pending"}`}><strong>{event.status === "RECORDED" ? "Confirmed evidence" : event.status === "FAILED" ? "Delivery needs attention" : "Evidence not yet confirmed"}</strong><span>{event.status === "RECORDED" && event.transactionHash ? <code title={event.transactionHash}>{event.transactionHash.slice(0, 14)}…{event.transactionHash.slice(-10)}</code> : event.status === "FAILED" ? "The financial operation remains in the database. The backend tracks delivery failure separately." : "A successful loan or repayment operation is separate from blockchain confirmation."}</span></div></article>)}</div> : <p>No blockchain event records are available yet.</p>; }

function OfficerLoanRow({ loan, mutate }: { loan: Loan; mutate: PageContext["mutate"] }) {
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  const disburse = () => {
    setBusy(true);
    void mutate(`/officer/loans/${loan.id}/disbursement`, "POST", { amount: loan.principal })
      .then(() => { setDone(true); })
      .finally(() => setBusy(false));
  };
  const short = (id: string) => `${id.slice(0, 8)}…${id.slice(-6)}`;
  return <article className={`servicing-loan-card servicing-${loan.status.toLowerCase()}`}>
    <header className="servicing-loan-head"><div><span className="eyebrow">Loan record</span><strong title={loan.id}>{short(loan.id)}</strong></div><Badge>{loan.status}</Badge></header>
    <div className="servicing-reference"><span>Borrower application reference</span><code title={loan.applicationId}>{short(loan.applicationId)}</code></div>
    <dl className="servicing-facts"><div><dt>Approved principal</dt><dd>{money(loan.principal)}</dd></div><div><dt>Current balance</dt><dd>{money(loan.outstandingBalance)}</dd></div></dl>
    <div className="servicing-action">{loan.status === "APPROVED" ? <><p>Record one full disbursement of <strong>{money(loan.principal)}</strong> to activate this loan.</p><Button variant="secondary" disabled={done} loading={busy} onClick={disburse}>{done ? "Disbursement submitted" : busy ? "Recording disbursement…" : "Record full disbursement"}</Button></> : loan.status === "ACTIVE" ? <p className="servicing-state-ready"><strong>Active loan</strong><span>Full disbursement is recorded. Repayment simulation is available to the borrower.</span></p> : loan.status === "COMPLETED" ? <p className="servicing-state-complete"><strong>Repaid in full</strong><span>Official outstanding balance is zero.</span></p> : <p>This loan is currently {loan.status.toLowerCase().replace(/_/g, " ")}.</p>}</div>
  </article>;
}

function StaffAccounts({ data, mutate, refresh }: { data: Staff[] | null; mutate: PageContext["mutate"]; refresh: () => void }) {
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [role, setRole] = useState("LOAN_OFFICER"); const [message, setMessage] = useState("");
  return <><Header title="Staff accounts" description="Administrators may provision and manage staff access. Borrower accounts are not included in this view." /><Panel className="staff-create-panel"><div className="records-intro"><div><span className="eyebrow">Secure provisioning</span><h2>Provision a staff account</h2><p className="card-subtitle">New staff must change their temporary password at first sign-in.</p></div><span className="staff-role-mark">STAFF</span></div><form className="form-grid" onSubmit={(e) => { e.preventDefault(); setMessage(""); void mutate("/admin/users", "POST", { email, password, role }).then(() => { setEmail(""); setPassword(""); setMessage("Staff account created. Share the temporary password through your approved private channel."); refresh(); }).catch((reason) => setMessage(reason instanceof Error ? reason.message : "The staff account could not be created.")); }}><Field label="Staff email" value={email} onChange={setEmail} type="email" required /><Field label="Temporary password" value={password} onChange={setPassword} type="password" required hint="At least 12 characters. Never share the temporary password in this workspace." /><label className="field"><span>Role</span><select value={role} onChange={(e) => setRole(e.target.value)}><option value="LOAN_OFFICER">Loan Officer</option><option value="ADMINISTRATOR">Administrator</option></select></label><div className="staff-create-action"><Button type="submit">Create staff account</Button></div></form>{message && <p role="status" className="inline-notice">{message}</p>}</Panel><Panel className="records-panel staff-records"><div className="records-intro"><div><span className="eyebrow">Access management</span><h2>Current staff accounts</h2></div><span>{data?.length ?? 0} {data?.length === 1 ? "account" : "accounts"}</span></div>{!data?.length ? <p>No staff accounts are available. Provision a Loan Officer or Administrator to make one appear here.</p> : <div className="staff-account-list">{data.map((staff) => <StaffRow key={staff.id} staff={staff} mutate={mutate} refresh={refresh} />)}</div>}</Panel></>;
}
function StaffRow({ staff, mutate, refresh }: { staff: Staff; mutate: PageContext["mutate"]; refresh: () => void }) {
  const [role, setRole] = useState(staff.role); const [status, setStatus] = useState(staff.status); const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  useEffect(() => { setRole(staff.role); setStatus(staff.status); }, [staff.role, staff.status]);
  const changed = role !== staff.role || status !== staff.status;
  return <article className="staff-account-card"><header><div><span className="eyebrow">Staff account</span><strong className="staff-email">{staff.email}</strong></div><Badge>{status}</Badge></header><div className="staff-account-controls"><label><span>Role</span><select aria-label={`Role for ${staff.email}`} value={role} onChange={(e) => setRole(e.target.value)}><option value="LOAN_OFFICER">Loan Officer</option><option value="ADMINISTRATOR">Administrator</option></select></label><label><span>Account status</span><select aria-label={`Status for ${staff.email}`} value={status} onChange={(e) => setStatus(e.target.value)}><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option></select></label><div className="staff-created"><span>Account created</span><strong>{date(staff.createdAt)}</strong></div></div><footer><small>{changed ? "Unsaved access changes" : "Access settings are up to date"}</small><Button variant="secondary" className="btn-compact" disabled={!changed} loading={busy} onClick={() => { setError(""); setBusy(true); void mutate(`/admin/users/${staff.id}`, "PATCH", { role, status }).then(refresh).catch((e) => setError(e instanceof Error ? e.message : "Update failed.")).finally(() => setBusy(false)); }}>{busy ? "Saving…" : changed ? "Save access changes" : "Up to date"}</Button></footer>{error && <small role="alert" className="staff-inline-error">{error}</small>}</article>;
}
