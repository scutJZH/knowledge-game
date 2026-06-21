import { request } from '@umijs/max';

/** 定时任务执行日志列表响应项 */
export interface ScheduledTaskLogItem {
  id: number;
  taskName: string;
  taskDisplay: string;
  executedAt: number;
  durationMs: number;
  totalCount: number;
  successCount: number;
  failureCount: number;
  failureDetails?: Array<{
    recycleBinId: number;
    resourceType: string;
    name: string;
    reason: string;
  }>;
  status: string;
}

/** 分页响应 */
export interface ScheduledTaskLogPage {
  content: ScheduledTaskLogItem[];
  totalElements: number;
  pageNumber: number;
  pageSize: number;
  totalPages: number;
}

/** 列表查询参数 */
export interface ScheduledTaskLogQuery {
  taskName?: string;
  page?: number;
  size?: number;
}

/**
 * 查询定时任务执行日志分页列表
 */
export async function listTaskLogs(params: ScheduledTaskLogQuery) {
  return request<{
    code: number;
    data: ScheduledTaskLogPage;
    message: string;
  }>('/api/admin/scheduled-task-logs', {
    method: 'GET',
    params,
  });
}
