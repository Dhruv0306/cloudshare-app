import React, { useState, useEffect, useCallback } from 'react';
import FormField from './FormField';
import { validateEmail } from '../utils/validation';
import { useNotification } from './NotificationSystem';
import { useSharingErrorHandler } from './SharingErrorBoundary';
import './ShareFileModal.css';

/**
 * Modal component for sharing files with configurable permissions and expiration
 * Supports email notifications and shareable link generation
 */
const ShareFileModal = ({ 
  isOpen, 
  onClose, 
  file, 
  onShare,
  loading = false 
}) => {
  // Hooks for notifications and error handling
  const { showSuccess, showError, showWarning, showLoading, removeNotification } = useNotification();
  const { handleSharingError } = useSharingErrorHandler();

  // Form state
  const [permission, setPermission] = useState('DOWNLOAD');
  const [expirationOption, setExpirationOption] = useState('1_DAY');
  const [customExpiration, setCustomExpiration] = useState('');
  const [emailRecipients, setEmailRecipients] = useState('');
  const [sendNotification, setSendNotification] = useState(false);
  
  // UI state
  const [shareUrl, setShareUrl] = useState('');
  const [isShared, setIsShared] = useState(false);
  const [copySuccess, setCopySuccess] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [touched, setTouched] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [retryCount, setRetryCount] = useState(0);

  // Reset form when modal opens/closes
  useEffect(() => {
    if (isOpen) {
      resetForm();
    }
  }, [isOpen, file]);



  /**
   * Reset form to initial state
   */
  const resetForm = () => {
    setPermission('DOWNLOAD');
    setExpirationOption('1_DAY');
    setCustomExpiration('');
    setEmailRecipients('');
    setSendNotification(false);
    setShareUrl('');
    setIsShared(false);
    setCopySuccess(false);
    setFieldErrors({});
    setTouched({});
    setIsSubmitting(false);
    setSubmitError(null);
    setRetryCount(0);
  };

  /**
   * Handle modal close
   */
  const handleClose = useCallback(() => {
    resetForm();
    onClose();
  }, [onClose]);

  // Handle Escape key to close modal
  useEffect(() => {
    const handleEscapeKey = (event) => {
      if (event.key === 'Escape' && isOpen) {
        handleClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEscapeKey);
    }

    return () => {
      document.removeEventListener('keydown', handleEscapeKey);
    };
  }, [isOpen, handleClose]);

  /**
   * Handle field changes and clear errors
   */
  const handleFieldChange = (field, value) => {
    switch (field) {
      case 'permission':
        setPermission(value);
        break;
      case 'expirationOption':
        setExpirationOption(value);
        if (value !== 'CUSTOM') {
          setCustomExpiration('');
        }
        break;
      case 'customExpiration':
        setCustomExpiration(value);
        break;
      case 'emailRecipients':
        setEmailRecipients(value);
        break;
      case 'sendNotification':
        setSendNotification(value);
        break;
      default:
        break;
    }

    // Clear field error when user makes changes
    if (fieldErrors[field]) {
      setFieldErrors(prev => ({
        ...prev,
        [field]: ''
      }));
    }
  };

  /**
   * Handle field blur for validation
   */
  const handleFieldBlur = (field) => {
    setTouched(prev => ({
      ...prev,
      [field]: true
    }));

    validateField(field);
  };

  /**
   * Validate individual field
   */
  const validateField = (field) => {
    let error = '';

    switch (field) {
      case 'customExpiration':
        if (expirationOption === 'CUSTOM' && !customExpiration) {
          error = 'Custom expiration date is required';
        } else if (expirationOption === 'CUSTOM' && customExpiration) {
          const selectedDate = new Date(customExpiration);
          const now = new Date();
          if (selectedDate <= now) {
            error = 'Expiration date must be in the future';
          }
        }
        break;
      case 'emailRecipients':
        if (sendNotification && emailRecipients.trim()) {
          const emails = emailRecipients.split(',').map(email => email.trim());
          for (const email of emails) {
            if (email) {
              const validation = validateEmail(email);
              if (!validation.isValid) {
                error = `Invalid email: ${email}`;
                break;
              }
            }
          }
        } else if (sendNotification && !emailRecipients.trim()) {
          error = 'Email recipients are required when sending notifications';
        }
        break;
      default:
        break;
    }

    if (error) {
      setFieldErrors(prev => ({
        ...prev,
        [field]: error
      }));
    }

    return !error;
  };

  /**
   * Validate entire form
   */
  const validateForm = () => {
    const fieldsToValidate = ['customExpiration', 'emailRecipients'];
    let isValid = true;

    fieldsToValidate.forEach(field => {
      if (!validateField(field)) {
        isValid = false;
      }
    });

    return isValid;
  };

  /**
   * Calculate expiration date based on selected option
   */
  const getExpirationDate = () => {
    if (expirationOption === 'NEVER') {
      return null;
    }
    
    if (expirationOption === 'CUSTOM') {
      return customExpiration ? new Date(customExpiration).toISOString() : null;
    }

    const now = new Date();
    switch (expirationOption) {
      case '1_HOUR':
        return new Date(now.getTime() + 60 * 60 * 1000).toISOString();
      case '1_DAY':
        return new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString();
      case '1_WEEK':
        return new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString();
      default:
        return new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString();
    }
  };

  /**
   * Handle form submission with comprehensive error handling
   */
  const handleSubmit = async (e) => {
    e.preventDefault();

    // Mark all relevant fields as touched
    setTouched({
      customExpiration: true,
      emailRecipients: true
    });

    if (!validateForm()) {
      showWarning('Please fix the form errors before sharing');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    // Show loading notification
    const loadingNotificationId = showLoading('Creating share link...', {
      persistent: true
    });

    const shareData = {
      permission,
      expiresAt: getExpirationDate(),
      recipientEmails: sendNotification && emailRecipients.trim() 
        ? emailRecipients.split(',').map(email => email.trim()).filter(email => email)
        : [],
      sendNotification
    };

    try {
      const result = await onShare(file.id, shareData);
      
      // Remove loading notification
      removeNotification(loadingNotificationId);
      
      if (result && result.shareUrl) {
        setShareUrl(result.shareUrl);
        setIsShared(true);
        setRetryCount(0);
        
        // Show success notification
        showSuccess('Share link created successfully!', {
          duration: 4000
        });
        
        // Show additional success message if email notifications were sent
        if (sendNotification && emailRecipients.trim()) {
          const emailCount = emailRecipients.split(',').length;
          showSuccess(`Email notifications sent to ${emailCount} recipient${emailCount !== 1 ? 's' : ''}`, {
            duration: 6000
          });
        }
      } else {
        throw new Error('Invalid response from server');
      }
    } catch (error) {
      // Remove loading notification
      removeNotification(loadingNotificationId);
      
      setSubmitError(error);
      setRetryCount(prev => prev + 1);
      
      // Handle error with specialized error handler
      handleSharingError(error, 'file sharing');
      
      // Show retry option for certain errors
      if (error.response?.status >= 500 || error.code === 'NETWORK_ERROR') {
        showError('Failed to create share link. Please try again.', {
          action: {
            label: 'Retry',
            onClick: () => handleSubmit(e)
          },
          duration: 8000
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * Copy share URL to clipboard with enhanced feedback
   */
  const handleCopyUrl = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopySuccess(true);
      showSuccess('Share link copied to clipboard!', { duration: 3000 });
      setTimeout(() => setCopySuccess(false), 2000);
    } catch (error) {
      console.error('Failed to copy URL:', error);
      
      try {
        // Fallback for older browsers
        const textArea = document.createElement('textarea');
        textArea.value = shareUrl;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        textArea.style.top = '-999999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        
        const successful = document.execCommand('copy');
        document.body.removeChild(textArea);
        
        if (successful) {
          setCopySuccess(true);
          showSuccess('Share link copied to clipboard!', { duration: 3000 });
          setTimeout(() => setCopySuccess(false), 2000);
        } else {
          throw new Error('Copy command failed');
        }
      } catch (fallbackError) {
        showError('Unable to copy to clipboard. Please select and copy the link manually.', {
          duration: 6000
        });
      }
    }
  };



  /**
   * Get minimum date for custom expiration (tomorrow)
   */
  const getMinDate = () => {
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    return tomorrow.toISOString().split('T')[0];
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={handleClose}>
      <div 
        className="modal-content" 
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-labelledby="modal-title"
        aria-modal="true"
      >
        <div className="modal-header">
          <h2 id="modal-title">Share File</h2>
          <button 
            className="modal-close-btn" 
            onClick={handleClose}
            aria-label="Close modal"
          >
            ×
          </button>
        </div>

        <div className="modal-body">
          {file && (
            <div className="file-info-section">
              <div className="file-icon">📄</div>
              <div className="file-details">
                <h3>{file.originalFileName}</h3>
                <p>{(file.fileSize / 1024).toFixed(1)} KB</p>
              </div>
            </div>
          )}

          {!isShared ? (
            <form onSubmit={handleSubmit} className="share-form">
              {/* Error Display */}
              {submitError && retryCount > 0 && (
                <div className="form-error-banner">
                  <div className="error-banner-content">
                    <span className="error-banner-icon">⚠️</span>
                    <div className="error-banner-text">
                      <strong>Failed to create share link</strong>
                      <p>
                        {submitError.response?.status === 413 
                          ? 'File is too large to share'
                          : submitError.response?.status === 403
                          ? 'You don\'t have permission to share this file'
                          : submitError.response?.status >= 500
                          ? 'Server error occurred. Please try again.'
                          : 'An unexpected error occurred. Please try again.'}
                      </p>
                      {retryCount > 1 && (
                        <small>Retry attempt: {retryCount}</small>
                      )}
                    </div>
                    <button
                      type="button"
                      className="error-banner-close"
                      onClick={() => setSubmitError(null)}
                      aria-label="Dismiss error"
                    >
                      ×
                    </button>
                  </div>
                </div>
              )}

              <div className="form-section">
                <h4>Permissions</h4>
                <div className="radio-group">
                  <label className="radio-option">
                    <input
                      type="radio"
                      name="permission"
                      value="VIEW_ONLY"
                      checked={permission === 'VIEW_ONLY'}
                      onChange={(e) => handleFieldChange('permission', e.target.value)}
                    />
                    <span className="radio-label">
                      <strong>View Only</strong>
                      <small>Recipients can view but not download the file</small>
                    </span>
                  </label>
                  <label className="radio-option">
                    <input
                      type="radio"
                      name="permission"
                      value="DOWNLOAD"
                      checked={permission === 'DOWNLOAD'}
                      onChange={(e) => handleFieldChange('permission', e.target.value)}
                    />
                    <span className="radio-label">
                      <strong>Download</strong>
                      <small>Recipients can view and download the file</small>
                    </span>
                  </label>
                </div>
              </div>

              <div className="form-section">
                <h4>Expiration</h4>
                <div className="select-group">
                  <select
                    value={expirationOption}
                    onChange={(e) => handleFieldChange('expirationOption', e.target.value)}
                    className="form-select"
                  >
                    <option value="1_HOUR">1 Hour</option>
                    <option value="1_DAY">1 Day</option>
                    <option value="1_WEEK">1 Week</option>
                    <option value="CUSTOM">Custom Date</option>
                    <option value="NEVER">Never</option>
                  </select>
                </div>

                {expirationOption === 'CUSTOM' && (
                  <div className="custom-date-field">
                    <div className={`form-field ${touched.customExpiration && fieldErrors.customExpiration ? 'has-error' : ''}`}>
                      <label htmlFor="customExpiration" className="form-label">
                        Expiration Date
                        <span className="required-indicator">*</span>
                      </label>
                      <input
                        id="customExpiration"
                        type="date"
                        value={customExpiration}
                        onChange={(e) => handleFieldChange('customExpiration', e.target.value)}
                        onBlur={() => handleFieldBlur('customExpiration')}
                        min={getMinDate()}
                        className={`form-input ${touched.customExpiration && fieldErrors.customExpiration ? 'error' : ''}`}
                        required
                        aria-invalid={touched.customExpiration && fieldErrors.customExpiration}
                        aria-describedby={touched.customExpiration && fieldErrors.customExpiration ? 'customExpiration-error' : undefined}
                      />
                      {touched.customExpiration && fieldErrors.customExpiration && (
                        <div 
                          id="customExpiration-error"
                          className="form-error"
                          role="alert"
                          aria-live="polite"
                        >
                          {fieldErrors.customExpiration}
                        </div>
                      )}
                    </div>
                  </div>
                )}
              </div>

              <div className="form-section">
                <div className="checkbox-group">
                  <label className="checkbox-option">
                    <input
                      type="checkbox"
                      checked={sendNotification}
                      onChange={(e) => handleFieldChange('sendNotification', e.target.checked)}
                    />
                    <span className="checkbox-label">Send email notification</span>
                  </label>
                </div>

                {sendNotification && (
                  <FormField
                    id="emailRecipients"
                    label="Email Recipients"
                    type="text"
                    value={emailRecipients}
                    onChange={(e) => handleFieldChange('emailRecipients', e.target.value)}
                    onBlur={() => handleFieldBlur('emailRecipients')}
                    error={touched.emailRecipients ? fieldErrors.emailRecipients : ''}
                    placeholder="Enter email addresses separated by commas"
                    className="email-recipients-field"
                  />
                )}
              </div>

              <div className="modal-actions">
                <button 
                  type="button" 
                  onClick={handleClose}
                  className="btn btn-secondary"
                  disabled={loading || isSubmitting}
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  className="btn btn-primary"
                  disabled={loading || isSubmitting}
                >
                  {isSubmitting ? (
                    <>
                      <span className="btn-spinner"></span>
                      Creating Share...
                    </>
                  ) : loading ? (
                    'Creating Share...'
                  ) : retryCount > 0 ? (
                    `Retry (${retryCount + 1})`
                  ) : (
                    'Create Share Link'
                  )}
                </button>
              </div>
            </form>
          ) : (
            <div className="share-success">
              <div className="success-icon">✅</div>
              <h3>File Shared Successfully!</h3>
              
              <div className="share-url-section">
                <label htmlFor="shareUrl" className="share-url-label">
                  Shareable Link:
                </label>
                <div className="share-url-container">
                  <input
                    id="shareUrl"
                    type="text"
                    value={shareUrl}
                    readOnly
                    className="share-url-input"
                  />
                  <button
                    type="button"
                    onClick={handleCopyUrl}
                    className="copy-btn"
                    title="Copy to clipboard"
                  >
                    {copySuccess ? '✓' : '📋'}
                  </button>
                </div>
                {copySuccess && (
                  <div className="copy-success-message">
                    Link copied to clipboard!
                  </div>
                )}
              </div>

              <div className="share-details">
                <p><strong>Permission:</strong> {permission === 'VIEW_ONLY' ? 'View Only' : 'Download'}</p>
                <p><strong>Expires:</strong> {
                  expirationOption === 'NEVER' ? 'Never' : 
                  expirationOption === 'CUSTOM' ? new Date(customExpiration).toLocaleDateString() :
                  expirationOption === '1_HOUR' ? 'In 1 hour' :
                  expirationOption === '1_DAY' ? 'In 1 day' :
                  'In 1 week'
                }</p>
                {sendNotification && emailRecipients && (
                  <p><strong>Notified:</strong> {emailRecipients}</p>
                )}
              </div>

              <div className="modal-actions">
                <button 
                  type="button" 
                  onClick={handleClose}
                  className="btn btn-primary"
                >
                  Done
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ShareFileModal;