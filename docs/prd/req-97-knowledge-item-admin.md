# REQ-97：知识条目 — 管理端 CRUD API + 管理页

> 状态：`designed`
> 创建：2026-06-18
> 前置依赖：REQ-09（questions API 模式）、REQ-65（旧知识库管理页已拆分为分类管理）、REQ-86（动态排序）、REQ-90（ImageUploadField 凭证式图片上传）、REQ-94（聚合根停用/启用校验）
> 后置依赖：REQ-98（用户端知识库浏览页）、REQ-99（学习记录追踪 + 富媒体扩展）

## 1. 背景与定位

REQ-96 已将原"知识库管理页"改名为"分类管理"（路由 `/content/category`），承载知识点分类（`knowledge_category`）的 CRUD。但真正的"知识库内容"实体一直缺失。

**REQ-97 引入知识条目（knowledge_item）实体**：承载可被用户学习的内容（标题 + Markdown 正文 + 封面图 + 标签 + 分类关联），是 REQ-98（用户端浏览）和 REQ-99（学习记录追踪）的前置依赖。

知识库**全局共享**，无群组维度（与 question 一致，与 card template 等不同）。

## 2. 设计决策汇总

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | Markdown 编辑器 | **vditor**（前端）+ **commonmark-java**（后端渲染）+ **jsoup** 消毒 | vditor 体验最佳，commonmark 渲染结果用于 REQ-98 用户端直接展示，避免用户端引入 Markdown 库 |
| 2 | 状态机 | **ACTIVE / INACTIVE 二元**（复刻 QuestionStatus） | 与项目现有模式一致；草稿场景用 INACTIVE 表达，避免引入 DRAFT 状态机 |
| 3 | 排序 | **sort_order 字段 + 上移/下移按钮**（不引入拖拽库） | 项目零拖拽依赖；上移/下移满足百级条目的运营需求 |
| 4 | 停用校验 | **双向补齐**：①停用 KnowledgeCategory 时校验 ACTIVE 知识条目关联数；②启用 KnowledgeItem 时校验关联分类全部 ACTIVE | 与 REQ-94 完全对齐，数据一致性强 |
| 5 | 分类必填 | **必填至少 1 个 ACTIVE 分类** | 避免游离条目；question 是选填，但知识条目需要分类导航 |
| 6 | tags 标签 | **保留**（复刻 question：≤10 项，每项 ≤20 字） | 与 question 一致，便于跨实体聚合标签筛选 |
| 7 | slug | **不引入**（YAGNI） | 用户端以 id 访问，REQ-98 真有分享链接需求再加 |
| 8 | externalLink | **不引入**（YAGNI） | 知识条目定位为自托管内容；外链型内容等独立需求 |

## 3. 数据模型

### 3.1 `knowledge_item` 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | — | 主键 |
| `title` | VARCHAR(200) | NOT NULL | 标题 |
| `content` | MEDIUMTEXT | NOT NULL | Markdown 源（MEDIUMTEXT 上限 16MB） |
| `content_html` | MEDIUMTEXT | NOT NULL | 后端 commonmark 渲染 + jsoup 消毒后的 HTML，供 REQ-98 直接渲染 |
| `cover_image_file_id` | BIGINT | NULLABLE | 封面图 fileId（FileRef） |
| `cover_image_url` | VARCHAR(500) | NULLABLE | 封面图 URL（FileRef 冗余字段，与 file_id 同存同缺） |
| `tags` | JSON | NULLABLE | 标签数组，如 `["Java","多线程"]` |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 排序权重，小者靠前 |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | ACTIVE / INACTIVE |
| `created_at` | DATETIME | NOT NULL | |
| `updated_at` | DATETIME | NOT NULL | |

**索引**：
- `idx_status_sort (status, sort_order, created_at)` — 列表默认排序
- `idx_created_at (created_at)` — 时间排序降级方案

**FileRef 半状态约束**：`cover_image_file_id` 与 `cover_image_url` 必须同时存在或同时为 NULL（由 `FileRef.of()` 在 Domain 层强制）。Converter `updatePO` 显式赋值双字段（避免 MapStruct `IGNORE` 策略导致半状态）。

### 3.2 `knowledge_item_category_relation` 表（复刻 `question_category_relation`）

| 字段 | 类型 | 约束 |
|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | — |
| `item_id` | BIGINT | NOT NULL |
| `category_id` | BIGINT | NOT NULL |

**索引**：
- `uk_item_category (item_id, category_id)` — 联合唯一
- `idx_category_id (category_id)` — 反向查询（停用分类时统计 ACTIVE 条目）

### 3.3 级联行为

- 知识条目软删除（→INACTIVE）时关联记录保留，恢复（→ACTIVE）后关联自动生效
- 分类软删除时，知识条目的分类列表查询应过滤掉已 INACTIVE 的分类
- **停用 KnowledgeCategory 前**：必须先确认无 ACTIVE 知识条目关联，否则拒绝停用
- **启用 KnowledgeItem 前**：必须先确认关联分类全部 ACTIVE
- **不做物理级联删除**（与 question 一致）

## 4. 后端 DDD 分层

### 4.1 Domain 层（core/domain）

```
domain/model/entity/
  KnowledgeItem.java                    — 聚合根（create/reconstruct/update/activate/deactivate/moveToSortOrder/updateContentHtml）
domain/model/domainenum/
  KnowledgeItemStatus.java              — ACTIVE / INACTIVE（复刻 QuestionStatus）
domain/model/vo/
  — （BatchSortItem 复用 admin 层 DTO `admin.api.dto.request.BatchSortItem`，不新建 domain VO，避免 domain 反向依赖 admin）
domain/port/outbound/
  KnowledgeItemRepository.java          — 出端口（无 Port 后缀，与 QuestionRepository 命名一致）
domain/service/
  KnowledgeItemDomainService.java       — 校验 + 工厂（validateAndCreate / validateUpdate / validateActivatable）
```

**KnowledgeItem 实体行为方法**：

