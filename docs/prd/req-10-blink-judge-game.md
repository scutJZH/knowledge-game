# REQ-10 秒判游戏 — 开始对局 + 答题流程

## 产品定位

用户端秒判游戏（Layer 1）后端 API。秒判是单局游戏的入口玩法：用户选择知识点分类（或不选=综合随机）→ 系统按 3 秒/题节奏下发判断题/单选题 → 用户作答 → 答错即终止本局。

本需求**仅记录答题事实**（题目、用户答案、对错、用时），不实现：
- **积分累计落账** → REQ-11
- **streak 连击计算** → REQ-11
- **streak=10 触发 Boss 关卡** → REQ-12
- **知识链串联** → REQ-13
- **对局结算 + 历史查询** → REQ-14

前端秒判页面（滑卡交互、倒计时 UI）是 REQ-30，本需求只交付后端 API。

## 用户故事

**作为** 已加入学习群组的用户
**我想要** 在群组内开始一局秒判游戏，按 3 秒/题节奏答题
**以便于** 后续（REQ-11~14）累积积分、触发更高层级玩法

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 与 REQ-11 边界 | REQ-10 仅记录答题事实，不计 score / 不维护 streak / 不落账 GroupMember.points | REQ-11 单独负责积分+连击，本需求职责清晰可独立验收 |
| 题型范围 | TRUE_FALSE + SINGLE_CHOICE | 符合产品 3 秒/题节奏；MULTIPLE_CHOICE 3 秒不可答；FILL_BLANK 留给 REQ-12 Boss 关卡 |
| 答题交互 | 一题一拉同步判定 | 答错即终止的产品规则由服务端保证；同步判定可防作弊；不引入 WebSocket |
| 超时判定 | 客户端 + 服务端双计时 | 客户端计时负责 UX 流畅，服务端计时（issuedAt 兜底，容差 500ms）防篡改 |
| 分类入参 | categoryId 可选，不传=综合随机 | 与产品文档 2.1「综合随机」选项一致；题库全局共享，不需群组授权 |
| 数据模型 | 双表双聚合根（GameSession + GameAnswer） | 参考 StudyGroup + GroupMember 模式；答题高频 INSERT 独立表最干净；REQ-14 历史查询天然按 session 聚合 |
| 「当前题」维护 | GameSession 内嵌 current_question_id + current_issued_at 字段 | 服务端需精确知道当前题才能校验 questionId 匹配 + 服务端计时 |
| 题量上限 | 保护性上限 100 题 | 正常游戏达不到，防 RAND 异常或攻击 |
| 服务端超时容差 | 3500ms（3s + 500ms） | 兼顾网络抖动 + 时钟偏移 |
| 主动放弃 | 显式 POST /api/games/{id}/end | 用户主动退场场景；僵尸 session 不在本需求清理 |
| 题目顺序 | 随机（ORDER BY RAND() LIMIT 1） | 单局内通过 game_answer 表排除已答 |
| 单选题选项 | 后端打乱顺序返回，玩家答案回传原始 key | 与题库设计「稳定标识符 A/B/C/D，可打乱显示顺序」一致 |
| 并发答题控制 | AppService.answer 捕获 `DataIntegrityViolationException` 转 `GAME_ALREADY_ENDED` | 单用户理论上不并发答题，但前端 bug 或恶意请求可能触发；防御 `UNIQUE(session_id, sequence)` 冲突返回 500 |
| 判断题答案 | String "true" / "false"（与 JSON 兼容） | Question.answer 已是 String 类型，无需类型转换 |
| 僵尸 session 清理 | 不在本需求范围 | YAGNI；后续可加 @Scheduled 清理 IN_PROGRESS 超 1h 的 session |
| 数据库外键 | 不设物理外键 | 项目规范，关联通过应用层保证 |

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/games` | 开始对局（用户端，需 JWT），返回 sessionId + 第 1 题 |
| POST | `/api/games/{id}/answers` | 提交本题答案，答对返回下一题，答错/超时返回结束信号 |
| POST | `/api/games/{id}/end` | 主动放弃 |

### 鉴权

需 JWT 鉴权（`/api/**` 前缀自动拦截），无需 ADMIN 角色。`userId` 从 JWT 提取（`SecurityUtils.getCurrentUserId()`），不依赖请求体。

### POST `/api/games` — 开始对局

**请求体（`StartGameRequest`）：**

```json
{
  "groupId": 1,
  "categoryId": 2
}
```

**字段规则：**
- `groupId`：必填，`@NotNull`，Long 类型
- `categoryId`：可选，Long 类型。null=综合随机（全分类 ACTIVE 题目混合抽取）

**响应体（`StartGameResponse`，包裹在 `Result<T>`）：**

```json
{
  "code": 0,
  "data": {
    "sessionId": 100,
    "groupId": 1,
    "categoryId": 2,
    "status": "IN_PROGRESS",
    "startedAt": 1718800000000,
    "currentQuestion": {
      "sequence": 1,
      "questionId": 42,
      "type": "TRUE_FALSE",
      "content": "Java 是面向对象语言",
      "options": null,
      "issuedAt": 1718800000000
    }
  }
}
```

**字段说明：**
- `sessionId`：对局 ID
- `status`：固定 `IN_PROGRESS`
- `startedAt` / `issuedAt`：毫秒时间戳（Long），项目统一规范
- `currentQuestion.options`：判断题为 null（前端固定渲染对/错按钮）；单选题为 `[{key, content}]` 数组（**已打乱顺序**）
- `currentQuestion.issuedAt`：服务端发题时刻，客户端据此启动 3 秒倒计时

### POST `/api/games/{id}/answers` — 提交答案

**请求体（`AnswerRequest`）：**

```json
{
  "questionId": 42,
  "userAnswer": "true",
  "timeCostMs": 2500
}
```

**字段规则：**
- `questionId`：必填，必须等于 session 当前的 `currentQuestionId`
- `userAnswer`：必填
  - TRUE_FALSE：`"true"` / `"false"` 字符串
  - SINGLE_CHOICE：选项原始 key 字符串（如 `"A"` / `"B"`，**原始 key 而非打乱后的显示位置**）
- `timeCostMs`：可选，客户端计时。**服务端不存储该值**，真实 `timeCostMs` 由服务端从 `answeredAt - currentIssuedAt` 计算（防篡改）；客户端值仅用于日志/调试

**响应（答对，返回下一题）：**

```json
{
  "code": 0,
  "data": {
    "sessionId": 100,
    "sequence": 1,
    "isCorrect": true,
    "status": "IN_PROGRESS",
    "nextQuestion": {
      "sequence": 2,
      "questionId": 43,
      "type": "SINGLE_CHOICE",
      "content": "Java 中哪个关键字用于继承？",
      "options": [
        {"key": "B", "content": "implements"},
        {"key": "A", "content": "extends"}
      ],
      "issuedAt": 1718800003000
    }
  }
}
```

**响应（答错或服务端超时，本局结束）：**

```json
{
  "code": 0,
  "data": {
    "sessionId": 100,
    "sequence": 1,
    "isCorrect": false,
    "status": "ENDED",
    "endReason": "ANSWER_WRONG",
    "correctAnswer": "true",
    "explanation": "Java 是纯面向对象语言..."
  }
}
```

**响应（答对但题库耗尽，本局结束）：**

```json
{
  "code": 0,
  "data": {
    "sessionId": 100,
    "sequence": 50,
    "isCorrect": true,
    "status": "ENDED",
    "endReason": "QUESTIONS_EXHAUSTED"
  }
}
```

### POST `/api/games/{id}/end` — 主动放弃

无请求体。

**响应：**

```json
{
  "code": 0,
  "data": {
    "sessionId": 100,
    "status": "ENDED",
    "endReason": "GIVE_UP",
    "endedAt": 1718800000000
  }
}
```

### 业务流程

#### start 流程

```
@Transactional
1. userId = SecurityUtils.getCurrentUserId()
2. if !studyGroupRepository.existsById(groupId) → GROUP_NOT_FOUND
3. if !groupMemberRepository.existsByGroupIdAndUserId(groupId, userId) → NOT_GROUP_MEMBER
4. if categoryId != null:
   a. category = knowledgeCategoryRepositoryPort.findById(categoryId)
      .orElseThrow(KNOWLEDGE_CATEGORY_NOT_FOUND)
   b. if category.status != ACTIVE → KNOWLEDGE_CATEGORY_NOT_ACTIVE
5. firstQuestion = questionRepository.findRandomActiveOneByTypesInAndCategoryExcluding(
       EnumSet.of(TRUE_FALSE, SINGLE_CHOICE), categoryId, Collections.emptySet())
   .orElseThrow(GAME_NO_AVAILABLE_QUESTIONS)
6. session = GameSession.start(groupId, userId, categoryId, firstQuestion.getId(), LocalDateTime.now())
7. gameSessionRepository.save(session)
8. return GameSessionAssembler.toStartResponse(session, firstQuestion)
```

#### answer 流程（核心循环）

```
@Transactional
try {
1. userId = SecurityUtils.getCurrentUserId()
2. session = gameSessionRepository.findById(sessionId)
      .orElseThrow(GAME_SESSION_NOT_FOUND)
3. if session.userId != userId → GAME_NOT_YOUR_SESSION
4. if session.status == ENDED → GAME_ALREADY_ENDED
5. if req.questionId != session.currentQuestionId → GAME_QUESTION_MISMATCH
6. answeredAt = LocalDateTime.now()
7. serverTimeCostMs = Duration.between(session.currentIssuedAt, answeredAt).toMillis()
8. question = questionRepository.findById(req.questionId)
      .orElseThrow(GAME_QUESTION_MISMATCH)  // 理论上不会触发，题目被删的兜底
9. isCorrect = judgeAnswer(question, req.userAnswer)  // 内部 null/blank 短路返回 false
10. if serverTimeCostMs > 3500 → isCorrect = false  // 服务端兜底超时
11. sequence = gameAnswerRepository.countBySessionId(sessionId) + 1
12. gameAnswerRepository.save(GameAnswer.record(
        sessionId, sequence, req.questionId, req.userAnswer,
        isCorrect, session.currentIssuedAt, answeredAt))
13. if !isCorrect:
    a. session.endWithWrong()
    b. gameSessionRepository.save(session)
    c. return AnswerResponse.ended(session, ANSWER_WRONG, question.answer, question.explanation)
14. // 答对，抽下一题
    excludeIds = gameAnswerRepository.findAnsweredQuestionIdsBySessionId(sessionId)
    nextQuestion = questionRepository.findRandomActiveOneByTypesInAndCategoryExcluding(
        EnumSet.of(TRUE_FALSE, SINGLE_CHOICE), session.categoryId, excludeIds)
15. if nextQuestion.isEmpty():
    a. session.endWithExhausted()
    b. gameSessionRepository.save(session)
    c. return AnswerResponse.ended(session, QUESTIONS_EXHAUSTED, null, null)
16. session.updateCurrentQuestion(nextQuestion.get().getId(), LocalDateTime.now())
17. gameSessionRepository.save(session)
18. return AnswerResponse.nextQuestion(session, nextQuestion.get())
} catch (DataIntegrityViolationException e) {
    // 并发答题：两个并发请求同时通过 status != ENDED 检查，第二个 INSERT 触发 UNIQUE(session_id, sequence)
    throw new BusinessException(ResultCode.GAME_ALREADY_ENDED);
}
```

**判定规则（`judgeAnswer`）：**

两种题型统一用 `question.answer.equalsIgnoreCase(userAnswer.trim())` 比对：
- TRUE_FALSE：题库 answer="true"/"false"，与用户答案忽略大小写比对
- SINGLE_CHOICE：题库 answer="A"/"B" 等 key，与用户回传的原始 key（非打乱后位置）忽略大小写比对（选项 key 实际为大写，case-insensitive 无副作用）

**null/blank 短路：** `userAnswer` 为 null 或 blank（纯空白字符）时直接返回 false（视为未作答，等同答错）。

#### end 流程

```
@Transactional
1. userId = SecurityUtils.getCurrentUserId()
2. session = gameSessionRepository.findById(sessionId)
      .orElseThrow(GAME_SESSION_NOT_FOUND)
3. if session.userId != userId → GAME_NOT_YOUR_SESSION
4. if session.status == ENDED → GAME_ALREADY_ENDED
5. session.endWithGiveUp()
6. gameSessionRepository.save(session)
7. return EndGameResponse(session)
```

### 题量上限保护

`sequence` 达到 100 时，下一次答题即使答对也强制 `endWithExhausted()`（防无限循环）。正常游戏达不到此上限。

## 数据模型

### game_session 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT AUTO_INCREMENT | PK | |
| group_id | BIGINT | NOT NULL | 群组 ID（无 FK） |
| user_id | BIGINT | NOT NULL | 用户 ID（无 FK） |
| category_id | BIGINT | NULL | 分类（NULL=综合随机） |
| current_question_id | BIGINT | NULL | 当前发题 ID（ENDED 时为 NULL） |
| current_issued_at | DATETIME | NULL | 当前发题时刻（ENDED 时为 NULL） |
| status | VARCHAR(20) | NOT NULL | IN_PROGRESS / ENDED |
| end_reason | VARCHAR(30) | NULL | ANSWER_WRONG / GIVE_UP / QUESTIONS_EXHAUSTED（ENDED 时非空） |
| started_at | DATETIME | NOT NULL | |
| ended_at | DATETIME | NULL | |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

**索引：**
- `INDEX(user_id, status)` — 用户查询进行中对局
- `INDEX(group_id, user_id)` — 群组维度统计（REQ-14 历史查询用）

### game_answer 表

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT AUTO_INCREMENT | PK | |
| session_id | BIGINT | NOT NULL | 对局 ID（无 FK） |
| sequence | INT | NOT NULL | 本局第几题（从 1 起） |
| question_id | BIGINT | NOT NULL | 题目 ID |
| user_answer | VARCHAR(50) | NULL | 用户答案（NULL=超时未提交） |
| is_correct | BOOLEAN | NOT NULL | 是否正确 |
| issued_at | DATETIME | NOT NULL | 后端发题时刻 |
| answered_at | DATETIME | NOT NULL | 后端接收答案时刻 |
| time_cost_ms | INT | NOT NULL | answered_at - issued_at（服务端计时） |
| created_at | DATETIME | NOT NULL | |

**索引：**
- `UNIQUE(session_id, sequence)` — 同局 sequence 唯一
- `INDEX(session_id)` — 反查本局已答题 ID

## 领域模型

### GameSession 聚合根（`core/domain/model/entity/GameSession.java`）

**字段：** `id, groupId, userId, categoryId, currentQuestionId, currentIssuedAt, status, endReason, startedAt, endedAt, createdAt, updatedAt`

**行为方法：**
- `start(groupId, userId, categoryId, firstQuestionId, issuedAt)` 工厂方法（创建新对局，status=IN_PROGRESS）
- `reconstruct(...)` 从 PO 重建
- `updateCurrentQuestion(questionId, issuedAt)` 答对后切换到下一题
- `endWithWrong()` / `endWithGiveUp()` / `endWithExhausted()` 三个结束语义（共享私有 `end(reason)` 方法）
- 状态机非法转换检测：ENDED 状态下任何 endXxx 调用抛 `IllegalStateException`
- `clearCurrentQuestion()` 内部幂等：`currentQuestionId` 已为 null 时直接返回（防御 IN_PROGRESS 但 currentQuestionId 异常为 null 的边缘情况，endWithGiveUp 不会 NPE）

> 不提供 setter。currentQuestionId/currentIssuedAt 通过 `updateCurrentQuestion` / `clearCurrentQuestion`（end 内部调用）变更。

### GameAnswer 聚合根（`core/domain/model/entity/GameAnswer.java`）

**字段：** `id, sessionId, sequence, questionId, userAnswer, isCorrect, issuedAt, answeredAt, timeCostMs, createdAt`

**行为方法：**
- `record(sessionId, sequence, questionId, userAnswer, isCorrect, issuedAt, answeredAt)` 工厂方法
  - 内部计算 `timeCostMs = Duration.between(issuedAt, answeredAt).toMillis()`
  - 校验 `sequence >= 1`
- `reconstruct(...)` 从 PO 重建

> 不可变聚合（仅创建，无 update 方法）。`userAnswer` 可为 null（超时未提交）。

### GameSessionStatus 枚举（`core/domain/model/domainenum/`）

`IN_PROGRESS` / `ENDED`

### GameEndReason 枚举（`core/domain/model/domainenum/`）

`ANSWER_WRONG` / `GIVE_UP` / `QUESTIONS_EXHAUSTED`

### 出端口（`core/domain/port/outbound/`）

**GameSessionRepository：**
```java
GameSession save(GameSession session);
Optional<GameSession> findById(Long id);
```

**GameAnswerRepository：**
```java
GameAnswer save(GameAnswer answer);
Set<Long> findAnsweredQuestionIdsBySessionId(Long sessionId);
int countBySessionId(Long sessionId);
```

**QuestionRepository（已存在，需扩展）：**
```java
// 新增方法
Optional<Question> findRandomActiveOneByTypesInAndCategoryExcluding(
    Collection<QuestionType> types, Long categoryId, Collection<Long> excludeQuestionIds);
```

- `types`：题型白名单（REQ-10 传 `{TRUE_FALSE, SINGLE_CHOICE}`）
- `categoryId`：null=不按分类过滤；非 null=按 `question_category_relation` 表关联过滤
- `excludeQuestionIds`：本 session 已答过的题（去重），空集合=不排除
- SQL 思路：`WHERE q.status='ACTIVE' AND q.type IN (...) AND q.id NOT IN (...) [AND EXISTS(SELECT 1 FROM question_category_relation WHERE question_id=q.id AND category_id=?)] ORDER BY RAND() LIMIT 1`

## 持久化层（core/infrastructure）

| 组件 | 文件位置 |
|------|---------|
| `GameSessionPO` | `core/infrastructure/db/entity/GameSessionPO.java` |
| `GameSessionJpaRepository` | `core/infrastructure/db/repository/GameSessionJpaRepository.java` |
| `GameSessionRepositoryAdapter` | `core/infrastructure/adapter/repoadapter/GameSessionRepositoryAdapter.java`（@Repository 自动扫描） |
| `GameSessionConverter` | `core/infrastructure/db/converter/GameSessionConverter.java`（MapStruct） |
| `GameAnswerPO` | `core/infrastructure/db/entity/GameAnswerPO.java` |
| `GameAnswerJpaRepository` | `core/infrastructure/db/repository/GameAnswerJpaRepository.java` |
| `GameAnswerRepositoryAdapter` | `core/infrastructure/adapter/repoadapter/GameAnswerRepositoryAdapter.java` |
| `GameAnswerConverter` | `core/infrastructure/db/converter/GameAnswerConverter.java` |

**注意：**
- 所有 PO 无 `@ForeignKey` 物理外键（项目规范）
- `GameSessionPO.status` / `endReason` 用 `@Enumerated(EnumType.STRING)` 持久化（参考 `GroupMemberPO.role`）
- `GameAnswerPO.isCorrect` 用 `BOOLEAN` 列（MySQL 自动映射 TINYINT(1)）
- QuestionRepositoryAdapter 扩展新方法，已有方法签名不变

## 应用层 + API 层（app 模块）

### 包结构

```
com.knowledgegame.app/
├── api/
│   ├── controller/GameSessionController.java
│   └── dto/
│       ├── StartGameRequest.java
│       ├── StartGameResponse.java
│       ├── AnswerRequest.java
│       ├── AnswerResponse.java
│       ├── EndGameResponse.java
│       └── QuestionView.java              (内嵌 DTO：sequence/questionId/type/content/options/issuedAt)
├── application/
│   ├── service/GameSessionAppService.java
│   └── assembler/GameSessionAssembler.java    (跟随 app 模块现有 Group 系列位置，见决策说明)
```

> **Assembler 位置决策**：CLAUDE.md 规定 Assembler 在 `api/assembler/`，但 app 模块现有 3 个 Assembler（GroupMember / StudyGroup / GroupIpLibrary）均位于 `application/assembler/`。本需求**跟随 app 模块内部约定**放在 `application/assembler/`，避免模块内出现两套位置约定。建议在 `docs/tech-debt.md` 单独立项统一迁移（覆盖 CLAUDE.md 修正或 Group 系列迁移）。

### GameSessionController

```java
@RestController
@RequestMapping("/api/games")
public class GameSessionController {
    private final GameSessionAppService appService;

    @PostMapping
    public Result<StartGameResponse> start(@Valid @RequestBody StartGameRequest request) {
        return Result.success(appService.start(request));
    }

    @PostMapping("/{id}/answers")
    public Result<AnswerResponse> answer(@PathVariable Long id,
                                          @Valid @RequestBody AnswerRequest request) {
        return Result.success(appService.answer(id, request));
    }

    @PostMapping("/{id}/end")
    public Result<EndGameResponse> end(@PathVariable Long id) {
        return Result.success(appService.end(id));
    }
}
```

### GameSessionAppService

```java
@Service
public class GameSessionAppService {
    private final GameSessionRepository gameSessionRepository;
    private final GameAnswerRepository gameAnswerRepository;
    private final QuestionRepository questionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StudyGroupRepository studyGroupRepository;
    private final KnowledgeCategoryRepositoryPort knowledgeCategoryRepositoryPort;

    private static final Set<QuestionType> SUPPORTED_TYPES = EnumSet.of(
            QuestionType.TRUE_FALSE, QuestionType.SINGLE_CHOICE);
    private static final long SERVER_TIMEOUT_MS = 3500;
    private static final int MAX_QUESTIONS_PER_SESSION = 100;

    @Transactional
    public StartGameResponse start(StartGameRequest req) { /* 见业务流程 */ }

    @Transactional
    public AnswerResponse answer(Long sessionId, AnswerRequest req) { /* 见业务流程 */ }

    @Transactional
    public EndGameResponse end(Long sessionId) { /* 见业务流程 */ }

    private boolean judgeAnswer(Question question, String userAnswer) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        return question.getAnswer().equalsIgnoreCase(userAnswer.trim());
    }
}
```

> `judgeAnswer` 实现简化：判断题（answer="true"/"false"）+ 单选题（answer="A"/"B"）都通过 `equalsIgnoreCase + trim` 比对，因为题库 answer 字段统一为 String。

### GameSessionAssembler（MapStruct）

- `toStartResponse(GameSession, Question): StartGameResponse` — 含 currentQuestion
- `toAnswerNextResponse(GameSession, Question, int sequence): AnswerResponse` — 答对+下一题
- `toAnswerEndedResponse(GameSession, int sequence, String correctAnswer, String explanation): AnswerResponse` — 结束
- `toEndResponse(GameSession): EndGameResponse`
- `toQuestionView(Question, int sequence, LocalDateTime issuedAt): QuestionView` — 单选题选项打乱顺序（Collections.shuffle）

### 错误处理

| 场景 | HTTP | ResultCode | 消息 |
|------|------|------------|------|
| groupId 不存在 | 200 | GROUP_NOT_FOUND（已有，404） | 群组不存在 |
| 用户非群组成员 | 200 | NOT_GROUP_MEMBER（已有，403） | 非群组成员 |
| categoryId 不存在 | 200 | **KNOWLEDGE_CATEGORY_NOT_FOUND**（新增，404） | 知识点分类不存在 |
| categoryId 已 INACTIVE | 200 | **KNOWLEDGE_CATEGORY_NOT_ACTIVE**（新增，400） | 知识点分类已停用 |
| 筛选条件下无可用题目 | 200 | **GAME_NO_AVAILABLE_QUESTIONS**（新增，400） | 暂无可用题目 |
| sessionId 不存在 | 200 | **GAME_SESSION_NOT_FOUND**（新增，404） | 对局不存在 |
| session 不属于当前用户 | 200 | **GAME_NOT_YOUR_SESSION**（新增，403） | 无权操作该对局 |
| session 已 ENDED（再提交答案/放弃） | 200 | **GAME_ALREADY_ENDED**（新增，400） | 对局已结束 |
| 提交的 questionId 与当前题不匹配 | 200 | **GAME_QUESTION_MISMATCH**（新增，400） | 题目与当前对局不匹配 |
| 服务端计时超时（>3500ms） | — | 不报错，按 isCorrect=false 处理，endReason=ANSWER_WRONG | — |
| Bean Validation 失败 | 400 | FAIL | 由 GlobalExceptionHandler.handleValidationException 处理 |
| 系统异常 | 500 | INTERNAL_ERROR | 由 GlobalExceptionHandler.handleException 兜底 |

**新增 ResultCode 决策**（7 个，全部加入 `core/common/result/ResultCode.java`）：

```java
// REQ-10 知识点分类校验（2 个）
KNOWLEDGE_CATEGORY_NOT_FOUND(404, "知识点分类不存在"),
KNOWLEDGE_CATEGORY_NOT_ACTIVE(400, "知识点分类已停用"),

