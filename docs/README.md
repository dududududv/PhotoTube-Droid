# PhotoTubeDroid 文档中心

本目录是项目的长期知识库。仓库根目录的 `CLAUDE.md` 通过 `@路径` 导入开发时必须立即掌握的规则；详细背景与决策下沉到这里，避免根级指令持续膨胀。

## 文档树

```text
docs/
├── README.md                 # 文档索引与维护责任
├── product/
│   └── README.md             # Mock 范围、页面状态与用户流程
├── architecture/
│   └── README.md             # 包结构、状态归属与依赖规则
├── design/
│   └── README.md             # 视觉尺寸、设计令牌和素材清单
├── development/
│   ├── README.md             # 本地构建与变更流程
│   └── backend-integration.md # 后端切片、缓存与契约实现状态
├── testing/
│   ├── README.md             # 功能、构建和视觉验收矩阵
│   └── android-16-e2e.md     # API 36 真服务会话、路径扫描与深页历史证据
└── decisions/
    ├── 0001-offline-mock-assets.md
    ├── 0002-lightweight-navigation.md
    └── 0003-android-16-baseline.md
```

## 文档维护责任

| 变更类型 | 必须更新的文档 |
|---|---|
| 新增或修改产品状态 | `product/README.md` |
| 包结构或依赖方向变化 | `architecture/README.md` |
| 间距、字体、形状或素材变化 | `design/README.md` |
| 构建环境或命令变化 | `development/README.md` |
| 验收标准变化 | `testing/README.md` |
| 需要长期保留的技术取舍 | 在 `decisions/` 下新增编号文件 |

文档同时服务于开发者和代码智能体：陈述事实、写明准确路径，并保证命令可以直接复制执行。
