# REQ-65：管理后台 — 知识库管理页

> 状态：`designed`
> 创建：2026-06-12
> 前置依赖：REQ-07（知识点分类管理端 CRUD API）、REQ-40（管理后台脚手架）、REQ-83（文件服务）、REQ-87（Admin 对接文件服务）

## 1. 概述

为管理后台实现知识库管理页面，采用左侧树形导航 + 右侧详情面板的交互模式，支持分类的增删改查、拖拽排序/移动、文件上传等功能。同时补充后端删除接口的子分类校验（REQ-07 遗漏）。

## 2. 用户故事

- **作为管理员**，我希望通过树形结构直观浏览知识点分类层级，方便快速定位目标分类
- **作为管理员**，我希望通过拖拽调整分类顺序和层级，操作自然流畅
- **作为管理员**，我希望上传分类图标和封面图，让分类展示更美观
- **作为管理员**，我希望删除分类时有安全校验，避免误删有子分类或关联题库的分类

## 3. 页面结构

### 3.1 整体布局

左右分栏结构，路由路径 `/content/knowledge-base`。

```
┌──────────────────┐  ┌────────────────────────────┐
│   CategoryTree   │  │     CategoryDetail          │
│                  │  │                              │
│  [搜索框]        │  │  分类名称                     │
│  [☐ 显示停用]    │  │  描述、图标、颜色、封面图      │
│                  │  │  排序号、状态                  │
│  📂 科学         │  │                              │
│   └ 物理 ←选中   │  │  [编辑] [移动] [删除]        │
│     └ 力学       │  │                              │
│  📂 历史         │  └────────────────────────────┘
│                  │
│  [+ 新建分类]    │  弹出层：
│                  │  - CategoryFormModal (新建/编辑)
└──────────────────┘  - MoveModal (TreeSelect 移动)
```

### 3.2 文件结构

```
frontend/admin/src/
├── services/
│   └── knowledge-category.ts          ← API 调用层
├── pages/KnowledgeBase/
│   ├── index.tsx                       ← 主页面（左右分栏 + 状态管理）
│   └── components/
│       ├── CategoryTree.tsx            ← 左侧树（拖拽排序/移动）
│       ├── CategoryDetail.tsx          ← 右侧详情面板
│       ├── CategoryFormModal.tsx       ← 新建/编辑 Modal 表单
│       └── MoveModal.tsx               ← 移动分类 Modal（TreeSelect）
```

## 4. 功能需求

### 4.1 左侧树（CategoryTree）

**数据加载：**
- 页面加载时调用 `GET /api/admin/knowledge-categories/tree` 获取完整分类树
- 默认展开第一级节点
- 按 `sortOrder` 排序展示

**搜索：**
- 顶部搜索框，输入关键词实时过滤树节点（前端过滤，匹配名称包含关键词的节点及其祖先链）

**显示停用分类：**
- 默认隐藏 INACTIVE 分类
- 提供 "显示停用" 复选框开关
- 启用后 INACTIVE 节点灰显展示，操作按钮中禁用"新建子分类"

**节点交互：**
- 单击节点 → 右侧展示该分类详情
- 拖拽排序：同级节点之间拖拽调整 sortOrder
- 拖拽移动：拖拽到某节点上悬停 > 0.5s，移动到该节点下成为子分类
- 新建按钮：底部"+ 新建分类"按钮，打开 CategoryFormModal（无预选父级=顶级）

### 4.2 右侧详情面板（CategoryDetail）

**未选中状态：**
- 显示引导提示"请在左侧选择一个分类"

**选中状态展示字段：**

| 字段 | 展示方式 |
|------|---------|
| 名称 | 文本 |
| 描述 | 文本（多行） |
| 图标 | 图片缩略图 + URL |
| 颜色 | 色块 + hex 值 |
| 封面图 | 图片缩略图 + URL |
| 排序号 | 数字 |
| 状态 | Tag 标签（ACTIVE 绿色 / INACTIVE 灰色） |
| 创建时间 | 格式化日期 |
| 更新时间 | 格式化日期 |

**操作按钮：**
- **编辑** → 打开 CategoryFormModal（编辑模式，预填当前值）
- **移动** → 打开 MoveModal（TreeSelect 选择目标父级）
- **删除** → 前端预检（是否有子分类）→ 确认弹窗 → 调用删除 API

### 4.3 新建/编辑 Modal（CategoryFormModal）

**表单字段：**

| 字段 | 组件 | 校验规则 | 备注 |
|------|------|---------|------|
| 父级分类 | TreeSelect | 新建可选（空=顶级） | 编辑模式不可修改 |
| 名称 | Input | 必填，2-50 字 | |
| 描述 | TextArea | 最多 500 字 | |
| 图标 | Upload | 图片类型，≤10MB | bizType=CATEGORY_ICON |
| 颜色 | ColorPicker + Input | 最多 20 字 | hex 色值 |
| 封面图 | Upload | 图片类型，≤10MB | bizType=CATEGORY_COVER |
| 排序号 | InputNumber | 必填，≥0 | |

