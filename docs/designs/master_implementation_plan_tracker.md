# PIM Lifecycle State Machine & Event-Driven Resilience: Master Implementation Plan & Tracker

* **Document Owner:** Thien An Nguyen
* **Status:** All Slices Completed (100% Implemented & Verified)
* **Design Specification Reference:** [pim-lifecycle-state-machine-and-resilience.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/designs/pim-lifecycle-state-machine-and-resilience.md)
* **Architecture:** Modular Monolith with In-Process Hexagonal Contexts & External Event-Driven Integration (Kafka KRaft)
* **Last Updated:** 2026-11-24

---

## 1. Executive Summary & Tracking Dashboard

This document serves as the **single source of truth** for tracking the engineering implementation of the PIM Lifecycle State Machine and Event-Driven Resilience Architecture. The project is decomposed into **1 Foundation Enabler Slice (Slice 0)** followed by **8 Vertical Business Slices (Slices 1–8)** corresponding 1-to-1 to the Critical User Journeys (CUJs).

| Slice | Name / Objective | Status | Target Phase | Verification Gate |
|---|---|---|---|---|
| **Slice 0** | **Foundation Infrastructure, Context Outboxes & ArchUnit Boundaries** | **COMPLETED** | Sprint 1 | ArchUnit tests pass (14/14), Outbox schemas & Redis repo verified |
| **Slice 1** | **Working Revisions & Two-Tier California Masking (CUJ 1)** | **COMPLETED** | Sprint 1 | VariantRevisionTest & California Hero Fallback unit tests |
| **Slice 2** | **Class 1 Safety Recall & Kafka QoS Event Dispatch (CUJ 2)** | **COMPLETED** | Sprint 2 | RecallQosDispatchTest & 3-Zone PONR integration tests |
| **Slice 3** | **Bundle Component Masking & Sub-2ms Protection (CUJ 3)** | **COMPLETED** | Sprint 2 | BundleRedisMaskingTest & BatchCoordinatorChunkingTest |
| **Slice 4** | **Omnichannel Feed Resilience & Anti-Thrashing (CUJ 4)** | **COMPLETED** | Sprint 3 | ChannelFeedbackAntiThrashingTest |
| **Slice 5** | **Skeleton Upsert, Embargoed Launch & Virtual Stock Math (CUJ 5)** | **COMPLETED** | Sprint 3 | Storefront Readiness Gate & EmbargoLiftedEvent tests |
| **Slice 6** | **"Last Variant Standing" & Concurrent Hold Protection (CUJ 6)** | **COMPLETED** | Sprint 4 | ProductFamilyStateMachineTest & SuspensionSetConcurrencyTest |
| **Slice 7** | **Financial Temporal Audit & UOM Logistics Reconciliation (CUJ 7)** | **COMPLETED** | Sprint 4 | FinancialBiTemporalAuditTest & UOM prerequisite validators |
| **Slice 8** | **End-of-Life Tombstoning & Monotonic CQRS Read-Model Upsert (CUJ 8)** | **COMPLETED** | Sprint 5 | CqrsMonotonicUpsertTest, ProductTombstoningTest & CatalogQuarantineFacadeTest |

---

## 2. Slice 0: Foundation Infrastructure, Context Outboxes & ArchUnit Boundaries

### Objective
Establish database migration scripts, multi-schema Transactional Outbox entities, ArchUnit boundary enforcement across hexagonal contexts, the Redis Set blacklist repository, and central boot application wiring (`pim-bootstrap`).

### Task Breakdown & Progress
- [x] **Task 0.1: Database Migration DDL (`infra/db-init/03-lifecycle-resilience.sql`)**
  - [x] Initialize `compliance` and `syndication` schemas with appropriate user grants and default privileges.
  - [x] Add `catalog.product_family.active_suspension_reasons` (`JSONB`).
  - [x] Add lifecycle state and metadata columns to `catalog.product_variant` (`lifecycle_state`, `active_suspension_reasons`, `channel_status_map`, `market_compliance_map`, `suspended_components`, `embargo_until`, `street_date`, `is_bestseller`, `display_priority`, `published_version`, `gtin`, `fulfillment_mode`).
  - [x] Extend `catalog.outbox_event` (`completed_at`, `destination`, `partition_key`, backfill existing).
  - [x] Create `catalog.variant_revision`, `catalog.bundle_component`, and `catalog.financial_attribute_history` tables.
  - [x] Create `compliance.outbox_event` and `syndication.outbox_event` tables with indices.
