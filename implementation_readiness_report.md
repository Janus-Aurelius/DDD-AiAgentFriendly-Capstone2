# Implementation Readiness Audit Report (Stage 9)

**Target Document:** [pim-lifecycle-state-machine-and-resilience.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/designs/pim-lifecycle-state-machine-and-resilience.md)  
**Target Scope:** Detailed Implementation Plan (Lines 592–832)  
**Auditors:** Tech Lead / Implementation Readiness (`egm-implementation-readiness`), Codebase Details (`egm-codebase-details`), and Technical Copy Editor (`egm-style`)  
**Standard Evaluated:** `implementation-readiness-standard.md` (The Zero-Decision Rule)  

---

## 1. Technical Quality & Codebase Alignment Findings (`egm-codebase-details`)

### Finding CD-1: Non-Existent File Paths Cited for Modification
* **Severity:** 🔴 Blocker
* **Affected Scope:** Lines 639 & 739 (`catalog-module/internal/.../ProductFamily.java`), Lines 696, 721, & 791 (`syndication-module/internal/.../ProductReadModelProjector.java`)
* **Discrepancy Description & Rationale:** The plan cites existing files for modification under non-existent module paths:
  - `catalog-module/internal` does not exist. The actual file is at [ProductFamily.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/ProductFamily.java) under module `catalog-context`.
  - `syndication-module/internal` does not exist. The actual file is at [ProductReadModelProjector.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/projections/ProductReadModelProjector.java) under module `syndication-context`.
  An automated execution agent or developer following this plan will fail or create detached files ignored by the build. The plan must either target the existing module structure (`catalog-context`, `syndication-context`) or explicitly define a prerequisite refactoring phase that moves and renames Maven modules.

