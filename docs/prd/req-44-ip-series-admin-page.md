# REQ-44 管理后台 — IP 系列管理页

## 产品定位

在管理后台（Ant Design Pro）中实现 IP 系列的完整 CRUD 管理页面，对接 REQ-16 后端 API。

## 前置依赖

- **REQ-40**：管理后台脚手架（已完成）
- **REQ-41**：管理后台登录鉴权（已完成）
- **REQ-16**：IP 系列 CRUD API（已完成）
- **REQ-83**：文件服务模块（已完成）
- **REQ-87**：Admin 对接文件服务（已完成，提供 `GET /api/admin/upload-credential` 凭证接口）

## 用户故事

**作为** 系统管理员
**我想要** 在管理后台的「内容管理 > IP 系列管理」页面查看、创建、编辑、删除 IP 系列
**以便于** 管理卡牌所属的 IP 分组，为后续盲盒抽卡池配置提供基础数据

## 功能需求

### 1. IP 系列列表（ProTable）

**路径**：`/content/ip-series`

| 功能 | 说明 |
|------|------|
| 表格列 | ID、编码(code)、名称(name)、描述(description)、封面图(coverImageUrl 缩略图)、状态(status Tag)、创建时间(createdAt)、更新时间(updatedAt)、操作 |
| 分页 | 默认每页 20 条，后端分页 |
| 搜索 | 名称模糊搜索（输入框） |
| 筛选 | 状态筛选（下拉：全部 / ACTIVE / INACTIVE） |
| 操作列 | 编辑按钮、删除按钮 |

### 2. 创建 IP 系列

| 项目 | 说明 |
|------|------|
| 触发 | 表格上方「新建」按钮 |
| 表单 | Modal 弹窗，字段如下 |
| 编码(code) | 必填，2~30 字符，输入框 |
| 名称(name) | 必填，2~50 字符，输入框 |
| 描述(description) | 可选，最大 500 字符，文本域 |
| 封面图(coverImageUrl) | 可选，图片上传组件（3 步凭证式上传，见下方上传流程） |
| 状态(status) | 必填，下拉选择 ACTIVE / INACTIVE，默认 ACTIVE |
| 提交 | POST `/api/admin/ip-series`，成功后刷新列表并关闭弹窗 |

### 3. 编辑 IP 系列

| 项目 | 说明 |
|------|------|
| 触发 | 操作列「编辑」按钮 |
| 表单 | 同创建弹窗，预填当前值 |
| 提交 | PUT `/api/admin/ip-series/{id}`，成功后刷新列表并关闭弹窗 |

### 4. 删除 IP 系列

| 项目 | 说明 |
|------|------|
| 触发 | 操作列「删除」按钮 |
| 确认 | Popconfirm 二次确认「确定删除该 IP 系列吗？」 |
| 调用 | DELETE `/api/admin/ip-series/{id}` |
| 成功 | 刷新列表 |
| 失败 | 显示后端错误信息（如「该 IP 下已有卡牌，不允许删除」） |

## 页面交互细节

- 创建/编辑弹窗提交时按钮显示 loading，防止重复提交
- 删除操作成功后显示 `message.success` 提示
- 表格不支持列排序（后端 list API 未提供 sort 参数）
- 状态列用 Tag 组件展示：ACTIVE 绿色、INACTIVE 灰色

## 封面图上传流程（3 步凭证式）

基于 REQ-87 提供的 admin 凭证接口 + REQ-83 文件服务：

1. **获取凭证**：前端调用 `GET /api/admin/upload-credential?bizType=IP_SERIES&count=1`（需 JWT 鉴权），返回 `{ token, uploadUrl }`
2. **直传文件**：前端携带凭证 `POST uploadUrl`，请求头 `X-Upload-Token: {token}` + `X-User-Id: {userId}`，body `multipart/form-data` 字段名 `file`
3. **入库 URL**：上传成功返回 `{ fileId, url }`，其中 `url` 为相对路径（如 `/static/ip-series/20260612/uuid.png`）。前端拼接 file 服务地址得到完整 URL（如 `http://localhost:8083/static/ip-series/20260612/uuid.png`），将完整 URL 作为 `coverImageUrl` 提交到创建/编辑 IP 系列接口。file 服务地址从凭证响应的 `uploadUrl` 中提取（`${uploadUrl.split('/api/')[0]}`）

