## Plan: `security/step-up-token-scope` (C1)

**Goal:** step-up tokens become single-purpose and single-use; they can no longer authenticate general requests.

1. **`JwtTokenProvider`**
   - Add a `type` claim to both token constructors: `generateAccessToken` → `"type": "access"`, `generateStepUpToken` → `"type": "step_up"`.
   - Add `getTokenType(String token)` helper.

2. **`JwtAuthenticationFilter`**
   - After `validateToken(jwt)` succeeds, also require `"access".equals(tokenProvider.getTokenType(jwt))`. If not, treat as unauthenticated (skip `SecurityContext` population) rather than 500ing — let downstream 401 handling take over.

3. **`StepUpAuthenticationFilter`**
   - `validateStepUpToken` already checks `step_up == true` — keep that, but move to checking `type == "step_up"` for consistency with the new claim.
   - **Single-use enforcement:** on successful validation, immediately write the step-up token's `jti` to the same Redis blacklist used for access-token revocation (`blacklist:token:<jti>`), with TTL = remaining token lifetime. This makes `JwtAuthenticationFilter`'s existing blacklist check *also* catch reused step-up tokens if someone tries to replay one as a bearer token — one mechanism, two enforcement points.
   - This requires injecting `StringRedisTemplate` (qualified `securityRedisTemplate`) into `StepUpAuthenticationFilter`.

4. **Tests to update/add**
   - `StepUpAuthenticationFilterTest`: assert step-up token is blacklisted after one successful use; second use with same token fails even within the 5-minute window.
   - New test in `JwtAuthenticationFilterTest` (doesn't exist yet — I'll add it) or extend an existing security test: a valid, unexpired, non-blacklisted step-up token presented as `Authorization: Bearer` on a non-admin route is rejected (no `SecurityContext` set → 401).
   - `AuthServiceTest`/`AuthControllerTest`: no change expected, but re-run since `stepUpMfa` response shape is untouched.

**Files touched:** `JwtTokenProvider.java`, `JwtAuthenticationFilter.java`, `StepUpAuthenticationFilter.java`, + 2 test files.

---

## Plan: `infra/lock-down-compose-network` (C2)

**Goal:** only `gateway` (80/443) is reachable from outside the Docker network, in both dev and staging compose.

1. **`docker-compose.yml`**
   - Remove `ports:` from `db`, `cache-aside`, `cache-security`, `clamav`, `storage`.
   - Remove `ports: - "8080:8080"` from `app`. If you want host-machine debugging access on Windows, replace with `127.0.0.1:8080:8080` — I'd default to removing it entirely and hitting the app through `https://localhost` via gateway, matching prod topology. Your call — I'll ask below.
   - MinIO console (`9001`): same treatment — internal-only. If you need the console during dev, `127.0.0.1:9001:9001` is reasonable since it's local-only anyway.

2. **`docker-compose.staging.yml`**
   - This file only currently overlays resource limits, so removing `ports:` in the base file is enough — nothing to override here. I'll add a comment noting *why* there's no `ports:` override, so a future edit doesn't accidentally reintroduce one.

3. **Out of band (I can't do this part — it's on Oracle Cloud, not in this repo):** once you do stand up the Oracle A1 staging box, the Security List/NSG needs to independently allow only 80/443 inbound. I'll drop a one-line reminder in `docs/staging-environment.md` so it's not lost, but this is a manual cloud-console step for you.

4. **Verification step:** after the compose change, `docker compose up` then confirm from the host: `curl localhost:5432` / `redis-cli -p 6379 ping` / `curl localhost:9001` all fail to connect, while `https://localhost/api/v1/auth/...` still works through gateway.

**Files touched:** `docker-compose.yml`, `docs/staging-environment.md` (comment only).