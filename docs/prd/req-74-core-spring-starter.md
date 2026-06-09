# REQ-74 Core 模块改造为 Spring Boot Starter + 包名整改

## 产品定位

将 `knowledge-game-core` 和 `component-auth` 从普通 JAR 改造为 Spring Boot Starter，使其 Bean 自动注册到 Spring 容器。同时对 core 和 app 模块的包名进行整改，增加模块标识后缀（`.core` / `.app`），使三个模块的包结构清晰可辨。

## 用户故事

**作为** 后端开发者
**我想要** core 模块作为 Spring Boot Starter 自动注册所有 Bean，且各模块包名有清晰的模块标识
**以便于** admin/app 模块不再需要 `@ComponentScan` 跨包扫描和手动 `DomainBeanRegister`，且通过包名即可区分代码属于哪个模块

## 当前问题

| 问题 | 位置 | 影响 |
|------|------|------|
| admin 需要 `@ComponentScan("com.knowledgegame")` | `KnowledgeGameAdminApplication` | 耦合 core 的包路径，core 重构包名时 consuming 模块要跟着改 |
| admin 需手动 `DomainBeanRegister` 注册领域服务 | `admin/config/DomainBeanRegister` | 每新增领域服务都要改 consuming 模块的配置类 |
| app 主类恰好在 `com.knowledgegame` 下隐式覆盖 | `KnowledgeGameApplication` | 隐式依赖，脆弱；如果 app 主类包路径变化则 Bean 丢失 |
| core 无自动配置机制 | `knowledge-game-core` | 不符合 Spring Boot Starter 惯例，新成员接手困惑 |
| core 和 app 共用 `com.knowledgegame` 根包 | core + app | 从包名无法区分模块归属，admin 已有 `.admin` 但 core/app 没有 |

## 包名整改方案

### 改动前

```
core:  com.knowledgegame.domain / .infrastructure / .common
app:   com.knowledgegame.api / .application / .config / .common
admin: com.knowledgegame.admin.*  (已有 .admin，不变)
```

### 改动后

```
core:  com.knowledgegame.core.domain / .core.infrastructure / .core.common
app:   com.knowledgegame.app.api / .app.application / .app.config / .app.common
admin: com.knowledgegame.admin.*  (不变)
```

### 影响范围

| 模块 | 源文件 | 测试文件 | 改动类型 |
|------|--------|----------|----------|
| core | ~30 | 4 | 移动目录 + 包声明 + import |
| app | ~11 | 2 | 移动目录 + 包声明 + import |
| admin | 12 | 4 | 仅 import（引用 core 的包名变了） |

## 功能需求

### Part A：包名整改

#### A1. Core 模块包名 `com.knowledgegame` → `com.knowledgegame.core`

- 移动目录：`com/knowledgegame/{domain,infrastructure,common}` → `com/knowledgegame/core/{domain,infrastructure,common}`
- 更新所有源文件和测试文件的 `package` 声明
- 更新所有内部 import

#### A2. App 模块包名 `com.knowledgegame` → `com.knowledgegame.app`

- 移动目录：`com/knowledgegame/{api,application,config,common}` → `com/knowledgegame/app/{api,application,config,common}`
- 移动主类：`com.knowledgegame.KnowledgeGameApplication` → `com.knowledgegame.app.KnowledgeGameApplication`
- 更新所有源文件和测试文件的 `package` 声明
- 更新所有内部 import

#### A3. Admin 模块 import 更新

- 所有 `import com.knowledgegame.domain.*` → `import com.knowledgegame.core.domain.*`
- 所有 `import com.knowledgegame.infrastructure.*` → `import com.knowledgegame.core.infrastructure.*`
- 所有 `import com.knowledgegame.common.*` → `import com.knowledgegame.core.common.*`

### Part B：Spring Boot Starter 改造

#### B1. Core pom.xml 添加依赖

添加 `spring-boot-autoconfigure` 依赖。

#### B2. 创建自动配置类

创建 `com.knowledgegame.core.config.KnowledgeGameCoreAutoConfiguration`：
- 通过 `@Bean` 方法注册所有纯 POJO 领域服务（当前仅有 `CardTemplateDomainService`，后续新增领域服务在此类中集中注册）
- 通过 `@ComponentScan` 扫描 `com.knowledgegame.core.infrastructure` 子包（Repository 适配器）

#### B3. 声明自动配置入口

创建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，内容为自动配置类全限定名。

#### B4. 清理 admin 模块

- 移除 `KnowledgeGameAdminApplication` 上的 `@ComponentScan(basePackages = "com.knowledgegame")`
- 删除 `admin/config/DomainBeanRegister.java`（由 core starter 自动注册）

#### B5. 检查并清理 app 模块

- 确认 app 模块是否存在类似的 `DomainBeanRegister`，如有则删除
- app 的 `@SpringBootApplication` 改为扫描 `com.knowledgegame.app` 子包即可，core 的 Bean 通过 starter 自动注册

### Part C：component-auth 改造为 Spring Boot Starter

#### C1. component-auth 改造

- 将 `PasswordEncoder` 配置类从 `@Configuration` 改为 `@AutoConfiguration`
- pom.xml 添加 `spring-boot-autoconfigure` 依赖
- 创建 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 验收标准

- [x] core 模块所有包名从 `com.knowledgegame.*` 改为 `com.knowledgegame.core.*`
- [x] app 模块所有包名从 `com.knowledgegame.*` 改为 `com.knowledgegame.app.*`
- [x] admin 模块所有 import 更新为 `com.knowledgegame.core.*`
- [x] core 模块包含 `AutoConfiguration.imports` 文件和自动配置类
- [x] 自动配置类注册所有领域服务 Bean（当前为 `CardTemplateDomainService`）
- [x] 自动配置类扫描基础设施层 Repository 适配器 + JPA Repository + Entity
- [x] admin 启动类无 `@ComponentScan`，无 `DomainBeanRegister`
- [x] component-auth 作为 Starter 自动注册 PasswordEncoder Bean
- [x] `mvn clean install` 全模块编译通过
- [x] admin/app 启动后能正常注入 core 和 component-auth 的 Bean

## 技术决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| 自动配置注册方式 | Spring Boot 3.x 标准方式 (`AutoConfiguration.imports`) | Spring Boot 3.x 已废弃 `spring.factories` |
| 领域服务注册方式 | `@Bean` 方法显式注册 | 领域服务是纯 POJO，集中管理清晰可控 |
| Repository 适配器注册 | `@ComponentScan` + `@EnableJpaRepositories` + `@EntityScan` | 适配器、JPA Repository 接口、Entity 需分别扫描 |
| core pom 新增依赖 | `spring-boot-autoconfigure` | 仅需自动配置能力，不需完整 starter |
| 不创建独立 starter 模块 | 在 core 内直接实现 | 当前仅一对 consuming 模块，拆分过度设计 |
| admin 包名 | 不变（已有 `.admin`） | 已满足模块标识需求 |
| 包名整改顺序 | 先包名，后 Starter | 包名变更是 Starter 自动配置类路径的前置依赖 |
| component-auth | 直接用现有类作 `@AutoConfiguration` | 仅一个 Bean，无需拆分 |
