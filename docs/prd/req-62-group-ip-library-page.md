# REQ-62 群组管理端 — IP 库关联页

## 产品定位

用户端群组详情页新增「IP 库」Tab，群组管理员（OWNER/ADMIN）通过 Checkbox 卡片网格勾选系统已启用的 IP 系列关联到本群组，群组成员（MEMBER）只读浏览。本需求消费 REQ-51 已实现的后端 API，并新增用户端「可选 IP 列表」查询接口作为前端勾选网格数据源。

知识库全局共享，群组维度的 IP 关联仅决定「群组成员可玩哪些 IP」，无授权语义。

## 前置依赖

| 编号 | 需求名称 | 状态 | 依赖内容 |
|------|---------|------|---------|
| REQ-26 | 用户端前端项目搭建 | done | React + Vite + 路由 + MainLayout |
| REQ-51 | 群组 — 关联 IP 库 API | done | GET/PUT `/api/study-groups/{id}/ip-library` + `GroupIpLibrary` 聚合根 + 5 条错误码（GROUP_NOT_FOUND / NOT_GROUP_MEMBER / NOT_GROUP_ADMIN / IP_SERIES_NOT_FOUND / IP_SERIES_NOT_ACTIVE）|
| REQ-61 | 群组详情 + 管理页面 | done | `/groups/:id` 页面 + Tabs（成员/知识库/设置）+ `StudyGroupDetailResponse.myRole` 字段 |

> 三个前置均已完成（done），本需求可立即开发。

## 用户故事

**作为** 群组管理员（OWNER 或 ADMIN）
**我想要** 在群组详情页通过卡片网格勾选系统已启用的 IP 系列
**以便于** 配置群组成员可玩的 IP 范围

**作为** 群组成员（MEMBER）
**我想要** 浏览本群组已关联的 IP 列表
**以便于** 了解可玩的 IP 范围

## 设计决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 入口位置 | 新增第 4 个 Tab「IP 库」 | 与「成员/知识库/设置」并列。IP 关联是群组核心配置之一，且 MEMBER 也能浏览（符合 REQ-51 GET 任意成员可调） |
| 编辑交互 | Checkbox 卡片网格（封面图+名称+编码） | 充分展示 IP 信息，原生交互，状态明确。系统 IP 数量预期"数十个"，网格可直接铺开无需分页 |
| MEMBER 可见性 | Tab 可见，Checkbox disabled | 与 REQ-51 GET「任意成员可查询」语义对齐。MEMBER 浏览群组可玩 IP 范围是合理需求 |
| 可选 IP 数据源 | 新增 GET `/api/ip-series`（仅返回 ACTIVE） | 复用 core `IpSeriesRepositoryPort`，职责单一，未来抽卡页等场景可复用。不侵入 REQ-51 已实现接口 |
| INACTIVE IP 处理 | 网格不展示 | PUT 全量替换语义下，前端不传 INACTIVE ID 即可；后端 IP_SERIES_NOT_ACTIVE 校验是兜底防御 |
| 保存时机 | 显式「保存修改」按钮（非即时保存） | REQ-51 PUT 是全量替换，每次勾选都 PUT 浪费请求；批量保存降低网络与服务端负载 |
| 数据加载时机 | 进入 Tab 才加载 | 与 MemberTab 一致，避免 GroupDetail 首次加载就发额外请求 |
| 网格默认排序 | `findByStatusOrderByIdAsc`（ID 升序） | IpSeries 无 `sortOrder` 字段（仅 KnowledgeCategory 有），ID 升序 = 系统添加 IP 的录入顺序，业务语义稳定。不选 name 是因为中英混合时 Unicode 码点排序对用户认知不友好；不选 code 是因为 code 是管理员内部标识，群组成员认知度低 |
| 离开未保存提示 | 不做 | 重进 Tab 即恢复，丢失成本极低，YAGNI |
| IP 搜索/分页 | 不做 | 系统预期"数十个" IP，YAGNI |

## 页面结构

