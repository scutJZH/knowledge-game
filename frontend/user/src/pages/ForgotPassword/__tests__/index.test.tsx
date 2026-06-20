import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ForgotPassword from '../index';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}));

describe('ForgotPassword', () => {
  it('渲染标题和副标题', () => {
    render(<ForgotPassword />);
    expect(screen.getByText('找回密码')).toBeInTheDocument();
    expect(screen.getByText(/即将上线/)).toBeInTheDocument();
  });

  it('点击「返回登录」跳转 /login', async () => {
    const user = userEvent.setup();
    render(<ForgotPassword />);
    await user.click(screen.getByText('返回登录'));
    expect(mockNavigate).toHaveBeenCalledWith('/login');
  });
});
