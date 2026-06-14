# REQ-91 V2：机机鉴权组件 — 多目标服务密钥映射

> 状态：`designed`
> 创建：2026-06-14
> 关联：REQ-85（初始机机鉴权组件）、REQ-91 V1（简化校验模型：从 `keys.get(serviceName)` 改为被调用方单一 key）

## 1. 概述

当前 `component-m2m-auth` 的 Feign 拦截器使用单一 `m2m.auth.api-key` 注入到所有 Feign 调用。当调用方（如 `admin`）需要同时调用多个被调用方（如 `file`、`app`），且各被调用方使用不同 API Key 时，单一 `api-key` 无法满足需求 —— 必有一个被调用方鉴权失败。

本需求迭代 REQ-91 V1 的简化模型：**被调用方零改动**（仍持有单一 `api-key`），仅扩展**调用方**能力，引入 `m2m.auth.services` 映射表，按 Feign 目标服务名注入对应 Key。命中失败抛错，强制运维显式补齐配置。

## 2. 用户故事

- **作为运维**，我希望一个调用方服务能使用不同的 API Key 调用不同被调用方，以便每个被调用方持有独立密钥、密钥泄露互不影响
- **作为开发者**，我希望 Feign 拦截器自动按目标服务选 Key，以便业务代码无感知
- **作为运维**，当漏配某个服务的 Key 时我希望启动后第一次调用就失败，以便尽早暴露问题，而不是默默回落到错误的兜底 Key
- **作为开发者**，我希望被调用方代码零改动，以便复用 REQ-91 V1 简化后的校验模型

## 3. 功能需求

### 3.1 调用方配置（Feign 拦截器）

- 通过 `m2m.auth.services` 配置 `Map<服务名, apiKey>`，服务名必须等于 `@FeignClient(name = "...")` 的值，与目标服务 `spring.application.name` 一致
- Feign 拦截器在每次调用时从 `RequestTemplate.feignTarget()` 取目标服务名 → 查 `services` Map → 命中则注入对应 Key 到 `X-Service-Key` 头
- **未命中行为**：抛出 `IllegalStateException`，中断当前 Feign 调用；异常信息含目标服务名，便于排查
- 服务自身标识仍用 `m2m.auth.service-name`，注入到 `X-Service-Name` 头（日志用，被调用方不依赖）

### 3.2 被调用方配置（Filter）

- **零改动**：仍使用 `m2m.auth.api-key`（单值）+ `m2m.auth.protected-paths`
- 校验逻辑不变：`X-Service-Key == apiKey` 即通过
- 这是 REQ-91 V1 的核心简化红利，本需求不动

### 3.3 字段职责划分

| 字段 | 持有方 | 用途 |
|------|--------|------|
| `m2m.auth.enabled` | 调用方 + 被调用方 | 总开关（默认 false） |
| `m2m.auth.protected-paths` | 被调用方 | 保护路径模式（Ant 风格） |
| `m2m.auth.api-key` | **被调用方** | Filter 校验自身 Key |
| `m2m.auth.service-name` | 调用方 | Feign 拦截器注入的自身标识（日志用） |
| `m2m.auth.services` | **调用方** | `Map<目标服务名, apiKey>`，调用方多目标 Key 映射 |

### 3.4 兼容性策略

- **废除**调用方对 `m2m.auth.api-key` 的依赖（被调用方仍需保留）
- 调用方若仍按旧方式只配 `api-key`、未配 `services`，第一次 Feign 调用会因 `services` 未命中抛错，强制迁移
- 不做静默回落（用户决策点 3），避免调用方误用兜底 Key 触发被调用方 401 难以排查

### 3.5 日志

- 拦截器注入成功：DEBUG（含目标服务名）
- 拦截器发现目标服务未在 `services` Map 中：抛 `IllegalStateException`，无需日志降级，由调用栈向上冒泡（业务代码通常捕获为 Feign 异常）
- Filter 校验失败：WARN（沿用 REQ-91 V1 行为）

## 4. 技术方案

### 4.1 M2mAuthProperties 改造

