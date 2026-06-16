# REQ-94 聚合根停用/启用前的关联校验

> 日期：2026-06-14
> 状态：designed
> 影响需求：REQ-16（IP 系列 CRUD API）、REQ-17（卡牌管理端 CRUD API）、REQ-65（知识库管理页）、题库管理 API
> 关联需求：REQ-89（知识点分类状态管理，本需求补充其停用/启用前置约束）

## 一、背景与目标

### 1.1 背景

当前四个聚合根的停用/启用接口缺失关联校验：

- **IP 系列停用**：`IpSeriesAppService.deleteIpSeries:108` 有 `// TODO: 检查是否有关联卡牌，有则不允许删除`，停用 IP 后 `card_template.ip_series_id` 仍指向已停用 IP，管理端无法识别"孤儿卡牌"。
- **知识点分类停用**：`KnowledgeCategoryDomainService.validateDelete` 仅校验"子分类数量"，未校验"是否有关联题目"。停用分类后 `question_category_relation.category_id` 仍指向已停用分类，题目仍可在该分类下被使用。
- **子分类校验粒度过粗**：现有 `countByParentId` 统计所有状态子分类（含 INACTIVE），但 INACTIVE 子分类本身已被停用，不应再阻止父级停用。
- **卡牌启用**：`CardTemplateAppService.updateCardTemplate` 在用户从 INACTIVE→ACTIVE 切换时，未校验关联的 IP 系列是否已启用。用户可能启用一张关联到已停用 IP 系列的卡牌，导致业务数据不一致（IP 不显示但卡牌可见）。
- **题目启用**：`QuestionAppService.batchActivate` 直接执行 `batchUpdateStatus`，未校验关联的分类是否都已启用。题目可能启用后指向已停用分类，用户端筛选/检索逻辑错乱。

### 1.2 目标

| 场景 | 校验规则 |
|---|---|
| 停用 IP 系列 | 存在 ACTIVE 卡牌 → 拒绝 |
| 停用知识点分类 | 存在 ACTIVE 子分类 → 拒绝；存在 ACTIVE 题目关联 → 拒绝 |
| 启用卡牌（单条/批量） | 关联的 IP 系列必须 ACTIVE，否则拒绝并提示 IP 名称 |
| 启用题目（批量） | 题目关联的所有分类必须全部 ACTIVE，否则拒绝并提示所有 INACTIVE 分类名 |

### 1.3 非目标

- ❌ 不修改前端 UI（前端已通过 `message.error` 显示后端返回的错误消息）
- ❌ 不调整 INACTIVE 实体对查询可见性的影响（属于 REQ-89 范畴）
- ❌ 不修改已停用实体的重新启用按钮逻辑（REQ-89 范畴）
- ❌ 不引入批量停用 IP 系列/知识点分类（当前都是单条停用）

## 二、范围

### 2.1 In Scope

| 层 | 改造内容 |
|---|---|
| core 出端口 | `CardTemplateRepositoryPort` 新增 `countActiveByIpSeriesId` + `findAllByIds`；`QuestionRepository` 新增 `countActiveByCategoryId` + `findAllCategoryIdsByQuestionIds`；`KnowledgeCategoryRepositoryPort` 新增 `countActiveByParentId`、`findAllByIdIn` |
| core 适配器 | 对应 Adapter + JPA Repository 实现新方法 |
| core 领域服务 | 新建 `IpSeriesDomainService.validateDeactivatable` + `validateCardTemplateActivatable`；`CardTemplateDomainService` 增加 `validateActivatable`；`KnowledgeCategoryDomainService.validateDelete` 改用 `countActiveByParentId` 并追加 Question 关联校验；`QuestionDomainService` 增加 `validateActivatable` |
| core 配置 | `KnowledgeGameCoreAutoConfiguration` 注册 `IpSeriesDomainService` Bean；`KnowledgeCategoryDomainService`、`QuestionDomainService`、`CardTemplateDomainService` Bean 调整注入 |
| admin API | 新增 `PUT /api/admin/card-templates/batch-activate`、`PUT /api/admin/card-templates/batch-deactivate`（对称 Question 现有接口，统一用 `@PutMapping`） |
| admin 应用层 | `IpSeriesAppService.deleteIpSeries` 调用 `validateDeactivatable`；`CardTemplateAppService.updateCardTemplate` 检测 INACTIVE→ACTIVE 时校验；新增 `batchActivate`；`QuestionAppService.batchActivate` 调用 `validateActivatable` |
| 测试 | 各层单元测试新增关联校验用例 |

