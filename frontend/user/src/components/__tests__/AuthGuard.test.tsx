import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useSearchParams } from 'react-router-dom';
import AuthGuard from '@/components/AuthGuard';

let mockAccessToken: string | null = null;

vi.mock('@/store/auth-store', () => ({
  useAuthStore: (selector?: (state: unknown) => unknown) => {
    const state = { accessToken: mockAccessToken };
    return selector ? selector(state) : state;
  },
}));

/**
 * 使用 MemoryRouter + Routes 而非 createMemoryRouter + RouterProvider，
 * 避免 jsdom AbortSignal cross-realm instanceof 不兼容问题（Trap #8）。
 */

/** 登录页面占位（验证 redirect 参数） */
function LoginPage() {
  const [searchParams] = useSearchParams();
  return <div>登录页面 (redirect: {searchParams.get('redirect') || '无'})</div>;
}

/** 辅助渲染 */
function renderAuthGuard(isAuth: boolean) {
  mockAccessToken = isAuth ? 'test-token' : null;
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route element={<AuthGuard />}>
          <Route path="/protected" element={<div>受保护的内容</div>} />
        </Route>
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AuthGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // 用例 1：已登录 → 渲染子路由
  it('已登录渲染子路由：isAuthenticated() 返回 true 时渲染 <Outlet />', () => {
    renderAuthGuard(true);
    expect(screen.getByText('受保护的内容')).toBeInTheDocument();
    expect(screen.queryByText(/登录页面/)).not.toBeInTheDocument();
  });

  // 用例 2：未登录 → 重定向 /login?redirect=...
  it('未登录重定向 /login：isAuthenticated() 返回 false 时跳转 /login?redirect=...', () => {
    renderAuthGuard(false);
    expect(screen.getByText(/登录页面/)).toBeInTheDocument();
    // URL 应包含 redirect 参数
    expect(screen.getByText(/redirect: \/protected/)).toBeInTheDocument();
  });
});
