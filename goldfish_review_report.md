# Goldfish Annotated Review Report

**Status:** resolved  
**Target:** [docs/designs/pim-lifecycle-state-machine-and-resilience.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/designs/pim-lifecycle-state-machine-and-resilience.md) (Full File, Lines 1–832)  
**Resolution Strategy:** Unified Architectural Consolidation (Resolving Contradictions & Codebase Traps)

---

## Prioritized Findings & Resolution Decisions

### Issue 1 [`egm-zoom-out` / `egm-comprehension` / `egm-implementation-readiness`] - Severity: Blocker
* **Affected Scope:** Goal 6, Section 6, Slice 2 (`oms-adapter`, lines 35, 339–387, 656–661)
* **Discrepancy Description:** `backend-java/oms-adapter` implements parcel-level carrier intercept APIs, delivery webhooks, pick halts, and customer refunds inside PIM, violating Non-Goals 2 & 3 and DDD bounded context separation.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** PIM master data is strictly bounded to product definitions and lifecycle state. In a safety recall, PIM transitions the variant to `SKU_RECALLED_LOT_HOLD` and emits `ProductLotRecallInitiatedEvent` to the Kafka QoS topic (`compliance.report.events.qos`). Downstream order fulfillment and parcel interception belong strictly to OMS/WMS.
* **Proposed Edit:**
  * Purge `backend-java/oms-adapter` and `RecallOrderInterceptionService.java` from Slice 2.
  * Reframe Section 6 as an external downstream consumer contract.
  * In Slice 2 integration tests, assert that initiating a recall publishes the expected Kafka QoS event.

---

### Issue 2 [`egm-codebase-details` / `egm-zoom-out`] - Severity: Blocker
* **Affected Scope:** Guardrail 2 (lines 193–207), Slices 0–8 File Lists
* **Discrepancy Description:** The design cites non-existent paths (`catalog-module/internal/...`) and reorganizes existing Maven modules into `api/` vs `internal/` submodules. The actual repository uses `backend-java/*-context` with Hexagonal layers (`domain/`, `application/`, `infrastructure/`, `presentation/`).
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Speculative directory restructuring creates massive file churn and breaks Maven compilation. Enforcing boundary isolation via ArchUnit on existing hexagonal packages preserves Clean Architecture with zero file churn.
* **Proposed Edit:**
  * Map all file paths in Slices 0–8 to existing `backend-java/<name>-context/src/main/java/com/retail/pim/<name>/...`.
  * Register `pim-bootstrap` in `backend-java/pom.xml` with its own `pom.xml` and `@SpringBootApplication` entry point (`PimApplication.java`).

---

### Issue 3 [`egm-codebase-details` / `egm-zoom-out`] - Severity: Blocker
* **Affected Scope:** Guardrail 3 (lines 209–212), Slice 0 (lines 606–610, 618)
* **Discrepancy Description:** Placing `OutboxEventJpaEntity`, `DurableModuleOutboxService`, and `RedisBlacklistRepository` into `shared-kernel` causes immediate compilation failures because `shared-kernel/pom.xml` is pure Java (`jakarta.validation-api` only) with zero Spring, JPA, or Redis dependencies.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Clean Architecture dictates that `shared-kernel` contains only pure domain primitives and base event contracts. Infrastructure entities and framework listeners must reside in context infrastructure packages.
* **Proposed Edit:**
  * Keep `shared-kernel` 100% pure Java.
  * Reuse the existing per-schema `<context>.outbox_event` pattern from `02-enable-jsonb.sql` (in `catalog-context`, `compliance-context`, `syndication-context`), placing JPA entities in each context's `infrastructure/persistence` package.
  * Place Redis repositories in `syndication-context/infrastructure/redis`.

---

### Issue 4 [`egm-codebase-details` / `egm-implementation-readiness`] - Severity: Blocker
* **Affected Scope:** Slices 0, 1, 4, 6, 8 (Target Files)
* **Discrepancy Description:** Missing core domain aggregates (`Variant.java`, `VariantJpaEntity.java`, `ProductFamily.java`, `ProductJpaEntity.java`) and lifecycle enums from the file modification inventory, despite adding 8 columns and 8 FSM states. Missing domain events, facades, and test paths.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Violates the Zero-Decision Rule and prevents automated or manual execution.
* **Proposed Edit:**
  * Explicitly list `Variant.java`, `VariantJpaEntity.java`, `ProductFamily.java`, and `ProductJpaEntity.java` as `(Modify)` with their exact fields.
  * Define `VariantLifecycleState` (8 states) and `FamilyLifecycleState` (4 states) enums in `catalog-context/domain/enums`.
  * Enumerate all domain events, public facades, repositories, and test classes with repository-relative paths.

---