### 2.2 Out of Scope

- 前端代码改造（消息由后端返回，前端 `message.error` 自动展示）
- 用户端（app）API 改造
- REQ-93（file_id 统一）独立需求

## 三、详细设计

### 3.1 业务规则

#### 3.1.1 停用前置校验

| 触发动作 | 校验规则 | 失败消息 |
|---|---|---|
| 停用 IP 系列 | `count(card_template WHERE ip_series_id = ? AND status = 'ACTIVE') > 0` | `IP 系列存在 {N} 张 ACTIVE 卡牌，无法停用` |
| 停用知识点分类 | `count(knowledge_category WHERE parent_id = ? AND status = 'ACTIVE') > 0` | `知识点分类下存在 {N} 个 ACTIVE 子分类，无法删除` |
| 停用知识点分类 | `count(question JOIN question_category_relation WHERE category_id = ? AND question.status = 'ACTIVE') > 0` | `知识点分类关联 {N} 道 ACTIVE 题目，无法删除` |

**校验顺序：** 子分类校验 → 题目校验。两个校验均通过才允许停用。

#### 3.1.2 启用前置校验

| 触发动作 | 校验规则 | 失败消息 |
|---|---|---|
| 启用卡牌（单条 update） | 当前 status=INACTIVE 且目标 status=ACTIVE 时，关联 IP 系列 status 必须 ACTIVE | `卡牌《{卡牌名}》关联的 IP 系列《{IP名}》处于停用状态，请先启用 IP 系列再启用卡牌` |
| 启用卡牌（批量 batchActivate） | 每个 INACTIVE 卡牌关联的 IP 系列必须 ACTIVE | `{N} 张卡牌因 IP 系列停用无法启用：卡牌《{卡牌名1}》(IP系列《{IP名1}》)、卡牌《{卡牌名2}》(IP系列《{IP名2}》)…` |
| 启用题目（批量 batchActivate） | 每个 INACTIVE 题目关联的所有分类必须全部 ACTIVE | `题目《{题目名}》关联的知识点分类《{分类名1}》、《{分类名2}》处于停用状态，请先启用对应分类再启用题目` |

**批量启用语义：** 原子操作——任一实体校验失败则整批拒绝，不部分启用。错误消息报告第一个失败的实体（用户修复后再次尝试可能暴露下一个失败）。

**单条卡牌场景说明：** `update` 接口 status 参数 null 或保持原值不触发校验；只有显式 INACTIVE→ACTIVE 切换才触发。

**INACTIVE 关联不阻止（停用侧）：** 已停用的卡牌/题目/子分类不阻塞父级停用（用户已主动废弃）。
**INACTIVE 父级必须先启用（启用侧）：** 子级启用前父级必须 ACTIVE，否则业务数据不一致。

### 3.2 出端口扩展

#### 3.2.1 `CardTemplateRepositoryPort`

新增方法：

```java
/**
 * 统计指定 IP 系列下的 ACTIVE 卡牌模板数量
 */
long countActiveByIpSeriesId(Long ipSeriesId);

/**
 * 根据 ID 批量查询卡牌模板（用于批量启用前的校验）
 */
List<CardTemplate> findAllByIdIn(List<Long> ids);
```

#### 3.2.2 `QuestionRepository`

新增方法：

```java
/**
 * 统计与指定分类关联的 ACTIVE 题目数量
 */
long countActiveByCategoryId(Long categoryId);

/**
 * 查询多道题目关联的全部分类 ID（去重），用于批量启用前的分类状态校验
 * 返回 Map<questionId, List<categoryId>>
 */
Map<Long, List<Long>> findCategoryIdsByQuestionIds(List<Long> questionIds);
```

