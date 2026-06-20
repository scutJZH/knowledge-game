# REQ-103：回收站通用恢复框架

> 状态：`confirmed`
> 创建：2026-06-19
> 前置依赖：REQ-100（通用回收站系统，状态 `done`）
> 后置依赖：REQ-104~108（各资源对接回收站，状态 `idea`）— REQ-103 框架就位后由它们补 strategy.restore 真实实现

## 0. 前置依赖声明

| 编号 | 状态 | 依赖内容 |
|------|------|---------|
| REQ-100 | `done` | 提供以下可复用契约与基础设施（详见 REQ-100 PRD）： |

- **AppService 留后方法签名**：`RecycleBinAppService.restore(Long)` / `batchRestore(List<Long>)` 已透传到 `strategyRegistry.get(type).restore(id)`，本需求在此基础上加事务、错误处理、响应结构
- **策略接口**：`RecycleBinItemStrategy<T>` 第 4.1.4 节已定义 `restore(recycleBinId)` + `batchRestore(ids)` 默认实现
- **注册中心**：`RecycleBinItemStrategyRegistry` 已实现，本需求交付时 `supportedTypes()` 返回空集（5 个策略 Bean 由 REQ-104~108 实现）
- **总览表**：`recycle_bin` 已建表，含 `restore_deadline` 字段（30 天保留期）
- **ResultCode 枚举**：`NOT_FOUND(404)` / `NOT_IMPLEMENTED(501)` 已存在，本需求直接使用
- **「恢复后强制 INACTIVE」契约**：REQ-100 第 4.1.5 节已固化，本需求在 strategy JavaDoc 中强化约束

**向前兼容检查**：REQ-104~108 将实现 strategy.restore 真实逻辑，必须遵守本需求固化的接口契约（详见第 4 节）和「强制 INACTIVE」语义。REQ-101（定时清理）和 REQ-102（手动永久删除）独立于恢复路径，无冲突。

## 1. Problem Statement

管理员在回收站列表页看到误删的资源，无法将其恢复。当前 `RecycleBinAppService.restore/batchRestore` 仅是透传签名，未挂 Controller 端点；前端「恢复」按钮 disabled + tooltip「等待资源对接」；本需求交付前**整个恢复链路不可用**。

REQ-100 已搭好框架（接口、注册中心、总览表），但**恢复动作的实际入口（端点 + UI）和框架级事务/错误处理尚未实现**。REQ-103 负责把这条链路从「未通」打通到「能调通」，让 REQ-104~108 各资源对接时只需实现 `strategy.restore` 内部逻辑即可自动贯通。

## 2. Solution

**交付恢复链路的完整骨架**：HTTP 端点 + AppService 框架性增强（事务边界、错误捕获、批量部分成功响应）+ 前端按钮启用。

**不实现 `strategy.restore` 内部具体恢复逻辑**（_deleted 表读取、关联校验、INSERT 原表、DELETE 详情表）— 那是 REQ-104~108 各资源的责任。本需求交付时 supportedTypes 仍为空，用户点恢复按钮会得到 501「资源类型 X 暂未接入回收站」（来自 `strategyRegistry.get(type)` 抛出的 BusinessException），等 REQ-104 对接第一个资源后自动贯通。

