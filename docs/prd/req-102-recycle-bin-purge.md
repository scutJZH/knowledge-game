# REQ-102：回收站手动永久删除框架

> 状态：`confirmed`
> 创建：2026-06-20
> 前置依赖：REQ-100（通用回收站系统，状态 `done`）、REQ-103（恢复框架，状态 `designed`，**未实现**）
> 后置依赖：REQ-104~108（各资源对接回收站，状态 `idea`）— REQ-102 框架就位后由它们补 strategy.purge 真实实现

## 0. 前置依赖声明

| 编号 | 状态 | 依赖内容 |
|------|------|---------|
| REQ-100 | `done` | 提供以下可复用契约与基础设施（详见 REQ-100 PRD）： |

- **AppService 留后方法签名**：`RecycleBinAppService.purge(Long)` / `batchPurge(List<Long>)` 已透传到 `strategyRegistry.get(type).purge(id)`，本需求在此基础上加事务、错误处理、响应结构
- **策略接口**：`RecycleBinItemStrategy<T>` 第 4.1.4 节已定义 `purge(recycleBinId)` + `batchPurge(ids)` 默认实现
- **注册中心**：`RecycleBinItemStrategyRegistry` 已实现，本需求交付时 `supportedTypes()` 返回空集（5 个策略 Bean 由 REQ-104~108 实现）
- **总览表**：`recycle_bin` 已建表，含 `restore_deadline` 字段（30 天保留期）
- **ResultCode 枚举**：`NOT_FOUND(404)` / `NOT_IMPLEMENTED(501)` 已存在，本需求直接使用
- **永久删除路径**：REQ-100 第 3.3 节「物理清理路径」已固化顺序：1) 删除 `_deleted` 详情表记录；2) 清理关联文件（通过 `ResourceType.toBizTypes()` 映射）；3) 删除 `recycle_bin` 总览表记录

| 编号 | 状态 | 依赖内容 |
|------|------|---------|
| REQ-103 | `designed` | PRD 已设计 self 注入 + `findAllById` + 批量方案 B + `restoreInNewTransaction` + `BatchRestoreResult` 基础设施（**未实现**） |

### 与 REQ-103 的基础设施时序假设（重要）

REQ-102 与 REQ-103 同属回收站框架级需求，使用相同的批量事务/响应模式。本需求 PRD 设计时假设 **REQ-103 尚未实现**，因此本需求实现时承担以下基础设施落地：

| 基础设施 | 落地需求 | 复用需求 |
|---------|---------|---------|
| `RecycleBinAppService` 构造器自注入 `self` | REQ-102 | REQ-103 |
| `RecycleBinItemRepositoryPort.findAllById(Collection<Long>)` | REQ-102 | REQ-103 |
| `RecycleBinItemRepositoryAdapter.findAllById(...)` 实现 | REQ-102 | REQ-103 |
| `RecycleBinAppService` 移除 `groupByResourceType` 私有方法（restore + purge 全部走方案 B） | REQ-102 | REQ-103 |
| `purgeInNewTransaction` + `BatchPurgeResult` | REQ-102（独有） | — |
| `restoreInNewTransaction` + `BatchRestoreResult` | REQ-103（独有） | — |

**并行开发风险**：如果 REQ-103 先于 REQ-102 合入主分支，REQ-102 实现时需注意：
- `self` 注入和 `findAllById` 已存在 → 跳过新建
- `groupByResourceType` 仍存在（REQ-103 决策#12 保留它给 batchPurge 使用）→ REQ-102 需删除它（本需求决策#14）并同时改造 batchPurge 为方案 B
- 不复用 REQ-103 的 batchRestore 实现（REQ-103 已改为方案 B），但 purge 相关代码需自己写

如果 REQ-102 先于 REQ-103 合入，REQ-103 后续实现时仅需：1) 加 `restore` 端点；2) 改 `restore/batchRestore` 调用为 `self.restoreInNewTransaction`（方法签名本需求已建好）；3) 加 `BatchRestoreResult` DTO；4) 复用 self 注入、findAllById 和方案 B 模式。REQ-103 PRD 决策#12 标注了此覆盖关系。

**向前兼容检查**：REQ-104~108 将实现 strategy.purge 真实逻辑，必须遵守本需求固化的接口契约（详见第 4 节）。REQ-101（定时清理）和 REQ-103（恢复）独立于永久删除路径，无冲突。

## 1. Problem Statement

管理员在回收站列表页看到不再需要的资源（已确定不再恢复），无法主动永久删除以释放存储空间。当前 `RecycleBinAppService.purge/batchPurge` 仅是透传签名，未挂 Controller 端点；前端「永久删除」按钮 disabled + tooltip「等待资源对接」；本需求交付前**整个永久删除链路不可用**。

如果不实现手动永久删除：
- 资源必须等待 30 天到期后由 REQ-101 定时清理任务统一处理，无法满足"立即释放"诉求
- 关联文件占用对象存储空间，无法及时回收
- 用户对"30 天保留期"的存在意义产生疑惑（既然不能恢复也不用，为什么要等？）

REQ-100 已搭好框架（接口、注册中心、总览表），但**永久删除动作的实际入口（端点 + UI）和框架级事务/错误处理尚未实现**。REQ-102 负责把这条链路从「未通」打通到「能调通」，让 REQ-104~108 各资源对接时只需实现 `strategy.purge` 内部逻辑即可自动贯通。

## 2. Solution

**交付永久删除链路的完整骨架**：HTTP 端点 + AppService 框架性增强（事务边界、错误捕获、批量部分成功响应）+ 前端按钮启用 + 二次确认（单条 Popconfirm，批量 Modal 强制输入文本）。

**不实现 `strategy.purge` 内部具体永久删除逻辑**（`_deleted` 表删除 + 文件清理 + 总览表删除）— 那是 REQ-104~108 各资源的责任。本需求交付时 supportedTypes 仍为空，用户点永久删除按钮会得到 501「资源类型 X 暂未接入回收站」（来自 `strategyRegistry.get(type)` 抛出的 BusinessException），等 REQ-104 对接第一个资源后自动贯通。

