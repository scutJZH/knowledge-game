import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import GroupInfoCard from '../GroupInfoCard';
import type { StudyGroupDetailResponse } from '@/types/group';

vi.mock('@/services/group-api', () => ({}));

const BASE_GROUP: StudyGroupDetailResponse = {
  id: 1, name: '测试群组', description: '描述',
  avatarFileId: null, avatarUrl: null, ownerId: 100,
  joinPolicy: 'OPEN', inviteCode: 'ABC12345',
  myRole: 'OWNER', memberCount: 5,
  createdAt: 1718800000000, updatedAt: 1718800000000,
};

describe('GroupInfoCard', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('显示群组名称', () => {
    render(<MemoryRouter><GroupInfoCard group={BASE_GROUP} /></MemoryRouter>);
    expect(screen.getByText('测试群组')).toBeInTheDocument();
  });

  it('显示角色标签', () => {
    render(<MemoryRouter><GroupInfoCard group={BASE_GROUP} /></MemoryRouter>);
    expect(screen.getByText('群主')).toBeInTheDocument();
  });

  it('显示成员数', () => {
    render(<MemoryRouter><GroupInfoCard group={BASE_GROUP} /></MemoryRouter>);
    expect(screen.getByText('5 名成员')).toBeInTheDocument();
  });

  it('显示开始游戏按钮', () => {
    render(<MemoryRouter><GroupInfoCard group={BASE_GROUP} /></MemoryRouter>);
    expect(screen.getByText('开始游戏')).toBeInTheDocument();
  });
});
