package com.cryptobet.engine.wallet;

import java.util.UUID;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(UUID id) {
        super("Wallet not found: " + id);
    }
}