// REQ-10 秒判游戏（5 个）
GAME_NO_AVAILABLE_QUESTIONS(400, "暂无可用题目"),
GAME_SESSION_NOT_FOUND(404, "对局不存在"),
GAME_NOT_YOUR_SESSION(403, "无权操作该对局"),
GAME_ALREADY_ENDED(400, "对局已结束"),
GAME_QUESTION_MISMATCH(400, "题目与当前对局不匹配"),
```

> **注意**：KNOWLEDGE_CATEGORY_NOT_FOUND / KNOWLEDGE_CATEGORY_NOT_ACTIVE 在 REQ-10 引入但可被后续其他需求复用（任何需要校验分类存在/启用的场景）。

## Impact Analysis

### 修改 / 新增文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `core/.../domain/model/entity/GameSession.java` | 新增 | 聚合根 |
| `core/.../domain/model/entity/GameAnswer.java` | 新增 | 聚合根 |
| `core/.../domain/model/domainenum/GameSessionStatus.java` | 新增 | 枚举 |
| `core/.../domain/model/domainenum/GameEndReason.java` | 新增 | 枚举 |
| `core/.../domain/port/outbound/GameSessionRepository.java` | 新增 | 出端口 |
| `core/.../domain/port/outbound/GameAnswerRepository.java` | 新增 | 出端口 |
| `core/.../infrastructure/db/entity/GameSessionPO.java` | 新增 | PO |
| `core/.../infrastructure/db/entity/GameAnswerPO.java` | 新增 | PO |
| `core/.../infrastructure/db/repository/GameSessionJpaRepository.java` | 新增 | Spring Data JPA |
| `core/.../infrastructure/db/repository/GameAnswerJpaRepository.java` | 新增 | Spring Data JPA |
| `core/.../infrastructure/adapter/repoadapter/GameSessionRepositoryAdapter.java` | 新增 | Repository 实现（@Repository 自动扫描） |
| `core/.../infrastructure/adapter/repoadapter/GameAnswerRepositoryAdapter.java` | 新增 | Repository 实现 |
| `core/.../infrastructure/db/converter/GameSessionConverter.java` | 新增 | MapStruct |
| `core/.../infrastructure/db/converter/GameAnswerConverter.java` | 新增 | MapStruct |
| `core/.../domain/port/outbound/QuestionRepository.java` | 修改 | 新增 `findRandomActiveOneByTypesInAndCategoryExcluding` 方法 |
| `core/.../infrastructure/adapter/repoadapter/QuestionRepositoryAdapter.java` | 修改 | 实现新方法（JPQL + JOIN question_category_relation） |
| `app/.../api/controller/GameSessionController.java` | 新增 | Controller |
| `app/.../api/dto/StartGameRequest.java` | 新增 | 请求 DTO |
| `app/.../api/dto/StartGameResponse.java` | 新增 | 响应 DTO |
| `app/.../api/dto/AnswerRequest.java` | 新增 | 请求 DTO |
| `app/.../api/dto/AnswerResponse.java` | 新增 | 响应 DTO |
| `app/.../api/dto/EndGameResponse.java` | 新增 | 响应 DTO |
| `app/.../api/dto/QuestionView.java` | 新增 | 内嵌题目展示 DTO |
| `app/.../application/service/GameSessionAppService.java` | 新增 | 应用服务 |
| `app/.../application/assembler/GameSessionAssembler.java` | 新增 | MapStruct |
| `core/.../common/result/ResultCode.java` | 修改 | 新增 7 个枚举 |
| `docs/card-system-data-model.md` | 修改 | 同步 game_session / game_answer 表设计 |
| `docs/overview.md` | 修改 | 同步 API 清单 + 数据模型表 + 设计决策 |

> KnowledgeGameCoreAutoConfiguration 无需修改：RepositoryAdapter 通过 `@ComponentScan("com.knowledgegame.core.infrastructure")` 自动扫描（@Repository 注解）；本需求不引入新的纯 POJO 领域服务（抽题逻辑放在 Repository，GameSession 状态机方法都是实体行为方法）。

### 依赖关系

- app 模块已依赖 core 模块，直接使用新增的领域类型和出端口
- GameSessionAppService 注入 6 个端口：GameSessionRepository / GameAnswerRepository / QuestionRepository / GroupMemberRepository / StudyGroupRepository / KnowledgeCategoryRepositoryPort（GroupMember + StudyGroup 已在 REQ-48 引入）
- 不新增 Maven 依赖
- 不修改现有表结构（仅新增 2 张表）

### 不受影响的现有功能

- 现有 GameSession 之外的所有聚合根不变（User / IpSeries / CardTemplate / Question / KnowledgeItem / StudyGroup / GroupMember 等）
- QuestionRepository 已有方法签名不变（仅新增方法）
- 已完成的需求（REQ-01~09, 16~17, 26~28, 40~65, 74~114）功能不受影响
- 前端 REQ-30（秒判页面）后续依赖本需求的 API，本需求交付后即可联调

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| **领域层单测** | GameSessionTest：`start` 工厂字段赋值、`updateCurrentQuestion` 切换、`endWithWrong/GiveUp/Exhausted` 三个结束语义、ENDED 状态下任何 endXxx 调用抛 `IllegalStateException`。GameAnswerTest：`record` 字段赋值（含 timeCostMs 计算）、`sequence < 1` 抛异常 |
| **Controller @WebMvcTest**（`addFilters=false`） | **start 端点 6 场景**：成功 / 群组不存在 / 非成员 / 分类不存在 / 分类 INACTIVE / 无可用题。**answer 端点 7 场景**：答对+下一题 / 答错+结束 / 服务端超时 / session 不存在 / 非本人 / 已结束 / 题不匹配。**end 端点 4 场景**：成功 / session 不存在 / 非本人 / 已结束 |
| **AppService 单测（Mockito）** | ArgumentCaptor 验证 GameSession save 参数（status/endReason 正确）；ArgumentCaptor 验证 GameAnswer save 参数（timeCostMs 由服务端计时计算）；服务端超时覆盖测试（mock currentIssuedAt 为 4s 前，验证 isCorrect 强制为 false）；抽题耗尽分支（mock findRandomActive... 返回 empty）；题量上限 100 测试 |
| **Repository 集成测试（@DataJpaTest + 真实 MySQL）** | GameSessionRepositoryAdapterTest：save（枚举持久化）、findById（找到/找不到）、status/endReason 枚举往返。GameAnswerRepositoryAdapterTest：save、`findAnsweredQuestionIdsBySessionId`、`countBySessionId`、`UNIQUE(session_id, sequence)` 唯一约束重复插入抛 `DataIntegrityViolationException`。QuestionRepositoryAdapterTest 新方法：4 种条件组合（全分类+无排除 / 全分类+有排除 / 指定分类+无排除 / 指定分类+有排除），验证 ORDER BY RAND() 随机性（多次调用结果不完全一致） |
| **Converter / Assembler 单测** | GameSessionConverter 双向转换（含枚举互转）、GameAnswerConverter 双向转换（含 timeCostMs 计算）。GameSessionAssembler：toStartResponse / toAnswerNextResponse / toAnswerEndedResponse / toEndResponse；toQuestionView 验证单选题 options 打乱（多次调用顺序不完全一致），判断题 options 为 null；毫秒时间戳转换 |

**集成测试配置（参考 REQ-48 模式）：**

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import({GameSessionRepositoryAdapter.class, GameAnswerRepositoryAdapter.class})
class GameSessionRepositoryAdapterTest { ... }
```

