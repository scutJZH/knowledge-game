import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import AuthLayout from '../AuthLayout';

let mockAccessToken: string | null = null;

vi.mock('@/store/auth-store', () => {
  const useAuthStore: unknown = (selector?: (state: unknown) => unknown) => {
    const state = { accessToken: mockAccessToken, isAuthenticated: () => !!mockAccessToken };
    return selector ? selector(state) : state;
  };
  return { useAuthStore };
});

vi.mock('react-router-dom', () => ({
  Outlet: () => <div>outlet-content</div>,
  Navigate: ({ to }: { to: string }) => <div data-testid="navigate">to={to}</div>,
}));

describe('AuthLayout', () => {
  it('未登录时渲染 Outlet', () => {
    mockAccessToken = null;
    render(<AuthLayout />);
    expect(screen.getByText('outlet-content')).toBeInTheDocument();
  });

  it('已登录时跳转 /home', () => {
    mockAccessToken = 'mock-token';
    render(<AuthLayout />);
    const nav = screen.getByTestId('navigate');
    expect(nav).toBeInTheDocument();
    expect(nav.textContent).toBe('to=/home');
  });

  it('渲染品牌区含 Logo 和 Slogan', () => {
    mockAccessToken = null;
    render(<AuthLayout />);
    expect(screen.getByText('🃏')).toBeInTheDocument();
    expect(screen.getByText('Knowledge Game')).toBeInTheDocument();
    expect(screen.getByText(/收集/)).toBeInTheDocument();
    expect(screen.getByText(/把知识点变成你想要的卡牌/)).toBeInTheDocument();
  });
});
