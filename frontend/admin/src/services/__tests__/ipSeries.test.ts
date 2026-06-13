import {
  createIpSeries,
  deleteIpSeries,
  listIpSeries,
  updateIpSeries,
} from '../ipSeries';

/** 模拟 umi 的 request 函数 */
jest.mock('umi', () => ({
  request: jest.fn(),
}));

const { request } = require('umi');

/** 构造模拟 IP 系列响应 */
function mockIpSeries(overrides: Record<string, any> = {}) {
  return {
    id: 1,
    code: 'IP001',
    name: '测试 IP',
    description: '测试描述',
    coverImageUrl: 'http://localhost:8083/static/ip-series/test.png',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-02T00:00:00',
    ...overrides,
  };
}

/** 构造模拟分页结果 */
function mockPageResult(content: any[], overrides: Record<string, any> = {}) {
  return {
    content,
    totalElements: content.length,
    pageNumber: 0,
    pageSize: 20,
    totalPages: 1,
    ...overrides,
  };
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe('listIpSeries', () => {
  it('应使用正确的 query 参数调用 GET 请求', async () => {
    const pageResult = mockPageResult([mockIpSeries()]);
    request.mockResolvedValue(pageResult);

    const result = await listIpSeries({ name: '测试', status: 'ACTIVE', page: 0, size: 10 });

    expect(request).toHaveBeenCalledWith('/api/admin/ip-series', {
      method: 'GET',
      params: { name: '测试', status: 'ACTIVE', page: 0, size: 10 },
    });
    expect(result).toEqual(pageResult);
  });

  it('不传可选参数时应正常调用', async () => {
    const pageResult = mockPageResult([]);
    request.mockResolvedValue(pageResult);

    const result = await listIpSeries({});

    expect(request).toHaveBeenCalledWith('/api/admin/ip-series', {
      method: 'GET',
      params: {},
    });
    expect(result.content).toEqual([]);
  });

  it('请求失败时应向上抛出异常', async () => {
    const error = new Error('网络错误');
    request.mockRejectedValue(error);

    await expect(listIpSeries({})).rejects.toThrow('网络错误');
  });
});

describe('createIpSeries', () => {
  it('应使用正确的 body 调用 POST 请求', async () => {
    const req = {
      code: 'IP002',
      name: '新 IP',
      description: '描述',
      coverImageUrl: 'http://localhost:8083/static/cover.png',
      status: 'ACTIVE' as const,
    };
    const created = mockIpSeries({ id: 2, code: 'IP002', name: '新 IP' });
    request.mockResolvedValue(created);

    const result = await createIpSeries(req);

    expect(request).toHaveBeenCalledWith('/api/admin/ip-series', {
      method: 'POST',
      data: req,
    });
    expect(result).toEqual(created);
  });

  it('可选字段不传时应正常调用', async () => {
    const req = {
      code: 'IP003',
      name: '最简 IP',
      status: 'INACTIVE' as const,
    };
    const created = mockIpSeries({ id: 3, code: 'IP003', name: '最简 IP', status: 'INACTIVE' });
    request.mockResolvedValue(created);

    const result = await createIpSeries(req);

    expect(request).toHaveBeenCalledWith('/api/admin/ip-series', {
      method: 'POST',
      data: req,
    });
    expect(result.status).toBe('INACTIVE');
  });

  it('创建失败时应向上抛出异常', async () => {
    request.mockRejectedValue(new Error('编码已存在'));

    await expect(
      createIpSeries({ code: 'IP001', name: '重复编码', status: 'ACTIVE' }),
    ).rejects.toThrow('编码已存在');
  });
});

describe('updateIpSeries', () => {
  it('应使用正确的 body 调用 PUT 请求', async () => {
    const req = {
      name: '更新后的名称',
      description: '更新后的描述',
    };
    const updated = mockIpSeries({ id: 1, name: '更新后的名称' });
    request.mockResolvedValue(updated);

    const result = await updateIpSeries(1, req);

    expect(request).toHaveBeenCalledWith('/api/admin/ip-series/1', {
      method: 'PUT',
      data: req,
    });
    expect(result.name).toBe('更新后的名称');
  });

  it('编辑不存在的记录应抛出异常', async () => {
    request.mockRejectedValue(new Error('IP 系列不存在'));

    await expect(updateIpSeries(999, { name: '不存在' })).rejects.toThrow('IP 系列不存在');
  });
});

describe('deleteIpSeries', () => {
  it('应使用正确的 id 调用 DELETE 请求', async () => {
    request.mockResolvedValue(undefined);

    await deleteIpSeries(1);

    expect(request).toHaveBeenCalledWith('/api/admin/ip-series/1', {
      method: 'DELETE',
    });
  });

  it('删除有关联卡牌的 IP 系列应抛出异常', async () => {
    request.mockRejectedValue(new Error('该 IP 下已有卡牌，不允许删除'));

    await expect(deleteIpSeries(1)).rejects.toThrow('该 IP 下已有卡牌，不允许删除');
  });

  it('删除不存在的记录应抛出异常', async () => {
    request.mockRejectedValue(new Error('IP 系列不存在'));

    await expect(deleteIpSeries(999)).rejects.toThrow('IP 系列不存在');
  });
});
