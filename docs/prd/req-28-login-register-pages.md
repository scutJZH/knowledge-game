# REQ-28 登录/注册页面

## 产品定位

在 REQ-27（Axios 封装 + 认证状态管理）基础上，实现用户端登录/注册页面。采用**分屏品牌化布局**：左侧品牌区（Logo + 渐变 + Slogan），右侧表单。登录/注册为两个独立页面（`/login` + `/register`），共享 `AuthLayout` 复用品牌区。注册成功后前端自动调登录接口拿到 token，写入 authStore 后跳转目标页。

同时执行 REQ-27 PRD 中列出的 15 个手工测试用例，闭环 REQ-27 端到端验证。

## 用户故事

作为玩家，我打开游戏后看到一张有品牌感的登录卡片（左侧 Logo + Slogan，右侧表单），输完用户名密码即可进入主页；想注册时点底部链接跳到注册页，填完字段（含确认密码）后系统自动登录带我进入主页。

作为玩家，在公共电脑上使用时我可以选择不勾选「记住我」（如果引入），关闭浏览器即登出；在私人电脑上勾选后，关闭浏览器重开仍是登录态。

作为玩家，输错密码或用户名已被占用时，表单顶部出现清晰的红色提示 + 短暂 Toast，不会让我猜测到底哪里错了。

## 功能需求

### F1. 路由结构（修改 `src/routes/index.tsx`）

替换 REQ-27 占位的 `/login` 和 `/register` 为真实页面，并引入共享 `AuthLayout`：

```
/login      → AuthLayout > Login
/register   → AuthLayout > Register
```

- 两个路由**不包裹 AuthGuard**（保持 REQ-27 白名单语义）
- 两个路由**不包裹 MainLayout**（独立全屏布局）
- 共享 `AuthLayout` 负责渲染分屏的左侧品牌区 + 右侧 `<Outlet />`，避免两页重复代码

**已登录访问 `/login` / `/register` 行为**：直接跳转 `/home`（避免登录态下还能看到登录页）

### F2. AuthLayout（`src/layouts/AuthLayout.tsx`）

**分屏布局**（桌面端 ≥992px）：

```
┌────────────────────┬────────────────────────┐
│                    │                        │
│   品牌区(flex:1)    │   表单区(flex:1.2)      │
│                    │                        │
│   渐变背景          │   <Outlet />           │
│   🃏 Logo          │                        │
│   Knowledge Game   │                        │
│   Slogan           │                        │
│                    │                        │
└────────────────────┴────────────────────────┘
```

**品牌区样式**：
- 背景：`linear-gradient(135deg, #3b0764, #7c2d12)`（紫→褐渐变）
- 居中显示：
  - `🃏`（font-size: 48px）
  - `Knowledge Game`（font-size: 22px, font-weight: bold）
  - 副标题：「收集 · 升星 · 串联记忆」（font-size: 13px, opacity: 0.7）
  - 副标题2：「把知识点变成你想要的卡牌」（font-size: 13px, opacity: 0.7）
- 品牌区文案固定，不随登录/注册切换

**移动端响应（<992px）**：
- 品牌区隐藏（仅显示表单区全屏）
- 实现方式：纯 CSS media query（在 `AuthLayout.css` 中），不引入 antd Grid 依赖

```css
.brand-area {
  display: flex;
  /* 桌面端默认显示 */
}

@media (max-width: 991px) {
  .brand-area {
    display: none;
  }
  .form-area {
    flex: 1;  /* 表单区全屏 */
  }
}
```

- 此处仅做基础响应（不破坏移动端浏览），完整移动端体验由后续需求负责

**表单区样式**：
- 居中限宽：`max-width: 400px; margin: 0 auto; padding: 32px 24px`
- 暗色背景（继承全局 darkAlgorithm）

### F3. Login 页面（`src/pages/Login/index.tsx`）

**结构**：

```tsx
<Form>
  <Title>欢迎回来</Title>
  <Form.Item name="username" label="用户名" rules={[{required}, {min:2, max:50}]}>
    <Input prefix={<UserOutlined />} placeholder="2-50 字符" />
  </Form.Item>
  <Form.Item name="password" label="密码" rules={[{required}, {min:6, max:50}]}>
    <Input.Password prefix={<LockOutlined />} placeholder="6-50 字符" />
  </Form.Item>
  <Form.Item>
    <Checkbox checked={rememberMe} onChange={...}>记住我</Checkbox>
    <Link to="/forgot-password" style={{float:'right'}}>忘记密码？</Link>
  </Form.Item>
  <Button type="primary" htmlType="submit" loading={submitting} block>登录</Button>
  <div style={{marginTop:16, textAlign:'center'}}>
    没有账号？<Link to="/register">去注册 →</Link>
  </div>
</Form>
```

