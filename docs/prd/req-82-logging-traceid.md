# REQ-82：后端统一日志 + TraceId 链路追踪

> 状态：`designed`
> 创建：2026-06-10

## 1. 概述

为后端所有模块建立统一的日志规范和全链路 TraceId 追踪能力，覆盖请求入口、异常处理、关键业务节点，同时联动前端 API 调用，预留微服务拆分后的跨服务传播能力。

## 2. 用户故事

- **作为开发者**，我希望所有请求自动携带 traceId，以便在日志中快速关联同一请求的完整调用链
- **作为开发者**，我希望请求入口自动记录 HTTP 元信息和业务参数，以便排查接口问题
- **作为开发者**，我希望业务异常和系统异常有明确分级日志，以便区分业务错误和系统故障
- **作为开发者**，我希望敏感信息在日志中自动脱敏，以便遵守数据安全规范
- **作为开发者**，我希望前端请求自动传播 traceId，以便联调时前后端日志关联

## 3. 功能需求

### 3.1 TraceId 链路追踪

- 入站请求携带 `X-Trace-Id` 请求头时沿用，否则生成 UUID 作为 traceId
- 每次请求生成唯一 `requestId`（与 traceId 区分，同一 traceId 下可有多个 requestId）
- 通过 Servlet Filter 将 traceId、requestId 注入 SLF4J MDC
- 响应头返回 `X-Trace-Id`，供前端和下游服务读取
- Filter 在 `finally` 中清理 MDC，防止线程池复用导致的上下文污染

### 3.2 MDC 上下文字段

| 字段 | 来源 | 注入位置 | 说明 |
|------|------|---------|------|
| `traceId` | 请求头或 UUID 生成 | TraceIdFilter | 跨请求/跨服务传播 |
| `requestId` | 每次请求 UUID 生成 | TraceIdFilter | 区分同一 trace 下的多次请求 |
| `clientIp` | `X-Forwarded-For` 或 `request.getRemoteAddr()` | TraceIdFilter | 客户端 IP |
| `requestPath` | `request.getRequestURI()` | TraceIdFilter | 当前请求路径 |
| `userId` | JWT 解析后从 SecurityContext 获取 | JwtAuthenticationFilter（component-auth） | 当前登录用户 ID，未登录为 `-` |

> **注意：** userId 不在 TraceIdFilter 中注入（此时 JWT 尚未解析），而是在 component-auth 的 JwtAuthenticationFilter 认证成功后通过 `MDC.put("userId", userId)` 补充设置，并在 Filter 清理时一并 `MDC.remove("userId")`。

### 3.3 日志输出配置

- **控制台**：JSON 格式，包含所有 MDC 字段，方便 ELK/Loki 采集
- **文件**：人类可读格式，按天滚动，保留 30 天，单文件上限 200MB
- **文件路径**：`logs/{应用名}.log`（app → `knowledge-game-app.log`，admin → `knowledge-game-admin.log`）

### 3.4 请求入口日志（两层）

**Filter 层（自动，零侵入）：**
- 在 TraceId Filter 中记录 HTTP 方法、请求路径、查询参数、客户端 IP
- 请求完成时记录响应状态码和耗时

**AOP 层（可选，按需注解）：**
- 提供 `@LogParam` 注解，标注在 Controller 方法上
- AOP 切面自动记录方法名和参数值（经过脱敏）
- 支持在注解中指定需要脱敏的参数名
- 参数序列化规则：
  - 简单类型（String、Number、Boolean）直接输出
  - 复杂对象使用 `Jackson.toJsonString()` 序列化，catch 异常时 fallback 到 `toString()`
  - 序列化结果超过 1000 字符截断，尾部追加 `...(truncated)`
  - `MultipartFile` / `InputStream` 类型仅记录文件名或 `<binary>`

### 3.5 异常日志

| 场景 | 级别 | 内容 |
|------|------|------|
| BusinessException（业务异常） | WARN | 异常码、异常信息、业务上下文参数 |
| MethodArgumentNotValidException（参数校验） | WARN | 字段名、校验错误信息 |
| Exception（系统级异常） | ERROR | 完整堆栈 + 请求上下文（MDC 自动附带） |

### 3.6 敏感信息脱敏

- 通过 Logback 自定义 `MessageConverter` 在日志输出层自动脱敏
- 脱敏规则通过配置文件（`application.yml`）配置字段列表，无需修改业务代码
- 默认脱敏字段：`password`、`token`、`accessToken`、`refreshToken`
- 脱敏策略：`password`/`secret` 类字段全遮掩（`***`），其他字段保留前 3 后 2 位（`abc***yz`）
- 支持后续通过配置追加字段，无需改代码

