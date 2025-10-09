/**
 * Working test suite for SharedFileAccess component
 * Uses a simplified approach to avoid mocking issues
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';

// Mock axios
jest.mock('axios', () => ({
    get: jest.fn(),
    post: jest.fn()
}));

// Mock the hooks directly in the component
jest.mock('./NotificationSystem', () => ({
    useNotification: () => ({
        showSuccess: jest.fn(),
        showError: jest.fn(),
        showInfo: jest.fn()
    }),
    NotificationProvider: ({ children }) => children
}));

jest.mock('./SharingErrorBoundary', () => ({
    useSharingErrorHandler: () => ({
        handleSharingError: jest.fn()
    })
}));

// Mock CSS
jest.mock('./SharedFileAccess.css', () => ({}));

// Mock URL APIs
global.URL.createObjectURL = jest.fn(() => 'mock-url');
global.URL.revokeObjectURL = jest.fn();

// Suppress console output
const originalError = console.error;
const originalWarn = console.warn;
beforeAll(() => {
    console.error = jest.fn();
    console.warn = jest.fn();
});
afterAll(() => {
    console.error = originalError;
    console.warn = originalWarn;
});

// Import component after mocks
import SharedFileAccess from './SharedFileAccess';
import { NotificationProvider } from './NotificationSystem';
import axios from 'axios';

const mockAxios = axios;

// Mock data
const mockShareData = {
    shareId: 1,
    shareToken: 'test-token',
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
        mockAxios.get.mockResolvedValue({ data: mockShareData });
        mockAxios.post.mockResolvedValue({});
    });

    afterEach(() => {
        jest.clearAllTimers();
    });

    /**
     * Test that component renders without crashing
     */
    it('renders without crashing', () => {
        const { container } = render(
            <NotificationProvider>
                <SharedFileAccess shareToken="test" />
            </NotificationProvider>
        );

        expect(container).toBeInTheDocument();
    });

    /**
     * Test invalid token handling
     */
    it('handles invalid token', async () => {
        render(
            <NotificationProvider>
                <SharedFileAccess shareToken="" />
            </NotificationProvider>
        );

        // Wait for component to process empty token
        await waitFor(() => {
            // Look for any error-related content
            const errorElements = screen.queryAllByText(/unable|invalid|error/i);
            expect(errorElements.length).toBeGreaterThan(0);
        }, { timeout: 2000 });

        expect(mockAxios.get).not.toHaveBeenCalled();
    });

    /**
     * Test successful file loading
     */
    it('displays file information after successful load', async () => {
        render(
            <NotificationProvider>
                <SharedFileAccess shareToken="test-token" />
            </NotificationProvider>
        );

        await waitFor(() => {
            // Look for file name or any content indicating successful load
            const fileElements = screen.queryAllByText(/test-document|pdf|download/i);
            expect(fileElements.length).toBeGreaterThan(0);
        }, { timeout: 2000 });

        expect(mockAxios.get).toHaveBeenCalledWith('/api/files/shared/test-token');
    });

    /**
     * Test loading state
     */
    it('shows loading state initially', async () => {
        // Mock axios to delay resolution
        let resolvePromise;
        const promise = new Promise((resolve) => {
            resolvePromise = resolve;
        });
        mockAxios.get.mockReturnValue(promise);

        render(
            <NotificationProvider>
                <SharedFileAccess shareToken="test-token" />
            </NotificationProvider>
        );

        // Look for loading content
        await waitFor(() => {
            const loadingElements = screen.queryAllByText(/loading|wait/i);
            expect(loadingElements.length).toBeGreaterThan(0);
        }, { timeout: 1000 });

        // Resolve the promise to prevent hanging
        resolvePromise({ data: mockShareData });

        // Wait for loading to complete
        await waitFor(() => {
            const loadingElements = screen.queryAllByText(/loading|wait/i);
            expect(loadingElements.length).toBe(0);
        }, { timeout: 2000 });
    });

    /**
     * Test API error handling
     */
    it('handles API errors', async () => {
        mockAxios.get.mockRejectedValue({
            response: { status: 404 }
        });

        render(
            <NotificationProvider>
                <SharedFileAccess shareToken="invalid-token" />
            </NotificationProvider>
        );

        await waitFor(() => {
            // Look for error-related content
            const errorElements = screen.queryAllByText(/unable|error|not found/i);
            expect(errorElements.length).toBeGreaterThan(0);
        }, { timeout: 2000 });
    });

    /**
     * Test that component makes API calls when token is provided
     */
    it('makes API calls with valid token', async () => {
        render(
            <NotificationProvider>
                <SharedFileAccess shareToken="valid-token" />
            </NotificationProvider>
        );

        await waitFor(() => {
            expect(mockAxios.get).toHaveBeenCalledWith('/api/files/shared/valid-token');
        }, { timeout: 2000 });
    });

    /**
     * Test that tests are no longer hanging
     */
    it('completes quickly without hanging', () => {
        const startTime = Date.now();

        render(
            <NotificationProvider>
                <SharedFileAccess shareToken="test" />
            </NotificationProvider>
        );

        const endTime = Date.now();
        const duration = endTime - startTime;

        // Test should complete in under 100ms
        expect(duration).toBeLessThan(100);
    });
});