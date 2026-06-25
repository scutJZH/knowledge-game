import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import MemberTab from '../MemberTab';

const mockList = vi.hoisted(() => vi.fn());
vi.mock('@/services/group-api', () => ({
  listGroupMembers: mockList,
  kickMember: vi.fn(),
  updateMemberRole: vi.fn(),
  transferOwnership: vi.fn(),
}));

describe('MemberTab', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('加载中显示 Spin', () => {
    mockList.mockReturnValue(new Promise(() => {}));
    render(<MemberTab groupId={1} myRole="OWNER" onGroupChanged={vi.fn()} />);
    expect(document.querySelector('.ant-spin')).toBeInTheDocument();
  });

  it('空列表显示 Empty', async () => {
    mockList.mockResolvedValue([]);
    render(<MemberTab groupId={1} myRole="OWNER" onGroupChanged={vi.fn()} />);
    await waitFor(() => { expect(screen.getByText('暂无成员')).toBeInTheDocument(); });
  });

  it('前三名显示奖牌', { timeout: 10000 }, async () => {
    mockList.mockResolvedValue([
      { userId: 1, nickname: 'A', role: 'OWNER', points: 500, avatarFileId: null, avatarUrl: null, joinedAt: 1 },
      { userId: 2, nickname: 'B', role: 'ADMIN', points: 300, avatarFileId: null, avatarUrl: null, joinedAt: 2 },
      { userId: 3, nickname: 'C', role: 'MEMBER', points: 100, avatarFileId: null, avatarUrl: null, joinedAt: 3 },
      { userId: 4, nickname: 'D', role: 'MEMBER', points: 50, avatarFileId: null, avatarUrl: null, joinedAt: 4 },
    ]);
    render(<MemberTab groupId={1} myRole="OWNER" onGroupChanged={vi.fn()} />);
    await waitFor(() => { expect(screen.getAllByText('A').length).toBeGreaterThanOrEqual(1); });
    const medals = ['🥇', '🥈', '🥉'];
    medals.forEach(m => expect(screen.getByText(m)).toBeInTheDocument());
    const rank4 = screen.getByText('4');
    expect(rank4).toBeInTheDocument();
  });

  it('OWNER 看见 OWNER/ADMIN/MEMBER 角色标签', async () => {
    mockList.mockResolvedValue([
      { userId: 1, nickname: 'A', role: 'OWNER', points: 100, avatarFileId: null, avatarUrl: null, joinedAt: 1 },
      { userId: 2, nickname: 'B', role: 'ADMIN', points: 50, avatarFileId: null, avatarUrl: null, joinedAt: 2 },
      { userId: 3, nickname: 'C', role: 'MEMBER', points: 0, avatarFileId: null, avatarUrl: null, joinedAt: 3 },
    ]);
    render(<MemberTab groupId={1} myRole="OWNER" onGroupChanged={vi.fn()} />);
    await waitFor(() => { expect(screen.getAllByText('群主').length).toBeGreaterThanOrEqual(1); });
    expect(screen.getByText('管理员')).toBeInTheDocument();
    expect(screen.getByText('成员')).toBeInTheDocument();
  });
});
