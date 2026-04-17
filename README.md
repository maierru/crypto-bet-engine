# Crypto Bet Engine — Project Design & Comparison
**Date:** 2026-04-17
**Purpose:** Learn Java 21 / Spring Boot through a domain-relevant project, understand Java advantages for real-time betting, compare with Ruby/Rails approach
**Repo name:** `crypto-bet-engine`
**Build tool:** Gradle (Kotlin DSL)

---

## What It Does

Users watch live crypto prices (real data from Binance). Place a bet: "BTC goes UP in 60 seconds." After 60s, bet auto-settles: won → payout to wallet, lost → amount already deducted.

Not a toy CRUD — a stateful, concurrent, real-time system.

---

## How Betting Actually Works (Mechanics)

### Bet Lifecycle (State Machine)

```
PLACED → ACTIVE → WON / LOST / VOID / PUSH
                    ↓
               SETTLED (payout processed)

Cashout branch:
ACTIVE → CASHED_OUT (partial payout, bet closed early)
```

| State | Meaning | What happens |
|---|---|---|
| PLACED | Server accepted the bet | Stake deducted from wallet, odds locked |
| ACTIVE | Waiting for event result | Nothing — bet lives until event resolves |
| WON | Event resolved in player's favor | Payout = stake × odds credited to wallet |
| LOST | Event resolved against player | Nothing — stake already deducted |
| VOID | Event cancelled | Stake refunded to wallet |
| PUSH | Price unchanged (tie with house) | Stake refunded |
| CASHED_OUT | Player took partial payout early | Partial payout, bet closed |

### Odds — How Payout Is Calculated

**Always store in decimal format.** Convert on input/output.

| Format | Example | Payout on $100 stake | Formula |
|---|---|---|---|
| Decimal | 2.50 | $250 ($150 profit) | `stake × odds` |
| Fractional | 3/2 | $250 ($150 profit) | `stake × (1 + num/den)` |
| American +150 | +150 | $250 ($150 profit) | `stake × (1 + american/100)` |
| American -200 | -200 | $150 ($50 profit) | `stake × (1 + 100/abs(american))` |

### Vigorish (Margin) — How the House Makes Money

The house does NOT predict outcomes. It embeds a margin into the odds.

**Example: coin flip**
- Fair odds: 2.00 / 2.00 (50% / 50%) — implied total = 100%
- With margin: 1.91 / 1.91 — implied = 52.4% + 52.4% = **104.8%**
- **Vig = 4.8%** — house takes ~4.8% of every dollar in turnover

**For our project (crypto price prediction):**
- Fair: BTC UP 2.00 / BTC DOWN 2.00
- With margin: UP 1.85 / DOWN 1.85
- Player bets $100 on UP, wins → receives $185 (not $200)
- House on average takes $15 per $200 wagered

**In accumulators, margin compounds:**
- 1 leg: 4.8% vig
- 3 legs: (1.048)³ - 1 = **15.1%** vig
- 5 legs: (1.048)⁵ - 1 = **26.3%** vig
- This is why casinos love accumulators

### Bet Types

**For our MVP — Single bet only:**
```
User places: "BTC UP in 60s, stake $50, odds 1.85"
Result: BTC was $64,230 at placement, $64,300 at resolve
→ BTC went UP → WON → payout = $50 × 1.85 = $92.50
```

**For domain understanding (not in MVP):**

**Accumulator / Parlay:** multiple events, all must win
```
Leg 1: BTC UP (1.85)
Leg 2: ETH UP (1.90)
Leg 3: SOL DOWN (2.10)
Combined odds: 1.85 × 1.90 × 2.10 = 7.38
Stake: $10, potential payout: $73.80
Result: Leg 3 lost → ENTIRE bet lost. Payout = $0.
```

**Each-way:** two bets in one (win + place). Not for crypto — horse racing thing.

**Live / In-play:** bet WHILE event is ongoing. Odds change every second. Main challenge:

### Live Betting — Odds Movement Problem

```
T=0.000s: User sees BTC UP odds = 1.85, clicks "Place Bet"
T=0.200s: Request travels to server
T=0.350s: BTC price changed, odds now 1.75
T=0.400s: Server receives request with odds=1.85, but actual = 1.75

What to do?
```

**Three strategies (user chooses):**

| Strategy | Behavior | UX |
|---|---|---|
| Exact odds | odds changed → reject, user re-submits | Safe for user, annoying |
| Accept better odds | odds improved (1.85 → 1.95) → accept; worsened (1.85 → 1.75) → reject | Best UX |
| Accept any movement | always accept current odds | Fast but risky for user |