### 2.1 核心设计决策汇总

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | REQ-103 范围 | 纯框架（端点 + AppService 增强 + 前端按钮 + 接口契约），strategy.restore 实现留 REQ-104~108 | 避免越俎代庖；保证 REQ-104~108 各资源 strategy 自由设计 |
| 2 | 「恢复后强制 INACTIVE」契约兜底 | 仅依赖 strategy，AppService 不跨层兜底 | AppService 是流程编排层，跨层访问 5 个原表 repo 违反 DDD；契约靠 JavaDoc + REQ-104~108 单测断言固化 |
| 3 | 批量恢复原子性 | 逐条独立事务 + 部分成功响应（`successIds` + `failures`） | 用户预期"批量可部分成功"；事务边界清晰 |
| 4 | 批量恢复性能优化 | 方案 B：阶段 1 批量预校验（`findAllById`）+ 阶段 2 逐条独立事务执行 | 消除"记录不存在"类失败免走 strategy；strategy 内部失败必须逐条 try-catch；真批量 SQL 优化留 REQ-104~108 |
| 5 | 批量恢复 HTTP 状态码 | 总是 200，成败由响应体描述 | 避免不通用 207；前端按 `failures.length` 决定 message 颜色 |
| 6 | 单条恢复事务 | `@Transactional(REQUIRES_NEW)` 标注在 `restoreInNewTransaction` 上，`restore(Long)` 自身无事务注解（仅做 findById + 委托） | 事务边界集中在 `restoreInNewTransaction`；`restore(Long)` 和 `batchRestore` 都通过 `self` 代理调用它，每次开启新事务保证原子性 |
| 7 | 事务自调用问题 | 注入 `self`（同 bean 自己）调 `restoreInNewTransaction` | Spring AOP 自调用失效；通过构造器自注入解决 |
| 8 | 错误消息来源 | BusinessException → 用 `getMessage()`；其他异常 → "恢复失败，请联系管理员" 兜底 | 区分业务可预期失败和系统异常；避免泄露内部细节 |
| 9 | 前端按钮启用条件 | 全启用（不再按 supportedTypes 动态判断） | 架构诚实；REQ-104 对接后自动贯通；501 是合法的"未对接"信号 |
| 10 | 前端二次确认 | `Popconfirm` 轻量弹窗，文案明确"恢复后将以停用状态回到原列表，需手动启用" | 明确契约；避免用户误以为"恢复=立即生效" |
| 11 | GET /{id} 详情端点 | 不在本需求范围，推迟到 REQ-104~108 | 详情含 `_deleted` 表 `related_data` JSON 解析，依赖具体资源字段，无法在通用框架中实现 |
| 12 | `groupByResourceType` 是否保留 | `batchRestore` 不再使用，改为方案 B 直接逐条调；`batchPurge` 内部仍用（属 REQ-102 范围，本需求不动） | `batchRestore` 的分组无意义（strategy.batchRestore 默认循环单条）；`batchPurge` 留 REQ-102 改造。**注意**：若 REQ-102 先于本需求合入主分支，此决策被 [REQ-102 PRD](req-102-recycle-bin-purge.md) §2.1 决策#14 覆盖（`groupByResourceType` 已被 REQ-102 彻底删除，`batchPurge` 也走了方案 B），REQ-103 实现时直接复用现有代码，无需关心此决策 |

## 3. User Stories

1. 作为管理员，我希望在回收站列表点击行内「恢复」按钮，能将误删的资源恢复到原列表（停用状态），以便继续使用其数据
2. 作为管理员，我希望恢复前看到二次确认弹窗，明确告知"恢复后将以停用状态回到原列表，需手动启用"，以避免误操作
3. 作为管理员，我希望恢复失败时（如关联分类已删除）看到清晰的错误消息，以便知道为何不能恢复
4. 作为管理员，我希望批量勾选多条回收站记录后点「批量恢复」一次性处理，以提升效率
5. 作为管理员，我希望批量恢复时部分失败不影响其他成功项，并看到「成功 X 条，失败 Y 条」的汇总，以便定位失败原因
6. 作为管理员，我希望批量恢复中全失败时也能看到第一条的具体失败原因，以便排障
7. 作为系统，我要求单条恢复是多步写入的原子操作（_deleted 删除 + 原表 INSERT + 关联重建），任一步失败必须整体回滚，避免数据不一致
8. 作为系统，我要求批量恢复逐条独立事务，A 条失败不回滚已成功的 B 条
9. 作为开发者，我希望 REQ-104~108 实现各资源 strategy.restore 时无需关心事务/错误处理，只需写"读快照→校验→INSERT→DELETE"业务逻辑，框架层自动处理事务边界
10. 作为开发者，我希望 strategy 接口 JavaDoc 明确写"恢复后强制 INACTIVE"契约，避免 REQ-104~108 实现者遗漏

## 4. Implementation Decisions

### 4.1 接口契约强化（core 层）

**`RecycleBinItemStrategy<T>` 接口的 `restore` 方法 JavaDoc 增强**（接口签名不变，仅文档约束）：

- 明确写："实现必须将原表行恢复为 `status='INACTIVE'`，不得恢复为原 status"
- 明确写："内部多步操作（读 _deleted → 校验 → INSERT 原表 → DELETE _deleted）的原子性由 AppService 层事务保证，strategy 实现无需自己加 `@Transactional`"
- 明确写："可恢复失败的场景（关联不存在、唯一约束冲突等）必须抛 `BusinessException`，由 AppService 捕获并归入批量响应的 failures"

**`batchRestore` 默认实现不变**：循环调单条（与事务边界正交，AppService 自行决定逐条事务）。

### 4.2 Controller（admin/api/controller/RecycleBinController.java 追加 2 个端点）

| 端点 | Method | 路径 | 入参 | 出参 | 异常 |
|------|--------|------|------|------|------|
| 单条恢复 | POST | `/api/admin/recycle-bin/{id}/restore` | `@PathVariable Long id` | `Result<Void>` | BusinessException（404/501/4xx）由全局异常处理器返回 |
| 批量恢复 | POST | `/api/admin/recycle-bin/batch-restore` | `@Valid @RequestBody BatchRestoreRequest` | `Result<BatchRestoreResult>` | 总是 200，成败由响应体描述 |

