# Android 性能修复（2026-10-02）

## 现场与备份

- 用户报告：SM8750 / 8 Elite 播放时约 60% CPU，首页也卡，低端机更严重。
- 真机：ADB 读取型号 `SM-S9360`、SoC `SM8750`。本轮未操作 Git，未切分支，未修改桌面构建脚本。
- 修改前源文件备份：`backups/performance-before-20261002-195204/before-performance.zip`。
- 备份 SHA-256：`1C8A2254F1FD69ADD7C537B0A6CFD092E0E973437BA12B8F83D332B8982F9149`。
- 旧版设备包：`dev.t1m3.qplayer.debug`，versionCode 67 / 1.3.0-debug，设备记录安装时间 2026-10-02 19:16:41。不是把磁盘上 19:27 的 APK 当成已安装版本。

## 已定位并修复

### 播放时钟使整个页面状态持续变化

`controllerState` 原先每 100ms 把播放位置、歌词位置、`System.nanoTime()` 放进包含首页、曲库、主题等数据的 `PlayerUiState`。暂停时仍每 400ms 写入新时间戳，因此始终不相等。

- 将高频字段放进独立的 `PlaybackUiClock`；`PlayerUiState` 保留同一个时钟引用，其结构相等判断不再受每次时钟采样影响。
- 歌词行号改为消费端根据时钟计算，避免每句歌词变化使首页刷新。
- 时钟与低频数据在同一个 mutable snapshot 中发布；暂停时忽略纯时间戳变化，保留 seek、歌词偏移和播放修订号变化。
- 进度读数下沉到独立 composable；插值在绘制中读取，不再让整个播放详情与时间文字逐帧重组。
- 展开动画的“是否正在运动”使用派生布尔状态，避免根节点订阅每帧展开数值。

### 不可见区域仍有视觉工作

- Activity 不可见时停止生成视觉快照；音频 `pump` 保持原节奏，以保留后台播放、切歌与过渡检测。
- 展开播放器遮住首页后，通过 `LocalUiRenderingActive` 停止隐藏首页的 MiniPlayer 圆环、跑马灯和均衡器动画。
- 正在使用的歌词列逐帧时钟受生命周期约束；歌词项只把共享时钟交给绘制，不再逐项订阅 100ms 位置。
- 动态背景 View 仅在封面、颜色、活动状态真正改变时 invalidate；单一待执行帧回调，在窗口隐藏、页面停止、View 分离时撤销。未调整背景视觉参数。

### 重复绘制与图片内存

- 波形进度条将逐帧数百条 `drawLine` 和三角函数运算改成缓存 Path，按进度裁剪绘制两次。
- MiniPlayer 标题增加独立 `graphicsLayer`，将跑马灯绘制与外层玻璃/展开快照分隔；保留滚动文字。下面记录的是整套修复的效果，不能把全部降幅归给这一层。
- 图片解码先读取尺寸，按实际用途采样：列表/小播放器上限 256px，详情默认上限 1024px，尺寸纳入缓存键。
- 同时最多两个封面解码任务；等待后再次查询原有 48MB 缓存，避免快速滚动时 IO 线程同时解码大量原图。
- 玻璃倾斜依旧使用 BiliPai 原有平滑和 3° 量化，只在最终光照方向变化时发布状态，避免传感器噪声广播到所有玻璃观察者。

### 不需要的音频分析

- 缓存歌曲的预加载在智能过渡关闭时不再启动静音/节拍探测；节拍预热也尊重节拍对齐开关。
- 静音探测在工作线程先读取磁盘缓存，避免每次进程启动后重新解码已有结果。
- 已排队但未开始的探测在启动前再次检查智能过渡开关。
- 静音分析结果先发布到缓存，再移除 in-flight 标志，消除并发重复请求的窗口。
- 未修改音频采样、解码质量、AI 过渡规则、歌词时序或 BiliPai/iOS Dialog 材质参数。

