package com.cryptobet.engine.bet;

import java.util.UUID;

public class BetNotFoundException extends RuntimeException {
    public BetNotFoundException(UUID id) {
        super("Bet not found: " + id);
    }
}
