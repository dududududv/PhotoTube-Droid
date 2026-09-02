# Android 后端接入

## 当前切片

当前已完成四十九个纵向切片：

1. 服务器配置、health、Cookie 会话和全局认证错误处理。
2. 远端照片列表、时间线摘要、Paging 3 游标分页和 Coil 认证缩略图。
3. 资产详情、原图、Media3 Range 视频、收藏和单图标签管理。
4. 远端普通相册、路径相册、成员管理、路径变更预览/应用与显式手动扫描。
5. 评分、归档、私密访问、回收站分页、恢复与彻底清理记录。
6. OPPO Motion Photo 动态组件播放与通用任务中心。
7. 完全重复项、普通/私密分域、副本路径核对与可逆处理。
8. 基础文件名搜索、类型/收藏/星级组合筛选与结果摘要。
9. 时间线年/月/日摘要切换、按 offset 分组与编辑式拼贴。
10. `/home` 首页概览、五个有界区块、布局切换与真实详情跳转。
11. JPEG/PNG 非破坏性编辑、不可变历史、原图/版本切换与内容寻址渲染。
12. XMP 能力检查、快照预估/创建、运行历史分页、详情轮询与私密授权。
13. 时间线高级筛选、日期范围、目录逐层浏览与标签 AND/OR/NOT 条件。
14. 服务端即时运维告警、Android 本机媒体缓存统计与安全清理。
15. 回忆日期范围屏蔽、规则游标分页与首页回忆刷新。
16. 图集重命名、拍摄时间排序、成员封面设置/清除与跨页面失效。
17. 人工标签库搜索、创建、重命名、删除与标签筛选统一失效。
18. 认证后账号入口、服务端登出、切换服务器确认与三层认证媒体缓存清理。
19. 路径相册扫描历史游标分页、逐路径详情与活动运行恢复观察。
20. 资产查看器只读照片信息：状态、原始时区时间、来源、文件事实、用户标记与 PhotoTube 标识。
21. 派生图片 `503 + Retry-After` 有界重试：只覆盖缩略图和编辑渲染 GET，不重放业务写请求。
22. 全局结构化错误诊断：保留服务端 `logId`，错误正文可选择并提供显式复制入口。
23. 类型化资产变更与跨页面失效矩阵：按收藏、评分、标签、私密、编辑及回收站语义统一换代消费者。
24. 加密 Cookie 持久账本：严格到期、响应覆盖/删除、重启往返、请求域匹配与换服务器隔离。
25. Android Keystore 设备验证基线：隔离测试存储、密文落盘、CookieJar 重建恢复、请求域约束与损坏密文自愈。
26. Media3 Range 播放恢复：HTTP 错误可见、416 从头重试、503/认证/缺失媒体分类提示且禁止自动循环。
27. 资产状态媒体读取闸门：仅 `BROWSABLE` 读取原图/视频，`PROCESSING` 只显示版本化缩略图与诚实状态，其余状态不生成原媒体请求。
28. 内容寻址版本键统一：时间线、相册封面、重复项与编辑渲染只接受 64 位小写 SHA-256，畸形值不得进入不可变缓存 URL。
29. 批量结果结构完整性：部分成功合法，但成功/失败集合必须互斥、无重复并精确覆盖请求目标，资产与相册成员写操作统一校验。
30. 私密资产跨入口窗口保护：查看器隐私采用未知/公开/私密三态，加载中先保护，任意来源的私密资产统一启用 `FLAG_SECURE` 与后台锁定。
31. 全局 401 锁存与竞态收口：订阅前不丢失、并发事件合并消费、会话 generation 阻止旧连接/登录/bootstrap 响应复活内容页。
32. 结构化错误驱动的 XMP 轮询熔断：UI 错误保留服务端机器码，历史或详情任一刷新失败即停止自动请求，只能由用户显式重试后恢复。
33. 手写游标分页一致恢复：单图标签候选、编辑历史深页与路径相册完整路径读取收到 `INVALID_CURSOR` 时丢弃旧集合并从首页重建；自动重建严格限制一次，重复失效或循环 cursor 暴露为诊断错误。
34. 协程取消边界统一：所有 ViewModel 的 `runCatching/onFailure` 在写 UI 错误前原样重抛 `CancellationException`，覆盖根会话、服务器切换、私密空间、图集、任务、重复项、回收站和资产操作，避免路由或作用域取消被伪装成请求失败。
35. 资产查看与编辑跨对象隔离：每次打开资产建立新的 request generation，详情、收藏、状态、标签搜索/追加/mutation 和私密解锁只允许原资产原 generation 写回；编辑完成事件显式携带 asset ID，旧资产结果不能污染当前查看器。
36. XMP 路由生命周期隔离：能力检查、预估、创建、运行详情与私密解锁分别拥有操作 generation，并共同绑定 route generation；离页、取消预估、关闭详情或关闭解锁框后，旧响应不得恢复已清理状态。
37. 重复项异步安全域隔离：路由、普通/私密分域、私密会话检查/变更、处理请求和选中组分别建立 generation；离页或切换分域后旧响应不得授予私密分页，处理 A 组的晚到结果也不能关闭后来打开的 B 组。
38. 底栏创作入口真实化：使用 `kind=PHOTO&archived=false&private=false` 的 Paging 3 照片流，提供全部/收藏与两档网格密度，点击资产进入既有查看器和不可变编辑版本流程；不新增上传、AI 或单图元数据写接口。
39. 任务中心异步一致性：摘要请求同时绑定 route generation 与 request generation，离页或新刷新会拒绝旧响应写回；已提交的暂停、继续或取消操作不因离页伪取消，其 in-flight 事实跨路由保留，重返页面时继续阻止重复提交并在完成后刷新摘要与 Paging。
40. 搜索提交原子性与 Unicode 边界：关键词按 Unicode code point 校验和截断 3～255 个字符，不切断代理对；每次有效提交都生成独立 request generation，因此相同筛选重复提交也会换代 Paging，旧摘要无法覆盖新结果；无效提交同步取消摘要、清空结果并结束统计加载态。
41. 首页聚合刷新一致性：初始 `/home` 未完成时到达的资产失效不再被吞掉；每次刷新递增 generation、取消旧请求并立即发起新请求，只有最新 generation 能提交首页数据或错误，即使旧网络调用不响应取消也不能覆盖新摘要。
42. 时间线摘要重复刷新隔离：摘要身份由 generation、完整 `AssetFilter` 与年/月/日粒度共同组成；根级资产失效在相同筛选和粒度下重复刷新时也会换代 generation，旧请求即使不响应取消也不能覆盖最新摘要或错误，Paging 仍由既有 `LazyPagingItems.refresh()` 独立换代。
43. 系统状态与本机缓存操作隔离：服务端状态拥有独立 generation；缓存统计与清理共享另一个 generation。重复状态刷新拒绝旧响应，开始清理会立即失效清理前快照，旧读取即使不响应取消并晚于清理完成，也不能把已清除的缓存数值或成功反馈覆盖掉。
44. 回忆屏蔽草稿所有权与持久失效：创建请求捕获提交时的日期草稿 revision，成功后只清理由该请求拥有的旧草稿，不覆盖提交后继续编辑的新范围；新增或删除成功通过有缓冲的一次性 Channel 通知 Paging 与首页，即使完成时页面没有订阅者，重新进入仍能消费刷新事件。
45. 标签库查询与 mutation 一致性：每次提交查询都推进 generation，相同关键词也会重建 Paging；创建、重命名或删除成功通过有缓冲的一次性 Channel 持久交付根级失效，离页期间完成也不会丢失；重命名响应必须保持请求标签 ID，服务端身份漂移按契约错误处理，不发送错误失效事件。
46. 相册详情跨对象异步隔离：详情、完整路径、目录浏览、路径预览、活动同步与历史详情分别绑定 `albumId + route generation + request generation`，切换相册后旧成功或失败不得写入新页面；已提交写操作保留原相册所有权，离页成功只通过有缓冲 Channel 持久交付根级失效；图集详情、设置、封面和同步响应同时校验所属 ID，读取错误不再误清 mutation 忙碌态。
47. 私密路由生命周期与锁定优先级：私密列表每次进入都先关闭本地 Paging 并重新读取服务端 session，route/check/auth generation 阻止离页、授权过期或新认证操作之前的旧检查与旧解锁复活内容；显式锁定立即 fail-closed，即使请求失败也不恢复缩略图；`SessionRepository` 串行化私密解锁与锁定，后提交的后台锁定等待活动解锁后执行，并淘汰尚在排队的旧解锁，保证最终服务端状态以锁定为准。
48. 回收站 mutation 根级可靠失效：`TrashViewModel` 提升到认证内容根级并按服务器与用户隔离实例，恢复或清除成功通过有缓冲 Channel 发送带资产 ID 的 `RESTORED/PURGED` 事件；根级收集器在回收站离页后仍存活，晚到成功也会立即按统一矩阵换代时间线、首页、图集、归档/私密、回收站、重复项、搜索与标签计数，失败不发送事件，忙碌期间拒绝重复写入。
49. Android 16 真服务端到端验收：在 API 36、16KB 页大小设备完成 HTTP Cookie 登录与冷启动恢复；路径相册保存后保持零运行，暂停队列后由 Android 创建唯一 `PENDING` 扫描，强杀重启仍恢复观察同一运行，恢复队列后导入 10 项；再以 105 条真实成功运行验证同步历史 `100 + 5` cursor 追加，并在同一设备通过 2 条真实 Keystore 仪器测试。完整证据见 `docs/testing/android-16-e2e.md`。