```java
public static KnowledgeItem create(String title, String content,
                                    FileRef coverImage, List<String> tags, int sortOrder);
public static KnowledgeItem reconstruct(Long id, String title, String content, String contentHtml,
                                        FileRef coverImage, List<String> tags, int sortOrder,
                                        KnowledgeItemStatus status,
                                        LocalDateTime createdAt, LocalDateTime updatedAt);

public void update(String title, String content, FileRef coverImage,
                   List<String> tags, Integer sortOrder);  // null 字段不更新
public void updateContentHtml(String html);  // 仅 AppService 渲染后调用
public void activate();
public void deactivate();
public void moveToSortOrder(int newOrder);
```

> **关于 `updateContentHtml` 单独方法**：contentHtml 由后端渲染，不属于用户输入。`update(...)` 方法只接受用户输入字段；contentHtml 在 AppService 中通过 `updateContentHtml(...)` 单独写入，避免污染 update 语义。

**KnowledgeItemRepositoryPort**（与 `QuestionRepository` 命名/职责完全对齐）：

```java
public interface KnowledgeItemRepository {
    KnowledgeItem save(KnowledgeItem item);
    Optional<KnowledgeItem> findById(Long id);
    PageResult<KnowledgeItem> findByConditions(String keyword, Long categoryId, String tag,
                                                KnowledgeItemStatus status,
                                                SortField sortField, int page, int size);
    List<KnowledgeItem> findByIds(List<Long> ids);
    void saveCategoryRelations(Long itemId, List<Long> categoryIds);

    /** 查询条目关联的分类 ID（过滤 INACTIVE 分类），用于 Response 返回给前端 */
    List<Long> findActiveCategoryIdsByItemId(Long itemId);

    /** 统计与指定分类关联的 ACTIVE 条目数量，给 KnowledgeCategoryDomainService.validateDelete 反向校验用 */
    long countActiveByCategoryId(Long categoryId);

    /** 查询多个条目关联的全部分类 ID（**不过滤**状态），用于批量启用前的分类状态校验
     *  返回 Map<itemId, List<categoryId>>。validateActivatable 用此方法 + categoryMap 查出 INACTIVE 名称 */
    Map<Long, List<Long>> findCategoryIdsByItemIds(List<Long> itemIds);

    void batchUpdateStatus(List<Long> ids, KnowledgeItemStatus status);
}
```

> **关于 batch-sort**：不在端口暴露专门方法。`batch-sort` API 在 `KnowledgeItemAppService.batchSort(List<BatchSortItem>)` 内逐条 load → update sortOrder → save，与 `KnowledgeCategoryAppService.batchSort`（行 140-159）模式一致。`BatchSortItem` 复用 admin 层 DTO `com.knowledgegame.admin.api.dto.request.BatchSortItem`，不污染 domain 层。

### 4.2 Infrastructure 层（core/infrastructure）

```
infrastructure/db/entity/
  KnowledgeItemPO.java                     — @Entity + FileRef 双字段持久化
  KnowledgeItemCategoryRelationPO.java     — 复刻 QuestionCategoryRelationPO
infrastructure/db/repository/
  KnowledgeItemJpaRepository.java          — Spring Data JPA + @Query（countActiveByCategoryId 等）
  KnowledgeItemCategoryRelationJpaRepository.java
infrastructure/db/converter/
  KnowledgeItemConverter.java              — PO ↔ Domain（tags JSON 用 default 方法；FileRef 双字段 default 方法）
infrastructure/adapter/repoadapter/
  KnowledgeItemRepositoryAdapter.java      — 实现出端口，含 Specification 多条件 + SortField 动态排序
infrastructure/markdown/
  MarkdownRenderer.java                    — commonmark-java + jsoup 工具类（@Component）
```

**MarkdownRenderer 设计**：

```java
@Component
public class MarkdownRenderer {
    private final Parser parser;       // commonmark + GFM 表格 + 删除线扩展
    private final HtmlRenderer renderer;
    private final org.jsoup.safety.Safelist safelist = Safelist.relaxed()
            .addAttributes(":all", "class")           // 保留 class（代码高亮等）
            .addTags("details", "summary")             // 折叠语法
            .addAttributes("code", "class");           // 代码语言标注

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String html = renderer.render(parser.parse(markdown));
        return Jsoup.clean(html, safelist);
    }
}
```

**RepositoryAdapter 关键点**：

- `FIELD_MAPPING`：`Map.of("createdAt", "createdAt", "updatedAt", "updatedAt", "sortOrder", "sortOrder")`
- 默认排序：`sort_order ASC, created_at DESC`（复合排序，在 toSpringSort 中处理）
- 标签筛选：`JSON_CONTAINS(tags, '"tag"')`（复刻 question）
- 分类筛选：子查询 `knowledge_item_category_relation`
- Converter `updatePO` 显式赋值 `coverImageFileId` + `coverImageUrl` 双字段（避免 MapStruct `NullValuePropertyMappingStrategy.IGNORE` 导致 FileRef 半状态）

**KnowledgeItemJpaRepository JPQL**（参照 `QuestionJpaRepository`，改表名/字段名即可）：

```java
public interface KnowledgeItemJpaRepository extends
        JpaRepository<KnowledgeItemPO, Long>,
        JpaSpecificationExecutor<KnowledgeItemPO> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE KnowledgeItemPO i SET i.status = :status, i.updatedAt = CURRENT_TIMESTAMP WHERE i.id IN :ids")
    void batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") KnowledgeItemStatus status);

    @Query("SELECT r.categoryId FROM KnowledgeItemCategoryRelationPO r " +
            "WHERE r.itemId = :itemId " +
            "AND r.categoryId IN (SELECT c.id FROM KnowledgeCategoryPO c WHERE c.status = 'ACTIVE')")
    List<Long> findActiveCategoryIdsByItemId(@Param("itemId") Long itemId);
}
```

**KnowledgeItemCategoryRelationJpaRepository JPQL**：

