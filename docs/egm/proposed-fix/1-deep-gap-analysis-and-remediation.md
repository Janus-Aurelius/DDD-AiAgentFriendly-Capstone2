# Deep Architectural Gap Analysis & Technical Remediation Blueprint

> **System:** Enterprise PIM Platform (Multi-Context Architecture)  
> **Type:** Architectural Defect Audit & Hardening Specification  
> **Priority Target:** Upcoming Technical Sprint / Fix Implementation

---

## 1. Executive Summary & Defect Severity Matrix

An exhaustive audit of the event choreography, sidecars, and cross-boundary data flows identified 6 architectural flaws:

| Finding ID | Vulnerability / Bottleneck Description | Impacted Subsystems | Severity | Target Fix Complexity |
| :--- | :--- | :--- | :---: | :---: |
| **GAP-01** | In-Memory Volatility of Syndication Claim-Check Buffer | `syndication-context` | **BLOCKER** | Medium (Persistence Table / Redis) |
| **GAP-02** | Missing Kafka Consumer Idempotency Guards outside Catalog | `localization-context`, `syndication-context` | **MAJOR** | Low (Promote Guard to Shared Kernel) |
| **GAP-03** | Database Contention from Multi-Poller Outbox Relays | `catalog`, `dam`, `localization` | **MAJOR** | High (Debezium CDC Sidecar) |
| **GAP-04** | Missing Outbox Event for Taxonomy Relocation Mutations | `taxonomy-context`, `syndication-context` | **MAJOR** | Medium (Add CategoryRelocated event) |
| **GAP-05** | Loss of Distributed Trace Context (`traceparent`) in Relays | All Outbox Relays & Kafka Producers | **MAJOR** | Low (W3C Header Injection) |
| **GAP-06** | JSON String Serialization without Schema Registry Protection | Cross-context Kafka messaging | **MAJOR** | Medium (Avro / Schema Registry) |

---

## 2. Detailed Technical Breakdown of Identified Gaps

---

### GAP-01: In-Memory Volatility of Syndication Claim-Check Buffer
* **Affected Component:** [`ProductReadModelProjector.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/application/projections/ProductReadModelProjector.java#L44-L48)
* **Mechanics:**
  ```java
  // In ProductReadModelProjector.java:
  private final Map<String, List<PendingAssetLink>> pendingAssetBuffer = new ConcurrentHashMap<>();
  ```
  The claim-check buffer holds media links when `AssetLinkedToProduct` arrives from Kafka before `RegionalItemPublished` has initialized the projection record.
* **Failure Mode & Business Impact:**
  1. If the `syndication-context` Kubernetes pod crashes, restarts, or undergoes rolling deployment, all in-memory pending media links vanish immediately.
  2. In horizontally scaled deployments (>1 pod), Instance A may receive the early `AssetLinkedToProduct` and buffer it in its local RAM, while Instance B receives the later `RegionalItemPublished`. Instance B has an empty local buffer and finalizes the product listing without images.
  3. External channel listings (e.g., Amazon SP-API) reject products lacking primary images, halting omnichannel distribution.

---

### GAP-02: Missing Kafka Consumer Idempotency Guards outside Catalog
* **Affected Components:**
  - [`GlobalProductPublishedListener.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/messaging/GlobalProductPublishedListener.java)
  - [`SyndicationEventsListener.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/syndication-context/src/main/java/com/retail/pim/syndication/infrastructure/messaging/SyndicationEventsListener.java)
* **Mechanics:**
  While `catalog-context` enforces deduplication via [`IdempotentConsumerGuard.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/messaging/IdempotentConsumerGuard.java), the other contexts directly execute application logic upon Kafka receipt.
* **Failure Mode & Business Impact:**
  Kafka guarantees *at-least-once* delivery. During network retransmits or consumer group rebalances, redelivered `AssetLinkedToProduct` events cause duplicated image URLs to be appended to `ChannelItemProjection.galleryImageUrls`, leading to invalid payloads and unnecessary external API invocations.

---