```
/groups/:id
┌─────────────────────────────────┐
│  群组信息卡片                    │
├─────────────────────────────────┤
│  [成员] [知识库] [IP 库] [设置*] │  ← IP 库 全员可见；设置仅 OWNER+ADMIN
│  (Tab 内容区)                    │
└─────────────────────────────────┘

IP 库 Tab 内容
┌─────────────────────────────────┐
│  已关联 X 项      [添加 IP 系列]  │  ← OWNER/ADMIN 可见添加按钮
├─────────────────────────────────┤
│  ┌─────┐ ┌─────┐ ┌─────┐         │
│  │  ⋮  │ │  ⋮  │ │  ⋮  │         │  ← ⋮ 菜单：禁用 / 删除
│  │封面 │ │封面 │ │封面 │         │
│  │宝可梦│ │原神 │ │鬼灭 │         │
│  │POKE │ │GENSH│ │DEMOM│         │
│  └─────┘ └─────┘ └─────┘         │
│                                   │
│  [添加弹窗]                        │
│  ┌─────────────────────────────┐  │
│  │ 🔍 搜索 IP 名称或编码        │  │
│  │ ☑ 系列A  ☐ 系列B  ☐ 系列C   │  │
│  │          [取消] [确定]       │  │
│  └─────────────────────────────┘  │
└─────────────────────────────────┘
```

## 功能需求

### 一、IpLibraryTab 组件

#### 角色权限矩阵

| 角色 | Tab 可见 | 添加按钮可见 | ⋮ 操作菜单可见 |
|------|---------|------------|--------------|
| OWNER | ✓ | ✓ | ✓ |
| ADMIN | ✓ | ✓ | ✓ |
| MEMBER | ✓ | ✗ | ✗ |

#### 数据加载

`useEffect` 中并发拉取两个接口：

```ts
Promise.all([
  listGroupIpLibrary(groupId),     // 已关联 IP（GET /api/study-groups/:id/ip-library）
  listActiveIpSeries(),            // 全部可选 IP（GET /api/ip-series，供添加弹窗用）
])
```

- 卡片网格仅展示「已关联」IP 列表，每张卡片右上角 ⋮ 操作菜单
- 添加弹窗展示「可选」IP 列表（已关联的排除），支持搜索过滤 + 多选
- 已关联 ID 与可选 ID 求交集，过滤孤儿 ID（已停用但仍关联的 IP）

#### 单张卡片视觉

```
┌──────────────────┐
│                ⋮ │ ← 操作菜单（禁用/删除），仅 OWNER/ADMIN 可见
│   [封面图 96×96]  │
│  宝可梦           │ ← 名称（粗体）
│  POKEMON         │ ← 编码（monospace，灰色）
└──────────────────┘
```

- 封面图为空：显示首字占位符（参考 `GroupCard` 模式：`name.charAt(0)` + `avatar-placeholder` CSS 类）
- MEMBER 视角：无 ⋮ 菜单，纯浏览

#### 添加弹窗

- 搜索框实时过滤（按名称或编码）
- 多选列表（Checkbox + 封面缩略图 + 名称 + 编码）
- 确认后合并当前 ID + 新选 ID → PUT 全量替换

#### 状态分支

| 场景 | 处理 |
|------|------|
| 加载中 | `<Spin />` 居中 |
| 加载失败 | `<Result status="error" />` + 重试按钮 |
| 已关联列表为空 | 空状态："暂未关联 IP 系列" |
| 添加弹窗可选为空 | 提示"无可用 IP 系列" |
| 确定按钮 disabled | 当且仅当未勾选任何 IP |
| 保存中 | 按钮 loading，防并发点击 |
| 操作成功 | `message.success`，用 PUT 响应更新已关联列表 |
| 操作失败 | `message.error(e.response.data.message)` |

#### 关键交互

1. **进入 Tab 才加载数据**：与 MemberTab 一致
2. **添加/删除均为全量替换**：前端构造完整 ID 列表，PUT 提交
3. **MEMBER 只读**：不渲染添加按钮和 ⋮ 菜单
4. **myRole 来源**：从父组件 `StudyGroupDetailResponse.myRole` 透传

### 二、前端文件结构

```
frontend/user/src/
├── pages/GroupDetail/
│   ├── index.tsx                  ← 修改：Tabs 数组插入第 4 项
│   ├── IpLibraryTab.tsx           ← 新增
│   └── __tests__/
│       └── IpLibraryTab.test.tsx  ← 新增
├── services/group-api.ts          ← 修改：加 3 个方法
└── types/group.ts                 ← 修改：加 2 个 interface
```

