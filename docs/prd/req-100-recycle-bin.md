# REQ-100：通用回收站系统 — 架构设计与前端页面

> 状态：`confirmed`
> 创建：2026-06-19
> 前置依赖：REQ-86（管理端列表排序规范）、REQ-94（聚合根停用/启用校验）
> 后置依赖：REQ-101（定时清理）、REQ-102（手动永久删除）、REQ-103（通用恢复框架）、REQ-104~108（各资源对接）

## 1. 背景与定位

当前 5 个管理端资源（IpSeries / CardTemplate / Question / KnowledgeCategory / KnowledgeItem）的 DELETE 接口全部走 `domain.deactivate()` → `status=INACTIVE` 的"软删除"，**`INACTIVE` 同时承担了「停用」和「删除」两种语义**，导致：

1. 用户无法区分"暂时停用"和"真正删除"
2. 没有"误删恢复"机制，INACTIVE 资源可以被重新启用但和"删除"语义混淆
3. 真正物理删除从未发生过，数据持续累积

**REQ-100 引入通用回收站系统**：将 DELETE 与 INACTIVE 解耦——

- **INACTIVE 保留为"停用"语义**：通过现有 UPDATE 接口切换 `status`，原表行不变
- **DELETE 变为新增的"删除"语义**：物理删除原表行 + 写入回收站快照，30 天保留期内可恢复

**REQ-100 是回收站系统的「框架搭建需求」**：只交付基础设施 + 列表查询能力 + 策略契约，**不实现任何具体资源的对接**（DELETE 端点改造由 REQ-104~108 分别负责）、**不实现恢复/永久删除动作**（REQ-101/102/103 负责）。

## 2. 设计决策汇总

| # | 决策项 | 结果 | 理由 |
|---|--------|------|------|
| 1 | DELETE 语义 | **DELETE 与 INACTIVE 并存**：DELETE 物理删除原表+移入回收站；INACTIVE 保留为"停用"语义 | 用户已明确区分两套语义；现有停用流程零改动 |
| 2 | 表结构 | **1 张总览索引表 + 5 张详情快照表**（每资源一张） | 总览表承担列表/筛选/查询（单表无 UNION），详情表存完整快照（恢复时用） |
| 3 | 关联表数据处理 | **一并存入资源 `_deleted` 表的 JSON 字段**（如 `related_data`） | 单表表达完整快照，恢复自含；避免中间表悬空引用 |
| 4 | KnowledgeCategory 树形级联 | **拒绝删除有子分类的父分类** | 保证树结构完整；用户需先删子后删父 |
| 5 | 跨资源引用校验 | **强校验拒绝**：被引用的资源不允许删除 | 与现有 INACTIVE 校验逻辑保持一致，数据完整性优先 |
| 6 | 恢复后状态 | **统一进入 INACTIVE**（已固化为契约） | 避免"ACTIVE 时删除→依赖此资源的其他资源已 INACTIVE→恢复时还原成 ACTIVE→数据不一致"；由用户手动启用 |
| 7 | REQ-100 scope | **只搭框架 + 列表只读页面** | 恢复/永久删除/定时清理分别由 REQ-101/102/103 承担；本需求不验证（等对接资源后一起验证） |
| 8 | `deleted_by` 字段 | **存 admin username**（不变更，从 `Authentication.getName()` 直接拿） | nickname 可变更，username 稳定；避免引入 ID 反查 |
| 9 | 列表 API 形态 | **1 个 endpoint** `/api/admin/recycle-bin?page&size&resourceType&keyword&sort` 查总览表 | 总览表是单表，无需 UNION；resourceType 缺省/`ALL`=不过滤 |
| 10 | `_deleted` 详情表主键 | **独立自增 PK + `original_id` 字段** | 主键独立、诊断清晰；恢复时显式指定原 ID（MySQL 支持跳过 AUTO_INCREMENT） |
| 11 | 资源类型实现 | **新建 `ResourceType` 枚举**（字符串值与 bizType 命名风格对齐） | bizType 是文件用途维度（CATEGORY_ICON/COVER 颗粒度不匹配），不能直接复用；永久删除时通过 `ResourceType → bizType[]` 映射做文件清理 |
| 12 | 扩展性 | **策略模式 + 注册中心 + 批量操作默认实现** | 新增资源只需写一个策略 Bean，注册中心自动发现；批量恢复/批量永久删除自动获得 |
| 13 | 前端入口 | **「系统」菜单下新增「回收站」子菜单** | 回收站是数据管理工具，不是业务功能；与「用户管理」同级 |
| 14 | 前端目录树 | **动态从 `GET /supported-types` 拉取** | 本需求交付时为空集，等 REQ-104~108 对接后自动出现各资源类型 |
| 15 | 前端批量按钮 | **预留位置但 disabled + tooltip** | 框架就位但功能未通；避免误导用户 |

## 3. 数据模型

### 3.1 总览索引表 `recycle_bin`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | — | 主键 |
| `resource_type` | VARCHAR(50) | NOT NULL | 资源类型（`ResourceType` 枚举字符串值） |
| `original_id` | BIGINT | NOT NULL | 原表 ID（恢复时用） |
| `original_name` | VARCHAR(255) | NOT NULL | 冗余：列表展示用（避免 JOIN 详情表） |
| `original_created_at` | DATETIME | NULLABLE | 原表审计字段快照 |
| `original_updated_at` | DATETIME | NULLABLE | 原表审计字段快照 |
| `original_created_by` | VARCHAR(64) | NULLABLE | 原表审计字段快照（username）。**预留字段**：现有 PO 体系无 `created_by`/`updated_by` 字段，本需求交付时写入为 NULL；REQ-104~108 对接时若原表已加审计字段则填入 |
| `original_updated_by` | VARCHAR(64) | NULLABLE | 同上，预留字段 |
| `deleted_by` | VARCHAR(64) | NOT NULL | 删除人 admin username |
| `deleted_at` | DATETIME | NOT NULL | 删除时间 |
| `restore_deadline` | DATETIME | NOT NULL | 恢复截止时间（`deleted_at + 30 天`，REQ-101 用，本需求建好字段并赋值） |

**字段设计说明**：
- **无 `updated_at` 列**：回收站记录创建后不可修改（恢复/永久删除是 DELETE，不是 UPDATE），不需要更新时间戳
- **无 `created_at` 列**：`deleted_at` 即记录创建时间，语义等价，避免冗余

**索引**：
- `uk_resource_original (resource_type, original_id)` — 联合唯一（同一资源在回收站只能有一份）
- `idx_resource_type (resource_type)` — resourceType 过滤
- `idx_restore_deadline (restore_deadline)` — REQ-101 定时清理扫描

### 3.2 详情快照表（5 张）

