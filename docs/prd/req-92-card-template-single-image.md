# REQ-92 卡牌模板图片模型简化 — 单图替代多星级图

> 日期：2026-06-14
> 状态：designed
> 影响需求：REQ-17（卡牌管理端 CRUD API）、REQ-45（卡牌管理页）
> 关联需求：REQ-93（图片字段统一以 file_id 为关联 key，独立需求）

## 一、背景与目标

### 1.1 背景

当前 `card_template` 与 `card_star_image` 是 1:N 关系，每张卡牌模板可挂载 1~5 张星级图片（唯一约束 `card_template_id + star_level`）。该设计将"星级"概念耦合在卡牌模板上，导致：

- 模型复杂：领域聚合根 `CardTemplate.starImages` 是 `List<CardStarImage>`，Converter 需手动处理双向关联与子集合原地更新
- 业务语义错配：星级本质是**用户在群组内收集维度的属性**（`user_card.star_level`），而非模板的属性；模板的图片数量决定用户星级上限的规则不合理
- 前端表单复杂：管理端需要 5 个星级图片上传组件 + 两步提交逻辑（PUT 基础字段 + 5 次 POST 单图接口）

### 1.2 产品决策（同步写入 knowledge-game.md）

经讨论明确以下产品方向：

- **管理端卡片模板**：纯粹只管理卡片基础信息 + 1 张卡面图
- **星级归属**：星级是用户在群组内收集的属性（`user_card.star_level`），不属于模板
- **升星本质**：升级可获得的奖励档位（重复获得卡牌可升星）
- **奖励配置归属**：群组管理员配置各星级对应奖励
- **星级展示**：用户端在收藏列表/图鉴中按 `user_card.star_level` 给同一张卡面图叠加星级边框/文字区分
- **星级提升规则与可获得奖励档位**：本 PRD 不实施，留给后续需求定义

### 1.3 目标

- 简化卡牌模板为单图模型
- 移除 `CardStarImage` 概念（领域 VO、PO、Repository、DTO、API）
- 同步更新设计文档反映新决策
- 不影响商城商品图、奖励兑换、保底机制等已落地功能

### 1.4 非目标

- ❌ 不实施"星级提升规则"和"群组配置奖励档位"业务逻辑（留给后续需求）
- ❌ 不统一图片字段为 file_id（独立需求 REQ-93）
- ❌ 不改 shop_product 表结构（商城商品仍按 `(card_template_id, star_level)` 唯一约束）
- ❌ 不改 reward_exchange_record 表结构（兑换记录仍记录兑换时星级）

## 二、范围

### 2.1 In Scope（本次实施）

| 层 | 改造内容 |
|---|---|
| 数据库 | `card_template` 加 `image_url` 列；`card_star_image` 表 DROP |
| 领域层（core） | 删除 `CardStarImage` VO、`CardStarImagePO`；`CardTemplate` 聚合根字段与方法改造；`CardTemplateConverter`、`CardTemplatePO` 同步瘦身 |
| Admin API | DTO/Controller/AppService 改造；删除 `POST /star-images` 接口 |
| Admin 前端 | 表单 5 图改 1 图；删除 `StarImageUpload` 组件；改用 `ImageUploadField` |
| 测试 | 5 个测试类同步改造 |
| 文档 | `knowledge-game.md`、`card-system-data-model.md`、`overview.md` 同步；REQ-17/45 PRD 加 deprecated 标注 |

### 2.2 Out of Scope

- REQ-93（图片字段统一为 file_id）独立处理
- 星级提升/奖励档位业务规则后续需求处理
- 用户端代码改造（用户端不依赖 `card_star_image`，无改动）
- `shop_product`、`reward_exchange_record`、`user_card.star_level` 等字段保持不变

## 三、详细设计

### 3.1 数据库变更

#### 3.1.1 `card_template` 表

新增列：

