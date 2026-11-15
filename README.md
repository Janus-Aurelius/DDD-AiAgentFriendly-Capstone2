# DDD AI-Agent Friendly PIM Platform (Capstone 2)

An enterprise-grade Product Information Management (PIM) platform built with **Domain-Driven Design (DDD)**, **Hexagonal Architecture (Ports and Adapters)**, API-first contracts, and an autonomous AI agent enrichment workflow.

---

## 📁 Repository Structure

```
├── contracts/                            # API-FIRST DESIGN BOUNDARY
│   ├── openapi/
│   │   ├── catalog-api.yaml              # REST spec for Product CRUD & Enrichment
│   │   └── taxonomy-api.yaml             # REST spec for Category Trees
│   └── asyncapi/
│       └── catalog-events.yaml           # Specs for ProductCreated, ProductEnriched (Prep for Phase 2)
│
├── backend-java/                         # MAVEN MULTI-MODULE (Phase 1 Core)
│   ├── pom.xml                           # Parent POM (Spring Boot 3.3.0, Java 17)
│   │
│   ├── shared-kernel/                    # COMMON DDD BUILDING BLOCKS
│   │   ├── pom.xml
│   │   └── src/main/java/com/pim/shared/
│   │       ├── domain/                   # Base classes: AggregateRoot, DomainEvent, Entity
│   │       └── exceptions/               # Base DomainException, ResourceNotFoundException
│   │
│   ├── catalog-context/                  # CORE PIM DOMAIN (Hexagonal Architecture)
│   │   ├── pom.xml
│   │   └── src/main/java/com/pim/catalog/
│   │       ├── domain/                   # PURE JAVA (No Spring, No DB annotations)
│   │       │   ├── model/ProductFamily.java        # The Aggregate Root
│   │       │   ├── model/ProductVariant.java       # Internal Entity (SKU)
│   │       │   ├── valueobjects/AttributeValue.java# Immutable attribute data
│   │       │   ├── valueobjects/Dimensions.java    # Standardized weight/size
│   │       │   └── repository/ProductRepository.java # Interface (The "Port")
│   │       │
│   │       ├── application/              # USE CASES (Orchestrates Domain & DB)
│   │       │   ├── commands/CreateProductCommand.java
│   │       │   ├── queries/GetProductDetailsQuery.java
│   │       │   └── services/ProductEnrichmentService.java
│   │       │
│   │       ├── infrastructure/           # ADAPTERS (Spring Data, Postgres, JSONB)
│   │       │   ├── persistence/PostgresProductRepositoryImpl.java # Implements the domain port
│   │       │   ├── persistence/entity/ProductJpaEntity.java       # JPA class mapping to tables
│   │       │   └── config/JsonbConverter.java                     # Maps ValueObjects to PG JSONB
│   │       │
│   │       └── presentation/             # WEB TIER
│   │           ├── rest/ProductController.java    # Implements openapi/catalog-api.yaml
│   │           └── mappers/ProductDtoMapper.java  # Converts Domain <-> DTO
│   │
│   ├── taxonomy-context/                 # CLASSIFICATION DOMAIN
│   │   ├── pom.xml
│   │   └── src/main/java/com/retail/pim/taxonomy/
│   │       ├── domain/model/CategoryNode.java
│   │       ├── infrastructure/persistence/ClosureTableRepositoryImpl.java
│   │       └── presentation/rest/CategoryController.java
│   │
│   ├── dam-context/                      # CONTEXT 4: DIGITAL ASSET MANAGEMENT
│   │   ├── pom.xml
│   │   └── src/main/java/com/retail/pim/dam/
│   │       ├── domain/model/DigitalAsset.java (Aggregate Root) & Rendition.java
│   │       ├── domain/events/AssetLicenseExpired.java (Wired to Core Catalog)
│   │       ├── application/services/RenditionGenerationWorker.java
│   │       └── infrastructure/aws/S3AssetStorageRepository.java
│   │
│   ├── localization-context/             # CONTEXT 5: LOCALIZATION & REGIONAL OPS
│   │   ├── pom.xml
│   │   └── src/main/java/com/retail/pim/localization/
│   │       ├── domain/model/RegionalCatalogItem.java (Aggregate Root) & RegionalOverride.java
│   │       ├── application/services/RegionalCatalogService.java
│   │       └── infrastructure/messaging/GlobalProductPublishedListener.java (Fans out US, CA, MX)
│   │
│   └── syndication-context/              # CONTEXT 6: CHANNEL SYNDICATION (CQRS EDGE)
│       ├── pom.xml
│       └── src/main/java/com/retail/pim/syndication/
│           ├── domain/model/ChannelCatalog.java (Aggregate Root) & ChannelItemProjection.java
│           ├── application/projections/ProductReadModelProjector.java (The Great Aggregation)
│           ├── infrastructure/database/ElasticsearchProjectionRepository.java (<10ms queries)
│           └── infrastructure/external/AmazonMarketplaceApiClient.java (Amazon SP-API)
│
├── ai-python/                            # (Empty in Phase 1 - Reserved for Phase 3)
│   └── README.md
│
├── frontend/                             # (Optional stub for Phase 6)
│   └── README.md
│
├── infra/                                # LOCAL DEVELOPMENT ENVIRONMENT
│   ├── docker-compose.yml                # Spins up PostgreSQL 16+ and pgAdmin 4
│   └── db-init/
│       ├── 01-create-schemas.sql         # Creates 'catalog' and 'taxonomy' DB schemas
│       └── 02-enable-jsonb.sql           # Postgres JSONB, Outbox & Closure Table DDL
│
└── docs/
    ├── enterprise-cross-domain-audit.md  # Comprehensive 6-context architecture & gap audit
    ├── core-catalog-design-review.md     # Deep dive review of Core Catalog state machine
    ├── cross-context-sync-and-review.md  # Master sync & review notes across all 6 contexts
    └── 3. The Cross-Boundary Workflow_cross-boundary-workflow.md # Java meets Python AI blueprint
```

---

## 🚀 Quick Start: Local Infrastructure

Start PostgreSQL 16 and pgAdmin:

```bash
cd infra
docker compose up -d
```

- **PostgreSQL**: `localhost:5432` (Database: `pim_db`, User: `pim_user`, Password: `pim_password`)
- **pgAdmin 4**: `http://localhost:5050` (Email: `admin@pim.local`, Password: `admin_password`)

The database automatically initializes the `catalog` and `taxonomy` schemas and required PostgreSQL extensions (`uuid-ossp`, `pgcrypto`, `btree_gin`).

---

## 🧩 Architectural Highlights

- **Pure Java Domain Model**: Core domain entities and value objects have **zero framework dependencies** (no Spring, no JPA annotations in `domain/`).
- **Hexagonal Architecture (Ports & Adapters)**:
  - Inbound: REST controllers translating HTTP requests to application commands/queries.
  - Outbound: Domain repository ports implemented by persistence adapters (`PostgresProductRepositoryImpl`, `ClosureTableRepositoryImpl`).
- **PostgreSQL JSONB + GIN Indexing**: Dynamic attributes are stored in JSONB columns with GIN indices for high search flexibility without schema migrations.
- **Closure Table Pattern**: The taxonomy category hierarchy uses a closure table (`category_closure`) allowing $O(1)$ subtree retrieval, efficient breadcrumb generation, and cycle-safe relocations.
- **Cross-Boundary AI Integration**: Read [`docs/cross-boundary-workflow.md`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/cross-boundary-workflow.md) for the end-to-end design of Java event publishing and Python AI agent enrichment.
