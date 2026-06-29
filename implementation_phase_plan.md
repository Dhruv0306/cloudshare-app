## Phase 6 — Lifecycle Schedulers

**Scope:** `FilePurgeScheduler` and `LinkCleanupScheduler` from `data-lifecycle.md`.

**Key work:**
- Enable Spring `@EnableScheduling` on the application
- `FilePurgeScheduler`: query `deleted = TRUE AND updated_at < NOW - 30 days`, delete physical file from storage first, then delete DB row — safe deletion order from the design doc
- `LinkCleanupScheduler`: `DELETE FROM share_links WHERE expires_at < CURRENT_TIMESTAMP`
- Both schedulers need audit log entries on purge events
- Unit tests mocking the scheduler trigger, integration test verifying a soft-deleted file older than 30 days is purged

**Why first:** Pure backend, no new dependencies, small surface area. Good warm-up phase.

---

## Phase 7 — KEK Rotation Worker

**Scope:** `ReKeyWorker` from `secrets-key-management.md`.

**Key work:**
- `@Profile("rekey-job")` `CommandLineRunner` activated via `--spring.profiles.active=rekey-job`
- `findBatchForReKey` custom query using `FOR UPDATE SKIP LOCKED LIMIT 100`
- Decrypt FEK using old KEK version → re-encrypt using new KEK version → update `kek_version` in DB
- Batch loop until no rows remain
- Audit `SYSTEM_REKEY` event per file
- Add `kek_version` system property parsing (`rekey.oldVersion`, `rekey.newVersion`)
- Unit tests for batch processing logic, concurrency safety via `SKIP LOCKED`

**Why here:** Depends on the encryption infrastructure from Phase 2 being stable and battle-tested. Independent of the frontend.

---

## Phase 8 — Frontend

**Scope:** JS frontend consuming the REST API.

**Key work — in order:**
- Auth pages: register, login (with MFA code field conditional on `mfaRequired`), logout
- File dashboard: paginated file list, upload with progress, download, delete
- Sharing UI: internal share modal, public link generator (expiry, password, download limit)
- MFA settings page: setup QR code display, verify + activate flow
- Admin panel (ROLE_ADMIN only): user list, audit log viewer with filters
- Nginx `frontend/` static asset serving (already wired in `docker-compose.yml` and `nginx.conf`)

**Suggested stack:** Vanilla JS or a lightweight SPA (Vue/React) — the system design leaves this open. Vanilla keeps it dependency-free and simpler to serve as static files.

**Why here:** All API contracts are stable and tested. Building against a complete, hardened backend avoids rework.

---

## Phase 9 — Gatling Load Tests

**Scope:** Performance validation against the KPIs in `testing-strategy.md`.

**Key work:**
- Gatling scenario simulating 100 concurrent users:
  1. Register + login → get token
  2. Upload 10MB random file
  3. List files
  4. Download uploaded file
- Assert KPIs:
  - p95 API latency `< 200ms`
  - p95 file streaming latency `< 1500ms`
  - Error rate `< 0.1%`
- Add a `performance` Maven profile so Gatling doesn't run on every `mvn verify`
- Add a separate GitHub Actions workflow `load-tests.yml` triggered manually (`workflow_dispatch`) rather than on every push

**Why last:** Needs the full stack stable including Nginx (Phase 5), and ideally a deployed environment rather than a CI runner with limited resources. Manual trigger is the right model here.

---