**字段校验（前端 antd Form rules）**：
- username：必填，长度 2-50（与后端 `@Size(min=2, max=50)` 对齐）
- password：必填，长度 6-50（与后端 `@Size(min=6, max=50)` 对齐）

**「记住我」checkbox（默认勾选）**：
- 控制登录态持久化策略（详见 F7）
- 默认勾选 = 沿用 REQ-27 行为（localStorage 持久化）
- 取消勾选 = 改用 sessionStorage（关闭浏览器即登出）

**「忘记密码」链接**：
- 跳转 `/forgot-password` 占位页（显示「找回密码功能即将上线」）
- 后端无找回密码 API，REQ-28 仅实现占位路由

**提交流程**：
1. 表单 antd 校验通过 → 调 `loginApi({username, password})`
2. 成功：调 `authStore.login(accessToken, refreshToken, expiresIn, user, rememberMe)`，**根据 rememberMe 写入持久化策略**（详见 F7），读取并**校验** URL `redirect` 参数，最后 `navigate(target, {replace: true})`
   - **redirect 校验规则**：必须是合法的站内路径（以 `/` 开头且不以 `//` 开头，防 open redirect），否则回退 `/home`
   - 实现：
     ```ts
     const rawRedirect = new URLSearchParams(location.search).get('redirect');
     const target = rawRedirect && rawRedirect.startsWith('/') && !rawRedirect.startsWith('//')
       ? rawRedirect
       : '/home';
     navigate(target, {replace: true});
     ```
3. 失败（catch ApiError）：
   - `code === 400 && message === '用户名或密码错误'`（`BAD_CREDENTIALS`）→ 表单顶部 antd `Alert` 显示「用户名或密码错误」+ `message.error('登录失败')`
   - 其他业务错误 → 表单顶部 `Alert` 显示 `error.message` + `message.error('登录失败')`
   - 网络错误（`httpStatus === 0`）→ 表单顶部 `Alert` 显示「网络异常，请检查网络连接」+ `message.error`

### F4. Register 页面（`src/pages/Register/index.tsx`）

**结构**：

```tsx
<Form>
  <Title>创建账号</Title>
  <Form.Item name="username" label="用户名" rules={[{required}, {min:2, max:50}]}>
    <Input prefix={<UserOutlined />} placeholder="2-50 字符" />
  </Form.Item>
  <Form.Item name="nickname" label="昵称" rules={[{required}, {min:1, max:50}]}>
    <Input placeholder="展示用名" />
  </Form.Item>
  <Form.Item name="password" label="密码" rules={[{required}, {min:6, max:50}]}>
    <Input.Password prefix={<LockOutlined />} placeholder="6-50 字符" />
  </Form.Item>
  <Form.Item name="confirmPassword" label="确认密码" dependencies={['password']}
    rules={[{required}, ({getFieldValue}) => ({
      validator(_, value) {
        if (!value || getFieldValue('password') === value) return Promise.resolve();
        return Promise.reject(new Error('两次输入的密码不一致'));
      }
    })]}>
    <Input.Password prefix={<LockOutlined />} placeholder="再输一次" />
  </Form.Item>
  <Button type="primary" htmlType="submit" loading={submitting} block>注册</Button>
  <div style={{marginTop:16, textAlign:'center'}}>
    已有账号？<Link to="/login">去登录 →</Link>
  </div>
</Form>
```

**字段校验（前端 antd Form rules）**：
- username：必填，长度 2-50
- nickname：必填，长度 1-50（与后端 `@Size(min=1, max=50)` 对齐）
- password：必填，长度 6-50
- confirmPassword：必填 + 自定义 validator 校验与 password 一致