#### 3.2.3 `KnowledgeCategoryRepositoryPort`

新增方法（删除旧 `countByParentId`，唯一调用方为 `validateDelete`）：

```java
/**
 * 统计指定父级下的 ACTIVE 子分类数量
 */
long countActiveByParentId(Long parentId);

/**
 * 根据 ID 批量查询分类（用于批量启用时的状态判定）
 */
List<KnowledgeCategory> findAllByIdIn(List<Long> ids);
```

### 3.3 JPA 实现

#### 3.3.1 `CardTemplateJpaRepository`

```java
@Query("SELECT COUNT(c) FROM CardTemplatePO c WHERE c.ipSeriesId = :ipSeriesId AND c.status = 'ACTIVE'")
long countActiveByIpSeriesId(@Param("ipSeriesId") Long ipSeriesId);

// findAllByIdIn 直接继承 JpaRepository.findAllById(Iterable<ID>)，无需自定义
```

#### 3.3.2 `KnowledgeCategoryJpaRepository`

```java
@Query("SELECT COUNT(c) FROM KnowledgeCategoryPO c WHERE c.parentId = :parentId AND c.status = 'ACTIVE'")
long countActiveByParentId(@Param("parentId") Long parentId);

// 删除 long countByParentId(Long parentId);

// findAllByIdIn 直接继承 JpaRepository.findAllById(Iterable<ID>)
```

#### 3.3.3 `QuestionCategoryRelationJpaRepository`

```java
@Query("""
       SELECT COUNT(DISTINCT r.questionId) FROM QuestionCategoryRelationPO r
       JOIN QuestionPO q ON r.questionId = q.id
       WHERE r.categoryId = :categoryId AND q.status = 'ACTIVE'
       """)
long countActiveQuestionsByCategoryId(@Param("categoryId") Long categoryId);

@Query("SELECT r FROM QuestionCategoryRelationPO r WHERE r.questionId IN :questionIds")
List<QuestionCategoryRelationPO> findAllByQuestionIdIn(@Param("questionIds") List<Long> questionIds);
```

`QuestionRepositoryAdapter.findCategoryIdsByQuestionIds` 调用 `findAllByQuestionIdIn` 后按 questionId 分组组装 Map。

### 3.4 领域服务

#### 3.4.1 新建 `IpSeriesDomainService`

**位置：** `core/domain/service/IpSeriesDomainService.java`

```java
package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IP 系列领域服务（跨聚合校验，纯 POJO）
 */
public class IpSeriesDomainService {

    private final CardTemplateRepositoryPort cardTemplateRepositoryPort;
    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;

    public IpSeriesDomainService(CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                  IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        this.cardTemplateRepositoryPort = cardTemplateRepositoryPort;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
    }

    /**
     * 校验 IP 系列是否可停用：存在 ACTIVE 卡牌时拒绝
     */
    public void validateDeactivatable(Long ipSeriesId) {
        long activeCardCount = cardTemplateRepositoryPort.countActiveByIpSeriesId(ipSeriesId);
        if (activeCardCount > 0) {
            throw new BusinessException("IP 系列存在 " + activeCardCount + " 张 ACTIVE 卡牌，无法停用");
        }
    }

    /**
     * 校验卡牌可否启用：关联的 IP 系列必须 ACTIVE
     * 单条场景：传入单个 CardTemplate
     */
    public void validateCardActivatable(CardTemplate card) {
        IpSeries ip = ipSeriesRepositoryPort.findById(card.getIpSeriesId())
                .orElseThrow(() -> new BusinessException("卡牌关联的 IP 系列不存在: " + card.getIpSeriesId()));
        if (ip.getStatus() != com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus.ACTIVE) {
            throw new BusinessException(
                    "卡牌《" + card.getName() + "》关联的 IP 系列《" + ip.getName() + "》处于停用状态，"
                            + "请先启用 IP 系列再启用卡牌");
        }
    }

    /**
     * 校验批量卡牌可否启用：返回所有不满足条件的卡牌错误描述
     * 批量场景：找到第一个失败即抛异常（消息含其 IP 名）
     */
    public void validateCardsActivatable(List<CardTemplate> cards) {
        for (CardTemplate card : cards) {
            IpSeries ip = ipSeriesRepositoryPort.findById(card.getIpSeriesId()).orElse(null);
            if (ip == null || ip.getStatus() != com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus.ACTIVE) {
                String ipName = ip != null ? ip.getName() : "(ID=" + card.getIpSeriesId() + ")";
                throw new BusinessException(
                        "卡牌《" + card.getName() + "》关联的 IP 系列《" + ipName + "》处于停用状态，"
                                + "请先启用 IP 系列再启用卡牌");
            }
        }
    }
}
```

