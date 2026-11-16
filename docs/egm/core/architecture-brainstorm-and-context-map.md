# Architecture Brainstorm & Multi-Context Interaction Map

> **System:** AI-Agent-Friendly Enterprise Product Information Management (PIM) Platform  
> **Architecture:** Domain-Driven Design (DDD), Hexagonal Architecture (Ports & Adapters), Event-Driven Choreography, CQRS Edge  
> **Status:** Working Reference Document for System Evolution & Brainstorming

---

## 1. Executive Context & Scope

This document details the multi-context system boundaries, interaction contracts, persistence topologies, messaging flows, and sidecars across the platform. Use this document as a whiteboard/brainstorming guide to trace data paths and evaluate architectural additions.

### 1.1 Context Boundaries & Responsibilities

| Context | Architectural Responsibility | Key Patterns & Mechanisms |
| :--- | :--- | :--- |
| **`shared-kernel`** | Common domain abstractions & contracts | `AggregateRoot`, `Entity`, `DomainEvent`, `DomainException` |
| **`supplier-context`** | External vendor payload ingestion & quarantine | File parsing, data normalization, vendor approval staging |
| **`catalog-context`** | Core PIM domain model & lifecycle state machine | Shadow versioning (`publishedSnapshot`), transactional outbox poller, idempotent consumer guard, category compliance SPI |
| **`taxonomy-context`** | Product classification & category hierarchy | PostgreSQL Closure Table ($O(1)$ subtrees, cycle-safe moves), rule scope matching |
| **`dam-context`** | Media assets, image transformations, and DRM legal safety | AWS S3 / MinIO binary storage, async transcoding workers, DRM pre-warning timers, transactional outbox |
| **`compliance-context`** | Dynamic regulatory & merchandising scoring | Spring Expression Language (SpEL) dynamic rule engine, hard gate vs soft gate scoring, version-locked compliance reports |
| **`localization-context`** | Global expansion, currency & regional overrides | Dynamic `RegionRuleRegistry`, automated `UomConverter` (metric/imperial), regional fan-out, transactional outbox |
| **`syndication-context`** | CQRS edge read model & external channels | "The Great Aggregation" projector, Claim-Check buffer, Elasticsearch projections (Summary vs Detail masks), Amazon SP-API client |
| **`ai-python`** | Autonomous attribute extraction & enrichment | FastAPI microservice, LangChain/LlamaIndex agents, confidence score threshold ($\ge 0.85$ auto-enrich vs $< 0.85$ HITL review) |

---

## 2. Global System Interaction & Sidecar Topology

The following diagram maps how events move across bounded contexts, how each context coordinates transactional persistence with outbox pollers/sidecars, and how edge adapters feed downstream systems.

