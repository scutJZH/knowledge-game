# REQ-26 用户端前端脚手架（React + Vite）

## 产品定位

为 Knowledge Game 用户端（玩家侧）搭建前端脚手架，提供项目骨架、路由、Layout 容器和占位页面。后续用户端需求（REQ-27 Axios 封装 + 认证状态、REQ-28 登录注册、REQ-29+ 各游戏/管理页面）在此基础上填充具体业务。

**桌面端优先**，后续需求阶段再做移动端全面适配。本需求为移动端预留架构（CSS 相对单位、Layout 限宽居中、antd 响应式断点能力），但不实现具体移动端断点。

## 用户故事

作为玩家，我需要一个能流畅运行游戏的用户端 Web 应用骨架，以便后续各功能模块（登录、首页、秒判、Boss、串联、图鉴、卡包、盲盒等）能快速接入。

作为前端开发，我需要一个配置完整的 Vite + React + TypeScript 项目，预装 UI 库、路由、HTTP 客户端、测试框架、代码规范工具，以便每个新需求直接进入业务开发而非环境搭建。

## 功能需求

### F1. 项目初始化

- 在 `frontend/` 目录下执行 `npm create vite@latest user -- --template react-ts` 生成 `frontend/user/`
- 技术栈：React 18 + TypeScript 5 + Vite 5
- 清理 Vite 默认模板内容（默认 react Logo、App.css 中的示例样式、Counter 示例代码、public/vite.svg 可保留也可替换）
- 保留 Vite 默认的 `vite-env.d.ts`、`tsconfig.json`、`tsconfig.node.json`
- `index.html` `<title>` 改为 `Knowledge Game`，`lang="zh-CN"`

### F2. 依赖装齐（REQ-26 仅装依赖，业务封装留给后续需求）

**生产依赖**：
- `react@^18.3`、`react-dom@^18.3`
- `react-router-dom@^6.26`（路由，v6.4+ data API）
- `antd@^5.21`（UI 库，与 admin 同生态）
- `@ant-design/icons@^5.5`
- `axios@^1.7`（REQ-27 才封装拦截器）
- `zustand@^4.5`（REQ-27 才用做认证状态）
- `dayjs@^1.11`（时间处理，与 admin 一致）

**开发依赖**：
- `@types/react@^18.3`、`@types/react-dom@^18.3`、`@types/node@^20`
- `@vitejs/plugin-react@^4`
- `@testing-library/react@^16`、`@testing-library/jest-dom@^6`、`@testing-library/user-event@^14`
- `vitest@^2.1`、`jsdom@^25`、`@vitest/coverage-v8@^2.1`
- `eslint@^9`、`@typescript-eslint/parser@^8`、`typescript-eslint@^8`、`eslint-plugin-react@^7`、`eslint-plugin-react-hooks@^4`、`eslint-config-prettier@^9`（ESLint 9 + flat config，与 Vite 5 / TS 5 生态对齐；admin 端 UmiJS 4 自带 ESLint 8 与此独立，无需对齐）
- `prettier@^3.3`
- `husky@^9`、`lint-staged@^15`

### F3. Layout 容器（`src/layouts/MainLayout.tsx`）

**顶导布局**：

```
┌────────────────────────────────────────────────────────────┐
│ 🃏 Knowledge Game     首页  图鉴  卡包  我的      [用户占位] │ ← Header
├────────────────────────────────────────────────────────────┤
│                                                            │
│              主内容区（max-width: 1200px, 居中）             │ ← Content <Outlet />
│                                                            │
└────────────────────────────────────────────────────────────┘
```

- 使用 antd `Layout` + `Layout.Header` + `Layout.Content`
- **Logo**：Header 左侧显示 `🃏 Knowledge Game` 文字 Logo
- **主菜单**：使用 antd `Menu`，`mode="horizontal"`，占位项：
  - 首页 → `/home`
  - 图鉴 → `/collection`（占位，目标 404）
  - 卡包 → `/card-bag`（占位，目标 404）
  - 我的 → `/profile`（占位，目标 404）
