# PhotoTube-Droid

原生 Android / Jetpack Compose 相册：支持深浅色的便当盒照片布局，底部可随滚动收缩的胶囊 Tab 与正圆搜索按钮，使用 AndroidLiquidGlass 本地源码实现液态玻璃和手势动效。

## 真实后端接入

首页照片墙已接入服务连接、应用内登录、按月分组、游标分页与缩略图加载。默认服务为 `http://192.168.6.56:18473`；首页支持媒体类型、收藏与归档筛选，搜索暂未接入真实接口；远程详情已复用原查看器接入原图及视频播放。相册及其他模块仍是本地界面预览。调用流程、功能边界和验收记录见 [照片墙开发说明](docs/development/photo-wall/README.md)。

## 已有界面预览

- 图库首页、按月分组的照片与视频；右上角筛选。统一详情支持翻页、缩放、缩略图导航、玻璃工具栏、信息与系统分享。
- 独立相册页面、方形封面；点击进入相册网格详情，支持排序和缩略图大小切换。
- 右下角按当前页面搜索照片或相册，各自保存关键词与滚动位置。
- 底部上滑从 60dp 收缩至 48dp，下滑恢复，保留玻璃拖动动效。
- 筛选浮层与独立设置页；设置采用 iOS 风格分组，复用上游玻璃按钮、开关、滑块，包含图库偏好、触感、玻璃深浅和视频预览。
- 外观子页：跟随系统／浅色／深色、四种主题色和玻璃参数；通过统一语义主题注入所有页面与玻璃组件，并持久化设置。
- 预览模块保留 6 张离线示例照片和 1 段本地视频；真实首页不使用演示数据兜底，无设备相册读取权限。
- 相册 → 设置 → 视频预览：本地视频循环播放，TextureView 实时玻璃采样，可拖动玻璃、暂停与恢复。

## 开发

使用 Android Studio 同步工程，运行 `app`。最低 Android 16（API 36），targetSdk 保持 36。compileSdk 37 用于匹配上游 Compose 依赖；沿用 Gradle 9.5.0 / AGP 9.3.2。

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:assembleDebug :app:lintDebug
# 启动 API 36+ 模拟器后
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 源码结构

- `app/src/main/java/com/yunai/phototube/ui/PhotoTubeApp.kt`：界面与本地演示状态。
- `libraries/liquid-ui/`：可直接修改的四类玻璃组件，基于上游 demo。
- `libraries/backdrop/`：Android 构建适配器，直接引用 vendored 库源码。
- `third_party/AndroidLiquidGlass/`：上游源码与 demo 快照，保留 Apache-2.0 许可证。
- `docs/design/`：选定设计稿、模拟器截图、素材来源与动效接入说明。

详见 [液态玻璃接入说明](docs/design/liquid-glass.md)。

全局颜色与组件开发约定见 [主题规范](docs/design/theme/README.md)。

详情交互与播放器生命周期见 [媒体查看器说明](docs/design/media-viewer/README.md)。
