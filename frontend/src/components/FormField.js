import React from 'react';
import './FormField.css';

/**
 * Reusable form field component with validation support
 */
const FormField = ({
  id,
  label,
  type = 'text',
  value,
  onChange,
  onBlur,
  error,
  disabled = false,
  required = false,
  placeholder,
  maxLength,
  autoComplete,
  className = '',
  children
}) => {
  const fieldId = id || `field-${label.toLowerCase().replace(/\s+/g, '-')}`;
  const hasError = error && error.trim() !== '';

  return (
    <div className={`form-field ${hasError ? 'has-error' : ''} ${className}`}>
      <label htmlFor={fieldId} className="form-label">
        {label}
        {required && <span className="required-indicator">*</span>}
      </label>
      
      <input
        id={fieldId}
        type={type}
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        disabled={disabled}
        required={required}
        placeholder={placeholder}
        maxLength={maxLength}
        autoComplete={autoComplete}
        className={`form-input ${hasError ? 'error' : ''}`}
        aria-invalid={hasError}
        aria-describedby={hasError ? `${fieldId}-error` : undefined}
      />
      
      {children}
      
      {hasError && (
        <div 
          id={`${fieldId}-error`}
          className="form-error"
          role="alert"
          aria-live="polite"
        >
          {error}
        </div>
      )}
    </div>
  );
};

export default FormField;