- **用户区占位**：Header 右侧预留 `UserOutlined` 图标 + "未登录" 文本（REQ-27 接入真实数据）
- **暗色主题**：通过 antd `ConfigProvider theme={{ algorithm: theme.darkAlgorithm }}` 全局开启（在 `App.tsx` 包裹）
- **限宽居中**：Content 内部 `max-width: 1200px; margin: 0 auto; padding: 24px 16px`

### F4. 路由配置（`src/routes/index.tsx`）

使用 React Router v6 `createBrowserRouter`（v6.4+ data API，未来可平滑升级 v7）。

| 路径 | 组件 | Layout 包裹 | 说明 |
|------|------|------------|------|
| `/` | `Navigate` → `/home` | — | 根路径重定向 |
| `/home` | `Home` | MainLayout | 占位首页 |
| `*` | `NotFound` | MainLayout | 404 兜底页，未注册路径（含 `/collection`、`/card-bag`、`/profile` 等菜单占位项）由此路由匹配并渲染 NotFound |

**未来需求路由（REQ-26 不实现，仅目录预留）**：

| 路径 | 负责需求 |
|------|---------|
| `/login`、`/register` | REQ-28（绕过 MainLayout，独立布局） |
| `/categories` | REQ-29 |
| `/game/quick-judge` | REQ-30 |
| `/game/boss` | REQ-31 |
| `/game/chain` | REQ-32 |
| `/reward/flip` | REQ-33 |
| `/settlement` | REQ-34 |
| `/collection` | REQ-35 |
| `/blind-box` | REQ-36 |
| `/card/{id}` | REQ-37 |
| `/pity` | REQ-38 |
| `/profile` | REQ-39 |
| `/card-bag` | REQ-71 |
| `/check-in` | REQ-72 |

### F5. 占位页面

**Home（`src/pages/Home/index.tsx`）**：
- antd `Typography.Title` 显示 "Knowledge Game"
- antd `Typography.Paragraph` 显示 "REQ-26 脚手架已就位，等待后续需求填充业务页面"
- antd `Card` 列出后续需求将填充的页面入口（仅展示文本，不跳转）

**NotFound（`src/pages/NotFound/index.tsx`）**：
- antd `Result` `status="404"`，`title="404"` `subTitle="页面不存在"`
- `extra={<Button type="primary" onClick={goHome}>返回首页</Button>}`

### F6. 开发代理（`vite.config.ts`）

```ts
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8082',
      changeOrigin: true,
    },
  },
}
```

**关键**：用户端 API 前缀为 `/api/**`（无 `/admin`），与管理端 `/api/admin/**` 不同。后端 `app` 服务实际端口为 **8082**（`backend/knowledge-game-app/src/main/resources/application.yml`），CLAUDE.md 中 8080 是过时记载。

### F7. 代码规范

- **ESLint**（`eslint.config.js` flat config）：
  - `@typescript-eslint/eslint-plugin` recommended 规则
  - `eslint-plugin-react` recommended 规则
  - `eslint-plugin-react-hooks` recommended 规则
  - `eslint-config-prettier`（关闭与 Prettier 冲突的规则）
- **Prettier**（`.prettierrc`）：`singleQuote: true`, `trailingComma: 'all'`, `printWidth: 100`, `tabWidth: 2`（admin 端无显式 `.prettierrc` 文件，依赖 UmiJS 内置默认值，user 端独立配置无需对齐）
- **Husky**（`.husky/pre-commit`）：执行 `npx lint-staged`
- **lint-staged**（`package.json` 中配置，职责分工：eslint 检查代码质量、prettier 检查格式，两者不重叠）：
  - `*.{ts,tsx}` → `eslint --fix`（仅做代码质量修复，格式交给 prettier）
  - `*.{ts,tsx,js,jsx,json,md,css,html}` → `prettier --write`（含 `*.html` 覆盖 `index.html`；CSS Modules 文件 `*.module.css` 不需 eslint，由 prettier 统一格式化）