### 三、后端 API

#### 新增：GET `/api/ip-series` — 查询所有 ACTIVE 的 IP 系列

需 JWT 鉴权（`/api/**` 前缀自动拦截），无需 ADMIN 角色。

**响应体：**

```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "宝可梦",
      "code": "POKEMON",
      "coverImageFileId": 10,
      "coverImageUrl": "https://..."
    },
    {
      "id": 2,
      "name": "原神",
      "code": "GENSHIN",
      "coverImageFileId": null,
      "coverImageUrl": null
    }
  ]
}
```

**说明：**
- 仅返回 ACTIVE 的 IP 系列
- 字段精简：仅 `id/name/code/coverImageFileId/coverImageUrl`，不暴露 description/status/sortOrder/createdAt 等管理元数据
- 不分页（系统 IP 数量预期"数十个"）
- 无需新增 ResultCode

**Controller：**

```java
@RestController
@RequestMapping("/api")
public class IpSeriesController {
    private final IpSeriesAppService appService;

    public IpSeriesController(IpSeriesAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/ip-series")
    public Result<List<ActiveIpSeriesResponse>> listActive() {
        return Result.success(appService.listActive());
    }
}
```

**AppService：**

```java
@Service
public class IpSeriesAppService {
    private final IpSeriesRepositoryPort ipSeriesRepository;

    public IpSeriesAppService(IpSeriesRepositoryPort ipSeriesRepository) {
        this.ipSeriesRepository = ipSeriesRepository;
    }

    @Transactional(readOnly = true)
    public List<ActiveIpSeriesResponse> listActive() {
        return ipSeriesRepository.findAllActive().stream()
                .map(IpSeriesAssembler.INSTANCE::toActiveResponse)
                .toList();
    }
}
```

> 已 Grep 验证：`IpSeriesRepositoryPort` / `IpSeriesJpaRepository` 当前均无 `findAllActive` / `findByStatusOrderByIdAsc`（admin 端复用同一 Port，亦无）。本 PRD 在 Port + Adapter + JpaRepo 三层新增 `findByStatusOrderByIdAsc(IpSeriesStatus status)` 派生方法。

**Assembler（MapStruct）：**

> FileRef 用 `fileId()` / `url()` 访问器（非 JavaBean getter），`@Mapping(source = "coverImage.fileId")` MapStruct 无法识别会编译失败。复用 admin `IpSeriesAssembler` 已验证模式：`expression = "java(...)"` + default null-safe helper。

```java
@Mapper
public interface IpSeriesAssembler {
    IpSeriesAssembler INSTANCE = Mappers.getMapper(IpSeriesAssembler.class);

    @Mapping(target = "coverImageFileId", expression = "java(fileIdOf(ipSeries.getCoverImage()))")
    @Mapping(target = "coverImageUrl", expression = "java(urlOf(ipSeries.getCoverImage()))")
    ActiveIpSeriesResponse toActiveResponse(IpSeries ipSeries);

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
```

**DTO：**

```java
public class ActiveIpSeriesResponse {
    private Long id;
    private String name;
    private String code;
    private Long coverImageFileId;
    private String coverImageUrl;
    // getter/setter
}
```

#### 已有：GET/PUT `/api/study-groups/{id}/ip-library`（REQ-51 实现，本需求零改动）

前端补 wiring 即可，错误码沿用 REQ-51 的 5 条。

### 四、前端 TS 类型 + services

**types/group.ts 新增：**

```typescript
/** 群组已关联 IP 列表项（对应后端 GroupIpLibraryResponse） */
export interface GroupIpLibraryResponse {
  id: number;
  groupId: number;
  ipSeriesId: number;
  ipSeriesName: string;
  ipSeriesCode: string;
  coverImageFileId: number | null;
  coverImageUrl: string | null;
  addedAt: number;
}

/** 可选 IP 系列精简信息（对应后端 ActiveIpSeriesResponse） */
export interface ActiveIpSeriesResponse {
  id: number;
  name: string;
  code: string;
  coverImageFileId: number | null;
  coverImageUrl: string | null;
}
```

**services/group-api.ts 新增：**

