# PesaChain Figma-importable wireframes

## What this is
This package contains **31 individual SVG screen/artboard files** created as editable vector source. There is no configured Figma connector in the current environment, so no native `.fig` file, hosted Figma project or prototype links are claimed. Figma accepts SVG import; vector groups, shapes and text can be selected and adjusted after import. Depending on importer/version, some items may initially arrive as a grouped vector; ungroup in Figma to edit individual parts. This is not a native Figma component library or auto-layout file.

## Import
1. Download and extract `PesaChain_Figma_Import_SVG.zip`.
2. In Figma, create a new Design file.
3. Drag one or more `.svg` files onto the canvas, or use **File → Place image** and choose the SVGs. If your Figma editor treats an SVG as a flat image, use **File → Open** for an SVG or paste SVG source into a compatible SVG-to-Figma importer/plugin, then ungroup. SVG text/shapes remain editable when imported as vectors; fonts may substitute if Inter is unavailable.
4. Rename imported top-level layers to the screen name; organize into Figma pages: `00 Foundations`, `01 Authentication`, `02 Borrower`, `03 Loan Officer`, `04 Administrator`, `05 Responsive`. Convert repeated controls to native Figma components after import.
5. To prototype, add Figma prototype connections manually using the flow map below. Static SVGs do not carry native Figma prototype links.

## Package contents
Every canvas is a 1440×1000 SVG (mobile examples are narrow device views placed on that presentation canvas). SVGs use named groups/IDs for navigation, page headings, cards, tables, actions and status badges. Palette is restrained deep fjord/navy + trust teal, pale mint, white and neutral canvas, with accessible text-bearing status badges: green success, amber pending, red rejected/error, blue informational. Typography uses Inter/system sans. The 8px spacing rhythm and 42–48px controls are design suggestions. All names/sample data are fictional.

## Screen inventory
- Foundations: `00-design-system`
- Authentication: `01-auth-login`, `02-borrower-registration`
- Borrower: `03-borrower-dashboard`, `04-loan-application-form`, `05-application-review-submit`, `06-borrower-application-details`, `07-borrower-loan-details`, `08-borrower-repayment-history`, `27-borrower-application-history`, `28-borrower-profile`
- Loan Officer: `09-officer-dashboard`, `10-officer-application-queue`, `11-officer-application-review`, `12-officer-approval-confirmation`, `13-officer-rejection-confirmation`, `14-officer-disbursement`, `15-officer-record-repayment`, `16-officer-loans`, `17-blockchain-event-history`, `18-blockchain-pending-state`, `19-blockchain-failure-state`, `29-officer-loan-detail`, `30-loan-completion-success`
- Administrator: `20-admin-dashboard`, `21-admin-user-management`, `22-admin-user-details-role`, `23-admin-activity-log`
- Responsive examples: `24-mobile-borrower-dashboard`, `25-mobile-officer-queue`, `26-mobile-borrower-loan-details`

## Flow map (connect manually in Figma)
Borrower: Sign in → Dashboard → Application form → Review/submit → Application detail (PENDING) → decision → Loan detail → Repayment history → COMPLETED.
Officer: Dashboard → Application queue → Application review → Approve confirmation or Reject confirmation → Disbursement → Active loan → Record repayment → completion; Blockchain history → pending/failure/confirmed states.
Administrator: Dashboard → User management → User details/role → Activity log.

## Requirements/permissions notes
- Borrowers can access only their own profile, applications, loans, repayments and event history.
- Loan officers review/decide applications, record disbursements and repayments, and inspect loan/event information.
- Administrator screens cover user/role management and monitoring; no approve/reject or repayment controls are granted to admins.
- Operational loan status and blockchain recording status are shown separately. A chain failure does not mean the operational loan action failed. No PII is represented as on-chain data.
- The existing SRS permits no payment integrations, crypto wallets, tokens, AI scoring, automated approval, government ID integration or native mobile app.

## Design assumptions and cautions
These are wireframes and illustrative content, not requirements or implemented product behavior. Sample currency displays KES because of project context; the SRS does not conclusively specify currency, amount constraints, application fields, decision-note policy, role access to PII, confirmation duration, or final status vocabulary. This layout uses `ACTIVE` immediately after the disbursement record and keeps the UI chain status independent; validate status naming with the backend design. Forgot-password and notification capabilities are not included as operational features because they are not specified by the SRS. Responsive examples are presentation proposals, not native mobile application screens. Do not use sample user or loan data in a live system.

## Accessibility & developer handoff
Use text plus status labels (not color alone), visible focus outlines, descriptive field labels, keyboard order, and adequate contrast. Recommended React primitives: `AppShell`, `Sidebar`, `Topbar`, `PageHeader`, `StatusBadge`, `MetricCard`, `DataTable`, `FormField`, `Alert`, `Timeline`, `ConfirmationDialog`, `BlockchainEventRow`. Import SVGs as references; rebuild native Figma components/auto-layout before implementation if editable responsive behavior is required.
