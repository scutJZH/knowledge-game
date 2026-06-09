# REQ-16 IP 系列 — 管理端 CRUD API

## 产品定位

管理端对 IP 系列进行增删改查。IP 系列是卡牌收集系统的顶层分组（如宝可梦、海贼王等），每张卡牌属于一个 IP 系列。

## 用户故事

**作为** 管理员
**我想要** 在管理后台对 IP 系列进行创建、查询、修改、删除操作
**以便于** 管理卡牌所属的 IP 分组和后续的盲盒抽卡池配置

## 功能需求

### API 列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/ip-series` | 创建 IP 系列 |
| GET | `/api/admin/ip-series` | 分页查询 IP 系列列表 |
| GET | `/api/admin/ip-series/{id}` | 查询单个 IP 系列详情 |
| PUT | `/api/admin/ip-series/{id}` | 更新 IP 系列 |
| DELETE | `/api/admin/ip-series/{id}` | 删除 IP 系列 |

### 创建 IP 系列

**请求体：**
```json
{
  "code": "POKEMON",
  "name": "宝可梦",
  "description": "精灵宝可梦系列卡牌",
  "coverImageUrl": "https://example.com/pokemon.jpg",
  "status": "ACTIVE"
}
```

**字段规则：**
- `code`：必填，2~30 字符，唯一（如 POKEMON、ONE_PIECE）
- `name`：必填，2~50 字符，唯一
- `description`：可选，最大 500 字符
- `coverImageUrl`：可选，最大 500 字符（URL 格式）
- `status`：必填，枚举 `ACTIVE` / `INACTIVE`，默认 `ACTIVE`

### 分页查询

**查询参数：**
- `name`（可选）：按名称模糊搜索
- `status`（可选）：按状态筛选
- `page`（默认 0）、`size`（默认 20）

### 更新 IP 系列

请求体同创建，所有字段可选（传 null 表示不修改）。

### 删除 IP 系列

- 软删除（status 置为 `INACTIVE`），保留数据
- 若该 IP 下已有卡牌，不允许删除（返回业务异常）

## 验收标准

- [ ] DDD 各层完整：Controller → AppService → DomainService → RepositoryPort → RepositoryAdapter
- [ ] 数据库表 `ip_series` 自动建表（JPA DDL auto）
- [ ] 创建时 code、name 唯一性校验
- [ ] 删除时校验关联卡牌（预留接口，当前可跳过实际校验）
- [ ] 统一返回体 `Result<T>` 包装
- [ ] 参数校验（@Valid + BusinessException）
- [ ] 分页查询支持名称模糊搜索 + 状态筛选

## 技术决策

| 决策项 | 选择 |
|--------|------|
| 数据库表名 | `ip_series` |
| 主键策略 | 自增 BIGINT |
| 软删除 | status 字段标记，不物理删除 |
| 分页 | Spring Data Pageable |
| 暂不含鉴权 | JWT 未实现，管理端 API 暂不拦截 |
