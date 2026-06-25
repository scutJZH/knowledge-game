# REQ-108：知识条目管理对接回收站

> 状态：`designed`
> 创建：2026-06-26
> 前置依赖：REQ-100 ✅, REQ-102 ✅, REQ-103 ✅, REQ-97 ✅

## 前置依赖

本需求基于 [REQ-100 通用回收站系统](./req-100-recycle-bin.md) 的框架实现。复用契约：
- `RecycleBinItemStrategy<T>` 接口（详见 REQ-100 第 4.1.4 节）
- `RecycleBinItemStrategyRegistry` 注册中心（自动发现策略 Bean）
- `knowledge_item_deleted` 详情表 PO + JPA Repository（详见 REQ-100 第 3.2 节，REQ-97 已建表）
- `ResourceType.KNOWLEDGE_ITEM` 枚举与 `toBizTypes()` → `["KNOWLEDGE_ITEM"]` 映射（详见 REQ-100 第 4.1.2 节）
- `RecycleBinAppService` 的 restore/purge/batchRestore/batchPurge 框架（REQ-102/103 已完成）

本需求的增量工作：
- 创建 `KnowledgeItemRecycleBinStrategy` 策略 Bean
- 修改 `KnowledgeItemAppService.delete()` 从 deactivate 改为移入回收站
- `KnowledgeItemDeletedJpaRepository` 新增 `findByOriginalId` / `findAllByOriginalIdIn` 查询方法

**与已实现策略的差异定位**：
- 区别于 REQ-106（Question）：KnowledgeItem **有封面图 coverImage**，purge 时需调用 `FileCleanupPort` 清理
- 区别于 REQ-107（KnowledgeCategory）：KnowledgeItem 无父子层级、无递归子树，无需拓扑排序恢复
- 与 REQ-106 一致：均有分类关联（M:N），`related_data` JSON 快照 `categoryAssociationIds`，restore 时校验分类全存在

### 框架集成接缝：三条完整调用链

**链路 1 — DELETE（REQ-108 专属）**

```
DELETE /api/admin/knowledge-items/{id}
  → KnowledgeItemController.delete(id)
    → KnowledgeItemAppService.delete(id)               [@Transactional]
      → SecurityUtils.getCurrentUsername()
      → strategy.validateDeletable(id)                  ← REQ-108 实现
      → strategy.moveToRecycleBin(id, deletedBy)        ← REQ-108 实现
        ├── itemRepository.findById()                    读 Domain
        ├── relationJpaRepository.findByItemId()         查分类关联
        ├── itemDeletedJpaRepository.save()              写快照（related_data = {"categoryAssociationIds": [...]})
        ├── relationJpaRepository.deleteByItemId()       删分类关联
        ├── itemJpaRepository.deleteById()               物理删原表
        └── recycleBinItemJpaRepository.save()           总览表登记
```

**链路 2 — 恢复（REQ-103 框架 + REQ-108 策略）**

```
POST /api/admin/recycle-bin/{id}/restore
  → RecycleBinController.restore(id)
    → RecycleBinAppService.restore(id)
      → strategyRegistry.get(KNOWLEDGE_ITEM)
          .restore(item.getId())                        ← REQ-108 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── itemDeletedJpaRepository.findByOriginalId()
            ├── categoryJpaRepository.countByIdIn()      校验分类全存在（REQ-106 同款）
            ├── native INSERT knowledge_item             保留原始 ID（INACTIVE）
            ├── itemRepository.saveCategoryRelations()   恢复分类关联
            ├── itemDeletedJpaRepository.deleteById()
            └── recycleBinItemJpaRepository.deleteById()
```

**链路 3 — 永久删除（REQ-102 框架 + REQ-108 策略）**

```
DELETE /api/admin/recycle-bin/{id}
  → RecycleBinController.purge(id)
    → RecycleBinAppService.purge(id)
      → strategyRegistry.get(KNOWLEDGE_ITEM)
          .purge(item.getId())                          ← REQ-108 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── itemDeletedJpaRepository.findByOriginalId()
            ├── fileCleanupPort.deleteFile(coverImageFileId)   清理封面图（异常仅 warn）
            ├── itemDeletedJpaRepository.deleteById()
            └── recycleBinItemJpaRepository.deleteById()
```