```java
public interface KnowledgeItemCategoryRelationJpaRepository
        extends JpaRepository<KnowledgeItemCategoryRelationPO, Long> {

    /** 反向查询：统计与指定分类关联的 ACTIVE 条目数（给 validateDelete 用） */
    @Query("SELECT COUNT(r.itemId) FROM KnowledgeItemCategoryRelationPO r " +
            "WHERE r.categoryId = :categoryId " +
            "AND r.itemId IN (SELECT i.id FROM KnowledgeItemPO i WHERE i.status = 'ACTIVE')")
    long countActiveItemsByCategoryId(@Param("categoryId") Long categoryId);

    void deleteByItemId(Long itemId);

    List<KnowledgeItemCategoryRelationPO> findAllByItemIdIn(List<Long> itemIds);
}
```

> **注意 @Modifying(clearAutomatically = true)**：批量更新后清空一级缓存，避免后续 findById 拿到脏数据。`CURRENT_TIMESTAMP` 由 Hibernate 翻译为数据库当前时间，不依赖应用服务器时区。

### 4.3 Admin 层（knowledge-game-admin）

```
admin/api/controller/
  KnowledgeItemController.java                — 10 个端点（CRUD + 分类关联 + 批量启停 + batch-sort）
admin/api/dto/request/
  CreateKnowledgeItemRequest.java             — title/content/coverImageFileId/tags/sortOrder/categoryIds
  UpdateKnowledgeItemRequest.java             — 同上（部分字段可选）
  KnowledgeItemCategoryUpdateRequest.java     — 复刻 question
  BatchStatusRequest.java                     — 复用 question 已有 DTO
  BatchSortRequest.java                       — 复用 KnowledgeCategory 已有 DTO（List<BatchSortItem>）
admin/api/dto/response/
  KnowledgeItemResponse.java                  — id/title/content/contentHtml/coverImageFileId/coverImageUrl/tags/categoryIds/sortOrder/status/createdAt(Long)/updatedAt(Long)
admin/api/assembler/
  KnowledgeItemAssembler.java                 — Domain → Response（含 toEpochMilli 时间转换）
admin/application/service/
  KnowledgeItemAppService.java                — @Transactional 编排：渲染 + verifyFileRef + 领域校验 + 持久化 + 关联写入；batchSort 在此内编排
```

> **不新建 FileRefResolver 类**。`verifyFileRef(fileId, bizType)` 作为 `KnowledgeItemAppService` 私有方法实现，与现有 `KnowledgeCategoryAppService.verifyFileRef`（行 173-193）、`IpSeriesAppService.verifyFileRef`、`CardTemplateAppService.verifyFileRef` 模式完全一致。未来如有第 6 个聚合根引入图片字段，可重构提取为共享组件，但 REQ-97 范围内不重构既有代码。

**verifyFileRef 实现要点（参照 KnowledgeCategoryAppService.java:173-193）**：

```java
private FileRef verifyFileRef(Long fileId, String expectedBizType) {
    if (fileId == null) {
        return null;  // null 表示"不更新"（Domain.update 通过 if(x!=null) 守卫保留原值）
    }
    Result<FileInfoResponse> result = fileServiceClient.getFileInfo(fileId);
    FileInfoResponse info = result.getData();
    if (info == null) {
        throw new BusinessException(400, "文件不存在: " + fileId);
    }
    Map<String, Object> metadata = info.getMetadata();
    if (metadata == null || !expectedBizType.equals(metadata.get("bizType"))) {
        throw new BusinessException(400, "文件类型不匹配，期望 " + expectedBizType);
    }
    Long currentUserId = SecurityUtils.getCurrentUserId();  // 不用 SecurityContextHolder
    Object metaUserId = metadata.get("userId");
    // JSON 反序列化后 userId 可能是 Integer，必须 Number.longValue() 转换
    Long metaUserIdLong = metaUserId instanceof Number ? ((Number) metaUserId).longValue() : null;
    if (!Objects.equals(currentUserId, metaUserIdLong)) {
        throw new BusinessException(403, "无权使用该文件");
    }
    return FileRef.of(fileId, info.getUrl());
}
```

> **返回 null 而非 FileRef.of(null, null)**：与现有 4 个 AppService 保持一致。null 表示"不更新"语义；FileRef.of(null, null) 在 Domain.update 中会被 `if(x!=null)` 守卫拦截（无法区分"清空"与"不更新"），现有项目用"不传 fileId = 不更新"语义。"清空图片"作为已知限制（参考 REQ-88）。

## 5. API 设计

### 5.1 端点清单

| 操作 | 方法 | 路径 | 参考样板 |
|------|------|------|---------|
| 创建知识条目 | POST | `/api/admin/knowledge-items` | question POST |
| 查询详情 | GET | `/api/admin/knowledge-items/{id}` | question GET |
| 分页列表 | GET | `/api/admin/knowledge-items` | question GET |
| 更新 | PUT | `/api/admin/knowledge-items/{id}` | question PUT |
| 软删除（→INACTIVE） | DELETE | `/api/admin/knowledge-items/{id}` | question DELETE |
| 查询关联分类 | GET | `/api/admin/knowledge-items/{id}/categories` | question GET categories |
| 替换关联分类（全量） | PUT | `/api/admin/knowledge-items/{id}/categories` | question PUT categories |
| 批量启用 | PUT | `/api/admin/knowledge-items/batch-activate` | question batch-activate |
| 批量停用 | PUT | `/api/admin/knowledge-items/batch-deactivate` | question batch-deactivate |
| 批量排序（拖拽/上移下移） | PUT | `/api/admin/knowledge-items/batch-sort` | knowledge-category batch-sort |

### 5.2 列表查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | String | 否 | 标题模糊匹配（`title LIKE '%kw%'`） |
| `categoryId` | Long | 否 | 按关联分类筛选（子查询 `knowledge_item_category_relation`） |
| `tag` | String | 否 | 按标签筛选（`JSON_CONTAINS(tags, '"tag"')`） |
| `status` | String | 否 | ACTIVE / INACTIVE |
| `sort` | String | 否 | `sortOrder`（默认）/ `createdAt` / `updatedAt` |
| `order` | String | 否 | `asc`（默认，sortOrder 升序直观）/ `desc` |
| `page` | int | 否 | 默认 0 |
| `size` | int | 否 | 默认 20 |