## 验证

- `mvn -pl player-core -Dtest=PlayerControllerProfileWorkTest,PlayerControllerPlaybackTest test`：33 项通过；其中新增 4 项验证禁用、已有磁盘结果、正常生成/去重，以及排队期间关闭再启用。
- `player-core install` 已成功，保证本地 Maven 仓库更新。
- `android-shell/performance-tests/Test-PlaybackUiClock.ps1`：使用实际编译的 `PlayerUiState` 及 Compose SnapshotStateObserver。600 次播放采样：根状态观察者失效 0 次、进度观察者更新 600 次；150 次暂停采样：两者均不更新；暂停 seek、歌词偏移、修订号仍可通知消费者。这是状态观察测试，不是整机 CPU 百分比。
- `Test-LyricWordLift.ps1`：30/60/90/120Hz、十分钟时钟、暂停/恢复、跳转、变速、逐字边界及动画组合回归通过。
- `Test-GlassMotion.ps1`：BiliPai 参数、弹簧、采样及折射回归通过。
- `Test-IosDialogRestore.ps1`：Dialog 与恢复备份对比和作用域隔离通过。

## 真机测量

CPU 数据来自 Android `top`，100% 表示一个逻辑核满载，8 核汇总上限 800%；不能把这些数值直接解释成整台手机所有核心的百分比。

旧版由用户确认在首页播放、不操作后采样：6 个进程 CPU 样本为 78.5、65、68.5、48.5、63、92，丢弃首个样本后均值 **67.4%**，范围 **48.5%–92%**。同期线程样本主要消耗在主线程（39%–60.7%）与 RenderThread，静音/节拍/预缓存线程在该段基本空闲。

旧版这段帧统计：968 帧，91 个 janky frames（9.40%），P50/P90/P95/P99 为 5/28/36/48ms，slow UI thread 91 次。原始诊断文件保存在 `.tmp/cpu-before-home-process.txt`、`.tmp/cpu-before-home-threads.txt`、`.tmp/gfx-before-home.txt`。

### 最终版首页稳态对照

安装后由用户选回《Brightest Lights (feat. POLIÇA)》，确认播放，并滚动到精简底栏；保留长标题跑马灯和玻璃效果。使用与旧版相同的 `top -b -n 6 -d 2 -p <pid>`，丢弃首样本；在采样前 reset gfxinfo，采样后读取帧统计。

最终版 6 个 CPU 样本为 **14.8、26、45.5、43.5、43、21**，稳态均值 **35.8%**，范围 **21%–45.5%**。相对旧版这次采样的 67.4%，均值下降 **46.9%**。

| 指标 | 旧版首页播放 | 最终版首页播放 |
| --- | ---: | ---: |
| CPU 均值（丢弃首样本） | 67.4% | 35.8% |
| 渲染帧数 | 968 | 980 |
| Janky frames | 91 / 9.40% | 16 / 1.63% |
| P50 / P90 / P95 / P99 | 5 / 28 / 36 / 48 ms | 5 / 6 / 9 / 12 ms |
| Slow UI thread | 91 | 13 |
| Slow issue draw commands | 80 | 2 |

原始数据：`.tmp/cpu-final-home-steady-process.txt`、`.tmp/gfx-final-home-steady.txt`。最终版前一段线程采样 `.tmp/cpu-final-home-threads.txt` 中主线程约 17%–29%、RenderThread 6%–16%，静音、节拍和预缓存线程已空闲。首页仍有实际的跑马灯/玻璃绘制开销，未声称达到零负载。

以上是同一台手机、同类首页播放场景的短时前后对照，并非锁定温度、频率、歌曲位置、缓存与队列的实验；旧版截图与新版同名歌曲的封面也不同。未测低端机、全曲库或所有皮肤，不能把 46.9% 当作所有设备的保证。

### 后台、暂停和功能检查

