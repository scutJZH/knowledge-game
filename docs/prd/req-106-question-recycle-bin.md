# REQ-106：题库管理对接回收站

> 状态：`confirmed`
> 创建：2026-06-24
> 前置依赖：REQ-100 ✅, REQ-102 ✅, REQ-103 ✅, REQ-09 ✅

## 前置依赖

本需求基于 [REQ-100 通用回收站系统](./req-100-recycle-bin.md) 的框架实现。复用契约：
- `RecycleBinItemStrategy<T>` 接口（详见 REQ-100 第 4.1.4 节）
- `RecycleBinItemStrategyRegistry` 注册中心（自动发现策略 Bean）
- `question_deleted` 详情表 PO + JPA Repository（详见 REQ-100 第 3.2 节）
- `ResourceType.QUESTION` 枚举与 `toBizTypes()` → `["QUESTION"]` 映射（详见 REQ-100 第 4.1.2 节）
- `RecycleBinAppService` 的 restore/purge/batchRestore/batchPurge 框架（REQ-102/103 已完成）

本需求的增量工作：
- 创建 `QuestionRecycleBinStrategy` 策略 Bean
- 修改 `QuestionAppService.delete()` 从 deactivate 改为移入回收站
- `QuestionDeletedJpaRepository` 新增 `findByOriginalId` / `findAllByOriginalIdIn` 查询方法

区别于 REQ-104/105：Question **无图片字段**，purge 无需文件清理，策略不注入 `FileCleanupPort`。区别于 REQ-107：Question 无递归子树，无需拓扑排序恢复。

### 框架集成接缝：三条完整调用链

**链路 1 — DELETE（REQ-106 专属）**

```
DELETE /api/admin/questions/{id}
  → QuestionController.delete(id)
    → QuestionAppService.delete(id)                       [@Transactional]
      → SecurityUtils.getCurrentUsername()
      → strategy.validateDeletable(id)                    ← REQ-106 实现
      → strategy.moveToRecycleBin(id, deletedBy)          ← REQ-106 实现
        ├── questionRepository.findById()                  读 Domain
        ├── relationJpaRepository.findByQuestionId()       查分类关联
        ├── questionDeletedJpaRepository.save()            写快照（related_data = {"categoryAssociationIds": [...]})
        ├── relationJpaRepository.deleteByQuestionId()     删分类关联
        ├── questionJpaRepository.deleteById()             物理删原表
        └── recycleBinItemJpaRepository.save()             总览表登记
```

**链路 2 — 恢复（REQ-103 框架 + REQ-106 策略）**

```
POST /api/admin/recycle-bin/{id}/restore
  → RecycleBinController.restore(id)
    → RecycleBinAppService.restore(id)
      → strategyRegistry.get(QUESTION)
          .restore(item.getId())                          ← REQ-106 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── questionDeletedJpaRepository.findByOriginalId()
            ├── native INSERT question                     保留原始 ID（INACTIVE）
            ├── saveCategoryRelations                      恢复分类关联
            ├── questionDeletedJpaRepository.deleteById()
            └── recycleBinItemJpaRepository.deleteById()
```

**链路 3 — 永久删除（REQ-102 框架 + REQ-106 策略）**

```
DELETE /api/admin/recycle-bin/{id}
  → RecycleBinController.purge(id)
    → RecycleBinAppService.purge(id)
      → strategyRegistry.get(QUESTION)
          .purge(item.getId())                            ← REQ-106 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── questionDeletedJpaRepository.findByOriginalId()
            ├── questionDeletedJpaRepository.deleteById()  无文件清理
            └── recycleBinItemJpaRepository.deleteById()
```

## 1. 问题陈述

当前 `DELETE /api/admin/questions/{id}` 的行为是调用 `Question.deactivate()` 将状态设为 INACTIVE。INACTIVE 同时承担了「停用」和「删除」两种语义，导致：

1. 管理员无法区分"暂时停用"和"真正删除"
2. 没有误删恢复机制
3. 题目关联的分类信息丢失（软删除后仍可通过 GET 接口查看分类，但分类可能已被修改）

## 2. 解决方案

将 DELETE 与 INACTIVE 解耦：
- DELETE 端点改为物理删除原表行 + 分类关联 + 写入回收站快照（30 天保留期）
- INACTIVE 保留为"停用"语义（通过 PUT update 接口切换 status）