> **复合排序**：当 `sort=sortOrder`（默认）时，次级排序按 `createdAt DESC`。RepositoryAdapter 中处理为 Spring `Sort.by("sortOrder").asc().and(Sort.by("createdAt").desc())`。

### 5.3 请求/响应 DTO

**CreateKnowledgeItemRequest**：

```json
{
  "title": "Java 多线程基础",
  "content": "# 多线程\n\n## 1. 线程创建...",
  "coverImageFileId": 123,
  "tags": ["Java", "多线程"],
  "sortOrder": 0,
  "categoryIds": [1, 2]
}
```

校验：`@NotBlank @Size(max=200) title`、`@NotBlank content`（长度由 DomainService 校验 ≤50000）、`@Size(max=10) tags`（每项 ≤20 字由 DomainService 校验）、`@NotEmpty categoryIds`、`coverImageFileId` 可空、`sortOrder` 默认 0。

**UpdateKnowledgeItemRequest**：与 Create 相同字段，但 `categoryIds` 不在此请求中（与 question 一致，分类关联独立端点管理）。`title`/`content`/`tags`/`coverImageFileId`/`sortOrder` 全可选（null 表示不更新）。

**KnowledgeItemResponse**：

```json
{
  "id": 1,
  "title": "Java 多线程基础",
  "content": "...",
  "contentHtml": "<h1>多线程</h1><h2>1. 线程创建</h2>...",
  "coverImageFileId": 123,
  "coverImageUrl": "https://...",
  "tags": ["Java", "多线程"],
  "categoryIds": [1, 2],
  "sortOrder": 0,
  "status": "ACTIVE",
  "createdAt": 1718700000000,
  "updatedAt": 1718700000000
}
```

**BatchSortRequest（复用 KnowledgeCategory 已有 DTO）**：

```json
{ "items": [{ "id": 1, "sortOrder": 0 }, { "id": 2, "sortOrder": 1 }] }
```

### 5.4 处理流程

#### Create

```
1. Controller 接收 CreateKnowledgeItemRequest
2. AppService.create(cmd):
   a. 渲染：contentHtml = markdownRenderer.render(cmd.content)
   b. FileRef 解析：coverImage = verifyFileRef(cmd.coverImageFileId, "KNOWLEDGE_ITEM_COVER")
      （fileId 为 null 时返回 null，Domain.create 接受 null 表示无图片）
   c. 领域校验 + 创建：item = domainService.validateAndCreate(cmd.title, cmd.content,
                          coverImage, cmd.tags, cmd.sortOrder, cmd.categoryIds)
   d. 写入 contentHtml：item.updateContentHtml(contentHtml)
   e. 持久化：saved = itemRepository.save(item)
   f. 写分类关联（同事务）：itemRepository.saveCategoryRelations(saved.getId(), cmd.categoryIds)
   g. 查询关联分类 ID：categoryIds = itemRepository.findActiveCategoryIdsByItemId(saved.getId())
   h. 组装响应：return assembler.toResponse(saved, categoryIds)
3. Controller 返回 Result<KnowledgeItemResponse>
```

#### Update（含 contentHtml 重渲染）

```
1. Controller 接收 PUT /knowledge-items/{id} + UpdateKnowledgeItemRequest
2. AppService.update(id, cmd):
   a. 加载：item = itemRepository.findById(id).orElseThrow(...)
   b. **若 cmd.content != null**（用户改了正文）：
      - contentHtml = markdownRenderer.render(cmd.content)
      - 校验 content 长度 ≤50000 字（DomainService.validateUpdate）
   c. FileRef 解析：coverImage = verifyFileRef(cmd.coverImageFileId, "KNOWLEDGE_ITEM_COVER")
   d. 领域校验：domainService.validateUpdate(item, cmd.title, cmd.content, coverImage, cmd.tags)
   e. 更新领域实体：item.update(cmd.title, cmd.content, coverImage, cmd.tags, cmd.sortOrder)
      （null 字段不更新；coverImage=null 时 if(fileRef!=null) 守卫保留原值）
   f. **若步骤 b 渲染了 contentHtml**：item.updateContentHtml(contentHtml)
   g. 持久化：itemRepository.save(item)
   h. 返回 Response（categoryIds 来自 findActiveCategoryIdsByItemId）
3. 前端 Drawer 提交时，**分类通过独立端点 PUT /knowledge-items/{id}/categories 更新**
   （与 question 一致，UpdateKnowledgeItemRequest 不含 categoryIds 字段）
```

> **关键约束**：`content` 与 `contentHtml` 必须同步。AppService.update 检测到 content 非空时强制重渲染 contentHtml，避免两者不一致。若用户只更新 title（content 为 null），contentHtml 保持不变。

## 6. 前端管理页

### 6.1 路由配置

**文件**：`frontend/admin/config/routes.ts`

```diff
       {
         path: '/content/category',
         name: '分类管理',
         component: './Category',
       },
+      {
+        path: '/content/knowledge-item',
+        name: '知识条目管理',
+        component: './KnowledgeItem',
+      },
```

### 6.2 目录结构

```
frontend/admin/src/
├── pages/
│   └── KnowledgeItem/                              ← 新建
│       ├── index.tsx                                ← ProTable 主页
│       ├── components/
│       │   ├── KnowledgeItemFormDrawer.tsx          ← Drawer 表单
│       │   └── __tests__/
│       │       └── KnowledgeItemFormDrawer.test.tsx
│       └── __tests__/
│           └── index.test.tsx
├── components/
│   └── VditorEditor/                                ← 新建（共享组件，便于 REQ-98 复用）
│       ├── index.tsx
│       └── __tests__/
│           └── index.test.tsx
├── services/
│   └── knowledge-item.ts                            ← API 服务
└── config/
    └── routes.ts                                    ← 新增路由
```

