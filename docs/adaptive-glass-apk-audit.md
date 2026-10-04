# Adaptive Luminance Glass：APK 参数核对与迁移

日期：2026-10-02

## 用户确认的范围

先备份当前实现，再采用所给 APK 的 Adaptive Luminance Glass 原版材质参数。
纯白背景下对比度降至 0、明暗和字色均过渡 1000ms，是本次明确选择的行为。
不再混入之前 QPlayer 的白底透字保护、额外白色薄层、中心凸面或底部内环阴影。
保留 QPlayer 组件布局、导航及 MiniPlayer 收缩、拖动弹簧、触摸光效和语义状态。
本次未操作 Git，也未修改桌面的构建脚本。

## 修改前备份

`backups/glass-before-apk-20261002-155119/current-glass-source.zip`

- 备份包含 `android-shell/app/src/main/java` 和 `android-shell/glass-motion-tests`，共 107 个条目。
- 大小：465109 bytes。
- SHA-256：`229C012810B089DB94FAFAACE0143DB6E4D7094AF828D9A47D0AC6D92523F508`。
- 如需回退，只提取本次涉及的玻璃文件；不要整包覆盖其他同时进行的功能修改。

## 参考 APK 与证据

- 输入：`C:/Users/xiaoz/Downloads/base(1).apk`。
- 包名：`com.kyant.backdrop.catalog`，版本 `1.0.0` / `1`。
- 大小：2422031 bytes。
- SHA-256：`75560A091AC59DE6ED448C8BFD039D7A9A4C3DBD46E2771A45CB6DB4A4095F50`。
- 工具：SDK 35.0.0 的 aapt、dexdump，以及 JADX 1.5.6。
- 本地分析目录：`android-shell/app/build/reference-glass-75560a09/`。
- JADX 共报告 60 处反编译错误，不能视为完整源码恢复。目标材质方法可读；采样协程的缺失部分已用 DEX 字节码核对。
- 下载目录内 `AndroidLiquidGlass-android.zip` 的 `AdaptiveLuminanceGlassContent.kt` 用作辅助，其核心参数已与 APK 逐项核验，而非只依据这个源码包推测。

| 项目 | APK 核对位置（相对于 `jadx/sources`） |
| --- | --- |
| 明暗、对比度、模糊、折射参数 | `u3/c.java`，`g()` 的 case 0 |
| 饱和度与颜色矩阵 | `w3/a.java` |
| 字色阈值与动画时间 | `u3/a.java` |
| 5×5 采样、平均、顺序等待 | `dexdump.txt` 中 `c0.f2.k`，约 157250–157420 行 |
| 完整材质调用、Plain 高光 | `u3/h.java`、`u3/b.java`、`x3/a.java`、`x3/g.java` |
| 默认外阴影、无内阴影/表面层 | `q3/c.java`、`y3/f.java` |
| 折射着色器与 uniform | `w0/g.java`，`h()` |

## 原版参数

### 采样及文字

原背景缩小为 5×5（原 APK 使用 `filter=false`），对全部 25 个像素取平均。
以编码 RGB 计算 `L = 0.2126 R + 0.7152 G + 0.0722 B`，不做 sRGB 线性化，不排除 alpha=0 的像素。

- `L > 0.5`：黑色；否则白色。
- 亮度与文字颜色均为 `tween(1000)`，使用默认 easing。
- 等待这一轮亮度动画完成，再采下一次；不高频取消重启 tween。
- 初始亮主题 L=1、暗主题 L=0；首次有效采样后按真实背景过渡。
- 每块玻璃读取自身边界下的背景，取消此前整个底部区域共用一次采样的逻辑。

### 颜色与模糊

设 `x = 2L - 1`，`s = sign(x) * x²`：

```text
brightness = s > 0 ? lerp(0.1, 0.5, s) : lerp(0.1, -0.2, -s)
contrast   = s > 0 ? lerp(1, 0, s) : 1
saturation = 1.5
blurDp     = s > 0 ? lerp(8, 16, s) : lerp(8, 2, -s)
```

| L | brightness | contrast | blurDp |
| --- | --- | --- | --- |
| 0 | -0.2 | 1 | 2 |
| 0.25 | 0.025 | 1 | 6.5 |
| 0.5 | 0.1 | 1 | 8 |
| 0.75 | 0.2 | 0.75 | 10 |
| 1 | 0.5 | 0 | 16 |

因此 **纯白采样下底层细节归白是原版行为**，不是迁移时的保底遮罩。

### 折射、高光、阴影

