import { describe, it, expect, beforeEach, vi } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import { Modal } from 'antd';

// auth store 的 mock 状态（可变变量，getState() 每次读取最新值）
let mockAccessToken: string | null = null;
let mockRefreshToken: string | null = null;
const mockLogout = vi.fn();
const mockSetTokens = vi.fn((accessToken: string, refreshToken: string) => {
  mockAccessToken = accessToken;
  mockRefreshToken = refreshToken;
});

vi.mock('@/store/auth-store', () => ({
  useAuthStore: {
    getState: () => ({
      accessToken: mockAccessToken,
      refreshToken: mockRefreshToken,
      logout: mockLogout,
      setTokens: mockSetTokens,
    }),
  },
}));

// 需要在模块导入前 mock antd Modal
vi.mock('antd', async () => {
  const actual = await vi.importActual<typeof import('antd')>('antd');
  return {
    ...actual,
    Modal: {
      ...actual.Modal,
      confirm: vi.fn(),
    },
  };
});

describe('api-client', () => {
  let apiClientMock: MockAdapter;
  let refreshClientMock: MockAdapter;

  beforeEach(async () => {
    vi.clearAllMocks();
    mockAccessToken = null;
    mockRefreshToken = null;

    // jsdom 不支持 navigation，测试中 mock window.location
    vi.stubGlobal('location', {
      href: '',
      pathname: '/current-page',
      assign: vi.fn(),
      replace: vi.fn(),
    });

    // 每次测试动态导入，确保拦截器使用最新的 mock 状态
    vi.resetModules();
    const mod = await import('@/services/api-client');
    const { apiClient, refreshClient } = mod;

    apiClientMock = new MockAdapter(apiClient);
    refreshClientMock = new MockAdapter(refreshClient);
  });

  // 用例 1：成功响应解包 — code=200 返回 response.data.data
  it('成功响应解包：code=200 返回 response.data.data', async () => {
    const mod = await import('@/services/api-client');
    apiClientMock.onGet('/test').reply(200, { code: 200, message: 'ok', data: { id: 1, name: 'test' } });
    const result = await mod.apiClient.get('/test');
    expect(result).toEqual({ id: 1, name: 'test' });
  });

  // 用例 2：业务错误抛 ApiError — code≠200 抛 ApiError(code, message, httpStatus)
  it('业务错误抛 ApiError：code≠200 抛 ApiError(code, message, httpStatus)', async () => {
    const mod = await import('@/services/api-client');
    apiClientMock.onGet('/test').reply(400, { code: 1001, message: '参数错误', data: null });
    await expect(mod.apiClient.get('/test')).rejects.toMatchObject({
      name: 'ApiError',
      code: 1001,
      message: '参数错误',
      httpStatus: 400,
    });
  });

  // 用例 3：网络错误 — 无 error.response 时抛 ApiError(0, '网络异常，请检查网络连接', 0)
  it('网络错误：无 error.response 时抛 ApiError(0, "网络异常，请检查网络连接", 0)', async () => {
    const mod = await import('@/services/api-client');
    apiClientMock.onGet('/test').networkError();
    await expect(mod.apiClient.get('/test')).rejects.toMatchObject({
      name: 'ApiError',
      code: 0,
      message: '网络异常，请检查网络连接',
      httpStatus: 0,
    });
  });

  // 用例 4：请求拦截器注入 token — auth store 有 accessToken 时请求头含 Authorization: Bearer <token>
  it('请求拦截器注入 token：auth store 有 accessToken 时请求头含 Authorization: Bearer <token>', async () => {
    mockAccessToken = 'test-token-123';
    const mod = await import('@/services/api-client');
    apiClientMock.onGet('/test').reply(200, { code: 200, message: 'ok', data: {} });
    await mod.apiClient.get('/test');
    const headers = apiClientMock.history.get?.[0]?.headers;
    expect(headers?.Authorization).toBe('Bearer test-token-123');
  });

  // 用例 5：无 token 时不注入 — auth store 无 accessToken 时请求头不含 Authorization
  it('无 token 时不注入：auth store 无 accessToken 时请求头不含 Authorization', async () => {
    mockAccessToken = null;
    const mod = await import('@/services/api-client');
    apiClientMock.onGet('/test').reply(200, { code: 200, message: 'ok', data: {} });
    await mod.apiClient.get('/test');
    const headers = apiClientMock.history.get?.[0]?.headers;
    expect(headers?.Authorization).toBeUndefined();
  });

  // 用例 6：401 自动刷新 — 模拟 401 → refresh 成功 → 重放原请求成功
  it('401 自动刷新：模拟 401 → refresh 成功 → 重放原请求成功', async () => {
    mockAccessToken = 'old-access';
    mockRefreshToken = 'old-refresh';
    const mod = await import('@/services/api-client');

    // 第一次请求返回 401；重放后返回 200
    apiClientMock.onGet('/protected').replyOnce(401, { code: 401, message: '未授权', data: null });
    apiClientMock.onGet('/protected').replyOnce(200, { code: 200, message: 'ok', data: { retried: true } });
    // refresh 请求返回 200
    refreshClientMock.onPost('/users/refresh-token').reply(200, {
      code: 200,
      message: 'ok',
      data: { accessToken: 'new-access', refreshToken: 'new-refresh', expiresIn: 3600 },
    });

    const result = await mod.apiClient.get('/protected');
    expect(result).toEqual({ retried: true });
    expect(mockSetTokens).toHaveBeenCalledWith('new-access', 'new-refresh', 3600);
    expect(mockAccessToken).toBe('new-access');
  });

  // 用例 7：并发 401 排队 — 3 个 401 同时到达 → 仅 1 次 refresh → 3 次重放
  it('并发 401 排队：3 个 401 同时到达 → 仅 1 次 refresh → 3 次重放', async () => {
    mockAccessToken = 'old-access';
    mockRefreshToken = 'old-refresh';
    const mod = await import('@/services/api-client');

    // 三个请求都先返回 401，重放后都返回 200
    apiClientMock.onGet('/a').replyOnce(401, { code: 401, message: '未授权', data: null });
    apiClientMock.onGet('/a').replyOnce(200, { code: 200, message: 'ok', data: 'A' });
    apiClientMock.onGet('/b').replyOnce(401, { code: 401, message: '未授权', data: null });
    apiClientMock.onGet('/b').replyOnce(200, { code: 200, message: 'ok', data: 'B' });
    apiClientMock.onGet('/c').replyOnce(401, { code: 401, message: '未授权', data: null });
    apiClientMock.onGet('/c').replyOnce(200, { code: 200, message: 'ok', data: 'C' });
    // refresh 仅被调用 1 次
    refreshClientMock.onPost('/users/refresh-token').reply(200, {
      code: 200,
      message: 'ok',
      data: { accessToken: 'new-access', refreshToken: 'new-refresh', expiresIn: 3600 },
    });

    const results = await Promise.all([
      mod.apiClient.get('/a'),
      mod.apiClient.get('/b'),
      mod.apiClient.get('/c'),
    ]);
    expect(results).toEqual(['A', 'B', 'C']);
    // 验证 refresh 仅被调用 1 次
    expect(refreshClientMock.history.post.filter((r) => r.url === '/users/refresh-token')).toHaveLength(1);
  });

  // 用例 8：refresh 失败 — refresh 返回 401 → 排队请求全部 reject + Modal.confirm 被调用 + authStore.logout() 被调用
  it('refresh 失败：refresh 返回 401 → 排队请求全部 reject + Modal.confirm 被调用 + authStore.logout() 被调用', async () => {
    mockAccessToken = 'old-access';
    mockRefreshToken = 'old-refresh';
    const mod = await import('@/services/api-client');

    apiClientMock.onGet('/x').replyOnce(401, { code: 401, message: '未授权', data: null });
    apiClientMock.onGet('/y').replyOnce(401, { code: 401, message: '未授权', data: null });
    // refresh 也返回 401
    refreshClientMock.onPost('/users/refresh-token').reply(401, {
      code: 401,
      message: 'refresh token 已过期',
      data: null,
    });

    const results = await Promise.allSettled([
      mod.apiClient.get('/x'),
      mod.apiClient.get('/y'),
    ]);
    // 两个请求都应被 reject
    expect(results[0].status).toBe('rejected');
    expect(results[1].status).toBe('rejected');

    // Modal.confirm 被调用，含 title/content/onOk
    expect(Modal.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '登录已过期',
        content: '请重新登录',
        onOk: expect.any(Function),
      }),
    );

    // onOk 回调中触发 logout（验证回调正确性）
    const confirmArgs = vi.mocked(Modal.confirm).mock.calls[0][0];
    confirmArgs.onOk?.();
    expect(mockLogout).toHaveBeenCalled();
  });
});