```typescript
import type { GroupIpLibraryResponse, ActiveIpSeriesResponse } from '@/types/group';

/** 查询群组已关联的 IP 列表 */
export function listGroupIpLibrary(groupId: number) {
  return apiClient.get<never, GroupIpLibraryResponse[]>(`/study-groups/${groupId}/ip-library`);
}

/** 批量设置群组 IP 库关联 */
export function updateGroupIpLibrary(groupId: number, ipSeriesIds: number[]) {
  return apiClient.put<never, GroupIpLibraryResponse[]>(
    `/study-groups/${groupId}/ip-library`,
    { ipSeriesIds },
  );
}

/** 查询所有启用的 IP 系列（前端勾选网格数据源） */
export function listActiveIpSeries() {
  return apiClient.get<never, ActiveIpSeriesResponse[]>('/ip-series');
}
```

### 五、GroupDetail/index.tsx 修改

```tsx
// 新增 import
import IpLibraryTab from './IpLibraryTab';

// tabItems 数组在「知识库」与「设置」之间插入：
const tabItems = [
  { key: 'members', label: '成员', children: <MemberTab ... /> },
  { key: 'knowledge', label: '知识库', children: <KnowledgeTab /> },
  { key: 'ip-library', label: 'IP 库', children: <IpLibraryTab groupId={group.id} myRole={group.myRole} /> },
  { key: 'settings', label: '设置', children: <SettingsTab ... /> },
];
```

### 六、错误处理

| 场景 | 处理 |
|------|------|
| Tab 加载失败（GET ip-library 或 GET /api/ip-series 任一失败） | `<Result status="error">` + 重试按钮 |
| 群组不存在（GET ip-library 返回 GROUP_NOT_FOUND） | 与 GroupDetail 主页面错误一致（已经在父级校验过群组存在，本场景极少触发） |
| 非群组成员（GET ip-library 返回 NOT_GROUP_MEMBER） | 同上，由父级 GroupDetail 页统一拦截 |
| 非 OWNER/ADMIN 调 PUT（NOT_GROUP_ADMIN） | `message.error('仅群主或管理员可操作')` |
| 传不存在的 IP（IP_SERIES_NOT_FOUND） | 理论不可触发（前端只展示 ACTIVE IP）。兜底：`message.error(e.response.data.message)` |
| 传 INACTIVE IP（IP_SERIES_NOT_ACTIVE） | 同上，兜底提示 |
| 网络异常 | `message.error(e.response?.data?.message || '保存失败')`，selectedIds 保留 |

## 后端文件结构

```
backend/knowledge-game-app/src/main/java/com/knowledgegame/app/
├── api/
│   ├── controller/
│   │   └── IpSeriesController.java          ← 新增
│   ├── dto/
│   │   └── response/
│   │       └── ActiveIpSeriesResponse.java  ← 新增
│   └── assembler/
│       └── IpSeriesAssembler.java           ← 新增
└── application/
    └── service/
        └── IpSeriesAppService.java          ← 新增

backend/knowledge-game-app/src/test/java/com/knowledgegame/app/
├── api/controller/IpSeriesControllerTest.java       ← 新增
├── application/service/IpSeriesAppServiceTest.java  ← 新增
└── api/assembler/IpSeriesAssemblerTest.java         ← 新增

backend/knowledge-game-core/src/test/java/com/knowledgegame/core/infrastructure/adapter/repoadapter/
└── IpSeriesRepositoryAdapterIntegrationTest.java    ← 新增（@DataJpaTest，参考同目录 KnowledgeItemRepositoryAdapterIntegrationTest）
```

> Adapter 集成测试放 core 模块（与被测 Adapter 同模块），不与已有的 `IpSeriesRepositoryAdapterTest.java`（Mockito 单测，line 139）冲突。项目命名约定：Mockito 单测用 `XxxTest`，@DataJpaTest 用 `XxxIntegrationTest`。

### core 模块改动

已 Grep 验证：`IpSeriesRepositoryPort` / `IpSeriesJpaRepository` 当前均无 `findAllActive` / `findByStatusOrderByIdAsc`，本 PRD 在 core 模块新增以下三层：

```java
// core/domain/port/outbound/IpSeriesRepositoryPort.java
List<IpSeries> findAllActive();

// core/infrastructure/db/repository/IpSeriesJpaRepository.java
List<IpSeriesPO> findByStatusOrderByIdAsc(IpSeriesStatus status);

// core/infrastructure/adapter/repoadapter/IpSeriesRepositoryAdapter.java
@Override
public List<IpSeries> findAllActive() {
    return jpaRepository.findByStatusOrderByIdAsc(IpSeriesStatus.ACTIVE).stream()
            .map(converter::toDomain)
            .toList();
}
```