静态合集页面只作为视觉基线保留；人物回忆屏蔽只兼容展示既有规则，当前 Android 不提供创建入口。

## 代码树

```text
data/
├── AppContainer.kt
├── ContentHash.kt
├── UnicodeText.kt
├── album/
│   ├── AlbumModels.kt
│   ├── AlbumPagingSources.kt
│   └── AlbumRepository.kt
├── asset/
│   └── AssetRepository.kt
├── connection/
│   ├── ServerAddress.kt
│   └── ServerPreferences.kt
├── duplicate/
│   ├── DuplicateModels.kt
│   ├── DuplicatePagingSources.kt
│   └── DuplicateRepository.kt
├── edit/
│   ├── EditModels.kt
│   └── EditRepository.kt
├── folder/
│   ├── FolderModels.kt
│   └── FolderRepository.kt
├── home/
│   ├── HomeModels.kt
│   └── HomeRepository.kt
├── job/
│   ├── JobModels.kt
│   ├── JobPagingSource.kt
│   └── JobRepository.kt
├── memory/
│   ├── MemoryModels.kt
│   ├── MemoryExclusionPagingSource.kt
│   └── MemoryExclusionRepository.kt
├── remote/
│   ├── PhotoTubeApi.kt
│   ├── PhotoTubeApiModels.kt
│   ├── PhotoTubeServiceFactory.kt
│   └── RemoteFailures.kt
├── session/
│   ├── CookiePersistence.kt
│   ├── EncryptedCookieJar.kt
│   ├── SessionEventBus.kt
│   └── SessionRepository.kt
├── system/
│   ├── LocalMediaCache.kt
│   ├── SystemModels.kt
│   └── SystemRepository.kt
├── tag/
│   ├── TagModels.kt
│   ├── TagPagingSource.kt
│   └── TagRepository.kt
├── timeline/
│   ├── TimelineModels.kt
│   ├── TimelinePagingSource.kt
│   └── TimelineRepository.kt
├── trash/
│   ├── TrashPagingSource.kt
│   └── TrashRepository.kt
└── xmp/
    ├── XmpModels.kt
    ├── XmpExportPagingSource.kt
    └── XmpExportRepository.kt

ui/
├── AppViewModel.kt
├── PhotoTubeApp.kt
├── account/AccountScreen.kt
├── auth/ConnectionScreens.kt
├── collections/
│   ├── RemoteCollectionsScreen.kt
│   ├── CollectionsViewModel.kt
│   ├── AlbumDetailScreen.kt
│   └── AlbumDetailViewModel.kt
├── creation/
│   ├── CreationScreen.kt
│   └── CreationViewModel.kt
├── library/
│   ├── LibraryAssetsScreen.kt
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
├── memory/
│   ├── MemoryExclusionScreen.kt
│   └── MemoryExclusionViewModel.kt
├── photos/
│   ├── AdvancedFilterModels.kt
│   ├── AdvancedFilterSheet.kt
│   ├── AdvancedFilterViewModel.kt
│   ├── RemotePhotoTimelineScreen.kt
│   ├── TimelineGrouping.kt
│   └── TimelineViewModel.kt
├── search/
│   ├── SearchScreen.kt
│   └── SearchViewModel.kt
├── system/
│   ├── SystemStatusScreen.kt
│   └── SystemStatusViewModel.kt
├── tags/
│   ├── TagManagementScreen.kt
│   └── TagManagementViewModel.kt
├── trash/
│   ├── TrashScreen.kt
│   └── TrashViewModel.kt
├── viewer/
│   ├── AssetEditViewModel.kt
│   ├── AssetViewerScreen.kt
│   ├── AssetViewerViewModel.kt
│   └── PhotoEditorScreen.kt
└── xmp/
    ├── XmpExportScreen.kt
    └── XmpExportViewModel.kt
```

## 服务地址

`ServerRoot.parse` 接受完整 HTTP/HTTPS 地址，也允许省略 scheme 并自动补 `http://`。
输入末尾是 `/api/v1` 时会还原为服务根地址，最终由 `apiBaseUrl` 只追加一次。

以下输入会被拒绝：

- 空字符串。
- 非 HTTP/HTTPS scheme。
- 带 query 或 fragment。
- 带 `/api/v1` 以外的路径。

地址只有在 `GET /health` 成功且数据库可达、迁移不为 dirty 后才保存。切换地址会先清除旧 Cookie。

## 会话与 Cookie

