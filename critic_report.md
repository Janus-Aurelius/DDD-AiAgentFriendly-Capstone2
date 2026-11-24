# Critic Review Report (Stage 7)

**Target Document:** [pim-lifecycle-state-machine-and-resilience.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/designs/pim-lifecycle-state-machine-and-resilience.md)  
**Target Scope:** FeatureRange (Lines 1–591)  
**Auditors:** Hostile Design Critic (`egm-design`), Structural Layout Validator (`egm-structural`), and Technical Copy Editor (`egm-style`)  
**Standards Evaluated:** `feature-spec-standard.md` & Style Guidelines  

---

## 1. System Architecture & Scalability Findings (`egm-design`)

### Finding D-1: Missing Mandatory "Risks, Limits & Mitigations" Section
* **Severity:** 🔴 Blocker
* **Affected Scope:** Feature Specification Root (Lines 1–591)
* **Finding & Recommendation:** The Feature Specification Standard mandates that every specification must explicitly detail constraints, failure modes, security implications, and scalability limits ("Risks & Limits" / "Risks & Mitigations"). While individual tactical mitigations are scattered across CUJs and architectural notes, lines 1..591 lack a dedicated top-level section consolidating operational failure modes, peak QPS limits, bundle fan-out limits, and connection pool exhaustion mitigations.  
*Action:* Add a dedicated top-level section `## Risks, Failure Modes & Mitigations` before `## Alternatives Considered`.

### Finding D-2: 7-Day Skeleton Eviction Causes Permanent Data Loss for Slow-Enriching SKUs
* **Severity:** 🔴 Blocker
* **Affected Scope:** Section 7: Composite Read-Model (lines 440–456) & CUJ 5 (lines 100–104)
* **Finding & Recommendation:** The readiness gate allows OMS to create unindexed skeleton documents upon warehouse arrival (`readinessStatus = "UNINDEXED_SKELETON"`). Lines 440–456 specify a background worker executing Elasticsearch `_delete_by_query` where `createdAt < now - 7d`. In enterprise retail, copywriting, photography, and compliance frequently take 2 to 4 weeks. If merchandising takes 8 days, Elasticsearch silently purges the skeleton document, permanently destroying the authoritative OMS warehouse stock quantity. Because OMS emits stock events transactionally on changes (not periodic full syncs), when PIM master data is published on Day 8+, the inventory fragment is missing, leaving `StockInitialized = false` and stranding the product permanently.  
*Action:* Never execute blind deletion of unindexed skeletons containing external operational state. The purge worker must verify with OMS before deletion, and add a fallback re-hydration query to OMS/ERP during the `ProductPublished` projector step if the skeleton is missing.

### Finding D-3: Kafka Partition Head-of-Line Blocking & Ephemeral Storage Loss in Gap-Hold Buffer
* **Severity:** 🟡 Major
* **Affected Scope:** Section 7: Consumer Monotonic Sequence Handling (lines 434–439) & CUJ 8 (lines 122–127)
* **Finding & Recommendation:** Holding out-of-order Kafka messages in a local pod-level RocksDB buffer for 15 seconds introduces two failure modes:
  1. *Partition Head-of-Line Blocking:* If consumption stalls waiting for missing versions, all unrelated SKUs on that shared Kafka partition are blocked.
  2. *Ephemeral Pod Loss:* If the consumer advances Kafka offsets, buffered events in local pod storage (RocksDB) are lost on Kubernetes pod restarts or consumer group rebalances, causing permanent state desynchronization.  
*Action:* Decouple gap buffering from the Kafka consumption loop. Commit Kafka offsets and store out-of-order events in a durable shared holding structure or dead-letter topic with asynchronous snapshot re-hydration.

### Finding D-4: Unbounded Projection Fan-Out Storm on Derived Virtual Bundle Stock
* **Severity:** 🟡 Major
* **Affected Scope:** Section 7: Composite Read-Model (lines 390–432) & Overview (lines 56–57)
* **Finding & Recommendation:** When OMS emits stock updates for a component SKU shared across 1,200 bundles, recomputing $\min(\lfloor Stock_i / Qty_i \rfloor)$ requires issuing 1,200 bulk document updates to Elasticsearch. During flash sales (50 updates/sec), this generates up to 60,000 document writes/sec to Elasticsearch, overwhelming cluster write queues.  
*Action:* Do not project derived bundle stock into persistent Elasticsearch documents on every individual component inventory event. Compute derived bundle ATP dynamically at checkout/cart time, or introduce a debouncing write accumulator that batches and flushes derived bundle stock updates at bounded intervals (e.g., at most once every 5–10 seconds per bundle).