每张详情表与对应原表**字段完全镜像 + 额外的 `original_id`、`related_data`、`deleted_by`、`deleted_at` 审计字段**。DDL 由 JPA `ddl-auto: update` 自动生成（PO 类标注 `@Entity`），无需手写迁移脚本。

#### 3.2.1 `ip_series_deleted`

```sql
-- 字段完全镜像 ip_series 表（id 除外，使用独立自增 PK）+ 以下额外字段：
id              BIGINT PK AUTO_INCREMENT
original_id     BIGINT NOT NULL          -- 原 ip_series.id
related_data    JSON                     -- NULL（IP 系列无关联表）
deleted_by      VARCHAR(64) NOT NULL
deleted_at      DATETIME    NOT NULL
INDEX idx_original_id (original_id)
```

#### 3.2.2 `card_template_deleted`

```sql
id              BIGINT PK AUTO_INCREMENT
original_id     BIGINT NOT NULL
-- 卡牌主字段：code/name/description/rarity/ip_series_id/star_level_image_files/status/sort_order 等
related_data    JSON     -- NULL（仅 ip_series_id 外键引用，已在主字段）
deleted_by      VARCHAR(64) NOT NULL
deleted_at      DATETIME    NOT NULL
INDEX idx_original_id (original_id)
```

#### 3.2.3 `question_deleted`

```sql
id              BIGINT PK AUTO_INCREMENT
original_id     BIGINT NOT NULL
-- 题目主字段：type/content/options(json)/answer(json)/explanation/difficulty/tags(json)/status 等
related_data    JSON     -- 例：{"categoryIds":[1,2,3]}（多对多分类关联快照）
deleted_by      VARCHAR(64) NOT NULL
deleted_at      DATETIME    NOT NULL
INDEX idx_original_id (original_id)
```

#### 3.2.4 `knowledge_category_deleted`

```sql
id              BIGINT PK AUTO_INCREMENT
original_id     BIGINT NOT NULL
-- 分类主字段：name/sort_order/status/parent_id 等
related_data    JSON     -- 例：{"childrenCount":0}（"拒绝删除有子分类的父分类"约束的快照证明）
deleted_by      VARCHAR(64) NOT NULL
deleted_at      DATETIME    NOT NULL
INDEX idx_original_id (original_id)
```

#### 3.2.5 `knowledge_item_deleted`

```sql
id              BIGINT PK AUTO_INCREMENT
original_id     BIGINT NOT NULL
-- 知识条目主字段：title/content/content_html/cover_image_file_id/cover_image_url/tags(json)/sort_order/status 等
related_data    JSON     -- 例：{"categoryIds":[1,2,3]}（多对多分类关联快照）
deleted_by      VARCHAR(64) NOT NULL
deleted_at      DATETIME    NOT NULL
INDEX idx_original_id (original_id)
```

### 3.3 级联与一致性约束

- **本需求范围内**：详情表 PO + JPA Repo 建好但**不写入数据**（写入路径在 REQ-104~108 各资源 DELETE 端点对接时实现）
- **写入路径**（REQ-104~108 实现）：
  1. 事务开启
  2. 校验可删除（强校验，被引用/有子节点则抛异常）
  3. `INSERT INTO xxx_deleted SELECT *, deleted_by, deleted_at FROM xxx WHERE id=?`
  4. `DELETE FROM xxx WHERE id=?`（物理删除原表）
  5. 同时清理关联表行（如 `question_category_relation`），关联数据已快照在 `related_data` JSON
  6. `INSERT INTO recycle_bin (...)`（总览表登记）
  7. 事务提交
- **恢复路径**（REQ-103 实现）：
  1. 校验关联仍存在（父分类、引用的分类等）
  2. 校验唯一约束不冲突（如 code/name）
  3. `INSERT INTO xxx SELECT ... FROM xxx_deleted WHERE original_id=?`（恢复时强制 `status='INACTIVE'`）
  4. 重建关联表行（从 `related_data` JSON 反序列化）
  5. `DELETE FROM xxx_deleted WHERE original_id=?`
  6. `DELETE FROM recycle_bin WHERE id=?`
- **物理清理路径**（REQ-101 定时清理 + REQ-102 手动永久删除）：
  1. 删除 `xxx_deleted` 详情表记录
  2. 清理关联文件（通过 `ResourceType.toBizTypes()` 映射查 `FilePathMapping` 拿到所有 bizType → 调 file 服务删除）
  3. 删除 `recycle_bin` 总览表记录

## 4. 后端 DDD 分层

### 4.1 Domain 层（`core/domain`）

#### 4.1.1 领域模型

```
domain/model/entity/
  RecycleBinItem.java                  — 聚合根（纯数据持有对象：仅 getter + all-args reconstruct 构造器；恢复/永久删除行为方法留 REQ-103/104~108）
domain/model/domainenum/
  ResourceType.java                    — 枚举（IP_SERIES/CARD_TEMPLATE/QUESTION/KNOWLEDGE_CATEGORY/KNOWLEDGE_ITEM）
                                       + toBizTypes() 映射方法（永久删除时用于文件清理）
domain/model/vo/
  DeletedSnapshot.java                 — 值对象标记接口（每种资源自己的实现，REQ-104~108 补）
```

#### 4.1.2 `ResourceType` 枚举设计

```java
public enum ResourceType {
    IP_SERIES,
    CARD_TEMPLATE,
    QUESTION,
    KNOWLEDGE_CATEGORY,
    KNOWLEDGE_ITEM;

    /**
     * 该资源类型对应的文件 bizType 列表（用于永久删除时清理关联文件）。
     * 与 admin 模块的 FilePathMapping.MAPPING 的 key 对齐。
     */
    public List<String> toBizTypes() {
        return switch (this) {
            case IP_SERIES -> List.of("IP_SERIES");
            case CARD_TEMPLATE -> List.of("CARD_TEMPLATE");
            case QUESTION -> List.of();  // 题目无图片
            case KNOWLEDGE_CATEGORY -> List.of("CATEGORY_ICON", "CATEGORY_COVER");
            case KNOWLEDGE_ITEM -> List.of("KNOWLEDGE_ITEM_COVER");
        };
    }

    public String displayName() {
        return switch (this) {
            case IP_SERIES -> "IP 系列";
            case CARD_TEMPLATE -> "卡牌模板";
            case QUESTION -> "题库";
            case KNOWLEDGE_CATEGORY -> "知识分类";
            case KNOWLEDGE_ITEM -> "知识条目";
        };
    }
}
```

#### 4.1.3 出端口

