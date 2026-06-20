import { describe, it, expect, beforeEach, vi } from 'vitest';

/**
 * auth-store 单元测试
 *
 * Zustand persist 中间件在 jsdom 环境下同步 hydration。
 * 每个测试前清空 localStorage + 重置模块缓存，确保独立 store 实例。
 */

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.resetModules();
});

/** 动态导入获取全新 store 实例（绕过模块缓存，支持 per-test 独立 store） */
async function getStore() {
  const mod = await import('@/store/auth-store');
  return mod.useAuthStore;
}

describe('auth-store', () => {
  // 用例 1：初始状态
  it('初始状态：isAuthenticated() 返回 false，所有字段为 null', async () => {
    const useAuthStore = await getStore();
    const state = useAuthStore.getState();
    expect(state.isAuthenticated()).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.expiresIn).toBeNull();
    expect(state.user).toBeNull();
  });

  // 用例 2：login() 设置 token + user 后 isAuthenticated() 返回 true
  it('login() 设置 token + user 后 isAuthenticated() 返回 true', async () => {
    const useAuthStore = await getStore();
    const user = { id: 1, username: 'test', nickname: '测试用户', role: 'USER', avatarFileId: null, avatarUrl: null };
    useAuthStore.getState().login('access-1', 'refresh-1', 1800, user);
    const state = useAuthStore.getState();
    expect(state.isAuthenticated()).toBe(true);
    expect(state.accessToken).toBe('access-1');
    expect(state.refreshToken).toBe('refresh-1');
    expect(state.expiresIn).toBe(1800);
    expect(state.user).toEqual(user);
  });

  // 用例 3：login() 传入的 user 字段完整性
  it('login() 传入的 user 字段完整性（id/username/nickname/role/avatarFileId/avatarUrl）', async () => {
    const useAuthStore = await getStore();
    const user = {
      id: 42,
      username: 'player1',
      nickname: '玩家一号',
      role: 'USER',
      avatarFileId: 100,
      avatarUrl: 'https://example.com/avatar.png',
    };
    useAuthStore.getState().login('tok', 'ref', 3600, user);
    const stored = useAuthStore.getState().user!;
    expect(stored.id).toBe(42);
    expect(stored.username).toBe('player1');
    expect(stored.nickname).toBe('玩家一号');
    expect(stored.role).toBe('USER');
    expect(stored.avatarFileId).toBe(100);
    expect(stored.avatarUrl).toBe('https://example.com/avatar.png');
  });

  // 用例 4：logout() 清空所有字段，isAuthenticated() 返回 false
  it('logout() 清空所有字段，isAuthenticated() 返回 false', async () => {
    const useAuthStore = await getStore();
    const user = { id: 1, username: 'test', nickname: 'Test', role: 'USER', avatarFileId: null, avatarUrl: null };
    useAuthStore.getState().login('access-1', 'refresh-1', 1800, user);
    useAuthStore.getState().logout();
    const state = useAuthStore.getState();
    expect(state.isAuthenticated()).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.expiresIn).toBeNull();
    expect(state.user).toBeNull();
  });

  // 用例 5：setTokens() 仅更新 accessToken/refreshToken/expiresIn，不改变 user
  it('setTokens() 仅更新 accessToken/refreshToken/expiresIn，不改变 user', async () => {
    const useAuthStore = await getStore();
    const user = { id: 1, username: 'test', nickname: 'Test', role: 'USER', avatarFileId: null, avatarUrl: null };
    useAuthStore.getState().login('old-access', 'old-refresh', 1800, user);
    useAuthStore.getState().setTokens('new-access', 'new-refresh', 3600);
    const state = useAuthStore.getState();
    expect(state.accessToken).toBe('new-access');
    expect(state.refreshToken).toBe('new-refresh');
    expect(state.expiresIn).toBe(3600);
    expect(state.user).toEqual(user);
  });

  // 用例 6：localStorage 持久化 — login() 后数据写入 auth-storage key
  it('localStorage 持久化：login() 后数据写入 auth-storage key', async () => {
    const useAuthStore = await getStore();
    const user = { id: 1, username: 'test', nickname: 'Test', role: 'USER', avatarFileId: null, avatarUrl: null };
    useAuthStore.getState().login('access-1', 'refresh-1', 1800, user);

    const raw = localStorage.getItem('auth-storage');
    expect(raw).not.toBeNull();

    const parsed = JSON.parse(raw!);
    expect(parsed.state.accessToken).toBe('access-1');
    expect(parsed.state.refreshToken).toBe('refresh-1');
    expect(parsed.state.expiresIn).toBe(1800);
    expect(parsed.state.user).toEqual(user);
  });

  // 用例 7：localStorage 恢复（hydrate）— 模拟已有数据，store 初始化后自动恢复
  it('localStorage 恢复（hydrate）：模拟已有数据，store 初始化后自动恢复', async () => {
    const user = { id: 2, username: 'cached', nickname: '缓存用户', role: 'ADMIN', avatarFileId: null, avatarUrl: null };
    const persisted = JSON.stringify({
      state: {
        accessToken: 'cached-access',
        refreshToken: 'cached-refresh',
        expiresIn: 7200,
        user,
        rememberMe: true,
      },
      version: 0,
    });
    localStorage.setItem('auth-storage', persisted);

    const useAuthStore = await getStore();
    const state = useAuthStore.getState();
    expect(state.accessToken).toBe('cached-access');
    expect(state.refreshToken).toBe('cached-refresh');
    expect(state.expiresIn).toBe(7200);
    expect(state.user).toEqual(user);
    expect(state.isAuthenticated()).toBe(true);
  });

  // 用例 8：rememberMe 默认为 true
  it('rememberMe 默认为 true', async () => {
    const useAuthStore = await getStore();
    expect(useAuthStore.getState().rememberMe).toBe(true);
  });

  // 用例 9：remember=false 时写入 sessionStorage 而非 localStorage
  it('remember=false 时 login 写入 sessionStorage 而非 localStorage', async () => {
    const useAuthStore = await getStore();
    const user = { id: 1, username: 'test', nickname: 'T', role: 'USER', avatarFileId: null, avatarUrl: null };
    useAuthStore.getState().login('at', 'rt', 1800, user, false);

    expect(localStorage.getItem('auth-storage')).toBeNull();
    const raw = sessionStorage.getItem('auth-storage');
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw!);
    expect(parsed.state.accessToken).toBe('at');
    expect(parsed.state.rememberMe).toBe(false);
  });

  // 用例 10：setRememberMe 触发迁移
  it('setRememberMe 从 localStorage 迁移到 sessionStorage', async () => {
    const useAuthStore = await getStore();
    const user = { id: 2, username: 'u2', nickname: 'U2', role: 'USER', avatarFileId: null, avatarUrl: null };
    useAuthStore.getState().login('at2', 'rt2', 3600, user, true);
    expect(localStorage.getItem('auth-storage')).not.toBeNull();

    useAuthStore.getState().setRememberMe(false);
    expect(localStorage.getItem('auth-storage')).toBeNull();
    const raw = sessionStorage.getItem('auth-storage');
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw!);
    expect(parsed.state.accessToken).toBe('at2');
    expect(parsed.state.rememberMe).toBe(false);
  });

  // 用例 11：hydration 从正确 storage 恢复（sessionStorage）
  it('hydration 从 sessionStorage 恢复（rememberMe: false）', async () => {
    const user = { id: 3, username: 'sess', nickname: 'Session', role: 'USER', avatarFileId: null, avatarUrl: null };
    sessionStorage.setItem('auth-storage', JSON.stringify({
      state: { accessToken: 'sess-at', refreshToken: 'sess-rt', expiresIn: 7200, user, rememberMe: false },
      version: 0,
    }));

    const useAuthStore = await getStore();
    const state = useAuthStore.getState();
    expect(state.accessToken).toBe('sess-at');
    expect(state.rememberMe).toBe(false);
  });
});
