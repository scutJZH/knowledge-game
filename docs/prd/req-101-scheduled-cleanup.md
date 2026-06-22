# REQ-101：回收站定时清理任务

> 状态：`confirmed`
> 创建：2026-06-21
> 前置依赖：REQ-100 ✅（回收站框架）、REQ-102 ✅（手动永久删除 purge）、REQ-103 ✅（通用恢复框架）

## 前置依赖

- 本需求基于 [REQ-100 通用回收站系统](./req-100-recycle-bin.md) 的框架实现
- 复用契约：
  - `RecycleBinItemStrategy<T>.purge()` 方法（详见 REQ-100 §4.1.4 节，由 REQ-102/104~108 实现）
  - `RecycleBinItemStrategyRegistry` 注册中心（按 resourceType 分发策略）
  - `restore_deadline` 字段（`deletedAt + 30 天`，DELETE 写入时赋值，REQ-100 §3.1）
  - 5 张 `_deleted` 详情表 + 总览索引表结构（详见 REQ-100 §3.2）
  - 文件清理走 `ResourceType.toBizTypes()` 映射（详见 REQ-100 §4.1.2）
- 本需求的增量工作：
  - 新增 `RecycleBinCleanupScheduler` 定时任务（Spring `@Scheduled`）
  - 新增通用 `scheduled_task_log` 表 + CRUD 查询 API + 前端管理页
  - `RecycleBinItemJpaRepository` 新增 `findByRestoreDeadlineBefore` 查询方法

## 1. 背景与定位

REQ-100 引入了回收站系统，`restore_deadline` 字段（`deletedAt + 30 天`）在 DELETE 移入回收站时写入。到期后数据需物理删除（含关联文件清理），否则回收站持续膨胀。

**本需求实现**：
1. 定时任务：每日执行，物理删除 `recycle_bin` 中 `restore_deadline < now()` 的记录（复用已有 `RecycleBinItemStrategy.purge()`）
2. 通用执行日志：`scheduled_task_log` 表，记录每轮执行结果（总数/成功/失败明细/耗时），供后续其他定时任务复用
3. 管理端查询页：查看各任务执行历史

## 2. 设计决策汇总

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | 模块归属 | **admin 模块** `application/scheduler/` 包 | admin 有策略 Bean 注册（`RecycleBinConfig`）+ `FileCleanupAdapter` 文件清理实现；core 无文件清理能力 |
| 2 | 执行时间 | **cron 表达式，默认 `0 0 3 * * ?`**（凌晨 3 点），`application.yml` 可配置 | 低峰期，配置化支持不同部署环境调整 |
| 3 | 事务策略 | **逐条 `@Transactional(REQUIRES_NEW)`**，与 REQ-102 `purgeInNewTransaction` 一致 | 一条失败不滚回其他；与批量永久删除共用同一模式 |
| 4 | 过期查询 | **`findByRestoreDeadlineBefore(LocalDateTime now)`** | Spring Data JPA 方法名查询，返回 PO 列表后逐条 purge |
| 5 | 执行日志表 | **通用 `scheduled_task_log`** 表 | 一张表服务所有定时任务，按 `task_name` 区分；避免每个任务建一张日志表 |
| 6 | 无过期记录 | **仍写入执行日志**（total=0, success=0, failure=0, status=SUCCESS） | 运维需要看到"执行过了，没有过期数据"的证明 |
| 7 | 日志表归属 | **core 模块**（领域实体 + PO + Repository + Adapter） | 通用基础设施；后续其他定时任务（群组清理、凭证过期等）共用同一表 |
| 8 | Adapter 注册 | core Adapter 加 `@Repository`，core 的 `@ComponentScan` 自动发现即可，无需在 admin 侧额外注册 | 沿用 `RecycleBinItemRepositoryAdapter` 的 `@Repository` 模式 |
| 9 | 策略 Bean 注册 | admin `RecycleBinConfig` 通过 `@Bean` 注册策略 Bean（如 `IpSeriesRecycleBinStrategy`） | 策略 Bean 依赖 `FileCleanupPort`（admin 侧实现），core 模块不能独立创建 |

## 3. 数据模型

### 3.1 `scheduled_task_log` 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | — | 主键 |
| `task_name` | VARCHAR(64) | NOT NULL | 任务标识（如 `RECYCLE_BIN_CLEANUP`） |
| `task_display` | VARCHAR(128) | NOT NULL | 中文显示名（如"回收站定时清理"） |
| `executed_at` | DATETIME | NOT NULL | 执行时间 |
| `duration_ms` | BIGINT | NOT NULL | 耗时毫秒 |
| `total_count` | INT | NOT NULL | 处理记录总数 |
| `success_count` | INT | NOT NULL | 成功数 |
| `failure_count` | INT | NOT NULL | 失败数 |
| `failure_details` | JSON | NULLABLE | 失败明细 `[{"recycleBinId":42,"resourceType":"IP_SERIES","name":"xxx","reason":"..."}]` |
| `status` | VARCHAR(20) | NOT NULL | `SUCCESS` / `PARTIAL_FAILURE` / `FAILURE` |

