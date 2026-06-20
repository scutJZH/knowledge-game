# REQ-109：知识条目管理 — 排序增强

| 字段 | 值 |
|------|---|
| 需求编号 | REQ-109 |
| 状态 | designed |
| 优先级 | P3 |
| 前置依赖 | REQ-97 ✅（知识条目 CRUD）、REQ-86 ✅（统一排序规范） |
| 影响模块 | `knowledge-game-core`、`knowledge-game-admin`、`frontend/admin` |

---

## 1. 背景

知识条目管理页（`/content/knowledge-item`）已实现分页列表查询，Controller 已接收 `sort`/`order` 参数，但后端排序实现未遵循 REQ-86 标准模式：

- Adapter 使用私有 `FIELD_MAPPING`（仅 3 个字段）而非标准 `ALLOWED_SORT_FIELDS`（`LinkedHashMap` 白名单）
- 未调用 `SortFieldSpec.validate()` 校验非法字段（抛 `IllegalArgumentException` 而非 `BusinessException(400)`）
- 未使用共享 `SortFields.toSpringSort()` 工具类
- AppService 自定义 `buildSortField` 方法而非使用 `SortField.parse()` 静态工厂
- 前端仅 `sortOrder` 和 `updatedAt` 列启用了 `sorter: true`，其他列不可点击排序
- 前端 `KnowledgeItemQuery.sort` 类型仅含 3 个字段

## 2. 目标

1. 后端排序实现对齐 REQ-86 标准模式（`ALLOWED_SORT_FIELDS` + `SortFieldSpec.validate` + `SortFields.toSpringSort`）
2. 扩展可排序字段至 7 个：ID、标题、分类名称、排序号、状态、创建时间、更新时间
3. 分类名称排序通过 JOIN `knowledge_category` 表实现（`MIN(c.name)` 子查询排序）
4. AppService 改用 `SortField.parse()` 替代自定义 `buildSortField`
5. 前端 7 列启用 `sorter: true`，新增 `createdAt` 展示列

## 3. 非目标

- 多字段组合排序（保持单字段，分类名称排序自动附加 `sortOrder ASC, createdAt DESC` 作为 tiebreaker）
- 用户端（app）接口改造
- 其他管理端列表接口

## 4. API 协议

### 4.1 排序参数（无变更）

