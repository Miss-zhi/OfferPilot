/*
 * Copyright (c) 2020-06-29 Qoder. All rights reserved.
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { STORAGE_KEYS } from '@/infra/constants';

export interface AuthState {
  token: string | null;
  userId: string | null;
  username: string | null;
  role: string | null;

  setAuth: (token: string, userId: string, username: string, role: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      userId: null,
      username: null,
      role: null,

      setAuth: (token, userId, username, role) => {
        localStorage.setItem(STORAGE_KEYS.TOKEN, token);
        localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify({ userId, username, role }));
        set({ token, userId, username, role });
      },

      logout: () => {
        localStorage.removeItem(STORAGE_KEYS.TOKEN);
        localStorage.removeItem(STORAGE_KEYS.USER);
        set({ token: null, userId: null, username: null, role: null });
      },

      isAuthenticated: () => {
        const { token } = get();
        if (!token) return false;
        try {
          const payload = JSON.parse(atob(token.split('.')[1]));
          return payload.exp * 1000 > Date.now();
        } catch {
          return false;
        }
      },
    }),
    {
      name: 'offerpilot-auth',
    },
  ),
);
