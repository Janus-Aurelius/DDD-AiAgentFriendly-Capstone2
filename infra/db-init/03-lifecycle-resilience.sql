-- ==============================================================================
-- 03-lifecycle-resilience.sql
-- Foundation Infrastructure: Context Outboxes, FSM States, Bundle Cascades & Temporal Audit
-- ==============================================================================

-- Schema Initializations for Remaining Bounded Contexts
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS compliance;
CREATE SCHEMA IF NOT EXISTS taxonomy;
CREATE SCHEMA IF NOT EXISTS dam;
CREATE SCHEMA IF NOT EXISTS syndication;
CREATE SCHEMA IF NOT EXISTS localization;

GRANT ALL ON SCHEMA catalog TO pim_user;
GRANT ALL ON SCHEMA compliance TO pim_user;
GRANT ALL ON SCHEMA taxonomy TO pim_user;
GRANT ALL ON SCHEMA dam TO pim_user;
GRANT ALL ON SCHEMA syndication TO pim_user;
GRANT ALL ON SCHEMA localization TO pim_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA catalog GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalog GRANT ALL ON SEQUENCES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA compliance GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA compliance GRANT ALL ON SEQUENCES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA taxonomy GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA taxonomy GRANT ALL ON SEQUENCES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA dam GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA dam GRANT ALL ON SEQUENCES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA syndication GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA syndication GRANT ALL ON SEQUENCES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA localization GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA localization GRANT ALL ON SEQUENCES TO pim_user;

-- Core Aggregate Alterations
ALTER TABLE catalog.product_family
    ADD COLUMN IF NOT EXISTS active_suspension_reasons JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(50) NOT NULL DEFAULT 'FAMILY_DRAFT';

ALTER TABLE catalog.product_variant
    ADD COLUMN IF NOT EXISTS lifecycle_state VARCHAR(50) NOT NULL DEFAULT 'SKU_DRAFT',
    ADD COLUMN IF NOT EXISTS active_suspension_reasons JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS channel_status_map JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS market_compliance_map JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS suspended_components JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS embargo_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS street_date TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS is_bestseller BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS display_priority INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS published_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS gtin VARCHAR(14),
    ADD COLUMN IF NOT EXISTS fulfillment_mode VARCHAR(50);

-- Context Transactional Outbox Alteration (Catalog)
ALTER TABLE catalog.outbox_event
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS destination VARCHAR(50) DEFAULT 'EXTERNAL_KAFKA',
    ADD COLUMN IF NOT EXISTS partition_key VARCHAR(100);
UPDATE catalog.outbox_event SET completed_at = processed_at WHERE completed_at IS NULL AND processed_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_catalog_outbox_unprocessed ON catalog.outbox_event (created_at) WHERE completed_at IS NULL;

-- Shadow Working Revisions
CREATE TABLE IF NOT EXISTS catalog.variant_revision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL REFERENCES catalog.product_variant(id) ON DELETE CASCADE,
    base_version BIGINT NOT NULL,
    revision_state VARCHAR(50) NOT NULL,
    remediated_attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Virtual Bundle Components Join Table
CREATE TABLE IF NOT EXISTS catalog.bundle_component (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bundle_variant_id UUID NOT NULL REFERENCES catalog.product_variant(id) ON DELETE CASCADE,
    component_sku VARCHAR(100) NOT NULL,
    required_qty INT NOT NULL CHECK (required_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bundle_component UNIQUE(bundle_variant_id, component_sku)
);
CREATE INDEX IF NOT EXISTS idx_bundle_component_sku ON catalog.bundle_component(component_sku);

-- Bi-Temporal Financial & Logistics Attribute History
CREATE TABLE IF NOT EXISTS catalog.financial_attribute_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(100) NOT NULL,
    uom_tier VARCHAR(50) NOT NULL,
    length_cm NUMERIC(10, 2) NOT NULL,
    width_cm NUMERIC(10, 2) NOT NULL,
    height_cm NUMERIC(10, 2) NOT NULL,
    gross_weight_kg NUMERIC(10, 3) NOT NULL,
    tax_band VARCHAR(50) NOT NULL,
    customs_hsn_code VARCHAR(50) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ NOT NULL DEFAULT 'infinity',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_financial_temporal ON catalog.financial_attribute_history (sku, uom_tier, effective_from, effective_to);

-- Context Transactional Outbox Tables
CREATE TABLE IF NOT EXISTS compliance.outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    destination VARCHAR(50) NOT NULL,
    partition_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_compliance_outbox_unprocessed ON compliance.outbox_event (created_at) WHERE completed_at IS NULL;

CREATE TABLE IF NOT EXISTS syndication.outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    destination VARCHAR(50) NOT NULL,
    partition_key VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_syndication_outbox_unprocessed ON syndication.outbox_event (created_at) WHERE completed_at IS NULL;
