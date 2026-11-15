# Cross-Context Architectural Invariants & Review Notes

> **Document Status:** Active Synchronization & Review Registry  
> **Context Index:** Context 1 of 6 (Phase 1 Baseline)  
> **Purpose:** Ensures architectural alignment, boundary discipline, and contract synchronization as subsequent contexts (2 through 6) are implemented.

---

## 1. Context 1 Foundational Invariants

These four foundational rules established in Context 1 are mandatory across all interacting contexts to guarantee domain consistency and prevent architectural erosion:

### 1. Asynchronous Event Choreography
* **Rule:** Event flow using `catalog.product.events` AsyncAPI contracts.
* **Specification:** [`contracts/asyncapi/catalog-events.yaml`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/contracts/asyncapi/catalog-events.yaml)
* **Mechanics:** 
  - State changes in the Java catalog context trigger domain events (`ProductCreated`, `ProductEnriched`, `ProductUpdated`, `ProductVariantAdded`).
  - Event payloads adhere to a common envelope (`eventId`, `eventType`, `timestamp`, `aggregateId`, `version`).
  - Downstream consumers (Context 2 Event Streaming, Context 3 AI Agents, Context 4 Search/RAG) must consume events asynchronously from the message bus without blocking core transaction boundaries.

### 2. Strict Boundary Rule
* **Rule:** Python AI never communicates directly with PostgreSQL; all updates go through Java Application Services to enforce domain invariants.
* **Mechanics:**
  - PostgreSQL database schemas (`catalog`, `taxonomy`) are strictly owned and managed by `backend-java`.
  - Python AI workers, external microservices, and scripts are completely isolated from direct DB connection strings or ORM entities.
  - Any AI enrichment, category recommendation, or metadata generation must enter the domain exclusively through the validated API-First port: `POST /api/v1/products/{id}/enrich` defined in [`contracts/openapi/catalog-api.yaml`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/contracts/openapi/catalog-api.yaml).
  - The Java Application Service (`ProductEnrichmentService`) validates business rules, aggregate state, and optimistic locking before persistence.

### 3. Java ↔ Python Pydantic Mapping
* **Rule:** Exact JSONB schema definitions for `AttributeValue` and `Dimensions`.
* **Mechanics:**
  - **`AttributeValue`**: Polymorphic typed attribute representation matching between Java and Python Pydantic:
    - Types: `STRING`, `NUMBER`, `BOOLEAN`, `JSON`, `LOCALIZED_STRING`.
    - Storage: PostgreSQL `JSONB` column with GIN indexation.
  - **`Dimensions`**: Standardized physical measurements (`length`, `width`, `height`, `weight`, `unit` e.g. `cm/kg`).
  - Contract parity between Java Value Objects in `catalog-context` and Python Pydantic models in `ai-python` guarantees zero deserialization drift or type loss.

### 4. Human-in-the-Loop (HITL) Quality Gate
* **Rule:** Confidence score thresholding ($\ge 0.85$ auto-apply vs. $< 0.85$ pending review).
* **Mechanics:**
  - **$\text{Confidence Score} \ge 0.85$**: The enrichment payload is considered high-confidence and is automatically applied to `ProductFamily.attributes`.
  - **$\text{Confidence Score} < 0.85$**: The enrichment payload is routed to a staging queue / flagged as `PENDING_REVIEW` to prevent hallucinated attributes from polluting the catalog.
  - **Human-curated values priority**: AI cannot silently overwrite human-verified attributes unless an explicit `force_override` authorization flag is supplied.

---

## 2. Six-Context Synchronization Matrix

This project spans 6 distinct bounded contexts and architectural phases:

| Context # | Bounded Context / Layer | Status | Sync Dependency with Context 1 |
| :---: | :--- | :---: | :--- |
| **Context 1** | **Core PIM (Catalog & Taxonomy)** | **Done (Phase 1 Core)** | **Base System of Record (SoR)**: Pure Java domain, Postgres JSONB, Closure Table, OpenAPI & AsyncAPI contracts. |
| **Context 2** | **Event-Driven Messaging & Streaming** | Planned (Phase 2) | Implements broker bindings for `catalog.product.events` (Kafka / RabbitMQ), outbox pattern, and dead-letter queues. |
| **Context 3** | **Python AI Agentic Copilot** | Planned (Phase 3) | Consumes AsyncAPI events, runs LLM extraction pipeline, adheres to Pydantic $\leftrightarrow$ Java mapping and HITL score gates. |
| **Context 4** | **Search, Discovery & RAG Context** | Planned (Phase 4) | Ingests enriched products and taxonomy hierarchies for vector embeddings (pgvector / OpenSearch). |
| **Context 5** | **Syndication / Channel Export Context** | Planned (Phase 5) | Reads validated product families and formats them into marketplace feeds (Amazon, Google Merchant, Shopify). |
| **Context 6** | **Frontend Curator & Review UI** | Planned (Phase 6) | Web UI for taxonomy tree navigation and the HITL review dashboard for approving low-confidence AI suggestions. |

---

## 3. Pre-Flight Review Checklist for Subsequent Contexts

Before starting Contexts 2 through 6, verify against this checklist:
- [ ] Does any new service attempt direct connection to `pim_db`? *(Violation: Must use Java REST/gRPC API)*
- [ ] Are new event schemas registered in `contracts/asyncapi/` before implementation?
- [ ] Do Python schemas in `ai-python` match `AttributeValueDto` and `DimensionsDto` in `contracts/openapi/catalog-api.yaml`?
- [ ] Is the $0.85$ confidence score check applied before calling the `/enrich` endpoint?
- [ ] Does the taxonomy navigation utilize the Closure Table endpoints (`/ancestors`, `/descendants`, `/tree`) rather than recursive DB queries?

---

## 4. Associated References & Detailed Specs
- Enterprise Cross-Domain Architecture Audit: [`docs/enterprise-cross-domain-audit.md`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/enterprise-cross-domain-audit.md)
- Core Catalog Design & Gap Review: [`docs/core-catalog-design-review.md`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/core-catalog-design-review.md)
- Detailed Cross-Boundary Blueprint: [`docs/3. The Cross-Boundary Workflow_cross-boundary-workflow.md`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/3.%20The%20Cross-Boundary%20Workflow_cross-boundary-workflow.md)
- REST API Spec: [`contracts/openapi/catalog-api.yaml`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/contracts/openapi/catalog-api.yaml)
- Events Spec: [`contracts/asyncapi/catalog-events.yaml`](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/contracts/asyncapi/catalog-events.yaml)