## 1. 问题陈述

当前 `DELETE /api/admin/knowledge-items/{id}` 的行为是调用 `KnowledgeItem.deactivate()` 将状态设为 INACTIVE。INACTIVE 同时承担了「停用」和「删除」两种语义，导致：

1. 管理员无法区分"暂时停用"和"真正删除"
2. 没有误删恢复机制
3. 条目关联的分类信息丢失（软删除后仍可通过 GET 接口查看分类，但分类可能已被修改）
4. 条目的封面图文件未清理，长期积累形成存储垃圾

## 2. 解决方案

将 DELETE 与 INACTIVE 解耦：
- DELETE 端点改为物理删除原表行 + 分类关联 + 写入回收站快照（30 天保留期）
- INACTIVE 保留为"停用"语义（通过 PUT update 接口切换 status）
- purge 时调用文件服务清理封面图（FileCleanupPort）

具体动作：
- 创建 `KnowledgeItemRecycleBinStrategy` 实现 `RecycleBinItemStrategy<KnowledgeItem>`
- 删除前校验：仅验证条目存在（KnowledgeItem 无下游依赖需校验）
- 移入回收站：快照所有字段到 `knowledge_item_deleted`，快照分类关联到 `related_data` JSON，删分类关联，物理删 `knowledge_item` 行，总览表登记
- 恢复：校验关联分类全存在 → 从快照重建（native INSERT），强制 INACTIVE，恢复分类关联
- 永久删除：清理封面图文件 → 物理清理快照 + 回收站记录

## 3. 用户故事

1. 作为系统管理员，我想要删除知识条目时它进入回收站而非仅仅停用，以便我能恢复误删的数据
2. 作为系统管理员，我想要从回收站恢复已删除的知识条目，恢复后状态为 INACTIVE，分类关联一并恢复，以便手动确认后重新启用
3. 作为系统管理员，我想要永久删除回收站中的知识条目记录（含封面图文件），以便释放数据库与存储空间

## 4. 实现决策

> **上下文**：REQ-108 是第五个 `RecycleBinItemStrategy` 实现。KnowledgeItem 介于 Question（最简）与 KnowledgeCategory（最复杂）之间——有封面图（与 Category 同需 FileCleanupPort），无父子层级（与 Question 同简单），有分类关联（与 Question 同 M:N 模式）。

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | 策略 Bean 位置 | core 模块 `infrastructure/adapter/repoadapter/` | 遵循现有模式 |
| 2 | Bean 注册方式 | admin 模块 `RecycleBinConfig` 显式 `@Bean` 注册 | 遵循现有模式；策略操作 JPA Repository，属于 infrastructure 层 |
| 3 | 持久化方式 | 读操作走 Port 接口，写/删操作走 JPA Repository | 遵循 REQ-100/104 确立的规则 |
| 4 | 删除前校验 | 仅验证条目存在（`itemRepository.findById()`） | KnowledgeItem 无下游依赖需校验 |
| 5 | 恢复后状态 | 强制 INACTIVE | REQ-100 已固化的契约 |
| 6 | 恢复时校验 | 分类关联全存在（`categoryJpaRepository.countByIdIn()`） | 与 REQ-106 一致：关联分类被删除则拒绝恢复 |
| 7 | 恢复实现方式 | native SQL `INSERT INTO knowledge_item (id, title, content, content_html, cover_image_file_id, cover_image_url, tags, sort_order, status, created_at, updated_at) VALUES (...)` | 保留原始 ID，JPA IDENTITY 策略不支持显式 ID |
| 8 | 分类关联快照 | `related_data` = `{"categoryAssociationIds": [1, 2, 3]}` | 通过 `relationJpaRepository.findByItemId()` 获取原始关联（不限制 ACTIVE） |
| 9 | 分类关联恢复 | 调 `itemRepository.saveCategoryRelations(itemId, categoryIds)` | 复用已有 Port 方法，内部 delete + insert |
| 10 | 事务 | 由调用方 `KnowledgeItemAppService.delete()` 的 `@Transactional` 保证原子性 | 策略接口契约"原子性由调用方保证" |
| 11 | 文件清理 | 需要 `FileCleanupPort`（仅 coverImage） | KnowledgeItem 有 coverImage FileRef 字段，purge 时需清理 |
| 12 | deletedBy 来源 | `SecurityUtils.getCurrentUsername()` | 遵循 REQ-100 决策 #8，在 `KnowledgeItemAppService.delete()` 中获取后传给策略 |
| 13 | originalName 取值 | `title` | KnowledgeItem.title 长度 ≤ 200（@Column length=200），无需截断 |
| 14 | content/contentHtml 字段处理 | 直接拷贝（MEDIUMTEXT 类型，无 JSON 包装） | 字段为纯文本/HTML，无序列化需求 |
| 15 | tags 字段处理 | 直接拷贝 PO 中已序列化的 JSON 字符串 | KnowledgeItemConverter 已统一序列化为 JSON 数组字符串 |