- `EncryptedCookieJar` 是进程内唯一 CookieJar。
- Cookie 明文只存在于内存；落盘前使用 Android Keystore 中的 256 位 AES 密钥和 `AES/GCM/NoPadding` 加密。
- `CookiePersistence` 把无 Android 依赖的账本规则与 Keystore 外壳分离：同名、同域、同路径的响应覆盖旧值；删除 Cookie 和 `expiresAt <= now` 的记录绝不进入请求。
- 恢复时按后写入值去重，Cookie 是否发送继续由 OkHttp 按 host、path、secure 等属性判定；只保存规范化 origin，不保存登录请求的 query 或 fragment。
- 加密 Cookie SharedPreferences 已从云备份和设备迁移中排除，避免恢复到没有对应 Keystore 密钥的设备。
- Retrofit、Coil 与 Media3 都使用 `PhotoTubeServiceFactory.sharedHttpClient`。
- 不读取、不解析、不打印 `phototube_session` 的 token 内容。
- 共享 OkHttp 缓存负责原图 ETag 的 `must-revalidate`；会话清理会同时清 Cookie、Coil 内存/磁盘缓存和 OkHttp 缓存。
- `PrivateAccessOperationGate` 串行化所有模块共享的 `/auth/private-access` POST/DELETE。锁定提交时先推进 epoch：正在执行的解锁完成后，锁定排队执行；尚未取得执行权的旧解锁因 epoch 失效直接拒绝，不能在后台锁定之后重新打开服务端私密 session。

### 认证后账号与服务器

- 点击头像进入“账号与管理”，只展示服务端 `SessionUser` 与当前 `ServerRoot`，不读取或展示 Cookie token。
- “退出登录”必须先收到 `POST /auth/logout` 的 `204`；服务端返回 `500` 时保留本机会话并显示结构化错误，使用户能够重试，不能只清 Cookie 后谎称服务端会话已删除。
- 退出成功保留服务器地址并回登录页，同时由 `LocalMediaCache` 统一清除 Coil 内存、Coil 磁盘和共享 OkHttp 认证缓存。
- “切换服务器”先尽力请求旧服务器登出，再无条件清除旧 Cookie、三层媒体缓存和保存的地址；旧服务器离线或拒绝登出不会阻断配置新服务器，但配置页必须提示旧服务端会话未得到确认。
- 两个动作都需要二次确认；文案明确不会修改照片、相册、PhotoTube 业务数据或 NAS 原文件。
- `AppViewModel` 是会话闸门与操作忙碌状态的唯一所有者；账号页不直接调用 Retrofit，也不保存第二份认证状态。

## 时间线与图片

- `AssetFilter.toQueryMap()` 是列表与摘要的唯一筛选参数来源，避免两个端点出现筛选漂移。
- PagingSource 把 `nextCursor` 视为不透明字符串，追加请求原样回传。
- 追加页返回 `INVALID_CURSOR` 时返回 `LoadResult.Invalid`，让 Pager 建立新一代 PagingSource 并从首页刷新。
- 首屏和单页上限分别遵守服务端约定的 `100` 与 `500`。
- Coil 通过单例 `ImageLoader` 使用共享 OkHttpClient，因此缩略图请求自动携带同一会话 Cookie。
- 缩略图 URL 由 `MediaAsset.thumbnailUrl` 统一生成，包含 `size=SM|MD` 和 `v=<contentHash>`；没有哈希时不发起错误缓存请求。
- 年/月/日选择器分别请求 `granularity=YEAR|MONTH|DAY`；切换时取消旧摘要请求，且只有与当前粒度一致的响应可以更新 UI。
- 分组不改变资产顺序，不用手机默认时区重新解释 `takenAt`；日分组的“今天/昨天”只比较已解析的本地日期。
- 编辑式拼贴只是当前已加载 Paging snapshot 的渲染变换；每个可见单元仍通过 `LazyPagingItems[index]` 访问，保留尾部预取与 cursor 追加。

## 首页聚合

- `GET /home` 是一次性有界摘要，不分页：`onThisDay/recentPhotos/recentImports` 各最多 `18`，`frequentAlbums` 最多 `8`，`jobSummary` 与任务中心同口径。
- `HomeFeed` 在模型边界校验四个列表上限，避免服务端契约漂移让首屏变成无界渲染。
- `HomeFeedViewModel.refresh()` 不合并或丢弃进行中的刷新。每次调用先推进 generation，再取消旧 Job 并发起新的 `/home`；回调只有匹配最新 generation 才能写入 `Ready/Error`。因此初始加载与收藏、评分、标签、归档、私密、编辑或回收站失效并发时，旧摘要不能覆盖变更后的首页。
- 往年今日空数组表示隐藏整个区块，不显示伪空卡。整个首页为空时才显示总空态。
- `HomeFeedViewModel` 与 `/home` 聚合能力保留在数据层，作为非主导航回归模块；它不再通过底部按钮切换，也不占用“所有照片 / 相册”双项一级导航。
- 首页资产、相册与任务摘要仍分别具备打开资产查看器、相册详情和任务中心的路由能力，供后续明确入口复用。

## 时间线高级筛选

- 顶部筛选按钮打开草稿面板；关闭面板不会改变当前列表，只有“应用”才生成新的不可变 `AssetFilter`。
- `TimelineViewModel` 用 `MutableStateFlow<AssetFilter>` 驱动 `flatMapLatest`。应用或清除条件会取消旧摘要请求、建立新的 Paging generation，并让列表与当前年/月/日摘要共享同一个查询映射。
- 摘要请求另外携带单调 generation、完整筛选与粒度。根级资产失效即使没有改变筛选和粒度，也会推进 generation 并重新请求；成功和失败只有同时匹配三者才能写回，不能只靠协程取消或对象相等判断新旧请求。
- 日期由 Android 16 日期选择器输入为本地日历日，请求使用设备当前时区的当天 `00:00:00` 到结束日 `23:59:59.999999999`，两端均包含；开始日晚于结束日时禁止应用。
- 目录选择使用 `GET /folders` 的虚拟根、媒体库节点与单层子目录游标，只保存 `libraryId + folderPath`。客户端拒绝绝对路径、反斜杠、空段、`.` 和 `..`，也不展示宿主机根路径。
- 媒体库离线时目录不可进入或选择；目录筛选包含该目录及全部后代，切换目录不会复用旧 cursor。
- 标签列表沿用 `/tags` 的 100 条游标和 120 字符搜索。每个标签在“全部包含（AND）”“任一包含（OR）”“排除（NOT）”中最多属于一组，每组最多 100 个。
- 已选标签保存在草稿的 ID→模式映射中，即使搜索结果换页或改变关键词仍可见、可单独移除；客户端不把标签名称当查询主键。
- 高级筛选固定 `archived=false`、`private=false`，不会绕过归档页或私密空间的独立安全边界。

## 系统状态与本机缓存

- 从“账号与管理”进入“系统状态与缓存”。每次进入都重新请求需认证的 `GET /system/status`；它与公开、轻量的 `/health` 分离，不轮询、不保存历史，也没有“已读”状态。
- 客户端严格建模四类告警：派生目录空间不足、媒体库空间不足、派生缓存接近上限和任务连续失败；数值只接受非负当前值、正阈值以及 `BYTES/COUNT` 两种单位。
- 服务端告警是只读即时事实。Android 不调用不存在的服务端缓存清理接口，不把本地清理描述成派生缓存治理；缓存或任务失败告警只能进入任务中心查看，磁盘告警提示在服务端/NAS 处理。
- `SystemRepository` 统计 Coil 内存、Coil 磁盘和共享 OkHttp 认证缓存。清除操作只调用三者的 `clear/evictAll`，保留加密 Cookie、服务器地址、数据库事实、服务端派生缓存和 NAS 原文件。
- 服务端状态刷新与本机缓存操作是两个 generation 域。状态重复刷新只接受最新 generation；缓存读取与清理共享代际，开始清理会先推进 generation 再取消旧读取。清理期间拒绝新的缓存统计，清理成功或失败只有仍为当前 generation 才能结束忙碌态或写反馈。
- 清除本地媒体缓存后，当前会话仍有效；照片在下次查看时按内容寻址 URL 重新下载。私密锁定、退出和切换服务器继续沿用同一三层缓存清理边界。
- 从系统告警进入任务中心时，返回目标记录为系统状态页；从“账号与管理”直接进入任务中心时返回账号管理页。
- `/system/ai-settings` 继续随 AI 功能冻结，系统页面不读取、不展示也不修改 AI 地址、模型或密钥状态。

