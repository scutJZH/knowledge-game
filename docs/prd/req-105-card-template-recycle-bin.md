# REQ-105：卡牌管理对接回收站

> 状态：`confirmed`
> 创建：2026-06-22
> 前置依赖：REQ-100 ✅, REQ-102 ✅, REQ-103 ✅, REQ-17 ✅, REQ-104 ✅

## 前置依赖

本需求基于 [REQ-100 通用回收站系统](./req-100-recycle-bin.md) 的框架实现。复用契约：
- `RecycleBinItemStrategy<T>` 接口（详见 REQ-100 第 4.1.4 节）
- `RecycleBinItemStrategyRegistry` 注册中心（自动发现策略 Bean）
- `card_template_deleted` 详情表 PO + JPA Repository（详见 REQ-100 第 3.2.2 节）
- `ResourceType.CARD_TEMPLATE` 枚举与 `toBizTypes()` → `["CARD_TEMPLATE"]` 映射（详见 REQ-100 第 4.1.2 节）
- `RecycleBinAppService` 的 restore/purge/batchRestore/batchPurge 框架（REQ-102/103 已完成）
- `FileCleanupPort` 端口（core 定义）+ `FileCleanupAdapter`（admin 实现），用于 purge 时文件清理，解耦 core 与 component-feign（REQ-104 建立此模式）

本需求的增量工作：
- 创建 `CardTemplateRecycleBinStrategy` 策略 Bean
- 修改 `CardTemplateAppService.deleteCardTemplate()` 从 deactivate 改为移入回收站
- `CardTemplateDeletedJpaRepository` 新增 `findByOriginalId` 查询方法

### 框架集成接缝：三条完整调用链

REQ-105 只需要实现策略的四个方法（`validateDeletable` / `moveToRecycleBin` / `restore` / `purge`），其余流程由已完成的 REQ-100/102/103 框架处理。

**链路 1 — DELETE（REQ-105 专属，不经过 RecycleBinAppService）**

```
DELETE /api/admin/card-templates/{id}
  → CardTemplateController.delete(id)
    → CardTemplateAppService.deleteCardTemplate(id)     [@Transactional]
      → SecurityUtils.getCurrentUsername()               （REQ-100 决策 #8）
      → strategy.validateDeletable(id)                  ← REQ-105 实现
      → strategy.moveToRecycleBin(id, deletedBy)        ← REQ-105 实现
        ├── cardTemplateRepositoryPort.findById()        读 Domain（Port 已有）
        ├── cardTemplateDeletedJpaRepository.save()      写快照（REQ-100 建表）
        ├── cardTemplateJpaRepository.deleteById()       物理删原表
        └── recycleBinItemJpaRepository.save()           总览表登记（REQ-100 建表）
```

**链路 2 — 恢复（REQ-103 框架 + REQ-105 策略）**

```
POST /api/admin/recycle-bin/{id}/restore
  → RecycleBinController.restore(id)                    [REQ-100 定义端点]
    → RecycleBinAppService.restore(id)                  [REQ-103 实现]
      → recycleBinRepository.findById(id)
      → self.restoreInNewTransaction(item)              [@Transactional(REQUIRES_NEW)]
        → strategyRegistry.get(CARD_TEMPLATE)           注册中心分派 → 返回 CardTemplateRecycleBinStrategy
          .restore(item.getId())                        ← REQ-105 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── cardTemplateDeletedJpaRepository.findByOriginalId()
            ├── ipSeriesRepositoryPort.existsById()     校验 IP 系列仍存在
            ├── cardTemplateJpaRepository.findByIpSeriesIdAndCode()  校验唯一性
            ├── native INSERT card_template             保留原始 ID（INACTIVE）
            ├── cardTemplateDeletedJpaRepository.deleteById()
            └── recycleBinItemJpaRepository.deleteById()
```

**链路 3 — 永久删除（REQ-102 框架 + REQ-105 策略）**

```
DELETE /api/admin/recycle-bin/{id}
  → RecycleBinController.purge(id)                      [REQ-100 定义端点]
    → RecycleBinAppService.purge(id)                    [REQ-102 实现]
      → recycleBinRepository.findById(id)
      → self.purgeInNewTransaction(item)                [@Transactional(REQUIRES_NEW)]
        → strategyRegistry.get(CARD_TEMPLATE)           注册中心分派 → 返回 CardTemplateRecycleBinStrategy
          .purge(item.getId())                          ← REQ-105 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── cardTemplateDeletedJpaRepository.findByOriginalId()
            ├── fileCleanupPort.deleteFile()             清理卡牌图片（容错）
            ├── cardTemplateDeletedJpaRepository.deleteById()
            └── recycleBinItemJpaRepository.deleteById()
```

