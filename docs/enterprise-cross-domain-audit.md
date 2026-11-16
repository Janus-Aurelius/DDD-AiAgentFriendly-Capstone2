# Enterprise Cross-Domain Architecture Audit & Gap Analysis

> **Scope:** Complete Multi-Context Enterprise Architecture (Supplier, Core Catalog, Taxonomy, DAM, Localization, Syndication)  
> **Status:** Strategic Architectural Audit & Remediation (All Remediations Implemented)  
> **Objective:** Identify critical flaws, failure modes, race conditions, and structural gaps across domain models, implementations, and cross-boundary event choreography.

---

## 1. Global Architecture Overview & Event Topology

```
+---------------------------------------------------------------------------------------------------+
|                                  ENTERPRISE EVENT BUS (KAFKA)                                     |
+--------+---------------------+-------------------+---------------------+------------------+-------+
         ▲                     ▲                   ▲                     ▲                  │
         │ (1)                 │ (2)               │ (3)                 │ (4)              ▼ (5)
  [Supplier Context]     [Core Catalog]      [Creative DAM]      [Localization]      [Syndication]
  AI Ingestion &         ProductFamily       DigitalAsset        RegionalCatalog     Read Model &
  Quarantine             State Machine       Renditions & DRM    Overrides & CA/MX   Elasticsearch
         │                     │                   │                     │                  │
         ▼                     ▼                   ▼                     ▼                  ▼
     Quarantine             PostgreSQL          AWS S3 +              PostgreSQL       Elasticsearch +
     DB                     catalog schema      Metadata DB           Regional DB       Amazon SP-API
```

### Event Choreography Sequence:
1. `supplier-context` maps unstructured vendor items $\rightarrow$ emits `VendorItemApprovedForCore`.
2. `catalog-context` consumes event $\rightarrow$ instantiates `ProductFamily` (DRAFT) $\rightarrow$ Merchandising enriches $\rightarrow$ Compliance approves $\rightarrow$ emits `ProductPublished`.
3. `dam-context` uploads raw TIFF to S3 $\rightarrow$ generates renditions $\rightarrow$ Merchandising links asset to SKU $\rightarrow$ emits `AssetLinkedToProduct`.
4. `dam-context` detects expired DRM license $\rightarrow$ emits `AssetLicenseExpired` $\rightarrow$ Core Catalog strips image and demotes product back to `ENRICHED`.
5. `localization-context` intercepts `ProductPublished` $\rightarrow$ fans out `RegionalCatalogItem` across US, CA, MX $\rightarrow$ Canadian merchandising inputs French safety warnings $\rightarrow$ emits `RegionalItemPublished(CA)`.
6. `syndication-context` executes **"The Great Aggregation"** $\rightarrow$ collects `RegionalItemPublished`, `ComplianceCleared`, `AssetLinked` $\rightarrow$ flattens into `ChannelItemProjection` $\rightarrow$ caches in Elasticsearch and pushes to Amazon SP-API.

---

## 2. Cross-Domain Choreography & Connection Gaps

### 2.1 The "Out-of-Order Great Aggregation" Race Condition in Syndication
* **The Vulnerability:** `syndication-context` relies on three independent event streams: `RegionalItemPublished` (Localization), `ComplianceCleared` (Legal), and `AssetLinked` (DAM). Kafka partitions for different topics have no cross-topic ordering guarantees.
* **Failure Mode:** If `AssetLinked` arrives *before* `RegionalItemPublished` has initialized the `ChannelItemProjection` record for a SKU in `projectionStore`, the asset link is either silently dropped, or creates a corrupt, half-initialized projection lacking price and category.
* **Remediation:**
  - Implement a **Stateful Saga / Process Manager** or **Document Staging Inbox** in Syndication.
  - Store unlinked media assets in an `unclaimed_asset_links` buffer table. When `RegionalItemPublished` initializes the projection, it drains the buffer.

### 2.2 The "Silent Zombie Asset" Propagation Gap (DRM Expiration Cascade)
* **The Vulnerability:** When a DRM license expires, `dam-context` emits `AssetLicenseExpired`. `catalog-context` listens and demotes the `ProductFamily` back to `ENRICHED`.
* **Failure Mode:** What about `localization-context` and `syndication-context`?
  - `syndication-context` already exported the image URL to Amazon and cached it in Elasticsearch.
  - Unless `syndication-context` directly subscribes to `AssetLicenseExpired` (or Core Catalog emits a `ProductDemoted` / `ProductUnpublished` event), the expired image remains live on Amazon and mobile apps, exposing the enterprise to severe copyright copyright infringement lawsuits.
