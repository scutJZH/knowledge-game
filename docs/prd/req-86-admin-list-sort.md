# REQ-86：管理端分页查询统一排序支持（含 REQ-95 合并）

| 字段 | 值 |
|------|---|
| 需求编号 | REQ-86（合并 REQ-95） |
| 状态 | designed → 待开发 |
| 优先级 | 中 |
| 影响模块 | `knowledge-game-core`、`knowledge-game-admin`、`frontend/admin` |

---

## 1. 背景

当前管理端 4 个分页列表查询接口中：
- **Question** 已实现参数化排序（`SortField` 领域值对象 + Controller `sort`/`order` 参数 + Adapter `FIELD_MAPPING` 白名单静默回退），是唯一的参考实现
- **IpSeries / CardTemplate / KnowledgeCategory** 三个接口排序硬编码在 Adapter 中（`Sort.by(DESC, "createdAt")`），前端无法通过列头点击切换排序
- 三个接口均缺少按编码（`code`）搜索能力

（4 个分页接口：Question / IpSeries / CardTemplate / KnowledgeCategory；KnowledgeCategory 的 `/tree` 接口是树形查询，不属于分页列表）

REQ-95 要求"卡片管理页和 IP 管理页增加按编码搜索 + 列头排序"，与 REQ-86 的排序参数化高度重叠。本需求将两者合并实现，并确立为**列表查询排序的通用规范**，未来所有列表查询接口必须遵循。

## 2. 目标

1. 三个新接口（IpSeries/CardTemplate/KnowledgeCategory）支持 `sort`/`order` 参数化排序
2. Question 顺手统一为严格白名单校验（消除静默回退反模式）
3. IpSeries/CardTemplate 后端 + 前端增加 `code` 独立搜索框（AND 组合）
4. 前端 IpSeries/CardTemplate 两个 ProTable 页面启用列头排序
5. 建立**列表查询排序通用规范**，写入 CLAUDE.md / overview.md

## 3. 非目标

- 多字段排序（保持单字段，KnowledgeCategory 默认双字段仅在 fallback 时生效）
- 用户端（app）接口改造（仅 admin 模块；但 `SortField.parse` 放 domain VO 以备未来 app 复用）
- 其他管理端接口（如未来 shop/order）改造
- **前端 KnowledgeBase 接入列头排序**（当前是纯树形页面，无 ProTable 列表视图；但 KnowledgeCategory 后端 API 仍改造，属于通用规范的一部分，未来列表视图可用）

## 4. API 协议

### 4.1 通用排序协议

```
GET /api/admin/{resource}?...&sort={field}&order={asc|desc}&page=0&size=20
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `sort` | String | 否 | 各接口默认字段 | 排序字段名（PO 字段驼峰式，如 `createdAt`） |
| `order` | String | 否 | `desc` | 排序方向，`asc`/`desc`，大小写不敏感；非法值静默降级为 `desc` |
| `code`（REQ-95） | String | 否 | - | 编码模糊匹配（仅 IpSeries/CardTemplate） |

**字段非法处理**：`sort` 不在白名单内 → HTTP 400 + `BusinessException`，消息包含允许字段列表。**禁止静默回退**（消除 Question 现有反模式）。

### 4.2 各接口改造清单

| 接口 | 默认排序（sort 缺失） | 可排序字段白名单 | 新增查询参数 |
|------|---------------------|----------------|------------|
| `GET /api/admin/ip-series` | `createdAt DESC` | `code`, `name`, `status`, `createdAt`, `updatedAt` | `code`（模糊） |
| `GET /api/admin/card-templates` | `createdAt DESC` | `code`, `name`, `rarity`, `status`, `createdAt`, `updatedAt` | `code`（模糊） |
| `GET /api/admin/knowledge-categories` | `sortOrder ASC, createdAt DESC`（双字段保留） | `name`, `sortOrder`, `status`, `createdAt`, `updatedAt` | 无（无 code 字段） |
| `GET /api/admin/questions` | `createdAt DESC` | `createdAt`, `updatedAt`, `difficulty`（沿用现状） | 无（仅校验逻辑改造） |

### 4.3 错误响应

非法字段示例：
```json
{
  "code": 400,
  "message": "不支持的排序字段: foo，允许的字段: [code, name, status, createdAt, updatedAt]",
  "data": null
}
```

复用 `ResultCode.PARAM_ERROR`，不新增枚举值。

## 5. 核心设计

### 5.1 SortField 值对象增强（domain VO 自包含工厂方法）

**位置**：`backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/model/vo/SortField.java`（已存在，本需求增强）

**新增内容**：

1. **构造器防御性 null 校验**（值对象基础不变性）：
```java
public SortField(String field, Direction direction) {
    this.field = Objects.requireNonNull(field, "field 不能为 null");
    this.direction = Objects.requireNonNull(direction, "direction 不能为 null");
}
```

2. **新增静态工厂方法 `parse`**（替代独立 SortParamParser 工具类）：
```java
/**
 * 解析 sort/order 字符串为 SortField
 * - sort 为 null/blank → 返回 null（由 Repository 决定默认排序）
 * - sort 非空 → 方向按 asc/desc 大小写不敏感解析；非 asc 一律视为 desc（含 null）
 */
