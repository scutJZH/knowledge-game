# 已知技术债

> 记录已确认但暂缓修复的问题，后续迭代按触发条件处理。

## DD-1: QuestionCategoryRelationJpaRepository JPQL 缺少集成测试

- **发现日期：** 2026-06-16
- **来源需求：** REQ-94（第二轮审查 N-1）
- **问题：** `countActiveQuestionsByCategoryId` 和 `findAllByQuestionIdIn` 的 JPQL（含 ad-hoc JOIN `QuestionCategoryRelationPO r JOIN QuestionPO q ON r.questionId = q.id`）仅在 `@Mock` 测试中运行，真实 SQL 执行零覆盖
- **暂缓原因：** core 是 Spring Boot Starter 库模块无主类，`@DataJpaTest` 缺少测试基础设施
- **修复计划：** admin 模块集成测试搭建时，通过 `@SpringBootTest` 天然覆盖 JPQL 真实执行
- **触发条件：** 下一次 admin 端 API 集成测试需求

## DD-2: CardTemplateAppService.batchActivate 方法体量不对称

- **发现日期：** 2026-06-16
- **来源需求：** REQ-94（第二轮审查 N-9）
- **问题：** `batchActivate` 23 行（含注释 5 行），`batchDeactivate` 仅 3 行；存在性校验+过滤 INACTIVE+校验+更新 4 步骤耦合在一起
- **暂缓原因：** 低优，不影响功能正确性
- **修复计划：** 抽出私有 `validateBeforeBatchActivate(List<CardTemplate>)` 方法
- **触发条件：** CardTemplateAppService 后续需求修改时

## DD-3: QuestionDomainService.validateActivatable 缺少 null 安全声明

- **发现日期：** 2026-06-16
- **来源需求：** REQ-94（第二轮审查 N-10）
- **问题：** 方法签名未声明 null 安全语义，若调用方误传 null `categoryMap` 会 NPE
- **暂缓原因：** 当前唯一调用方 `QuestionAppService.batchActivate` 保证非空
- **修复计划：** 方法签名加 `@NonNull` 注解或 `Objects.requireNonNull`
- **触发条件：** QuestionDomainService 后续需求修改时

## DD-4: KnowledgeCategoryDomainService.validateDelete 方法名与实际语义不符

- **发现日期：** 2026-06-16
- **来源需求：** REQ-94（第一轮 #11 补充）
- **问题：** 方法名 `validateDelete` 但实际做停用（deactivate）校验；`KnowledgeCategoryAppService.delete(Long id)` 也是同样问题（实际调用 `category.deactivate()`）
- **暂缓原因：** PRD 也使用此名，需 PRD + 代码 + 测试三方同步重命名，影响面大
- **修复计划：** 统一重命名为 `validateDeactivate` + AppService 的 `deactivate(Long id)`
- **触发条件：** 知识点分类相关新需求时统一处理

## DD-5: 前端手动测试发现的遗留问题（REQ-94 后端范围外）

- **发现日期：** 2026-06-17
- **来源：** REQ-94 手动测试

**DD-5.1 分类树勾选联动：** 勾选分类时自动勾选所有子分类。需确认是 Tree 组件预期行为还是 bug。

**DD-5.2 分类停用按钮文案：** 按钮已改为"停用"，确认弹窗同步更新。剩余：API 路径名称 `DELETE` 和后端错误消息"无法删除"仍使用删除术语，需后续统一。

**DD-5.3 分类缺少重新启用按钮：** 已停用的分类无"启用"操作入口。需在分类树/列表中为 INACTIVE 节点增加启用按钮，对应后端 `PUT /api/admin/knowledge-categories/{id}` 传 status=ACTIVE。

**DD-5.4 卡牌缺少批量操作 UI：** 后端已提供 `PUT /batch-activate` 和 `PUT /batch-deactivate`，前端卡牌管理页未对接。需在 ProTable 加批量选择 + 批量启用/停用按钮（参照题库管理页 `tableAlertOptionRender` 模式）。

- **暂缓原因：** REQ-94 明确 scope 不包含前端修改（PRD §1.3）
- **修复计划：** 后续前端需求统一处理
- **触发条件：** 分类管理页或卡牌管理页前端迭代时

## DD-6: verifyFileRef 在 4 个 AppService 中重复定义

- **发现日期：** 2026-06-17
- **来源需求：** REQ-93（代码审查）
- **问题：** `IpSeriesAppService`、`KnowledgeCategoryAppService`、`CardTemplateAppService`、`UserAppService` 四个类中 `verifyFileRef(Long, String)` 方法完全一致，仅 `expectedBizType` 常量不同
- **暂缓原因：** PRD 明确 YAGNI，当前 4 个 bizType 均共享同一校验模板
- **修复计划：** 当某 bizType 需要额外 metadata 校验字段（如 categoryId）时，抽 `FileRefVerifier` 工具类或策略接口
- **触发条件：** 新聚合根接入 FileRef 时，或现有 bizType 需要扩展 metadata 校验逻辑时

