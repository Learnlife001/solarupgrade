-- Why a stock figure changed; see the h2 copy for the reasoning.
CREATE TABLE stock_movements (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    quantity_change INTEGER      NOT NULL,
    resulting_stock INTEGER      NOT NULL,
    reason          VARCHAR(32)  NOT NULL,
    order_id        BIGINT,
    actor           VARCHAR(255),
    happened_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, happened_at DESC);
