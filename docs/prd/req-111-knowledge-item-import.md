# REQ-111 知识条目管理 — 批量导入

## 产品定位

在管理后台知识条目管理页中实现两种批量导入模式：Excel 批量导入（单文件含多条）和 Markdown zip 包导入（多个 .md + 元数据 index.xlsx）。参考 REQ-110 卡牌批量导入模式。

## 前置依赖

| 依赖 | 说明 | 状态 |
|------|------|------|
| REQ-97 | 知识条目管理端 CRUD API + 管理页 | done |
| REQ-07 | 知识点分类管理端 CRUD API（用于校验分类名称存在性） | done |

## 用户故事

**作为** 系统管理员
**我想要** 通过 Excel 或 Markdown zip 包批量导入知识条目
**以便于** 一次性录入大量知识条目数据，避免逐条手动创建

## 功能需求

### 1. 下载模板

**端点**：`GET /api/admin/knowledge-items/import-template`

通过 Apache POI `XSSFWorkbook` 动态生成模板文件（参考 REQ-110 CardTemplate 实现），含表头冻结、列宽自适应、2 行示例数据、末尾 1 行填写说明（首列以 `【` 开头，导入时跳过）。

- 返回文件名：`knowledge_item_import_template.xlsx`
- 文本列（标题、标签）单元格格式设为「文本」，防止 Excel 自动转换数字（如 "001" → "1"）
- 限定值列（状态）使用下拉选择（数据验证），可选值：启用、停用

**Excel 模板列定义**（表头在第 1 行，数据从第 2 行开始）：

| 列号 | 列名 | 说明 | 必填 | 单元格格式 |
|------|------|------|------|-----------|
| A | 标题 | 1~200 字符 | ✅ | 文本 |
| B | Markdown正文 | 知识条目正文内容，最大 50000 字符 | ✅ | — |
| C | 标签 | 逗号分隔，最多 10 个，每个最长 20 字符 | 否 | 文本 |
| D | 分类名称 | KnowledgeCategory name，逗号分隔多选，必须全部存在且 ACTIVE | ✅ | — |
| E | 状态 | 启用 / 停用，缺失默认启用 | 否 | 下拉选择 |
| F | 排序 | 整数，默认 0 | 否 | — |

> **分类名称必填理由**：`KnowledgeItemDomainService.validateAndCreate()` 要求 `categoryIds` 非空，与现有创建 API 的 `@NotNull categoryIds` 一致。

### 2. Excel 批量导入

**端点**：`POST /api/admin/knowledge-items/import`

- Content-Type: multipart/form-data，字段名 `file`
- 文件类型：仅 .xlsx，最大 10MB
- 单次上限：200 行（数据行 > 200 直接拒绝，不逐行处理）

**逐行处理**（每行独立 try/catch，部分成功）：

| 校验项 | 失败原因示例 |
|--------|-------------|
| 标题为空 | 标题不能为空 |
| 标题超过 200 字符 | 知识条目标题不能超过 200 字 |
| Markdown正文为空 | Markdown正文不能为空 |
| Markdown正文超过 50000 字符 | 知识条目内容不能超过 50000 字 |
| 分类名称为空 | 知识条目必须关联至少一个分类 |
| 分类名称不存在或非 ACTIVE | 分类名称不存在或已停用：XXX |
| 状态不在 启用/停用 范围 | 状态格式错误：XXX，可选值 启用/停用 |
| 排序值非数字 | 排序值格式错误：XXX，必须为整数 |
| 标签超过 10 个 | 标签最多 10 个 |
| 标签长度超过 20 字符 | 标签长度不能超过 20 字: XXX |

**跳过行判定**：首列内容以 `【` 开头的行视为说明行，空行跳过，不计入 totalCount。

**边界**：仅含表头（无数据行）的模板 → 返回 `{ totalCount: 0, successCount: 0, failCount: 0, failDetails: [] }`，不报错。

**导入的条目** coverImage 为 null，contentHtml 在 `importOne()` 内部由 MarkdownRenderer 渲染，后续通过编辑功能单独上传封面图。

