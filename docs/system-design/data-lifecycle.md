# Data Retention & Lifecycle Policies

To control infrastructure costs, optimize query execution, and adhere to global privacy frameworks (such as GDPR), CloudShare defines automated workflows for file deletion, link expiration, and user account purging.

---

## 1. Storage & Link Lifecycle States

Files and links progress through the following states during their lifetime:

```
[ Active File ] ---> (User Deletes) ---> [ Soft Deleted (Recycle Bin) ] ---> (30-Day Expiry) ---> [ Permanent Purge ]
                                                                                                    (Physical & DB Wipe)

[ Shared Link ] ---> (Expiration Time / Download Limit) ---> [ Expired / Inactive Link ] ---> (Daily Purge) ---> [ Row Deleted ]
```

### Lifecycle Rules:
1.  **Active Files:** Stored encrypted. Fully searchable by owner.
2.  **Soft Deleted (Recycle Bin):** `deleted = true` in PostgreSQL. Kept for **30 days** to allow user restoration. Files in this state do not appear in normal list queries but count toward user storage quotas.
3.  **Expired Shared Links:** Validated at runtime. Access is blocked immediately upon expiration.
4.  **Audit Logs:** Retained in PostgreSQL for 1 year, then detached, compressed, and archived to cold storage.

---

## 2. Automated Cleanup Engine (Spring Boot Schedulers)

We use Spring Boot's scheduling framework to automate cleanup tasks. These jobs are configured to run during low-traffic periods (off-peak hours).

### 2.1 File Purge Scheduler (`FilePurgeScheduler`)
*   **Schedule:** Runs daily at 2:00 AM UTC (`cron = "0 0 2 * * ?"`).
*   **Logic:** Selects all files flagged as deleted where the deletion timestamp is older than 30 days.

```mermaid
flowchart TD
    Start[Cron triggers at 2:00 AM] --> Query[Query DB: deleted = TRUE and updated_at < NOW - 30 days]
    Query --> Check{Any files?}
    Check -->|No| End[Scheduler Sleep]
    Check -->|Yes| FetchRow[Get next File Metadata]
    FetchRow --> DeletePhysical[Call StorageService.delete - Remove physical file from Disk/S3]
    DeletePhysical -->|Success| DeleteDB[Delete metadata row from files table in DB]
    DeletePhysical -->|Failure/Offline| LogError[Log Critical Error - Retain DB metadata for retry]
    DeleteDB --> Check
```

#### Safe Deletion Order Rule:
To prevent **orphaned storage files** (files taking up space on disk/S3 with no database pointers), the scheduler must execute deletion sequentially:
1.  Attempt physical file removal from storage.
2.  Upon verification of storage deletion, execute the database `DELETE` transaction.
3.  If the storage server is offline or fails, skip the database deletion so the job retries on the next execution cycle.

### 2.2 Shared Link Cleanup Scheduler (`LinkCleanupScheduler`)
*   **Schedule:** Runs daily at 3:00 AM UTC (`cron = "0 0 3 * * ?"`).
*   **Logic:** Deletes expired links to keep index tables compact.
    ```sql
    DELETE FROM share_links WHERE expires_at < CURRENT_TIMESTAMP;
    ```

---

## 3. GDPR Compliance: "Right to be Forgotten"

GDPR Article 17 requires that users can request the permanent removal of their personal data. Although self-service account deactivation is not currently exposed via the application's REST API, the system is designed to support manual/administrative **Account Deletion Flows** executed directly by DBAs:

1.  **Manual User Pruning:** A database administrator manually soft-deletes or removes the user record.
2.  **File Flagging:** All files owned by the user are flagged as `deleted = true` in PostgreSQL.
3.  **Cascade Cleanup:**
    *   All direct permissions mapping to other users (`file_shares`) are immediately deleted.
    *   All public sharing links (`share_links`) pointing to the user's files are immediately deleted.
4.  **Wipe Execution:** The automated `FilePurgeScheduler` runs daily at 2:00 AM UTC. Any file owned by the user that has been flagged as `deleted = true` for over 30 days is permanently deleted from storage, and its metadata row is dropped.
5.  **Audit Exception:** For compliance and security tracking, the audit log entries showing past transactions are *not* deleted immediately. They are kept for the standard 1-year archive window, anonymizing user names where required.