```sql
ALTER TABLE card_template ADD COLUMN image_url VARCHAR(500);
```

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| image_url | VARCHAR(500) | NULLABLE | 卡面图 URL |

dev 环境由 JPA `ddl-auto: update` 自动同步。

#### 3.1.2 `card_star_image` 表

```sql
DROP TABLE card_star_image;
```

dev 环境由 Hibernate 自动检测并删除（`@OneToMany` 关系移除后）。如未自动 DROP，人工执行 SQL。

#### 3.1.3 `user_card` 表

无 schema 变更（`display_image_url` 字段仅存在于文档中，代码层未实现）。

文档同步删除该字段定义。

### 3.2 领域层（knowledge-game-core）

#### 3.2.1 删除的类

- `com.knowledgegame.core.domain.model.vo.CardStarImage`
- `com.knowledgegame.core.infrastructure.db.entity.CardStarImagePO`
- 相关 JPA Repository（如存在 `CardStarImageRepository`）

#### 3.2.2 `CardTemplate` 聚合根改造

**字段：**

```java
// 删除
private List<CardStarImage> starImages;

// 新增
private String imageUrl;
```

**方法签名：**

```java
// create：去掉 starImages，加 imageUrl
public static CardTemplate create(Long ipSeriesId, String code, String name,
                                  CardRarity rarity, String description,
                                  CardTemplateStatus status, String imageUrl);

// reconstruct：同样改造
public static CardTemplate reconstruct(Long id, Long ipSeriesId, String code, String name,
                                       CardRarity rarity, String description,
                                       CardTemplateStatus status, String imageUrl,
                                       LocalDateTime createdAt, LocalDateTime updatedAt);

// update：合并 imageUrl
public void update(String code, String name, CardRarity rarity,
                   String description, CardTemplateStatus status, String imageUrl);
                   // imageUrl 为 null 时跳过（沿用 NullValuePropertyMappingStrategy.IGNORE 语义）
```

**删除的方法：**

- `replaceStarImages(List<CardStarImage>)`
- `addOrUpdateStarImage(CardStarImage)`
- 静态常量 `DEFAULT_IMAGE_URL`（不再需要默认图片兜底）

**默认值规则变更：**

原 `create()` 在 `starImages` 为空时自动生成 1 星默认图片（`DEFAULT_IMAGE_URL`）。
新设计中 `imageUrl` 允许为 null（管理端允许暂存无图的模板）。

#### 3.2.3 `CardTemplateDomainService`

`validateAndCreate()` 签名同步：去掉 `List<CardStarImage> starImages`，加 `String imageUrl`。

#### 3.2.4 `CardTemplatePO`

```java
// 删除
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true,
        mappedBy = "cardTemplate", fetch = FetchType.LAZY)
@Builder.Default
private List<CardStarImagePO> starImages = new ArrayList<>();

// 新增
@Column(name = "image_url", length = 500)
private String imageUrl;
```

#### 3.2.5 `CardTemplateConverter`

```java
// toDomain：删除 starImages 嵌套循环，新增 imageUrl 字段映射
default CardTemplate toDomain(CardTemplatePO po) {
    // ...
    return CardTemplate.reconstruct(
            po.getId(), po.getIpSeriesId(), po.getCode(), po.getName(),
            po.getRarity(), po.getDescription(), po.getStatus(),
            po.getImageUrl(),  // ← 新参数
            po.getCreatedAt(), po.getUpdatedAt()
    );
}

// toPO：删除 starImages 处理，新增 imageUrl
default CardTemplatePO toPO(CardTemplate template) {
    return CardTemplatePO.builder()
            // ...
            .imageUrl(template.getImageUrl())  // ← 新增
            .build();
}

// updatePO：删除子集合原地更新逻辑，新增 imageUrl 单字段
default void updatePO(@MappingTarget CardTemplatePO po, CardTemplate template) {
    // ...
    // imageUrl 在 domain 层 update() 已做 null 跳过，此处直接写入当前值
    po.setImageUrl(template.getImageUrl());
}
```

