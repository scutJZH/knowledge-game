# REQ-83：文件服务模块（图片上传/下载/删除）

> 状态：`designed`
> 创建：2026-06-11
> 前置依赖：REQ-84（Nacos 服务注册与发现 + OpenFeign）、REQ-85（机机鉴权组件）

## 1. 概述

新建独立 Maven 模块 `knowledge-game-file`（端口 8083），提供图片文件的上传、下载、删除能力。通过存储抽象层（`FileStorageProvider`）隔离本地磁盘与未来云存储（OSS/S3），业务表冗余存储 `file_id` + `url` 实现读写分离。

## 2. 用户故事

- **作为管理端用户**，我希望上传图片时先获取上传凭证，再直传文件服务，以保证上传安全性
- **作为管理端用户**，我希望上传完成后业务接口自动验证文件有效性并将 URL 入库，以便前端直接展示图片
- **作为管理端用户**，我希望删除业务数据时异步清理关联文件，即使文件删除失败也不影响业务操作
- **作为开发者**，我希望文件存储方式可配置切换（本地磁盘 → OSS/S3），以便未来迁移云存储时零改动业务代码
- **作为开发者**，我希望文件服务通过 API Key 组件做机机鉴权，业务服务无需感知鉴权逻辑

## 3. 功能需求

### 3.1 上传凭证

- 前端通过 app/admin 业务接口请求上传凭证（app/admin 通过 Feign 调用 `POST /api/file/internal/credential`）
- 请求参数：`userId`（上传者 ID）、`count`（本次凭证允许上传的文件数量，默认 1）
- 凭证包含 `userId` + 一次性 `token`（UUID）+ 剩余可用次数
- 凭证有效期为 **5 分钟**，过期自动失效
- 凭证支持指定次数上传（由 count 决定），每次上传消费 1 次，次数耗尽后凭证失效
- file 服务内存中维护凭证状态（`ConcurrentHashMap`），定时任务每分钟清理过期凭证

### 3.2 文件上传

- 前端携带凭证（`X-Upload-Token` 请求头 + `X-User-Id` 请求头）直接向 file 服务上传文件
- **单文件上传**：`POST /api/file/upload`，`multipart/form-data`，字段名 `file`
- **批量上传**：`POST /api/file/batch-upload`，`multipart/form-data`，字段名 `files`（多文件）
- 批量上传具有**原子性**：全部成功或全部失败（`@Transactional`）
- 批量上传的文件数量必须与凭证 count 完全一致（不多不少），否则返回 400
- 凭证消费时机：存储+入库成功后才消费，上传失败不浪费凭证
- 路径规划：
  - `/api/file/upload` — 前端直传（凭证鉴权），不走 M2M
  - `/api/file/internal/**` — 机机接口（M2M 鉴权），由 app/admin 通过 Feign 调用
  - `/static/**` — 静态资源，无鉴权
- 支持的业务类型（`bizType` 参数）：

| bizType | 说明 |
|---------|------|
| `IP_SERIES` | IP 系列封面图 |
| `CARD_STAR_IMAGE` | 卡牌星级图片 |
| `CATEGORY_ICON` | 知识点分类图标 |
| `CATEGORY_COVER` | 知识点分类封面 |
| `AVATAR` | 用户头像 |

- 文件约束：
  - 允许类型：`image/jpeg`、`image/png`、`image/gif`、`image/webp`
  - 单文件大小上限：**10MB**
- 上传成功返回：`{ fileId, url }`
- 凭证验证失败返回 `401`，文件约束不符返回 `400`

### 3.3 文件元数据

上传成功后 file 服务写入 `file_info` 表：

```sql
CREATE TABLE file_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_name VARCHAR(255) NOT NULL COMMENT '存储文件名（UUID）',
    file_path VARCHAR(500) NOT NULL COMMENT '磁盘相对路径',
    url VARCHAR(500) NOT NULL COMMENT '静态访问 URL',
    content_type VARCHAR(50) NOT NULL COMMENT 'MIME 类型',
    file_size BIGINT NOT NULL COMMENT '字节数',
    biz_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    uploader_id BIGINT NOT NULL COMMENT '上传者 userId',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记'
);
```

### 3.4 静态资源访问

- URL 格式：`http://{file-host}:{port}/static/{bizType}/{yyyyMMdd}/{storedName}`
- 通过 Spring `WebMvcConfigurer` 的 `addResourceHandlers` 映射磁盘目录到 `/static/**`
- 无需鉴权，前端/浏览器直接通过 URL 访问图片

### 3.5 文件删除

- `DELETE /api/file/internal/{fileId}` — M2M 接口（需 API Key 鉴权）
- 业务方（app/admin）在删除业务实体时，异步调用 file 服务删除关联文件
- 文件删除采用软删除（`file_info.deleted = 1`），定时任务异步清理磁盘文件
- 容忍删除失败：业务操作不受文件删除结果影响，失败时记录日志后续重试

