import axios, { AxiosError } from 'axios';
import { message } from 'antd';

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
});

// Request interceptor: inject token from localStorage
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: handle 401 globally
http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      message.error('未授权，请重新登录');
      // Note: Not auto-redirecting because T5.7 login is not implemented
    }
    return Promise.reject(error);
  }
);

export default http;