具体动作：
- 创建 `QuestionRecycleBinStrategy` 实现 `RecycleBinItemStrategy<Question>`
- 删除前校验：验证题目存在
- 移入回收站：快照所有字段到 `question_deleted`，快照分类关联到 `related_data` JSON，删分类关联，物理删 `question` 行，总览表登记
- 恢复：从快照重建（native INSERT），强制 INACTIVE，恢复分类关联
- 永久删除：物理清理快照 + 回收站记录（无文件清理，Question 无图片字段）

## 3. 用户故事

1. 作为系统管理员，我想要删除题目时它进入回收站而非仅仅停用，以便我能恢复误删的数据
2. 作为系统管理员，我想要从回收站恢复已删除的题目，恢复后状态为 INACTIVE，分类关联一并恢复，以便手动确认后重新启用
3. 作为系统管理员，我想要永久删除回收站中的题目记录，以便释放数据库空间

## 4. 实现决策

> **上下文**：REQ-106 是第四个 `RecycleBinItemStrategy` 实现。Question 是最简单的聚合根——无图片字段、无父子层级、无唯一性约束。仅有的特殊处理是分类关联（M:N → `question_category_relation`）需在 `related_data` JSON 中快照。

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | 策略 Bean 位置 | core 模块 `infrastructure/adapter/repoadapter/` | 遵循现有模式 |
| 2 | Bean 注册方式 | admin 模块 `RecycleBinConfig` 显式 `@Bean` 注册 | 遵循现有模式；策略操作 JPA Repository，属于 infrastructure 层 |
| 3 | 持久化方式 | 读操作走 Port 接口，写/删操作走 JPA Repository | 遵循 REQ-100/104 确立的规则 |
| 4 | 删除前校验 | 仅验证题目存在（`questionRepository.findById()`） | Question 无下游依赖需校验 |
| 5 | 恢复后状态 | 强制 INACTIVE | REQ-100 已固化的契约 |
| 6 | 恢复时校验 | 无额外校验 | Question 无 name/code 唯一性约束，无 IP 系列等外键依赖 |
| 7 | 恢复实现方式 | native SQL `INSERT INTO question (id, type, content, options, answer, explanation, difficulty, tags, status, created_at, updated_at) VALUES (...)` | 保留原始 ID，JPA IDENTITY 策略不支持显式 ID |
| 8 | 分类关联快照 | `related_data` = `{"categoryAssociationIds": [1, 2, 3]}` | 通过 `relationJpaRepository.findByQuestionId()` 获取原始关联（不限制 ACTIVE） |
| 9 | 分类关联恢复 | 调 `questionRepository.saveCategoryRelations(questionId, categoryIds)` | 复用已有 Port 方法，内部 delete + insert |
| 10 | 事务 | 由调用方 `QuestionAppService.delete()` 的 `@Transactional` 保证原子性 | 策略接口契约"原子性由调用方保证" |
| 11 | 文件清理 | 不需要 `FileCleanupPort` | Question 无任何 FileRef 字段（content/options/answer/explanation/tags 均为 JSON/TEXT） |
| 12 | deletedBy 来源 | `SecurityUtils.getCurrentUsername()` | 遵循 REQ-100 决策 #8，在 `QuestionAppService.delete()` 中获取后传给策略 |

### 与已有策略的关键差异

| 维度 | REQ-104 IpSeries | REQ-105 CardTemplate | REQ-107 Category | **REQ-106 Question** |
|------|:---:|:---:|:---:|:---:|
| FileCleanupPort | ✓ | ✓ | ✓ | **✗** |
| 递归子树 | ✗ | ✗ | ✓ | **✗** |
| related_data JSON | ✗ | ✓ (ipSeriesName) | ✓ (subtreeIds) | **✓ (categoryIds)** |
| 恢复后校验 | ✗ | ✓ (IP存在+code冲突) | ✓ (同名冲突) | **✗** |
| 恢复后关联恢复 | ✗ | ✗ | ✗ | **✓ (分类关联)** |
| 构造函数依赖数 | 7 | 8 | 8 | **7** |

### 策略依赖注入清单

```
QuestionRecycleBinStrategy 注入：
  ├── QuestionRepository                     （findById — 读 Domain 对象）
  ├── RecycleBinItemRepositoryPort           （findById — 读 RecycleBinItem）
  ├── QuestionJpaRepository                  （deleteById — 物理删原表）
  ├── QuestionDeletedJpaRepository           （save / findByOriginalId / findAllByOriginalIdIn / deleteById）
  ├── QuestionCategoryRelationJpaRepository  （findByQuestionId / deleteByQuestionId）
  ├── RecycleBinItemJpaRepository            （save / deleteById）
  └── EntityManager                          （@PersistenceContext 注入，restore 时 native SQL INSERT）
```

