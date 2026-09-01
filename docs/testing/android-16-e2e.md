# Android 16 真服务端到端验收记录

## 验收范围

本记录固化 2026-09-01 在 Android 16 模拟器与隔离 PhotoTube 服务上的真实联调结果，覆盖：

1. Cookie 登录、加密持久化与应用进程冷启动恢复。
2. 路径相册“保存配置不扫描”的边界。
3. 活动扫描在应用进程被结束后的恢复观察。
4. 100 条首批历史与 5 条第二页历史的真实 cursor 追加。
5. Android Keystore 仪器测试。

这不是生产部署认证。HTTPS 反代、服务端会话主动失效、未初始化口令等部署边界仍按
`README.md` 的功能冒烟矩阵逐环境验收。

## 环境事实

| 项目 | 验收值 |
|---|---|
| AVD | `PhotoTube_API_36` |
| 设备序列号 | `emulator-5556` |
| Android | `16` |
| SDK | `36` |
| 内存页大小 | `16384` 字节 |
| 服务入口 | 模拟器访问 `http://10.0.2.2:18083` |
| Core 健康状态 | `status=ok`、`migrationVersion=43`、`dirty=false` |
| 媒体库 | 隔离只读目录 `/media/library/trip` |
| 测试素材 | 10 张 PNG |

服务使用独立 Compose 项目 `phototube_android_e2e`、独立数据库和临时端口，不读取或修改用户已有
PhotoTube 数据。测试账号口令和 Cookie 不写入仓库、截图、日志或本文。

设备事实通过以下命令读取：

```bash
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" -s emulator-5556 shell getprop ro.build.version.release
"$ADB" -s emulator-5556 shell getprop ro.build.version.sdk
"$ADB" -s emulator-5556 shell getconf PAGE_SIZE
```

结果依次为 `16`、`36`、`16384`。

## 会话与加密 Cookie

### 应用流程

1. 清除后的应用从服务器配置页连接 `10.0.2.2:18083`，health 成功后进入登录页。
2. 使用隔离管理员账号登录后进入真实远端照片页，而不是 Mock 页面。
3. 执行 `am force-stop com.yunai.phototube` 后以 `MainActivity` 冷启动。
4. 应用不再要求输入服务器或口令，直接恢复已认证内容页。
5. `shared_prefs/phototube_secure_session.xml` 存在；对测试用户名和口令的明文搜索结果均为 `0`。

该流程证明真实服务 Cookie 可以跨应用进程恢复。Cookie 的 host、path、secure、损坏密文自愈等细粒度
边界由 JVM 契约测试和下述真实 Keystore 测试共同覆盖。

### Android Keystore

在同一 API 36 设备安装 Debug APK 与 AndroidTest APK 后执行：

```bash
adb -s emulator-5556 shell am instrument -w \
  com.yunai.phototube.test/androidx.test.runner.AndroidJUnitRunner
```

结果：

```text
com.yunai.phototube.data.session.EncryptedCookieJarInstrumentedTest:..
Time: 0.232
OK (2 tests)
```

测试使用独立 SharedPreferences，验证密文不含 Cookie 明文、Jar 重建可恢复、请求域约束仍生效，且损坏
密文会被丢弃而不会阻断启动。

## 路径相册与活动扫描恢复

### 保存不扫描

通过 Android 图集页创建 `PATH_SYNC` 相册并选择 `trip`：

- 按钮明确显示“保存路径相册（不扫描）”。
- 保存后详情显示 `0 项`、目录 `尚未扫描`、扫描历史为空。
- 数据库同时为 `media_assets=0`、当前 `album_assets=0`、当前相册 `sync_runs=0`。

因此创建相册只调用相册配置接口，没有暗中调用扫描接口。

### 冷启动恢复同一运行

为稳定保留活动状态，先通过正式接口暂停 `ALBUM_PATH_SYNC` 队列，再由 Android 点击“手动扫描”：

1. Android 显示运行 `PENDING`、目录“等待”。
2. 数据库产生唯一运行 `2b602896-307c-4132-9b34-0ca0fd59cd8b`。
3. 强制结束应用进程并冷启动，认证会话直接恢复。
4. 图集列表显示 `1 个目录 · PENDING`；重新进入详情仍显示同一运行 `PENDING`。
5. 冷启动前后该相册运行数始终为 `1`，没有第二次触发扫描。
6. 恢复队列后原运行变为 `SUCCEEDED`，路径汇总为成功 `1`、失败 `0`、离线 `0`。
7. 数据库变为 `media_assets=10`、当前 `album_assets=10`、当前相册 `sync_runs=1`。
8. Android 自动刷新为 `10 项`，显示“发现 10 · 复用 0 · 新增 10”，并渲染真实文件缩略图。

这证明页面恢复依赖服务端 `lastRunId/lastRunState`，而不是靠再次 POST 或仅靠进程内布尔值。

## 深层同步历史

Android 的 `Pager` 初始页为 100 条。测试通过正式 `POST /albums/{id}/syncs` 串行创建 105 条运行，
每条都等待终态后再创建下一条：

- 数据库：`105` 条运行，`105` 条 `succeeded`。
- `GET /albums/{id}/syncs?limit=100`：返回 100 条，`nextCursor` 非空。
- 携带该 cursor 请求第二页：返回 5 条，`nextCursor=null`。
- 冷启动 Android 建立新的 PagingSource 后滚动到底部，界面显示首条运行的 `09月01日 09:48`。
- 接口证据确认这条最早运行只存在于第二页，因此界面不是只显示首批 100 条或本地伪造历史。

客户端始终原样传递服务端 cursor，不把 cursor 解析成页码，也不在本地合成第二页。

## 结论与未扩大声明

本轮通过项：

- Android 16 / API 36 / 16KB 页大小运行。
- 真服务 HTTP 登录和跨进程会话恢复。
- 真 Android Keystore 加密 Cookie 测试。
- 路径相册保存不扫描。
- 活动扫描冷启动恢复且不重复创建运行。
- 真实素材入库、认证缩略图与成员刷新。
- 100 + 5 深页同步历史 cursor 追加。

本轮未覆盖项：

- HTTPS 反向代理和受信任证书部署。
- 服务端主动失效会话后的真实并发 `401`；该竞态当前由自动化测试覆盖。
- 服务端尚未初始化管理员口令的真实部署页面。

这些未覆盖项不能引用本记录作为通过证据。
