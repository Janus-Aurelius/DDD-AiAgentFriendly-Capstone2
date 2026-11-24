# PIM Lifecycle State Machine & Event-Driven Resilience: Master Implementation Plan & Tracker

* **Document Owner:** Thien An Nguyen
* **Status:** In Progress (Active Implementation)
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
| **Slice 4** | Omnichannel Feed Resilience & Anti-Thrashing (CUJ 4) | **IN PROGRESS** | Sprint 3 | ChannelFeedbackAntiThrashingTest |
| **Slice 5** | Skeleton Upsert, Embargoed Launch & Virtual Stock Math (CUJ 5) | PENDING | Sprint 3 | Storefront Readiness Gate & EmbargoLiftedEvent tests |
| **Slice 6** | "Last Variant Standing" & Concurrent Hold Protection (CUJ 6) | PENDING | Sprint 4 | ProductFamilyStateMachineTest & SuspensionSetConcurrencyTest |
| **Slice 7** | Financial Temporal Audit & UOM Logistics Reconciliation (CUJ 7) | PENDING | Sprint 4 | FinancialBiTemporalAuditTest & UOM prerequisite validators |
| **Slice 8** | End-of-Life Tombstoning & Monotonic CQRS Read-Model Upsert (CUJ 8) | PENDING | Sprint 5 | CqrsMonotonicUpsertTest & ProductTombstoningTest |

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

### Slice 4: Omnichannel Feed Resilience & Anti-Thrashing (CUJ 4) - IN PROGRESS
* **Goal:** External marketplace feed feedback processing with strict version-pinning to prevent stale rejection thrashing.
* **Target Files:**
  - `syndication-context`: `MarketplaceFeedReportParser.java`, `ChannelStatusFeedbackService.java`.
  - `catalog-context`: `CatalogChannelFeedbackFacade.java`, `ChannelStatusMap`.
  - Tests: `ChannelFeedbackAntiThrashingTest.java`.

### Slice 5: Skeleton Upsert, Embargoed Launch & Virtual Stock Math (CUJ 5)
* **Goal:** Multi-lifecycle readiness gate (PIM, ERP, OMS) for edge read model and embargo timestamp scheduling.
* **Target Files:**
  - `syndication-context`: `StorefrontReadinessGate.java`, `ElasticsearchCompositeProjector.java`, `EmbargoLiftedEvent.java`.
  - Tests: `StorefrontReadinessGateTest.java`.

### Slice 6: "Last Variant Standing" & Concurrent Hold Protection (CUJ 6)
* **Goal:** Dynamic family status demotion on zero active variants and PostgreSQL JSONB set concurrency for multiple suspension holds.
* **Target Files:**
  - `catalog-context`: `ProductFamily.java`, `ActiveSuspensionReasons`.
  - Tests: `ProductFamilyStateMachineTest.java`, `SuspensionSetConcurrencyTest.java`.

### Slice 7: Financial Temporal Audit & UOM Logistics Reconciliation (CUJ 7)
* **Goal:** Bi-temporal historical auditing on financial & dimensional attributes and Base UOM (`EACH`) prerequisite validation.
* **Target Files:**
  - `catalog-context`: `FinancialAttributeHistoryJpaEntity.java`, `FinancialTemporalAuditRepository.java`, `UomPrerequisiteValidator.java`.
  - Tests: `FinancialBiTemporalAuditTest.java`, `UomPrerequisiteValidatorTest.java`.

### Slice 8: End-of-Life Tombstoning & Monotonic CQRS Read-Model Upsert (CUJ 8)
* **Goal:** Tombstoning decommissioned products and enforcing monotonic sequence updates on read-model projectors.
* **Target Files:**
  - `syndication-context`: `MonotonicVersionEnforcer.java`, `ProductTombstonedEventListener.java`.
  - `catalog-context`: `ProductTombstonedEvent.java`.
  - Tests: `CqrsMonotonicUpsertTest.java`, `ProductTombstoningTest.java`.
