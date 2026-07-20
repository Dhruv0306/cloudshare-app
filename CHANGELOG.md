# Changelog

All notable changes to the **CloudShare** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-07-20

### 🚀 Added
- **Single-Use Administrative Step-Up Authentication**: Introduced `StepUpAuthenticationFilter` for `/api/v1/admin/*` endpoints with single-use step-up JWT validation backed by Redis Security blacklisting (`blacklist:token:<jti>`).
- **Multi-Tier Distributed Rate Limiting**: Added `RateLimitingFilter` powered by Redis Lua sliding-window counter scripts supporting auth limits (5/min), MFA limits (5/min), upload limits (10/min), general API limits (100/min), and two-tier public link protection (30/min per link + 100/min global per IP).
- **JWT Request Memoization (`ResolvedJwt`)**: Introduced immutable `ResolvedJwt` record cached in request attributes to share pre-parsed JWT validation state between `RateLimitingFilter` and `JwtAuthenticationFilter`, eliminating duplicate cryptographic operations.
- **Fail-Loud Permission Cache Self-Healing**: Added 10-minute bypass markers (`cache:permissions:bypass:<file_id>`) in Redis when permission cache eviction fails, forcing permission checks to bypass stale Redis caches and query PostgreSQL directly while self-healing.
- **Fail-Closed KEK Startup Validation**: Added `SecretsStartupValidator` to validate secrets and ensure KEK shapes are exactly 32 Base64-decoded bytes at startup, with an explicit `crypto.kek.allow-raw-passphrase=true` opt-in property for legacy SHA-256 digested passphrases.
- **Breached Password & Anti-Enumeration Protections**: Integrated `BreachedPasswordService` into authentication flows and implemented generic registration responses to prevent account/email enumeration.
- **Playwright E2E & Python Integration Test Suites**: Added full end-to-end test suite (`tests/e2e`) covering auth, TOTP MFA, file operations, sharing, and step-up flows, along with `tests/api_test.py` integration tests.
- **Comprehensive System Design Documentation**: Created and updated technical system design specifications for security (`docs/system-design/security.md`), caching/rate limiting (`docs/system-design/caching-strategy.md`), and infrastructure/CI-CD (`docs/system-design/infrastructure-cicd.md`).

### 🛡️ Security & Infrastructure Enhancements
- **Internal-Network-Only Topology**: Locked down Docker Compose service topology so no backing service (`app`, `db`, `cache-aside`, `cache-security`, `clamav`, `storage`) exposes host ports. Nginx `gateway` acts as the sole edge ingress (ports 80/443).
- **Header Spoofing Mitigation**: Configured `ClientIpResolver` and Nginx reverse proxy to unconditionally overwrite `X-Real-IP` with remote socket addresses, preventing client IP spoofing in rate limit buckets.
- **Fail-Secure Audit Logging**: Ensured all audit log failures abort operations (upload, download, delete, share creation/revocation) for compliance.
- **Polyglot & Extension Spoofing Rejection**: Expanded file upload validation to reject dangerous extensions, MIME mismatches via Apache Tika, and polyglot script markup.

### 🐛 Fixed
- **Step-Up Single-Use Grace Period Defeat**: Removed `env`-gated grace period block in `StepUpAuthenticationFilter` that silently reset `isBlacklisted` back to false in production environments.

---

## [1.0.0] - 2026-06-01

### Added
- **Initial Production Release of CloudShare Application**:
  - Spring Boot 3.5 core REST API architecture.
  - PostgreSQL 17 database schema with range-partitioned audit logs.
  - Dual-Redis architecture (Cache-Aside & Security instance split).
  - AES-256-GCM envelope encryption with per-file FEK wrapping.
  - ClamAV container sidecar antivirus scanning.
  - Refresh Token Rotation (RTR) authentication.
  - Dark glassmorphic Vanilla JS SPA dashboard.
  - Nginx edge gateway with SSL/TLS 1.3 termination.
