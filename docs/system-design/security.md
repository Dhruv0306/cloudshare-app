# Security Architecture

Security is the primary requirement of the **CloudShare** application. This document details the defense-in-depth strategies implemented across all tiers of the application to safeguard user identities, secure REST APIs, and protect stored files from theft and unauthorized access.

---

## 1. Authentication & Session Security

To combine modern REST API standard practices with high security, CloudShare implements stateless authentication using JSON Web Tokens (JWT) coupled with **Refresh Token Rotation (RTR)**.

```mermaid
sequenceDiagram
    autonumber
    actor User as Client Browser
    participant API as Spring Boot App
    participant Redis as Redis Cache
    participant DB as PostgreSQL DB

    Note over User, API: Authentication Flow
    User->>API: POST /api/v1/auth/login (Credentials)
    API->>DB: Fetch User (BCrypt password verification)
    DB-->>API: User Data (Role validation)
    API->>Redis: Save Refresh Token ID (active state)
    API-->>User: HTTP 200 OK + JWT Access Token (JSON body) + Refresh Token (HttpOnly Cookie)

    Note over User, API: Token Refresh & Rotation (RTR) Flow
    User->>API: POST /api/v1/auth/refresh (Sends HttpOnly Cookie)
    API->>Redis: Check if Refresh Token is blacklisted/revoked
    alt Token is valid & not rotated
        API->>Redis: Revoke old Refresh Token
        API->>Redis: Issue new Refresh Token (save in active state)
        API-->>User: New Access Token (JSON) & New Refresh Token (HttpOnly Cookie)
    else Token Reuse Detected (Token Theft Attack)
        API->>Redis: Revoke ALL refresh tokens for this user family
        API-->>User: HTTP 401 Unauthorized (Force re-login)
    end
```

### Detailed Token Management Protocol
1.  **Access Token:**
    *   **Lifetime:** 15 minutes (short-lived).
    *   **Payload:** Contains subject (User UUID), roles (`ROLE_USER`, `ROLE_ADMIN`), issued-at time, and expiration time. Signed with an HMAC SHA-256 or RSA-256 private key.
    *   **Storage:** Kept only in client-side memory (JavaScript variable). This isolates the access token from Cross-Site Scripting (XSS) extraction and prevents CSRF since it is not automatically sent by the browser.
2.  **Refresh Token:**
    *   **Lifetime:** 7 days (longer-lived).
    *   **Storage:** Sent to the client as an `HttpOnly`, `Secure`, `SameSite=Strict` cookie.
        *   `HttpOnly`: Prevents client-side scripts from reading the cookie, mitigating XSS theft.
        *   `Secure`: Ensures the cookie is only transmitted over HTTPS (TLS) connections.
        *   `SameSite=Strict`: Prevents the cookie from being sent on cross-site requests, mitigating Cross-Site Request Forgery (CSRF).
3.  **Refresh Token Rotation (RTR):**
    *   Every time a client requests a new Access Token using their Refresh Token, the Spring Boot backend generates a *new* Refresh Token and invalidates the old one.
    *   If a malicious actor steals a Refresh Token and tries to reuse it, the backend detects that the token was already consumed. To prevent breach, the backend immediately invalidates the entire token family (forcing both the legitimate user and the attacker to re-authenticate).
4.  **Credential Hashing:**
    *   All user passwords are encrypted using `BCrypt` with a work factor of 12 before database storage. Cleartext passwords are never logged, logged in memory, or stored.

### Multi-Factor Authentication (MFA) Enrollment Flow
CloudShare supports two-factor authentication using the **Time-Based One-Time Password (TOTP)** algorithm (RFC 6238).

1.  **MFA Setup (`POST /api/v1/auth/mfa/setup`):**
    *   The backend generates a cryptographically secure, random 160-bit secret key (Base32 encoded).
    *   It creates a standard key URI formatted as: `otpauth://totp/CloudShare:username?secret=SECRET&issuer=CloudShare&algorithm=SHA1&digits=6&period=30`.
    *   The URI is rendered as a Base64-encoded PNG QR code and returned to the frontend along with the raw secret.
    *   The secret key is stored in the database flagged as unverified (using a temporary staging column or pending state).
