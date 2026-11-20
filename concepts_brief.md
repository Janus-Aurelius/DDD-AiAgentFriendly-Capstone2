# PIM Lifecycle State Machine & Event-Driven Resilience Architecture

* **Author:** Thien An Nguyen
* **Date:** 2026-11-17
* **Version:** 2.1 (Multi-Lens Validated & Hardened)

## Overview

The enterprise Product Information Management (PIM) platform governs the end-to-end lifecycle of product master data from supplier ingestion through AI enrichment, compliance verification, and multi-channel syndication.

This document defines the domain boundaries, event contracts, bidirectional state machine transitions, error compensation mechanisms, and cross-context integration patterns with ERP, WMS, OMS, and Finance. High-velocity transactional inventory and sales operations are decoupled using CQRS and Event Sourcing composite read models, keeping the PIM aggregate authoritative for master metadata without incurring transactional lock contention.

---

## Analysis

### 1. Inventive Contradictions & System Resolutions (TRIZ)
* **Contradiction 1: Lot Recall Precision vs. Bounded Context Separation**
  - *Conflict:* PIM must not track high-velocity warehouse lot/bin numbers, yet regulatory recalls require preventing contaminated items from reaching customers without indiscriminately halting sales of uncontaminated inventory across unaffected regional distribution centers.
  - *Resolution:* Implement a Two-Stage Selective Quarantine pattern. At the physical layer, WMS/OMS immediately locks allocation against affected lot IDs, while the edge read-model decrements available-to-promise (ATP) inventory. The PIM global `SKU_RECALLED_LOT_HOLD` state is reserved exclusively for uncontained, single-lot, or systemic contamination events.
* **Contradiction 2: Embargo Secrecy vs. Launch Thundering Herds**
  - *Conflict:* Embargoed products must remain unsearchable until timestamp $T$, but indexing thousands of embargoed SKUs at timestamp $T$ triggers search cluster re-indexing spikes and syndication quota exhaustion.
  - *Resolution:* Pre-warm composite read models to edge caches in a cryptographically gated dormant state evaluated against a timestamp predicate (`now() >= embargoUntil`). At timestamp $T$, scheduled timer events (`EmbargoLiftedEvent`) flip visibility flags across search gateways without bulk re-hydration.
* **Contradiction 3: Monotonic Sequence Ordering vs. Buffer Deadlock**
  - *Conflict:* Message consumers must enforce strict aggregate version ordering, but dropped intermediate messages can stall consumption partitions indefinitely.
  - *Resolution:* Implement a sliding gap-hold buffer with a jittered 15-second TTL (`15s + rand(0, 5000ms)`). If missing intermediate events fail to arrive within the TTL window, the consumer executes an Authoritative State Re-hydration request (rate-limited via bulkhead) to fetch the latest composite snapshot directly from PIM/ERP/OMS and resumes with the new sequence pointer.
* **Contradiction 4: Immediate Bundle Masking vs. Eventual Batch DB Cascades**
  - *Conflict:* Storefronts must immediately block sales of bundles containing recalled components, but asynchronously chunking 5,000 bundle DB updates takes several seconds to minutes.
  - *Resolution (TRIZ Inverse Resolution Gate):* Storefront edge projectors check a distributed Redis Set (`blacklisted_component_skus` via `SADD` / `SREM` / `SMISMEMBER`). If any `componentSku` in a bundle is present in the set, the bundle is instantly masked on the storefront with 0ms latency and 0% false positives, while the Batch Coordinator persists database bundle cascades in bounded 250-SKU chunks at leisure.

### 2. Guarded Finite State Machine & Aggregate Invariants
* **Two-Tier State Model:**
  - *Family Lifecycle States:* `FAMILY_DRAFT`, `FAMILY_PUBLISHED`, `FAMILY_SUSPENDED`, `FAMILY_ARCHIVED`.
  - *Variant Lifecycle States:* `SKU_DRAFT`, `SKU_IN_REVIEW`, `SKU_ACTIVE`, `SKU_RESTRICTED`, `SKU_SUSPENDED`, `SKU_RECALLED_LOT_HOLD`, `SKU_DISCONTINUED`, `SKU_ARCHIVED`.
  - *Scoped Availability Masks:* Variants maintain localized maps (`ChannelStatusMap[channelId]`, `MarketComplianceMap[marketCode]`). Local channel rejections or regional compliance bans (e.g., California Prop 65) mask availability specifically in those channels or regions while preserving live status on healthy storefronts. Merchandising resolves metadata defects via a Shadow Working Revision (`workingVersion` vs. `publishedVersion`).
