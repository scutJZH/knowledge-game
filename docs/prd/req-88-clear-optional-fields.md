# REQ-88：管理端/用户端 update 接口引入 JSON Merge Patch — 显式区分「不更新」与「清空」

> 状态：`designed`
> 创建：2026-06-18
> 前置依赖：REQ-93（图片字段统一为 FileRef，已完成）
> 关联需求：REQ-90（ImageUploadField 通用组件，已 done — REQ-88 落地后删除图片 UX 自动完整）

## 1. 概述

引入 **JSON Merge Patch（RFC 7396）** 三态语义，统一 4 个聚合根（KnowledgeCategory / IpSeries / CardTemplate / User）update 接口的「不更新 / 清空 / 更新为新值」语义。技术实现上引入 `jackson-databind-nullable`，将可清空字段（FileRef + 可选 String）包装为 `JsonNullable<T>`，必填字段（name / nickname / sortOrder / status 等）保持原类型不动。

## 2. 用户故事

- **作为管理员**，我希望编辑知识点分类时，能直接删除已上传的图标或封面图（点删除按钮后真正清空，下次打开编辑器不再显示旧图片）
- **作为管理员**，我希望编辑 IP 系列、卡牌模板时能清空可选字段（描述、颜色等）
- **作为用户**，我希望编辑个人信息时能删除头像
- **作为开发者**，我希望 update 接口语义统一、可预期：字段不存在=不更新、字段为 null=清空、字段有值=更新，且必填字段无法被前端通过传 null 绕过校验清空
- **作为架构守护者**，我希望新加聚合根时直接套用统一约定，不再出现「4 个聚合根 4 种 update 策略」的混乱

## 3. 背景

### 3.1 当前状态

4 个聚合根的 update 策略**不一致**，存在两类 bug：

| 聚合根 | 图片字段策略 | 字符串字段策略 | 后果 |
|--------|------------|--------------|------|
| KnowledgeCategory | `if (x != null) this.x = x` | 同左 | **REQ-88 原 bug**：前端传 null 也无法清空 icon / coverImage / description / color |
| CardTemplate | `if (x != null) this.x = x` | 同左 | 同上，无法清空 image / description |
| IpSeries | `this.coverImage = coverImage`（直接覆盖） | `this.description = description`（直接覆盖） | **部分更新 bug**：只改 name 也会把 coverImage / description 误清空 |
| User | `this.avatar = avatar`（直接覆盖） | `this.nickname = nickname`（直接覆盖） | **部分更新 bug**：只改 nickname 也会把 avatar 误清空（nickname 还会触发 NOT NULL 约束失败） |

前端实际调用方式也不统一：

| 文件 | 行 | 处理方式 |
|------|----|---------|
| `pages/IpSeries/index.tsx` | 124 | `coverImageFileId: values.x ?? null`（总是传字段，null 表清空） |
| `pages/KnowledgeBase/components/CategoryFormModal.tsx` | 57, 59 | `iconFileId: values.x ?? undefined`（undefined 时省略字段） |

两种前端写法 + 两种后端策略 = 4 种行为组合，每种聚合根表现都不同。

### 3.2 JSON Merge Patch 三态语义（业界标准）

HTTP PATCH + JSON Merge Patch（RFC 7396）：

| 前端传值 | JSON 序列化 | 语义 |
|---------|------------|------|
| 字段未传 / undefined | 字段不存在 | 不更新（保持原值） |
| 显式 `null` | `"field": null` | 清空字段（设为 null） |
| 具体值 | `"field": 100` | 更新为该值 |

**为何选 JSON Merge Patch：**
- axios `JSON.stringify` 默认行为：undefined 字段不序列化、null 字段保留 — 天然贴合三态
- Spring + Jackson 生态有成熟库 `jackson-databind-nullable`（OpenAPI 出品），DTO 字段包装为 `JsonNullable<T>` 自动识别
- 业界 RESTful PATCH 标准，未来对接第三方系统语义可预期

### 3.3 必填字段的约束

**核心原则：必填字段不可被清空。**

实现上：必填字段（name / nickname / sortOrder / status / code / rarity 等）**保持原 Java 类型不动**（仍是 `String name` / `Integer sortOrder` 等），沿用 `null=不更新` 旧语义，**不包装 JsonNullable**，从根本上杜绝前端通过 `null` 清空必填字段的可能性。

## 4. 设计方案

### 4.1 字段分类总表

