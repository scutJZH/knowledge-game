# Knowledge Game — 功能说明

> 面向使用者的功能清单，每次需求完成后更新。

## 已上线功能

### 用户端前端（React + Vite，端口 5173）

- 项目脚手架（Vite 5 + React 18 + TypeScript 5.6 + React Router v6.4 + antd 5）
- 暗色主题（antd darkAlgorithm，全局应用）
- 顶导布局 + 限宽 1200px 居中（Logo + 4 菜单占位：首页/图鉴/卡包/我的 + 用户信息区）
- 首页占位（后续页面入口预览）
- 路由（/ → /home 重定向、/login 占位、/register 占位、/home、* → 404）
- 404 页面（antd Result，含"返回首页"按钮）
- API 代理（/api → localhost:8082）
- Axios 封装 + 认证状态管理（REQ-27）
  - Axios 实例：自动注入 Bearer token、Result.data 自动解包、网络异常/业务错误统一转换为 ApiError
  - 401 自动刷新：并发请求排队、仅发一次 refresh、失败弹 Modal 提示 + 清除登录态
  - Zustand auth store：accessToken/refreshToken/expiresIn/user，localStorage 持久化（key: auth-storage）
  - 路由守卫（AuthGuard）：未登录自动跳转 /login?redirect=...，白名单路由（/login /register）不拦截
  - MainLayout Header：已登录显示头像+昵称+下拉菜单（个人中心/退出登录）
- 代码规范工具链（ESLint 9 flat config + Prettier + Husky + lint-staged）
- 测试基础设施（Vitest + jsdom + @testing-library/react，10 文件 36 用例）

### 用户管理（后端 API，用户端 8082）

- 用户注册（用户名 + 密码 + 昵称，密码 BCrypt 加密）
- 用户登录（用户名 + 密码，返回 Access Token + Refresh Token）
- 刷新令牌（Refresh Token 换取新的双令牌）
- 用户登出（Access Token + Refresh Token 加入黑名单）
- 查询用户信息（需 JWT）
- 用户列表（需 JWT）
- 更新用户资料（昵称、头像；avatarFileId 支持三态语义：undefined 不更新 / null 清空 / 值 更新）
- 删除用户

### 学习群组（后端 API，用户端 8082，需 JWT）

- 创建学习群组（名称必填 ≤50 字、描述可选 ≤500 字、头像可选凭证式上传）
- 创建时自动成为群主（OWNER），群组与成员记录原子写入
- 头像校验（bizType 必须为 STUDY_GROUP_AVATAR，文件必须属于当前用户）
- 同一用户可创建多个同名群组（name 无唯一约束）
- 直接加入 OPEN 群组 / 凭邀请码加入 INVITE_ONLY 群组
- 退出群组（OWNER 需先转让，ADMIN/MEMBER 可直接退出）
- 重新生成邀请码（仅 OWNER，Crockford Base32 8 位，SecureRandom 生成）
- 查询当前成员身份（角色 + 积分 + 加入时间）
- 更新成员角色（仅 OWNER，升级为 ADMIN 或降级为 MEMBER，幂等）
- 转让群主（仅 OWNER，转让后原 OWNER 自动变为 ADMIN，同步更新群组 ownerId）
- 踢出成员（OWNER 可踢 ADMIN/MEMBER，ADMIN 仅可踢 MEMBER，不可踢 OWNER）
- 群组成员并发防重（DB 唯一约束 + AppService 层 DataIntegrityViolationException 转 ALREADY_GROUP_MEMBER）
- 查询我的群组列表（含当前角色 myRole + 成员数 memberCount）
- 查询群组详情（OWNER/ADMIN 含邀请码，MEMBER 不含）
- 编辑群组信息（仅 OWNER，名称/描述/头像/加入方式）
- 解散群组（仅 OWNER，物理删除群组及成员记录）
- 查询群组成员列表（按积分降序，含用户昵称/头像）
- 群组 IP 库关联（OWNER/ADMIN 管理，MEMBER 只读）
  - 已关联 IP 卡片网格展示（封面图/名称/编码）
  - 添加弹窗（搜索过滤 + 多选，排除已关联 IP）
  - ⋮ 操作菜单：禁用（灰显保留数据）、恢复、删除（彻底移除）
  - 已禁用 IP 在本 Tab 可见，支持恢复