* **Asymmetric Recovery & Concurrency Control on Suspension Sets:**
  - `ProductFamily` and Bundle aggregates maintain an `activeSuspensionReasons: Set<SuspensionReason>`. Bundles additionally maintain `suspendedComponents: Set<SkuId>`.
  - When active variants reach zero, the parent family transitions to `FAMILY_SUSPENDED` and adds `NO_ACTIVE_VARIANTS` to the set.
  - Variant recovery removes `NO_ACTIVE_VARIANTS` from the set. The parent family restores to `FAMILY_PUBLISHED` if and only if `activeSuspensionReasons.isEmpty()`, preventing variant updates from clearing concurrent legal, trademark, or manual compliance holds.
  - *Bundle Component Recovery:* When a component SKU recovers, its SKU ID is removed from `suspendedComponents` via `SREM`. The bundle removes `COMPONENT_DEPENDENCY` from `activeSuspensionReasons` if and only if `suspendedComponents.isEmpty()`.
  - *Unified Concurrency Guarantee:* To eliminate ORM entity cache desynchronization across cross-family components, all state-modifying commands and cascade events route through the broker keyed by the target aggregate root ID (`partitionKey = targetFamilyOrBundleId`). This guarantees that concurrent component events targeting the same parent Bundle serialize on that bundle's partition. Database mutations enforce optimistic version checking (`UPDATE ... WHERE version = :expectedVersion`) with a 3-attempt exponential backoff (`10ms -> 50ms -> 200ms`).
* **Hero Variant Fallback & URL Stability:**
  - If a canonical display variant is restricted, an automated fallback designates the next compliant `SKU_ACTIVE` variant (sorted deterministically by `displayPriority ASC`, `isBestseller DESC`, `createdAt ASC`) as the storefront representative. Canonical URL slugs are decoupled from variant SKUs to prevent SEO volatility.
* **Composite Bundle Restriction Cascades & Batching Thresholds:**
  - Component SKU suspension cascades `COMPONENT_DEPENDENCY` to parent virtual bundles (restricted to single-level nesting depth = 1).
  - *Threshold-Gated Fan-Out:*
    - Low Cardinality ($\le 50$ parent bundles): Cascades execute inline, emitting individual `VariantRestrictedEvent` messages.
    - High Cardinality ($> 50$ parent bundles): Sets transient flag `CASCADE_IN_PROGRESS` on parent aggregates and routes to the Batch Component Dependency Coordinator, which processes database state transitions in bounded chunks of 250 bundles per transaction and emits chunked `BatchComponentDependencySuspendedEvent` payloads (max 250 SKU IDs per message). Read-model projectors execute bulk `update_by_query` against `componentSkus`.

### 3. Cross-Context Architecture & Composite Edge Read Models
* **Bounded Context Identity Separation:**
  - The human-readable SKU string becomes strictly immutable across PIM, ERP, and WMS/OMS once exiting `SKU_DRAFT`. System-generated `VariantId` (UUID) serves as the technical primary key.
* **Read-Model Readiness Gate:**
  - Storefront read models (Elasticsearch / Redis) hold unindexed skeleton documents until three criteria are met:
    1. **PIM:** State == `SKU_ACTIVE` AND `now() >= embargoUntil`
    2. **ERP:** `PriceInitialized == True` AND (`Price > 0` OR `allowZeroPricePromo == True`)
    3. **OMS:** `StockInitialized == True` (direct stock for standard SKUs; derived $\min(\lfloor Stock_i / Qty_i \rfloor)$ for Virtual Bundles)
* **Partial Update Isolation & Two-Stage Skeleton TTL:**
  - Document upserts utilize namespaced partial scripts (`pim.*`, `erp.*`, `oms.*`) with optimistic concurrency (`_seq_no` / `_primary_term`).
  - *Orphan Governance:* Unindexed skeleton records carry a 48-hour Warning Threshold emitting batched `OrphanedSkeletonMismatchAlert` (grouped by vendor/batch to prevent alert fatigue) and a hard 7-Day Eviction TTL (604,800s). Redis evicts via native key TTL; Elasticsearch evicts via a scheduled worker executing `delete_by_query` on expired unindexed skeletons.
* **Terminal State Tombstoning:**
  - Terminal states (`SKU_DISCONTINUED`, `SKU_ARCHIVED`) emit `ProductTombstonedEvent`, instructing read-model projectors to purge search index records while retaining soft-deleted records in PIM for historical order audits.

