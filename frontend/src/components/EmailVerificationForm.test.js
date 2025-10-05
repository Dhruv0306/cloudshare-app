import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import EmailVerificationForm from './EmailVerificationForm';

// Mock fetch
global.fetch = jest.fn();

describe('EmailVerificationForm', () => {
  const mockProps = {
    email: 'test@example.com',
    onVerificationSuccess: jest.fn(),
    onBackToSignup: jest.fn(),
  };

  beforeEach(() => {
    fetch.mockClear();
    mockProps.onVerificationSuccess.mockClear();
    mockProps.onBackToSignup.mockClear();
  });

  afterEach(() => {
    jest.clearAllTimers();
  });

  test('renders email verification form with correct email', () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    expect(screen.getByText('Verify Your Email')).toBeInTheDocument();
    expect(screen.getByText('test@example.com')).toBeInTheDocument();
    expect(screen.getByLabelText(/verification code/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verify Email' })).toBeInTheDocument();
  });

  test('only allows numeric input and limits to 6 digits', () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    const input = screen.getByLabelText(/verification code/i);
    
    // Test numeric input
    fireEvent.change(input, { target: { value: '123abc456' } });
    expect(input.value).toBe('123456');
    
    // Test length limit
    fireEvent.change(input, { target: { value: '1234567890' } });
    expect(input.value).toBe('123456');
  });

  test('shows validation error for empty code', async () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Verification code is required')).toBeInTheDocument();
    });
  });

  test('shows validation error for incomplete code', async () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    const input = screen.getByLabelText(/verification code/i);
    fireEvent.change(input, { target: { value: '123' } });
    
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Verification code must be exactly 6 digits')).toBeInTheDocument();
    });
  });

  test('submit button is enabled when not loading', () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    
    expect(submitButton).not.toBeDisabled();
  });

  test('handles successful verification', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: 'Email verified successfully' }),
    });

    render(<EmailVerificationForm {...mockProps} />);
    
    const input = screen.getByLabelText(/verification code/i);
    fireEvent.change(input, { target: { value: '123456' } });
    
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    fireEvent.click(submitButton);
    
    expect(screen.getByText('Verifying...')).toBeInTheDocument();
    
    await waitFor(() => {
      expect(screen.getByText('Email verified successfully! Redirecting to login...')).toBeInTheDocument();
    });

    // Test that success callback is called after delay
    await waitFor(() => {
      expect(mockProps.onVerificationSuccess).toHaveBeenCalled();
    }, { timeout: 3000 });
  });

  test('handles verification errors', async () => {
    fetch.mockResolvedValueOnce({
      ok: false,
      json: async () => ({ message: 'Invalid verification code' }),
    });

    render(<EmailVerificationForm {...mockProps} />);
    
    const input = screen.getByLabelText(/verification code/i);
    fireEvent.change(input, { target: { value: '123456' } });
    
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Invalid verification code')).toBeInTheDocument();
    });
  });

  test('handles expired code error', async () => {
    fetch.mockResolvedValueOnce({
      ok: false,
      json: async () => ({ message: 'Verification code has expired' }),
    });

    render(<EmailVerificationForm {...mockProps} />);
    
    const input = screen.getByLabelText(/verification code/i);
    fireEvent.change(input, { target: { value: '123456' } });
    
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Verification code has expired. Please request a new one.')).toBeInTheDocument();
    });
  });

  test('handles network errors', async () => {
    fetch.mockRejectedValueOnce(new Error('Network error'));

    render(<EmailVerificationForm {...mockProps} />);
    
    const input = screen.getByLabelText(/verification code/i);
    fireEvent.change(input, { target: { value: '123456' } });
    
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    fireEvent.click(submitButton);
    
    await waitFor(() => {
      expect(screen.getByText('Network error. Please check your connection and try again.')).toBeInTheDocument();
    });
  });

  test('handles resend code functionality', async () => {
    fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: 'Verification code sent' }),
    });

    render(<EmailVerificationForm {...mockProps} />);
    
    const resendButton = screen.getByRole('button', { name: 'Resend Code' });
    fireEvent.click(resendButton);
    
    expect(screen.getByText('Sending...')).toBeInTheDocument();
    
    await waitFor(() => {
      expect(screen.getByText('New verification code sent to your email!')).toBeInTheDocument();
    });

    // Check that cooldown is active
    expect(screen.getByText(/Resend in \d+s/)).toBeInTheDocument();
  });

  test('handles resend rate limiting', async () => {
    fetch.mockResolvedValueOnce({
      ok: false,
      json: async () => ({ message: 'Too many requests. Rate limit exceeded.' }),
    });

    render(<EmailVerificationForm {...mockProps} />);
    
    const resendButton = screen.getByRole('button', { name: 'Resend Code' });
    fireEvent.click(resendButton);
    
    await waitFor(() => {
      expect(screen.getByText('Too many requests. Rate limit exceeded.')).toBeInTheDocument();
    });
  });

  test('back to signup button works', () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    const backButton = screen.getByRole('button', { name: '← Back to Sign Up' });
    fireEvent.click(backButton);
    
    expect(mockProps.onBackToSignup).toHaveBeenCalled();
  });

  test('clears error when user starts typing', () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    // Trigger an error first
    const submitButton = screen.getByRole('button', { name: 'Verify Email' });
    fireEvent.click(submitButton);
    
    expect(screen.getByText('Verification code is required')).toBeInTheDocument();
    
    // Start typing to clear error
    const input = screen.getByLabelText(/verification code/i);
    fireEvent.change(input, { target: { value: '1' } });
    
    expect(screen.queryByText('Verification code is required')).not.toBeInTheDocument();
  });

  test('shows digit counter', () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    const input = screen.getByLabelText(/verification code/i);
    
    expect(screen.getByText('0/6 digits')).toBeInTheDocument();
    
    fireEvent.change(input, { target: { value: '123' } });
    expect(screen.getByText('3/6 digits')).toBeInTheDocument();
  });

  test('displays helpful tips', () => {
    render(<EmailVerificationForm {...mockProps} />);
    
    expect(screen.getByText('Tips:')).toBeInTheDocument();
    expect(screen.getByText('Check your spam/junk folder if you don\'t see the email')).toBeInTheDocument();
    expect(screen.getByText('The code expires in 15 minutes')).toBeInTheDocument();
    expect(screen.getByText('Make sure you entered the correct email address')).toBeInTheDocument();
  });
});