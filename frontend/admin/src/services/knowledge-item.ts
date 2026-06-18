import type { PageResult } from './typing';
// @ts-ignore — UmiJS 构建时生成类型
import { request } from '@umijs/max';

/** 知识条目响应 */
export interface KnowledgeItemResponse {
  id: number;
  title: string;
  content: string;
  contentHtml: string;
  coverImageFileId: number | null;
  coverImageUrl: string | null;
  tags: string[];
  categoryIds: number[];
  sortOrder: number;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: number;
  updatedAt: number;
}

/** 创建知识条目请求 */
export interface CreateKnowledgeItemRequest {
  title: string;
  content: string;
  coverImageFileId?: number | null;
  tags?: string[];
  sortOrder?: number;
  categoryIds: number[];
}

/** 更新知识条目请求 */
export interface UpdateKnowledgeItemRequest {
  title?: string;
  content?: string;
  coverImageFileId?: number | null;
  tags?: string[];
  sortOrder?: number;
}

/** 分页查询参数 */
export interface KnowledgeItemQuery {
  keyword?: string;
  categoryId?: number;
  tag?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  sort?: 'createdAt' | 'updatedAt' | 'sortOrder';
  order?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

/** 分类表单数据 */
export interface CategoryFormData {
  categoryIds: number[];
}

/** 批量排序项 */
export interface BatchSortItem {
  id: number;
  sortOrder: number;
}

/** 分页查询知识条目列表 */
export async function listKnowledgeItems(params: KnowledgeItemQuery) {
  return request<PageResult<KnowledgeItemResponse>>('/api/admin/knowledge-items', {
    method: 'GET',
    params,
  });
}

/** 查询知识条目详情 */
export async function getKnowledgeItemById(id: number) {
  return request<KnowledgeItemResponse>(`/api/admin/knowledge-items/${id}`, {
    method: 'GET',
  });
}

/** 创建知识条目 */
export async function createKnowledgeItem(data: CreateKnowledgeItemRequest) {
  return request<KnowledgeItemResponse>('/api/admin/knowledge-items', {
    method: 'POST',
    data,
  });
}

/** 更新知识条目 */
export async function updateKnowledgeItem(id: number, data: UpdateKnowledgeItemRequest) {
  return request<KnowledgeItemResponse>(`/api/admin/knowledge-items/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 删除知识条目（软删除） */
export async function deleteKnowledgeItem(id: number) {
  return request<void>(`/api/admin/knowledge-items/${id}`, {
    method: 'DELETE',
  });
}

/** 查询知识条目关联的分类 */
export async function getKnowledgeItemCategories(id: number) {
  return request<number[]>(`/api/admin/knowledge-items/${id}/categories`, {
    method: 'GET',
  });
}

/** 更新知识条目分类关联 */
export async function updateKnowledgeItemCategories(id: number, data: CategoryFormData) {
  return request<void>(`/api/admin/knowledge-items/${id}/categories`, {
    method: 'PUT',
    data,
  });
}

/** 批量启用 */
export async function batchActivate(ids: number[]) {
  return request<void>('/api/admin/knowledge-items/batch-activate', {
    method: 'PUT',
    data: { ids },
  });
}

/** 批量禁用 */
export async function batchDeactivate(ids: number[]) {
  return request<void>('/api/admin/knowledge-items/batch-deactivate', {
    method: 'PUT',
    data: { ids },
  });
}

/** 批量排序 */
export async function batchSort(items: BatchSortItem[]) {
  return request<void>('/api/admin/knowledge-items/batch-sort', {
    method: 'PUT',
    data: { items },
  });
}
