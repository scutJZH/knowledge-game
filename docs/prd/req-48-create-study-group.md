# REQ-48 群组 — 创建群组 API

## 产品定位

用户端创建学习群组 API。群组是知识游戏的维度基础，积分 / 抽卡 / 卡牌收集 / 保底 / 签到全部基于群组维度（详见 [card-system-data-model.md](../card-system-data-model.md) 核心设计决策）。本需求只覆盖「创建群组 + 创建人自动成为 OWNER」的最小可用闭环，成员管理、加入方式、IP 库关联等延后到 REQ-49 / REQ-51。

## 用户故事

**作为** 普通用户
**我想要** 创建一个学习群组
**以便于** 后续邀请其他用户加入、关联 IP 库、开展积分 / 抽卡 / 收集玩法

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 创建人入群 | 原子写入 OWNER 成员记录 | 创建后群组立即可用，不依赖 REQ-49 才有成员 |
| 聚合根边界 | StudyGroup 和 GroupMember 双独立聚合根 | 群组成员会频繁增减（REQ-49），独立聚合根避免加载整个 StudyGroup 聚合 |
| name 唯一性 | 无唯一约束 | 用户私域资源，允许同名（与系统级 IpSeries/CardTemplate 全局唯一策略不同） |
| join_policy 字段 | 不预留，REQ-49 再加 | REQ-48 范围最小化，为 REQ-49 保留加入方式设计决策空间 |
| 创建数量上限 | 不限制 | 用户端不防滥用，依赖前端交互约束和未来管理端审计 |
| 删除策略 | 用户端硬删除（不软删除，不进回收站） | 用户端资源简化，前端二次提醒即可。REQ-100 回收站覆盖管理端，不影响用户端 |
| status 字段 | 不引入 | 用户端无软删除语义，无状态切换 |
| 头像字段 | FileRef 双字段持久化 | 对齐 REQ-93 图片字段规范 |
| 数据库外键 | 不设物理外键 | 项目规范，关联通过应用层保证 |
| 出端口命名 | `StudyGroupRepository` / `GroupMemberRepository`（无 Port 后缀） | 与最近两个聚合根（QuestionRepository / KnowledgeItemRepository，REQ-09 / REQ-97）保持一致；早期聚合根用 `XxxRepositoryPort` 后缀（User/IpSeries/CardTemplate/KnowledgeCategory）。两套并存为已知命名漂移，可后续单独 REQ 统一 |

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/study-groups` | 创建群组（用户端，需 JWT） |

### 鉴权

需 JWT 鉴权（`/api/**` 前缀自动拦截），无需 ADMIN 角色，未登录返回 401。`ownerId` 从 JWT 中提取（`SecurityUtils.getCurrentUserId()`），不依赖请求体传入。

### 请求体（`CreateStudyGroupRequest`）

```json
{
  "name": "我的学习群",
  "description": "一起学Java",
  "avatarFileId": 123
}
```

**字段规则：**

- `name`：必填，`@NotBlank`，`@Size(max=50)`，无唯一约束
- `description`：可选，`@Size(max=500)`
- `avatarFileId`：可选，Long 类型。提交后由 AppService 调 `FileServiceClient.getFileInfo(fileId)` 校验 metadata（bizType + userId），并组装 `FileRef` 写入。前端不传 url

### 响应体（`StudyGroupResponse`，包裹在 `Result<T>`）

```json
{
  "code": 0,
  "data": {
    "id": 1,
    "name": "我的学习群",
    "description": "一起学Java",
    "avatarFileId": 123,
    "avatarUrl": "https://...",
    "ownerId": 100,
    "createdAt": 1718800000000,
    "updatedAt": 1718800000000
  }
}
```

**字段说明：**

- `avatarFileId` / `avatarUrl`：FileRef 双字段，FileRef 为空时均为 `null`
- `ownerId`：从 JWT 提取的当前用户 ID
- `createdAt` / `updatedAt`：毫秒时间戳（Long），项目统一规范
- 仅返回 `study_group` 本身，不含 owner 的 GroupMember 记录（如需查询自己的角色，由 REQ-49 `/api/study-groups/{id}/members/me` 提供）

### 业务流程

```
@Transactional
1. ownerId = SecurityUtils.getCurrentUserId()
2. name/description 字段校验由 @Valid 在 Controller 层完成（@NotBlank / @Size）
3. if avatarFileId != null:
   a. result = FileServiceClient.getFileInfo(avatarFileId)   // 返回 Result<FileInfoResponse>
   b. fileInfo = result.getData()
   c. if fileInfo == null → 抛 BusinessException(FILE_NOT_FOUND)
   d. 校验 fileInfo.metadata.bizType == "STUDY_GROUP_AVATAR"，否则抛 BusinessException(FILE_BIZ_TYPE_MISMATCH)
   e. 校验 fileInfo.metadata.userId == ownerId，否则抛 BusinessException(FILE_OWNER_MISMATCH)
   f. avatar = FileRef.of(avatarFileId, fileInfo.url)
4. group = StudyGroup.create(name, description, avatar, ownerId)
5. savedGroup = studyGroupRepository.save(group)
6. ownerMember = GroupMember.createOwner(savedGroup.getId(), ownerId)
7. groupMemberRepository.save(ownerMember)
8. return StudyGroupAssembler.toResponse(savedGroup)
```

> FileServiceClient.getFileInfo() 返回 `Result<FileInfoResponse>`（不是裸 FileInfoResponse），需先 `result.getData()` 解包。参考 `UserAppService.verifyFileRef`（UserAppService.java:185-198）。

事务包裹步骤 4-7，保证群组与 OWNER 成员记录原子写入。任一步骤失败，整体回滚。

### FileRef 写入流程（对齐 REQ-93）

1. 前端通过 `GET /api/upload-credential?bizType=STUDY_GROUP_AVATAR&count=1` 获取凭证（凭证接口在 `FilePathMapping` 注册 bizType 后自动可用）
2. 前端用凭证直传文件服务，得到 `fileId`
3. 前端调用 `POST /api/study-groups` 时只传 `avatarFileId`
4. 后端 AppService 调用 `FileServiceClient.getFileInfo(fileId)` 获取 url + metadata，校验 bizType + userId 后组装 FileRef

## 数据模型

### study_group 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT AUTO_INCREMENT | PK | 主键 |
| name | VARCHAR(50) | NOT NULL | 群组名称（无唯一约束） |
| description | VARCHAR(500) | NULL | 描述（可清空） |
| avatar_file_id | BIGINT | NULL | 头像文件 ID（REQ-93 FileRef，指向 file_info） |
| avatar_url | VARCHAR(500) | NULL | 头像 URL（REQ-93 冗余查询字段） |
| owner_id | BIGINT | NOT NULL | 创建人 ID（无 FK 约束） |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

**说明：** 无 status 字段（用户端硬删除语义，无状态切换）。无 `join_policy` 字段（REQ-49 再加）。无 `invite_code` 字段（REQ-49 再加）。

### group_member 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT AUTO_INCREMENT | PK | 主键 |
| group_id | BIGINT | NOT NULL | 关联群组 ID（无 FK 约束） |
| user_id | BIGINT | NOT NULL | 关联用户 ID（无 FK 约束） |
| role | VARCHAR(20) | NOT NULL | OWNER / ADMIN / MEMBER（VARCHAR 存枚举名，参考现有枚举存储惯例） |
| points | INT | NOT NULL DEFAULT 0 | 群组内积分（初始 0） |
| joined_at | DATETIME | NOT NULL | 加入时间 |

唯一约束：`UNIQUE(group_id, user_id)`

**说明：** 本表为 group_member 聚合根的存储，REQ-48 仅写入 OWNER 记录（points=0）。其余成员由 REQ-49 加入流程写入。`role` 用 VARCHAR 存枚举名而非 MySQL ENUM 类型，与项目现有 `KnowledgeCategoryStatus` / `QuestionStatus` 等枚举存储方式一致。

## 领域模型

### StudyGroup 聚合根（`core/domain/model/entity/StudyGroup.java`）

**字段：** `id` / `name` / `description` / `avatar (FileRef)` / `ownerId` / `createdAt` / `updatedAt`

**行为方法（REQ-48 仅需 create + reconstruct）：**

- `create(name, description, avatar, ownerId)` — 工厂方法（创建新群组，description/avatar 可为 null）
- `reconstruct(...)` — 从 PO 重建（Repository 加载用）

> update 系列方法（`update` / `updateDescription` / `clearDescription` / `updateAvatar` / `clearAvatar`）不在 REQ-48 范围。REQ-49 或独立的「群组管理员编辑群组信息」需求（未在 requirements.md 列出）实现时再加，遵循 YAGNI 原则。

**注意：** 不提供 `deactivate()` / `activate()` 等状态切换方法（用户端硬删除语义）。

### GroupMember 聚合根（`core/domain/model/entity/GroupMember.java`）

**字段：** `id` / `groupId` / `userId` / `role (GroupRole)` / `points` / `joinedAt`

**行为方法（REQ-48 仅需 create + reconstruct）：**

- `createOwner(groupId, userId)` — 工厂方法，构造 role=OWNER / points=0 / joinedAt=now 的成员记录
- `reconstruct(...)` — 从 PO 重建

> REQ-49 / REQ-52 扩展其他工厂方法（`joinAsMember`、`promoteToAdmin`、`updatePoints` 等）

### GroupRole 枚举（`core/domain/model/domainenum/GroupRole.java`）

`OWNER` / `ADMIN` / `MEMBER`

### 出端口（`core/domain/port/outbound/`）

**StudyGroupRepository：**
- `save(StudyGroup): StudyGroup`
- `findById(Long): Optional<StudyGroup>`
- `existsById(Long): boolean`
- `deleteById(Long): void` — 硬删除

**GroupMemberRepository：**
- `save(GroupMember): GroupMember`
- `findByGroupIdAndUserId(Long, Long): Optional<GroupMember>`
- `existsByGroupIdAndUserId(Long, Long): boolean`

> REQ-48 仅暴露创建所需方法。后续 REQ-49 扩展 `findByGroupId`、`deleteByGroupIdAndUserId` 等

## 持久化层（infrastructure）

| 组件 | 文件位置 |
|------|---------|
| `StudyGroupPO` | `core/infrastructure/db/entity/StudyGroupPO.java` |
| `StudyGroupJpaRepository` | `core/infrastructure/db/repository/StudyGroupJpaRepository.java` |
| `StudyGroupRepositoryAdapter` | `core/infrastructure/adapter/repoadapter/StudyGroupRepositoryAdapter.java`（实现出端口） |
| `StudyGroupConverter` | `core/infrastructure/db/converter/StudyGroupConverter.java`（MapStruct） |
| `GroupMemberPO` | `core/infrastructure/db/entity/GroupMemberPO.java` |
| `GroupMemberJpaRepository` | `core/infrastructure/db/repository/GroupMemberJpaRepository.java` |
| `GroupMemberRepositoryAdapter` | `core/infrastructure/adapter/repoadapter/GroupMemberRepositoryAdapter.java` |
| `GroupMemberConverter` | `core/infrastructure/db/converter/GroupMemberConverter.java`（MapStruct） |

**注意：**
- 所有 PO 不含 `@ForeignKey` 物理外键（项目规范）
- `StudyGroupPO` 的 `avatar_file_id` + `avatar_url` 双字段持久化（参考 IpSeriesPO 的 cover_image 实现）
- `GroupMemberPO` 的 `role` 字段使用 `@Enumerated(EnumType.STRING)` 持久化（参考现有枚举存储）

## 应用层 + API 层（app 模块）

### 包结构

```
com.knowledgegame.app/
├── api/
│   ├── controller/StudyGroupController.java
│   └── dto/
│       ├── CreateStudyGroupRequest.java
│       └── StudyGroupResponse.java
├── application/
│   ├── service/StudyGroupAppService.java
│   └── assembler/StudyGroupAssembler.java
```

### StudyGroupController

```java
@RestController
@RequestMapping("/api/study-groups")
public class StudyGroupController {
    private final StudyGroupAppService appService;

    @PostMapping
    public Result<StudyGroupResponse> create(@Valid @RequestBody CreateStudyGroupRequest request) {
        return Result.success(appService.create(request));
    }
}
```

### StudyGroupAppService

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
        StudyGroup group = StudyGroup.create(request.getName(), request.getDescription(), avatar, ownerId);
        StudyGroup saved = studyGroupRepository.save(group);
        GroupMember owner = GroupMember.createOwner(saved.getId(), ownerId);
        groupMemberRepository.save(owner);
        return StudyGroupAssembler.INSTANCE.toResponse(saved);
    }

    private FileRef resolveAvatar(Long fileId, Long ownerId) {
        if (fileId == null) return null;
        Result<FileInfoResponse> result = fileServiceClient.getFileInfo(fileId);
        FileInfoResponse info = result.getData();
        if (info == null) throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        Map<String, Object> metadata = info.getMetadata();
        if (metadata == null || !"STUDY_GROUP_AVATAR".equals(metadata.get("bizType")))
            throw new BusinessException(ResultCode.FILE_BIZ_TYPE_MISMATCH);
        Object metaUserId = metadata.get("userId");
        Long metaUserIdLong = metaUserId instanceof Number ? ((Number) metaUserId).longValue() : null;
        if (!Objects.equals(ownerId, metaUserIdLong))
            throw new BusinessException(ResultCode.FILE_OWNER_MISMATCH);
        return FileRef.of(fileId, info.getUrl());
    }
}
```

> 伪代码参考 `UserAppService.verifyFileRef`（UserAppService.java:185-198），含 JSON 列 Integer/Long 陷阱处理（`metaUserId` 转 Long 后比较）。

### StudyGroupAssembler（MapStruct）

- `toResponse(StudyGroup): StudyGroupResponse`
- FileRef 解包为 `avatarFileId` + `avatarUrl`
- `LocalDateTime` → 毫秒时间戳（参考 app 模块 `KnowledgeCategoryAssembler.toEpochMilli`，KnowledgeCategoryAssembler.java:31-34）

### 错误处理

| 场景 | HTTP | ResultCode | 消息 |
|------|------|------------|------|
| name 空 / 超长 | 400 | FAIL（Bean Validation 触发） | 由 `GlobalExceptionHandler.handleValidationException` 处理 |
| description 超长 | 400 | FAIL（Bean Validation 触发） | 同上 |
| avatarFileId 文件不存在 | 200 | **FILE_NOT_FOUND**（新增，code=400） | 文件不存在 |
| avatarFileId bizType 不匹配 | 200 | **FILE_BIZ_TYPE_MISMATCH**（新增，code=400） | 文件业务类型不匹配 |
| avatarFileId userId 不匹配 | 200 | **FILE_OWNER_MISMATCH**（新增，code=403） | 无权使用该文件 |
| FileServiceClient Feign 调用失败（网络/服务宕机） | 500 | INTERNAL_ERROR | 由 `GlobalExceptionHandler.handleException` 兜底；返回"服务器内部错误"，详细信息查日志（traceId） |

> **注意：** BusinessException 由 GlobalExceptionHandler 统一返回 HTTP 200，通过 Result.code 区分业务错误类型（400/403）。仅 Bean Validation 异常（MethodArgumentNotValidException）和系统异常（Exception）会修改 HTTP 状态码。

**新增 ResultCode 决策**（推荐方案 A，对齐项目"禁止魔法字符串"设计决策）：

在 `core/common/result/ResultCode.java` 新增三条枚举：

```java
FILE_NOT_FOUND(400, "文件不存在"),
FILE_BIZ_TYPE_MISMATCH(400, "文件业务类型不匹配"),
FILE_OWNER_MISMATCH(403, "无权使用该文件"),
```

> 已知技术债：现有 `UserAppService.verifyFileRef`（UserAppService.java:189-197）仍用硬编码 `new BusinessException(400, "...")`，未迁移到新枚举。**迁移不在 REQ-48 范围**，建议作为独立技术债清理任务（如不迁移，则 `ResultCode` 与现有硬编码并存，违反设计决策但不破坏功能）。

### FilePathMapping 注册

在 app 模块的 `FilePathMapping.java` 注册新 bizType。现有类使用 `Map.of(...)` 不可变 Map，value 格式无前后斜杠（参考现有 `"user-avatar"`）：

```java
// 修改前
private static final Map<String, String> MAPPING = Map.of(
        "USER_AVATAR", "user-avatar"
);

// 修改后（新增一条）
private static final Map<String, String> MAPPING = Map.of(
        "USER_AVATAR", "user-avatar",
        "STUDY_GROUP_AVATAR", "study-group-avatar"
);
```

> admin 模块暂不注册（管理端群组创建功能不在 REQ-48 范围）

## Impact Analysis

### 修改 / 新增文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `core/.../domain/model/entity/StudyGroup.java` | 新增 | 聚合根 |
| `core/.../domain/model/entity/GroupMember.java` | 新增 | 聚合根 |
| `core/.../domain/model/domainenum/GroupRole.java` | 新增 | 枚举 |
| `core/.../domain/port/outbound/StudyGroupRepository.java` | 新增 | 出端口接口 |
| `core/.../domain/port/outbound/GroupMemberRepository.java` | 新增 | 出端口接口 |
| `core/.../infrastructure/db/entity/StudyGroupPO.java` | 新增 | PO |
| `core/.../infrastructure/db/entity/GroupMemberPO.java` | 新增 | PO |
| `core/.../infrastructure/db/repository/StudyGroupJpaRepository.java` | 新增 | Spring Data JPA |
| `core/.../infrastructure/db/repository/GroupMemberJpaRepository.java` | 新增 | Spring Data JPA |
| `core/.../infrastructure/adapter/repoadapter/StudyGroupRepositoryAdapter.java` | 新增 | Repository 实现 |
| `core/.../infrastructure/adapter/repoadapter/GroupMemberRepositoryAdapter.java` | 新增 | Repository 实现 |
| `core/.../infrastructure/db/converter/StudyGroupConverter.java` | 新增 | MapStruct |
| `core/.../infrastructure/db/converter/GroupMemberConverter.java` | 新增 | MapStruct |
| `app/.../api/controller/StudyGroupController.java` | 新增 | Controller |
| `app/.../api/dto/CreateStudyGroupRequest.java` | 新增 | 请求 DTO |
| `app/.../api/dto/StudyGroupResponse.java` | 新增 | 响应 DTO |
| `app/.../application/service/StudyGroupAppService.java` | 新增 | 应用服务 |
| `app/.../application/assembler/StudyGroupAssembler.java` | 新增 | MapStruct |
| `app/.../config/FilePathMapping.java` | 修改 | 注册 STUDY_GROUP_AVATAR bizType |
| `docs/card-system-data-model.md` | 修改 | 同步 study_group / group_member 表设计，共 6 处差异：(1) `study_group.status` 列删除（无软删除语义）(2) `study_group.avatar VARCHAR(500)` 拆为 `avatar_file_id BIGINT` + `avatar_url VARCHAR(500)` 双字段（FileRef 规范）(3) `study_group.owner_id` 移除 `FK → user.id` 声明（项目无 FK 规范）(4) `group_member.group_id` 移除 `FK → study_group.id` 声明 (5) `group_member.user_id` 移除 `FK → user.id` 声明 (6) `group_member.role` 从 `ENUM('OWNER','ADMIN','MEMBER')` 改为 `VARCHAR(20)`（对齐项目现有枚举存储惯例） |
| `docs/overview.md` | 修改 | 同步 API 清单 + 数据模型表 + 设计决策 |

### 依赖关系

- app 模块已依赖 core 模块，直接使用新增的领域类型和出端口
- app 模块已依赖 component-feign（FileServiceClient），无需新增依赖
- core 模块新增两个独立聚合根 + 出端口 + 仓储适配器
- admin 模块不受影响（REQ-48 仅用户端）
- 无需修改现有表结构

### 不受影响的现有功能

- 用户端现有 API（User / KnowledgeCategory / UploadCredit）不受影响
- 管理端全部功能不受影响
- 现有 IpSeries / CardTemplate / KnowledgeCategory / Question / KnowledgeItem 聚合根不受影响

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| **领域层单测** | StudyGroupTest：`create` 字段赋值（含 description/avatar 为 null 边界）、createdAt/updatedAt 非空。GroupMemberTest：`createOwner` 构造 role=OWNER/points=0/joinedAt 非空 |
| **Controller @WebMvcTest** | 创建成功（200 + 毫秒时间戳响应）、name 空 / 超长（400）、description 超长（400）、AppService 抛 BusinessException 透传。`addFilters=false` 禁用 Security Filter |
| **AppService 单测（Mockito）** | 正常路径（mock FileServiceClient 返回有效 fileInfo，ArgumentCaptor 捕获两个 save 参数，断言 ownerId/role 正确）、avatarFileId=null 跳过 FileRef 校验、文件不存在抛 BusinessException、bizType 不匹配抛 BusinessException、userId 不匹配抛 BusinessException |
| **Repository 集成测试（@DataJpaTest 走真实 MySQL）** | StudyGroupRepositoryAdapterTest：save（含 FileRef 双字段持久化）、findById（找到/找不到）、deleteById（硬删除验证）。GroupMemberRepositoryAdapterTest：save 成功持久化 OWNER 记录、`UNIQUE(group_id, user_id)` 重复插入抛 `DataIntegrityViolationException`、`existsByGroupIdAndUserId`、`findByGroupIdAndUserId` |
| **Converter / Assembler 单测** | StudyGroupConverter 双向转换（含 FileRef 双字段映射）、GroupMemberConverter 双向转换（含 role 枚举互转）、StudyGroupAssembler（FileRef 解包 + LocalDateTime → 毫秒时间戳） |

**集成测试配置：**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import({StudyGroupRepositoryAdapter.class, GroupMemberRepositoryAdapter.class})
class StudyGroupRepositoryAdapterTest { ... }
```

### 手动验证

1. 注册并登录用户 A，调用 `POST /api/study-groups`，传 name + description，验证返回 200 + 正确响应
2. 查询 `study_group` 表，确认记录创建，owner_id = A 的 id
3. 查询 `group_member` 表，确认有一条 role=OWNER、points=0、joined_at 非空的记录
4. 上传一张头像（`STUDY_GROUP_AVATAR` bizType），创建群组时传 avatarFileId，验证响应包含 avatarUrl
5. 用别人的 fileId 创建群组，预期返回 400「文件不属于当前用户」
6. 用错误 bizType 的 fileId 创建群组，预期返回 400「文件业务类型不匹配」
7. **事务回滚验证**：执行第 5 或第 6 步后，查询 `study_group` 和 `group_member` 表，确认均无新增记录（`@Transactional` 回滚生效，FileRef 校验失败时群组与 OWNER 成员记录都不应写入）
8. 同一用户连续创建 2 个同名群组，验证均成功（无唯一约束）
9. 未登录调用 `POST /api/study-groups`，预期返回 401

### 回滚标准

- 仅新增文件 + 1 处修改，回滚只需：
  - 删除 app 模块 5 个新文件（Controller / CreateStudyGroupRequest / StudyGroupResponse / AppService / Assembler）
  - 删除 core 模块 13 个新文件（实体×2 / 枚举 / 端口×2 / PO×2 / JpaRepo×2 / Adapter×2 / Converter×2）
  - 撤销 `app/.../config/FilePathMapping.java` 中 `STUDY_GROUP_AVATAR` 注册行
- 数据库表如已创建需手动 DROP（`study_group` + `group_member`）
- `card-system-data-model.md` 和 `overview.md` 的同步修改一并回滚

## 未来扩展（不在 REQ-48 范围）

| 需求 | 涉及改动 |
|------|---------|
| REQ-49 成员管理（加入/退出/邀请） | (1) **必须先扩展 GroupMemberRepository 出端口**：新增 `findById(Long)` / `findByGroupId(Long)` / `deleteByGroupIdAndUserId(Long, Long)` / `countByGroupId(Long)` 等方法。REQ-48 仅暴露 save / findByGroupIdAndUserId / existsByGroupIdAndUserId 是 YAGNI 决策（避免提前加未使用方法），REQ-49 接手时必须扩展。(2) study_group 表可能新增 `join_policy` / `invite_code` 字段。(3) 新增 GroupMember 聚合的 `joinAsMember` / `leaveGroup` 等行为方法 |
| REQ-50 管理员设置与转让 | GroupMember 聚合的 `promoteToAdmin` / `transferOwner` 行为方法 + 对应出端口方法 |
| REQ-51 关联 IP 库 | 新增 `group_ip_library` 表 + 聚合根 |
| REQ-52 积分管理 | GroupMember 聚合的 `addPoints` / `deductPoints` 行为方法 + `point_transaction` 表 |
| 用户端编辑群组信息（未在 requirements.md 列出） | 复用 StudyGroup.update / updateDescription / clearDescription / updateAvatar / clearAvatar 方法，新增 `PUT /api/study-groups/{id}` 接口（需校验操作者为 OWNER） |
| 用户端删除群组（未在 requirements.md 列出） | 新增 `DELETE /api/study-groups/{id}`，调用 `studyGroupRepository.deleteById` 硬删除（需校验操作者为 OWNER，可能级联删除 group_member / 关联数据，依赖后续需求确定） |
