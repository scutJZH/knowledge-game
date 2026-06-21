# REQ-50 群组 — 管理员设置与转让 API

## 产品定位

群组角色管理 API。在 REQ-48（创建群组）+ REQ-49（成员加入/退出/邀请）基础上，实现 OWNER 设置/撤销管理员、转让群主。踢人（`DELETE /api/study-groups/{id}/members/{userId}`）划入 REQ-63（群组管理端成员与积分管理页），不在本需求范围。

## 用户故事

**作为** 群组 OWNER
**我想要** 提升成员为管理员、撤销管理员、将群组转让给其他成员
**以便于** 分权管理和交接群组所有权

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 角色更新 API | 统一 `PUT /api/study-groups/{id}/members/{userId}` 传 `{"role":"ADMIN"\|"MEMBER"}` | 一个端点覆盖升/降，减少 API 数量 |
| 转让 API | 独立 `POST /api/study-groups/{id}/transfer-ownership` | 转让语义特殊（新旧 OWNER 对调），独立端点意图更明确 |
| 转让后原 OWNER 角色 | 自动变为 ADMIN | 保留管理权限，最常见做法 |
| 操作权限 | 仅 OWNER | ADMIN 无权提升/降级/转让 |
| 提升/降级幂等 | 已是目标角色时静默返回成功 | 减少前端分支判断 |
| OWNER 升降自己 | 拒绝（promoteToAdmin/demoteToMember 内部抛异常） | OWNER 角色变更必须走转让 |
| 只能转让给同群成员 | 校验 `target.groupId == groupId` | 防止跨群组转让 |
| leave() 修改 | 不需要 | 转让后原 OWNER 角色变 ADMIN，OWNER_CANNOT_LEAVE 守卫自然放行 |
| StudyGroup.ownerId 同步 | 转让时更新 | 群主身份变更需反映在 StudyGroup 聚合根，新增 `updateOwner(Long)` 行为方法 |
| 踢人 | 划入 REQ-63 | 踢人是管理端成员列表页配套操作，与成员列表一起交付 |

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| PUT | `/api/study-groups/{id}/members/{userId}` | 更新成员角色（ADMIN / MEMBER） |
| POST | `/api/study-groups/{id}/transfer-ownership` | 转让群主 |

### 鉴权

需 JWT 鉴权（`/api/**` 前缀自动拦截），无需 ADMIN 角色，未登录返回 401。userId 从 JWT 中提取（`SecurityUtils.getCurrentUserId()`），不依赖请求体传入。

### API 1 — 更新成员角色

**请求：** `PUT /api/study-groups/{id}/members/{userId}`

```json
{
  "role": "ADMIN"
}
```

**响应：** `Result<Void>`（空 data）

**字段规则：**

- `role`：必填，`@NotBlank`，仅允许 `"ADMIN"` 或 `"MEMBER"`

**业务流程：**

```
1. callerRole = getCurrentMemberRole(groupId) → 非 OWNER 抛 NOT_GROUP_OWNER
2. target = groupMemberRepository.findById(targetUserId).orElseThrow(NOT_GROUP_MEMBER)
3. if target.groupId != groupId → NOT_GROUP_MEMBER
4. if target.role == OWNER → 抛 CANNOT_CHANGE_OWNER_ROLE
5. switch role:
   ADMIN  → target.promoteToAdmin()
   MEMBER → target.demoteToMember()
6. groupMemberRepository.save(target)
```

### API 2 — 转让群主

**请求：** `POST /api/study-groups/{id}/transfer-ownership`

```json
{
  "toUserId": 123
}
```

**响应：** `Result<Void>`（空 data）

**字段规则：**

- `toUserId`：必填，`@NotNull`

**业务流程：**

```
@Transactional
1. callerRole = getCurrentMemberRole(groupId) → 非 OWNER 抛 NOT_GROUP_OWNER
2. owner = groupMemberRepository.findByGroupIdAndUserId(groupId, callerUserId).orElseThrow(...)
3. target = groupMemberRepository.findById(toUserId).orElseThrow(NOT_GROUP_MEMBER)
4. if target.groupId != groupId → NOT_GROUP_MEMBER
5. group = studyGroupRepository.findById(groupId).orElseThrow(GROUP_NOT_FOUND)
6. group.updateOwner(toUserId)
7. studyGroupRepository.save(group)
8. owner.transferOwnershipTo(target)
9. groupMemberRepository.save(owner)
10. groupMemberRepository.save(target)
```

