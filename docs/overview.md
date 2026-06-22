# Knowledge Game — 系统概览

> 最新状态快照，每次需求完成或变更时同步更新。无需遍历 PRD 即可了解系统全貌。

## 技术栈

- 后端：Spring Boot 3.x (JDK 21) + Spring Cloud Alibaba (Nacos + OpenFeign) + MySQL + Spring Data JPA + MapStruct + Spring Security + JWT
- 前端：React + TypeScript + Vite
- 管理后台：Ant Design Pro (React + UmiJS 4)

## 模块架构

```
knowledge-game/
├── backend/
│   ├── knowledge-game-core/           Spring Boot Starter（domain + infrastructure + common）
│   │   └── com.knowledgegame.core.*   领域实体、端口、仓储适配器、通用工具
│   ├── knowledge-game-components/     组件模块父工程
│   │   ├── component-auth/            认证组件 Starter（BCrypt + JWT + Token 黑名单 + SecurityUtils）
│   │   ├── component-m2m-auth/        机机鉴权 Starter（API Key + Feign 拦截器 + 服务端 Filter）
│   │   └── component-feign/           Feign Client 接口共享 + 公共 DTO（FileServiceClient + UploadCredentialResponse）
│   ├── knowledge-game-app/            用户端 Spring Boot（端口 8082）
│   │   └── com.knowledgegame.app.*    Controller + DTO + Assembler + AppService
│   ├── knowledge-game-admin/          管理端 Spring Boot（端口 8081）
│   │   └── com.knowledgegame.admin.*  Controller + DTO + Assembler + AppService
│   └── knowledge-game-file/           文件服务 Spring Boot（端口 8083）
│       └── com.knowledgegame.file.*   图片上传/下载/删除，存储抽象层，凭证鉴权
└── frontend/
    ├── user/                          用户端前端（React + Vite）
    └── admin/                         管理端前端（Ant Design Pro，端口 8000）
```

**依赖关系：** app、admin → core + components，两者互不依赖。file → core + component-m2m-auth。app、admin → component-feign（Feign Client 接口共享）+ component-m2m-auth（Feign 拦截器注入 M2M 请求头）。Spring Cloud 组件依赖（nacos-discovery、nacos-config、openfeign、loadbalancer）由各服务模块显式引入。

**Bean 自动注册：** core 和各 component 均为 Spring Boot Starter，通过 `AutoConfiguration.imports` 自动配置 Bean，app/admin 无需 `@ComponentScan` 跨包扫描。

## DDD 分层（core 模块，包名 `com.knowledgegame.core.*`）

| 层 | 职责 | 规则 |
|----|------|------|
| domain | 领域实体、枚举、值对象、端口、领域服务、业务规则校验（domain/spec/） | 零框架依赖（使用领域 PageResult 替代 Spring Page） |
| infrastructure | 仓储适配器、PO、JPA、Converter（MapStruct）、adapter 内部辅助工具（adapter/support/） | 实现 domain 端口 |
| common | Result、BusinessException | 通用工具 |
| config | KnowledgeGameCoreAutoConfiguration | @AutoConfiguration，注册领域服务 + 扫描适配器 + JPA |

app / admin 各自包含 api（Controller + DTO + Assembler（MapStruct））、application（AppService）、config。

## 数据模型

### 已实现

| 表 | 说明 | 所属需求 |
|----|------|---------|
| user | 用户表 | REQ-01 |
| ip_series | IP 系列表 | REQ-16 |
| card_template | 卡牌模板表 | REQ-17 |
| card_star_image | 卡牌星级图片表（REQ-92 已废弃删除） | REQ-17 |
| knowledge_category | 知识点分类表（邻接表，树形层级） | REQ-07 |
| question | 题目表（四种题型，JSON 存储 options/answer/tags） | REQ-09 |
| question_category_relation | 题目-分类多对多关联表 | REQ-09 |
| knowledge_item | 知识条目表（标题/Markdown 正文/HTML 渲染/封面图/标签/排序） | REQ-97 |
| knowledge_item_category_relation | 知识条目-分类多对多关联表 | REQ-97 |
| file_info | 文件信息表（图片元数据、metadata JSON 列、basePath 存储路径、软删除） | REQ-83、REQ-87、REQ-93 |
| study_group | 学习群组表（无 FK 约束 + join_policy + invite_code + avatar FileRef 双字段） | REQ-48, REQ-49 |
| group_member | 群组成员表（UNIQUE(group_id, user_id)，role VARCHAR 存枚举名，无 FK 约束） | REQ-48 |
| recycle_bin | 回收站总览索引表（resource_type + original_id 联合唯一，restore_deadline 定时清理索引） | REQ-100 |
| ip_series_deleted | IP 系列删除快照表（镜像 ip_series 字段 + original_id/deleted_by/deleted_at） | REQ-100, REQ-104 |
| card_template_deleted | 卡牌模板删除快照表（镜像 card_template 字段 + ip_series_name 冗余 + original_id 索引） | REQ-100, REQ-105 |
| question_deleted | 题目删除快照表（镜像 question 字段 + related_data JSON 存分类关联） | REQ-100 |
| knowledge_category_deleted | 知识分类删除快照表（镜像 knowledge_category 字段 + related_data JSON） | REQ-100 |
| knowledge_item_deleted | 知识条目删除快照表（镜像 knowledge_item 字段 + related_data JSON 存分类关联） | REQ-100 |
| scheduled_task_log | 定时任务执行日志表（task_name/task_display/执行统计/failure_details JSON） | REQ-101 |

### 已设计（待实现）

> 详细设计见 [card-system-data-model.md](card-system-data-model.md)

