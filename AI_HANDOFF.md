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

1. **AI DJ 两首歌过渡**（详见第七节）。
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

- **P1 双播放器后端**（前提，工作量最大）：`AndroidAudioBackend` 的 `player` → `playerActive`/
  `playerIncoming`，各带 `setVolume`；新增 `beginTransition(ms)` / `swapTo(which)`；
  媒体会话/通知/进度只跟当前出声的一路，交换点取重叠区中点。风险：CPU/耗电、两路漂移（重叠窗口保持 4~12s）。
- **P2 顺序淡化**（快赢，单播放器）：切歌时 A 淡出 → 起 B → B 淡入。可复用现有
  `startFadeOut` / `applyVolume`。无重叠、听不出混音。
- **P3 重叠交叉淡化**（P1 之后）：窗口内 A 1→0、B 0→1，B 提前起播。
- **P4 分析与对拍**（零依赖 DSP）：`MediaExtractor`+`MediaCodec` 解 A 尾/B 头各 ~15s PCM →
  谱通量 onset + 自相关估 BPM → 定 B 的切入点与 A 的退出点；结果按歌曲 id 缓存进 `DiskCache`；
  只分析"当前 + 下一首"（复用 `preloadAdjacent` 的时机）。小幅 BPM 差用 `PlaybackParams` 对齐。
- **P5 低频互换**（"DJ 感"关键）：重叠期砍掉 B 的低频，到对齐的那一拍换回。
- **P6 AI 部分**：`AiClient` **只听得到元数据/歌词，听不到声音** —— 让它负责"选下一首、挑过渡风格、
  生成 DJ 串词"（语音可用平台 `TextToSpeech`），**不要指望它算 BPM**。
- **P7 界面**：设置里加开关/时长；视频类排除在过渡之外。

**顺序**：P1 → P3 → P5 → P4 → P6。不要先做 P6。

## 八、工作方式（为了省上下文，请遵守）

1. **先读本文档，再动手**；不要先探索仓库。
2. **"找/验"类工作派子代理**（Explore / general-purpose），让它读大文件、只回结论 ——
   这是本仓库最有效的省上下文手段。
3. **一轮只推进一个能编译的步骤**，跨轮计划写进本文档（不要记在脑子里）。
4. 改完后：编译 →（设备在线就）装机 → 用 `musicplayer` 标签的 logcat 验证 → 更新本文档。
5. 编辑前重读目标区域（多工作区同时改），用 `sed -n 'N,Mp'` 而不是整文件读。
