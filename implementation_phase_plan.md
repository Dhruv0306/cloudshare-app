## H1 — `security/fail-loud-permission-cache`

**Problem:** `ShareService.evictPermissionsCache` swallows Redis delete failures with a log-only catch. If Redis errors at the exact moment of a revoke, the DB-level revocation succeeds but the cached permission hash survives for up to an hour — a revoked user keeps read access with no retry, no alert, no compensating check.

**Plan:**
1. `ShareService.evictPermissionsCache`: on delete failure, write a short-TTL bypass marker (`cache:permissions:bypass:<fileId>`, ~10 min TTL) instead of just logging.
2. `FileService.verifyFileAccess`: check for the bypass marker before trusting the cached hash. If present, attempt a self-healing retry of the delete; if that also fails, skip the cache entirely and authorize from a fresh DB query for this request only (don't repopulate the cache while the bypass is active).
3. Tag the failure log distinctly (`PERMISSION_CACHE_EVICTION_FAILED`) so it's greppable/alertable later.
4. Tests: `ShareServiceTest` — delete-throws → bypass marker set. `FileServiceTest` — bypass marker present + stale hash still grants access to a revoked user → `verifyFileAccess` throws anyway (DB wins over stale cache); retry-succeeds path clears the marker and resumes normal caching.

**Files:** `ShareService.java`, `FileService.java`, `ShareServiceTest.java`, `FileServiceTest.java`.

---

## H2 — resolved as a consequence of C2 (COMPLETED)

**Problem:** general-API rate limiting falls back to raw IP for unauthenticated callers; combined with C2 (spoofable `X-Real-IP` if `:8080` was reachable directly), the IP-keyed buckets weren't trustworthy.

**Resolution:** No standalone code change. The security boundary was successfully established under C2 by removing public port exposures in `docker-compose.yml` for all backend services (including the app container `app:8080`). External clients can only access the services through the Nginx gateway, which overrides the `X-Real-IP` header with the true connection source IP (`$remote_addr`). 

**Verification & Documentation:**
1. Added `test_gateway_ip_spoofing_mitigation` regression test in `tests/api_test.py` verifying that direct requests to the backend container fail from outside the Docker network and that spoofed `X-Real-IP` headers sent through the gateway are handled cleanly (overwritten).
2. Documented the Client IP resolution architecture and gateway trust assumptions in `docs/system-design/caching-strategy.md` and linked it in `docs/system-design/security.md`.

**Files:** `tests/api_test.py`, `docs/system-design/caching-strategy.md`, `docs/system-design/security.md`.

---

## H3 — `docs/step-up-timer-clarification` (docs-only)

**Problem:** the admin UI's client-side step-up countdown could be mistaken by a future contributor for a security control, when the real boundary is the server-side JWT `exp` claim.

**Plan:**
1. `docs/system-design/security.md`: add a subsection under MFA/step-up stating explicitly that the client countdown is UX only, has no security effect, and that the 5-minute JWT expiry enforced in `StepUpAuthenticationFilter` is the actual boundary — a client that clears, ignores, or extends its local timer gains nothing.
2. Add a one-line comment directly above `JwtTokenProvider.generateStepUpToken` pointing at that doc section, so a reader lands on the explanation without hunting for it.
3. No test changes — this is documentation, not behavior.

**Files:** `docs/system-design/security.md`, `JwtTokenProvider.java` (comment only).

---

## H4 — `security/kek-fail-closed` (Option B: opt-in fallback, loud) (COMPLETED)

**Problem:** `EncryptionService.getMasterKek` silently SHA-256-digests any KEK that isn't exactly 32 bytes after Base64 decode, rather than failing startup — a misconfigured KEK boots successfully and silently encrypts under an unintended derived key.

**Resolution:**
1. Added `allowRawPassphrase` configuration property nested under `crypto.kek` in `CryptoProperties.java`, defaulting to `false`.
2. Configured and wired `crypto.kek.allow-raw-passphrase` in `application.yml`.
3. Created a `validateKekShape` method in `SecretsStartupValidator.java` checking that all configured KEKs (master and versioned KEKs) decode to exactly 32 bytes. If not, it fails startup with an `IllegalStateException` unless `allowRawPassphrase` is `true` (in which case it logs a warning).
4. Modified `EncryptionService.java` to log a warning on the first use of a digested KEK version.
5. Added unit and integration tests verifying correct validation and logging behaviors.

**Files:** `CryptoProperties.java`, `application.yml`, `SecretsStartupValidator.java`, `EncryptionService.java`, `SecretsStartupValidatorTest.java`, `EncryptionServiceTest.java`, `application-test.yml`.

---

## H5 — `security/link-rate-limit-scoping`

**Problem:** public share-link access (`GET /api/v1/shares/link/**`) is rate-limited on a single blanket `limit:<ip>:/api/v1/shares/link` key. One abusive anonymous client on a shared IP (NAT/corporate proxy) can exhaust the bucket for every legitimate user behind that same egress IP, across every share link.

**Plan:**
1. `RateLimitingFilter`: replace the single key with a two-tier scheme, both of which must pass:
   - **Per-link bucket:** `limit:link:<shareCode>:<ip>` at the existing `linkLimit` (default 30/min) — bounds hammering one specific link.
   - **Per-IP coarse bucket:** `limit:linkglobal:<ip>` at a new, higher ceiling — bounds one IP enumerating many share codes without starving other NAT-mates out of any single link they legitimately hold.
2. Extract `shareCode` from the request path (`/api/v1/shares/link/{shareCode}`) via simple path parsing — no new dependency.
3. New config property `security.rate-limiting.link-global-limit` (default 100), wired the same way as the existing `*-limit` properties; corresponding `RATE_LIMIT_LINK_GLOBAL` env var added to `docker-compose.yml` / `.env.example` for consistency with the existing pattern.
4. Tests: `RateLimitingFilterTest` — 30 requests to link A from one IP blocks link A but not link B from the same IP; 100 requests to distinct links from one IP trips the global bucket regardless of per-link state.

**Files:** `RateLimitingFilter.java`, `application.yml`, `docker-compose.yml`, `.env.example`, `RateLimitingFilterTest.java`.