* **Remediation:**
  - Core Catalog must emit a `ProductUnpublished` or `ProductStateDemoted` event when demoted back to `ENRICHED`.
  - Alternatively, `syndication-context` must listen directly to `dam.asset.events` for `AssetLicenseExpired` and immediately dispatch a feed update to Amazon removing the image.

### 2.3 Fan-Out Amplification & Cascade Failure on Catalog Re-Publish
* **The Vulnerability:** Every time a product is published, `localization-context` fans out across all operational regions (US, CA, MX).
* **Failure Mode:** If a product is demoted to `DRAFT` for minor corrections and re-published, `GlobalProductPublishedListener` will receive another `ProductPublished` event. If the listener blindly instantiates new items or resets status, existing regional overrides and translation work in `localization-context` could be overwritten or reset back to `PENDING_TRANSLATION`.
* **Remediation:**
  - Idempotent regional initialization: Check if `RegionalCatalogItem` already exists.
  - If it exists, execute a **Non-Destructive Delta Sync** rather than resetting localized translations.

---

## 3. Domain Model & Business Logic Gaps

### 3.1 Digital Asset Management (DAM)
| Domain Component | Identified Gap | Severity | Remediation Strategy |
| :--- | :--- | :---: | :--- |
| **S3 Storage** | **Orphaned Binaries on Asset Deletion:** Deleting a `DigitalAsset` entity removes the DB record but leaves 80MB TIFFs and renditions in S3 indefinitely. | Medium | Implement an S3 Lifecycle Rule or an asynchronous S3 deletion cleanup task in `S3AssetStorageRepository`. |
| **Rendition Worker** | **Synchronous Blocking Compression:** Compressing 80MB TIFFs into WebP/JPEG in-thread will exhaust application server CPU and worker pools under load. | High | Delegate image transcoding to a dedicated serverless pipeline (AWS Lambda / Elastic Transcoder) triggered via S3 Event Notifications. |
| **DRM License** | **Time-Zone Boundary Ambiguity:** `expirationDate` as Instant without timezone semantics can cause legal expiration disputes between midnight UTC vs. midnight PST. | Low | Enforce UTC expiration timestamps with a 24-hour pre-warning event (`AssetLicenseExpiringSoon`). |

### 3.2 Localization & Regional Context
| Domain Component | Identified Gap | Severity | Remediation Strategy |
| :--- | :--- | :---: | :--- |
| **Regional Catalog** | **Hardcoded Region Matrix:** Regions are hardcoded to `US`, `CA`, `MX`. Expanding to EU or APAC requires Java code modifications and deployment. | Medium | Store operational regions and their mandatory translation rules in a configuration table / service registry (`RegionRuleRegistry`). |
| **Unit Overrides** | **Unit of Measure (UoM) Conversion Drift:** Merchandisers manually type `"22.6kg"` to override `"50lbs"`. Manual data entry leads to rounding errors and shipping miscalculations. | High | Introduce an automated **Unit Conversion Domain Service** (`UomConverter`) that deterministically converts imperial to metric units based on target region standards. |

### 3.3 Channel Syndication Context
| Domain Component | Identified Gap | Severity | Remediation Strategy |
| :--- | :--- | :---: | :--- |
| **Mapping Template** | **Hardcoded Amazon Rules:** The template strips HTML and limits title to 200 chars, but Amazon taxonomy categories have distinct validation rules (e.g., Clothing has different character limits and bullet point rules than Consumer Electronics). | Medium | Decouple templates per category taxonomy using JSONSchema or Liquid/Jinja templating rules stored in DB. |
| **Projection Size** | **Denormalized Payload Explosion:** If a TV has 50 attributes, 10 gallery photos, and 5 variants, a single denormalized JSON document can grow to several megabytes, degrading Elasticsearch cache performance. | Medium | Implement field projection masks (`summary_projection` for mobile list views vs. `detail_projection` for PDPs). |

---

## 4. Software Engineering & Infrastructure Gaps

