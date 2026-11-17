# PIM Lifecycle State Machine & Event-Driven Resilience Architecture

* **Author:** Thien An Nguyen
* **Date:** 2026-11-17

## Overview

The enterprise Product Information Management (PIM) platform governs the end-to-end lifecycle of product master data from supplier ingestion through AI enrichment, compliance verification, and multi-channel syndication. 

This document defines the refined domain boundaries, event contracts, bidirectional state machine transitions, error compensation mechanisms, and cross-context integration patterns with ERP, WMS, OMS, and Finance. High-velocity transactional inventory and sales operations are decoupled using CQRS and Event Sourcing composite read models, keeping the PIM aggregate authoritative for master metadata without incurring transactional lock contention.

---

## Architectural Constraints, Invariants & Edge Cases

### 1. Granular Variant State Machine & Aggregate Invariants
* **Two-Tier State Model (Global State vs. Scoped Availability Masks):**
  - **Global Lifecycle State:** (`SKU_DRAFT`, `SKU_IN_REVIEW`, `SKU_ACTIVE`, `SKU_RESTRICTED`, `SKU_SUSPENDED`, `SKU_RECALLED_LOT_HOLD`, `SKU_DISCONTINUED`, `SKU_ARCHIVED`) determines whether the SKU is fundamentally viable across the enterprise.
  - **Scoped Availability Masks:** Variants maintain localized maps: `ChannelStatusMap[channelId]` and `MarketComplianceMap[marketCode]`. If an individual marketplace (e.g., Amazon US) rejects a feed or a specific jurisdiction (e.g., California Prop 65 or EU chemical standards) issues a regional restriction, the variant remains globally `SKU_ACTIVE` while masking availability solely in the affected market or channel. Merchandising resolves metadata issues using a Shadow Working Revision (`workingVersion` vs. `publishedVersion`) without taking down compliant storefront channels.
* **Guarded Finite State Machine (FSM) & Remediation Flows:**
  - State transitions form a Guarded Finite State Machine rather than a cyclic graph without governance.
  - Transitions from `SKU_SUSPENDED` or `SKU_RESTRICTED` back to `SKU_ACTIVE` require validation gates. Metadata remediation occurs on a Shadow Working Revision (`SKU_IN_REVIEW`), requiring the compliance engine to emit a fresh `ProductComplianceApprovedEvent` before `SKU_ACTIVE` status is restored.
  - *Operational Hold Exception:* Precautionary warehouse holds (`SKU_RECALLED_LOT_HOLD`) transition directly back to `SKU_ACTIVE` upon consuming an authoritative `LotQuarantineCertifiedEvent` from WMS/Compliance, bypassing metadata re-authoring in `SKU_DRAFT`.
  - Only `SKU_DISCONTINUED` and `SKU_ARCHIVED` act as terminal acyclic sinks.
* **The "Hero Variant" Fallback:**
  - A `ProductFamily` designates one canonical variant for storefront search thumbnails and category landing pages.
  - If the canonical variant transitions to `SKU_RESTRICTED` or `SKU_SUSPENDED`, the `ProductFamily` executes an automated fallback promotion rule, designating the next compliant `SKU_ACTIVE` variant as the canonical representative.
* **The "Last Variant Standing" Rule & Asymmetric Recovery via Suspension Sets:**
  - A `ProductFamily` remains `PUBLISHED` on storefronts as long as at least one child variant remains `SKU_ACTIVE`.
  - Both `ProductFamily` and Bundle aggregates maintain an `activeSuspensionReasons: Set<SuspensionReason>` (e.g., `NO_ACTIVE_VARIANTS`, `EXPLICIT_COMPLIANCE_HOLD`, `TRADEMARK_DISPUTE`, `COMPONENT_DEPENDENCY`).
  - When active variants reach 0 (`activeVariantCount == 0`), the `ProductFamily` adds `NO_ACTIVE_VARIANTS` to `activeSuspensionReasons` and transitions to `SUSPENDED`.
  - **Upward Recovery Rule:** When an individual variant is remediated and restored to `SKU_ACTIVE` (`activeVariantCount: 0 -> 1`), it removes `NO_ACTIVE_VARIANTS` from the set. The `ProductFamily` automatically restores to `PUBLISHED` if and only if `activeSuspensionReasons.isEmpty()`. This prevents automated variant recovery from prematurely clearing concurrent legal, trademark, or manual compliance holds.
