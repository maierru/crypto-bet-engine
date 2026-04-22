# Crypto Bet Engine

Real-time crypto betting engine built with Java 21 / Spring Boot. Users bet on crypto price direction (UP/DOWN), bets auto-settle in 60 seconds against live Binance prices.

Built autonomously using the [Ralph loop](scripts/ralph/) — an AI agent that implements user stories one at a time from a PRD.

## How It Works

```
Binance WS ──> [Price Cache] ──> WebSocket push to browser
                    |
                    v
              [Bet Placement] <── Browser (REST API)
                    |
                    v
              [PostgreSQL: wallets, bets]
                    |
                    v
              [Settlement Scheduler] ── every 1s, resolves expired bets
```

1. Live crypto prices stream from Binance into an in-memory cache
2. Prices push to connected browsers via WebSocket (STOMP)
3. User places a bet: "BTC goes UP in 60s" with a stake
4. Stake deducted from wallet, odds locked (1.90 with 5% vig)
5. After 60s, scheduler compares current price vs entry price
6. WON: payout = stake x odds credited to wallet. LOST: stake already gone

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.4 |
| Database | PostgreSQL 16 + Flyway migrations |
| Cache/Exposure | Redis 7 |
| Price Feed | Binance WebSocket API |
| Client Push | Spring WebSocket (STOMP) |
| Frontend | Vanilla HTML/CSS/JS (dark theme, no framework) |
| E2E Tests | Ferrum (Ruby + Chrome CDP) |
| Build | Gradle (Kotlin DSL) |
| Container | Docker + docker-compose |
| CI | GitHub Actions |

## Project Structure

```
src/main/java/com/cryptobet/engine/
  bet/           Bet entity, placement API, status enum
  error/         Global exception handler, custom exceptions
  exposure/      Redis-based exposure tracking per symbol
  odds/          Odds calculation with configurable vigorish
  price/         Binance WebSocket client, price cache
  settlement/    Scheduler + settlement service (WON/LOST/PUSH)
  wallet/        Wallet entity, deposit, balance with pessimistic locking
  websocket/     STOMP config, price + bet update handlers

src/main/resources/
  static/        Frontend (index.html, css/style.css, js/app.js)
  db/migration/  Flyway SQL migrations (V1-V5)
  application.yml / application-prod.yml / application-e2e.yml

test/e2e/        Ruby E2E browser tests (Ferrum + Minitest)
tasks/           PRD documents
scripts/ralph/   Ralph autonomous agent (PRD-driven development)
docs/            Design docs and architecture comparisons
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/wallets` | Create wallet (nickname, initial balance) |
| GET | `/api/wallets/{id}` | Get wallet details |
| POST | `/api/wallets/{id}/deposit` | Deposit funds |
| GET | `/api/wallets/{id}/bets` | Bet history (paginated, filterable by status) |
| POST | `/api/bets` | Place a bet (symbol, direction, stake) |
| GET | `/api/bets/{id}` | Get bet details |
| GET | `/api/prices` | All cached prices |
| GET | `/api/prices/{symbol}` | Price for symbol |
| GET | `/api/odds` | Current odds (symbol + direction) |
| GET | `/api/exposure/{symbol}` | Current exposure |

**WebSocket (STOMP over `/ws`):**
- `/topic/prices` — live price updates
- `/topic/bets/{walletId}` — bet settlement notifications

## Run Locally

**Prerequisites:** Docker Desktop running

```bash
./run.sh
```

Opens http://localhost:8080 in browser. Creates wallet, watch live prices, place bets.

**Without Docker** (requires local PostgreSQL + Redis):

```bash
./gradlew bootRun
```

## Run Tests

**Java unit/integration tests:**

```bash
./gradlew test
```

**E2E browser tests** (requires Docker + Chrome):

```bash
test/e2e/run.sh
```

Watch tests visually:

```bash
HEADLESS=false test/e2e/run.sh
```

## Ralph Loop

This project was built using Ralph — an autonomous coding agent that takes a PRD (Product Requirements Document) and implements it story by story.

```
scripts/ralph/
  ralph.sh       Runner script — executes one story per iteration
  prd.json       Current PRD with user stories and acceptance criteria
  progress.txt   Log of completed work and codebase patterns
  CLAUDE.md      Agent instructions
  archive/       Previous ralph runs
```

**How it works:**
1. Write a PRD with user stories and acceptance criteria
2. Run `./scripts/ralph/ralph.sh`
3. Ralph picks the highest-priority unfinished story
4. Implements it, runs tests, commits if green
5. Repeat until all stories pass

**Completed runs:**
- `ralph/crypto-bet-engine` — Backend (12 stories: scaffolding through production config)
- `ralph/crypto-bet-frontend` — Frontend (15 stories: app shell through error handling)
- `ralph/e2e-testing` — E2E tests (9 stories: scaffolding through CI integration)

## Design Docs

- [docs/DESIGN.md](docs/DESIGN.md) — Betting mechanics, architecture, Java vs Ruby comparison
- [docs/swim-lanes.md](docs/swim-lanes.md) — Swim lane diagrams: component interactions, data flow per operation
