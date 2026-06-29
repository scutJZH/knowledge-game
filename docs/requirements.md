# Requirements — Knowledge Game

> 状态流转：`idea` → `confirmed` → `designed` → `ready-for-dev` → `in-progress` → `testing` → `done`
> PRD 规则：开发哪个需求才记录哪个 PRD，其余需求在设计阶段完成后创建

## 优先级总览

```
P0 核心闭环  ████████████  回收站 + 群组系统 → 游戏核心 → 卡牌系统 → 商城 → 知识库浏览
P1 用户端    ████████      前端项目搭建 + 游戏界面 + 群组/卡牌/签到前端
P2 管理端    ██████        仪表盘 + 盲盒配置 + 用户管理 + 商城/成就管理
P3 基础设施  ████          Redis Token + 批量导入 + 编辑器增强
```

| 优先级 | 定义 | 需求数 | 依赖关系 |
|--------|------|--------|---------|
| **P0** | 核心产品闭环 — 没有它产品跑不起来 | 46 | 回收站先做（后续新资源 DELETE 走回收站，避免返工）；群组是积分/抽卡/收集的维度基础 |
| **P1** | 用户端体验 — 用户能看到的界面和交互 | 27 | 依赖 P0 后端 API 完成；前端页面可与后端并行开发 |
| **P2** | 管理端完善 — 运营工具和系统管理 | 9 | 部分依赖 P0（商城管理依赖商城后端）；独立管理端页面可并行 |
| **P3** | 基础设施增强 — Redis/批量导入/编辑器 | 5 | 批量导入依赖对应模块已完成 |

### 前置需求标注说明

每个需求表格最右列标注该需求的硬性前置需求（必须在其之前完成）。标注规则：
- `无` — 无硬性前置，可作为起点
- `REQ-XX` — 单项前置
- `REQ-XX, REQ-YY` — 多项前置（全部需要先完成）
- `REQ-XX ✅` — 前置已完成，可立即开发
- 括号内为前置原因（如"群组维度"、"共用 JPA"等）

### P0 依赖链

```
回收站基础设施 (REQ-100~103)
  ↓
5 个资源对接回收站 (REQ-104~108) ← 覆盖 IP系列/卡牌/题库/分类/知识条目 ✅
  ↓
群组系统 (REQ-48~54, 68~69)     ← 群组 DELETE 直接走回收站
  ↓
  ├─→ 游戏核心 (REQ-10~14)
  └─→ 卡牌系统 (REQ-15, 18~25, 55, 70, 75, 76)
        ↓
      商城 (REQ-57~59)
        ↓
      知识库浏览 (REQ-98) ← 依赖 REQ-97 ✅
        ↓
      学习记录 (REQ-99)
```

### 跨 Phase 依赖总览

```
Phase 1 (认证+基础) ──→ Phase 3 (群组) ──→ Phase 2 (游戏核心)
                    │                  │
                    │                  ├──→ Phase 4 (卡牌) ──→ Phase 5 (商城)
                    │                  │
                    ├──→ Phase 7 (管理后台 CRUD)
                    │                  │
                    └──→ Phase 6 (前端) ←── 依赖所有对应后端 API

Phase 8 (回收站) ←── 依赖 Phase 1/2/7 中各资源的管理端 CRUD
```

---

## 已完成需求（56 项）

