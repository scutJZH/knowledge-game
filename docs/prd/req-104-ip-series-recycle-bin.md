# REQ-104：IP 系列对接回收站

> 状态：`confirmed`
> 创建：2026-06-21
> 前置依赖：REQ-100 ✅, REQ-102 ✅, REQ-103 ✅, REQ-16 ✅

## 前置依赖

本需求基于 [REQ-100 通用回收站系统](./req-100-recycle-bin.md) 的框架实现。复用契约：
- `RecycleBinItemStrategy<T>` 接口（详见 REQ-100 第 4.1.4 节）
- `RecycleBinItemStrategyRegistry` 注册中心（自动发现策略 Bean）
- `ip_series_deleted` 详情表 PO + JPA Repository（详见 REQ-100 第 3.2.1 节）
- `ResourceType.IP_SERIES` 枚举与 `toBizTypes()` 映射（详见 REQ-100 第 4.1.2 节）
- `RecycleBinAppService` 的 restore/purge/batchRestore/batchPurge 框架（REQ-102/103 已完成）

本需求的增量工作：
- 创建 `IpSeriesRecycleBinStrategy` 策略 Bean
- 修改 `IpSeriesAppService.deleteIpSeries()` 从 deactivate 改为移入回收站
- 给 `FileServiceClient` 新增 `deleteFile` 方法（purge 文件清理）
- `IpSeriesDeletedJpaRepository` 新增 `findByOriginalId` 查询方法

### 框架集成接缝：三条完整调用链

REQ-104 只需要实现策略的四个方法（`validateDeletable` / `moveToRecycleBin` / `restore` / `purge`），其余流程由已完成的 REQ-100/102/103 框架处理。以下是三条调用链的具体接缝：

**链路 1 — DELETE（REQ-104 专属，不经过 RecycleBinAppService）**

```
DELETE /api/admin/ip-series/{id}
  → IpSeriesController.delete(id)
    → IpSeriesAppService.deleteIpSeries(id)          [@Transactional]
      → SecurityUtils.getCurrentUsername()            （REQ-100 决策 #8）
      → strategy.validateDeletable(id)                ← REQ-104 实现
      → strategy.moveToRecycleBin(id, deletedBy)      ← REQ-104 实现
        ├── ipSeriesRepositoryPort.findById()         读 Domain（Port 已有）
        ├── ipSeriesDeletedJpaRepository.save()       写快照（REQ-100 建表）
        ├── ipSeriesJpaRepository.deleteById()        物理删原表
        └── recycleBinItemJpaRepository.save()        总览表登记（REQ-100 建表）
```

**链路 2 — 恢复（REQ-103 框架 + REQ-104 策略）**

```
POST /api/admin/recycle-bin/{id}/restore
  → RecycleBinController.restore(id)                  [REQ-100 定义端点]
    → RecycleBinAppService.restore(id)                [REQ-103 实现]
      → recycleBinRepository.findById(id)             查总览表
      → self.restoreInNewTransaction(item)            [@Transactional(REQUIRES_NEW)]
        → strategyRegistry.get(IP_SERIES)             注册中心分派 → 返回 IpSeriesRecycleBinStrategy
          .restore(item.getId())                      ← REQ-104 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── ipSeriesDeletedJpaRepository.findByOriginalId()
            ├── ipSeriesJpaRepository.findByCode/Name() 校验唯一性
            ├── ipSeriesJpaRepository.save()           INSERT 原表（INACTIVE）
            ├── ipSeriesDeletedJpaRepository.deleteById()
            └── recycleBinItemJpaRepository.deleteById()
```

**链路 3 — 永久删除（REQ-102 框架 + REQ-104 策略）**

```
DELETE /api/admin/recycle-bin/{id}
  → RecycleBinController.purge(id)                    [REQ-100 定义端点]
    → RecycleBinAppService.purge(id)                  [REQ-102 实现]
      → recycleBinRepository.findById(id)
      → self.purgeInNewTransaction(item)              [@Transactional(REQUIRES_NEW)]
        → strategyRegistry.get(IP_SERIES)             注册中心分派 → 返回 IpSeriesRecycleBinStrategy
          .purge(item.getId())                        ← REQ-104 实现
            ├── recycleBinItemRepositoryPort.findById()
            ├── ipSeriesDeletedJpaRepository.findByOriginalId()
            ├── fileServiceClient.deleteFile()         清理封面图（容错）
            ├── ipSeriesDeletedJpaRepository.deleteById()
            └── recycleBinItemJpaRepository.deleteById()
```

