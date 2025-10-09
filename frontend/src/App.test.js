import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import axios from 'axios';

import App from './App';

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
      });
      expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
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
      });
      expect(screen.getByLabelText(/email/i)).toBeInTheDocument();

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
      });
      expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /upload file/i })).toBeInTheDocument();
    });

    test('shows login form when currentUser exists but token is missing', async () => {
      // Mock user without token
      mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
      mockAuthContext.token = null;

      render(<App />);

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
      });
      expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/password/i)).toBeInTheDocument();

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
      });
      expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/password/i)).toBeInTheDocument();

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
      });
      expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/password/i)).toBeInTheDocument();

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
      });
      expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();

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
      });

      await waitFor(() => {
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
        expect(screen.getByText('Error fetching files')).toBeInTheDocument();
      });
      expect(mockAuthContext.logout).not.toHaveBeenCalled();
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
      });
      expect(screen.getByRole('button', { name: /upload file/i })).toBeInTheDocument();
    });

    test('upload button is disabled initially', async () => {
      render(<App />);

      await waitFor(() => {
        const uploadButton = screen.getByRole('button', { name: /upload file/i });
        expect(uploadButton).toBeDisabled();
      });
    });

    test('enables upload button when file is selected', async () => {
      render(<App />);

      // Wait for initial render and check button is disabled
      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload file/i })).toBeDisabled();
      });

      // Get elements and simulate file selection
      const fileInput = document.getElementById('fileInput');
      const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
      fireEvent.change(fileInput, { target: { files: [file] } });

      // Check button is enabled after file selection
      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload file/i })).toBeEnabled();
      });
    });

    test('upload button is disabled when no file is selected', async () => {
      render(<App />);

      await waitFor(() => {
        const uploadButton = screen.getByRole('button', { name: /upload file/i });
        expect(uploadButton).toBeDisabled();
      });
    });

    test('handles successful file upload', async () => {
      axios.post.mockResolvedValue({ data: { success: true } });

      render(<App />);

      // Wait for components to render
      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload file/i })).toBeInTheDocument();
      });

      // Get elements and simulate file upload
      const fileInput = document.getElementById('fileInput');
      const uploadButton = screen.getByRole('button', { name: /upload file/i });

      // Select a file
      const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
      fireEvent.change(fileInput, { target: { files: [file] } });

      // Upload the file
      fireEvent.click(uploadButton);

      await waitFor(() => {
        expect(axios.post).toHaveBeenCalledWith('/api/files/upload', expect.any(FormData), {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
      });

      await waitFor(() => {
        expect(screen.getByText('File uploaded successfully!')).toBeInTheDocument();
      });
    });

    test('handles file upload error', async () => {
      axios.post.mockRejectedValue(new Error('Upload failed'));

      render(<App />);

      // Wait for components to render
      await waitFor(() => {
        expect(screen.getByRole('button', { name: /upload file/i })).toBeInTheDocument();
      });

      // Get elements and simulate file upload
      const fileInput = document.getElementById('fileInput');
      const uploadButton = screen.getByRole('button', { name: /upload file/i });

      // Select a file
      const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
      fireEvent.change(fileInput, { target: { files: [file] } });

      // Upload the file
      fireEvent.click(uploadButton);

      await waitFor(() => {
        expect(screen.getByText('Error uploading file')).toBeInTheDocument();
      });
    });

    test('shows uploading state during file upload', async () => {
      // Mock a delayed response
      axios.post.mockImplementation(() =>
        new Promise(resolve => setTimeout(() => resolve({ data: { success: true } }), 100))
      );

      render(<App />);

      await waitFor(() => {
        const fileInput = document.getElementById('fileInput');
        const uploadButton = screen.getByRole('button', { name: /upload file/i });

        // Select a file
        const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
        fireEvent.change(fileInput, { target: { files: [file] } });

        // Upload the file
        fireEvent.click(uploadButton);

        // Should show uploading state
        expect(screen.getByRole('button', { name: /uploading/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /uploading/i })).toBeDisabled();
      });
    });
  });

  describe('File Display and Operations', () => {
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
        expect(screen.getByTitle('Download file')).toBeInTheDocument();
        expect(screen.getByTitle('Delete file')).toBeInTheDocument();
      });
    });

    test('shows empty state when no files', async () => {
      axios.get.mockResolvedValue({ data: [] });

      render(<App />);

      await waitFor(() => {
        expect(screen.getByText('No files uploaded yet')).toBeInTheDocument();
      });
    });

    test('handles file download successfully', async () => {
      const mockFiles = [{
        id: 1,
        fileName: 'stored-file.txt',
        originalFileName: 'test.txt',
        fileSize: 1024,
        uploadTime: '2023-01-01T10:00:00'
      }];

      axios.get.mockResolvedValueOnce({ data: mockFiles }); // For initial fetch
      axios.get.mockResolvedValueOnce({ data: new Blob(['file content']) }); // For download

      render(<App />);

      await waitFor(() => {
        const downloadButton = screen.getByTitle('Download file');
        fireEvent.click(downloadButton);
      });

      await waitFor(() => {
        expect(axios.get).toHaveBeenCalledWith('/api/files/download/stored-file.txt', {
          responseType: 'blob'
        });
      });
    });

    test('handles file download error', async () => {
      const mockFiles = [{
        id: 1,
        fileName: 'stored-file.txt',
        originalFileName: 'test.txt',
        fileSize: 1024,
        uploadTime: '2023-01-01T10:00:00'
      }];

      axios.get.mockResolvedValueOnce({ data: mockFiles }); // For initial fetch
      axios.get.mockImplementation((url) => {
        if (url.includes('/download/')) {
          return Promise.reject(new Error('Download failed'));
        }
        return Promise.resolve({ data: mockFiles });
      });

      render(<App />);

      // Wait for files to load first
      await waitFor(() => {
        expect(screen.getByText('test.txt')).toBeInTheDocument();
      });

      // Click download button
      const downloadButton = screen.getByTitle('Download file');
      fireEvent.click(downloadButton);

      // Wait for error message to appear
      await waitFor(() => {
        expect(screen.getByText('Error downloading file')).toBeInTheDocument();
      }, { timeout: 5000 });
    });

    test('handles file deletion with confirmation', async () => {
      const mockFiles = [{
        id: 1,
        fileName: 'stored-file.txt',
        originalFileName: 'test.txt',
        fileSize: 1024,
        uploadTime: '2023-01-01T10:00:00'
      }];

      // Mock window.confirm to return true
      window.confirm = jest.fn(() => true);

      axios.get.mockResolvedValue({ data: mockFiles });
      axios.delete.mockResolvedValue({ data: { success: true } });

      render(<App />);

      await waitFor(() => {
        const deleteButton = screen.getByTitle('Delete file');
        fireEvent.click(deleteButton);
      });

      await waitFor(() => {
        expect(window.confirm).toHaveBeenCalledWith('Are you sure you want to delete this file?');
        expect(axios.delete).toHaveBeenCalledWith('/api/files/1');
        expect(screen.getByText('File deleted successfully!')).toBeInTheDocument();
      });
    });

    test('cancels file deletion when user declines confirmation', async () => {
      const mockFiles = [{
        id: 1,
        fileName: 'stored-file.txt',
        originalFileName: 'test.txt',
        fileSize: 1024,
        uploadTime: '2023-01-01T10:00:00'
      }];

      // Mock window.confirm to return false
      window.confirm = jest.fn(() => false);

      axios.get.mockResolvedValue({ data: mockFiles });

      render(<App />);

      await waitFor(() => {
        const deleteButton = screen.getByTitle('Delete file');
        fireEvent.click(deleteButton);
      });

      expect(window.confirm).toHaveBeenCalledWith('Are you sure you want to delete this file?');
      expect(axios.delete).not.toHaveBeenCalled();
    });

    test('handles file deletion error', async () => {
      const mockFiles = [{
        id: 1,
        fileName: 'stored-file.txt',
        originalFileName: 'test.txt',
        fileSize: 1024,
        uploadTime: '2023-01-01T10:00:00'
      }];

      window.confirm = jest.fn(() => true);

      axios.get.mockResolvedValue({ data: mockFiles });
      axios.delete.mockRejectedValue(new Error('Delete failed'));

      render(<App />);

      await waitFor(() => {
        const deleteButton = screen.getByTitle('Delete file');
        fireEvent.click(deleteButton);
      });

      await waitFor(() => {
        expect(screen.getByText('Error deleting file')).toBeInTheDocument();
      });
    });

    test('formats file sizes correctly', async () => {
      const mockFiles = [
        { id: 1, fileName: 'file1.txt', originalFileName: 'small.txt', fileSize: 512, uploadTime: '2023-01-01T10:00:00' },
        { id: 2, fileName: 'file2.txt', originalFileName: 'medium.txt', fileSize: 1536, uploadTime: '2023-01-01T10:00:00' },
        { id: 3, fileName: 'file3.txt', originalFileName: 'large.txt', fileSize: 1048576, uploadTime: '2023-01-01T10:00:00' }
      ];

      axios.get.mockResolvedValue({ data: mockFiles });

      render(<App />);

      await waitFor(() => {
        expect(screen.getByText(/512 Bytes/)).toBeInTheDocument();
        expect(screen.getByText(/1.5 KB/)).toBeInTheDocument();
        expect(screen.getByText(/1 MB/)).toBeInTheDocument();
      });
    });

    test('formats upload dates correctly', async () => {
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
        // Check that the date is formatted (exact format depends on locale)
        expect(screen.getByText(/Uploaded:/)).toBeInTheDocument();
      });
    });
  });
});


