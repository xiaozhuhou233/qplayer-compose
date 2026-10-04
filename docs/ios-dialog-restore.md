# iOS Dialog 局部恢复（2026-10-02）

恢复来源：`backups/glass-before-bilipai-20261002-163859/current-glass-source.zip`。

本次恢复前额外备份：`backups/dialog-before-restore-20261002-192318/before-dialog-restore.zip`（14748 bytes；SHA-256 `6CE634C66F3E0DF1F5BFD3D63B2D591B60A43ECE5D1D87017847AA39942778C2`）。

## 恢复范围

- `IosDialogs.kt` 恢复备份中的布局、遮罩、间距、滚动、按钮区域、旧材质及低版本回退。
- 保留 `QPlayerDialogBackdropHost` 中提供给整个应用的 BiliPai 重力监听；它不属于弹窗局部效果，不能随 Dialog 一起删除。
- `RestoredIosDialogGlass.kt` 从备份提取原有采样和颜色过渡代码：5×5 采样、串行读回、生命周期管理、1000ms 颜色过渡、旧亮度滤镜及折射；未改参数。
- 恢复 Plain 高光和半径24dp、偏移4dp、黑色alpha0.10的旧阴影；移除弹窗中的 BiliPai 表面填充和双光源高光。
- 弹窗内按钮、滑块、开关和图标按钮使用旧效果。作用域开关默认关闭，仅在 iOS Dialog 内提供；页面上的同类控件、MiniPlayer 和导航栏仍用 BiliPai。
- MD Dialog 路径不变。不使用 Git，不修改桌面构建脚本，不覆盖用户并行编辑的播放、歌词、AI 等业务代码。

## 验证

`android-shell/glass-motion-tests/Test-IosDialogRestore.ps1` 已通过：

- Dialog 与备份逐字比对，仅允许依赖重命名、Dialog 作用域开关以及保留共享 BiliPai 监听。
- 原采样和颜色动画主体与备份逐字比对通过。
- 旧颜色模型、采样策略、折射源码与备份一致。
- 默认关闭的恢复开关和弹窗外 BiliPai 绘制路径检查通过。

- 桌面原脚本构建成功：`BUILD SUCCESSFUL in 2m 3s`，35 tasks（6 executed / 29 up-to-date）。脚本未修改；仅在构建子进程设置 `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2`，沿用上一轮限制构建线程的做法。
- APK：`android-shell/app/build/outputs/apk/debug/app-debug.apk`，2026-10-02 19:27:48（Asia/Shanghai），95929362 bytes。
- SHA-256：`CDC6621D9FFDF7DEDD410C2F90C59EEEDAAD19A8F2AC6B6552038FEA9B8C162C`。
- `apksigner verify --verbose` 通过，v2 签名有效；构建后再次执行 Dialog 回归脚本全部通过。共用 BiliPai 调色/材质文件与本轮开始时一致。
- 自动安装仍被 ADB 的 `Cannot mkdir '\\.android': Permission denied` 阻断，未确认设备连接状态，尚未进行真机视觉验收。