### 2.1 核心设计决策汇总

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | REQ-102 范围 | 纯框架（端点 + AppService 增强 + 前端按钮 + 接口契约），strategy.purge 实现留 REQ-104~108 | 避免越俎代庖；保证 REQ-104~108 各资源 strategy 自由设计 |
| 2 | 基础设施归属 | REQ-102 落地 self 注入 + findAllById + 方案 B + 移除 groupByResourceType；REQ-103 后续直接复用 | REQ-103 已 designed 但未实现；两个需求同质化，避免代码重复 |
| 3 | HTTP 方法 | 单条 DELETE /{id}，批量 POST /batch-purge | DELETE 符合 RESTful 语义且 REQ-100 已固化；HTTP DELETE 不带 body（部分代理/防火墙丢弃），批量必须用 POST |
| 4 | 单条永久删除事务 | `@Transactional(REQUIRES_NEW)` 标注在 `purgeInNewTransaction` 上，`purge(Long)` 自身无事务注解（仅做 findById + 委托） | 与 REQ-103 设计保持一致；事务边界集中在 `purgeInNewTransaction` |
| 5 | 事务自调用问题 | 注入 `self`（同 bean 自己）调 `purgeInNewTransaction` | Spring AOP 自调用失效；通过构造器自注入解决 |
| 6 | 批量永久删除原子性 | 逐条独立事务 + 部分成功响应（`successIds` + `failures`） | 用户预期"批量可部分成功"；事务边界清晰；与 REQ-103 完全对称 |
| 7 | 批量永久删除性能优化 | 方案 B：阶段 1 批量预校验（`findAllById`）+ 阶段 2 逐条独立事务执行 | 消除"记录不存在"类失败免走 strategy；strategy 内部失败必须逐条 try-catch；真批量 SQL 优化留 REQ-104~108 |
| 8 | 批量永久删除 HTTP 状态码 | 总是 200，成败由响应体描述 | 避免不通用 207；前端按 `failures.length` 决定 message 颜色；与 REQ-103 对称 |
| 9 | 错误消息来源 | BusinessException → 用 `getMessage()`；其他异常 → "永久删除失败，请联系管理员" 兜底 | 区分业务可预期失败和系统异常；避免泄露内部细节 |
| 10 | 二次确认强度 | 单条 Popconfirm（轻量）；批量 Modal 强制输入选中数量（防批量误操作） | 永久删除不可逆，影响面越大确认越强；与 GitHub 删除仓库同档；交互一致性可接受损失 |
| 11 | 前端按钮样式 | 单条 + 批量均 `danger` 红色 | 视觉信号提示不可逆操作；与「恢复」按钮（普通样式）形成对比 |
| 12 | 前端按钮启用条件 | 全启用（不再按 supportedTypes 动态判断） | 架构诚实；REQ-104 对接后自动贯通；501 是合法的"未对接"信号 |
| 13 | GET /{id} 详情端点 | 不在本需求范围，推迟到 REQ-104~108 | 详情含 `_deleted` 表 `related_data` JSON 解析，依赖具体资源字段，无法在通用框架中实现 |
| 14 | `groupByResourceType` 是否保留 | 删除（restore + purge 全部走方案 B） | 与 REQ-103 设计对齐；分组无意义（strategy.batchPurge 默认循环单条）。**注意**：REQ-103 PRD 决策#12 选择保留 groupByResourceType 给 batchPurge，本决策覆盖它 — 详见第 0 节「并行开发风险」
| 15 | Strategy 接口 JavaDoc 强化 | 同 REQ-103 对 restore 的处理：明确「原子性由 AppService 保证」「业务失败抛 BusinessException」「文件清理通过 ResourceType.toBizTypes()」 | REQ-104~108 实现者必须知道契约边界 |

## 3. User Stories

1. 作为管理员，我希望在回收站列表点击行内「永久删除」按钮，能将不再需要的资源物理删除（含关联文件），以释放存储空间
2. 作为管理员，我希望永久删除前看到二次确认弹窗，明确告知"删除后不可恢复，关联文件将一并清除"，以避免误操作
3. 作为管理员，我希望永久删除失败时（如文件服务不可达）看到清晰的错误消息，以便知道为何不能删除
4. 作为管理员，我希望批量勾选多条回收站记录后点「批量永久删除」一次性处理，以提升效率
5. 作为管理员，我希望批量永久删除时部分失败不影响其他成功项，并看到「成功 X 条，失败 Y 条」的汇总，以便定位失败原因
6. 作为管理员，我希望批量永久删除时**必须输入选中数量才能确认**，因为批量操作影响面大，需更强的防误操作保护
7. 作为管理员，我希望批量永久删除中全失败时也能看到第一条的具体失败原因，以便排障
8. 作为系统，我要求单条永久删除是多步写入的原子操作（_deleted 删除 + 文件清理 + 总览表删除），任一步失败必须整体回滚，避免数据不一致
9. 作为系统，我要求批量永久删除逐条独立事务，A 条失败不回滚已成功的 B 条
10. 作为开发者，我希望 REQ-104~108 实现各资源 strategy.purge 时无需关心事务/错误处理，只需写"删 _deleted 表 → 清理文件 → 删总览表"业务逻辑，框架层自动处理事务边界
11. 作为开发者，我希望 strategy 接口 JavaDoc 明确写"原子性由 AppService 保证""文件清理通过 ResourceType.toBizTypes()"，避免 REQ-104~108 实现者遗漏

## 4. Implementation Decisions

### 4.1 接口契约强化（core 层）

**`RecycleBinItemStrategy<T>` 接口的 `purge` 方法 JavaDoc 强化**（接口签名不变，仅文档约束）：

- 明确写："实现必须完成三步物理删除：1) 删除 `_deleted` 详情表对应记录；2) 通过 `ResourceType.toBizTypes()` 拿到所有相关 bizType，调 file 服务删除关联文件；3) 删除 `recycle_bin` 总览表对应记录"
- 明确写："内部多步操作的原子性由 AppService 层事务保证，strategy 实现无需自己加 `@Transactional`"
- 明确写："可永久删除失败的场景（file 服务不可达、并发删除冲突等）必须抛 `BusinessException`，由 AppService 捕获并归入批量响应的 failures"
- 明确写："策略实现应容忍 `recycle_bin` 记录已被并发删除的情况（如两个管理员同时永久删除同一记录，后执行的事务中 `recycle_bin` 已不存在）。当 `DELETE FROM recycle_bin` 返回 affectedRows=0 时静默成功，不抛异常，仅清理可访问的资源（`_deleted` 表、关联文件）"
- 明确写："strategy 实现内部不应该再次校验 `restoreDeadline`（已过期的可清理，未到期的也可手动清理）；时间窗口的语义由调用方（管理员/REQ-101 定时任务）决定"

**`batchPurge` 默认实现不变**：循环调单条（与事务边界正交，AppService 自行决定逐条事务）。

### 4.2 Controller（admin/api/controller/RecycleBinController.java 追加 2 个端点）

| 端点 | Method | 路径 | 入参 | 出参 | 异常 |
|------|--------|------|------|------|------|
| 单条永久删除 | DELETE | `/api/admin/recycle-bin/{id}` | `@PathVariable Long id` | `Result<Void>` | BusinessException（404/501/4xx）由全局异常处理器返回 |
| 批量永久删除 | POST | `/api/admin/recycle-bin/batch-purge` | `@Valid @RequestBody BatchPurgeRequest` | `Result<BatchPurgeResult>` | 总是 200，成败由响应体描述 |

**鉴权**：所有 `/api/admin/**` 端点需 ADMIN 角色 JWT（沿用现有 Security 配置，本需求无变更）。

**HTTP 方法选择权衡**：

- 单条用 DELETE：符合 RESTful 语义（DELETE 表示资源消失），且 REQ-100 PRD 第 4.4.1 节已固化该路径
- 批量用 POST：HTTP/1.1 规范未禁止 DELETE 携带 body，但部分代理/防火墙/WAF 会丢弃 DELETE body，业界主流实践（GitHub / GitLab / Stripe）批量删除都用 POST `/batch-delete` 或类似路径
- 两个端点路径不对称（`/{id}` vs `/batch-purge`）是 RESTful 批量操作的标准做法，不构成 API 设计问题