- 查询全部 ACTIVE IP 系列（GET /api/ip-series，供添加弹窗数据源）
- 用户端题目查询（按分类分页，仅返回 ACTIVE 题目）

### 知识点分类（用户端 API，8082，需 JWT）

- 查询 ACTIVE 分类嵌套树（GET /api/knowledge-categories/tree，供前端级联选择/树形导航消费）

### JWT 鉴权（后端，全端）

- 用户端 JWT 无状态认证（登录/注册/刷新公开，其余需认证）
- 管理端 JWT 认证 + ADMIN 角色校验（登录/刷新公开，其余需 ADMIN 角色）
- 管理端独立登录接口（仅 ADMIN 角色可登录，返回 Token + 用户信息）
- 管理端独立刷新接口（校验 Refresh Token 中的 ADMIN 角色）
- 管理端登出（Access Token + Refresh Token 加入黑名单）
- Token 黑名单（内存实现，支持登出主动失效，惰性清理）
- 旧 Token 兼容（无 jti 的旧 Token 正常通过，不强制重新登录）

### IP 系列管理（后端 API，管理端 8081，需 ADMIN 角色）

- 创建 IP 系列（编码 code、名称、描述、封面图、状态）
- 分页查询 IP 系列（支持名称/编码模糊搜索 + 状态筛选 + sort/order 列头排序）
- 查看 IP 系列详情
- 更新 IP 系列信息（必填字段 code/name/status 不可清空；可清空字段 description/coverImageFileId 支持三态语义：undefined 不更新 / null 清空 / 值 更新）
- 删除 IP 系列（移入回收站，30 天保留期；删除前校验无 ACTIVE 卡牌引用，有则拒绝；恢复后强制 INACTIVE）
- 唯一性校验（code 和 name 不允许重复）

### 卡牌管理（后端 API，管理端 8081，需 ADMIN 角色）

- 创建卡牌模板（含卡面图，编码同一 IP 系列下唯一）
- 分页查询卡牌模板（支持名称/编码模糊搜索 + IP 系列 / 稀有度 / 状态筛选 + sort/order 列头排序，默认 ACTIVE）
- 查看卡牌模板详情（含卡面图）
- 更新卡牌基础信息（必填字段 code/name/rarity/status 不可清空；可清空字段 description/imageFileId 支持三态语义：undefined 不更新 / null 清空 / 值 更新；支持仅传 status 切换启用/停用，INACTIVE→ACTIVE 时校验关联 IP 系列状态）
- 批量启用/停用卡牌模板（最多 100 条，启用时校验关联 IP 系列 ACTIVE）
- 删除卡牌模板（移入回收站，30 天保留期；恢复时校验关联 IP 系列仍存在 + 同 IP 下编码唯一；恢复后强制 INACTIVE）
- 下载 Excel 导入模板（6 列：编码/名称/所属IP系列编码/稀有度/描述/状态，含示例+说明行）
- 批量导入卡牌模板（Excel 上传，逐行校验+部分成功，200 行上限，返回成功/失败明细）

### 知识点分类管理（后端 API，管理端 8081，需 ADMIN 角色）

- 创建分类（名称、描述、图标、主题色、封面图、排序，支持父子层级）
- 分页查询分类（支持名称搜索 + 状态筛选 + 父级筛选，默认按 sort_order 排序）
- 查询完整分类树（含 INACTIVE 节点，树形组件直接消费）
- 查看分类详情
- 更新分类信息（必填字段 name/sortOrder 不可清空；可清空字段 description/iconFileId/color/coverImageFileId 支持三态语义：undefined 不更新 / null 清空 / 值 更新；不可修改 parentId）
- 移动分类（移到新父级或顶级，循环引用 + 目标父级同名唯一性校验）
- 批量排序（同级节点拖拽排序，最多 50 个）
- 停用分类（status 置为 INACTIVE，有 ACTIVE 子分类或关联 ACTIVE 题目时拒绝，前端按钮已改为"停用"）
- 同级名称唯一性校验（包含 INACTIVE 记录）
- sortOrder 自动计算（未传时取同级最大值 + 1）

### 题库管理（后端 API，管理端 8081，需 ADMIN 角色）

