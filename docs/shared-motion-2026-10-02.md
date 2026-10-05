# 共享转场、玻璃描边与 main 歌词同步（2026-10-02）

## 范围与来源

本轮处理首页/详情共享元素、MiniPlayer 展开收起、玻璃描边，并定向合入远端 `main` 的歌词修改。保留此前的播放时钟分离、封面采样、图层缓存及后台停止视觉更新。

Git 2.55.0.windows.5 已实际成功执行 `fetch --no-tags origin main`。来源为 `xiaozhuhou233/qplayer-compose` 的 `9dd5cf26e906cea461ee7dfbb8794c655efcf43a`，提交标题为 `fix(lyric): 逐字 lift 时钟 + 去掉逐行 RenderEffect 模糊`，提交时间 2026-10-02 21:16 +08:00。只移植歌词相关差异，没有覆盖整个 Activity，没有切换分支、提交或推送。

## 修改

- `PlayerExpansionHost.kt`：展开容器的背景只画固定底色，由已有动态圆角层裁切；背景不再额外订阅逐帧 bounds，减少重复记录绘制。
- `IosGlassPalette.kt`：高频玻璃色、亮度及运动状态使用有读取跟踪的 CompositionLocal，避免每次变化使整个 Provider 子树重新执行。
- `ArtworkPageTheme.kt`、`QPlayerThemeMotion.kt`：保留 48 色角色、700ms 曲线；真实页面/共享元素转场或播放器运动期间冻结颜色驱动，结束后连续恢复。隐藏/后台也暂停。MD3 不再创建随后丢弃的封面主题动画。主题和导航宿主结构保持稳定。
- `ComposeQPlayerActivity.kt`：页面与共享封面共用显式 Transition，并上报实际运行状态；详情页进入/退出均使用同一容器动画策略。播放器运动状态覆盖准备、展开、收起和末尾弹簧。
- 复测后将页面配色锁定在转场的 `currentState`，直到退出页离开，避免返回首页时详情页提前变色；移除退出页的全屏 RenderEffect 模糊，减少缩放/淡出之外的离屏渲染。
- 玻璃外壳高光 alpha `0.75 → 0.55`，导航指示器 `1.00 → 0.75`，均匀白边系数 `0.12 → 0.08`。实际均匀白边贡献约减半，折射、色散、模糊及动态光源保留。独立恢复的 iOS Dialog 材质不变。
- 歌词逐字按自身 `startMs/endMs` 起跳，去掉逐行 RenderEffect blur，加入远端的 `-2.5dp` 整行弹簧抬升。适配共享播放时钟、可见性和弹簧开关，复用既有图层，避免恢复每帧整行重组。

## 已通过的独立回归

- `Test-SharedMotion.ps1`：真实 Compose Recomposer，120 次玻璃 local 更新使非消费者额外执行 0 次；旧静态 local 控制为 120 次。48 色冻结 60 帧不变，恢复、换目标、快速反向及多个运动来源均通过。
- `Test-LyricWordLift.ps1`：279,613 个既有动作采样、十分钟时钟以及新增逐字区间、空白、暂停和 seek。
- `Test-LyricLineLift.ps1`：真实 Animatable 速度续接、静止无帧请求、结束归零、隐藏取消与恢复。
- `Test-GlassMotion.ps1` 与 `Test-IosDialogRestore.ps1`：玻璃参数与运动检查、Dialog 旧材质隔离均通过。
- 最终改动后再次通过 `Test-SharedMotion.ps1`，Android `:app:assembleDebug --offline` 成功，APK 已安装并在 Redmi K20 Pro 前台启动。

## 真机方法与旧版基线

设备为 Redmi K20 Pro / SM8150，序列号 `efaa83b2`，Android SDK 36，1080×2340。基线安装的是上一轮 20:46 APK，并非手机原有的 9 月旧包。基线 SHA256：`8A85DB65F66F48C616E5BE94541A2C0A1F59CC99EADAE98601F434AE96552F86`。

同一设备、同一推荐歌单、同一首暂停的 Passionfruit / Drake。每组先预热，再 reset gfxinfo，执行 8 轮打开/返回，每次停留 1.6 秒。歌单点击坐标 `(260,980)`，MiniPlayer `(350,1980)`，返回为 Android BACK。统计覆盖整个操作流程；主题颜色与容器错开后，总帧数也可能变化，不能只比较一个百分比。

