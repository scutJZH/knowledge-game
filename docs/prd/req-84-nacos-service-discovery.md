# REQ-84：Nacos 服务注册与发现 + 配置中心 + OpenFeign

> 状态：`designed`
> 创建：2026-06-11

## 1. 概述

引入 Spring Cloud Alibaba Nacos 实现服务注册与发现、配置中心，并引入 Spring Cloud OpenFeign 为服务间调用提供基础设施。同时升级 Spring Boot 至 3.5.x 以匹配 SCA 2025.0.0.0 版本要求。

> **注意：** Spring Boot 3.5.x OSS 支持将于 2026-06-30 结束。本轮选择 3.5.x 是因为 SCA 2025.0.0.0 原生适配该版本；后续可跟随 SCA 2025.1.x 分支升级到 Spring Boot 4.x（需较大改动，建议作为独立需求规划）。

**前置依赖：** REQ-82（日志+TraceId）完成后执行。REQ-82 的 `knowledgegame.log.masking-fields` 配置将在本需求中迁移到 Nacos。

## 2. 用户故事

- **作为开发者**，我希望各服务启动时自动注册到 Nacos，以便通过服务名发现其他服务
- **作为开发者**，我希望公共配置（MySQL、JWT 等）集中在 Nacos 配置中心管理，以便统一维护和动态刷新
- **作为开发者**，我希望通过 OpenFeign 声明式调用其他服务的接口，以便快速实现服务间通信
- **作为开发者**，我希望服务注册/发现/配置中心的基础设施对现有业务代码零侵入，以便不影响已有功能

## 3. 功能需求

### 3.1 Spring Boot 版本升级

- 将 Spring Boot 从 3.4.1 升级至 **3.5.x**（最新稳定补丁版本）
- 升级后需验证现有全部 API 功能正常（认证、CRUD 等）

**Spring Boot 3.4 → 3.5 Breaking Changes 验证清单：**

| 变更项 | 本项目影响 | 验证方式 |
|--------|-----------|---------|
| 3.3 deprecated API 移除 | 低风险（项目从 3.4 起步） | 编译时检查 |
| `@ConditionalOnMissingBean` 现匹配泛型返回类型 | 需关注 — core AutoConfiguration 中的 `@Bean` 方法 | 编译 + 启动验证 |
| `spring-boot-parent` 不再发布 | 不影响（用 `spring-boot-starter-parent`） | 无需验证 |
| `taskExecutor` Bean 别名移除 | 不影响（未用 `@Async`） | 无需验证 |
| Profile 命名规则收紧 | 不影响（无特殊字符 profile） | 无需验证 |

### 3.2 Parent POM 依赖管理

- 在 parent POM 的 `<dependencyManagement>` 中引入：
  - `spring-cloud-dependencies` BOM（2025.0.0）
  - `spring-cloud-alibaba-dependencies` BOM（2025.0.0.0）
- 在 `<properties>` 中统一管理 Spring Cloud、SCA 版本号
- 各服务模块按需引入具体 starter，不继承全量依赖

### 3.3 Nacos 服务注册与发现

- **注册服务**：
  - `knowledge-game-app`（用户端，端口 8082，服务名 `knowledge-game-app`）
  - `knowledge-game-admin`（管理端，端口 8081，服务名 `knowledge-game-admin`）
  - `knowledge-game-file`（文件服务，端口 **8083**，待 REQ-83 实现，本轮预留配置模板）

> **端口分配：** Nacos Server=8080, admin=8081, app=8082, file=8083。REQ-83 中 file 服务端口需从 8082 改为 8083 以避免与 app 冲突。
- **配置方式**：使用 `spring.config.import=nacos:` 方式加载 Nacos 配置（Spring Cloud 2025 已移除 bootstrap 机制）
- **每个服务**需在 `application.yml` 中配置：
  - `spring.application.name`（服务名）
  - `spring.cloud.nacos.discovery.server-addr`（Nacos 地址）
  - `spring.cloud.nacos.discovery.namespace`（命名空间，可选）