### Phase 1：后端基础 + 认证

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-01 | 后端项目骨架（Spring Boot + DDD 目录结构） | done | [req-01-backend-skeleton.md](../docs/prd/req-01-backend-skeleton.md) | 已重构为 backend/frontend 分组 + Maven 三模块 | 无 |
| REQ-03 | 通用返回体 Result + 异常处理 | done | [req-03-global-response.md](../docs/prd/req-03-global-response.md) | GlobalExceptionHandler 在各端模块中已存在 | REQ-01 ✅ |
| REQ-04 | 用户注册 API | done | [req-04-user-register.md](../docs/prd/req-04-user-register.md) | component-auth 模块 + BCrypt 加密 + RegisterCommand 隔离 | REQ-01 ✅, REQ-03 ✅ |
| REQ-05 | 用户登录 API（JWT 签发） | done | [req-05-user-login.md](../docs/prd/req-05-user-login.md) | Spring Security + Access/Refresh Token 双令牌 | REQ-01 ✅, REQ-03 ✅, REQ-04 ✅ |
| REQ-06 | JWT 鉴权拦截 + 角色权限 | done | [req-06-jwt-auth.md](../docs/prd/req-06-jwt-auth.md) | 管理端 JWT 鉴权 + 内存 Token 黑名单 + 登出 + SecurityUtils | REQ-01 ✅, REQ-05 ✅ |
| REQ-74 | Core 模块改造为 Spring Boot Starter + 包名整改 | done | [req-74-core-spring-starter.md](../docs/prd/req-74-core-spring-starter.md) | core/app 包名加模块后缀 + Starter 自动配置 + component-auth Starter | REQ-01 ✅, REQ-03 ✅ |
| REQ-82 | 后端统一日志 + TraceId 链路追踪 | done | [req-82-logging-traceid.md](../docs/prd/req-82-logging-traceid.md) | SLF4J/Logback 日志配置 + MDC TraceId Filter | REQ-01 ✅ |
| REQ-83 | 文件服务模块（图片上传/下载/删除） | done | [req-83-file-service.md](../docs/prd/req-83-file-service.md) | 独立 Maven 模块 knowledge-game-file，端口 8083 | REQ-01 ✅, REQ-84 ✅ |
| REQ-84 | Nacos 服务注册与发现 + 配置中心 + OpenFeign | done | [req-84-nacos-service-discovery.md](../docs/prd/req-84-nacos-service-discovery.md) | Spring Boot 3.5.14 + SCA 2025.0.0.0 + Nacos 注册发现 + 配置中心 + OpenFeign 骨架 | REQ-01 ✅ |
| REQ-85 | 机机鉴权组件 | done | [req-85-m2m-auth.md](../docs/prd/req-85-m2m-auth.md) | API Key 固定密钥，独立 component-m2m-auth 模块，Feign 拦截器 + 服务端校验 Filter | REQ-84 ✅ |

### Phase 2：题库 + 游戏核心

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-07 | 知识点分类 — 管理端 CRUD API | done | [req-07-knowledge-category-admin.md](../docs/prd/req-07-knowledge-category-admin.md) | | REQ-01 ✅, REQ-03 ✅ |
| REQ-08 | 知识点分类 — 用户端查询 API | done | [req-08-knowledge-category-user.md](prd/req-08-knowledge-category-user.md) | `GET /api/knowledge-categories/tree` 返回 ACTIVE 分类嵌套树 | REQ-07 ✅ |
| REQ-09 | 题库 — 管理端 CRUD API（三种题型） | done | [req-09-question-bank-admin.md](../docs/prd/req-09-question-bank-admin.md) | | REQ-01 ✅, REQ-03 ✅, REQ-07 ✅ |

### Phase 3：群组系统（用户端）

> 群组是积分、抽卡、卡牌收集、保底的核心维度。

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-48 | 群组 — 创建群组 API | done | [req-48-create-study-group.md](prd/req-48-create-study-group.md) | 用户端硬删除（无 status）+ 原子加 OWNER 成员 + 双独立聚合根（StudyGroup + GroupMember） | REQ-01 ✅, REQ-06 ✅ |
| REQ-49 | 群组 — 成员管理 API（加入/退出/邀请） | done | [req-49-group-member-mgmt.md](prd/req-49-group-member-mgmt.md) | OPEN+INVITE_ONLY 双策略 + 8 位 Crockford Base32 邀请码 + OWNER 不能退 + 8 个新 ResultCode | REQ-48 ✅ |
| REQ-50 | 群组 — 管理员设置与转让 API | done | [req-50-admin-and-transfer.md](prd/req-50-admin-and-transfer.md) | 提升/降级 ADMIN + 转让 OWNER（含 StudyGroup.ownerId 同步）。踢人划入 REQ-63 | REQ-48 ✅ |
| REQ-51 | 群组 — 关联 IP 库 API | done | [req-51-group-ip-library.md](prd/req-51-group-ip-library.md) | 群组管理员将系统 IP 库添加到群组。知识库全局共享，不需要群组授权 | REQ-48 ✅, REQ-16 ✅ |