```mermaid
flowchart TD
    subgraph SUPPLIER_BC["Supplier Context (Quarantine & Feeds)"]
        RawFeed["Supplier Feeds (CSV/JSON/API)"]
        QuarantineDB[("Quarantine DB")]
        SupplierMapper["Vendor Ingestion Service"]
        SupplierOutbox["Supplier Outbox Table"]
        SupplierRelay["Outbox Relay / Producer"]
        
        RawFeed --> QuarantineDB
        QuarantineDB --> SupplierMapper
        SupplierMapper --> SupplierOutbox
        SupplierOutbox --> SupplierRelay
    end

    subgraph KAFKA_BUS["Enterprise Kafka Event Bus (KRaft)"]
        TopicSupplier["supplier.vendor-item.approved"]
        TopicCatalog["catalog.product.events"]
        TopicDam["dam.asset.events"]
        TopicLocalization["localization.regional.events"]
        TopicCompliance["compliance.report.events"]
        DLQ["Dead Letter Queue (DLQ)"]
    end

    subgraph CATALOG_BC["Core Catalog Context"]
        VendorListener["VendorItemApprovedListener"]
        CatalogGuard["Idempotent Consumer Guard<br/>(catalog.consumed_messages)"]
        CatalogAppService["ProductLifecycleService<br/>(Retry on Optimistic Lock)"]
        ProductFamilyAR["ProductFamily Aggregate Root<br/>(Shadow Versioning / State Machine)"]
        CatalogDB[("PostgreSQL: catalog schema<br/>product_family & product_variant")]
        CatalogOutbox[("catalog.outbox_event")]
        CatalogRelay["OutboxPollingRelay<br/>(FOR UPDATE SKIP LOCKED)"]
        AssetDemoteListener["AssetLicenseExpiredListener"]

        VendorListener --> CatalogGuard
        CatalogGuard --> CatalogAppService
        AssetDemoteListener --> CatalogAppService
        CatalogAppService --> ProductFamilyAR
        ProductFamilyAR --> CatalogDB
        ProductFamilyAR --> CatalogOutbox
        CatalogOutbox --> CatalogRelay
    end

    subgraph AI_AGENT["AI Python Context (FastAPI)"]
        AiConsumer["Catalog Event Consumer"]
        LLMExtractor["LangChain / LlamaIndex Worker"]
        ConfidenceGate{"Confidence >= 0.85?"}
        HITLQueue[("HITL Review Queue")]
        CatalogRestPort["Catalog REST API Adapter<br/>(POST /api/v1/products/{id}/enrich)"]

        AiConsumer --> LLMExtractor
        LLMExtractor --> ConfidenceGate
        ConfidenceGate -- "Yes (Score >= 0.85)" --> CatalogRestPort
        ConfidenceGate -- "No (Score < 0.85)" --> HITLQueue
    end

    subgraph TAXONOMY_BC["Taxonomy Context"]
        ClosureRepo["ClosureTableRepository<br/>(PostgreSQL Closure Table)"]
        TaxonomyDB[("PostgreSQL: taxonomy schema<br/>category_node & category_closure")]
        ComplianceRuleStore[("taxonomy.compliance_rule")]
        TaxonomyAPI["Taxonomy REST API"]

        ClosureRepo --> TaxonomyDB
        TaxonomyAPI --> ClosureRepo
    end

    subgraph DAM_BC["Digital Asset Management (DAM) Context"]
        AssetUpload["Asset Ingestion REST API"]
        MinioS3[("AWS S3 / MinIO<br/>Raw Binaries")]
        DamWorker["RenditionGenerationWorker<br/>(Async Worker)"]
        DamAggregate["DigitalAsset Aggregate Root<br/>(DRMLicense, Renditions)"]
        DamDB[("PostgreSQL: dam.digital_asset")]
        DamOutbox[("dam.outbox_event")]
        DamRelay["DamOutboxPollingRelay<br/>(FOR UPDATE SKIP LOCKED)"]
        S3Cleanup["S3 Lifecycle Async Purge"]

        AssetUpload --> MinioS3
        AssetUpload --> DamWorker
        DamWorker --> DamAggregate
        DamAggregate --> DamDB
        DamAggregate --> DamOutbox
        DamAggregate --> S3Cleanup
        DamOutbox --> DamRelay
    end

    subgraph COMPLIANCE_BC["Compliance Context"]
        ComplianceListener["Product State Event Listener"]
        RuleEngine["SpEL Dynamic Policy Evaluator"]
        ComplianceReportStore[("compliance.compliance_report")]
        ComplianceOutbox[("compliance.outbox_event")]
        ComplianceRelay["Compliance Outbox Relay"]

        ComplianceListener --> RuleEngine
        RuleEngine --> ComplianceReportStore
        RuleEngine --> ComplianceOutbox
        ComplianceOutbox --> ComplianceRelay
    end

    subgraph LOCALIZATION_BC["Localization Context"]
        ProductPublishedListener["GlobalProductPublishedListener"]
        RegionRegistry["Dynamic RegionRuleRegistry"]
        UomDomainService["UomConverter (Metric/Imperial)"]
        RegionalAggregate["RegionalCatalogItem Aggregate Root"]
        LocDB[("PostgreSQL: localization schema<br/>regional_catalog_item")]
        LocOutbox[("localization.outbox_event")]
        LocRelay["LocalizationOutboxPollingRelay<br/>(FOR UPDATE SKIP LOCKED)"]

        ProductPublishedListener --> RegionRegistry
        RegionRegistry --> UomDomainService
        UomDomainService --> RegionalAggregate
        RegionalAggregate --> LocDB
        RegionalAggregate --> LocOutbox
        LocOutbox --> LocRelay
    end

    subgraph SYNDICATION_BC["Syndication Context (CQRS Edge)"]
        SyndicationListener["SyndicationEventsListener"]
        ClaimCheckBuffer[("Claim-Check Buffer<br/>(Unlinked Assets Memory/Store)")]
        Projector["ProductReadModelProjector<br/>(The Great Aggregation)"]
        CatTemplateReg["CategoryMappingTemplateRegistry"]
        ESRepo[("Elasticsearch Index<br/>(Summary & Detail Masks)")]
        AmazonAdapter["AmazonMarketplaceApiClient<br/>(Amazon SP-API Feeds)"]

        SyndicationListener --> ClaimCheckBuffer
        SyndicationListener --> Projector
        ClaimCheckBuffer --> Projector
        Projector --> CatTemplateReg
        Projector --> ESRepo
        CatTemplateReg --> AmazonAdapter
    end

    %% Kafka Routing & Sidecars
    SupplierRelay --> TopicSupplier
    TopicSupplier --> VendorListener
    CatalogRelay --> TopicCatalog
    TopicCatalog --> AiConsumer
    TopicCatalog --> ProductPublishedListener
    TopicCatalog --> ComplianceListener
    DamRelay --> TopicDam
    TopicDam --> AssetDemoteListener
    TopicDam --> SyndicationListener
    ComplianceRelay --> TopicCompliance
    TopicCompliance --> SyndicationListener
    LocRelay --> TopicLocalization
    TopicLocalization --> SyndicationListener
    CatalogRestPort -.->|REST HTTP / OpenAPI| CatalogAppService
    RuleEngine -.->|Sync Read Hierarchy| TaxonomyAPI
```

