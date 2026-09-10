# Liquid Glass 接入与动效

上游：https://github.com/Kyant0/AndroidLiquidGlass
分支：kmp
快照：65ab177e90e5c1d8c62e70cf7755841982da65f6
许可证：Apache-2.0，原始 LICENSE 保存在 third_party/AndroidLiquidGlass/LICENSE。

## 本地源码

- `third_party/AndroidLiquidGlass/`：完整上游源码快照，包含 Android/iOS/桌面 demo 和四类示例控件。以普通源文件纳入工程，而不是依赖 Git 子模块或外部路径。
- `libraries/backdrop/build.gradle.kts`：Android 构建适配器，直接编译上游 `backdrop/src/commonMain` 和 `androidMain`；修改这些源文件后重新编译即生效。
- `libraries/liquid-ui/`：从上游 demo 复制并注明来源的按钮、Tab、开关、滑块及手势工具。产品相关调整集中在这里，上游示例保留可对照。

## 已检查并复用的 demo 动效

- `LiquidBottomTabs`：手势位移转换为 Tab 进度；松手取最近 Tab；外层 panelOffset 使用 EaseOut 限幅并以弹簧回零。
- `DampedDragAnimation`：value、velocity、pressProgress、scaleX、scaleY 分别使用弹簧，保留速度导致的非等比拉伸与松手回弹。
- Tab 选中块：按压渐进增加 lens 折射、内阴影、高光，组合页面 backdrop 与 Tab 图标文字的 backdrop，保留放大折射效果。
- `InteractiveHighlight`：高光跟随触点，松手后弹簧回到起点并消退。
- `LiquidButton`：tanh 限制跟手偏移，按手势方向改变横纵比例。
- `LiquidToggle` / `LiquidSlider`：复用相同的阻尼拖动机制；增加无障碍点击、开关状态、滑块进度语义。

## 首页固定参数

- 左下 Tab：196 × 64 dp，内部 4 dp inset、每项 94 dp；图标 24 dp、文字 11 sp，上下排列。
- 右下筛选：64 × 64 dp，显式 `CircleShape`，图标 24 dp，无文字。
- 默认深灰玻璃表面 alpha 0.4；Tab blur 2 dp（上游 8 dp）、lens 12/24 dp（上游 24/24 dp），按钮 blur 2 dp、lens 12/24 dp。
- 两者统一底部对齐，适配系统导航栏和状态栏安全区。
- 设置页提供玻璃深浅滑块（表面 alpha 0.25–0.55）与触感开关，便于后续调试。

## 边界

本轮为原生静态数据 mock，6 张照片打包在 APK 中；没有网络请求、账户接入或照片权限。搜索、收藏筛选、排序、相册、单图浏览和设置均使用内存状态。重启进程不保证保存设置，后续接后端时替换数据层即可。

本地素材与选定设计稿的照片内容不同，保持便当盒结构和照片题材；素材来源见 `photo-sources.md`。

图标使用 AndroidX Material Icons 1.7.8。仅将所需的 PhotoLibrary、CollectionsBookmark、Tune 官方向量源文件提取到 app/ui/icons，并保留文件内 Apache-2.0 声明，避免打包整个扩展图标库。

## 原版 demo 对照修正（当前版本）

实际安装并运行仓库随附的 Android demo APK，通过 UIAutomator 检查 Tab 点击、拖动，以及 Buttons、Glass playground 页面。对照截图在 `original-demo/`。

此前自定义的 86% 深灰覆盖、选中块额外的纯色 backdrop，以及被削弱的高光和折射会遮住原版效果；现已撤销这些修改。恢复原版 blur / lens 参数、默认高光、页面与 Tab 内容的 combined backdrop，保留按压色散、速度拉伸和松手弹簧。

保持 Tab 196×64 dp、筛选正圆 64×64 dp、无彩色图标和现有便当盒布局。整排底部无固定遮罩，只有控件自身采样照片并应用玻璃效果。设置滑块仅调整表面深浅。

当前截图在 `glass-restored/`：`glass-over-photos.png` 在照片细节位于控件下方时检查静止模糊与边缘折射；`tab-pressed.png`、`filter-pressed.png` 检查按压状态。旧的 `runtime/`、`refined/`、`transparent-bottom/` 仅为历史版本。

## Tab 静止态专项检查