### 3.6 文件查询（M2M）

- `GET /api/file/internal/{fileId}` — 验证文件存在 + 返回文件信息（M2M 接口）
- `POST /api/file/internal/batch-urls` — 批量查询文件 URL（请求体 `{ fileIds: [1,2,3] }`）
- app/admin 在业务写接口中调用验证文件存在性，同时获取 URL 冗余入库

### 3.7 存储抽象层

```java
// 领域层出端口
public interface FileStorageProvider {
    StoredFile store(String bizType, String originalName, InputStream content, long size, String contentType);
    void delete(String filePath);
    InputStream load(String filePath);
}
```

- `LocalFileStorageProvider`：本地磁盘实现，按 `bizType/yyyyMMdd/` 组织目录
- 未来扩展：`OssFileStorageProvider`、`S3FileStorageProvider`，只需实现接口 + 切换配置

## 4. 技术方案

### 4.1 模块结构

```
backend/knowledge-game-file/
├── pom.xml
└── src/main/java/com/knowledgegame/file/
    ├── api/
    │   ├── controller/
    │   │   └── FileController.java              ← 文件上传/删除/查询接口
    │   └── dto/
    │       ├── FileUploadResponse.java           ← 上传响应
    │       └── FileInfoResponse.java             ← 文件信息响应
    ├── application/
    │   └── FileAppService.java                   ← 应用服务（@Transactional）
    ├── domain/
    │   ├── model/
    │   │   ├── FileInfo.java                     ← 文件信息聚合根
    │   │   ├── StoredFile.java                   ← 存储结果值对象
    │   │   └── BizType.java                      ← 业务类型枚举
    │   ├── service/
    │   │   └── UploadCredentialService.java      ← 凭证生成/验证领域服务
    │   └── port/outbound/
    │       ├── FileStorageProvider.java           ← 存储出端口
    │       └── FileInfoRepository.java            ← 文件信息仓储端口
    ├── infrastructure/
    │   ├── adapter/repoadapter/
    │   │   └── FileInfoRepositoryAdapter.java     ← 仓储适配器
    │   ├── db/
    │   │   ├── entity/
    │   │   │   └── FileInfoPO.java               ← PO 持久化对象
    │   │   ├── repository/
    │   │   │   └── FileInfoJpaRepository.java     ← Spring Data JPA
    │   │   └── converter/
    │   │       └── FileInfoConverter.java          ← MapStruct PO ↔ Domain
    │   └── storage/
    │       └── LocalFileStorageProvider.java       ← 本地磁盘存储实现
    ├── common/
    │   └── config/
    │       ├── WebMvcConfig.java                   ← 静态资源映射 + CORS
    │       └── FileServiceAutoConfiguration.java   ← Bean 注册
    └── KnowledgeGameFileApplication.java          ← Spring Boot 启动类
```

### 4.2 依赖关系

```
knowledge-game-file → knowledge-game-core（依赖 Result、BusinessException）
knowledge-game-file → component-m2m-auth（REQ-85，M2M 鉴权）
knowledge-game-file → spring-boot-starter-web（Multipart + Controller）
knowledge-game-file → spring-boot-starter-data-jpa（file_info 表）
knowledge-game-file → spring-cloud-starter-openfeign（REQ-84，服务间调用）
```

### 4.3 上传流程时序

```
前端                app/admin            file 服务
 │                     │                    │
 │──POST /upload-credential──>│             │
 │                     │──Feign(M2M)────────>│ 生成凭证(userId+token)
 │                     │<──token+fileId──────│
 │<──{token, fileId}──│                    │
 │                     │                    │
 │──POST /api/file/upload───────────────────>│ 验证凭证+存储文件
 │   (X-Upload-Token, X-User-Id)            │
 │<──{fileId, url}──────────────────────────│
 │                     │                    │
 │──PUT /api/admin/ip-series/{id}──>│       │
 │   {coverImageFileId: fileId}     │       │
 │                     │──Feign(M2M)────────>│ 验证文件存在+获取URL
 │                     │<──{url}─────────────│
 │                     │ 更新业务表          │
 │                     │ file_id=url冗余写入 │
 │<──200───────────────│                    │
```

### 4.4 删除流程

```
业务方(app/admin)          file 服务
      │                      │
      │──Feign(M2M)──────────>│ DELETE /api/file/{fileId}
      │                      │ 软删除(deleted=1)
      │<──200────────────────│
      │                      │ (定时任务异步清理磁盘文件)
```

### 4.5 配置项

```yaml
# application.yml
knowledgegame:
  file:
    storage:
      type: local                          # 存储类型：local（未来支持 oss、s3）
      local:
        base-path: ./uploads               # 本地磁盘根目录
    upload:
      max-file-size: 10MB                  # 单文件大小上限
      allowed-types:                       # 允许的 MIME 类型
        - image/jpeg
        - image/png
        - image/gif
        - image/webp
    credential:
      expire-minutes: 5                    # 凭证过期时间
```