- 折射高度：24dp。
- 折射量：组件较短边像素尺寸的 1/2；传给 shader 的 uniform 为其负值。
- depthEffect=true，chromaticAberration=false。
- 原版圆弧曲线：`1 - sqrt(1 - x²)`。
- 深度法线：边缘 SDF 法线与中心方向法线相加后归一化。
- 没有旧 QPlayer 的位移上限、浅圆弧曲线、中心凸面、色散。
- Plain 高光：宽 0.5dp、模糊 0.25dp、alpha 1，白色 alpha 0.38，Plus 混合。
- 外阴影：半径 24dp、偏移 `(0, 4dp)`、黑色 alpha 0.1。
- 无 innerShadow，无额外白色表面填充。

## 接入位置

- `AndroidGlassColorModel.kt`：纯亮度/参数模型。
- `IosAdaptiveGlass.kt`：组件边界采样、原版时间与黑白字色。
- `IosGlassPalette.kt`：统一颜色/模糊/折射、高光及阴影。
- `effects/Lens.kt` 与 `internal/Shaders.kt`：独立 `ApkAdaptiveRefraction` shader，旧 lens API 保留兼容，但已迁移的组件不再走旧材质。
- `IosLiquidGlass.kt`：顶部/播放器通用玻璃，MiniPlayer 和搜索/主页按钮也接入此路径。
- `LiquidBottomTabs.kt`、`LiquidButton.kt`、`LiquidSlider.kt`、`LiquidToggle.kt`：导航、按钮、滑块与开关。
- `IosDialogs.kt`、`IosControls.kt`：对话框与其中按钮的窗口坐标采样、字色，避免主题色覆盖采样字色。
- `IosPlayerDock.kt`：移除不再使用的隐形整区域采样节点，不变更布局计算。

## 有意保留的工程适配及边界

1. 原示例 160dp 方框、24dp 圆角属于演示布局，不移植为应用所有控件的尺寸。
2. 仍保留已有选中标记、强调按钮颜色、按压/拖动光效。导航选中透镜的进入/退出继续使用原有进度值调制折射，完整显现时使用上述参数。这些是交互状态层，不是新增基础白色填充。
3. 折射设置关闭时仍跳过 lens；低版本 Android 保留能力降级，对话框在不支持 RenderEffect 时保留可读背景，不能宣称这类设备与原版渲染一致。
4. 保留生命周期暂停、离屏跳过、运动期间采样暂停、全局串行 readback 和失败退避，避免同时读取多个全尺寸背景。
5. QPlayer 直接把背景绘制到 5×5 的 GPU 缩略图，保留控件的逻辑绘制尺寸。APK 则先得到 bitmap 再用 nearest 缩小；栅格过滤和亚像素边界可能存在细微差别，因此不是“像素级一致”保证。
6. 原 AGSL 在小控件中心会出现零向量归一化奇点。迁移仅添加零向量和 sqrt 舍入保护；正常点仍采用原公式，不添加强度钳位。
7. 保留 Apache-2.0 归属和现有许可证说明。

## 验证

`android-shell/glass-motion-tests/Test-GlassMotion.ps1` 已通过：

- 原有拖动物理弹簧回归。
- 原 APK 数值端点、1001 个亮度等级、5×5 RGB 采样、阈值、1000ms 常量、无附加白层/内阴影。
- 新原版折射的 367236 个坐标有限性检查；366984 个非奇点与无保护原公式一致，覆盖 6 个密度和 6 种控件尺寸。
- 旧镜片兼容性回归仍保留，**不能把它的 1399464 次检查当作新材质验证**。
- 采样策略边界、失败退避、全局预算及 1000ms 顺序采样模拟。原测试里过时的 120/480ms 假定改为检查实际策略常量，没有为通过测试倒改生产策略。

以上为 CPU 模型、源码检查和编译层验证，不等同于手机 GPU 渲染、长时间性能或视觉验收。

### 桌面原脚本构建结果

直接运行 `C:/Users/xiaoz/Desktop/Build-QPlayer.ps1`，未修改脚本：

- player-core Maven package：成功。
- Android `:app:assembleDebug`：`BUILD SUCCESSFUL in 2m 40s`，36 项任务。
- APK：`android-shell/app/build/outputs/apk/debug/app-debug.apk`。
- 修改时间：2026-10-02 16:09:37（本地时间）。
- 大小：192954772 bytes。
- SHA-256：`0B3F011752B1E1B0F2ADD121CD8D48E5D3B871FC5FBBEF268D493E55805FEE8F`。
- SDK 35.0.0 `apksigner verify --verbose`：通过，v2 签名有效，1 个签名者。
- 自动安装未完成：ADB 创建 `\\.android` 时权限不足。脚本虽随后提示未发现设备，但查询本身失败，不能据此认定手机未连接。
- 未进行真机视觉/性能验收，不将 CPU 公式测试与编译成功视为视觉等价证明。
