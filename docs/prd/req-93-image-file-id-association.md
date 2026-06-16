# REQ-93：图片字段统一以 file_id 为关联 key，image_url 降级为冗余查询字段

> 状态：`designed`
> 创建：2026-06-14
> 前置依赖：REQ-92（卡牌模板图片模型简化，必须先完成）
> 关联需求：REQ-90（ImageUploadField 通用组件，已 done）、REQ-83（文件服务）、REQ-87（admin/app 对接文件服务）、REQ-91（m2m 鉴权组件）

## 1. 概述

将所有引用图片的表从「以 `image_url` 字符串存储」改造为「以 `file_id` 关联 `knowledge-game-file` 的 FileInfo，`url` 作为冗余查询字段」。同时引入元数据（metadata）驱动校验机制：业务方在申请上传凭证时由后端组装 metadata，文件服务持久化到 FileInfo；业务方在写入业务表前通过内部机机接口取回 metadata 做自定义校验（防跨 bizType 滥用、防冒用他人 fileId）。

## 2. 用户故事

- **作为管理员**，我希望上传 IP 封面图、分类图标、卡面图等图片时，系统自动校验"该文件确实是我刚刚上传的、且用于正确的业务场景"，防止他人通过抓包改 fileId 把别人的图片塞进我的业务对象
- **作为用户**，我希望上传头像时享受同样的安全保障
- **作为开发者**，我希望所有图片字段使用统一的 `FileRef` 值对象表达，避免每个聚合根重复定义裸 `String url` 字段；前端表单只关心 fileId，url 由后端从文件服务获取并保证一致
- **作为架构守护者**，我希望未来新表（study_group / shop_product / achievement_template / group_achievement 等）创建时强制遵循同一套图片字段设计规范，避免出现历史遗留的不一致模式

## 3. 背景

### 3.1 当前状态

实际存在的图片字段（5 个字段、4 张表）：

| 表 | PO 字段 | 当前类型 |
|----|---------|---------|
| `user` | `UserPO.avatar` | VARCHAR |
| `card_template` | `CardTemplatePO.imageUrl` | VARCHAR(500)（REQ-92 引入） |
| `ip_series` | `IpSeriesPO.coverImageUrl` | VARCHAR(500) |
| `knowledge_category` | `KnowledgeCategoryPO.iconUrl` | VARCHAR(500) |
| `knowledge_category` | `KnowledgeCategoryPO.coverImageUrl` | VARCHAR(500) |

> **注**：requirements.md 提及的 `study_group/shop_product/achievement_template/group_achievement` 等表当前尚未实现 PO，本次仅预留设计规范（写入 CLAUDE.md），待这些表落地时强制遵循。

### 3.2 现有问题

1. **信任边界错位**：前端表单直接传 url 字符串到后端，后端无校验"该 url 是否真的指向用户自己上传的文件"，存在被伪造的可能
2. ** fileId 缺失**：业务表无法定位"该图片是 file_info 表的哪条记录"，无法做文件清理、引用计数、跨业务追溯
3. **跨 bizType 滥用**：用户上传一张 CATEGORY_ICON 的图片后，可拿同一 url 当作 CARD_TEMPLATE 提交，绕过 basePath 隔离
4. **冒用他人 fileId**：（本次改造前还没 fileId）改造后若不校验 uploader，用户可枚举他人 fileId 给自己用
5. **重复字段定义**：每个聚合根的图片字段都是 `String xxxUrl`，缺乏统一的"图片"概念表达

### 3.3 设计哲学

- **fileId 是事实源，url 是冗余**：双字段并存，但 url 始终由后端从 file 服务获取，前端不传 url
- **元数据驱动校验**：file 服务不感知 metadata 语义，业务方按 bizType 走策略录入自定义 metadata，校验时业务自实现
- **不依赖 basePath 映射校验**：未来 basePath 与 bizType 映射可能变化，用 metadata 而非 basePath 做校验保证历史数据兼容性
- **YAGNI 推迟扩展**：当前所有 bizType 共享 `{ bizType, userId }` metadata 模板；未来需要更多字段（如 categoryId、cardTemplateId）时再抽 `MetadataProvider` 策略

## 4. 设计方案

### 4.1 整体数据流

**写入流（POST/PUT 业务表单）：**

```
前端 ImageUploadField
  └─ 调 admin/app 凭证接口：GET /api/admin/upload-credit?bizType=CATEGORY_ICON
       （前端只关心 bizType，不传 metadata）

admin/app 凭证 Controller
  ├─ 从 JWT 拿 userId
  ├─ 按 bizType 组装 metadata：{ bizType: "CATEGORY_ICON", userId: 1 }
  ├─ 调 file 服务 POST /api/file/internal/credential，body 携带 metadata
  └─ 文件服务把 metadata 存进 CredentialState（内存 Map）
       返回 token + uploadUrl 给前端

前端 ImageUploadField 拿 token + uploadUrl
  └─ 直传文件到 file 服务 POST /api/file/upload
       Header: X-Upload-Token, X-User-Id

文件服务上传完成后
  └─ 从 CredentialState 取出 metadata，写入 FileInfo.metadata 列
       返回前端：{ fileId, url }（不返回 metadata）

前端 ImageUploadField 拿 { fileId, url }
  ├─ 组件内部存 url 用于缩略图显示
  └─ onChange(fileId) 通知父组件

业务表单提交：仅传 imageFileId

业务 AppService（admin/app 各自实现）
  ├─ 调 file 服务 Feign client: GET /api/file/internal/{fileId}
  ├─ 收到 { url, metadata, contentType, ... }（机机接口才返回 metadata）
  ├─ 业务自校验 metadata（bizType、userId 等）
  └─ 通过 → 用 file 服务返回的 url 覆盖前端传值（前端零信任）
          → 组装 FileRef(fileId, url) → 持久化双字段
```

**读取流（GET 业务接口）：**

```
业务 AppService 查询 → PO 取出双字段 → Assembler 转 DTO
  └─ DTO 同时返回 imageFileId + imageUrl
       前端用 imageUrl 渲染、用 imageFileId 回填编辑表单
```