## 1. 问题陈述

当前 `DELETE /api/admin/card-templates/{id}` 的行为是调用 `CardTemplate.deactivate()` 将状态设为 INACTIVE。INACTIVE 同时承担了「停用」和「删除」两种语义，导致：

1. 管理员无法区分"暂时停用"和"真正删除"
2. 没有误删恢复机制
3. 删除的卡牌仍占 `(ip_series_id, code)` 唯一键，无法在同一 IP 系列下创建同名卡牌

## 2. 解决方案

将 DELETE 与 INACTIVE 解耦：
- DELETE 端点改为物理删除原表行 + 写入回收站快照（30 天保留期）
- INACTIVE 保留为"停用"语义（通过 PUT update 接口切换 status）

具体动作：
- 创建 `CardTemplateRecycleBinStrategy` 实现 `RecycleBinItemStrategy<CardTemplate>`
- 删除前校验：验证卡牌存在（当前无用户卡牌收集等下游依赖）
- 移入回收站：快照所有字段到 `card_template_deleted`，物理删 `card_template` 行，总览表登记
- 恢复：从快照重建，强制 INACTIVE，校验 IP 系列仍存在 + `(ip_series_id, code)` 不冲突
- 永久删除：物理清理快照 + 回收站记录 + 关联卡牌图片

## 3. 用户故事

1. 作为系统管理员，我想要删除卡牌模板时它进入回收站而非仅仅停用，以便我能恢复误删的数据
2. 作为系统管理员，我想要从回收站恢复已删除的卡牌模板，恢复后状态为 INACTIVE，以便手动确认后重新启用
3. 作为系统管理员，我想要恢复卡牌时被阻止（如果关联的 IP 系列已被删除），以便避免数据孤岛
4. 作为系统管理员，我想要永久删除回收站中的卡牌记录并清理关联图片文件，以便释放存储空间

## 4. 实现决策

> **上下文**：REQ-105 是第二个 `RecycleBinItemStrategy` 实现。REQ-104（IpSeriesRecycleBinStrategy）已完成并建立了策略模板。CardTemplate 的数据模型与 IpSeries 有两个关键差异：(1) code 唯一性范围是 `(ip_series_id, code)` 而非全局唯一；(2) 卡牌通过 `ip_series_id` 外键关联 IP 系列，恢复时需校验关联仍存在。

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | 策略 Bean 位置 | core 模块 `infrastructure/adapter/repoadapter/` | 遵循 REQ-104 建立的模式；策略直接操作 JPA Repository，属于 infrastructure 层 |
| 2 | Bean 注册方式 | admin 模块 `RecycleBinConfig` 显式 `@Bean` 注册 | 遵循 REQ-104 模式；core 模块不依赖 component-feign，策略构造需要 `FileCleanupPort` 由 admin 提供 adapter 实现；`@Component` 会让 core 缺少 FileCleanupPort Bean 时启动失败 |
| 3 | 持久化方式 | 读操作走 Port 接口，写/删操作走 JPA Repository | 遵循 REQ-100/104 确立的规则 |
| 4 | 删除前校验 | 仅验证卡牌存在（`cardTemplateRepositoryPort.existsById()`） | 当前无用户卡牌收集系统（Phase 4），无下游依赖可校验；后续 REQ-18/19 实现后可扩展为校验用户收集引用 |
| 5 | 恢复后状态 | 强制 INACTIVE | REQ-100 已固化的契约 |
| 6 | 恢复时 IP 系列校验 | 调用 `ipSeriesRepositoryPort.existsById()` 校验关联 IP 系列仍存在 | 卡牌通过 `ip_series_id` 外键关联 IP 系列，若 IP 系列已被删除则恢复后成为数据孤岛；注意：此校验不要求 IP 系列为 ACTIVE（INACTIVE 的 IP 系列下卡牌恢复后应仍存在） |
| 7 | 恢复时唯一性校验 | `(ip_series_id, code)` 组合唯一 | CardTemplate 的 code 唯一性范围是同一 IP 系列下（与 IpSeries 全局 code 唯一不同），恢复时通过 `cardTemplateJpaRepository.findByIpSeriesIdAndCode()` 校验 |
| 8 | 恢复实现方式 | 使用 native SQL `INSERT INTO card_template (id, ...)` 而非 JPA `save()` | 遵循 REQ-104 决策；保留原始 ID，JPA IDENTITY 策略不支持显式 ID |
| 9 | 事务 | 由调用方 `CardTemplateAppService.deleteCardTemplate` 的 `@Transactional` 保证原子性 | 策略接口契约"原子性由调用方保证" |
| 10 | 文件清理 | `FileCleanupPort.deleteFile(fileId)`，容错（catch 后仅 log.warn） | 遵循 REQ-104 建立的文件清理模式；core 不依赖 component-feign |
| 11 | 关联数据处理 | CardTemplate 无多对多关联表，`related_data` 写入 `null` | 与 IpSeries 一致；Question/KnowledgeItem 有 JSON 快照则不同 |
| 12 | deletedBy 来源 | `SecurityUtils.getCurrentUsername()` | 遵循 REQ-100 决策 #8，在 `CardTemplateAppService.deleteCardTemplate()` 中获取后传给策略 |