### Phase 6：前端游戏界面（用户端）

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-26 | 前端项目搭建（React + Vite + 路由 + 布局） | done | docs/prd/req-26-user-scaffold.md | | 无 |
| REQ-27 | 前端 Axios 封装 + 认证状态管理 | done | docs/prd/req-27-axios-auth.md | | REQ-26 ✅ |
| REQ-28 | 登录/注册页面 | done | docs/prd/req-28-login-register-pages.md | 分屏品牌化登录/注册页面 + 记住我 storage 切换 + AuthLayout | REQ-26 ✅, REQ-27 ✅, REQ-04 ✅, REQ-05 ✅ |
| REQ-60 | 群组列表 + 创建/加入页面 | done | [req-60-group-list-create-join.md](prd/req-60-group-list-create-join.md) | 含 REQ-49 手工测试用例 | REQ-26 ✅, REQ-48 ✅, REQ-49 ✅ |
| REQ-61 | 群组详情 + 管理页面 | done | [req-61-group-detail-management.md](prd/req-61-group-detail-management.md) | 含 REQ-49 重新生成邀请码 + REQ-50 管理员设置与转让手工验收 | REQ-26 ✅, REQ-48 ✅ |

### Phase 7：系统管理后台（管理端）

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-16 | IP 系列 — 管理端 CRUD API | done | [req-16-ip-series-admin.md](../docs/prd/req-16-ip-series-admin.md) | | REQ-01 ✅, REQ-03 ✅ |
| REQ-17 | 卡牌 — 管理端 CRUD API | done | [req-17-card-template-admin.md](../docs/prd/req-17-card-template-admin.md) | | REQ-01 ✅, REQ-03 ✅, REQ-16 ✅ |
| REQ-40 | 管理后台脚手架（Ant Design Pro） | done | [req-40-admin-scaffold.md](../docs/prd/req-40-admin-scaffold.md) | 手动搭建（非 CLI），5 次 commit | 无 |
| REQ-41 | 管理后台 — 登录 + 鉴权 | done | [req-41-admin-auth.md](../docs/prd/req-41-admin-auth.md) | 前端登录+token管理+路由守卫+401刷新 | REQ-40 ✅, REQ-05 ✅ |
| REQ-43 | 管理后台 — 题库管理页 | done | [req-43-question-bank-admin-page.md](../docs/prd/req-43-question-bank-admin-page.md) | Drawer 表单 + 批量导入 + 分类嵌入 + 批量启用/禁用 | REQ-40 ✅, REQ-41 ✅, REQ-09 ✅ |
| REQ-44 | 管理后台 — IP 系列管理页 | done | [req-44-ip-series-admin-page.md](../docs/prd/req-44-ip-series-admin-page.md) | 封面图用凭证式上传（依赖 REQ-87） | REQ-40 ✅, REQ-41 ✅, REQ-16 ✅ |
| REQ-45 | 管理后台 — 卡牌管理页（含图片上传） | done | [req-45-card-template-admin-page.md](../docs/prd/req-45-card-template-admin-page.md) | 星级图片用凭证式上传（依赖 REQ-87） | REQ-40 ✅, REQ-41 ✅, REQ-17 ✅ |
| REQ-65 | 管理后台 — 知识库管理页 | done | [req-65-knowledge-base-admin-page.md](../docs/prd/req-65-knowledge-base-admin-page.md) | REQ-96 改名为分类管理页（路由 /content/category） | REQ-40 ✅, REQ-41 ✅, REQ-07 ✅ |
| REQ-97 | 知识条目 — 管理端 CRUD API + 管理页 | done | - | 新增 `knowledge_item` 表 + 多对多关联表。后端 CRUD + 管理端 ProTable + Drawer + Markdown 编辑器 + 凭证式封面图上传 + 分类多选 | REQ-01 ✅, REQ-03 ✅, REQ-96 ✅ |

