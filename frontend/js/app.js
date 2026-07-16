/**
 * CloudShare Application Manager
 * Coordinates the SPA router, authentication state, UI events,
 * DOM rendering with XSS safety, MFA step-up timing, and notifications.
 */

import { api } from './api.js';

// Application State
const state = {
    user: null, // User details: { id, username, email, roles, mfaRequired }
    files: {
        content: [],
        page: 0,
        size: 10,
        sort: 'createdAt,desc',
        searchQuery: '',
        totalPages: 1
    },
    sharedFiles: {
        content: [],
        page: 0,
        size: 10,
        totalPages: 1
    },
    admin: {
        users: { content: [], page: 0, totalPages: 1 },
        logs: { content: [], page: 0, totalPages: 1, userIdFilter: '', actionFilter: '' }
    },
    stepUpTimer: null
};

// Initializer
document.addEventListener('DOMContentLoaded', () => {
    initApp();
});

function initApp() {
    // Register API Authentication Failure callback (Redirects to Login on expired sessions)
    api.registerAuthFailureCallback((message) => {
        showToast(message, 'danger');
        clearSession();
    });

    // Check if user is already logged in (try refresh first)
    checkActiveSession();

    // Bind Event Listeners (Strict compliance: no inline handlers in HTML)
    bindGlobalEvents();
}

/**
 * Helper to decode JWT token payload on client side
 */
