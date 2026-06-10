# REQ-41 管理后台 — 登录 + 鉴权

## 背景

REQ-40 脚手架阶段已搭建 Ant Design Pro 前端骨架，登录页为 disabled 占位状态，token 注入和 401 处理逻辑已注释。后端管理端认证 API 已全部实现（AdminLoginAppService + SecurityConfig + JWT 双令牌）。本需求补全前端登录、token 管理、请求拦截和路由守卫。

## 用户故事

作为系统管理员，我需要在管理后台登录后才能访问各功能页面，关闭浏览器后仍保持登录状态，token 过期时自动续签，无需手动重新登录。

## 后端 API（已实现）

| 路径 | 方法 | 请求体 | 响应体 | 说明 |
|------|------|--------|--------|------|
| /api/admin/login | POST | `{ username, password }` | `{ accessToken, refreshToken, expiresIn, user: { id, username, nickname, role } }` | 管理端登录（仅 ADMIN 角色） |
| /api/admin/refresh-token | POST | `{ refreshToken }` | `{ accessToken, refreshToken, expiresIn }` | 刷新令牌（校验 ADMIN 角色） |
| /api/admin/logout | POST | `{ refreshToken }` | `void` | 登出（黑名单双 token） |

- Access Token 有效期：30 分钟（1800 秒），后端通过 expiresIn 字段返回
- Refresh Token 有效期：7 天
- 所有管理端 API 需 JWT + hasRole('ADMIN')
- expiresIn 策略：采用纯被动刷新（仅 401 时触发），不使用 expiresIn 做主动续签。原因：管理后台使用频率低，30 分钟过期 + 被动刷新足够，主动续签增加复杂度无实际收益

## 功能需求

### F1. 登录页

- 启用现有 LoginForm 组件（移除 disabled 属性）
- 用户名和密码为必填项，前端做非空校验
- 提交后调用 `POST /api/admin/login`
- 登录成功：从响应体提取 accessToken、refreshToken 存入 localStorage，提取 user 对象（{ id, username, nickname, role }）存入 localStorage 的 `admin_user_info`，跳转仪表盘
- 全局响应拦截器返回 `result.data`（即 `{ accessToken, refreshToken, expiresIn, user }`），登录页直接从返回值提取 token 存储
- 登录失败：显示后端返回的错误信息（如"用户名或密码错误"、"无管理员权限"）
- 登录中按钮显示 loading 状态，防止重复提交

### F2. Token 管理

- **存储**：localStorage，键名：
  - `admin_access_token` — Access Token
  - `admin_refresh_token` — Refresh Token
  - `admin_user_info` — `{ id, username, nickname, role }`
- **持久化**：关闭浏览器后重新打开仍保持登录状态
- **清除时机**：登出、refreshToken 刷新失败

### F3. 请求拦截器

- **请求拦截**：每个请求自动注入 `Authorization: Bearer {accessToken}`
- **401 响应处理**：
  1. 检查 localStorage 是否有 refreshToken
  2. 无 → 清除所有 token，跳转 `/login`
  3. 有 → 调用 `POST /api/admin/refresh-token`
  4. 刷新成功 → 用新 token 更新 localStorage 并重试原请求
  5. 刷新失败 → 清除所有 token，跳转 `/login`
- **并发控制**：多个请求同时 401 时，仅发一次 refresh 请求，其余排队等待刷新完成后用新 token 重试

### F4. 路由守卫

- `onRouteChange` 拦截：
  - 无 token + 非 `/login` 页面 → 跳转 `/login`
  - 有 token + 在 `/login` 页面 → 跳转 `/`（已登录跳过登录页）
- `getInitialState` 返回 localStorage 中的用户信息，供 ProLayout 消费

### F5. ProLayout 用户菜单

- 顶部栏右侧显示 nickname（无 nickname 时 fallback 到 username）
- 下拉菜单包含「退出登录」选项
- 退出登录调用 `POST /api/admin/logout`，请求体传入 `{ refreshToken }`（从 localStorage 取），清除 localStorage，跳转 `/login`

## 修改范围

| 文件 | 变更 |
|------|------|
| `frontend/admin/src/pages/Login/index.tsx` | 启用表单、接入登录 API、loading 状态 |
| `frontend/admin/src/services/request.ts` | 取消注释 token 注入、实现 401 拦截与 refresh 逻辑 |
| `frontend/admin/src/app.tsx` | 添加 getInitialState、onRouteChange、layout 用户菜单配置 |
| `frontend/admin/config/routes.ts` | 无变更（`/login` 已设 `layout: false`） |

## 非目标

- 不做前端路由级别权限控制（RBAC），所有页面统一需要登录即可
- 不做「记住我」勾选框（默认持久化到 localStorage）
- 不做登录验证码
- 不做修改密码功能
- 不做多标签页同步登出（一个标签页登出不影响其他已打开的标签页，直到其他标签页下次 API 调用触发 401）

## 技术方案

采用 Ant Design Pro 原生模式：利用 UmiJS 的 `getInitialState` 插件管理用户状态，`plugin-layout` 的 ProLayout 渲染用户菜单，`plugin-request` 的拦截器处理 token 注入和刷新。符合框架设计哲学，代码量最少。
