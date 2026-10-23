# UI/UX refinement and frontend integration guide

## 1. Design goal

Upgrade the existing prototype into a professional, accessible financial application without
turning it into a crypto dashboard. Preserve its information architecture, restrained navy/teal
direction, role separation, status vocabulary, and operational-versus-blockchain distinction.

## 2. Optional skills and component sources

At session start, inspect the actual Claude Code skill/MCP inventory.

If installed, use relevant guidance from:

- `ui-ux-pro-max` for heuristic review, accessibility, and responsive patterns;
- `awesome-design` or `frontend-design` for visual direction;
- `21st-dev` or the 21st.dev MCP for component inspiration;
- `playwright` for browser validation;
- motion/animation skills for interaction choreography.

Rules:

- Never claim an unavailable skill was used.
- Never paste a 21st.dev component blindly.
- Do not introduce a second design language or generic dashboard kit.
- Adapt generated components to local tokens, semantics, React version, and accessibility.
- Prefer a small local component system over many unrelated dependencies.

## 3. Frontend architecture

Refactor the single prototype file incrementally:

```text
src/
  app/                 router, providers, authenticated shell
  components/
    ui/                Button, Field, Select, Dialog, Badge, Table, Skeleton
    domain/            LoanLifecycle, OperationChainStatus, EventTimeline
  features/
    auth/
    borrower/
    officer/
    admin/
  services/            typed API client, auth/session, error mapping
  styles/              tokens, motion, responsive utilities
  test/                fixtures and component-test helpers
```

Do not refactor all markup before one real API slice works. Move components as each vertical slice
is integrated.

## 4. Visual system

Define semantic tokens rather than scattered colors:

- surfaces: canvas, elevated, inset, overlay;
- text: strong, default, muted, inverse;
- borders: subtle, default, focused;
- actions: primary, secondary, destructive;
- statuses: success, pending, danger, informational;
- blockchain states: recorded, pending, failed, unavailable;
- spacing, radii, shadows, type scale, and motion durations.

Use a sophisticated palette:

- deep navy for trust and navigation;
- neutral warm/cool white surfaces with clear elevation;
- restrained teal for primary and positive actions;
- amber for pending;
- red only for errors, rejection, and destructive confirmation;
- subtle gradients for hierarchy, never rainbow or neon crypto effects.

Recommended gradient uses:

- low-contrast hero mesh behind one focal product mockup;
- dark navy-to-slate administrator/landing surfaces;
- subtle teal edge glow for verified-event graphics;
- status fills that retain text contrast.

Avoid gradients inside dense tables, form controls, or every card.

## 5. Layout standards

- Use a consistent content max width and 12-column desktop logic.
- Keep primary content left-aligned and actions predictably placed.
- Use 8-point spacing rhythm.
- Make dashboards answer a role-specific question, not display random charts.
- On tablet, collapse side navigation and reduce multi-column forms.
- On mobile, convert tables to labeled cards, use a compact header, stack actions, and preserve
  lifecycle readability without tiny text.
- Keep important status and action information above the fold.
- Use sticky summary/action panels only where they do not hide content.

## 6. Required reusable components

- role-aware application shell and navigation;
- page header and breadcrumbs/back action;
- button, icon button, field, select, textarea, checkbox;
- confirmation dialog with focus trap and return focus;
- semantic status badge with icon/text, never color alone;
- loading skeleton matching final content;
- empty, error, unauthorized, not-found, and success states;
- responsive data table/card view;
- application and loan lifecycle visualization;
- `OperationChainStatus` with separate operational and recording states;
- event verification timeline with opaque references;
- toast or inline feedback with accessible live regions.

## 7. Motion guidelines

Motion should clarify state and hierarchy:

- page/route content: 160–240 ms fade and slight translate;
- dialogs: 140–200 ms opacity/scale with focus management;
- status changes: brief highlight or progress transition;
- lifecycle updates: staged line/node transition;
- skeleton shimmer: restrained and disabled for reduced motion;
- landing visuals: slow ambient movement only;
- no parallax on critical forms and no bouncing financial values.

Honor `prefers-reduced-motion`. Animations must never delay form submission, hide errors, or imply
blockchain confirmation before the API confirms it.

## 8. UX behavior by workflow

### Authentication

- Generic credential error.
- Preserve safe form input after validation failure.
- Clearly identify the active prototype/real role context.
- Never expose whether an email exists during password recovery.

### Applications

- Autosave only if truly supported; otherwise do not imply it.
- Show required fields and server errors next to fields.
- Review before submit.
- After submit, show application ID, `PENDING`, timestamp, and next step.

### Decisions

- Approve and reject are visually distinct.
- Dialog identifies the exact application.
- Rejection wording never implies borrower-account rejection.
- After completion, show operational result first and chain state second.

### Disbursement and repayments

- Show current loan state and balance.
- Confirm amount/date/reference.
- Prevent overpayment in UI and server.
- Show balance after repayment during review.
- Final repayment clearly produces `COMPLETED`.

### Blockchain UX

- Use “verification,” “event evidence,” and “recording,” not wallet/trading terminology.
- Explain that sensitive information remains off-chain.
- Truncate transaction references visually while retaining accessible copy/view behavior.
- For outages, show verification unavailable, not an empty history.

## 9. Accessibility and quality gate

- WCAG 2.2 AA contrast.
- Full keyboard operation and visible focus.
- Semantic landmarks, labels, headings, table headers, and dialog semantics.
- Icon-only controls have accessible names.
- Status is not communicated by color alone.
- Inputs expose help and errors through `aria-describedby`.
- Async updates use appropriate live regions.
- Test 320 px mobile through large desktop without horizontal page scrolling.
- Check zoom at 200%.

## 10. UI review pass

Before declaring a screen complete, ask:

1. What question is this page answering for this role?
2. Is the primary action unambiguous?
3. Is the current application/loan state immediately visible?
4. Are operation and blockchain recording visibly separate?
5. Are empty, loading, validation, error, and forbidden states implemented?
6. Does mobile reorganize rather than merely shrink?
7. Is every animation purposeful and reduced-motion safe?
8. Is any decorative choice making the financial workflow less clear?
