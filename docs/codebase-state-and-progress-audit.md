# Master Codebase State, Progress Audit & Architectural Roadmap

> **Platform:** Enterprise AI-Agent-Friendly Product Information Management (PIM) Platform  
> **Architecture:** Domain-Driven Design (DDD), Hexagonal Architecture (Ports & Adapters), Event Choreography, CQRS Edge  
> **Status Date:** November 2026  
> **Version:** 1.1.0-remediations-complete  
> **Branch:** `phase1_DDD_PIM`

---

## 1. Executive Summary & Enterprise Vision

The **DDD AI-Agent-Friendly PIM Platform** is an enterprise-grade digital core designed to solve the critical friction points of modern omnichannel retail:
1. **Unstructured Supplier Data Chaos:** Automating multi-vendor ingestion, quarantine, and AI-assisted catalog enrichment.
2. **Strict Domain Governance:** Enforcing deterministic product lifecycle states (`DRAFT` $\rightarrow$ `ENRICHED` $\rightarrow$ `APPROVED` $\rightarrow$ `PUBLISHED`), compliance gates, and shadow draft revisions without risking corrupt or premature storefront publications.
3. **High-Performance Omnichannel Distribution:** Separating write-heavy transactional domains from read-heavy distribution channels via a hardened CQRS Syndication Edge (Elasticsearch + Amazon SP-API / Shopify).
4. **AI-Agent Boundary Safety:** Isolating autonomous LLM enrichment agents from direct transactional database access, forcing all AI updates through formal REST/OpenAPI contracts and validation gates with Human-In-The-Loop (HITL) quality scorecards.

Following the thorough execution of all remediations outlined in [`docs/enterprise-cross-domain-audit.md`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/enterprise-cross-domain-audit.md) and [`docs/core-catalog-design-review.md`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/core-catalog-design-review.md), this document provides the **updated state-of-the-codebase audit**, technical inventory, gap analysis, and next steps.

---

## 2. Where We Are At: Codebase Architectural Inventory

The platform is structured as an API-first multi-module Maven monorepo paired with local Docker container specifications, contract specifications, and modular domain contexts.

### 2.1 Domain Contexts & Architecture Matrix

| Bounded Context | Package Root | DDD Role & Patterns | Current Implementation State |
| :--- | :--- | :--- | :--- |
| **`shared-kernel`** | `com.retail.pim.shared.*` | Building blocks (`AggregateRoot`, `Entity`, `DomainEvent`, base exceptions) | **100% Complete**. Zero framework dependencies; pure Java. |
| **`catalog-context`** | `com.retail.pim.catalog.*` | Core PIM Domain (Hexagonal). Aggregate Root `ProductFamily`, SKU `Variant`, Value Objects (`Dimensions`, `AttributeValue`), State Machine. | **100% Complete**. Pure domain, shadow versioning, category compliance policies, outbox poller, idempotent consumer guard, JPA entities, REST controllers. |
| **`taxonomy-context`** | `com.retail.pim.taxonomy.*` | Classification Domain. Category hierarchy backed by PostgreSQL Closure Table pattern. | **100% Complete**. Cycle-safe relocations, $O(1)$ subtree retrieval, path breadcrumbs, REST controller, JPA closure mapping. |
| **`dam-context`** | `com.retail.pim.dam.*` | Creative & Media Management. `DigitalAsset` aggregate, `Rendition` variations, EXIF `AssetMetadata`, `DRMLicense`. | **100% Complete**. Full JPA entities (`DigitalAssetJpaEntity`), transactional outbox relay, S3 lifecycle async cleanup, serverless transcoding worker, DRM 24h pre-warning event. |
| **`localization-context`** | `com.retail.pim.localization.*` | Global Ops & Regional Overrides. `RegionalCatalogItem` aggregate, `TranslationBlock`, `RegionalOverride`. | **100% Complete**. Full JPA entities (`RegionalCatalogJpaEntity`), transactional outbox relay, dynamic `RegionRuleRegistry`, `UomConverter`, non-destructive delta sync. |
| **`syndication-context`** | `com.retail.pim.syndication.*` | CQRS Edge Read Model & Channel Distribution. Aggregates Core, Regional, Legal, and DAM into `ChannelItemProjection`. | **95% Complete**. Read model projector with Claim-Check buffer (out-of-order race elimination), DRM zombie asset purge handler, dynamic `CategoryMappingTemplateRegistry`, and field projection masks (`SummaryProjection` vs `DetailProjection`). |
| **`supplier-context`** | Contracts & Choreography | Vendor Quarantine & AI Ingestion. Maps raw CSV/JSON feeds into `VendorItemApprovedForCore`. | **Contracts & Choreography Defined**. Event wiring to Core Catalog in place. |