> 实施时 Grep 确认无误：core 端 `IpSeriesRepositoryPort` 与 admin 端均无 `findAllActive`，本 PRD 新增。

## 验证计划

### 自动化测试

| 测试类型 | 覆盖内容 |
|----------|---------|
| **后端 Controller 单测（@WebMvcTest）** | `IpSeriesControllerTest`：`@WebMvcTest(controllers = IpSeriesController.class) + @AutoConfigureMockMvc(addFilters = false) + @Import({GlobalExceptionHandler.class, JacksonConfig.class}) + @MockitoBean IpSeriesAppService`，参照同模块 `StudyGroupControllerTest:39-41`。3 个用例：① listActive 委托 AppService ② 响应体 code=0 + data 非空 ③ Jackson 序列化验证 coverImageFileId=null 字段在 JSON 中显式存在。**注**：CLAUDE.md @EnableScheduling→@WebMvcTest pitfall 是条件性的，仅当主类含 @EnableXxx 时触发；app 模块主类 `KnowledgeGameApplication` 仅 @SpringBootApplication，限制不适用 |
| **后端 AppService 单测（Mockito）** | `IpSeriesAppServiceTest`：mock `IpSeriesRepository.findAllActive` 返回**仅 ACTIVE 的列表**（含 null coverImage 的 IP）→ 验证字段映射正确（无 description/status 等管理字段透传到 DTO）。**不验证 INACTIVE 过滤**（端口契约 `findAllActive` 语义已保证，过滤逻辑属 Adapter 层职责） |
| **后端 Adapter 集成测试（@DataJpaTest 走真实 MySQL）** | `IpSeriesRepositoryAdapterIntegrationTest`（core 模块，参考 `KnowledgeItemRepositoryAdapterIntegrationTest`）：`@DataJpaTest + @AutoConfigureTestDatabase(replace=NONE) + @ActiveProfiles("test") + @EntityScan + @EnableJpaRepositories + @Import(IpSeriesRepositoryAdapter.class)`。验证 `findAllActive()`：① 仅返回 status=ACTIVE 的记录（INACTIVE 不出现）② 按 ID 升序排列 ③ 空表返回空 List |
| **后端 Assembler 单测** | `IpSeriesAssemblerTest`：IpSeries → ActiveIpSeriesResponse 字段映射正确，coverImage (FileRef) → coverImageFileId + coverImageUrl 解包正确，null coverImage 时两字段均为 null |
| **前端组件测试（vitest + @testing-library/react）** | `IpLibraryTab.test.tsx`：<br>① OWNER 渲染：勾选已关联 IP、Checkbox 可点击<br>② ADMIN 渲染：同 OWNER<br>③ MEMBER 渲染：Checkbox disabled、保存按钮不渲染<br>④ 勾选新 IP → 保存按钮 enabled → 点击 → PUT 调用参数正确 → 成功后 selectedIds 用响应重置<br>⑤ 取消已勾选 → PUT 调用参数排除该 ID<br>⑥ 无修改时保存按钮 disabled<br>⑦ 加载中 Spin、加载失败 Result error、空状态文案<br>⑧ Promise.all 中 listGroupIpLibrary 或 listActiveIpSeries 失败 → error 展示<br>⑨ 封面图为空时渲染首字占位符（复用 GroupCard 模式） |
| **前端 TS 类型检查** | `npx tsc --noEmit` 确保类型对齐后端 DTO |

> 注：REQ-51 后端测试已覆盖 GET/PUT ip-library 路径，本需求不重复。

### 手工验收