## DD-7: admin/app FileController 错误处理不对称

- **发现日期：** 2026-06-16
- **来源需求：** REQ-93（第一轮审查 #13）
- **问题：** admin 端 `FileController.getCredential` 用 try-catch 捕获 FeignException 包装为 BusinessException，app 端直接调用无 try-catch。两端逻辑相同的代码风格不一致
- **暂缓原因：** YAGNI，两个 Controller 维护成本可接受
- **修复计划：** 统一抽取共享类或基类处理 Feign 异常
- **触发条件：** 新增第三个调用方或现有调用方出现 Feign 异常未处理的问题时

## DD-8: 文件上传磁盘孤儿无回滚

- **发现日期：** 2026-06-16
- **来源需求：** REQ-93（第二轮审查 R2-6）
- **问题：** `FileAppService.uploadFile` 流程：磁盘存储 → DB 保存（事务）→ 凭证消费（内存）。若 DB 保存失败或凭证过期，磁盘文件已保存但事务回滚，产生孤儿文件。`batchUploadFiles` 更严重——N 个文件全存盘后才回滚
- **暂缓原因：** 历史问题（REQ-83 引入），非 REQ-93 新增
- **修复计划：** 可选方案：① 凭证消费前置（先消费再存盘）；② `@TransactionalEventListener` 监听回滚事件删除磁盘文件；③ `cleanupDeletedFiles` 定期清理补充孤儿检测
- **触发条件：** 文件服务可靠性优化需求

## DD-9: UploadCredentialService 凭证过期清理有 2 倍宽限期

- **发现日期：** 2026-06-16
- **来源需求：** REQ-93（第二轮审查 R2-9）
- **问题：** `safetyThreshold = now - expireMinutes * 60 * 2`，已过期凭证需等 2 倍过期时间才被清理。设计意图可能是给传输中的文件 buffer，但未在 javadoc 说明。metadata 中可能含敏感信息（userId），滞留时间不确定
- **暂缓原因：** 历史设计（REQ-83 引入），低优
- **修复计划：** javadoc 中说明 safetyThreshold 设计意图；评估是否缩减为 1.5 倍或 1 倍
- **触发条件：** UploadCredentialService 重构或迁移 Redis 时（与 REQ-81 协同）

## DD-10: 4 个聚合根的 Converter 单元测试覆盖但缺少 @DataJpaTest

- **发现日期：** 2026-06-17
- **来源需求：** REQ-93（第二轮审查 R2-12 + 集成测试补充）
- **问题：** `@JdbcTypeCode(SqlTypes.JSON)` 的 MySQL JSON 列仅在 file 模块有 `@DataJpaTest` 覆盖（`FileInfoJpaRepositoryTest`），core 模块 4 个 PO 的 metadata 相关字段（`cover_image_file_id` 等）无真实 SQL 执行验证。纯 Mock 测试无法发现 JSON 序列化往返的类型转换问题（如 Long→Integer）
- **暂缓原因：** core 是 Spring Boot Starter 库模块，`@DataJpaTest` 缺少测试基础设施（同 DD-1）
- **修复计划：** admin 或 app 模块集成测试搭建时，通过 `@SpringBootTest` 天然覆盖 JPA 真实执行
- **触发条件：** 下一次 admin/app 端 API 集成测试需求

## DD-11: 列表查询 code/name 字段 LIKE 全模糊匹配的性能上限

- **发现日期：** 2026-06-18
- **来源需求：** REQ-86（code 搜索 + 列头排序）
- **问题：** IpSeries/CardTemplate/KnowledgeCategory 的 `findByConditions` 中 `code`/`name` 字段使用 `cb.like(root.get("code"), "%" + code + "%")` 双侧通配模糊匹配，B-tree 失效会全表扫描。当前数据量 < 5k 行时可接受（< 10ms），但数据量增长后会出现明显性能下降
- **暂缓原因：** 当前数据量小，性能可接受；优化方案需评估索引策略或迁移到 ES（增加架构复杂度）
- **修复计划：** 评估方向：① 改为前缀匹配 `code + "%"`（牺牲模糊匹配能力）；② 加全文索引 `FULLTEXT INDEX`（MySQL 8.0+ 支持 ngram 分词中文）；③ 数据量 > 10 万行时迁移到 Elasticsearch
- **触发条件：** 任一表数据量 > 10 万行，或 LIKE 查询 P95 延迟 > 100ms 时

## DD-12: SortFieldSpec 错误消息中 allowedFields 顺序不稳定

- **发现日期：** 2026-06-18
- **来源需求：** REQ-86（ISSUE-8 黑盒测试发现）
- **问题：** PRD §4.3 错误消息示例是 `[code, name, status, createdAt, updatedAt]` 有序列表格式；但 `SortFieldSpec` 实现使用 `Set<String>.toString()`，HashSet 顺序不可控，错误消息中字段顺序随机。功能正确但显示不一致（前端可读性差，日志难复现）
- **暂缓原因：** 不影响功能正确性（前端只关心字段是否在白名单内）
- **修复计划：** 改造为 `new ArrayList<>(allowedFields)` 或排序后再拼接，保证消息稳定
- **触发条件：** 下次 SortFieldSpec 重构或前端依赖稳定顺序时

