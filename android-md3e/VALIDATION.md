# MD3E 验证记录

## 2026-10-04 错误提示、悬浮播放器、B站视频与队列排序

- 本次登录/队列修正版 APK 于北京时间 `2026-10-04 10:14:13` 生成，17,762,576 字节；SHA-256：`5B70EFDCF26B350C32DBB85FEBCE0597E92AC75DAFAA239B12B383CB9CF36602`。桌面 `Build-QPlayer-MD3E.ps1 -BuildOnly` 成功（3m 52s），APK 校验通过，18,498 个 DEX 类定义。
- 网易云网页登录 Activity 已从 MD3E 移除，账号入口统一使用旧核心二维码 key/矩阵/轮询接口的 Compose Dialog；B站二维码登录仍使用旧核心流程。新增二维码关闭、刷新、单飞轮询和 B站取消回归，共 3 项通过；本轮定向测试 50 项全部通过（播放 30、队列 13、二维码 3、错误提示 4）。
- 右上角播放队列崩溃原因为拖拽库默认的 Compose 旧版 `animateItemPlacement` 与当前 Compose ABI 不兼容；队列行现在显式传入当前版本 `animateItem`，并重新构建验证。
- 最终 APK 于北京时间 `2026-10-04 09:41:57` 生成，17,762,576 字节（约 16.9 MiB）；SHA-256：`1D81D9912F3523A653910875DC8F7207711AF85A226F232CB71D7857417938AA`。桌面 `Build-QPlayer-MD3E.ps1 -BuildOnly` 构建成功（2m 31s）；APK 内容校验通过，18,469 个 DEX 类定义，确认 BiliClient 与 Md3eBiliKt 存在，旧玻璃 UI 和 ONNX 未打包。
- 已修复后台推荐、歌词预取与音源回退共用全局提示导致的反复弹窗；每日推荐每轮只请求一次，歌词非 200 响应进入重试，不再缓存为“无歌词”。删除换源成功提示。未取得原始“参数错误”响应，不能断言具体服务器拒绝接口；接口日志现在记录 endpoint、code 和 message。
- MiniPlayer 的避让改为列表内可滚动的 contentPadding，移除整页固定底部 padding；页面内容延伸至透明悬浮区域。
- B站搜索、扫码登录/会话恢复、收藏夹列表和详情、视频收藏管理、分 P 队列、章节进度、分享、封面/视频切换与横屏全屏接入旧后端；根界面保持同一个 SurfaceView，其他页面停泊画面并保留音频。
- 播放队列使用旧版 reorderable 组件，支持拖动排序、边缘自动滚动和无障碍上下移动；每个 occurrence 有独立键，支持重复歌曲与同 BVID 的多个分 P。调整当前队列，不提交远端歌单排序。
- 排序时音源、歌词和封面的异步结果跟随歌曲身份及既有请求世代，不再绑定原列表索引；切歌后的迟到结果仍被丢弃。错误提示移到根层，播放详情和队列页也能看到最终播放失败。
- 最终核心回归 **64 项全部通过**：播放控制器 30、队列排序 13、通知作用域 4、网易云接口 2、首页解析 8、设置目录 7。搜索历史迁移改为保存/删除后发布；排序期间的 playAt 延迟索引发布改为实时索引并校验请求世代。测试覆盖排序不重启/不归零、会话恢复进度、重复歌曲/分 P、真实本地 API 延迟解析、失败跳过、封面/歌词迟到结果及 A→B→A 请求失效。
- 本轮只执行源码检查、核心回归和 APK 构建/校验；没有调用 adb、安装应用、操作实体机或启动模拟器，设备验收由用户完成。

## 2026-10-04 整体 MD 界面迁移