describe('User Interface and Interactions', () => {
  beforeEach(() => {
    mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
    mockAuthContext.token = 'valid-token';
  });

  test('displays user welcome message and logout button', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Welcome, testuser!')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /logout/i })).toBeInTheDocument();
    });
  });

  test('calls logout function when logout button is clicked', async () => {
    render(<App />);

    await waitFor(() => {
      const logoutButton = screen.getByRole('button', { name: /logout/i });
      fireEvent.click(logoutButton);
    });

    expect(mockAuthContext.logout).toHaveBeenCalled();
  });

  test('clears file input after successful upload', async () => {
    // Reset mocks to ensure clean state
    jest.clearAllMocks();
    axios.get.mockResolvedValue({ data: [] }); // For initial file fetch
    axios.post.mockResolvedValue({ data: { success: true } });

    render(<App />);

    // Wait for component to load
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /upload file/i })).toBeInTheDocument();
    });

    const fileInput = document.getElementById('fileInput');
    const uploadButton = screen.getByRole('button', { name: /upload file/i });

    // Select a file
    const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [file] } });

    // Verify file is selected and button is enabled
    expect(uploadButton).toBeEnabled();

    // Upload the file
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith('/api/files/upload', expect.any(FormData), {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
    });

    await waitFor(() => {
      expect(screen.getByText('File uploaded successfully!')).toBeInTheDocument();
    });

    // File input should be cleared (button should be disabled again)
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /upload file/i })).toBeDisabled();
    });
  });

  test('shows loading state while fetching files', async () => {
    // Mock a delayed response
    axios.get.mockImplementation(() =>
      new Promise(resolve => setTimeout(() => resolve({ data: [] }), 100))
    );

    render(<App />);

    // Should show loading initially
    expect(screen.getByText('Loading files...')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('No files uploaded yet')).toBeInTheDocument();
    });
  });

  test('handles multiple files display correctly', async () => {
    const mockFiles = [
      { id: 1, fileName: 'file1.txt', originalFileName: 'document1.txt', fileSize: 1024, uploadTime: '2023-01-01T10:00:00' },
      { id: 2, fileName: 'file2.pdf', originalFileName: 'document2.pdf', fileSize: 2048, uploadTime: '2023-01-02T10:00:00' },
      { id: 3, fileName: 'file3.jpg', originalFileName: 'image.jpg', fileSize: 4096, uploadTime: '2023-01-03T10:00:00' }
    ];

    axios.get.mockResolvedValue({ data: mockFiles });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('document1.txt')).toBeInTheDocument();
      expect(screen.getByText('document2.pdf')).toBeInTheDocument();
      expect(screen.getByText('image.jpg')).toBeInTheDocument();

      // Should have 3 download buttons and 3 delete buttons
      expect(screen.getAllByTitle('Download file')).toHaveLength(3);
      expect(screen.getAllByTitle('Delete file')).toHaveLength(3);
    });
  });
});

