# REQ-17 卡牌模板 — 管理端 CRUD API

> ⚠ 本 PRD 已被 REQ-92 部分废弃（card_star_image 模型简化为单图），卡牌模板相关 API/数据结构以最新 overview.md 和 req-92 PRD 为准。
>
> 日期：2026-06-08
> 状态：设计中

## 产品定位

管理端对卡牌模板进行增删改查。卡牌模板是盲盒抽卡的核心实体，属于 IP 系列（REQ-16），具有稀有度和 1~5 星级图片。

## 用户故事

**作为** 管理员
**我想要** 在管理后台对卡牌模板进行创建、查询、修改、删除操作（含星级图片管理）
**以便于** 配置盲盒抽卡池的卡牌内容和各星级展示图片

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/card-templates` | 创建卡牌模板（含星级图片） |
| GET | `/api/admin/card-templates` | 分页查询卡牌模板列表（不含星级图片） |
| GET | `/api/admin/card-templates/{id}` | 查询详情（含星级图片） |
| PUT | `/api/admin/card-templates/{id}` | 更新卡牌模板基础信息 |
| POST | `/api/admin/card-templates/{id}/star-images` | 添加/替换单张星级图片 |
| DELETE | `/api/admin/card-templates/{id}` | 软删除 |

### 创建卡牌模板

**请求体：**
```json
{
  "ipSeriesId": 1,
  "code": "PIKACHU",
  "name": "皮卡丘",
  "rarity": "SR",
  "description": "电气鼠宝可梦",
  "status": "ACTIVE",
  "starImages": [
    { "starLevel": 1, "imageUrl": "https://..." },
    { "starLevel": 2, "imageUrl": "https://..." }
  ]
}
```

**字段规则：**
- `ipSeriesId`：必填，必须存在且 ACTIVE 的 IP 系列
- `code`：必填，2~50 字符，全局唯一
- `name`：必填，1~50 字符
- `rarity`：必填，枚举 `N` / `R` / `SR` / `SSR` / `SP`
- `description`：可选，最大 500 字符
- `status`：必填，枚举 `ACTIVE` / `INACTIVE`，默认 `ACTIVE`
- `starImages`：可选，starLevel 1~5 不可重复；为空时自动生成 1 星默认图片

### 分页查询

**查询参数：** name(模糊)、ipSeriesId(精确)、rarity(精确)、status(精确)、page、size

列表不返回 starImages（需详情接口获取）。

### 更新

更新卡牌模板基础信息（不含星级图片）。所有字段可选（null 不修改）。

### 添加/替换星级图片

```
POST /api/admin/card-templates/{id}/star-images
请求体：{ "starLevel": 3, "imageUrl": "https://..." }
```

- starLevel：必填，1~5
- imageUrl：必填
- 已有该星级则替换图片，没有则新增
- 走聚合根行为方法，保持 DDD 一致性

### 删除

软删除（status → INACTIVE）。若已有用户收集（user_card），不允许删除。

---

## 设计方案

### 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| CardStarImage 定位 | 聚合内值对象 | 生命周期完全属于聚合根，无独立业务含义 |
| 星级范围 | 1~5 星（铁/铜/银/金/钻石） | 去除动态等级，5 星足够 |
| CardStarImage 字段 | starLevel + imageUrl（无 starName） | 前端根据 level 自行展示名称 |
| 空图片策略 | 自动生成 1 星默认图片 | 运营灵活性，图片可后续补充 |
| 单独添加星级图片 | 独立 API 端点 | 支持逐步上传，不需每次全量替换 |
| 跨聚合校验 | 领域服务 CardTemplateDomainService | DDD 规范：业务规则不泄漏到 Application 层 |
| 一对多持久化 | @OneToMany(cascade=ALL, orphanRemoval=true) | 聚合一致性边界内的一次事务保存 |
| Converter 嵌套 | default 方法手动转换 | MapStruct 对嵌套不可变值对象支持有限 |
| 列表/详情响应 | 分离两个 DTO | 列表不含 starImages，减少数据量 |
| 领域服务注册 | @Configuration + @Bean | 遵循 ddd-designer 规范：纯 POJO 领域服务 |

### 领域层

#### 枚举

- `CardRarity`：N, R, SR, SSR, SP
- `CardTemplateStatus`：ACTIVE, INACTIVE

#### 值对象 CardStarImage

```
domain/model/vo/CardStarImage.java
- final 字段：starLevel(int), imageUrl(String)
- 静态工厂 create(int starLevel, String imageUrl)
- 相等性由 starLevel 决定（同模板内唯一）
```

#### 聚合根 CardTemplate

```
domain/model/entity/CardTemplate.java
- 字段：id, ipSeriesId, code, name, rarity, description, status,
        starImages(List<CardStarImage>), createdAt, updatedAt
