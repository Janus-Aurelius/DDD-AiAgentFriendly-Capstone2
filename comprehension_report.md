# Comprehension Test Report (Stage 6)

**Target Document:** [pim-lifecycle-state-machine-and-resilience.md](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/designs/pim-lifecycle-state-machine-and-resilience.md)  
**Target Scope:** FeatureRange (lines 1–591) and DetailedRange (lines 592–832)  
**Auditor:** Comprehension Goldfish (`egm-comprehension`)  
**Evaluation Standard:** Self-Containment & Naive Reader Audit  

---

### What this proposes

This document proposes an enterprise Product Information Management (PIM) platform structured as a modular monolith in a dual-role deployment (`pim-api` for interactive REST operations and `pim-worker` for background asynchronous processing). The design replaces a brittle linear progression with an 8-state variant lifecycle state machine and a 4-state family lifecycle, incorporating two-tier availability masks (global viability vs. localized channel/market restrictions) and shadow working revisions for non-disruptive remediation. It also integrates an event-driven edge read model (CQRS over Elasticsearch and Redis) that gates product readiness across PIM, ERP, and OMS, along with automated resilience patterns including instant Redis Set bundle blacklisting, chunked database fan-out cascades, bi-temporal financial history, and a 3-zone recall protocol.

### How the existing system works, per this document

The existing system operates on a simplistic, linear happy-path lifecycle (`DRAFT -> ENRICHED -> APPROVED -> PUBLISHED`) that lacks robust remediation loops, causing variants with compliance failures or missing media assets to silently fail or trigger blunt demotions of entire product families back to `ENRICHED`. Its catalog codebase lacks domain events and consumer listeners for expected asynchronous events (`ProductCreated`, `ProductEnriched`, `ComplianceEvaluated`), leaving products stranded in unapproved states. Furthermore, syndication treats external marketplace feeds as fire-and-forget without handling asynchronous rejections, component suspensions trigger unthrottled cascade storms across virtual bundles, and emergency recalls either fail to stop in-flight warehouse orders or indiscriminately halt sales nationwide.

---

### Findings

#### Finding C-1: Architectural Scope Contradiction Regarding OMS Order Interception (`oms-adapter`)
* **Severity:** 🟡 Major
* **Affected Scope:** Non-Goal 3 (lines 45–46), Guardrail 2 (lines 193–207), Section 6 (lines 339–387), and Slice 2 (lines 656–661)
* **Discrepancy Description & Rationale:** Non-Goal 3 explicitly states: *"Transactional Order & Stock Processing in PIM: Real-time stock reservation, shopping cart checkout, and warehouse fulfillment are owned by WMS/OMS."* Furthermore, Section 1 and Section 6 model OMS as an external enterprise service consuming recall events via Kafka, and Guardrail 2 does not list any `oms-adapter` in the Maven/Gradle module hierarchy. However, Slice 2 directs the creation of `backend-java/oms-adapter/src/main/java/com/retail/pim/oms/adapter/services/RecallOrderInterceptionService.java` inside the PIM repository, which implements transactional OMS responsibilities: evaluating package manifests, sending warehouse pick halt commands, voiding payment authorizations, invoking carrier intercept APIs, and executing ledger refunds. If `oms-adapter` is merely a test harness/mock for Testcontainers integration testing, the document must state this explicitly. If it is production code, it directly violates Non-Goal 3 and Guardrail 2.

#### Finding C-2: Dead-End State in Variant FSM for Recalled SKUs
* **Severity:** 🟡 Major
* **Affected Scope:** Section 3: Variant Lifecycle State Model (lines 249–256)
* **Discrepancy Description & Rationale:** In the 8-state state diagram, `SKU_RECALLED_LOT_HOLD` has only a single exit transition: `--> SKU_ACTIVE: LotQuarantineCertifiedEvent (WMS Bypass)`. There is no transition from `SKU_RECALLED_LOT_HOLD` to `SKU_DISCONTINUED`, `SKU_IN_REVIEW`, or `SKU_ARCHIVED`. If a recalled lot cannot be isolated in the warehouse, or if a defect is catastrophic and requires permanently discontinuing or re-authoring the SKU, the FSM provides no valid transition path, trapping the SKU in `SKU_RECALLED_LOT_HOLD` indefinitely.