### Phase 8：回收站与数据管理增强

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-100 | 通用回收站系统 — 架构设计与前端页面 | done | [req-100-recycle-bin.md](prd/req-100-recycle-bin.md) | DELETE 与 INACTIVE 解耦；1 总览表 + 5 _deleted 详情表 + 策略模式 + 管理端列表页 | 无 |
| REQ-101 | 回收站定时清理任务 | done | [req-101-scheduled-cleanup.md](prd/req-101-scheduled-cleanup.md) | Spring `@Scheduled` 定时任务 + 通用 `scheduled_task_log` 表 + 管理端查询页 | REQ-100 ✅ |
| REQ-102 | 回收站手动永久删除 | done | [req-102-recycle-bin-purge.md](prd/req-102-recycle-bin-purge.md) | 单条永久删除 + 批量永久删除；逐条独立事务 + BatchPurgeResult 部分成功响应 | REQ-100 ✅, REQ-103 ✅ |
| REQ-103 | 回收站通用恢复框架 | done | [req-103-recycle-bin-restore.md](prd/req-103-recycle-bin-restore.md) | 单条恢复 + 批量恢复；AppService self 注入 + @Transactional(REQUIRES_NEW) 逐条独立事务 | REQ-100 ✅ |
| REQ-104 | IP 系列对接回收站 | done | [PRD](prd/req-104-ip-series-recycle-bin.md) | DELETE 改为移入回收站；实现 `IpSeriesRecycleBinStrategy` Bean | REQ-100 ✅, REQ-103 ✅, REQ-102 ✅, REQ-16 ✅ |
| REQ-105 | 卡牌管理对接回收站 | done | [PRD](prd/req-105-card-template-recycle-bin.md) | DELETE 改为移入回收站；实现 `CardTemplateRecycleBinStrategy` Bean；恢复时校验 IP 系列仍存在 | REQ-100 ✅, REQ-103 ✅, REQ-102 ✅, REQ-17 ✅ |
| REQ-106 | 题库管理对接回收站 | done | [PRD](prd/req-106-question-recycle-bin.md) | DELETE 改为移入回收站；实现 `QuestionRecycleBinStrategy` Bean，恢复时校验关联分类仍存在 | REQ-100 ✅, REQ-103 ✅, REQ-102 ✅, REQ-09 ✅ |
| REQ-107 | 分类管理对接回收站 | done | [PRD](prd/req-107-knowledge-category-recycle-bin.md) | DELETE 改为递归移入回收站；实现 `KnowledgeCategoryRecycleBinStrategy` Bean；删除前校验子树全部节点无关联题目/条目 | REQ-100 ✅, REQ-103 ✅, REQ-102 ✅, REQ-07 ✅ |
| REQ-109 | 知识条目管理 — 排序增强 | done | [req-109-knowledge-item-sort.md](prd/req-109-knowledge-item-sort.md) | Adapter 标准化 + categoryName 子查询排序 + 前端 7 列 sorter | REQ-97 ✅ |
| REQ-110 | 卡牌管理 — 批量导入 | done | [req-110-card-template-import.md](prd/req-110-card-template-import.md) | Excel 批量导入卡牌模板（参考题库 REQ-43 导入模式） | REQ-17 ✅ |
| REQ-111 | 知识条目管理 — 批量导入 | done | [req-111-knowledge-item-import.md](prd/req-111-knowledge-item-import.md) | Excel + Markdown zip 双模式批量导入。校验：分类存在且ACTIVE + 域层长度/数量限制 | REQ-97 ✅ |
| REQ-112 | 知识条目管理 — Markdown 源码编辑 + 在线预览 | done | - | Input.TextArea 纯文本编辑 + vditor 静态 preview() 预览 Modal | REQ-97 ✅ |