REQ-104 不触碰的框架部分（已由 REQ-100/102/103 交付）：
- `RecycleBinController` 的端点定义、参数校验
- `RecycleBinAppService` 的 batchRestore/batchPurge、`doBatchOperation` 泛型执行器、逐条 `REQUIRES_NEW` 事务
- 注册中心的 `supportedTypes()` 自动更新、重复注册检测
- 回收站管理端列表页（`supported-types` 拉取目录树，自动展示 IP_SERIES）

## 1. 问题陈述

当前 `DELETE /api/admin/ip-series/{id}` 的行为是调用 `IpSeries.deactivate()` 将状态设为 INACTIVE。INACTIVE 同时承担了「停用」和「删除」两种语义，导致：

1. 管理员无法区分"暂时停用"和"真正删除"
2. 没有误删恢复机制
3. 删除的 IP 系列仍占 code/name 唯一键，无法创建同名新系列

## 2. 解决方案

将 DELETE 与 INACTIVE 解耦：
- DELETE 端点改为物理删除原表行 + 写入回收站快照（30 天保留期）
- INACTIVE 保留为"停用"语义（通过 PUT update 接口切换 status）

具体动作：
- 创建 `IpSeriesRecycleBinStrategy` 实现 `RecycleBinItemStrategy<IpSeries>`
- 删除前校验：有 ACTIVE 卡牌引用时拒绝删除
- 移入回收站：快照所有字段到 `ip_series_deleted`，物理删 `ip_series` 行，总览表登记
- 恢复：从快照重建，强制 INACTIVE，校验 code/name 不冲突
- 永久删除：物理清理快照 + 回收站记录 + 关联文件

## 3. 用户故事

1. 作为系统管理员，我想要删除一个 IP 系列时它进入回收站而非仅仅停用，以便我能恢复误删的数据
2. 作为系统管理员，我想要在删除 IP 系列时被阻止（如果该系列下还有 ACTIVE 卡牌），以便我不会意外删除正在使用的系列
3. 作为系统管理员，我想要从回收站恢复已删除的 IP 系列，恢复后状态为 INACTIVE，以便我手动确认后重新启用
4. 作为系统管理员，我想要永久删除回收站中的 IP 系列记录并清理关联的封面图文件，以便释放存储空间

## 4. 实现决策

> **上下文**：REQ-104 是第一个 `RecycleBinItemStrategy` 实现。当前代码仓库中不存在任何策略实现，策略接口 `RecycleBinItemStrategy<T>` 和注册中心 `RecycleBinItemStrategyRegistry` 由 REQ-100 交付但尚未被任何资源对接。

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | 策略 Bean 位置 | core 模块 `infrastructure/adapter/repoadapter/` | 策略虽实现 `domain/service` 接口，但直接操作 JPA Repository，属于 infrastructure 层。放 `repoadapter` 包与其他 Adapter 聚集，便于统一管理基础设施适配器 |
| 2 | Bean 注册方式 | admin 模块 `RecycleBinConfig` 显式 `@Bean` 注册 | 策略构造函数需要 `FileCleanupPort`，该端口仅 admin 模块有实现（`FileCleanupAdapter`）。若用 `@Component` 放 core 模块，core 无 `FileCleanupPort` Bean 时启动失败。注册中心通过 `List<RecycleBinItemStrategy<?>>` 自动发现 |
| 3 | 持久化方式 | 读操作走 Port 接口，写/删操作走 JPA Repository | 读操作（`findById`/`findByCode`/`findByName`）Port 已提供，零新增即可用，且返回 Domain 对象省去手动转换；写/删操作（`deleteById`/`save`/`findByOriginalId`）Port 未提供且不应为策略单独膨胀 Port 接口，直接调 JPA Repository |
| 4 | 删除前校验 | 委托 `IpSeriesDomainService.validateDeactivatable()` | 消除校验逻辑重复：当前该规则已在 DomainService 实现，策略不重复调 `CardTemplateJpaRepository.countActiveByIpSeriesId()`；统一错误消息；REQ-94 校验自然覆盖 DELETE 路径。注意：DomainService 当前消息为"无法停用"，策略层 catch 后重新包装为"无法删除" |
| 5 | 恢复后状态 | 强制 INACTIVE | REQ-100 已固化的契约，避免恢复 ACTIVE 时关联数据不一致 |
| 6 | 恢复时间戳处理 | `createdAt` 保留原值，`updatedAt` 重置为当前时间 | 保留原始创建时间便于审计追溯，更新时间反映恢复动作的发生时间 |
| 7 | 恢复冲突处理 | 应用层预校验 code/name 唯一性 + DB unique 约束双重保护 | code/name 唯一约束覆盖所有状态（ACTIVE 和 INACTIVE 均占用），因此预校验无需额外区分。预校验给管理员友好错误消息，DB 约束兜底并发场景 |
| 8 | 事务 | `moveToRecycleBin` 不自己管理事务，由调用方 `IpSeriesAppService.deleteIpSeries` 的 `@Transactional` 保证原子性 | 策略接口契约"原子性由调用方保证" |
| 9 | Flush | 不需要 | 跨表操作（删 ip_series + 插 ip_series_deleted + 插 recycle_bin），不同表无 unique key 复用 |
| 10 | 文件清理 | `FileServiceClient` 新增 `deleteFile(Long fileId)` 方法，purge 时调用；调用失败时 catch 后仅 log，不影响数据库清理 | 永久删除应清理关联文件；file 服务已有 `DELETE /api/file/internal/{fileId}` 端点，Feign 接口补方法即可；文件清理失败不应阻塞数据库清理 |
| 11 | 关联数据处理 | IP 系列无关联表，`related_data` 写入 `null` | 与其他资源（Question/KnowledgeItem 有 JSON 快照）形成对比 |
| 12 | deletedBy 来源 | `SecurityUtils.getCurrentUsername()` | 参照 REQ-100 决策 #8（存 admin username），在 `IpSeriesAppService.deleteIpSeries()` 中获取后传给策略 |