**提交流程**：
1. 表单 antd 校验通过（含确认密码）→ 调 `registerApi({username, password, nickname})`
2. 成功（拿到 `UserInfo`）→ **立即调 `loginApi({username, password})` 完成自动登录**
3. login 成功：调 `authStore.login(...)`，读取 URL `redirect` 参数（缺失跳 `/home`，校验规则同 F3 步骤 2），`navigate(redirect, {replace: true})`，`message.success('注册成功，欢迎加入 Knowledge Game')`。**用 `replace: true`** 避免注册成功后用户按浏览器回退键回到注册页（与 F3 登录跳转一致）
4. **register 成功 + login 失败兜底**：register 已创建账号但 login 失败（网络异常等），显示 `Modal.error`：「账号已创建，但自动登录失败，请手动登录」，确认后跳 `/login`
5. register 失败（catch ApiError）：
   - `code === 400 && message.includes('用户名已存在')`（`DUPLICATE_USERNAME`）→ username 字段 `validateStatus: 'error'` + `help: '该用户名已被注册'`
     > **为何用 `includes` 而非 `===`**：后端 `UserAppService.register` 抛异常时拼接了 username：`ResultCode.DUPLICATE_USERNAME.getMessage() + ": " + command.getUsername()`，即 `"用户名已存在: zhao"`。F3 的 `BAD_CREDENTIALS` 是精确的 `"用户名或密码错误"`（无后缀），故用 `===`；此处必须用 `includes` 才能命中
   - 其他业务错误 → 表单顶部 `Alert` 显示 `error.message` + `message.error('注册失败')`
   - 网络错误 → 表单顶部 `Alert` 显示「网络异常」+ `message.error`

### F5. 忘记密码占位页（`src/pages/ForgotPassword/index.tsx`）

- antd `Result` `status="info"`，title「找回密码」，subTitle「找回密码功能即将上线，敬请期待」
- 一个「返回登录」按钮 → `navigate('/login')`
- 加入路由表 `/forgot-password`，**包裹在 AuthLayout 内**（与 `/login` `/register` 共享布局），不包裹 AuthGuard / MainLayout

### F6. 已登录拦截（修改 `src/routes/index.tsx` + AuthLayout）

`/login`、`/register`、`/forgot-password` 三个白名单路由加载时检测 `authStore.isAuthenticated()`，若已登录直接 `<Navigate to="/home" replace />`，避免登录态下还能看到登录页。

**实现位置**：在 AuthLayout 顶部判断（统一处理三个白名单路由），不在每个页面重复判断。

**白名单实现机制澄清**：

本 PRD 涉及两种"白名单"概念，需明确区分：

| 白名单类型 | 实现机制 | 条目 |
|---------|---------|------|
| **路由白名单**（不需登录即可访问的路由） | **通过路由结构隐式实现**：`/login`、`/register`、`/forgot-password` 在 `routes/index.tsx` 中**不被 `AuthGuard` 包裹**，因此默认公开。无显式白名单常量 | `/login`、`/register`、`/forgot-password`（REQ-28 新增第三个） |
| **API 端点白名单**（REQ-27 已实现，401 拦截器跳过刷新） | 通过 `api-client.ts` 中显式常量 `AUTH_WHITELIST` 实现，命中后 401 直接 reject 不触发刷新 | `/api/users/login`、`/api/users/register`、`/api/users/refresh-token` |

**REQ-28 对两种白名单的影响**：
- 路由白名单：新增 `/forgot-password` 路由（同样不包裹 AuthGuard）
- API 端点白名单：**无新增**（`/forgot-password` 是占位页，不发任何 API 请求）

### F7. 「记住我」持久化策略（修改 `src/store/auth-store.ts`）

REQ-27 默认全持久化到 localStorage。REQ-28 引入「记住我」checkbox，根据勾选状态切换 storage。

**核心难点**：zustand `persist` 在 store 创建时绑定 storage，无法运行时直接替换。**采用方案：在 store 中维护 `rememberMe: boolean` 状态字段，自定义 `storage` adapter 代理到正确的底层 storage。**

**实现方案**：

```ts
// auth-store.ts

// 全局记录当前 rememberMe 偏好（独立于 store state，避免循环依赖）
let currentRememberMe = true;

const dynamicStorage = createJSONStorage(() => {
  // 每次读写时根据 currentRememberMe 选择底层 storage
  return currentRememberMe ? localStorage : sessionStorage;
});

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      // ... REQ-27 原字段
      rememberMe: true,  // 新增：默认 true 兼容 REQ-27

      login: (accessToken, refreshToken, expiresIn, user, remember = true) => {
        // 切换底层 storage 前先迁移已有数据
        if (remember !== currentRememberMe) {
          migrateAuthStorage(remember);
        }
        currentRememberMe = remember;
        set({ accessToken, refreshToken, expiresIn, user, rememberMe: remember });
      },

      setRememberMe: (remember: boolean) => {
        if (remember === currentRememberMe) return;
        migrateAuthStorage(remember);
        currentRememberMe = remember;
        set({ rememberMe: remember });
      },
      // ...
    }),
    {
      name: 'auth-storage',
      storage: dynamicStorage,
    },
  ),
);

// 迁移：从旧 storage 读 auth-storage → 写入新 storage → 删除旧 storage key
function migrateAuthStorage(targetRemember: boolean) {
  const fromStorage = currentRememberMe ? localStorage : sessionStorage;
  const toStorage = targetRemember ? localStorage : sessionStorage;
  const data = fromStorage.getItem('auth-storage');
  if (data) {
    toStorage.setItem('auth-storage', data);
    fromStorage.removeItem('auth-storage');
  }
}
```