### 后期优化 / 跨领域增强

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-86 | 管理端分页查询统一排序支持（含 REQ-95 合并） | done | [req-86-admin-list-sort.md](prd/req-86-admin-list-sort.md) | Question/IpSeries/CardTemplate/KnowledgeCategory 四个分页接口支持 sort/order。220+ 测试全绿 | REQ-09 ✅, REQ-16 ✅, REQ-17 ✅, REQ-07 ✅ |
| REQ-87 | Admin/App 对接文件服务（获取上传凭证） | done | [req-87-file-service-integration.md](../docs/prd/req-87-file-service-integration.md) | 含 REQ-83 迭代（BizType→basePath）。App: /api/upload-credit, Admin: /api/admin/upload-credit | REQ-83 ✅, REQ-84 ✅ |
| REQ-88 | 管理端更新接口支持清空可选字段 | done | [req-88-clear-optional-fields.md](prd/req-88-clear-optional-fields.md) | JSON Merge Patch（RFC 7396）三态语义，4 个聚合根 update 接口可清空字段统一改造 | REQ-07 ✅, REQ-16 ✅, REQ-17 ✅ |
| REQ-89 | 知识点分类状态管理（停用/启用切换） | done | - | update 接口增加 status 参数，双向校验（停用→validateDelete，启用→validateActivate），前端启用/停用切换按钮 | REQ-07 ✅ |
| REQ-90 | 知识库管理页 — 图片字段对接文件上传 + ImageUploadField 通用组件 | done | [req-90-knowledge-base-image-upload.md](../docs/prd/req-90-knowledge-base-image-upload.md) | 抽象 ImageUploadField 通用组件，迁移 IpSeries/CardTemplate 现有上传逻辑 | REQ-65 ✅, REQ-87 ✅ |
| REQ-91 | 机机鉴权组件优化 | done | [req-91-multi-target-m2m-auth.md](../docs/prd/req-91-multi-target-m2m-auth.md) | V2：调用方 `m2m.auth.services` 多目标服务密钥映射，Feign 拦截器按 target 选 Key | REQ-85 ✅ |
| REQ-92 | 卡牌模板图片模型简化 — 单图替代多星级图 | done | [req-92-card-template-single-image.md](../docs/prd/req-92-card-template-single-image.md) | 去除 card_star_image 表，每卡牌仅保留 1 张图片。影响 REQ-17/REQ-45 | REQ-17 ✅ |
| REQ-93 | 图片字段统一以 file_id 为关联 key，image_url 降级为冗余查询字段 | done | [req-93-image-file-id-association.md](prd/req-93-image-file-id-association.md) | 4 聚合根 FileRef 迁移 + 前端联动。260 tests 全绿 | REQ-83 ✅, REQ-87 ✅ |
| REQ-94 | 聚合根停用/启用前的关联校验 | done | [req-94-deactivation-association-check.md](../docs/prd/req-94-deactivation-association-check.md) | core Repository 出端口 + 领域服务 + Adapter。285 tests pass | REQ-07 ✅, REQ-16 ✅, REQ-17 ✅ |
| REQ-96 | 分类管理页改名（原"知识库管理"） | done | [req-96-category-rename.md](prd/req-96-category-rename.md) | 仅前端展示层改名（菜单标签/路由/组件目录），后端不动 | REQ-65 ✅ |
| REQ-113 | 后端日志路径统一 | done | - | 3 个模块 `logging.file.path` 从 `logs` 改为 `../logs`，统一输出到 `backend/logs/` | REQ-82 ✅ |
| REQ-114 | 知识条目列表性能优化 — 列表与详情分离 | done | [req-114-knowledge-item-list-optimization.md](prd/req-114-knowledge-item-list-optimization.md) | DTO + JPA 投影双裁剪，Tuple 查询只 SELECT 9 个非正文列。前端 TS 类型对齐新 ListResponse | REQ-97 ✅ |

---

## 未完成需求（58 项）

### Phase 1：后端基础 + 认证

> 优先级：P3

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-02 | 数据库建表 + 初始配置数据 | idea | - | 表数量需根据群组/商城设计重新评估 | 所有核心表设计完成（群组/卡牌/商城等） |

### Phase 2：题库 + 游戏核心

> 优先级：P0 — 游戏核心玩法，需群组系统先就位（积分/连击均为群组维度）

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-10 | 秒判游戏 — 开始对局 + 答题流程 | in-progress | [req-10-blink-judge-game.md](prd/req-10-blink-judge-game.md) | 双表双聚合根（GameSession + GameAnswer），3 API：开始/答题/放弃；客户端+服务端双计时（容差 500ms）；仅 TRUE_FALSE+SINGLE_CHOICE；不计分不连击不触发 Boss（留 REQ-11/12）。检视意见修复 9 条已沉淀到 stage-1.md 5 处规则。计划已拆 5 Issue（plans/req-10-blink-judge-game/task_plan.md） | REQ-09 ✅, REQ-48 ✅ |
| REQ-11 | 秒判游戏 — 积分计算 + 连击逻辑 | idea | - | 积分为群组维度 | REQ-10, REQ-48 ✅ |
| REQ-12 | Boss 关卡 — 关键词补全 API | idea | - | | REQ-09 ✅, REQ-10 |
| REQ-13 | 知识链串联 — 概念关系 API | idea | - | | REQ-09 ✅, REQ-10 |
| REQ-14 | 游戏结算 — 记录对局 + 查询历史 API | idea | - | | REQ-10, REQ-48 ✅ |

