import React from 'react';
import { render, screen } from '@testing-library/react';
import PasswordStrengthIndicator from './PasswordStrengthIndicator';

describe('PasswordStrengthIndicator', () => {
  test('renders password strength indicator', () => {
    render(<PasswordStrengthIndicator password="" />);
    expect(screen.getByText('Password Strength:')).toBeInTheDocument();
  });

  test('shows WEAK for empty password', () => {
    render(<PasswordStrengthIndicator password="" />);
    expect(screen.getByText('WEAK')).toBeInTheDocument();
  });

  test('shows WEAK for password less than 8 characters', () => {
    render(<PasswordStrengthIndicator password="Test1!" />);
    expect(screen.getByText('WEAK')).toBeInTheDocument();
  });

  test('shows MEDIUM for password with lowercase, uppercase, and numbers', () => {
    render(<PasswordStrengthIndicator password="Password123" />);
    expect(screen.getByText('MEDIUM')).toBeInTheDocument();
  });

  test('shows STRONG for password with all requirements and 12+ characters', () => {
    render(<PasswordStrengthIndicator password="StrongPassword123!" />);
    expect(screen.getByText('STRONG')).toBeInTheDocument();
  });

  test('displays requirements checklist', () => {
    render(<PasswordStrengthIndicator password="Test123!" />);
    expect(screen.getByText('✓ At least 8 characters')).toBeInTheDocument();
    expect(screen.getByText('✓ Contains lowercase letters')).toBeInTheDocument();
    expect(screen.getByText('✓ Contains uppercase letters')).toBeInTheDocument();
    expect(screen.getByText('✓ Contains numbers')).toBeInTheDocument();
    expect(screen.getByText('✓ Contains special characters')).toBeInTheDocument();
  });

  test('shows unmet requirements with ✗', () => {
    render(<PasswordStrengthIndicator password="password" />);
    expect(screen.getByText('✗ Contains uppercase letters')).toBeInTheDocument();
    expect(screen.getByText('✗ Contains numbers')).toBeInTheDocument();
    expect(screen.getByText('✗ Contains special characters')).toBeInTheDocument();
  });

  test('applies correct CSS classes for strength levels', () => {
    const { rerender } = render(<PasswordStrengthIndicator password="weak" />);
    expect(screen.getByText('WEAK')).toHaveClass('strength-weak');

    rerender(<PasswordStrengthIndicator password="Password123" />);
    expect(screen.getByText('MEDIUM')).toHaveClass('strength-medium');

    rerender(<PasswordStrengthIndicator password="StrongPassword123!" />);
    expect(screen.getByText('STRONG')).toHaveClass('strength-strong');
  });
});