**鉴权**：所有 `/api/admin/**` 端点需 ADMIN 角色 JWT（沿用现有 Security 配置，本需求无变更）。

### 4.3 DTO 设计

#### 4.3.1 `BatchRestoreRequest`（admin/api/dto/request/）

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `ids` | `List<Long>` | 是 | `@NotEmpty` + `@Size(max=100)` + 元素 `@NotNull` | 待恢复的 recycleBinId 列表，上限 100 防超大请求 |

#### 4.3.2 `BatchRestoreResult`（admin/api/dto/response/）

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `successIds` | `List<Long>` | AppService 收集 | 成功恢复的 recycleBinId 列表（顺序与请求一致，过滤掉失败的） |
| `failures` | `List<Failure>` | AppService 收集 | 失败列表 |

**`BatchRestoreResult.Failure`** 嵌套静态类：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `id` | `Long` | recycleBinId | 失败的 recycleBinId |
| `errorMessage` | `String` | `BusinessException.getMessage()` 或兜底文案 | 业务可预期失败用原始中文消息；未知异常用 "恢复失败，请联系管理员" |

**字段设计要点**：
- 不返回 `errorCode` —— 前端只需展示文本，分类由 message 内容自然传达
- `successIds` 顺序与请求一致，便于前端比对
- `failures` 顺序按 AppService 遍历顺序（先预校验失败的，再 strategy 失败的）

**重复 id 处理语义**：请求中同一 id 重复出现时（如 `[42, 42, 43]`），AppService **不去重**：
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
      { "id": 43, "errorMessage": "关联分类「数学」已删除，无法恢复" },
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

#### 4.4.1 单条恢复 `restore(Long recycleBinId)`

```java
public void restore(Long recycleBinId) {
    RecycleBinItem item = recycleBinRepository.findById(recycleBinId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND.getCode(),
                    "回收站记录不存在: " + recycleBinId));
    self.restoreInNewTransaction(item);  // 通过 self 代理开启 REQUIRES_NEW 事务
}
```

**关键说明**：
- `restore` 自身**不加** `@Transactional`，仅做 findById 查询 + 委托
- 真正的事务边界由 `restoreInNewTransaction`（`@Transactional(REQUIRES_NEW)`）承担
- Controller 直接调 `restore(Long)`，**不要**直接调 `restoreInNewTransaction`

#### 4.4.2 批量恢复 `batchRestore(List<Long> recycleBinIds)` → 返回 `BatchRestoreResult`

```java
public BatchRestoreResult batchRestore(List<Long> recycleBinIds) {
    // 阶段 1：批量预校验（findAllById 一次查回，避免逐条 findById）
    List<RecycleBinItem> items = recycleBinRepository.findAllById(recycleBinIds);
    Set<Long> foundIds = items.stream().map(RecycleBinItem::getId).collect(Collectors.toSet());

    List<Long> successIds = new ArrayList<>();
    List<BatchRestoreResult.Failure> failures = new ArrayList<>();

    // 预校验失败的 id 直接入 failures（按请求顺序）
    for (Long id : recycleBinIds) {
        if (!foundIds.contains(id)) {
            failures.add(new BatchRestoreResult.Failure(id, "回收站记录不存在: " + id));
        }
    }

    // 阶段 2：逐条独立事务执行（item 已查，直接传 item，避免重复 findById）
    for (RecycleBinItem item : items) {
        try {
            self.restoreInNewTransaction(item);  // 通过 self 代理，每次新事务
            successIds.add(item.getId());
        } catch (BusinessException e) {
            failures.add(new BatchRestoreResult.Failure(item.getId(), e.getMessage()));
        } catch (Exception e) {
            failures.add(new BatchRestoreResult.Failure(item.getId(), "恢复失败，请联系管理员"));
        }
    }
    return new BatchRestoreResult(successIds, failures);
}
```

#### 4.4.3 内部事务方法 `restoreInNewTransaction(RecycleBinItem item)`