## 回忆日期屏蔽

- 从“账号与管理”进入“回忆屏蔽”。`GET /memory-exclusions` 固定按服务端 `(createdAt DESC, id DESC)` 顺序分页，客户端把 cursor 当不透明字符串；追加页收到 `INVALID_CURSOR` 时重建 Paging generation。
- Android 当前只创建 `DATE_RANGE`。`POST /memory-exclusions` 精确发送 `kind/dateFrom/dateTo`，日期使用 `YYYY-MM-DD` 且两端均包含；逆序范围在模型和界面两层拒绝。服务端重复创建返回既有记录的 `201` 仍视为幂等成功。
- 响应模型兼容既有 `PERSON` 规则并允许删除，但页面明确标注当前不能新增人物规则。客户端不调用服务端明确拒绝的人物创建能力，也不存在地点/场所屏蔽模型或入口。
- `DELETE /memory-exclusions/{uuid}` 不携带请求体，重复删除的 `204` 按幂等成功处理。新增或删除完成后同时刷新规则 Paging 和照片首页 `/home`，让“往年今日”立即重新求值。
- 创建请求捕获提交时的日期草稿 revision。请求挂起期间用户可以继续选择下一组日期；旧请求成功只在 revision 未变化时清空旧草稿，不能删除新输入。mutation 全局串行，忙碌期间创建和删除都拒绝重复提交。
- 成功变更使用 ViewModel 持有的有缓冲 Channel 发送一次性失效事件，而不是 `replay=0` 的 SharedFlow。页面活跃时立即刷新 Paging 与首页；页面无订阅时事件保留到重新收集，消费后不再重复播放。
- 规则只影响首页回忆查询，不隐藏、归档或删除资产，不改变时间线、搜索、图集、NAS 原文件或其它资产事实。服务端按资产自身 `taken_at_offset_min` 判断本地月日，Android 不用设备时区重新计算。
- 回忆规则属于需要服务端数据库备份的用户事实；Android 不在 Room 或偏好设置中维护第二份真相。

## 基础搜索

- 照片页顶部搜索入口打开独立结果页；查询仅匹配 `fileName` 的大小写不敏感字面子串，不把路径、EXIF、OCR、标签或语义搜索写成已有能力。
- 关键词在请求前 `trim`，最少 3 个、最多 255 个 Unicode code point；输入截断使用 code point 边界，不能留下半个 UTF-16 代理对。输入不足时原子取消旧摘要、清空结果和统计加载态，不发起全库查询。
- 类型、仅收藏与精确星级与关键词组合进入同一 `AssetFilter`。`SearchViewModel` 把该对象同时传给列表与 DAY 摘要，保证结果数和分页内容一致。
- 每次搜索建立带单调 generation 的不可变请求并取消旧摘要；即使完整筛选与上次相同也会创建新的 PagingSource。摘要回调必须匹配当前请求 generation 与完整 filter，旧响应即使忽略协程取消也不能写回计数或错误。
- 底层仍使用 `(takenAt DESC, id DESC)` 不透明 cursor，不做客户端二次排序；`%` 和 `_` 只是文字，不是客户端通配符。
- 普通搜索固定 `private=false`、`archived=false`，不泄露私密资产，也不把已归档内容静默混回结果。

## 资产查看与写操作

- `GET /assets/{assetId}` 负责刷新详情；页面按 `state` 决定是否读取媒体，`TRASHED/OFFLINE` 的 200 不会被当成 404。
- 照片先显示 MD 占位，再通过 Coil 读取 `/original`，解码尺寸受显示约束控制。
- `MediaAsset.canReadOriginal()` 是原媒体请求的模型层闸门。只有 `BROWSABLE` 可以构造 `/original`、当前编辑 `PREVIEW` 或 `/motion-video`；`PROCESSING` 只允许卡片/查看器尝试内容版本化缩略图，其余状态只展示事实。
- `ContentHash.isSha256ContentHash()` 是所有 `v=` 查询参数的唯一格式判定。空值、大小写错误、非十六进制或非 64 位值只能回退占位/原图，禁止进入缩略图、封面或编辑渲染的不可变缓存键。
- `Throwable.isInvalidCursorFailure(cursor)` 是手写分页识别深页游标失效的统一入口；首页错误不会被误吞。标签候选、编辑历史和路径配置必须丢弃旧 generation 后从第一页最多自动重建一次，第二次失效、重复 cursor 或其它错误进入可诊断错误态，禁止无限恢复循环。
- `Throwable.rethrowCancellation()` 是异步 UI 失败分支的统一取消闸门。任何 `runCatching` 包裹的挂起调用都必须先原样抛出 `CancellationException`，再映射普通错误；切换路由、筛选、资产、服务器或 ViewModel 清理时，不得把协程取消写成错误卡、私密解锁失败或服务器切换失败。
- `AssetViewerViewModel` 为每次有效 `open(assetId, revision)` 分配 request generation。详情读取、收藏、评分、归档、私密、回收站、标签候选和标签 mutation 都捕获 `assetId + generation`；晚到结果只能影响原上下文。私密解锁另有 attempt generation，用户关闭口令框或切换资产会使旧解锁回调失效，旧回调不得对新资产调用 `setPrivate`。
- 标签 mutation 在协程启动前固化目标资产 ID，服务端成功后再由类型化 `TagMutation` 更新当前快照；不得在挂起调用之后重新读取可变 `assetId`。编辑保存和版本切换同样捕获 `loadGeneration`，`EditApplyEvent` 必须携带所属 asset ID，查看器只消费当前资产事件。
- XMP 页面使用 `XmpRequestToken(routeGeneration, requestGeneration)` 隔离异步回调。能力探测、预估、创建、运行详情和私密解锁各自推进操作 generation；`onRouteLeft` 推进 route generation 并清空私密事实、预估、详情、忙碌态与错误。服务端已接受的导出任务继续运行，但旧创建响应不能重新打开详情，重新进入页面必须从能力与历史接口恢复事实。
- `cancelPreview`、`closeRun` 和 `dismissPrivateUnlock` 必须分别使对应操作 token 失效。关闭运行详情后轮询响应不能重新打开底部面板；关闭口令框后解锁响应不能恢复私密范围或自动续接预估；取消预估后旧 preview 响应不能重建确认卡。
- 视频使用 Media3 `OkHttpDataSource.Factory(sharedHttpClient)`，由 ExoPlayer 发起和恢复单段 Range 请求。
- 播放失败从 Media3 异常链提取 HTTP 状态。`416` 只提供用户驱动的“从头重试”，`503`、认证失败、无权限、原文件缺失与未知状态分别展示诚实文案；任何错误都不自动循环 `prepare()`。
- 收藏请求始终发送 `{"assetIds":[...],"favorite":目标值}`，不向服务端发送 toggle。
- `BatchOperationResult.failed` 非空时，即使 HTTP 为 200 也进入错误态；成功返回时间线时刷新 Paging generation 和摘要。
- `BatchOperationResult.validatedAgainst()` 先验证响应结构：成功和失败目标分别唯一、两组互斥，合并后必须与去重后的请求目标完全一致。合法部分成功继续逐项展示；矛盾、漏报或夹带目标被视为契约错误。
- 标签库支持不透明 cursor、120 字符搜索、创建后添加、已有标签添加和删除/抑制；标签 ID 使用 `Long`。
- “账号与管理”中的“标签管理”使用独立 Paging 3 列表，固定遵循服务端 `(assetCount DESC, id ASC)` 顺序。每次提交都会推进查询 generation，因此相同关键词也可显式刷新；追加页 `INVALID_CURSOR` 会丢弃旧页并从首页恢复。
- 创建和重命名统一按 Unicode code point 校验 1–120 个字符；重命名只发送 `{name}`，保持原 Long ID 和全部资产关系。名称唯一性仍由服务端 `lower(btrim(name))` 约束裁决。
- 重命名成功响应的标签 ID 必须与请求 ID 一致；身份漂移属于服务端契约错误，只展示错误，不伪造成功反馈或失效其它标签。
- `DELETE /tags/{tagId}` 返回 `affectedAssetCount`。删除确认明确说明它会移除全部资产关系和派生 `tagIds`，影响标签筛选与智能图集，但不会修改照片、其它标签或 NAS 原文件。
- 标签库变更通过有缓冲 Channel 交付给根导航，再统一失效资产详情、普通/私密/归档/回收站列表、搜索、图集与首页；mutation 在离页期间完成时事件保留到下次收集并只消费一次。删除时还从高级筛选草稿和当前已应用的 AND/OR/NOT 条件中精确移除该 ID；创建和重命名只刷新候选名称，不清除仍有效的筛选。
- AI 标签扫描没有进入任何正式数据层或页面。
- `motionPhoto.format=OPPO_MOTION_PHOTO_V2` 时，查看器可在静态 `/original` 与动态 `/motion-video` 之间切换；两个视频入口都使用共享 Media3 OkHttp DataSource，不从 JPEG 容器自行猜 offset。