**关键点**：
- `currentRememberMe` 是模块级变量，不进入 store state（避免循环依赖）
- `dynamicStorage` 每次读写时根据 `currentRememberMe` 代理到 localStorage / sessionStorage
- `rememberMe` 字段进入 store state（持久化到当前 storage），用于 UI 显示 checkbox 状态
- 切换 storage 时调用 `migrateAuthStorage` 同步数据，避免状态丢失

**Hydration 时机陷阱（重要）**：

zustand `persist` 在 store 创建时立即调 `getItem` 做 hydration，此时 `currentRememberMe` 还是默认值 `true`，因此首次 hydration **总是从 localStorage 读**。如果用户上次选了 sessionStorage（`rememberMe=false`），刷新后会发生：

1. 模块加载 → `currentRememberMe = true`（默认）
2. store 创建 → persist 调 `getItem('auth-storage')` → 从 localStorage 读（**读到 null**，因为数据在 sessionStorage 里）
3. store hydrated 完毕 → state 全部为初始值（未登录）
4. UI 渲染 → `authStore.isAuthenticated()` 为 false → 用户被踢回 `/login`

为避免此问题，**模块加载时先从 localStorage 读 `rememberMe` 字段初始化 `currentRememberMe`**：

```ts
// auth-store.ts 顶部（store 创建之前）
function readRememberMeFromStorage(): boolean {
  // 优先尝试 localStorage，再尝试 sessionStorage
  // 解析 auth-storage JSON，取 rememberMe 字段
  try {
    const localRaw = localStorage.getItem('auth-storage');
    if (localRaw) {
      const parsed = JSON.parse(localRaw);
      if (typeof parsed?.state?.rememberMe === 'boolean') {
        return parsed.state.rememberMe;
      }
    }
    const sessionRaw = sessionStorage.getItem('auth-storage');
    if (sessionRaw) {
      const parsed = JSON.parse(sessionRaw);
      if (typeof parsed?.state?.rememberMe === 'boolean') {
        return parsed.state.rememberMe;
      }
    }
  } catch {
    // 解析失败走默认值
  }
  return true;  // 默认 true 兼容 REQ-27
}

let currentRememberMe = readRememberMeFromStorage();
```

这样 store 创建时 `currentRememberMe` 已是上次的偏好，hydration 从正确的 storage 读取。

**与 REQ-27 的兼容性**：
- `login()` 签名 `login(access, refresh, exp, user, remember = true)`，默认 true 保持 REQ-27 行为
- MainLayout 的 `handleLogout()` 调用不变（不需要 remember 参数）
- REQ-27 所有现有测试应保持通过（默认 remember=true 走 localStorage）
- REQ-27 测试需补充：remember=false 时数据写入 sessionStorage 而非 localStorage；setRememberMe 切换时数据正确迁移

### F8. 错误处理 UX 规范

| 错误类型 | UX 表现 | 实现 |
|---------|---------|------|
| 字段校验失败（必填/长度/密码不一致） | 字段下方红色文字 | antd `Form.Item` 内置 `rules` |
| 业务错误（用户名或密码错/用户名已存在） | 表单顶部红色 `Alert` + 顶部 `message.error` Toast | `useState<ApiError>` + `message` |
| 网络错误（无响应） | 表单顶部红色 `Alert`「网络异常，请检查网络连接」+ Toast | 同上，`httpStatus === 0` 分支 |
| Token 失效（登录态过期） | REQ-27 已实现：Modal 弹窗 + 跳 `/login` | 不重复实现 |

**Alert 显示规则**：每次提交失败时清空旧 Alert 显示新错误；提交成功后跳转，Alert 自然消失。

### F9. Loading 状态

- 提交按钮 `loading={submitting}`，提交期间禁用
- `submitting` 为 `useState<boolean>`，try 前置 true，finally 置 false
- 防重复提交：按钮 loading 状态阻止二次点击

### F10. 文件结构

