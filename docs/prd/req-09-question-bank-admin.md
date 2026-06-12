# REQ-09 题库 — 管理端 CRUD API

## 背景

题库是游戏出题的基础设施。管理端需要能够创建、管理三种基础题型（选择题、判断题、填空题），为后续游戏层（REQ-10 秒判、REQ-12 Boss、REQ-13 串联）提供题目数据源。

## 需求概述

管理端题库 CRUD API，支持选择题（单选/多选）、判断题、填空题三种题型，包含基础 CRUD、批量导入、状态管理、分类关联管理功能。

## 数据模型

### question 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | 主键 |
| `type` | VARCHAR(20) NOT NULL | 题型：SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / FILL_BLANK |
| `content` | TEXT NOT NULL | 题目内容（纯文本，支持换行符） |
| `options` | JSON | 选项列表（选择题专用），格式：`[{"key":"A","content":"选项内容"}]` |
| `answer` | JSON NOT NULL | 正确答案。选择题：`"A"` 或 `["A","C"]`；判断题：`true` / `false`；填空题：`["keyword1","keyword2"]` |
| `explanation` | TEXT | 答案解析 |
| `difficulty` | TINYINT NOT NULL | 难度等级：1=简单 2=中等 3=困难 |
| `tags` | JSON | 标签数组，如 `["Java","多线程"]` |
| `status` | VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' | 状态：ACTIVE / INACTIVE |
| `created_at` | DATETIME NOT NULL | 创建时间 |
| `updated_at` | DATETIME NOT NULL | 更新时间 |

### question_category_relation 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | 主键 |
| `question_id` | BIGINT NOT NULL | 关联题目 |
| `category_id` | BIGINT NOT NULL | 关联知识点分类 |

联合唯一索引：`uk_question_category(question_id, category_id)`

### 级联行为

- 题目软删除（→INACTIVE）时关联记录保留，恢复（→ACTIVE）后关联自动生效
- 分类软删除时，题目的分类列表查询应过滤掉已 INACTIVE 的分类
- 不做物理级联删除

### 答案数据格式说明

选项使用稳定标识符（A/B/C/D），游戏出题时可打乱显示顺序，玩家选择后传回原始 key 进行答案校验。

| 题型 | options | answer |
|------|---------|--------|
| 单选 | `[{"key":"A","content":"选项A"},{"key":"B","content":"选项B"}]` | `"A"` |
| 多选 | `[{"key":"A","content":"选项A"},{"key":"B","content":"选项B"},{"key":"C","content":"选项C"}]` | `["A","C"]` |
| 判断 | `null` | `true` |
| 填空 | `null` | `["extends","implements"]` |

### 填空题答案匹配规则

填空题答案匹配忽略大小写（游戏层判定时统一转小写比较）。关键词顺序不要求一致（玩家答案集合与正确答案集合做子集/交集判定，具体逻辑由 REQ-10/12/13 定义）。

## 管理端 API

### 基础 CRUD

| 操作 | 方法 | 路径 |
|------|------|------|
| 创建题目 | POST | `/api/admin/questions` |
| 查询题目详情 | GET | `/api/admin/questions/{id}` |
| 分页查询题目列表 | GET | `/api/admin/questions` |
| 更新题目 | PUT | `/api/admin/questions/{id}` |
| 删除题目（软删除→INACTIVE） | DELETE | `/api/admin/questions/{id}` |

### 列表查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | String | 否 | 按题目内容模糊搜索 |
| `type` | String | 否 | 按题型筛选 |
| `difficulty` | Integer | 否 | 按难度筛选（1/2/3） |
| `categoryId` | Long | 否 | 按关联的知识点分类筛选 |
| `tag` | String | 否 | 按标签筛选 |
| `status` | String | 否 | 按状态筛选（ACTIVE/INACTIVE） |
| `sort` | String | 否 | 排序字段，可选值：`createdAt`（默认）、`updatedAt`、`difficulty` |
| `order` | String | 否 | 排序方向：`asc` / `desc`（默认 `desc`） |
| `page` | int | 否 | 页码，默认 0 |
| `size` | int | 否 | 每页大小，默认 20 |

### 分类关联管理

| 操作 | 方法 | 路径 |
|------|------|------|
| 查询题目关联的分类列表 | GET | `/api/admin/questions/{id}/categories` |
| 更新题目的分类关联（全量替换） | PUT | `/api/admin/questions/{id}/categories` |

### 状态管理

| 操作 | 方法 | 路径 |
|------|------|------|
| 批量启用 | PUT | `/api/admin/questions/batch-activate` |
| 批量禁用 | PUT | `/api/admin/questions/batch-deactivate` |

请求体：`{"ids": [1, 2, 3]}`

### 批量导入

| 操作 | 方法 | 路径 |
|------|------|------|
| 下载导入模板 | GET | `/api/admin/questions/import-template` |
| 导入题目 | POST | `/api/admin/questions/import`（`multipart/form-data`，`@RequestPart MultipartFile`） |

### 文件说明

导入上传和模板下载均为临时文件传输，不经过 REQ-83 文件服务（凭证/上传/存储流程）。
- 导入：Spring `MultipartFile` 接收 Excel，后端解析入库后即丢弃，不持久化文件
- 模板下载：通过 `HttpServletResponse` 输出流直接返回 Excel 文件

