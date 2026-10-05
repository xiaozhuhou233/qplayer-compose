# Claude 设计风格

设置 → 外观 → **Claude 设计风格**。默认关闭，开启立即切换并持久保存；与 iOS design 互斥，关闭后回到 MD3。深浅色仍遵循现有设置，莫奈配色偏好保留但不覆盖 Claude 的纸色主题。最低配模式可以与 Claude 风格同时使用，停止唱片旋转等额外动效。

## 设计与功能接入

参考来源是用户提供的 `回响音乐.zip`，以其中 Compose 布局、色值、细边框、胶带标签、唱片封套和黑胶纹路为设计参考。没有导入演示歌曲、假账号、虚构同步时间或独立的音频管理器。

- 首页：人文式问候、AI DJ 面板、私人 FM、纸面歌单卡片、带编号的三首歌曲推荐栏、专辑探索。
- 歌单：创建／收藏分类、实际账号状态、同步、新建歌单、长按删除确认、B站收藏夹入口。
- 本地：权限与扫描入口、搜索、歌曲／专辑／歌手分类、多选入队；分类内播放仅使用当前可见曲目。
- 搜索：纸面输入框、音乐类型快捷搜索、网易云／B站来源切换、加载和空结果状态。
- 详情：歌单／专辑的封套档案布局、播放全部、编号曲目；艺人、账号、设置与现有弹窗继承纸面配色与字体。
- 播放器：黑胶唱片、封套、逐字歌词、拖动排序队列、收藏、播放模式、进度拖动、定时关闭、专辑与艺人跳转、分享链接。B站复用唯一视频层及原有全屏控制。
- 导航：纸面四标签底栏、选中圆点、细进度悬浮播放器。底栏和播放器由 Scaffold 测量占位，避免遮住列表末尾。

网络、登录、AI 推荐、播放队列和音频仍由原有 PlayerController 管理；此开关仅选择界面。AI 推荐需要用户原有 AI 设置，未引入新服务。

## 字体

采用用户 `Downloads/fonts` 中的 Anthropic Sans / Serif / Mono。WOFF2 经 fontTools 转为静态 TTF，打包至 `app/src/main/res/font/claude_*`。Sans 用于正文与操作，Serif 用于标题、封套与歌词，Mono 用于时间、编号和档案标签。中文由 Android 字体回退处理。没有使用参考应用的字体，也没有复制字体目录里的品牌标记。

字体转换脚本保存在 `.tmp/convert_claude_fonts.py`，产物已入源码资源，正常构建无需 Python 或字体转换工具。

## 构建边界

遵照本次用户要求，只编译和打包，不运行单元测试、UI 测试、模拟器、真机安装或应用启动。编译成功不代表已完成运行验证。

核心使用 Maven `install -Dmaven.test.skip=true`，Android 使用 Gradle `:app:assembleDebug --offline --no-daemon --max-workers=2`。本次为本地 debug APK，应用 ID `dev.t1m3.qplayer.debug`，继承现有 debug 签名，未发布 release。

## 本次产物

构建成功：2026-10-04（Asia/Shanghai）。

- 文件：android-build-output/QPlayer-Claude-1.3.0-debug.apk
- 字节数：99415447
- SHA256：7089013D878D230A45FB69D52B5915A40370AD036646526ABFAFBD855AFDF3ED
- Maven 核心安装成功；Gradle assembleDebug 成功。未运行测试、安装或启动。