### 4.3 DTO 设计

#### 4.3.1 `BatchPurgeRequest`（admin/api/dto/request/）

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `ids` | `List<Long>` | 是 | `@NotEmpty` + `@Size(max=100)` + 元素 `@NotNull` | 待永久删除的 recycleBinId 列表，上限 100 防超大请求 |

#### 4.3.2 `BatchPurgeResult`（admin/api/dto/response/）

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `successIds` | `List<Long>` | AppService 收集 | 成功永久删除的 recycleBinId 列表（顺序为 findAllById 返回顺序，通常即主键升序） |
| `failures` | `List<Failure>` | AppService 收集 | 失败列表 |

**`BatchPurgeResult.Failure`** 嵌套静态类：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `id` | `Long` | recycleBinId | 失败的 recycleBinId |
| `errorMessage` | `String` | `BusinessException.getMessage()` 或兜底文案 | 业务可预期失败用原始中文消息；未知异常用 "永久删除失败，请联系管理员" |

**与 REQ-103 `BatchRestoreResult` 的关系**：完全对称独立。两者字段相同（successIds + failures.Failure{id, errorMessage}），但不抽取公共基类或泛型：
- 原因：① 字段语义完全相同但语义上恢复和删除是两个动作；② 公共抽象会引入跨需求耦合；③ 项目 DDD 规范禁止 admin/application 跨需求共享父类
- 复制成本（约 30 行 Java）远低于抽象的耦合成本

**字段设计要点**：
- 不返回 `errorCode` —— 前端只需展示文本，分类由 message 内容自然传达
- `successIds` 顺序为 `findAllById` 返回顺序（JPA IN 查询不保证与输入顺序一致，通常按主键升序），前端不应依赖 successIds 与请求顺序的对应关系
- `failures` 顺序按 AppService 遍历顺序（先预校验失败的（按请求顺序），再 strategy 失败的（按 findAllById 返回顺序））

**重复 id 处理语义**：请求中同一 id 重复出现时（如 `[42, 42, 43]`），AppService **不去重**（与 REQ-103 完全对称）：
- 阶段 1 `findAllById` 用 `IN` 查询天然去重，返回 1 条
- 阶段 2 循环遍历**已找到的 items**（去重后的），所以 strategy 不会被同一 id 调用 2 次
- `successIds` 不含重复，长度 = `items.size()`
- **请求中的重复 id 不会单独报错**（不像"记录不存在"那样入 failures），相当于自动去重
- 失败 id 也不重复（每个 item 至多在 failures 中出现 1 次）

→ 设计权衡：选择"静默去重"而非"重复报错"，理由是用户体验更好（前端选择表格行天然无重复，重复只是恶意/异常请求；恶意场景由 @Size(max=100) 限制总量）。

**完全空 ids 拒绝**：`@NotEmpty` 保证空列表/缺字段返回 400。但**单个 null 元素**（如 `[42, null]`）由 `@NotNull` 元素级校验拒绝，返回 400。

**响应示例（全成功）**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "successIds": [42, 43, 44],
    "failures": []
  }
}
```

**响应示例（部分失败）**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "successIds": [42, 44],
    "failures": [
      { "id": 43, "errorMessage": "文件服务暂时不可达，请稍后重试" },
      { "id": 45, "errorMessage": "回收站记录不存在: 45" }
    ]
  }
}
```

