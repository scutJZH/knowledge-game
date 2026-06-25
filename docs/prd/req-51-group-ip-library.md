# REQ-51 群组 — 关联 IP 库 API

## 产品定位

群组管理员将系统中已启用的 IP 系列关联到群组，使群组成员可以在该 IP 下进行后续抽卡/收集等玩法。知识库全局共享，不需要群组授权。

## 前置依赖

| 编号 | 需求名称 | 状态 | 依赖内容 |
|------|---------|------|---------|
| REQ-48 | 群组 — 创建群组 API | done | `study_group` 表 + `group_member` 表 + StudyGroup/GroupMember 聚合根 + OWNER/ADMIN/MEMBER 角色体系 |
| REQ-49 | 群组 — 成员管理 API | done | `GroupMemberRepository.findByGroupIdAndUserId` / `existsByGroupIdAndUserId` |
| REQ-16 | IP 系列 — 管理端 CRUD API | done | `ip_series` 表 + IpSeries 聚合根 + `IpSeriesRepositoryPort.findAllByIdIn` |

> 三个前置均已完成（done），本需求可立即开发。

## 用户故事

**作为** 群组管理员（OWNER 或 ADMIN）
**我想要** 将系统中的 IP 系列添加到群组
**以便于** 群组成员在该 IP 下进行抽卡、卡牌收集等玩法