### 4.2 关键不变量

- **双字段同事务一致性**：fileId 与 url 在写入时由 AppService 同事务组装（fileId 来自请求，url 来自 file 服务响应），不存在"fileId 有值但 url 为 null"的合法状态
- **前端零信任**：前端表单不传 url，url 完全由后端从 file 服务获取并强制覆盖
- **metadata 后端组装**：metadata 完全是后端之间的事，前端零感知
- **上传响应不含 metadata**：文件服务上传响应（前端可见）只含 `{ fileId, url }`；内部机机接口才返回 metadata

### 4.3 数据迁移策略

**不迁移历史数据**（与 REQ-92 风格一致）。dev 环境数据无生产价值：

- 旧 `image_url`/`icon_url`/`cover_image_url`/`avatar` 列直接 drop
- 新 `*_file_id` + `*_url` 双字段初始为 NULL
- 现有业务记录的图片需要重新上传

dev 由 JPA `ddl-auto: update` 自动完成 schema 变更（drop + add）。

## 5. 详细设计

### 5.1 文件服务（knowledge-game-file）改造

#### 5.1.1 数据库

```sql
ALTER TABLE file_info ADD COLUMN metadata JSON NULL COMMENT '业务自定义元数据';
```

#### 5.1.2 领域层

**FileInfo 领域模型新增 metadata 字段：**

```java
public class FileInfo {
    private Map<String, Object> metadata;
    // ...

    public static FileInfo create(String originalName, StoredFile storedFile,
                                   String basePath, long uploaderId,
                                   Map<String, Object> metadata) {
        FileInfo info = new FileInfo();
        // ...
        info.metadata = metadata;
        return info;
    }

    public static FileInfo reconstruct(Long id, String originalName, String storedName, String filePath,
                                String url, String contentType, long fileSize, String basePath,
                                long uploaderId, LocalDateTime createdAt, boolean deleted,
                                Map<String, Object> metadata) {
        // ...
    }

    public Map<String, Object> getMetadata() { return metadata; }
}
```

**UploadCredentialService 改造：**

```java
public String generateCredential(long userId, int count, String basePath,
                                  Map<String, Object> metadata) {
    String token = UUID.randomUUID().toString();
    String key = buildKey(userId, token);
    long expireAt = Instant.now().plusSeconds(expireMinutes * 60L).getEpochSecond();
    credentials.put(key, new CredentialState(expireAt, count, basePath, metadata));
    return token;
}

public Map<String, Object> getMetadata(long userId, String token) {
    CredentialState state = getState(userId, token);
    return state == null ? null : state.metadata;
}

private static class CredentialState {
    final long expireAt;
    final AtomicInteger remainingCount;
    final String basePath;
    final Map<String, Object> metadata;  // 新增

    CredentialState(long expireAt, int count, String basePath, Map<String, Object> metadata) {
        this.expireAt = expireAt;
        this.remainingCount = new AtomicInteger(count);
        this.basePath = basePath;
        this.metadata = metadata;
    }
}
```

#### 5.1.3 基础设施层

**FileInfoPO 新增 metadata 列（Hibernate 6 原生 JSON 支持）：**

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "metadata")
private Map<String, Object> metadata;
```

**FileInfoConverter 自动映射**（同类型 Map，MapStruct 直接处理）。

#### 5.1.4 API 层

**FileController 内部 m2m 凭证接口改造**（`POST /api/file/internal/credential`）：

参数从 `@RequestParam` 改为 `@RequestBody`，因为新增的 metadata 是 Map，不适合 query string。**注意：仅 file 服务的内部 m2m 接口改造，admin/app 的对外接口（前端可见）保持 GET 不变**。

```java
// 旧：@RequestParam 接收 userId、count、basePath
// 新：@RequestBody 接收（含 metadata）
@PostMapping("/internal/credential")
public Result<String> generateCredential(@RequestBody GenerateCredentialRequest request) {
    String token = fileAppService.generateCredential(
        request.userId(), request.count(), request.basePath(), request.metadata()
    );
    return Result.success(token);
}

public record GenerateCredentialRequest(
    Long userId,
    Integer count,
    String basePath,
    Map<String, Object> metadata
) {}
```

**FileInfoResponse 新增 metadata 字段：**

```java
@Getter
@Builder
public class FileInfoResponse {
    private Long fileId;
    private String url;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String basePath;
    private Long uploaderId;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;  // 新增
}
```

**FileUploadResponse 不变**（仍只返回 `{ fileId, url }`，前端可见，不暴露 metadata）。

#### 5.1.5 Application 层

**FileAppService.uploadFile 改造：**

```java
@Transactional
public FileUploadResponse uploadFile(long userId, String token, MultipartFile file) {
    if (!credentialService.validate(userId, token)) {
        throw new BusinessException(401, "上传凭证无效或已过期");
    }
    String basePath = credentialService.getBasePath(userId, token);
    Map<String, Object> metadata = credentialService.getMetadata(userId, token);

    // ... 文件类型/大小校验、存储

    FileInfo fileInfo = FileInfo.create(
        file.getOriginalFilename(),
        storedFile,
        basePath,
        userId,
        metadata  // 新增
    );
    FileInfo saved = fileInfoRepository.save(fileInfo);
    // ...
}
```

**FileAppService.batchUploadFiles 同步改造**（与单上传保持一致）。

**FileAppService.generateCredential 签名同步：**

```java
public String generateCredential(long userId, int count, String basePath,
                                  Map<String, Object> metadata) {
    validateBasePath(basePath);
    return credentialService.generateCredential(userId, count, basePath, metadata);
}
```

### 5.2 core 模块改造

#### 5.2.1 新增 FileRef 值对象

**位置：** `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/model/vo/FileRef.java`

```java
package com.knowledgegame.core.domain.model.vo;

import java.util.Objects;

