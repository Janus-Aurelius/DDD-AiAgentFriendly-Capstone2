# AI-Python Service (Phase 3 Reserved)

This directory is reserved for Phase 3 of the **DDD AI-Agent Friendly PIM** project.

## Purpose & Scope (Phase 3 Preview)
The Python AI service acts as an autonomous agent and copilot for product information management:
- **Product Categorization**: Inferring taxonomy placement and hierarchy suggestions based on unstructured product titles and raw supplier specs.
- **Dynamic Attribute Extraction & Enrichment**: Parsing unstructured PDFs, catalogs, HTML descriptions into typed attributes (`AttributeValue`) and standardized dimensions.
- **Automated Copywriting & SEO**: Generating localized, marketing-optimized product descriptions adhering to brand tone and style guidelines.
- **Quality Scoring & Validation**: Inspecting completeness of product aggregates before publishing to downstream sales channels.

## Integration Blueprint
See the comprehensive specification document:
👉 [Cross-Boundary Workflow (Where Java meets Python AI)](file:///home/annguyen/master_projects/DDD-AiAgentFriendly-Capstone2/docs/cross-boundary-workflow.md)

## Tech Stack (Planned)
- Python 3.11+
- FastAPI / gRPC
- LangChain / LlamaIndex / Agentic frameworks
- Kafka / RabbitMQ consumer (consuming AsyncAPI events from `contracts/asyncapi/catalog-events.yaml`)
- Pydantic models validated against OpenAPI specs in `contracts/openapi/catalog-api.yaml`
