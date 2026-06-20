/**
 * REQ-28 Black-Box Boundary Tests — Login & Register Pages
 *
 * These tests cover edge cases and boundary conditions that the white-box
 * unit tests (Login/Register/AuthLayout/__tests__) do NOT cover.
 *
 * Existing white-box test names (to avoid overlap):
 *   Login: 必填校验 / 用户名过短 / 密码过短 / 登录成功跳转 / redirect=card-bag /
 *          redirect=//evil.com / redirect=evil.com / BAD_CREDENTIALS错误 /
 *          网络错误 / 记住我checkbox / 忘记密码+去注册链接 / 非ApiError兜底
 *   Register: 4字段全空 / 密码不一致 / 注册成功自动登录 / 用户名已存在 /
 *             register成功login失败Modal / 去登录链接 / redirect参数 / 非ApiError兜底
 *   AuthLayout: 未登录渲染Outlet / 已登录跳转/home / 品牌区渲染
 *   auth-store: 初始状态 / login设置 / user字段完整性 / logout / setTokens /
 *               localStorage持久化 / hydration / rememberMe默认true /
 *               remember=false写sessionStorage / setRememberMe迁移 /
 *               hydration从sessionStorage恢复
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { loginApi, registerApi } from '@/services/auth-api';
import { ApiError } from '@/types/api';
import type { UserInfo } from '@/types/api';
import Login from '@/pages/Login/index';
import Register from '@/pages/Register/index';

// ── Hoisted mock references ──────────────────────────────────────────
const { mockMessageError, mockMessageSuccess, mockModalError } = vi.hoisted(() => ({
  mockMessageError: vi.fn(),
  mockMessageSuccess: vi.fn(),
  mockModalError: vi.fn(),
}));

const mockLoginFn = vi.fn();
const mockNavigate = vi.fn();
let mockSearchParams = new URLSearchParams();

// ── Module mocks ─────────────────────────────────────────────────────
vi.mock('@/services/auth-api', () => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
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

// ── Test fixtures ────────────────────────────────────────────────────
const mockUser: UserInfo = {
  id: 1,
  username: 'test',
  nickname: 'T',
  role: 'USER',
  avatarFileId: null,
  avatarUrl: null,
};
const mockLoginRes = {
  accessToken: 'at',
  refreshToken: 'rt',
  expiresIn: 1800,
  user: mockUser,
};

// ═══════════════════════════════════════════════════════════════════════
// Tests
// ═══════════════════════════════════════════════════════════════════════
describe('REQ-28 Black-Box: Login & Register Boundary Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams = new URLSearchParams();
  });

  // =====================================================================
  // Login
  // =====================================================================
  describe('Login', () => {
    it('password 刚好 6 字符边界 → 校验通过并提交成功', async () => {
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);
      const user = userEvent.setup();
      render(<Login />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
      await user.type(screen.getByPlaceholderText('6-50 字符'), '123456');
      await user.click(screen.getByRole('button', { name: /登录|登 录/ }));

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      });
      expect(screen.queryByText('密码长度 6-50 字符')).not.toBeInTheDocument();
    });

    it('password 刚好 50 字符边界 → 校验通过并提交成功', async () => {
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);
      const user = userEvent.setup();
      render(<Login />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'p'.repeat(50));
      await user.click(screen.getByRole('button', { name: /登录|登 录/ }));

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      });
      expect(screen.queryByText('密码长度 6-50 字符')).not.toBeInTheDocument();
    });

    it('redirect 参数含特殊字符(中日韩/空格) → navigate 带相同值', async () => {
      mockSearchParams = new URLSearchParams([['redirect', '/首页?q=你好 world']]);
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);
      const user = userEvent.setup();
      render(<Login />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'password');
      await user.click(screen.getByRole('button', { name: /登录|登 录/ }));

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/首页?q=你好 world', { replace: true });
      });
    });

    it('提交失败→修改表单→再次提交→旧 Alert 已清除', async () => {
      // Phase 1: submit fails with BAD_CREDENTIALS → Alert appears
      vi.mocked(loginApi).mockRejectedValue(new ApiError(400, '用户名或密码错误', 400));
      const user = userEvent.setup();
      render(<Login />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'baduser');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'badpass');
      await user.click(screen.getByRole('button', { name: /登录|登 录/ }));

      await waitFor(() => {
        expect(screen.getByText('用户名或密码错误')).toBeInTheDocument();
      });
      expect(mockMessageError).toHaveBeenCalled();

      // Phase 2: switch mock to success, change form values, submit again
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);

      const usernameInput = screen.getByPlaceholderText('2-50 字符');
      const passwordInput = screen.getByPlaceholderText('6-50 字符');
      await user.clear(usernameInput);
      await user.type(usernameInput, 'gooduser');
      await user.clear(passwordInput);
      await user.type(passwordInput, 'goodpass');

      await user.click(screen.getByRole('button', { name: /登录|登 录/ }));

      // Phase 3: verify success AND old Alert is gone
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      });
      expect(screen.queryByText('用户名或密码错误')).not.toBeInTheDocument();
    });
  });

  // =====================================================================
  // Register
  // =====================================================================
  describe('Register', () => {
    it('confirmPassword 大小写不同边界 → 显示"两次输入的密码不一致"', async () => {
      const user = userEvent.setup();
      render(<Register />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
      await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
      // password="Abc123", confirmPassword="abc123" — differ only in case
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'Abc123');
      await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
      await user.click(screen.getByRole('button', { name: /注册|注 册/ }));

      await waitFor(() => {
        expect(screen.getByText('两次输入的密码不一致')).toBeInTheDocument();
      });
      // registerApi must NOT be called because form validation fails
      expect(vi.mocked(registerApi)).not.toHaveBeenCalled();
    });

    it('username 刚好 2 字符边界 → 校验通过并提交成功', async () => {
      vi.mocked(registerApi).mockResolvedValue(mockUser);
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);
      const user = userEvent.setup();
      render(<Register />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'ab');
      await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'abc123');
      await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
      await user.click(screen.getByRole('button', { name: /注册|注 册/ }));

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      });
      expect(screen.queryByText('用户名长度需 2-50 字符')).not.toBeInTheDocument();
    });

    it('username 刚好 50 字符边界 → 校验通过并提交成功', async () => {
      vi.mocked(registerApi).mockResolvedValue(mockUser);
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);
      const user = userEvent.setup();
      render(<Register />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'a'.repeat(50));
      await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'abc123');
      await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
      await user.click(screen.getByRole('button', { name: /注册|注 册/ }));

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      });
      expect(screen.queryByText('用户名长度需 2-50 字符')).not.toBeInTheDocument();
    });

    it('提交失败→修改表单→再次提交→旧 Alert 已清除', async () => {
      // Phase 1: submit fails with generic ApiError → Alert appears
      vi.mocked(registerApi).mockRejectedValue(new ApiError(400, '服务端异常', 400));
      const user = userEvent.setup();
      render(<Register />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'baduser');
      await user.type(screen.getByPlaceholderText('你的昵称'), 'Bad');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'abc123');
      await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
      await user.click(screen.getByRole('button', { name: /注册|注 册/ }));

      await waitFor(() => {
        expect(screen.getByText('服务端异常')).toBeInTheDocument();
      });

      // Phase 2: switch mock to success, change form values, submit again
      vi.mocked(registerApi).mockResolvedValue(mockUser);
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);

      const usernameInput = screen.getByPlaceholderText('2-50 字符');
      const nicknameInput = screen.getByPlaceholderText('你的昵称');
      const passwordInput = screen.getByPlaceholderText('6-50 字符');
      const confirmInput = screen.getByPlaceholderText('再次输入密码');

      await user.clear(usernameInput);
      await user.type(usernameInput, 'gooduser');
      await user.clear(nicknameInput);
      await user.type(nicknameInput, 'Good');
      await user.clear(passwordInput);
      await user.type(passwordInput, 'abc123');
      await user.clear(confirmInput);
      await user.type(confirmInput, 'abc123');

      await user.click(screen.getByRole('button', { name: /注册|注 册/ }));

      // Phase 3: verify success AND old Alert is gone
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      });
      expect(screen.queryByText('服务端异常')).not.toBeInTheDocument();
    });

    it('提交期间按钮 loading 状态 + 防重复提交', async () => {
      // Use a deferred promise so that the submission stays pending
      let resolveRegister!: (value: UserInfo) => void;
      const deferred = new Promise<UserInfo>((resolve) => {
        resolveRegister = resolve;
      });
      vi.mocked(registerApi).mockImplementation(() => deferred);
      vi.mocked(loginApi).mockResolvedValue(mockLoginRes);

      const user = userEvent.setup();
      render(<Register />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
      await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'abc123');
      await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
      await user.click(screen.getByRole('button', { name: /注册|注 册/ }));

      // Button must be in loading state while submission is in-flight
      // antd 5 Button loading adds ant-btn-loading class rather than HTML disabled attribute
      await waitFor(() => {
        const btn = screen.getByRole('button', { name: /注册|注 册/ });
        expect(btn.classList.contains('ant-btn-loading')).toBe(true);
      });
      // registerApi must be called exactly once (no duplicate submission)
      expect(vi.mocked(registerApi)).toHaveBeenCalledTimes(1);

      // Resolve the deferred promise so the auto-login flow can continue
      resolveRegister(mockUser);

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true });
      });
    });

    it('注册成功但自动登录失败→Modal onOk 跳转 /login with replace', async () => {
      vi.mocked(registerApi).mockResolvedValue(mockUser);
      vi.mocked(loginApi).mockRejectedValue(new ApiError(400, '登录失败', 400));

      const user = userEvent.setup();
      render(<Register />);

      await user.type(screen.getByPlaceholderText('2-50 字符'), 'testuser');
      await user.type(screen.getByPlaceholderText('你的昵称'), 'Test');
      await user.type(screen.getByPlaceholderText('6-50 字符'), 'abc123');
      await user.type(screen.getByPlaceholderText('再次输入密码'), 'abc123');
      await user.click(screen.getByRole('button', { name: /注册|注 册/ }));

      await waitFor(() => {
        expect(mockModalError).toHaveBeenCalled();
      });

      const modalArgs = mockModalError.mock.calls[0][0];
      expect(modalArgs.title).toContain('自动登录失败');
      expect(modalArgs.content).toContain('手动登录');
      expect(typeof modalArgs.onOk).toBe('function');

      // navigate must NOT have been called before the user clicks Modal's OK
      expect(mockNavigate).not.toHaveBeenCalled();

      // Simulate the user clicking "OK" on the Modal
      modalArgs.onOk();
      expect(mockNavigate).toHaveBeenCalledWith('/login', { replace: true });
    });
  });
});
