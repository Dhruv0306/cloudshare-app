import React, { useState } from 'react';
import PasswordStrengthIndicator from './PasswordStrengthIndicator';

/**
 * Demo component to showcase PasswordStrengthIndicator functionality
 * This component demonstrates real-time password evaluation without server calls
 */
const PasswordStrengthDemo = () => {
  const [password, setPassword] = useState('');

  return (
    <div style={{ maxWidth: '400px', margin: '20px auto', padding: '20px' }}>
      <h3>Password Strength Indicator Demo</h3>
      <div className="form-group">
        <label htmlFor="demo-password">Enter a password to test:</label>
        <input
          id="demo-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Type your password here..."
          style={{
            width: '100%',
            padding: '12px',
            border: '1px solid #ddd',
            borderRadius: '4px',
            fontSize: '16px',
            boxSizing: 'border-box'
          }}
        />
      </div>
      
      {/* Real-time password strength evaluation */}
      <PasswordStrengthIndicator password={password} />
      
      <div style={{ marginTop: '20px', fontSize: '14px', color: '#666' }}>
        <h4>Test Examples:</h4>
        <ul>
          <li><strong>Weak:</strong> "pass" (less than 8 characters)</li>
          <li><strong>Medium:</strong> "Password123" (lowercase, uppercase, numbers)</li>
          <li><strong>Strong:</strong> "StrongPassword123!" (all requirements + 12+ chars)</li>
        </ul>
      </div>
    </div>
  );
};

export default PasswordStrengthDemo;