* **Composite Products (Bundles, Kits & Multipacks):**
  - Retail catalogs contain virtual bundles and multipacks consisting of multiple physical component SKUs.
  - When any component SKU transitions to `SKU_SUSPENDED` or `RECALLED`, an upward cascade adds `COMPONENT_DEPENDENCY` to the parent bundle's `activeSuspensionReasons` and transitions the bundle to `SKU_RESTRICTED`.
  - When all component SKUs recover to `SKU_ACTIVE`, `COMPONENT_DEPENDENCY` is removed from the set. The parent bundle automatically restores to `SKU_ACTIVE` if and only if `activeSuspensionReasons.isEmpty()`.
* **Terminal States (End-of-Life) & Tombstoning:**
  - Terminal states (`SKU_DISCONTINUED`, `SKU_ARCHIVED`) and suspended parent families emit `ProductTombstonedEvent` / `VariantTombstonedEvent`.
  - The CQRS read-model projector consumes tombstone events to evict the document from Elasticsearch and Redis caches, immediately removing it from storefront search while preserving historical master records in PIM for returns and financial audits.
* **Identifier Immutability:**
  - The human-readable SKU string becomes strictly immutable across all contexts once a variant exits `SKU_DRAFT`.
  - Packaging, formulation, or barcode changes requiring a new GTIN must either map to the existing SKU via an internal 1:N barcode alias table or trigger a net-new SKU lifecycle. System-generated `VariantId` (UUID) remains the technical primary key.

### 2. Multi-Channel Feedback & Syndication Constraints
* **Version-Pinned Channel Feedback (Anti-Thrashing):**
  - Asynchronous `ChannelListingRejectedEvent` messages carry the exact `CatalogSnapshotVersion` or `CorrelationId` of the evaluated payload.
  - `catalog-context` ignores rejection events for superseded versions, preventing stale callbacks from overwriting remediated product states.
* **Transient vs. Terminal Rejection Filtering:**
  - Syndication adapters distinguish between terminal domain rejections (e.g., character limit exceeded, missing country of origin) and transient infrastructure errors (HTTP 429, timeouts).
  - Only terminal domain errors emit `ChannelListingRejectedEvent`. Transient errors route to syndication retry queues with exponential backoff and jitter.
* **Recall QoS & Emergency Express Lane:**
  - `ProductComplianceRevokedEvent` and `ProductLotRecallInitiatedEvent` route through a dedicated high-priority messaging topic (Quality of Service Express Lane) that preempts routine catalogue sync.
* **Temporal Publishing (Embargoes vs. Street Dates):**
  - **`embargoUntil` (Information Embargo):** Secret products; strictly withheld from search indexing and storefront displays until timestamp $T$.
  - **`streetDate` (Commercial Release Date):** Product is visible on storefronts for "Pre-Order" / "Coming Soon", but fulfillment and shipping are locked until timestamp $T$.
  - A scheduled timer service emits `EmbargoLiftedEvent` and `StreetDateReachedEvent` at timestamp $T$ to release held syndication feeds and read-model projections without database polling.

### 3. Continuous Compliance & The Lot Recall Impedance Mismatch
* **Precautionary Global SKU Suspension on Lot Recall:**
  - PIM does not track physical warehouse lots or expiration dates (owned by WMS).
  - When an emergency `ProductLotRecallInitiatedEvent` occurs, PIM executes a precautionary global SKU suspension to block further purchases, while WMS locates, quarantines, and purges the contaminated physical lot numbers.
  - Authoritative `LotQuarantineCertifiedEvent` from WMS restores the SKU directly to `SKU_ACTIVE`.
* **In-Flight Customer Order Interception:**
  - The OMS context consumes `ProductComplianceRevokedEvent` and `ProductLotRecallInitiatedEvent` to immediately halt unfulfilled, in-flight customer orders currently being picked or packed in fulfillment centers.
* **Jurisdictional Compliance Scoping:**
  - Compliance policies and revocation events support jurisdictional scoping (`marketCode: EU | US_CA | GLOBAL`), updating `MarketComplianceMap` to restrict sales in non-compliant territories while preserving live status elsewhere.

### 4. Cross-Context Identity & The Composite Read-Model
* **Composite Read-Model Readiness Gate:**
  - Storefront read models (Elasticsearch / Redis) ingest asynchronous out-of-order event fragments from PIM, ERP, and OMS.
  - A SKU document remains dormant/unindexed until all three bounded contexts confirm baseline readiness:
    1. **PIM:** State == `SKU_ACTIVE` AND `now() >= embargoUntil`
    2. **ERP:** `PriceInitialized == True` AND (`Price > 0` OR `allowZeroPricePromo == True` [supports Gift-with-Purchase, free samples, warranty parts])
    3. **OMS:** `StockInitialized == True` (direct physical inventory for standard SKUs; derived $\min(\lfloor Stock_i / Qty_i \rfloor)$ for Virtual Bundles)
