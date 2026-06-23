# REQ-107：知识分类对接回收站

> 状态：`confirmed`
> 创建：2026-06-23
> 前置依赖：REQ-100 ✅, REQ-102 ✅, REQ-103 ✅, REQ-104 ✅, REQ-105 ✅, REQ-07 ✅

## 前置依赖

本需求基于 [REQ-100 通用回收站系统](./req-100-recycle-bin.md) 的框架实现。复用契约：
- `RecycleBinItemStrategy<T>` 接口（详见 REQ-100 第 4.1.4 节）
- `RecycleBinItemStrategyRegistry` 注册中心（自动发现策略 Bean）
- `knowledge_category_deleted` 详情表 PO + JPA Repository（详见 REQ-100 第 3.2.4 节）
- `ResourceType.KNOWLEDGE_CATEGORY` 枚举与 `toBizTypes()` → `["CATEGORY_ICON", "CATEGORY_COVER"]` 映射（详见 REQ-100 第 4.1.2 节）
- `RecycleBinAppService` 的 restore/purge/batchRestore/batchPurge 框架（REQ-102/103 已完成）
- `FileCleanupPort` 端口 + `FileCleanupAdapter` 实现（REQ-104 建立，REQ-105 沿用）

本需求的增量工作：
- 创建 `KnowledgeCategoryRecycleBinStrategy` 策略 Bean（**首个递归子树删除策略**）
- 修改 `KnowledgeCategoryAppService.delete()` 从 deactivate 改为移入回收站
- `KnowledgeCategoryDeletedJpaRepository` 新增 `findByOriginalId` + `findAllByOriginalIdIn` 查询方法
- 分类管理页前端删除按钮改为弹窗警告子分类数量

## 1. 问题陈述

当前 `DELETE /api/admin/knowledge-categories/{id}` 调用 `deactivate()` 将状态设为 INACTIVE。问题：
1. INACTIVE 同时承担「停用」和「删除」语义
2. 没有误删恢复机制
3. 树形分类删除后子分类变成孤儿节点
4. REQ-100 原设计"拒绝删除有子分类的父分类"导致一棵子树必须从叶子逐层删到根，操作繁琐

## 2. 解决方案

**核心变更**：父分类删除时**递归删除整个子树**——所有下级子分类一并移入回收站。全部节点需通过关联校验（无 ACTIVE 题目 + 无 ACTIVE 知识条目）。

具体动作：
- 创建 `KnowledgeCategoryRecycleBinStrategy` 实现 `RecycleBinItemStrategy<KnowledgeCategory>`
- `validateDeletable`：收集子树全部节点 → 逐节点校验无关联题目和条目
- `moveToRecycleBin`：批量快照子树全部节点到 `knowledge_category_deleted`（保留原始 `parentId`），主节点 `related_data` 记录子树元信息，物理删除子树全部行，总览表登记（一条记录）
- `restore`：收集全部子树快照 → 校验名称不冲突 → 按深度排序（根→一级→二级）→ 逐条 native INSERT（保留原始 ID、强制 INACTIVE）
- `purge`：遍历子树全部快照 → 清理 icon + coverImage 文件 → 物理删除全部快照 + 总览表

## 3. 用户故事

1. 作为系统管理员，我想要删除一个父分类时其下所有子分类一并移入回收站，以便一步操作取代逐层删除
2. 作为系统管理员，我想要在删除前被阻止（如果子树中任一节点关联了 ACTIVE 题目或知识条目），以便不会意外删除正在使用的分类数据
3. 作为系统管理员，我想要从回收站恢复已删除的分类树，所有节点恢复后为 INACTIVE，树结构完整恢复
4. 作为系统管理员，我想要永久删除回收站中的分类记录时清理全部关联图标和封面图文件

## 4. 实现决策

