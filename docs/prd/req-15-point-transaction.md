# REQ-15：积分流水记录（point_transaction）

| 字段 | 值 |
|------|---|
| 需求编号 | REQ-15 |
| 状态 | designed |
| 优先级 | P0 |
| 前置依赖 | REQ-48 ✅（StudyGroup + GroupMember 聚合根） |
| 影响模块 | `knowledge-game-core`（domain + infrastructure）、`knowledge-game-app`（api + application） |

---

## 1. 背景

积分是知识游戏的单一货币，贯穿游戏结算、盲盒抽卡、直购、兑换、签到等所有核心场景。所有积分变动需要可审计、可查询、可对账。

数据模型设计见 [card-system-data-model.md](../card-system-data-model.md) 第 15 节。

**积分余额已存在于** `GroupMember.points` 字段（[core/domain/model/entity/GroupMember.java](../../backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/model/entity/GroupMember.java)），是 `(groupId, userId)` 维度的个人积分。本需求在此基础上新增"流水"维度。

## 2. 目标

1. **写入抽象**：提供 `PointTransaction` 实体 + `PointTransactionRepository` 出端口 + `PointTransactionService` 领域服务，作为所有积分变动场景的统一写入入口（供 REQ-11 / 19 / 20 / 21 / 22 / 23 / 24 / 52 / 68 等后期需求调用）
2. **查询接口**：提供两个用户端 REST 端点（群组视角 + 个人跨群组视角），支持权限驱动的可见性、分页、过滤、排序
3. **一致性保证**：余额变动与流水写入在同一事务，使用 `@Version` 乐观锁防止并发扣分冲突
4. **审计完整性**：流水表只追加（append-only），不提供 Update / Delete REST 端点；管理员误调整记反向流水抵消，原流水保留

## 3. 非目标

- **管理员调整 API**：由 REQ-52 独立实现（调用本需求的 `PointTransactionService`），不在本需求范围内
- **流水修改/删除**：不提供 Update / Delete REST 端点（审计完整性优先）
- **事件溯源**：不采用 event sourcing，`GroupMember.points` 为余额权威，`balance_after` 仅作快照
- **管理端 API**：本需求是用户端（frontend/user/），管理后台（frontend/admin/）暂无积分流水查询需求
- **跨群组总积分**：不实现"用户所有群组积分总和"概念，每条流水明确归属于单个 `(groupId, userId)`
- **流水导出**：不实现 Excel/CSV 导出（后期优化）

## 4. API 协议

### 4.1 端点 A：群组视角查询

```
GET /api/study-groups/{groupId}/point-transactions
```

**请求参数（Query）**

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `page` | int | 否 | 1 | 页码，从 1 开始 |
| `size` | int | 否 | 10 | 每页条数，最大 100 |
| `type` | String | 否 | — | `EARN` / `SPEND` |
| `referenceType` | String | 否 | — | `GACHA` / `PURCHASE` / `DECOMPOSE` / `GAME_REWARD` / `FLIP_REWARD` / `CHECK_IN` / `ADMIN_ADJUST` / `EXCHANGE_POINTS` |
| `userId` | Long | 否 | — | 仅 ADMIN/OWNER 有效；普通成员传也会被强制改为自己 |
| `startDate` | Long | 否 | — | 毫秒时间戳，闭区间 |
| `endDate` | Long | 否 | — | 毫秒时间戳，闭区间 |
| `sort` | String | 否 | `createdAt` | 允许字段：`createdAt`、`amount` |
| `order` | String | 否 | `desc` | `asc` / `desc` |

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 101,
        "groupId": 5,
        "userId": 42,
        "userNickname": "玩家小明",
        "userAvatarUrl": "https://cdn.example.com/avatars/42.png",
        "type": "EARN",
        "amount": 5,
        "referenceType": "CHECK_IN",
        "referenceId": null,
        "balanceAfter": 152,
        "createdAt": 1719791999000
      }
    ],
    "totalElements": 87,
    "totalPages": 9
  }
}
```

> **分页约定**：请求参数 `page` 从 1 开始，AppService 调用 Repository 时传 `page - 1`（PageResult.pageNumber 是 0-based）。响应不返回 `page` / `size`（请求时已知，无需回显），与现有 `QuestionPageResponse` 模式一致。

**权限驱动可见性**

| 调用者角色 | 行为 |
|-----------|------|
| MEMBER | 强制 `userId = currentUserId`，前端传 `userId` 被忽略 |
| ADMIN / OWNER | `userId` 不传 → 看全员；传 `userId` → 看指定人 |
| 非群组成员 | 返回 `403 NOT_GROUP_MEMBER`（已存在） |

### 4.2 端点 B：个人视角查询（跨群组）

```
GET /api/me/point-transactions
```

> **路径前缀说明**：群组相关查询统一使用 `/api/study-groups/{groupId}/...` 前缀，与现有 `StudyGroupController` / `GroupMemberController` 一致。个人跨群组视角不属于单个群组上下文，使用 `/api/me/...` 前缀。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` / `size` / `sort` / `order` / `type` / `referenceType` / `startDate` / `endDate` | 同端点 A | — | — |
| `groupId` | Long | 否 | 不传 = 跨所有群组 |

