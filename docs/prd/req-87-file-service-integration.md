# REQ-87：Admin/App 对接文件服务（获取上传凭证）+ 文件服务 basePath 改造

> 状态：`designed`
> 创建：2026-06-12
> 前置依赖：REQ-83（文件服务模块）、REQ-84（Nacos + Feign）、REQ-85（M2M 鉴权）

## 1. 概述

两部分工作：

1. **文件服务 basePath 改造**（REQ-83 迭代）：将 `BizType` 枚举替换为 `String basePath`，file 服务只感知目录路径，不感知业务含义。新增业务类型无需改 file 服务。
2. **Admin/App 凭证接口**：通过 Feign Client 调用 file 服务凭证接口，暴露获取上传凭证 API 给前端。公共部分统一放在 component-feign。

## 2. 用户故事

- **作为管理端用户**，我希望上传图片前先获取上传凭证和上传地址，以便安全地直传文件服务
- **作为普通用户**，我希望上传头像等图片时能获取上传凭证和上传地址
- **作为开发者**，我希望新增业务类型时只需在 app/admin 配置映射，无需改 file 服务
- **作为开发者**，我希望 Feign Client 和公共 DTO 按服务提供方维度组织在 component-feign 中，app/admin 共用

## 3. 功能需求

### 3.1 文件服务 basePath 改造

**目标**：将 `BizType` 枚举解耦为纯路径字符串，file 服务不持有业务类型定义。

改造内容：
- 移除 `BizType` 枚举类
- `FileInfo` 领域模型：`BizType bizType` → `String basePath`
- `FileInfoPO` 实体：`biz_type` 列重命名为 `base_path`（VARCHAR(50) 不变）
- `FileInfoConverter`：移除 `.name()` / `valueOf()` 转换，直接映射 String
- `FileInfoAssembler`：同上
- `FileAppService`：移除 `EnumUtils.valueOf(BizType.class, ...)` 校验，改为 basePath 格式校验（非空、无 `..`、无 `/` 前后缀）。上传接口不再接收 basePath 参数，改为从凭证中提取
- `FileController`：上传接口移除 `bizType`/`basePath` 参数，改为从凭证中获取；凭证接口增加 `basePath` 参数
- `LocalFileStorageProvider`：参数名从 `bizType` → `basePath`（逻辑不变，已用字符串拼路径）
- 凭证接口：`POST /api/file/internal/credential` 新增 `basePath` 参数，凭证绑定 basePath
- `UploadCredential`：内部存储增加 `basePath` 字段，上传时校验与凭证一致
- 所有测试用例：`BizType.IP_SERIES` → `"ip-series"` 等字符串常量

路径格式规范：
- basePath 必须为小写字母、数字、短横线组成（如 `ip-series`、`card-star-image`、`avatar`）
- 不允许包含 `..`、`/`、`\` 等路径遍历字符
- file 服务仅做格式校验，不维护合法值白名单

### 3.2 Feign Client（component-feign）

新建 `FileServiceClient` 接口，按提供方维度放在 component-feign 的 client 包中：

- `@FeignClient(name = "knowledge-game-file")`
- 调用 `POST /api/file/internal/credential` 获取上传凭证
- 请求参数：`userId`（Long）、`count`（int，默认 1）、`basePath`（String）
- 响应：`Result<String>`（凭证 token）

M2M 鉴权由 `M2mFeignInterceptor` 在调用方（app/admin）classpath 上自动注入请求头，Feign Client 接口本身不感知 M2M。

### 3.3 公共 DTO（component-feign）

`UploadCredentialResponse` 统一放在 component-feign，app/admin 共用：

```java
public record UploadCredentialResponse(
    String token,      // 上传凭证 token
    String uploadUrl   // 文件服务上传地址（如 http://localhost:8083/api/file/upload）
) {}
```

### 3.4 凭证 API — App 端

- 路径：`GET /api/upload-credential`
- 鉴权：JWT（用户登录态）
- 请求参数：`bizType`（String，必填，app/admin 业务类型）、`count`（可选，int，默认 1）
- userId 从 JWT 上下文获取（`SecurityUtils.getCurrentUserId()`），不信任前端传参
- 响应：`Result<UploadCredentialResponse>`

app/admin 在代码中维护 `bizType` → `basePath` 的映射关系（非配置文件），前端传入的 bizType 查不到对应 basePath 时返回 400

| bizType（前端传） | basePath（传给 file 服务） | 说明 |
|------------------|--------------------------|------|
| `IP_SERIES` | `ip-series` | IP 系列封面图 |
| `CARD_STAR_IMAGE` | `card-star-image` | 卡牌星级图片 |
| `CATEGORY_ICON` | `category-icon` | 知识点分类图标 |
| `CATEGORY_COVER` | `category-cover` | 知识点分类封面 |
| `AVATAR` | `avatar` | 用户头像 |

新增业务类型只需在 app/admin 的映射代码中添加一行，file 服务无需改动。

- 当 `count > 1` 时，`uploadUrl` 为批量上传地址（`/api/file/batch-upload`）
- 当 `count == 1` 时，`uploadUrl` 为单文件上传地址（`/api/file/upload`）

### 3.5 凭证 API — Admin 端

- 路径：`GET /api/admin/upload-credential`
- 其余与 App 端完全一致（鉴权、参数、响应、映射关系）

### 3.6 上传流程

凭证生成时已将 basePath 绑定到凭证内部，前端上传时无需再传 basePath，file 服务从凭证中提取：

```
前端 → POST uploadUrl
     → X-Upload-Token: {token}
     → X-User-Id: {userId}
     → file: {文件}

