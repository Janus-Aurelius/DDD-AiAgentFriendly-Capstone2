# Intermediate Zoom Out Review Report (Stage 8)

**Target Document:** [pim-lifecycle-state-machine-and-resilience.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/designs/pim-lifecycle-state-machine-and-resilience.md)  
**Target Scope:** Full File (Lines 1–832)  
**Auditor:** Skeptical Architect (`egm-zoom-out`)  
**Evaluation Standard:** High-Altitude Simplicity, YAGNI, Codebase Minimization & Bounded Context Integrity  

---

### Finding ZO-8.1: Compounding Complexity in 7-Day Skeleton Eviction & Synchronous REST Re-hydration
* **Severity:** 🔴 Blocker
* **Affected Scope:** Goal 7 (lines 36–37), CUJ 5 (lines 103–104), Section 7 (Skeleton Cleanup, lines 440–455), Slice 5 (lines 728–732), and Proposed Critic/Catalyst Mitigations (IC-2, D-2)
* **Simplicity Rationale:** 
  - *The Manufactured Problem:* Skeletons in Elasticsearch with `readinessStatus == 'UNINDEXED_SKELETON'` are deleted after 7 days via automated `_delete_by_query` under the premise of "preventing storage bloat." Critic D-2 and Catalyst IC-2 correctly identified that in enterprise retail, physical inventory (OMS) or pricing (ERP) often arrives 2 to 4 weeks before copywriting and compliance complete. Purging skeletons after 7 days destroys warehouse inventory state, stranding the product upon publication.
  - *The Flawed Mitigation:* The proposed mitigations suggest keeping the 7-day purge and introducing a synchronous `AuthoritativeSnapshotClient` (with Resilience4j bulkheads and rate limiters) to query OMS/ERP REST APIs during `ProductPublishedEvent` projection to recover missing fragments.
  - *Skeptical Evaluation:* This is a textbook snowball of accidental complexity. An unindexed skeleton document is minimal (~200 bytes of JSON); 100,000 unindexed skeletons consume merely ~20 MB of disk space. Deleting active inventory data to save 20 megabytes, and then engineering synchronous cross-service REST fallback clients to recover from self-inflicted data loss during asynchronous projection, is severe over-engineering.
  - *Simpler Direction:* Eliminate the 7-day blind purge entirely. Unindexed skeletons represent valid upstream warehouse arrival state and must remain dormant until PIM master data is published. Stale or canceled items can be flagged via an asynchronous operational report (e.g., 30-day stale skeleton alert) for human audit. Eliminating the purge completely removes the need for synchronous REST snapshot re-hydration during event consumption.

---

### Finding ZO-8.2: Retaining External OMS Domain Logic as "Test Harness" Inside PIM Repository
* **Severity:** 🔴 Blocker
* **Affected Scope:** Goal 6 (line 35), Section 6 ("The 3-Zone Point of No Return Protocol in OMS", lines 339–387), Slice 2 (lines 656–661), and Proposed Implementation Readiness Mitigation (IR-3)
* **Simplicity Rationale:**
  - *The Scope Leak:* Non-Goal 3 explicitly specifies: *"Transactional Order & Stock Processing in PIM: Real-time stock reservation, shopping cart checkout, and warehouse fulfillment are owned by WMS/OMS."* Yet Slice 2 introduced `oms-adapter` with `RecallOrderInterceptionService` that evaluates order line manifests, halts warehouse picking, checks a $25 carrier intercept threshold, calls carrier APIs, and handles customer refunds.
  - *The Flawed Mitigation:* IR-3 proposes clarifying that `RecallOrderInterceptionService` is a test simulation stub residing in `pim-bootstrap/src/test/...` to simulate external OMS across Kafka.
  - *Skeptical Evaluation:* Moving `RecallOrderInterceptionService` from production code to test scope avoids an explicit architecture violation but retains an entire external bounded context's business logic within PIM's repository. PIM has no business implementing or testing carrier delivery intercept fees, carrier delivery webhook race escalation, or financial ledger refunds.
  - *Simpler Direction:* PIM's responsibility during a safety recall is strictly bounded: (1) transition the variant to `SKU_RECALLED_LOT_HOLD` in Catalog, and (2) emit `ProductLotRecallInitiatedEvent` to the Kafka QoS topic (`compliance.report.events.qos`). Section 6 should be documented purely as an external sequence diagram illustrating downstream consumer expectations, not as an internal PIM implementation contract. The integration test for Slice 2 should simply assert that initiating a recall publishes the expected Kafka event with correct headers. Purge `oms-adapter` and its multi-zone carrier simulation entirely from the PIM repository.

