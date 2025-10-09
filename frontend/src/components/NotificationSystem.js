import React, { createContext, useContext, useState, useCallback } from 'react';
import './NotificationSystem.css';

/**
 * Notification context for managing global notifications
 * Provides methods to show success, error, warning, and info messages
 */
const NotificationContext = createContext();

/**
 * Hook to use notification system
 * @returns {object} Notification methods and state
 */
export const useNotification = () => {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotification must be used within a NotificationProvider');
  }
  return context;
};

/**
 * Notification provider component
 * Manages notification state and provides methods to show/hide notifications
 */
export const NotificationProvider = ({ children }) => {
  const [notifications, setNotifications] = useState([]);

  /**
   * Add a new notification
   * @param {string} type - Notification type (success, error, warning, info)
   * @param {string} message - Notification message
   * @param {object} options - Additional options (duration, persistent, action)
   */
  const addNotification = useCallback((type, message, options = {}) => {
    const id = Date.now() + Math.random();
    const notification = {
      id,
      type,
      message,
      timestamp: new Date(),
      duration: options.duration || (type === 'error' ? 8000 : 5000),
      persistent: options.persistent || false,
      action: options.action || null,
      ...options
    };

    setNotifications(prev => [...prev, notification]);

    // Auto-remove notification after duration (unless persistent)
    if (!notification.persistent) {
      setTimeout(() => {
        removeNotification(id);
      }, notification.duration);
    }

    return id;
  }, []);

  /**
   * Remove a notification by ID
   * @param {string|number} id - Notification ID
   */
  const removeNotification = useCallback((id) => {
    setNotifications(prev => prev.filter(notification => notification.id !== id));
  }, []);

  /**
   * Clear all notifications
   */
  const clearAllNotifications = useCallback(() => {
    setNotifications([]);
  }, []);

  /**
   * Show success notification
   * @param {string} message - Success message
   * @param {object} options - Additional options
   */
  const showSuccess = useCallback((message, options = {}) => {
    return addNotification('success', message, options);
  }, [addNotification]);

  /**
   * Show error notification
   * @param {string} message - Error message
   * @param {object} options - Additional options
   */
  const showError = useCallback((message, options = {}) => {
    return addNotification('error', message, options);
  }, [addNotification]);

  /**
   * Show warning notification
   * @param {string} message - Warning message
   * @param {object} options - Additional options
   */
  const showWarning = useCallback((message, options = {}) => {
    return addNotification('warning', message, options);
  }, [addNotification]);

  /**
   * Show info notification
   * @param {string} message - Info message
   * @param {object} options - Additional options
   */
  const showInfo = useCallback((message, options = {}) => {
    return addNotification('info', message, options);
  }, [addNotification]);

  /**
   * Show loading notification with progress
   * @param {string} message - Loading message
   * @param {object} options - Additional options
   */
  const showLoading = useCallback((message, options = {}) => {
    return addNotification('loading', message, { 
      persistent: true, 
      ...options 
    });
  }, [addNotification]);

  const value = {
    notifications,
    showSuccess,
    showError,
    showWarning,
    showInfo,
    showLoading,
    removeNotification,
    clearAllNotifications
  };

  return (
    <NotificationContext.Provider value={value}>
      {children}
      <NotificationContainer />
    </NotificationContext.Provider>
  );
};

/**
 * Individual notification component
 * Displays a single notification with appropriate styling and actions
 */
const Notification = ({ notification, onRemove }) => {
  const { id, type, message, action, timestamp } = notification;

  /**
   * Get notification icon based on type
   */
  const getIcon = () => {
    switch (type) {
      case 'success':
        return '✅';
      case 'error':
        return '❌';
      case 'warning':
        return '⚠️';
      case 'info':
        return 'ℹ️';
      case 'loading':
        return <div className="notification-spinner"></div>;
      default:
        return 'ℹ️';
    }
  };

  /**
   * Handle notification click
   */
  const handleClick = () => {
    if (action && action.onClick) {
      action.onClick();
    }
  };

  /**
   * Handle close button click
   */
  const handleClose = (e) => {
    e.stopPropagation();
    onRemove(id);
  };

  return (
    <div 
      className={`notification notification-${type} ${action ? 'clickable' : ''}`}
      onClick={handleClick}
      role="alert"
      aria-live="polite"
    >
      <div className="notification-icon">
        {getIcon()}
      </div>
      
      <div className="notification-content">
        <div className="notification-message">
          {message}
        </div>
        
        {action && action.label && (
          <button 
            className="notification-action-btn"
            onClick={(e) => {
              e.stopPropagation();
              action.onClick();
            }}
          >
            {action.label}
          </button>
        )}
        
        <div className="notification-timestamp">
          {timestamp.toLocaleTimeString()}
        </div>
      </div>
      
      <button 
        className="notification-close-btn"
        onClick={handleClose}
        aria-label="Close notification"
      >
        ×
      </button>
    </div>
  );
};

/**
 * Notification container component
 * Renders all active notifications in a fixed position
 */
const NotificationContainer = () => {
  const { notifications, removeNotification } = useNotification();

  if (notifications.length === 0) {
    return null;
  }

  return (
    <div className="notification-container">
      {notifications.map(notification => (
        <Notification
          key={notification.id}
          notification={notification}
          onRemove={removeNotification}
        />
      ))}
    </div>
  );
};

export default NotificationProvider;