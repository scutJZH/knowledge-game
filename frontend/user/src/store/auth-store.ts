import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserInfo } from '@/types/api';

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  expiresIn: number | null;
  user: UserInfo | null;

  /** 派生状态：是否已认证（有 accessToken 即视为已登录） */
  isAuthenticated: () => boolean;

  /** 登录：设置 token + user */
  login: (accessToken: string, refreshToken: string, expiresIn: number, user: UserInfo) => void;

  /** 登出：清空本地状态（API 调用由调用方负责） */
  logout: () => void;

  /** 仅更新 token（refresh 成功后调用），不改变 user */
  setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      expiresIn: null,
      user: null,

      isAuthenticated: () => !!get().accessToken,

      login: (accessToken, refreshToken, expiresIn, user) =>
        set({ accessToken, refreshToken, expiresIn, user }),

      logout: () =>
        set({ accessToken: null, refreshToken: null, expiresIn: null, user: null }),

      setTokens: (accessToken, refreshToken, expiresIn) =>
        set({ accessToken, refreshToken, expiresIn }),
    }),
    {
      name: 'auth-storage',
    },
  ),
);
