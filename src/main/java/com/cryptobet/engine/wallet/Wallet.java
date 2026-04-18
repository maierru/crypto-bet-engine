package com.cryptobet.engine.wallet;

import com.cryptobet.engine.error.InsufficientBalanceException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {}

    public Wallet(BigDecimal initialBalance) {
        this.balance = initialBalance.setScale(4, java.math.RoundingMode.HALF_UP);
        this.currency = "USD";
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
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void deductStake(BigDecimal stake) {
        if (this.balance.compareTo(stake) < 0) {
            throw new InsufficientBalanceException(this.id, stake, this.balance);
        }
        this.balance = this.balance.subtract(stake);
    }
}