| 聚合根 | 字段 | 类型 | 可清空 | DTO 包装 | 后端行为方法 |
|--------|------|------|--------|---------|-------------|
| KnowledgeCategory | name | String | ❌ 必填 | String（不变） | `update()` |
| KnowledgeCategory | description | String | ✅ | `JsonNullable<String>` | `clearDescription()` |
| KnowledgeCategory | color | String | ✅ | `JsonNullable<String>` | `clearColor()` |
| KnowledgeCategory | icon | FileRef | ✅ | `JsonNullable<Long>` | `updateIcon(FileRef)` + `clearIcon()` |
| KnowledgeCategory | coverImage | FileRef | ✅ | `JsonNullable<Long>` | `updateCoverImage(FileRef)` + `clearCoverImage()` |
| KnowledgeCategory | sortOrder | Integer | ❌ 必填 | Integer（不变） | `update()` |
| KnowledgeCategory | status | enum | ❌ 必填 | enum（不变） | `update()` |
| IpSeries | code | String | ❌ 必填 | String（不变） | `update()` |
| IpSeries | name | String | ❌ 必填 | String（不变） | `update()` |
| IpSeries | description | String | ✅ | `JsonNullable<String>` | `clearDescription()` |
| IpSeries | coverImage | FileRef | ✅ | `JsonNullable<Long>` | `updateCoverImage(FileRef)` + `clearCoverImage()` |
| IpSeries | status | enum | ❌ 必填 | enum（不变） | `update()` |
| CardTemplate | code | String | ❌ 必填 | String（不变） | `update()` |
| CardTemplate | name | String | ❌ 必填 | String（不变） | `update()` |
| CardTemplate | description | String | ✅ | `JsonNullable<String>` | `clearDescription()` |
| CardTemplate | image | FileRef | ✅ | `JsonNullable<Long>` | `updateImage(FileRef)` + `clearImage()` |
| CardTemplate | rarity | enum | ❌ 必填 | enum（不变） | `update()` |
| CardTemplate | status | enum | ❌ 必填 | enum（不变） | `update()` |
| User | username | String | ❌ 必填 | （不在 update 接口） | — |
| User | nickname | String | ❌ 必填 | String（不变） | `updateProfile()` |
| User | avatar | FileRef | ✅ | `JsonNullable<Long>` | `updateAvatar(FileRef)` + `clearAvatar()` |

### 4.2 整体数据流

```
┌─ frontend (axios) ─────────────────────────────────────────────────┐
│  ImageUploadField 删除按钮 → onChange(undefined)                    │
│  ProForm 表单值 undefined → axios JSON.stringify 自动省略字段        │
│  ProForm 显式 null → 序列化为 "field": null                         │
└──────────────────────────┬──────────────────────────────────────────┘
                           ↓ HTTP PATCH
┌─ admin/app Controller DTO ──────────────────────────────────────────┐
│  可清空字段：JsonNullable<T>                                       │
│    字段未传 → JsonNullable.undefined()                              │
│    "field": null → JsonNullable.of(null)                           │
│    "field": 100 → JsonNullable.of(100L)                            │
│                                                                    │
│  必填字段：原类型                                                   │
│    字段未传或 null → null（沿用旧「不更新」语义）                    │
│    有值 → 直接更新                                                  │
└──────────────────────────┬──────────────────────────────────────────┘
                           ↓
┌─ AppService ──────────────────────────────────────────────────────┐
│  applyField() 三态分派工具：                                       │
│    undefined → 跳过                                                │
│    of(null) → domain.clearXxx()                                    │
│    of(value) → verifyFileRef() + domain.updateXxx(value)           │
│                                                                    │
│  必填字段：原 if (x != null) 逻辑（透传到 update()）                │
└──────────────────────────┬──────────────────────────────────────────┘
                           ↓
┌─ Domain Entity ───────────────────────────────────────────────────┐
│  updateXxx(T value): 写入新值（拒绝 null，清空走 clear）           │
│  clearXxx(): 显式置 null                                          │
│  保留旧 update(...) 方法处理必填字段                               │
│  所有方法都更新 updatedAt                                          │
└────────────────────────────────────────────────────────────────────┘
```

### 4.3 关键不变量

- **必填字段不可清空**：必填字段不包装 JsonNullable，前端无法通过 `null` 触发清空（DTO 类型本身就阻止了）
- **FileRef 整体替换**：FileRef 字段沿用 REQ-93 的整体替换语义，updateXxx / clearXxx 互斥（不可半状态）
- **updatedAt 必更新**：每个行为方法（含 clearXxx）都更新 updatedAt
- **JsonNullable 仅在 api/dto + application 层可见**：领域层零框架依赖，AppService 负责拆包后调领域方法

### 4.4 AppService 三态分派工具

**明确决策：每个 AppService 各自内联实现，不抽取共享基类或工具类。** 详见 5.4.3 节决策说明。

```java
// 通用工具方法（每个 AppService 内联实现）
static <T> void applyField(JsonNullable<T> field, Runnable clear, Consumer<T> update) {
    if (field == null || !field.isPresent()) return;   // undefined → 跳过
    T value = field.get();
    if (value == null) clear.run();                    // of(null) → 清空
    else update.accept(value);                          // of(value) → 更新
}

// FileRef 专用（含 verifyFileRef 校验）
static void applyFileRefField(JsonNullable<Long> fileIdField, String bizType,
                              Runnable clear, Consumer<FileRef> update,
                              FileServiceClient fileServiceClient, Long currentUserId) {
    if (fileIdField == null || !fileIdField.isPresent()) return;
    Long fileId = fileIdField.get();
    if (fileId == null) {
        clear.run();
    } else {
        FileRef verified = verifyFileRef(fileServiceClient, fileId, bizType, currentUserId);
        update.accept(verified);
    }
}
```

### 4.5 领域实体行为方法模板

以 `KnowledgeCategory` 为例（其他聚合根同理）：