- 最终版后台继续播放：6 个 CPU 样本为 **3.7、1、1、1、2.5、0.5**，丢弃首样本均值 **1.2%**；同窗口 **0 个渲染帧**。线程采样中 RenderThread 为 0%，主线程为 0%–1%。原始文件：`.tmp/cpu-final-background-process.txt`、`.tmp/cpu-final-background-threads.txt`、`.tmp/gfx-final-background.txt`。0 帧窗口没有可用的帧耗时百分位，不能把 gfxinfo 的占位数值当成卡顿。
- 暂停后点击进度条跳到 **1:48**，相隔 3 秒的两张截图仍为 **1:48**，按钮保持播放图标，确认暂停 seek 更新有效且时间不漂移；暂停详情页短样本为 0、5、6%（丢弃首样本为 5.5%）。
- 真机检查了首页滚动、MiniPlayer 展开/收起、详情进度、歌词逐字高亮与滚动、暂停/恢复、后台返回。歌词截图中时间与高亮同步推进。玻璃与封面保持正常显示。
- 查询本次应用进程的 crash buffer，未匹配到该进程的崩溃记录；这不替代长时间稳定性测试。

### 避免混淆的测量

- 第一版修复在同类精简首页/长标题场景均值 **39.2%**，jank 1.42%、P99 12ms（`.tmp/cpu-after-home-compact-process.txt`、`.tmp/gfx-after-home-compact.txt`）；最终版增加文字绘制层后重新构建测量。两段差异还包含 JIT、调度与跑马灯周期，不能据此单独量化该文字层的收益。
- 短标题歌曲首轮首页均值 **8.0%**，该窗口 0 个渲染帧（`.tmp/cpu-after-home-process.txt`）；因为没有跑马灯，不用它与旧版长标题窗口计算降幅。
- **AI 过渡预渲染仍有多核计算峰值**：复测线程中曾出现多个 `qplayer-precache` 工作线程各约 85%–99%，最终版刚恢复播放时进程也出现约 **381%–407%** 的峰值（`.tmp/cpu-after-home-threads.txt`、`.tmp/cpu-final-home-process.txt`）。这是与稳态 UI 分开的负载，相关窗口保留，没有删除后冒充低占用。本轮没有改变模型、分离质量或 AI 过渡规则，也没有声称消除 AI 计算成本。
- 首轮歌词页含切歌/预渲染活动，1170 帧、jank 1.37%、P99 21ms（`.tmp/gfx-after-lyrics.txt`）；没有对应旧版歌词页基线，所以只作功能与负载记录，不作同比。

## 构建说明

使用 `C:/Users/xiaoz/Desktop/Build-QPlayer.ps1` 原脚本。首次构建的 Gradle JVM 申请 365953024 字节 G1 内存失败；重试仅在构建子进程设置 1200MB 堆、SerialGC 和单 worker，未改全局环境、脚本或项目构建配置。

最终构建日志：`.tmp/performance-build-marquee-retry.log`，`BUILD SUCCESSFUL in 55s`；35 个任务，3 个执行、32 个 up-to-date。前一次跑马灯构建随工具会话中断，确认进程已结束、APK 未更新后才重试。

- APK：`android-shell/app/build/outputs/apk/debug/app-debug.apk`
- 文件时间：**2026-10-02 20:46:13（Asia/Shanghai）**
- 大小：**99,381,308 bytes**
- SHA-256：`8A85DB65F66F48C616E5BE94541A2C0A1F59CC99EADAE98601F434AE96552F86`
- `apksigner verify --verbose` 通过，v2 签名有效。
- 原桌面脚本 ADB 安装返回 `Success / Install completed`；最终 APK 已安装到 SM-S9360，包名 `dev.t1m3.qplayer.debug`，仍为 versionCode 67 / 1.3.0-debug，未作为版本发布。
- 最终构建后再次运行真实 `PlayerUiState` 时钟回归，全部通过。
