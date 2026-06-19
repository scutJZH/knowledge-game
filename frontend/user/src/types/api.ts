/** 后端 Result 包装层，用于拦截器未解包前的原始响应类型 */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

/** 自定义 API 错误，区分网络错误 / 业务错误 / HTTP 错误 */
export class ApiError extends Error {
  code: number;
  httpStatus: number;

  constructor(code: number, message: string, httpStatus: number) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.httpStatus = httpStatus;
  }
}

/** 后端 UserResponse 对应的前端类型 */
export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  role: string;
  avatarFileId: number | null;
  avatarUrl: string | null;
}