### Settlement — Resolve Logic

Every 1s, scheduler finds bets past their `resolve_at` time:
- Compare current price vs price at placement
- Price went in player's direction → WON, payout = stake × locked odds
- Price went against → LOST, no payout (stake already deducted)
- Price unchanged → PUSH, stake refunded

Important: settlement must be wrapped in a transaction that also credits the wallet. Both operations must succeed or fail together.

### Risk Management (Exposure Tracking)

The house cannot accept unlimited bets on one side — if everyone bets "BTC UP" and BTC rises, the house goes bankrupt.

```
Liability per event per direction:
  BTC UP:   sum(all active bets on UP × odds) = potential payout
  BTC DOWN: sum(all active bets on DOWN × odds) = potential payout

  Max liability per event = $100,000 (configurable)

  New bet: if current_liability + (new_stake × new_odds) > max_liability → REJECT
```

**In our MVP:** Redis counter per event+direction.

**! Important: Money in Redis — `double` Precision Problem**

Redis stores numbers as IEEE 754 `double`. This causes rounding errors with decimal currency values:

```
0.1 + 0.2 = 0.30000000000000004   (in double)
```

For an exposure counter accumulating thousands of bets, rounding errors compound and can cause:
- Incorrect rejection of valid bets (false positives)
- Accepting bets that exceed the actual limit (false negatives)

**Options:**

| Approach | How it works | Tradeoff |
|---|---|---|
| **Store as cents (long)** | Multiply by 100 before storing, divide on read. `INCRBY exposure:BTC:UP 18500` instead of `INCRBYFLOAT ... 185.00` | Exact for 2-decimal currencies. Requires consistent conversion everywhere. Breaks if you need sub-cent precision |
| **Lua script with string math** | Store as string, do arithmetic in Lua with custom decimal lib | Exact, but complex. Lua has no native BigDecimal — you'd write your own or use a lib |
| **Accept `double` imprecision** | Use `INCRBYFLOAT`, accept ±0.01 error on exposure limits | Simplest. For exposure tracking (not wallet balance), ±1 cent error on a $100K limit is acceptable |
| **DB-based exposure** | Skip Redis, query `SUM(stake * odds)` from PostgreSQL with `DECIMAL` type | Exact. Slower under high load — every bet placement hits DB for exposure check + wallet lock |
| **Hybrid: Redis approx + DB verify** | Redis for fast approximate check, DB for final exact check inside transaction | Best of both. Extra DB query on bets near the limit only |

**Recommendation for MVP:** store as cents (`long`). Simple, exact, no rounding issues. Reserve `INCRBYFLOAT` for non-financial counters.

**! Race condition note:** a naive read-then-increment pattern has a TOCTOU race between the check and the update. Use a Lua script to make check+increment atomic:

```
-- Atomic check-and-increment in Redis
local key = KEYS[1]
local amount = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local current = tonumber(redis.call('GET', key) or '0')
if current + amount > limit then
  return 0  -- rejected
end
redis.call('INCRBY', key, amount)
return 1  -- accepted
```

### Cashout (Nice to Have, Not MVP)

Player can close a bet BEFORE the result. House offers a current price:

```
Bet placed: BTC UP, odds 1.85, stake $100
Currently: BTC is going up, 30s left
Cashout offer: $150 (less than full $185 win, but guaranteed)

User accepts → bet closed, $150 credited, no further risk
```

Cashout price = function of current probability + time to resolve + house margin.

---

## Single Instance Architecture

```
Binance WS ──→ [Price Cache (in-memory)] ──→ WS push to clients
                       │
                       ▼
              [Bet Placement] ←── client (WS or REST)
                       │
                       ▼
              [PostgreSQL: wallets, bets]
                       │
                       ▼
              [Settlement Scheduler] ── every 1s, resolves expired bets
```

