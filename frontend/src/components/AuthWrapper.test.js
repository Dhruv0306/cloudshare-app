import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import axios from 'axios';

// Mock axios
jest.mock('axios', () => ({
  get: jest.fn(() => Promise.resolve({ data: [] })),
  post: jest.fn(() => Promise.resolve({ data: {} })),
  delete: jest.fn(() => Promise.resolve({ data: {} })),
  defaults: {
    headers: {
      common: {}
    }
  }
}));

// Mock AuthContext
const mockAuthContext = {
  currentUser: null,
  token: null,
  login: jest.fn(),
  logout: jest.fn(),
  signup: jest.fn(),
  verifyEmail: jest.fn(),
  resendVerificationCode: jest.fn(),
  getUserEmail: jest.fn()
};

jest.mock('../context/AuthContext', () => ({
  AuthProvider: ({ children }) => children,
  useAuth: () => mockAuthContext
}));

import App from '../App';

// Mock localStorage
const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  clear: jest.fn(),
};
global.localStorage = localStorageMock;

// Mock URL methods for file operations
global.URL.createObjectURL = jest.fn(() => 'mocked-url');
global.URL.revokeObjectURL = jest.fn();

describe('AuthWrapper Component', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorageMock.getItem.mockReturnValue(null);
    mockAuthContext.currentUser = null;
    mockAuthContext.token = null;
    axios.get.mockResolvedValue({ data: [] });
  });

  describe('Authentication State Management', () => {
    test('renders FileManager when both token and currentUser are present', async () => {
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = 'valid-jwt-token';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
        expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument();
      });
    });

    test('renders Login when token is missing but currentUser exists', async () => {
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = null;
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('renders Login when currentUser is missing but token exists', async () => {
      mockAuthContext.currentUser = null;
      mockAuthContext.token = 'valid-jwt-token';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('renders Login when both token and currentUser are missing', async () => {
      mockAuthContext.currentUser = null;
      mockAuthContext.token = null;
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });
  });

  describe('Edge Cases for Authentication States', () => {
    test('handles empty string token as falsy', async () => {
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = '';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('handles undefined token', async () => {
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = undefined;
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('handles empty object as currentUser', async () => {
      mockAuthContext.currentUser = {};
      mockAuthContext.token = 'valid-jwt-token';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
        // Should handle missing username gracefully
        expect(screen.getByText('Welcome, !')).toBeInTheDocument();
      });
    });

    test('handles currentUser with missing properties', async () => {
      mockAuthContext.currentUser = { id: 1 }; // Missing username and email
      mockAuthContext.token = 'valid-jwt-token';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
        expect(screen.getByText('Welcome, !')).toBeInTheDocument();
      });
    });
  });

  describe('Authentication State Transitions', () => {
    test('transitions from login to FileManager when authentication completes', async () => {
      // Start unauthenticated
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      // Complete authentication
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = 'valid-jwt-token';
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
        expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();
      });
      
      expect(screen.queryByRole('heading', { name: /login/i })).not.toBeInTheDocument();
    });

    test('transitions from FileManager to login when authentication is lost', async () => {
      // Start authenticated
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = 'valid-jwt-token';
      
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
      });
      
      // Lose authentication (token expires)
      mockAuthContext.token = null;
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('transitions from FileManager to login when user is cleared', async () => {
      // Start authenticated
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = 'valid-jwt-token';
      
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
      });
      
      // Clear user (logout)
      mockAuthContext.currentUser = null;
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });
  });

  describe('Component Behavior with Different Auth States', () => {
    test('does not attempt to fetch files when not fully authenticated', async () => {
      // Test various incomplete auth states
      const incompleteStates = [
        { currentUser: null, token: null },
        { currentUser: null, token: 'valid-token' },
        { currentUser: { id: 1, username: 'test' }, token: null },
        { currentUser: { id: 1, username: 'test' }, token: '' },
      ];

      for (const state of incompleteStates) {
        jest.clearAllMocks();
        mockAuthContext.currentUser = state.currentUser;
        mockAuthContext.token = state.token;
        
        render(<App />);
        
        // Should not call API when not fully authenticated
        expect(axios.get).not.toHaveBeenCalledWith('/api/files');
      }
    });

    test('fetches files only when fully authenticated', async () => {
      mockAuthContext.currentUser = { 
        id: 1, 
        username: 'testuser', 
        email: 'test@example.com' 
      };
      mockAuthContext.token = 'valid-jwt-token';
      
      render(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledWith('/api/files');
      });
    });
  });

  describe('Integration with Login/Signup Flow', () => {
    test('maintains login form state when switching between login and signup', async () => {
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      // Switch to signup
      const signupLink = screen.getByText('Sign up here');
      signupLink.click();
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /sign up/i })).toBeInTheDocument();
      });
      
      // Switch back to login
      const loginLink = screen.getByText('Login here');
      loginLink.click();
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
    });
  });
});