### 3. Markdown zip 包批量导入

**端点**：`POST /api/admin/knowledge-items/import-markdown`

- Content-Type: multipart/form-data，字段名 `file`
- 文件类型：仅 .zip，最大 20MB
- 单次上限：200 个 .md 文件

**zip 包结构**：

```
knowledge_items.zip
├── index.xlsx          ← 元数据：文件名 | 标题 | 标签 | 分类名称 | 状态 | 排序
├── java-basics.md      ← Markdown 正文
├── spring-ioc.md
└── design-patterns.md
```

**处理规则**：
- `index.xlsx` 中「文件名」列对应 zip 内 `.md` 文件名（含 `.md` 扩展名）
- 不在 `index.xlsx` 中的 `.md` 文件 → 忽略不导入
- `index.xlsx` 中列出但 `.md` 文件缺失 → 记入失败明细：「Markdown 文件不存在：xxx.md」
- `.md` 文件内容作为 Markdown正文 列的值（如果同时有 .md 文件和 index.xlsx 中的 Markdown正文列，以 .md 文件内容为准）

**index.xlsx 列定义**（表头在第 1 行，数据从第 2 行开始）：

| 列号 | 列名 | 说明 | 必填 |
|------|------|------|------|
| A | 文件名 | 对应 zip 内 .md 文件名（含 `.md` 扩展名） | ✅ |
| B | 标题 | 1~200 字符 | ✅ |
| C | 标签 | 逗号分隔，最多 10 个，每个最长 20 字符 | 否 |
| D | 分类名称 | KnowledgeCategory code，逗号分隔多选，必须全部存在且 ACTIVE | ✅ |
| E | 状态 | 启用 / 停用，默认启用 | 否 |
| F | 排序 | 整数，默认 0 | 否 |

**逐行校验**（同上 Excel 导入，增加文件名校验）：

| 校验项 | 失败原因示例 |
|--------|-------------|
| 文件名为空 | 文件名不能为空 |
| 标题为空 | 标题不能为空 |
| 标题超过 200 字符 | 知识条目标题不能超过 200 字 |
| .md 文件缺失 | Markdown 文件不存在：xxx.md |
| .md 文件内容为空 | Markdown正文不能为空 |
| .md 文件内容超过 50000 字符 | 知识条目内容不能超过 50000 字 |
| 分类名称为空 | 知识条目必须关联至少一个分类 |
| 分类名称不存在或非 ACTIVE | 分类名称不存在或已停用：XXX |
| 状态不在 启用/停用 范围 | 状态格式错误：XXX |
| 标签超过 10 个 | 标签最多 10 个 |
| 标签长度超过 20 字符 | 标签长度不能超过 20 字: XXX |

**边界**：仅含表头的 index.xlsx（无数据行）→ 返回空结果，不报错。

### 4. 导入结果 DTO

复用 `ImportFailDetail.java`（admin 模块已有），新增 `KnowledgeItemImportResult.java`：

```java
public class KnowledgeItemImportResult {
    int totalCount;
    int successCount;
    int failCount;
    List<ImportFailDetail> failDetails;
}
```

JSON 示例：

```json
{
  "totalCount": 10,
  "successCount": 8,
  "failCount": 2,
  "failDetails": [
    { "row": 3, "reason": "分类名称不存在或已停用：JAVA" },
    { "row": 7, "reason": "Markdown 文件不存在：missing-file.md" }
  ]
}
```

### 5. 前端交互

KnowledgeItem 列表页 toolbar 新增三个按钮：

- **下载模板**：点击后浏览器下载 `knowledge_item_import_template.xlsx`
- **Excel 批量导入**：点击触发 AntD Upload（beforeUpload 拦截校验 .xlsx 扩展名和 10MB 上限）→ POST 上传 → 完成后弹 ImportResultModal
- **Markdown zip 导入**：点击触发 AntD Upload（beforeUpload 拦截校验 .zip 扩展名和 20MB 上限）→ POST 上传 → 完成后弹 ImportResultModal