```java
// domain/port/outbound/RecycleBinItemRepositoryPort.java
public interface RecycleBinItemRepositoryPort {
    /** 本需求实现：列表查询 */
    PageResult<RecycleBinItem> findAll(ResourceType type, String keyword, int page, int size, SortField sortField);

    /** 本需求实现：按 ID 查询（前端单条详情时用，本需求前端不调） */
    Optional<RecycleBinItem> findById(Long id);

    // ===== 留后签名（REQ-102/103/104~108 实现）=====

    /** REQ-104~108 DELETE 端点对接时调用 */
    void save(RecycleBinItem item);
    /** REQ-102/103 用 */
    void deleteById(Long id);
}
```

#### 4.1.4 策略模式（核心扩展机制）

```
domain/service/recyclebin/
  RecycleBinItemStrategy.java           — 策略接口（每种资源一个实现）
  RecycleBinItemStrategyRegistry.java   — 注册中心（按 resourceType 分派）
  DeletedSnapshot.java                  — 同 4.1.1 vo
```

##### `RecycleBinItemStrategy<T>` 接口

```java
public interface RecycleBinItemStrategy<T> {

    /** 该策略处理的资源类型（注册 key） */
    ResourceType getResourceType();

    // ===== 单条操作（本需求仅声明签名，具体实现由 REQ-104~108 各资源补）=====

    /** 删除前校验：被引用/有子节点则抛 BusinessException */
    void validateDeletable(Long originalId);

    /** 序列化原领域对象 + 关联数据 → 写入对应 _deleted 详情表 + 总览表登记 */
    void moveToRecycleBin(Long originalId, String deletedBy);

    /** 从 _deleted 详情表读快照 → 校验关联仍存在 → INSERT 原表（强制 INACTIVE）→ DELETE _deleted */
    void restore(Long recycleBinId);

    /** 物理删除 _deleted 详情表记录（+ 通过 ResourceType.toBizTypes() 清理关联文件） */
    void purge(Long recycleBinId);

    // ===== 批量操作（默认实现：循环调单条，子类可覆盖为批量 SQL）=====

    default void batchRestore(List<Long> recycleBinIds) {
        recycleBinIds.forEach(this::restore);
    }

    default void batchPurge(List<Long> recycleBinIds) {
        recycleBinIds.forEach(this::purge);
    }
}
```

##### `RecycleBinItemStrategyRegistry` 注册中心

```java
public class RecycleBinItemStrategyRegistry {

    private final Map<ResourceType, RecycleBinItemStrategy<?>> registry = new EnumMap<>(ResourceType.class);

    /**
     * Spring 注入所有策略 Bean，自动按 resourceType 注册。
     * 这是核心扩展点：新增资源只需写一个策略 Bean，自动被发现。
     */
    public RecycleBinItemStrategyRegistry(List<RecycleBinItemStrategy<?>> strategies) {
        for (RecycleBinItemStrategy<?> s : strategies) {
            RecycleBinItemStrategy<?> existing = registry.put(s.getResourceType(), s);
            if (existing != null) {
                throw new IllegalStateException("ResourceType " + s.getResourceType()
                        + " 已注册策略: " + existing.getClass().getName()
                        + "，新策略: " + s.getClass().getName());
            }
        }
    }

    public RecycleBinItemStrategy<?> get(ResourceType type) {
        RecycleBinItemStrategy<?> s = registry.get(type);
        if (s == null) {
            throw new BusinessException(501, "资源类型 " + type + " 暂未接入回收站");
        }
        return s;
    }

    /** 当前已注册的资源类型（供前端目录树渲染 + 列表 resourceType 校验） */
    public Set<ResourceType> supportedTypes() {
        return Collections.unmodifiableSet(registry.keySet());
    }
}
```

**注册位置**：`KnowledgeGameCoreAutoConfiguration` 集中注册（参考现有领域服务注册模式）。

#### 4.1.5 关键约束（已固化为契约，REQ-103/104~108 必须遵守）

- **恢复后状态强制 INACTIVE**：`RecycleBinItemStrategy.restore()` 实现时，恢复到原表的行必须 `status='INACTIVE'`，由用户通过 UPDATE 接口手动启用
- **强校验拒绝**：`validateDeletable()` 实现时，被引用（如 IpSeries 被 CardTemplate 引用）或有子节点（KnowledgeCategory 树形）则抛 `BusinessException`，不允许删除
- **快照完整性**：`moveToRecycleBin()` 必须把关联表数据写入 `related_data` JSON，并物理删除关联表行

#### 4.1.6 空策略 Bean 注入行为（**本需求交付时关键现象**）

`RecycleBinItemStrategyRegistry` 构造函数接受 `List<RecycleBinItemStrategy<?>>` 由 Spring 自动注入。**本需求交付时无任何策略 Bean 注册**（5 个具体策略 Bean 由 REQ-104~108 实现），Spring 对 `List<T>` 注入在无候选 Bean 时**返回空列表**（不抛 `NoSuchBeanDefinitionException`，是 Spring 标准行为），所以：

- 注册中心以**空 Map** 启动，应用正常启动不报错
- `supportedTypes()` 返回 `Collections.emptySet()`
- `/api/admin/recycle-bin/supported-types` 返回 `[]`
- `/api/admin/recycle-bin` 列表查询正常工作（不依赖策略 Bean，直接查总览表）

**首次接入策略 Bean 的预期变化**（REQ-104 起）：实现 `IpSeriesRecycleBinStrategy` Bean 后，Spring 自动收集到 List 中，注册中心自动注册，无需修改本需求代码。

### 4.2 Infrastructure 层（`core/infrastructure`）

```
infrastructure/db/entity/
  RecycleBinItemPO.java                 — 总览表 PO
  IpSeriesDeletedPO.java                — IP 系列详情表 PO（仅建表，本需求不写入）
  CardTemplateDeletedPO.java            — 卡牌详情表 PO（同上）
  QuestionDeletedPO.java                — 题目详情表 PO（同上）
  KnowledgeCategoryDeletedPO.java       — 分类详情表 PO（同上）
  KnowledgeItemDeletedPO.java           — 知识条目详情表 PO（同上）
infrastructure/db/repository/
  RecycleBinItemJpaRepository.java      — 总览表 Spring Data JPA Repo（本需求实现 findAll）
  IpSeriesDeletedJpaRepository.java     — 详情表 Repo（仅声明，本需求不用）
  CardTemplateDeletedJpaRepository.java
  QuestionDeletedJpaRepository.java
  KnowledgeCategoryDeletedJpaRepository.java
  KnowledgeItemDeletedJpaRepository.java
infrastructure/db/converter/
  RecycleBinItemConverter.java          — MapStruct PO ↔ Domain
infrastructure/adapter/repoadapter/
  RecycleBinItemRepositoryAdapter.java  — 出端口实现（仅 findAll/findById；save/deleteById 留 TODO）
infrastructure/adapter/support/
  （沿用现有 SortFields.java：领域 SortField ↔ Spring Sort 转换）
```

