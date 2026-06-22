/** 群组列表项（对应后端 StudyGroupListResponse） */
export interface StudyGroupListResponse {
  id: number;
  name: string;
  description: string | null;
  avatarFileId: number | null;
  avatarUrl: string | null;
  ownerId: number;
  joinPolicy: 'OPEN' | 'INVITE_ONLY';
  myRole: 'OWNER' | 'ADMIN' | 'MEMBER';
  memberCount: number;
  createdAt: number;
  updatedAt: number;
}

/** 创建群组请求 */
export interface CreateStudyGroupRequest {
  name: string;
  description?: string;
  avatarFileId?: number;
  joinPolicy?: 'OPEN' | 'INVITE_ONLY';
}

/** 凭邀请码加入群组请求 */
export interface JoinByInviteRequest {
  inviteCode: string;
}
