# REQ-43 管理后台 — 题库管理页

## 产品定位

在管理后台（Ant Design Pro）中实现题库的完整 CRUD 管理页面，对接 REQ-09 后端 API，支持四种题型（单选/多选/判断/填空）、分类关联、批量启用/禁用、Excel 批量导入。

## 前置依赖

| 依赖 | 说明 | 状态 |
|------|------|------|
| REQ-09 | 题库管理端 CRUD API（13 个端点） | done |
| REQ-40 | 管理后台脚手架（Ant Design Pro + 路由） | done |
| REQ-41 | 管理后台登录鉴权 | done |
| REQ-07 | 知识点分类管理端 API（分类树用于题目关联） | done |
| `services/typing.ts` | 共享 PageResult\<T\> | 已存在 |
| `services/request.ts` | 全局 axios 拦截器（JWT 注入、Result 解包） | 已存在 |

**路由**：`/content/question-bank` 已在 `config/routes.ts` 配置（菜单"题库管理"）。
**不需要文件上传服务**：题库无图片字段，Excel 导入走 Spring `MultipartFile`，不经 REQ-83 文件服务。

## 用户故事

**作为** 系统管理员
**我想要** 在管理后台的「内容管理 > 题库管理」页面查看、创建、编辑、停用题目，并能批量导入、批量启停
**以便于** 维护游戏出题的基础数据，覆盖秒判/Boss/串联三种游戏层

## 功能需求

### 1. 题目列表（ProTable）

**路径**：`/content/question-bank`

| 功能 | 说明 |
|------|------|
| 表格列 | ID、题型(type Tag)、题目内容(content 截断)、难度(difficulty Tag)、分类(categoryIds 转名称 Tag 列表)、状态(status Tag)、更新时间(updatedAt)、操作 |
| 分页 | 默认每页 20 条，后端分页 |
| 搜索 | keyword 输入框（按 content 模糊） |
| 筛选 | type 下拉（全部/单选/多选/判断/填空）、difficulty 下拉（全部/简单/中等/困难）、status 下拉（全部/启用/停用）、categoryId TreeSelect（按知识点分类筛选） |
| 排序 | sort 下拉（创建时间/更新时间/难度）、order 切换（升/降）。**这是项目首个支持客户端排序的管理页** |
| 行选择 | checkbox，支持多选 |
| 操作列 | 「编辑」（打开 Drawer）、「停用/启用」（单条切换状态） |
| 批量操作 | 选中行后顶部显示「批量启用」「批量停用」按钮，调对应 batch 接口 |

#### 列展示细节

- **题型 Tag 颜色**：单选=blue、多选=purple、判断=cyan、填空=orange
- **难度 Tag 颜色**：简单=green、中等=gold、困难=red
- **状态 Tag**：ACTIVE=green「启用」、INACTIVE=default「停用」
- **content 列**：超过 50 字截断显示 `...`，hover 显示完整内容（Tooltip）
- **categoryIds 列**：异步加载分类名称，每项渲染为 Tag，最多显示 2 个 + `+N` 折叠
- **answer 列不展示**（避免泄露答案，列表只看题干元数据）

#### difficulty 与 type 的常量映射

实现层在 `src/services/questionBank.ts` 导出集中常量，避免散落硬编码：

```typescript
// 难度：后端 Integer（1/2/3）↔ 前端展示中文
export const DIFFICULTY_OPTIONS = [
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' },
] as const;

// 题型：后端 String 枚举 ↔ 前端展示中文 + Tag 颜色
export const QUESTION_TYPE_OPTIONS = [
  { value: 'SINGLE_CHOICE',   label: '单选', color: 'blue' },
  { value: 'MULTIPLE_CHOICE', label: '多选', color: 'purple' },
  { value: 'TRUE_FALSE',      label: '判断', color: 'cyan' },
  { value: 'FILL_BLANK',      label: '填空', color: 'orange' },
] as const;

// 状态：后端 String ↔ 前端展示中文 + Tag 颜色
export const QUESTION_STATUS_OPTIONS = [
  { value: 'ACTIVE',   label: '启用', color: 'green' },
  { value: 'INACTIVE', label: '停用', color: 'default' },
] as const;
```

