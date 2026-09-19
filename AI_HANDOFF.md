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
   **Maven Central 是通的**（ONNX Runtime 之类理论上可加，但需实测 + 评估 APK 体积）。
2. 视频播放只能走 `android.media.MediaPlayer`（+ 我们的常驻 SurfaceView 图层）。
3. 编辑那个 7700 行文件时**不要手工数括号**：历史教训是多次因多/少一个 `}` 导致整文件
   解析失败（165 个报错）。改结构后立刻编译。
4. 队列存档按 `Track.Source` 名字读写；BILI 的 `biliBvid`/`biliCid` 必须一起存取。

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
   **第五轮（2026-09-19：每个边界一行说明混音结果、无 AI 答案改用 8 秒交叉淡化、置信度换成"窗口自洽"、
   下一首音频预缓存 —— 四项都已装机验证；期间发现**真正的瓶颈是周期选择**，见第七节⑤）**都已实现，
   见第七节（含未验证项与风险清单）—— **下一步第一件事是听感**（第四轮与第五轮都没让任何一次
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
**顺序**：P1 → P3 → P5 → P4 → P6。不要先做 P6。（P1/P2/P3/P7、**P6** 与 **P4 的分析+对齐**都已落地；
剩下的仍是**听觉验证**（P4 现在有装机证据了：能 align=on，但**听感**仍未验证），然后才是 P5 低频互换
与 P4 的下一步"变速对拍"（`setPlaybackParams` 把 B 拉到 A 的 BPM —— 本轮只对齐了网格，没有拉速度，
所以两首速度差得多时对齐会被兼容门直接跳过）。）
**第五轮之后的第一件事（有数据支撑）**：⑤ 里那张 18 首的表说明**瓶颈是周期选择，不是置信度门** ——
测得 72.7 的歌真值 109、测得 97.2 的真值 145（都是 2:3 错位），而门只能在"拒掉错的/放行对的"之间选。
建议的下一步（小、可测）：`BeatAnalysis` 在窗口周期与各段自己选出的周期之间加**和声关系**检查
（1/2/3 倍或 1/2、1/3 视为同一网格，2:3/3:4 不算），它能把"段落只是选了自己的倍数"的正解放行，
同时把 2:3 判死；有了它才值得再动 `MIN_SEGMENT_SUPPORT`。
之后才是第五轮没做的两件事：**听感**（第五轮完全没让 8000ms 兜底真的播给人听过）与预缓存的两个
未走分支。

## 八、工作方式（为了省上下文，请遵守）

1. **先读本文档，再动手**；不要先探索仓库。
2. **"找/验"类工作派子代理**（Explore / general-purpose），让它读大文件、只回结论 ——
   这是本仓库最有效的省上下文手段。
3. **一轮只推进一个能编译的步骤**，跨轮计划写进本文档（不要记在脑子里）。
4. 改完后：编译 →（设备在线就）装机 → 用 `musicplayer` 标签的 logcat 验证 → 更新本文档。
5. 编辑前重读目标区域（多工作区同时改），用 `sed -n 'N,Mp'` 而不是整文件读。
