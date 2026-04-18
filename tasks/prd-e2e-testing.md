# PRD: E2E Integration Testing with Ferrum (Ruby)

## Introduction

Add browser-based E2E tests for the Crypto Bet Engine frontend using Ferrum — a Ruby gem that drives Chrome directly via Chrome DevTools Protocol. No npm, no chromedriver, no Selenium. Tests verify the full user flow: wallet creation, live prices, bet placement, and settlement — ensuring the JS frontend works correctly with the backend API and WebSocket.

## Goals

- Verify frontend-backend integration end-to-end in a real browser
- Test WebSocket-driven DOM updates (prices, bet settlements)
- Run headless in CI (GitHub Actions)
- Zero npm/node dependency — Ruby only
- Complement existing Java unit/integration tests

## Approach: Ferrum + Minitest

**Why Ferrum:**
- Talks directly to Chrome via CDP — no chromedriver binary needed
- Single gem install, works with system Chrome (auto-detects on macOS ARM, no Rosetta)
- Ruby-native — fits user's tooling preference
- Supports headless mode for CI
- Can wait for DOM changes driven by WebSocket

**Test structure:** `test/e2e/` directory with Ruby test files, Gemfile, and a run script.

**Prerequisites:** Docker running (app + postgres + redis via docker-compose), Chrome installed.

## User Stories

### US-E01: E2E test scaffolding
**Description:** As a developer, I need the test infrastructure set up so I can write E2E tests.

**Acceptance Criteria:**
- [ ] Create `test/e2e/Gemfile` with `ferrum ~> 0.17` and `minitest ~> 5.0` gems
- [ ] Create `test/e2e/test_helper.rb` with:
  - Browser setup/teardown (`browser.quit` in teardown, not `close`)
  - `wait_for(selector, timeout)` — polls DOM for element presence
  - `wait_for_visible(selector, timeout)` — polls for element not display:none
  - `wait_for_hidden(selector, timeout)` — polls for element hidden/removed
  - `wait_for_text(selector, text, timeout)` — polls for element text containing string
  - `wait_for_text_match(selector, pattern, timeout)` — polls for regex match
  - `wait_for_class(selector, css_class, timeout)` — polls for CSS class
- [ ] Headless by default, `HEADLESS=false` env var to watch tests visually
- [ ] Base URL defaults to `http://localhost:8080`, configurable via `APP_URL` env var
- [ ] Screenshots saved to `test/e2e/screenshots/` on test failure
- [ ] Helper methods: `create_wallet(nickname, balance)`, `wait_for_ws_connection`, `wait_for_prices`, `place_bet(symbol, direction, amount)`
- [ ] Create `src/main/resources/application-e2e.yml` with `betting.default-duration-seconds: 10`
- [ ] `bundle install` in `test/e2e/` succeeds
- [ ] A trivial smoke test (page loads, `h1.header__title` contains "Crypto Bet Engine") passes

### US-E02: Wallet creation test
**Description:** As a developer, I want to verify that wallet creation works end-to-end in the browser.

**Acceptance Criteria:**
- [ ] Test opens http://localhost:8080
- [ ] Verifies `#wallet-modal` is visible (no wallet in fresh browser)
- [ ] Fills `#wallet-nickname` with "TestBot" and `#wallet-initial-balance` with "1000"
- [ ] Clicks `#create-wallet-form button[type="submit"]`
- [ ] Verifies `#wallet-modal` hides (display:none)
- [ ] Verifies `#wallet-name` text is "TestBot"
- [ ] Verifies `#wallet-balance` text is "$1000.00"
- [ ] Verifies `#wallet-details` contains "TestBot" and balance text
- [ ] Validation: submit with empty nickname — modal stays open
- [ ] Validation: submit with balance "0" — error in `#wallet-form-error`

### US-E03: Live price display test
**Description:** As a developer, I want to verify that WebSocket prices appear in the browser.

**Acceptance Criteria:**
- [ ] After wallet creation, wait for `#conn-status` to have class `conn-status--connected`
- [ ] Wait for `#price-card-BTCUSDT` to appear (replaces skeleton placeholders)
- [ ] Wait for `#price-card-ETHUSDT` to appear
- [ ] Verify `#price-BTCUSDT` text matches `/\$[\d,.]+/` (non-zero price)
- [ ] Verify `#price-ETHUSDT` text matches `/\$[\d,.]+/` (non-zero price)
- [ ] Timeout: 15 seconds for prices to appear (Binance feed may take a moment)