### Issue 5 [`egm-design` / `egm-structural`] - Severity: Blocker
* **Affected Scope:** Document Structure (pre-Alternatives)
* **Discrepancy Description:** Missing mandatory top-level "Risks, Limits & Mitigations" section required by `feature-spec-standard.md`.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Standard compliance requires explicit documentation of operational ceilings, failure modes, and architectural trade-offs.
* **Proposed Edit:**
  * Add a dedicated top-level section `## Risks, Failure Modes & Mitigations` before `## Alternatives Considered`.

---

### Issue 6 [`egm-design` / `egm-inventive-catalyst` / `egm-zoom-out`] - Severity: Blocker
* **Affected Scope:** Section 7 (lines 440–456), Slice 5 (lines 728–732)
* **Discrepancy Description:** Deleting unindexed skeletons after 7 days via automated `_delete_by_query` destroys valid warehouse stock if merchandising takes > 7 days, stranding the product upon publication.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** 100,000 skeletons consume merely ~20 MB. Deleting active inventory data and building synchronous REST fallback clients is accidental complexity.
* **Proposed Edit:**
  * Delete the automated 7-day `_delete_by_query` deletion worker.
  * Retain unindexed skeletons in Elasticsearch until `ProductPublishedEvent` completes enrichment.
  * Add a non-destructive 30-day operational alert for stale skeletons.
  * Remove redundant synchronous `AuthoritativeSnapshotClient` during publish projection.

---

### Issue 7 [`egm-implementation-readiness`] - Severity: Blocker
* **Affected Scope:** Slices 1, 2, 6, 7, 8 (Controllers)
* **Discrepancy Description:** Multiple REST controllers omit request payload structures, response schemas, and HTTP status codes.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Violates the Zero-Decision Rule.
* **Proposed Edit:**
  * Define explicit DTO record schemas, HTTP verbs, paths, and response codes (200, 201, 204, 400, 404, 409) for all endpoints.

---

### Issue 8 [`egm-zoom-out` / `egm-design` / `egm-inventive-catalyst`] - Severity: Major
* **Affected Scope:** Section 7 (lines 434–439), Slice 8 (lines 797–805)
* **Discrepancy Description:** Embedding C++ JNI RocksDB inside stateless Spring Boot pods for a 15-second gap-hold window causes head-of-line blocking and pod-restart data loss.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** CQRS read-models receive state-replacement updates (current stock, current price). Newer versions supersede older versions.
* **Proposed Edit:**
  * Drop RocksDB, the 15-second timer, and `AuthoritativeSnapshotClient`.
  * Implement Monotonic Upsert on CQRS consumers: if `incoming.version > lastProcessedVersion`, upsert current state and advance pointer; if `<=`, drop as stale.

---

### Issue 9 [`egm-comprehension`] - Severity: Major
* **Affected Scope:** Section 3 (lines 249–256)
* **Discrepancy Description:** `SKU_RECALLED_LOT_HOLD` has only one exit transition (`LotQuarantineCertifiedEvent`), trapping products if the lot cannot be quarantined or if reformulation is required.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** A robust FSM must support permanent discontinuation and re-enrichment.
* **Proposed Edit:**
  * Add exit transitions: `SKU_RECALLED_LOT_HOLD --> SKU_DISCONTINUED: ProductDecommissionedEvent` and `SKU_RECALLED_LOT_HOLD --> SKU_IN_REVIEW: RemediationInitiatedEvent`.

---

### Issue 10 [`egm-comprehension` / `egm-design`] - Severity: Major
* **Affected Scope:** Section 4 (lines 271–280), Slice 3 (lines 673–695)
* **Discrepancy Description:** BatchComponentCoordinator updates variants via raw SQL without updating parent `ProductFamily.activeVariantCount` (violating "Last Variant Standing" if active variants drop to 0). Unsorted chunk IDs risk PostgreSQL deadlocks.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Relational row-level locks on `ANY(...)` without sorting deadlock under concurrent cascades. Parent families must be kept consistent with variant states.
* **Proposed Edit:**
  * Sort chunk UUIDs deterministically (`ORDER BY id` / ascending UUIDs).
  * Add companion family sync UPDATE in the same transaction transitioning any parent `ProductFamily` with 0 active variants to `FAMILY_SUSPENDED` with `{"NO_ACTIVE_VARIANTS": true}`.

---

### Issue 11 [`egm-design`] - Severity: Major
* **Affected Scope:** Section 7 (lines 390–432)
* **Discrepancy Description:** Recomputing bundle ATP on every individual component inventory event can produce 60,000 Elasticsearch writes/sec during flash sales.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** High-velocity component inventory changes must not overwhelm Elasticsearch write queues.
* **Proposed Edit:**
  * Introduce a debouncing write accumulator in `syndication-context` that flushes derived bundle ATP updates to Elasticsearch at bounded intervals (max once every 5 seconds per bundle).

---