```
GET /api/admin/knowledge-items?...&sort={field}&order={asc|desc}&page=0&size=20
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `sort` | String | 否 | `sortOrder` | 排序字段名（PO 字段驼峰式，分类名称用 `categoryName`） |
| `order` | String | 否 | `asc`(默认字段) | 排序方向，`asc`/`desc`，大小写不敏感 |

### 4.2 可排序字段

| sort 参数值 | 排序逻辑 | 排序字段中文名 | tiebreaker |
|------------|---------|-------------|-----------|
| `id` | `knowledge_item.id` | ID | 无 |
| `title` | `knowledge_item.title` | 标题 | 无 |
| `categoryName` | `MIN(c.name)` 子查询（c = `knowledge_category` 表，通过 `knowledge_item_category_relation` 关联表 JOIN） | 分类名称 | `sortOrder ASC, createdAt DESC` |
| `sortOrder` | `knowledge_item.sort_order` **（强制升序，忽略 `order` 参数）** | 排序号 | `createdAt DESC` |
| `status` | `knowledge_item.status` | 状态 | 无 |
| `createdAt` | `knowledge_item.created_at` | 创建时间 | 无 |
| `updatedAt` | `knowledge_item.updated_at` | 更新时间 | 无 |

默认排序（`sort` 参数缺失时）：`sortOrder ASC, createdAt DESC`。

### 4.3 排序参数缺失时的行为

| 场景 | 行为 |
|------|------|
| `sort` 缺失或空 | 默认 `sortOrder ASC, createdAt DESC` |
| `sort` 不在白名单 | HTTP 400 + `"不支持的排序字段: xxx，允许的字段: [ID, 标题, 分类名称, 排序号, 状态, 创建时间, 更新时间]"` |
| `order` 缺失或非法 | 非 `asc` 一律视为 `desc`（与 `SortField.parse()` 行为一致） |

复用 `ResultCode.PARAM_ERROR`，不新增枚举值。

## 5. 核心设计

### 5.1 Adapter 重构（对齐 REQ-86 标准）

**文件**：`KnowledgeItemRepositoryAdapter.java`

**改造点**：

1. **新增 `ALLOWED_SORT_FIELDS`**（`LinkedHashMap`，PO 字段名 → 中文显示名）：
   ```java
   private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>() {{
       put("id", "ID");
       put("title", "标题");
       put("categoryName", "分类名称");
       put("sortOrder", "排序号");
       put("status", "状态");
       put("createdAt", "创建时间");
       put("updatedAt", "更新时间");
   }};
   ```

2. **删除私有 `FIELD_MAPPING`**（被 `ALLOWED_SORT_FIELDS` 替代）

3. **重构 `toSpringSort`** 为标准模式（仅处理可直接映射到 PO 字段的排序，`categoryName` 不在此方法处理）：
   ```java
   private Sort toSpringSort(SortField sortField) {
       SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
       if (validated == null) {
           return Sort.by(Sort.Direction.ASC, "sortOrder")
                   .and(Sort.by(Sort.Direction.DESC, "createdAt"));
       }
       if ("categoryName".equals(validated.getField())) {
           throw new IllegalStateException(
                   "categoryName 排序应在 findByConditions 入口分支处理，不应到达 toSpringSort");
       }
       if ("sortOrder".equals(validated.getField())) {
           return Sort.by(Sort.Direction.ASC, "sortOrder")
                   .and(Sort.by(Sort.Direction.DESC, "createdAt"));
       }
       return SortFields.toSpringSort(validated);
   }
   ```

4. **`categoryName` 在 `toSpringSort` 中不处理**——它在上层 `findByConditions` 方法中走独立的 EntityManager 查询路径（见 5.2）

5. **新增 `EntityManager` 字段注入**（仅用于 `categoryName` 排序路径）：
   ```java
   private final EntityManager em;
   ```

### 5.2 分类名称排序（EntityManager + CriteriaBuilder + 子查询）

**触发条件**：`sortField.getField().equals("categoryName")`

`findByConditions` 方法开头增加分支判断，`categoryName` 时委托给私有方法 `findByConditionsOrderByCategoryName`：

```
findByConditions(keyword, categoryId, tag, status, sortField, pageNumber, pageSize)
  ├─ sortField 为 null 或非 "categoryName"
  │    → 现有 Specification + PageRequest 路径（使用重构后的 toSpringSort）
  └─ sortField.field = "categoryName"
       → findByConditionsOrderByCategoryName(...)
            ├─ 复用 Specification 构建 WHERE 谓词（避免重复过滤逻辑）
            ├─ CriteriaBuilder + Subquery 构建 ORDER BY (SELECT MIN(c.name) FROM ...)
            ├─ 执行 TypedQuery（含 DISTINCT + ORDER BY + tiebreaker 排序）
            └─ 单独 COUNT 查询（无 JOIN，直接复用 Specification 谓词）
```

**子查询 ORDER BY 表达式**（JPA Criteria API）：

```
Subquery<String> → SELECT MIN(c.name)
  FROM KnowledgeItemCategoryRelationPO r
  JOIN KnowledgeCategoryPO c ON c.id = r.categoryId
  WHERE r.itemId = root.id
  
ORDER BY subquery ASC/DESC, root.sortOrder ASC, root.createdAt DESC
```

`CriteriaBuilder.asc(subquery)` / `CriteriaBuilder.desc(subquery)` 将子查询作为 ORDER BY 表达式（Hibernate 6.x 支持）。

**DISTINCT**：`cq.distinct(true)` 防止一个条目有多个分类时产生重复行。

**COUNT 查询**：不与数据查询共用 CriteriaQuery（避免 DISTINCT + JOIN 对 COUNT 的干扰），单独用 `CriteriaQuery<Long>` + `cb.count(root)` + 复用 Specification 谓词。

**NULL 处理**：无分类的条目，子查询返回 NULL。统一使用 NULLS LAST 策略——无分类条目始终排在最后（不论 ASC 还是 DESC）。通过 CriteriaBuilder 的 `asc(subquery).nullsLast()` / `desc(subquery).nullsLast()` 显式指定，不依赖 MySQL 默认行为。

### 5.3 AppService 简化

**文件**：`KnowledgeItemAppService.java`

删除私有方法 `buildSortField(String sort, String order)`，直接使用 `SortField.parse(sort, order)`：

```java
// 改造前：
SortField sortField = buildSortField(sort, order);

