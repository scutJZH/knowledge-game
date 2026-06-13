import { getUploadCredential, uploadFile } from '../fileUpload';

/** 模拟 umi 的 request 函数 */
jest.mock('umi', () => ({
  request: jest.fn(),
}));

/** 模拟 token 工具 */
jest.mock('@/utils/token', () => ({
  getUserInfo: jest.fn(() => ({ id: 1, username: 'admin', nickname: '管理员', role: 'ADMIN' })),
}));

const { request } = require('umi');
const { getUserInfo } = require('@/utils/token');

/** 模拟 fetch */
const mockFetch = jest.fn();
global.fetch = mockFetch;

function mockFetchResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response;
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('getUploadCredential', () => {
  it('应使用默认 count=1 调用凭证接口', async () => {
    const credential = { token: 'token-xxx', uploadUrl: 'http://localhost:8083/api/file/upload' };
    request.mockResolvedValue(credential);

    const result = await getUploadCredential('IP_SERIES');

    expect(request).toHaveBeenCalledWith(
      '/api/admin/upload-credential?bizType=IP_SERIES&count=1',
      { method: 'GET' },
    );
    expect(result).toEqual(credential);
  });

  it('应支持自定义 count 参数', async () => {
    const credential = { token: 'token-yyy', uploadUrl: 'http://localhost:8083/api/file/batch-upload' };
    request.mockResolvedValue(credential);

    const result = await getUploadCredential('IP_SERIES', 5);

    expect(request).toHaveBeenCalledWith(
      '/api/admin/upload-credential?bizType=IP_SERIES&count=5',
      { method: 'GET' },
    );
    expect(result.token).toBe('token-yyy');
  });

  it('凭证获取失败时应抛出异常', async () => {
    request.mockRejectedValue(new Error('不支持的业务类型'));

    await expect(getUploadCredential('INVALID_TYPE')).rejects.toThrow('不支持的业务类型');
  });
});

describe('uploadFile', () => {
  const token = 'upload-token-123';
  const uploadUrl = 'http://localhost:8083/api/file/upload';
  const file = new File(['test'], 'test.png', { type: 'image/png' });
  const userId = 1;

  it('应使用凭证 token 鉴权直传文件', async () => {
    mockFetch.mockResolvedValue(
      mockFetchResponse(200, {
        code: 200,
        data: { fileId: 'file-001', url: '/static/ip-series/20260612/abc.png' },
      }),
    );

    const result = await uploadFile(token, uploadUrl, file, userId);

    // 验证 fetch 调用参数
    expect(mockFetch).toHaveBeenCalledWith(uploadUrl, {
      method: 'POST',
      headers: {
        'X-Upload-Token': token,
        'X-User-Id': String(userId),
      },
      body: expect.any(FormData),
    });

    // 验证返回完整 URL
    expect(result).toBe('http://localhost:8083/static/ip-series/20260612/abc.png');
  });

  it('HTTP 非 OK 状态码应抛出异常', async () => {
    mockFetch.mockResolvedValue(
      mockFetchResponse(401, 'Unauthorized'),
    );

    await expect(uploadFile('bad-token', uploadUrl, file, userId)).rejects.toThrow();
  });

  it('业务返回非 200 code 应抛出异常', async () => {
    mockFetch.mockResolvedValue(
      mockFetchResponse(200, {
        code: 500,
        message: '文件存储失败',
        data: null,
      }),
    );

    await expect(uploadFile(token, uploadUrl, file, userId)).rejects.toThrow('文件存储失败');
  });

  it('应正确拼接完整 URL（从 uploadUrl 提取 base）', async () => {
    // 测试不同端口的 uploadUrl
    const customUploadUrl = 'https://files.example.com:8443/api/file/upload';
    mockFetch.mockResolvedValue(
      mockFetchResponse(200, {
        code: 200,
        data: { fileId: 'file-002', url: '/static/ip-series/20260612/xyz.webp' },
      }),
    );

    const result = await uploadFile(token, customUploadUrl, file, userId);

    expect(result).toBe('https://files.example.com:8443/static/ip-series/20260612/xyz.webp');
  });

  it('fetch 网络异常应抛出异常', async () => {
    mockFetch.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(uploadFile(token, uploadUrl, file, userId)).rejects.toThrow('Failed to fetch');
  });
});