### Phase 3：群组系统（用户端）

> 群组是积分、抽卡、卡牌收集、保底的核心维度，需在卡牌系统之前实现。
> 优先级：**P0**
> ⚠️ 本 Phase 所有需求均为**用户端**（群主/管理员在用户 App 内管理自己的群组），与系统管理端（Phase 7）无关。

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-52 | 群组 — 积分管理 API | idea | - | 查看群组积分、管理员修改成员积分 | REQ-48 ✅, REQ-15 |
| REQ-53 | 群组 — 全局统计查询 API | idea | - | 跨群组的卡片收集汇总统计 | REQ-48 ✅ |
| REQ-54 | 群组管理端 — 基础框架 | idea | - | 群组创建人/管理员进入群组管理端（用户 App 内的群组后台，非系统管理后台） | REQ-48 ✅ |
| REQ-68 | 群组 — 每日学习签到 API | idea | - | 群组维度签到，连续签到积分奖励，策略由群组管理员配置 | REQ-48 ✅, REQ-52 |
| REQ-69 | 群组 — 签到策略配置 API | idea | - | 群组管理员配置签到规则和积分奖励阶梯 | REQ-68 |

### Phase 4：卡牌系统

> 整体数据模型设计：[card-system-data-model.md](card-system-data-model.md)
> 所有积分/抽卡/卡牌收集/保底均基于群组维度
> 优先级：**P0** — 核心抽卡+收集+兑换循环

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-15 | 积分流水记录（point_transaction CRUD） | in-progress | [req-15-point-transaction.md](prd/req-15-point-transaction.md) | 写入抽象（PointTransaction 实体 + Service + @Version 乐观锁）+ 双视角查询接口（群组 + 个人跨群组）+ 轻量余额读。流水只追加，管理员调整归 REQ-52 | REQ-48 ✅ |
| REQ-18 | 卡牌 — 用户端查询 API（系列/收集状态） | idea | - | 基于群组维度 | REQ-17 ✅, REQ-48 ✅ |
| REQ-19 | 盲盒抽取 — 单抽 API（概率 + 保底） | idea | - | 群组维度，增加保留/换积分选择 | REQ-17 ✅, REQ-48 ✅, REQ-15 |
| REQ-20 | 盲盒抽取 — 十连抽 API | idea | - | 群组维度，增加保留/换积分选择 | REQ-19 |
| REQ-21 | 直购卡牌 API | idea | - | 直购后也需做保留/换积分选择 | REQ-17 ✅, REQ-48 ✅ |
| REQ-22 | 卡牌升星与实体奖励兑换 API | idea | - | 星级无上限（由模板星级图片决定），最高星级时只能兑换，兑换后清零保留解锁 | REQ-17 ✅, REQ-48 ✅ |
| REQ-23 | 卡牌分解 API | idea | - | 用户可随时分解已有卡牌换积分 | REQ-17 ✅, REQ-48 ✅ |
| REQ-24 | 翻牌奖励 — 生成奖池 + 翻牌 API | idea | - | 群组维度，翻牌后也需保留/换积分/放入卡包选择 | REQ-17 ✅, REQ-48 ✅, REQ-15 |
| REQ-25 | 卡牌图鉴 + 保底进度查询 API | idea | - | 群组维度 + 全局统计视图 | REQ-17 ✅, REQ-48 ✅, REQ-18 |
| REQ-55 | 卡牌展示图片编辑 API | idea | - | 用户可选择 ≤ 当前星级的图片进行展示 | REQ-17 ✅, REQ-92 ✅ |
| REQ-70 | 卡包 API | idea | - | 群组维度卡包，暂存待处理卡片，支持取出后换取积分/升星/兑换 | REQ-17 ✅, REQ-48 ✅ |
| REQ-75 | 收藏里程碑与成就 — 群组管理端配置 API | idea | - | 群组管理员为群组内 IP 设置里程碑阶梯和收藏成就（自定义或从模板选取），配置奖励商品 | REQ-48 ✅, REQ-17 ✅, REQ-77 |
| REQ-76 | 收藏里程碑与成就 — 用户端查询与自动检测 API | idea | - | 查询群组内收藏进度、成就完成状态，自动检测达成条件并发放奖励 | REQ-75 |