function parseJwt(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(window.atob(base64).split('').map(function (c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
        return JSON.parse(jsonPayload);
    } catch (e) {
        return null;
    }
}

/**
 * Perform initial login state verification by hitting refresh endpoint.
 * This runs silently when the user loads the app.
 */
async function checkActiveSession() {
    try {
        await api.performTokenRefresh();

        const token = api.getAccessToken();
        if (token) {
            const claims = parseJwt(token);
            if (claims) {
                state.user = {
                    id: claims.sub,
                    username: claims.username,
                    roles: claims.roles,
                    mfaRequired: false
                };
                setupShell();
                router();
            } else {
                clearSession();
                router();
            }
        } else {
            clearSession();
            router();
        }
    } catch (e) {
        clearSession();
        router();
    }
}

/**
 * Setup layout shell elements after a successful authentication
 */
function setupShell() {
    const appShell = document.getElementById('app-shell');
    const authGateway = document.getElementById('auth-gateway');
    const headerUsername = document.getElementById('header-username');
    const navAdminBtn = document.getElementById('nav-admin-btn');

    authGateway.classList.add('hidden');
    appShell.classList.remove('hidden');

    if (state.user) {
        headerUsername.textContent = state.user.username;

        // Role check to toggle Admin button visibility
        const isAdmin = state.user.roles && state.user.roles.includes('ROLE_ADMIN');
        if (isAdmin) {
            navAdminBtn.classList.remove('hidden');
        } else {
            navAdminBtn.classList.add('hidden');
        }
    }
}

/**
 * Tear down session data, reset tokens and routing
 */
function clearSession() {
    state.user = null;
    api.clearTokens();

    if (state.stepUpTimer) {
        clearTimeout(state.stepUpTimer);
        state.stepUpTimer = null;
    }

    // Hide dashboard shell, show login gateway
    document.getElementById('app-shell').classList.add('hidden');
    document.getElementById('auth-gateway').classList.remove('hidden');
    document.getElementById('public-share-gateway').classList.add('hidden');

    // Reset hash to login page if it's not a public share link or the register page
    const currentHash = window.location.hash;
    if (!currentHash.startsWith('#share/') && currentHash !== '#register') {
        window.location.hash = '#login';
    }
}

/* --- SPA Router --- */
function router() {
    const hash = window.location.hash || '#dashboard';

    // De-activate all view sections and navigation buttons
    document.querySelectorAll('.app-view').forEach(view => view.classList.add('hidden'));
    document.querySelectorAll('.nav-btn').forEach(btn => btn.classList.remove('active'));

    // Handle Public Share route first (independent of authentication)
    if (hash.startsWith('#share/')) {
        const shareCode = hash.split('/')[1];
        if (shareCode) {
            showPublicShareView(shareCode);
            return;
        }
    }

    // Protect regular views - Redirect to login if anonymous
    if (!api.getAccessToken()) {
        showAuthCard(hash === '#register' ? 'register' : 'login');
        return;
    }

    // Standard logged-in views
    if (hash === '#dashboard') {
        document.getElementById('view-dashboard').classList.remove('hidden');
        document.getElementById('nav-dashboard-btn').classList.add('active');
        loadFilesDashboard();
    } else if (hash === '#mfa') {
        document.getElementById('view-mfa').classList.remove('hidden');
        document.getElementById('nav-mfa-btn').classList.add('active');
        loadMfaSettings();
    } else if (hash === '#admin') {
        // Enforce Admin role restriction
        const isAdmin = state.user.roles && state.user.roles.includes('ROLE_ADMIN');
        if (!isAdmin) {
            window.location.hash = '#dashboard';
            showToast('Access Denied: Administrative privileges required.', 'danger');
            return;
        }

        // Verify Step-up Token state
        if (!api.getStepUpToken()) {
            triggerAdminStepUp();
        } else {
            document.getElementById('view-admin').classList.remove('hidden');
            document.getElementById('nav-admin-btn').classList.add('active');
            loadAdminPanel();
        }
    } else {
        // Default Fallback
        window.location.hash = '#dashboard';
    }
}

function showAuthCard(cardType) {
    document.getElementById('app-shell').classList.add('hidden');
    document.getElementById('public-share-gateway').classList.add('hidden');
    document.getElementById('auth-gateway').classList.remove('hidden');

    if (cardType === 'register') {
        document.getElementById('auth-login-card').classList.add('hidden');
        document.getElementById('auth-register-card').classList.remove('hidden');
    } else {
        document.getElementById('auth-login-card').classList.remove('hidden');
        document.getElementById('auth-register-card').classList.add('hidden');
    }
}

/* --- Global Event Bindings --- */
function bindGlobalEvents() {
    // Router binds
    window.addEventListener('hashchange', router);
    window.addEventListener('load', router);

    // Nav navigation buttons
    document.getElementById('nav-dashboard-btn').addEventListener('click', () => { window.location.hash = '#dashboard'; });
    document.getElementById('nav-mfa-btn').addEventListener('click', () => { window.location.hash = '#mfa'; });
    document.getElementById('nav-admin-btn').addEventListener('click', () => { window.location.hash = '#admin'; });
    document.getElementById('nav-logout-btn').addEventListener('click', handleLogout);

    // Auth toggle buttons
    document.getElementById('switch-to-register-btn').addEventListener('click', () => { window.location.hash = '#register'; });
    document.getElementById('switch-to-login-btn').addEventListener('click', () => { window.location.hash = '#login'; });

    // Forms submissions
    document.getElementById('login-form').addEventListener('submit', handleLoginSubmit);
    document.getElementById('register-form').addEventListener('submit', handleRegisterSubmit);
    document.getElementById('admin-stepup-form').addEventListener('submit', handleAdminStepUpSubmit);
    document.getElementById('admin-stepup-cancel-btn').addEventListener('click', cancelAdminStepUp);

    // Dashboard: Files filtering, sort, and pagination
    document.getElementById('file-search-input').addEventListener('input', debounce((e) => {
        state.files.searchQuery = e.target.value;
        state.files.page = 0;
        loadFilesDashboard();
    }, 300));
    document.getElementById('file-sort-select').addEventListener('change', (e) => {
        state.files.sort = e.target.value;
        state.files.page = 0;
        loadFilesDashboard();
    });
    document.getElementById('files-prev-btn').addEventListener('click', () => {
        if (state.files.page > 0) {
            state.files.page--;
            loadFilesDashboard();
        }
    });
    document.getElementById('files-next-btn').addEventListener('click', () => {
        if (state.files.page < state.files.totalPages - 1) {
            state.files.page++;
            loadFilesDashboard();
        }
    });

    document.getElementById('shared-prev-btn').addEventListener('click', () => {
        if (state.sharedFiles.page > 0) {
            state.sharedFiles.page--;
            loadSharedFiles();
        }
    });
    document.getElementById('shared-next-btn').addEventListener('click', () => {
        if (state.sharedFiles.page < state.sharedFiles.totalPages - 1) {
            state.sharedFiles.page++;
            loadSharedFiles();
        }
    });

    // Dashboard: Drag and Drop Upload
    const dropZone = document.getElementById('drop-zone');
    const fileInput = document.getElementById('file-input');

    dropZone.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', (e) => {
        handleFilesUpload(e.target.files);
    });

    ['dragenter', 'dragover'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropZone.classList.add('drag-active');
        }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-active');
        }, false);
    });

    dropZone.addEventListener('drop', (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        handleFilesUpload(files);
    }, false);

    // Modals Sharing close trigger
    document.getElementById('share-modal-close-btn').addEventListener('click', closeShareModal);

    // Sharing modal tabs toggle
    const tabInternalBtn = document.getElementById('tab-internal-btn');
    const tabPublicBtn = document.getElementById('tab-public-btn');
    const tabInternalContent = document.getElementById('tab-internal-content');
    const tabPublicContent = document.getElementById('tab-public-content');

    tabInternalBtn.addEventListener('click', () => {
        tabInternalBtn.classList.add('active');
        tabPublicBtn.classList.remove('active');
        tabInternalContent.classList.remove('hidden');
        tabPublicContent.classList.add('hidden');
    });

    tabPublicBtn.addEventListener('click', () => {
        tabPublicBtn.classList.add('active');
        tabInternalBtn.classList.remove('active');
        tabPublicContent.classList.remove('hidden');
        tabInternalContent.classList.add('hidden');
    });

    // Sharing Submit Action Forms
    document.getElementById('share-internal-form').addEventListener('submit', handleInternalShareSubmit);
    document.getElementById('share-public-form').addEventListener('submit', handlePublicLinkSubmit);
    document.getElementById('copy-public-link-btn').addEventListener('click', copyPublicLinkToClipboard);

    // MFA Configuration Forms
    document.getElementById('mfa-verify-form').addEventListener('submit', handleMfaVerifySubmit);

    // Admin Logs Filtering & Pagination
    document.getElementById('admin-logs-user-filter').addEventListener('input', debounce((e) => {
        state.admin.logs.userIdFilter = e.target.value.trim();
        state.admin.logs.page = 0;
        loadAdminLogs();
    }, 300));
    document.getElementById('admin-logs-action-filter').addEventListener('change', (e) => {
        state.admin.logs.actionFilter = e.target.value;
        state.admin.logs.page = 0;
        loadAdminLogs();
    });

    document.getElementById('admin-users-prev-btn').addEventListener('click', () => {
        if (state.admin.users.page > 0) {
            state.admin.users.page--;
            loadAdminUsers();
        }
    });
    document.getElementById('admin-users-next-btn').addEventListener('click', () => {
        if (state.admin.users.page < state.admin.users.totalPages - 1) {
            state.admin.users.page++;
            loadAdminUsers();
        }
    });

    document.getElementById('admin-logs-prev-btn').addEventListener('click', () => {
        if (state.admin.logs.page > 0) {
            state.admin.logs.page--;
            loadAdminLogs();
        }
    });
    document.getElementById('admin-logs-next-btn').addEventListener('click', () => {
        if (state.admin.logs.page < state.admin.logs.totalPages - 1) {
            state.admin.logs.page++;
            loadAdminLogs();
        }
    });

    // Public share View events
    document.getElementById('public-download-btn').addEventListener('click', handlePublicDownload);
}

