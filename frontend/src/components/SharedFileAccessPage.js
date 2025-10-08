import React from 'react';
import { useParams } from 'react-router-dom';
import SharedFileAccess from './SharedFileAccess';

/**
 * Page wrapper component for shared file access
 * Extracts share token from URL parameters and passes to SharedFileAccess component
 */
const SharedFileAccessPage = () => {
  const { token } = useParams();

  return <SharedFileAccess shareToken={token} />;
};

export default SharedFileAccessPage;