### 与已有策略的关键差异

| 维度 | REQ-104 IpSeries | REQ-105 CardTemplate | REQ-106 Question | REQ-107 Category | **REQ-108 KnowledgeItem** |
|------|:---:|:---:|:---:|:---:|:---:|
| FileCleanupPort | ✓ | ✓ | ✗ | ✓ (icon+cover) | **✓ (仅 cover)** |
| 递归子树 | ✗ | ✗ | ✗ | ✓ | **✗** |
| related_data JSON | ✗ | ✓ (ipSeriesName) | ✓ (categoryIds) | ✓ (subtreeIds) | **✓ (categoryIds)** |
| 恢复后校验 | ✗ | ✓ (IP存在+code冲突) | ✓ (分类存在) | ✓ (同名冲突) | **✓ (分类存在)** |
| 恢复后关联恢复 | ✗ | ✗ | ✓ (分类关联) | ✗ | **✓ (分类关联)** |
| 构造函数依赖数 | 7 | 8 | 7 | 8 | **8** |

### 策略依赖注入清单

```
KnowledgeItemRecycleBinStrategy 注入：
  ├── KnowledgeItemRepository                       （findById — 读 Domain 对象；saveCategoryRelations — 恢复关联）
  ├── RecycleBinItemRepositoryPort                  （findById — 读 RecycleBinItem）
  ├── KnowledgeItemJpaRepository                    （deleteById — 物理删原表）
  ├── KnowledgeItemDeletedJpaRepository             （save / findByOriginalId / findAllByOriginalIdIn / deleteById）
  ├── KnowledgeItemCategoryRelationJpaRepository    （findByItemId / deleteByItemId）
  ├── RecycleBinItemJpaRepository                   （save / deleteById）
  ├── KnowledgeCategoryJpaRepository                （countByIdIn — 恢复时校验分类存在）
  ├── FileCleanupPort                               （deleteFile — purge 时清理 coverImage）
  └── EntityManager                                 （@PersistenceContext 注入，restore 时 native SQL INSERT）
```

> 说明：构造函数接收前 8 个依赖，EntityManager 通过 setter 注入（与 REQ-105/106/107 一致），共 8 项构造参数。

### 策略方法伪代码