/* --- Authentication actions --- */

async function handleLoginSubmit(e) {
    e.preventDefault();
    const usernameOrEmail = document.getElementById('login-username').value;
    const password = document.getElementById('login-password').value;
    const mfaCode = document.getElementById('login-mfa').value;

    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    try {
        const res = await api.login(usernameOrEmail, password, mfaCode || null);
        if (res.success && res.data) {
            state.user = res.data.user;
            setupShell();
            window.location.hash = '#dashboard';
            showToast(`Welcome back, ${state.user.username}!`, 'success');

            // Clear inputs
            document.getElementById('login-username').value = '';
            document.getElementById('login-password').value = '';
            document.getElementById('login-mfa').value = '';
        }
    } catch (err) {
        showToast(err.message, 'danger');
    } finally {
        btn.disabled = false;
    }
}

async function handleRegisterSubmit(e) {
    e.preventDefault();
    const username = document.getElementById('register-username').value;
    const email = document.getElementById('register-email').value;
    const password = document.getElementById('register-password').value;

    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    try {
        const res = await api.register(username, email, password);
        if (res.success) {
            showToast('Account registered successfully. Please sign in.', 'success');
            showAuthCard('login');

            // Populate login card username
            document.getElementById('login-username').value = username;

            // Clear registration inputs
            document.getElementById('register-username').value = '';
            document.getElementById('register-email').value = '';
            document.getElementById('register-password').value = '';
        }
    } catch (err) {
        showToast(err.message, 'danger');
    } finally {
        btn.disabled = false;
    }
}

async function handleLogout() {
    try {
        await api.logout();
        showToast('Logout successful', 'success');
    } catch (err) {
        // Ignored, state cleared regardless
    } finally {
        clearSession();
    }
}

/* --- Files Dashboard Logic --- */

async function loadFilesDashboard() {
    try {
        const res = await api.listFiles(state.files.page, state.files.size, state.files.sort);
        if (res.success && res.data) {
            state.files.content = res.data.content || [];
            state.files.totalPages = res.data.totalPages || 1;
            renderFilesTable();
        }
    } catch (err) {
        showToast(`Failed to load files: ${err.message}`, 'danger');
    }
    loadSharedFiles();
}

