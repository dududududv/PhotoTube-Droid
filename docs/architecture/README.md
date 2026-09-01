# 架构说明

## 包结构

```text
com.yunai.phototube/
├── MainActivity.kt                  # 生命周期、系统栏和 Compose 装配
├── data/
│   ├── AppContainer.kt              # 进程级手工依赖容器
│   ├── ContentHash.kt               # 内容寻址版本键的共享 SHA-256 边界
│   ├── UnicodeText.kt               # 用户文本的 Unicode code point 计数与安全截断
│   ├── album/                       # 相册/成员/同步历史 PagingSource 与路径同步仓库
│   ├── asset/                       # 详情与资产写操作仓库
│   ├── connection/                  # 服务地址规范化与保存
│   ├── duplicate/                   # SHA-256 重复组、成员分页与可逆处理
│   ├── edit/                        # 不可变编辑配方、历史与并发选择仓库
│   ├── folder/                      # 虚拟根、媒体库与单层目录浏览
│   ├── home/                        # 有界首页聚合模型与仓库
│   ├── job/                         # 任务模型、PagingSource 与队列控制仓库
│   ├── memory/                      # 回忆屏蔽模型、PagingSource 与仓库
│   ├── remote/                      # Retrofit API、JSON 模型和共享客户端
│   │   └── DerivativeRetryInterceptor.kt # 派生图片 503 Retry-After 有界重试
│   ├── session/                     # 加密 Cookie、纯 Kotlin 持久账本、会话仓库与失效事件
│   ├── system/                      # 即时告警与统一三层认证媒体缓存边界
│   ├── tag/                         # 标签模型、PagingSource 与标签仓库
│   ├── timeline/                    # 筛选、游标 PagingSource 与时间线仓库
│   ├── trash/                       # 回收站专用 PagingSource 与仓库
│   ├── xmp/                         # XMP 预估、不可变快照运行与历史分页
│   └── MockAlbumRepository.kt       # 仅供静态视觉基线使用
├── model/
│   └── AlbumModels.kt               # 不可变的相册数据模型
└── ui/
    ├── AppViewModel.kt              # 连接、登录和会话闸门状态
    ├── AssetInvalidation.kt         # 资产变更类型到页面消费者的集中失效矩阵
    ├── CoroutineFailures.kt         # 挂起请求取消与普通失败的统一分界
    ├── PhotoTubeApp.kt              # 根闸门与认证后页面导航
    ├── account/AccountScreen.kt     # 当前账号、退出与切换服务器确认
    ├── auth/ConnectionScreens.kt    # 服务器配置、登录和初始化提示
    ├── components/
    │   ├── AlbumChrome.kt           # 共用顶部栏和底部悬浮导航
    │   ├── Android16Glass.kt        # 背景捕获与 RenderEffect 空间模糊
    │   ├── AppIcons.kt              # 不依赖图标库的 Canvas 图标
    │   ├── DiagnosticErrorText.kt   # 可选择错误正文与日志 ID 剪贴板入口
    │   └── PhotoDatePickerDialog.kt # 筛选与回忆规则共用日期选择器
    ├── collections/
    │   ├── CollectionsScreen.kt       # 保留的静态视觉基线
    │   ├── RemoteCollectionsScreen.kt
    │   ├── CollectionsViewModel.kt
    │   ├── AlbumDetailScreen.kt
    │   └── AlbumDetailViewModel.kt
    ├── creation/
    │   ├── CreationScreen.kt        # 真实照片创作选片与状态标记
    │   └── CreationViewModel.kt     # 全部/收藏 Paging 与 2/3 列密度
    ├── photos/
    │   ├── AdvancedFilterModels.kt  # 日期、目录与标签筛选草稿转换
    │   ├── AdvancedFilterSheet.kt   # Android 16 高级筛选底部面板
    │   ├── AdvancedFilterViewModel.kt
    │   ├── PhotoTimelineScreen.kt   # 保留的静态视觉基线
    │   ├── RemotePhotoTimelineScreen.kt
    │   ├── TimelineGrouping.kt       # 粒度分组与编辑式行节奏
    │   └── TimelineViewModel.kt
    ├── memory/
    │   ├── MemoryExclusionScreen.kt
    │   └── MemoryExclusionViewModel.kt
    ├── search/
    │   ├── SearchScreen.kt
    │   └── SearchViewModel.kt
    ├── system/
    │   ├── SystemStatusScreen.kt
    │   └── SystemStatusViewModel.kt
    ├── tags/
    │   ├── TagManagementScreen.kt
    │   └── TagManagementViewModel.kt
    ├── library/
    │   ├── LibraryAssetsScreen.kt   # 归档与私密资产列表、私密解锁门
    │   └── LibraryAssetsViewModel.kt
    ├── jobs/
    │   ├── JobCenterScreen.kt
    │   └── JobCenterViewModel.kt
    ├── duplicates/
    │   ├── DuplicateCenterScreen.kt
    │   └── DuplicateCenterViewModel.kt
    ├── home/
    │   ├── HomeFeedScreen.kt
    │   └── HomeFeedViewModel.kt
    ├── trash/
    │   ├── TrashScreen.kt
    │   └── TrashViewModel.kt
    ├── xmp/
    │   ├── XmpExportScreen.kt
    │   └── XmpExportViewModel.kt
    ├── viewer/
    │   ├── AssetEditViewModel.kt    # 编辑草稿、历史、并发冲突与应用事件
    │   ├── AssetInfoFormatting.kt   # 只读资产事实的确定性中文格式化
    │   ├── AssetViewerScreen.kt
    │   ├── AssetViewerViewModel.kt
    │   ├── VideoPlaybackErrors.kt  # Media3 HTTP 错误分类与 416 恢复策略
    │   └── PhotoEditorScreen.kt     # 全屏裁剪/旋转/镜像编辑器
    └── theme/
        ├── DesignTokens.kt          # 色彩、间距和圆角令牌
        ├── Theme.kt                 # Material 色彩主题
        └── Type.kt                  # 应用字体层级
```