### Finding CD-2: Missing Maven Module Declarations & Scaffolding (`pim-bootstrap`, root `pom.xml`)
* **Severity:** 🔴 Blocker
* **Affected Scope:** Lines 611–622 (`backend-java/pim-bootstrap/src/...`) & Line 813 (`mvn clean test-compile && mvn test -Dtest=ArchUnitBoundaryTest`)
* **Discrepancy Description & Rationale:** The plan creates production code and ArchUnit tests inside `pim-bootstrap`. However:
  - Neither `backend-java/pim-bootstrap/pom.xml` nor its Spring Boot main class (`PimApplication.java`) is listed in the plan (Create).
  - [backend-java/pom.xml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/pom.xml#L24-L32) parent POM `<modules>` list is not marked for modification to register `pim-bootstrap`.
  Without POM scaffolding and parent registration, Maven will not compile `pim-bootstrap`, and the verification command `mvn test -Dtest=ArchUnitBoundaryTest` will fail immediately.

### Finding CD-3: Architectural Inversion & Missing Dependencies in `shared-kernel`
* **Severity:** 🔴 Blocker
* **Affected Scope:** Lines 606–609 (`OutboxEventJpaEntity.java`, `DurableModuleOutboxService.java`), Line 618 (`RedisBlacklistRepository.java`)
* **Discrepancy Description & Rationale:** According to [shared-kernel/pom.xml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/shared-kernel/pom.xml#L19-L24), `shared-kernel` is a pure Java library whose sole dependency is `jakarta.validation-api`. It has NO dependencies on:
  - Jakarta Persistence / Hibernate (`@Entity`, `@Table`)
  - Spring Framework (`@TransactionalEventListener`, `@Transactional`)
  - Spring Data Redis / Lettuce / Jedis
  Adding JPA entities, Spring transactional listener services, and Redis repositories directly into `shared-kernel` causes immediate compilation failures and violates Clean Architecture invariants. These infrastructure components belong in bootstrap or dedicated infrastructure packages.

### Finding CD-4: Missing Target Scope for Variant Domain Aggregate & JPA Entity Updates
* **Severity:** 🔴 Blocker
* **Affected Scope:** Lines 605, 636–640 (Slice 1), 711–713 (Slice 4), 788–794 (Slice 8)
* **Discrepancy Description & Rationale:** In Slice 0, `catalog.product_variant` is altered with 8 new columns (`active_suspension_reasons`, `channel_status_map`, `market_compliance_map`, `suspended_components`, `embargo_until`, `street_date`, `is_bestseller`, `display_priority`). However, neither [Variant.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/Variant.java) nor [VariantJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/entity/VariantJpaEntity.java) is listed in the Target Scope for modification. `Variant.java` currently uses a legacy 3-state enum (`AVAILABLE`, `SKU_INCOMPLETE`, `DISCONTINUED`) with zero support for the 8-state FSM.

### Finding CD-5: Missing Domain & JPA Entity Updates for `ProductFamily` & Lifecycle Enums
* **Severity:** 🟡 Major
* **Affected Scope:** Lines 604, 740–757 (Slice 6)
* **Discrepancy Description & Rationale:** Slice 0 alters `catalog.product_family` (`active_suspension_reasons JSONB`) and Slice 6 implements `FAMILY_SUSPENDED` / `FAMILY_PUBLISHED` transitions. However:
  - [ProductJpaEntity.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/entity/ProductJpaEntity.java) is omitted from the plan and has no column mapping or JSONB converter for `active_suspension_reasons`.
  - [ProductLifecycleState.java](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/enums/ProductLifecycleState.java) currently defines only `DRAFT`, `ENRICHED`, `APPROVED`, `PUBLISHED`. The document never specifies modifying `ProductLifecycleState` or creating separate `FamilyLifecycleState` and `VariantLifecycleState` enums.

### Finding CD-6: PostgreSQL DDL Missing `outbox` Schema Creation & Permissions
* **Severity:** 🟡 Major
* **Affected Scope:** Lines 602–603 (`infra/db-init/03-lifecycle-resilience.sql`)
* **Discrepancy Description & Rationale:** The plan specifies DDL for `outbox.outbox_event`. However, [01-create-schemas.sql](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/db-init/01-create-schemas.sql#L7-L16) only initializes `catalog`, `taxonomy`, `dam`, and `localization`. Schema `outbox` does not exist. If `03-lifecycle-resilience.sql` executes without `CREATE SCHEMA IF NOT EXISTS outbox;` and `GRANT ALL ON SCHEMA outbox TO pim_user;`, container initialization fails with `ERROR: schema "outbox" does not exist`.

---

## 2. Execution Actionability & Readiness Findings (`egm-implementation-readiness`)

### Finding IR-1: Incomplete File Inventory & Missing Write-Allowlist Paths
* **Severity:** 🔴 Blocker
* **Affected Scope:** Lines 601–805 (Slices 0 to 8) & Lines 810–832 (Verification Plan)
* **Finding & Guidance:** The plan omits dozens of essential files explicitly referenced in the text:
  1. *Core Domain Entities:* `ProductVariant.java` (Modify), `BundleComponent.java` (Entity), `ProductVariantRepository.java` (Create/Modify).
  2. *Domain Events:* `VariantPromotedEvent.java`, `EmbargoLiftedEvent.java`, `StreetDateReachedEvent.java`, `FamilyRepublishedEvent.java`, `FinancialMasterUpdatedEvent.java`, `ProductTombstonedEvent.java`, `OmsStockEvent.java`, `ErpPriceEvent.java`.
  3. *Public Facades & Clients:* `ComplianceEvaluationFacade.java` in `compliance`, `SyndicationModuleFacade.java` in `syndication`, `AuthoritativeSnapshotClient.java`.
  4. *Controllers:* `EmergencyRecallController.java` (`POST /api/v1/compliance/recalls`).
  5. *Test Paths:* All 5 Unit test files (Lines 817–822) and all 7 Integration test files (Lines 824–832) lack repository-relative paths.  
  *Guidance:* Add every missing entity, event, facade, client, controller, POM, and test file with its full repository-relative path and tag `(Create)` or `(Modify)`.

### Finding IR-2: Unspecified REST API Contracts & Endpoint Definitions
* **Severity:** 🔴 Blocker
* **Affected Scope:** Lines 641–643 (`VariantRevisionController`), Lines 654–655 (`EmergencyRecallService`), Lines 758–760 (`AdministrativeHoldController`), Lines 779–781 (`FinancialMasterDataController`), Lines 803–805 (`AuthoritativeSnapshotController`)
* **Finding & Guidance:** Multiple controllers omit request payload structures, response schemas, and HTTP status codes:
  - `VariantRevisionController`: Omits HTTP verbs, paths, request DTOs, and responses.
  - `EmergencyRecallService`: Omits request JSON payload and response status for `POST /api/v1/compliance/recalls`.
  - `AdministrativeHoldController`: Omits request payload, version passing mechanism (`If-Match` vs body), and status codes for `POST /api/v1/catalog/families/{id}/holds` and `DELETE /api/v1/catalog/families/{id}/holds/{reason}`.
  - `FinancialMasterDataController`: Omits request body schema for `PUT /api/v1/catalog/skus/{sku}/financials`.
  - `AuthoritativeSnapshotController`: Omits snapshot response DTO structure for `GET /api/v1/catalog/products/{id}/snapshot`.  
  *Guidance:* Provide concrete DTO record definitions, path patterns, HTTP methods, and exact HTTP response codes (200, 201, 204, 400, 404, 409).

### Finding IR-3: Bounded Context Boundary Contradiction in Slice 2 (`oms-adapter`)
* **Severity:** 🔴 Blocker
* **Affected Scope:** Slice 2, Lines 656–661 (`RecallOrderInterceptionService.java` in `backend-java/oms-adapter`)
* **Finding & Guidance:** Slice 2 introduces `backend-java/oms-adapter` which cancels warehouse picks, voids payment authorizations, and queries carrier manifests. This contradicts Non-Goal 3 (Line 45) and Guardrail 2 (Line 194), while the database lacks order or parcel tables.  
  *Guidance:* Clarify that `RecallOrderInterceptionService` is a test simulation stub residing under `pim-bootstrap/src/test/...` (simulating external OMS behavior across Kafka for Testcontainers verification), not production PIM code.

### Finding IR-4: Incomplete DDL and Missing JPA Schema Mapping Details
* **Severity:** 🟡 Major
* **Affected Scope:** Slice 0 (Lines 602–605), Slice 4 (Lines 710–713), Slice 7 (Lines 774–775)
* **Finding & Guidance:**
  - Slice 0 does not specify exact SQL for altering `product_family` and `product_variant`. If JSONB columns lack `DEFAULT '{}'::jsonb NOT NULL`, PostgreSQL JSONB concatenation produces `NULL`.
  - Slice 4 requires `variant.publishedVersion`, but `published_version` is never added to `product_variant` in Slice 0.
  - Slice 7 requires GTIN-12 validation and `fulfillmentMode == PALLET_LOGISTICS`, but neither `gtin` nor `fulfillment_mode` is declared in DDL.  
  *Guidance:* Include the exact SQL `ALTER TABLE` statements in Slice 0 with column names, types, nullability, and default constraints.

### Finding IR-5: Underspecified Outbox Replay & Deserialization
* **Severity:** 🟡 Major
* **Affected Scope:** Slice 0, Lines 608–611 (`DurableModuleOutboxService.java`)
* **Finding & Guidance:** Storing `event_type VARCHAR(100)` and `payload JSONB` requires an event-type-to-Java-class mapping registry for safe deserialization. Post-commit row completion (`completed_at = NOW()`) requires `@Transactional(propagation = Propagation.REQUIRES_NEW)` to avoid silent JPA omissions.  
  *Guidance:* Document the event-type registry pattern and explicitly prescribe `Propagation.REQUIRES_NEW`.

### Finding IR-6: Undefined Kafka Invocation Mechanics for Batch Coordinator and Readiness Gate
* **Severity:** 🟡 Major
* **Affected Scope:** Slice 3 (Lines 673–695) & Slice 5 (Lines 721–725)
* **Finding & Guidance:** The plan omits listener annotations (`@KafkaListener` vs `@TransactionalEventListener`), topics, container factories, and DLQ retry policies for `BatchComponentCoordinator` and `ProductReadModelProjector`.  
  *Guidance:* Specify exact listener annotations, topics/event classes, and consumer group configurations.

---

## 3. Style & Tone Findings in Implementation Scope (`egm-style`)

### Finding SY-IR: Jargon Bloat and Uncalibrated Metrics
* **Severity:** 🟢 Nit
* **Affected Scope:** Lines 610, 637, 684, 701, 764
* **Style Rationale:** Strip uncalibrated micro-metrics (*"< 0.1ms latency"* $\to$ *"in-process synchronous execution"*), pseudo-technical comments (*"-- On Component Recovery (Atomic Symmetry):"* $\to$ *"-- On Component Recovery:"*), and buzzword inflation (*"Anti-Thrashing Engine"* $\to$ *"Anti-Thrashing Logic"*; *"Logistics UOM Engine"* $\to$ *"Logistics UOM Module"*).
