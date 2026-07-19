## LM1 — `security/secrets-hygiene-audit`

**Problem:** never actually confirmed no real secret (KEK, JWT signing key, DB/MinIO creds) ever touched a committed file — `.env.example` looked clean in the snapshot I reviewed, but "looked clean in the current tree" and "never appeared in history" are different guarantees, especially heading into a v1.1 tag.

**Plan:**
1. Run `git log -p --all -- .env .env.* '**/application*.yml'` and grep the full diff history for patterns that look like real secrets (long base64/hex strings in a `KEK`/`JWT_SECRET`/`PASSWORD` context) rather than the `${VAR:-default}` placeholders you use everywhere else.
2. Cross-check `.gitignore` actually covers `.env` (not just `.env.example`) and has covered it since the first commit that could have contained one — a `.gitignore` added late doesn't retroactively scrub history.
3. If anything real is found: rotate that secret immediately (KEK rotation already has tooling via `ReKeyWorker`; JWT secret rotation just needs a deploy + forces re-login for all sessions), then decide whether to rewrite history (`git filter-repo`) — only worth it pre-wide-distribution, skip if the repo already has external clones/forks.
4. If clean: no code change, just close the item with a note in `docs/system-design/secrets-key-management.md` recording that the audit was done and when.

**Files:** none (if clean) or the affected secret's rotation path + `secrets-key-management.md` note.
**Tests:** none — this is an audit, not a code change.

---

## LM2 — `infra/minio-console-lockdown`

**Problem:** MinIO console (port 9001) is enabled and, pre-C2-fix, was publicly reachable; even with C2 fixed (internal-only network), it's still running and reachable by anything on the Docker network with only root credentials guarding it — more attack surface than a file-sharing app's object store needs.

**Plan:**
1. `docker-compose.yml`: change the MinIO `command` to drop `--console-address ":9001"` in staging/prod-like profiles, or gate it behind a compose profile (e.g. `profiles: ["dev-tools"]`) so it's opt-in locally and absent by default.
2. Confirm nothing in `MinIoStorageServiceImpl` or app startup depends on the console being up (it shouldn't — the console is a human UI, the S3 API on 9000 is what the app uses).
3. Document the operational alternative in `docs/system-design/infrastructure-cicd.md`: bucket inspection via `mc` (MinIO client CLI) run ad hoc against the internal network or through an SSH tunnel, rather than a standing web console.
4. If you still want the console available for local dev convenience, that's fine — just make sure the staging/prod compose path doesn't inherit it (same "override doesn't remove ports" trap you hit in C2 — verify explicitly rather than assuming).

**Files:** `docker-compose.yml`, `docs/system-design/infrastructure-cicd.md`.
**Tests:** none (infra config, not app logic) — verify manually post-deploy that `9001` isn't reachable where you didn't intend it.

---

## LM3 — `perf/rate-limit-single-jwt-parse`

**Problem:** `RateLimitingFilter.getUserIdFromAuthorizationHeader` and `JwtAuthenticationFilter` each independently parse and validate the same JWT per request — correct today, just duplicated CPU work.

I flagged in the original review that fixing this by having `RateLimitingFilter` run *after* auth and read `SecurityContextHolder` trades away some defense-in-depth (rate limiting currently applies before a full auth context is built, which matters if JWT parsing itself were ever a DoS vector). Given that tradeoff, I'd suggest **not** reordering the filters, and instead just avoiding the duplicate parse without changing filter order:

**Plan:**
1. Add a lightweight request-scoped cache: after `JwtAuthenticationFilter` runs (it's already `addFilterBefore(rateLimitingFilter, JwtAuthenticationFilter.class)` — wait, currently `RateLimitingFilter` runs *before* `JwtAuthenticationFilter`, so it can't reuse anything from it yet). Given the current order is rate-limit → JWT-auth, the cheapest real fix is: have `RateLimitingFilter` parse the JWT once into a small local record (`userId`, validity) and stash it as a request attribute (`request.setAttribute("resolvedUserId", ...)`), then have `JwtAuthenticationFilter` check for that attribute before re-parsing, falling back to its own parse if absent (keeps each filter independently correct/testable).
2. This keeps the current security ordering (rate limiting still gates before full auth context exists) while removing the duplicate parse in the common case.
3. Tests: add a test asserting `JwtAuthenticationFilter` uses the pre-resolved attribute when present (e.g. via a spy/mock verifying the token parser is called at most once across both filters for a single request), and that it still works correctly (falls back to its own parse) when the attribute is absent — covers the case where `RateLimitingFilter` is disabled (`rateLimitingEnabled=false`).

**Files:** `RateLimitingFilter.java`, `JwtAuthenticationFilter.java`, `RateLimitingFilterTest.java`, a small addition to `JwtAuthenticationFilter`'s test coverage (create one if it doesn't exist yet — I didn't see a dedicated `JwtAuthenticationFilterTest` in the file list, only `AuthenticationMdcFilterTest`, `MdcFilterTest`, `RateLimitingFilterTest`, `StepUpAuthenticationFilterTest`).

**Note:** this is genuinely optional — it's a CPU-cycle optimization, not a correctness or security fix. Given everything else on your plate, I'd rank this last of the five LOW/MEDIUM items.

---

## LM4 — `testing/polyglot-upload-regression`

**Problem:** upload pipeline order (ClamAV scan → Tika MIME detection → dangerous-extension/MIME check → encrypt) is correct, but there's no test proving a polyglot file (valid image header + trailing malicious payload, or a file whose extension and magic bytes disagree) is actually caught by the MIME/extension check rather than relying on ClamAV signature coverage alone.

**Plan:**
1. Add fixture files under `src/test/resources/fixtures/`: e.g. a PNG with a `.php` extension, a plain-text file renamed to `.jpg`, and a valid JPEG with an appended `<script>` payload after EOF (classic polyglot pattern) — all non-malicious payloads, just mismatched-signature test data, so nothing ClamAV-flagged needs to be checked into the repo.
2. `FileServiceTest`: new tests calling `uploadFile` with each fixture, asserting `UnsupportedMediaTypeException` is thrown based on Tika's detected MIME type disagreeing with the extension/dangerous-type list — independent of ClamAV's mocked response (mock ClamAV as "clean" in these tests specifically, to isolate that the MIME check is the thing catching it, not the scanner).
3. Optionally extend `tests/e2e/specs/dashboard.spec.ts` (or a new spec) with one real end-to-end polyglot upload attempt through the full stack, since Playwright fixtures already exist for upload flows — lower priority than the unit-level `FileServiceTest` coverage, which is the part actually missing.

**Files:** `src/test/resources/fixtures/*` (new), `FileServiceTest.java`, optionally `tests/e2e/specs/*.spec.ts`.

