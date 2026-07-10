/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import axios, { AxiosError } from 'axios';
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { STORAGE_KEYS } from './constants';

/** 后端统一响应格式 */
interface ApiResponseBody<T = unknown> {
  code: number;
  message: string;
  data: T;
}

const http = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/* 请求拦截器：注入 JWT Token */
http.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error: AxiosError) => Promise.reject(error),
);

/* 响应拦截器：解包 ApiResponse + 统一错误处理 */
http.interceptors.response.use(
  (response: AxiosResponse<ApiResponseBody | unknown>) => {
    const body = response.data;
    if (body && typeof body === 'object' && 'code' in body) {
      const apiBody = body as ApiResponseBody;
      if (apiBody.code === 200) {
        // 解包：直接返回 data
        return { ...response, data: apiBody.data } as AxiosResponse;
      }
      // 业务错误
      return Promise.reject(new Error(apiBody.message || '请求失败'));
    }
    return response;
  },
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(STORAGE_KEYS.TOKEN);
      localStorage.removeItem(STORAGE_KEYS.USER);
      window.location.href = '/login';
    }
    console.error('API Error:', error);
    return Promise.reject(error);
  },
);

export default http;
