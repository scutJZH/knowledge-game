# REQ-45 管理后台 — 卡牌管理页

## 产品定位

在管理后台（Ant Design Pro）中实现卡牌模板的完整 CRUD 管理页面，对接 REQ-17 后端 API。卡牌模板是盲盒抽卡池的核心实体，每个模板可配置 1~5 星级图片。

## 前置依赖

- **REQ-40**：管理后台脚手架（已完成）
- **REQ-41**：管理后台登录鉴权（已完成）
- **REQ-17**：卡牌模板 CRUD API（已完成）
- **REQ-16**：IP 系列 CRUD API（已完成，作为卡牌模板的下拉数据源）
- **REQ-83**：文件服务模块（已完成）
- **REQ-87**：Admin 对接文件服务（已完成，提供 `GET /api/admin/upload-credential` 凭证接口）

## 用户故事

**作为** 系统管理员
**我想要** 在管理后台的「内容管理 > 卡牌管理」页面查看、创建、编辑、删除卡牌模板（含 1~5 星级图片配置）
**以便于** 维护盲盒抽卡池的卡牌内容与各星级展示图片

## 功能需求

### 1. 卡牌列表（ProTable）

**路径**：`/content/card-template`

| 功能 | 说明 |
|------|------|
| 表格列 | ID、编码(code)、名称(name)、IP 系列(ipSeriesName)、稀有度(rarity Tag)、状态(status Tag)、创建时间(createdAt)、更新时间(updatedAt)、操作 |
| 分页 | 默认每页 20 条，后端分页 |
| 搜索 | 名称模糊搜索（输入框） |
| 筛选 | IP 系列（下拉）、稀有度（下拉：N/R/SR/SSR/SP）、状态（下拉：全部/ACTIVE/INACTIVE，**默认 ACTIVE**） |
| 操作列 | 编辑按钮、删除按钮 |

列表不返回 starImages（需进入编辑弹窗查看）。

### 2. 创建卡牌模板

| 项目 | 说明 |
|------|------|
| 触发 | 表格上方「新建」按钮 |
| 表单 | ModalForm 弹窗，字段见下方 |
| IP 系列(ipSeriesId) | 必填，下拉选择（数据源来自 `listIpSeries`，仅展示 ACTIVE 状态，size=1000） |
| 编码(code) | 必填，2~50 字符，输入框 |
| 名称(name) | 必填，1~50 字符，输入框 |
| 稀有度(rarity) | 必填，下拉选择 N/R/SR/SSR/SP |
| 描述(description) | 可选，最大 500 字符，文本域 |
| 状态(status) | 必填，下拉选择 ACTIVE/INACTIVE，默认 ACTIVE |
| 星级图片(starImages) | 可选，1~5 星级各一个图片上传槽位（凭证式上传）。可上传任意 1~5 张，未上传的星级留空 |
| 提交 | POST `/api/admin/card-templates`，请求体包含基础字段 + starImages 数组；成功后刷新列表并关闭弹窗 |

### 3. 编辑卡牌模板

| 项目 | 说明 |
|------|------|
| 触发 | 操作列「编辑」按钮 |
| 步骤 0 | 点击编辑 → 调用 `getCardTemplateById(id)` 拉取详情（含 starImages，列表 API 不返回）→ 打开预填弹窗 + 记录初始 starImages 快照 |
| 表单 | 同创建弹窗，预填当前值（基础字段 + 已有星级图片） |
| 提交流程 | 两步：① PUT `/api/admin/card-templates/{id}` 更新基础字段；② 对每个被修改或新增的星级图片，调用 POST `/api/admin/card-templates/{id}/star-images` 单独更新（idempotent，已存在则替换） |
| 成功 | 刷新列表并关闭弹窗 |

**星级图片变更追踪**：编辑弹窗打开时记录初始 starImages 快照，提交时比对当前值与快照，仅对发生变化的星级（新增或 URL 变化）发起 addOrUpdateStarImage 请求。未变更的星级跳过，减少不必要请求。