**响应**：`Result<PageResult<PointTransactionResponse>>`，每条响应额外携带 `groupName` + `groupAvatarUrl`（跨群组展示需要）。

```json
{
  "id": 101,
  "groupId": 5,
  "groupName": "面试冲刺群",
  "groupAvatarUrl": "https://cdn.example.com/groups/5.png",
  "userId": 42,
  "userNickname": "玩家小明",
  "userAvatarUrl": "...",
  "type": "EARN",
  "amount": 5,
  "referenceType": "CHECK_IN",
  "referenceId": null,
  "balanceAfter": 152,
  "createdAt": 1719791999000
}
```

强制 `WHERE user_id = currentUserId`，权限与群组无关。

### 4.3 端点 C：当前余额查询（轻量）

```
GET /api/study-groups/{groupId}/members/me/balance
```

返回当前用户在指定群组的积分余额。供游戏页面、抽卡页面高频调用。

```json
{ "code": 200, "data": { "groupId": 5, "userId": 42, "balance": 152 } }
```

> **说明**：本质是读 `GroupMember.points` 字段，无需查流水表。提供独立轻量端点避免列表分页查询的开销。

## 5. 核心设计

### 5.1 领域模型

#### 5.1.1 新增实体 `PointTransaction`（只读历史记录，不是聚合根边界）

文件：`core/domain/model/entity/PointTransaction.java`

```java
@Getter
public class PointTransaction {
    private Long id;
    private Long groupId;
    private Long userId;
    private TxType type;            // EARN / SPEND
    private int amount;
    private ReferenceType referenceType;
    private Long referenceId;        // nullable
    private int balanceAfter;
    private LocalDateTime createdAt;

    /** 工厂方法（仅由 GroupMember.earnPoints / spendPoints 调用） */
    public static PointTransaction record(Long groupId, Long userId, TxType type,
                                          int amount, ReferenceType referenceType,
                                          Long referenceId, int balanceAfter) {
        // 参数校验
        PointTransaction tx = new PointTransaction();
        tx.groupId = groupId;
        tx.userId = userId;
        tx.type = type;
        tx.amount = amount;
        tx.referenceType = referenceType;
        tx.referenceId = referenceId;
        tx.balanceAfter = balanceAfter;
        tx.createdAt = LocalDateTime.now();
        return tx;
    }

    /** 重建（Repository 加载，参数列表与字段一一对应） */
    public static PointTransaction reconstruct(Long id, Long groupId, Long userId,
                                                TxType type, int amount,
                                                ReferenceType referenceType, Long referenceId,
                                                int balanceAfter, LocalDateTime createdAt) {
        PointTransaction tx = new PointTransaction();
        tx.id = id;
        tx.groupId = groupId;
        tx.userId = userId;
        tx.type = type;
        tx.amount = amount;
        tx.referenceType = referenceType;
        tx.referenceId = referenceId;
        tx.balanceAfter = balanceAfter;
        tx.createdAt = createdAt;
        return tx;
    }
}
```

#### 5.1.2 新增值对象 / 枚举

`core/domain/model/domainenum/TxType.java`

```java
/**
 * 积分变动方向。
 * 缩写 Tx = Transaction，保持类名简短，与项目其他枚举（GroupRole / JoinPolicy / CardRarity）一样以领域概念为主。
 */
public enum TxType { EARN, SPEND }
```

`core/domain/model/domainenum/ReferenceType.java`

```java
/**
 * 积分变动来源类型。
 * 缩写 Reference = 业务关联，保持类名简短。
 */
public enum ReferenceType {
    GACHA,              // 盲盒抽卡
    PURCHASE,           // 直购卡牌
    DECOMPOSE,          // 卡牌分解
    GAME_REWARD,        // 游戏结算（秒判/Boss/串联）
    FLIP_REWARD,        // 翻牌奖励
    CHECK_IN,           // 群组签到
    ADMIN_ADJUST,       // 管理员手动调整（REQ-52）
    EXCHANGE_POINTS    // 卡牌换积分（盲盒抽到不要的卡 / 卡包取出换分）
}
```

