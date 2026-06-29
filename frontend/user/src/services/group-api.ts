import { apiClient } from './api-client';
import type {
  StudyGroupListResponse,
  StudyGroupDetailResponse,
  GroupMemberListResponse,
  CreateStudyGroupRequest,
  UpdateStudyGroupRequest,
  QuestionListResponse,
  QuestionPageResponse,
  GroupIpLibraryResponse,
  ActiveIpSeriesResponse,
} from '@/types/group';

/** 查询当前用户已加入的群组列表 */
export function listMyGroups() {
  return apiClient.get<never, StudyGroupListResponse[]>('/study-groups');
}

/** 查询群组详情 */
export function getGroupDetail(id: number) {
  return apiClient.get<never, StudyGroupDetailResponse>(`/study-groups/${id}`);
}

/** 创建群组 */
export function createGroup(data: CreateStudyGroupRequest) {
  return apiClient.post<never, StudyGroupListResponse>('/study-groups', data);
}

/** 编辑群组信息 */
export function updateGroup(id: number, data: UpdateStudyGroupRequest) {
  return apiClient.put<never, StudyGroupDetailResponse>(`/study-groups/${id}`, data);
}

/** 解散群组 */
export function disbandGroup(id: number) {
  return apiClient.delete<never, void>(`/study-groups/${id}`);
}

/** 查询群组成员列表 */
export function listGroupMembers(groupId: number) {
  return apiClient.get<never, GroupMemberListResponse[]>(`/study-groups/${groupId}/members`);
}

/** 凭邀请码加入群组 */
export function joinByInvite(inviteCode: string) {
  return apiClient.post<never, { id: number }>('/study-groups/join-by-invite', { inviteCode });
}

/** 更新成员角色 */
export function updateMemberRole(groupId: number, userId: number, role: string) {
  return apiClient.put<never, void>(`/study-groups/${groupId}/members/${userId}`, { role });
}

/** 转让群主 */
export function transferOwnership(groupId: number, toUserId: number) {
  return apiClient.post<never, void>(`/study-groups/${groupId}/transfer-ownership`, { toUserId });
}

/** 退出群组 */
export function leaveGroup(groupId: number) {
  return apiClient.delete<never, void>(`/study-groups/${groupId}/members/me`);
}

/** 踢出成员 */
export function kickMember(groupId: number, userId: number) {
  return apiClient.delete<never, void>(`/study-groups/${groupId}/members/${userId}`);
}

/** 重新生成邀请码 */
export function regenerateInviteCode(groupId: number) {
  return apiClient.post<never, { inviteCode: string }>(`/study-groups/${groupId}/invite-code/regenerate`);
}

/** 查询题目列表（按分类分页） */
export function listQuestionsByCategory(categoryId: number, page = 1, size = 20) {
  return apiClient.get<never, QuestionPageResponse>(
    '/questions',
    { params: { categoryId, page, size } },
  );
}

/** 获取上传凭证 */
export function getUploadCredential(bizType: string) {
  return apiClient.get<never, { token: string; uploadUrl: string }>(
    '/upload-credential',
    { params: { bizType, count: 1 } },
  );
}

/** 查询群组已关联的 IP 库列表 */
export function listGroupIpLibrary(groupId: number) {
  return apiClient.get<never, GroupIpLibraryResponse[]>(`/study-groups/${groupId}/ip-library`);
}

/** 全量更新群组关联的 IP 库 */
export function updateGroupIpLibrary(groupId: number, ipSeriesIds: number[]) {
  return apiClient.put<never, GroupIpLibraryResponse[]>(`/study-groups/${groupId}/ip-library`, { ipSeriesIds });
}

/** 查询全部 ACTIVE 状态的 IP 系列 */
export function listActiveIpSeries() {
  return apiClient.get<never, ActiveIpSeriesResponse[]>('/ip-series');
}

/** 更新群组 IP 库单个关联状态（禁用/恢复） */
export function updateGroupIpLibraryStatus(
  groupId: number,
  ipSeriesId: number,
  status: 'ACTIVE' | 'DISABLED',
) {
  return apiClient.patch<never, GroupIpLibraryResponse>(
    `/study-groups/${groupId}/ip-library/${ipSeriesId}`,
    { status },
  );
}