### 4. Master Data for Finance, Tax & Logistics
* **Temporal Versioning (`effectiveFrom` / `effectiveTo`):**
  - Financial metadata (Tax Bands, HS Tariff Codes, Category Node assignments, Packaging Dimensions) is event-sourced with timestamps. Accounting audits past carrier invoices and customer returns against the state active on transaction date $T$.
* **Unit of Measure (UOM) Hierarchy:**
  - PIM models dimensions per packaging level (`EACH`, `INNER_PACK`, `MASTER_CARTON`, `PALLET`). Base tier `EACH` is universally required, while outer packaging tiers are conditionally required for palletized physical goods.
* **Cost Center & Vendor Rebate Attribution:**
  - Finance maps ERP sales units against PIM taxonomy branches and tracks SKU-to-campaign associations for co-op marketing chargebacks.

### 5. Event Choreography, QoS & The Granular 3-Zone PONR Contract
* **Three-Way Monotonic Sequence Handling:**
  - `Event.version <= lastProcessedVersion`: Discard and ACK immediately.
  - `Event.version == lastProcessedVersion + 1`: Process immediately and increment pointer.
  - `Event.version > lastProcessedVersion + 1`: Buffer in gap-hold queue (TTL = 15s + jitter) with fallback to bulkhead-governed authoritative state snapshot re-hydration.
* **QoS Express Lane & Granular Line-Item PONR Interception:**
  - Recall and compliance revocation events route through a dedicated high-priority messaging topic.
  - In-flight orders evaluate PONR at the **`FulfillmentPackage` / `OrderLine`** level (not aggregate order level) anchored at the `CarrierManifestCommitted` scan:
    1. **Zone 1: Pre-PONR (`ORDER_PLACED` through `PACKED_AWAITING_MANIFEST`):** OMS halts picking/packing, routes picked units to physical quarantine, and triggers immediate payment authorization void (or refund if already captured).
    2. **Zone 2: Post-PONR In-Transit (`CARRIER_MANIFESTED` through `OUT_FOR_DELIVERY`):**
       - *Value-Gated Intercept with Safety Override:* If `ItemValue < $25.00` AND `recallSeverity != CLASS_1_CRITICAL_SAFETY`, OMS bypasses carrier intercept and immediately triggers Zone 3 customer notification and refund. Class 1 safety recalls force carrier intercept regardless of unit price.
       - *Standard Intercept:* OMS emits `CarrierDeliveryInterceptRequestedEvent` to carrier APIs and marks line items `RECALL_INTERCEPT_PENDING`.
       - *Webhook Race Guard:* If carrier webhooks emit `CarrierDeliveredEvent` while in `RECALL_INTERCEPT_PENDING`, the state machine auto-escalates immediately to Zone 3.
    3. **Zone 3: Post-Delivery / Intercept Failed (`DELIVERED` or Carrier Intercept Rejected):** Emits `PostDeliveryRecallNoticeTriggeredEvent` to dispatch regulatory safety recall notices (email, SMS, physical letter) with disposal instructions and unconditional refund.
* **Transactional Outbox:**
  - Contexts execute local database commits and outbox record inserts atomically (`FOR UPDATE SKIP LOCKED` poller or CDC) to prevent phantom or missed event emissions.

---

## 6. Enforced Operational Thresholds & System Constants

| Parameter | Enforced Value | Architectural Purpose |
| :--- | :--- | :--- |
| **`BUNDLE_FANOUT_THRESHOLD`** | `50 bundles` | Switches cascade mode from inline events to the Batch Coordinator. |
| **`BUNDLE_BATCH_CHUNK_SIZE`** | `250 SKU IDs` | Bounds DB lock duration and keeps Kafka payloads `< 64KB`. |
| **`GAP_HOLD_BUFFER_TTL`** | `15s + rand(0, 5s)` | Jittered buffer wait before fallback snapshot re-hydration. |
| **`SKELETON_WARNING_THRESHOLD`** | `48 hours` | Triggers aggregated `OrphanedSkeletonMismatchAlert` for unenriched skeletons. |
| **`SKELETON_HARD_EVICTION_TTL`** | `7 days` | Eviction window: native Redis TTL + scheduled ES `delete_by_query`. |
| **`OPTIMISTIC_LOCK_RETRIES`** | `3 attempts` | Backoff schedule (`10ms, 50ms, 200ms`) on version conflicts. |
| **`IDEMPOTENCY_KEY_TTL`** | `7 days` | Redis deduplication key TTL; database versions remain permanent authority. |
| **`VALUE_INTERCEPT_MIN_FLOOR`**| `$25.00` | Minimum item value for Zone 2 carrier intercept (bypassed on `CLASS_1_CRITICAL_SAFETY`). |

