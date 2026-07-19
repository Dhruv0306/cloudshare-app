# Caching & Rate Limiting Strategy

To prevent relational database saturation and block malicious scraping or brute-force attacks, CloudShare deploys **Redis** as an in-memory cache and rate-limiting store.

---

## 1. Dual-Redis Split Instance Architecture

To prevent eviction of security-critical keys (like JWT blacklists and rate limiter counters) under memory pressure, CloudShare deploys **two separate physical/logical Redis instances** with tailored configurations:

1.  **Redis Cache (`cache-aside`)**: Holds transient business objects. Allows key eviction when RAM limits are reached.
2.  **Redis Security (`cache-security`)**: Holds JWT blacklists, MFA session states, and sliding-window rate limit sets. Strict `noeviction` policy enforces security rules.

| Target Instance | Dataset Category | Redis Key Pattern | Data Structure | TTL | Eviction Policy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Redis Security** | **Revoked JWTs** | `blacklist:token:<jti>` | String | Token expiry | **No Eviction** |
| **Redis Security** | **API Rate Limits** | `limit:<ip_or_userid>:<endpoint>` | Sorted Set | 1 Minute | **No Eviction** |
| **Redis Cache** | **Cache-Aside Metadata** | `cache:user:<id>` <br> `cache:permissions:<file_id>` | Hash / String | 1 Hour | `allkeys-lru` |

---

## 2. The Cache-Aside Pattern

For performance optimization on database queries (e.g., checking user metadata or resolving file access permissions during downloads), the application follows the **Cache-Aside** architecture:

```mermaid
flowchart TD
    Start[API Request received] --> Key{Key in Redis?}
    Key -->|Yes - Cache Hit| Return[Return data immediately]
    Key -->|No - Cache Miss| QueryDB[Query PostgreSQL Database]
    QueryDB --> CheckDB{Row exists?}
    CheckDB -->|No| ReturnNull[Return null/404]
    CheckDB -->|Yes| WriteCache[Write to Redis with TTL = 1 hour]
    WriteCache --> Return
```

### Cache Invalidation Rules:
To prevent dirty reads (returning outdated permissions or details), we implement active invalidation:
*   **Write-Through Eviction:** Whenever file access permissions are modified (`POST /api/v1/shares/internal`) or a file is renamed/deleted, the application immediately deletes the corresponding Redis key (`cache:permissions:<file_id>`) in the same database transaction.
*   **No Cache for Files:** The binary streams of files are *never* stored in Redis. Redis is strictly reserved for metadata, session IDs, and rate limit counters.

---

## 3. Distributed Rate Limiting (Token Bucket / Sliding Window)

To protect critical endpoints (such as authentication or file downloads) from Denial of Service (DoS) and brute-force attacks, CloudShare implements a **Sliding Window Counter** rate limiter in Redis.

To ensure atomic transactions under high concurrency, rate evaluation is executed via a **Redis Lua Script**:

```lua
-- KEYS[1] = Rate limit key (e.g., "limit:192.168.1.50:/auth/login")
-- ARGV[1] = Current Unix timestamp (seconds)
-- ARGV[2] = Window size (seconds, e.g., 60)
-- ARGV[3] = Max allowed requests in window (e.g., 5)

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

local clear_before = now - window

-- Remove requests outside the sliding window
redis.call('ZREMRANGEBYSCORE', key, '-inf', clear_before)

-- Count total requests in the window
local current_requests = redis.call('ZCARD', key)

if current_requests < limit then
    -- Add the current request timestamp as score and value
    redis.call('ZADD', key, now, now)
    -- Extend key expiration to cover window duration
    redis.call('EXPIRE', key, window)
    return 1 -- Allowed
else
    return 0 -- Rate limit exceeded
end
```

### 3.1 Rate Limit Thresholds:
*   **Authentication Routes (`POST /api/v1/auth/*`):** Max 5 attempts per minute per IP.
*   **File Upload Routes (`POST /api/v1/files/upload`):** Max 10 uploads per minute per User ID.
*   **Public Link Access (`GET /api/v1/shares/link/*`):** Max 30 requests per minute per IP.
*   **General REST APIs:** Max 100 requests per minute per User ID.

### 3.2 Client IP Resolution & Spoofing Protection (H2 / C2)
For unauthenticated rate limiting (e.g., login attempts, public share link accesses), the application maps rate-limiting buckets using client IP addresses.

To guarantee the integrity of these IP-keyed rate limits and prevent attackers from spoofing their source address via custom headers, CloudShare relies on a secure network design:
1. **Gateway Trust Assumption (C2):** The Spring Boot application container (`app:8080`) is not exposed publicly to the host or internet. All inbound traffic must pass through the Nginx API gateway (`gateway:443`).
2. **IP Header Overwriting:** Nginx unconditionally overrides the incoming `X-Real-IP` and `X-Forwarded-For` HTTP headers with the socket's actual connection IP (`$remote_addr`) before forwarding requests upstream to the app:
   ```nginx
   proxy_set_header X-Real-IP $remote_addr;
   proxy_set_header X-Forwarded-For $remote_addr;
   ```
3. **Application IP Resolution:** The backend `ClientIpResolver` reads the `X-Real-IP` header. Since direct access to the app container port is blocked by the network topology, the application can safely trust the `X-Real-IP` header because Nginx is guaranteed to have populated it securely from the actual remote client IP.

This design ensures IP rate limiting is fully protected against header-spoofing bypass attacks.

---

## 4. Redis Configuration & Tuning Spec

The two Redis instances are configured with distinct memory limits and eviction policies to guarantee security and system reliability under load.

### 4.1 Redis Cache Config (`cache-aside`)
*   **Max Memory:** 256MB.
*   **Max Memory Policy:** `allkeys-lru` (Least Recently Used). If memory limit is reached, Redis evicts the oldest user profiles or permission caches to make room for new metadata.
*   **Tuning Properties:**
    ```properties
    maxmemory 268435456
    maxmemory-policy allkeys-lru
    ```

### 4.2 Redis Security Config (`cache-security`)
*   **Max Memory:** 256MB.
*   **Max Memory Policy:** `noeviction`. Security records (such as blacklisted JWT IDs and IP rate limits) must never be dropped dynamically. If memory is full, new write requests fail with an Out-of-Memory error, protecting the application from brute-force floods or replay attacks bypassing checks.
*   **Tuning Properties:**
    ```properties
    maxmemory 268435456
    maxmemory-policy noeviction
    ```
*   **Alerting:** Prometheus monitors `redis_memory_used_bytes` for both instances. If usage exceeds 80% on either node, an automated alert triggers to notify operators to allocate more memory.