---

## 3. End-to-End Choreography Sequence

```mermaid
sequenceDiagram
    autonumber
    participant SUP as Supplier Context
    participant KAFKA as Kafka Message Broker
    participant CAT as Core Catalog Context
    participant AI as Python AI Worker
    participant DAM as DAM Context
    participant COMP as Compliance Context
    participant LOC as Localization Context
    participant SYN as Syndication Context (CQRS)
    participant EXT as Amazon SP-API / ES

    Note over SUP,CAT: Phase 1: Supplier Ingestion to Core Draft
    SUP->>KAFKA: Publish VendorItemApprovedForCore (via Supplier Outbox)
    KAFKA->>CAT: Deliver to VendorItemApprovedListener
    CAT->>CAT: IdempotentConsumerGuard.tryConsume(eventId)
    CAT->>CAT: ProductLifecycleService.mergeSupplierUpdate (Draft Created)
    CAT->>KAFKA: Publish ProductCreated (via Catalog Outbox Relay)

    Note over CAT,AI: Phase 2: AI Automated Enrichment
    KAFKA->>AI: Deliver ProductCreated
    AI->>AI: Extract specs & score confidence
    alt Confidence >= 0.85
        AI->>CAT: POST /api/v1/products/{id}/enrich (OpenAPI REST)
        CAT->>CAT: ProductFamily.enrich() -> Status: ENRICHED
    else Confidence < 0.85
        AI->>AI: Stage to Merchandiser HITL Review Queue
    end

    Note over CAT,COMP: Phase 3: Compliance & Approval
    CAT->>KAFKA: Publish ProductEnriched
    KAFKA->>COMP: Deliver ProductEnriched
    COMP->>COMP: Evaluate SpEL Rules & Calculate Completeness Score
    COMP->>KAFKA: Publish ComplianceEvaluated (via Compliance Outbox)
    CAT->>CAT: ProductFamily.approve(policy) -> Status: APPROVED

    Note over DAM,SYN: Phase 4: Media Asset Ingestion & Linking
    DAM->>DAM: Transcode renditions & S3 upload
    DAM->>KAFKA: Publish AssetLinkedToProduct (via Dam Outbox Relay)
    KAFKA->>SYN: Deliver AssetLinkedToProduct
    alt Base Projection Not Yet Created
        SYN->>SYN: Store in Claim-Check Buffer (Early Media)
    end

    Note over CAT,LOC: Phase 5: Publication & Regional Fan-out
    CAT->>CAT: ProductFamily.publish() -> Snapshot frozen, Revision incremented
    CAT->>KAFKA: Publish ProductPublished
    KAFKA->>LOC: Deliver ProductPublished to GlobalProductPublishedListener
    LOC->>LOC: Dynamic fan-out (US, CA, MX) + UomConverter auto-conversion
    LOC->>KAFKA: Publish RegionalItemPublished (via Localization Outbox Relay)

    Note over LOC,SYN: Phase 6: CQRS Aggregation & Distribution
    KAFKA->>SYN: Deliver RegionalItemPublished
    SYN->>SYN: Drain Claim-Check Buffer & merge DAM asset links
    SYN->>EXT: Upsert to Elasticsearch (Detail & Summary Masks)
    SYN->>EXT: Transform & Push Listing Feed to Amazon SP-API

    Note over DAM,SYN: Phase 7: DRM Expiry Revocation Cascade
    DAM->>KAFKA: Publish AssetLicenseExpired
    par Immediate Core Demotion
        KAFKA->>CAT: AssetLicenseExpiredListener
        CAT->>CAT: Strip asset -> Demote ProductFamily back to ENRICHED
    and Immediate Marketplace Purge
        KAFKA->>SYN: SyndicationEventsListener
        SYN->>EXT: Retract image from Amazon SP-API & Elasticsearch
    end
```

