/** 模拟 @umijs/max 的 request 函数 */
jest.mock('@umijs/max', () => ({
  request: jest.fn(),
}));

const { request } = require('@umijs/max');

import {
  getTree,
  getById,
  create,
  update,
  move,
  deleteCategory,
  batchSort,
} from '../knowledge-category';

beforeEach(() => {
  (request as jest.Mock).mockReset();
});

describe('knowledge-category service', () => {
  it('getTree 调用 GET /api/admin/knowledge-categories/tree', async () => {
    (request as jest.Mock).mockResolvedValue([]);
    await getTree();
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories/tree');
  });

  it('getById 调用 GET /api/admin/knowledge-categories/{id}', async () => {
    (request as jest.Mock).mockResolvedValue({});
    await getById(1);
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories/1');
  });

  it('create 调用 POST', async () => {
    (request as jest.Mock).mockResolvedValue({});
    await create({ name: '测试' });
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories', {
      method: 'POST',
      data: { name: '测试' },
    });
  });

  it('update 调用 PUT', async () => {
    (request as jest.Mock).mockResolvedValue({});
    await update(1, { name: '新名称' });
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories/1', {
      method: 'PUT',
      data: { name: '新名称' },
    });
  });

  it('move 调用 PUT move 端点', async () => {
    (request as jest.Mock).mockResolvedValue({});
    await move(1, 2);
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories/1/move', {
      method: 'PUT',
      data: { newParentId: 2 },
    });
  });

  it('move 移到顶级传 null', async () => {
    (request as jest.Mock).mockResolvedValue({});
    await move(1, null);
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories/1/move', {
      method: 'PUT',
      data: { newParentId: null },
    });
  });

  it('deleteCategory 调用 DELETE', async () => {
    (request as jest.Mock).mockResolvedValue(undefined);
    await deleteCategory(1);
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories/1', {
      method: 'DELETE',
    });
  });

  it('batchSort 调用 PUT batch-sort', async () => {
    (request as jest.Mock).mockResolvedValue(undefined);
    await batchSort([{ id: 1, sortOrder: 0 }, { id: 2, sortOrder: 1 }]);
    expect((request as jest.Mock)).toHaveBeenCalledWith('/api/admin/knowledge-categories/batch-sort', {
      method: 'PUT',
      data: { items: [{ id: 1, sortOrder: 0 }, { id: 2, sortOrder: 1 }] },
    });
  });
});
