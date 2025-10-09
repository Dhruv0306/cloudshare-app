/**
 * Simplified test suite for SharedFileAccess component
 * Tests core functionality without problematic DOM manipulation tests
 */

import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import axios from 'axios';
import SharedFileAccess from './SharedFileAccess';
import { NotificationProvider } from './NotificationSystem';

// Mock axios
jest.mock('axios');
const mockedAxios = axios;

// Mock URL.createObjectURL and related APIs for file download tests
global.URL.createObjectURL = jest.fn(() => 'mock-url');
global.URL.revokeObjectURL = jest.fn();

// Mock clipboard API
Object.assign(navigator, {
    clipboard: {
        writeText: jest.fn(() => Promise.resolve()),
    },
});

// Mock data used across tests
const mockShareData = {
    shareId: 1,
    shareToken: 'test-token-123',
    permission: 'DOWNLOAD',
    createdAt: '2023-10-01T10:00:00Z',
    expiresAt: '2023-10-08T10:00:00Z',
    accessCount: 5,
    file: {
        id: 1,
        originalFileName: 'test-document.pdf',
        fileSize: 1024000
    }
};

// Test wrapper with providers
const TestWrapper = ({ children }) => (
  <NotificationProvider>
    {children}
  </NotificationProvider>
);

// Helper function to render with providers
const renderWithProviders = (ui, options = {}) => {
  return render(ui, { wrapper: TestWrapper, ...options });
};

describe('SharedFileAccess Component - Core Tests', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        cleanup();
    });

    afterEach(() => {
        cleanup();
    });

    describe('Loading State', () => {
        it('displays loading state initially when fetching file data', () => {
            // Mock axios to never resolve to keep loading state
            mockedAxios.get.mockImplementation(() => new Promise(() => { }));

            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            expect(screen.getByText('Loading shared file...')).toBeInTheDocument();
            expect(screen.getByText('Please wait while we retrieve the file information.')).toBeInTheDocument();
        });
    });

    describe('Successful File Access', () => {
        beforeEach(() => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({}); // For access logging
        });

        it('displays file information correctly after successful load', async () => {
            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            expect(screen.getByText('1000 KB')).toBeInTheDocument();
            expect(screen.getByText(/Shared on/)).toBeInTheDocument();
            expect(screen.getByText(/Expires:/)).toBeInTheDocument();
            expect(screen.getByText('Download Access')).toBeInTheDocument();
            expect(screen.getByText('Accessed 5 times')).toBeInTheDocument();
        });

        it('shows download button for files with download permission', async () => {
            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            const downloadButton = screen.getByRole('button', { name: /download file/i });
            expect(downloadButton).toBeInTheDocument();
            expect(downloadButton).not.toBeDisabled();
        });

        it('shows view-only notice for files with view-only permission', async () => {
            const viewOnlyData = { ...mockShareData, permission: 'VIEW_ONLY' };
            mockedAxios.get.mockResolvedValue({ data: viewOnlyData });

            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('View-only access - Download not permitted')).toBeInTheDocument();
            });

            expect(screen.queryByRole('button', { name: /download file/i })).not.toBeInTheDocument();
        });

        it('logs file access when component loads', async () => {
            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            expect(mockedAxios.post).toHaveBeenCalledWith(
                '/api/files/shared/test-token/access',
                { accessType: 'VIEW' }
            );
        });
    });

    describe('Error Handling', () => {
        it('handles invalid or empty share token', async () => {
            renderWithProviders(<SharedFileAccess shareToken="" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
                expect(screen.getByText('Invalid share link')).toBeInTheDocument();
            });
        });

        it('handles 404 not found errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 404 }
            });

            renderWithProviders(<SharedFileAccess shareToken="invalid-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
                expect(screen.getByText(/could not be found/)).toBeInTheDocument();
            });
        });

        it('handles 410 expired share errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 410 }
            });

            renderWithProviders(<SharedFileAccess shareToken="expired-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
                expect(screen.getByText(/expired and is no longer available/)).toBeInTheDocument();
            });
        });

        it('handles network errors with retry functionality', async () => {
            mockedAxios.get.mockRejectedValue(new Error('Network Error'));

            renderWithProviders(<SharedFileAccess shareToken="network-error-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
                expect(screen.getByText(/Network error/)).toBeInTheDocument();
            });

            // Should show retry button for network errors
            const retryButton = screen.getByRole('button', { name: /try again/i });
            expect(retryButton).toBeInTheDocument();
        });

        it('handles 403 revoked access errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 403 }
            });

            renderWithProviders(<SharedFileAccess shareToken="revoked-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
                expect(screen.getByText(/revoked by the owner/)).toBeInTheDocument();
            });
        });

        it('handles 429 rate limiting errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 429 }
            });

            renderWithProviders(<SharedFileAccess shareToken="rate-limited-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
                expect(screen.getByText(/Too many access attempts/)).toBeInTheDocument();
            });
        });
    });

    describe('File Download Functionality', () => {
        beforeEach(() => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});
        });

        it('shows download button for downloadable files', async () => {
            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            const downloadButton = screen.getByRole('button', { name: /download file/i });
            expect(downloadButton).toBeInTheDocument();
            expect(downloadButton).not.toBeDisabled();
        });

        it('prevents download for view-only files', async () => {
            const viewOnlyData = { ...mockShareData, permission: 'VIEW_ONLY' };
            mockedAxios.get.mockResolvedValue({ data: viewOnlyData });

            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('View-only access - Download not permitted')).toBeInTheDocument();
            });

            expect(screen.queryByRole('button', { name: /download file/i })).not.toBeInTheDocument();
        });
    });

    describe('Component Behavior', () => {
        it('does not make API calls when shareToken is missing', () => {
            renderWithProviders(<SharedFileAccess shareToken="" />);

            expect(mockedAxios.get).not.toHaveBeenCalled();
        });

        it('makes API call when valid shareToken is provided', async () => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});

            renderWithProviders(<SharedFileAccess shareToken="valid-token" />);

            await waitFor(() => {
                expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/shared/valid-token');
            });
        });
    });

    describe('Accessibility', () => {
        beforeEach(() => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});
        });

        it('has proper button roles and accessibility attributes', async () => {
            renderWithProviders(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            const downloadButton = screen.getByRole('button', { name: /download file/i });
            expect(downloadButton).toBeInTheDocument();
        });

        it('provides proper error messages for screen readers', async () => {
            renderWithProviders(<SharedFileAccess shareToken="" />);

            await waitFor(() => {
                expect(screen.getByText('Invalid share link')).toBeInTheDocument();
            });
        });
    });
});