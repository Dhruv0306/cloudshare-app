import axios from 'axios';

// Mock axios
jest.mock('axios', () => ({
  get: jest.fn(),
}));

describe('fetchFiles function behavior', () => {
  let mockSetFiles;
  let mockSetLoading;
  let mockSetMessage;
  let mockSetMessageType;
  let consoleErrorSpy;

  beforeEach(() => {
    jest.clearAllMocks();
    mockSetFiles = jest.fn();
    mockSetLoading = jest.fn();
    mockSetMessage = jest.fn();
    mockSetMessageType = jest.fn();
    consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  // Simulate the fetchFiles function logic
  const fetchFiles = async () => {
    try {
      const response = await axios.get('/api/files');
      // Ensure we always set an array, even if response.data is null/undefined
      mockSetFiles(Array.isArray(response.data) ? response.data : []);
      mockSetLoading(false);
    } catch (error) {
      console.error('Error fetching files:', error);
      mockSetMessage('Error fetching files');
      mockSetMessageType('error');
      mockSetFiles([]); // Ensure files is always an array
      mockSetLoading(false);
    }
  };

  test('handles successful response with valid array', async () => {
    const mockFiles = [
      { id: 1, originalFileName: 'test1.txt', fileName: 'uuid1_test1.txt', fileSize: 1024, uploadTime: '2023-01-01T00:00:00Z' },
      { id: 2, originalFileName: 'test2.pdf', fileName: 'uuid2_test2.pdf', fileSize: 2048, uploadTime: '2023-01-02T00:00:00Z' }
    ];
    
    axios.get.mockResolvedValueOnce({ data: mockFiles });

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith(mockFiles);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).not.toHaveBeenCalled();
    expect(mockSetMessageType).not.toHaveBeenCalled();
  });

  test('handles null response data by setting empty array', async () => {
    axios.get.mockResolvedValueOnce({ data: null });

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith([]);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).not.toHaveBeenCalled();
    expect(mockSetMessageType).not.toHaveBeenCalled();
  });

  test('handles undefined response data by setting empty array', async () => {
    axios.get.mockResolvedValueOnce({ data: undefined });

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith([]);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).not.toHaveBeenCalled();
    expect(mockSetMessageType).not.toHaveBeenCalled();
  });

  test('handles non-array response data by setting empty array', async () => {
    axios.get.mockResolvedValueOnce({ data: 'invalid-data' });

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith([]);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).not.toHaveBeenCalled();
    expect(mockSetMessageType).not.toHaveBeenCalled();
  });

  test('handles object response data by setting empty array', async () => {
    axios.get.mockResolvedValueOnce({ data: { message: 'some object' } });

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith([]);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).not.toHaveBeenCalled();
    expect(mockSetMessageType).not.toHaveBeenCalled();
  });

  test('handles network error by setting empty array and error message', async () => {
    const networkError = new Error('Network error');
    axios.get.mockRejectedValueOnce(networkError);

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith([]);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).toHaveBeenCalledWith('Error fetching files');
    expect(mockSetMessageType).toHaveBeenCalledWith('error');
    expect(consoleErrorSpy).toHaveBeenCalledWith('Error fetching files:', networkError);
  });

  test('handles API error response by setting empty array and error message', async () => {
    const apiError = {
      response: {
        status: 500,
        data: { message: 'Internal server error' }
      }
    };
    axios.get.mockRejectedValueOnce(apiError);

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith([]);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).toHaveBeenCalledWith('Error fetching files');
    expect(mockSetMessageType).toHaveBeenCalledWith('error');
    expect(consoleErrorSpy).toHaveBeenCalledWith('Error fetching files:', apiError);
  });

  test('handles empty array response correctly', async () => {
    axios.get.mockResolvedValueOnce({ data: [] });

    await fetchFiles();

    expect(axios.get).toHaveBeenCalledWith('/api/files');
    expect(mockSetFiles).toHaveBeenCalledWith([]);
    expect(mockSetLoading).toHaveBeenCalledWith(false);
    expect(mockSetMessage).not.toHaveBeenCalled();
    expect(mockSetMessageType).not.toHaveBeenCalled();
  });

  test('ensures Array.isArray check works correctly for various data types', async () => {
    const testCases = [
      { input: [], expected: [] },
      { input: [1, 2, 3], expected: [1, 2, 3] },
      { input: null, expected: [] },
      { input: undefined, expected: [] },
      { input: 'string', expected: [] },
      { input: 123, expected: [] },
      { input: { key: 'value' }, expected: [] },
      { input: true, expected: [] },
      { input: false, expected: [] }
    ];

    for (const testCase of testCases) {
      jest.clearAllMocks();
      axios.get.mockResolvedValueOnce({ data: testCase.input });

      await fetchFiles();

      expect(mockSetFiles).toHaveBeenCalledWith(testCase.expected);
    }
  });
});