## 依赖方向

```text
MainActivity → AppContainer → SessionRepository → Retrofit / OkHttp / CookieJar
                        └──→ LocalMediaCache → Coil 内存 / Coil 磁盘 / OkHttp 缓存
                        └──→ TimelineRepository → PagingSource
                        └──→ AlbumRepository → 相册 / 路径 / 同步 PagingSource
                        └──→ TrashRepository → 回收站 PagingSource
                        └──→ JobRepository → 任务 PagingSource / 摘要 / 控制
                        └──→ DuplicateRepository → 重复组 / 成员 PagingSource / 可逆处理
                        └──→ HomeRepository → 首页有界聚合摘要
                        └──→ EditRepository → 不可变编辑历史 / 乐观并发切换
                        └──→ FolderRepository → 虚拟根 / 相对目录浏览
                        └──→ SystemRepository → 即时告警 / 本机缓存统计与清理
                        └──→ MemoryExclusionRepository → 回忆规则 Paging / 新增 / 删除
                        └──→ XmpExportRepository → 能力 / 预估 / 快照运行分页
                        └──→ AssetRepository / TagRepository → 标签 Paging / 创建 / 重命名 / 删除
                        └──→ Coil ImageLoader ───→ 共享 OkHttpClient
                        └──→ Media3 DataSource ───→ 共享 OkHttpClient
            ↓
       PhotoTubeApp → AppViewModel → 会话闸门
                    └→ 认证后功能页面 → Repository → 不可变模型
```

