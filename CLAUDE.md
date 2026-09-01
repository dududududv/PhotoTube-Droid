# PhotoTubeDroid 项目记忆

PhotoTubeDroid 是一个使用 Kotlin 与 Jetpack Compose 开发的 Android 16 原生相册客户端。高保真 UI Mock 已作为视觉基线保留，当前里程碑是按后端稳定契约逐步接入真实功能。

## 开始开发前必读

- 产品范围：@docs/product/README.md
- 架构与依赖规则：@docs/architecture/README.md
- 视觉系统与还原目标：@docs/design/README.md
- 构建与开发流程：@docs/development/README.md
- 验收清单：@docs/testing/README.md

## 不可破坏的规则

- `MainActivity` 只负责 Android 生命周期、系统栏和 Compose 装配；页面代码必须放在 `ui/` 下。
- UI 使用不可变模型，并通过 ViewModel / Repository 读取数据；禁止在页面 Composable 中直接调用 Retrofit 或拼装全局演示数据。
- 未完成远端替换的页面可以暂时保留确定性 Mock，但必须在功能文档中明确，不得把 Mock 状态描述成真实后端能力。
- 整个项目以 Android 16（API 36）为唯一运行基线；`minSdk`、`targetSdk` 和 `compileSdk` 保持为 36，不为旧版本编写兼容分支。
- 可以直接使用 API 36 图形、系统栏、预测返回和窗口能力；新增 Android 16 特性时必须记录到架构或设计文档。
- 新建重复组件前，先复用 `DesignTokens.kt` 和 `ui/components/` 中的共享组件。
- 保持沉浸式边到边布局，正确处理状态栏与导航栏安全区。
- 实现代码变更后必须运行 `:app:assembleDebug`；JDK 命令参见 @docs/development/README.md。
- 公共流程、依赖方向、设计令牌或验收规则发生变化时，必须同步更新对应文档节点。
- 后端字段和状态码以 PhotoTube 仓库的 `contracts/openapi/` 为准；AI 打标以及单图标题、描述、GPS、时间编辑当前禁止接入。

## 文档规则

- 根目录 `CLAUDE.md` 必须简洁，只保存项目级强约束和文档入口。
- 持久性说明放进对应的 `docs/<主题>/README.md`。
- UI 子树专属规范放在 `app/src/main/java/com/yunai/phototube/ui/CLAUDE.md`。
- 文档默认使用中文；代码标识符、命令、路径和设计稿中的原始文案保留原格式。
- 规则必须给出具体命令、路径或可测量数值，避免“保持优雅”等无法验证的模糊表述。