#### 4.2.1 `RecycleBinItemRepositoryAdapter` 关键实现

```java
public class RecycleBinItemRepositoryAdapter implements RecycleBinItemRepositoryPort {

    private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>() {{
        put("deletedAt", "删除时间");
        put("restoreDeadline", "剩余保留天数");
        put("resourceType", "资源类型");
        put("originalName", "资源名称");
        put("originalCreatedAt", "原始创建时间");
    }};
    // 默认排序：deletedAt DESC（最近删除的排最前）

    @Override
    public PageResult<RecycleBinItem> findAll(ResourceType type, String keyword,
                                              int page, int size, SortField sortField) {
        Sort springSort = toSpringSort(sortField);
        PageRequest pageRequest = PageRequest.of(page - 1, size, springSort);

        Specification<RecycleBinItemPO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("resourceType"), type));
            }
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(root.get("originalName"), "%" + keyword + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RecycleBinItemPO> poPage = jpaRepository.findAll(spec, pageRequest);
        return PageResult.of(
                poPage.getContent().stream().map(Converter.INSTANCE::toDomain).toList(),
                poPage.getTotalElements(),
                page,
                size
        );
    }

    private Sort toSpringSort(SortField sortField) {
        SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
        if (sortField == null) {
            return Sort.by(Sort.Direction.DESC, "deletedAt");
        }
        return SortFields.toSpringSort(sortField);
    }

    // save/deleteById 留 TODO，REQ-102/103/104~108 实现
}
```

### 4.3 Application 层（`admin/application`）

#### 4.3.1 `RecycleBinItemAssembler`（MapStruct @Mapper，位于 `admin/api/assembler/`）

```java
@Mapper
public interface RecycleBinItemAssembler {

    RecycleBinItemAssembler INSTANCE = Mappers.getMapper(RecycleBinItemAssembler.class);

    /**
     * Domain → Response。
     * - LocalDateTime 字段转 epoch 毫秒（遵循 REQ-86 时间戳协议）
     * - daysUntilPurge 在此计算（基于 LocalDateTime.now()，时间源详见 4.3.2）
     */
    // resourceType 走 source 映射链（MapStruct 自动调 enum.name()），无需手写 expression
    @Mapping(target = "resourceType", source = "resourceType.name")
    // resourceTypeDisplay 需 displayName() 中文映射，无 source 链可用，必须用 expression
    @Mapping(target = "resourceTypeDisplay", expression = "java(item.getResourceType().displayName())")
    @Mapping(target = "daysUntilPurge", expression = "java(calcDaysUntilPurge(item.getRestoreDeadline()))")
    @Mapping(target = "originalCreatedAt", source = "originalCreatedAt", qualifiedByName = "toEpochMilli")
    // ... 其他 *At 字段同样 toEpochMilli
    RecycleBinItemResponse toResponse(RecycleBinItem item);

    @Named("toEpochMilli")
    default Long toEpochMilli(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    default Integer calcDaysUntilPurge(LocalDateTime restoreDeadline) {
        if (restoreDeadline == null) return 0;
        long diffMs = restoreDeadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                - System.currentTimeMillis();
        return (int) Math.max(0, (diffMs + 86_399_999) / 86_400_000);  // 向上取整
    }
}
```

**与项目惯例对齐**：参考 `KnowledgeItemAssembler`（admin/api/assembler/）的模式，`@Mapper` 接口 + `INSTANCE` 单例 + `@Named` default 方法。

#### 4.3.2 `RecycleBinAppService`

```java
@Service
public class RecycleBinAppService {

    private final RecycleBinItemRepositoryPort recycleBinRepository;
    private final RecycleBinItemStrategyRegistry strategyRegistry;

    /** 本需求实现：列表查询 */
    public PageResult<RecycleBinItemResponse> list(RecycleBinListRequest request) {
        SortField sortField = SortField.parse(request.getSort(), request.getOrder());
        ResourceType type = parseResourceType(request.getResourceType());
        PageResult<RecycleBinItem> result = recycleBinRepository.findAll(
                type, request.getKeyword(), request.getPage(), request.getSize(), sortField);
        return PageResult.of(
                result.getList().stream().map(RecycleBinItemAssembler.INSTANCE::toResponse).toList(),
                result.getTotal(),
                result.getPage(),
                result.getSize()
        );
    }

    /**
     * 解析 resourceType 参数。
     * - null / 空 / "ALL"（大小写不敏感）→ 返回 null（不过滤）
     * - 合法枚举字符串 → 返回 ResourceType
     * - 非法值 → 抛 BusinessException(400, "不支持的资源类型: XXX")
     */
    private ResourceType parseResourceType(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return ResourceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的资源类型: " + raw
                    + "，允许的值: ALL, " + Arrays.stream(ResourceType.values())
                        .map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    /** 本需求实现：已接入的资源类型（驱动前端目录树） */
    public List<SupportedTypeResponse> supportedTypes() {
        return strategyRegistry.supportedTypes().stream()
                .map(t -> new SupportedTypeResponse(t.name(), t.displayName()))
                .sorted(Comparator.comparing(SupportedTypeResponse::getDisplayName))
                .toList();
    }

    // ===== 留后方法签名（REQ-102/103 实现）=====

    /** REQ-103 实现：单条恢复 */
    public void restore(Long recycleBinId) {
        RecycleBinItem item = recycleBinRepository.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException(404, "回收站记录不存在: " + recycleBinId));
        strategyRegistry.get(item.getResourceType()).restore(recycleBinId);
    }

    /** REQ-103 实现：批量恢复（按 resourceType 分组分派） */
    public void batchRestore(List<Long> recycleBinIds) {
        Map<ResourceType, List<Long>> grouped = groupByResourceType(recycleBinIds);
        grouped.forEach((type, ids) -> strategyRegistry.get(type).batchRestore(ids));
    }

    /** REQ-102 实现：单条永久删除 */
    public void purge(Long recycleBinId) {
        RecycleBinItem item = recycleBinRepository.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException(404, "回收站记录不存在: " + recycleBinId));
        strategyRegistry.get(item.getResourceType()).purge(recycleBinId);
    }

    /** REQ-102 实现：批量永久删除 */
    public void batchPurge(List<Long> recycleBinIds) {
        Map<ResourceType, List<Long>> grouped = groupByResourceType(recycleBinIds);
        grouped.forEach((type, ids) -> strategyRegistry.get(type).batchPurge(ids));
    }

    private Map<ResourceType, List<Long>> groupByResourceType(List<Long> recycleBinIds) {
        return recycleBinIds.stream()
                .collect(Collectors.groupingBy(
                        id -> recycleBinRepository.findById(id)
                                .orElseThrow(() -> new BusinessException(404, "回收站记录不存在: " + id))
                                .getResourceType()
                ));
    }
}
```

