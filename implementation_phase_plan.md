## 1. `security/xff-trust-chain`
**Problem:** Nginx's `$proxy_add_x_forwarded_for` appends the real IP to any client-supplied `X-Forwarded-For`. Every `getClientIp()` took the *first* entry — attacker-controlled — so rate limiting and audit IPs were spoofable by just sending `X-Forwarded-For: 1.2.3.4`.

**Parts:**
1. **New file** `security/ClientIpResolver.java` — reads `X-Real-IP` (set unconditionally by Nginx from `$remote_addr`, never client input), falls back to `request.getRemoteAddr()`.
2. **`RateLimitingFilter.java`** — add `ClientIpResolver` field, replace `getClientIp(request)` call, delete the old local method.
3. **`FileController.java` / `ShareController.java` / `AuthController.java`** — same pattern: inject resolver, replace calls, delete local method.
4. **`nginx.conf`** — change `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;` → `$remote_addr;` (defense in depth for anything downstream still reading that header).

**Verify locally:** `mvn compile` (this is pure Spring wiring + one new `@Component`, lowest compile risk of the 8). Then functionally: hit any rate-limited endpoint with `curl -H "X-Forwarded-For: 1.2.3.4" ...` past the limit and confirm it now gets throttled instead of resetting.

---

## 2. `security/fail-fast-secrets`
**Problem:** `application.yml` had hardcoded fallback secrets (`JWT_SECRET`, DB password, MinIO creds, `CRYPTO_MASTER_KEK`) — public since they're in this repo.

**Parts:**
1. **`application.yml`** — strip the literal fallback values, default to empty string instead.
2. **New file** `config/SecretsStartupValidator.java` — `@PostConstruct` bean that throws `IllegalStateException` at boot if any required secret is blank, too short, or matches a known former default.
3. **`.env.example` / `tests/.env.staging.example`** — update placeholder values so they don't trip the new validator.
4. **CI workflows** (`api-tests.yml`, `e2e-tests.yml`, `load-tests.yml`) — change `minioadmin` → `cloudshare_ci_minio` in generated `tests/.env.ci`.

**⚠️ This is the one most likely to break something in your actual environment** if any real deployment (even your own local docker-compose) still has old/default values in a real `.env` — the app will now refuse to boot. Before merging: `grep` your actual `.env` (not `.env.example`) for `minioadmin`, `StrongDBPassword123!`, or the old JWT secret string, and rotate them first.

**Verify locally:** `mvn spring-boot:run` with a deliberately-blank `JWT_SECRET` — should fail immediately with a clear message, not an opaque Spring placeholder error.

---

## 3. `security/mfa-strict-rate-limit`
**Problem:** `/auth/mfa/verify` and `/auth/mfa/step-up` used the general 100/min bucket instead of the strict 5/min bucket login gets.