```java
public class KnowledgeCategory {

    // 旧 update 方法保留，仅处理必填字段（不支持清空）
    public void update(String name, Integer sortOrder) {
        if (name != null) this.name = name;
        if (sortOrder != null) this.sortOrder = sortOrder;
        this.updatedAt = LocalDateTime.now();
    }

    // 可清空 String：description
    public void updateDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("description 清空请用 clearDescription()");
        }
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public void clearDescription() {
        this.description = null;
        this.updatedAt = LocalDateTime.now();
    }

    // 可清空 String：color
    public void updateColor(String color) {
        if (color == null) {
            throw new IllegalArgumentException("color 清空请用 clearColor()");
        }
        this.color = color;
        this.updatedAt = LocalDateTime.now();
    }

    public void clearColor() {
        this.color = null;
        this.updatedAt = LocalDateTime.now();
    }

    // 可清空 FileRef：icon
    public void updateIcon(FileRef icon) {
        if (icon == null) {
            throw new IllegalArgumentException("icon 清空请用 clearIcon()");
        }
        this.icon = icon;
        this.updatedAt = LocalDateTime.now();
    }

    public void clearIcon() {
        this.icon = null;
        this.updatedAt = LocalDateTime.now();
    }

    // 可清空 FileRef：coverImage
    public void updateCoverImage(FileRef coverImage) {
        if (coverImage == null) {
            throw new IllegalArgumentException("coverImage 清空请用 clearCoverImage()");
        }
        this.coverImage = coverImage;
        this.updatedAt = LocalDateTime.now();
    }

    public void clearCoverImage() {
        this.coverImage = null;
        this.updatedAt = LocalDateTime.now();
    }

    // ... 其他方法（create/reconstruct/moveTo/deactivate）不变
}
```

**核心原则：** 旧 `update()` 方法只处理必填字段（name/sortOrder 等不支持清空）。所有可清空字段（含 String 类型的 description / color，含 FileRef 类型的 icon / coverImage 等）一律独立提供 `updateXxx(value)` + `clearXxx()` 两个行为方法，由 AppService 通过 `applyField()` 工具按 JsonNullable 三态分派。

## 5. 详细设计

### 5.1 依赖引入

**admin 模块 `pom.xml`：**
```xml
<dependency>
    <groupId>org.openapitools</groupId>
    <artifactId>jackson-databind-nullable</artifactId>
    <version>0.2.6</version>
</dependency>
```

**app 模块 `pom.xml`：** 同上（admin/app 各自引入，不通过 core 传递）

### 5.2 Jackson 配置

**admin 模块新增 `JacksonConfig`：**

位置：`backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/config/JacksonConfig.java`

```java
package com.knowledgegame.admin.config;

import org.openapitools.jackson.databind.nullable.JsonNullableModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonNullableCustomizer() {
        return builder -> builder.modulesToInstall(new JsonNullableModule());
    }
}
```

**app 模块新增同名类**：`backend/knowledge-game-app/src/main/java/com/knowledgegame/app/config/JacksonConfig.java`（代码相同）

### 5.3 admin 模块 DTO 改造

#### 5.3.1 `UpdateKnowledgeCategoryRequest`

```java
@Getter @Setter
public class UpdateKnowledgeCategoryRequest {

    @Size(min = 2, max = 50)
    private String name;                                          // 必填，不包装

    private JsonNullable<String> description = JsonNullable.undefined();   // 可清空
    private JsonNullable<Long> iconFileId = JsonNullable.undefined();      // 可清空 FileRef
    private JsonNullable<String> color = JsonNullable.undefined();         // 可清空
    private JsonNullable<Long> coverImageFileId = JsonNullable.undefined();// 可清空 FileRef

    private Integer sortOrder;                                    // 必填，不包装
}
```

#### 5.3.2 `UpdateIpSeriesRequest`

```java
@Getter @Setter
public class UpdateIpSeriesRequest {

    @Size(min = 2, max = 30) private String code;
    @Size(min = 2, max = 50) private String name;

    private JsonNullable<String> description = JsonNullable.undefined();
    private JsonNullable<Long> coverImageFileId = JsonNullable.undefined();

    private IpSeriesStatus status;
}
```

#### 5.3.3 `UpdateCardTemplateRequest`

```java
@Getter @Setter
public class UpdateCardTemplateRequest {

    @Size(min = 2, max = 50) private String code;
    @Size(min = 1, max = 50) private String name;

    private CardRarity rarity;
    private JsonNullable<String> description = JsonNullable.undefined();
    private CardTemplateStatus status;

    private JsonNullable<Long> imageFileId = JsonNullable.undefined();
}
```

#### 5.3.4 app 模块 `UpdateUserRequest`

```java
@Getter @Setter
public class UpdateUserRequest {

    @Size(max = 50)
    private String nickname;                                      // 必填，不包装

    private JsonNullable<Long> avatarFileId = JsonNullable.undefined();
}
```

### 5.4 AppService 改造

以 `KnowledgeCategoryAppService.update` 为例：

```java
@Transactional
public KnowledgeCategoryResponse update(Long id, UpdateKnowledgeCategoryRequest req) {
    KnowledgeCategory category = categoryRepositoryPort.findById(id)
            .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));

    if (req.getName() != null && !req.getName().equals(category.getName())) {
        if (categoryRepositoryPort.existsByNameAndParentId(req.getName(), category.getParentId())) {
            throw new BusinessException("同一父级下已存在同名分类: " + req.getName());
        }
    }

    // 必填字段：原 if-null 语义
    category.update(req.getName(), req.getSortOrder());

    // 可清空 String：description / color
    applyField(req.getDescription(), category::clearDescription, category::updateDescription);
    applyField(req.getColor(), category::clearColor, category::updateColor);

    // 可清空 FileRef：icon / coverImage
    applyFileRefField(req.getIconFileId(), "CATEGORY_ICON",
            category::clearIcon, category::updateIcon);
    applyFileRefField(req.getCoverImageFileId(), "CATEGORY_COVER",
            category::clearCoverImage, category::updateCoverImage);

    KnowledgeCategory saved = categoryRepositoryPort.save(category);
    return KnowledgeCategoryAssembler.INSTANCE.toResponse(saved);
}
```

