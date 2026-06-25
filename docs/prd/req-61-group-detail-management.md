# REQ-61 群组详情 + 管理页面

## 产品定位

用户端群组详情页面，用户从群组列表点击卡片进入。包含群组信息展示、成员列表（含积分排名）、知识库入口、群组设置管理。同时补齐前端缺失的群组详情与成员列表后端 API。

## 用户故事

**作为** 群组成员
**我想要** 查看群组详情、成员积分排名、浏览群组题库
**以便于** 了解群组状态，进入游戏，从题库学习提升通关率

**作为** OWNER/ADMIN
**我想要** 管理群组成员、编辑群组信息、管理邀请码
**以便于** 维护群组秩序和配置

## 前置依赖

| 需求 | 状态 | 依赖内容 |
|------|------|---------|
| REQ-26 | done | 前端项目搭建（React + Vite + 路由 + MainLayout） |
| REQ-27 | done | Axios 封装 + 认证状态管理 |
| REQ-48 | done | 创建群组 API + StudyGroupRepository 出端口 |
| REQ-49 | done | 成员管理 API（joinByInvite / leave / getCurrentMember）+ 邀请码 |
| REQ-50 | done | 管理员设置与转让 API |
| REQ-60 | done | 群组列表页（卡片点击跳转 /groups/:id） |

> REQ-60 已实现 `listMyGroups` 后端 API 和前端 GroupList 页面，卡片点击 `navigate('/groups/' + id)` 指向本需求页面。

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 页面结构 | 顶部信息卡片 + 底部三 Tab（成员/知识库/设置） | 成员 Tab 和设置 Tab 内容差异大，Tab 分隔清晰；知识库 Tab 作全局题库入口 |
| 设置 Tab 可见性 | 仅 OWNER + ADMIN 可见 | MEMBER 无管理权限，不显示 Tab 减少干扰 |
| 邀请码可见性 | OWNER + ADMIN 可见可重新生成 | ADMIN 需要发邀请码给新成员，不必事事找 OWNER |
| 成员列表排序 | 按累计总积分降序 | 促进良性竞争；积分是累计值（含已消费用于兑换的部分） |
| 成员列表前三名 | 奖牌图标（🥇🥈🥉） | 激励效果 |
| 成员操作 | 通过「更多」下拉按钮 | 保持列表简洁，避免每行多个操作按钮挤占空间 |
| 游戏入口 | 信息卡片内醒目「开始游戏」按钮 | 群组首页自然承载游戏入口跳转 |
| 知识库 Tab | 按分类展示题目统计 → 分类内题目列表 | 用户按分类浏览题库，关联学习提高游戏通关率 |
| 解散群组 | 走回收站（REQ-100） | 与其他资源 DELETE 语义一致，物理移入回收站 |
| 知识库不按群组隔离 | 共用全局题库 + 分类 | 题库全局共享，群组只是入口维度 |
| StudyGroup 新增 status 字段 | 新增 StudyGroupStatus 枚举（ACTIVE/INACTIVE） | 对接回收站 restore 时强制设 INACTIVE，同时为后续停用/启用预留。DDL：`ALTER TABLE study_group ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` |

## 页面结构

```
/groups/:id
┌─────────────────────────────────┐
│  群组信息卡片                    │
│  [头像] 名称 | 描述 | 邀请码行   │
│  加入方式 | 成员数 | 创建时间    │
│  [开始游戏] 按钮                │
├─────────────────────────────────┤
│  [成员] [知识库] [设置*]        │  ← 设置仅 OWNER+ADMIN 可见
│  (Tab 内容区)                    │
└─────────────────────────────────┘
```

## 功能需求

### 一、群组信息卡片

所有成员可见。展示内容：

| 字段 | 说明 |
|------|------|
| 头像 | 有自定义图显示图片，无图显示首字渐变方块 |
| 名称 | 粗体 |
| 描述 | 普通文字（可为空） |
| 邀请码 | OWNER + ADMIN 可见，8 位码 + 复制按钮 + 重新生成按钮 |
| 加入方式 | OPEN / 仅邀请 |
| 成员数 | 从成员列表获取 |
| 创建时间 | dayjs 格式化 |

「开始游戏」按钮：醒目样式，点击进入游戏流程（REQ-31~34 后续实现，当前可 toast 提示"即将开放"或跳转占位）。

### 二、成员 Tab

#### 成员列表

每行展示：头像 + 昵称 + 角色标签 + 累计总积分