ImportResultModal：参考 QuestionBank 和 CardTemplate 模式，在 KnowledgeItem 目录下新增第三份副本（类型改为 `KnowledgeItemImportResult`），展示成功数/失败数统计 + 失败明细 AntD Table。三个 ImportResultModal 各自独立副本，不共享组件。

## 技术实现

### 后端新增文件

| 文件 | 说明 |
|------|------|
| `admin/api/dto/response/KnowledgeItemImportResult.java` | 导入结果 DTO，字段 int totalCount / int successCount / int failCount / List\<ImportFailDetail\> failDetails |

### 后端修改文件

| 文件 | 变更 |
|------|------|
| `admin/api/controller/KnowledgeItemController.java` | +downloadImportTemplate +importExcel +importMarkdownZip |
| `admin/application/service/KnowledgeItemAppService.java` | +downloadImportTemplate +importExcel +importMarkdownZip 方法（含文件校验） |

> `KnowledgeCategoryRepositoryPort.findAll()` 已存在（第 45 行），直接使用，无需修改 Port 和 Adapter。

### 配置变更

| 配置 | 变更 | 说明 |
|------|------|------|
| `admin/src/main/resources/application.yml` | `spring.servlet.multipart.max-file-size: 10MB → 20MB`<br>`spring.servlet.multipart.max-request-size: 10MB → 20MB` | 支持 Markdown zip 20MB 上传。Spring MultipartResolver 在框架层校验，AppService 层校验无法绕过 |

### 前端修改文件

| 文件 | 变更 |
|------|------|
| `frontend/admin/src/services/knowledge-item.ts` | +downloadImportTemplate +importExcel +importMarkdownZip API 函数 + KnowledgeItemImportResult 类型 |
| `frontend/admin/src/pages/KnowledgeItem/index.tsx` | toolbar 增加下载模板 + Excel 导入 + Markdown zip 导入按钮 + ImportResultModal |
| `frontend/admin/src/pages/KnowledgeItem/components/ImportResultModal.tsx` | 新增：导入结果展示 Modal（KnowledgeItemImportResult 类型，与 CardTemplate/QuestionBank 各有一份独立副本） |

### 复用

- `admin/api/dto/response/ImportFailDetail.java` — 共用失败明细 DTO
- Apache POI (`XSSFWorkbook`) — 项目中已有依赖
- `MarkdownRenderer` — 导入时渲染 contentHtml
- `KnowledgeItemDomainService.validateAndCreate()` — 复用创建校验逻辑（含分类非空 + 标题/内容/标签长度校验）
- `KnowledgeCategoryRepositoryPort.findAll()` — 已有方法，预加载 ACTIVE 分类 code→id 映射

### Excel 导入核心流程

```
importExcel(MultipartFile file)
  1. 校验文件扩展名（.xlsx）和大小（≤10MB）
  2. 打开 XSSFWorkbook
  3. 跳过表头行（第1行），统计数据行数（跳过以【开头的说明行和空行）
  4. totalCount == 0 → 返回空结果（不报错）
  5. totalCount > 200 → throw BusinessException("单次导入上限 200 行")
  6. 预加载所有 ACTIVE 分类 code→id 映射（调用 categoryRepositoryPort.findAll() 一次性查库）
  7. 逐行解析（从第2行开始）：
     - 跳过空行 + 填写说明行（首列以【开头）
     - parseRow() → 校验必填/枚举/分类名称
     - importOne() → markdownRenderer.render() → domainService.validateAndCreate() → itemRepository.save() → itemRepository.saveCategoryRelations()
     - try/catch per row, 失败记入 failDetails
  8. 返回 KnowledgeItemImportResult
```

### Markdown zip 导入核心流程