> 后端契约：`difficulty` 为 `Integer`（1/2/3），不可改为 String 枚举（破坏 REQ-09 既有契约）。前端通过常量做映射。

### 2. 创建题目（Drawer）

| 项目 | 说明 |
|------|------|
| 触发 | 表格上方「新建题目」按钮 |
| 容器 | 右侧抽屉 Drawer，宽度 720px |
| 表单字段 | 见下方「题型动态表单」章节 |
| 提交 | POST `/api/admin/questions`，请求体含 categoryIds，一次性完成主表创建+分类关联 |
| 成功 | 关闭抽屉、刷新列表、`message.success('创建成功')` |
| 失败 | Drawer 不关闭，表单底部展示后端错误信息（如「题目内容不能为空」） |

### 3. 编辑题目（Drawer）

| 项目 | 说明 |
|------|------|
| 触发 | 操作列「编辑」按钮 |
| 容器 | 同创建抽屉，预填当前题目数据 |
| 加载 | GET `/api/admin/questions/{id}` 返回完整字段（含 categoryIds） |
| 提交 | **两次请求**：① PUT `/api/admin/questions/{id}`（更新主表，不含 categoryIds）→ ② PUT `/api/admin/questions/{id}/categories`（全量替换分类关联）。两次都成功才算提交成功 |
| 题型字段不可改 | 编辑模式下题型 Select 禁用（题型改变会破坏答案结构，本期不支持），如需换题型请删除重建 |
| 成功 | 同创建 |

### 4. 停用/启用（单条 + 批量）

| 操作 | 触发 | API |
|------|------|-----|
| 单条停用 | 操作列「停用」按钮（仅 ACTIVE 行显示） | PUT `/api/admin/questions/batch-deactivate` body `{ids:[id]}` |
| 单条启用 | 操作列「启用」按钮（仅 INACTIVE 行显示） | PUT `/api/admin/questions/batch-activate` body `{ids:[id]}` |
| 批量停用 | 顶部「批量停用」按钮（选中行后显示） | PUT `/api/admin/questions/batch-deactivate` body `{ids:[...]}` |
| 批量启用 | 顶部「批量启用」按钮（选中行后显示） | PUT `/api/admin/questions/batch-activate` body `{ids:[...]}` |

**确认弹窗**：批量操作 Popconfirm「确定对选中的 N 题执行{启用/停用}？」；单条直接执行（操作可逆，无确认）。

**关于 DELETE 接口**：后端提供 `DELETE /api/admin/questions/{id}`（软删除→INACTIVE），效果与 batch-deactivate 等价。本期**前端不暴露 DELETE 操作**，统一走批量接口，避免管理员混淆"删除"与"停用"。如未来需物理删除或区别对待，再单独暴露。

### 5. Excel 批量导入

| 元素 | 说明 |
|------|------|
| 入口 | 表格上方「批量导入」按钮 + 「下载模板」按钮 |
| 下载模板 | GET `/api/admin/questions/import-template`，浏览器直接下载 .xlsx |
| 导入流程 | 点击「批量导入」→ AntD Upload（beforeUpload 拦截）→ 弹确认 → POST `/api/admin/questions/import`（multipart/form-data，字段名 file） |
| 结果展示 | 导入完成后弹 Modal，展示 totalCount/successCount/failCount 统计 + 失败明细表格（row、reason） |
| 失败处理 | 部分失败时已成功行已入库，失败行展示明细供用户修正后重新导入（重新导入会创建重复题目，需在结果 Modal 中提示「请仅修正失败行重新上传」） |
| 文件约束 | 仅 .xlsx，单文件最大 10MB |

**模板列定义**（已在 REQ-09 后端实现）：
题型、题目内容、选项A、选项B、选项C、选项D、正确答案、难度、解析、标签、分类ID

### 6. 题型动态表单

Drawer 表单根据 `type` 字段动态渲染不同子区域。表单结构：

