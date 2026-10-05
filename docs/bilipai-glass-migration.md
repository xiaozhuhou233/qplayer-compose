# BiliPai 玻璃参数迁移（2026-10-02）

本记录替代上一轮 `adaptive-glass-apk-audit.md` 的当前材质说明。初次迁移时，用户要求改用下载目录的 BiliPai-main，并强调参数原样照搬。之后用户明确要求减弱描边，认为原来的白边过强、像塑料；当前高光强度已按下述“描边减弱”一节调整，其余光学参数继续保留迁移基线。

后续变更：用户要求将 iOS Dialog 恢复到迁移前备份。Dialog 及其内部控件已单独恢复旧材质，导航栏、MiniPlayer 和页面上的其他玻璃仍采用 BiliPai 材质，并使用本轮减弱后的描边。Dialog 不受本轮参数调整影响。详见 `ios-dialog-restore.md`。

## 基线与备份

- 输入：`C:/Users/xiaoz/Downloads/BiliPai-main.zip`。
- ZIP SHA-256：`1BE18217134201B80364265EA90DFA4B17A415CC154C7F5EF49F013EC52CBD32`。
- 修改前备份：`backups/glass-before-bilipai-20261002-163859/current-glass-source.zip`，471462 bytes。
- 备份 SHA-256：`0951B6D85BA12245F1384386C8D2B1F9603AD6EFF53C413C0C21503ABF11A035`。
- 本地参考目录：`android-shell/app/build/bilipai-reference/`。
- 未操作 Git，未修改桌面构建脚本，不覆盖其他并行功能修改。

## 采用哪一套参数

采用源码 `SettingsManager.kt` 与 `LiquidGlassTuning.kt` 的默认配置：

- `BILIPAI_TUNED`，`BALANCED`，`progress=0.5`。
- 进阶参数：可读性 `0.62`、色散控制 `0.56`、内容扭曲 `0.45`。
- 默认可读性模式为 `STABLE`，不是另一个可选的 `ADAPTIVE` 模式。

沿 `BottomBar.kt -> FloatingBottomBar.kt` 核对真实参数消费者，不把其它预设、未消费的调参字段或其它组件的默认值混合起来。

## 迁移时的原版参数对照

下表保留原始迁移基线；当前描边强度的覆盖值见后文，其余参数继续生效。

| 参数 | 原版值/公式 | 原始位置 |
| --- | --- | --- |
| 亮度 / 对比度 / 饱和度 | `0 / 1 / 1.5` | `liquid/Vibrancy.kt` |
| 背景模糊 | `4dp` | `LiquidGlassTuning.kt` 的平衡锚点 |
| 表面颜色 | 浅色白；深色 `#242424` | `BottomBar.kt: resolveBiliPaiBottomBarContainerColor`、`HomeVisualPalette.kt` |
| 表面 alpha | `0.40` | `LiquidGlassTuning.kt` |
| 可读性保护 | `protection=0.62⁴`；`boost=0.6*protection` | `LiquidGlassTuning.kt` |
| 可读性薄层 | `boost*(0.12+protection*0.22)`；浅白/深黑 | `FloatingBottomBar.kt` |
| 外壳折射高度/量 | `24dp / 24dp`，depth=false，色散=0 | `FloatingBottomBar.kt`、`liquid/Lens.kt` |
| 矮组件缩放 | `clamp(heightDp/64, 0, 1)` | `FloatingBottomBarGeometry.kt` |
| 外壳采样余量 | `(24+16)*geometryScale` dp | 同上 |
| 指示器折射高度/量 | `10dp / 14dp` × 按压进度，depth=true | `FloatingBottomBar.kt` |
| 指示器色散 | `0.5`，RGB 三次采样 | `LiquidGlassTuning.kt`、`liquid/Lens.kt` |
| 外阴影 | 半径 `10dp`，偏移 `0`，黑色；浅色 alpha `0.1`、深色 `0.2` | `FloatingBottomBar.kt` |
| 指示器内阴影 | 半径 `8dp*p`、默认向下偏移相同半径、黑色 alpha `0.15`、整体 alpha `p` | `FloatingBottomBar.kt`、`liquid/InnerShadow.kt` |
| 高光模型 | `BloomStroke`，`dualPeak=true`，Plus | `FloatingDockChrome.kt` |
| 外壳高光 | 宽 `1dp`、alpha `0.75` | 同上及 `FloatingBottomBar.kt` |
| 指示器高光宽度 | `1+clamp((width/height-1.35)/8,0,1)` dp | `FloatingBottomBarGeometry.kt` |
| 高光细边 / 内光晕 | 白色 alpha `0.12` / `2dp` | `FloatingDockChrome.kt` |
| 主光源 | 初始 `(0.5,-0.3,-0.05)`，白，强度 `1` | 同上 |
| 次光源 | `(0.5,0.8,-0.5)`，白，强度 `0.4` | 同上 |
| 设备倾斜 | 平滑 `0.15`、方向量化 `3°`、零方向阈值平方 `0.01` | 原项目及锁定的 Miuix sensor |
| 主光源方向 | 中心 `(0.5,0.7)`，外壳旋转 `-45°`、指示器 `+90°` | `FloatingDockChrome.kt` |
| 文字 | 默认主题 onSurface、对比度保护 `3:1`、过渡 `240ms` | `LiquidGlassAdaptiveReadability.kt` |

