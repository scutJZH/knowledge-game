# REQ-114：知识条目列表性能优化 — 列表与详情分离

| 字段 | 值 |
|------|---|
| 需求编号 | REQ-114 |
| 状态 | designed |
| 优先级 | P2 |
| 前置依赖 | REQ-97 ✅（知识条目 CRUD） |
| 影响模块 | `knowledge-game-core`、`knowledge-game-admin`、`frontend/admin` |

---

## 1. 背景

`GET /api/admin/knowledge-items` 列表接口当前返回完整 `KnowledgeItemResponse`，包含 `content`（MEDIUMTEXT Markdown 正文）和 `contentHtml`（MEDIUMTEXT 渲染 HTML）。以单条知识条目平均 content 10KB + contentHtml 15KB 估算，每页 20 条时响应体约 500KB（不含其他字段），影响列表加载性能。

前端 ProTable 列定义**不使用** `content`/`contentHtml` 字段（仅展示 ID、标题、分类、标签、排序号、状态、时间），预览和编辑均通过独立的 `GET /{id}` 详情请求获取正文 — 列表响应中的正文数据从未被消费。

## 2. 目标

1. 列表接口不再返回 `content`/`contentHtml`，拆分为轻量 `KnowledgeItemListResponse`
2. DB 查询层同步裁剪 — 列表查询 SQL 不 SELECT `content`/`contentHtml` 列
3. 详情接口（`GET /{id}`）保持不变，继续返回完整 `KnowledgeItemResponse`
4. 创建/更新接口返回值不变（保持返回完整 `KnowledgeItemResponse`，单条数据无需优化）
5. 前端列表页 TS 类型对齐新 ListResponse

## 3. 非目标

- 创建/更新接口返回值裁剪（单条数据响应体不大，且创建后可能需要详情）
- 用户端 API（REQ-98 尚未实现，届时可直接采用轻量列表设计）
- 其他管理端列表接口（逐个按需改造）

## 4. API 协议

### 4.1 列表接口（变更）

```
GET /api/admin/knowledge-items?keyword=&categoryId=&tag=&status=&sort=&order=&page=0&size=20
```

**响应类型变更**：`Result<PageResult<KnowledgeItemResponse>>` → `Result<PageResult<KnowledgeItemListResponse>>`

**请求参数**：无变更（keyword/categoryId/tag/status/sort/order/page/size 全部保持）

### 4.2 KnowledgeItemListResponse 字段

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `id` | `Long` | 领域实体 | |
| `title` | `String` | 领域实体 | |
| `coverImageFileId` | `Long` | `FileRef.fileId()` | 返回 null 如果未设置封面 |
| `coverImageUrl` | `String` | `FileRef.url()` | 返回 null 如果未设置封面 |
| `tags` | `List<String>` | 领域实体 | |
| `categoryIds` | `List<Long>` | 关联表查询 | 仅含 ACTIVE 分类的 ID |
| `sortOrder` | `int` | 领域实体 | |
| `status` | `String` | 领域实体 name() | `ACTIVE` / `INACTIVE` |
| `createdAt` | `Long` | epoch 毫秒 | |
| `updatedAt` | `Long` | epoch 毫秒 | |

**不含字段**：`content`、`contentHtml`（仅这两个字段被移除，其余与 `KnowledgeItemResponse` 一致）

### 4.3 详情接口（无变更）

```
GET /api/admin/knowledge-items/{id}  →  Result<KnowledgeItemResponse>
```

保持返回完整 `KnowledgeItemResponse`（含 content/contentHtml）。

### 4.4 创建/更新接口（无变更）

```
POST /api/admin/knowledge-items  →  Result<KnowledgeItemResponse>
PUT  /api/admin/knowledge-items/{id}  →  Result<KnowledgeItemResponse>
```

保持返回完整 `KnowledgeItemResponse`。

## 5. 核心设计

### 5.1 新增领域 VO：KnowledgeItemSummary

**文件**：`core/domain/model/vo/KnowledgeItemSummary.java`（新增）

不含 `content`/`contentHtml` 的知识条目轻量投影，用于列表查询。

**设计决策**：选择 `CriteriaQuery<Tuple>` + `KnowledgeItemSummary` VO 方案，而非 JPA Interface Projection 或 JPQL 构造器表达式。理由：① Tuple 转换在 Adapter 层通过 Converter 内联完成，与现有 `PO ↔ Domain` 转换模式一致，不引入新的转换路径；② JPQL `NEW com.xxx.Summary(...)` 无法处理 `tags` JSON 列（需 Converter 的 `parseTags()` 手动解析）；③ JPA Interface Projection 返回代理对象无法直接传给 Domain 层 Assembler。

