import { message } from 'antd';
import type { RequestConfig } from '@umijs/max';

// 后端统一返回体类型
interface Result<T> {
  code: number;
  message: string;
  data: T;
}

// 统一请求配置
const requestConfig: RequestConfig = {
  timeout: 10000,
  requestInterceptors: [
    (config: any) => {
      // Token 注入预留（REQ-41 接入）
      // const token = localStorage.getItem('access_token');
      // if (token) {
      //   config.headers.Authorization = `Bearer ${token}`;
      // }
      return config;
    },
  ],
  responseInterceptors: [
    (response: any) => {
      const result = response.data as Result<any>;

      // 成功：code === 200，返回 data 部分
      if (result.code === 200) {
        return result.data;
      }

      // 业务错误：非 200 code
      const errorMsg = result.message || '请求失败';
      message.error(errorMsg);
      throw new Error(errorMsg);
    },
  ],
  errorConfig: {
    errorHandler: (error: any) => {
      // 网络异常
      if (error.response === undefined) {
        message.error('网络异常，请检查网络连接');
        return;
      }
      // HTTP 状态码错误
      const status = error.response?.status;
      if (status === 401) {
        message.error('未授权，请重新登录');
        // REQ-41 接入后跳转到登录页
        // history.push('/login');
      } else if (status === 403) {
        message.error('无权限访问');
      } else if (status >= 500) {
        message.error('服务器错误');
      }
    },
  },
};

export default requestConfig;