### 4.4 API 层（`admin/api`）

#### 4.4.1 Controller

```java
@RestController
@RequestMapping("/api/admin/recycle-bin")
public class RecycleBinController {

    private final RecycleBinAppService appService;

    /** 本需求实现：分页查询 */
    @GetMapping
    public Result<PageResult<RecycleBinItemResponse>> list(RecycleBinListRequest request) {
        return Result.success(appService.list(request));
    }

    /** 本需求实现：已接入的资源类型（前端目录树） */
    @GetMapping("/supported-types")
    public Result<List<SupportedTypeResponse>> supportedTypes() {
        return Result.success(appService.supportedTypes());
    }

    // ===== 留后端点（REQ-102/103 实现，本需求不写）=====
    //
    // GET  /{id}               — REQ-103 单条详情查询（含 _deleted 表 related_data JSON 解析）
    // POST /{id}/restore       — REQ-103 单条恢复
    // POST /batch-restore      — REQ-103 批量恢复
    // DELETE /{id}             — REQ-102 单条永久删除
    // POST /batch-purge        — REQ-102 批量永久删除
}
```

**本需求范围内只实现 `/list` 与 `/supported-types`**。其余端点不写（不为不会发生的场景写代码），REQ-102/103 时按需新增。前端预留按钮位置但 disabled。

#### 4.4.2 DTO

```
api/dto/
  RecycleBinListRequest.java           — 列表查询参数（含 JSR-303 约束，详见下表）
  RecycleBinItemResponse.java          — 列表项响应
  SupportedTypeResponse.java           — type/displayName
```

**DTO 范围说明**：本需求只写以上 3 个 DTO。`RecycleBinItemDetailResponse`（含 `related_data` JSON 解析）、`BatchOperationRequest` 等留 REQ-102/103 时按需新增。

##### `RecycleBinListRequest` 字段定义

| 字段 | 类型 | 必填 | 约束 / 默认 | 说明 |
|------|------|------|-----------|------|
| `page` | `Integer` | 否 | `@Min(0)`，默认 `0` | 页码（0-based，与项目其他列表接口一致） |
| `size` | `Integer` | 否 | `@Min(1) @Max(100)`，默认 `20` | 每页条数（防超大查询） |
| `resourceType` | `String` | 否 | `null` / 空 / `"ALL"`（大小写不敏感）= 不过滤；否则必须能解析为 `ResourceType` 枚举（AppService 校验） | 资源类型过滤 |
| `keyword` | `String` | 否 | `@Size(max=100)` | 关键字模糊匹配 `original_name` |
| `sort` | `String` | 否 | 由 `SortField.parse` 解析为 VO，白名单校验在 Adapter 层 `SortFieldSpec.validate` 完成（详见 4.2.1 节）；非白名单字段抛 BusinessException(400) | 排序字段 |
| `order` | `String` | 否 | `"asc"` / `"desc"`（大小写不敏感），非 `asc` 一律视为 `desc`，详见 REQ-86 SortField.parse | 排序方向 |

**参数语义**：本接口所有参数可选，全空时返回第一页默认排序（`deletedAt DESC`）的全部记录。

##### `RecycleBinItemResponse` 字段定义

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `id` | `Long` | RecycleBinItem.id | 回收站记录 ID（前端用它做行 selection，REQ-102/103 用它做恢复/删除调用） |
| `resourceType` | `String` | ResourceType.name() | 枚举字符串值 |
| `resourceTypeDisplay` | `String` | ResourceType.displayName() | 中文显示 |
| `originalId` | `Long` | 原表 ID | 恢复时用 |
| `originalName` | `String` | 原表 name 冗余 | 列表展示 |
| `originalCreatedAt` | `Long` | 原表 created_at | epoch 毫秒 |
| `originalUpdatedAt` | `Long` | 原表 updated_at | epoch 毫秒 |
| `originalCreatedBy` | `String` | 预留字段，本需求始终 `null` | 详见 3.1 节 |
| `originalUpdatedBy` | `String` | 预留字段，本需求始终 `null` | 同上 |
| `deletedBy` | `String` | admin username | 删除人 |
| `deletedAt` | `Long` | deleted_at | epoch 毫秒 |
| `restoreDeadline` | `Long` | restore_deadline | epoch 毫秒 |
| `daysUntilPurge` | `Integer` | Assembler 计算 | `max(0, ceil((restoreDeadline - now) / 86400_000))`，前端按 `< 7` 高亮红色 |

#### 4.4.3 列表响应规范（遵循 REQ-86 时间戳协议）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 42,
        "resourceType": "IP_SERIES",
        "resourceTypeDisplay": "IP 系列",
        "originalId": 17,
        "originalName": "火影忍者",
        "originalCreatedAt": 1718000000000,
        "originalUpdatedAt": 1718100000000,
        "originalCreatedBy": null,
        "originalUpdatedBy": null,
        "deletedBy": "operator01",
        "deletedAt": 1720000000000,
        "restoreDeadline": 1722592000000,
        "daysUntilPurge": 23
      }
    ],
    "totalElements": 87,
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 5
  }
}
```

- 所有 `*At` 时间字段统一返回 epoch 毫秒（Long）
- `daysUntilPurge` 是计算列：`max(0, ceil((restoreDeadline - now) / 86400_000))`，前端按 `< 7` 高亮红色
- 计算位置：**后端 Assembler 计算**（不存数据库列），时间源用 `System.currentTimeMillis()`（项目惯例，无 Clock 注入）。测试断言策略：测试数据用 `now + 7 天` / `now + 0 天` 等相对时间构造，断言用 `>=` / `<=` / 区间容忍（不引入 Clock 框架避免过度设计）

#### 4.4.4 异常规范

| 场景 | HTTP | ResultCode |
|------|------|------------|
| `resourceType` 非合法枚举值 | 400 | **PARAM_ERROR**（"不支持的资源类型: XXX"）— **本 PRD 新增枚举值**，详见 4.5 节 |
| `sort` 字段不在白名单 | 400 | `BusinessException(400, "...")` 双参构造（沿用 `SortFieldSpec.validate` 现有模式） |
| 找不到 `id` 对应的回收站记录 | 404 | NOT_FOUND（"回收站记录不存在: XXX"） |
| 调用未实现的端点 | 501 | **NOT_IMPLEMENTED**（"该功能暂未启用"）— **本 PRD 新增枚举值**，详见 4.5 节 |

### 4.5 ResultCode 枚举扩展（**本 PRD 新增**）

现有 `core/common/result/ResultCode.java` 不含以下枚举值，REQ-100 ISSUE-1 需新增：

```java
PARAM_ERROR(400, "参数错误"),         // 替代 FAIL，专用于参数校验失败（与 FAIL 通用语义区分）
NOT_IMPLEMENTED(501, "功能未实现"),   // 用于本需求"留后端点"等场景；后续 REQ-102/103 端点接入后不再使用
```

**与现有枚举的关系**：
- 现有 `FAIL(400)` 用于通用业务失败；新增 `PARAM_ERROR(400)` 用于**参数校验**失败，语义更精确
- 现有 `INTERNAL_ERROR(500)` 用于服务器内部错误；新增 `NOT_IMPLEMENTED(501)` 用于**端点契约已声明但实现留后**的场景，HTTP 语义更准确

## 5. 前端设计

### 5.1 路由

```typescript
// frontend/admin/config/routes.ts 「系统」菜单下新增
{
  path: '/system',
  name: '系统',
  icon: 'SettingOutlined',
  routes: [
    { path: '/system/user', name: '用户管理', component: './User' },
    { path: '/system/recycle-bin', name: '回收站', component: './RecycleBin' },  // 新增
  ],
}
```

### 5.2 文件结构

```
frontend/admin/src/pages/RecycleBin/
├── index.tsx                              — 页面主入口（布局 + 状态管理）
└── components/
    ├── ResourceTypeTree.tsx               — 左侧目录树（动态从 /supported-types 拉）
    └── RecycleBinTable.tsx                — 右侧 ProTable