file 服务内部：校验 token → 从凭证中取出绑定的 basePath → 按 basePath 目录存储
```

## 4. 技术方案

### 4.1 新增文件

| 文件 | 说明 |
|------|------|
| `component-feign/.../client/FileServiceClient.java` | Feign Client 接口，调用 file 服务凭证 API |
| `component-feign/.../dto/UploadCredentialResponse.java` | 凭证响应 DTO（token + uploadUrl） |

### 4.2 修改文件 — 文件服务改造

| 文件 | 变更内容 |
|------|---------|
| `knowledge-game-file/.../domain/model/BizType.java` | 删除此文件 |
| `knowledge-game-file/.../domain/model/FileInfo.java` | `BizType bizType` → `String basePath` |
| `knowledge-game-file/.../domain/service/UploadCredentialService.java` | 凭证生成/校验增加 basePath 参数 |
| `knowledge-game-file/.../infrastructure/db/entity/FileInfoPO.java` | `bizType` 字段 → `basePath`，`@Column` 名改为 `base_path` |
| `knowledge-game-file/.../infrastructure/db/converter/FileInfoConverter.java` | 移除 enum 转换，直接 String 映射 |
| `knowledge-game-file/.../infrastructure/storage/LocalFileStorageProvider.java` | 参数名 bizType → basePath |
| `knowledge-game-file/.../application/FileAppService.java` | 移除 EnumUtils 校验，改为 basePath 格式校验；凭证接口增加 basePath |
| `knowledge-game-file/.../application/FileInfoAssembler.java` | 移除 `.name()` 调用 |
| `knowledge-game-file/.../api/controller/FileController.java` | 上传接口移除 bizType/basePath 参数（改为从凭证中获取）；凭证接口增加 basePath 参数 |
| `knowledge-game-file/.../api/dto/FileInfoResponse.java` | 字段名 bizType → basePath |
| knowledge-game-file 测试文件（约 4 个） | BizType.IP_SERIES → 字符串常量 |

### 4.3 修改文件 — Admin/App 对接

| 文件 | 变更内容 |
|------|---------|
| `component-feign/pom.xml` | 添加 knowledge-game-core 依赖（Result\<T\> 类型） |
| `knowledge-game-app/pom.xml` | 添加 component-m2m-auth 依赖 |
| `knowledge-game-admin/pom.xml` | 添加 component-m2m-auth 依赖 |
| `knowledge-game-app/.../api/controller/FileController.java` | **新增** App 端凭证 Controller |
| `knowledge-game-admin/.../api/controller/FileController.java` | **新增** Admin 端凭证 Controller |
| `knowledge-game-app/.../config/FilePathMapping.java` | **新增** bizType → basePath 代码映射 |
| `knowledge-game-admin/.../config/FilePathMapping.java` | **新增** bizType → basePath 代码映射 |
| `knowledge-game-app/src/main/resources/application.yml` | 添加 M2M 配置 + file 服务上传地址配置 |
| `knowledge-game-admin/src/main/resources/application.yml` | 同上 |

### 4.4 数据库变更

项目处于开发阶段，`file_info` 表中仅有测试数据，先清空再改列名：

```sql
TRUNCATE TABLE file_info;
ALTER TABLE file_info CHANGE COLUMN biz_type base_path VARCHAR(50) NOT NULL COMMENT '文件存储路径标识';
```

### 4.5 配置变更

**app/admin 的 application.yml 新增**：

```yaml
m2m:
  auth:
    enabled: true
    service-name: knowledge-game-app   # admin 端为 knowledge-game-admin
    api-key: ${FILE_SERVICE_API_KEY:default-dev-key}

knowledgegame:
  file:
    upload-base-url: http://localhost:8083   # file 服务地址，前端直传用