---

### 2.2 Detailed Breakdown of Context Implementations

#### Context 1: Core Catalog (`catalog-context`)
- **Domain State Machine:** Strict transitions in [ProductFamily.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/ProductFamily.java):
  - `DRAFT` $\rightarrow$ `ENRICHED` via `enrich()`: Verifies localized marketing copy exists.
  - `ENRICHED` $\rightarrow$ `APPROVED` via `approve(policy)`: Enforces SKU variants exist, dimensions/weights are present, and evaluates pluggable [CategoryCompliancePolicy](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/compliance/CategoryCompliancePolicy.java) (e.g. [ElectronicsCompliancePolicy](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/compliance/ElectronicsCompliancePolicy.java), [ApparelCompliancePolicy](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/compliance/ApparelCompliancePolicy.java)).
  - `APPROVED` $\rightarrow$ `PUBLISHED` via `publish()`: Requires primary digital asset for every publishable variant; records immutable `publishedSnapshot`, increments `revisionNumber`, and registers [ProductPublished](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/events/ProductPublished.java).
- **Shadow Versioning / Concurrent Live Editing:** `startDraftRevision()` creates a working mutable draft while storefronts continue serving the immutable `publishedSnapshot`.
- **Partial SKU Publishing:** [Variant.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/Variant.java) marks incomplete SKUs as `SKU_INCOMPLETE` without blocking the publication of ready variants.
- **Supplier Re-Import Delta Merging:** [ProductLifecycleService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/ProductLifecycleService.java) merges new supplier payloads into existing families without wiping enriched marketing copy.
- **Transactional Outbox & Relay:** [OutboxPollingRelay.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/OutboxPollingRelay.java) polls `catalog.outbox_event` using `FOR UPDATE SKIP LOCKED`, preventing dual-write inconsistencies.
- **Kafka Idempotency Guard:** [IdempotentConsumerGuard.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/messaging/IdempotentConsumerGuard.java) guarantees at-least-once Kafka messages are processed exactly once using PostgreSQL atomic inserts on `catalog.consumed_messages`.

#### Context 2: Taxonomy Context (`taxonomy-context`)
- **Closure Table Architecture:** [ClosureTableRepositoryImpl.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/taxonomy-context/src/main/java/com/retail/pim/taxonomy/infrastructure/persistence/ClosureTableRepositoryImpl.java) manages hierarchical classification trees using the `category_closure` table.
- **Key Operations:**
  - $O(1)$ subtree fetching via `SELECT descendant_id FROM category_closure WHERE ancestor_id = :id`.
  - Ordered breadcrumb path generation via depth ordering.
  - Cycle-safe subtree relocation: disconnects ancestral paths and reconnects beneath target nodes without full table locks.

#### Context 4: Digital Asset Management (`dam-context`)
- **Aggregate & Value Objects:** [DigitalAsset.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/domain/model/DigitalAsset.java), [Rendition.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/domain/model/Rendition.java), [DRMLicense.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/domain/valueobjects/DRMLicense.java).
- **Core Catalog Demotion Wire:** When a license expires, [AssetLifecycleService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/application/services/AssetLifecycleService.java) publishes [AssetLicenseExpired.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/domain/events/AssetLicenseExpired.java). Core Catalog's [AssetLicenseExpiredListener.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/messaging/AssetLicenseExpiredListener.java) strips the asset and drops the product state back to `ENRICHED` if primary asset requirements are violated.
- **24-Hour Pre-Warning:** Emits [AssetLicenseExpiringSoon.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/domain/events/AssetLicenseExpiringSoon.java) to alert merchandising before automatic demotion occurs.
- **Persistence & Outbox:** Concrete [DigitalAssetJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/infrastructure/persistence/entity/DigitalAssetJpaEntity.java), [SpringDataDigitalAssetRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/infrastructure/persistence/SpringDataDigitalAssetRepository.java), [DamOutboxService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/infrastructure/persistence/DamOutboxService.java), and [DamOutboxPollingRelay.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/infrastructure/persistence/DamOutboxPollingRelay.java).
- **Async S3 & Transcoding:** Asynchronous binary purge via `purgeAssetBinariesAsync` in [S3AssetStorageRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/infrastructure/aws/S3AssetStorageRepository.java) and non-blocking worker offloading in [RenditionGenerationWorker.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/application/services/RenditionGenerationWorker.java).