describe('Error Handling and Edge Cases', () => {
  beforeEach(() => {
    mockAuthContext.currentUser = { id: 1, username: 'testuser', email: 'test@example.com' };
    mockAuthContext.token = 'valid-token';
  });

  test('handles zero byte file size formatting', async () => {
    const mockFiles = [{
      id: 1,
      fileName: 'empty.txt',
      originalFileName: 'empty.txt',
      fileSize: 0,
      uploadTime: '2023-01-01T10:00:00'
    }];

    axios.get.mockResolvedValue({ data: mockFiles });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/0 Bytes/)).toBeInTheDocument();
    });
  });

  test('handles very large file sizes', async () => {
    const mockFiles = [{
      id: 1,
      fileName: 'large.zip',
      originalFileName: 'large.zip',
      fileSize: 1073741824, // 1 GB
      uploadTime: '2023-01-01T10:00:00'
    }];

    axios.get.mockResolvedValue({ data: mockFiles });

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/1 GB/)).toBeInTheDocument();
    });
  });

  test('handles malformed date strings gracefully', async () => {
    const mockFiles = [{
      id: 1,
      fileName: 'test.txt',
      originalFileName: 'test.txt',
      fileSize: 1024,
      uploadTime: 'invalid-date'
    }];

    axios.get.mockResolvedValue({ data: mockFiles });

    render(<App />);

    await waitFor(() => {
      // Should still render the file even with invalid date
      expect(screen.getByText('test.txt')).toBeInTheDocument();
      expect(screen.getByText(/Uploaded:/)).toBeInTheDocument();
    });
  });

  test('clears previous messages when new operations are performed', async () => {
    // First show an error
    axios.get.mockRejectedValueOnce(new Error('Fetch failed'));

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText('Error fetching files')).toBeInTheDocument();
    });

    // Reset the mock for subsequent calls
    axios.get.mockResolvedValue({ data: [] });
    axios.post.mockResolvedValue({ data: { success: true } });

    const fileInput = document.getElementById('fileInput');
    const uploadButton = screen.getByRole('button', { name: /upload file/i });

    const file = new File(['test content'], 'test.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [file] } });
    fireEvent.click(uploadButton);

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith('/api/files/upload', expect.any(FormData), {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
    });

    await waitFor(() => {
      expect(screen.getByText('File uploaded successfully!')).toBeInTheDocument();
    });

    // Previous error message should be replaced
    expect(screen.queryByText('Error fetching files')).not.toBeInTheDocument();
  });
});