**文件上传流程（遵循 REQ-83 + REQ-87）：**
1. 点击上传 → 调用 `GET /api/admin/upload-credential?bizType=CATEGORY_ICON`（或 `CATEGORY_COVER`）
2. 后端通过 Feign M2M 调用 file 服务获取凭证，返回 `{ token, uploadUrl }`（uploadUrl 由后端从配置拼接，如 `http://localhost:8083/api/file/upload`）
3. 前端携带 `X-Upload-Token: {token}` + `X-User-Id: {userId}` 请求头，直传 uploadUrl（`POST multipart/form-data`，字段名 `file`）
4. 上传成功返回 `{ fileId, url }`，自动填入表单对应字段
5. 提交表单时，URL 随新建/编辑请求入库

**上传错误处理：**
- 凭证获取失败（网络异常/服务不可用）→ 提示"上传服务暂时不可用，请稍后重试"
- 直传文件失败（网络异常）→ 提示"上传失败，请重试"
- 文件类型不符 → 提示"仅支持 JPG/PNG/GIF/WEBP 格式"
- 文件大小超限 → 提示"文件大小不能超过 10MB"
- 凭证过期 → 提示"上传凭证已过期，请重新选择文件"

### 4.4 移动 Modal（MoveModal）

- 展示分类树选择器（Ant Design TreeSelect）
- 排除当前节点及其后代（后端已有循环引用校验）
- 选中目标父级后调用 `PUT /api/admin/knowledge-categories/{id}/move`

### 4.5 拖拽排序/移动

- 使用 `@dnd-kit/core` + `@dnd-kit/sortable` 实现树形拖拽
- **排序**：拖拽到同级节点之间时显示"插入"指示线（蓝色横线）→ 释放后调用 `PUT /api/admin/knowledge-categories/batch-sort`，批量更新所有受影响兄弟节点的 sortOrder
- **移动**：拖拽到某节点上时该节点高亮显示"移入"效果（蓝色边框背景）→ 释放后调用 `PUT /api/admin/knowledge-categories/{id}/move`
- 排序和移动通过 drop zone 位置区分：悬停在节点上方/下方 1/4 区域为插入排序，悬停在节点中央 1/2 区域为移入该节点
- 操作成功后刷新树

### 4.6 删除

**前端预检：**
- 检查当前节点是否有 children → 有则弹提示"该分类下有子分类，无法删除"，不调用 API
- 无子分类 → 确认弹窗"确定删除该分类？"→ 确认后调用 `DELETE /api/admin/knowledge-categories/{id}`

**后端校验（REQ-65 补充）：**
- `KnowledgeCategoryAppService.delete()` 增加子分类存在性检查（ACTIVE 和 INACTIVE 均计算）
- 题库关联检查预留（题库模块开发后补充）
- 后端错误消息明确数量："该分类下存在 N 个子分类（含已停用），无法删除"，前端直接展示该消息

## 5. 后端改动（归入 REQ-65）

### 5.1 删除接口增加子分类校验

**文件：** `KnowledgeCategoryAppService.java`

**改动：** `delete()` 方法在执行软删除前，检查该分类是否存在子分类（ACTIVE 或 INACTIVE 均算）。存在子分类时抛出 `BusinessException("该分类下存在子分类，无法删除")`。

**改动：** `KnowledgeCategoryDomainService.java` 新增 `validateDelete()` 方法，校验逻辑：
1. 查询该分类的子分类数量（通过 `categoryRepositoryPort.countByParentId(id)`）
2. 子分类数 > 0 时抛异常

**仓储端口新增方法：** `KnowledgeCategoryRepositoryPort.countByParentId(Long parentId)` → `long`

### 5.2 上传凭证接口（已完成，REQ-87）

管理端上传凭证接口 `GET /api/admin/upload-credential?bizType=xxx` 已由 REQ-87 完成，前端直接调用即可。bizType 映射：
- `CATEGORY_ICON` → 知识点分类图标
- `CATEGORY_COVER` → 知识点分类封面

### 5.3 批量排序接口

**文件：** `KnowledgeCategoryController.java`

**新增端点：** `PUT /api/admin/knowledge-categories/batch-sort`
- 请求体：`{ items: [{ id: Long, sortOrder: Integer }] }`
- 一次事务内批量更新多个兄弟节点的 sortOrder
- 用于拖拽排序后原子性更新所有受影响节点
- **校验规则：**
  - items 不能为空，数量上限 50
  - 所有 id 必须存在
  - 所有 id 必须属于同一父级（不同父级的节点不能一起排序）

### 5.4 sortOrder 默认值规则

**改动：** `KnowledgeCategoryAppService.create()` — 如果前端未传 sortOrder（null），后端自动取同级最大 sortOrder + 1；同级无节点时默认为 0。前端表单中 sortOrder 字段默认不填（由后端自动计算），但允许手动输入覆盖。

### 5.5 题库关联检查（预留）

题库模块开发后，在 `delete()` 方法中补充题库关联检查：
- 查询该分类下是否存在题库记录
- 存在时抛出 `BusinessException("该分类下存在题库，无法删除")`

## 6. API 调用层

**文件：** `frontend/admin/src/services/knowledge-category.ts`

