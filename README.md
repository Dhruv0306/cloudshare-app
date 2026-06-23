# CloudShare - Secure File Sharing Web Application

CloudShare is a production-ready, highly secure file-sharing web application built using **Spring Boot** and a **modern JavaScript frontend**. It features robust user authentication, secure file upload/download pipelines, sharing controls, structured auditing, and automated testing, packaged with Docker support and a pre-configured CI/CD pipeline.

This repository contains the complete system design, architecture blueprints, data models, and infrastructure specifications for CloudShare.

---

## 🛠️ Technology Stack
*   **Backend:** Java 17+, Spring Boot 3.x (Spring Security, Spring Data JPA, Spring Boot Actuator)
*   **Frontend:** Modern JavaScript (Vanilla ES6+ or SPA framework like React/Vue), HTML5, Vanilla CSS
*   **Database:** PostgreSQL (Metadata, Users, Permissions, Audit Logs)
*   **Cache:** Redis (Session cache, API Rate Limiting, verification tokens)
*   **Storage (Pluggable):** 
    *   *Local Filesystem* (Default for free/local dev)
    *   *MinIO* (S3-compatible, self-hosted container for free S3 APIs)
    *   *AWS S3* (Production option)
*   **Security Scanning:** ClamAV (Anti-virus sidecar container)
*   **Build Tool & Dependency Management:** Maven
*   **Testing Framework:** JUnit 5, Mockito, Testcontainers
*   **Containerization & Deployment:** Docker, Docker Compose, Kubernetes (K8s)
*   **CI/CD:** GitHub Actions

---

## 📚 System Design Documentation Hub

The architecture and implementation specifications of CloudShare are divided into the following specialized design modules. Click on any module to review its detailed design, specifications, and Mermaid diagrams:

1.  **[System Architecture & Requirements](docs/system-design/architecture.md)**
    *   High-level architecture topology.
    *   Functional and Non-Functional Requirements (Scalability, Availability, Durability).
2.  **[Security Architecture](docs/system-design/security.md)**
    *   Authentication (OAuth2 & JWT Rotation) and Session Security.
    *   Encryption-at-Rest (Envelope Encryption via AES-256) and Encryption-in-Transit (TLS 1.3).
    *   Secure Upload Sanitization Pipeline (ClamAV virus scanning, MIME-type checks, filename sanitization).
    *   OWASP Top 10 defenses (CSRF, XSS, Path Traversal, CORS).
3.  **[RESTful API Specification](docs/system-design/api-spec.md)**
    *   API contract for User Auth, File Management, Secure Links, and Administration.
    *   Request/Response JSON schemas and HTTP response codes.
4.  **[Database Schema & ERD](docs/system-design/database.md)**
    *   Entity-Relationship Diagram (ERD).
    *   Relational DB schema (PostgreSQL DDL), indexes, and partitioning strategy.
    *   Database connection pooling (HikariCP) and row-level access controls.
5.  **[Core Data Flows & Sequence Diagrams](docs/system-design/data-flows.md)**
    *   Detailed logic flows for User Login, Secure File Upload, Secure Download, and Share Link Verification.
6.  **[Infrastructure, Containerization & CI/CD](docs/system-design/infrastructure-cicd.md)**
    *   Production-ready multi-stage `Dockerfile`.
    *   Multi-container local deployment with `docker-compose.yml`.
    *   Kubernetes production manifests.
    *   Continuous Integration and Continuous Deployment (CI/CD) pipelines with automated security scans (Trivy, OWASP Dependency Check).
7.  **[Observability, Logging & Audit Trails](docs/system-design/observability.md)**
    *   Structured JSON logging (Logback/SLF4J).
    *   Tamper-proof compliance auditing.
    *   Application metrics & telemetry (Actuator, Prometheus, Grafana).
8.  **[Testing Strategy & Test Plan](docs/system-design/testing-strategy.md)**
    *   Unit and Integration testing using JUnit 5, Mockito, and Testcontainers.
    *   Static security analysis (OWASP, SpotBugs) and performance load testing (Gatling).
9.  **[Key Management & Secrets Rotation](docs/system-design/secrets-key-management.md)**
    *   Envelope encryption configurations and external Key Management Service (KMS) integration.
    *   Zero-downtime KEK/FEK rotation runbooks.
10. **[Data Retention & Lifecycle Policies](docs/system-design/data-lifecycle.md)**
    *   Recycle bin soft-delete lifecycles and automated Spring Scheduler cleanups.
    *   GDPR compliance and account deletion procedures.
11. **[Disaster Recovery & Backup Blueprint](docs/system-design/disaster-recovery.md)**
    *   PostgreSQL backups (WAL-G), object storage redundancy, and RTO/RPO targets.
    *   Automated restore verification drill setups.
12. **[Caching & Rate Limiting Strategy](docs/system-design/caching-strategy.md)**
    *   Redis Cache-Aside metadata patterns and eviction parameters.
    *   Sliding window token rate limiting using Redis Lua scripting.
13. **[Threat Modeling & Risk Assessment](docs/system-design/threat-model.md)**
    *   STRIDE security threat classification and architectural countermeasures.