**注意：** `CardTemplate.update()` 在 imageUrl==null 时跳过更新（沿用与 code/name 等基础字段相同的"null 表示不更新"语义）。Converter 的 `updatePO` 直接把领域层产出的当前 imageUrl 写入 PO，不再单独判空——领域层已保证传给 Converter 的值是期望值。这与现有 `NullValuePropertyMappingStrategy.IGNORE` 不矛盾，因为 imageUrl 不再走 MapStruct 自动映射，而是显式赋值。

### 3.3 Admin API 层

#### 3.3.1 DTO 变更

| 类 | 改造 |
|---|---|
| `CreateCardTemplateRequest` | 删除 `List<StarImageRequest> starImages`；新增 `@Size(max=500) String imageUrl` |
| `UpdateCardTemplateRequest` | 新增 `String imageUrl`（可选，null 表示不更新） |
| `CardTemplateResponse` | 删除 `List<StarImageResponse> starImages`；新增 `String imageUrl` |
| `StarImageRequest` | **删除整个类** |
| `StarImageResponse` | **删除整个类** |
| `StarImageCommand`（application 层） | **删除整个类** |

#### 3.3.2 Controller

- 删除 `POST /api/admin/card-templates/{id}/star-images` 接口（对应 `addOrUpdateStarImage` 方法）
- `POST /api/admin/card-templates` 的请求体已包含 `imageUrl`
- `PUT /api/admin/card-templates/{id}` 的请求体已包含 `imageUrl`

#### 3.3.3 AppService

```java
// createCardTemplate：去掉 starImageCommands，加 imageUrl
@Transactional
public CardTemplateResponse createCardTemplate(Long ipSeriesId, String code, String name,
                                               CardRarity rarity, String description,
                                               CardTemplateStatus status, String imageUrl);

// updateCardTemplate：加 imageUrl 参数（合并到一次 PUT）
@Transactional
public CardTemplateResponse updateCardTemplate(Long id, String code, String name,
                                               CardRarity rarity, String description,
                                               CardTemplateStatus status, String imageUrl);

// 删除方法
public CardTemplateResponse addOrUpdateStarImage(Long id, int starLevel, String imageUrl);
```

#### 3.3.4 Assembler

- 删除 `StarImageResponse toStarImageResponse(CardStarImage img)` 方法

### 3.4 Admin 前端

#### 3.4.1 `services/cardTemplate.ts`

```typescript
// 类型变更
export interface CardTemplateResponse {
  // ...
  imageUrl?: string;       // ← 替代 starImages
  // starImages 字段删除
}

export interface CreateCardTemplateRequest {
  // ...
  imageUrl?: string;       // ← 替代 starImages
}

export interface UpdateCardTemplateRequest {
  // ...
  imageUrl?: string;       // ← 新增
}

// 删除
export interface StarImageRequest { ... }
export interface StarImageResponse { ... }
export interface AddStarImageRequest { ... }
export async function addOrUpdateStarImage(...) { ... }
```

#### 3.4.2 `pages/CardTemplate/index.tsx`

**表单值类型：**

```typescript
// 删除
type CardTemplateFormValues = UpdateCardTemplateRequest & {
  ipSeriesId?: number;
  starImage_1?: string;
  starImage_2?: string;
  starImage_3?: string;
  starImage_4?: string;
  starImage_5?: string;
};

// 改为
type CardTemplateFormValues = UpdateCardTemplateRequest & {
  ipSeriesId?: number;
  imageUrl?: string;
};
```

**删除：**

- `buildStarImageMap()` 工具函数
- `initialStarImagesRef` ref
- `StarImageUpload` import
- 编辑模式下的"两步提交"逻辑（PUT 基础字段 + 5 次 POST star-images）
- 创建模式下的"5 张图收集"逻辑

**`handleFinish()` 简化：**

