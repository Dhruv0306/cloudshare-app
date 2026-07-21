# CloudShare — Adversarial Audit Report
**Source of truth:** `dhruv0306-cloudshare-app` (uploaded snapshot, post `security/xff-trust-chain` era — `ClientIpResolver` already injected into all 5 filters)
**Reviewers:** Ethical Hacker · Senior QA Automation Engineer · Chaotic User
**Scope:** Spring Boot 3.5 backend, PostgreSQL 17/Flyway, dual-Redis, MinIO, ClamAV, Nginx gateway, vanilla-JS SPA

> **Headline:** This codebase is materially further along than a typical portfolio project — the three previously-documented CRITICALs (step-up token type-confusion, host-network container exposure, spoofable client IP) all appear **fixed** in this snapshot. The findings below are the *next* layer down: TOCTOU races in single-use enforcement and download-limit checks, fail-open behavior under Redis memory pressure, and a defense-in-depth inconsistency in KEK shape enforcement. Nothing here is "the app is insecure" — it's "here's where a determined attacker or a flaky network still wins."

---

## STEP 0 — Architectural & Deep-Dive Review

### 0.1 System Map

```
Internet
   │  :80 → 301 redirect, :443 TLS1.2/1.3
   ▼
┌─────────────────────────────────────────────┐
│ Nginx "gateway" (sole edge ingress)          │
│  - CSP / HSTS / X-Frame-Options / nosniff    │
│  - overwrites X-Real-IP / X-Forwarded-For    │
│  - proxies /api/* and /actuator/health only  │
│  - serves SPA static assets                  │
└───────────────┬───────────────────────────────┘
                │ internal Docker bridge network (no host port bindings)
   ┌────────────┼─────────────┬───────────┬───────────┐
   ▼            ▼             ▼           ▼           ▼
┌─────┐   ┌───────────┐  ┌──────────┐ ┌───────┐  ┌─────────┐
│ app │──▶│ Postgres17│  │ClamAV    │ │MinIO  │  │ (n/a)   │
│Spring│  │ (Flyway)  │  │daemon    │ │(S3 API│  │         │
│Boot │   └───────────┘  └──────────┘ │console│  └─────────┘
│3.5  │──▶┌──────────────┐┌──────────────┐  off) │
└─────┘   │Redis cache-  ││Redis security│└───────┘
          │aside (LRU,   ││(noeviction,  │
          │256MB)        ││256MB)        │
          └──────────────┘└──────────────┘
```

Confirmed from `docker-compose.yml`: only `gateway` publishes host ports (`80`, `443`). `app`, `db`, `cache-aside`, `cache-security`, `clamav`, `storage` have **no** `ports:` mapping — they're reachable only on the internal Compose network. This is the fix for the previously-documented "backing services on host network" CRITICAL. MinIO console is explicitly disabled (`MINIO_BROWSER=off`).

### 0.2 Data Flow: Upload Pipeline
`multipart POST /api/v1/files/upload` →
1. Buffer to disk temp file (bounded memory)
2. ClamAV scan (`clamAvService.scan`)
3. Apache Tika magic-byte MIME detection
4. Dangerous-MIME / dangerous-extension blocklist
5. Extension↔MIME compatibility check (`MediaTypeFactory`)
6. Polyglot markup sniff for image/PDF uploads (`<script`, `<?php`, `<html`)
7. Filename sanitized to `[a-zA-Z0-9._- ]` allowlist
8. AES-256-GCM streaming encryption with fresh FEK + random 12-byte IV, SHA-256 checksum computed in the same pass (`DigestInputStream`)
9. Encrypted blob → MinIO/local storage
10. FEK wrapped via AESWrap under current KEK version
11. Metadata persisted; audit log write is fail-secure (exception aborts the whole upload)