// 改造后：
SortField sortField = SortField.parse(sort, order);
// null → adapter 使用默认排序 (sortOrder ASC, createdAt DESC)
```

`SortField.parse()` 返回 null（sort 缺失时）的行为与 adapter 默认排序一致，不改变现有默认排序语义。

### 5.4 前端改造

**文件**：`KnowledgeItem/index.tsx`

| 列 | 改造内容 |
|----|---------|
| ID | 添加 `sorter: true` |
| 标题 | 添加 `sorter: true` |
| 分类 | 添加 `sorter: true`，sort 字段名 `categoryName`（需映射：ProTable sort key `categoryIds` → API 参数 `categoryName`） |
| 排序号 | 已有 `sorter: true`，不变 |
| 状态 | 添加 `sorter: true` |
| **创建时间**（新增列） | 添加 `dataIndex: 'createdAt'`，`valueType: 'dateTime'`，`sorter: true`，`search: false` |
| 更新时间 | 已有 `sorter: true`，不变 |

**分类列排序特殊处理**：分类列的 `dataIndex` 是 `categoryIds`（数组），ProTable 点击排序时 sort key 为 `categoryIds`。前端 request 函数需做映射。完整改造如下：

```typescript
// request 函数中 sort 处理（替换现有第 290-297 行）
request={async (params, sort) => {
    let sortField: string | undefined;
    let sortOrder: string | undefined;
    if (sort && typeof sort === 'object' && Object.keys(sort).length > 0) {
        const key = Object.keys(sort)[0];
        // ProTable 分类列的 dataIndex 是 categoryIds，映射为后端 categoryName
        sortField = key === 'categoryIds' ? 'categoryName' : key;
        sortOrder = (sort as Record<string, string>)[key] === 'ascend' ? 'asc' : 'desc';
    }
    const res = await listKnowledgeItems({
        keyword: params.keyword,
        categoryId: params.categoryId,
        tag: params.tag,
        status: params.status,
        sort: sortField as KnowledgeItemQuery['sort'],
        order: sortOrder as 'asc' | 'desc' | undefined,
        page: (params.current || 1) - 1,
        size: params.pageSize || 20,
    });
    ...
}}
```

**文件**：`knowledge-item.ts`

`KnowledgeItemQuery.sort` 类型扩展：
```typescript
sort?: 'id' | 'title' | 'categoryName' | 'sortOrder' | 'status' | 'createdAt' | 'updatedAt';
```

## 6. Impact Analysis

### 6.1 修改文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `backend/knowledge-game-core/src/main/java/.../adapter/repoadapter/KnowledgeItemRepositoryAdapter.java` | 重构+扩展 | ALLOWED_SORT_FIELDS、toSpringSort 标准化、注入 EntityManager、新增 categoryName 排序路径 |
| `backend/knowledge-game-admin/src/main/java/.../application/service/KnowledgeItemAppService.java` | 简化 | 删除 buildSortField，改用 SortField.parse() |
| `backend/knowledge-game-core/src/test/java/.../adapter/repoadapter/KnowledgeItemRepositoryAdapterTest.java` | 修改+新增用例 | 新增排序相关用例 #1-9（toSpringSort 重构 + 非法字段 + 默认排序） |
| `backend/knowledge-game-core/src/test/java/.../adapter/repoadapter/KnowledgeItemRepositoryAdapterIntegrationTest.java` | **新建** | categoryName 排序集成测试（`@DataJpaTest`），用例 #10-13 |
| `backend/knowledge-game-admin/src/test/java/.../application/service/KnowledgeItemAppServiceTest.java` | 修改+新增用例 | 新增 sortField 透传断言，用例 #14-15 |
| `frontend/admin/src/pages/KnowledgeItem/index.tsx` | 扩展 | 7 列 sorter + 新增 createdAt 列 + categoryIds→categoryName 映射 |
| `frontend/admin/src/services/knowledge-item.ts` | 扩展 | KnowledgeItemQuery.sort 类型扩展 |

### 6.2 不变更

- `KnowledgeItemRepository.java`（Port 接口）— `SortField` 参数签名不变
- `KnowledgeItemJpaRepository.java` — 不需要新增 @Query 方法（categoryName 走 EntityManager）
- `KnowledgeItemController.java` — 端点签名不变
- `KnowledgeItemResponse.java` — 响应字段不变（已有 `createdAt` 字段）

### 6.3 新增依赖

- `KnowledgeItemRepositoryAdapter` 新增 `jakarta.persistence.EntityManager` 依赖（由 Spring 容器自动注入，无需额外配置）

### 6.4 向前兼容

未开发需求中无直接依赖知识条目排序的需求。REQ-108（知识条目对接回收站）和 REQ-111（知识条目批量导入）与本需求无排序契约冲突。

## 7. Verification Plan

### 7.1 自动化测试

#### 后端 Adapter 单元测试

| # | 场景 | 预期 |
|---|------|------|
| 1 | sort=null → 默认排序 | `Sort.by(ASC, "sortOrder").and(Sort.by(DESC, "createdAt"))` |
| 2 | sort="id" order="asc" | `Sort.by(ASC, "id")` |
| 3 | sort="title" order="desc" | `Sort.by(DESC, "title")` |
| 4 | sort="status" | `Sort.by(DESC, "status")`（order 缺失视为 DESC） |
| 5 | sort="sortOrder" | 复合排序 `ASC sortOrder + DESC createdAt` |
| 6 | sort="createdAt" order="asc" | `Sort.by(ASC, "createdAt")` |
| 7 | sort="updatedAt" order="desc" | `Sort.by(DESC, "updatedAt")` |
| 8 | sort="categoryName" | 走 EntityManager 分支，验证 `itemJpaRepository.findAll(Spec, Pageable)` 未被调用 |
| 9 | sort="invalidField" | `BusinessException(400)`，消息含允许字段中文名列表 |

#### 后端 Adapter 集成测试（`@DataJpaTest`）

| # | 场景 | 预期 |
|---|------|------|
| 10 | 按分类名称升序排列 | 有分类条目按 `MIN(c.name)` ASC 排列在前，无分类条目 NULLS LAST 排在最后；同分类内按 `sortOrder ASC, createdAt DESC` |
| 11 | 按分类名称降序排列 | 有分类条目按 `MIN(c.name)` DESC 排列在前，无分类条目 NULLS LAST 排在最后；同分类内按 `sortOrder ASC, createdAt DESC` |
| 12 | 条目有多个分类时 categoryName 排序 | 按 MIN(category.name) 排序，DISTINCT 去重，不重复出现 |
| 13 | COUNT 与数据分页一致性 | 创建 1 个关联 3 个分类的条目，验证分页返回 totalElements=1（不是 3） |

#### AppService 单元测试

| # | 场景 | 预期 |
|---|------|------|
| 14 | list 方法 sort 不传 | 调用 adapter 时 sortField 为 null |
| 15 | list 方法 sort="title" order="asc" | sortField 为 `SortField("title", ASC)` |

#### 前端类型检查

| # | 命令 | 预期 |
|---|------|------|
| 16 | `npx tsc --noEmit` | 无类型错误 |

### 7.2 手工验收

1. 打开管理端 `/content/knowledge-item` 页面，点击各列头验证排序切换
2. 点击"分类"列头，验证按分类名称排序（升序/降序）
3. 点击"创建时间"列头（新增列），验证排序
4. 验证默认排序为 sortOrder 升序
5. 验证分类名称排序时，筛选条件（关键词/分类/标签/状态）仍然生效

### 7.3 回滚标准

- 任何已有测试用例失败
- 分类名称排序性能显著低于其他列排序（单次查询 > 500ms，数据量 < 10000 条）
- 前端列头排序点击后页面崩溃或白屏
