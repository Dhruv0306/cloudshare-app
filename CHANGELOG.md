# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - YYYY-MM-DD

### Security

- Enforced runtime parity between `SecretsStartupValidator`'s fail-closed KEK shape validation
  and `EncryptionService`'s runtime KEK resolution — a non-32-byte KEK is now rejected at
  runtime (not just at startup) unless `crypto.kek.allow-raw-passphrase` is explicitly set (§1.3).
- Added Redis-backed anti-replay tracking for TOTP codes — a valid MFA code can no longer be
  presented more than once within its validity window across `/mfa/verify` and `/mfa/step-up` (§1.4, §3.5).
- Isolated rate-limiting Redis capacity from security-critical token-blacklist and refresh-token
  tracking by moving rate-limit keys to a dedicated `allkeys-lru` Redis instance (`cache-ratelimit`),
  preventing rate-limiter write volume from ever triggering OOM-driven failures in unrelated
  security enforcement paths. Refresh-token-family tracking was also migrated to a Redis sorted
  set with time-based pruning, bounding unbounded growth without weakening RTR breach-detection
  guarantees within the full refresh-token lifetime (§3.4).
- Public share-link downloads now return an identical response for "share code does not exist,"
  "share code exists but is expired," and "share code exists but the password is missing or
  incorrect," closing a minor enumeration vector. A new `/api/v1/shares/link/{code}/info`
  endpoint supports the password-prompt UX without reintroducing the same signal on the download
  path itself (§1.5).

### Fixed

- Capped previously-unbounded page-size requests on the admin audit-log listing endpoint and
  added a global page-size ceiling across all paginated API endpoints (§1.6).
- Removed a brittle hardcoded string-length assumption in public-link rate-limit key parsing
  that could have caused request failures on any future route rename (§2.4).
- File-purge and share-link-cleanup scheduled jobs now emit success/failure metrics instead of
  silently logging per-item failures with no aggregate visibility (§2.5).
- Automated creation of future monthly `audit_logs` table partitions via a new
  `AuditPartitionScheduler`, removing a previously manual maintenance dependency whose neglect
  could have taken down all audit-logged write operations (upload, download, share, delete)
  app-wide. A new admin endpoint (`POST /api/v1/admin/audit-logs/partitions`) also allows
  triggering partition maintenance on demand as an operational fallback (§2.6).
- Bounded global concurrent ClamAV scan throughput independent of per-user upload rate limits,
  protecting the single ClamAV daemon sidecar from being saturated by distributed
  low-and-slow uploads. Runtime-tunable via a new admin endpoint
  (`POST /api/v1/admin/clamav/limit`) (§3.3).
- Bounded concurrent decrypt-to-temporary-file operations across both authenticated and
  public-link downloads, preventing a burst of concurrent large-file downloads from exhausting
  shared container temp storage and disrupting unrelated users' downloads. Runtime-tunable via
  a new admin endpoint (`POST /api/v1/admin/downloads/limit`) (§3.6).

### Documentation

- Added an operational runbook for audit-log partition maintenance
  (`docs/runbooks/audit-partition-maintenance.md`), including manual fallback procedures.

## [1.1.1] - 2026-07-21

### Security

- Fixed a race condition in MFA step-up token single-use enforcement by replacing check-then-set logic with an atomic `setIfAbsent` claim in Redis (§1.1).
- Enforced fail-closed behavior (HTTP 503 Service Unavailable) when Redis security store is unavailable during step-up token validation (§1.2).

### Fixed

- Fixed a TOCTOU race condition in public share link downloads by replacing application-level read-check-increment with an atomic conditional database update (§2.1, §3.1).

## [1.1.0] - 2026-05-24

### Added

- Implemented OAuth 2.0 Client Credentials Flow for machine-to-machine authentication and service account authorization (§1.4).
- Added `/api/v1/oauth/token` endpoint for client credential issuance and token exchange (§1.4.1).
- Implemented global request rate limiting using Redis for brute force protection and denial-of-service prevention (§1.5).
- Added `/api/v1/admin/rate-limits` endpoint for administrative view of current rate limit usage (§1.5.3).
- Implemented per-second public download rate limiting per share link to prevent abuse (§3.2.1).
- Added upload concurrency controls with sliding window rate limiting to prevent resource exhaustion (§3.3.1).
- Added configurable ClamAV AV scan concurrency limit and job queueing in storage service (§2.5).

### Security

- Enforced OAuth 2.0 client authentication and PKCE validation for all authenticated API requests (§1.4).
- Added granular access control for rate limiting admin endpoints with tenant and role restrictions (§1.5.3).
- Implemented atomic download counting with conditional checks to prevent TOCTOU race conditions (§3.2.1).
- Added request size validation and X-Body-Length header enforcement to prevent request smuggling (§1.6).
- Implemented max upload file size enforcement per tenant and global configuration (§2.3).
- Enforced tenant isolation for rate limit tracking and configuration data storage (§1.5.3, §3.2.1).
- Added validation for share expiration date to prevent time-based bypasses (§3.1.3).

### Fixed

- Fixed issue where share deletion did not cascade to audit log purge jobs, causing unbounded growth (§2.2.2, §5.3.3).
- Fixed database query for file listing to handle special characters in folder names correctly (§2.2.3).
- Fixed issue where share access checks did not correctly enforce download limits for public links (§3.2.1).
- Fixed pagination query to handle large limit values without performance degradation (§4.3.2).

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