```java
/**
 * 内部事务方法：开启 REQUIRES_NEW 事务执行 strategy.restore。
 * <p>
 * 可见性：public（Spring AOP CGLIB 代理要求；保持 public 避免代理失效风险）。
 * 仅以下两处允许调用：
 * - {@link #restore(Long)}：单条恢复端点入口
 * - {@link #batchRestore(List)}：批量恢复阶段 2
 * Controller 不应直接调用此方法（应调 appService.restore(Long)）。
 *
 * @param item 已查询的回收站条目（避免重复 findById）
 */
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void restoreInNewTransaction(RecycleBinItem item) {
    strategyRegistry.get(item.getResourceType()).restore(item.getId());
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

#### 4.4.5 关键设计权衡（避免重复 DB 查询）

- 单条恢复端点：1 次 `findById` + 1 次 `strategy.restore` = 共 1 次回收站表查询
- 批量恢复：1 次 `findAllById`（阶段 1） + N 次 `strategy.restore` = 共 1 次回收站表查询（不再逐条 findById）
- 通过让 `restoreInNewTransaction` 直接接受 `RecycleBinItem` 对象（而非 `Long id`），消除"先批量查、又逐条查"的重复查询模式

#### 4.4.6 `purge` / `batchPurge` 不动

REQ-102 范围。本需求不修改这两个方法的实现，但保留原签名。

### 4.5 前端改造（frontend/admin/）

#### 4.5.1 行内「恢复」操作（RecycleBinTable.tsx）

| 项 | 实现 |
|----|------|
| 按钮启用 | 全启用（替换原 disabled + tooltip） |
| 二次确认 | `Popconfirm`，标题 "恢复该条目？"，描述 "恢复后将以「停用」状态回到原列表，需手动启用。"，确认 "恢复"，取消 "取消" |
| 调用 | `restoreItem(record.id)` |
| 成功反馈 | `message.success('恢复成功，已回到原列表（停用状态）')` + 列表刷新 |
| 失败反馈 | `message.error(e?.message \|\| '恢复失败')` |

#### 4.5.2 工具栏「批量恢复」按钮

| 项 | 实现 |
|----|------|
| 按钮启用条件 | `selectedRowKeys.length > 0`（替换原 disabled + tooltip） |
| 二次确认 | `Popconfirm`，标题 `批量恢复选中的 ${selectedRowKeys.length} 条？`，描述 "恢复后将以「停用」状态回到原列表，需手动启用。" |
| 调用 | `batchRestoreItems(selectedRowKeys)` |
| 全成功反馈（failures.length === 0） | `message.success(\`成功恢复 ${successIds.length} 条\`)` |
| 部分成功反馈（successIds.length > 0 && failures.length > 0） | `message.warning(\`成功 ${successIds.length} 条，失败 ${failures.length} 条\`)` |
| 全失败反馈（successIds.length === 0 && failures.length > 0） | `message.error(\`全部失败：${failures[0].errorMessage}\`)` |
| 「批量永久删除」按钮 | 保持 disabled + tooltip（REQ-102 范围） |

#### 4.5.3 API 封装追加（services/recycleBin.ts）

```typescript
export async function restoreItem(id: number): Promise<void> {
  await request(`/api/admin/recycle-bin/${id}/restore`, { method: 'POST' });
}

export async function batchRestoreItems(ids: number[]): Promise<BatchRestoreResult> {
  return request('/api/admin/recycle-bin/batch-restore', {
    method: 'POST',
    data: { ids },
  });
}

export interface BatchRestoreResult {
  successIds: number[];
  failures: Array<{ id: number; errorMessage: string }>;
}
```

## 5. Impact Analysis

### 5.1 Files to Modify

#### 后端 admin 模块

| 文件 | 改动 |
|------|------|
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/controller/RecycleBinController.java` | 追加 2 个端点：`POST /{id}/restore` + `POST /batch-restore` |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/dto/request/BatchRestoreRequest.java` | **新建**：批量恢复请求 DTO，含 `@NotEmpty` + `@Size(max=100)` 校验 |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/api/dto/response/BatchRestoreResult.java` | **新建**：批量恢复响应 DTO，含嵌套 `Failure` 静态类 |
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/application/service/RecycleBinAppService.java` | 1) `restore(Long)` 重构：内部 findById 后委托 `self.restoreInNewTransaction(item)`，自身不加 `@Transactional`<br>2) `batchRestore(List<Long>)` 改为返回 `BatchRestoreResult`，移除 `groupByResourceType` 分组，改方案 B 实现（findAllById + 循环）<br>3) **新增** `restoreInNewTransaction(RecycleBinItem item)` 方法（`@Transactional(REQUIRES_NEW)` + public + JavaDoc 标注「仅内部用」）<br>4) 构造器加 `self` 自注入<br>5) `purge`/`batchPurge` 签名保持不变（REQ-102 范围） |

#### 后端 core 模块