## 非破坏性图片编辑

- 入口只对 `PHOTO + BROWSABLE + 64 位 contentHash + jpg/jpeg/png` 开放；不为 GIF、WebP、RAW、视频或处理中资产制造不可用入口。
- `EditTransform` 完整建模百万分之一裁剪坐标、`0/90/180/270` 旋转与双轴镜像，并在构造边界拒绝越界或小于 `10,000 PPM` 的裁剪。
- `GET /assets/{id}/edit-versions` 按不透明 cursor 分页。首次进入若当前激活版本不在第一页，会继续加载直到找到；`INVALID_CURSOR` 会丢弃旧历史并从首页刷新。
- `POST /assets/{id}/edit-versions` 必须显式发送可空 `expectedActiveEditVersionId`、当前 `sourceContentHash` 和完整配方。专用 Moshi adapter 保证预期原图时也实际发送 JSON `null`。
- `PATCH /assets/{id}/active-edit` 同时显式发送预期当前版本和目标版本；目标为 `null` 表示回原图并保留历史。
- 保存或切换遇到 `409` 时，客户端重新读取资产与历史并刷新查看器，不覆盖其它客户端先完成的修改。
- `sourceState=STALE` 的历史版本只展示，不能激活；当前摘要为 `STALE` 时所有媒体入口回退原图。
- `CURRENT` 激活版本的网格缩略图使用 `SM/MD` 编辑 render，查看器使用 `PREVIEW`，URL 同时包含版本 ID 和原图哈希 `v`。
- 客户端没有标题、描述、GPS 或拍摄时间编辑字段，也不会写 NAS 原文件。

## 创作入口

- 底栏“创作”是稳定非破坏性编辑的照片选择入口，不建立服务端不存在的作品、模板、上传或 AI 生成模型。
- `CreationViewModel` 固定以 `AssetFilter(kind=PHOTO, archived=false, private=false)` 建立 Paging generation；“仅显示收藏”只额外发送目标状态 `favorite=true`。
- 页面使用 `contentHash` 版本化缩略图。`BROWSABLE + 64 位哈希 + jpg/jpeg/png` 标为“可编辑”；其它照片仍可进入只读查看器，但明确显示“处理中 / 离线 / 仅查看”。
- 点击照片先进入统一资产查看器，再由查看器校验编辑能力并打开编辑器。保存、切换版本、回原图和 `409` 冲突处理继续复用既有契约，不复制第二套编辑状态。
- 左侧底栏动作在 2 列舒适网格与 3 列紧凑网格间切换；右侧动作与顶部筛选共同切换全部/收藏，均执行真实行为。
- 收藏或编辑成功后，根级 `TIMELINE_AND_HOME` revision 同时刷新时间线、首页和创作列表；从创作进入搜索时，返回目标保持为创作页。

## XMP 照片信息备份

- 页面进入时先读取 `/health` 的 `xmpExport.available`。`false` 是合法部署，不影响连接、登录、时间线、相册或媒体查看，也不会收集 XMP 历史 Paging。
- `POST /xmp-exports/preview` 由用户显式触发；默认请求体只有 `includePrivate=false`，`libraryIds` 缺席表示当前用户全部已注册媒体库。
- 预估不冻结集合。创建时必须把 `resolvedLibraryIds` 精确回传到 `POST /xmp-exports`；预估返回空媒体库时禁止把空范围错误变回“全部”。
- 客户端没有输出目录字段。服务端只返回 export volume 内 `xmp/<snapshotId>` 逻辑路径，模型拒绝绝对路径、`..` 和非 SHA-256 摘要。
- 运行历史每页 20 条、最大 500 条，cursor 原样传递；追加页 `INVALID_CURSOR` 使当前 generation 失效。
- 只有历史快照中仍存在 `PENDING/RUNNING` 且最近一次刷新没有失败时，才每 5 秒刷新列表；选中的活动详情遵循同一规则。进入终态或出现任何请求错误都会停止自动轮询，旧的活动快照不能驱动无限请求；用户显式点击重试成功后才恢复。
- `TimelineError` 保留服务端 `code/message/logId/retryable`，页面按机器码分支而不是解析中文。`RATE_LIMITED` 显示“稍后手动重试”，绝不在后台自动循环。
- 包含私密范围时先调用 `/auth/private-access`，预估和创建都重新依赖服务端授权。收到 `PRIVATE_ACCESS_REQUIRED` 会清除预估并要求重新解锁、重新估算。
- 历史与详情只建模状态、范围、计数、snapshot ID、逻辑目录、两类摘要哈希和脱敏 `errorCode`；不制造 facts/manifest 下载、逐项资产或源路径接口。
- XMP 不复制或修改 NAS 原文件，不写原文件旁，不导出编辑配方、相册关系、收藏、私密状态或审计；数据库备份仍是完整恢复通道。

## 普通相册与路径相册

