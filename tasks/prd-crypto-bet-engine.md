# PRD: Crypto Bet Engine

## Introduction

A real-time crypto betting engine where users watch live crypto prices (from Binance) and place bets on price direction (e.g., "BTC goes UP in 60 seconds"). After the bet window expires, the system auto-settles: winners get payouts, losers already had stake deducted. Built with Java 21 / Spring Boot to learn the stack deeply while producing a production-grade system.

This is not a toy CRUD — it's a stateful, concurrent, real-time system covering wallet management, live price feeds, WebSocket communication, bet placement with odds/margin, and automated settlement.

## Goals

- Build a fully functional crypto bet engine covering Phase 1 (Foundation) and Phase 2 (Real-Time)
- Learn Java 21 / Spring Boot patterns: Virtual Threads, JPA, WebSocket, scheduling, concurrency
- Produce a polished, production-grade system demonstrating real-time architecture
- Implement proper betting mechanics: decimal odds, vigorish, exposure tracking, idempotency
- Support local development (brew Postgres + Redis) with production-ready config for later CI/CD deployment

## Development Approach: TDD

Every story follows strict TDD:

1. **Write tests first** — define expected behavior, verify tests fail (red)
2. **Implement** — write minimum code to make tests pass (green)
3. **Refactor** — clean up while keeping tests green

Tests are not an afterthought — they ARE the first deliverable of each story.

## User Stories

### US-001: Spring Boot Project Scaffolding
**Description:** As a developer, I need a properly configured Spring Boot project so that I have a solid foundation to build on.

**Acceptance Criteria:**
- [ ] Spring Boot 3.x project with Java 21, Gradle (Kotlin DSL)
- [ ] Dependencies: Web, JPA, PostgreSQL, WebSocket, Redis (Lettuce), Flyway
- [ ] Application connects to local PostgreSQL and Redis
- [ ] `application.yml` with local profile and placeholder for production profile
- [ ] Project compiles and starts without errors
- [ ] Virtual Threads enabled for request handling

**Note:** No TDD for this story — it's pure scaffolding. Verify by compiling and starting.

---

### US-002: Wallet Entity and REST API
**Description:** As a user, I want to create a wallet and check my balance so that I can manage funds for betting.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: create wallet returns 201 with UUID and initial balance
- [ ] Test: get wallet returns correct balance
- [ ] Test: deposit increases balance
- [ ] Test: deposit with zero/negative amount returns 400
- [ ] Test: get non-existent wallet returns 404
- [ ] Test: concurrent deposits on same wallet don't lose money

**Step 2 — Implementation (make tests pass):**
- [ ] `Wallet` entity: id (UUID), balance (BigDecimal), currency (String default "USD"), created_at, updated_at
- [ ] Flyway migration: wallets table with `DECIMAL(19,4)` for balance
- [ ] `POST /api/wallets` — create wallet with initial balance
- [ ] `GET /api/wallets/{id}` — returns wallet with current balance
- [ ] `POST /api/wallets/{id}/deposit` — add funds to wallet
- [ ] Pessimistic locking (`SELECT FOR UPDATE`) on wallet row for balance mutations
- [ ] Validation: balance cannot go negative, deposit must be positive

---

### US-003: Bet Entity and Placement API
**Description:** As a user, I want to place a bet on crypto price direction so that I can wager on market movements.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: place bet returns 201 with bet details, odds locked at 1.85
- [ ] Test: wallet balance decreases by stake after placing bet
- [ ] Test: bet with insufficient balance returns 409
- [ ] Test: bet with invalid symbol returns 400
- [ ] Test: bet with stake < $1 or > $10,000 returns 400
- [ ] Test: bet with invalid duration (not 30/60/120/300) returns 400
- [ ] Test: duplicate idempotency_key returns same bet (200), wallet not double-charged
- [ ] Test: concurrent bets on same wallet — no double-spend
- [ ] Test: resolve_at = placed_at + duration_seconds

