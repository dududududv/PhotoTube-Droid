# 视频玻璃试验

入口：相册 → 设置 → 视频预览。默认静音循环，支持暂停/恢复、回到开头、关闭及重新进入；玻璃块可以拖动。

使用 Media3 1.9.3 ExoPlayer，XML 明确指定 PlayerView 的 texture_view，运行时检查 videoSurfaceView 类型。视频外层 layerBackdrop，AndroidView 使用 graphicsLayer alpha=0.999，玻璃以兄弟层采样。没有定时帧截图、Bitmap 读回、CPU 模糊或周期性重组驱动。背景暂停在生命周期 ON_STOP，退出释放播放器。

这是社区方案的本项目试验：https://github.com/Kyant0/AndroidLiquidGlass/issues/82 。0.999 是兼容 workaround，不是跨设备稳定性保证。

素材：https://samplelib.com/sample-mp4.html ，下载 https://samplelib.com/mp4/sample-10s-720p.mp4 。来源页面声明可下载且无许可限制。打包为 raw/glass_video.mp4，约 4.8 MB；预览以 zoom 裁剪填充屏幕，静音，不需要联网权限。正式播放器的宽高比切换、HDR、DRM 等不在本次试验范围。

验证：Android 16 API36 模拟器。HomeMockTest 两项通过；VideoGlassTest 通过：首帧、拖动、同位置玻璃内部两帧像素变化、暂停恢复、重新播放、关闭和重入。playing-1.png / playing-2.png 相隔约 1.5 秒，玻璃本身未移动，可观察内容变化；测试截图仅用于验证，未参与应用渲染。两张截图不能证明持续帧率、无掉帧或真机性能，仍需实机测试。