- `AlbumPagingSource` 与 `AlbumAssetPagingSource` 把 cursor 当作不透明字符串；追加页返回 `INVALID_CURSOR` 时使当前 PagingSource 失效。
- 图集详情设置通过 `PATCH /albums/{albumId}` 更新名称与 `TAKEN_AT_DESC/TAKEN_AT_ASC`；智能图集排序由动态规则固定，Android 不制造可修改入口。
- 成员照片右上角菜单可以设为封面，固定发送成员资产 ID 与中心焦点 `{x:0.5,y:0.5}`。服务端负责验证照片确属当前图集；客户端不允许从全库任意指定封面。
- 清除封面必须显式发送 `"coverAssetId":null` 与 `"coverFocalPoint":null`。专用 `UpdateAlbumRequestJsonAdapter` 区分“未修改封面”和“将封面清空”，避免默认 Moshi 省略 null 后产生空 PATCH。
- 名称、排序或封面更新成功后立即替换详情中的服务端 `Album`，并同时失效图集列表与照片首页；成员增减、路径变更和扫描完成也沿用同一跨页面失效事件。
- `AlbumDetailViewModel` 是 Activity 级单实例；每次切换图集推进 route generation，详情、路径、目录、预览、活动同步和历史详情各自再推进 request generation。取消不合作的旧请求即使晚到，也只能影响原图集，不能覆盖新图集的数据、错误、选择或加载态。
- 成员、设置、封面、路径和删除 mutation 在发起时捕获原图集上下文。切换图集不会伪取消已提交的服务端写操作；成功后通过有缓冲 Channel 保留根级失效，但只有原 route 仍匹配时才能写页面反馈。相同图集的在途 mutation 由独立锁存事实阻止重复提交。
- 图集详情、设置/封面返回的 `Album.id`，路径条目的 `albumId`，以及同步运行的 `albumId/runId` 都必须与请求目标一致；身份漂移按契约错误处理，不把其它图集的数据写进当前页。
- 详情、路径、目录、同步与 mutation 的错误状态分域处理；辅助读取失败只更新错误或自己的加载态，不得提前释放仍在执行的写操作锁。
- 创建 `NORMAL` 相册不携带路径；创建 `PATH_SYNC` 相册可以携带初始路径，但只保存配置，绝不自动调用同步端点。
- 普通相册成员添加与移除每次最多 `500` 个 ID，并检查 HTTP 200 中的 `failed` 列表。
- 路径浏览使用相对目录参数；客户端不拼接或推断 NAS 绝对路径。
- 路径变更必须经过 `previewPathChange`，应用时复用预览阶段的原始请求并携带服务端返回的 `generation`，避免预览内容与实际提交漂移。
- `ADD`、`UPDATE` 和 `REMOVE` 都只修改路径配置；唯一扫描入口是用户明确触发的 `POST /albums/{id}/syncs`。
- 同步详情按 `1/2/3/5` 秒退避轮询。`PENDING`、`RUNNING` 继续轮询，其它状态均视为终态并刷新成员列表。
- `GET /albums/{id}/syncs` 通过 `AlbumPathSyncRunPagingSource` 按服务端 `(createdAt DESC, id DESC)` 顺序分页，默认 100、上限 500；cursor 原样传递，追加页 `INVALID_CURSOR` 使 generation 失效。
- 历史卡片区分 `INITIAL/MANUAL/LIBRARY_SCAN` 来源和六种汇总状态。点击后使用详情端点读取至多 100 条逐路径快照，并显示发现、复用、新增、移除、跳过和脱敏错误。
- 重新进入路径相册时读取摘要的 `lastRunId/lastRunState`；若最后一次运行仍是 `PENDING/RUNNING`，自动恢复详情轮询，而不是要求用户再次触发扫描。
- 历史详情请求与活动运行观察使用独立 Job；查看旧终态不会取消当前扫描，活动运行完成后同时刷新历史 Paging、成员列表、图集计数与首页。
- 路径离线时保留原成员；删除相册只删除配置与关系，UI 不提供删除资产或 NAS 原文件的误导选项。

## 评分、归档、私密与回收站

- 收藏、归档、评分和私密请求都发送幂等目标状态；评分清除必须显式序列化 `"rating": null`，因此使用专用 Moshi adapter，不能依赖默认的 null 省略行为。
- 单资产操作仍检查 `BatchOperationResult.failed`；HTTP 200 不代表目标资产成功。
- 归档成功后退出默认时间线，并刷新列表与时间轴摘要；归档不是删除，也不改变资产 `state`。
- 私密设置与取消都要求服务端 session 已通过 `/auth/private-access` 短时解锁。客户端从 `/auth/session` 读取授权事实，不自行延长或伪造授权。
- `LibraryAssetsRoute` 进入和离开 Composition 时显式通知 ViewModel。每次进入先把 `accessGranted=false`，清除旧口令、截止时间、错误与 Paging，再发起新的 `/auth/session`；不能因为 Activity 级 ViewModel 仍存活而复用上次进入的本地布尔值。
- 私密列表的 session 检查与解锁/锁定分别携带 route、check 和 auth generation。离页、重新进入、授权过期或开始新的认证操作都会使旧回调失效；旧成功不得重新开启 `private=true` Pager，旧失败也不得覆盖新路由错误区。
- 用户点击锁定时先本地 fail-closed，再请求服务端：立即丢弃私密 Paging、口令和过期时间。服务端锁定失败只显示可重试错误，不恢复已隐藏内容；`PRIVATE_ACCESS_REQUIRED` 同样会失效任何在途解锁并触发共享缓存清理。
- 私密内容范围启用 `FLAG_SECURE`；查看器不依赖来源页猜测隐私，而是把资产详情的 `private` 事实提升到根导航。加载中隐私未知时先保守保护，服务端明确返回公开后才解除；应用进入后台或用户点击锁定时调用 `DELETE /auth/private-access`，随后清除 Coil 与共享 HTTP 媒体缓存。
- 回收站使用独立 `(trashedAt DESC, id DESC)` cursor，不复用时间线 cursor。追加页 `INVALID_CURSOR` 会使当前 PagingSource 失效。
- 恢复请求只发送 ID，恢复到哪个状态完全由服务端保存的 `state_before_trash` 决定。
- `TrashViewModel` 由 `AuthenticatedPhotoTubeApp` 持有，不再随 `TrashRoute` 离开 Composition 而失去 mutation 完成事件。实例 key 同时包含服务器根地址和用户 ID，切换服务器或账号不会复用旧服务器创建的 Paging 流。
- 恢复或清除成功只发送一次带资产 ID 的类型化 Channel 事件；认证根级收集器将其交给统一资产消费者矩阵。回收站本身也只消费根级 `trashRefreshRevision`，不再维护第二份页面内刷新 revision，避免同一次成功重复刷新 Paging。
- `POST /trash/purge` 每次最多 `500` 个 ID，只删除 PhotoTube 记录与派生缓存。UI 明确说明不删除 NAS 原文件且未来扫描可能重新发现。
- 客户端没有 `all: true` 或删除 NAS 原文件的假参数；将来实现清空回收站时也必须按页、分批并展示累计进度。

## 通用任务中心