async function loadSharedFiles() {
    try {
        const res = await api.listSharedWithMe(state.sharedFiles.page, state.sharedFiles.size);
        if (res.success && res.data) {
            state.sharedFiles.content = res.data.content || [];
            state.sharedFiles.totalPages = res.data.totalPages || 1;
            renderSharedFilesTable();
        }
    } catch (err) {
        showToast(`Failed to load shared files: ${err.message}`, 'danger');
    }
}

function renderFilesTable() {
    const tbody = document.getElementById('files-tbody');
    const emptyState = document.getElementById('files-empty-state');

    // Clear rows safely
    tbody.textContent = '';

    // Filter files based on client-side search query
    let filteredFiles = state.files.content;
    if (state.files.searchQuery) {
        const query = state.files.searchQuery.toLowerCase();
        filteredFiles = filteredFiles.filter(file => file.name.toLowerCase().includes(query));
    }

    if (filteredFiles.length === 0) {
        emptyState.classList.remove('hidden');
        document.getElementById('files-prev-btn').disabled = true;
        document.getElementById('files-next-btn').disabled = true;
        document.getElementById('files-page-info').textContent = 'Page 1 of 1';
        return;
    }

    emptyState.classList.add('hidden');

    // Render files safely (XSS defense: use textContent and element construction)
    filteredFiles.forEach(file => {
        const tr = document.createElement('tr');

        // File name & Icon
        const tdName = document.createElement('td');
        const fileDiv = document.createElement('div');
        fileDiv.className = 'file-name-cell';

        const fileIcon = document.createElement('span');
        fileIcon.className = 'file-icon-s';
        fileIcon.textContent = getFileEmoji(file.name);

        const nameSpan = document.createElement('span');
        nameSpan.className = 'file-name-text';
        nameSpan.textContent = file.name;

        fileDiv.appendChild(fileIcon);
        fileDiv.appendChild(nameSpan);
        tdName.appendChild(fileDiv);

        // Size
        const tdSize = document.createElement('td');
        tdSize.textContent = formatBytes(file.sizeBytes);

        // Upload Time
        const tdTime = document.createElement('td');
        tdTime.textContent = new Date(file.uploadedAt).toLocaleString();

        // Actions
        const tdActions = document.createElement('td');
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'row-actions';

        // Download Action
        const btnDownload = document.createElement('button');
        btnDownload.className = 'action-btn action-btn-download';
        btnDownload.textContent = '📥';
        btnDownload.title = 'Secure Download';
        btnDownload.setAttribute('aria-label', 'Secure Download');
        btnDownload.addEventListener('click', () => handleFileDownload(file.id, file.name));

        // Share Action
        const btnShare = document.createElement('button');
        btnShare.className = 'action-btn action-btn-share';
        btnShare.textContent = '🔗';
        btnShare.title = 'Share File';
        btnShare.setAttribute('aria-label', 'Share File');
        btnShare.addEventListener('click', () => openShareModal(file.id, file.name));

        // Delete Action
        const btnDelete = document.createElement('button');
        btnDelete.className = 'action-btn action-btn-delete';
        btnDelete.textContent = '🗑️';
        btnDelete.title = 'Delete File';
        btnDelete.setAttribute('aria-label', 'Delete File');
        btnDelete.addEventListener('click', () => handleFileDelete(file.id, file.name));

        actionsDiv.appendChild(btnDownload);
        actionsDiv.appendChild(btnShare);
        actionsDiv.appendChild(btnDelete);
        tdActions.appendChild(actionsDiv);

        tr.appendChild(tdName);
        tr.appendChild(tdSize);
        tr.appendChild(tdTime);
        tr.appendChild(tdActions);

        tbody.appendChild(tr);
    });

    // Update pagination buttons state
    document.getElementById('files-prev-btn').disabled = state.files.page === 0;
    document.getElementById('files-next-btn').disabled = state.files.page >= state.files.totalPages - 1;
    document.getElementById('files-page-info').textContent = `Page ${state.files.page + 1} of ${state.files.totalPages}`;
}