**错误聚合策略**：编辑提交涉及 N 个串行请求（1×PUT + M×addOrUpdateStarImage）。任一请求失败时，UI 策略为：
- 弹窗保持打开、显示错误、不自动关闭
- 收集所有失败请求的错误信息，用单个 `message.error` 统一展示（避免多个 toast 刷屏）
- 用户修正问题后可直接重新点击「确定」重试，PUT 与 addOrUpdateStarImage 均 idempotent，重试安全（已成功的请求重复调用不会产生副作用）

### 4. 删除卡牌模板

| 项目 | 说明 |
|------|------|
| 触发 | 操作列「删除」按钮 |
| 确认 | Popconfirm 二次确认「确定删除该卡牌模板吗？」 |
| 调用 | DELETE `/api/admin/card-templates/{id}` |
| 成功 | 刷新列表 |
| 失败 | 显示后端错误信息（如「已有用户收集，不允许删除」） |

## 页面交互细节

- 创建/编辑弹窗提交时按钮显示 loading，防止重复提交
- 编辑提交涉及多个请求（PUT + N×star-images），任一失败则整体回滚 UI 状态（弹窗不关闭、显示错误），错误聚合策略见编辑章节
- 删除操作成功后显示 `message.success` 提示
- 列表 status 筛选默认 ACTIVE，删除（软删除 → INACTIVE）后记录从当前视图消失；切换为「全部」可查看 INACTIVE 记录
- 状态列用 Tag：ACTIVE 绿色、INACTIVE 灰色
- 稀有度列用 Tag 颜色区分：N 灰、R 蓝、SR 紫、SSR 金、SP 红
- 星级图片不支持删除（后端无删除接口），仅支持新增/替换
- 星级范围固定 1~5 星（与后端 `@Max(5)` 校验一致；card-system-data-model.md 原描述"无上限"已本次同步更新为"1~5 星"）

## 星级图片上传流程（3 步凭证式）

复用 IP 系列的凭证式上传（REQ-87）+ REQ-83 文件服务：

1. **获取凭证**：前端调用 `GET /api/admin/upload-credential?bizType=CARD_TEMPLATE&count=1`（需 JWT 鉴权），返回 `{ token, uploadUrl }`
2. **直传文件**：前端携带凭证 `POST uploadUrl`，请求头 `X-Upload-Token: {token}` + `X-User-Id: {userId}`，body `multipart/form-data` 字段名 `file`
3. **入库 URL**：上传成功返回 `{ fileId, url }`，前端拼接完整 URL（`${uploadUrl.split('/api/')[0]}${url}`）存入表单状态，提交时一并发送

星级图片上传组件交互：
- 每个星级（★1~★5）一个独立上传槽位，grid 布局（每行 2~3 个）
- 点击或拖拽触发上传
- 上传中显示进度，成功显示缩略图预览
- 已有图片显示「替换」按钮（再次上传即替换 URL）
- 文件约束：仅允许 `image/jpeg`、`image/png`、`image/gif`、`image/webp`，单文件最大 10MB

## 技术实现

### 新增文件

| 文件 | 说明 |
|------|------|
| `src/services/cardTemplate.ts` | 新增 | API 服务层：类型定义 + 6 个请求函数（list/create/get/update/addOrUpdateStarImage/delete） |
| `src/pages/CardTemplate/index.tsx` | 重写 | 占位符替换为完整 CRUD 页面 |
| `src/pages/CardTemplate/components/StarImageUpload.tsx` | 新增 | 星级图片上传组件（凭证式上传，props: starLevel + value + onChange） |

### 复用现有文件

| 文件 | 复用内容 |
|------|---------|
| `src/services/fileUpload.ts` | getUploadCredential + uploadFile 函数（bizType 传 `CARD_TEMPLATE`） |
| `src/services/ipSeries.ts` | listIpSeries 函数（填充 IP 系列下拉） |
| `src/services/typing.ts` | PageResult\<T\> 类型 |

