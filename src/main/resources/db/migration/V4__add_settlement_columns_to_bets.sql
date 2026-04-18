ALTER TABLE bets ADD COLUMN resolve_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bets ADD COLUMN price_at_resolution DECIMAL(19, 4);
ALTER TABLE bets ADD COLUMN resolved_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_bets_settlement ON bets (status, resolve_at) WHERE status = 'OPEN';
