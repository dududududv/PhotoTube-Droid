# 全局主题

入口：相册 → 设置 → 外观。默认跟随 Android 系统；可显式选择浅色或深色，以及雾蓝、烟紫、藕粉、暖砂主题色。配置即时应用并保存在本地，重新打开应用仍保留。玻璃参数也持久化；“恢复默认玻璃效果”只重置光学参数，不改变显示模式和主题色。

## 开发约定

- `ui/theme/PhotoTubeTheme.kt` 是主题入口。`AppTheme.colors` 提供 background、groupedBackground、group、text、secondary、glass、separator、accent、selected、subtle 等语义色；页面不能声明颜色常量，也不能判断当前模式来选色。
- 标准 Compose Material 组件从 `MaterialTheme.colorScheme` 取色；两套 API 由同一个主题配置生成。新增语义色先补齐浅色和深色映射，再在组件使用。
- `LocalGlassPalette` 是应用向 `liquid-ui` 注入颜色的边界；组件库不依赖 app 或 Material3。滑块、开关和标签读取注入的主题色，禁止重新引入 `isLightTheme = false` 等局部模式判断。
- 玻璃物理高光、阴影、透明遮罩不是界面前景色，保留库中的光学常量。照片/视频内容不随模式反色；媒体文字、背景、遮罩统一通过 `MediaColors` 取色。
- Activity 与全屏 Dialog 是不同的 Window，均使用 `ThemeSystemBars()` 更新状态栏、导航栏的图标对比度。媒体 Dialog 使用 `media = true`。
- `ThemeController` 是外观配置的唯一写入口，用枚举名称保存显示模式和主题色（不保存序号），未知名称回退默认值。偏好通过 SharedPreferences 的异步 `apply()` 写入，无额外依赖。
- 显示模式同时同步 `UiModeManager.setApplicationNightMode()`，让 Android 原生窗口、启动画面和配置限定符一致。这里的 `MODE_NIGHT_AUTO` 清除应用覆盖，恢复继承系统配置；不要使用修改系统设置的 `setNightMode()`。
- 原生启动窗口必须使用资源，`values/theme_tokens.xml` 与 `values-night/theme_tokens.xml` 的背景与 Compose background 保持一致。`forceDarkAllowed=false`，避免系统二次反色。

## 验证

`ThemeTest` 覆盖系统深浅切换、显式模式覆盖、主题色选择、Activity 重建后保留、图库/相册/设置浅深色背景与浅色应用中的媒体黑底。`HomeMockTest` 覆盖原导航收缩、筛选、搜索、滑块拖动和参数恢复；`VideoGlassTest` 验证实时视频玻璃与播放控制。

设计截图保存在本目录。主题选项有选择语义和文字名称，不仅依靠颜色表达选中状态；每个主题色分别定义浅色和深色值。

参考：[Compose 自定义设计系统](https://developer.android.com/develop/ui/compose/designsystems/custom)、[Android 深色主题](https://developer.android.com/develop/ui/views/theming/darktheme)、[AOSP 应用夜间模式实现](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/UiModeManagerService.java)。

设置图标使用 `SettingsGlyph` 的分层矢量插画，每个功能具有固定语义色；配色统一定义在 `SettingsIconPalette.kt`，不随主题色整组染色。主题色负责交互状态，图标插画负责功能识别。

设置图标参考 [iOS 26 设备指南中的设置截图](https://devicehelp.optus.com.au/apple/iphone-air-ios-26/getting-started/choose-siri-settings)：使用明快的功能色和轻渐变，保留灰色中性类别；低饱和主题色只作用于交互状态，不降低图标的饱和度。
