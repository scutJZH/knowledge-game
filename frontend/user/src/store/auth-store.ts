import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { StateStorage } from 'zustand/middleware';
import type { UserInfo } from '@/types/api';

function readRememberMeFromStorage(): boolean {
  try {
    const localRaw = localStorage.getItem('auth-storage');
    if (localRaw) {
      const parsed = JSON.parse(localRaw);
      if (typeof parsed?.state?.rememberMe === 'boolean') return parsed.state.rememberMe;
    }
    const sessionRaw = sessionStorage.getItem('auth-storage');
    if (sessionRaw) {
      const parsed = JSON.parse(sessionRaw);
      if (typeof parsed?.state?.rememberMe === 'boolean') return parsed.state.rememberMe;
    }
  } catch { /* 解析失败走默认值 */ }
  return true;
}

let currentRememberMe = readRememberMeFromStorage();

const dynamicStorage: StateStorage = {
  getItem: (name) => {
    const s = currentRememberMe ? localStorage : sessionStorage;
    const raw = s.getItem(name);
    if (raw === null) return null;
    return JSON.parse(raw);
  },
  setItem: (name, value) => {
    const s = currentRememberMe ? localStorage : sessionStorage;
    s.setItem(name, JSON.stringify(value));
  },
  removeItem: (name) => {
    const s = currentRememberMe ? localStorage : sessionStorage;
    s.removeItem(name);
  },
};

function migrateAuthStorage(targetRemember: boolean) {
  const fromStorage = currentRememberMe ? localStorage : sessionStorage;
  const toStorage = targetRemember ? localStorage : sessionStorage;
  const raw = fromStorage.getItem('auth-storage');
  if (raw) {
    toStorage.setItem('auth-storage', raw);
    fromStorage.removeItem('auth-storage');
  }
  currentRememberMe = targetRemember;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  expiresIn: number | null;
  user: UserInfo | null;
  rememberMe: boolean;

  /** 派生状态：是否已认证（有 accessToken 即视为已登录） */
  isAuthenticated: () => boolean;

  /** 登录：设置 token + user，remember 默认 true（localStorage），false 则用 sessionStorage */
  login: (accessToken: string, refreshToken: string, expiresIn: number, user: UserInfo, remember?: boolean) => void;

  /** 登出：清空本地状态（API 调用由调用方负责） */
  logout: () => void;

  /** 仅更新 token（refresh 成功后调用），不改变 user */
  setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => void;

  /** 切换「记住我」storage 策略，并迁移已有数据 */
  setRememberMe: (remember: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      expiresIn: null,
      user: null,
      rememberMe: currentRememberMe,

      isAuthenticated: () => !!get().accessToken,

      login: (accessToken, refreshToken, expiresIn, user, remember = true) => {
        if (remember !== currentRememberMe) {
          migrateAuthStorage(remember);
        }
        set({ accessToken, refreshToken, expiresIn, user, rememberMe: remember });
      },

      logout: () =>
        set({ accessToken: null, refreshToken: null, expiresIn: null, user: null }),

      setTokens: (accessToken, refreshToken, expiresIn) =>
        set({ accessToken, refreshToken, expiresIn }),

      setRememberMe: (remember) => {
        if (remember !== currentRememberMe) {
          migrateAuthStorage(remember);
        }
        set({ rememberMe: remember });
      },
    }),
    {
      name: 'auth-storage',
      storage: dynamicStorage,
    },
  ),
);