| 表 | 说明 | 所属需求 |
|----|------|---------|
| user_card | 用户卡牌收集（群组维度） | REQ-18 |
| pity_counter | 保底计数器（群组+IP 维度） | REQ-19 |
| point_transaction | 积分流水（群组维度） | REQ-15 |

### 图片字段规范（REQ-93）

所有引用图片的表统一使用 `file_id` + `url` 双字段持久化，领域模型使用 `FileRef` 值对象：

| 表 | 图片字段 | file_id 列 | url 列 |
|----|---------|-----------|--------|
| ip_series | coverImage | cover_image_file_id | cover_image_url |
| knowledge_category | icon | icon_file_id | icon_url |
| knowledge_category | coverImage | cover_image_file_id | cover_image_url |
| card_template | image | image_file_id | image_url |
| user | avatar | avatar_file_id | avatar_url |
| knowledge_item | coverImage | cover_image_file_id | cover_image_url |
| study_group | avatar | avatar_file_id | avatar_url |

- 写入链路：前端仅传 fileId → AppService 调 `FileServiceClient.getFileInfo` 校验 metadata（bizType + userId）→ 返回 url 组装 FileRef → 双字段持久化
- 读取链路：GET API 同时返回 `xxxFileId`（Long）和 `xxxUrl`（String）
- 文件服务：metadata JSON 列存储 `{bizType, userId}`，由 admin/app 凭证接口组装

### 更新接口三态语义（REQ-88）

所有 update 接口遵循 JSON Merge Patch (RFC 7396) 三态语义：

- 可清空字段（description / imageFileId / coverImageFileId / iconFileId / color / avatarFileId 等）包装为 `JsonNullable<T>`：
  - 字段缺失 → undefined（不更新）
  - 字段为 null → of(null)（清空）
  - 字段有值 → of(value)（更新）
- 必填字段（name / code / status / rarity / nickname / sortOrder 等）保持原 Java 类型，沿用 null=不更新语义，**杜绝被前端误清空**
- 领域层提供 `updateXxx(value)` + `clearXxx()` 双方法（清空必须显式调用 clearXxx）
- AppService 内联 `applyField` / `applyFileRefField` 三态分派（不抽共享基类，避免 core 依赖 Jackson）
- 前端表单提交时按字段对比构造 payload：未变更字段不发送（undefined 三态），变更字段（含清空）才发送

### 待设计

| 表 | 说明 | 所属需求 |
|----|------|---------|
| group_ip_library | 群组关联 IP 库 | REQ-51 |
| group_knowledge_base | 群组关联知识库 | REQ-51 |
| check_in_strategy | 签到策略（群组配置） | REQ-69 |
| check_in_record | 签到记录 | REQ-68 |
| reward_exchange_record | 实体奖励兑换记录 | REQ-22 |
| shop_product | 商城商品 | REQ-56 |
| order | 订单 | REQ-59 |
| achievement_template | 成就模板（系统级，按 IP 维度） | REQ-77 |
| group_achievement | 群组收藏成就（群组维度，自定义或模板） | REQ-75 |
| group_collection_milestone | 群组收藏里程碑（群组维度，按 IP） | REQ-75 |
| user_achievement_record | 用户成就达成记录 | REQ-76 |
| user_milestone_claim_record | 用户里程碑奖励领取记录 | REQ-76 |

## API 清单

### 用户端 `/api/**`（app 模块，端口 8082）

| 路径 | 方法 | 说明 | 状态 |
|------|------|------|------|
| /api/users/register | POST | 用户注册（BCrypt 加密） | 已实现 |
| /api/users/login | POST | 用户登录（JWT 双令牌） | 已实现 |
| /api/users/refresh-token | POST | 刷新令牌 | 已实现 |
| /api/users/{id} | GET | 查询用户 | 已实现（需 JWT） |
| /api/users | GET | 用户列表 | 已实现（需 JWT） |
| /api/users/{id} | PUT | 更新用户 | 已实现（需 JWT） |
| /api/users/{id} | DELETE | 删除用户 | 已实现（需 JWT） |
| /api/users/logout | POST | 用户登出（黑名单 Token） | 已实现（需 JWT） |
| /api/knowledge-categories/tree | GET | 查询 ACTIVE 分类嵌套树 | 已实现（需 JWT） |
| /api/upload-credential | GET | 获取上传凭证（bizType + count） | 已实现（需 JWT，Feign → file 服务） |
| /api/study-groups | POST | 创建学习群组（原子加 OWNER 成员） | 已实现（需 JWT） |
| /api/study-groups/{id}/members | POST | 直接加入 OPEN 群组 | 已实现（需 JWT，REQ-49） |
| /api/study-groups/join-by-invite | POST | 凭邀请码加入群组 | 已实现（需 JWT，REQ-49） |
| /api/study-groups/{id}/members/me | DELETE | 退出群组 | 已实现（需 JWT，REQ-49） |
| /api/study-groups/{id}/invite-code/regenerate | POST | 重新生成邀请码（仅 OWNER） | 已实现（需 JWT，REQ-49） |
| /api/study-groups/{id}/members/me | GET | 查询当前成员身份 | 已实现（需 JWT，REQ-49） |
| /api/study-groups/{id}/members/{userId} | PUT | 更新成员角色（仅 OWNER，ADMIN/MEMBER 互转） | 已实现（需 JWT，REQ-50） |
| /api/study-groups/{id}/transfer-ownership | POST | 转让群主（仅 OWNER，原 OWNER 变 ADMIN） | 已实现（需 JWT，REQ-50） |

### 管理端 `/api/admin/**`（admin 模块，端口 8081）

