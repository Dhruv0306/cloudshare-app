# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