上传组件交互：
- 点击上传区域或拖拽图片触发上传
- 上传中显示进度，上传成功显示缩略图预览
- 支持删除已上传图片（仅前端清除，不调 file 删除接口）
- 文件约束：仅允许 `image/jpeg`、`image/png`、`image/gif`、`image/webp`，单文件最大 10MB

鉴权说明：
- 凭证接口（`GET /api/admin/upload-credential`）需 JWT 鉴权，由全局 request 拦截器自动注入
- 文件上传接口（`POST uploadUrl`）使用凭证 token 鉴权（`X-Upload-Token` 请求头），不走 JWT，需手动构造请求

## 技术实现

### 新增文件

| 文件 | 说明 |
|------|------|
| `src/services/typing.ts` | 新增 | 共享分页类型 PageResult\<T\>，供所有管理页复用 |
| `src/services/fileUpload.ts` | 新增 | 文件上传服务：获取凭证 + 上传函数，供所有管理页复用 |
| `src/services/ipSeries.ts` | 新增 | API 服务层：类型定义 + 4 个请求函数 |
| `src/pages/IpSeries/index.tsx` | 重写 | 占位符替换为完整 CRUD 页面 |

### 类型定义

```typescript
// 共享分页类型（src/services/typing.ts，所有管理页复用）
interface PageResult<T> {
  content: T[];
  totalElements: number;
  pageNumber: number;
  pageSize: number;
  totalPages: number;
}

// IP 系列响应类型
interface IpSeriesResponse {
  id: number;
  code: string;
  name: string;
  description: string;
  coverImageUrl: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}

// 创建请求
interface CreateIpSeriesRequest {
  code: string;
  name: string;
  description?: string;
  coverImageUrl?: string;
  status: 'ACTIVE' | 'INACTIVE';
}

// 更新请求（类型定义上字段可选，但编辑表单预填全部字段，提交时发送完整对象）
interface UpdateIpSeriesRequest {
  code?: string;
  name?: string;
  description?: string;
  coverImageUrl?: string;
  status?: 'ACTIVE' | 'INACTIVE';
}

// 分页查询参数
interface IpSeriesQuery {
  name?: string;
  status?: string;
  page?: number;
  size?: number;
}
```

### API 函数

```typescript
createIpSeries(data: CreateIpSeriesRequest): Promise<IpSeriesResponse>
listIpSeries(params: IpSeriesQuery): Promise<PageResult<IpSeriesResponse>>
updateIpSeries(id: number, data: UpdateIpSeriesRequest): Promise<IpSeriesResponse>
deleteIpSeries(id: number): Promise<void>
```

### 页面组件结构

使用 Ant Design ProComponents：
- `ProTable` 列表（内置分页、搜索、筛选）
- `ModalForm` 创建/编辑弹窗
- `Popconfirm` 删除确认
- `Tag` 状态展示

## Impact Analysis

### 修改的文件

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `admin/.../config/FilePathMapping.java` | 修改 | 添加 `IP_SERIES → ip-series` 映射 |
| `src/services/typing.ts` | 新增 | 共享分页类型 PageResult\<T\> |
| `src/services/fileUpload.ts` | 新增 | 文件上传服务（凭证 + 上传） |
| `src/pages/IpSeries/index.tsx` | 重写 | 占位符替换为完整 CRUD 页面 |
| `src/services/ipSeries.ts` | 新增 | API 服务层 |

### 配置变更

无。路由已在 `config/routes.ts` 中配置 `/content/ip-series`。

### 受影响的现有功能

| 功能 | 影响 |
|------|------|
| admin FilePathMapping | 需添加 `IP_SERIES → ip-series` 映射。当前 map 为空，不添加则凭证接口返回 400 |

## Verification Plan

### 手动验证步骤

1. 启动后端 `knowledge-game-admin`（端口 8081）
2. 启动前端 `npm run dev`
3. 登录管理后台
4. 导航到「内容管理 > IP 系列管理」
5. **创建**：点击新建 → 填写所有字段 → 提交 → 确认列表刷新且新记录出现
6. **查询**：在搜索框输入名称 → 确认筛选生效；切换状态筛选 → 确认筛选生效
7. **编辑**：点击编辑 → 修改名称和描述 → 提交 → 确认列表中数据更新
8. **删除**：点击删除 → 取消（确认不执行）→ 再次点击删除 → 确认 → 确认记录消失
9. **边界**：创建时 code 重复 → 确认显示错误提示；删除有关联卡牌的 IP → 确认显示错误提示

### 回滚标准

删除 `src/services/ipSeries.ts`，恢复 `src/pages/IpSeries/index.tsx` 为占位符内容即可。
