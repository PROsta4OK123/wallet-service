ALTER TABLE wallet
    ADD COLUMN opening_balance NUMERIC(19, 2) NOT NULL DEFAULT 0;

UPDATE wallet SET opening_balance = balance;

ALTER TABLE wallet
    ADD CONSTRAINT ck_wallet_non_negative_opening_balance CHECK (opening_balance >= 0);
