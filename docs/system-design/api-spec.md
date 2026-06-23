# RESTful API Specification

The CloudShare backend exposes a secure REST API. All requests and responses must use `application/json` unless specified otherwise (e.g., file upload multipart data).

---

## 1. Global Response Envelopes

To maintain a consistent API structure, all responses are wrapped in a standard JSON envelope.

### Success Response
```json
{
  "success": true,
  "data": {},
  "timestamp": "2026-06-23T19:57:04Z"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "The request body failed to validate.",
    "details": [
      {
        "field": "email",
        "issue": "Must be a well-formed email address"
      }
    ]
  },
  "timestamp": "2026-06-23T19:57:04Z"
}
```

---

## 2. Authentication & Session Endpoints (`/api/v1/auth`)

### 2.1 User Registration
*   **Endpoint:** `POST /api/v1/auth/register`
*   **Authentication:** None
*   **Request Body:**
    ```json
    {
      "username": "johndoe",
      "email": "johndoe@example.com",
      "password": "SecurePassword123!"
    }
    ```
*   **Responses:**
    *   `201 Created`: User successfully registered.
    *   `400 Bad Request`: Validation failure (e.g., weak password, duplicate email).

### 2.2 User Login
*   **Endpoint:** `POST /api/v1/auth/login`
*   **Authentication:** None
*   **Request Body:**
    ```json
    {
      "usernameOrEmail": "johndoe@example.com",
      "password": "SecurePassword123!",
      "mfaCode": "123456" 
    }
    ```
*   **Headers Set in Response:**
    *   `Set-Cookie: refresh_token=uuid_token; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`
*   **Response Body:**
    ```json
    {
      "success": true,
      "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "tokenType": "Bearer",
        "expiresIn": 900,
        "user": {
          "id": "e4c278e9-d7bb-4f40-8b6b-352277d33d9c",
          "username": "johndoe",
          "email": "johndoe@example.com",
          "roles": ["ROLE_USER"],
          "mfaRequired": false
        }
      }
    }
    ```
*   **Responses:**
    *   `200 OK`: Login successful.
    *   `401 Unauthorized`: Invalid credentials or invalid MFA code.

### 2.3 Token Refresh
*   **Endpoint:** `POST /api/v1/auth/refresh`
*   **Authentication:** Implicit via `refresh_token` Cookie
*   **Request Body:** None
*   **Headers Set in Response:**
    *   `Set-Cookie: refresh_token=new_uuid_token; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=604800`
*   **Response Body:**
    ```json
    {
      "success": true,
      "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "expiresIn": 900
      }
    }
    ```
*   **Responses:**
    *   `200 OK`: Tokens rotated successfully.
    *   `401 Unauthorized`: Refresh token expired, invalid, or re-used.

### 2.4 User Logout
*   **Endpoint:** `POST /api/v1/auth/logout`
*   **Authentication:** Bearer Token + Cookie
*   **Request Body:** None
*   **Headers Set in Response:**
    *   `Set-Cookie: refresh_token=; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict; Max-Age=0`
*   **Responses:**
    *   `200 OK`: Logout successful (token blacklisted in Redis).

---

## 3. File Endpoints (`/api/v1/files`)

All operations in this section require an `Authorization: Bearer <accessToken>` header.

### 3.1 Upload File
*   **Endpoint:** `POST /api/v1/files/upload`
*   **Content-Type:** `multipart/form-data`
*   **Request Params:**
    *   `file`: Binary file upload.
*   **Response Body:**
    ```json
    {
      "success": true,
      "data": {
        "id": "7bf3d834-ff4d-4cb0-a548-52fb9882a934",
        "name": "project_report.pdf",
        "sizeBytes": 2048576,
        "mimeType": "application/pdf",
        "checksum": "sha256-e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "uploadedAt": "2026-06-23T19:57:04Z"
      }
    }
    ```