### GAP-03: Database Contention from Multi-Poller Outbox Relays
* **Affected Components:**
  - [`OutboxPollingRelay.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/catalog-context/src/main/java/com/retail/pim/catalog/infrastructure/persistence/OutboxPollingRelay.java)
  - [`DamOutboxPollingRelay.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/dam-context/src/main/java/com/retail/pim/dam/infrastructure/persistence/DamOutboxPollingRelay.java)
  - [`LocalizationOutboxPollingRelay.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/localization-context/src/main/java/com/retail/pim/localization/infrastructure/persistence/LocalizationOutboxPollingRelay.java)
* **Mechanics:**
  Every 2000ms, three separate background tasks query PostgreSQL using:
  ```sql
  SELECT ... FROM {schema}.outbox_event WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED
  ```
* **Failure Mode & Business Impact:**
  During high-volume supplier onboarding (e.g., 20,000 SKUs uploaded in a batch), multiple concurrent polling relays running `FOR UPDATE` place heavy lock management and disk I/O load on PostgreSQL, increasing transaction latency for core write queries.

---

### GAP-04: Missing Outbox Event for Taxonomy Relocation Mutations
* **Affected Component:** [`ClosureTableRepositoryImpl.java`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/backend-java/taxonomy-context/src/main/java/com/retail/pim/taxonomy/infrastructure/persistence/ClosureTableRepositoryImpl.java)
* **Mechanics:**
  Moving a subcategory in the closure table recalculates paths in `category_closure`, but emits no domain event across Kafka.
* **Failure Mode & Business Impact:**
  Downstream read projections in `syndication-context` and Elasticsearch cache continue storing obsolete hierarchical breadcrumb paths (e.g., `Electronics > Displays > Smart TVs` when moved to `Living Room > Televisions`). Storefront faceted navigation breaks, and syndicated Amazon feeds encounter category taxonomy mismatches.

---

### GAP-05: Loss of Distributed Trace Context (`traceparent`) in Relays
* **Affected Components:** All Outbox Poller Relays
* **Mechanics:**
  Domain events written to `outbox_event` tables do not record the active OpenTelemetry W3C trace context (`traceparent`, `tracestate`). Relays send messages to Kafka without injecting trace headers into `ProducerRecord`.
* **Failure Mode & Business Impact:**
  Trace contexts break across Kafka boundaries. When an asset expiration demotes a product or a vendor ingestion fails, developers cannot trace the execution path across contexts, increasing troubleshooting time in production.

---

### GAP-06: Raw String JSON Serialization without Schema Registry Protection
* **Affected Components:** All Kafka Producers and Consumers
* **Mechanics:**
  Payloads are converted to plain JSON strings via Jackson `ObjectMapper.writeValueAsString()` and read as untyped `JsonNode` or unvalidated POJOs.
* **Failure Mode & Business Impact:**
  Field renames or removals in producer events are undetected at compile time. Consuming services encounter null pointer exceptions or deserialization failures, stranding the consumer offset and creating poison pills in Kafka partitions.

---

## 3. Concrete Remediation Specifications & Implementation Steps

---

### Remediation for GAP-01: Persistent Staging Table for Claim-Check Buffer

1. **Create DDL Table in PostgreSQL:**
   Add to `infra/db-init/02-enable-jsonb.sql`:
   ```sql
   CREATE TABLE IF NOT EXISTS syndication.unclaimed_asset_buffer (
       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       sku VARCHAR(100) NOT NULL,
       region_code VARCHAR(10) NOT NULL,
       image_url TEXT NOT NULL,
       is_primary BOOLEAN NOT NULL DEFAULT FALSE,
       created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
       CONSTRAINT uq_unclaimed_asset UNIQUE (sku, region_code, image_url)
   );
   CREATE INDEX IF NOT EXISTS idx_unclaimed_asset_lookup ON syndication.unclaimed_asset_buffer(sku, region_code);
   ```

2. **Refactor `ProductReadModelProjector.java`:**
   Replace the in-memory map with repository calls:
   - When early asset arrives: `unclaimedAssetRepository.save(new UnclaimedAssetEntity(sku, region, url, isPrimary));`
   - When regional item arrives:
     ```java
     List<UnclaimedAssetEntity> pending = unclaimedAssetRepository.findBySkuAndRegion(sku, regionCode);
     for (UnclaimedAssetEntity asset : pending) {
         applyAssetLink(projection, asset.getImageUrl(), asset.isPrimary());
     }
     unclaimedAssetRepository.deleteBySkuAndRegion(sku, regionCode);
     ```

---

### Remediation for GAP-02: Universal Idempotent Consumer Guard

1. **Move Guard to `shared-kernel`:**
   Refactor `IdempotentConsumerGuard` into `shared-kernel` so any context can use it:
   ```java
   package com.retail.pim.shared.infrastructure.messaging;

   public interface IdempotencyStore {
       boolean tryAcquire(UUID eventId, String consumerGroup);
   }
   ```
2. **Add Consumed Messages Tables to All Contexts:**
   ```sql
   CREATE TABLE IF NOT EXISTS localization.consumed_messages (
       event_id UUID NOT NULL,
       consumer_group VARCHAR(100) NOT NULL,
       processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY (event_id, consumer_group)
   );
   CREATE TABLE IF NOT EXISTS syndication.consumed_messages (
       event_id UUID NOT NULL,
       consumer_group VARCHAR(100) NOT NULL,
       processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY (event_id, consumer_group)
   );
   ```
3. **Wrap Listeners:**
   In `GlobalProductPublishedListener` and `SyndicationEventsListener`, verify `guard.tryConsume(eventId, CONSUMER_GROUP)` before executing handler logic.

---

### Remediation for GAP-03: Debezium CDC Sidecar Migration Plan

1. **Docker Compose Sidecar Integration:**
   Add Debezium PostgreSQL connector to `infra/docker-compose.yml`:
   ```yaml
   debezium:
     image: debezium/connect:2.5
     container_name: pim-debezium
     ports:
       - "8083:8083"
     environment:
       BOOTSTRAP_SERVERS: kafka:9092
       GROUP_ID: 1
       CONFIG_STORAGE_TOPIC: my_connect_configs
       OFFSET_STORAGE_TOPIC: my_connect_offsets
       STATUS_STORAGE_TOPIC: my_connect_statuses
     depends_on:
       - postgres
       - kafka
   ```
2. **Migration Path:**
   - Retain polling relays for local integration unit tests (`@SpringBootTest`).
   - Configure Debezium outbox event router SMT (Single Message Transformation) in staging/production to tail WAL logs and emit directly to target Kafka topics with zero DB read queries.

---

### Remediation for GAP-04: Category Relocated Domain Event

1. **Define Event in `taxonomy-context`:**
   ```java
   public record CategoryRelocated(
       UUID eventId,
       UUID categoryId,
       UUID oldParentId,
       UUID newParentId,
       List<UUID> affectedDescendantIds,
       Instant occurredOn
   ) implements DomainEvent {}
   ```
2. **Publish on Tree Moves:**
   Inside `ClosureTableRepositoryImpl.moveSubtree()`, stage `CategoryRelocated` into `taxonomy.outbox_event`.
3. **Subscribe in Downstream Contexts:**
   - `catalog-context`: Updates cached category breadcrumbs on active products.
   - `syndication-context`: Triggers batch re-indexing of affected projections in Elasticsearch.

---

### Remediation for GAP-05: W3C Trace Context Propagation in Outbox Tables

1. **Alter Outbox Schema:**
   Add a `trace_context` column:
   ```sql
   ALTER TABLE catalog.outbox_event ADD COLUMN IF NOT EXISTS trace_parent VARCHAR(255);
   ALTER TABLE dam.outbox_event ADD COLUMN IF NOT EXISTS trace_parent VARCHAR(255);
   ALTER TABLE localization.outbox_event ADD COLUMN IF NOT EXISTS trace_parent VARCHAR(255);
   ```
2. **Capture at Save Time:**
   In `DamOutboxService` and similar publishers:
   ```java
   String currentSpanTraceParent = Span.current().getSpanContext().asTraceparent();
   // Persist currentSpanTraceParent into trace_parent column
   ```
3. **Inject at Relay Time:**
   When reading pending records in `OutboxPollingRelay`:
   ```java
   ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
   if (traceParent != null) {
       record.headers().add(new RecordHeader("traceparent", traceParent.getBytes(StandardCharsets.UTF_8)));
   }
   kafkaTemplate.send(record);
   ```

---

### Remediation for GAP-06: Schema Registry & Contract Binding

1. **Container Integration:**
   Add Apicurio or Confluent Schema Registry on port 8081 in `docker-compose.yml`.
2. **Code Generation:**
   Configure `avro-maven-plugin` to generate Java domain transfer objects from the `contracts/asyncapi/` schema specifications.
3. **Template Configuration:**
   Switch `KafkaTemplate<String, String>` to `KafkaTemplate<String, SpecificRecord>` using `io.confluent.kafka.serializers.KafkaAvroSerializer`.