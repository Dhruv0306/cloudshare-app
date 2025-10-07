import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import axios from 'axios';

// Mock axios before importing App
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

jest.mock('./context/AuthContext', () => ({
  AuthProvider: ({ children }) => children,
  useAuth: () => mockAuthContext
}));

import App from './App';

// Mock localStorage
const localStorageMock = {
  getItem: jest.fn(),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  clear: jest.fn(),
};
global.localStorage = localStorageMock;

// Mock URL.createObjectURL and revokeObjectURL for file download tests
global.URL.createObjectURL = jest.fn(() => 'mocked-url');
global.URL.revokeObjectURL = jest.fn();

describe('App Component', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    localStorageMock.getItem.mockReturnValue(null);
    // Reset mock auth context
    mockAuthContext.currentUser = null;
    mockAuthContext.token = null;
    axios.get.mockResolvedValue({ data: [] });
  });

  describe('Authentication Flow', () => {
    test('renders login form when user is not authenticated', async () => {
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      });
    });

    test('switches between login and signup forms', async () => {
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });

      // Switch to signup
      const signupLink = screen.getByText('Sign up here');
      fireEvent.click(signupLink);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /sign up/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
      });

      // Switch back to login
      const loginLink = screen.getByText('Login here');
      fireEvent.click(loginLink);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
    });

    test('shows validation errors on empty form submission', async () => {
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });

      // Try to submit empty login form
      const loginButton = screen.getByRole('button', { name: /login/i });
      fireEvent.click(loginButton);

      await waitFor(() => {
        expect(screen.getByText('Please correct the errors below')).toBeInTheDocument();
      });
    });
  });

  describe('AuthWrapper Component - Enhanced Authentication Logic', () => {
    test('renders FileManager only when both token and currentUser exist', async () => {
      // Mock authenticated state with both token and user
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = 'valid-token';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
        expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: /upload file/i })).toBeInTheDocument();
      });
    });

    test('shows login form when currentUser exists but token is missing', async () => {
      // Mock user without token
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = null;
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      });
      
      // Should not show FileManager
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('shows login form when token exists but currentUser is missing', async () => {
      // Mock token without user
      mockAuthContext.currentUser = null;
      mockAuthContext.token = 'valid-token';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      });
      
      // Should not show FileManager
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('shows login form when both token and currentUser are missing', async () => {
      // Mock no authentication
      mockAuthContext.currentUser = null;
      mockAuthContext.token = null;
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
        expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
      });
      
      // Should not show FileManager
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('transitions from login to FileManager when authentication is complete', async () => {
      // Start with no authentication
      const { rerender } = render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      // Update to fully authenticated state
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = 'valid-token';
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('File Sharing App')).toBeInTheDocument();
        expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();
      });
      
      // Should not show login form anymore
      expect(screen.queryByRole('heading', { name: /login/i })).not.toBeInTheDocument();
    });

    test('handles partial authentication states correctly', async () => {
      // Test with empty string token (falsy but not null)
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = '';
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });

    test('handles undefined authentication states correctly', async () => {
      // Test with undefined values
      mockAuthContext.currentUser = undefined;
      mockAuthContext.token = undefined;
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      
      expect(screen.queryByText('File Sharing App')).not.toBeInTheDocument();
    });
  });

  describe('FileManager Component - File Operations with Enhanced Auth', () => {
    beforeEach(() => {
      // Ensure full authentication for FileManager tests
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = 'valid-token';
    });

    test('fetches files only when both user and token are available', async () => {
      const mockFiles = [
        {
          id: 1,
          fileName: 'test-file.txt',
          originalFileName: 'test.txt',
          fileSize: 1024,
          uploadTime: '2023-01-01T10:00:00'
        }
      ];
      axios.get.mockResolvedValue({ data: mockFiles });
      
      render(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledWith('/api/files');
        expect(screen.getByText('test.txt')).toBeInTheDocument();
      });
    });

    test('handles 401 error gracefully (logout handled by interceptor)', async () => {
      const unauthorizedError = {
        response: { status: 401 }
      };
      axios.get.mockRejectedValue(unauthorizedError);
      
      render(<App />);
      
      await waitFor(() => {
        // The 401 error should be handled by the axios interceptor in AuthContext
        // The component should show a generic error message
        expect(screen.getByText('Error fetching files')).toBeInTheDocument();
      });
      
      // Note: The actual logout is handled by the axios interceptor in AuthContext
      // which is mocked separately and not directly testable in this component test
    });

    test('handles non-401 errors without logging out', async () => {
      const serverError = {
        response: { status: 500 }
      };
      axios.get.mockRejectedValue(serverError);
      
      render(<App />);
      
      await waitFor(() => {
        expect(mockAuthContext.logout).not.toHaveBeenCalled();
        expect(screen.getByText('Error fetching files')).toBeInTheDocument();
      });
    });

    test('ensures files array is always set even with null response', async () => {
      axios.get.mockResolvedValue({ data: null });
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('No files uploaded yet')).toBeInTheDocument();
      });
    });

    test('refetches files when authentication state changes', async () => {
      // Start with partial authentication (should not fetch)
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = null;
      
      const { rerender } = render(<App />);
      
      expect(axios.get).not.toHaveBeenCalledWith('/api/files');
      
      // Complete authentication (should fetch)
      mockAuthContext.token = 'valid-token';
      
      rerender(<App />);
      
      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledWith('/api/files');
      });
    });
  });

  describe('File Upload Functionality', () => {
    beforeEach(() => {
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = 'valid-token';
    });

    test('shows upload section when authenticated', async () => {
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /upload file/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /upload file/i })).toBeInTheDocument();
      });
    });

    test('upload button is disabled initially', async () => {
      render(<App />);
      
      await waitFor(() => {
        const uploadButton = screen.getByRole('button', { name: /upload file/i });
        expect(uploadButton).toBeDisabled();
      });
    });
  });

  describe('File Display', () => {
    beforeEach(() => {
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = 'valid-token';
    });

    test('displays files when available', async () => {
      const mockFiles = [{
        id: 1,
        fileName: 'stored-file.txt',
        originalFileName: 'test.txt',
        fileSize: 1024,
        uploadTime: '2023-01-01T10:00:00'
      }];
      
      axios.get.mockResolvedValue({ data: mockFiles });
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('test.txt')).toBeInTheDocument();
        expect(screen.getByText(/1 KB/)).toBeInTheDocument();
      });
    });

    test('shows empty state when no files', async () => {
      axios.get.mockResolvedValue({ data: [] });
      
      render(<App />);
      
      await waitFor(() => {
        expect(screen.getByText('No files uploaded yet')).toBeInTheDocument();
      });
    });
  });
});