- 创建题目（选择题/多选题/判断题/填空题，含选项、答案、难度、标签、解析）
- 分页查询题目（支持 keyword/type/difficulty/categoryId/tag/status 筛选 + sort/order 动态排序）
- 查看题目详情
- 更新题目信息
- 软删除题目（status 置为 INACTIVE）
- 查询题目关联的知识点分类（过滤 INACTIVE 分类）
- 更新题目分类关联（全量替换，校验分类存在且 ACTIVE）
- 批量启用/禁用题目（最多 100 条，启用时校验关联知识点分类全部 ACTIVE）
- 下载 Excel 导入模板
- 导入题目（Excel 上传，返回成功/失败明细）
- 通用排序机制（SortField 值对象，RepositoryAdapter 维护字段映射，降级为 createdAt DESC）

### 知识条目管理（后端 API，管理端 8081，需 ADMIN 角色）

- 创建知识条目（标题、Markdown 正文、封面图、标签、分类关联）
- 分页查询知识条目（支持 keyword/categoryId/tag/status 筛选 + 6 字段列头排序 id/title/categoryName/status/createdAt/updatedAt，默认 sortOrder ASC + createdAt DESC，分类名称排序无分类条目排最后；列表响应不含正文 content/contentHtml，性能优化）
- 查看知识条目详情
- 更新知识条目信息
- 删除知识条目（移入回收站，30 天保留期；快照含分类关联 + 封面图；恢复后强制 INACTIVE + 校验分类全存在；purge 时清理封面图）
- 查询知识条目关联的分类（含 INACTIVE 分类）
- 更新知识条目分类关联（全量替换，禁止同时选祖先与后代）
- 批量启用/禁用知识条目（启用时校验关联分类全部 ACTIVE）
- 批量排序（无父子层级，纯交换 sortOrder）
- Markdown 后端渲染为 HTML（CommonMark + GFM 表格/删除线 + jsoup XSS 消毒）

### 管理后台前端（Ant Design Pro，端口 8000）

- 管理后台脚手架（Ant Design Pro v6+，ProLayout 侧边栏布局，手风琴菜单）
- 登录页（JWT 认证，ADMIN 角色校验）
- 路由鉴权（getInitialState + onRouteChange，401 被动刷新 Token）
- 分类管理页（左侧分类树 + 右侧详情面板）
  - 分类树搜索过滤（保留匹配节点及其祖先链）
  - 显示停用分类开关（默认隐藏，灰显展示）
  - 拖拽排序（同级节点，batch-sort 接口）
  - 拖拽移动（跨级或拖入节点，move 接口，自动设置 sortOrder）
  - 新建/编辑分类（Modal 表单，图标/封面图支持凭证式文件上传）
  - 移动分类（TreeSelect 选择目标父级）
  - 停用分类（前端预检有 ACTIVE 子分类 + Popconfirm 确认，按钮已改为"停用"）
- **IP 系列管理页**（ProTable CRUD + 凭证式封面图上传 + 启用/停用切换，默认筛选 ACTIVE）
- **卡牌管理页**（ProTable CRUD + 凭证式图片上传（单图）+ 启用/停用切换 + 点击缩略图 Image 预览 + 下载模板 + Excel 批量导入，编码 IP 内唯一，默认筛选 ACTIVE）
- **题库管理页**（ProTable + Drawer 表单 + 4 题型动态切换 + 批量启用/停用 + Excel 导入/下载模板 + 分类 TreeSelect 筛选 + 列头排序，默认筛选 ACTIVE）
- **知识条目管理页**（ProTable + Drawer 表单 + vditor Markdown 编辑器 + 凭证式封面图上传 + 分类 TreeSelect 多选 + 上移/下移排序 + 批量启用/停用 + 6 列头排序含分类名称 + 创建时间列）
- **回收站页面**（「系统」→「回收站」，左侧资源类型过滤 + 右侧 ProTable 列表，支持关键字搜索/排序/多选；行内「恢复」按钮（Popconfirm 确认）+ 工具栏「批量恢复」按钮（选中启用）；恢复后资源以停用状态回到原列表，需手动启用；行内「永久删除」按钮（danger Popconfirm）+ 工具栏「批量永久删除」按钮（Modal 数字确认，三档反馈：全成功/部分失败/全失败））
- 占位页面（商品、订单、抽奖配置、成就模板、用户管理）

### 微服务基础设施

