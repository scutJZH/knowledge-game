# REQ-85：机机鉴权组件

> 状态：`designed`
> 创建：2026-06-10

## 1. 概述

为后端微服务间 Feign/OpenFeign 调用提供统一的身份验证机制。通过 API Key 固定密钥方式，确保服务间内部接口仅允许已授权的调用方访问。独立为 `component-m2m-auth` 模块，与现有用户 JWT 鉴权（component-auth）解耦。

## 2. 用户故事

- **作为开发者**，我希望调用方服务通过 Feign 拦截器自动注入 API Key，以便无需在每次 Feign 调用中手动传递认证信息
- **作为开发者**，我希望被调用方服务通过 Filter 自动校验 API Key，以便内部接口得到保护且对业务代码零侵入
- **作为开发者**，我希望 M2M 鉴权与用户 JWT 鉴权互不干扰，以便两套认证机制可以独立配置和演进
- **作为开发者**，我希望通过配置文件管理服务密钥，以便新增服务时无需改代码

## 3. 功能需求

### 3.1 服务端校验（M2mAuthFilter）

- 拦截所有请求，进行绑定校验：从 `X-Service-Name` 头获取服务名 → `keys.get(serviceName)` → 与 `X-Service-Key` 比对
- `X-Service-Name` 缺失、服务名不在 keys 中、或 Key 不匹配，均返回 401
- 配置 `m2m.auth.protected-paths` 指定需要 M2M 鉴权的路径前缀列表（如 `/internal/**`）
- 非保护路径不受影响，直接放行
- Filter 排序需在 Spring Security Filter 之前（`Ordered.HIGHEST_PRECEDENCE + 100`），确保 SecurityConfig 中的路径排除规则仍可生效
- 通过 `FilterRegistrationBean` 注册，默认不启用，需配置 `m2m.auth.enabled=true` 开启

### 3.2 客户端注入（M2mFeignInterceptor）

- 实现 `RequestInterceptor`（OpenFeign 接口）
- 自动在 Feign 请求中注入 `X-Service-Key` 请求头和 `X-Service-Name` 请求头
- 密钥通过 `m2m.auth.api-key` 配置
- 服务名通过 `m2m.auth.service-name` 配置
- 通过 `@AutoConfiguration` 注册，有 Feign 依赖时自动生效

### 3.3 配置项

**服务端（被调用方，如 file 服务）配置：**

```yaml
m2m:
  auth:
    enabled: true
    keys:
      app: "app-secret-key-xxx"
      admin: "admin-secret-key-xxx"
    protected-paths:
      - "/internal/**"
```

**客户端（调用方，如 app/admin）配置：**

```yaml
m2m:
  auth:
    enabled: true
    service-name: app
    api-key: "app-secret-key-xxx"
```

### 3.4 与用户 JWT 鉴权的关系

- M2M 鉴权和用户 JWT 鉴权是两套独立机制，互不干扰
- M2M Filter 在 Spring Security Filter Chain 之前执行，不参与 Spring Security 的认证链
- **启用前提**：使用 M2M 的服务必须在 SecurityConfig 中将 `protectedPaths` 路径排除出 JWT 鉴权（加入 `permitAll` 白名单），否则 M2M 校验通过后请求仍会被 JWT Filter 拦截返回 401

### 3.5 日志

- M2M 鉴权失败时记录 WARN 日志（包含来源 IP、请求路径、原因）
- M2M 鉴权成功时记录 DEBUG 日志（包含服务名）
- 日志自动附带 MDC 中的 traceId（依赖 component-log 的 TraceIdFilter）

## 4. 技术方案

### 4.1 模块结构

```
backend/knowledge-game-components/component-m2m-auth/
├── pom.xml
└── src/main/
    ├── java/com/knowledgegame/components/m2mauth/
    │   ├── config/
    │   │   ├── M2mAuthProperties.java              ← @ConfigurationProperties
    │   │   └── M2mAuthAutoConfiguration.java       ← @AutoConfiguration，注册 Filter 和 Interceptor
    │   ├── filter/
    │   │   └── M2mAuthFilter.java                  ← OncePerRequestFilter，服务端校验
    │   └── interceptor/
    │       └── M2mFeignInterceptor.java            ← RequestInterceptor，客户端注入
    └── resources/
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 4.2 核心类设计

**M2mAuthProperties：**
- `enabled`：是否启用 M2M 鉴权（默认 false）
- `keys`：`Map<String, String>`，服务名 → API Key 映射（服务端用）
- `protectedPaths`：`List<String>`，需要保护的路径模式（默认空）
- `serviceName`：当前服务名（客户端用）
- `apiKey`：当前服务的 API Key（客户端用）

**M2mAuthFilter：**
- 继承 `OncePerRequestFilter`
- 通过 `AntPathMatcher` 匹配 protectedPaths
- 匹配的路径执行绑定校验：读取 `X-Service-Name` 和 `X-Service-Key` 头，`keys.get(serviceName)` 与提交的 Key 比对
- `X-Service-Name` 缺失、服务名未知、Key 不匹配均视为校验失败
- 校验通过时将 serviceName 存入 request attribute，供后续使用
- 校验失败返回 401 JSON 响应（复用 `Result` 和 `ResultCode`）

**M2mFeignInterceptor：**
- 实现 `feign.RequestInterceptor`
- 在 `apply` 方法中添加 `X-Service-Key` 和 `X-Service-Name` 请求头
- 使用 `@ConditionalOnClass(feign.RequestInterceptor.class)` 条件装配

### 4.3 依赖关系

```
component-m2m-auth → core（依赖 Result、ResultCode、BusinessException）
component-m2m-auth → spring-boot-starter-web（Servlet Filter）
component-m2m-auth → spring-boot-autoconfigure（@AutoConfiguration）
component-m2m-auth → openfeign（可选，provided scope，仅 Interceptor 需要）
```

### 4.4 Maven 依赖

**component-m2m-auth/pom.xml 关键依赖：**

```xml
<dependency>
    <groupId>com.knowledgegame</groupId>
    <artifactId>knowledge-game-core</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>
