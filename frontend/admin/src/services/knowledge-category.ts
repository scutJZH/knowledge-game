// @ts-ignore — UmiJS 构建时生成类型，IDE 无法解析虚拟路径
import { request } from '@umijs/max';

/** 分类树节点 */
export interface CategoryTreeNode {
  id: number;
  parentId: number | null;
  name: string;
  status: string;
  iconUrl: string | null;
  color: string | null;
  sortOrder: number;
  children?: CategoryTreeNode[];
}

/** 分类详情 */
export interface CategoryDetail {
  id: number;
  parentId: number | null;
  name: string;
  description: string | null;
  iconUrl: string | null;
  color: string | null;
  coverImageUrl: string | null;
  sortOrder: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

/** 创建/编辑表单数据 */
export interface CategoryFormData {
  parentId?: number | null;
  name: string;
  description?: string;
  iconUrl?: string;
  color?: string;
  coverImageUrl?: string;
  sortOrder?: number;
}

/** 批量排序项 */
export interface BatchSortItem {
  id: number;
  sortOrder: number;
}

/** 获取分类树 */
export async function getTree(): Promise<CategoryTreeNode[]> {
  return request('/api/admin/knowledge-categories/tree');
}

/** 获取分类详情 */
export async function getById(id: number): Promise<CategoryDetail> {
  return request(`/api/admin/knowledge-categories/${id}`);
}

/** 创建分类 */
export async function create(data: CategoryFormData): Promise<CategoryDetail> {
  return request('/api/admin/knowledge-categories', {
    method: 'POST',
    data,
  });
}

/** 更新分类 */
export async function update(id: number, data: CategoryFormData): Promise<CategoryDetail> {
  return request(`/api/admin/knowledge-categories/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 移动分类 */
export async function move(id: number, newParentId: number | null): Promise<CategoryDetail> {
  return request(`/api/admin/knowledge-categories/${id}/move`, {
    method: 'PUT',
    data: { newParentId },
  });
}

/** 删除分类 */
export async function deleteCategory(id: number): Promise<void> {
  return request(`/api/admin/knowledge-categories/${id}`, {
    method: 'DELETE',
  });
}

/** 批量排序 */
export async function batchSort(items: BatchSortItem[]): Promise<void> {
  return request('/api/admin/knowledge-categories/batch-sort', {
    method: 'PUT',
    data: { items },
  });
}
