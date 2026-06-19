# REQ-27 前端 Axios 封装 + 认证状态管理

## 产品定位

在 REQ-26 脚手架基础上，实现前端 HTTP 客户端封装和认证状态管理。提供带 JWT 拦截器的 Axios 实例、Zustand 认证状态存储（localStorage 持久化）、401 并发请求队列（token 自动刷新）、以及全量路由守卫。

REQ-28（登录/注册页面）依赖本需求的 auth store 和路由守卫。

## 用户故事

作为前端开发，我需要一个封装好的 Axios 实例，自动注入 token、处理 401、解包 Result 包装层，以便业务开发时直接调用 API 拿业务数据，不用重复处理认证逻辑。

作为玩家，我登录后刷新页面不会掉登录态；access token 过期时自动刷新无感知；refresh token 也过期时有明确的 Modal 提示；关闭标签页再打开仍是登录状态。

## 功能需求

### F1. Axios 实例（`src/services/api-client.ts`）

- 创建 `apiClient: AxiosInstance`，`baseURL: '/api'`，`timeout: 15000`
- 响应拦截器处理 `Result<T>` 自动解包：`code === 200` 返回 `response.data.data`；`code !== 200` 抛 `ApiError`（含 code + message + httpStatus）
- 网络错误（无响应体）抛 `ApiError`（`httpStatus: 0`，`message: '网络异常，请检查网络连接'`）

### F2. 请求拦截器（Token 注入）

- 每次请求前从 auth store 读取 `accessToken`
- 有 token 则注入 `Authorization: Bearer <token>` 请求头
- 无 token 则跳过（注册、登录等公开接口不发 token，后端不校验）

### F3. 401 响应拦截与并发刷新队列

**401 白名单**：以下端点返回 401 时直接 reject，不进入刷新队列（避免无限循环）：

```ts
const AUTH_WHITELIST = ['/api/users/login', '/api/users/register', '/api/users/refresh-token'];
```

拦截器收到 401 时先检查请求 URL 是否匹配白名单（使用 `url.includes(endpoint)` 或精确匹配），命中则跳过刷新逻辑，直接 reject。

**刷新流程**：
- 非白名单请求收到 401 → 进入刷新队列
- 第一个 401 用独立 axios 实例（`axios.create()` 不含 401 响应拦截器）调 `/api/users/refresh-token`，防止循环触发
- 刷新期间后续 401 排队等待
- 刷新成功：用新 token 重放所有排队请求
- 刷新失败：**所有排队请求立即 reject**（`ApiError(code=401, message='登录已过期')`），不等待 Modal 交互结果。同时弹出 antd `Modal.confirm`（title: "登录已过期"，content: "请重新登录"），用户确认后清除本地 token（`authStore.logout()`）+ 跳转 `/login?redirect=<当前路径>`
- 并发安全：`isRefreshing` 标志位 + 请求队列 `pendingRequests: Array<{resolve, reject}>`，确保同一时间最多一个 refresh 请求

### F4. Auth Store（`src/store/auth-store.ts`）

```ts
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  expiresIn: number | null;
  user: { id: number; username: string; nickname: string; role: string; avatarFileId: number | null; avatarUrl: string | null } | null;

  // 派生
  isAuthenticated: () => boolean;

  // Actions
  login: (accessToken: string, refreshToken: string, expiresIn: number, user: UserInfo) => void;
  logout: () => Promise<void>;
  setTokens: (accessToken: string, refreshToken: string, expiresIn: number) => void;
}
```

- 使用 Zustand `persist` 中间件，序列化到 localStorage（key: `auth-storage`）
- `isAuthenticated` 派生自 `!!accessToken`
- `logout()` 先调 `POST /api/users/logout`（后端黑名单，fire-and-forget），再清空本地状态
- `setTokens()` 用于 refresh 成功后更新 token，不触发完整的 login 流程

### F5. Auth API（`src/services/auth-api.ts`）

