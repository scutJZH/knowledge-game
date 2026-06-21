# REQ-110 卡牌管理 — 批量导入

## 产品定位

在管理后台卡牌管理页中实现 Excel 批量导入卡牌模板，参考 REQ-43 题库批量导入模式。运营人员下载模板 → 填写数据 → 上传导入，系统逐行校验并返回导入结果。

## 前置依赖

| 依赖 | 说明 | 状态 |
|------|------|------|
| REQ-17 | 卡牌模板管理端 CRUD API | done |
| REQ-45 | 卡牌管理前端页面 | done |
| REQ-16 | IP 系列管理端 CRUD API（用于校验 IP 系列 code 存在性） | done |

## 用户故事

**作为** 系统管理员
**我想要** 通过 Excel 批量导入卡牌模板
**以便于** 一次性录入大量卡牌数据，避免逐条手动创建

## 功能需求

### 1. 下载模板

**端点**：`GET /api/admin/card-templates/import-template`

返回文件名 `card_template_import_template.xlsx`。

**模板列定义**（表头在第 1 行，数据从第 2 行开始）：

| 列号 | 列名 | 说明 | 必填 |
|------|------|------|------|
| A | 编码 | 2~50 字符，同一 IP 系列下唯一 | ✅ |
| B | 名称 | 1~50 字符 | ✅ |
| C | 所属IP系列编码 | IP 系列的 code，必须存在且 ACTIVE | ✅ |
| D | 稀有度 | N / R / SR / SSR / SP | ✅ |
| E | 描述 | 最大 500 字符 | 否 |
| F | 状态 | 启用 / 停用，缺失默认启用 | 否 |

模板含 2 行示例数据 + 末尾 1 行填写说明（首列以 `【` 开头，导入时跳过）。

### 2. 批量导入

**端点**：`POST /api/admin/card-templates/import`

- Content-Type: multipart/form-data，字段名 `file`
- 文件类型：仅 .xlsx，最大 10MB
- 单次上限：200 行（数据行 > 200 直接拒绝，不逐行处理）

**逐行处理**（每行独立 try/catch，部分成功）：

| 校验项 | 失败原因示例 |
|--------|-------------|
| 编码/名称/稀有度 任一为空 | 编码不能为空 |
| 稀有度不在 N/R/SR/SSR/SP 范围 | 稀有度格式错误：XXX，可选值 N/R/SR/SSR/SP |
| 状态不在 启用/停用 范围 | 状态格式错误：XXX，可选值 启用/停用 |
| IP 系列 code 不存在或非 ACTIVE | IP系列编码不存在或已停用：XXX |
| 编码在 Excel 内重复 | 编码 PIKACHU 与第3行重复 |
| 编码在同一 IP 系列内已存在 | 编码 PIKACHU 在 IP 系列 PKM 内已存在 |

**说明**：编码唯一性范围为「同一 IP 系列内唯一」，与现有 `CardTemplateAppService.createCardTemplate` 的 `findByIpSeriesIdAndCode` 校验一致。同一编码可在不同 IP 系列下分别使用。

**跳过行判定**：首列内容以 `【` 开头的行视为说明行，空行跳过，不计入 totalCount。

**边界**：仅含表头（无数据行）的模板 → 返回 `{ totalCount: 0, successCount: 0, failCount: 0, failDetails: [] }`，不报错。

**导入结果 DTO**（`CardTemplateImportResult.java`，结构对齐 `QuestionImportResult`）：

```java
public class CardTemplateImportResult {
    int totalCount;
    int successCount;
    int failCount;
    List<ImportFailDetail> failDetails;  // 复用 admin 模块已有 DTO
}
```

JSON 示例：

```json
{
  "totalCount": 10,
  "successCount": 8,
  "failCount": 2,
  "failDetails": [
    { "row": 3, "reason": "编码 PIKACHU 在 IP 系列 PKM 内已存在" },
    { "row": 7, "reason": "IP系列编码不存在或已停用：XXX" }
  ]
}
```

导入的卡牌 `image` 字段为 null，后续通过编辑功能单独上传卡面图。

### 3. 前端交互

CardTemplate 列表页 toolbar 新增两个按钮：

- **下载模板**：点击后浏览器下载 `card_template_import_template.xlsx`
- **批量导入**：点击触发 AntD Upload（beforeUpload 拦截校验 .xlsx 扩展名和 10MB 上限）→ POST 上传 → 完成后弹 ImportResultModal 展示 totalCount / successCount / failCount + 失败明细表格（row、reason）

ImportResultModal 复用 REQ-43 模式：成功数/失败数统计 + 失败明细 AntD Table。

## 技术实现

### 后端新增文件

| 文件 | 说明 |
|------|------|
| `admin/api/dto/response/CardTemplateImportResult.java` | 导入结果 DTO，字段 int totalCount / int successCount / int failCount / List\<ImportFailDetail\> failDetails |

### 后端修改文件

| 文件 | 变更 |
|------|------|
| `admin/api/controller/CardTemplateController.java` | +downloadImportTemplate +importCardTemplates（文件校验在 AppService 层，与 REQ-09 一致） |
| `admin/application/service/CardTemplateAppService.java` | +downloadImportTemplate +importCardTemplates 方法（含文件类型/大小校验） |
| `core/domain/port/outbound/IpSeriesRepositoryPort.java` | +`List<IpSeries> findAll()` 方法 |
| `core/infrastructure/adapter/repoadapter/IpSeriesRepositoryAdapter.java` | +findAll 实现（委托 JPA `findAll()`） |