```java
public class M2mAuthProperties {
    private boolean enabled = false;
    private List<String> protectedPaths = new ArrayList<>();   // 被调用方
    private String serviceName;                                // 调用方（日志用）
    private String apiKey;                                     // 被调用方持有（Filter 校验）
    private Map<String, String> services = new HashMap<>();    // 调用方持有（按目标服务映射）
    // getters / setters
}
```

### 4.2 M2mFeignInterceptor 改造

```java
@Override
public void apply(RequestTemplate template) {
    String serviceName = properties.getServiceName();
    if (!StringUtils.hasText(serviceName)) {
        throw new IllegalStateException("m2m.auth.service-name 未配置，无法注入 X-Service-Name");
    }

    String apiKey = resolveApiKey(template);   // 命中失败抛 IllegalStateException
    template.header(HEADER_SERVICE_NAME, serviceName);
    template.header(HEADER_SERVICE_KEY, apiKey);
}

/**
 * 按目标服务名解析 apiKey
 * @throws IllegalStateException 目标服务名无法解析或 services Map 未覆盖该服务
 */
private String resolveApiKey(RequestTemplate template) {
    Target<?> target = template.feignTarget();
    if (target == null || !StringUtils.hasText(target.name())) {
        throw new IllegalStateException("Feign RequestTemplate.feignTarget() 为空，无法解析目标服务名");
    }
    String targetName = target.name();
    String key = properties.getServices().get(targetName);
    if (!StringUtils.hasText(key)) {
        throw new IllegalStateException(
            "目标服务 [" + targetName + "] 未在 m2m.auth.services 中配置 apiKey");
    }
    return key;
}
```

**AutoConfiguration 影响**：`M2mFeignInterceptor` 构造器签名不变（仍接收 `M2mAuthProperties`），`services` 字段已挂在 `M2mAuthProperties` 上，`M2mAuthAutoConfiguration` 的 `@Bean` 方法**无需修改**。

### 4.3 关键 API 依赖

- `feign.RequestTemplate.feignTarget()` —— feign-core 12.1+ 提供，本项目使用 13.6.1，已验证可用
- 返回 `feign.Target<?>`，通过 `target.name()` 拿到 `@FeignClient(name = "...")` 的值

### 4.4 模块依赖关系

零变化，沿用 REQ-91 V1。

### 4.5 配置示例

**调用方（admin 同时调 file + 未来调 app）：**

```yaml
m2m:
  auth:
    enabled: true
    service-name: knowledge-game-admin
    services:
      knowledge-game-file: ${FILE_SERVICE_API_KEY:default-dev-key}
      knowledge-game-app: ${APP_SERVICE_API_KEY:default-dev-app-key}
```

**被调用方（file，零改动）：**

```yaml
m2m:
  auth:
    enabled: true
    protected-paths: /api/file/internal/**
    api-key: ${FILE_SERVICE_API_KEY:default-dev-key}
```

### 4.6 Nacos 配置布局

- `services` Map 属于调用方特有，**不**放入共享 `common.yml`
- 各调用方在自己模块的 `application.yml` 或 Nacos 专属配置（如 `knowledge-game-admin.yml`）维护 `services`
- API Key 实际值通过环境变量注入（`${FILE_SERVICE_API_KEY}`），避免配置文件硬编码

## 5. Impact Analysis

### 5.1 修改文件

| 文件 | 变更内容 |
|------|---------|
| `component-m2m-auth/src/.../config/M2mAuthProperties.java` | 新增 `services` 字段及 getter/setter |
| `component-m2m-auth/src/.../interceptor/M2mFeignInterceptor.java` | `apply` 方法改为按 target 解析 apiKey；未命中抛 `IllegalStateException` |
| `backend/knowledge-game-admin/src/main/resources/application.yml` | 删除 `m2m.auth.api-key`，改为 `m2m.auth.services.knowledge-game-file` |
| `backend/knowledge-game-admin/src/test/resources/application-test.yml` | 同步迁移测试配置 |
| `backend/knowledge-game-app/src/main/resources/application.yml` | 删除 `m2m.auth.api-key`，改为 `m2m.auth.services.knowledge-game-file` |
| `backend/knowledge-game-app/src/test/resources/application-test.yml` | 同步迁移测试配置 |
| `docs/requirements.md` | REQ-91 状态从 `done` 改为 `in-progress`，备注补 V2 说明，PRD 列填本文件 |

### 5.2 测试文件改造