describe('Component Integration', () => {
  test('integrates properly with AuthContext', async () => {
    // Test that the component responds to auth context changes
    mockAuthContext.currentUser = null;
    mockAuthContext.token = null;

    const { rerender } = render(<App />);

    // Should show login form
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /login/i })).toBeInTheDocument();
    });

    // Update auth context to authenticated state
    mockAuthContext.currentUser = { id: 1, username: 'newuser', email: 'new@example.com' };
    mockAuthContext.token = 'new-token';

    rerender(<App />);

    // Should show file manager
    await waitFor(() => {
      expect(screen.getByText('Welcome, newuser!')).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /upload file/i })).toBeInTheDocument();
    });
  });

  test('maintains component state during re-renders', async () => {
    const mockFiles = [{
      id: 1,
      fileName: 'persistent.txt',
      originalFileName: 'persistent.txt',
      fileSize: 1024,
      uploadTime: '2023-01-01T10:00:00'
    }];

    axios.get.mockResolvedValue({ data: mockFiles });

    const { rerender } = render(<App />);

    await waitFor(() => {
      expect(screen.getByText('persistent.txt')).toBeInTheDocument();
    });

    // Re-render with same props
    rerender(<App />);

    // File should still be displayed
    expect(screen.getByText('persistent.txt')).toBeInTheDocument();
  });
});
