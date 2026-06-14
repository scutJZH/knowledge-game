# REQ-90：知识库管理页 — 图片字段对接文件上传 + ImageUploadField 通用组件抽象

> 状态：`designed`
> 创建：2026-06-14
> 前置依赖：REQ-65（知识库管理页阶段一，已完成）、REQ-87（Admin 对接文件服务，已完成）、REQ-83（文件服务，已完成）

## 1. 概述

完成 REQ-65 阶段二的拆分工作。两件事：

1. **知识库管理页图片字段升级**：将 `CategoryFormModal` 中的 `iconUrl`/`coverImageUrl` 从 URL 文本输入升级为凭证式文件上传（对接 REQ-87 上传凭证接口）
2. **ImageUploadField 通用组件抽象**：抽出统一的图片上传组件，迁移现有 `CoverImageUpload`（IpSeries）和 `StarImageUpload`（CardTemplate）使用，消除现有重复实现

## 2. 用户故事

- **作为管理员**，我希望新建/编辑知识点分类时，能直接选择本地图片上传为图标和封面图，无需先在外部图床获取 URL
- **作为管理员**，我希望上传时有类型和大小校验，并展示上传进度，上传失败有明确提示
- **作为开发者**，我希望图片上传逻辑收敛到统一组件，新增业务类型时只需传 `bizType` 参数，无需复制粘贴整段上传代码

## 3. 背景

REQ-65 在阶段一交付时，将图片字段降级为 URL 文本输入以规避前端集成风险，并在 PRD 第 8 节明确阶段二拆分。当前后端依赖已全部就绪：

| 依赖 | 状态 | 说明 |
|------|------|------|
| REQ-87 上传凭证 API | 已完成 | `GET /api/admin/upload-credential?bizType=xxx` 返回 `{ token, uploadUrl }` |
| bizType 映射（`IP_SERIES`/`CARD_TEMPLATE`） | 已完成 | admin 端 `FilePathMapping` 已注册这两条，无需新增 |
| bizType 映射（`CATEGORY_ICON`/`CATEGORY_COVER`） | **需新增** | admin 端 `FilePathMapping` 当前未注册，本轮需新增（详见 4.2.5） |
| 前端服务层 | 已完成 | `services/fileUpload.ts` 提供 `getUploadCredential` / `uploadFile` |

现有重复实现：

| 组件 | 位置 | bizType | 差异 |
|------|------|---------|------|
| CoverImageUpload | `pages/IpSeries/index.tsx`（内嵌，第 30-104 行） | `IP_SERIES` | 支持删除 |
| StarImageUpload | `pages/CardTemplate/components/StarImageUpload.tsx`（独立文件） | `CARD_TEMPLATE` | 支持图片预览，不支持删除 |

两者上传逻辑（凭证获取、文件校验、customRequest、错误处理）几乎完全一致，仅 bizType、占位文字、是否预览/删除有差异。

## 4. 设计方案

### 4.1 ImageUploadField 通用组件

**位置：** `frontend/admin/src/components/ImageUploadField/index.tsx`

**Props：**

```typescript
interface ImageUploadFieldProps {
  /** 业务类型，对应后端 bizType（如 'CATEGORY_ICON'） */
  bizType: string;
  /** 上传占位区域显示的文字，默认「上传图片」 */
  placeholder?: string;
  /** 是否允许预览（点击缩略图弹出大图），默认 true */
  preview?: boolean;
  /** 是否允许删除（渲染 removeIcon），默认 true */
  allowRemove?: boolean;
  /** 受控值（完整图片 URL） */
  value?: string;
  /** 值变更回调 */
  onChange?: (value: string | undefined) => void;
  /** 文件大小上限（字节），默认 10MB */
  maxSize?: number;
  /** 允许的文件类型，默认 ['image/jpeg','image/png','image/gif','image/webp'] */
  acceptTypes?: string[];
}
```

**bizType 与 basePath 对照表（admin 端 FilePathMapping）：**

