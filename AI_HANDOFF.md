# qplayer 交接文档

> 给下一个会话/工作区。**先读这份，不要先探索仓库** —— 探索会烧掉大量上下文，
> 而下面的路径、命令、约束、已验证的外部事实都是当前真实状态。
>
> 约定：**行号会漂移，按名字定位**（`grep -n "名字" 文件`），下面括号里的行号只是线索。

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
   **下一步第一件事仍然是**听感**（第四/五/八/九轮都没让任何一次
   过渡真的播给人听过；第五轮只证明了 8000ms 兜底"进了状态机并开始 ramp"），优先确认：
   (a) **合适的对子到底能不能跑出变速/改调**（日志里找 `incoming mix applied ... platform reports
   speed x...`；若每条合适的边界都是 `the backend did not apply the mix`，说明这台设备在 parked
   状态下不接受 `setPlaybackParams`，按第七节第四轮风险 1 处理）；
   (b) 10 秒重叠 + 双份鼓组听起来是否真的比 4 秒好（这是整轮的核心假设，只有耳朵能判）；
   (c) 晋升后 6 秒的速度/音高还原能不能听出来；
   (d) 低频互换那一刀是不是太狠（`BASS_SWAP_HZ` / 增益上限都可调）；
   (e) 线性曲线的中点凹陷（设置里「淡化曲线」可直接切等功率对比）；
   (f) 自动规则把"不同专辑的长歌"判成 静音裁切 是否正确（若听着别扭，
   `HeuristicTransitionChooser` 的规则可整段替换）。
   ⚠️ 真机（efaa83b2）的 `transitionKind` 一度从 2（强制交叉淡化）改成 **0（自动）**，
   否则 AI 选择器永远不会被问到；要回到强制模式在设置里改回「过渡方式」即可。
   ⚠️ 想快速试到"混音"这条路，挑两首 BPM 接近（±8% 以内）、调性相近的歌，
   并把「过渡方式」先留「自动」——强制某个 kind 会跳过 chooser，但**不会**跳过 `MixMatch`
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

## 八、工作方式（为了省上下文，请遵守）

1. **先读本文档，再动手**；不要先探索仓库。
2. **"找/验"类工作派子代理**（Explore / general-purpose），让它读大文件、只回结论 ——
   这是本仓库最有效的省上下文手段。
3. **一轮只推进一个能编译的步骤**，跨轮计划写进本文档（不要记在脑子里）。
4. 改完后：编译 →（设备在线就）装机 → 用 `musicplayer` 标签的 logcat 验证 → 更新本文档。
5. 编辑前重读目标区域（多工作区同时改），用 `sed -n 'N,Mp'` 而不是整文件读。