| 路径 | 方法 | 说明 | 状态 |
|------|------|------|------|
| /api/admin/login | POST | 管理端登录（仅 ADMIN 角色） | 已实现 |
| /api/admin/refresh-token | POST | 管理端刷新令牌（校验 ADMIN 角色） | 已实现 |
| /api/admin/logout | POST | 管理端登出（黑名单 Token） | 已实现（需 JWT + ADMIN） |
| /api/admin/ip-series | POST | 创建 IP 系列 | 已实现 |
| /api/admin/ip-series | GET | 分页查询（name/code 模糊 + status 筛选 + sort/order 排序） | 已实现 |
| /api/admin/ip-series/{id} | GET | 查询详情 | 已实现 |
| /api/admin/ip-series/{id} | PUT | 更新 | 已实现 |
| /api/admin/ip-series/{id} | DELETE | 移入回收站（快照 ip_series_deleted + 物理删原表 + 总览表登记） | 已实现（REQ-104） |
| /api/admin/card-templates | POST | 创建卡牌模板（含卡面图） | 已实现 |
| /api/admin/card-templates | GET | 分页查询（name/code 模糊 + ipSeriesId/rarity/status 筛选 + sort/order 排序） | 已实现 |
| /api/admin/card-templates/{id} | GET | 查询详情（含卡面图） | 已实现 |
| /api/admin/card-templates/{id} | PUT | 更新基础信息 | 已实现 |
| /api/admin/card-templates/{id} | DELETE | 移入回收站（快照 card_template_deleted + 物理删原表 + 总览表登记） | 已实现（REQ-105） |
| /api/admin/card-templates/batch-activate | PUT | 批量启用（含 IP 系列状态前置校验，上限 100 条） | 已实现 |
| /api/admin/card-templates/batch-deactivate | PUT | 批量停用（上限 100 条） | 已实现 |
| /api/admin/card-templates/import-template | GET | 下载卡牌导入模板（Excel） | 已实现 |
| /api/admin/card-templates/import | POST | 批量导入卡牌模板（Excel multipart，上限 200 行） | 已实现 |
| /api/admin/card-templates/{id}/star-images | POST | 添加/替换单张星级图片（REQ-92 已废弃） | 已废弃 |
| /api/admin/knowledge-categories | POST | 创建知识点分类 | 已实现 |
| /api/admin/knowledge-categories | GET | 分页查询（name 模糊 + status + parentId 筛选 + sort/order 排序，默认 sortOrder ASC + createdAt DESC） | 已实现 |
| /api/admin/knowledge-categories/tree | GET | 查询完整分类树 | 已实现 |
| /api/admin/knowledge-categories/{id} | GET | 查询详情 | 已实现 |
| /api/admin/knowledge-categories/{id} | PUT | 更新分类信息 | 已实现 |
| /api/admin/knowledge-categories/{id}/move | PUT | 移动分类到新父级（自动设置 sortOrder + 同级名称唯一性校验） | 已实现 |
| /api/admin/knowledge-categories/batch-sort | PUT | 批量更新同级节点 sortOrder（最多 50 个，校验同父级） | 已实现 |
| /api/admin/knowledge-categories/{id} | DELETE | 软删除（有 ACTIVE 子分类或关联 ACTIVE 题目时拒绝） | 已实现 |
| /api/admin/upload-credential | GET | 获取上传凭证（bizType + count） | 已实现（需 JWT + ADMIN，Feign → file 服务） |
| /api/admin/questions | POST | 创建题目（选择题/判断题/填空题） | 已实现 |
| /api/admin/questions | GET | 分页查询（keyword/type/difficulty/categoryId/tag/status 筛选 + sort/order 排序） | 已实现 |
| /api/admin/questions/{id} | GET | 查询题目详情 | 已实现 |
| /api/admin/questions/{id} | PUT | 更新题目 | 已实现 |
| /api/admin/questions/{id} | DELETE | 软删除 | 已实现 |
| /api/admin/questions/{id}/categories | GET | 查询题目关联的分类 ID（过滤 INACTIVE） | 已实现 |
| /api/admin/questions/{id}/categories | PUT | 更新分类关联（全量替换，校验分类存在且 ACTIVE） | 已实现 |
| /api/admin/questions/batch-activate | PUT | 批量启用 | 已实现 |
| /api/admin/questions/batch-deactivate | PUT | 批量禁用 | 已实现 |
| /api/admin/questions/import-template | GET | 下载 Excel 导入模板 | 已实现 |
| /api/admin/questions/import | POST | 导入题目（Excel multipart） | 已实现 |
| /api/admin/knowledge-items | POST | 创建知识条目 | 已实现 |
| /api/admin/knowledge-items | GET | 分页查询（keyword/categoryId/tag/status 筛选 + sort/order 排序，默认 sortOrder ASC + createdAt DESC，返回 KnowledgeItemListResponse 不含 content/contentHtml） | 已实现 |
| /api/admin/knowledge-items/{id} | GET | 查询知识条目详情 | 已实现 |
| /api/admin/knowledge-items/{id} | PUT | 更新知识条目 | 已实现 |
| /api/admin/knowledge-items/{id} | DELETE | 软删除（含分类关联校验） | 已实现 |
| /api/admin/knowledge-items/{id}/categories | GET | 查询知识条目关联的分类 | 已实现 |
| /api/admin/knowledge-items/{id}/categories | PUT | 更新分类关联（全量替换） | 已实现 |
| /api/admin/knowledge-items/batch-activate | PUT | 批量启用（含分类状态前置校验） | 已实现 |
| /api/admin/knowledge-items/batch-deactivate | PUT | 批量禁用 | 已实现 |
| /api/admin/knowledge-items/batch-sort | PUT | 批量排序（无父级层级，纯交换 sortOrder） | 已实现 |

### 文件服务 `/api/file/**`（file 模块，端口 8083）