```
frontend/user/src/
├── layouts/
│   └── AuthLayout.tsx              ← 新增
│   └── __tests__/
│       └── AuthLayout.test.tsx     ← 新增
├── pages/
│   ├── Login/
│   │   ├── index.tsx               ← 新增
│   │   └── __tests__/
│   │       └── index.test.tsx      ← 新增
│   ├── Register/
│   │   ├── index.tsx               ← 新增
│   │   └── __tests__/
│   │       └── index.test.tsx      ← 新增
│   └── ForgotPassword/
│       ├── index.tsx               ← 新增（占位页）
│       └── __tests__/
│           └── index.test.tsx      ← 新增（占位页冒烟测试：渲染 + 返回登录跳转）
├── routes/
│   └── index.tsx                   ← 修改：替换占位为真实页面 + AuthLayout 包裹
└── store/
    └── auth-store.ts               ← 修改：新增 setRememberMe + login 增加 remember 参数
    └── __tests__/
        └── auth-store.test.ts      ← 修改：新增 remember=false 用例
```

## 非功能需求

### 鉴权

- `/login`、`/register`、`/forgot-password` 三个白名单路由不要求登录
- 已登录访问白名单路由自动跳转 `/home`
- 登录成功后写入 authStore，后续请求自动注入 Bearer token（REQ-27 已实现）
- 注册成功后自动登录写入 authStore，跳转目标页

### 性能

- 表单提交使用 antd `loading` 状态防抖
- AuthLayout 品牌区无图片资源（纯 CSS 渐变 + emoji），无网络请求开销
- 路由切换时 AuthLayout 不重渲染（同结构 Outlet 复用）

### 安全

- 密码字段使用 antd `Input.Password`（含眼睛图标，可切换显隐）
- 不在 URL 中携带密码（login/register 都用 POST body）
- 注册成功后内存中清除 confirmPassword 字段（避免泄露）
- 沿用 REQ-27 的 token 黑名单机制（logout 时 invalidate）

### 兼容性

- 桌面端 ≥992px：完整分屏布局
- 移动端 <992px：品牌区隐藏，表单区全屏（基础响应）
- 完整移动端 UX 由后续需求负责

## 前置依赖

| 需求 | 状态 | 依赖内容 |
|------|------|---------|
| REQ-26 用户端脚手架 | `done` | 暗色主题 antd 5、CSS Modules、Vite + Vitest + RTL、路由表预留 `/login` `/register` |
| REQ-27 Axios 封装 + 认证状态管理 | `done` | `auth-api.ts` 提供 `loginApi`/`registerApi`；`auth-store.ts` 提供 `login`/`logout`；`AuthGuard` 提供未登录跳转 |
| REQ-01 用户注册/登录 API（后端） | `done` | `POST /api/users/login`、`POST /api/users/register` 已实现，字段校验规则：username 2-50、password 6-50、nickname 1-50 |

## Impact Analysis

### 新增文件

| 文件 | 说明 |
|------|------|
| `frontend/user/src/layouts/AuthLayout.tsx` | 分屏布局（左品牌区 + 右 Outlet），含已登录拦截 |
| `frontend/user/src/layouts/__tests__/AuthLayout.test.tsx` | 已登录拦截 + 品牌区渲染测试 |
| `frontend/user/src/pages/Login/index.tsx` | 登录表单 |
| `frontend/user/src/pages/Login/__tests__/index.test.tsx` | 表单校验 + 提交成功/失败用例 |
| `frontend/user/src/pages/Register/index.tsx` | 注册表单 + 自动登录 |
| `frontend/user/src/pages/Register/__tests__/index.test.tsx` | 表单校验 + 注册成功自动登录 + register 成功/login 失败兜底 |
| `frontend/user/src/pages/ForgotPassword/index.tsx` | 占位页 |
| `frontend/user/src/pages/ForgotPassword/__tests__/index.test.tsx` | 占位页冒烟测试（渲染 + 返回登录跳转） |

### 修改文件

| 文件 | 变更 | 说明 |
|------|------|------|
| `frontend/user/src/routes/index.tsx` | `/login` `/register` 占位替换为 `AuthLayout > Login/Register`；新增 `/forgot-password` | REQ-27 占位路由替换 |
| `frontend/user/src/store/auth-store.ts` | 新增 `setRememberMe(remember)`；`login` 增加 `remember = true` 参数；storage 动态切换 + 迁移 | 兼容 REQ-27 默认行为 |
| `frontend/user/src/store/__tests__/auth-store.test.ts` | 新增 remember=false 用例（写入 sessionStorage） | 保持原用例 + 新增 |

### 受影响的现有功能