| 文件 | 改动 |
|------|------|
| `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/port/outbound/RecycleBinItemRepositoryPort.java` | **新增方法** `List<RecycleBinItem> findAllById(Collection<Long> ids)`：批量查询回收站条目（供 AppService 阶段 1 预校验用，避免逐条 findById） |
| `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/infrastructure/adapter/repoadapter/RecycleBinItemRepositoryAdapter.java` | **实现** `findAllById`：调 `jpaRepository.findAllById(ids)` + Converter `toDomain` 列表映射 |
| `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/service/recyclebin/RecycleBinItemStrategy.java` | 接口签名不变，仅强化 `restore` 方法 JavaDoc：「必须恢复为 INACTIVE」「原子性由 AppService 保证」「业务失败抛 BusinessException」 |

#### 后端 admin 模块测试

| 文件 | 改动 |
|------|------|
| `backend/knowledge-game-admin/src/test/java/com/knowledgegame/admin/api/controller/RecycleBinControllerWebMvcTest.java` | 追加测试：单条恢复成功/失败、批量恢复全成功/部分失败/全失败、参数校验 |
| `backend/knowledge-game-admin/src/test/java/com/knowledgegame/admin/application/service/RecycleBinAppServiceTest.java` | 追加测试：restore 成功/记录不存在/无 strategy、batchRestore 全成功/部分预校验失败/部分 strategy 失败/未知异常、self 调用验证 |

#### 前端 admin

| 文件 | 改动 |
|------|------|
| `frontend/admin/src/services/recycleBin.ts` | 追加 `restoreItem` + `batchRestoreItems` + `BatchRestoreResult` 接口 |
| `frontend/admin/src/pages/RecycleBin/components/RecycleBinTable.tsx` | 1) 行内操作列「恢复」按钮启用 + Popconfirm<br>2) 工具栏「批量恢复」按钮启用 + Popconfirm（条件启用）<br>3) 三档 message 反馈 |
| `frontend/admin/src/pages/RecycleBin/index.tsx` | 追加 `handleRestore` + `handleBatchRestore` 处理函数，传递给 Table 组件 |
| `frontend/admin/src/pages/RecycleBin/components/__tests__/RecycleBinTable.test.tsx` | **新建或追加**：行内恢复、批量恢复、message 三档反馈测试 |

### 5.2 Configuration Changes

无配置变更：
- 不新增配置项
- 不新增环境变量
- 不新增数据库迁移（recycle_bin 表 REQ-100 已建好，schema 不变）
- 不新增 Maven/npm 依赖

### 5.3 Affected Functionality

| 现有功能 | 影响评估 |
|---------|---------|
| 回收站列表页（REQ-100） | ✅ 无回归：列表查询逻辑、ProTable 渲染不变；仅新增按钮事件处理 |
| `RecycleBinAppService.list` / `supportedTypes` | ✅ 无回归：本需求不改这两个方法 |
| `RecycleBinAppService.purge` / `batchPurge` | ✅ 无回归：本需求不动这两个方法的实现（REQ-102 改造） |
| Strategy 接口 | ✅ 无回归：仅改 JavaDoc，签名不变，REQ-104~108 实现者无影响 |
| `ResultCode` 枚举 | ✅ 无变更：`NOT_FOUND(404)` / `NOT_IMPLEMENTED(501)` REQ-100 已新增 |
| 全局异常处理器 | ✅ 无回归：本需求不引入新异常类型 |
| Security Filter Chain | ✅ 无变更：`/api/admin/recycle-bin/**` 已在 ADMIN 角色保护范围内 |
| Spring 自注入 | ⚠ 注意点：Spring 4.3+ 支持构造器自注入，但需确认项目当前 Spring Boot 版本（3.x ✓）；若使用字段注入会有循环依赖警告，本需求用构造器注入规避 |

## 6. Verification Plan

### 6.1 Manual Verification Steps

> ⚠ 本需求交付时 `supportedTypes()` 为空（无 strategy Bean），点击「恢复」按钮预期得到 501「资源类型 X 暂未接入回收站」。**完整端到端验证留到 REQ-104 对接第一个资源后**。

**框架层手动验证**（本需求范围内）：

1. 启动 admin 后端 + 前端
2. 进入 `/system/recycle-bin`
3. **行内按钮启用检查**：
   - 期望：「恢复」按钮可点击，「批量永久删除」按钮仍 disabled
4. **点击行内「恢复」按钮**：
   - 期望：弹 Popconfirm，文案正确
   - 点确认 → 调 API → 由于无 strategy Bean，得到 501 → message.error("资源类型 X 暂未接入回收站")