- `JobPagingSource` 固定按服务端 cursor 翻页，可组合 `state` 与 `kind` 筛选；追加页 `INVALID_CURSOR` 会使当前 generation 失效。
- `/jobs/summary` 不分页。`total=null` 表示服务端仍在发现任务，页面显示 `discovered`，不会计算虚假百分比。
- 页面每次进入都会建立新的 route generation，并发起新的摘要请求；离页会同时使当前摘要 generation 失效。旧路由或旧刷新返回的成功、失败都不能覆盖后来页面的摘要、加载态或错误。
- 队列暂停只改变尚未领取与后续入队任务，运行中的任务允许完成；界面明确展示该语义。
- 取消只对 `PENDING`、`PAUSED`、`RUNNING` 生效，运行任务依靠 heartbeat 协作退出，不向用户描述成强杀线程。
- 暂停、继续与取消一旦提交便是服务端操作，离开页面只清理当前页面反馈，不取消或伪造操作结果。ViewModel 独立锁存 in-flight target；操作未完成时重返页面仍显示忙碌并拒绝第二次控制请求，完成后只在当前路由刷新任务列表与摘要。
- `AI_INDEX` 与 `TAG_SCAN` 只读展示。`JobRepository` 会拒绝它们的暂停、继续和取消，避免间接接入冻结中的 AI 控制能力。

## 完全重复项

- `GET /duplicates` 仅返回 SHA-256 字节级完全相同且当前在线的组，不把相似图片或连拍误当成重复文件。
- `DuplicateGroupPagingSource` 原样传递 `contentHash` cursor、`includeReviewed` 和 `private`；成员端点有独立的 UUID cursor。追加页 `INVALID_CURSOR` 会使当前 generation 失效。
- `private=false` 与 `private=true` 是完全隔离的查询范围。后者必须先有服务端私密会话授权；收到 `PRIVATE_ACCESS_REQUIRED` 立即丢弃当前组与分页内容。
- 私密 session 检查同时绑定 route、scope、check 与 auth generation。切回普通范围、离页、发起新检查或开始解锁/锁定后，旧检查的成功和失败都不得写回。解锁结果只授予它发起时的私密范围；锁定是全局服务端事实，最新锁定成功会撤销当前本地授权。
- 页面离开会清空本地私密授权、口令、当前组、忙碌态和两个 Pager 的查询入口；服务端解锁事实不由 Android 布尔值延长，重新进入私密范围必须再次读取 `/auth/session`。
- `PUT /duplicates/{contentHash}/review` 表示用户确认保留全部副本；`DELETE` 同路径只删除该处理事实并恢复提醒。两者都不携带伪造请求体。
- review/reopen 捕获请求分域、操作 generation、目标哈希和选中组 generation。只有原安全域仍有效时才刷新；只有原选中详情从未变化且仍是同一哈希时才能自动关闭，避免 A 组回调清掉 B 组。
- `PRIVATE_ACCESS_REQUIRED` 已证明服务端授权失效，客户端只清本地门与 Paging，不再补发异步 `DELETE /auth/private-access`，避免该晚到锁定覆盖用户随后完成的新解锁。
- review 不删除资产记录，不移动或改写 NAS 文件。服务端后续发现新副本时，该组可重新出现为待处理提醒。
- 成员列表若因离线变为少于两个，端点返回空页；客户端提示返回刷新，不对旧组作破坏性操作。

## 错误分流

`SessionRepository` 按结构化 `code` 处理错误。共享 OkHttp 拦截器只在响应同时满足以下条件时发布会话失效事件：

1. HTTP 状态为 `401`。
2. 错误体 `code` 为 `UNAUTHORIZED`。

`SessionEventBus` 使用可锁存的 `StateFlow<Boolean>`，不是 `replay=0` 的瞬时事件。401 在根收集器启动前到达仍会被原子消费；同一批图片、视频和 API 并发 401 合并为一次本地会话清理。`AppViewModel` 在失效时递增 session generation，旧的连接、登录或 bootstrap 结果晚到也不得覆盖登录页。
3. 请求不是 `/auth/login`。

因此登录表单的 `INVALID_CREDENTIALS` 不会触发根页面循环跳转；`RATE_LIMITED` 也不会自动重试。

## 测试

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

当前自动化覆盖：