### 策略方法伪代码

```
validateDeletable(originalId):
    if not questionRepository.findById(originalId).isPresent():
        throw new BusinessException("题目不存在: " + originalId)

moveToRecycleBin(originalId, deletedBy):
    question = questionRepository.findById(originalId)
        .orElseThrow(() -> new BusinessException("题目不存在: " + originalId))
    relations = relationJpaRepository.findByQuestionId(originalId)
    categoryIds = relations.stream().map(QuestionCategoryRelationPO::getCategoryId).toList()
    deletedPO = QuestionDeletedPO from question fields
        + relatedData = writeJson({"categoryAssociationIds": categoryIds})
        + deletedBy = deletedBy, deletedAt = now()
    questionDeletedJpaRepository.save(deletedPO)
    relationJpaRepository.deleteByQuestionId(originalId)
    questionJpaRepository.deleteById(originalId)
    recycleBinItemJpaRepository.save(RecycleBinItemPO(
        resourceType=QUESTION, originalId=...,
	        originalName=content.length() > 100 ? content.substring(0, 100) : content,
        originalCreatedAt=..., originalUpdatedAt=..., deletedBy=..., deletedAt=now(),
        restoreDeadline=now().plusDays(30)
    ))

restore(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = questionDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
        .orElseThrow(() -> new BusinessException("题目快照不存在: " + recycleBinItem.getOriginalId()))
    entityManager.createNativeQuery(
        "INSERT INTO question (id, type, content, options, answer, explanation, "
            + "difficulty, tags, status, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, deletedPO.getOriginalId())
        ...status = INACTIVE, createdAt保留原值, updatedAt=now()
        .executeUpdate()
    categoryIds = parseJson(deletedPO.getRelatedData()).get("categoryAssociationIds")
    questionRepository.saveCategoryRelations(deletedPO.getOriginalId(),
        categoryIds != null ? categoryIds : Collections.emptyList())
    questionDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)

purge(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = questionDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
    if deletedPO == null:
        recycleBinItemJpaRepository.deleteById(recycleBinId)
        return
    questionDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)
```

## 5. 影响分析

### 新建文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `core/.../infrastructure/adapter/repoadapter/QuestionRecycleBinStrategy.java` | core | 策略 Bean，实现 `RecycleBinItemStrategy<Question>` |

### 修改文件

| 文件 | 模块 | 变更 |
|------|------|------|
| `admin/.../application/service/QuestionAppService.java` | admin | `delete()` 改为委托策略（validateDeletable + moveToRecycleBin）；注入策略 Bean |
| `admin/.../config/RecycleBinConfig.java` | admin | 新增 `questionRecycleBinStrategy` `@Bean` 注册 |
| `core/.../infrastructure/db/repository/QuestionDeletedJpaRepository.java` | core | 新增 `findByOriginalId(Long)` / `findAllByOriginalIdIn(List<Long>)` 查询方法 |

### 不需要修改

- `QuestionDeletedPO` — REQ-100 已建表，字段完整
- `QuestionController` — DELETE 端点不变，HTTP 方法相同；Javadoc 注释建议从「软删除」改为「删除题目（移入回收站）」，与 REQ-104/105/107 对齐
- `RecycleBinAppService` — 框架已就位（REQ-102/103）
- 前端回收站页面 — 通过 `supported-types` API 自动显示 QUESTION 类型
- 前端题库管理页面 — DELETE 调用不变

### 受影响功能

| 功能 | 影响 | 风险 |
|------|------|------|
| `DELETE /api/admin/questions/{id}` | 行为变更：从 deactivate 改为移入回收站 | 前端调用方无需改动 |
| 回收站管理端列表页 | QUESTION 类型出现在目录树和列表中 | 低风险，REQ-100 已做空集兼容 |
| `POST /api/admin/recycle-bin/{id}/restore` | 支持恢复题目（含分类关联） | 低风险，REQ-103 框架已就位 |
| `DELETE /api/admin/recycle-bin/{id}` | 支持永久删除题目 | 低风险，REQ-102 框架已就位 |

## 6. 验证计划

### 手工验证步骤