### 类型定义

```typescript
// 卡牌模板响应（详情，含星级图片）
interface CardTemplateResponse {
  id: number;
  ipSeriesId: number;
  ipSeriesName: string;
  code: string;
  name: string;
  rarity: 'N' | 'R' | 'SR' | 'SSR' | 'SP';
  description: string;
  status: 'ACTIVE' | 'INACTIVE';
  starImages: StarImageResponse[];
  createdAt: string;
  updatedAt: string;
}

// 卡牌模板响应（列表，不含星级图片）
// 注意：description 字段后端返回但列表页不展示，保留供未来扩展（如鼠标 hover 提示）
interface CardTemplateListResponse {
  id: number;
  ipSeriesId: number;
  ipSeriesName: string;
  code: string;
  name: string;
  rarity: 'N' | 'R' | 'SR' | 'SSR' | 'SP';
  description: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}

// 星级图片
interface StarImageResponse {
  starLevel: number;
  imageUrl: string;
}

// 创建请求
interface CreateCardTemplateRequest {
  ipSeriesId: number;
  code: string;
  name: string;
  rarity: 'N' | 'R' | 'SR' | 'SSR' | 'SP';
  description?: string;
  status: 'ACTIVE' | 'INACTIVE';
  starImages?: { starLevel: number; imageUrl: string }[];
}

// 更新请求（基础字段，不含星级图片）
interface UpdateCardTemplateRequest {
  code?: string;
  name?: string;
  rarity?: 'N' | 'R' | 'SR' | 'SSR' | 'SP';
  description?: string;
  status?: 'ACTIVE' | 'INACTIVE';
}

// 添加/替换星级图片请求
interface AddStarImageRequest {
  starLevel: number;
  imageUrl: string;
}

// 分页查询参数
interface CardTemplateQuery {
  name?: string;
  ipSeriesId?: number;
  rarity?: string;
  status?: string;
  page?: number;
  size?: number;
}
```

### API 函数

```typescript
listCardTemplates(params: CardTemplateQuery): Promise<PageResult<CardTemplateListResponse>>
getCardTemplateById(id: number): Promise<CardTemplateResponse>
createCardTemplate(data: CreateCardTemplateRequest): Promise<CardTemplateResponse>
updateCardTemplate(id: number, data: UpdateCardTemplateRequest): Promise<CardTemplateResponse>
addOrUpdateStarImage(id: number, data: AddStarImageRequest): Promise<CardTemplateResponse>
deleteCardTemplate(id: number): Promise<void>
```

### 页面组件结构

使用 Ant Design ProComponents：
- `ProTable` 列表（内置分页、搜索、筛选）
- `ModalForm` 创建/编辑弹窗（含星级图片区）
- `Popconfirm` 删除确认
- `Tag` 稀有度与状态展示
- 自定义 `StarImageUpload` 组件处理单星级上传

**星级图片区布局**：在 ModalForm 内通过 `ProForm.Group` 包裹 5 个 StarImageUpload 组件（grid 自动换行），每个组件接收 `starLevel` 作为标识，value 为 imageUrl 字符串。

## Impact Analysis

### 修改的文件

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `admin/.../config/FilePathMapping.java` | 修改 | 添加 `CARD_TEMPLATE → card-template` 映射（**当前分支 working tree 已预添加**：IP_SERIES 来自 REQ-44 未 commit 改动，CARD_TEMPLATE 来自本次 ISSUE-1 已执行） |
| `admin/.../config/FilePathMappingTest.java` | 修改 | 新增 `CARD_TEMPLATE → card-template` 正向断言（**已执行**） |
| `src/services/cardTemplate.ts` | 新增 | API 服务层 |
| `src/pages/CardTemplate/index.tsx` | 重写 | 占位符替换为完整 CRUD 页面 |
| `src/pages/CardTemplate/components/StarImageUpload.tsx` | 新增 | 星级图片上传组件 |

