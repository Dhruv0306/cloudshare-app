import React, { useState } from 'react';
import SharedFileAccess from './SharedFileAccess';

/**
 * Demo component for testing SharedFileAccess component
 * This is for development/testing purposes only
 */
const SharedFileAccessDemo = () => {
  const [shareToken, setShareToken] = useState('demo-token-123');

  return (
    <div>
      <div style={{ padding: '20px', background: '#f0f0f0', marginBottom: '20px' }}>
        <h3>SharedFileAccess Demo</h3>
        <label>
          Share Token: 
          <input 
            type="text" 
            value={shareToken} 
            onChange={(e) => setShareToken(e.target.value)}
            style={{ marginLeft: '10px', padding: '5px', width: '200px' }}
          />
        </label>
        <p>Try different tokens to test error states:</p>
        <ul>
          <li><code>demo-token-123</code> - Valid token (will make API call)</li>
          <li><code>expired-token</code> - Test expired error</li>
          <li><code>invalid-token</code> - Test not found error</li>
          <li><code></code> (empty) - Test invalid token error</li>
        </ul>
      </div>
      
      <SharedFileAccess shareToken={shareToken} />
    </div>
  );
};

export default SharedFileAccessDemo;