#### 5.1.3 GroupMember 新增行为方法

文件：`core/domain/model/entity/GroupMember.java`（修改）

```java
/** 增加积分（游戏/签到/分解/翻牌等场景调用）。
 *  返回待持久化的 PointTransaction 对象（由调用方在同一事务里 save）。
 *  amount 必须为正数，否则抛 IllegalArgumentException */
public PointTransaction earnPoints(int amount, ReferenceType refType, Long referenceId) {
    if (amount <= 0) {
        throw new IllegalArgumentException("amount 必须为正数");
    }
    this.points += amount;
    return PointTransaction.record(this.groupId, this.userId, TxType.EARN,
                                    amount, refType, referenceId, this.points);
}

/** 扣减积分（抽卡/直购/兑换等场景调用）。
 *  余额不足抛 BusinessException(POINT_TRANSACTION_INSUFFICIENT_BALANCE) */
public PointTransaction spendPoints(int amount, ReferenceType refType, Long referenceId) {
    if (amount <= 0) {
        throw new IllegalArgumentException("amount 必须为正数");
    }
    if (this.points < amount) {
        throw new BusinessException(ResultCode.POINT_TRANSACTION_INSUFFICIENT_BALANCE);
    }
    this.points -= amount;
    return PointTransaction.record(this.groupId, this.userId, TxType.SPEND,
                                    amount, refType, referenceId, this.points);
}
```

#### 5.1.4 新增出端口 `PointTransactionRepository`

文件：`core/domain/port/outbound/PointTransactionRepository.java`

```java
public interface PointTransactionRepository {
    PointTransaction save(PointTransaction tx);
    
    /** 群组维度分页查询（管理员视角：userId 可空） */
    PageResult<PointTransaction> findByGroup(Long groupId, Long userId,
                                              TxType type, ReferenceType refType,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              SortField sortField, int page, int size);
    
    /** 用户维度分页查询（个人视角：跨群组） */
    PageResult<PointTransaction> findByUser(Long userId, Long groupId,
                                             TxType type, ReferenceType refType,
                                             LocalDateTime startDate, LocalDateTime endDate,
                                             SortField sortField, int page, int size);
}
```

### 5.2 领域服务 `PointTransactionService`

文件：`core/domain/service/PointTransactionService.java`（**纯 POJO，无 @Service 注解**，由 `KnowledgeGameCoreAutoConfiguration` 注册为 `@Bean`）

```java
public class PointTransactionService {
    private final GroupMemberRepository memberRepo;
    private final PointTransactionRepository txRepo;
    // 构造器注入

    /** 统一写入入口。供 REQ-11/19/22/52/68 等所有写入场景调用。
     *  注意：@Transactional 实际在 application 层 AppService 上，此处不写注解 */
    public PointTransaction record(Long groupId, Long userId, TxType type,
                                    int amount, ReferenceType refType, Long referenceId) {
        GroupMember member = memberRepo.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new BusinessException(ResultCode.POINT_TRANSACTION_USER_NOT_IN_GROUP));
        PointTransaction tx = (type == TxType.EARN)
            ? member.earnPoints(amount, refType, referenceId)
            : member.spendPoints(amount, refType, referenceId);
        memberRepo.save(member);  // 触发 @Version 乐观锁
        return txRepo.save(tx);
    }
}
```

**注册到 Spring 容器**：在 `core/config/KnowledgeGameCoreAutoConfiguration.java` 新增一个 `@Bean` 方法：

```java
@Bean
public PointTransactionService pointTransactionService(
        GroupMemberRepository groupMemberRepository,
        PointTransactionRepository pointTransactionRepository) {
    return new PointTransactionService(groupMemberRepository, pointTransactionRepository);
}
```

### 5.3 应用层 AppService

文件：`backend/knowledge-game-app/src/main/java/com/knowledgegame/app/application/service/PointTransactionAppService.java`

**内部辅助对象 `PointTransactionQuery`**（应用层 query 对象，由 Controller 的 `PointTransactionQueryRequest.toQuery()` 转换而来）：

```java
// 位于 application/command/ 或 application/dto/query/，参考现有 Command 模式
public record PointTransactionQuery(
    Long userId,                       // 仅 ADMIN/OWNER 有效；普通成员被强制改为自己
    Long groupId,                      // 仅端点 B 个人视角使用（可空 = 跨群组）
    TxType type,                       // EARN / SPEND（可空）
    ReferenceType referenceType,       // 可空
    LocalDateTime startDate,           // 可空
    LocalDateTime endDate,             // 可空
    SortField sortField,               // 可空 → Adapter 默认 createdAt DESC
    int page,                          // 1-based
    int size
) {}
```