*   **Responses:**
    *   `201 Created`: Upload succeeded, virus check clean, file stored.
    *   `400 Bad Request`: Rejected due to failed file verification (mime check).
    *   `413 Payload Too Large`: Exceeded size limits.
    *   `415 Unsupported Media Type`: Magic number mismatch or blacklisted extension.
    *   `422 Unprocessable Entity`: Virus detected by ClamAV scanner.

### 3.2 List Files (Paged)
*   **Endpoint:** `GET /api/v1/files?page=0&size=10&sort=uploadedAt,desc`
*   **Response Body:**
    ```json
    {
      "success": true,
      "data": {
        "content": [
          {
            "id": "7bf3d834-ff4d-4cb0-a548-52fb9882a934",
            "name": "project_report.pdf",
            "sizeBytes": 2048576,
            "mimeType": "application/pdf",
            "uploadedAt": "2026-06-23T19:57:04Z"
          }
        ],
        "pageable": { "pageNumber": 0, "pageSize": 10 },
        "totalElements": 1,
        "totalPages": 1,
        "last": true
      }
    }
    ```

### 3.3 Download File
*   **Endpoint:** `GET /api/v1/files/{id}/download`
*   **Response Content-Type:** `application/octet-stream`
*   **Headers Set in Response:**
    *   `Content-Disposition: attachment; filename="project_report.pdf"`
*   **Response Body:** Binary Stream (Encrypted file is decrypted on-the-fly and streamed).
*   **Responses:**
    *   `200 OK`: Streaming download.
    *   `401 Unauthorized`: Missing or invalid token.
    *   `403 Forbidden`: Token valid, but user has no permission to read this file.
    *   `404 Not Found`: File does not exist.

### 3.4 Delete File
*   **Endpoint:** `DELETE /api/v1/files/{id}`
*   **Responses:**
    *   `204 No Content`: File deleted (or soft-deleted).
    *   `403 Forbidden`: User is not the owner of the file.

---

## 4. Sharing Endpoints (`/api/v1/shares`)

### 4.1 Internal User Sharing
*   **Endpoint:** `POST /api/v1/shares/internal`
*   **Request Body:**
    ```json
    {
      "fileId": "7bf3d834-ff4d-4cb0-a548-52fb9882a934",
      "targetUsernameOrEmail": "janedoe@example.com",
      "permissionType": "READ" 
    }
    ```
*   **Response Body:**
    ```json
    {
      "success": true,
      "data": {
        "shareId": "d8e3b2a2-83b4-4b53-a75d-35756fb60971",
        "fileId": "7bf3d834-ff4d-4cb0-a548-52fb9882a934",
        "sharedWith": "janedoe@example.com",
        "permission": "READ"
      }
    }
    ```

### 4.2 Create Public Link
*   **Endpoint:** `POST /api/v1/shares/link`
*   **Request Body:**
    ```json
    {
      "fileId": "7bf3d834-ff4d-4cb0-a548-52fb9882a934",
      "expiresInSeconds": 86400,
      "password": "DownloadSecret123!", 
      "downloadLimit": 5 
    }
    ```
*   **Response Body:**
    ```json
    {
      "success": true,
      "data": {
        "shareCode": "aB7cdX9Y",
        "shareUrl": "https://cloudshare.app/share/aB7cdX9Y",
        "expiresAt": "2026-06-24T19:57:04Z",
        "passwordProtected": true
      }
    }
    ```

### 4.3 Download via Public Link
*   **Endpoint:** `GET /api/v1/shares/link/{shareCode}/download`
*   **Authentication:** None (Public)
*   **Headers Required if Password Protected:**
    *   `X-Share-Password: DownloadSecret123!`
*   **Response Content-Type:** `application/octet-stream`
*   **Response Body:** Binary Stream of decrypted file.
*   **Responses:**
    *   `200 OK`: Success.
    *   `401 Unauthorized`: Password required but missing/invalid.
    *   `403 Forbidden`: Link has expired, or reached its download limit.
    *   `404 Not Found`: Link code does not exist.
