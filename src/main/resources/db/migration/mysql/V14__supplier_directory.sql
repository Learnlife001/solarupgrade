-- German supplier directory.
--
-- Company-level data only. A company name, its general enquiries address and
-- its city are business information; a named salesperson's direct line is
-- personal data under the GDPR. There is deliberately no column for one, so the
-- mistake cannot be made later by filling in a field that happens to exist.
--
-- verified_at and how_verified are the point of the table. Anyone can copy a
-- list of company names; what makes this worth reading is that each row says
-- when somebody last checked and how. Both are nullable and neither is
-- defaulted -- an unchecked entry must look unchecked.

CREATE TABLE suppliers (
    id              BIGINT       AUTO_INCREMENT,
    name            VARCHAR(160) NOT NULL,
    city            VARCHAR(120) NOT NULL,
    region          VARCHAR(120),
    website         VARCHAR(255),
    contact_email   VARCHAR(255),
    trade           VARCHAR(32)  NOT NULL,
    export_stance   VARCHAR(32)  NOT NULL,
    minimum_order   VARCHAR(120),
    incoterms       VARCHAR(60),
    languages       VARCHAR(120),
    lead_time_weeks INTEGER,
    notes           VARCHAR(1000),
    verified_at     DATETIME(6),
    how_verified    VARCHAR(500),
    PRIMARY KEY (id)
);

-- What each supplier stocks, in the same vocabulary as the shop's catalogue.
CREATE TABLE supplier_categories (
    supplier_id BIGINT      NOT NULL,
    category    VARCHAR(32) NOT NULL,
    PRIMARY KEY (supplier_id, category),
    CONSTRAINT fk_supplier_categories_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers (id) ON DELETE CASCADE
);

-- The directory is browsed by who exports and what they stock.
CREATE INDEX idx_suppliers_export_stance ON suppliers (export_stance);
CREATE INDEX idx_suppliers_name ON suppliers (name);