- [x] **Task 0.2: Outbox JPA Entities**
  - [x] Create `CatalogOutboxEventJpaEntity` in `catalog-context` mapping `catalog.outbox_event`.
  - [x] Create `ComplianceOutboxEventJpaEntity` in `compliance-context` mapping `compliance.outbox_event`.
  - [x] Create `SyndicationOutboxEventJpaEntity` in `syndication-context` mapping `syndication.outbox_event`.
- [x] **Task 0.3: Dependency Updates**
  - [x] Add `spring-boot-starter-data-jpa` to `compliance-context/pom.xml`.
  - [x] Add `spring-boot-starter-data-jpa` and `spring-boot-starter-data-redis` to `syndication-context/pom.xml`.
- [x] **Task 0.4: `pim-bootstrap` Module Wiring**
  - [x] Register `pim-bootstrap` in `backend-java/pom.xml`.
  - [x] Create `backend-java/pim-bootstrap/pom.xml` including contexts, starters, ArchUnit, and Testcontainers.
  - [x] Create `PimApplication.java` entry point with `@SpringBootApplication` and `@EnableScheduling`.
- [x] **Task 0.5: Outbox Reconciliation Sweeper**
  - [x] Implement `OutboxReconciliationSweeper.java` in `pim-bootstrap` running every 60s to re-dispatch uncompleted outbox events.
- [x] **Task 0.6: Redis Blacklist Repository & Cold-Start Initializer**
  - [x] Define interface `RedisBlacklistRepository` in `syndication-context`.
  - [x] Implement `RedisBlacklistRepositoryImpl` leveraging `RedisOperations<String, String>`.
  - [x] Create `RedisBlacklistInitializer` cold-start listener (`ApplicationReadyEvent`) syncing suspended SKUs to Redis.
- [x] **Task 0.7: ArchUnit Architecture Boundary Verification**
  - [x] Implement `ArchUnitBoundaryTest` validating pure `shared-kernel`, hexagonal layer boundaries, and zero forbidden cross-context imports.
  - [x] Execute `mvn clean test-compile && mvn test -Dtest=ArchUnitBoundaryTest` (14/14 ArchUnit rules verified passing).

---

## 3. Detailed Slices Roadmap (Slices 1 through 8)

