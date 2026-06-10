// frontend/admin/src/services/request.ts
import { message } from 'antd';
import { getAccessToken, getRefreshToken, clearAuth } from '@/utils/token';

/** TraceId 相关常量 */
const TRACE_ID_HEADER = 'X-Trace-Id';
const TRACE_ID_STORAGE_KEY = 'admin_trace_id';

/** 从 sessionStorage 获取缓存的 traceId */
function getCachedTraceId(): string | null {
  return sessionStorage.getItem(TRACE_ID_STORAGE_KEY);
}

/** 缓存 traceId（仅首次缓存，避免并发请求覆盖） */
function cacheTraceId(traceId: string): void {
  if (!sessionStorage.getItem(TRACE_ID_STORAGE_KEY)) {
    sessionStorage.setItem(TRACE_ID_STORAGE_KEY, traceId);
  }
}

/** 从响应头读取 traceId 并缓存 */
function extractAndCacheTraceId(response: any): void {
  const traceId = response?.headers?.[TRACE_ID_HEADER.toLowerCase()]
    || response?.headers?.[TRACE_ID_HEADER];
  if (traceId) {
    cacheTraceId(traceId);
  }
}

/** 获取当前 traceId 用于错误日志 */
function getCurrentTraceId(): string {
  return getCachedTraceId() || '-';
}

// 后端统一返回体类型
interface Result<T> {
  code: number;
  message: string;
  data: T;
}

// 刷新令牌响应
interface RefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

// 防止并发刷新的锁
let isRefreshing = false;

/** 用 raw fetch 刷新 token（绕过 axios 拦截器） */
async function tryRefreshToken(): Promise<void> {
  if (isRefreshing) return;
  isRefreshing = true;

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    clearAuth();
    window.location.href = '/login';
    return;
  }

  try {
    const resp = await fetch('/api/admin/refresh-token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    const result: Result<RefreshResponse> = await resp.json();
    if (result.code === 200 && result.data) {
      localStorage.setItem('admin_access_token', result.data.accessToken);
      localStorage.setItem('admin_refresh_token', result.data.refreshToken);
      // 刷新成功，reload 页面使新 token 生效
      window.location.reload();
    } else {
      clearAuth();
      window.location.href = '/login';
    }
  } catch {
    clearAuth();
    window.location.href = '/login';
  } finally {
    isRefreshing = false;
  }
}

// 统一请求配置
const requestConfig = {
  timeout: 10000,
  requestInterceptors: [
    (config: any) => {
      const token = getAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      // 自动带上缓存的 traceId
      const traceId = getCachedTraceId();
      if (traceId) {
        config.headers[TRACE_ID_HEADER] = traceId;
      }
      return config;
    },
  ],
  responseInterceptors: [
    (response: any) => {
      // 从响应头提取并缓存 traceId
      extractAndCacheTraceId(response);

      const result = response.data as Result<any>;

      // 成功：将 response.data 从 Result<T> 解包为 T
      if (result.code === 200) {
        response.data = result.data;
        return response;
      }

      // 业务错误（非 200 code）
      const errorMsg = result.message || '请求失败';
      message.error(errorMsg);
      return Promise.reject(new Error(errorMsg));
    },
  ],
  errorConfig: {
    errorHandler: (error: any) => {
      // 业务错误已由响应拦截器处理并抛出（纯 Error 无 response/config），直接忽略
      if (!error.response && !error.config) {
        return;
      }
      // 网络异常（AxiosError 无 response = 请求未到达服务器）
      if (!error.response) {
        console.error(`[网络异常] traceId=${getCurrentTraceId()}`);
        message.error('网络异常，请检查网络连接');
        return;
      }
      const status = error.response?.status;
      if (status === 401) {
        tryRefreshToken();
      } else if (status === 403) {
        message.error('无权限访问');
      } else if (status >= 500) {
        console.error(`[服务器错误] traceId=${getCurrentTraceId()}`);
        message.error('服务器错误');
      }
    },
  },
};

export default requestConfig;