### Finding D-5: Redis Blacklist Synchronization, Cold-Start Vulnerability & Search Inconsistency
* **Severity:** 🟡 Major
* **Affected Scope:** Section 4: Instant Bundle Protection via Native Redis Set (lines 308–320) & CUJ 3 (lines 79–87)
* **Finding & Recommendation:**
  1. *Search Facet and Pagination Drift:* Elasticsearch executes search, facets, and pagination internally. If bundle exclusion is performed post-query at the API gateway via Redis, pagination totals and facet counts will include masked bundles, causing "ghost" pages.
  2. *Cold-Start Vulnerability:* If Redis restarts, `blacklisted_component_skus` is wiped. The specification defines no cold-start re-hydration mechanism from PostgreSQL.  
*Action:* Specify that edge Redis masking is enforced strictly at the Checkout Gate, Cart, and PDP direct fetch (Boundary A), while Elasticsearch search index masking is eventual via the Batch Coordinator (Boundary B). Add an automated Redis cold-start re-hydration routine that repopulates `blacklisted_component_skus` from PostgreSQL on application startup.

### Finding D-6: Outbox Schema Lacks Multi-Listener Fan-Out & Startup Replay Locking
* **Severity:** 🟡 Major
* **Affected Scope:** Section 2: Guardrail 3 (lines 209–212) & Section 8: Persistence DDL (lines 515–529)
* **Finding & Recommendation:** The `outbox.outbox_event` table defines a single `listener_id VARCHAR(100)` and a single `completed_at TIMESTAMPTZ`. If an internal domain event (e.g., `ProductPublished`) has multiple module listeners (`Compliance`, `Syndication`, `DAM`), a single `completed_at` column cannot track independent completion. Furthermore, multiple pods restarting simultaneously will execute concurrent duplicate replays without distributed locking or `FOR UPDATE SKIP LOCKED`.  
*Action:* Separate event publication from subscription tracking using a multi-listener subscription model (or Spring Modulith event publication registry) and enforce `FOR UPDATE SKIP LOCKED` on internal replay pollers.

### Finding D-7: Unsorted Batch Updates Risk PostgreSQL Deadlocks
* **Severity:** 🟡 Major
* **Affected Scope:** Section 4: Batch Coordinator Persistence (lines 273–279)
* **Finding & Recommendation:** The batch coordinator updates 250 bundles using `WHERE id = ANY(:chunkOf250BundleVariantIds)`. If two component failures cascade across overlapping bundle sets simultaneously without deterministic sorting of IDs, PostgreSQL row-level locks on `ANY(...)` can result in cyclic deadlock exceptions.  
*Action:* Enforce deterministic sorting (`ORDER BY id` / sorted array of IDs) on chunk IDs to eliminate database deadlocks.

### Finding D-8: Embargo Mechanism Ambiguity (Query Predicate vs Scheduled Event)
* **Severity:** 🟢 Nit
* **Affected Scope:** Section 5: Temporal Embargoes & Street Dates (lines 332–336)
* **Finding & Recommendation:** Clarify that search queries dynamically gate listings via `now() >= embargoUntil`, while `EmbargoLiftedEvent` invalidates API Gateway response caches and index alias filters without triggering bulk document re-indexing.

### Finding D-9: Missing Table DDL for Core Aggregates in Persistence Appendix
* **Severity:** 🟢 Nit
* **Affected Scope:** Section 8: Persistence DDL Appendix (lines 459–530)
* **Finding & Recommendation:** Include the schema definitions (or ALTER statements) for `catalog.product_family` and `catalog.product_variant` in Section 8 so column types, nullability, and default constraints can be verified.

---

## 2. Document Structure & Layout Findings (`egm-structural`)

### Finding ST-1: Maintainer Attribution Violation without Linked Citations
* **Severity:** 🟡 Major
* **Affected Scope:** `## Alternatives Considered` (Lines 538, 543, 548, 553, 558, 563, 568, 573, 578, 583, 588)
* **Discrepancy Description & Rationale:** The Feature Specification Standard states: *"Rejection rationales must state concrete technical or scope trade-offs and must not attribute a rejection to a maintainer without linking their issue, discussion, or PR."* All 11 alternatives attribute rejection to authority figures without linked citations (e.g., *"Rejected by author."*, *"Rejected by review lenses."*).  
*Action:* Replace with objective technical headers: *"Decision: Rejected due to [technical rationale]"*.

### Finding ST-2: Structural Misplacement of Appendix
* **Severity:** 🟢 Nit
* **Affected Scope:** `### 8. Persistence DDL Appendix (Multi-Schema PostgreSQL)` (Lines 458–529)
* **Discrepancy Description & Rationale:** Section 8 is designated as an "Appendix" but is nested as a numbered subsection (`### 8`) within `## Technical Plan (Architecture)` prior to `## Alternatives Considered`.  
*Action:* Move to the end of the document as an unnumbered top-level appendix: `## Appendix: Persistence DDL`.