function renderSharedFilesTable() {
    const tbody = document.getElementById('shared-tbody');
    const emptyState = document.getElementById('shared-empty-state');

    // Clear rows safely
    tbody.textContent = '';

    const files = state.sharedFiles.content;

    if (files.length === 0) {
        emptyState.classList.remove('hidden');
        document.getElementById('shared-prev-btn').disabled = true;
        document.getElementById('shared-next-btn').disabled = true;
        document.getElementById('shared-page-info').textContent = 'Page 1 of 1';
        return;
    }

    emptyState.classList.add('hidden');

    // Render files safely (XSS defense: use textContent and element construction)
    files.forEach(file => {
        const tr = document.createElement('tr');

        // File name & Icon
        const tdName = document.createElement('td');
        const fileDiv = document.createElement('div');
        fileDiv.className = 'file-name-cell';

        const fileIcon = document.createElement('span');
        fileIcon.className = 'file-icon-s';
        fileIcon.textContent = getFileEmoji(file.name);

        const nameSpan = document.createElement('span');
        nameSpan.className = 'file-name-text';
        nameSpan.textContent = file.name;

        fileDiv.appendChild(fileIcon);
        fileDiv.appendChild(nameSpan);
        tdName.appendChild(fileDiv);

        // Size
        const tdSize = document.createElement('td');
        tdSize.textContent = formatBytes(file.sizeBytes);

        // Shared By
        const tdSharedBy = document.createElement('td');
        tdSharedBy.textContent = file.sharedByUsername;

        // Shared Time
        const tdTime = document.createElement('td');
        tdTime.textContent = new Date(file.sharedAt).toLocaleString();

        // Actions
        const tdActions = document.createElement('td');
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'row-actions';

        // Download Action
        const btnDownload = document.createElement('button');
        btnDownload.className = 'action-btn action-btn-download';
        btnDownload.textContent = '📥';
        btnDownload.title = 'Secure Download';
        btnDownload.setAttribute('aria-label', 'Secure Download');
        btnDownload.addEventListener('click', () => handleFileDownload(file.id, file.name));

        actionsDiv.appendChild(btnDownload);
        tdActions.appendChild(actionsDiv);

        tr.appendChild(tdName);
        tr.appendChild(tdSize);
        tr.appendChild(tdSharedBy);
        tr.appendChild(tdTime);
        tr.appendChild(tdActions);

        tbody.appendChild(tr);
    });

    // Update pagination buttons state
    document.getElementById('shared-prev-btn').disabled = state.sharedFiles.page === 0;
    document.getElementById('shared-next-btn').disabled = state.sharedFiles.page >= state.sharedFiles.totalPages - 1;
    document.getElementById('shared-page-info').textContent = `Page ${state.sharedFiles.page + 1} of ${state.sharedFiles.totalPages}`;
}

async function handleFileDownload(fileId, filename) {
    showToast(`Initializing secure download: ${filename}`, 'info');
    try {
        await api.downloadFile(fileId);
    } catch (err) {
        showToast(`Download failed: ${err.message}`, 'danger');
    }
}

async function handleFileDelete(fileId, filename) {
    const confirmation = confirm(`Are you sure you want to delete ${filename}?`);
    if (!confirmation) return;

    try {
        const res = await api.deleteFile(fileId);
        if (res.success) {
            showToast(`${filename} deleted successfully.`, 'success');
            loadFilesDashboard();
        }
    } catch (err) {
        showToast(`Deletion failed: ${err.message}`, 'danger');
    }
}

/* --- Secure Upload Logic --- */

function handleFilesUpload(filesList) {
    if (!filesList || filesList.length === 0) return;

    // Process each file in array
    Array.from(filesList).forEach(file => {
        uploadFileFlow(file);
    });
}

async function uploadFileFlow(file) {
    const progressList = document.getElementById('upload-progress-list');

    // Create progress list UI element safely
    const progressItem = document.createElement('div');
    progressItem.className = 'progress-item';

    const infoDiv = document.createElement('div');
    infoDiv.className = 'progress-info';

    const nameSpan = document.createElement('span');
    nameSpan.className = 'progress-filename';
    nameSpan.textContent = file.name;

    const percentSpan = document.createElement('span');
    percentSpan.className = 'progress-percent';
    percentSpan.textContent = '0%';

    infoDiv.appendChild(nameSpan);
    infoDiv.appendChild(percentSpan);

    const barContainer = document.createElement('div');
    barContainer.className = 'bar-container progress-bar-container';

    const barFill = document.createElement('div');
    barFill.className = 'progress-bar-fill';

    barContainer.appendChild(barFill);
    progressItem.appendChild(infoDiv);
    progressItem.appendChild(barContainer);
    progressList.appendChild(progressItem);

    try {
        // Trigger multi-part file upload
        await api.uploadFile(file, ({ percent }) => {
            barFill.style.width = `${percent}%`;
            percentSpan.textContent = `${percent}%`;
        });

        // Upload Success
        barFill.classList.add('success');
        showToast(`Uploaded ${file.name} successfully.`, 'success');

        // Reload dashboard files list
        loadFilesDashboard();

        // Clear indicator card after short delay
        setTimeout(() => {
            progressItem.remove();
        }, 2000);

    } catch (err) {
        // Upload Failure (Virus detected, file size exceeded, or network limits)
        barFill.classList.add('danger');
        percentSpan.textContent = 'Failed';

        let displayError = err.message;
        if (err.status === 422) {
            displayError = `Security Alert: Malicious signature detected in ${file.name} by virus scanner. Upload blocked.`;
        }

        showToast(displayError, 'danger');
    }
}