例如：64dp 底栏外壳使用24/24；48dp MiniPlayer 为18/18；44dp 小按钮为16.5/16.5。这是参考包原有比例公式，不是自行削弱参数。

调参对象中虽有 `whiteOverlayAlpha=0.04`，但此浮动底栏的绘制链没有消费它；本次同样不额外叠加。页面顶部渐进模糊不是组件材质，不顺带改写页面布局和顶部区域。

## 描边减弱（2026-10-02）

按用户本轮要求，减弱包围整个组件的均匀白边，同时保留随设备倾斜移动的双光源高光。调整沿 `rememberBiliPaiGlassHighlight` 的真实绘制链生效，参数仍集中在 `BiliPaiGlassParameters.kt`。

| 参数 | 原版 / 调整前 | 当前值 |
| --- | --- | --- |
| 外壳高光整体 alpha | `0.75` | `0.55` |
| 导航指示器高光整体 alpha | `1.00` | `0.75` |
| 均匀白色细边 alpha | `0.12` | `0.08` |

BloomStroke 着色器会将细边贡献再乘整体 alpha，因此外壳细边的系数从 `0.75×0.12=0.09` 降至 `0.55×0.08=0.044`，指示器从 `1×0.12=0.12` 降至 `0.75×0.08=0.06`，均约减半；双光源高光的整体贡献分别降低约 27% 和 25%。这是着色器系数变化，不是对所有画面像素亮度的测量。

外壳 `1dp` 描边、指示器 `1–2dp` 比例公式、`2dp` 内光晕、主次光源强度 `1 / 0.4`、重力方向及 `3°` 量化保持不变。折射、色散、模糊、表面颜色和阴影同样保持。本轮作用于全局 BiliPai 导航、MiniPlayer、主页/搜索按钮和页面普通玻璃控件；独立恢复的 iOS Dialog 及其内部控件继续使用旧材质。

- 调整前源码与参数测试备份：`backups/glass-outline-before-20261002/before-outline-softening.zip`。
- 备份 SHA-256：`95F6516827CEDF549A7914C85C253CA5E266B9470B7583E2C218A75CC37C36A6`。
- `Test-GlassMotion.ps1` 已通过：当前描边参数、720 个重力方向、双光源结构、弹簧和既有颜色/折射/采样回归。
- `Test-IosDialogRestore.ps1` 已通过：Dialog 原材质逐字比对及弹窗内外作用域隔离。

## 代码接线

