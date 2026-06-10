// frontend/admin/src/utils/token.ts
// Token 和用户信息管理工具

const ACCESS_TOKEN_KEY = 'admin_access_token';
const REFRESH_TOKEN_KEY = 'admin_refresh_token';
const USER_INFO_KEY = 'admin_user_info';

/** 用户信息（与后端 AdminUserResponse 一致） */
export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  role: string;
}

/** 获取 Access Token */
export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

/** 获取 Refresh Token */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/** 获取用户信息 */
export function getUserInfo(): UserInfo | null {
  const raw = localStorage.getItem(USER_INFO_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** 保存认证信息（登录成功后调用） */
export function setAuth(
  accessToken: string,
  refreshToken: string,
  user: UserInfo,
): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user));
}

/** 清除认证信息（登出或 token 失效时调用） */
export function clearAuth(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_INFO_KEY);
}

/** 是否已登录（仅检查 token 是否存在） */
export function isAuthenticated(): boolean {
  return !!getAccessToken();
}