### 错误处理

| 场景 | HTTP 状态码 | ResultCode | 消息 |
|------|-----------|------------|------|
| role 为空 | 400 | FAIL（Bean Validation） | 由 GlobalExceptionHandler 处理 |
| role 非法值（非 ADMIN/MEMBER） | 400 | FAIL（Bean Validation） | 由 GlobalExceptionHandler 处理 |
| toUserId 为空 | 400 | FAIL（Bean Validation） | 同上 |
| 非 OWNER 操作 | 200 | NOT_GROUP_OWNER（已有） | 仅群主可操作 |
| targetUserId 不存在 | 200 | NOT_GROUP_MEMBER（已有） | 非群组成员 |
| target 不在该群组 | 200 | NOT_GROUP_MEMBER（已有） | 非群组成员 |
| 尝试改 OWNER 角色 | 200 | CANNOT_CHANGE_OWNER_ROLE（新增，code=400） | 不能通过此接口修改群主角色，请使用转让功能 |

> 注：业务异常和校验异常统一返回 HTTP 200，错误码在响应 body 的 `code` 字段。上表"HTTP 状态码"列标注的是异常的语义级别（code 值），非实际 HTTP 响应码。

**新增 ResultCode：**

```java
CANNOT_CHANGE_OWNER_ROLE(400, "不能通过此接口修改群主角色，请使用转让功能"),
```

## 数据模型

无新增表或字段。`group_member.role` 列已有 `ADMIN` 值（GroupRole 枚举自 REQ-48 已定义，ENUM 值 'OWNER','ADMIN','MEMBER'）。

## 领域模型

### GroupMember 聚合根（修改）

**新增行为方法：**

```java
/**
 * 提升为管理员。幂等。OWNER 调用抛 IllegalStateException。
 */
public void promoteToAdmin() {
    if (this.role == GroupRole.OWNER) {
        throw new IllegalStateException("群主不能降级为管理员，请使用转让功能");
    }
    this.role = GroupRole.ADMIN;
}

/**
 * 降级为成员。幂等。OWNER 调用抛 IllegalStateException。
 */
public void demoteToMember() {
    if (this.role == GroupRole.OWNER) {
        throw new IllegalStateException("群主不能降级为成员，请使用转让功能");
    }
    this.role = GroupRole.MEMBER;
}

/**
 * 转让群主。this 为原 OWNER，转让后变为 ADMIN。
 * @throws IllegalStateException 非 OWNER 调用时抛出
 * @throws IllegalArgumentException target 不在同群组时抛出
 */
public void transferOwnershipTo(GroupMember target) {
    if (this.role != GroupRole.OWNER) {
        throw new IllegalStateException("仅群主可以转让");
    }
    if (!this.groupId.equals(target.groupId)) {
        throw new IllegalStateException("只能转让给同群组成员");
    }
    this.role = GroupRole.ADMIN;
    target.role = GroupRole.OWNER;
}
```

### StudyGroup 聚合根（修改）

**新增行为方法：**

```java
/**
 * 更新群主 ID（转让时调用）。
 */
public void updateOwner(Long newOwnerId) {
    this.ownerId = newOwnerId;
    this.updatedAt = LocalDateTime.now();
}
```

### StudyGroupRepository（修改）

无新增方法。现有 `save()` 已支持更新路径。

### 出端口（GroupMemberRepository，修改）

**新增方法：**

```java
Optional<GroupMember> findById(Long id);
```

> 现有 `save()` 已支持 `id != null` 的更新路径（见 GroupMemberRepositoryAdapter 的 if-else 分支），无需新增 `updateRole`。

## 持久化层（infrastructure）

### GroupMemberJpaRepository

无需修改。继承 `JpaRepository<GroupMemberPO, Long>` 已提供 `findById`。

### GroupMemberRepositoryAdapter（修改）

**新增实现：**