**注册到 Spring 容器：** 在 `KnowledgeGameCoreAutoConfiguration` 添加 `@Bean` 方法。

#### 3.4.2 `KnowledgeCategoryDomainService.validateDelete` 改造

注入 `QuestionRepository`，调整子分类校验，追加题目校验：

```java
private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
private final QuestionRepository questionRepository;

// 构造器同步改造

public void validateDelete(Long categoryId) {
    // 子分类校验：仅统计 ACTIVE
    long activeChildCount = categoryRepositoryPort.countActiveByParentId(categoryId);
    if (activeChildCount > 0) {
        throw new BusinessException("知识点分类下存在 " + activeChildCount + " 个 ACTIVE 子分类，无法删除");
    }
    // 题目关联校验：仅统计 ACTIVE 题目
    long activeQuestionCount = questionRepository.countActiveByCategoryId(categoryId);
    if (activeQuestionCount > 0) {
        throw new BusinessException("知识点分类关联 " + activeQuestionCount + " 道 ACTIVE 题目，无法删除");
    }
}
```

#### 3.4.3 `QuestionDomainService` 新增 `validateActivatable`

注入 `KnowledgeCategoryRepositoryPort`：

```java
public void validateActivatable(Long questionId, Map<Long, List<Long>> questionToCategoryIds) {
    List<Long> categoryIds = questionToCategoryIds.getOrDefault(questionId, List.of());
    if (categoryIds.isEmpty()) {
        return; // 题目未关联任何分类，允许启用
    }
    List<KnowledgeCategory> categories = categoryRepositoryPort.findAllByIdIn(categoryIds);
    List<KnowledgeCategory> inactive = categories.stream()
            .filter(c -> c.getStatus() != KnowledgeCategoryStatus.ACTIVE)
            .toList();
    if (!inactive.isEmpty()) {
        String names = inactive.stream().map(KnowledgeCategory::getName).collect(Collectors.joining("、"));
        throw new BusinessException(
                "题目关联的知识点分类《" + names + "》处于停用状态，请先启用对应分类再启用题目");
    }
}
```

**批量场景：** `QuestionAppService.batchActivate` 预先用 `findCategoryIdsByQuestionIds` 拿到全部关联，逐题校验，第一个失败抛异常。

`KnowledgeGameCoreAutoConfiguration` 中相关 `@Bean` 方法同步调整注入参数。

### 3.5 应用层改造

#### 3.5.1 `IpSeriesAppService`

注入 `IpSeriesDomainService`，`deleteIpSeries` 调用前先校验：

```java
private final IpSeriesDomainService ipSeriesDomainService;

// 构造器同步改造

@Transactional
public void deleteIpSeries(Long id) {
    IpSeries ipSeries = ipSeriesRepositoryPort.findById(id)
            .orElseThrow(() -> new BusinessException("IP 系列不存在: " + id));
    ipSeriesDomainService.validateDeactivatable(id);  // 替换原 TODO
    ipSeries.deactivate();
    ipSeriesRepositoryPort.save(ipSeries);
}
```

#### 3.5.2 `CardTemplateAppService` 改造

注入 `IpSeriesDomainService`：

```java
private final IpSeriesDomainService ipSeriesDomainService;

// 构造器同步改造
```

`updateCardTemplate` 检测 INACTIVE→ACTIVE 切换时校验：