5. **批量恢复（选中 2 条 + 1 个不存在的 id）**：
   - 用 curl 或 Postman 直接调 `POST /api/admin/recycle-bin/batch-restore` 带 `{ ids: [42, 43, 99999] }`
   - 期望：HTTP 200，响应 `{ successIds: [], failures: [{id:42, ...501...}, {id:43, ...501...}, {id:99999, "回收站记录不存在: 99999"}] }`
6. **空 ids 校验**：调 `POST /batch-restore` 带 `{ ids: [] }` → 期望 400 参数校验失败
7. **超过 100 ids**：调 101 个 id → 期望 400 参数校验失败
8. **单条不存在**：调 `POST /api/admin/recycle-bin/99999/restore` → 期望 404 「回收站记录不存在: 99999」

**端到端验证（推迟到 REQ-104）**：实现 IpSeries strategy.restore 后，删除一条 IpSeries → 回收站可见 → 点恢复 → IpSeries 列表 INACTIVE 状态可见 → 重新启用 → 数据完整。

### 6.2 Automated Test Coverage

#### 后端 admin 模块

**`RecycleBinControllerWebMvcTest`**（追加）：

| 用例 | 输入 | 期望 |
|------|------|------|
| 单条恢复成功 | mock `appService.restore` 无异常 | HTTP 200 + `Result<Void>` |
| 单条恢复失败（404） | mock `appService.restore` 抛 `BusinessException(404, "回收站记录不存在: 99")` | HTTP 200 + `Result{code:404, message:...}` |
| 单条恢复失败（501） | mock `appService.restore` 抛 `BusinessException(501, "资源类型 X 暂未接入回收站")` | HTTP 200 + `Result{code:501, message:...}` |
| 批量恢复全成功 | mock 返回 `BatchRestoreResult([42,43], [])` | HTTP 200 + 响应体含 successIds |
| 批量恢复部分失败 | mock 返回 `BatchRestoreResult([42], [{id:43, errorMessage:"..."}])` | HTTP 200 + 响应体含 failures |
| 批量恢复全失败 | mock 返回 `BatchRestoreResult([], [{id:42,...},{id:43,...}])` | HTTP 200 + successIds 空 |
| 空 ids | POST body `{"ids":[]}` | HTTP 400 参数校验 |
| 缺少 ids 字段 | POST body `{}` | HTTP 400 参数校验 |
| 超过 100 ids | POST body 含 101 个 id | HTTP 400 参数校验 |
| JSON 序列化 | 任意批量恢复请求 | `successIds` 是 `List<Long>` 数组（不是字符串），`failures[].id` 是 Long |

**`RecycleBinAppServiceTest`**（追加）：

| 用例 | mock 行为 | 期望 |
|------|----------|------|
| restore 成功 | `findById` 返回 item，`self.restoreInNewTransaction` 不抛异常 | `self.restoreInNewTransaction(item)` 被 verify 调用 1 次 |
| restore 记录不存在 | `findById` 返回 empty | 抛 BusinessException(NOT_FOUND) |
| restore 资源类型无 strategy | `findById` 返回 item，`self.restoreInNewTransaction` 调用时 mock 抛 BusinessException(501) | 异常向上抛（不捕获） |
| batchRestore 全成功 | `findAllById` 返回 3 条，`self.restoreInNewTransaction` 不抛异常 | successIds=[1,2,3], failures=[] |
| batchRestore 部分预校验失败 | `findAllById` 返回 [item1, item3]（item2 不存在） | failures 含 `{id:2, "回收站记录不存在: 2"}`，item1/item3 走 strategy |
| batchRestore strategy 失败 | `self.restoreInNewTransaction(item2)` 抛 BusinessException("关联分类已删除") | failures 含 `{id:2, "关联分类已删除"}` |
| batchRestore 未知异常 | `self.restoreInNewTransaction(item)` 抛 RuntimeException | failures 含 `{id:X, "恢复失败，请联系管理员"}` |
| batchRestore 不重复查 DB | `findAllById` 返回 N 条 | verify `findById` **0 次**（阶段 2 直接传 item，不再逐条查） |
| batchRestore self 调用验证 | 任意 | verify `self.restoreInNewTransaction` 被调用 N 次（N=找到的记录数） |

#### 前端 admin 模块

**`RecycleBinTable.test.tsx`**：