**冗余字段来源**（参考现有 `GroupMemberAppService` 批量预加载模式）：
- `userNickname` ← `User.nickname`
- `userAvatarUrl` ← `User.avatar.url()`（FileRef 值对象的 url 字段）
- `groupName` ← `StudyGroup.name`
- `groupAvatarUrl` ← `StudyGroup.avatar.url()`（FileRef 值对象的 url 字段）

```java
@Service
public class PointTransactionAppService {
    private final PointTransactionService txService;          // domain service
    private final PointTransactionRepository txRepo;          // domain port（查询走 repo）
    private final GroupMemberRepository memberRepo;            // domain port（权限校验）
    private final UserRepositoryPort userRepo;                 // ← 注意是 Port 后缀（不是 UserRepository）
    private final StudyGroupRepository groupRepo;              // 冗余 group 信息

    public PointTransactionAppService(PointTransactionService txService,
                                       PointTransactionRepository txRepo,
                                       GroupMemberRepository memberRepo,
                                       UserRepositoryPort userRepo,
                                       StudyGroupRepository groupRepo) {
        // 显式构造器（与现有 AppService 风格一致，不使用 @RequiredArgsConstructor）
    }

    /** 群组视角查询 — 权限驱动可见性 */
    @Transactional(readOnly = true)
    public PointTransactionPageResponse listByGroup(Long groupId, PointTransactionQuery query) {
        Long callerUserId = SecurityUtils.getCurrentUserId();  // 静态调用
        GroupMember caller = memberRepo.findByGroupIdAndUserId(groupId, callerUserId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        Long effectiveUserId = switch (caller.getRole()) {
            case MEMBER -> callerUserId;                       // 强制看自己
            case ADMIN, OWNER -> query.userId();               // null = 全员
        };

        PageResult<PointTransaction> page = txRepo.findByGroup(
            groupId, effectiveUserId, query.type(), query.referenceType(),
            query.startDate(), query.endDate(), query.sortField(),
            query.page() - 1, query.size());                    // page 1-based → 0-based 转换

        // 批量预加载冗余信息（避免 N+1）
        List<Long> userIds = page.getContent().stream().map(PointTransaction::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepo.findByIdIn(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

        List<PointTransactionResponse> content = page.getContent().stream()
            .map(tx -> PointTransactionAssembler.INSTANCE.toResponse(tx, userMap))
            .toList();

        return new PointTransactionPageResponse(content, page.getTotalElements(), page.getTotalPages());
    }

    /** 个人视角查询（跨群组） */
    @Transactional(readOnly = true)
    public PointTransactionPageResponse listByUser(PointTransactionQuery query) {
        Long callerUserId = SecurityUtils.getCurrentUserId();
        PageResult<PointTransaction> page = txRepo.findByUser(
            callerUserId, query.groupId(), query.type(), query.referenceType(),
            query.startDate(), query.endDate(), query.sortField(),
            query.page() - 1, query.size());

        // 批量预加载：userMap + groupMap
        List<Long> userIds = page.getContent().stream().map(PointTransaction::getUserId).distinct().toList();
        List<Long> groupIds = page.getContent().stream().map(PointTransaction::getGroupId).distinct().toList();
        Map<Long, User> userMap = userRepo.findByIdIn(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, StudyGroup> groupMap = groupRepo.findByIdIn(groupIds).stream()
            .collect(Collectors.toMap(StudyGroup::getId, Function.identity()));

        List<PointTransactionResponse> content = page.getContent().stream()
            .map(tx -> PointTransactionAssembler.INSTANCE.toResponse(tx, userMap, groupMap))
            .toList();

        return new PointTransactionPageResponse(content, page.getTotalElements(), page.getTotalPages());
    }

    /** 当前余额（轻量读，不查流水表） */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        GroupMember m = memberRepo.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
        return new BalanceResponse(groupId, userId, m.getPoints());
    }
}
```

> **关键模式**：
> 1. AppService 内部静态调 `SecurityUtils.getCurrentUserId()`，不通过 Controller 传参（与现有 AppService 一致）
> 2. `page - 1` 转换：API 接收 page=1，调 repo 时传 page-1（0-based）
> 3. 冗余信息走批量预加载（`findByIdIn` + Map 映射），不用 SQL join，与 `GroupMemberAppService` 模式一致

### 5.4 Controller

文件：`backend/knowledge-game-app/src/main/java/com/knowledgegame/app/api/controller/PointTransactionController.java`

