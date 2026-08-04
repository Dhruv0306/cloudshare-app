# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-08-04

### Changed

- **[Major/Internal]** Split the monolithic `frontend/js/app.js` (1,262 lines) into 10 ES
  modules: `state.js` (the single shared state object, exported once as a true singleton),
  `shared.js` (pure UI helpers with no state/API dependency), `session.js` (session lifecycle:
  JWT parsing, active-session check, shell setup/teardown), `router.js` (the SPA router),
  `views/{auth,dashboard,sharing,mfa,admin}.js` (one file per feature area), and a slim
  bootstrap `app.js` that only wires up `DOMContentLoaded` and event listeners. No behavior
  change — verified via a full function-name inventory diff (all 42 original top-level
  functions present, none missing, none added), a byte-identical diff of the `state` object
  definition, and a Node.js ESM import-resolution harness confirming every module's imports
  resolve against real exports (this caught one real bug before merge: `bindGlobalEvents`'s
  admin pagination/filter handlers weren't importing `loadAdminUsers`/`loadAdminLogs`).
  `index.html` required no changes — it already loads `app.js` as `type="module"`, and the app
  has zero inline `onclick="..."` HTML attributes. This is a breaking internal restructure
  (import paths, module boundaries) but touches no external/public contract — no HTTP endpoint,
  request/response shape, or user-facing behavior changed.
- **[Major/Internal]** Extracted `PermissionCacheService` out of `FileService` and `ShareService`,
  removing duplicated Redis permission-cache logic. The near-identical eviction-with-bypass-marker
  pattern previously existed independently in `FileService.deleteFile` and
  `ShareService.evictPermissionsCache`; the cache-read-with-self-heal pattern existed only in
  `FileService.verifyFileAccess`. The new service owns only the Redis mechanics (cache-aside
  read/write, eviction, bypass-marker self-healing) — it does not query repositories directly;
  `FileService.verifyFileAccess` keeps owning the database-fallback orchestration. Both services'
  constructors now take `PermissionCacheService` instead of a directly-qualified
  `StringRedisTemplate`. Test coverage: `FileServiceTest`/`ShareServiceTest` mocks rewritten (37
  prior Redis-mock references across both files) to mock the new service and verify correct
  delegation; a new `PermissionCacheServiceTest` is now the authoritative coverage for the
  extracted cache-aside/eviction/self-healing mechanics themselves. No API/endpoint contract
  change.

## [1.3.0] - 2026-08-02

### Added

- Added `healthcheck:` blocks and `restart: unless-stopped` to every service in
  `docker-compose.yml`, and upgraded `app`'s `depends_on` to the long-form
  `condition: service_healthy` syntax. Previously `depends_on` only guaranteed
  container *start* order, not readiness — Postgres/Redis/MinIO could still be
  unready when the app started. `db` uses `pg_isready`; the three Redis instances use
  `redis-cli ping`; `storage` (MinIO) uses `mc ready local` (the currently-recommended
  check — the official `minio/minio` image dropped both `curl` and `wget` in late
  2023, so the commonly-referenced `curl .../minio/health/live` example no longer
  works on current image tags); `clamav` needs no new healthcheck since the official
  `clamav/clamav` image already ships its own (`clamdcheck.sh`).
- Added JaCoCo coverage reporting (`jacoco-maven-plugin`, report-only — no enforced
  `jacoco:check` threshold yet, since this repo's actual coverage baseline hasn't
  been measured from a real build). The `verify` CI job now uploads the generated
  HTML/XML report as a build artifact for visibility. An enforced minimum can follow
  once a real baseline number is available.
- Added `@Min(1)`/`@Max(200)` constraints directly to `AdminController`'s
  `clamav/limit` and `downloads/limit` endpoint parameters, mirroring the bounds
  already enforced in `ClamAvService`/`DownloadConcurrencyLimiter`. Uses Spring
  Framework 6.1+'s native method-validation support (no class-level `@Validated`,
  which would route through the older, discouraged-for-this-version AOP-proxy path).
  A new `GlobalExceptionHandler` handler for `HandlerMethodValidationException`
  returns the same `VALIDATION_FAILED` response shape already used for request-body
  validation failures, instead of a bad value only surfacing one layer down.
- Added explicit, environment-overridable connection pooling and command timeouts
  for all three Redis `LettuceConnectionFactory` beans (cache-aside, security,
  rate-limit), previously running on library defaults. Timeout defaults are chosen
  per instance's actual failure semantics: the security instance (fail-closed on
  step-up token checks) keeps a more forgiving 2000ms default so transient latency
  doesn't turn into incorrectly rejected legitimate step-up attempts, while the
  rate-limit instance (fails open by design in `RateLimiterService`) uses a
  deliberately shorter 500ms default, since there's no security benefit to waiting
  longer before falling through to "allow."

## [1.2.2] - 2026-08-02

### Added