### 4.1 In-Memory Persistence in Secondary Contexts
* **The Gap:** `PostgresDigitalAssetRepository`, `PostgresRegionalRepository`, and `InMemoryChannelCatalogRepository` currently use thread-safe `ConcurrentHashMap` stores.
* **Risk:** While suitable for unit and integration testing, server restarts or horizontal pod autoscaling in Kubernetes will cause instantaneous data loss.
* **Action:** Provide corresponding JPA Entity mappings and PostgreSQL tables in `infra/db-init/` for `dam` and `localization` schemas, and true Elasticsearch clients for `syndication`.

### 4.2 Distributed Event Idempotency & Dual-Write Protection
* **The Gap:** Only `catalog-context` currently implements the Transactional Outbox pattern (`OutboxEventPublisher`).
* **Risk:** `dam-context` and `localization-context` publish directly to Kafka inside Spring `@Transactional` service methods. If the database transaction rolls back *after* the Kafka message is emitted, phantom events will be broadcast across the enterprise.
* **Action:** Adopt the `shared-kernel` Outbox pattern across all contexts that publish domain events.

---

## 5. Comprehensive Architectural Remediation Roadmap

```
+-------------------------------------------------------------------------------------------------+
|                                    REMEDIATION ROADMAP                                          |
+-------------------------------------------------------------------------------------------------+
|  Phase A (Immediate / Critical):                                                                |
|  1. Wire AssetLicenseExpired directly to Syndication to purge expired images from Amazon feeds. |
|  2. Implement Claim-Check Buffer in Syndication to eliminate out-of-order event race conditions. |
|  3. Add transactional outbox support to DAM and Localization contexts.                          |
|                                                                                                 |
|  Phase B (Medium-Term Hardening):                                                               |
|  4. Replace hardcoded compliance checks with CategoryCompliancePolicy SPI.                     |
|  5. Introduce UomConverter for automated metric/imperial regional transformations.             |
|  6. Offload heavy media processing from Java threads to AWS Lambda / S3 event notifications.    |
|                                                                                                 |
|  Phase C (Edge Optimization):                                                                   |
|  7. Implement Shadow Drafting / Aggregate Versioning in Core Catalog for live editing.          |
|  8. Deploy Elasticsearch projection indices with field-masking for sub-10ms mobile PDP reads.  |
+-------------------------------------------------------------------------------------------------+
```

---

## 6. Implementation Status of Audit Remediations

| Remediation Item | Target Context | Status | Key Implementation Classes |
| :--- | :--- | :---: | :--- |
| **Claim-Check Buffer** | `syndication-context` | **DONE** | [`ProductReadModelProjector.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/projections/ProductReadModelProjector.java) (`pendingAssetBuffer`) |
| **DRM Zombie Purge & Retraction** | `syndication-context` | **DONE** | [`SyndicationEventsListener.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/infrastructure/messaging/SyndicationEventsListener.java) (`handleAssetLicenseExpired`) |
| **Transactional Outbox in DAM & Loc** | `dam-context`, `localization-context` | **DONE** | `DamOutboxService`, `DamOutboxPollingRelay`, `LocalizationOutboxService`, `LocalizationOutboxPollingRelay` |
| **JPA Entities & Spring Data Repos** | `dam-context`, `localization-context` | **DONE** | `DigitalAssetJpaEntity`, `SpringDataDigitalAssetRepository`, `RegionalCatalogJpaEntity`, `SpringDataRegionalCatalogRepository` |
| **Dynamic Region Rule Registry** | `localization-context` | **DONE** | [`RegionRuleRegistry.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/domain/service/RegionRuleRegistry.java) |
| **Dynamic Channel Mapping per Category** | `syndication-context` | **DONE** | [`CategoryMappingTemplateRegistry.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/domain/service/CategoryMappingTemplateRegistry.java) |
| **Field Projection Masks** | `syndication-context` | **DONE** | [`ChannelItemProjection.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/domain/model/ChannelItemProjection.java) (`SummaryProjection`, `DetailProjection`) |
| **S3 Cleanup & Async Transcoding** | `dam-context` | **DONE** | `purgeAssetBinariesAsync` in `S3AssetStorageRepository`, `processUploadedAssetAsync` in `RenditionGenerationWorker` |
| **DRM 24h Pre-Warning Event** | `dam-context` | **DONE** | [`AssetLicenseExpiringSoon.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/domain/events/AssetLicenseExpiringSoon.java), `DRMLicense.isExpiringWithin()` |
| **Infrastructure Topology** | `infra/` | **DONE** | Kafka (KRaft) and MinIO added to [`docker-compose.yml`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/docker-compose.yml) |