- 最终 APK 于北京时间 `2026-10-04 08:36:40` 生成，14,153,500 字节（约 13.5 MiB）；SHA-256：`DD7A1C802BE60DA0CDCF3C673067C5082F9EE4EDD1330BE33A23F5B44F9CF630`。
- 桌面 `Build-QPlayer-MD3E.ps1 -BuildOnly` 完成最终构建，`BUILD SUCCESSFUL in 2m 21s`；APK 校验通过，18,128 个 DEX 类定义，旧版取色器/播放器/换源模块存在，旧玻璃 UI 和 ONNX 未打包。已核对旧歌词字体与 Seal 许可/来源说明进入 APK。
- 已迁移旧版 MD 导航、主页推荐布局、歌单/专辑详情、设置页、播放详情和歌词页；页面转场、共享封面及动态封面取色接入新应用。旧版 VIP 音源解锁仍由共享 `PlayerController` 执行。
- `SettingsCatalogTest` 与 `PlayerControllerPlaybackTest` 通过；页面动画预设现为可持久化的共享设置项。
- 本次 APK 未安装到设备，未进行实机界面验收；用户自行测试。已确认连接设备型号为 Redmi K20 Pro，未使用模拟器。
- 本轮已补齐 MiniPlayer 悬浮/展开、歌单详情去序号、播放控制动效、Seal shared-axis 与顶部/选择组/弹窗/局部动画、歌词逐帧时钟/逐字扫色/动态背景，以及旧版封面取色器和深浅色切换。后续又补齐旧字体、标点并组、普通 LRC 整行绘制、整行上浮和和声曲线；切歌保留歌词 tab。
- Seal 原下载包当前未找到，随后取得公开上游源码 `JunkFood02/Seal`，revision `7677f61a20fda4210e84215fef9e9b51d25daa47`，核对适用绘制/动画代码；具体来源见 APK 内 `assets/licenses/Seal-NOTICE.txt`。下载 URL、格式选择和 yt-dlp 任务业务不属于播放器迁移范围。
- `PlayerControllerPlaybackTest` 30 项与 `SettingsCatalogTest` 7 项通过。新增回归覆盖用户切队列后丢弃 AI DJ 迟到续播结果，并确认新队列续播结果仍可追加。
- 动态歌词背景需要 Android 13 的 RuntimeShader；旧系统沿用静态模糊封面回退。本轮只做构建和静态/APK 检查，未操作实体机，也未运行模拟器；不将编译结果表述为像素或流畅度验收。
- 最终 APK 的构建时间、大小与 SHA-256 以交付时的文件为准。

## 2026-10-03 导航与 VIP 换源迁移

- 新 APK 已构建：`app/build/outputs/apk/debug/app-debug.apk`，13,055,717 字节，SHA-256 `AA51104AA5A706DED9697547A95E43CA86EE03FC70EDAED4C9A04C4ECA000076`。
- `verify-apk.ps1` 已通过，检查 17,709 个 DEX 类定义；`SongUnblocker` 及网易云、波点、酷我旧换源类均存在。未打包旧 Compose UI、玻璃类和 ONNX 资产。
- `PlayerControllerPlaybackTest` 29 项通过。另一次包含 `SettingsCatalogTest` 的运行共 36 项，35 项通过；失败项 `pageTransitionDefaultsToZoomAndOffersAccessibleFallback` 期望当前共享 `SettingsCatalog` 中不存在的 `pageTransitionPreset` 行，属于工作区原有设置/测试不一致，本阶段未修改该目录。
- MD3E 启动时读取 `unblock` 持久化设置并交给旧 `PlayerController`；设置页开关会持久化并更新控制器。官方完整 URL 优先，缺失或仅试听时才调用旧 `SongUnblocker`。没有改写旧版音源或匹配算法。
- 新导航、歌单、歌手/专辑详情、搜索分页、本地 MediaStore 扫描、系统音频权限、完整播放页和队列页已编译进独立应用。
- 新页面交互、账户歌单、本地音频和真实 VIP 换源尚未完成实体机验收。后续仅使用实体机；本机 `adb devices` 目前因无法创建 `\.android` 而退出，未连接设备。以下旧版实机冒烟记录仅对应迁移前的第一阶段 APK。

日期：2026-10-03（Asia/Shanghai）。工作区：`C:\Users\xiaoz\.codex\worktrees\888a\qplayer`。

## 结果

- **APK 构建成功**：`:app:assembleDebug`，`BUILD SUCCESSFUL in 3m 41s`。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，12,858,273 字节（约 12.3 MiB）。
- SHA-256：`DFB933D866688598FEE8C17B56748C34CE21C4102368BD2995E9AAA676BA3BF8`。
- 包名：`dev.t1m3.qplayer.md3e`；版本：`0.1.0-md3e` / code 1；入口：`dev.t1m3.qplayer.android.md3eui.Md3eActivity`。
- 现有 `PlayerControllerPlaybackTest`：**29 项通过，0 失败、0 错误、0 跳过**。报告位于 `core/build/reports/tests/test/index.html`。
- `verify-apk.ps1` 检查 **17,457 个 DEX 类定义**：新 Activity、PlayerController、NeteaseClient、AndroidAudioBackend、PlaybackService 均存在；旧 Compose UI、玻璃类、Android stem renderer 和 ONNX 资产未进入 APK。
- 七个暂存 Java 文件与源文件逐个 SHA-256 对比完全一致。任务开始时的 **28 个已修改 tracked 文件**在任务结束时再次比对，内容未改变。未 reset、checkout、清理或覆盖用户源码。

## 实机冒烟

独立安装成功。冷启动 `am start -W` 返回 `Status: ok`，总耗时 1274 ms；回到已有任务耗时 62 ms。这是单次观测，不是性能基准。