- 角色标签：OWNER 紫色 / ADMIN 蓝色 / MEMBER 灰色
- 按累计总积分降序排列
- 前三名显示奖牌图标（🥇🥈🥉）
- 通过「更多」下拉按钮触发操作

#### 「更多」操作矩阵

| 操作 | 可见角色 | 目标对象限制 |
|------|---------|------------|
| 查看详情 | 所有人 | 弹窗：头像 + 昵称 + 角色标签 + 累计积分 + 加入时间 |
| 提升为管理员 | OWNER | 当前为 MEMBER |
| 降级为成员 | OWNER | 当前为 ADMIN |
| 转让群主 | OWNER | ADMIN / MEMBER，需二次确认弹窗 |
| 踢出群组 | OWNER + ADMIN | ADMIN 只能踢 MEMBER；OWNER 可踢 ADMIN/MEMBER；不可踢 OWNER |

- 当前用户本人的行不显示「踢出群组」「提升/降级」「转让群主」操作
- 转让群主：二次确认弹窗，确认后调 `POST /api/study-groups/{id}/transfer-ownership`
- 踢出群组：二次确认弹窗，确认后调 `DELETE /api/study-groups/{id}/members/{userId}`

#### 成员详情弹窗

点击「查看详情」弹出 Modal/Drawer 展示：
- 头像 + 昵称
- 角色标签
- 累计积分
- 加入时间

> 成员信息中的头像和昵称从 `UserRepositoryPort.findByIdIn` 批量获取后组装。不展示 IP 收集进度（卡牌收集功能尚未实现，后续需求补齐）。

### 三、知识库 Tab

#### 阶段一：分类树展示（本期必做）

- 调用已有 `GET /api/knowledge-categories/tree` 获取分类树
- 扁平展示为卡片列表（或网格），每个卡片显示分类名称
- 验收标准：分类树正确渲染，加载失败显示 message.error + 重试按钮

#### 阶段二：分类题目列表（本期必做）

依赖新增用户端题目查询接口：
- 本需求新增 `GET /api/questions?categoryId={id}&page={page}&size={size}`（用户端 app 模块，复用 core 已有 `QuestionRepository` 出端口和 JPA Repository）。**用户端查询默认过滤 `status=ACTIVE`**，仅返回已启用的题目，不暴露 INACTIVE 题目
- 点击分类卡片 → 进入该分类的题目列表（子页面或展开区）
- 列表项：题干 text（截断 80 字）+ 创建时间，分页
- 点击标题 → 展开/弹窗显示题干 + 答案
- 题目详情底部：链接「查看相关知识点」→ 跳转知识库浏览（REQ-98 后续对接）
- 验收标准：分页正确、分类切换正确刷新列表、展开答案正确显示

> 题目数量统计（分类卡片上的题目计数）不在本需求范围，等后续需求补充统计接口。

### 四、设置 Tab（仅 OWNER + ADMIN 可见）

#### 编辑群组信息（仅 OWNER）

表单字段：
- 群组名称：必填，Input，max 50
- 描述：选填，Input.TextArea，max 500
- 头像：选填，AvatarUpload 组件（REQ-60 已建）
- 加入方式：Radio（OPEN / INVITE_ONLY）

调 `PUT /api/study-groups/{id}` 提交。

#### 邀请码管理（OWNER + ADMIN）

- 展示当前邀请码（8 位）+ 复制按钮
- 「重新生成」按钮，调 `POST /api/study-groups/{id}/invite-code/regenerate`

#### 解散群组（仅 OWNER，危险操作）

- 红色按钮，点击弹出二次确认
- 确认后输入群组名称以确认（防止误操作）
- 调 `DELETE /api/study-groups/{id}`
- 成功后跳转回群组列表页，message.success

### StudyGroup 新增行为方法

```java
// 更新群组信息（仅必填字段，null=不更新）
public void updateInfo(String name, String description, FileRef avatar, JoinPolicy joinPolicy)

// 激活群组（restore 后手动启用）
public void activate()

// 停用群组（停用而不解散）
public void deactivate()
```

- `updateInfo`：四字段均为可选更新（if-null 守卫），不为 null 时更新并刷新 `updatedAt`
- `activate`/`deactivate`：状态切换方法，含合法性校验（如已 INACTIVE 不能再 deactivate）
- 创建群组默认 status = `ACTIVE`