### 配置变更

| 配置 | 变更 | 说明 |
|------|------|------|
| `knowledge-game-admin.yml` (Nacos) | 新增 | `spring.servlet.multipart.max-file-size: 10MB` + `max-request-size: 10MB` |

admin 模块此前无 multipart 上传端点（图片上传走文件服务直传），此为首次接收 MultipartFile。

### 前端修改文件

| 文件 | 变更 |
|------|------|
| `frontend/admin/src/services/cardTemplate.ts` | +downloadImportTemplate +importCardTemplates API 函数 + CardTemplateImportResult 类型 |
| `frontend/admin/src/pages/CardTemplate/index.tsx` | toolbar 增加下载模板 + 批量导入按钮 + ImportResultModal |
| `frontend/admin/src/pages/CardTemplate/components/ImportResultModal.tsx` | 新增：导入结果展示 Modal |

### 复用

- `admin/api/dto/response/ImportFailDetail.java` — 与 REQ-09 共用失败明细 DTO
- Apache POI (`XSSFWorkbook`) — 项目中已有依赖
- `CardTemplateDomainService.validateAndCreate()` — 复用创建校验逻辑（含 IP 系列存在性+ACTIVE、编码同 IP 系列下唯一）

### 导入逻辑核心流程

```
importCardTemplates(MultipartFile file)
  1. 校验文件扩展名（.xlsx）和大小（≤10MB）—— 与 REQ-09 一致，校验在 AppService 层
  2. 打开 XSSFWorkbook
  3. 跳过表头行（第1行），统计数据行数（跳过以【开头的说明行和空行）
  4. totalCount == 0 → 返回空结果（不报错）
  5. totalCount > 200 → throw BusinessException("单次导入上限 200 行")
  6. 预加载所有 ACTIVE IP 系列 code→id 映射（调用 IpSeriesRepositoryPort.findAll() 一次性查库）
  7. 逐行解析（从第2行开始）：
     - 跳过空行 + 填写说明行（首列以【开头）
     - parseRow() → 校验必填/枚举/IP系列/code唯一性
     - importOne() → domainService.validateAndCreate() → repository.save()
     - try/catch per row, 失败记入 failDetails
  8. 返回 CardTemplateImportResult
```

### 校验职责

- **AppService 层**：文件类型校验（.xlsx）、大小校验（≤10MB）、行数上限、逐行业务校验、枚举转换（与 REQ-09 一致）
- **DomainService 层**：IP 系列存在性+ACTIVE（已有）、编码同 IP 系列下唯一（已有）

## Impact Analysis

- 纯增量功能，不影响现有 CRUD 接口
- 不修改领域模型（CardTemplate 聚合根无变化）
- 不修改数据库表结构
- `IpSeriesRepositoryPort` 新增 `findAll()` 方法（纯新增，现有方法不受影响）
- admin 模块首次启用 multipart 支持，需 Nacos 配置 `spring.servlet.multipart.*`
- 前端在现有页面 toolbar 增加按钮，不影响现有表单和列表

## Verification Plan

### 手动验证

1. 启动后端 + 前端
2. 导航到「卡牌管理」页
3. 点击「下载模板」→ 浏览器下载 `card_template_import_template.xlsx`，确认列名和示例数据正确
4. 填写 5 行有效数据（含不同 IP 系列下相同编码）→ 上传 → 结果 Modal 显示全部成功
5. 填写含错误的 Excel（非法稀有度、不存在的 IP 系列 code、同 IP 系列下重复编码）→ 上传 → 失败明细正确
6. 仅含表头的空模板上传 → 返回 `{ totalCount: 0, successCount: 0 }`，不报错
7. 非 .xlsx 文件上传 → 前端 Upload beforeUpload 拦截
8. 超 200 行上传 → 后端拒绝并提示

### 自动化测试

- **`CardTemplateAppServiceTest`**（单元测试，mock 依赖）：
  - mock `MultipartFile`：使用 `new MockMultipartFile("file", "test.xlsx", "...", byteArray)` 构造测试 Excel
  - mock `IpSeriesRepositoryPort.findAll()` 返回预设 IP 系列列表
  - mock `CardTemplateRepositoryPort` 和 `CardTemplateDomainService`
  - 覆盖：正常导入 / 各校验失败路径 / 行数超限 / Excel 内部 code 重复 / 同 IP 系列下编码已存在 / 空模板
- **`CardTemplateControllerTest`**（`@WebMvcTest`）：
  - 使用 `mockMvc.perform(multipart("/api/admin/card-templates/import").file(mockFile))` 测试导入端点
  - 使用 `mockMvc.perform(get("/api/admin/card-templates/import-template"))` 测试模板下载
- **前端**：
  - `cardTemplate.test.ts`：mock request，验证 importCardTemplates / downloadImportTemplate 的 URL、method、payload
  - `ImportResultModal.test.tsx`：渲染统计数字 + 失败明细表格

## 已知限制

- 导入不支持卡面图（Excel 无法传文件），导入后需通过编辑功能单独上传
- 状态列支持中文（启用/停用），不支持英文枚举值
- 编码唯一性为同一 IP 系列内唯一（非全局唯一），与现有 CRUD 行为一致
