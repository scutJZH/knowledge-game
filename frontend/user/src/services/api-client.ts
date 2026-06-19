import axios, { type AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { ApiError } from '@/types/api';
import type { ApiResult } from '@/types/api';
import { useAuthStore } from '@/store/auth-store';
import { Modal } from 'antd';

/** 401 白名单 — 这些端点返回 401 时直接 reject，不进入刷新队列 */
export const AUTH_WHITELIST = ['/api/users/login', '/api/users/register', '/api/users/refresh-token'];

/** 主 api 实例：baseURL /api，timeout 15s，含 token 注入 + Result 解包 + 401 刷新队列 */
export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

/** 独立 axios 实例（不含 401 响应拦截器），供 refresh 调用避免无限循环 */
export const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
});

/* ---- 401 并发刷新队列 ---- */

let isRefreshing = false;
let pendingRequests: Array<{
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
  config: InternalAxiosRequestConfig;
}> = [];

/* ---- 请求拦截器：注入 Bearer token ---- */

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/* ---- 响应拦截器：Result 解包 + 异常转换 + 401 刷新 ---- */

apiClient.interceptors.response.use(
  (response: AxiosResponse<ApiResult<unknown>>) => {
    const { code, message, data } = response.data;
    if (code === 200) {
      return data;
    }
    throw new ApiError(code, message, response.status);
  },
  async (error: AxiosError<ApiResult<unknown>>) => {
    // 网络错误（无响应体）
    if (!error.response) {
      return Promise.reject(new ApiError(0, '网络异常，请检查网络连接', 0));
    }

    const { config, status, data } = error.response;

    // 非 401 错误：业务异常
    if (status !== 401) {
      if (data) {
        return Promise.reject(new ApiError(data.code, data.message, status));
      }
      return Promise.reject(new ApiError(status, error.message, status));
    }

    // 401 处理
    if (!config) {
      return Promise.reject(new ApiError(401, '未授权', 401));
    }

    const url = config.url || '';
    if (AUTH_WHITELIST.some((endpoint) => url.includes(endpoint))) {
      return Promise.reject(new ApiError(401, data?.message || '未授权', 401));
    }

    // 防止重试后的请求再次触发刷新（_retry 标记）
    if ((config as Record<string, unknown>)._retry) {
      return Promise.reject(new ApiError(401, 'Token 刷新后仍失败', 401));
    }

    // 排队机制：所有 401 请求（含首个）推入队列，由第一个 401 触发刷新
    return new Promise((resolve, reject) => {
      pendingRequests.push({ resolve, reject, config });

      if (!isRefreshing) {
        isRefreshing = true;

        (async () => {
          try {
            const refreshToken = useAuthStore.getState().refreshToken;
            const refreshRes = await refreshClient.post<ApiResult<{
              accessToken: string;
              refreshToken: string;
              expiresIn: number;
            }>>('/users/refresh-token', { refreshToken });

            if (refreshRes.data.code === 200) {
              const { accessToken, refreshToken: newRefresh, expiresIn } = refreshRes.data.data;
              useAuthStore.getState().setTokens(accessToken, newRefresh, expiresIn);

              // 重放所有排队请求（使用新 token）
              pendingRequests.forEach(({ resolve: res, reject: rej, config: pendingConfig }) => {
                delete pendingConfig.headers.Authorization;
                (pendingConfig as Record<string, unknown>)._retry = true;
                apiClient.request(pendingConfig).then(res).catch(rej);
              });
              pendingRequests = [];
            } else {
              throw new Error('refresh api returned non-200');
            }
          } catch {
            // 刷新失败：拒绝所有排队请求
            pendingRequests.forEach(({ reject: rej }) =>
              rej(new ApiError(401, '登录已过期', 401)),
            );
            pendingRequests = [];

            // 提示用户重新登录，确认后清除本地 token 并跳转登录页
            Modal.confirm({
              title: '登录已过期',
              content: '请重新登录',
              onOk: () => {
                useAuthStore.getState().logout();
                window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`;
              },
            });
          } finally {
            isRefreshing = false;
          }
        })();
      }
    });
  },
);