- `model` 不得依赖 UI 或数据层。
- `data` 可以依赖 Android 资源和 `model`，但不得依赖 Composable。
- `remote` 只描述传输契约，页面不得直接依赖 Retrofit 接口。
- `BatchOperationResult` 在 Repository 边界调用 `validatedAgainst()` 后才能交给 UI：部分失败是业务结果，集合矛盾或目标不守恒则是服务端契约错误，两者不得混为一谈。
- 共享 OkHttp 的派生图片重试只识别 `GET /thumbnail` 与 `GET /edit-versions/{id}/render`：正等待最多额外请求两次、每次不超过 5 秒；`Retry-After: 0` 只由 OkHttp 内建机制立即重放一次。原图、视频、普通业务 API 和写请求不进入自定义重试层。
- 查看器的信息面板只消费 `MediaAsset` 快照；日期、字节数、时长和枚举文案由无 Android 依赖的纯函数格式化，不向 Repository 产生写请求。
- 原图访问能力由 `MediaAsset.canReadOriginal()` 统一判定，页面不能仅凭存在 `contentHash` 推断原文件可读。`BROWSABLE` 才能构造原图、视频和 Motion Photo 组件 URL；`PROCESSING` 的 `contentHash` 只允许驱动版本化缩略图。
- 内容寻址 URL 统一调用 `isSha256ContentHash()`；功能包不得复制正则或把任意非空字符串当版本键。畸形值保留在只读诊断信息中，但不进入 Coil/OkHttp 缓存键。
- 结构化服务端错误先映射为保留 `code/message/logId/retryable` 的 `UiError/TimelineError`，再由 `DiagnosticErrorText` 统一展示；页面不得先取 `.message` 丢掉机器码或诊断字段。剪贴板写入只发生在用户点击“复制日志 ID”后。
- ViewModel 中的挂起请求使用 `runCatching` 时，失败分支必须先经过 `rethrowCancellation()`；协程取消属于控制流，不是可展示错误。该规则覆盖普通 mutation、私密授权、根会话和尽力清理任务，防止已离开的页面或已销毁作用域写回错误态。
- Paging 3 与手写分页共享相同的 `INVALID_CURSOR` 语义：只对携带非空 cursor 的失败换代。手写的标签候选、编辑历史和路径集合丢弃已累积数据并从首页最多重建一次；连续失效或循环 cursor 不能被自动循环掩盖。
- `AssetViewerViewModel` 累积 `AssetChangeKind`，退出查看器时由根导航一次性求消费者并集并提升对应 revision。页面不得根据“从哪里打开”只刷新返回页，也不得让收藏误触发标签库换代。
- CookieJar 是 Retrofit、Coil 与 Media3 的唯一认证来源；禁止再建第二份 token 状态。
- `EncryptedCookieJar` 只负责 SharedPreferences、Android Keystore 与 AES-GCM 外壳；`CookiePersistence` 负责可在 JVM 验证的覆盖、删除、严格到期、JSON 往返、去重和 OkHttp 匹配规则。
- `MockAlbumRepository` 只服务静态视觉基线，认证后功能页面不得读取它。
- 共享组件不得反向依赖具体功能页面。
- `MainActivity` 不得持有功能页面状态或目标页面状态。

## 状态归属