1. **删除成功**：`DELETE /api/admin/questions/{id}` → 返回 200
   - 验证 `question` 表该行已删除
   - 验证 `question_category_relation` 表该题关联已删除
   - 验证 `question_deleted` 表有新快照行（含所有题目字段 + `related_data` JSON 含分类关联 ID）
   - 验证 `recycle_bin` 表有新记录（resource_type=QUESTION）
   - 验证管理端回收站页面可见该记录

2. **删除被拒**：`DELETE /api/admin/questions/{id}`（不存在的 ID）→ 返回 400 "题目不存在: X"

3. **恢复成功**：`POST /api/admin/recycle-bin/{id}/restore` → 返回 200
   - 验证 `question` 表行恢复，status=INACTIVE，createdAt 保留原值
   - 验证 `question_category_relation` 表分类关联已恢复
   - 验证 `question_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除

4. **永久删除**：`DELETE /api/admin/recycle-bin/{id}` → 返回 200
   - 验证 `question_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除

5. **恢复无分类关联的题目**：删除一道无分类关联的题目 → 恢复 → 验证恢复后无分类关联，related_data 为 `{"categoryAssociationIds": []}`

6. **恢复后手动启用**：恢复题目后 → PUT update status=ACTIVE → 验证题目可正常使用

### 自动化测试覆盖

| 测试类 | 类型 | 模块 | 覆盖目标 |
|--------|------|------|---------|
| `QuestionRecycleBinStrategyTest` | 集成（`@DataJpaTest`） | core | validateDeletable(2) + moveToRecycleBin(3) + restore(4) + purge(3) = 12 用例 |
| `QuestionRecycleBinStrategyBlackBoxTest` | 单元（Mockito） | admin | 恢复 INACTIVE 覆盖 + 循环删除恢复 + 并发删快照静默跳过 + 分类关联恢复 = 5 用例 |
| `QuestionAppServiceTest` | 单元（Mockito） | admin | delete 委托策略 + 不存在抛异常（修改已有 2 用例） |

### 测试用例明细

| # | 方法 | 场景 | 预期 |
|---|------|------|------|
| 1 | `validateDeletable` | 题目存在 | 正常返回 |
| 2 | `validateDeletable` | 题目不存在 | `BusinessException("题目不存在: X")` |
| 3 | `moveToRecycleBin` | 有分类关联 | question 行删除 + 关联删除 + deleted 有快照（related_data 含 categoryIds）+ recycle_bin 有记录 |
| 4 | `moveToRecycleBin` | 无分类关联 | 同上，related_data = `{"categoryAssociationIds": []}` |
| 5 | `moveToRecycleBin` | 题目不存在 | `BusinessException("题目不存在: X")` |
| 6 | `restore` | 有分类关联 | 行恢复(INACTIVE) + 分类关联恢复 + 快照删除 + 回收站删除；createdAt 保留原值 |
| 7 | `restore` | 无分类关联 | 同上，无关联恢复 |
| 8 | `restore` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |
| 9 | `restore` | 快照不存在 | `BusinessException("题目快照不存在: X")` |
| 10 | `purge` | 正常路径 | deleted + recycle_bin 均删除 |
| 11 | `purge` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |
| 12 | `purge` | 快照已被并发删除 | 静默跳过，仅删 recycle_bin |

## 7. 测试策略

### 测试类型选择

策略测试使用 `@DataJpaTest` 集成测试（真实 MySQL），因为策略直接操作多个 JPA Repository，Mock 无法覆盖 flush 顺序、唯一约束、自增 ID 等 JPA 行为。

测试注解模板：
```java
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuestionRecycleBinStrategy.class,
         QuestionRepositoryAdapter.class,
         RecycleBinItemRepositoryAdapter.class})
@ActiveProfiles("test")
class QuestionRecycleBinStrategyTest { ... }
```

策略依赖两个 Port 接口（`QuestionRepository`、`RecycleBinItemRepositoryPort`），需 `@Import` 对应的 Adapter 实现。Question 无 `FileCleanupPort`，无需 `@MockBean`。

## 8. 不在范围

- 前端页面改动：无需改动，回收站页面由 REQ-100 交付，通过 `supported-types` API 自动显示 QUESTION 类型
- 批量删除题目入回收站（当前仅有单条 DELETE 端点）
- 题目关联的知识条目（Question 与 KnowledgeItem 无直接关联）
- 回收站定时清理（REQ-101 已实现）