| 路径 | 方法 | 说明 | 状态 |
|------|------|------|------|
| /api/file/upload | POST | 单文件上传（凭证鉴权，basePath 从凭证中提取） | 已实现 |
| /api/file/batch-upload | POST | 批量上传（凭证鉴权，原子性，basePath 从凭证中提取） | 已实现 |
| /api/file/internal/credential | POST | 生成上传凭证（M2M，含 basePath 绑定） | 已实现 |
| /api/file/internal/{fileId} | GET | 查询文件信息（M2M） | 已实现 |
| /api/file/internal/{fileId} | DELETE | 软删除文件（M2M） | 已实现 |
| /api/file/internal/batch-urls | POST | 批量查询文件 URL（M2M） | 已实现 |
| /static/** | GET | 静态资源访问（无鉴权） | 已实现 |

## 管理后台前端页面

| 页面 | 路由 | 说明 | 状态 |
|------|------|------|------|
| 登录 | /login | JWT 认证 + ADMIN 角色校验 | 已实现 |
| 仪表盘 | /dashboard | 占位页面 | 待实现 |
| IP 系列管理 | /content/ip-series | ProTable CRUD + 凭证式封面图上传 + 启停切换 | 已实现 |
| 卡牌管理 | /content/card-template | ProTable CRUD + 凭证式图片上传（单图）+ 启停切换 | 已实现 |
| 分类管理 | /content/category | 左侧分类树 + 右侧详情面板 + 凭证式图片上传（图标/封面图）+ 拖拽排序/移动 + 增删改 | 已实现 |
| 题库管理 | /content/question-bank | ProTable + Drawer 4 题型动态表单 + 批量启停 + Excel 导入/模板下载 + 分类筛选 + 列头排序 | 已实现 |
| 知识条目管理 | /content/knowledge-item | ProTable + Drawer + vditor Markdown 编辑器 + 凭证式封面图上传 + 分类多选 + 上移/下移排序 + 批量启停 | 已实现 |
| 商品管理 | /shop/product | 占位页面 | 待实现 |
| 订单管理 | /shop/order | 占位页面 | 待实现 |
| 盲盒配置 | /config/blind-box | 占位页面 | 待实现 |
| 成就模板 | /config/achievement | 占位页面 | 待实现 |
| 回收站 | /system/recycle-bin | 左侧目录树 + 右侧 ProTable 列表，支持 resourceType 过滤/关键字搜索/排序/多选 | 已实现 |
| 用户管理 | /system/user | 占位页面 | 待实现 |

## 用户端前端页面

| 页面 | 路由 | 说明 | 状态 |
|------|------|------|------|
| 首页 | /home | 占位首页，等待 REQ-29 填充业务内容 | REQ-26 已实现 |
| 404 兜底 | * | 未匹配路由显示 antd Result 404 | REQ-26 已实现 |
| 登录 | /login | 分屏品牌化布局，表单校验/错误提示/redirect跳转/记住我 | REQ-28 已实现 |
| 注册 | /register | 分屏品牌化布局，确认密码/自动登录/redirect跳转/错误兜底 | REQ-28 已实现 |
| 忘记密码 | /forgot-password | 占位页面，功能待后续实现 | REQ-28 已实现 |

### 认证基础组件（REQ-27）

| 组件 | 文件 | 说明 |
|------|------|------|
| api-client | `src/services/api-client.ts` | Axios 实例：baseURL /api，timeout 15s，请求拦截器注入 Bearer token，响应拦截器解包 Result + 401 并发刷新队列 |
| auth-store | `src/store/auth-store.ts` | Zustand + persist（动态 storage），管理 accessToken/refreshToken/user/rememberMe，提供 login/logout/setTokens/setRememberMe |
| auth-api | `src/services/auth-api.ts` | loginApi / registerApi / refreshTokenApi / logoutApi |
| AuthGuard | `src/components/AuthGuard.tsx` | 路由守卫：未登录重定向 /login?redirect=...，白名单路由（/login /register）不包裹 |
| 401 白名单 | `AUTH_WHITELIST` | `/api/users/login` `/api/users/register` `/api/users/refresh-token` |

### 登录/注册页面（REQ-28）

| 组件 | 文件 | 说明 |
|------|------|------|
| AuthLayout | `src/layouts/AuthLayout.tsx` | 分屏品牌化布局（左渐变+Slogan + 右表单区），已登录重定向 /home，CSS 媒体查询移动端隐藏品牌区 |
| Login | `src/pages/Login/index.tsx` | 登录页：用户名+密码表单校验，BAD_CREDENTIALS 精确匹配，网络错误兜底，redirect 防开放重定向，记住我 checkbox |
| Register | `src/pages/Register/index.tsx` | 注册页：username+nickname+password+confirmPassword，自动登录+兜底 Modal，DUPLICATE_USERNAME 字段级红字 |
| ForgotPassword | `src/pages/ForgotPassword/index.tsx` | 占位页（antd Result info），后端无 API，留入口不阻塞用户 |

技术栈：Vite 5 + React 18 + TypeScript 5 + React Router v6.4 + antd 5 + axios 1.7 + zustand 4.5 + dayjs + Vitest + axios-mock-adapter + ESLint 9 flat config + Prettier + Husky。开发端口 5173，API 代理到 localhost:8082。

测试：5 个白盒测试（App/MainLayout/NotFound/AuthGuard）+ 4 个黑盒测试（菜单激活态/根路径重定向/暗色主题/CSS 布局）+ auth-store（11 用例）+ api-client（8 用例）+ AuthLayout（3 用例）+ Login（12 用例）+ Register（8 用例）+ ForgotPassword（2 用例），共 14 文件 65 用例。已知 jsdom 限制：createXxxRouter 导航因 AbortSignal 跨 realm 问题不可用，测试用 MemoryRouter + Routes 或 mock useNavigate 绕过。

## DDD 合规状态

| 检查项 | 状态 |
|--------|------|
| domain 层零框架依赖 | ✅ 使用 PageResult 替代 Spring Page |
| Controller 零领域模型依赖 | ✅ AppService 返回 DTO |
| @Transactional 仅 application 层 | ✅ |
| 领域实体无 ORM 注解 | ✅ |
| 领域实体无 public setter（除行为方法） | ✅ |
| Converter 使用 MapStruct | ✅ |
| Assembler 使用 MapStruct | ✅ |
| 领域服务零框架注解 | ✅ 通过 AutoConfiguration @Bean 注册 |

## 设计决策记录

| 决策 | 选择 | 日期 |
|------|------|------|
| 后端架构 | Maven 多模块：core + components + app + admin + file | 2026-06-07 |
| 卡牌模型 | 二级结构：IP→卡牌模板（单图） | 2026-06-07；REQ-92 简化 |
| 抽卡概率 | 由卡池中各稀有度卡牌数量自然决定 | 2026-06-07 |
| 删除策略 | 软删除（status 字段） | 2026-06-07 |
| 保底存储 | 每用户每群组每 IP 一行，三个计数字段 | 2026-06-08 |
| 对象转换 | MapStruct（Converter + Assembler） | 2026-06-07 |
| 密码加密 | BCrypt（component-auth 模块，spring-security-crypto） | 2026-06-09 |
| 认证方案 | Spring Security + JWT（Access Token 30min + Refresh Token 7d） | 2026-06-09 |
| JWT 位置 | component-auth 模块（JwtTokenProvider + JwtAuthenticationFilter） | 2026-06-09 |
| SecurityConfig | 各模块独立配置（app 需 JWT，admin 需 JWT + hasRole ADMIN） | 2026-06-09 |
| 领域分页 | 自定义 PageResult<T>（domain 层零框架依赖） | 2026-06-07 |
| 注释语言 | 中文注释（全局规则） | 2026-06-07 |
| 核心维度 | 积分/抽卡/卡牌收集/保底全部基于群组维度 | 2026-06-08 |
| 卡牌星级 | user_card.star_level，星级是用户收集维度属性，升级规则待后续需求定义 | 2026-06-08；REQ-92 简化 |
| 实体奖励 | 每个星级对应商城商品，兑换后清零保留解锁 | 2026-06-08 |
| 商城可见性 | 仅系统管理员可管理商城；用户看商品图，群组管理员看价格 | 2026-06-08 |
| 抽卡选择 | 抽到卡后可选保留或换取积分 | 2026-06-08 |
| 图片上传组件 | ImageUploadField 通用凭证式上传组件（bizType 驱动），统一 IpSeries/CardTemplate/KnowledgeBase 三处上传逻辑 | 2026-06-15 |
| 包名规范 | core `com.knowledgegame.core.*`，app `com.knowledgegame.app.*`，admin `com.knowledgegame.admin.*` | 2026-06-09 |
| Bean 注册 | core/component 均为 Spring Boot Starter（AutoConfiguration.imports） | 2026-06-09 |
| Token 黑名单 | 内存实现（InMemoryTokenBlacklist），后期迁移 Redis（REQ-81） | 2026-06-10 |
| 登出策略 | 同时黑名单 Access Token + Refresh Token 的 jti | 2026-06-10 |
| 错误码 | ResultCode 枚举统一管理，禁止魔法字符串 | 2026-06-10 |
| 管理端前端鉴权 | Ant Design Pro 原生模式：getInitialState + onRouteChange + ProLayout logout（REQ-41） | 2026-06-10 |
| Token 刷新策略 | 401 被动刷新 + window.location.reload()，管理后台使用频率低，无需主动续签 | 2026-06-10 |
| 服务注册与发现 | Spring Cloud Alibaba + Nacos（服务注册 + 配置中心） | 2026-06-11 |
| 服务间调用 | OpenFeign 声明式调用，Feign Client 接口由 component-feign 共享 | 2026-06-11 |
| 机机鉴权 | API Key 被调用方单一 key 校验，调用方 Feign 拦截器按目标服务 `services` Map 注入对应 Key，未命中抛错 | 2026-06-11 |
| 日志与链路追踪 | SLF4J/Logback + MDC TraceId，日志脱敏（key=value + JSON 双格式） | 2026-06-11 |
| 知识点分类存储 | 邻接表（parent_id 外键），MySQL 8 CTE 递归查询树形结构 | 2026-06-11 |
| 文件服务架构 | 独立 Maven 模块 knowledge-game-file，存储抽象层（FileStorageProvider），本地磁盘优先 | 2026-06-11 |
| 文件存储路径 | basePath（String）替代 BizType 枚举，file 服务不感知业务类型，由 app/admin 维护 bizType→basePath 映射 | 2026-06-12 |
| Feign Client 组织 | 按服务提供方维度放 component-feign（如 FileServiceClient），app/admin 共用 | 2026-06-12 |
| 文件上传流程 | 前端 → app/admin 获取凭证（含 token + uploadUrl）→ 前端直传 file 服务 → basePath 从凭证中提取 | 2026-06-12 |
| 日志目录 | 三个服务统一通过 logging.file.path 配置，logback-spring.xml 引用 ${LOG_PATH} | 2026-06-12 |
| 题库题型 | 选择题（单选/多选）+ 判断题 + 填空题，统一 question 表，options/answer/tags 用 JSON 存储 | 2026-06-13 |
| 题库分类关联 | 多对多（question_category_relation），题目软删除时关联保留，查询时过滤 INACTIVE 分类 | 2026-06-13 |
| 通用排序 | SortField 值对象（domain 层），各 RepositoryAdapter 维护字段映射，降级为 createdAt DESC | 2026-06-13 |
| 难度存储 | Difficulty 枚举（EASY/MEDIUM/HARD），数据库 VARCHAR 存枚举名，前后端统一用整数 level（1/2/3） | 2026-06-13 |
| 分类管理页布局 | 左侧树形导航 + 右侧详情面板（CategoryTree + CategoryDetail），Ant Design Tree 内置 draggable（非 @dnd-kit） | 2026-06-13 |
| 拖拽交互 | dropToGap=同级排序（batch-sort），dropToGap 跨级 / dropToGap=false=移动（move + 自动 sortOrder） | 2026-06-13 |
| 删除前置校验 | 知识点分类：有 ACTIVE 子分类或关联 ACTIVE 题目时拒绝删除（双重保障，消息含数量） | 2026-06-13；REQ-94 扩展 |
| 停用/启用前置关联校验 | 停用 IP 系列→校验 ACTIVE 卡牌；停用分类→校验 ACTIVE 子分类+题目；启用卡牌→校验 IP 系列 ACTIVE；启用题目→校验全部分类 ACTIVE | 2026-06-15 REQ-94 |
| 批量操作上限 | @Size(max=100)，BatchStatusRequest 全局约束，Question/CardTemplate 批量接口统一收紧 | 2026-06-15 REQ-94 |
| sortOrder 自动计算 | 未传时取同级 max + 1，无同级时为 0；move 到新父级时自动设置 | 2026-06-13 |
| 移动校验 | validateMove：不能移到自身/后代 + 目标 ACTIVE + 目标父级下同名唯一 | 2026-06-13 |
| 知识条目 Markdown 渲染 | 后端渲染（commonmark-java 0.22 + jsoup 1.17 XSS 消毒），存 content_html 列供用户端零渲染依赖使用 | 2026-06-18 REQ-97 |
| 知识条目封面图 | FileRef 双字段持久化（cover_image_file_id + cover_image_url），verifyFileRef 校验 metadata（bizType + userId） | 2026-06-18 REQ-97 |
| 知识条目排序 | 默认 sortOrder ASC + createdAt DESC 复合排序，batchSort 无父子层级（跳过同父级校验） | 2026-06-18 REQ-97 |
| 知识条目-分类双向停用校验 | 创建时校验全部分类 ACTIVE；停用分类时校验无 ACTIVE 条目关联；启用条目时校验全部分类 ACTIVE | 2026-06-18 REQ-97 |
| 知识条目编辑器 | vditor 3.x wysiwyg 模式，动态 import 拆分 chunk（~2MB），ImageUploadField 凭证式封面图上传 | 2026-06-18 REQ-97 |
| 知识条目列表性能优化 | JPA CriteriaQuery&lt;Tuple&gt; 投影查询只 SELECT 9 个非正文列（跳过 MEDIUMTEXT content/contentHtml），KnowledgeItemSummary VO + KnowledgeItemListResponse DTO 分离列表与详情 | 2026-06-21 REQ-114 |
| IP 系列管理页 | ProTable + ModalForm CRUD + 凭证式封面图上传 + 启用/停用切换，默认筛选 ACTIVE | 2026-06-13 |
| 机机鉴权 V2 | REQ-91 V2：调用方 `m2m.auth.services` 多目标服务密钥映射，按 Feign target 选 Key，未命中抛 IllegalStateException，被调用方零改动 | 2026-06-14 |
| 凭证式上传组件 | ImageUploadField：通用 bizType 驱动凭证式上传组件，统一 CoverImageUpload/StarImageUpload 三处调用点，可删除重传 | 2026-06-15 |
| 卡牌管理页 | ProTable + ModalForm + ImageUploadField 单图上传 + 启用/停用切换 + 编码 IP 内唯一 | 2026-06-14 |
| 时间序列化 | Jackson 全局 `write-dates-as-timestamps=true`，`LocalDateTime` 序列化为毫秒时间戳，前端 ProTable `valueType: 'dateTime'` 自动格式化 | 2026-06-14 |
| 题库管理前端 | ProTable 列头排序（项目首个客户端排序页），Drawer 表单 4 题型动态切换，分类树一次性加载 useRef 缓存，批量操作 tableAlertOptionRender，导入 Modal.confirm 确认 | 2026-06-14 |
| 数据库外键 | 禁止数据库层面外键约束，JPA 关联用 `@ForeignKey(ConstraintMode.NO_CONSTRAINT)`，一致性由应用层保证 | 2026-06-14 |
| @OneToMany 更新 | Converter updatePO 中子集合必须原地更新（setXxx/add/removeIf），禁止 clear()+add(new PO)，防止 Hibernate flush 顺序导致唯一约束冲突 | 2026-06-14 |
| 图片字段统一规范 | 所有图片字段使用 FileRef 值对象（fileId + url 双字段），PO 双字段持久化，Converter 显式赋值禁止 NullValuePropertyMappingStrategy.IGNORE 歧义，前端表单仅传 fileId 后端校验 metadata（bizType + userId）后回填 url | 2026-06-17 REQ-93 |
| 图片上传 metadata | 文件服务 FileInfo 新增 metadata JSON 列，上传凭证携带 metadata（bizType + userId），上传完成后写入 FileInfo，业务方通过 getFileInfo 取回校验 | 2026-06-17 REQ-93 |
| ImageUploadField 协议 | value 类型 string→number（fileId），新增 url prop 用于编辑模式回填缩略图，组件内部 internalUrl 管理上传临时显示 | 2026-06-17 REQ-93 |
| 更新接口三态语义 | JSON Merge Patch (RFC 7396)：可清空字段包装为 `JsonNullable<T>`（undefined/null/value 三态），必填字段保持原 Java 类型（null=不更新，杜绝误清空），领域层提供 `updateXxx + clearXxx` 双方法，AppService 内联 applyField/applyFileRefField 三态分派，前端按字段对比构造 payload | 2026-06-18 REQ-88 |
| Jackson 模块注册 | admin/app 各自模块新增 `JacksonConfig`，通过 `Jackson2ObjectMapperBuilderCustomizer` 注册 `JsonNullableModule`；依赖 `org.openapitools:jackson-databind-nullable` 放 admin/app 不放 core（core 禁止依赖 Jackson 框架类型） | 2026-06-18 REQ-88 |
| 列表查询排序规范 | 所有管理端 list API 统一支持 `sort={field}&order=asc\|desc`；新增 3 个核心组件：`SortField.parse`（domain VO 静态工厂）、`SortFieldSpec.validate`（domain/spec/ 白名单校验，非法字段抛 400）、`SortFields.toSpringSort`（infrastructure/adapter/support/ 转 Spring Sort）；Adapter 用 `ALLOWED_SORT_FIELDS` 白名单 + `SortFieldSpec.validate` 替代旧的 `Map.getOrDefault` 静默回退；KnowledgeCategory 默认排序保留双字段（sortOrder ASC + createdAt DESC），其他单字段 createdAt DESC | 2026-06-18 REQ-86 |
| 集成测试跨模块扫描 | admin 模块 `@DataJpaTest` 默认只扫描 `com.knowledgegame.admin` 包，core 模块的 JpaRepository 扫描不到；需加 `@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")` + `@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")` + `@AutoConfigureTestDatabase(replace=NONE)` + `@ActiveProfiles("test")` 走真实 MySQL `knowledge_game_test` 库 | 2026-06-18 REQ-86 |
| 学习群组创建 | 用户端 POST /api/study-groups，创建时原子写入 OWNER 成员记录（同事务），avatar 使用 FileRef 双字段持久化（verifyFileRef 校验 metadata），用户端硬删除（无 status），name 无唯一约束 | 2026-06-19 REQ-48 |
| 群组与成员双独立聚合根 | StudyGroup 和 GroupMember 为独立聚合根，避免成员频繁增减时加载整个群组聚合 | 2026-06-19 REQ-48 |
| 群组头像 FileRef | 遵循 REQ-93 图片规范，avatar_file_id + avatar_url 双字段，前端只传 fileId 后端校验 bizType + userId 后回填 url | 2026-06-19 REQ-48 |
| 新增 ResultCode | FILE_NOT_FOUND(400) / FILE_BIZ_TYPE_MISMATCH(400) / FILE_OWNER_MISMATCH(403)，统一管理文件校验错误码 | 2026-06-19 REQ-48 |
| 群组成员管理 API | 5 个端点：直接加入（仅 OPEN）、凭邀请码加入（任何 joinPolicy）、退出（OWNER 不能退）、重新生成邀请码（仅 OWNER，碰撞重试 1 次 + 失败抛 INVITE_CODE_GENERATION_FAILED）、查询当前身份 | 2026-06-20 REQ-49 |
| 群组双加入策略 | OPEN（任何人可直接加入）+ INVITE_ONLY（凭邀请码加入），join_policy 列 DEFAULT 'OPEN'，创建时可指定 | 2026-06-20 REQ-49 |
| 邀请码格式与安全 | Crockford Base32 8 位（排除 I/L/O/U，32 字符集），SecureRandom 生成，全局唯一约束，OWNER 可重新生成使旧码失效，格式错和未匹配统一返回 INVITE_CODE_INVALID 防侧信道枚举 | 2026-06-20 REQ-49 |
| OWNER 不可退出 | OWNER 调用退出返回 OWNER_CANNOT_LEAVE（需先转让群组），ADMIN/MEMBER 可正常退出 | 2026-06-20 REQ-49 |
| 群组成员的并发加入防护 | DB 层 UNIQUE(group_id, user_id) 唯一约束 + AppService 层 catch DataIntegrityViolationException 转 ALREADY_GROUP_MEMBER | 2026-06-20 REQ-49 |
| invite_code 碰撞重试 | regenerateInviteCode 时 save 抛 DIVI → 重生成 + 重试一次 → 再抛 → INVITE_CODE_GENERATION_FAILED（400 可重试） | 2026-06-20 REQ-49 |
| 退出为硬删除 | DELETE /members/me 物理删除 group_member 记录，不进回收站（成员关系非资源） | 2026-06-20 REQ-49 |
| 群组角色管理 | 统一 PUT /{id}/members/{userId} 端点传 {"role":"ADMIN"\|"MEMBER"} 实现升/降管理员；转让独立 POST /{id}/transfer-ownership 端点；仅 OWNER 可操作，转让后原 OWNER 自动变为 ADMIN；OWNER 角色变更必须走转让，不能通过角色更新接口 | 2026-06-20 REQ-50 |
| GroupMember 新增行为方法 | promoteToAdmin() / demoteToMember()（OWNER 调用抛 IllegalStateException，幂等）、transferOwnershipTo(GroupMember)（非 OWNER/跨群组抛异常） | 2026-06-20 REQ-50 |
| StudyGroup 转让同步 | transferOwnership 同步更新 StudyGroup.updateOwner(Long) 使 ownerId 反映新群主 | 2026-06-20 REQ-50 |
| 新增 ResultCode | CANNOT_CHANGE_OWNER_ROLE(400) —"不能通过此接口修改群主角色，请使用转让功能" | 2026-06-20 REQ-50 |
| 新增 ResultCode（8 个） | GROUP_NOT_FOUND / GROUP_JOIN_POLICY_MISMATCH / ALREADY_GROUP_MEMBER / INVITE_CODE_INVALID / NOT_GROUP_MEMBER / OWNER_CANNOT_LEAVE / NOT_GROUP_OWNER / INVITE_CODE_GENERATION_FAILED | 2026-06-20 REQ-49 |
| 回收站系统 | 主设计：[REQ-100](prd/req-100-recycle-bin.md)。DELETE 与 INACTIVE 解耦（前者物理删入回收站，后者停用）；1 张总览表 + 5 张 _deleted 详情表；策略模式（RecycleBinItemStrategy<T> + RecycleBinItemStrategyRegistry 自动发现）；恢复后强制 INACTIVE；强校验拒绝；永久删除三步物理清除。已对接资源：IP 系列（REQ-104）、卡牌模板（REQ-105）；未对接：题库/分类/知识条目（REQ-106~108）。定时清理 REQ-101 已完成 | 2026-06-19 REQ-100; 更新 2026-06-23 REQ-105 |
| 回收站 ResourceType 枚举 | IP_SERIES/CARD_TEMPLATE/QUESTION/KNOWLEDGE_CATEGORY/KNOWLEDGE_ITEM，含 toBizTypes() 映射（用于永久删除时文件清理）和 displayName() 中文 | 2026-06-19 REQ-100 |
| 新增 ResultCode | PARAM_ERROR(400) / NOT_IMPLEMENTED(501)，参数校验失败和留后端点专用 | 2026-06-19 REQ-100 |
| 回收站 REST API | GET /api/admin/recycle-bin（列表分页/过滤/排序）、GET /api/admin/recycle-bin/supported-types（已接入资源类型）、POST /api/admin/recycle-bin/{id}/restore（单条恢复）、POST /api/admin/recycle-bin/batch-restore（批量恢复）、DELETE /api/admin/recycle-bin/{id}（单条永久删除）、POST /api/admin/recycle-bin/batch-purge（批量永久删除）。所有批量操作均使用方案 B（逐条独立事务），HTTP 总是 200 成败由响应体描述 | 2026-06-19 REQ-100; 更新 2026-06-20 REQ-102/103 |
| 回收站详情表 | ip_series_deleted / card_template_deleted / question_deleted / knowledge_category_deleted / knowledge_item_deleted，镜像原表字段 + originalId/relatedData(JSON)/deletedBy/deletedAt | 2026-06-19 REQ-100 |
| IP 系列回收站策略 | IpSeriesRecycleBinStrategy（validateDeletable 委托 IpSeriesDomainService → moveToRecycleBin 快照+物理删 → restore 校验 code/name 唯一性+强制 INACTIVE → purge 清理封面图）；admin RecycleBinConfig @Bean 注册 | 2026-06-21 REQ-104 |
| 卡牌模板回收站策略 | CardTemplateRecycleBinStrategy（validateDeletable 查存在性 → moveToRecycleBin 快照+物理删+留存 ipSeriesName → restore 校验 IP 系列仍存在+同 IP 下 code 唯一性+强制 INACTIVE → purge 清理卡面图）；恢复时 ipSeriesName 来自快照冗余，错误消息展示 IP 名称而非仅 ID | 2026-06-22 REQ-105 |
| 策略 Bean 注册方式 | admin 模块 RecycleBinConfig 显式 @Bean 注册，非 @Component（策略需 FileCleanupPort，仅 admin 有 FileCleanupAdapter 实现） | 2026-06-21 REQ-104; 2026-06-22 REQ-105 |
| AppService 注入策略类型 | 声明为 RecycleBinItemStrategy<T> 领域接口类型，不声明为具体 infrastructure 实现类，遵守 DDD 分层依赖规则 | 2026-06-23 REQ-105 |
| 回收站恢复框架 | AppService 构造器自注入 `self`（@Lazy），restore(Long) 无事务委托 → self.restoreInNewTransaction(RecycleBinItem)（@Transactional REQUIRES_NEW 独立新事务）；批量恢复方案 B：阶段 1 findAllById 预校验 + 阶段 2 逐条独立事务 try-catch（BusinessException 泄露消息 / RuntimeException 兜底）；HTTP 总是 200 由 successIds/failures 描述；恢复后强制 INACTIVE 契约由 Strategy 实现者遵守 | 2026-06-20 REQ-103 |
| 回收站永久删除框架 | purge(Long) 无事务委托 → self.purgeInNewTransaction(RecycleBinItem)（@Transactional REQUIRES_NEW）；批量永久删除方案 B 同恢复模式（去重→预校验→逐条独立事务 try-catch）；purgeInNewTransaction 通过 strategyRegistry.get().purge() 委托 Strategy 执行三步物理清除；doBatchOperation 泛型辅助方法消除 batchRestore/batchPurge 70 行重复代码 | 2026-06-20 REQ-102 |
| 前端 HTTP 客户端 | Axios 实例封装（baseURL /api，timeout 15s），请求拦截器注入 Bearer token，响应拦截器解包 Result.data + 401 并发刷新队列 | 2026-06-19 REQ-27 |
| 前端认证状态管理 | Zustand + persist 中间件（localStorage key: auth-storage），accessToken/refreshToken/expiresIn/user | 2026-06-19 REQ-27 |
| 前端路由守卫 | 全量拦截 + 白名单（/login /register），AuthGuard 包裹需认证路由，未登录重定向 /login?redirect=... | 2026-06-19 REQ-27 |
| 前端测试策略 | Vitest + axios-mock-adapter（axios 级别 mock，拦截器链完整执行）+ jsdom + @testing-library/react；jsdom AbortSignal 跨 realm 问题用 MemoryRouter + Routes 替代 createMemoryRouter | 2026-06-19 REQ-27 |