| 影响 | 说明 |
|------|------|
| REQ-27 auth-store | `login()` 签名扩展（新增可选 remember 参数，默认 true 兼容），storage 策略可切换。**原有测试应全部通过**（默认 remember=true 走 localStorage） |
| REQ-27 路由占位 | REQ-27 占位路由被 REQ-28 替换为真实页面，行为升级 |
| MainLayout 用户区 | 不受影响（REQ-27 已实现登录态显示用户信息） |

### 向前兼容检查

| 未来需求 | 与 REQ-28 的约束关系 |
|---------|---------------------|
| 忘记密码找回（实际功能） | 替换 `/forgot-password` 占位页为真实流程，需后端支持 |
| 移动端适配 | AuthLayout 已做 ≥992px 响应式断点；移动端完整 UX 由后续需求补足 |
| 第三方登录（OAuth2 / 微信 / QQ） | REQ-27 PRD 已声明「未规划」；如未来引入，登录页需增加「其他登录方式」分区，不破坏现有结构 |
| 验证码（图形/短信） | 后端无支持；如未来引入，注册页需增加验证码字段，与表单校验逻辑独立 |
| 邀请码 / 用户协议勾选 | 当前未引入；如未来引入，注册表单底部追加字段 |

## Verification Plan

### 手动验证步骤

> 前置：启动后端 `cd backend/knowledge-game-app && mvn spring-boot:run`，启动前端 `cd frontend/user && npm run dev`

#### A. REQ-28 自身功能（13 个用例）

| # | 用例 | 操作 | 预期 |
|---|------|------|------|
| 1 | 进入登录页 | 浏览器打开 `/login` | 显示分屏：左品牌区（紫褐渐变 + 🃏 + Knowledge Game + Slogan）+ 右登录表单（用户名/密码/记住我/忘记密码/登录按钮/去注册链接） |
| 2 | 进入注册页 | 浏览器打开 `/register` | 同样分屏布局，右表单为注册（用户名/昵称/密码/确认密码/注册按钮/去登录链接） |
| 3 | 登录页字段校验 - 用户名为空 | 直接点登录 | 用户名下方红字「请输入用户名」 |
| 4 | 登录页字段校验 - 用户名过短 | 输入 `a` → 失焦 | 用户名下方红字「用户名长度需 2-50 字符」 |
| 5 | 登录页成功 | 输入正确账号密码 → 点登录 | 按钮变 loading → 成功 → 跳转 `/home` → Header 显示用户昵称 |
| 6 | 登录失败 - 密码错 | 输入错误密码 | 表单顶部红色 Alert「用户名或密码错误」+ 顶部 Toast「登录失败」 |
| 7 | 注册成功 - 自动登录 | 注册页填合法字段 → 点注册 | loading → 注册成功 Toast → 自动登录 → 跳转 `/home` → Header 显示新用户昵称 |
| 8 | 注册失败 - 用户名重复 | 注册已存在用户名 | username 字段红字「该用户名已被注册」 |
| 9 | 注册失败 - 密码不一致 | password=abc123, confirmPassword=abc124 → 提交 | 确认密码字段红字「两次输入的密码不一致」 |
| 10 | register 成功 + login 失败兜底 | DevTools Network 拦截 `/api/users/login` 返回失败 → 注册页填合法字段 → 点注册 | 注册请求成功 → login 请求失败 → 弹 Modal「账号已创建，但自动登录失败，请手动登录」→ 跳 `/login` |
| 11 | 已登录访问白名单 | 模拟登录态（localStorage 写入 auth-storage）→ 访问 `/login` | 自动跳转 `/home`，不显示登录表单 |
| 12 | redirect 参数跳转 | 未登录 → 浏览器手动访问 `/login?redirect=/card-bag` → 输入账号密码登录成功 | 跳转 `/card-bag`（当前路由未实现，落到 NotFound 是预期）。**验证目标**：redirect 参数被读取并跳转，而非默认 `/home`。如果回退到 `/home`，说明 redirect 读取逻辑有问题 |
| 13 | 「记住我」切换 | 取消勾选记住我 → 登录 → 关闭浏览器标签 → 重新打开 | 不再是登录态（sessionStorage 已清空）；勾选记住我则保持登录 |

#### B. 忘记密码占位 + 移动端响应（2 个用例）

| # | 用例 | 操作 | 预期 |
|---|------|------|------|
| 14 | 占位页 | 登录页点「忘记密码？」| 跳转 `/forgot-password`，显示「找回密码功能即将上线」+ 返回登录按钮 |
| 15 | 移动端响应 | DevTools 切换设备模拟 → 选择手机机型（视口 <992px）→ 访问 `/login` | 品牌区隐藏，仅显示右侧表单区全屏；表单可正常输入提交（CSS media query 控制，jsdom 无法覆盖） |