frontend/admin/src/services/recycleBin.ts  — API 调用封装 + TS 类型定义
```

### 5.3 页面布局

```
┌──────────────────────────────────────────────────────────────────────┐
│  /system/recycle-bin                                       [刷新]      │
├──────────┬───────────────────────────────────────────────────────────┤
│ 目录树   │  ProTable                                                 │
│          │  ┌─ 工具栏 ─────────────────────────────────────────────┐ │
│ ▼ 全部   │  │ [批量恢复] [批量永久删除]    🔍 关键字搜索         │ │
│   IP系列 │  │ (本需求 disabled + tooltip「等待资源对接」)          │ │
│   卡牌   │  └─────────────────────────────────────────────────────┘ │
│   题目   │  ☐ 资源类型   资源名称   删除人   删除时间   剩余保留天数│
│   分类   │  ☐ ...                                                    │
│   知识   │  分页                                                     │
└──────────┴───────────────────────────────────────────────────────────┘
```

### 5.4 ProTable 列定义

| 列 | 字段 | 说明 |
|----|------|------|
| 选择框 | - | `rowSelection` 多选（本需求支持选，批量按钮 disabled） |
| 资源类型 | resourceTypeDisplay | supported-types 拉来的 displayName |
| 资源名称 | originalName | 高亮关键字 |
| 删除人 | deletedBy | admin username |
| 删除时间 | deletedAt | `valueType: 'dateTime'`，dayjs 自动格式化 |
| 剩余保留天数 | daysUntilPurge | `< 7` 红色高亮 |
| 操作 | - | 「查看详情」/「恢复」/「永久删除」全部 disabled |

### 5.5 目录树动态渲染（核心扩展机制）

```typescript
const { data: types } = await fetchSupportedTypes();
// 本需求交付时 types = []，目录树只显示「全部」
// REQ-104 对接第一个资源后自动出现该资源类型

const treeData = [
  { key: 'ALL', title: '全部' },
  ...types.map(t => ({ key: t.type, title: t.displayName }))
];