### 3.7 关键业务节点日志

- 在 AppService 层关键操作处打印 INFO 日志（如积分变动、卡牌抽取、状态流转）
- 日志内容包含操作描述和关键业务标识（如 userId、groupId、cardId）
- 领域层（domain/service/）不添加 Logger，不打印日志。领域层通过抛出 BusinessException 传递错误信息，由上层 GlobalExceptionHandler 统一记录

### 3.8 前端 TraceId 联动

- 响应拦截器从响应头读取 `X-Trace-Id`，仅在 sessionStorage 中无值时缓存（避免并发请求覆盖）
- 请求拦截器自动在请求头中带上 `X-Trace-Id`
- 网络错误或服务端错误时，在控制台打印 traceId，方便用户反馈问题时附带

## 4. 技术方案

### 4.1 新建 component-log 模块

```
backend/knowledge-game-components/component-log/
├── pom.xml
└── src/main/
    ├── java/com/knowledgegame/components/log/
    │   ├── config/
    │   │   └── LogAutoConfiguration.java          ← @AutoConfiguration，注册 Filter、AOP、脱敏 Converter
    │   ├── filter/
    │   │   └── TraceIdFilter.java                  ← Servlet Filter，MDC 注入/清理 + 入口日志
    │   ├── aspect/
    │   │   └── LogParamAspect.java                 ← AOP 切面，处理 @LogParam 注解
    │   ├── annotation/
    │   │   └── LogParam.java                       ← 标注需要记录参数的 Controller 方法
    │   ├── masking/
    │   │   └── SensitiveDataConverter.java         ← Logback MessageConverter，配置化脱敏
    │   └── properties/
    │       └── LogProperties.java                  ← @ConfigurationProperties，脱敏字段列表等配置
    └── resources/
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 4.1b 新建 component-exception 模块

```
backend/knowledge-game-components/component-exception/
├── pom.xml
└── src/main/
    ├── java/com/knowledgegame/components/exception/
    │   ├── config/
    │   │   └── ExceptionAutoConfiguration.java     ← @AutoConfiguration，扫描异常处理器
    │   └── handler/
    │       └── GlobalExceptionHandler.java          ← 合并后的共享异常处理器（含 WARN/ERROR 日志）
    └── resources/
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 4.2 GlobalExceptionHandler 合并

- 将 app/admin 两端相同的 `GlobalExceptionHandler` 合并到 `component-exception` 模块的 `handler/GlobalExceptionHandler.java` 中
- 加入 WARN/ERROR 分级日志
- app/admin 模块删除各自的 `GlobalExceptionHandler`，通过引入 component-exception 自动获得统一的异常处理
- 不放在 core 中，避免 core 引入 `spring-boot-starter-web` 依赖，保持领域层纯净
- 异常处理和日志基础设施分离为两个独立组件，职责单一

### 4.3 Logback 配置

- 在 component-log 中提供默认 `logback-spring.xml` 模板（通过 `spring.logging.config` 或各端模块 resources 下放置）
- 各端模块（app/admin）在 `src/main/resources/` 下放置各自的 `logback-spring.xml`，include 共享配置

### 4.4 Filter 排序

- `TraceIdFilter` 优先级需高于 `JwtAuthenticationFilter`，确保所有请求（包括未认证的）都有 traceId
- 通过 `FilterRegistrationBean` 设置 `order = Ordered.HIGHEST_PRECEDENCE`

### 4.5 依赖关系

```
component-log → core（依赖 BusinessException、ResultCode）
component-log → spring-boot-starter-web（Servlet Filter）
component-log → spring-boot-starter-aop（AOP 切面）
component-exception → core（依赖 BusinessException、ResultCode）
component-exception → spring-boot-starter-web（@RestControllerAdvice）
component-auth → core（已有，补充 MDC userId 设置）
app → component-log, component-exception, component-auth
admin → component-log, component-exception, component-auth
```

## 5. 日志打印原则

| 场景 | 级别 | 要求 |
|------|------|------|
| 请求入口（Filter） | INFO | HTTP 方法、路径、查询参数、客户端 IP |
| 请求出口（Filter） | INFO | 响应状态码、耗时 |
| Controller 业务参数（@LogParam） | INFO | 方法名、参数值（已脱敏） |
| BusinessException | WARN | 异常码、异常信息 |
| 参数校验异常 | WARN | 字段名、错误信息 |
| 系统级异常 | ERROR | 完整堆栈 |
| 关键业务节点 | INFO | 操作描述、关键业务标识 |

## 6. Impact Analysis