| 状态 | 所有者 | 生命周期 |
|---|---|---|
| 当前认证后页面 | `AuthenticatedPhotoTubeApp` | `rememberSaveable` |
| 服务器、连接与会话闸门 | `AppViewModel` | Activity 级 ViewModel |
| 退出与切换服务器 mutation | `AppViewModel` / `AppSessionGateway` | Activity 级；账号页仅提交意图，状态切换由根闸门完成 |
| 服务地址 | `ServerPreferences` | SharedPreferences 持久化 |
| 会话 Cookie | `EncryptedCookieJar` / `CookiePersistence` | Keystore AES-GCM 加密持久化；纯 Kotlin 账本执行严格到期与请求域匹配 |
| 远端资产分页 | `TimelineViewModel` / Paging | 页面级 ViewModel |
| 时间线摘要 | `TimelineViewModel` | 页面级 StateFlow；generation + 完整筛选 + 粒度共同定义请求身份 |
| 时间线年/月/日粒度 | `TimelineViewModel` | 页面级 StateFlow，切换时取消并换代旧摘要请求 |
| 时间线已应用筛选与 Paging generation | `TimelineViewModel` | 页面级 StateFlow；应用/清除时同时换代列表和摘要 |
| 高级筛选草稿、目录浏览与标签候选 | `AdvancedFilterViewModel` | 页面级 ViewModel；关闭面板不提交草稿 |
| 照片布局模式（时间线/首页概览） | `TimelineViewModel` | 页面级 StateFlow，查看器返回后保留 |
| 首页五区块摘要 | `HomeFeedViewModel` | 页面级 StateFlow；每次刷新换代 generation，旧聚合响应不得写回 |
| 创作照片筛选、网格密度与分页 | `CreationViewModel` / Paging | 页面级 ViewModel；只读取普通未归档照片，编辑状态仍由查看器持有 |
| 搜索草稿、已提交请求、结果分页与摘要 | `SearchViewModel` / Paging | 页面级 ViewModel；每次提交使用独立 generation，列表与摘要共享同一不可变筛选 |
| 当前资产、收藏和标签操作 | `AssetViewerViewModel` | Activity 级单实例、按 assetId/revision generation 换代；异步结果携带请求身份 |
| 编辑历史、当前配方与未保存草稿 | `AssetEditViewModel` | Activity 级单实例、按 assetId/contentHash/activeEdit 换代；mutation 与完成事件携带资产身份 |
| 远端相册分页与创建 | `CollectionsViewModel` / Paging | 页面级 ViewModel |
| 当前相册、设置、成员、路径与同步运行 | `AlbumDetailViewModel` | Activity 级单实例；route/request generation 隔离全部读取，mutation 锁存原 albumId 并以 Channel 持久交付失效 |
| 归档或私密资产分页 | `LibraryAssetsViewModel` / Paging | Activity 级单实例；私密路由每次进场复核，route/check/auth generation 隔离旧授权回调 |
| 私密访问授权事实 | PhotoTube 服务端 session / `PrivateAccessOperationGate` | 客户端本地 fail-closed；共享锁定在并发解锁之后最终占优 |
| 回收站分页、恢复与清理进度 | `TrashViewModel` / Paging | 认证内容根级；按服务器与用户隔离，Channel 由根级持续收集 |
| 任务筛选、摘要与控制反馈 | `JobCenterViewModel` / Paging | 页面级 ViewModel；route/request generation 隔离摘要，in-flight mutation 跨离页锁存 |
| 重复组筛选、当前组、私密门与处理反馈 | `DuplicateCenterViewModel` / Paging | 页面级 ViewModel |
| XMP 能力、预估、创建、运行详情与私密门 | `XmpExportViewModel` / Paging | 页面级 ViewModel；route + operation generation 隔离晚到回调，离开路由清本地私密事实 |
| 服务端即时告警与本机缓存操作 | `SystemStatusViewModel` | 页面级 ViewModel；状态与缓存分属 generation 域，缓存读取和清理共享代际 |
| 回忆规则分页、日期草稿与变更反馈 | `MemoryExclusionViewModel` / Paging | 页面级 ViewModel；草稿 revision 隔离旧回调，有缓冲 Channel 持久交付一次性失效 |
| 标签搜索、分页与库级 mutation | `TagManagementViewModel` / Paging | 页面级 ViewModel；查询 generation 支持同词刷新，有缓冲 Channel 持久交付根级失效 |
| 静态视觉基线图片 | Android `drawable-nodpi` | APK 资源 |

当前使用手工 `AppContainer`，避免在功能边界尚未稳定时引入大型 DI 框架。Paging、Coil 与 Media3 已接入并复用 `PhotoTubeServiceFactory.sharedHttpClient`。Room 只在离线页缓存成为明确需求后引入，不复制第二份可变业务真相。

设备原生会话测试位于 `app/src/androidTest/.../session/EncryptedCookieJarInstrumentedTest.kt`。它通过 `ContextWrapper` 将 SharedPreferences 重定向到测试专用文件，因此能复用生产 Keystore 和加密代码路径，又不会读取、覆盖或清理用户的真实会话文件。JVM 测试负责纯账本规则，仪器测试负责 Android Keystore/SharedPreferences 边界，两层证据不得互相冒充。

资产查看器只保留一个 ViewModel。每次有效打开都会推进 request generation，并在提交请求时同时捕获目标 ID 与 generation；旧请求晚到不能覆盖新资产或同资产的新 revision。标签 mutation 在挂起前固化目标 ID，私密解锁还使用独立 attempt generation，因此关闭口令框或切换资产后不会续接旧 mutation。编辑完成事件携带 asset ID，资产 A 的保存、切换版本或冲突刷新不能更新资产 B。收藏或标签变化后返回时间线会建立新 Paging generation 并刷新摘要。