**其他 AppService（IpSeries / CardTemplate / User）改造模式相同。**

#### 5.4.1 CardTemplateAppService.update 示例（含 rarity 必填枚举字段）

```java
@Transactional
public CardTemplateResponse update(Long id, UpdateCardTemplateRequest req) {
    CardTemplate template = cardTemplateRepositoryPort.findById(id)
            .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));

    // 必填字段：原 if-null 语义（含 rarity / status 枚举）
    template.update(req.getCode(), req.getName(), req.getRarity(), req.getStatus());

    // 可清空 String：description
    applyField(req.getDescription(), template::clearDescription, template::updateDescription);

    // 可清空 FileRef：image
    applyFileRefField(req.getImageFileId(), "CARD_TEMPLATE",
            template::clearImage, template::updateImage);

    CardTemplate saved = cardTemplateRepositoryPort.save(template);
    return CardTemplateAssembler.INSTANCE.toResponse(saved);
}
```

#### 5.4.2 AppService 签名变更说明（重要）

**所有 update 方法的签名从「逐字段接收」改为「直接接收 Request DTO」**，**且方法名统一为 `update`（无实体后缀，与 AppService 类名表达实体类型互补）**：

| AppService | 旧方法名 + 签名 | 新方法名 + 签名 |
|-----------|---------------|----------------|
| KnowledgeCategoryAppService | `update(Long, String name, String desc, Long iconId, String color, Long coverId, Integer sort)` | `update(Long, UpdateKnowledgeCategoryRequest req)`（方法名保持） |
| IpSeriesAppService | `updateIpSeries(Long, String code, String name, String desc, Long coverId, IpSeriesStatus status)` | `update(Long, UpdateIpSeriesRequest req)`（方法名重命名：updateIpSeries → update） |
| CardTemplateAppService | `updateCardTemplate(Long, String code, String name, CardRarity, String desc, CardTemplateStatus, Long imageId)` | `update(Long, UpdateCardTemplateRequest req)`（方法名重命名：updateCardTemplate → update） |
| UserAppService | `updateUser(Long, String nickname, Long avatarId)` | `update(Long, UpdateUserRequest req)`（方法名重命名：updateUser → update） |

**方法名统一为 `update` 的理由：**
- AppService 类名已表达实体类型（如 `IpSeriesAppService`），方法名加后缀（`updateIpSeries`）是冗余
- 4 个 AppService 命名一致，调用方代码模式统一：`appService.update(id, request)`
- KnowledgeCategoryAppService.update 已是此模式，本次让其他 3 个 AppService 对齐

**原因：** 可清空字段已包装为 `JsonNullable<T>`，AppService 必须接收整个 DTO 才能解析三态；逐字段拆包会丢失「字段是否存在」的语义信息。

**Controller 同步改造（必做）：** 见 6.2 节，4 个 Controller 的 update 方法体改为直接透传 DTO，且调用方的方法名同步从 `updateXxx` 改为 `update`。

#### 5.4.3 applyField / applyFileRefField 内联策略（决策）

**明确决策：每个 AppService 各自内联实现 `applyField` 和 `applyFileRefField`，不抽取共享基类或工具类。**

理由：
- 4 个 AppService 分布在 admin 和 app 两个独立模块，无法跨模块共享 application 层代码（admin 和 app 互不依赖）
- core 模块（DDD domain 层）禁止依赖 Jackson（JsonNullable 是 Jackson 包装类型），不能放共享组件
- 每个方法代码量 < 10 行，4 处内联总成本 < 引入新共享模块的依赖传递成本

**实施约定：** 在 CLAUDE.md 的 Component Development Rules 中新增条目：

> `applyField(JsonNullable)` / `applyFileRefField(JsonNullable)` 工具方法在每个 AppService 内联实现，不可放入 core 模块或共享 component（JsonNullable 是 Jackson 框架类型，禁止污染 DDD domain 层）

### 5.5 领域实体改造

#### 5.5.1 `KnowledgeCategory`

**新增方法：**
- `updateDescription(String)` / `clearDescription()`
- `updateColor(String)` / `clearColor()`
- `updateIcon(FileRef)` / `clearIcon()`
- `updateCoverImage(FileRef)` / `clearCoverImage()`

**旧 `update(...)` 方法签名简化：** `update(String name, String description, FileRef icon, String color, FileRef coverImage, Integer sortOrder)` → `update(String name, Integer sortOrder)`

#### 5.5.2 `IpSeries`

**新增方法：**
- `updateDescription(String)` / `clearDescription()`
- `updateCoverImage(FileRef)` / `clearCoverImage()`

**旧 `update(...)` 方法签名简化：** `update(String code, String name, String description, FileRef coverImage, IpSeriesStatus status)` → `update(String code, String name, IpSeriesStatus status)`

#### 5.5.3 `CardTemplate`

**新增方法：**
- `updateDescription(String)` / `clearDescription()`
- `updateImage(FileRef)` / `clearImage()`