```
validateDeletable(originalId):
    if not itemRepository.findById(originalId).isPresent():
        throw new BusinessException("知识条目不存在: " + originalId)

moveToRecycleBin(originalId, deletedBy):
    item = itemRepository.findById(originalId)
        .orElseThrow(() -> new BusinessException("知识条目不存在: " + originalId))
    relations = relationJpaRepository.findByItemId(originalId)
    categoryIds = relations.stream().map(KnowledgeItemCategoryRelationPO::getCategoryId).toList()
    deletedPO = KnowledgeItemDeletedPO from item fields:
        - originalId, title, content, contentHtml
        - coverImageFileId / coverImageUrl（直接拷贝领域实体 FileRef 双字段）
        - tags（已序列化的 JSON 字符串，从领域 List<String> 重新序列化保证一致性）
        - sortOrder, status
        - createdAt / updatedAt
        - relatedData = writeJson({"categoryAssociationIds": categoryIds})
        - deletedBy, deletedAt = now()
    itemDeletedJpaRepository.save(deletedPO)
    relationJpaRepository.deleteByItemId(originalId)
    itemJpaRepository.deleteById(originalId)
    recycleBinItemJpaRepository.save(RecycleBinItemPO(
        resourceType=KNOWLEDGE_ITEM, originalId=...,
        originalName=item.getTitle(),                     # title ≤ 200，无需截断
        originalCreatedAt=..., originalUpdatedAt=...,
        deletedBy=..., deletedAt=now(),
        restoreDeadline=now().plusDays(30)
    ))

restore(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = itemDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
        .orElseThrow(() -> new BusinessException("知识条目快照不存在: " + recycleBinItem.getOriginalId()))
    categoryIds = parseJson(deletedPO.getRelatedData()).get("categoryAssociationIds")
    if categoryIds != null && !categoryIds.isEmpty():
        existingCount = categoryJpaRepository.countByIdIn(categoryIds)
        if existingCount != categoryIds.size():
            throw new BusinessException("知识条目关联的分类已被删除，无法恢复")
    entityManager.createNativeQuery(
        "INSERT INTO knowledge_item (id, title, content, content_html, "
        + "cover_image_file_id, cover_image_url, tags, sort_order, "
        + "status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, deletedPO.getOriginalId())
        .setParameter(2, deletedPO.getTitle())
        .setParameter(3, deletedPO.getContent())
        .setParameter(4, deletedPO.getContentHtml())
        .setParameter(5, deletedPO.getCoverImageFileId())
        .setParameter(6, deletedPO.getCoverImageUrl())
        .setParameter(7, deletedPO.getTags())
        .setParameter(8, deletedPO.getSortOrder())
        .setParameter(9, KnowledgeItemStatus.INACTIVE.name())
        .setParameter(10, deletedPO.getCreatedAt())
        .setParameter(11, LocalDateTime.now())
        .executeUpdate()
    if categoryIds != null && !categoryIds.isEmpty():
        itemRepository.saveCategoryRelations(deletedPO.getOriginalId(), categoryIds)
    itemDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)

purge(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = itemDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
        .orElse(null)
    if deletedPO == null:
        recycleBinItemJpaRepository.deleteById(recycleBinId)
        return
    if deletedPO.getCoverImageFileId() != null:
        try:
            fileCleanupPort.deleteFile(deletedPO.getCoverImageFileId())
        catch Exception e:
            log.warn("封面图清理失败: originalId={}, coverImageFileId={}", ...)
    itemDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)
```

## 5. 影响分析

### 新建文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `core/.../infrastructure/adapter/repoadapter/KnowledgeItemRecycleBinStrategy.java` | core | 策略 Bean，实现 `RecycleBinItemStrategy<KnowledgeItem>` |

### 修改文件

| 文件 | 模块 | 变更 |
|------|------|------|
| `admin/.../application/service/KnowledgeItemAppService.java` | admin | `delete()` 改为委托策略（validateDeletable + moveToRecycleBin）；注入 `RecycleBinItemStrategy<KnowledgeItem>` Bean；移除 `item.deactivate()` + `itemRepository.save(item)` 旧逻辑 |
| `admin/.../api/controller/KnowledgeItemController.java` | admin | Javadoc 注释从「软删除，无前置校验」改为「移入回收站」 |
| `admin/.../config/RecycleBinConfig.java` | admin | 新增 `knowledgeItemRecycleBinStrategy` `@Bean` 注册（含 `FileCleanupPort` 依赖） |
| `core/.../infrastructure/db/repository/KnowledgeItemDeletedJpaRepository.java` | core | 新增 `findByOriginalId(Long)` / `findAllByOriginalIdIn(List<Long>)` 查询方法 |