编辑状态与普通详情写操作分离：`AssetEditViewModel` 只持有当前照片的服务端历史、激活摘要、基线配方和瞬时草稿。初次读取会继续沿不透明 cursor 查找当前激活版本；追加 cursor 失效时从第一页重建，重复 cursor 被视为契约错误。保存新版本同时提交 `expectedActiveEditVersionId` 与 `sourceContentHash`，切换历史提交当前/目标两个版本 ID；`409` 后读取最新资产和历史，并请求查看器刷新，禁止最后写赢。

编辑版本是不可变事实。切回原图只把 `activeEdit` 设为 `null`，不删除历史；`sourceState=STALE` 的配方仍可审阅但不能激活。草稿只存在于编辑 ViewModel，退出或切换历史前必须确认，不写 Room 或本地文件。

底栏创作页只负责选择编辑目标，不复制编辑状态。`CreationViewModel` 用全部/收藏布尔值换代固定照片 `AssetFilter`，Pager 仍由 `TimelineRepository` 创建；点击后根导航把 `viewerReturnDestination` 设为 `Creation`，查看器返回时消费统一资产变更集合。搜索也保存独立 `searchReturnDestination`，因此从创作发起搜索不会错误回到照片时间线。

首页 `/home` 是有界聚合快照，但它同时消费多种资产失效。`HomeFeedViewModel` 通过 `HomeFeedGateway` 隔离数据源，并为每次刷新分配单调 generation；新的根级 revision 会取消旧 Job 并立即启动新请求，而不是因旧请求活跃而丢弃。成功和失败都必须命中当前 generation 才能写入 StateFlow，因此不合作的旧网络回调也不能复活变更前首页。

任务中心把“页面可写状态”和“已提交的服务端操作”分开管理。摘要回调必须同时命中当前 route generation 与最新 request generation；离页只失效页面摘要、加载和错误，不撤销已经发送的暂停、继续或取消。操作目标由 ViewModel 独立锁存，重返路由时恢复忙碌展示并拒绝重复提交；若操作在当前路由完成，才推进 Paging revision 并发起新摘要请求。首次进入不额外刷新 Paging，后续重返才换代列表。

相册详情同样只保留一个 ViewModel，并由根导航保存当前 `albumId`。普通相册成员列表和待选资产列表各自拥有独立 Pager；路径相册再拥有按 albumId 换代的同步历史 Pager。每次切换图集会推进 route generation，详情、完整路径、目录浏览、路径预览、活动同步和历史详情再使用各自的 request generation；旧请求即使不响应取消也不能写回新图集。已提交 mutation 捕获原 albumId 并独立锁存忙碌事实，跨离页成功通过有缓冲 Channel 交付根级失效，只有原 route 仍匹配时才更新页面反馈。名称、排序、封面、路径和同步响应还校验所属图集或运行 ID，辅助读取失败不得误清仍在执行的 mutation 锁。路径变更不会自动触发扫描，只有显式 `triggerManualSync` 可以创建同步运行。

`AlbumPathSyncRunPagingSource` 只负责固定顺序的历史摘要和不透明 cursor；逐路径结果由单运行详情端点读取。ViewModel 为活动运行观察和历史详情选择维护两个独立 Job，避免用户查看旧终态时取消当前轮询。打开路径相册后，如果服务端 `pathSync.lastRunState` 仍非终态，会使用 `lastRunId` 恢复观察；终态到达时刷新成员 Pager、历史 Pager、相册详情以及根级图集/首页消费者。

`UpdateAlbumRequest` 使用语义构造器和专用 Moshi adapter。普通设置只序列化名称与排序；设置封面才序列化资产 ID 和焦点；清除封面则强制写出两个 JSON `null`。内部 `updatesCover` 只用于区分 PATCH 语义，永远不进入网络请求体。

归档和私密列表使用同一页面结构，但筛选条件保持独立：归档传 `archived=true`，私密传 `private=true`。私密 ViewModel 是 Activity 级单实例，因此 route 的 Composition 生命周期必须显式推进 route generation；每次进入先关闭 `accessGranted`、清理局部状态，再请求 `/auth/session` 确认服务端授权。检查、解锁与锁定分别绑定 check/auth generation，离页、重返、授权过期或后续认证操作都会拒绝旧回调。收到 `PRIVATE_ACCESS_REQUIRED` 时立即丢弃当前 Paging generation 并重新显示口令门。