### 与 REQ-104（IpSeries）的关键差异

| 维度 | REQ-104 IpSeries | REQ-105 CardTemplate | 影响 |
|------|-----------------|---------------------|------|
| 唯一性范围 | code/name 全局唯一 | code 仅在 `(ip_series_id, code)` 下唯一 | 恢复时只需校验同 IP 系列的 code 冲突 |
| 删除前校验 | 委托 `IpSeriesDomainService.validateDeactivatable()` 检查 ACTIVE 卡牌引用 | 仅验证存在性（无下游依赖） | 更简单；后续可扩展 |
| 恢复时额外校验 | 无 | 校验关联 IP 系列仍存在 | 防数据孤岛 |
| 文件字段 | `coverImage`（封面图） | `image`（卡牌图） | 仅字段名不同 |
| 领域服务介入 | 有（`IpSeriesDomainService`） | 无 | 策略构造函数参数少一个 |

### 策略依赖注入清单

```
CardTemplateRecycleBinStrategy 注入：
  ├── CardTemplateRepositoryPort         （findById / existsById 读 Domain 对象）
  ├── RecycleBinItemRepositoryPort       （findById 读 RecycleBinItem）
  ├── CardTemplateJpaRepository          （deleteById / findByIpSeriesIdAndCode）
  ├── CardTemplateDeletedJpaRepository   （save / findByOriginalId / deleteById）
  ├── RecycleBinItemJpaRepository        （save / deleteById）
  ├── IpSeriesRepositoryPort             （existsById — restore 时校验 IP 系列存在）
  ├── FileCleanupPort                    （deleteFile — purge 文件清理，admin 提供 FileCleanupAdapter）
  └── EntityManager                      （@PersistenceContext 注入，restore 时 native SQL INSERT；
                                           提供 package-private setEntityManager() 供测试注入 mock）
```

### 策略方法伪代码

```
validateDeletable(originalId):
    if not cardTemplateRepositoryPort.existsById(originalId):
        throw new BusinessException("卡牌模板不存在: " + originalId)

moveToRecycleBin(originalId, deletedBy):
    template = cardTemplateRepositoryPort.findById(originalId)
        .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + originalId))
    deletedPO = CardTemplateDeletedPO from template fields
        + relatedData=null, deletedBy=deletedBy, deletedAt=now()
    cardTemplateDeletedJpaRepository.save(deletedPO)
    cardTemplateJpaRepository.deleteById(originalId)
    recycleBinItemJpaRepository.save(RecycleBinItemPO(
        resourceType=CARD_TEMPLATE, originalId=..., originalName=template.getName(),
        originalCreatedAt=..., originalUpdatedAt=..., deletedBy=..., deletedAt=now(),
        restoreDeadline=now().plusDays(30)
    ))

restore(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = cardTemplateDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
        .orElseThrow(() -> new BusinessException("卡牌模板快照不存在: " + recycleBinItem.getOriginalId()))
    if not ipSeriesRepositoryPort.existsById(deletedPO.getIpSeriesId()):
        throw new BusinessException("卡牌模板关联的 IP 系列已不存在（ID=" + deletedPO.getIpSeriesId() + "），无法恢复")
    if cardTemplateJpaRepository.findByIpSeriesIdAndCode(deletedPO.getIpSeriesId(), deletedPO.getCode()).isPresent():
        throw new BusinessException("卡牌编码已存在，无法恢复: " + deletedPO.getCode())
    entityManager.createNativeQuery(
        "INSERT INTO card_template (id, ip_series_id, code, name, rarity, "
            + "description, status, image_file_id, image_url, created_at, updated_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")
        .setParameter(1, originalId)...status=INACTIVE...
        .executeUpdate()
    cardTemplateDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)

purge(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = cardTemplateDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
    if deletedPO != null:
        if deletedPO.getImageFileId() != null:
            try:
                fileCleanupPort.deleteFile(deletedPO.getImageFileId())
            catch Exception e:
                log.warn("文件清理失败: originalId={}, fileId={}", originalId, ...)
        cardTemplateDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)
```