### 手动验证

1. 注册用户 A、B；A 创建群组 G；B 加入 G
2. 管理端录入若干 ACTIVE 题目（TRUE_FALSE + SINGLE_CHOICE），关联分类 C
3. A 调 `POST /api/games` body `{groupId: G, categoryId: C}`，预期返回 sessionId + 第 1 题
4. A 调 `POST /api/games/{id}/answers` 提交正确答案，预期返回下一题（sequence=2，issuedAt 更新）
5. A 调 `POST /api/games/{id}/answers` 提交错误答案，预期返回 status=ENDED + endReason=ANSWER_WRONG + correctAnswer + explanation
6. A 调 `POST /api/games/{id}/end`，预期 400 GAME_ALREADY_ENDED
7. 服务端超时验证：A 拿到题后 sleep 4s 再提交，预期 isCorrect=false（即使答案正确）
8. 单选题选项打乱：连续发起 10 局对局，验证单选题 options 顺序不完全一致
9. 题库耗尽：管理端某分类只录 2 题，A 持续答对，预期第 2 题答对后返回 status=ENDED + endReason=QUESTIONS_EXHAUSTED
10. 跨用户校验：B 尝试 `POST /api/games/{A's sessionId}/answers`，预期 403 GAME_NOT_YOUR_SESSION
11. 已结束对局：A 对已 ENDED 的 session 调 answer，预期 400 GAME_ALREADY_ENDED
12. 综合随机：A 不传 categoryId，预期从全分类抽题