```java
@Getter
public class KnowledgeItemSummary {
    private Long id;
    private String title;
    private FileRef coverImage;
    private List<String> tags;  // null 表示未设置标签，与 KnowledgeItem 语义一致
    private int sortOrder;
    private KnowledgeItemStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeItemSummary reconstruct(Long id, String title,
            FileRef coverImage, List<String> tags, int sortOrder,
            KnowledgeItemStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        // ...
    }
}
```

`tags` 字段沿用 `Converter.parseTags()` 的返回值：null/blank JSON → null（未设置标签），解析异常 → `Collections.emptyList()`。前端使用 `record.tags || []` 兜底。

### 5.2 新增管理员 DTO：KnowledgeItemListResponse

**文件**：`admin/api/dto/response/KnowledgeItemListResponse.java`（新增）

字段表见 §4.2，Builder 模式，与 `KnowledgeItemResponse` 同风格。

### 5.3 Port 扩展

**文件**：`core/domain/port/outbound/KnowledgeItemRepository.java`

新增方法签名：

```java
PageResult<KnowledgeItemSummary> findByConditionsSummary(
    String keyword, Long categoryId, String tag,
    KnowledgeItemStatus status, SortField sortField,
    int pageNumber, int pageSize);
```

`findByConditions` 保留不动（详情/更新/批量操作仍需要完整 `KnowledgeItem`）。

### 5.4 Adapter 查询优化

**文件**：`core/infrastructure/adapter/repoadapter/KnowledgeItemRepositoryAdapter.java`

**WHERE 条件复用**：将现有 `findByConditions` 中的 `Specification<KnowledgeItemPO>` lambda 提取为 `private Specification<KnowledgeItemPO> buildWhereSpec(keyword, categoryId, tag, status)` 方法，`findByConditions` 和 `findByConditionsSummary` 共享。

**summary 查询使用 `CriteriaQuery<Tuple>`** + `multiselect` 只选 9 个非正文列：

```
id, title, coverImageFileId, coverImageUrl, tags, sortOrder, status, createdAt, updatedAt
```

不 SELECT `content`、`contentHtml`（两个 MEDIUMTEXT 列）。

**排序逻辑**：与 `findByConditions` 完全一致（默认 `sortOrder ASC, createdAt DESC`，categoryName 走独立子查询路径，其余走 `SortFields.toSpringSort`）。

**分页 COUNT**：单独 COUNT 查询（无 JOIN），与现有模式一致。

**Tuple → KnowledgeItemSummary**：在 Adapter 内联转换（调用 Converter 的 `toSummaryDomain` default 方法）。

### 5.5 Converter 扩展

**文件**：`core/infrastructure/db/converter/KnowledgeItemConverter.java`

新增 `toSummaryDomain(JPA Tuple)` default 方法：

```java
default KnowledgeItemSummary toSummaryDomain(Tuple tuple) {
    // 从 Tuple 提取各列 → KnowledgeItemSummary.reconstruct(...)
}
```

tags JSON 列解析逻辑复用现有 `parseTags()` private 方法。

### 5.6 AppService 变更

**文件**：`admin/application/service/KnowledgeItemAppService.java`

`list()` 方法改为：
1. 调用 `itemRepository.findByConditionsSummary(...)` 获取 `PageResult<KnowledgeItemSummary>`
2. categoryIds 批量查询逻辑不变（复用现有 `findActiveCategoryIdsByItemIds`）
3. 使用 `KnowledgeItemAssembler.INSTANCE.toListResponse(summary, categoryIds)` 转换

### 5.7 Assembler 扩展

**文件**：`admin/api/assembler/KnowledgeItemAssembler.java`

新增方法：

```java
@Mapping(target = "coverImageFileId", expression = "...")
@Mapping(target = "coverImageUrl", expression = "...")
@Mapping(target = "status", expression = "java(summary.getStatus().name())")
@Mapping(target = "categoryIds", ignore = true)
@Mapping(target = "createdAt", expression = "java(toEpochMilli(summary.getCreatedAt()))")
@Mapping(target = "updatedAt", expression = "java(toEpochMilli(summary.getUpdatedAt()))")
KnowledgeItemListResponse toListResponse(KnowledgeItemSummary summary);

default KnowledgeItemListResponse toListResponse(KnowledgeItemSummary summary, List<Long> categoryIds) {
    // builder 追加 categoryIds
}
```

