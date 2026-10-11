ALTER TABLE products ADD COLUMN demo_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN demo_mode BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE products DROP CONSTRAINT products_sku_key;
ALTER TABLE products ADD CONSTRAINT products_demo_mode_sku_unique UNIQUE (demo_mode, sku);

ALTER TABLE orders DROP CONSTRAINT orders_idempotency_key_key;
ALTER TABLE orders ADD CONSTRAINT orders_demo_mode_idempotency_key_unique
    UNIQUE (demo_mode, idempotency_key);

CREATE TABLE demo_activity (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    last_activity_at TIMESTAMPTZ NOT NULL
);

INSERT INTO demo_activity (id, last_activity_at) VALUES (1, CURRENT_TIMESTAMP);
