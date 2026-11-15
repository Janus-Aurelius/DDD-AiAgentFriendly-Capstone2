# Core Catalog Architecture & Domain Review

> **Document:** Core Catalog State Machine & Handoff Design Review  
> **Status:** Critical Review & Remediation Roadmap  
> **Context:** Context 1 (Core PIM Domain)  

---

## Executive Summary

The implementation of the **Core Catalog Context** successfully establishes:
1. **Event-Driven Boundary Handoff**: Decoupled consumption of `VendorItemApprovedForCore` from the Supplier Context via Kafka and translation to `CreateDraftProductCommand`.
2. **Pure DDD Aggregate Root**: `ProductFamily` encapsulates the complete lifecycle state machine (`DRAFT -> ENRICHED -> APPROVED -> PUBLISHED`) with zero framework or database annotations.
3. **Transactional Outbox Pattern**: Atomic persistence of domain events (`ProductPublished`) within the primary PostgreSQL transaction to avoid dual-write inconsistencies.
4. **PostgreSQL JSONB & Closure Table Integration**: Dynamic attribute flexibility alongside relational integrity.

However, an exhaustive audit from both **Business Merchandising** and **Enterprise Software Engineering** viewpoints reveals critical edge cases and architectural gaps that should be reviewed and addressed before production rollout.

---

## 1. Business Domain Gaps & Vulnerabilities

### 1.1 The "Live Edit" Dilemma (Lack of Shadow Drafting / Versioning)
* **The Problem:** In production retail PIMs (Akeneo, Salsify, Pimcore), once a product reaches `PUBLISHED`, merchandising teams will continuously revise marketing copy, add secondary photos, or update MSRP.
* **The Gap:** The current state machine is linear. If a product transitions back to `DRAFT` via `returnToDraft()`, it could inadvertently unpublish the item from live e-commerce channels (Amazon, Shopify), resulting in major revenue disruption.
* **Recommendation:** Introduce **Shadow Branching / Aggregate Versioning**:
  - The aggregate retains a `published_version` (serving public channels) and a `draft_version` (under active merchandising).
  - When the draft reaches `PUBLISHED`, it atomically swaps into the active published snapshot.

### 1.2 Hardcoded Regulatory Invariants Violating the Open-Closed Principle
* **The Problem:** In `ProductFamily.approve()`, compliance validation is hardcoded:
  ```java
  if (isElectronics()) {
      AttributeValue energyRating = attributes.get("energy_rating");
      // ...
  }
  ```
* **The Gap:** Hardcoding category-specific rules directly inside the aggregate root creates high coupling. Retail catalogs contain thousands of diverse categories:
  - Apparel requires fabric composition and wash-care labels.
  - Food/Grocery requires allergen warnings and expiry date formatting.
  - Chemicals require safety data sheets (SDS) and Hazmat flags.
* **Recommendation:** Decouple compliance checks using a **Specification Pattern / Policy Engine**:
  ```java
  public interface CategoryCompliancePolicy {
      void validateCompliance(ProductFamily product);
  }
  ```
  The application service can inject category-specific compliance policies based on the `categoryId` before calling `productFamily.approve()`.

### 1.3 Variant Lifecycle Monolith vs. Granular SKU Availability
* **The Problem:** The aggregate rule mandates: `variants.size() > 0 AND all variants have weight and dimensions` and `every variant must have at least one primary image`.
* **The Gap:** If 9 out of 10 TV sizes are fully enriched and ready to sell, but the 85-inch model is missing its box dimensions, the **entire product family is blocked from publishing**.
* **Recommendation:** Support **Partial Publication / Variant Availability Flags**:
  - Allow publishing the family as long as at least one variant is approved and publishable.
  - Non-compliant variants remain in `SKU_INCOMPLETE` status and are excluded from the e-commerce syndication payload.

### 1.4 Monolithic Publishing vs. Multi-Channel & Multi-Locale Reality
* **The Problem:** The state machine transitions globally to `PUBLISHED` once localized descriptions exist.
* **The Gap:** Retailers sell across multiple channels (Web, Mobile App, Amazon, B2B Wholesale) and multiple locales (`en-US`, `es-MX`, `fr-CA`). A product may be fully approved for the US Web storefront, but still pending French translation for Canada.
* **Recommendation:** Move from a single boolean status to a **Channel & Locale Publication Matrix** (`Map<ChannelId, PublicationStatus>`).

### 1.5 Supplier Re-Import & Delta Merging
* **The Problem:** `VendorItemApprovedListener` only handles `CreateDraftProductCommand`.
* **The Gap:** In supply chains, vendors frequently update specifications, prices, or packaging on items that already exist in the Core Catalog.
* **Recommendation:** Add a `MergeSupplierUpdateCommand` and aggregate method `mergeSupplierDelta(...)` that applies vendor updates without overwriting internal merchandising copy.

---

## 2. Software Engineering & Technical Architecture Gaps

### 2.1 Outbox Poller / Relay Worker Missing
* **The Problem:** `OutboxEventPublisher` writes domain events to `catalog.outbox_event` with `status = 'PENDING'`.
* **The Gap:** Writing to the outbox table completes step 1 of the Transactional Outbox pattern, but without an active Polling Relay (Spring `@Scheduled` batch poller) or Debezium CDC (Change Data Capture) worker, events will accumulate in PostgreSQL and never actually reach Kafka.
* **Recommendation:** Implement an Outbox Relay service that queries `SELECT * FROM catalog.outbox_event WHERE status = 'PENDING' LIMIT 100 FOR UPDATE SKIP LOCKED`, pushes to Kafka, and marks status as `PROCESSED`.

### 2.2 Kafka Consumer Idempotency & Re-Delivery
* **The Problem:** Kafka provides **at-least-once** delivery. Network hiccups or partition rebalances will re-deliver `VendorItemApprovedForCore`.
* **The Gap:** If `VendorItemApprovedListener` receives the same event twice, it checks `existsByCode()`. If the code was generated dynamically or contains different casing, duplicate records or unhandled exceptions may occur.
* **Recommendation:** Maintain an explicit **Idempotent Consumer Table** (`catalog.consumed_messages`) keyed on `event_id` and `consumer_group` to deduplicate before executing application logic.

### 2.3 Optimistic Concurrency Bottlenecks Across Business Roles
* **The Problem:** The `ProductFamily` aggregate combines merchandising data (`localizedDescriptions`), logistics data (`variants.dimensions`), and marketing media (`digitalAssets`).
* **The Gap:** If a Merchandiser is writing marketing descriptions while a Supply Chain coordinator is entering box dimensions, both commit against the same `@Version` column. The second commit will throw an `OptimisticLockingFailureException`.
* **Recommendation:** Implement automated retry with exponential backoff on optimistic lock exceptions in `ProductLifecycleService`, or partition update commands to minimize contention.

---

## 3. Remediation Roadmap

| Priority | Area | Action Item |
| :---: | :--- | :--- |
| **High** | Infrastructure | Implement the Outbox Poller relay worker to forward pending outbox rows to Kafka. |
| **High** | Messaging | Add an `idempotent_consumer` table to ensure safe Kafka re-delivery handling. |
| **Medium** | Domain | Refactor `isElectronics()` compliance check into an extensible `ComplianceRulePolicy`. |
| **Medium** | Business | Introduce SKU-level publication status so missing variant assets do not block entire families. |
| **Low** | Architecture | Implement Shadow Versioning for live editing published products without taking down active listings. |