### 不需要修改

- `KnowledgeItemDeletedPO` — REQ-97 已建表，字段完整（含 coverImageFileId/coverImageUrl 双字段 + related_data）
- `KnowledgeItemController` — DELETE 端点签名不变，HTTP 方法相同
- `RecycleBinAppService` — 框架已就位（REQ-102/103）
- 前端回收站页面 — 通过 `supported-types` API 自动显示 KNOWLEDGE_ITEM 类型
- 前端知识条目管理页面 — DELETE 调用不变

### 受影响功能

| 功能 | 影响 | 风险 |
|------|------|------|
| `DELETE /api/admin/knowledge-items/{id}` | 行为变更：从 deactivate 改为移入回收站 | 前端调用方无需改动 |
| 回收站管理端列表页 | KNOWLEDGE_ITEM 类型出现在目录树和列表中 | 低风险，REQ-100 已做空集兼容 |
| `POST /api/admin/recycle-bin/{id}/restore` | 支持恢复知识条目（含分类关联） | 低风险，REQ-103 框架已就位 |
| `DELETE /api/admin/recycle-bin/{id}` | 支持永久删除知识条目（含封面图清理） | 低风险，REQ-102 框架已就位 |
| 知识条目批量启用 | 不受影响（仍走 `batchActivate`，但批量启用前 `findCategoryIdsByItemIds` 已校验分类 ACTIVE） | 无风险，本需求不改批量启用流程 |

## 6. 验证计划

### 手工验证步骤

1. **删除成功**：`DELETE /api/admin/knowledge-items/{id}` → 返回 200
   - 验证 `knowledge_item` 表该行已删除
   - 验证 `knowledge_item_category_relation` 表该条目关联已删除
   - 验证 `knowledge_item_deleted` 表有新快照行（含所有条目字段 + `related_data` JSON 含分类关联 ID + coverImageFileId/coverImageUrl）
   - 验证 `recycle_bin` 表有新记录（resource_type=KNOWLEDGE_ITEM，original_name=title）
   - 验证管理端回收站页面可见该记录

2. **删除被拒**：`DELETE /api/admin/knowledge-items/{id}`（不存在的 ID）→ 返回 400 "知识条目不存在: X"

3. **恢复成功**：`POST /api/admin/recycle-bin/{id}/restore` → 返回 200
   - 验证 `knowledge_item` 表行恢复，status=INACTIVE，createdAt 保留原值，updatedAt 为当前时间
   - 验证 `knowledge_item_category_relation` 表分类关联已恢复
   - 验证 `knowledge_item_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除

4. **恢复失败（关联分类已删除）**：删除一道有分类关联的条目入回收站 → 删除其关联分类（入回收站，物理删） → 恢复条目 → 返回 400 "知识条目关联的分类已被删除，无法恢复"

5. **永久删除（有封面图）**：`DELETE /api/admin/recycle-bin/{id}` → 返回 200
   - 验证 `knowledge_item_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除
   - 验证文件服务中封面图已被删除（调 file 服务 GET 接口确认 404）

6. **永久删除（无封面图）**：删除一道无封面图的条目 → 永久删除 → 不报错（FileCleanupPort 不调用）

7. **永久删除（封面图清理失败）**：模拟文件服务故障 → 永久删除 → 仍返回 200（仅 warn 日志），数据库行已清理

8. **恢复无分类关联的条目**：删除一道无分类关联的条目 → 恢复 → 验证恢复后无分类关联，related_data 为 `{"categoryAssociationIds": []}`

9. **恢复后手动启用**：恢复条目后 → PUT update status=ACTIVE → 验证条目可正常使用

### 自动化测试覆盖

