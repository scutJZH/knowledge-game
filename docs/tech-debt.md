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
