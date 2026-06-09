# REQ-40 管理后台脚手架（Ant Design Pro）

## 产品定位

为 Knowledge Game 系统管理后台搭建前端脚手架，提供布局骨架、路由配置、请求封装和占位页面。后续管理端需求（REQ-41~47, 65~67, 80）在此基础上填充具体业务页面。

## 用户故事

作为系统管理员，我需要一个管理后台基础框架，以便后续各功能模块（IP 系列、卡牌、题库等）能快速接入。

## 功能需求

### F1. 项目初始化

- 使用 `@ant-design/pro-cli` 初始化 Ant Design Pro 项目，选择 `simple` 模板
- 初始化命令：`pro create knowledge-game-admin --template simple`
- 技术栈：React + TypeScript + UmiJS 4 + Ant Design 5.x
- Ant Design Pro 版本：v6+
- 初始化后清理多余模板代码（示例页面、国际化等），仅保留骨架核心
- 保留 Ant Design Pro 默认 404 页面

### F2. 布局

- **侧边栏布局**：左侧固定导航菜单 + 右侧内容区
- **顶部栏**：面包屑导航 + 折叠按钮 + 用户头像占位
- **侧边栏 Logo**：显示 "Knowledge Game" 文字
- 侧边栏支持折叠/展开
- 内容区使用 Ant Design Pro 的 `ProLayout` 组件

### F3. 侧边栏菜单（手风琴折叠）

按业务域分组，支持手风琴式展开/折叠（同时只展开一个分组）：

```
仪表盘
内容管理（可折叠）
  ├─ IP 系列管理
  ├─ 卡牌管理
  ├─ 题库管理
  └─ 知识库管理
运营管理（可折叠）
  ├─ 商品管理
  ├─ 订单管理
  ├─ 抽卡配置
  └─ 成就模板
系统（可折叠）
  └─ 用户管理
```

### F4. 路由配置

| 路径 | 页面组件 | 菜单名称 | 说明 |
|------|---------|---------|------|
| `/dashboard` | Dashboard | 仪表盘 | 默认着陆页，`/` 重定向至此 |
| `/content/ip-series` | IpSeries | IP 系列管理 | 占位页 |
| `/content/card-template` | CardTemplate | 卡牌管理 | 占位页 |
| `/content/question-bank` | QuestionBank | 题库管理 | 占位页 |
| `/content/knowledge-base` | KnowledgeBase | 知识库管理 | 占位页 |
| `/operation/product` | Product | 商品管理 | 占位页 |
| `/operation/order` | Order | 订单管理 | 占位页 |
| `/operation/blind-box` | BlindBox | 抽卡配置 | 占位页，具体可配参数由 REQ-46 定义 |
| `/operation/achievement` | Achievement | 成就模板 | 占位页 |
| `/system/user` | User | 用户管理 | 占位页 |
| `/login` | Login | 登录 | 占位页，REQ-41 填充 |

所有占位页仅显示页面标题（如 "IP 系列管理"），无具体内容。

### F5. 请求封装

- 基于 `umi-request`（Ant Design Pro 内置）
- 统一请求工具 `src/services/request.ts`
- 响应拦截规则：
  - 判断 `result.code === 200` 为成功，返回 `result.data`
  - 非 200 code 时抛出业务错误，预留全局 `message.error` 通知占位
  - 网络异常（超时、断网）统一提示网络错误
- 开发代理：`/api/**` → `http://localhost:8081`
- CORS：后端 `cors.allowed-origins` 已覆盖 `http://localhost:*`，前端无需额外配置
- Token 注入预留：请求拦截器中预留 `Authorization` header 设置点（REQ-41 接入）

### F6. 登录页占位

- 路由 `/login` 存在，显示空白登录页占位
- 登录页布局使用 Ant Design Pro 的 `LoginForm` 组件壳
- 不包含实际登录逻辑（REQ-41 负责）
- 访问受保护页面时暂不拦截（鉴权由 REQ-41 实现）

## 非功能需求

- 开发服务器端口：8000（Ant Design Pro 默认）
- 构建产物可部署到 Nginx 等静态服务器
- 响应式：侧边栏在窄屏下自动折叠

> **安全警告**：本需求不包含鉴权拦截，所有页面无需登录即可访问。**禁止将 scaffold 阶段产物部署到生产环境**。鉴权由 REQ-41 实现。

## 项目目录结构

```
frontend/admin/
├── package.json
├── config/
│   ├── routes.ts               # 路由表
│   ├── proxy.ts                # 开发代理
│   └── defaultSettings.ts      # 布局/主题配置
├── src/
│   ├── app.tsx                 # 运行时配置
│   ├── pages/
│   │   ├── Dashboard/
│   │   │   └── index.tsx
│   │   ├── IpSeries/
│   │   │   └── index.tsx
│   │   ├── CardTemplate/
│   │   │   └── index.tsx
│   │   ├── QuestionBank/
│   │   │   └── index.tsx
│   │   ├── KnowledgeBase/
│   │   │   └── index.tsx
│   │   ├── Product/
│   │   │   └── index.tsx
│   │   ├── Order/
│   │   │   └── index.tsx
│   │   ├── BlindBox/
│   │   │   └── index.tsx
│   │   ├── Achievement/
│   │   │   └── index.tsx
│   │   ├── User/
│   │   │   └── index.tsx
│   │   └── Login/
│   │       └── index.tsx
│   ├── services/
│   │   └── request.ts
│   ├── components/               # 公共组件占位，清理 Ant Design Pro 自带模板组件后保留空目录
│   └── utils/                    # 工具函数占位，清理 Ant Design Pro 自带模板工具后保留空目录
├── .env
└── tsconfig.json
```

## 技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 框架 | Ant Design Pro v6+ CLI 脚手架（simple 模板） | 开箱即用 ProLayout + ProTable + 路由管理 |
| 构建工具 | UmiJS 4（内置 Webpack） | Ant Design Pro 深度集成 |
| 布局组件 | ProLayout | 自带侧边栏、面包屑、折叠、手风琴菜单 |
| 请求库 | umi-request | Ant Design Pro 内置，与 ProTable 集成好 |
| 代理目标 | localhost:8081 | 管理端后端端口 |

## 不包含的内容

| 内容 | 负责需求 |
|------|---------|
| 登录鉴权逻辑 | REQ-41 |
| 各业务模块 CRUD 页面 | REQ-43~47, 65~67, 80 |
| 数据统计仪表盘 | REQ-42 |

## 验收标准

1. `npm run dev` 能正常启动开发服务器（端口 8000）
2. 浏览器访问后能看到侧边栏布局，菜单按手风琴分组正确显示
3. 点击每个菜单项能跳转到对应路由，显示占位页面标题
4. `/` 自动重定向到 `/dashboard`
5. 开发代理能正常转发 `/api/**` 请求到 `localhost:8081`（需先启动 admin 后端服务）
6. 无多余模板代码（Ant Design Pro 示例页面已清理）
7. 未匹配路由显示默认 404 页面
