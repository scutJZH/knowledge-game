# REQ-60 群组列表 + 创建/加入页面

## 产品定位

用户端群组列表页面 + 创建群组 + 加入群组。用户登录后在群组页面查看已加入的群组、创建新群组、凭邀请码或公开加入他人群组。本需求同时补齐前端缺失的「我的群组列表」后端 API。

## 用户故事

**作为** 普通用户
**我想要** 查看我已加入的群组列表、创建新群组、凭邀请码加入他人群组
**以便于** 进入各群组参与知识记忆玩法

## 前置依赖

| 需求 | 状态 | 依赖内容 |
|------|------|---------|
| REQ-26 | done | 前端项目搭建（React + Vite + 路由 + MainLayout） |
| REQ-27 | done | Axios 封装 + 认证状态管理（apiClient、AuthGuard、authStore） |
| REQ-48 | done | 创建群组 API（POST /api/study-groups）+ StudyGroupRepository 出端口 |
| REQ-49 | done | 成员管理 API（joinByInvite / leave / getCurrentMember）+ GroupMemberRepository 出端口 |

> 注：user 端当前**无任何带 API 调用 + 状态的列表页面**（pages 目录仅含 Home/Login/Register/ForgotPassword/NotFound），本需求需从零搭建首个列表页。user 端**无 ImageUploadField 组件**（该组件仅存在于 admin 端，REQ-90 产物），本需求需为 user 端新建简易头像上传逻辑。

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 页面结构 | 单页聚合（列表 + 双按钮） | 三个动作（查看/创建/加入）关联紧密，单页减少跳转 |
| 创建表单 | Modal 居中弹窗 | 字段少（3 个），不需要整页跳转 |
| 加入邀请码 | 单输入框 + 自动大写 | 8 格输入框移动端体验差，单输入框支持粘贴 + 自动转大写 |
| 头像上传 | 简易上传：`<input type="file">` + 手动调 credential API + fetch 直传文件服务，封装为 `AvatarUpload` 小组件 | user 端无 ImageUploadField，不走跨端复用（admin 端 Pro 生态与本端差异大）。上传流程见下方凭证式上传小节 |
| 后端列表 API | `GET /api/study-groups` 返回已加入群组列表 | REQ-49 PRD 已标记此 API 为 REQ-60 前端依赖 |
| API 返回结构 | 每项含群组信息 + 成员数 + 我的角色 | 前端一次性渲染卡片所需全部数据 |
| 列表分页 | 首版无分页 | 用户加入群组数有限（通常 < 20），数量 > 50 时再加 |
| 列表加载 | 进入页面即加载 | 首次渲染发请求，Spin 占位 |
| 空状态 | 引导式空状态（图标 + 文案 + 双按钮） | 无群组时直接提供创建/加入入口 |
| 路由位置 | `/groups`，MainLayout 内，需登录 | AuthGuard 保护 |

## 功能需求

### 前端页面

#### 路由

- 路径：`/groups`
- 挂载在 `AuthGuard → MainLayout` 下
- MainLayout 顶部菜单新增"群组"入口（`key: '/groups', label: '群组'`）

#### 群组列表

- 进入页面调 `GET /api/study-groups` 加载列表
- 加载中显示 Spin
- 加载失败显示 antd Result（error status + 重试按钮）
- 列表为空显示空状态引导（图标 + "还没有加入群组" + 创建/加入双按钮）
- 有数据时渲染群组卡片列表

**群组卡片：**
- 左侧：头像（有自定义图显示图片，无图显示首字渐变方块）
- 中间：群组名称（粗体）+ 成员数 + 角色中文名
- 右侧：角色标签（OWNER 紫色 / ADMIN 蓝色 / MEMBER 灰色）+ 箭头 `›`
- 点击卡片 → `navigate('/groups/' + id)`（REQ-61 群组详情页）

#### 创建群组 Modal