- Nacos 服务注册与发现（各服务注册到 Nacos，配置中心管理共享配置）
- OpenFeign 服务间调用（Feign Client 接口共享，声明式 HTTP 调用）
- 机机鉴权组件（调用方按目标服务 `services` Map 注入不同 Key，被调用方单一 Key 校验不变）
- 统一日志 + TraceId 链路追踪（SLF4J/Logback + MDC，全链路请求追踪，日志脱敏）

## 规划中（Phase 2 题库 + 游戏核心）

- 秒判游戏（3 秒限时判断对错）
- Boss 关卡（关键词补全）
- 知识链串联（概念关系）
- 游戏结算与历史记录

## 规划中（Phase 3 群组系统）

- [x] 群组成员管理（加入 / 退出 / 邀请）
- [x] 群组管理员设置与群组转让
- [x] 剔除成员（OWNER/ADMIN 踢人，不可踢 OWNER）
- [x] 群组关联 IP 库（知识库全局共享，不需要群组授权）
- 群组内积分管理（查看 / 管理员修改成员积分）
- 跨群组全局卡片收集统计
- 每日学习签到（群组维度，管理员自定义连续签到奖励阶梯）
- 群组管理端（创建人/管理员进入群组专属管理界面）

## 规划中（Phase 4 卡牌系统）

- 积分流水记录（群组维度）
- 卡牌图鉴（用户端查看群组内收集状态）
- 盲盒抽取（单抽 / 十连抽，概率由卡池数量决定，群组维度）
- 保底机制（R:10 抽, SR:50 抽, SSR:100 抽，群组维度）
- 直购指定卡牌
- 卡牌升星（星级无上限，星级是用户收集维度属性，升星规则待后续需求定义）
- 实体奖励兑换（任意星级可兑换对应商品，兑换后清零保留解锁）
- 卡牌分解（随时分解已有卡牌换回积分）
- 卡包（群组维度，暂存待处理卡片，后续可换取积分/升星/兑换）
- 翻牌奖励（DNF 风格）
- 保底进度查询
- 卡牌展示（所有星级共用同一张卡面图，通过前端叠加星级边框/文字区分）
- 收藏里程碑（群组管理员为每个 IP 设置解锁里程碑阶梯，达成获取商城商品奖励）
- 收藏成就（收集指定卡牌达成成就，支持自定义和系统模板两种方式，达成获取商城商品奖励）
- 收藏成就自动检测（用户获得新卡牌时自动检测是否达成里程碑或成就，触发奖励发放）

## 规划中（Phase 5 商城及订单系统）

- 商城商品管理（系统管理员，每个 IP 卡片每星级对应一个商品）
- 卡片关联商品展示（用户看商品图，群组管理员看价格）
- 实体奖励兑换通知与下单
- 订单状态管理（待付款→已付款→已发货→已完成）

## 规划中（Phase 6 前端游戏界面）

- 用户端前端脚手架（REQ-26 已上线）
- 登录 / 注册页面
- 首页（群组选择 + 知识点分类）
- 秒判游戏页面（滑卡 + 倒计时 + 连击）
- Boss 关卡页面（关键词输入）
- 知识链串联页面（图谱可视化）
- 翻牌奖励页面（DNF 风格翻牌动画）
- 游戏结算 + 历史页面
- 卡牌图鉴页面（群组维度 + 全局统计切换）
- 盲盒抽卡页面（保留/换积分/放入卡包选择）
- 卡牌详情页面（升星/兑换/分解）
- 卡包页面
- 每日签到页面
- 保底进度页面
- 个人中心页面
- [x] 群组列表 + 创建/加入页面（REQ-60）
- [x] 群组详情 + 管理页面（REQ-61）
- [x] 群组 IP 库关联 Tab（REQ-62 + REQ-109）
- 群组管理端（成员积分 / 奖励订单 / 签到策略配置）
- 群组管理端 — 收藏里程碑与成就配置（为群组内各 IP 设置里程碑阶梯和收藏成就）
- 用户端 — 收藏成就与里程碑展示（查看收藏进度、成就列表、已达成奖励）

## 规划中（Phase 7 系统管理后台前端页面）

- 数据统计仪表盘
- 盲盒概率配置页
- 商城商品管理页
- 订单管理页
- 用户管理页
- 收藏成就模板管理页（系统管理员为每个 IP 创建/管理成就模板，支持设为默认启用）

## 规划中（通用服务）

- Token 黑名单 Redis 存储迁移（从内存迁移到 Redis，支持多实例部署）

