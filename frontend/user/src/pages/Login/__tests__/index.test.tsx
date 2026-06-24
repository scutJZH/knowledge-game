import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { loginApi } from '@/services/auth-api';
import { ApiError } from '@/types/api';
import Login from '../index';

const { mockMessageError } = vi.hoisted(() => ({ mockMessageError: vi.fn() }));
const mockLoginFn = vi.fn();
const mockNavigate = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('@/services/auth-api', () => ({
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
  Navigate: ({ to }: { to: string }) => <div data-testid="navigate" data-to={to} />,
  Link: ({ to, children }: { to: string; children: React.ReactNode }) => <a href={to}>{children}</a>,
}));

vi.mock('antd', async () => {
  const actual = await vi.importActual('antd');
  return {
    ...(actual as object),
    message: { error: mockMessageError },
  };
});

describe('Login', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  // 用例 1：必填校验
  it('点击登录按钮时校验用户名和密码必填', async () => {
    const user = userEvent.setup();
    render(<Login />);
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(screen.getByText('请输入用户名')).toBeInTheDocument();
      expect(screen.getByText('请输入密码')).toBeInTheDocument();
    });
  });

  // 用例 2：用户名过短
  it('用户名少于 2 字符时显示校验错误', async () => {
    const user = userEvent.setup();
    render(<Login />);
    const input = screen.getByPlaceholderText('2-50 字符');
    await user.type(input, 'a');
    await user.tab();
    await waitFor(() => {
      expect(screen.getByText('用户名长度需 2-50 字符')).toBeInTheDocument();
    });
  });

  // 用例 3：密码过短
  it('密码少于 6 字符时显示校验错误', async () => {
    const user = userEvent.setup();
    render(<Login />);
    const input = screen.getByPlaceholderText('6-50 字符');
    await user.type(input, '12345');
    await user.tab();
    await waitFor(() => {
      expect(screen.getByText('密码长度 6-50 字符')).toBeInTheDocument();
    });
  });

  // 用例 4：登录成功-默认 redirect 到 /groups
  it('登录成功后调 authStore.login 并跳转 /groups', async () => {
    const mockUser = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    vi.mocked(loginApi).mockResolvedValue({ accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, user: mockUser });
    const user = userEvent.setup();
    render(<Login />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('6-50 字符'), 'password');
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(mockLoginFn).toHaveBeenCalledWith('at', 'rt', 1800, mockUser, true);
      expect(screen.getByTestId('navigate')).toHaveAttribute('data-to', '/groups');
    });
  });

  // 用例 5：redirect 参数
  it('redirect=/card-bag 时登录成功跳转 /card-bag', async () => {
    mockSearchParams = new URLSearchParams('redirect=/card-bag');
    const mockUser = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    vi.mocked(loginApi).mockResolvedValue({ accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, user: mockUser });
    const user = userEvent.setup();
    render(<Login />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('6-50 字符'), 'password');
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(screen.getByTestId('navigate')).toHaveAttribute('data-to', '/card-bag');
    });
  });

  // 用例 6：redirect 以 // 开头回退 /groups
  it('redirect=//evil.com 时回退到 /groups', async () => {
    mockSearchParams = new URLSearchParams('redirect=//evil.com');
    const mockUser = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    vi.mocked(loginApi).mockResolvedValue({ accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, user: mockUser });
    const user = userEvent.setup();
    render(<Login />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('6-50 字符'), 'password');
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(screen.getByTestId('navigate')).toHaveAttribute('data-to', '/groups');
    });
  });

  // 用例 7：redirect 不以 / 开头回退 /groups
  it('redirect=evil.com 时回退到 /groups', async () => {
    mockSearchParams = new URLSearchParams('redirect=evil.com');
    const mockUser = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    vi.mocked(loginApi).mockResolvedValue({ accessToken: 'at', refreshToken: 'rt', expiresIn: 1800, user: mockUser });
    const user = userEvent.setup();
    render(<Login />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('6-50 字符'), 'password');
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(screen.getByTestId('navigate')).toHaveAttribute('data-to', '/groups');
    });
  });

  // 用例 8：失败-BAD_CREDENTIALS
  it('BAD_CREDENTIALS 错误时显示 Alert 并 toast', async () => {
    vi.mocked(loginApi).mockRejectedValue(new ApiError(400, '用户名或密码错误', 400));
    const user = userEvent.setup();
    render(<Login />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'baduser');
    await user.type(screen.getByPlaceholderText('6-50 字符'), 'badpass');
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(screen.getByText('用户名或密码错误')).toBeInTheDocument();
      expect(mockMessageError).toHaveBeenCalled();
    });
  });

  // 用例 9：网络错误
  it('网络错误时显示 Alert', async () => {
    vi.mocked(loginApi).mockRejectedValue(new ApiError(0, '网络异常', 0));
    const user = userEvent.setup();
    render(<Login />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('6-50 字符'), 'password');
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(screen.getByText('网络异常，请检查网络连接')).toBeInTheDocument();
    });
  });

  // 用例 10：「记住我」默认勾选
  it('「记住我」checkbox 默认勾选', () => {
    render(<Login />);
    const checkbox = screen.getByRole('checkbox') as HTMLInputElement;
    expect(checkbox.checked).toBe(true);
  });

  // 用例 11：链接存在
  it('渲染「忘记密码？」和「去注册 →」链接', () => {
    render(<Login />);
    expect(screen.getByText('忘记密码？').getAttribute('href')).toBe('/forgot-password');
    expect(screen.getByText('去注册 →').getAttribute('href')).toBe('/register');
  });

  // 用例 12：非 ApiError 异常兜底
  it('非 ApiError 异常时显示通用错误提示', async () => {
    vi.mocked(loginApi).mockRejectedValue(new Error('TypeError'));
    const user = userEvent.setup();
    render(<Login />);
    await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
    await user.type(screen.getByPlaceholderText('6-50 字符'), 'password');
    await user.click(screen.getByRole('button', { name: /登录|登 录/ }));
    await waitFor(() => {
      expect(screen.getByText('未知错误，请稍后重试')).toBeInTheDocument();
      expect(mockMessageError).toHaveBeenCalled();
    });
  });
});
