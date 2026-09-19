# qplayer 交接文档（AI 续作用）

> 这份文件是给"下一个会话/下一个 AI 工作区"看的。里面的路径、构建命令、约束、
> 已完成与未完成事项都是当前真实状态，不要凭猜测改动。

## 一、这是什么项目

`C:\Users\xiaoz\Desktop\SkidTime\qplayer` —— Android 音乐播放器 **qplayer**
（原 QML/qml4j 桌面端，正在用 Jetpack Compose 重写 Android 端）。

- Android UI 只有一个文件（非常大，**6000+ 行**）：
  `android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/ComposeQPlayerActivity.kt`
- 播放内核：`player-core/`（Java，`PlayerController.java` ~6000 行，`NeteaseClient`、
  新增的 `bili/BiliClient`）
- Android 播放后端：`android-shell/app/src/main/java/dev/t1m3/qplayer/android/playback/AndroidAudioBackend.java`
- **有多个 AI 工作区在同时改这个仓库**，所以每次编辑前必须重新读当前内容。

## 二、构建与验证（必须照这个来，否则很慢或失败）

```bash
# 核心（改了 player-core 必须做，Android 侧吃的是这个 jar）
export JAVA_HOME='C:\Program Files\Java\jdk-21'
cd /c/Users/xiaoz/Desktop/SkidTime/qplayer
/d/qplayer-dev/download/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests \
    -Dmaven.repo.local=D:/qplayer-dev/cache/maven -pl player-core install

# App（用桌面 Build-QPlayer 的那套环境：项目内 gradle-cache，比 D: 盘快 3~5 倍）
cd android-shell
export JAVA_HOME='C:\Program Files\Java\jdk-21'
export GRADLE_USER_HOME='C:\Users\xiaoz\Desktop\SkidTime\qplayer\gradle-cache'
export ANDROID_USER_HOME='C:\Users\xiaoz\Desktop\SkidTime\qplayer\android-user'
G="/d/qplayer-dev/cache/gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle.bat"
"$G" :app:assembleDebug :app:assembleRelease --no-daemon    # 约 1~2.5 分钟

# 16KB / 无 native 校验（build-tools 34 的 zipalign 没有 -P 16，用 -p）
/d/qplayer-dev/android-sdk/build-tools/34.0.0/zipalign.exe -c -p -v 4 <apk>
unzip -l <apk> | grep -cE 'lib/|\.so|skija'      # 目前是 4（AndroidX graphics-path）

# 设备
/c/Users/xiaoz/Downloads/platform-tools/adb.exe      # 手机偶尔掉线/变 unauthorized
```

设备信息：debug 包名 `dev.t1m3.qplayer.debug`，入口
`dev.t1m3.qplayer.android.ui.ComposeQPlayerActivity`。

## 三、硬约束（不要碰）

1. **不能新增依赖**：这台机器 **Google Maven 不通**（dl.google.com 无响应），
   所以没有 media3/ExoPlayer、没有 Palette、没有 Coil。
   - 图片加载一律用现成的 `rememberCoverBitmap(...)`；
   - 取色用文件里已有的色彩数学（LCh/HSL 那套）自己算；
   - **视频播放只能走 `android.media.MediaPlayer` + `SurfaceView`**；
2. **不引入 Skija native**；APK 里只能有 AndroidX graphics-path 那 4 个 .so；
3. **不要大改 `Track.Source` 之外的持久化格式**（队列存档按 source 名读写）；
4. 编辑这个巨型文件时：**不要手工数括号**。历史教训：多次因为多/少一个 `}` 导致
   整文件解析失败（165 个报错）。改结构时用脚本 + 括号配平校验，改完立刻编译。

## 四、已完成（可用）

### 动画系统（照抄 legado-with-MD3）
- 页面转场：**legado 的单规格**（新页整幅滑入 480ms FastOutSlowIn + 淡入 360ms
  LinearOutSlowIn；返回时旧页 `scaleOut(0.8)` + 淡出）。实现在
  `pageTransitionTransform(...)`，旧的五预设版本保留为 `legacyPageTransitionTransform`。
- 主题：`MaterialTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive())`。
- 共享元素：`SharedTransitionLayout` **包住整个导航（含 Scaffold）**，
  `sharedBounds` 用于容器与封面（照抄 legado 的 `CoilBookCover`），
  `clipInOverlayDuringTransition = OverlayClip(自己的形状)`，
  以及 legado 的**圆角接力缓存** `sharedCoverRadiusCache` +
  `rememberSharedCoverTransitionRadius(...)`（源页静止时写、目标页读作起始圆角）。
- 已接路径：**歌手→专辑**、**主页/我的/搜索→歌单**。键：`album_container_<id>` /
  `playlist_container_<id>` / `album_cover_<id>` / `playlist_cover_<id>`。
- 页面内容出现时机：容器展开前 60% 全透明，之后 `fadeIn(150ms, delay 270ms)` + 微位移。
- 列表卡片文字在**被点的那一张**上 80ms 淡出（`leavingCoverKey`，**不要**用
  `sharedTransitionScope.isTransitionActive`，那是整页级的，会导致整页文字闪空）。

### 功能
- 歌手页：前 10 首热门歌曲 → 专辑 → 简介；Apple Music 风格 hero（模糊+渐变+72dp
  描边头像，取色用 **Monet（专辑封面）**，照片只到第一首歌上方）。
