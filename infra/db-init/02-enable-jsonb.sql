-- ==============================================================================
-- 02-enable-jsonb.sql
-- Enables Postgres extensions and creates core tables for Catalog, Taxonomy, DAM, and Localization
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
    published_snapshot JSONB, -- Shadow Versioning: preserves live storefront snapshot during revisions
    revision_number INT NOT NULL DEFAULT 1,
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
    availability_status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE', -- Granular SKU Availability
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_variant_family_id ON catalog.product_variant(product_family_id);
CREATE INDEX IF NOT EXISTS idx_product_variant_attributes_gin ON catalog.product_variant USING gin (attributes);
CREATE INDEX IF NOT EXISTS idx_product_variant_dimensions_gin ON catalog.product_variant USING gin (dimensions);
CREATE INDEX IF NOT EXISTS idx_product_variant_availability ON catalog.product_variant(availability_status);

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

CREATE INDEX IF NOT EXISTS idx_catalog_outbox_event_status ON catalog.outbox_event(status, created_at);

CREATE TABLE IF NOT EXISTS catalog.consumed_messages (
    event_id UUID NOT NULL,
    consumer_group VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer_group)
);

-- ------------------------------------------------------------------------------
-- DIGITAL ASSET MANAGEMENT (DAM) TABLES
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS dam.digital_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_file_name VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    raw_s3_url TEXT NOT NULL,
    metadata JSONB,
    license JSONB NOT NULL,
    renditions JSONB NOT NULL DEFAULT '[]'::jsonb,
    linked_product_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dam_asset_active ON dam.digital_asset(is_active);
CREATE INDEX IF NOT EXISTS idx_dam_asset_license_gin ON dam.digital_asset USING gin (license);

CREATE TABLE IF NOT EXISTS dam.outbox_event (
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

CREATE INDEX IF NOT EXISTS idx_dam_outbox_event_status ON dam.outbox_event(status, created_at);

-- ------------------------------------------------------------------------------
-- LOCALIZATION & REGIONAL TABLES
-- ------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS localization.regional_catalog_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    global_product_id UUID NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    region_code VARCHAR(10) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_TRANSLATION',
    overrides JSONB NOT NULL DEFAULT '{}'::jsonb,
    translations JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_regional_item UNIQUE (global_product_id, region_code)
);

CREATE INDEX IF NOT EXISTS idx_regional_item_lookup ON localization.regional_catalog_item(product_code, region_code);
CREATE INDEX IF NOT EXISTS idx_regional_item_status ON localization.regional_catalog_item(status);

CREATE TABLE IF NOT EXISTS localization.outbox_event (
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

CREATE INDEX IF NOT EXISTS idx_localization_outbox_event_status ON localization.outbox_event(status, created_at);