### Issue 12 [`egm-style` / `egm-design` / `egm-inventive-catalyst` / `egm-zoom-out`] - Severity: Major
* **Affected Scope:** Overview, Section 4, Slice 0, Slice 3
* **Discrepancy Description:** "Instant 0ms" claims are physically impossible and contradicted by `< 2ms` integration tests. Redis Set masking at search query time causes pagination drift and cold-start risks.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Calibrate terminology to "sub-2ms" and restrict Redis `SMISMEMBER` strictly to Boundary A (Storefront Bundle Availability & PDP Direct Read API), while search index masking is eventual via Batch Coordinator.
* **Proposed Edit:**
  * Replace "0ms" with "sub-2ms".
  * Scope Boundary A strictly to PIM/Syndication Storefront Availability / PDP Read API (do not claim PIM runs checkout).
  * Add `@PostConstruct` / `ApplicationReadyEvent` cold-start initializer hydrating active suspended component SKUs from PostgreSQL into Redis on startup.

---

### Issue 13 [`egm-inventive-catalyst` / `egm-implementation-readiness`] - Severity: Major
* **Affected Scope:** Guardrail 3 (lines 209–212), Slice 0 (lines 608–611)
* **Discrepancy Description:** In-process outbox events replayed only on startup remain stranded if pods run for months without restarts. Single `completed_at` column cannot track multiple listeners.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Decouple event delivery guarantees from pod restart lifecycles.
* **Proposed Edit:**
  * Add a 60-second reconciliation sweeper in `pim-bootstrap` querying uncompleted outbox records (`completed_at IS NULL AND created_at < NOW() - INTERVAL '60 SECONDS'`).
  * Enforce `@Transactional(propagation = Propagation.REQUIRES_NEW)` for outbox row completion writes.

---

### Issue 14 [`egm-zoom-out` / `egm-style`] - Severity: Major
* **Affected Scope:** Guardrail 4, Slice 0 (lines 213–218, 611–615)
* **Discrepancy Description:** Premature introduction of dual-role profiles and multi-datasource read-replica routing when no read replica exists in docker-compose.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Keep the single binary supporting profiles in production, but drop `DataSourceBulkheadConfig.java` from Slice 0. Calibrate lock contention claims per SY-1.
* **Proposed Edit:**
  * Remove `DataSourceBulkheadConfig.java` from Slice 0.
  * Calibrate claims: lock contention is bounded by chunking into 250 items per transaction.

---

### Issue 15 [`egm-implementation-readiness` / `egm-codebase-details`] - Severity: Major
* **Affected Scope:** Section 8, Slice 0
* **Discrepancy Description:** Missing `CREATE SCHEMA IF NOT EXISTS` for compliance and syndication; missing columns (`published_version`, `gtin`, `fulfillment_mode`) and nullability/defaults in DDL.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Prevents SQL execution errors.
* **Proposed Edit:**
  * Add complete verified SQL DDL in Slice 0 covering schemas, grants, and explicit ALTER statements with `DEFAULT '{}'::jsonb NOT NULL`.

---

### Issue 16 [`egm-structural` / `egm-style`] - Severity: Major
* **Affected Scope:** Section: Alternatives Considered
* **Discrepancy Description:** Rejections cite "Rejected by author" or "Rejected by review lenses" without linked citations, violating standard.
* **AI Suggestion:** PROPOSE FIX
* **Justification:** Standards require objective technical trade-offs.
* **Proposed Edit:**
  * Replace attribution with: `"Decision: Rejected due to [objective technical rationale]"`.

---

### Issue 17 [`egm-style`] - Severity: Major
* **Affected Scope:** Section 7 (line 438) vs Section 8 / Slice 8 (line 801)
* **Discrepancy Description:** Inconsistent jitter formula ($15\text{ s} \pm 5\text{ s}$ vs `15s + rand(0, 5000ms)`).
* **AI Suggestion:** PROPOSE FIX (superseded by Monotonic Upsert, but standardized if noted)
* **Justification:** Mathematical consistency.
* **Proposed Edit:** Standardize on Monotonic Upsert without timer.

---

### Issues 18–27 (Nits) - Severity: Nit
* **AI Suggestion:** PROPOSE FIX
* **Proposed Edits:**
  * Relocate Persistence DDL to unnumbered top-level `## Appendix: Persistence DDL` at end of doc.
  * Tag code block as `http` in Skeleton Cleanup.
  * Escape dollar signs in currency notation (`\$15–\$25`).
  * Fix Markdown divider to `---` and unify bullet nesting.
  * Define acronym "ATP" (Available to Promise) inline in Section 1.
  * Clarify `EmbargoLiftedEvent` mechanism as cache/alias invalidator.
  * Strip colloquialisms ("whiteboard designs", "network hiccup", "downtime") and marketing fluff in CUJ 5.
  * Replace buzzwords ("Engine", "Atomic Symmetry") with standard technical terms.
  * Rephrase CUJ 6 to attribute variant deactivation to compliance holds or missing media rather than physical stockouts.
  * Document test infrastructure dependencies in Verification Plan.