### 6.1 新增文件

| 文件 | 说明 |
|------|------|
| `backend/knowledge-game-components/component-log/pom.xml` | 新模块 POM |
| `backend/knowledge-game-components/component-exception/pom.xml` | 新模块 POM |
| `backend/knowledge-game-components/pom.xml` | 添加 component-log、component-exception 子模块 |
| `component-log/src/.../config/LogAutoConfiguration.java` | 日志自动配置类 |
| `component-log/src/.../filter/TraceIdFilter.java` | TraceId Filter |
| `component-log/src/.../aspect/LogParamAspect.java` | AOP 切面 |
| `component-log/src/.../annotation/LogParam.java` | 参数日志注解 |
| `component-log/src/.../masking/SensitiveDataConverter.java` | 脱敏 Converter |
| `component-log/src/.../properties/LogProperties.java` | 配置属性类 |
| `component-log/src/.../AutoConfiguration.imports` | 日志自动配置注册 |
| `component-exception/src/.../config/ExceptionAutoConfiguration.java` | 异常自动配置类 |
| `component-exception/src/.../handler/GlobalExceptionHandler.java` | 合并后的共享异常处理器（含日志） |
| `component-exception/src/.../AutoConfiguration.imports` | 异常自动配置注册 |

### 6.2 修改文件

| 文件 | 变更内容 |
|------|---------|
| `backend/knowledge-game-components/pom.xml` | 添加 component-log、component-exception 子模块 |
| `backend/knowledge-game-app/pom.xml` | 添加 component-log、component-exception 依赖 |
| `backend/knowledge-game-admin/pom.xml` | 添加 component-log、component-exception 依赖 |
| `backend/knowledge-game-app/.../common/exception/GlobalExceptionHandler.java` | 删除，改用 component-exception 中的共享版本 |
| `backend/knowledge-game-admin/.../common/exception/GlobalExceptionHandler.java` | 删除，改用 component-exception 中的共享版本 |
| `backend/knowledge-game-components/component-auth/.../JwtAuthenticationFilter.java` | 认证成功后添加 `MDC.put("userId", userId)`，清理时 `MDC.remove("userId")` |
| `frontend/admin/src/services/request.ts` | 添加 TraceId 请求/响应拦截器 |

### 6.3 配置变更

| 配置 | 说明 |
|------|------|
| `application.yml`（app/admin） | 添加 `knowledgegame.log.masking.fields` 脱敏字段列表 |
| `logback-spring.xml`（app/admin） | 新增 Logback 配置文件，引用 component-log 的 JSON 格式和脱敏 Converter |

### 6.4 受影响的现有功能

| 功能 | 影响 |
|------|------|
| 所有 API 请求 | TraceId Filter 自动生效，日志中多出 MDC 字段，不影响业务逻辑 |
| 异常处理 | GlobalExceptionHandler 行为不变，增加日志输出 |
| 前端 API 调用 | 请求头自动带 X-Trace-Id，响应头多一个 header，不影响业务 |

## 7. Verification Plan

### 7.1 手动验证

1. 启动 app 模块，发送不带 `X-Trace-Id` 的请求，确认响应头返回 `X-Trace-Id`
2. 发送带 `X-Trace-Id: test-trace-123` 的请求，确认响应头返回 `test-trace-123`
3. 检查控制台输出为 JSON 格式，包含 traceId、requestId、requestPath 等字段
4. 检查 `logs/` 目录生成日志文件
5. 触发业务异常，确认控制台输出 WARN 级别日志
6. 触发系统异常（如传错参数导致 500），确认控制台输出 ERROR 级别日志及完整堆栈
7. 日志中出现 password/token 等字段时确认已被脱敏
8. 前端发送请求后，确认 sessionStorage 中缓存了 traceId

### 7.2 自动化测试

- `TraceIdFilterTest`：验证 MDC 注入/清理、traceId 沿用/生成、响应头设置
- `SensitiveDataConverterTest`：验证各脱敏规则（全遮掩、部分遮掩、配置字段列表）
- `LogParamAspectTest`：验证 AOP 参数记录和脱敏
- `GlobalExceptionHandlerTest`：验证 WARN/ERROR 日志输出（使用 Logback ListAppender 断言日志内容，位于 component-exception 模块）

### 7.3 回滚标准

- 移除 app/admin 的 component-log、component-exception 依赖
- 恢复 app/admin 各自的 GlobalExceptionHandler
- 恢复 component-auth 中 JwtAuthenticationFilter 的 MDC 相关改动
- 删除前端 TraceId 拦截器代码
- 删除 `logback-spring.xml` 配置文件
