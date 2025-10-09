import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { useNotification } from './NotificationSystem';
import { useSharingErrorHandler } from './SharingErrorBoundary';
import './SharedFileAccess.css';

/**
 * Public component for accessing shared files via share tokens
 * Handles file preview, download, and various error states
 */
const SharedFileAccess = ({ shareToken }) => {
  // Hooks for notifications and error handling
  const { showSuccess, showError, showInfo } = useNotification();
  const { handleSharingError } = useSharingErrorHandler();

  // State management
  const [shareData, setShareData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [downloading, setDownloading] = useState(false);
  const [accessLogged, setAccessLogged] = useState(false);
  const [retryCount, setRetryCount] = useState(0);

  /**
   * Handle API errors and set appropriate error states
   */
  const handleApiError = useCallback((err) => {
    if (err.response) {
      switch (err.response.status) {
        case 404:
          setError({
            type: 'NOT_FOUND',
            message: 'This file share could not be found. It may have been removed or the link is incorrect.'
          });
          break;
        case 410:
          setError({
            type: 'EXPIRED',
            message: 'This share link has expired and is no longer available.'
          });
          break;
        case 403:
          setError({
            type: 'REVOKED',
            message: 'Access to this file has been revoked by the owner.'
          });
          break;
        case 429:
          setError({
            type: 'RATE_LIMITED',
            message: 'Too many access attempts. Please try again later.'
          });
          break;
        default:
          setError({
            type: 'SERVER_ERROR',
            message: 'Unable to load the shared file. Please try again later.'
          });
      }
    } else {
      setError({
        type: 'NETWORK_ERROR',
        message: 'Network error. Please check your connection and try again.'
      });
    }
  }, []);

  /**
   * Log file access for analytics and security
   */
  const logFileAccess = useCallback(async (accessType) => {
    try {
      await axios.post(`/api/files/shared/${shareToken}/access`, {
        accessType
      });
    } catch (err) {
      // Access logging failure shouldn't prevent file access
      console.warn('Failed to log file access:', err);
    }
  }, [shareToken]);

  /**
   * Load shared file information from the API
   */
  const loadSharedFile = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await axios.get(`/api/files/shared/${shareToken}`);
      setShareData(response.data);
      setRetryCount(0);

      // Log the access if not already logged
      if (!accessLogged) {
        try {
          await axios.post(`/api/files/shared/${shareToken}/access`, {
            accessType: 'VIEW'
          });
        } catch (err) {
          // Access logging failure shouldn't prevent file access
          console.warn('Failed to log file access:', err);
        }
        setAccessLogged(true);
      }

      // Show info about file access
      if (response.data.permission === 'VIEW_ONLY') {
        try {
          showInfo('This file is view-only. Download is not permitted.', {
            duration: 4000
          });
        } catch (infoErr) {
          // Ignore notification errors
          console.warn('Failed to show info notification:', infoErr);
        }
      }
    } catch (err) {
      console.error('Error loading shared file:', err);
      setRetryCount(prev => prev + 1);
      
      // Handle API errors inline to avoid dependency issues
      if (err.response) {
        switch (err.response.status) {
          case 404:
            setError({
              type: 'NOT_FOUND',
              message: 'This file share could not be found. It may have been removed or the link is incorrect.'
            });
            break;
          case 410:
            setError({
              type: 'EXPIRED',
              message: 'This share link has expired and is no longer available.'
            });
            break;
          case 403:
            setError({
              type: 'REVOKED',
              message: 'Access to this file has been revoked by the owner.'
            });
            break;
          case 429:
            setError({
              type: 'RATE_LIMITED',
              message: 'Too many access attempts. Please try again later.'
            });
            break;
          default:
            setError({
              type: 'SERVER_ERROR',
              message: 'Unable to load the shared file. Please try again later.'
            });
        }
      } else {
        setError({
          type: 'NETWORK_ERROR',
          message: 'Network error. Please check your connection and try again.'
        });
      }
      
      // Use specialized error handler for better user experience
      try {
        handleSharingError(err, 'loading shared file');
      } catch (handlerErr) {
        // Ignore error handler failures
        console.warn('Error handler failed:', handlerErr);
      }
    } finally {
      setLoading(false);
    }
  }, [shareToken, accessLogged]);

  // Load shared file data on component mount
  useEffect(() => {
    if (shareToken) {
      loadSharedFile();
    } else {
      setError({
        type: 'INVALID_TOKEN',
        message: 'Invalid share link'
      });
      setLoading(false);
    }
  }, [shareToken, loadSharedFile]);

  /**
   * Handle file download with enhanced feedback
   */
  const handleDownload = async () => {
    if (!shareData || shareData.permission === 'VIEW_ONLY') {
      showError('Download is not permitted for this file');
      return;
    }

    try {
      setDownloading(true);
      
      // Show download starting notification
      showInfo('Starting download...', { duration: 2000 });

      // Log download access
      await logFileAccess('DOWNLOAD');

      const response = await axios.get(`/api/files/shared/${shareToken}/download`, {
        responseType: 'blob',
        timeout: 30000, // 30 second timeout
        onDownloadProgress: (progressEvent) => {
          // Could show progress here if needed
          console.log('Download progress:', progressEvent);
        }
      });

      // Validate response
      if (!response.data || response.data.size === 0) {
        throw new Error('Downloaded file is empty');
      }

      // Create download link
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', shareData.file.originalFileName);
      link.style.display = 'none';
      document.body.appendChild(link);
      link.click();
      
      // Cleanup
      setTimeout(() => {
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      }, 100);

      // Show success notification
      showSuccess(`Downloaded "${shareData.file.originalFileName}" successfully!`, {
        duration: 4000
      });

    } catch (err) {
      console.error('Error downloading file:', err);
      
      // Handle specific download errors
      if (err.code === 'ECONNABORTED' || err.message.includes('timeout')) {
        showError('Download timed out. The file might be too large or your connection is slow.', {
          action: {
            label: 'Retry',
            onClick: handleDownload
          },
          duration: 8000
        });
      } else if (err.response?.status === 413) {
        showError('File is too large to download');
      } else if (err.response?.status === 404) {
        showError('File not found or has been removed');
      } else {
        handleApiError(err);
        handleSharingError(err, 'downloading file');
      }
    } finally {
      setDownloading(false);
    }
  };

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
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  /**
   * Get file type icon based on file extension
   */
  const getFileIcon = (fileName) => {
    if (!fileName) return '📄';

    const extension = fileName.split('.').pop().toLowerCase();
    const iconMap = {
      // Images
      'jpg': '🖼️', 'jpeg': '🖼️', 'png': '🖼️', 'gif': '🖼️', 'svg': '🖼️',
      // Documents
      'pdf': '📕', 'doc': '📘', 'docx': '📘', 'txt': '📄',
      // Spreadsheets
      'xls': '📊', 'xlsx': '📊', 'csv': '📊',
      // Presentations
      'ppt': '📊', 'pptx': '📊',
      // Archives
      'zip': '🗜️', 'rar': '🗜️', '7z': '🗜️',
      // Code
      'js': '📜', 'html': '📜', 'css': '📜', 'json': '📜',
      // Media
      'mp4': '🎬', 'avi': '🎬', 'mov': '🎬',
      'mp3': '🎵', 'wav': '🎵', 'flac': '🎵'
    };

    return iconMap[extension] || '📄';
  };

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <div className="shared-file-container">
        <div className="shared-file-content">
          <div className="loading-state">
            <div className="loading-spinner"></div>
            <h2>Loading shared file...</h2>
            <p>Please wait while we retrieve the file information.</p>
          </div>
        </div>
      </div>
    );
  }

  /**
   * Render error state
   */
  if (error) {
    return (
      <div className="shared-file-container">
        <div className="shared-file-content">
          <div className="error-state">
            <div className="error-icon">
              {error.type === 'EXPIRED' && '⏰'}
              {error.type === 'NOT_FOUND' && '🔍'}
              {error.type === 'REVOKED' && '🚫'}
              {error.type === 'RATE_LIMITED' && '⚠️'}
              {(error.type === 'SERVER_ERROR' || error.type === 'NETWORK_ERROR' || error.type === 'INVALID_TOKEN') && '❌'}
            </div>
            <h2>Unable to Access File</h2>
            <p className="error-message">{error.message}</p>

            {error.type === 'NETWORK_ERROR' && (
              <button
                className="retry-btn"
                onClick={loadSharedFile}
              >
                Try Again
              </button>
            )}

            {(error.type === 'EXPIRED' || error.type === 'REVOKED') && (
              <div className="error-help">
                <p>If you believe this is an error, please contact the person who shared this file with you.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  /**
   * Handle missing or invalid file data
   */
  if (!shareData.file) {
    return (
      <div className="shared-file-container">
        <div className="shared-file-content">
          <div className="error-state">
            <div className="error-icon">❌</div>
            <h2>Unable to Access File</h2>
            <p className="error-message">File information is not available or has been corrupted.</p>
          </div>
        </div>
      </div>
    );
  }

  /**
   * Render successful file access
   */
  return (
    <div className="shared-file-container">
      <div className="shared-file-content">
        <div className="file-header">
          <div className="file-icon-large">
            {getFileIcon(shareData.file.originalFileName)}
          </div>
          <div className="file-info">
            <h1 className="file-title">{shareData.file.originalFileName}</h1>
            <div className="file-metadata">
              <span className="file-size">{formatFileSize(shareData.file.fileSize)}</span>
              <span className="file-separator">•</span>
              <span className="file-date">Shared on {formatDate(shareData.createdAt)}</span>
            </div>
            {shareData.expiresAt && (
              <div className="expiration-info">
                <span className="expiration-label">Expires:</span>
                <span className="expiration-date">{formatDate(shareData.expiresAt)}</span>
              </div>
            )}
          </div>
        </div>

        <div className="file-preview-section">
          <div className="preview-placeholder">
            <div className="preview-icon">
              {getFileIcon(shareData.file.originalFileName)}
            </div>
            <p className="preview-text">
              File preview is not available for this file type.
            </p>
          </div>
        </div>

        <div className="file-actions">
          {shareData.permission === 'DOWNLOAD' ? (
            <button
              className="download-btn primary"
              onClick={handleDownload}
              disabled={downloading}
            >
              {downloading ? (
                <>
                  <span className="btn-spinner"></span>
                  Downloading...
                </>
              ) : (
                <>
                  <span className="download-icon">⬇️</span>
                  Download File
                </>
              )}
            </button>
          ) : (
            <div className="view-only-notice">
              <span className="view-icon">👁️</span>
              <span>View-only access - Download not permitted</span>
            </div>
          )}
        </div>

        <div className="share-info">
          <div className="permission-badge">
            <span className="badge-icon">
              {shareData.permission === 'DOWNLOAD' ? '⬇️' : '👁️'}
            </span>
            <span className="badge-text">
              {shareData.permission === 'DOWNLOAD' ? 'Download Access' : 'View Only'}
            </span>
          </div>

          {shareData.accessCount > 0 && (
            <div className="access-count">
              <span className="access-icon">👥</span>
              <span>Accessed {shareData.accessCount} time{shareData.accessCount !== 1 ? 's' : ''}</span>
            </div>
          )}
        </div>

        <div className="powered-by">
          <p>Powered by File Sharing App</p>
        </div>
      </div>
    </div>
  );
};

export default SharedFileAccess;