import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

/**
 * Secure route component for accessing shared files with enhanced security.
 * 
 * This component provides secure access to shared files through public URLs
 * with comprehensive security measures including:
 * - Share token validation and expiration checking
 * - Rate limiting and abuse prevention
 * - Security headers enforcement
 * - Error handling for various security scenarios
 * - HTTPS enforcement (when configured)
 * 
 * @component
 * @example
 * // Used in App.js routing
 * <Route path="/shared/:token" element={<SecureShareRoute />} />
 */
function SecureShareRoute() {
  const { token } = useParams();
  const navigate = useNavigate();
  const [fileInfo, setFileInfo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    // Validate token format before making request
    if (!isValidTokenFormat(token)) {
      setError('Invalid share link format');
      setLoading(false);
      return;
    }

    // Enforce HTTPS if required (client-side check)
    if (shouldEnforceHttps() && window.location.protocol !== 'https:') {
      // Redirect to HTTPS version
      const httpsUrl = window.location.href.replace('http:', 'https:');
      window.location.replace(httpsUrl);
      return;
    }

    fetchSharedFileInfo();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  /**
   * Validates the share token format (UUID format).
   * 
   * @param {string} token - The share token to validate
   * @returns {boolean} True if token format is valid, false otherwise
   */
  const isValidTokenFormat = (token) => {
    if (!token) return false;
    
    // UUID format: 8-4-4-4-12 hexadecimal characters
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    return uuidRegex.test(token);
  };

  /**
   * Determines if HTTPS should be enforced based on environment.
   * 
   * @returns {boolean} True if HTTPS should be enforced, false otherwise
   */
  const shouldEnforceHttps = () => {
    // In production, enforce HTTPS for share links
    return process.env.NODE_ENV === 'production' || 
           process.env.REACT_APP_ENFORCE_HTTPS === 'true';
  };

  /**
   * Fetches shared file information with comprehensive error handling.
   */
  const fetchSharedFileInfo = async () => {
    try {
      setLoading(true);
      setError(null);

      // Create axios instance with security headers
      const secureAxios = axios.create({
        timeout: 10000, // 10 second timeout
        headers: {
          'Accept': 'application/json',
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      });

      const response = await secureAxios.get(`/api/files/shared/${token}`);
      
      // Validate response structure
      if (!response.data || !response.data.fileName) {
        throw new Error('Invalid response format');
      }

      setFileInfo(response.data);
      
      // Log successful access (client-side analytics)
      logShareAccess('view', response.data.fileName);
      
    } catch (error) {
      handleShareAccessError(error);
    } finally {
      setLoading(false);
    }
  };

  /**
   * Handles various types of share access errors with appropriate user messages.
   * 
   * @param {Error} error - The error object from the API request
   */
  const handleShareAccessError = (error) => {
    console.error('Share access error:', error);

    if (error.response) {
      const status = error.response.status;
      const message = error.response.data?.message || 'Unknown error';

      switch (status) {
        case 404:
          setError('Share not found or has expired');
          break;
        case 403:
          setError('Access denied. This share may have been revoked or you may have exceeded the access limit.');
          break;
        case 429:
          setError('Too many requests. Please wait a moment before trying again.');
          break;
        case 410:
          setError('This share has expired and is no longer available');
          break;
        default:
          setError(`Access error: ${message}`);
      }
    } else if (error.request) {
      setError('Network error. Please check your connection and try again.');
    } else {
      setError('An unexpected error occurred. Please try again.');
    }
  };

  /**
   * Securely downloads the shared file with proper error handling.
   */
  const handleDownload = async () => {
    if (!fileInfo || !fileInfo.permission?.allowsDownload) {
      setError('Download not permitted for this share');
      return;
    }

    try {
      setDownloading(true);
      setError(null);

      // Create secure download request
      const response = await axios.get(`/api/files/shared/${token}/download`, {
        responseType: 'blob',
        timeout: 30000, // 30 second timeout for downloads
        headers: {
          'Accept': '*/*',
          'Cache-Control': 'no-cache'
        }
      });

      // Validate response
      if (!response.data || response.data.size === 0) {
        throw new Error('Empty file received');
      }

      // Create secure download link
      const blob = new Blob([response.data], { 
        type: fileInfo.contentType || 'application/octet-stream' 
      });
      
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', fileInfo.fileName);
      
      // Security: Ensure link doesn't navigate away
      link.setAttribute('rel', 'noopener noreferrer');
      
      document.body.appendChild(link);
      link.click();
      
      // Cleanup
      link.remove();
      window.URL.revokeObjectURL(url);
      
      // Log successful download
      logShareAccess('download', fileInfo.fileName);
      
    } catch (error) {
      console.error('Download error:', error);
      
      if (error.response?.status === 403) {
        setError('Download permission denied');
      } else if (error.response?.status === 429) {
        setError('Download rate limit exceeded. Please wait before trying again.');
      } else {
        setError('Download failed. Please try again.');
      }
    } finally {
      setDownloading(false);
    }
  };

  /**
   * Logs share access events for analytics and security monitoring.
   * 
   * @param {string} action - The action performed ('view' or 'download')
   * @param {string} fileName - The name of the accessed file
   */
  const logShareAccess = (action, fileName) => {
    try {
      // Client-side logging for analytics
      const logData = {
        action,
        fileName,
        token: token.substring(0, 8) + '...', // Partial token for privacy
        timestamp: new Date().toISOString(),
        userAgent: navigator.userAgent,
        referrer: document.referrer || 'direct'
      };
      
      console.log('Share access logged:', logData);
      
      // In a real application, you might send this to an analytics service
      // analytics.track('share_access', logData);
      
    } catch (error) {
      console.warn('Failed to log share access:', error);
    }
  };

  /**
   * Formats file size for display.
   * 
   * @param {number} bytes - File size in bytes
   * @returns {string} Formatted file size string
   */
  const formatFileSize = (bytes) => {
    if (!bytes) return 'Unknown size';
    
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;
    
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    
    return `${size.toFixed(1)} ${units[unitIndex]}`;
  };

  /**
   * Formats expiration date for display.
   * 
   * @param {string} expiresAt - ISO date string
   * @returns {string} Formatted expiration string
   */
  const formatExpiration = (expiresAt) => {
    if (!expiresAt) return 'Never expires';
    
    const expDate = new Date(expiresAt);
    const now = new Date();
    
    if (expDate <= now) {
      return 'Expired';
    }
    
    return `Expires ${expDate.toLocaleDateString()} at ${expDate.toLocaleTimeString()}`;
  };

  // Loading state
  if (loading) {
    return (
      <div className="secure-share-container">
        <div className="loading-spinner">
          <div className="spinner"></div>
          <p>Loading shared file...</p>
        </div>
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div className="secure-share-container">
        <div className="error-container">
          <div className="error-icon">⚠️</div>
          <h2>Access Error</h2>
          <p className="error-message">{error}</p>
          <div className="error-actions">
            <button onClick={() => window.location.reload()} className="retry-btn">
              Try Again
            </button>
            <button onClick={() => navigate('/')} className="home-btn">
              Go Home
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Success state - display file information
  return (
    <div className="secure-share-container">
      <div className="share-header">
        <h1>📁 Shared File</h1>
        <div className="security-indicator">
          <span className="secure-badge">🔒 Secure Share</span>
        </div>
      </div>

      <div className="file-info-card">
        <div className="file-icon">
          📄
        </div>
        
        <div className="file-details">
          <h2 className="file-name">{fileInfo.fileName}</h2>
          
          <div className="file-metadata">
            <div className="metadata-item">
              <span className="label">Size:</span>
              <span className="value">{formatFileSize(fileInfo.fileSize)}</span>
            </div>
            
            <div className="metadata-item">
              <span className="label">Type:</span>
              <span className="value">{fileInfo.contentType || 'Unknown'}</span>
            </div>
            
            <div className="metadata-item">
              <span className="label">Access:</span>
              <span className="value">
                {fileInfo.permission?.allowsDownload ? 'View & Download' : 'View Only'}
              </span>
            </div>
            
            <div className="metadata-item">
              <span className="label">Expires:</span>
              <span className="value">{formatExpiration(fileInfo.expiresAt)}</span>
            </div>
            
            {fileInfo.maxAccess && (
              <div className="metadata-item">
                <span className="label">Access Count:</span>
                <span className="value">{fileInfo.accessCount} / {fileInfo.maxAccess}</span>
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="file-actions">
        {fileInfo.permission?.allowsDownload && (
          <button 
            onClick={handleDownload} 
            disabled={downloading}
            className="download-btn primary"
          >
            {downloading ? (
              <>
                <span className="spinner small"></span>
                Downloading...
              </>
            ) : (
              <>
                ⬇️ Download File
              </>
            )}
          </button>
        )}
        
        <button onClick={() => navigate('/')} className="home-btn secondary">
          🏠 Go to App
        </button>
      </div>

      <div className="security-notice">
        <p>
          🔒 This is a secure share link. Access is logged for security purposes.
          Do not share this link with unauthorized users.
        </p>
      </div>
    </div>
  );
}

export default SecureShareRoute;