### 0.3 Data Flow: Auth / RTR Lifecycle
- Access token: 15 min TTL JWT, `type:"access"` claim, unique `jti`.
- Refresh token: opaque UUID, **not** a JWT — stored server-side in Redis Security as `refresh:active:<id>` (rotatable) + `refresh:metadata:<id>` (persists post-rotation) + `refresh:family:<userId>` (Set, sliding 7-day TTL). Delivered via `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`.
- Rotation uses atomic `GETDEL` on the active key — this correctly prevents two concurrent refresh calls from both succeeding on the same token (only one wins the delete).
- Reuse detection: if a token id shows up in `metadata` + is still a member of the `family` set but is *not* in `active` (i.e., it was already rotated away), the entire family is revoked and a `SecurityException` is thrown — textbook RTR breach detection.
- Access-token logout: JTI written to `blacklist:token:<jti>` for its remaining TTL; checked on every request by `JwtAuthenticationFilter`.

### 0.4 Data Flow: Envelope Encryption Boundary (FEK/KEK)
- Every file gets a unique random AES-256 FEK.
- FEK is wrapped with `AESWrap` under a versioned master KEK (`CryptoProperties.keks[version]` or legacy `masterKek` for v1).
- `SecretsStartupValidator` enforces **fail-closed** KEK shape (must Base64-decode to exactly 32 bytes) unless the operator opts in to `crypto.kek.allow-raw-passphrase=true`.
- Downloads: ciphertext is unwrapped, decrypted **fully to a temp file first**, with `Cipher.doFinal()` (GCM tag verification) completed *before* any byte is exposed to the HTTP response stream via `DeleteOnCloseInputStream`. This is the correct pattern — it avoids the classic `CipherInputStream`-with-GCM pitfall where unauthenticated plaintext leaks to the client before the tag check fails.

### 0.5 Runtime Assumptions
- ClamAV reachable as a sidecar daemon (`CLAMAV_HOST`/`PORT`) — upload blocks until scan completes; no async/fire-and-forget path.
- MinIO used as the pluggable S3-compatible backend in Compose; local filesystem and future AWS S3 are alternate `StorageService` implementations.
- Postgres `audit_logs` table is monthly range-partitioned (`V3__Add_Audit_Logs_Partitions.sql`) — assumes an out-of-band job/runbook to pre-create future-month partitions (not present in this snapshot as a scheduler — see QA §2.6).
- Single-instance deployment assumed for `@Scheduled` jobs (`FilePurgeScheduler`, `LinkCleanupScheduler`) — no distributed lock (e.g., ShedLock). Fine for one Oracle Cloud VM; would double-fire on any horizontal scale-out.

---

## SECTION 1 — Ethical Hacker Security Audit

### 1.1 [HIGH] TOCTOU race in step-up token single-use enforcement
**File:** `StepUpAuthenticationFilter.java`

**Flaw:** Single-use enforcement is check-then-set, not atomic:
```java
boolean isBlacklisted = ... securityRedisTemplate.hasKey(blacklistKey);   // READ
...
if (isBlacklisted || ... ) { reject }
...
securityRedisTemplate.opsForValue().set(blacklistKey, "blacklisted", ttl); // WRITE (later, after doFilter setup)
```
Between the `hasKey` read and the `set` write, nothing prevents a second, concurrently-arriving request bearing the *same* step-up JWT from also passing the `hasKey` check (still `false`) before the first request's `set` lands. Redis `GET`/`SET` here are two round-trips, not a single atomic op.

**Exploitation:**
1. Attacker (or malicious script) obtains one valid step-up token (5-min TTL, single MFA prompt).
2. Fire N parallel requests to different `/api/v1/admin/*` endpoints with the same `X-StepUp-Token` at the same instant (trivial with `curl` + `xargs -P`, or a browser `Promise.all`).
3. All N requests race the same blacklist check; several land before any blacklist write completes and are treated as valid step-up-authenticated admin actions.
4. Net effect: the "single-use" MFA step-up guarantee degrades to "used a handful of times within a tight race window" rather than exactly once.

**Fix:** Make the single-use claim atomic with `SET key value NX EX ttl` (or `SETNX` + `EXPIRE`) performed *first*, and treat "key already existed" as the rejection signal — don't separately GET then SET:
```java
Boolean firstUse = securityRedisTemplate.opsForValue()
        .setIfAbsent(blacklistKey, "blacklisted", Duration.ofMillis(remainingTimeMs));
if (!Boolean.TRUE.equals(firstUse) || !tokenProvider.validateStepUpToken(...)) {
    // reject — token already consumed or invalid
}
```
Do the `setIfAbsent` claim *before* running `validateStepUpToken`'s business checks, so the claim itself is the race-free gate.

