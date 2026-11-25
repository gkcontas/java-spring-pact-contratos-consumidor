CREATE TABLE products (
    sku          VARCHAR(32)    PRIMARY KEY,
    name         VARCHAR(120)   NOT NULL,
    description  VARCHAR(500),
    price        NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    weight_grams INTEGER        NOT NULL CHECK (weight_grams > 0),
    stock        INTEGER        NOT NULL CHECK (stock >= 0),
    version      BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE stock_reservations (
    id         UUID         PRIMARY KEY,
    sku        VARCHAR(32)  NOT NULL REFERENCES products (sku),
    quantity   INTEGER      NOT NULL CHECK (quantity > 0),
    order_ref  VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
