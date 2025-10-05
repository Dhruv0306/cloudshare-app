import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import Signup from './Signup';

// Mock the AuthContext
const mockSignup = jest.fn();
jest.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    signup: mockSignup
  })
}));

describe('Signup Component', () => {
  const mockOnSwitchToLogin = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('renders signup form with all fields', () => {
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    expect(screen.getByRole('heading', { name: /sign up/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign up/i })).toBeInTheDocument();
  });

  test('disables submit button when fields are empty (weak password)', () => {
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    
    // Submit button should be disabled because empty password is weak
    expect(submitButton).toBeDisabled();
    
    // Clicking disabled button should not call signup
    fireEvent.click(submitButton);
    expect(mockSignup).not.toHaveBeenCalled();
  });

  test('shows error when passwords do not match', async () => {
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: 'Password123' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Password456' } });
    
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Passwords do not match')).toBeInTheDocument();
    });
    
    expect(mockSignup).not.toHaveBeenCalled();
  });

  test('disables submit button when password is too weak', () => {
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: '123' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: '123' } });
    
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    
    // Submit button should be disabled for weak password
    expect(submitButton).toBeDisabled();
    
    // Password strength should show WEAK
    expect(screen.getByText('WEAK')).toBeInTheDocument();
    
    // Clicking disabled button should not call signup
    fireEvent.click(submitButton);
    expect(mockSignup).not.toHaveBeenCalled();
  });

  test('calls signup with correct data on successful form submission', async () => {
    mockSignup.mockResolvedValue({ success: true });
    
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: 'Password123' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Password123' } });
    
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(mockSignup).toHaveBeenCalledWith('testuser', 'test@example.com', 'Password123');
    });
  });

  test('shows email verification form on successful signup', async () => {
    mockSignup.mockResolvedValue({ success: true });
    
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: 'Password123' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Password123' } });
    
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Verify Your Email')).toBeInTheDocument();
      expect(screen.getByText('test@example.com')).toBeInTheDocument();
      expect(screen.getByLabelText(/verification code/i)).toBeInTheDocument();
    });
  });

  test('shows error message on failed signup', async () => {
    mockSignup.mockResolvedValue({ success: false, error: 'Username already exists' });
    
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'test@example.com' } });
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: 'Password123' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Password123' } });
    
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Username already exists')).toBeInTheDocument();
    });
  });

  test('renders password strength indicator and disables submit for weak passwords', () => {
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    // Type a weak password
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: 'weak' } });
    
    // Check that password strength indicator is rendered
    expect(screen.getByText('Password Strength:')).toBeInTheDocument();
    expect(screen.getByText('WEAK')).toBeInTheDocument();
    
    // Check that submit button is disabled for weak password
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    expect(submitButton).toBeDisabled();
  });

  test('enables submit button for medium strength passwords', () => {
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    // Type a medium strength password
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: 'Password123' } });
    
    // Check that password strength shows MEDIUM
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
    
    // Check that submit button is enabled for medium password
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    expect(submitButton).not.toBeDisabled();
  });

  test('shows validation errors when form is submitted with medium password but missing fields', async () => {
    render(<Signup onSwitchToLogin={mockOnSwitchToLogin} />);
    
    // Only fill password field with medium strength password
    fireEvent.change(screen.getByLabelText(/^password/i), { target: { value: 'Password123' } });
    fireEvent.change(screen.getByLabelText(/confirm password/i), { target: { value: 'Password123' } });
    
    const submitButton = screen.getByRole('button', { name: /sign up/i });
    expect(submitButton).not.toBeDisabled(); // Should be enabled for medium password
    
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Please correct the errors below')).toBeInTheDocument();
    });
    
    expect(mockSignup).not.toHaveBeenCalled();
  });
});