---

## 4. Current Codebase Implementation Inventory

For code tracing and reference during architectural discussions:

1. **Transactional Outbox Relays:**
   - Catalog: [`OutboxPollingRelay.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/OutboxPollingRelay.java)
   - DAM: [`DamOutboxPollingRelay.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/infrastructure/persistence/DamOutboxPollingRelay.java)
   - Localization: [`LocalizationOutboxPollingRelay.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/persistence/LocalizationOutboxPollingRelay.java)
2. **Listeners & Inbound Adapters:**
   - Vendor Approved: [`VendorItemApprovedListener.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/messaging/VendorItemApprovedListener.java)
   - Asset License Expired: [`AssetLicenseExpiredListener.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/messaging/AssetLicenseExpiredListener.java)
   - Localization Fan-out: [`GlobalProductPublishedListener.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/messaging/GlobalProductPublishedListener.java)
   - Great Aggregation: [`SyndicationEventsListener.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/infrastructure/messaging/SyndicationEventsListener.java)
3. **Core Aggregators & Domain Models:**
   - CQRS Aggregation & Claim-Check Buffer: [`ProductReadModelProjector.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/projections/ProductReadModelProjector.java)
   - Core Aggregate & Shadow Versioning: [`ProductFamily.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/domain/model/ProductFamily.java)
   - Category Hierarchy & Closure Table: [`ClosureTableRepositoryImpl.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/taxonomy-context/src/main/java/com/retail/pim/taxonomy/infrastructure/persistence/ClosureTableRepositoryImpl.java)
4. **Database Schemas & Extensions:**
   - DDL definitions: [`01-create-schemas.sql`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/db-init/01-create-schemas.sql) and [`02-enable-jsonb.sql`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/infra/db-init/02-enable-jsonb.sql)