// 点击树节点 → 切换 ProTable 的 resourceType 参数
// ALL 时 resourceType 不传，后端返回所有类型
```

### 5.6 本需求交付的前端能力（边界）

✅ **能用**：
- 目录树动态加载 supported-types
- 列表分页 + resourceType 过滤 + 关键字搜索 + 排序
- 列表行多选

❌ **预留但不启用**（按钮 disabled + tooltip「等待资源对接」）：
- 「批量恢复」/「批量永久删除」按钮
- 行内「恢复」/「永久删除」/「查看详情」操作

## 6. 测试策略

遵循项目 CLAUDE.md「测试穿透力」原则，必须有集成测试覆盖。

**测试模块归属**（重要）：

| 测试对象 | 测试类所在模块 | 原因 |
|---------|--------------|------|
| Adapter（`RecycleBinItemRepositoryAdapter`）集成测试 | **core 模块** | Adapter 在 core，core 内测试无跨模块扫描问题 |
| `ResourceType` / `RecycleBinItemStrategyRegistry` / 领域模型 | **core 模块** | 纯领域类型，与 admin/app 解耦 |
| Controller（`RecycleBinController`）`@WebMvcTest` | **admin 模块** | Controller 在 admin |
| AppService（`RecycleBinAppService`）单元测试 | **admin 模块** | AppService 在 admin |
| Assembler（`RecycleBinItemAssembler`） | **admin 模块** | Assembler 在 admin/application |

core 模块集成测试标准配置：
```java
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RecycleBinItemRepositoryAdapter.class)
@ActiveProfiles("test")
class RecycleBinItemRepositoryAdapterIT { ... }
```

admin 模块 `@WebMvcTest` 标准配置：`@AutoConfigureMockMvc(addFilters = false)` 禁用 Security Filter。

| 测试范围 | 类型 | 工具 | 覆盖目标 |
|---------|------|------|---------|
| **总览表 + 5 张详情表 DDL 通过 ddl-auto 生成** | 集成测试 | `@DataJpaTest` + 真实 MySQL | 6 张表都能成功建表 |
| **总览表分页/过滤/排序** | 集成测试 | `@DataJpaTest` + `@Import(RecycleBinItemRepositoryAdapter.class)` | resourceType 过滤、keyword 模糊、白名单排序、默认排序 deletedAt DESC |
| **`ResourceType.toBizTypes()` 映射** | 单元测试 | JUnit | 5 个枚举值都返回正确 bizType 列表 |
| **`RecycleBinItemStrategyRegistry` 自动发现 + 重复注册检测** | 单元测试 | Mockito | 注入 mock 策略列表，验证按 ResourceType 注册 + 重复时抛 IllegalStateException |
| **`RecycleBinController` 列表查询 + supported-types** | `@WebMvcTest` | MockMvc + `@AutoConfigureMockMvc(addFilters=false)` | 参数解析、异常处理、响应序列化、Long 时间戳往返 |
| **`daysUntilPurge` 计算列** | 单元测试 | JUnit | 边界：构造 `restoreDeadline = now + 6.5 天` 断言 `= 7`；`now` 断言 `= 0`；`now - 1 天`（不应出现）兜底返回 `0` |
| **白名单排序校验** | 单元测试 | JUnit | 非白名单字段抛 BusinessException 含中文错误消息 |
| **`parseResourceType` 解析逻辑** | 单元测试 | JUnit | null/blank/"ALL" → null；合法枚举值 → ResourceType；非法值 → BusinessException(400) |
| **端到端：DDL + Adapter + 序列化** | 集成测试 | `@DataJpaTest` + 真实数据 | 手动 INSERT 总览表 + 查询 → 验证响应字段映射 |
| **前端目录树渲染** | 单元测试 | jest + testing-library | 空类型集（本需求交付态）+ 有类型集两种场景 |
| **前端 ProTable 交互** | 单元测试 | jest + testing-library | 分页/过滤/排序/多选行为 |
| **前端类型检查** | 类型检查 | `npx tsc --noEmit` | TS 类型安全（jest 不跑类型） |

**关键集成测试**：必须建一个完整的「物理删除原表 → 写入 `recycle_bin` + `ip_series_deleted` → 查询总览列表 → 验证返回」的端到端测试（虽然写入路径不在本需求实现，但测试可以通过 SQL 直接造数据，验证读取链路）。

## 7. 已知约束与非目标

| 项 | 是否在 REQ-100 内 | 留给谁 |
|----|-----------------|--------|
| 单条/批量恢复端点 + UI 按钮 | ❌ | REQ-103 |
| 单条/批量永久删除端点 + UI 按钮 | ❌ | REQ-102 |
| 定时清理（30 天到期物理删除） | ❌ | REQ-101 |
| 各资源 DELETE 端点改造（移入回收站） | ❌ | REQ-104~108 |
| 5 个资源策略 Bean 实现 | ❌ | REQ-104~108 |
| AdminUserHolder 基础设施（拿 username） | ✅ 已含（`Authentication.getName()` 直接取，不需要新建 Holder） | - |
| `restoreDeadline = deleted_at + 30天` 字段建好 + 写入时赋值 | ✅ | REQ-101 才用 |
| 本需求端到端验证 | ❌ 不验证 | REQ-104~108 对接资源后一起验证 |

## 8. 关键风险 & 缓解

| 风险 | 缓解 |
|------|------|
| 5 张 `_deleted` 表 PO + Repo 全建好但本需求不用，可能积灰 | 接受。这是"框架就位"的代价。REQ-104~108 必须基于这些表，提前建好避免后续返工 |
| `supported-types` 本需求交付时返回空集，前端页面"看起来没东西" | 接受。等 REQ-104 第一个资源对接后立即丰富。集成测试时 SQL 直接造数据验证 |
| 策略接口签名定义但无实现，编译期可能出问题 | 接口本身就是契约，无实现也能编译。`RecycleBinItemStrategyRegistry` 接受 `List<RecycleBinItemStrategy<?>>`，空列表也能注入 |
| `restoreDeadline` 字段建好但 REQ-101 才用，可能被遗忘 | 在字段注释中标注「REQ-101 用」；本需求范围内 `RecycleBinItem` 是纯数据持有对象（仅 getter + reconstruct 构造器），`restoreDeadline` 由调用方在构造时显式传入（如 `new RecycleBinItem(..., deletedAt.plusDays(30))`）；行为方法（如 `withRestoreDeadline()`）待 REQ-104~108 实现 save 路径时补 |
| `original_id` 恢复时与原表 AUTO_INCREMENT 冲突 | MySQL 支持显式指定 ID（`INSERT INTO xxx (id, ...) VALUES (...)`），即使该 ID 已被 AUTO_INCREMENT 跳过也能恢复；前提是回收站期间该 ID 没被新行占用（由 `uk_resource_original` 唯一约束 + 业务约定保证） |
| 策略 Bean 注入失败（接口不兼容） | 注册中心构造函数显式校验 + 重复注册检测，启动时立即报错而非运行时崩 |

## 9. 文档维护机制（双向引用）

### 9.1 REQ-100 作为「单一真相源」

本 PRD 是回收站系统的"宪法"，包含：
- 表结构 DDL（5 张 `_deleted` + 1 张总览）
- `RecycleBinItemStrategy<T>` 接口契约（含方法签名语义、批量默认实现）
- `ResourceType` 枚举与 bizType 映射
- 术语表与已固化的决策（"恢复进 INACTIVE"、"强校验拒绝"、"拒绝级联删子分类"）

后续需求**不重复设计**，只描述"在 REQ-100 框架下我做什么具体动作"。

### 9.2 后续 PRD 顶部强制写「前置依赖」段

REQ-101/102/103/104~108 各自的 PRD 顶部固定写：

```markdown
## 前置依赖

- 本需求基于 [REQ-100 通用回收站系统](./req-100-recycle-bin.md) 的框架实现
- 复用契约：
  - `RecycleBinItemStrategy<T>` 接口（详见 REQ-100 第 4.1.4 节）
  - `RecycleBinItemStrategyRegistry` 注册中心
  - 5 张 `_deleted` 详情表 PO + Repo（详见 REQ-100 第 3.2 节）
  - `ResourceType` 枚举与 bizType 映射（详见 REQ-100 第 4.1.2 节）
- 本需求的增量工作：
  - ...（具体动作）