## 5. 影响分析

### 新建文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `core/.../infrastructure/adapter/repoadapter/CardTemplateRecycleBinStrategy.java` | core | 策略 Bean，实现 `RecycleBinItemStrategy<CardTemplate>` |
| `core/.../infrastructure/adapter/repoadapter/CardTemplateRecycleBinStrategyTest.java` | core | 策略集成测试（`@DataJpaTest` + 真实 MySQL），15 用例 |
| `admin/.../application/service/recyclebin/CardTemplateRecycleBinStrategyBlackBoxTest.java` | admin | 策略黑盒测试（纯 Mockito），6 用例 |

### 修改文件

| 文件 | 模块 | 变更 |
|------|------|------|
| `admin/.../application/service/CardTemplateAppService.java` | admin | `deleteCardTemplate()` 改为委托策略（validateDeletable + moveToRecycleBin）；注入策略 Bean |
| `admin/.../config/RecycleBinConfig.java` | admin | 新增 `cardTemplateRecycleBinStrategy` `@Bean` 注册 |
| `core/.../infrastructure/db/repository/CardTemplateDeletedJpaRepository.java` | core | 新增 `findByOriginalId(Long)` 查询方法 |
| `admin/.../application/service/CardTemplateAppServiceTest.java` | admin | 修改 delete 测试用例（mock 策略而非 deactivate）；新增不存在抛异常用例 |

### 不需要修改

- `CardTemplateDeletedPO` — REQ-100 已建表
- `CardTemplateDeletedJpaRepository` — REQ-100 已声明（仅补 `findByOriginalId` 方法）
- `CardTemplateController` — DELETE 端点不变，HTTP 方法相同
- `RecycleBinAppService` — 框架已就位（REQ-102/103）
- 前端回收站页面 — 通过 `supported-types` API 自动显示 CARD_TEMPLATE 类型
- `FileCleanupPort` / `FileCleanupAdapter` — REQ-104 已建立

### 受影响功能

| 功能 | 影响 | 风险 |
|------|------|------|
| `DELETE /api/admin/card-templates/{id}` | 行为变更：从 deactivate 改为移入回收站 | 前端调用方无需改动 |
| 回收站管理端列表页 | CARD_TEMPLATE 类型出现在目录树和列表中 | 低风险，REQ-100 已做空集兼容 |
| `POST /api/admin/recycle-bin/{id}/restore` | 支持恢复卡牌模板 | 低风险，REQ-103 框架已就位 |
| `DELETE /api/admin/recycle-bin/{id}` | 支持永久删除卡牌模板 | 低风险，REQ-102 框架已就位 |
| 卡牌 `(ip_series_id, code)` 唯一约束 | 删除后释放唯一键，恢复时校验冲突 | 正面改进 |

## 6. 验证计划

### 手工验证步骤

1. **删除成功**：`DELETE /api/admin/card-templates/{id}` → 返回 200
   - 验证 `card_template` 表该行已删除
   - 验证 `card_template_deleted` 表有新快照行（含 ipSeriesId/code/name/rarity/image 等全部字段）
   - 验证 `recycle_bin` 表有新记录（resource_type=CARD_TEMPLATE）
   - 验证管理端回收站页面可见该记录

2. **删除被拒**：`DELETE /api/admin/card-templates/{id}`（不存在的 ID）→ 返回 400 "卡牌模板不存在: X"

