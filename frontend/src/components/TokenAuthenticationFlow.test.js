import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import axios from 'axios';

import App from '../App';

// Mock axios
jest.mock('axios', () => ({
  get: jest.fn(),
  post: jest.fn(),
  delete: jest.fn(),
  defaults: {
    headers: {
      common: {}
    }
  }
}));

// Mock AuthContext with different scenarios
const createMockAuthContext = (currentUser = null, token = null) => ({
  currentUser,
  token,
  login: jest.fn(),
  logout: jest.fn(),
  signup: jest.fn(),
  verifyEmail: jest.fn(),
  resendVerificationCode: jest.fn(),
  getUserEmail: jest.fn()
});

let mockAuthContext = createMockAuthContext();

jest.mock('../context/AuthContext', () => ({
  AuthProvider: ({ children }) => children,
  useAuth: () => mockAuthContext
}));

describe('Token-based Authentication Flow', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    axios.get.mockResolvedValue({ data: [] });
  });

  describe('useEffect dependency array behavior', () => {
    test('calls fetchFiles when both currentUser and token are provided initially', async () => {
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      render(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledWith('/api/files');
      });
    });

    test('does not call fetchFiles when currentUser is null', async () => {
      mockAuthContext = createMockAuthContext(null, 'valid-token');
      
      render(<App />);
      
      // Wait a bit to ensure useEffect has run
      await new Promise(resolve => setTimeout(resolve, 100));
      
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
    });

    test('does not call fetchFiles when token is null', async () => {
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        null
      );
      
      render(<App />);
      
      // Wait a bit to ensure useEffect has run
      await new Promise(resolve => setTimeout(resolve, 100));
      
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
    });

    test('does not call fetchFiles when both currentUser and token are null', async () => {
      mockAuthContext = createMockAuthContext(null, null);
      
      render(<App />);
      
      // Wait a bit to ensure useEffect has run
      await new Promise(resolve => setTimeout(resolve, 100));
      
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
    });
  });

  describe('useEffect re-execution on dependency changes', () => {
    test('re-executes useEffect when currentUser changes from null to valid user', async () => {
      // Start with no user
      mockAuthContext = createMockAuthContext(null, 'valid-token');
      
      const { rerender } = render(<App />);
      
      // Verify no initial call
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
      
      // Update to have user
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledWith('/api/files');
      });
    });

    test('re-executes useEffect when token changes from null to valid token', async () => {
      // Start with no token
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        null
      );
      
      const { rerender } = render(<App />);
      
      // Verify no initial call
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
      
      // Update to have token
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledWith('/api/files');
      });
    });

    test('re-executes useEffect when currentUser changes to different user', async () => {
      // Start with first user
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'user1', email: 'user1@example.com' },
        'token1'
      );
      
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledTimes(1);
      });
      
      // Change to different user
      mockAuthContext = createMockAuthContext(
        { id: 2, username: 'user2', email: 'user2@example.com' },
        'token1'
      );
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledTimes(2);
      });
    });

    test('re-executes useEffect when token changes to different token', async () => {
      // Start with first token
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'token1'
      );
      
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledTimes(1);
      });
      
      // Change to different token
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'token2'
      );
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledTimes(2);
      });
    });

    test('stops calling fetchFiles when user logs out (both become null)', async () => {
      // Start authenticated
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledTimes(1);
      });
      
      // Simulate logout
      mockAuthContext = createMockAuthContext(null, null);
      
      rerender(<App />);
      
      // Should not make additional calls after logout
      await new Promise(resolve => setTimeout(resolve, 100));
      expect(axios.get).toHaveBeenCalledTimes(1);
    });
  });

  describe('Error handling with authentication', () => {
    test('handles fetch error gracefully when authenticated', async () => {
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      axios.get.mockRejectedValue(new Error('Network error'));
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('Error fetching files')).toBeInTheDocument();
      });
    });

    test('ensures files array is always an array even with null response', async () => {
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      axios.get.mockResolvedValue({ data: null });
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('No files uploaded yet')).toBeInTheDocument();
      });
    });

    test('ensures files array is always an array even with undefined response', async () => {
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      axios.get.mockResolvedValue({ data: undefined });
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('No files uploaded yet')).toBeInTheDocument();
      });
    });
  });

  describe('Authentication state transitions', () => {
    test('transitions from login screen to file manager when authentication is complete', async () => {
      // Start unauthenticated
      mockAuthContext = createMockAuthContext(null, null);
      
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      // Simulate successful authentication
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
      });
      expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();
      expect(axios.get).toHaveBeenCalledWith('/api/files');
    });

    test('transitions from file manager to login screen when user logs out', async () => {
      // Start authenticated
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        'valid-token'
      );
      
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
      });
      
      // Simulate logout
      mockAuthContext = createMockAuthContext(null, null);
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
    });
  });

  describe('Token validation scenarios', () => {
    test('handles expired token scenario (user exists but token is null)', async () => {
      mockAuthContext = createMockAuthContext(
        { id: 1, username: 'testuser', email: 'test@example.com' },
        null // Token expired/removed
      );
      
      render(<App />);
      
      // Should show login screen since token is missing
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      // Should not attempt to fetch files
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
    });

    test('handles invalid user scenario (token exists but user is null)', async () => {
      mockAuthContext = createMockAuthContext(
        null, // User cleared/invalid
        'valid-token'
      );
      
      render(<App />);
      
      // With enhanced authentication logic, both token AND currentUser are required
      // Should show login screen since currentUser is null
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      // Should not attempt to fetch files since currentUser is null
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
    });
  });
});