### 5.8 Controller 变更

**文件**：`admin/api/controller/KnowledgeItemController.java`

`list()` 方法返回类型：`Result<PageResult<KnowledgeItemResponse>>` → `Result<PageResult<KnowledgeItemListResponse>>`

其余方法不变。

### 5.9 前端变更

**文件**：`frontend/admin/src/services/knowledge-item.ts`

- 新增 `KnowledgeItemListResponse` 接口（不含 content/contentHtml）
- `listKnowledgeItems()` 返回类型改为 `PageResult<KnowledgeItemListResponse>`
- `KnowledgeItemResponse` 保留不动（用于 getById / create / update）

**文件**：`frontend/admin/src/pages/KnowledgeItem/index.tsx`

- ProTable 泛型 `ProTable<KnowledgeItemResponse>` → `ProTable<KnowledgeItemListResponse>`
- `dataSource` 类型相应调整
- `columns` 和 `request` 逻辑无需改动（列表列已不使用 content/contentHtml）

## 6. 鉴权

所有涉及的 API 端点均需 ADMIN 角色（JWT 鉴权），无公开端点。无变更。

## 7. 错误处理

不新增 ResultCode。现有错误码直接复用：

| 场景 | ResultCode |
|------|-----------|
| 非法排序字段 | `PARAM_ERROR`（`SortFieldSpec.validate` 抛 `BusinessException`） |
| 条目不存在（GET /{id}） | `BusinessException("知识条目不存在: " + id)` |
| 参数校验失败（POST/PUT） | `VALIDATION_ERROR`（`@Valid` 触发） |

## 8. 测试策略

### 8.1 单元测试

| 模块 | 测试内容 | 工具 |
|------|---------|------|
| core | `KnowledgeItemSummary.reconstruct()` 工厂方法 | JUnit 5 |
| core | `KnowledgeItemConverter.toSummaryDomain(Tuple)` 转换 | JUnit 5 + Mockito `mock(Tuple.class)` |
| admin | `KnowledgeItemAssembler.toListResponse()` 映射字段完整性 | JUnit 5 |
| admin | `KnowledgeItemAppService.list()` 调用 summary 路径 | JUnit 5 + Mockito |

### 8.2 集成测试

| 模块 | 测试内容 | 配置 |
|------|---------|------|
| admin | Controller `GET /` 返回 `KnowledgeItemListResponse`，验证不含 content/contentHtml | `@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)` |
| core | Adapter `findByConditionsSummary` SQL 不含 content 列 | `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")` + `@EntityScan` + `@EnableJpaRepositories` + `@Import(KnowledgeItemRepositoryAdapter.class)`，参照现有 `KnowledgeItemRepositoryAdapterIntegrationTest` |

### 8.3 手工验收

1. 管理端知识条目列表页正常加载，响应时间对比优化前明显下降
2. 列表响应 JSON 中无 `content`/`contentHtml` 字段
3. 预览按钮（眼睛图标）正常展示 contentHtml
4. 编辑按钮正常加载 content 到表单
5. 创建/更新条目后列表正常刷新

## 9. 回滚标准

- 列表接口响应中缺少 `title`/`status` 等必需字段 → 阻塞回滚
- 详情接口 `GET /{id}` 行为异常 → 阻塞回滚
- 列表查询返回空数据（但 DB 有数据）→ 阻塞回滚
- 创建/更新返回值缺少 content → 阻塞回滚

## 10. Impact Analysis

### 10.1 新增文件（2 个）

| 文件 | 模块 | 说明 |
|------|------|------|
| `core/domain/model/vo/KnowledgeItemSummary.java` | core | 领域 VO，不含 content/contentHtml |
| `admin/api/dto/response/KnowledgeItemListResponse.java` | admin | 列表响应 DTO |

### 10.2 修改文件（13 个）