### 策略依赖注入清单

```
IpSeriesRecycleBinStrategy 注入：
  ├── IpSeriesDomainService              （委托 validateDeactivatable）
  ├── IpSeriesRepositoryPort             （findById 读 Domain 对象）
  ├── RecycleBinItemRepositoryPort       （findById 读 RecycleBinItem）
  ├── IpSeriesJpaRepository              （deleteById / save / findByCode / findByName）
  ├── IpSeriesDeletedJpaRepository       （save / findByOriginalId / deleteById）
  ├── RecycleBinItemJpaRepository        （save / deleteById）
  ├── FileCleanupPort                    （deleteFile — purge 文件清理，admin 提供 FileCleanupAdapter）
  └── EntityManager                      （@PersistenceContext 注入，restore 时 native SQL INSERT）
```

### 策略方法伪代码

```
validateDeletable(originalId):
    try:
        ipSeriesDomainService.validateDeactivatable(originalId)
    catch BusinessException e:
        throw new BusinessException(e.getMessage().replace("无法停用", "无法删除"))

moveToRecycleBin(originalId, deletedBy):
    ipSeries = ipSeriesRepositoryPort.findById(originalId)
        .orElseThrow(() -> new BusinessException("IP 系列不存在: " + originalId))
    deletedPO = IpSeriesDeletedPO.builder()
        .originalId(ipSeries.getId()).code(ipSeries.getCode())
        .name(ipSeries.getName()).description(ipSeries.getDescription())
        .coverImageFileId(...).coverImageUrl(...).status(ipSeries.getStatus())
        .createdAt(ipSeries.getCreatedAt()).updatedAt(ipSeries.getUpdatedAt())
        .relatedData(null).deletedBy(deletedBy).deletedAt(LocalDateTime.now())
        .build()
    ipSeriesDeletedJpaRepository.save(deletedPO)
    ipSeriesJpaRepository.deleteById(originalId)
    recycleBinItemJpaRepository.save(RecycleBinItemPO(
        resourceType=IP_SERIES, originalId=..., originalName=...,
        originalCreatedAt=..., originalUpdatedAt=..., deletedBy=..., deletedAt=now(),
        restoreDeadline=now().plusDays(30)
    ))

restore(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = ipSeriesDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
        .orElseThrow(() -> new BusinessException("IP 系列快照不存在: " + recycleBinItem.getOriginalId()))
    if ipSeriesJpaRepository.findByCode(deletedPO.getCode()).isPresent():
        throw new BusinessException("IP 系列编码已存在，无法恢复: " + deletedPO.getCode())
    if ipSeriesJpaRepository.findByName(deletedPO.getName()).isPresent():
        throw new BusinessException("IP 系列名称已存在，无法恢复: " + deletedPO.getName())
    restoredPO = IpSeriesPO from deletedPO
        with id=deletedPO.getOriginalId(), status=INACTIVE,
        createdAt=deletedPO.getCreatedAt()（保留原值）, updatedAt=now()
    ipSeriesJpaRepository.save(restoredPO)
    ipSeriesDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)

purge(recycleBinId):
    recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
        .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId))
    deletedPO = ipSeriesDeletedJpaRepository.findByOriginalId(recycleBinItem.getOriginalId())
    if deletedPO != null:
        if deletedPO.getCoverImageFileId() != null:
            try:
                fileServiceClient.deleteFile(deletedPO.getCoverImageFileId())
            catch Exception e:
                log.warn("文件清理失败 fileId={}", deletedPO.getCoverImageFileId(), e)
        ipSeriesDeletedJpaRepository.deleteById(deletedPO.getId())
    recycleBinItemJpaRepository.deleteById(recycleBinId)
```

