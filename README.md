# PhotoTube-Droid

原生 Android / Jetpack Compose 相册，使用 AndroidLiquidGlass 本地源码实现玻璃控件、胶囊导航和手势动效。

## 当前开发边界

**仅限本地 Mock。** 应用使用进程内 `LocalMockData` 与随包示例媒体；禁止访问 NAS、真实服务、私有媒体及旧账号会话。请求层拒绝外部来源，测试只允许显式注入的本地隔离夹具。具体约束见 [AGENTS.md](AGENTS.md) 与 [本地 Mock 说明](docs/development/local-mock-only.md)。旧文档中的真实服务地址和操作说明均不再适用，不得执行。

## 当前界面

- 图库：按月展示照片与视频，筛选、搜索、选择和批量操作。
- 相册：首页展示相册封面与图内标题，加号与设置共用玻璃胶囊；不按“文件夹 / 智能相册”添加首页分类。
- 资料库管理入口位于相册右上角设置中。
- 详情：图片缩放、连续浏览、视频横向 seek、封面展开与返回过渡。
- 底部图库/相册胶囊与搜索按钮随纵向滚动收缩。
- 外观：系统/浅色/深色、主题色及玻璃参数，通过全局语义主题统一注入。

数据访问与管理模块保留已有代码，但当前运行和验证均受 Mock 边界限制。代码存在不表示对应真实服务流程已通过验收；产品功能范围以用户当前确认内容为准。

## 本地构建与验证

依赖已缓存时可离线编译：

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew --offline :app:assembleDebug :app:assembleDebugAndroidTest
```

目前只授权 `emulator-5560`（`PhotoTube_Mock_Audit`）。不得连接其他模拟器或真机，也不得启动可恢复真实账号会话的旧安装应用。构建产物仅用于授权模拟器验证，用户未要求时不交付 APK。

最近验证覆盖相册入口、详情返回、新建草稿、搜索位置恢复、深浅色及设置管理导航；尚未对全部历史 UI 用例重新回归。截图、录屏和测试日志保留本地，不纳入 Git。

## 源码结构

- `app/src/main/java/com/yunai/phototube/ui/`：主界面、查看器、管理界面、全局主题。
- `app/src/main/java/com/yunai/phototube/data/`：数据模型、Repository、本地 Mock 与请求边界。
- `app/src/test/`、`app/src/androidTest/`：单元测试、合成夹具与隔离 UI 测试。
- `libraries/liquid-ui/`：基于上游 demo 的玻璃控件。
- `libraries/backdrop/`：Android 构建适配。
- `third_party/AndroidLiquidGlass/`：上游源码快照，保留 Apache-2.0 许可证。

后续每完成一块可验证改动，及时提交并推送；不使用强制推送覆盖远端。
