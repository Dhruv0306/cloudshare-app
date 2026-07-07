## Phase 10 — Share Revocation (do this first)

**Scope:** Close the gap identified in review — no way to revoke an internal share or a public link.

**Key work:**
- `DELETE /api/v1/shares/internal/{shareId}` — validate caller is the file owner (or `sharedBy`), delete the `FileShare` row, call `evictPermissionsCache(fileId)`, audit log `SHARE_REVOKED`
- `DELETE /api/v1/shares/link/{shareCode}` — validate caller owns the underlying file, delete the `ShareLink` row, audit log `SHARE_LINK_REVOKED`
- Confirm `evictPermissionsCache` is called on **every** mutation path (create, update permission, revoke) — not just create, which is the only one that exists today
- Unit tests: revoke by non-owner returns 403/404 (BOLA), revoked share immediately fails `verifyFileAccess`, cache eviction confirmed via Redis mock
- Extend `tests/api_test.py`: create share → download succeeds → revoke → download fails

**Why first:** Tagging v1.0.0 with an unfinishable share lifecycle (create-only, no revoke) is a real product gap, not a nice-to-have. Cheap to fix now; expensive to patch after a version tag implies a stable contract.

**Workflow:** plan → your review → implement → diff → my review → fix flagged issues → squash-merge to main, same as phases 1–9.

---

## Phase 11 — Tag v1.0.0

**Scope:** Formal release cut once Phase 10 is merged.

**Key work:**
- Bump `pom.xml` version from `1.0.0-SNAPSHOT` → `1.0.0`
- Final full-suite run: `mvn clean test`, `python tests/api_test.py`, manual smoke pass through the frontend (auth, upload/download, internal share, public link, MFA, admin panel)
- Confirm `maven.yml` and `api-tests.yml` are green on the tag commit
- `git tag -a v1.0.0 -m "..."` + push tag; create a GitHub Release with the phase summary from README as release notes
- Post-tag: bump `pom.xml` to `1.1.0-SNAPSHOT` on main to signal active development resumes

**Why here:** Everything else on your original "on the horizon" list (README, V3 partitions) is already done. Phase 10 was the only functional gap blocking a clean tag.

---

## Phase 12 — Playwright/Cypress E2E Tests

**Scope:** Browser-level E2E coverage of the frontend against the live stack, complementing (not replacing) `api_test.py`.

**Key work:**
- Tool choice: Playwright recommended over Cypress here — better multi-tab support (useful for testing "share with another user" flows across two authenticated sessions) and native TypeScript support
- Test scenarios, roughly in priority order:
  1. Register → login → MFA enrollment → MFA-gated login
  2. Upload file → verify appears in dashboard → download → checksum matches
  3. Internal share flow across two browser contexts (owner + recipient)
  4. Public link: create with password + expiry → download as unauthenticated guest → expired/limit-exceeded rejection
  5. Revoke share (Phase 10) → verify recipient loses access
  6. Admin panel: user list, audit log viewer with filters (ROLE_ADMIN only)
  7. Step-up auth prompt on sensitive admin actions
- Run against Docker Compose stack (same as `api_test.py` does), not a mocked backend — this is meant to catch integration issues the unit/API layers miss
- New GitHub Actions workflow `e2e-tests.yml`, likely `workflow_dispatch` or nightly rather than per-push, given stack startup cost

**Why here:** Needs Phase 10's revoke flow to have full coverage, and needs a tagged, stable API surface (Phase 11) so tests aren't chasing a moving target.

---

## Phase 13 — Dedicated Gatling Staging Environment

**Scope:** Give the existing Gatling suite (Phase 9) a real target instead of a resource-constrained CI runner.

**Key work:**
- Provision a staging environment — sizing matched to production expectations, not a laptop/CI runner, so p95 latency numbers are meaningful
- Deploy via the same `docker-compose.yml` (or a staging-specific compose override) with production-like resource limits on Postgres/Redis/MinIO
- Point `load-tests.yml` (`workflow_dispatch`) at the staging host instead of `localhost`
- Re-baseline the KPIs from `testing-strategy.md` (p95 API < 200ms, p95 streaming < 1500ms, error rate < 0.1%) against staging and record the results as the new reference baseline
- Decide on data lifecycle for staging: fresh DB per run, or persistent with periodic purge via the existing schedulers (Phase 6)

**Why last:** Lowest urgency of the four — the Gatling suite already exists and runs; this just makes its numbers trustworthy. No dependency on the other three phases, so it can run in parallel with Phase 12 if you want.

---