```

**Nacos 配置规划**：
- `common.yml`：`upload-base-url`、`m2m.auth.api-key`（各服务共用）
- 各服务专属 yml：`m2m.auth.service-name`（各服务不同）
- `biz-type-mapping` 不放配置文件，由 app/admin 在代码中维护（Java Map / 常量类），新增业务类型改代码即可

### 4.6 时序图

```
前端                  app/admin              component-feign          file 服务
 │                       │                       │                      │
 │──GET /upload-credit──>│                       │                      │
 │  (bizType, count)     │                       │                      │
 │                       │  userId = SecurityUtils.getCurrentUserId()   │
 │                       │  basePath = mapping.get(bizType)             │
 │                       │  (bizType 无映射则返回 400)                  │
 │                       │──FileServiceClient.generateCredential()──────>│
 │                       │  (userId, count, basePath)                   │
 │                       │  (M2mFeignInterceptor 在 app/admin 注入)     │
 │                       │<──Result<String>(token)──────────────────────│
 │                       │  拼接 uploadUrl = baseUrl + upload/batch     │
 │<──{token, uploadUrl}──│                       │                      │
 │                       │                       │                      │
 │──POST uploadUrl──────────────────────────────────────────────────────>│
 │  (X-Upload-Token, X-User-Id, file)                                   │
 │  file 服务：校验 token → 从凭证中取出 basePath → 存储文件            │
 │<──{fileId, url}─────────────────────────────────────────────────────│
```

### 4.7 依赖关系

```
knowledge-game-app ──→ component-feign（FileServiceClient + UploadCredentialResponse）
                   ──→ component-m2m-auth（M2mFeignInterceptor 注入鉴权请求头）
                   ──→ component-auth（SecurityUtils 获取 userId）

knowledge-game-admin → component-feign（FileServiceClient + UploadCredentialResponse）
                   ──→ component-m2m-auth（M2mFeignInterceptor 注入鉴权请求头）
                   ──→ component-auth（SecurityUtils 获取 userId）

component-feign ──→ knowledge-game-core（Result<T> 类型）
```

## 5. Impact Analysis

### 5.1 受影响的现有功能

| 功能 | 影响 |
|------|------|
| 文件服务上传接口 | 参数名 `bizType` → `basePath`，前后端均需同步。当前无前端调用，影响范围可控 |
| 文件服务测试用例 | 约 4 个测试文件需更新 BizType 枚举引用为字符串 |
| 现有 Controller（app/admin） | 无影响，新增 FileController |
| 现有配置 | app/admin application.yml 新增配置项，不影响已有配置 |
| component-feign | 新增 Client + DTO + core 依赖，不影响现有自动配置 |

### 5.2 回滚标准

- 还原 file 服务 BizType 枚举及相关代码（git revert）
- 还原 `file_info` 表列名 `base_path` → `biz_type`
- 移除 app/admin 新增的 FileController
- 移除 component-feign 新增的 FileServiceClient 和 UploadCredentialResponse
- 移除 app/admin pom.xml 新增依赖和 application.yml 新增配置

## 6. Verification Plan

### 6.1 手动验证

1. `mvn clean install` 全模块编译通过（含 file 服务改造）
2. 执行 DDL：`TRUNCATE TABLE file_info; ALTER TABLE file_info CHANGE COLUMN ...`
3. 启动 file 服务（8083）、app（8082）、admin（8081），确认三个服务均正常注册到 Nacos
4. 使用管理员 JWT 调用 `GET /api/admin/upload-credential?bizType=IP_SERIES&count=1`，确认返回 `{ token, uploadUrl }`
5. 使用用户 JWT 调用 `GET /api/upload-credential?bizType=AVATAR&count=1`，确认返回一致格式
6. 调用 `GET /api/upload-credential?bizType=INVALID_TYPE`，确认返回 400（无映射）
7. 验证 count>1 时 uploadUrl 为批量上传地址
8. 使用返回的 token + uploadUrl 直传 file 服务（不传 basePath），确认上传成功且文件存储在 `ip-series/` 目录
9. 未携带 JWT Token 调用凭证接口，确认返回 401
10. 验证 M2M 拦截器正确注入请求头（查看 file 服务日志）

### 6.2 自动化测试

- **File 服务测试**（需更新）：
  - `UploadCredentialServiceTest`：更新为字符串 basePath，增加凭证绑定 basePath 校验
  - `FileAppServiceTest`：更新为字符串 basePath，增加格式校验测试
  - `FileControllerTest`：参数名 bizType → basePath
  - `FileInfoRepositoryAdapterTest`：BizType → String
- **App/Admin 测试**（新增）：
  - `FileControllerTest`（App @WebMvcTest）：凭证接口参数校验、JWT 鉴权、响应格式、fileBizType 映射
  - `FileControllerTest`（Admin @WebMvcTest）：同上