```java
@RestController
public class PointTransactionController {
    private final PointTransactionAppService appService;

    public PointTransactionController(PointTransactionAppService appService) {
        this.appService = appService;  // 显式构造器，与项目其他 Controller 一致
    }

    @GetMapping("/api/study-groups/{groupId}/point-transactions")
    public Result<PointTransactionPageResponse> listByGroup(
            @PathVariable Long groupId,
            @ModelAttribute PointTransactionQueryRequest req) {
        return Result.success(appService.listByGroup(groupId, req.toQuery()));
    }

    @GetMapping("/api/me/point-transactions")
    public Result<PointTransactionPageResponse> listByUser(
            @ModelAttribute PointTransactionQueryRequest req) {
        return Result.success(appService.listByUser(req.toQuery()));
    }

    @GetMapping("/api/study-groups/{groupId}/members/me/balance")
    public Result<BalanceResponse> getBalance(@PathVariable Long groupId) {
        return Result.success(appService.getBalance(groupId));
    }
}
```

> **关键模式**：Controller 不直接调 `SecurityUtils`，AppService 内部自取（与现有 Controller 一致）。Controller 只做参数接收 + 结果返回，不含业务逻辑。

### 5.5 排序规范（遵循 REQ-86）

Adapter 内 `ALLOWED_SORT_FIELDS`：

```java
private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>();
static {
    ALLOWED_SORT_FIELDS.put("createdAt", "创建时间");
    ALLOWED_SORT_FIELDS.put("amount", "金额");
}
```

默认 `createdAt DESC`，非法字段抛 `BusinessException(400, "不支持的排序字段: xxx，允许的字段: [创建时间, 金额]")`。

### 5.6 持久化层

#### 5.6.1 PO：`PointTransactionPO`

文件：`core/infrastructure/db/entity/PointTransactionPO.java`

```java
@Entity
@Table(name = "point_transaction")
public class PointTransactionPO {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TxType type;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

#### 5.6.2 GroupMemberPO 新增 version 字段

```java
@Version
@Column(name = "version", nullable = false)
private int version;
```

#### 5.6.3 JPA Repository

```java
public interface PointTransactionJpaRepository extends JpaRepository<PointTransactionPO, Long> {
    // 动态 Specification 实现，按 group/user/type/referenceType/dateRange 组合
}
```

#### 5.6.4 Adapter

文件：`core/infrastructure/adapter/repoadapter/PointTransactionRepositoryAdapter.java`

- 实现 `PointTransactionRepository` 出端口
- 用 `Specification` + `PageRequest` 构建查询
- 排序字段调用 `SortFieldSpec.validate`，默认 `createdAt DESC`
- **N+1 解决方案**：Adapter 仅返回纯 PointTransaction 领域实体（不含 nickname/avatar 等冗余字段），冗余信息由 AppService 通过 `UserRepositoryPort.findByIdIn(userIds)` + `StudyGroupRepository.findByIdIn(groupIds)` 批量预加载后传给 Assembler 拼装。与现有 `GroupMemberAppService` 模式一致，不用 SQL join。

### 5.7 三层模型转换

```
PointTransactionPO (infrastructure)
  ↕ Converter (MapStruct, @Mapper, INSTANCE 单例)
PointTransaction (domain)
  ↕ Assembler (MapStruct, @Mapper, INSTANCE 单例, api/assembler/)
PointTransactionResponse (api/dto/response)
```

**Converter 行为说明**：
- 仅含 `toPO(PointTransaction)` 和 `toDomain(PointTransactionPO)` 两个方法
- **不含 `updatePO`** — 流水只追加（append-only），写入后永不修改。无 update 场景
- 与现有 Converter（如 `GroupMemberConverter` 含 `updatePO`）有差异，原因是聚合根生命周期不同

**Assembler 行为说明**：
- `toResponse(PointTransaction tx, Map<Long, User> userMap)` — **端点 A 群组视角**调用此重载（仅冗余 user 信息，因为同群组上下文已知 groupId）
- `toResponse(PointTransaction tx, Map<Long, User> userMap, Map<Long, StudyGroup> groupMap)` — **端点 B 个人跨群组视角**调用此重载（需冗余 user + group 信息，因流水可能跨多个群组）
- 时间字段：`LocalDateTime → Long`（epoch 毫秒），遵循项目 API 时间约定

## 6. 数据库迁移

### 6.1 新增表

```sql
CREATE TABLE point_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL COMMENT '群组ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    type VARCHAR(10) NOT NULL COMMENT '收入/支出（EARN/SPEND）',
    amount INT NOT NULL COMMENT '金额（正数）',
    reference_type VARCHAR(30) NOT NULL COMMENT '来源类型',
    reference_id BIGINT NULL COMMENT '关联业务ID（可空）',
    balance_after INT NOT NULL COMMENT '操作后余额',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    
    INDEX idx_group_user_created (group_id, user_id, created_at DESC),
    INDEX idx_user_created (user_id, created_at DESC),
    INDEX idx_group_created (group_id, created_at DESC),
    INDEX idx_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水（群组维度）';
