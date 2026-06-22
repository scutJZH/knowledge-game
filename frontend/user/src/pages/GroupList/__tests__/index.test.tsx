import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import GroupList from '../index';

const mockList = vi.hoisted(() => vi.fn());
vi.mock('@/services/group-api', () => ({
  listMyGroups: mockList,
  createGroup: vi.fn(),
  joinByInvite: vi.fn(),
  getUploadCredential: vi.fn(),
}));

function renderPage() {
  return render(<MemoryRouter initialEntries={['/groups']}><GroupList /></MemoryRouter>);
}

describe('GroupList 页面', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockList.mockResolvedValue([]);
  });

  it('加载中显示 Spin', async () => {
    mockList.mockReturnValue(new Promise(() => {}));
    renderPage();
    await waitFor(() => {
      expect(document.querySelector('.ant-spin')).toBeInTheDocument();
    });
  });

  it('加载失败显示 Result error', async () => {
    mockList.mockRejectedValue(new Error('network'));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('加载失败')).toBeInTheDocument();
    });
  });

  it('空列表显示引导 + 创建/加入按钮', async () => {
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('还没有加入群组')).toBeInTheDocument();
      expect(screen.getByText('+ 创建群组')).toBeInTheDocument();
      expect(screen.getByText('🔗 加入群组')).toBeInTheDocument();
    });
  });

  it('有数据时显示群组头部和卡片', async () => {
    mockList.mockResolvedValue([
      { id: 1, name: 'Java 组', avatarFileId: null, avatarUrl: null,
        ownerId: 100, joinPolicy: 'OPEN', myRole: 'OWNER', memberCount: 3,
        createdAt: 1, updatedAt: 1 },
      { id: 2, name: 'Python 组', avatarFileId: null, avatarUrl: null,
        ownerId: 200, joinPolicy: 'INVITE_ONLY', myRole: 'MEMBER', memberCount: 8,
        createdAt: 1, updatedAt: 1 },
    ] as any);
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('我的群组')).toBeInTheDocument();
      expect(screen.getByText('2 个')).toBeInTheDocument();
      expect(screen.getByText('Java 组')).toBeInTheDocument();
      expect(screen.getByText('Python 组')).toBeInTheDocument();
    });
  });
});