### F8. 测试框架（Vitest + RTL）

- Vitest 配置写入 `vite.config.ts` 的 `test` 字段（用 `defineConfig` 的三参数泛型 `defineConfig<{ test: }>`），或独立 `vitest.config.ts`（推荐前者，与 Vite 共享 resolve alias）
- **jsdom 环境**：`test.environment: 'jsdom'`
- **全局 setup**：`test.setupFiles: ['./src/test/setup.ts']`
- **setup 文件**（`src/test/setup.ts`）：除了 `import '@testing-library/jest-dom'` 外，主动预置两个 jsdom mock（参考 admin 端 `src/setupTests.ts`，避免首轮测试即踩 Windows + jsdom + antd 兼容性坑）：
  - `window.matchMedia` mock（用 `vi.fn()`，jsdom 缺失，antd 响应式组件如 `Grid.useBreakpoint` 依赖）
  - `window.getComputedStyle` 异常捕获包装（jsdom 在读取伪元素或不存在样式时抛错，影响 antd `Modal`/`Drawer`；捕获异常时返回空 `CSSStyleDeclaration`）
- **路径别名**：`resolve.alias: { '@': '/src' }`，对应 `tsconfig.json` `paths: { "@/*": ["./src/*"] }`
- **脚本**（`package.json`）：
  - `"dev": "vite"`
  - `"build": "tsc && vite build"`
  - `"preview": "vite preview"`
  - `"lint": "eslint . --ext ts,tsx"`
  - `"test": "vitest run"`
  - `"test:watch": "vitest"`
  - `"test:coverage": "vitest run --coverage"`
  - `"prepare": "husky"`（注意：husky v9 用 `husky` 命令初始化）

### F9. 目录结构

```
frontend/user/
├── public/
│   └── vite.svg                           ← 保留 Vite 默认 logo（或后续替换）
├── src/
│   ├── main.tsx                           ← 入口：createRoot + StrictMode
│   ├── App.tsx                            ← 根组件：ConfigProvider + RouterProvider
│   ├── App.test.tsx                       ← App 冒烟测试
│   ├── routes/
│   │   └── index.tsx                      ← createBrowserRouter 路由定义
│   ├── layouts/
│   │   ├── MainLayout.tsx                 ← 顶导 + Outlet
│   │   ├── MainLayout.css                 ← Layout 样式（CSS Modules）
│   │   └── __tests__/
│   │       └── MainLayout.test.tsx        ← Layout 渲染测试
│   ├── pages/
│   │   ├── Home/
│   │   │   ├── index.tsx
│   │   │   └── Home.css
│   │   └── NotFound/
│   │       ├── index.tsx
│   │       └── __tests__/
│   │           └── index.test.tsx
│   ├── components/                        ← 空目录（.gitkeep）
│   ├── services/                          ← 空目录（.gitkeep，REQ-27 填充）
│   ├── store/                             ← 空目录（.gitkeep，REQ-27 填充）
│   ├── utils/                             ← 空目录（.gitkeep）
│   ├── types/                             ← 空目录（.gitkeep）
│   ├── styles/
│   │   └── theme.ts                       ← antd 主题 token（仅 darkAlgorithm，不细化配色）
│   ├── test/
│   │   └── setup.ts                       ← Vitest 全局 setup
│   ├── index.css                          ← 入口样式（CSS reset）
│   └── vite-env.d.ts                      ← Vite 类型声明
├── eslint.config.js                         ← ESLint 9 flat config
├── .prettierrc
├── .prettierignore
├── .husky/
│   └── pre-commit                         ← husky v9 格式（无 shebang）
├── .gitignore                             ← node_modules, dist, *.local, coverage, .env, .env.*（放行 !.env.example）
├── index.html                             ← title: Knowledge Game, lang: zh-CN
├── package.json
├── tsconfig.json                          ← 含 @/* paths、compilerOptions.noEmit: true（避免 tsc 产出 .js 污染源码；Vite 用 esbuild 做 transpile）
├── tsconfig.node.json
└── vite.config.ts                         ← 含 /api 代理 + Vitest 配置 + @ alias
```