**旧 `update(...)` 方法签名简化：** `update(String code, String name, CardRarity rarity, String description, CardTemplateStatus status, FileRef image)` → `update(String code, String name, CardRarity rarity, CardTemplateStatus status)`

#### 5.5.4 `User`

**新增方法：**
- `updateAvatar(FileRef)` / `clearAvatar()`

**旧 `updateProfile(String nickname, FileRef avatar)` 签名简化为 `updateProfile(String nickname)`。**

**关键点：** User.nickname 数据库列为 `NOT NULL`，旧代码 `this.nickname = nickname` 直接赋值时若 nickname 为 null 会触发 DB 约束失败。新方法必须显式 if-null 守卫：

```java
public class User {

    // 仅处理 nickname（必填，if-null 不更新）
    public void updateProfile(String nickname) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        this.updatedAt = LocalDateTime.now();
    }

    // 可清空 FileRef：avatar
    public void updateAvatar(FileRef avatar) {
        if (avatar == null) {
            throw new IllegalArgumentException("avatar 清空请用 clearAvatar()");
        }
        this.avatar = avatar;
        this.updatedAt = LocalDateTime.now();
    }

    public void clearAvatar() {
        this.avatar = null;
        this.updatedAt = LocalDateTime.now();
    }

    // ... create / reconstruct / 其他方法不变
}
```

**测试关键用例：** `updateProfile(null)` 必须保持 nickname 不变且不抛异常（验证 NOT NULL 保护）。

#### 5.5.5 现有调用方影响

`KnowledgeCategoryAppService.batchSort` 和 `move` 方法内部调用 `category.update(null, null, null, null, null, sortOrder)`（6 参数版本），改造后需同步更新：

```java
// 旧
category.update(null, null, null, null, null, newSortOrder);
// 新
category.update(null, newSortOrder);
```

其他 AppService 内部若直接调用旧 `update(...)` 签名（如 batch 操作中复用 update 方法），同样需同步调整。REQ-88 实施时全模块 grep `\.update\(` 和 `\.updateProfile\(` 排查所有调用方。

### 5.6 前端改造

#### 5.6.1 ImageUploadField 协议不变

组件已支持 `value?: number` + `onChange?: (fileId: number | undefined) => void`，删除按钮触发 `onChange(undefined)`。**本次不动 ImageUploadField。**

#### 5.6.2 业务页面提交逻辑改造

**通用模式：** 父组件根据「字段是否有变化」决定传 `undefined`（不发送）还是 `null`（清空）还是 `number`（更新）。

以 `CategoryFormModal.tsx` 为例：

```typescript
const onFinish = async (values: CategoryFormData) => {
  const payload: UpdateKnowledgeCategoryRequest = {
    name: values.name,
  };

  // description：与原值对比，有变化才传
  const descChanged = values.description !== (editingCategory?.description ?? '');
  if (descChanged) {
    payload.description = values.description || null;  // 空字符串 → null 清空
  }

  // iconFileId：有变化才传
  if (values.iconFileId !== editingCategory?.iconFileId) {
    payload.iconFileId = values.iconFileId ?? null;  // undefined → null 清空
  }

  // coverImageFileId、color、sortOrder 同理
  // ...

  await update(editingCategory.id, payload);
};
```

**关键点：**
- 没变化的字段**完全不传**（axios 默认 undefined → 不序列化）
- 删除时**显式传 null**
- 新值正常传 number / string

#### 5.6.3 受影响前端文件

| 文件 | 改造 |
|------|------|
| `frontend/admin/src/pages/KnowledgeBase/components/CategoryFormModal.tsx` | onFinish 逻辑改为「按字段对比」模式 |
| `frontend/admin/src/pages/IpSeries/index.tsx` | handleFinish 中 coverImageFileId 处理改为对比模式 |
| `frontend/admin/src/pages/CardTemplate/index.tsx` | imageFileId / description 同理 |
| `frontend/user/src/pages/Profile.tsx`（用户端） | **预留**，当前 `frontend/user/` 目录未初始化。未来开发时按本次 PRD 的 onFinish 字段对比模式实现 |
| `frontend/admin/src/services/knowledgeBase.ts`（或 CategoryFormModal 内联类型） | Update 类型字段改为可选（含 null） |
| `frontend/admin/src/services/ipSeries.ts` | 同上 |
| `frontend/admin/src/services/cardTemplate.ts` | 同上 |

#### 5.6.4 TypeScript 类型变更

`UpdateKnowledgeCategoryRequest` 类型从：
```typescript
{ iconFileId?: number; description?: string; ... }
```
改为：
```typescript
{ iconFileId?: number | null; description?: string | null; ... }
```

允许 null 值传入 axios，axios 会序列化为 `"field": null`。

### 5.7 测试覆盖

#### 5.7.1 领域层单元测试

每个聚合根新增以下用例（以 KnowledgeCategory 为例）：

| 用例 | 覆盖点 |
|------|--------|
| `updateIcon(FileRef.of(1L, "url"))` | icon 字段正确写入，updatedAt 更新 |
| `updateIcon(null)` | 抛 `IllegalArgumentException` |
| `clearIcon()` | icon 设为 null，updatedAt 更新 |
| `updateCoverImage(ref)` / `updateCoverImage(null)` / `clearCoverImage()` | 同上模式 |
| `updateDescription("xxx")` | description 正常写入 |
| `updateDescription(null)` | 抛 `IllegalArgumentException` |
| `clearDescription()` | description 设为 null |
| `updateColor("#FF5500")` / `updateColor(null)` / `clearColor()` | 同上模式 |