---

## Goals vs. Non-Goals

* **In-Scope Goals:**
  - Implement a Guarded Finite State Machine with all eight declared global variant lifecycle states.
  - Implement the Two-Tier State Model with scoped availability masks (`ChannelStatusMap`, `MarketComplianceMap`).
  - Implement the "Hero Variant" fallback rule with deterministic tie-breaking and decoupled canonical URLs.
  - Implement the "Last Variant Standing" rule with upward recovery gated by `activeSuspensionReasons.isEmpty()` and protected by `@Version` optimistic locking.
  - Implement threshold-gated bundle restriction cascades ($\le 50$ inline; $> 50$ chunked in batches of 250 via `BatchComponentDependencySuspendedEvent`) paired with distributed Redis Set (`blacklisted_component_skus`) 0ms storefront inverse masking via `SMISMEMBER`.
  - Distinguish between transient channel errors and terminal catalog errors with version-pinned feedback.
  - Build a high-priority QoS Express Lane for recalls, paired with the 3-Zone Line-Item OMS Point-of-No-Return (PONR) interception protocol with `CLASS_1_CRITICAL_SAFETY` override.
  - Implement wall-clock event scheduling and dormant pre-warming for `embargoUntil` and `streetDate`.
  - Implement the Composite Read-Model Readiness Gate (`SKU_ACTIVE`, `PriceInitialized`, `StockInitialized`) with aggregated 48h warning alerts and 7-day skeleton TTL eviction.
  - Enforce consumer idempotency guards and monotonic version ordering with jittered 15s TTL snapshot re-hydration.
  - Define UOM packaging hierarchies with conditionally mandatory pallet tiers.
  - Implement temporal versioning (`effectiveFrom` / `effectiveTo`) for financial and logistical attributes.
  - Standardize SKU as the immutable universal key across PIM, ERP, and WMS/OMS.
  - Route cross-aggregate cascade commands keyed by target aggregate ID (`partitionKey = targetFamilyOrBundleId`).
  - Emit tombstone events on terminal states to purge read models from search indexes.
* **Out-of-Scope Non-Goals:**
  - Physical Lot/Batch tracking inside PIM (owned exclusively by WMS/ERP).
  - Real-time warehouse bin allocation or sales transaction processing inside PIM (owned by WMS/OMS).
  - General ledger and accounts payable accounting inside PIM (owned by ERP).
  - Re-enrichment loop controls and AI agent throttling (deferred as saved work for the Python AI agent phase).
  - Immediate implementation of distributed Saga coordinator infrastructure (deferred to event governance phase).

---

## Alternatives Weighed

* **Alternative 1: Absorbing Inventory & Sales into Catalog Aggregate**
  - *Decision:* Rejected by author. High-velocity stock updates cause severe database lock contention, invalidate cache snapshots, and violate bounded context separation.
* **Alternative 2: Global Family Demotion on Any Variant Defect**
  - *Decision:* Rejected by author. Pulling an entire product family when only one regional or physical variant is non-compliant causes unwarranted commercial disruption.
* **Alternative 3: In-Place Overwriting of Financial Attributes**
  - *Decision:* Rejected by author. Destructive attribute overwrites prevent accounting from auditing historical shipping invoices against historical dimensional weight.
* **Alternative 4: Tracking Warehouse Lots inside PIM**
  - *Decision:* Rejected by author. Lot tracking requires physical warehouse location knowledge, expiry date monitoring, and bin picking coordinates, which belong exclusively to WMS/OMS.
* **Alternative 5: Modeling State Progression as a DAG**
  - *Decision:* Rejected by author. Cyclic remediation loops between active, restricted, and review states invalidate DAG constraints; state transitions must be modeled as a Guarded Finite State Machine.
* **Alternative 6: Single Scalar Suspension Reason**
  - *Decision:* Rejected by author. A single scalar field allows automatic child variant recovery to inadvertently overwrite concurrent legal, trademark, or manual compliance holds.
* **Alternative 7: Indefinite Gap-Hold Buffering for Out-of-Order Events**
  - *Decision:* Rejected by review lenses. Buffering indefinitely on missing sequence numbers stalls consumer partitions if an intermediate event is dropped; a 15s TTL with authoritative snapshot re-hydration is required.