### Slice 1: Working Revisions & Two-Tier California Masking (CUJ 1) - COMPLETED
* **Goal:** Dynamic hero variant fallback and shadow revisions for non-disruptive regional compliance remediation.
* **Delivered Components:**
  - `catalog-context` Domain Enums & Models:
    - [VariantLifecycleState.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/enums/VariantLifecycleState.java): 8-state guarded FSM (`SKU_DRAFT`, `SKU_IN_REVIEW`, `SKU_ACTIVE`, `SKU_RESTRICTED`, `SKU_SUSPENDED`, `SKU_RECALLED_LOT_HOLD`, `SKU_DISCONTINUED`, `SKU_ARCHIVED`).
    - [RestrictionReason.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/enums/RestrictionReason.java): `PROP_65_NON_COMPLIANT`, `REGIONAL_COMPLIANCE_HOLD`, etc.
    - [RevisionState.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/enums/RevisionState.java): `WORKING_DRAFT`, `IN_REVIEW`, `APPROVED`, `REJECTED`, `PROMOTED`.
    - [VariantRevision.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/VariantRevision.java): Shadow working revision aggregate root.
    - [Variant.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/Variant.java): Enhanced with `marketComplianceMap`, `isRegionallyCompliant`, `applyRegionalMask`, `clearRegionalMask`, `bestseller`, `displayPriority`, `publishedVersion`.
    - [ProductFamily.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/ProductFamily.java): Enhanced with deterministic `resolveHeroVariant(marketLocale)` (filters compliant variants, sorts by bestseller -> displayPriority -> SKU).
  - Outgoing Ports & Facades:
    - [ComplianceValidatorPort.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/ports/ComplianceValidatorPort.java): Pure domain outgoing port in `catalog-context`.
    - [ComplianceEvaluationFacade.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/main/java/com/retail/pim/compliance/application/facades/ComplianceEvaluationFacade.java): Evaluates working revision attributes against California Prop 65 mandatory fields without circular Maven dependencies.
    - [CatalogOutboxPort.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/ports/CatalogOutboxPort.java): Port for decoupled outbox event persistence.
  - Infrastructure & Persistence:
    - [VariantRevisionJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/entity/VariantRevisionJpaEntity.java) & [PostgresVariantRevisionRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/PostgresVariantRevisionRepository.java).
    - [VariantJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/entity/VariantJpaEntity.java) & [PostgresProductRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/PostgresProductRepository.java) updated for FSM and market compliance mapping.
  - Application Orchestration & REST Presentation:
    - [VariantRevisionUseCase.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/VariantRevisionUseCase.java) & [VariantRevisionService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/VariantRevisionService.java): Coordinates shadow revision creation, evaluation, promotion (attribute merge, mask clearance, version bump, and `VariantPromotedEvent`).
    - [VariantRevisionController.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/presentation/VariantRevisionController.java): REST endpoints under `/api/v1/catalog/variants/{sku}/revisions`.
  - Events:
    - [VariantPromotedEvent.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/events/VariantPromotedEvent.java).
  - Tests:
    - [HeroVariantSelectionTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/domain/model/HeroVariantSelectionTest.java): CUJ 1 California Prop 65 hero fallback vs unrestricted NY market tests (5/5 passing).
    - [VariantRevisionTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/domain/model/VariantRevisionTest.java): Aggregate lifecycle and transition invariants (5/5 passing).
    - [VariantRevisionServiceTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/services/VariantRevisionServiceTest.java): End-to-end orchestration tests (7/7 passing).
    - [VariantRevisionControllerTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/presentation/VariantRevisionControllerTest.java): MockMvc REST endpoint validation (5/5 passing).
  - Verification: 9/9 modules `BUILD SUCCESS` (including 14/14 ArchUnit boundary checks).

---

