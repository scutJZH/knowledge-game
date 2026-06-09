# REQ-07 知识点分类 — 管理端 CRUD API

## 产品定位

管理端对知识点分类进行增删改查。知识点分类是题库系统的顶层组织结构，采用树形层级（父子关系），全局独立于 IP 系列。题目通过知识点分类进行归类。

## 用户故事

**作为** 管理员
**我想要** 在管理后台对知识点分类进行创建、查询、修改、删除、移动操作
**以便于** 组织题库的知识点分类体系，为后续题库管理和游戏出题提供分类基础

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/knowledge-categories` | 创建分类 |
| GET | `/api/admin/knowledge-categories` | 分页查询分类列表 |
| GET | `/api/admin/knowledge-categories/tree` | 查询完整分类树 |
| GET | `/api/admin/knowledge-categories/{id}` | 查询分类详情 |
| PUT | `/api/admin/knowledge-categories/{id}` | 更新分类信息 |
| PUT | `/api/admin/knowledge-categories/{id}/move` | 移动分类到新父级 |
| DELETE | `/api/admin/knowledge-categories/{id}` | 删除分类（软删除，status→INACTIVE） |

### 数据模型

```
knowledge_category
├── id              BIGINT PK AUTO_INCREMENT
├── parent_id       BIGINT FK → knowledge_category.id  (NULL=顶级分类)
├── name            VARCHAR(50) NOT NULL                 -- 分类名称
├── description     VARCHAR(500)                         -- 分类描述
├── icon_url        VARCHAR(500)                         -- 图标URL
├── color           VARCHAR(20)                          -- 主题色(如 #FF5500)
├── cover_image_url VARCHAR(500)                         -- 封面图URL
├── sort_order      INT NOT NULL DEFAULT 0               -- 同级排序(升序)
├── status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE/INACTIVE 枚举
├── created_at      DATETIME NOT NULL
├── updated_at      DATETIME NOT NULL
```

### 创建分类

**请求体：**
```json
{
  "parentId": null,
  "name": "编程",
  "description": "编程相关知识分类",
  "iconUrl": "https://example.com/icons/code.png",
  "color": "#FF5500",
  "coverImageUrl": "https://example.com/covers/code.jpg",
  "sortOrder": 0
}
```

**字段规则：**
- `parentId`：可选，null 表示顶级分类，传值必须为已存在的 ACTIVE 状态分类 ID
- `name`：必填，2~50 字符，同一父级下唯一
- `description`：可选，最大 500 字符
- `iconUrl`：可选，最大 500 字符（URL 格式）
- `color`：可选，最大 20 字符（十六进制颜色值）
- `coverImageUrl`：可选，最大 500 字符（URL 格式）
- `sortOrder`：必填，默认 0，同级排序用

### 分页查询

**查询参数：**
- `keyword`（可选）：按名称模糊搜索
- `status`（可选）：按状态筛选（ACTIVE/INACTIVE）
- `parentId`（可选）：按父级筛选，-1 表示顶级分类
- `page`（默认 0）、`size`（默认 20）
- **默认排序**：ORDER BY sort_order ASC, created_at DESC

### 查询分类树

返回完整的树形结构，包含所有分类（含 INACTIVE 状态），管理端树形组件直接消费。

**响应体结构：**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "编程",
      "status": "ACTIVE",
      "iconUrl": "https://example.com/icons/code.png",
      "color": "#FF5500",
      "sortOrder": 0,
      "children": [
        {
          "id": 2,
          "name": "Java",
          "status": "ACTIVE",
          "iconUrl": null,
          "color": null,
          "sortOrder": 0,
          "children": []
        }
      ]
    }
  ]
}
```

### 更新分类信息

**请求体：**
```json
{
  "name": "编程语言",
  "description": "编程语言相关知识",
  "iconUrl": "https://example.com/icons/code-v2.png",
  "color": "#0055FF",
  "coverImageUrl": "https://example.com/covers/code-v2.jpg",
  "sortOrder": 1
}
```

所有字段可选（传 null 表示不修改）。不可修改 parentId，移动使用专用 API。

### 移动分类

**请求体：**
```json
{
  "newParentId": 5
}
```

- `newParentId`：目标父级 ID，null 表示移到顶级，目标分类必须为 ACTIVE 状态
- 不允许将自己设为自己的父级，不允许将分类移动到自己的后代下

### 删除分类

- 软删除：将 status 置为 INACTIVE，与 IpSeries/CardTemplate 删除行为一致
- 删除后数据保留，管理端可查，用户端不可见

### 领域行为方法

```java
public class KnowledgeCategory {
    // 工厂方法：创建新分类
    static KnowledgeCategory create(name, description, parentId, iconUrl, color, coverImageUrl, sortOrder)
    // 重建方法：从持久化层重建领域对象
    static KnowledgeCategory reconstruct(id, name, description, parentId, iconUrl, color, coverImageUrl, sortOrder, status, createdAt, updatedAt)
    // 更新基本信息（不含 parentId）
    void update(name, description, iconUrl, color, coverImageUrl, sortOrder)
    // 移动到新父级
    void moveTo(newParentId)
    // 软删除（status→INACTIVE）
    void deactivate()
    // 判断 targetId 是否是自己的祖先（用于循环引用校验）
    boolean isDescendantOf(targetId, categoryIdPath)
}
```

## 业务规则

1. 同一父级下分类名称不可重复（包含 INACTIVE 记录，防止恢复时冲突）
2. 创建子分类时，parentId 对应的分类必须为 ACTIVE 状态
3. 不能将自己设为自己的父级
4. 不能将分类移动到自己的后代节点下
5. 移动分类时，目标父级分类必须为 ACTIVE 状态
6. INACTIVE 状态的分类不出现在用户端查询，管理端可见

## 验收标准

- [ ] DDD 各层完整：Controller → AppService → DomainService → RepositoryPort → RepositoryAdapter
- [ ] 数据库表 `knowledge_category` 自动建表（JPA DDL auto）
- [ ] 创建时同级名称唯一性校验
- [ ] 创建时父级分类状态校验（必须 ACTIVE）
- [ ] 移动时循环引用校验（不能移到自己的后代下）
- [ ] 删除为软删除（status→INACTIVE），与 IpSeries/CardTemplate 模式一致
- [ ] 树形查询返回完整层级结构（含 INACTIVE 节点）
- [ ] 分页查询支持名称搜索 + 状态筛选 + 父级筛选，默认排序 sort_order ASC, created_at DESC
- [ ] 统一返回体 `Result<T>` 包装
- [ ] 参数校验（@Valid + BusinessException）
- [ ] 单元测试覆盖正常路径 + 异常路径 + 边界条件

## 技术决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 树形存储 | 邻接表（parent_id 外键） | 知识点分类层级不深（2-4层），邻接表 + MySQL 8 CTE 足够；移动节点只需改 parent_id，维护成本低 |
| 数据库表名 | `knowledge_category` | - |
| 主键策略 | 自增 BIGINT | - |
| 状态字段 | VARCHAR(20) + 领域枚举 `KnowledgeCategoryStatus(ACTIVE/INACTIVE)` | 与 IpSeriesStatus/CardTemplateStatus 保持一致，使用 @Enumerated(EnumType.STRING) |
| 删除策略 | 软删除（status→INACTIVE） | 与 IpSeries/CardTemplate 删除模式一致 |
| 树形 vs 扁平 | 选择树形 | 产品文档当前分类为扁平列表，但预留树形扩展能力以支持未来细分领域（如：编程→Java→集合框架），避免后期改表 |
| 分页 | Spring Data Pageable | - |
| 鉴权 | 已有 JWT 鉴权（REQ-06） | 管理端 API 受 ADMIN 角色保护 |