**响应示例（全失败）**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "successIds": [],
    "failures": [
      { "id": 42, "errorMessage": "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站" },
      { "id": 43, "errorMessage": "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站" }
    ]
  }
}
```

### 4.4 AppService 改造（admin/application/service/RecycleBinAppService.java）

#### 4.4.1 单条永久删除 `purge(Long recycleBinId)`

```java
public void purge(Long recycleBinId) {
    RecycleBinItem item = recycleBinRepository.findById(recycleBinId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND.getCode(),
                    "回收站记录不存在: " + recycleBinId));
    self.purgeInNewTransaction(item);  // 通过 self 代理开启 REQUIRES_NEW 事务
}
```

**关键说明**：
- `purge` 自身**不加** `@Transactional`，仅做 findById 查询 + 委托
- 真正的事务边界由 `purgeInNewTransaction`（`@Transactional(REQUIRES_NEW)`）承担
- Controller 直接调 `purge(Long)`，**不要**直接调 `purgeInNewTransaction`

#### 4.4.2 批量永久删除 `batchPurge(List<Long> recycleBinIds)` → 返回 `BatchPurgeResult`

```java
public BatchPurgeResult batchPurge(List<Long> recycleBinIds) {
    // 阶段 1：批量预校验（findAllById 一次查回，避免逐条 findById）
    List<RecycleBinItem> items = recycleBinRepository.findAllById(recycleBinIds);
    Set<Long> foundIds = items.stream().map(RecycleBinItem::getId).collect(Collectors.toSet());

    List<Long> successIds = new ArrayList<>();
    List<BatchPurgeResult.Failure> failures = new ArrayList<>();

    // 预校验失败的 id 直接入 failures（按请求顺序）
    for (Long id : recycleBinIds) {
        if (!foundIds.contains(id)) {
            failures.add(new BatchPurgeResult.Failure(id, "回收站记录不存在: " + id));
        }
    }

    // 阶段 2：逐条独立事务执行（item 已查，直接传 item，避免重复 findById）
    for (RecycleBinItem item : items) {
        try {
            self.purgeInNewTransaction(item);  // 通过 self 代理，每次新事务
            successIds.add(item.getId());
        } catch (BusinessException e) {
            failures.add(new BatchPurgeResult.Failure(item.getId(), e.getMessage()));
        } catch (Exception e) {
            failures.add(new BatchPurgeResult.Failure(item.getId(), "永久删除失败，请联系管理员"));
        }
    }
    return new BatchPurgeResult(successIds, failures);
}
```

#### 4.4.3 内部事务方法 `purgeInNewTransaction(RecycleBinItem item)`

```java
/**
 * 内部事务方法：开启 REQUIRES_NEW 事务执行 strategy.purge。
 * <p>
 * 可见性：public（Spring AOP CGLIB 代理要求；保持 public 避免代理失效风险）。
 * 仅以下两处允许调用：
 * - {@link #purge(Long)}：单条永久删除端点入口
 * - {@link #batchPurge(List)}：批量永久删除阶段 2
 * Controller 不应直接调用此方法（应调 appService.purge(Long)）。
 *
 * @param item 已查询的回收站条目（避免重复 findById）
 */
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void purgeInNewTransaction(RecycleBinItem item) {
    strategyRegistry.get(item.getResourceType()).purge(item.getId());
}
```

**为什么不用 package-private**：Spring AOP 通过 CGLIB 子类代理时技术上可代理 package-private 方法，但 Spring 官方文档仅保证 public 方法的代理行为；为避免未来切换代理策略（如引入接口）导致 AOP 静默失效，保持 public + JavaDoc 标注「仅内部用」。

#### 4.4.4 self 注入方式

构造器注入 self（Spring 4.3+ 支持自注入）：

```java
public RecycleBinAppService(RecycleBinItemRepositoryPort recycleBinRepository,
                             RecycleBinItemStrategyRegistry strategyRegistry,
                             RecycleBinAppService self) {
    this.recycleBinRepository = recycleBinRepository;
    this.strategyRegistry = strategyRegistry;
    this.self = self;
}
```

**自注入的字段声明**：

```java
private final RecycleBinAppService self;
```

**注意**：Spring 4.3+ 支持构造器自注入，但 Spring Boot 3.x（项目当前版本）已稳定支持。如果未来引入接口代理模式（接口 + 实现类），需调整为注入接口类型。

#### 4.4.5 同时改造 restore / batchRestore（落地 REQ-103 设计）

为避免 REQ-103 后续实现时再改一次 AppService，本需求实现时**同时**改造 restore 相关方法（落地 REQ-103 已设计但未实现的部分）：

- `restore(Long)` 重构：findById 后委托 `self.restoreInNewTransaction(item)`（自身不加 `@Transactional`）
- 新增 `restoreInNewTransaction(RecycleBinItem item)`：`@Transactional(REQUIRES_NEW)` + public + JavaDoc
- `batchRestore(List<Long>)` 改造为方案 B（findAllById + 循环 self.restoreInNewTransaction），返回 `BatchRestoreResult`
- 删除 `groupByResourceType` 私有方法（restore + purge 全部走方案 B）

**新增的 REQ-103 类**：
- `BatchRestoreRequest`（admin/api/dto/request/）— 与 BatchPurgeRequest 对称
- `BatchRestoreResult`（admin/api/dto/response/）— 与 BatchPurgeResult 对称

**新增的 REQ-103 端点**（仅签名，等 REQ-103 真正实现时挂上 Controller）：
- 本需求不挂 restore 相关端点（不在范围内）
- 但 AppService 的 restore 方法实现完整可用，REQ-103 后续仅需在 Controller 加端点 + 前端按钮启用

**为什么这样做**：避免「REQ-102 改一次 AppService、REQ-103 再改一次」的双次重构。两个需求同质化，一次改造落地所有基础设施。

**风险与缓解**：
- 风险：REQ-102 实现了 REQ-103 部分代码，看起来「越权」
- 缓解：在本 PRD 中明确说明，且 progress.md 记录每行代码的归属（REQ-102 自身 vs 替 REQ-103 落地）
- 后续追踪：REQ-103 实现时仅需「加 Controller 端点 + 前端按钮启用」，progress.md 中明确标注「复用 REQ-102 已落地的基础设施」

#### 4.4.6 关键设计权衡（避免重复 DB 查询）

- 单条永久删除端点：AppService 层仅 1 次回收站表查询（`findById`）。strategy.purge 内部按 REQ-100 第 3.3 节物理清理路径，需从 recycleBinId 反查 `_deleted` 详情表对应的 original_id，这会在 strategy 内部产生 1 次额外的 `recycle_bin` 查询。此查询为 strategy 内部行为（主键索引，性能可忽略），不计入 AppService 层查询次数
- 批量永久删除：AppService 层仅 1 次回收站表查询（`findAllById` 阶段 1），不再逐条 findById
- 通过让 `purgeInNewTransaction` 直接接受 `RecycleBinItem` 对象（而非 `Long id`），消除"先批量查、又逐条查"的重复查询模式

### 4.5 Port 改动（core 层）

**`RecycleBinItemRepositoryPort` 新增方法**：

```java
/**
 * 批量查询回收站条目（REQ-102/103 共用，按 id 集合一次查回）
 * <p>
 * 用于批量操作时阶段 1 预校验，避免逐条 findById。
 * 不存在的 id 静默忽略（与 Spring Data JPA findAllById 语义一致）。
 *
 * @param ids 回收站记录 ID 集合
 * @return 找到的回收站条目列表（顺序不保证）
 */
List<RecycleBinItem> findAllById(Collection<Long> ids);
```

**`RecycleBinItemRepositoryAdapter` 实现**：

```java
@Override
public List<RecycleBinItem> findAllById(Collection<Long> ids) {
    return jpaRepository.findAllById(ids).stream()
            .map(RecycleBinItemConverter.INSTANCE::toDomain)
            .toList();
}
```

### 4.6 前端改造（frontend/admin/）

#### 4.6.1 行内「永久删除」操作（RecycleBinTable.tsx）

| 项 | 实现 |
|----|------|
| 按钮启用 | 全启用（替换原 disabled + tooltip） |
| 按钮样式 | `danger`（红色）— 视觉信号提示不可逆 |
| 二次确认 | `Popconfirm`，标题 "永久删除该条目？"，描述 "删除后不可恢复，关联文件将一并清除。"，确认 "永久删除"（红色），取消 "取消" |
| 调用 | `purgeItem(record.id)` |
| 成功反馈 | `message.success('永久删除成功')` + 列表刷新 |
| 失败反馈 | `message.error(e?.message \|\| '永久删除失败')` |

#### 4.6.2 工具栏「批量永久删除」按钮

| 项 | 实现 |
|----|------|
| 按钮启用条件 | `selectedRowKeys.length > 0`（替换原 disabled + tooltip） |
| 按钮样式 | `danger`（红色） |
| 二次确认 | **Modal**（不用 Popconfirm）：标题 `批量永久删除选中的 ${selectedRowKeys.length} 条？`，描述 "删除后不可恢复，关联文件将一并清除。请输入选中数量 ${selectedRowKeys.length} 确认。"，输入框要求用户输入的数字等于 `selectedRowKeys.length` 才允许点确认 |
| 确认按钮启用条件 | 输入框值 === String(selectedRowKeys.length) |
| 调用 | `batchPurgeItems(selectedRowKeys)` |
| 全成功反馈（failures.length === 0） | `message.success(\`成功永久删除 ${successIds.length} 条\`)` + 列表刷新 + 清空选择 |
| 部分成功反馈（successIds.length > 0 && failures.length > 0） | `message.warning(\`成功 ${successIds.length} 条，失败 ${failures.length} 条\`)` + 列表刷新 + 清空选择 |
| 全失败反馈（successIds.length === 0 && failures.length > 0） | `message.error(\`全部失败：${failures[0].errorMessage}\`)` + 列表刷新（防御性，可能数据已变） + 清空选择 |

**「批量恢复」按钮**：本需求不动（保持 REQ-103 范围）

#### 4.6.3 Modal 实现要点（React + Ant Design 5）

```tsx
const [modalOpen, setModalOpen] = useState(false);
const [confirmText, setConfirmText] = useState('');

const openBatchPurgeModal = () => {
  setConfirmText('');
  setModalOpen(true);
};

const handleConfirm = async () => {
  if (confirmText !== String(selectedRowKeys.length)) return;
  // 调 batchPurgeItems + 三档反馈
  setModalOpen(false);
};

<Modal
  title={`批量永久删除选中的 ${selectedRowKeys.length} 条？`}
  open={modalOpen}
  onOk={handleConfirm}
  onCancel={() => setModalOpen(false)}
  okText="永久删除"
  cancelText="取消"
  okButtonProps={{
    danger: true,
    disabled: confirmText !== String(selectedRowKeys.length),
  }}
>
  <p>删除后不可恢复，关联文件将一并清除。</p>
  <p>请输入选中数量 <strong>{selectedRowKeys.length}</strong> 确认：</p>
  <Input
    value={confirmText}
    onChange={(e) => setConfirmText(e.target.value)}
    placeholder={`请输入 ${selectedRowKeys.length}`}
  />
</Modal>
```

#### 4.6.4 API 封装追加（services/recycleBin.ts）

```typescript
export async function purgeItem(id: number): Promise<void> {
  await request(`/api/admin/recycle-bin/${id}`, { method: 'DELETE' });
}

export async function batchPurgeItems(ids: number[]): Promise<BatchPurgeResult> {
  return request('/api/admin/recycle-bin/batch-purge', {
    method: 'POST',
    data: { ids },
  });
}

export interface BatchPurgeResult {
  successIds: number[];
  failures: Array<{ id: number; errorMessage: string }>;
}
```

## 5. Impact Analysis

### 5.1 Files to Modify

#### 后端 admin 模块

| 文件 | 改动 |
|------|------|
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/controller/RecycleBinController.java` | 追加 2 个端点：`DELETE /{id}` + `POST /batch-purge` |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/dto/request/BatchPurgeRequest.java` | **新建**：批量永久删除请求 DTO，含 `@NotEmpty` + `@Size(max=100)` 校验 |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/dto/response/BatchPurgeResult.java` | **新建**：批量永久删除响应 DTO，含嵌套 `Failure` 静态类 |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/application/service/RecycleBinAppService.java` | 1) `purge(Long)` 重构：内部 findById 后委托 `self.purgeInNewTransaction(item)`，自身不加 `@Transactional`<br>2) `batchPurge(List<Long>)` 改为返回 `BatchPurgeResult`，移除 `groupByResourceType` 分组，改方案 B 实现（findAllById + 循环）<br>3) **新增** `purgeInNewTransaction(RecycleBinItem item)` 方法（`@Transactional(REQUIRES_NEW)` + public + JavaDoc 标注「仅内部用」）<br>4) 构造器加 `self` 自注入<br>5) **同时改造 restore/batchRestore** 落地 REQ-103 设计（详见 4.4.5）<br>6) **新增** `restoreInNewTransaction(RecycleBinItem item)` 方法<br>7) 删除 `groupByResourceType` 私有方法 |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/dto/request/BatchRestoreRequest.java` | **新建（替 REQ-103 落地）**：批量恢复请求 DTO |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/dto/response/BatchRestoreResult.java` | **新建（替 REQ-103 落地）**：批量恢复响应 DTO |