**索引**：`idx_task_executed (task_name, executed_at DESC)` — 覆盖按任务类型过滤 + 时间降序的分页查询。

**字段设计说明**：
- 单表通用设计：`task_name` 区分不同任务，后续其他定时任务（如群组清理、凭证过期清理）共用此表
- `failure_details` MySQL `json` 列（`@Column(columnDefinition = "json")`），Java `String` 类型，Converter 做 `writeValueAsString` / `readValue` 序列化。与项目中 `QuestionPO.answer` JSON 列模式一致
- 无 `created_at` / `updated_at` 列：`executed_at` 即记录创建时间；日志写入后不可修改

### 3.2 领域模型

```
core/domain/model/entity/
  ScheduledTaskLog.java        — 纯数据持有对象（全参构造器 + getter，无行为方法）
core/domain/model/domainenum/
  TaskExecutionStatus.java     — SUCCESS / PARTIAL_FAILURE / FAILURE
core/domain/model/vo/
  FailureDetail.java           — 值对象（recycleBinId, resourceType, name, reason）
core/domain/port/outbound/
  ScheduledTaskLogRepositoryPort.java  — void save(ScheduledTaskLog log);
                                        PageResult<ScheduledTaskLog> findAll(String taskName, int page, int size);
```

### 3.3 Infrastructure 层

```
core/infrastructure/db/entity/
  ScheduledTaskLogPO.java
core/infrastructure/db/repository/
  ScheduledTaskLogJpaRepository.java
core/infrastructure/db/converter/
  ScheduledTaskLogConverter.java  — PO ↔ Domain（failure_details JSON 序列化/反序列化）
core/infrastructure/adapter/repoadapter/
  ScheduledTaskLogRepositoryAdapter.java  — @Repository，core 的 @ComponentScan 自动发现，无需在 AutoConfiguration 额外注册 @Bean
```

## 4. 核心定时任务设计

### 4.1 `RecycleBinCleanupScheduler`

```
admin/application/scheduler/
  RecycleBinCleanupScheduler.java  — @Component，admin 模块内
```

**执行流程**：

1. 记录开始时间 `start = System.currentTimeMillis()`
2. `LocalDateTime now = LocalDateTime.now()`
3. 调 `recycleBinItemJpaRepository.findByRestoreDeadlineBefore(now)` 获取过期 PO 列表
4. 若列表为空：写入一条 `total=0, success=0, failure=0, status=SUCCESS` 日志行，结束
5. 若列表非空：逐条处理
   - `RecycleBinItemConverter.INSTANCE.toDomain(po)` 将 PO 转为领域实体 `RecycleBinItem`
   - 调 `self.purgeInNewTransaction(item)`（`@Transactional(REQUIRES_NEW)` 独立事务，签名 `purgeInNewTransaction(RecycleBinItem item)`，与 `RecycleBinAppService` 中的方法同签名）
   - purge 内部：查详情表 → 删文件（`fileCleanupPort`）→ 删详情表 → 删总览表
   - 成功：`success++`
   - 异常：记录 FailureDetail，`failure++`，log.error，继续下一条
6. 汇总：`duration = now - start`，确定 status，写入 `scheduled_task_log`
7. log.info 输出汇总：`回收站定时清理完成: 总数=X, 成功=Y, 失败=Z, 耗时=Xms`

**关键设计点**：

- **自注入**：`Scheduler` 注入 `self`（自身代理）调用 `purgeInNewTransaction`，绕过 Spring AOP 代理限制
- **`restore_deadline` 语义**：`restore_deadline < now()` = 过期。`restore_deadline` 在 DELETE 时写入为 `deletedAt + 30 天`，写入后永不更新
- **策略未注册处理**：`strategyRegistry.get(resourceType)` 对未接入的资源类型抛 `BusinessException(501)`，被 catch 后计入失败明细。当前仅 REQ-104（IP 系列）已接入，其余资源在 REQ-105~108 完成前不会产生回收站记录
- **`@Scheduled` 配置**：`cron = "${knowledgegame.recycle-bin.cleanup-cron:0 0 3 * * ?}"`

### 4.2 现有 JPA Repo 扩展

`RecycleBinItemJpaRepository` 新增方法：

```java
List<RecycleBinItemPO> findByRestoreDeadlineBefore(LocalDateTime deadline);
```

Spring Data JPA 自动实现，无需写 `@Query`。

### 4.3 配置