```
┌──────────────────────────────────────────┐
│ 题型 [Select: 单选/多选/判断/填空] *      │  ← 创建模式可选，编辑模式禁用
│ 难度 [Select: 简单/中等/困难] *            │
│ 题目内容 [TextArea, max 500] *            │
├──────────────────────────────────────────┤
│ 选项区域（仅选择题显示）                   │
│   A [Input] [删除]                         │
│   B [Input] [删除]                         │
│   [+ 新增选项] （最多 6 个，最少 2 个）    │
├──────────────────────────────────────────┤
│ 答案区域（根据题型）                       │
│   单选：RadioGroup（选项列表）             │
│   多选：CheckboxGroup（选项列表）          │
│   判断：RadioGroup（对/错）                 │
│   填空：关键词列表 [Input + +/- 按钮]       │
├──────────────────────────────────────────┤
│ 解析 [TextArea, 选填]                      │
│ 标签 [Select multiple, mode=tags]          │
│ 知识点分类 [TreeSelect multiple]           │  ← 嵌入表单，不单独 Modal
└──────────────────────────────────────────┘
```

#### 题型切换 UX（仅创建模式）

切换题型时的字段处理（用 form 提示，无确认弹窗，避免打断）：

| 切换方向 | 选项 | 答案 |
|---------|------|------|
| → 单选/多选 | 保留（不足 2 个则补默认 A/B） | 清空 |
| → 判断 | 清空 | 默认 `true` |
| → 填空 | 清空 | 默认空列表 |
| 单选 ↔ 多选 | 保留 | 保留（单选转单元素数组，多选取首元素） |

#### answer 字段传输协议

| 题型 | 前端控件值 | 提交的 answer 字段（String） |
|------|-----------|-----------------------------|
| 单选 | `"A"` | `"A"` |
| 多选 | `["A","C"]` | `"[\"A\",\"C\"]"`（JSON.stringify） |
| 判断 | `true` | `"true"` |
| 填空 | `["k1","k2"]` | `"[\"k1\",\"k2\"]"`（JSON.stringify） |

回显时按 `type` 反向解析：单选/判断直接用 String，多选/填空 `JSON.parse`。

### 7. 校验规则（前端表单）

| 字段 | 规则 |
|------|------|
| 题型 | 必选 |
| 难度 | 必选 |
| 题目内容 | 必填，≤500 字 |
| 选项（选择题） | 至少 2 个非空，最多 6 个；选项 key 自动分配 A/B/C/D/E/F |
| 答案 | 单选必选一项；多选至少 2 项；判断必选；填空至少 1 个关键词 |
| 解析 | 选填，≤1000 字 |
| 标签 | 选填，每项 ≤20 字，最多 10 个 |
| 分类 | 选填，多选 |

前端校验通过后才提交。后端返回的校验错误（如分类不存在）在 Drawer 底部统一展示。

## 技术实现

### 新增文件

| 文件 | 说明 |
|------|------|
| `src/services/questionBank.ts` | API 服务层：类型定义 + 全部请求函数 |
| `src/pages/QuestionBank/index.tsx` | 重写：列表页主入口（ProTable + 批量操作 + 导入） |
| `src/pages/QuestionBank/components/QuestionFormDrawer.tsx` | 创建/编辑抽屉（含题型动态表单） |
| `src/pages/QuestionBank/components/ImportResultModal.tsx` | 导入结果展示 Modal |
| `src/pages/QuestionBank/components/__tests__/QuestionFormDrawer.test.tsx` | 表单组件测试 |
| `src/pages/QuestionBank/components/__tests__/ImportResultModal.test.tsx` | 导入结果 Modal 测试 |
| `src/pages/QuestionBank/__tests__/index.test.tsx` | 列表页测试 |
| `src/services/__tests__/questionBank.test.ts` | 服务层测试 |

### 复用现有

- `services/request.ts`（全局 axios）
- `services/typing.ts`（PageResult\<T\>）
- `services/knowledge-category.ts` 中的 `getTree()`（用于分类 TreeSelect 数据源）

### 分类树加载与缓存策略

页面初始化时**一次性**调用 `getTree()`，flatten 为 `Map<number, string>`（id → name）缓存到组件 state/ref：

