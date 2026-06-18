import type { PageResult } from './typing';
import { request } from 'umi';

/** 题型枚举 */
export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_BLANK';

/** 题目选项 */
export interface QuestionOption {
  key: string;
  content: string;
}

/** 题目响应 */
export interface QuestionResponse {
  id: number;
  type: QuestionType;
  content: string;
  options: QuestionOption[] | null;
  answer: string;
  explanation: string;
  difficulty: 1 | 2 | 3;
  tags: string[];
  status: 'ACTIVE' | 'INACTIVE';
  categoryIds: number[];
  createdAt: number;
  updatedAt: number;
}

/** 创建题目请求（含 categoryIds，一次性提交） */
export interface CreateQuestionRequest {
  type: QuestionType;
  content: string;
  options: QuestionOption[] | null;
  answer: string;
  difficulty: 1 | 2 | 3;
  explanation?: string;
  tags?: string[];
  categoryIds?: number[];
}

/** 更新题目请求（不含 type 和 categoryIds，分类单独 PUT）
 *  explanation / tags 支持 null 表示清空（REQ-88 三态语义）。
 *  字段缺失（undefined）= 不更新；显式 null = 清空；有值 = 更新为新值。
 */
export interface UpdateQuestionRequest {
  content?: string;
  options?: QuestionOption[] | null;
  answer?: string;
  difficulty?: 1 | 2 | 3;
  explanation?: string | null;
  tags?: string[] | null;
}

/** 分页查询参数 */
export interface QuestionQuery {
  keyword?: string;
  type?: QuestionType;
  difficulty?: 1 | 2 | 3;
  categoryId?: number;
  tag?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  sort?: 'createdAt' | 'updatedAt' | 'difficulty';
  order?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

/** 导入失败明细 */
export interface ImportFailDetail {
  row: number;
  reason: string;
}

/** 导入结果 */
export interface QuestionImportResult {
  totalCount: number;
  successCount: number;
  failCount: number;
  failDetails: ImportFailDetail[];
}

/** 难度常量映射：后端 Integer（1/2/3）↔ 前端展示中文 + Tag 颜色 */
export const DIFFICULTY_OPTIONS = [
  { value: 1, label: '简单', color: 'green' },
  { value: 2, label: '中等', color: 'gold' },
  { value: 3, label: '困难', color: 'red' },
] as const;

/** 题型常量映射：后端 String 枚举 ↔ 前端展示中文 + Tag 颜色 */
export const QUESTION_TYPE_OPTIONS = [
  { value: 'SINGLE_CHOICE', label: '单选', color: 'blue' },
  { value: 'MULTIPLE_CHOICE', label: '多选', color: 'purple' },
  { value: 'TRUE_FALSE', label: '判断', color: 'cyan' },
  { value: 'FILL_BLANK', label: '填空', color: 'orange' },
] as const;

/** 状态常量映射：后端 String ↔ 前端展示中文 + Tag 颜色 */
export const QUESTION_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '启用', color: 'green' },
  { value: 'INACTIVE', label: '停用', color: 'default' },
] as const;

/** 分页查询题目列表 */
export async function listQuestions(
  params: QuestionQuery,
): Promise<PageResult<QuestionResponse>> {
  return request<PageResult<QuestionResponse>>('/api/admin/questions', {
    method: 'GET',
    params,
  });
}

/** 查询题目详情 */
export async function getQuestionById(id: number): Promise<QuestionResponse> {
  return request<QuestionResponse>(`/api/admin/questions/${id}`, {
    method: 'GET',
  });
}

/** 创建题目 */
export async function createQuestion(
  data: CreateQuestionRequest,
): Promise<QuestionResponse> {
  return request<QuestionResponse>('/api/admin/questions', {
    method: 'POST',
    data,
  });
}

/** 更新题目 */
export async function updateQuestion(
  id: number,
  data: UpdateQuestionRequest,
): Promise<QuestionResponse> {
  return request<QuestionResponse>(`/api/admin/questions/${id}`, {
    method: 'PUT',
    data,
  });
}

/** 更新题目的分类关联（全量替换） */
export async function updateQuestionCategories(
  id: number,
  categoryIds: number[],
): Promise<void> {
  return request<void>(`/api/admin/questions/${id}/categories`, {
    method: 'PUT',
    data: { categoryIds },
  });
}

/** 批量启用 */
export async function batchActivate(ids: number[]): Promise<void> {
  return request<void>('/api/admin/questions/batch-activate', {
    method: 'PUT',
    data: { ids },
  });
}

/** 批量停用 */
export async function batchDeactivate(ids: number[]): Promise<void> {
  return request<void>('/api/admin/questions/batch-deactivate', {
    method: 'PUT',
    data: { ids },
  });
}

/** 下载导入模板（返回 Blob） */
export async function downloadImportTemplate(): Promise<Blob> {
  return request('/api/admin/questions/import-template', {
    method: 'GET',
    responseType: 'blob',
  });
}

/** Excel 批量导入题目 */
export async function importQuestions(
  file: File,
): Promise<QuestionImportResult> {
  const formData = new FormData();
  formData.append('file', file);
  return request<QuestionImportResult>('/api/admin/questions/import', {
    method: 'POST',
    data: formData,
  });
}