- Added a container-level `HEALTHCHECK` to the production `Dockerfile`, polling the Actuator
  liveness probe (`/actuator/health/liveness`, already exposed unauthenticated via
  `SecurityConfig` and `management.endpoint.health.probes.enabled`). Gives `docker ps` and any
  orchestrator visibility into application health without requiring compose-level tooling.

### Security

- Expanded the upload pipeline's dangerous-MIME deny-list (`isDangerousMimeType`) to also reject
  `application/x-executable`, `application/x-elf`, `text/x-python`, and `application/x-httpd-php`,
  closing a few common executable/script MIME variants that were previously absent from the
  secondary content-based deny-list (the primary extension allow-list already blocks the
  associated file extensions independently).

- Fixed locale-sensitive case folding in `FileService`: `String#toLowerCase()`/`toUpperCase()`
  called with no `Locale` argument use the JVM's default locale, which can silently corrupt
  ASCII-only string matching under certain locales (e.g. Turkish, where `I`/`i` fold differently).
  Switched to `toLowerCase(Locale.ROOT)` in `containsDangerousMarkup` (polyglot markup scan),
  `isDangerousMimeType` (MIME deny-list check), and `isDisallowedExtension` (extension allow-list
  check) to make case folding deterministic regardless of server locale. Caught in PR review.

### Changed

- Reduced redundant work in the polyglot-file markup scanner (`containsDangerousMarkup`): the
  sliding content window is now maintained pre-lowercased and updated incrementally per chunk,
  instead of re-lowercasing the entire (up to 5000-character) window from scratch on every 8KB
  read. The window size, trim threshold, and boundary-overlap behavior are unchanged, so detection
  coverage for markers split across chunk reads is unaffected — this is a constant-factor
  efficiency improvement, not a change in scanning behavior.
- Split the permission-cache failure log tag in `FileService.verifyFileAccess` into two distinct
  markers: `[PERMISSION_CACHE_EVICTION_FAILED]` (unchanged, used only when a permission-cache
  *eviction* fails after a share/ownership change — a real stale-permission risk) and the new
  `[PERMISSION_CACHE_READ_FAILED]` (used when a cache *read* fails and the code safely falls back
  to the database as source of truth — not a security risk). Previously both conditions were
  unlabeled/conflated, which would have caused false-positive alerting on the benign path if
  either tag is used for monitoring.

## [1.2.1] - 2026-08-02

### Security

- Fixed a TOTP anti-replay bypass where the single-use guard was keyed on the server's current
  time-step rather than the submitted code's value. Because the code verifier accepts a +/-1
  step discrepancy window by default, a code could be replayed once in an adjacent 30s window
  since each window computed a different Redis claim key. The guard is now keyed on a hash of
  the code itself, closing the window regardless of which step it validated under.
- Removed logging of raw refresh-token values at `DEBUG` level in `RefreshTokenService`, and
  changed the default `com.cloudshare` logging level from `DEBUG` to `INFO` in both
  `application.yml` and the non-dev `logback-spring.xml` profile, so this class of sensitive
  data-in-logs mistake isn't shipped as the default in any environment.
- Added authentication (`requirepass`) to all three Redis instances (`cache-aside`,
  `cache-security`, `cache-ratelimit`), which previously relied solely on Docker network
  isolation. `SecretsStartupValidator` now fails closed at boot if a Redis password is missing,
  unless explicitly overridden for local development.
- Added a per-account (hashed-identifier) rate limit on `/api/v1/auth/login`, layered on top of
  the existing per-IP limit, to reduce exposure to credential-stuffing attempts distributed
  across many source IPs against a single target account.
- Added audit logging (`STEP_UP_GRANTED` / `STEP_UP_FAILED`) for MFA step-up token issuance,
  closing a gap where every other sensitive action had an audit trail except this one.
- Client-facing authentication error responses no longer echo raw `UsernameNotFoundException`
  messages (which could include internal identifiers); a generic message is returned instead
  while the detail is still captured server-side in logs.

### Fixed

- Public share-link creation (`expiresInSeconds`) now enforces an upper bound (30 days) in
  addition to the existing lower bound, preventing effectively-permanent share links and a
  potential `Instant` overflow on extreme input values.
- Admin-tunable ClamAV scan and download concurrency limits now enforce an upper sanity bound
  (200) in addition to the existing lower bound, preventing accidental resource exhaustion from
  a misconfigured value.

### Changed

- File-upload extension filtering switched from a deny-list of known-dangerous extensions to an
  allow-list of permitted extensions, closing the inherent gap where novel or uncommon
  executable/script extensions could bypass a fixed blocklist.
- Removed unreachable fallback branches in `JwtAuthenticationFilter` and `RateLimitingFilter`
  that handled a `null` return from `JwtTokenProvider#resolveToken`, which never actually
  returns `null` (code clarity only, no behavior change).

## [1.2.0] - 2026-07-27

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