| 函数 | 方法 | 端点 | 返回值 | 备注 |
|------|------|------|--------|------|
| `loginApi(data)` | POST | `/api/users/login` | `LoginResponse`（accessToken, refreshToken, expiresIn, user） | |
| `registerApi(data)` | POST | `/api/users/register` | `UserResponse` | |
| `refreshTokenApi(refreshToken)` | POST | `/api/users/refresh-token` | `RefreshTokenResponse`（accessToken, refreshToken, expiresIn） | 使用独立 axios 实例（不含 401 响应拦截器），避免无限循环 |
| `logoutApi(refreshToken)` | POST | `/api/users/logout` | `void` | accessToken 由请求拦截器自动注入 Authorization 头，调用方只需传 refreshToken |

### F6. 路由守卫（`src/components/AuthGuard.tsx`）

- 包裹 `<Outlet />`，检查 `authStore.isAuthenticated()`
- 已登录 → 渲染子路由
- 未登录 → `<Navigate to="/login?redirect=<当前完整路径>" replace />`
- 白名单路由（`/login`、`/register`）不包裹 AuthGuard，使用独立布局

### F7. 路由配置修改（`src/routes/index.tsx`）

- 新增 `/login` 和 `/register` 路由（占位组件 → 提示"REQ-28 实现"）
- 除白名单外的路由包裹 `<AuthGuard />`
- 路由结构：

```
/ → Navigate /home
/login → LoginPlaceholder（白名单，独立布局）
/register → RegisterPlaceholder（白名单，独立布局）
AuthGuard 包裹：
  /home → Home
  * → NotFound
```

### F8. MainLayout Header 改造（`src/layouts/MainLayout.tsx`）

- 替换 Header 右侧的"未登录"占位为真实用户数据
- 读取 `authStore.user`：显示头像（`Avatar`）+ 昵称
- 点击用户区 → 下拉菜单（"个人中心" + "退出登录"）
- 退出登录：调 `authStore.logout()` → 跳转 `/login`

### F9. 类型定义（`src/types/api.ts`）

```ts
// 后端 Result 包装层（用于拦截器未解包前的原始响应类型）
interface ApiResult<T> { code: number; message: string; data: T; }

// ApiError
class ApiError extends Error {
  code: number;
  httpStatus: number;
  constructor(code: number, message: string, httpStatus: number);
}

// 后端 UserResponse 前端类型
interface UserInfo { id: number; username: string; nickname: string; role: string; avatarFileId: number | null; avatarUrl: string | null; }
```

## 非功能需求

### 鉴权

- Access token 有效期 30 分钟，过期后自动刷新（用户无感知）
- Refresh token 有效期 7 天，过期后用 Modal 明确告知
- Token 存储在 localStorage，页面刷新/关闭重开不丢登录态
- 所有需认证的 API 自动注入 Bearer token
- 登录/注册/刷新 token 三个公开接口不走拦截器的 401 判断（实现方式见 F3 白名单）

### 性能

- Zustand store 使用 selector 精确订阅（避免不必要的重渲染）
- `isRefreshing` 队列机制确保同一时间最多一个 refresh 请求
- Axios timeout 15 秒，避免请求卡死

### 安全

- Token 存储于 localStorage（XSS 可读取，但后端有 blacklist 兜底）
- Logout 时调后端接口将 token 加入黑名单（即使 XSS 窃取后无法使用）
- `apiClient` 导出但不推荐业务代码直接使用（业务代码通过 `auth-api.ts` 等 service 层调用）；测试文件通过 `apiClient` 直接发起请求验证拦截器行为

## 前置依赖

| 需求 | 状态 | 依赖内容 |
|------|------|---------|
| REQ-26 前端项目搭建 | `done` | `frontend/user/` 脚手架已就位，包含 MainLayout（Header + 菜单 + "未登录"占位）、路由（`createBrowserRouter`，MainLayout 包裹 `/home` 和 `*`）、axios/zustand 依赖、services/store 空目录 |

## Impact Analysis

### 新增文件