| 项目 | 证据 / 结果 |
| --- | --- |
| MD3E 根界面 | 暗色实色界面、推荐歌单、封面、MiniPlayer 正常渲染，截图已检查 |
| 公开推荐 | 未登录状态实际拉取网易云推荐和封面，无静态演示歌曲 |
| 登录入口 | 点击进入 `NeteaseWebLoginActivity`，Back 后回到新根界面，登录按钮恢复可用 |
| 真正的播放链路 | 推荐歌单经 NeteaseClient 解析 CDN 音频，Android MediaPlayer `prepareAsync` / `prepared`；《唇印》时长 224601 ms，MiniPlayer 进度实际递增至 1:20 以上 |
| 试听版权 | 《慕容雪》返回 45035 ms 试听流，《紧急联络人》返回 30024 ms；遵循已有网易云账号/版权行为 |
| 自动下一首 | 试听结束后进入《唇印》，界面元数据和封面随之更新 |
| 暂停 | 媒体会话 `PAUSED`，位置 153238 ms；间隔两秒再读取仍为 153238 ms |
| 继续 | 媒体会话恢复 `PLAYING`；后端日志记录 `resume at 153238ms` |
| 下一首 | 从《唇印》切换至《紧急联络人》，后端准备新音频 |
| 上一首 | 回到《唇印》，后端重新准备 224601 ms 音频 |
| 后台 | Home 返回桌面后会话仍为 `PLAYING`；系统媒体暂停命令生效，返回应用显示播放按钮 |
| 测试结束 | 留在 MD3E 根界面、暂停状态；未输入凭据或提交账号登录 |

截图：`app/build/verification/home.png`、`app/build/verification/playing.png`。左上绿色数字属于设备现有刷新率叠层，不是 MD3E 界面元素。

真实音频验证依据后端解码/准备日志、持续递增的播放进度和媒体会话；未人工听音评价音质。没有完成本人账户登录后的个性化推荐、锁屏/蓝牙、通知点击、屏幕旋转、弱网/断网及任务划走的完整验收。通知返回路径由 manifest alias 接入，已检查 manifest，但未点击通知实测。

需要后续关注的既有边界：原 PlaybackService 停止时不会取消控制器已提交的音频 URL 解析；“解析期间划走任务 / 停止通知后重新播放”仍需专项设备验证，本阶段未修改旧核心和服务。SettingsCore 可选字体枚举会记录缺少 Skija native 的日志，现有 fallback 正常工作，没有导致启动失败。

## 复现命令

在仓库根目录使用 JDK 21、Android SDK 35：

```powershell
./android-shell/gradlew.bat -p android-md3e :app:assembleDebug --offline --no-daemon '-Dmaven.repo.local=D:/qplayer-dev/cache/maven'
./android-shell/gradlew.bat -p android-md3e :core:test --tests '*PlayerControllerPlaybackTest' --offline --no-daemon '-Dmaven.repo.local=D:/qplayer-dev/cache/maven'
./android-md3e/verify-apk.ps1
```

本机实际使用缓存中的 Gradle 8.7 `bin/gradle.bat` 运行相同参数。其他机器按自己的 Maven 仓库路径调整，缓存不完整时移除 `--offline`。

构建环境修正均限制于新工程：离线依赖版本对齐、按白名单暂存原 Java 文件、单 worker + 768 MB 堆 + SerialGC。早先 1536 MB 堆构建发生 native memory allocation failure；改用最终配置后成功。将扩展图标全集替换为九个所需矢量图标后，避免将无用图标打进 APK。

## 新增文件清单（均在 android-md3e 内）

- `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`：独立构建入口与内存配置。
- `core/build.gradle.kts`：原位编译现有核心及其测试，输出隔离。
- `platform/build.gradle.kts`：白名单复用七个现有 Android Java 文件。
- `app/build.gradle.kts`：新应用 ID、Compose 依赖和调试签名。
- `app/src/main/AndroidManifest.xml`：新入口、官方登录页、播放服务及通知别名。
- `app/src/main/res/values/themes.xml`、`app/src/main/res/drawable/ic_md3e.xml`：启动主题与图标。
- `app/src/main/java/dev/t1m3/qplayer/android/md3eui/Md3eActivity.kt`：生命周期、登录回调、通知权限和系统栏。
- `app/src/main/java/dev/t1m3/qplayer/android/md3eui/Md3eRuntime.kt`：现有控制器/设置连接和独立 UI 状态。
- `app/src/main/java/dev/t1m3/qplayer/android/md3eui/Md3eApp.kt`：MD3E 推荐首页、登录入口、MiniPlayer 与错误提示。
- `app/src/main/java/dev/t1m3/qplayer/android/md3eui/Artwork.kt`：有并发、下载及内存上限的异步封面加载。
- `app/src/main/java/dev/t1m3/qplayer/android/md3eui/Md3eIcons.kt`：九个按需 Material 矢量图标。
- `verify-apk.ps1`：APK 类及资产隔离检查。
- `README.md`、`VALIDATION.md`：构建、架构边界与本次验收记录。

现有页面、核心文件、主 Android 构建入口均没有修改；新增代码未提交到 Git。