```java
@Transactional
public CardTemplateResponse updateCardTemplate(...) {
    CardTemplate template = cardTemplateRepositoryPort.findById(id)
            .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
    // ... existing code uniqueness check ...

    // 状态切换 INACTIVE → ACTIVE 时校验 IP 系列状态
    boolean activating = status == CardTemplateStatus.ACTIVE
            && template.getStatus() != CardTemplateStatus.ACTIVE;
    if (activating) {
        ipSeriesDomainService.validateCardActivatable(template);
    }

    template.update(code, name, rarity, description, status, imageUrl);
    CardTemplate saved = cardTemplateRepositoryPort.save(template);
    return assembleDetailResponse(saved);
}
```

新增 `batchActivate`：

```java
@Transactional
public void batchActivate(List<Long> ids) {
    // ids 非空由 BatchStatusRequest.@NotEmpty @Size 在 DTO 层保证，AppService 不重复校验
    List<CardTemplate> cards = cardTemplateRepositoryPort.findAllByIdIn(ids);
    if (cards.size() != ids.size()) {
        throw new BusinessException("部分卡牌 ID 不存在");
    }
    // 仅对当前 INACTIVE 的卡牌校验（已 ACTIVE 跳过）
    List<CardTemplate> toActivate = cards.stream()
            .filter(c -> c.getStatus() != CardTemplateStatus.ACTIVE)
            .toList();
    ipSeriesDomainService.validateCardsActivatable(toActivate);
    // 调用 Repository 批量更新（新增 Repository 方法 batchUpdateStatus）
    cardTemplateRepositoryPort.batchUpdateStatus(ids, CardTemplateStatus.ACTIVE);
}

@Transactional
public void batchDeactivate(List<Long> ids) {
    // ids 非空由 DTO 层保证
    cardTemplateRepositoryPort.batchUpdateStatus(ids, CardTemplateStatus.INACTIVE);
}
```

**新增 Repository 方法：** `CardTemplateRepositoryPort.batchUpdateStatus(List<Long> ids, CardTemplateStatus status)`。

**批量上限：** `BatchStatusRequest.ids` 由 DTO 层 `@NotEmpty` + `@Size(max=100)` 双重约束。QuestionController 现有接口同步收紧（现状只有 `@NotEmpty`），避免单次 SQL IN 子句过长 + 校验阶段的内存加载压力。上限 100 条依据：管理端默认分页 10/20/50，跨页选择最大场景约 50；留 2x 余量到 100。超出由前端分批调用。

#### 3.5.3 `QuestionAppService.batchActivate` 改造

注入 `QuestionDomainService.validateActivatable` 调用（空列表由 DTO 层 `@NotEmpty` 保证）：

```java
@Transactional
public void batchActivate(List<Long> ids) {
    // ids 非空由 BatchStatusRequest.@NotEmpty 在 DTO 层保证
    // 查询所有题目的分类关联（不分状态）
    Map<Long, List<Long>> questionToCategoryIds = questionRepository.findCategoryIdsByQuestionIds(ids);
    // 逐题校验，第一个失败抛异常
    for (Long id : ids) {
        questionDomainService.validateActivatable(id, questionToCategoryIds);
    }
    // 全部通过则执行
    questionRepository.batchUpdateStatus(ids, QuestionStatus.ACTIVE);
}
```

### 3.6 错误码

复用现有 `BusinessException` + `ResultCode.BUSINESS_ERROR`，前端 `message.error` 自动展示消息内容。

### 3.7 Controller 改造

新增 `CardTemplateController` 的两个批量接口，HTTP 方法用 `@PutMapping` 与 `QuestionController` 现有批量接口一致（`PUT /batch-activate`、`PUT /batch-deactivate`，对应"更新资源状态"语义）：

```java
@PutMapping("/batch-activate")
public Result<Void> batchActivate(@Valid @RequestBody BatchStatusRequest request) {
    appService.batchActivate(request.getIds());
    return Result.success(null);
}

@PutMapping("/batch-deactivate")
public Result<Void> batchDeactivate(@Valid @RequestBody BatchStatusRequest request) {
    appService.batchDeactivate(request.getIds());
    return Result.success(null);
}
```