| bizType（前端传） | basePath（file 服务存储路径） | 使用点 | 本轮状态 |
|------------------|------------------------------|--------|---------|
| `CATEGORY_ICON` | `category-icon` | KnowledgeBase 图标 | **新增映射** |
| `CATEGORY_COVER` | `category-cover` | KnowledgeBase 封面图 | **新增映射** |
| `IP_SERIES` | `ip-series` | IpSeries 封面图 | 已注册，迁移组件 |
| `CARD_TEMPLATE` | `card-template` | CardTemplate 星级图片 | 已注册，迁移组件 |

**统一行为：**
- 文件类型校验：默认 JPG/PNG/GIF/WebP，不通过 `message.warning`
- 文件大小校验：默认 ≤10MB，不通过 `message.warning`
- 凭证式上传：调 `getUploadCredential(bizType)` → `uploadFile(...)` → `onChange(fullUrl)`
- 上传中状态：`uploading` state 控制占位区域显隐
- 错误处理：捕获异常 → `message.error(e.message || '上传失败')`
- 用户未登录兜底：`userInfo` 为空时抛「用户信息获取失败，请重新登录」
- 预览模式（`preview=true`）：点击缩略图 → Image 组件 `preview.visible` 控制
- 删除模式（`allowRemove=true`）：渲染 `showRemoveIcon: true`，点击 → `onChange(undefined)`

**Ant Design `Upload` 配置：**
- `accept`：根据 `acceptTypes` 拼接（如 `image/jpeg,image/png,image/gif,image/webp`）
- `maxCount={1}`：单图
- `listType="picture-card"`：缩略图卡片
- `fileList`：根据 `value` 受控构造 `[{ uid: '-1', name, status: 'done', url: value }]`
- `beforeUpload`：返回 false 时 Ant Design 不会触发 `customRequest`
- `customRequest`：凭证式上传实现，调用 `onSuccess`/`onError` 通知 Upload 内部状态

### 4.2 改动范围

#### 4.2.1 新增

| 文件 | 说明 |
|------|------|
| `frontend/admin/src/components/ImageUploadField/index.tsx` | 通用图片上传组件 |
| `frontend/admin/src/components/ImageUploadField/__tests__/index.test.tsx` | 组件单元测试 |

#### 4.2.2 修改 — KnowledgeBase 接入（本次主目标）

| 文件 | 变更内容 |
|------|---------|
| `frontend/admin/src/pages/KnowledgeBase/components/CategoryFormModal.tsx` | `iconUrl` 字段：`ProFormText` → `ProForm.Item + <ImageUploadField bizType="CATEGORY_ICON" placeholder="上传图标" />`；`coverImageUrl` 字段同理使用 `bizType="CATEGORY_COVER"`。表单提交逻辑保持不变（`values.iconUrl || undefined`） |
| `frontend/admin/src/pages/KnowledgeBase/__tests__/CategoryFormModal.test.tsx`（如存在） | 新增上传成功 / 上传失败 / 文件校验失败的测试用例 |

#### 4.2.3 修改 — IpSeries 迁移

| 文件 | 变更内容 |
|------|---------|
| `frontend/admin/src/pages/IpSeries/index.tsx` | 删除内嵌的 `CoverImageUpload`（第 30-104 行），引入 `ImageUploadField`，Modal 中 `<CoverImageUpload />` 改为 `<ImageUploadField bizType="IP_SERIES" placeholder="上传封面图" allowRemove />`。**注意**：当前 CoverImageUpload 中 `handleRemove` 调用 `onChange(undefined)`，迁移后由组件内部统一处理。 |
| `frontend/admin/src/pages/IpSeries/__tests__/index.test.tsx` | 已有 coverImageUrl 相关断言（第 57、282 行），需确认迁移后断言仍通过 |

#### 4.2.4 修改 — CardTemplate 迁移