Stateful components (live in JVM memory):
- Price cache (ConcurrentHashMap)
- WebSocket sessions (who's connected)
- Settlement scheduler (in-process @Scheduled)

Stateless components (DB is source of truth):
- Wallet balance
- Bet records
- Settlement results

---

## Multi-Instance Architecture (Fault Tolerance)

```
                    ┌─────────────┐
                    │ Load Balancer│
                    │ (nginx/ALB) │
                    └──────┬──────┘
               ┌───────────┼───────────┐
               ▼           ▼           ▼
         ┌──────────┐┌──────────┐┌──────────┐
         │ Instance1 ││ Instance2 ││ Instance3 │
         │           ││           ││           │
         │ Binance WS││ Binance WS││ Binance WS│ ← each connects independently
         │ Price Cache││ Price Cache││ Price Cache│ ← same prices, separate cache
         │ WS Sessions││ WS Sessions││ WS Sessions│ ← each holds its own clients
         │ Settlement ││ Settlement ││ Settlement │ ← ONLY ONE runs (ShedLock)
         └─────┬──────┘└─────┬──────┘└─────┬──────┘
               │             │             │
               └─────────────┼─────────────┘
                             ▼
                    ┌─────────────────┐
                    │   PostgreSQL    │
                    │   (shared)      │
                    └─────────────────┘
                             │
                    ┌─────────────────┐
                    │     Redis       │
                    │  (ShedLock +    │
                    │   shared state) │
                    └─────────────────┘
```

### How Each Component Scales

**Price Cache:**
- Each instance connects to Binance WS independently
- Same prices everywhere — no coordination needed
- Instance dies → others unaffected, prices still flowing

**WebSocket (client-facing):**
- Load balancer distributes WS connections across instances
- Each instance holds its own set of clients in memory
- Instance dies → clients reconnect to another instance (client-side retry)
- No shared WS state needed — each client gets same price data from any instance

**Bet Placement:**
- Any instance can accept a bet — stateless operation
- DB pessimistic lock on wallet row handles concurrency
- Two instances placing bets for same wallet simultaneously → DB serializes them
- Already fault-tolerant: nothing in-memory to lose

**Settlement Scheduler — THE HARD PROBLEM:**
- @Scheduled runs on every instance by default → same bet settled 3 times = triple payout!
- Solution: **ShedLock** (Spring library) — acquires DB/Redis lock before scheduled task runs
- Only one instance executes settlement at a time
- Instance holding the lock dies → lock expires (TTL) → another instance picks up next cycle
- Bets are in DB → nothing lost, next scheduler run resolves them

### What Happens When Instance Dies

| Component | Impact | Recovery |
|---|---|---|
| Price cache | Zero — others have their own | Automatic |
| WS clients on that instance | Disconnected | Client reconnects to another instance |
| Pending bets | Zero — stored in DB | Next settlement cycle picks them up |
| Settlement lock held | Lock expires (TTL 30s) | Another instance takes over |
| Bet being placed mid-crash | Transaction rollback | Client retries with same idempotency key |

**Key insight:** DB is the source of truth. In-memory state (prices, sessions) is reconstructible. Nothing is lost on crash.

### WebSocket Reconnect Strategy (Instance Restart / Deploy)

**TCP connections cannot be migrated between instances.** Instance dies → connections die. Standard approach: server signals, client reconnects.

**Two signals, different scenarios:**

| Scenario | Server action | Client behavior |
|---|---|---|
| Graceful deploy | Send `{"type":"reconnect"}` message THEN close with code 1001 | Instant reconnect, no backoff (<100ms gap) |
| Crash | Nothing — connection drops | `onclose` fires → exponential backoff reconnect (1-2-4-8s) |

**Graceful shutdown flow:**
1. Server sends `{"type":"reconnect","reason":"deploy"}` to all connected clients
2. Server closes connections with code 1001 (Going Away)
3. Clients reconnect instantly (no backoff) to another instance
4. Clients re-send `subscribe` message after reconnect

**Kubernetes rolling deploy sequence:**
```
State: [Instance A: 2000 WS] [Instance B: 2000 WS]

1. K8s starts Instance C (new version)
2. C passes readiness probe → LB adds C
3. K8s sends SIGTERM to Instance A
4. A sends {"type":"reconnect"} to all 2000 clients
5. A sends close frame 1001
6. 2000 clients reconnect instantly → land on B or C
7. A exits after graceful timeout
8. Repeat for B

Result: zero downtime, <100ms gap per client
```

**Rails comparison:** ActionCable stores subscription state in Redis, so after reconnect the server knows what client was subscribed to. Spring WebSocket keeps subscriptions in-memory — client must re-send `subscribe` message explicitly.

---

## Java vs Ruby — Same System, Different Implementation

### Ruby/Rails Version

```
                    ┌─────────────┐
                    │ Load Balancer│
                    └──────┬──────┘
               ┌───────────┼───────────┐
               ▼           ▼           ▼
         ┌──────────┐┌──────────┐┌──────────┐
         │  Puma    ││  Puma    ││  Puma    │
         │ 5 workers││ 5 workers││ 5 workers│  ← 15 OS processes
         │ (GIL per ││ (GIL per ││ (GIL per │
         │  worker) ││  worker) ││  worker) │
         └─────┬────┘└─────┬────┘└─────┬────┘
               │           │           │
         ┌─────┴────┐      │     ┌─────┴────┐
         │ActionCable│      │     │ActionCable│  ← WS through Rails
         │ (limited) │      │     │ (limited) │
         └──────────┘      │     └──────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
    ┌─────────┐    ┌─────────────┐    ┌───────────┐
    │PostgreSQL│    │    Redis    │    │  Sidekiq  │
    │         │    │(ActionCable │    │ (separate │
    │         │    │ pub/sub +   │    │  process) │
    │         │    │ cache)      │    │           │
    └─────────┘    └─────────────┘    └───────────┘
```

### Side-by-Side Comparison

| Aspect | Java (Spring Boot) | Ruby (Rails) |
|---|---|---|
| **Process model** | 1 JVM, Virtual Threads | Puma: N workers × M threads, each worker = OS process with own GIL |
| **Memory for 5000 WS** | ~400MB (1 JVM) | ~3-5GB (multiple Puma workers + ActionCable + Redis pub/sub) |
| **WebSocket** | Native Spring WebSocket, sessions in-memory | ActionCable → Redis pub/sub between workers. Every WS message goes through Redis |
| **Price feed consumer** | In-process thread, writes to ConcurrentHashMap | Separate process or thread, writes to Redis (shared across workers) |
| **Settlement** | @Scheduled in-process + ShedLock | Sidekiq job (separate process), needs sidekiq-cron or whenever gem |
| **Concurrency on wallet** | DB lock (same) | DB lock (same) — this part identical |
| **Deployment** | 1-2 containers per instance | 1 Puma + 1 Sidekiq + ActionCable = 3 process types minimum |
| **Startup time** | ~3-5s (Spring Boot) | ~5-10s (Rails) |
| **CPU-bound work** | Real parallelism (no GIL) | GIL: one thread computes at a time per worker |
| **Dependencies for real-time** | Spring WebSocket (built-in) | ActionCable + Redis adapter + sidekiq + sidekiq-cron = 4 moving parts |

### The Core Difference — Visualized

**Ruby: 1000 concurrent bets**
```
Puma Worker 1 (GIL): [bet1] [bet2] [bet3] ... sequential, one at a time
Puma Worker 2 (GIL): [bet4] [bet5] [bet6] ... sequential
Puma Worker 3 (GIL): [bet7] [bet8] [bet9] ... sequential
...need 5 workers × 3 instances = 15 OS processes
Memory: 15 × 300MB = 4.5GB
```

**Java: 1000 concurrent bets**
```
JVM (Virtual Threads): [bet1] [bet2] [bet3] ... [bet1000] ALL concurrent
  └─ each Virtual Thread: ~1KB stack
  └─ blocked on DB? thread yields, another runs (no OS thread wasted)
Memory: 1 JVM × 500MB
```

**Ruby isn't wrong.** It works. Sweatcoin serves 12M DAU on Rails. But for the same load, you need more infrastructure, more processes, more memory, more moving parts.

### Where Ruby Actually Wins

| Aspect | Ruby advantage |
|---|---|
| Development speed | Rails generators, conventions, less boilerplate |
| Prototyping | Ship an MVP in days, not weeks |
| Metaprogramming | DSLs, dynamic methods — impossible in Java |
| Ecosystem for web | Devise, Pundit, ActiveAdmin — batteries included |
| Testing | RSpec + FactoryBot = fastest test writing in any language |

**Honest take:** if building a content site or a SaaS dashboard, Rails would be the better choice. Java wins for real-time betting with tens of thousands of concurrent users on persistent connections.

---

## Implementation Plan (Priority Order)

### Phase 1: Foundation
- [ ] Spring Boot project (start.spring.io: Web, JPA, PostgreSQL, WebSocket) + Gradle
- [ ] Wallet + Bet entities with JPA
- [ ] REST: create wallet, place bet (with pessimistic lock + idempotency key)
- [ ] Basic tests (JUnit 5)

### Phase 2: Real-Time
- [ ] Binance WS client (connect, parse price, cache in ConcurrentHashMap)
- [ ] Settlement scheduler (@Scheduled, resolves pending bets)
- [ ] WebSocket server → push prices to connected clients

### Phase 3: Multi-Instance (nice to have)
- [ ] ShedLock for settlement
- [ ] Docker Compose: 2 app instances + postgres + redis + nginx LB
- [ ] Crash recovery verification

---

## Open Questions



3. Binance WS API — no auth needed for public price stream. Confirm no geo-blocking in Portugal.
