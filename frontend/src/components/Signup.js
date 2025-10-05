import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import PasswordStrengthIndicator from './PasswordStrengthIndicator';
import EmailVerificationForm from './EmailVerificationForm';
import FormField from './FormField';
import ErrorBoundary from './ErrorBoundary';
import { validateSignupForm } from '../utils/validation';

function Signup({ onSwitchToLogin }) {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [showEmailVerification, setShowEmailVerification] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [touched, setTouched] = useState({});
  const { signup } = useAuth();

  // Helper function to evaluate password strength
  const evaluatePasswordStrength = (password) => {
    if (!password) return 'WEAK';
    
    // Based on requirements 2.2, 2.3, 2.4 from requirements.md
    if (password.length < 8) {
      return 'WEAK'; // Requirement 2.2: Less than 8 characters = Weak
    }
    
    const hasLower = /[a-z]/.test(password);
    const hasUpper = /[A-Z]/.test(password);
    const hasNumber = /\d/.test(password);
    const hasSpecial = /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password);
    
    if (hasLower && hasUpper && hasNumber) {
      if (hasSpecial && password.length >= 12) {
        return 'STRONG'; // Requirement 2.4: lowercase, uppercase, numbers, special chars, 12+ length = Strong
      } else {
        return 'MEDIUM'; // Requirement 2.3: lowercase, uppercase, numbers = Medium
      }
    }
    
    return 'WEAK';
  };

  const handleFieldChange = (field, value) => {
    // Update field value
    switch (field) {
      case 'username':
        setUsername(value);
        break;
      case 'email':
        setEmail(value);
        break;
      case 'password':
        setPassword(value);
        break;
      case 'confirmPassword':
        setConfirmPassword(value);
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
    const formData = { username, email, password, confirmPassword };
    const validation = validateSignupForm(formData);
    
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
      email: true,
      password: true,
      confirmPassword: true
    });

    // Validate all fields
    const formData = { username, email, password, confirmPassword };
    const validation = validateSignupForm(formData);
    
    if (!validation.isValid) {
      setFieldErrors(validation.errors);
      setError('Please correct the errors below');
      return;
    }

    // Requirement 2.5: Prevent form submission with weak passwords
    const passwordStrength = evaluatePasswordStrength(password);
    if (passwordStrength === 'WEAK') {
      setFieldErrors(prev => ({
        ...prev,
        password: 'Password is too weak. Please create a stronger password with at least 8 characters, including uppercase, lowercase, and numbers.'
      }));
      setError('Please correct the errors below');
      return;
    }

    setLoading(true);
    setError('');
    setSuccess('');
    setFieldErrors({});

    try {
      const result = await signup(username, email, password);
      
      if (result.success) {
        // Requirement 4.1: Display message indicating verification email was sent
        setRegisteredEmail(email);
        setShowEmailVerification(true);
        setSuccess('Account created successfully! Please check your email for verification.');
      } else {
        // Handle server validation errors
        if (result.error && typeof result.error === 'object' && result.error.errors) {
          setFieldErrors(result.error.errors);
          setError('Please correct the errors below');
        } else {
          setError(result.error || 'Registration failed. Please try again.');
        }
      }
    } catch (err) {
      setError('An unexpected error occurred. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // Handle email verification success - redirect to login
  const handleVerificationSuccess = () => {
    setShowEmailVerification(false);
    onSwitchToLogin();
  };

  // Handle back to signup from verification
  const handleBackToSignup = () => {
    setShowEmailVerification(false);
    setRegisteredEmail('');
    setSuccess('');
  };

  // Show email verification form if user just registered
  if (showEmailVerification) {
    return (
      <EmailVerificationForm
        email={registeredEmail}
        onVerificationSuccess={handleVerificationSuccess}
        onBackToSignup={handleBackToSignup}
      />
    );
  }

  return (
    <ErrorBoundary>
      <div className="auth-container">
        <div className="auth-form">
          <h2>Sign Up</h2>
          {error && <div className="error">{error}</div>}
          {success && <div className="success">{success}</div>}
          
          <form onSubmit={handleSubmit} noValidate>
            <FormField
              id="signup-username"
              label="Username"
              type="text"
              value={username}
              onChange={(e) => handleFieldChange('username', e.target.value)}
              onBlur={() => handleFieldBlur('username')}
              error={touched.username ? fieldErrors.username : ''}
              disabled={loading}
              required
              placeholder="Enter your username"
              maxLength={20}
              autoComplete="username"
            />
            
            <FormField
              id="signup-email"
              label="Email"
              type="email"
              value={email}
              onChange={(e) => handleFieldChange('email', e.target.value)}
              onBlur={() => handleFieldBlur('email')}
              error={touched.email ? fieldErrors.email : ''}
              disabled={loading}
              required
              placeholder="Enter your email address"
              maxLength={50}
              autoComplete="email"
            />
            
            <FormField
              id="signup-password"
              label="Password"
              type="password"
              value={password}
              onChange={(e) => handleFieldChange('password', e.target.value)}
              onBlur={() => handleFieldBlur('password')}
              error={touched.password ? fieldErrors.password : ''}
              disabled={loading}
              required
              placeholder="Enter your password"
              maxLength={40}
              autoComplete="new-password"
            >
              {/* Real-time password strength feedback as user types */}
              <PasswordStrengthIndicator password={password} />
            </FormField>
            
            <FormField
              id="signup-confirm-password"
              label="Confirm Password"
              type="password"
              value={confirmPassword}
              onChange={(e) => handleFieldChange('confirmPassword', e.target.value)}
              onBlur={() => handleFieldBlur('confirmPassword')}
              error={touched.confirmPassword ? fieldErrors.confirmPassword : ''}
              disabled={loading}
              required
              placeholder="Confirm your password"
              maxLength={40}
              autoComplete="new-password"
            />
            
            <button 
              type="submit" 
              disabled={loading || evaluatePasswordStrength(password) === 'WEAK'} 
              className="auth-btn"
            >
              {loading ? 'Creating Account...' : 'Sign Up'}
            </button>
          </form>
          
          <p className="auth-switch">
            Already have an account?{' '}
            <button onClick={onSwitchToLogin} className="link-btn">
              Login here
            </button>
          </p>
        </div>
      </div>
    </ErrorBoundary>
  );
}

export default Signup;