- 点击"创建群组"按钮打开 Modal
- 表单字段：
  - 群组名称：必填，Input，max 50
  - 描述：选填，Input.TextArea，max 500
  - 头像：选填，`AvatarUpload` 组件（本需求新建，见下方凭证式上传流程）
  - 加入方式：Radio 或分段按钮，OPEN（默认）/ INVITE_ONLY
- 提交 → 组装 `CreateStudyGroupRequest`（name + description + joinPolicy + 已上传 fileId）
- 成功后关闭 Modal，message.success，刷新列表
- 失败后 message.error，Modal 保持打开，表单保留输入值

**凭证式头像上传流程（`AvatarUpload` 组件）：**

本需求在 user 端新建简易头像上传逻辑（admin 端 ImageUploadField 基于 Ant Design Pro 生态，不跨端复用）。

```
1. 用户点击头像区域 → <input type="file" accept="image/*"> 选择图片
2. 调 GET /api/upload-credential?bizType=STUDY_GROUP_AVATAR&count=1 获取上传凭证
   （此接口已存在，凭证由后端 FilePathMapping 按 bizType 组装，前端零感知）
3. 用凭证直传文件服务（FormData + fetch）
4. 文件服务返回 fileId → AvatarUpload 组件 onChange(fileId: number) 通知父表单
5. 父表单记录 avatarFileId，Modal 提交时传入 CreateStudyGroupRequest
```

组件接口（`AvatarUpload`）：
- Props：`value?: number`（当前 fileId）、`onChange?: (fileId: number) => void`
- 展示：选中后显示缩略图预览（从 `GET /api/file/{fileId}?action=download` 加载），未选时显示虚线框 + "点击上传头像"
- 上传中显示 Spin overlay，失败 message.error

> 参考：后端文件上传约定见 CLAUDE.md "Image Field Design Rules"；凭证接口见 REQ-87。

#### 加入群组 Modal

- 点击"加入群组"按钮打开 Modal
- 单输入框：placeholder "8 位邀请码"，自动转大写（`onChange` 中 `toUpperCase()`）
- 输入长度判断：< 8 位时按钮 disabled
- 提交 → `POST /api/study-groups/join-by-invite`
- 成功后关闭 Modal，message.success，刷新列表
- 失败后 message.error（INVITE_CODE_INVALID / ALREADY_GROUP_MEMBER）
- 提供直接加入 OPEN 群组的副入口：如用户有 OPEN 群组链接，也可调 `POST /api/study-groups/{id}/members`（不在本需求首版前端 UI 中暴露，后续迭代再加"发现公开群组"）

### 后端 API

#### GET /api/study-groups — 我的群组列表

**Controller：** 新增方法在 `StudyGroupController`（或新建 `StudyGroupQueryController`）

**请求：** `GET /api/study-groups`（无参数，从 JWT 提取 userId）

**响应：**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "Java 进阶小组",
      "description": "...",
      "avatarFileId": 123,
      "avatarUrl": "https://...",
      "ownerId": 100,
      "joinPolicy": "OPEN",
      "myRole": "OWNER",
      "memberCount": 12,
      "createdAt": 1718800000000,
      "updatedAt": 1718800000000
    }
  ]
}
```

**字段说明：**
- 复用 `StudyGroupResponse` 已有字段（id/name/description/avatarFileId/avatarUrl/ownerId/joinPolicy/createdAt/updatedAt）
- **新增字段 `myRole`**（String）：当前用户在该群组的角色（OWNER/ADMIN/MEMBER）
- **新增字段 `memberCount`**（int）：群组成员总数
- 不返回 inviteCode（邀请码仅 OWNER 在详情页可见，REQ-61 范围）

**业务流程：**

```
1. userId = SecurityUtils.getCurrentUserId()
2. members = groupMemberRepository.findByUserIdOrderByJoinedAtDesc(userId)
3. if empty → return []
4. groupIds = members.map(GroupMember::getGroupId)
5. groups = studyGroupRepository.findByIdIn(groupIds)
6. groupMemberCounts = groupMemberRepository.countByGroupIdIn(groupIds)
   // JPA @Query 返回 List<Object[]> → adapter 手动组装为 Map<Long, Integer>
   // @Query("SELECT gm.groupId, COUNT(gm) FROM GroupMemberPO gm WHERE gm.groupId IN :ids GROUP BY gm.groupId")