**作为** 群组成员
**我想要** 查看群组已关联的 IP 系列列表
**以便于** 了解当前群组支持哪些 IP 的玩法

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 管理权限 | OWNER + ADMIN 均可管理 | 与成员管理/踢人等管理操作对齐（REQ-50），ADMIN 是群主授权的管理者 |
| 可关联 IP 范围 | 所有 ACTIVE 的 IpSeries | 简单直接，群组管理员自由选择。INACTIVE 的 IP 被系统级停用后不可新关联 |
| API 粒度 | PUT 全量替换（批量） | 前端传完整 IP ID 列表，后端计算 diff（增量添加+删除），单次请求完成。比多次单条调用更高效 |
| 移除约束 | 有活跃卡牌数据时禁止移除 | 保护数据一致性。校验点在卡牌系统（REQ-18~）实现时补齐，本需求预留校验扩展点 |
| 查询权限 | 任意群组成员 | 查看已关联 IP 不涉及管理操作，成员可见 |
| 聚合根边界 | GroupIpLibrary 独立聚合根 | 与 GroupMember 一致（独立于 StudyGroup），IP 关联会频繁增删，独立聚合根避免加载整个 StudyGroup |
| 数据库外键 | 不设物理外键 | 项目规范，关联通过应用层保证 |
| 出端口命名 | `GroupIpLibraryRepository`（无 Port 后缀） | 与 StudyGroupRepository / GroupMemberRepository 保持一致 |

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/study-groups/{id}/ip-library` | 查询群组已关联的 IP 列表（任意成员） |
| PUT | `/api/study-groups/{id}/ip-library` | 批量设置 IP 库关联（OWNER/ADMIN） |

### 鉴权

需 JWT 鉴权（`/api/**` 前缀自动拦截），无需 ADMIN 角色，未登录返回 401。

### GET — 查询群组已关联 IP

**响应体：**

```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "groupId": 1,
      "ipSeriesId": 1,
      "ipSeriesName": "宝可梦",
      "ipSeriesCode": "POKEMON",
      "coverImageFileId": 10,
      "coverImageUrl": "https://...",
      "addedAt": 1718800000000
    }
  ]
}
```

**错误：**

| 场景 | ResultCode |
|------|------------|
| 群组不存在 | GROUP_NOT_FOUND |
| 非群组成员 | NOT_GROUP_MEMBER |

### PUT — 批量设置 IP 库关联

**请求体（`UpdateGroupIpLibraryRequest`）：**

```json
{
  "ipSeriesIds": [1, 2, 3]
}
```

`ipSeriesIds`：必填，`@NotNull @Size(max=100)`，List\<Long\>。传空数组表示清空全部关联。上限 100 基于系统 IP 系列总量预期（数十个），留有充足余量防恶意超大请求。

**响应体：** 同 GET，返回操作后的完整关联列表。

**业务流程：**

```
@Transactional
1. groupId 校验：群组存在，否则抛 GROUP_NOT_FOUND
2. currentUserId = SecurityUtils.getCurrentUserId()
3. 查询 currentUserId 在群组中的角色：
   - 非成员 → 抛 NOT_GROUP_MEMBER
   - MEMBER → 抛 NOT_GROUP_ADMIN
4. 去重 request.ipSeriesIds
5. 校验所有 ipSeriesId 存在且 status=ACTIVE：
   - 不存在 → 抛 IP_SERIES_NOT_FOUND
   - INACTIVE → 抛 IP_SERIES_NOT_ACTIVE
6. 查询当前已关联的 IP ID 列表 (currentIds)
7. 计算 diff：
   - toAddIds = requestIds - currentIds
   - toRemoveIds = currentIds - requestIds
8. 移除 toRemoveIds（批量 delete）
9. 新增 toAddIds（批量 save，每条 GroupIpLibrary.create(groupId, ipSeriesId)）
10. 查询最终关联列表，组装响应（含 IpSeries name/code/coverImage）
```

**错误：**

| 场景 | HTTP | ResultCode | 消息 |
|------|------|------------|------|
| ipSeriesIds 为 null | 400 | FAIL | 由 Bean Validation 触发 |
| ipSeriesIds 超过 100 个 | 400 | FAIL | 由 Bean Validation 触发 |
| 群组不存在 | 200 | GROUP_NOT_FOUND | 群组不存在 |
| 非群组成员 | 200 | NOT_GROUP_MEMBER | 非群组成员 |
| 非 OWNER/ADMIN | 200 | **NOT_GROUP_ADMIN**（新增） | 仅群主或管理员可操作 |
| ipSeriesId 不存在 | 200 | **IP_SERIES_NOT_FOUND**（新增） | IP系列不存在 |
| ipSeriesId 对应 IP 为 INACTIVE | 200 | **IP_SERIES_NOT_ACTIVE**（新增） | IP系列未启用 |

> **注意：** 重复添加（toAddIds 含已关联的 IP）幂等跳过，不报错。移除已不存在的关联同样幂等。空数组 `[]` 合法，表示清空全部关联。

### 新增 ResultCode

在 `core/common/result/ResultCode.java` 新增三条枚举：

```java
NOT_GROUP_ADMIN(403, "仅群主或管理员可操作"),
IP_SERIES_NOT_FOUND(400, "IP系列不存在"),
IP_SERIES_NOT_ACTIVE(400, "IP系列未启用"),
```

## 数据模型

### group_ip_library 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT AUTO_INCREMENT | PK | 主键 |
| group_id | BIGINT | NOT NULL | 群组 ID（无 FK 约束） |
| ip_series_id | BIGINT | NOT NULL | IP 系列 ID（无 FK 约束） |
| added_at | DATETIME | NOT NULL | 添加时间 |

唯一约束：`UNIQUE(group_id, ip_series_id)`

**说明：** 仅 `added_at` 一个时间字段，无 `created_at`/`updated_at`。关联记录只有创建和删除两种操作，无可变字段（IP 系列 ID 和群组 ID 均不可变），故不需要 `updated_at`。

## 领域模型

### GroupIpLibrary 聚合根（`core/domain/model/entity/GroupIpLibrary.java`）

**字段：** `id` / `groupId` / `ipSeriesId` / `addedAt`

**行为方法：**

- `create(groupId, ipSeriesId)` — 工厂方法（新建关联，addedAt=now）
- `reconstruct(...)` — 从 PO 重建（Repository 加载用）

> 无 update 方法 — 关联记录只有创建和删除两种操作，无可变字段。无 status 字段（硬删除）。

### 出端口（`core/domain/port/outbound/`）

**GroupIpLibraryRepository：**

- `save(GroupIpLibrary): GroupIpLibrary`
- `saveAll(List<GroupIpLibrary>): List<GroupIpLibrary>` — 批量新增
- `findByGroupId(Long): List<GroupIpLibrary>` — 查询群组全部关联
- `existsByGroupIdAndIpSeriesId(Long, Long): boolean` — 存在性检查
- `deleteByGroupIdAndIpSeriesIdIn(Long, List<Long>): void` — 批量删除
- `deleteAllByGroupId(Long): void` — 清空群组全部关联（当前无调用场景，预留供群组硬删除时级联清理）

## 持久化层（infrastructure）

| 组件 | 文件位置 |
|------|---------|
| `GroupIpLibraryPO` | `core/infrastructure/db/entity/GroupIpLibraryPO.java` |
| `GroupIpLibraryJpaRepository` | `core/infrastructure/db/repository/GroupIpLibraryJpaRepository.java` |
| `GroupIpLibraryRepositoryAdapter` | `core/infrastructure/adapter/repoadapter/GroupIpLibraryRepositoryAdapter.java` |
| `GroupIpLibraryConverter` | `core/infrastructure/db/converter/GroupIpLibraryConverter.java`（MapStruct） |

**注意：**
- PO 不含 `@ForeignKey` 物理外键（项目规范）
- JpaRepository 的 `deleteByGroupIdAndIpSeriesIdIn` / `deleteAllByGroupId` 为派生删除方法，需加 `@Modifying` 注解（参照 `GroupMemberJpaRepository.deleteByGroupIdAndUserId`）
- Adapter 由 `@ComponentScan("com.knowledgegame.core.infrastructure")` 自动注册，无需在 AutoConfiguration 手动声明 Bean

## 应用层 + API 层（app 模块）

### 包结构

```
com.knowledgegame.app/
├── api/
│   ├── controller/StudyGroupController.java    ← 修改：新增 GET/PUT 端点
│   └── dto/
│       ├── UpdateGroupIpLibraryRequest.java    ← 新增
│       └── GroupIpLibraryResponse.java         ← 新增
├── application/
│   ├── service/StudyGroupAppService.java       ← 修改：新增 ip library 方法
│   └── assembler/GroupIpLibraryAssembler.java  ← 新增
```

### StudyGroupController 新增方法

```java
@GetMapping("/{id}/ip-library")
public Result<List<GroupIpLibraryResponse>> listIpLibrary(@PathVariable("id") Long groupId) {
    return Result.success(appService.listIpLibrary(groupId));
}

@PutMapping("/{id}/ip-library")
public Result<List<GroupIpLibraryResponse>> updateIpLibrary(
        @PathVariable("id") Long groupId,
        @Valid @RequestBody UpdateGroupIpLibraryRequest request) {
    return Result.success(appService.updateIpLibrary(groupId, request));
}
```

### StudyGroupAppService 新增方法

```java
@Transactional(readOnly = true)
public List<GroupIpLibraryResponse> listIpLibrary(Long groupId) {
    Long userId = SecurityUtils.getCurrentUserId();
    if (!studyGroupRepository.existsById(groupId)) {
        throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
    }
    if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
        throw new BusinessException(ResultCode.NOT_GROUP_MEMBER);
    }
    List<GroupIpLibrary> items = groupIpLibraryRepository.findByGroupId(groupId);
    if (items.isEmpty()) return List.of();
    List<Long> ipIds = items.stream().map(GroupIpLibrary::getIpSeriesId).toList();
    Map<Long, IpSeries> ipMap = ipSeriesRepositoryPort.findAllByIdIn(ipIds).stream()
            .collect(Collectors.toMap(IpSeries::getId, Function.identity()));
    return items.stream()
            .map(item -> GroupIpLibraryAssembler.INSTANCE.toResponse(item, ipMap.get(item.getIpSeriesId())))
            .toList();
}

@Transactional
public List<GroupIpLibraryResponse> updateIpLibrary(Long groupId, UpdateGroupIpLibraryRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();

    if (!studyGroupRepository.existsById(groupId)) {
        throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
    }
    GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
            .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
    if (member.getRole() != GroupRole.OWNER && member.getRole() != GroupRole.ADMIN) {
        throw new BusinessException(ResultCode.NOT_GROUP_ADMIN);
    }

    List<Long> requestIds = request.getIpSeriesIds().stream().distinct().toList();

    // 校验 IP 存在且 ACTIVE
    if (!requestIds.isEmpty()) {
        List<IpSeries> ipList = ipSeriesRepositoryPort.findAllByIdIn(requestIds);
        if (ipList.size() != requestIds.size()) {
            throw new BusinessException(ResultCode.IP_SERIES_NOT_FOUND);
        }
        boolean hasInactive = ipList.stream().anyMatch(ip -> ip.getStatus() != IpSeriesStatus.ACTIVE);
        if (hasInactive) {
            throw new BusinessException(ResultCode.IP_SERIES_NOT_ACTIVE);
        }
    }

    List<GroupIpLibrary> existing = groupIpLibraryRepository.findByGroupId(groupId);
    Set<Long> currentIds = existing.stream().map(GroupIpLibrary::getIpSeriesId).collect(Collectors.toSet());
    Set<Long> requestIdSet = new HashSet<>(requestIds);

    // toRemove: currentIds - requestIds
    List<Long> toRemove = currentIds.stream().filter(id -> !requestIdSet.contains(id)).toList();
    // toAdd: requestIds - currentIds
    List<Long> toAdd = requestIds.stream().filter(id -> !currentIds.contains(id)).toList();

    // TODO: 未来在此处插入 Q4 移除校验 — 检查 toRemove 中是否有活跃卡牌数据

    if (!toRemove.isEmpty()) {
        groupIpLibraryRepository.deleteByGroupIdAndIpSeriesIdIn(groupId, toRemove);
    }
    if (!toAdd.isEmpty()) {
        List<GroupIpLibrary> newItems = toAdd.stream()
                .map(ipId -> GroupIpLibrary.create(groupId, ipId))
                .toList();
        groupIpLibraryRepository.saveAll(newItems);
    }

    // 返回操作后的完整列表
    List<GroupIpLibrary> updated = groupIpLibraryRepository.findByGroupId(groupId);
    if (updated.isEmpty()) return List.of();
    List<Long> ipIds = updated.stream().map(GroupIpLibrary::getIpSeriesId).toList();
    Map<Long, IpSeries> ipMap = ipSeriesRepositoryPort.findAllByIdIn(ipIds).stream()
            .collect(Collectors.toMap(IpSeries::getId, Function.identity()));
    return updated.stream()
            .map(item -> GroupIpLibraryAssembler.INSTANCE.toResponse(item, ipMap.get(item.getIpSeriesId())))
            .toList();
}
```

> `listIpLibrary` 先校验群组存在（`GROUP_NOT_FOUND`）再校验成员身份（`NOT_GROUP_MEMBER`）——若群组已被硬删除，应优先报群组不存在而非成员身份错误。

### GroupIpLibraryAssembler（MapStruct）

- `toResponse(GroupIpLibrary, IpSeries): GroupIpLibraryResponse`
- 从 GroupIpLibrary 取 `id/groupId/ipSeriesId/addedAt`
- 从 IpSeries 取 `name/code/coverImage` → 解包为 `ipSeriesName/ipSeriesCode/coverImageFileId/coverImageUrl`
- `addedAt` 转为毫秒时间戳（Long）

### UpdateGroupIpLibraryRequest

```java
public class UpdateGroupIpLibraryRequest {
    @NotNull
    @Size(max = 100)
    private List<Long> ipSeriesIds;
    // getter/setter
}
```

### GroupIpLibraryResponse

```java
public class GroupIpLibraryResponse {
    private Long id;
    private Long groupId;
    private Long ipSeriesId;
    private String ipSeriesName;
    private String ipSeriesCode;
    private Long coverImageFileId;
    private String coverImageUrl;
    private Long addedAt;
    // getter/setter
}
```

## Impact Analysis

### 修改 / 新增文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `core/.../domain/model/entity/GroupIpLibrary.java` | 新增 | 聚合根 |
| `core/.../domain/port/outbound/GroupIpLibraryRepository.java` | 新增 | 出端口接口 |
| `core/.../infrastructure/db/entity/GroupIpLibraryPO.java` | 新增 | PO |
| `core/.../infrastructure/db/repository/GroupIpLibraryJpaRepository.java` | 新增 | Spring Data JPA |
| `core/.../infrastructure/adapter/repoadapter/GroupIpLibraryRepositoryAdapter.java` | 新增 | Repository 实现 |
| `core/.../infrastructure/db/converter/GroupIpLibraryConverter.java` | 新增 | MapStruct |
| `core/.../common/result/ResultCode.java` | 修改 | 新增 3 条枚举 |
| `app/.../api/controller/StudyGroupController.java` | 修改 | 新增 GET/PUT 端点 |
| `app/.../api/dto/UpdateGroupIpLibraryRequest.java` | 新增 | 请求 DTO |
| `app/.../api/dto/GroupIpLibraryResponse.java` | 新增 | 响应 DTO |
| `app/.../application/service/StudyGroupAppService.java` | 修改 | 新增 listIpLibrary / updateIpLibrary 方法 |
| `app/.../application/assembler/GroupIpLibraryAssembler.java` | 新增 | MapStruct |
| `docs/card-system-data-model.md` | 修改 | 同步 group_ip_library 表设计变更（如有差异） |

### 依赖关系

- app 模块已依赖 core 模块，直接使用新增的领域类型和出端口
- app 模块已注入 `StudyGroupRepository` / `GroupMemberRepository` / `IpSeriesRepositoryPort`，AppService 新增 `GroupIpLibraryRepository` 依赖
- core 模块新增 1 个独立聚合根 + 出端口 + 仓储适配器
- admin 模块不受影响（REQ-51 仅用户端）
- 表结构无变更，仅新增 `group_ip_library` 表
- `card-system-data-model.md` 中 `group_ip_library` 表已定义，需同步：(1) 去掉 `FK → study_group.id` / `FK → ip_series.id` 标注（项目无物理外键）(2) `added_at` 补充 `NOT NULL` 约束

### 不受影响的现有功能

- 用户端现有 API（StudyGroup CRUD / GroupMember 管理 / KnowledgeCategory / User）不受影响
- 管理端全部功能不受影响
- 现有聚合根（StudyGroup / GroupMember / IpSeries）不受影响

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| **领域层单测** | GroupIpLibraryTest：`create` 字段赋值、addedAt 非空 |
| **Controller @WebMvcTest** | GET 成功（200 + 含 IP 名称/封面图）、PUT 成功（200 + 返回更新后列表）、ipSeriesIds 为 null（400）、群组不存在（GROUP_NOT_FOUND）、非成员（NOT_GROUP_MEMBER）、非 ADMIN（NOT_GROUP_ADMIN）、IP 不存在（IP_SERIES_NOT_FOUND）、IP INACTIVE（IP_SERIES_NOT_ACTIVE）。`addFilters=false` 禁用 Security Filter |
| **AppService 单测（Mockito）** | GET 正常路径（mock 返回关联列表 + IP 信息）、空列表、PUT 正常路径（计算 diff、验证 saveAll/deleteByXxx 参数）、PUT 空数组清空全部、幂等（requestIds 与 currentIds 完全相同→无操作）、IP 校验失败抛异常、非 OWNER/ADMIN 抛异常 |
| **Repository 集成测试（@DataJpaTest 走真实 MySQL）** | GroupIpLibraryRepositoryAdapterTest：save 成功、saveAll 批量成功、findByGroupId、deleteByGroupIdAndIpSeriesIdIn 批量删除、`UNIQUE(group_id, ip_series_id)` 重复插入抛 `DataIntegrityViolationException`、deleteAllByGroupId |
| **Converter / Assembler 单测** | GroupIpLibraryConverter 双向转换、GroupIpLibraryAssembler（addedAt → 毫秒时间戳 + IpSeries 字段解包） |

**集成测试配置：**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(GroupIpLibraryRepositoryAdapter.class)
class GroupIpLibraryRepositoryAdapterTest { ... }
```

### 手动验证

1. 用户 A 创建群组，调用 `GET /api/study-groups/{id}/ip-library`，验证返回空数组
2. 用户 A（OWNER）调用 `PUT /api/study-groups/{id}/ip-library`，传 `{"ipSeriesIds":[1,2]}`，验证返回 2 条关联记录（含 IP 名称/封面图）
3. 再次 GET，验证返回 2 条记录
4. 用户 A 再次 PUT 传 `{"ipSeriesIds":[2,3]}`，验证 IP 1 被移除、IP 3 被新增、IP 2 保留
5. PUT 传 `{"ipSeriesIds":[]}`，验证全部关联被清空
6. 用户 B（非成员）访问 GET/PUT，验证返回 NOT_GROUP_MEMBER
7. 用户 C（MEMBER）访问 PUT，验证返回 NOT_GROUP_ADMIN
8. 用户 C（MEMBER）访问 GET，验证正常返回
9. PUT 传不存在的 IP ID，验证返回 IP_SERIES_NOT_FOUND
10. PUT 传 INACTIVE IP，验证返回 IP_SERIES_NOT_ACTIVE
11. 使用群组中已有 ADMIN 角色的成员调用 PUT，验证成功（ADMIN 无需提升即可操作）
12. 用户 A（OWNER）重复 PUT 相同列表，验证幂等（无变化，不报错）

### 回滚标准

- 仅新增文件 + 3 处修改，回滚只需：
  - 删除 core 模块 6 个新文件（实体 / 端口 / PO / JpaRepo / Adapter / Converter）
  - 删除 app 模块 3 个新文件（DTO×2 / Assembler）
  - 撤销 `StudyGroupController.java` 新增方法和 `StudyGroupAppService.java` 新增方法/依赖
  - 撤销 `ResultCode.java` 新增的 3 条枚举
- 数据库表如已创建需手动 DROP（`group_ip_library`）

## 未来扩展（不在 REQ-51 范围）

| 需求/场景 | 涉及改动 |
|----------|---------|
| Q4 移除校验（有活跃卡牌数据时禁止移除） | 新增领域服务 `GroupIpLibraryDomainService`，注入卡牌相关端口（user_card / card_bag_item / pity_counter），在 `updateIpLibrary` 的 toRemove 计算后调用。校验失败抛特定 BusinessException |
| 前端 IP 库关联页（REQ-62） | 前端 `frontend/user/` 新增群组管理端 IP 库关联页，使用 Transfer/Select 组件展示可选 IP 列表和已关联 IP 列表，调用 GET/PUT |
| 群组删除时级联清理 | 当 `DELETE /api/study-groups/{id}` 实现时，需级联删除 `group_ip_library` 中该群组的全部记录 |