```typescript
const handleFinish = async (values: CardTemplateFormValues) => {
  const { ipSeriesId, ...payload } = values;
  if (editingRecord) {
    await updateCardTemplate(editingRecord.id, payload as UpdateCardTemplateRequest);
  } else {
    await createCardTemplate({ ...payload, ipSeriesId } as CreateCardTemplateRequest);
  }
  // ...
};
```

**`buildInitialValues()` 简化：**

```typescript
const buildInitialValues = (): CardTemplateFormValues => {
  if (!editingRecord) return { status: 'ACTIVE' };
  return {
    ipSeriesId: editingRecord.ipSeriesId,
    code: editingRecord.code,
    name: editingRecord.name,
    rarity: editingRecord.rarity,
    description: editingRecord.description,
    status: editingRecord.status,
    imageUrl: editingRecord.imageUrl,
  };
};
```

**表单 UI：**

将原 `ProForm.Group title="星级图片"` 整组（5 个 `StarImageUpload`）替换为单个 `ImageUploadField`：

```tsx
<ProForm.Item name="imageUrl" label="卡面图">
  <ImageUploadField bizType="CARD_TEMPLATE" placeholder="上传卡面图" />
</ProForm.Item>
```

**bizType 说明：** 复用已注册的 `CARD_TEMPLATE`（FilePathMapping 已映射为 `card-template` 路径，FilePathMapping.java:19）。原 REQ-45 的星级图片也使用此 bizType，本次需求将其语义收敛为"卡牌模板单图"，无需新增 FilePathMapping 项。

#### 3.4.3 删除的文件

- `pages/CardTemplate/components/StarImageUpload.tsx`
- `pages/CardTemplate/__tests__/StarImageUpload.test.tsx`

### 3.5 文档变更

#### 3.5.1 `knowledge-game.md`

| 章节 | 改动 |
|---|---|
| 4.6 升星规则 | 删除"每张卡牌的星级上限由其卡牌模板拥有的星级图片数量决定"段落；写入新决策："**卡片模板仅维护单张卡面图，星级是用户在群组内收集维度的属性（user_card.star_level）；不同星级通过前端叠加星级边框/文字区分，不通过不同图片；星级提升规则与可获得奖励档位由后续需求定义**" |
| 4.6 卡牌展示 | 删除"用户可编辑卡牌展示图片，允许选择 ≤ 当前星级的图片"；改为"**收藏列表/图鉴中**所有星级用同一张卡面图，通过前端叠加星级边框/文字区分用户当前持有星级" |
| 4.6 实体奖励兑换 | 无改动（兑换机制本身不变） |
| 8.2 奖励商品映射 | 删除"星级数量由卡牌模板的星级图片决定，无固定上限" |
| 9.2 卡片商品展示 | **保留原文**——详情页查看各星级对应奖励商品（shop_product.product_image_url）的功能不变，与卡片模板图独立 |

#### 3.5.2 `card-system-data-model.md`

| 位置 | 改动 |
|---|---|
| 核心设计决策表第 2 行 | "三级结构 IP→卡牌模板→星级图片" → "二级结构 IP→卡牌模板（单图）" |
| 核心设计决策表"卡牌星级"行 | 标注"星级规则改造，新规则待后续需求定义" |
| 表 3 (card_template) | 新增 `image_url VARCHAR(500)` 列说明 |
| 表 4 (card_star_image) | **整表删除** |
| 表 12 (user_card) | 删除 `display_image_url` 列；删除"用户可切换为 ≤ 当前星级的任意星级图片"业务规则 |
| 实体关系图 | 删除 `card_template (1) ──→ (N) card_star_image` 连线 |
| 抽卡流程"6.b.保留"分支 | 删除"模板有 (N+1) 星图片"判断逻辑（保留升级/兑换语义，新规则待后续需求） |

#### 3.5.3 `docs/prd/req-17-card-template-admin.md`

文件头增加标注：

