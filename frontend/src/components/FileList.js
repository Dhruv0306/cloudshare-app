import React, { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import ShareFileModal from './ShareFileModal';
import { useNotification } from './NotificationSystem';
import { useSharingErrorHandler } from './SharingErrorBoundary';
import LoadingSpinner, { SkeletonLoader } from './LoadingSpinner';
import './FileList.css';

/**
 * Enhanced file list component with sharing features
 * Displays files with share status indicators, quick actions, and bulk operations
 */
const FileList = ({
  files,
  onFileUpdate,
  onDownload,
  onDelete,
  loading = false
}) => {
  // Hooks for notifications and error handling
  const { showSuccess: showSuccessNotification, showError: showErrorNotification } = useNotification();
  const { handleSharingError } = useSharingErrorHandler();

  // State for sharing functionality
  const [selectedFiles, setSelectedFiles] = useState(new Set());
  const [shareModalOpen, setShareModalOpen] = useState(false);
  const [fileToShare, setFileToShare] = useState(null);
  const [contextMenu, setContextMenu] = useState({ visible: false, x: 0, y: 0, file: null });
  const [bulkActionsVisible, setBulkActionsVisible] = useState(false);
  const [fileShares, setFileShares] = useState({});
  const [loadingShares, setLoadingShares] = useState(false);
  const [sharingInProgress, setSharingInProgress] = useState(false);

  // Load sharing information for all files
  const loadFileShares = useCallback(async () => {
    if (!files || files.length === 0) return;

    setLoadingShares(true);
    try {
      // Fetch sharing information for all files
      const sharePromises = files.map(async (file) => {
        try {
          const response = await axios.get(`/api/files/${file.id}/shares`);
          return { fileId: file.id, shares: response.data };
        } catch (error) {
          // If file has no shares or error, return empty array
          return { fileId: file.id, shares: [] };
        }
      });

      const shareResults = await Promise.all(sharePromises);
      const shareMap = {};

      shareResults.forEach(({ fileId, shares }) => {
        shareMap[fileId] = {
          shares: shares || [],
          shareCount: shares ? shares.length : 0,
          hasActiveShares: shares ? shares.some(share => share.active) : false,
          lastSharedAt: shares && shares.length > 0
            ? Math.max(...shares.map(share => new Date(share.createdAt).getTime()))
            : null
        };
      });

      setFileShares(shareMap);
    } catch (error) {
      console.error('Error loading file shares:', error);
    } finally {
      setLoadingShares(false);
    }
  }, [files]);

  // Load shares when files change
  useEffect(() => {
    loadFileShares();
  }, [loadFileShares]);

  // Close context menu when clicking outside
  useEffect(() => {
    const handleClickOutside = () => {
      setContextMenu({ visible: false, x: 0, y: 0, file: null });
    };

    if (contextMenu.visible) {
      document.addEventListener('click', handleClickOutside);
      return () => document.removeEventListener('click', handleClickOutside);
    }
  }, [contextMenu.visible]);

  /**
   * Handle file selection for bulk operations
   */
  const handleFileSelect = (fileId, isSelected) => {
    const newSelection = new Set(selectedFiles);
    if (isSelected) {
      newSelection.add(fileId);
    } else {
      newSelection.delete(fileId);
    }
    setSelectedFiles(newSelection);
    setBulkActionsVisible(newSelection.size > 0);
  };

  /**
   * Select all files
   */
  const handleSelectAll = () => {
    if (selectedFiles.size === files.length) {
      setSelectedFiles(new Set());
      setBulkActionsVisible(false);
    } else {
      setSelectedFiles(new Set(files.map(file => file.id)));
      setBulkActionsVisible(true);
    }
  };

  /**
   * Clear all selections
   */
  const handleClearSelection = () => {
    setSelectedFiles(new Set());
    setBulkActionsVisible(false);
  };

  /**
   * Handle right-click context menu
   */
  const handleContextMenu = (e, file) => {
    e.preventDefault();
    setContextMenu({
      visible: true,
      x: e.clientX,
      y: e.clientY,
      file
    });
  };

  /**
   * Handle quick share action
   */
  const handleQuickShare = (file) => {
    setFileToShare(file);
    setShareModalOpen(true);
    setContextMenu({ visible: false, x: 0, y: 0, file: null });
  };

  /**
   * Handle share creation with enhanced feedback
   */
  const handleShare = async (fileId, shareData) => {
    const file = files.find(f => f.id === fileId);
    const fileName = file?.originalFileName || 'Unknown file';

    try {
      setSharingInProgress(true);

      const response = await axios.post(`/api/files/${fileId}/share`, shareData);

      // Reload shares to update indicators
      await loadFileShares();

      // Notify parent component of file update
      if (onFileUpdate) {
        onFileUpdate();
      }

      // Show success notification
      showSuccessNotification(`Successfully shared "${fileName}"`);

      return {
        shareUrl: `${window.location.origin}/shared/${response.data.shareToken}`,
        ...response.data
      };
    } catch (error) {
      console.error('Error creating share:', error);
      handleSharingError(error, `sharing "${fileName}"`);
      showErrorNotification(`Failed to share "${fileName}"`);
      throw error;
    } finally {
      setSharingInProgress(false);
    }
  };

  /**
   * Handle bulk share operation
   */
  const handleBulkShare = () => {
    if (selectedFiles.size === 1) {
      const fileId = Array.from(selectedFiles)[0];
      const file = files.find(f => f.id === fileId);
      handleQuickShare(file);
    } else {
      // For multiple files, we could implement a bulk share modal
      // For now, we'll share the first selected file as an example
      const firstFileId = Array.from(selectedFiles)[0];
      const file = files.find(f => f.id === firstFileId);
      handleQuickShare(file);
    }
  };

  /**
   * Format file size
   */
  const formatFileSize = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  /**
   * Format date
   */
  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString();
  };

  /**
   * Get share status for a file
   */
  const getShareStatus = (fileId) => {
    const shareInfo = fileShares[fileId];
    if (!shareInfo) return { hasShares: false, shareCount: 0, isActive: false };

    return {
      hasShares: shareInfo.shareCount > 0,
      shareCount: shareInfo.shareCount,
      isActive: shareInfo.hasActiveShares,
      lastSharedAt: shareInfo.lastSharedAt
    };
  };

  /**
   * Render share status indicator
   */
  const renderShareIndicator = (file) => {
    const status = getShareStatus(file.id);

    if (!status.hasShares) return null;

    return (
      <div className={`share-indicator ${status.isActive ? 'active' : 'inactive'}`}>
        <span className="share-icon">🔗</span>
        <span className="share-count">{status.shareCount}</span>
        {status.isActive && <span className="active-dot"></span>}
      </div>
    );
  };

  /**
   * Render context menu
   */
  const renderContextMenu = () => {
    if (!contextMenu.visible || !contextMenu.file) return null;

    return (
      <div
        className="context-menu"
        style={{
          position: 'fixed',
          top: contextMenu.y,
          left: contextMenu.x,
          zIndex: 1000
        }}
      >
        <button
          className="context-menu-item"
          onClick={() => handleQuickShare(contextMenu.file)}
        >
          <span className="context-menu-icon">🔗</span>
          Share File
        </button>
        <button
          className="context-menu-item"
          onClick={() => {
            onDownload(contextMenu.file.fileName, contextMenu.file.originalFileName);
            setContextMenu({ visible: false, x: 0, y: 0, file: null });
          }}
        >
          <span className="context-menu-icon">⬇️</span>
          Download
        </button>
        <button
          className="context-menu-item danger"
          onClick={() => {
            onDelete(contextMenu.file.id);
            setContextMenu({ visible: false, x: 0, y: 0, file: null });
          }}
        >
          <span className="context-menu-icon">🗑️</span>
          Delete
        </button>
      </div>
    );
  };

  if (loading) {
    return (
      <div className="file-list-loading">
        <LoadingSpinner message="Loading files..." />
        <div className="file-list-skeleton">
          {Array.from({ length: 3 }, (_, index) => (
            <div key={index} className="file-item-skeleton">
              <SkeletonLoader lines={2} height="1.2rem" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (!files || files.length === 0) {
    return (
      <div className="no-files">
        <div className="no-files-icon">📁</div>
        <h3>No files uploaded yet</h3>
        <p>Upload your first file to get started with sharing!</p>
      </div>
    );
  }

  return (
    <div className="file-list-container">
      {/* Bulk Actions Bar */}
      {bulkActionsVisible && (
        <div className="bulk-actions-bar">
          <div className="bulk-actions-info">
            <span>{selectedFiles.size} file{selectedFiles.size !== 1 ? 's' : ''} selected</span>
          </div>
          <div className="bulk-actions-buttons">
            <button
              className="bulk-action-btn share-btn"
              onClick={handleBulkShare}
              title="Share selected files"
            >
              🔗 Share
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

      {/* File List Header */}
      <div className="file-list-header">
        <div className="header-left">
          <label className="select-all-checkbox">
            <input
              type="checkbox"
              checked={selectedFiles.size === files.length && files.length > 0}
              onChange={handleSelectAll}
            />
            <span>Select All</span>
          </label>
        </div>
        <div className="header-right">
          {loadingShares && (
            <div className="loading-shares">
              <LoadingSpinner size="small" showMessage={false} />
              <span>Loading shares...</span>
            </div>
          )}
        </div>
      </div>

      {/* File Items */}
      <div className="file-list">
        {files.map((file) => {
          const isSelected = selectedFiles.has(file.id);
          const shareStatus = getShareStatus(file.id);

          return (
            <div
              key={file.id}
              className={`file-item ${isSelected ? 'selected' : ''} ${shareStatus.isActive ? 'shared' : ''}`}
              onContextMenu={(e) => handleContextMenu(e, file)}
            >
              <div className="file-item-left">
                <label className="file-checkbox">
                  <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={(e) => handleFileSelect(file.id, e.target.checked)}
                  />
                </label>
                <div className="file-info">
                  <div className="file-name-row">
                    <span className="file-name">{file.originalFileName}</span>
                    {renderShareIndicator(file)}
                  </div>
                  <div className="file-details">
                    Size: {formatFileSize(file.fileSize)} |
                    Uploaded: {formatDate(file.uploadTime)}
                    {shareStatus.hasShares && shareStatus.lastSharedAt && (
                      <span className="share-activity">
                        | Last shared: {formatDate(new Date(shareStatus.lastSharedAt))}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <div className="file-actions">
                <button
                  className="action-btn share-btn"
                  onClick={() => handleQuickShare(file)}
                  title="Share file"
                >
                  🔗
                </button>
                <button
                  className="action-btn download-btn"
                  onClick={() => onDownload(file.fileName, file.originalFileName)}
                  title="Download file"
                >
                  ⬇️
                </button>
                <button
                  className="action-btn delete-btn"
                  onClick={() => onDelete(file.id)}
                  title="Delete file"
                >
                  🗑️
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {/* Context Menu */}
      {renderContextMenu()}

      {/* Share Modal */}
      <ShareFileModal
        isOpen={shareModalOpen}
        onClose={() => {
          setShareModalOpen(false);
          setFileToShare(null);
        }}
        file={fileToShare}
        onShare={handleShare}
        loading={sharingInProgress}
      />
    </div>
  );
};

export default FileList;