- `BiliPaiGlassParameters.kt`：参数和默认可读性公式的单一来源。
- `IosGlassPalette.kt`：外壳和指示器分离；指示器不再重复过滤已捕获的外壳/文字。
- `effects/BiliPaiLens.kt`：原样移植 `liquid/Lens.kt` 的三段 AGSL；仅做 QPlayer Backdrop 接口适配。
- `BiliPaiBloomStroke.kt`、`BiliPaiBloomShader.kt`：移植 BiliPai 锁定的 Miuix `5c91d5e5` 双光源实现。
- `HighlightModifier.kt`：BloomStroke 使用原来的全矩形 SDF 绘制，不套入旧轮廓描边/模糊层，避免裁掉内光晕。
- `BiliPaiGlassHighlight.kt`、`BiliPaiDeviceTilt.kt`：保留原重力方向与光源模型，使用本轮减弱后的高光 alpha；应用范围共用一个传感器监听，页面销毁/停止时解除监听。
- `IosAdaptiveGlass.kt`：按原版默认 STABLE 模式，移除上一轮 APK 的5×5亮度采样及1000ms材质反馈；保留原版文字对比度保护。
- 现有导航、MiniPlayer、搜索/主页圆钮、页面普通玻璃按钮、滑块、开关接到同一外壳材质。导航的移动指示器单独接指示器材质。iOS Dialog 及其内部控件已独立恢复旧材质，不使用本轮描边参数。

保留 QPlayer 的布局、业务按钮语义、现有拖动/收缩动画和折射开关；不会为了迁移材质把界面替换成视频软件的布局。

## 来源与适配边界

`gradle/libs.versions.toml` 锁定 `miuix=0.9.4-5c91d5e5-SNAPSHOT`，因此从公开上游同一提交核对并取回 BloomStroke 和 sensor 源码，而不是用最新版本替代。

BiliPai 仓库附带 GPL-3.0 许可证，保存在 `assets/licenses/BiliPai-GPL-3.0.txt`。其 Lens 文件与依赖 Miuix 文件分别明确标记 Apache-2.0；新移植文件保留作者和许可证标识。

QPlayer 仍使用现有 Backdrop/Android RenderEffect 底层，未整体升级 Miuix 或 Compose。Lens 的尺寸/半径/折射在此后端以 downscaleFactor=1 传递，数值按同一物理尺寸对应。除明确列出的描边强度调整外，保留迁移时的光学参数和着色器实现；不据此承诺不同渲染后端、设备和布局逐像素相同。

## 初次迁移时的验证记录

以下 APK、构建时间与安装状态对应初次迁移，不能代表后续描边调整后的构建或设备状态。

- 新增 `BiliPaiGlassCheck.kt`：默认参数、矮控件比例、表面/薄层、阴影、240ms文字、720个重力方向及双峰高光检查通过。
- 三段 Lens AGSL 与 ZIP 原文逐字对比通过（仅规范化换行）。
- BloomStroke shader builder 与锁定 Miuix 源码逐字对比通过。
- 原有拖动弹簧及历史材质回归仍通过；历史 APK 检查不代表当前材质仍采用 APK 配方。
- 桌面原脚本 `C:/Users/xiaoz/Desktop/Build-QPlayer.ps1` 构建成功：`BUILD SUCCESSFUL in 3m 1s`，36 tasks（8 executed / 28 up-to-date）。脚本未修改。
- 首次构建的 Gradle JVM 因 native memory allocation 失败退出。重试仅在子进程环境中设置 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2`，限制构建线程规模；未修改项目构建配置、应用参数或全局环境。
- 最终 APK：`android-shell/app/build/outputs/apk/debug/app-debug.apk`，2026-10-02 16:56:28（Asia/Shanghai），192985505 bytes。
- APK SHA-256：`975E33311C48F1C8A5B956330AD93D1ED304A3686ECF7D449F9D598417C75EA9`。
- `apksigner verify --verbose` 通过，v2 签名有效；ZIP 中包含 `assets/licenses/BiliPai-GPL-3.0.txt`（35149 bytes）。
- 自动安装未完成：ADB 报 `Cannot mkdir '\\.android': Permission denied`。脚本随后显示的 “No Android device found” 不能证明手机未连接，因为 ADB 本身未正常启动。
- 尚未完成真机视觉和 AGSL GPU 运行验收；Kotlin 编译、参数回归及源码一致性不等于逐像素效果已验证。
