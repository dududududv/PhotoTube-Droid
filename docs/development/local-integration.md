# 本机 API 联调

2026-10-10 用户明确授权的本地服务：`http://127.0.0.1:18474/api/v1`。本例外仅适用于此地址，禁止连接 NAS、其他局域网地址、生产服务或远程 AI。

## 启用

在不入库的 `local.properties` 中增加 `phototube.localIntegration=true`，或构建时传入 `-Pphototube.localIntegration=true`。只影响 debug；release 和未启用配置的构建仍使用进程内示例数据，不会自动回退至其他服务。

仅在已授权模拟器上执行：

```sh
adb -s emulator-5560 reverse tcp:18474 tcp:18474
```

应用使用服务根地址 `http://127.0.0.1:18474`，仓库统一追加 `/api/v1/`。启动检查健康状态与会话，然后显示登录或图库。通过应用内账号/密码字段登录；密码不保存，Cookie 在 Android Keystore 支持下加密保存到独立的本地联调会话空间，重启可复用，退出或失效后需要重新登录。切换构建不会恢复以前的生产账号。

网络策略只对本机 HTTP 开放，运行时进一步限制为精确的协议、地址、端口；禁止代理、重定向和跨来源请求。测试注入的 loopback fixture 保持可用。

## 验证边界

合成测试必须显式使用 `-Pphototube.localIntegration=false`，不要在联调构建上运行会写入数据的测试。联调只验证登录、会话恢复与读取；不自动启动扫描、AI、删除、分享或其他写操作。截图、日志、凭据及响应数据不入库。

2026-10-10 验证：4 项 JVM 地址边界测试通过；在 emulator-5560 上升级安装联调构建，`LocalIntegrationSmokeTest.loginReadGalleryAndRestoreSession` 通过，覆盖应用内登录、真实本机服务图库读取和新 Activity/ViewModel 的会话恢复。该测试需要额外的 `localIntegrationSmoke=true` 与运行时账号参数，普通合成测试中跳过；不保存凭据、服务响应或媒体截图。

另执行 force-stop 后冷启动，界面直接回到图库、未出现登录表单或连接错误，验证跨进程会话恢复。
