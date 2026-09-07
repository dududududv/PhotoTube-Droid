# PhotoTube-Droid

仅包含 Android 基础工程，不包含产品 UI 或业务逻辑。

## 基线

- 单一 `app` 模块，包名 `com.yunai.phototube`。
- Android 16：minSdk / targetSdk / compileSdk 均为 36。
- 沿用原工程 Gradle 9.5.0、Android Gradle Plugin 9.3.2，使用 AGP 内置 Kotlin 支持。
- 唯一 Activity 是空启动入口，不调用 `setContentView` 或 `setContent`。
- 不引入 Compose、网络、数据库、播放器、图片加载等依赖，也不申请权限。

## 构建

用 Android Studio 打开根目录并同步 Gradle。SDK 路径由本机 `local.properties` 指定，不提交 Git。

macOS 命令：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:lintDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。启动后只有系统空白窗口，没有页面、控件、演示素材、后台任务或后端连接。
