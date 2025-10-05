import React, { useState, useEffect } from 'react';
import './EmailVerificationForm.css';
import FormField from './FormField';
import ErrorBoundary from './ErrorBoundary';
import { validateEmailVerificationForm } from '../utils/validation';

const EmailVerificationForm = ({ 
  email, 
  onVerificationSuccess, 
  onBackToSignup 
}) => {
  const [userEmail, setUserEmail] = useState(email || '');
  const [verificationCode, setVerificationCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [resendLoading, setResendLoading] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [resendError, setResendError] = useState('');
  const [emailConfirmed, setEmailConfirmed] = useState(!!email);
  const [fieldErrors, setFieldErrors] = useState({});
  const [touched, setTouched] = useState({});

  // Cooldown timer for resend functionality
  useEffect(() => {
    let timer;
    if (resendCooldown > 0) {
      timer = setTimeout(() => {
        setResendCooldown(resendCooldown - 1);
      }, 1000);
    }
    return () => clearTimeout(timer);
  }, [resendCooldown]);

  const handleFieldChange = (field, value) => {
    switch (field) {
      case 'email':
        setUserEmail(value);
        break;
      case 'code':
        // Only allow digits and limit to 6 characters
        const cleanValue = value.replace(/\D/g, '');
        if (cleanValue.length <= 6) {
          setVerificationCode(cleanValue);
        }
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

    // Clear general errors when user makes changes
    if (error) setError('');
    if (resendError) setResendError('');
  };

  const handleFieldBlur = (field) => {
    setTouched(prev => ({
      ...prev,
      [field]: true
    }));

    // Validate field on blur
    const formData = { 
      email: userEmail, 
      code: field === 'code' ? verificationCode : undefined 
    };
    const validation = validateEmailVerificationForm(formData);
    
    if (!validation.isValid && validation.errors[field]) {
      setFieldErrors(prev => ({
        ...prev,
        [field]: validation.errors[field]
      }));
    }
  };

  const handleVerificationSubmit = async (e) => {
    e.preventDefault();
    
    // Mark fields as touched
    setTouched({ email: true, code: true });

    // Validate form
    const formData = { email: userEmail, code: verificationCode };
    const validation = validateEmailVerificationForm(formData);
    
    if (!validation.isValid) {
      setFieldErrors(validation.errors);
      setError('Please correct the errors below');
      return;
    }

    setLoading(true);
    setError('');
    setSuccess('');
    setFieldErrors({});

    try {
      const response = await fetch('/api/auth/verify-email', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          email: userEmail,
          verificationCode: verificationCode.trim()
        }),
      });

      const data = await response.json();

      if (response.ok) {
        setSuccess('Email verified successfully! Redirecting to login...');
        setTimeout(() => {
          onVerificationSuccess();
        }, 2000);
      } else {
        // Handle server validation errors
        if (data.errors && typeof data.errors === 'object') {
          setFieldErrors(data.errors);
          setError('Please correct the errors below');
        } else {
          // Handle specific error cases based on requirements
          if (data.message?.includes('expired')) {
            setError('Verification code has expired. Please request a new one.');
          } else if (data.message?.includes('invalid')) {
            setError('Invalid verification code. Please check and try again.');
          } else if (data.message?.includes('already used')) {
            setError('This verification code has already been used. Please request a new one.');
          } else {
            setError(data.message || 'Verification failed. Please try again.');
          }
        }
      }
    } catch (error) {
      setError('Network error. Please check your connection and try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleResendCode = async () => {
    if (resendCooldown > 0) {
      return;
    }

    setResendLoading(true);
    setResendError('');
    setError('');

    try {
      const response = await fetch('/api/auth/resend-verification', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email: userEmail }),
      });

      const data = await response.json();

      if (response.ok) {
        setSuccess('New verification code sent to your email!');
        setVerificationCode(''); // Clear the input
        setResendCooldown(60); // 60 second cooldown
      } else {
        if (data.message?.includes('rate limit')) {
          setResendError('Too many requests. Please wait before requesting another code.');
          setResendCooldown(300); // 5 minute cooldown for rate limiting
        } else {
          setResendError(data.message || 'Failed to resend verification code.');
        }
      }
    } catch (error) {
      setResendError('Network error. Please check your connection and try again.');
    } finally {
      setResendLoading(false);
    }
  };

  const handleEmailConfirm = async (e) => {
    e.preventDefault();
    
    // Mark email as touched
    setTouched({ email: true });

    // Validate email
    const formData = { email: userEmail };
    const validation = validateEmailVerificationForm(formData);
    
    if (!validation.isValid) {
      setFieldErrors(validation.errors);
      setError('Please correct the errors below');
      return;
    }

    setLoading(true);
    setError('');
    setResendError('');
    setFieldErrors({});

    try {
      const response = await fetch('/api/auth/resend-verification', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email: userEmail }),
      });

      const data = await response.json();

      if (response.ok) {
        setSuccess('Verification code sent to your email!');
        setEmailConfirmed(true);
        setResendCooldown(60); // 60 second cooldown
      } else {
        // Handle server validation errors
        if (data.errors && typeof data.errors === 'object') {
          setFieldErrors(data.errors);
          setError('Please correct the errors below');
        } else {
          if (data.message?.includes('rate limit')) {
            setError('Too many requests. Please wait before requesting another code.');
            setResendCooldown(300); // 5 minute cooldown for rate limiting
          } else {
            setError(data.message || 'Failed to send verification code.');
          }
        }
      }
    } catch (error) {
      setError('Network error. Please check your connection and try again.');
    } finally {
      setLoading(false);
    }
  };

  // Remove this function as it's now handled by handleFieldChange

  return (
    <ErrorBoundary>
      <div className="auth-container">
        <div className="auth-form">
          <h2>Verify Your Email</h2>
          
          <div className="verification-info">
            {emailConfirmed ? (
              <>
                <p>We've sent a 6-digit verification code to:</p>
                <strong>{userEmail}</strong>
                <p>Please enter the code below to verify your account.</p>
              </>
            ) : (
              <>
                <p>Please enter your email address to receive a verification code:</p>
              </>
            )}
          </div>

          {error && <div className="error">{error}</div>}
          {success && <div className="success">{success}</div>}
          {resendError && <div className="error">{resendError}</div>}
          
          <form onSubmit={emailConfirmed ? handleVerificationSubmit : handleEmailConfirm} noValidate>
            {!emailConfirmed && (
              <FormField
                id="user-email"
                label="Email Address"
                type="email"
                value={userEmail}
                onChange={(e) => handleFieldChange('email', e.target.value)}
                onBlur={() => handleFieldBlur('email')}
                error={touched.email ? fieldErrors.email : ''}
                disabled={loading}
                required
                placeholder="Enter your email address"
                maxLength={50}
                autoComplete="email"
              />
            )}
            
            {emailConfirmed && (
              <FormField
                id="verification-code"
                label="Verification Code"
                type="text"
                value={verificationCode}
                onChange={(e) => handleFieldChange('code', e.target.value)}
                onBlur={() => handleFieldBlur('code')}
                error={touched.code ? fieldErrors.code : ''}
                disabled={loading}
                required
                placeholder="Enter 6-digit code"
                maxLength={6}
                autoComplete="one-time-code"
                className="verification-input"
              >
                <div className="input-hint">
                  {verificationCode.length}/6 digits
                </div>
              </FormField>
            )}
            
            <button 
              type="submit" 
              disabled={loading} 
              className="auth-btn"
            >
              {loading ? (emailConfirmed ? 'Verifying...' : 'Sending Code...') : 
               (emailConfirmed ? 'Verify Email' : 'Send Verification Code')}
            </button>
          </form>

        <div className="verification-actions">
          {emailConfirmed && (
            <div className="resend-section">
              <p>Didn't receive the code?</p>
              <button
                type="button"
                onClick={handleResendCode}
                disabled={resendLoading || resendCooldown > 0}
                className="link-btn resend-btn"
              >
                {resendLoading ? 'Sending...' : 
                 resendCooldown > 0 ? `Resend in ${resendCooldown}s` : 
                 'Resend Code'}
              </button>
              <button
                type="button"
                onClick={() => {
                  setEmailConfirmed(false);
                  setVerificationCode('');
                  setError('');
                  setSuccess('');
                }}
                className="link-btn"
                disabled={loading}
              >
                Change Email
              </button>
            </div>
          )}

          <div className="back-section">
            <button
              type="button"
              onClick={onBackToSignup}
              className="link-btn"
              disabled={loading}
            >
              ← Back to Sign Up
            </button>
          </div>
        </div>

        <div className="verification-tips">
          <h4>Tips:</h4>
          <ul>
            <li>Check your spam/junk folder if you don't see the email</li>
            <li>The code expires in 15 minutes</li>
            <li>Make sure you entered the correct email address</li>
          </ul>
        </div>
      </div>
    </div>
    </ErrorBoundary>
  );
};

export default EmailVerificationForm;