```markdown
> ⚠ 本 PRD 已被 REQ-92 部分废弃（card_star_image 模型简化为单图），
> 卡牌模板相关 API/数据结构以最新 overview.md 和 req-92 PRD 为准。
```

#### 3.5.4 `docs/prd/req-45-card-template-admin-page.md`

同上文件头标注，指向 REQ-92。

#### 3.5.5 `docs/overview.md`

如涉及"卡牌模板拥有多张星级图片"等表述，同步更新为单图模型描述。

#### 3.5.6 `docs/requirements.md`

REQ-92 状态：`idea → designed`，分配 PRD 链接 `req-92-card-template-single-image.md`。

### 3.6 测试改造

| 测试类 | 处理 |
|---|---|
| `backend/.../core/domain/model/vo/CardStarImageTest.java` | **整个文件删除** |
| `backend/.../core/domain/model/entity/CardTemplateTest.java` | 删除 starImages 相关测试；新增 imageUrl 字段测试（create/update/null 跳过） |
| `backend/.../core/domain/service/CardTemplateDomainServiceTest.java` | 同上签名调整 |
| `backend/.../admin/api/controller/CardTemplateControllerTest.java` | 删除 `POST /star-images` 接口测试；create/update 请求体加 imageUrl 字段；删除 starImages 相关断言 |
| `backend/.../admin/application/service/CardTemplateAppServiceTest.java` | 删除 `addOrUpdateStarImage` 测试；create/update 测试加 imageUrl |
| `frontend/admin/src/pages/CardTemplate/__tests__/index.test.tsx` | 重写：去掉星级图相关断言，加 imageUrl 字段测试，简化提交流程测试 |

### 3.7 数据迁移

dev 环境，无生产数据需保留：

- `card_template.image_url` 新字段初始为 NULL
- `card_star_image` 表数据丢弃（DROP TABLE）
- 现有 `card_template` 记录的卡面图需要重新上传

由 JPA `ddl-auto: update` 自动完成 schema 变更。

## 四、影响分析

### 4.1 受影响文件清单

**后端 core 层：**

| 文件 | 操作 |
|---|---|
| `domain/model/vo/CardStarImage.java` | 删除 |
| `domain/model/entity/CardTemplate.java` | 改造 |
| `domain/service/CardTemplateDomainService.java` | 改造 |
| `infrastructure/db/entity/CardTemplatePO.java` | 改造 |
| `infrastructure/db/entity/CardStarImagePO.java` | 删除 |
| `infrastructure/db/converter/CardTemplateConverter.java` | 改造 |
| `infrastructure/adapter/repoadapter/CardTemplateRepositoryAdapter.java` | **无需改动**（save 通过 Converter 完成 PO 持久化，Converter 改造后 Adapter 自动适配；其他 findById/findByConditions 等查询方法与 imageUrl 无关） |

**后端 admin 层：**

| 文件 | 操作 |
|---|---|
| `api/controller/CardTemplateController.java` | 改造（删除 star-images 接口） |
| `api/dto/request/CreateCardTemplateRequest.java` | 改造 |
| `api/dto/request/UpdateCardTemplateRequest.java` | 改造 |
| `api/dto/request/StarImageRequest.java` | 删除 |
| `api/dto/response/CardTemplateResponse.java` | 改造 |
| `api/dto/response/StarImageResponse.java` | 删除 |
| `api/assembler/CardTemplateAssembler.java` | 改造 |
| `application/service/CardTemplateAppService.java` | 改造 |
| `application/command/StarImageCommand.java` | 删除（如存在） |

**前端 admin：**

| 文件 | 操作 |
|---|---|
| `pages/CardTemplate/index.tsx` | 改造 |
| `pages/CardTemplate/components/StarImageUpload.tsx` | 删除 |
| `pages/CardTemplate/__tests__/index.test.tsx` | 重写 |
| `pages/CardTemplate/__tests__/StarImageUpload.test.tsx` | 删除 |
| `services/cardTemplate.ts` | 改造 |

