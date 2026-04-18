# PRD: Crypto Bet Engine — Browser Frontend

## Introduction

A production-ready single-page frontend for the Crypto Bet Engine, served as static files from Spring Boot. Users can create wallets, watch live crypto prices, place bets on price direction, and track results in real-time. Built with vanilla HTML/CSS/JS (no framework, no build step) with a dark trading-style theme.

## Goals

- Provide a complete browser-based interface to all betting engine features
- Show real-time price updates via WebSocket with sparkline visualization
- Enable informed bet placement with live odds, exposure, and estimated payout
- Production-quality UX: responsive, accessible, polished dark theme
- Zero build tooling — works by opening the page, no npm/webpack/etc.

## User Stories

### US-F01: App Shell and Dark Theme
**Description:** As a user, I want a polished dark-themed layout so the app feels like a real trading platform.

**Acceptance Criteria:**
- [ ] Single `index.html` in `src/main/resources/static/`
- [ ] CSS in separate `static/css/style.css`
- [ ] JS in separate `static/js/app.js`
- [ ] Dark theme: dark backgrounds (#1a1a2e / #16213e palette), light text, accent colors for UP (green) and DOWN (red)
- [ ] Responsive layout: sidebar + main content on desktop, stacked on mobile (<768px)
- [ ] Header with app name and wallet indicator
- [ ] Navigation between views: Dashboard, Bet History
- [ ] Verify in browser

### US-F02: Wallet Creation
**Description:** As a new user, I want to create a wallet with a nickname so I can start using the platform.

**Acceptance Criteria:**
- [ ] On first visit (no wallet in localStorage), show a welcome/create-wallet modal
- [ ] Form fields: nickname (text input), initial balance (number input, min 0)
- [ ] Calls `POST /api/wallets` with `{ "nickname": "<name>", "initialBalance": <amount> }`
- [ ] On success, store `walletId` and `nickname` in localStorage
- [ ] Modal closes, app loads with wallet context
- [ ] Validation: nickname required (min 2 chars), balance required (> 0)
- [ ] Error display on API failure
- [ ] Verify in browser

### US-F03: Wallet Dashboard
**Description:** As a user, I want to see my wallet balance and recent activity at a glance.

**Acceptance Criteria:**
- [ ] Fetch wallet details from `GET /api/wallets/{id}` on load
- [ ] Display: nickname, current balance (formatted as USD), wallet ID (truncated)
- [ ] Deposit button opens inline form with amount input
- [ ] Deposit calls `POST /api/wallets/{id}/deposit` with `{ "amount": <value> }`
- [ ] Balance updates after successful deposit
- [ ] Show count of active (PENDING) bets
- [ ] Handle wallet-not-found (cleared localStorage, prompt re-create)
- [ ] Verify in browser

### US-F04: Live Price Ticker
**Description:** As a user, I want to see real-time crypto prices so I can make informed bets.

**Acceptance Criteria:**
- [ ] Connect to WebSocket via STOMP at `/ws`
- [ ] Subscribe to `/topic/prices` for all symbol updates
- [ ] Display price cards for each symbol (BTCUSDT, ETHUSDT)
- [ ] Each card shows: symbol name, current price (formatted), price change arrow (up/down from previous)
- [ ] Price text flashes green on increase, red on decrease (brief CSS animation)
- [ ] Mini sparkline showing last 50 price points (canvas or SVG, no library)
- [ ] Auto-reconnect WebSocket on disconnect with visual indicator
- [ ] Verify in browser

### US-F05: Bet Placement Panel
**Description:** As a user, I want to place a bet with full information about odds, exposure, and payout so I can make a confident decision.

**Acceptance Criteria:**
- [ ] Bet form: symbol selector (dropdown), direction toggle (UP/DOWN buttons), amount input
- [ ] On symbol + direction selection, fetch odds from `GET /api/odds?symbol={s}&direction={d}`
- [ ] On symbol selection, fetch exposure from `GET /api/exposure/{symbol}`
- [ ] Display before confirming: current odds (decimal), current exposure for symbol, estimated payout (amount * odds)
- [ ] Confirmation step: "Place Bet" shows summary, user clicks "Confirm" or "Cancel"
- [ ] On confirm, call `POST /api/bets` with `{ "walletId", "symbol", "direction", "amount" }`
- [ ] Success: show toast notification, refresh wallet balance
- [ ] Error: show error message (insufficient balance, etc.)
- [ ] Amount validation: min 1, max = wallet balance
- [ ] Verify in browser

### US-F06: Live Bet Updates
**Description:** As a user, I want to see my bets settle in real-time so I know immediately if I won.

**Acceptance Criteria:**
- [ ] Subscribe to `/topic/bets/{walletId}` via STOMP
- [ ] On bet update event, show toast notification with result (WON/LOST/PUSH/VOID)
- [ ] Toast color: green for WON, red for LOST, yellow for PUSH/VOID
- [ ] Include payout amount in WON notification
- [ ] Auto-refresh wallet balance after settlement
- [ ] Update bet in active bets list if visible
- [ ] Verify in browser

### US-F07: Active Bets Display
**Description:** As a user, I want to see my currently active (pending) bets on the dashboard.

**Acceptance Criteria:**
- [ ] Section on dashboard showing pending bets
- [ ] Fetch from `GET /api/wallets/{id}/bets?status=PENDING&page=0&size=10`
- [ ] Each bet shows: symbol, direction (with color), amount, placed time, target price
- [ ] Countdown or time indicator showing bet duration remaining
- [ ] Auto-refresh list when bet update received via WebSocket
- [ ] Empty state: "No active bets — place one above!"
- [ ] Verify in browser

### US-F08: Bet History View
**Description:** As a user, I want to browse my past bets to review my performance.

**Acceptance Criteria:**
- [ ] Separate view/page accessible from navigation
- [ ] Fetch from `GET /api/wallets/{id}/bets?page=0&size=20`
- [ ] Filter tabs: All, Won, Lost, Push, Void
- [ ] Table/list with columns: symbol, direction, amount, odds, payout, status, placed time, settled time
- [ ] Status badges with color coding (green/red/yellow/gray)
- [ ] Pagination: Previous/Next buttons, current page indicator
- [ ] Verify in browser

### US-F09: Wallet Switching
**Description:** As a user, I want to switch between wallets or create a new one without clearing browser data.

**Acceptance Criteria:**
- [ ] Wallet indicator in header is clickable, opens dropdown
- [ ] Dropdown shows current wallet nickname + ID
- [ ] Options: "Create New Wallet", "Switch Wallet (enter ID)"
- [ ] Switch wallet: input field for wallet ID, validates via `GET /api/wallets/{id}`
- [ ] On switch: update localStorage, reload all data
- [ ] Verify in browser

### US-F10: Error Handling and Loading States
**Description:** As a user, I want clear feedback when things are loading or go wrong.

**Acceptance Criteria:**
- [ ] Loading spinners on initial data fetch
- [ ] Skeleton placeholders for price cards while connecting
- [ ] WebSocket connection status indicator (connected/reconnecting/disconnected)
- [ ] API error responses displayed as user-friendly messages
- [ ] Network failure: banner at top "Connection lost — retrying..."
- [ ] All interactive elements disabled during pending API calls (prevent double-submit)
- [ ] Verify in browser

## Functional Requirements

- FR-1: All static files served from `src/main/resources/static/` — no build step required
- FR-2: Single `index.html` entry point with separate CSS and JS files
- FR-3: WebSocket connection via STOMP protocol using `sockjs` and `stomp.js` (CDN)
- FR-4: All API calls use `fetch()` with proper error handling
- FR-5: Wallet state persisted in `localStorage` (walletId, nickname)
- FR-6: Price sparkline rendered with canvas (no chart library)
- FR-7: Responsive breakpoint at 768px (mobile/desktop)
- FR-8: CSS animations for price flashes, toast notifications (no animation library)
- FR-9: All monetary values formatted to 2 decimal places (USD) or 8 decimal places (crypto prices)
- FR-10: Toast notifications auto-dismiss after 5 seconds, stackable

## Non-Goals

- No user authentication or password protection
- No server-side rendering
- No build tools, bundlers, or transpilers
- No CSS framework (Bootstrap, Tailwind, etc.)
- No JS framework (React, Vue, etc.)
- No chart library (Chart.js, D3, etc.)
- No trade execution or real money — this is simulated betting
- No admin panel or back-office UI
- No i18n/localization
- No PWA/offline support

## Design Considerations

- Dark color palette: `#0f0f1a` (bg), `#1a1a2e` (cards), `#16213e` (accents), `#e0e0e0` (text)
- Green `#00e676` for UP/WON, Red `#ff1744` for DOWN/LOST, Yellow `#ffd600` for PUSH
- Monospace font for prices and numbers (e.g., `JetBrains Mono` from Google Fonts)
- Sans-serif for UI text (system font stack)
- Card-based layout with subtle borders and shadows
- Micro-animations: price flash, toast slide-in, sparkline draw

## Technical Considerations

- SockJS + STOMP.js loaded from CDN (unpkg/cdnjs) for WebSocket
- Sparkline: lightweight canvas drawing, store last 50 data points per symbol in memory
- CSS custom properties (variables) for theme colors — easy to adjust
- JS modules not used (broad compat) — single `app.js` with IIFE or simple structure
- `fetch()` wrapper with base URL config and error handling
- Backend CORS already allows `*` on WebSocket; verify REST endpoints also allow browser origin

## Success Metrics

- User can go from first page load to placing a bet in under 30 seconds
- Live prices update within 1 second of backend receiving Binance data
- Bet settlement notification appears within 1 second of settlement
- Page works on Chrome, Firefox, Safari (latest versions)
- Fully usable on mobile (375px width and up)

## Open Questions

- Does `POST /api/wallets` accept a `nickname` field, or does it need to be added to the backend?
- Does the wallet creation request body match `{ "nickname": "...", "initialBalance": ... }` or different shape?
- Are CORS headers configured for REST endpoints (not just WebSocket)?
- Is `sockjs-client` needed or does the backend support raw WebSocket upgrade for STOMP?