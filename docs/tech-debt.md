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

**DD-5.2 分类停用按钮文案：** 按钮显示"删除"而非"停用"，与后端软删除（status→INACTIVE）语义不一致。需改前端按钮文案和确认提示。

**DD-5.3 分类缺少重新启用按钮：** 已停用的分类无"启用"操作入口。需在分类树/列表中为 INACTIVE 节点增加启用按钮，对应后端 `PUT /api/admin/knowledge-categories/{id}` 传 status=ACTIVE。

**DD-5.4 卡牌缺少批量操作 UI：** 后端已提供 `PUT /batch-activate` 和 `PUT /batch-deactivate`，前端卡牌管理页未对接。需在 ProTable 加批量选择 + 批量启用/停用按钮（参照题库管理页 `tableAlertOptionRender` 模式）。

- **暂缓原因：** REQ-94 明确 scope 不包含前端修改（PRD §1.3）
- **修复计划：** 后续前端需求统一处理
- **触发条件：** 分类管理页或卡牌管理页前端迭代时

**DD-5.5 分类树前端预判逻辑：** 停用按钮置灰条件只看子节点是否存在（`children.length > 0`），不区分 ACTIVE/INACTIVE。导致子分类已停用时按钮仍置灰，后端 `countActiveByParentId` 的 ACTIVE-only 逻辑被前端拦截无法生效。

- **修复：** 前端改为判断 `children.filter(c => c.status === 'ACTIVE').length > 0` 或直接去掉前端预判，依赖后端校验返回的错误消息。