```
importMarkdownZip(MultipartFile file)
  1. 校验文件扩展名（.zip）和大小（≤20MB）
  2. 解压 zip，读取 index.xlsx + 所有 .md 文件到内存 Map<文件名, 内容>
  3. 从 index.xlsx 解析数据行（同 Excel 导入解析逻辑）
  4. totalCount == 0 → 返回空结果（不报错）
  5. totalCount > 200 → throw BusinessException("单次导入上限 200 行")
  6. 预加载所有 ACTIVE 分类 code→id 映射
  7. 逐行处理：
     - 用「文件名」列从 .md 内容 Map 中获取正文
     - .md 文件缺失 → 失败明细
     - parseRow() → 同 Excel 校验
     - importOne() → 同 Excel 导入
     - try/catch per row
  8. 返回 KnowledgeItemImportResult
```

### 校验职责

- **AppService 层**：文件类型校验（.xlsx/.zip）、大小校验（≤10MB/20MB）、行数上限、逐行解析、枚举转换
- **DomainService 层**：标题/内容/标签/分类名称校验（全部已有，`validateAndCreate()` 一条龙覆盖）

## Impact Analysis

- 纯增量功能，不影响现有 CRUD 接口
- 不修改领域模型（KnowledgeItem 聚合根无变化）
- 不修改数据库表结构
- 不修改 `KnowledgeCategoryRepositoryPort` / `KnowledgeCategoryRepositoryAdapter`（`findAll()` 已存在）
- 需修改 `application.yml` 的 multipart 上限（10MB → 20MB），影响所有 multipart 端点（现有 Excel 导入不受影响）
- 前端在现有页面 toolbar 增加按钮，不影响现有表单和列表

## Verification Plan

### 手动验证

1. 启动后端 + 前端
2. 导航到「知识条目管理」页
3. 点击「下载模板」→ 浏览器下载 `knowledge_item_import_template.xlsx`，确认列名和示例数据正确、标题/标签列为文本格式、状态列为下拉选择
4. Excel 导入：填写 5 行有效数据（含多个分类名称 + 标签）→ 上传 → 结果 Modal 显示全部成功
5. Excel 导入：填写含错误的 Excel（非法状态、不存在的分类名称、标签超限）→ 上传 → 失败明细正确
6. Excel 导入：仅含表头的空模板上传 → 返回 `{ totalCount: 0, successCount: 0 }`，不报错
7. Markdown zip 导入：准备 zip（index.xlsx + 3 个 .md 文件）→ 上传 → 全部成功
8. Markdown zip 导入：index.xlsx 引用缺失的 .md 文件 → 失败明细显示「Markdown 文件不存在」
9. 非 .xlsx/.zip 文件上传 → 前端 Upload beforeUpload 拦截
10. 超 200 行/200 个 .md 文件 → 后端拒绝并提示
11. 20MB zip 上传 → 成功（验证 multipart 上限已提升）

### 自动化测试

- **`KnowledgeItemAppServiceTest`**（单元测试，mock 依赖）：
  - mock `MultipartFile`：使用 `MockMultipartFile` 构造测试 Excel/zip
  - mock `KnowledgeCategoryRepositoryPort.findAll()` 返回预设分类列表
  - mock `KnowledgeItemRepository` 和 `KnowledgeItemDomainService`
  - 覆盖：正常导入 / 各校验失败路径 / 行数超限 / 分类名称不存在 / 空模板 / zip 中 .md 文件缺失
- **`KnowledgeItemControllerTest`**（`@WebMvcTest`）：
  - 使用 `mockMvc.perform(multipart(...).file(mockFile))` 测试两个导入端点
  - 使用 `mockMvc.perform(get(...))` 测试模板下载
- **前端**：
  - `knowledge-item.test.ts`：mock request，验证 API 函数的 URL、method、payload
  - `ImportResultModal.test.tsx`：渲染统计数字 + 失败明细表格

## 已知限制

- 导入不支持封面图（Excel/zip 无法传文件），导入后需通过编辑功能单独上传
- 状态列支持中文（启用/停用），不支持英文枚举值
- 无编码唯一性校验（知识条目无 code 字段）
- Markdown zip 导入将所有 .md 文件内容加载到内存，超大 zip（500+ 文件）可能 OOM
- 分类名称必填，无分类的知识条目需走手动创建
