ALTER TABLE orders
    ADD COLUMN request_hash CHAR(64);

UPDATE orders
SET request_hash = repeat('0', 64)
WHERE request_hash IS NULL;

ALTER TABLE orders
    ALTER COLUMN request_hash SET NOT NULL;