- **依赖引入**：各服务模块添加 `spring-cloud-starter-alibaba-nacos-discovery`

### 3.4 Nacos 配置中心

- **共享配置**（抽取到 Nacos `common.yml`）：
  - 数据源配置（MySQL URL、用户名、密码）
  - JPA 公共配置（`show-sql: true`、`open-in-view: false`）
  - JWT 配置（secret、过期时间）
  - 日志脱敏字段列表（`knowledgegame.log.masking-fields`）
- **服务级别配置**（保留本地 `application.yml` 或 Nacos `{service-name}.yml`）：
  - `server.port`
  - `spring.application.name`
  - `cors.allowed-origins`
  - `spring.jpa.hibernate.ddl-auto`（各服务不同）
  - Nacos 连接信息本身
- **Nacos 配置结构**：
  - 共享配置：`common.yml`（Data ID），放在 `DEFAULT_GROUP` 组
  - 服务专属配置：`{service-name}.yml`（如 `knowledge-game-app.yml`）
- **依赖引入**：各服务模块添加 `spring-cloud-starter-alibaba-nacos-config`
- **动态刷新**：使用 `@RefreshScope` 或 `@ConfigurationProperties` 支持配置热更新（不使用 `@Value` + `@RefreshScope` 组合，避免代理问题）

### 3.5 OpenFeign 基础设施

- **新建共享模块** `component-feign`，存放所有 Feign Client 接口
- 模块结构：
  ```
  component-feign/
  ├── pom.xml
  └── src/main/java/com/knowledgegame/components/feign/
      ├── config/
      │   └── FeignAutoConfiguration.java
      └── client/
          └── (待具体业务需求时添加 Feign Client)
  ```
- **本轮不编写具体 Feign Client**（尚无服务间调用场景），只搭建模块骨架和自动配置
- 各服务模块添加 `spring-cloud-starter-openfeign` 依赖 + `component-feign` 依赖
- 启动类添加 `@EnableFeignClients` 注解，扫描 `com.knowledgegame.components.feign.client` 包

### 3.6 统一 application.yml 结构

升级后各服务的 `application.yml` 仅保留：

```yaml
server:
  port: 8082

spring:
  application:
    name: knowledge-game-app
  config:
    import:
      - nacos:common.yml?refresh=true
      - nacos:knowledge-game-app.yml?refresh=true
  cloud:
    nacos:
      server-addr: 127.0.0.1:8080
      discovery:
        namespace: ${NACOS_NAMESPACE:}
      config:
        namespace: ${NACOS_NAMESPACE:}
```

> `spring.application.name` 为新增配置项（当前 application.yml 中未设置），Nacos 服务注册依赖此属性。
> `logback-spring.xml` 保留在各服务本地 resources 目录，不迁移到 Nacos（Spring 上下文初始化前即需加载）。

> `NACOS_NAMESPACE` 环境变量用于区分开发/测试/生产环境，默认为空（public 命名空间）。

## 4. 技术方案

### 4.1 版本选型

| 组件 | 当前版本 | 目标版本 |
|------|---------|---------|
| Spring Boot | 3.4.1 | 3.5.x（最新补丁） |
| Spring Cloud | 无 | 2025.0.0 |
| Spring Cloud Alibaba | 无 | 2025.0.0.0 |
| nacos-client | 无 | 3.0.3（随 SCA 引入） |
| Nacos Server | 3.1.1 | 不变（兼容 3.0.3 客户端） |

### 4.2 Maven 依赖结构

**parent POM 新增：**
```xml
<properties>
    <spring-cloud.version>2025.0.0</spring-cloud.version>
    <spring-cloud-alibaba.version>2025.0.0.0</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Cloud Alibaba BOM -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- component-feign -->
        <dependency>
            <groupId>com.knowledgegame</groupId>
            <artifactId>component-feign</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**各服务模块新增依赖：**
```xml
<!-- Nacos 服务注册与发现 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