> **上下文**：REQ-107 是第三个 `RecycleBinItemStrategy` 实现。与前两个（REQ-104/105 单聚合根删除）不同，知识分类是**树形结构**（`parent_id` 自引用），删除根节点时需递归处理整个子树。这是首次处理"聚合根含子集合"的回收站策略。

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | 策略 Bean 位置 | core 模块 `infrastructure/adapter/repoadapter/` | 遵循 REQ-104/105 建立的一致模式 |
| 2 | Bean 注册方式 | admin 模块 `RecycleBinConfig` 显式 `@Bean` 注册 | 遵循 REQ-104/105 模式；需 `FileCleanupPort` |
| 3 | 删除策略 | **递归删除整个子树**（父分类 + 全部子孙） | 替代 REQ-100 原设计"拒绝删除有子分类的父分类"；用户要求一步操作 |
| 4 | 删除前校验 | 策略内联校验：收集子树全部节点 ID → 逐节点调 `questionRepository.countActiveByCategoryId()` + `itemRepository.countActiveByCategoryId()`。**不委托 `KnowledgeCategoryDomainService.validateDelete()`**，因为该方法第一步检查 `countActiveByParentId > 0`，在递归删除场景下父节点的子节点正是要一起删的，该检查必然为 true 会误抛异常 | 方案 B：策略直接持有 `QuestionRepository` 和 `KnowledgeItemRepository`，跳过与递归删除冲突的 `countActiveByParentId` 子分类检查。子树节点全部已知且即将一起删除，无需检查父节点下是否有子分类 |
| 5 | 校验粒度 | **逐节点独立校验，全部通过才允许继续**。不采用"跳过有问题的节点仅删除可通过的" | 保证数据完整性；部分成功会导致树结构断裂 |
| 6 | 快照元信息 | 主节点 `related_data` JSON：`{"subtreeOriginalIds":[2,3,5,7]}` 记录子树全部节点的 `original_id`。子节点快照的 `related_data` 为 null | 恢复时批量查所有快照即可重建树；子节点通过 `parent_id` 字段确定层级关系，不需要额外 JSON |
| 7 | 总览表记录 | 整棵子树写**一条**总览记录（`originalName`=根分类名） | 整棵树作为一个回收单元，恢复/永久删除均整棵树操作 |
| 8 | 恢复后状态 | 强制 INACTIVE | REQ-100 已固化的契约 |
| 9 | 恢复排序 | 按原始 `parentId` 拓扑排序：顶级（`parentId=null`）→ 二级 → 三级... | 不按此顺序插入会在恢复子节点时父级尚未存在，DB 无 FK 约束但数据完整性受损 |
| 10 | 恢复名称冲突校验 | 恢复前校验：同一父级下 `name` 不冲突（含 ACTIVE 和 INACTIVE 记录）。同一子树内若同名同父级（原本就存在）则警告但允许恢复 | 名称唯一约束范围是 `(parentId, name)`，与创建逻辑一致 |
| 11 | 恢复实现方式 | 使用 native SQL `INSERT INTO knowledge_category (id, parent_id, ...)` 逐条插入，按深度排序 | 遵循 REQ-104/105 模式；保留原始 ID |
| 12 | 事务 | 由调用方 `KnowledgeCategoryAppService.delete()` 的 `@Transactional` 保证原子性 | 策略接口契约 |
| 13 | 文件清理 | `FileCleanupPort.deleteFile()` 清理 icon + coverImage，每个节点两份。容错（catch 后仅 log.warn） | 遵循 REQ-104/105 模式 |
| 14 | deletedBy 来源 | `SecurityUtils.getCurrentUsername()` | REQ-100 决策 #8 |

### 与 REQ-104/105 的关键差异

| 维度 | REQ-104/105 | REQ-107 |
|------|------------|---------|
| 聚合根结构 | 单表，无自引用 | **树形 `parent_id` 自引用** |
| 删除范围 | 单行 | **递归整棵子树** |
| 校验范围 | 单节点 | **子树全部节点** |
| 快照数 | 1 条 `_deleted` 行 | **N 条**（子树节点数） |
| 总览记录 | 1 条 | **1 条**（代表整棵子树） |
| 恢复顺序 | 无要求 | **按深度拓扑排序** |
| 文件清理 | 1 张图片 | **每节点 2 张**（icon + coverImage） |
| `related_data` | null | **主节点存 `subtreeOriginalIds`** |

### 策略依赖注入清单

```
KnowledgeCategoryRecycleBinStrategy 注入：
  ├── KnowledgeCategoryRepositoryPort     （findById / findDescendantIds / existsByNameAndParentId）
  ├── QuestionRepository                  （countActiveByCategoryId — validateDeletable 逐节点校验）
  ├── KnowledgeItemRepository             （countActiveByCategoryId — validateDeletable 逐节点校验）
  ├── RecycleBinItemRepositoryPort        （findById 读 RecycleBinItem）
  ├── KnowledgeCategoryJpaRepository      （deleteAllById）
  ├── KnowledgeCategoryDeletedJpaRepository（saveAll / findByOriginalId / findAllByOriginalIdIn / deleteAllById）
  ├── RecycleBinItemJpaRepository         （save / deleteById）
  ├── FileCleanupPort                     （deleteFile — purge 文件清理）
  └── EntityManager                       （@PersistenceContext，restore 时 native SQL INSERT）
```