## 非功能需求

### 鉴权（独立声明）

**REQ-26 不实现任何鉴权**：
- 所有路由（含 `/home`）公开可访问
- 不读取 token、不跳转登录页、不拦截未登录访问
- 鉴权逻辑由 REQ-27（token 状态管理）+ REQ-28（登录页）联合实现
- **安全警告**：scaffold 阶段产物禁止部署到生产环境

### 性能

- 开发模式 HMR < 200ms（Vite 默认）
- 生产构建产物 gzip 后 < 300KB（含 antd 按需加载，无业务代码）
- antd 5.x 默认支持 ES Module tree-shaking，无需 babel-plugin-import

### 兼容性

- 桌面浏览器：Chrome 90+ / Firefox 88+ / Safari 14+ / Edge 90+
- 暂不支持移动端浏览器（后续需求阶段适配）
- Node.js 20+（Vite 5 最低要求）

## Impact Analysis

### 新增文件

`frontend/user/` 目录全部为新增（约 30 个文件），不影响现有代码。

### 配置变更

| 文件 | 变更 | 说明 |
|------|------|------|
| `CLAUDE.md` | 修正「用户端后端（端口 8080）」→ 8082 | CLAUDE.md 中过时，application.yml 实际为 8082 |
| `docs/overview.md:22` | 修正「用户端 Spring Boot（端口 8080）」→ 8082 | 与 CLAUDE.md 同源过时记载 |
| `docs/overview.md` | 「管理后台前端页面」段后新增「用户端前端页面」段，列出 `/home` 和 404，状态 REQ-26 | 同步现状 |
| `docs/features.md:7` | 修正「用户管理（后端 API，用户端 8080）」→ 8082 | 与 CLAUDE.md 同源过时记载 |
| `docs/requirements.md` | REQ-26 状态 `idea` → `designed`，PRD 列填入 `docs/prd/req-26-user-scaffold.md` | 阶段一完成时 |

### 受影响的现有功能

无。`frontend/user/` 是全新目录，与 admin 端完全独立（admin 在 `frontend/admin/`，互不影响）。

### 向前兼容检查（grep 未开发需求）

| 未来需求 | 与 REQ-26 的约束关系 |
|---------|---------------------|
| **REQ-27**（Axios 封装 + 认证状态管理） | REQ-26 装依赖（axios + zustand + dayjs），REQ-27 实际封装。**约束**：REQ-27 必须使用 REQ-26 已装的版本，不引入冲突依赖。 |
| **REQ-28**（登录/注册页面） | REQ-26 路由表预留 `/login` `/register` 路径。**约束**：REQ-28 在 `src/pages/Login/` `src/pages/Register/` 创建组件，使用独立布局（不包裹 MainLayout）。 |
| **REQ-29**（首页 — 群组选择 + 知识点分类） | REQ-26 占位 `Home/index.tsx` 由 REQ-29 替换。**约束**：REQ-29 修改 Home 内容，不修改 MainLayout 结构。 |
| **REQ-29 集成测试（REQ-08 分类树联调）** | REQ-26 代理 `/api → localhost:8082` 是联调前提。**约束**：REQ-26 代理配置正确，REQ-29 才能调通 `GET /api/knowledge-categories/tree`。 |
| **REQ-30~39, 71~72, 98** 等用户端业务页面 | 全部依赖 REQ-26 的 Layout 容器 + 路由表 + 代理配置。**约束**：REQ-26 必须在 `routes/index.tsx` 用 `children` 嵌套路由，子路由用 `<Outlet />` 渲染。 |
| **未来移动端适配需求** | REQ-26 使用 antd ConfigProvider + CSS 相对单位 + Layout 限宽居中。**约束**：未来移动端适配不应推翻 Layout 容器结构，仅添加断点 + 替换组件（如 Menu 横向 → Drawer 抽屉）。 |

