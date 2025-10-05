import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import EmailVerificationForm from './EmailVerificationForm';
import FormField from './FormField';
import ErrorBoundary from './ErrorBoundary';
import { validateLoginForm } from '../utils/validation';

function Login({ onSwitchToSignup }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showEmailVerification, setShowEmailVerification] = useState(false);
  const [unverifiedEmail, setUnverifiedEmail] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [touched, setTouched] = useState({});
  const { login, getUserEmail } = useAuth();

  const handleFieldChange = (field, value) => {
    // Update field value
    switch (field) {
      case 'username':
        setUsername(value);
        break;
      case 'password':
        setPassword(value);
        break;
      default:
        break;
    }

    // Clear field error when user starts typing
    if (fieldErrors[field]) {
      setFieldErrors(prev => ({
        ...prev,
        [field]: ''
      }));
    }

    // Clear general error when user makes changes
    if (error) {
      setError('');
    }
  };

  const handleFieldBlur = (field) => {
    setTouched(prev => ({
      ...prev,
      [field]: true
    }));

    // Validate individual field on blur
    const formData = { username, password };
    const validation = validateLoginForm(formData);
    
    if (!validation.isValid && validation.errors[field]) {
      setFieldErrors(prev => ({
        ...prev,
        [field]: validation.errors[field]
      }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Mark all fields as touched
    setTouched({
      username: true,
      password: true
    });

    // Validate all fields
    const formData = { username, password };
    const validation = validateLoginForm(formData);
    
    if (!validation.isValid) {
      setFieldErrors(validation.errors);
      setError('Please correct the errors below');
      return;
    }

    setLoading(true);
    setError('');
    setFieldErrors({});

    try {
      const result = await login(username, password);
      
      if (!result.success) {
        // Handle server validation errors
        if (result.error && typeof result.error === 'object' && result.error.errors) {
          setFieldErrors(result.error.errors);
          setError('Please correct the errors below');
        } else {
          // Check if the error is related to email verification
          if (result.error && result.error.includes('verify your email')) {
            // Get the user's email for verification
            const emailResult = await getUserEmail(username);
            if (emailResult.success) {
              setUnverifiedEmail(emailResult.email);
              setShowEmailVerification(true);
            } else {
              // Fallback: show verification form without email (user will need to enter it)
              setUnverifiedEmail('');
              setShowEmailVerification(true);
            }
          } else {
            setError(result.error || 'Login failed. Please try again.');
          }
        }
      }
    } catch (err) {
      setError('An unexpected error occurred. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // Handle email verification success - user can now login
  const handleVerificationSuccess = () => {
    setShowEmailVerification(false);
    setUnverifiedEmail('');
    setError('');
    // Clear the form and show success message
    setUsername('');
    setPassword('');
    setError('Email verified successfully! You can now log in.');
  };

  // Handle back to login from verification
  const handleBackToLogin = () => {
    setShowEmailVerification(false);
    setUnverifiedEmail('');
  };

  // Show email verification form if user needs to verify email
  if (showEmailVerification) {
    return (
      <EmailVerificationForm
        email={unverifiedEmail}
        onVerificationSuccess={handleVerificationSuccess}
        onBackToSignup={handleBackToLogin}
      />
    );
  }

  return (
    <ErrorBoundary>
      <div className="auth-container">
        <div className="auth-form">
          <h2>Login</h2>
          {error && <div className="error">{error}</div>}
          
          <form onSubmit={handleSubmit} noValidate>
            <FormField
              id="username"
              label="Username"
              type="text"
              value={username}
              onChange={(e) => handleFieldChange('username', e.target.value)}
              onBlur={() => handleFieldBlur('username')}
              error={touched.username ? fieldErrors.username : ''}
              disabled={loading}
              required
              placeholder="Enter your username"
              autoComplete="username"
            />
            
            <FormField
              id="password"
              label="Password"
              type="password"
              value={password}
              onChange={(e) => handleFieldChange('password', e.target.value)}
              onBlur={() => handleFieldBlur('password')}
              error={touched.password ? fieldErrors.password : ''}
              disabled={loading}
              required
              placeholder="Enter your password"
              autoComplete="current-password"
            />
            
            <button type="submit" disabled={loading} className="auth-btn">
              {loading ? 'Logging in...' : 'Login'}
            </button>
          </form>
          
          <p className="auth-switch">
            Don't have an account?{' '}
            <button onClick={onSwitchToSignup} className="link-btn">
              Sign up here
            </button>
          </p>
        </div>
      </div>
    </ErrorBoundary>
  );
}

export default Login;