### US-E04: Bet placement test
**Description:** As a developer, I want to verify the bet placement flow works in the browser.

**Acceptance Criteria:**
- [ ] Verify `#bet-submit-btn` is `disabled` initially
- [ ] Select "BTCUSDT" from `#bet-symbol` dropdown
- [ ] Click `.bet-dir-btn[data-dir="UP"]`
- [ ] Verify `#bet-odds` shows a numeric value matching `/\d+\.\d+/`
- [ ] Verify `#bet-info` is visible (not display:none)
- [ ] Enter "100" in `#bet-amount`
- [ ] Verify `#bet-payout` shows a value matching `/\$\d+/`
- [ ] Verify `#bet-submit-btn` is no longer disabled
- [ ] Click `#bet-submit-btn`
- [ ] Verify `#bet-confirm-modal` becomes visible
- [ ] Verify `#bet-confirm-details` text contains "BTCUSDT", "UP", "$100.00"
- [ ] Click `#bet-confirm-btn`
- [ ] Verify `#bet-confirm-modal` hides
- [ ] Verify `.toast--success` appears
- [ ] Verify `.active-bet` appears in `#active-bets` containing "BTCUSDT"

### US-E05: Bet settlement test
**Description:** As a developer, I want to verify that bet settlement updates the UI correctly.

**Acceptance Criteria:**
- [ ] Place a bet via `place_bet` helper
- [ ] Wait for `.active-bet` to appear
- [ ] Record `#wallet-balance` text
- [ ] Wait for `.active-bet__countdown` text to contain "Settling" (timeout: 25s with e2e profile)
- [ ] Wait for `#active-bets` text to contain "No active bets" (timeout: 20s)
- [ ] Verify `#wallet-balance` text has changed from initial value

### US-E06: Bet history test
**Description:** As a developer, I want to verify the bet history view works after settlement.

**Acceptance Criteria:**
- [ ] After a bet settles, click `.sidebar__link[data-view="history"]`
- [ ] Wait for `.history-table` to appear
- [ ] Verify `.history-table tbody tr` exists (at least 1 row)
- [ ] Verify row text contains: symbol, direction, stake amount
- [ ] Verify `.history-table .badge` exists with text matching `/WON|LOST|PUSH/`
- [ ] Click `.history-tab[data-filter="WON"]` and verify filter applies

### US-E07: Deposit test
**Description:** As a developer, I want to verify the deposit flow works.

**Acceptance Criteria:**
- [ ] Wait for `#deposit-toggle-btn` to appear (inside dynamically rendered wallet card)
- [ ] Click `#deposit-toggle-btn`
- [ ] Verify `#deposit-section` becomes visible
- [ ] Enter "500" in `#deposit-amount`
- [ ] Submit `#deposit-form`
- [ ] Verify `.toast--success` appears
- [ ] Verify `#wallet-balance` text is "$1500.00"

### US-E08: Run script for local testing
**Description:** As a developer, I need a script to run E2E tests locally.

**Acceptance Criteria:**
- [ ] Create `test/e2e/run.sh` that:
  - Starts docker-compose with `SPRING_PROFILES_ACTIVE=prod,e2e`
  - Waits for app health via `/actuator/health`
  - Runs `bundle exec ruby test_*.rb`
  - Stops docker-compose on exit (trap EXIT)
- [ ] Script exits with test exit code (0 = pass, non-zero = fail)
- [ ] Script supports `HEADLESS=false` env var for visual debugging
- [ ] Create `docker-compose.e2e.yml` override that sets `SPRING_PROFILES_ACTIVE=prod,e2e`

### US-E09: CI integration
**Description:** As a developer, I need E2E tests running in GitHub Actions.

**Acceptance Criteria:**
- [ ] Add E2E test job to `.github/workflows/ci.yml` (runs after unit tests)
- [ ] CI installs `google-chrome-stable` (NOT `chromium-browser` snap)
- [ ] CI installs Ruby 3.2+ via `ruby/setup-ruby@v1`
- [ ] CI runs `test/e2e/run.sh`
- [ ] Ferrum browser options include `'no-sandbox': nil` on Linux
- [ ] Upload `test/e2e/screenshots/` as artifact on failure
- [ ] Tests pass in CI