2.  **MFA Verification (`POST /api/v1/auth/mfa/verify`):**
    *   The user scans the QR code in their authenticator app (Google Authenticator, Authy, etc.) and submits the current 6-digit code.
    *   The backend calculates the expected code for the current time window (allowing a drift window of +/- 1 interval).
    *   If correct, the backend permanently saves the secret key in the `users` table and toggles `mfa_enabled = true`.

### Administrative Step-Up Authentication
To protect critical configurations (like viewing system logs or modifying other user accounts), endpoints protected by `ROLE_ADMIN` require **step-up authentication**:

*   **Logic:** When accessing `/api/v1/admin/*`, the client must present an `X-StepUp-Token` header.
*   **Token Generation:** The user calls `POST /api/v1/auth/mfa/step-up`, passing their current 6-digit MFA code. If valid, the backend issues a separate, short-lived JWT token (`stepUpToken`) containing the claim `step_up: true` with a strict **5-minute expiration**.
*   **Security Interceptor:** A Spring Security filter intercepts all admin paths and verifies that the `X-StepUp-Token` is present, valid, and unexpired. This prevents session hijackers from executing administrative actions even if they possess a valid bearer access token.

---

## 2. Encryption Architecture

### 2.1 Encryption-in-Transit
*   **Protocols:** The API gateway (Nginx) enforces **TLS 1.3** (with TLS 1.2 as a minimum fallback). Older, insecure TLS versions (1.0, 1.1) and SSL are disabled.
*   **HSTS:** HTTP Strict Transport Security (HSTS) headers are injected to force client browsers to communicate exclusively over HTTPS:
    ```http
    Strict-Transport-Security: max-age=63072000; includeSubDomains; preload
    ```
*   **Cipher Suites:** Restricts connections to highly secure ciphers, e.g., `TLS_AES_256_GCM_SHA384` and `TLS_CHACHA20_POLY1305_SHA256`.
*   **Header Protection in Transit:** Sensitive parameters (like the public link access password `X-Share-Password`) are transmitted exclusively within the TLS 1.3 encrypted tunnel, protecting them from wire sniffing. At the reverse proxy level, Nginx is configured to strip these headers before writing the standard access logs, preventing cleartext leaks in operational logs. No weak client-side pre-hashing is used as it acts as a static password equivalence; transport security is managed entirely via TLS 1.3.

### 2.2 Encryption-at-Rest: Envelope Encryption
To secure stored files against physical disk compromise or unauthorized access to the S3 bucket/local directory, CloudShare uses **Envelope Encryption** powered by **AES-256-GCM** (Galois/Counter Mode).

```mermaid
flowchart TD
    subgraph Encrypt Pipeline (Upload)
        File[Raw Uploaded File] -->|AES-256-GCM| EncryptedFile[Encrypted File on Disk/S3]
        FEK[File Encryption Key - AES 256] -->|Used to Encrypt| File
        KEK[Key Encryption Key - Master Key] -->|Encrypts| FEK
        FEK -->|Envelope Encrypted| E_FEK[Encrypted FEK]
        E_FEK -->|Stored| DB[(PostgreSQL DB)]
    end
```

#### The Cryptographic Flow:
1.  **Key Hierarchy:**
    *   **Data Encryption Key / File Encryption Key (FEK):** A unique, cryptographically random AES-256 key generated for *each* uploaded file.
    *   **Key Encryption Key (KEK):** A master key stored securely outside the database (e.g., in a Key Management Service (KMS) or an environment variable on a secure, restricted container).
2.  **File Upload (Encryption):**
    *   When a user uploads a file, the application generates a random 256-bit FEK.
    *   The file data is encrypted using AES-256-GCM, generating cipher data and a 16-byte authentication tag (ensuring integrity).
    *   The FEK is encrypted using the KEK (Envelope Encryption).
    *   The encrypted file is stored in MinIO/local storage.
    *   The encrypted FEK, the GCM Initialization Vector (IV), and file metadata are saved in the PostgreSQL database.
3.  **File Download (Decryption):**
    *   The backend retrieves the encrypted FEK and GCM IV from the database.
    *   The KEK decrypts the encrypted FEK back to cleartext in memory.
    *   The backend streams the encrypted file from storage, decrypts it block-by-block using the FEK, and streams the cleartext file directly to the client response stream. The decrypted file is never stored on disk.

---

## 3. Secure File Upload Pipeline

Allowing arbitrary file uploads presents severe vulnerabilities (malware, remote code execution, denial of service). CloudShare implements a strict sanitization pipeline:

