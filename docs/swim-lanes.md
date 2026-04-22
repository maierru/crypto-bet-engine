# Swim Lane Diagrams — Crypto Bet Engine

## Component Overview

```mermaid
graph TB
    subgraph Clients
        HTTP[HTTP REST Client]
        WSC[WebSocket Client]
    end

    subgraph Controllers
        BetCtrl[BetController<br>/api/bets]
        WalletCtrl[WalletController<br>/api/wallets]
        PriceCtrl[PriceController<br>/api/prices]
        OddsCtrl[OddsController<br>/api/odds]
        ExposureCtrl[ExposureController<br>/api/exposure]
    end

    subgraph Services
        BetSvc[BetService]
        WalletSvc[WalletService]
        PriceSvc[PriceService]
        OddsSvc[OddsService]
        ExposureSvc[ExposureService]
        SettlementSvc[SettlementService]
    end

    subgraph Background
        Scheduler[SettlementScheduler<br>every 1s, batch 100]
        BinanceFeed[BinancePriceFeed<br>miniTicker stream]
    end

    subgraph Events
        PriceEvent[PriceUpdateEvent]
        BetEvent[BetUpdateEvent]
    end

    subgraph WebSocket Handlers
        PriceWS[PriceWebSocketHandler<br>/topic/prices]
        BetWS[BetWebSocketHandler<br>/topic/bets/walletId]
    end

    subgraph Infrastructure
        PG[(PostgreSQL<br>wallets, bets)]
        Redis[(Redis<br>exposure:symbol)]
        Binance[Binance WS API<br>wss://stream.binance.com]
        Memory[ConcurrentHashMap<br>in-memory prices]
    end

    HTTP --> BetCtrl & WalletCtrl & PriceCtrl & OddsCtrl & ExposureCtrl
    BetCtrl --> BetSvc
    WalletCtrl --> WalletSvc
    PriceCtrl --> PriceSvc
    OddsCtrl --> OddsSvc
    ExposureCtrl --> ExposureSvc

    BetSvc --> PriceSvc & OddsSvc & ExposureSvc
    BetSvc --> PG
    BetSvc --> BetEvent
    WalletSvc --> PG
    PriceSvc --> Memory
    PriceSvc --> PriceEvent
    ExposureSvc --> Redis
    OddsSvc -.- |pure calc| OddsSvc

    Scheduler --> SettlementSvc
    SettlementSvc --> PG & PriceSvc & ExposureSvc
    SettlementSvc --> BetEvent

    BinanceFeed --> PriceSvc
    Binance --> BinanceFeed

    PriceEvent --> PriceWS
    BetEvent --> BetWS
    PriceWS --> WSC
    BetWS --> WSC
```

## Flow 1: Place Bet

```mermaid
sequenceDiagram
    participant C as Client
    participant BC as BetController
    participant BS as BetService
    participant PS as PriceService
    participant OS as OddsService
    participant WR as WalletRepo (PG)
    participant BR as BetRepo (PG)
    participant ES as ExposureService (Redis)
    participant EB as EventBus
    participant WS as WS Client

    C->>BC: POST /api/bets {walletId, symbol, direction, stake, durationSeconds}
    BC->>BS: placeBet(request)

    BS->>PS: getPrice(symbol)
    PS-->>BS: BigDecimal entryPrice

    BS->>OS: calculateOdds(symbol, direction)
    OS-->>BS: BigDecimal odds (e.g. 1.9524)

    Note over BS: potentialPayout = stake × odds

    BS->>WR: findByIdForUpdate(walletId)
    Note over WR: PESSIMISTIC_WRITE lock
    WR-->>BS: Wallet

    alt balance < stake
        BS-->>BC: throw InsufficientBalanceException
        BC-->>C: 409 Conflict
    end

    BS->>WR: wallet.deductBalance(stake) → save
    BS->>BR: save(new Bet)
    BS->>ES: addExposure(symbol, potentialPayout)
    Note over ES: Redis SET exposure:{symbol}

    BS->>EB: publish(BetUpdateEvent)
    EB->>WS: /topic/bets/{walletId}

    BS-->>BC: Bet
    BC-->>C: 201 Created + Bet JSON
```

## Flow 2: Price Update (Binance → Clients)

