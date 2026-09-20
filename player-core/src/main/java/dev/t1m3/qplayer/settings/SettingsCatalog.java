package dev.t1m3.qplayer.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Every setting the app has, declared once. The settings page is generated from
 * this list at runtime — adding a row here is the whole change, on both
 * platforms at once.
 *
 * <p>Rows render in declaration order under the category they name, and
 * consecutive rows sharing a {@code group} share one card. The grouping,
 * wording and widget choice below reproduce the hand-written page this replaced
 * one for one.
 *
 * <p>A row limited to one host (the desktop-only folder paths) carries
 * {@link SettingSpec.Builder#onlyOn}; the other host never sees it, which
 * replaces the {@code typeof settings.x !== "undefined"} guards the QML used to
 * carry.
 */
public final class SettingsCatalog {

    public static final String DESKTOP = "desktop";
    public static final String ANDROID = "android";

    public static final String APPEARANCE = "外观";
    public static final String PLAYBACK = "播放";
    public static final String LYRIC = "歌词";
    public static final String LOCAL = "本地";
    public static final String ABOUT = "关于";
    public static final String AI = "AI";
    public static final String PALETTE = "调色板";

    public static final List<String> CATEGORIES = Collections.unmodifiableList(
            Arrays.asList(APPEARANCE, PLAYBACK, LYRIC, LOCAL, AI, PALETTE, ABOUT));

    /** Fluid-background mode, 0 dynamic / 1 static. Stored under a new key
     *  because the same setting used to be a boolean ("lyricBgStatic") and a
     *  store can't reinterpret a persisted bool as an int — see the one-time
     *  migration in SettingsCore.load. */
    public static final String BG_MODE_KEY = "lyricBgMode";

    /** Fluid backdrop renderer, kept separate from dynamic/static so every style
     *  can still use the existing battery-saving static cache. */
    public static final String BG_STYLE_KEY = "lyricBgStyle";
    /** When enabled, lyric/detail pages use the blurred album artwork. When
     *  disabled, they use a flat Monet/MD3 colour background instead. */
    public static final String COVER_BACKGROUND_KEY = "lyricCoverBackground";
    public static final int BG_STYLE_PIXI_RENDERER = 0;
    public static final int BG_STYLE_MESH_GRADIENT = 1;
    public static final int BG_STYLE_CLASSIC = 2;

    // Dark-mode row values (the segmented control's indices).
    public static final int MODE_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    /** Shared full-page transition presets. QML and the host-drawn lyric page
     *  both read this index so navigation never changes motion language when it
     *  crosses the QML/Skia rendering boundary. */
    public static final String PAGE_TRANSITION_KEY = "pageTransitionPreset";
    public static final int PAGE_TRANSITION_ZOOM = 0;
    public static final int PAGE_TRANSITION_FADE = 1;
    public static final int PAGE_TRANSITION_SLIDE_HORIZONTAL = 2;
    public static final int PAGE_TRANSITION_SLIDE_VERTICAL = 3;
    public static final int PAGE_TRANSITION_NONE = 4;

    // ---- Track-to-track transitions (「智能过渡」) -------------------------
    // Three rows, one feature. The switch is the single source of truth for
    // whether any transition happens at all; the other two only say which one,
    // and are hidden while it is off (dependsOn below).

    /** Whether one track gives way to the next through a transition instead of
     *  the historical hard cut. Persisted as a bool and pushed straight into
     *  PlayerController.setTransitionEnabled, which is the only thing that reads
     *  it — so toggling it takes effect on the next boundary and survives a
     *  restart through the ordinary settings store. */
    public static final String SMART_TRANSITION_KEY = "smartTransition";
    /** Which transition every boundary gets: 0 = 自动 (PlayerController's
     *  TransitionChooser decides, the local heuristic by default), otherwise
     *  index+1 into {@link dev.t1m3.qplayer.audio.TransitionKind#CHOICES}. */
    public static final String TRANSITION_KIND_KEY = "transitionKind";
    /** The gain curve the overlapping kinds ramp along: 0 = 线性 (FadeCurve.LINEAR,
     *  what P1 shipped), 1 = 等功率 (FadeCurve.EQUAL_POWER). A separate row rather
     *  than a fourth kind because it applies to two kinds at once. */
    public static final String TRANSITION_CURVE_KEY = "transitionCurve";
    /** Whether an overlapping transition is snapped onto the two tracks' beat grids
     *  (P4): the outgoing ramp starts on a beat of the outgoing track and the
     *  incoming track starts on a beat of its own, instead of wherever the file
     *  boundaries are. Only ever acts when both grids are measured and compatible —
     *  a wrong grid must not move a boundary — so leaving it on cannot make a
     *  boundary worse than not having it, only better. Persisted as a bool and
     *  pushed into PlayerController.setBeatAlignmentEnabled. */
    public static final String BEAT_ALIGN_KEY = "beatAlign";
    /** Whether the low end changes hands in the middle of an overlap (「低频互换」):
     *  the incoming track's bass is cut while the outgoing one still owns it, and the
     *  two swap on a beat. This is the one part of a blend that needs a platform audio
     *  effect to work, so it has its own switch: a device whose effect framework
     *  misbehaves plays the same overlap and loses only this. Persisted as a bool and
     *  pushed into PlayerController.setBassSwapEnabled. */
    public static final String BASS_SWAP_KEY = "bassSwap";
    /** How long an ordinary pair is blended over, in SECONDS — the one number the user
     *  sets about a transition's length. Stored in seconds (4–30, step 1) because that is
     *  what the row shows, and converted to ms where the controller wants it:
     *  {@code PlayerController.setBlendDurationMs}. Applied immediately and on every
     *  change — the controller keeps no copy, so a boundary decided after a change uses
     *  the new length, and the length appears in the boundary's own log line
     *  ({@code 重叠=long 20000ms}) plus in the reason a chooser's shorter answer was
     *  raised ({@code raised to the 过渡时长 (20s) blend}). The default is
     *  {@code TransitionPlan.OVERLAP_LONG_MS}, which is what every round before this row
     *  used as a constant. */
    public static final String TRANSITION_BLEND_KEY = "transitionBlendSeconds";

    private SettingsCatalog() {}

    public static List<SettingSpec> specs() {
        List<SettingSpec> out = new ArrayList<>();

        // ---- 外观 -----------------------------------------------------------
        out.add(SettingSpec.segmented("darkMode", APPEARANCE, "深色模式", MODE_SYSTEM,
                        "跟随系统", "浅色", "深色")
                .build());
        out.add(SettingSpec.toggle("monet", APPEARANCE, "莫奈取色", true)
                .desc("随封面动态生成主题配色")
                .accessory("swatch")
                .build());
        out.add(SettingSpec.action("pickFont", APPEARANCE, "字体", "选择")
                .provider("fontName")
                .desc("歌词页立即生效；其余界面文字需要重启软件")
                .build());
        out.add(SettingSpec.radio("graphicsBackend", APPEARANCE, "图形后端", 0,
                        "OpenGL", "Vulkan")
                .desc("切换后重启软件生效；若 Vulkan 初始化失败，将自动切回 OpenGL")
                .onlyOn(DESKTOP)
                .build());
        out.add(SettingSpec.toggle("windowDecorated", APPEARANCE, "使用系统标题栏", false)
                .desc("仅 Windows 生效；关闭时使用 QPlayer 标题栏，切换后重启软件生效")
                .onlyOn(DESKTOP)
                .build());
        out.add(SettingSpec.toggle("showLocalTab", APPEARANCE, "显示本地标签", true)
                .desc("关闭后隐藏底部导航栏和侧栏中的“本地”入口")
                .build());

        // ---- 播放 -----------------------------------------------------------
        out.add(SettingSpec.toggle("unblock", PLAYBACK, "音源解锁", true)
                .desc("灰色/VIP/试听歌曲自动尝试其他音源")
                .build());
        // Default follows the UI locale: the mirror only helps from mainland China.
        out.add(SettingSpec.toggle("mirror", PLAYBACK, "下载加速镜像", isSimplifiedChinese())
                .desc("通过 gh-proxy 镜像下载应用更新")
                .build());
        out.add(SettingSpec.toggle("fade", PLAYBACK, "淡入淡出", false)
                .desc("切歌/播放结束时音量渐变，而不是直接切断")
                .build());
        // 「智能过渡」 and the two rows that refine it. Default ON: the borders are
        // what this feature is for, and every kind falls back to the historical
        // hard cut on its own whenever it cannot be carried out.
        out.add(SettingSpec.toggle(SMART_TRANSITION_KEY, PLAYBACK, "智能过渡", true)
                .desc("切歌时按歌曲信息选择合适的过渡方式；无法完成时自动回退为硬切。"
                        + "已在「AI 音乐助手」里配置服务商时，由 AI 依据歌曲信息挑选（含重叠长度），"
                        + "同样只是建议：无网络/超时/回答无法解析时用本地规则；AI 听不到音频。"
                        + "过渡会做节拍对齐、低频互换，并按两首测得的拍速/调性做小幅变速与升降调"
                        + "（只动下一首、幅度很小、晋升后 6 秒内还原）；测不到就完全不做，"
                        + "任何情况下都不会因此取消过渡")
                .build());
        out.add(SettingSpec.segmented(TRANSITION_KIND_KEY, PLAYBACK, "过渡方式", 0,
                        "自动",
                        dev.t1m3.qplayer.audio.TransitionKind.CUT.label(),
                        dev.t1m3.qplayer.audio.TransitionKind.CROSSFADE.label(),
                        dev.t1m3.qplayer.audio.TransitionKind.QUICK_FADE.label(),
                        dev.t1m3.qplayer.audio.TransitionKind.FADE_OUT_IN.label(),
                        dev.t1m3.qplayer.audio.TransitionKind.SILENCE_TRIM.label())
                .desc("自动：本地规则按时长与静音测量挑选（AI 已配置时由 AI 挑选）；其余为强制使用某一种。"
                        + "普通的可流式歌曲之间默认是约 15 秒的交叉淡化")
                .dependsOn(SMART_TRANSITION_KEY)
                .build());
        out.add(SettingSpec.segmented(TRANSITION_CURVE_KEY, PLAYBACK, "淡化曲线", 2,
                        dev.t1m3.qplayer.audio.FadeCurve.LINEAR.label(),
                        dev.t1m3.qplayer.audio.FadeCurve.EQUAL_POWER.label(),
                        dev.t1m3.qplayer.audio.FadeCurve.DJ_BLEND.label())
                .desc("DJ 式：下一首早早低声铺进来，当前这首压住到结尾才用一小段退出去，"
                        + "整段里大部分时间两首都听得到（像串烧）；等功率/线性是对称的，"
                        + "只有中点附近两首差不多响，听感更像淡入淡出。"
                        + "AI 或本地规则选了 8 秒以上的重叠时自动用 DJ 式")
                .dependsOn(SMART_TRANSITION_KEY)
                .build());
        out.add(SettingSpec.toggle(BEAT_ALIGN_KEY, PLAYBACK, "节拍对齐", true)
                .desc("交叉/快速淡化时把重叠长度对到整拍，并让下一首从自己的拍点进入，"
                        + "两首的节拍才能真正对上（需要两侧都测得可信的节拍，"
                        + "测不到时自动不参与，等于没有这个功能；拍速差得多时会先把下一首"
                        + "小幅变速对上，再对齐）")
                .dependsOn(SMART_TRANSITION_KEY)
                .build());
        out.add(SettingSpec.toggle(BASS_SWAP_KEY, PLAYBACK, "低频互换", true)
                .desc("交叉/快速淡化时，在拍点上把低频从当前这首交给下一首（系统均衡器），"
                        + "避免两条低频线打架。设备不支持音频效果器时自动不参与，"
                        + "只少了低频互换，过渡照常")
                .dependsOn(SMART_TRANSITION_KEY)
                .build());
        // The blend's length. One number for every ordinary pair, applied on the next
        // boundary that is decided (the controller reads it, never a copy), and the
        // floor under the chooser's own answer: a chooser that names 8s is raised to it.
        out.add(SettingSpec.slider(TRANSITION_BLEND_KEY, PLAYBACK, "过渡时长", 15, 4, 30, 1)
                .unit(" 秒").dots()
                .desc("两首普通歌曲交叉淡化多久。太短听着像淡入淡出（这也是默认改成 15 秒的原因），"
                        + "太长则两首不搭的歌会同时很响；4–30 秒，默认 15 秒。"
                        + "当前这首的收尾实测很平淡（没有人声、没有起音、也没有还在往上爬）时，"
                        + "会在用户选的时长上最多再提前 "
                        + dev.t1m3.qplayer.audio.TransitionPlan.PLAIN_EXTENSION_MS / 1000L
                        + " 秒开始融合；"
                        + "「过渡方式」强制成某一种时，这里只影响交叉淡化/快速淡化")
                .dependsOn(SMART_TRANSITION_KEY)
                .build());
        out.add(SettingSpec.toggle("highQuality", PLAYBACK, "高音质播放", true)
                .desc("关闭后使用低音质播放以节省流量")
                .build());

        // Custom API source: one card, the switch plus the field block it gates.
        out.add(SettingSpec.toggle("customApiEnabled", PLAYBACK, "启用自定义 API 源", false)
                .desc("使用第三方接口搜索/播放，独立于内置网易云音源")
                .group("customApi")
                .build());

        // ---- AI 音乐助手 ---------------------------------------------------
        out.add(SettingSpec.dropdown("aiProvider", AI, "AI 服务商", 0,
                "自定义 OpenAI 兼容", "DeepSeek", "OpenAI", "Gemini")
                .build());
        out.add(SettingSpec.text("aiBaseUrl", AI, "API 地址 *", "https://api.openai.com/v1")
                .desc("填写兼容 OpenAI /v1/chat/completions 的地址")
                .build());
        out.add(SettingSpec.text("aiApiKey", AI, "API Key *", "")
                .desc("仅保存在本机设置中，不会写入源码")
                .build());
        out.add(SettingSpec.text("aiModel", AI, "模型名称 *", "gpt-5.6-luna")
                .build());
        out.add(SettingSpec.slider("aiTimeoutMs", AI, "请求超时", 60000, 10000, 180000, 5000)
                .unit(" ms").build());
        out.add(SettingSpec.toggle("aiExcludeLiked", AI, "排除已收藏歌曲", false)
                .desc("推荐时仍以收藏歌曲分析风格，但不重复推荐收藏歌曲")
                .build());
        out.add(SettingSpec.toggle("aiGeminiKnowledgeOnly", AI, "Gemini 强制使用知识库", false)
                .desc("Gemini 不使用联网工具，仅依据模型知识生成歌曲推荐")
                .build());
        out.add(SettingSpec.text("aiWebSearchUrl", AI, "搜索 API 地址", "https://api.tavily.com/search")
                .desc("默认使用 Tavily，也可填写兼容的自定义搜索接口")
                .build());
        out.add(SettingSpec.text("aiWebSearchKey", AI, "搜索 API Key", "")
                .desc("由用户自行申请并填写，仅保存在本机设置中")
                .build());
        out.add(SettingSpec.toggle("aiForceKnowledge", AI, "强制使用知识库", false)
                .desc("联网搜索不可用时使用 AI 内置知识库继续生成，不因搜索失败直接拒绝")
                .build());
        out.add(SettingSpec.toggle("aiShowOutput", AI, "显示输出结果", false)
                .desc("在 AI 对话框中显示模型原始输出，即使解析歌曲失败也可查看")
                .build());
        addCustomApiFields(out);

        // ---- 专辑封面调色板调试 -------------------------------------------
        out.add(SettingSpec.toggle("paletteEnabled", PALETTE, "启用专辑封面调色板", true)
                .desc("使用当前专辑封面生成 Material You / Monet 主题色")
                .build());
        out.add(SettingSpec.slider("paletteChroma", PALETTE, "调色板鲜艳度", 1, 0, 2, 1)
                .desc("0=柔和，1=标准，2=增强；用于调试取色规则")
                .build());
        out.add(SettingSpec.segmented("paletteStyle", PALETTE, "调色板风格", 0,
                "Tonal Spot", "Vibrant", "Expressive", "Fruit Salad")
                .desc("基于封面主色生成不同的 Material 3 色彩方案")
                .build());

        // ---- 歌词 -----------------------------------------------------------
        // One card per control, like every other tab: no group() here, so each
        // row is its own card and the wide-window grid can pair them up.
        out.add(SettingSpec.slider("lyricFontSize", LYRIC, "字号", 28, 14, 40, 1)
                .unit(" px").dots()
                .build());
        out.add(SettingSpec.segmented("lyricFontWeight", LYRIC, "字重", 2,
                        "极细", "细", "常规", "中等")
                .build());
        out.add(SettingSpec.slider("lyricLineSpacing", LYRIC, "行间距", 200, 100, 250, 5)
                .scale(100).unit("×").dots()
                .build());
        out.add(SettingSpec.toggle("lyricSpring", LYRIC, "弹簧动效", true)
                .desc("滚动与逐字上抬使用弹簧物理")
                .build());
        out.add(SettingSpec.toggle("lyricScale", LYRIC, "放大缩放", true)
                .desc("当前行放大、其余行略缩")
                .build());
        out.add(SettingSpec.toggle("lyricGlow", LYRIC, "单词发光", true)
                .desc("仅逐字歌词：唱到的单词显示白色辉光和飘带上浮(较耗电)。"
                        + "歌词阴影开启时仅持续1.5秒以上的单词发光，关闭时所有单词都发光")
                .build());
        out.add(SettingSpec.toggle("lyricShadow", LYRIC, "歌词阴影", true)
                .desc("为歌词、背景声部和翻译添加柔和投影")
                .build());
        out.add(SettingSpec.toggle("lyricLinearAnim", LYRIC, "非逐字歌词线性动画", false)
                .desc("关闭时整行一起点亮")
                .build());
        out.add(SettingSpec.toggle("lyricEdgeBlur", LYRIC, "边缘模糊", false)
                .desc("未聚焦歌词按远近渐进高斯模糊(较耗电)")
                .build());
        out.add(SettingSpec.toggle("lyricMd3Color", LYRIC, "MD3 歌词颜色", false)
                .desc("歌词使用当前 Monet / Material 3 主题色")
                .build());
        out.add(SettingSpec.toggle("lyricParticles", LYRIC, "粒子歌词", false)
                .desc("当前歌词周围的 Monet 光点随逐字进度汇聚到文字")
                .build());
        out.add(SettingSpec.toggle("desktopLyricEnabled", LYRIC, "桌面歌词", false)
                .desc("使用独立渲染线程显示置顶歌词浮窗")
                .onlyOn(DESKTOP)
                .build());
        out.add(SettingSpec.segmented("lyricProgressStyle", LYRIC, "进度条样式", 1,
                        "波浪", "直线")
                .build());
        out.add(SettingSpec.toggle(COVER_BACKGROUND_KEY, LYRIC, "封面背景", true)
                .desc("开启：使用封面高斯模糊；关闭：使用纯色 Monet / Material 3 高斯模糊背景")
                .build());
        // ---- 本地 -----------------------------------------------------------
        out.add(SettingSpec.slider("maxCacheSizeMB", LOCAL, "最大缓存", 200, 50, 1024, 1)
                .unit(" MB").group("cache")
                .build());
        out.add(SettingSpec.action("clearCache", LOCAL, "当前占用", "清除缓存")
                .provider("cacheUsage").inlineProvider().buttonType("outlined")
                .group("cache")
                .build());
        out.add(SettingSpec.path("cacheFolder", LOCAL, "缓存目录", "")
                .desc("本地音乐库封面/歌词缓存与网易云缓存都存在这里；修改后不会自动搬运旧文件，会重新扫描并在新目录下重建缓存")
                .hint("目录路径")
                .group("cache")
                .onlyOn(DESKTOP)
                .build());
        out.add(SettingSpec.path("musicFolder", LOCAL, "本地音乐目录", "")
                .desc("修改后将自动重新扫描该目录中的音乐文件")
                .hint("目录路径")
                .onlyOn(DESKTOP)
                .build());

        // ---- 关于 -----------------------------------------------------------
        out.add(SettingSpec.action("openRepo", ABOUT, "QPlayer", "")
                .icon("link")
                .provider("version").inlineProvider()
                // Hard-coded breaks: qml4j's auto-wrap mis-measures this width.
                .desc("网易云音乐第三方客户端\nMaterial You 风格 · Apple Music 风逐字歌词\n"
                        + "由自研 qml4j 引擎强力驱动 · Skia 渲染后端")
                .build());
        out.add(SettingSpec.action("checkUpdate", ABOUT, "检查更新", "")
                .icon("system_update")
                .build());

        return out;
    }

    /** The custom-API adapter's field block — all in its card, all gated on the
     *  switch above them. */
    private static void addCustomApiFields(List<SettingSpec> out) {
        addApiField(out, "customApiSearchUrl", "搜索接口 URL 模板", "https://host/search?key={keyword}");
        addApiField(out, "customApiSearchListPath", "搜索结果列表路径", "如 data.list");
        addApiField(out, "customApiIdPath", "id 字段路径", "如 id");
        addApiField(out, "customApiNamePath", "歌名字段路径", "如 name");
        addApiField(out, "customApiArtistPath", "歌手字段路径（可选）", "如 artists[].name");
        addApiField(out, "customApiAlbumPath", "专辑字段路径（可选）", "如 album.name");
        addApiField(out, "customApiCoverPath", "封面字段路径（可选）", "如 pic");
        addApiField(out, "customApiDurationPath", "时长字段路径（可选，单位：秒）", "如 duration");
        addApiField(out, "customApiUrlUrl", "播放地址 URL 模板", "https://host/url?id={id}");
        addApiField(out, "customApiUrlResultPath", "播放地址结果路径", "如 data.url");
        addApiField(out, "customApiLyricUrl", "歌词接口 URL 模板（可选）", "https://host/lyric?id={id}");
        addApiField(out, "customApiLyricResultPath", "歌词结果路径（可选，纯 LRC 文本）", "如 data.lyric");
        addApiField(out, "customApiHeaders", "请求头（可选，多个用 ; 分隔）",
                "如 Authorization: Bearer xxx; X-Custom: 1");
    }

    private static void addApiField(List<SettingSpec> out, String key, String title, String hint) {
        out.add(SettingSpec.text(key, PLAYBACK, title, "")
                .hint(hint)
                .dependsOn("customApiEnabled")
                .group("customApi")
                .build());
    }

    /** Mainland-Chinese UI locale (zh, not Traditional, not TW/HK/MO) — the one
     *  place that test lives now; both platform Settings classes used to carry
     *  their own copy of it. */
    public static boolean isSimplifiedChinese() {
        java.util.Locale l = java.util.Locale.getDefault();
        if (!"zh".equalsIgnoreCase(l.getLanguage())) return false;
        if ("Hant".equalsIgnoreCase(l.getScript())) return false;
        String country = l.getCountry();
        return !("TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country)
                || "MO".equalsIgnoreCase(country));
    }
}