public static SortField parse(String sort, String order) {
    if (sort == null || sort.isBlank()) {
        return null;
    }
    Direction direction = "asc".equalsIgnoreCase(order)
            ? Direction.ASC : Direction.DESC;
    return new SortField(sort, direction);
}
```

3. **删除 `defaultSort()` 方法**（新架构下无调用方，避免死代码）。

**为什么放 SortField 自身而非独立工具类**：
- **避免 common → domain 反向依赖**：原方案 SortParamParser 在 `core/common/util/` 但返回 SortField（domain/vo），形成反向依赖。`EnumUtils` 是泛型工具不 import 具体 domain 类型，SortParamParser 硬 import SortField 是具体耦合
- **符合项目既定模式**：`FileRef.of(Long fileId, String url)` 是值对象自包含工厂方法的先例（含校验逻辑）。SortField.parse(sort, order) 与之同性质
- **减少新建文件**：合并到现有 VO，零新文件
- **domain VO 自带 parse 是 JDK 惯例**：如 `LocalDateTime.parse(String)`、`Integer.parseInt(String)`

**为什么不把默认排序固化进来**：默认排序策略因接口而异（IpSeries 单字段 / KnowledgeCategory 双字段），不应固化在通用 VO 里。各 Adapter 自己决定 fallback。

### 5.2 SortFieldSpec（新增，领域规则校验器）

**位置**：`backend/knowledge-game-core/src/main/java/com/knowledgegame/core/domain/spec/SortFieldSpec.java`

> ⚠ **`core/domain/spec/` 目录当前不存在，本需求新建。** CLAUDE.md 已在 DDD 分层规范中预留该子包用途为"业务规则校验"。

**职责**：校验客户端传入的 sort 字段是否在接口允许的白名单内，非法时抛 `BusinessException(400)`。

**接口**：
```java
public static SortField validate(SortField sortField, Set<String> allowedFields);
```

- 入参 sortField 为 null → 返回 null（由调用方决定默认排序）
- 入参字段在白名单内 → 返回原值
- 入参字段不在白名单 → 抛 `BusinessException(400, "不支持的排序字段: xxx，允许的字段: [a, b, c]")`

**DDD 合规性**：
- `domain/spec/` 是 CLAUDE.md 既定的"业务规则校验"位置
- 现有 4 个领域服务（QuestionDomainService 等）均 `import core.common.exception.BusinessException`，**`domain → common` 依赖是项目既定模式**，本类同样允许
- domain 层不引入任何 Spring/JPA 框架依赖，SortFieldSpec 仅依赖 JDK + domain VO + common.exception

### 5.3 SortFields 工具类（新增，infrastructure 层共享技术工具）

**位置**：`backend/knowledge-game-core/src/main/java/com/knowledgegame/core/infrastructure/adapter/support/SortFields.java`

> 新建 `infrastructure/adapter/support/` 子包，是 Adapter 内部的辅助工具，依赖 Spring Data 合规（infrastructure 层允许依赖技术框架）。与 `infrastructure/db/converter/`（MapStruct）性质类似：都是 infrastructure 层内部的技术工具。

**职责**：把领域 `SortField` 转换为 Spring Data `Sort`，供 4 个 RepositoryAdapter 共享，避免 4 份相同 toSpringSort 方法复制。

**接口**：
```java
public static Sort toSpringSort(SortField sortField);
```

实现：
```java
public static Sort toSpringSort(SortField sortField) {
    Sort.Direction dir = sortField.getDirection() == SortField.Direction.ASC
            ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(dir, sortField.getField());
}
```

**为什么不能放 domain**：返回类型 `org.springframework.data.domain.Sort` 是 Spring 框架类型，domain 层零框架依赖，禁止引入。

### 5.4 Repository Adapter 改造模式

以 `IpSeriesRepositoryAdapter` 为例：

```java
// 类变量：白名单
private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "code", "name", "status", "createdAt", "updatedAt"
);

