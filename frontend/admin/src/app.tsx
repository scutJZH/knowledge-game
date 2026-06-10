// frontend/admin/src/app.tsx
import requestConfig from './services/request';
import {
  isAuthenticated,
  getUserInfo,
  clearAuth,
  getRefreshToken,
  getAccessToken,
} from '@/utils/token';

// 统一请求配置（UmiJS request 插件自动读取）
export const request = requestConfig;

// 全局初始化状态（ProLayout 消费）
// 返回 undefined 表示未登录，ProLayout 不渲染用户信息
// 返回含 name 字段的对象，ProLayout 右上角显示 name
export async function getInitialState() {
  if (!isAuthenticated()) {
    return undefined;
  }
  const user = getUserInfo();
  if (!user) return undefined;
  return {
    name: user.nickname || user.username,
    ...user,
  };
}

// 路由切换拦截
export function onRouteChange() {
  const pathname = window.location.pathname;
  const loggedIn = isAuthenticated();

  // 未登录且不在登录页 → 跳转登录
  if (!loggedIn && pathname !== '/login') {
    window.location.href = '/login';
    return;
  }
  // 已登录且在登录页 → 跳转首页
  if (loggedIn && pathname === '/login') {
    window.location.href = '/';
  }
}

// ProLayout 配置
export const layout = () => {
  return {
    title: 'Knowledge Game',
    logo: false,
    // dark 主题下 ProLayout 把用户信息渲染在侧边栏底部，light 主题才会在顶部 header 右侧
    navTheme: 'light',
    layout: 'side',
    fixedHeader: true,
    fixSiderbar: true,
    // 定义 logout 后，右上角头像下拉菜单自动显示「退出登录」
    logout: async () => {
      const refreshToken = getRefreshToken();
      const accessToken = getAccessToken();
      if (refreshToken && accessToken) {
        // 用 raw fetch 发登出请求，避免拦截器干扰
        try {
          await fetch('/api/admin/logout', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${accessToken}`,
            },
            body: JSON.stringify({ refreshToken }),
          });
        } catch {
          // 登出请求失败也继续清除本地状态
        }
      }
      clearAuth();
      window.location.href = '/login';
    },
  };
};
