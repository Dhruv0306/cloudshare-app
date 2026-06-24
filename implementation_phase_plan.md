## Phase 1 — Core Foundation (do this first)
1. PostgreSQL schema — run the DDL exactly as documented, including the `kek_version` column and the soft-delete trigger. This gives you a stable base.
2. User registration + BCrypt password hashing — simplest auth flow, no JWT complexity yet.
3. JWT login + access token issuance — gets you a working auth loop.
4. Refresh Token Rotation with Redis Security instance — completes the session layer.

## Phase 2 — File Operations
5. File upload pipeline — size check → ClamAV scan → MIME check → AES-256-GCM encryption → MinIO/local write → DB insert.
6. File download — reverse the crypto pipeline, stream to client.

## Phase 3 — Sharing
7. Internal user-to-user shares.
8. Public share links with expiry, download limits, and password protection.

## Phase 4 — Security Hardening
9. MFA enrollment and TOTP verification.
10. Admin endpoints + step-up authentication.
11. Redis Lua rate limiting on auth and upload routes.

## Phase 5 — Observability & Ops
12. Structured JSON logging + Nginx header stripping.
13. Actuator + Prometheus metrics.
