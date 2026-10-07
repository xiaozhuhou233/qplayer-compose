# MD3E 整体性能优化记录（2026-10-05）

优化已应用至实际重构工程：`C:/Users/xiaoz/.codex/worktrees/888a/qplayer/android-md3e`。本轮修改/新增 11 个 UI Kotlin 文件，原始源码备份、完整应用清单、测试和构建日志保存在当前工作区 `work/md3e-performance`。

## 主要改动

| 路径 | 改动及目的 |
| --- | --- |
| Md3eMotion.kt、Md3eApp.kt | 共享封面改为 sharedBounds + ScaleToBounds，固定端点子布局，通过图层缩放完成中间帧；匹配时只绘制进入端封面。保留正反向端点注册与共享背景，不增加封面淡入淡出；歌单/专辑共享转场关闭 AnimatedContent 尺寸动画。 |
| Md3ePlayerExpansion.kt、Md3eRenderPolicy.kt | 提供可见性和转场状态，展开/收起期间暂停详情动态绘制，遮挡底页停止相关视觉任务；快照捕获时重新激活 MiniPlayer，保留原有展开视觉。 |
| Md3eRuntime.kt、Md3ePlaybackClock.kt | 后台继续 pump 播放核心，停止构造/发布页面视觉快照；前台状态原子发布。暂停时不再发布纯时间戳变化，seek/暂停/恢复仍更新。歌词索引交给可见歌词消费者，B站 cookie 写盘移出主线程。 |
| Md3eLyrics.kt | 通过开始时间二分和前缀最大结束时间查找歌词，保留重叠和声的“最早有效行”行为；动画状态只按可见行创建，换行/手动滚动回收屏幕外状态；缓存逐字判断和文本范围，整行绘制静止歌词，暂停不可见的帧循环、粒子及动态 shader 背景。 |
| Md3ePlayer.kt | 进度波形几何缓存为 Path，通过两次裁剪绘制替代每帧数百次正弦计算/线段绘制；进度动画读取移入绘制，时间文本仅整秒更新。 |
| Artwork.kt、Md3eMiniPlayer.kt | 封面淡入读取移至图层，缓存 ImageBitmap；歌词背景复用有并发/下载/内存上限的共享图片加载器；隐藏跑马灯、加载旋转和进度时钟停止工作。 |
| Md3eHome.kt、Md3eApp.kt | 首页分组缓存，折叠顶部高度读取移至布局；缓存 Typography，Claude 外观停止创建无用的动态主题动画，页面/播放器转场期间暂停主题颜色动画。 |

## 验证结果

- `Build-QPlayer-MD3E.ps1 -BuildOnly`：最终构建成功，2m 5s。
- APK 内容校验通过：18,716 个类定义，新 UI 与原播放核心存在，旧 UI、玻璃和 ONNX 未打包。
- 回归直接使用实际编译的 PlaybackState 与生产 PlaybackClock / Md3eLyricIndex，使用 Compose SnapshotStateObserver 观察失效：600 次播放样本、0 次元数据根状态失效；150 次暂停时间戳、0 次时钟失效；暂停 seek 与恢复播放通知通过。
- 312,846 次歌词索引对比全部通过，包含空数组、重复时间戳、重叠行、长和声、时间空隙、开始/结束前后边界与随机 seek。
- 最终桌面 JVM 基准：4096 行、200,000 次随机查找，索引 22.1255 ms，旧线性逻辑 161.2806 ms，约 7.3 倍。另一次运行分别为 33.6149 / 362.255 ms；这些是单次主机算法测量，会受 JVM 和系统负载影响，不能换算成手机 FPS。
- 11 个已修改实际源文件与暂存交付文件逐一 SHA-256 核对一致。原有未提交改动保留，未提交、推送或安装 APK。

## APK

路径：`C:/Users/xiaoz/.codex/worktrees/888a/qplayer/android-md3e/app/build/outputs/apk/debug/app-debug.apk`

大小：14842711 字节。

SHA-256：`B1A70ACBA35A55CFF5D429A14EE6C877844F786A22EE10E9F9F120BFA7D8C233`

## 测试复现及限制

在当前工作区执行：

```powershell
& ./work/md3e-performance/tests/Test-Performance.ps1
& C:/Users/xiaoz/Desktop/Build-QPlayer-MD3E.ps1 -BuildOnly
```

测试脚本复用本机已有 Kotlin / Compose 缓存，不下载依赖。`manifest.json` 和 `Apply.ps1` 对应用前源码做哈希保护；`before/` 是本次优化前包含用户既有改动的备份。

本轮没有手机帧时间、掉帧率、GPU 或功耗前后对比，也没有实机视觉验收。adb 设备列表读取失败（无法创建 \.android）；不将编译/主机基准表述为“共享动画已稳定 60 FPS”。共享封面首次点击、返回、快速反转、播放器展开/收起及歌词 seek 的视觉连续性仍需要在设备上验收。