* **Skeleton Upserts (Out-of-Order Ingestion):**
  - If OMS stock events arrive before PIM master data publication, the read-model projector creates a hidden skeleton record holding the stock integer. Arrival of the PIM event enriches the skeleton and passes the readiness gate.

### 5. Finance, Tax & Accounting Temporal Constraints
* **Temporal Versioning for Financial Attributes:**
  - Financial, tax, and customs metadata (Tax Bands, HS Tariff Codes, Category Node assignments, Packaging Dimensions) must be event-sourced with `effectiveFrom` and `effectiveTo` timestamps.
  - Destructive in-place overwrites are prohibited. Accounting audits historical logistics invoices and customer returns against the master metadata active on the exact transaction date $T$.
* **Unit of Measure (UOM) Dimensional Hierarchy:**
  - PIM explicitly models logistics dimensions and weights keyed by packaging hierarchy levels:
    - `EACH` (Consumer unit, GTIN-12)
    - `INNER_PACK` (e.g., Pack of 6)
    - `MASTER_CARTON` (Case of 24, GTIN-14)
    - `PALLET`
  - Freight audit rules in Finance cross-check billed carrier weight against the specific UOM level billed, preventing false-positive invoice disputes.
* **Cost Center and P&L Taxonomy Mapping:**
  - Finance joins ERP sales units with PIM hierarchical category data to report profitability by taxonomy branch.
* **Vendor Rebates and Co-Op Marketing Attribution:**
  - PIM tracks SKU-to-campaign associations, allowing Finance to calculate and claim vendor co-op chargebacks.

### 6. Event Resilience & Choreography Mechanics
* **Consumer Idempotency & Monotonic Version Ordering with TTL Fallback:**
  - Guaranteed-delivery event brokers exhibit at-least-once delivery and out-of-order message arrival across partitions.
  - All domain events carry a monotonically increasing `aggregateVersion` and a unique `eventId`.
  - Downstream consumers enforce strict three-way sequence handling:
    1. `Event.aggregateVersion <= Consumer.lastProcessedVersion`: Silently discard and ACK immediately (duplicate or superseded state).
    2. `Event.aggregateVersion == Consumer.lastProcessedVersion + 1`: Process event immediately and increment consumer sequence pointer.
    3. `Event.aggregateVersion > Consumer.lastProcessedVersion + 1`: Buffer in a short-lived gap-hold queue with a strict TTL (after which the consumer triggers an authoritative state snapshot fetch or routes to dead-letter queue) awaiting missing intermediate events.
* **Transactional Outbox Pattern:**
  - State mutations in `catalog-context`, `compliance-context`, and `dam-context` are committed atomically with outbound event records using the Transactional Outbox pattern (`FOR UPDATE SKIP LOCKED` poller or CDC), ensuring zero phantom events or missed notifications.

---

## Goals vs. Non-Goals

* **In-Scope Goals:**
  - Implement a Guarded Finite State Machine (FSM) enforcing verified remediation loops and distinct terminal sinks (`SKU_DISCONTINUED`, `SKU_ARCHIVED`).
  - Declare and enforce all eight global variant lifecycle states: `SKU_DRAFT`, `SKU_IN_REVIEW`, `SKU_ACTIVE`, `SKU_RESTRICTED`, `SKU_SUSPENDED`, `SKU_RECALLED_LOT_HOLD`, `SKU_DISCONTINUED`, `SKU_ARCHIVED`.
  - Implement the Two-Tier State Model: Global Lifecycle State paired with Scoped Availability Masks (`ChannelStatusMap`, `MarketComplianceMap`).
  - Implement the "Hero Variant" fallback rule when a canonical variant is restricted.
  - Implement the "Last Variant Standing" rule with upward recovery gated by `activeSuspensionReasons.isEmpty()`.
  - Implement bundle/kit restriction and auto-recovery cascades based on component dependencies using suspension reason sets.
  - Distinguish between transient channel infrastructure errors and terminal catalog data errors in syndication feedback.
  - Implement version-pinned channel feedback to eliminate race-condition state thrashing.
  - Build a high-priority QoS Express Lane for compliance recalls and legal takedowns.
  - Define jurisdictional compliance scoping for regional regulatory bans.
  - Implement wall-clock event scheduling for `embargoUntil` and `streetDate`.
  - Implement the Composite Read-Model Readiness Gate (`SKU_ACTIVE`, `PriceInitialized`, `StockInitialized`) with support for zero-price promotional items and derived bundle stock.
  - Enforce consumer idempotency guards and strict three-way monotonic version ordering with TTL fallback across all listeners.
  - Define UOM (Unit of Measure) hierarchy constraints (`EACH`, `INNER_PACK`, `MASTER_CARTON`, `PALLET`) as a prerequisite for `SKU_ACTIVE` state and ERP/WMS ingestion.
  - Implement temporal versioning (`effectiveFrom` / `effectiveTo`) for financial and logistical master data changes.
  - Standardize SKU as the immutable universal key across PIM, ERP, and WMS/OMS.
  - Emit tombstone events on terminal states to evict read models from search/storefront indexes.