---

### Finding ZO-8.3: Replacing RocksDB with CQRS Monotonic Upsert vs. Distributed Queues
* **Severity:** 🟡 Major
* **Affected Scope:** CUJ 8 (lines 126–127), Section 7 (Consumer Monotonic Sequence Handling, lines 434–439), Slice 8 (lines 797–805), and Proposed Critic/Catalyst Mitigations (IC-4, D-3)
* **Simplicity Rationale:**
  - *The Manufactured Problem:* The design specifies an embedded C++ RocksDB buffer via JNI in `pim-worker` pods with a 15-second jittered timer to hold out-of-order Kafka messages, falling back to an `AuthoritativeSnapshotClient` (REST client) to re-fetch state from OMS/ERP.
  - *The Flawed Mitigation:* Critic D-3 and Catalyst IC-4 recommended replacing node-local RocksDB with a shared distributed holding structure (Redis delayed sorted set or Kafka dead-letter delay topic) and asynchronous REST snapshot fallback.
  - *Skeptical Evaluation:* In Kafka, messages partitioned by `sku` are strictly ordered per partition by design. Furthermore, the CQRS read-model in Syndication is an eventual-consistency projection of current stock and price, not an append-only financial ledger. If version 7 arrives with `stockLevel = 450`, the read-model needs current stock. Holding version 7 for 15 seconds waiting for obsolete version 6, or building synchronous REST snapshot endpoints to query external systems, introduces fragile synchronous coupling back into upstream systems.
  - *Simpler Direction:* Rely on standard Kafka partition keying by SKU. For the CQRS read model, adopt standard monotonic upsert semantics: if `incoming.version > lastProcessedVersion`, upsert current attributes into Elasticsearch/Redis and set `lastProcessedVersion = incoming.version`; if `incoming.version <= lastProcessedVersion`, drop as stale. If an application-level delta event genuinely requires strict gap repair, standard Kafka retry topics (`@RetryableTopic`) handle it natively without RocksDB, Redis delayed sets, or REST snapshot fallback clients.

---