### 五、后端 API 清单

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/study-groups/{id}` | 群组详情 + 当前用户角色 | **新增** |
| GET | `/api/study-groups/{id}/members` | 成员列表（含用户昵称头像、按累计积分降序） | **新增** |
| PUT | `/api/study-groups/{id}` | 编辑群组信息（仅 OWNER） | **新增** |
| DELETE | `/api/study-groups/{id}` | 解散群组（走回收站，仅 OWNER） | **新增** |
| DELETE | `/api/study-groups/{id}/members/{userId}` | 踢出成员（路由安全：Spring MVC 优先匹配 `/members/me` 而非 `/{userId}`，字符串 "me" 无法 bind 到 Long，不会冲突） | **新增** |
| POST | `/api/study-groups/{id}/invite-code/regenerate` | 重新生成邀请码 | 已有 |
| PUT | `/api/study-groups/{id}/members/{userId}` | 更新成员角色 | 已有 |
| POST | `/api/study-groups/{id}/transfer-ownership` | 转让群主 | 已有 |
| GET | `/api/study-groups` | 我的群组列表 | 已有 |
| GET | `/api/knowledge-categories/tree` | 分类树 | 已有 |
| GET | `/api/questions` | 题目列表（按分类分页） | **新增**（app 模块） |

#### GET /api/study-groups/{id} — 群组详情

**响应：**

```json
{
  "code": 200,
  "data": {
    "id": 1,
    "name": "Java 进阶小组",
    "description": "...",
    "avatarFileId": 123,
    "avatarUrl": "https://...",
    "ownerId": 100,
    "joinPolicy": "OPEN",
    "inviteCode": "A1B2C3D4",
    "myRole": "OWNER",
    "memberCount": 12,
    "createdAt": 1718800000000,
    "updatedAt": 1718800000000
  }
}
```

**字段说明：**
- `inviteCode`：OWNER/ADMIN 返回真实码，MEMBER 返回 null
- `myRole`：当前用户在该群组的角色
- 其他字段复用 `StudyGroupResponse` 已有结构

#### PUT /api/study-groups/{id} — 编辑群组信息

仅 OWNER 可操作。

**请求体：**

```json
{
  "name": "新名称",
  "description": "新描述",
  "avatarFileId": 456,
  "joinPolicy": "INVITE_ONLY"
}
```

- 四个字段均为可选（发什么更新什么，不发不更新）
- `null` 表示不更新（与 `JsonNullable.undefined()` 语义一致）
- 头像仅接受 `avatarFileId`（Long），url 由后端从 file 服务获取并强制覆盖（零信任写入原则）
- 调 `StudyGroup.updateInfo(name, description, newFileRef, joinPolicy)` 行为方法

#### GET /api/study-groups/{id}/members — 成员列表

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "userId": 100,
      "nickname": "张三",
      "avatarFileId": 1,
      "avatarUrl": "https://...",
      "role": "OWNER",
      "points": 1500,
      "joinedAt": 1718800000000
    }
  ]
}
```

**说明：**
- 按 `points` 降序排列（字段名与 GroupMember 实体一致）
- `points` 为群组内累计总积分（含已消费用于兑换的部分）
- 成员头像和昵称来源：调 `UserRepositoryPort.findByIdIn(List<Long> ids)` 批量获取用户信息后内存组装

### 六、Recycle Bin 适配（解散群组）

解散群组走 REQ-100 回收站机制。前置改造：StudyGroup 需新增 status 字段以符合回收站 contract（restore 后资源为 INACTIVE）。

**DDL：** `ALTER TABLE study_group ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER join_policy;`

**StudyGroupStatus 枚举（core/domain/model/domainenum/）：**
- `ACTIVE` — 正常状态（新建群组默认值）
- `INACTIVE` — 停用状态（从回收站恢复后进入）

**领域实体变更：**
- StudyGroup 新增 `status: StudyGroupStatus` 字段
- `create()` 工厂方法初始化 `status = StudyGroupStatus.ACTIVE`
- `reconstruct()` 新增 `StudyGroupStatus status` 参数
- 新增 `activate()` / `deactivate()` 行为方法（供后续停用/启用使用）
- 更新时间戳的现有方法无需改动

**PO 变更：**
- StudyGroupPO 新增 `@Column(nullable = false, length = 20) @Enumerated(EnumType.STRING) private StudyGroupStatus status;`（默认 ACTIVE）
- Converter 新增 status 字段映射