### Finding ST-3: Code Block Language Identifier Mismatch
* **Severity:** 🟢 Nit
* **Affected Scope:** `#### Skeleton Cleanup` (Lines 442–455)
* **Discrepancy Description & Rationale:** The Elasticsearch delete-by-query endpoint invocation is enclosed in a code block tagged as ````json`, but line 443 contains an HTTP command header (`POST /storefront_catalog/_delete_by_query`).  
*Action:* Retag block as `http` or `console`.

### Finding ST-4: LaTeX Math Delimiter Collision with Currency Notation
* **Severity:** 🟢 Nit
* **Affected Scope:** `### Alternative K` (Line 586)
* **Discrepancy Description & Rationale:** Line 586 contains the inline string `fees ($15-$25/package)`. Standard Markdown engines supporting math typesetting (KaTeX) parse `$15-$` as an inline math formula and corrupt text display.  
*Action:* Escape currency amounts as `\$15–\$25`.

### Finding ST-5: Formatting Syntax Inconsistencies
* **Severity:** 🟢 Nit
* **Affected Scope:** Line 7 (80-character hyphen divider) & Lines 328–330 (asterisk vs hyphen bullet nesting)
* **Discrepancy Description & Rationale:** Line 7 uses an 80-character hyphen divider instead of standard CommonMark `---`. Lines 328–330 mix asterisk and hyphen list markers.  
*Action:* Standardize on `---` and consistent bullet nesting.

---

## 3. Style, Tone & Calibration Findings (`egm-style`)

### Finding SY-1: Uncalibrated Absolute Claim on Zero-Impact Database Isolation
* **Severity:** 🟡 Major
* **Affected Scope:** Section 2: Guardrail 4 (Lines 216–217)
* **Style Rationale:** Line 217 claims: *"Heavy background spikes on `pim-worker` never impact latency or pool availability on `pim-api`."* While dedicated connection pools isolate against thread pool starvation, both profiles share the same Primary PostgreSQL cluster for write operations. Under heavy batch mutations, PostgreSQL CPU, disk I/O, WAL generation, and row lock contention on the primary instance directly affect `pim-api` query latency.  
*Action:* `CALIBRATE`. Narrow to: *"Dedicated connection pools isolate `pim-api` from background worker connection starvation; write load on the shared primary database is constrained by chunking (250 items per transaction) to minimize lock contention."*

### Finding SY-2: Hyperbolic Latency Claims ("Instant 0ms") Contradicted by Integration Test Criteria
* **Severity:** 🟡 Major
* **Affected Scope:** Lines 13, 34, 79, 83, 318, 664, 674, 826
* **Style Rationale:** The document repeatedly asserts *"instant 0ms Redis Set inverse masking"*, *"masking all 1,200 bundles... within 0ms"*, and *"blocks checkout with 0ms latency and 0% false positives"*. "0ms" is physically impossible over a network socket and represents marketing hyperbole, directly contradicted by the integration test criteria in Line 826 ($< 2\text{ ms}$).  
*Action:* `CALIBRATE`. Replace all occurrences of "0ms" and "instant" with calibrated performance terminology (e.g., *"sub-2ms"* or *"low-latency edge masking"*). Replace *"0% false positives"* with *"deterministic set membership"*.

### Finding SY-3: Inconsistent Gap-Hold Buffer Jitter Specification
* **Severity:** 🟡 Major
* **Affected Scope:** Section 7: Monotonic Sequence Handling (Line 438) vs. Section 8 / Slice 8 (Line 801) & Concepts Brief (Line 26)
* **Style Rationale:** Line 438 defines the buffer timeout as *"a jittered window ($15\text{ s} \pm 5\text{ s}$)"* (10s to 20s), whereas Line 801 and `concepts_brief.md` define it as *"a jittered 15-second timer (`15s + rand(0, 5000ms)`)"* (15s to 20s).  
*Action:* `CALIBRATE`. Standardize Line 438 to: *"a jittered window of 15 to 20 seconds (`15s + rand(0, 5000ms)`)"*.

### Finding SY-4: Colloquialisms and Fluff in CUJs and Problem Statement
* **Severity:** 🟢 Nit
* **Affected Scope:** Lines 21, 24, 68, 97, 104, 123
* **Style Rationale:** Strip informal slang ("whiteboard designs" $\to$ "initial specifications"; "network hiccup" $\to$ "transient network failure"; "storefront downtime" $\to$ "restricting SKU availability on compliant channels"). Strip marketing storytelling in CUJ 5 ("secret global launch", "flagship smartphone").