```java
@Override
public Optional<GroupMember> findById(Long id) {
    return jpaRepository.findById(id)
            .map(GroupMemberConverter.INSTANCE::toDomain);
}
```

### GroupMemberConverter

无需修改。`updatePO` 已支持 role 字段的 MapStruct 映射。

## 应用层 + API 层（app 模块）

### GroupMemberAppService（修改）

**新增方法：**

```java
/**
 * 更新成员角色（仅 OWNER，ADMIN / MEMBER 互转）
 */
@Transactional
public void updateRole(Long groupId, Long targetUserId, String role) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    enforceOwner(groupId, currentUserId);

    GroupMember target = groupMemberRepository.findById(targetUserId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

    if (!target.getGroupId().equals(groupId)) {
        throw new BusinessException(ResultCode.NOT_GROUP_MEMBER);
    }

    if (target.getRole() == GroupRole.OWNER) {
        throw new BusinessException(ResultCode.CANNOT_CHANGE_OWNER_ROLE);
    }

    if ("ADMIN".equals(role)) {
        target.promoteToAdmin();
    } else if ("MEMBER".equals(role)) {
        target.demoteToMember();
    } else {
        throw new BusinessException(ResultCode.PARAM_ERROR);  // @Pattern 已拦截，防御性兜底
    }
    groupMemberRepository.save(target);
}

/**
 * 转让群主（仅 OWNER，转让后原 OWNER 变为 ADMIN）
 */
@Transactional
public void transferOwnership(Long groupId, Long toUserId) {
    Long currentUserId = SecurityUtils.getCurrentUserId();
    GroupMember owner = enforceOwner(groupId, currentUserId);

    GroupMember target = groupMemberRepository.findById(toUserId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

    if (!target.getGroupId().equals(groupId)) {
        throw new BusinessException(ResultCode.NOT_GROUP_MEMBER);
    }

    StudyGroup group = studyGroupRepository.findById(groupId)
            .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));
    group.updateOwner(toUserId);
    studyGroupRepository.save(group);

    owner.transferOwnershipTo(target);
    groupMemberRepository.save(owner);
    groupMemberRepository.save(target);
}

private GroupMember enforceOwner(Long groupId, Long currentUserId) {
    GroupMember current = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
    if (current.getRole() != GroupRole.OWNER) {
        throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
    }
    return current;
}
```

> `enforceOwner` 返回 `GroupMember`，`transferOwnership` 直接复用返回值，避免二次 DB 查询。`GroupMemberAppService` 构造器需新增 `StudyGroupRepository` 参数（转让时更新 `StudyGroup.ownerId`）。现有方法（joinDirectly 等）的 OWNER 校验重构不在 REQ-50 范围。

### GroupMemberController（修改）

**新增 2 个端点：**

```java
@PutMapping("/{id}/members/{userId}")
public Result<Void> updateRole(@PathVariable("id") Long groupId,
                                @PathVariable("userId") Long targetUserId,
                                @Valid @RequestBody UpdateMemberRoleRequest request) {
    appService.updateRole(groupId, targetUserId, request.getRole());
    return Result.success(null);
}

@PostMapping("/{id}/transfer-ownership")
public Result<Void> transferOwnership(@PathVariable("id") Long groupId,
                                       @Valid @RequestBody TransferOwnershipRequest request) {
    appService.transferOwnership(groupId, request.getToUserId());
    return Result.success(null);
}
```

### 新增 DTO

**UpdateMemberRoleRequest：**

```java
public class UpdateMemberRoleRequest {
    @NotBlank
    @Pattern(regexp = "ADMIN|MEMBER", message = "角色仅允许 ADMIN 或 MEMBER")
    private String role;
    // getter / setter
}
```

**TransferOwnershipRequest：**

```java
public class TransferOwnershipRequest {
    @NotNull
    private Long toUserId;
    // getter / setter
}
```

## Impact Analysis