### 策略方法伪代码

```
validateDeletable(originalId):
    category = categoryRepositoryPort.findById(originalId)
        .orElseThrow(() -> new BusinessException("知识点分类不存在: " + originalId))
    subtreeIds = categoryRepositoryPort.findDescendantIds(originalId) + [originalId]
    // 逐节点校验题目和条目关联（跳过子分类检查，因为子树节点将一起删除）
    for each id in subtreeIds:
        activeQuestionCount = questionRepository.countActiveByCategoryId(id)
        if activeQuestionCount > 0:
            throw new BusinessException("知识点分类关联 " + activeQuestionCount + " 道 ACTIVE 题目，无法删除")
        activeItemCount = itemRepository.countActiveByCategoryId(id)
        if activeItemCount > 0:
            throw new BusinessException("知识点分类关联 " + activeItemCount + " 个 ACTIVE 知识条目，无法删除")

moveToRecycleBin(originalId, deletedBy):
    root = categoryRepositoryPort.findById(originalId)
        .orElseThrow(() -> new BusinessException("知识点分类不存在: " + originalId))
    subtreeIds = categoryRepositoryPort.findDescendantIds(originalId) + [originalId]

    // 批量加载全部节点 domain 对象
    allCategories = categoryRepositoryPort.findAllByIdIn(subtreeIds)

    // 批量写快照
    for each category in allCategories:
        deletedPO = KnowledgeCategoryDeletedPO from category fields
            + relatedData = (category.id == originalId)
                ? {"subtreeOriginalIds": subtreeIds}
                : null
            + deletedBy = deletedBy
            + deletedAt = now()
    knowledgeCategoryDeletedJpaRepository.saveAll(deletedPOs)

    // 物理删除原表子树全部行
    knowledgeCategoryJpaRepository.deleteAllById(subtreeIds)

    // 总览表登记一条记录
    recycleBinItemJpaRepository.save(RecycleBinItemPO(
        resourceType=KNOWLEDGE_CATEGORY, originalId=originalId,
        originalName=root.getName(), ..., restoreDeadline=now().plusDays(30)
    ))

restore(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    rootDeletedPO = knowledgeCategoryDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
        .orElseThrow(() -> new BusinessException("分类快照不存在: " + recycleBinItem.getOriginalId()))

    // 从 related_data JSON 解析子树 ID 列表 → 批量查全部快照
    // relatedData 在数据库中是 String（MySQL json 列），用 Jackson ObjectMapper 读写：
    //   写入：new ObjectMapper().writeValueAsString(Map.of("subtreeOriginalIds", subtreeIds))
    //   读取：new ObjectMapper().readValue(po.getRelatedData(), new TypeReference<>(){})
    subtreeIds = parseRelatedData(rootDeletedPO)  // Jackson readValue → List<Long>
    allDeletedPOs = knowledgeCategoryDeletedJpaRepository.findAllByOriginalIdIn(subtreeIds)

    // 恢复前校验：同名同父级不冲突
    for each deletedPO in allDeletedPOs:
        if categoryRepositoryPort.existsByNameAndParentId(deletedPO.getName(), deletedPO.getParentId()):
            throw new BusinessException("目标父级下已存在同名分类: " + deletedPO.getName())

    // 按 parentId 拓扑排序：顶级 → 二级 → 三级 ...
    sortedPOs = topologicalSort(allDeletedPOs)

    // 逐条 native INSERT（保留原始 ID、强制 INACTIVE）
    for each po in sortedPOs:
        entityManager.createNativeQuery(
            "INSERT INTO knowledge_category (id, parent_id, name, description, "
                + "icon_file_id, icon_url, color, cover_image_file_id, cover_image_url, "
                + "sort_order, status, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
            .setParameter(1, po.getOriginalId())...status=INACTIVE...
            .executeUpdate()

    // 删全部快照 + 总览记录
    knowledgeCategoryDeletedJpaRepository.deleteAllById(deletedPOIds)
    recycleBinItemJpaRepository.deleteById(recycleBinId)

purge(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    rootDeletedPO = knowledgeCategoryDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
    if rootDeletedPO == null:
        recycleBinItemJpaRepository.deleteById(recycleBinId)
        return
    subtreeIds = parseRelatedData(rootDeletedPO)  // Jackson readValue
    allDeletedPOs = knowledgeCategoryDeletedJpaRepository.findAllByOriginalIdIn(subtreeIds)

    // 逐节点清理文件（icon + coverImage）
    for each po in allDeletedPOs:
        for fileId in [po.getIconFileId(), po.getCoverImageFileId()]:
            if fileId != null:
                try fileCleanupPort.deleteFile(fileId)
                catch log.warn

    knowledgeCategoryDeletedJpaRepository.deleteAllById(deletedPOIds)
    recycleBinItemJpaRepository.deleteById(recycleBinId)
```