/**
 * 图片引用值对象
 * <p>
 * 表示聚合根对一张图片的引用，包含 fileId（事实源）和 url（冗余查询字段）。
 * <p>
 * 允许的两种状态：
 * - (null, null)：无图片
 * - (Long, String)：完整引用
 * <p>
 * 禁止的半状态（构造时抛 IllegalArgumentException）：
 * - (Long, null)：fileId 存在但 url 缺失
 * - (null, String)：url 存在但 fileId 缺失
 */
public final class FileRef {

    private final Long fileId;
    private final String url;

    private FileRef(Long fileId, String url) {
        this.fileId = fileId;
        this.url = url;
    }

    /**
     * 工厂方法：fileId 与 url 必须同时存在或同时为空
     */
    public static FileRef of(Long fileId, String url) {
        if (fileId == null && url == null) {
            return new FileRef(null, null);
        }
        if (fileId != null && url != null) {
            return new FileRef(fileId, url);
        }
        throw new IllegalArgumentException(
            "FileRef 禁止半状态: fileId=" + fileId + ", url=" + url
        );
    }

    public Long fileId() {
        return fileId;
    }

    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileRef other)) return false;
        return Objects.equals(fileId, other.fileId) && Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileId, url);
    }
}
```

#### 5.2.2 聚合根改造

| 聚合根 | 原字段 | 新字段 |
|---|---|---|
| `User` | `String avatar` | `FileRef avatar` |
| `IpSeries` | `String coverImageUrl` | `FileRef coverImage` |
| `KnowledgeCategory` | `String iconUrl` + `String coverImageUrl` | `FileRef icon` + `FileRef coverImage` |
| `CardTemplate`（REQ-92 后） | `String imageUrl` | `FileRef image` |

**聚合根行为方法签名**（以 KnowledgeCategory 为例）：

```java
public static KnowledgeCategory create(String name, FileRef icon, FileRef coverImage, ...);
public static KnowledgeCategory reconstruct(Long id, String name, FileRef icon, FileRef coverImage, ...);
public void update(String name, FileRef icon, FileRef coverImage, ...);
```

**关键规则：**
- `update()` 整体替换 FileRef（不做半更新），消除 `NullValuePropertyMappingStrategy.IGNORE` 歧义
- update 收到 `null` FileRef → 调用方明确表示"清空图片"
- update 收到 `FileRef.of(null, null)` → 同上
- update 收到 `FileRef.of(fileId, url)` → 写入新图片

#### 5.2.3 PO 改造（双字段持久化）

| PO | 改造 |
|---|---|
| `UserPO` | 删 `avatar`；新增 `avatar_file_id BIGINT` + `avatar_url VARCHAR(500)` |
| `IpSeriesPO` | 删 `cover_image_url`；新增 `cover_image_file_id BIGINT` + `cover_image_url VARCHAR(500)` |
| `KnowledgeCategoryPO` | 删 `icon_url`/`cover_image_url`；新增 `icon_file_id`/`icon_url`/`cover_image_file_id`/`cover_image_url` |
| `CardTemplatePO`（REQ-92 后） | 删 `image_url`；新增 `image_file_id BIGINT` + `image_url VARCHAR(500)` |

**数据库 schema 改造 SQL：**

```sql
ALTER TABLE user DROP COLUMN avatar;
ALTER TABLE user ADD COLUMN avatar_file_id BIGINT NULL;
ALTER TABLE user ADD COLUMN avatar_url VARCHAR(500) NULL;

ALTER TABLE ip_series DROP COLUMN cover_image_url;
ALTER TABLE ip_series ADD COLUMN cover_image_file_id BIGINT NULL;
ALTER TABLE ip_series ADD COLUMN cover_image_url VARCHAR(500) NULL;

ALTER TABLE knowledge_category DROP COLUMN icon_url;
ALTER TABLE knowledge_category ADD COLUMN icon_file_id BIGINT NULL;
ALTER TABLE knowledge_category ADD COLUMN icon_url VARCHAR(500) NULL;
ALTER TABLE knowledge_category DROP COLUMN cover_image_url;
ALTER TABLE knowledge_category ADD COLUMN cover_image_file_id BIGINT NULL;
ALTER TABLE knowledge_category ADD COLUMN cover_image_url VARCHAR(500) NULL;

ALTER TABLE card_template DROP COLUMN image_url;
ALTER TABLE card_template ADD COLUMN image_file_id BIGINT NULL;
ALTER TABLE card_template ADD COLUMN image_url VARCHAR(500) NULL;
```

dev 由 JPA `ddl-auto: update` 自动同步。

#### 5.2.4 Converter 改造

每个 Converter 用 default 方法处理 PO 双字段 ↔ Domain FileRef 双向映射：

```java
@Mapper
public interface KnowledgeCategoryConverter {

    default KnowledgeCategory toDomain(KnowledgeCategoryPO po) {
        FileRef icon = toFileRef(po.getIconFileId(), po.getIconUrl());
        FileRef cover = toFileRef(po.getCoverImageFileId(), po.getCoverImageUrl());
        return KnowledgeCategory.reconstruct(
            po.getId(), po.getName(), icon, cover, ...
        );
    }

    default KnowledgeCategoryPO toPO(KnowledgeCategory domain) {
        return KnowledgeCategoryPO.builder()
            .iconFileId(domain.getIcon() != null ? domain.getIcon().fileId() : null)
            .iconUrl(domain.getIcon() != null ? domain.getIcon().url() : null)
            // ...
            .build();
    }

    default void updatePO(@MappingTarget KnowledgeCategoryPO po, KnowledgeCategory domain) {
        // 显式赋值，不依赖 MapStruct IGNORE 策略
        if (domain.getIcon() == null) {
            po.setIconFileId(null);
            po.setIconUrl(null);
        } else {
            po.setIconFileId(domain.getIcon().fileId());
            po.setIconUrl(domain.getIcon().url());
        }
        // coverImage 同理
    }

