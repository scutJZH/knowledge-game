import type { PageResult } from './typing';
// @ts-ignore — UmiJS 构建时生成类型
import { request } from '@umijs/max';

/** 回收站列表项 */
export interface RecycleBinItem {
  id: number;
  resourceType: string;
  resourceTypeDisplay: string;
  originalId: number;
  originalName: string;
  originalCreatedAt: number | null;
  originalUpdatedAt: number | null;
  originalCreatedBy: string | null;
  originalUpdatedBy: string | null;
  deletedBy: string;
  deletedAt: number;
  restoreDeadline: number;
  daysUntilPurge: number;
}

/** 已接入的资源类型 */
export interface SupportedType {
  type: string;
  displayName: string;
}

/** 列表查询参数 */
export interface RecycleBinListParams {
  page?: number;
  size?: number;
  resourceType?: string;
  keyword?: string;
  sort?: string;
  order?: 'asc' | 'desc';
}

/** 获取回收站列表 */
export async function fetchRecycleBinList(params: RecycleBinListParams) {
  return request<PageResult<RecycleBinItem>>('/api/admin/recycle-bin', {
    method: 'GET',
    params,
  });
}

/** 获取已接入回收站的资源类型 */
export async function fetchSupportedTypes() {
  return request<SupportedType[]>('/api/admin/recycle-bin/supported-types', {
    method: 'GET',
  });
}

/** 单条恢复 */
export async function restoreItem(id: number): Promise<void> {
  return request<void>(`/api/admin/recycle-bin/${id}/restore`, {
    method: 'POST',
  });
}

/** 批量恢复失败条目 */
export interface BatchRestoreFailure {
  id: number;
  errorMessage: string;
}

/** 批量恢复结果 */
export interface BatchRestoreResult {
  successIds: number[];
  failures: BatchRestoreFailure[];
}

/** 批量恢复 */
export async function batchRestoreItems(ids: number[]): Promise<BatchRestoreResult> {
  return request<BatchRestoreResult>('/api/admin/recycle-bin/batch-restore', {
    method: 'POST',
    data: { ids },
  });
}

/** 单条永久删除 */
export async function purgeItem(id: number): Promise<void> {
  return request<void>(`/api/admin/recycle-bin/${id}`, {
    method: 'DELETE',
  });
}

/** 批量永久删除失败条目 */
export interface BatchPurgeFailure {
  id: number;
  errorMessage: string;
}

/** 批量永久删除结果 */
export interface BatchPurgeResult {
  successIds: number[];
  failures: BatchPurgeFailure[];
}

/** 批量永久删除 */
export async function batchPurgeItems(ids: number[]): Promise<BatchPurgeResult> {
  return request<BatchPurgeResult>('/api/admin/recycle-bin/batch-purge', {
    method: 'POST',
    data: { ids },
  });
}