* **Alternative 8: Unbounded Single-Event or Unchunked Bundle Cascades**
  - *Decision:* Rejected by review lenses. Firing thousands of individual events overwhelms Kafka partitions, while updating thousands of bundles in a single transaction causes DB lock contention; threshold-gated chunking ($> 50$ bundles chunked at 250 per event) balances throughput and isolation.
* **Alternative 9: Probabilistic Bloom Filter for Component Blacklisting**
  - *Decision:* Rejected by author. Standard Bloom Filters do not support `SREM` deletion upon component recovery and introduce probabilistic false positives that randomly mask compliant bundles; a native Redis Set provides exact $O(1)$ operations with zero false positives.

---

## Resolved Reviewer Objections & Risk Mitigations

1. **High-Cardinality Bundle Cascade Storms (`egm-zoom-out` & `egm-structural`) — RESOLVED:**
   - *Mitigation:* Enforced in Section 2 and Section 6. Component cascades exceeding `BUNDLE_FANOUT_THRESHOLD` (50) are routed through the Batch Component Dependency Coordinator and emitted in bounded chunks of 250 SKU IDs via `BatchComponentDependencySuspendedEvent`. Storefront edge projectors check a distributed Redis Set (`blacklisted_component_skus` via `SMISMEMBER`) for 0ms immediate inverse masking with zero false positives.
2. **In-Flight Order "Point of No Return" (`egm-zoom-out`) — RESOLVED:**
   - *Mitigation:* Enforced in Section 5. The PONR boundary is evaluated per `FulfillmentPackage` / `OrderLine` at the `CarrierManifestCommitted` scan: Zone 1 Pre-PONR warehouse cancellation, Zone 2 Post-PONR transit intercept (governed by `$25.00` value floor with mandatory override for `CLASS_1_CRITICAL_SAFETY` and delivery-race auto-escalation), and Zone 3 Post-Delivery safety outreach.
3. **Concurrency on Suspension Sets & Skeleton Storage Leaks (`egm-codebase-details`) — RESOLVED:**
   - *Mitigation:* Enforced in Section 2 and Section 3. Cascade events serialize by target aggregate root (`partitionKey = targetFamilyOrBundleId`) and are guarded by conditional optimistic updates (`WHERE version = :expectedVersion`). Redis evicts skeletons via native TTL, while an index maintenance worker purges Elasticsearch skeletons matching `readinessStatus == 'UNINDEXED_SKELETON' AND createdAt < now - 7d`.
4. **Upward Recovery Asymmetry for Bundles (`egm-structural`) — RESOLVED:**
   - *Mitigation:* Bundles maintain `suspendedComponents: Set<SkuId>`. When a component SKU recovers, its ID is removed from `suspendedComponents` via `SREM`. The bundle only clears `COMPONENT_DEPENDENCY` from `activeSuspensionReasons` when `suspendedComponents.isEmpty()`.

---

## Key Decisions & Staging for Next Steps

1. **Phase 1: Guarded FSM & Two-Tier Availability Engine:** Implement all 8 declared global states, Two-Tier availability masks (`ChannelStatusMap`, `MarketComplianceMap`), "Hero Variant" fallback, "Last Variant Standing" with `@Version`-guarded `activeSuspensionReasons: Set<SuspensionReason>`, bundle `suspendedComponents: Set<SkuId>`, target-keyed partition routing (`partitionKey = targetFamilyOrBundleId`), and threshold-gated chunked bundle cascades (50 threshold / 250 chunk size) with distributed Redis Set inverse masking (`SMISMEMBER`).
2. **Phase 2: Continuous Compliance & 3-Zone Line-Item PONR Recall Ingestion:** Implement listeners in `catalog-context` and `oms-context` for compliance revocation, Two-Stage Selective Quarantine, QoS Express Lane, and the 3-Zone Line-Item Point-of-No-Return order interception protocol with `CLASS_1_CRITICAL_SAFETY` override.
3. **Phase 3: Omnichannel Feedback & Temporal Publishing:** Implement transient vs. terminal error classification, version-pinned feedback ingestion in `syndication-context`, dormant pre-warming and wall-clock scheduling for `embargoUntil` / `streetDate`, and index tombstoning.
4. **Phase 4: Financial Master Data & CQRS Read-Model Projection:** Implement event-sourced financial attributes with `effectiveFrom` / `effectiveTo` timestamps, multi-tier UOM dimensional models, and the Composite Read-Model Readiness Gate supporting Skeleton Upserts (aggregated 48h alert / 7d TTL via Redis TTL and ES `delete_by_query`) and jittered 15s TTL authoritative snapshot re-hydration.