### 6.3 主页 `KnowledgeItem/index.tsx`

参照 `QuestionBank/index.tsx`：

- **列定义**：
  - ID（80px）
  - 封面图（50x50 缩略，无封面显示占位）
  - 标题（带 Tooltip 显示完整标题）
  - 分类（多 Tag，参考 question：前 2 个 + "+N"）
  - 标签（多 Tag，同上）
  - 排序（InputNumber，可直接编辑 + 上移/下移按钮触发 batch-sort）
  - 状态（ACTIVE/INACTIVE Tag）
  - 更新时间（dateTime）
  - 操作（编辑、启用/停用、上移、下移）
- **工具栏**：新建知识条目、批量启用、批量停用
- **查询表单**：keyword（Input）、categoryId（TreeSelect）、tag（Input）、status（Select）

### 6.4 表单 `KnowledgeItemFormDrawer.tsx`

```tsx
<Drawer title={mode === 'create' ? '新建知识条目' : '编辑知识条目'} width={900} open={open}>
  <Form form={form} layout="vertical">
    <Form.Item label="标题" name="title" rules={[{ required: true, max: 200 }]}>
      <Input placeholder="请输入标题" />
    </Form.Item>

    <Form.Item label="封面图" name="coverImageFileId">
      <ImageUploadField bizType="KNOWLEDGE_ITEM_COVER" url={initialValues?.coverImageUrl} />
    </Form.Item>

    <Form.Item label="正文（Markdown）" name="content" rules={[{ required: true }]}>
      <VditorEditor />
    </Form.Item>

    <Form.Item label="分类" name="categoryIds"
               rules={[{ required: true, message: '至少选择 1 个分类' }]}>
      <TreeSelect treeData={convertToTreeDataActiveOnly(categoryTree)} multiple showSearch
                  treeNodeFilterProp="title" placeholder="选择分类（可多选）" />
    </Form.Item>

    <Form.Item label="标签" name="tags">
      <Select mode="tags" placeholder="输入后按回车" tokenSeparators={[',']} />
    </Form.Item>

    <Form.Item label="排序" name="sortOrder" initialValue={0}>
      <InputNumber min={0} precision={0} style={{ width: 120 }} />
    </Form.Item>
  </Form>
</Drawer>
```

**编辑保存的两步调用流程**（与 question 一致）：

```ts
const onFinish = async (values) => {
  if (mode === 'create') {
    // 一次性创建：CreateRequest 含 categoryIds
    await createKnowledgeItem({ ...values, categoryIds });
  } else {
    // 两步更新：UpdateRequest 不含 categoryIds，分类通过独立端点
    await updateKnowledgeItem(editingId, values);  // 不传 categoryIds
    if (values.categoryIds !== initialValues.categoryIds) {
      await updateKnowledgeItemCategories(editingId, values.categoryIds);
    }
  }
};
```

> **理由**：与 question 完全一致（UpdateQuestionRequest 不含 categoryIds）。统一模式便于跨实体维护。

### 6.5 VditorEditor 组件（共享组件，单一文件）

**目录**：`frontend/admin/src/components/VditorEditor/index.tsx`（共享组件，REQ-98 用户端可直接复用渲染逻辑；不在 `pages/KnowledgeItem/components/` 下另建薄封装，避免重复）

```tsx
interface Props {
  value?: string;
  onChange?: (md: string) => void;
}

const VditorEditor: React.FC<Props> = ({ value, onChange }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const vditorRef = useRef<Vditor | null>(null);

  // 初始化（仅 mount 一次）
  useEffect(() => {
    if (!containerRef.current) return;
    import('vditor')
      .then(({ default: Vditor }) => {
        vditorRef.current = new Vditor({
          value: value ?? '',
          mode: 'wysiwyg',
          height: 400,
          cache: { enable: false },
          preview: { hljs: { lineNumber: true, style: 'github' } },
          upload: {
            accept: 'image/*',
            handler: async (files): Promise<string | null> => {
              // 调用 services/fileUpload.ts，凭证式上传
              // 返回 markdown 图片语法 ![](url)，vditor 自动插入
            },
          },
          input: () => onChange?.(vditorRef.current!.getValue()),
        });
      })
      .catch(() => {
        message.error('Markdown 编辑器加载失败，请刷新重试');
      });
    return () => vditorRef.current?.destroy();
  }, []);

  // 外部 value 变化同步（如编辑模式回填）
  useEffect(() => {
    if (vditorRef.current && value !== undefined && value !== vditorRef.current.getValue()) {
      vditorRef.current.setValue(value, true);  // true: 不触发 input
    }
  }, [value]);

  return <div ref={containerRef} />;
};
```

**要点**：
- vditor 体积较大（~2MB），用动态 `import('vditor')` 拆分 chunk，避免影响首屏 bundle
- `.catch()` 处理 CDN/网络失败，避免编辑器区域静默空白
- 测试时 `jest.mock('vditor', ...)`，跳过 jsdom 不兼容的 DOM API（MutationObserver 等）

### 6.6 行排序交互（上移/下移 + 输入框）

- 每行操作列含「上移」「下移」按钮
- 点击上移：取当前页前一条记录交换 sortOrder，调 `batch-sort` API
- 点击下移：取当前页后一条记录交换 sortOrder，调 `batch-sort` API
- InputNumber 直接修改：单条 batch-sort 调用（items 仅含一条）
- 拖拽不实现（YAGNI）

## 7. 校验规则汇总

### 7.1 创建/更新校验

| 校验项 | 规则 | 抛出位置 |
|--------|------|---------|
| 标题 | 非空、≤200 字 | DomainService |
| content（Markdown） | 非空、≤50000 字 | DomainService |

