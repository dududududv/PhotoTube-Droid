# 独立设置页

入口：相册右上角设置。全屏页面，深色分组卡片、大标题、组外说明、内缩细分隔线、彩色图标块。滚动后导航区淡入小标题，左上角悬浮玻璃返回。

复用 libraries/liquid-ui 中来自 Kyant0/AndroidLiquidGlass demo 的 LiquidButton、LiquidToggle、LiquidSlider；本轮没有修改其渲染或手势实现。组背景单独录入 backdrop，避免开关或滑块采样自身。外层列表仅供返回按钮采样。

设置接到现有页面状态：只看收藏、最新照片优先、触感反馈、玻璃深浅；视频预览保持可用。当前仍为 UI mock 的 rememberSaveable 状态，不包含账户、后端设置同步或进程重启后的磁盘持久化。

验证：assembleDebug 通过，HomeMockTest 三项通过（包括设置生效与返回保留），VideoGlassTest 一项通过。截图 settings.png 使用设备完整窗口截图，等待系统弹窗进入动画结束，避免透明过渡误判。