| 用例 | mock | 期望 |
|------|------|------|
| 行内「恢复」按钮可点击 | 渲染带数据的行 | 按钮存在且 enabled |
| 点击「恢复」→ Popconfirm 弹出 | 模拟 click | Popconfirm 文案可见 |
| Popconfirm 确认 → API 调用 | mock `restoreItem` resolve | 调用 1 次，message.success 显示 |
| 单条恢复失败 | mock `restoreItem` reject | message.error 显示 |
| 工具栏批量按钮：未选中 disabled | `selectedRowKeys=[]` | 按钮 disabled |
| 工具栏批量按钮：选中启用 | `selectedRowKeys=[1,2]` | 按钮 enabled，Popconfirm 标题含 "2" |
| 批量全成功 | mock `batchRestoreItems` resolve 返回 `{successIds:[1,2], failures:[]}` | message.success 显示含 "2" |
| 批量部分成功 | mock 返回 `{successIds:[1], failures:[{id:2,...}]}` | message.warning 显示 |
| 批量全失败 | mock 返回 `{successIds:[], failures:[{id:1,...},{id:2,...}]}` | message.error 显示含第一条 errorMessage |

**`npx tsc --noEmit`**：通过（`BatchRestoreResult` TS 接口与后端 DTO 字段对齐）。

### 6.3 Rollback Criteria

满足以下任一条件应回滚或修复后重发：

1. **后端单元测试失败**：Controller 或 AppService 测试不通过
2. **前端类型检查或 jest 测试失败**
3. **手动验证步骤 1-8 任一不通过**（框架层验证必须全过）
4. **回收站列表页（REQ-100 已交付功能）出现回归**：列表查询、ProTable 渲染、目录树加载异常
5. **应用启动失败**：Spring 自注入导致循环依赖错误

## 7. Testing Decisions

### 7.1 测试基础设施主动防御

**前端 jest + jsdom + Ant Design**：必须预置 `window.matchMedia` + `window.getComputedStyle` mock（Ant Design 5 依赖），否则 Popconfirm/Button 渲染报错。

### 7.2 self-injection 单测技巧

由于 `batchRestore` 和 `restore(Long)` 内部通过 `self.restoreInNewTransaction(item)` 调用，单测中 mock `self`：

```java
RecycleBinAppService self = mock(RecycleBinAppService.class);
RecycleBinAppService service = new RecycleBinAppService(repo, registry, self);

// 让 self.restoreInNewTransaction 调用不抛异常（默认 void）
doNothing().when(self).restoreInNewTransaction(any(RecycleBinItem.class));

service.batchRestore(List.of(1L, 2L));

verify(self, times(2)).restoreInNewTransaction(any(RecycleBinItem.class));
verify(repo, never()).findById(anyLong());  // 验证阶段 2 不重复查 DB
```

### 7.3 测试模块归属

| 测试对象 | 模块 | 原因 |
|---------|------|------|
| Controller @WebMvcTest | admin | Controller 在 admin，标准 `@AutoConfigureMockMvc(addFilters=false)` |
| AppService 单测 | admin | AppService 在 admin，无跨模块扫描 |
| 前端 jest 测试 | frontend/admin | 单一前端目录 |

**无 core 模块测试**：本需求 core 层仅改 strategy JavaDoc，无代码逻辑变更。

### 7.4 不在测试范围

- strategy.restore 内部业务校验测试：REQ-104~108 各 strategy 单测负责
- 真实事务回滚测试（@DataJpaTest）：需 Spring 容器 + 真实 DB，本需求不写。等 REQ-104~108 strategy 实现后端到端集成测试时覆盖
- 跨服务调用测试：本需求无 Feign/Nacos 依赖

## 8. Out of Scope

| 项 | 留给谁 |
|----|--------|
| `strategy.restore` 内部实现（读 _deleted → 校验 → INSERT 原表 → DELETE _deleted） | REQ-104~108 各资源 |
| GET /{id} 详情端点（含 `_deleted` 表 `related_data` JSON 解析） | REQ-104~108（依赖具体资源字段） |
| 单条/批量永久删除端点 + 「永久删除」按钮启用 | REQ-102 |
| 定时清理任务（30 天到期自动物理删除） | REQ-101 |
| 真实事务回滚的集成测试 | REQ-104~108 端到端验证时 |
| 性能压测 | 不需要（批量 ≤ 100 条） |
| 国际化 | 不需要（管理端中文） |

## 9. Further Notes

### 9.1 与 REQ-100 第 4.4.1 节的对接清单

REQ-100 第 4.4.1 节把以下端点划给 REQ-103，本需求只实现前 2 个，第 3 个推迟：

| 端点 | REQ-100 划分 | REQ-103 实际 | 原因 |
|------|-------------|-------------|------|
| POST /{id}/restore | REQ-103 | ✅ 实现 | 框架就位 |
| POST /batch-restore | REQ-103 | ✅ 实现 | 框架就位 |
| GET /{id}（详情查询） | REQ-103 | ❌ 推迟 | 详情含 `_deleted` 表字段，依赖具体资源，框架阶段无意义 |

### 9.2 「恢复后强制 INACTIVE」契约的兜底策略

