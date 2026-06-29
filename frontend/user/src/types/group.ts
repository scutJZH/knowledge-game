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

/** 群组已关联 IP 库响应（REQ-51） */
export interface GroupIpLibraryResponse {
  id: number;
  groupId: number;
  ipSeriesId: number;
  ipSeriesName: string;
  ipSeriesCode: string;
  coverImageFileId: number | null;
  coverImageUrl: string | null;
  status: 'ACTIVE' | 'DISABLED';
  addedAt: number;
}

/** ACTIVE IP 系列响应（用户端 IP 库 Tab 数据源） */
export interface ActiveIpSeriesResponse {
  id: number;
  name: string;
  code: string;
  coverImageFileId: number | null;
  coverImageUrl: string | null;
}