**文档：**

| 文件 | 操作 |
|---|---|
| `knowledge-game.md` | 改造 4.6/8.2 节 |
| `docs/card-system-data-model.md` | 删表 4、改表 3/12、更新决策表 |
| `docs/overview.md` | 同步卡牌模型描述 |
| `docs/prd/req-17-card-template-admin.md` | 加 deprecated 标注 |
| `docs/prd/req-45-card-template-admin-page.md` | 加 deprecated 标注 |
| `docs/requirements.md` | REQ-92 状态推进 |

### 4.2 不受影响

- 用户端 `knowledge-game-app`（无 card_star_image 引用）
- 用户端前端 `frontend/user`
- `shop_product`、`reward_exchange_record`、`pity_counter`、`order` 等表
- 文件服务 `knowledge-game-file`
- REQ-83（文件服务）、REQ-91（m2m 鉴权）等已落地功能

## 五、验收标准

1. ✅ `card_template` 表新增 `image_url` 列；`card_star_image` 表已删除
2. ✅ 后端单测全部通过（core + admin，含改造后的 5 个测试类）
3. ✅ `POST /api/admin/card-templates/{id}/star-images` 接口已删除（404）
4. ✅ `POST /api/admin/card-templates` 请求体支持 `imageUrl` 字段
5. ✅ `PUT /api/admin/card-templates/{id}` 请求体支持 `imageUrl` 字段（null 表示不更新）
6. ✅ `GET /api/admin/card-templates/{id}` 响应含 `imageUrl` 字段，无 `starImages` 字段
7. ✅ 管理端卡牌管理页：创建/编辑表单仅一个图片上传位（ImageUploadField）
8. ✅ 管理端单测全部通过（包含 index.test.tsx 重写）
9. ✅ `knowledge-game.md`、`card-system-data-model.md` 已同步新决策
10. ✅ REQ-17/45 PRD 文件头已加 deprecated 标注
11. ✅ `docs/requirements.md` REQ-92 状态变为 `designed`

## 六、风险与权衡

| 风险 | 影响 | 缓解 |
|---|---|---|
| 破坏式 API 改动 | REQ-17/45 已 done，需要回改测试与前端 | 用户端代码尚不存在，无外部消费者；REQ-17/45 自身改造 |
| `updatePO` 中 imageUrl 覆盖 null 风险 | AppService 调用 update 时若 imageUrl 为 null 可能误清字段 | 由 `CardTemplate.update()` 在领域层处理 null 跳过；Converter 信任领域层产出 |
| 文档遗漏 | 改动面广，文档可能漏改 | 文档清单已穷举；自审环节检查 |
| 星级规则真空 | 4.6 节"星级提升规则"暂未定义新规则 | 在文档中明确标注"待后续需求定义"，避免误解为已完成 |

## 七、关联需求

- **REQ-17**：卡牌管理端 CRUD API（已 done，本需求破坏式改造）
- **REQ-45**：卡牌管理页（已 done，本需求破坏式改造）
- **REQ-93**：图片字段统一以 file_id 为关联 key（独立 idea 需求，与本需求解耦）
- 后续需求（待立项）：星级提升规则 + 群组管理员奖励档位配置

## 八、附录

### 8.1 关键代码示例

**新 CardTemplate.create() 调用：**

```java
CardTemplate template = CardTemplate.create(
    ipSeriesId, code, name, rarity, description, status,
    imageUrl  // 单字段
);
```

**新前端表单：**

```tsx
<ProForm.Item name="imageUrl" label="卡面图">
  <ImageUploadField bizType="CARD_TEMPLATE" placeholder="上传卡面图" />
</ProForm.Item>
```

### 8.2 数据库迁移 SQL（人工备份用）

```sql
-- REQ-92 卡牌模板图片模型简化
ALTER TABLE card_template ADD COLUMN image_url VARCHAR(500);
DROP TABLE card_star_image;
```