## 5. 影响分析

### 新建文件

| 文件 | 模块 | 说明 |
|------|------|------|
| `core/.../infrastructure/adapter/repoadapter/IpSeriesRecycleBinStrategy.java` | core | 策略 Bean，实现 `RecycleBinItemStrategy<IpSeries>` |
| `core/.../infrastructure/adapter/repoadapter/IpSeriesRecycleBinStrategyTest.java` | core | 策略单元测试（集成测试，用 `@DataJpaTest`） |

### 修改文件

| 文件 | 模块 | 变更 |
|------|------|------|
| `admin/.../application/service/IpSeriesAppService.java` | admin | `deleteIpSeries()` 改为委托策略；注入策略 Bean |
| `core/.../infrastructure/db/repository/IpSeriesDeletedJpaRepository.java` | core | 新增 `findByOriginalId(Long)` 查询方法 |
| `components/.../feign/client/FileServiceClient.java` | component-feign | 新增 `deleteFile(Long fileId)` Feign 方法 |
| `admin/.../application/service/IpSeriesAppServiceTest.java` | admin | 修改 delete 测试用例（mock 策略而非 deactivate） |

### 不需要修改的测试文件

| 文件 | 原因 |
|------|------|
| `RecycleBinAppServiceBlackBoxTest.java` | 该测试 mock 了 `self`（`RecycleBinAppService` 自身），单条操作委托到 mock 而非真实策略 Bean，因此 `strategyRegistry.get(IP_SERIES)` 不会被实际调用。REQ-104 接入后该测试无需修改 |

### 配置变更

`admin` 模块 `RecycleBinConfig` 新增 `@Bean` 注册 `IpSeriesRecycleBinStrategy`（需注入 `FileCleanupPort`，由 admin 的 `FileCleanupAdapter` 提供实现）。`RecycleBinItemStrategyRegistry` 通过 `List<RecycleBinItemStrategy<?>>` 自动发现。

### 受影响功能

| 功能 | 影响 | 风险 |
|------|------|------|
| `DELETE /api/admin/ip-series/{id}` | 行为变更：从 deactivate 改为移入回收站 | 前端调用方无需改动，HTTP 方法不变 |
| 回收站管理端列表页 | REQ-104 对接后，IP_SERIES 类型出现在目录树和列表中 | 低风险，REQ-100 已做空集兼容 |
| `POST /api/admin/recycle-bin/{id}/restore` | 支持恢复 IP 系列 | 低风险，REQ-103 框架已就位 |
| `DELETE /api/admin/recycle-bin/{id}` | 支持永久删除 IP 系列 | 低风险，REQ-102 框架已就位 |
| IP 系列编码/名称唯一约束 | 删除后释放唯一键，恢复时校验冲突 | 正面改进 |
| FileServiceClient 接口 | 新增 `deleteFile` 方法，需确保 file 服务端已有对应端点 | file 服务 `DELETE /api/file/internal/{fileId}` 已存在 |

## 6. 验证计划

### 手工验证步骤

1. **删除成功**：`DELETE /api/admin/ip-series/{id}`（该系列无 ACTIVE 卡牌）→ 返回 200
   - 验证 `ip_series` 表该行已删除
   - 验证 `ip_series_deleted` 表有新快照行
   - 验证 `recycle_bin` 表有新记录（resource_type=IP_SERIES）
   - 验证管理端回收站页面可见该记录

2. **删除被拒**：`DELETE /api/admin/ip-series/{id}`（该系列有 ACTIVE 卡牌）→ 返回 400 "IP 系列存在 N 张 ACTIVE 卡牌，无法删除"