| 文件 | 改造内容 |
|------|---------|
| `component-m2m-auth/src/test/.../interceptor/M2mFeignInterceptorTest.java` | 改用 `services` Map 构造；补未命中抛错用例 |
| `component-m2m-auth/src/test/.../interceptor/IndependentM2mFeignInterceptorTest.java` | 同上（脱离 Spring 上下文版本） |

### 5.3 不修改

- `M2mAuthFilter.java`（被调用方零改动）
- `M2mAuthAutoConfiguration.java`（自动装配逻辑不变）
- `M2mAuthFilterTest.java`（Filter 校验逻辑无变化）
- file / app 模块任何文件

### 5.4 受影响的现有功能

| 功能 | 影响 |
|------|------|
| admin → file Feign 调用 | 行为不变（`services` 配置后等同于原 `api-key`），配置写法迁移 |
| app → file Feign 调用 | 同上，配置写法迁移 |
| file 服务端 | 无影响（Filter 校验逻辑不变） |
| app 服务端 | 无影响（app 不暴露 internal 接口，未被任何服务通过 M2M 调用） |
| 现有 Filter 校验逻辑 | 无影响 |

## 6. Verification Plan

### 6.1 单元测试

`M2mFeignInterceptorTest` + `IndependentM2mFeignInterceptorTest` 必须覆盖：

| 用例 | 期望结果 |
|------|---------|
| services Map 命中目标服务 | 注入对应 apiKey + serviceName |
| services Map 未命中目标服务 | 抛 `IllegalStateException`，异常信息含目标服务名 |
| `RequestTemplate.feignTarget()` 返回 null | 抛 `IllegalStateException` |
| `properties.service-name` 未配置 | 抛 `IllegalStateException` |
| services Map 为空 + properties.apiKey 仍有值（旧配置未迁移场景） | 抛 `IllegalStateException`（迁移期断点，必须显式测试，避免误以为兜底生效） |
| 多个目标服务映射 | 不同 Feign Client 注入不同 apiKey |
| 多个调用并发 | 线程安全（无共享可变状态，天然安全） |

### 6.2 手动验证

1. 改造后启动 admin，配置 `services.knowledge-game-file`，调用 `/api/admin/upload-credential`，验证 file 端 Filter 放行
2. 故意删除 `services` 配置，启动后第一次 Feign 调用应抛 `IllegalStateException`，业务层捕获后返回"文件服务暂不可用"
3. 故意把 `services.knowledge-game-file` 配成错误 Key，调用应被 file Filter 拦截返回 401
4. 临时加一条 `services.knowledge-game-app` 配置（即使 app 未暴露 internal 接口），验证多目标映射能力（不需真实调用，只看拦截器注入）

### 6.3 回滚标准

- 恢复 `M2mAuthProperties` 移除 `services` 字段
- 恢复 `M2mFeignInterceptor.apply` 原逻辑
- admin `application.yml` 改回 `m2m.auth.api-key`
- `docs/requirements.md` REQ-91 状态改回 `done`

## 7. 不在本需求范围

- 被调用方多 Key 校验（如未来需要按调用方白名单）—— 不实现，REQ-91 V1 简化模型不变
- app / admin 新增对 **非 file** 的其他服务调用 —— 本轮不涉及。admin → file（现有）、app → file（现有）已通过 `services` Map 统一覆盖；未来新增调用链路时仅需在调用方 yml 增加一条 `services.<目标服务名>` 即可，零代码改动
- 配置中心动态刷新 `services` —— 沿用现有 `@ConfigurationProperties`，支持 Spring Cloud 配置刷新（如有需要另起需求）
- API Key 轮换机制 —— 不在本需求范围

## 8. 风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| 升级 feign-core 后 `feignTarget()` API 变化 | 已验证 13.6.1 可用；锁版本，升级时回归测试 |
| 漏配 services 导致线上首次调用失败 | 选择"抛错不回落"策略是为此 —— 显性失败优于隐性 401 |
| 多服务并发调用线程安全 | Properties 是 Spring 单例 Bean，但只读访问，无共享可变状态 |
| 异常向上冒泡被吞掉难排查 | `IllegalStateException` 消息含目标服务名；业务层应捕获并打 ERROR 日志 |
