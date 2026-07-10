/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import http from '@/infra/http';
import { API } from '@/infra/constants';

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
  role: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  email?: string;
}

export const authService = {
  login: (data: LoginRequest) =>
    http.post<AuthResponse>(API.AUTH_LOGIN, data).then((res) => res.data),

  register: (data: RegisterRequest) =>
    http.post<AuthResponse>(API.AUTH_REGISTER, data).then((res) => res.data),
};