逐行 diff 确认：上一版 Tab 的渲染与上游一致，差异为固定暗色、白色强调色及可调表面 alpha；调用处为两项 196 dp 胶囊，demo 为三/四项宽胶囊和墙纸背景。

原版 Tab 外壳始终 lens 24/24；隐藏采样行的 lens 为 24/24 × pressProgress；选中透镜为 10/14 × pressProgress。松手时后两者关闭折射，选中块还叠加 10% 白色。圆按钮则始终 blur 2、lens 12/24，不能预期相同观感。

当前明确偏离上游的调整：外壳及隐藏行 blur 从 8 改为 4 dp；隐藏行保留 24/24 基础折射，避免静止选中区域重新采样无折射的背景。选中透镜本身仍采用原版按压进度与色散动效。上游快照未修改。

`tab-refraction/before.png` 与 `glass-over-photos.png` 为同位置前后截图，`tab-over-photos-pressed.png` 为按压截图。构建及两个 HomeMockTest 通过。

## 静止选中透镜修复（最新）

Tab 外壳和隐藏采样行统一采用与圆筛选相同的 blur 2 dp、lens 12/24 dp。选中透镜不再在松手时关闭：静止 lens 4/6 dp，按压插值到原版 10/14 dp，保留色散与阻尼拖动。选中表面白色覆盖从 10% 降至 4%，静止高光为 0.25，按压增至 1。尺寸与整排透明布局不变。

最新截图在 `glass-final/`，旧参数记录作为历史保留。

## 顶部玻璃控件

头像、搜索、设置改为 64 dp 圆形 LiquidButton，与筛选共享 blur 2 / lens 12、24 和玻璃深浅设置。头像为 44 dp，留出玻璃边缘；工具栏作为独立悬浮层采样列表，避免把玻璃本身录入 backdrop。顶部工具栏固定，标题和照片继续滚动。截图见 `top-glass/`。

顶部与底部统一 64 dp 控件高度；搜索、设置、筛选图标统一 24 dp。顶部工具栏占位同步增至 68 dp。HomeMockTest 验证三个顶部按钮均为 64×64 dp。

## 屏幕圆角与边距

使用 WindowInsets.getRoundedCorner 的圆心，以控件半径 32 dp 求边距。每排取两端所需边距最大值，水平与垂直采用同一数值；上下排分别处理。若系统栏/切口加 8 dp 安全距离更大，则向内退让；无圆角数据时以 20 dp 最小边距与安全区计算。因安全区、非对称圆角或圆角过小，不能保证所有设备严格同心。顶部占位同步跟随计算值，去除原先额外叠加的系统栏 padding。

实现参考：https://developer.android.com/develop/ui/views/layout/insets/rounded-corners
截图：`corner-alignment/home.png`。构建与两个交互测试通过。

## 紧凑边距修正（最新）

上一版把垂直安全区最大值用于水平边距，导致顶部过度内缩，现已移除该算法。对照用户提供的苹果相册截图采用紧凑边距：左右 16 dp（横向系统遮挡更大时独立避让），上下为各自 safeDrawing 安全区加 4 dp。此数值为本项目设计选择，并非苹果公开规格。保留 64 dp 控件尺寸，不再强制屏幕与控件严格同心。采用 Compose WindowInsets 更新，移除逐帧读取圆角的监听器。

最新截图 `compact-edges/home.png`；构建及两个交互测试通过。

## 图库与相册独立布局（当前版本）

移除头像入口；图库右上角为筛选，相册右上角为设置，均 48 dp / 22 dp 图标。右下角为当前页面搜索，分别保存照片与相册关键词，切换页面不会互相覆盖；两个页面各自持有 LazyListState。相册使用两列 1:1 圆角方形封面，名称和数量位于封面下。

底部默认高度 64 dp、Tab 宽 196 dp；向上拖动累计超过 20 dp 后以弹簧动画收缩至高度 48 dp、宽 148 dp，图标从 24 缩为 22 dp，文字高度与透明度降至零。向下拖动超过阈值后恢复；切换页面也恢复。手势仅观察用户滚动，不消费位移。Tab 拖动实时读取动画中的项宽，避免收缩后命中错位。保持原版玻璃按压与拖动弹簧。

截图：`pages-v2/home.png`、`albums.png`、`collapsed.png`、`expanded.png`、`album-search.png`。HomeMockTest 验证页面入口、独立搜索、方形比例、上下滑收缩恢复及收缩态拖动切换。
