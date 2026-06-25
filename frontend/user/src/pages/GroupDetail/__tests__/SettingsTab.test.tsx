import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import SettingsTab from '../SettingsTab';
import type { StudyGroupDetailResponse } from '@/types/group';

vi.mock('@/services/group-api', () => ({
  regenerateInviteCode: vi.fn(),
  disbandGroup: vi.fn(),
  updateGroup: vi.fn(),
  leaveGroup: vi.fn(),
  listGroupMembers: vi.fn().mockResolvedValue([]),
  transferOwnership: vi.fn(),
}));

const BASE_GROUP: StudyGroupDetailResponse = {
  id: 1, name: '测试群组', description: '',
  avatarFileId: null, avatarUrl: null, ownerId: 100,
  joinPolicy: 'OPEN', inviteCode: 'ABC12345',
  myRole: 'OWNER', memberCount: 5,
  createdAt: 1, updatedAt: 1,
};

function renderSettings(group: StudyGroupDetailResponse, myRole: string) {
  return render(
    <MemoryRouter>
      <SettingsTab group={group} myRole={myRole} onUpdated={vi.fn()} />
    </MemoryRouter>,
  );
}

describe('SettingsTab', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('OWNER 看到编辑信息按钮', () => {
    renderSettings(BASE_GROUP, 'OWNER');
    expect(screen.getByText('编辑群组信息')).toBeInTheDocument();
  });

  it('ADMIN 看不到编辑信息按钮', () => {
    renderSettings(BASE_GROUP, 'ADMIN');
    expect(screen.queryByText('编辑群组信息')).not.toBeInTheDocument();
  });

  it('OWNER + ADMIN 都看到邀请码管理', () => {
    renderSettings(BASE_GROUP, 'ADMIN');
    expect(screen.getByText('邀请码管理')).toBeInTheDocument();
    expect(screen.getByText('重新生成')).toBeInTheDocument();
  });

  it('MEMBER 看不到邀请码管理', () => {
    renderSettings(BASE_GROUP, 'MEMBER');
    expect(screen.queryByText('邀请码管理')).not.toBeInTheDocument();
  });

  it('OWNER 看到解散群组危险按钮', () => {
    renderSettings(BASE_GROUP, 'OWNER');
    expect(screen.getByText('解散群组')).toBeInTheDocument();
  });

  it('所有角色看到退出群组按钮', () => {
    renderSettings(BASE_GROUP, 'MEMBER');
    expect(screen.getByRole('button', { name: '退出群组' })).toBeInTheDocument();
  });
});