根导航通过 `shouldProtectPrivateContent` 统一决定 `WindowManager.LayoutParams.FLAG_SECURE`。它覆盖私密列表、私密重复项、XMP 私密范围，以及从任意页面打开且详情返回 `private=true` 的资产查看器。查看器隐私是三态值：加载中为未知并先保护，服务端明确公开后才解除，避免详情与窗口状态之间出现未保护帧。Activity 进入 `ON_STOP` 时调用私密锁定端点并退出私密路由；锁定回调清除 Coil 内存缓存、Coil 磁盘缓存与共享 OkHttp 缓存。`SessionRepository` 内的 `PrivateAccessOperationGate` 让 POST 解锁与 DELETE 锁定共享互斥序列：后提交锁定会在活动解锁后执行，并使等待中的旧解锁失效，所以后台锁定不只隐藏页面，也保证服务端 session 的最终操作是锁定。

回收站恢复和清除属于已经提交的服务端写操作，不由 `TrashRoute` 的 Composition 生命周期拥有。`AuthenticatedPhotoTubeApp` 按服务器根地址与用户身份创建 `TrashViewModel` 并持续收集其有缓冲 Channel；因此用户提交后立即返回照片页，晚到成功仍会携带资产 ID 与 `RESTORED/PURGED` 类型进入统一失效矩阵。失败只更新回收站错误状态，不伪造成功事件；同一时刻只允许一个回收站 mutation。回收站 Paging 只响应根级 revision，避免页面局部 revision 与跨页面失效对同一次成功重复刷新。

任务列表由 `JobFilter` 变化建立新的 Paging generation，摘要单独请求 `/jobs/summary`。暂停/继续操作作用于整个 kind 队列，取消操作只作用于单个活动任务。`AI_INDEX` 与 `TAG_SCAN` 虽然可出现在通用任务响应中，但 `JobKind.canControl=false`，数据层和 UI 都拒绝控制，以免越过 AI 契约冻结边界。

完全重复项由两个独立 Pager 组成：组列表按 `contentHash` cursor 分页，成员列表按资产 UUID cursor 分页。`DuplicateCenterViewModel` 同时持有 route、scope、private-check、private-auth、review 与 selection generation；普通/私密切换、离页、会话 mutation 和组选中变化分别使对应旧回调失效，不混合安全域，也不允许 A 组操作关闭后来打开的 B 组。`DuplicateCenterRoute` 在离开 Composition 时显式清空页面级私密事实和当前组，重新进入私密范围必须读取服务端 session。私密重复项、组详情及从其打开的查看器纳入同一 `FLAG_SECURE` 范围；进入后台即锁定并退回照片页。

XMP 页面先请求公开 `/health` 读取 `xmpExport.available`，只有可用时才收集历史 Paging，避免合法的未配置部署反复请求 503。预估是显式 mutation；创建请求必须复用预估返回的精确 `resolvedLibraryIds`，空解析范围不能退化成“全部媒体库”。能力、预估、创建、运行详情和解锁回调都携带 route + operation generation；离页或关闭局部交互后旧结果不能恢复 UI。运行历史使用 20 条 keyset 页，只有当前已加载范围存在 `PENDING/RUNNING` 且刷新状态没有错误时每 5 秒刷新；详情使用单 run 端点按同节奏轮询。终态或任一失败都会取消对应 Compose Effect，保留旧活动数据也不能继续自动请求；用户显式重试成功后再恢复观察。

XMP 私密范围仍以服务端 session 为唯一授权事实。开启私密范围时直接走当前账号口令解锁；路由通过 `onPrivateScopeChanged` 把范围提升到根导航，从而启用 `FLAG_SECURE` 和后台锁定。路由离开会丢弃本地 `privateUnlocked/includePrivate/preview`，重新进入必须再次读取 `/auth/session`，不能沿用过期布尔值。