<!-- OpenFeign：可选依赖，仅客户端拦截器需要 -->
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-core</artifactId>
    <optional>true</optional>
</dependency>
```

## 5. Impact Analysis

### 5.1 新增文件

| 文件 | 说明 |
|------|------|
| `backend/knowledge-game-components/component-m2m-auth/pom.xml` | 新模块 POM |
| `component-m2m-auth/src/.../config/M2mAuthProperties.java` | 配置属性类 |
| `component-m2m-auth/src/.../config/M2mAuthAutoConfiguration.java` | 自动配置类 |
| `component-m2m-auth/src/.../filter/M2mAuthFilter.java` | 服务端鉴权 Filter |
| `component-m2m-auth/src/.../interceptor/M2mFeignInterceptor.java` | Feign 客户端拦截器 |
| `component-m2m-auth/src/.../AutoConfiguration.imports` | 自动配置注册 |
| `docs/prd/req-85-m2m-auth.md` | 本 PRD |

### 5.2 修改文件

| 文件 | 变更内容 |
|------|---------|
| `backend/knowledge-game-components/pom.xml` | 添加 component-m2m-auth 子模块 |
| 各服务的 `SecurityConfig.java` | 启用 M2M 时需将 `protectedPaths` 路径加入 JWT 白名单（`permitAll`） |

### 5.3 配置变更

| 配置 | 说明 |
|------|------|
| `application.yml`（file 服务，待 REQ-83 实现） | 添加 `m2m.auth.keys` 和 `m2m.auth.protected-paths` |
| `application.yml`（app/admin） | 添加 `m2m.auth.service-name` 和 `m2m.auth.api-key`（待 Feign 调用时配置） |

### 5.4 受影响的现有功能

| 功能 | 影响 |
|------|------|
| 现有 API 请求 | 无影响，M2M Filter 默认不启用（`enabled=false`），启用后仅拦截 protectedPaths |
| 用户 JWT 鉴权 | 无影响，两套机制独立运行 |
| component-auth | 无变更，M2M 模块不修改任何现有组件 |

## 6. Verification Plan

### 6.1 手动验证

1. 启动任意一个后端模块（如 app），不配置 `m2m.auth.enabled`，确认所有请求正常（M2M 未启用）
2. 配置 `m2m.auth.enabled=true` 和 protectedPaths，发送不带请求头的请求到保护路径，确认返回 401
3. 发送带正确 `X-Service-Name` + `X-Service-Key` 绑定对的请求，确认正常放行
4. 发送带错误 Key 的请求，确认返回 401
5. 发送带合法 Key 但伪造 `X-Service-Name`（冒充其他服务）的请求，确认返回 401（绑定校验失败）
6. 发送缺少 `X-Service-Name` 头的请求，确认返回 401
7. 发送到非保护路径的请求（不带 Key），确认正常放行
8. 配置 `m2m.auth.service-name` 和 `m2m.auth.api-key`，确认 Feign 调用自动携带两个请求头

### 6.2 自动化测试

- `M2mAuthFilterTest`：
  - 验证未启用时所有请求放行
  - 验证保护路径校验通过（绑定 Key 匹配）
  - 验证保护路径校验失败（无 Key / 错误 Key / 缺少 Service-Name）
  - 验证服务冒充防护（合法 Key + 伪造 Service-Name 应返回 401）
  - 验证非保护路径不受影响
  - 验证多个服务的绑定 Key 均可校验通过
- `M2mFeignInterceptorTest`：
  - 验证请求头中注入 `X-Service-Key` 和 `X-Service-Name`
  - 验证未配置时不注入
- `M2mAuthPropertiesTest`：
  - 验证配置绑定

### 6.3 回滚标准

- 从 `knowledge-game-components/pom.xml` 移除 component-m2m-auth 子模块
- 删除 `component-m2m-auth` 目录
- 移除相关配置（如有）
- 无需修改任何现有代码，模块完全独立