```mermaid
flowchart TD
    Start[User Submits File] --> CheckSize{File Size <= Limit?}
    CheckSize -->|No| Reject[Reject Upload - 413 Payload Too Large]
    CheckSize -->|Yes| SanitizeName[Sanitize Filename & Map to UUID]
    SanitizeName --> StreamClamAV[Stream to ClamAV Daemon]
    StreamClamAV --> ScanResult{Virus Detected?}
    ScanResult -->|Yes| AuditAlert[Log Security Event & Delete File]
    ScanResult -->|No| CheckMime{Validate MIME Magic Numbers}
    CheckMime -->|Invalid| RejectMime[Reject - 415 Unsupported Media Type]
    CheckMime -->|Valid| Encrypt[Encrypt File with FEK]
    Encrypt --> Store[Write Encrypted File to S3/Disk]
    Store --> WriteDB[Write File Metadata to PostgreSQL]
```

### Sanitization Mechanisms
1.  **Size Validation:** Strict size limits (e.g., max 100MB per file) are verified at the gateway level (Nginx `client_max_body_size`) and backend application properties to prevent RAM exhaustion or Denial of Service (DoS) attacks.
2.  **Filename Sanitization & Path Traversal Prevention:**
    *   Users might upload files containing path traversal sequences (e.g., `../../../etc/passwd`).
    *   The application strips directory paths and sanitizes characters.
    *   **Physical Isolation:** The actual file is written to storage using a random `UUIDv4` as its identifier. The user's input filename is kept only as an encoded text column in the metadata database.
3.  **Magic Number Verification:**
    *   Do not trust the `Content-Type` header sent by the browser or the file extension.
    *   The backend reads the first few bytes (magic numbers) using an library like Apache Tika to determine the true MIME type. If a user uploads an executable masquerading as a PDF, it is rejected.
4.  **Virus Scanning (ClamAV Integration):**
    *   The backend establishes a socket connection to a ClamAV daemon.
    *   The file stream is split: one stream is scanned by ClamAV in chunked format, and the other is buffered in memory/temp file.
    *   If ClamAV detects malware, the transaction is immediately rolled back, the storage is wiped, and a high-priority audit warning is triggered.

---

## 4. OWASP Top 10 Mitigations

| Vulnerability | Threat Vector in File Sharing | CloudShare Mitigation Strategy |
| :--- | :--- | :--- |
| **Broken Object Level Authorization (BOLA)** | User accesses another user's private file by guessing the database ID. | Files are identified via random `UUIDv4` instead of sequential IDs. Access control queries enforce ownership check: `SELECT * FROM files WHERE id = :fileId AND owner_id = :currentUserId`. |
| **Cross-Site Scripting (XSS)** | Attacker uploads an HTML/SVG file containing malicious JS, which executes when another user downloads/views it. | All file downloads force the `Content-Disposition: attachment; filename="sanitized.ext"` and `Content-Type: application/octet-stream` headers, preventing the browser from rendering files inline. In addition, Nginx sets a strict `Content-Security-Policy (CSP)`. |
| **SQL Injection (SQLi)** | Attacker inputs SQL commands in the filename search query. | Use Spring Data JPA with standard repository queries which run Parameterized Queries under the hood, neutralizing injection. |
| **Path Traversal** | Attacker uploads files with path parameters, overwriting system files. | Absolute separation between user-facing metadata name and backend storage name (random UUID folder/file structure on disk). |
| **Cross-Site Request Forgery (CSRF)** | Attacker triggers state changes (like deleting files) on behalf of a logged-in user. | Stateless JWTs are not sent via standard cookies; they must be provided in the `Authorization: Bearer <JWT>` request header, which browsers do not automatically attach to cross-site requests. The refresh token cookie is configured as `SameSite=Strict`. |
| **Rate Limiting & Brute Force** | Attackers perform automated login guessing or script file downloads to crash the server. | Redis token bucket rate limiters filter IP ranges and user IDs. Client IP resolution is secured by blocking direct application container port access (`app:8080` port isolation) and configuring the Nginx gateway to unconditionally overwrite proxy headers (`X-Real-IP`), preventing spoofing (see [Client IP Resolution & Spoofing Protection](caching-strategy.md#32-client-ip-resolution--spoofing-protection-h2--c2)). |