**`BatchStatusRequest` 复用策略：** 现有 `com.knowledgegame.admin.api.dto.request.BatchStatusRequest` 已位于 admin 模块共享的 `api/dto/request/` 目录（非 question 子目录），CardTemplateController 直接 import 即可，无需移动或新建。

## 四、影响分析

### 4.1 受影响文件清单

**后端 core 层：**

| 文件 | 操作 |
|---|---|
| `domain/port/outbound/CardTemplateRepositoryPort.java` | 新增 `countActiveByIpSeriesId`、`findAllByIdIn`、`batchUpdateStatus` |
| `domain/port/outbound/QuestionRepository.java` | 新增 `countActiveByCategoryId`、`findCategoryIdsByQuestionIds` |
| `domain/port/outbound/KnowledgeCategoryRepositoryPort.java` | 新增 `countActiveByParentId`、`findAllByIdIn`；删除 `countByParentId` |
| `domain/service/IpSeriesDomainService.java` | 新建（含 validateDeactivatable、validateCardActivatable、validateCardsActivatable） |
| `domain/service/KnowledgeCategoryDomainService.java` | 注入 QuestionRepository；validateDelete 改造 |
| `domain/service/QuestionDomainService.java` | 注入 KnowledgeCategoryRepositoryPort；新增 validateActivatable |
| `config/KnowledgeGameCoreAutoConfiguration.java` | 注册 IpSeriesDomainService Bean；KnowledgeCategoryDomainService/QuestionDomainService Bean 调整注入 |
| `infrastructure/db/repository/CardTemplateJpaRepository.java` | 新增 `countActiveByIpSeriesId` |
| `infrastructure/db/repository/KnowledgeCategoryJpaRepository.java` | 新增 `countActiveByParentId`；删除 `countByParentId` |
| `infrastructure/db/repository/QuestionCategoryRelationJpaRepository.java` | 新增 `countActiveQuestionsByCategoryId`、`findAllByQuestionIdIn` |
| `infrastructure/adapter/repoadapter/CardTemplateRepositoryAdapter.java` | 实现新方法 |
| `infrastructure/adapter/repoadapter/QuestionRepositoryAdapter.java` | 实现新方法 |
| `infrastructure/adapter/repoadapter/KnowledgeCategoryRepositoryAdapter.java` | 替换/删除/新增方法实现 |

**后端 admin 层：**

| 文件 | 操作 |
|---|---|
| `application/service/IpSeriesAppService.java` | 注入 IpSeriesDomainService；deleteIpSeries 改造 |
| `application/service/CardTemplateAppService.java` | 注入 IpSeriesDomainService；updateCardTemplate 改造；新增 batchActivate/batchDeactivate |
| `application/service/QuestionAppService.java` | batchActivate 改造增加 validateActivatable 调用 |
| `api/controller/CardTemplateController.java` | 新增 `/batch-activate`、`/batch-deactivate` 接口（`@PutMapping`，与 QuestionController 一致） |
| `api/dto/request/BatchStatusRequest.java` | 加 `@Size(max=100)` 约束（同时影响 QuestionController 既有接口，全局收紧） |

**文档：**

| 文件 | 操作 |
|---|---|
| `docs/overview.md` | 在 IP 系列/知识点分类/卡牌/题库相关段落补充"停用/启用前置校验"说明 |
| `docs/requirements.md` | REQ-94 状态推进 + 备注 |

### 4.2 不受影响

- 用户端 `knowledge-game-app`、用户端前端
- 管理端前端（消息由后端返回）
- REQ-83/91 等已落地功能
- 数据库 schema（无新增列、无新增表）

## 五、验收标准