    default FileRef toFileRef(Long fileId, String url) {
        if (fileId == null && url == null) return null;
        return FileRef.of(fileId, url);
    }
}
```

`UserConverter`、`IpSeriesConverter`、`CardTemplateConverter` 同理。

#### 5.2.5 领域服务

| 类 | 改造 |
|---|---|
| `UserDomainService` | `validateAndCreate` 等签名：String avatar → FileRef avatar |
| `IpSeriesDomainService` | 同上：coverImageUrl → coverImage |
| `KnowledgeCategoryDomainService` | 同上：iconUrl + coverImageUrl → icon + coverImage |
| `CardTemplateDomainService`（REQ-92 后） | 同上：imageUrl → image |

#### 5.2.6 core 模块测试

| 测试类 | 用例 |
|---|---|
| `FileRefTest`（新增） | `of(null, null)` OK；`of(Long, String)` OK；`of(Long, null)` 抛 `IllegalArgumentException`；`of(null, String)` 抛异常；equals/hashCode 基于 fileId+url |
| `UserTest` | create/update 时 FileRef 整体替换；update 传 null 清空 |
| `IpSeriesTest` | 同上，coverImage |
| `KnowledgeCategoryTest` | 同上，icon + coverImage 双字段 |
| `CardTemplateTest`（REQ-92 后） | 同上，image |
| `*ConverterTest`（4 个） | PO 双字段 → Domain FileRef；Domain FileRef → PO 双字段；null FileRef ↔ 双 null PO 字段；updatePO 整体替换 |
| `*DomainServiceTest`（4 个） | 签名同步 |

### 5.3 component-feign 改造

| 类 | 改造 |
|---|---|
| `FileServiceClient.generateCredential` | 签名加 `Map<String, Object> metadata` 参数（破坏式改造，所有调用方同步更新）。请求方式从 `@RequestParam` 改为 `@RequestBody` |
| `FileServiceClient.getFileInfo` | **新增方法**，映射文件服务已有端点 `GET /api/file/internal/{fileId}`，供业务 AppService 校验时调用。返回 `Result<FileInfoResponse>`，响应自动含 metadata 字段。**注：file 服务端端点已存在（`FileController:92`），但 Feign Client 中尚未暴露，本次新增** |
| `FileInfoResponse`（共享 DTO，位于 component-feign） | 加 `private Map<String, Object> metadata` 字段 |

### 5.4 admin 模块改造

#### 5.4.1 凭证接口组装 metadata

**关键约束**：admin 端对外凭证接口 `GET /api/admin/upload-credit`（前端可见）保持不变，仍接收 `bizType` 与 `count` 查询参数。**仅在该 Controller 内部按 bizType 组装 metadata 后，调 file 服务时透传 metadata**。

```java
@GetMapping("/upload-credential")
public Result<UploadCredentialResponse> getCredential(
        @RequestParam String bizType,
        @RequestParam(defaultValue = "1") int count) {
    Long userId = SecurityUtils.getCurrentUserId();
    String basePath = FilePathMapping.toBasePath(bizType);
    if (basePath == null) {
        throw new BusinessException("不支持的业务类型: " + bizType);
    }

    // 简单实现：所有 bizType 共享 { bizType, userId } metadata 模板
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("bizType", bizType);
    metadata.put("userId", userId);

    // 调 file 服务时透传 metadata（FileServiceClient.generateCredential 签名扩展）
    Result<String> credentialResult = fileServiceClient.generateCredential(userId, count, basePath, metadata);
    String token = credentialResult.getData();
    // ...其余逻辑不变
}
```

**预留扩展点**：未来若某 bizType 需要更多元数据（如 categoryId/cardTemplateId），抽 `MetadataProvider` 接口 + 各 bizType 实现。当前 YAGNI，所有 bizType 共享同一模板。

#### 5.4.2 业务 AppService 校验逻辑

```java
// KnowledgeCategoryAppService 示例
@Transactional
public KnowledgeCategoryResponse create(Long parent, String name,
                                         Long iconFileId, Long coverImageFileId, ...) {
    FileRef verifiedIcon = verifyFileRef(iconFileId, "CATEGORY_ICON");
    FileRef verifiedCover = verifyFileRef(coverImageFileId, "CATEGORY_COVER");

    KnowledgeCategory category = knowledgeCategoryDomainService.validateAndCreate(
        parent, name, verifiedIcon, verifiedCover, ...
    );
    // ...
}

/**
 * 校验 fileId 对应文件的 metadata，返回组装好的 FileRef
 *
 * @param fileId 前端传入的 fileId，可为 null
 * @param expectedBizType 期望的 bizType
 * @return FileRef（fileId 为 null 时返回 null），用 file 服务返回的 url 覆盖
 */