搜索页把用户正在编辑的草稿与已提交的 `SearchRequest(generation, filter)` 分开，只在关键词去空后满足 3～255 个 Unicode code point 时建立 Paging generation。输入截断沿 code point 边界执行，不产生孤立代理项。同一个不可变 `AssetFilter` 同时传给 `/assets` 和 `/assets/timeline-summary`；每次提交都推进 generation，所以相同筛选也能显式刷新。新请求先换代身份再取消旧摘要，旧回调即使晚到也无法覆盖新结果；无效提交会同步结束旧统计加载态。

高级筛选也分离草稿与已应用状态：`AdvancedFilterViewModel` 负责 Android 日期、目录游标和标签候选，只有用户点击“应用”才把草稿转换成 `AssetFilter` 交给 `TimelineViewModel`。后者以 `flatMapLatest` 换代 Pager；摘要则以单调 generation、筛选对象和粒度三重比对响应。筛选或粒度变化拒绝旧条件，同条件根级刷新也拒绝旧 generation，即使底层调用不响应取消也不能覆盖最新状态。目录数据层只接受媒体库 ID 与安全相对路径，标签以 `Long` ID 而不是名称持久引用。

人工标签库由 `TagManagementViewModel` 持有搜索草稿、已提交请求 generation、Paging 流和串行 mutation。每次提交都会换代请求身份，因此同一关键词也能显式重建列表。创建、重命名或删除成功后通过有缓冲 Channel 发出带标签 ID 的一次性变化事件，离页期间完成仍会在重新收集时交付；重命名响应还必须保持请求 ID，否则按契约错误拒绝成功反馈和失效。根导航递增所有资产与图集消费者的刷新版本，并把删除 ID 传给 `TimelineViewModel` 和 `AdvancedFilterViewModel`。前者从已应用 `AssetFilter` 的三组标签集合删除该 ID，后者同步清理草稿、已知名称与候选项；重命名因 ID 不变，只刷新名称而不破坏筛选语义。

`AssetViewerViewModel` 同时记录标签库 revision。重新打开同一 assetId 时，revision 变化会绕过详情缓存并重新读取服务端资产，避免长期显示已重命名或已删除的标签。标签库本身不落 Room，服务端仍是名称、计数和关系的唯一真相。

认证后账号页是无独立 ViewModel 的受控页面。用户信息、服务地址、忙碌状态与结构化错误全部来自 Activity 级 `AppViewModel`；页面只发出退出或切换意图。普通退出必须由服务端 `204` 证明会话行已删除，失败时不清本地 Cookie，保留重试能力；成功后根闸门回到登录页但继续保留 `ServerRoot`。

切换服务器的可用性边界不同：`SessionRepository.switchServer` 对旧服务端登出做尽力请求，但通过 `finally` 保证保存的地址被移除，并始终触发 Cookie 与媒体缓存清理。返回的 `serverLogoutConfirmed` 只描述旧服务器是否确认，不充当本地清理结果；根闸门无论该值如何都进入服务器配置页，未确认时展示警告。

系统页将服务端与本机状态分为两个请求域：`GET /system/status` 只读取当前即时告警，`SystemRepository` 的本地分支通过进程单例 `LocalMediaCache` 读取和清理 Coil 内存、Coil 磁盘与共享 OkHttp 缓存。ViewModel 为服务端状态维护独立 generation；缓存读取与清理共享另一个 generation，清理开始即使所有旧快照失效，且清理期间不允许新读取进入。私密锁定、受保护媒体失效、退出、切换服务器和系统页主动清理都复用同一缓存对象，避免三层实现漂移；只有会话边界额外清 `EncryptedCookieJar`。因此主动清缓存不是退出登录，也不可能改变服务端 `DERIVATIVE_CACHE_HIGH` 告警。

回忆屏蔽页把日期选择草稿、mutation 串行状态和规则 Paging 交给 `MemoryExclusionViewModel`。每次日期编辑推进草稿 revision；创建请求只拥有提交时的 revision，因此成功后不会清空挂起期间继续填写的新草稿。成功新增或删除通过有缓冲 Channel 交付一次性变化事件：活跃页面立即刷新当前 Paging generation 并让根导航递增照片页版本；离页期间完成的事件保留到重新收集，消费后不重放。规则本身不写入 Android 本地持久层；服务端数据库保持唯一真相。响应中的 `PERSON` 仅为向前兼容展示，当前创建模型在构造边界锁定为 `DATE_RANGE`。

