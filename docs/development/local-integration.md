# 本机 API 联调

2026-10-10 用户明确授权的本地服务：`http://127.0.0.1:18474/api/v1`。本例外仅适用于此地址，禁止连接 NAS、其他局域网地址、生产服务或远程 AI。

## 运行模式（2026-10-10 更新）

已移除应用运行时 mock 数据源和 `phototube.localIntegration` 开关。Android Studio 普通 Run 的 debug、release 均连接同一个授权服务。连接失败显示连接错误，不会回退到示例图片。原来 release 被固定为 mock 的行为已删除。

合成 fixture、假 API 响应和示例相册数据仅保留在 `androidTest`；测试通过 `UiTestActivity` 显式注入，不使用 MainActivity 自动加载测试数据。`LocalIntegrationSmokeTest` 仍需运行时显式 opt-in，凭据不写入代码。

仅在已授权模拟器上执行：

```sh
adb -s emulator-5560 reverse tcp:18474 tcp:18474
```

应用使用服务根地址 `http://127.0.0.1:18474`，仓库统一追加 `/api/v1/`。启动检查健康状态与会话，然后显示登录或图库。通过应用内账号/密码字段登录；密码不保存，Cookie 在 Android Keystore 支持下加密保存到独立的本地联调会话空间，重启可复用，退出或失效后需要重新登录。切换构建不会恢复以前的生产账号。

网络策略只对本机 HTTP 开放，运行时进一步限制为精确的协议、地址、端口；禁止代理、重定向和跨来源请求。测试注入的 loopback fixture 保持可用。

## 验证边界

合成测试只启动注入测试数据的 Activity，不要对本地服务运行写入测试。联调只验证登录、会话恢复与读取；不自动启动扫描、AI、删除、分享或其他写操作。截图、日志、凭据及响应数据不入库。

2026-10-10 验证：4 项 JVM 地址边界测试通过；在 emulator-5560 上升级安装联调构建，`LocalIntegrationSmokeTest.loginReadGalleryAndRestoreSession` 通过，覆盖应用内登录、真实本机服务图库读取和新 Activity/ViewModel 的会话恢复。该测试需要额外的 `localIntegrationSmoke=true` 与运行时账号参数，普通合成测试中跳过；不保存凭据、服务响应或媒体截图。

另执行 force-stop 后冷启动，界面直接回到图库、未出现登录表单或连接错误，验证跨进程会话恢复。

## 图片加载链路排查 — 2026-10-10

已确认 Retrofit 与 Coil 共用 `PhotoTubeServer.client` 和同一个 `SessionCookieJar`。缩略图地址由客户端构造为 `/api/v1/assets/{id}/thumbnail?size=MD&v={contentHash}`，不依赖接口返回 `thumbnailUrl`。

本次复现的错误为 Coil `HttpException: HTTP 504`：隔离模拟器没有默认网络，Coil 的连接检查器将图片请求变为 `only-if-cached`，OkHttp 在没有缓存时返回 504。本机服务通过 ADB reverse 可达，因此 API 列表成功和图片失败可以同时发生。仅对精确匹配授权 origin 的图片加载器使用 `ConnectivityChecker.ONLINE`；认证、地址白名单、禁用代理和重定向规则保持不变。

Debug 日志标签为 `PhotoTubeImage`：`stage=response` 记录媒体 URL、HTTP 状态及是否来自网络；`stage=transport` 记录连接或读取异常；`stage=image` 记录最终 Coil 错误。解码异常未携带 HTTP 状态时，按同一 URL 查看 response 日志。URL 仅保留媒体路径、允许的 size 与内容哈希，异常文字脱敏，不记录 Cookie、Authorization、响应体或凭据。Release 不输出这些诊断。

验证使用用户明确确认无隐私的 localhost 生成测试图片，仅操作 `emulator-5560 / PhotoTube_Mock_Audit`。新增 opt-in 测试使用未经测试拦截器修改的生产图片加载器，禁用内存缓存，实际成功解码 **86 次缩略图及 1 张 PNG 原图**；会话恢复测试也通过。模拟器图库和详情页人工检查已显示合成图片，对应网络日志为 HTTP 200。此前编辑器出现的偶发流中断不以本次验证推定已根治。

手机上的 `127.0.0.1` 指向手机自身，不会自动指向开发电脑；本次移除 mock 不扩展到其他服务地址，也不自动连接或配置用户手机。

## 移除运行时 mock 后的验证

- debug、release 构建通过；release DEX 不包含 `LocalMockData`、示例相册实现或 `LOCAL_INTEGRATION`。
- 4 项本地地址边界单元测试通过，移至测试 Activity 的照片查看测试通过，本地服务登录/会话恢复、缩略图与原图解码测试通过。解码测试不再假定测试集必须包含 PNG。
- 仅在 emulator-5560 安装 release：移除端口转发时显示连接错误，无 mock 回退；恢复转发并冷启动后显示本地服务当前的 12 项生成测试内容。
- 未连接用户手机、NAS 或其他服务；截图、日志、APK、凭据均不提交。