> **content 50000 字限制依据**：知识条目定位为单篇阅读内容，50000 中文字符（约 150KB UTF-8）覆盖绝大多数技术博客与教程（多数文章 <10000 字）。数据库列虽为 MEDIUMTEXT（16MB），但产品上不应允许超长正文（移动端阅读体验、首屏渲染性能、富文本 payload 体积）。超出建议拆分为系列条目或附件形式。
| content_html | 后端渲染 + jsoup 消毒，存 MEDIUMTEXT | AppService（MarkdownRenderer） |
| tags | ≤10 项，每项 ≤20 字 | DomainService |
| categoryIds | **必填**（≥1），所有分类必须 ACTIVE | DomainService（创建/更新） |
| coverImageFileId | 可选；提供时校验 bizType=KNOWLEDGE_ITEM_COVER + userId 匹配 | AppService（verifyFileRef 私有方法） |
| sortOrder | ≥0，默认 0 | DomainService |

### 7.2 状态切换校验

| 操作 | 校验规则 | 抛出位置 |
|------|---------|---------|
| 启用 KnowledgeItem（INACTIVE→ACTIVE） | 关联分类全部 ACTIVE | DomainService.validateActivatable |

> **启用校验的数据来源**：`validateActivatable(title, categoryIds, categoryMap)` 由 `KnowledgeItemAppService.batchActivate` 编排：
> 1. 调 `findCategoryIdsByItemIds(ids)` 拿到 **不过滤** 状态的全部分类 ID（含 INACTIVE）
> 2. 批量加载 `KnowledgeCategory` 构建 `categoryMap`
> 3. 调 `validateActivatable`：遍历 categoryMap，对 INACTIVE/缺失分类收集名称，构造错误消息
>
> 此模式与 `QuestionDomainService.validateActivatable`（行 159-183）+ `QuestionAppService.batchActivate`（行 190-204）完全一致。**注意**：`findActiveCategoryIdsByItemId`（过滤 INACTIVE）仅用于 Response 返回前端展示，不用于校验。
| 停用 KnowledgeItem（ACTIVE→INACTIVE） | 无前置校验（与 question 一致） | DomainService.deactivate |
| 停用 KnowledgeCategory（REQ-94 扩展） | 无 ACTIVE 子分类 + 无 ACTIVE 题目关联 + **无 ACTIVE 知识条目关联** | KnowledgeCategoryDomainService.validateDelete |

## 8. KnowledgeCategoryDomainService 改造（破坏性）

```diff
 public class KnowledgeCategoryDomainService {
     private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
     private final QuestionRepository questionRepository;
+    private final KnowledgeItemRepository itemRepository;

-    public KnowledgeCategoryDomainService(KnowledgeCategoryRepositoryPort categoryRepositoryPort,
-                                           QuestionRepository questionRepository) {
+    public KnowledgeCategoryDomainService(KnowledgeCategoryRepositoryPort categoryRepositoryPort,
+                                           QuestionRepository questionRepository,
+                                           KnowledgeItemRepository itemRepository) {
         this.categoryRepositoryPort = categoryRepositoryPort;
         this.questionRepository = questionRepository;
+        this.itemRepository = itemRepository;
     }

     public void validateDelete(Long categoryId) {
         long activeChildCount = categoryRepositoryPort.countActiveByParentId(categoryId);
         if (activeChildCount > 0) {
             throw new BusinessException("知识点分类下存在 " + activeChildCount + " 个 ACTIVE 子分类，无法删除");
         }
         long activeQuestionCount = questionRepository.countActiveByCategoryId(categoryId);
         if (activeQuestionCount > 0) {
             throw new BusinessException("知识点分类关联 " + activeQuestionCount + " 道 ACTIVE 题目，无法删除");
         }
+        long activeItemCount = itemRepository.countActiveByCategoryId(categoryId);
+        if (activeItemCount > 0) {
+            throw new BusinessException("知识点分类关联 " + activeItemCount + " 个 ACTIVE 知识条目，无法删除");
+        }
     }
 }
```

**联动改造**：
- `KnowledgeGameCoreAutoConfiguration` 的 `@Bean` 方法签名：注入新增的 `KnowledgeItemRepository`
- `KnowledgeCategoryDomainServiceTest`：mock 新增 `itemRepository`，新增「item 关联非 0 时 validateDelete 抛 BusinessException」用例
- **现有用例不能破坏**：所有原 question 关联场景的行为不变

## 9. 测试策略

### 9.1 后端单元测试

| 测试类 | 覆盖点 |
|--------|--------|
| `KnowledgeItemTest` | create/reconstruct/update/activate/deactivate/moveToSortOrder/updateContentHtml 各分支 |
| `KnowledgeItemDomainServiceTest` | validateAndCreate（标题/内容/tags/categoryIds 各分支）、validateUpdate（null 字段不更新）、validateActivatable（关联分类 ACTIVE/INACTIVE） |
| `MarkdownRendererTest` | CommonMark 标准用例、GFM 表格、删除线、`<script>`/`onclick`/`javascript:` XSS 全剥离、空输入 |
| `KnowledgeItemConverterTest` | PO↔Domain 双向（tags JSON 往返、FileRef 双字段） |
| `KnowledgeItemRepositoryAdapterTest` (`@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`) | 沿用 `QuestionRepositoryAdapterTest` 现有模式：mock `KnowledgeItemJpaRepository` + `KnowledgeItemCategoryRelationJpaRepository`，覆盖各方法的委托逻辑（findByConditions 组装 Specification、saveCategoryRelations 调用顺序、countActiveByCategoryId 委托等） |
| `KnowledgeItemAppServiceTest` | create/update/batchActivate/batchDeactivate/batchSort 编排；Markdown 渲染调用顺序；verifyFileRef 失败抛 BusinessException；事务边界 |
| `KnowledgeItemControllerTest` (`@WebMvcTest` + `addFilters=false`) | 10 个端点 + 参数校验（@Valid）+ Result 包装 |
| **回归** `KnowledgeCategoryDomainServiceTest` | 改造后的 validateDelete：item 关联非 0 时抛 BusinessException |

### 9.2 前端单元测试