<!-- Nacos 配置中心 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>

<!-- OpenFeign -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<!-- Feign Client 共享模块 -->
<dependency>
    <groupId>com.knowledgegame</groupId>
    <artifactId>component-feign</artifactId>
</dependency>
```

### 4.3 component-feign 模块

```
backend/knowledge-game-components/component-feign/
├── pom.xml
└── src/main/
    ├── java/com/knowledgegame/components/feign/
    │   ├── config/
    │   │   └── FeignAutoConfiguration.java     ← @AutoConfiguration，注册 Feign 相关 Bean
    │   └── client/                              ← Feign Client 接口目录（本轮为空）
    └── resources/
        └── META-INF/spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

- `pom.xml` 依赖：`spring-cloud-starter-openfeign`（provided scope，由使用方提供实际依赖）
- 本轮不包含 LoadBalancer 依赖（单实例部署暂不需要客户端负载均衡，后续有需要时添加）

### 4.4 各服务启动类变更

- 添加 `@EnableFeignClients(basePackages = "com.knowledgegame.components.feign.client")`
- 不添加 `@EnableDiscoveryClient`（Spring Cloud 2025 自动注册，无需显式声明）

### 4.5 Nacos Server 安全措施

- **开启鉴权**：Nacos Server 必须开启 `nacos.core.auth.enabled=true`，防止未授权访问
- **命名空间隔离**：通过 `NACOS_NAMESPACE` 环境变量区分开发/测试/生产环境
- **敏感配置**：本轮使用 Nacos 明文存储（数据库密码、JWT secret）；后续可通过 Nacos 内置加密或外部密钥管理（如 Vault）增强安全性

### 4.6 Nacos Server 侧操作

在 Nacos 控制台中手动创建以下配置：

| Data ID | Group | 格式 | 内容 |
|---------|-------|------|------|
| `common.yml` | DEFAULT_GROUP | YAML | 数据源、JPA 公共配置、JWT、日志脱敏等共享配置 |
| `knowledge-game-app.yml` | DEFAULT_GROUP | YAML | app 专属配置（如 JPA ddl-auto: none） |
| `knowledge-game-admin.yml` | DEFAULT_GROUP | YAML | admin 专属配置（如 JPA ddl-auto: update） |

> 本轮通过文档说明手动创建步骤，后续可考虑自动化脚本。

### 4.8 依赖关系

```
parent POM
  ├── spring-cloud-dependencies BOM
  ├── spring-cloud-alibaba-dependencies BOM
  ├── component-auth（已有）
  ├── component-exception（已有）
  ├── component-log（已有）
  └── component-feign（新增）

component-feign → spring-cloud-starter-openfeign（provided）
app → component-feign, nacos-discovery, nacos-config, openfeign, component-auth, component-exception, component-log
admin → component-feign, nacos-discovery, nacos-config, openfeign, component-auth, component-exception, component-log
```

## 5. Impact Analysis

### 5.1 新增文件

| 文件 | 说明 |
|------|------|
| `backend/knowledge-game-components/component-feign/pom.xml` | Feign 共享模块 POM |
| `backend/knowledge-game-components/component-feign/src/.../config/FeignAutoConfiguration.java` | Feign 自动配置类 |
| `backend/knowledge-game-components/component-feign/src/.../AutoConfiguration.imports` | 自动配置注册 |
| `backend/knowledge-game-components/component-feign/src/.../client/.gitkeep` | Feign Client 目录占位 |

### 5.2 修改文件