@Override
public PageResult<IpSeries> findByConditions(String name, String code, IpSeriesStatus status,
                                              SortField sortField, int pageNumber, int pageSize) {
    // 1. 白名单校验（非法字段抛 400）
    SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
    // 2. null 走默认排序，非 null 走 SortFields 转换
    Sort springSort = (validated == null)
            ? Sort.by(Sort.Direction.DESC, "createdAt")
            : SortFields.toSpringSort(validated);

    Specification<IpSeriesPO> spec = (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            predicates.add(cb.like(root.get("name"), "%" + name + "%"));
        }
        if (code != null && !code.isBlank()) {
            predicates.add(cb.like(root.get("code"), "%" + code + "%"));  // REQ-95 新增
        }
        if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    };
    Page<IpSeriesPO> springPage = ipSeriesJpaRepository.findAll(spec,
            PageRequest.of(pageNumber, pageSize, springSort));
    // ... 转 PageResult<IpSeries> 不变
}
```

**关键变化**：
- 删除 Question 现有的 `FIELD_MAPPING` Map（领域字段名 = PO 字段名，零映射，简化）
- 用 `Set<String>` 替代 `Map<String, String>`，语义清晰
- `toSpringSort` 抽到 `SortFields` 工具类（4 个 Adapter 共享）
- 显式 null 检查走默认排序，非 null 走白名单校验
- KnowledgeCategory 默认排序保留双字段：`Sort.by(Order.asc("sortOrder"), Order.desc("createdAt"))`
- **删除 `SortField.defaultSort()`**（domain VO 中的死代码，新架构下无调用方）
- **`SortField` 构造器加防御性 null 校验**：`Objects.requireNonNull(field)` + `Objects.requireNonNull(direction)`。值对象基础不变性，避免下游 `SortFields.toSpringSort` 在 null direction 时隐式走 DESC 分支造成误判。现有调用方仅 `SortField.parse`（保证非 null），不破坏调用方

### 5.5 Repository Port 签名变更

| Port | 新签名 |
|------|--------|
| `IpSeriesRepositoryPort.findByConditions` | `(String name, String code, IpSeriesStatus status, SortField sortField, int pageNumber, int pageSize)` |
| `CardTemplateRepositoryPort.findByConditions` | `(String name, String code, Long ipSeriesId, CardRarity rarity, CardTemplateStatus status, SortField sortField, int pageNumber, int pageSize)` |
| `KnowledgeCategoryRepositoryPort.findByConditions` | `(String keyword, KnowledgeCategoryStatus status, Long parentId, SortField sortField, int pageNumber, int pageSize)` |
| `QuestionRepository.findByConditions` | 签名不变（已含 SortField），仅内部校验逻辑改造 |

### 5.6 AppService 调用链

```java
public PageResult<IpSeriesResponse> listIpSeries(String name, String code, String status,
                                                  String sort, String order,
                                                  int pageNumber, int pageSize) {
    IpSeriesStatus statusEnum = EnumUtils.valueOfNullable(IpSeriesStatus.class, status);
    SortField sortField = SortField.parse(sort, order);  // domain VO 自带工厂方法
    PageResult<IpSeries> domainPage = ipSeriesRepositoryPort.findByConditions(
            name, code, statusEnum, sortField, pageNumber, pageSize);
    // ... 转 PageResult<IpSeriesResponse> 不变
}
```

Question 的旧 `buildSortField` private 方法被替换为 `SortField.parse` 调用，**Question 改造完成后该方法删除**。

### 5.7 Controller 改造

新增 `sort`、`order` 查询参数（IpSeries/CardTemplate 还加 `code`）：

```java
@GetMapping
public Result<PageResult<IpSeriesResponse>> list(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) String order,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    PageResult<IpSeriesResponse> result = ipSeriesAppService.listIpSeries(
            name, code, status, sort, order, page, size);
    return Result.success(result);
}
```

## 6. 前端改造

### 6.1 改造目标页面（2 个）

| 页面 | 改造内容 |
|------|---------|
| `frontend/admin/src/pages/IpSeries/index.tsx` | 列头排序（白名单字段）+ `code` 搜索框 |
| `frontend/admin/src/pages/CardTemplate/index.tsx` | 列头排序（白名单字段）+ `code` 搜索框 |

**KnowledgeBase 前端不接入**：当前是纯树形页面（基于 `CategoryTree.tsx`，无 ProTable 列表视图），列头排序语义不适用。后端 KnowledgeCategory API 仍改造（属于通用规范），未来若新增列表视图立即可用。

### 6.2 列头排序实现细节

1. 为白名单列加 `sorter: true`（如 `code`、`name`、`createdAt` 列）
2. ProTable `request` 函数中把 ProTable 产出的 `{ field: 'ascend'|'descend' }` 转为 `sort=field&order=asc|desc`（复用 `QuestionBank/index.tsx:364-383` 的转换逻辑）
3. IpSeries/CardTemplate 的查询表单增加 `code` 搜索项

### 6.3 可点击排序的列（前端）

| 页面 | 可点击排序的列 |
|------|--------------|
| `IpSeries/index.tsx` | 编码、名称、状态、创建时间、更新时间 |
| `CardTemplate/index.tsx` | 编码、名称、稀有度、状态、创建时间、更新时间 |
| `QuestionBank/index.tsx` | 沿用（无改造） |

## 7. 错误处理

| 场景 | 处理 |
|------|------|
| `sort` 字段不在白名单 | 抛 `BusinessException(400)`，消息含允许字段列表；前端 toast 提示 |
| `order` 非 `asc`/`desc` | 静默降级为 `desc`（沿用 Question 现状，非致命错误不阻断） |
| `sort` 为空 | 用默认排序，不报错 |
| `code` 搜索为空字符串 | 跳过 Predicate，不参与查询 |
| **前端兼容性**：后端先上线，sort/order 参数为可选；若前端先上线而后端未上线，Spring MVC 默认忽略未识别 query param，请求仍成功（仅 sort 不生效）。**推荐后端先上线或同 PR 上线**。 |

## 8. 测试策略

### 8.1 后端单元测试

> **Mock 策略说明**：Adapter 的 `findByConditions` 单元测试用 Mockito，`jpaRepository.findAll(spec, pageRequest)` 不会执行真实 JPA。**用 `ArgumentCaptor<PageRequest>` 捕获 JPA 调用的第二个参数**，断言 `captor.getValue().getSort()` 的字段名和方向，不依赖真实数据库执行。这与现有 Adapter 测试风格（verify 简单委托）不同，需在测试类 Javadoc 中注明。

| 测试对象 | 关键用例 |
|---------|---------|
| `SortField` | parse(sort=null, *)→null；parse(sort="", *)→null；parse(sort="x", order="asc")→ASC；parse(sort="x", order="ASC")→ASC（大小写不敏感）；parse(sort="x", order=null)→DESC；parse(sort="x", order="invalid")→DESC；构造器 null field/direction 抛 NPE |
| `SortFieldSpec` | null 返回 null；白名单内返回原值；白名单外抛 400；消息含允许字段列表 |
| `SortFields` | ASC 方向正确转换；DESC 方向正确转换 |
| 各 `RepositoryAdapter` | `findByConditions_sortByXxx` 用 ArgumentCaptor 断言 Sort；`findByConditions_invalidSortField` 抛 400；`findByConditions_nullSort` 用默认 Sort；`findByConditions_code` 模糊匹配（仅 IpSeries/CardTemplate） |
| 各 `Controller @WebMvcTest` | `list_带sort和order_透传AppService`；`list_带code_透传AppService`（仅 IpSeries/CardTemplate） |
| `QuestionRepositoryAdapter` 回归 | 现有用例不破坏；新增 `invalidSortField` 抛异常用例；旧的 `buildSortField` 方法测试删除 |

### 8.2 后端集成测试（关键，不能省）

⚠ **核心陷阱**：Mock 测试用 `when(...).thenReturn(...)` 不会触发 JPA → SQL → 数据库的实际 ORDER BY，无法验证排序真的生效。

**位置**：`backend/knowledge-game-admin/src/test/java/.../integration/`（admin 模块，Port 的实际调用方；core 是 Spring Boot Starter 无 `@SpringBootApplication`，无法直接 `@DataJpaTest`）。

**基础设施**：
- 参照 `knowledge-game-file/src/test/java/.../FileInfoJpaRepositoryTest.java` 既有 `@DataJpaTest` 模式
- ⚠ **注意**：FileInfoJpaRepositoryTest 用了 `@AutoConfigureTestDatabase(replace = Replace.ANY)`，该注解会替换 DataSource 为嵌入式 DB（H2 等）。**集成测试不要照抄此注解**，否则 MySQL connector 被跳过，达不到 MySQL 函数兼容性验证目的。直接连接 `application-test.yml` 配置的 MySQL `knowledge-game-test` 库即可
- 用 `@SpringBootTest` + MySQL 测试库 或 `@DataJpaTest + @Import(XxxRepositoryAdapter.class)` 切片
- **测试库走 MySQL**（与生产一致），不用 H2（避免 `JSON_CONTAINS` 等 MySQL 函数兼容问题；本需求涉及的 3 个 PO 没有 JSON 列，但统一规范）

**测试库配置**（admin 模块 `application-test.yml` 需调整）：
- 当前配置 `jdbc:mysql://localhost:3306/knowledge-game`（**生产库，会污染数据**）+ `ddl-auto: none`
- 改为：`jdbc:mysql://localhost:3306/knowledge-game-test`（**与生产库隔离**）+ `ddl-auto: create-drop`（自动建表/清理）
- 本地开发需事先 `CREATE DATABASE knowledge_game_test DEFAULT CHARACTER SET utf8mb4`

