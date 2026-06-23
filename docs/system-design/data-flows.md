# Core Data Flows & Sequence Diagrams

This document contains step-by-step sequence diagrams showing the interaction between the Client, Spring Boot Application, Spring Security, Redis, ClamAV, the Database, and the Storage backend for key system operations.

---

## 1. User Authentication & Session Setup

This diagram shows how users log in and how their access and refresh sessions are established.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser (Javascript)
    participant SecFilter as Spring Security Filters
    participant Provider as AuthManager / Provider
    participant Redis as Redis Cache
    participant DB as PostgreSQL DB

    Client->>SecFilter: POST /api/v1/auth/login
    SecFilter->>Provider: Authenticate (username, password)
    Provider->>DB: Query user where username/email = input
    DB-->>Provider: User Entity (hashed password, roles, salt)
    Provider->>Provider: Validate password (BCrypt verify)
    
    alt Credentials Invalid
        Provider-->>Client: HTTP 401 Unauthorized (Invalid credentials)
    else Credentials Valid
        Provider->>SecFilter: Authenticated Authentication Object
        SecFilter->>SecFilter: Generate random UUID for Session (Refresh Token)
        SecFilter->>SecFilter: Generate Access JWT (claims: username, roles, exp 15m)
        SecFilter->>Redis: Save Refresh Session (key: refresh_token_id, val: user_id, TTL: 7d)
        SecFilter-->>Client: HTTP 200 OK + Body (Access JWT) + Cookie (HttpOnly, Secure, refresh_token=UUID)
    end
```

---

## 2. Secure File Upload Pipeline

During file upload, the application stream is processed sequentially: virus scanned, verified for MIME types, encrypted on the fly, stored, and cataloged.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser (Javascript)
    participant Controller as FileController (Spring Boot)
    participant Service as FileService
    participant ClamAV as ClamAV Container (TCP Socket)
    participant Crypto as EncryptionEngine (AES-GCM)
    participant Storage as StorageService (Local/MinIO)
    participant DB as PostgreSQL DB

    Client->>Controller: POST /api/v1/files/upload (MultipartFormData)
    Controller->>Service: handleUpload(multipartFile, currentUser)
    
    %% Size and Virus check
    Service->>Service: Verify file size <= limit (e.g. 50MB)
    Service->>ClamAV: Stream file bytes for virus check (instream scan)
    ClamAV-->>Service: Scan Result (OK or FOUND_VIRUS)
    
    alt Virus Detected
        Service-->>Client: HTTP 422 Unprocessable Entity (Malware detected)
    else Clean scan
        %% MIME Check
        Service->>Service: Inspect magic numbers (magic byte verification)
        Service->>Service: Sanitize filename and generate random storage UUID
        
        %% Crypto & Write
        Service->>Crypto: Generate random 256-bit FEK (File Encryption Key)
        Service->>Crypto: Generate random 12-byte IV (Initialization Vector)
        Service->>Crypto: Encrypt FEK with KEK (Master Key) -> Encrypted_FEK
        
        Service->>Storage: Store Encrypted stream (Stream raw bytes -> encrypt AES-256-GCM -> write to disk/S3)
        Storage-->>Service: Storage Path / ETag
        
        Service->>DB: INSERT into files (id, owner_id, storage_path, original_filename, size, mime, checksum_sha256, encrypted_fek, iv_gcm)
        DB-->>Service: Insert Success
        
        Service-->>Client: HTTP 201 Created + File Metadata
    end
```

---

## 3. Secure File Decryption & Download

File downloads must never allow direct file access. Decryption is performed in memory while streaming.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Browser (Javascript)
    participant Controller as FileController
    participant Service as FileService
    participant DB as PostgreSQL DB
    participant Crypto as EncryptionEngine (AES-GCM)
    participant Storage as StorageService (Local/MinIO)

    Client->>Controller: GET /api/v1/files/{fileId}/download (Auth Header)
    Controller->>Service: streamFile(fileId, currentUser)
    
    %% Access Authorization
    Service->>DB: Fetch file metadata & share permissions
    DB-->>Service: Metadata (encrypted_fek, iv_gcm, owner_id, shared_users)
    
    Service->>Service: Evaluate Access (Is owner? Is shared? Has READ role?)
    
    alt Unauthorized
        Service-->>Client: HTTP 403 Forbidden
    else Authorized
        %% Log Access
        Service->>DB: INSERT INTO audit_logs (DOWNLOAD, user_id, file_id, IP)
        
        %% Decryption Setup
        Service->>Crypto: Decrypt encrypted_fek using Master KEK -> Plaintext FEK
        Service->>Storage: Retrieve encrypted stream (storage_path)
        Storage-->>Service: Encrypted Binary Stream
        
        %% Decryption & Stream
        Service->>Crypto: Wrap Encrypted stream with CipherInputStream (FEK, IV)
        Service-->>Client: HTTP 200 OK + Content-Disposition: attachment + Decrypted Stream
    end
```

---

## 4. Secure Shared Link Verification

This flow describes accessing shared resources securely using short-lived codes with credentials.

```mermaid
sequenceDiagram
    autonumber
    actor Guest as Guest User Browser
    participant Controller as ShareController
    participant Service as ShareService
    participant DB as PostgreSQL DB
    participant Crypto as EncryptionEngine (AES-GCM)
    participant Storage as StorageService

    Guest->>Controller: GET /api/v1/shares/link/{shareCode}/download
    Note over Guest, Controller: Optional Header: X-Share-Password
    Controller->>Service: downloadSharedLink(shareCode, passwordHeader)
    
    Service->>DB: Fetch share_link & related file metadata
    DB-->>Service: ShareLink data (expires_at, password_hash, count, limit)
    
    Service->>Service: Check Expiration (expires_at < current_time)
    Service->>Service: Check Download Limit (download_count >= download_limit)
    
    alt Link Expired or Limits Reached
        Service-->>Guest: HTTP 403 Forbidden (Link expired/inactive)
    else Limits OK
        %% Password Check
        alt Password Protected Link
            Service->>Service: Compare passwordHeader with password_hash (BCrypt)
            alt Password Mismatch
                Service-->>Guest: HTTP 401 Unauthorized (Password required)
            end
        end
        
        %% Increment count
        Service->>DB: UPDATE share_links SET download_count = download_count + 1
        Service->>DB: INSERT INTO audit_logs (GUEST_DOWNLOAD, file_id, IP)
        
        %% Streaming
        Service->>Crypto: Decrypt FEK with Master KEK
        Service->>Storage: Get encrypted file stream
        Storage-->>Service: Encrypted stream
        Service->>Crypto: Decrypt stream on-the-fly (AES-GCM)
        Service-->>Guest: HTTP 200 OK + Attachment Stream
    end
```