1. ✅ `CardTemplateRepositoryPort` 新增 3 个方法实现 + 单测通过
2. ✅ `QuestionRepository` 新增 2 个方法实现 + 单测通过
3. ✅ `KnowledgeCategoryRepositoryPort` 新增 2 个方法实现 + 单测通过；旧 `countByParentId` 已删除
4. ✅ `IpSeriesDomainService` 3 个 validate 方法实现 + 单测覆盖（含正常/异常路径，错误消息含名称）
5. ✅ `KnowledgeCategoryDomainService.validateDelete` 改造 + 单测覆盖（4 种组合：ACTIVE/INACTIVE × 子分类/题目）
6. ✅ `QuestionDomainService.validateActivatable` 实现 + 单测覆盖（含部分分类 INACTIVE 时消息列出全部 INACTIVE 名称）
7. ✅ `IpSeriesAppService.deleteIpSeries`、`CardTemplateAppService.updateCardTemplate/batchActivate`、`QuestionAppService.batchActivate` 改造 + 单测覆盖
8. ✅ `CardTemplateController` 新增 2 个批量接口 + `@WebMvcTest` 单测
9. ✅ `KnowledgeGameCoreAutoConfiguration` 注册新 Bean + 调整注入
10. ✅ 后端全模块编译通过（`mvn clean install`）
11. ✅ 后端全模块单测通过
12. ✅ REQ-94 状态从 `designed` 推进到 `done`

## 六、风险与权衡

| 风险 | 影响 | 缓解 |
|---|---|---|
| JPQL 中枚举字面量 `'ACTIVE'` 与枚举值不匹配 | 启动期不报错，运行期返回 0 误判可通过 | TDD 用例覆盖 ACTIVE/INACTIVE 两态可立即发现 |
| `countByParentId` 删除可能误伤其他调用方 | 编译失败 | 已 grep 确认唯一调用方为 `validateDelete`，删除安全 |
| `IpSeriesDomainService` 是新建类，可能漏注册到 `AutoConfiguration` | 启动期 Bean 缺失，运行期 NPE | 验收标准强制要求；AppService 单测中通过 `@SpringBootTest` 验证 |
| 批量启用 N+1 查询性能 | 大批量启用卡牌时多次查 IP 系列 | 通过 `findAllByIdIn` 一次性加载所有 IP/分类，再在内存匹配 |
| 卡牌 update 接口的"激活检测"逻辑依赖参数比较 | 若调用方传 status=ACTIVE 且当前已 ACTIVE，不触发校验 | 实现中通过 `template.getStatus() != ACTIVE && status == ACTIVE` 双重判断 |
| `@Size(max=100)` 收紧 Question 既有接口 | QuestionController 现有批量接口客户端可能已发 >100 条；加约束后会被 400 拒绝 | ①确认前端批量选择 UI 当前实际不会发 >100（默认分页 50，无跨页全选）；②若存在超 100 场景，需前端先改为分批调用；③本需求先在 DTO 层加约束，单测验证 100/101 边界，回归测试 QuestionController 既有用例 |

## 七、关联需求

- **REQ-16**：IP 系列 CRUD API（已 done，本需求补充停用前置校验）
- **REQ-17**：卡牌管理端 CRUD API（已 done，本需求新增批量启用接口 + 启用前置校验）
- **REQ-65**：知识库管理页（已 done，后端校验增强，前端无改动）
- **REQ-89**：知识点分类状态管理（idea 状态，本需求补充其停用前置校验，但不依赖其前端实现）

## 八、附录

### 8.1 当前 TODO 位置

- `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/application/service/IpSeriesAppService.java:108` — `// TODO: 检查是否有关联卡牌，有则不允许删除`
- `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/application/service/CardTemplateAppService.java:116` — `// TODO: 检查是否有关联用户收集，有则不允许删除`（**不在本需求范围**，标注为已知遗漏）

### 8.2 实现顺序建议（TDD）

1. core 层 Repository port + adapter + JPA 实现 + 单测（红→绿）
2. core 层 DomainService 改造 + 新建 + 单测（红→绿）
3. core 配置 `KnowledgeGameCoreAutoConfiguration` 注册 Bean
4. admin 应用层 AppService 改造 + 单测（红→绿）
5. admin Controller 新增批量接口 + 单测
6. 全量回归测试 + 文档同步