### Phase 5：商城及订单系统

> 优先级：**P0** — 实体奖励兑换是卡牌系统的收口

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-57 | 卡片关联商品展示 API | idea | - | 卡片详情中展示商品图，群组管理员看价格，成员不看价格 | REQ-17 ✅, REQ-56 |
| REQ-58 | 实体奖励兑换 — 通知与下单 API | idea | - | 兑换通知群组管理员，管理员下单 | REQ-57, REQ-22, REQ-48 ✅ |
| REQ-59 | 订单状态管理 API | idea | - | 待付款→已付款→已发货→已完成，群组管理员查看送货状态 | REQ-58 |

### Phase 6：前端游戏界面（用户端）

> 优先级：**P1** — 用户端界面，依赖 P0 后端 API；部分页面可与后端并行开发
> ⚠️ 本 Phase 所有需求均为**用户端前端**（frontend/user/），与系统管理端前端（Phase 7，frontend/admin/）无关。

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-29 | 首页 — 群组选择 + 知识点分类 | idea | - | 分类树接口与前端联调验证在此需求进行 | REQ-26 ✅, REQ-48 ✅, REQ-08 ✅ |
| REQ-30 | 秒判游戏页面（滑卡 + 倒计时 + 连击） | idea | - | | REQ-26 ✅, REQ-10 |
| REQ-31 | Boss 关卡页面（关键词输入） | idea | - | | REQ-26 ✅, REQ-12 |
| REQ-32 | 知识链串联页面（图谱可视化） | idea | - | | REQ-26 ✅, REQ-13 |
| REQ-33 | 翻牌奖励页面（DNF 风格翻牌动画） | idea | - | | REQ-26 ✅, REQ-24 |
| REQ-34 | 游戏结算 + 历史页面 | idea | - | | REQ-26 ✅, REQ-14 |
| REQ-35 | 卡牌图鉴页面（网格 + 筛选） | idea | - | 群组维度 + 全局统计切换 | REQ-26 ✅, REQ-18 |
| REQ-36 | 盲盒抽卡页面（动画 + 特效） | idea | - | 增加保留/换积分/放入卡包选择 UI | REQ-26 ✅, REQ-19, REQ-20 |
| REQ-37 | 卡牌详情页面（升星/兑换/分解） | idea | - | 增加实体奖励兑换入口 | REQ-26 ✅, REQ-22, REQ-23, REQ-57 |
| REQ-38 | 保底进度页面 | idea | - | 群组维度 | REQ-26 ✅, REQ-25 |
| REQ-39 | 个人中心页面 | idea | - | | REQ-26 ✅ |
| REQ-62 | 群组管理端 — IP 库关联页 | done | [req-62-group-ip-library-page.md](prd/req-62-group-ip-library-page.md) | 知识库全局共享，不需要群组授权 | REQ-26 ✅, REQ-51 ✅ |
| REQ-109 | IP 库 — 禁用/删除语义分离 | done | - | group_ip_library 表新增 status 字段（ACTIVE/DISABLED），禁用=软删除（保留关联数据可恢复），删除=彻底解除关联。前端 ⋮ 菜单中「禁用」和「删除」行为区分 | REQ-62 |
| REQ-110 | IP 库 — 已收集卡片禁止删除校验 | idea | - | 前后端双重校验：有群组成员已收集该 IP 卡片的关联不可删除（前端删除选项置灰，后端 PUT 拒绝）。需新增「查询群组各 IP 卡片收集量」接口 | REQ-62 |
| REQ-63 | 群组管理端 — 成员与积分管理页 | idea | - | 成员列表查询 + 踢人 API + 积分修改。踢人仅 OWNER/ADMIN 可操作，不能踢 OWNER | REQ-26 ✅, REQ-49 ✅, REQ-52, REQ-50 ✅ |
| REQ-64 | 群组管理端 — 奖励兑换与订单页 | idea | - | | REQ-26 ✅, REQ-58, REQ-59 |
| REQ-71 | 卡包页面 | idea | - | 查看卡包中的卡片，支持取出处理 | REQ-26 ✅, REQ-70 |
| REQ-72 | 每日签到页面 | idea | - | 群组维度签到、连续签到进度展示 | REQ-26 ✅, REQ-68 |
| REQ-73 | 群组管理端 — 签到策略配置页 | idea | - | 配置签到规则和积分奖励阶梯 | REQ-26 ✅, REQ-69 |
| REQ-78 | 群组管理端 — 收藏里程碑与成就配置页 | idea | - | 群组管理员配置群组内各 IP 的收藏里程碑阶梯和成就 | REQ-26 ✅, REQ-75 |
| REQ-79 | 用户端 — 收藏成就与里程碑展示页 | idea | - | 查看群组内收藏进度、成就列表、已达成成就和里程碑奖励 | REQ-26 ✅, REQ-76 |
| REQ-98 | 用户端 — 知识库浏览页（按分类浏览/阅读/学习埋点） | idea | - | 用户按分类浏览知识条目、阅读详情；学习行为埋点。分类树接口与前端联调验证在此需求进行 | REQ-26 ✅, REQ-97 ✅, REQ-08 ✅ |