- 搜索页：**「网易云 / B站」双页签**（用"切换数据源"实现，无代码块包裹）。
- 账户页：网易云 + **B站扫码登录**（TV 登录二维码，zxing 生成）。
- B站：**搜索**（wbi 签名可用，匿名可搜）、**视频播放（有声、出画、点击全屏）**、
  cookie 存文件（`filesDir/bili_cookies.txt`）。
- 歌词页：逐字（逐字母）渲染 + 整行单动画 + 同色 span 合并；涟漪（级联延迟 18ms/行）。
- 播放详情页：分享按钮（复制 `https://music.163.com/song?id=<id>`）。

## 五、**未完成 / 待修**（按优先级）

1. **🔴 崩溃：`playBiliCollection` 一播就闪退。**
   - 已实现：`Track.Source.BILI` + `biliBvid/biliCid` 字段 + 队列存档宽容解析 +
     `toTrackBili(...)` + `playBiliCollection(...)`（分P入队替换队列）+
     `resolveAndPlayBili(...)` + `playAt` 里的 BILI 分支。
   - 现状：UI 入口**已切回**已知可用的 `playBiliVideo(video)`（那条游离旁路，视频正常）；
     `playBiliCollection` 保留但未接线。
   - 复现与定位：连手机 → 清缓冲 `adb logcat -c` → 点一条 B站结果 → 立刻
     `adb logcat -d | grep -A40 "FATAL EXCEPTION"`。**先拿到栈再改**。
     可疑点（按可能性）：`playAt` 里面向网易云的缓存/元数据分支（`neteaseId=0` 的
     `diskCache.hasAudio(0)` 之类）、`notifyPlayback()` 在 shell 里构建
     `MediaMetadata` 时对 `source` 的假设、`saveQueue` 对未知源的写出。
2. 全屏控制条完善（清晰度切换）+ 弹幕。
3. **UP 主章节（video chapters）标记画到进度条**：接口
   `GET /x/player/wbi/v2?bvid=&cid=` → `data.view_points[]`（`from`/`to`/`content`，秒）。
4. 退出 B站账号时删除 `bili_cookies.txt`（现在只清了内存）。
5. 旧的 `playBiliVideo` 旁路与 `playBiliCollection` 合并（崩溃修好后）。
6. 搜索页 B站无分页（只取第 1 页 20 条）。

## 六、B站 API 契约（从 PiliPlusX 源码抄的，别再猜）

- 源码：`C:\Users\xiaoz\Downloads\PiliPlusX-dev-new.zip`（已解到 `/tmp/pp`）；
  legado 源码：`C:\Users\xiaoz\Downloads\legado-with-MD3-main.zip`（已解到 `/tmp/legado`）。
- **wbi 签名**：`GET /x/web-interface/nav` → `data.wbi_img.img_url/sub_url` → 取文件名拼接
  → 按 64 位重排表 `MIXIN_KEY_ENC_TAB` 取前 32 = mixinKey → 参数排序拼 mixinKey 求 MD5
  = `w_rid`（另带 `wts`）。要求浏览器 UA + `Referer: https://www.bilibili.com/`。
- **TV 扫码登录**（`passport.bilibili.com`）：`/x/passport-tv-login/qrcode/auth_code` →
  `/qrcode/poll`；appkey `4409e2ce8ffd12b8`，appsec `59b43e04ad6965f34319062b478f83dd`
  （公开的 TV 客户端凭据），`sign` = MD5(排序参数 + appsec)。
- **搜索**：`/x/web-interface/wbi/search/type?search_type=video`（要签名）。
- **分P / 合集**：`/x/player/pagelist?bvid=`（`pages[]`：cid/part/duration）；
  合集在 `/x/web-interface/view` 的 `ugc_season`。
- **取流**：`/x/player/wbi/playurl?bvid=&cid=&qn=64&fnval=1&fnver=0&fourk=1`（要签名）
  → `data.durl[0].url` 是**音视频合一**的 progressive 流（本项目没有 media3，播不了 DASH）。
- **⚠️ CDN 必须带 Referer**：`AndroidAudioBackend.setDataSource` 里对 bili 域名加了
  `Referer: https://www.bilibili.com/` + 浏览器 UA，否则 403（没声音）。
- **弹幕**：`/x/v2/dm/web/seg.so?type=1&oid=<cid>&segment_index=N`（protobuf+deflate）；
  纯显示用 `/x/v1/dm/list.so?oid=<cid>`（XML+deflate）更省事，不需要 proto 解码器。
  渲染建议：Compose `Canvas` 三层轨道（滚动/顶部/底部）+ 与 `MediaPlayer.currentPosition`
  同步 + 设置里一个开关。

## 七、其它已解决的坑（别重犯）

- **bili 搜索结果**：标题要剥 `<em>`；封面是协议相对 `//i0.hdslb.com/...` 要补 https。
- **歌词粒度**：逐字符（字母）单位靠 `expandLyricUnits` 展开；渲染要合并同色 span。
- **取词慢**：AMLL 镜像 8s 超时是主因，已改成两源**并行竞速** + 镜像 4s + 负缓存。
- **打开专辑没动画**：原因是加载时整页 early-return，容器/封面没被组合。
- **歌单入场闪**：目标页封面要用列表那张卡的封面做 `fallbackCoverPath`（同一 URL=同一位图）。