**Step 2 — Implementation (make tests pass):**
- [ ] `Bet` entity: id (UUID), wallet_id, symbol, direction (UP/DOWN), stake (BigDecimal), odds (BigDecimal), status enum (PLACED/ACTIVE/WON/LOST/VOID/PUSH), price_at_placement, price_at_resolution (nullable), placed_at, resolve_at, resolved_at, idempotency_key (unique)
- [ ] Flyway migration: bets table with indexes on wallet_id, status, resolve_at, idempotency_key
- [ ] `POST /api/bets` — place bet (params: wallet_id, symbol, direction, stake, duration_seconds, idempotency_key)
- [ ] Fixed odds: 1.85 for both UP and DOWN (8% margin)
- [ ] Atomic: deduct stake + create bet in one transaction
- [ ] Idempotency: duplicate key returns original bet
- [ ] Status set to ACTIVE, resolve_at = placed_at + duration_seconds

---

### US-004: Odds Calculation with Vigorish
**Description:** As the house, I need odds that include a margin so the system is economically sustainable.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: default odds = 1.85 for 50/50 event (8% margin)
- [ ] Test: payout = stake * odds (BigDecimal math, HALF_UP rounding)
- [ ] Test: margin configurable — changing margin changes odds
- [ ] Test: per-symbol margin override works
- [ ] Test: zero or negative margin rejected
- [ ] Test: no floating point used anywhere in money calculations

**Step 2 — Implementation (make tests pass):**
- [ ] `OddsService` with configurable margin (default 8%)
- [ ] Fair odds 2.00 → with margin → 1.85 (formula: `2.0 / (1 + margin)`)
- [ ] Per-symbol margin override via application config
- [ ] All math uses BigDecimal with explicit HALF_UP rounding scale

---

### US-005: Exposure Tracking via Redis
**Description:** As the house, I need to limit total exposure per event direction so the system doesn't take on unlimited risk.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: placing bet increments exposure in Redis (key: `exposure:BTCUSDT:UP`)
- [ ] Test: exposure stored as cents (long), not float
- [ ] Test: bet rejected when exposure + new potential payout exceeds $100,000 limit
- [ ] Test: bet accepted when exactly at limit
- [ ] Test: concurrent bets — atomic Lua script prevents exceeding limit under race conditions
- [ ] Test: exposure decremented after bet settlement (WON, LOST, or PUSH)

**Step 2 — Implementation (make tests pass):**
- [ ] Redis key pattern: `exposure:{symbol}:{direction}`
- [ ] Lua script: atomic check-and-increment (no TOCTOU race)
- [ ] All amounts converted to cents before Redis storage
- [ ] Configurable max liability per symbol (default $100,000 = 10,000,000 cents)
- [ ] Integrate into bet placement flow
- [ ] Decrement on settlement

---

### US-006: Binance WebSocket Price Feed
**Description:** As the system, I need live crypto prices from Binance so that bets can be placed and settled against real market data.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: parse Binance trade message JSON into PriceSnapshot (symbol, price, timestamp)
- [ ] Test: price cache updates on new message
- [ ] Test: stale price (older timestamp) does not overwrite newer
- [ ] Test: reconnect triggered when no message received within timeout
- [ ] Test: exponential backoff on repeated disconnects (1s, 2s, 4s, 8s, max 30s)

**Step 2 — Implementation (make tests pass):**
- [ ] WebSocket client connects to `wss://stream.binance.com:9443/ws`
- [ ] Subscribe to trade streams for configurable symbols (default: BTCUSDT, ETHUSDT, SOLUSDT)
- [ ] `ConcurrentHashMap<String, PriceSnapshot>` cache
- [ ] Auto-reconnect with exponential backoff
- [ ] Health check: force reconnect if no message in 30s
- [ ] Connection status logging

---

### US-007: Settlement Scheduler
**Description:** As the system, I need to automatically resolve expired bets so that users receive payouts without manual intervention.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: WON — price moved in player's direction → wallet credited `stake * odds`
- [ ] Test: LOST — price moved against → wallet unchanged (stake already deducted)
- [ ] Test: PUSH — price unchanged → stake refunded to wallet
- [ ] Test: bet records price_at_resolution and resolved_at
- [ ] Test: only ACTIVE bets with resolve_at <= now are settled
- [ ] Test: settlement + wallet credit happen in one transaction (rollback if either fails)
- [ ] Test: Redis exposure decremented after settlement
- [ ] Test: batch limit — max 100 bets per cycle