**用例**：
- 不同 sort 字段实际产出正确的 ORDER BY SQL
- 白名单字段外的字段被拦截（端到端验证）
- code 模糊匹配实际命中数据库行

## 9. 文档同步

| 文件 | 同步内容 |
|------|---------|
| `CLAUDE.md` | 新增"列表查询排序规范"小节（指向本 PRD）；说明 `core/domain/spec/`、`core/infrastructure/adapter/support/` 两个新包的用途 |
| `docs/overview.md` | API 清单补 sort/order/code 参数；记录通用列表查询规范 |
| `docs/features.md` | 用户感知：列头点击排序、按编码搜索 |
| `docs/prd/req-86-admin-list-sort.md` | 本 PRD |
| `docs/tech-debt.md` | 新增"LIKE 全模糊匹配性能上限"技术债记录 |
| `docs/requirements.md` | REQ-86/REQ-95 状态 idea → done（收尾阶段，本需求完成后） |

## 10. 性能评估

### 10.1 LIKE '%xxx%' 全模糊匹配影响

`code` 字段使用 `LIKE '%xxx%'` 双侧通配，B-tree 索引失效，会触发全表扫描。

### 10.2 数据量预期与可接受性

| 表 | 预期数据量 | 全表扫描评估 |
|----|----------|-------------|
| `ip_series` | < 1k 行（IP 配置元数据） | 可接受（MySQL 1k 行扫描 < 5ms） |
| `card_template` | < 5k 行（每 IP 平均 50 张卡） | 可接受（< 10ms） |
| `knowledge_category` | < 1k 行（分类树） | 可接受 |