## 通用排序机制

本项目首个支持动态排序的功能。设计通用机制供后续列表接口复用。

### Domain 层

新增值对象 `SortField`（`domain/model/vo/SortField.java`）：
- `field`：排序字段（领域语义）
- `direction`：排序方向枚举（ASC / DESC）
- 提供静态工厂方法 `defaultSort()` 返回 `createdAt DESC`

### 各层传递

```
Controller (sort/order 请求参数)
  → AppService 组装 SortField
    → RepositoryPort 方法签名接收 SortField
      → RepositoryAdapter 维护 领域字段名→PO字段名 映射，转为 Spring Sort
```

### 字段映射

每个 RepositoryAdapter 维护自己的映射表：

```java
private static final Map<String, String> FIELD_MAPPING = Map.of(
    "createdAt", "createdAt",
    "updatedAt", "updatedAt",
    "difficulty", "difficulty"
);
```

传入不存在的字段时降级为默认排序（createdAt DESC）。

## 批量导入设计

### Excel 模板列

| 列 | 必填 | 说明 | 示例 |
|----|------|------|------|
| 题型 | 是 | 单选/多选/判断/填空 | 单选 |
| 题目内容 | 是 | 题干 | Java中哪个关键字用于继承？ |
| 选项A | 选择题必填 | 选项内容 | extends |
| 选项B | 选择题必填 | 选项内容 | implements |
| 选项C | 否 | 选项内容 | inherits |
| 选项D | 否 | 选项内容 | super |
| 正确答案 | 是 | 选择题填 A/B/C/D，多选用逗号分隔，判断题填 对/错，填空题填关键词用逗号分隔 | A |
| 难度 | 是 | 简单/中等/困难 | 中等 |
| 解析 | 否 | 答案解析 | extends用于类继承... |
| 标签 | 否 | 逗号分隔 | Java,面向对象 |
| 分类ID | 否 | 逗号分隔的分类 ID | 1,3 |

### 导入结果响应

```json
{
  "totalCount": 50,
  "successCount": 45,
  "failCount": 5,
  "failDetails": [
    {"row": 3, "reason": "选择题必须填写选项A和选项B"},
    {"row": 7, "reason": "题型格式错误，可选值：单选/多选/判断/填空"}
  ]
}
```

## 校验规则

### 创建/更新校验

| 校验项 | 规则 |
|--------|------|
| 题目内容 | 必填，不超过 500 字 |
| 题型 | 必填，枚举值校验 |
| 选项 | 选择题必填 2~6 个选项；判断题/填空题必须为空 |
| 正确答案 | 选择题答案必须是已有选项的 key；多选题至少 2 个答案；判断题答案为 true/false；填空题至少 1 个关键词 |
| 难度 | 必填，取值 1/2/3 |
| 标签 | 选填，每项不超过 20 字，最多 10 个 |
| 分类关联 | 选填，分类必须存在且为 ACTIVE |

## 已知限制与未来优化

- **tags JSON 查询性能**：当前阶段题目量不大，`JSON_CONTAINS` 查询可接受。题目量增长后考虑拆为 `question_tag` 关联表或 MySQL 虚拟列索引
- **导入模板分类 ID**：当前要求管理员手动填写分类 ID，后续版本可优化为支持分类名称+自动匹配

## DDD 分层结构

### Domain 层（core/domain）

```
domain/model/entity/
  Question.java              — 题目聚合根（create/reconstruct 工厂方法，update/deactivate/activate 行为方法）
domain/model/vo/
  QuestionOption.java        — 选项值对象（key + content）
  QuestionType.java          — 题型枚举
  Difficulty.java            — 难度枚举（EASY=1, MEDIUM=2, HARD=3）
  SortField.java             — 通用排序值对象（field + direction）
domain/port/outbound/
  QuestionRepository.java       — 出端口（命名与现有 IpSeriesRepository、FileInfoRepository 一致，无 Port 后缀）
domain/service/
  QuestionDomainService.java  — 校验逻辑（选项与答案一致性、必填字段）
```

### Infrastructure 层（core/infrastructure）

```
infrastructure/db/entity/
  QuestionPO.java
  QuestionCategoryRelationPO.java
infrastructure/db/repository/
  QuestionJpaRepository.java
  QuestionCategoryRelationJpaRepository.java
infrastructure/db/converter/
  QuestionConverter.java                   — PO ↔ Domain（options/answer JSON 转换用 default 方法）
infrastructure/adapter/repoadapter/
  QuestionRepositoryAdapter.java           — 实现出端口，处理分页+排序+分类关联+标签查询
```

### Admin 层（knowledge-game-admin）

```
admin/api/controller/
  QuestionController.java
admin/api/dto/
  CreateQuestionRequest.java
  UpdateQuestionRequest.java
  QuestionResponse.java
  QuestionCategoryUpdateRequest.java
  BatchStatusRequest.java
  QuestionImportResult.java
  ImportFailDetail.java
admin/api/assembler/
  QuestionAssembler.java                   — Domain → DTO
admin/application/
  QuestionAppService.java                  — 编排领域服务+仓储+导入逻辑（与现有 IpSeriesAppService 包路径一致，无 service 子目录）
```
