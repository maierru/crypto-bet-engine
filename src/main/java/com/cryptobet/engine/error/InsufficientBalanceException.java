package com.cryptobet.engine.error;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(UUID walletId, BigDecimal requested, BigDecimal available) {
        super("Insufficient balance in wallet " + walletId + ": requested " + requested + ", available " + available);
    }
}
