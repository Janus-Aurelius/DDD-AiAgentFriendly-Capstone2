-- ==============================================================================
-- 01-create-schemas.sql
-- Initializes bounded-context schemas for Product Information Management (PIM)
-- ==============================================================================

-- Create separate schemas reflecting Domain-Driven Design Context Boundaries
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS taxonomy;

-- Grant usage and table rights to pim_user
GRANT ALL ON SCHEMA catalog TO pim_user;
GRANT ALL ON SCHEMA taxonomy TO pim_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA catalog GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA catalog GRANT ALL ON SEQUENCES TO pim_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA taxonomy GRANT ALL ON TABLES TO pim_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA taxonomy GRANT ALL ON SEQUENCES TO pim_user;
