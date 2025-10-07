import React, { createContext, useState, useContext, useEffect } from 'react';
import axios from 'axios';

const AuthContext = createContext();

export const useAuth = () => {
  return useContext(AuthContext);
};

export const AuthProvider = ({ children }) => {
  const [currentUser, setCurrentUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [loading, setLoading] = useState(true);

  // Logout function (defined early so it can be used in interceptor)
  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    setToken(null);
    setCurrentUser(null);
    delete axios.defaults.headers.common['Authorization'];
  };

  useEffect(() => {
    // Set up axios response interceptor to handle 401 errors globally
    const responseInterceptor = axios.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401 && token) {
          // Token is invalid/expired, logout user
          console.log('Token expired, logging out user');
          logout();
        }
        return Promise.reject(error);
      }
    );

    const storedToken = localStorage.getItem('token');
    const storedUserInfo = localStorage.getItem('userInfo');
    
    if (storedToken && storedUserInfo) {
      try {
        const userInfo = JSON.parse(storedUserInfo);
        
        // Set token and headers
        setToken(storedToken);
        setCurrentUser(userInfo);
        axios.defaults.headers.common['Authorization'] = `Bearer ${storedToken}`;
      } catch (error) {
        console.error('Error parsing user info from localStorage:', error);
        // If parsing fails, clear the invalid data
        localStorage.removeItem('userInfo');
        localStorage.removeItem('token');
        setToken(null);
        setCurrentUser(null);
      }
    }
    
    setLoading(false);

    // Cleanup interceptor on unmount
    return () => {
      axios.interceptors.response.eject(responseInterceptor);
    };
  }, [token]);

  const login = async (username, password) => {
    try {
      const response = await axios.post('/api/auth/signin', {
        username,
        password
      });
      
      const { accessToken, id, username: user, email } = response.data;
      
      const userInfo = { id, username: user, email };
      
      localStorage.setItem('token', accessToken);
      localStorage.setItem('userInfo', JSON.stringify(userInfo));
      setToken(accessToken);
      setCurrentUser(userInfo);
      axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
      
      return { success: true };
    } catch (error) {
      return { 
        success: false, 
        error: error.response?.data?.message || 'Login failed' 
      };
    }
  };

  const signup = async (username, email, password) => {
    try {
      await axios.post('/api/auth/signup', {
        username,
        email,
        password
      });
      
      return { success: true };
    } catch (error) {
      return { 
        success: false, 
        error: error.response?.data?.message || 'Signup failed' 
      };
    }
  };

  const verifyEmail = async (email, verificationCode) => {
    try {
      const response = await axios.post('/api/auth/verify-email', {
        email,
        verificationCode
      });
      
      return { success: true, data: response.data };
    } catch (error) {
      return { 
        success: false, 
        error: error.response?.data?.message || 'Email verification failed' 
      };
    }
  };

  const resendVerificationCode = async (email) => {
    try {
      const response = await axios.post('/api/auth/resend-verification', {
        email
      });
      
      return { success: true, data: response.data };
    } catch (error) {
      return { 
        success: false, 
        error: error.response?.data?.message || 'Failed to resend verification code' 
      };
    }
  };

  const getUserEmail = async (username) => {
    try {
      const response = await axios.get(`/api/auth/user-email/${username}`);
      return { success: true, email: response.data.email };
    } catch (error) {
      return { 
        success: false, 
        error: error.response?.data?.message || 'Failed to get user email' 
      };
    }
  };

  const value = {
    currentUser,
    token,
    login,
    signup,
    logout,
    verifyEmail,
    resendVerificationCode,
    getUserEmail
  };

  return (
    <AuthContext.Provider value={value}>
      {!loading && children}
    </AuthContext.Provider>
  );
};