```

**索引设计理由**：
- `idx_group_user_created` — 群组视角按用户过滤 + 时间倒序分页（最高频查询）
- `idx_user_created` — 个人跨群组视角时间倒序分页
- `idx_group_created` — 管理员看群组全员流水
- `idx_reference` — 反查"这笔流水对应什么业务"（如某次抽卡的扣分记录）

**索引命中分析**：
- 端点 A（群组视角）：
  - 管理员不传 userId → 走 `idx_group_created`（group_id 等值 + created_at 排序）
  - 传 userId → 走 `idx_group_user_created`（group_id + user_id 等值 + created_at 排序）
  - 加 `type` / `referenceType` / `dateRange` 过滤 → 在主索引范围扫描后再 filter（这些字段选择度低，不进索引）
- 端点 B（个人跨群组）：
  - 不传 groupId → 走 `idx_user_created`
  - 传 groupId → 仍走 `idx_user_created`（无 group_id+user_id+createdAt 复合索引；前端可选传 groupId 作为后过滤）。如性能不达标，后续可考虑加 `idx_user_group_created (user_id, group_id, created_at DESC)` 复合索引
- 单条反查（referenceType + referenceId）→ 走 `idx_reference`

**EXPLAIN 验证计划**（写入集成测试 8.3 节）：
- 6 种过滤组合各跑一次 EXPLAIN，断言 type=ref/range（不能是 ALL 全表扫描）
- 重点验证分页查询走索引排序（`Using index for order by`），避免 filesort

**不建外键**（项目约定，CLAUDE.md "禁止在数据库层面设置外键约束"）。

### 6.2 group_member 表新增字段

```sql
ALTER TABLE group_member ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
```

存量数据 `version=0` 即可，无需 backfill。

## 7. 错误处理

### 7.1 新增 ResultCode

文件：`core/common/result/ResultCode.java`

```java
POINT_TRANSACTION_INSUFFICIENT_BALANCE(400, "积分余额不足"),
POINT_TRANSACTION_INVALID_AMOUNT(400, "积分变动金额必须为正数"),
POINT_TRANSACTION_USER_NOT_IN_GROUP(400, "用户未加入该群组"),
POINT_TRANSACTION_REFERENCE_TYPE_INVALID(400, "不支持的积分来源类型"),
OPTIMISTIC_LOCK_CONFLICT(409, "数据已被其他操作修改，请重试"),  // 通用，可被其他需求复用
```

> **说明**：项目 ResultCode 沿用 HTTP 标准码（200/400/401/403/404/500/501），用枚举名区分场景而非自定义编号段。

### 7.2 错误场景清单

| 场景 | 触发条件 | HTTP | ResultCode |
|------|---------|------|-----------|
| 群组端点调用者不是成员 | groupId 中无 caller | 403 | NOT_GROUP_MEMBER（已存在） |
| 群组端点 groupId 不存在 | 数据库查不到 | 404 | GROUP_NOT_FOUND（已存在） |
| 普通成员传 userId 试图看别人 | role=MEMBER + userId != self | 不报错，自动改为自己 | — |
| 个人端点 groupId 不存在 | 数据库查不到 | 404 | GROUP_NOT_FOUND |
| 写入场景 amount ≤ 0 | 领域校验 | 400 | POINT_TRANSACTION_INVALID_AMOUNT |
| 写入场景余额不足 | points < amount | 400 | POINT_TRANSACTION_INSUFFICIENT_BALANCE |
| referenceType 非法字符串 | 不在枚举中 | 400 | POINT_TRANSACTION_REFERENCE_TYPE_INVALID |
| 并发冲突（@Version 失败） | 并发修改 GroupMember | 409 | OPTIMISTIC_LOCK_CONFLICT |
| sort 字段非法 | 不在白名单 | 400 | FAIL（含 "不支持的排序字段: xxx" 消息） |

## 8. 测试策略

### 8.1 领域层单测

`PointTransactionTest.java`
- `record()` 工厂方法参数校验（amount > 0、必填字段非空）

`GroupMemberTest.java`（扩展）
- `earnPoints()` 正数加分、`balanceAfter` 正确
- `earnPoints()` 0 / 负数 → 抛 `IllegalArgumentException`
- `spendPoints()` 正常扣分
- `spendPoints()` 0 / 负数 → 抛 `IllegalArgumentException`
- `spendPoints()` 余额不足 → 抛 `BusinessException(POINT_TRANSACTION_INSUFFICIENT_BALANCE)`
- `spendPoints()` 余额恰好等于扣分量 → 成功，余额变 0

### 8.2 领域服务单测

`PointTransactionServiceTest.java`（Mockito mock repository）
- `record(EARN)` 正常路径：member 找到 → earnPoints → 两个 save 被调用
- `record(SPEND)` 正常路径：同上但走 spendPoints
- `record()` 用户不存在 → 抛 `BusinessException`
- `record()` 余额不足 → 抛 `BusinessException`（透传 GroupMember 抛出的异常）

### 8.3 Repository 集成测试

`PointTransactionRepositoryAdapterTest.java`（`@DataJpaTest` + MySQL）
- 写入 + 按 group+user+created DESC 分页查询
- 4 种过滤组合（type / referenceType / dateRange / userId）
- 排序字段白名单（createdAt / amount）+ 非法字段抛错
- 个人跨群组视角查询（不传 groupId）
- 索引使用情况（EXPLAIN 验证，避免全表扫描）

### 8.4 Controller 切片测试

`PointTransactionControllerTest.java`（`@WebMvcTest(addFilters=false)`）
- 群组端点权限驱动可见性：
  - MEMBER 不传 userId → 看自己（断言查询参数被强制改为自己）
  - MEMBER 传 userId=别人 → 看自己（同上）
  - ADMIN 不传 userId → 看全员
  - ADMIN 传 userId=指定 → 看指定人
  - OWNER 看全员
  - 非成员调用 → 403
- 个人端点：跨群组返回 + 强制 `user_id = currentUserId`
- 余额端点：返回 `balance` 字段
- 参数校验：page/size 边界（0、负数、> 100）
- 排序字段非法 → 400

### 8.5 并发冲突测试

`PointTransactionConcurrencyTest.java`
- 启动两个线程同时调用 `record(SPEND, amount=100)`，初始余额 150
- 断言：一个成功（余额 50），另一个抛 `BusinessException(OPTIMISTIC_LOCK_CONFLICT)`
- 使用 `EntityManager.flush()` 触发 version 检查
- `CompletableFuture.allOf` 等待两个线程结束

**异常转换要求**：JPA 抛出的 `OptimisticLockException` 必须由应用层捕获并转换为 `BusinessException(OPTIMISTIC_LOCK_CONFLICT)`，最终通过 `GlobalExceptionHandler` 映射为 HTTP 409 响应。转换点有两种实现选择，二选一（在 Issue 拆解时定）：

1. **AppService 层 try-catch**（推荐）：在调用 PointTransactionService.record 的 AppService 方法里 try-catch `ObjectOptimisticLockingFailureException`（Spring 包装后的异常）转 BusinessException。优点：作用域清晰，只对积分写入相关方法生效。
2. **GlobalExceptionHandler 全局兜底**：在 `app/common/GlobalExceptionHandler.java` 新增 `@ExceptionHandler(OptimisticLockException.class)` / `@ExceptionHandler(ObjectOptimisticLockingFailureException.class)` 统一转 BusinessException。优点：所有聚合根 @Version 冲突都受益，可被后续需求复用。

### 8.6 集成测试（端到端）

`PointTransactionFlowIntegrationTest.java`（可选，在 Controller 层之上）
- 模拟一次完整的"答题 → 加分 → 流水写入 → 查询流水"链路
- 验证 `balanceAfter` 字段值正确反映快照

## 9. 验收清单

### 9.1 写入抽象层

- [ ] `PointTransaction` 实体 + `record()` / `reconstruct()` 工厂
- [ ] `TxType` / `ReferenceType` 枚举（8 种 referenceType）
- [ ] `GroupMember.earnPoints()` / `spendPoints()` 行为方法
- [ ] `PointTransactionRepository` 出端口接口（save + 两个 find）
- [ ] `PointTransactionService.record()` 领域服务
- [ ] **`KnowledgeGameCoreAutoConfiguration` 新增 `pointTransactionService()` `@Bean` 方法**（注册纯 POJO 领域服务到 Spring 容器）
- [ ] `PointTransactionPO` + `PointTransactionJpaRepository`
- [ ] `PointTransactionConverter`（MapStruct，仅 `toPO` + `toDomain`，无 `updatePO`）
- [ ] `PointTransactionRepositoryAdapter`（含 ALLOWED_SORT_FIELDS + SortFieldSpec）
- [ ] `GroupMemberPO` 新增 `@Version version` 字段
- [ ] `group_member` 表 DDL 迁移（version 列）

### 9.2 查询接口层

- [ ] `PointTransactionAppService.listByGroup()` — 权限驱动可见性
- [ ] `PointTransactionAppService.listByUser()` — 个人跨群组
- [ ] `PointTransactionAppService.getBalance()` — 轻量读
- [ ] `PointTransactionController` 三端点
- [ ] `PointTransactionQueryRequest` DTO（Controller 入参绑定 + 校验 + `toQuery()` 转换）
- [ ] `PointTransactionQuery` 应用层 query record（参考 5.3 节定义）
- [ ] `PointTransactionResponse` DTO（单条流水）
- [ ] `PointTransactionPageResponse` DTO（分页包装：`content` + `totalElements` + `totalPages`，仿 `QuestionPageResponse`）
- [ ] `BalanceResponse` DTO
- [ ] `PointTransactionAssembler`（MapStruct，domain → DTO，含 2 个 `toResponse` 重载对应端点 A/B）
- [ ] 冗余字段（userNickname/userAvatarUrl/groupName/groupAvatarUrl）查询优化（避免 N+1）

### 9.3 测试

- [ ] 领域层单测全覆盖（含边界）
- [ ] PointTransactionService 单测（mock repo）
- [ ] Repository 集成测试（含排序白名单 + 索引验证）
- [ ] Controller 切片测试（含 5 种权限场景）
- [ ] 并发冲突测试（两个线程同时扣分）
- [ ] 全部测试通过：`mvn test` 全绿

### 9.4 数据库 + 文档

- [ ] `point_transaction` 表 DDL 脚本（含 4 个索引）
- [ ] `group_member.version` 列迁移脚本
- [ ] 更新 `requirements.md` REQ-15 状态（idea → designed，PRD 通过审查后）
- [ ] 后期开发完成后再次更新（designed → ready-for-dev → in-progress → testing → done）
- [ ] 更新 `card-system-data-model.md` 第 15 节（如字段有调整）

## 10. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 并发抽卡扣分导致余额错乱 | `GroupMember.@Version` 乐观锁 + 冲突重试策略（由调用方实现） |
| 流水表数据量大影响分页查询 | 复合索引 `idx_group_user_created` / `idx_user_created` 覆盖最高频查询；EXPLAIN 验证 |
| 跨群组查询返回 groupName 冗余导致 N+1 | AppService 批量预加载（`UserRepositoryPort.findByIdIn` + `StudyGroupRepository.findByIdIn` + Map 映射），不用 SQL join |
| 后期需求扩展 referenceType 漏改 | 枚举集中定义，新增场景加枚举值即可（成本极低） |
| 历史 `group_member` 数据无 version | DDL 默认值 0，无需 backfill |
| JPA 乐观锁异常透出 JPA 原生异常 | AppService 或 GlobalExceptionHandler 捕获 `OptimisticLockException` 转 `BusinessException(OPTIMISTIC_LOCK_CONFLICT)`（详见 8.5） |

## 11. 后期需求对接说明

本需求提供的写入抽象供以下后期需求调用：

| 需求 | 写入场景 | 调用方式 |
|------|---------|---------|
| REQ-11（游戏结算） | 秒判/Boss/串联加分 | `txService.record(groupId, userId, EARN, amount, GAME_REWARD, gameSessionId)` |
| REQ-19 / 20（盲盒） | 抽卡扣分 | `txService.record(groupId, userId, SPEND, amount, GACHA, drawId)` |
| REQ-19 / 20（盲盒） | 不要的卡换积分 | `txService.record(groupId, userId, EARN, amount, EXCHANGE_POINTS, userCardId)` |
| REQ-21（直购） | 直购扣分 | `txService.record(groupId, userId, SPEND, amount, PURCHASE, productId)` |
| REQ-22（升星兑换） | 实体奖励兑换 | `txService.record(groupId, userId, SPEND, amount, EXCHANGE_POINTS, exchangeId)` |
| REQ-23（分解） | 卡牌分解加分 | `txService.record(groupId, userId, EARN, amount, DECOMPOSE, userCardId)` |
| REQ-24（翻牌） | 翻牌奖励加分 | `txService.record(groupId, userId, EARN, amount, FLIP_REWARD, flipId)` |
| REQ-52（管理员调整） | 手动调整加减 | `txService.record(groupId, userId, type, amount, ADMIN_ADJUST, null)` |
| REQ-68（签到） | 签到加分 | `txService.record(groupId, userId, EARN, amount, CHECK_IN, checkInRecordId)` |