## 5. 影响分析

### 新建文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `core/.../infrastructure/adapter/repoadapter/KnowledgeCategoryRecycleBinStrategy.java` | core | 策略 Bean，实现 `RecycleBinItemStrategy<KnowledgeCategory>` |
| `core/.../infrastructure/adapter/repoadapter/KnowledgeCategoryRecycleBinStrategyTest.java` | core | 策略集成测试（`@DataJpaTest` + 真实 MySQL） |
| `admin/.../application/service/recyclebin/KnowledgeCategoryRecycleBinStrategyBlackBoxTest.java` | admin | 策略黑盒测试（纯 Mockito） |

### 修改文件

| 文件 | 模块 | 变更 |
|------|------|------|
| `admin/.../application/service/KnowledgeCategoryAppService.java` | admin | `delete()` 改为委托策略；注入策略 Bean |
| `admin/.../config/RecycleBinConfig.java` | admin | 新增 `knowledgeCategoryRecycleBinStrategy` `@Bean` 注册 |
| `core/.../infrastructure/db/repository/KnowledgeCategoryDeletedJpaRepository.java` | core | 新增 `findByOriginalId(Long)` + `findAllByOriginalIdIn(List<Long>)` |
| `core/.../domain/port/outbound/KnowledgeCategoryRepositoryPort.java` | core | 无变更（`findDescendantIds` / `findAllByIdIn` / `existsByNameAndParentId` 均已存在） |
| `frontend/admin/src/pages/Category/index.tsx` | admin 前端 | 删除按钮改为弹窗警告子分类数量 |

### 配置变更

`admin` 模块 `RecycleBinConfig` 新增 `@Bean` 注册（无其他配置变更）。

### 受影响功能

| 功能 | 影响 | 风险 |
|------|------|------|
| `DELETE /api/admin/knowledge-categories/{id}` | 行为变更：从 deactivate 改为递归移入回收站 | 中：删除范围从单节点扩展到整棵子树，前端需警告 |
| 回收站管理端列表页 | KNOWLEDGE_CATEGORY 类型出现在目录树和列表中 | 低：REQ-100 已做空集兼容 |
| `POST /api/admin/recycle-bin/{id}/restore` | 支持恢复整棵分类树 | 低：REQ-103 框架已就位 |
| `DELETE /api/admin/recycle-bin/{id}` | 支持永久删除整棵分类树 | 低：REQ-102 框架已就位 |
| 分类树结构 | 删除后释放 `(parentId, name)` 唯一键 | 正面改进 |

### 向前兼容检查

| 未来需求 | 影响 | 约束 |
|---------|------|------|
| REQ-106（题库对接回收站） | Question 恢复时需校验关联分类仍存在 | REQ-107 先做，Question 恢复校验逻辑复用 `KnowledgeCategoryRepositoryPort.existsById()` 模式 |
| REQ-108（知识条目对接回收站） | KnowledgeItem 恢复时需校验关联分类仍存在 | 同上 |
| REQ-51（群组关联 IP 库） | 无影响 | — |

## 6. 验证计划

### 手工验证步骤

1. **删除成功（叶子节点）**：`DELETE /api/admin/knowledge-categories/{id}`（无子分类、无关联题目/条目）→ 返回 200
   - 验证 `knowledge_category` 表该行已删除
   - 验证 `knowledge_category_deleted` 表有新快照行
   - 验证 `recycle_bin` 表有新记录

2. **删除成功（父分类+子分类）**：`DELETE /api/admin/knowledge-categories/{parentId}`（有 3 个子分类，全部无关联项）→ 返回 200
   - 验证 `knowledge_category` 表 4 行全删除
   - 验证 `knowledge_category_deleted` 表 4 条快照，主节点 `related_data` 含 4 个 ID
   - 验证 `recycle_bin` 表 1 条记录

3. **删除被拒（有关联题目）**：父分类下某子分类关联 ACTIVE 题目 → 返回 400，消息含分类名和题目数

4. **删除被拒（有关联条目）**：父分类下某子分类关联 ACTIVE 知识条目 → 返回 400

