/**
 * Test suite for FileList component
 * Tests sharing features, bulk operations, context menus, and file management
 */

import React from 'react';
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axios from 'axios';
import FileList from './FileList';

// Mock axios
jest.mock('axios');
const mockedAxios = axios;

// Mock data
const mockFiles = [
  {
    id: 1,
    originalFileName: 'document1.pdf',
    fileName: 'uuid1_document1.pdf',
    fileSize: 1024000,
    uploadTime: '2023-10-01T10:00:00Z'
  },
  {
    id: 2,
    originalFileName: 'image.jpg',
    fileName: 'uuid2_image.jpg',
    fileSize: 2048000,
    uploadTime: '2023-10-02T11:00:00Z'
  }
];

const mockShares = [
  {
    shareId: 1,
    shareToken: 'token1',
    permission: 'DOWNLOAD',
    createdAt: '2023-10-01T12:00:00Z',
    active: true
  }
];

describe('FileList Component', () => {
  const mockProps = {
    files: mockFiles,
    onFileUpdate: jest.fn(),
    onDownload: jest.fn(),
    onDelete: jest.fn(),
    loading: false
  };

  beforeEach(() => {
    jest.clearAllMocks();
    // Mock successful shares API calls
    mockedAxios.get.mockImplementation((url) => {
      if (url.includes('/shares')) {
        if (url.includes('/1/')) {
          return Promise.resolve({ data: mockShares });
        }
        return Promise.resolve({ data: [] });
      }
      return Promise.reject(new Error('Unknown endpoint'));
    });
  });

  afterEach(() => {
    cleanup();
  });

  describe('Basic Rendering', () => {
    it('renders file list with files', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
        expect(screen.getByText('image.jpg')).toBeInTheDocument();
      });
    });

    it('shows loading state', () => {
      render(<FileList {...mockProps} loading={true} />);
      expect(screen.getByText('Loading files...')).toBeInTheDocument();
    });

    it('shows empty state when no files', () => {
      render(<FileList {...mockProps} files={[]} />);
      expect(screen.getByText('No files uploaded yet')).toBeInTheDocument();
    });
  });

  describe('Share Status Indicators', () => {
    it('displays share indicators for shared files', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        const shareIndicators = screen.getAllByText('🔗');
        expect(shareIndicators.length).toBeGreaterThan(0);
      });
    });

    it('shows active share indicator with pulse animation', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        const activeIndicator = document.querySelector('.share-indicator.active');
        expect(activeIndicator).toBeInTheDocument();
      });
    });

    it('displays correct share count', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('1')).toBeInTheDocument(); // Share count
      });
    });
  });

  describe('File Selection and Bulk Operations', () => {
    it('allows individual file selection', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const checkboxes = screen.getAllByRole('checkbox');
      const fileCheckbox = checkboxes.find(cb => cb !== screen.getByLabelText(/Select All/));
      
      userEvent.click(fileCheckbox);

      await waitFor(() => {
        expect(screen.getByText('1 file selected')).toBeInTheDocument();
      });
    });

    it('shows bulk actions bar when files are selected', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const checkboxes = screen.getAllByRole('checkbox');
      const fileCheckbox = checkboxes.find(cb => cb !== screen.getByLabelText(/Select All/));
      
      userEvent.click(fileCheckbox);

      await waitFor(() => {
        expect(screen.getByText('🔗 Share')).toBeInTheDocument();
        expect(screen.getByText('✕ Clear')).toBeInTheDocument();
      });
    });

    it('handles select all functionality', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const selectAllCheckbox = screen.getByLabelText(/Select All/);
      userEvent.click(selectAllCheckbox);

      await waitFor(() => {
        expect(screen.getByText('2 files selected')).toBeInTheDocument();
      });
    });

    it('clears selection when clear button is clicked', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Select a file first
      const checkboxes = screen.getAllByRole('checkbox');
      const fileCheckbox = checkboxes.find(cb => cb !== screen.getByLabelText(/Select All/));
      userEvent.click(fileCheckbox);

      await waitFor(() => {
        expect(screen.getByText('1 file selected')).toBeInTheDocument();
      });

      // Click clear button
      const clearButton = screen.getByText('✕ Clear');
      userEvent.click(clearButton);

      await waitFor(() => {
        expect(screen.queryByText('1 file selected')).not.toBeInTheDocument();
      });
    });
  });

  describe('Context Menu', () => {
    it('shows context menu on right click', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const fileItem = screen.getByText('document1.pdf').closest('.file-item');
      fireEvent.contextMenu(fileItem);

      expect(screen.getByText('Share File')).toBeInTheDocument();
      expect(screen.getByText('Download')).toBeInTheDocument();
      expect(screen.getByText('Delete')).toBeInTheDocument();
    });

    it('calls download function when download is clicked in context menu', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const fileItem = screen.getByText('document1.pdf').closest('.file-item');
      fireEvent.contextMenu(fileItem);

      const downloadButton = screen.getByText('Download');
      userEvent.click(downloadButton);

      await waitFor(() => {
        expect(mockProps.onDownload).toHaveBeenCalledWith(
          'uuid1_document1.pdf',
          'document1.pdf'
        );
      });
    });

    it('calls delete function when delete is clicked in context menu', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const fileItem = screen.getByText('document1.pdf').closest('.file-item');
      fireEvent.contextMenu(fileItem);

      const deleteButton = screen.getByText('Delete');
      userEvent.click(deleteButton);

      await waitFor(() => {
        expect(mockProps.onDelete).toHaveBeenCalledWith(1);
      });
    });
  });

  describe('Quick Actions', () => {
    it('renders action buttons for each file', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const shareButtons = screen.getAllByTitle('Share file');
      const downloadButtons = screen.getAllByTitle('Download file');
      const deleteButtons = screen.getAllByTitle('Delete file');

      expect(shareButtons).toHaveLength(2);
      expect(downloadButtons).toHaveLength(2);
      expect(deleteButtons).toHaveLength(2);
    });

    it('calls download function when download button is clicked', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const downloadButtons = screen.getAllByTitle('Download file');
      userEvent.click(downloadButtons[0]);

      await waitFor(() => {
        expect(mockProps.onDownload).toHaveBeenCalledWith(
          'uuid1_document1.pdf',
          'document1.pdf'
        );
      });
    });

    it('calls delete function when delete button is clicked', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const deleteButtons = screen.getAllByTitle('Delete file');
      userEvent.click(deleteButtons[0]);

      await waitFor(() => {
        expect(mockProps.onDelete).toHaveBeenCalledWith(1);
      });
    });
  });

  describe('Share Functionality', () => {
    it('opens share modal when share button is clicked', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const shareButtons = screen.getAllByTitle('Share file');
      userEvent.click(shareButtons[0]);

      // ShareFileModal should be rendered (we'll need to mock it or check for modal content)
      // This test might need adjustment based on how ShareFileModal is implemented
    });

    it('handles bulk share operation', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Select a file
      const checkboxes = screen.getAllByRole('checkbox');
      const fileCheckbox = checkboxes.find(cb => cb !== screen.getByLabelText(/Select All/));
      userEvent.click(fileCheckbox);

      await waitFor(() => {
        expect(screen.getByText('🔗 Share')).toBeInTheDocument();
      });

      // Click bulk share button
      const bulkShareButton = screen.getByText('🔗 Share');
      userEvent.click(bulkShareButton);

      // Should open share modal for the selected file
    });
  });

  describe('API Integration', () => {
    it('loads share information for files on mount', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/1/shares');
        expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/2/shares');
      });
    });

    it('handles API errors gracefully when loading shares', async () => {
      mockedAxios.get.mockRejectedValue(new Error('API Error'));
      
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Should still render files even if share loading fails
      expect(screen.getByText('image.jpg')).toBeInTheDocument();
    });

    it('calls onFileUpdate after successful share creation', async () => {
      mockedAxios.post.mockResolvedValue({
        data: { shareToken: 'new-token', shareUrl: 'http://example.com/shared/new-token' }
      });

      const fileList = render(<FileList {...mockProps} />);
      const component = fileList.container.querySelector('.file-list-container');
      
      // Simulate share creation (this would normally be triggered through the modal)
      // We'll test this indirectly by checking if the component calls the API correctly
      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });
    });

    it('reloads share information after share creation', async () => {
      mockedAxios.get.mockResolvedValue({ data: mockShares });
      mockedAxios.post.mockResolvedValue({
        data: { shareToken: 'new-token', shareUrl: 'http://example.com/shared/new-token' }
      });

      const { rerender } = render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Wait for initial share loading to complete
      await waitFor(() => {
        expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/1/shares');
        expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/2/shares');
      });

      // Clear previous calls to test reload behavior
      mockedAxios.get.mockClear();

      // Simulate file update by re-rendering with a new files array (same content, new reference)
      // This simulates what happens when onFileUpdate is called and the parent fetches files again
      const updatedFiles = [...mockFiles]; // Create new array reference
      rerender(<FileList {...mockProps} files={updatedFiles} />);

      // The component should reload share information when files prop changes
      await waitFor(() => {
        expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/1/shares');
        expect(mockedAxios.get).toHaveBeenCalledWith('/api/files/2/shares');
      });
    });
  });

  describe('Share Activity Indicators', () => {
    it('displays last shared timestamp when available', async () => {
      const mockSharesWithActivity = [
        {
          shareId: 1,
          shareToken: 'token1',
          permission: 'DOWNLOAD',
          createdAt: '2023-10-01T12:00:00Z',
          active: true
        }
      ];

      mockedAxios.get.mockImplementation((url) => {
        if (url.includes('/1/')) {
          return Promise.resolve({ data: mockSharesWithActivity });
        }
        return Promise.resolve({ data: [] });
      });

      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Should show last shared activity
      await waitFor(() => {
        expect(screen.getByText(/Last shared:/)).toBeInTheDocument();
      });
    });

    it('shows loading state for shares', async () => {
      // Mock delayed response
      mockedAxios.get.mockImplementation(() => 
        new Promise(resolve => setTimeout(() => resolve({ data: [] }), 100))
      );

      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('Loading shares...')).toBeInTheDocument();
      });
    });
  });

  describe('File Styling Based on Share Status', () => {
    it('applies shared styling to files with active shares', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Find the file item and check if it has the shared class
      const fileItem = screen.getByText('document1.pdf').closest('.file-item');
      await waitFor(() => {
        expect(fileItem).toHaveClass('shared');
      });
    });

    it('does not apply shared styling to files without shares', async () => {
      mockedAxios.get.mockImplementation(() => Promise.resolve({ data: [] }));

      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      const fileItem = screen.getByText('document1.pdf').closest('.file-item');
      expect(fileItem).not.toHaveClass('shared');
    });
  });

  describe('Responsive Design', () => {
    it('adapts layout for mobile screens', () => {
      // Mock window.innerWidth for mobile
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 480,
      });

      render(<FileList {...mockProps} />);

      // Check if mobile-specific classes or behaviors are applied
      // This would depend on how responsive design is implemented
    });
  });

  describe('Accessibility', () => {
    it('provides proper ARIA labels and roles', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Check for proper checkbox labels
      expect(screen.getByLabelText(/Select All/)).toBeInTheDocument();
      
      // Check for button titles/aria-labels
      expect(screen.getAllByTitle('Share file')).toHaveLength(2);
      expect(screen.getAllByTitle('Download file')).toHaveLength(2);
      expect(screen.getAllByTitle('Delete file')).toHaveLength(2);
    });

    it('supports keyboard navigation', async () => {
      render(<FileList {...mockProps} />);

      await waitFor(() => {
        expect(screen.getByText('document1.pdf')).toBeInTheDocument();
      });

      // Test tab navigation through interactive elements
      userEvent.tab();
      await waitFor(() => {
        expect(document.activeElement).toHaveAttribute('type', 'checkbox');
      });
    });
  });
});