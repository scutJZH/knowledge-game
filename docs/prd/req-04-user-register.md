# REQ-04 用户注册 API

## 产品定位

提供用户注册接口，密码 BCrypt 加密存储。同时搭建 component-auth 认证组件模块的基础骨架。

## 用户故事

**作为** 新用户
**我想要** 通过用户名 + 密码 + 昵称注册账号
**以便于** 参与知识记忆卡牌游戏

## 前置依赖

- REQ-03（GlobalExceptionHandler）已完成
- 需新建 `knowledge-game-components/component-auth` Maven 模块

## 功能需求

### API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users/register` | 用户注册 |

**请求体：**
```json
{
  "username": "player1",
  "password": "123456",
  "nickname": "玩家一"
}
```

**字段规则：**
- `username`：必填，2~50 字符，唯一
- `password`：必填，6~50 字符
- `nickname`：必填，1~50 字符

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "player1",
    "nickname": "玩家一",
    "role": "USER"
  }
}
```

### 组件模块搭建（Issue 0）

创建 `knowledge-game-components/component-auth` Maven 模块：

```
knowledge-game-components/         (父 POM, packaging=pom)
└── component-auth/
    ├── pom.xml                    (依赖 spring-security-crypto + spring-boot-starter)
    └── src/main/java/com/knowledgegame/auth/
        └── security/
            └── PasswordEncoder.java   (BCrypt 封装)
```

**依赖链：** component-auth 零业务依赖，不依赖 core。

## 验收标准

- [ ] component-auth 模块创建，`mvn clean install` 通过
- [ ] PasswordEncoder Bean 可在 app/admin 中注入使用
- [ ] 注册接口：用户名重复返回 400 业务异常
- [ ] 注册接口：密码 BCrypt 加密存储（非明文）
- [ ] 注册接口：返回 Result<UserResponse>，不含密码
- [ ] 原有 UserController 的 create 方法替换为 register 或共存
- [ ] User 领域实体/PO 同步更新：移除 totalPoints、dailyStreak、lastLoginDate（迁移到群组维度）
- [ ] Controller 通过 RegisterCommand 隔离 domain 层（遵循 CLAUDE.md Coding Rules）

## 技术决策

| 决策项 | 选择 |
|--------|------|
| 密码加密 | BCrypt（spring-security-crypto） |
| 模块结构 | component-auth 作为 components 的子模块 |
| 原有 create 接口 | 保留但标记为内部使用，register 为正式注册接口 |
| Controller 隔离 | Controller 转 RegisterCommand → AppService 转 User 领域对象 |
| User 模型同步 | 移除 totalPoints/dailyStreak/lastLoginDate，与 card-system-data-model.md 对齐 |
