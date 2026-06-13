import type { PageResult } from './typing';
import { request } from 'umi';

/** 卡牌稀有度枚举 */
export type CardRarity = 'N' | 'R' | 'SR' | 'SSR' | 'SP';

/** 卡牌状态枚举 */
export type CardTemplateStatus = 'ACTIVE' | 'INACTIVE';

/** 星级图片响应 */
export interface StarImageResponse {
  starLevel: number;
  imageUrl: string;
}

/** 卡牌模板响应（详情，含星级图片） */
export interface CardTemplateResponse {
  id: number;
  ipSeriesId: number;
  ipSeriesName: string;
  code: string;
  name: string;
  rarity: CardRarity;
  description: string;
  status: CardTemplateStatus;
  starImages: StarImageResponse[];
  createdAt: string;
  updatedAt: string;
}

/**
 * 卡牌模板响应（列表，不含星级图片）
 * 注意：description 字段后端返回但列表页不展示，保留供未来扩展（如鼠标 hover 提示）
 */
export interface CardTemplateListResponse {
  id: number;
  ipSeriesId: number;
  ipSeriesName: string;
  code: string;
  name: string;
  rarity: CardRarity;
  description: string;
  status: CardTemplateStatus;
  createdAt: string;
  updatedAt: string;
}

/** 星级图片请求 */
export interface StarImageRequest {
  starLevel: number;
  imageUrl: string;
}

/** 创建卡牌模板请求 */
export interface CreateCardTemplateRequest {
  ipSeriesId: number;
  code: string;
  name: string;
  rarity: CardRarity;
  description?: string;
  status: CardTemplateStatus;
  starImages?: StarImageRequest[];
}

/** 更新卡牌模板请求（仅基础字段，不含星级图片） */
export interface UpdateCardTemplateRequest {
  code?: string;
  name?: string;
  rarity?: CardRarity;
  description?: string;
  status?: CardTemplateStatus;
}

/** 添加/替换星级图片请求 */
export interface AddStarImageRequest {
  starLevel: number;
  imageUrl: string;
}

/** 分页查询参数 */
export interface CardTemplateQuery {
  name?: string;
  ipSeriesId?: number;
  rarity?: string;
  status?: string;
  page?: number;
  size?: number;
}

/** 分页查询卡牌模板列表 */
export async function listCardTemplates(
  params: CardTemplateQuery,
): Promise<PageResult<CardTemplateListResponse>> {
  return request<PageResult<CardTemplateListResponse>>('/api/admin/card-templates', {
    method: 'GET',
    params,
  });
}

/** 查询卡牌模板详情（含星级图片） */
export async function getCardTemplateById(
  id: number,
): Promise<CardTemplateResponse> {
  return request<CardTemplateResponse>(`/api/admin/card-templates/${id}`, {
    method: 'GET',
  });
}

/** 创建卡牌模板 */
export async function createCardTemplate(
  data: CreateCardTemplateRequest,
): Promise<CardTemplateResponse> {
  return request<CardTemplateResponse>('/api/admin/card-templates', {
    method: 'POST',
    data,
  });
}

/** 更新卡牌模板基础信息 */
export async function updateCardTemplate(
  id: number,
  data: UpdateCardTemplateRequest,
): Promise<CardTemplateResponse> {
  return request<CardTemplateResponse>(`/api/admin/card-templates/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 添加/替换单张星级图片 */
export async function addOrUpdateStarImage(
  id: number,
  data: AddStarImageRequest,
): Promise<CardTemplateResponse> {
  return request<CardTemplateResponse>(
    `/api/admin/card-templates/${id}/star-images`,
    {
      method: 'POST',
      data,
    },
  );
}

/** 删除卡牌模板（软删除） */
export async function deleteCardTemplate(id: number): Promise<void> {
  return request<void>(`/api/admin/card-templates/${id}`, {
    method: 'DELETE',
  });
}