**回收站适配：**
- 删除时物理移入 `recycle_bin` 表
- 新建 `StudyGroupRecycleBinItemStrategy` 实现 `RecycleBinItemStrategy<StudyGroup>`
- purge 时清理群组关联数据（成员记录等）+ 文件清理（头像，通过 `FileCleanupPort` 解耦）
- restore 时群组 status 设为 `INACTIVE`（用户手动启用）

### 七、错误处理

| 场景 | 处理方式 |
|------|---------|
| 群组不存在 | 404 页面或 Result error + 返回群组列表按钮 |
| 非成员访问 | `NOT_GROUP_MEMBER`，提示"您不是该群组成员" + 返回群组列表 |
| 非 OWNER 操作 | `NOT_GROUP_OWNER(403, "仅群主可操作")`，message.error（已有） |
| 非管理员操作 | `NOT_GROUP_ADMIN`，message.error（**新增**） |
| 踢出 OWNER | 前端按钮不可见 + 后端拒绝（`CANNOT_KICK_OWNER`，**新增**） |
| 转让需二次确认 | Modal 确认框，确认后执行 |
| 解散需输入群组名确认 | Modal 输入框 + 确认按钮 |
| 成员列表加载失败 | message.error + 重试按钮 |
| 知识库分类加载失败 | message.error + 重试按钮 |
| 编辑群组表单校验失败 | antd Form 内置提示 |

**需新增 ResultCode：**
- `NOT_GROUP_ADMIN(403, "仅管理员可操作")` — 仅 ADMIN 权限操作（如踢人）时使用
- `CANNOT_KICK_OWNER(400, "不能踢出群主")` — 防止踢出 OWNER

## 前端文件结构

```
frontend/user/src/
├── pages/GroupDetail/
│   ├── index.tsx                  ← 主页面（信息卡片 + Tab 容器）
│   ├── GroupDetail.css            ← 样式
│   ├── GroupInfoCard.tsx          ← 群组信息卡片组件
│   ├── MemberTab.tsx              ← 成员 Tab
│   ├── MemberListItem.tsx         ← 成员列表行组件
│   ├── MemberDetailModal.tsx      ← 成员详情弹窗
│   ├── KnowledgeTab.tsx           ← 知识库 Tab（分类统计 + 题目列表）
│   ├── SettingsTab.tsx            ← 设置 Tab
│   ├── EditGroupModal.tsx         ← 编辑群组 Modal
│   ├── TransferConfirmModal.tsx   ← 转让确认 Modal
│   ├── KickConfirmModal.tsx       ← 踢出确认 Modal
│   ├── DisbandConfirmModal.tsx    ← 解散确认 Modal
│   └── __tests__/
│       ├── index.test.tsx
│       ├── GroupInfoCard.test.tsx
│       ├── MemberTab.test.tsx
│       ├── KnowledgeTab.test.tsx
│       └── SettingsTab.test.tsx
├── services/group-api.ts          ← 修改：加群组详情/成员列表/编辑/解散/踢人 API
├── types/group.ts                 ← 修改：加新接口 TS 类型
└── routes/index.tsx               ← 修改：加 /groups/:id 路由
```

## 后端文件结构