| 文件 | 说明 |
|------|------|
| `frontend/user/src/services/api-client.ts` | Axios 实例 + 请求/响应拦截器 |
| `frontend/user/src/services/auth-api.ts` | 认证相关 API 调用 |
| `frontend/user/src/store/auth-store.ts` | Zustand auth store |
| `frontend/user/src/components/AuthGuard.tsx` | 路由守卫组件 |
| `frontend/user/src/types/api.ts` | ApiResult / ApiError / UserInfo 类型定义 |
| `frontend/user/src/services/__tests__/api-client.test.ts` | api-client 单元测试 |
| `frontend/user/src/store/__tests__/auth-store.test.ts` | auth-store 单元测试 |
| `frontend/user/src/components/__tests__/AuthGuard.test.tsx` | AuthGuard 测试 |

### 新增依赖

| 依赖 | 类型 | 说明 |
|------|------|------|
| `axios-mock-adapter` | devDependency | 测试中 mock axios 响应，拦截器链完整执行 |

### 修改文件

| 文件 | 变更 | 说明 |
|------|------|------|
| `frontend/user/src/routes/index.tsx` | 新增 `/login`、`/register` 占位路由；包裹 `AuthGuard` | REQ-26 路由结构基础上增加认证层 |
| `frontend/user/src/layouts/MainLayout.tsx` | Header 用户区接入真实数据 | 替换 REQ-26 "未登录"占位 |
| `frontend/user/src/layouts/__tests__/MainLayout.test.tsx` | 新增 auth store mock 用例 | 验证登录态下 Header 显示用户信息 |

### 受影响的现有功能

| 影响 | 说明 |
|------|------|
| REQ-26 脚手架 | MainLayout Header + 路由定义被 REQ-27 修改，需确保测试仍然通过 |
| REQ-28 登录/注册 | 依赖 REQ-27 的 `auth-api.ts` 的 `loginApi`/`registerApi`、`authStore.login()`、路由守卫的 `redirect` 参数 |
| REQ-29+ 业务页面 | 依赖 REQ-27 的 `apiClient` 调用后端 API、auth store 获取当前用户 |

### 向前兼容检查

| 未来需求 | 与 REQ-27 的约束关系 |
|---------|---------------------|
| REQ-28（登录/注册页面） | 调用 `authStore.login()` 传入 token + user；登录页读 URL `redirect` 参数决定跳转目标；注册成功后自动登录 |
| REQ-29+（业务页面） | 使用 `apiClient` 调后端 API，通过 `authStore.useAuthStore()` 获取当前用户 |
| 未来移动端适配 | auth store 与平台无关，无需修改 |
| 未来 WebSocket 实时通信 | WebSocket 连接建立时可能需传 token，可复用 auth store 中的 accessToken |

## Verification Plan

### 手动验证步骤

1. **启动开发环境**：`cd frontend/user && npm run dev`，后端 app 服务（8082）启动
2. **未登录拦截**：访问 `http://localhost:5173/home` → 被重定向到 `/login?redirect=%2Fhome`
3. **登录流程**（需 REQ-28 完成后）：登录 → 跳转回 `/home` → Header 显示用户昵称+头像
4. **刷新保持登录态**：F5 刷新 → 仍保持登录，Header 仍显示用户信息
5. **登出**：点击 Header 用户区 → "退出登录" → 清空 token → 跳转 `/login`
6. **Token 自动刷新**：等待或手动过期 access token → 调任意 API → 自动刷新，用户无感知
7. **Refresh 过期**：手动删除 localStorage 中 refresh token → 调任意 API → 弹出 Modal → 确认后跳转 `/login`
8. **API 代理**：DevTools Network 确认 `/api/**` 请求携带 `Authorization: Bearer <token>`

### 自动化测试覆盖

