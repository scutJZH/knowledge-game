# REQ-08 知识点分类 — 用户端查询 API

## 产品定位

用户端查询知识分类树，用于前端渲染分类导航菜单。只读接口，仅返回 ACTIVE 状态的分类。

## 用户故事

**作为** 普通用户
**我想要** 查看知识分类树
**以便于** 按分类浏览知识条目（后续 REQ-29/REQ-98 前端页面调用）

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/knowledge-categories/tree` | 查询完整 ACTIVE 分类树 |

### 鉴权

需 JWT 鉴权（`/api/**` 前缀自动拦截），无需 ADMIN 角色，未登录返回 401。

### 响应结构

与 admin 模块 `KnowledgeCategoryResponse` 保持一致的扁平字段风格（FileRef 拆为 xxxFileId / xxxUrl 双字段）：

```json
[
  {
    "id": 1,
    "parentId": null,
    "name": "编程",
    "description": "编程相关知识分类",
    "iconFileId": 10,
    "iconUrl": "https://example.com/icons/code.png",
    "color": "#FF5500",
    "coverImageFileId": 20,
    "coverImageUrl": "https://example.com/covers/code.jpg",
    "sortOrder": 0,
    "status": "ACTIVE",
    "createdAt": 1718700000000,
    "updatedAt": 1718700000000,
    "children": [
      {
        "id": 2,
        "parentId": 1,
        "name": "Java",
        "...": "...",
        "children": []
      }
    ]
  }
]
```

### 业务规则

- 仅返回 `status = ACTIVE` 的分类。同级的子分类靠父级引用建树，父级 ACTIVE 则其所有子孙也必然 ACTIVE（管理端 `validateDelete` / REQ-89 未来 `updateStatus` 均需保证此约束，见下方「向前兼容」节）
- 按 `parentId` 构建嵌套树结构，同级按 `sortOrder` 升序排列，递归应用到所有层级
- 不需要分页、关键词搜索、父级筛选
- 时间字段返回毫秒时间戳（Long），遵循项目 API 规范
- `status` 字段固定为 `"ACTIVE"`（已过滤 INACTIVE），保留便于前端统一渲染，调用方可忽略

### 性能边界

当前 `KnowledgeCategoryRepositoryPort.findAll()` 返回全量分类（含 INACTIVE），ACTIVE 过滤在 AppService 内存中完成。分类总量预计 < 500，内存过滤可接受。若未来超过此阈值，可新增 `findByStatus(ACTIVE)` 端口方法。

### 向前兼容：REQ-89（分类状态管理）

REQ-89 将为管理端 update 接口增加 status 参数，允许将 INACTIVE 分类重新启用为 ACTIVE，或直接停用 ACTIVE 分类。实现 REQ-89 时若允许 update 直接改 status，必须在 `KnowledgeCategoryDomainService` 中增加校验：**将 ACTIVE 分类改为 INACTIVE 前，检查是否存在 ACTIVE 子分类**（与 `validateDelete` 的子分类阻断逻辑相同），确保本接口的"无需祖先校验"前提不被破坏。REQ-94 已扩展 `validateDelete` 覆盖此校验。

## Impact Analysis

### 修改/新增文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/.../api/controller/KnowledgeCategoryController.java` | 新增 | `GET /api/knowledge-categories/tree` |
| `app/.../api/dto/response/KnowledgeCategoryTreeResponse.java` | 新增 | 树节点响应 DTO（扁平字段 iconFileId/iconUrl/coverImageFileId/coverImageUrl + children） |
| `app/.../api/assembler/KnowledgeCategoryAssembler.java` | 新增 | MapStruct，领域实体 → DTO |
| `app/.../application/service/KnowledgeCategoryAppService.java` | 新增 | 查全量 → 过滤 ACTIVE → 建树 → 递归排序 |

### 依赖关系

- app 模块已依赖 core 模块，直接使用 `KnowledgeCategoryRepositoryPort`、`KnowledgeCategory` 等领域类型
- 无需修改 core 模块（`findAll()` 方法已存在）
- 无需修改 admin 模块
- 无需新增数据库表或变更

### 不受影响的现有功能

- 管理端分类 CRUD（REQ-07）不受影响
- 用户端现有 API（User/File）不受影响
- 分类管理页改名（REQ-96）不受影响 —— 仅改前端展示层

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| AppService 单元测试 | mock RepositoryPort，覆盖：(a) 单层多节点 sortOrder 排序 (b) 2 层父子嵌套 (c) 空列表返回 `[]` (d) 全 INACTIVE 返回 `[]` (e) 混合 ACTIVE/INACTIVE 只返回 ACTIVE |
| Controller `@WebMvcTest` | 验证 200 响应结构与 JSON 示例一致、401 未鉴权 |

### 手动验证

1. 管理端创建 3 个 ACTIVE 分类 + 1 个 INACTIVE 分类，调用 `GET /api/knowledge-categories/tree`，验证只返回 3 个 ACTIVE 的
2. 创建父子层级（父→子），验证嵌套结构正确、children 非 null（空时为空数组）
3. 验证同级按 sortOrder 排序，且排序递归到子级

### 回滚标准

- 接口不影响管理端功能，回滚只需删除 app 模块新增的 4 个文件