### Slice 2: Class 1 Safety Recall & Kafka QoS Event Dispatch (CUJ 2) - COMPLETED
* **Goal:** Emergency recall execution publishing directly to Kafka QoS Express Lane with zero database wait.
* **Delivered Components:**
  - `compliance-context` Domain Events:
    - [ProductLotRecallInitiatedEvent.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/main/java/com/retail/pim/compliance/domain/events/ProductLotRecallInitiatedEvent.java): High-priority safety recall domain event.
  - Outgoing Ports & Messaging:
    - [KafkaQosPort.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/main/java/com/retail/pim/compliance/application/ports/KafkaQosPort.java): Port for emergency Kafka event dispatch.
    - [KafkaQosEventPublisher.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/main/java/com/retail/pim/compliance/infrastructure/messaging/KafkaQosEventPublisher.java): Publishes directly to topic `compliance.report.events.qos` keyed by SKU with zero database wait.
  - Public Facades & In-Process Integration:
    - [CatalogRecallFacade.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/facades/CatalogRecallFacade.java) & [CatalogRecallFacadeImpl.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/facades/CatalogRecallFacadeImpl.java): Public facade in `catalog-context` transitioning variants to `SKU_RECALLED_LOT_HOLD`, applying safety restriction masks, adding lot hold reasons, and persisting state.
  - Application Services & REST Presentation:
    - [EmergencyRecallUseCase.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/main/java/com/retail/pim/compliance/application/services/EmergencyRecallUseCase.java) & [EmergencyRecallService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/main/java/com/retail/pim/compliance/application/services/EmergencyRecallService.java): Orchestrates in-process variant quarantine and zero-wait Kafka QoS dispatch.
    - [EmergencyRecallController.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/main/java/com/retail/pim/compliance/presentation/controllers/EmergencyRecallController.java): REST API `POST /api/v1/compliance/recalls` returning `202 Accepted` (`RecallResponseDto`).
  - Catalog Asynchronous Event Listener:
    - [RecallEventListener.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/messaging/RecallEventListener.java): Consumer on `compliance.report.events.qos` using `IdempotentConsumerGuard` for idempotent replay protection.
  - Tests:
    - [RecallQosDispatchTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/test/java/com/retail/pim/compliance/RecallQosDispatchTest.java): CUJ 2 end-to-end integration test (Infant formula Lot #9042 Cronobacter contamination recall).
    - [EmergencyRecallServiceTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/test/java/com/retail/pim/compliance/application/services/EmergencyRecallServiceTest.java): Service orchestration tests.
    - [KafkaQosEventPublisherTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/test/java/com/retail/pim/compliance/infrastructure/messaging/KafkaQosEventPublisherTest.java): Kafka messaging serialization and topic dispatch tests.
    - [EmergencyRecallControllerTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/compliance-context/src/test/java/com/retail/pim/compliance/presentation/controllers/EmergencyRecallControllerTest.java): REST API MockMvc tests.
    - [CatalogRecallFacadeTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/facades/CatalogRecallFacadeTest.java): Facade quarantine state and suspension reason tests.
    - [RecallEventListenerTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/infrastructure/messaging/RecallEventListenerTest.java): Idempotent Kafka consumer adapter tests.
  - Verification: 9/9 modules `BUILD SUCCESS` (including 14/14 ArchUnit boundary checks).

---

### Slice 3: Bundle Component Masking & Sub-2ms Protection (CUJ 3) - COMPLETED
* **Goal:** Distributed Redis Set instant component masking combined with chunked (250 items, `ORDER BY id`) database cascades.
* **Delivered Components:**
  - `catalog-context` Domain Model & Repository:
    - [BundleComponent.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/BundleComponent.java): Domain entity for bundle component dependencies.
    - [BundleComponentRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/repository/BundleComponentRepository.java): Domain repository port for bundle mapping queries.
  - Persistence & Infrastructure:
    - [BundleComponentJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/entity/BundleComponentJpaEntity.java) & [PostgresBundleComponentRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/PostgresBundleComponentRepository.java): JPA mapping for `catalog.bundle_component`.
  - Batch Coordinator Engine:
    - [BundleMaskNotificationPort.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/ports/BundleMaskNotificationPort.java): Domain outgoing port for immediate blacklist and bundle masking.
    - [BatchComponentCoordinator.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/BatchComponentCoordinator.java): Coordinates chunked cascades (250 per chunk), deterministic lock ordering (`ORDER BY id` / ascending natural sorting), and companion parent family auto-suspension.
  - Syndication Facade & Sub-2ms Read Controller:
    - [SyndicationModuleFacade.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/facades/SyndicationModuleFacade.java) & [SyndicationModuleFacadeImpl.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/facades/SyndicationModuleFacadeImpl.java): In-process facade implementing `BundleMaskNotificationPort` and integrating with `RedisBlacklistRepository`.
    - [StorefrontAvailabilityController.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/presentation/controllers/StorefrontAvailabilityController.java): REST API `GET /api/v1/syndication/availability/bundles/{sku}` checking Redis Set `blacklisted_component_skus` sub-2ms.
  - Tests:
    - [BundleComponentTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/domain/model/BundleComponentTest.java): Domain entity invariant and value equality tests.
    - [BatchComponentCoordinatorTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/services/BatchComponentCoordinatorTest.java): Chunking (600 items -> 3 chunks) and deterministic ascending UUID sorting verification.
    - [SyndicationModuleFacadeTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/application/facades/SyndicationModuleFacadeTest.java): Facade blacklist and bundle availability evaluation tests.
    - [StorefrontAvailabilityControllerTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/presentation/controllers/StorefrontAvailabilityControllerTest.java): REST MockMvc availability response tests.
    - [BundleRedisMaskingTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/BundleRedisMaskingTest.java): CUJ 3 end-to-end integration test (1,200 bundles containing stop-sale SKU-CBL masked sub-2ms in Redis, with 5 deterministic sorted chunks in PostgreSQL).
  - Verification: 9/9 modules `BUILD SUCCESS` (including 14/14 ArchUnit boundary checks).

---

### Slice 4: Omnichannel Feed Resilience & Anti-Thrashing (CUJ 4) - COMPLETED
* **Goal:** External marketplace feed feedback processing with strict version-pinning to prevent stale rejection thrashing.
* **Delivered Components:**
  - `catalog-context` Public Facade & Application Service:
    - [CatalogChannelFeedbackFacade.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/facades/CatalogChannelFeedbackFacade.java) & [CatalogChannelFeedbackFacadeImpl.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/facades/CatalogChannelFeedbackFacadeImpl.java): Public facade exposing `reportChannelRejection`, `reportChannelSuccess`, and `getChannelStatuses`.
    - [ChannelFeedbackService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/ChannelFeedbackService.java): Anti-thrashing validation engine comparing `evaluatedVersion` against live `publishedVersion` (stale rejections discarded without mutating state, valid rejections set `channelStatusMap[channel] = RESTRICTED`).
  - `syndication-context` Feed Processing Engine:
    - [AmazonFeedProcessingReportParser.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/services/AmazonFeedProcessingReportParser.java): Parses asynchronous Amazon SP-API report feeds, routes HTTP 429 rate limiting errors to retry queues without state churn, and dispatches terminal errors to `CatalogChannelFeedbackFacade`.
  - Tests:
    - [ChannelFeedbackServiceTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/services/ChannelFeedbackServiceTest.java): Anti-thrashing unit tests (5/5 passing).
    - [AmazonFeedProcessingReportParserTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/application/services/AmazonFeedProcessingReportParserTest.java): SP-API report parsing and HTTP 429 backoff tests (2/2 passing).
    - [ChannelFeedbackAntiThrashingTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/ChannelFeedbackAntiThrashingTest.java): CUJ 4 end-to-end integration test (Amazon rejects older v1 while v2 is publishing, discarded as stale; subsequent v2 rejection restricts Amazon while preserving Shopify).
  - Verification: 9/9 modules `BUILD SUCCESS` (including 14/14 ArchUnit boundary checks).

---

### Slice 5: Skeleton Upsert, Embargoed Launch & Virtual Stock Math (CUJ 5) - COMPLETED
* **Goal:** Multi-lifecycle readiness gate (PIM, ERP, OMS) for edge read model, virtual bundle ATP math, and temporal embargo unmasking.
* **Delivered Components:**
  - `syndication-context` Domain Model & Event Primitives:
    - [ReadinessStatus.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/domain/model/ReadinessStatus.java): `UNINDEXED_SKELETON` (dormant unindexed read-model state) and `READY_INDEXED` (all 3 lifecycles satisfied).
    - [ChannelItemProjection.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/domain/model/ChannelItemProjection.java): Enhanced with `evaluateReadiness()` (PIM active, ERP price initialized, OMS stock initialized), dynamic discovery predicate `isDiscoverable(Instant asOf)` checking embargo schedules, monotonic sequence handlers (`applyPimMasterData`, `applyErpPrice`, `applyOmsStock`), and `createSkeleton()` factory.
    - [EmbargoLiftedEvent.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/domain/events/EmbargoLiftedEvent.java): Implements `DomainEvent` emitted when embargo schedule reaches threshold $T$.
  - `syndication-context` Application Services & Projector:
    - [StorefrontReadinessGate.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/services/StorefrontReadinessGate.java): Gatekeeper verifying 3-lifecycle readiness and filtering storefront search queries by dynamic embargo timestamp predicate.
    - [ProductReadModelProjector.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/projections/ProductReadModelProjector.java): Enhanced with `projectPimMasterData`, `projectErpPrice`, `projectOmsStock`, virtual bundle ATP calculation ($\min \lfloor Stock_i / Qty_i \rfloor$), and debounced write accumulator flusher (5-second window per bundle).
    - [ElasticsearchProjectionRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/infrastructure/database/ElasticsearchProjectionRepository.java): Extended with `getAllProjections()` and `deleteBySku()`.
  - `pim-bootstrap` Schedulers & Operational Workers:
    - [TemporalEmbargoScheduler.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/pim-bootstrap/src/main/java/com/retail/pim/bootstrap/schedulers/TemporalEmbargoScheduler.java): Scheduled poller identifying `READY_INDEXED` items whose `embargoUntil` has passed and publishing `EmbargoLiftedEvent` with deduplication caching.
    - [StaleSkeletonAuditReporter.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/pim-bootstrap/src/main/java/com/retail/pim/bootstrap/workers/StaleSkeletonAuditReporter.java): Non-destructive weekly auditor reporting dormant unindexed skeletons older than 30 days for human merchandising review without destructive deletion.
  - Tests:
    - [StorefrontReadinessGateTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/application/projections/StorefrontReadinessGateTest.java): 3-lifecycle readiness gate, temporal embargo discovery, bundle ATP math, debounced write accumulator, and monotonic version guards (5/5 passing).
    - [StorefrontReadinessGateIntegrationTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/StorefrontReadinessGateIntegrationTest.java): CUJ 5 smartphone launch with warehouse replenishment stock and ERP price arriving before PIM master data, unindexed skeleton verification, and discovery gating at timestamp $T$ (1/1 passing).
    - [TemporalEmbargoSchedulerTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/pim-bootstrap/src/test/java/com/retail/pim/bootstrap/schedulers/TemporalEmbargoSchedulerTest.java): Embargo threshold lifting, exactly-once event emission, and skeleton exclusion tests (2/2 passing).
    - [StaleSkeletonAuditReporterTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/pim-bootstrap/src/test/java/com/retail/pim/bootstrap/workers/StaleSkeletonAuditReporterTest.java): Non-destructive audit reporting of skeletons older than 30 days while preserving documents in storage (2/2 passing).
  - Verification: All 9 reactor modules `BUILD SUCCESS` (0 failures, 0 errors, 14/14 ArchUnit tests passed).

---

### Slice 6: "Last Variant Standing" & Concurrent Hold Protection (CUJ 6) - COMPLETED
* **Goal:** Dynamic family status demotion on zero active variants ("Last Variant Standing" rule) and PostgreSQL JSONB set concurrency for multiple suspension holds.
* **Delivered Components:**
  - `catalog-context` Domain FSM & Aggregates:
    - [FamilyLifecycleState.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/enums/FamilyLifecycleState.java): 4 states (`FAMILY_DRAFT`, `FAMILY_PUBLISHED`, `FAMILY_SUSPENDED`, `FAMILY_ARCHIVED`).
    - [VariantLifecycleState.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/enums/VariantLifecycleState.java): Guarded FSM transitions (`canTransitionTo`) across all 8 variant states.
    - [ProductFamily.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/ProductFamily.java): Added `activeSuspensionReasons` (`Set<String>`) and `lifecycleState` (`FamilyLifecycleState`). Implemented `applyHold()`, `liftHold()`, and `evaluateVariantCount()` enforcing the "Last Variant Standing" rule (auto-suspension with `NO_ACTIVE_VARIANTS` when active variants drop to 0, recovery when variants restore, ensuring concurrent administrative/legal holds prevent premature restoration).
    - [ProductLifecycleState.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/enums/ProductLifecycleState.java): Deprecated in favor of modern FSM architecture.
  - `catalog-context` Persistence Layer:
    - [03-lifecycle-resilience.sql](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/db-init/03-lifecycle-resilience.sql): Added `lifecycle_state` column to `catalog.product_family`.
    - [ProductJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/entity/ProductJpaEntity.java): Mapped `active_suspension_reasons` JSONB column with `JsonbConverters.StringSetConverter` and `lifecycle_state` enum column.
    - [PostgresProductRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/PostgresProductRepository.java): Deep domain <-> JPA conversion for `activeSuspensionReasons` and `lifecycleState`.
  - `catalog-context` Application Services & Presentation API:
    - [FamilyHoldUseCase.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/FamilyHoldUseCase.java): Pure use-case interface for hold management.
    - [FamilyHoldManagementService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/FamilyHoldManagementService.java): Implements `applyHold`, `liftHold`, and `getActiveHolds` with optimistic locking version verification.
    - [VariantStateManagementService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/VariantStateManagementService.java): Manages variant state transitions (`transitionVariantState`, `suspendVariant`, `restoreVariant`) and evaluates parent family "Last Variant Standing" state.
    - [AdministrativeHoldController.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/presentation/AdministrativeHoldController.java): REST endpoints (`POST /api/v1/catalog/families/{id}/holds`, `DELETE /api/v1/catalog/families/{id}/holds/{reason}`, `GET /api/v1/catalog/families/{id}/holds`) supporting `If-Match` optimistic concurrency headers.
  - Tests:
    - [ProductFamilyStateMachineTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/domain/model/ProductFamilyStateMachineTest.java): "Last Variant Standing" auto-suspension on 0 variants, recovery on restoration, legal hold coexistence preventing premature recovery, and 8-state variant FSM guards (3/3 passing).
    - [SuspensionSetConcurrencyTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/services/SuspensionSetConcurrencyTest.java): Multiple hold coexistence, optimistic lock version conflict protection, and variant state coordinator integration (3/3 passing).
    - [AdministrativeHoldControllerTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/presentation/AdministrativeHoldControllerTest.java): REST API unit tests for applying and lifting holds with `If-Match` headers (3/3 passing).
  - Verification: 9/9 modules `BUILD SUCCESS` (including 14/14 ArchUnit boundary checks).

---

### Slice 7: Financial Temporal Audit & UOM Logistics Reconciliation (CUJ 7) - COMPLETED
* **Goal:** Bi-temporal historical auditing on financial & dimensional attributes across Unit-of-Measure (UOM) tiers and Base UOM (`EACH`) prerequisite validation.
* **Delivered Components:**
  - `catalog-context` Domain Model & Repository Port:
    - [FinancialAttributeHistory.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/FinancialAttributeHistory.java): Bi-temporal entity tracking dimensions (`lengthCm`, `widthCm`, `heightCm`), `grossWeightKg`, `taxBand`, `customsHsnCode`, with temporal validity check `isEffectiveAt(Instant asOf)` and `closeEffectivePeriod(Instant closedAt)`.
    - [FinancialTemporalAuditRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/repository/FinancialTemporalAuditRepository.java): Domain repository port exposing `findHistoricalSnapshot`, `findCurrentActive`, and `findHistoryBySku`.
  - `catalog-context` Persistence Layer:
    - [FinancialAttributeHistoryJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/entity/FinancialAttributeHistoryJpaEntity.java): JPA entity mapping `catalog.financial_attribute_history`.
    - [SpringDataFinancialAttributeHistoryJpaRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/SpringDataFinancialAttributeHistoryJpaRepository.java): Spring Data JPA repository with temporal range queries (`effectiveFrom <= asOf AND effectiveTo > asOf`).
    - [PostgresFinancialTemporalAuditRepository.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/PostgresFinancialTemporalAuditRepository.java): PostgreSQL adapter implementing `FinancialTemporalAuditRepository`.
  - `catalog-context` Application Services & Presentation API:
    - [UomPrerequisiteValidator.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/compliance/UomPrerequisiteValidator.java): Validates mandatory Base UOM tier (`EACH`), valid GTIN-12..14 barcode, and strictly positive physical dimensions before allowing variant transitions to `SKU_ACTIVE`.
    - [FinancialMasterDataUseCase.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/FinancialMasterDataUseCase.java): Pure use-case interface for financial and logistics temporal auditing.
    - [FinancialMasterDataService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/FinancialMasterDataService.java): Orchestrates bi-temporal close-and-insert semantics (closing active record at `effectiveFrom` and inserting new record active until `INFINITY`).
    - [FinancialMasterDataController.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/presentation/FinancialMasterDataController.java): REST endpoints (`GET /api/v1/catalog/skus/{sku}/financials?asOf={timestamp}&uomTier={tier}`, `PUT`, `POST`).
  - Tests:
    - [FinancialBiTemporalAuditTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/domain/model/FinancialBiTemporalAuditTest.java): CUJ 7 historical freight invoice audit (`asOf = 2026-10-25`), customer return tax reconciliation (`asOf = 2026-11-05`), current active state (`asOf = 2026-11-15`), and bi-temporal continuity invariants (2/2 passing).
    - [UomPrerequisiteValidatorTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/compliance/UomPrerequisiteValidatorTest.java): Base UOM ("EACH") requirement, GTIN-12..14 barcode validation, and physical dimension verification (4/4 passing).
    - [FinancialMasterDataControllerTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/presentation/FinancialMasterDataControllerTest.java): REST API unit tests for point-in-time `asOf` snapshot queries and attribute updates (2/2 passing).
  - Verification: 9/9 modules `BUILD SUCCESS` (including 14/14 ArchUnit boundary checks).

---

### Slice 8: End-of-Life Tombstoning & Monotonic CQRS Read-Model Upsert (CUJ 8) - COMPLETED
* **Goal:** Tombstoning decommissioned products, purging edge search caches while retaining relational historical records for statutory warranty lookups, providing WMS quarantine certification exit bypass, and enforcing monotonic sequence updates on read-model projectors.
* **Delivered Components:**
  - `catalog-context` Domain Model & Events:
    - [ProductTombstonedEvent.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/events/ProductTombstonedEvent.java): Domain event carrying `sku`, `familyId`, `reason`, and `occurredOn` emitted when variants are permanently decommissioned.
  - `catalog-context` Application Services & Facades:
    - [VariantDecommissionService.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/services/VariantDecommissionService.java): Implements `decommissionVariant` (`SKU_DISCONTINUED` transition, `ProductTombstonedEvent` dispatch, parent family "Last Variant Standing" evaluation) and `archiveVariant` (`SKU_ARCHIVED` transition after 7-year statutory warranty retention period).
    - [CatalogQuarantineFacade.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/facades/CatalogQuarantineFacade.java): Application facade defining `certifyLotQuarantine(String sku, String lotNumber)` for WMS operational bypasses.
    - [CatalogQuarantineFacadeImpl.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/application/facades/CatalogQuarantineFacadeImpl.java): Restores quarantined variants from `SKU_RECALLED_LOT_HOLD` back to `SKU_ACTIVE`, strips recall suspension reasons, and synchronizes parent family state.
  - `syndication-context` Application Messaging & Projections:
    - [ProductReadModelProjector.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/projections/ProductReadModelProjector.java): Extended with `purgeProjection(String sku)` to evict tombstoned SKUs from in-memory projection stores and `ElasticsearchProjectionRepository`.
    - [ProductTombstonedEventListener.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/infrastructure/messaging/ProductTombstonedEventListener.java): Spring `@EventListener` consuming `ProductTombstonedEvent` and triggering immediate edge cache eviction.
    - [ExternalMonotonicSequenceProjector.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/messaging/ExternalMonotonicSequenceProjector.java): Monotonic sequence enforcer for external Kafka streams (ERP pricing, OMS inventory) guaranteeing out-of-order stale versions are discarded (`incoming.version <= lastProcessedVersion`).
  - Tests:
    - [ProductTombstoningTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/services/ProductTombstoningTest.java): Variant decommissioning to `SKU_DISCONTINUED`, `ProductTombstonedEvent` emission, parent family auto-suspension on last variant decommissioning, archival to `SKU_ARCHIVED`, and relational persistence retention for warranty lookup (4/4 passing).
    - [CatalogQuarantineFacadeTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/test/java/com/retail/pim/catalog/application/facades/CatalogQuarantineFacadeTest.java): WMS quarantine certification bypass restoring `SKU_RECALLED_LOT_HOLD` to `SKU_ACTIVE`, stripping recall suspension reasons, and invalid state guard tests (3/3 passing).
    - [CqrsMonotonicUpsertTest.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/test/java/com/retail/pim/syndication/application/messaging/CqrsMonotonicUpsertTest.java): Monotonic guard verifying ERP pricing updates in order, stale message discards, OMS inventory version ordering, and `ProductTombstonedEvent` edge cache eviction (3/3 passing).
  - Verification: 9/9 modules `BUILD SUCCESS` (including 14/14 ArchUnit boundary checks).