```mermaid
sequenceDiagram
    participant B as Binance WS API
    participant BF as BinancePriceFeed
    participant PS as PriceService
    participant MEM as ConcurrentHashMap
    participant EB as EventBus
    participant PH as PriceWebSocketHandler
    participant WS as WS Clients

    B->>BF: miniTicker stream {s: "BTCUSDT", c: "45230.12"}
    BF->>PS: updatePrice("BTCUSDT", 45230.12)
    PS->>MEM: put("BTCUSDT", 45230.12)
    PS->>EB: publish(PriceUpdateEvent)
    EB->>PH: handlePriceUpdate(event)

    par broadcast
        PH->>WS: /topic/prices {symbol, price, timestamp}
    and symbol-specific
        PH->>WS: /topic/prices/BTCUSDT {symbol, price, timestamp}
    end

    Note over BF: Auto-reconnect on disconnect (5s backoff)
```

## Flow 3: Settlement (Background Job)

```mermaid
sequenceDiagram
    participant SCH as SettlementScheduler
    participant SS as SettlementService
    participant BR as BetRepo (PG)
    participant PS as PriceService
    participant WR as WalletRepo (PG)
    participant ES as ExposureService (Redis)
    participant EB as EventBus
    participant WS as WS Client

    Note over SCH: @Scheduled(fixedDelay=1000)

    SCH->>SS: settle()
    SS->>BR: findSettleableBets(limit=100)
    Note over BR: WHERE status='OPEN'<br/>AND resolve_at <= now()
    BR-->>SS: List<Bet>

    loop for each bet
        SS->>PS: getPrice(bet.symbol)
        PS-->>SS: currentPrice

        Note over SS: Compare currentPrice vs entryPrice<br/>+ bet.direction → WON / LOST / PUSH

        alt WON
            SS->>WR: findByIdForUpdate(walletId)
            Note over WR: PESSIMISTIC_WRITE lock
            SS->>WR: wallet.addBalance(potentialPayout) → save
        end

        alt PUSH (price unchanged)
            SS->>WR: findByIdForUpdate(walletId)
            SS->>WR: wallet.addBalance(stake) → save
        end

        SS->>ES: removeExposure(symbol, potentialPayout)
        SS->>BR: save(bet: status, priceAtResolution, resolvedAt)

        SS->>EB: publish(BetUpdateEvent)
        EB->>WS: /topic/bets/{walletId} {type, betId, status, payout}
    end
```

## Flow 4: Wallet Operations

```mermaid
sequenceDiagram
    participant C as Client
    participant WC as WalletController
    participant WS as WalletService
    participant WR as WalletRepo (PG)
    participant BS as BetService
    participant BR as BetRepo (PG)

    Note over C,WR: Create Wallet
    C->>WC: POST /api/wallets {nickname}
    WC->>WS: createWallet(request)
    WS->>WR: save(new Wallet)
    WR-->>WS: Wallet
    WS-->>WC: Wallet
    WC-->>C: 201 Created

    Note over C,WR: Deposit
    C->>WC: POST /api/wallets/{id}/deposit {amount}
    WC->>WS: deposit(id, amount)
    WS->>WR: findByIdForUpdate(id)
    Note over WR: PESSIMISTIC_WRITE lock
    WS->>WR: wallet.addBalance(amount) → save
    WR-->>WS: Wallet
    WS-->>WC: Wallet
    WC-->>C: 200 OK

    Note over C,BR: Get Wallet Bets
    C->>WC: GET /api/wallets/{id}/bets?status=OPEN&page=0
    WC->>BS: getBetsForWallet(id, status, pageable)
    BS->>BR: findByWalletId...(id, pageable)
    BR-->>BS: Page<Bet>
    BS-->>WC: Page<Bet>
    WC-->>C: 200 OK
```

## Infrastructure Summary

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | PostgreSQL 16 | Wallets, Bets (persistent state) |
| Cache/State | Redis 7 | Exposure tracking per symbol |
| Prices | In-memory ConcurrentHashMap | Live price feed (ephemeral) |
| Events | Spring ApplicationEventPublisher | In-JVM event bus |
| Real-time | WebSocket + STOMP | Push prices & bet updates to clients |
| External | Binance WebSocket API | Live crypto price feed |
| Scheduling | Spring @Scheduled | Settlement every 1s |
| Concurrency | Pessimistic DB locks + Virtual Threads (Java 21) | Safe wallet mutations |