private FileRef verifyFileRef(Long fileId, String expectedBizType) {
    if (fileId == null) return null;

    FileInfoResponse info = fileServiceClient.getFileInfo(fileId);
    if (info == null) {
        throw new BusinessException(400, "文件不存在: " + fileId);
    }

    Map<String, Object> metadata = info.getMetadata();
    if (metadata == null || !expectedBizType.equals(metadata.get("bizType"))) {
        throw new BusinessException(400, "文件类型不匹配，期望 " + expectedBizType);
    }
    if (!Objects.equals(currentUserId, metadata.get("userId"))) {
        throw new BusinessException(403, "无权使用该文件");
    }

    // 用 file 服务返回的 url 覆盖（前端零信任）
    return FileRef.of(fileId, info.getUrl());
}
```

**校验逻辑放置位置**：每个 AppService 内联实现（YAGNI）。未来若校验逻辑出现重复，抽 `FileRefVerifier` 工具类。

#### 5.4.3 DTO 改造

| DTO | 改造 |
|---|---|
| `CreateKnowledgeCategoryRequest` | `String iconUrl/coverImageUrl` → `Long iconFileId/coverImageFileId` |
| `UpdateKnowledgeCategoryRequest` | 同上 |
| `KnowledgeCategoryResponse` | 新增 `Long iconFileId + String iconUrl`、`Long coverImageFileId + String coverImageUrl`（双字段并列） |
| `CreateIpSeriesRequest/UpdateIpSeriesRequest` | `coverImageUrl` → `coverImageFileId` |
| `IpSeriesResponse` | 新增双字段 |
| `CreateCardTemplateRequest/UpdateCardTemplateRequest`（REQ-92 后） | `imageUrl` → `imageFileId` |
| `CardTemplateResponse` | 新增双字段 |

#### 5.4.4 Controller 改造

仅 DTO 类型变化，逻辑不动（参数接收 + 结果返回）。

#### 5.4.5 admin 模块测试

| 测试类 | 用例 |
|---|---|
| `KnowledgeCategoryAppServiceTest` | `verifyFileRef` 三分支：fileId 为 null（跳过）；metadata 匹配（通过）；bizType 不匹配（抛异常）；userId 不匹配（抛异常） |
| `IpSeriesAppServiceTest` | 同上，coverImage |
| `CardTemplateAppServiceTest`（REQ-92 后版本） | 同上，image |
| `FileControllerTest`（凭证接口） | 验证调 file 服务时传了 metadata 含 bizType + userId |
| 各 `*ControllerTest` | Request DTO 字段改名，断言同步 |

### 5.5 app 模块改造

| 类 | 改造 |
|---|---|
| `UpdateUserRequest` | `String avatar` → `Long avatarFileId` |
| `UserResponse` | `String avatar` → `Long avatarFileId + String avatarUrl`（双字段） |
| `UserAppService.update` | 调 `verifyFileRef(avatarFileId, "USER_AVATAR")` 校验 |
| `app/api/controller/FileController`（凭证接口） | 同 admin 模式，组装 `{ bizType: "USER_AVATAR", userId }` metadata |
| `app/config/FilePathMapping` | 新增 `USER_AVATAR → user-avatar` 映射 |

#### 5.5.1 app 模块测试

| 测试类 | 用例 |
|---|---|
| `UserAppServiceTest` | `verifyFileRef` 三分支 |
| `FileControllerTest`（app 端凭证接口） | 验证 metadata 含 bizType + userId |
| `UserControllerTest` | Request DTO 字段改名，断言同步 |
| `app/config/FilePathMappingTest` | 新增 USER_AVATAR 映射断言 |

### 5.6 前端改造

#### 5.6.1 ImageUploadField 协议变更

**url 双来源设计原因**：

`url` 与 `internalUrl` 看似冗余，实际是为区分两种生命周期阶段：

| 来源 | 阶段 | 含义 |
|---|---|---|
| `internalUrl`（组件内部 state） | 新建模式 / 重新上传后 | 用户刚刚上传、尚未提交业务表单的临时 url。父组件未持久化该值，仅组件内部用于即时显示缩略图 |
| `url`（props 传入） | 编辑模式初始回填 | 父组件从后端 GET 响应中取出的已持久化 url，用于编辑模式打开表单时显示已有图片 |

派生值 `displayUrl = internalUrl ?? url` 优先显示最新上传的，其次显示已持久化的。表单提交时父组件只发 `fileId`，url 永远不进入业务表单 payload。

```typescript
export interface ImageUploadFieldProps {
  bizType: string;
  placeholder?: string;
  preview?: boolean;
  allowRemove?: boolean;

  /** 受控值：fileId（不再是 url 字符串） */
  value?: number;
  /** fileId 变更回调 */
  onChange?: (fileId: number | undefined) => void;

  /** 当前 fileId 对应的 url，用于显示缩略图。
   *  编辑模式由父组件从后端响应取出 url 传入；
   *  新建模式可不传，上传成功后组件内部维护临时 url */
  url?: string;

  maxSize?: number;
  acceptTypes?: string[];
}
```

**组件内部状态管理：**

```typescript
const [internalUrl, setInternalUrl] = useState<string | undefined>(undefined);

// 渲染时优先用 internalUrl（用户刚上传的），否则用 props.url（编辑模式回填）
const displayUrl = internalUrl ?? url;

const fileList: UploadFile[] = displayUrl
  ? [{ uid: '-1', name: 'image', status: 'done', url: displayUrl }]
  : [];

const handleCustomRequest = async (options) => {
  // ...上传逻辑不变
  const { fileId, url: newUrl } = await uploadFile(...);
  setInternalUrl(newUrl);    // 组件内部记录 url 用于显示
  onChange?.(fileId);        // 只对外暴露 fileId
  onSuccess?.({ url: newUrl });
};

const handleRemove = () => {
  setInternalUrl(undefined);
  onChange?.(undefined);
  return true;
};
```

#### 5.6.2 services 层改造

| 文件 | 改造 |
|---|---|
| `services/ipSeries.ts` | `CreateIpSeriesRequest.coverImageUrl: string` → `coverImageFileId: number`；`IpSeriesResponse` 新增双字段 |
| `services/cardTemplate.ts`（REQ-92 后版本） | `imageUrl` → `imageFileId`；`CardTemplateResponse` 双字段 |
| `services/knowledgeBase.ts`（或 CategoryFormModal 内联类型） | `iconUrl/coverImageUrl` → `iconFileId/coverImageFileId`；Response 双字段 |
| `services/user.ts`（用户端） | `UpdateUserRequest.avatar` → `avatarFileId`；`UserResponse` 双字段 |
| `services/fileUpload.ts` | **不变**（凭证接口仍只传 bizType） |

#### 5.6.3 业务页面表单改造（通用模式）

```typescript
// 表单值类型（以 IpSeries 为例）
type IpSeriesFormValues = {
  name: string;
  description?: string;
  coverImageFileId?: number;  // ← 替代 coverImageUrl
};

// 编辑模式初始值
const buildInitialValues = (): IpSeriesFormValues => {
  if (!editingRecord) return {};
  return {
    name: editingRecord.name,
    description: editingRecord.description,
    coverImageFileId: editingRecord.coverImageFileId,  // fileId 作为受控值
  };
};

// 表单 UI
<ProForm.Item name="coverImageFileId" label="封面图">
  <ImageUploadField
    bizType="IP_SERIES"
    placeholder="上传封面图"
    url={editingRecord?.coverImageUrl}  // ← 编辑模式从后端响应取 url 用于回填缩略图
  />