- create(...) — 传入参数 + starImages；空列表时自动创建 1 星默认图片
- reconstruct(...) — 从持久化重建
- update(...) — 更新基础字段（null 不修改）
- addOrUpdateStarImage(CardStarImage) — 添加或替换单个星级图片
- replaceStarImages(List<CardStarImage>) — 全量替换星级图片
- deactivate() — 软删除
```

#### 领域服务 CardTemplateDomainService

```
domain/service/CardTemplateDomainService.java
- 纯 POJO，无 @Service
- 构造器注入 IpSeriesRepositoryPort
- validateAndCreate(...) — 校验 IpSeries 存在 + ACTIVE，调用 CardTemplate.create()
- 注册方式：config/ 下的 @Configuration + @Bean
```

#### 出端口 CardTemplateRepositoryPort

```
domain/port/outbound/CardTemplateRepositoryPort.java
- save, findById, findByCode, findByConditions, existsById
```

### 基础设施层

#### PO

- `CardTemplatePO`：@Entity，@OneToMany(cascade=ALL, orphanRemoval=true) 映射 starImages
- `CardStarImagePO`：@Entity，字段 id/starLevel/imageUrl，@ManyToOne(fetch=LAZY) 关联 cardTemplate，唯一约束 (card_template_id, star_level)

#### JPA Repository

- `CardTemplateJpaRepository`：findByCode, findAll(Specification)
- 不需要独立的 CardStarImageJpaRepository

#### Converter

```
CardTemplateConverter.java
- toDomain(PO) — default 方法，手动转换嵌套 CardStarImagePO → CardStarImage
- toPO(domain) — 主表字段自动映射，starImages 需手动设置双向关联
- updatePO(@MappingTarget, domain) — 手动处理嵌套列表
```

#### Adapter

```
CardTemplateRepositoryAdapter.java
- 实现 CardTemplateRepositoryPort
- Specification 4 条件：name(like), ipSeriesId(eq), rarity(eq), status(eq)
```

### 应用层（admin 模块）

```
CardTemplateAppService.java
- @Service，注入 CardTemplateDomainService + CardTemplateRepositoryPort + IpSeriesRepositoryPort
- create — 领域服务创建 → 保存 → 组装 DTO
- getById — 查询 → 查 ipSeriesName → 组装 DTO
- list — 多条件分页 → 转列表 DTO（不含 starImages）
- update — 查询 → 聚合行为方法 → 保存 → 组装 DTO
- addOrUpdateStarImage — 查询 → 聚合 addOrUpdateStarImage → 保存 → 组装 DTO
- delete — 查询 → deactivate → 保存
```

### API 层（admin 模块）

#### DTO

- `CreateCardTemplateRequest`：含 List<StarImageRequest>
- `StarImageRequest`：starLevel(@Min(1) @Max(5)), imageUrl
- `UpdateCardTemplateRequest`：基础字段可选（无 starImages）
- `AddStarImageRequest`：starLevel(@Min(1) @Max(5)), imageUrl
- `CardTemplateResponse`：基础字段 + List<StarImageResponse> + ipSeriesName
- `StarImageResponse`：starLevel, imageUrl
- `CardTemplateListResponse`：基础字段，无 starImages

#### Assembler

- `CardTemplateAssembler`：toResponse（详情）、toListResponse（列表）

#### Controller

- `CardTemplateController`：`/api/admin/card-templates`，六个端点（CRUD + 单独添加星级图片）

---

## 验收标准

- DDD 各层完整且合规（domain 零框架依赖、Controller 零领域模型依赖）
- card_template + card_star_image 表自动建出
- 创建时空 starImages 自动生成 1 星默认图片
- code 全局唯一性校验
- ipSeriesId 存在性 + ACTIVE 校验（领域服务）
- 分页查询支持 4 维筛选
- 单独添加/替换星级图片接口可用
- 统一返回体 Result<T>
- 单元测试覆盖领域行为

## 技术决策

| 决策项 | 选择 |
|--------|------|
| 数据库表 | card_template、card_star_image |
| 星级范围 | 1~5 星 |
| CardStarImage 字段 | starLevel + imageUrl（无 starName） |
| 主键策略 | 自增 BIGINT |
| 软删除 | status 字段 |
| 领域服务注册 | @Configuration + @Bean |
| 暂不含鉴权 | JWT 未实现 |