3. **恢复成功**：`POST /api/admin/recycle-bin/{id}/restore` → 返回 200
   - 验证 `ip_series` 表行恢复，status=INACTIVE
   - 验证 `ip_series_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除

4. **永久删除**：`DELETE /api/admin/recycle-bin/{id}` → 返回 200
   - 验证 `ip_series_deleted` 表该行删除
   - 验证 `recycle_bin` 表该记录删除

5. **恢复冲突**：创建同名 ACTIVE（或 INACTIVE）系列（不删除）→ 恢复旧系列 → 应报 "IP 系列名称已存在，无法恢复: XXX"（因为 code/name 唯一约束覆盖所有状态）

### 自动化测试覆盖

| 测试类 | 类型 | 覆盖目标 |
|--------|------|---------|
| `IpSeriesRecycleBinStrategyTest` | 集成（`@DataJpaTest`） | 四方法正常路径 + 异常路径 + 边界 |
| `IpSeriesAppServiceTest` | 单元（Mockito） | `deleteIpSeries` 委托策略 + 不存在抛异常 |
| `IpSeriesControllerTest` | `@WebMvcTest` | DELETE 端点行为不变，响应码正确 |

### 回滚标准

- 回收站列表页出现非预期数据
- IP 系列删除/恢复后数据不一致
- 删除后 code/name 仍被占用（未释放唯一键）
- 批量操作（batchRestore/batchPurge）中 IP 系列类型处理异常

## 7. 测试策略

### 测试类型选择

策略测试使用 `@DataJpaTest` 集成测试（真实 MySQL），因为策略直接操作多个 JPA Repository，Mock 无法覆盖 flush 顺序、唯一约束、自增 ID 等 JPA 行为。

测试注解模板：
```java
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IpSeriesRecycleBinStrategy.class,
         IpSeriesDomainService.class,
         IpSeriesRepositoryAdapter.class,
         CardTemplateRepositoryAdapter.class,
         RecycleBinItemRepositoryAdapter.class})
@ActiveProfiles("test")
class IpSeriesRecycleBinStrategyTest { ... }
```

策略依赖两个 Port 接口（`IpSeriesRepositoryPort`、`RecycleBinItemRepositoryPort`），需 `@Import` 对应的 Adapter 实现。`IpSeriesDomainService` 依赖 `CardTemplateRepositoryPort`，需一并 `@Import` `CardTemplateRepositoryAdapter`。`FileServiceClient` 在 `@DataJpaTest` 上下文中不可用（Feign Client 需要 Nacos），使用 `@MockBean` mock。

### 测试用例

| # | 方法 | 场景 | 预期 |
|---|------|------|------|
| 1 | `validateDeletable` | 无 ACTIVE 卡牌引用 | 正常返回 |
| 2 | `validateDeletable` | 有 ACTIVE 卡牌引用 | `BusinessException`，消息含"无法删除" |
| 3 | `moveToRecycleBin` | 正常路径 | ip_series 行删除 + deleted 表有快照 + recycle_bin 有记录 |
| 4 | `moveToRecycleBin` | IP 系列不存在 | `BusinessException("IP 系列不存在: X")` |
| 5 | `restore` | 正常路径 | ip_series 行恢复(INACTIVE) + deleted 快照删除 + recycle_bin 删除；createdAt 保留原值，updatedAt=now |
| 6 | `restore` | code 冲突 | `BusinessException("IP 系列编码已存在，无法恢复: X")` |
| 7 | `restore` | name 冲突 | `BusinessException("IP 系列名称已存在，无法恢复: X")` |
| 8 | `restore` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |
| 9 | `restore` | 快照不存在 | `BusinessException("IP 系列快照不存在: X")` |
| 10 | `purge` | 无封面图 | deleted 快照 + recycle_bin 均删除 |
| 11 | `purge` | 有封面图 | 调 `fileServiceClient.deleteFile(fileId)`，然后清理 DB |
| 12 | `purge` | file 服务调用失败 | catch 后仅 log.warn，DB 清理不受影响 |
| 13 | `purge` | 回收站记录不存在 | `BusinessException("回收站记录不存在: X")` |

## 8. 不在范围

- 前端页面改动：无需改动，回收站页面由 REQ-100 交付，通过 `supported-types` API 自动显示 IP_SERIES 类型
- 批量导入/导出回收站数据
- IP 系列关联的卡牌级联删除
- 回收站定时清理（REQ-101）
