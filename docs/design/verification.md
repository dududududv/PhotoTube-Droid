# Mock 验证记录

2026-09-08，PhotoTube_API_36 模拟器，Android 16，1280×2856 / 480 dpi。

- `:app:assembleDebug`：通过。
- `:app:lintDebug`：0 errors / 9 warnings，主要为版本更新提示、沿用 targetSdk 36、备份声明和未设置正式应用图标。
- `:app:connectedDebugAndroidTest`：3 个测试（含原版 demo 对照），0 failures / 0 errors；调整截图滚动位置后重新运行 2 个 PhotoTube 集成交互测试，通过。
- 测试覆盖：Tab 宽高、筛选按钮宽高、Tab 点击和拖动、单图打开关闭、收藏筛选、搜索、玻璃深浅状态。
- 视觉核对：当前 `glass-restored/` 七张实际渲染图及 `original-demo/` 四张原版截图。照片位于控件下方时可见静止模糊与边缘折射，按压态保留折射与形变。
- 最终 debug APK 已安装并打开至模拟器首页。

当前截图在 `glass-restored/`，`runtime/` 为历史版本。测试使用静态照片，无后端依赖。当前不是与设计稿照片逐像素一致的实现：示例照片已换为可离线打包的真实照片，保留相同的大小块布局和题材。

模拟器中的测试验证了功能与渲染；真实设备的帧率和触觉体验还需后续实机验证。

最新 Tab 专项截图见 `tab-refraction/`，包含同位置修改前后及照片背景上的按压状态。`:app:assembleDebug` 和两个 HomeMockTest 再次通过。

2026-09-09：图库/相册重组通过 assembleDebug 与两轮 HomeMockTest。覆盖顶部入口、独立搜索、封面 1:1、64→48→64dp 滚动切换、缩小态 Tab 拖动。当前截图 `pages-v2/`。