```
backend/knowledge-game-app/src/main/java/com/knowledgegame/app/
├── api/
│   ├── controller/
│   │   ├── StudyGroupController.java          ← 修改：加 getDetail/update/disband
│   │   ├── GroupMemberController.java         ← 修改：加 listMembers/kick
│   │   └── QuestionController.java            ← 新增：GET /api/questions（按分类分页）
│   ├── dto/
│   │   ├── StudyGroupDetailResponse.java      ← 新增
│   │   ├── UpdateStudyGroupRequest.java       ← 新增
│   │   ├── GroupMemberListResponse.java       ← 新增
│   │   └── QuestionListResponse.java          ← 新增
│   └── assembler/
│       ├── StudyGroupAssembler.java           ← 修改
│       ├── GroupMemberAssembler.java          ← 修改
│       └── QuestionAssembler.java             ← 新增
└── application/
    └── service/
        ├── StudyGroupAppService.java           ← 修改：加 getDetail/update/disband
        ├── GroupMemberAppService.java          ← 修改：加 listMembers/kick
        └── QuestionAppService.java             ← 新增：题目查询

backend/knowledge-game-core/src/main/java/com/knowledgegame/core/
├── domain/
│   ├── model/
│   │   ├── entity/
│   │   │   ├── StudyGroup.java                ← 修改：加 status/updateInfo/activate/deactivate
│   │   │   └── GroupMember.java               ← 无需修改（已有 promote/demote/transfer）
│   │   └── domainenum/
│   │       └── StudyGroupStatus.java          ← 新增：ACTIVE/INACTIVE
│   ├── port/outbound/
│   │   ├── StudyGroupRepository.java          ← 修改：确认 deleteById 是否可废弃（解散走回收站）
│   │   ├── GroupMemberRepository.java         ← 修改：加 findByGroupIdIn + findByGroupIdOrderByPointsDesc
│   │   └── UserRepositoryPort.java            ← 修改：加 findByIdIn(List<Long> ids) 批量查询
│   └── service/
│       └── StudyGroupRecycleBinItemStrategy.java ← 新增（回收站策略）
└── infrastructure/
    ├── db/
    │   ├── entity/
    │   │   └── StudyGroupPO.java               ← 修改：加 status 字段
    │   ├── repository/
    │   │   ├── StudyGroupJpaRepository.java    ← 确认 deleteById 是否可废弃
    │   │   ├── GroupMemberJpaRepository.java   ← 修改：加 findByGroupIdIn + findByGroupIdOrderByPointsDesc
    │   │   └── UserJpaRepository.java          ← 修改：加 findByIdIn
    │   └── converter/
    │       └── StudyGroupConverter.java         ← 修改：加 status 字段映射
    └── adapter/repoadapter/
        ├── StudyGroupRepositoryAdapter.java   ← 修改：加 status 相关 + 确认 deleteById
        ├── GroupMemberRepositoryAdapter.java  ← 修改：加 findByGroupIdIn + findByGroupIdOrderByPointsDesc
        └── UserRepositoryAdapter.java         ← 修改：加 findByIdIn
```

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| **前端组件测试（vitest + @testing-library/react）** | 群组信息卡片渲染（OWNER/ADMIN/MEMBER 三种角色不同视图）；成员列表排序 + 奖牌；更多下拉操作可见性矩阵；设置 Tab 可见性（OWNER/ADMIN/MEMBER）；编辑表单校验 + 提交；解散/踢出/转让二次确认流程 |
| **前端 TS 类型检查** | `npx tsc --noEmit` 确保类型对齐后端 DTO |
| **AppService 单测（Mockito）** | getDetail 三种角色返回不同 inviteCode；listMembers 排序正确；kick 角色权限矩阵（OWNER 踢 ADMIN、ADMIN 踢 MEMBER、MEMBER 不可踢）；update 仅 OWNER 可调；disband 仅 OWNER 可调 |
| **Controller 单测（Mockito）** | `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` Controller + `@Mock` AppService，验证委托链、响应体格式（`assertThat(result.getCode()).isEqualTo(200)`）。不用 `@WebMvcTest`（app 模块 `@SpringBootApplication` 会触发 Nacos 自动配置导致 ApplicationContext 启动失败，已知环境限制） |
| **Repository 集成测试（@DataJpaTest 真实 MySQL）** | findByGroupIdOrderByPointsDesc 排序正确性 |

### 手工验收

1. 从群组列表点击卡片 → 进入 `/groups/:id` → 信息卡片正确展示（头像/名称/描述/加入方式/成员数/创建时间）
2. OWNER 视角：信息卡片显示邀请码 + 重新生成按钮；三 Tab 全部可见
3. ADMIN 视角：信息卡片显示邀请码 + 重新生成；成员 + 知识库 + 设置三 Tab，设置中只能管理邀请码
4. MEMBER 视角：信息卡片不显示邀请码；只有成员 + 知识库两个 Tab
5. 非成员访问 → 提示"不是群组成员"+ 返回群组列表
6. 成员列表按积分降序，前三名有奖牌图标
7. OWNER 通过「更多」提升成员为 ADMIN → 角色标签更新
8. OWNER 通过「更多」转让群主 → 二次确认 → 转让成功，原 OWNER 变为 ADMIN
9. ADMIN 通过「更多」踢出 MEMBER → 二次确认 → 踢出成功
10. OWNER 编辑群组信息（名称/描述/头像/加入方式）→ 提交成功 → 信息卡片刷新
11. OWNER 重新生成邀请码 → 邀请码更新显示
12. ADMIN 重新生成邀请码 → 邀请码更新显示
13. OWNER 解散群组 → 输入群组名确认 → 跳转回群组列表
14. 知识库 Tab 显示分类统计 → 点击分类 → 题目列表分页 → 展开题目详情
15. 点击「开始游戏」→（当前可 toast 占位，后续对接 REQ-31）
