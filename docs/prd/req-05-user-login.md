# REQ-05 用户登录 API（JWT 签发 + Spring Security）

## 产品定位

提供用户登录接口，基于 JWT 实现无状态认证。采用 Access Token + Refresh Token 双令牌策略，集成 Spring Security 框架。

## 用户故事

**作为** 已注册用户
**我想要** 通过用户名 + 密码登录
**以便于** 获取 Token 访问受保护的 API

## 前置依赖

- REQ-04（用户注册）已完成
- component-auth 模块已存在（BCrypt）
- Spring Security 当前未引入

## 技术决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 认证框架 | Spring Security（SecurityFilterChain） | 用户选择，为后续角色权限奠定基础 |
| Token 策略 | Access Token（30min）+ Refresh Token（7d） | 双令牌，支持无感刷新 |
| Token 存储 | 纯无状态 JWT，不落库 | 简单直接；后续可加 blocklist 实现吊销 |
| JWT 工具位置 | component-auth 模块 | 与 BCrypt 同模块，app/admin 共用 |
| SecurityConfig 位置 | 各应用模块（app/admin）各自配置 | 不同模块公开路径和权限不同 |
| JWT 库 | jjwt-api + jjwt-impl + jjwt-jackson | 业界标准 |

## 功能需求

### API

| 方法 | 路径 | 说明 | 是否公开 |
|------|------|------|---------|
| POST | `/api/users/login` | 用户登录 | 是 |
| POST | `/api/users/refresh-token` | 刷新令牌 | 是 |

#### 登录

**请求体：**
```json
{
  "username": "player1",
  "password": "123456"
}
```

**成功响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 1800,
    "user": {
      "id": 1,
      "username": "player1",
      "nickname": "玩家一",
      "role": "USER"
    }
  }
}
```

**失败场景：**
- 用户名不存在 → 401 `用户名或密码错误`
- 密码不匹配 → 401 `用户名或密码错误`
- 不区分"用户名不存在"和"密码错误"，统一提示防止枚举攻击

#### 刷新令牌

**请求体：**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**成功响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 1800
  }
}
```

**失败场景：**
- Token 无效/过期 → 401 `Refresh Token 无效或已过期`

### JWT Payload 结构

**Access Token：**
```json
{ "userId": 1, "username": "player1", "role": "USER", "type": "access", "iat": ..., "exp": ... }
```

**Refresh Token：**
```json
{ "userId": 1, "type": "refresh", "iat": ..., "exp": ... }
```

### Spring Security 配置

**app 模块公开路径：**
- `POST /api/users/register` — 注册
- `POST /api/users/login` — 登录
- `POST /api/users/refresh-token` — 刷新令牌
- 其余所有 `/api/**` 需认证

**admin 模块（临时）：**
- 所有路径 permit-all（待 REQ-06 完善）

### JWT 配置参数

```yaml
jwt:
  secret: "your-256-bit-secret-key-for-knowledge-game-must-be-long-enough"
  access-token-expiration: 1800000    # 30 分钟（毫秒）
  refresh-token-expiration: 604800000 # 7 天（毫秒）
```

## Issue 拆解

### Issue 0：component-auth 扩展 — JWT + Spring Security 基础设施

- [ ] pom.xml 添加依赖：`spring-boot-starter-security`、`jjwt-api`、`jjwt-impl`、`jjwt-jackson`
- [ ] 创建 `JwtProperties`（@ConfigurationProperties，绑定 `jwt.*` 配置）
- [ ] 创建 `JwtTokenProvider`（生成/解析/验证 Access Token 和 Refresh Token）
- [ ] 创建 `JwtAuthenticationFilter`（extends OncePerRequestFilter，提取 Authorization Header → 验证 → 设置 SecurityContext）
- [ ] 重构 `PasswordEncoder.java` → `AuthAutoConfiguration`（注册 PasswordEncoder + JwtTokenProvider + enable JwtProperties）
- [ ] 更新 `AutoConfiguration.imports`

### Issue 1：app 模块 — 登录 + Token 刷新

- [ ] 创建 `SecurityConfig`（SecurityFilterChain：公开路径 permitAll + 注册 JwtAuthenticationFilter + 无状态 Session）
- [ ] 创建 `LoginRequest` DTO（username + password）
- [ ] 创建 `LoginResponse` DTO（accessToken + refreshToken + expiresIn + UserResponse）
- [ ] 创建 `RefreshTokenRequest` DTO（refreshToken）
- [ ] 创建 `RefreshTokenResponse` DTO（accessToken + refreshToken + expiresIn）
- [ ] 创建 `LoginCommand`（username + rawPassword）
- [ ] UserAppService 添加 `login()` 和 `refreshToken()` 方法
- [ ] UserController 添加 `login()` 和 `refreshToken()` 端点
- [ ] application.yml 添加 jwt 配置

### Issue 2：admin 模块 — 临时 SecurityConfig

- [ ] 创建 `SecurityConfig`（所有路径 permitAll，csrf disable，无状态 Session）
- [ ] application.yml 添加 jwt 配置

### Issue 3：验证

- [ ] `mvn clean install` 通过
- [ ] 启动 app，测试注册 → 登录 → 用 Token 访问受保护接口
- [ ] 测试 Refresh Token 刷新
- [ ] 测试 Token 过期/无效返回 401
- [ ] 启动 admin，确认现有 CRUD 接口不受影响
- [ ] DDD 规范审计通过

## 文件清单

### component-auth（新增/修改）

```
component-auth/
├── pom.xml                                                    （添加依赖）
└── src/main/java/com/knowledgegame/auth/
    └── security/
        ├── AuthAutoConfiguration.java                         （重构自 PasswordEncoder.java）
        ├── JwtProperties.java                                 （新增）
        ├── JwtTokenProvider.java                              （新增）
        └── JwtAuthenticationFilter.java                       （新增）
```

### app 模块（新增/修改）

```
app/src/main/java/com/knowledgegame/app/
├── api/
│   ├── controller/UserController.java                         （添加 login、refreshToken 端点）
│   ├── dto/request/LoginRequest.java                          （新增）
│   ├── dto/request/RefreshTokenRequest.java                   （新增）
│   ├── dto/response/LoginResponse.java                        （新增）
│   └── dto/response/RefreshTokenResponse.java                 （新增）
├── application/
│   ├── command/LoginCommand.java                              （新增）
│   └── service/UserAppService.java                            （添加 login、refreshToken 方法）
├── config/
│   └── SecurityConfig.java                                    （新增）
└── resources/
    └── application.yml                                        （添加 jwt 配置）
```

### admin 模块（新增）

```
admin/src/main/java/com/knowledgegame/admin/
├── config/
│   └── SecurityConfig.java                                    （新增，临时 permitAll）
└── resources/
    └── application.yml                                        （添加 jwt 配置）
```

## 验收标准

- [ ] 登录接口：用户名 + 密码正确返回 Access Token + Refresh Token + 用户信息
- [ ] 登录接口：用户名或密码错误返回 401，不泄露具体原因
- [ ] Refresh Token 接口：有效 Refresh Token 返回新的双令牌
- [ ] Refresh Token 接口：无效/过期 Token 返回 401
- [ ] 受保护接口：无 Token 或无效 Token 返回 401
- [ ] 受保护接口：有效 Token 正常访问
- [ ] admin 模块现有 CRUD 不受影响
- [ ] DDD 规范：Controller 零领域模型依赖
