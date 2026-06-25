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

/** 群组详情响应 */
export interface StudyGroupDetailResponse {
  id: number;
  name: string;
  description: string | null;
  avatarFileId: number | null;
  avatarUrl: string | null;
  ownerId: number;
  joinPolicy: 'OPEN' | 'INVITE_ONLY';
  inviteCode: string | null;
  myRole: 'OWNER' | 'ADMIN' | 'MEMBER';
  memberCount: number;
  createdAt: number;
  updatedAt: number;
}

/** 群组成员列表项 */
export interface GroupMemberListResponse {
  userId: number;
  nickname: string;
  avatarFileId: number | null;
  avatarUrl: string | null;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  points: number;
  joinedAt: number;
}

/** 编辑群组请求 */
export interface UpdateStudyGroupRequest {
  name?: string;
  description?: string;
  avatarFileId?: number;
  joinPolicy?: 'OPEN' | 'INVITE_ONLY';
}

/** 题目列表项 */
export interface QuestionListResponse {
  id: number;
  title: string;
  fullText: string;
  answer: string;
  difficulty: number | null;
  type: string | null;
  createdAt: number;
}

/** 题目分页响应 */
export interface QuestionPageResponse {
  content: QuestionListResponse[];
  totalElements: number;
  totalPages: number;
}