---

### 1.2 [HIGH] Step-up single-use enforcement fails open on Redis errors
**File:** `StepUpAuthenticationFilter.java`

**Flaw:** The blacklist write is wrapped in try/catch that only logs — it never blocks the request:
```java
try {
    securityRedisTemplate.opsForValue().set(blacklistKey, "blacklisted", ...);
} catch (Exception e) {
    log.error("Failed to blacklist step-up token jti={}", jti, e);
    // request proceeds regardless
}
```
If the Redis Security instance is under memory pressure (it's configured `noeviction`, so it starts rejecting writes with `OOM command not allowed` once full — see §3.4 for how an attacker can *cause* this), the blacklist write throws, is swallowed, and the admin action proceeds. Worse: the token is **not** blacklisted, so it remains valid and reusable for its full remaining 5-minute TTL, silently defeating the "single-use" security boundary exactly when the system is under the most stress.

**Fix:** Fail closed. If the write to enforce single-use fails, the request must not be treated as step-up-authenticated:
```java
try {
    Boolean firstUse = securityRedisTemplate.opsForValue().setIfAbsent(...);
    if (!Boolean.TRUE.equals(firstUse)) { reject(); return; }
} catch (Exception e) {
    log.error("Step-up enforcement unavailable — failing closed", e);
    response.setStatus(503);
    response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Security state store unavailable.\"}}");
    return;
}
```

---

### 1.3 [MEDIUM] Runtime KEK-shape enforcement is weaker than startup validation (defense-in-depth gap)
**Files:** `SecretsStartupValidator.java` vs `EncryptionService.getMasterKek()`

**Flaw:** `SecretsStartupValidator.checkKekShape()` throws `IllegalStateException` for a non-32-byte KEK *unless* `crypto.kek.allow-raw-passphrase=true` is explicitly set. But `EncryptionService.getMasterKek(version)` — the method actually used at encrypt/decrypt time — has no awareness of that flag at all:
```java
if (keyBytes.length != 32) {
    log.warn(...);
    keyBytes = MessageDigest.getInstance("SHA-256").digest(keyBytes); // always digests, regardless of allow-raw-passphrase
}
```
Today this is masked because startup validation runs first and aborts boot for any misconfigured KEK. But it's a latent landmine: any future code path that resolves a KEK version *without* having gone through `SecretsStartupValidator` (e.g., a hot-reloaded KEK during zero-downtime rotation, or a KEK added via `application-rekey-job.yml` for the separate rekey-job profile, which is a distinct process invocation with its own startup) will silently digest a malformed key into an unexpected 256-bit value rather than failing loudly. Silent key-derivation drift is exactly the failure mode the KEK-rotation design is supposed to prevent — a file encrypted under a "digested" KEK becomes unrecoverable if the raw-vs-digested behavior isn't perfectly mirrored between the process that wrapped it and the process that later unwraps it.

**Fix:** Inject the `allowRawPassphrase` flag into `EncryptionService` and make the runtime behavior match the startup contract exactly — throw, don't digest, unless explicitly allowed:
```java
if (keyBytes.length != 32) {
    if (!cryptoProperties.getKek().isAllowRawPassphrase()) {
        throw new IllegalStateException("KEK version " + v + " is not 32 bytes and raw-passphrase fallback is disabled.");
    }
    keyBytes = digest(keyBytes);
}
```

---

### 1.4 [MEDIUM] No anti-replay window on TOTP codes
**File:** `MfaService.java`

**Flaw:** `codeVerifier.isValidCode(secret, code)` (samstevens `DefaultCodeVerifier`) validates against the current time-step window (and its default adjacent-step tolerance) but there is no server-side "this code/time-step has already been consumed for this user" tracking. A TOTP code observed once (shoulder-surfing, screen share, compromised log line, malicious browser extension) is valid for reuse by anyone within its ~30–90s window against **both** `/mfa/verify` and `/mfa/step-up`.

**Fix:** Track `mfa:used:<userId>:<timeStepCounter>` in Redis Security with a short TTL (~90s) and reject if already present, immediately after a successful `isValidCode` check:
```java
String usedKey = "mfa:used:" + userId + ":" + timeProvider.getTime() / 30;
if (!Boolean.TRUE.equals(securityRedisTemplate.opsForValue().setIfAbsent(usedKey, "1", Duration.ofSeconds(90)))) {
    return false; // code already consumed this window
}
```

---

### 1.5 [LOW] Share-code existence is distinguishable from wrong-password on public links
**File:** `ShareService.downloadPublicLink()`

**Flaw:** A nonexistent share code returns `404 NOT_FOUND` ("Link code does not exist") while a valid code with a wrong/missing password returns `401 UNAUTHORIZED` ("Password required but missing/invalid"). This lets an attacker distinguish "this 8-character code exists" from "this code doesn't exist" — useful only in combination with some other leak of partial share codes (e.g., referrer logs, browser history sync), since 62^8 (~218 trillion) makes blind brute force infeasible on its own. Low severity given the keyspace, but free to fix and matches the registration-enumeration mitigation already applied elsewhere in `AuthService`.

**Fix:** Return a uniform `404`-shaped response for both "code doesn't exist" and "code exists but no/expired/limit-reached", reserving the password-specific `401` only *after* the caller has proven they at least know a password was required (or just merge both into a generic `ACCESS_DENIED` regardless of cause) — matching the "don't leak existence" pattern already used for email enumeration in `AuthService.registerUser`.

---

### 1.6 [LOW] Admin listing endpoints accept unbounded page size
**File:** `AdminController.java` (`listUsers`, `getAuditLogs`)

**Flaw:** `Pageable pageable` is bound directly from query params with no `@PageableDefault(maxPageSize=...)` or global `maxPageSize` config visible in `application.yml`. A caller (an admin account, or anyone who compromises one) can request `?size=2000000` and force a very large single query/response — cheap self-inflicted DoS or memory pressure on the `app` JVM, and a large `audit_logs` scan defeats the partitioning's performance intent.

**Fix:**
```java
@PageableDefault(size = 25, sort = "createdAt")
```
plus a global cap:
```yaml
spring.data.web.pageable.max-page-size: 100
```

---

### 1.7 [INFORMATIONAL] Upload/download plaintext transits local disk unencrypted
**Files:** `FileService.uploadFile`, `FileService.downloadFile`, `ShareService.downloadPublicLink`

Not a vulnerability per se (ClamAV needs a real file to scan; GCM needs the tag verified before releasing plaintext), but worth documenting explicitly for the threat model: plaintext temp files exist briefly on the container's local filesystem during both upload (pre-encryption) and download (post-decryption, pre-stream). If the container filesystem is ever shared/mounted insecurely, or if `/tmp` isn't wiped on an unclean shutdown, this is a small residual exposure window. It's also the mechanism behind the storage-exhaustion finding in §3.6. Recommend confirming temp directories are `tmpfs` (RAM-backed) in the runbook, and that `Files.createTempFile` uses a container-local, non-persisted volume.

---

## SECTION 2 — QA Tester Flow Audit

### 2.1 [HIGH] Public-link download-limit is a TOCTOU race, not an atomic decrement
**File:** `ShareService.downloadPublicLink()`, `ShareLink.java`

**Flaw:**
```java
if (shareLink.getDownloadLimit() != null && shareLink.getDownloadCount() >= shareLink.getDownloadLimit()) {
    throw new AccessDeniedException("Download limit reached");
}
shareLink.setDownloadCount(shareLink.getDownloadCount() + 1);
shareLinkRepository.save(shareLink);
```
This read-check-increment happens inside a single `@Transactional` method, but there's **no row-level lock** (`@Lock(PESSIMISTIC_WRITE)` / `SELECT ... FOR UPDATE`) and **no optimistic-locking `@Version`** column on `ShareLink`. Under Postgres's default `READ COMMITTED` isolation, two concurrent transactions each read the same `downloadCount` before either commits its increment — both see themselves as "under the limit" and both proceed.

**Reproduction (QA repro steps):**
1. Owner creates a public link with `downloadLimit: 1`.
2. Fire 10 concurrent `GET /api/v1/shares/link/{code}/download` requests (e.g., `ab -n 10 -c 10`, or `Promise.all` of 10 fetches).
3. Observe: more than 1 request receives `200 OK` with the file body; `download_count` in Postgres ends up incremented by however many transactions raced (bounded by app thread-pool concurrency, not by the intended limit of 1).

**Fix:** Use a pessimistic lock on the read, or an atomic conditional update:
```sql
UPDATE share_links
SET download_count = download_count + 1
WHERE id = :id AND (download_limit IS NULL OR download_count < download_limit)
RETURNING *;
```
via a `@Modifying @Query` in `ShareLinkRepository`, then treat "0 rows updated" as limit-exceeded/expired — this pushes the check-and-increment into a single atomic statement instead of two round-trips guarded only by Java-level `@Transactional`.

---

### 2.2 [MEDIUM] Rate limiter fails open, and it fails open exactly when abuse is most likely
**File:** `RateLimiterService.isAllowed()`

```java
} catch (Exception e) {
    log.error(...);
    return true; // Fallback to true (allow request) ...
}
```
This is a deliberate availability trade-off (documented in-line), but from a pure flow-break perspective: any Redis Security hiccup — network blip, `noeviction` OOM (see §3.4), Lua script compile error, connection pool exhaustion — makes **every rate limit in the app (auth, MFA, upload, public-link, general) evaluate to "allowed" simultaneously**, with no partial degradation. QA should have an explicit test asserting the *opposite* behavior is intentional (currently there's no test asserting what happens under a Redis outage at all, per the file list — `RateLimitingFilterTest` mocks the service, not Redis failure paths at the `RateLimiterService` layer).

**Suggested test to add:** `RateLimiterServiceTest` with a `StringRedisTemplate` mock that throws on `execute(...)`, asserting `isAllowed()` returns `true` and logs at `ERROR`, so this is a documented, tested decision rather than an implicit one.

---

### 2.3 [MEDIUM] Frontend has no visible circuit-breaker for the refresh-token queue
**File:** `frontend/js/api.js`

`request()` on a 401 enqueues the original request and kicks off `performTokenRefresh()` if not already in flight; the queue is resolved/rejected once. This is correctly deduplicated for concurrent 401s. However: if `performTokenRefresh()` itself returns a **non-401 error** (e.g., the `/refresh` endpoint 500s, or the network drops mid-request), the `.catch()` path clears tokens and fires `onAuthFailureCallback`, which is fine — but there's no distinction in the UI layer (not shown in this file, presumably in `app.js`) between "your session expired, please log in" and "the server is temporarily down, please retry." A momentary backend blip during token refresh will force **every open tab** into a full logout, even though the refresh cookie itself may still be perfectly valid. Recommend the callback carry the failure reason (`network` vs `expired`) so `app.js` can retry-with-backoff on transient failures instead of unconditionally logging the user out.

### 2.4 [LOW] `RateLimitingFilter` public-link path parsing is brittle
**File:** `RateLimitingFilter.java`
```java
String remaining = path.substring(20); // Length of "/api/v1/shares/link/"
```
The magic number `20` is a silent coupling to the literal string length. If the route is ever renamed (e.g., adding an API version prefix, or nesting under a different base path) this becomes an off-by-N bug that either throws `StringIndexOutOfBoundsException` (caught nowhere in this method — it would 500 instead of 429/200) or silently rate-limits the wrong substring as the "share code." Recommend deriving the prefix length from a shared constant instead of a magic number, and adding a QA test that intentionally breaks route naming to confirm graceful degradation rather than a 500.

### 2.5 [LOW] Scheduler exceptions are swallowed per-file but the job has no completion metric
**File:** `FilePurgeScheduler.java`
Per-file failures are caught and logged (good — one bad file doesn't kill the whole run), but there's no counter/metric emitted for "N files failed to purge this run." Over time, silently-failing purges (e.g., a MinIO object that's already gone, or a permissions issue) accumulate DB rows that never get cleaned up, and nobody would notice without grepping logs. Given the project already has Micrometer/Prometheus wired up (per README), recommend emitting `cloudshare.purge.failures` / `cloudshare.purge.success` counters here for observability parity with the rest of the stack.

### 2.6 [LOW] No visible scheduler for future audit-log partition creation
**Files:** `V3__Add_Audit_Logs_Partitions.sql`, scheduler package
The audit log table is documented as monthly range-partitioned, but the scheduler package contains only `FilePurgeScheduler` and `LinkCleanupScheduler` — no `AuditPartitionScheduler` or equivalent. If partitions aren't pre-created for future months via an out-of-band cron/runbook, audit log writes will start failing once the current partition's range is exhausted — and per the documented fail-secure stance, **audit failures abort the parent operation** (upload, download, share, delete all throw `RuntimeException` if `auditLogService.log()` fails). This means a missed partition-maintenance window doesn't just lose audit trail — it takes down uploads/downloads/sharing entirely. Recommend a `pg_partman`-style scheduled job or an explicit `AuditPartitionScheduler` that pre-creates N months ahead, with an alert if it ever falls behind.

---

## SECTION 3 — Chaotic User Audit

### 3.1 [HIGH] Exceed public-link download limits via concurrency
Direct restatement of §2.1 from the abuse angle: a "1 download, self-destructing" link is not actually self-destructing under concurrent access. A malicious recipient who wants to exfiltrate a file meant to be viewed once (or re-share it past its configured limit) just needs to fire the download request several times in parallel instead of sequentially. No special tooling required — a bookmarklet with `Promise.all([...Array(20)].map(() => fetch(url)))` is enough.

### 3.2 [MEDIUM] Replay a captured step-up token across parallel admin calls
Restatement of §1.1 from the abuse angle: a malicious insider (or someone who captured a step-up token via XSS/logging/proxy) doesn't need to guess anything — they just need to fan the same token out across several admin requests simultaneously to get more than "one" privileged action out of a single MFA prompt, and — per §1.2 — if they can additionally nudge Redis Security into refusing writes, the token stays valid and reusable for its **entire remaining 5-minute TTL**.

### 3.3 [LOW] Spam uploads to exhaust ClamAV/Tika throughput ahead of the rate limiter
Upload is rate-limited at 10/min per user (or per-IP if unauthenticated — but `/upload` requires auth, so effectively per-user). Within that allowance, `client_max_body_size 100M` at Nginx means an attacker can push 10×100MB = ~1GB/min of attacker-controlled bytes through the full pipeline: disk buffer → ClamAV scan → Tika detect → GCM encrypt → MinIO store, all before the next window resets. This is within "normal" rate-limit tolerances by design, but combined with §3.6 (disk exhaustion) and the fact that ClamAV scanning is synchronous and blocking per-request, a modest botnet (multiple accounts, each independently rate-limited) can still meaningfully saturate the single ClamAV daemon sidecar, since nothing in the pipeline limits *global* concurrent scans — only per-user request rate. Recommend a bounded semaphore/thread-pool in front of `ClamAvService.scan()` so ClamAV concurrency is capped independent of how many distinct users are uploading simultaneously.

### 3.4 [MEDIUM] Force Redis Security into `noeviction` OOM to blind rate limiting AND step-up single-use enforcement simultaneously
**Files:** `docker-compose.yml` (`cache-security` → `noeviction`, `256mb`), `RateLimiterService`, `StepUpAuthenticationFilter`, `RefreshTokenService`

This chains three independent findings into one abuse path:
1. Every login creates `refresh:active:*`, `refresh:metadata:*` keys and adds to a `refresh:family:<userId>` **Set** whose TTL is refreshed (extended) on every subsequent token creation — meaning a user (or script) that logs in and refreshes repeatedly over the 7-day window keeps growing/reviving that family's footprint.
2. Every rate-limit check writes sorted-set members (`ZADD ... now-seq`) that live for the window duration — high request volume against multiple endpoints from multiple accounts/IPs multiplies key count fast.
3. `cache-security` is configured `noeviction` with a `256mb` cap and **no host-level alerting shown in this snapshot** for approaching that ceiling.

**Reproduction concept:**
1. Register many throwaway accounts (registration itself isn't described as CAPTCHA'd or otherwise abuse-gated beyond the 5/min-per-IP auth rate limit — distributable across IPs/time).
2. Script each account to log in and hit `/api/v1/auth/refresh` and various rate-limited endpoints continuously, maximizing distinct Redis keys (varying paths, share codes, etc., since limiter keys are per-path/per-identifier).
3. As `cache-security` approaches 256MB under `noeviction`, further writes start throwing `OOM command not allowed`.
4. Per §3.2/§1.2, this specifically **disables** step-up single-use enforcement (fails open, swallowed exception) and, per §2.2, **disables all rate limiting app-wide** (fails open, by design) at exactly the same moment — turning a resource-exhaustion attack into a full authz/rate-limit bypass.

**Fix:**
- Add a memory-pressure health signal (e.g., `INFO memory` polled by an Actuator health indicator) that flips the app into a degraded/maintenance mode — or at minimum alerts — before Redis Security hits its ceiling, rather than discovering it via silent fail-open behavior.
- Bound `refresh:family:<userId>` set growth (cap membership size / prune on each rotation instead of only on breach detection).
- Consider `allkeys-lru` for the *rate-limiting* keys specifically (a separate logical DB/instance from the blacklist/step-up keys, which genuinely need `noeviction` semantics) so capacity pressure degrades rate-limit precision rather than blacklist correctness.

### 3.5 [LOW] TOTP code reuse (restated from §1.4, abuse framing)
A malicious user who screen-shares "look, let me show you my authenticator" and reads the code aloud, or who captures it via a compromised proxy before it's consumed, can hand that same 6-digit code to a second device and both will succeed within the validity window — no lockout, no lockout counter, nothing distinguishing "used once already."

### 3.6 [MEDIUM] Storage/disk exhaustion via concurrent large-file downloads
**Files:** `FileService.downloadFile`, `ShareService.downloadPublicLink`

Both download paths fully decrypt to a **local temp file** before streaming (correct for GCM-tag safety, per §0.4/§1.7). But this means each in-flight download consumes disk equal to the full plaintext file size (up to the 100MB Nginx cap) for its duration. A malicious/curious user who:
1. Uploads several near-100MB files, or shares one with a public link, and
2. Fires many concurrent downloads (own files, shared files, or public-link downloads — public links only cost an anonymous GET, no auth required, only rate-limited at 30/min per link + 100/min per IP, which a small pool of source IPs comfortably satisfies)

...can drive the container's temp storage toward exhaustion, at which point `Files.createTempFile` starts throwing `IOException`, which the download handlers catch and convert into a generic 500 (`GlobalExceptionHandler`'s catch-all) — a real, user-triggerable denial of service against *other* users' legitimate downloads, since temp space is shared across the JVM/container. Recommend: cap concurrent decrypt-in-flight downloads per container (bounded semaphore), stream-verify-then-stream (chunked authenticated decryption in fixed-size sealed segments) instead of whole-file temp buffering if this becomes a real constraint, or at minimum mount the temp directory with a hard quota separate from the rest of the container filesystem so exhaustion degrades gracefully rather than taking down unrelated requests.

### 3.7 [LOW] Manipulate client-side state to bypass share-password UI, not the API
**File:** `frontend/js/app.js` / `api.js`

Because the actual enforcement (`passwordEncoder.matches`) lives server-side in `ShareService.downloadPublicLink`, there's no way to bypass a password-protected link purely via DevTools/localStorage tampering — confirmed the backend is the source of truth here, which is correct. The only client-manipulable surface is UX (e.g., forcing the "download" button to enable before a password is entered), which just produces a `401 InvalidSharePasswordException` server-side — cosmetic only, not a security bypass. Noted here as a **negative finding** (checked, not exploitable) rather than a vulnerability, since the prompt specifically asked this to be probed.

---

## Summary Table

| # | Section | Finding | Severity |
|---|---------|---------|----------|
| 1.1 | Hacker | Step-up token single-use check is a TOCTOU race | High |
| 1.2 | Hacker | Step-up single-use enforcement fails open on Redis error | High |
| 1.3 | Hacker | Runtime KEK-shape enforcement weaker than startup validator | Medium |
| 1.4 | Hacker | No TOTP anti-replay tracking | Medium |
| 1.5 | Hacker | Share-code existence distinguishable from wrong password | Low |
| 1.6 | Hacker | Unbounded `Pageable` size on admin endpoints | Low |
| 1.7 | Hacker | Plaintext temp files during up/download (informational) | Info |
| 2.1 | QA | Public-link download limit is a TOCTOU race | High |
| 2.2 | QA | Rate limiter fails open on Redis errors (undertested) | Medium |
| 2.3 | QA | No frontend distinction between "expired" vs "backend down" on refresh failure | Medium |
| 2.4 | QA | Brittle magic-number path parsing in rate limiter | Low |
| 2.5 | QA | Scheduler swallows per-file failures with no metric | Low |
| 2.6 | QA | No scheduler for future audit-log partition creation | Low |
| 3.1 | Chaos | Exceed download limits via concurrency (= 2.1) | High |
| 3.2 | Chaos | Replay captured step-up token across parallel calls (= 1.1/1.2) | Medium–High |
| 3.3 | Chaos | Global ClamAV concurrency uncapped across users | Low |
| 3.4 | Chaos | Redis `noeviction` OOM blinds rate limiting + step-up enforcement together | Medium |
| 3.5 | Chaos | TOTP code reuse (= 1.4) | Low |
| 3.6 | Chaos | Disk exhaustion via concurrent large-file downloads | Medium |
| 3.7 | Chaos | Client-side password bypass attempt — confirmed not exploitable | — (negative finding) |

**Recommended fix order**, matching your existing branch-prefix convention:
1. `security/stepup-atomic-single-use` — §1.1 + §1.2 together (same filter, same fix shape: `setIfAbsent` + fail-closed)
2. `security/download-limit-atomic-increment` — §2.1/§3.1 (single highest-impact business-logic bypass)
3. `security/redis-capacity-isolation` — §3.4 (split rate-limit keys from blacklist keys, or add memory-pressure alerting)
4. `security/kek-runtime-shape-parity` — §1.3
5. `security/totp-anti-replay` — §1.4/§3.5
6. Lower-severity QA/hardening items (§1.5, §1.6, §2.4, §2.5, §2.6, §3.3, §3.6) as a batch cleanup pass

---

## Release Targeting (from v1.1.0)

**Target: v1.1.1 for the two HIGH-severity atomic-fix items, v1.2.0 for the full remediation batch.**

### Reasoning

- **v1.1.1 (patch)** — §2.1/§3.1 (download-limit race) and §1.1/§1.2 (step-up single-use race + fail-open) are pure bug fixes: no API contract changes, no new config, no new behavior visible to a well-behaved client. They just make existing guarantees actually hold. That's textbook patch-release territory, and since both are genuine security bypasses currently live in v1.1.0, ship this fast and don't bundle it with anything else.

- **v1.2.0 (minor)** — the rest of the batch introduces new behavior/config surface, which nudges it past "patch":
  - §1.4/§3.5 TOTP anti-replay adds a new Redis key pattern and a new rejection path (behavior change for legitimate double-submits, however rare)
  - §1.3 KEK runtime/startup parity change alters what `EncryptionService` does when `allow-raw-passphrase` is unset (could change boot/runtime behavior for anyone relying on the current silent-digest fallback)
  - §3.4 Redis capacity isolation likely means either a new Redis logical DB/config knob or new health-indicator wiring
  - §1.6/§2.4/§2.5/§2.6 are config/observability additions (`max-page-size`, metrics, a new scheduler)

### Branch → Version Mapping

| Branch | Version |
|---|---|
| `security/stepup-atomic-single-use` | v1.1.1 |
| `security/download-limit-atomic-increment` | v1.1.1 (or v1.1.2 if shipped as a separate tagged fix) |
| `security/redis-capacity-isolation` | v1.2.0 |
| `security/kek-runtime-shape-parity` | v1.2.0 |
| `security/totp-anti-replay` | v1.2.0 |
| Low-severity batch (§1.5, §1.6, §2.4, §2.5, §2.6, §3.3, §3.6) | v1.2.0 |

### Open Question
Whether v1.1.1/v1.1.2 should be quiet patch tags or get a proper GitHub Release (like v1.0.0 did). Given these two close real authz-adjacent bypasses, a short release note is worth considering even if terse — e.g., "Fixed: public share links could exceed configured download limits under concurrent access; fixed: MFA step-up tokens could be reused under specific timing/failure conditions" — so there's something to point to later.
