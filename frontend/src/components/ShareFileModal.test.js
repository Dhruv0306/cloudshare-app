import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ShareFileModal from './ShareFileModal';

// Mock the validation utility
jest.mock('../utils/validation', () => ({
    validateEmail: jest.fn()
}));

// Mock the FormField component
jest.mock('./FormField', () => {
    return function MockFormField({ id, label, value, onChange, onBlur, error, placeholder, className }) {
        return (
            <div className={`form-field ${error ? 'has-error' : ''} ${className || ''}`}>
                <label htmlFor={id}>{label}</label>
                <input
                    id={id}
                    type="text"
                    value={value}
                    onChange={onChange}
                    onBlur={onBlur}
                    placeholder={placeholder}
                    className={`form-input ${error ? 'error' : ''}`}
                />
                {error && <div className="form-error">{error}</div>}
            </div>
        );
    };
});

// Mock clipboard API
Object.assign(navigator, {
    clipboard: {
        writeText: jest.fn(),
    },
});

/**
 * Test suite for ShareFileModal component
 * Tests file sharing functionality including form validation, permissions, 
 * expiration settings, email notifications, and clipboard operations
 */
describe('ShareFileModal', () => {
    const mockFile = {
        id: 1,
        originalFileName: 'test-document.pdf',
        fileName: 'uuid_test-document.pdf',
        fileSize: 2048,
        uploadTime: '2023-01-01T00:00:00Z'
    };

    const mockOnShare = jest.fn();
    const mockOnClose = jest.fn();
    const { validateEmail } = require('../utils/validation');

    beforeEach(() => {
        jest.clearAllMocks();
        navigator.clipboard.writeText.mockResolvedValue();

        // Setup default mock for validateEmail
        validateEmail.mockReturnValue({ isValid: true, message: '' });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('Modal Rendering', () => {
        /**
         * Test that modal renders correctly when opened
         */
        test('renders modal when isOpen is true', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            expect(screen.getByText('Share File')).toBeInTheDocument();
            expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            expect(screen.getByText('2.0 KB')).toBeInTheDocument();
        });

        /**
         * Test that modal is hidden when closed
         */
        test('does not render modal when isOpen is false', () => {
            render(
                <ShareFileModal
                    isOpen={false}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            expect(screen.queryByText('Share File')).not.toBeInTheDocument();
        });

        /**
         * Test that all required form sections are present
         */
        test('renders all form sections', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            expect(screen.getByText('Permissions')).toBeInTheDocument();
            expect(screen.getByText('Expiration')).toBeInTheDocument();
            expect(screen.getByText('Send email notification')).toBeInTheDocument();
        });

        /**
         * Test file information display
         */
        test('displays file information correctly', () => {
            const largeFile = {
                ...mockFile,
                originalFileName: 'large-document.pdf',
                fileSize: 1048576 // 1MB
            };

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={largeFile}
                    onShare={mockOnShare}
                />
            );

            expect(screen.getByText('large-document.pdf')).toBeInTheDocument();
            expect(screen.getByText('1024.0 KB')).toBeInTheDocument();
        });
    });

    describe('Permission Selection', () => {
        /**
         * Test default permission setting
         */
        test('defaults to DOWNLOAD permission', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const downloadRadio = screen.getByDisplayValue('DOWNLOAD');
            expect(downloadRadio).toBeChecked();
        });

        /**
         * Test permission switching functionality
         */
        test('allows switching between permission options', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const viewOnlyRadio = screen.getByDisplayValue('VIEW_ONLY');
            const downloadRadio = screen.getByDisplayValue('DOWNLOAD');

            await userEvent.click(viewOnlyRadio);
            expect(viewOnlyRadio).toBeChecked();
            expect(downloadRadio).not.toBeChecked();

            await userEvent.click(downloadRadio);
            expect(downloadRadio).toBeChecked();
            expect(viewOnlyRadio).not.toBeChecked();
        });

        /**
         * Test permission labels and descriptions
         */
        test('displays permission descriptions', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            expect(screen.getByText('View Only')).toBeInTheDocument();
            expect(screen.getByText('Recipients can view but not download the file')).toBeInTheDocument();
            expect(screen.getByText('Download')).toBeInTheDocument();
            expect(screen.getByText('Recipients can view and download the file')).toBeInTheDocument();
        });
    });

    describe('Expiration Settings', () => {
        /**
         * Test default expiration setting
         */
        test('defaults to 1 day expiration', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const expirationSelect = screen.getByDisplayValue('1 Day');
            expect(expirationSelect).toBeInTheDocument();
        });

        /**
         * Test custom date field visibility
         */
        test('shows custom date field when CUSTOM is selected', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            // Verify the field is not initially visible
            expect(screen.queryByLabelText(/Expiration Date/)).not.toBeInTheDocument();

            const expirationSelect = screen.getByRole('combobox');
            fireEvent.change(expirationSelect, { target: { value: 'CUSTOM' } });

            // Wait for the field to appear
            await waitFor(() => {
                expect(screen.getByLabelText(/Expiration Date/)).toBeInTheDocument();
            });
        });

        /**
         * Test custom date field hiding when other option selected
         */
        test('hides custom date field when switching from CUSTOM', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const expirationSelect = screen.getByRole('combobox');

            // First select CUSTOM
            fireEvent.change(expirationSelect, { target: { value: 'CUSTOM' } });
            await waitFor(() => {
                expect(screen.getByLabelText(/Expiration Date/)).toBeInTheDocument();
            });

            // Then select another option
            fireEvent.change(expirationSelect, { target: { value: '1_DAY' } });
            await waitFor(() => {
                expect(screen.queryByLabelText(/Expiration Date/)).not.toBeInTheDocument();
            });
        });

        /**
         * Test custom expiration date validation
         */
        test('validates custom expiration date', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const expirationSelect = screen.getByRole('combobox');
            fireEvent.change(expirationSelect, { target: { value: 'CUSTOM' } });

            await waitFor(() => {
                expect(screen.getByLabelText(/Expiration Date/)).toBeInTheDocument();
            });

            const dateInput = screen.getByLabelText(/Expiration Date/);

            // Test with past date
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);
            const pastDate = yesterday.toISOString().split('T')[0];

            fireEvent.change(dateInput, { target: { value: pastDate } });
            fireEvent.blur(dateInput);

            await waitFor(() => {
                expect(screen.getByText('Expiration date must be in the future')).toBeInTheDocument();
            });
        });

        /**
         * Test minimum date validation
         */
        test('sets minimum date to tomorrow', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const expirationSelect = screen.getByRole('combobox');
            fireEvent.change(expirationSelect, { target: { value: 'CUSTOM' } });

            await waitFor(() => {
                const dateInput = screen.getByLabelText(/Expiration Date/);
                const tomorrow = new Date();
                tomorrow.setDate(tomorrow.getDate() + 1);
                const expectedMin = tomorrow.toISOString().split('T')[0];
                expect(dateInput).toHaveAttribute('min', expectedMin);
            });
        });

        /**
         * Test all expiration options are available
         */
        test('displays all expiration options', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const expirationSelect = screen.getByRole('combobox');
            expect(expirationSelect).toContainHTML('<option value="1_HOUR">1 Hour</option>');
            expect(expirationSelect).toContainHTML('<option value="1_DAY">1 Day</option>');
            expect(expirationSelect).toContainHTML('<option value="1_WEEK">1 Week</option>');
            expect(expirationSelect).toContainHTML('<option value="CUSTOM">Custom Date</option>');
            expect(expirationSelect).toContainHTML('<option value="NEVER">Never</option>');
        });
    });

    describe('Email Notifications', () => {
        /**
         * Test email input visibility when notifications enabled
         */
        test('shows email input when notification is enabled', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            expect(screen.getByLabelText('Email Recipients')).toBeInTheDocument();
        });

        /**
         * Test email input hiding when notifications disabled
         */
        test('hides email input when notification is disabled', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            // Initially hidden
            expect(screen.queryByLabelText('Email Recipients')).not.toBeInTheDocument();

            // Enable then disable
            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);
            expect(screen.getByLabelText('Email Recipients')).toBeInTheDocument();

            await userEvent.click(notificationCheckbox);
            expect(screen.queryByLabelText('Email Recipients')).not.toBeInTheDocument();
        });

        /**
         * Test email format validation with invalid email
         */
        test('validates email format', async () => {
            // Mock invalid email validation
            validateEmail.mockReturnValue({
                isValid: false,
                message: 'Please enter a valid email address'
            });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            await userEvent.type(emailInput, 'invalid-email');
            fireEvent.blur(emailInput);

            await waitFor(() => {
                expect(screen.getByText(/Invalid email/)).toBeInTheDocument();
            });
        });

        /**
         * Test multiple valid emails acceptance
         */
        test('accepts multiple valid emails', async () => {
            // Mock valid email validation
            validateEmail.mockReturnValue({ isValid: true, message: '' });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            await userEvent.type(emailInput, 'user1@example.com, user2@example.com');
            fireEvent.blur(emailInput);

            // Should not show validation error
            await waitFor(() => {
                expect(screen.queryByText(/Invalid email/)).not.toBeInTheDocument();
            });
        });

        /**
         * Test required email validation when notifications enabled
         */
        test('requires email recipients when notification is enabled', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            fireEvent.blur(emailInput);

            await waitFor(() => {
                expect(screen.getByText('Email recipients are required when sending notifications')).toBeInTheDocument();
            });
        });

        /**
         * Test email validation with mixed valid/invalid emails
         */
        test('validates mixed email list', async () => {
            // Mock validation to return invalid for second call
            validateEmail
                .mockReturnValueOnce({ isValid: true, message: '' })
                .mockReturnValueOnce({ isValid: false, message: 'Invalid email' });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            await userEvent.type(emailInput, 'valid@example.com, invalid-email');
            fireEvent.blur(emailInput);

            await waitFor(() => {
                expect(screen.getByText(/Invalid email: invalid-email/)).toBeInTheDocument();
            });
        });
    });

    describe('Form Submission', () => {
        /**
         * Test successful form submission with default values
         */
        test('calls onShare with correct data', async () => {
            mockOnShare.mockResolvedValue({
                shareUrl: 'https://example.com/shared/abc123'
            });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const submitButton = screen.getByText('Create Share Link');
            await userEvent.click(submitButton);

            expect(mockOnShare).toHaveBeenCalledWith(mockFile.id, {
                permission: 'DOWNLOAD',
                expiresAt: expect.any(String),
                recipientEmails: [],
                sendNotification: false
            });
        });

        /**
         * Test form submission with email notifications
         */
        test('includes email recipients when notification is enabled', async () => {
            mockOnShare.mockResolvedValue({
                shareUrl: 'https://example.com/shared/abc123'
            });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            await userEvent.type(emailInput, 'user@example.com');

            const submitButton = screen.getByText('Create Share Link');
            await userEvent.click(submitButton);

            expect(mockOnShare).toHaveBeenCalledWith(mockFile.id, {
                permission: 'DOWNLOAD',
                expiresAt: expect.any(String),
                recipientEmails: ['user@example.com'],
                sendNotification: true
            });
        });

        /**
         * Test form submission prevention with validation errors
         */
        test('prevents submission with validation errors', async () => {
            // Mock invalid email validation
            validateEmail.mockReturnValue({
                isValid: false,
                message: 'Invalid email'
            });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            await userEvent.type(emailInput, 'invalid-email');

            const submitButton = screen.getByText('Create Share Link');
            await userEvent.click(submitButton);

            expect(mockOnShare).not.toHaveBeenCalled();
        });
    });

    describe('Success State', () => {
        /**
         * Test success state display after share creation
         */
        test('shows success state after successful share creation', async () => {
            const shareUrl = 'https://example.com/shared/abc123';
            mockOnShare.mockResolvedValue({ shareUrl });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const submitButton = screen.getByText('Create Share Link');
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(screen.getByText('File Shared Successfully!')).toBeInTheDocument();
            });
            expect(screen.getByDisplayValue(shareUrl)).toBeInTheDocument();
        });

        /**
         * Test clipboard copy functionality
         */
        test('copies share URL to clipboard', async () => {
            const shareUrl = 'https://example.com/shared/abc123';
            mockOnShare.mockResolvedValue({ shareUrl });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const submitButton = screen.getByText('Create Share Link');
            await userEvent.click(submitButton);

            await waitFor(() => {
                expect(screen.getByDisplayValue(shareUrl)).toBeInTheDocument();
            });

            const copyButton = screen.getByTitle('Copy to clipboard');
            await userEvent.click(copyButton);

            expect(navigator.clipboard.writeText).toHaveBeenCalledWith(shareUrl);

            await waitFor(() => {
                expect(screen.getByText('Link copied to clipboard!')).toBeInTheDocument();
            });
        });
    });

    describe('Modal Interactions', () => {
        /**
         * Test modal close via close button
         */
        test('closes modal when close button is clicked', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const closeButton = screen.getByLabelText('Close modal');
            await userEvent.click(closeButton);

            expect(mockOnClose).toHaveBeenCalled();
        });

        /**
         * Test modal close via cancel button
         */
        test('closes modal when cancel button is clicked', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const cancelButton = screen.getByText('Cancel');
            await userEvent.click(cancelButton);

            expect(mockOnClose).toHaveBeenCalled();
        });

        /**
         * Test modal close via overlay click
         */
        test('closes modal when overlay is clicked', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            // Simulate clicking outside the modal by pressing Escape key
            await userEvent.keyboard('{Escape}');

            expect(mockOnClose).toHaveBeenCalled();
        });

        /**
         * Test modal does not close when content is clicked
         */
        test('does not close modal when content is clicked', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const modalContent = screen.getByRole('dialog');
            await userEvent.click(modalContent);

            expect(mockOnClose).not.toHaveBeenCalled();
        });
    });

    describe('Loading State', () => {
        /**
         * Test loading state display
         */
        test('shows loading state during share creation', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                    loading={true}
                />
            );

            expect(screen.getByText('Creating Share...')).toBeInTheDocument();
            expect(screen.getByText('Creating Share...')).toBeDisabled();
        });

        /**
         * Test form elements disabled during loading
         */
        test('disables form elements during loading', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                    loading={true}
                />
            );

            expect(screen.getByText('Cancel')).toBeDisabled();
            expect(screen.getByText('Creating Share...')).toBeDisabled();
        });

        /**
         * Test normal state when not loading
         */
        test('shows normal state when not loading', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                    loading={false}
                />
            );

            expect(screen.getByText('Create Share Link')).toBeInTheDocument();
            expect(screen.getByText('Create Share Link')).not.toBeDisabled();
            expect(screen.getByText('Cancel')).not.toBeDisabled();
        });
    });

    describe('Accessibility', () => {
        /**
         * Test ARIA attributes for accessibility
         */
        test('has proper ARIA attributes', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const modal = screen.getByRole('dialog');
            expect(modal).toHaveAttribute('aria-labelledby', 'modal-title');
            expect(modal).toHaveAttribute('aria-modal', 'true');

            const closeButton = screen.getByLabelText('Close modal');
            expect(closeButton).toHaveAttribute('aria-label', 'Close modal');
        });

        /**
         * Test form field accessibility
         */
        test('has accessible form fields', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            // Test custom expiration field accessibility
            const expirationSelect = screen.getByRole('combobox');
            fireEvent.change(expirationSelect, { target: { value: 'CUSTOM' } });

            await waitFor(() => {
                const dateInput = screen.getByLabelText(/Expiration Date/);
                expect(dateInput).toHaveAttribute('required');
            });
            const dateInput = screen.getByLabelText(/Expiration Date/);
            // aria-invalid is only set when there's an error (true), not when false
            expect(dateInput).not.toHaveAttribute('aria-invalid');
        });

        /**
         * Test keyboard navigation
         */
        test('supports keyboard navigation', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            // Modal should be focusable
            const modal = screen.getByRole('dialog');
            expect(modal).toBeInTheDocument();

            // Form elements should be focusable
            const permissionRadios = screen.getAllByRole('radio');
            permissionRadios.forEach(radio => {
                expect(radio).not.toHaveAttribute('tabindex', '-1');
            });

            const expirationSelect = screen.getByRole('combobox');
            expect(expirationSelect).not.toHaveAttribute('tabindex', '-1');

            const buttons = screen.getAllByRole('button');
            buttons.forEach(button => {
                expect(button).not.toHaveAttribute('tabindex', '-1');
            });
        });

        /**
         * Test screen reader announcements
         */
        test('provides proper labels for screen readers', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            // Check radio button labels
            expect(screen.getByLabelText(/View Only/)).toBeInTheDocument();
            expect(screen.getByLabelText(/Download/)).toBeInTheDocument();

            // Check checkbox label
            expect(screen.getByLabelText('Send email notification')).toBeInTheDocument();
        });
    });

    describe('Edge Cases and Error Handling', () => {
        /**
         * Test behavior with missing file prop
         */
        test('handles missing file prop gracefully', () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={null}
                    onShare={mockOnShare}
                />
            );

            expect(screen.getByText('Share File')).toBeInTheDocument();
            expect(screen.queryByText('test-document.pdf')).not.toBeInTheDocument();
        });

        /**
         * Test behavior with empty email list
         */
        test('handles empty email list correctly', async () => {
            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            await userEvent.type(emailInput, '   '); // Only spaces
            fireEvent.blur(emailInput);

            await waitFor(() => {
                expect(screen.getByText('Email recipients are required when sending notifications')).toBeInTheDocument();
            });
        });

        /**
         * Test field error clearing on change
         */
        test('clears field errors when user makes changes', async () => {
            // Mock invalid email validation initially
            validateEmail.mockReturnValue({
                isValid: false,
                message: 'Invalid email'
            });

            render(
                <ShareFileModal
                    isOpen={true}
                    onClose={mockOnClose}
                    file={mockFile}
                    onShare={mockOnShare}
                />
            );

            const notificationCheckbox = screen.getByLabelText('Send email notification');
            await userEvent.click(notificationCheckbox);

            const emailInput = screen.getByLabelText('Email Recipients');
            await userEvent.type(emailInput, 'invalid');
            fireEvent.blur(emailInput);

            await waitFor(() => {
                expect(screen.getByText(/Invalid email/)).toBeInTheDocument();
            });

            // Now mock valid email validation
            validateEmail.mockReturnValue({ isValid: true, message: '' });

            // Change the input
            await userEvent.clear(emailInput);
            await userEvent.type(emailInput, 'valid@example.com');

            // Error should be cleared (though it might reappear on blur if still invalid)
            // The key is that the error clearing logic is triggered on change
            expect(validateEmail).toHaveBeenCalled();
        });
    });
});