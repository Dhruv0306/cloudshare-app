import React from 'react';
import { useNotification } from './NotificationSystem';
import './SharingErrorBoundary.css';

/**
 * Enhanced error boundary specifically for file sharing components
 * Provides specialized error handling and recovery options for sharing features
 */
class SharingErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { 
      hasError: false, 
      error: null, 
      errorInfo: null,
      errorType: 'unknown',
      retryCount: 0
    };
  }

  static getDerivedStateFromError(error) {
    // Analyze error to determine type and appropriate response
    const errorType = SharingErrorBoundary.categorizeError(error);
    
    return { 
      hasError: true,
      errorType
    };
  }

  /**
   * Categorize error to provide appropriate handling
   * @param {Error} error - The caught error
   * @returns {string} Error category
   */
  static categorizeError(error) {
    const message = error.message?.toLowerCase() || '';
    
    if (message.includes('network') || message.includes('fetch')) {
      return 'network';
    }
    
    if (message.includes('permission') || message.includes('unauthorized')) {
      return 'permission';
    }
    
    if (message.includes('share') || message.includes('token')) {
      return 'sharing';
    }
    
    if (message.includes('validation') || message.includes('invalid')) {
      return 'validation';
    }
    
    return 'unknown';
  }

  componentDidCatch(error, errorInfo) {
    // Log error details for debugging
    console.error('SharingErrorBoundary caught an error:', error, errorInfo);
    
    this.setState({
      error: error,
      errorInfo: errorInfo
    });

    // Report error to monitoring service if available
    this.reportError(error, errorInfo);
  }

  /**
   * Report error to monitoring service
   * @param {Error} error - The caught error
   * @param {object} errorInfo - Error information from React
   */
  reportError = (error, errorInfo) => {
    // In a real application, you would send this to your error monitoring service
    // Example: Sentry, LogRocket, Bugsnag, etc.
    
    const errorReport = {
      message: error.message,
      stack: error.stack,
      componentStack: errorInfo.componentStack,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href,
      errorType: this.state.errorType,
      retryCount: this.state.retryCount
    };
    
    // Log to console for development
    console.error('Error Report:', errorReport);
    
    // TODO: Send to error monitoring service
    // errorMonitoringService.captureException(error, errorReport);
  };

  /**
   * Handle retry attempt
   */
  handleRetry = () => {
    this.setState(prevState => ({ 
      hasError: false, 
      error: null, 
      errorInfo: null,
      retryCount: prevState.retryCount + 1
    }));
  };

  /**
   * Handle fallback to safe mode
   */
  handleFallback = () => {
    // Redirect to a safe state or show minimal UI
    if (this.props.onFallback) {
      this.props.onFallback();
    } else {
      // Default fallback behavior
      window.location.reload();
    }
  };

  /**
   * Get error-specific messages and actions
   */
  getErrorDetails = () => {
    const { errorType, retryCount } = this.state;
    
    switch (errorType) {
      case 'network':
        return {
          title: 'Connection Problem',
          message: 'Unable to connect to the server. Please check your internet connection and try again.',
          icon: '🌐',
          showRetry: true,
          showReload: true,
          suggestions: [
            'Check your internet connection',
            'Try refreshing the page',
            'Contact support if the problem persists'
          ]
        };
        
      case 'permission':
        return {
          title: 'Permission Denied',
          message: 'You don\'t have permission to perform this action. Please log in again or contact an administrator.',
          icon: '🔒',
          showRetry: false,
          showReload: true,
          suggestions: [
            'Try logging out and logging back in',
            'Contact your administrator for access',
            'Refresh the page to update your session'
          ]
        };
        
      case 'sharing':
        return {
          title: 'Sharing Error',
          message: 'There was a problem with the file sharing feature. This might be a temporary issue.',
          icon: '🔗',
          showRetry: true,
          showReload: false,
          suggestions: [
            'Try the sharing operation again',
            'Check if the file still exists',
            'Try sharing a different file to test'
          ]
        };
        
      case 'validation':
        return {
          title: 'Invalid Data',
          message: 'The information provided is not valid. Please check your input and try again.',
          icon: '⚠️',
          showRetry: true,
          showReload: false,
          suggestions: [
            'Check all form fields for errors',
            'Ensure email addresses are valid',
            'Verify expiration dates are in the future'
          ]
        };
        
      default:
        return {
          title: 'Something Went Wrong',
          message: 'An unexpected error occurred. We\'re sorry for the inconvenience.',
          icon: '❌',
          showRetry: retryCount < 3,
          showReload: true,
          suggestions: [
            'Try refreshing the page',
            'Clear your browser cache',
            'Contact support if the problem continues'
          ]
        };
    }
  };

  render() {
    if (this.state.hasError) {
      const errorDetails = this.getErrorDetails();
      const { retryCount } = this.state;
      
      return (
        <div className="sharing-error-boundary">
          <div className="sharing-error-content">
            <div className="error-icon">
              {errorDetails.icon}
            </div>
            
            <h2 className="error-title">
              {errorDetails.title}
            </h2>
            
            <p className="error-message">
              {errorDetails.message}
            </p>
            
            {retryCount > 0 && (
              <div className="retry-info">
                <small>Retry attempt: {retryCount}</small>
              </div>
            )}
            
            <div className="error-actions">
              {errorDetails.showRetry && retryCount < 3 && (
                <button 
                  onClick={this.handleRetry}
                  className="error-btn retry-btn"
                >
                  Try Again
                </button>
              )}
              
              {errorDetails.showReload && (
                <button 
                  onClick={() => window.location.reload()}
                  className="error-btn reload-btn"
                >
                  Reload Page
                </button>
              )}
              
              <button 
                onClick={this.handleFallback}
                className="error-btn fallback-btn"
              >
                Go Back
              </button>
            </div>
            
            {errorDetails.suggestions && (
              <div className="error-suggestions">
                <h4>What you can try:</h4>
                <ul>
                  {errorDetails.suggestions.map((suggestion, index) => (
                    <li key={index}>{suggestion}</li>
                  ))}
                </ul>
              </div>
            )}
            
            {process.env.NODE_ENV === 'development' && this.state.error && (
              <details className="error-details">
                <summary>Technical Details (Development Mode)</summary>
                <div className="error-stack">
                  <h5>Error Message:</h5>
                  <pre>{this.state.error.toString()}</pre>
                  
                  <h5>Stack Trace:</h5>
                  <pre>{this.state.error.stack}</pre>
                  
                  <h5>Component Stack:</h5>
                  <pre>{this.state.errorInfo?.componentStack}</pre>
                </div>
              </details>
            )}
            
            <div className="error-help">
              <p>
                If this problem continues, please{' '}
                <a 
                  href="mailto:support@example.com?subject=File Sharing Error"
                  className="support-link"
                >
                  contact support
                </a>
                {' '}with details about what you were trying to do.
              </p>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

/**
 * Hook-based wrapper for the error boundary
 * Allows functional components to use the error boundary with notifications
 */
export const withSharingErrorBoundary = (Component) => {
  return function WrappedComponent(props) {
    return (
      <SharingErrorBoundary>
        <Component {...props} />
      </SharingErrorBoundary>
    );
  };
};

/**
 * Higher-order component for adding sharing error boundary
 */
export const useSharingErrorHandler = () => {
  const { showError, showWarning } = useNotification();
  
  /**
   * Handle sharing-specific errors with appropriate notifications
   * @param {Error} error - The error to handle
   * @param {string} context - Context where the error occurred
   */
  const handleSharingError = (error, context = 'sharing operation') => {
    console.error(`Error in ${context}:`, error);
    
    if (error.response) {
      const status = error.response.status;
      const message = error.response.data?.message || error.message;
      
      switch (status) {
        case 400:
          showError(`Invalid request: ${message}`, {
            action: {
              label: 'Try Again',
              onClick: () => window.location.reload()
            }
          });
          break;
          
        case 401:
          showError('You need to log in to perform this action', {
            action: {
              label: 'Login',
              onClick: () => window.location.href = '/login'
            }
          });
          break;
          
        case 403:
          showError('You don\'t have permission to perform this action');
          break;
          
        case 404:
          showError('The requested file or share was not found');
          break;
          
        case 409:
          showWarning('This action conflicts with the current state. Please refresh and try again', {
            action: {
              label: 'Refresh',
              onClick: () => window.location.reload()
            }
          });
          break;
          
        case 429:
          showWarning('Too many requests. Please wait a moment and try again');
          break;
          
        case 500:
        default:
          showError('Server error occurred. Please try again later', {
            action: {
              label: 'Retry',
              onClick: () => window.location.reload()
            }
          });
          break;
      }
    } else if (error.code === 'NETWORK_ERROR') {
      showError('Network connection problem. Please check your internet connection', {
        action: {
          label: 'Retry',
          onClick: () => window.location.reload()
        }
      });
    } else {
      showError(`An error occurred during ${context}. Please try again`);
    }
  };
  
  return { handleSharingError };
};

export default SharingErrorBoundary;