1. OWNER 进入 `/groups/:id`，点击「IP 库」Tab → 网格展示所有 ACTIVE IP，已关联的勾选状态正确
2. OWNER 勾选新 IP（如宝可梦）→ 保存按钮变 enabled → 点击保存 → toast「IP 库已更新」→ 网格状态保持
3. OWNER 取消已关联 IP → 保存 → 列表更新；切到「成员」Tab 再切回 → 状态正确恢复
4. ADMIN 视角同 OWNER（确认 ADMIN 可操作）
5. MEMBER 视角：Tab 可见、Checkbox disabled、保存按钮不渲染 → 无法修改
6. 切换 Tab 后再回来 → 数据正确重新加载（不残留旧 state）
7. 网络异常（断网）点保存 → toast 提示后端错误消息 → 本地勾选状态保留，可重试
8. 系统中无 ACTIVE IP（admin 把所有 IP 停用）→ 空状态文案「系统中暂无可用 IP 系列」
9. 群组无任何关联且 IP 列表非空 → 所有 Checkbox 未勾选，提示「点击卡片选择 IP」
10. 并发：保存中按钮 loading，二次点击不触发第二次 PUT

## 影响分析

### 修改文件

| 文件 | 改动 |
|------|------|
| `frontend/user/src/pages/GroupDetail/index.tsx` | Tabs 数组插入第 4 项「IP 库」 |
| `frontend/user/src/services/group-api.ts` | 新增 3 个方法：`listGroupIpLibrary` / `updateGroupIpLibrary` / `listActiveIpSeries` |
| `frontend/user/src/types/group.ts` | 新增 2 个 interface：`GroupIpLibraryResponse` / `ActiveIpSeriesResponse` |
| `backend/.../core/domain/port/outbound/IpSeriesRepositoryPort.java` | 新增 `findAllActive()` 方法签名 |
| `backend/.../core/infrastructure/db/repository/IpSeriesJpaRepository.java` | 新增 `findByStatusOrderByIdAsc(IpSeriesStatus status)` 派生查询 |
| `backend/.../core/infrastructure/adapter/repoadapter/IpSeriesRepositoryAdapter.java` | 实现 `findAllActive()`：调用 JpaRepo 派生查询 + Converter 转 domain |

### 新增文件

| 文件 | 说明 |
|------|------|
| `frontend/user/src/pages/GroupDetail/IpLibraryTab.tsx` | Tab 组件 |
| `frontend/user/src/pages/GroupDetail/__tests__/IpLibraryTab.test.tsx` | 组件单测 |
| `backend/.../app/api/controller/IpSeriesController.java` | GET /api/ip-series |
| `backend/.../app/api/dto/response/ActiveIpSeriesResponse.java` | 响应 DTO（精简字段） |
| `backend/.../app/api/assembler/IpSeriesAssembler.java` | IpSeries → ActiveIpSeriesResponse |
| `backend/.../app/application/service/IpSeriesAppService.java` | listActive() 方法 |
| 4 个后端测试文件 | app 模块 3 个单测（Controller/AppService/Assembler）+ core 模块 1 个 Adapter 集成测试（`IpSeriesRepositoryAdapterIntegrationTest`） |

### 不受影响

- REQ-51 后端 API（GET/PUT `/api/study-groups/{id}/ip-library`）零改动
- SettingsTab / MemberTab / KnowledgeTab / GroupInfoCard 零改动
- admin 模块零改动
- 数据库零改动（`group_ip_library` 表 REQ-51 已建）
- core 模块仅可能新增端口方法（实现层零业务逻辑变更）

## 回滚标准

- 前端：删除 `IpLibraryTab.tsx` 及其测试，回滚 index.tsx / group-api.ts / group.ts 三处修改
- 后端：删除 4 个新增文件（Controller/DTO/Assembler/AppService）+ 4 个测试文件（含 Adapter @DataJpaTest）
- core 端口 + Adapter + JpaRepo 三处 `findAllActive` / `findByStatusOrderByIdAsc` 相应回滚
- 无 DB schema 变更，无数据迁移

## 未来扩展（不在 REQ-62 范围）

| 需求/场景 | 涉及改动 |
|----------|---------|
| IP 关联数量上限校验 | 后端 PUT 加数量校验（如最多 50 个），前端展示剩余配额 |
| 移除有活跃卡牌数据的 IP | 接入 REQ-51 预留的 Q4 移除校验扩展点（GroupIpLibraryDomainService） |
| IP 搜索/筛选 | IP 数量超过 30 个时考虑加搜索框 |
| 关联时间排序 | 网格支持按 addedAt 排序/筛选 |
| 抽卡页等场景复用 IP 列表 | 已通过 GET /api/ip-series 解耦，抽卡页直接调用即可 |
