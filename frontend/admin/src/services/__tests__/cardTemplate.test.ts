import {
  createCardTemplate,
  deleteCardTemplate,
  getCardTemplateById,
  listCardTemplates,
  updateCardTemplate,
} from '../cardTemplate';

/** 模拟 umi 的 request 函数 */
jest.mock('umi', () => ({
  request: jest.fn(),
}));

const { request } = require('umi');

beforeEach(() => {
  jest.clearAllMocks();
});

describe('listCardTemplates', () => {
  it('应该以 GET 方法调用分页查询，并传递正确的 query params', async () => {
    const pageResult = { content: [], totalElements: 0 };
    request.mockResolvedValue(pageResult);

    const result = await listCardTemplates({ name: 'test', rarity: 'SSR', status: 'ACTIVE', page: 0, size: 20 });

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates', {
      method: 'GET',
      params: { name: 'test', rarity: 'SSR', status: 'ACTIVE', page: 0, size: 20 },
    });
    expect(result).toEqual(pageResult);
  });

  it('空参数时应能正常调用', async () => {
    const pageResult = { content: [], totalElements: 0 };
    request.mockResolvedValue(pageResult);

    const result = await listCardTemplates({});

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates', {
      method: 'GET',
      params: {},
    });
    expect(result.content).toEqual([]);
  });

  it('应传递 ipSeriesId 筛选参数', async () => {
    request.mockResolvedValue({ content: [], totalElements: 0 });

    await listCardTemplates({ ipSeriesId: 1 });

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates', {
      method: 'GET',
      params: { ipSeriesId: 1 },
    });
  });

  it('请求失败时应向上抛出异常', async () => {
    request.mockRejectedValue(new Error('网络错误'));

    await expect(listCardTemplates({})).rejects.toThrow('网络错误');
  });
});

describe('getCardTemplateById', () => {
  it('应以 GET 方法调用带 ID 的详情接口', async () => {
    const mockData = { id: 1, code: 'CT001', name: '测试卡牌', imageUrl: 'https://example.com/card.png' };
    request.mockResolvedValue(mockData);

    const result = await getCardTemplateById(1);

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates/1', {
      method: 'GET',
    });
    expect(result).toEqual(mockData);
  });

  it('查询不存在的记录应抛出异常', async () => {
    request.mockRejectedValue(new Error('卡牌模板不存在'));

    await expect(getCardTemplateById(999)).rejects.toThrow('卡牌模板不存在');
  });
});

describe('createCardTemplate', () => {
  it('应以 POST 方法提交完整创建数据（含图片 URL）', async () => {
    const data = {
      ipSeriesId: 1,
      code: 'CT001',
      name: '测试卡牌',
      rarity: 'SSR' as const,
      description: '描述',
      status: 'ACTIVE' as const,
      imageUrl: 'https://example.com/card.png',
    };
    const mockResponse = { id: 1, ...data };
    request.mockResolvedValue(mockResponse);

    const result = await createCardTemplate(data);

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates', {
      method: 'POST',
      data,
    });
    expect(result).toEqual(mockResponse);
  });

  it('不带图片时也应能正常创建', async () => {
    const data = {
      ipSeriesId: 1,
      code: 'CT002',
      name: '无图卡牌',
      rarity: 'N' as const,
      status: 'ACTIVE' as const,
    };
    request.mockResolvedValue({ id: 2, ...data });

    await createCardTemplate(data);

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates', {
      method: 'POST',
      data,
    });
  });

  it('code 重复时应抛出异常', async () => {
    request.mockRejectedValue(new Error('卡牌编码已存在'));

    await expect(
      createCardTemplate({ ipSeriesId: 1, code: 'CT001', name: '重复编码', rarity: 'N' as const, status: 'ACTIVE' as const }),
    ).rejects.toThrow('卡牌编码已存在');
  });
});

describe('updateCardTemplate', () => {
  it('应以 PUT 方法提交基础字段更新', async () => {
    const data = { name: '更新名称', description: '新描述' };
    request.mockResolvedValue({ id: 1, name: '更新名称' });

    await updateCardTemplate(1, data);

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates/1', {
      method: 'PUT',
      data,
    });
  });

  it('应支持仅更新单个字段', async () => {
    request.mockResolvedValue({ id: 1, status: 'INACTIVE' });

    await updateCardTemplate(1, { status: 'INACTIVE' });

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates/1', {
      method: 'PUT',
      data: { status: 'INACTIVE' },
    });
  });

  it('应支持更新图片 URL', async () => {
    request.mockResolvedValue({ id: 1, imageUrl: 'https://example.com/new-card.png' });

    await updateCardTemplate(1, { imageUrl: 'https://example.com/new-card.png' });

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates/1', {
      method: 'PUT',
      data: { imageUrl: 'https://example.com/new-card.png' },
    });
  });

  it('更新不存在的记录应抛出异常', async () => {
    request.mockRejectedValue(new Error('卡牌模板不存在'));

    await expect(updateCardTemplate(999, { name: '不存在' })).rejects.toThrow('卡牌模板不存在');
  });
});

describe('deleteCardTemplate', () => {
  it('应以 DELETE 方法调用删除接口', async () => {
    request.mockResolvedValue(undefined);

    await deleteCardTemplate(1);

    expect(request).toHaveBeenCalledWith('/api/admin/card-templates/1', {
      method: 'DELETE',
    });
  });

  it('删除有关联用户收集的卡牌应抛出异常', async () => {
    request.mockRejectedValue(new Error('已有用户收集，不允许删除'));

    await expect(deleteCardTemplate(1)).rejects.toThrow('已有用户收集，不允许删除');
  });

  it('删除不存在的记录应抛出异常', async () => {
    request.mockRejectedValue(new Error('卡牌模板不存在'));

    await expect(deleteCardTemplate(999)).rejects.toThrow('卡牌模板不存在');
  });
});
