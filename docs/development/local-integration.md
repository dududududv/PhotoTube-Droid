# 服务器连接与本机验证

## 应用行为

- debug、release 使用同一套连接流程，没有 mock 开关或默认服务器。
- 首次启动输入 HTTP/HTTPS 服务器地址，支持域名、IP、自定义端口；粘贴以 `/api/v1` 结尾的 API 地址会归一化为根地址。
- 连接后登录。设置中的“切换服务器”和登录页的“更换服务器”可重新输入地址。
- 连接成功后保存地址；下次启动恢复连接。Cookie 按协议、主机、端口分别加密保存，切换不向新服务器发送旧服务器 Cookie。密码不保存。
- 使用新的连接/会话存储空间，不自动导入此前开发版本的服务器配置与历史账号。
- 支持自托管 HTTP，HTTPS 保持系统证书验证；请求仍限于所选 origin，禁止自动重定向。LAN 服务不以系统“是否有互联网”判断可达性，实际请求决定连接结果。
- 连接失败显示错误，不回退到假数据。

## Agent 验证边界

应用允许用户连接自己的服务器，不代表开发代理可以访问它们。仅使用 emulator-5560 / PhotoTube_Mock_Audit、本地合成 fixture，以及用户明确授权且仅含生成测试图片的 `http://127.0.0.1:18474/api/v1`。禁止连接手机、NAS、其他私有服务或远程 AI。测试用数据只在 androidTest 中注入。

本机联调需要在该模拟器配置 `adb -s emulator-5560 reverse tcp:18474 tcp:18474`，再在应用地址框输入 `http://127.0.0.1:18474`。手机的 localhost 指向手机自身，本流程不替用户配置手机连接。

`LocalIntegrationSmokeTest` 仍需运行时显式传入 `localIntegrationSmoke=true` 和账号参数；凭据、日志、截图、APK 不入库。`ServerSwitchTest` 使用两个独立 loopback 服务验证输入地址、分别登录、切换与会话隔离，不接触外部服务。

## 图片诊断

Retrofit 与 Coil 共用 OkHttpClient/CookieJar；缩略图在客户端构造 `/api/v1/assets/{id}/thumbnail?size=MD&v={contentHash}`。debug 的 PhotoTubeImage 标签记录媒体 URL、HTTP 状态与脱敏异常，禁止输出 Cookie、Authorization 和响应体。之前离线模拟器的缓存 504 来自网络状态误判，现由实际 HTTP 请求判断服务可达性。

## 本次验证

3 项地址解析测试通过；debug/release 构建通过。仅在指定模拟器运行两个本地 fixture 的完整登录切换测试，覆盖 A→B→A、Cookie 隔离和新 Activity/ViewModel 的地址与会话恢复。release 实际启动显示空的服务器地址输入框，不包含固定联调地址。未连接用户手机或其他服务。