| 文件 | 变更内容 |
|------|---------|
| `frontend/admin/src/pages/CardTemplate/components/StarImageUpload.tsx` | **删除整个文件**，逻辑收敛到 ImageUploadField |
| `frontend/admin/src/pages/CardTemplate/index.tsx` | 星级图片槽位的 `<StarImageUpload starLevel={n} />` 替换为 `<ImageUploadField bizType="CARD_TEMPLATE" placeholder={`★${n}`} preview allowRemove={false} />` |
| `frontend/admin/src/pages/CardTemplate/__tests__/StarImageUpload.test.tsx` | **删除整个文件**（测试目标已不存在） |
| `frontend/admin/src/pages/CardTemplate/__tests__/index.test.tsx` | 星级图片相关用例：mock 从 `StarImageUpload` 改为 mock `ImageUploadField`，断言不变 |

> **关于 REQ-92**：REQ-92（卡牌模板图片模型简化 — 单图替代多星级图）将在后续落地，StarImageUpload 的多星级场景会被简化为单图。本次迁移到 ImageUploadField 后，REQ-92 只需在 CardTemplate/index.tsx 中删掉多星级循环、保留一个 `<ImageUploadField bizType="CARD_TEMPLATE" />` 即可，ImageUploadField 本身无需改动。在 PRD 中显式标注此依赖关系，避免 REQ-92 实施时误以为还要再次重构上传组件。

#### 4.2.5 后端改动 — admin 端 FilePathMapping 新增映射

**背景：** 当前 admin 端 `FilePathMapping` 仅注册 `IP_SERIES`/`CARD_TEMPLATE`，新增 `CATEGORY_ICON`/`CATEGORY_COVER` 才能让 KnowledgeBase 图片上传走通凭证接口。

**注意：** `Map.of(...)` 最多支持 10 个键值对，目前 4 条远未到上限，可直接在原 `Map.of` 中追加。`Map.of` 返回不可变 Map，无需改实现。

| 文件 | 变更内容 |
|------|---------|
| `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/config/FilePathMapping.java` | `MAPPING` 中追加 `"CATEGORY_ICON" → "category-icon"` 和 `"CATEGORY_COVER" → "category-cover"` 两条映射 |
| `backend/knowledge-game-admin/src/test/java/com/knowledgegame/admin/config/FilePathMappingTest.java` | `shouldReturnBasePath_forRegisteredBizType` 测试方法中追加两条 `assertEquals` 断言（验证 `CATEGORY_ICON` → `category-icon`、`CATEGORY_COVER` → `category-cover`） |

**范围说明：** 仅 admin 端 FilePathMapping 改动。app 端 FilePathMapping 不动（YAGNI — REQ-90 是管理端需求；未来用户端如需上传分类图标/封面，再单独添加，不在本次范围）。

### 4.3 不在本次范围

| 项目 | 原因 |
|------|------|
| 颜色字段升级为 ColorPicker | REQ-65 PRD 提到 ColorPicker，但 REQ-90 范围只是图片上传，颜色字段维持文本输入 |
| REQ-88 清空字段问题 | 见第 5 节「已知限制」 |
| 用户端（app）的图片上传 | 仅管理端 3 个使用点，用户端上传组件（如头像）属于其他需求 |
| 批量上传（count > 1） | 当前所有场景都是单图，count 默认 1 |

## 5. 已知限制：REQ-88（update 接口 null 语义）

### 5.1 问题描述

后端 `KnowledgeCategoryConverter`、`IpSeriesConverter`、`CardTemplateConverter` 均配置 `NullValuePropertyMappingStrategy.IGNORE`，MapStruct 在 update 时会跳过 null 字段。前端调用：

- `CategoryFormModal` 提交：`iconUrl: values.iconUrl || undefined`
- `IpSeries` ModalForm 提交：`coverImageUrl: values.coverImageUrl ?? null`
- `CardTemplate` 编辑：星级图片走单独的 `addOrUpdateStarImage` 接口（idempotent），不受影响

用户在 UI 上删除图片后，前端值变为 `undefined`/`null`，提交时后端 IGNORE → 字段实际未清空 → 下次编辑时图片仍然存在。

### 5.2 本次处理方式