/* --- Sharing Modals Managers --- */

function openShareModal(fileId, filename) {
    const modal = document.getElementById('share-modal');
    document.getElementById('share-modal-filename').textContent = filename;
    document.getElementById('share-internal-file-id').value = fileId;
    document.getElementById('share-public-file-id').value = fileId;

    // Reset modals forms
    document.getElementById('share-internal-form').reset();
    document.getElementById('share-public-form').reset();
    document.getElementById('public-link-result-card').classList.add('hidden');

    // Force default active tabs
    document.getElementById('tab-internal-btn').click();

    modal.classList.remove('hidden');
}

function closeShareModal() {
    document.getElementById('share-modal').classList.add('hidden');
}

async function handleInternalShareSubmit(e) {
    e.preventDefault();
    const fileId = document.getElementById('share-internal-file-id').value;
    const target = document.getElementById('share-internal-target').value;
    const permission = document.getElementById('share-internal-permission').value;

    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    try {
        const res = await api.shareInternal(fileId, target, permission);
        if (res.success) {
            showToast(`Shared file successfully with ${target}.`, 'success');
            closeShareModal();
        }
    } catch (err) {
        showToast(err.message, 'danger');
    } finally {
        btn.disabled = false;
    }
}

async function handlePublicLinkSubmit(e) {
    e.preventDefault();
    const fileId = document.getElementById('share-public-file-id').value;
    const expiry = document.getElementById('share-public-expiry').value;
    const password = document.getElementById('share-public-password').value;
    const limit = document.getElementById('share-public-limit').value;

    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    try {
        const res = await api.createPublicLink(fileId, expiry, password || null, limit || null);
        if (res.success && res.data) {
            const linkDisplay = document.getElementById('public-link-url-display');
            const resultCard = document.getElementById('public-link-result-card');

            // Formulate standard client link using location origin
            // E.g., https://localhost/#share/aB7cdX9Y
            const customShareUrl = `${window.location.origin}/#share/${res.data.shareCode}`;
            linkDisplay.value = customShareUrl;

            resultCard.classList.remove('hidden');
            showToast('Secure public link generated successfully.', 'success');
        }
    } catch (err) {
        showToast(err.message, 'danger');
    } finally {
        btn.disabled = false;
    }
}

async function copyPublicLinkToClipboard() {
    const input = document.getElementById('public-link-url-display');
    input.select();
    input.setSelectionRange(0, 99999); // Mobile compatibility

    try {
        await navigator.clipboard.writeText(input.value);
        showToast('Link copied to clipboard.', 'success');
    } catch (e) {
        showToast('Failed to copy. Please manually select the URL.', 'warning');
    }
}

/* --- Public Shared Link view Logic --- */

async function showPublicShareView(shareCode) {
    document.getElementById('app-shell').classList.add('hidden');
    document.getElementById('auth-gateway').classList.add('hidden');

    const gateway = document.getElementById('public-share-gateway');
    document.getElementById('public-file-code').textContent = `Link Code: ${shareCode}`;

    // Always show password field as optional fallback, or we can prompt if 401 is received.
    // To make it user friendly, we show it, explaining to enter it if it's secured.
    document.getElementById('public-password-section').classList.remove('hidden');
    document.getElementById('public-password').value = '';

    gateway.classList.remove('hidden');
}

async function handlePublicDownload() {
    const hash = window.location.hash;
    const shareCode = hash.split('/')[1];
    if (!shareCode) return;

    const password = document.getElementById('public-password').value;
    const btn = document.getElementById('public-download-btn');
    btn.disabled = true;

    showToast('Initializing download...', 'info');

    try {
        await api.downloadPublicLink(shareCode, password || null);
        showToast('File downloaded successfully.', 'success');
    } catch (err) {
        let msg = err.message;
        if (err.status === 401) {
            msg = 'Access Denied: Invalid or missing link passcode.';
        } else if (err.status === 403) {
            msg = 'Access Denied: Link has expired or reached download limit.';
        } else if (err.status === 404) {
            msg = 'Access Denied: Shared link does not exist.';
        }
        showToast(msg, 'danger');
    } finally {
        btn.disabled = false;
    }
}

/* --- Multi-Factor Authentication Logic --- */

