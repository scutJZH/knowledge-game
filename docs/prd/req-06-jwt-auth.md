# REQ-06 JWT 鉴权拦截 + 角色权限

## 背景

用户端 (8080) JWT 认证已实现（登录/注册/刷新 Token），但存在两个缺口：

1. 管理端 (8081) 完全开放，所有请求 `permitAll()`
2. ADMIN 角色存在但未用于端点保护

## 需求范围

### 1. Token 黑名单（component-auth）

**目的：** 支持登出功能，使未过期的 Token 主动失效。

- `JwtTokenProvider.generateAccessToken()` 增加 `jti`（UUID）claim，唯一标识每张 Token
- `JwtTokenProvider.generateRefreshToken()` 同样增加 `jti` claim + `role` claim
- 新增 `TokenBlacklist` 接口 + 内存实现
  - `blacklist(String jti, Instant expireAt)` — 加入黑名单
  - `isBlacklisted(String jti)` — 检查是否在黑名单
  - 惰性清理：查询时删除已过期条目（定期全量扫描留到 REQ-81 Redis 方案时处理）
- `JwtAuthenticationFilter` 验证 Token 后提取 `jti` 检查黑名单，命中则跳过认证
- **旧 Token 兼容：** jti 为 null（上线前签发的旧 Token）直接放行，避免强制所有用户重新登录
- `AuthAutoConfiguration` 注册 `TokenBlacklist` Bean

**后期优化：** 引入 Redis 后将黑名单存储从内存迁移到 Redis（REQ-81）。

### 2. 管理端鉴权（admin 模块）

**SecurityConfig 改造：**
- 加入 `JwtAuthenticationFilter`
- 公开端点：`/api/admin/login`、`/api/admin/refresh-token`
- 其余 `/api/admin/**` 需认证且 `hasRole("ADMIN")`（双重保障：登录关卡 + 端点级校验）
- 401/403 响应格式与用户端一致（`{ code, message, data }`）

**管理端登录：**
- `POST /api/admin/login`，请求体 `{ username, password }`
- `AdminLoginAppService` 验证密码后检查 `role == ADMIN`，非 ADMIN 返回业务异常
- 复用 `JwtTokenProvider` 签发 Token
- 返回 `{ accessToken, refreshToken, expiresIn, user: { id, username, nickname, role } }`

**管理端刷新 Token：**
- `POST /api/admin/refresh-token`，请求体 `{ refreshToken }`
- Refresh Token 含 `role` claim，管理端刷新时校验 `role == ADMIN`，非 ADMIN 返回业务异常

### 3. 登出接口

- `POST /api/users/logout`（用户端，加在现有 UserController 中）
- `POST /api/admin/logout`（管理端）
- 客户端登出时同时传 `refreshToken`，后端分别提取 Access Token 和 Refresh Token 的 `jti` 一并加入黑名单
- 返回 `Result<Void>`

### 4. SecurityUtils 工具类（component-auth）

**目的：** 封装从 SecurityContextHolder 获取当前用户信息的操作，供 Controller / AppService 使用。

- 新增 `SecurityUtils` 工具类（静态方法）
  - `getCurrentUserId()` → 从 `SecurityContextHolder` principal 取 userId，未认证抛 `BusinessException`
  - `getCurrentUsername()` → 从 authentication details 取 username
  - `getCurrentUserRole()` → 从 authorities 取角色
- 现有 `JwtAuthenticationFilter` 已将 userId 设为 principal、username 设为 details、authorities 设为 `ROLE_xxx`，无需修改

### 5. 权限策略

| 端 | SecurityConfig 规则 |
|---|------|
| admin (8081) | `/api/admin/login`、`/api/admin/refresh-token` 公开，其余 `/api/admin/**` 需认证 + `hasRole("ADMIN")` |
| app (8080) | `/api/users/register`、`/api/users/login`、`/api/users/refresh-token` 公开，其余需认证 |

- 管理端双重保障：登录接口限定 ADMIN + 端点级 `hasRole("ADMIN")`
- 当前不加 `@EnableMethodSecurity` 和 `@PreAuthorize`，后期角色细分时再加

## 不在范围内

- `@EnableMethodSecurity` / `@PreAuthorize` 方法级权限（后期角色细分时再加）
- CORS 配置（当前阶段前端同源部署）
- 限流、安全头等高级安全特性
- Token 黑名单 Redis 存储（REQ-81）

## 影响文件

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `component-auth/.../JwtTokenProvider.java` | Access/Refresh Token 增加 jti claim，Refresh Token 增加 role claim |
| 修改 | `component-auth/.../JwtAuthenticationFilter.java` | 增加黑名单检查（jti null 放行） |
| 修改 | `component-auth/.../AuthAutoConfiguration.java` | 注册 TokenBlacklist Bean |
| 新增 | `component-auth/.../TokenBlacklist.java` | 黑名单接口 |
| 新增 | `component-auth/.../InMemoryTokenBlacklist.java` | 内存实现（惰性清理） |
| 新增 | `component-auth/.../SecurityUtils.java` | 当前用户信息工具类 + extractBearerToken |
| 修改 | `component-auth/.../pom.xml` | 新增 knowledge-game-core 依赖（BusinessException） |
| 修改 | `admin/.../SecurityConfig.java` | 加入 JWT Filter + hasRole(ADMIN) + 端点规则 |
| 新增 | `admin/.../controller/AdminAuthController.java` | 管理端登录/刷新/登出 |
| 新增 | `admin/.../application/service/AdminLoginAppService.java` | 管理端登录逻辑 |
| 新增 | `admin/.../dto/request/AdminLoginRequest.java` | 登录请求 DTO |
| 新增 | `admin/.../dto/request/AdminRefreshTokenRequest.java` | 刷新 Token 请求 DTO |
| 新增 | `admin/.../dto/response/AdminLoginResponse.java` | 登录响应 DTO（含 user 字段） |
| 新增 | `admin/.../dto/response/AdminRefreshTokenResponse.java` | 刷新 Token 响应 DTO |
| 修改 | `app/.../controller/UserController.java` | 新增登出端点 |
| 修改 | `requirements.md` | 新增 REQ-81 |

## 验收标准

1. 管理端登录：ADMIN 用户登录成功返回 Token + 用户信息；非 ADMIN 用户登录返回业务异常
2. 管理端鉴权：无 Token 访问 `/api/admin/**` 返回 401；USER 角色 Token 访问返回 403
3. 管理端刷新：ADMIN 角色刷新成功；USER 角色的 Refresh Token 调用管理端刷新返回业务异常
4. 登出：登出后原 Access Token 访问受保护接口返回 401
5. 黑名单：已登出的 Token jti 在黑名单中，未登出的正常通过
6. 旧 Token 兼容：无 jti 的旧 Token 正常通过认证（不强制重新登录）
7. SecurityUtils：`getCurrentUserId()` 在认证上下文中返回正确 userId，未认证时抛异常
8. 现有用户端认证不受影响：注册/登录/刷新/受保护接口均正常
9. 单元测试覆盖：TokenBlacklist、SecurityUtils、AdminLoginAppService（ADMIN 成功/非 ADMIN 拒绝）、登出逻辑
