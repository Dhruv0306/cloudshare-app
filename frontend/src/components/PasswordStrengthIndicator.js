import React from 'react';
import './PasswordStrengthIndicator.css';

const PasswordStrengthIndicator = ({ password }) => {
  // Password strength evaluation logic
  const evaluatePasswordStrength = (password) => {
    if (!password) {
      return {
        level: 'WEAK',
        score: 0,
        requirements: [],
        color: '#dc3545'
      };
    }

    let score = 0;
    const requirements = [];

    // Length check (minimum 8 characters)
    if (password.length >= 8) {
      score += 25;
      requirements.push('✓ At least 8 characters');
    } else {
      requirements.push('✗ At least 8 characters');
    }

    // Lowercase check
    if (/[a-z]/.test(password)) {
      score += 15;
      requirements.push('✓ Contains lowercase letters');
    } else {
      requirements.push('✗ Contains lowercase letters');
    }

    // Uppercase check
    if (/[A-Z]/.test(password)) {
      score += 15;
      requirements.push('✓ Contains uppercase letters');
    } else {
      requirements.push('✗ Contains uppercase letters');
    }

    // Number check
    if (/\d/.test(password)) {
      score += 15;
      requirements.push('✓ Contains numbers');
    } else {
      requirements.push('✗ Contains numbers');
    }

    // Special character check
    if (/[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(password)) {
      score += 20;
      requirements.push('✓ Contains special characters');
    } else {
      requirements.push('✗ Contains special characters');
    }

    // Length bonus for 12+ characters
    if (password.length >= 12) {
      score += 10;
      requirements.push('✓ 12+ characters (bonus)');
    }

    // Determine strength level based on requirements from design document
    let level, color;
    
    if (password.length < 8) {
      // Requirement 2.2: Less than 8 characters = Weak
      level = 'WEAK';
      color = '#dc3545'; // Red
    } else if (/[a-z]/.test(password) && /[A-Z]/.test(password) && /\d/.test(password)) {
      if (/[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(password) && password.length >= 12) {
        // Requirement 2.4: lowercase, uppercase, numbers, special chars, 12+ length = Strong
        level = 'STRONG';
        color = '#28a745'; // Green
      } else {
        // Requirement 2.3: lowercase, uppercase, numbers = Medium
        level = 'MEDIUM';
        color = '#ffc107'; // Yellow
      }
    } else {
      level = 'WEAK';
      color = '#dc3545'; // Red
    }

    return {
      level,
      score: Math.min(score, 100),
      requirements,
      color
    };
  };

  const strength = evaluatePasswordStrength(password);

  return (
    <div className="password-strength-indicator">
      <div className="strength-header">
        <span className="strength-label">Password Strength:</span>
        <span 
          className={`strength-level strength-${strength.level.toLowerCase()}`}
          style={{ color: strength.color }}
        >
          {strength.level}
        </span>
      </div>
      
      <div className="strength-meter">
        <div className="strength-meter-track">
          <div 
            className="strength-meter-fill"
            style={{ 
              width: `${strength.score}%`,
              backgroundColor: strength.color
            }}
          />
        </div>
      </div>

      <div className="strength-requirements">
        {strength.requirements.map((requirement, index) => (
          <div 
            key={index} 
            className={`requirement ${requirement.startsWith('✓') ? 'met' : 'unmet'}`}
          >
            {requirement}
          </div>
        ))}
      </div>
    </div>
  );
};

export default PasswordStrengthIndicator;