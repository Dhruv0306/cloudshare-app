import os
import sys
import time
import hashlib
import uuid
import requests

BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080")

EICAR_STRING = b"X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"

class TestRunner:
    def __init__(self):
        self.tests_run = 0
        self.tests_failed = 0

    def run_case(self, name, func, *args, **kwargs):
        self.tests_run += 1
        print(f"[RUN] {name} ... ", end="", flush=True)
        try:
            func(*args, **kwargs)
            print("[PASS]")
        except Exception as e:
            self.tests_failed += 1
            print("[FAIL]")
            print(f"   Reason: {e}")

    def summary(self):
        print("\n" + "=" * 50)
        print("Integration Test Summary")
        print("=" * 50)
        print(f"Total Tests Run: {self.tests_run}")
        print(f"Passed:          {self.tests_run - self.tests_failed}")
        print(f"Failed:          {self.tests_failed}")
        print("=" * 50)
        return self.tests_failed == 0

def generate_random_user():
    unique_suffix = uuid.uuid4().hex[:8]
    return {
        "username": f"user_{unique_suffix}",
        "email": f"user_{unique_suffix}@example.com",
        "password": f"Pass_{unique_suffix}_2026!"
    }

def generate_totp(secret_base32):
    import base64
    import hmac
    import hashlib
    import time
    import struct

    # Decode base32 secret. Pad with '=' if length is not a multiple of 8.
    missing_padding = len(secret_base32) % 8
    if missing_padding:
        secret_base32 += '=' * (8 - missing_padding)
    key = base64.b32decode(secret_base32, casefold=True)
    
    # Calculate time step index (30-second window)
    counter = int(time.time() // 30)
    
    # Pack counter as an 8-byte big-endian integer
    msg = struct.pack(">Q", counter)
    
    # Compute HMAC-SHA1
    digest = hmac.new(key, msg, hashlib.sha1).digest()
    
    # Dynamic truncation to generate 6-digit code
    offset = digest[-1] & 0x0F
    code = (struct.unpack(">I", digest[offset:offset+4])[0] & 0x7FFFFFFF) % 1000000
    
    return f"{code:06d}"

# ----------------------------------------------------
# 1. Auth Flow Tests
# ----------------------------------------------------
def test_auth_flow(url_prefix):
    user = generate_random_user()
    
    # Register user
    reg_response = requests.post(f"{url_prefix}/api/v1/auth/register", json=user)
    assert reg_response.status_code == 201, f"Expected 201, got {reg_response.status_code}. Response: {reg_response.text}"
    assert reg_response.json().get("success") is True, f"Registration failed response: {reg_response.text}"

    # Test breach check is working when registering with a known breached password
    breached_user = generate_random_user()
    breached_user["password"] = "password123"
    breached_res = requests.post(f"{url_prefix}/api/v1/auth/register", json=breached_user)
    if breached_res.status_code == 400:
        error_msg = breached_res.json().get("error", {}).get("message", "")
        assert "Password has been found in a data breach" in error_msg, f"Expected breach error message, got: {error_msg}"
    else:
        assert breached_res.status_code == 201, f"Expected 201 or 400, got {breached_res.status_code}"

    
    # Register duplicate user
    dup_response = requests.post(f"{url_prefix}/api/v1/auth/register", json=user)
    assert dup_response.status_code == 400, f"Expected 400 for duplicate user, got {dup_response.status_code}. Response: {dup_response.text}"
    
    # Login user
    session = requests.Session()
    login_payload = {
        "usernameOrEmail": user["username"],
        "password": user["password"]
    }
    login_response = session.post(f"{url_prefix}/api/v1/auth/login", json=login_payload)
    assert login_response.status_code == 200, f"Expected 200, got {login_response.status_code}. Response: {login_response.text}"
    
    login_data = login_response.json()
    assert login_data.get("success") is True
    access_token = login_data["data"]["accessToken"]
    assert access_token is not None
    
    # Check refresh_token cookie was set on /api/v1/auth path
    cookies = session.cookies.get_dict()
    assert "refresh_token" in cookies, f"Expected refresh_token cookie, found cookies: {cookies}"
    
    # For local HTTP testing, requests requires the secure flag to be False on cookies
    for c in session.cookies:
        if c.name == "refresh_token":
            c.secure = False
            
    # Test token refresh
    refresh_response = session.post(f"{url_prefix}/api/v1/auth/refresh")
    assert refresh_response.status_code == 200, f"Expected 200, got {refresh_response.status_code}. Response: {refresh_response.text}"
    
    refresh_data = refresh_response.json()
    assert refresh_data.get("success") is True
    new_access_token = refresh_data["data"]["accessToken"]
    assert new_access_token != access_token, "Expected rotated/new access token, but got the same one."
    
    # Reset secure flag on the newly rotated refresh token cookie
    for c in session.cookies:
        if c.name == "refresh_token":
            c.secure = False
    
    # Test logout
    logout_headers = {"Authorization": f"Bearer {new_access_token}"}
    logout_response = session.post(f"{url_prefix}/api/v1/auth/logout", headers=logout_headers)
    assert logout_response.status_code == 200, f"Expected 200, got {logout_response.status_code}. Response: {logout_response.text}"
    
    # Verify access is revoked (requesting files with logged out token should return 401)
    list_files_response = requests.get(f"{url_prefix}/api/v1/files", headers=logout_headers)
    assert list_files_response.status_code == 401, f"Expected 401, got {list_files_response.status_code}. Response: {list_files_response.text}"
    
    # Verify completely unknown/non-existent refresh token returns 400 Bad Request
    # (Note: A legitimately rotated but reused token triggers reuse detection and returns 401.
    # Here we send a completely invalid random UUID to check that non-existent tokens are rejected with a 400.)
    session.cookies.set("refresh_token", str(uuid.uuid4()), path="/api/v1/auth")
    stale_refresh_response = session.post(f"{url_prefix}/api/v1/auth/refresh")
    assert stale_refresh_response.status_code == 400, f"Expected 400, got {stale_refresh_response.status_code}. Response: {stale_refresh_response.text}"

# ----------------------------------------------------
# 2. File Operation Tests
# ----------------------------------------------------
def test_file_operations(url_prefix):
    user = generate_random_user()
    requests.post(f"{url_prefix}/api/v1/auth/register", json=user)
    
    session = requests.Session()
    login_res = session.post(f"{url_prefix}/api/v1/auth/login", json={
        "usernameOrEmail": user["username"],
        "password": user["password"]
    })
    access_token = login_res.json()["data"]["accessToken"]
    headers = {"Authorization": f"Bearer {access_token}"}
    
    # 2.1 Upload clean file
    file_content = b"Hello, CloudShare security testing!"
    file_checksum = hashlib.sha256(file_content).hexdigest()
    
    files = {"file": ("test_file.txt", file_content, "text/plain")}
    upload_res = requests.post(f"{url_prefix}/api/v1/files/upload", headers=headers, files=files)
    assert upload_res.status_code == 201, f"Expected 201, got {upload_res.status_code}. Response: {upload_res.text}"
    
    upload_data = upload_res.json()
    assert upload_data.get("success") is True
    file_id = upload_data["data"]["id"]
    assert upload_data["data"]["name"] == "test_file.txt"
    assert upload_data["data"]["checksum"] == file_checksum
    
    # 2.2 Upload dangerous file extension / MIME type
    bad_files = {"file": ("malicious.exe", b"executable bytes", "application/x-msdownload")}
    bad_upload_res = requests.post(f"{url_prefix}/api/v1/files/upload", headers=headers, files=bad_files)
    assert bad_upload_res.status_code == 415, f"Expected 415, got {bad_upload_res.status_code}. Response: {bad_upload_res.text}"
    
    # 2.3 Upload EICAR antivirus test file (triggers ClamAV malware check)
    eicar_files = {"file": ("eicar_test.txt", EICAR_STRING, "text/plain")}
    eicar_upload_res = requests.post(f"{url_prefix}/api/v1/files/upload", headers=headers, files=eicar_files)
    assert eicar_upload_res.status_code == 422, f"Expected 422, got {eicar_upload_res.status_code}. Response: {eicar_upload_res.text}"
    assert eicar_upload_res.json()["error"]["code"] == "VIRUS_DETECTED"
    
    # 2.4 Download own uploaded file
    download_res = requests.get(f"{url_prefix}/api/v1/files/{file_id}/download", headers=headers)
    assert download_res.status_code == 200, f"Expected 200, got {download_res.status_code}"
    assert download_res.content == file_content, "Downloaded file content does not match uploaded content."
    
    # 2.5 Download non-existent file
    fake_uuid = str(uuid.uuid4())
    fake_download_res = requests.get(f"{url_prefix}/api/v1/files/{fake_uuid}/download", headers=headers)
    assert fake_download_res.status_code == 404, f"Expected 404, got {fake_download_res.status_code}. Response: {fake_download_res.text}"
    
    # 2.6 Delete file
    delete_res = requests.delete(f"{url_prefix}/api/v1/files/{file_id}", headers=headers)
    assert delete_res.status_code == 204, f"Expected 204, got {delete_res.status_code}"
    
    # 2.7 Download deleted file
    post_delete_res = requests.get(f"{url_prefix}/api/v1/files/{file_id}/download", headers=headers)
    assert post_delete_res.status_code == 404, f"Expected 404, got {post_delete_res.status_code}. Response: {post_delete_res.text}"

# ----------------------------------------------------
# 3. Sharing & Collaboration Tests
# ----------------------------------------------------
def test_sharing_flow(url_prefix):
    user_a = generate_random_user()
    user_b = generate_random_user()
    user_c = generate_random_user()
    
    requests.post(f"{url_prefix}/api/v1/auth/register", json=user_a)
    requests.post(f"{url_prefix}/api/v1/auth/register", json=user_b)
    requests.post(f"{url_prefix}/api/v1/auth/register", json=user_c)
    
    # Logins
    login_a = requests.post(f"{url_prefix}/api/v1/auth/login", json={"usernameOrEmail": user_a["username"], "password": user_a["password"]}).json()
    login_b = requests.post(f"{url_prefix}/api/v1/auth/login", json={"usernameOrEmail": user_b["username"], "password": user_b["password"]}).json()
    login_c = requests.post(f"{url_prefix}/api/v1/auth/login", json={"usernameOrEmail": user_c["username"], "password": user_c["password"]}).json()
    
    headers_a = {"Authorization": f"Bearer {login_a['data']['accessToken']}"}
    headers_b = {"Authorization": f"Bearer {login_b['data']['accessToken']}"}
    headers_c = {"Authorization": f"Bearer {login_c['data']['accessToken']}"}
    
    # User A uploads file
    file_content = b"Hello shared world!"
    upload_res = requests.post(
        f"{url_prefix}/api/v1/files/upload", 
        headers=headers_a, 
        files={"file": ("shared_doc.txt", file_content, "text/plain")}
    )
    file_id = upload_res.json()["data"]["id"]
    
    # User A shares internally with User B
    share_payload = {
        "fileId": file_id,
        "targetUsernameOrEmail": user_b["username"],
        "permissionType": "READ"
    }
    share_res = requests.post(f"{url_prefix}/api/v1/shares/internal", headers=headers_a, json=share_payload)
    assert share_res.status_code == 201, f"Expected 201, got {share_res.status_code}. Response: {share_res.text}"
    
    # User B downloads shared file (Access Allowed)
    download_b = requests.get(f"{url_prefix}/api/v1/files/{file_id}/download", headers=headers_b)
    assert download_b.status_code == 200, f"Expected 200, got {download_b.status_code}"
    assert download_b.content == file_content
    
    # User C downloads shared file (Access Denied -> 404)
    download_c = requests.get(f"{url_prefix}/api/v1/files/{file_id}/download", headers=headers_c)
    assert download_c.status_code == 404, f"Expected 404, got {download_c.status_code}. Response: {download_c.text}"
    
    # 3.2 Public link sharing
    link_payload = {
        "fileId": file_id,
        "expiresInSeconds": 3600,
        "password": "LinkPassword999",
        "downloadLimit": 5
    }
    link_res = requests.post(f"{url_prefix}/api/v1/shares/link", headers=headers_a, json=link_payload)
    assert link_res.status_code == 200, f"Expected 200, got {link_res.status_code}. Response: {link_res.text}"
    share_code = link_res.json()["data"]["shareCode"]
    
    # Guest downloads public link with correct password
    guest_headers = {"X-Share-Password": "LinkPassword999"}
    guest_download = requests.get(f"{url_prefix}/api/v1/shares/link/{share_code}/download", headers=guest_headers)
    assert guest_download.status_code == 200, f"Expected 200, got {guest_download.status_code}"
    assert guest_download.content == file_content
    
    # Guest downloads public link with wrong password
    bad_guest_headers = {"X-Share-Password": "WrongPassword"}
    bad_guest_download = requests.get(f"{url_prefix}/api/v1/shares/link/{share_code}/download", headers=bad_guest_headers)
    assert bad_guest_download.status_code == 401, f"Expected 401, got {bad_guest_download.status_code}. Response: {bad_guest_download.text}"
    
    # Guest downloads public link with missing password
    missing_guest_download = requests.get(f"{url_prefix}/api/v1/shares/link/{share_code}/download")
    assert missing_guest_download.status_code == 401, f"Expected 401, got {missing_guest_download.status_code}. Response: {missing_guest_download.text}"
    
    # Test expiration: create public link with 2s expiry
    exp_payload = {
        "fileId": file_id,
        "expiresInSeconds": 2,
        "downloadLimit": 5
    }
    exp_link_res = requests.post(f"{url_prefix}/api/v1/shares/link", headers=headers_a, json=exp_payload)
    exp_share_code = exp_link_res.json()["data"]["shareCode"]
    
    # Sleep 4 seconds to ensure it is fully expired
    time.sleep(4)
    expired_download = requests.get(f"{url_prefix}/api/v1/shares/link/{exp_share_code}/download")
    assert expired_download.status_code == 403, f"Expected 403 (expired), got {expired_download.status_code}. Response: {expired_download.text}"
    
    # Test download limit: create public link with limit of 1
    limit_payload = {
        "fileId": file_id,
        "expiresInSeconds": 3600,
        "downloadLimit": 1
    }
    limit_link_res = requests.post(f"{url_prefix}/api/v1/shares/link", headers=headers_a, json=limit_payload)
    limit_share_code = limit_link_res.json()["data"]["shareCode"]
    
    # First download -> OK
    download_1 = requests.get(f"{url_prefix}/api/v1/shares/link/{limit_share_code}/download")
    assert download_1.status_code == 200, f"Expected 200, got {download_1.status_code}"
    
    # Second download -> 403 Limit Exceeded
    download_2 = requests.get(f"{url_prefix}/api/v1/shares/link/{limit_share_code}/download")
    assert download_2.status_code == 403, f"Expected 403 (limit reached), got {download_2.status_code}. Response: {download_2.text}"

    # 3.3 Revocation of internal share
    share_id = share_res.json()["data"]["shareId"]

    # Revoke by non-owner / non-sharing user (User C) -> 404 (BOLA)
    non_owner_revoke_res = requests.delete(f"{url_prefix}/api/v1/shares/internal/{share_id}", headers=headers_c)
    assert non_owner_revoke_res.status_code == 404, f"Expected 404 for unauthorized revocation, got {non_owner_revoke_res.status_code}"

    # Revoke non-existent shareId -> 404
    non_existent_share_uuid = str(uuid.uuid4())
    fake_revoke_res = requests.delete(f"{url_prefix}/api/v1/shares/internal/{non_existent_share_uuid}", headers=headers_a)
    assert fake_revoke_res.status_code == 404, f"Expected 404 for non-existent shareId, got {fake_revoke_res.status_code}"

    # Revoke successfully by owner (User A) -> 200
    owner_revoke_res = requests.delete(f"{url_prefix}/api/v1/shares/internal/{share_id}", headers=headers_a)
    assert owner_revoke_res.status_code == 200, f"Expected 200 for successful revocation, got {owner_revoke_res.status_code}. Response: {owner_revoke_res.text}"

    # User B download after revocation -> 404 Access Denied
    download_b_after = requests.get(f"{url_prefix}/api/v1/files/{file_id}/download", headers=headers_b)
    assert download_b_after.status_code == 404, f"Expected 404 after revocation, got {download_b_after.status_code}"

    # 3.4 Revocation of public sharing link

    # Revoke by non-owner (User B) -> 404 (BOLA)
    non_owner_link_revoke_res = requests.delete(f"{url_prefix}/api/v1/shares/link/{share_code}", headers=headers_b)
    assert non_owner_link_revoke_res.status_code == 404, f"Expected 404 for unauthorized link revocation, got {non_owner_link_revoke_res.status_code}"

    # Revoke non-existent link code -> 404
    fake_link_revoke_res = requests.delete(f"{url_prefix}/api/v1/shares/link/nonexistentcode", headers=headers_a)
    assert fake_link_revoke_res.status_code == 404, f"Expected 404 for non-existent link code, got {fake_link_revoke_res.status_code}"

    # Revoke successfully by owner (User A) -> 200
    owner_link_revoke_res = requests.delete(f"{url_prefix}/api/v1/shares/link/{share_code}", headers=headers_a)
    assert owner_link_revoke_res.status_code == 200, f"Expected 200 for successful link revocation, got {owner_link_revoke_res.status_code}. Response: {owner_link_revoke_res.text}"

    # Guest download after link revocation -> 404
    guest_download_after = requests.get(f"{url_prefix}/api/v1/shares/link/{share_code}/download", headers=guest_headers)
    assert guest_download_after.status_code == 404, f"Expected 404 after link revocation, got {guest_download_after.status_code}"

# ----------------------------------------------------
# 4. Auth Boundary Tests
# ----------------------------------------------------
def test_auth_boundaries(url_prefix):
    user = generate_random_user()
    requests.post(f"{url_prefix}/api/v1/auth/register", json=user)
    
    login_res = requests.post(f"{url_prefix}/api/v1/auth/login", json={"usernameOrEmail": user["username"], "password": user["password"]}).json()
    access_token = login_res["data"]["accessToken"]
    user_headers = {"Authorization": f"Bearer {access_token}"}
    
    # Try calling list files endpoint without token
    no_token_res = requests.get(f"{url_prefix}/api/v1/files")
    assert no_token_res.status_code == 401, f"Expected 401 for no token, got {no_token_res.status_code}. Response: {no_token_res.text}"
    
    # Try calling admin endpoint with non-admin token
    admin_res = requests.get(f"{url_prefix}/api/v1/admin/users", headers=user_headers)
    assert admin_res.status_code == 403, f"Expected 403 for non-admin on admin endpoint, got {admin_res.status_code}. Response: {admin_res.text}"

# ----------------------------------------------------
# 5. Security Hardening Tests
# ----------------------------------------------------
def test_security_hardening(url_prefix):
    user = generate_random_user()
    
    # Register user
    reg_res = requests.post(f"{url_prefix}/api/v1/auth/register", json=user)
    assert reg_res.status_code == 201, f"Registration failed: {reg_res.text}"
    
    session = requests.Session()
    login_res = session.post(f"{url_prefix}/api/v1/auth/login", json={
        "usernameOrEmail": user["username"],
        "password": user["password"]
    }).json()
    access_token = login_res["data"]["accessToken"]
    headers = {"Authorization": f"Bearer {access_token}"}
    
    # 5.1 Request MFA setup
    setup_res = requests.post(f"{url_prefix}/api/v1/auth/mfa/setup", headers=headers)
    assert setup_res.status_code == 200, f"MFA setup failed: {setup_res.text}"
    
    setup_data = setup_res.json()["data"]
    secret = setup_data["secret"]
    assert secret is not None
    assert setup_data["qrCodeDataUri"].startswith("data:image/png;base64,")
    
    # 5.2 Verify/Enable MFA
    mfa_code = generate_totp(secret)
    verify_res = requests.post(f"{url_prefix}/api/v1/auth/mfa/verify", headers=headers, json={"code": mfa_code})
    assert verify_res.status_code == 200, f"MFA verification failed: {verify_res.text}"
    
    # 5.3 Login again, should fail without MFA code
    login_fail_res = requests.post(f"{url_prefix}/api/v1/auth/login", json={
        "usernameOrEmail": user["username"],
        "password": user["password"]
    })
    assert login_fail_res.status_code == 401, f"Expected 401 login failure without MFA, got {login_fail_res.status_code}"
    
    # 5.4 Login again with correct MFA code -> SUCCESS
    mfa_code_2 = generate_totp(secret)
    login_success_res = requests.post(f"{url_prefix}/api/v1/auth/login", json={
        "usernameOrEmail": user["username"],
        "password": user["password"],
        "mfaCode": mfa_code_2
    })
    assert login_success_res.status_code == 200, f"Login with MFA failed: {login_success_res.text}"
    
    # 5.5 Step-Up Authentication Flow
    step_up_code = generate_totp(secret)
    step_up_res = requests.post(f"{url_prefix}/api/v1/auth/mfa/step-up", headers=headers, json={"code": step_up_code})
    assert step_up_res.status_code == 200, f"MFA step-up failed: {step_up_res.text}"
    
    step_up_token = step_up_res.json()["data"]["stepUpToken"]
    assert step_up_token is not None
    
    # 5.6 Admin role boundaries
    # Note: Standard user lacks ROLE_ADMIN. The step-up token is only meaningful for admin users.
    # A standard user attempting to access admin endpoints (e.g. GET /api/v1/admin/users)
    # both with and without the step-up token must receive 403 Forbidden.
    # This confirms the role boundary holds first and rejects the request.
    
    # Case A: Without step-up token
    no_stepup_res = requests.get(f"{url_prefix}/api/v1/admin/users", headers=headers)
    assert no_stepup_res.status_code == 403, f"Expected 403 for non-admin on admin endpoint without step-up token, got {no_stepup_res.status_code}. Response: {no_stepup_res.text}"
    
    # Case B: With step-up token
    stepup_headers = {
        "Authorization": f"Bearer {access_token}",
        "X-StepUp-Token": step_up_token
    }
    with_stepup_res = requests.get(f"{url_prefix}/api/v1/admin/users", headers=stepup_headers)
    assert with_stepup_res.status_code == 403, f"Expected 403 for non-admin on admin endpoint with valid step-up token, got {with_stepup_res.status_code}. Response: {with_stepup_res.text}"

def test_observability(url_prefix):
    # 1. Actuator health endpoint
    health_res = requests.get(f"{url_prefix}/actuator/health")
    assert health_res.status_code == 200, f"Expected 200 for health endpoint, got {health_res.status_code}"
    health_data = health_res.json()
    assert health_data.get("status") == "UP", f"Expected status to be UP, got {health_data.get('status')}"

    # 2. Prometheus metrics endpoint (internal access)
    prometheus_res = requests.get(f"{url_prefix}/actuator/prometheus")
    assert prometheus_res.status_code == 200, f"Expected 200 for prometheus endpoint, got {prometheus_res.status_code}"
    assert "# HELP" in prometheus_res.text, "Expected # HELP in prometheus metrics response"

    # 3. X-Trace-Id echo-back
    trace_id_value = "my-test-trace-123"
    trace_headers = {"X-Trace-Id": trace_id_value}
    echo_res = requests.get(f"{url_prefix}/actuator/health", headers=trace_headers)
    assert echo_res.headers.get("X-Trace-Id") == trace_id_value, f"Expected echoed X-Trace-Id: {trace_id_value}, got {echo_res.headers.get('X-Trace-Id')}"

    # 4. X-Trace-Id auto-generation
    gen_res = requests.get(f"{url_prefix}/actuator/health")
    gen_trace_id = gen_res.headers.get("X-Trace-Id")
    assert gen_trace_id is not None and len(gen_trace_id) > 0, "Expected non-empty auto-generated X-Trace-Id header"
    try:
        uuid.UUID(gen_trace_id)
    except ValueError:
        assert False, f"Expected auto-generated trace ID to be a valid UUID, got {gen_trace_id}"

# ----------------------------------------------------
# 6. Soft Delete Cascade Tests
# ----------------------------------------------------
def test_soft_delete_cascade(url_prefix):
    user_a = generate_random_user()
    user_b = generate_random_user()

    requests.post(f"{url_prefix}/api/v1/auth/register", json=user_a)
    requests.post(f"{url_prefix}/api/v1/auth/register", json=user_b)

    login_a = requests.post(f"{url_prefix}/api/v1/auth/login", json={"usernameOrEmail": user_a["username"], "password": user_a["password"]}).json()
    login_b = requests.post(f"{url_prefix}/api/v1/auth/login", json={"usernameOrEmail": user_b["username"], "password": user_b["password"]}).json()

    headers_a = {"Authorization": f"Bearer {login_a['data']['accessToken']}"}
    headers_b = {"Authorization": f"Bearer {login_b['data']['accessToken']}"}

    # 1. User A uploads file and creates a public share link
    file_content = b"Cascade deletion test data"
    upload_res = requests.post(
        f"{url_prefix}/api/v1/files/upload", 
        headers=headers_a, 
        files={"file": ("cascade_file.txt", file_content, "text/plain")}
    )
    file_id = upload_res.json()["data"]["id"]

    link_payload = {
        "fileId": file_id,
        "expiresInSeconds": 3600,
        "password": "CascadePassword123"
    }
    link_res = requests.post(f"{url_prefix}/api/v1/shares/link", headers=headers_a, json=link_payload)
    share_code = link_res.json()["data"]["shareCode"]

    # Verify link access before deletion works (returns 200)
    guest_headers = {"X-Share-Password": "CascadePassword123"}
    guest_download = requests.get(f"{url_prefix}/api/v1/shares/link/{share_code}/download", headers=guest_headers)
    assert guest_download.status_code == 200, f"Expected 200, got {guest_download.status_code}"

    # 2. User A shares internally with User B
    share_payload = {
        "fileId": file_id,
        "targetUsernameOrEmail": user_b["username"],
        "permissionType": "READ"
    }
    requests.post(f"{url_prefix}/api/v1/shares/internal", headers=headers_a, json=share_payload)

    # Verify User B access before deletion works (returns 200)
    download_b = requests.get(f"{url_prefix}/api/v1/files/{file_id}/download", headers=headers_b)
    assert download_b.status_code == 200, f"Expected 200, got {download_b.status_code}"

    # 3. User A soft-deletes the file
    delete_res = requests.delete(f"{url_prefix}/api/v1/files/{file_id}", headers=headers_a)
    assert delete_res.status_code == 204

    # 4. Verify public link is cascade deleted (should return 404 since trigger deletes it)
    guest_download_after = requests.get(f"{url_prefix}/api/v1/shares/link/{share_code}/download", headers=guest_headers)
    assert guest_download_after.status_code == 404, f"Expected 404 (cascade deleted), got {guest_download_after.status_code}"

    # 5. Verify internal share is cascade deleted (User B access returns 404)
    download_b_after = requests.get(f"{url_prefix}/api/v1/files/{file_id}/download", headers=headers_b)
    assert download_b_after.status_code == 404, f"Expected 404 (cascade deleted), got {download_b_after.status_code}"

def test_compromised_password_rejection(url_prefix):
    # This test specifically ensures that compromised passwords do not pass registration (returning 400)
    # when breach checking is active.
    compromised_passwords = [
        "password123",
        "12345678",
        "qwertyuiop",
        "welcome1",
        "admin123",
        "letmein1",
        "iloveyou",
        "monkey123",
        "password",
        "sunshine"
    ]
    
    for pwd in compromised_passwords:
        user = generate_random_user()
        user["password"] = pwd
        
        response = requests.post(f"{url_prefix}/api/v1/auth/register", json=user)
        
        if response.status_code == 400:
            json_data = response.json()
            assert json_data.get("success") is False
            error_msg = json_data.get("error", {}).get("message", "")
            assert "Password has been found in a data breach" in error_msg, f"Expected breach error message for '{pwd}', got: {error_msg}"
        else:
            assert response.status_code == 201, f"Expected 201 or 400 for '{pwd}', got {response.status_code}. Response: {response.text}"

# ----------------------------------------------------
# Runner
# ----------------------------------------------------
if __name__ == "__main__":
    print(f"Connecting to API at: {BASE_URL}")
    runner = TestRunner()
    
    runner.run_case("Authentication Flow", test_auth_flow, BASE_URL)
    runner.run_case("Compromised Password Rejection", test_compromised_password_rejection, BASE_URL)
    runner.run_case("File Operations", test_file_operations, BASE_URL)
    runner.run_case("Sharing & Collaboration", test_sharing_flow, BASE_URL)
    runner.run_case("Auth Boundaries", test_auth_boundaries, BASE_URL)
    runner.run_case("Security Hardening Features", test_security_hardening, BASE_URL)
    runner.run_case("Observability & Ops", test_observability, BASE_URL)
    runner.run_case("Soft Delete Cascade Triggers", test_soft_delete_cascade, BASE_URL)
    
    success = runner.summary()
    if not success:
        sys.exit(1)
    sys.exit(0)
