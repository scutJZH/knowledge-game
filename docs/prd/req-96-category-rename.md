# REQ-96：分类管理页改名（原"知识库管理"）

> 状态：`designed`
> 创建：2026-06-18
> 前置依赖：REQ-65（原"知识库管理页"已实现）、REQ-40（管理后台脚手架）

## 1. 背景

当前管理后台的"知识库管理"页（路由 `/content/knowledge-base`）实际承载的是**知识点分类**的 CRUD（树形结构、拖拽排序、移动、增删改）。该命名容易让使用者误以为这里是"知识库内容（可学习的知识）"的管理入口。

产品定位上，"知识库"应当是承载**可被用户学习的内容**的场所（独立需求 REQ-97 知识条目管理 + REQ-98 用户端浏览页），与"分类管理"是两个完全不同的概念。

本需求将现有的"知识库管理"页改名为"分类管理"，使其命名与实际承载内容（知识点分类 CRUD）一致。

## 2. 范围

### 2.1 包含（前端展示层改名）

- 菜单标签、页面标题、面包屑：「知识库管理」→「分类管理」
- 路由：`/content/knowledge-base` → `/content/category`
- 组件目录：`frontend/admin/src/pages/KnowledgeBase/` → `src/pages/Category/`

### 2.2 不包含

- **后端代码、API、表名全部不动**：
  - 表名 `knowledge_category`（语义本就是"知识点分类"，符合新定位）
  - 后端类名 `KnowledgeCategory*`（保留以与未来其他分类区分，如卡牌分类、商品分类）
  - API 路径 `/api/admin/knowledge-categories`（前端 service 层 `services/knowledge-category.ts` 同步保留）
- **真正的"知识库"实体**：拆为独立需求 REQ-97（知识条目管理）
- **用户端知识库浏览页**：拆为独立需求 REQ-98
- **学习记录追踪 + 富媒体扩展**：拆为独立需求 REQ-99

## 3. 改动清单

### 3.1 路由配置

**文件：** `frontend/admin/config/routes.ts`

```diff
       {
-        path: '/content/knowledge-base',
-        name: '知识库管理',
-        component: './KnowledgeBase',
+        path: '/content/category',
+        name: '分类管理',
+        component: './Category',
       },
```

### 3.2 组件目录重命名

`frontend/admin/src/pages/KnowledgeBase/` → `frontend/admin/src/pages/Category/`

涉及的文件（一并迁移到新目录）：

- `index.tsx`
- `components/CategoryDetail.tsx`
- `components/CategoryFormModal.tsx`
- `components/CategoryTree.tsx`
- `components/MoveModal.tsx`
- `components/__tests__/CategoryDetail.test.tsx`
- `components/__tests__/CategoryFormModal.test.tsx`
- `components/__tests__/CategoryTree.test.tsx`
- `components/__tests__/MoveModal.test.tsx`

### 3.3 主组件改名

**文件：** `frontend/admin/src/pages/Category/index.tsx`（重命名后的位置）

```diff
-const KnowledgeBase: React.FC = () => {
+const Category: React.FC = () => {
```

```diff
-export default KnowledgeBase;
+export default Category;
```

## 4. 不需要改的地方（语义验证）

以下位置名称本就符合"分类管理"语义，无需调整：

| 文件 | 行号 | 现有内容 | 状态 |
|------|------|---------|------|
| `CategoryTree.tsx` | L158 | `title="分类管理"` | ✅ 已符合 |
| `CategoryFormModal.tsx` | L49 | `title={isEdit ? '编辑分类' : '新建分类'}` | ✅ 已符合 |
| `CategoryDetail.tsx` | L53 | `title={detail.name}` | ✅ 已符合 |
| `MoveModal.tsx` | L70 | `title="移动分类"` | ✅ 已符合 |
| `services/knowledge-category.ts` | — | 服务文件名（对应后端 API） | ✅ 保留（后端 API 不改名） |

## 5. 影响标注

### 5.1 对已完成需求的影响

