import React from 'react';
import './LoadingSpinner.css';

/**
 * Reusable loading spinner component with customizable size and message
 * Provides consistent loading states across the application
 */
const LoadingSpinner = ({ 
  size = 'medium', 
  message = 'Loading...', 
  overlay = false,
  className = '',
  showMessage = true 
}) => {
  const spinnerClass = `loading-spinner ${size} ${className}`;
  const containerClass = `loading-container ${overlay ? 'overlay' : ''}`;

  return (
    <div className={containerClass}>
      <div className="loading-content">
        <div className={spinnerClass} role="status" aria-label={message}>
          <div className="spinner-circle"></div>
        </div>
        {showMessage && (
          <div className="loading-message" aria-live="polite">
            {message}
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Inline loading spinner for buttons and small components
 */
export const InlineSpinner = ({ size = 'small', className = '' }) => {
  return (
    <div className={`inline-spinner ${size} ${className}`} role="status" aria-label="Loading">
      <div className="spinner-circle"></div>
    </div>
  );
};

/**
 * Loading overlay for covering entire components during async operations
 */
export const LoadingOverlay = ({ message = 'Loading...', visible = true }) => {
  if (!visible) return null;

  return (
    <div className="loading-overlay">
      <LoadingSpinner 
        size="large" 
        message={message} 
        overlay={true}
        showMessage={true}
      />
    </div>
  );
};

/**
 * Progress bar component for file operations
 */
export const ProgressBar = ({ 
  progress = 0, 
  message = 'Processing...', 
  showPercentage = true,
  className = '' 
}) => {
  const clampedProgress = Math.max(0, Math.min(100, progress));

  return (
    <div className={`progress-container ${className}`}>
      <div className="progress-header">
        <span className="progress-message">{message}</span>
        {showPercentage && (
          <span className="progress-percentage">{Math.round(clampedProgress)}%</span>
        )}
      </div>
      <div className="progress-bar">
        <div 
          className="progress-fill"
          style={{ width: `${clampedProgress}%` }}
          role="progressbar"
          aria-valuenow={clampedProgress}
          aria-valuemin="0"
          aria-valuemax="100"
          aria-label={`${Math.round(clampedProgress)}% complete`}
        />
      </div>
    </div>
  );
};

/**
 * Skeleton loader for content placeholders
 */
export const SkeletonLoader = ({ 
  lines = 3, 
  height = '1rem', 
  className = '',
  animated = true 
}) => {
  return (
    <div className={`skeleton-container ${className}`}>
      {Array.from({ length: lines }, (_, index) => (
        <div 
          key={index}
          className={`skeleton-line ${animated ? 'animated' : ''}`}
          style={{ 
            height,
            width: index === lines - 1 ? '75%' : '100%'
          }}
        />
      ))}
    </div>
  );
};

export default LoadingSpinner;