| 测试类 | 覆盖点 |
|--------|--------|
| `KnowledgeItemFormDrawer.test.tsx` | 表单校验（标题/分类必填）、提交 payload 正确性、ImageUploadField value 同步、VditorEditor value 同步 |
| `VditorEditor.test.tsx` | jsdom 下 `jest.mock('vditor', ...)` 跳过实际渲染，仅测试 props 透传与 onChange 调用 |
| `index.test.tsx` | ProTable 渲染、上移/下移按钮调 batch-sort、批量启停 |

### 9.3 测试穿透力（按 my-workflow 铁律十）

| 场景 | 测试类型 | 说明 |
|------|---------|------|
| tags JSON 序列化往返（`List<String>`） | Converter 单元 + Adapter Mockito 单元 | 避免字段类型不匹配（Mockito 覆盖委托逻辑；真实序列化往返由手动集成验证） |
| FileRef 双字段持久化 | Converter 单元 + Adapter Mockito 单元 | 验证无半状态（Converter default 方法 + Adapter save 显式赋值；真实持久化由手动集成验证） |
| MarkdownRenderer XSS 消毒 | 单元（含真实 jsoup 调用） | 必须有，安全关键 |
| JSON_CONTAINS 标签筛选 / Specification 子查询 | **Mockito 单元（覆盖委托逻辑）** + 集成环境手动验证 | 现有 `QuestionRepositoryAdapter` 即此模式，core 模块无 @DataJpaTest 基础设施。**已知技术债**：JSON_CONTAINS SQL 语义未自动化测试，依赖集成环境/手动验证（见 §13） |
| KnowledgeCategoryDomainService 改造 | 回归测试 | 现有用例不能破坏 |

> **关于测试覆盖标准**：`KnowledgeItemRepositoryAdapterTest` 是本项目首次对 RepositoryAdapter 做全方法覆盖（现有 `QuestionRepositoryAdapterTest` 仅覆盖 REQ-94 新增的 2 个方法）。测试标准高于既有模式 — 实施时按本表覆盖范围执行，不参照 question 的现有覆盖范围。

## 10. 新增依赖

### 10.1 Maven（backend/knowledge-game-core/pom.xml）

