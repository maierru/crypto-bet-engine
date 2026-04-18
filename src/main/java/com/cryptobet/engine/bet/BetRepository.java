package com.cryptobet.engine.bet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface BetRepository extends JpaRepository<Bet, UUID> {

    @Query("SELECT b FROM Bet b WHERE b.status = :status AND b.resolveAt <= :now ORDER BY b.createdAt ASC")
    List<Bet> findSettleableBets(BetStatus status, Instant now, Pageable pageable);

    Page<Bet> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Page<Bet> findByWalletIdAndStatusOrderByCreatedAtDesc(UUID walletId, BetStatus status, Pageable pageable);
}