### 修改 / 新增文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `core/.../entity/GroupMember.java` | 修改 | 加 3 个行为方法 |
| `core/.../entity/StudyGroup.java` | 修改 | 加 `updateOwner(Long)` 行为方法 |
| `core/.../outbound/GroupMemberRepository.java` | 修改 | 加 `findById` |
| `core/.../repoadapter/GroupMemberRepositoryAdapter.java` | 修改 | 实现 `findById` |
| `core/.../result/ResultCode.java` | 修改 | 加 `CANNOT_CHANGE_OWNER_ROLE` |
| `app/.../controller/GroupMemberController.java` | 修改 | 加 2 个端点 |
| `app/.../application/service/GroupMemberAppService.java` | 修改 | 加 2 个公开方法 + 1 个私有 `enforceOwner`（注入 `StudyGroupRepository`） |
| `app/.../api/dto/UpdateMemberRoleRequest.java` | 新增 | 请求 DTO |
| `app/.../api/dto/TransferOwnershipRequest.java` | 新增 | 请求 DTO |

纯扩展现有文件，无新增聚合根/PO/JpaRepository/Converter。

### 不受影响的现有功能

- REQ-48 创建群组流程不受影响
- REQ-49 加入/退出/邀请码流程不受影响（`leave()` 无需修改，转让后原 OWNER 角色已变 ADMIN）
- admin 模块不受影响（REQ-50 仅用户端 API）

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| **领域层单测** | GroupMemberTest：`promoteToAdmin`（MEMBER→ADMIN / ADMIN 幂等 / OWNER 抛异常）、`demoteToMember`（ADMIN→MEMBER / MEMBER 幂等 / OWNER 抛异常）、`transferOwnershipTo`（正常转让+角色验证 / 非 OWNER 抛异常 / 不同群组抛异常） |
| **Controller @WebMvcTest** | updateRole 成功（200 空 data）、transferOwnership 成功（200 空 data）、参数校验失败（400）。`addFilters=false` |
| **AppService 单测（Mockito）** | updateRole：正常提升 / 正常降级 / 幂等 / 非 OWNER 抛 NOT_GROUP_OWNER / 目标不存在抛 NOT_GROUP_MEMBER / 目标不在该群组抛 NOT_GROUP_MEMBER / 目标为 OWNER 抛 CANNOT_CHANGE_OWNER_ROLE。transferOwnership：正常转让（验证 StudyGroup.updateOwner 调用 + GroupMember save 调用 2 次 + 角色变更） / 非 OWNER 抛异常 / 目标不存在抛异常 / 目标不在同群组抛异常 |
| **Repository 集成测试（@DataJpaTest 走真实 MySQL）** | GroupMemberRepositoryAdapterTest：`findById` 找到/找不到 |

**集成测试配置（沿用 REQ-48/49 模式）：**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(GroupMemberRepositoryAdapter.class)
class GroupMemberRepositoryAdapterTest { ... }
```

### 手动验证

1. OWNER 提升 MEMBER 为 ADMIN → `group_member` 表 role 变为 ADMIN
2. OWNER 降级 ADMIN 为 MEMBER → role 变为 MEMBER
3. 非 OWNER 调用 PUT → 返回 NOT_GROUP_OWNER
4. OWNER 转让给 MEMBER → 原 OWNER 变 ADMIN，新 OWNER 变 OWNER，`study_group.owner_id` 更新为新群主 ID，三次 save 成功
5. 转让后原 OWNER 可调用 DELETE 退群
6. 新 OWNER 无法调用 DELETE 退群（OWNER_CANNOT_LEAVE 生效）
7. PUT 传 `"role":"OWNER"` → 返回 CANNOT_CHANGE_OWNER_ROLE
8. PUT 传非法 role 值 → 参数校验 400

### 回滚标准

- 删除 `app/api/dto/` 下 2 个新 DTO 文件
- 撤销 7 个现有文件的修改（GroupMember / StudyGroup / GroupMemberRepository / GroupMemberRepositoryAdapter / ResultCode / GroupMemberController / GroupMemberAppService）
- 无数据库表变更

## 未来扩展（不在 REQ-50 范围）

| 需求 | 涉及改动 |
|------|---------|
| REQ-63 踢人 | `DELETE /api/study-groups/{id}/members/{userId}`（仅 OWNER/ADMIN，不能踢 OWNER） |
| ADMIN 提升/降级权限 | 当前仅 OWNER 可操作，未来可能允许 ADMIN 管理 MEMBER |