#### C. REQ-27 端到端验证（15 个用例，复制自 REQ-27 PRD Verification Plan）

> REQ-28 完成后即可执行（之前 REQ-27 因无登录页只能通过 localStorage mock 验证，现在可端到端验证）。**预期升级**：原 REQ-27 PRD 中"显示登录页占位"的预期升级为"显示真实登录页"。

| # | 用例 | 操作 | 预期 |
|---|------|------|------|
| 16 | 未登录拦截 | 浏览器打开 `http://localhost:5173/home` | 跳转 `/login?redirect=%2Fhome`，显示真实登录页 |
| 17 | 白名单不拦截 | 访问 `/login` | 显示登录页，不重定向 |
| 18 | 注册页不拦截 | 访问 `/register` | 显示注册页，不重定向 |
| 19 | 根路径重定向 | 访问 `/` | → `/home` → AuthGuard → `/login?redirect=%2Fhome` |
| 20 | 真实登录态 | 通过登录页登录 | Header 显示用户昵称 |
| 21 | 退出登录 | 已登录态点击 Header 用户昵称 → 下拉菜单 → 退出登录 | 清除 token，跳转 `/login` |
| 22 | F5 保持登录态（默认 remember=true） | 登录后 F5 刷新 | Header 仍显示用户昵称（localStorage 持久化） |
| 23 | 清除登录态 | 删除 localStorage 的 `auth-storage` key → 刷新 | 显示「未登录」，访问 `/home` 被拦截 |
| 24 | API 携带 token | 已登录态，DevTools Network 观察任意 `/api/` 请求 | 请求头含 `Authorization: Bearer <token>` |
| 25 | 单元测试 | `npm run test` | 全部通过 |
| 26 | 类型检查 | `npx tsc --noEmit` | 无错误 |
| 27 | 生产构建 | `npm run build` | 构建成功 |
| 28 | ESLint | `npx eslint src/` | 无报错 |
| 29 | access token 过期自动刷新 | 将后端 access token 有效期调短为 1min → 登录 → 等待过期 → 访问需认证 API | Network 可见 1 次 401 + 1 次 `/api/users/refresh-token` → 重放成功 |
| 30 | refresh token 过期 | 将 localStorage 中 refreshToken 设为无效值 → 访问 API | 弹出 Modal「登录已过期」/「请重新登录」，确认后跳转 `/login` |

### 自动化测试覆盖

| 测试文件 | 覆盖场景 | 预期断言 |
|---------|---------|---------|
| `AuthLayout.test.tsx` | 未登录渲染子路由 | Outlet 正常渲染 |
| | 已登录跳转 /home | `authStore.isAuthenticated()` 为 true 时 `<Navigate to="/home" />` |
| | 品牌区文案 | 含「Knowledge Game」+「收集 · 升星 · 串联记忆」+「把知识点变成你想要的卡牌」 |
| `Login.test.tsx` | 表单校验 - 必填 | 用户名/密码为空时点登录，红字提示出现 |
| | 表单校验 - 长度 | 用户名 1 字符失焦，红字提示「2-50 字符」 |
| | 登录成功跳转 | mock `loginApi` resolve → `authStore.login` 被调用 → `navigate` 跳 `/home` |
| | 登录失败 - BAD_CREDENTIALS | mock `loginApi` reject ApiError(400, '用户名或密码错误') → Alert 显示 |
| | 登录失败 - 网络错误 | mock reject ApiError(0) → Alert 显示「网络异常」 |
| | redirect 参数 | URL 含 `?redirect=/card-bag` → 登录成功后 navigate('/card-bag')（验证 redirect 读取，而非默认 /home） |
| | rememberMe 默认勾选 | Checkbox 默认 checked |
| | rememberMe 取消勾选 | 取消后 login 调用传入 remember=false |
| | 忘记密码链接 | 点击 → navigate('/forgot-password') |
| | 去注册链接 | 点击 → navigate('/register') |
| `Register.test.tsx` | 表单校验 - 必填 | 4 个字段任一为空，红字提示 |
| | 表单校验 - 确认密码不一致 | password=abc123, confirmPassword=abc124 → 红字 |
| | 注册成功自动登录 | mock registerApi + loginApi 双 resolve → authStore.login 被调用 → navigate 跳转 |
| | 注册失败 - 用户名重复 | mock registerApi reject ApiError(400, '用户名已存在: xxx') → username 字段 validateStatus=error |
| | register 成功 + login 失败 | mock registerApi resolve + loginApi reject → Modal.error 弹出 + navigate('/login') |
| | 去登录链接 | 点击 → navigate('/login') |
| `auth-store.test.ts`（REQ-27 扩展） | 默认 remember=true 走 localStorage | 原 REQ-27 用例保持通过 |
| | remember=false 走 sessionStorage | login(...,false) 后 sessionStorage 有 auth-storage，localStorage 无 |
| | setRememberMe 切换迁移 | 切换时旧 storage 数据迁移到新 storage |
| `ForgotPassword.test.tsx` | 占位页渲染 | 含「找回密码功能即将上线」+ 返回登录按钮 |
| | 返回登录跳转 | 点击按钮 → navigate('/login') |