#### 后端 core 模块

| 文件 | 改动 |
|------|------|
| `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/port/outbound/RecycleBinItemRepositoryPort.java` | **新增方法** `List<RecycleBinItem> findAllById(Collection<Long> ids)`：批量查询回收站条目（供 AppService 阶段 1 预校验用，避免逐条 findById） |
| `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/infrastructure/adapter/repoadapter/RecycleBinItemRepositoryAdapter.java` | **实现** `findAllById`：调 `jpaRepository.findAllById(ids)` + Converter `toDomain` 列表映射 |
| `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/service/recyclebin/RecycleBinItemStrategy.java` | 接口签名不变，仅强化 `purge` 方法 JavaDoc：「三步物理删除（_deleted + 文件清理 + 总览表）」「原子性由 AppService 保证」「业务失败抛 BusinessException」 |

#### 后端测试

| 文件 | 改动 |
|------|------|
| `backend/knowledge-game-admin/src/test/java/com/knowledgegame/admin/api/controller/RecycleBinControllerWebMvcTest.java` | **追加测试**（文件已存在）：单条永久删除成功/失败(404/501)、批量永久删除全成功/部分失败/全失败、参数校验、**DELETE 方法支持验证**（MockMvc 默认支持 DELETE） |
| `backend/knowledge-game-admin/src/test/java/com/knowledgegame/admin/application/service/RecycleBinAppServiceTest.java` | **追加测试**（文件已存在）：purge 成功/记录不存在/无 strategy、batchPurge 全成功/部分预校验失败/部分 strategy 失败/未知异常、self 调用验证、batchPurge 不重复查 DB；**restore 相关测试用例同步追加**（验证 REQ-102 替 REQ-103 落地的代码正确性） |
| `backend/knowledge-game-core/src/test/java/com/knowledgegame/core/infrastructure/adapter/repoadapter/RecycleBinItemRepositoryAdapterIT.java` | **追加测试**（文件已存在）：`findAllById` 测试 — 正常 id、部分不存在 id、空集合 |

#### 前端 admin

| 文件 | 改动 |
|------|------|
| `frontend/admin/src/services/recycleBin.ts` | 追加 `purgeItem` + `batchPurgeItems` + `BatchPurgeResult` 接口 |
| `frontend/admin/src/pages/RecycleBin/components/RecycleBinTable.tsx` | 1) **扩展 `RecycleBinTableProps` 接口**：新增 `onPurge(id: number): Promise<void>` + `onBatchPurge(ids: number[]): Promise<BatchPurgeResult>` 回调 prop<br>2) 行内操作列「永久删除」按钮启用 + Popconfirm + danger 样式<br>3) 工具栏「批量永久删除」按钮启用 + Modal 强制输入 + danger 样式<br>4) 三档 message 反馈<br>5) Modal 状态管理（modalOpen + confirmText） |
| `frontend/admin/src/pages/RecycleBin/index.tsx` | 追加 `handlePurge` + `handleBatchPurge` 处理函数，作为 `onPurge` / `onBatchPurge` prop 传递给 Table 组件 |
| `frontend/admin/src/pages/RecycleBin/components/__tests__/RecycleBinTable.test.tsx` | **新建或追加**：行内永久删除（Popconfirm + danger）、批量永久删除（Modal 输入文本）、message 三档反馈测试 |

### 5.2 Configuration Changes

无配置变更：
- 不新增配置项
- 不新增环境变量
- 不新增数据库迁移（recycle_bin 表 REQ-100 已建好，schema 不变）
- 不新增 Maven/npm 依赖

### 5.3 Affected Functionality