3. **恢复成功**：`POST /api/admin/recycle-bin/{id}/restore` → 返回 200
   - 验证 `card_template` 表行恢复，status=INACTIVE
   - 验证 `card_template_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除

4. **恢复被拒 — IP 系列已删**：删除一个 IP 系列 → 恢复其下的卡牌模板 → 应报 "卡牌模板关联的 IP 系列已不存在（ID=X），无法恢复"

5. **恢复被拒 — code 冲突**：在当前 IP 系列下创建同 code 新卡牌（不删除）→ 恢复旧卡牌 → 应报 "卡牌编码已存在，无法恢复: XXX"

6. **永久删除**：`DELETE /api/admin/recycle-bin/{id}` → 返回 200
   - 验证 `card_template_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除

### 自动化测试覆盖

| 测试类 | 类型 | 覆盖目标 |
|--------|------|---------|
| `CardTemplateRecycleBinStrategyTest` | 集成（`@DataJpaTest`） | validateDeletable(2) + moveToRecycleBin(3) + restore(5) + purge(5) = 15 用例 |
| `CardTemplateRecycleBinStrategyBlackBoxTest` | 单元（Mockito） | 恢复 INACTIVE 覆盖(2) + 循环删除恢复(1) + 并发删除静默跳过(2) + IP 系列不存在(1) = 6 用例 |
| `CardTemplateAppServiceTest` | 单元（Mockito） | delete 委托策略 + 不存在抛异常（2 用例） |

## 7. 测试策略

### 测试类型选择

策略测试使用 `@DataJpaTest` 集成测试（真实 MySQL），因为策略直接操作多个 JPA Repository，Mock 无法覆盖 flush 顺序、唯一约束、自增 ID 等 JPA 行为。

测试注解模板：
```java
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CardTemplateRecycleBinStrategy.class,
         CardTemplateRepositoryAdapter.class,
         RecycleBinItemRepositoryAdapter.class,
         IpSeriesRepositoryAdapter.class})
@ActiveProfiles("test")
class CardTemplateRecycleBinStrategyTest { ... }
```

策略依赖三个 Port 接口（`CardTemplateRepositoryPort`、`RecycleBinItemRepositoryPort`、`IpSeriesRepositoryPort`），需 `@Import` 对应的 Adapter 实现。`FileCleanupPort` 在 `@DataJpaTest` 上下文中不可用（admin 模块的 `FileCleanupAdapter` 依赖 Feign Client），使用 `@MockBean` mock。

### 测试用例

| # | 方法 | 场景 | 预期 |
|---|------|------|------|
| 1 | `validateDeletable` | 卡牌存在 | 正常返回 |
| 2 | `validateDeletable` | 卡牌不存在 | `BusinessException("卡牌模板不存在: X")` |
| 3 | `moveToRecycleBin` | 正常路径 | card_template 行删除 + deleted 有快照 + recycle_bin 有记录 |
| 4 | `moveToRecycleBin` | 卡牌模板不存在 | `BusinessException("卡牌模板不存在: X")` |
| 5 | `moveToRecycleBin` | 含图片的卡牌 | 快照保留 imageFileId 和 imageUrl |
| 6 | `restore` | 正常路径 | 行恢复(INACTIVE) + 快照删除 + 回收站删除；createdAt 保留原值，updatedAt=now |
| 7 | `restore` | IP 系列已不存在 | `BusinessException("卡牌模板关联的 IP 系列已不存在（ID=X）")` |
| 8 | `restore` | code 冲突 | `BusinessException("卡牌编码已存在，无法恢复: X")` |
| 9 | `restore` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |
| 10 | `restore` | 快照不存在 | `BusinessException("卡牌模板快照不存在: X")` |
| 11 | `purge` | 无图片 | deleted + recycle_bin 均删除 |
| 12 | `purge` | 有图片 | 调 `fileCleanupPort.deleteFile(fileId)`，然后清理 DB |
| 13 | `purge` | FileCleanupPort 抛异常 | catch 后仅 log.warn，DB 清理不受影响 |
| 14 | `purge` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |
| 15 | `purge` | 快照已被并发删除 | 静默跳过，仅删 recycle_bin |

## 8. 不在范围

- 前端页面改动：无需改动，回收站页面由 REQ-100 交付，通过 `supported-types` API 自动显示 CARD_TEMPLATE 类型
- 级联删除卡牌收集数据（用户卡牌收集系统尚未实现）
- 批量导入/导出回收站数据
- 回收站定时清理（REQ-101）
