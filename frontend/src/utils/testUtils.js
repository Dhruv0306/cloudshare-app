import React from 'react';
import { render } from '@testing-library/react';
import { AuthProvider } from '../context/AuthContext';

// Custom render function that includes providers
export const renderWithAuth = (ui, options = {}) => {
  const { initialAuthState = {}, ...renderOptions } = options;

  const Wrapper = ({ children }) => {
    return (
      <AuthProvider {...initialAuthState}>
        {children}
      </AuthProvider>
    );
  };

  return render(ui, { wrapper: Wrapper, ...renderOptions });
};

// Mock file for testing file uploads
export const createMockFile = (name = 'test.txt', size = 1024, type = 'text/plain') => {
  const file = new File(['test content'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
};

// Mock axios response helper
export const createMockAxiosResponse = (data, status = 200) => ({
  data,
  status,
  statusText: 'OK',
  headers: {},
  config: {},
});

// Mock axios error helper
export const createMockAxiosError = (message = 'Network Error', status = 500) => {
  const error = new Error(message);
  error.response = {
    data: { message },
    status,
    statusText: 'Internal Server Error',
  };
  return error;
};

// Helper to wait for async operations
export const waitForAsync = () => new Promise(resolve => setTimeout(resolve, 0));

// Mock user data
export const mockUser = {
  id: 1,
  username: 'testuser',
  email: 'test@example.com',
};

// Mock file data
export const mockFiles = [
  {
    id: 1,
    originalFileName: 'document.pdf',
    fileName: 'uuid1_document.pdf',
    fileSize: 2048,
    uploadTime: '2023-01-01T12:00:00Z',
    contentType: 'application/pdf',
  },
  {
    id: 2,
    originalFileName: 'image.jpg',
    fileName: 'uuid2_image.jpg',
    fileSize: 1024000,
    uploadTime: '2023-01-02T12:00:00Z',
    contentType: 'image/jpeg',
  },
  {
    id: 3,
    originalFileName: 'text.txt',
    fileName: 'uuid3_text.txt',
    fileSize: 512,
    uploadTime: '2023-01-03T12:00:00Z',
    contentType: 'text/plain',
  },
];

// Helper to simulate user interactions
export const userInteractions = {
  fillLoginForm: (getByLabelText, username = 'testuser', password = 'password123') => {
    const usernameInput = getByLabelText(/username/i);
    const passwordInput = getByLabelText(/password/i);
    
    usernameInput.value = username;
    passwordInput.value = password;
    
    return { usernameInput, passwordInput };
  },

  fillSignupForm: (getByLabelText, userData = {}) => {
    const {
      username = 'newuser',
      email = 'new@example.com',
      password = 'password123',
      confirmPassword = 'password123'
    } = userData;

    const usernameInput = getByLabelText(/username/i);
    const emailInput = getByLabelText(/email/i);
    const passwordInput = getByLabelText(/^password/i);
    const confirmPasswordInput = getByLabelText(/confirm password/i);
    
    usernameInput.value = username;
    emailInput.value = email;
    passwordInput.value = password;
    confirmPasswordInput.value = confirmPassword;
    
    return { usernameInput, emailInput, passwordInput, confirmPasswordInput };
  },
};

export * from '@testing-library/react';