时间线列表仍保持服务端 `(takenAt DESC, id DESC)` 顺序。`TimelineGrouping` 只对当前 Paging snapshot 做稳定分段，不二次排序；分组日期直接取 `takenAt` 自带 offset，避免手机时区让边界附近资产跨日。日粒度组内以 `2 + 3 + 2` 为首个编辑式节奏，后续在三等分与双等分之间循环；单张使用满宽行。

底栏左侧动作只切换 `PhotoLayoutMode.TIMELINE/OVERVIEW`，不改变根导航的“照片”目的地。`HomeFeedViewModel` 通过独立 `HomeRepository` 读取 `/home`，不用本地聚合代替服务端口径。首页打开图集时，根导航记录 `albumReturnDestination=Photos`，返回时恢复原首页布局；从图集列表打开则返回 `Collections`。

## 会话状态流

```text
无服务器 → 输入地址 → GET /health
                         └→ GET /auth/session
                              ├→ authenticated=true → 内容
                              ├→ passwordSet=true → 登录
                              └→ passwordSet=false → 服务端初始化提示

POST /auth/login 204 → GET /auth/session → 内容
受保护请求 401 + UNAUTHORIZED → 清本地 Cookie → 登录
账号页退出 → POST /auth/logout 204 → 清 Cookie + 三层媒体缓存 → 登录
账号页切换 → 尽力 POST /auth/logout → 清 Cookie + 三层媒体缓存 + 地址 → 服务器配置
```

`SessionEventBus` 保存的是待消费事实而非瞬时通知。`notifyUnauthorized()` 把状态锁存为 true，根闸门通过原子 compare-and-set 取得一次处理权；突发 401 自动合并。`AppViewModel.sessionGeneration` 隔离失效前后异步操作，旧 generation 的成功或失败结果一律丢弃，避免会话过期后被晚到响应重新带回内容页。

## 渲染策略

- Compose 使用边到边绘制，在最接近页面边界的共享容器中处理状态栏和导航栏安全区。
- 远端时间线由 Paging 3 驱动 `LazyColumn`，只组合当前可见行并在接近尾部时预取。
- 远端时间线已使用固定高度与相对权重的编辑式拼贴：主图行 `1.72:1`，其后是三等分与双等分行，间隙统一 `4dp`。
- 六个基础图标使用 Compose `Canvas` 绘制，避免为了少量图标增加额外依赖。
- 每个页面使用同一个 `HazeState`：`hazeSource` 把列表背景录入独立图形层，`hazeEffect` 在悬浮组件坐标处采样该图层并执行硬件空间模糊。
- 项目依赖 Haze `1.7.2` 管理背景坐标映射与图层生命周期；在 API 36 上实际渲染由 Android `RenderEffect` 完成，不使用旧系统的色块降级路径。
- 普通 `Modifier.blur` 只模糊组件自身，不能作为背景毛玻璃方案；禁止用半透明白底冒充玻璃组件。
- 远端缩略图由 Coil 按 `ContentScale.Crop` 裁切；仅合集 Mock 图片继续作为 `drawable-nodpi` 打包。
- 原图由 Coil 从 `/original` 读取并按查看器约束下采样；存在 `CURRENT` 激活编辑时，缩略图和查看器分别读取带 `size=SM|MD|PREVIEW` 与 `v=contentHash` 的编辑 WebP 渲染。视频由 Media3 的 OkHttp DataSource 处理 Range。
- `PROCESSING` 查看器不进入原图或 Media3 分支：有内容版本时显示低透明度 MD 缩略图，没有时保留空占位，两种情况都覆盖明确的后台处理中状态。
- OPPO Motion Photo 的静图仍读取 `/original`，内嵌 MP4 只读取 `/motion-video`；两种视频入口共用同一个 Media3 OkHttp DataSource。
- Media3 错误监听器只负责把异常链映射为 `VideoPlaybackFailure`。普通错误保留当前位置等待用户重试；HTTP 416 明确标记 `retryFromStart`，点击后才 `seekTo(0) + prepare()`，避免后台无限 Range 循环。
- 会话边界同时也是媒体缓存边界；清 Cookie 时必须同步清三层受保护媒体缓存。
