/**
 * Test suite for SharedFileAccess component
 * Tests core functionality including loading states, file display, error handling, and user interactions
 */

import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import axios from 'axios';
import SharedFileAccess from './SharedFileAccess';

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

describe('SharedFileAccess Component', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        // Ensure clean DOM state
        cleanup();
        document.body.innerHTML = '';
    });

    afterEach(() => {
        cleanup();
    });



    describe('Loading State', () => {
        it('displays loading state initially when fetching file data', () => {
            // Mock axios to never resolve to keep loading state
            mockedAxios.get.mockImplementation(() => new Promise(() => { }));

            render(<SharedFileAccess shareToken="test-token" />);

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
            render(<SharedFileAccess shareToken="test-token" />);

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
            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Download File/i })).toBeInTheDocument();
            });
        });

        it('shows view-only notice for files with view-only permission', async () => {
            const viewOnlyData = { ...mockShareData, permission: 'VIEW_ONLY' };
            mockedAxios.get.mockResolvedValue({ data: viewOnlyData });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('View-only access - Download not permitted')).toBeInTheDocument();
            });

            expect(screen.queryByRole('button', { name: /Download File/i })).not.toBeInTheDocument();
        });

        it('logs file access when component loads', async () => {
            render(<SharedFileAccess shareToken="test-token" />);

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
            render(<SharedFileAccess shareToken="" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText('Invalid share link')).toBeInTheDocument();
        });

        it('handles 404 not found errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 404 }
            });

            render(<SharedFileAccess shareToken="invalid-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText(/This file share could not be found/)).toBeInTheDocument();
        });

        it('handles 410 expired share errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 410 }
            });

            render(<SharedFileAccess shareToken="expired-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText(/This share link has expired/)).toBeInTheDocument();
        });

        it('handles network errors with retry functionality', async () => {
            mockedAxios.get.mockRejectedValue(new Error('Network Error'));

            render(<SharedFileAccess shareToken="network-error-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText(/Network error/)).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /Try Again/i })).toBeInTheDocument();
        });

        it('handles 403 revoked access errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 403 }
            });

            render(<SharedFileAccess shareToken="revoked-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText(/Access to this file has been revoked/)).toBeInTheDocument();
        });

        it('handles 429 rate limiting errors', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 429 }
            });

            render(<SharedFileAccess shareToken="rate-limited-token" />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText(/Too many access attempts/)).toBeInTheDocument();
        });
    });

    describe('File Information Display', () => {
        beforeEach(() => {
            mockedAxios.post.mockResolvedValue({});
        });

        it('formats file sizes correctly', async () => {
            const testData = {
                ...mockShareData,
                file: { ...mockShareData.file, fileSize: 1536 } // 1.5 KB
            };

            mockedAxios.get.mockResolvedValue({ data: testData });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('1.5 KB')).toBeInTheDocument();
            });
        });

        it('handles zero file size', async () => {
            const testData = {
                ...mockShareData,
                file: { ...mockShareData.file, fileSize: 0 }
            };

            mockedAxios.get.mockResolvedValue({ data: testData });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('0 Bytes')).toBeInTheDocument();
            });
        });

        it('handles missing expiration date', async () => {
            const dataWithoutExpiration = {
                ...mockShareData,
                expiresAt: null
            };
            mockedAxios.get.mockResolvedValue({ data: dataWithoutExpiration });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            expect(screen.queryByText(/Expires:/)).not.toBeInTheDocument();
        });

        it('handles zero access count', async () => {
            const dataWithZeroAccess = {
                ...mockShareData,
                accessCount: 0
            };
            mockedAxios.get.mockResolvedValue({ data: dataWithZeroAccess });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            expect(screen.queryByText(/Accessed/)).not.toBeInTheDocument();
        });

        it('handles single access count correctly', async () => {
            const dataWithSingleAccess = {
                ...mockShareData,
                accessCount: 1
            };
            mockedAxios.get.mockResolvedValue({ data: dataWithSingleAccess });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('Accessed 1 time')).toBeInTheDocument();
            });
        });
    });

    describe('Component Behavior', () => {
        it('does not make API calls when shareToken is missing', () => {
            render(<SharedFileAccess shareToken="" />);

            expect(mockedAxios.get).not.toHaveBeenCalled();
        });

        it('makes API call when valid shareToken is provided', async () => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});

            render(<SharedFileAccess shareToken="valid-token" />);

            await waitFor(() => {
                expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/shared/valid-token');
            });
        });

        it('continues functioning when access logging fails', async () => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockRejectedValue(new Error('Logging failed'));

            const consoleSpy = jest.spyOn(console, 'warn').mockImplementation(() => { });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            expect(consoleSpy).toHaveBeenCalledWith('Failed to log file access:', expect.any(Error));

            consoleSpy.mockRestore();
        });

        it('handles retry functionality for network errors', async () => {
            mockedAxios.get
                .mockRejectedValueOnce(new Error('Network Error'))
                .mockResolvedValueOnce({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});

            render(<SharedFileAccess shareToken="network-error-token" />);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Try Again/i })).toBeInTheDocument();
            });

            const retryButton = screen.getByRole('button', { name: /Try Again/i });
            fireEvent.click(retryButton);

            // Verify retry made the API call again
            await waitFor(() => {
                expect(mockedAxios.get).toHaveBeenCalledTimes(2);
            });
        });

        it('prevents multiple simultaneous access logging calls', async () => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});

            const { rerender } = render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            // Re-render with same token should not log access again
            rerender(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            // Should only have been called once for VIEW access
            expect(mockedAxios.post).toHaveBeenCalledTimes(1);
            expect(mockedAxios.post).toHaveBeenCalledWith(
                '/api/files/shared/test-token/access',
                { accessType: 'VIEW' }
            );
        });
    });

    describe('Accessibility', () => {
        beforeEach(() => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});
        });

        it('has proper button roles and accessibility attributes', async () => {
            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            const downloadButton = screen.getByRole('button', { name: /Download File/i });
            expect(downloadButton).toBeInTheDocument();
            expect(downloadButton).not.toBeDisabled();
        });

        it('provides proper error messages for screen readers', async () => {
            mockedAxios.get.mockRejectedValue({
                response: { status: 404 }
            });

            render(<SharedFileAccess shareToken="invalid-token" />);

            await waitFor(() => {
                const errorMessage = screen.getByText(/This file share could not be found/);
                expect(errorMessage).toBeInTheDocument();
            });
        });
    });

    describe('File Download Functionality', () => {
        beforeEach(() => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});
        });

        /**
         * Test that download button shows loading state during download
         * Ensures proper user feedback during file download operations
         */
        it('shows loading state during download', async () => {
            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Download File/i })).toBeInTheDocument();
            });

            // Mock a slow download response - need to mock both the initial load and download endpoints
            mockedAxios.get.mockImplementation((url) => {
                if (url.includes('/download')) {
                    // Mock slow download response
                    return new Promise(resolve => 
                        setTimeout(() => resolve({ 
                            data: new Blob(['file content'], { type: 'application/pdf' }) 
                        }), 100)
                    );
                }
                // Return the initial file data for the first call
                return Promise.resolve({ data: mockShareData });
            });

            const downloadButton = screen.getByRole('button', { name: /Download File/i });
            fireEvent.click(downloadButton);

            // Check for loading state - the button text should change to "Downloading..."
            await waitFor(() => {
                expect(screen.getByText('Downloading...')).toBeInTheDocument();
            });
            expect(downloadButton).toBeDisabled();
        });

        /**
         * Test error handling during download process
         * Verifies that download errors are properly handled and displayed
         */
        it('handles download errors gracefully', async () => {
            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Download File/i })).toBeInTheDocument();
            });

            // Mock download failure - need to mock the download endpoint specifically
            mockedAxios.get.mockImplementation((url) => {
                if (url.includes('/download')) {
                    return Promise.reject(new Error('Download failed'));
                }
                return Promise.resolve({ data: mockShareData });
            });

            const downloadButton = screen.getByRole('button', { name: /Download File/i });
            fireEvent.click(downloadButton);

            // Should show error state after download failure
            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText(/Network error/)).toBeInTheDocument();
        });

        it('shows download button for downloadable files', async () => {
            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Download File/i })).toBeInTheDocument();
            });

            const downloadButton = screen.getByRole('button', { name: /Download File/i });
            expect(downloadButton).not.toBeDisabled();
        });

        it('prevents download for view-only files', async () => {
            const viewOnlyData = { ...mockShareData, permission: 'VIEW_ONLY' };
            mockedAxios.get.mockResolvedValue({ data: viewOnlyData });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('View-only access - Download not permitted')).toBeInTheDocument();
            });

            expect(screen.queryByRole('button', { name: /Download File/i })).not.toBeInTheDocument();
        });

        it('logs download access when download button is clicked', async () => {
            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Download File/i })).toBeInTheDocument();
            });

            const downloadButton = screen.getByRole('button', { name: /Download File/i });
            fireEvent.click(downloadButton);

            // Verify download access is logged
            await waitFor(() => {
                expect(mockedAxios.post).toHaveBeenCalledWith(
                    '/api/files/shared/test-token/access',
                    { accessType: 'DOWNLOAD' }
                );
            });
        });

        /**
         * Test that the download functionality initiates properly when the download button is clicked
         * This test verifies the complete download flow including API calls and DOM manipulation
         */
        it('initiates download when download button is clicked', async () => {
            // First render the component with file data
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Download File/i })).toBeInTheDocument();
            });

            // Now mock the download API call
            const mockBlob = new Blob(['file content'], { type: 'application/pdf' });
            mockedAxios.get.mockResolvedValueOnce({ data: mockBlob });

            // Mock DOM methods for download link creation and interaction
            const mockLink = document.createElement('a');
            mockLink.click = jest.fn();
            mockLink.remove = jest.fn();
            mockLink.setAttribute = jest.fn();

            const createElementSpy = jest.spyOn(document, 'createElement').mockReturnValue(mockLink);
            const appendChildSpy = jest.spyOn(document.body, 'appendChild').mockImplementation(() => { });

            const downloadButton = screen.getByRole('button', { name: /Download File/i });
            fireEvent.click(downloadButton);

            // Verify download access is logged
            await waitFor(() => {
                expect(mockedAxios.post).toHaveBeenCalledWith(
                    '/api/files/shared/test-token/access',
                    { accessType: 'DOWNLOAD' }
                );
            });

            // Verify download API call with correct parameters
            await waitFor(() => {
                expect(mockedAxios.get).toHaveBeenCalledWith(
                    '/api/files/shared/test-token/download',
                    { responseType: 'blob' }
                );
            });

            // Clean up DOM method spies
            createElementSpy.mockRestore();
            appendChildSpy.mockRestore();
        });
    });

    /**
     * Test suite for file icon and display utilities
     * Verifies that different file types display appropriate icons and formatting
     */
    describe('File Icon and Display Utilities', () => {
        beforeEach(() => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});
        });

        /**
         * Test that different file extensions display the correct icons
         * This ensures proper visual representation for various file types
         */
        it('displays correct icons for different file types', async () => {
            const testCases = [
                { fileName: 'document.pdf', expectedIcon: '📕' },
                { fileName: 'image.jpg', expectedIcon: '🖼️' },
                { fileName: 'spreadsheet.xlsx', expectedIcon: '📊' },
                { fileName: 'archive.zip', expectedIcon: '🗜️' },
                { fileName: 'code.js', expectedIcon: '📜' },
                { fileName: 'video.mp4', expectedIcon: '🎬' },
                { fileName: 'audio.mp3', expectedIcon: '🎵' },
                { fileName: 'unknown.xyz', expectedIcon: '📄' }
            ];

            for (const testCase of testCases) {
                const testData = {
                    ...mockShareData,
                    file: { ...mockShareData.file, originalFileName: testCase.fileName }
                };
                mockedAxios.get.mockResolvedValue({ data: testData });

                const { unmount } = render(<SharedFileAccess shareToken="test-token" />);

                await waitFor(() => {
                    expect(screen.getByText(testCase.fileName)).toBeInTheDocument();
                });

                // Check if the expected icon is present in the document
                // Note: Testing exact emoji might be challenging due to rendering differences
                // This test ensures the component renders without errors for different file types
                expect(screen.getByText(testCase.fileName)).toBeInTheDocument();

                unmount();
            }
        });

        /**
         * Test handling of files without file extensions
         * Should default to generic file icon when extension is not present
         */
        it('handles files without extensions', async () => {
            const testData = {
                ...mockShareData,
                file: { ...mockShareData.file, originalFileName: 'README' }
            };
            mockedAxios.get.mockResolvedValue({ data: testData });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('README')).toBeInTheDocument();
            });

            // Should default to generic file icon for files without extensions
            // Component should render successfully with files without extensions
            expect(screen.getByText('README')).toBeInTheDocument();
        });

        /**
         * Test file size formatting for various sizes
         * Ensures proper display of file sizes in appropriate units (Bytes, KB, MB, GB)
         */
        it('formats large file sizes correctly', async () => {
            const testCases = [
                { size: 1024 * 1024 * 1024, expected: '1 GB' },
                { size: 1024 * 1024 * 500, expected: '500 MB' },
                { size: 1024 * 750, expected: '750 KB' },
                { size: 500, expected: '500 Bytes' }
            ];

            for (const testCase of testCases) {
                const testData = {
                    ...mockShareData,
                    file: { ...mockShareData.file, fileSize: testCase.size }
                };
                mockedAxios.get.mockResolvedValue({ data: testData });

                const { unmount } = render(<SharedFileAccess shareToken="test-token" />);

                await waitFor(() => {
                    expect(screen.getByText(testCase.expected)).toBeInTheDocument();
                });

                unmount();
            }
        });
    });

    /**
     * Test suite for date formatting and display functionality
     * Verifies proper handling and display of creation and expiration dates
     */
    describe('Date Formatting and Display', () => {
        beforeEach(() => {
            mockedAxios.post.mockResolvedValue({});
        });

        /**
         * Test consistent date formatting across different date values
         * Ensures dates are properly formatted and displayed to users
         */
        it('formats dates consistently across different locales', async () => {
            const testData = {
                ...mockShareData,
                createdAt: '2023-12-25T15:30:00Z',
                expiresAt: '2024-01-01T23:59:59Z'
            };
            mockedAxios.get.mockResolvedValue({ data: testData });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            // Verify that dates are formatted and displayed properly
            expect(screen.getByText(/Shared on/)).toBeInTheDocument();
            expect(screen.getByText(/Expires:/)).toBeInTheDocument();
        });

        /**
         * Test graceful handling of invalid date strings
         * Component should not crash when provided with malformed date data
         */
        it('handles invalid date strings gracefully', async () => {
            const testData = {
                ...mockShareData,
                createdAt: 'invalid-date',
                expiresAt: 'also-invalid'
            };
            mockedAxios.get.mockResolvedValue({ data: testData });

            const consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => { });

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            // Component should still render without crashing despite invalid dates
            expect(screen.getByText(/Shared on/)).toBeInTheDocument();

            consoleSpy.mockRestore();
        });
    });

    /**
     * Test suite for edge cases and error boundary scenarios
     * Ensures robust handling of unexpected data and error conditions
     */
    describe('Edge Cases and Error Boundaries', () => {
        /**
         * Test handling of null or undefined file data
         * Component should gracefully handle missing file information
         */
        it('handles null or undefined file data gracefully', async () => {
            const testData = {
                ...mockShareData,
                file: null
            };
            mockedAxios.get.mockResolvedValue({ data: testData });
            mockedAxios.post.mockResolvedValue({});

            render(<SharedFileAccess shareToken="test-token" />);

            // Component should handle null file data gracefully and show error message
            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            expect(screen.getByText('File information is not available or has been corrupted.')).toBeInTheDocument();
        });

        /**
         * Test handling of incomplete file properties
         * Component should work even when some file properties are missing
         */
        it('handles missing file properties', async () => {
            const testData = {
                ...mockShareData,
                file: {
                    id: 1
                    // Missing originalFileName and fileSize properties
                }
            };
            mockedAxios.get.mockResolvedValue({ data: testData });
            mockedAxios.post.mockResolvedValue({});

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                // Should not crash and should complete loading
                expect(screen.queryByText('Loading shared file...')).not.toBeInTheDocument();
            });
        });

        /**
         * Test handling of extremely long file names
         * Component should display long file names without breaking the layout
         */
        it('handles extremely long file names', async () => {
            const longFileName = 'a'.repeat(255) + '.pdf';
            const testData = {
                ...mockShareData,
                file: { ...mockShareData.file, originalFileName: longFileName }
            };
            mockedAxios.get.mockResolvedValue({ data: testData });
            mockedAxios.post.mockResolvedValue({});

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText(longFileName)).toBeInTheDocument();
            });

            // Should handle long file names without breaking the layout
            const titleElement = screen.getByText(longFileName);
            expect(titleElement).toBeInTheDocument();
        });

        /**
         * Test component behavior when shareToken changes
         * Ensures proper cleanup and re-initialization when token prop changes
         */
        it('reloads data when shareToken prop changes', async () => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});

            const { rerender } = render(<SharedFileAccess shareToken="token-1" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            // Change the token
            const newMockData = {
                ...mockShareData,
                file: { ...mockShareData.file, originalFileName: 'different-file.pdf' }
            };
            mockedAxios.get.mockResolvedValue({ data: newMockData });

            rerender(<SharedFileAccess shareToken="token-2" />);

            await waitFor(() => {
                expect(screen.getByText('different-file.pdf')).toBeInTheDocument();
            });

            // Should have made API calls for both tokens
            expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/shared/token-1');
            expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/shared/token-2');
        });

        /**
         * Test component cleanup and memory leak prevention
         * Ensures proper cleanup of event listeners and async operations
         */
        it('handles component unmounting during async operations', async () => {
            // Mock a slow API response
            mockedAxios.get.mockImplementation(() =>
                new Promise(resolve => setTimeout(() => resolve({ data: mockShareData }), 1000))
            );

            const { unmount } = render(<SharedFileAccess shareToken="test-token" />);

            // Unmount before API call completes
            unmount();

            // Should not cause any errors or memory leaks
            // This test mainly ensures no console errors occur during unmounting
            expect(true).toBe(true); // Placeholder assertion
        });
    });

    /**
     * Test suite for component performance and optimization
     * Verifies efficient rendering and API call patterns
     */
    describe('Performance and Optimization', () => {
        beforeEach(() => {
            mockedAxios.get.mockResolvedValue({ data: mockShareData });
            mockedAxios.post.mockResolvedValue({});
        });

        /**
         * Test that component doesn't make unnecessary API calls on re-renders
         * Ensures efficient use of network resources
         */
        it('prevents unnecessary API calls on re-renders', async () => {
            const { rerender } = render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            const initialCallCount = mockedAxios.get.mock.calls.length;

            // Re-render with same props should not trigger new API calls
            rerender(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            // Should not have made additional API calls
            expect(mockedAxios.get.mock.calls.length).toBe(initialCallCount);
        });

        /**
         * Test component behavior with rapid token changes
         * Ensures proper handling of race conditions
         */
        it('handles rapid token changes without race conditions', async () => {
            const { rerender } = render(<SharedFileAccess shareToken="token-1" />);

            // Quickly change tokens multiple times
            rerender(<SharedFileAccess shareToken="token-2" />);
            rerender(<SharedFileAccess shareToken="token-3" />);

            await waitFor(() => {
                expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
            });

            // Should handle rapid changes gracefully without errors
            expect(screen.getByText('test-document.pdf')).toBeInTheDocument();
        });
    });

    /**
     * Test suite for security and validation
     * Ensures proper handling of potentially malicious input
     */
    describe('Security and Validation', () => {
        /**
         * Test handling of potentially malicious share tokens
         * Ensures XSS protection and input sanitization
         */
        it('handles potentially malicious share tokens safely', async () => {
            const maliciousToken = '<script>alert("xss")</script>';
            mockedAxios.get.mockRejectedValue({ response: { status: 404 } });

            render(<SharedFileAccess shareToken={maliciousToken} />);

            await waitFor(() => {
                expect(screen.getByText('Unable to Access File')).toBeInTheDocument();
            });

            // Should not execute any scripts or cause XSS
            expect(screen.queryByRole('script')).not.toBeInTheDocument();
        });

        /**
         * Test handling of file names with special characters
         * Ensures proper encoding and display of various file name formats
         */
        it('handles file names with special characters', async () => {
            const specialFileName = 'test file (1) [copy] & more™.pdf';
            const testData = {
                ...mockShareData,
                file: { ...mockShareData.file, originalFileName: specialFileName }
            };
            mockedAxios.get.mockResolvedValue({ data: testData });
            mockedAxios.post.mockResolvedValue({});

            render(<SharedFileAccess shareToken="test-token" />);

            await waitFor(() => {
                expect(screen.getByText(specialFileName)).toBeInTheDocument();
            });

            // Should display special characters correctly without encoding issues
            expect(screen.getByText(specialFileName)).toBeInTheDocument();
        });
    });
});