- 服务根地址规范化与非法路径拒绝。
- `/api/v1` 不会重复追加。
- 登录 `204 + Set-Cookie` 后，后续 `/auth/session` 自动携带同一 Cookie。
- Cookie 账本 JSON 往返保留 host-only、path、secure 与 httpOnly；覆盖、删除、精确到期边界、重复恢复和跨 host/path/scheme 请求隔离均有纯 JVM 测试。
- `EncryptedCookieJarInstrumentedTest` 使用隔离 SharedPreferences 在 API 36 设备上走真实 Android Keystore：检查落盘不含 Cookie 明文、重建 Jar 后仍可恢复、请求域仍受限、损坏密文被丢弃且不阻断启动。测试 APK 已纳入编译门禁，设备执行结果必须单独记录。
- 筛选参数校验、空格归一化与标签 CSV 稳定排序。
- 高级筛选整日日期边界、逆序日期拒绝、标签三态映射与激活维度计数。
- `/folders` 虚拟根/目录响应、媒体库相对路径、游标原样传递与宿主机/遍历路径拒绝。
- `/system/status` 只读 GET 路径、四类稳定告警、时间/数值/单位模型边界。
- 本地缓存合计和字节/次数的人类可读格式；缓存清理的 Cookie/NAS 保留边界由代码审计和真机冒烟验证。
- 系统状态与本机缓存双 generation：旧状态响应拒绝写回，清理前缓存快照不得覆盖清理后的计数和成功反馈。
- 回忆规则两种响应形状、日期范围边界、仅日期创建请求体和无 body 删除路径。
- 回忆规则不透明 cursor 原样传递、500 条上限与 `INVALID_CURSOR` generation 失效。
- 回忆创建草稿 revision 所有权、忙碌期重复提交拒绝，以及无订阅窗口内成功事件的 Channel 保留与单次消费。
- 时间线首屏、追加页和不透明 cursor 原样传递。
- 追加页 `INVALID_CURSOR` 使当前 PagingSource 失效。
- 缩略图 URL 同时携带尺寸与内容版本。
- 时间线缩略图、相册封面、重复项预览和编辑渲染共享 64 位小写 SHA-256 校验；畸形版本不生成 `v=`，当前编辑回退原图且编辑入口关闭。
- 缩略图和编辑渲染只在合法 `Retry-After` 下有限重试；正等待单次封顶 5 秒、最多两次，零秒不与 OkHttp 内建重放叠加。
- `ApiFailure` 到页面状态保留 `message/logId/retryable`；空 `logId` 不显示伪入口，本地网络异常也不伪造服务端日志编号。
- 查看器不再只回传 `changed=true`；变更类型先合并为集合，再映射到时间线/首页、图集、相册详情、归档/私密、回收站、重复项、搜索和标签库 revision。
- 资产详情可解析 Motion Photo 摘要与标签明细。
- 六种资产状态的原图读取策略、非 `BROWSABLE` URL 构造拒绝、Motion Photo 状态隔离和 `PROCESSING` 版本化缩略图保留。
- 资产信息格式化保留响应时区，覆盖未知时间回显、二进制文件大小、媒体时长、六种状态和五种时间来源。
- 收藏请求体和 200 部分失败语义。
- 批量成功/失败集合的完整覆盖、重复、交集、漏报和未请求目标边界；资产状态写入、回收站恢复/清理与普通相册成员增删统一经过该校验。
- 标签 `Long` ID、Unicode 名称边界、列表 cursor、创建、重命名、删除影响数、单图添加与移除路径。
- 标签库追加游标的 500 条上限与 `INVALID_CURSOR` generation 失效，以及已删除 ID 从三组筛选条件中精确移除。
- 标签库相同关键词重复提交的 Paging generation、离页窗口内成功事件的 Channel 保留、mutation 串行拒绝，以及重命名响应 ID 漂移的契约错误边界。
- 登出必须命中精确 POST 路径，只有服务端 `204` 后才清本地会话；服务端失败时保持可重试状态。
- 切换服务器在服务端确认和未确认两种结果下都清除 Cookie、三层认证媒体缓存回调与已保存地址，并让根状态机进入服务器配置页。
- 直接连接另一个服务器时，也会在新地址 health/session 探测前清除旧 Cookie 与认证缓存边界。
- 相册创建、列表 cursor、相册成员 cursor 与 `INVALID_CURSOR`。
- 路径相册创建只发送一次 `/albums`，不会隐式请求 `/syncs`。
- 路径变更预览与应用发送完全一致的请求体，并把手动扫描作为独立请求。
- 路径同步历史固定排序、Long generation、三种 trigger、六种状态、非负汇总以及 RFC 3339 时间模型边界。
- 同步历史 cursor 原样传递、500 条上限、`INVALID_CURSOR` generation 失效与详情独立 GET 路径。
- 相册成员删除使用带 JSON body 的 DELETE；来源目录始终发送相对路径。
- 图集名称/排序 PATCH 精确字段、成员封面中心焦点以及清除封面的两个显式 JSON null。
- 相册详情同对象重复刷新和跨对象切换的 generation 隔离、不可取消旧路径响应拒绝、离页 mutation 成功事件的 Channel 保留、响应图集 ID 校验，以及辅助读取错误不释放写操作锁。
- 归档、评分 null、私密和移入回收站的精确请求路径与 JSON body。
- 私密访问 POST 解锁、DELETE 锁定与服务端 session 探测。
- 私密列表每次进场复核、离页旧检查/旧解锁拒绝、授权过期失效在途解锁、锁定失败保持 fail-closed，以及会话层“活动解锁后锁定最终执行/排队旧解锁被淘汰”的顺序保证。
- 私密列表、私密重复项、XMP 私密范围及任意入口资产查看器的窗口保护矩阵；查看器未知/公开/私密三态和非查看器状态隔离。
- 回收站独立不透明 cursor、`INVALID_CURSOR`、恢复与 purge 路径隔离。
- 回收站恢复/清除成功事件的资产 ID 与 `RESTORED/PURGED` 类型、无订阅窗口 Channel 保留、失败不发送事件，以及忙碌期重复 mutation 拒绝。
- Motion Photo URL 固定使用资产 ID 与 `/motion-video`，不传路径或容器 offset。
- 视频 HTTP 错误文案覆盖 `401/403/404/416/503`、无状态网络异常和未知状态；只有 `416` 的恢复策略重置播放位置。
- 任务列表 cursor 与筛选原样传递、摘要 `total=null`、队列控制和单任务取消路径。
- AI 任务类型在模型层保持只读，不能通过通用任务仓库绕过冻结边界。
- 重复组 SHA-256 校验、缩略图版本 URL、组/成员 cursor 和普通/私密参数原样传递。
- 重复组 review/reopen 使用精确 PUT/DELETE 方法、同一路径、无 body 且可逆。
- 文件名搜索关键词 trim、字面 `%`/`_`、类型/收藏/星级参数以及普通范围的 `private=false`。
- 搜索列表与 DAY 摘要传递同一组筛选参数。
- 搜索关键词 3～255 Unicode code point 边界、代理对安全截断、相同筛选重复提交换代，以及摘要 request generation 隔离。
- 年/月/日分组保留 `takenAt` offset，不被手机时区改写；7 张组稳定生成 `2 + 3 + 2` 拼贴行。
- 时间线摘要 token 同时匹配 generation、完整筛选和粒度；同条件重复刷新时，不响应取消的旧回调也不能覆盖最新计数或错误。
- `/home` 精确五区块解析、空摘要、无分页参数以及 `18/8` 固定上限校验。
- 首页刷新 generation：进行中再次刷新必须启动新请求，旧请求即使忽略取消并晚到也不得覆盖最新聚合。
- 编辑变换边界、镜像后的旋转共轭、裁剪镜像与最小裁剪尺寸。
- 编辑历史 cursor、创建新版本和切回原图的精确方法、路径与显式 null 请求体。
- 资产 `activeEdit` 摘要解析、CURRENT 编辑 render URL、STALE 回退原图和格式入口白名单。
- `health.xmpExport.available` 合法关闭能力、默认预估省略 `libraryIds` 且保留必填 `includePrivate`。
- XMP 创建复用精确媒体库范围，列表/详情路径、脱敏摘要字段与逻辑目录边界。
- XMP 运行模型非负计数、终态判断、SHA-256 校验和空预估范围拒绝创建。
- XMP 历史不透明 cursor 原样回传与 `INVALID_CURSOR` generation 失效。
- XMP 自动轮询只在存在活动任务且没有刷新失败时成立；失败后熔断，用户显式重试可以恢复。
- 手写分页的 `INVALID_CURSOR` 判定只接受非空请求 cursor；路径集合与编辑历史从首页最多自动重建一次，旧 generation 数据不与新结果拼接。
- 协程取消闸门原样保留同一 `CancellationException` 实例；普通异常不被改写。根会话与各功能 ViewModel 的失败分支均在状态写入前经过该闸门。
- 资产 request token 同时匹配 ID 与 generation；任一变化都拒绝写回。编辑完成事件必须携带所属资产 ID，查看器不会消费其它资产的编辑摘要。
- XMP request token 同时匹配 route 与具体操作 generation；离开路由和关闭局部交互可以独立使旧请求失效，且不取消服务端已经接受的后台快照任务。
- 任务摘要 token 同时匹配 route 与 request generation；新刷新或离页均拒绝旧摘要写回。任务控制 token 只匹配 mutation generation 与目标，故离页不会把已提交的服务端操作误判为已取消，同时仍能阻止重复提交。
- 既有 Mock 数据稳定性。

已在隔离真实 PhotoTube 环境完成：

- Keystore 加密 Cookie 跨进程恢复。
- HTTP 局域网地址连接、登录与会话恢复。
- 路径相册保存不扫描、活动运行冷启动恢复与同一运行终态刷新。
- 105 条真实同步历史的 `100 + 5` 深页 cursor 追加。

仍需按实际部署环境完成：

- HTTPS 反代地址与证书链。
- 全局会话过期返回登录页。
- 订阅前 401 锁存、并发 401 单次清理，以及失效后旧 bootstrap 成功响应不得恢复认证内容。
- 服务端口令未初始化提示。

## 后续切片状态

稳定契约内没有待实现的功能切片。后续工作只在具体部署环境补齐 HTTPS、真实会话失效和未初始化口令
验收；发现契约或客户端缺陷时再建立新的纵向切片。

AI 打标以及单图标题、描述、GPS、拍摄时间编辑继续冻结，服务端重新发布稳定契约前不建立接入切片。
