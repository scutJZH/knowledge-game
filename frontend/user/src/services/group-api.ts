import { apiClient } from './api-client';
import type { StudyGroupListResponse, CreateStudyGroupRequest } from '@/types/group';

/** 查询当前用户已加入的群组列表 */
export function listMyGroups() {
  return apiClient.get<never, StudyGroupListResponse[]>('/study-groups');
}

/** 创建群组 */
export function createGroup(data: CreateStudyGroupRequest) {
  return apiClient.post<never, StudyGroupListResponse>('/study-groups', data);
}

/** 凭邀请码加入群组 */
export function joinByInvite(inviteCode: string) {
  return apiClient.post<never, { id: number }>('/study-groups/join-by-invite', { inviteCode });
}

/** 获取上传凭证 */
export function getUploadCredential(bizType: string, count: number = 1) {
  return apiClient.get<never, { credentials: Array<{ uploadUrl: string; fileId: number }> }>(
    '/upload-credential',
    { params: { bizType, count } },
  );
}