**结论**：当前数据量级别下全表扫描性能可接受，**本次不实施索引改造**。

### 10.3 未来数据量增长的应对

若未来单表数据量超过 10 万行，需评估：
- 改为前缀匹配 `LIKE 'xxx%'`（牺牲搜索灵活性换索引可用）
- 引入 ElasticSearch 等搜索引擎
- 作为独立技术债项跟踪（已记入 `docs/tech-debt.md`）

## 11. 影响面追溯表

### 11.1 IpSeriesRepositoryPort.findByConditions 签名变更

| 层 | 文件 | 改动类型 | 说明 |
|----|------|---------|------|
| Controller | `IpSeriesController.list` | 增加参数 | 加 `code`、`sort`、`order` 三个 query param |
| AppService | `IpSeriesAppService.listIpSeries` | 增加参数 + 调 SortField.parse | 透传 code/sort/order，调用 `SortField.parse` |
| Port | `IpSeriesRepositoryPort.findByConditions` | 签名变更 | 加 `String code`、`SortField sortField` |
| Adapter | `IpSeriesRepositoryAdapter.findByConditions` | 增加白名单 + 调 SortFieldSpec/SortFields | 加 `ALLOWED_SORT_FIELDS`、Spec 校验、code Predicate |
| 单元测试 | `IpSeriesRepositoryAdapterTest` | **新建文件** + 新增用例 | 当前不存在，新建；覆盖 sortBy/sortInvalid/nullSort/code 模糊 |
| 集成测试 | `IpSeriesListSortIntegrationTest`（新建） | 新建 | MySQL 真实排序验证 |
| Controller 测试 | `IpSeriesControllerTest`（已存在） | 新增用例 | sort/order/code 透传 |
| 前端 service | `services/ip-series.ts` | 加 query 参数 | listIpSeries 加 code/sort/order |
| 前端 page | `pages/IpSeries/index.tsx` | 加 sorter + 搜索项 | 白名单列 `sorter: true`，request 转换 sort，表单加 code |
| 前端测试 | `pages/IpSeries/__tests__/index.test.tsx` | 新增用例 | 点击列头触发 sort、code 搜索 |

