# REQ-49 群组 — 成员管理 API（加入 / 退出 / 邀请）

## 产品定位

用户端群组成员管理 API。在 REQ-48「创建群组 + OWNER 自动入群」骨架基础上，扩展完整的成员加入 / 退出 / 邀请码闭环，让普通用户能加入他人群组、退出已加入群组、OWNER 能通过邀请码私密邀请成员。

**核心场景：**
- 用户 A 创建 OPEN 群组 → 用户 B 凭 groupId 直接加入
- 用户 A 创建 INVITE_ONLY 群组 → 用户 B 必须凭 A 分享的邀请码加入
- 任意成员退出群组（OWNER 除外，需先转让）
- OWNER 重新生成邀请码（应对邀请码泄露）

## 用户故事

**作为** 普通用户
**我想要** 加入他人创建的学习群组、退出已加入的群组、凭邀请码加入私密群组
**以便于** 参与多人协作的知识记忆玩法（积分 / 抽卡 / 收集 / 保底均基于群组维度）

**作为** 群组 OWNER
**我想要** 生成 / 重新生成邀请码、控制群组加入门槛（OPEN 或 INVITE_ONLY）
**以便于** 管理群组成员来源，防止陌生人涌入或邀请码泄露后被滥用

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 加入策略 | `OPEN` + `INVITE_ONLY` 双策略（`join_policy` 字段） | 覆盖大多数学习小组场景，邀请码留私密群组用；申请-审批（APPLICATION）作为第三种值推迟到独立需求（YAGNI） |
| 邀请码生成时机 | 群组创建时自动生成 | 用户无感，无额外交互步骤 |
| 邀请码格式 | 8 位 Crockford Base32（排除 I/L/O/U 易混淆字符） | 32^8 ≈ 1.1 万亿组合，碰撞概率极低；字符集易读易输入 |
| 邀请码唯一性 | 全局唯一（DB unique 约束） | 邀请码即可定位群组，前端无需先查 groupId |
| 邀请码生命周期 | 永久有效，OWNER 可重新生成 | 应对邀请码泄露场景，旧码立即失效 |
| 邀请码碰撞处理 | AppService 捕获 `DataIntegrityViolationException` 重试 1 次 | 32^8 容量下几乎不触发，重试兜底即可 |
| 退出约束 | ADMIN/MEMBER 直接退；OWNER 不能退（必须先转让，REQ-50 范围） | REQ-49 不跨入 REQ-50 转让逻辑；保护群组关联数据（积分 / 卡牌收集）不因 OWNER 误退丢失 |
| 退出语义 | 物理删除 group_member 记录（硬删除） | 成员关系本身不是「资源」，无回收站语义；与 REQ-48 创建时硬写入对称 |
| 邀请码错误返回 | 格式错和未匹配统一返回 `INVITE_CODE_INVALID` | 防侧信道枚举有效邀请码（安全设计） |
| API 路径风格 | RESTful 复数资源（延续 REQ-48） | 与项目惯例一致 |
| 邀请码可见性 | 仅创建后通过 `StudyGroupResponse` 返回 OWNER；`GroupMemberResponse` 不含邀请码 | 邀请码属于群组维度而非成员维度；未来 GET 群组详情接口决定其他角色可见性 |
| AppService 拆分 | `StudyGroupAppService`（创建 + 邀请码）+ `GroupMemberAppService`（加入 / 退出 / 查询身份） | 按聚合根维度拆分，职责清晰 |
| 成员列表查询 | 不在 REQ-49 范围 | 成员列表是管理端功能（REQ-63 群组管理端成员与积分管理页），用户端 API 不暴露 |
| 创建时 joinPolicy | `CreateStudyGroupRequest` 加可选字段，默认 OPEN | 向后兼容 REQ-48 现有调用 |
| 文件外键 | 不设物理外键 | 项目规范，关联通过应用层保证（沿用 REQ-48） |

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/study-groups/{id}/members` | 直接加入（仅 join_policy=OPEN 群组） |
| POST | `/api/study-groups/join-by-invite` | 凭邀请码加入（OPEN / INVITE_ONLY 均可） |
| DELETE | `/api/study-groups/{id}/members/me` | 退出群组（OWNER 拒绝，ADMIN/MEMBER 直接退） |
| POST | `/api/study-groups/{id}/invite-code/regenerate` | 重新生成邀请码（仅 OWNER） |
| GET | `/api/study-groups/{id}/members/me` | 查询当前用户在该群组的成员身份 |

### 鉴权

需 JWT 鉴权（`/api/**` 前缀自动拦截），无需 ADMIN 角色，未登录返回 401。`userId` 从 JWT 中提取（`SecurityUtils.getCurrentUserId()`），不依赖请求体传入。

### API 1 — 直接加入

**请求：** `POST /api/study-groups/{id}/members`（无请求体）

**响应（`GroupMemberResponse`，包裹在 `Result<T>`）：**

```json
{
  "code": 0,
  "data": {
    "id": 1,
    "groupId": 100,
    "userId": 200,
    "role": "MEMBER",
    "points": 0,
    "joinedAt": 1718800000000
  }
}
```

**业务流程：**

```
@Transactional
1. userId = SecurityUtils.getCurrentUserId()
2. group = studyGroupRepository.findById(groupId).orElseThrow(GROUP_NOT_FOUND)
3. if !group.isJoinPolicyOpen() → throw BusinessException(GROUP_JOIN_POLICY_MISMATCH)
4. if existsByGroupIdAndUserId(groupId, userId) → throw BusinessException(ALREADY_GROUP_MEMBER)
5. member = GroupMember.joinAsMember(groupId, userId)
6. try { saved = groupMemberRepository.save(member) }
   catch DataIntegrityViolationException → throw BusinessException(ALREADY_GROUP_MEMBER)  // 并发竞态：DB UNIQUE(group_id, user_id) 兜底
7. return GroupMemberAssembler.toResponse(saved)
```

### API 2 — 凭邀请码加入

**请求：** `POST /api/study-groups/join-by-invite`

```json
{
  "inviteCode": "ABC23456"
}
```

**字段规则：**
- `inviteCode`：必填，`@NotBlank`，`@Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{8}$")`（Crockford Base32 字符集，排除 I/L/O/U）

**响应：** 同 API 1（`GroupMemberResponse`）

**业务流程：**

```
@Transactional
1. userId = SecurityUtils.getCurrentUserId()
2. try { InviteCode.of(inviteCode) } 
   catch IllegalArgumentException → throw BusinessException(INVITE_CODE_INVALID)  // 格式错
3. group = studyGroupRepository.findByInviteCode(inviteCode)
                              .orElseThrow(() -> new BusinessException(INVITE_CODE_INVALID))  // 未匹配
4. if existsByGroupIdAndUserId(group.id, userId) → throw BusinessException(ALREADY_GROUP_MEMBER)
5. member = GroupMember.joinAsMember(group.id, userId)
6. try { saved = groupMemberRepository.save(member) }
   catch DataIntegrityViolationException → throw BusinessException(ALREADY_GROUP_MEMBER)  // 并发竞态兜底
7. return GroupMemberAssembler.toResponse(saved)
```

> **安全设计**：邀请码格式错和未匹配统一返回 `INVITE_CODE_INVALID`，攻击者无法通过错误消息枚举有效邀请码。

### API 3 — 退出

**请求：** `DELETE /api/study-groups/{id}/members/me`（无请求体）

**响应：** `Result<Void>`（`data: null`）

**业务流程：**

```
@Transactional
1. userId = SecurityUtils.getCurrentUserId()
2. member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                                 .orElseThrow(NOT_GROUP_MEMBER)
3. if member.getRole() == OWNER → throw BusinessException(OWNER_CANNOT_LEAVE)
4. groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId)
5. return Result.success(null)
```

### API 4 — 重新生成邀请码

**请求：** `POST /api/study-groups/{id}/invite-code/regenerate`（无请求体）

**响应（`StudyGroupResponse`）：**

```json
{
  "code": 0,
  "data": {
    "id": 100,
    "name": "...",
    "description": "...",
    "avatarFileId": 123,
    "avatarUrl": "https://...",
    "ownerId": 100,
    "joinPolicy": "INVITE_ONLY",
    "inviteCode": "NEWCODE9",
    "createdAt": 1718800000000,
    "updatedAt": 1718800000000
  }
}
```

**业务流程：**

```
@Transactional
1. userId = SecurityUtils.getCurrentUserId()
2. group = studyGroupRepository.findById(groupId).orElseThrow(GROUP_NOT_FOUND)
3. member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                                 .orElseThrow(NOT_GROUP_MEMBER)
4. if member.getRole() != OWNER → throw BusinessException(NOT_GROUP_OWNER)
5. group.regenerateInviteCode()
6. try { studyGroupRepository.save(group) }
   catch DataIntegrityViolationException {  // update 场景下唯一可能是 invite_code 唯一约束冲突（其他字段未变）
       group.regenerateInviteCode();
       try { studyGroupRepository.save(group); }
       catch DataIntegrityViolationException ex → throw BusinessException(INVITE_CODE_GENERATION_FAILED)  // 重试仍碰撞（极端）
   }
7. return StudyGroupAssembler.toResponse(group)
```

### API 5 — 查询当前用户身份

**请求：** `GET /api/study-groups/{id}/members/me`

**响应：** 同 API 1（`GroupMemberResponse`）

**业务流程：**

```
1. userId = SecurityUtils.getCurrentUserId()
2. group = studyGroupRepository.findById(groupId).orElseThrow(GROUP_NOT_FOUND)  // 群组存在性优先
3. member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                                 .orElseThrow(NOT_GROUP_MEMBER)
4. return GroupMemberAssembler.toResponse(member)
```

> 「查询自己身份」对前端判断角色至关重要：判断能否调 regenerate（需 OWNER）、展示当前角色、判断是否已加入（前端切换"加入"按钮状态）。

### Bean Validation

`JoinByInviteRequest.inviteCode`：`@NotBlank` + `@Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{8}$")`。Controller 用 `@Valid`，校验失败由 `GlobalExceptionHandler.handleValidationException` 兜底返回 400。

## 数据模型

### `study_group` 表加 2 列

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `join_policy` | VARCHAR(20) | NOT NULL DEFAULT 'OPEN' | 加入策略枚举：`OPEN` / `INVITE_ONLY` |
| `invite_code` | VARCHAR(8) | NOT NULL UNIQUE | 邀请码（8 位 Crockford Base32） |

**唯一索引：** `UNIQUE(invite_code)` — DB 层强约束全局唯一，AppService 重试机制兜底碰撞。

### `group_member` 表无变更

REQ-48 已建字段（id / group_id / user_id / role / points / joined_at）足够覆盖加入 / 退出场景。

### Crockford Base32 字符集

`0123456789ABCDEFGHJKMNPQRSTVWXYZ`（32 个字符，排除 `I` / `L` / `O` / `U`）

正则表达式：`^[0-9A-HJKMNP-TV-Z]{8}$`

### 数据库迁移

- 项目用 JPA `ddl-auto=update`（无 Flyway/Liquibase），新增列自动同步
- REQ-48 刚完成，无生产数据，不需要回填脚本
- 测试环境如已有 study_group 数据：手动 `TRUNCATE study_group` 让新约束生效，或 DROP 表让 JPA 重建

## 领域模型

### StudyGroup 聚合根（扩展 REQ-48）

**新增字段：**
- `joinPolicy: JoinPolicy`
- `inviteCode: InviteCode`（值对象）

**方法：**

| 方法 | 说明 |
|------|------|
| `create(name, description, avatar, ownerId, joinPolicy)` | 工厂方法（加 joinPolicy 参数；内部调 `InviteCode.generate()` 自动生成邀请码） |
| `reconstruct(...)` | 从 PO 重建（加 joinPolicy + inviteCode 参数） |
| `regenerateInviteCode()` | 内部调 `InviteCode.generate()`；更新 `updatedAt` |
| `isJoinPolicyOpen()` | AppService 校验直接加入时用 |
| `isJoinPolicyInviteOnly()` | 对称辅助方法 |
| `getInviteCodeValue()` | 返回 inviteCode.value，Assembler 调用 |

### GroupMember 聚合根（扩展 REQ-48）

**新增方法：**

- `joinAsMember(groupId, userId)` — 工厂方法，构造 `role=MEMBER` / `points=0` / `joinedAt=now`

**不加 `leave()` 方法**：退出业务由 AppService 层校验（OWNER 不能退）+ Repository.deleteByGroupIdAndUserId 完成。GroupMember 自身无内部不变量需要保护（YAGNI）。

### JoinPolicy 枚举（新增）

`core/domain/model/domainenum/JoinPolicy.java`：

```java
public enum JoinPolicy {
    OPEN,
    INVITE_ONLY
}
```

### InviteCode 值对象（新增）

`core/domain/model/vo/InviteCode.java`：

| 方法 | 说明 |
|------|------|
| `static InviteCode generate()` | `SecureRandom` 生成 8 位 Crockford Base32 |
| `static InviteCode of(String value)` | 校验格式（8 位 + 字符集），失败抛 `IllegalArgumentException` |
| `boolean matches(String input)` | 校验用户输入 |
| `String getValue()` | 取值（Converter / Assembler 用） |

### 出端口扩展

**StudyGroupRepository**（REQ-48 已有方法不变）：
- `findByInviteCode(String inviteCode): Optional<StudyGroup>` — **新增**

重新生成邀请码复用 `save()`，AppService 层捕获 `DataIntegrityViolationException` 重试 1 次。

**GroupMemberRepository**（REQ-48 已有方法不变）：
- `deleteByGroupIdAndUserId(Long groupId, Long userId): void` — **新增**

**不加** `findById` / `findByGroupId` / `countByGroupId`（YAGNI；`findByGroupIdAndUserId` 已覆盖「查询当前用户身份」，成员列表 / count 是 REQ-63 范围）。

## 持久化层

| 组件 | 操作 | 说明 |
|------|------|------|
| `StudyGroupPO` | 修改 | 加 `join_policy` 列（`@Enumerated(EnumType.STRING)`）+ `invite_code` 列（含 unique 约束） |
| `StudyGroupJpaRepository` | 修改 | 加派生查询方法 `findByInviteCode(String inviteCode)` |
| `GroupMemberJpaRepository` | 修改 | 加派生方法 `deleteByGroupIdAndUserId(Long groupId, Long userId)` |
| `StudyGroupConverter` | 修改 | MapStruct 加 joinPolicy 枚举映射 + InviteCode ↔ String 双向映射（default 方法） |
| `GroupMemberConverter` | 不变 | joinAsMember 字段集与 createOwner 相同 |

**注意：**
- `StudyGroupPO.inviteCode` 用 `@Column(name = "invite_code", nullable = false, unique = true, length = 8)` 强约束
- `StudyGroupPO.joinPolicy` 用 `@Enumerated(EnumType.STRING)` + `@Column(name = "join_policy", nullable = false)`，DB 默认值由 JPA 在 INSERT 时填充（不依赖 DB DEFAULT）
- Converter 中 `InviteCode ↔ String` 双向映射需 default 方法处理（值对象不能直接被 MapStruct 自动映射）

## 应用层 + API 层

### 包结构（app 模块）

```
com.knowledgegame.app/
├── api/
│   ├── controller/
│   │   ├── StudyGroupController.java       (修改：加 regenerateInviteCode)
│   │   └── GroupMemberController.java      (新增)
│   └── dto/
│       ├── CreateStudyGroupRequest.java    (修改：加 joinPolicy)
│       ├── StudyGroupResponse.java         (修改：加 joinPolicy + inviteCode)
│       ├── JoinByInviteRequest.java        (新增)
│       └── GroupMemberResponse.java        (新增)
└── application/
    ├── service/
    │   ├── StudyGroupAppService.java       (修改：create 透传 + 加 regenerateInviteCode)
    │   └── GroupMemberAppService.java      (新增)
    └── assembler/
        ├── StudyGroupAssembler.java        (修改：映射 joinPolicy + inviteCode)
        └── GroupMemberAssembler.java       (新增)
```

### StudyGroupController（修改）

```java
@RestController
@RequestMapping("/api/study-groups")
public class StudyGroupController {
    private final StudyGroupAppService appService;

    @PostMapping
    public Result<StudyGroupResponse> create(@Valid @RequestBody CreateStudyGroupRequest request) { ... }

    @PostMapping("/{id}/invite-code/regenerate")
    public Result<StudyGroupResponse> regenerateInviteCode(@PathVariable Long id) {
        return Result.success(appService.regenerateInviteCode(id));
    }
}
```

### GroupMemberController（新增）

```java
@RestController
@RequestMapping("/api/study-groups")
public class GroupMemberController {
    private final GroupMemberAppService appService;

    @PostMapping("/{id}/members")
    public Result<GroupMemberResponse> joinDirectly(@PathVariable Long id) {
        return Result.success(appService.joinDirectly(id));
    }

    @PostMapping("/join-by-invite")
    public Result<GroupMemberResponse> joinByInvite(@Valid @RequestBody JoinByInviteRequest request) {
        return Result.success(appService.joinByInvite(request.getInviteCode()));
    }

    @DeleteMapping("/{id}/members/me")
    public Result<Void> leave(@PathVariable Long id) {
        appService.leave(id);
        return Result.success(null);
    }

    @GetMapping("/{id}/members/me")
    public Result<GroupMemberResponse> getCurrentMember(@PathVariable Long id) {
        return Result.success(appService.getCurrentMember(id));
    }
}
```

> 注意：`join-by-invite` 路径不包含 `{id}` 占位符（邀请码全局唯一即可定位群组），与 `/{id}/members` 同在 `/api/study-groups` 前缀下。Spring MVC 路径匹配优先具体路径，不会冲突。

### StudyGroupAppService（修改）

```java
@Service
public class StudyGroupAppService {
    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FileServiceClient fileServiceClient;

    @Transactional
    public StudyGroupResponse create(CreateStudyGroupRequest request) {
        Long ownerId = SecurityUtils.getCurrentUserId();
        FileRef avatar = resolveAvatar(request.getAvatarFileId(), ownerId);
        JoinPolicy joinPolicy = request.getJoinPolicy() != null ? request.getJoinPolicy() : JoinPolicy.OPEN;
        StudyGroup group = StudyGroup.create(request.getName(), request.getDescription(), avatar, ownerId, joinPolicy);
        StudyGroup saved = studyGroupRepository.save(group);
        GroupMember owner = GroupMember.createOwner(saved.getId(), ownerId);
        groupMemberRepository.save(owner);
        return StudyGroupAssembler.INSTANCE.toResponse(saved);
    }

    @Transactional
    public StudyGroupResponse regenerateInviteCode(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
        if (member.getRole() != GroupRole.OWNER) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }
        group.regenerateInviteCode();
        try {
            studyGroupRepository.save(group);
        } catch (DataIntegrityViolationException e) {  // update 场景下唯一可能是 invite_code 唯一约束冲突
            group.regenerateInviteCode();
            try {
                studyGroupRepository.save(group);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ResultCode.INVITE_CODE_GENERATION_FAILED);
            }
        }
        return StudyGroupAssembler.INSTANCE.toResponse(group);
    }

    private FileRef resolveAvatar(Long fileId, Long ownerId) { ... }  // REQ-48 原有
}
```

### GroupMemberAppService（新增）

```java
@Service
public class GroupMemberAppService {
    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public GroupMemberResponse joinDirectly(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));
        if (!group.isJoinPolicyOpen()) {
            throw new BusinessException(ResultCode.GROUP_JOIN_POLICY_MISMATCH);
        }
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }
        GroupMember member = GroupMember.joinAsMember(groupId, userId);
        try {
            GroupMember saved = groupMemberRepository.save(member);
            return GroupMemberAssembler.INSTANCE.toResponse(saved);
        } catch (DataIntegrityViolationException e) {  // 并发竞态：DB uk_group_member_group_user 兜底
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }
    }

    @Transactional
    public GroupMemberResponse joinByInvite(String inviteCodeInput) {
        Long userId = SecurityUtils.getCurrentUserId();
        InviteCode inviteCode;
        try {
            inviteCode = InviteCode.of(inviteCodeInput);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.INVITE_CODE_INVALID);
        }
        StudyGroup group = studyGroupRepository.findByInviteCode(inviteCode.getValue())
                .orElseThrow(() -> new BusinessException(ResultCode.INVITE_CODE_INVALID));
        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }
        GroupMember member = GroupMember.joinAsMember(group.getId(), userId);
        try {
            GroupMember saved = groupMemberRepository.save(member);
            return GroupMemberAssembler.INSTANCE.toResponse(saved);
        } catch (DataIntegrityViolationException e) {  // 并发竞态兜底
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }
    }

    @Transactional
    public void leave(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
        if (member.getRole() == GroupRole.OWNER) {
            throw new BusinessException(ResultCode.OWNER_CANNOT_LEAVE);
        }
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    public GroupMemberResponse getCurrentMember(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
        return GroupMemberAssembler.INSTANCE.toResponse(member);
    }
}
```

### Assembler

**StudyGroupAssembler**（修改，REQ-48 已有）：
- toResponse 加 `joinPolicy` + `inviteCode` 映射
- `inviteCode` 从 `StudyGroup.getInviteCodeValue()` 取值

**GroupMemberAssembler**（新增）：
- `toResponse(GroupMember): GroupMemberResponse`
- `joinedAt` LocalDateTime → 毫秒时间戳（参考 REQ-48 `StudyGroupAssembler.toEpochMilli`）

## 错误处理

### 新增 ResultCode（`core/common/result/ResultCode.java`）

| 枚举 | code | 消息 | 触发场景 |
|------|------|------|---------|
| `GROUP_NOT_FOUND` | 404 | 群组不存在 | findById 空 |
| `GROUP_JOIN_POLICY_MISMATCH` | 400 | 该群组需要邀请码加入 | 直接加入但 INVITE_ONLY |
| `INVITE_CODE_INVALID` | 400 | 邀请码无效 | 格式错 / 未匹配（统一返回防侧信道枚举） |
| `ALREADY_GROUP_MEMBER` | 400 | 已是群组成员 | 加入时已存在 |
| `NOT_GROUP_MEMBER` | 403 | 非群组成员 | 退出 / 查询 me 时无记录 |
| `OWNER_CANNOT_LEAVE` | 400 | 群主不能退出，请先转让群组 | OWNER 退出 |
| `NOT_GROUP_OWNER` | 403 | 仅群主可操作 | 重新生成邀请码非 OWNER |
| `INVITE_CODE_GENERATION_FAILED` | 400 | 邀请码生成失败，请重试 | 重新生成时碰撞重试 1 次后仍失败（极端边界） |

### 错误场景映射

| 场景 | HTTP | ResultCode |
|------|------|------------|
| 未登录调用任意 API | 401 | （Spring Security 兜底） |
| inviteCode 格式错（少位 / 含 I/L/O/U） | 400 | FAIL（Bean Validation 触发） |
| 群组不存在 | 200 | GROUP_NOT_FOUND |
| 直接加入 INVITE_ONLY 群组 | 200 | GROUP_JOIN_POLICY_MISMATCH |
| 邀请码无效（格式错 / 未匹配） | 200 | INVITE_CODE_INVALID |
| 已加入再调加入 API | 200 | ALREADY_GROUP_MEMBER |
| 非成员退出 / 查询 me | 200 | NOT_GROUP_MEMBER |
| OWNER 调退出 | 200 | OWNER_CANNOT_LEAVE |
| 非 OWNER 调重新生成 | 200 | NOT_GROUP_OWNER |
| 邀请码碰撞（极端） | AppService 重试 1 次；重试仍碰撞 → 200 | INVITE_CODE_GENERATION_FAILED |
| 并发加入同一群组（同一用户双击 / 多请求并发） | 200 | ALREADY_GROUP_MEMBER（DB `uk_group_member_group_user` 兜底） |

> **注意：** BusinessException 由 GlobalExceptionHandler 统一返回 HTTP 200，通过 Result.code 区分业务错误类型。仅 Bean Validation 异常和系统异常会修改 HTTP 状态码。

## Impact Analysis

### 修改 / 新增文件清单

#### core / domain 层

| 文件 | 操作 |
|------|------|
| `domain/model/entity/StudyGroup.java` | 修改（加字段 + 方法） |
| `domain/model/entity/GroupMember.java` | 修改（加 joinAsMember） |
| `domain/model/domainenum/JoinPolicy.java` | 新增 |
| `domain/model/vo/InviteCode.java` | 新增 |
| `domain/port/outbound/StudyGroupRepository.java` | 修改（加 findByInviteCode） |
| `domain/port/outbound/GroupMemberRepository.java` | 修改（加 deleteByGroupIdAndUserId） |

#### core / infrastructure 层

| 文件 | 操作 |
|------|------|
| `infrastructure/db/entity/StudyGroupPO.java` | 修改（加列） |
| `infrastructure/db/repository/StudyGroupJpaRepository.java` | 修改（加 findByInviteCode） |
| `infrastructure/db/repository/GroupMemberJpaRepository.java` | 修改（加 deleteByGroupIdAndUserId） |
| `infrastructure/db/converter/StudyGroupConverter.java` | 修改（加映射） |

#### core / common

| 文件 | 操作 |
|------|------|
| `common/result/ResultCode.java` | 修改（加 8 个错误码） |

#### app / api 层

| 文件 | 操作 |
|------|------|
| `api/controller/StudyGroupController.java` | 修改 |
| `api/controller/GroupMemberController.java` | 新增 |
| `api/dto/CreateStudyGroupRequest.java` | 修改 |
| `api/dto/StudyGroupResponse.java` | 修改 |
| `api/dto/JoinByInviteRequest.java` | 新增 |
| `api/dto/GroupMemberResponse.java` | 新增 |

#### app / application 层

| 文件 | 操作 |
|------|------|
| `application/service/StudyGroupAppService.java` | 修改 |
| `application/service/GroupMemberAppService.java` | 新增 |
| `application/assembler/StudyGroupAssembler.java` | 修改 |
| `application/assembler/GroupMemberAssembler.java` | 新增 |

#### 测试

| 文件 | 操作 |
|------|------|
| `StudyGroupTest` / `GroupMemberTest` | 扩展（REQ-48 已有） |
| `InviteCodeTest` / `JoinPolicyTest` | 新增 |
| `StudyGroupConverterTest` | 扩展 |
| `StudyGroupRepositoryAdapterTest` / `GroupMemberRepositoryAdapterTest` | 扩展（findByInviteCode / deleteByGroupIdAndUserId / 唯一约束 / 默认值） |
| `StudyGroupAppServiceTest` | 扩展（加 regenerateInviteCode 分支） |
| `GroupMemberAppServiceTest` | 新增 |
| `StudyGroupControllerTest` | 扩展（加 regenerateInviteCode） |
| `GroupMemberControllerTest` | 新增 |
| `StudyGroupAssemblerTest` | 扩展 |
| `GroupMemberAssemblerTest` | 新增 |

#### 文档

| 文件 | 操作 |
|------|------|
| `docs/prd/req-49-group-member-mgmt.md` | 新增（本 PRD） |
| `docs/card-system-data-model.md` | 修改（同步 study_group 表加 2 列） |
| `docs/overview.md` | 修改（同步 API 清单） |
| `docs/requirements.md` | 修改（阶段一末尾 idea → confirmed；阶段二再 designed → ready-for-dev） |

### 不受影响的现有功能

- REQ-48 创建群组 API（请求体加可选字段 `joinPolicy`，向后兼容；现有调用不传时默认 OPEN）
- REQ-48 `POST /api/study-groups` 响应体新增 `joinPolicy` + `inviteCode` 字段（向前兼容：旧客户端忽略未知字段即可；项目刚上线无外部消费者，无破坏性影响）
- 其他用户端 API（User / KnowledgeCategory / UploadCredit / KnowledgeItem）
- 管理端全部功能
- File Service / M2M Auth / 网关 / Nacos
- 现有 IpSeries / CardTemplate / KnowledgeCategory / Question / KnowledgeItem / User 聚合根

### 依赖关系

- REQ-49 后端独立完成，无跨需求前置依赖（REQ-48 已 done）
- 前端 REQ-60「群组列表 + 创建/加入页面」依赖 REQ-49，但 REQ-49 不依赖 REQ-60

## 手工验收用例

> 以下用例在 REQ-60（群组列表 + 创建/加入页面）完成时执行，覆盖 5 个 API 的端到端行为。

### 前置准备

1. 启动 app 服务（端口 8082）
2. 注册两个测试用户：用户 A（群主 `test_owner`）+ 用户 B（成员 `test_member`），记录各自的 accessToken
3. 用户 A 创建两个群组：一个 `joinPolicy=OPEN`，一个 `joinPolicy=INVITE_ONLY`
   - 记录 OPEN 群组 ID → `GROUP_ID_OPEN`
   - 记录 INVITE_ONLY 群组 ID + inviteCode → `GROUP_ID_INVITE` / `CODE_INVITE`

### 用例清单

| # | API | 操作 | 预期 |
|---|-----|------|------|
| 1 | POST /{id}/members | 用户 B 直接加入 OPEN 群组 | 200，`role="MEMBER"`，`points=0`，`joinedAt` 为毫秒数字 |
| 2 | POST /{id}/members | 用户 B 重复加入同一群组 | `ALREADY_GROUP_MEMBER` (400) |
| 3 | POST /{id}/members | 用户 B 直接加入 INVITE_ONLY 群组 | `GROUP_JOIN_POLICY_MISMATCH` (400) |
| 4 | POST /join-by-invite | 用户 B 用正确邀请码加入 INVITE_ONLY 群组 | 200，`groupId=GROUP_ID_INVITE` |
| 5 | POST /join-by-invite | 空邀请码 | 400 FAIL（@NotBlank） |
| 6 | POST /join-by-invite | 7 位邀请码 | 400 FAIL（@Pattern） |
| 7 | POST /join-by-invite | 含排除字符 L 的邀请码（如 `ABCDEFGL`） | 400 FAIL（@Pattern，防侧信道） |
| 8 | POST /join-by-invite | 格式正确但不存在的邀请码（如 `ABC12345`） | `INVITE_CODE_INVALID` (400)，**不是** 404 |
| 9 | GET /{id}/members/me | 用户 B 查询在 INVITE_ONLY 群组的身份 | 200，`role="MEMBER"` |
| 10 | GET /{id}/members/me | 用户 A 查询自己身份 | 200，`role="OWNER"` |
| 11 | GET /{id}/members/me | 未加入群组的用户查询 | `NOT_GROUP_MEMBER` (403) |
| 12 | DELETE /{id}/members/me | 用户 B 退出 INVITE_ONLY 群组 | 200，`data:null` |
| 13 | GET /{id}/members/me | 用户 B 退出后再次查询 | `NOT_GROUP_MEMBER` (403)（验证退出生效） |
| 14 | DELETE /{id}/members/me | 用户 A（OWNER）尝试退出 | `OWNER_CANNOT_LEAVE` (400) |
| 15 | POST /{id}/invite-code/regenerate | 用户 B（非 OWNER）尝试重新生成 | `NOT_GROUP_OWNER` (403) |
| 16 | POST /{id}/invite-code/regenerate | 用户 A（OWNER）重新生成 | 200，`inviteCode` ≠ 旧值，`updatedAt` 更新 |
| 17 | POST /join-by-invite | 用旧邀请码加入 | `INVITE_CODE_INVALID`（验证旧码立即失效） |
| 18 | POST /join-by-invite | 用新邀请码加入 | 200（验证新码生效） |

### 回滚标准

```sql
ALTER TABLE study_group DROP COLUMN join_policy, DROP COLUMN invite_code;
```

- **新增文件**（约 10 个源文件 + 6 个测试文件）：直接删除
- **修改文件**：`git revert` REQ-49 提交（与 REQ-48 完成时的差异即 REQ-49 全部改动）
- **数据库**：手动 DROP 两列（如已建表）
- **文档**：手动恢复 `card-system-data-model.md` / `overview.md`

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| **领域层单测** | `StudyGroupTest`：`create` 加 joinPolicy / `regenerateInviteCode` 旧码≠新码 + updatedAt 更新 / `isJoinPolicyOpen` / `isJoinPolicyInviteOnly`。`GroupMemberTest`：`joinAsMember` 构造 role=MEMBER / points=0 / joinedAt 非空。`InviteCodeTest`：`generate` 8 位 Crockford 字符集 / `of(valid)` 正常 / `of(invalid)` 短/长/含 I/L/O/U 抛 IllegalArgumentException / `matches` 相同 true。`JoinPolicyTest`：枚举值 |
| **Converter / Assembler 单测** | `StudyGroupConverter` 双向映射 joinPolicy + InviteCode↔String。`StudyGroupAssembler` 响应含 joinPolicy + inviteCode。`GroupMemberAssembler` LocalDateTime → 毫秒 |
| **AppService 单测（Mockito）** | `StudyGroupAppService.regenerateInviteCode`：OWNER 成功 / 非 OWNER → NOT_GROUP_OWNER / 群组不存在 / 非成员 / 碰撞重试 1 次成功 / 重试仍碰撞 → INVITE_CODE_GENERATION_FAILED。`GroupMemberAppService`：`joinDirectly`（含 existsBy 检查通过 + save 抛 DataIntegrityViolationException → ALREADY_GROUP_MEMBER 的并发竞态分支）/ `joinByInvite`（含相同并发竞态分支）/ `leave`（3 分支）/ `getCurrentMember`（3 分支） |
| **Controller @WebMvcTest** | 5 个 API 各覆盖成功 + 业务异常透传；inviteCode @Pattern 失败 → 400；`addFilters=false` 禁用 Security |
| **Repository 集成测试（@DataJpaTest 真实 MySQL）** | `invite_code` 唯一约束：插两条相同 code → `DataIntegrityViolationException`。`join_policy` 默认值：插 PO 不设 → 查回为 "OPEN"。`findByInviteCode` 找到 / 未找到。`deleteByGroupIdAndUserId` 删除 / 无副作用 |

### 集成测试配置（参考 REQ-48 模式）

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import({StudyGroupRepositoryAdapter.class, GroupMemberRepositoryAdapter.class})
class StudyGroupRepositoryAdapterTest { ... }
```

### 测试穿透力保障（CLAUDE.md 全局铁律 10）

| 风险点 | 测试手段 |
|--------|---------|
| `invite_code` 唯一约束实际生效 | 集成测试：插两条相同 code → 断言 `DataIntegrityViolationException` |
| `join_policy` 默认值 OPEN 在 DB 层 | 集成测试：插 PO 不设 → 查回断言为 "OPEN" |
| `StudyGroup.create` 自动生成邀请码 | 单测：断言 inviteCode 非空 + 长度 8 + Crockford 字符集 |
| `@Pattern` 邀请码格式校验生效 | @WebMvcTest：POST 传 `"abc"` → 400 |
| JSON 序列化 `GroupMemberResponse` | @WebMvcTest 自动覆盖 Spring MVC 序列化 |
| Converter 双向映射不丢字段 | 单测：构造 PO → toDomain → toPO → 字段全等 |
| `@Pattern` 正则实际排除 L | 单测：`InviteCode.of("ABCDEFGL")` → IllegalArgumentException（含 L 触发）+ `@WebMvcTest`：传 `"ABCDEFGH"` → 200 成功 vs 传 `"ABCDEFGL"` → 400 |
| 并发加入竞态被 AppService 捕获 | AppService 单测：mock `save` 抛 `DataIntegrityViolationException` → 断言抛 `BusinessException(ALREADY_GROUP_MEMBER)` 而非穿透到 500 |
| `regenerateInviteCode` 重试仍碰撞返回友好错误 | AppService 单测：mock `save` 连续两次抛 `DataIntegrityViolationException` → 断言抛 `BusinessException(INVITE_CODE_GENERATION_FAILED)` |

### 手动验证

1. 创建 INVITE_ONLY 群组 → 直接加入返回 GROUP_JOIN_POLICY_MISMATCH，邀请码加入成功
2. 创建 OPEN 群组 → 直接加入 + 邀请码加入均成功
3. 重复加入 → ALREADY_GROUP_MEMBER
4. OWNER 调退出 → OWNER_CANNOT_LEAVE
5. 普通成员退出 → 成功，查 `group_member` 表已删除
6. 非 OWNER 调重新生成邀请码 → NOT_GROUP_OWNER
7. OWNER 调重新生成 → 旧 invite_code 在 DB 已替换，旧码加入返回 INVITE_CODE_INVALID
8. 输错邀请码（少 1 位 / 含字母 O）→ INVITE_CODE_INVALID
9. 查询当前身份：未加入群组 → NOT_GROUP_MEMBER；加入后 → 返回 role / points / joinedAt

### 回滚标准

详见 [Impact Analysis → 回滚标准](#回滚标准)。

## 未来扩展（不在 REQ-49 范围）

| 需求 | 涉及改动 |
|------|---------|
| 申请-审批加入（APPLICATION 第三种 join_policy） | 加 `JoinPolicy.APPLICATION` + `group_join_application` 表（status: PENDING/APPROVED/REJECTED）+ 申请 / 审批 API |
| OWNER 转让群组（REQ-50） | GroupMember 聚合加 `transferOwner(Long toUserId)` 行为方法；出端口加 `updateRoleByGroupIdAndUserId`；DELETE 退出 API 解除 OWNER 限制 |
| 设置 / 切换 join_policy | `PUT /api/study-groups/{id}/join-policy`（仅 OWNER），StudyGroup 加 `updateJoinPolicy(JoinPolicy)` |
| 群组成员列表查询（REQ-63 管理端） | GroupMemberRepository 加 `findByGroupId(Long, Pageable)` + `countByGroupId(Long)`；管理端 controller `/api/admin/study-groups/{id}/members` |
| 踢人（REQ-50 管理员管理） | `DELETE /api/study-groups/{id}/members/{userId}`（仅 OWNER/ADMIN），不能踢 OWNER |
| 用户端查询群组详情（GET /api/study-groups/{id}） | 决定邀请码可见性（推荐仅 OWNER/ADMIN 可见，MEMBER 不可见） |
| 群组列表查询（REQ-60 前端依赖） | `GET /api/study-groups`（已加入的群组列表）+ `GET /api/study-groups?ownerId=...`（自己创建的） |