REQ-100 第 4.1.5 节已固化契约。本需求选择不在 AppService 层做防御性兜底（如「调完 strategy 后再 UPDATE 原表 SET status=INACTIVE」），理由：

1. AppService 是流程编排层，跨层访问 5 个原表 repo 违反 DDD 分层
2. strategy 才知道字段名（如 `status` vs `Status` vs 枚举名）和具体表
3. 代码兜底会形成误导（"反正 AppService 兜底，strategy 不用管"），降低契约严肃性
4. 测试侧保证：每个 strategy 单测必须断言「恢复后 status=INACTIVE」，约束写进 REQ-104~108 各 PRD

### 9.3 `batchRestore` 不再按 ResourceType 分组

当前 `RecycleBinAppService.batchRestore` 用 `groupByResourceType` 按 ResourceType 分组后调 `strategy.batchRestore(ids)`。本需求改造移除这个分组，理由：

- `strategy.batchRestore` 默认实现就是循环调单条（REQ-100 4.1.4 节）
- 分组毫无价值（无法批量优化，因为 strategy.restore 仍逐条执行）
- 改为方案 B 后，AppService 直接逐条调，统一在 AppService 层收集结果，代码更清晰

**`batchPurge` 内部的 `groupByResourceType` 保留不动**（REQ-102 范围，由 REQ-102 决定是否同样改造）。

### 9.4 文档维护（实现完成后执行）

| 文档 | 改动 |
|------|------|
| `docs/requirements.md` REQ-103 备注列 | 实现 done 后追加 PRD 链接 `prd/req-103-recycle-bin-restore.md`，状态 `idea → done` |
| `docs/overview.md` 回收站章节 | 更新「恢复动作」状态：未实现 → 框架就位（待 REQ-104~108 对接） |
| `CLAUDE.md` 「Recycle Bin Convention」 | 无需改动（恢复契约 REQ-100 已写） |

### 9.5 已知问题（本需求不修，留作未来改进）

| # | 问题 | 严重度 | 何时修 |
|---|------|-------|--------|
| 1 | `RecycleBinItemStrategyRegistry.get(type)` 抛 `BusinessException(501, "资源类型 " + type + " 暂未接入回收站")` 中 `type` 是 `ResourceType.name()`（如 `KNOWLEDGE_CATEGORY`），对中文管理员不友好 | 低 | REQ-104 对接第一个资源后 `supportedTypes` 不再为空，本错误极少触发；如需修，改 `Registry.get()` 用 `type.displayName()` 包装消息（属 REQ-100 范围的迭代优化，不属本需求） |

### 9.6 验收清单（DoD）

#### 后端
- [ ] `BatchRestoreRequest` + `BatchRestoreResult` DTO 创建
- [ ] `RecycleBinController` 追加 2 个端点
- [ ] `RecycleBinAppService.restore(Long)` 重构：findById + 委托 `self.restoreInNewTransaction(item)`（自身不加 `@Transactional`）
- [ ] `RecycleBinAppService.restoreInNewTransaction(RecycleBinItem item)` 新增：`@Transactional(REQUIRES_NEW)` + public + JavaDoc 标注「仅内部用」
- [ ] `RecycleBinAppService.batchRestore` 改造为方案 B（findAllById + 循环 self.restoreInNewTransaction）
- [ ] `RecycleBinAppService` 构造器自注入 `self`
- [ ] `RecycleBinItemStrategy` JavaDoc 强化「恢复后强制 INACTIVE」契约
- [ ] `RecycleBinControllerWebMvcTest` 全部测试通过
- [ ] `RecycleBinAppServiceTest` 全部测试通过

#### 前端
- [ ] `services/recycleBin.ts` 追加 `restoreItem` / `batchRestoreItems` / `BatchRestoreResult`
- [ ] `RecycleBinTable.tsx` 行内「恢复」按钮启用 + Popconfirm
- [ ] `RecycleBinTable.tsx` 工具栏「批量恢复」按钮启用 + 条件启用 + Popconfirm
- [ ] 三档 message 反馈（全成功 / 部分 / 全失败）
- [ ] `RecycleBinTable.test.tsx` 全部测试通过
- [ ] `npx tsc --noEmit` 通过

#### 文档
- [ ] `docs/prd/req-103-recycle-bin-restore.md` 本文档（**brainstorming 收尾已写**）
- [ ] `docs/requirements.md` REQ-103 状态：`idea → confirmed`（**brainstorming 收尾执行**，实现完成时由用户更新到 `done`）
- [ ] `docs/overview.md` 回收站章节更新（**实现完成后执行**，引用最终类路径）