| 现有功能 | 影响评估 |
|---------|---------|
| 回收站列表页（REQ-100） | 无回归：列表查询逻辑、ProTable 渲染不变；仅新增按钮事件处理 |
| `RecycleBinAppService.list` / `supportedTypes` | 无回归：本需求不改这两个方法 |
| `RecycleBinAppService.restore` / `batchRestore` | **改造**：从「REQ-100 留后签名（groupByResourceType 分组分派）」改为「REQ-103 设计的方案 B 实现」。**对外行为变化**：原来调用会调 strategy（因 supportedTypes 空，strategyRegistry.get 抛 501）；现在改造后行为相同（仍抛 501）。REQ-103 端点未挂 Controller，所以无人调用，无回归 |
| `RecycleBinAppService.purge` / `batchPurge` | **改造**：同上，从「REQ-100 留后签名（groupByResourceType 分组分派）」改为「REQ-102 设计的方案 B 实现」 |
| Strategy 接口 | 无回归：仅改 JavaDoc，签名不变，REQ-104~108 实现者无影响 |
| `ResultCode` 枚举 | 无变更：`NOT_FOUND(404)` / `NOT_IMPLEMENTED(501)` REQ-100 已新增 |
| 全局异常处理器 | 无回归：本需求不引入新异常类型 |
| Security Filter Chain | 无变更：`/api/admin/recycle-bin/**` 已在 ADMIN 角色保护范围内；admin 后端 `SecurityConfig.java` 已显式 `csrf(AbstractHttpConfigurer::disable)`，DELETE 方法可用，无需额外配置 |
| Spring 自注入 | 注意点：Spring Boot 3.x 支持构造器自注入；若使用字段注入会有循环依赖警告，本需求用构造器注入规避 |

**Spring Security CSRF 已禁用**：项目当前 admin 后端 `SecurityConfig.java:36` 已显式 `csrf(AbstractHttpConfigurer::disable)`（admin 后端用 JWT 而非 cookie session，CSRF 防护非必需）。DELETE 方法可直接使用，无 CSRF 风险。

## 6. Verification Plan

### 6.1 Manual Verification Steps

> 本需求交付时 `supportedTypes()` 为空（无 strategy Bean），点击「永久删除」按钮预期得到 501「资源类型 X 暂未接入回收站」。**完整端到端验证留到 REQ-104 对接第一个资源后**。

**框架层手动验证**（本需求范围内）：

1. 启动 admin 后端 + 前端
2. 进入 `/system/recycle-bin`
3. **行内按钮启用检查**：
   - 期望：「永久删除」按钮可点击（红色），「批量永久删除」按钮在选中行后可点击（红色）
4. **点击行内「永久删除」按钮**：
   - 期望：弹 Popconfirm，文案正确（含「关联文件将一并清除」）
   - 点确认 → 调 API → 由于无 strategy Bean，得到 501 → message.error("资源类型 X 暂未接入回收站")
5. **批量永久删除 Modal 流程**：
   - 选中 2 条记录
   - 点击「批量永久删除」按钮 → 弹 Modal
   - 期望：Modal 标题含 "2"，描述含 "请输入选中数量 2 确认"
   - 输入框为空时确认按钮 disabled
   - 输入 "1" → 确认按钮仍 disabled
   - 输入 "2" → 确认按钮启用
   - 点确认 → 调 API → 由于无 strategy Bean，得到全失败响应 → message.error 显示 "资源类型 X 暂未接入回收站"
6. **批量永久删除（含不存在 id）**：
   - 用 curl 或 Postman 直接调 `POST /api/admin/recycle-bin/batch-purge` 带 `{ ids: [42, 43, 99999] }`
   - 期望：HTTP 200，响应 `{ successIds: [], failures: [{id:42, ...501...}, {id:43, ...501...}, {id:99999, "回收站记录不存在: 99999"}] }`
7. **空 ids 校验**：调 `POST /batch-purge` 带 `{ ids: [] }` → 期望 400 参数校验失败
8. **超过 100 ids**：调 101 个 id → 期望 400 参数校验失败
9. **单条不存在**：调 `DELETE /api/admin/recycle-bin/99999` → 期望 404 「回收站记录不存在: 99999」
10. **DELETE 方法可用性**：通过浏览器前端调用 DELETE 端点（不通过 curl），确认 Spring Security 配置允许 DELETE（已确认 CSRF 禁用，预期通过）

**端到端验证（推迟到 REQ-104）**：实现 IpSeries strategy.purge 后，删除一条 IpSeries → 移入回收站 → 在回收站点永久删除 → ip_series_deleted 表行消失 → 文件服务对应 bizType 文件被删 → recycle_bin 总览表行消失。

### 6.2 Automated Test Coverage

#### 后端 admin 模块

**`RecycleBinControllerWebMvcTest`**（追加）：

| 用例 | 输入 | 期望 |
|------|------|------|
| 单条永久删除成功 | mock `appService.purge` 无异常 | HTTP 200 + `Result<Void>` |
| 单条永久删除失败（404） | mock `appService.purge` 抛 `BusinessException(404, "回收站记录不存在: 99")` | HTTP 200 + `Result{code:404, message:...}` |
| 单条永久删除失败（501） | mock `appService.purge` 抛 `BusinessException(501, "资源类型 X 暂未接入回收站")` | HTTP 200 + `Result{code:501, message:...}` |
| 批量永久删除全成功 | mock 返回 `BatchPurgeResult([42,43], [])` | HTTP 200 + 响应体含 successIds |
| 批量永久删除部分失败 | mock 返回 `BatchPurgeResult([42], [{id:43, errorMessage:"..."}])` | HTTP 200 + 响应体含 failures |
| 批量永久删除全失败 | mock 返回 `BatchPurgeResult([], [{id:42,...},{id:43,...}])` | HTTP 200 + successIds 空 |
| 空 ids | POST body `{"ids":[]}` | HTTP 400 参数校验 |
| 缺少 ids 字段 | POST body `{}` | HTTP 400 参数校验 |
| 超过 100 ids | POST body 含 101 个 id | HTTP 400 参数校验 |
| JSON 序列化 | 任意批量永久删除请求 | `successIds` 是 `List<Long>` 数组（不是字符串），`failures[].id` 是 Long |
| DELETE 方法支持 | DELETE /{id} | MockMvc 不报 method not supported |

**`RecycleBinAppServiceTest`**（追加）：

| 用例 | mock 行为 | 期望 |
|------|----------|------|
| purge 成功 | `findById` 返回 item，`self.purgeInNewTransaction` 不抛异常 | `self.purgeInNewTransaction(item)` 被 verify 调用 1 次 |
| purge 记录不存在 | `findById` 返回 empty | 抛 BusinessException(NOT_FOUND) |
| purge 资源类型无 strategy | `findById` 返回 item，`self.purgeInNewTransaction` 调用时 mock 抛 BusinessException(501) | 异常向上抛（不捕获） |
| batchPurge 全成功 | `findAllById` 返回 3 条，`self.purgeInNewTransaction` 不抛异常 | successIds=[1,2,3], failures=[] |
| batchPurge 部分预校验失败 | `findAllById` 返回 [item1, item3]（item2 不存在） | failures 含 `{id:2, "回收站记录不存在: 2"}`，item1/item3 走 strategy |
| batchPurge strategy 失败 | `self.purgeInNewTransaction(item2)` 抛 BusinessException("文件服务不可达") | failures 含 `{id:2, "文件服务不可达"}` |
| batchPurge 未知异常 | `self.purgeInNewTransaction(item)` 抛 RuntimeException | failures 含 `{id:X, "永久删除失败，请联系管理员"}` |
| batchPurge 不重复查 DB | `findAllById` 返回 N 条 | verify `findById` **0 次**（阶段 2 直接传 item，不再逐条查） |
| batchPurge self 调用验证 | 任意 | verify `self.purgeInNewTransaction` 被调用 N 次（N=找到的记录数） |
| restore 相关测试 | （替 REQ-103 落地） | 同上模式，验证 restore/restoreInNewTransaction/batchRestore |