```yaml
# admin/src/main/resources/application.yml
knowledgegame:
  recycle-bin:
    cleanup-cron: "0 0 3 * * ?"
```

admin 启动类 `KnowledgeGameAdminApplication` 加 `@EnableScheduling`。

## 5. 日志查询 API

### 5.1 Controller

```
GET /api/admin/scheduled-task-logs?taskName=&page=0&size=20
```

`admin/api/controller/ScheduledTaskLogController.java`

### 5.2 DTO

**`ScheduledTaskLogListRequest`**：

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `taskName` | String | 否 | null（不过滤） | 任务标识过滤 |
| `page` | Integer | 否 | 0 | 页码（0-based） |
| `size` | Integer | 否 | 20 | 每页条数（最大 100） |

**`ScheduledTaskLogResponse`**：

| 字段 | 类型 | 来源 |
|------|------|------|
| `id` | Long | — |
| `taskName` | String | — |
| `taskDisplay` | String | — |
| `executedAt` | Long | epoch 毫秒 |
| `durationMs` | Long | — |
| `totalCount` | Integer | — |
| `successCount` | Integer | — |
| `failureCount` | Integer | — |
| `failureDetails` | JSON array | null 时省略 |
| `status` | String | 枚举 name |

时间字段遵循 REQ-86 时间戳协议（epoch 毫秒）。

### 5.3 AppService

`admin/application/service/ScheduledTaskLogAppService.java`

```java
@Service
public class ScheduledTaskLogAppService {
    public PageResult<ScheduledTaskLogResponse> list(ScheduledTaskLogListRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        PageResult<ScheduledTaskLog> result = repositoryPort.findAll(
                request.getTaskName(), page, size);
        return PageResult.<ScheduledTaskLogResponse>builder()
                .content(result.getContent().stream()
                        .map(Assembler.INSTANCE::toResponse).toList())
                .totalElements(result.getTotalElements())
                .pageNumber(result.getPageNumber())
                .pageSize(result.getPageSize())
                .totalPages(result.getTotalPages())
                .build();
    }
}
```

## 6. 前端设计

### 6.1 路由

「系统」菜单下新增「定时任务日志」页：

```typescript
// config/routes.ts
{ path: '/system/scheduled-task-logs', name: '定时任务日志', component: './ScheduledTaskLog' }
```

### 6.2 页面

单层 ProTable（无目录树），文件名 `frontend/admin/src/pages/ScheduledTaskLog/index.tsx`。

**列定义**：

| 列 | 字段 | 说明 |
|----|------|------|
| 任务名称 | taskDisplay | 列头 filter 下拉。`valueEnum` 从前端已加载数据中按 `taskName` 去重提取（无需独立 API；类似现有 ProTable status 列 filter 模式） |
| 执行时间 | executedAt | `valueType: 'dateTime'`，默认降序 |
| 耗时 | durationMs | 格式化：≥1000 显示"Xs"，< 1000 显示"Xms" |
| 过期总数 | totalCount | — |
| 成功 | successCount | 绿色 |
| 失败 | failureCount | `> 0` 红色高亮；hover Tooltip 展示 failure_details 列表 |
| 状态 | status | Tag：SUCCESS=green, PARTIAL_FAILURE=orange, FAILURE=red |

**ProTable 参数绑定**：
- taskName filter → URL query `taskName`
- 分页 → `page`/`size`

### 6.3 服务层

`frontend/admin/src/services/scheduledTaskLog.ts` — API 调用封装 + TS 类型定义。

## 7. 测试策略

### 测试归属

| 测试对象 | 所在模块 |
|---------|---------|
| `ScheduledTaskLog` / `TaskExecutionStatus` / `FailureDetail` 领域模型 | core |
| `ScheduledTaskLogConverter` PO ↔ Domain | core |
| `ScheduledTaskLogRepositoryAdapter` 集成测试 | core |
| `RecycleBinCleanupScheduler` 单元测试 | admin |
| `ScheduledTaskLogAppService` 单元测试 | admin |
| `ScheduledTaskLogController` @WebMvcTest | admin |
| `ScheduledTaskLogAssembler` 单元测试 | admin |
| 前端 ProTable 单元测试 | frontend/admin |
| `npx tsc --noEmit` 类型检查 | frontend/admin |

### 测试覆盖清单

