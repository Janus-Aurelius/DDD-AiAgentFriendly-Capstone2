-- ==============================================================================
-- 02-enable-jsonb.sql
-- Enables Postgres extensions and creates core tables for Catalog, Taxonomy, and Outbox
-- ==============================================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- ------------------------------------------------------------------------------
-- TAXONOMY BOUNDED CONTEXT TABLES
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS taxonomy.category_node (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_id UUID REFERENCES taxonomy.category_node(id) ON DELETE SET NULL,
    level INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS taxonomy.category_closure (
    ancestor_id UUID NOT NULL REFERENCES taxonomy.category_node(id) ON DELETE CASCADE,
    descendant_id UUID NOT NULL REFERENCES taxonomy.category_node(id) ON DELETE CASCADE,
    depth INT NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id)
);

CREATE INDEX IF NOT EXISTS idx_category_closure_descendant ON taxonomy.category_closure(descendant_id);
CREATE INDEX IF NOT EXISTS idx_category_closure_depth ON taxonomy.category_closure(depth);

-- ------------------------------------------------------------------------------
-- CATALOG BOUNDED CONTEXT TABLES
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS catalog.product_family (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    localized_descriptions JSONB NOT NULL DEFAULT '{}'::jsonb,
    category_id UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_family_status ON catalog.product_family(status);
CREATE INDEX IF NOT EXISTS idx_product_family_attributes_gin ON catalog.product_family USING gin (attributes);

CREATE TABLE IF NOT EXISTS catalog.product_variant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_family_id UUID NOT NULL REFERENCES catalog.product_family(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    dimensions JSONB,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    digital_assets JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_variant_family_id ON catalog.product_variant(product_family_id);
CREATE INDEX IF NOT EXISTS idx_product_variant_attributes_gin ON catalog.product_variant USING gin (attributes);
CREATE INDEX IF NOT EXISTS idx_product_variant_dimensions_gin ON catalog.product_variant USING gin (dimensions);

-- ------------------------------------------------------------------------------
-- TRANSACTIONAL OUTBOX PATTERN TABLE
-- Ensures reliable domain event publishing across system boundaries
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS catalog.outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_status_created ON catalog.outbox_event(status, created_at);