### Phase 7：系统管理后台（管理端）

> 系统管理员操作的后端 API + 前端页面（frontend/admin/），与用户侧功能独立。
> 优先级：**P2** — 管理端运营工具，部分依赖 P0 后端，独立管理端页面可并行

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-42 | 管理后台 — 数据统计仪表盘 | idea | - | | REQ-40 ✅, REQ-41 ✅ |
| REQ-46 | 管理后台 — 盲盒概率配置页 | idea | - | | REQ-40 ✅, REQ-41 ✅, REQ-19 |
| REQ-47 | 管理后台 — 用户管理页 | idea | - | | REQ-40 ✅, REQ-41 ✅ |
| REQ-56 | 商城商品 — 系统管理员 CRUD API | idea | - | 每个 IP 卡片每个星级对应一个商品 | REQ-17 ✅ |
| REQ-66 | 管理后台 — 商城商品管理页 | idea | - | 卡片星级与商品映射 | REQ-40 ✅, REQ-41 ✅, REQ-56 |
| REQ-67 | 管理后台 — 订单管理页 | idea | - | 全局订单查看与状态管理 | REQ-40 ✅, REQ-41 ✅, REQ-59 |
| REQ-77 | 收藏成就模板 — 系统管理员 CRUD API | idea | - | 系统管理员为每个 IP 创建/管理成就模板（名称+达成条件+推荐奖励） | REQ-17 ✅ |
| REQ-80 | 管理后台 — 收藏成就模板管理页 | idea | - | 系统管理员管理各 IP 的成就模板，支持设为默认启用 | REQ-40 ✅, REQ-41 ✅, REQ-77 |

### Phase 8：回收站与数据管理增强

> 通用回收站系统 + 批量导入 + 编辑器优化。回收站替代当前软删除。
> 优先级：**P0**（回收站基础设施 + 资源对接） / **P3**（批量导入/编辑器）

**资源对接回收站**

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-108 | 知识条目管理对接回收站 | done | [req-108-knowledge-item-recycle-bin.md](prd/req-108-knowledge-item-recycle-bin.md) | DELETE 改为移入回收站；实现 `KnowledgeItemRecycleBinStrategy` Bean，`knowledge_item_deleted.related_data` JSON 快照，恢复时校验关联分类仍存在，purge 时清理封面图 | REQ-100 ✅, REQ-103 ✅, REQ-102 ✅, REQ-97 ✅ |

### 后期优化 / 跨领域增强

| 编号 | 需求名称 | 状态 | PRD | 备注 | 前置需求 |
|------|---------|------|-----|------|---------|
| REQ-81 | Token 黑名单 Redis 存储迁移 | idea | - | P3：从内存迁移到 Redis，支持多实例部署。REQ-06 预留接口 | REQ-06 ✅ |
| REQ-99 | 知识库 — 学习记录追踪 + 富媒体扩展 | idea | - | P0：`user_learning_record` 表（用户维度，浏览/停留时长/累计学习统计），富媒体扩展（视频/外链等） | REQ-97 ✅, REQ-98 |