其他聚合根（IpSeries / CardTemplate / User）按各自字段表展开。

#### 5.7.2 AppService 单元测试

`KnowledgeCategoryAppServiceTest` 新增：

| 用例 | 输入 | 期望 |
|------|------|------|
| 全部字段 undefined | 请求体 `{}` | 不调任何 update/clear 方法，repo.save 调用一次 |
| 单字段清空 icon | `{iconFileId: null}` | 调用 `clearIcon()`，其他不动 |
| 单字段更新 icon | `{iconFileId: 100}` | verifyFileRef + 调用 `updateIcon(FileRef)` |
| 多字段同时操作 | `{iconFileId: null, coverImageFileId: 200, name: "新名"}` | 顺序调用 clearIcon、verifyFileRef+updateCoverImage、update(name) |
| 必填字段 null | `{name: null}` | name 不更新（沿用旧语义） |
| verifyFileRef 失败 | `{iconFileId: 999}`（文件不存在） | 抛 BusinessException，不调用领域方法 |

#### 5.7.3 Controller 集成测试（@WebMvcTest）

`KnowledgeCategoryControllerTest` 新增 Jackson 反序列化用例：

| 用例 | 请求体 | 期望 DTO 状态 |
|------|--------|--------------|
| 缺字段 | `{"name":"x"}` | iconFileId = undefined() |
| null 字段 | `{"iconFileId":null}` | iconFileId = of(null) |
| 数值 | `{"iconFileId":100}` | iconFileId = of(100L) |
| 混合 | `{"name":"x","iconFileId":null,"coverImageFileId":200}` | name 正常、iconFileId = of(null)、coverImageFileId = of(200L) |

#### 5.7.4 前端单元测试

`CategoryFormModal.test.tsx` 新增：

| 用例 | 输入 | 期望 payload |
|------|------|-------------|
| 编辑模式不修改任何字段直接保存 | formValues 与原值相同 | payload 仅含 name（必填），其他字段不出现 |
| 编辑模式删除 icon | formValues.iconFileId = undefined（原值为 100） | payload.iconFileId = null |
| 编辑模式上传新 icon | formValues.iconFileId = 200（原值为 100） | payload.iconFileId = 200 |
| 编辑模式同时改 name + 删 icon | formValues.name="新", iconFileId=undefined | payload = {name:"新", iconFileId:null} |

#### 5.7.5 手动验证

1. **管理端 — 知识库分类**
   - 编辑现有分类，删除 icon 图片，保存 → 刷新页面 icon 不再显示
   - 编辑现有分类，删除 coverImage 图片，保存 → 刷新不再显示
   - 编辑现有分类，清空 description → 详情页描述区消失
   - 编辑现有分类，清空 color → 颜色显示消失
   - 编辑现有分类，只改 name → 其他字段保持不变
2. **管理端 — IP 系列**
   - 编辑现有 IP 系列，删除封面图 → 刷新不再显示
   - 编辑现有 IP 系列，清空 description → 详情页描述消失
3. **管理端 — 卡牌模板**
   - 编辑现有卡牌，删除卡面图 → 刷新不再显示
   - 编辑现有卡牌，清空 description → 详情消失
4. **用户端 — 个人信息**
   - 编辑个人信息，删除头像 → 头像恢复默认
   - 编辑个人信息，只改 nickname → 头像保持不变
5. **必填字段保护**
   - 通过 curl/fiddler 直接构造 `{"name": null}` 请求 → 后端不更新 name（保持原值），不抛异常
   - 通过 curl 构造 `{}`（空请求） → 后端不更新任何字段，返回 200

## 6. 改动范围汇总

### 6.1 新增

