# 开发流程

## 环境要求

- 安装包含 JBR 的 Android Studio。
- 安装 Android 16 SDK Platform 36；项目不支持 API 35 及以下版本。
- 依赖缓存完成后，应用构建与运行不要求网络连接。

## Android 版本策略

- `minSdk = 36`
- `targetSdk = 36`
- `compileSdk = 36`
- 不添加 `Build.VERSION.SDK_INT` 兼容判断或旧版替代实现。
- Android 16 专属 API 可以直接调用；使用新图形 API 时必须增加至少一次 API 36 设备运行验证。

## 背景模糊依赖

- 使用 `dev.chrisbanes.haze:haze:1.7.2` 捕获 Compose 背景图层并管理模糊区域坐标。
- 页面根列表必须挂载 `hazeSource`，所有 `android16Glass` 必须位于对应的 `Android16HazeProvider` 中。
- 禁止删除背景捕获后只保留 tint；没有空间模糊的透明色块不算毛玻璃。

## 后端接入

- 当前接入状态与代码入口见 [backend-integration.md](backend-integration.md)。
- 服务端契约以 PhotoTube 仓库 `contracts/openapi/` 为准，Android 不手写未声明端点。
- Retrofit、Coil 与 Media3 已复用 `AppContainer.serviceFactory.sharedHttpClient`；新增媒体入口不得另建客户端。
- 禁止记录 Cookie、口令、私密内容 URL 或包含凭据的请求头。

## 构建命令

终端已经配置 Java 时：

```bash
./gradlew :app:assembleDebug
```

macOS 终端找不到 `java` 时，使用 Android Studio 自带 JDK：

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 修改步骤

1. 确认变更属于连接/会话、资产、相册或其它明确功能领域。
2. 顶部栏、悬浮底栏和图标优先复用 `AlbumTopBar`、`FloatingAlbumDock` 与 `AppIcon`。
3. 可复用的稳定数值加入 `DesignTokens.kt`；只服务于单个拼贴结构的比例保留在功能页面内。
4. 未远端化页面新增 Mock 数据仍放在 `MockAlbumRepository`；真实数据必须经过 Repository。
5. 位图素材放进 `drawable-nodpi`，文件名必须使用小写语义名称。
6. 构建调试 APK。
7. 执行 `docs/testing/README.md` 中的视觉验收矩阵。
8. 行为或约束改变后更新对应文档节点。

## Kotlin 与 Compose 约定

- 使用不可变数据模型和尾随逗号。
- 事件通过回调向上提升，功能 Composable 不得直接访问 `Activity`。
- 页面级 Composable 负责编排，私有子 Composable 负责局部视觉。
- 需要在 Activity 重建后保留的可见状态使用 `rememberSaveable`。
- 重复出现的颜色和尺寸使用语义化令牌，不散落魔法值。
- 图片必须提供有意义的 `contentDescription`，或明确标记为装饰内容。
- 一个小型稳定 Compose 原语可以完成的能力，不得为其单独引入第三方库。