7. 组装 DTO：
   - 遍历 members（已按 joinedAt DESC 排序）
   - 对每个 member，从 groups map 取 StudyGroup，从 counts map 取 memberCount
   - Assembler.toListResponse(StudyGroup, myRole, memberCount) 逐条转换
8. 返回 StudyGroupListResponse 列表
```

#### 出端口扩展

**GroupMemberRepository（core/domain/port/outbound/）：**
- `findByUserIdOrderByJoinedAtDesc(Long userId): List<GroupMember>` — **新增**，查询用户所有成员记录（按加入时间降序）
- `countByGroupIdIn(List<Long> groupIds): Map<Long, Integer>` — **新增**，批量查成员数（adapter 内部用 `@Query` 返回 `List<Object[]>` 后手动组装 Map，JPA 不会直接返回 Map）

**StudyGroupRepository（core/domain/port/outbound/）：**
- `findByIdIn(List<Long> ids): List<StudyGroup>` — **新增**，批量查群组

#### DTO 层

新建 `StudyGroupListResponse`（`app/api/dto/StudyGroupListResponse.java`），字段表：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| id | Long | StudyGroup.id | 群组 ID |
| name | String | StudyGroup.name | 名称 |
| description | String | StudyGroup.description | 描述（可为 null） |
| avatarFileId | Long | StudyGroup.avatar.fileId | 头像文件 ID（可为 null） |
| avatarUrl | String | StudyGroup.avatar.url | 头像 URL（可为 null） |
| ownerId | Long | StudyGroup.ownerId | 创建人 ID |
| joinPolicy | String | StudyGroup.joinPolicy | OPEN / INVITE_ONLY |
| myRole | String | GroupMember.role | 当前用户角色：OWNER / ADMIN / MEMBER |
| memberCount | int | countByGroupIdIn 结果 | 群组成员总数 |
| createdAt | Long | StudyGroup.createdAt | 创建时间（epoch 毫秒） |
| updatedAt | Long | StudyGroup.updatedAt | 更新时间（epoch 毫秒） |

共 11 个字段。不包含 inviteCode（邀请码仅 OWNER 在群组详情页可见，REQ-61 范围）。

## UI 设计要点

| 元素 | 规格 |
|------|------|
| 卡片头像 | 44×44，圆角 12px，无自定义图时渐变背景 + 群组名首字（白色粗体） |
| 卡片背景 | 白色，圆角 12px，阴影 `0 1px 3px rgba(0,0,0,0.06)` |
| 卡片间距 | 10px |
| 角色标签 | OWNER 紫底紫字、ADMIN 蓝底蓝字、MEMBER 灰底灰字，圆角 10px |
| 创建按钮 | 全宽渐变紫（#8b5cf6 → #a78bfa），白色文字 |
| 加入按钮 | 全宽白底紫边框，紫色文字 |
| 空状态图标 | emoji 🏠，56px |
| Modal 宽度 | 约 330px（移动端自适应） |
| 邀请码输入框 | 居中，等宽字体，letter-spacing 6px，font-size 20px |

## 错误处理

| 场景 | 处理方式 |
|------|---------|
| 列表加载失败 | Result error + 重试按钮 |
| 创建群组 name 空 | 表单校验（@NotBlank），antd Form 内置提示 |
| 创建群组 name 超长 | 表单校验（@Size max=50） |
| 创建群组 description 超长 | 表单校验（@Size max=500） |
| 创建群组 fileId 无效 | message.error 显示后端错误消息 |
| 加入群组邀请码 < 8 位 | 按钮 disabled，不发送请求 |
| 加入群组邀请码无效 | message.error("邀请码无效") |
| 加入群组已是成员 | message.error("已是群组成员") |

## 前端文件结构

```
frontend/user/src/
├── pages/GroupList/
│   ├── index.tsx              ← 主页面组件
│   ├── GroupList.css          ← 样式
│   ├── GroupCard.tsx          ← 群组卡片组件
│   ├── CreateGroupModal.tsx   ← 创建群组 Modal
│   ├── JoinGroupModal.tsx     ← 加入群组 Modal
│   ├── AvatarUpload.tsx       ← 简易头像上传组件（新建，user 端首版）
│   └── __tests__/
│       ├── index.test.tsx
│       ├── GroupCard.test.tsx
│       ├── AvatarUpload.test.tsx
│       ├── CreateGroupModal.test.tsx
│       └── JoinGroupModal.test.tsx
├── services/group-api.ts      ← study group API 调用
├── types/group.ts             ← TS 类型定义
├── routes/index.tsx           ← 修改：加 /groups 路由
└── layouts/MainLayout.tsx     ← 修改：菜单加"群组"入口
```

## 后端文件结构

```
backend/knowledge-game-app/src/main/java/com/knowledgegame/app/
├── api/
│   ├── controller/
│   │   └── StudyGroupController.java          ← 修改：加 listMyGroups 方法
│   └── dto/
│       └── StudyGroupListResponse.java        ← 新增
└── application/
    ├── service/
    │   └── StudyGroupAppService.java           ← 修改：加 listMyGroups 方法
    └── assembler/
        └── StudyGroupAssembler.java            ← 修改：加 toListResponse