| 函数 | 方法 | 端点 | 用途 |
|------|------|------|------|
| `getTree()` | GET | `/api/admin/knowledge-categories/tree` | 加载分类树 |
| `getById(id)` | GET | `/api/admin/knowledge-categories/{id}` | 获取分类详情 |
| `create(data)` | POST | `/api/admin/knowledge-categories` | 创建分类 |
| `update(id, data)` | PUT | `/api/admin/knowledge-categories/{id}` | 更新分类 |
| `move(id, data)` | PUT | `/api/admin/knowledge-categories/{id}/move` | 移动分类 |
| `deleteCategory(id)` | DELETE | `/api/admin/knowledge-categories/{id}` | 删除分类 |
| `getUploadCredential(bizType)` | GET | `/api/admin/upload-credential?bizType={bizType}` | 获取上传凭证（含 uploadUrl） |

## 7. 依赖项

| 依赖 | 说明 | 状态 |
|------|------|------|
| REQ-07 知识点分类 CRUD API | 后端 7 个 CRUD 端点 | done |
| REQ-40 管理后台脚手架 | Ant Design Pro + 路由 + 请求服务 | done |
| REQ-83 文件服务 | 图片上传/下载 | done |
| REQ-87 Admin 对接文件服务 | 上传凭证接口 + Feign Client | done |
| REQ-84 Nacos 服务发现 | file 服务注册发现（上传凭证 Feign 调用依赖） | done |
| REQ-85 机机鉴权 | file 服务 M2M 鉴权 | done |
| `@dnd-kit/core` | npm 依赖，拖拽排序 | 待引入 |
| `@dnd-kit/sortable` | npm 依赖，拖拽排序 | 待引入 |
| 题库模块 | 删除接口补充题库关联检查 | 未开发 |

## 8. 分阶段交付策略

REQ-83（文件服务）和 REQ-87（Admin 对接文件服务）均已完成，采用分阶段交付以降低前端集成风险：

**阶段一（核心功能）：**
- 分类 CRUD（新建、编辑、查看详情、删除）
- 左侧树形导航 + 搜索 + 停用开关
- 拖拽排序 + 拖拽移动
- 移动 Modal
- 后端删除校验补充
- 后端批量排序接口
- 图标/封面图字段为 URL 文本输入（降级模式）

**阶段二（文件上传前端集成）：**
- 图标/封面图字段升级为文件上传（Upload 组件 + 凭证 + 直传）
- 上传错误处理和重试

**前端实现方式：** 上传组件封装为 `ImageUploadField`，阶段一时该组件渲染为 URL 文本输入；阶段二升级为 Upload 组件，通过配置开关控制。上传凭证接口已由 REQ-87 完成，阶段二只需前端对接。

## 9. Verification Plan

### 9.1 手动验证

1. 页面加载，左侧树正确展示分类层级，默认展开第一级
2. 搜索框输入关键词，树节点实时过滤
3. 勾选"显示停用"，INACTIVE 分类灰显出现；取消勾选则隐藏
4. 点击树节点，右侧展示该分类详情（名称、描述、图标、颜色、封面图、排序、状态、时间）
5. 点击"新建分类"，Modal 弹出，填写表单提交成功，树中新增节点
6. 点击"编辑"，Modal 弹出预填当前值，修改后提交成功，详情刷新
7. 上传图标/封面图，凭证获取 → 直传 file 服务 → URL 回填正常
8. 拖拽排序：同级节点拖拽后顺序变化，刷新页面顺序保持
9. 拖拽移动：将节点拖到另一节点上，成为其子分类，树结构更新
10. 点击"移动"，TreeSelect 选择目标父级，移动成功
11. 删除有子分类的分类 → 前端提示无法删除
12. 删除叶子节点 → 确认弹窗 → 删除成功，树中移除该节点
13. 未选中分类时右侧显示引导提示

### 9.2 自动化测试

- `CategoryTree.test.tsx`：树渲染、搜索过滤、停用开关、节点选中
- `CategoryDetail.test.tsx`：未选中状态、详情展示、操作按钮
- `CategoryFormModal.test.tsx`：新建表单提交、编辑预填、字段校验、文件上传
- `MoveModal.test.tsx`：TreeSelect 展示、排除当前节点及后代、移动提交
- `KnowledgeCategoryService.test.ts`：API 调用函数正确性
- 后端：`KnowledgeCategoryAppServiceTest` 补充 delete 方法子分类校验测试用例
- 后端：`KnowledgeCategoryDomainServiceTest` 补充 validateDelete 测试用例

## 10. 回滚标准

- 恢复 `KnowledgeBase/index.tsx` 为原始 placeholder
- 删除 `pages/KnowledgeBase/components/` 目录
- 删除 `services/knowledge-category.ts`
- 移除 `@dnd-kit` 依赖（`package.json` + `node_modules`）
- 后端回滚 `KnowledgeCategoryAppService.delete()` 和 `KnowledgeCategoryDomainService` 改动
- 回滚 `KnowledgeCategoryRepositoryPort.countByParentId` 新增方法及其实现
- 删除新增的 `PUT /api/admin/knowledge-categories/batch-sort` 端点