#### Context 5: Localization & Regional Ops (`localization-context`)
- **Regional Model:** [RegionalCatalogItem.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/domain/model/RegionalCatalogItem.java) aggregates regional overrides and localized marketing blocks.
- **Dynamic Region Rules:** [RegionRuleRegistry.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/domain/service/RegionRuleRegistry.java) eliminates hardcoded region matrices, allowing runtime onboarding of new jurisdictions.
- **Automated UoM Domain Service:** [UomConverter.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/domain/service/UomConverter.java) handles deterministic unit transformations (e.g. lbs to kg, inches to cm).
- **Non-Destructive Delta Sync:** [RegionalCatalogService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/application/services/RegionalCatalogService.java) preserves in-flight regional translations when global catalog updates trigger re-fanout.
- **Persistence & Outbox:** Concrete [RegionalCatalogJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/persistence/entity/RegionalCatalogJpaEntity.java), [SpringDataRegionalCatalogRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/persistence/SpringDataRegionalCatalogRepository.java), [LocalizationOutboxService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/persistence/LocalizationOutboxService.java), and [LocalizationOutboxPollingRelay.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/persistence/LocalizationOutboxPollingRelay.java).

#### Context 6: Channel Syndication (`syndication-context`)
- **The Great Aggregation:** [ProductReadModelProjector.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/projections/ProductReadModelProjector.java) merges streams into read-optimized `ChannelItemProjection` records.
- **Race Condition Immunity (Claim-Check Buffer):** Solves out-of-order Kafka message arrival (e.g., `AssetLinked` arriving before `RegionalItemPublished`) via an unlinked asset buffer that auto-drains upon base projection creation.
- **DRM Copyright Legal Safety:** Listens directly for `AssetLicenseExpired`, immediately purging expired image URLs from cached projections and firing retraction updates to Amazon SP-API.
- **Dynamic Category Mapping Templates:** [CategoryMappingTemplateRegistry.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/domain/service/CategoryMappingTemplateRegistry.java) dynamically resolves Amazon and Mobile formatting constraints per merchandise category.
- **Field Projection Masks:** [ChannelItemProjection.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/domain/model/ChannelItemProjection.java) supports lightweight `SummaryProjection` (<1KB) for mobile list views and complete `DetailProjection` for PDPs, indexed in [ElasticsearchProjectionRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/infrastructure/database/ElasticsearchProjectionRepository.java).

---

### 2.3 Contracts & Database Schema Inventory

- **API Contracts:**
  - REST OpenAPI Specs: [catalog-api.yaml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/contracts/openapi/catalog-api.yaml) & [taxonomy-api.yaml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/contracts/openapi/taxonomy-api.yaml)
  - AsyncAPI Specs: [catalog-events.yaml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/contracts/asyncapi/catalog-events.yaml)
- **Database DDL:**
  - [01-create-schemas.sql](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/db-init/01-create-schemas.sql): Schemas `catalog`, `taxonomy`, `dam`, `localization`.
  - [02-enable-jsonb.sql](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/db-init/02-enable-jsonb.sql): Tables `category_node`, `category_closure`, `product_family`, `product_variant`, `catalog.outbox_event`, `catalog.consumed_messages`, `dam.digital_asset`, `dam.outbox_event`, `localization.regional_catalog_item`, `localization.outbox_event`, with GIN indices on JSONB attribute columns.
- **Infrastructure Containers:**
  - [docker-compose.yml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/docker-compose.yml): PostgreSQL 16, pgAdmin 4, Apache Kafka (KRaft mode on 9092), and MinIO (S3 mock on 9000/9001).

---

## 3. Progress vs. Goal: How Far We Are To The Goal

The enterprise project roadmap spans six distinct implementation phases:

```
+---------------------------------------------------------------------------------------------------+
|                                 ENTERPRISE PIM MATURITY PHASES                                    |
+---------------------+-------------------+---------------------+-------------------+---------------+
| Phase 1: Core DDD   | Phase 2: Kafka    | Phase 3: Python AI  | Phase 4: Hybrid   | Phase 5 & 6:  |
| & Multi-Context     | & Infrastructure  | Enrichment Agent    | Search & RAG      | Portals & UI  |
| [=================] | [=======----------] | [-----------------] | [-----------------] | [-----------] |
|        100%         |        65%        |         5%          |         5%        |      0%       |
+---------------------+-------------------+---------------------+-------------------+---------------+
```

### Phase Completion Scorecard

| Phase | Description | Goal Status | Current Completion |
| :--- | :--- | :---: | :---: |
| **Phase 1: Core Domain Models & Hexagonal Architecture** | Pure Java domain state machine, value objects, multi-context models (Taxonomy, DAM, Localization, Syndication), DDL schemas, contracts, JPA persistence, and outbox relays. | **COMPLETED** | **100%** |
| **Phase 2: Distributed Messaging & Infrastructure** | Kafka cluster, MinIO S3 container, Outbox poller relays, consumer guards, docker-compose topology. | **Substantially Advanced** | **65%** (Brokers containerized, pollers & DDL complete; needs Schema Registry & CI tests) |
| **Phase 3: Autonomous AI Agent Enrichment Service** | Python FastAPI service, LangChain/LlamaIndex agents, HITL confidence threshold ($\ge 0.85$), Pydantic JSONB schema sync. | **Next Focus Area** | **5%** (Architecture & design docs ready) |
| **Phase 4: Hybrid Search & RAG** | Elasticsearch cluster, dense vector embeddings (`pgvector` or OpenSearch), multi-attribute faceted search queries. | **Planned** | **5%** (Projection model & repo port defined) |
| **Phase 5: Multi-Channel Syndication Expansion** | Amazon SP-API, Shopify GraphQL, Walmart Marketplace connectors, dynamic Liquid/Jinja templating. | **Planned** | **25%** (Projector, Amazon SP-API stub, Claim-Check, dynamic templates ready) |
| **Phase 6: Merchandising & Governance Portal** | React/Next.js frontend for merchandising workflows, taxonomy trees, HITL review queue, DAM gallery. | **Future** | **0%** (Directory stub exists) |

**Overall Goal Progress: ~45% of total enterprise platform lifecycle, with 100% of Phase 1 fully realized.**

---

## 4. Deep Gap Analysis: Software Engineering & Infrastructure Gaps

With the completion of JPA persistence, transactional outbox relays, and Docker containerization for Kafka and MinIO, the remaining software engineering gaps are refined to:

### 4.1 Schema Registry & Kafka Serialization
* **Current State:** Messages are serialized to JSON strings using Jackson and published with Spring `KafkaTemplate<String, String>`.
* **Gap:** Enterprise Kafka requires Confluent Schema Registry (Avro or Protobuf) to enforce backward/forward schema compatibility and prevent silent event deserialization failures across versions.
* **Action Required:** Add Apicurio or Confluent Schema Registry container; bind AsyncAPI schemas to Avro generators.

### 4.2 Change Data Capture (CDC) vs. Polling Outbox
* **Current State:** Core Catalog, DAM, and Localization all use scheduled transactional outbox pollers with `SELECT ... FOR UPDATE SKIP LOCKED`.
* **Trade-off:** Highly reliable and easy to run locally; under extreme production load (10,000 vendor updates/sec), continuous DB polling generates CPU load.
* **Action Required:** Retain Poller Relay for local development; introduce Debezium PostgreSQL CDC connector streaming directly from WAL logs to Kafka for production deployment.

### 4.3 Distributed Tracing & Observability
* **Current State:** Standard SLF4J logging without correlated span contexts.
* **Risk:** In an event choreography spanning Supplier $\rightarrow$ Catalog $\rightarrow$ DAM $\rightarrow$ Localization $\rightarrow$ Syndication, debugging a dropped message or latency bottleneck is difficult without distributed tracing.
* **Action Required:** Integrate OpenTelemetry (OTel) and Micrometer Tracing; inject `traceparent` headers into all Kafka outbox events.

---

## 5. Deep Gap Analysis: Business & Domain Model Gaps

Beyond technical plumbing, real-world retail enterprise business requirements to address in upcoming phases include:

### 5.1 Matrix Variant Dimensions (Parent-Child Product Models)
* **Current State:** The domain model represents `ProductFamily` as a 1-to-N list of `Variant` SKUs with arbitrary attributes.
* **Business Gap:** Real retail requires **2-axis matrix variants** (e.g. T-Shirt with Size [S, M, L, XL] $\times$ Color [Red, Blue, Green]). Pricing, inventory, and barcoding (UPC/EAN) depend on variant axis definitions.
* **Domain Enhancement:** Add `VariantAxis` (e.g. `AXIS_COLOR`, `AXIS_SIZE`) value objects to `ProductFamily` to validate that every SKU uniquely occupies a valid coordinate in the variant matrix.

### 5.2 Product Bundles & Composite Assemblies
* **Current State:** Only standalone physical items are supported.
* **Business Gap:** Retailers frequently sell bundles (e.g., "DSLR Camera Kit" comprising Camera Body SKU + 50mm Lens SKU + SD Card SKU) with virtual pricing and linked inventory deduction.
* **Domain Enhancement:** Introduce a `CompositeProduct` aggregate root or `BundleComponent` value object linking child SKUs with quantity ratios and bundle pricing rules.

### 5.3 Human-In-The-Loop (HITL) Merchandiser Review Queue
* **Current State:** The $\ge 0.85$ confidence score thresholding rule is documented, but the persistent review queue for $< 0.85$ items is not yet materialized in code.
* **Business Gap:** Merchandisers need a dedicated REST API and inbox UI to view AI-generated descriptions with confidence highlights, accept/reject recommendations, and supply manual overrides before the state machine can call `enrich()`.
* **Domain Enhancement:** Add a `ReviewQueueItem` entity and review API endpoints in `catalog-context`.

---

## 6. Strategic Roadmap: What's Next To Do

### Sprint Roadmap

```
+---------------------------------------------------------------------------------------------------+
|                                     IMMEDIATE SPRINT ROADMAP                                      |
+-----------------------------------+-----------------------------------+---------------------------+
| Sprint 1: Phase 3 AI Agent Core   | Sprint 2: Search & Elasticsearch  | Sprint 3: Channel Feeds   |
| (Python FastAPI & Pydantic)       | (CQRS Read Optimization)          | (Syndication Expansion)   |
+-----------------------------------+-----------------------------------+---------------------------+
| 1. Python FastAPI microservice    | 1. Elasticsearch cluster setup    | 1. Shopify GraphQL client |
| 2. Pydantic schema validation     | 2. Faceted attribute queries      | 2. Walmart Marketplace    |
| 3. LangChain extraction agent     | 3. Hybrid vector search (pgvector)| 3. Liquid template engine |
| 4. HITL review queue REST API     | 4. Sub-5ms PDP read endpoints     | 4. End-to-end load tests  |
+-----------------------------------+-----------------------------------+---------------------------+
```

### Action Items for Immediate Execution (Sprint 1):
1. **Initialize `ai-python/` Microservice Scaffold:**
   - Create FastAPI application structure with UV / Poetry dependency management.
   - Author Pydantic models with schema parity against Java `AttributeValue` and `Dimensions`.
2. **Implement Autonomous AI Enrichment Agent:**
   - Consume `catalog.product.events` (`ProductCreated`).
   - Extract unstructured vendor specs into structured attributes.
   - Implement the $\ge 0.85$ confidence threshold:
     - $\ge 0.85 \rightarrow$ Call Java REST `POST /api/v1/products/{id}/enrich`.
     - $< 0.85 \rightarrow$ Send to Staged HITL Review Queue.
3. **Build HITL Merchandising Review Endpoints:**
   - Create `/api/v1/review-queue` endpoints in `catalog-context` for human curation.

---

## 7. Cross-Referenced Documentation Links

- [README.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/README.md) - Project root documentation and quick start.
- [enterprise-cross-domain-audit.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/enterprise-cross-domain-audit.md) - Complete 6-context enterprise architecture audit & gap analysis.
- [core-catalog-design-review.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/core-catalog-design-review.md) - Deep dive review of the Core Catalog state machine and remediations.
- [cross-context-sync-and-review.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/cross-context-sync-and-review.md) - Synchronized cross-context audit and implementation notes.
- [cross-boundary-workflow.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/cross-boundary-workflow.md) - Async event choreography and Python AI integration boundary.