</ProForm.Item>
```

**业务页面改造清单：**

| 页面 | 字段改造 |
|---|---|
| `pages/IpSeries/index.tsx` | `coverImageUrl` → `coverImageFileId`；`<ImageUploadField url={editingRecord?.coverImageUrl} />` |
| `pages/KnowledgeBase/components/CategoryFormModal.tsx` | `iconUrl/coverImageUrl` → `iconFileId/coverImageFileId`；两个 ImageUploadField 分别传 `url` prop |
| `pages/CardTemplate/index.tsx`（REQ-92 后版本） | `imageUrl` → `imageFileId`；`<ImageUploadField url={editingRecord?.imageUrl} />` |
| `frontend/user/.../Profile.tsx`（用户端） | `avatar` → `avatarFileId`；`<ImageUploadField url={user?.avatarUrl} />` |

#### 5.6.4 前端测试改造

| 测试类 | 改造 |
|---|---|
| `components/ImageUploadField/__tests__/index.test.tsx` | 旧 `onChange(string)` 断言全改为 `onChange(number)`；新增"url prop 渲染缩略图"用例；新增"上传成功后 internalUrl 更新"用例；新增"删除清空 internalUrl"用例 |
| `pages/IpSeries/__tests__/index.test.tsx` | 表单字段名 `coverImageUrl` → `coverImageFileId`；提交 payload 改为 `{ coverImageFileId: 1 }`；编辑模式 url prop 传入 |
| `pages/KnowledgeBase/__tests__/CategoryFormModal.test.tsx` | 同上，icon/cover 双字段 |
| `pages/CardTemplate/__tests__/index.test.tsx`（REQ-92 后版本） | 同上 |
| `frontend/user/.../Profile.test.tsx` | avatar → avatarFileId |

### 5.7 CLAUDE.md 新增章节

在项目 CLAUDE.md 的 `Component Development Rules` 章节后新增 `Image Field Design Rules`：

```markdown
## Image Field Design Rules

- 所有引用图片的表（聚合根）必须使用 `FileRef` 值对象（位于 `core/domain/model/vo/FileRef.java`），包含 `fileId` 与 `url` 双字段，禁止单独存储 `xxxUrl` 字符串
- PO 层双字段持久化：`xxx_file_id BIGINT` + `xxx_url VARCHAR(500)`，由 Converter default 方法处理双向映射
- 前端表单提交只传 `xxxFileId: Long`，**不传 url**；url 由后端从 file 服务获取并覆盖
- 业务 AppService 写入前必须调 `FileServiceClient.getFileInfo(fileId)` 校验 metadata：
  - `metadata.bizType` 匹配（防跨 bizType 滥用）
  - `metadata.userId` 等于当前操作用户（防冒用他人 fileId）
  - 校验失败抛 BusinessException
