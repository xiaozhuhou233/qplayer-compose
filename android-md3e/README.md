# QPlayer MD3E

独立 Android 构建，应用 ID `dev.t1m3.qplayer.md3e`，显示名称 **QPlayer MD3E**。可与现有 QPlayer 并排安装；账号、缓存和设置均在新应用自己的沙箱中，需要单独登录。没有修改旧构建入口或旧页面。

## 构建

需要 JDK 21、Android SDK 35 / Build Tools 35.0.0。复用仓库的 Gradle 8.7 wrapper：

```powershell
./android-shell/gradlew.bat -p android-md3e :app:assembleDebug --no-daemon
./android-shell/gradlew.bat -p android-md3e :core:test --tests '*PlayerControllerPlaybackTest' --no-daemon
```
如果 `qml4j-core:0.2.31` 位于非默认 Maven 本地仓库，增加 `-Dmaven.repo.local=<仓库绝对路径>`。可在缓存完整时增加 `--offline`。有 `android-shell/local-debug.keystore` 时复用该调试签名，否则使用 Android 默认调试签名。

APK：`android-md3e/app/build/outputs/apk/debug/app-debug.apk`。

## 边界和连接

- 新 Compose UI 全部位于 `app/src/main/java/dev/t1m3/qplayer/android/md3eui/`，采用 Material 3 Expressive 主题、实色容器和基础控件。
- `:core` 直接编译现有 `player-core/src/main`，输出只在本构建中；不覆盖 Maven snapshot、旧 `target` 或其他构建产物。
- Java-only `:platform` 模块在新 `build/generated` 中逐字节暂存白名单内的八个现有 Android Java 文件，再编译复用：`QPlayerApplication`、密钥保护、`PrefsSettingsStore`、`AndroidMetadataReader`、`AndroidLibraryScanner`、`AndroidColorExtractor`、`AndroidAudioBackend`、`PlaybackService`。网易云登录现在使用 Compose 二维码 Dialog，网页登录 Activity 已从新 APK 移除。不编译 `ComposeQPlayerActivity` 或任何玻璃页面。
- `Md3eRuntime` 持有应用级控制器，使用 applicationContext；界面销毁不关闭后台播放。前台播放沿用旧歌词时钟的 100 ms 采样，其他活动状态每 250 ms 排空控制器 UI 队列。界面隐藏且没有播放服务时停止轮询。
- `PlayerController` → `NeteaseClient` 获取推荐、扫码登录、解析真实音频；点击歌曲加载整组队列，点击歌单复用原歌单播放路径。网易云二维码复用旧核心的 key、矩阵与扫码状态接口，801 等待、802 确认、803 成功，过期自动刷新，关闭/手动刷新使旧请求失效，每轮只允许一个未完成的轮询。登录凭证仍由现有加密存储管理。B站登录复用旧 TV 二维码和轮询流程，关闭及退出登录停止后续请求。
- 底部导航接入发现、网易云歌单、本地音乐、搜索和设置；歌单、歌手、专辑详情可打开并播放。MiniPlayer 可进入完整播放页和队列页。
- 本地音乐复用 `AndroidLibraryScanner`，请求系统音频读取权限后在后台扫描 MediaStore；搜索复用控制器的网易云歌曲/专辑/歌手搜索、本地匹配及歌曲分页。
- 复用 SettingsCore/PrefsSettingsStore 读取主题、音质、缓存和 `unblock` 设置。旧版 VIP/灰色/试听歌曲自动换源由共享 `PlayerController` 和 `SongUnblocker` 执行，默认开启，可在设置页关闭或重新开启；新应用的数据沙箱独立，不继承旧应用的开关状态。设置页已接入播放、AI、ACE、封面取色、页面动画等目录项。
- `PlaybackService` 的旧通知 Activity 名通过 manifest alias 指向新根界面，服务无需修改。服务启动失败和网易云错误显示在 Snackbar；账号和推荐有加载、空态、错误重试。
- 首页、导航、设置、播放详情和歌词采用旧 MD 布局与动效方案；已对照真实 Seal 上游源码接入 shared-axis 详情过渡、顶部滚动、选择组圆角、弹窗阶段、局部状态/进度和图片淡入动画，保留详情共享封面和 LCh 动态主题。歌词使用旧字体、逐帧播放时钟、字形测量与逐字扫色、标点并组、整行/和声曲线，并在支持的系统上绘制动态背景。MiniPlayer 恢复悬浮私人漫游入口、滚动显隐和展开/收回动画。视觉与性能由用户在实体机验收。AI 生成沿用共享控制器，并阻止旧续播请求污染新队列；APK 不打包 ONNX 模型。

## 设备验收

1. 安装 APK，确认是独立的 QPlayer MD3E 图标；旧 QPlayer 数据和页面不变。
2. 未登录时显示公开推荐；断网刷新能结束加载并显示重试入口。
3. 点击账号、主页或歌单页的网易云登录入口，直接显示二维码 Dialog；检查获取、刷新、扫码确认、成功关闭与取消后重新打开，完成登录后显示账号及每日推荐。重启应用验证凭证恢复。不要将 Cookie 写入日志或测试产物。
4. 点可播放歌曲/歌单，确认真实声音、标题和进度；验证暂停/继续、上一首/下一首。
5. 返回桌面、锁屏，确认播放及系统媒体控件；点击通知返回 MD3E 根界面。
6. 旋转屏幕后只有一个播放实例；从最近任务划走后服务停止。重新打开恢复队列，保持暂停直至用户操作。
7. 切换发现、歌单、本地、搜索和设置；授权音频读取后扫描、搜索并播放本地歌曲。
8. 登录网易云后检查创建/收藏歌单及详情；搜索歌曲、专辑、歌手并播放，歌曲列表加载更多。
9. 用一首旧版可换源的 VIP/灰色/试听歌曲验证：设置页“音源解锁”默认开启，官方完整音源优先；必要时静默换源，关闭后不再尝试。切换只影响后续音频解析。
10. 搜索页切换到 B站，可搜索并播放视频；扫码登录后打开收藏夹，检查分 P 队列、章节进度、收藏管理、显示视频/封面、全屏与返回。画面沿用旧 AndroidAudioBackend 的同一个 Surface，返回列表继续后台音频。
11. 右上角打开播放队列，检查空/非空队列都正常显示；拖动每行右侧的排序柄，确认当前歌曲和进度保持；连续拖动到列表边缘自动滚动。拖拽库的默认旧动画 API 已改为显式当前 Compose `animateItem`，避开进入非空队列时的 ABI 崩溃。排序调整当前播放队列，不修改远端网易云歌单的持久顺序。
12. MiniPlayer 覆盖在页面内容上，背景区域透明；列表末尾仅增加可滚动的避让空间，不再把整页截短。滚动显隐和点击进入详情沿用旧逻辑。

后台首页推荐、歌词预取及可恢复的官方音源请求失败不再触发全局错误提示；接口失败仍记录 endpoint/code/message，并保留页面错误、歌词重试和最终播放失败反馈。桌面脚本使用 `Build-QPlayer-MD3E.ps1 -BuildOnly` 只构建与校验 APK。

网易云受网络、账号及歌曲版权影响。构建和控制器单测通过不等于完成已登录账户或真实音频的设备验收；本次实测范围见 `VALIDATION.md`。