| 旧版场景 | 总帧/超时帧 | 现代 jank | legacy jank | P50 | P90 | P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| MD3 首页↔歌单 | 500 / 42 | 8.40% | 48.40% | 22ms | 44ms | 200ms |
| MD3 MiniPlayer↔详情 | 671 / 92 | 13.71% | 34.13% | 20ms | 44ms | 150ms |
| iOS 首页↔歌单 | 504 / 53 | 10.52% | 74.80% | 31ms | 69ms | 200ms |
| iOS MiniPlayer↔详情 | 692 / 157 | 22.69% | 94.80% | 29ms | 53ms | 125ms |

原始记录在 `.tmp/motion-baseline-*.txt`；MD3 歌单第一组记录来自命令输出。iOS 播放器 framestats 仅保留最近 120 帧、4.89 秒，不能冒充完整 692 帧。该窗口 UI 活动 P50 6.34ms、P90 11.60ms，渲染命令阶段 P50 14.08ms、P90 18.47ms；最坏帧 UI 活动 119.74ms。说明主线程尖峰和渲染提交/缓冲等待并存，尚不能归因于单一 Compose 死循环或单一 GPU shader。

该 ROM 的动态 deadline 可能大于单次刷新周期。现代 jank 与 legacy 指标定义不同，低现代 jank 不代表稳定 60fps。GPU 直方图的 4950ms 尾桶与帧记录不一致，不用于前后比较。

## 新版真机对照

相同手机、iOS 模式、首页推荐歌单坐标和暂停的 Passionfruit / Drake，各场景预热后 reset gfxinfo，再做 8 轮打开/返回。以下是完整操作区间的累计值；小样本和系统负载变化会影响百分比。

| 场景 | 旧版 超时/总帧 | 首版改动 超时/总帧 | 最终版 超时/总帧 | 旧版 P50/P90 | 最终版 P50/P90 |
| --- | ---: | ---: | ---: | ---: | ---: |
| iOS 首页↔歌单 | 53/504 (10.52%) | 77/532 (14.47%) | 66/493 (13.39%) | 31/69ms | 27/89ms |
| iOS MiniPlayer↔详情 | 157/692 (22.69%) | 150/712 (21.07%) | 142/711 (19.97%) | 29/53ms | 30/46ms |

首版与最终版原始累计记录分别在 `.tmp/motion-new-ios-*.txt`、`.tmp/motion-final-ios-*.txt`。播放器转场有小幅改善；歌单中位帧缩短，但超时帧占比和 P90 仍高于旧版。故本轮不能声称共享元素转场已稳定 60fps，也不能把歌单路径的掉帧视为解决。全屏退出模糊移除后，歌单首版到最终版的超时占比从 14.47% 降为 13.39%，但单次采样不能证明因果。快速反向后 logcat 未发现 QPlayer 崩溃；测试过程中手机另行切到了系统设置页，因此该交互检查并不完整。

## 构建环境处理

本轮 C 盘一度耗尽。保留源码、备份、已有 APK 和测试参考文件，清理可再生的 `android-shell/app/build/intermediates`，曾将该路径通过 junction 指向 D 盘。随后确认 Windows 持续记录磁盘 1 / D 盘的事件 153（I/O 重试）与 UASPStor 129（USB 存储重置），因此已删除此 junction 并恢复普通的 C 盘构建目录。没有删除链接目标的数据。此前尝试整体复制 build 也已停止，原 build 仍在 C 盘；D 盘上有少量未用于构建的复制副本。

原桌面 `Build-QPlayer.ps1` 未修改。D 盘连接恢复后，首次重试已成功构建并安装歌词/玻璃/转场首版 APK。最终改动使用同一 Gradle 8.7，在 `--offline --no-daemon --max-workers=2` 下成功构建并安装。最终 APK 为 `android-shell/app/build/outputs/apk/debug/app-debug.apk`，2026-10-03 00:14:31，95,980,451 字节，SHA256 `6DDBF763334D663A3B6A79AB4EAD79AFA2D4F68861483026ADBC14D6380ED22F`。手机前台运行已确认。D 盘此前的 I/O 重试与 USB 存储重置仍是构建环境风险。

## 修改前备份

- `backups/shared-motion-before-20261002/before-shared-motion.zip`
- `backups/shared-motion-before-20261002/before-theme-scheduling.zip`
- `backups/glass-outline-before-20261002/before-outline-softening.zip`
- `backups/lyrics-main-before-20261002-213523/`