| 文件 | 变更内容 |
|------|---------|
| `backend/pom.xml`（parent） | Spring Boot 版本 3.4.1 → 3.5.x；新增 Spring Cloud + SCA BOM；新增 component-feign 版本管理 |
| `backend/knowledge-game-components/pom.xml` | 添加 component-feign 子模块 |
| `backend/knowledge-game-app/pom.xml` | 新增 nacos-discovery、nacos-config、openfeign、component-feign 依赖 |
| `backend/knowledge-game-admin/pom.xml` | 同上 |
| `backend/knowledge-game-app/src/main/resources/application.yml` | 精简为仅含端口、服务名、Nacos 连接、spring.config.import；新增 `spring.application.name` |
| `backend/knowledge-game-admin/src/main/resources/application.yml` | 同上 |
| `backend/knowledge-game-app/src/.../KnowledgeGameApplication.java` | 添加 @EnableFeignClients |
| `backend/knowledge-game-admin/src/.../KnowledgeGameAdminApplication.java` | 同上 |

### 5.3 配置变更

| 配置 | 说明 |
|------|------|
| Nacos Server 新建 `common.yml` | 数据源、JWT、日志脱敏共享配置 |
| Nacos Server 新建 `knowledge-game-app.yml` | app 专属配置 |
| Nacos Server 新建 `knowledge-game-admin.yml` | admin 专属配置 |
| `application.yml`（app/admin） | 精简，公共配置迁移到 Nacos |

### 5.4 受影响的现有功能

| 功能 | 影响 |
|------|------|
| Spring Boot 版本 | 3.4.1 → 3.5.x，需全量回归验证 |
| 所有 API 请求 | Nacos Discovery Filter 自动生效，不影响业务逻辑 |
| 数据源配置 | 从本地 yml 迁移到 Nacos，启动时从 Nacos 拉取 |
| JWT 配置 | 从本地 yml 迁移到 Nacos，行为不变 |
| 现有单元测试 | 需验证是否因 Spring Boot 升级导致 API 变化 |
| logback-spring.xml | 保留本地，不迁移到 Nacos（Spring 上下文初始化前即需加载） |

### 5.5 不受影响的部分

| 功能 | 原因 |
|------|------|
| domain 层代码 | 纯 Java，无框架依赖 |
| infrastructure 层 | JPA 注解不受 Spring Boot 小版本升级影响 |
| 前端代码 | 纯后端基础设施变更，前端无感知 |

## 6. Verification Plan

### 6.1 手动验证

1. `mvn clean install` 全量构建通过，无编译错误
2. 启动 Nacos Server（端口 8080），确认其运行正常
3. 在 Nacos 控制台创建 `common.yml`、`knowledge-game-app.yml`、`knowledge-game-admin.yml` 配置
4. 启动 admin 模块（8081），确认 Nacos 控制台服务列表中出现 `knowledge-game-admin`
5. 启动 app 模块（8082），确认 Nacos 控制台服务列表中出现 `knowledge-game-app`
6. 调用已有 API（注册、登录、知识点分类 CRUD），确认功能正常
7. 修改 Nacos 中 `common.yml` 的某个配置值，确认服务自动刷新（如日志级别）
8. 停止 Nacos Server，确认已运行的服务仍可正常响应请求（Nacos 本地缓存机制）
9. **Nacos 不可用时首次启动**：停止 Nacos 后尝试启动新服务实例，确认启动失败并给出明确错误信息（`spring.config.import=nacos:` 默认要求连接成功）。评估是否需要配置 `spring.cloud.nacos.config.import-check.enabled` 容错
10. 检查日志输出中无 Nacos 相关 ERROR

### 6.2 自动化测试

- 现有单元测试全部通过（`mvn test`）
- 现有 Controller 测试（`@WebMvcTest`）全部通过
- `FeignAutoConfigurationTest`：验证自动配置类加载正确

### 6.3 回滚标准

- 移除 app/admin 的 nacos-discovery、nacos-config、openfeign、component-feign 依赖
- 恢复 app/admin 的 `application.yml` 为完整本地配置
- 移除启动类的 `@EnableFeignClients` 注解
- 还原 parent POM 的 Spring Boot 版本为 3.4.1，移除 Spring Cloud + SCA BOM
- 删除 `component-feign` 模块
