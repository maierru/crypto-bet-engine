CREATE TABLE bets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id   UUID NOT NULL REFERENCES wallets(id),
    symbol      VARCHAR(20) NOT NULL,
    direction   VARCHAR(10) NOT NULL,
    stake       DECIMAL(19, 4) NOT NULL,
    entry_price DECIMAL(19, 4) NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_bets_wallet_id ON bets(wallet_id);
CREATE INDEX idx_bets_status ON bets(status);