### 配置变更

无。路由已在 `config/routes.ts` 中配置 `/content/card-template`。

### 受影响的现有功能

| 功能 | 影响 |
|------|------|
| admin FilePathMapping | `CARD_TEMPLATE → card-template` 映射已在 working tree 添加（与 REQ-44 的 IP_SERIES 改动共存于同一文件，待一并 commit） |
| FilePathMappingTest | 现有断言「card-star-image 返回 null」保留（仍是未注册 bizType）；新增「CARD_TEMPLATE → card-template」正向断言 |
| `docs/card-system-data-model.md` | 第 19 行「卡牌星级 \| 无上限」需同步更新为「1~5 星」；第 141 行 card_star_image.star_level 注释「无上限」同步更新。原描述与 REQ-17 后端 `@Max(5)` 实现矛盾，本次确认以 5 星为准 |

## Verification Plan

### 单元测试（后端）

`FilePathMappingTest` 新增：
- `CARD_TEMPLATE → card-template` 正向断言

### 单元测试（前端）

参考 `src/services/__tests__/ipSeries.test.ts` 和 `src/pages/IpSeries/__tests__/index.test.tsx` 模式：

| 测试文件 | 覆盖范围 |
|---------|---------|
| `src/services/__tests__/cardTemplate.test.ts` | mock request，验证 6 个 API 函数（listCardTemplates / getCardTemplateById / createCardTemplate / updateCardTemplate / addOrUpdateStarImage / deleteCardTemplate）的 method / url / params / data 正确性，覆盖正常路径 + 参数边界 |
| `src/pages/CardTemplate/__tests__/StarImageUpload.test.tsx` | 组件交互：上传成功回调完整 URL、上传失败显示错误、文件类型/大小校验、删除回调 undefined |
| `src/pages/CardTemplate/__tests__/index.test.tsx` | 页面关键逻辑：列表渲染、创建表单提交、编辑流程（getCardTemplateById → 预填 → PUT + addOrUpdateStarImage）、星级图片变更快照比对 |

### 手动验证步骤

1. 启动后端 `knowledge-game-admin`（端口 8081）
2. 启动前端 `npm run dev`
3. 登录管理后台
4. 导航到「内容管理 > 卡牌管理」
5. **创建**：点击新建 → 选 IP 系列 → 填编码/名称/稀有度 → 上传 1~3 张星级图片 → 提交 → 确认列表刷新
6. **查询**：按名称搜索、按 IP 系列筛选、按稀有度筛选、按状态筛选，分别确认生效；默认 status=ACTIVE，新建的 ACTIVE 记录可见
7. **编辑**：点击编辑 → 修改名称 → 替换一张星级图片、新增一张星级图片 → 提交 → 重新打开编辑确认变更已保存
8. **删除**：点击删除 → 取消（确认不执行）→ 再次点击 → 确认 → **确认记录从列表消失**（默认 status=ACTIVE，软删除后变 INACTIVE 被过滤）；切换 status 筛选为「全部」可看到该记录显示为灰色 INACTIVE Tag
9. **边界**：创建时 code 重复 → 确认显示错误提示；上传超过 10MB 的图片 → 确认前端拦截；上传非图片格式 → 确认前端拦截
10. **（可选）** 编辑提交时模拟某个 addOrUpdateStarImage 失败（DevTools block 请求或断网）→ 确认错误聚合展示且弹窗不关闭。此场景已由前端单元测试覆盖（见上方「单元测试（前端）」），手动难以稳定复现，可跳过

### 回滚标准

删除 `src/services/cardTemplate.ts`、`src/pages/CardTemplate/components/StarImageUpload.tsx`，恢复 `src/pages/CardTemplate/index.tsx` 为占位符内容，回滚 FilePathMapping 的 CARD_TEMPLATE 映射即可。