**不在 REQ-90 中解决**，PRD 中明确标注为已知限制。理由：

1. REQ-88 是横向需求，影响 `KnowledgeCategory`/`IpSeries`/`CardTemplate`/未来的 `Shop` 等所有管理端更新接口，统一方案更合适
2. REQ-88 设计方案未定（sentinel 值 vs clear 标记），强行在 REQ-90 引入可能与未来方案冲突
3. CardTemplate 星级图片不调用 update，不受此问题影响；KnowledgeBase 和 IpSeries 两个使用点受影响，但用户感知是「删除后下次编辑图片还在」，属于 UX 瑕疵而非数据正确性问题

### 5.3 临时缓解措施（PRD 显式标注）

- `ImageUploadField` 保留 `allowRemove` 默认 true，前端可正常删除（UX 一致）
- 待 REQ-88 落地后，REQ-90 自动受益（无需修改前端，后端 update 接口支持清空后即可工作）

## 6. Verification Plan

### 6.1 自动化测试

**ImageUploadField 单元测试**（`components/ImageUploadField/__tests__/index.test.tsx`）：

| 用例 | 覆盖点 |
|------|--------|
| 渲染 — 无 value | 显示上传占位，不显示 fileList |
| 渲染 — 有 value | fileList 显示缩略图，占位隐藏 |
| 文件类型校验失败 | mock `message.warning`，`beforeUpload` 返回 false |
| 文件大小校验失败 | 同上 |
| 上传成功 | mock `getUploadCredential` + `uploadFile`，断言 `onChange` 被调用 |
| 上传失败 | mock `uploadFile` reject，断言 `message.error` |
| 用户未登录 | mock `getUserInfo` 返回 null，断言抛错并 `message.error` |
| 删除（allowRemove=true） | 渲染 removeIcon，点击触发 `onChange(undefined)` |
| 禁用删除（allowRemove=false） | 不渲染 removeIcon |
| 预览（preview=true） | 点击缩略图触发 Image preview visible |

**KnowledgeBase 测试**（修改 `CategoryFormModal.test.tsx`）：

| 用例 | 覆盖点 |
|------|--------|
| iconUrl 字段渲染 ImageUploadField | bizType=CATEGORY_ICON |
| coverImageUrl 字段渲染 ImageUploadField | bizType=CATEGORY_COVER |
| 表单提交携带 iconUrl/coverImageUrl | 不破坏现有提交逻辑 |

**IpSeries 测试**（修改 `index.test.tsx`）：

| 用例 | 覆盖点 |
|------|--------|
| 现有 coverImageUrl 渲染断言 | 迁移后保持通过 |
| CoverImageUpload 内嵌组件已移除 | grep 确认不再出现 `const CoverImageUpload` |

**CardTemplate 测试**（修改 `index.test.tsx`，删除 `StarImageUpload.test.tsx`）：

| 用例 | 覆盖点 |
|------|--------|
| 星级图片区使用 ImageUploadField | 5 个 ImageUploadField 实例，bizType=CARD_TEMPLATE，placeholder=`★1`~`★5` |
| 现有上传 / 校验用例 | 迁移到 ImageUploadField 后通过 mock 重新验证 |

### 6.2 手动验证

1. **知识库管理页**
   - 新建分类：上传 icon 和 cover，提交成功，详情正确展示
   - 编辑分类：现有 URL 回填到 ImageUploadField 显示缩略图
   - 上传中：占位隐藏， fileList 显示 loading
   - 文件类型不符：弹 warning，不发起上传
   - 文件超 10MB：弹 warning，不发起上传
   - 上传失败（断网或文件服务停）：弹 error，可重试
2. **IP 系列管理页**（迁移后回归）
   - 新建 IP 系列：上传封面图，提交成功
   - 编辑 IP 系列：现有封面图回填
3. **卡牌管理页**（迁移后回归）
   - 编辑卡牌：5 个星级图片上传槽位正常工作
   - 单星级图片上传成功后回填
   - 点击缩略图弹大图预览