backend/knowledge-game-core/src/main/java/com/knowledgegame/core/
├── domain/port/outbound/
│   ├── StudyGroupRepository.java              ← 修改：加 findByIdIn
│   └── GroupMemberRepository.java             ← 修改：加 findByUserId + countByGroupIdIn
└── infrastructure/
    ├── db/repository/
    │   ├── StudyGroupJpaRepository.java       ← 修改：加 findByIdIn
    │   └── GroupMemberJpaRepository.java      ← 修改：加 findByUserId + countByGroupIdIn
    └── adapter/repoadapter/
        ├── StudyGroupRepositoryAdapter.java   ← 修改：实现 findByIdIn
        └── GroupMemberRepositoryAdapter.java  ← 修改：实现 findByUserId + countByGroupIdIn
```

## Verification Plan

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|----------|
| **前端组件测试（vitest + @testing-library/react）** | 列表加载/空状态/错误态；创建 Modal 表单校验 + 提交成功/失败；加入 Modal 邀请码输入 + 禁用逻辑 + 提交成功/失败；卡片点击跳转 |
| **前端 TS 类型检查** | `npx tsc --noEmit` 确保类型对齐后端 DTO |
| **AppService 单测（Mockito）** | listMyGroups：有群组 / 无群组 / 成员数和角色正确映射 |
| **Controller @WebMvcTest** | GET /api/study-groups 返回正确 JSON 结构（含 myRole + memberCount） |
| **Repository 集成测试（@DataJpaTest 真实 MySQL）** | findByUserId / findByIdIn / countByGroupIdIn 正确性 |

### 手工验收

1. 用户未加入任何群组 → 访问 /groups → 显示空状态引导
2. 点击"创建群组" → Modal 弹出 → 填名称 + 选择加入方式 → 提交成功 → 列表刷新出现新群组
3. 点击"加入群组" → Modal 弹出 → 输入邀请码 → 加入成功 → 列表刷新
4. 输入 < 8 位邀请码 → 按钮 disabled
5. 输入无效邀请码 → message.error 提示
6. 重复加入同一群组 → message.error 提示
7. **快速双击"加入"按钮** → 后端 DataIntegrityViolationException 兜底，仅加入一次，不报错（message.success 一次）
8. 点击群组卡片 → 跳转 /groups/:id（REQ-61）
9. 顶部菜单"群组"高亮当前路由

### 前端测试基础设施

- user 端测试使用 vitest + @testing-library/react + jsdom
- **jsdom 环境必备 mock**：`window.matchMedia`、`window.getComputedStyle`（Ant Design 组件依赖）