| 文件 | 说明 |
|------|------|
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/config/JacksonConfig.java` | 注册 JsonNullableModule |
| `backend/knowledge-game-app/src/main/java/com/knowledgegame/app/config/JacksonConfig.java` | 同上 |
| `backend/knowledge-game-admin/src/test/java/com/knowledgegame/admin/config/JacksonConfigTest.java` | 单元测试（验证 JsonNullable 反序列化） |
| `backend/knowledge-game-app/src/test/java/com/knowledgegame/app/config/JacksonConfigTest.java` | 同上 |

### 6.2 修改 — 后端

| 模块 | 文件 | 改造 |
|------|------|------|
| admin | `pom.xml` | 新增 jackson-databind-nullable 依赖 |
| app | `pom.xml` | 同上 |
| admin | `api/dto/request/UpdateKnowledgeCategoryRequest.java` | description/iconFileId/color/coverImageFileId 改为 JsonNullable |
| admin | `api/dto/request/UpdateIpSeriesRequest.java` | description/coverImageFileId 改为 JsonNullable |
| admin | `api/dto/request/UpdateCardTemplateRequest.java` | description/imageFileId 改为 JsonNullable |
| app | `api/dto/request/UpdateUserRequest.java` | avatarFileId 改为 JsonNullable |
| admin | `application/service/KnowledgeCategoryAppService.java` | update 方法签名改为 `update(Long, UpdateKnowledgeCategoryRequest)`；内联 applyField / applyFileRefField 分派（方法名保持） |
| admin | `application/service/IpSeriesAppService.java` | 方法名 `updateIpSeries` → `update`，签名改为 `update(Long, UpdateIpSeriesRequest)`；同上分派 |
| admin | `application/service/CardTemplateAppService.java` | 方法名 `updateCardTemplate` → `update`，签名改为 `update(Long, UpdateCardTemplateRequest)`；同上分派（含 rarity/status 必填枚举） |
| app | `application/service/UserAppService.java` | 方法名 `updateUser` → `update`，签名改为 `update(Long, UpdateUserRequest)`；update 拆为 `user.updateProfile(req.getNickname())` + applyFileRefField |
| admin | `api/controller/KnowledgeCategoryController.java` | **必做改造**：update 方法体从 `appService.update(id, request.getName(), request.getDescription(), request.getIconFileId(), request.getColor(), request.getCoverImageFileId(), request.getSortOrder())` 改为 `appService.update(id, request)`（直接透传 DTO） |
| admin | `api/controller/IpSeriesController.java` | **必做改造**：update 方法体从 `appService.updateIpSeries(id, ...)` 改为 `appService.update(id, request)` |
| admin | `api/controller/CardTemplateController.java` | **必做改造**：update 方法体从 `cardTemplateAppService.updateCardTemplate(id, request.getCode(), request.getName(), ..., request.getImageFileId())` 改为 `cardTemplateAppService.update(id, request)` |
| app | `api/controller/UserController.java` | **必做改造**：update 方法体从 `userAppService.updateUser(id, request.getNickname(), request.getAvatarFileId())` 改为 `userAppService.update(id, request)` |
| core | `domain/model/entity/KnowledgeCategory.java` | 新增 updateDescription/clearDescription/updateColor/clearColor/updateIcon/clearIcon/updateCoverImage/clearCoverImage；update 签名简化为 `update(String name, Integer sortOrder)` |
| core | `domain/model/entity/IpSeries.java` | 新增 updateDescription/clearDescription/updateCoverImage/clearCoverImage；update 签名简化为 `update(String code, String name, IpSeriesStatus status)` |
| core | `domain/model/entity/CardTemplate.java` | 新增 updateDescription/clearDescription/updateImage/clearImage；update 签名简化为 `update(String code, String name, CardRarity rarity, CardTemplateStatus status)` |
| core | `domain/model/entity/User.java` | 新增 updateAvatar/clearAvatar；updateProfile 签名简化为 `updateProfile(String nickname)` |
| admin | `application/service/*AppService.java` 内部对旧 `update()` 的调用方（batchSort/move 等） | 同步调整调用方式（去掉已删除的参数） |

### 6.3 修改 — 前端

| 文件 | 改造 |
|------|------|
| `frontend/admin/src/pages/KnowledgeBase/components/CategoryFormModal.tsx` | onFinish 改为按字段对比模式，新增 null 显式清空 |
| `frontend/admin/src/pages/IpSeries/index.tsx` | handleFinish 同上 |
| `frontend/admin/src/pages/CardTemplate/index.tsx` | 同上 |
| `frontend/admin/src/services/knowledgeBase.ts` | Update 类型字段允许 null |
| `frontend/admin/src/services/ipSeries.ts` | 同上 |
| `frontend/admin/src/services/cardTemplate.ts` | 同上 |
| `frontend/user/src/pages/Profile.tsx` 及 `frontend/user/src/services/user.ts` | **预留，当前不存在**。`frontend/user/` 目录尚未初始化（REQ-93 PRD 描述的 user 端图片改造也属于预留）。未来开发用户端时按本次 PRD 的 onFinish 字段对比模式实现 |

### 6.4 修改 — 文档

| 文件 | 改造 |
|------|------|
| `docs/requirements.md` | **PRD 审查通过后立即更新**：REQ-88 状态 `idea → designed`，挂 PRD 链接 `[req-88-clear-optional-fields.md](../docs/prd/req-88-clear-optional-fields.md)` |
| `docs/overview.md` | 同步 update 接口语义说明（增加 JsonNullable 章节） |
| `CLAUDE.md` | 新增 `Update API Null Semantics` 章节（必填字段不包装、可清空字段 JsonNullable 三态、必填字段不可清空原则、`applyField`/`applyFileRefField` 内联不抽取共享基类） |

### 6.5 修改 — 测试

| 测试类 | 改造 |
|--------|------|
| `KnowledgeCategoryTest` | 新增 updateDescription/clearDescription/updateColor/clearColor/updateIcon/clearIcon/updateCoverImage/clearCoverImage 用例（每方法覆盖写入、拒绝 null、清空三个用例） |
| `IpSeriesTest` | 新增 updateDescription/clearDescription/updateCoverImage/clearCoverImage 用例 |
| `CardTemplateTest` | 新增 updateDescription/clearDescription/updateImage/clearImage 用例 |
| `UserTest` | 新增 updateAvatar/clearAvatar 用例；`updateProfile(null)` 验证 nickname 保持不变且不抛异常 |
| `KnowledgeCategoryAppServiceTest` | 新增 applyField / applyFileRefField 三态用例 |
| `IpSeriesAppServiceTest` | 同上 |
| `CardTemplateAppServiceTest` | 同上 |
| `UserAppServiceTest` | 同上 |
| `KnowledgeCategoryControllerTest` | 新增 Jackson 反序列化三态用例（缺字段/null/数值） |
| `IpSeriesControllerTest` | 同上 |
| `CardTemplateControllerTest` | 同上 |
| `UserControllerTest` | 同上 |
| `frontend/admin/src/pages/KnowledgeBase/components/__tests__/CategoryFormModal.test.tsx` | 新增按字段对比 / 显式 null 清空用例 |
| `frontend/admin/src/pages/IpSeries/__tests__/index.test.tsx` | 同上 |
| `frontend/admin/src/pages/CardTemplate/__tests__/index.test.tsx` | 同上 |

## 7. 不在本次范围

| 项目 | 原因 |
|------|------|
| 未来的 study_group / shop_product / achievement_template 等表的 update 接口 | 当前无 PO，本次仅预留设计规范到 CLAUDE.md |
| 必填字段（如 name/sortOrder）的清空能力 | 必填字段语义上不允许清空，本次明确禁止 |
| **Create 接口 DTO**（如 `CreateKnowledgeCategoryRequest` / `CreateIpSeriesRequest` / `CreateCardTemplateRequest`） | 创建时字段缺失即表示不设置该值，本身就是「清空」语义。无三态需求，DTO 字段保持原类型不变（FileRef 字段仍是 `Long xxxFileId` 普通类型） |
| 全量 PUT 替换语义（每次 GET+PUT 整体对象） | 并发风险高、传输冗余，本次仍保持 PATCH 局部更新 |
| 历史数据迁移 | 无生产数据，dev 由 JPA ddl-auto 自动同步 |
| `frontend/user/` 用户端前端的实际开发 | 目录尚未初始化，PRD 仅预留设计规范 |

## 8. Verification Plan

### 8.1 自动化测试

详见 5.7 节。

### 8.2 验证命令

```bash
# 后端构建（在 backend/ 目录下）
mvn clean install

# 后端单测（全部模块）
mvn test

# 前端类型检查（admin — 用户端目录未初始化，跳过）
cd frontend/admin && npx tsc --noEmit

# 前端单测
cd frontend/admin && npm test
```

**测试数量目标：**
- 新增后端测试用例（预估）：领域层 ~24（4 实体 × 约 6 方法）、AppService ~16（4 AppService × 约 4 三态场景）、Controller ~12（4 Controller × 3 反序列化用例）、JacksonConfig ~4（admin/app 各 2） → 合计 **~56 个新增**
- 新增前端测试用例（预估）：3 个页面 × 约 4 场景 → 合计 **~12 个新增**
- 完成标准：mvn 全绿、tsc 零新增 error、jest 全绿

### 8.3 手动验证

详见 5.7.5 节。

## 9. 影响分析

### 9.1 受影响的现有功能

| 功能 | 影响 |
|------|------|
| KnowledgeCategory update | 修复「无法清空 icon/coverImage/description/color」bug；签名简化 |
| IpSeries update | 修复「部分更新误清空 coverImage/description」bug；签名简化 |
| CardTemplate update | 修复「无法清空 image/description」bug；签名简化 |
| User update | 修复「部分更新误清空 avatar，nickname 触发 NOT NULL 失败」bug；签名简化 |
| ImageUploadField 删除按钮 | REQ-90 已支持，REQ-88 落地后「删除后下次编辑还在」UX 瑕疵自动修复 |
| 前端 form 提交逻辑 | 需同步改造为「按字段对比」模式（4 个页面） |

### 9.2 与 REQ-90 / REQ-93 的关系

```
REQ-90（已 done）     ImageUploadField 抽象，删除按钮触发 onChange(undefined)
   ↓ 依赖
REQ-93（已 done）     图片字段统一为 FileRef{fileId, url}，Converter 显式赋值
   ↓ 依赖
REQ-88（本次）         update 接口引入 JsonNullable 三态语义
```

**对 REQ-90 的影响：** ImageUploadField 协议不动，但父组件（CategoryFormModal/IpSeries/CardTemplate）的 onFinish 逻辑需改造。

**对 REQ-93 的影响：** FileRef 值对象不动，领域行为方法（updateXxx + clearXxx）作为 REQ-93 整体替换语义的最终落地。

### 9.3 兼容性

- **API 兼容性**：Update DTO 字段类型变化（破坏式），所有调用方都是内部前端，可控
- **数据库兼容性**：无 schema 变更
- **Jackson 序列化兼容性**：JsonNullableModule 注册后，已不传字段的旧行为（undefined）→ 与新的 undefined 等价；显式 null 行为新增
- **前端兼容性**：旧前端代码（不显式传 null）→ 字段省略 → undefined → 不更新（与旧行为等价）

## 10. 回滚标准

- 移除 `JacksonConfig.java`（admin/app 各自）
- 移除 pom.xml 中的 `jackson-databind-nullable` 依赖
- 还原 4 个 Update Request DTO 字段类型（JsonNullable → Long/String）
- 还原 4 个领域实体的 update 方法签名（恢复多参数版本）
- 删除新增的 updateXxx / clearXxx 方法
- 还原 AppService.update 调用方式
- 还原前端 onFinish 提交逻辑
- 删除 CLAUDE.md 中 `Update API Null Semantics` 章节

## 11. 后续依赖

| 后续需求 | 影响 |
|---------|------|
| 未来表（study_group / shop_product / achievement_template 等）的 update 接口 | 必须遵循 CLAUDE.md 的 `Update API Null Semantics` 章节，必填字段不包装、可清空字段 JsonNullable |
| 全量 PUT 语义（如果未来需要） | 在 PATCH 之外新增 PUT 端点，DTO 字段全必填，覆盖式更新 |
