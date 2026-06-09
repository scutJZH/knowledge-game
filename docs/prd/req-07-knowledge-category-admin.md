# REQ-07 知识点分类 — 管理端 CRUD API

## 产品定位

管理端对知识点分类进行增删改查。知识点分类是题库系统的顶层组织结构，采用树形层级（父子关系），全局独立于 IP 系列。题目通过知识点分类进行归类。

## 用户故事

**作为** 管理员
**我想要** 在管理后台对知识点分类进行创建、查询、修改、删除、移动、启用/停用操作
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
| PUT | `/api/admin/knowledge-categories/{id}/status` | 启用/停用分类 |
| DELETE | `/api/admin/knowledge-categories/{id}` | 删除分类 |

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
├── status          TINYINT NOT NULL DEFAULT 1           -- 1=启用 0=停用
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
- `parentId`：可选，null 表示顶级分类，传值必须为已存在的分类 ID
- `name`：必填，2~50 字符，同一父级下唯一
- `description`：可选，最大 500 字符
- `iconUrl`：可选，最大 500 字符（URL 格式）
- `color`：可选，最大 20 字符（十六进制颜色值）
- `coverImageUrl`：可选，最大 500 字符（URL 格式）
- `sortOrder`：必填，默认 0，同级排序用

### 分页查询

**查询参数：**
- `keyword`（可选）：按名称模糊搜索
- `status`（可选）：按状态筛选（1=启用，0=停用）
- `parentId`（可选）：按父级筛选，-1 表示顶级分类
- `page`（默认 0）、`size`（默认 20）

### 查询分类树

返回完整的树形结构，包含所有分类（含停用的），管理端树形组件直接消费。

**响应体结构：**
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "编程",
      "children": [
        { "id": 2, "name": "Java", "children": [] },
        { "id": 3, "name": "Python", "children": [] }
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

- `newParentId`：目标父级 ID，null 表示移到顶级
- 不允许将自己设为自己的父级，不允许将分类移动到自己的后代下

### 启用/停用分类

**请求体：**
```json
{
  "status": 0
}
```

- `status`：1=启用，0=停用
- 停用分类不影响子分类状态（子分类独立控制）

### 删除分类

- 物理删除，仅作为停用的补充手段
- 前提条件：无子分类、无关联题目（当前题目表未实现，预留校验接口）
- 不满足条件时返回业务异常

## 业务规则

1. 同一父级下分类名称不可重复
2. 不能将自己设为自己的父级
3. 不能将分类移动到自己的后代节点下
4. 停用的分类不出现在用户端查询，管理端可见
5. 删除操作仅无子分类且无关联题目时可用

## 验收标准

- [ ] DDD 各层完整：Controller → AppService → DomainService → RepositoryPort → RepositoryAdapter
- [ ] 数据库表 `knowledge_category` 自动建表（JPA DDL auto）
- [ ] 创建时同级名称唯一性校验
- [ ] 移动时循环引用校验（不能移到自己的后代下）
- [ ] 删除时子分类关联校验
- [ ] 树形查询返回完整层级结构
- [ ] 分页查询支持名称搜索 + 状态筛选 + 父级筛选
- [ ] 统一返回体 `Result<T>` 包装
- [ ] 参数校验（@Valid + BusinessException）
- [ ] 单元测试覆盖正常路径 + 异常路径 + 边界条件

## 技术决策

| 决策项 | 选择 |
|--------|------|
| 树形存储 | 邻接表（parent_id 外键），层级不深时足够 |
| 数据库表名 | `knowledge_category` |
| 主键策略 | 自增 BIGINT |
| 软删除 | status 字段标记（0=停用），另提供物理删除 API |
| 分页 | Spring Data Pageable |
| 鉴权 | 已有 JWT 鉴权（REQ-06），管理端 API 受 ADMIN 角色保护 |