#### Finding C-3: Domain Aggregate Disconnection During Direct SQL Batch Cascades
* **Severity:** 🟡 Major
* **Affected Scope:** Section 4 (lines 271–280) and Slice 3 (lines 673–695)
* **Discrepancy Description & Rationale:** When a shared component SKU is suspended, `BatchComponentCoordinator` updates thousands of bundle variants directly via raw SQL (`UPDATE catalog.product_variant SET active_suspension_reasons = ... WHERE id = ANY(:chunkOf250BundleVariantIds)`). However, Section 3 and Slice 6 specify that whenever a variant becomes inactive, the parent `ProductFamily` aggregate root must evaluate `activeVariantCount` and suspend the family if active variants drop to 0 ("Last Variant Standing"). Because the batch coordinator executes bulk SQL updates that bypass JPA domain entity lifecycle events, the parent `ProductFamily` records for these bundles will never detect the variant suspensions and will remain in `FAMILY_PUBLISHED`, violating family lifecycle invariants.

#### Finding C-4: Conflation of Physical Warehouse Stock with Master Data Variant Lifecycle
* **Severity:** 🟡 Major
* **Affected Scope:** CUJ 6 (line 108)
* **Discrepancy Description & Rationale:** CUJ 6 states: *"When: SKU-SM runs out of stock or loses its primary image, transitioning out of active status (`activeVariantCount: 1 -> 0`)."* However, Non-Goal 2 and Alternative B explicitly establish that physical warehouse stock levels are decoupled from PIM master data and owned by OMS/WMS to avoid massive lock contention on catalog aggregates. Stating that running out of stock causes a variant to transition out of `SKU_ACTIVE` in PIM conflates OMS transactional inventory depletion with PIM master data lifecycle management, contradicting Non-Goal 2 and Alternative B.

#### Finding C-5: Undefined Domain Acronym "ATP"
* **Severity:** 🟢 Nit
* **Affected Scope:** Section 1 (line 161)
* **Discrepancy Description & Rationale:** Line 161 references `OMS (Orders, ATP & 3-Zone PONR)`. The acronym `ATP` (Available to Promise) is never expanded, defined inline, or explained anywhere in the document.

#### Finding C-6: Ambiguous Execution Mechanism for `EmbargoLiftedEvent`
* **Severity:** 🟢 Nit
* **Affected Scope:** CUJ 5 (lines 104–105), Section 5 (lines 332–336), and Slice 5 (lines 726–728)
* **Discrepancy Description & Rationale:** Section 5 states that embargoes are evaluated in search queries via a timestamp predicate (`now() >= embargoUntil`), but then states that `pim-worker` emits `EmbargoLiftedEvent` at timestamp $T$ to unmask listings without cluster re-indexing. In Slice 5, `TemporalEmbargoScheduler` publishes this event, but no listener or projector handler is defined to consume it. If the query predicate already dynamically gates searchability based on wall-clock time, the event is redundant; if the event mutates an index document status, the update mechanism is omitted.

#### Finding C-7: Missing Public Facade Declarations in Implementation Plan
* **Severity:** 🟢 Nit
* **Affected Scope:** Slice 1 (line 637) and Slice 3 (line 695)
* **Discrepancy Description & Rationale:** Slice 1 states that `VariantRevisionService` calls `ComplianceEvaluationFacade.validateVariantRevision(...)`, and Slice 3 states that `BatchComponentCoordinator` calls `SyndicationModuleFacade.bulkMaskBundles(...)`. However, neither `ComplianceEvaluationFacade` nor `SyndicationModuleFacade` is declared in the target files list of Slice 1, Slice 3, or Slice 0.

#### Finding C-8: Undefined Taxonomy Closure Table Schema in Persistence DDL
* **Severity:** 🟢 Nit
* **Affected Scope:** Section 1 (line 145) and Section 8: Persistence DDL (lines 459–529)
* **Discrepancy Description & Rationale:** The high-level architecture diagram labels `TaxonomyMod` as using a "Closure Table Hierarchy", and Section 8 executes `CREATE SCHEMA IF NOT EXISTS taxonomy;`. However, the appendix contains no DDL table definitions for the taxonomy closure table, leaving this architectural concept without schema details.