**Step 2 — Implementation (make tests pass):**
- [ ] `@Scheduled` task, runs every 1 second
- [ ] Query: `SELECT * FROM bets WHERE status = 'ACTIVE' AND resolve_at <= now() LIMIT 100`
- [ ] Compare current price (from cache) vs price_at_placement
- [ ] WON → credit wallet, LOST → noop, PUSH → refund
- [ ] All in single transaction per bet
- [ ] Update bet: status, price_at_resolution, resolved_at
- [ ] Decrement Redis exposure

---

### US-008: WebSocket Price Push to Clients
**Description:** As a user, I want to see live crypto prices in real-time so that I can make informed betting decisions.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: client subscribes to symbols, receives price updates for those symbols only
- [ ] Test: client does NOT receive updates for unsubscribed symbols
- [ ] Test: price updates throttled to max 1 per symbol per second
- [ ] Test: client disconnect removes session from registry
- [ ] Test: invalid subscribe message returns error

**Step 2 — Implementation (make tests pass):**
- [ ] WebSocket endpoint at `/ws/prices`
- [ ] Client sends `{"type":"subscribe","symbols":["BTCUSDT","ETHUSDT"]}`
- [ ] Server pushes `{"type":"price","symbol":"BTCUSDT","price":"64230.50","timestamp":1234567890}`
- [ ] Throttle: max 1 update per symbol per second per client
- [ ] Graceful disconnect handling

---

### US-009: Bet Status and History API
**Description:** As a user, I want to see my bet history and current active bets so I can track my performance.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: list bets for wallet returns paginated results sorted by placed_at desc
- [ ] Test: filter by status returns only matching bets
- [ ] Test: get single bet includes all fields + potential_payout
- [ ] Test: empty wallet returns empty list (not 404)
- [ ] Test: pagination params work (page, size)

**Step 2 — Implementation (make tests pass):**
- [ ] `GET /api/wallets/{id}/bets` — paginated list, sorted by placed_at desc
- [ ] `GET /api/bets/{id}` — single bet with all fields + calculated potential_payout
- [ ] Optional `status` query param filter
- [ ] Pagination: page + size params, default page=0 size=20

---

### US-010: WebSocket Bet Updates
**Description:** As a user, I want to receive real-time notifications when my bet settles so I don't have to poll.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: client subscribes with wallet_id, receives bet status updates for that wallet
- [ ] Test: bet settlement triggers push with bet_id, status, payout
- [ ] Test: client does NOT receive updates for other wallets
- [ ] Test: push on all status changes: PLACED, WON, LOST, PUSH, VOID

**Step 2 — Implementation (make tests pass):**
- [ ] WebSocket endpoint at `/ws/bets`
- [ ] Client subscribes with wallet_id
- [ ] Server pushes `{"type":"bet_update","bet_id":"...","status":"WON","payout":"185.00"}`
- [ ] Settlement service triggers push after status change

---

### US-011: Global Error Handling and Validation
**Description:** As a developer, I need consistent error responses so the API is predictable and debuggable.

**Step 1 — Tests (write first, verify they fail):**
- [ ] Test: validation error returns 400 with `{"error":"VALIDATION_ERROR","message":"...","details":{...}}`
- [ ] Test: not found returns 404 with consistent format
- [ ] Test: insufficient balance returns 409
- [ ] Test: exposure limit exceeded returns 409
- [ ] Test: duplicate idempotency key returns 200 with original bet
- [ ] Test: unhandled exception returns 500 with generic message (no stack trace leak)

**Step 2 — Implementation (make tests pass):**
- [ ] `@ControllerAdvice` global exception handler
- [ ] Consistent format: `{"error":"CODE","message":"human readable","details":{}}`
- [ ] Bean Validation annotations on request DTOs (`@NotNull`, `@Positive`, etc.)
- [ ] Custom exceptions: InsufficientBalanceException, ExposureLimitExceededException, WalletNotFoundException, BetNotFoundException

---