```

### 9.3 REQ-100 对接清单（活的 changelog，REQ-104~108 完成时勾选）

| 资源 | 状态 | 策略 Bean | 对接需求 |
|------|------|----------|---------|
| IP 系列 | ⬜ 未对接 | - | REQ-104 |
| 卡牌模板 | ⬜ 未对接 | - | REQ-105 |
| 题库 | ⬜ 未对接 | - | REQ-106 |
| 知识分类 | ⬜ 未对接 | - | REQ-107 |
| 知识条目 | ⬜ 未对接 | - | REQ-108 |

### 9.4 `docs/requirements.md` 备注列补充（REQ-100 实现完成时执行）

REQ-101~108 各自的备注列追加（具体内容见下方 8 条 diff）：

#### REQ-101（定时清理任务）

> Spring `@Scheduled` 定时任务，每日执行，物理删除 `recycle_bin` 中 `deleted_at < now() - 30天` 的记录（含关联文件清理）。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：复用 `restore_deadline` 字段、5 张 `_deleted` 详情表 + 总览索引表结构、文件清理走 `ResourceType.toBizTypes()` 映射**

#### REQ-102（手动永久删除）

> 管理端回收站页面支持手动永久删除，二次确认弹窗。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：实现 `RecycleBinAppService.purge/batchPurge` 已留后的方法签名 + 行内「永久删除」按钮启用 + `RecycleBinItemStrategy.purge` 策略（含 bizType 文件清理）**

#### REQ-103（通用恢复框架）

> 实现 `RecycleBinAppService.restore/batchRestore` 已留后的方法签名 + 行内「恢复」按钮启用 + 通用恢复流程（`_deleted` 详情表 → 校验关联 → INSERT 原表 INACTIVE → DELETE 详情表）。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：「恢复后强制 INACTIVE」契约由 REQ-100 固化（4.1.5 节）**

#### REQ-104（IP 系列对接回收站）

> DELETE 改为移入回收站；实现 `IpSeriesRecycleBinStrategy implements RecycleBinItemStrategy` Bean（注册中心自动发现）；恢复时校验原数据完整性。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：策略接口契约（4.1.4 节）、`ip_series_deleted` 表结构（3.2.1 节）、强校验拒绝规则**

#### REQ-105（卡牌管理对接回收站）

> DELETE 改为移入回收站；实现 `CardTemplateRecycleBinStrategy` Bean；恢复时校验 IP 系列仍存在。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：策略接口契约、`card_template_deleted` 表结构（3.2.2 节）、强校验拒绝规则（IP 系列不存在则恢复失败）**

#### REQ-106（题库管理对接回收站）

> DELETE 改为移入回收站；实现 `QuestionRecycleBinStrategy` Bean；恢复时处理分类关联 + JSON 字段（`answer`/`options`/`tags`）。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：策略接口契约、`question_deleted.related_data` JSON 快照格式 `{"categoryIds":[...]}`**

#### REQ-107（分类管理对接回收站）

> DELETE 改为移入回收站；实现 `KnowledgeCategoryRecycleBinStrategy` Bean；恢复时校验父级仍存在 + 同级名称唯一。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：策略接口契约、`knowledge_category_deleted` 表结构（3.2.4 节）、「拒绝删除有子分类的父分类」约束（4.1.5 节）**

#### REQ-108（知识条目管理对接回收站）

> DELETE 改为移入回收站；实现 `KnowledgeItemRecycleBinStrategy` Bean；恢复时处理分类多对多关联 + `content_html` 字段。**设计时必须参考 [REQ-100 PRD](prd/req-100-recycle-bin.md)：策略接口契约、`knowledge_item_deleted.related_data` JSON 快照格式**

### 9.5 `docs/overview.md` 维护「回收站系统」章节

```markdown
## 回收站系统

**主设计**：[REQ-100](prd/req-100-recycle-bin.md)

**当前状态**：
- 框架已就位（总览表 + 5 `_deleted` 表 + 策略接口 + 列表页）
- 已对接资源：无（等待 REQ-104~108）
- 定时清理：未实现（REQ-101）
- 手动永久删除：未实现（REQ-102）
- 恢复动作：未实现（REQ-103）

**已固化的决策**：
- DELETE 与 INACTIVE 并存（前者删入回收站，后者停用）
- 恢复后统一进 INACTIVE
- 强校验拒绝（被引用/有子节点）
- 总览表 + N 详情表（索引/详情分离）
```

### 9.6 CLAUDE.md 增加「Recycle Bin Convention」章节

在 CLAUDE.md 里加一节，类似现有的「Image Field Design Rules」/「Update API Null Semantics」：

```markdown
## Recycle Bin Convention

> 回收站系统框架由 REQ-100 建立。所有资源的 DELETE 操作（REQ-104~108）
> 必须遵循以下规范：

- DELETE = 物理移入回收站（不是 status=INACTIVE 软删除）
- INACTIVE 是"停用"语义，与 DELETE 解耦
- 恢复后资源统一进入 INACTIVE 状态（用户手动启用）
- 每种资源实现 `RecycleBinItemStrategy<T>` 策略 Bean，注册中心自动发现
- 详见 docs/prd/req-100-recycle-bin.md
```

## 10. 验收清单（DoD）

### 后端

- [ ] `recycle_bin` 总览表 + 5 张 `_deleted` 详情表 DDL 通过 ddl-auto 自动生成（启动不报错）
- [ ] `ResourceType` 枚举 + `toBizTypes()` / `displayName()` 方法
- [ ] `RecycleBinItemStrategy<T>` 策略接口（含单条/批量方法签名）
- [ ] `RecycleBinItemStrategyRegistry` 注册中心（自动发现 + 重复注册检测）
- [ ] `RecycleBinItemRepositoryPort` + `RecycleBinItemRepositoryAdapter`（findAll/findById 实现，save/deleteById 留 TODO）
- [ ] `RecycleBinController` + 2 个实现端点（list / supported-types）+ 5 个留后端点
- [ ] `RecycleBinAppService.list` / `supportedTypes` 实现 + 留后方法签名
- [ ] 全部单元测试通过
- [ ] 全部集成测试通过（真实 MySQL）

### 前端

- [ ] `/system/recycle-bin` 路由注册（「系统」菜单下）
- [ ] 左侧目录树（动态从 supported-types 拉取，本需求交付时为空只显示"全部"）
- [ ] ProTable（分页 + resourceType 过滤 + keyword + 排序）
- [ ] 行选择 + 「批量恢复」/「批量永久删除」按钮 disabled + tooltip
- [ ] `npx tsc --noEmit` 通过
- [ ] jest 单元测试通过

### 文档

> **执行时机**：文档更新分两类，避免顺序混乱
> - **写 PRD 时**（brainstorming 收尾，立即执行）：第 1 项（本文档）、第 6 项（requirements.md 状态切到 `confirmed`）
> - **实现完成后**（REQ-100 全部代码合入并测试通过后执行）：第 2/3/4/5 项，因为这些文档要引用最终实现的类名/路径/接口签名，提前写会过时

- [ ] `docs/prd/req-100-recycle-bin.md` 本文档（**brainstorming 收尾已写**）
- [ ] `docs/requirements.md` REQ-100 状态：`idea → confirmed`（**brainstorming 收尾执行**，实现完成时由用户更新到 `done`）
- [ ] `docs/overview.md` 增加回收站章节（**实现完成后执行**，引用最终类路径）
- [ ] `docs/features.md` 增加管理端回收站功能描述（**实现完成后执行**，引用最终前端路由）
- [ ] `CLAUDE.md` 增加「Recycle Bin Convention」章节（**实现完成后执行**，引用最终接口签名）
- [ ] `docs/requirements.md` REQ-101~108 备注列追加「设计时必须参考 REQ-100 PRD」（**实现完成后执行**，建立完整双向链）

### 不在验收清单（明确不做）

- 手动测试端到端：本需求交付时 supported-types 为空，无资源可删入回收站；端到端验证留到 REQ-104~108
- 性能压测：列表查询走单表 + 索引，预期无瓶颈
- 国际化：管理端中文，无 i18n 需求