### 回滚标准

- 仅新增文件 + 4 处修改（ResultCode / QuestionRepository / QuestionRepositoryAdapter / docs），回滚只需：
  - 删除 app 模块 8 个新文件（Controller / AppService / Assembler / 6 DTO）
  - 删除 core 模块 14 个新文件（实体×2 / 枚举×2 / 端口×2 / PO×2 / JpaRepo×2 / Adapter×2 / Converter×2）
  - 撤销 `QuestionRepository.java` + `QuestionRepositoryAdapter.java` 中新增的方法
  - 撤销 `ResultCode.java` 中新增的 7 个枚举
  - 撤销 `card-system-data-model.md` 和 `overview.md` 同步修改
- 数据库表如已创建需手动 DROP（`game_session` + `game_answer`）

## 未来扩展（不在 REQ-10 范围）

| 需求 | 涉及改动 |
|------|---------|
| REQ-11 积分计算 + 连击 | GameSession 新增 `score` + `streak` 字段；answer 流程中 score+=1，streak++；答错或终止时落账 GroupMember.points + 写 point_transaction |
| REQ-12 Boss 关卡 | streak=10 时由秒判切换到 Boss 状态（GameSession 新增 BOSS_IN_PROGRESS 等）；引入 FILL_BLANK 题型；Boss 答题独立 API |
| REQ-13 知识链串联 | Boss 通过后切换到串联状态；引入 MULTIPLE_CHOICE 题型 |
| REQ-14 结算 + 历史查询 | GET /api/games/{id} 查询对局详情；GET /api/games/history 分页查询用户历史；翻牌奖励触发 |
| 僵尸 session 清理 | @Scheduled 定时任务，扫描 IN_PROGRESS 超 1h 的 session，标记为 ENDED + endReason=TIMEOUT（新增枚举） |
| 题目难度递增 | 按 sequence 调整 difficulty 权重（前期 EASY，后期 HARD） |
| 反作弊增强 | 设备指纹 + IP 限频 + 异常答题模式检测 |
