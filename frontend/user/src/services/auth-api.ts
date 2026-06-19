import { apiClient, refreshClient } from './api-client';
import type { UserInfo } from '@/types/api';

/** 登录 */
export function loginApi(data: { username: string; password: string }) {
  return apiClient.post<
    never,
    { accessToken: string; refreshToken: string; expiresIn: number; user: UserInfo }
  >('/users/login', data);
}

/** 注册 */
export function registerApi(data: { username: string; password: string; nickname: string }) {
  return apiClient.post<never, UserInfo>('/users/register', data);
}

/** 刷新 token — 使用独立 refreshClient（不含 401 响应拦截器），防止无限循环 */
export function refreshTokenApi(refreshToken: string) {
  return refreshClient.post<
    never,
    { accessToken: string; refreshToken: string; expiresIn: number }
  >('/users/refresh-token', { refreshToken });
}

/**
 * 登出 — accessToken 由 apiClient 的请求拦截器自动注入 Authorization 头，
 * 调用方只需传 refreshToken
 */
export function logoutApi(refreshToken?: string) {
  return apiClient.post<never, void>('/users/logout', { refreshToken });
}