5. **恢复成功**：恢复含子分类的树 → 返回 200
   - 验证所有节点恢复，status=INACTIVE
   - 验证树结构正确（parent_id 关系不变）
   - 验证快照表和总览表清理

6. **恢复被拒（名称冲突）**：恢复后创建同名同父级分类 → 删除 → 再恢复 → 报名称冲突

7. **永久删除**：`DELETE /api/admin/recycle-bin/{id}` → 验证全部快照删除、总览记录删除

### 自动化测试覆盖

| 测试类 | 类型 | 覆盖目标 |
|--------|------|---------|
| `KnowledgeCategoryRecycleBinStrategyTest` | 集成（`@DataJpaTest`） | validateDeletable(3) + moveToRecycleBin(3) + restore(5) + purge(4) = ~15 用例 |
| `KnowledgeCategoryRecycleBinStrategyBlackBoxTest` | 单元（Mockito） | 恢复 INACTIVE 覆盖 + 深度排序 + 并发删除静默处理 |
| `KnowledgeCategoryAppServiceTest` | 单元（Mockito） | delete 委托策略 + 不存在抛异常 |

### 回滚标准

- 分类树删除后数据结构异常（孤儿节点、断裂父子关系）
- 恢复后树结构与删除前不一致
- 批量操作（batchRestore/batchPurge）中 KNOWLEDGE_CATEGORY 类型处理异常

## 7. 测试策略

### 测试类型选择

策略测试使用 `@DataJpaTest` 集成测试（真实 MySQL）。核心验证点：
1. 子树批量快照是否正确写入
2. 恢复时的拓扑排序是否正确（父先子后）
3. related_data JSON 序列化/反序列化
4. 多节点文件清理是否完整

测试注解模板：
```java
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({KnowledgeCategoryRecycleBinStrategy.class,
         KnowledgeCategoryRepositoryAdapter.class,
         QuestionRepositoryAdapter.class,
         KnowledgeItemRepositoryAdapter.class,
         RecycleBinItemRepositoryAdapter.class})
@ActiveProfiles("test")
class KnowledgeCategoryRecycleBinStrategyTest { ... }
```

策略直接注入 `QuestionRepository` 和 `KnowledgeItemRepository` 做内联校验（方案 B），需 `@Import` 对应 Adapter。`FileCleanupPort` 使用 `@MockBean` mock。

### 测试用例

| # | 方法 | 场景 | 预期 |
|---|------|------|------|
| 1 | `validateDeletable` | 叶子节点，无关联 | 正常返回 |
| 2 | `validateDeletable` | 父分类+子分类，全部无关联 | 正常返回 |
| 3 | `validateDeletable` | 子树中某节点有关联题目 | `BusinessException`，含分类名和数量 |
| 4 | `validateDeletable` | 子树中某节点有关联条目 | `BusinessException` |
| 5 | `validateDeletable` | 分类不存在 | `BusinessException` |
| 6 | `moveToRecycleBin` | 叶子节点正常路径 | 原表删 1 行 + deleted 1 条快照 + recycle_bin 1 条 |
| 7 | `moveToRecycleBin` | 父分类+3 子分类 | 原表删 4 行 + deleted 4 条快照 + recycle_bin 1 条 + 主快照 `related_data.subtreeOriginalIds` = 4 |
| 8 | `moveToRecycleBin` | 分类不存在 | `BusinessException` |
| 9 | `restore` | 叶子节点 | 行恢复(INACTIVE) + 快照删 + 回收站删 |
| 10 | `restore` | 父分类+3 子分类树 | 全部恢复，树结构正确，全部 INACTIVE |
| 11 | `restore` | 同名同父级冲突 | `BusinessException`，含分类名 |
| 12 | `restore` | 回收站记录不存在 | `BusinessException` |
| 13 | `restore` | 主快照不存在 | `BusinessException` |
| 14 | `purge` | 叶子节点无图片 | deleted + recycle_bin 均删除 |
| 15 | `purge` | 树含 icon + coverImage | 调 `fileCleanupPort.deleteFile()` 每文件一次，然后清理 DB |
| 16 | `purge` | FileCleanupPort 抛异常 | catch 后仅 log.warn，DB 清理不受影响 |
| 17 | `purge` | 回收站记录不存在 | `BusinessException` |

## 8. 不在范围

- 前端回收站页面改动：无需改动（已由 REQ-100/102/103 交付）
- 级联删除题目或知识条目（仅校验拒绝，不级联）
- 回收站定时清理（REQ-101）
- Question/KnowledgeItem 对接回收站（REQ-106/108）