### US-012: Production Configuration and CI
**Description:** As a developer, I need production-ready configuration so the app can be deployed beyond local development.

**Acceptance Criteria:**
- [ ] `application-prod.yml` with env var placeholders for DB_URL, REDIS_URL
- [ ] HikariCP connection pool settings reviewed and configured
- [ ] Dockerfile (multi-stage build, slim JRE image)
- [ ] `docker-compose.yml` for production-like setup (app + postgres + redis)
- [ ] Health check endpoint (`/actuator/health`) enabled
- [ ] Structured JSON logging for production profile
- [ ] GitHub Actions CI: build, test, Docker image build

**Note:** No TDD for this story — it's infrastructure/config. Verify by building Docker image and running compose.

## Functional Requirements

- FR-1: All monetary values stored as `BigDecimal` in Java and `DECIMAL(19,4)` in PostgreSQL — never `double` or `float`
- FR-2: Wallet balance mutations use pessimistic locking (`SELECT ... FOR UPDATE`)
- FR-3: Bet placement atomically deducts stake and creates bet in one transaction
- FR-4: Idempotency key prevents duplicate bets (unique constraint + return original on duplicate)
- FR-5: Fixed odds with 8% house margin (1.85 decimal odds for 50/50 events)
- FR-6: Exposure per symbol+direction tracked in Redis as cents (long), atomic Lua check-and-increment
- FR-7: Binance WebSocket price feed auto-reconnects with exponential backoff
- FR-8: Settlement scheduler runs every 1s, resolves expired bets, credits/refunds wallets transactionally
- FR-9: Client WebSocket pushes price updates throttled to 1/second per symbol
- FR-10: Client WebSocket pushes bet status changes in real-time
- FR-11: All API errors return consistent JSON format with appropriate HTTP status codes
- FR-12: Database schema managed via Flyway migrations — no `ddl-auto`
- FR-13: All endpoints accept and return JSON
- FR-14: Concurrent bet placements on same wallet are safe (no double-spend)
- FR-15: Bet durations: 30s, 60s, 120s, 300s
- FR-16: Stake limits: min $1, max $10,000

## Non-Goals (Out of Scope)

- No accumulator/parlay bets — singles only
- No cashout feature
- No live odds movement strategies — MVP uses fixed odds at placement
- No user authentication or registration — wallets accessed directly by ID
- No UI/frontend — API and WebSocket only (demo via Postman/wscat)
- No multi-instance deployment — single instance only
- No ShedLock — not needed for single instance
- No real money — all play money, no payment integration
- No sub-cent precision — 4 decimal places sufficient
- No admin panel or back-office tools

## Technical Considerations

- **Java 21** with Virtual Threads — `spring.threads.virtual.enabled=true`
- **Spring Boot 3.x** — latest stable
- **Gradle Kotlin DSL** — build tool
- **PostgreSQL** — local via brew (port 5432), production via env vars
- **Redis** — local via brew (port 6379), used for exposure tracking
- **Flyway** — database migrations, no Hibernate auto-DDL
- **JUnit 5 + H2 + embedded Redis** — fast tests, no container overhead
- **WebSocket** — Spring native, no STOMP (raw WebSocket for simplicity)
- **Binance WS API** — public, no auth, `wss://stream.binance.com:9443/ws/btcusdt@trade`
- **BigDecimal everywhere** — monetary amounts, odds, payouts
- **UUID primary keys** — no sequential IDs exposed externally
- **TDD** — tests first, verify failure, then implement

## Success Metrics

- All bet lifecycle states work correctly: PLACED -> ACTIVE -> WON/LOST/PUSH
- Live prices from Binance displayed via WebSocket within 1s of market data
- Bet settlement happens within 2s of resolve_at time
- No double-spend possible under concurrent load (verified by tests)
- Exposure limits enforced atomically (verified by tests)
- System handles 100+ concurrent WebSocket connections
- Full test suite passes with H2/embedded Redis (fast execution)
- Application starts in under 5 seconds
- Demo flow works end-to-end: create wallet -> deposit -> place bet -> watch price -> bet settles -> check balance

## Open Questions

None — all resolved.