### Finding ZO-8.4: Hand-Rolling Distributed Multi-Listener Outbox Plumbing vs. Leveraging Established Standards
* **Severity:** 🟡 Major
* **Affected Scope:** Section 2 (Guardrail 3, lines 209–212), Section 8 (lines 515–529), Slice 0 (lines 606–610, 614–615), and Proposed Critic/Catalyst Mitigations (IC-1, D-6)
* **Simplicity Rationale:**
  - *The Compounding Complexity:* Slice 0 creates a new schema `outbox`, placing `OutboxEventJpaEntity` into pure Java `shared-kernel`. Critic D-6 noted it lacks multi-listener fan-out (single `listener_id`, single `completed_at`), and Catalyst IC-1 noted failed in-process events remain stranded indefinitely without an active sweeper watchdog. To mitigate this, proposals require: (1) multi-listener subscription tracking tables, (2) active background sweeper polling every 60s with `FOR UPDATE SKIP LOCKED`, (3) an event-type-to-Class deserialization registry, and (4) `REQUIRES_NEW` transaction management.
  - *Skeptical Evaluation:* Hand-rolling a custom transactional event router with multi-listener completion tracking is reinventing an enterprise messaging broker inside relational tables, while polluting pure Java `shared-kernel` with JPA/Spring dependencies.
  - *Simpler Direction:* Keep `shared-kernel` strictly pure Java (zero JPA, zero Spring). To solve in-process cross-module event dispatch reliably without hand-rolling custom table infrastructure, either:
    1. Leverage Spring Modulith's built-in `spring-modulith-starter-jpa` (`EventPublicationRegistry`), which natively provides automated multi-listener tracking, completion management, and resilient background republishing out of the box; or
    2. For external messaging to Kafka, reuse the existing `catalog.outbox_event` table already present in [02-enable-jsonb.sql](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/db-init/02-enable-jsonb.sql#L80-L92) with a standard scheduled relay in `catalog-context`. Hand-rolling a custom multi-tenant `outbox.outbox_event` table in `shared-kernel` violates YAGNI and codebase conventions.

---

### Finding ZO-8.5: Premature Optimization of Dual-Role Kubernetes Profiles and Multi-DataSource Routing
* **Severity:** 🟡 Major
* **Affected Scope:** Goal 1 (line 30), Section 1 (lines 135–140), Section 2 (Guardrail 4, lines 213–218), Slice 0 (lines 611–615)
* **Simplicity Rationale:**
  - *The Proposal:* Splitting the single monolith into two separate Kubernetes deployment profiles (`pim-api` and `pim-worker`) with distinct HikariCP connection pools (`maxPoolSize = 30` vs `10`), 3-second statement timeouts, profile-conditional worker beans, and dynamic read-replica routing (`@Transactional(readOnly = true)`).
  - *Skeptical Evaluation:* This is premature optimization before the domain model is even functional. The repository currently has no read-replica infrastructure configured in Docker Compose ([docker-compose.yml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/docker-compose.yml#L1-L20) runs a single Postgres instance). Introducing dynamic routing data sources, multiple HikariCP connection pools, and deployment role splits adds heavy configuration overhead, complicates local testing and CI, and provides zero benefit during early feature implementation.
  - *Simpler Direction:* Deploy as a unified Spring Boot application. Batch operations (e.g., `BatchComponentCoordinator`) and background schedulers can run asynchronously using standard Spring task executors (`@Async`, `@Scheduled`) within the single application. Connection pool tuning and deployment profile separation (`pim-api` vs `pim-worker`) should be deferred until real performance bottlenecks or connection starvation are measured in staging/production environments.

---

### Finding ZO-8.6: Unnecessary Redis Dependency for Checkout Gate Masking Outside PIM Domain
* **Severity:** 🟡 Major
* **Affected Scope:** Goal 5 (line 34), CUJ 3 (lines 83–87), Section 4 ("Instant Bundle Protection via Native Redis Set", lines 308–320), Slice 0 (lines 618–619), Slice 3 (lines 674, 696–698)
* **Simplicity Rationale:**
  - *The Proposal:* Introducing a distributed Redis Set (`blacklisted_component_skus`) to provide low-latency bundle masking at request time, while asynchronously persisting updates to PostgreSQL in chunks of 250.
  - *Skeptical Evaluation:*
    1. *Context Leakage:* Non-Goal 3 explicitly states shopping cart checkout and real-time stock reservations belong to WMS/OMS, not PIM. Implementing a low-latency Redis Set specifically for the Checkout Gate creates an unmanaged secondary authority inside PIM for a capability owned by e-commerce/OMS.
    2. *Infrastructure bloat:* Redis is not present in [docker-compose.yml](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/docker-compose.yml). Adding Redis introduces operational overhead (deployment, clustering, memory management, connection pooling).
    3. *Split-brain & cold-start:* Maintaining both Redis and PostgreSQL creates dual-authority drift. As Critic D-5 highlighted, Redis restarts wipe the set without cold-start hydration, and Elasticsearch search queries cannot query Redis, leading to post-query filtering pagination bugs ("ghost" pages).
  - *Simpler Direction:* Remove Redis as an infrastructure dependency. PIM's responsibility when a component SKU is suspended is to update its catalog aggregates and project the restriction into the storefront read model (Elasticsearch) via the Batch Component Coordinator. E-commerce checkout validation belongs to commerce services inspecting bundle availability. Removing Redis simplifies local development, eliminates split-brain risks, and aligns strictly with PIM's bounded context.

---

### Finding ZO-8.7: Module Renaming and Directory Restructuring Churn
* **Severity:** 🟢 Nit
* **Affected Scope:** Section 2 (Guardrail 2, lines 193–207), Slice 0 through Slice 8 target file paths
* **Simplicity Rationale:**
  - *The Proposal:* Renaming existing Maven modules from `*-context` (e.g., `catalog-context`, `syndication-context`) to `*-module` and forcing an `api/` vs `internal/` submodule hierarchy.
  - *Skeptical Evaluation:* The existing repository uses established Hexagonal Architecture across contexts (`catalog-context`, `taxonomy-context`, `dam-context`, `localization-context`, `syndication-context`, `compliance-context`) with packages `domain/`, `application/`, `infrastructure/`, and `presentation/`. Inventing a wholesale module renaming scheme (`*-module/api` vs `*-module/internal`) introduces massive file churn, breaks parent POM references, and creates broken file paths in the design document without architectural necessity.
  - *Simpler Direction:* Retain the existing `backend-java/*-context` module naming and hexagonal package structure. Enforce boundary isolation using ArchUnit tests on hexagonal layer rules (e.g., domain does not depend on infrastructure; contexts communicate only via public application facades or domain events) rather than physically restructuring Maven directories.
