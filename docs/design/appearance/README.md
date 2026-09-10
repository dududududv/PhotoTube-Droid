# 外观子设置与滑块同步修复

入口：相册 → 设置 → 外观。设置主页面仅保留外观入口，子页面提供图片上的实时玻璃预览。

参数：表面 alpha 0.1–0.7、模糊半径 0–24dp、折射高度 0–24dp、折射强度 0–48dp、色散开关。恢复默认为 0.4 / 2 / 12 / 24 / 关闭。通过 LocalGlassAppearance 传递给 LiquidButton、LiquidBottomTabs，视频按钮表面 alpha 也跟随设置。Tab 选中层折射随参数按比例调整，设为零时关闭对应折射。调整用的滑块与开关保持上游材质，避免极端参数妨碍操作。

LiquidSlider 原先的 LaunchedEffect 捕获初始 value 回调，当调用者传入普通 Float 参数时，数字更新而内部动画仍读取旧回调。使用 rememberUpdatedState 获取最新 value、onValueChange 与轨道宽度。LiquidToggle 的拖动释放回调同样改为读取最新 selected / onSelect，保证连续触摸切换。

验证包含：无障碍设置数值后手柄实际位置变化、触摸拖动后手柄位移、色散连续触摸开关、恢复默认与返回重入；另跑图库相册和视频玻璃回归。仍采用 rememberSaveable，不新增后端同步或磁盘持久化。