* **Out-of-Scope Non-Goals:**
  - Tracking physical Lot/Batch granularity inside PIM (PIM operates strictly at the SKU abstraction; Lot/Expiry tracking remains in WMS/ERP).
  - Real-time warehouse bin counting or order transaction processing inside PIM (owned by WMS/OMS).
  - General ledger, COGS accounting, and accounts payable inside PIM (owned by ERP).
  - Re-enrichment loop controls and AI agent throttling (deferred as saved work for the Python AI agent phase).
  - Immediate implementation of distributed Saga coordinator infrastructure (deferred to event governance phase).

---

## Saved Work / Deferred Scope

* **AI Re-Enrichment Loop Controls:** Throttling, causation tracking (`actorType: AI_AGENT`), and loop guards between Catalog and Python AI service are deferred until the AI agent integration phase. All subsequent architectural documentation must preserve this distinction.

---

## Alternatives Weighed

* **Alternative 1: Absorbing Inventory & Sales into Catalog Aggregate (Rejected)**
  - *Reason for Rejection:* Rejected by author. High-velocity stock updates cause severe database lock contention, invalidate cache snapshots, and violate bounded context separation.
* **Alternative 2: Global Family Demotion on Any Variant Defect (Rejected)**
  - *Reason for Rejection:* Rejected by author. Pulling an entire product family when only one regional or physical variant is non-compliant causes unwarranted commercial disruption.
* **Alternative 3: In-Place Overwriting of Financial Attributes (Rejected)**
  - *Reason for Rejection:* Rejected by author. Destructive attribute overwrites prevent accounting from auditing historical shipping invoices against historical dimensional weight.
* **Alternative 4: Tracking Warehouse Lots inside PIM (Rejected)**
  - *Reason for Rejection:* Rejected by author. Lot tracking requires physical warehouse location knowledge, expiry date monitoring, and bin picking coordinates, which belong exclusively to WMS/OMS.
* **Alternative 5: Modeling State Progression as a DAG (Rejected)**
  - *Reason for Rejection:* Rejected by author. Cyclic remediation loops between active, restricted, and review states invalidate DAG constraints; state transitions must be modeled as a Guarded Finite State Machine.
* **Alternative 6: Single Scalar Suspension Reason (Rejected)**
  - *Reason for Rejection:* Rejected by author. A single scalar field allows automatic child variant recovery to inadvertently overwrite concurrent legal, trademark, or manual compliance holds.

---

## Key Decisions & Staging for Next Steps

1. **Phase 1: Guarded FSM & Two-Tier Availability Engine:** Implement all 8 declared global states, Two-Tier availability masks (`ChannelStatusMap`, `MarketComplianceMap`), "Hero Variant" fallback, "Last Variant Standing" with `activeSuspensionReasons: Set<SuspensionReason>`, and bundle cascade rules.
2. **Phase 2: Continuous Compliance & Recall Ingestion:** Implement listeners in `catalog-context` and `oms-context` for compliance revocation and emergency lot recalls driving global SKU suspensions; establish the QoS Express Lane and operational `LotQuarantineCertifiedEvent` recovery.
3. **Phase 3: Omnichannel Feedback & Temporal Publishing:** Implement transient vs. terminal error classification, version-pinned feedback ingestion in `syndication-context`, temporal wall-clock scheduling for `embargoUntil` / `streetDate`, and index tombstoning.
4. **Phase 4: Financial Master Data & CQRS Read-Model Projection:** Implement event-sourced financial attributes with `effectiveFrom` / `effectiveTo` timestamps, multi-tier UOM dimensional models, and the Composite Read-Model Readiness Gate supporting Skeleton Upserts, zero-price promotional SKUs, and monotonic version ordering with TTL fallback.