| 测试文件 | 覆盖场景 | 预期断言 |
|---------|---------|---------|
| `api-client.test.ts` | 成功响应解包 data | `apiClient.get('/test')` 返回 `response.data.data` |
| | 业务错误抛出 ApiError | `code !== 200` 抛 `ApiError(code, message, httpStatus)` |
| | 网络错误 | `message: '网络异常，请检查网络连接'` |
| | 请求拦截器注入 token | auth store 有 token 时请求头含 `Authorization` |
| | 无 token 时不注入 | auth store 无 token 时请求头不含 `Authorization` |
| | 401 自动刷新 | 模拟 401 → refresh → 重放成功 |
| | 并发 401 排队 | 3 个 401 同时到达 → 仅发 1 次 refresh → 3 次重放 |
| | refresh 失败弹出 Modal | refresh 返回 401 → Modal.confirm 被调用 |
| `auth-store.test.ts` | login 设置 token + user | `isAuthenticated()` 返回 true |
| | logout 清除状态 + 调 API | token 清空，`isAuthenticated()` 返回 false |
| | persist 恢复 | 模拟 localStorage 有数据，hydrate 后恢复 |
| `AuthGuard.test.tsx` | 已登录渲染子路由 | Outlet 正常渲染 |
| | 未登录重定向 /login | Navigate to `/login?redirect=...` |
| `MainLayout.test.tsx` | 登录态显示用户信息 | Header 显示 user.nickname |
| | 未登录态显示"未登录" | 兜底显示占位文本 |

### 回滚标准

- 回滚操作：删除 `frontend/user/src/services/` 和 `frontend/user/src/store/` 下的 REQ-27 新增文件，还原 `routes/index.tsx` 和 `MainLayout.tsx` 为 REQ-26 版本
- 无数据库变更，无后端变更，前后端独立，回滚无副作用

## 验收标准

1. 未登录访问 `/home` 重定向到 `/login?redirect=%2Fhome`
2. 登录后 `/home` 正常渲染，Header 显示用户昵称 + 头像
3. F5 刷新后保持登录态（localStorage 持久化）
4. 点击"退出登录"后清除 token，重定向 `/login`
5. Access token 过期时自动刷新（Network 面板仅 1 次 refresh 请求）
6. Refresh token 过期时弹 Modal，确认后跳转 `/login`
7. 所有 `/api/**` 请求携带 `Authorization: Bearer <token>`
8. API 响应 `code === 200` 自动解包返回 `data` 字段
9. `npm run test` 全部通过（至少 5 个测试文件）
10. `npm run build` 通过，无 TS 错误
11. `npm run lint` 通过

## 技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| HTTP 客户端 | Axios 1.7 | REQ-26 已装，拦截器机制成熟 |
| 状态管理 | Zustand + persist 中间件 | REQ-26 已装，API 简洁，TS 友好，内置 localStorage 持久化 |
| 路由守卫 | 全量拦截 + 白名单 | 游戏应用，所有核心功能需登录；`/login` `/register` 为仅有的公开路由 |
| Token 持久化 | localStorage | 关闭标签页重开自动恢复登录态；后端黑名单兜底 |
| 401 并发处理 | 队列等待机制 | 行业最佳实践，避免重复 refresh |
| Refresh 过期处理 | Modal 确认 | 明确告知玩家会话过期，比静默跳转更友好 |
| Result 解包 | 拦截器自动解包 `data` | 组件代码最干净，只管拿业务数据 |
| 错误类型 | 自定义 ApiError 类 | 区分网络错误/业务错误/HTTP 错误，统一错误处理 |
| Timeout | 15000ms | 兼顾慢网络与用户耐心 |
| 测试框架 | Vitest + `axios-mock-adapter` | 在 axios 实例级别 mock HTTP 响应，拦截器链完整执行，真实覆盖 token 注入/401 刷新/Result 解包逻辑 |

## 不包含的内容

| 内容 | 负责需求 |
|------|---------|
| 登录/注册页面 | REQ-28 |
| 用户注册流程 | REQ-28（页面）+ REQ-01（后端已实现） |
| 记住密码 / 自动登录 | 未规划 |
| 社交登录（OAuth2 / 微信 / QQ） | 未规划 |
| 多设备登录踢下线 | 未规划 |
| Token 过期前主动刷新（定时器前置刷新） | 未规划（当前仅被动刷新：401 触发） |
| PWA 离线认证 | 未规划 |