- metadata 由 admin/app 后端凭证接口按 bizType 组装（前端零感知），通过 file 服务 `POST /api/file/internal/credential` 持久化到 FileInfo
- API 响应（GET）必须同时返回 `xxxFileId` 和 `xxxUrl`，前端用 url 直接渲染、用 fileId 回填编辑表单
- 前端图片上传统一使用 `<ImageUploadField bizType="XXX" url={response.xxxUrl} />`，value 是 fileId
- FileRef 禁止 `(Long, null)` 与 `(null, String)` 半状态，`FileRef.of()` 抛异常阻止
- Converter `updatePO` 必须显式赋值双字段（参考 NullValuePropertyMappingStrategy.IGNORE 坑），整体替换 FileRef
```

## 6. 改动范围汇总

### 6.1 新增

| 文件 | 说明 |
|---|---|
| `backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/model/vo/FileRef.java` | FileRef 值对象 |
| `backend/knowledge-game-core/src/test/java/com/knowledgegame/core/domain/model/vo/FileRefTest.java` | FileRef 单元测试 |

### 6.2 修改 — 后端

| 模块 | 文件 | 改造 |
|---|---|---|
| file | `domain/model/FileInfo.java` | 新增 metadata 字段，签名扩展 |
| file | `domain/service/UploadCredentialService.java` | 新增 metadata 参数与 getMetadata 方法 |
| file | `infrastructure/db/entity/FileInfoPO.java` | 新增 metadata JSON 列 |
| file | `application/FileAppService.java` | generateCredential + uploadFile 签名扩展 |
| file | `api/controller/FileController.java` | 内部 m2m 凭证接口 `POST /api/file/internal/credential` 从 `@RequestParam` 改为 `@RequestBody` |
| file | `api/dto/FileInfoResponse.java` | 新增 metadata 字段 |
| core | `domain/model/entity/User.java` | avatar: String → FileRef |
| core | `domain/model/entity/IpSeries.java` | coverImageUrl: String → FileRef coverImage |
| core | `domain/model/entity/KnowledgeCategory.java` | iconUrl + coverImageUrl → FileRef icon + coverImage |
| core | `domain/model/entity/CardTemplate.java`（REQ-92 后） | imageUrl: String → FileRef image |
| core | `infrastructure/db/entity/UserPO.java` | 双字段持久化 |
| core | `infrastructure/db/entity/IpSeriesPO.java` | 双字段持久化 |
| core | `infrastructure/db/entity/KnowledgeCategoryPO.java` | 双字段持久化（2 组） |
| core | `infrastructure/db/entity/CardTemplatePO.java`（REQ-92 后） | 双字段持久化 |
| core | `infrastructure/db/converter/UserConverter.java` | default 方法处理双向映射 |
| core | `infrastructure/db/converter/IpSeriesConverter.java` | 同上 |
| core | `infrastructure/db/converter/KnowledgeCategoryConverter.java` | 同上 |
| core | `infrastructure/db/converter/CardTemplateConverter.java`（REQ-92 后） | 同上 |
| core | `domain/service/UserDomainService.java` | 签名同步 |
| core | `domain/service/IpSeriesDomainService.java` | 同上 |
| core | `domain/service/KnowledgeCategoryDomainService.java` | 同上 |
| core | `domain/service/CardTemplateDomainService.java`（REQ-92 后） | 同上 |
| component-feign | `FileServiceClient.java` | generateCredential 签名加 metadata；新增 getFileInfo 方法（映射 file 服务已有端点 `GET /api/file/internal/{fileId}`） |
| component-feign | `FileInfoResponse.java`（共享 DTO） | 加 metadata 字段 |
| admin | `api/dto/request/*Request.java`（4 个） | url → fileId |
| admin | `api/dto/response/*Response.java`（4 个） | 新增双字段 |
| admin | `application/service/*AppService.java`（3 个） | verifyFileRef 校验逻辑 |
| admin | `api/controller/FileController.java`（凭证接口） | 组装 metadata |
| app | `api/dto/request/UpdateUserRequest.java` | avatar → avatarFileId |
| app | `api/dto/response/UserResponse.java` | 双字段 |
| app | `application/service/UserAppService.java` | verifyFileRef 校验 |
| app | `api/controller/FileController.java`（凭证接口） | 组装 metadata |
| app | `config/FilePathMapping.java` | 新增 USER_AVATAR 映射 |

### 6.3 修改 — 前端

| 文件 | 改造 |
|---|---|
| `frontend/admin/src/components/ImageUploadField/index.tsx` | value/onChange 类型从 string 改为 number；新增 url prop |
| `frontend/admin/src/components/ImageUploadField/__tests__/index.test.tsx` | 全面重写 |
| `frontend/admin/src/pages/IpSeries/index.tsx` | coverImageUrl → coverImageFileId |
| `frontend/admin/src/pages/IpSeries/__tests__/index.test.tsx` | 同步 |
| `frontend/admin/src/pages/KnowledgeBase/components/CategoryFormModal.tsx` | iconUrl/coverImageUrl → iconFileId/coverImageFileId |
| `frontend/admin/src/pages/KnowledgeBase/components/__tests__/CategoryFormModal.test.tsx` | 同步 |
| `frontend/admin/src/pages/CardTemplate/index.tsx`（REQ-92 后版本） | imageUrl → imageFileId |
| `frontend/admin/src/pages/CardTemplate/__tests__/index.test.tsx` | 同步 |
| `frontend/admin/src/services/ipSeries.ts` | 类型同步 |
| `frontend/admin/src/services/cardTemplate.ts` | 类型同步 |
| `frontend/admin/src/services/knowledgeBase.ts` | 类型同步（如独立文件） |
| `frontend/user/.../Profile.tsx` | avatar → avatarFileId |
| `frontend/user/.../Profile.test.tsx` | 同步 |
| `frontend/user/src/services/user.ts` | 类型同步 |

### 6.4 修改 — 文档

| 文件 | 改造 |
|---|---|
| `CLAUDE.md` | 新增 `Image Field Design Rules` 章节 |
| `docs/requirements.md` | REQ-93 状态 `idea → designed`，挂 PRD 链接 |
| `docs/overview.md` | 同步图片字段说明 |
| `docs/card-system-data-model.md` | 同步 card_template 字段（与 REQ-92 协同） |

## 7. 不在本次范围

| 项目 | 原因 |
|---|---|
| 历史数据迁移 | dev 环境无生产数据，旧 url 字段直接 drop（用户决策） |
| MetadataProvider 策略抽象 | 当前所有 bizType 共享 `{ bizType, userId }` 模板，YAGNI |
| study_group/shop_product/achievement_template/group_achievement 等未来表的图片字段 | 当前无 PO，本次仅预留设计规范到 CLAUDE.md |
| Redis 化凭证管理 | 当前内存 Map 满足单实例需求，多实例部署时再迁移（与 REQ-81 同等待办） |
| FileInfo metadata 的 schema 校验 | file 服务不感知 metadata 语义，纯透传 |

## 8. Verification Plan

### 8.1 自动化测试

**FileRef 单元测试**（`core/domain/model/vo/FileRefTest.java`）：

| 用例 | 覆盖点 |
|---|---|
| `of(null, null)` | 返回 FileRef，fileId/url 均为 null |
| `of(Long, String)` | 返回 FileRef，字段正确 |
| `of(Long, null)` | 抛 IllegalArgumentException |
| `of(null, String)` | 抛 IllegalArgumentException |
| equals/hashCode | 基于 fileId+url |

**UploadCredentialService 测试**（file 模块）：

| 用例 | 覆盖点 |
|---|---|
| metadata 存储 | generateCredential 后 getMetadata 返回相同 Map |
| 过期清理 | cleanupExpired 时 metadata 一并清理 |
| token 复用 | getMetadata 与 validate 共享同一 CredentialState |

**FileAppService 测试**（file 模块）：

| 用例 | 覆盖点 |
|---|---|
| uploadFile 写入 metadata | FileInfo.metadata 等于凭证传入的 metadata |
| batchUploadFiles 同上 | 批量场景同样写入 |

**Converter 测试**（core 模块，4 个）：

| 用例 | 覆盖点 |
|---|---|
| toDomain: PO 双字段 → FileRef | 双 null 返回 null；(Long, String) 返回 FileRef |
| toPO: FileRef → PO 双字段 | null FileRef → 双 null；FileRef.of(Long, String) → 双字段 |
| updatePO: 整体替换 | null FileRef 清空双字段；非 null 写入新值 |

**AppService 测试**（admin/app 模块，4 个）：

| 用例 | 覆盖点 |
|---|---|
| verifyFileRef: fileId 为 null | 跳过校验，返回 null |
| verifyFileRef: metadata 匹配 | 通过，返回 FileRef.of(fileId, info.url) |
| verifyFileRef: bizType 不匹配 | 抛 BusinessException(400) |
| verifyFileRef: userId 不匹配 | 抛 BusinessException(403) |
| verifyFileRef: 文件不存在 | 抛 BusinessException(400) |

**Controller 测试**：

| 测试类 | 用例 |
|---|---|
| admin `FileControllerTest`（凭证接口） | 调 file 服务时传 metadata 含 bizType + userId |
| app `FileControllerTest`（凭证接口） | 同上 |
| 各业务 `*ControllerTest` | Request/Response DTO 字段改名同步 |
| app `FilePathMappingTest` | 新增 USER_AVATAR → user-avatar 映射断言 |

**前端测试**：

| 测试类 | 用例 |
|---|---|
| `ImageUploadField/__tests__/index.test.tsx` | value 类型 number；onChange 类型 number；url prop 渲染缩略图；internalUrl 更新；删除清空 |
| 业务页面测试 | 表单字段名变更、提交 payload 变更 |

### 8.2 手动验证

1. **admin 端 IP 系列管理页**
   - 新建 IP 系列：上传封面图，提交成功，详情正确展示
   - 编辑 IP 系列：现有 fileId 回填，url 缩略图正常显示
   - 上传图片后，FileRef 校验通过，url 与 file 服务一致
2. **admin 端知识库管理页**
   - 新建分类：上传 icon + cover，提交成功
   - 编辑分类：fileId 回填，url 缩略图显示
3. **admin 端卡牌管理页**（REQ-92 后）
   - 编辑卡牌：上传卡面图，提交成功
4. **app 端用户中心**
   - 编辑个人信息：上传头像，提交成功
   - 编辑模式回填头像缩略图
5. **跨 bizType 滥用防护**
   - 上传一张 CATEGORY_ICON 图片，拿 fileId 提交到 CARD_TEMPLATE 表单
   - 后端校验失败，返回 400「文件类型不匹配」
6. **冒用他人 fileId 防护**
   - 用户 A 上传头像拿到 fileId
   - 用户 B 拿这个 fileId 提交自己的头像更新
   - 后端校验失败，返回 403「无权使用该文件」

### 8.3 验证命令

```bash
# 后端构建（在 backend/ 目录下）
mvn clean install

# 后端单测
mvn test

# 前端类型检查（admin + user）
cd frontend/admin && npx tsc --noEmit
cd frontend/user && npx tsc --noEmit

# 前端单测
cd frontend/admin && npm test
cd frontend/user && npm test
```

完成标准：mvn 全绿、tsc 零新增 error、jest 全绿。

## 9. 影响分析

### 9.1 受影响的现有功能

| 功能 | 影响 |
|---|---|
| IpSeries 封面图（admin） | 字段 url → fileId，dev 数据需重新上传 |
| KnowledgeBase 图标 + 封面（admin） | 同上 |
| CardTemplate 卡面图（admin，REQ-92 后） | REQ-92 先引入 imageUrl，REQ-93 再改为 imageFileId |
| User 头像（app） | 字段 url → fileId，dev 数据需重新上传 |
| 文件服务内部 m2m 凭证接口（`POST /api/file/internal/credential`） | `@RequestParam` → `@RequestBody`（破坏式），所有调用方都是内部 Feign，可控。**admin/app 对外凭证接口（前端可见）保持 GET 不变** |
| FileInfoResponse | 新增 metadata 字段（只增） |
| ImageUploadField | value 类型 `string` → `number`（破坏式） |
| FileServiceClient | generateCredential 签名加 metadata（破坏式） |

### 9.2 与 REQ-90 / REQ-92 的关系

```
REQ-90（已 done）       ImageUploadField 抽象 + 3 个页面迁移
   ↓ 依赖
REQ-92（in-progress）   card_star_image → card_template.image_url（单图）
   ↓ 顺序依赖
REQ-93（本次）          图片字段统一为 FileRef{fileId, url}
```

**对 REQ-90 的影响**：ImageUploadField 协议从 `value: string` 改为 `value: number`，破坏式改造。所有 REQ-90 接入点（IpSeries/CardTemplate/KnowledgeBase）需同步修改表单字段类型。REQ-90 测试用例需重写。

**对 REQ-92 的依赖**：REQ-93 必须等 REQ-92 完成（card_star_image 删除 + card_template.image_url 引入）才能进入。本次 PRD 中 card_template 相关改造描述以"REQ-92 后版本"为准。

### 9.3 兼容性

- **数据库兼容性**：旧 image_url 字段 drop，dev 数据丢失（用户决策）
- **API 兼容性**：DTO 字段改名（破坏式），所有调用方都是内部前端，可控
- **凭证接口兼容性**：file 服务内部 m2m 接口 `@RequestParam` → `@RequestBody`（破坏式），所有调用方都是内部 Feign，可控。**admin/app 对外凭证接口（前端可见）保持 GET 不变**

## 10. 回滚标准

- 删除 `FileRef.java`
- 还原 4 个聚合根字段为 String（User.avatar / IpSeries.coverImageUrl / KnowledgeCategory.iconUrl+coverImageUrl / CardTemplate.imageUrl）
- 还原 4 个 PO 双字段为单字段（drop `*_file_id` 列，rename `*_url` 为原名）
- 还原 4 个 Converter（删除 default 方法）
- 还原 file 服务：FileInfo 删 metadata 字段、FileInfoPO 删 metadata 列、FileInfoResponse 删 metadata 字段、UploadCredentialService 删 metadata 参数
- 还原 FileController 凭证接口为 `@RequestParam`
- 还原 FileServiceClient.generateCredential 签名
- 还原 ImageUploadField value 类型为 string
- 还原所有 DTO 字段
- 删除 CLAUDE.md "Image Field Design Rules" 章节
- dev 数据库手动执行反向 SQL 恢复

## 11. 后续依赖

| 后续需求 | 影响 |
|---------|------|
| 未来表创建（study_group / shop_product / achievement_template / group_achievement 等） | 必须遵循 CLAUDE.md 的 `Image Field Design Rules` 章节 |
| 多实例部署（REQ-81 类似） | UploadCredentialService 内存 Map → Redis 迁移，metadata 一并迁移 |
| MetadataProvider 策略抽象 | 当某 bizType 需要更多 metadata（如 categoryId）时引入 |
| 文件清理增强 | 业务表双字段使得"按 fileId 反查业务引用"成为可能，可做文件清理时的引用计数 |