```xml
<!-- Markdown 渲染 -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.22.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
    <version>0.22.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-strikethrough</artifactId>
    <version>0.22.0</version>
</dependency>
<!-- HTML 消毒 -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

### 10.2 npm（frontend/admin/package.json）

```json
{
  "dependencies": {
    "vditor": "^3.10.0"
  }
}
```

## 11. 影响范围

| 文件/模块 | 改动类型 | 说明 |
|----------|---------|------|
| `KnowledgeCategoryDomainService` | **改造（破坏性）** | 新增构造参数 `KnowledgeItemRepository`，validateDelete 增加 item 关联校验 |
| `KnowledgeGameCoreAutoConfiguration` | 改造 | `KnowledgeCategoryDomainService` @Bean 方法签名更新 |
| `KnowledgeCategoryDomainServiceTest` | 改造 | mock 新增 `itemRepository`，新增 item 关联失败用例 |
| `routes.ts`（admin 前端） | 新增 | `/content/knowledge-item` 路由 + 菜单项 |
| `docs/overview.md` | 更新 | 数据模型表新增 knowledge_item；API 清单新增 `/api/admin/knowledge-items/**` |
| `docs/features.md` | 更新 | 管理后台功能新增"知识条目管理" |
| `docs/requirements.md` | 更新 | REQ-97 状态 idea → designed；REQ-98 备注明确前置已完成 |

**无影响**：REQ-94 现有测试用例（仅扩展校验范围，不破坏现有行为）、REQ-09 questions 全部代码、KnowledgeCategory CRUD 流程、ImageUploadField 组件。

## 12. 文件清单（实施时核对）

### 12.1 后端 core（新增 11 个 + 改造 3 个）

**新增**：
1. `core/domain/model/entity/KnowledgeItem.java`
2. `core/domain/model/domainenum/KnowledgeItemStatus.java`
3. `core/domain/port/outbound/KnowledgeItemRepository.java`
4. `core/domain/service/KnowledgeItemDomainService.java`
5. `core/infrastructure/db/entity/KnowledgeItemPO.java`
6. `core/infrastructure/db/entity/KnowledgeItemCategoryRelationPO.java`
7. `core/infrastructure/db/repository/KnowledgeItemJpaRepository.java`
8. `core/infrastructure/db/repository/KnowledgeItemCategoryRelationJpaRepository.java`
9. `core/infrastructure/db/converter/KnowledgeItemConverter.java`
10. `core/infrastructure/adapter/repoadapter/KnowledgeItemRepositoryAdapter.java`
11. `core/infrastructure/markdown/MarkdownRenderer.java`

**改造**：
1. `core/domain/service/KnowledgeCategoryDomainService.java`（构造器 + validateDelete）
2. `core/config/KnowledgeGameCoreAutoConfiguration.java`（@Bean 签名）
3. `core/pom.xml`（新增 4 个依赖）

### 12.2 后端 admin（新增 ~10 个）

1. `admin/api/controller/KnowledgeItemController.java`
2. `admin/api/dto/request/CreateKnowledgeItemRequest.java`
3. `admin/api/dto/request/UpdateKnowledgeItemRequest.java`
4. `admin/api/dto/request/KnowledgeItemCategoryUpdateRequest.java`
5. `admin/api/dto/response/KnowledgeItemResponse.java`
6. `admin/api/assembler/KnowledgeItemAssembler.java`
7. `admin/application/service/KnowledgeItemAppService.java`（含 verifyFileRef 私有方法，复刻 KnowledgeCategoryAppService 模式）

### 12.3 前端 admin（新增 6 个 + 改造 1 个）

1. `frontend/admin/src/pages/KnowledgeItem/index.tsx`
2. `frontend/admin/src/pages/KnowledgeItem/components/KnowledgeItemFormDrawer.tsx`
3. `frontend/admin/src/components/VditorEditor/index.tsx`（共享组件，REQ-98 可复用）
4. `frontend/admin/src/services/knowledge-item.ts`
5. `frontend/admin/config/routes.ts`（改造，新增路由）

### 12.4 后端测试（新增 ~8 个 + 改造 1 个）

1. `core/domain/model/entity/KnowledgeItemTest.java`
2. `core/domain/service/KnowledgeItemDomainServiceTest.java`
3. `core/infrastructure/markdown/MarkdownRendererTest.java`
4. `core/infrastructure/db/converter/KnowledgeItemConverterTest.java`
5. `core/infrastructure/adapter/repoadapter/KnowledgeItemRepositoryAdapterTest.java`
6. `core/domain/service/KnowledgeCategoryDomainServiceTest.java`（改造，新增 item 关联用例）
7. `admin/application/service/KnowledgeItemAppServiceTest.java`
8. `admin/api/controller/KnowledgeItemControllerTest.java`

### 12.5 前端测试（新增 ~4 个）

1. `frontend/admin/src/pages/KnowledgeItem/__tests__/index.test.tsx`
2. `frontend/admin/src/pages/KnowledgeItem/components/__tests__/KnowledgeItemFormDrawer.test.tsx`
3. `frontend/admin/src/components/VditorEditor/__tests__/index.test.tsx`

## 13. 已知限制

1. **vditor 与 commonmark 渲染差异**：vditor 基于 lute（GFM 实现），commonmark 是 CommonMark 标准 + 扩展。约 5%-10% 边缘语法（脚注、数学公式、mermaid 图、自定义容器 `!!!`）commonmark 不识别，会原样输出 Markdown 源字符。REQ-98 用户端可能偶尔看到原始语法。**接受此限制**。
2. **vditor 在 jsdom 下测试难度大**：依赖 DOM API（MutationObserver、Selection 等），单测需 `jest.mock('vditor', ...)` 跳过实际渲染。真实渲染验证依赖手动浏览器测试。
3. **content_html 存储成本翻倍**：MEDIUMTEXT 16MB 上限足够，知识库内容不会超大，可接受。
4. **行拖拽未实现**：上移/下移按钮 + InputNumber 满足百级条目运营需求；千级以上数据可能需要拖拽（后置需求）。
5. **批量导入未实现**：Markdown 正文不适合 Excel 表格承载；后续如需批量导入可考虑 Markdown 文件 zip 包上传。
6. **RepositoryAdapter JSON_CONTAINS / Specification 子查询未集成测试**：`KnowledgeItemRepositoryAdapterTest` 沿用 `QuestionRepositoryAdapterTest` 的 Mockito 模式（core 模块无 @DataJpaTest 基础设施），仅验证委托逻辑，不验证真实 SQL 语义。JSON_CONTAINS 标签匹配、Specification 子查询的正确性依赖集成环境/手动验证（§14.1 第 3 项新建场景覆盖基本路径）。**未来技术债**：core 模块引入 @DataJpaTest 基础设施（H2 或 Testcontainers MySQL）后，可补集成测试。

## 14. 验证清单

### 14.1 手动验证

1. 启动 admin 后端（8081）+ admin 前端（8000），登录后左侧菜单显示「知识条目管理」
2. 点击菜单进入 `/content/knowledge-item`，ProTable 加载正常
3. 新建知识条目：标题 + Markdown 正文（含表格、代码块）+ 封面图 + 多分类 + 多标签 → 保存成功
4. 编辑：标题、封面图、标签、分类、sortOrder 修改 → 保存成功，updatedAt 更新
5. 上移/下移：sortOrder 交换，列表顺序变化
6. 启用/停用：单条 + 批量均正常
7. **REQ-94 联动验证**（依赖 §8 KnowledgeCategoryDomainService 改造完成）：创建一条关联某分类的知识条目 → 尝试停用该分类 → 应被拒绝（错误消息含「关联 X 个 ACTIVE 知识条目」）
8. **启用校验联动**（依赖 §4 KnowledgeItemDomainService.validateActivatable 完成）：把知识条目的关联分类停用 → 尝试启用此条目 → 应被拒绝（错误消息含「关联的知识点分类 X 处于停用状态」）
9. 详情页查看 contentHtml 渲染：标题、列表、表格、代码块均正常显示
10. Markdown XSS 攻击测试：正文输入 `<script>alert(1)</script>` 保存 → 详情页不应执行脚本

### 14.2 自动化验证

```bash
# 后端
cd backend && mvn clean test        # 含 KnowledgeItem 系列 + KnowledgeCategoryDomainService 回归

# 前端
cd frontend/admin && npm run test   # 含 KnowledgeItem 系列
cd frontend/admin && npm run build  # 构建无错误
cd frontend/admin && npm run lint   # 无新增 lint 警告
```

## 15. 工作量评估

| 模块 | 文件数 | 工时 |
|------|--------|------|
| core/domain | 4 新文件 | 0.5 天 |
| core/infrastructure | 7 新文件 + MarkdownRenderer | 0.75 天 |
| admin/api + application | 8 新文件 | 0.75 天 |
| KnowledgeCategoryDomainService 改造 | 1 改造 + 联动 | 0.25 天 |
| 前端 | 6 新文件 + routes | 1 天 |
| 后端测试 | 8 测试类 + 1 改造 | 1 天 |
| 前端测试 | 4 测试类 | 0.5 天 |
| 文档（PRD + overview.md + features.md + requirements.md） | 4 更新 | 0.25 天 |
| **合计** | ~30 新文件 + 5 改造 | **~5 天** |

## 16. 后续需求

| 编号 | 名称 | 归类 Phase |
|------|------|-----------|
| REQ-98 | 用户端 — 知识库浏览页（按分类浏览/阅读/学习埋点） | Phase 6 前端游戏界面 |
| REQ-99 | 知识库 — 学习记录追踪 + 富媒体扩展 | 后期优化 |

REQ-97 完成后，REQ-98 用户端可直接消费 `contentHtml` 字段渲染（零 Markdown 渲染依赖），首屏体验最佳。
