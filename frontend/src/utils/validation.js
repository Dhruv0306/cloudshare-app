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

/**
 * Validates multiple email addresses separated by commas
 * @param {string} emailList - Comma-separated email addresses
 * @returns {object} - { isValid: boolean, message: string, validEmails: array }
 */
export const validateEmailList = (emailList) => {
  if (!emailList || emailList.trim() === '') {
    return { isValid: false, message: 'Email addresses are required', validEmails: [] };
  }
  
  const emails = emailList.split(',').map(email => email.trim()).filter(email => email);
  const validEmails = [];
  const invalidEmails = [];
  
  if (emails.length === 0) {
    return { isValid: false, message: 'Please enter at least one email address', validEmails: [] };
  }
  
  if (emails.length > 10) {
    return { isValid: false, message: 'Maximum 10 email addresses allowed', validEmails: [] };
  }
  
  for (const email of emails) {
    const validation = validateEmail(email);
    if (validation.isValid) {
      validEmails.push(email);
    } else {
      invalidEmails.push(email);
    }
  }
  
  if (invalidEmails.length > 0) {
    return { 
      isValid: false, 
      message: `Invalid email addresses: ${invalidEmails.join(', ')}`, 
      validEmails 
    };
  }
  
  return { isValid: true, message: '', validEmails };
};

/**
 * Validates share expiration date
 * @param {string} expirationDate - ISO date string
 * @returns {object} - { isValid: boolean, message: string }
 */
export const validateShareExpiration = (expirationDate) => {
  if (!expirationDate) {
    return { isValid: false, message: 'Expiration date is required' };
  }
  
  const expDate = new Date(expirationDate);
  const now = new Date();
  
  if (isNaN(expDate.getTime())) {
    return { isValid: false, message: 'Invalid date format' };
  }
  
  if (expDate <= now) {
    return { isValid: false, message: 'Expiration date must be in the future' };
  }
  
  // Check if expiration is more than 1 year in the future
  const oneYearFromNow = new Date();
  oneYearFromNow.setFullYear(oneYearFromNow.getFullYear() + 1);
  
  if (expDate > oneYearFromNow) {
    return { isValid: false, message: 'Expiration date cannot be more than 1 year in the future' };
  }
  
  return { isValid: true, message: '' };
};

/**
 * Validates file sharing form data
 * @param {object} formData - Share form data
 * @returns {object} - { isValid: boolean, errors: object }
 */
export const validateShareForm = (formData) => {
  const errors = {};
  let isValid = true;
  
  // Validate permission
  if (!formData.permission || !['VIEW_ONLY', 'DOWNLOAD'].includes(formData.permission)) {
    errors.permission = 'Valid permission is required';
    isValid = false;
  }
  
  // Validate expiration if provided
  if (formData.expiresAt) {
    const expirationValidation = validateShareExpiration(formData.expiresAt);
    if (!expirationValidation.isValid) {
      errors.expiresAt = expirationValidation.message;
      isValid = false;
    }
  }
  
  // Validate email recipients if notification is enabled
  if (formData.sendNotification) {
    if (!formData.recipientEmails || formData.recipientEmails.length === 0) {
      errors.recipientEmails = 'Email recipients are required when sending notifications';
      isValid = false;
    } else {
      // Validate each email in the array
      const invalidEmails = [];
      for (const email of formData.recipientEmails) {
        const emailValidation = validateEmail(email);
        if (!emailValidation.isValid) {
          invalidEmails.push(email);
        }
      }
      
      if (invalidEmails.length > 0) {
        errors.recipientEmails = `Invalid email addresses: ${invalidEmails.join(', ')}`;
        isValid = false;
      }
    }
  }
  
  return { isValid, errors };
};