## Verification Plan

### 手动验证步骤

1. **环境准备**：Node.js 20+，在 `frontend/user/` 执行 `npm install`

2. **开发模式启动**：
   ```bash
   cd frontend/user && npm run dev
   ```
   - 访问 `http://localhost:5173`
   - 期望：浏览器显示**暗色主题**的 "Knowledge Game" 首页，顶部 Header 含 Logo + 4 个菜单项（首页/图鉴/卡包/我的）+ 右侧用户占位

3. **路由验证**：
   - 访问 `/` → URL 自动变为 `/home`，显示首页占位内容
   - 访问 `/home` → 显示首页占位
   - 访问 `/nonexistent` → 显示 404 页（antd Result + "返回首页"按钮）
   - 点击"返回首页" → URL 跳转 `/home`

4. **菜单交互**：
   - 当前激活的菜单项高亮（首页）
   - 点击"图鉴" → URL 变为 `/collection`，显示 404（路由未实现）
   - 点击"首页" → URL 回到 `/home`

5. **API 代理（需启动后端 app 服务）**：
   ```bash
   # 在另一个终端
   cd backend/knowledge-game-app && mvn spring-boot:run
   ```
   - 浏览器 DevTools → Network
   - 触发任意 `/api/**` 请求（如在 Console 执行 `fetch('/api/users/login', {method:'POST', body: JSON.stringify({})})`）
   - 期望：请求被代理到 `http://localhost:8082/api/users/login`，返回后端响应（400/401，不再是 Vite dev server 的 404）

6. **代码规范**：
   - 故意写一行 `const x = "hello"` （双引号）→ `git add . && git commit -m "test"` → husky 触发 lint-staged，自动修复为单引号后提交
   - 故意引入未使用变量 → `npm run lint` 报错

7. **生产构建**：
   ```bash
   cd frontend/user && npm run build
   ```
   - 期望：`dist/` 目录生成，无 TS 错误，无 Vite 构建错误
   - `npm run preview` → 访问 `http://localhost:4173` 能正常显示

### 自动化测试覆盖

| 测试文件 | 覆盖场景 | 预期断言 |
|---------|---------|---------|
| `src/App.test.tsx` | App 根组件渲染 | ConfigProvider + RouterProvider 不抛错，渲染出 DOM 节点 |
| `src/layouts/__tests__/MainLayout.test.tsx` | MainLayout 渲染 | 含 "Knowledge Game" 文本；含 4 个菜单项文案（首页/图鉴/卡包/我的）；Outlet 透传子内容 |
| `src/pages/NotFound/__tests__/index.test.tsx` | 404 页面 | 渲染 antd Result status=404；"返回首页"按钮存在；点击后 mockNavigate 被调用 |
| `src/test/black-box/menu-active-state.test.tsx` | 菜单激活态响应路径切换 | /home → /collection → /home 切换时 selectedKeys 正确响应 |
| `src/test/black-box/root-redirect.test.tsx` | 根路径重定向 | / → /home 重定向；未知路径 → NotFound |
| `src/test/black-box/dark-theme.test.tsx` | 暗色主题应用 | darkAlgorithm 配置正确；ConfigProvider 渲染无异常 |
| `src/test/black-box/content-max-width.test.tsx` | Content 限宽居中 | .content div max-width: 1200px；margin: auto 居中 |

**实现演进说明（REQ-26 交付后更新）**：实际交付比 PRD 多 4 个黑盒测试（`src/test/black-box/`），由 ISSUE-5 封闭式 subagent 独立编写，覆盖白盒测试遗漏的集成场景（菜单激活态、根路径重定向、暗色主题、CSS 布局）。

**jsdom 已知限制**：`createBrowserRouter` 和 `createMemoryRouter` 在 jsdom + Node.js 20+ 环境下因 `AbortSignal` 跨 realm `instanceof` 检查失败，触发客户端导航时报错。root-redirect 和 menu-active-state 测试改用 `MemoryRouter` + `Routes`（渲染阶段导航）替代 router 级导航；NotFound 测试改用 `vi.mock('react-router-dom')` mock `useNavigate`。当前 jsdom 25 未解决此问题，后续若 jsdom 修复可恢复 createMemoryRouter 路径。

