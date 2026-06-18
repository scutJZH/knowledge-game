import type { PageResult } from './typing';
import { request } from 'umi';

/** IP 系列响应类型 */
export interface IpSeriesResponse {
  id: number;
  code: string;
  name: string;
  description: string;
  coverImageFileId: number | null;
  coverImageUrl: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: number;
  updatedAt: number;
}

/** 创建 IP 系列请求 */
export interface CreateIpSeriesRequest {
  code: string;
  name: string;
  description?: string;
  coverImageFileId?: number;
  status: 'ACTIVE' | 'INACTIVE';
}

/**
 * 更新 IP 系列请求（三态语义）
 * - 字段缺失（undefined）：不更新
 * - 字段为 null：清空（仅可清空字段：description / coverImageFileId）
 * - 字段有值：更新
 * 必填字段（code/name/status）不可清空
 */
export interface UpdateIpSeriesRequest {
  code?: string;
  name?: string;
  description?: string | null;
  coverImageFileId?: number | null;
  status?: 'ACTIVE' | 'INACTIVE';
}

/** 分页查询参数 */
export interface IpSeriesQuery {
  name?: string;
  code?: string;
  status?: string;
  sort?: string;
  order?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

/** 分页查询 IP 系列列表 */
export async function listIpSeries(
  params: IpSeriesQuery,
): Promise<PageResult<IpSeriesResponse>> {
  return request<PageResult<IpSeriesResponse>>('/api/admin/ip-series', {
    method: 'GET',
    params,
  });
}

/** 创建 IP 系列 */
export async function createIpSeries(
  data: CreateIpSeriesRequest,
): Promise<IpSeriesResponse> {
  return request<IpSeriesResponse>('/api/admin/ip-series', {
    method: 'POST',
    data,
  });
}

/** 更新 IP 系列 */
export async function updateIpSeries(
  id: number,
  data: UpdateIpSeriesRequest,
): Promise<IpSeriesResponse> {
  return request<IpSeriesResponse>(`/api/admin/ip-series/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 删除 IP 系列 */
export async function deleteIpSeries(id: number): Promise<void> {
  return request<void>(`/api/admin/ip-series/${id}`, {
    method: 'DELETE',
  });
}