### 回滚标准

- 删除新增的 `AuthLayout.tsx`、`Login/`、`Register/`、`ForgotPassword/` 目录及测试
- 还原 `routes/index.tsx` 为 REQ-27 占位版本
- 还原 `auth-store.ts` 为 REQ-27 版本（移除 remember 参数与 storage 切换）
- 还原 `auth-store.test.ts`
- 无数据库变更，无后端变更，前后端独立，回滚无副作用

## 验收标准

1. 访问 `/login` 显示分屏登录页（左品牌区 + 右表单），暗色主题正确
2. 访问 `/register` 显示分屏注册页（含确认密码字段）
3. 登录页字段校验生效（必填、长度 2-50、6-50）
4. 注册页字段校验生效（含确认密码一致性校验）
5. 登录成功跳转 redirect 参数路径，缺失则 `/home`
6. 注册成功后前端自动登录并跳转，Header 显示新用户昵称
7. 登录失败显示 Alert + Toast
8. 注册用户名重复显示字段级红字
9. register 成功 + login 失败兜底 Modal 正常弹出
10. 已登录访问 `/login` `/register` `/forgot-password` 自动跳转 `/home`
11. 「记住我」勾选/不勾选行为符合 F7 规范
12. 「忘记密码」跳转占位页
13. REQ-27 端到端验证 15 个用例全部通过（用例 16-30）
14. `npm run test` 全部通过（至少新增 4 个测试文件 + auth-store 扩展用例）
15. `npm run build` 通过，无 TS 错误
16. `npx tsc --noEmit` 通过
17. `npm run lint` 通过

## 技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 视觉风格 | 分屏品牌化（左品牌区 + 右表单） | 桌面端主流玩法，沉浸感强，留足品牌空间 |
| 页面组织 | 两个独立页面 + AuthLayout 共享品牌区 | 与 REQ-27 路由表对齐；AuthLayout 复用消除重渲染顾虑 |
| 注册字段 | + 确认密码 | 防误输密码；前端纯校验，零后端改动 |
| 注册后行为 | 自动登录后跳转 | 现代 Web App 标配；一次操作完成 |
| 「记住我」实现 | 动态切换 storage（localStorage ↔ sessionStorage） | 兼容 REQ-27 默认行为；公共电脑/私人电脑场景区分 |
| 「忘记密码」处理 | 占位页 + 即将上线提示 | 后端无 API，留入口不阻塞用户 |
| 错误处理 UX | 字段级 Form.Item + 业务级 Alert + Toast | 字段错误用内联避免噪声；业务错误用 Alert 突出 + Toast 告知 |
| Loading 状态 | antd Button loading + submitting state | 防重复提交，无额外组件 |
| 移动端适配 | 仅 ≥992px 响应式（品牌区隐藏） | REQ-26 已声明桌面优先；完整移动端由后续需求负责 |
| 品牌区元素 | 纯 CSS 渐变 + emoji | 无图片资源，无网络请求，加载零开销 |

## 不包含的内容

| 内容 | 负责需求 |
|------|---------|
| 找回密码实际功能（含邮件/短信验证） | 后续需求（需后端支持） |
| 第三方登录（OAuth2 / 微信 / QQ） | REQ-27 PRD 已声明「未规划」 |
| 验证码（图形/邮箱/短信） | 后续需求（需后端支持） |
| 邀请码 / 用户协议勾选 | 未规划 |
| 移动端完整 UX（断点细节、触控手势） | 后续需求 |
| 注册成功邮件通知 | 后续需求（需邮件服务集成） |
| 多设备登录踢下线 | 未规划 |
| 品牌区插画 / 卡牌动画 / 视觉细化 | 后续视觉细化需求 |
| MainLayout 改造 | REQ-27 已完成，REQ-28 不动 |