### 11.2 CardTemplateRepositoryPort.findByConditions 签名变更

| 层 | 文件 | 改动类型 | 说明 |
|----|------|---------|------|
| Controller | `CardTemplateController.list` | 增加参数 | 加 `code`、`sort`、`order` |
| AppService | `CardTemplateAppService.listCardTemplates` | 加参数 + SortField.parse | 同 IpSeries |
| Port | `CardTemplateRepositoryPort.findByConditions` | 加 `String code`、`SortField sortField` | 同 IpSeries |
| Adapter | `CardTemplateRepositoryAdapter.findByConditions` | 加白名单 + 校验 + code Predicate | 同 IpSeries |
| 单元测试 | `CardTemplateRepositoryAdapterTest`（**已存在**，仅覆盖 REQ-94 方法） | **仅新增 findByConditions 排序用例** | 不新建文件；约 4-5 个用例（sortBy/sortInvalid/nullSort/code 模糊） |
| 集成测试 | `CardTemplateListSortIntegrationTest`（新建） | 新建 | MySQL 真实排序验证 |
| Controller 测试 | `CardTemplateControllerTest`（已存在） | 新增用例 | 同 IpSeries |
| 前端 service | `services/card-template.ts` | 加 query 参数 | 同 IpSeries |
| 前端 page | `pages/CardTemplate/index.tsx` | 加 sorter + 搜索项 | 同 IpSeries |
| 前端测试 | `pages/CardTemplate/__tests__/index.test.tsx` | 新增用例 | 同 IpSeries |

### 11.3 KnowledgeCategoryRepositoryPort.findByConditions 签名变更

| 层 | 文件 | 改动类型 | 说明 |
|----|------|---------|------|
| Controller | `KnowledgeCategoryController.list` | 加 `sort`、`order`（无 code） | - |
| AppService | `KnowledgeCategoryAppService.list` | 加参数 + SortField.parse | - |
| Port | `KnowledgeCategoryRepositoryPort.findByConditions` | 加 `SortField sortField` | - |
| Adapter | `KnowledgeCategoryRepositoryAdapter.findByConditions` | 加白名单 + 校验 + 默认双字段 | - |
| 单元测试 | `KnowledgeCategoryRepositoryAdapterTest`（**已存在**，仅覆盖 REQ-94 方法） | **仅新增 findByConditions 排序用例** | 不新建文件；约 3 个用例（sortBy/sortInvalid/nullSort） |
| Controller 测试 | `KnowledgeCategoryControllerTest`（已存在） | 新增用例 | sort/order 透传 |
| 集成测试 | `KnowledgeCategoryListSortIntegrationTest`（新建） | 新建 | MySQL 真实排序验证 |
| 前端 | **不改造** | KnowledgeBase 是树形页面，无列头语义 |

### 11.4 QuestionRepository.findByConditions（仅校验逻辑改造，签名不变）