- **筛选区 TreeSelect**：直接消费 tree 结构（保留层级）
- **列表 categoryIds 列渲染**：从 Map 中按 id 查 name，渲染为 Tag 列表
- **Drawer 表单 TreeSelect**：复用同一份 tree 数据

**禁止**每行渲染时单独请求分类名称。`getTree()` 在整个页面生命周期内只调用一次，刷新页面或路由切换时重新加载。

抽屉组件通过 props 接收 tree 数据和 idToName Map，不自行请求。

### 类型定义

```typescript
// 题型枚举
type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_BLANK';

// 题目响应
interface QuestionResponse {
  id: number;
  type: QuestionType;
  content: string;
  options: { key: string; content: string }[] | null;
  answer: string;            // 单选="A" 多选="[\"A\",\"C\"]" 判断="true" 填空="[\"k1\"]"
  explanation: string;
  difficulty: 1 | 2 | 3;
  tags: string[];
  status: 'ACTIVE' | 'INACTIVE';
  categoryIds: number[];
  createdAt: string;
  updatedAt: string;
}

// 创建请求（含 categoryIds，一次性提交）
interface CreateQuestionRequest {
  type: QuestionType;
  content: string;
  options: { key: string; content: string }[] | null;
  answer: string;
  difficulty: 1 | 2 | 3;
  explanation?: string;
  tags?: string[];
  categoryIds?: number[];
}

// 更新请求（不含 categoryIds，分类单独 PUT）
interface UpdateQuestionRequest {
  content?: string;
  options?: { key: string; content: string }[];
  answer?: string;
  difficulty?: 1 | 2 | 3;
  explanation?: string;
  tags?: string[];
}

// 分页查询参数
interface QuestionQuery {
  keyword?: string;
  type?: QuestionType;
  difficulty?: 1 | 2 | 3;
  categoryId?: number;
  tag?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  sort?: 'createdAt' | 'updatedAt' | 'difficulty';
  order?: 'asc' | 'desc';
  page?: number;
  size?: number;
}

// 导入结果
interface QuestionImportResult {
  totalCount: number;
  successCount: number;
  failCount: number;
  failDetails: { row: number; reason: string }[];
}
```

### API 函数

```typescript
listQuestions(params: QuestionQuery): Promise<PageResult<QuestionResponse>>
getQuestionById(id: number): Promise<QuestionResponse>
createQuestion(data: CreateQuestionRequest): Promise<QuestionResponse>
updateQuestion(id: number, data: UpdateQuestionRequest): Promise<QuestionResponse>
updateQuestionCategories(id: number, categoryIds: number[]): Promise<void>
batchActivate(ids: number[]): Promise<void>
batchDeactivate(ids: number[]): Promise<void>
downloadImportTemplate(): Promise<Blob>
importQuestions(file: File): Promise<QuestionImportResult>
```

## Impact Analysis

### 修改的文件

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `src/pages/QuestionBank/index.tsx` | 重写 | 占位符 → 完整 CRUD 页面 |
| `src/services/questionBank.ts` | 新增 | API 服务层 |
| `src/pages/QuestionBank/components/QuestionFormDrawer.tsx` | 新增 | 创建/编辑 Drawer |
| `src/pages/QuestionBank/components/ImportResultModal.tsx` | 新增 | 导入结果 Modal |
| `src/pages/QuestionBank/**/__tests__/*.test.tsx` | 新增 | 组件/页面测试 |
| `src/services/__tests__/questionBank.test.ts` | 新增 | 服务层测试 |

### 配置变更

无。路由 `/content/question-bank` 已在 `config/routes.ts` 配置。

### 受影响的现有功能

无。QuestionBank 当前仅占位符，无现有功能受影响。

### 后端改动

**无**。REQ-09 后端 13 个端点已全部实现，本期纯前端。

## Verification Plan

### 手动验证步骤

1. 启动后端 `knowledge-game-admin`（端口 8081）+ Nacos + MySQL
2. 启动前端 `cd frontend/admin && npm run dev`
3. 登录管理后台
4. 导航到「内容管理 > 题库管理」

**CRUD 路径**：

