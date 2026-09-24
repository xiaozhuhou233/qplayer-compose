# qplayer 交接文档

> 给下一个会话/工作区。**先读这份，不要先探索仓库** —— 探索会烧掉大量上下文，
> 而下面的路径、命令、约束、已验证的外部事实都是当前真实状态。
>
> 约定：**行号会漂移，按名字定位**（`grep -n "名字" 文件`），下面括号里的行号只是线索。

## 零、接手须知（新会话先读这一段，约 45 行，够开工）

**状态**：分支 `feat/ai-dj-transition`（**`main` 一直没动**，仍是 `5efc1ba`）。
最近一次发布：tag `ai-dj-transition-2026-09-24a`（**192,179,303 bytes ≈ 192 MB**，sha256 见发布说明），
`https://github.com/xiaozhuhou233/qplayer-compose/releases/download/ai-dj-transition-2026-09-24a/app-debug.apk`
（能装的是 **debug** 包；release 是未签名的）。发布用 `gh`，`github.com:443` 在本机被拦，
**必须走本地代理 `127.0.0.1:7890`**；资产 URL 要**从 `gh` 输出里原样复制**（前几轮手打错过账号名）。

**模型随 APK 交付（第 19 轮，2026-09-24）**：htdemucs-quarter 模型现在**打在 APK 的
`assets/models/htdemucs-quarter.onnx` 里**，App 在**首次渲染**（preload 通道）把它复制到自己的
`files/models/` 再照旧用 manifest（名字+字节数+sha256）验一遍 —— 所以**把 APK 发给别人，装完打开就有模型，
不需要 adb push，也不需要任何手工步骤**。两条日志是这一条链路的证据：
`the stem model was copied out of the APK … (97978156 bytes, sha256 427b9588…)` 与
`stem DJ edits are ON — htdemucs-quarter.onnx verified in …`（真机原文见第七节第 19 轮）。
**构建端**：模型**不在 git 里**（98MB，别人要 clone），打包时从
`D:\qplayer-dev\htdemucs\htdemucs-quarter.onnx` 抄进 assets（Gradle 任务 `stageStemModel`），
**文件不在或 hash 不对 ⇒ 构建直接失败**（故意的：没有模型的 APK 装上就是哑的，见第二节）。
本机另外那个 `htdemucs.onnx`（half）**不打进 APK**。

**做完一件事就发版**：`mvn -pl player-core install` + `:app:assembleDebug`（命令见第二节），
然后 push 分支 + `gh release create` 把 debug APK 作为资产，并核对 HTTP 200。

**这个功能是什么**：切歌时的 AI 过渡（DJ 式融合）。已实现并**在真机上验证过机制**的部分：
双播放器交叉淡化；按配对选择过渡方式（CROSSFADE / SILENCE_TRIM / FADE_OUT_IN / QUICK_FADE / CUT 五种都出现过，
按你曲库 1260 个有序配对统计为 CROSSFADE 76% / FADE_OUT_IN 24%）；出曲压成 −10dB 垫底、82% 处 −66dB；
入曲人声**等到融合结束+1 秒的小节线**才回来；调性过渡是**两侧相向的阶梯**（每步一个整调，读到平台确认）；
低频在 15% 处交接；节拍对齐；`过渡时长` 滑块 4–30 秒（默认 15）。

**第 18 轮加了「融合过渡」**：过渡段不再靠 A 的原混音以 −10dB 垫底与 B 的伴奏交叉，而是**把两首歌
的去人声伴奏在预渲染文件里拼成一小段**——交界点起 A 的鼓/贝斯接着打，B 的床在一小节内**淡入**
（用户听过后点名要的），然后 A 的鼓、贝斯各自在自己的小节线上**用 80ms 拼接切走**（不是淡出），
B 的鼓/贝斯在同一时刻切进来；出曲的**人声 row 从不参与携带**，入曲人声仍等融合结束后的小节线。
两台播放器在交界处做 **300ms 线性等增益交接**（等功率会把两轨相加成 +3dB 鼓包：交界处两轨是
同源相关信号）。文件头上还有**峰值限制器**和**有上限的补偿增益**（把人声让出的电平补回来）。
前提：模型在位（`files/models/htdemucs-quarter.onnx`，sha256 `427b9588…`）、两首**节拍锁得住**
（周期差 ≤2%，即同一速度族）、且 A 的交界处**还有律动**（`StemFusion` 在 ±2 小节内优先挑这样的位置）。
任一不满足就**原样退回第 17 轮的行为**，不报错。

**已知缺陷 / 未验证（下一轮优先）**：
1. **渲染文件的"去人声"在某些文件上从 ~13.4 秒起失效**（计划 16.7 秒）：第 18 轮找到候选根因——
   渲染按 `removalMs`（= 混合时长 + `djEditEntryMs`）划窗口，而播放端过去用自己的 `beatEntryMs`，
   两者最多差一个拍。融合编辑现在把自己的 `entryMs` 写进文件名（`-e`）并强制播放端照用，
   所以**融合路径上已解**，但 `-b` 桥和纯编辑仍走老路。round 19 应让**每种编辑都带 `-e`**
   （渲染端本来就会算拍对齐的 entry），播放端一律照用 —— 这条缺陷才算整体关闭。
2. **17% 双可闻**（从 68% 砍下来的）可能**过头了**：出曲退得太早会显得过渡太短/太淡。要拧就拧 `FadeCurve` 的 dB 表。
3. **卡顿的根因未证明**（用户报的 I Walk Alone → 3 Strike 那一下）：参数写入已从 ~218 次降到 12 次，
   但根因只剩两个候选（parked 入曲通道未就绪 / 渲染抢 CPU），那对歌不在开发机曲库里。
4. **桥（StemBridge）从未在真机上播放过**：一次判定素材不合格放弃，一次 82 秒渲染装不进提前量。
5. **启动掉帧**：改动有代码依据（缓存遍历内联、主线程 totalSize、后台通道立刻开工、启动闸门），
   但**一帧都没测**。现成脚本：`D:\qplayer-dev\harness15\measure.sh`。
6. **B站 视频预览圆角**：机制取自 AOSP（outline + clipToOutline），**没有截图确认**。
7. **测试有 1 个长期失败**（不是我们的）：`SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`
   —— 另一个并行工作区留下的 `pageTransitionPreset` 半成品。

**硬约束**：① **不能新增依赖**（本机 Google Maven 不通；Maven Central 可以）——唯一例外是已批准的
`onnxruntime-android`；② **C 盘只剩 ~13G**，大文件（模型/渲染/scratch）一律放 `D:\qplayer-dev\`，
不要复制仓库；③ 开发机只有 **Redmi K20 Pro（`efaa83b2`）**，8e 那台不能常连；④ 大文件是
`ComposeQPlayerActivity.kt`（7700+ 行，**永远别整读**，用 `sed -n` + `grep -n`）；⑤ 编辑前重读目标区域。

**工作方式**：把"找/验"类活**派子代理**（它们有自己的上下文，只回结论）；**一轮只推一个能编译的步骤**；
每轮结束更新本文档（§7 是流水账日志，按需翻，别通读）。**第 18 轮的融合段已经用真模型在 PC 上渲染成
WAV 并被用户听过**（用户听出的"交界卡顿"正是那一版的交界电平落差，已按实测修掉：−7.25dB → −0.09dB）；
**但在手机 App 里的实际听感仍未被验证**，因为没有设备时只能看日志。PC 侧渲染/测量工具在
`D:\qplayer-dev\harness\fusion\`（venv + onnxruntime + htdemucs-quarter，`round18.py` 是带最终规则的渲染）。

## 一、这是什么

`C:\Users\xiaoz\Desktop\SkidTime\qplayer` —— Android 音乐播放器 qplayer（原 QML 桌面端，
正用 Jetpack Compose 重写 Android 端）。远程仓库
`https://github.com/xiaozhuhou233/qplayer-compose`（分支 `main`，直接推）。

- **大文件（7700+ 行，永远不要整个读）**：
  `android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/ComposeQPlayerActivity.kt`
- 播放内核：`player-core/`（Java）。`bridge/PlayerController.java`（6000+ 行）是总控，
  `netease/NeteaseClient.java`、`bili/BiliClient.java`、`ai/AiClient.java`。
- Android 播放后端：`android-shell/.../playback/AndroidAudioBackend.java`
- 通知/媒体会话：`android-shell/.../playback/PlaybackService.java`
- **多个 AI 工作区会同时改这个仓库，每次编辑前必须重读当前内容。**

## 二、构建与验证（照这个来）

```bash
# 核心（改了 player-core 必做；app 直接吃 target 下的 jar）
export JAVA_HOME='C:\Program Files\Java\jdk-21'
cd /c/Users/xiaoz/Desktop/SkidTime/qplayer
/d/qplayer-dev/download/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests \
    -Dmaven.repo.local=D:/qplayer-dev/cache/maven -pl player-core install

# App
cd android-shell
export GRADLE_USER_HOME='C:\Users\xiaoz\Desktop\SkidTime\qplayer\gradle-cache'
export ANDROID_USER_HOME='C:\Users\xiaoz\Desktop\SkidTime\qplayer\android-user'
G="/d/qplayer-dev/cache/gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle.bat"
"$G" :app:assembleDebug --no-daemon          # 约 1~1.5 分钟

# 也可以直接用桌面脚本（一次做完 core+app+装机）：C:\Users\xiaoz\Desktop\Build-QPlayer.bat
```

- **app 依赖的是 `player-core/target/*.jar`（`files(...)` 直接引用），不是 maven 仓库** —— 所以 `package`/`install` 都会更新它。
- **构建前必须有模型**（第 19 轮起）：`D:\qplayer-dev\htdemucs\htdemucs-quarter.onnx`
  （97,978,156 bytes，sha256 `427b9588…`）。Gradle 任务 `stageStemModel` 把它抄进
  `app/build/generated/assets/stageStemModel/models/`（在 `build/` 里，git 看不见），再由 AGP 打进
  `assets/models/`。**文件不在、字节数不对或 hash 不对 ⇒ 构建失败**，失败信息里带路径/字节数/sha256 ——
  这是有意的：没有模型的 APK 装上以后功能静默失效。换一台机器（或 CI）用
  `-PqplayerStemModel=<路径>` 或 `QPLAYER_STEM_MODEL=<路径>` 指过去；**CI 上没这个文件，所以
  APK 的 CI 构建会失败**（除非先把模型放上去）。
- **发布用的 APK 要从干净的 `packageDebug` 取**：增量打包会把上一版 APK 的字节留在文件里
  （实测同一内容 **196.5MB vs 192.2MB**）。稳妥做法：删掉
  `app/build/outputs/apk/debug/app-debug.apk` 和 `app/build/intermediates/apk/debug/`，再
  `"$G" :app:packageDebug --no-daemon`，然后 `sha256sum` 记录。
- adb：`C:\Users\xiaoz\Downloads\platform-tools\adb.exe`；装机 `adb install -r <apk>`。
- **设备经常掉线**（`no devices/emulators found`）：每次动手前先 `adb devices`。
- **adb 路径坑**：Git Bash 下 `adb shell ... /sdcard/x.png` 会被改写成 Windows 路径，必须
  `export MSYS_NO_PATHCONV=1`。
- logcat 只看 App 自己：tag 是 `musicplayer`（`Logger` 走 JUL）。不要用宽 grep，会被系统日志淹没。
- 读大文件用 `sed -n 'N,Mp' 文件`，不要 Read 整个文件。

## 三、硬约束（不要碰）

1. **不能新增依赖**（这台机器 **Google Maven 不通**）：没有 media3/ExoPlayer、没有 Palette/Coil。
   图片一律 `rememberCoverBitmap(...)`；取色用文件里已有的色彩数学。
   **Maven Central 是通的**。
   **唯一的例外是 `com.microsoft.onnxruntime:onnxruntime-android:1.30.0`**（2026-09-19 经用户同意加入，
   为 htdemucs 人声分离；它给 APK 加 +29.1MB，且**目前还没有任何功能代码用它** —— 见第七节第六轮）。
   除它之外仍然不许加。
2. 视频播放只能走 `android.media.MediaPlayer`（+ 我们的常驻 SurfaceView 图层）。
3. 编辑那个 7700 行文件时**不要手工数括号**：历史教训是多次因多/少一个 `}` 导致整文件
   解析失败（165 个报错）。改结构后立刻编译。
4. 队列存档按 `Track.Source` 名字读写；BILI 的 `biliBvid`/`biliCid` 必须一起存取。
5. **磁盘（2026-09-19 起）：C: 只剩 ~6.2GB（98% 满，仓库自己也占了 6.5GB 的构建产物）。
   一切大文件 —— 模型、harness、下载物、日志、python 依赖、参考实现源码 —— 一律放
   `D:\qplayer-dev\...`（432GB 空闲）。不要往 C: 拷仓库、不要留 APK 副本。
   动手前后都 `df -h /c` 看一眼。**

## 四、已完成（可用，已实测）

### B站视频播放
- **队列化播放**：搜索点一条 → `playBiliCollection` → 分P 入队 → `playAt` 的 **BILI 分支**
  （`PlayerController.playAt` / `resolveAndPlayBili` / `startBiliStream`）。
  ⚠️ 历史 bug：BILI 分支曾被嵌在 `source == NETEASE` 的 else 里 = 死代码，点了完全没反应且无日志。
- **全屏**：双击进出、单击呼出/隐藏进度条+暂停键、横屏沉浸、按**原片比例**居中（左右 24dp、
  不裁切不拉伸）。**收藏按钮所在的底部那排现在播视频时也显示**（曾用 `height(0.dp)` 收起）。
- **章节**：`BiliClient.chapters(bvid, cid)` 读 `/x/player/wbi/v2` 的 `view_points[]`，
  画成全屏进度条上的刻度。
- **流地址失效自愈**：长视频播到中途 B站 CDN 令牌过期 → `MediaPlayer error: what=-38` →
  `PlayerController.onPlaybackError` 里 **BILI 分支**重新取流并从当前位置继续
  （防循环用 `biliErrorRetryBvid`，与 `errorRetryId` 同一套）。网易云那条一直就有。
- **队列恢复**：`saveQueue`/`loadQueue` 写读 `biliBvid`/`biliCid`，`loadQueue` 有 BILI 分支。

### 视频画面架构（关键，别改坏）
- **全 App 只有一个视频节点**：`VideoLayer`（根节点，`QPlayerComposeApp` 里 `LoginDialog` 之前），
  内部是 `BiliVideoSurface`（真正的 `SurfaceView`）。
- 它**常驻**，只改位置和尺寸；没有槽位要它时**停靠到窗外**（parked，不是隐藏/销毁）——
  Surface 因此全程存活，**播放器不会被要求重建画面**。
- 槽位由 `VideoSlotReporter(priority = 1|2, ...)` 上报（详情页 = 2）；全屏 = 整屏 + `BiliFullscreenControls`。
- 详情页的队列面板打开时把图层设 `videoSlotObscured = true`（停靠），否则会浮在面板之上。
- ⚠️ 曾经的错误做法：把视频做成**窗口级覆盖层**（`addContentView`）—— 它是 Compose 视图的兄弟节点，
  永远压在面板之上 → 视频悬浮。**已废弃但代码还在（见第六节）**。
- ⚠️ 另一个已删的坑：`attachVideoSurface` 里曾对新挂上的 surface 先 `seekTo(当前位置)`，
  结果"退出 App 再进来会重放当前帧一次"（`PlaybackService` 后台还在播，新 surface 挂在活的播放器上）。
  **已删除，不要再加回来。**
- `setVideoScalingMode` 也已删除（每次调用强制平台重渲）。

### B站收藏夹（本轮新增）
- `BiliClient`：`favFolders(rid)` / `favItems(mediaId,page,ps)` / `favDeal(avid,add,del)` /
  `videoAid(bvid)` / `selfMid()`（读 cookie `DedeUserID`）/ `csrf()`（读 cookie `bili_jct`）。
- `PlayerController`：`loadBiliFavFolders(rid)` / `loadBiliFavFoldersForBvid(bvid)` /
  `loadBiliFavItems(mediaId,title)` / `playBiliFavFolder` / **`playBiliFavItemAt(index)`**
  （点第 i 条 → **整夹入队、从该条开始**，零额外网络）/ `setBiliFavs(bvid,add,del)`。
- 界面：歌单页「B站收藏夹」入口 → 夹列表 → 夹内容页（`BiliFavFoldersScreen` / `BiliFavItemsScreen`，
  导航枚举 `ComposeScreen.BILI_FAV` / `BILI_FAV_DETAIL`）；底部「收藏」按钮 → `BiliFavPickerDialog`
  （多选、已收藏打勾、**只提交差异**）。
- ⚠️ `BiliClient.post` 曾经**漏发 Cookie 头**（GET 的 `request` 有，POST 没有）→ 收藏请求以未登录身份发出、
  静默失败。已修（`if (isLoggedIn()) c.setRequestProperty("Cookie", cookieHeader())`）。

### 其他
- 主题/动画/歌词/播放详情等见旧版本记录，未变。
- **音频焦点（别人放歌/视频 → 自动暂停；别人停了 → 自动恢复）—— 2026-09-21 修好并装机验证，
  见第九节**（含"为什么以前不会暂停"的两个根因、四种焦点的规则、以及能复现全部四种焦点的探针工具）。

## 五、待办

1. **AI DJ 两首歌过渡**：P1（双播放器）、P7（过渡目录 + 选择器 + 设置开关 + 静音裁切）、
   **P6（AI 选择器 + 重叠长度 + 内容起点，2026-09-19 落地并装机验证）**、
   **P4（节拍分析 + 重叠对齐，2026-09-19 落地并装机验证，含"探针太晚"的两个修复）**、
   **P5 的替代（第四轮：变速对拍 + 改调 + 低频互换，代码+纯 Java 测试落地，未装机）**与
   **第八轮（2026-09-19：周期选择的"和声关系检查"，把 2:3 错格修掉；门里的段内 support 也要和声感知；
   装机验证 `mix:` 第一次写成"做了什么"、低频互换第一次真的换了低音）** ——
   但仍**卡在调性强度阈值**（36 首里 31 首低于 0.25，见第七节第八轮"下一道约束"），
   另：**第七轮（2026-09-19）已把 stem 顺序验证（结论：声明顺序成立）、Folia 的 stem 手势移植成纯 Java
   （`audio/StemGesture`，18 个单测全绿）、以及第一个预渲染混音跑了出来（只有日志，播放路径没碰）** ——
   见第七节第七轮；**下一步仍是听感，然后才是接线**。
   **第五轮（2026-09-19：每个边界一行说明混音结果、无 AI 答案改用 8 秒交叉淡化、置信度换成"窗口自洽"、
   下一首音频预缓存 —— 四项都已装机验证；期间发现**真正的瓶颈是周期选择**，见第七节⑤）**都已实现，
   见第七节（含未验证项与风险清单）—— **第九轮（2026-09-19）把调性强度在真实音乐上重新校准了：
   `KeyAnalysis` 去掉 log 压缩、强度改成"与不可能混的调"的余量、新增 `KeyProfile.MIN_TRIAD` 第二把门、
   `MixMatch` 不再把"移调没意义"当成"调打架"、磁盘格式 VERSION 5；过门 9/36 → 21/36，
   **第一次在两首不同的歌上跑出变速+移调+低频互换（已装机验证）** —— 见第七节第九轮。**
   **第十一轮（2026-09-20：变速/改调以自然化器回归、曲线换成 DJ 式分阶段、`align=on` 5/7、
   两首同响 41%→67%，已装机验证）见第七节末尾。**
   **第十二轮（2026-09-20：过渡时长成为滑动条设置、incoming 渲染成“人声剥离的 DJ 编辑文件”并真的播了出去、
   音高在人声前 2s 回到原调（放不下就整对不移调）、DJ 式曲线 hold 指数 1.8→2.6 + 低频交接从中间移到五分之三
   （同响 67%→72%、交接 10056→12151ms），已装机验证；`BlendPulse` 记录了“三种拍子在位代理都测不出真实音乐”
   这条否定结论）见第七节末尾。**
   **下一步第一件事仍然是**听感**（第四/五/八/九/十/十一轮都没让任何一次过渡真的播给人听过），
   第十一轮末尾列了"没人听过"的具体清单，优先确认：
   (a) **DJ 式曲线 + 16.5s 重叠**听起来是不是真的不只是"淡入淡出"（先听
   `Life's A Mess → death bed` 这条：日志里 `both tracks audible … (67%)`）；
   (b) 中段约 +2dB 的抬升是否显得"音量跳"，以及低频互换那一刀是不是太狠
   （`BASS_SWAP_HZ` / `FadeCurve.DJ_BLEND` 的两个指数 / 增益上限都可调）；
   (c) 晋升后 6 秒的速度/音高还原能不能听出来（`easing the promoted track back …`）；
   (d) 自动规则把"收尾有实测静音的长歌"判成 静音裁切 是否正确（若听着别扭，
   `HeuristicTransitionChooser` 的规则可整段替换）。
   ⚠️ 真机（efaa83b2）的 `transitionKind` 一度从 2（强制交叉淡化）改成 **0（自动）**，
   否则 AI 选择器永远不会被问到；要回到强制模式在设置里改回「过渡方式」即可。
   ⚠️ 想快速试到"混音"这条路，挑两首**收尾没有 ≥1.2s 实测静音**、BPM 差在 ±8% 以内的歌，
   「过渡方式」留**自动**——强制某个 kind 会跳过 chooser，但**不会**跳过 `MixNaturaliser`
   （变速/改调/低频是按对子判断的，与 kind 无关）。
2. 可选清理：**已废弃的窗口覆盖层死代码**（`videoOverlay` / `videoSurfaceView` / `videoOverlayParent` /
   `videoOverlayOutline` 四个字段 + `installVideoOverlay` / `positionVideoOverlay` / `hideVideoOverlay`），
   **全仓库无调用**。同一类死代码还有 `QmlLyricPage`（含歌词偏移面板）、`QmlLyricColumnRestoredExperimental`。
3. 收藏夹可选增强：夹内容页目前进入即整夹加载（≤25 页×40），超大便分页；私密夹未专门处理。

## 六、已验证的外部事实（别再重新试）

**B站 API**（实测结论，`curl` 均可复现；UA 用浏览器 UA + `Referer: https://www.bilibili.com/`）

- **收藏夹全部接口都不需要 wbi 签名**（PiliPlus 的 `fav.dart` 也没用 wbi）。
- `/x/v3/fav/folder/created/list-all?up_mid=<自己>`：**未登录时返回 `code:0` 但 `data:null`**，
  不是 `-101` —— 必须把 `code:0 && data==null` 当"未登录/非本人"，否则静默变成"你没有收藏夹"。
  带 `rid=<avid>&type=2` 时每个夹对象带 `fav_state`（1=已收藏）。
- `/x/v3/fav/resource/list?media_id=<夹id>&ps=<≤40>`：**`ps` 必填且最大 40**（41 → `-400`）；
  空夹返回 `medias: null`（不是 `[]`）。条目：`id`=avid、`bvid`/`bv_id`=BV、`duration`=**秒**、
  `upper.name`=UP主、`ugc.first_cid`=**分P cid（可直接入队，省一次 pagelist）**。
- `/x/v3/fav/resource/batch-deal`（**POST** form-urlencoded）：`resources=<aid>:2`、
  `add_media_ids`、`del_media_ids`、`csrf`；csrf 从 cookie `bili_jct` 取，作**表单字段**发。
  缺 csrf 或值错 → `-111 CSRF 校验失败`；GET 该端点 → 405。
- `/x/player/wbi/v2?bvid=&cid=`：`data.view_points[]`，`from`/`to` 是**秒**，`content` 是标题；
  **不需要签名**。
- 登录态 cookie：App 存在 `filesDir/bili_cookies.txt`（`run-as dev.t1m3.qplayer.debug cat`），
  只有 `SESSDATA`/`bili_jct`/`DedeUserID` 等，**没有 buvid** → 某些接口可能撞 `-352` 风控，
  必要时调 `/x/frontend/finger/spi` 取 `b_3`/`b_4` 并缓存。
- 媒体流必须带 `Referer: https://www.bilibili.com/`（`AndroidAudioBackend.setDataSource` 已处理）。

**Android 平台能力**（AI DJ 分析用得上）

- 解码 PCM：`MediaExtractor` + `MediaCodec`（零依赖）。
- 同时出声：两个 `MediaPlayer` 即可，AudioFlinger 自己混。
- 每路音量：`MediaPlayer.setVolume`；EQ/低频：`Equalizer`/`BassBoost`/`LoudnessEnhancer`
  （按 `getAudioSessionId()` 挂）。
- **保音高变速**：`MediaPlayer.setPlaybackParams(speed, pitch)` —— 小幅 BPM 对齐够用。
- ⚠️ **采样率不是恒定的**：用户库里同一批歌既有 48kHz 也有 44.1kHz（`MediaFormat.KEY_SAMPLE_RATE` 实测）。
  htdemucs 只吃 44.1kHz —— 把 48k 样本直接喂进去 = 同一段音乐慢 8.8%、低一个纯五度，而且所有"秒"都错位。
  **任何分离/分析路径都必须先重采样**（harness 里是 `BlendBench.toRate`）。
- ⚠️ **dex 工具链**：`android-sdk/build-tools/34.0.0/d8.bat` 是 **R8 8.2**，遇到带**嵌套类**的 class
  （= 任何 Java-11 语言的 `NestMembers`）会 `NullPointerException: Cannot invoke "String.length()"`；
  用 gradle 缓存里的 **`com.android.tools:r8:8.13.17`**（`java -cp r8-8.13.17.jar com.android.tools.r8.D8`）
  就正常 —— 那也正是 AGP 自己用的那个。另外 `javac --release 8` 能避免 nest 属性。
- ❌ **平台没有任何 BPM/节拍/调性/结构分析 API**，这部分必须自己写 DSP。
- `Visualizer` 能拿实时 FFT，但需要 `RECORD_AUDIO` **且只能拿输出混音** → 不推荐，优先解码文件。

## 七、AI DJ 过渡计划（下一个大功能）

**前置结论：单个 `MediaPlayer` 无法同时播两首歌，所以真正的交叉淡化必须先做 P1。**

- **P1 双播放器后端 —— ✅ 已实现（编译通过，未装机、未试听）**
  - `AndroidAudioBackend` 新增：`prepareIncoming(src,startMs[,startMuted])->bool` /
    `beginCrossfade(ms[,curve])->bool` /
    `cancelIncoming()` / `setOnCrossfadeComplete(Runnable)` / `setOnCrossfadeAbandoned(Runnable)`。
    incoming 用与 `play()` **同一条** `setDataSource` 路径建（含 bili 的 Referer/UA），`setVolume(0,0)`
    后 `start()` 先静音跑；**不再 requestFocus**（焦点是 per-backend，活跃那路已持有）；
    **视频 surface 绝不交给 incoming**，只在晋升后重挂到新活跃播放器；`applyVolume()` 在淡化期间
    提前返回，避免控制器的淡入淡出曲线压掉斜坡；`pause()`/`seek()`/三种焦点丢失都会 `abortCrossfade`；
    `releasePlayer()` 清两路。
  - `AudioBackend` 接口加了 7 个 `default` 方法（默认返回 false / 空实现），所以桌面后端与测试假后端结构上
    不可能"半淡化"。
  - `PlayerController`：`setTransitionEnabled(bool)`（旧名 `setCrossfadeEnabled` 保留为别名，**默认开**）。
  - 回落（全部回落到老路径，播放绝不受影响）：非可流式源（BILI/LOCAL）、单曲循环、私人FM、一起听跟随者
    都不 arm；队列被移动 / 拿不到 URL / `prepareIncoming` 或 `beginCrossfade` 返回 false / 剩余 <1.5 秒
    → 放弃并回到硬切；incoming 出错、暂停、seek、焦点丢失 → `onCrossfadeAbandoned` 后按老逻辑走边界。
  - **未验证 / 已知风险（下一轮优先处理）**：
    1. **从没被听过**（当时无设备）。只有"能编译 + 状态机可回落"是确定的。
    2. **线性增益斜坡**（`base*(1-t)` / `base*t`）—— 中点会有约 **3 dB 凹陷**（已用程序确认：线性斜坡
       中点功率和 = 0.5，等功率 = 1.0）。现在可在设置里直接切「淡化曲线」对比。
    3. 曲末竞态**偏向老路径**：后端不吞 outgoing 的 `onCompletion`（吞了若 incoming 再出错会把队列卡死），
       所以若 MediaPlayer 报的位置/时长偏乐观超 250ms，`autoAdvance` 会赢 → 硬切（丢过渡，但不会丢播放）。
       时长报错或为 0 的曲子**根本不会 arm**。
    4. 斜坡起点受 Compose 10Hz pump 量化（±100ms），与第 3 点相互作用。
    5. 暂停/seek/焦点丢失会放弃重叠（正确性优先）。
    6. 成本：每次可过渡的切歌都会多一个解码器静音跑最多约 4 秒；桌面端 `prepareIncoming` 返回 false，
       但每次曲末仍会白做一次解析（会走到 `abandonTransition` → 硬切）。
- **P7 过渡目录 + 选择器 + 设置开关 —— ✅ 已实现（编译通过，未装机、未试听）**
  - 新增（全在 `player-core/.../audio/`）：`TransitionKind`（CUT/CROSSFADE/QUICK_FADE/FADE_OUT_IN/
    SILENCE_TRIM，各带中文名与"需要的重叠窗口"）、`FadeCurve`（LINEAR/EQUAL_POWER，`outGain/inGain`）、
    `TransitionContext`（只有元数据：两首 Track + 剩余毫秒 + 两侧是否可流式；**Track 没有 BPM/调性字段**）、
    `TransitionChooser`（`TransitionKind choose(TransitionContext)`）、`HeuristicTransitionChooser`（默认实现）、
    `SilenceProfile` / `SilenceProfiler`（静音测量接缝）。
  - `PlayerController` 公共接缝：`setTransitionEnabled` / `setTransitionChooser` / `setTransitionKindOverride`
    / `setFadeCurve` / `setSilenceProfiler`。**没有调用 `AiClient`**（按要求只留接缝）。
  - 自动规则（`HeuristicTransitionChooser`，逐条短路）：两侧非可流式（BILI/LOCAL）→ CUT；剩余 <2s → CUT；
    任一侧时长未知 → CUT；任一侧 <90s → QUICK_FADE；同专辑（albumId 相同，或名字相同）→ CROSSFADE；
    两侧都 ≥150s → SILENCE_TRIM；其余 → FADE_OUT_IN。选择器返回的任何 kind 后面还要被
    `decideTransition` 校验（需要第二个播放器但下一首不可流式 → 降级 CUT）。
  - 设置（`SettingsCatalog` + `SettingsCore.pushTransition`，UI 由目录自动生成，Android/桌面同一套）：
    `smartTransition` bool（默认 **开**）、`transitionKind` 0=自动/~5 个 kind、`transitionCurve` 0=线性/1=等功率。
    设置是唯一真值源，改完立刻生效（`setTransitionKindOverride(null)` = 交回选择器）。
  - SILENCE_TRIM：`AndroidSilenceProfiler`（`MediaExtractor`+`MediaCodec`，10s 窗口 / 4s 上限 / 20ms 块 RMS
    -46dBFS；**尾端必须解到 EOS 才算数**，否则中段的安静会被误当成尾部静音），结果按
    `silenceKey`（neteaseId / customId / 源串）存 `DiskCache` 新的 `silence/` 子目录（8 字节，LRU 一起参与淘汰）。
    单次最多跳过 3s（`MAX_TRIM_SKIP_MS`），避免"测量的是另一份音源"时一刀切掉音乐；测量不到 → 直接 CUT
    （不退回交叉淡化：选它的理由就是"别把两首歌混在一起"）。
  - **P7 未验证 / 风险**：全部未试听；静音测量只在 Android 有（桌面 `SilenceProfiler` 为空 → 永远 CUT）；
    `MediaPlayer.start()` 的启动延迟（约 0.1–0.3s）会让裁切缝变成"略微留白"而不是"略微重叠"（安全方向）；
    被裁切的那一路是**停放（不滚动）**的，等待期间白占一个解码器与连接；
    流媒体上每次边界最多多读 2–4 个 10s 窗口的音频（头部测量 + 尾部测量）。
  - **2026-09-19 真机定位（首次有设备）**：`自动` 模式下每个边界都止步于
    `transition: no playable source for slot N ... using the hard cut`（`armIncoming` 收到 null）。
    根因：`PlayerController.resolveIncomingSource` 的 NETEASE 分支**只问 `netease.songUrlInfo`
    且拒绝 trial，没有 `SongUnblocker` 兜底** —— 而普通播放路径（`resolveAndPlayNetease`）
    对同一首歌恰恰是靠 unblock 才拿到 URL（真机日志里每次都是 `unblock: resolved ... via NETEASE`）。
    于是"整库靠换源播放"的用户每个边界都拿不到 incoming 源，**任何 kind 都退化成硬切**。
    已修：`unblockEnabled` 时按与普通路径相同的顺序补 `SongUnblocker.resolve(songId, title, artist)`；
    两者都拿不到 → 仍然是 `abandonTransition` → 硬切。另一半原因见下一条。
  - **诊断日志（判断走哪条分支）**：`adb logcat -d | grep musicplayer | grep transition`
    - 每个边界一行：`transition: slot a -> b: KIND (why); remaining=..ms, length=../..ms, streamable=../.., 智能过渡=on`
      （`why` = `自动 rule` / `forced by 过渡方式` / `too late: ...` / `<kind> cannot be performed into BILI|LOCAL`）。
    - 边界被门挡住时**每个 track 一行**：`transition: tick running, slot N is not transitioned: <原因>`
      （设置关 / 源不是 NETEASE|CUSTOM_API / 队列只有 1 首 / 单曲循环 / 私人FM / 一起听跟随者 / 未在播放 / 时长未知）。
    - 放弃时 `transition: <原因>`（`no playable source ...`、`SILENCE_TRIM: no measurement in time`、
      `no room left to ...`、`backend refused ...`）。
    - 重叠类 kind 的那一行末尾还有 **P4 的一段**：`; beat: A=<bpm>BPM/<conf> B=... align=on|off (...)`，
      写明两侧 BPM/置信度、是否对齐、对到的重叠长度与拍数、drift、incoming 的入口偏移。**看不到 `align=`
      说明这个边界不是重叠类 kind**（CUT/淡出淡入/静音裁切都没有可对齐的重叠）。
    - **每个边界还有一段 `mix:`（第五轮起）**：做了什么（速度比例 / 移调半音 / 低频互换时刻，
      不做也写明 `x1.0000` 与原因），或者为什么不（`no credible grid for B, confidence 0.29 < 0.35,
      prom 0.74` / `tempos too far apart: …` / `keys clash (…)` / `合拍改调 is off (settings)` /
      `CUT never has both tracks audible`）。**判据：从这一行就能区分"没混音（及原因）"与"这个包没有混音"**。
    - 拍格本身：`beat profile for <key>: BeatProfile{97.0BPM/0.31 (prom 0.74), ...}`（算出来并写缓存）/
      `beat profile loaded for <key>: ...`（磁盘命中）/ `beat probe gave up for <key>`（无拍、超时、
      非 16-bit、解码失败 —— 都是正常的，只表示这个边界不对齐）/
      `beat probe: no source for <key> yet`（早探连源都没解析出来，等于这一轮不对齐）。
      **注意**：`beat profile for` 出现在**起播后几秒**（当前曲）或**preload 时**（下一首）属于正常；
      这两条正是下面那条修复的目的，见第七节末尾。
      **括号里 `prom x.xx` 是旧的突出度读数**（第五轮换掉的那个判据，留着对比；校准完成后可以摘掉）。
    - 下一首的音频预缓存（第五轮起）：`transition: pre-caching the next track (<歌名>), Ns of it to fetch
      before the boundary` → `transition: pre-cached the next track (<歌名>): NKB on disk, …`；
      边界那行旁边 `transition: incoming slot N (<歌名>) served from the audio cache, nothing to resolve`
      （网络现解析时是 `source: official=…, unblock=…, playable (not cached: resolved inside the
      boundary's window)`）。看不到预缓存行 = 当前曲不是普通队列播放（单曲循环/私人FM）或设置关。
    - **一条 `transition:` 都没有** = tick 没在跑（前台 pump 未运行）或装的是旧包，而不是"过渡坏了"。
  - **剩下最可疑的一环（未验证）**：自动模式对"两侧都 ≥150s"的曲子一律选 `SILENCE_TRIM`，而它要求
    两端的静音测量在 `remaining` 从 9000ms 走到 3000ms（`SILENCE_TRIM_DECIDE_MS`）之间到达；
    incoming 那一路若不在磁盘缓存里，先要经历 songUrlInfo + unblock（真机实测 1–9s）再开始解码测量，
    很可能赶不上 → `no measurement in time` → CUT。想先听到效果，可在设置里把「过渡方式」强制成
    交叉淡化/快速淡化/淡出淡入，那几种不依赖静音测量。
    （注：`requestSilenceProfile` 会随 preload 一起把相邻几首的 `.sil` 写进磁盘缓存，真机实测
    第二次之后的边界基本都是"缓存命中"，SILENCE_TRIM 能正常走下去。）
  - **2026-09-19 P0：晋升即死（已定位并修复）**。现象：`transition promoted` 之后
    `transition: tick running, slot N is not transitioned: the player is not playing`，
    从此无声、队列永不前进。
    根因：`AndroidAudioBackend.promoteIncoming()` 在**释放 outgoing 之前**就调用
    `in.setSurface(videoSurface)`，而 SurfaceView 的 buffer queue 此刻正被 outgoing 这一路
    （同一个 media service 进程里的另一个 client）连着。平台侧
    `MediaPlayerService::Client::setVideoSurfaceTexture` 连不上时**会把自己这个 player `reset()`**
    （AOSP 原文注释："we must do the reset before disconnecting from the ANW"），
    于是被 reset 的正是**刚晋升上来的那个 player** → `isPlaying()=false`、`getDuration()` 返回
    `-2147483648`、`MediaPlayerNative: internal/external state mismatch corrected`，
    而 reset 的 player 既不会 `onCompletion` 也不会 `onError` → 边界永远不再触发。
    （真机可复现证据：app 日志 `promoted` 后 2ms，media server 里
    `SurfaceUtils: Failed to connect to surface 0xf45c3688, err -22` →
    `MediaPlayerService: setVideoSurfaceTexture failed: -22` → `NuPlayerDriver: reset(0xf4e8fc80) at state 5`，
    而 0xf4e8fc80 正是 incoming 的那一个。）
    修复（`AndroidAudioBackend`）：
    1. 顺序改为 装监听（`onPrepared`/`onPlayerError`/`onCompleted`）→ 赋值共享状态 → **只释放 outgoing**
       → 最后才把 surface 交给晋升者，且**只交给真有画面的源**（`isBiliCdn(source)`，而过渡本来就不可能
       arm 到 BILI/LOCAL），所以音频晋升这一步彻底不碰 surface。
    2. 晋升前先校验 `in.isPlaying()`：不通过就**在释放任何东西之前放弃**（`cancelIncoming()` 把 outgoing
       的音量还原 + `onCrossfadeAbandoned`），回到普通硬切 —— 走到"没声且不前进"在结构上不可能。
    3. 晋升后的 player 换成普通监听器，否则 reset/错误被 `onIncomingError` 静默吞掉，队列卡死。
    真机验证（`transition:` + framework 日志）：slot 3→4、4→5 连续两次 `SILENCE_TRIM` 晋升，
    晋升后**没有** `not transitioned` 行、位置继续前进、media server 只 reset outgoing 一个 player；
    晋升后的那首放到自然结尾 → `MediaPlayer: completed` → `play netease (audio cache): ...` → 下一首正常起播。
- **P2 顺序淡化**：已由 `FADE_OUT_IN` + 控制器里的 `transitionFadeIn` 覆盖（淡出→起 B→淡入，
  B 的淡入不受单独「淡入淡出」设置影响）。
- **P6 AI 选择器（2026-09-19 本轮新增，已装机验证）** —— 提前做了 P6（P5/P4 仍未做）。
  - 新增：`audio/TransitionPlan`（kind + 重叠长度 + 曲线 + 谁决定的 + 8 字节磁盘格式 +
    `trackKey`/`pairKey`）、`audio/AiTransitionChooser`。`TransitionChooser` 加了一个
    `default TransitionPlan plan(ctx)`（**`choose` 没动**：旧 chooser 照旧可用，默认重叠 =
    CROSSFADE 的 medium 8s；heuristic 因此"默认 medium"）。
  - **长度三档**：`TransitionPlan.OVERLAP_SHORT_MS=4000` / `MEDIUM=8000` / `LONG=15000`。
    本地规则对重叠类 kind 给 medium；AI 可挑。**长重叠自动用 EQUAL_POWER**（除非 AI 明确指定曲线），
    并在日志写 `长重叠改用等功率，覆盖设置的X`。
  - **门（两把，缺一即完全等于今天）**：`PlayerController.setAiTransitionConfig(baseUrl, apiKey,
    model, timeoutMs)`，由 `SettingsCore.pushTransition()` 用 **aiBaseUrl/aiApiKey/aiModel/aiTimeoutMs**
    推送 —— 就是「AI DJ」那三个值，没有第二套配置/第二个 key 输入框。空地址或空模型 → 装回
    `HeuristicTransitionChooser`。`smartTransition` 关 → `tickCrossfade` 直接不进（且 prefetch 也不发）。
  - **预取在 `preloadAdjacent()` → `prefetchAiTransition()`**（每首起播时问"下一对"，离边界还有几分钟）。
    边界 `decideTransition` 调 `transitionChooser.plan()`，只读内存 → 磁盘，**没有就问不出来时用
    `HeuristicTransitionChooser`**，并顺手在后台补问（下次/下次启动就有）。**播放路径上结构上没有网络调用**：
    问跑在 `qplayer-ai-transition`（单线程守护，进程内共享）上。
  - **缓存**：`DiskCache` 新子目录 `TRANSITION="transition"`，键 = `pairKey`（`n<id>`/`c<id>`/`s<path>`
    拼 `A>B`，与 silence 同一套 trackKey —— `PlayerController.silenceKey` 现在就是
    `TransitionPlan.trackKey`），文件 `abs(key.hashCode())+".trn"`，8 字节。已计入 `totalSize`/
    `evictIfNeeded`/`clearAll`，另按文件数上限 `TRANSITION_MAX_COUNT=2000` 淘汰。`prefetch` **也先查磁盘**
    → 同一对永不重复问，离线/重播照常。
  - **提示词契约**：系统提示（`AiTransitionChooser.SYSTEM_PROMPT`，中文全文在源码里）枚举 5 种 kind +
    三档重叠 + 两条曲线，要求"只输出一行 `KIND OVERLAP CURVE`"，无把握只输出 `NONE`；解析只取第一段非空
    行的前 8 个 token（容忍 ``` 围栏和 `答案:` 前缀），**unknown kind / NONE / 空 / 超 240 字符 → 无意见 →
    本地规则**；unknown 重叠 → 该 kind 默认；unknown 曲线 → 设置里的曲线（长重叠仍强制等功率）。模型的原话
    只进日志，不进状态机。**AI 听不到音频**（只有元数据 + 先验），这一句写在提示词里、类注释里和报告里。
  - **超时**：`AiClient` 新增 `chatPlain(system,user)`（不带 `response_format`）。原因实测：
    DeepSeek 在 prompt 里没有 "json" 字样时**直接 400 拒绝** `response_format: json_object`，
    `chat()` 得先付一次 400 再重试。同一账号、同一提示词、同一模型实测 **4.5s 与 13.3s 各一次**
    （推理模型的 reasoning 开销不固定），所以超时 = `clamp(设置值, 15s, 60s)`（原来只给 20s → 真机
    直接 timeout，虽然按设计回落了，但等于白配）。
  - **长窗口能真的跑出来（本轮改的守卫，全在 PlayerController）**：
    1. 决策窗口：`CROSSFADE_LEAD_MS`(9s) 不再是决策提前量，而是**解析预算**；真正的提前量是
       `TRANSITION_DECIDE_LEAD_MS = LONG + TAIL + 9s = 24_250ms`。原先固定 9s 窗口 + 15s 重叠
       = 永远只能跑 ~8.75s（**这就是"长选项被静默降级"的那个 bug**）。
    2. 装配时刻按计划算：`transitionArmLeadMs = 重叠 + TAIL + 9s`，重叠类 kind 在 `tickCrossfade`
       里**等这个窗口才 armCrossfade**（决策可以早，装配不早：早装配只是白占一个解码器+连接）。
    3. 斜坡起点 `remaining <= overlap + TAIL`（原来 `<= overlap`，再 `min(overlap, remaining-TAIL)`
       → 每次都比计划短 250ms）。真机 15000 → **14993ms**。
    4. 边界比计划近时：`decideTransition` 把重叠裁到 `remaining - TAIL`，并把原因**写进 plan 的
       decidedBy**（日志 `(AI; capped to what is left (4156ms))`）—— 不再静默变短。
    5. 短歌守卫：任一侧 <90s 时把 long/medium 降为 short（`AiTransitionChooser.clamp`，带原因）。
    6. 旧守卫保留但只在"起播时就已经接近结尾"才会命中，且都有日志：`remaining <= 1500ms → CUT`
       (`too late: ...`)、heuristic 的 `<2s → CUT`、`remaining < CROSSFADE_MIN_MS → no room left`。
  - **incoming 从内容起点开始**：重叠类 kind 改用 `IncomingMode.PARKED` + `contentStartMs()`（=
    `SilenceProfiler` 的 headMs，封顶 `MAX_OVERLAP_HEAD_SKIP_MS=3s`）。原 ROLLING 会让 incoming
    静音滚动一段时间、起点漂移不可控；PARKED 由 `beginCrossfade` 起播，偏移就是内容起点（真机
    `arming slot 7 behind slot 6 (incoming starts at 60ms, its own content start)`）。**缺测量 → 0**
    （照旧从文件头开始，不停不炸），并在 arm 时补测该轨（下次同对用得上）。
  - **真机验证（2026-09-19，efaa83b2，AI=DeepSeek）**：
    - `prefetch (preload): HEARD OF US -> Pray 4 Love` → 22s 后
      `AI 过渡决策 ...: CROSSFADE[short 4000ms, EQUAL_POWER, AI] (reply="CROSSFADE SHORT EQUAL_POWER")`
      → 边界 `slot 4 -> 5: CROSSFADE 重叠=short 3906ms, curve=EQUAL_POWER (AI); remaining=4156ms`
      → `crossfade begin over 3008ms` → `promoted queue slot 5`。
    - 缓存命中不再问模型：`AI decision loaded from cache for ...: CROSSFADE[medium 8000ms ...]` →
      边界 `(AI cached)` → 17.25s 处 arm → `ramping 7903ms` → promoted →
      `dumpsys audio` = `state:started`（**晋升不死，P0 修复仍成立**）。
    - 长重叠：`slot 6 -> 7: CROSSFADE 重叠=long 15000ms, curve=EQUAL_POWER (AI cached)` → 立刻 arm
      → `ramping 14993ms` → promoted → `state:started`；重放同一对时 `incoming starts at 60ms`。
    - 失败即回落：一次 `AI 过渡决策 failed for ...: timeout` → `无意见 ... 用本地规则`，边界照常。
  - **P6 未验证 / 风险**：`smartTransition=off`、AI 未配置两条门只做了代码路径检查（没在真机上切成那
    两种状态跑过）；**听感无法判断**（这台机器只能看日志）；决策提前量 24.25s 让每个边界更早拿计划
    （SILENCE_TRIM 的测量窗口因此变长，其它 kind 只是更早 arm）；`clamp` 的短歌降级、`NONE` 频繁
    （模型对"听不到的音频"很保守）都还没有跨风格的样本量。
  - **AI 的诚实边界**：文本模型**听不到音频**，不知道 BPM/调性/结构；15s 重叠在**没有对拍**（P4 未做）
    时，对"听着不搭"的两首会比 4s 更糟 —— 所以 AI/规则选哪两首值得混，比长度本身更重要。
    想直接听某个 kind 的效果，把设置「过渡方式」设成它（此时不走 AI）。

- **P4 节拍分析 + 重叠对齐（2026-09-19 落地；同日第二轮装机验证并修掉"探针太晚"，见本段末尾）**
  - 新增（全在 `player-core/.../audio/`）：`BeatProfile`（BPM / 周期 / 首拍偏移 / 置信度，12 字节磁盘格式，
    `beatAtOrAfter` / `shiftToBeatMs` / `trustworthy` / `gridsCompatible` / `gridDriftMs` / `label()`）、
    `BeatProfiler`（接缝，形状与 `SilenceProfiler` 一致，`probe(source, durationMsHint)`，hint 未用）、
    `BeatAnalysis`（**纯 Java** 的估计器）。Android 侧新增 `AndroidBeatProfiler`。
  - **估计方法**（`BeatAnalysis`，无依赖、无 FFT）：40ms 帧 / 10ms hop 的帧能量 → log 压缩后一阶差分 +
    半波整流（= 廉价的 spectral flux 替身）→ 减 150ms 局部均值（自适应白化）→ 再宽化一次（3 抽头
    `[1/4,1/2,1/4]`，**关键**：onset 只有一帧宽时，分数 lag 上的自相关会让 2 倍/3 倍周期比真周期还高，
    实测差到 12%）→ 在 **0.1 hop 细 lag 网格**上算归一化自相关（插值到分数 lag，否则 160 BPM 的
    37.6 hop 周期必然输给它的 2 倍）→ 每个候选取 `r(L)+r(2L)/2+r(3L)/3` 的加权均值（谐波求和）→
    **beat-rate 启发式**：所有局部峰里取"最短且强度 ≥ 最优的 90%"的那个（纯周期信号上拍和它的 2 倍
    在数学上不可区分，只有"听者会跟着更快的那个点头"这条先验能分开；这就是为什么 175 BPM 报 175 而不是 87）→
    相位用**分数周期**的脉冲串扫一遍，取收集 onset 能量最多的偏移 → 置信度 = 峰的突出度（±半周期窗口内的最低值之差）。
  - **有界且便宜**：窗口 30s（`WINDOW_MS`）、deadline 8s（`DEADLINE_MS`）、降到约 11kHz 单声道
    （`TARGET_RATE`，整数抽取 + 声道平均）、少于 8s 可用音频就放弃；单线程守护 `qplayer-beat`，
    **故意不与静音测量共用线程**（静音测量有 9s 硬窗口，被拍测量挡在后面会毁掉一次 trim）；
    每轨只算一次（`beatProfiles` 内存 → `beat/cache` 磁盘）；失败一律返回 null（无音轨 / 解码器起不来 /
    非 16-bit PCM / deadline / 没有 onset / 置信度不足 / 没有可用拍）。
  - **请求时机（2026-09-19 第二轮修正，见下面的「探针太晚」）**：起播预热（`warmCurrentSilenceProfile`
    顺带，**只在源已经知道时才算数**）/ preload 相邻曲（音频已在本地缓存时）/ `armIncoming` 的 PARKED
    分支（incoming 的源第一次存在时）/ **起播后 4s 的当前曲预热**（`warmCurrentTrackProfilesSoon`）/
    **preload 时对下一首的无条件早探**（`requestEarlyBeatProfile`，不管音频在不在本地）。
    **从不阻塞播放**：拿不到就是这一轮不对齐。
  - **对齐规则（只作用于重叠类 kind；`SILENCE_TRIM`/`FADE_OUT_IN` 不参与** —— 代码里写了理由：
    trim 是"A 淡出、B 从内容起点接上"，把它挪半拍是音乐的洞而不是对齐）：
    1. 两侧网格都存在且 `confidence ≥ BeatProfile.MIN_CONFIDENCE = 0.35`（合成材料实测：click ≥0.9、
       4/4 变化重音 0.38、无拍 pad 被 onset 门直接拒、语音状 0.23）；
    2. 重叠长度取**离 chooser 要的长度最近的"整拍 A 数"**：斜坡起点 = `dur - 250ms - overlap`，
       所以 `overlap ≡ dur - 250 - 首拍 (mod 周期A)` 时斜坡正好落在 A 的拍上；"最近"最多挪半拍，
       随后**重新套用"还剩多少"的上限**（所以长重叠仍然 arm 得够早、仍然完整听到）；
    3. 两侧网格必须**兼容**（`gridsCompatible`：整段重叠内两网格相位滑移 < incoming 的半拍；
       等价于约 4s 容忍 6% 速度差 / 8s 3% / 15s 1.6%）—— 不兼容就**整个跳过对齐**，不硬凑；
    4. incoming 入口 = "它自己内容起点之后的第一个拍"，位移上限 `MAX_BEAT_ENTRY_SHIFT_MS = 400ms`，
       且不超过 silence 那 3s 的上限；超了就保留内容起点。
    缺网格 / 低置信 / 不兼容 / 未启用 → **完全等于 P4 之前的行为**，并在日志里写明是哪一条。
  - 设置：`beatAlign`「节拍对齐」（默认开，`dependsOn(SMART_TRANSITION_KEY)`），由
    `SettingsCore.pushTransition` 推 `setBeatAlignmentEnabled`，UI 由目录自动生成。
  - **日志（每边界仍然只有一行）**：重叠类 kind 在行尾追加一段，**真机实测两种都有**：
    `; beat: A=160.0BPM/0.52 B=160.1BPM/0.59, align=on (overlap 4000->4026ms = 11 beats of A, drift 2ms of 187ms; entry 0->59ms)`
    或 `; beat: A=120.1BPM/0.81 B=151.2BPM/0.12, align=off (confidence 0.12 < 0.35)`；
    另外还有 `align=off (no grid for B)` / `off (tempos incompatible: the grids slide 320ms apart over 15000ms,
    more than half a beat of 96.0BPM)` 这两种措辞。
    估计器自己另外两行：`beat profile for <key>: BeatProfile{...}`（算出来并缓存）/
    `beat probe gave up for <key>`（没有拍）/ `beat profile loaded for <key>: ...`（磁盘命中）。
  - **估计器已用已知答案核对（本轮唯一有真值的验证）**：
    · **纯 JVM harness**（直接喂合成信号给 `BeatAnalysis`）：60/70/90/100/120/128/140/160/175/190 BPM 的
      click track **全部命中**（最大误差 0.11 BPM），相位误差 ≤19ms（多数 ≤10ms）；128 BPM + 八分 hat、
      100 BPM 4/4 重音、±20ms 摇摆 118 BPM 也都命中；pad / 白噪声 / 数字静音 / 5s 窗口 → null；
      语音状噪声 → 0.227（被门挡住，harness 里显示为 MISS，属于"故意保守"）。
    · **Android 解码路径 harness**：把平台类做成桩（`MediaExtractor` 读真 WAV、`MediaCodec` 直通、
      `MediaFormat`/`Context`/`Uri` 最小实现），**跑的是仓库里那份 `AndroidBeatProfiler`**：
      96/128/150 BPM 全部命中、相位误差 ≤10ms、无拍 pad → null，且与"同一份 WAV 手工解出来的结果"逐位一致。
      **这一步抓出两个真 bug**：① 降采样按"16-bit 单元"而不是按帧循环 → 立体声下实际输出速率只有声称的
      一半（128 BPM 报成 64）；② 相位原点取的是"第一个非 0 时间戳的缓冲"（= 第二个缓冲）→ 整格晚一个缓冲
      （实测 +23ms）。两个都已修，修完 harness ALL OK。
    · **重叠对齐的算术**（`BeatProfile.snapOverlapMs`，纯函数，所以能直接测）：20000 组随机网格
      （55–200 BPM、任意相位、任意长度/剩余/请求）验证不变量 —— 斜坡起点**精确落在 A 的拍上**、
      跳过只在"确实连一拍都放不下"时发生、套用上限后不会越界，离请求最远正好半拍；
      再用真实估计器跑一条 122 BPM 的 click track：请求 15000ms → 14941ms（= 30 拍），斜坡相位 0。
  - **P4 未验证 / 风险（重要）**：
    1. **本轮没有装机**：`adb devices` 全程为空（任务里说的 `efaa83b2` 不在线；重启 adb、`mdns services`
       都试过；仓库那台 AVD 没有 system image，模拟器起不来）。所以"真机解码真实 MP3/AAC"、"拍格缓存命中"、
       "对齐之后播放照常不中断"这三件事**没有证据**。目前的把握只来自：编译通过 + 已知答案的合成验证。
    2. 真机解码那段是**照抄静音测量那条已有实测的路径**（同一个 `MediaCodec` 循环、同样依赖
       `INFO_OUTPUT_FORMAT_CHANGED` 回调、同样假设输出缓冲 `position=0`），但它仍是另一份代码，未实测。
    3. **错 BPM 仍是最大的风险**：置信度门 + 位移上限 + 兼容门是三层层，但都不构成证明。一个"置信度高
       但 BPM 错"的网格（例如把明显的八分音符当拍点）会让重叠和入口都按错的格走。可听后果是"对不齐"
       （不会比不对齐更糟），唯一的坏方向是把入口推到反拍上（位移 ≤400ms，挡不住）。
    4. **两个时钟不是同一个**：拍格来自 `MediaExtractor/MediaCodec` 的时间戳，`dur` 来自
       `MediaPlayer.getDuration()`；同一个文件两者可能差几十 ms（VBR / 编码器 padding），这个差没有测量，
       所以"斜坡落在 A 的拍上"只能准到这个量级。
    5. 模型只有"一个周期 + 一个相位"：变速、渐慢、bar/section 结构一概不认。
    6. 成本：每首歌首次多解码一个 30s 窗口（流媒体 = 多下载 30s）；不同 kind 都会请求（和静音测量一样，
       被 gate 挡掉时等于白读一次）。
    7. `BeatProfile` 可能对"有拍的语音/说唱"给出低置信度网格；使用方**必须自己查 `trustworthy()`**。
  - **2026-09-19 第二轮：P4 真机验证 + "探针太晚"的修复（已装机，efaa83b2）**
    - **症状（真机日志，决策行）**：`slot 12 -> 13: CROSSFADE 重叠=medium 8000ms, curve=EQUAL_POWER (AI); …
      beat: A=none B=none, align=off (no grid for A)`。**不是**置信度拒、**不是**速度不兼容 —— 决策那一刻
      两侧都没有拍格，而估计器明明是好的（同一次会话里算出 5 个 profile：135.8/0.27、96.6/0.40、
      160.0/0.52、128.1/0.80、106.6/0.29，3 个过 0.35 门、BPM 都合理）。
    - **根因**：profile 只在"换歌/preload"时才产生。
      · **A（当前曲）在 restore/resume 起播时根本没人问**：`warmCurrentSilenceProfile` 是在 playAt 末尾
        跑的，而那一刻 streamUrl 还没解析出来、音频也没进本地缓存 → `measureSourceOf` 返回 null →
        `requestSilenceProfile`/`requestBeatProfile` 都是 no-op。**从磁盘恢复的队列**最典型：App 起播的
        那一首从来没当过别人的"下一首"（preload 跑的是它后面那首），于是它自己的边界两侧都是 none。
      · **B（下一首）只在 arm 时被测**：`armIncoming` 的 PARKED 分支（边界前约 17s，而**决策在 24.25s 前**
        就做完了 —— 后到的网格那次决策根本用不上），或 preload 且**音频已在本地缓存**时。这个用户的库靠
        unblock 流式播放，B 从来不在本地 → 早探根本没发生。
    - **修复（全在 `PlayerController`，播放路径零改动）**：
      1. **当前曲的预热**：`playBackend(source, startMs)`（所有进入播放的路由的最后一步，**源在手里**）→
         `warmCurrentTrackProfilesSoon(currentTrack(), source, playIndex)`：用新的
         `qplayer-profile-warm` 单线程调度器**延后 4s**（起播那几秒最忙：解析、封面、歌词、存队列；而且
         这些测量永远没人等），任务里再核对 generation（每次起播取一个新号，换歌即作废）与 queue 槽位，
         然后走 `warmTrackProfiles`（静音 + 拍格，源优先用 `measureSourceOf`，否则用 playBackend 传进来的）。
         **BILI/LOCAL 一律不测**（`crossfadeStreamable` 门：过渡既不会重叠也不会 trim 它们）。
         "每轨只测一次"由 `probeBeatProfile` 的 per-key 记账保证。
      2. **下一首的早探**：`preloadAdjacent()` → `requestEarlyBeatProfile(next)`（**只对 next** —— 过渡只会
         重叠到后面那个槽位，给 prev 探是白解析 + 白解码）。没有本地缓存也会测：`probeBeatProfile(t,
         () -> resolveProbeSource(t))` 里那个 supplier 在 **beat 线程上**自己解析 URL
         （`resolveProbeSource`：官方 → unblock → 自定义源，trial 一律不收，与播放路径同序），
         **不写回 `streamUrl`**（写回会让下次普通播放走"缓存 url"快路，跳过元数据补全）。
    - **本轮抓到的自伤 bug**（真机才暴露）：`requestEarlyBeatProfile` 第一版自己先做了
      `probingBeats.add(key)`，而共享尾 `probeBeatProfile` 又查一次"这个 key 在不在飞行中" → 看到自己
      的占用直接静默 return，**一行日志都没有** = 正是它要消灭的 `no grid for B`。已改成记账只归
      `probeBeatProfile` 一处。排查这件事的经验：**那台设备的 `logcat -d` 会滞后几十秒**，别用"刚 dump
      没看到行"当结论。
    - **真机证据**：
      · 恢复的队列起播 → 当前曲的网格不再缺席：`beat profile loaded for n28862639`（+4.5s）/ 流式曲
        `beat profile for n34280327: 133.4BPM/0.34`（+17s，8s 解码在 beat 线程）/ 下一首
        `beat profile for n3347087904: 141.9BPM/0.36`（preload 后 9s，**它自己解析的源**）。
      · **自然边界**（恢复的队列 0→1，I'm Waiting → Towards the Light；AI 缓存给 CROSSFADE medium 8000）：
        `beat: A=120.1BPM/0.81 B=151.2BPM/0.12, align=off (confidence 0.12 < 0.35)` —— 真实门（置信度），
        不再是 `no grid for A/B`；B 的网格正是早探在 4 分钟前测出来的。随后 `arming … (incoming starts at
        0ms)` → `ramping 7995ms` → `promoted queue slot 1`，`dumpsys audio` 只剩一路 `state:started`、
        位置继续前进（**P0 没复发**）。
      · **align=on**（手工把 `queue.json` 写成 [镇海(160.0/0.52), Baby Pluto(160.1/0.59)]、`positionMs`
        落在离曲末 ~60s 处，两个网格都过门且速度几乎相同）：
        `align=on (overlap 4000->4026ms = 11 beats of A, drift 2ms of 187ms; entry 0->59ms)`，plan 标签里
        带 `beat-aligned to a whole number of A's beats` → `arming … (incoming starts at 59ms, its own
        content start)` → `ramping 3946ms` → `promoted queue slot 1 (Baby Pluto)`，之后一路正常。
        **这是 P4 第一次真机跑通对齐**（此前只有 JVM/桩的合成验证）。
    - **仍未验证 / 风险**：`SILENCE_TRIM` / `FADE_OUT_IN` / `CUT` 本轮都没被走到（两次 AI 都给了
      CROSSFADE）；置信度门很紧（`n34280327` 实测 0.34、`n28862639` 0.367，差一点就过），一条边界
      "该不该对齐"经常是掷硬币；早探的代价是每轨每会话多一次 URL 解析 + 一个 30s 窗口（都在 beat 线程，
      8s deadline 兜底），当前曲的预热还会给流式曲补一次静音测量（尾部要读到 EOS，是整文件读，跑在两条
      线程的静音 worker 上）；对齐之后的**听感**依然没法从日志判断。
  - **留给后续阶段**：真正的对拍需要**变速/时间伸缩**（§六 已验证 `MediaPlayer.setPlaybackParams` 可用）：
    把 B 的 BPM 拉到 A 的 BPM 再对齐；本轮**没有**做任何变速。P5 低频互换仍未做。AI 的角色没变
    （仍只挑 kind/长度/曲线），而且它**听不到音频、也拿不到 BPM**（提示词没动）。
- **2026-09-19 第三轮：用户报"第二首歌从头播"（本轮只改了回落路径 + 加诊断，未装机）**
  - **用户原话**（`sess_a253fbca`，16:47，听完 15:54 那次 Run B 之后）：
    「基本对了，但是现在如果第二首歌的开头融进了第一首歌的结尾，播放第二首歌时还是会从头播放而非丝滑的融合」
    —— 即 overlap 听得到，但过了边界又从头放了一遍那几秒。
  - **先证伪了两件事（重要，别再查一遍）**：
    1. **promotion 那条路径本身是对的**：`playAt(idx, true)` 的 `resumeMs = backend.position()`
       （`PlayerController` 的 handoff 分支），不重新解析、不重新 prepare、不 seek；`promoteIncoming` 里
       `pendingSeekMs = 0` 且晋升者**只有**普通监听器与音量写入（没有 surface、没有 seek）。
       **真机证据**（从 `sess_a253fbca` 的 logcat 里挖出来的 12:35 那次 CROSSFADE）：
       `crossfade begin over 3008ms` → 3.03s 后 `transition promoted` → 之后 app 发布的
       `PlaybackState{state=PAUSED, position=8450}`（= 晋升点 ~3000ms + 5.45s）**证明位置在继续走**；
       同一时刻的 `AudioPlaybackConfiguration ... state:paused` 是**测试脚本自己按了暂停**（同刻有
       `vibrator`/`RecentsView: onGestureAnimationStart` 手势日志），不是晋升死的。
    2. **`position()` 不会在晋升瞬间变 0**（12:35 那次的晋升点 ≈ ramp 长度，与 ramp 的墙钟一致）。
  - **因此"从头播"只可能来自"过渡被放弃后走老路径"**：老路径 `playAt(i, false)` 里
    `resumeMs = (i == pendingResumeIndex) ? pendingResumeMs : 0` —— 对一首已经被 overlap 播出 3-5 秒的
    歌（incoming 在 ramp 期间一直放着、还渐入过），**这里就是 0**，于是 `playBackend(src, 0)` 从头再放一遍。
    能走到这里的路：（a）**曲末竞速输给 autoAdvance**（老路径的硬切；见 P1 风险第 3 条：只留 250ms 余量，
    MediaPlayer 报的位置/时长偏乐观就会输）；（b）`onCrossfadeAbandoned` 之后边界落到老路径（焦点丢失/
    暂停/seek/用户跳过，incoming 已可闻）；（c）错误重试（`onPlaybackError` 的 resume 取
    `max(backend.position(), positionMs.peek())`，两者都小时同样按 0 起）。
  - **本轮改动（`player-core` + `AudioBackend`/`AndroidAudioBackend`；不新增依赖）**：
    1. `AudioBackend` 新增两个 default 方法：`incomingPosition()`（incoming 若已在滚动，返回它播到哪）与
       `droppedIncomingPosition()`（backend 自己丢弃一个**滚动中**的 incoming 时记下的位置）。桌面后端与
       测试假后端不用改。
    2. `AndroidAudioBackend`：`releaseIncoming()` 在丢一个滚动中的 incoming 前记下它的位置；
       `clearCrossfade()`/`onCrossfadeAbandoned()` 把这个值交给控制器；`prepareIncoming` 清掉上一段的读数。
    3. `PlayerController`：新增 `droppedIncomingMs`/`droppedIncomingIndex`（+`autoAdvanceTarget`
       限定只作用于**自动**过边界那一路）；`playAt(i, false)` 在 `i == autoAdvanceTarget && i ==
       droppedIncomingIndex` 时把 resume 抬到"已经放过的毫秒数"——**手工点歌仍然从 0 开始**，
       没 arm 过过渡的边界完全不变（`incomingPosition()` 返回 -1 时什么都不做）。
    4. **曲末竞速**:`autoAdvance()` 在 `crossfadeRunning`（真的在 ramp，不是只 park）时**不再抢边界**，
       让 ramp 把已可闻的 incoming 晋升掉；`outgoingEndedDuringRamp` + `ADVANCE_DEFERRAL_MS=700ms`
       的看门狗（在 `tickFade` 里）保证"ramp 既不晋升也不回话"时仍然照常前进（P0 不会复发）；
       `onCrossfadeAbandoned()` 发现老曲已经结束时立刻 `performAutoAdvance()`。
    5. handoff 分支的 resume 取 `max(backend.position(), crossfadeIncomingStartMs + crossfadeRampMs)`
       —— **overlap 已经放过的量是下限**（ramp 只在跑满时才晋升），差 >200ms 时 `Logger.warn` 写明
       "promoted player reports Xms but the overlap already played Yms"。**故意不 seek**：晋升瞬间动播放器
       是 P0 的教训，而且分不清"位置报小了"与"真的没播"之前，seek 会把没听过的部分跳掉。
    6. 诊断（永久保留）：`MediaPlayer: setDataSource + prepareAsync (startMs=)`、`MediaPlayer: seek to Nms`、
       `pause at Nms` / `resume at Nms`、四种 `audio focus` 事件（都带位置）、晋升时打印
       "incoming 的位置 vs overlap 已经放过的毫秒数（BEHIND 时点名）"；控制器侧 `transition: promoted
       queue slot N (...) at Nms`、`playAt: slot N starts at Sms, not 0ms — the dropped overlap ...`、
       `skipUnplayable` 现在也写一行（`playback: giving up on slot N (...)`）。
  - **未验证（本轮最大的缺口）**：**没装机**。装机中途设备从 `adb` 上消失（`R5CY10P6MJF`/`SM-S9360`，
    开头还在、改代码期间掉线；`kill-server`/`reconnect`/`mdns`/扫描 LAN 找 5555 都试过；仓库里那台模拟器
    没有 system image），所以：
    1. "overlap 之后不再重播"**没有真机日志证据**，只有代码路径 + 上面 12:35 的历史日志作反证。
    2. 用户听到的到底是 (a) 回落路径从 0 重放，还是 (b) promoted player 的**位置报小了**（那样只会让
       UI/歌词钟倒退，不重放音频），**这一轮没有区分开**——下一轮第一件事就是看新加的两行：
       `playAt: slot N starts at ...` 与 `... BEHIND by Nms ...`。若出现 BEHIND，才轮到"要不要在晋升时
       把播放器 seek 到 overlap 放过的位置"（那是有 glitch 风险的，别在没日志之前做）。
    3. `autoAdvance` 的让位 + 看门狗这条路径没被真机走到过（不知道 250ms 竞速在真机上多久输一次）；它的
       回退（ramp 里 incoming 出错 → abandon → `performAutoAdvance`）也没被走到过。
  - **旁证（`git status` 之外的小发现，别浪费时间去查）**：`mvn -pl player-core test` 现在有 1 个既有失败
    `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（`pageTransitionPreset`
    这个 key 在 `SettingsCatalog` 里只有常量、没有 spec，是并行工作区的半成品），与本轮无关；
    其余 74 个测试通过。
- **2026-09-19 第四轮：快慢/调性/低频（用户要"提前十秒合拍改调子，不满足于淡入淡出"）——
  代码+纯 Java 测试落地，**未装机**（`adb devices` 全程为空：手机从 USB 上物理消失，
  `Get-PnpDevice -PresentOnly` 里连 Android 设备都没有，不是 adb 的问题）**
  - **总的原则**：这三件事（变速、改调、低频互换）**只在"两首歌合适"时发生**，而且**任何一环
    做不到就退回本轮之前的行为**（= 只是淡化/对齐），绝不牺牲 P0 与"第二首不从头播"。
    判断集中在 `audio/MixMatch`（纯 Java），执行集中在 `AndroidAudioBackend`。
  - **① 变速对拍**：`MediaPlayer.setPlaybackParams(speed, pitch)`（保音高，平台 Sonic），**只作用在
    incoming 那一路上**，audible 那首一个字节都不动。
    - 比例 = `periodB / periodA`（= `bpmA/bpmB`）：把 B 播快 r 倍后它的拍周期正好等于 A 的。
    - **clamp = 应用在 B 上的速度 ±8%**（`MixMatch.MAX_SPEED_STEP`）。注意 clamp 卡的是"**施加的
      伸缩**"而不是"BPM 差"：BPM 差 -7.5% 需要 +8.1% 的伸缩，已经在门外（`MixMatchTest` 里踩过）。
    - **超出 clamp → 完全不伸缩**，退回旧行为（`align=` 仍按 P4 的兼容门走）；本来就已经"合得上"
      （`gridsCompatible` 在**混音那个长度**上成立，比如 120.0 vs 120.4）也**不伸缩**——0.3% 的拉伸
      只有 artefact 没有收益。
    - **时长换算（最容易漏的一环）**：B 的**文件时间轴**按 r 倍前进，所以"重叠已经放过多少毫秒"
      不再是 `rampMs` 而是 `round(r × rampMs)`。两处都改了：`PlayerController.playAt` 的 handoff
      下限（`crossfadeIncomingStartMs + round(crossfadeRampMs × incomingPlaySpeed)`）与
      `AndroidAudioBackend.promoteIncoming` 的 `BEHIND` 判定；**不换算就会把下一首往前多跳/少跳
      几百毫秒**。A 的时间轴（时长、剩余、斜坡、拍格）**全部不动**，因为被伸缩的只有 B。
    - **晋升后平滑还原**（`PROMOTION_RESTORE_MS = 6s`，10Hz 步进）：见下面"决定"那段。
  - **② 改调（和声混音）**：
    - `audio/KeyAnalysis`（纯 Java，无依赖）：**手写 radix-2 FFT**（4096 点、hop 2048）→ 频点折到
      最近的半音（55Hz–2kHz）→ 12 维 chroma → 与 Krumhansl-Kessler 大小调模板的 24 个旋转做相关 →
      取最好；置信度 = 最好与次好的**相关差**。
    - **两个必须记住的实测坑（都在这轮被测出来）**：
      1. **chroma 的均值必须在归一化之后减**。之前写成 `mean = total/12`（原始对数和），减完剩下一个
         常数向量，于是**任何调都同样相关**→ margin≈0，但赢家照样是一个"看起来很正常"的调名。
         这是最危险的一类 bug（有答案、没证据），`KeyAnalysisTest` 现在把 margin 打印出来。
      2. **必须给高频降权**（400Hz 以下满权，2kHz 处 0.25）。理由：400Hz 以上大部分能量是**低音的
         谐波**，而谐波不是它的音级（三次谐波是五度、五次谐波是大三度）。不降权时 C 大三和弦
         （0.815）和 E 小调（0.813）只差 0.002 —— 因为 C 大三和弦的谐波真的拼出了一个 E 小和弦。
         降权后 margin 从 0.002 涨到 0.144。
    - **改调决策**：候选 n ∈ {-2..+2}，度量 = 两个 chroma 的**总变差距离**（`KeyProfile.distance`），
      要求 (a) 移动后距离 ≤ 0.55，(b) 相对提升 ≥0.05 **且** ≤ 原距离的 75%，(c) 除非提升很大，
      否则还要求**Camelot 相邻**（同号/相对大小调/五度，`KeyProfile.camelotCompatible`）。
      "Camelot 相邻"这条是给估错的调名兜底：**只有"整个调之间的关系成立"才算数**。
    - **持久化**：key 与拍格同住一个 profile/同一个 cache 文件（一次解码回答两个问题）。
      `BeatProfile.toBytes` 12 字节 → **27 字节（VERSION 2）**；**VERSION 1 仍然能被读**（只是没有 key），
      所以升级不会作废任何已缓存的拍格。`KeyProfile.toBytes` = tonic/mode/strength + 12 字节的
      归一化 chroma（每字节 0.004，远细于判据的 0.05）。
  - **③ 低频互换**：`android.media.audiofx.Equalizer` **按 `getAudioSessionId()`** 分别挂在两路播放器上
    （只用 Equalizer，不用 BassBoost：Equalizer 能指名**频段中心频率**，BassBoost 只能整体增益）。
    - 挂载时机 = incoming **prepare 完成时**（此时它还没出声、还没滚动，两首歌同时响的时候不做任何分配）；
    低频段 = 中心频率 < 200Hz 的段，切到设备自己的**最低增益**（通常 -15dB）。
    - 交换时刻 = **A 的拍点**上、且**重叠中点之后的第一拍**（`matchSwapMs`）；backend 还会把它夹到
      `rampMs - 200ms` 以内，因为实际斜坡可能比计划短（早到的边界 / 被拒的 mix），而**交换点晚于斜坡
      结束就永远不会发生**→ 晋升后的那首会带着切掉的低频一直播下去。
    - **释放**：晋升、放弃、release 都会 `releaseEqualizers()`（**两路都放**——放弃时 audible 那首的
      低频可能已经被切了，释放效果器就是把它还原）。**这是"会不会把一路搞无声"的那道保险。**
    - 设备不支持 / 挂不上 / 没有低频段 → 打日志并**只放弃低频互换**，变速与改调照常。
  - **决定：晋升后怎么还原？→ 速度与音高都**平滑**还原到 1.0**（6 秒、10Hz 步进，
    `PROMOTION_RESTORE_MS`；暂停/seek/切歌/释放时**立即**还原到位，不留半途的速度）。理由：
    1. 伸缩与移调是**为重叠买的**：只剩一首歌在放时，没有拍可以对齐、没有和声会打架，留下的只有
       artefact —— 一首听起来"快 5%、高一个半音"的熟歌就是错的；
    2. **不还原会让误差累积**：下一处边界的拍格是按**文件**测的，而被拉伸中的当前曲的时间轴不是文件的，
       拍格/时长/剩余全都偏一个 r（现在因为会还原，这个问题只在"晋升后 6 秒内又到边界"这种极端情况下
       才存在，而且 ≤8% × ≤6s）；
    3. 6 秒比任何一拍都慢，而且此时两首歌已经不再同时被听到，所以过渡本身听不出来。
  - **日志（每边界仍然一行，末尾追加一段）**：
    `... ; beat: A=120.0BPM/0.81 C major/0.90 B=126.3BPM/0.77 D major/0.88, align=on (mix: speed x0.9503 on B
    (126.3->120.0BPM, -5.0%); keys already sit together/distance..., overlap 10000ms = 20 beats of A at A's
    tempo, drift 0ms by construction; entry 0->59ms; bass swap at 5000ms)`
    - 不合适时是同一行的 `align=...; mix: off (<原因>)`，原因逐条写明：`no key measured for B` /
      `key not trustworthy (D major/0.11)` / `tempos too far apart: 120.0 vs 140.0BPM needs x0.857, the clamp
      is x1.08` / `keys clash (chroma distance 0.93; the best shift, -1, only reaches 0.62)` /
      `only 8000ms of the track is left, a mix wants 10000ms` / `合拍改调 is off (settings)`。
    - backend 侧新增：`MediaPlayer: incoming setDataSource + prepareAsync (startMuted=false, IncomingMix{...})`
      / **`MediaPlayer: incoming mix applied to the INCOMING player (speed x0.9503 pitch x0.9439 asked;
      platform reports speed x0.9503 pitch x0.9439; the audible track is untouched; it is parked, and nothing
      was started by the call)`**（**回读**，这是"平台真的吃下去了"的唯一证据）/ `bass swap armed...` /
      `bass swap done...` / `MediaPlayer: crossfade begin over 10000ms (EQUAL_POWER, IncomingMix{...})` /
      晋升那行现在写 `(the incoming is at 9500ms; the overlap already played 10000ms at IncomingMix{...})` /
      `MediaPlayer: easing the promoted track back to its own tempo and pitch over 6000ms (from x0.9503 / x0.9439)`
      → `MediaPlayer: the promoted track is back at its own tempo and pitch`。
    - backend 拒绝 mix 时控制器写：`transition: the backend did not apply the mix (asked ..., got
      IncomingMix{...}); this boundary runs un-stretched, at the 8000ms the plan asked for — its beats are not
      on A's grid`，**并把斜坡缩回 chooser 原本要的长度**（10 秒的两套不重合的网格 = 两首歌一起放）。
  - **协调（"遇到合适的"）**：判断在 `MixMatch`，四个条件缺一不可：两侧拍格都可信 + 速度在 clamp 内
    （或本来就合得上）+ 调性可被 ±2 半音调和 + 剩余时间够（≥ `TransitionPlan.OVERLAP_MATCHED_MS` = 10s）。
    kind/曲线/长度仍归 AI/本地规则；**只有长度是"下限"**：合适的对子至少混 10 秒（`plan.mixed()`：抬高长度
    时若 chooser 没指定曲线则改用等功率，AI 指定了就听 AI 的），比 10 秒更长就照 AI 的。日志里写
    `mix: overlap 4000->10000ms at A's tempo and key`。
    **决策提前量没变**（`TRANSITION_DECIDE_LEAD_MS` = 24.25s ≥ 10s+0.25s+9s），所以"长选项被静默降级"
    那个 bug 不会回来；arm 仍然按 `transitionArmLeadMs(plan)` 等窗口。
  - **设置**（两行，都 `dependsOn(智能过渡)`，默认**开**）：`合拍改调`（`harmonize`）与 `低频互换`
    （`bassSwap`）。顺手补了一个既有小洞：`BEAT_ALIGN_KEY` 之前**不在** `SettingsCore.apply` 的
    `pushTransition` 分支里，所以切「节拍对齐」要重启才生效（现在三行都在）。低频互换是独立开关，
    就是为了设备效果器不可靠时能单独关掉它而不丢变速/改调。
  - **纯 Java 测试（本轮唯一的真值证据，`mvn -pl player-core test`，97 个用例只有那个既有失败）**：
    - `KeyAnalysisTest`（7 个）：**大调 I-V-vi-IV 12/12 全中**（12 个调都测）；
      **小调 i-V-i-VI（含导音）12/12 全中**；**自然小调 i-VI-III-VII 12/12 被读成它的关系大调**
      （这是**真·歧义**，不是 bug：两者的音完全一样，功能差别只在强调；而且两者是**同一个 Camelot 号**，
      所以对本功能无害——这条写成断言留在测试里）。
      移调测试 6/6（上行 1/2/3/5/7/10 半音都报对）。负例：白噪声 margin 0.03（strength 0.09）、
      440Hz 单音 0.00、数字静音/不足 3 秒 → null（都被 `MIN_STRENGTH = 0.25` 挡住）。
      贝斯+底鼓+hi-hat 的 I-IV-V-I 仍报 C major（strength 0.57）。
      和弦进行 strength 实测 0.37–1.0（`MARGIN_FOR_FULL_CONFIDENCE = 0.35` 是这么定的）。
    - `MixMatchTest`（11 个）：比例算术（120/126 → x0.95238，且 `periodB/r == periodA`）、clamp 边界
      （x1.08 收、x1.0801 拒）、超出即不伸缩（120 vs 140、**87 vs 174 不做半/倍速重解释**）、
      已经合得上就不伸缩、调性各分支（同调不移、D→C 移 -2 半音、C vs F# 拒绝、缺 key/低置信拒绝）、
      Camelot 号（8A=A小调、8B=C大调、12A=C#小调）、磁盘格式与 V1 兼容。
      **最硬的一条**：`aStretchedIncomingTrackLandsOnTheOutgoingTracksBeats` —— 24 组速度对里
      **18 组被伸缩、6 组本来就不用**，**532 个拍点逐一核对，伸缩后的最大偏差 0.0000ms**（推导是
      精确的：B 从自己的拍点以 r 播放，第 k 拍落在 `k·periodA`）；对照组同两首不伸缩时 10 秒漂移
      **566ms**（B 的半拍只有 236ms）→ 旧代码只会直接跳过对齐。
  - **未验证 / 风险（重要，下一轮第一件事）**：
    1. **没装机**：`adb devices` 全程为空（手机物理掉线）。所以"平台真的吃下 speed/pitch"、
       "**在 parked（未 start）状态下 setPlaybackParams 是否会抛**"、"EQ 能不能挂上/CONFIG 会不会
       报错"、"听感"全部**没有真机证据**。代码里的态度是：任何一环失败都**降级并写日志**，
       不会静音、不会卡队列。
       ⚠️ 如果真机上 `setPlaybackParams` 在 parked 时抛异常，表现会是每个合适的边界都写
       `the backend did not apply the mix ...`（不是坏掉，而是这个功能不生效）——那种情况下应把
       调用挪到 `beginCrossfade` 里 `start()` 之后，并让控制器按那时的结果决定斜坡长度。
    2. **听感完全没人听过**：变速 ±8% 的 artefact、两首歌都改调后的"合唱感"、低频互换的接缝、
       6 秒还原是否会听出"速度在爬"，这些都只能靠耳朵。**最大的怀疑**：10 秒的重叠 + 双份鼓组即使
       拍点完全对齐，也可能比 4 秒更像"两首歌"。
    3. **错误的 key 估计**：置信度门 + 距离改善门 + Camelot 门是三层，但没有证明。可听后果是
       "一首被移了 1–2 个半音"（6 秒内还原）；**最坏方向**是移对了、但两首都听得出被改过调。
       自然小调被读成关系大调是**已知且无害**的一类（同 Camelot 号）。
    4. **`crossfadeIncomingSpeed` 的边界情况**：如果晋升后 6 秒内就到达下一处边界，当前曲还在还原
       过程中（速度 ≠1），而它的拍格/剩余都是按文件算的 → 那个边界会偏 ≤8%×剩余时间。极端罕见。
    5. **EQ 的副作用**：`Equalizer` 的 Q 值由设备决定，"< 200Hz 的段"在 5 段 EQ 上通常是第 0 段
       （带宽很宽），切到 -15dB 是**很重**的一刀；如果听着太狠，改 `BASS_SWAP_HZ` 或把增益改成
       min/2 即可（都在 `AndroidAudioBackend` 顶部）。
    6. **成本**：每轨多一次 4096 点 FFT × ~160 帧（在同一份已解码的 30s 窗口上，实测 JVM 里
       ~0.1s，真机未测）；磁盘缓存每个拍格从 12 涨到 27 字节。
  - **留给后续**：半速/倍速重解释（87 与 174 这种同一拍格的两首现在不参与，需要把"每两拍"当成 B 的
    网格——`BeatProfile` 已经能表达，只是没做）；分段（intro/outro）识别；把 BPM/调性喂给 AI 提示词
    （现在仍然只是让 AI 挑 kind/长度/曲线）。
- **2026-09-19 第五轮：拒绝必须出声、无 AI 答案不再退化成 250ms、置信度换成"窗口自洽"、下一首预缓存
  （全部装机验证；`efaa83b2` / Redmi K20 Pro）**
  - **背景（真机证据，这一轮的起点）**：18:29/18:30 那次边界日志里
    `transition: slot 2 -> 3: CROSSFADE 重叠=short 4000ms (AI); … beat: A=97.0BPM/0.74 B=64.0BPM/0.29,
    align=off (confidence 0.29 < 0.35)` —— **一行都没提混音**：边界在"任何混音判断之前"就被拒了
    （B 的拍格不可信），而 250ms 的 `SILENCE_TRIM (rule: no AI decision yet)` 又几乎听不见，
    于是"没生效"与"没装"在日志里长得一模一样。四条都按这个改。
  - **① 每个边界一行说明混音结果（Task 1）**：`PlayerController` 的
    `alignToBeatGrid` 现在**每一条 early return** 都带 `mix:` 片段（过去只有"真的判过混音"才写，
    网格缺失/置信度不足这些**上游**拒绝一条都不写）。措辞（真机原样）：
    - `; mix: off (no credible grid for A, confidence 0.00 < 0.35, prom 0.38)`（网格缺失/不可信；
      `confidence` 是新的门读的数，`prom` 是旧的突出度，见 ③）
    - `; mix: off (no credible grid for B: the probe found no beat in its audio)`（探针根本没测出拍）
    - `; mix: off (no beat profiler on this device)` / `mix: off (节拍对齐 is off: a mix is performed
      on the two grids)` / `mix: off (only 8000ms of the track is left, a mix wants 10000ms)`
    - 不重叠的 kind 也写：`; mix: off (CUT never has both tracks audible)`（SILENCE_TRIM /
      FADE_OUT_IN 同理）、`mix: off (too late: 1200ms left)`。
    - 应用时那一半本来就是完整的一句，`MixMatch` 把它补成**两个比例永远都在**：
      `mix: speed x0.9503 on B (126.3->120.0BPM, -5.0%); pitch x1.0595 on B (+1 semitone: …)`；
      不动速度/调时也写明 `speed x1.0000 on B (tempo already holds: 12ms of drift over 8000ms)`、
      `pitch x1.0000 on B (0 semitones: keys already sit together, …)`，再接 `bass swap at 5000ms`
      / `bass swap off (settings)`。**判据：一行必须能区分"没混音（含为什么）"与"这个包没有混音"**。
      ⚠️ **第九轮起这一段的调名后面多一个 `(triad 0.32)`**（`KeyProfile.label()` 现在把它印出来，
      因为门可以因为三和弦占比拒绝一个调，那它就必须在同一行里看得见）；并且**出现过、
      但不值得做的移调**会写成：`pitch x1.0000 on B (0 semitones: keys already sit together, …
      , chroma distance 0.18; -2 was allowed but only reaches 0.17, so nothing is applied)` ——
      这与"根本没有移调可用"（没有分号那半句）是两件事，**不要把它们当同一行**。
  - **② 没有 AI 答案时不再选最听不见的那种（Task 2）**：`AiTransitionChooser.plan` 的
    `rule: no AI decision yet` 分支原来用的是本地规则自己的 kind —— 而对"两首都够长"的对子，
    规则给的正是 `SILENCE_TRIM`（250ms 缝），于是一个晚到几秒的 AI 答案会把边界降级成"什么都没发生"。
    现在该分支直接给 `TransitionPlan.of(CROSSFADE, OVERLAP_MEDIUM_MS=8000)` 再过一遍 `clamp`
    （短歌降到 short 4s），**对子根本不能重叠时仍然 CUT**（能力门在那条分支之前就已返回）。
    真机：`transition: slot 30 -> 31: CROSSFADE 重叠=medium 8000ms, curve=EQUAL_POWER
    (rule: no AI decision yet); …; mix: off (no credible grid for A: the probe found no beat in its
    audio)`，同时后台把 AI 答案取回来（`AI 过渡决策 double take -> Can't Feel My Face:
    CROSSFADE[short 4000ms…]`），下一次同对就是 `(AI cached)`。其它 kind 仍然只能由 chooser 选中
    （强制「过渡方式」时连这条分支都不过）。
  - **③ 置信度换成"窗口自洽"（Task 3，本轮唯一的真改动）**：原判据是**自相关峰的突出度**，
    它惩罚的是"编曲密"（每个细分都有 onset，峰的相对高度就小），与"拍格可不可信"关系不大。
    现在 `BeatAnalysis.windowAgreement`：把 30s 窗口**三等分**，每段用同一套规则独立求一次
    （同一份包络、同一 lag 网格、同一 beat-rate 规则），然后
    - **tempo 半**：每段在**窗口自己那个 lag** 上的强度 ÷ 该段自己的最强强度，取**最差**的一段
      （`MIN_SEGMENT_SUPPORT = 0.35`，线性映射到 0）。**刻意不用"每段各自选出的周期"去比**：
      10 秒的段落经常分不清拍和它的 2 倍（这正是整个窗口 + beat-rate 规则存在的理由），
      真机上**每一首**密编曲的歌都因此拿 0 分（两个拍对 click 一样周期）。
    - **phase 半**：每段在该窗口周期下自己的最佳相位，与"窗口的相位外推到该段"的圆周距离，
      取**平均**（`PHASE_SPREAD_TOLERANCE = 0.4` 拍，超出即 0 分）。
    - **confidence = tempo × phase**；段太短/测不出周期/拍数不够的段**跳过**（不当作反对票），
      少于两个段能回答 → **返回 -1，沿用旧的突出度**（8 秒窗口就是这条，行为不变）。
    - **两个数都在日志里**：`BeatProfile{97.0BPM/0.31 (prom 0.74), …}`，
      边界那行 `A=97.0BPM/0.31 (prom 0.74) B=…`（校准期过后 `prom` 可以摘掉，见下）。
    - **磁盘格式 VERSION 3（14 字节网格 + 15 字节 key）**：同时存新的门读数和旧的突出度。
      **V1/V2 一律拒绝**（返回 null → 重新测一次）——旧的 12 字节里那个数字含义不同，
      读进新的字段就是"用错误理由信任/拒绝一个网格"，正是这一层要防的事。
      `BeatProfile.toString()` 因此变长了一点点（`(prom x.xx)`），只有日志受影响。
    - **纯 Java 真值验证（`BeatAnalysisAgreementTest`，9 个用例）**：合成 120 BPM click → 0.90、
      128 BPM 密编曲 → 0.92、click+白噪 → 0.86、8 秒窗口 → 1.00（走突出度）**都可信**；
      pad / 白噪 / 数字静音 → 没有网格；**三种错格全部被拒**：120→132→144 变速 0.00（旧判据 0.41，
      **会放行**）、120 与 170 两段无关速度 0.00（旧 0.62，**会放行**）、三段相位互相错开 0.33
      （旧 0.99，**会放行**）。半拍相位跳变（只错一段）则是 0.60 可信、但明显低于稳定曲的 0.90
      ——**故意如此**：accent 级别的相位分歧在真歌里是常态，判死它就是把真库拒掉一半。
  - **真机校准（Task 3 的数据部分，18 首用户的歌，`beat profile for …` 里的新/旧两个数）**：
    | 歌 | 测得 BPM | 新 confidence | 旧 prominence | 备注 |
    |---|---|---|---|---|
    | One Right Now | 97.0 | 0.31 | 0.74 | **真值 97（tunebat）→ 网格是对的，新判据误拒** |
    | double take | 72.7 | 0.25 | 0.38 | **真值 109 → 2:3 错格，旧判据放行、新判据拒掉（正确）** |
    | Better Now | 97.2 | 0.30 | 0.21 | **真值 145 → 2:3 错格，两个判据都拒** |
    | Obsessed With You | 71.6 | 0.66 | 0.41 | **真值 143 → 半速（可用），新判据放行** |
    | The Other Side Of Paradise | 64.0 | 0.75 | 0.29 | 旧拒新收 |
    | White Iverson | 66.1 | 0.43 | 0.21 | 旧拒新收 |
    | Portland | 135.8 | 0.51 | 0.27 | 旧拒新收 |
    | Life's A Mess | 143.2 | 0.47 | 0.32 | 旧拒新收 |
    | DAY1 | 66.9 | 0.35 | 0.23 | 旧拒新收 |
    | Edamame | 105.9 | 0.80 | 0.92 | 都收 |
    | HEARD OF US | 83.3 | 0.70 | 0.39 | 都收 |
    | Ice Cream Man | 144.0 | 0.60 | 0.60 | 都收 |
    | Moonlight | 127.8 | 0.41 | 0.38 | 都收 |
    | Lalala(赛马娘版) | 83.3 | 0.37 | 0.46 | 都收 |
    | OWA OWA | 83.8 | 0.44 | 0.40 | 都收 |
    | Violet | 119.9 | 0.27 | 0.51 | 新拒旧收 |
    | ON MY WAY | 96.6 | 0.01 | 0.40 | 新拒旧收 |
    | 08 | 170.0 | 0.26 | 0.37 | 新拒旧收 |
    **结论（重要，别只看通过率）**：18 首里**两个判据都是 12 首通过**，只是**换了一批**：
    新判据多收 5 首（旧判据 0.21-0.32 拒掉的），多拒 5 首（含 2 首**可证的错格**：
    `double take` 报 72.7 而真值 109、`Better Now` 报 97.2 而真值 145，都是 2:3 错位；
    以及 1 首**可证的对格被误拒**：`One Right Now` 真值 97 = 测得 97）。
    **所以这台库真正的瓶颈不是置信度门，而是估计器的周期选择**（密编曲上经常落在真拍的
    半速/2:3 上），门做得再准也只能"拒掉错的"或"放行对的"，不能把错的变对。
    下一步（比再调阈值值得做得多）：**在窗口周期与各段自己的周期之间加一条和声关系检查**
    （1、2、3 倍或 1/2、1/3 视为同一个网格；2:3、3:4 这种**不算**）——这一条同时能放行
    `One Right Now` 这类"段落只是选了自己的倍数"的正解，并拒掉 2:3。
    两个旋钮的实测敏感度：`MIN_SEGMENT_SUPPORT` 0.35（低到 0.25 就多收 1 首，但两首错格的
    余量掉到 0.02）、`PHASE_SPREAD_TOLERANCE` 0.4（0.35 → 3/9 通过、0.4 → 5/9、0.5 → 7/9）。
  - **④ 下一首的音频提前缓存（Task 4，用户明确要求）**：`preloadAdjacent()`（每首起播时）新增
    `precacheNextAudio(next)`。理由是边界需要的**是**incoming 的**源**：SILENCE_TRIM 的测量要在
    9 秒窗口内到达，重叠类要在 17 秒前就挂好停放播放器，而 kind/对齐在 24.25 秒前就决定了；
    这台库走 unblock，实测解析一首要 1-9 秒，所以没缓存过的下一首经常赶不上 → 边界硬切。
    - **复用现成的音频磁盘缓存**（`DiskCache.cacheAudio(url, neteaseId)`，与普通播放路径同一份、
      同一个 key），**没有第二个下载器**；解析用 `resolveProbeSource`（官方 → unblock，**试听一律不要**）。
      **绝不写回 `Track.streamUrl`**（会让下次普通播放走"缓存 url"快路，跳过元数据补全 —— 这个坑踩过一次）。
    - **边界**：只在 `transitionEnabled` 时；只做 NETEASE（音频缓存按 neteaseId 存，custom-api 没有 id 可存）；
      试听/BILI/LOCAL 不做；已经在本地 → 不做；**缓存已经到大小预算** → 不做（避免刚下完就被 LRU 淘汰的churn）；
      自己一条单线程 lane `qplayer-precache`（不与当前曲自己的缓存 / 歌词 / 解析 / 拍探针抢）；
      每次起播 `precacheGeneration` +1，排队中的旧任务直接返回（**已经在下完的不打断**，留下的文件
      仍然是那个 key 的有效缓存，用户接受它之后被淘汰）。
    - **刚下载的东西要验货**：下了但**明显不像这首歌**（< max(200KB, 时长×64kbps)）就删掉并 warn ——
      unblock 源返回 200 + 错误页是常事，而普通播放路径**优先用缓存文件**，一个坏文件会长期毁掉那首歌。
    - 日志：`transition: pre-caching the next track (Free throw), 177s of it to fetch before the boundary`
      → `transition: pre-cached the next track (Free throw): 6917KB on disk, the boundary will not have
      to resolve it`；边界那行旁边 `transition: incoming slot 31 (Can't Feel My Face) served from the
      audio cache, nothing to resolve`（网络现解析时同一行写 `(not cached: resolved inside the
      boundary's window)`）。
    - **真机证据**：`pre-cached … 6917KB / 41865KB / 27293KB / 5225KB` 多次成功；
      随后的边界 `incoming slot 31 (Can't Feel My Face) served from the audio cache` → arm 立刻完成
      （`incoming prepared parked at its offset`）→ `crossfade begin over 7990ms`。**"随后的普通播放
      也从缓存起播"**（`play netease (audio cache): Inside Outside`）说明它确实进了普通路径用的那份缓存。
  - **本轮没做的事 / 未验证**：
    1. **没有试听**：变速/改调/低频互换（第四轮）依旧没人听过；这一轮也没让新的 8000ms 兜底
       真正播出来听过（只证明它进了状态机并开始 ramp）。
    2. `MIN_SEGMENT_SUPPORT` / `PHASE_SPREAD_TOLERANCE` 是从**18 首**里选的，其中只有 4 首有真值
       （97 / 109 / 145 / 143）；换风格（古典、现场、说唱 freestyle）没有样本。
    3. 段长 `MIN_SEGMENT_MS = 6s` 意味着 12 秒以下的解码窗口只用两段、18 秒以下没有段
       （退回旧判据）——真机窗口是 30 秒，所以这条只在"只解出一小段"时才走到。
    4. 预缓存的**带宽**成本：每首起播多下一首整曲（实测 5-42MB）。在有"仅 Wi-Fi 下载"这类设置前
       （`SettingsCatalog` 里**目前没有**这类 key，已确认）它只在 `智能过渡` 开时发生。
    5. 预缓存与"下一首的早探"各自解析一次 URL（两条 lane，互不等待）：每次起播多一次
       `songUrlInfo`+unblock 往返；没有做共享 memo（两条路各自都要在另一种关闭时仍然能工作）。
    6. `precacheWorker` 没有单独测试；缓存到预算时的"不下"分支、bad-download 删除分支
       **在真机上都没被走到过**。
- **2026-09-19 第六轮：htdemucs 人声分离「可行性闸门」（只有测量；App 代码只加了依赖与 abiFilters）** ——
  目的是回答"手机上跑得动吗、吃多少内存、怎么配"，**结论是可行，而且找到了比 Folia 更省的配置**。
  参考实现在 `D:\qplayer-dev\folia-src\folia-major-main`（zip 在 `C:\Users\xiaoz\Downloads\`）；
  **权威文档是它的 `src/services/automix/MODELS.md` 与 `shared/modelManifest.json`，别只看任务描述**。
  - **磁盘规则（硬约束，见第三节）**：**C: 只剩 ~6.2GB（98% 满）**，大文件一律放 **`D:\qplayer-dev\...`**。
    本轮落盘：`D:\qplayer-dev\htdemucs\`（两个模型）、`D:\qplayer-dev\harness\`（harness 源码 + dex +
    拉下来的音频 + python 脚本）、`D:\qplayer-dev\ort\`（aar 与 classes.jar）、`D:\qplayer-dev\pylibs\`
    （`onnx` 1.23.0，**只用于改图**；**inference 永远不在 PC 上跑**）。
  - **模型（字节数与哈希已核实）**：**108,644,650 字节**，
    **sha256 `099b5be76c1f6922124d07f850250f39d1f33f254a0b8cc90f4ec0dfd0912329`（实测匹配）**。
    ⚠️ **"上游 URL"这个提法是错的，必须修正**：这个文件**不在** `itamiArika/htdemucs-int8-memory` 里 ——
    那个仓库只有 `htdemucs-dft-fp16-transparent{,-portable}.onnx`（129,977,189 / 129,981,572）与
    `htdemucs-dft-int8-fp16-{final,portable,chunked}.onnx`（98,461,394 / 98,474,789 / 98,511,195），
    它的 `SHA256SUMS` 里**没有** `099b5be...`。**108,644,650 这个字节流是 Folia 自己改出来的产物**，
    由 Folia 的发布通道分发：**`https://hf-mirror.com/HUAI4236/folia-models/resolve/main/htdemucs.onnx`**
    （`HUAI4236/folia-models` 里那个文件的 LFS oid 就是上面这个 sha256）或
    `https://github.com/AZURE-HUAI/folia-models/releases/download/weights-v1/htdemucs.onnx`。
    **huggingface.co 在这台机器上连不上**（curl 60s 超时），**hf-mirror.com 通**，本轮的模型是从后者拿的。
    来历（Folia `MODELS.md` §2）：Meta 原版 → ① 第三方单文件四轨 ONNX → ② fp16 权重（130.0MB）→
    ③ "transparent" 重导出（STFT 从展开的循环换成真 DFT 算子，节点 24917→1556，图优化峰值 4594MB→353MB）→
    ④ **Folia 把段长砍半**（343980→172032，130.0→108.6MB）。**所以"从上游下载并核对哈希"这件事，
    只有走 Folia 的通道才成立**；要自己复现 ③④，脚本 `build/htdemucs_halve_segment.py` 在 Folia 源码里。
  - **ORT**：`com.microsoft.onnxruntime:onnxruntime-android:1.30.0`（**Maven Central 通**，已解析/编译/装机）。
    **这是全仓库唯一一个越过"不能新增依赖"的依赖**（用户已同意），加在 `android-shell/app/build.gradle.kts`。
    - **APK 体积实测**：debug APK **64,902,780 → 94,053,487 字节（+29.1MB）**。aar 本体 53.0MB，
      内含**四个 ABI**（arm64-v8a 33.0 / armeabi-v7a 23.3 / x86 39.3 / x86_64 39.5 MB，**全带上就是 +135MB**），
      所以 `defaultConfig` 里加了 **`ndk { abiFilters += "arm64-v8a" }`**；要 32 位或模拟器就把
      `"armeabi-v7a"` 加回去（**这是本轮唯一改了打包行为的改动，可一行还原**）。
    - ⚠️ **gradle 缓存里原本躺着 `onnxruntime-android-1.23.2`**（更早的会话试过）。**1.23.2 < 1.25，
      加载这个模型会直接失败**（iSTFT 把 DFT 的 `inverse`+`onesided` 一起设了）。**不要图省事用缓存里那个。**
  - **怎么测的（可复现）**：`D:\qplayer-dev\harness\HtdemucsBench.java` —— **独立进程**的 harness
    （`app_process` + `d8` 出的 `classes.dex` + aar 里的 arm64 `.so`），所以它
    **`/proc/self/status` 的 `VmHWM` 就是这次推理自己的 OS 高水位**，不是进程内采样器、也没被 Compose 污染
    （Folia 的教训：进程内采样漏掉了 4GB 的瞬时峰值）。跑法**实测可用**：
    ```
    adb shell "cd /data/local/tmp/hb && LD_LIBRARY_PATH=/data/local/tmp/hb \
      CLASSPATH=/data/local/tmp/hb/classes.dex app_process /system/bin HtdemucsBench \
      /data/local/tmp/hb/htdemucs-quarter.onnx /data/local/tmp/hb/audio1.bin 30 120000 \
      --threads=4 --arena=off --mempool=off"
    ```
    输出全以 `HB|` 开头（`MEM` 行 = VmHWM/VmRSS/VmPeak，`RUN` 行 = 墙钟与四轨相加的误差）。
    `/data/local/tmp/hb/` 里**已经 staged 了** dex、两个 `.so`、两个模型、一段真实音乐（约 200MB），
    下一轮可以直接复用；重建 dex 的法子在 `harness/` 里（javac → `jar` → `d8`，
    **注意 d8 吃 jar 不吃目录**）。音频取自 App 的音频缓存
    （`adb exec-out run-as dev.t1m3.qplayer.debug cat files/cache/audio/18969210.cache`，**FLAC**，548.2s）。
    - ⚠️ **两个 adb 坑（已踩，别再浪费一轮）**：① `adb push <local> <dir>/`（**目标带斜杠**）
      **会把文件名的最后一个字符吃掉**（`audio1.bin` → `audio1.bi`）→ **总是给完整目标文件名**再 `md5sum` 核。
      ② 这台机器 push 大文件**有时只有 0.8MB/s**（108MB 用了 127s），有时 32MB/s，**不要拿一次测速估算**。
  - **实测数字（Redmi K20 Pro `efaa83b2`，Android 16，arm64，5.6GB RAM；CPU；VmHWM = OS 峰值）**：

    | 模型 | 段长 | 窗口 | 墙钟 | 倍速 | **VmHWM** | 四轨相加 vs mix |
    |---|---|---|---|---|---|---|
    | htdemucs.onnx，arena **on**（默认） | 3.901s | 8s | 9.57s | 1.20x | **2146MB** | −33.3dB |
    | htdemucs.onnx，arena **off** | 3.901s | 8s | 10.23s | 1.28x | **1225MB** | −33.3dB |
    | htdemucs.onnx，arena off | 3.901s | 15s | 18.11s | 1.21x | 1266MB | −33.0dB |
    | htdemucs.onnx，arena off | 3.901s | **30s** | **36.83s** | 1.23x | **1329MB** | −32.7dB |
    | **quarter（本轮生成）** | **1.950s** | 15s | 15.65s | 1.04x | **800MB** | −32.7dB |
    | **quarter** | **1.950s** | **30s** | **32.20s** | **1.07x** | **860MB** | −32.2dB |
    | quarter，同进程第二次 | 1.950s | 30s | 35.16s | 1.17x | 880MB | −32.6dB |
    | htdemucs.onnx + **NNAPI** | 3.901s | 15s | **153.28s** | **10.22x** | 1308MB | **+15.9dB（垃圾）** |

    - **I/O 契约（设备上读出来的，不是抄的）**：输入名 **`mix`**、`[1,2,172032]` float32（quarter `[1,2,86016]`）；
      输出名 `stems`、`[1,4,2,172032]`、**取 index 0**；**段长向模型查询**（`get_inputs()[0].shape[2]`）。
      **会话创建 0.62–0.78s**，那一步的 VmHWM **430–442MB**（= Folia 的"图优化 353MB/0.65s"，**对得上** →
      **这个 export 的图优化不是问题**）。MediaCodec 解码 FLAC：15s→1.34s，30s→2.70s。
      分块几何与参考实现一致：stride = 段长 − 段长/4、三角窗、按权重和归一（`HtdemucsBench.buildWindow`）。
    - **四轨相加 ≈ mix**：**模型原生的四轨**相加与 mix 的 RMS 误差 **−32~−33dB**（≈2% RMS）；
      把 `other` 按 `mix − drums − bass − vocals` 重算后是 **−149dB（纯 float 舍入）** —— 这正是
      "四轨在 unity 下精确还原 master"那条要求。stem 顺序 `drums/bass/other/vocals` 与 Folia 一致，
      能量也合理（bass 最响、other 最轻）。
    - **四个开关的实测结论**：
      1. **`setCPUArenaAllocator(false)` 是 Java 侧唯一真正的大杠杆**：同一窗口、输出**逐位相同**，
         峰值 **2146MB → 1225MB**，且跑完 RSS 掉回 376MB（开着 arena 就一直占着）。
         这就是 Folia 那个**只能在 Python 设**的 `enable_mem_reuse=False`（2543→790MB）的**Java 可及替代**
         （mem_reuse 在 C API 里没有，但 arena 这个开关 C/Java 都有）。
      2. **线程数**：1→46.14s / 2→26.82s / **4→18.11s** / 8→23.10s（**8 反而比 4 慢**：8 核大小核被小核拖）。
         **内存与线程数无关**（1/2/4/8 都是 1266–1268MB）→ 峰值是"单段激活值"的一次性分配，不是每线程一份。
      3. **`setMemoryPatternOptimization` 与 `memory.enable_memory_arena_shrinkage=cpu:0` 对峰值毫无影响**
         （1267–1306MB，全在噪声里）。不用折腾。
      4. **NNAPI 直接判死**：会话创建 12.67s、**每段 25.5s（CPU 2.99s，慢 8.5 倍）**，而且**输出是垃圾**
         —— 四轨相加比 mix **高 15.9dB**、stem 电平冲到 **+9.1dBFS（超过满刻度）**。
         **它会"加载成功"然后自信地给出错误音频**，正是任务警告的那一类；我们的相加自检把它抓出来了。
         设备报的可用 EP 是 `[CPU, NNAPI, XNNPACK, WEBGPU]`，**CPU 是唯一验证可用的**
         （XNNPACK/WEBGPU 本轮没测）。**按墙钟与相加自检判断，不要按"加载成功"判断。**
  - **段长这个杠杆（Phase 2b）**：**我们拿到的模型本来就是"砍半"那一版**（段长 **172032 = 3.901s**，
    未改的上游 transparent 版是 343980 = 7.8s）—— 已用 `get_inputs()[0].shape[2]` 在设备上确认。
    既然"更短的段"是唯一还能压峰值的杠杆，本轮**又砍一刀**：用 Folia 脚本的等价物
    （`D:\qplayer-dev\harness\quarter.py`，**只改图、不跑模型**）生成 **`htdemucs-quarter.onnx`**：
    172032 → **86016（1.951s）**，`97,978,156` 字节，
    sha256 `e4481383bee7c9f2e0998b514f11e6ebb73bf6b8994c7591b963d5c875ca41f0`。
    **每一栏都更好**：30s 窗口 峰值 **1329 → 860MB（−35%）**、墙钟 **36.83 → 32.20s（−13%）**、
    相加自检 **仍是 −32.2dB**、stem 电平与砍半版相差 ≤0.5dB。同进程第二次：峰值 880MB（**不涨、无泄漏**），
    输出逐位相同，第二次慢 8%（**热**）。
    - ⚠️ **改图的坑（真踩到了，必须记住）**：`strict_mode` 的 shape inference **会通过，但图是错的** ——
      我漏了两个"帧数的倍数"常量 `344 = 2×(frames+4)` 与 `1376 = 8×(frames+4)`
      （`/dft_stft/flat_*` 与 `/dft_istft/iv_*` 的 reshape 目标），设备上直接报
      `Reshape ... Input shape:{2,88,4096}, requested shape:{344,4096,1}`。
      **改图必须把"所有长度常量"清点一遍**（清点脚本 `harness/inv.py`；完整清单 = 时间链
      172032/43008/10752/2688/672 及其 +2 的 trim、STFT 的 173568/175104/177152/179200、
      帧数族 168/170/172/173/174/175 及其倍数 344/1376/1344、3 张位置表 + iSTFT 归一化表的**拼接**
      （拼接：保留头 86 个 hop、尾 1 个 hop，接点落在恒定段内）），**并且一定要在真机上用"四轨相加"验货**。
    - ⚠️ **听感没人验过**：Folia 只做过 7.8s→3.9s 的盲听（"听不出区别"）；**3.9s→1.95s 没有任何盲听证据**。
      若采纳 quarter，这是第一件要听的事。
  - **结论：对手机「可行」，但不免费** ——
    - 推荐配置：**CPU + `setCPUArenaAllocator(false)` + `setIntraOpNumThreads(4)` + quarter 段长**，
      30s 窗口 = **860MB OS 峰值、32.2s 墙钟（1.07x 实时）**。860MB/1.3GB 在 5.6GB（可用 ~2.3GB）的机器上
      **能活**，但**建议放独立进程**（harness 本身就是证明：`app_process` + dex + `.so` 完全可行）——
      被系统杀掉只是"这次没有 pre-render"，不连累播放进程。
    - **必须提前起跑**：32s 算力 vs `TRANSITION_DECIDE_LEAD_MS` = 24.25s → **"决策那一刻"才动手一定来不及**。
      要挂在**下一首的预缓存/preload 那条 lane** 上（起播时就有几分钟余量，见第五轮 ④），或改用 15s 窗口（15.65s）。
    - **三个开关都要显式设**，**不要用默认**（默认 arena=on 是 2146MB）。
  - **本轮明确没做的事**：
    1. **Phase 3（预渲染混音文件）一行代码都没写。** 理由：它要动的正是
       `PlayerController`/`AndroidAudioBackend` 那条**被 P0 咬过一次的状态机**，而 §5/§7 里那条
       "**没有任何一次过渡真的播给人听过**"至今没做；在没被听过的过渡系统上再叠一层没人听过的预渲染混合，
       是本仓库明确反对的推进方式（第三轮/第四轮的教训都是"先听，再叠"）。
    2. **没有任何听感**：四轨本身、quarter 段的音质、30s 窗口的接缝，谁也没听过。本轮唯一的客观质量证据
       就是"四轨相加 = mix 到 −32dB"（**与 Folia 同一判据**：他们 7.8s 版 26.5dB、3.9s 版 26.9dB，
       **口径一致、我们这个数更好**）。
    3. 只在**一台设备**上测过（Redmi K20 Pro，骁龙 855）；三星那台没碰。**XNNPACK / WEBGPU 未测**。
       音频只有一段真实音乐（18969210.cache，FLAC），没有跨风格/跨码率的样本量。
  - **下一轮的入口（按顺序）**：
    1. 决定段长（quarter 还是砍半版）→ 决定后就"先听"（这是唯一能判 quarter 的依据）。
    2. **Phase 3 的最小可测切片**：把 harness 里已经跑通的三件事（MediaCodec 解码 → ORT 分离 → 写文件）
       搬进 Android 侧，**只做"能生成一个预渲染文件"**，**先不接播放路径**；用 `DiskCache` 的
       `TRANSITION` 式子目录存，跑在 preload 那条 lane 上。**验收标准是日志**（文件多大、耗时、
       四轨相加误差），**播放路径一个字都不改**。
    3. 等"听感"那一轮做完（§5 里那 6 条），再把 `PlayerController` 接上：incoming 播预渲染文件、
       缺了/晚了就回落到今天的交叉淡化。

- **2026-09-19 第七轮：stem 顺序定论 + Folia 手势移植 + 第一个预渲染混音（纯日志验收）** ——
  只加了两个**纯 Java** 类（`audio/StemGesture`、`audio/StemBlendRenderer`）与 `DiskCache` 的 `BLEND` 子目录，
  **播放路径一个字都没改**。设备：**Redmi K20 Pro `efaa83b2`**（8 Elite 全程未插上，`adb devices` 只有这台；
  下面所有耗时都是这台 855 的）。
  - **Phase A：stem 顺序 = 确认（drums/bass/other/vocals），rows 2/3 不再只靠 Folia 的常数**。
    做法：给 `HtdemucsBench` 加了 `--metrics`（整窗每行 RMS/峰值/ZCR/<100Hz/100–4k/>4k/静音帧占比）、
    `--series=dir`（每窗 25ms 逐帧包络 CSV）与 `--scan=from,to,hop`（一次会话扫多窗）；
    两首带"无人声段落"的歌各扫多窗（坏女孩 5 窗共 110s、In My Head 2 窗共 50s，全部按 25ms 帧统计）。
    - **判据一（决定性）**：行 2（声明 `other`）在**两首歌的每一窗里都有 0 个**低于 -50dBFS 的 25ms 帧
      （4400 + 2028 帧，静音占比 0.000），行 3（声明 `vocals`）时有时无：
      坏女孩无歌词段（78.4–111.2s，LRC 无任何行）**中位 -43.5dBFS、44.1% 的帧 <-50dBFS**，
      而同一段行 2 的中位是 **-24.4dBFS、0% 静音**；出了这段（111.2–150s）行 3 变成 **-20.2dBFS、1.0% 静音**，
      行 2 只动了 **0.3dB**（-24.4 → -26.0）。In My Head 的 7.2–24.4s 前奏同理：
      行 3 中位 **-69.8dBFS（数字静音）、72.1% 的帧 <-50dBFS**，行 2 中位 **-23.0dBFS、0% 静音**；
      人声进来后（24.9–54.4s）行 3 变 **-19.9dBFS、26.3% 静音**，行 2 只动了 0.5dB。
      **若两行标签互换，就意味着"残差 stem（=所有非鼓非贝斯非人声的东西）在人声段几乎全在、在人声消失的
      33 秒里归零，而人声 stem 一直在响"** —— htdemucs 里没有这种机制；四轨相加 = mix（-33dB）也把
      {2,3} 钉死为 {other, vocals}。**结论：声明顺序成立，不要改。**
    - **判据二（任务要求的逐行数字）**：ZCR 与频带。行 3（vocals）在每一窗都是 <100Hz 能量占比最低且
      ZCR 最高的行（<100Hz 0.15–3.1%、ZCR 2.2–4.4k/s），行 2（other）带更多低频
      （<100Hz 1.2–11.5%、ZCR 1.0–1.3k/s）。⚠️ 频带是用**两级一阶低通**劈的（故意粗糙），
      所以 `>4k` 那一栏被高估（vocals 12–20%、other 3.4–5%），只能当趋势看。
    - ⚠️ **必须记下的反例（它会误导下一轮）**：**LRC 的"没有歌词行"不等于"没有唱"**。
      坏女孩那 33 秒里行 3 的中位是 -43.5dBFS 但 **p90 = -17.7dBFS**、峰值 -0.4dBFS —— 里面确实有
      sustained 内容（run-length 显示"响"的部分主要在 ≥0.2s 的连续段里，不是一帧的毛刺），即那段要么有
      人声 ad-lib、要么 htdemucs 把某个持续音色塞进了 vocals 轨；In My Head 的前奏同理。
      所以**逐行数字比"某一段该是静音"的假设可靠**；下一轮若再验证，请用"行间对比"（行 2 是否恒定、
      行 3 是否随歌词线起伏）而不是"某段该静音"。歌词线起始处的对齐检验（42/60 条线，行 3 比行 2 高
      2–5.4dB，符号对但幅度小）单独不足以定论，只作旁证。
  - **Phase B：Folia 的 `stemGesture.ts` 已移植为纯 Java（`player-core/.../audio/StemGesture.java`）+ 22 个纯 JVM 测试全绿**。
    移植的是**算术**：常量（含每个常量"为什么是这个值"的原文理由）、`envelopeOf`、`median`、
    `findSustain`、`planVocalExit`、`lastVocalMoment`、`singsInWindow`、`planStemHandover`、
    `rise`/`fall`/`curveOf`、`outgoingCurves`/`incomingCurves`、`OTHER_SWEEP_*`、`SWAP_EDGE_SEC`；
    另外 `StemBlendRenderer`（`crossfadeGraph.ts` 的那一半）：`softLimit`（knee 0.95 / range 4）、
    每 stem 峰值除数（`peakOf`，**存储**用，读写相消所以不改电平）、outgoing `other` 的 25→2200Hz 高通扫频
    （块式 RBJ 重算，64 样本一块 —— 离线等价物，写在注释里）。
    六条"必须在移植后仍然成立"的规则都有断言（`StemGestureTest` 18 个用例，数字全部打印）：
    - **鼓 6ms 切换、不淡入淡出**（`SWAP_EDGE_SEC=0.006`）：断言 `rise(swap, swap+6ms, swap)=0`、末端 =1；
      并且发现 **200 点/秒的采样曲线 → 6ms 边沿只有 1 个采样宽**，所以"实际播放的边沿"被量化到 5ms
      （Folia 的 `setValueCurveAtTime` 同样线性插值，这条值得记住）。
    - **bass 晚一小节**（`bassAt - swap == bar`）、**incoming `other` 比自己的鼓早 1.2s**
      （`curveAt(other, swap)=1`、`curveAt(drums, swap)<0.2`、`+20ms=1`）。
    - **鼓切换落在真实小节线上**：用 `downbeatOffset + beatsPerBar` 造小节线（`barLines`），
      断言 swap ∈ 小节线集合、且**不是**把 beat 相位当 downbeat 用（同一条节拍网格按 beat 相位造线会落在
      0.1/2.1/4.1…，差整整一拍）；没有 downbeat offset → 不加小节线、swap 落回 0.42 的分数位（"说实话而不是猜线"）。
    - **MAX_TAIL_BARS=2**：30s 窗口、120BPM（bar 2s）时分数位 12.6s 会让切换后留 3 小节 → 目标改成
      `30 - 3*2 = 24s`（**手势整体后移，而不是缩短重叠**），bass 26s、incoming voice 28s。
    - **人声退场是自由搜索（不吸附节拍）**：两个 rest（2.0s 处 -60dB、5.5s 处 -30.5dB）→ 取**后**那个
      （5.60s，且切割时长按深度分级 = 1.18s，而 -60dB 的孤立 rest 只给 0.5s）；rest 在 4.35s（不在任何
      0.4s 拍格线上）时 `exit.from` 精确 = 4.35s。
    - **recede 从 swap 起**（不是窗口起点）：无 rest 时 `exit.from == swap`；窗口太短则 `from = min(swap,
      to - 1s)`（**末端是钉死的**，起点往前挪而不是缩短淡出）。
    - **两条人声不叠 + 不留洞**：`vocalGap = vocalIn - exit.to = -0.457s`（目标 ≈ -0.5s），且
      **两个人声推子之和最大 = 1.000**（线性交叉淡化是 1.0，两边都 unity 是 2.0）。
    - **8ms 交接的前提 = 所有 outgoing 曲线从 unity 开始**：断言四条 outgoing 曲线在 t=0 都是 1.0000、
      四条 incoming 曲线在窗口末端（-0.4s 处）都是 1.0000、且 outgoing 四条在窗口末端都到 0。
    - **release 分支**（最后一个 hold 跨过 deadline 且能在窗内放开）：`exit.from = held.to = 12.0s`，
      incoming 的入口后移到 `exit.from - 1 bar`（`vocalIn = 9.119s`，> dueAt）；已知调性冲突
      （`keysClash=true`）时**不 ride**，回落到 rest/recede。
    - **`sings=false` 时 deadline 整体溶解**：`vocalIn = window`、exit 到窗口末端（Folia 那条"为一个
      根本不来的人声压掉自己的歌手"的教训）。
    - **小节线的相位问题（任务点名要回答的那条）**：我们的 `BeatProfile` 暴露的是**节拍网格的相位**
      （`firstBeatMs`，原文注释："every beat is `firstBeatMs + k * periodMs`"），**不是 downbeat offset**。
      所以移植后的 `barLines(bpm, downbeatOffsetSec, beatsPerBar, windowStart, window)` **把 downbeat offset
      当独立入参**，null 就没有小节线。因为本平台没有结构分析，本轮加了一个**明确标注为"扩展、非移植"**的
      估计器 `StemGesture.downbeatOffsetSec(...)`：在 beat 网格的 `beatsPerBar` 个候选相位里，选**低频最强**
      的那个（`--phaseA/--phaseB` 传的是**节拍相位**，从不直接当小节线用）。单测用"kick 只在第 3 拍上"的
      合成包络验证它找到 1.13s（bar 2.0s）。
  - **Phase C：跑出第一个预渲染混音（只写日志 + 落一个文件，播放路径没碰）**。
    对子：**A = Obsessed With You（n1876325947）→ B = OWA OWA（n2710036557）**（两首都在当前队列里、音频都在
    设备上；A 的尾段一直唱到 106.7s/108.7s，这是验收要的"混音前有人声"；两首的拍格都是**本机测过且过 0.35 门**的：
    A 71.60BPM/0.661、B 83.80BPM/0.444 —— B 那条是**本轮从设备 `beat/` 缓存里解出来核过的**，
    `n2710036557 → 1385817802.bpm` 这套 `abs(trackKey.hashCode())` 命名也验证过了）。
    窗口 15s（= 我们的 `OVERLAP_LONG`）。工具：`D:\qplayer-dev\harness\BlendBench.java`（`app_process` + dex，
    quarter 模型 + arena off + 4 线程），输出全以 `BL|` 开头，日志 `D:\qplayer-dev\harness\blend2.log`，
    渲染文件 `D:\qplayer-dev\harness\render\blend_obsessed_owa.wav`（设备上 `/data/local/tmp/hb/render/`）。
    - **顺手抓到一个真 bug（否则所有时间错位 8.8%）**：这两首都是 **48kHz**，而模型与时间轴都是 44.1kHz。
      第一次跑就是拿 48k 样本当 44.1k 喂模型（同一段音乐变成慢 8.8%、低一个纯五度），并且 plan 的"秒"落在
      错误的位置。已修：`BlendBench.toRate()` 线性重采样到 44.1kHz 并打印。**库里有两种采样率，别假设。**
    - **计划（15s 窗）**：`swap=7.746s`（落在小节线上，A bar=3.352s）、`bassAt=11.098s`、
      `vocalIn=10.610s`、`dueAt=10.610s`、**`gap=-0.500s`**；退场分支 **RECEDE 7.75–11.11s**
      （"最安静的半秒" = -1dB → 全程都在唱，正确走 recede），`held 8.80–10.25s`（1dB over the mix）。
    - **人声抑制（逐 25ms 实测，不是假设）**：`voice` 列 = **只渲染 vocal stem 的那一路**
      （另做一次 render 得到），所以数字就是人声本身：
      **混音前（0–7.75s）平均 -17.2dBFS；混音中 -21.8dBFS（outgoing -24.0 / incoming -25.9）；窗口末端 -24.4dBFS**。
      逐帧更清楚：0–10.4s 只有 outgoing（-12…-40dBFS），10.6–11.0s 两个推子交叉（10.795s 处
      outgoing -53.7 / incoming -29.4），11.2s 之后只剩 incoming（-15.9…-51dBFS）。
      **不叠**：整窗**最大只比"更响的那一路"高 1.76dB**（10.62s 处），而**两路都 unity 会高 3.01dB**；
      渲染比"两路相加"低 **5.05dB**。CSV：`D:\qplayer-dev\harness\render\voice2.csv`。
    - **接缝无台阶（all-unity 不变式）**：渲染第 1 个采样 vs outgoing 的四轨之和（=master）
      **-52.0dB**（差值**全部**来自 outgoing `other` 的 25Hz 高通 —— 这是 Folia 故意的；第一次跑把 sweep 关掉时
      是 -240dB，即逐位相等）；最后一个采样 vs incoming 的 master **-152.7dB**。
      各交接点 ±5ms 内的**最大 |x[n]-x[n-1]|**：7.75s→0.0525、11.10s→0.0693、10.61s→0.2610、11.11s→0.3806，
      而**源音频自己的 p99.9 台阶是 0.5443、最大 1.0675** —— **渲染在每个交接点的台阶都比音乐本身的
      99.9 百分位台阶还小**，接缝没有新产生台阶。
    - **削波/峰值**：总线前峰值 **+2.9dBFS**（3407 个采样越过满刻度 = 0.26%）；限幅器（knee 0.95/range 4）
      碰到 14685 / 1,323,000 个采样（1.1%），最多压 **-2.93dB**，**渲染峰值 0.0dBFS**（恰好满刻度，
      因为 `softLimit` 渐近到 1.0 而不是停在前一点）。写文件用 **每文件峰值除数 max(1, peak) = 1.0**
      （16-bit，2,646,044 字节），**没有回绕/硬削**。
      **要说清楚**：Folia 的"每 stem 峰值除数"是**存储**用的（进去除、出来乘，相消，不改电平），
      真正防止两路相加越界的是**总线上的 `softLimit`**；本轮**两个都实现了**，渲染本身走 float。
    - **成本（K20 Pro，quarter + arena off + 4 线程）**：解码 3.23s + 分离 15.31s(A) + 16.06s(B) + 混音 0.33s
      = **整跑 36.14s**，即 **2.41× 实时**（15s 窗口）；VmHWM **872MB**、跑完 RSS 415MB。
    - **落盘方式**：`DiskCache` 新增 `BLEND` 子目录（`files/cache/blend/`，键 = `pairKey`，
      文件名 `abs(key.hashCode()) + ".blend"`，计数上限 20，**并且计入 `totalSize()`** —— 它是这里唯一
      以 MB 计的派生文件）。本条就是用 `run-as … cat >` 写进去的，md5 与 D: 上的副本逐位一致：
      `files/cache/blend/788630542.blend`（2,646,044 字节，= `abs("n1876325947>n2710036557".hashCode())`）。
      **播放路径仍然没有任何东西读它。**
    - ⚠️ **第六轮记的 quarter 模型 sha256 是错的**：设备上和 `D:\qplayer-dev\htdemucs\` 上的
      `htdemucs-quarter.onnx`（97,978,156 字节，两边逐位相同）实际是
      **`427b9588287d85d78f212d9f6f4acbc42b626e28ef9d2d948fed78311f4aec20`**，
      不是原文写的 `e4481383…5ca41f0`（大小对得上、哈希对不上 → 21:23 那次重新生成之后没更新文档）。
      **交付路径必须用 427b9588…，否则 App 会拒绝自己刚下载的模型。**（`htdemucs.onnx` 的
      `099b5be7…d0912329` 已在设备上 `sha256sum` 复核一致。）
  - **交付路径（模型 98–108MB，不能进 APK）—— 决定**：
    1. **首次使用时下载 + sha256 校验**，不打包、不做 APK 资源、不做 obb：APK 已经 94–98MB，再加 98MB 是两倍；
       而且这个功能是"锦上添花"，缺了它必须完全等于今天的行为。
    2. **地址**只能走 Folia 的通道（`https://hf-mirror.com/HUAI4236/folia-models/resolve/main/htdemucs.onnx`，
       302 → CDN；**本轮在设备上实测可达**：`curl -sI` 返回 302 + `Accept-Ranges: bytes`；github release 那条也通）。
       落到 app 私有目录（`files/models/`），**sha256 用 427b9588…（quarter）或 099b5be7…（砍半版）**，
       校验失败就删除并**功能保持关闭**。
    3. **inert 门**：`智能过渡` 开 **且** 模型存在 **且** 校验过 → 才允许进 stem 路径；否则**一行分支都不进**，
       完全走今天的交叉淡化/硬切。开关默认**关**（下载 100MB 不能默认发生）。
    4. **不让用户等**：下载走 preload 那条 lane、带进度、可中断；只在 Wi-Fi 下默认允许（`SettingsCatalog`
       里目前**没有**这类 key，要加）。
    5. **本轮没有为交付写任何代码**（不往仓库塞没人调用的死代码，§5 第 2 条正是在清这个）；只**验证了两个前提**：
       设备能到达镜像站（302 可达）与设备侧 sha256 校验可用（`toybox sha256sum` 与文档值一致）。
       ⚠️ **实测下行只有 ~0.24MB/s（108MB ≈ 7.5 分钟）**，一次下载要按分钟计，别设计成"点一下立刻开唱"。
  - **本轮没做的事 / 没人听过**：
    1. **混音文件没有任何人听过**（照旧）。本轮的"验收"全部是**客观数字**：文件时长/交接时刻、人声抑制、
       接缝台阶、峰值与限幅量、墙钟。四轨本身、扫频、recede 的手感、quarter 段长的音质 —— **全部没听过**。
    2. **没有接播放路径**（明确要求）。没有任何代码读 `files/cache/blend/`；`PlayerController`/`AndroidAudioBackend`
       一行没改；所有 15s 重叠、AI 计划、拍格对齐的老路径照旧。
    3. 只跑了**一个对子、一次**（15s 窗）。REST 分支（真的找到 rest）在真实素材上**没跑到** ——
       这个对子全程在唱，走的是 RECEDE；release 分支只在单测里出现。
    4. 拍格用的是**本机历史测量值**（A 71.60/0.661、B 83.80/0.444 都来自设备 beat 缓存），不是本轮现算的；
       `downbeatOffsetSec` 这个**扩展**（低频最强相位）**是本轮新写的、只在合成信号上单测过**，
       真实音乐上的小节线正确性**没有验证**（要验证得有人听"鼓是不是在小节上换的手"）。
    5. 两首网格在 15s 里滑了两秒多（71.6 vs 83.8BPM 本来就不该对拍）—— 本轮手势**不要求对拍**，
       但"对不对得上"仍然只有耳朵能判。
    6. 8 Elite **没插上**（`adb devices` 全程只有 K20 Pro）→ 目标机器的真实耗时/内存**仍然没有数据**。
    7. APK 现在 **98,294,148 字节**（ORT 的 arm64 `.so` 33.0MB 是主要新增）；与第六轮记的 94,053,487 差
      +4.2MB，**不是本轮代码造成的**（3 个新类 ≈ 50KB dex），疑似一次全量重新 dex vs 增量 dex，未细查。
  - **下一轮建议（按顺序）**：
    1. **听**。把 `D:\qplayer-dev\harness\render\blend_obsessed_owa.wav`（以及 §5 里那 6 条）放给人听。
       这是唯一能判 quarter 段长、扫频、recede 手感的依据；在那之前不要再叠功能。
    2. **接线的最小切片**（等 1 之后）：`PlayerController` 在边界前把渲染文件交给 incoming 播放
       （缺了/晚了/校验失败就回落到今天的交叉淡化），`DiskCache.BLEND` 的键是现成的。必须先定**谁触发渲染**
       （preload lane）与**渲染失败时的超时**：本文档的 24.25s 决策提前量**不够 36s 的渲染**，所以只能挂在
       起播时的 preload 上。
    3. 交付：把"下载 + sha256 + inert 门"写进 `SettingsCore` + 一个下载器（用现有 `HttpURLConnection` 路径，
       不新增依赖），并用 **427b9588…** 这个真实哈希。

- **2026-09-19 第八轮：周期选择的和声关系检查（2:3 错格）—— 装机验证，`mix:` 第一次真的写成了"做了什么"**
  上一轮遗留的那件事（见本节末尾的"第五轮之后的第一件事"）：测得 72.7 的歌真值 109、测得 97.2 的真值 145，
  门只能在"拒掉错的/放行对的"之间选。本轮把它做了，并且**先造了能反复跑的测量工具**。
  - **工具（全在 `D:\qplayer-dev\harness\`，PC 与设备两侧）**：
    · `BeatDump.java` + `build_beat.sh`：**设备侧** harness（`app_process` + dex，dex 里编的是仓库那份
      `BeatAnalysis`/`BeatProfile`/`KeyProfile`/`KeyAnalysis`）。逐首把**30s 窗口按 `AndroidBeatProfiler`
      同一套循环**（同一个 window/整数抽取/按帧声道平均）解出来 → 打印 profile（含 key）→ **同时把
      这窗口写成 float32 的 `.f32`**（24 字节/帧的 int16 会丢精度，而门是一个 0.35 的阈值，"差不多"不是
      校准里该有的东西）。跑法同 `BlendBench`（`CLASSPATH=/data/local/tmp/bd/classes.dex app_process
      /system/bin BeatDump /data/local/tmp/bd/out <id>=<path>...`）。
      **设备上已 staged（可直接复用）**：`/data/local/tmp/bd/classes.dex`（只有 25KB）、
      `bd/audio/*.cache`（36 首，从 app 的 `files/cache/audio/` 用 `run-as … cat >` 抄出来的）、
      `bd/out/*.f32`。
    · `BeatPc.java`（表 + gate）/`Candidates.java`（每个候选 lag 当选中时的 confidence）/`Grids.java`
      （每个候选网格的**脉冲串收集到的 onset 能量**：总/每拍/拍数）/`Harmonics.java`（raw r(L)、r(2L)、r(3L)
      与两种加权）/`Diag.java`（逐段 support/相位）/`Phases.java`（逐段相位，给定周期）/`Synth.java`
      （合成信号前后对照）/`Ratio.java`/`Keys.java`：**PC 侧**，直接编仓库源码跑那些 `.f32`。
      `Copy.java` 是**老选择的副本**（只用于打印 before/after，不参与任何决定）。
      **PC 与设备逐位一致**（见下表：设备 `beat profile for …` 与 PC 表的数字最后一位相同），
      所以"改一行、2 秒看 18 首"是成立的。
  - **实测到的机制（这是本轮全部改动的依据，别再重新猜）**：错的那一格不是"置信度没测准"，是
    **自相关选错了周期**。`double take`（真值 109）里，**最强峰在 825.8ms = 72.66BPM**，而真拍
    550.4ms 的强度只有它的 0.512；`OWA OWA`（真值 126）同理（最强 83.8 vs 真 125.8）。反查 raw 相关：
    72.66 的 `r(L)=0.361 / r(2L)=0.298 / r(3L)=0.397` 全部高于 109 的 `0.174 / 0.111 / 0.298` ——
    **这类歌的 onset 包络真的以"1.5 拍"为周期重复**（切分/复合律动的重音模式），所以
    **任何只看相关的判据都救不了它**。救它的是**攻击能量**：109 的网格上 30s 里收集到
    **7.535**（每拍 0.1395），72.66 只有 **5.416**（每拍 0.1504）—— 109 的网格**多收了 39% 的攻击**，
    只是每拍低 7%。也就是说：**真拍上"攻击更多"，错格上"相关更高"**。
  - **① 改动一：`BeatAnalysis.finerBeat`（周期选择里的和声关系检查）** —— 相关选完之后，拿它选中的周期
    的**更细和声亲属**（`2/3` 先（就是上面的错格）、然后 `1/2`、`1/3`）比对，取**第一个同时满足**
    下面两条的作为最终周期：
    · **攻击支持** `FINER_GRID_SUPPORT = 0.8`：更细网格的**每拍攻击能量 ≥ 粗网格的 80%**
      （= 拍与拍之间那 1/3 的位置不是空的）。合成材料实测 **0.30–0.50**（128 click 0.349、
      128+反拍 hat 0.343、dense 128 0.369、120+等响八分 0.365、120+三连音 0.502、120+每三拍重音 0.358、
      60 click 的 1/2 是 0.458）；真材料里**要救的那几首是 0.80–1.27**（double take 0.93、
      OWA OWA 1.02、HEARD OF US 1.23、ON MY WAY 1.27）。阈值 0.8 落在这条空档里，
      **最近的两个实测是 0.798 与 0.799**（都留在"不改"那一侧：0.798 是 DAY1 的半拍格、今天在过门；
      0.799 是 Better Now，它改成 145 也照样被门拒）。
    · **自身周期性** `FINER_GRID_PERIODICITY = 0.45`：该亲属附近**邻域内**的相关强度 ≥ 最优的 0.45
      （"邻域"是必须的：真拍的 lag 常常落在**两个峰之间**，卡在一个 lag 上会把它判死 —— 这是
      Better Now 的第一版死因）。合成材料：所有 click 类**一个都没被搬动**（55–200BPM 全中、
      反拍 hat/等响八分/三连音/每三拍重音/dense 128 全部**数字完全不变**）。
    · **近似关系要重新调谐**（`FINER_GRID_TUNE = 0.01`）：两个真实周期的 2:3 关系**不是精确算术**
      （实测差 0.5%），而脉冲串一旦漂开能量掉得极快 —— 30s 窗口里 0.5% = 145ms 漂移 = **38% 的能量**。
      所以候选周期在 ±1% 内**各取自己的最优**再比（不加这一步，Better Now 停在 0.799/0.8）。
    · 只从相关的选择出发**搬一次**（不链式），**只会更细、永不变粗**（变粗只会丢对齐分辨率），
      且受 `MIN_BPM/MAX_BPM` 兜住；**不满足就完全等于今天**（genuine 半拍/三分之一拍读数照旧保留）。
  - **② 改动二：`BeatAnalysis.segmentSupport`（置信度的"段内标尺"也要和声感知）** —— 只改周期的话，
    **通过率反而从 12/18 掉到 10/18**：门的 tempo 那一半还是"这一段在窗口 lag 上的相关 ÷ 它自己的最好"，
    而**段自己也会犯同一个 2:3 错**（10 秒的第三段同样在 1.5× 处相关更高）——`double take` 改正到 109
    之后 support 只有 **0.220**（它的三个第三段相位反而**更一致**：0.055/0.055/0.109 拍）。
    所以**2:3（及 3:2）的亲属不再允许当"标尺"**（`HARMONIC_RELATION_TOLERANCE = 0.03`），
    留下的最大值才作分母，并 clamp 到 1。效果：**double take 的相位半 0.826 终于在 support 上也不再被反咬**，
    Violet 0.271→**0.458（过门）**、HEARD OF US 0.104→**0.507（过门）**、Moonlight 0.415→0.618、
    Lalala 0.369→0.887、Ice Cream Man 0.599→0.653（负例全部保持不动：白噪/纯音/pad 无网格、
    变速窗口拒绝、120/170 拒绝、三段相位互斥拒绝、半拍跳变 0.61 < 稳定 0.91）。
  - **③ 磁盘格式 VERSION 3 → 4**（布局不变，只是**写的人变了**）：旧文件里的 BPM 是**旧估计器的答案**
    （很多是对的，但错的那些正是 2:3 错格 —— 那是不该被对齐的网格），所以一律不读、重测一次。
    真机日志确认：`disk cache written: … .bpm (29 B)` → `beat profile loaded for n1388960663: BeatProfile{127.8BPM/0.62 …}`
    （旧文件是 V3，直接重测）。
  - **校准（18 首用户队列的真实音频，PC 与设备数字逐位一致；"真值"来自外部 BPM 库/卡拉OK 伴奏页，逐条查过）**：

    | 歌 | 真值 | 旧：测得/置信 | 新：测得/置信 | 结论 |
    |---|---|---|---|---|
    | double take | **109** | 72.7 / 0.25 ❌门 | **109.0** / 0.00 | **2:3 错格被修好**（仍被门拒） |
    | OWA OWA | **126** | 83.8 / 0.44 ✅门 | **126.1** / 0.06 | **2:3 错格被修好**（相位半 0.09 拒之） |
    | Better Now | **145** | 97.2 / 0.30 ❌ | 97.2 / 0.30 ❌ | **仍是 2:3 错格**（0.799 vs 0.8，见上） |
    | Violet | **119–120** | 119.9 / 0.27 ❌门 | 119.9 / **0.46** ✅ | **正解，本来被门误拒 → 现在过门** |
    | Moonlight | 128 | 127.8 / 0.42 ✅ | 127.8 / **0.62** ✅ | 本来就是对的 |
    | Ice Cream Man | 144 | 144.0 / 0.60 ✅ | 144.0 / 0.65 ✅ | 不变 |
    | Portland | 136 | 135.8 / 0.51 ✅ | 135.8 / 0.51 ✅ | 不变 |
    | Life's A Mess | 143 | 143.2 / 0.48 ✅ | 143.2 / 0.48 ✅ | 不变 |
    | Edamame | 106 | 105.9 / 0.80 ✅ | 105.9 / 0.80 ✅ | 不变 |
    | One Right Now | 97 | 97.0 / 0.31 ❌门 | 97.0 / 0.31 ❌ | 正解，仍被门拒 |
    | Obsessed With You | 143 | 71.6 / 0.66 ✅ | 71.6 / 0.66 ✅ | 半拍（同一网格，可用）|
    | The Other Side… | 128 | 64.0 / 0.75 ✅ | 64.0 / 0.75 ✅ | 半拍，不变 |
    | White Iverson | 130 | 66.1 / 0.43 ✅ | 66.1 / 0.43 ✅ | 半拍（≈132），不变 |
    | Lalala(赛马娘版) | ? | 83.3 / 0.37 ✅ | **125.1 / 0.89** ✅ | 网格改了 1.5×（同一族） |
    | HEARD OF US | ? | 83.3 / 0.71 ✅ | **125.0 / 0.51** ✅ | 网格改了 1.5×（同一族） |
    | ON MY WAY | ? | 96.6 / 0.01 ❌ | **145.0** / 0.20 ❌ | 网格改了 1.5×，仍拒 |
    | DAY1 | ? | 66.9 / 0.35 ✅ | 66.9 / 0.35 ✅ | 不变（0.798 那条被挡住） |
    | 08 | ? | 170.0 / 0.26 ❌ | 170.0 / 0.26 ❌ | 不变 |
    · **通过率 12/18 → 12/18**（看起来没变，但**成分变了**）：13 首有真值的里，**正确的网格过门的
      8 → 9**（Violet 进来）、**错误的网格过门的 1 → 0**（OWA OWA 的 83.8 以前在过门）、
      **2:3 错格 3 → 1**（double take/OWA OWA 修成真值；Better Now 停在 0.799，且它无论改不改都被拒）。
      没有一首"本来对的变成错的"（合成 55–200BPM 一个数字都没动）。
  - **装机验证（真机 efaa83b2）—— `mix:` 第一次写出"做了什么"**：
    · 队列 `[Moonlight, Moonlight]`（**同一首两遍**：自动模式下同专辑 → CROSSFADE，但它也正好是
      "网格相同 + 调性相同"的极端对子），把「过渡方式」临时强制成**交叉淡化**（避免 AI 挑 CUT；
      测完已还原成 0=自动）。决策行（原样）：
      `transition: slot 0 -> 1: CROSSFADE 重叠=long 9801ms, curve=EQUAL_POWER (mix: overlap 8000->9801ms
      at A's tempo and key); …; beat: A=127.8BPM/0.62 (prom 0.38) B=127.8BPM/0.62 (prom 0.38),
      align=on (mix: speed x1.0000 on B (tempo already holds: 0ms of drift over 10000ms);
      pitch x1.0000 on B (0 semitones: keys already sit together, A minor/0.70 / A minor/0.70, chroma
      distance 0.00); overlap 9801ms = 21 beats of A at A's tempo, drift 0ms by construction;
      entry 40->19ms; bass swap at 4700ms)`
      之后：`incoming setDataSource + prepareAsync (startMuted=false, IncomingMix{x1.0000, pitch x1.0000,
      bassSwap=4700ms})` → `incoming mix applied … platform reports speed x1.0000 pitch x1.0000` →
      `bass swap armed: … 1 band(s) below 200Hz …` → `crossfade begin over 9746ms` →
      `ramping 9746ms` → **`bass swap done: the incoming track owns the low end now`**。
      **这是低频互换第一次在真实音频上真的换了低音**（EQ 按 sessionId 挂上并生效）。
    · 队列 `[Moonlight, Lalala]`（两首**不同的歌**、同一速度家族、网格兼容）：
      `… beat: A=127.8BPM/0.62 B=125.1BPM/0.89, align=on (overlap 8000->7921ms = 17 beats of A,
      drift 168ms of 240ms; entry 0->119ms); mix: off (key not trustworthy (A minor/0.05))` →
      arm → `ramping 7887ms` → `promoted queue slot 1 (Lalala)`。
      **`align=on` 用的是改正后的 B 网格（125.1，旧读数是 83.3）**：旧读数下这一对"节奏差 53%"，
      只会写 `align=off (tempos incompatible…)` + `mix: off (tempos too far apart…)`；现在**对齐真的发生了**，
      唯一的拒绝理由变成**调性**。`beat profile for n2608072275: BeatProfile{125.1BPM/0.89 …}` 就是
      设备自己现算的那一行。
    · 日志存了一份：`D:\qplayer-dev\harness\round8-device.log`。
  - **下一道约束（有数字，不是猜）—— 变速/移调真正跑不起来了，卡在"调性"。**
    把 36 首缓存全测了一遍（`BeatDump` 带 key）：**只有 5 首的 key 强度 ≥ `MIN_STRENGTH = 0.25`**
    （Obsessed 0.43、Moonlight 0.70、The Other Side 0.37、1410815174 0.32、1969845253 0.33），
    其余 **31 首都在 0.02–0.24**；而 `MixMatch` 要求**两侧 key 都可信**，于是：
    ① 这 5 首里**没有任何两首的速度差在 ±8% 以内**（最近的 71.6 vs 64.0 = 11.9%），
    ② 差一点就成的两对：**Ice Cream Man 0.24 vs 1410815174 0.32（速度完全相同 143.976，同 A minor）**
    与 **Violet 0.10 / Lalala 0.05 那类**，都被 0.25 这一刀挡在门外（Ice Cream Man 差 **0.01**）。
    也就是说：**修好拍格之后，`合拍改调` 的下一道门是调性估计器的强度阈值**（合成材料上它测出
    0.37–1.0、负例 ≤0.09，所以 0.25 在当时是合理的；真实密编曲整体低一大截 —— 与第五轮
    "置信度门"是同一个故事）。**这一轮没有动它**（没有真值材料来重新校准，动了就是盲目放宽）。
    另一条同类的：**95–100 家族**（One Right Now 0.312、Better Now 0.304、ON MY WAY 0.196）
    的正解仍然过不了 0.35，需要的是下一轮那件事（真值材料上重校 tempo 门/段长），不是再调周期。
  - **本轮没做的事 / 风险**：
    1. **变速（`setPlaybackParams` 真正拉伸）这一轮仍然没有被真机走到**：能跑的只有
       "网格相同/兼容 → x1.0000" 这条（上面两对都是 x1.0000）。原因是调性门（见上），
       不是代码。**EQ 低频互换这一轮走了**（bass swap armed → done）。
    2. **仍然没有任何人听过**：这一轮的所有验收都是日志与数字（`align=on` 的参数、`mix:` 的比例、
       bass swap 的两个时刻、promoted 之后位置继续前进）。10 秒重叠 + 两首歌的听感、
       低频那一刀的手感、6 秒还原 —— **全部没听过**。
    3. `FINER_GRID_SUPPORT = 0.8` 是从**一个库的 18 首**里选的，其中 13 首有真值；
       **风格样本量不足**（古典/现场/freestyle 没有样本）。两个最近的实测值是 0.798/0.799，
       阈值就压在那条边上 —— 换库要重新看这两个数。
    4. `HARMONIC_RELATION_TOLERANCE = 0.03` 的副作用是"一个第三段整体在 2:3 速度上"也会被当成
       同一个网格（现有测试里 120/180 那种 2:3 混合窗口本来就是"可接受"的，见
       `twoTemposWithNoCommonGridAreRefused` 的注释），**有意如此**但没有真机样本。
    5. 设备侧的 `MiX` 那次是**同一首歌两遍**（极端对子）：它证明了这条链在真实音频上能走完
       （网格→MixMatch→plan→后端→EQ），**不能**证明"两首不同的歌混在一起好听"。
    6. `MIN_BPM/MAX_BPM` 会挡住"整首歌都在 2:3 上、真拍低于 55"的极端情况（本轮没遇到）。

- **2026-09-19 第九轮：调性强度——在真实音乐上重新校准（chroma 折叠的是压缩过的幅度 + 强度是"亚军"的
  余量，两件都错）—— 装机验证，`mix:` 第一次真的在**两首不同的歌**上跑出变速+移调+低频互换**
  上一轮留下的下一件事。结论：**门不是要放宽，估计器是坏的**；改完之后 36 首里 21 首过门（原来 9 首），
  并且**变速（`setPlaybackParams` 真的拉伸）第一次在真机上被走到**。
  - ⚠️ **与上一轮记的数字不一致**：第七节第八轮写的是"只有 5 首的 key 强度 ≥ 0.25"，本轮在**同一批 36 个窗口**上用**未改动的旧估计器**重测是 **9 首**（0.251/0.253/0.321/0.334/0.370/0.432/0.434/0.541/0.704），另外 27 首落在 0.021–0.245。第八轮那次大概是更早/更小的一批窗口。**本文档一律以本轮的 9/36 为准。**
  - **测量工具（全在 `D:\qplayer-dev\harness\`，PC 侧，可复用）**：把设备的 36 个 `.f32` 窗口（24 字节/帧
    的 float32，`/data/local/tmp/bd/out/`）**逐个**拉到 `harness/f32/`（**不要 `cat *` 拼一个文件**：文件名
    里就有采样率，拼了就丢了）。`KeyCalib.java`（旧强度 + 候选测量的表）、`ChromaLab.java`（chroma 折叠方式
    的参数化：压缩函数/按 bin 求和还是求平均/逐帧归一化/下限频率/高频权重）、`VariantTable.java`、
    `Score.java`（**带真值表的准确率计分**）、`Strength.java`（真实曲目 + 7 个负例的分布）、
    `Pairs.java`（**编的是仓库里那份** `BeatAnalysis`/`KeyProfile`/`KeyAnalysis`/`MixMatch`，对全部
    36 首两两跑 `MixMatch.between`，把"过门/被拒+原因"都打出来）。PC 与设备数字**逐位一致**（下面的
    真机日志与 `Pairs.java` 的数字最后一位相同）。
  - **① 真正的病：chroma 几乎是平的。** `KeyAnalysis` 把 `log1p(幅度)` 在**五个八度的约 400 个 bin 上相加**，
    等于给每个音级都堆上一个近似常数。实测 36 首真实窗口：**每 bin 0.066–0.097，而均匀分布是 0.083**；
    冠军调自己的三和弦只占 **0.255–0.268**（均匀是 0.25）——它根本没有描述调性，**22/36 首都被报成 A minor，整库只用到 6 个调名**
    （这正是"31 首低于 0.25"的来源）。改成**直接折叠幅度**（去掉 log 压缩）后：三和弦 0.26–0.41、
    准确率表（下）大幅改善。**注意当初加 log 的理由（"别让一个低音主宰 profile"）不成立**：低音偏置由
    权重锥（400Hz 以下满权）和归一化处理，压缩反而把音级信息抹平了。
  - **② 强度不该是"与亚军的余量"。** 真实音乐里亚军几乎总是**关系大小调或五度**（36 首里 24 首），
    而 `camelotCompatible` 本来就要混它们 —— 所以旧读数测的是"调式歧义"，不是"有没有调"。现在
    **强度 = (冠军 − 最好的"不可能混的调") / `MARGIN_FOR_FULL_CONFIDENCE`（仍是 0.35）**，
    `MIN_STRENGTH` 保持 0.25（**数字没动，动的是它背后的测量**——与第八轮 `BeatAnalysis` 的
    和声关系检查同一个思路）。
  - **③ 一个测量分不开，所以加了第二把门**：`KeyProfile.MIN_TRIAD = 0.28`（冠军自己的主音/三度/五度
    必须占到 profile 的 28%，均匀是 25%）。理由是一条**构造出来的负例**：带固定频谱包络的噪声
    （低通 + 两个共振峰 + 3.1Hz 包络，也就是语音/掌声/镲片洗音在 chroma 里的样子）在余量上得 **0.469**，
    高于本库一半以上的真歌。它的 profile 是平的（三和弦 0.270）。**两把门一起**才让它出局。
    `triadWeight()` 从 `chroma()` 现算，所以**磁盘格式不需要变**（12 字节 profile 的量化保留形状）。
  - **实测分布（36 首真实 30s 窗口，PC 与设备一致；门 = 余量 ≥0.25 且三和弦 ≥0.28）**：

    | | 旧（压缩 + 亚军余量） | 新（幅度 + 无关调余量，两把门） |
    |---|---|---|
    | 过门（只算调性门） | **9/36**（另外 27 首是 0.021–0.245） | **21/36**（余量 0.012–1.54，中位 0.44；三和弦 0.261–0.407；其中 1 首没有拍格，所以**真能参与混音的是 20 首**） |
    | 报成 A minor | **22/36**（整库只用 6 个调名） | **4/36**（17 个调名） |

    **负例（全部 7 个都不进门）**：白噪 0.089/0.261、440Hz 纯音 0.000/0.994（被余量拒）、数字静音（无
    chroma）、无音高鼓组 0.192/0.272（两边都差一点）、纯音+噪声 0.029、**带包络的噪声 0.469/0.270**
    （余量过了、三和弦拒）、粉噪 0.461/0.273（同上）。**最薄的一条是 0.270 vs 0.28**（见风险 2）。
  - **真值核对（13 首查了外部 key 库：tunebat/musicstax/songbpm/Music Gateway 一类，多数有多个来源互相印证）**：
    过门的 11 首里 **7 首完全正确、3 首同 Camelot 号或差一格**（D#/Ab、G#/Db、Gmin/Eb 这些），
    **1 首差两格**（90210：测得 B minor，外部来源在 A minor / E minor / B minor 之间互相矛盾）。
    另外**只算两把门都过**的话，这 13 首里有 8 首（One Right Now / Can't Feel My Face / double take
    是被节拍门挡住的）。旧估计器在同一批里只有 **5/13 落在正确的和声邻域**（`double take` 报 Gmin 而真值 Ab、
    `Flashing Lights` 报 Amin 而真值 F#m、`Runaway` 报 Amin 而真值 C#m/E、`Edamame` 报 Amin 而真值 Bm）。
    ⚠️ **这些"真值"本身是算法产的**（外部库和我们的估计器同属一类方法，同一首歌常常互相打架），
    所以这张表是**弱真值**，只能当趋势看。
  - **④ 顺手修掉一个被这次校准暴露出来的 `MixMatch` 缺陷**：一个被 Camelot 允许、但改善不到
    `MIN_KEY_IMPROVEMENT = 0.05` 的移调，会让 `bestShift != 0` 走进拒绝分支 →
    **把"移调没意义"变成"这两个调打架"**。实测它拒掉了 `White Iverson → The Other Side Of Paradise`
    （chroma 距离 **0.18**，速度 1.033x —— 类里每一条阈值都说这两首合适）。现在这种情况
    **什么都不做**（= `pitch x1.0000 "keys already sit together"`，与"根本没有移调可用"同一条出路），
    并在日志里写 `; -2 was allowed but only reaches 0.17, so nothing is applied`；
    `atZero > MAX_KEY_DISTANCE` 的真打架仍然用原来的措辞拒绝。**注意返回值也要一致**：
    第一版只改了日志没把 `bestShift` 归零，`semitones()` 仍是 -2（测试抓到的，已修）。
    **效果：本库合适的**不同歌**对子从 5 对变成 9 对。**
  - **磁盘格式 VERSION 4 → 5**（布局没变，只是**profile 是旧折叠法算的**）：旧文件里的 key 是平
    profile 的产物，读进来就是"用错误理由信任/拒绝一个调"，与 3→4 同一个理由。真机日志确认：
    `disk cache written: … .bpm (29 B)` → 重新测量。
  - **装机验证（真机 efaa83b2，`transitionKind` 临时强制成 2=CROSSFADE，测完已还原成 0）**：
    队列 `[Moonlight, I'm Waiting (Radio Edit)]`（**两首不同的歌**；`positionMs=90090`），
    原始日志（`D:\qplayer-dev\harness\round9-device.log`）：
    · 两侧 profile 由**设备自己**测出，与 PC 逐位一致：
      `beat profile for n1408295636: BeatProfile{120.1BPM/0.37 (prom 0.81), KeyProfile{B major/1.00 (triad 0.32), camelot 1B}, first beat=19ms, period=500ms}`、
      `beat profile for n1388960663: BeatProfile{127.8BPM/0.62 (prom 0.38), KeyProfile{A minor/1.00 (triad 0.38), camelot 8A}, first beat=19ms, period=470ms}`。
    · 决策行（原样）：
      `transition: slot 0 -> 1: CROSSFADE 重叠=long 9801ms, curve=EQUAL_POWER (mix: overlap 8000->9801ms at A's tempo and key); remaining=24154ms, length=135090/270000ms, streamable=yes/yes, 智能过渡=on; beat: A=127.8BPM/0.62 (prom 0.38) B=120.1BPM/0.37 (prom 0.81), align=on (mix: speed x1.0637 on B (120.1->127.8BPM, +6.4%); pitch x1.0595 on B (+1 semitone: B major/1.00 (triad 0.32) (1B) -> C major/1.00 (triad 0.32) (8B), chroma distance 0.18->0.09); overlap 9801ms = 21 beats of A at A's tempo, drift 0ms by construction; entry 200->19ms; bass swap at 4700ms)`
    · 执行链（每一步都成功）：
      `incoming setDataSource + prepareAsync (startMuted=false, IncomingMix{x1.0637, pitch x1.0595, bassSwap=4700ms})` →
      **`incoming mix applied to the INCOMING player (speed x1.0637 pitch x1.0595 asked; platform reports speed x1.0637 pitch x1.0595; …)`**
      （**回读**）→ `bass swap armed: … 1 band(s) below 200Hz …` → `crossfade begin over 9745ms` →
      `CROSSFADE ramping 9745ms into queue slot 1 (EQUAL_POWER)` → `bass swap done: the incoming track owns the low end now` →
      `transition promoted, releasing the outgoing player (the incoming is at 19769ms; the overlap already played 10385ms at IncomingMix{…})` →
      `easing the promoted track back to its own tempo and pitch over 6000ms (from x1.0637 / x1.0595)` →
      `the promoted track is back at its own tempo and pitch` → `promoted queue slot 1 (I'm Waiting (Radio Edit)) at 19811ms`。
      之后 `dumpsys audio` 只剩一路 `state:started … sampleRate=44100`，**P0 没有复发**。
    · **这一条同时补上了第四轮/第八轮最大的缺口**：`setPlaybackParams` 在 parked 状态下**平台真的吃下去了**
      （speed 与 pitch 都回读一致），而且升降的还原跑完了 —— 第四轮风险 1 里那个"如果抛异常就挪到 start 之后"
      的方案**不需要了**。
  - **本轮没做的事 / 风险（重要）**：
    1. **仍然没有任何人听过**。这一轮的所有验收仍是日志：10 秒重叠、±6.4% 的拉伸、+1 半音的移调、
       低频那一刀、6 秒还原 —— **全部没听过**（第五/八/九轮都一样）。本轮动作幅度是历来最大的
       （同时变了速度、音高、低频），所以**听感这一条比任何时候都更该先做**。
    2. **`MIN_TRIAD = 0.28` 的余量很薄**：最接近的负例（带包络的噪声）是 0.270，而真实音乐这一侧
       最低的三首是 0.261/0.262/0.269（被判不信任，方向安全）。**换风格要重新看这条**。
       两个旋钮的实测敏感度（36 首，过门 = 余量≥F 且 三和弦≥T）：**T**：0.26 → 24、0.27/0.28/0.29 → 21、0.30 → 18；**F**：0.20/0.22 → 23、0.25 → 21、0.30 → 17。选 (0.25, 0.28) 是因为它落在两个负例（带包络噪声 far 0.469 但 triad 0.270、粉噪 0.461/0.273）与真实音乐（最低三首 0.261/0.262/0.269，都是被判不信任的那一侧）之间：**T 再降 0.01 就会放进那两条负例**，这是整组阈值里最薄的一条。
       `MIN_KEY_IMPROVEMENT = 0.05` 是**旧 chroma 上校准的**，本轮**没有重新校准它们**
       （`MixMatchTest` 的合成三和弦仍然过）。**换库/换风格时要回头看这两个数。**
    5. `Pairs.java` 里 9 对合适对子中，**2 对是 `speed x1.0000`（速度本来就合得上）**，
       真正带变速的是 4 对；**移调只有 3 对非零**（-2/-1/+1 半音各一）。样本依然小。
    6. 移调方向正确性只有"外部真值 + profile 距离改善"两层，**没有听感**；最坏方向仍是
       "移对了、但两首都听得出被改过调"（6 秒内还原）。
    7. ⚠️ 观察（**不是本轮改的**，但没人验证过）：`incoming mix applied` 那行的措辞是
       `… it is parked, and was started by the call` —— 也就是 **incoming 在 arm 那一刻就被
       `setPlaybackParams` 启动了**（静音滚动），到 ramp 开始时它已经跑了约 9 秒，晋升时它的位置是
       **19769ms** 而重叠只"放过"10385ms。**听者因此从没听到 incoming 的前 ~9 秒**。
       若这不是设计意图（P6 原文说 PARKED 由 `beginCrossfade` 起播），下一轮要查
       `AndroidAudioBackend.applyIncomingMix` 里的 start 调用；`playAt` 的 handoff 下限用的是
       `max(backend.position(), floor)`，所以它**跟着高的那个走**，不会重播。
  - **交付（Task B/C）**：debug APK `android-shell/app/build/outputs/apk/debug/app-debug.apk`，
    **98,295,855 字节**（ORT 的 arm64 `.so` 是大头；release 包未签名不能装）。
    **已发布为 GitHub Release 资产**（不提交进仓库，`.gitignore` 里的 `*.apk` 保持原样）：
    分支 `feat/ai-dj-transition`（commit `65976c5`，**`main` 未动**）、tag `ai-dj-transition-2026-09-19`、
    页面 `https://github.com/xiaozhuhou233/qplayer-compose/releases/tag/ai-dj-transition-2026-09-19`、
    直链 `https://github.com/xiaozhuhou233/qplayer-compose/releases/download/ai-dj-transition-2026-09-19/app-debug.apk`
    （实测 200，重定向到 CDN）。

- **2026-09-20 第十轮：15 秒是默认长度、收尾平淡就再早一点、彻底丢掉变速/改调（装机验证，8 个边界）**
  - **用户原话**（本次任务）：「两首歌融合的时间太短了，尝试在十五秒（如果歌曲较为平淡，没有人声，只是收尾）
    就开始融合背景鼓点音调啥的，这样更丝滑，不然像淡入淡出」+「先不管调速变调」。**第八/九轮那套
    `MixMatch`（变速 ±8% / 移调 ±2 半音 / key 门）整体下线** —— 不是关掉开关，是**删掉**：`MixMatch`
    与其 11 个单测、`IncomingMix` 的 speed/pitch、`AndroidAudioBackend` 的 `setPlaybackParams` 与
    6 秒还原（`applyTempo`/`startTempoRestore`/`finishRestoreNow`/`PROMOTION_RESTORE_MS`）、
    `TransitionPlan.OVERLAP_MATCHED_MS`/`mixed()`、`KeyProfile.MIN_TRIAD` 的第二把门（**只作为门删掉**：
    `KeyAnalysis`/`KeyProfile` 仍在，仍随拍格测量与落盘，只是**没有任何东西再拿它拒绝一次过渡**）。
    **全仓库再无 `MixMatch` 引用，也没有一处 `setPlaybackParams`。**（那一段代码在本地 git 历史里，
    想恢复就 `git show 65976c5:player-core/.../MixMatch.java`。）
  - **① 15 秒成为普通对子的默认**：`TransitionPlan.defaultOverlapMs(CROSSFADE)` 从 medium(8s) 改成
    **long(15s)**，`TransitionKind.CROSSFADE` 声明的窗口跟着改成 long；`HeuristicTransitionChooser.plan()`
    给 CROSSFADE 的标签是「两个普通流、前面有时间，所以走普通 `15s` 重叠（等功率：这么长的线性斜坡
    中点会凹 3dB）」+ `FadeCurve.EQUAL_POWER`。
    **长度是下限不是上限**：`PlayerController.widenForOrdinaryPair`（`decideTransition` 里、在"裁到还剩多少"
    之前）对**两侧都 ≥90s 的 CROSSFADE** 先抬到 15s（chooser 报了更短就写进 label：`raised to the
    ordinary 15s blend (the chooser asked for Nms)`），再按下面的"平淡收尾"往上抬。**AI 答案不能取消它**：
    `AiTransitionChooser.plan` 现在对**不重叠的 kind**（CUT/FADE_OUT_IN/SILENCE_TRIM）在本地规则会给重叠
    时**推翻它**并写一行 `AI answered X, but this pair is two streamable tracks with time to spare ... a blend
    given up, so the local rules' answer (CROSSFADE) is used instead`；`SHORT_TRACK_MS` 以下的短歌、
    以及强制「过渡方式」时仍然照旧。
  - **② "平淡收尾" = 用已有的静音测量多算一个数，不新增分析**：`SilenceProfile` 新增
    **`plainTailMs`（收尾有多"平"）与 `tailAttacks`**，由**同一次尾部解码**的 20ms 块包络算出
    （`AndroidSilenceProfiler.TailSink` 多存一个 20s 的包络数组 + 一个 250Hz–3.5kHz 一带的占比，
    窗口从 10s 提到 **20s**（`TAIL_WINDOW_MS`），`tailMs` 仍封顶 10s、`DEADLINE_MS` 4→6s）。
    判据（`SilenceProfile.plainTailMsOf`，纯 Java，11 个单测）：从文件末尾往前扫，一块算"平"要同时满足
    ·**没有攻击**（后一块不比前一块高 2 倍以上，`ATTACK_RATIO`）；·**声带带占比 < 0.75**（`PLAIN_PRESENT_MAX`，
    且只对 ≥-40dBFS 的块生效，`VOICE_FLOOR` —— 近静音块的占比是两个接近 0 的数相除，实测把一个
    20s 的平淡收尾砍到 1.8s）；·**不比窗口前半段最响的那一块更响**（`PLAIN_LEVEL_SHARE=1.0`，这就是
    "还在往结尾爬"的那条）。
    ⚠️ **这几个数是在 18 首真实收尾上重新定的**（见下），不是拍的：第一版用"低于窗口最响十分位的
    一半(-6dB)"当响度门，18 首里**没有一首**能连续 15s 满足（多数连 3s 都不到 —— 衰减段的"响"就在
    收尾自己里面）。
  - **③ 平淡收尾 → 更早开始**：`widenForOrdinaryPair` 第二段：`plainTailMs ≥ 15s` 时，重叠 =
    `min(plainTailMs, OVERLAP_EXTENDED_MS=20s)`，label 写 `the outgoing track's ending is measured plain for
    Nms (nothing attacks in it, nothing sustained in the vocal band, nothing still rising), so the blend
    starts at the start of it rather than at 15000ms`。**只有测量能给**（没测到 = 0 = 一切照旧）。
  - **④ 守卫按"最长的重叠"重新定尺**（这是任务点名的那条"别让长重叠被静默降级"）：
    `TRANSITION_DECIDE_LEAD_MS = OVERLAP_EXTENDED_MS(20s) + TAIL(250ms) + LEAD(9s) + BEAT_SNAP_SLACK_MS(600ms)
    = 29 850ms`（原 24 250ms 是给 15s 准备的）；arm 仍是计划自己的 `overlap+tail+9s`。
    **每个边界那行现在同时写提前量、要求和实际**：`remaining=29847ms, lead=25750ms, 重叠=long 16500ms`
    → 后面 `ramping 16478ms`（实际）。被裁时 label 写 `capped to what is left (Nms)`。
  - **⑤ 第二到第四个边界：incoming 必须从融合开始就一直被听到**（上一轮遗留的缺陷：
    `promoted ... at 19769ms` 而重叠只"放过"10385ms）。根因是 `setPlaybackParams` **会启动一个只 prepare 过的
    player** —— 变速下线后那个副作用一起消失了，`AndroidAudioBackend.onIncomingPrepared` 的 PARKED 分支
    现在**主动校验**：`parkIncoming()` 看 `isPlaying()`/`getCurrentPosition()`，动过就 `pause()`+`seekTo` 回
    偏移并 `Logger.warn("the parked incoming had started itself ...")`。真机 8 个边界**一次都没触发**那条 warn
    （= 平台确实没自己启动），而晋升那行现在是
    `the incoming is at 16345ms; the overlap heard 16518ms = its start 40ms + the 16478ms ramp`
    （差 = MediaPlayer 的启动延迟，另一处日志单独解释：`its own clock is Nms behind the ramp's wall clock
    (start latency, not a skipped part: nothing is seeked here)`）。
  - **⑥ 低频互换不再被"两格不兼容"带走**：15s 重叠让 `gridsCompatible` 的容忍度从 4s 的 6% 收紧到约 1.6%，
    实测这个库里的对子**几乎全部**落在门外 → 而互换只需要 **A 自己的一拍**（A 在按自己的速度跑）。
    所以 `alignToBeatGrid` 里三条"不aligned"的分支现在**照样把 `IncomingMix{bassSwap}` 交给后端**，
    日志写成 `mix: bass swap, no tempo/key (the two grids slide 1997ms apart over 16525ms, so the overlap is
    not aligned (nothing is stretched to force it); bass swap at 8380ms)`；aligned 时是
    `mix: align + bass swap, no tempo/key (aligned to A's beats, bass swap at 4700ms)`。
    **"没有变速/改调"这句话现在印在每一个重叠边界的 mix 片段里**。
  - **⑦ 装机验证（Redmi K20 Pro `efaa83b2`，8 个边界，全部是真机日志；`transitionKind=0`(自动)、
    curve=1(等功率)、`aiBaseUrl` 为空 → 本轮全部走本地规则）**：

    | 边界 | kind | 要求 | 实际（ramping） | 说明 |
    |---|---|---|---|---|
    | Life's A Mess → Moonlight | CROSSFADE | **16500ms** | 16478ms / 16470ms / 16433ms | `plainEnding=16500ms` 触发提前开始；3 次重跑一致 |
    | 90210 → Pray 4 Love | CROSSFADE | 15000ms | 14925ms / 14932ms | 普通对子；B 无拍格 → `align=off (no grid for B)`，**bass swap at 7440ms 照常** |
    | Portland → Lucid Dreams | CROSSFADE | 15000ms | 14914ms | B 不在音频缓存 → `pre-caching ... 26460KB` 后 `served from the audio cache`；第二次**故意删掉缓存文件**→ `official=-, unblock=ok, playable (not cached: resolved inside the boundary's window)` 照样 arm |
    | I'm Waiting → Moonlight | SILENCE_TRIM | 250ms | 250ms | A 实测尾部静音 4600ms → 规则选裁切（**唯一会因为测量选它的分支**） |
    | Moonlight → Lalala | CUT | – | – | `too late: less than 1500ms left`（物理原因，唯一的 CUT） |

    · 连续性（P0/不重播）：`promoted queue slot 0 (Moonlight) at 16381ms` 之后队列存档的位置继续前进
      （另一次：晋升 14860ms → 40 秒后 `positionMs=59373`），`dumpsys audio` 只有一路 `state:started`；
      `handoff resume=16518ms (the promoted player reports 16382ms, the overlap heard 40..16518ms)` ——
      **晋升后的位置下限是"重叠已经放过的量"**，所以第二首不会从头再放。
    · `bass swap armed → bass swap done` 在 Life's A Mess → Moonlight 与 Portland → Lucid Dreams 上都真的跑了。
  - **本轮没做/未验证**：
    1. **没人听过**（第五/八/九/十轮都一样，这次动作最大：默认长度翻倍 + 提前开始的判据）。
       "15 秒比 8 秒好听吗""20s 的提前开始会不会太长"只有耳朵能判。
    2. **AI 这条链本轮没被走到**（设备 `aiBaseUrl` 是空的 → 一直用本地规则）。所以
       `AiTransitionChooser` 那两个新分支（"无 AI 答案时给规则自己的 plan"、"AI 说不重叠就推翻"）
       **只有代码与单测**；第八/九轮的 `AI cached`/`AI 决策` 行为没变。
    3. **平淡收尾的判据是用 18 首的收尾定的**（这库几乎全是 hip-hop/EDM）：`plainTailMs ≥ 15s` 的
       **只有 Life's A Mess 一首（16500ms）**，Runaway 14840ms（差 160ms 没进）、White Iverson 12700ms、
       Flashing Lights 0（-18.5dBFS 的安静收尾里有一块 0.9 的带占比，那是 `VOICE_FLOOR` 那条修之前的数）。
       ⚠️ **`PLAIN_PRESENT_MAX=0.75` 是全部常量里最薄的一个**：18 首实测中位 0.21–0.85，中间没有真值来
       划"有没有人声"这条线；它只拒掉了 I'm Waiting（0.85，整段是 EDM 主音）。换风格要重看。
    4. 这个库的拍格**在 15s 上几乎都不兼容**（`gridsCompatible` 的容忍度按重叠长度收紧），所以
       **`align=on` 本轮一次都没出现**（8/8 都是 `align=off`）：对齐仍然在，只是要求严了。想让长重叠
       也能对拍，要么给对齐换一种做法（把重叠吸附到两格都"站得住"的长度），要么重新考虑兼容门。
    5. `SILENCE_TRIM` 仍是"有实测尾部静音就选它"，所以**一个收尾有 15s 静音、却又有 15s 平淡段的歌，
       永远走裁切而不是长融合**（本库没遇到）。
    6. 非可流式（BILI/LOCAL）造成的 CUT 本轮没有真机样本（无法在队列里造出可播的 BILI 条目），
       只有代码路径：heuristic 规则 1 + `decideTransition` 的 `cannot be performed into <source>` 降级。
    7. 设备的静音/拍格缓存里是我这轮重测的值（缓存格式 VERSION 3 会拒掉旧的 17/8 字节文件，所以
       **换包就会重测**，不是脏数据）。测完已把 `queue.json` 还原成原来那两首（playIndex=1/21325ms）、
       删掉 `/data/local/tmp/q.json`；**设置一项都没动**（`transitionKind=0`、curve=1、smartTransition=on）。
  - **交付**：debug APK `98,293,607` 字节；分支 `feat/ai-dj-transition`；tag `ai-dj-transition-2026-09-20`；
    页面 `https://github.com/xiaozhuhou233/qplayer-compose/releases/tag/ai-dj-transition-2026-09-20`、
    直链 `https://github.com/xiaozhuhou233/qplayer-compose/releases/download/ai-dj-transition-2026-09-20/app-debug.apk`。
  - **下一件事**：① 听（15s + 提前开始的听感，这是唯一还没有人做的事）；② AI 那条链（给它配一个
    baseUrl 再跑同样的 8 个边界）；③ 若"15s 好听但没人对得上"，回头处理对齐的兼容门（见未验证 4）。

- **2026-09-20 第十一轮：变速/改调以"自然化器"身份回归 + DJ 式分阶段曲线（装机验证，10 个边界）**
  用户反馈：「听感可能还是不到十五秒但是还是比以前强了」+「你可以想办法降调/变速啥的让过渡更加自然，优化吧」。
  **第十轮删掉的那条 lane 整体回归，但角色反过来了：它们是"让两首更融合"的手段，永远不是"能不能混"的条件。**
  - **① 诊断（先看数据再改）**：第十轮的斜坡是**对称等功率**，两首"都清楚听得到"的区间只有中间约 **41%**
    （`FadeCurve.bothAudibleMs`：两路增益相差 ≤6dB 的时间），两端各有一段只有一首在响 = 听感就是淡入淡出；
    而且真机 **8/8 个边界 `align=off`** —— 15s 的兼容门只容忍约 1.6% 速度差，这个库的对子普遍飘 2% 以上，
    所以**完全没有节奏锁**，这正是"像淡化不像混音"的原因。所以这一轮做了两件事：把速度拉齐（让长重叠真的能对齐）、
    换曲线形状（让中间大部分时间两首都听得到）。
  - **② 变速/移调回归为自然化器（新增 `audio/MixNaturaliser`，纯 Java；`IncomingMix` 重新带上 speed/pitch）**：
    - **变速**：`speed = periodB/periodA`（把 B 拉到 A 的拍上），**只作用在 incoming 那一路**、保音高
      （`setPlaybackParams`，平台 Sonic）。**clamp 仍是施加在 B 上的 ±8%**（`IncomingMix.MAX_SPEED_STEP`），
      超出**不伸缩**——但**绝不因此取消过渡**（旧版会退化成短斜坡，这一轮明确反过来）。
      本来就合得上（`gridsCompatible`）也**不伸缩**（0.3% 的拉伸只有 artefact）。
    - **改调**：候选 n ∈ {-2..+2}，用 `KeyProfile.distance` + Camelot 相邻/大幅改善两条许可 + "值得做"
      （改善 ≥0.05 且 ≤原距离 75%）三层；**不满足就是 0 半音，不是拒绝**。`IncomingMix.MAX_SEMITONES = 2`。
    - **晋升后 6 秒平滑还原**（`AndroidAudioBackend.startTempoRestore`，10Hz 步进；暂停/seek/换歌/释放
      **立即**还原到位），第四轮那套回来。
    - **读回**：`applyTempo` 之后 `getPlaybackParams()` 回读并打日志，控制器在 ramp 开始前读
      `backend.incomingMix()`，**只有平台真的吃下去了才把 `crossfadeIncomingSpeed` 记成那个比例**
      （`playAt` 的 handoff 因此用 `start + round(ramp × speed)`）。**为什么必须读回**：文件时间轴被拉伸后
      "重叠已经放过多少毫秒"不再是 rampMs。
    - **对齐的兼容门改成问"听众会听到的那个网格"**（`MixNaturaliser.asHeard(b, speed)`）：拉过速度之后
      B 的拍周期**就是** A 的，drift 0ms，"两格滑开"这条不再拒掉长重叠 —— 这就是 `align=on` 从 0/8 变成
      5/7 的原因。
    - **⚠️ 一个平台副作用必须有对策**：`setPlaybackParams` **会把只 prepare 过的 player 启起来**
      （第九轮就观察到了）。`onIncomingPrepared` 现在在应用 mix **之后**调 `parkIncoming()`：pause + 回到
      自己的偏移，并写一行 warn 说明是 mix 把它启起来的、这一步把它放回去了。真机每个拉伸边界都能看到这行，
      晋升那行的"重叠听过 start..start+ramp"仍然连续（**"第二首不从头播/不被跳过"这条不变量仍然成立**）。
  - **③ 曲线换成 `FadeCurve.DJ_BLEND`（"DJ 式"，新增第三个枚举常量，排在末尾所以磁盘格式不变）**：
    - 形状：outgoing `cos(t^1.8 · π/2)`（前 55% 基本压在自己电平上，最后约四分之一才退出去）；
      incoming `sin(t^0.55 · π/2)`（第一个七分之一就到 bed 电平，此后一直在下面铺着）。
      中段两路都接近满——这正是"像串烧"的地方——代价是**中段功率约 +2.0dB**（`FadeCurveTest` 量了上界
      2.5dB）；低频那一刀保证最相关的低频段永远不会两份叠起来。
    - **谁来选**：`TransitionPlan.curveOr` 现在对 **≥8s 的重叠**强制 DJ 式（原来这里是强制等功率，理由从
      "别凹 3dB"换成"对称形状只有约 41% 的时间两首都听得到"）；不足 8s（快速淡化/AI 自己选的 short）
      仍然听设置的曲线。`HeuristicTransitionChooser` 给 CROSSFADE 的标签直接写 `FadeCurve.DJ_BLEND`。
      设置「淡化曲线」加第三个选项（默认改成 2=DJ 式，但**已装的机器里旧值 1 会保留**，只影响 <8s 的边界）。
    - **可量化代理**：每个重叠边界那行现在写
      `both tracks audible for 10039ms of the 15039ms ramp (67%) — the symmetric EQUAL_POWER-style ramp gives 6166ms of the same 15039ms`，
      即"两首互相在 6dB 之内的时长"，新形状 **≈67%** 对对称形状的 **41%**。
  - **④ 真机验证（Redmi K20 Pro `efaa83b2`，10 个边界；`transitionKind=0`(自动)、curve=1、`aiBaseUrl` 空
    → 全走本地规则；日志在 `D:\qplayer-dev\harness\round11\`）**：

    | 边界（A → B） | kind | 要求 → 实际 | 两首同响 | 速度 | 半音 | align | 低频互换 |
    |---|---|---|---|---|---|---|---|
    | 90210 → I'm Waiting | CROSSFADE | 15137 → **15039** | 10039ms(67%) / 对称 6166ms | **x1.0752**（回读一致） | 0 | **on** | 7440ms ✓ |
    | Lalala → I'm Waiting | CROSSFADE | 15050 → **14967** | 9990ms(67%) / 6136ms | **x1.0416**（回读一致） | 0 | **on** | 7680ms ✓ |
    | Lalala → HEARD OF US | CROSSFADE | 15050 → **15044** | 10042ms(67%) / 6168ms | x1.0000（本来合得上） | 0 | **on** | 7680ms ✓ |
    | The Other Side Of Paradise → White Iverson | CROSSFADE | 15271 → **15181** | 10133ms(67%) / 6224ms | **x0.9681**（回读一致） | 0 | **on** | 7504ms ✓ |
    | Life's A Mess → death bed | CROSSFADE | 16525（平淡收尾抬升）→ **16508** | 11019ms(67%) / 6768ms | x1.0000 | **+2**（`pitch x1.1225`，回读一致） | **on** | 8380ms ✓ |
    | 90210 → Life's A Mess | CROSSFADE | 15000 → **14954** | 9982ms(67%) / 6131ms | x1.0000（**超出 clamp，不伸缩**） | **+1**（回读一致） | off（两格滑 1486ms） | 7440ms ✓ |
    | Ice Cream Man → In My Head | CROSSFADE | 15000 → **14922** | 9960ms(67%) / 6118ms | x1.0000（**超出 clamp**） | 0（A 的调不可信） | off（两格滑 5281ms） | 7506ms ✓ |
    | I'm Waiting → Moonlight | SILENCE_TRIM | 250 → 250 | – | – | – | – | – |
    | death bed → Life's A Mess | SILENCE_TRIM | 250 → 250 | – | – | – | – | – |
    | Moonlight → I'm Waiting（剩 800ms 起播） | **CUT** | – | – | – | – | – | – |
    | Moonlight → I'm Waiting（剩 2000ms 起播） | **CUT**（SILENCE_TRIM 来不及测量→硬切） | – | – | – | – | – | – |

    · **`align=on` 5/7 个重叠边界**（第十轮 0/8；另外 2 个 off 都是"超出 clamp"——**那是测量结果，不是拒绝**）。
      两条 on 的完整行（原样）：
      `align=on (overlap 15000->15137ms = 33 beats of A, drift 0ms of 232ms; entry 200->519ms); mix: speed x1.0752 on B + bass swap at 7440ms + aligned to A's beats (speed x1.0752 on B (120.1->129.1BPM, +7.5%); pitch x1.0000 on B (0 semitones: keys already sit together, B minor/0.29 (triad 0.34) / B major/1.00 (triad 0.32), chroma distance 0.25; -2 was allowed but only reaches 0.24, so nothing is applied); …)`、
      `…; mix: +2 semitones on B + bass swap at 8380ms + aligned to A's beats (speed x1.0000 on B (tempo already holds: 89ms of drift over 16520ms); pitch x1.1225 on B (+2 semitones: E minor/0.75 (triad 0.30) (9A) -> F# minor/0.75 (triad 0.30) (11A), chroma distance 0.28->0.20); …)`。
    · **平台回读**（"真的吃下去了"的唯一证据）每个拉伸/移调边界都有：
      `MediaPlayer: incoming mix applied to the INCOMING player (speed x1.0752 pitch x1.0000 asked; platform reports speed x1.0752 pitch x1.0000; the audible track is untouched; it is parked, and it was started by the call — the park below puts it back)`。
    · **连续性/P0**：晋升行 `the overlap heard 519..16689ms`（= 起点 + 整个 ramp，**连续、不跳不重**）、
      handoff `resume=16864ms`（≥ 重叠已放过的量）、6 秒还原跑完
      (`the promoted track is back at its own tempo and pitch`)；`dumpsys audio` 全程只有**一路**
      `state:started`；**全部 10 个边界都没有出现 `playAt: slot N starts at 0ms`**（没有一次从 0 重放）。
    · **覆盖**：blend 7（全部 CROSSFADE），trim 2，CUT 2（1 个"太晚"、1 个 trim 来不及）；
      7 个重叠边界的低频互换**全部 armed 且全部 `bass swap done`**。
  - **本轮没做/未验证**：
    1. **仍然没有任何人听过**（第五/八/九/十/十一轮都一样）。这一轮同时改了曲线形状、变了速度（最大 +7.5%）
       与音高（±2 半音），所以"听感"这条比任何时候都更该先做。**没人听过的具体清单**：DJ 式曲线的听感、
       2dB 中段抬升会不会显得"音量跳"、6% 拉伸的 artefact、+2 半音（`pitch x1.1225`）是否听得出来、
       6 秒还原是否听得出"速度在爬"、以及低频互换在 15s 重叠中间那一刀。
    2. **AI 那条链本轮没走到**（设备 `aiBaseUrl` 仍为空）；AI 现在能选 DJ 式曲线（提示词已加），但没有样本。
    3. **调性门仍然常拒**：本库实测 7 个重叠边界里只有 2 个真的移了调（`+2` / `+1`），其余是
       "A 的调强度 <0.25 / 三和弦 <0.28"或"允许的移调只把距离从 0.25 拉到 0.24（不值得做）"。**换风格要重看**。
    4. **速度锁的相位**：拍点在斜坡起点对齐是"入口落在 B 的一拍 + 斜坡起点落在 A 的一拍"两条合起来的
       推论，**残余误差是 MediaPlayer 的启动延迟**（本轮实测 123–822ms，`its own clock is Nms behind the
       ramp's wall clock`），所以两首的鼓组并不总是精确重合（差最多约半拍）。真机日志里 `drift 0ms` 说的是
       **两格不再相对滑移**，不是"绝对相位零误差"。
    5. 15s 重叠的听感本来就没有人验证过（第十轮遗留），这一轮把它换成了 DJ 式，**两个变量一起动了**。
    6. `pause/seek/焦点丢失` 会 `finishRestoreNow()`（立即还原到 1.0），这条路径本轮没有真机样本。
    7. `promoted` 的 `resume` 用重叠计数做下限（设计如此），本轮实测播放器自报位置最多**落后 822ms**，
       也就是**发布的进度/歌词位置最多比音频快 0.8 秒**（不是回归，第八轮就存在；但这次速度拉伸会把
       "已经放过的文件毫秒"算得更准，所以差别更明显）。
  - **交付**：debug APK；分支 `feat/ai-dj-transition`；tag `ai-dj-transition-2026-09-20b`；页面/直链见下。
  - **下一件事**：① **听**（唯一还没人做的事，建议先听 `Life's A Mess → death bed`：16.5s + DJ 式 + 6dB
    中段抬升 + 一个 `+2` 半音的移调，信息量最大）；② 把 AI 那条链接上再跑同样几个边界；
    ③ 若"15s 好听但两首鼓组还是错开"，下一步是把入口偏移从"自己的拍点"改成"与 A 的拍点对齐"
    （现在受 `MAX_BEAT_ENTRY_SHIFT_MS = 400ms` 与启动延迟限制）。

- **2026-09-20 第十二轮：过渡时长成设置项、人声感知的 incoming（DJ 编辑）、音高规则、第一个歌的拍子
  （四项都装机验证；Redmi K20 Pro `efaa83b2`，`transitionKind` 临时强制 2 跑完已还原 0，模型跑完已删除）**
  **用户原话（四项）**：①「在设置里添加能让用户自定义过渡时间的滑动条设置」②「把前一首后一首歌的开头和结尾
  人声分离只混背景，再逐渐接近人声，如果两首歌结束和开始时没有人声的话」③「在下一首歌人声出来前两秒钟
  必须停止降调」④「让第一个歌的拍子不那么早结束，取决于你」。
  - **① 过渡时长（滑动条）**：`SettingsCatalog.TRANSITION_BLEND_KEY = "transitionBlendSeconds"`，
    `SettingSpec.slider(..., 15, 4, 30, 1)` + `unit(" 秒")` + `dots()`，`dependsOn(智能过渡)`；
    目录里**本来就有 slider 类型**（`SettingSpec.SLIDER`，字号/缓存上限/超时都用它），所以 Compose
    设置页与 QML 页**一行 UI 都没改**（`ComposeQPlayerActivity.kt` 的 `SettingSpec.SLIDER` 分支 /
    `shared-qml/settings/SettingSliderRow.qml` 自动渲染）。`SettingsCore.pushTransition` 推
    `PlayerController.setBlendDurationMs(秒 × 1000)`（内部 clamp 到 4–30s）；控制器**只在决策那一刻读**，
    所以**改完不用重启、下一个边界生效**，并打一行 `transition: 过渡时长 set to 20000ms (20s)`。
    `TransitionPlan.OVERLAP_LONG_MS` 仍是常量，但**只作为 chooser 没指定时的答案与设置默认值**；
    「平淡收尾」的上抬**改成相对用户值**（`PLAIN_EXTENSION_MS = 5s`，`plain >= 用户值` 才抬，
    上限 `用户值 + 5s`）——真机两种都验到了：用户 20s 时 Life's A Mess（`plainEnding=16520ms`）**不抬**
    （16520 < 20000，边界就是 20000ms）；用户 15s 时抬到 16520ms，日志写
    `(at most 5000ms past the user's 15s)`。
    `TRANSITION_DECIDE_LEAD_MS` = `BLEND_MAX(30s) + 5s + 250ms + 9s + 600ms = 44.85s`（按最长的**可能**计划定尺）。
  - **② 人声感知的 incoming = 一个 DJ 编辑文件（只做 incoming 一侧，这是本轮的取舍）**
    · **为什么只做一侧**：outgoing 是一路正在播的流，剥离它必须**在播放中途换音频源**——那正是 P0
      （"晋升即死"）与"第二首从头重放"两次事故的机制。incoming 可以干净地做：**渲染成一个连续文件**
      （背景 + 人声在一条小节线上回来 + 之后就是原 master），incoming 播放器**自始至终只开这一个源**，
      过渡、晋升、之后的整首歌都不换源。**要不要剥 outgoing 是下一个切片**（见未验证 8）。
    · **模型查找**：app 私有目录 `files/models/`，`StemModel` 的清单里两个候选，**名字 + 字节数 + sha256
      三者全对才认**：`htdemucs-quarter.onnx`（97,978,156 字节，`427b9588…4aec20`）与
      `htdemucs.onnx`（108,644,650，`099b5be7…d0912329`）。**缺失或不匹配 ⇒ 完全 inert**，只写一行日志
      （含 `adb push` 那一行与两个哈希），边界与今天**逐位相同**——真机验到：无模型时
      `stem DJ edits are OFF — no verified model in /data/user/0/dev.t1m3.qplayer.debug/files/models …`
      以及控制器的 `no DJ edit for Moonlight this time; the boundary blends the plain stream`。
      **注意**：`available()`/`logInertReason` 只在**真的被要求渲染时**才跑，所以那行不在起播时出现。
    · **只挂 preload lane**：`PlayerController.requestStemEdit`（在 `precacheNextAudio` 的同一条
      `qplayer-precache` 单线程 lane 上，下一首音频到位那一刻提交；`stillWanted` 每 16 个解码块 / 每个分离
      分块轮询一次，队列一走就丢）。**渲染成本实测比第六轮估的高**：`separation` 25.9–28.2s（20–22.7s 窗口）
      + 整曲 AAC 重编码 → **整跑 53.2 / 53.4 / 72.1 / 82.5 秒**（`Session 0.6s`、`arena off`、4 线程、
      quarter 段长 86016）；所以要 **≥2 分钟的提前量**（本轮给的是 120s，都在边界前完成）。
      产物落到新子目录 `files/cache/djedit/`（键 = `trackKey + "@" + removalMs`，文件名
      `abs(key.hashCode())+".m4a"`，**扩展名不能改**：平台按扩展名挑 extractor），计数上限 4、计入
      `totalSize()` 与 LRU/clearAll（`DiskCache.DJEDIT`）。**文件存在即"渲染完成"**：任何失败路径都删掉半成品。
    · **文件内容**（`DjEdit` + `AndroidStemEditRenderer`）：解码**文件开头**的
      `removalMs + (一拍×4 + 1s，上限 8s)` 窗口 → 需要时重采样到 44.1k（库里 48k 的歌很多）→ htdemucs 分离
      → `other` 用**减法**重算（模型四轨相加只有 −32dB，会有"残余人声"，减出来才精确）→
      `DjEdit.renderHead`（其它三轨 unity，vocals 走 `Plan.vocalGainAt`）→ 重采样回源采样率 → **同一个
      encoder/muxer 继续把整首歌的其余部分接在后面**（时间轴 = 文件时间轴，所以 content start / 拍点入口 /
      斜坡 / 发布位置**含义不变**）。**不加总线限幅器**（一路 deck，编辑必须就是 master；只用 16-bit 硬
      clamp 并计数）。人声回来的位置 = **incoming 自己的一小节线**：`downbeatOffsetSec` 从**分离出来的 bass
      轨**低频包络估（平台没有结构分析，这是第 7 轮就写下的"扩展、非移植"），**从不用拍格相位**（那错 3/4）。
    · **人声存在性（用户那条 clause）**：`DjEdit.presence`，判据 = 窗口内 25ms 帧里**高于 −50dBFS 的比例
      ≥ 5%**（`SINGING_FRAME_SHARE`）。**装机实测：三首 incoming 全部 "sings"**（Moonlight 31%、
      Life's A Mess 99%、The Other Side Of Paradise 30%），所以**短路分支本轮一次都没走到**；而且按第 7 轮
      测过的两段"没人声"素材（In My Head 前奏 27.9% 高于门、坏女孩 33s 段 56% 高于门）**它们也不会短路**。
      **结论：这个阈值实际上让所有真实歌都走渲染色**——想省下渲染（50–85s CPU）就动
      `SINGING_FRAME_SHARE` 这一个数，不要动别的。这条日志正反两面都写清（`its first Nms does sing — …
      median/p90/peak/静音帧占比` / `not needed — …`）。
    · **人声抑制"实测"（不是断言）**：把渲染出来的 `djedit/589080835.m4a` 与 app 的原始
      `audio/1460801818.cache` 都放到设备 `/data/local/tmp/hb/`，用**同一个分离 harness**
      （`HtdemucsBench --metrics`）各测一遍同一窗口：
      **blend 窗口（0–20s）：vocals rms −22.5 → −43.0 dBFS（抑制 20.5dB），25ms 帧静音占比 1.4% → 80%**；
      **小节线之后（22–32s）：vocals −19.4 → −19.5 dBFS（0.1dB = 二次编码的差），mix −18.4 → −18.5**，
      即"人声在小节线回来、之后就是 master"。**对照行**：`other`（最大的一路背景）−30.1 → −30.3dBFS，
      证明编辑**只**动了人声。（⚠️ 那条 harness 命令解 48k 不重采样，两个文件都一样地测，所以**比较成立**、
      绝对值不可当真——见它自己打的 `WARNING: source is 48000Hz`。）
    · **播种（delivery 仍未实现，这一行就是交付路径）**：
      `adb push htdemucs-quarter.onnx /sdcard/ && adb shell run-as dev.t1m3.qplayer.debug cp /sdcard/htdemucs-quarter.onnx files/models/`
      （本轮装机就是这么做的：模型本来就在设备上，`run-as cp /data/local/tmp/hb/htdemucs-quarter.onnx files/models/`
      **不用重新 push 98MB**）。**跑完已把模型删掉**（保持用户默认行为不变）；用户想试再 push。
  - **③ 音高规则（用户那条"人声出来前两秒必须停止降调"）**：`MixNaturaliser` 新增
    `VOCAL_PITCH_MARGIN_MS = 2000` 与 `pitchFitsBeforeVocals(vocalIn, overlap)`；`IncomingMix` 新增
    `pitchIdentityAtFileMs`（+`RESTORE_MS = 6s` 现在从这里导出）；`AndroidAudioBackend` 新增
    `startPitchBack` / `pitchBackStep` / `endPitchBackAtPromotion`，**由播放器自己的
    `getCurrentPosition()` 驱动**（不用墙钟：vocalIn 本来就是同一文件的毫秒数，用墙钟会把设备启动延迟
    （123–822ms）悄悄从 2s 余量里扣掉）。`PlayerController.blend` 里判定：
    · **能放下 ⇒ 排定还原**：`vocalInBlendMs = blendDurationMs − RETURN_RAMP_MS − entry`（**下界**：
      DJ 编辑的人声在小节线上回来，晚于它；没有编辑时 = 0），`pitchIdentityAtFileMs = entry + vocalIn − 2000`。
    · **放不下 ⇒ 这一对完全不移调**（不是"压缩还原"），并把理由写进 `mix:` 片段
      （真机：`pitch x1.0000 on B (dropped: the incoming deck plays the track's own master, so its vocals
      are in the blend's first sample (0ms of 20000ms), …)`）。
    · **真机两条分支都验到**（这是本轮最硬的一条）：
      `pitch rule — this pair is transposed 1 semitone(s) and the pitch is back at the incoming track's own
      by 17500ms of its file (eased back over 6000ms from 11500ms), 2000ms before its vocals arrive at
      19500ms; the blend is 20000ms` → 后端回读
      `incoming mix applied … platform reports speed x1.0000 pitch x1.0595` →
      **`the incoming track's pitch is its own again — its own clock says 17513ms, the deadline the rule set
      was 17500ms of its file (2000ms of margin before its vocals …)`**（另一条边界 17572ms；**比名义期限晚
      13/72ms = 10Hz 驱动的一拍**，但离人声还有 1987/1928ms，规则要的 2s 余量正是为了吃掉这一拍）→
      晋升时 `the transposition was already its own pitch at the promotion … so this ramp is the tempo's alone`。
      **那对子是 `Life's A Mess(n1460801818) → Moonlight(n1388960663)`（−1 半音）与反向（+1 半音）**。
    · **音色的推荐（任务要求"评估并建议，不要静默决定"）**：**建议速度不要提前还原**（保持晋升后 6s 平滑还原）。
      理由与数字：① 速度锁的**唯一目的**就是"两首还同时听得见的时候，拍子不许散"——提前还原正好在
      **两首都还在响**的窗口里把 incoming 拉回自己的网格；② 本轮的移调之所以能提前，是因为**音高是绝对的**
      （唱出来的音高有绝对参照），而**速度是相对的**：一个快 6% 的人声听起来"和另一首的鼓在一起"，
      不是"被改过"；③ 提前还原会**重新引入漂移**：第 11 轮的真机 `x1.0752`（+7.5%）若在 `vocalIn`
      （≈14.5s）处还原，剩下的 ~5.5s 里两格会滑开 `0.0752 × 5500ms ≈ 414ms ≈ 1.8 拍`——那正是速度锁存在的理由
      （`drift 0ms by construction` 会立刻变成几百毫秒）；④ 代价不对称：音高晚还原 ⇒ 听众听到**一整段变调的人声**
      （不可接受），速度晚还原 ⇒ 听众听到**被拉齐的鼓**（正是想要的）。所以速度维持现状，**规则只对音高生效**。
  - **④ 第一个歌的拍子（D）—— 量化 + 两处改动 + 一条诚实的否定结论**
    · **新工具 `audio/BlendPulse`（纯 Java，6 个单测）+ 设备 harness `D:\qplayer-dev\harness\BlendPulseBench.java`**
      （`build_bp.sh` 出 dex，`app_process`，用**仓库自己的** `BeatAnalysis`/`FadeCurve`/`BlendPulse`；
      网格在**曲末 30s 就地测**并把相位投到 ramp 起点，`period=`/`phase=` 可覆盖）。
      它报告：逐 2s 切片的**低频段电平**（相对本窗口最响切片，dB）与**最深的"坑"**（`lowHoleDb`）、
      **低频段交接的精确时刻**（`outgoingKickEndsAt`，来自 swap 参数）与 **incoming 内容到位的时刻**
      （`incomingEstablishedAt`，**从增益曲线精确算出**，不是测的）、以及**两首同响的窗口**
      （`FadeCurve.bothAudibleMs`）。
    · **⚠️ 否定结论（必须记住，别再走一遍）："这一小段里还有没有拍"这个数，用三种做法在这台库的真实音乐上
      都测不出来。**（a）低频段电平比（on-beat ÷ 本切片均值）：对一首 125BPM 拍点很清楚的歌，
      **把网格相位扫 8 个点，读数全程 0.54–2.15**（真实音乐的低频是"持续贝斯 + 上面的鼓"，电平比不动）；
      （b）onset 包络占比：同一首歌同一次扫描 **0.4–1.8**（密编曲每个细分都有 onset）；
      （c）on-beat ÷ **between-beats**（经典突出度）：分母在"incoming 低频被切掉"时本身趋 0，
      实测**在满音量的混音中段报出 "beatless stretch"**（假阳性）。估计器自己的梳状分能分开（0.5–0.9），
      但要 30s 窗口，**无法定位 15s 里的 2s**。**要多段"拍子没掉"的证据，只能把 outgoing 的鼓组留下来
      （= 剥它的 stems）**——这与用户"让第一个歌的拍子不那么早结束"的诉求是同一件事的两面。
      这段推理**写进了 `BlendPulse` 的类注释**（下一个会话不用重新发明）。
    · **改动 1：`FadeCurve.DJ_BLEND` 的 `OUT_HOLD_EXPONENT` 1.8 → 2.6**。依据是一张**算术表**
      （`harness/ExponentTable.java`，15s 斜坡、incoming 指数 0.55）：
      `p=1.80: out@75 −4.5dB, out@90 −11.4dB, 同响 67%, 峰值 +2.0dB`；
      `p=2.60: −2.7dB / −8.7dB / 72% / +2.3dB`；`p=3.00: −2.1 / −7.7 / 74% / +2.4dB`。
      选 2.6：九成处的留声比原来多 2.7dB（那正是"听不见了"的地方），代价只是中段 +0.3dB；
      3.0 再多买 1dB 但尾巴更陡。**incoming 一侧刻意没动**（把它推晚只会缩短"两首都在"的窗口）。
    · **改动 2：低频交接从"重叠的中间"移到"五分之三"**（`PlayerController.BASS_SWAP_AT = 0.6`，
      取 A 的拍上、仍夹在 `overlap − period` 内）。依据：交接那一刻就是**outgoing 失去底鼓**的时刻
      （`bassSwapNow` 一次把 incoming 低频还回来、把 outgoing 的低频切到设备最低增益），
      而 outgoing 是一路单流，**这是唯一能给它拍子延命的旋钮**。移到 0.6 后：outgoing 的底鼓多留
      1.5s/15s（真机 20s 重叠：10056ms → 12151ms），晋升前 incoming 拥有低频的时间仍有 40%。
    · **真机 before/after（同一对子、同一个 20s 计划，只差 core jar）**：
      | | 同响窗口 | 低频交接 |
      |---|---|---|
      | 旧（1.8 / 中间） | 13292ms of 19913ms（**67%**） | **10056ms**（50.3%） |
      | 新（2.6 / 0.6） | 14352ms of 19933ms（**72%**） | **12151ms**（60.8%） |
      另外两条边界（Life's A Mess→TOSOP、Moonlight→Life's A Mess）都是 72% / 14324–14397ms、交接 12220ms。
    · **一个观测（不是本轮改的，但值得记）**：`align=on` 用的 A 的网格是**文件开头 30s** 测的那一份，
      再用 `MediaPlayer.getDuration()` 外推到曲末；`BlendPulseBench` 一开始照这个做，**同一首歌在
      116s 处的相位就与"就地测最后 30s"给出的差得让读数全成了噪声**（换成就地测量才拿到有意义的数字）。
      这正是 §7 第四轮风险 4"两个时钟不是同一个"的可听版本：**长曲子上的 `align=on` 可能差半拍**。
      下一轮若要做"两首鼓组真的重合"，先量这个投影误差。
    · **顺带的一个新数据**：把设备自己的 18 个 profile 拉下来（`harness/PairScan.java` + `pull_prof.py`，
      编译的是**仓库里的** `BeatProfile`/`MixNaturaliser`），**26 对吗子会被移调**（第 11 轮那 7 个边界里只有
      2 个真的移成），速度锁也有若干对；也就是说 `合拍改调` 现在**经常**会动音高——本轮的真机边界就是
      "−1 半音 / +1 半音"各一次。profile 缓存（`files/cache/beat/`）现在是 VERSION 5、29 字节。
  - **覆盖率（本轮 6 次装机跑）**：
    | 边界 | kind | 计划 → 实际 | 同响 | 交接 | DJ 编辑 |
    |---|---|---|---|---|---|
    | Life's A Mess → Moonlight（用户 20s） | CROSSFADE | 20000 → 19913ms | 67%→72% | 10056→**12151** | 有（渲染+播放） |
    | Moonlight → Life's A Mess（用户 20s） | CROSSFADE | 20000 → 19996ms | 72% | 12220ms | 有（同一对子复用） |
    | Life's A Mess → The Other Side Of Paradise | CROSSFADE | 20000 → 19995ms | 72% | 12151ms | 有 |
    | Life's A Mess → Moonlight（用户 15s，自动规则） | CROSSFADE | **16520**（平淡收尾相对上抬）→ 16490ms | 72% | 10056ms | 有（15s 窗口） |
    | Life's A Mess → Moonlight（剩 1s） | **CUT** | – | – | – | 无（`too late: less than 1500ms left`） |
    | 无模型时的同一条边界 | CROSSFADE | 20000ms | – | – | 无（inert） |
  - **不变量（本轮 6 次跑全部成立）**：**没有一行 `playAt: slot N starts at …`**（没有任何边界从 0 重放）、
    `dumpsys audio` **只有一路** `state:started`、两次晋升后都没有 `not transitioned`（唯一那条出现在起播前的
    暂停态、行号在晋升之前）、`handoff resume ≥ 重叠已放过的量`、晋升行
    `the overlap heard start..start+ramp` 连续。
  - **本轮没做/未验证（重要）**：
    1. **没人听过**（第 5/8/9/10/11 轮都一样，而且这一轮第一次让**渲染出来的文件真的播了出去**）。
       DJ 编辑里"背景-only 20s → 人声在小节线回来"的手感、2.6 的尾巴、延后 2s 的交接、−1/+1 半音
      （真机上真的移了）、20s 的默认长度，**全部只能靠耳朵**。
    2. **小节线的正确性没有听感验证**：`downbeatOffsetSec`（bass 低频最强相位）只在合成信号上单测过；
       "人声正好在小节线上回来"目前只有"它落在 14/13/7 条小节线之一"这一层证据。
    3. **短路分支一次都没走到**（三首 incoming 全部 sings，见 ②）。这也是**设计冗余**的证据：
       `SINGING_FRAME_SHARE=5%` 实际上等于"永远渲染"。
    4. **渲染成本 53–85s**：比第六轮估的"15–35s"高一倍多（多出来的是**整曲 AAC 重编码**：43.8s/52.4s of
       encode）。preload lane 上一条单线程：**队列走得快（或提前量 <2 分钟）时渲染会赶不上**，
       `stillWanted` 会把它丢掉——行为是安全的（回落到今天的流），但用户不会知道"这次没有 DJ 编辑"。
    5. **DJ 编辑与计划长度可能不一致**（本轮实测）：编辑按**渲染那一刻的设置**（15s）做，而边界计划
       （16520ms，平淡收尾上抬）更长 → 人声在小节线（~15.7s）回来，**早于计划终点**；这不是 bug
       （规则的下界法仍然安全），但"移除窗口 = 设置值 ≠ 计划长度"这件事要记住。
    6. **编辑二次编码的代价**：晋升后听到的是 AAC 192kbps 重编码（实测 22–32s 窗口 mix 差 0.1dB），
       编辑比原曲长 47ms（muxer 量化）。
    7. **`pitchBack` 的一拍**：10Hz 驱动，所以"正好在期限上"实际会晚 ≤100ms（本轮 13/72ms）；
       2s 余量就是为它准备的。若以后有人把余量调小，这条会成为第一个响的。
    8. **剥 outgoing 的 stems**（本轮的取舍另一半，下一个切片）：要让**第一首**的鼓组在它身体淡出后
       继续存在，必须给 outgoing 也做一个"文件"——也就是**在播放中途换源**。可行做法是
       **提前把 outgoing 的尾巴也渲染成一个文件**（同一套 DJ 编辑机制，只是加在 outgoing 上），
       并在**一个已知的、非边界的时刻**把当前播放切成那个文件；风险正是 P0 那类换源问题，
       所以要有独立验收（切源后位置连续、只有一个 started 播放器、不重放）。
    9. **`BlendPulse` 只在这台库的 18 首上试过**；低频段的 `LOW_CORNER_HZ=200` / `BASS_CUT_DB=-15`
       是设备 EQ 的近似（设备真实响应是它自己的 5 段）。
  - **交付**：debug APK `98,312,565` 字节；分支 `feat/ai-dj-transition`（commit `72a4aae`，**`main` 未动**）；
    tag `ai-dj-transition-2026-09-20c`；
    页面 `https://github.com/xiaozhuhou233/qplayer-compose/releases/tag/ai-dj-transition-2026-09-20c`、
    直链 `https://github.com/xiaozhuhou233/qplayer-compose/releases/download/ai-dj-transition-2026-09-20c/app-debug.apk`
    （实测 HTTP **200**，98,312,565 字节）。`release.yml` 照旧失败（既有原因）。
  - **测试**：`mvn -pl player-core test` = **175 个用例、1 个失败**，就是那个既有的
    `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（`pageTransitionPreset`
    只有常量没有 spec，与本轮无关）。本轮新增：`BlendPulseTest` 6 个、`DjEditTest` 11 个、
    `StemModelTest` 5 个（后两个是上一轮遗留的；本轮第一次真的编 app 才发现
    `AndroidStemEditRenderer:228` 给 `final` 变量赋值——**core 能过不代表 app 能过**）。
  - **设备卫生**：跑完把 `transitionKind` 还原成 0、`transitionBlendSeconds` 还原成 15、`files/state/queue.json`
    还原成 [Lalala, Moonlight] playIndex=1 positionMs=21325（app 已 force-stop）、
    **模型已删除**（`run-as rm files/models/htdemucs-quarter.onnx`）、`/data/local/tmp/hb/` 里本轮的
    `edit_lam.m4a`（4.9MB）与 `orig_lam.bin`（42MB）已删。留在设备上的新东西：
    `/data/local/tmp/bp/classes.dex`（38KB，`BlendPulseBench` 用）。
  - **下一件事（按顺序）**：① **听**——这一轮第一次有"真的播出去的渲染文件"，先听
    `Moonlight → Life's A Mess`（20s + DJ 式 2.6 + 延后 2s 的交接 + 一个 +1 半音 + 人声在 20.75s 回来），
    再听反向的 `Life's A Mess → Moonlight`（−1 半音）；② 若"背景-only 的 20s"听着太空或太长，先动
    `SINGING_FRAME_SHARE` 与移除窗口的关系（**不要**动 `RETURN_RAMP_MS` / `BEATS_PER_BAR`）；
    ③ 剥 outgoing 的 stems（见未验证 8）；④ 量 `align` 的头部网格投影误差（见 ④ 的观测）。

- **2026-09-20 第十三轮：卡顿的原因（可听的那一声）→ 参数写入从 158 次砍到 1 次；以及"两条歌的素材合成的
  一小段桥"（第一次真的渲染进 DJ 编辑并播放；本轮**所有渲染出来的东西都没人听过**）**
  用户反馈（两件事）：① 在 **I Walk Alone → 3 Strike** 上，**下一首第一句歌词（"don…"）刚唱完的那一刻有卡顿**，
  且"有些歌有有些歌没有"；怀疑是**强制停掉变速/变调**造成的。②「尝试在过渡段中插入两个歌的鼓点和其他能合的
  东西合成的一小段音乐，这样过渡效果可能更好」。
  - **① 这一轮最重要的一件事先说：`I Walk Alone` 与 `3 Strike` 不在设备上的库里**（app 的
    `files/indexes/playlists.json` 648 首、`files/indexes/songs.json` 90 首、仓库里那五份 `queue*.json`
    与 `harness/queue-before.json` 的 18 首、`/sdcard/Music` 93 个 mp3、`harness/lyrics/*.nlrc` 全查过；
    "walk alone" / "strike" 一个都没有）。**所以那一对边界本轮**没有**被复现**，只复现了它的**机制**：
    设备上的 `Life's A Mess → Moonlight`（−1 半音，DJ 编辑在，和用户那一对同样"有移位"）。**要真正回答
    用户的这一声，得先拿到那两首歌**（把歌名/netease id 给作者，或换成库里的对子让用户听同一段）。
  - **② 仪表：每一次 `setPlaybackParams` 都留痕 + 一个音频时钟探针（`AndroidAudioBackend`）**
    · `writeParams(player, who, reason, speed, pitch)` 是**唯一**的写入出口，每个调用点都必须经过它：
    日志形如
    `MediaPlayer: params-write reason=<apply-mix|pitch-return|tempo-restore|…> who=<incoming|audible>
    asked=speed x1.0000 pitch x1.0000 platform=speed x1.0000 pitch x1.0000 took=2ms pos 17456->17458ms
    audible=199594ms`（**回读**、"这次调用本身花了多久"、两个播放器各自的播放头都在里面）。
    `reason` 就是"为什么"：`apply-mix` / `repair-after-start` / `pitch-return` / `tempo-restore` /
    `tempo-restore-early` / 以及 A/B 臂专用的 `*-tick`。
    · **音频时钟探针**（`startClockMonitor`/`clockTick`/`checkClock`）：50 Hz 同时采两个播放器的
    `getCurrentPosition()`，把"播放头没跟着墙钟走 / 走过头"的每一段打成
    `MediaPlayer: audio-clock gap at <uptime>ms uptime: incoming at=15116ms moved=65ms expected=32ms
    over=32ms drift=33ms;`，每秒再打一行 `audio-clock <pos>ms on the incoming deck(<pos>ms on the
    audible one); N gap(s) so far`；边界丢掉/晋升后 8s 自动停。
    ⚠️ **这台设备上这个探针分辨不了"可听的一声"**：`getCurrentPosition()` 的上报粒度约 30ms，
    所以每次采样的漂移是 **±25–52ms 的抖动**，**before 臂 17 个、after 臂 17 个（同样的对子、同样的
    17.9s 斜坡），分布一样** —— 它安静地说明了"这条路器不能证明也没能否证那一声"。
    但它**能**抓到真正严重的故障：另一次 after 臂运行里入歌播放器**从头到尾停在 40ms**（387 个 gap，
    每一个都是 `moved=0`），那是"入歌那半首根本没响"（见下面 ④）。
    · 平台侧的证据也取了：`dumpsys media.audio_flinger` 的 **Local log**（带时间戳的
    `AT::add` / `AT::remove` / `removeTrack_l`）说明**这些参数写入并没有新建/销毁 AudioTrack**
    （19:26:56 建、19:27:16 晋升时销毁，中间没有别的），所以"管线重建"若发生，也不是靠重建 track 发生的。
    A/B 的原始数据在 `D:\qplayer-dev\harness\r13\`（`log-before-ml-lam.txt` / `log-after-ml-lam.txt` /
    `full-*.txt` / `flinger-*.txt` / `log-bridged2.txt`）。
  - **③ 修法（已装机验证：同一条边界 158 → 2 次写入，其中只有 1 次打在"听得到的那一路"上）**
    · **`AndroidAudioBackend` 里三处"每 tick 一次平台调用"全部改成"一次写入"**：
    (a) `restoreTempoNow(speed)` 取代 `startTempoRestore`+60 步 `restoreStep`：**晋升那一刻写一次
    `speed x1 pitch x1`**；而且 `speed == 1.0`（本轮这对子就是）时**一次都不写**，只打一行
    `the promoted track's tempo is its own already (it ran at x1.0000 through the overlap), so there is
    nothing to write back`。
    (b) `pitchBackCheck` 取代 `pitchBackStep`：**变调一直保持到 `pitchIdentityAtFileMs`，然后一次写完**
    （不再有 6s 60 步的滑落；两次写入之间的 17s 里**一次都不写**，这正是"两首还都听得见时保持移调"的
    更好答案）。
    (c) `beginCrossfade` 里**不再无条件重写** mix，改成 `verifyMixOnRollingPlayer()` 只**回读**；
    只有平台报回来的值和应用值不一致时才补写一次（真机上是 `the rolling incoming player still has the
    mix the parked one read back … so no second parameter write at the start of the blend`）。
    · **"在安全的一刻 snap"**：`BeatProfile.beatAtOrBefore(t)`（新增，5 行）—— 变调的落点现在是
    **下一首自己网格上、不晚于规则期限的最后一个拍**（DJ 编辑这段没有 vocals，最响的是鼓，拍点上最藏得住）。
    真机：`pitchIdentityAtFileMs=17409`（期限 17500，早了 91ms），`params-write reason=pitch-return
    … pos 17456->17458ms`，**比名义期限晚 8ms**，离人声还有 2091ms（规则要的 2s 余量仍在）。
    · **不变的规则**："音高必须在 `vocalIn − 2000ms` 之前回到原位"照旧成立**而且更结实**：
    `vocalInBlendMs` 现在优先用**渲染时写进文件名**的真实小节线（见 ⑤），拿不到才退回原来的下界。
    · ⚠️ **A/B 臂是这一轮的测量工具**：`files/legacy-ease-backs` 存在时走**第十二轮**的每 tick 滑落
    （`legacyRestoreStep`/`legacyPitchBackStep`），不存在时（**普通安装的默认**）走一次写入。
    每次进程都会打 `MediaPlayer: ease-back arm = LEGACY (10Hz glide) / single write (…)`，所以日志自己
    说得出是哪一臂。**诚实的一条**：我复刻的 legacy 节奏滑落**混用了 `nanoTime()` 与
    `elapsedRealtime()`**，所以它只写了 1 步就结束了；**158 次这个数字里，156 次是变调滑落（复刻正确），
    节奏滑落本该还有 ~60 次**（第十二轮真实代码的总量约 218）。fix 之后是 **2** 次（其中 `apply-mix` 打在
    **停着的**播放器上，听不到）。
  - **④ 顺手抓到的、比"卡顿"更严重的**：**停着的入歌播放器偶尔根本起不来**
    `MediaClip` 那套"apply 把它启起来 → `parkIncoming` pause + 再 seek → 后来 `start()`"留下了
    `W/NuPlayerRenderer: onDrainAudioQueue(): audio sink is not ready`，之后整段 overlap 播放头停在 40ms
    （before 臂 17:53 那次没有；18:54 那次有）。**这不是本轮改动引入的**（apply/park 顺序两轮一样），
    但它是真机上"第二首没响/半首歌没了"的机制。**下一步应该做的就是这个**：
    把 mix 的写入从 prepare 挪到 `beginCrossfade`（`setPlaybackParams` 本来就会启起 player，
    所以正好和 `start()` 合成一步，**park 的 pause+再 seek 整个消失**），代价是控制器要改成
    **在 `beginCrossfade` 之后**读回 mix（现在已经知道该在哪儿读）。本轮**没做**（一个变量一个变量地改）。
  - **⑤ 桥（Task 2）：把前一首的低音**搬进**后一首的文件里，只在渲染层做**
    · 新纯 Java 类 **`audio/StemBridge`**（11 个单测 `StemBridgeTest`，全绿）：`plan()` 决定位置、
    `layer()` 造素材、`measure()` 出验收数字。**素材只有前一首的 `bass` stem**，位置是**后一首自己小节的
    **第一**条 ≥ `0.6 × 移除窗口` 的小节线**（`BARS=2` 条，`0.6` 与 `PlayerController.BASS_SWAP_AT` 同一个数），
    长度 = 2 × 后一首的小节；素材取自**前一首尾窗最后 3 小节**（同一套 `downbeatOffsetSec` + `barLines`），
    并且**按 `speed` 拉伸**（因为整份文件被 `speed` 播放，不拉伸就会以错的节拍继续）。
    **为什么不搬鼓**（这条要记住）：按每播放器 EQ 只能切 <200Hz 那一档，鼓的军鼓/踩镲会**和还在响的
    out 流叠加**（实测那一档的 out 增益在 72% 处还有 −2.7dB、90% 处 −8.7dB）= 相干叠加；`other`（旋律）
    同理且无法门控。所以只有低音被搬，而且**低频交接被移到桥的起点**（`bassSwapAtMs` 由控制器用
    渲染写进文件名的 `bridgeStartMs` 反算）——**交接即桥的起点，所以同一个带里永远只有一份**。
    · **数字怎么从渲染传到边界**：渲染自己命名文件（`StemEditRenderer.Result` / `Result.suffixOf`），
    名字形如 `<hash>-b<桥起点ms>-v<人声回来ms>.m4a`（`DiskCache.djEditBaseName/djEditDir/djEditNames`）；
    边界 `PlayerController.djEditFor` 先按**每对子**的 key 找（有桥的），找不到再退回第十二轮的
    普通编辑名。**普通名字一个字节都没变**，所以第十二轮渲染好的编辑仍然命中。
    · **渲染成本**：头窗 26.3s + **前一首尾窗（7703ms）12.1s** + 整曲 AAC 编码 ≈ **82s**（第十二轮 53–85s）。
    ⚠️ 真机上 55s 的提前量**不够**（`DJ edit for Moonlight cancelled while writing the body (the queue
    moved on)`）→ 回落成"这次没有编辑"（安全，但用户不会知道）。**真实会话里提前量是整首歌**，够；
    但要把这句话写进下一轮的验收假设里。
    · **验收：渲染出来的桥没能播出去，因为它没通过自己的测量**（这是本轮 Task 2 的核心结论，不是失败）
      实测（`D:\qplayer-dev\harness\r13\log-bridged2.txt`）：
      `bridge measured: carried −76.1 dBFS, its source −76.1 dBFS (the incoming's own head −17.3 dBFS
      under it); vocals: incoming −240.0, outgoing 0.2 dBFS; one copy (carries 1.000 of its source,
      peaking at lag 0); no doubling (the sum is +0.0 dB above the louder contribution at worst);
      pitch artefact +0.00 semitones; pulse: 1 of 7 beats carry a low-end attack, longest gap 2818ms
      of a 470ms period -- NOT ACCEPTABLE: the carried layer is silent; the pulse has a 2817.8ms hole
      with a 469.6ms period`
      → 于是 `W/… the bridge did not pass its own measurement, so it is NOT in this edit — the boundary
      plays the round-12 edit`。**原因**：Life's A Mess 最后两小节（分离出来的 bass 轨）本来就是
      −76dBFS 的收尾，搬过来就是**两小节近乎静音**。所以渲染器现在**先测量、不过就丢掉那一层**，
      这正是"渲染产物要过自己的验收，否则退回今天的行为"的落地，也让"材料本身没有低频"这类歌
      自动走回老路。
    · **桥的正例证明在单测里**（已知素材）：carried 与 source 同电平（<3dB）、拉过速度后同一段里
      鼓点数一致、`alignment=1.000 / lag 0`（**一份拷贝、同相**）、`doubling ≤ 3.5dB`（同素材叠自己会
      报 +5dB 以上 → 抓得住）、incoming 人声在桥里被抓住、**outgoing 人声用"对齐度"抓而不是"电平"抓**
      （前一首的人声本来就在那几小节里，电平门会误报——这是本轮修掉的一个真误报）、把中间一小节挖空
      会报 `pulse has a 2818ms hole`。
    · ⚠️ **测不出来的**：**周期性的第二份拷贝在信号域不可判定**（延迟正好等于素材自己一拍的拷贝，
      和素材的周期性是一回事——`StemBridge.alignmentAtZeroLag` 的注释里写明了）。设计上不依赖这个测量：
      out 的低频在桥的起点被 EQ 切掉，所以"房间里只有一份"是**构造出来的**，设备侧的 EQ 日志是那条证据。
      另外**没人听过任何一段桥**。
  - **真机覆盖（本轮 3 次跑，Redmi K20 Pro `efaa83b2`，`transitionKind=0` 自动、curve=1、
    `transitionBlendSeconds=20`（跑完已还原 15）、模型临时推上去（跑完已删））**：

    | 运行 | 对子 | 臂 | params-write | 时钟 gap | DJ 编辑 | 结果 |
    |---|---|---|---|---|---|---|
    | before-ml-lam 18:50 | Life's A Mess → Moonlight（−1 半音，swap 12151ms） | LEGACY（每 tick 滑落） | **158**（156 变调 tick + 1 节奏 tick + 1 apply） | 17 | 有（普通） | 连续晋升 19960ms |
    | after-ml-lam 18:53 | 同一对子 | single write | **2**（apply + `pitch-return-at-promotion`） | **387（全是 `moved=0`）** | 有（普通） | **入歌仓停在 40ms**：`audio sink is not ready`，晋升后无声（见 ④） |
    | bridged2 19:26 | 同一对子 | single write | **2**（apply + `pitch-return`，落点 17458ms） | 17 | **桥渲染了、被自己的测量否掉** | 桥起 13.169–16.926s、拉伸 x1、素材取自尾窗 3.496s；**没通过**；渲染没赶上 55s 提前量 → 本次无编辑 |
  - **不变量（三次跑都成立）**：没有任何一行 `playAt: slot N starts at …`（没有从 0 重放）；
    `dumpsys media.audio_flinger` 全程只有一路 active track；晋升行 `the overlap heard start..start+ramp`
    连续；`handoff resume ≥ 重叠已放过的量`。
  - **本轮没做/未验证（重要）**：
    1. **用户的 `I Walk Alone → 3 Strike` 没被复现**（歌不在库里，见 ①）。**卡顿的根因没有被证明**：
       我能证明的是"旧代码在这条路上写 158 次（真实第十二轮约 218）参数、每次都是一个平台调用"，
       以及"新代码写 1 次（听得到的那一路）"；**没能证明那一声就是这些调用造成的**（探针分辨不了），
       也**没有排除**另外两个候选：**(a) 停着的入歌播放器起不来**（④，真机抓到过一次，机制清楚）、
       **(b) 渲染引擎的 CPU 占用**（一个 DJ 编辑渲染是 82s×4 线程，本轮 before 臂那次渲染正好和边界重叠）。
    2. **桥一次都没有被听到**，而且**真机上唯一渲染出来的那一次被自己的测量否掉**：正例只有单测与合成
       素材。**位置/素材/门控的听感、拉伸 ±1.3 半音的代价、两小节是不是太长/太短，全部没人听过。**
    3. 桥的**渲染成本 82s**：55s 提前量不够（本轮实测被 `stillWanted` 丢掉）。真实会话的提前量是一整首
       歌（够），但**队列走得快时桥不会落地**，用户也不会知道。
    4. `legacy` A/B 臂只用于测量（默认不存在），且它的节奏滑落有上面那个时钟 bug —— **不要**用它的
       节奏写入数量当第十二轮的真值。
    5. 时钟探针的 `CLOCK_GAP_MS=25` 对这台设备**太紧**（±25–52ms 是上报粒度）；下一轮应该把它抬到
       ~80ms 并保留 `moved=0` 的冻结检测（本轮 387 个 gap 那次才是**真的**信号）。
    6. `BASS_SWAP_AT`（0.6）与渲染时的 `0.6 × removalMs` 之间有 **≤1 拍的不一致**
       （计划重叠可能 ≠ 设置长度，第十二轮记过这件事）：桥存在时**低频交接就是桥的起点**，
       所以不一致表现为"最多一拍的低频多一份或空一拍"，**没有测量**。
    7. 视频层/`BiliClient`/收藏夹/自愈/`resolveIncomingSource` 的 unblock 回落/拍探针预热 —— 本轮**一行没碰**。
  - **交付**：debug APK `98,323,453` 字节；分支 `feat/ai-dj-transition`（**`main` 未动**）；
    tag `ai-dj-transition-2026-09-20d`；页面/直链见下（实测 HTTP 200）。
  - **测试**：`mvn -pl player-core test` = **186 个用例、1 个失败**，就是那个既有的
    `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（`pageTransitionPreset`
    只有常量没有 spec，与本轮无关）。本轮新增 **`StemBridgeTest` 11 个**（全绿，含"延迟拷贝/人声/
    静音/挖空"四类否定用例）。
  - **设备卫生（跑完已做）**：`transitionBlendSeconds` 20→**15**、`transitionKind` 0、
    `files/models/` **删除**、`files/legacy-ease-backs` **删除**、`files/cache/djedit/` 清空、
    `files/state/queue.json` 还原成 `[Lalala, Moonlight]` playIndex=1 positionMs=21325。
  - **下一件事（按顺序）**：① **把那两首歌给作者**（或让用户听库里的同一段）—— 否则卡顿这件事永远只能
    靠"机制"解释；② **把 mix 的写入挪到 `beginCrossfade`**（④，park 的 start/pause/再 seek 整个消失）；
    ③ **拿一首结尾还有低频的歌再跑一次桥**（库里 `90210 → Pray 4 Love` 之类）—— 本轮唯一渲染出来的
    那一次被测量否掉，等于桥的正例在真机上还没出现；④ 把探针阈值抬到 80ms + 保留冻结检测。

**顺序**：P1 → P3 → P5 → P4 → P6。不要先做 P6。（P1/P2/P3/P7、**P6** 与 **P4 的分析+对齐**都已落地；
剩下的仍是**听觉验证**（P4 现在有装机证据了：能 align=on，但**听感**仍未验证），然后才是 P5 低频互换
与 P4 的下一步"变速对拍"（`setPlaybackParams` 把 B 拉到 A 的 BPM —— 本轮只对齐了网格，没有拉速度，
所以两首速度差得多时对齐会被兼容门直接跳过）。）
**第五轮之后的第一件事（有数据支撑）—— ✅ 第八轮已做（见第七节末尾）**：⑤ 里那张 18 首的表说明
**瓶颈是周期选择，不是置信度门** —— 测得 72.7 的歌真值 109、测得 97.2 的真值 145（都是 2:3 错位），
而门只能在"拒掉错的/放行对的"之间选。第八轮的结论与当时的设想**不完全一样，记在这里免得重走**：
`BeatAnalysis` 里加的是**"选出的周期 vs 它自己的更细和声亲属（2/3、1/2、1/3）"**的检查（不是"窗口 vs
各段自己选的周期"）：**赢的判据是攻击能量，不是相关**（错格的相关确实更高，见第八轮）；而且
**只改周期会把通过率从 12/18 拉到 10/18** —— 门里"段内 support"那一半会犯**同样的 2:3 错**，
所以它也得跟着和声感知（`segmentSupport`）；两件事一起做才回到 12/18，且**错的网格从 1 首过门变成 0 首**。
**下一件事（有数据支撑）—— ✅ 第九轮已做（见第七节末尾）**：调性估计器的强度阈值。**结论与设想不一样，
记在这里免得重走**：问题**不在阈值**，而在**两处测量**——(a) chroma 折叠的是 `log1p(幅度)` 再逐 bin 相加，
在五个八度上把每个音级都堆成常数（36 首真实窗口每 bin 0.066–0.097，均匀是 0.083；22 首被报成 A minor）；
(b) 强度是"与亚军的余量"，而真实音乐里亚军几乎总是关系大小调或五度（24/36），**那正是本来就要混的**。
所以：**改折叠方式（用幅度）+ 把余量改成"与不可能混的调比" + 加第二把门（三和弦占比）**；
`MIN_STRENGTH = 0.25` 这个数字**没有动**。过门率 8/36 → **21/36**，并且**第一次让两首不同的歌在真机上
跑完了变速+移调+低频互换**（`Moonlight → I'm Waiting`，speed x1.0637 / pitch x1.0595 / bass swap，
平台回读一致）。**下一步仍然是听感**（第五/八/九轮都没让任何人听过），而且这一轮同时动了速度/音高/低频，
所以它比前两轮更该先被听。之后才是**接线预渲染混音**（第七轮 Phase C 的文件仍然没有任何代码读它）。
⚠️ **这一整段的"变速/移调"在第十轮被整体下线了**（用户要求先不管调速变调，见第七节末尾的第十轮）；
上面关于调性门/强度的推理仍然是**当时的记录**，但那些门与 `MixMatch` 已经不在仓库里，键盘上的
下一步不再是"重校调性阈值"。

- **2026-09-20 第十四轮：降调回退从"一次 snap"变成一段听得见的调制（Task A 调性融合）；设置页 AI 说明压缩（Task B）**
  用户要求：电子乐里"前一首的尾调要**明显地**融合过渡到后一首的新调子"，这一段可以**拉长**（借助手段），而且
  **后一首第一句人声/歌词必须在这段之后**才开始。
  - **① 关键结论（推导，不是偏好）：一段"滑过去"的调制只能两边一起动。**
    设后一首（K_b）被移调 d 个半音后落在前一首的调（K_a = K_b + d）。要在任意时刻两首都在**同一个调**里
    （这正是当初要移调的目的），则两边的移调量 a(t)（前一首 deck）、b(t)（后一首 deck）必须满足
    b(t) − a(t) = d。开始 (a,b)=(0,d)；结束时后一首必须回到原调 b=0（人声规则），于是 **a=−d**：
    前一首 deck 也整整走了一个调。所以三个选项里——**两边一起动**（每个都走 |d| 个半音、同向，
    调性中心从 K_a 滑到 K_b，这就是 EDM 那个 riser 进新调的效果）／**都不动**（a≡0 强迫 b≡d，等于今天的
    snap）／**只动后一首**（前一首不动 → 后一首自己滑走 → 后半段两首**故意错开一个调**还在同时响，
    比今天的 snap 更差，因为今天至少整段同调）。**中间那个"只动后一首"不是第三种形状，是最差的一种。**
  - **② `KeyGlide`（新增，纯 Java，10 个单测）**：一段调制 = 从后一首 deck 的入口到"规则期限前最后一个拍"
    （round 13 的 `pitchIdentityAtFileMs`，同一个数），按**后一首自己的拍网格**分成 k 步（尽量取整小节），
    每步在**两块 deck 上各写一次**：后一首 `d·(n−1−i)/n → 0`，前一首 `0 → −d`。
    **上限 `MAX_STEPS = 6`（每块 deck ≤6 次写）**，<2 步就不滑（退回 round 13 的一次 snap，并在日志里说明为什么）。
    首次实现的两个 bug 被自己的单测抓住并已修：`%f` 格式喂 int 抛异常；把**间距**取整而不是用网格自己的
    整数周期（`round(periodMs)`），会让阶梯在半小节内漂移。
  - **③ 把"挤占调制段"的东西删掉（这一轮真正的"拉长"）**：
    · `MixNaturaliser.pitchFitsBeforeVocals` 一直要求 `vocalIn − 2000 − RESTORE_MS(6000) ≥ 0`——那是 round 12
      **6 秒滑落**时代的条件，round 13 把回退改成一次写之后这个门就**过时了**：人声回来前不足 8 秒的对子
      被**静默拒绝移调**，而能移调的对子其调制段也被压在 `overlap − 2.5s − 6s`。现在这个门只要求
      "规则的期限落在这次 blend 里"，滑不滑得动由 `KeyGlide` 自己判断。**更多对子会拿到移调**（行为变化，
      记录在案）。
    · DJ 编辑的**移除窗口现在算上"这块 deck 跳过的头部静音"**（`djEditRemovalMs = 过渡时长 + min(内容起点, 3s)`）：
      窗口是**文件**的区间，而 deck 是从内容起点进场的，所以 3 秒静音开头会让"人声静音窗口"比用户设的
      时长短 3 秒。本对子头部只有 40ms，所以这一轮只长了 40ms（真机日志有这一行）；长静音开头的歌最多 +3s
      （=分离窗口按比例变长，见渲染成本）。
    · **人声回来看渲染写进文件名的真实小节线**（round 13 已有），所以"调制结束后才开始唱"是可测量的：
      本轮真机 `vocalReturnEnd=21152ms`，第一句人声 = 20652ms，调制末步 18370ms → **人声在调制结束 2282ms 之后**。
  - **④ 真机（Redmi K20 Pro `efaa83b2`，`transitionKind=0`、curve=1、`transitionBlendSeconds=20`（跑完还原 15）、
    模型临时推上去）**：对子 **Life's A Mess → Moonlight（−1 半音）**，两次都成功（第一次被一次 seek 打断，
    见 ⑥）。
    | 项 | 值 |
    |---|---|
    | 调制段 | **18309ms**（= 用户设的 20000ms 的 **91.5%**；剩下的 1651ms 是规则 2s 余量 + 拍点吸附让出的部分）|
    | 台阶 | **5 步 / 每块 deck**，每 **8 拍**（3760ms，后一首 127.8BPM）——阶梯全在拍网格上 |
    | 平台回读曲线（后一首/前一首，半音）| step1 3340ms(−0.80/+0.20) step2 7161(−0.60/+0.40) step3 10842(−0.40/+0.60) step4 14655(−0.20/+0.80) step5 18370(**0.00**/+1.00) |
    | 回读一致性 | 每一步 `asked` 与 `platform` **完全相同**（含**活着的**那路 deck，pitch-only），`took=2–4ms` |
    | 落点误差 | +31/+92/+13/+66/+21 ms（≤ 一个 100ms 轮询 tick）|
    | 规则余量（**测出来的**）| 最后一步落在 18370ms（期限 18349ms，晚 21ms）→ 移调在人声回来前**至少 1979ms** 关闭（规则要 2000ms；拍点吸附通常还多给）|
    | 整个边界 `params-write` 总数 | **12**（1 次 apply-mix 在停着的 deck 上 + 10 次 key-glide（5/5）+ 1 次 pitch-return-at-promotion）——round 13 是 2，round 12 约 218 |
    | 时钟探针 | **0 gap / 0 frozen**（两块 deck 都 0；`audio-clock monitor off ... 0 gap(s) ... no frozen samples`），覆盖整段 20s blend + 晋升后 8s 尾巴 |
    | 不变量 | 无 `playAt: slot … starts at`（0 行）；`the overlap heard 20021ms = its start 40ms + the 19981ms ramp`；`handoff resume=20021ms`；晋升后 `tempo is its own already … nothing to write back` |
  - **⑤ 安全网（必须有，而且本轮真机验证到了）**：前一首 deck 被弯了音高之后，**任何"丢掉这次 overlap 但不晋升"的路径都必须把它还原**，
    否则用户一直听的那首歌会**整首走调**——比这次过渡失败严重得多。`restoreOutgoingPitch()` 挂在 `releaseIncoming()`
    的最前面（所有 abandon 路径都经过它）；第一次 run 正好被一次 `seek to 174252ms` 打断（seek 会中止过渡），
    日志是 `the overlap was dropped before the key blend finished — the audible deck's own pitch is back (one write)`
    且 `platform=x1.0000` 回读一致——**这条路径是真机验证过的，不是纸面设计**。（那次 seek 的来源没查清，
    不在本轮改动范围内；它在 round 13 的代码里同样会中止过渡。）
  - **⑥ 探针的第二处修正（round 13 遗留的建议 + 本轮踩的坑）**：`CLOCK_GAP_MS` 25 → **80ms**（设备
    `getCurrentPosition()` 的粒度约 30ms），并且**"冻结"必须重新定义**：第一版把 `moved == 0` 当冻结，
    结果第一次 run 的 36 条 gap **全是** `moved` 在 −31…+5ms 之间（正常播放的量化噪声）被标成 FROZEN，
    等于把阈值又降回去且名字骗人。现在冻结 = **位置连续 250ms 完全不变**（每个 episode 只报一行），
    round 13 那次"整段停在 40ms"会抓到，量化噪声不会。改完的第二次 run 就是上面那行 **0 gap / 0 frozen**。
  - **⑦ 渲染成本**：入口 allowance 让窗口变成 `过渡时长 + 入口`（本对子 +40ms，可忽略）；真机这轮两次 DJ 编辑
    渲染都是在 120s 提前量内跑完的（日志 `渲染…` 行，本轮没有量到 82s 以上的增长）。**长静音开头的歌最多 +3s
    分离窗口**（≈ +3s 渲染），仍然没有解决 round 13 记的"整曲 AAC 重编码 ~44s"那块——**建议的成本修法**
    （下一轮）：body 走 `MediaExtractor`+`MediaMuxer` 直通复用（不重编码），能把 82s 砍到 ~40s。**本轮没做。**
  - **⑧ 未验证 / 风险（重要，按 round 13 的惯例直说）**：
    1. **没有人听过任何一段**：阶梯是"每 8 拍跳一次、每次 ≤0.2 半音"（|d|=2 时 ≤0.4），听感是"调制"还是"阶梯"没人知道；
       台阶间距、`MAX_STEPS`、是否该在开头 hold 一段（现在是立刻开始滑）都是可调但**未听**的。
    2. **两边一起动 ⇒ 前一首的尾部人声也被移调**（最多 ±2 半音，跨很多秒）。结构上的缓解是"弯得最大时它最轻"
       （阶梯单调 + DJ 曲线本来就在把前一首压下去），每一步的日志里都带**前一首 deck 当时的自身增益**——
       但**这个缓解没有任何听感验证**。
    3. **平台接受 pitch-only 写在活着的 deck 上**这一点本轮证明了（回读一致、`took=2–4ms`、探针 0 gap），
       但**只在一台设备、一个 pair 上证明过**；换设备/换音频 HAL 需要重测。
    4. `KeyGlide` 用的是**拍网格相位**（round 13 单步的同一个选择），不是渲染测出的**小节线**（`StemGesture.barLines`
       那条"相位不是小节"的教训）：所以台阶落在拍上、**不保证落在小节线上**（8 拍一档时小节感通常还是对的）。
    5. 调制段长度 = `vocalInFileMs − 2000 − entry`，而 `vocalInFileMs` 来自渲染写进文件名的**真实小节线**——
       所以"用户把滑块拉到 30s"时它会跟着变长，但**本轮只测了 20s 一档**，30s 档未测。
    6. Gate 放宽后**更多对子会拿到移调**（以前不足 8 秒窗口会被拒）——这是行为变化，只在新日志里可见，未听。
    7. 视频层/`BiliClient`/收藏夹/基础播放路径/其它过渡 kind（CUT/QUICK_FADE/FADE_OUT_IN/SILENCE_TRIM）本轮**一行没碰**。
  - **⑨ Task B（设置页 AI 说明压缩，单一来源 `SettingsCatalog`，Compose 与 QML 都吃它）**：8 行 desc 改短
    （例：`联网搜索不可用时使用 AI 内置知识库继续生成，不因搜索失败直接拒绝` → `联网搜索失败时改用模型知识库继续生成`；
    `在 AI 对话框中显示模型原始输出，即使解析歌曲失败也可查看` → `显示模型原始输出，便于排查解析失败`）。
    行 key/默认值/滑块语义一概未动。
  - **⑩ 交付**：debug APK（本轮 ~98.3MB）；分支 `feat/ai-dj-transition`（**`main` 未动**）；tag/release 见本节末尾。
  - **测试**：`mvn -pl player-core test` = **196 个用例、1 个失败**，仍是既有的
    `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（与本轮无关）。新增
    **`KeyGlideTest` 10 个**（全绿：阶梯在网格上、两边差恒等于调距、末步归零、|d| 的行程、窗口太短/网格不可信/
    不移调 → `none` + 原因、`pitchFitsBeforeVocals` 不再要求 6 秒）。
  - **设备卫生（跑完已做）**：`transitionBlendSeconds` 20→**15**、`transitionKind` 0、
    `files/models/` 删除、`files/cache/djedit/` 清空、`files/state/queue.json` 还原成库里的
    `[Lalysa? 见 queue-before 的第 8/12 项]`……⚠️ 具体见下面 ⑪ 的还原清单。
  - **⑪ 下一件事（按顺序）**：① **听**（这一轮比 round 13 更该听：新东西是一个**听得见的效果**，不是一次修正；
    先听 `Life's A Mess → Moonlight`，日志里那 5 步的曲线就是它）；② 把 body 的直通复用做掉（渲染 82s → ~40s，
    否则队列走得快时编辑永远赶不上）；③ 台阶间距/是否开头 hold/`MAX_STEPS` 全按听感调；
    ④ round 13 ④ 那条"停着的入歌播放器起不来"仍未修（本轮第一次 run 的 9 个"停住"样本是**探针误报**，
    但 round 13 那次是真的）。

- **2026-09-20 第十五轮：过渡/AI 设置文案压缩、B站内联预览的圆角（平台 outline）、启动掉帧的三处结构性改动
  —— ⚠️ 本轮**没有设备**（`adb devices` 全程为空，USB 树里也没有 Android 设备，LAN 扫 5555 无应答），
  所以 Task B 的截图与 Task C 的帧数据都**没有拿到**，下面逐条写明"已证明/未证明"。**
  用户三条要求：①「简化 ai 过渡那些文案」②「播放 b 站视频的预览直角边切成圆角」③「修复刚开应用所有东西都掉帧的问题」。
  - **① Task A（文案，唯一真值源 `SettingsCatalog`，Compose 与 QML 都吃它）**：只改 `desc` 文本，
    **key/类型/默认值/滑条语义一个都没动**（`SettingsCatalogTest` 只断言类型与默认值，没有 desc 断言）。
    | 行 | before | after |
    |---|---|---|
    | 智能过渡 | 切歌时按歌曲信息选择合适的过渡方式；无法完成时自动回退为硬切。已在「AI 音乐助手」里配置服务商时，由 AI 依据歌曲信息挑选（含重叠长度），同样只是建议：无网络/超时/回答无法解析时用本地规则；AI 听不到音频。过渡会做节拍对齐、低频互换，并按两首测得的拍速/调性做小幅变速与升降调（只动下一首、幅度很小、晋升后 6 秒内还原）；测不到就完全不做，任何情况下都不会因此取消过渡 | 切歌时自动选择过渡方式；已配置 AI 时由 AI 挑选（它听不到音频，只是建议，报错或用不上就用本地规则）。无法完成时回退为硬切。 |
    | 过渡方式 | 自动：本地规则按时长与静音测量挑选（AI 已配置时由 AI 挑选）；其余为强制使用某一种。普通的可流式歌曲之间默认是约 15 秒的交叉淡化 | 自动：按歌曲信息挑选（已配置 AI 时由 AI 挑选）；其余为强制使用某一种。 |
    | 淡化曲线 | DJ 式：下一首早早低声铺进来，当前这首压住到结尾才用一小段退出去，整段里大部分时间两首都听得到（像串烧）；等功率/线性是对称的，只有中点附近两首差不多响，听感更像淡入淡出。AI 或本地规则选了 8 秒以上的重叠时自动用 DJ 式 | DJ 式：大部分时间两首都听得到，像串烧；等功率/线性是对称的，更像淡入淡出。8 秒以上的重叠自动用 DJ 式。 |
    | 节拍对齐 | 交叉/快速淡化时把重叠长度对到整拍，并让下一首从自己的拍点进入，两首的节拍才能真正对上（需要两侧都测得可信的节拍，测不到时自动不参与，等于没有这个功能；拍速差得多时会先把下一首小幅变速对上，再对齐） | 把重叠对到整拍、让下一首从拍点进入，两首鼓点才对得上；测不到可信的节拍就不参与，过渡照常。 |
    | 低频互换 | 交叉/快速淡化时，在拍点上把低频从当前这首交给下一首（系统均衡器），避免两条低频线打架。设备不支持音频效果器时自动不参与，只少了低频互换，过渡照常 | 在拍点上把低频交给下一首，避免两条低频线打架；设备不支持时不参与，过渡照常。 |
    | 过渡时长 | 两首普通歌曲交叉淡化多久。太短听着像淡入淡出（这也是默认改成 15 秒的原因），太长则两首不搭的歌会同时很响；4–30 秒，默认 15 秒。当前这首的收尾实测很平淡（没有人声、没有起音、也没有还在往上爬）时，会在用户选的时长上最多再提前 5 秒开始融合；「过渡方式」强制成某一种时，这里只影响交叉淡化/快速淡化 | 两首交叉淡化多久，4–30 秒。太短像淡入淡出，太长则两首不搭的歌会同时很响。当前这首收尾很平淡时最多提前 5 秒开始融合。 |
    （`过渡时长` 那句里的 5 仍然来自 `TransitionPlan.PLAIN_EXTENSION_MS / 1000L`，所以单一真值源没破。）
    全仓库只在 `SettingsCatalog` 里有面向用户的过渡文案：扫过 `*.kt/*.qml/*.java` 里所有「过渡/淡入淡出/AI 挑选/节拍对齐」
    命中，其余都在注释、日志或 `AiTransitionChooser` 的**模型提示词**里（后者不是给用户看的，未动）。
  - **② Task B（B站内联预览圆角）—— 改了，但**没有截图**（无设备）**
    - **原来为什么是直角**：画面由根节点 `VideoLayer` 里的 `BiliVideoSurface`（真 `SurfaceView`）绘制，
      而内联槽位 `VideoSlotReporter` 上那个 `.clip(ShapeExtraLarge)` 只是画在**上报盒子的空节点**上
      （它一个字都不画）—— 播放画面不在那个节点里。Compose 的 clip 对 SurfaceView 天然无效：
      画面根本不是这个窗口画的。
    - **平台机制（本轮从 AOSP 源码确认，不是猜）**：默认 Z 序的 `SurfaceView` 是**在窗口下面**合成的，
      靠"在窗口里挖洞"显示出来 —— `SurfaceView.clearSurfaceViewPort()` 最终是
      `SkiaCanvas::punchHole() = mCanvas->drawRRect(rect, kDstOut)`（`libs/hwui/SkiaCanvas.cpp`），
      也就是一个**普通绘制操作**，因此**受 canvas 的 clip 约束**。所以把承载这个洞的 view 用圆角 outline
      clip 起来，洞就变圆角，而**四个角保留 App 自己画的像素**（详情页根节点是
      `Box(fillMaxSize().background(colorScheme.surfaceContainer))`，不透明）→ 看上去就是和周围 UI 一致的圆角。
      （`SurfaceView.setCornerRadius()` 是 `@hide`、且不在任何 greylist 上 → 反射在 targetSdk 35 上会被挡，不能用。）
    - **改动（`ComposeQPlayerActivity.kt`，只有一个视频节点）**：`BiliVideoSurface(modifier, cornerRadiusPx)`
      的 AndroidView 根从裸 `SurfaceView` 换成一个 `FrameLayout` 容器，**容器和 SurfaceView 两个都**挂
      `RoundRectOutlineProvider`（圆角半径）并 `clipToOutline = radius > 0`（`applyVideoCornerRadius`）；
      内联传 `SHAPE_EXTRA_LARGE_DP = 32f`（= 原来的 `ShapeExtraLarge`，注释里写死绑定关系），
      **全屏传 0 = 不 clip（edge-to-edge 保持）**。outline 由 view 的宽高现算，所以盒子缩放时圆角跟着走。
      `ApkDetailCover` 里那条（当前不可达的）bili 分支也传同一个半径。
  - **③ Task C（启动掉帧）—— 代码层面的三处改动 + 埋点；**帧数据没有拿到**（无设备，见上）**
    - **这一轮的诚实边界**：任务要求"先量再改"，但设备全程不在线，**我没有测到任何一帧**。
      所以下面写的是"**从代码里能确证在启动路径上的重活**"以及把它们移出启动窗口的改动，
      **不是**"测出来就是它"。下一轮的第一件事就是跑 `D:\qplayer-dev\harness\r15\measure.sh`（见下）把 before/after 补齐。
    - **确证在启动路径上（主线程）的两处 I/O**（这两条不需要设备就能看出来）：
      1. `SettingsCore` 加载时 `pushToController()` → `PlayerController.setCacheMaxSizeMB` →
         `DiskCache.setMaxSizeMB` 里**同步** `evictIfNeeded()`：`totalSize()` 会**遍历九个子目录**（每个缓存文件一次
         `listFiles`+`length`，用过一阵的设备上几百个），超预算时还要**排序所有文件并逐个删除**；
         紧接着 `refreshCacheSize()` 再走一遍。全部发生在 `onCreate` 里、首帧之前。
      2. `preloadAdjacent()` 末尾的 `precacheNextAudio(next)` 在主线程上调 `diskCache.totalSize()`（同一个走查）。
    - **改动**：
      1. `DiskCache.setMaxSizeMB` **只存预算**，新增 `evictIfOverBudget()` 由调用方决定线程；
         `PlayerController.setCacheMaxSizeMB/refreshCacheSize` 把"淘汰 + 走查 + 发布 `cacheSizeMB`"整体丢给 `worker`
         （两个调用者都不等这个数：设置页那一行读的是 state）。
      2. `precacheNextAudio` 的**预算检查**移到 `qplayer-precache` lane 里（generation 检查之后）——
         那里的调用者就是主线程。
      3. **启动闸门**（`PlayerController` 新增，`STARTUP_QUIET_MS = 3s` / `STARTUP_FALLBACK_MS = 15s`）：
         `runAfterStartup(Runnable)` 在闸门没开时把**提交动作**（不是工作本身）排队，开闸后按顺序放行。
         `notifyUiInteractive()` 由宿主在"第一帧之后"调用（Android：`setContent` 之后
         `Choreographer.postFrameCallback { controller.notifyUiInteractive() }`，3 秒安静期后开闸）；
         没有宿主的进程（桌面/测试）由 15 秒兜底开闸，**闸门只会延后、永远不会把功能关掉**。
         走闸门的只有 `preloadAdjacent()` 里的四个预热（`warmCurrentSilenceProfile`、`requestEarlyBeatProfile`、
         `prefetchAiTransition`、`precacheNextAudio`）与 `warmCurrentTrackProfilesSoon`（改成"开闸后再等 4s"）——
         **解析、起播、封面、歌词、边界时刻自己的测量（`armIncoming`）一律不过闸门**，
         所以"过渡从不在播放路径上做重活"这条不变量没有变，只是把"起播瞬间的预热"推迟到 UI 起来之后。
         取消语义仍然靠既有的 generation/`playIndex` 检查（推迟提交不会让一条过期的预热真的跑起来）。
      4. `qplayer-beat` 与 `qplayer-precache` 两条 lane 设 `Thread.MIN_PRIORITY`（**不是** `qplayer-silence`：
         静音测量是唯一可能被边界"等"的探针，9 秒窗口里要出结果，见 lane 上的注释）。
         ORT 的 4 条线程是从 precache lane 里创建的本地线程、**继承它的 nice**，所以 DJ 编辑渲染也一起降级。
      5. **埋点**（下一轮归因用）：`PlayerController` 构造函数一行
         `startup: controller ready in Nms (lyric offsets … song meta … playlist cache … queue … custom playlist …; search history 与缓存走查在 worker 上；过渡预热等首帧)`；
         开闸一行 `startup: the UI is up and quiet — releasing N deferred transition warmup(s)`；
         `AndroidStemEditRenderer.model()` 的 ON 行现在带 `hashed in Nms`（模型校验耗时）。
    - **HTTP 测量脚本（已写好，下一轮直接用）**：`D:\qplayer-dev\harness\r15\measure.sh <apk> <label> [runs] [secs]`
      —— force-stop → `gfxinfo reset` → `am start -W` → 等 10s → 收 `dumpsys gfxinfo`（含 `Janky frames`/
      `Number Frame deadline missed`/p50–p99）、`framestats`（逐帧）与 `logcat -v threadtime | grep musicplayer`
      （把帧与"那一秒在干什么"对齐）。基线包已留在 `D:\qplayer-dev\harness\r15\before-1f3520d.apk`
      （= HEAD `1f3520d` 的 debug 包，98,328,180 字节，md5 `d4c2ca2bbb2e0426d5457329f9589b79`）。
    - ⚠️ **仍未排除的三个嫌疑（本轮一行没动，因为无法测量）**：素材库扫描（`AndroidLibraryScanner` + 元数据读取，
      宿主线程）、`controller.loadHome()`（网络+JSON）、以及**首帧本身的合成成本**（Monet 取色、首页列表、
      封面位图上传 —— 封面解码本身已经在 `Dispatchers.IO` 上，见 `rememberCoverBitmap`）。
      另外 `songMetaIndex.load()`/`playlistCacheIndex.load()` 仍在构造函数里同步读 JSON。
  - **测试**：`mvn -pl player-core test` = **196 个用例、1 个失败**，就是那个既有的
    `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（`pageTransitionPreset` 只有常量没有 spec）。
    本轮**没有新增测试**（改的是设置文本、启动顺序与线程归属；Kotlin 侧没有测试基建）。
  - **交付**：debug APK **98,329,797 字节**（sha256 `b4d0782d4b6bf7457ac42248e8487fcda87cd768ab774acee2cac0f83f8557cf`，
    与 GitHub 资产自报的 digest 逐位一致）；分支 `feat/ai-dj-transition`（代码 commit `440a815`，**`main` 未动**）；
    tag `ai-dj-transition-2026-09-20f`；
    页面 `https://github.com/xiaozhuhou233/qplayer-compose/releases/tag/ai-dj-transition-2026-09-20f`、
    直链 `https://github.com/xiaozhuhou233/qplayer-compose/releases/download/ai-dj-transition-2026-09-20f/app-debug.apk`
    （实测 HTTP **200**，302 到 CDN；`gh` 两次 TLS handshake timeout，第三次成功）。
    `release.yml` 照旧失败（既有原因）。基线包留在 `D:\qplayer-dev\harness\r15\before-1f3520d.apk`
    （98,328,180 字节，md5 `d4c2ca2bbb2e0426d5457329f9589b79`），测量脚本 `r15\measure.sh`。
  - **设备卫生**：本轮**没有设备**，所以没有 push/装/改任何设备状态（`files/models/`、`files/legacy-ease-backs`、
    `transitionKind`、`transitionBlendSeconds`、`queue.json` 全部保持上一轮还原后的样子）。
  - **下一件事（按顺序）**：① **插上 K20 Pro**，先跑 `measure.sh` 的 before（`before-1f3520d.apk`）再跑 after（本轮包），
    把"哪些帧掉了、那一秒在干什么"写进本文档（这是本轮唯一欠的账）；② 顺手在真机上**看一眼 B站内联预览的四个角**
    （一个截图即可）——若仍是直角，`punchHole` 那条推理的某个前提不成立，下一步是把
    "用页面自己的背景色画四个角"的 matte 加上（`VideoLayer` 里画在 `BiliVideoSurface` **之后**即可盖住画面：
    画面在窗口下面，App 画的东西永远在它上面），或者把内联那一路换成 `TextureView`（代价是
    第 4 节那条"一个常驻 Surface 永不重建"的架构要重做，风险高，别先做）；
    ③ 仍然**没有人听过任何一次过渡**（第五/八/九/十/十一/十二/十三/十四轮都没听过）。

- **2026-09-21 第十六轮：DJ 式的"出口"变成一段真正的淡出（上一首歌不再戛然而止）——一次只改形状的修复，
  前后都在真机上测了**
  用户原话：「现在给人的感觉是过渡时上一首歌戛然而止了，请在短时间内修复，可能你的过渡有问题」。
  任务书给了两个嫌疑：① 晋升时把还在出声的 outgoing 播放器释放了；② 尾巴是"掉下去"不是"淡出去"。
  - **① 结论（真机数据，不是推断）：真正的原因是 ②，① 被数据否掉。**
    同一对歌（Moonlight → Lalala）、同一个决策（CROSSFADE / 重叠 14971ms、mix x1.0212 + bass swap 8930ms）、
    两次都从 95090ms 起播（剩 40s），`transitionKind` 临时强制成 2（CROSSFADE）以免 AI 每次选别的。
    **before（= 今天出厂的曲线，只多了本轮埋点）**，ramp 14906ms，outgoing 自己的电平：
    | t | 0.8577 | 0.9357 | 0.9701 | 0.9859 | 0.9935 | 0.9967 | 0.9991 | 1.0000 |
    |---|---|---|---|---|---|---|---|---|
    | dB | −6.12 | −12.16 | −18.51 | −24.89 | −31.56 | −37.52 | −48.62 | 恰好 0 |
    每档之间 1162/513/234/113/48/35ms —— **两秒多一点里从 −6 dB 掉到听不见，而且这两秒正好贴在结尾**。
    释放那一下的断言行：`the ramp's last write for it was 0.0000 of unity (−60 dB) written 2ms before
    the release. The last write above that floor was 0.0037 (−48.6 dB), 35ms before it` ——
    **释放本身已经在静音上，嫌疑 ① 不成立**（而且那次 promotion 的 tick 只晚了 19ms，不是主线程卡住）。
    加剧因素是低频：`bass swap done: … (the outgoing track's 1 band(s) below 200Hz are at −1500mB from
    here on, 8957ms into a 14906ms ramp — its level is unchanged, its bottom is gone)` —— 从 60% 开始，
    仍然在响的那首歌**低音被 ban 掉 15 dB**，剩下 40% 是"人声在飘"，听感上很容易读成"这首歌结束了"。
  - **② 改法（最小、只有形状）**：`FadeCurve.DJ_BLEND` 的 outgoing 现在 = 原来的 hold 形状
    × **一段 raised-cosine 出口窗**（`OUT_RELEASE_FRACTION = 0.25`，最后一个四分之一），
    并且最后 `OUT_SILENT_TAIL = 0.01`（1%）**恒等于 0**。hold 指数 2.6 一个字没动
    （那是第 12 轮为"别太早淡出"定的，本轮的问题不在它）。新增 `FadeCurve.INAUDIBLE_DB = −60f`
    与 `outSilentTail()`；`gainDb()` 把 ≤0 的增益折到地板上，好让日志能印数。
  - **③ after 真机（同一对歌、同一决策，ramp 14873ms）**，逐档电平：
    | t | 0.8174 | 0.8698 | 0.9016 | 0.9249 | 0.9408 | 0.9534 | 0.9646 | 0.9713 | 0.9792 | 0.9817 | 0.9865 | 0.9887 | 0.9910→1.0 |
    |---|---|---|---|---|---|---|---|---|---|---|---|---|---|
    | dB | −6.07 | −12.22 | −18.29 | −24.60 | −30.40 | −36.36 | −43.31 | −48.70 | −56.94 | −60.33 | −68.15 | −72.77 | **恰好 0** |
    释放行：`…the last write above that floor was 0.0002 of unity (−72.77 dB), 198ms before it, and this
    curve (DJ_BLEND) holds the last 149ms of the ramp — 1 percent of it — at exactly zero` ——
    **出口提前了约 1.3s 开始，在 ramp 结束前 272ms 就已经到 −60 dB 地板**，并且最后 149ms 恒为 0，
    所以"释放一个还在响的播放器"在这个形状下不可能发生（任何 tick 落在那 149ms 里写的都是 0）。
    同一时刻的对照（dB）：t=0.85: −6.1→约 −10；t=0.90: 约 −9.7→−18.3；t=0.9357: −12.2→约 −27；
    t=0.97: −18.5→约 −47；t=0.986: −24.9→−68。
  - **④ 代价（说清楚）**：`both tracks audible` 10732ms/14906ms（72%）→ **10114ms/14873ms（68%）**
    —— 出口一开始（t=0.75）就压电平，6 dB 那条线自然提前到。对称曲线仍是 41%。峰值功率 2.34→2.34 dB。
    出口的**dB/s 速率几乎没变**（约 20→22 dB/s）：变的是出口**更早开始、绝对电平更低、
    并且结束在释放之前**。旋钮只有 `OUT_RELEASE_FRACTION` 一个；更平滑的一档是 0.35
    （`TailTable` 里那行：out@85 −14.0、out@90 −23.2、both6dB 64%），**要不要换成它取决于听感**。
  - **⑤ 不变量（两轮都核过）**：`audio-clock monitor off … 0 gap(s) on the incoming deck, 0 on the audible
    one (no frozen samples on either deck)`；`transition: promoted queue slot 0` 之后照常播（P0）；
    新歌从 blend 起点连续被听到、没有重播（`handoff resume` 与 `overlap heard` 两行）；本对子没有
    pitch/glide（`no pitch: the key for B is not trustworthy`），所以 round-13 的写次数经济性没有被碰到；
    参数写只有 promotion 那一次 `tempo-restore`（asked x1.0000）。
  - **⑥ 新增的、以后能看见回归的两条日志**（本轮的意义一半在这里）：
    (a) **尾巴轨迹**：outgoing 每跨 6 dB 一行 `the outgoing track is at −12.2 dB (0.2448 of unity) at
    t=0.8698 of the ramp (12936ms of 14873ms, 779ms after the previous step); the incoming is at 0.9933`
    —— 一次 blend 约 10 行，形状是"淡出还是悬崖"从日志就能判；
    (b) **释放断言**：promotion 释放 outgoing 之前一定打的那行（静音→INFO，**高于 −60 dB 就 WARN**
    并直说 `上一首歌戛然而止`），带"最后一次写是多少 dB / 隔了多久 / 上一次高于地板是多少 dB / 曲线承诺
    末尾多少 ms 恒为 0"。`rampStep` 里 `setVolume` 的 `catch (Throwable ignored)` 也改成 WARN 了
    （写被平台拒绝时，断言看到的"我们请求的值"和真实值会不一致，这条日志是唯一线索）。
  - **⑦ 未验证 / 直说**：**这个改动只被日志证明，没有被耳朵证明** —— 没有人听过 before 或 after。
    "现在听起来还会不会像戛然而止"只有用户能判；如果还像，下一步动 `OUT_RELEASE_FRACTION`
    （0.35 那行已经算好）或把 bass swap 从 60% 挪后（`BASS_SWAP_AT`，注意 `StemBridge` 里有一份复制的
    0.6），那是本轮**故意没碰**的另一半（它会让 40% 的 blend 少掉低音）。
    另外本对子**没有低频互换以外的低频问题**、`x1.0212` 的变速在 after 里照旧，没有单独验证。
  - **⑧ 交付**：debug APK **98,330,322 字节**（sha256 `17210c639b29de079575b3f7cfd5a2b79c49286e8d9de963733c84da0a537c46`，
    与 GitHub 资产自报的 digest 逐位一致；**这个包就是真机上跑出上面 after 数据的那个**，md5 `4d52d55c9d14059e114220b419ff06f0`）；
    分支 `feat/ai-dj-transition`（代码 commit `0cb5b76`，**`main` 未动**）；tag `ai-dj-transition-2026-09-21`；
    页面 `https://github.com/xiaozhuhou233/qplayer-compose/releases/tag/ai-dj-transition-2026-09-21`、
    直链 `https://github.com/xiaozhuhou233/qplayer-compose/releases/download/ai-dj-transition-2026-09-21/app-debug.apk`
    （实测 HTTP **200**，302 到 CDN）。`release.yml` 照旧失败（既有原因）。
    本轮 harness：`D:\qplayer-dev\harness\r16\`（`TailTable.java` 曲线候选表、`blend.sh` 一次边界、
    `devstate.sh` 设备状态、`before.logcat`/`before2.logcat`/`after.logcat` 三份真机日志、
    `before.apk`/`app-debug.apk`）。**APK 只有这两份，没有多余副本**。
  - **测试**：`mvn -pl player-core test` = **197 个用例、1 个失败**（`Tests run: 197, Failures: 1, Errors: 0,
    Skipped: 0`），仍是既有的
    `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（与本轮无关）。
    `FadeCurveTest` 7→**8** 个（全绿；新增 `theDjShapeEndsInAHeldZeroSoTheReleaseCannotBeHeardAsACut`：
    末段恒 0、进入恒 0 的那一步已在 −60 dB 以下、出口单调不升、`outSilentTail()` 与曲线一致、
    对称曲线不承诺恒 0；原"0.9 处 ≤ −8.7 dB / 0.98 处 ≤ −18.5 dB"两条按新形状改成 ≤0.15 / ≤−30 dB）。
  - **设备卫生（跑完已做）**：`transitionKind` 2→**0**、`transitionBlendSeconds` 仍是 15、
    `files/state/queue.json` 还原成 `[Lalala, Moonlight]` playIndex=1 positionMs=21325（600）、
    `/data/local/tmp/` 的临时文件删掉、`files/models/` 与 `files/cache/djedit/` 本来就没有（本轮没推模型，
    日志里那句 `stem DJ edits are OFF — no verified model` 就是证据）。
  - **下一件事（按顺序）**：① **让人听一遍**（这一轮的唯一判据；听 `Lalala → Moonlight` 或库里任何一对，
    同一段 15s）；② 若还嫌"戛然而止"，换 `OUT_RELEASE_FRACTION = 0.35` 再听；③ 若听出"低音先没了"，
    再把 `BASS_SWAP_AT` 与 `StemBridge` 里那份 0.6 一起往后挪；④ 第 15 轮欠的启动帧测量
    （`r15/measure.sh` + `before-1f3520d.apk`）与 B站内联预览圆角截图仍未做。


- **2026-09-21 第十七轮：上一首压成背景（A）、人声等融合完再回来（B）、调性融合可测量（C）、五种过渡真的都用上（D）
  —— 四项全部装机验证（Redmi K20 Pro `efaa83b2`，同对子同长度的 before/after）**
  用户原话（四条）：①「让ai过渡部分的前一首歌部分淡一点，要不抢了」②「过渡部分的人声混合的很乱，问题挺严重，
  让过渡部分不要保留非常高亢人声，过渡完再放人声，实在不行就播淡的人声」③「音调融合效果还是不是很强,听着有点割裂,
  注意一定要是歌的背景音乐完全融合再接入人声才是丝滑」④「确保用上多种过渡方式」。
  **约束（不要反抗）**：outgoing 不能被剥 stems（那要在播放中途换音源 = P0「晋升即死」与「重播一次」的机制）；
  只有 incoming 被渲染成一个连续文件。所以「过渡里人声乱」= 两首歌的人声同时在响，只能用**电平/形状/时序**解决。
  - **A. `FadeCurve.DJ_BLEND` 改成用 dB 定义的三段**（`player-core/.../audio/FadeCurve.java`）：
    · 常量：`OUT_BED_DB=-10`、`OUT_BED_ARRIVE=0.30`、`OUT_EXIT_START=0.50`、`OUT_EXIT_FLOOR=0.82`、
      `OUT_EXIT_FLOOR_DB=-66`、`OUT_SILENT_TAIL=0.01`（不变）、`IN_BED_EXPONENT 0.55→0.45`。
      形状在 **dB 域**里是三段各带 raised-cosine 缓入缓出（首段降到 bed、中段保持、后段退到 -66dB），
      `outGain = 10^(db/20)`；**dB 单调不升**（测试按 1/1000 走一遍）。
    · 实测电平表（15s 斜坡，`r17/Curve17.java` 与 `FadeCurveTest` 打印同一张表）：

      | t | 0.25 | 0.50 | 0.75 | 0.90 | -30dB 处 | -60dB 处 | 两首同响 |
      |---|---|---|---|---|---|---|---|
      | 旧（12–16 轮 `cos t^2.6` + 出口窗） | -0.0 | -0.3 | -2.7 | -17.9 | 94% | 98% | **68%** |
      | 新（本轮） | **-9.3** | **-10.0** | **-59.6** | **-66.0** | **63%** | **75%** | **17%** |

      真机同一对子（`1460801818` Life's A Mess → `1410815174`，15s 用户设置、强制 CROSSFADE）逐 6dB 轨迹：
      旧 `-6.0dB@t=0.817 / -12.1@0.869 / -18.5@0.903 / -30@0.945 / 静音@0.99`；
      新 `-6.1@0.171 / -12.2@0.540 / -18.1@0.579 / -30.2@0.631 / -48.1@0.698 / -66(-60 地板)@0.820`。
      日志那行现在是 `both tracks audible for 2472ms of the 14758ms ramp (17%) — the symmetric … gives 6051ms`。
    · **低频交接从 0.6 挪到 0.15**（`PlayerController.BASS_SWAP_AT` 与 `StemBridge.SWAP_AT` 同步改）：
      依据是**两条增益曲线的交点 t=0.14**（out 0.60 / in 0.60，差 0.1dB）——低频是"一次拿走一份、还回一份"，
      只有那一刻交换电平才不掉；`BlendPulse` 实测同一合成对子的低端最深处：**无交接 -1.9dB / 0.15 处 -2.2dB /
      旧的 0.6 处 -7.3dB**。代价是 outgoing 的底鼓 2.3s（15s 里）就交出去了，而它 4.5s 时本来就只剩 -10dB。
      真机：`bass swap done … 2104ms into a 14819ms ramp`（旧的是 8841ms）。
  - **B. 人声严格在融合之后回来（`DjEdit` + 控制器 + 渲染器）**：
    · 新增 `DjEdit.VOCAL_RETURN_MARGIN_SEC = 2×RETURN_RAMP_SEC = 1.0s`：**整段 blend 人声恒定 0**，
      回升从 `blend 结束 + 1.0s - 0.5s` 才开始，**落点是"blend 结束 +1s 之后的第一条小节线"**（几乎总是更晚）。
      旧行为是 removal 窗口正好在 blend 结束处、回升的 0.5s 有一半在 blend 里面。
    · `MixNaturaliser.pitchFitsBeforeVocals` 去掉了"deadline 必须落在 blend 内"这条（那是**梯子**的约束，不是规则的），
      梯子改为在 `PlayerController` 里**夹到 promotion 之前最后一个拍**（它的写作用于"马上要被释放的那一路"）；
      规则本身只剩"人声前 2s 音高回到原位"。
    · **blend 不许活得比 edit 长**：`blend()` 里若 `plan.overlapMs() > 人声回来的时刻`，就把重叠裁到那一刻并写明理由。
      这条是**本轮真机抓到的真洞**：平淡收尾把 blend 抬到 16525ms，而 edit 是按 15s 设置渲染的，
      于是那行自己报出 `the voice is INSIDE THE BLEND — THE RULE IS NOT MET`。
    · **每一条重叠边界一行显式的时序证据**（用户规则的 headline，`grep "backing before vocals"`）：
      `the blend is 14849ms long (…); the key modulation's last write is at 13806ms of the file (5 steps on each deck);
       the incoming track's voice is first heard at 16169ms of the ramp, 1320ms after its end (the DJ edit's own bar
       line puts the voice back at 16214ms …) — the voice is outside the blend, and the modulation (whose last write is
       at 13761ms of the ramp) is over 2408ms before the first vocal: 13761ms <= 16169ms, with the blend ending at 14849ms`。
    · **无模型/无 edit 的降级路**（`capWithoutEdit`，`DEGRADED_BLEND_MS = 8000`）：此时 incoming 播原曲、人声在
      blend 第一帧里，时序救不了 → **把 blend 砍到 8s** 并在 reason 里写明 `DEGRADED: …`（用户原话"实在不行就播淡的
      人声"）。真机（删掉模型 + 清 djedit 缓存）：`CROSSFADE 重叠=long 8145ms … DEGRADED: no DJ edit for this pair
      (no verified model, or the render is not ready) … the blend is cut to 8000ms rather than the 16520ms the user asked for`。
  - **C. 调性融合**：`KeyGlide.MAX_STEPS 6→8`；调制段现在**跑满整个 blend**（规则的 deadline 不再被 blend 截断，
    真机 12093→13761ms、4→5 步）；新增 `KeyGlide.convergenceNote(...)`：用两首**真实 chroma** 报"混音的调"
    与"下一首自己的调"的距离（起点/中点/末步，中点是半音 bin 之间的插值）——真机同一对子
    `0.28 → 0.19 → 0.20`（末值是这对子移调后**自己的残差**，不是没到位）。可 `grep "key convergence"`。
  - **D. 选择器按时序/测量分流**（`HeuristicTransitionChooser` + `TransitionContext.PairFit` + `BeatProfile.relatedTempo`）：
    · 新增公开测量 `BeatProfile.relatedTempo(a,b)`（同一律动：1、2、0.5、3、1/3、1.5、2/3，容差 8% = `MAX_SPEED_STEP`）
      与 `TransitionContext.PairFit`（keys 测到打架 / 速度既锁不住又不是亲属 → `overlapsBadly()`），
      由 `PlayerController.pairFitOf` 用两首的缓存 profile 现算（和静音测量同样的"决策时读、绝不等"）。
    · 规则顺序：不可播/太晚/长度未知 → CUT；**测到尾部静音 ≥1.2s → SILENCE_TRIM（提到短歌规则之前）**；
      任一侧 <90s → QUICK_FADE；`overlapsBadly()` → **FADE_OUT_IN**；其余 → 15s CROSSFADE。
    · **真机五种全到**（同一台设备，`transitionKind=0` 自动）：CROSSFADE（多）、SILENCE_TRIM（3 次，含
      `measured to end in 5300ms of silence`）、FADE_OUT_IN（`PairFit{keys not measured, tempo UNRELATED
      (144.0 vs 61.0BPM, the incoming at x2.3599 (outside the clamp))}`）、QUICK_FADE（`one of the two tracks is
      only 60s long (under 90s)`；库里最短 96s，这一条是把**元数据**时长改成 60s 逼出来的分支，音频是真文件）、
      CUT（`too late: less than 1500ms left`）。
    · **规则分布（设备自己的 36 个 profile，1260 个有序对，`r17/R17Dist.java`）**：CROSSFADE 959（76%）、
      FADE_OUT_IN 301（24%）；"只靠调性打架"0 对（这库里 key 门很少真打架），全部来自速度无关。
      ⚠️ 第一版把 `123.1 vs 61.0BPM`（x2.02 = 同一拍读成一半）判成"打架"，是 harness 抓出来的假阳性 ——
      `relatedTempo` 就是为它写的。真实队列的 6 个可测边界全是 CROSSFADE（相邻曲目本来就近）。
  - **装机 A/B（同对子、同长度、同一台设备；before = `r16/app-debug.apk`，after = 本轮包）**：

    | 项 | before（12–16 轮形状） | after（本轮） |
    |---|---|---|
    | 两首同响 | 10054ms of 14785ms（**68%**） | 2472ms of 14758ms（**17%**） |
    | outgoing 到 -30dB | t=0.945 | **t=0.631** |
    | outgoing 静音 | t≈0.99 | **t=0.82**（-60dB 地板） |
    | 低频交接 | 0.6（8841ms） | **0.15（2104ms）** |
    | 人声回来（incoming 文件内） | 15.047s（= removal 窗末，**在 blend 内 347ms**） | **16.714s（blend 结束 +1.3s）** |
    | 调性梯子 | 4 步 / 12093ms | **5 步 / 13761ms** |
    | 不变量 | 无 `playAt: slot … starts at`；一路 started；0 clock gap | 同（0 gap / 0 frozen） |

  - **B 的"实测"（用 harness 量渲染出来的文件，不是断言）**：把 `djedit/1204756479-v16714.m4a`（本轮渲染）
    与源文件 `cache/audio/1410815174.cache` 都推到设备，用同一个分离 harness（`HtdemucsBench --metrics --series`）
    分别测 0–20s 的 vocal stem：
    · **0–12.5s：抑制 40–70dB**（原始中位 -20.8dBFS → 编辑里 -86…-94dBFS，几乎数字静音）；
    · **13.4s 起：抑制崩到 3–4dB**（人声回来了）；**另一首（`405867780-v17399.m4a`，48000Hz 源、需要重采样）
      完全按计划**：0–16.5s 都是 -85dBFS 上下，16.5–17.4s 回升，17.0s 到 -22.8（= master）✓。
    · ⚠️ **这是本轮发现但没修好的真洞**：第一首渲染出来的 edit，人声在 **13.4s** 就回来了，而**它的计划与日志写的是
      16.21s**（差 2 条小节线 = 3.33s）。已排除：时间轴错位（drums 行 12s 之后 lag 稳定在 +0.05s、差 0.7–1.1dB）、
      文件时长（编辑 173.429s vs 源 173.383s ✓）、解码/重采样（这一首 44100，无重采样）、bridge（本轮 bridge 在
      3.379–6.713s 且被自己的测量否掉）。**没有结论**；下一轮第一件事就是把这个"早 2 条小节线"查清（怀疑渲染器把 plan
      的秒映射到 head 数组时的一处偏移），在那之前"人声在 blend 里为 0"这句话**对每个文件都要实测**，不要只信 plan/日志。
  - **本轮没做/未验证**：
    1. **仍然没有人听过任何一段**（第 5/8/9/10/11/12/13/14/15/16 轮都一样）。这一轮同时改了曲线形状、低频交接时刻、
       人声回归时刻、调性梯子长度与选择器，**听感这一条比任何时候都更该先做**。
    2. 上面那条早退（B 的核心）只在计划/日志层面成立；A/C/D 的真机证据是完整的。
    3. `relatedTempo` 的 8% 与 FADE_OUT_IN 的 24% 分布只在这台库的 36 个窗口上量过；换库要看这个数
       （"一半的歌被读成半/倍速"是这个库的常态，第 8 轮记过）。
    4. `capWithoutEdit`（8s 降级）与 `blend()` 里的"裁到 edit 的窗口"各只在真机上走到一次。
    5. 前一首的 stems 仍然没剥（用户规则的另一半：让第一首的鼓组活到它身体淡出之后）——要做只能"提前把尾巴也渲染成
       文件、在一个非边界的时刻换源"，风险是 P0 那一类，必须独立验收。
  - **交付**：debug APK `98,335,123` 字节；分支 `feat/ai-dj-transition`；tag/release 见本节末尾。
    本轮 harness：`D:\qplayer-dev\harness\r17\`（`Curve17.java` 电平表、`R17Pulse.java` 低频穴、`R17Dist.java` 分布 +
    `dist-final.txt`、`R17Pairs.java` 选对子、`run17.sh`/`poll17.sh` 真机跑一次边界、`before.logcat`/`after4.logcat`/
    `degraded.logcat`/`foi.logcat`/`cut.logcat`/`qf.logcat`、`edit.m4a`/`orig.bin`/`edit-lam.m4a` 与 `ser-*.csv` 帧数据）。
    ⚠️ **两个真机坑（本轮踩到，记住）**：① `run-as … cat` 读二进制会坏（`exec-out` 才对，md5 验证过）；
    ② 设备**锁屏时什么都不会播**（`am start` 成功但 player 不启动），且**跑之前必须先 force-stop**，
    否则还在跑的 App 会把自己的 queue 状态盖在刚 push 的 queue 上；脚本里加了 `input tap` 按播放键（首页 mini-player 中心 766,1999）。
  - **测试**：`mvn -pl player-core test` = **207 个用例、1 个失败**，仍是既有的
    `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（与本轮无关）。新增
    `HeuristicTransitionChooserTest`（8 个：五种 kind 都可达 + 亲属速度不是打架）；`KeyGlideTest` 加了收敛报告用例；
    `FadeCurveTest` 重写为 dB 形状的电平表。
  - **设备卫生（跑完已做）**：`transitionKind` 2→**0**、`transitionBlendSeconds` 仍 15、`files/models/` 删除、
    `files/cache/djedit/` 清空、`files/state/queue.json` 还原成跑之前的 33 首（playIndex 0 / positionMs 30769）、
    `/data/local/tmp/r17` 与临时文件删除、`svc power stayon false`。
  - **下一件事（按顺序）**：① **听**（听 `Life's A Mess → 1410815174`：A 的床 + 17% 同响 + 0.15 的低频交接 +
    5 步 +2 半音梯子 + 人声在 blend 之后 1.32s 回来）；② 查 B 的那个"早 2 条小节线"（渲染器 plan→head 的映射）；
    ③ 听感决定 `OUT_BED_DB`（-10 是不是太狠）与 `BASS_SWAP_AT`（0.15 会不会让人觉得第一首的鼓没了）；
    ④ 若"两首同响 17%"听下来太空，把 `OUT_BED_DB` 抬到 -7/-8 或把 `OUT_EXIT_START` 往后挪（一个数）。

### 第 18 轮（2026-09-22）：融合过渡 —— 把两首的背景拼成一小段，不是交叉淡化

**用户的原始要求**：「在过渡时可以尝试提取前一首背景音乐和后一首的背景做一段融合过渡，接近过渡里，
绝对避免像淡入淡出，也尽量避免过渡时出现人声」。听完第一版 PC 原型后用户又补了两条：
「我在听融合部分时会感觉有卡顿一下大概第九到第十秒处 fusion-huai_owa」、「新歌的进入请用淡入效果」。

**设计（与第 17 轮的根本区别）**：过渡的**可听内容全部搬进预渲染文件**（入曲 deck 仍是一个源放到底，
"绝不中途换源"的不变量不变）。在 `[entry, fusionEnd]`（3 小节）里：
- 交界点起 **A 的鼓 + 贝斯接着打** —— 它与 A 的 live deck 在交界处是**同源、同相位**的材料，所以交界能用
  线性等增益交接而不塌；
- **B 的床在一小节内淡入到 unity**（用户点名要的；它同时压住了"A 的人声消失"带来的电平落差）；
- A 的鼓在 `entry + 1 小节`、贝斯在 `entry + 2 小节` 上**用 80ms 等功率拼接切走**（不是淡出），
  B 的鼓/贝斯**同一时刻切进来**（鼓点不断）；A 的 `other`（旋律）以 −9dB 挂到 `+2 小节`一起切走；
- **A 的人声 row 从不参与携带**；入曲人声仍等融合结束后第一条小节线（`-v`）；
- 交界那条小节线在 ±2 小节内**优先挑还有律动的位置**（排序：groove → 安静人声 → 最近）；
- 文件头加**峰值限制器**（1ms attack / 150ms release，无 makeup）与**有上限 +8dB 的补偿增益**
  （把人声让出的电平补回来）；交界处两台 deck 做 **300ms 线性等增益交接**（`FadeCurve.FUSION`；等功率会把
  同源相关信号加成 +3dB 鼓包，线性才平）；
- **任一条不满足就原样退回第 17 轮的行为**，不报错。

**共享契约（新，别再改回去）**：`StemEditRenderer.Result` 增加 `entryMs/junctionMs/fusionEndMs` + `isFusion()`，
文件名后缀增加 `-e/-j/-f`（`suffixOf` 5 参重载）；`Request` 增加 `blendMs`/`incomingContentStartMs`/`canFuse()`。
播放端：见到 `-j/-f` → deck 起点用编辑自己的 `-e`（**不再用 `beatEntryMs`**）、**按出曲 position ≥ `-j` 触发**
（不是按剩余时长）、曲线用 `FUSION`、**不 arm 低频 EQ、不上调性梯子**（文件是一个混好的源，梯子会连 A 的素材
一起移调）、`|position − junctionMs| > 400ms`（用户拖过进度条）则放弃融合走老路
（`FUSION_JUNCTION_GUARD_MS = 400`：闸门比 150 宽，因为触发比的是**live deck 的位置**与**渲染从文件里探到的位置**，
两者能差上百毫秒，闸门太紧会在正常边界上误弃，而误弃比切偏更糟）。新常量都在 `StemFusion`：
`LOCK_TOLERANCE 2%`、`JUNCTION_SEARCH_BARS 2`、`MAKEUP_MAX_DB 8`、`JUNCTION_STEP_MAX_DB 3.5`、`CUT_MS 80`、
`BED_FADE_BARS 1`、`FUSION_TAIL_MAX_MS 12000`、`GROOVE_FLOOR_DBFS`。

**PC 上真渲染 + 人耳（本项目第一次有人听过过渡）**：`D:\qplayer-dev\harness\fusion\`（venv + onnxruntime 1.30 +
htdemucs-quarter + PyAV；PyPI 走本地代理 `127.0.0.1:7890` 可装）。两版 WAV 渲染给用户听：
`render\fusion-r18-audio_huai_audio_owa.wav`（最终规则）与 `…-quietjct.wav`（交界改用"最近且安静人声"的对照）。
- **用户听到 9–10 秒处"卡顿"= 交界**：那一版交界电平落差 **−7.25dB**（100/500/2000ms 分别 −2.81/−4.10/−11.46）。
  修完（限制器 + 旋律回 −9dB + 补偿 + groove 交界）**交界落差 −0.09dB**（100/500ms = −1.39/−0.04），
  代价是那条交界线上 A 正在唱（人声 row −14dBFS），300ms 交接会把最后一个字切掉；对照版人声干净但要 +5.5dB 补偿。
- **人声（用户的硬要求）**：窗口内 **A 的人声 row 恒为 0**；把渲染结果再喂回模型复测，整体人声
  **−61.30dBFS = 比两首 master 里更响的那条人声低 30.96dB**；作为对照，第 17 轮那条路（A 的 −10dB 床）会把
  A 的人声放在 **−40.34dBFS**，高 21dB。
- 素材本身是瓶颈：7 首测试曲里**没有一对同时"锁定 + 都可听"**，唯一锁得住的那对出曲结尾是纯 pad。

**真机（Redmi K20 Pro `efaa83b2`）**：模型推到 `files/models/`（`/data/local/tmp` 中转再 `run-as cp`；
sha256 一致，日志 `stem DJ edits are ON … sha256 427b9588…`）。**本轮结束时模型与 `cache/djedit/*.m4a` 是留下的**
（用户要在手机上听融合就必须留着模型；删法见 `logInertReason`）。
- 跑① `Life's A Mess → Moonlight`：**对子选错了**（143.2 vs 127.8 = 10.8%，不锁）→ 融合被"锁定"条款拒绝，
  日志把理由写全；桥也被它自己的测量否掉（A 尾段没有低频）→ 播 round-12 编辑；**边界跑通**。
- 跑② `Life's A Mess → 34364062`（143.2 vs 144.0，**锁 0.54%**）→ **融合规划成功**：交界小节线 187092ms
  （+149ms，搜索 ±3293ms）、B 起点 17ms、相位差 28ms、鼓 1684ms 换、低频 3351ms 换、A 全走 5018ms、
  素材 3414ms、床一小节淡入；补偿顶到 **+8.00dB**（交界 −10.96 → **−3.23dB**，条款 3.5dB 内）→
  **仍被脉冲条款拒**：「carried bass −66.9dBFS 是哑的 / 段落脉冲有 1676ms 空洞」= **A 的结尾有鼓（−29.1dBFS）
  却没有贝斯**，融合的第一小节（只有 A 的素材在响）因此被读成空洞。
  → 优雅退回；边界照跑：**promoted at 16416ms，两个 deck 音频时钟 0 gap / 0 frozen**（目前能拿到的最强"不卡"证据），
  下一首的渲染接着启动。渲染成本 **126s**（失败的融合多花了 16.3s 的分离）。
- **未验证**：**融合从未在 App 里被听见过**（没找到合适素材），`FadeCurve.FUSION` 的 300ms 交接、`-e` 的 deck 起点、
  400ms 闸门都还只到"编译 + 单测"这一层。

**用户的两个决定（2026-09-23，听完试听件后，照此执行，不要再问）**：
1. **交界策略保持"自动"**：`StemFusion` 那条排序（律动 → 安静人声 → 最近）就是最终答案，**不做面向用户的开关**。
   用户听过 `fusion-r18-audio_huai_audio_owa-r6.wav`（律动）与 `…-r6-quiet.wav`（安静人声）后原话：
   「你可以拿 ai 自动切换律动和人声安静，但我感觉都差不多」—— 即**两者听感差别不值得再投入**，
   以后不要把精力花在交界选择上，也不要把这个当作待定项。
2. **优先级跟着这条走**：既然交界听不出差别，可听性的瓶颈只有两个 —— **能不能触发**（对你库里
   87.6% 的"速度无关"对子，只有 SLAM 那一条路）与**素材**（出曲尾段是否有鼓有贝斯）。用户点名的
   第一对素材是 **The Kid LAROI《A COLD PLAY》(id 2743017086, 179.9s) → s0rrow《unhappy》(id 3332771263, 98.1s)**：
   实测网格 100.62BPM/596.3ms/conf 0.66 与 117.42BPM/511.0ms/conf 0.56，**差 16.70% = 无关速度 → 只有 SLAM**；
   出曲那条交界（167238ms，距目标 −412ms）**12 拍鼓全在打且人声 −70…−85dBFS**（目前最好的一份），
   它自己的结尾在衰减 —— 轻尾规则正确避开了末尾。**下一步就是给这一对渲一份 SLAM 并检查入曲的声部
   是否在一小节内到达。**

：逐拍实测 A 的鼓只有前两拍有真鼓点
**下一件事（按顺序）**：
1. **脉冲条款已按真实素材校准 —— 结论是"拒绝正确，条款不动"**：逐拍实测 A 的鼓只有前两拍有真鼓点
   （−28.2/−24.6 dBFS），之后掉到 −65…−75，A 的贝斯全程在地板（−76），B 的前奏约 2 秒内没有节奏内容 ——
   **这段的律动真的停了**（breakdown）。所以 `Life's A Mess → 34364062` **不该融合**，那对素材就是这样；
   那些逐拍数字已写进条款自己的 doc（读者换它时从这两个数开始：本库口径 7/11 拍、空洞 1676ms；离线复算 6/11、2104ms）。
   **融合的头号前提由此明确：出曲末尾 2–3 小节必须有鼓有贝斯**，选材要看这个，别看"歌好不好听"。
2. **桥的验收条款已修（这是个真 bug，且影响老功能）**：旧条款要求"每一拍都有 +6dB 的低频攻击"，而真实承载的低音是
   **连奏**（−14.1 dBFS、与源一比一、每拍只比自身中位高 +0.5…+3.3dB）→ 数出 "0 of 7 拍" → **桥在所有试过的真实对子上
   都被静默丢掉**（第 17 轮那两次也一样）。新口径问的是它文档本来想问的问题——低频在不在——用"每拍的峰值 vs
   **源**的每拍峰值中位"（20dB 内算在响；拿源做参照是因为空洞够大时会把承载自己的中位一起拽下去）。
   设备素材读作 **7/7 拍、0ms 空洞 → 接受**；合成的空洞用例仍会触发；新增用例 `aLegatoBassCarryIsNotAHole`。
   同时：**没有低频的入曲**改用节拍网格自己的小节线（`phase + k·4·period`），不再用从缺失低频里估出来的 downbeat
   （那是抛硬币），日志会写"beats are measured and the bar grouping is a guess"。
3. **（已做）** `StemFusion` 两处日志：`%s` 占位符漏进日志已修；`played back at x1.4649` 是"3 小节 ÷（2 小节 + CUT）"的
   **小节数比**，不是 deck 速率（deck 一直是 x1.0000，同一行里 `pitch +0.00` 与各行电平与源一致就是旁证）；现在那行
   分别打印 **deck 的播放比**与 **carry 被拉伸的比**，用例 `theDescribeReportsTheDecksRatioAndNotABarCountRatio`。
4. **让每种编辑都带 `-e`**：融合编辑已经把自己的 `entryMs` 告诉播放端，`-b`/纯编辑还没有 —— 这正是 §零 缺陷 1
   （人声从 13.4s 提前回来）的候选根因，round 19 应把它整体关掉。
5. 融合失败代价高（126s）：测量不过时应**复用已分离的 A 素材**去试桥，不要重拉一遍。
6. `1410815174` 那次 `no bar line of the incoming track inside its own two-bar window` **仍未复现**（离线任意相位都构造不出来）：
   要复现需要那一次 boundary 的原文日志行与 Request 的三个数（`removalMs`/`contentStartMs`/`beatPeriodMs`），
   以及 B 的小节线数组是否为空。低频缺失那条已由上面的节拍网格兜底。

**测试**：`mvn -pl player-core test` 全量 **235 个用例、1 个失败**（仍是既有的
`SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`）；本人独立复跑
`DjEditTest/StemBridgeTest/StemFusionTest/FadeCurveTest/BlendPulseTest/KeyGlideTest/MixNaturaliserTest/
PlayerControllerPlaybackTest` = **105 个用例 0 失败**。`StemBridge.stretch` 的方向 bug 已修（文档说"按 speed 拉长"、
代码一直在缩短 → 旧的桥 carry 实听是 speed²，最多 ±16%；该桥从未在真机放过，无回归可保）。
本轮 harness：`D:\qplayer-dev\harness\fusion\`（PC 渲染/测量）、`harness\r18\run18.sh`（真机一次边界）+
`harness\r18\fusion2.out`、`harness-check\`（曲线与文件名解析）、`FUSION-SPEC.md`（本轮规格，改过按它走）。

### 第 19 轮（2026-09-24）：模型随 APK 交付 —— 装完打开就有，不用 adb push

**用户的原始要求**：「apk我要给别的用户，你要保证模型会在apk安装时加载」——APK 要发给别人，
所以模型必须在 APK 里、安装后自动就位，不允许 `adb push` 或任何手工步骤。

**为什么以前不行（两条，都在代码里）**：① 模型是用户手工推到 `files/models/` 的，
而 `logInertReason()` 那行日志在过去**就是**交付路径（它打印 adb push 命令）；②
`AndroidStemEditRenderer.model()` 的核对**每进程只做一次**（98MB 哈希 ~1s），
所以「进程运行中才出现的模型」要等重启才被看见 —— 这决定了复制必须发生在
**本进程第一次问模型之前**，不能是一个更晚的启动步骤。

**做法（最小改动，三处）**：
1. **Gradle 把模型打进 assets**（`android-shell/app/build.gradle.kts`）：新任务类
   `StageStemModel`（`@InputFile modelFile` / `@Input expectedBytes,expectedSha256` /
   `@OutputDirectory outputDir`）把
   `-PqplayerStemModel` > `$QPLAYER_STEM_MODEL` > `D:/qplayer-dev/htdemucs/htdemucs-quarter.onnx`
   抄到 `models/<name>`，并用
   `androidComponents.onVariants { it.sources.assets?.addGeneratedSourceDirectory(stageStemModel) { t -> t.outputDir } }`
   接进每个 variant（debug/release 都有，打包会等它）。**校验发生在抄完之后、对将要打包的字节做**：
   文件不存在 / 字节数不对 / sha256 不对 ⇒ `GradleException`，信息里有路径、字节数、sha256、
   覆盖用的属性名，以及「为什么拒绝产出没有模型的 APK」。**98MB 不进 git**：输出目录是 AGP 的
   `build/generated/assets/stageStemModel/`（`.gitignore` 已忽略 `**/build/`）。
   `androidResources { noCompress += "onnx" }`：模型是已压缩过的浮点权重，存着不压（APK 大小可预期、
   首跑复制是直读）。
   ⚠️ **踩过的坑**：一开始用 `sourceSets["main"].assets.srcDir(stageStemModel)` —— Gradle **不报错、
   也不建依赖**，`mergeDebugAssets` 照样 UP-TO-DATE，产出的 APK 里**没有模型**（正好是这一步要防的事）。
   必须用 `addGeneratedSourceDirectory` 那条路。另外：`build.gradle.kts` 里 `java.security.MessageDigest`
   的 `java` 会被解析成项目的 `JavaPluginExtension`，要在文件头 `import java.security.MessageDigest`。
2. **首跑复制**（`AndroidStemEditRenderer`）：`model()` 的循环现在先问 manifest（`verified()`，
   它就是原来的哈希+`StemModel.recognise`，日志原文不变），**不被接受时**才
   `extractFromAssets(candidate)`：写到 `<name>.part` → 读完 → 对**复制出来的字节**做
   `StemModel.recognise`（同一个门，不减弱）→ `renameTo(<name>)`。**文件缺失或存在但被 manifest 拒绝
   都会走这条路**（`renameTo` 会替换被拒的文件），所以它同时是「坏文件的修复路径」。
   日志一行带字节数+sha256+耗时。失败（asset 不在 / EACCES / digest 不对）只写一行 warn、删掉半成品、
   返回 false —— 功能照旧 inert，**不抛异常、不阻塞播放**。`logInertReason()` 里
   **adb push 那段已删除**（旧的 OFF 行现在说「asset 里没有、手工放的文件同样接受」+ 完整 manifest）。
   `StemModel` 的 manifest 一个字没动（`recognise` 仍是唯一的门；三处数字必须一起改：
   `StemModel.QUARTER`、`StemModelTest`、`build.gradle.kts` 顶部三个常量）。
3. **改动面**：`build.gradle.kts`、`AndroidStemEditRenderer.java`、`ComposeQPlayerActivity.kt`
   （只改 setStemEditRenderer 上方的注释）、`StemModel.java`（只改类注释）。
   **没碰** `StemFusion`/`DjEdit`/`PlayerController`/`FadeCurve`。

**真机证据（Redmi K20 Pro `efaa83b2`，2026-09-24；APK sha256 `d60c79ef…`，192,179,303 bytes）**：
脚本 `D:\qplayer-dev\harness\r25\run25c.sh`（先 `rm -rf files/models`，或放一个 100KB 的假模型，
再装新 APK → 起 App → 点首页播放键 → 收 `musicplayer` 日志）。两个用例的原文：
- **干净设备（无模型）**：`the stem model was copied out of the APK — assets/models/htdemucs-quarter.onnx
  -> /data/user/0/dev.t1m3.qplayer.debug/files/models/htdemucs-quarter.onnx (97978156 bytes,
  sha256 427b9588287d85d78f212d9f6f4acbc42b626e28ef9d2d948fed78311f4aec20 verified in 212ms)`；
  下一行 `stem DJ edits are ON — htdemucs-quarter.onnx verified in
  /data/user/0/dev.t1m3.qplayer.debug/files/models (97978156 bytes, sha256 427b9588… hashed in 101ms)`；
  之后**渲染真的跑完了**（67.3s，写出 `145287354-v17128-x4-r4.m4a`）。
- **存在但被拒的文件（100KB 假模型）**：先 `… is present but does not match the manifest (102400 bytes,
  sha256 f627ca4c…) — refusing it`，再上面那两行；`files/models/` 由 102400 bytes 变成 97978156 bytes。
- 两份日志里 **`adb push` 出现 0 次**；复制+校验共 ~200ms（98MB，preload 通道），
  两个用例的日志在 `harness\r25\r25b_final.logcat` 与 `r25a.logcat`。
- **copy 失败也要 inert**（`harness\r25\run25b.sh`）：把 `files/models/` 建好再 `chmod 500`
  （app 自己写不进去）→ `W … could not be copied out of the APK's assets/… (java.io.FileNotFoundException:
  …htdemucs-quarter.onnx.part: open failed: EACCES (Permission denied))` → `I stem DJ edits are OFF …` →
  `no DJ edit for <track> this time; the boundary blends the plain stream`；
  播放照常（media_session 在、进程活着、全日志 0 个 crash）。
- **测试**：`mvn -pl player-core test` **267 个用例，1 个失败**（仍是既有的
  `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`）。
- **未验证**：release variant 没打过（只打了 debug，但 assets 是 source set 级的，两个 variant 走同一
  条路）；CI 上没试过（按设计会失败，除非把模型放上去）；用户手上那台 8e（`R5CY10P6MJF`）本轮
  中途掉线，只用了 K20 Pro。

**第 19 轮之后仍未做（不要以为已经做了）**：§零 缺陷 1 的收尾 —— **让每种编辑都带 `-e`**
（融合路径已经带了，`-b` 桥和纯编辑还没有，人声从 13.4s 提前回来的候选根因）；融合段在真机上
**仍未被人耳听过**；模型交付之外的老问题（启动掉帧、桥没在真机放过、B站圆角、17% 双可闻等）
仍都在 §零 的清单里。




## 八、工作方式（为了省上下文，请遵守）

1. **先读本文档，再动手**；不要先探索仓库。
2. **"找/验"类工作派子代理**（Explore / general-purpose），让它读大文件、只回结论 ——
   这是本仓库最有效的省上下文手段。
3. **一轮只推进一个能编译的步骤**，跨轮计划写进本文档（不要记在脑子里）。
4. 改完后：编译 →（设备在线就）装机 → 用 `musicplayer` 标签的 logcat 验证 → 更新本文档。
5. 编辑前重读目标区域（多工作区同时改），用 `sed -n 'N,Mp'` 而不是整文件读。

## 九、音频焦点：自动暂停 / 自动恢复（2026-09-21 修复，装机验证）

**用户诉求**：别的 App（B站、别的视频软件、别的音乐）开始放 → qplayer 自动暂停；那个 App 停了/暂停了
→ qplayer 自动恢复。这一节是这条契约的全部事实与证据，别再重新摸索。

### 为什么以前不会暂停（两个根因，都在代码里，都在真机上复现过）

1. **`hasFocus` 在永久丢失后没有清掉**（`AndroidAudioBackend.requestFocus()` 的
   `if (hasFocus || audioManager == null) return;`）。另一个 App 用 `AUDIOFOCUS_GAIN`（视频/音乐 App
   的常规请求）拿走焦点时，框架给我们的 `AUDIOFOCUS_LOSS` 会**把我们移出焦点栈**——
   `dumpsys audio` 实测：`handleLoss code:-2` 之后（另一 App 释放后）栈是**空的**，
   我们**再也不会收到任何焦点回调**。而 `hasFocus` 仍为 true → 用户手动按播放时
   `requestFocus()` 直接 return → 之后所有 App 抢焦点我们**收不到通知** → "它不再暂停了"。
   → 修：永久丢失时 `forgetFocusAfterPermanentLoss()`（`focusRequest=null; hasFocus=false`），
   下一次 `play()/resume()` 重新申请。**这是本轮的主修**。
2. **`setWillPauseWhenDucked(false)` 让框架替我们"静默 duck"**。别的 App 发
   `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`（视频 App 为了让自己能被导航/提示压过去，常用这个）时，
   框架**不问我们**，直接给我们自己的 MediaPlayer 挂一个 VolumeShaper：`dumpsys audio` 实测
   `ducking player piid:13335 … mVolumes[]=[1.0, 0.2]`，**没有任何 focus 回调**：
   歌继续以 20% 音量在视频底下响，媒体会话还写着 PLAYING，另一 App 走了也不会恢复。
   这正是"B站一放，qplayer 没暂停"的现场。
   → 修：`setWillPauseWhenDucked(true)`（焦点栈里我们的 flags 变成 `PAUSES_ON_DUCKABLE_LOSS`），
   同一个请求于是变成一个我们能处理的 transient loss → 暂停、并在回来时恢复。

### 四种焦点的规则（每条都有一行日志：哪种、做了什么、会不会恢复）

| 收到 | 行为 | 恢复 |
|---|---|---|
| `AUDIOFOCUS_LOSS`（永久，别的视频/音乐 App 抢走） | 丢弃重叠 + 暂停，并**忘掉焦点申请**（下次 play/resume 重申请） | **不自动恢复**（该平台根本不会再来 GAIN），用户按播放才回来 |
| `AUDIOFOCUS_LOSS_TRANSIENT`（来电、短暂打断） | 丢弃重叠 + 暂停，`resumeOnGain=true` | GAIN 时自动恢复 |
| `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` | **暂停，绝不 duck**（两个播放器不同时出声），与 transient 同规则 | GAIN 时自动恢复 |
| `AUDIOFOCUS_GAIN` | `pausedByFocus=false`，`resumeOnGain` 为真才 `start()` | —— |

- **`resumeOnGain` 的清零点**（= 用户插手的四种方式）：`pause()`（App/用户自己的暂停，
  焦点丢失自己的暂停走 `player.pause()`，刻意不经过它）、`play()`（换/起一首）、`resume()`、
  `cancelAutoResume()`（控制器在**用户按下暂停的那一刻**调用，而不是等淡出结束——
  `mediaPause()` 的 `cancelAutoResume()` 放在 `if (!playingIntent) return;` **之前**，
  这样硬件/蓝牙暂停键在"已被焦点暂停"时也算数）。
- **过渡中途丢焦点**：`onFocusLoss()` 先落 `wantPlay=false` / `pausedByFocus=true`，
  **再** `abortCrossfade(true)`——丢弃重叠会走到控制器的常规换歌（`outgoingEndedDuringRamp` 那条），
  它必须发现 App 已经暂停。另外 `performAutoAdvance()` 开头有闸门
  （`backend.pausedByAudioFocusLoss()`）：焦点在别人手上时**队列不推进到播放**，
  既不偷回焦点也不在用户背后开声（`playback: audio focus is with another app — the queue is not advanced`）。

### 真机证据（Redmi K20 Pro `efaa83b2`，2026-09-21，logcat tag `musicplayer`）

- **别的视频 App 抢焦点 → 暂停**：`Glimpse`（LineageOS 相册，media3，焦点栈里 `gain: GAIN`）
  ```
  MediaPlayer: audio focus LOST (permanent: another app took it) at 6352ms — pausing
  (audible=true, overlapping=false); NO auto-resume and the focus request is forgotten,
  so the next play/resume asks for it again
  ```
  媒体会话：`PLAYING(3) position=217` → `PAUSED(2) position=6353`（就停在被打断处）。
- **视频退出 → 不自动恢复**：`dumpsys audio` 的焦点栈变空，App **没有** GAIN 行（永久丢失不会再被通知）。
- **用户手动播放 → 重新拿到焦点**（主修的验证点）：`pack: dev.t1m3.qplayer.debug / gain: GAIN /
  flags: PAUSES_ON_DUCKABLE_LOSS`，会话回到 `PLAYING(3) position=6394`（接着 6353 走，不重头）。
- **transient（探针 hint=2，模拟来电）**：`LOST (transient) at 16367ms … auto-resume is armed`
  → 6s 后 `audio focus gained at 16368ms (ducked=false, resumeOnGain=true)` → `PLAYING(3) position=16526`。
- **may-duck（探针 hint=3，视频 App 那种）**：`LOST (transient, may duck — qplayer pauses instead of
  ducking) at 23058ms` → GAIN → `PLAYING(3)`。
- **用户按下暂停后再来焦点**：`the user's own pause cancels the pending focus auto-resume` →
  `audio focus gained … (ducked=false, resumeOnGain=false)` →
  `focus regained, but nothing is resumed: this pause was not made for a focus loss`，会话**保持 PAUSED**。
- **过渡不变量**（同一次会话，自动模式 15s DJ_BLEND）：`audio-clock … 0 gap(s)` 全程、
  `transition promoted, releasing the outgoing player (the incoming is at 14999ms; the overlap heard
  15151ms = its start 160ms + the 14991ms ramp)`、`handoff resume=15151ms (… 15072ms …)`、
  `releasing the outgoing player at silence — … (-60.0000 dB …)` → 听得连续、不重播、按拍交接。
- **过渡中途丢焦点**（框架侧，`dumpsys audio` 的 Events log；那一秒 App 自己的行被长跑 logcat 吞了）：
  `22:38:48.021 requestAudioFocus() … req=2` → `22:38:48.022 focus owner: …qplayer… code:-2 event:handleLoss`
  （此时 15s 的 crossover 正在跑）→ 之后 7 秒**没有任何 audio-clock 行**（重叠已丢弃、无声音），
  `22:38:55.025 … code:1 event:handleGain` → App 恢复并**重新 arm** 了那条过渡。

### 复现工具（不必装 B站/第二个播放器）

**这台机器上 `tv.danmaku.bili` 并没有安装**（`pm list packages -3` 只有 5 个包，没有 B站），
所以四种焦点用探针复现：`D:\qplayer-dev\harness\focusprobe\`（源码 + 已编译的 `probe.jar`）
```bash
adb push D:/qplayer-dev/harness/focusprobe/focusprobe.jar /data/local/tmp/probe.jar   # ⚠️ 别用目录形式推送：文件名会被截成 focusprobe.ja
adb shell "CLASSPATH=/data/local/tmp/probe.jar app_process /system/bin dev.t1m3.probe.FocusProbe 2 6000"
#   hint: 1=GAIN(永久, 视频 App) 2=TRANSIENT(来电) 3=TRANSIENT_MAY_DUCK(视频/提示)；第二个参数=持有毫秒数，到时 abandon(=对方停了)
```
- 它用 `ActivityThread.systemMain()` 拿系统 Context（无 APK，走 shell uid），
  `d8` 用 gradle 缓存里的 `com.android.tools:r8:8.13.17` 打 dex（build-tools 里的老 d8 会 NPE）。
- **看框架侧的权威记录**：`adb shell "dumpsys audio" | sed -n '/Events log/,$p' | tail -20`
  —— 每次 `requestAudioFocus()`（带 `req=` 提示号）与 `focus owner … code:… event:handleLoss/handleGain`
  都在这里，App 自己的 logcat 掉行时靠它。
- **看会话状态**：`adb shell dumpsys media_session | grep state=PlaybackState`；
  `adb shell cmd media_session dispatch play|pause` 可以当成用户按播放/暂停。
- ⚠️ **长跑 `logcat` 到文件会掉行也会滞后**（本轮就有一次 `audio focus LOST` 的行被吞掉）；
  要证据就 `dumpsys audio` 对一遍。另外 `adb shell` 里 `date "+%F"` 不支持，`grep -a -i` 有时被
  ugrep 误解析 → 用 `awk`。

### 未验证 / 风险

1. **B站本身没测到**（没装）。规则是"任何 App 的焦点请求"通用，但 B站到底发 GAIN 还是
   MAY_DUCK **没有实测**；若是 MAY_DUCK，本轮第二个根因修的就是它（暂停 + 恢复都会生效）。
2. **永久丢失后"对方停了自动恢复"在这一层做不到**：框架不再给永久丢失方任何回调
   （实测栈为空）。要那个行为只能靠别的手段（例如 `AudioManager.isMusicActive()` 轮询），
   而它违反"永久丢失只由用户播放恢复"的约定，本轮**没做**。
3. **`outgoingEndedDuringRamp` + 焦点丢失**那个窄窗口（出曲已经在 ramp 里放完、队列还没推进）
   没被时间上撞到过；闸门在 `performAutoAdvance()` 里，逻辑上覆盖，但没有那一条日志证据。
4. 每一次焦点丢失都会**丢弃正在进行的重叠**（正确性优先，与既有约定一致）：过渡会因为一次来电
   从头重新 arm（`transition: arming slot N behind slot M`），听感上重叠会重新开始。

### 交付（2026-09-21b）

- debug APK **98,330,171 字节**，sha256 `96ff317195bec68112ea01fabe9770e604349a01847c5eda4d15ce1a39fe1761`
  （与 GitHub 资产自报的 `digest` 逐位一致）；**这个包就是真机上跑出上面全部焦点日志的那个**。
- 分支 `feat/ai-dj-transition`（代码 commit `0b0a660`，**`main` 未动**）；tag `ai-dj-transition-2026-09-21b`；
  页面 `https://github.com/xiaozhuhou233/qplayer-compose/releases/tag/ai-dj-transition-2026-09-21b`、
  直链 `https://github.com/xiaozhuhou233/qplayer-compose/releases/download/ai-dj-transition-2026-09-21b/app-debug.apk`
  （经 `127.0.0.1:7890`：`HEAD` → 302 到 CDN，`Range: 0-1048575` → **HTTP 206**、1048576 字节全到；
  98MB 整包经代理在超时内没下完，**不走代理是被墙的**（`http=000`））。`release.yml` 照旧失败
  （`android / Build release APK` 与 desktop 的 `Package`，与上四个 tag 同一批既有原因）。
- 本轮 harness：`D:\qplayer-dev\harness\focusprobe\`（`FocusProbe.java` + `probe.jar`）、
  `D:\qplayer-dev\harness\focuslogs\final.txt`（上面每条真机证据的原始日志）。
  **APK 只有仓库 build 目录那一份，没有多余副本；设备上的探针文件已删除。**
- 真机 `player-core` 测试：197 跑 1 失败，唯一失败是既有的
  `SettingsCatalogTest.pageTransitionDefaultsToZoomAndOffersAccessibleFallback`（与本轮无关）。
