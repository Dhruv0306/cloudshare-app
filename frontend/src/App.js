import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Login from './components/Login';
import Signup from './components/Signup';
import ErrorBoundary from './components/ErrorBoundary';
import SharedFileAccessPage from './components/SharedFileAccessPage';
import FileList from './components/FileList';
import './components/ErrorBoundary.css';

function FileManager() {
  const [files, setFiles] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [messageType, setMessageType] = useState('');
  const { currentUser, logout, token } = useAuth();

  useEffect(() => {
    // Only fetch files if user is authenticated and token is available
    if (currentUser && token) {
      fetchFiles();
    }
  }, [currentUser, token]);

  const fetchFiles = async () => {
    try {
      const response = await axios.get('/api/files');
      // Ensure we always set an array, even if response.data is null/undefined
      setFiles(Array.isArray(response.data) ? response.data : []);
      setLoading(false);
    } catch (error) {
      console.error('Error fetching files:', error);
      // 401 errors are now handled globally by the axios interceptor
      setMessage('Error fetching files');
      setMessageType('error');
      setFiles([]); // Ensure files is always an array
      setLoading(false);
    }
  };

  const handleFileSelect = (event) => {
    setSelectedFile(event.target.files[0]);
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      setMessage('Please select a file to upload');
      setMessageType('error');
      return;
    }

    setUploading(true);
    const formData = new FormData();
    formData.append('file', selectedFile);

    try {
      await axios.post('/api/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      setMessage('File uploaded successfully!');
      setMessageType('success');
      setSelectedFile(null);
      document.getElementById('fileInput').value = '';
      fetchFiles();
    } catch (error) {
      console.error('Error uploading file:', error);
      setMessage('Error uploading file');
      setMessageType('error');
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (fileName, originalFileName) => {
    try {
      const response = await axios.get(`/api/files/download/${fileName}`, {
        responseType: 'blob',
      });

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', originalFileName);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Error downloading file:', error);
      setMessage('Error downloading file');
      setMessageType('error');
    }
  };

  const handleDelete = async (fileId) => {
    if (window.confirm('Are you sure you want to delete this file?')) {
      try {
        await axios.delete(`/api/files/${fileId}`);
        setMessage('File deleted successfully!');
        setMessageType('success');
        fetchFiles();
      } catch (error) {
        console.error('Error deleting file:', error);
        setMessage('Error deleting file');
        setMessageType('error');
      }
    }
  };



  return (
    <div className="container">
      <div className="header-section">
        <h1 className="header">File Sharing App</h1>
        <div className="user-info">
          <span>Welcome, {currentUser?.username}!</span>
          <button onClick={logout} className="logout-btn">Logout</button>
        </div>
      </div>

      {message && (
        <div className={messageType === 'error' ? 'error' : 'success'}>
          {message}
        </div>
      )}

      <div className="upload-section">
        <h2>Upload File</h2>
        <div className="file-input">
          <input
            id="fileInput"
            type="file"
            onChange={handleFileSelect}
            disabled={uploading}
          />
        </div>
        <button
          className="upload-btn"
          onClick={handleUpload}
          disabled={uploading || !selectedFile}
        >
          {uploading ? 'Uploading...' : 'Upload File'}
        </button>
      </div>

      <div className="files-section">
        <h2>Uploaded Files</h2>
        <FileList
          files={files}
          onFileUpdate={fetchFiles}
          onDownload={handleDownload}
          onDelete={handleDelete}
          loading={loading}
        />
      </div>
    </div>
  );
}

function AuthWrapper() {
  const [isLogin, setIsLogin] = useState(true);
  const { token, currentUser } = useAuth();

  // Only show FileManager if BOTH token and currentUser exist
  if (token && currentUser) {
    return <FileManager />;
  }

  return isLogin ? (
    <Login onSwitchToSignup={() => setIsLogin(false)} />
  ) : (
    <Signup onSwitchToLogin={() => setIsLogin(true)} />
  );
}

function App() {
  return (
    <ErrorBoundary>
      <Router>
        <Routes>
          {/* Public route for shared file access */}
          <Route path="/shared/:token" element={<SharedFileAccessPage />} />
          
          {/* Main application routes */}
          <Route path="/*" element={
            <AuthProvider>
              <AuthWrapper />
            </AuthProvider>
          } />
        </Routes>
      </Router>
    </ErrorBoundary>
  );
}

export default App;