## Functional Requirements

- FR-1: All tests in `test/e2e/` directory, separate from Java tests
- FR-2: Ferrum gem for browser automation, Minitest for assertions
- FR-3: Each test starts with a fresh browser context (no localStorage carryover)
- FR-4: Tests run against Docker-composed app (not local bootRun)
- FR-5: Default timeout 10s for DOM waits, 25s for settlement (with e2e profile: 10s bet + 15s buffer)
- FR-6: Screenshots saved on test failure to `test/e2e/screenshots/`
- FR-7: Tests output standard Minitest format (dots, F for failures)
- FR-8: E2E profile shortens bet duration to 10s via `application-e2e.yml`

## Non-Goals

- No visual regression testing (screenshot comparison)
- No performance/load testing
- No mobile viewport testing
- No multi-browser testing (Chrome only)
- No parallel test execution
- No test data seeding via API — tests use the UI for all actions
- No API contract tests (separate PRD if needed)
- No wallet switching tests (separate PRD if needed)

## Technical Considerations

- **macOS ARM (M4):** Ferrum works natively, no Rosetta needed. Chrome auto-detected at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`
- **CI (Ubuntu):** Use `google-chrome-stable` apt package, NOT `chromium-browser` snap. Pass `'no-sandbox': nil` browser option (GitHub runners run as root)
- **Teardown:** Use `browser.quit` (not `browser.close`) to clean up Chrome processes and Ruby resources
- **Ferrum version:** >= 0.17 recommended for better CDP error handling
- **WebSocket testing:** Indirect — verify DOM changes, not raw WS frames
- **Bet settlement:** With `application-e2e.yml` profile, bets settle in ~10s instead of 60s
- **Idempotent tests:** Each test creates its own wallet, places its own bets. Fresh browser context per test prevents localStorage bleed
- **Deposit button timing:** `#deposit-toggle-btn` is dynamically rendered by `renderWalletCard()` — tests must wait for wallet card to render before clicking

## Selector Reference

| Element | Selector | Notes |
|---------|----------|-------|
| Wallet modal | `#wallet-modal` | Check style.display |
| Nickname input | `#wallet-nickname` | |
| Balance input | `#wallet-initial-balance` | |
| Create wallet submit | `#create-wallet-form button[type="submit"]` | |
| Header nickname | `#wallet-name` | |
| Header balance | `#wallet-balance` | Format: `$1000.00` (no comma) |
| Connection status | `#conn-status` | Class: `conn-status--connected` |
| Price card (BTC) | `#price-card-BTCUSDT` | Dynamic |
| Price text (BTC) | `#price-BTCUSDT` | Dynamic |
| Symbol dropdown | `#bet-symbol` | `<select>` |
| UP button | `.bet-dir-btn[data-dir="UP"]` | |
| DOWN button | `.bet-dir-btn[data-dir="DOWN"]` | |
| Amount input | `#bet-amount` | |
| Odds display | `#bet-odds` | Inside `#bet-info` |
| Exposure display | `#bet-exposure` | |
| Payout display | `#bet-payout` | |
| Place Bet button | `#bet-submit-btn` | Disabled until valid |
| Confirm modal | `#bet-confirm-modal` | |
| Confirm button | `#bet-confirm-btn` | |
| Active bets | `#active-bets` | |
| Countdown | `.active-bet__countdown` | |
| History nav | `.sidebar__link[data-view="history"]` | |
| History table | `.history-table` | Dynamic |
| Filter tabs | `.history-tab[data-filter="WON"]` | |
| Deposit button | `#deposit-toggle-btn` | Dynamic, inside wallet card |
| Deposit amount | `#deposit-amount` | |
| Deposit form | `#deposit-form` | |
| Toast success | `.toast--success` | |
| Network banner | `#network-banner` | Class: `network-banner--visible` |

## Success Metrics

- All 9 test stories pass locally and in CI
- Total test suite runs in under 90 seconds (with e2e profile)
- Tests catch real integration bugs (like the field name mismatches we found)
- No flaky tests (retry-resistant with proper wait_for helpers)