## 5. Impact Analysis

### 5.1 新增文件

| 文件 | 说明 |
|------|------|
| `backend/knowledge-game-file/pom.xml` | 新模块 POM |
| `backend/pom.xml` | 添加 knowledge-game-file 子模块 |
| `knowledge-game-file/src/.../api/controller/FileController.java` | 文件上传/删除/查询 Controller |
| `knowledge-game-file/src/.../api/dto/FileUploadResponse.java` | 上传响应 DTO |
| `knowledge-game-file/src/.../api/dto/FileInfoResponse.java` | 文件信息响应 DTO |
| `knowledge-game-file/src/.../application/FileAppService.java` | 应用服务 |
| `knowledge-game-file/src/.../domain/model/FileInfo.java` | 文件信息聚合根 |
| `knowledge-game-file/src/.../domain/model/StoredFile.java` | 存储结果值对象 |
| `knowledge-game-file/src/.../domain/model/BizType.java` | 业务类型枚举 |
| `knowledge-game-file/src/.../domain/service/UploadCredentialService.java` | 凭证领域服务 |
| `knowledge-game-file/src/.../domain/port/outbound/FileStorageProvider.java` | 存储出端口 |
| `knowledge-game-file/src/.../domain/port/outbound/FileInfoRepository.java` | 仓储端口 |
| `knowledge-game-file/src/.../infrastructure/adapter/repoadapter/FileInfoRepositoryAdapter.java` | 仓储适配器 |
| `knowledge-game-file/src/.../infrastructure/db/entity/FileInfoPO.java` | PO 持久化对象 |
| `knowledge-game-file/src/.../infrastructure/db/repository/FileInfoJpaRepository.java` | JPA Repository |
| `knowledge-game-file/src/.../infrastructure/db/converter/FileInfoConverter.java` | MapStruct Converter |
| `knowledge-game-file/src/.../infrastructure/storage/LocalFileStorageProvider.java` | 本地存储实现 |
| `knowledge-game-file/src/.../common/config/WebMvcConfig.java` | 静态资源映射 |
| `knowledge-game-file/src/.../common/config/FileServiceAutoConfiguration.java` | 自动配置 |
| `knowledge-game-file/src/.../KnowledgeGameFileApplication.java` | 启动类 |

### 5.2 修改文件

| 文件 | 变更内容 |
|------|---------|
| `backend/pom.xml` | 添加 knowledge-game-file 子模块 |
| 业务表 PO（未来） | 添加 `file_id` 列（当各业务模块接入文件服务时） |

### 5.3 配置变更

| 配置 | 说明 |
|------|------|
| `knowledge-game-file/src/main/resources/application.yml` | 数据源、端口 8082、文件存储配置 |
| Nacos 配置中心 | file 服务注册到 Nacos（依赖 REQ-84） |

### 5.4 受影响的现有功能

| 功能 | 影响 |
|------|------|
| 现有图片 URL 字段 | 无影响。现有 `coverImageUrl` 等字段继续使用，后续接入文件服务时逐步增加 `file_id` 字段 |
| app/admin 模块 | 新增 Feign Client 调用 file 服务（依赖 REQ-84） |

## 6. Verification Plan

### 6.1 手动验证

1. 启动 file 服务（端口 8082），确认无报错
2. 通过 app/admin 调用获取上传凭证接口，确认返回 token
3. 使用 curl/Postman 携带凭证上传图片，确认返回 `{fileId, url}`
4. 使用同一凭证再次上传，确认返回 401（凭证已消费）
5. 等待 6 分钟后使用新凭证上传，确认旧凭证已过期返回 401
6. 上传非图片文件（如 .txt），确认返回 400
7. 通过返回的 URL 在浏览器直接访问图片，确认静态资源映射正常
8. 通过 M2M 接口删除文件，确认 file_info 标记为 deleted
9. 通过 M2M 接口批量查询文件 URL，确认返回正确

### 6.2 自动化测试

- `UploadCredentialServiceTest`：验证凭证生成、验证、消费、过期清理
- `FileAppServiceTest`：验证上传流程（文件约束校验、凭证校验、元数据入库）
- `LocalFileStorageProviderTest`：验证文件存储到磁盘、目录结构、文件删除
- `FileInfoRepositoryAdapterTest`：验证 PO ↔ Domain 转换、软删除
- `FileControllerTest`（@WebMvcTest）：验证各端点的请求/响应、参数校验、错误码

### 6.3 回滚标准

- 删除 `backend/knowledge-game-file/` 目录
- 移除 `backend/pom.xml` 中的 file 子模块声明
- 移除 app/admin 中新增的 Feign Client（如有）
- 删除数据库 `file_info` 表