### 回滚标准

- 如某依赖版本与 Node 20 不兼容 → 降级到兼容版本（如 Vite 5 → Vite 4）
- 如 antd 5.x 暗色主题在 Vite 5 下构建报错 → 切换到 antd 4.x 或回退到亮色主题（仅 darkAlgorithm 改为 defaultAlgorithm）
- 如 Vitest 在 Windows 下不稳定 → 切换到 Jest（与 admin 端统一，但需重新配置）

**回滚操作**：删除 `frontend/user/` 目录即可，无副作用（admin 端独立）。

## 验收标准

1. `npm install` 在 Node.js 20+ 下无报错完成
2. `npm run dev` 启动开发服务器（端口 5173），浏览器控制台无 error
3. 访问 `/` 自动重定向到 `/home`
4. MainLayout 暗色主题正确显示，Logo + 4 个菜单项可见
5. 访问未匹配路由显示 antd Result 404 页，"返回首页"按钮可用
6. `/api/**` 请求被代理到 `localhost:8082`（需启动后端 app 服务）
7. `npm run lint` 无错误
8. `npm run test` 全部通过（至少 3 个测试文件）
9. `npm run build` 成功生成 `dist/`，无 TS 错误
10. CLAUDE.md / docs/overview.md:22 / docs/features.md:7 三处用户端 API 端口均修正为 8082
11. `docs/overview.md` 新增「用户端前端页面」段
12. `docs/requirements.md` REQ-26 状态更新为 `designed`，PRD 列填入路径

## 技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 构建工具 | Vite 5 | HMR 最快，官方推荐，比 UmiJS 4 轻量 |
| 框架 | React 18 | 与 admin 一致，团队已有经验 |
| 路由 | React Router v6.4 `createBrowserRouter` | 主流方案，data API 面向未来，可平滑升级 v7 |
| UI 库 | Ant Design 5.x | 与 admin 同生态，ConfigProvider 原生支持暗色，团队已有经验 |
| 状态管理 | Zustand（REQ-27 才用） | 比 Redux 轻 10 倍，API 简洁，TS 友好 |
| HTTP 客户端 | Axios（REQ-27 才封装） | 拦截器机制成熟，与 admin 端的 umi-request 思路一致 |
| CSS 方案 | CSS Modules | Vite 原生支持零配置；不引入 Tailwind 避免与 antd 样式冲突 |
| 测试框架 | Vitest + RTL | 比 Jest 快 5-10 倍，与 Vite 原生集成 |
| 布局结构 | 顶导 + 居中限宽内容区 | 桌面端主流，游戏沉浸感强 |
| 主题调性 | antd darkAlgorithm | REQ-26 仅开启暗色基调，具体配色（金紫渐变/稀有度色阶）由后续视觉细化需求决定 |
| 开发端口 | 5173（Vite 默认） | 避开 admin 的 8000 |
| API 代理 | `/api → localhost:8082` | 用户端 API 前缀 `/api/**`，端口 8082 |

## 不包含的内容

| 内容 | 负责需求 |
|------|---------|
| Axios 请求拦截器、Token 注入、401 刷新 | REQ-27 |
| 认证状态管理（Zustand store） | REQ-27 |
| 路由守卫、未登录跳转 | REQ-27 + REQ-28 |
| 登录 / 注册页面 | REQ-28 |
| 业务页面（首页/秒判/Boss/串联/图鉴/卡包/盲盒/签到/个人中心等） | REQ-29~39, 71~72, 98 |
| 暗色主题具体配色细化（金紫渐变、稀有度色阶、卡牌发光） | 后续视觉细化需求 |
| 移动端断点 / 触控手势适配 | 后续需求阶段 |
| PWA / Service Worker / 离线支持 | 未规划 |