## DD-11: 列表查询 code/name 字段 LIKE 全模糊匹配的性能上限

- **发现日期：** 2026-06-18
- **来源需求：** REQ-86（code 搜索 + 列头排序）
- **问题：** IpSeries/CardTemplate/KnowledgeCategory 的 `findByConditions` 中 `code`/`name` 字段使用 `cb.like(root.get("code"), "%" + code + "%")` 双侧通配模糊匹配，B-tree 失效会全表扫描。当前数据量 < 5k 行时可接受（< 10ms），但数据量增长后会出现明显性能下降
- **暂缓原因：** 当前数据量小，性能可接受；优化方案需评估索引策略或迁移到 ES（增加架构复杂度）
- **修复计划：** 评估方向：① 改为前缀匹配 `code + "%"`（牺牲模糊匹配能力）；② 加全文索引 `FULLTEXT INDEX`（MySQL 8.0+ 支持 ngram 分词中文）；③ 数据量 > 10 万行时迁移到 Elasticsearch
- **触发条件：** 任一表数据量 > 10 万行，或 LIKE 查询 P95 延迟 > 100ms 时


## DD-14: 出端口命名不一致（Port 后缀 vs 无后缀）

- **发现日期：** 2026-06-19
- **来源需求：** REQ-100（PRD 审查 #10）
- **问题：** `core/domain/port/outbound/` 下出端口命名两种风格并存：
  - 带后缀（4 个）：`CardTemplateRepositoryPort` / `IpSeriesRepositoryPort` / `KnowledgeCategoryRepositoryPort` / `UserRepositoryPort`
  - 无后缀（2 个）：`KnowledgeItemRepository` / `QuestionRepository`
- **暂缓原因：** 命名一致性属于代码风格，不影响功能；现有调用面广，统一改名涉及大量 import 修改
- **修复计划：** 统一为 `XxxRepositoryPort`（多数派 + 语义更清晰）。改名时全工程一次性替换 + 编译验证
- **触发条件：** 下一次 DDD 大重构，或新增出端口时（强制带 Port 后缀作为新规范）
- **当前规范：** 新增出端口一律带 `Port` 后缀（如 REQ-100 的 `RecycleBinItemRepositoryPort`）

## DD-15: CLAUDE.md Dependency Rules 表与实际代码不一致（domain → common.exception 依赖）

- **发现日期：** 2026-06-19
- **来源需求：** REQ-100（PRD 审查 #8）
- **问题：** CLAUDE.md 「Dependency Rules」表声明 domain 层「May depend on: JDK only」，但实际所有 5 个 DomainService（`CardTemplateDomainService` / `IpSeriesDomainService` / `KnowledgeCategoryDomainService` / `KnowledgeItemDomainService` / `QuestionDomainService`）和 `SortFieldSpec` 均 `import com.knowledgegame.core.common.exception.BusinessException`。`common.exception` 既不在 JDK 内，也不在 domain 自身
- **暂缓原因：** 不影响功能；`common.exception.BusinessException` 是项目通用基础类，与 DDD「领域层可依赖通用基础类」的实践一致（只是 CLAUDE.md 表述过于严格）
- **修复计划：** 修订 CLAUDE.md Dependency Rules 表，将 domain 层「May depend on」改为「JDK + core 自身的 common 包（BusinessException 等通用基础类）」
- **触发条件：** 下一次 CLAUDE.md 文档审查，或新增 domain 层引入跨包依赖时（应明确允许 common 基础类）

## DD-16: Assembler 实际位置与 CLAUDE.md 规范冲突

- **发现日期：** 2026-06-19
- **来源需求：** REQ-100（task_plan.md 审查 #2）
- **问题：** CLAUDE.md「DDD Architecture」节声明「Assembler 属于 application 层，不应放在 api/dto 包中」，但实际所有 6 个现有 Assembler 都位于 `admin/api/assembler/`：`AdminUserAssembler` / `QuestionAssembler` / `CardTemplateAssembler` / `IpSeriesAssembler` / `KnowledgeCategoryAssembler` / `KnowledgeItemAssembler`
- **暂缓原因：** 不影响功能；统一迁移涉及大量 import 修改，且 `admin/application/` 目录目前不存在，需新建
- **修复计划：** 全量迁移 `admin/api/assembler/` → `admin/application/assembler/`，同步更新 CLAUDE.md 强制规范。或反向修订 CLAUDE.md，承认现状（Assembler 放 api 层）
- **触发条件：** 下一次 DDD 大重构，或新增 Assembler 时（**当前规范：跟随现状，放 `admin/api/assembler/`**，与 4-15 一致）