| 测试类 | 类型 | 模块 | 覆盖目标 |
|--------|------|------|---------|
| `KnowledgeItemRecycleBinStrategyTest` | 集成（`@DataJpaTest`） | core | validateDeletable(2) + moveToRecycleBin(4) + restore(5) + purge(5) = 16 用例 |
| `KnowledgeItemRecycleBinStrategyBlackBoxTest` | 单元（Mockito） | admin | 恢复 INACTIVE 覆盖 + 分类校验失败 + 循环删除恢复 + 并发删快照静默跳过 + 文件清理失败容错 = 5 用例 |
| `KnowledgeItemAppServiceTest` | 单元（Mockito） | admin | delete 委托策略 + 不存在抛异常（修改已有 2 用例） |

### 测试用例明细

| # | 方法 | 场景 | 预期 |
|---|------|------|------|
| 1 | `validateDeletable` | 条目存在 | 正常返回 |
| 2 | `validateDeletable` | 条目不存在 | `BusinessException("知识条目不存在: X")` |
| 3 | `moveToRecycleBin` | 有分类关联 + 有封面图 | item 行删除 + 关联删除 + deleted 有快照（related_data 含 categoryIds + coverImageFileId/coverImageUrl）+ recycle_bin 有记录 |
| 4 | `moveToRecycleBin` | 无分类关联 + 无封面图 | 同上，related_data = `{"categoryAssociationIds": []}`，coverImage 字段为 null |
| 5 | `moveToRecycleBin` | 条目不存在 | `BusinessException("知识条目不存在: X")` |
| 6 | `moveToRecycleBin` | originalName 使用 title | recycle_bin.original_name = 条目 title（不截断） |
| 7 | `restore` | 有分类关联 + 分类全存在 | 行恢复(INACTIVE) + 分类关联恢复 + 快照删除 + 回收站删除；createdAt 保留原值 |
| 8 | `restore` | 关联分类部分被删 | `BusinessException("知识条目关联的分类已被删除，无法恢复")`，原表不恢复 |
| 9 | `restore` | 无分类关联 | 同 #7，无关联恢复 |
| 10 | `restore` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |
| 11 | `restore` | 快照不存在 | `BusinessException("知识条目快照不存在: X")` |
| 12 | `purge` | 正常路径（有封面图） | deleted + recycle_bin 均删除 + FileCleanupPort.deleteFile 被调用 |
| 13 | `purge` | 正常路径（无封面图） | 同上，FileCleanupPort 不被调用 |
| 14 | `purge` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |
| 15 | `purge` | 快照已被并发删除 | 静默跳过，仅删 recycle_bin |
| 16 | `purge` | 文件清理失败 | 仅 warn 日志，deleted + recycle_bin 仍删除（事务不回滚） |

## 7. 测试策略

### 测试类型选择

策略测试使用 `@DataJpaTest` 集成测试（真实 MySQL），因为策略直接操作多个 JPA Repository，Mock 无法覆盖 flush 顺序、唯一约束、自增 ID、native INSERT 等 JPA 行为。

测试注解模板：
```java
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({KnowledgeItemRecycleBinStrategy.class,
         KnowledgeItemRepositoryAdapter.class,
         RecycleBinItemRepositoryAdapter.class,
         MockFileCleanupPortConfig.class})  // 提供 FileCleanupPort 测试 Mock Bean
@ActiveProfiles("test")
class KnowledgeItemRecycleBinStrategyTest { ... }
```

策略依赖两个 Port 接口（`KnowledgeItemRepository`、`RecycleBinItemRepositoryPort`），需 `@Import` 对应的 Adapter 实现。`FileCleanupPort` 通过测试配置类提供 Mock Bean（参考 REQ-107 `KnowledgeCategoryRecycleBinStrategyTest` 模式）。

## 8. 不在范围

- 前端页面改动：无需改动，回收站页面由 REQ-100 交付，通过 `supported-types` API 自动显示 KNOWLEDGE_ITEM 类型
- 批量删除条目入回收站（当前仅有单条 DELETE 端点）
- 知识条目的下游引用（条目无被其他聚合引用）
- 回收站定时清理（REQ-101 已实现）
- 删除前对引用了该条目的题目做校验（Question 与 KnowledgeItem 无直接关联，REQ-106 已确认）