**Parts:**
1. **`RateLimitingFilter.java`** — new `@Value("${security.rate-limiting.mfa-limit:5}") private int mfaLimit;`, new branch matching those two paths, keyed by authenticated user id (via existing `getUserIdFromAuthorizationHeader` helper — already present in the file for the upload branch, I reused it, didn't add a new one).
2. **`application.yml`** — add `mfa-limit: ${RATE_LIMIT_MFA:5}`.
3. **`.env.example` / `tests/.env.staging.example` / CI workflows** — add `RATE_LIMIT_MFA` alongside existing `RATE_LIMIT_AUTH`.

**Verify locally:** unit-test-free change (pure filter logic); functional check is to hit `/auth/mfa/verify` 6 times in a minute as one user and confirm the 6th is throttled.

---

## 4. `security/atomic-refresh-token-rotation`
**Problem:** `rotateRefreshToken()` did Redis `GET` then separate `DELETE` — a TOCTOU race where two concurrent requests could both pass the check.

**Parts:**
1. **`RefreshTokenService.java`** — replace `valueOperations.get(activeKey)` + `securityRedisTemplate.delete(activeKey)` with one atomic `valueOperations.getAndDelete(activeKey)` (Spring Data Redis's `GETDEL` wrapper — confirmed available, you're on Spring Boot 3.5.0 → Spring Data Redis 3.5.x, `getAndDelete` has been there since 2.6).
2. **`RefreshTokenServiceTest.java`** — updated 3 existing test mocks from `.get()` to `.getAndDelete()` to match (`rotateRefreshToken_success`, `_reuseAttack_revokesAll`, `_invalidToken_throwsException`). `revokeToken`/`revokeAllUserTokens` tests untouched since those methods weren't changed.

**Verify locally:** `mvn test -Dtest=RefreshTokenServiceTest` — this is the branch I'd most want you to actually run given it's touching auth-critical Redis interaction. Lowest structural risk of the 8, but worth confirming Mockito mock matches your exact `StringRedisTemplate` bean setup.

---

## 5. `security/breached-password-check`
**Problem:** Password policy was min-8-chars only, nothing checked against known-breached passwords.

**Parts:**
1. **New file** `service/BreachedPasswordService.java` — SHA-1 hashes the password, sends only the first 5 hex chars to HIBP's range API (k-anonymity), checks the response for the remaining 35-char suffix. Uses `java.net.http.HttpClient` (JDK built-in, no new Maven dependency). Fails open on any error.
2. **`AuthService.java`** — inject the service, add the check in `registerUser()` before account creation.
3. **`application.yml`** — new `security.password.breach-check.enabled/timeout-ms`.
4. **`.env.example`** — document `PASSWORD_BREACH_CHECK_ENABLED/_TIMEOUT_MS`.
5. **CI workflows** — set `PASSWORD_BREACH_CHECK_ENABLED=false` in generated test env (determinism + don't hammer a third party during load tests).

**Verify locally:** this one needs actual outbound internet to `api.pwnedpasswords.com` to test the positive path — try registering with `password123` (well-known breached password) and confirm rejection; then with a random strong password and confirm success. Also worth confirming your network/firewall allows outbound HTTPS to that host from wherever this runs.

---

## 6. `security/generic-registration-response`
**Problem:** `registerUser()` disclosed "Email is already registered" — account-existence enumeration via email.

**Parts:**
1. **`AuthService.java`** — when email already exists, `registerUser()` now returns normally (as if successful) instead of throwing, and logs a new `REGISTRATION_DUPLICATE_EMAIL_ATTEMPT` audit event instead. Username-taken is left throwing an explicit error, deliberately (documented in a docstring — usernames are meant to be publicly checkable, unlike emails).

**This is a product decision as much as a code change** — flagging again in case you'd rather keep the explicit email error for UX reasons and accept the minor enumeration risk. No test file existed for `registerUser` to update.

**Verify locally:** register with an already-used email and confirm you get a "success" response but no duplicate row is created in `users`; check `audit_logs` for the new event type.

---

## 7. `feature/shared-with-me-view`
**Problem:** No UI path for a recipient to see files shared with them.

**Parts (largest diff of the 8 — review this one most carefully):**
1. **`FileShareRepository.java`** — new `findBySharedWithIdAndFileNotDeleted(userId, pageable)` query, `JOIN FETCH` on `file` and `sharedBy` to avoid N+1.
2. **New file** `dto/SharedFileResponse.java` — id, name, size, mime, checksum, uploadedAt, sharedByUsername, permissionType, sharedAt.
3. **`FileService.java`** — new `listSharedWithMe(userId, pageable)`, `@Transactional(readOnly = true)`, maps `FileShare` → `SharedFileResponse`.
4. **`FileController.java`** — new `GET /api/v1/files/shared-with-me`.
5. **`FileServiceTest.java`** — new test asserting the mapping (sharer username, permission, shared-at).
6. **`frontend/index.html`** — new "Shared with Me" card (table + pagination + empty state) alongside "My Files".
7. **`frontend/js/api.js`** — `listSharedWithMe(page, size)`.
8. **`frontend/js/app.js`** — state, load/render functions, pagination wiring; download button reuses the existing `handleFileDownload` unchanged (the download endpoint already authorized recipients correctly via `findAccessibleFile`).
9. **`tests/e2e/specs/sharing.spec.ts`** — updated the now-stale comment documenting the old gap; left the spec itself still driving User B via API (re-pointing it at the new UI is a reasonable separate follow-up).

**Verify locally:** `mvn test -Dtest=FileServiceTest`, then manually: share a file between two test accounts, log in as recipient, confirm it appears in the new card and downloads correctly. This is the branch most likely to have a real frontend bug I can't catch without a browser — worth an actual click-through.

---

## 8. `infra/remote-staging-oracle-a1` — **SKIPPED per your credit-card constraint**

Kept the branch/commit in the bundle in case you change your mind later, but not recommending you merge it. For the record, the one genuinely free, no-card path if you ever want to revisit this: self-host on a spare machine you already own (old laptop, Raspberry Pi, whatever) and expose it with **Cloudflare Tunnel** (free, no card, no inbound firewall ports needed). It's not "cloud," and home bandwidth will skew the latency numbers Gatling reports, so it wouldn't give you trustworthy SLA baselines — but it would genuinely exercise the untested remote-mode branch of `load-tests.yml` at $0. Not proposing you do this now, just flagging it exists if the deferred item ever becomes worth revisiting.

---