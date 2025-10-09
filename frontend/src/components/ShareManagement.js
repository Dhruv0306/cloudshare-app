import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import './ShareManagement.css';

/**
 * Comprehensive share management dashboard component
 * Displays user's shared files with statistics, access logs, and management controls
 * Supports filtering, sorting, bulk operations, and permission updates
 */
const ShareManagement = () => {
  // State management
  const [sharedFiles, setSharedFiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedShares, setSelectedShares] = useState(new Set());
  const [bulkActionsVisible, setBulkActionsVisible] = useState(false);
  
  // Filter and sort state
  const [filters, setFilters] = useState({
    status: 'all', // all, active, expired, revoked
    permission: 'all', // all, VIEW_ONLY, DOWNLOAD
    dateRange: 'all', // all, today, week, month
    searchTerm: ''
  });
  const [sortBy, setSortBy] = useState('createdAt');
  const [sortOrder, setSortOrder] = useState('desc');
  
  // UI state
  const [expandedShare, setExpandedShare] = useState(null);
  const [showStatistics, setShowStatistics] = useState(true);
  const [statistics, setStatistics] = useState({
    totalShares: 0,
    activeShares: 0,
    totalAccess: 0,
    recentActivity: []
  });

  /**
   * Load user's shared files from the API
   */
  const loadSharedFiles = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await axios.get('/api/files/my-shares');
      setSharedFiles(response.data || []);
      
      // Calculate statistics
      calculateStatistics(response.data || []);
    } catch (err) {
      console.error('Error loading shared files:', err);
      setError('Failed to load shared files. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Calculate sharing statistics from the data
   */
  const calculateStatistics = (shares) => {
    const now = new Date();
    const activeShares = shares.filter(share => 
      share.active && (!share.expiresAt || new Date(share.expiresAt) > now)
    );
    
    const totalAccess = shares.reduce((sum, share) => sum + (share.accessCount || 0), 0);
    
    // Get recent activity (last 7 days)
    const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    const recentActivity = shares
      .filter(share => new Date(share.createdAt) > weekAgo)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      .slice(0, 5);

    setStatistics({
      totalShares: shares.length,
      activeShares: activeShares.length,
      totalAccess,
      recentActivity
    });
  };

  /**
   * Load share access history for a specific share
   */
  const loadShareAccess = async (shareId) => {
    try {
      const response = await axios.get(`/api/files/shares/${shareId}/access`);
      return response.data || [];
    } catch (error) {
      console.error('Error loading share access:', error);
      return [];
    }
  };

  /**
   * Revoke a file share
   */
  const revokeShare = async (shareId) => {
    try {
      await axios.delete(`/api/files/shares/${shareId}`);
      await loadSharedFiles(); // Reload data
      
      // Remove from selection if it was selected
      const newSelection = new Set(selectedShares);
      newSelection.delete(shareId);
      setSelectedShares(newSelection);
      setBulkActionsVisible(newSelection.size > 0);
      
      return true;
    } catch (error) {
      console.error('Error revoking share:', error);
      throw error;
    }
  };

  /**
   * Update share permissions
   */
  const updateSharePermissions = async (shareId, newPermission) => {
    try {
      await axios.put(`/api/files/shares/${shareId}`, {
        permission: newPermission
      });
      await loadSharedFiles(); // Reload data
      return true;
    } catch (error) {
      console.error('Error updating share permissions:', error);
      throw error;
    }
  };

  /**
   * Handle bulk revoke operation
   */
  const handleBulkRevoke = async () => {
    if (selectedShares.size === 0) return;
    
    const confirmMessage = `Are you sure you want to revoke ${selectedShares.size} share${selectedShares.size !== 1 ? 's' : ''}?`;
    if (!window.confirm(confirmMessage)) return;

    try {
      const revokePromises = Array.from(selectedShares).map(shareId => 
        axios.delete(`/api/files/shares/${shareId}`)
      );
      
      await Promise.all(revokePromises);
      await loadSharedFiles();
      
      setSelectedShares(new Set());
      setBulkActionsVisible(false);
    } catch (error) {
      console.error('Error in bulk revoke:', error);
      alert('Some shares could not be revoked. Please try again.');
    }
  };

  /**
   * Handle share selection for bulk operations
   */
  const handleShareSelect = (shareId, isSelected) => {
    const newSelection = new Set(selectedShares);
    if (isSelected) {
      newSelection.add(shareId);
    } else {
      newSelection.delete(shareId);
    }
    setSelectedShares(newSelection);
    setBulkActionsVisible(newSelection.size > 0);
  };

  /**
   * Select all visible shares
   */
  const handleSelectAll = () => {
    const filteredShares = getFilteredAndSortedShares();
    if (selectedShares.size === filteredShares.length) {
      setSelectedShares(new Set());
      setBulkActionsVisible(false);
    } else {
      setSelectedShares(new Set(filteredShares.map(share => share.id)));
      setBulkActionsVisible(true);
    }
  };

  /**
   * Clear all selections
   */
  const handleClearSelection = () => {
    setSelectedShares(new Set());
    setBulkActionsVisible(false);
  };

  /**
   * Apply filters and sorting to shares
   */
  const getFilteredAndSortedShares = () => {
    let filtered = [...sharedFiles];
    const now = new Date();

    // Apply status filter
    if (filters.status !== 'all') {
      filtered = filtered.filter(share => {
        switch (filters.status) {
          case 'active':
            return share.active && (!share.expiresAt || new Date(share.expiresAt) > now);
          case 'expired':
            return share.expiresAt && new Date(share.expiresAt) <= now;
          case 'revoked':
            return !share.active;
          default:
            return true;
        }
      });
    }

    // Apply permission filter
    if (filters.permission !== 'all') {
      filtered = filtered.filter(share => share.permission === filters.permission);
    }

    // Apply date range filter
    if (filters.dateRange !== 'all') {
      const filterDate = new Date();
      switch (filters.dateRange) {
        case 'today':
          filterDate.setHours(0, 0, 0, 0);
          break;
        case 'week':
          filterDate.setDate(filterDate.getDate() - 7);
          break;
        case 'month':
          filterDate.setMonth(filterDate.getMonth() - 1);
          break;
        default:
          break;
      }
      
      if (filters.dateRange !== 'all') {
        filtered = filtered.filter(share => new Date(share.createdAt) >= filterDate);
      }
    }

    // Apply search filter
    if (filters.searchTerm) {
      const searchLower = filters.searchTerm.toLowerCase();
      filtered = filtered.filter(share =>
        (share.file?.originalFileName?.toLowerCase().includes(searchLower)) ||
        (share.shareToken?.toLowerCase().includes(searchLower))
      );
    }

    // Apply sorting
    filtered.sort((a, b) => {
      let aValue, bValue;
      
      switch (sortBy) {
        case 'fileName':
          aValue = (a.file?.originalFileName || '').toLowerCase();
          bValue = (b.file?.originalFileName || '').toLowerCase();
          break;
        case 'accessCount':
          aValue = a.accessCount || 0;
          bValue = b.accessCount || 0;
          break;
        case 'expiresAt':
          aValue = a.expiresAt ? new Date(a.expiresAt) : new Date('2099-12-31');
          bValue = b.expiresAt ? new Date(b.expiresAt) : new Date('2099-12-31');
          break;
        case 'createdAt':
        default:
          aValue = new Date(a.createdAt);
          bValue = new Date(b.createdAt);
          break;
      }

      if (aValue < bValue) return sortOrder === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortOrder === 'asc' ? 1 : -1;
      return 0;
    });

    return filtered;
  };

  /**
   * Toggle share details expansion
   */
  const toggleShareDetails = async (shareId) => {
    if (expandedShare === shareId) {
      setExpandedShare(null);
    } else {
      setExpandedShare(shareId);
      // Load access history when expanding
      const share = sharedFiles.find(s => s.id === shareId);
      if (share && !share.accessHistory) {
        const accessHistory = await loadShareAccess(shareId);
        // Update the share with access history
        setSharedFiles(prev => prev.map(s => 
          s.id === shareId ? { ...s, accessHistory } : s
        ));
      }
    }
  };

  // Load data on component mount
  useEffect(() => {
    loadSharedFiles();
  }, [loadSharedFiles]);

  /**
   * Format file size for display
   */
  const formatFileSize = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  /**
   * Format date for display
   */
  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  /**
   * Get share status information
   */
  const getShareStatus = (share) => {
    const now = new Date();
    
    if (!share.active) {
      return { status: 'revoked', label: 'Revoked', className: 'revoked' };
    }
    
    if (share.expiresAt && new Date(share.expiresAt) <= now) {
      return { status: 'expired', label: 'Expired', className: 'expired' };
    }
    
    return { status: 'active', label: 'Active', className: 'active' };
  };

  /**
   * Copy share URL to clipboard
   */
  const copyShareUrl = async (shareToken) => {
    const shareUrl = `${window.location.origin}/shared/${shareToken}`;
    try {
      await navigator.clipboard.writeText(shareUrl);
      // Could add a toast notification here
    } catch (error) {
      console.error('Failed to copy URL:', error);
    }
  };

  // Get filtered and sorted shares for display
  const displayShares = getFilteredAndSortedShares();

  if (loading) {
    return (
      <div className="share-management-container">
        <div className="loading-state">
          <div className="loading-spinner"></div>
          <h2>Loading shared files...</h2>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="share-management-container">
        <div className="error-state">
          <div className="error-icon">❌</div>
          <h2>Error Loading Shares</h2>
          <p>{error}</p>
          <button className="retry-btn" onClick={loadSharedFiles}>
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="share-management-container">
      {/* Header */}
      <div className="share-management-header">
        <h1>Share Management</h1>
        <p>Manage your shared files, view access statistics, and control permissions</p>
      </div>

      {/* Statistics Section */}
      {showStatistics && (
        <div className="statistics-section">
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-value">{statistics.totalShares}</div>
              <div className="stat-label">Total Shares</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{statistics.activeShares}</div>
              <div className="stat-label">Active Shares</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{statistics.totalAccess}</div>
              <div className="stat-label">Total Access</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{statistics.recentActivity.length}</div>
              <div className="stat-label">Recent Activity</div>
            </div>
          </div>
          
          <button 
            className="toggle-stats-btn"
            onClick={() => setShowStatistics(false)}
            title="Hide statistics"
          >
            ▲ Hide Stats
          </button>
        </div>
      )}

      {!showStatistics && (
        <button 
          className="show-stats-btn"
          onClick={() => setShowStatistics(true)}
        >
          ▼ Show Statistics
        </button>
      )}

      {/* Filters and Controls */}
      <div className="controls-section">
        <div className="filters-row">
          <div className="filter-group">
            <label htmlFor="status-filter">Status:</label>
            <select
              id="status-filter"
              value={filters.status}
              onChange={(e) => setFilters(prev => ({ ...prev, status: e.target.value }))}
            >
              <option value="all">All</option>
              <option value="active">Active</option>
              <option value="expired">Expired</option>
              <option value="revoked">Revoked</option>
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="permission-filter">Permission:</label>
            <select
              id="permission-filter"
              value={filters.permission}
              onChange={(e) => setFilters(prev => ({ ...prev, permission: e.target.value }))}
            >
              <option value="all">All</option>
              <option value="VIEW_ONLY">View Only</option>
              <option value="DOWNLOAD">Download</option>
            </select>
          </div>

          <div className="filter-group">
            <label htmlFor="date-filter">Date Range:</label>
            <select
              id="date-filter"
              value={filters.dateRange}
              onChange={(e) => setFilters(prev => ({ ...prev, dateRange: e.target.value }))}
            >
              <option value="all">All Time</option>
              <option value="today">Today</option>
              <option value="week">Last Week</option>
              <option value="month">Last Month</option>
            </select>
          </div>

          <div className="search-group">
            <input
              type="text"
              placeholder="Search files..."
              value={filters.searchTerm}
              onChange={(e) => setFilters(prev => ({ ...prev, searchTerm: e.target.value }))}
              className="search-input"
            />
          </div>
        </div>

        <div className="sort-row">
          <div className="sort-group">
            <label htmlFor="sort-by">Sort by:</label>
            <select
              id="sort-by"
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
            >
              <option value="createdAt">Date Created</option>
              <option value="fileName">File Name</option>
              <option value="accessCount">Access Count</option>
              <option value="expiresAt">Expiration</option>
            </select>
          </div>

          <button
            className="sort-order-btn"
            onClick={() => setSortOrder(prev => prev === 'asc' ? 'desc' : 'asc')}
            title={`Sort ${sortOrder === 'asc' ? 'descending' : 'ascending'}`}
          >
            {sortOrder === 'asc' ? '↑' : '↓'}
          </button>
        </div>
      </div>

      {/* Bulk Actions Bar */}
      {bulkActionsVisible && (
        <div className="bulk-actions-bar">
          <div className="bulk-actions-info">
            <span>{selectedShares.size} share{selectedShares.size !== 1 ? 's' : ''} selected</span>
          </div>
          <div className="bulk-actions-buttons">
            <button 
              className="bulk-action-btn revoke-btn"
              onClick={handleBulkRevoke}
              title="Revoke selected shares"
            >
              🚫 Revoke Selected
            </button>
            <button 
              className="bulk-action-btn clear-btn"
              onClick={handleClearSelection}
              title="Clear selection"
            >
              ✕ Clear
            </button>
          </div>
        </div>
      )}

      {/* Shares List */}
      <div className="shares-section">
        {displayShares.length === 0 ? (
          <div className="no-shares">
            <div className="no-shares-icon">📤</div>
            <h3>No shared files found</h3>
            <p>
              {sharedFiles.length === 0 
                ? "You haven't shared any files yet."
                : "No files match your current filters."
              }
            </p>
          </div>
        ) : (
          <>
            {/* List Header */}
            <div className="shares-list-header">
              <div className="header-left">
                <label className="select-all-checkbox">
                  <input
                    type="checkbox"
                    checked={selectedShares.size === displayShares.length && displayShares.length > 0}
                    onChange={handleSelectAll}
                  />
                  <span>Select All ({displayShares.length})</span>
                </label>
              </div>
            </div>

            {/* Shares List */}
            <div className="shares-list">
              {displayShares.map((share) => {
                const isSelected = selectedShares.has(share.id);
                const isExpanded = expandedShare === share.id;
                const status = getShareStatus(share);
                
                return (
                  <div key={share.id} className={`share-item ${isSelected ? 'selected' : ''} ${status.className}`}>
                    <div className="share-item-main">
                      <div className="share-item-left">
                        <label className="share-checkbox">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={(e) => handleShareSelect(share.id, e.target.checked)}
                          />
                        </label>
                        
                        <div className="share-file-info">
                          <div className="file-name-row">
                            <span className="file-name">{share.file?.originalFileName || 'Unknown File'}</span>
                            <div className={`status-badge ${status.className}`}>
                              {status.label}
                            </div>
                          </div>
                          <div className="share-details">
                            <span className="file-size">{formatFileSize(share.file?.fileSize || 0)}</span>
                            <span className="separator">•</span>
                            <span className="permission-info">
                              {share.permission === 'VIEW_ONLY' ? '👁️ View Only' : '⬇️ Download'}
                            </span>
                            <span className="separator">•</span>
                            <span className="access-count">
                              {share.accessCount || 0} access{(share.accessCount || 0) !== 1 ? 'es' : ''}
                            </span>
                            <span className="separator">•</span>
                            <span className="created-date">
                              Created {formatDate(share.createdAt)}
                            </span>
                            {share.expiresAt && (
                              <>
                                <span className="separator">•</span>
                                <span className="expiry-date">
                                  Expires {formatDate(share.expiresAt)}
                                </span>
                              </>
                            )}
                          </div>
                        </div>
                      </div>
                      
                      <div className="share-item-actions">
                        <button
                          className="action-btn copy-btn"
                          onClick={() => copyShareUrl(share.shareToken)}
                          title="Copy share link"
                          disabled={!share.active}
                        >
                          📋
                        </button>
                        
                        {share.active && (
                          <select
                            className="permission-select"
                            value={share.permission}
                            onChange={(e) => updateSharePermissions(share.id, e.target.value)}
                            title="Change permissions"
                          >
                            <option value="VIEW_ONLY">View Only</option>
                            <option value="DOWNLOAD">Download</option>
                          </select>
                        )}
                        
                        <button
                          className="action-btn details-btn"
                          onClick={() => toggleShareDetails(share.id)}
                          title={isExpanded ? "Hide details" : "Show details"}
                        >
                          {isExpanded ? '▲' : '▼'}
                        </button>
                        
                        {share.active && (
                          <button
                            className="action-btn revoke-btn"
                            onClick={() => revokeShare(share.id)}
                            title="Revoke share"
                          >
                            🚫
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Expanded Details */}
                    {isExpanded && (
                      <div className="share-details-expanded">
                        <div className="details-grid">
                          <div className="detail-section">
                            <h4>Share Information</h4>
                            <div className="detail-item">
                              <span className="detail-label">Share Token:</span>
                              <span className="detail-value monospace">{share.shareToken}</span>
                            </div>
                            <div className="detail-item">
                              <span className="detail-label">Share URL:</span>
                              <span className="detail-value">
                                <code>{window.location.origin}/shared/{share.shareToken}</code>
                                <button 
                                  className="copy-url-btn"
                                  onClick={() => copyShareUrl(share.shareToken)}
                                >
                                  Copy
                                </button>
                              </span>
                            </div>
                            <div className="detail-item">
                              <span className="detail-label">Max Access:</span>
                              <span className="detail-value">
                                {share.maxAccess ? share.maxAccess : 'Unlimited'}
                              </span>
                            </div>
                          </div>

                          {share.accessHistory && share.accessHistory.length > 0 && (
                            <div className="detail-section">
                              <h4>Recent Access History</h4>
                              <div className="access-history">
                                {share.accessHistory.slice(0, 5).map((access, index) => (
                                  <div key={index} className="access-item">
                                    <span className="access-type">
                                      {access.accessType === 'DOWNLOAD' ? '⬇️' : '👁️'}
                                    </span>
                                    <span className="access-ip">{access.accessorIp}</span>
                                    <span className="access-time">{formatDate(access.accessedAt)}</span>
                                  </div>
                                ))}
                                {share.accessHistory.length > 5 && (
                                  <div className="access-more">
                                    +{share.accessHistory.length - 5} more access{share.accessHistory.length - 5 !== 1 ? 'es' : ''}
                                  </div>
                                )}
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default ShareManagement;