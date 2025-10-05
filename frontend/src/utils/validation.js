/**
 * Validation utilities for form inputs
 */

/**
 * Validates email format
 * @param {string} email - Email to validate
 * @returns {object} - { isValid: boolean, message: string }
 */
export const validateEmail = (email) => {
  if (!email || email.trim() === '') {
    return { isValid: false, message: 'Email is required' };
  }
  
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return { isValid: false, message: 'Please enter a valid email address' };
  }
  
  if (email.length > 50) {
    return { isValid: false, message: 'Email must not exceed 50 characters' };
  }
  
  return { isValid: true, message: '' };
};

/**
 * Validates username format
 * @param {string} username - Username to validate
 * @returns {object} - { isValid: boolean, message: string }
 */
export const validateUsername = (username) => {
  if (!username || username.trim() === '') {
    return { isValid: false, message: 'Username is required' };
  }
  
  if (username.length < 3) {
    return { isValid: false, message: 'Username must be at least 3 characters long' };
  }
  
  if (username.length > 20) {
    return { isValid: false, message: 'Username must not exceed 20 characters' };
  }
  
  const usernameRegex = /^[a-zA-Z0-9_]+$/;
  if (!usernameRegex.test(username)) {
    return { isValid: false, message: 'Username can only contain letters, numbers, and underscores' };
  }
  
  return { isValid: true, message: '' };
};

/**
 * Validates password format
 * @param {string} password - Password to validate
 * @returns {object} - { isValid: boolean, message: string }
 */
export const validatePassword = (password) => {
  if (!password || password.trim() === '') {
    return { isValid: false, message: 'Password is required' };
  }
  
  if (password.length < 8) {
    return { isValid: false, message: 'Password must be at least 8 characters long' };
  }
  
  if (password.length > 40) {
    return { isValid: false, message: 'Password must not exceed 40 characters' };
  }
  
  return { isValid: true, message: '' };
};

/**
 * Validates verification code format
 * @param {string} code - Verification code to validate
 * @returns {object} - { isValid: boolean, message: string }
 */
export const validateVerificationCode = (code) => {
  if (!code || code.trim() === '') {
    return { isValid: false, message: 'Verification code is required' };
  }
  
  const codeRegex = /^\d{6}$/;
  if (!codeRegex.test(code)) {
    return { isValid: false, message: 'Verification code must be exactly 6 digits' };
  }
  
  return { isValid: true, message: '' };
};

/**
 * Validates password confirmation
 * @param {string} password - Original password
 * @param {string} confirmPassword - Password confirmation
 * @returns {object} - { isValid: boolean, message: string }
 */
export const validatePasswordConfirmation = (password, confirmPassword) => {
  if (!confirmPassword || confirmPassword.trim() === '') {
    return { isValid: false, message: 'Password confirmation is required' };
  }
  
  if (password !== confirmPassword) {
    return { isValid: false, message: 'Passwords do not match' };
  }
  
  return { isValid: true, message: '' };
};

/**
 * Validates all signup form fields
 * @param {object} formData - Form data object
 * @returns {object} - { isValid: boolean, errors: object }
 */
export const validateSignupForm = (formData) => {
  const errors = {};
  let isValid = true;
  
  const usernameValidation = validateUsername(formData.username);
  if (!usernameValidation.isValid) {
    errors.username = usernameValidation.message;
    isValid = false;
  }
  
  const emailValidation = validateEmail(formData.email);
  if (!emailValidation.isValid) {
    errors.email = emailValidation.message;
    isValid = false;
  }
  
  const passwordValidation = validatePassword(formData.password);
  if (!passwordValidation.isValid) {
    errors.password = passwordValidation.message;
    isValid = false;
  }
  
  const confirmPasswordValidation = validatePasswordConfirmation(
    formData.password, 
    formData.confirmPassword
  );
  if (!confirmPasswordValidation.isValid) {
    errors.confirmPassword = confirmPasswordValidation.message;
    isValid = false;
  }
  
  return { isValid, errors };
};

/**
 * Validates login form fields
 * @param {object} formData - Form data object
 * @returns {object} - { isValid: boolean, errors: object }
 */
export const validateLoginForm = (formData) => {
  const errors = {};
  let isValid = true;
  
  if (!formData.username || formData.username.trim() === '') {
    errors.username = 'Username is required';
    isValid = false;
  }
  
  if (!formData.password || formData.password.trim() === '') {
    errors.password = 'Password is required';
    isValid = false;
  }
  
  return { isValid, errors };
};

/**
 * Validates email verification form fields
 * @param {object} formData - Form data object
 * @returns {object} - { isValid: boolean, errors: object }
 */
export const validateEmailVerificationForm = (formData) => {
  const errors = {};
  let isValid = true;
  
  const emailValidation = validateEmail(formData.email);
  if (!emailValidation.isValid) {
    errors.email = emailValidation.message;
    isValid = false;
  }
  
  if (formData.code !== undefined) {
    const codeValidation = validateVerificationCode(formData.code);
    if (!codeValidation.isValid) {
      errors.code = codeValidation.message;
      isValid = false;
    }
  }
  
  return { isValid, errors };
};