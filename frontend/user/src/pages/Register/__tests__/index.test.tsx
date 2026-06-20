import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { registerApi } from '@/services/auth-api';
import { loginApi } from '@/services/auth-api';
import { ApiError } from '@/types/api';
import Register from '../index';

const { mockMessageError, mockMessageSuccess, mockModalError } = vi.hoisted(() => ({
  mockMessageError: vi.fn(),
  mockMessageSuccess: vi.fn(),
  mockModalError: vi.fn(),
}));
const mockLoginFn = vi.fn();
const mockNavigate = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('@/services/auth-api', () => ({
  registerApi: vi.fn(),
  loginApi: vi.fn(),
}));

vi.mock('@/store/auth-store', () => {
  const useAuthStore: unknown = (selector?: (state: unknown) => unknown) => {
    const state = { login: mockLoginFn, accessToken: null };
    return selector ? selector(state) : state;
  };
  return { useAuthStore };
});

vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
  useSearchParams: () => [mockSearchParams, vi.fn()],
  Link: ({ to, children }: { to: string; children: React.ReactNode }) => <a href={to}>{children}</a>,
}));

vi.mock('antd', async () => {
  const actual = await vi.importActual('antd');
  return {
    ...(actual as object),
    message: { error: mockMessageError, success: mockMessageSuccess },
    Modal: { error: mockModalError },
  };
});

describe('Register', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  // 用例 1：必填校验
  it('4 个字段全空点注册时显示必填提示', async () => {
    const user = userEvent.setup();
    render(<Register />);
    await user.click(screen.getByRole('button', { name: /注册|注 册/ }));
    await waitFor(() => {
      expect(screen.getByText('请输入用户名')).toBeInTheDocument();
      expect(screen.getByText('请输入昵称')).toBeInTheDocument();
      expect(screen.getByText('请输入密码')).toBeInTheDocument();
      expect(screen.getByText('请确认密码')).toBeInTheDocument();
    });
  });

  // 用例 2：确认密码不一致
  it('两次密码不一致时显示错误提示', async () => {
    const user = userEvent.setup();
    render(<Register />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
    await user.type(screen.getAllByPlaceholderText('6-50 字符')[0], 'abc123');
    await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc124');
    await user.click(screen.getByRole('button', { name: /注册|注 册/ }));
    await waitFor(() => {
      expect(screen.getByText('两次输入的密码不一致')).toBeInTheDocument();
    });
  });

  // 用例 3：注册成功-自动登录
  it('注册成功后自动登录并跳转 /home', async () => {
    const mockUser = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    vi.mocked(registerApi).mockResolvedValue(mockUser);
    vi.mocked(loginApi).mockResolvedValue({ accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, user: mockUser });
    const user = userEvent.setup();
    render(<Register />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
    await user.type(screen.getAllByPlaceholderText('6-50 字符')[0], 'abc123');
    await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
    await user.click(screen.getByRole('button', { name: /注册|注 册/ }));
    await waitFor(() => {
      expect(mockLoginFn).toHaveBeenCalledWith('at', 'rt', 1800, mockUser);
      expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      expect(mockMessageSuccess).toHaveBeenCalled();
    });
  });

  // 用例 4：注册失败-用户名重复
  it('用户名已存在时显示字段红字', async () => {
    vi.mocked(registerApi).mockRejectedValue(new ApiError(400, '用户名已存在: zhao', 400));
    const user = userEvent.setup();
    render(<Register />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'zhao');
    await user.type(screen.getByPlaceholderText('你的昵称'), 'Zhao');
    await user.type(screen.getAllByPlaceholderText('6-50 字符')[0], 'abc123');
    await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
    await user.click(screen.getByRole('button', { name: /注册|注 册/ }));
    await waitFor(() => {
      expect(screen.getByText('该用户名已被注册')).toBeInTheDocument();
    });
  });

  // 用例 5：register 成功 + login 失败兜底
  it('注册成功但自动登录失败时弹出 Modal', async () => {
    const mockUser = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    vi.mocked(registerApi).mockResolvedValue(mockUser);
    vi.mocked(loginApi).mockRejectedValue(new ApiError(400, '登录失败', 400));
    const user = userEvent.setup();
    render(<Register />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
    await user.type(screen.getAllByPlaceholderText('6-50 字符')[0], 'abc123');
    await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
    await user.click(screen.getByRole('button', { name: /注册|注 册/ }));
    await waitFor(() => {
      expect(mockModalError).toHaveBeenCalled();
      const callArgs = mockModalError.mock.calls[0][0];
      expect(callArgs.title).toContain('自动登录失败');
      expect(callArgs.content).toContain('手动登录');
    });
  });

  // 用例 6：链接存在
  it('渲染「去登录 →」链接', () => {
    render(<Register />);
    expect(screen.getByText('去登录 →').getAttribute('href')).toBe('/login');
  });

  // 用例 7：redirect 参数
  it('redirect=/card-bag 时注册成功跳转 /card-bag', async () => {
    mockSearchParams = new URLSearchParams('redirect=/card-bag');
    const mockUser = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    vi.mocked(registerApi).mockResolvedValue(mockUser);
    vi.mocked(loginApi).mockResolvedValue({ accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, user: mockUser });
    const user = userEvent.setup();
    render(<Register />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
    await user.type(screen.getAllByPlaceholderText('6-50 字符')[0], 'abc123');
    await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
    await user.click(screen.getByRole('button', { name: /注册|注 册/ }));
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/card-bag', { replace: true });
    });
  });

  // 用例 8：非 ApiError 异常兜底
  it('非 ApiError 异常时显示通用错误提示', async () => {
    vi.mocked(registerApi).mockRejectedValue(new Error('TypeError'));
    const user = userEvent.setup();
    render(<Register />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
    await user.type(screen.getAllByPlaceholderText('6-50 字符')[0], 'abc123');
    await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
    await user.click(screen.getByRole('button', { name: /注册|注 册/ }));
    await waitFor(() => {
      expect(screen.getByText('未知错误，请稍后重试')).toBeInTheDocument();
      expect(mockMessageError).toHaveBeenCalled();
    });
  });
});