### 6.3 验证命令

```bash
# 前端类型检查（admin 端）
cd frontend/admin && npx tsc --noEmit

# 前端单测（admin 端）
cd frontend/admin && npm test -- --testPathPattern="(ImageUploadField|KnowledgeBase|IpSeries|CardTemplate)"

# 全量单测
cd frontend/admin && npm test
```

完成标准：tsc 零新增 error，jest 全绿（包含迁移后的 IpSeries/CardTemplate 既有用例）。

## 7. 影响分析

### 7.1 受影响的现有功能

| 功能 | 影响 |
|------|------|
| IpSeries 封面图上传 | 内嵌 CoverImageUpload 替换为 ImageUploadField，行为等价（已通过单测保证） |
| CardTemplate 星级图片上传 | StarImageUpload 独立组件删除，由 ImageUploadField 接管，行为等价 |
| KnowledgeBase 新建/编辑分类 | URL 文本输入替换为文件上传，UX 改善 |
| admin 端 FilePathMapping | 新增 `CATEGORY_ICON`/`CATEGORY_COVER` 两条映射；凭证接口本身逻辑无变化（仍按 FilePathMapping 查询） |

### 7.2 测试文件变更清单

| 文件 | 操作 | 用例覆盖 |
|------|------|---------|
| `frontend/admin/src/components/ImageUploadField/__tests__/index.test.tsx` | 新增 | 渲染/校验/上传成功失败/未登录/删除/预览（约 10 例） |
| `frontend/admin/src/pages/KnowledgeBase/components/__tests__/CategoryFormModal.test.tsx` | 修改（或新增，若文件不存在） | 新增 iconUrl/coverImageUrl 字段渲染为 ImageUploadField 的断言 |
| `frontend/admin/src/pages/IpSeries/__tests__/index.test.tsx` | 修改 | 迁移后回归，既有 coverImageUrl 断言保持通过 |
| `frontend/admin/src/pages/CardTemplate/__tests__/index.test.tsx` | 修改 | mock 从 StarImageUpload 切换到 ImageUploadField，断言不变 |
| `frontend/admin/src/pages/CardTemplate/__tests__/StarImageUpload.test.tsx` | 删除 | 测试目标已不存在 |
| `backend/knowledge-game-admin/src/test/java/com/knowledgegame/admin/config/FilePathMappingTest.java` | 修改 | 新增 2 条 `assertEquals` 断言（CATEGORY_ICON/CATEGORY_COVER） |

### 7.3 兼容性

- 已有数据库 `iconUrl`/`coverImageUrl` 字段值（旧 URL 数据）→ ImageUploadField 受控渲染，正常显示缩略图
- 既有 IpSeries/CardTemplate 测试用例中硬编码的 URL mock → 迁移后保持兼容（组件受控值不变）

## 8. 回滚标准

- 删除 `frontend/admin/src/components/ImageUploadField/` 目录
- 还原 `CategoryFormModal.tsx`：iconUrl/coverImageUrl 恢复为 `ProFormText`
- 还原 `IpSeries/index.tsx`：恢复内嵌 `CoverImageUpload` 定义与引用
- 还原 `CardTemplate/components/StarImageUpload.tsx` 与对应测试文件
- 还原 `CardTemplate/index.tsx`：星级图片槽位恢复使用 StarImageUpload
- 删除 `backend/knowledge-game-admin/src/main/java/com/knowledgegame/admin/config/FilePathMapping.java` 中新增的 `CATEGORY_ICON`/`CATEGORY_COVER` 映射
- 删除 `FilePathMappingTest.java` 中新增的 2 条 `assertEquals` 断言

## 9. 后续依赖

| 后续需求 | 影响 |
|---------|------|
| REQ-88（update 接口清空字段） | 落地后 REQ-90 的删除图片 UX 自动完整，无需修改前端 |
| REQ-92（卡牌图片模型简化） | 本次抽象后 ImageUploadField 无需改动，仅 CardTemplate/index.tsx 删除多星级循环 |
