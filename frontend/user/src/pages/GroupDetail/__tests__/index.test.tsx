import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import GroupDetail from '../index';

const mockGetDetail = vi.hoisted(() => vi.fn());
vi.mock('@/services/group-api', () => ({
  getGroupDetail: mockGetDetail,
  listGroupMembers: vi.fn().mockResolvedValue([]),
  updateGroup: vi.fn(),
  disbandGroup: vi.fn(),
  kickMember: vi.fn(),
  updateMemberRole: vi.fn(),
  transferOwnership: vi.fn(),
  regenerateInviteCode: vi.fn(),
  listQuestionsByCategory: vi.fn(),
}));
vi.mock('@/services/api-client', () => ({
  apiClient: { get: vi.fn().mockResolvedValue([]) },
}));

const BASE_GROUP = {
  id: 1, name: '测试群组', description: '描述',
  avatarFileId: null, avatarUrl: null, ownerId: 100,
  joinPolicy: 'OPEN' as const, inviteCode: 'ABC12345',
  myRole: 'OWNER' as const, memberCount: 5,
  createdAt: 1718800000000, updatedAt: 1718800000000,
};

function renderPage(initialEntries = ['/groups/1']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/groups/:id" element={<GroupDetail />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('GroupDetail 页面', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('加载中显示 Spin', () => {
    mockGetDetail.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(document.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('加载失败显示 Result error + 返回按钮', { timeout: 10000 }, async () => {
    mockGetDetail.mockRejectedValue(new Error('fail'));
    renderPage();
    await waitFor(() => { expect(screen.getByText('返回群组列表')).toBeInTheDocument(); }, { timeout: 5000 });
  });

  it('OWNER 视角显示三个 Tab', { timeout: 10000 }, async () => {
    mockGetDetail.mockResolvedValue(BASE_GROUP);
    renderPage();
    await waitFor(() => { expect(screen.getByRole('tab', { name: '成员' })).toBeInTheDocument(); }, { timeout: 5000 });
    expect(screen.getByRole('tab', { name: '知识库' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '设置' })).toBeInTheDocument();
  });

  it('MEMBER 视角不显示设置 Tab', { timeout: 10000 }, async () => {
    mockGetDetail.mockResolvedValue({ ...BASE_GROUP, myRole: 'MEMBER' as const, inviteCode: null });
    renderPage();
    await waitFor(() => { expect(screen.getByRole('tab', { name: '成员' })).toBeInTheDocument(); }, { timeout: 5000 });
    expect(screen.queryByRole('tab', { name: '设置' })).not.toBeInTheDocument();
  });
});
