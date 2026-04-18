package com.cryptobet.engine.bet;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bets")
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BetDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal stake;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal odds = BigDecimal.ZERO;

    @Column(name = "potential_payout", nullable = false, precision = 19, scale = 4)
    private BigDecimal potentialPayout = BigDecimal.ZERO;

    @Column(name = "resolve_at")
    private Instant resolveAt;

    @Column(name = "price_at_resolution", precision = 19, scale = 4)
    private BigDecimal priceAtResolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BetStatus status = BetStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bet() {}

    public Bet(UUID walletId, String symbol, BetDirection direction, BigDecimal stake, BigDecimal entryPrice) {
        this.walletId = walletId;
        this.symbol = symbol;
        this.direction = direction;
        this.stake = stake.setScale(4, RoundingMode.HALF_UP);
        this.entryPrice = entryPrice.setScale(4, RoundingMode.HALF_UP);
        this.status = BetStatus.OPEN;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public String getSymbol() { return symbol; }
    public BetDirection getDirection() { return direction; }
    public BigDecimal getStake() { return stake; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getOdds() { return odds; }
    public BigDecimal getPotentialPayout() { return potentialPayout; }
    public Instant getResolveAt() { return resolveAt; }
    public BigDecimal getPriceAtResolution() { return priceAtResolution; }
    public Instant getResolvedAt() { return resolvedAt; }
    public BetStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setOdds(BigDecimal odds) {
        this.odds = odds.setScale(4, RoundingMode.HALF_UP);
    }

    public void setPotentialPayout(BigDecimal potentialPayout) {
        this.potentialPayout = potentialPayout.setScale(4, RoundingMode.HALF_UP);
    }

    public void setStatus(BetStatus status) {
        this.status = status;
    }

    public void setResolveAt(Instant resolveAt) {
        this.resolveAt = resolveAt;
    }

    public void setPriceAtResolution(BigDecimal priceAtResolution) {
        this.priceAtResolution = priceAtResolution.setScale(4, RoundingMode.HALF_UP);
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
