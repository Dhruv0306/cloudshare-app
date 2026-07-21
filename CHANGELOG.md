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