#### 后端 core 模块

**`RecycleBinItemRepositoryAdapterIT`**（如已有则追加，否则新建）：

| 用例 | 期望 |
|------|------|
| findAllById 正常 id | 返回对应条目列表 |
| findAllById 部分不存在 | 仅返回存在的，不抛异常 |
| findAllById 空集合 | 返回空列表 |

#### 前端 admin 模块

**`RecycleBinTable.test.tsx`**：

| 用例 | mock | 期望 |
|------|------|------|
| 行内「永久删除」按钮可点击 | 渲染带数据的行 | 按钮存在、enabled、danger 样式 |
| 点击「永久删除」→ Popconfirm 弹出 | 模拟 click | Popconfirm 文案可见，含「关联文件将一并清除」 |
| Popconfirm 确认 → API 调用 | mock `purgeItem` resolve | 调用 1 次，message.success 显示 |
| 单条永久删除失败 | mock `purgeItem` reject | message.error 显示 |
| 工具栏批量按钮：未选中 disabled | `selectedRowKeys=[]` | 按钮 disabled |
| 工具栏批量按钮：选中启用 | `selectedRowKeys=[1,2]` | 按钮 enabled，danger 样式 |
| 点击批量按钮 → Modal 弹出 | 模拟 click | Modal 标题含 "2"，输入框 placeholder 含 "2" |
| Modal 输入错误数量 → 确认按钮 disabled | 输入 "1" | 确认按钮 disabled |
| Modal 输入正确数量 → 确认按钮启用 | 输入 "2" | 确认按钮 enabled |
| 批量全成功 | mock `batchPurgeItems` resolve 返回 `{successIds:[1,2], failures:[]}` | message.success 显示含 "2"，列表刷新，选择清空 |
| 批量部分成功 | mock 返回 `{successIds:[1], failures:[{id:2,...}]}` | message.warning 显示 |
| 批量全失败 | mock 返回 `{successIds:[], failures:[{id:1,...},{id:2,...}]}` | message.error 显示含第一条 errorMessage |

**`npx tsc --noEmit`**：通过（`BatchPurgeResult` TS 接口与后端 DTO 字段对齐）。

### 6.3 Rollback Criteria

满足以下任一条件应回滚或修复后重发：

1. **后端单元测试失败**：Controller 或 AppService 测试不通过
2. **前端类型检查或 jest 测试失败**
3. **手动验证步骤 1-10 任一不通过**（框架层验证必须全过，特别是 DELETE 方法支持验证）
4. **回收站列表页（REQ-100 已交付功能）出现回归**：列表查询、ProTable 渲染、目录树加载异常
5. **应用启动失败**：Spring 自注入导致循环依赖错误
6. **Spring Security 拦截 DELETE 请求**：（已确认 CSRF 禁用，理论不会触发；若触发需排查 Security 配置变更）

## 7. Testing Decisions

### 7.1 测试基础设施主动防御

**前端 jest + jsdom + Ant Design 5**：必须预置 `window.matchMedia` + `window.getComputedStyle` mock（Ant Design 5 依赖），否则 Popconfirm/Button/Modal 渲染报错。

**Modal 异步交互测试技巧**：

```tsx
// 用 fireEvent.change 模拟输入
fireEvent.change(input, { target: { value: '2' } });

// 等待确认按钮启用
await waitFor(() => {
  expect(okButton).not.toBeDisabled();
});

// 用 fireEvent.click 触发确认
fireEvent.click(okButton);
```

### 7.2 self-injection 单测技巧

由于 `batchPurge` 和 `purge(Long)` 内部通过 `self.purgeInNewTransaction(item)` 调用，单测中 mock `self`：

```java
RecycleBinAppService self = mock(RecycleBinAppService.class);
RecycleBinAppService service = new RecycleBinAppService(repo, registry, self);

// 让 self.purgeInNewTransaction 调用不抛异常（默认 void）
doNothing().when(self).purgeInNewTransaction(any(RecycleBinItem.class));

service.batchPurge(List.of(1L, 2L));

verify(self, times(2)).purgeInNewTransaction(any(RecycleBinItem.class));
verify(repo, never()).findById(anyLong());  // 验证阶段 2 不重复查 DB
```

### 7.3 测试模块归属

| 测试对象 | 模块 | 原因 |
|---------|------|------|
| Controller @WebMvcTest | admin | Controller 在 admin，标准 `@AutoConfigureMockMvc(addFilters=false)` |
| AppService 单测 | admin | AppService 在 admin，无跨模块扫描 |
| Port/Adapter findAllById 集成测试 | core | Adapter 在 core，core 内测试无跨模块扫描 |
| 前端 jest 测试 | frontend/admin | 单一前端目录 |

### 7.4 不在测试范围

- strategy.purge 内部业务校验测试：REQ-104~108 各 strategy 单测负责
- 真实事务回滚测试（@DataJpaTest）：需 Spring 容器 + 真实 DB，本需求不写。等 REQ-104~108 strategy 实现后端到端集成测试时覆盖
- 跨服务调用测试（file 服务调用）：本需求 framework 层不调 file 服务（由 strategy 内部调），等 REQ-104~108 覆盖
- 真实 file 服务 mock：strategy 内部行为，不在 framework 范围

## 8. Out of Scope

| 项 | 留给谁 |
|----|--------|
| `strategy.purge` 内部实现（删 `_deleted` 表 → 清理文件 → 删总览表） | REQ-104~108 各资源 |
| GET /{id} 详情端点（含 `_deleted` 表 `related_data` JSON 解析） | REQ-104~108（依赖具体资源字段） |
| restore 相关 Controller 端点 + 「恢复」按钮启用 | REQ-103（本需求仅替 REQ-103 落地 AppService 代码和 DTO，端点和前端按钮留 REQ-103） |
| 定时清理任务（30 天到期自动物理删除） | REQ-101 |
| 真实事务回滚的集成测试 | REQ-104~108 端到端验证时 |
| 性能压测 | 不需要（批量 ≤ 100 条） |
| 国际化 | 不需要（管理端中文） |

## 9. Further Notes

### 9.1 与 REQ-100 第 4.4.1 节的对接清单

REQ-100 第 4.4.1 节把以下端点划给 REQ-102，本需求全部实现：

| 端点 | REQ-100 划分 | REQ-102 实际 | 原因 |
|------|-------------|-------------|------|
| DELETE /{id} | REQ-102 | 实现 | 框架就位 |
| POST /batch-purge | REQ-102 | 实现 | 框架就位 |

### 9.2 与 REQ-103 的协调清单

