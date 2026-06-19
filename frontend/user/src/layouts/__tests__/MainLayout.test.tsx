import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import MainLayout from '../MainLayout';

let mockUser: {
  id: number;
  username: string;
  nickname: string;
  role: string;
  avatarFileId: number | null;
  avatarUrl: string | null;
} | null = null;

const mockLogoutFn = vi.fn();

vi.mock('@/store/auth-store', () => {
  const useAuthStore: unknown = (selector?: (state: unknown) => unknown) => {
    const state = { user: mockUser, refreshToken: 'test-refresh', logout: mockLogoutFn };
    return selector ? selector(state) : state;
  };
  (useAuthStore as { getState: () => unknown }).getState = () => ({
    user: mockUser,
    refreshToken: 'test-refresh',
    logout: mockLogoutFn,
  });
  return { useAuthStore };
});

vi.mock('@/services/auth-api', () => ({
  logoutApi: vi.fn().mockResolvedValue(undefined),
}));

describe('MainLayout', () => {
  // 用例 1：未登录态显示"未登录"占位
  it('未登录态显示"未登录"占位', () => {
    mockUser = null;
    render(
      <MemoryRouter initialEntries={['/home']}>
        <MainLayout />
      </MemoryRouter>,
    );

    expect(screen.getByText(/Knowledge Game/)).toBeInTheDocument();
    expect(screen.getByText('首页')).toBeInTheDocument();
    expect(screen.getByText('图鉴')).toBeInTheDocument();
    expect(screen.getByText('卡包')).toBeInTheDocument();
    expect(screen.getByText('我的')).toBeInTheDocument();
    expect(screen.getByText('未登录')).toBeInTheDocument();
  });

  // 用例 2：登录态显示用户昵称，不显示"未登录"
  it('登录态显示用户昵称', () => {
    mockUser = {
      id: 1,
      username: 'player1',
      nickname: '玩家一号',
      role: 'USER',
      avatarFileId: null,
      avatarUrl: null,
    };
    render(
      <MemoryRouter initialEntries={['/home']}>
        <MainLayout />
      </MemoryRouter>,
    );

    expect(screen.getByText('玩家一号')).toBeInTheDocument();
    expect(screen.queryByText('未登录')).not.toBeInTheDocument();
  });

  // 用例 3：子路由内容渲染
  it('should render child content via Outlet', () => {
    mockUser = null;
    render(
      <MemoryRouter initialEntries={['/home']}>
        <MainLayout />
      </MemoryRouter>,
    );
    expect(screen.getByText('首页')).toBeInTheDocument();
  });
});