| 需求 | 影响 |
|------|------|
| REQ-65 | REQ-65 实现的"知识库管理页"被 REQ-96 改名为"分类管理页"，路由 `/content/knowledge-base` → `/content/category`，组件目录 `pages/KnowledgeBase/` → `pages/Category/`。REQ-65 PRD 中的页面标题/路由描述不再准确，以 REQ-96 为准 |
| REQ-40 | REQ-40 的菜单配置中"知识库管理"标签由 REQ-96 改为"分类管理"，菜单层级和图标不变 |

### 5.2 不受影响

- REQ-07（知识点分类管理端 CRUD API）：后端 API、表名、类名全部保留
- REQ-43（题库管理页）：知识条目实体尚未引入（REQ-97），题库管理页不需要"关联知识条目"字段
- REQ-94（聚合根停用/启用校验）：知识条目实体尚未引入，停用/启用校验暂不涉及

### 5.3 对其他文档的影响（REQ-96 实施时同步更新）

> 位置引用采用"章节/表格 + 内容关键词"描述式，不依赖行号，避免文档变更后行号偏移。

| 文档 | 位置（描述式） | 现有引用 | 改为 |
|------|---------------|---------|------|
| `docs/features.md` | "已上线功能 → 管理后台前端" 列表项 | "知识库管理页（左侧分类树 + 右侧详情面板）" | "分类管理页（左侧分类树 + 右侧详情面板）" |
| `docs/features.md` | "规划中（Phase 3 群组系统）" 列表项 | "群组关联 IP 库和知识库" | "群组关联 IP 库"（知识库全局共享，不需要群组授权） |
| `docs/overview.md` | "管理后台前端页面"表格中"知识库管理"行 | "知识库管理 \| /content/knowledge-base \| ..." | "分类管理 \| /content/category \| ..." |
| `docs/overview.md` | "设计决策记录"表格中"知识库管理页布局"行 | "知识库管理页布局 \| ..." | "分类管理页布局 \| ..." |
| `knowledge-game.md` | 一、产品概述 | 已在 2026-06-15 提前添加"概念演进（REQ-96）"提示框（产品概述末尾），REQ-96 实施时无需修改该提示框 |
| `docs/requirements.md` | REQ-65 行的备注列 | "阶段一完成，阶段二（图片上传对接）拆分为 REQ-90" | 追加"；REQ-96 改名为分类管理页（路由 /content/category）" |

## 6. 验证清单

### 6.1 手动验证

1. 启动 admin 前端（端口 8000），登录后左侧菜单显示「分类管理」而不是「知识库管理」
2. 点击「分类管理」菜单，路由跳转至 `/content/category`
3. 页面正常加载，分类树渲染正常
4. 拖拽排序、新建/编辑/移动/删除分类功能全部正常
5. 4 个测试文件的测试用例运行通过（路径变化不影响断言）
6. 浏览器访问旧路由 `/content/knowledge-base` 应显示 404（不配置 redirect，用户量小可接受）

### 6.2 自动化验证

```bash
cd frontend/admin
npm run test    # 4 个组件测试文件应全部通过
npm run build   # 构建无错误
npm run lint    # 无新增 lint 警告
```

## 7. 测试策略

- **不新增测试**：纯改名无逻辑变更，现有测试断言不引用 `KnowledgeBase` 字面量
- **保留现有测试**：4 个组件测试文件随目录重命名，路径更新但断言不变
- **回归验证**：手动验证 6.1 节清单

## 8. 回滚标准

改动面小（2 处文件改动 + 1 处目录重命名），单 commit 完整回滚：

```bash
git revert <commit-hash>
```

或手动恢复：
1. `git mv frontend/admin/src/pages/Category frontend/admin/src/pages/KnowledgeBase`
2. 恢复 `routes.ts` 中 3 行配置
3. 恢复 `index.tsx` 中的组件名

## 9. 后续需求（不在本范围）

| 编号 | 名称 | 归类 Phase |
|------|------|-----------|
| REQ-97 | 知识条目 — 管理端 CRUD API + 管理页 | Phase 7 系统管理后台 |
| REQ-98 | 用户端 — 知识库浏览页（按分类浏览/阅读/学习埋点） | Phase 6 前端游戏界面 |
| REQ-99 | 知识库 — 学习记录追踪 + 富媒体扩展 | 后期优化 |