| 测试范围 | 类型 | 覆盖目标 |
|---------|------|---------|
| 领域模型构造 | 单元 | 全参构造器 created 对象各字段正确；`TaskExecutionStatus` 三个枚举值 |
| `FailureDetail` record | 单元 | 构造 + equals（用于断言 failure_details） |
| Converter JSON 往返 | 集成 | `failure_details` null → PO null；有值 → 序列化 → 反序列化 → 值相等。**关键**：验证 `Long`/`Integer` 类型不被 Jackson 截断（遵循项目 CLUADE.md 已知陷阱） |
| Adapter save + findAll | 集成 | save 后 findAll 可查到；按 taskName 过滤正确；无 taskName 时返回全部；分页正确 |
| Scheduler 有过期记录 | 单元（Mock） | Mock 5 条过期 PO → 验证逐条调 `strategyRegistry.get().purge()` 5 次 → 验证 `logPort.save` 被调且 status=SUCCESS |
| Scheduler 无过期记录 | 单元（Mock） | Mock 空列表 → 验证 `logPort.save` 被调且 total=0, status=SUCCESS |
| Scheduler 部分失败 | 单元（Mock） | Mock 其中 2 条 purge 抛异常 → 验证继续处理剩余 3 条 → status=PARTIAL_FAILURE → failure_details 含 2 条失败信息 |
| Scheduler 全部失败 | 单元（Mock） | Mock 全部 purge 抛异常 → status=FAILURE |
| Controller 参数解析 | @WebMvcTest | page/size 默认值、taskName 过滤、响应 JSON 结构 |
| Assembler 时间转换 | 单元 | LocalDateTime → epoch 毫秒；null → null |
| 前端 ProTable | jest | taskName filter + 分页 + status Tag 颜色 + failure tooltip |
| 前端 TS 类型 | `npx tsc --noEmit` | API 响应类型与 ProTable 列类型一致 |

### 集成测试关键覆盖

`ScheduledTaskLogConverter` 涉及 MySQL JSON 列 → Jackson 序列化/反序列化，纯 Mock 测不出 `Long`→`Integer` 类型截断。必须用 `@DataJpaTest` + 真实 MySQL 验证 `failure_details` 写入后读回字段值一致。

core 集成测试标准配置：
```java
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ScheduledTaskLogRepositoryAdapter.class, ScheduledTaskLogConverter.class})
@ActiveProfiles("test")
class ScheduledTaskLogRepositoryAdapterIT { ... }
```

## 8. 已知约束与非目标

| 项 | 是否在 REQ-101 内 | 留给谁 |
|----|-----------------|--------|
| `RecycleBinItemStrategy.purge()` 具体实现 | ❌ 复用已有 | REQ-102（框架）+ REQ-104~108（各资源） |
| 策略未实现的资源类型无法清理 | ✅ 自动处理（计入 failure），等 REQ-105~108 对接后自然解决 | — |
| 定时任务的分布式锁（单实例） | ❌ | 后续多实例部署时考虑（项目当前单实例） |
| `scheduled_task_log` 表历史数据清理 | ❌ | 后续 REQ（日志表持续增长，需定期清理老数据） |
| 其他定时任务接入 `scheduled_task_log` | ❌ 仅建好通用表 | 各任务自行接入 |

## 9. 验收清单（DoD）

### 后端

- [ ] `scheduled_task_log` 表 DDL 通过 ddl-auto 自动生成
- [ ] `TaskExecutionStatus` 枚举 + `ScheduledTaskLog` 领域实体 + `FailureDetail` 值对象
- [ ] `ScheduledTaskLogRepositoryPort` + `ScheduledTaskLogRepositoryAdapter`（save + findAll 实现）
- [ ] `ScheduledTaskLogConverter`（PO ↔ Domain，含 JSON 序列化/反序列化）
- [ ] `RecycleBinItemJpaRepository.findByRestoreDeadlineBefore(LocalDateTime)` 方法
- [ ] `RecycleBinCleanupScheduler`（`@Scheduled` + 自注入 `purgeInNewTransaction`）
- [ ] `ScheduledTaskLogAppService` + `ScheduledTaskLogController` + `ScheduledTaskLogAssembler`
- [ ] admin 启动类加 `@EnableScheduling`
- [ ] `application.yml` 加 `knowledgegame.recycle-bin.cleanup-cron` 配置项
- [ ] 全部单元测试通过
- [ ] 全部集成测试通过（真实 MySQL）
- [ ] 启动应用确认 `@Scheduled` cron 表达式正确解析（启动日志无 `CronExpression` 解析错误；可通过临时改为短周期如 `*/30 * * * * ?` 手动验证一次执行后改回默认值）

### 前端

- [ ] `/system/scheduled-task-logs` 路由注册
- [ ] ProTable（taskName filter + 分页 + status Tag）
- [ ] `npx tsc --noEmit` 通过
- [ ] jest 单元测试通过

### 文档

- [ ] `docs/prd/req-101-scheduled-cleanup.md` 本文档
- [ ] `docs/requirements.md` REQ-101 状态：`idea → confirmed`
- [ ] `docs/requirements.md` REQ-101 备注列已含"设计时必须参考 REQ-100 PRD"
