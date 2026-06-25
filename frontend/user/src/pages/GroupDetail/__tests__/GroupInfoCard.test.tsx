import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import GroupInfoCard from '../GroupInfoCard';
import type { StudyGroupDetailResponse } from '@/types/group';

const mockRegen = vi.hoisted(() => vi.fn());
vi.mock('@/services/group-api', () => ({
  regenerateInviteCode: mockRegen,
}));

const BASE_GROUP: StudyGroupDetailResponse = {
  id: 1, name: '测试群组', description: '描述',
  avatarFileId: null, avatarUrl: null, ownerId: 100,
  joinPolicy: 'OPEN', inviteCode: 'ABC12345',
  myRole: 'OWNER', memberCount: 5,
  createdAt: 1718800000000, updatedAt: 1718800000000,
};

describe('GroupInfoCard', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('OWNER 可见邀请码 + 重新生成按钮', () => {
    render(<GroupInfoCard group={BASE_GROUP} onRefreshed={vi.fn()} />);
    expect(screen.getByText('ABC12345')).toBeInTheDocument();
    expect(screen.getByText('重新生成')).toBeInTheDocument();
  });

  it('MEMBER 不可见邀请码', () => {
    render(<GroupInfoCard group={{ ...BASE_GROUP, myRole: 'MEMBER', inviteCode: null }} onRefreshed={vi.fn()} />);
    expect(screen.queryByText('ABC12345')).not.toBeInTheDocument();
  });

  it('显示角色标签', () => {
    render(<GroupInfoCard group={BASE_GROUP} onRefreshed={vi.fn()} />);
    expect(screen.getByText('群主')).toBeInTheDocument();
  });

  it('显示开始游戏按钮', () => {
    render(<GroupInfoCard group={BASE_GROUP} onRefreshed={vi.fn()} />);
    expect(screen.getByText('开始游戏')).toBeInTheDocument();
  });
});
