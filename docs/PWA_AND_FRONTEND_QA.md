# Frontend PWA and verification notes

## PWA behavior

PesaChain now supplies a web app manifest and 192 px, 512 px, and maskable 512 px icons derived
from the existing PesaChain mark. The mark artwork is not modified; it is centered with safe padding
on the approved Champagne background. The theme color is Emerald Ink (`#064E3B`). The manifest uses
relative start and scope paths so it follows the application's base URL.

The production service worker caches only versioned Vite build assets and the three public app
icons. It ignores non-GET requests and explicitly bypasses `/api/` requests. It does not cache HTML,
sessions, authentication responses, borrower records, loan details, or financial operations. The
cache is bounded to 80 assets and old cache versions are removed on activation. This is installable
web-app support with conservative static-asset caching; it is not full offline application support.

Registration is enabled only in production builds and only when the browser supports service
workers. Installation UI is left to the browser; the application does not display an install button
when it cannot establish installability.

## Verification boundary

Manifest parsing, icon dimensions and presence, Vite production output, and local HTTP responses for
the manifest, worker, and icons can be checked without browser access. A browser must still verify
the actual install prompt, installed launch behavior, service-worker lifecycle/update behavior, and
visual rendering. Do not claim installability or offline support until those browser checks have
been performed on the intended HTTPS host. Localhost is treated as a secure context by modern
browsers; other deployment hosts must provide HTTPS.

Browser automation is currently unavailable in the Codex Work environment. Consequently, the
landing page and dashboard layouts at device widths, keyboard/screen-reader journeys, contrast across
all component states, and role-based browser journeys remain unverified here.

## Product contact and policies

The landing page now displays the contact details supplied by the project owner: `jamesbriandungu@gmail.com`,
`0794523302`, and Njoro, Nakuru County. Email and telephone links open the user's corresponding
contact application. No privacy-policy page or terms page exists, so the site does not link to
placeholder legal pages.

The main application wordmark and preferred favicon use the transparent Emerald Ink/Champagne
linked-ledger SVG. Existing raster PWA icons remain as compatibility fallbacks; verify the installed
icon appearance on each target browser before treating the icon update as fully browser-verified.