async function loadMfaSettings() {
    const banner = document.getElementById('mfa-status-banner');
    const setupSection = document.getElementById('mfa-setup-section');
    const activeSection = document.getElementById('mfa-active-section');

    // Clean previous setup info
    banner.textContent = '';
    setupSection.classList.add('hidden');
    activeSection.classList.add('hidden');

    try {
        // Probe initMfa: if MFA is already enabled, the backend returns 400 Bad Request
        const res = await api.initMfa();

        // If it succeeded, MFA is not enabled yet, show setup details
        state.user.mfaRequired = false;
        banner.className = 'mfa-status-banner disabled';
        banner.textContent = '⚠️ Multi-Factor Authentication is currently Disabled. Enable it to secure your files.';
        setupSection.classList.remove('hidden');

        document.getElementById('mfa-qr-img').src = res.data.qrCodeDataUri;
        document.getElementById('mfa-secret-text').textContent = res.data.secret;
    } catch (err) {
        // If it failed because MFA is already enabled, display active state
        if (err.status === 400 || (err.message && err.message.includes('already enabled'))) {
            state.user.mfaRequired = true;
            banner.className = 'mfa-status-banner enabled';
            banner.textContent = '🔒 TOTP Multi-Factor Authentication is currently Active';
            activeSection.classList.remove('hidden');
        } else {
            showToast(`Failed to initialize MFA settings: ${err.message}`, 'danger');
        }
    }
}

async function initializeMfaSetup() {
    try {
        const res = await api.initMfa();
        if (res.success && res.data) {
            document.getElementById('mfa-qr-img').src = res.data.qrCodeDataUri;
            document.getElementById('mfa-secret-text').textContent = res.data.secret;
        }
    } catch (err) {
        showToast(`Failed to initialize MFA: ${err.message}`, 'danger');
    }
}

async function handleMfaVerifySubmit(e) {
    e.preventDefault();
    const code = document.getElementById('mfa-verification-code').value;
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    try {
        const res = await api.verifyMfa(code);
        if (res.success) {
            showToast('Multi-Factor Authentication enabled successfully!', 'success');

            // Update local user state
            state.user.mfaRequired = true;

            // Refresh MFA panel
            loadMfaSettings();
        }
    } catch (err) {
        showToast(err.message, 'danger');
    } finally {
        btn.disabled = false;
        document.getElementById('mfa-verification-code').value = '';
    }
}

/* --- Admin panel logic & step-up timer --- */

function triggerAdminStepUp() {
    document.getElementById('admin-stepup-code').value = '';
    document.getElementById('admin-stepup-modal').classList.remove('hidden');
}

function cancelAdminStepUp() {
    document.getElementById('admin-stepup-modal').classList.add('hidden');
    // Bounce user back to dashboard view
    window.location.hash = '#dashboard';
}

async function handleAdminStepUpSubmit(e) {
    e.preventDefault();
    const code = document.getElementById('admin-stepup-code').value;
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    try {
        const res = await api.stepUpMfa(code);
        if (res.success && res.data) {
            document.getElementById('admin-stepup-modal').classList.add('hidden');

            showToast('Administrative authorization verified.', 'success');

            // Configure automatic step-up expiration timer using response TTL
            const ttlSeconds = res.data.expiresInSeconds || 300;

            if (state.stepUpTimer) {
                clearTimeout(state.stepUpTimer);
            }

            state.stepUpTimer = setTimeout(() => {
                api.setStepUpToken(null);
                showToast('Administrative session expired. Step-up authorization required.', 'warning');
                if (window.location.hash === '#admin') {
                    router(); // Re-route to trigger step-up prompt
                }
            }, ttlSeconds * 1000);

            // Access granted - reload router views
            router();
        }
    } catch (err) {
        showToast(`Verification failed: ${err.message}`, 'danger');
    } finally {
        btn.disabled = false;
        document.getElementById('admin-stepup-code').value = '';
    }
}

function loadAdminPanel() {
    state.admin.users.page = 0;
    state.admin.logs.page = 0;

    loadAdminUsers();
    loadAdminLogs();
}

async function loadAdminUsers() {
    try {
        const res = await api.listUsers(state.admin.users.page, 10);
        if (res.success && res.data) {
            state.admin.users.content = res.data.content || [];
            state.admin.users.totalPages = res.data.totalPages || 1;
            renderAdminUsers();
        }
    } catch (err) {
        showToast(`Failed to load users: ${err.message}`, 'danger');
    }
}