| 层 | 文件 | 改动类型 |
|----|------|---------|
| Adapter | `QuestionRepositoryAdapter.toSpringSort` | 删除 `Map.getOrDefault` 静默回退，改 `SortFieldSpec.validate` + `SortFields.toSpringSort` |
| Adapter | `FIELD_MAPPING` Map | 删除（用 Set 替代） |
| AppService | `QuestionAppService.buildSortField` | 删除 private 方法，改用 `SortField.parse` |
| Domain VO | `SortField.defaultSort()` | 删除（死代码） |
| 测试 | `QuestionRepositoryAdapterTest` | 修改：原 "invalidField 静默回退 createdAt" 用例改为 "抛 400"；删除 buildSortField 测试 |

**行为等价性**（避免 reviewer 自己推导）：
- **旧路径**：`buildSortField(null)` → `SortField.defaultSort()` → Adapter `toSpringSort` 用 field="createdAt" 查 `FIELD_MAPPING["createdAt"]` → `Sort.by(DESC, "createdAt")`
- **新路径**：`SortField.parse(null, *)` → `null` → Adapter fallback `Sort.by(DESC, "createdAt")`
- **最终 SQL 完全一致**：`ORDER BY created_at DESC`。改造仅是架构重构，不改变运行时行为

## 12. 验收标准

### 12.1 后端 API 验收（4 个接口）

| 用例编号 | 接口 | 操作 | 预期 |
|--------|------|------|------|
| AC-1 | `GET /api/admin/ip-series?sort=code&order=asc` | 按 code 升序 | 返回列表按 code 字典序升序 |
| AC-2 | `GET /api/admin/ip-series?sort=foo` | 非法字段 | HTTP 400，消息含允许字段列表 |
| AC-3 | `GET /api/admin/ip-series` | 不传 sort | 按 createdAt DESC 默认排序 |
| AC-4 | `GET /api/admin/ip-series?code=IP001` | code 精确搜索 | 返回 code 含 IP001 的记录 |
| AC-5 | `GET /api/admin/card-templates?sort=rarity&order=desc` | 按 rarity 降序 | 返回按稀有度降序 |
| AC-6 | `GET /api/admin/knowledge-categories?sort=name` | order 未传，走默认方向 | 返回按 name DESC 排序（order 未传 → `SortField.parse` 解析 direction 为 null → 视为 DESC；与 §4.1 "order 默认 desc" 一致） |
| AC-7 | `GET /api/admin/knowledge-categories` | 不传 sort | 按 sortOrder ASC + createdAt DESC 双字段默认 |
| AC-8 | `GET /api/admin/questions?sort=createdAt&order=asc` | Question 严格校验回归 | 按 createdAt 升序 |
| AC-9 | `GET /api/admin/questions?sort=foo` | Question 非法字段 | HTTP 400（原静默回退改为严格） |

### 12.2 前端验收

| 用例编号 | 页面 | 操作 | 预期 |
|--------|------|------|------|
| AC-FE-1 | IpSeries 列表 | 点击"编码"列头 | 列表按 code 排序，列头显示升序箭头 |
| AC-FE-2 | IpSeries 列表 | 再次点击"编码"列头 | 切换为降序，箭头变向 |
| AC-FE-3 | IpSeries 列表 | code 搜索框输入 IP001 + 回车 | 请求带 `code=IP001` 参数，返回过滤后列表 |
| AC-FE-4 | CardTemplate 列表 | 点击"创建时间"列头 | 按创建时间排序 |
| AC-FE-5 | KnowledgeBase 页面 | 无列头排序 UI | 树形展示，不报错（API 改造不影响前端） |

### 12.3 通用规范验收

- [ ] `core/domain/spec/` 目录已创建
- [ ] `core/domain/model/vo/SortField.java` 已加 `parse` 静态工厂方法 + 构造器防御 + 删除 `defaultSort()`
- [ ] `core/infrastructure/adapter/support/SortFields.java` 已创建
- [ ] **不新建** `core/common/util/SortParamParser.java`（合并到 SortField）
- [ ] 4 个 Adapter 共享同一套校验+转换工具，零重复
- [ ] CLAUDE.md 已记录"列表查询排序规范"小节

## 13. 实施顺序（建议）