5. **创建单选题**：新建 → 选「单选」→ 填内容 → 加 4 个选项 → 选答案 A → 选分类 → 提交 → 确认列表刷新
6. **创建多选题**：同上，验证答案 `["A","C"]` 提交后回显正确
7. **创建判断题**：选「判断」→ 验证选项区域隐藏，答案变为对/错 Radio
8. **创建填空题**：选「填空」→ 验证选项隐藏，答案变为关键词 +/- 列表
9. **编辑题目**：点击编辑 → 验证所有字段（含分类）正确预填 → 修改后提交 → 验证两次请求都成功
10. **题型不可改**：编辑模式下题型 Select 应禁用

**批量操作路径**：

11. **单条停用/启用**：操作列切换 → 状态 Tag 变化
12. **批量启用**：选中 3 条停用题 → 批量启用 → 确认弹窗 → 全部变为 ACTIVE
13. **批量停用**：选中混合状态 → 批量停用 → 全部变为 INACTIVE

**导入路径**：

14. **下载模板**：点击下载 → 浏览器下载 .xlsx
15. **填写模板**：填入 5 行数据（含 1 行故意错误，如选择题缺选项 B）
16. **导入**：上传 .xlsx → 结果 Modal 显示 totalCount=5, successCount=4, failCount=1，失败明细含 row + reason
17. **空文件导入**：上传空 .xlsx → 后端返回错误 → 前端展示

**筛选/排序路径**：

18. **搜索**：keyword 输入"Java" → 列表过滤
19. **题型筛选**：选「单选」→ 仅显示单选题
20. **分类筛选**：选某分类 → 仅显示该分类下的题
21. **排序**：sort=难度, order=升 → 列表按难度升序

**边界路径**：

22. **创建校验**：选项少于 2 个 → 提交按钮禁用或表单报错
23. **多选答案**：只选 1 个 → 报错"多选至少 2 项"
24. **后端校验**：分类 ID 不存在 → Drawer 底部展示错误信息

### 自动化测试覆盖

- `services/__tests__/questionBank.test.ts`：所有 API 函数（mock request，验证 URL、method、payload）
- `pages/QuestionBank/__tests__/index.test.tsx`：列表渲染、搜索/筛选/排序、批量选择、批量操作、操作列
- `pages/QuestionBank/components/__tests__/QuestionFormDrawer.test.tsx`：
  - 创建模式：4 种题型表单渲染、提交 payload 正确
  - 编辑模式：预填、题型禁用、两次请求编排
  - 题型切换 UX：选项/答案的处理
  - 校验：必填、选项数量、多选答案数量
- `pages/QuestionBank/components/__tests__/ImportResultModal.test.tsx`：统计展示、失败明细表格

### 回滚标准

- 恢复 `src/pages/QuestionBank/index.tsx` 为原始占位符内容（已备份在 git history）
- 删除 `src/services/questionBank.ts`
- 删除 `src/pages/QuestionBank/components/` 目录
- 删除所有新增测试文件

无需后端回滚、无需路由回滚、无需依赖回滚。

## 已知限制与未来优化

- **题型不可改**：编辑模式题型字段禁用。如需换题型，需删除重建。后续可考虑支持题型迁移（带答案转换规则）
- **DELETE 接口未暴露**：前端只走 batch-deactivate。如需区别"停用"和"软删除"，再单独暴露 DELETE
- **导入失败重试**：当前需用户手动从原 Excel 中筛出失败行修正。后续可生成"失败行 Excel"供直接修正
- **answer 字段类型**：String + JSON.stringify 模式较脆（后端如未正确序列化会前端 JSON.parse 失败）。后续后端可拆为结构化字段（如 `String | Boolean | List<String>` 联合类型），但需评估对游戏层 REQ-10/12/13 的影响
- **列表接口冗余字段**：`GET /api/admin/questions`（列表）返回完整 `QuestionResponse`（含 options/answer/explanation），前端列表页只消费元数据字段（id/type/difficulty/categoryIds/status/updatedAt），每页 20 条多传 ~10KB 未消费数据。当前题库量小可接受。后续后端可拆分 `QuestionListItemResponse`（仅列字段）优化传输，属于 REQ-09 后端迭代，不在本期范围