| 文件 | 模块 | 变更内容 |
|------|------|---------|
| `core/.../KnowledgeItemRepository.java` | core | 新增 `findByConditionsSummary` Port 方法签名 |
| `core/.../KnowledgeItemRepositoryAdapter.java` | core | 提取共享 WHERE 逻辑 + 实现 Tuple 查询 |
| `core/.../KnowledgeItemConverter.java` | core | 新增 `toSummaryDomain(Tuple)` |
| `admin/.../KnowledgeItemAppService.java` | admin | `list()` 改用 summary 路径 |
| `admin/.../KnowledgeItemAssembler.java` | admin | 新增 `toListResponse(KnowledgeItemSummary)` |
| `admin/.../KnowledgeItemController.java` | admin | `list()` 返回类型变更 |
| `frontend/admin/src/services/knowledge-item.ts` | frontend/admin | 新增 `KnowledgeItemListResponse` TS 类型 |
| `frontend/admin/src/pages/KnowledgeItem/index.tsx` | frontend/admin | ProTable 泛型变更 |
| `admin/.../KnowledgeItemAppServiceTest.java` | admin | 新增 summary 路径单测用例 |
| `admin/.../KnowledgeItemControllerTest.java` | admin | list 返回类型变更需更新断言 |
| `core/.../KnowledgeItemRepositoryAdapterTest.java` | core | 新增 `findByConditionsSummary` 单测用例 |
| `core/.../KnowledgeItemConverterTest.java` | core | 新增 `toSummaryDomain` 单测用例 |
| `core/.../KnowledgeItemRepositoryAdapterIntegrationTest.java` | core | 新增 `findByConditionsSummary` 集成测试用例 |

### 10.3 不受影响的功能

- 创建知识条目
- 更新知识条目
- 删除知识条目（软删除）
- 详情查询（GET /{id}）
- 分类关联管理
- 批量启用/禁用/排序
- 分类名称排序（categoryName sort）
- 用户端 API（尚未实现）

### 10.4 DB 变更

无 DDL 变更。仅 SELECT 列裁剪。

## 11. 前置依赖检查

| 前置需求 | 状态 | 依赖内容 |
|---------|------|---------|
| REQ-97 | done ✅ | 知识条目 CRUD API + 管理页面已完成 |

## 12. 向前兼容检查

- **REQ-98（用户端知识库浏览页）**：尚未实现。用户端 API 设计时可直接采用轻量列表 + 详情分离模式，与本需求一致。
- **REQ-108（知识条目对接回收站）**：操作对象为 `KnowledgeItem` 完整实体（包括 content），不受列表查询优化影响。
- **REQ-111（知识条目批量导入）**：导入操作涉及 content 写入，不受列表查询优化影响。

## 13. Verification Plan

### 13.1 自动化测试

1. **Converter 转换**：mock Tuple 返回全部 9 列，验证 `toSummaryDomain` 输出字段完整
2. **Assembler 映射**：验证 `toListResponse` 输出 DTO 字段与 domain Summary 字段一一对应，categoryIds 正确追加
3. **Controller 响应验证**：Mock 列表返回，JSONPath 断言无 `$.content[*].content` / `$.content[*].contentHtml`
4. **Adapter 集成测试**：真实 MySQL 验证 SQL 不扫描 content/contentHtml 列（`@DataJpaTest`）
5. **AppService 单测**：Mock Repository 返回 `PageResult<KnowledgeItemSummary>`，验证 `list()` 返回 `PageResult<KnowledgeItemListResponse>`

### 13.2 手工验证

| 步骤 | 操作 | 预期 |
|------|------|------|
| 1 | 打开管理端知识条目列表页 | 列表正常加载。以 20 条/页、平均 content 10KB 估算，优化前列表响应约 500KB，优化后预期 < 10KB（仅元数据字段），响应时间应有可感知下降 |
| 2 | 浏览器 DevTools Network 查看列表请求响应 | JSON 中无 `content`/`contentHtml` 字段 |
| 3 | 点击预览按钮（眼睛图标） | Modal 弹出，contentHtml 正常渲染 |
| 4 | 点击编辑按钮 | Drawer 打开，content 正常加载到 Markdown 编辑器 |
| 5 | 创建新条目后关闭 Drawer | 列表自动刷新，新条目正常展示 |
| 6 | 更新条目后关闭 Drawer | 列表自动刷新，更新后的条目正常展示 |
| 7 | 测试排序（ID/标题/分类/状态/时间/排序号） | 排序行为不变 |
| 8 | 测试筛选（关键词/分类/标签/状态） | 筛选行为不变 |

### 13.3 回滚验证

- 恢复旧版代码 → 列表接口重新返回 content/contentHtml → 前端列表页仍可正常展示（向后兼容）