1. 增强 `SortField`（加 parse、加构造器防御、删 defaultSort）+ 单元测试（红/绿）
2. 新增 `SortFieldSpec` + 单元测试（红/绿）— domain/spec/ 新建目录
3. 新增 `SortFields` 工具类 + 单元测试（红/绿）— infrastructure/adapter/support/
4. 改造 `QuestionRepositoryAdapter` + 删除 `SortField.defaultSort()` + 删除 QuestionAppService.buildSortField（最小风险先验证模式可跑通）
5. 改造 `IpSeries/CardTemplate/KnowledgeCategory` Adapter + Port + AppService + Controller
6. 后端集成测试（admin 模块新建 integration/ 目录 + 调整 application-test.yml 用 knowledge-game-test 库 + ddl-auto: create-drop）
7. **后端先上线**（向后兼容旧前端）
8. 前端 IpSeries/CardTemplate 页面接入列头排序 + code 搜索
9. 前端测试补充
10. 文档同步（CLAUDE.md / overview.md / features.md / tech-debt.md）

## 14. 决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| 单字段 vs 多字段排序 | 单字段 | YAGNI；KnowledgeCategory 默认双字段保留作为 fallback |
| 字段名映射 | 直接用 PO 字段名（驼峰） | 简化，无需 FIELD_MAPPING |
| 非法字段处理 | 严格校验抛 400 | 消除 Question 静默回退反模式；前端调试更明确 |
| Question 是否同步改造 | 是 | 管理端排序行为统一，避免长期不一致 |
| SortFieldSpec 位置 | `core/domain/spec/`（新建） | CLAUDE.md 既定"业务规则校验"层；`domain → common` 依赖是项目既定模式 |
| **sort/order 解析逻辑位置** | **合并到 SortField 自身（`SortField.parse`）** | **避免 common → domain 反向依赖**；符合项目既定值对象自包含工厂方法模式（`FileRef.of`）；零新文件；JDK 惯例（LocalDateTime.parse） |
| toSpringSort 抽取位置 | `core/infrastructure/adapter/support/SortFields` | 4 个 Adapter 共享；不引入新顶层约定（adapter/ 子包内部工具） |
| 删除 `SortField.defaultSort()` | 是 | 新架构下无调用方，避免死代码 |
| `SortField` 构造器 null 防御 | 加 `Objects.requireNonNull(field/direction)` | 值对象基础不变性；防止下游 SortFields.toSpringSort 在 null direction 时隐式走 DESC 造成误判；现有调用方仅 SortField.parse，不破坏 |
| REQ-86 与 REQ-95 合并 | 合并 | 范围重叠，统一开发避免重复改造 |
| code 搜索语义 | 独立搜索框，AND 组合 | ProTable 表单习惯；精确过滤更符合管理员场景 |
| KnowledgeBase 前端是否接入 | 不接入（树形页面无列表视图） | API 层仍改造（通用规范），前端不破坏 |
| 集成测试位置 | admin 模块 src/test/integration | core 是 starter 无 @SpringBootApplication；admin 是 Port 调用方 |
| 集成测试数据库 | MySQL（与生产一致） | 避免 H2 MySQL 函数兼容问题；统一规范 |
| 集成测试库隔离 | `knowledge-game-test`（新建库） + `ddl-auto: create-drop` | 当前 application-test.yml 指向生产库 knowledge-game，会污染数据；需新建测试专用库 |
| 此架构定位 | 项目通用规范，未来列表查询必须遵循 | 用户明确要求 |
| 后端先上线 | 是 | 向后兼容旧前端（Spring 默认忽略未识别 query param） |
| 索引改造 | 本次不做 | 数据量小（< 5k 行），全表扫描可接受；未来 > 10 万行再评估，记入 tech-debt.md |
| 枚举字段排序语义 | 字典序，非游戏语义序（**已知行为，本次不修复**） | 枚举字段 `@Enumerated(EnumType.STRING)` 持久化，ORDER BY 走字符串字典序。`CardRarity`（声明 N→R→SR→SSR→SP）ASC 排序实际产出 N→R→SP→SR→SSR（SP 错位）；`Difficulty`（声明 EASY→MEDIUM→HARD）ASC 实际产出 EASY→HARD→MEDIUM（HARD 错位）；Status 类（ACTIVE/INACTIVE）字母序与语义一致，无影响。**Question 已有此行为**（非 REQ-86 引入），保持现状避免破坏。未来如需游戏语义序排序，需引入 ordinal 列或专用 CASE WHEN 表达式，作为独立技术债 |
