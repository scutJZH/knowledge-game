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
