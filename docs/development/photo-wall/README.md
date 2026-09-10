# Android 真实后端：照片墙第一阶段

本阶段只接入首页照片墙与其必要的连接、会话基础层。契约依据：PhotoTube 后端 `docs/03-development/android-api/00-common.md`、`01-photo-wall.md`、`02-photo-viewer.md` 与 `contracts/openapi/`，2026-09-10。

## 调用流程

1. 输入服务根地址，校验格式；请求 `GET /api/v1/health`，确认 PhotoTube 健康对象后才保存。
2. `GET /auth/session`；未设置口令提示管理员初始化，未认证进入登录表单。
3. `POST /auth/login` 接受 204 空响应，保存 Set-Cookie，再请求 session 确认登录。
4. `GET /assets/timeline-summary?granularity=MONTH&archived=false`。
5. Paging 3 请求 `GET /assets?limit=100&archived=false`，后续原样回传 nextCursor，null 才终止。
6. Coil 用相同 OkHttp 客户端请求 `/assets/{id}/thumbnail?size=MD&v={contentHash}`。

列表不请求 `/home`：该端点是首页摘要，不可替代照片墙分页。首阶段不发送关键词、收藏、kind 等筛选条件，也不在客户端重排服务端时间顺序。

## 实现边界

| 层 | 实现 |
| --- | --- |
| 网络与契约投影 | `data/PhotoTubeServer.kt`：Retrofit、错误码、资源 ID、游标、缩略图 URL |
| 会话 | `data/SessionCookieJar.kt`：Android Keystore AES-GCM；origin 的 scheme/host/port 隔离 |
| 页面状态 | `data/PhotoWallViewModel.kt`：认证状态、摘要先行、分页生命周期、刷新 |
| 原生 UI | `ui/remote/RemotePhotoWall.kt`：按原始比例横向等高、整行对齐的虚拟照片墙、月分组、加载/空库/错误/追加重试 |

- 48/60dp 导航与现有主题、液态玻璃继续使用；图库标题随照片墙滚动。
- 缩略图最多 MD 640px；没有把缩略图当详情原图。此阶段远程资产卡片不打开旧的本地演示查看器。
- 右上角恢复筛选，右下角恢复搜索及原有展开动效；目前明确标注界面预览，暂未调用真实筛选/搜索服务。服务器连接入口移至相册 → 设置，列表标题处提供刷新。
- 相册等原有页面仍是界面预览，已标注“尚未连接服务器”；不声称它们是实际 NAS 数据。
- 不上传、不扫描、不收藏/删除、不写入 NAS；未接入 `/original`、视频播放、相册 API、任务或 AI。
- Cookie 不进入日志；密码不持久保存；切换服务器清除旧 Cookie，并取消旧请求、销毁图片加载器。图片磁盘缓存目前关闭，避免跨会话残留。
- 客户端禁止跨地址重定向携带认证信息。HTTPS 使用系统证书；明文例外仅限用户确认的 `http://192.168.6.56:18473`，其他地址需要 HTTPS。
- `INVALID_CURSOR` 从第一页恢复；追加错误保留已有项目。Paging 内存窗口 500 项，前后页用不透明游标衔接，不使用 OFFSET。
- 已知拍摄时区按该偏移分月；未知时区利用摘要 startsAt，不套用手机时区。
- `contentHash=null` 不构造假的 v；显示处理中。`DERIVATIVE_PENDING` 按 Retry-After 最多重试两次，等待超过 5 秒则交给手动重试；429 不自动重试。
- HTTP 客户端自身对 503/Retry-After:0 的隐藏重试已避免，统一采用至少 1 秒且有上限的策略。

## 验收记录

- [x] 编译与 lint 无错误。
- [x] 隔离 HTTPS 测试：204 登录、共享 Cookie、加密持久化、端口隔离、游标回传/结束、失效游标、429、401 与错误口令区分、缩略图版本及有界重试、时间分组。
- [x] 隔离 UI 测试：105 项、分两页、虚拟滚动、图片网络加载。
- [x] 真实 `192.168.6.56:18473`：health 正常、数据库可达；session 要求登录；未认证 assets 返回 401 UNAUTHORIZED。
- [x] 模拟器真实服务连接至登录表单；首页不再出现演示照片兜底。
- [ ] 用户在应用中登录后，核对真实 NAS 照片、月分组及分页。
- [ ] 真机弱网、10 万项实库、跨会话缓存和长期内存压力验收。

测试命令：设置 `ANDROID_SERIAL=emulator-5554`，运行 `PhotoWallApiTest`、`RemoteWallUiTest`、`PhotoWallConnectionTest`。旧 HomeMockTest 是演示首页测试，不能作为真实首页验收；照片墙迁移后的新测试如上。隔离测试使用独立 HTTPS 服务和独立连接偏好，不保存或消费真实口令。

## 开发安装与登录保持

Gradle 仪器测试默认在结束时卸载应用，会清除偏好、会话和 Keystore 密钥。项目已设置 `android.injected.androidTest.leaveApksInstalledAfterRun=true`，保留测试后安装。后续更新用 `adb install -r`，不要卸载或清数据。应用启动恢复加密会话；服务端失效或主动清除数据仍需要重新登录。

跨进程回归：分别运行 `SessionRestartTest`，传入 `sessionRestartPhase=seed` 和 `sessionRestartPhase=restore`，中间覆盖安装 APK。该测试仅使用 `.invalid` 域名的固定测试会话，不访问真实 NAS 会话。

会话写入在 OkHttp 后台线程使用 `commit()`，确认落盘后才返回登录成功，避免 `apply()` 尚未完成即结束进程。已验证独立进程恢复以及覆盖安装后恢复。