function renderAdminUsers() {
    const tbody = document.getElementById('admin-users-tbody');
    tbody.textContent = '';

    if (state.admin.users.content.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 6;
        td.className = 'text-center text-muted';
        td.textContent = 'No registered users found.';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }

    // Render safely using DOM creator logic
    state.admin.users.content.forEach(user => {
        const tr = document.createElement('tr');

        const tdId = document.createElement('td');
        tdId.textContent = user.id;

        const tdUser = document.createElement('td');
        tdUser.textContent = user.username;

        const tdEmail = document.createElement('td');
        tdEmail.textContent = user.email;

        const tdRoles = document.createElement('td');
        tdRoles.textContent = user.roles ? user.roles.join(', ') : 'ROLE_USER';

        const tdMfa = document.createElement('td');
        tdMfa.textContent = user.mfaEnabled ? '🛡️ Enabled' : '❌ Disabled';
        if (user.mfaEnabled) tdMfa.className = 'text-success';

        const tdJoined = document.createElement('td');
        tdJoined.textContent = new Date(user.createdAt).toLocaleDateString();

        tr.appendChild(tdId);
        tr.appendChild(tdUser);
        tr.appendChild(tdEmail);
        tr.appendChild(tdRoles);
        tr.appendChild(tdMfa);
        tr.appendChild(tdJoined);
        tbody.appendChild(tr);
    });

    // Pagination updates
    document.getElementById('admin-users-prev-btn').disabled = state.admin.users.page === 0;
    document.getElementById('admin-users-next-btn').disabled = state.admin.users.page >= state.admin.users.totalPages - 1;
    document.getElementById('admin-users-page-info').textContent = `Page ${state.admin.users.page + 1} of ${state.admin.users.totalPages}`;
}

async function loadAdminLogs() {
    try {
        const filterUser = state.admin.logs.userIdFilter || null;
        const filterAction = state.admin.logs.actionFilter || null;

        const res = await api.getAuditLogs(state.admin.logs.page, 15, filterUser, filterAction);
        if (res.success && res.data) {
            state.admin.logs.content = res.data.content || [];
            state.admin.logs.totalPages = res.data.totalPages || 1;
            renderAdminLogs();
        }
    } catch (err) {
        showToast(`Failed to load audit logs: ${err.message}`, 'danger');
    }
}

function renderAdminLogs() {
    const tbody = document.getElementById('admin-logs-tbody');
    tbody.textContent = '';

    if (state.admin.logs.content.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 5;
        td.className = 'text-center text-muted';
        td.textContent = 'No matching audit logs found.';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }

    state.admin.logs.content.forEach(log => {
        const tr = document.createElement('tr');

        const tdTime = document.createElement('td');
        tdTime.textContent = new Date(log.createdAt).toLocaleString();

        const tdUser = document.createElement('td');
        tdUser.textContent = log.userId ? log.userId : 'SYSTEM / ANONYMOUS';

        const tdAction = document.createElement('td');
        const badge = document.createElement('span');
        badge.className = `badge-action ${getActionBadgeClass(log.action)}`;
        badge.textContent = log.action;
        tdAction.appendChild(badge);

        const tdIp = document.createElement('td');
        tdIp.textContent = log.ipAddress || '---';

        const tdDetails = document.createElement('td');
        tdDetails.textContent = log.details || '';

        tr.appendChild(tdTime);
        tr.appendChild(tdUser);
        tr.appendChild(tdAction);
        tr.appendChild(tdIp);
        tr.appendChild(tdDetails);
        tbody.appendChild(tr);
    });

    // Pagination updates
    document.getElementById('admin-logs-prev-btn').disabled = state.admin.logs.page === 0;
    document.getElementById('admin-logs-next-btn').disabled = state.admin.logs.page >= state.admin.logs.totalPages - 1;
    document.getElementById('admin-logs-page-info').textContent = `Page ${state.admin.logs.page + 1} of ${state.admin.logs.totalPages}`;
}

/* --- UI Helper Utilities --- */

function showToast(message, type = 'info') {
    const container = document.getElementById('notification-container');

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const textSpan = document.createElement('span');
    textSpan.textContent = message;

    const closeBtn = document.createElement('button');
    closeBtn.className = 'toast-close';
    closeBtn.textContent = '×';
    closeBtn.addEventListener('click', () => {
        toast.remove();
    });

    toast.appendChild(textSpan);
    toast.appendChild(closeBtn);
    container.appendChild(toast);

    // Auto cleanup after 5 seconds
    setTimeout(() => {
        if (toast.parentNode) {
            toast.remove();
        }
    }, 5000);
}

function getFileEmoji(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    switch (ext) {
        case 'pdf': return '📕';
        case 'doc':
        case 'docx': return '📘';
        case 'xls':
        case 'xlsx': return '📗';
        case 'png':
        case 'jpg':
        case 'jpeg':
        case 'gif': return '🖼️';
        case 'zip':
        case 'rar':
        case 'tar':
        case 'gz': return '📦';
        case 'txt': return '📄';
        default: return '📄';
    }
}

function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
}

function getActionBadgeClass(action) {
    if (action.includes('SUCCESS') || action.includes('ENABLED')) {
        return 'badge-success';
    }
    if (action.includes('FAILED') || action.includes('VIRUS')) {
        return 'badge-failed';
    }
    if (action.includes('REKEY')) {
        return 'badge-warning';
    }
    return 'badge-info';
}

function debounce(func, delay) {
    let timeout;
    return function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), delay);
    };
}