本需求实现时**替 REQ-103 落地以下代码**（详见 4.4.5 节），REQ-103 后续实现时仅需追加 Controller 端点和前端按钮启用：

| 替 REQ-103 落地的代码 | REQ-102 实现 | REQ-103 后续工作 |
|---------------------|--------------|------------------|
| `self` 注入 | ✅ | 复用 |
| `findAllById` Port + Adapter | ✅ | 复用 |
| `RecycleBinAppService.restore` 重构 | ✅（落地 REQ-103 设计） | 验证已存在即可 |
| `RecycleBinAppService.restoreInNewTransaction` | ✅ | 验证已存在即可 |
| `RecycleBinAppService.batchRestore` 改造 | ✅ | 验证已存在即可 |
| `BatchRestoreRequest` DTO | ✅ | 复用 |
| `BatchRestoreResult` DTO | ✅ | 复用 |
| `POST /{id}/restore` 端点 | ❌ | REQ-103 新增 |
| `POST /batch-restore` 端点 | ❌ | REQ-103 新增 |
| 行内「恢复」按钮启用 | ❌ | REQ-103 启用 |
| 工具栏「批量恢复」按钮启用 | ❌ | REQ-103 启用 |
| `services/recycleBin.ts` `restoreItem` / `batchRestoreItems` | ❌ | REQ-103 追加 |
| `RecycleBinItemStrategy.restore` JavaDoc 强化 | ❌ | REQ-103 强化（本需求不动） |

### 9.3 永久删除的 file 服务清理职责

本需求 framework 层**不调 file 服务**。文件清理是 `strategy.purge` 内部行为（按 REQ-100 第 3.3 节固化的物理清理路径）：

1. strategy.purge 内部读 `_deleted` 表 PO（含 file ID 信息）
2. 通过 `ResourceType.toBizTypes()` 拿到所有相关 bizType
3. 调 file 服务删除关联文件（具体调用方式由 REQ-104~108 各 strategy 决定）
4. 删除 `_deleted` 表记录
5. 删除 `recycle_bin` 总览表记录

framework 层只负责事务边界（保证上述 5 步原子性）和错误捕获（businessException → failures）。

### 9.4 Spring Security CSRF 已禁用（验证完毕）

项目 `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/config/SecurityConfig.java:36` 已显式 `csrf(AbstractHttpConfigurer::disable)`（admin 后端用 JWT 而非 cookie session）。DELETE 方法可直接使用，无 CSRF 风险。如未来切换为 cookie session 认证，需重新评估。

### 9.5 已知问题（本需求不修，留作未来改进）

| # | 问题 | 严重度 | 何时修 |
|---|------|-------|--------|
| 1 | `RecycleBinItemStrategyRegistry.get(type)` 抛 `BusinessException(501, "资源类型 " + type + " 暂未接入回收站")` 中 `type` 是 `ResourceType.name()`（如 `KNOWLEDGE_CATEGORY`），对中文管理员不友好 | 低 | REQ-104 对接第一个资源后 `supportedTypes` 不再为空，本错误极少触发；如需修，改 `Registry.get()` 用 `type.displayName()` 包装消息（属 REQ-100 范围的迭代优化，不属本需求；同 REQ-103） |
| 2 | 批量永久删除与批量恢复的 DTO 完全对称但独立，存在代码重复 | 低 | 如果未来出现第 3 种批量操作，可考虑抽取 `BatchOperationResult<T>` 泛型；当前 2 个需求重复成本可接受 |
| 3 | 批量 Modal 强制输入选中数量而非资源名称，对于熟悉系统的用户稍繁琐 | 低 | 设计权衡：数量比名称更短，输入成本更低；防误操作足够 |

### 9.6 验收清单（DoD）

#### 后端

- [ ] `BatchPurgeRequest` + `BatchPurgeResult` DTO 创建
- [ ] `BatchRestoreRequest` + `BatchRestoreResult` DTO 创建（替 REQ-103 落地）
- [ ] `RecycleBinController` 追加 2 个端点（DELETE /{id} + POST /batch-purge）
- [ ] `RecycleBinAppService.purge(Long)` 重构：findById + 委托 `self.purgeInNewTransaction(item)`（自身不加 `@Transactional`）
- [ ] `RecycleBinAppService.purgeInNewTransaction(RecycleBinItem item)` 新增：`@Transactional(REQUIRES_NEW)` + public + JavaDoc 标注「仅内部用」
- [ ] `RecycleBinAppService.batchPurge` 改造为方案 B（findAllById + 循环 self.purgeInNewTransaction）
- [ ] **替 REQ-103 落地**：`restore(Long)` 重构 + `restoreInNewTransaction` 新增 + `batchRestore` 改造
- [ ] `RecycleBinAppService` 构造器自注入 `self`
- [ ] `RecycleBinItemRepositoryPort` 新增 `findAllById(Collection<Long>)`
- [ ] `RecycleBinItemRepositoryAdapter` 实现 `findAllById`
- [ ] `RecycleBinItemStrategy` JavaDoc 强化「三步物理删除 + 文件清理 + 原子性保证」契约
- [ ] `RecycleBinControllerWebMvcTest` 全部测试通过（含 DELETE 方法支持验证）
- [ ] `RecycleBinAppServiceTest` 全部测试通过（含 restore 相关用例）
- [ ] `RecycleBinItemRepositoryAdapterIT` `findAllById` 测试通过

#### 前端

- [ ] `services/recycleBin.ts` 追加 `purgeItem` / `batchPurgeItems` / `BatchPurgeResult`
- [ ] `RecycleBinTable.tsx` 行内「永久删除」按钮启用 + Popconfirm + danger 样式
- [ ] `RecycleBinTable.tsx` 工具栏「批量永久删除」按钮启用 + Modal 强制输入 + danger 样式
- [ ] 三档 message 反馈（全成功 / 部分 / 全失败）
- [ ] `RecycleBinTable.test.tsx` 全部测试通过
- [ ] `npx tsc --noEmit` 通过

#### 文档

- [ ] `docs/prd/req-102-recycle-bin-purge.md` 本文档（**brainstorming 收尾已写**）
- [ ] `docs/requirements.md` REQ-102 状态：`idea → confirmed`（**brainstorming 收尾执行**，实现完成时由用户更新到 `done`）
- [ ] `docs/overview.md` 回收站章节更新（**实现完成后执行**，引用最终类路径）

### 9.7 文档维护（实现完成后执行）

| 文档 | 改动 |
|------|------|
| `docs/requirements.md` REQ-102 备注列 | 实现 done 后追加 PRD 链接 `prd/req-102-recycle-bin-purge.md`，状态 `confirmed → done` |
| `docs/requirements.md` REQ-103 备注列 | 追加说明「REQ-102 已落地 self 注入 / findAllById / 方案 B / restore 改造，REQ-103 实现时复用」 |
| `docs/overview.md` 回收站章节 | 更新「手动永久删除」状态：未实现 → 框架就位（待 REQ-104~108 对接） |
| `CLAUDE.md` 「Recycle Bin Convention」 | 无需改动（永久删除契约 REQ-100 已写） |
