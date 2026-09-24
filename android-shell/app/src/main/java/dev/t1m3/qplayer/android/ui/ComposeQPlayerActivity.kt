package dev.t1m3.qplayer.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import dev.t1m3.qplayer.android.library.AndroidLibraryScanner
import dev.t1m3.qplayer.android.playback.AndroidAudioBackend
import dev.t1m3.qplayer.android.playback.PlaybackService
import dev.t1m3.qplayer.android.settings.PrefsSettingsStore
import dev.t1m3.qplayer.audio.AudioBackend
import dev.t1m3.qplayer.audio.MetadataReader
import dev.t1m3.qplayer.bridge.PlayerController
import dev.t1m3.qplayer.bridge.SearchRow
import dev.t1m3.qplayer.model.Track
import dev.t1m3.qplayer.netease.dto.NeteaseAlbum
import dev.t1m3.qplayer.netease.dto.NeteasePlaylist
import dev.t1m3.qplayer.netease.dto.NeteaseSong
import dev.t1m3.qplayer.settings.SettingsCatalog
import dev.t1m3.qplayer.settings.SettingsCore
import dev.t1m3.qplayer.settings.SettingSpec
import io.github.timer_err.qml4j.android.R
import dev.t1m3.qplayer.lyric.LyricLine
import dev.t1m3.qplayer.lyric.LyricTimeline
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

//all by chatgpt

private const val REQ_AUDIO = 1
private const val REQ_NOTIFICATIONS = 2
private const val REQ_NETEASE_WEB_LOGIN = 4

// MD3 shape scale tokens applied across the app (small 8 / medium 12 / large 20 /
// extra-large 32 / sheet-top 48 / full pill).
private val ShapeSmall = RoundedCornerShape(8.dp)
private val ShapeMedium = RoundedCornerShape(12.dp)
private val ShapeLarge = RoundedCornerShape(20.dp)
private val ShapeExtraLarge = RoundedCornerShape(32.dp)

/** The same 32dp, as a number, for the one place a corner radius has to reach a
 *  platform {@link android.view.ViewOutlineProvider} rather than a Compose shape
 *  (see [BiliVideoSurface]). Kept beside [ShapeExtraLarge] so the two cannot drift. */
private const val SHAPE_EXTRA_LARGE_DP = 32f
private val ShapeSheetTop = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
private val GoogleSansFlexBold = FontFamily(
    Font(R.font.google_sans_flex_bold, FontWeight.Bold)
)

/** Android entry point for the Compose UI. QML is no longer mounted here. */
class ComposeQPlayerActivity : ComponentActivity() {
    private lateinit var controller: PlayerController
    private lateinit var settings: SettingsCore
    private lateinit var reader: MetadataReader
    private var mainHandler: Handler? = null

    companion object {
        private var sharedController: PlayerController? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let Compose own the complete window, including the status-bar region.
        // The status bar itself is recolored from the active Monet scheme below.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val backend: AudioBackend = AndroidAudioBackend(this)
        videoBackend = backend as? AndroidAudioBackend
        videoBackend?.setVideoSizeListener { w, h ->
            // Fires on the media thread; publish on the main one so this is an
            // ordinary Compose state write.
            runOnUiThread {
                videoSourceWidth = w
                videoSourceHeight = h
            }
        }
        reader = dev.t1m3.qplayer.android.library.AndroidMetadataReader(this)
        controller = sharedController ?: PlayerController(backend, reader).also {
            sharedController = it
        }
        controller.setColorExtractor(dev.t1m3.qplayer.android.graphics.AndroidColorExtractor())
        // Silence measurement for the 智能过渡 feature (silence trimming): the
        // controller only ever uses it through the SilenceProfiler seam, and without
        // one SILENCE_TRIM is simply never performed — every other transition kind
        // works without it.
        controller.setSilenceProfiler(
            dev.t1m3.qplayer.android.playback.AndroidSilenceProfiler(this)
        )
        // Beat measurement for the same feature (P4): a bounded decode window per
        // track, tempo and phase estimated off it, so an overlap can put the two
        // tracks' beats on top of each other. Without it every transition behaves as
        // it did before — nothing else reads it.
        controller.setBeatProfiler(
            dev.t1m3.qplayer.android.playback.AndroidBeatProfiler(this)
        )
        // The stem renderer for the DJ edit: the incoming track's own audio with its
        // vocals taken out of the blend window, rendered on the preload lane minutes
        // before the boundary and played as ONE file by the incoming deck (so nothing is
        // ever switched mid-playback). The model that renders it ships INSIDE the APK
        // (assets/models/htdemucs-quarter.onnx, staged by app/build.gradle.kts) and the
        // renderer copies it into the app's private files/models/ on the preload lane the
        // first time a render is asked for — so an installed APK needs no adb push and no
        // other manual step. It stays inert until a file passes the manifest check
        // (name + bytes + sha256) in AndroidStemEditRenderer.
        controller.setStemEditRenderer(
            dev.t1m3.qplayer.android.stem.AndroidStemEditRenderer(this)
        )
        controller.setClipboard { value ->
            runOnUiThread {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("link", value))
            }
        }
        controller.setWebLoginLauncher {
            runOnUiThread {
                try {
                    startActivityForResult(
                        Intent(this, NeteaseWebLoginActivity::class.java), REQ_NETEASE_WEB_LOGIN
                    )
                } catch (_: Throwable) {
                    controller.cancelWebLogin()
                }
            }
        }

        mainHandler = Handler(Looper.getMainLooper())
        controller.setMainExecutor(mainHandler!!::post)
        PlaybackService.attachController(controller) {
            try {
                val intent = Intent(this, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_REFRESH)
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
            } catch (_: Throwable) {
            }
        }
        controller.setExitListener { runOnUiThread { moveTaskToBack(true) } }

        settings = SettingsCore()
        settings.attach(controller)
        settings.setSystemDark(isSystemDark())
        settings.registerAction("clearCache", controller::clearDiskCache)
        settings.registerAction("checkUpdate", controller::checkForUpdateManual)
        settings.registerAction("openRepo") {
            controller.openExternalUrl("https://github.com/TIMER-err/qplayer")
        }
        settings.registerInfo("version") { "v${controller.appVersion.peek()}" }
        settings.registerInfo("cacheUsage") { "${controller.cacheSizeMB.peek()} MB" }
        settings.load(PrefsSettingsStore(this), SettingsCatalog.ANDROID)

        setContent { QPlayerComposeApp(controller, settings) }
        // The app is on screen as of the first frame this window draws: that frame is the
        // one carrying the first composition, so the callback below is what "the first
        // frame" means here. The controller holds the transition warmups (a whole track's
        // download, two decodes, an AI question, and a separation render that would hash
        // 98 MB and then take four cores) until it has this signal plus a quiet interval —
        // see PlayerController's startup gate. Nothing on the playback path waits on them.
        android.view.Choreographer.getInstance().postFrameCallback {
            controller.notifyUiInteractive()
        }
        controller.loadHome()
        requestAudioPermission()
        requestNotificationPermission()
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun requestAudioPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            scanMusic()
        } else {
            requestPermissions(arrayOf(permission), REQ_AUDIO)
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQ_AUDIO && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) scanMusic()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_NETEASE_WEB_LOGIN) return
        val cookie = data?.getStringExtra(NeteaseWebLoginActivity.EXTRA_COOKIE_HEADER)
        if (resultCode == RESULT_OK && !cookie.isNullOrBlank()) {
            controller.completeWebLogin(cookie)
        } else {
            controller.cancelWebLogin()
        }
    }

    private fun scanMusic() {
        val scanner = AndroidLibraryScanner(contentResolver, reader)
        Thread {
            val tracks = scanner.scan()
            runOnUiThread { controller.scanTracks(tracks) }
        }.start()
    }

    override fun onDestroy() {
        if (::controller.isInitialized && !controller.isPlaying() && !PlaybackService.isRunning()) {
            controller.shutdown()
            sharedController = null
        }
        mainHandler = null
        super.onDestroy()
    }
}

private enum class ComposeScreen { HOME, SEARCH, LIBRARY, LOCAL, PRIVATE_FM, QUEUE, SETTINGS, ACCOUNT, PLAYLIST, ALBUM, ARTIST, LYRICS, BILI_FAV, BILI_FAV_DETAIL }

/** A screen plus the id needed to restore the exact drill-down destination. */
/** The concrete MediaPlayer-backed backend, so the UI can hand a video surface
 *  to the very player that owns the audio. */
private var videoBackend: AndroidAudioBackend? = null

/** Source picture size as the shared player last reported it (0 until a stream
 *  reports one). The video box is framed from this so a 4:3 video keeps its 4:3
 *  instead of being squeezed into a fixed 16:9 frame. */
private var videoSourceWidth by mutableStateOf(0)
private var videoSourceHeight by mutableStateOf(0)

/** Where the picture must be drawn right now, in window coordinates, reported by
 *  whichever slot is showing it (the mini player's cover, the detail page's box, or
 *  the whole window in full-screen). One node at the app root draws it, so the player
 *  is handed a Surface once and never rebuilds the picture — a fresh Surface makes it
 *  start the video pipeline at the preceding sync frame, which is the "replays the
 *  last second" seen whenever the picture moved between slots. */
private var videoSlotRect by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

/** Priority of the slot that last reported, so a transition frame with both slots
 *  composed cannot leave the lower one (the mini player) in charge. */
private var videoSlotPriority by mutableStateOf(0)

/** Set while a panel has to draw over the picture — the detail page's queue sheet is
 *  the only one that overlaps it. The layer is parked off-window for that (see
 *  [VideoLayer]) rather than dropped, so its Surface survives. */
private var videoSlotObscured by mutableStateOf(false)

/** Side padding the full-screen picture keeps, matching the horizontal padding the
 *  rest of the app's pages use — the video never runs to the screen edge. */
private const val VIDEO_SIDE_PADDING_DP = 24f

/** The one B站 video surface for the whole window.
 *
 *  It lives in an overlay added above the Compose content (see
 *  [installVideoOverlay]) and is afterwards only ever moved and resized — never
 *  detached, never replaced by a second SurfaceView, and never put in another
 *  window. That matters because detaching destroys the Surface: when a new one is
 *  handed to the player it re-renders the stream from the preceding sync frame,
 *  which is the "replays the last second" seen when the video changed box (opening
 *  the player) or window (the full-screen dialog used to own a second SurfaceView of
 *  its own). With one surface that is only ever resized, the same Surface object
 *  stays attached and the picture is never rebuilt. */
private var videoOverlay: android.widget.FrameLayout? = null
private var videoSurfaceView: android.view.SurfaceView? = null
private var videoOverlayParent: android.view.ViewGroup? = null
private var videoOverlayOutline: android.view.ViewOutlineProvider? = null
/** Box (window coordinates) the overlay must cover while not full-screen. */
private var videoInlineBounds by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
/** True while the full-screen transport dialog is up. */
private var videoFullscreen by mutableStateOf(false)

/** Adds the video overlay to the window. Called once from onCreate, so the Surface
 *  exists before any playback needs it. */
private fun installVideoOverlay(activity: android.app.Activity) {
    if (videoOverlay != null) return
    val parent = activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
    val radius = 32f * activity.resources.displayMetrics.density
    val outline = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: android.view.View, o: android.graphics.Outline) {
            o.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
    videoOverlayOutline = outline

    val holder = android.widget.FrameLayout(activity).apply {
        clipToOutline = true
        outlineProvider = outline
        visibility = android.view.View.GONE
        // The picture is fitted inside this holder rather than stretched across it, so
        // in full-screen the letterbox bars have to be painted here.
        setBackgroundColor(android.graphics.Color.BLACK)
    }
    // The overlay sits above the Compose UI, so it — not the box drawn under it — is
    // what a tap on the picture reaches. Double-tap switches full-screen; a single tap
    // is deliberately left free (in full-screen it shows/hides the controls, and this
    // view is under that dialog while it is up).
    val gestures = android.view.GestureDetector(
        activity,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean = true

            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                // Only ever enters: while full-screen is up the controls dialog is a
                // window above this one, so it owns both gestures there.
                if (!videoFullscreen) videoFullscreen = true
                return true
            }
        }
    )
    holder.setOnTouchListener { _, event -> gestures.onTouchEvent(event) }
    // The picture for B站 playback: the same MediaPlayer that owns the audio renders
    // into this surface, so picture and sound share one clock.
    //
    // Kept at the default Z order on purpose. setZOrderOnTop(true) lifts the surface
    // above everything the window paints, which also lifted it above the app's own
    // panels — the queue sheet ended up behind a floating video. At the default order
    // the surface shows through the window where this view is, and anything drawn
    // over it (dialogs, the queue sheet) stays on top where it belongs.
    val surface = android.view.SurfaceView(activity)
    surface.holder.addCallback(object : android.view.SurfaceHolder.Callback {
        override fun surfaceCreated(h: android.view.SurfaceHolder) {
            videoBackend?.attachVideoSurface(h.surface)
        }
        override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, hh: Int) {
            // Resizing keeps the same Surface (a new surface object is never handed
            // over), so the backend sees no change and leaves the player alone.
            videoBackend?.attachVideoSurface(h.surface)
        }
        override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
            videoBackend?.detachVideoSurface(h.surface)
        }
    })
    holder.addView(surface, android.widget.FrameLayout.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT))
    parent.addView(holder, android.widget.FrameLayout.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT))
    videoOverlay = holder
    videoSurfaceView = surface
    videoOverlayParent = parent
}

/** Lays the overlay out: over the inline box, or across the whole window in
 *  full-screen.
 *
 *  <p>The holder is what changes size/position — the SurfaceView inside it is never
 *  detached, so the Surface it owns survives (see [videoOverlay]). The picture is
 *  then given the source's own shape, centred, and never stretched or cropped: in
 *  full-screen it is fitted inside the window minus the same side padding the rest of
 *  the app uses, and the holder's black background supplies the bars. */
private fun positionVideoOverlay(fullscreen: Boolean,
                                 inline: androidx.compose.ui.geometry.Rect?,
                                 aspect: Float) {
    val holder = videoOverlay ?: return
    val parent = videoOverlayParent ?: return
    val picture = videoSurfaceView ?: return
    val lp = holder.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
    val pictureLp = picture.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
    val safeAspect = if (aspect > 0.01f && aspect.isFinite()) aspect else 16f / 9f

    if (fullscreen) {
        // Full-screen is laid out as MATCH_PARENT so a rotation resize needs no help.
        holder.outlineProvider = null
        holder.clipToOutline = false
        lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        lp.leftMargin = 0
        lp.topMargin = 0
        val pad = (VIDEO_SIDE_PADDING_DP * holder.resources.displayMetrics.density).toInt()
        val availableW = (parent.width - 2 * pad).coerceAtLeast(1)
        val availableH = parent.height.coerceAtLeast(1)
        var w = availableW
        var h = (w / safeAspect).toInt()
        if (h > availableH) {
            h = availableH
            w = (h * safeAspect).toInt()
        }
        pictureLp.width = w.coerceAtLeast(1)
        pictureLp.height = h.coerceAtLeast(1)
        pictureLp.gravity = android.view.Gravity.CENTER
    } else {
        if (inline == null || inline.width <= 0f || inline.height <= 0f) {
            holder.visibility = android.view.View.GONE
            return
        }
        // boundsInWindow is in window coordinates; the overlay is a child of the
        // window's content view, so subtract where that view starts.
        val loc = IntArray(2)
        parent.getLocationInWindow(loc)
        holder.outlineProvider = videoOverlayOutline
        holder.clipToOutline = true
        lp.width = inline.width.toInt()
        lp.height = inline.height.toInt()
        lp.leftMargin = (inline.left - loc[0]).toInt()
        lp.topMargin = (inline.top - loc[1]).toInt()
        // The Compose box already has the source's shape, so the picture just fills it.
        pictureLp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        pictureLp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        pictureLp.gravity = android.view.Gravity.CENTER
    }
    picture.layoutParams = pictureLp
    holder.visibility = android.view.View.VISIBLE
    holder.layoutParams = lp
}

/** Takes the picture off screen. The Surface goes with it (the view is no longer
 *  shown), so the backend also drops it — leaving the player with no video output,
 *  which is what a detail page that is not open should look like. */
private fun hideVideoOverlay() {
    videoOverlay?.visibility = android.view.View.GONE
}

private data class ComposeRoute(
    val screen: ComposeScreen,
    val id: Long = 0L
)

// Tab order used to give page transitions a direction (shared-axis X):
// moving "forward" slides content left, going back slides it right.
private val screenOrder = listOf(
    ComposeScreen.HOME, ComposeScreen.SEARCH, ComposeScreen.LIBRARY, ComposeScreen.LOCAL,
    ComposeScreen.QUEUE, ComposeScreen.SETTINGS, ComposeScreen.ACCOUNT, ComposeScreen.PLAYLIST,
    ComposeScreen.ALBUM, ComposeScreen.ARTIST, ComposeScreen.BILI_FAV, ComposeScreen.BILI_FAV_DETAIL
)

private data class PlayerUiState(
    val playing: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artistId: Long = 0L,
    val artistIdsCsv: String = "",
    val artistNamesCsv: String = "",
    val album: String = "",
    val albumId: Long = 0L,
    val songId: Long = 0L,
    val positionMs: Long = 0,
    /** Playback time adjusted by the lyric offset; used only by lyric rendering. */
    val lyricPositionMs: Long = 0,
    val durationMs: Long = 0,
    val liked: Boolean = false,
    val likeable: Boolean = false,
    val playMode: Int = 0,
    val privateFmMode: Boolean = false,
    val loading: Boolean = false,
    val loggedIn: Boolean = false,
    val userName: String = "",
    val qrImage: List<List<Boolean>> = emptyList(),
    val qrStatus: Int = 0,
    val webLoginAvailable: Boolean = false,
    val webLoginBusy: Boolean = false,
    val webLoginError: String = "",
    val webLoginSuccessRevision: Long = 0L,
    val recommendations: List<NeteaseSong> = emptyList(),
    val recommendPlaylists: List<NeteasePlaylist> = emptyList(),
    val myPlaylists: List<NeteasePlaylist> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val searchRows: List<SearchRow> = emptyList(),
    val playlistTracks: List<NeteaseSong> = emptyList(),
    val queueTracks: List<Track> = emptyList(),
    val lyrics: List<LyricLine> = emptyList(),
    val lyricsRevision: Long = 0L,
    val lyricIndex: Int = -1,
    val lyricOffsetMs: Int = 0,
    val lyricsCoverOnly: Boolean = true,
    val coverModeManual: Boolean = false,
    val lyricFontSize: Int = 28,
    val lyricFontWeight: Int = 2,
    val lyricLineSpacing: Int = 200,
    val lyricSpring: Boolean = true,
    val lyricScale: Boolean = true,
    val lyricGlow: Boolean = true,
    val lyricShadow: Boolean = true,
    val lyricLinearAnim: Boolean = false,
    val lyricEdgeBlur: Boolean = false,
    val lyricMd3Color: Boolean = false,
    val lyricParticles: Boolean = false,
    val lyricProgressStyle: Int = 1,
    val lyricBgMode: Int = 0,
    val lyricBgStyle: Int = 0,
    val lyricCoverBackground: Boolean = true,
    val playlistTitle: String = "",
    val playlistCoverPath: String = "",
    val artistName: String = "",
    val artistCoverPath: String = "",
    val artistBriefDesc: String = "",
    val artistLoading: Boolean = false,
    val artistSongs: List<NeteaseSong> = emptyList(),
    val artistAlbums: List<NeteaseAlbum> = emptyList(),
    val openArtistId: Long = 0L,
    val albumTitle: String = "",
    val openAlbumId: Long = 0L,
    val albumCoverPath: String = "",
    val albumArtist: String = "",
    val albumYear: String = "",
    val albumTracks: List<NeteaseSong> = emptyList(),
    val albumLoading: Boolean = false,
    val lyricsLoading: Boolean = false,
    val artistHeaderPath: String = "",
    val biliSearchResults: List<dev.t1m3.qplayer.bili.BiliClient.BiliVideo> = emptyList(),
    val biliSearchLoading: Boolean = false,
    val biliLoggedIn: Boolean = false,
    val biliQrUrl: String = "",
    val biliQrStatus: Int = 0,
    val biliError: String = "",
    val biliPlaying: Boolean = false,
    val biliChapterMarks: List<Float> = emptyList(),
    /** B站收藏夹列表（歌单页入口与收藏弹框共用）。 */
    val biliFavFolders: List<dev.t1m3.qplayer.bili.BiliClient.BiliFavFolder> = emptyList(),
    val biliFavLoading: Boolean = false,
    /** 空列表时用来说明原因：未登录时接口返回 code:0 但 data:null，只能靠这条区分。 */
    val biliFavError: String = "",
    /** 当前打开的收藏夹内容。 */
    val biliFavItems: List<dev.t1m3.qplayer.bili.BiliClient.BiliFavItem> = emptyList(),
    val biliFavItemsLoading: Boolean = false,
    val biliFavItemsTitle: String = "",
    val searchArtistId: Long = 0L,
    val searchArtistName: String = "",
    val searchArtistCoverPath: String = "",
    val searchArtistFollowed: Boolean = false,
    val playlistLoading: Boolean = false,
    val homeLoading: Boolean = false,
    val searchLoading: Boolean = false,
    val coverBytes: ByteArray? = null,
    val coverPath: String? = null,
    val coverSeed: String = "",
    val trackKey: String = "",
    val queueIndex: Int = -1,
    val dark: Boolean = false
)

/** One frame of the original QPlayer lyric scroll offset. The Compose port uses
 * delayed samples of this value to reproduce LyricRenderer's short cascade rather
 * than translating the entire column in lockstep. */
private data class LyricScrollSample(val timeNs: Long, val offsetPx: Float)

private val snapshotCache = HashMap<String, List<*>>()

/** Same main-line selection rule as PlayerController, but evaluated from the
 * live backend clock so Compose does not wait for the controller's 5 Hz UI pump. */
private fun lyricIndexForPosition(lines: List<LyricLine>, positionMs: Long): Int {
    var index = -1
    for (i in lines.indices) {
        val line = lines[i]
        if (line.startMs() > positionMs) break
        if (
            index < 0 ||
                line.startMs() > lines[index].startMs() ||
                (
                    line.startMs() == lines[index].startMs() &&
                        lines[index].vocalChannel != LyricLine.VocalChannel.MAIN &&
                        line.vocalChannel == LyricLine.VocalChannel.MAIN
                    )
        ) {
            index = i
        }
    }
    return index
}

/** QPlayer changes its scroll anchor when a line enters the visual fade-in
 * window, rather than waiting for the exact lyric timestamp. */
private fun lyricVisualGroupIndex(
    groups: List<LyricTimeline.Group>,
    positionMs: Long
): Int {
    var result = -1
    for (index in groups.indices) {
        if (groups[index].startMs - 450L > positionMs) break
        result = index
    }
    return result
}

// Direct Kotlin counterparts of LyricMotion.java. Keeping these values and
// curves here makes the Compose renderer follow the original QML/Skia timing
// instead of inventing a second set of lyric transitions.
private fun lyricSmoothstep(value: Float): Float {
    val x = value.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lyricFadeInStart(startMs: Long): Long = startMs - 450L

private fun lyricActiveK(positionMs: Long, startMs: Long, endMs: Long): Float {
    val fadeInStart = lyricFadeInStart(startMs)
    val fadeInEnd = fadeInStart + 600L
    if (positionMs < fadeInStart) return 0f
    if (positionMs < fadeInEnd) {
        return lyricSmoothstep((positionMs - fadeInStart) / 600f)
    }
    val fadeOutStart = endMs + 100L
    val fadeOutEnd = fadeOutStart + 350L
    if (positionMs < fadeOutStart) return 1f
    if (positionMs >= fadeOutEnd) return 0f
    return 1f - lyricSmoothstep((positionMs - fadeOutStart) / 350f)
}

private fun lyricEaseOutBack(value: Float): Float {
    val c1 = 1.7f
    val c3 = c1 + 1f
    val shifted = value.coerceIn(0f, 1f) - 1f
    return 1f + c3 * shifted * shifted * shifted + c1 * shifted * shifted
}

private fun lyricBackgroundScale(positionMs: Long, startMs: Long, endMs: Long): Float {
    val popStart = startMs + 150L
    if (positionMs < popStart) return 0f
    if (positionMs < popStart + 460L) {
        return lyricEaseOutBack((positionMs - popStart) / 460f)
    }
    if (positionMs < endMs) return 1f
    val out = (positionMs - endMs) / 280f
    return if (out >= 1f) 0f else 1f - lyricSmoothstep(out)
}

private fun controllerState(controller: PlayerController, settings: SettingsCore): PlayerUiState {
    // Snapshot cache: the 200ms poll used to copy every list on every tick
    // (thousands of allocations/s with a large library → GC hitches, visibly
    // dropped frames during drags). The controller's Property values are
    // already fresh immutable lists; reuse the previous snapshot whenever the
    // underlying list instance hasn't changed.
    fun <T> cached(cacheKey: String, value: List<T>?): List<T> {
        val list = value ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val prev = snapshotCache[cacheKey] as? List<T>
        if (prev !== list) snapshotCache[cacheKey] = list
        @Suppress("UNCHECKED_CAST")
        return (snapshotCache[cacheKey] as List<T>)
    }
    val currentTrack = controller.currentTrack()
    fun settingInt(key: String, fallback: Int): Int =
        if (settings.has(key)) settings.intOf(key) else fallback
    fun settingBool(key: String, fallback: Boolean): Boolean =
        if (settings.has(key)) settings.bool(key) else fallback
    val livePositionMs = controller.lyricClockPosition().coerceAtLeast(0L)
    val lyricOffsetMs = controller.lyricOffsetMs.peek() ?: 0
    val liveLyricPositionMs = livePositionMs - lyricOffsetMs
    val liveLyrics = cached("lyrics", controller.lyrics.peek())
    return PlayerUiState(
        playing = controller.playing.peek() == true,
        title = controller.title.peek() ?: "",
        artist = controller.artist.peek() ?: "",
        artistId = controller.playingArtistId.peek() ?: 0L,
        // Read the complete credits from the live Track. The Android shell can
        // therefore use the existing core binary without requiring a rebuilt JAR.
        artistIdsCsv = currentTrack?.artistIdsCsv ?: "",
        artistNamesCsv = currentTrack?.artistNamesCsv ?: "",
        album = controller.album.peek() ?: "",
        albumId = controller.playingAlbumId.peek() ?: 0L,
        songId = controller.playingSongId.peek() ?: 0L,
        positionMs = livePositionMs,
        lyricPositionMs = liveLyricPositionMs,
        durationMs = controller.durationMs.peek() ?: 0L,
        liked = controller.currentLiked.peek() == true,
        likeable = controller.currentLikeable.peek() == true,
        playMode = controller.playMode.peek() ?: 0,
        privateFmMode = controller.privateFmActive.peek() == true,
        loading = controller.loading.peek() == true,
        coverBytes = controller.coverBytes.peek(),
        coverPath = controller.coverPath.peek(),
        coverSeed = controller.coverSeed.peek() ?: "",
        // Identity of the playing track only — deliberately WITHOUT the queue
        // index, so dragging the current song to a new slot doesn't replay the
        // cover transition. Netease tracks carry no filePath; title+artist
        // identifies them well enough for the crossfade trigger.
        trackKey = listOf(
            controller.currentFilePath.peek() ?: "",
            controller.title.peek() ?: "",
            controller.artist.peek() ?: ""
        ).joinToString("|"),
        queueIndex = controller.index.peek() ?: -1,
        loggedIn = controller.loggedIn.peek() == true,
        userName = controller.userName.peek() ?: "",
        qrImage = controller.qrImage.peek() ?: emptyList(),
        qrStatus = controller.qrStatus.peek() ?: 0,
        webLoginAvailable = controller.webLoginAvailable.peek() == true,
        webLoginBusy = controller.webLoginBusy.peek() == true,
        webLoginError = controller.webLoginError.peek() ?: "",
        webLoginSuccessRevision = controller.webLoginSuccessRevision.peek() ?: 0L,
        recommendations = cached("recommendations", controller.recommendations.peek()),
        recommendPlaylists = cached("recommendPlaylists", controller.recommendPlaylists.peek()),
        myPlaylists = cached("myPlaylists", controller.myPlaylists.peek()),
        tracks = cached("tracks", controller.tracks.peek()),
        searchRows = cached("searchRows", controller.searchRows.peek()),
        playlistTracks = cached("playlistTracks", controller.playlistTracks.peek()),
        queueTracks = cached("queueTracks", controller.queueTracks.peek()),
        lyrics = liveLyrics,
        // Lyrics are loaded asynchronously after playback starts. Use the core's
        // lyric revision (rather than the track/playback revision) so the lyric
        // column is re-anchored when an initially empty list is replaced by the
        // fetched list.
        lyricsRevision = controller.lyricsRevision.peek() ?: 0L,
        lyricIndex = lyricIndexForPosition(liveLyrics, liveLyricPositionMs),
        lyricOffsetMs = lyricOffsetMs,
        lyricsCoverOnly = controller.lyricsCoverOnly.peek() == true,
        coverModeManual = controller.coverModeManual.peek() == true,
        lyricFontSize = settingInt("lyricFontSize", 28),
        lyricFontWeight = settingInt("lyricFontWeight", 2),
        lyricLineSpacing = settingInt("lyricLineSpacing", 200),
        lyricSpring = settingBool("lyricSpring", true),
        lyricScale = settingBool("lyricScale", true),
        lyricGlow = settingBool("lyricGlow", true),
        lyricShadow = settingBool("lyricShadow", true),
        lyricLinearAnim = settingBool("lyricLinearAnim", false),
        lyricEdgeBlur = settingBool("lyricEdgeBlur", false),
        lyricMd3Color = settingBool("lyricMd3Color", false),
        lyricParticles = settingBool("lyricParticles", false),
        lyricProgressStyle = settingInt("lyricProgressStyle", 1),
        lyricBgMode = settingInt("lyricBgMode", 0),
        lyricBgStyle = settingInt("lyricBgStyle", 0),
        lyricCoverBackground = settingBool("lyricCoverBackground", true),
        playlistTitle = controller.playlistTitle.peek() ?: "",
        playlistCoverPath = controller.playlistCoverPath.peek() ?: "",
        artistName = controller.artistName.peek() ?: "",
        artistCoverPath = controller.artistCoverPath.peek() ?: "",
        artistBriefDesc = controller.artistBriefDesc.peek() ?: "",
        artistLoading = controller.artistLoading.peek() == true,
        artistSongs = cached("artistSongs", controller.artistSongs.peek()),
        artistAlbums = cached("artistAlbums", controller.artistAlbums.peek()),
        openArtistId = controller.openArtistId.peek() ?: 0L,
        albumTitle = controller.albumName.peek() ?: "",
        openAlbumId = controller.openAlbumId.peek() ?: 0L,
        albumCoverPath = controller.albumCoverPath.peek() ?: "",
        albumArtist = controller.albumArtistName.peek() ?: "",
        albumYear = controller.albumPublishYear.peek() ?: "",
        albumTracks = cached("albumTracks", controller.albumTracks.peek()),
        albumLoading = controller.albumLoading.peek() == true,
        lyricsLoading = controller.lyricsLoading.peek() == true,
        artistHeaderPath = controller.artistHeaderPath.peek() ?: "",
        biliSearchResults = controller.biliSearchResults.peek() ?: emptyList(),
        biliSearchLoading = controller.biliSearchLoading.peek() == true,
        biliLoggedIn = controller.biliLoggedIn.peek() == true,
        biliQrUrl = controller.biliQrUrl.peek() ?: "",
        biliQrStatus = controller.biliQrStatus.peek() ?: 0,
        biliError = controller.biliError.peek() ?: "",
        biliPlaying = controller.biliPlaying.peek() == true,
        biliChapterMarks = controller.biliChapterMarks.peek() ?: emptyList(),
        biliFavFolders = controller.biliFavFolders.peek() ?: emptyList(),
        biliFavLoading = controller.biliFavLoading.peek() == true,
        biliFavError = controller.biliFavError.peek() ?: "",
        biliFavItems = controller.biliFavItems.peek() ?: emptyList(),
        biliFavItemsLoading = controller.biliFavItemsLoading.peek() == true,
        biliFavItemsTitle = controller.biliFavItemsTitle.peek() ?: "",
        searchArtistId = controller.searchArtistId.peek() ?: 0L,
        searchArtistName = controller.searchArtistName.peek() ?: "",
        searchArtistCoverPath = controller.searchArtistCoverPath.peek() ?: "",
        searchArtistFollowed = controller.searchArtistFollowed.peek() == true,
        playlistLoading = controller.playlistLoading.peek() == true,
        homeLoading = controller.homeLoading.peek() == true,
        searchLoading = controller.searchLoading.peek() == true,
        dark = settings.resolvedDarkValue()
    )
}

@Composable
private fun rememberQPlayerColorScheme(seed: String, dark: Boolean, enabled: Boolean, paletteStyle: Int = 0, paletteChroma: Int = 1, settingsRevision: Int = 0): ColorScheme {
    val fallback = ComposeColor(0xFF6750A4)
    val target = remember(seed, enabled) {
        if (enabled) parseSeedColor(seed) ?: fallback else fallback
    }
    val primary by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "dynamic_seed_color"
    )
    return remember(primary, dark, paletteStyle, paletteChroma, settingsRevision) { qPlayerColorScheme(primary, dark, paletteStyle, paletteChroma) }
}

private fun parseSeedColor(seed: String): ComposeColor? {
    if (seed.isBlank()) return null
    return try {
        ComposeColor(Color.parseColor(seed))
    } catch (_: RuntimeException) {
        null
    }
}

private data class PlayingArtist(
    val name: String,
    val id: Long
)

/**
 * Rebuild the current track's credits without losing the id/name pairing.
 * Netease uses comma-separated ids and U+0001-separated names. When older
 * cached tracks lack the CSV fields, the legacy first-artist fields remain a
 * usable fallback.
 */
private fun playingArtists(state: PlayerUiState): List<PlayingArtist> {
    val names = if (state.artistNamesCsv.isNotBlank()) {
        state.artistNamesCsv.split('\u0001')
    } else if (state.artist.isNotBlank()) {
        state.artist.split(" / ")
    } else {
        emptyList()
    }
    val ids = if (state.artistIdsCsv.isNotBlank()) {
        state.artistIdsCsv.split(',').map { it.trim().toLongOrNull() ?: 0L }
    } else {
        listOf(state.artistId)
    }
    val count = maxOf(names.size, ids.size)
    return (0 until count).mapNotNull { index ->
        val name = names.getOrNull(index).orEmpty().trim()
        if (name.isBlank()) return@mapNotNull null
        PlayingArtist(name, ids.getOrNull(index) ?: 0L)
    }.ifEmpty {
        if (state.artist.isNotBlank()) listOf(PlayingArtist(state.artist, state.artistId)) else emptyList()
    }
}

@Composable
private fun PlayingArtistLinks(
    state: PlayerUiState,
    openArtist: (Long) -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit,
    centered: Boolean = true
) {
    val artists = remember(state.artist, state.artistId, state.artistIdsCsv, state.artistNamesCsv) {
        playingArtists(state)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        artists.forEachIndexed { index, artist ->
            if (index > 0) {
                Text(
                    text = " / ",
                    fontSize = fontSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = artist.name,
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = if (artist.id != 0L) {
                    Modifier.clickable { openArtist(artist.id) }
                } else Modifier
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Material You dynamic color: CIELAB / LCh tone system.
//
// The cover seed's hue drives five M3-style tonal palettes (primary,
// secondary at hue+24°, tertiary at hue+60°, neutral, neutral-variant) with
// expressive chroma, so surfaces visibly inherit the album art's hue. Colors
// are built at the spec's tone values (primary T40 light / T80 dark,
// containers T90/T30, five-level neutral surface containers) and chroma is
// gamut-mapped by binary search when a request falls outside sRGB.
// ---------------------------------------------------------------------------

private const val WP_X = 0.95047f // D65 white point
private const val WP_Z = 1.08883f

private fun linearizeSrgb(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

private fun delinearizeSrgb(c: Float): Float =
    if (c <= 0.0031308f) c * 12.92f
    else (1.055 * Math.pow(c.toDouble(), 1.0 / 2.4) - 0.055).toFloat().coerceIn(0f, 1f)

private fun rgbToXyz(r: Float, g: Float, b: Float): FloatArray {
    val rl = linearizeSrgb(r)
    val gl = linearizeSrgb(g)
    val bl = linearizeSrgb(b)
    return floatArrayOf(
        0.4124564f * rl + 0.3575761f * gl + 0.1804375f * bl,
        0.2126729f * rl + 0.7151522f * gl + 0.0721750f * bl,
        0.0193339f * rl + 0.1191920f * gl + 0.9503041f * bl
    )
}

/** sRGB from XYZ, or null when the color falls outside the gamut (pre-clamp). */
private fun xyzToRgb(x: Float, y: Float, z: Float): FloatArray? {
    val r = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
    val g = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
    val b = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z
    if (r < -0.001f || r > 1.001f || g < -0.001f || g > 1.001f || b < -0.001f || b > 1.001f) return null
    return floatArrayOf(
        delinearizeSrgb(r.coerceIn(0f, 1f)),
        delinearizeSrgb(g.coerceIn(0f, 1f)),
        delinearizeSrgb(b.coerceIn(0f, 1f))
    )
}

private fun labF(t: Float): Float =
    if (t > 216f / 24389f) Math.cbrt(t.toDouble()).toFloat() else (24389f / 27f * t + 16f) / 116f

private fun labFInv(t: Float): Float {
    val t3 = t * t * t
    return if (t3 > 216f / 24389f) t3 else (116f * t - 16f) * 27f / 24389f
}

private fun xyzToLab(x: Float, y: Float, z: Float): FloatArray {
    val fx = labF(x / WP_X)
    val fy = labF(y)
    val fz = labF(z / WP_Z)
    return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
}

private fun labToXyz(l: Float, a: Float, b: Float): FloatArray {
    val fy = (l + 16f) / 116f
    val fx = fy + a / 500f
    val fz = fy - b / 200f
    return floatArrayOf(labFInv(fx) * WP_X, labFInv(fy), labFInv(fz) * WP_Z)
}

/** L* / chroma / hue of a color; hue normalized to [0, 360). */
private fun seedLch(seed: ComposeColor): FloatArray {
    val xyz = rgbToXyz(seed.red, seed.green, seed.blue)
    val lab = xyzToLab(xyz[0], xyz[1], xyz[2])
    var h = Math.toDegrees(atan2(lab[2], lab[1]).toDouble()).toFloat()
    if (h < 0f) h += 360f
    return floatArrayOf(lab[0], sqrt(lab[1] * lab[1] + lab[2] * lab[2]), h)
}

/** Tone (L*) + chroma + hue → sRGB, shrinking chroma until the color is in gamut. */
private fun lchColor(tone: Float, chroma: Float, hue: Float): ComposeColor {
    val hRad = Math.toRadians(hue.toDouble())
    fun at(c: Float): ComposeColor? {
        val xyz = labToXyz(tone, (c * cos(hRad)).toFloat(), (c * sin(hRad)).toFloat())
        val rgb = xyzToRgb(xyz[0], xyz[1], xyz[2]) ?: return null
        return ComposeColor(red = rgb[0], green = rgb[1], blue = rgb[2])
    }
    at(chroma)?.let { return it }
    var lo = 0f
    var hi = chroma
    repeat(16) {
        val mid = (lo + hi) / 2f
        if (at(mid) != null) lo = mid else hi = mid
    }
    return at(lo) ?: ComposeColor(0xFF808080)
}

private fun qPlayerColorScheme(seed: ComposeColor, dark: Boolean, paletteStyle: Int = 0, paletteChroma: Int = 1): ColorScheme {
    val hue = seedLch(seed)[2]
    val hsv = FloatArray(3)
    Color.RGBToHSV((seed.red * 255f).toInt(), (seed.green * 255f).toInt(), (seed.blue * 255f).toInt(), hsv)
    val neutralCover = hsv[1] < 0.08f
    // Secondary keeps the SOURCE hue (as real M3 does). Rotating it +24° in
    // Lab space jumps hue families — a violet cover seed (H≈305°) lands its
    // containers in hot pink (H≈329°), which is exactly the pink nav pill seen
    // on device. Tertiary keeps the +60° expressive offset (lyric accent only).
    val hue2 = when (paletteStyle) { 2 -> (hue + 30f) % 360f; 3 -> (hue + 18f) % 360f; else -> hue }
    val hue3 = when (paletteStyle) { 1 -> (hue + 90f) % 360f; 2 -> (hue + 60f) % 360f; 3 -> (hue + 120f) % 360f; else -> (hue + 60f) % 360f }
    val boost = when (paletteStyle) { 0 -> 0.85f; 1 -> 1.25f; 2 -> 1.05f; else -> 1.15f } * (0.75f + paletteChroma * 0.25f)
    // Expressive chroma: punchy accents, neutral surfaces that still carry the hue.
    fun chroma(value: Float) = if (neutralCover) 0f else value
    fun p(tone: Float) = lchColor(tone, chroma(48f * boost), hue)
    fun s(tone: Float) = lchColor(tone, chroma((if (paletteStyle == 3) 32f else 24f) * boost), hue2)
    fun tr(tone: Float) = lchColor(tone, chroma((if (paletteStyle == 1) 40f else 32f) * boost), hue3)
    fun n(tone: Float) = lchColor(tone, chroma((if (paletteStyle == 2) 10f else 8f) * boost), hue)
    fun nv(tone: Float) = lchColor(tone, chroma(12f * boost), hue)
    return if (dark) {
        darkColorScheme(
            primary = p(80f), onPrimary = p(20f),
            primaryContainer = p(30f), onPrimaryContainer = p(90f),
            secondary = s(80f), onSecondary = s(20f),
            secondaryContainer = s(30f), onSecondaryContainer = s(90f),
            tertiary = tr(80f), onTertiary = tr(20f),
            tertiaryContainer = tr(30f), onTertiaryContainer = tr(90f),
            background = n(6f), onBackground = nv(90f),
            surface = n(6f), onSurface = nv(90f),
            surfaceVariant = nv(30f), onSurfaceVariant = nv(80f),
            surfaceContainerLowest = n(4f),
            surfaceContainerLow = n(10f),
            surfaceContainer = n(12f),
            surfaceContainerHigh = n(17f),
            surfaceContainerHighest = n(22f),
            outline = nv(60f), outlineVariant = nv(30f)
        )
    } else {
        lightColorScheme(
            primary = p(40f), onPrimary = p(100f),
            primaryContainer = p(90f), onPrimaryContainer = p(10f),
            secondary = s(40f), onSecondary = s(100f),
            secondaryContainer = s(90f), onSecondaryContainer = s(10f),
            tertiary = tr(40f), onTertiary = tr(100f),
            tertiaryContainer = tr(90f), onTertiaryContainer = tr(10f),
            background = n(98f), onBackground = nv(10f),
            surface = n(98f), onSurface = nv(10f),
            surfaceVariant = nv(90f), onSurfaceVariant = nv(30f),
            surfaceContainerLowest = n(100f),
            surfaceContainerLow = n(96f),
            surfaceContainer = n(94f),
            surfaceContainerHigh = n(92f),
            surfaceContainerHighest = n(90f),
            outline = nv(50f), outlineVariant = nv(80f)
        )
    }
}

@Composable
private fun rememberPlayerState(controller: PlayerController, settings: SettingsCore): PlayerUiState {
    var state by remember { mutableStateOf(controllerState(controller, settings)) }
    LaunchedEffect(controller) {
        while (true) {
            controller.pump()
            state = controllerState(controller, settings)
            // Compose reads the live lyric clock in controllerState, so word fill
            // and auto-follow remain smooth without changing the shared core's
            // observable 5 Hz position cadence.
            // Position-only updates used to rebuild the complete application
            // state 20 times a second. 10 Hz is enough for the progress/lyric
            // interpolators while leaving the render thread time to animate
            // and scroll large lists smoothly.
            delay(100)
        }
    }
    return state
}

private fun pageTransitionTransform(preset: Int, forward: Boolean): ContentTransform {
    // Ported from legado-with-MD3's MainActivity NavDisplay transitionSpec: only one
    // layer translates (the incoming page crosses a full width) while the other
    // moves a quarter and fades, and the motion is slow (480ms slide / 360ms fade)
    // so each frame covers less distance — a dropped frame stops being visible,
    // which is what "not stuttering" actually comes down to.
    val fosin = androidx.compose.animation.core.FastOutSlowInEasing
    val linOut = androidx.compose.animation.core.LinearOutSlowInEasing
    if (forward) {
        return (slideInHorizontally(
            animationSpec = tween(480, easing = fosin),
            initialOffsetX = { it }
        ) + fadeIn(tween(360, easing = linOut))).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(480, easing = fosin),
                targetOffsetX = { it / 4 }
            ) + fadeOut(tween(360, easing = linOut))
        )
    }
    // Pop: the page being returned to comes back from a quarter away, while the
    // page being left shrinks and fades.
    return (slideInHorizontally(
        animationSpec = tween(480, easing = fosin),
        initialOffsetX = { -it / 4 }
    ) + fadeIn(tween(360, easing = linOut))).togetherWith(
        scaleOut(targetScale = 0.8f, animationSpec = tween(480, easing = fosin)) +
            fadeOut(tween(360))
    )
}

@Suppress("unused")
private fun legacyPageTransitionTransform(preset: Int, forward: Boolean): ContentTransform {
    val easing = androidx.compose.animation.core.FastOutSlowInEasing
    return when (preset) {
        SettingsCatalog.PAGE_TRANSITION_FADE -> {
            fadeIn(tween(260, easing = easing)) togetherWith
                fadeOut(tween(180, easing = easing))
        }
        SettingsCatalog.PAGE_TRANSITION_SLIDE_HORIZONTAL -> {
            val inFrom = if (forward) 1 else -1
            val outTo = -inFrom
            (slideInHorizontally(
                initialOffsetX = { inFrom * it / 4 },
                animationSpec = tween(280, easing = easing)
            ) + fadeIn(tween(280, easing = easing))).togetherWith(
                slideOutHorizontally(
                    targetOffsetX = { outTo * it / 4 },
                    animationSpec = tween(180, easing = easing)
                ) + fadeOut(tween(180, easing = easing))
            )
        }
        SettingsCatalog.PAGE_TRANSITION_SLIDE_VERTICAL -> {
            val inFrom = if (forward) 1 else -1
            val outTo = -inFrom
            (slideInVertically(
                initialOffsetY = { inFrom * it / 4 },
                animationSpec = tween(300, easing = easing)
            ) + fadeIn(tween(280, easing = easing))).togetherWith(
                slideOutVertically(
                    targetOffsetY = { outTo * it / 4 },
                    animationSpec = tween(190, easing = easing)
                ) + fadeOut(tween(180, easing = easing))
            )
        }
        SettingsCatalog.PAGE_TRANSITION_NONE ->
            EnterTransition.None togetherWith ExitTransition.None
        else -> {
            // Zoom In / Out, matching the original default preset.
            (fadeIn(tween(300, easing = easing)) + scaleIn(
                initialScale = if (forward) 0.92f else 1.08f,
                animationSpec = tween(320, easing = easing)
            )).togetherWith(
                fadeOut(tween(220, easing = easing)) + scaleOut(
                    targetScale = if (forward) 1.08f else 0.92f,
                    animationSpec = tween(220, easing = easing)
                )
            )
        }
    }
}

private const val DETAIL_MOTION_MS = 360
private const val DETAIL_META_DELAY_MS = 60L
private const val DETAIL_CONTROLS_DELAY_MS = 110L
private const val DETAIL_EXIT_TOTAL_MS = DETAIL_MOTION_MS.toLong()

/** How long the 定时条 must be left alone before its countdown starts. */
private const val SLEEP_SETTLE_MS = 3_500L

/** Flight time of the shared container/cover, per the Material container transform. */
private const val SHARED_MOTION_MS = 460

/** Pages joined by a shared cover/container: their page transition stays a plain
 *  fade, because the official guidance is to keep everything that is not the
 *  shared element simple instead of running a second full-screen animation. */
private fun sharedCoverRoute(screen: ComposeScreen): Boolean =
    screen == ComposeScreen.ARTIST ||
        screen == ComposeScreen.ALBUM ||
        screen == ComposeScreen.PLAYLIST

@OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun QPlayerComposeApp(controller: PlayerController, settings: SettingsCore) {
    val state = rememberPlayerState(controller, settings)
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepRemaining by remember { mutableLongStateOf(0L) }
    // False while the countdown is still waiting for the user to finish sliding
    // the 定时条, so the control can show that it is holding, not counting.
    var sleepArmed by remember { mutableStateOf(false) }
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes <= 0) { sleepRemaining = 0L; sleepArmed = false; return@LaunchedEffect }
        // Show the chosen duration immediately, but do not start counting yet.
        // Every slider move changes sleepMinutes and therefore restarts this
        // effect, so the settle delay below can only expire once the slider has
        // been left alone for SLEEP_SETTLE_MS — each new slide re-arms it. The
        // countdown also always starts from the value that was picked last
        // instead of continuing from a stale remainder of an earlier setting.
        sleepRemaining = sleepMinutes * 60L
        sleepArmed = false
        delay(SLEEP_SETTLE_MS)
        sleepArmed = true
        var left = sleepRemaining
        while (left > 0L) {
            sleepRemaining = left
            delay(1000L)
            left--
        }
        // The controller has no pause() — only toggle(). Guarding on the actual
        // playing state keeps "到时自动暂停" a pause: a blind toggle() would
        // *start* playback when the user had already paused by hand before the
        // countdown expired.
        if (controller.playing.peek() == true) controller.toggle()
        sleepMinutes = 0
        sleepRemaining = 0L
        sleepArmed = false
    }
    // B站 session survives restarts: the core keeps the cookies in memory, so the
    // shell stores the cookie header in settings and hands it back on launch.
    // The cookie lives in a file next to the app's own data: a custom key in the
    // settings store is dropped on load because it is not part of the settings
    // catalog, which is why the login did not survive a restart.
    val biliContext = LocalContext.current
    val biliCookieFile = remember { java.io.File(biliContext.filesDir, "bili_cookies.txt") }
    LaunchedEffect(Unit) {
        val saved = runCatching { biliCookieFile.takeIf { it.exists() }?.readText() }.getOrNull()
        if (!saved.isNullOrBlank()) controller.restoreBiliSession(saved)
    }
    LaunchedEffect(state.biliLoggedIn) {
        if (state.biliLoggedIn) {
            runCatching { biliCookieFile.writeText(controller.biliClient().cookieHeader()) }
        }
    }
    // SettingsCore is also used by the legacy QML settings bridge and its
    // values are not Compose snapshot state.  Observe the small settings
    // surface used by the theme so palette changes are visible immediately,
    // without requiring a route change or activity restart.
    var paletteRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            paletteRevision++
        }
    }
    var navigationStack by remember {
        mutableStateOf(listOf(ComposeRoute(ComposeScreen.HOME)))
    }
    var loginOpen by remember { mutableStateOf(false) }
    // The card the user just tapped: its cover is the same bitmapped URL the
    // detail page will want, so handing it over means the shared element flies
    // with real artwork instead of an empty placeholder (and nothing re-loads on
    // landing).
    var openedAlbum by remember { mutableStateOf<Pair<Long, String>?>(null) }
    // Same hand-off for playlists: the row's own thumbnail is the bitmap the detail
    // page wants first, so the shared cover flies with real artwork instead of the
    // empty placeholder the page shows until the core publishes its cover path.
    var openedPlaylist by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var miniPlayerVisible by remember { mutableStateOf(true) }
    val miniScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) miniPlayerVisible = false
                else if (available.y > 1f) miniPlayerVisible = true
                return Offset.Zero
            }
        }
    }
    // True while the player detail page is on its way out. The page reads it to
    // play its own entrance animation backwards, so leaving looks like the
    // entrance running in reverse instead of the page sliding away intact.
    var detailClosing by remember { mutableStateOf(false) }
    val route = navigationStack.last()
    val screen = route.screen
    val pageTransitionPreset = settings.intOf(SettingsCatalog.PAGE_TRANSITION_KEY).coerceIn(
        SettingsCatalog.PAGE_TRANSITION_ZOOM,
        SettingsCatalog.PAGE_TRANSITION_NONE
    )

    fun navigateTo(target: ComposeRoute, asRoot: Boolean = false) {
        if (target == navigationStack.last()) return
        if (target.screen == ComposeScreen.LYRICS) detailClosing = false
        navigationStack = if (asRoot) listOf(target) else navigationStack + target
    }

    fun goBack() {
        navigationStack = when {
            navigationStack.size > 1 -> navigationStack.dropLast(1)
            screen != ComposeScreen.HOME -> listOf(ComposeRoute(ComposeScreen.HOME))
            else -> {
                controller.requestExit()
                navigationStack
            }
        }
    }

    // Leaves the detail page. The reversal of the entrance starts in the same
    // frame as the slide-down, so nothing pauses before the page starts moving.
    // Pop the route in the same state transaction as the closing flag. The
    // AnimatedContent target then changes immediately, while its outgoing
    // PlayerDetailScreen still receives closing=true and reverses its child
    // timeline during the very same 220 ms window.
    fun closeDetail() {
        if (detailClosing) return
        detailClosing = true
        goBack()
    }

    // Keep the outgoing detail content in closing mode until the outer page
    // transition has finished. This reset does not start another transition:
    // the route has already been popped, so the target remains the base page.
    LaunchedEffect(detailClosing) {
        if (!detailClosing) return@LaunchedEffect
        delay(DETAIL_EXIT_TOTAL_MS)
        detailClosing = false
    }

    BackHandler {
        when {
            loginOpen -> loginOpen = false
            else -> goBack()
        }
    }

    val scheme = rememberQPlayerColorScheme(
        seed = state.coverSeed,
        dark = state.dark,
        enabled = settings.bool("monet"),
        paletteStyle = settings.intOf("paletteStyle").coerceIn(0, 3),
        paletteChroma = settings.intOf("paletteChroma").coerceIn(0, 2),
        settingsRevision = paletteRevision
    )
    // Same as legado's LegadoTheme: the official expressive motion scheme drives
    // every Material component (sheets, menus, buttons) instead of per-call specs.
    MaterialTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive()
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
            // Official shared-element host, wrapping the whole navigation (Scaffold
            // included) so a container flying between pages is composited above the
            // bottom navigation and the mini player instead of being cut by them.
            SharedTransitionLayout {
            val sharedTransitionScope = this
            // The row that flew away gets its text back once the flight is over —
            // otherwise its title would stay hidden until the next tap.
            LaunchedEffect(sharedTransitionScope.isTransitionActive) {
                if (!sharedTransitionScope.isTransitionActive) leavingCoverKey = null
            }
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = route.screen == ComposeScreen.LYRICS && !detailClosing,
                transitionSpec = {
                    val motion = tween<Float>(
                        durationMillis = DETAIL_MOTION_MS,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                    if (targetState && !initialState) {
                        // The detail page grows out of the same bottom zone as
                        // the MiniPlayer.  Scale and translation share one
                        // clock, so the cover/container do not appear as two
                        // unrelated page transitions.
                        (slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight },
                            animationSpec = tween(DETAIL_MOTION_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.92f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                            animationSpec = motion
                        ) + fadeIn(motion)).togetherWith(
                            fadeOut(tween(DETAIL_MOTION_MS / 2))
                        )
                    } else {
                        // Exact reverse: the detail surface contracts toward
                        // the MiniPlayer anchor while the base surface fades
                        // in underneath it.
                        fadeIn(motion).togetherWith(
                            slideOutVertically(
                                targetOffsetY = { fullHeight -> fullHeight },
                                animationSpec = tween(DETAIL_MOTION_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + scaleOut(
                                targetScale = 0.92f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
                                animationSpec = motion
                            ) + fadeOut(motion)
                        )
                    }
                },
                label = "player_detail_screen_transition"
            ) { isDetail ->
                if (isDetail) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = scheme.background
                    ) {
                        PlayerDetailScreen(
                            state = state,
                            controller = controller,
                            openAlbum = { id ->
                                if (id != 0L) {
                                    controller.openAlbum(id)
                                    navigateTo(ComposeRoute(ComposeScreen.ALBUM, id))
                                }
                            },
                            openArtist = { id ->
                                if (id != 0L) {
                                    controller.openArtist(id)
                                    navigateTo(ComposeRoute(ComposeScreen.ARTIST, id))
                                }
                            },
                            close = ::closeDetail,
                            closing = detailClosing,
                            sleepMinutes = sleepMinutes,
                            sleepRemaining = sleepRemaining,
                            sleepArmed = sleepArmed,
                            onSleepMinutesChanged = { sleepMinutes = it }
                        )
                    }
                } else {
                    val isMainTab = screen in listOf(
                        ComposeScreen.HOME,
                        ComposeScreen.SEARCH,
                        ComposeScreen.LIBRARY,
                        ComposeScreen.LOCAL
                    )
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        containerColor = scheme.background,
                        topBar = {
                            ComposeTopBar(route, state,
                                canGoBack = navigationStack.size > 1,
                                onBack = ::goBack,
                                onQueue = { navigateTo(ComposeRoute(ComposeScreen.QUEUE)) },
                                onSettings = { navigateTo(ComposeRoute(ComposeScreen.SETTINGS)) },
                                onAccount = {
                                    if (state.loggedIn) navigateTo(ComposeRoute(ComposeScreen.ACCOUNT)) else loginOpen = true
                                })
                        },
                        // This is the actual MD3 bottom-bar slot. It is no
                        // longer drawn as an overlay or a rounded floating
                        // Surface; NavigationBar owns its height, insets, and
                        // selection indicator.
                        bottomBar = {
                            if (isMainTab) {
                                StandardBottomNav(
                                    modifier = Modifier.fillMaxWidth(),
                                    screen = screen,
                                    showLocalTab = settings.bool("showLocalTab"),
                                    onScreen = { navigateTo(ComposeRoute(it), asRoot = true) },
                                    onSearch = {
                                        navigateTo(ComposeRoute(ComposeScreen.SEARCH), asRoot = true)
                                    }
                                )
                            }
                        }
                    ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).nestedScroll(miniScrollConnection)) {
                        // Official shared-element API: the layout owns the overlay and
                        // the two scopes that a screen needs to bind an element.
                        AnimatedContent(
                            modifier = Modifier.fillMaxSize(),
                            targetState = route,
                            transitionSpec = {
                                // Restore the shared page-transition setting used by
                                // the original QML shell. Lyrics keeps its own
                                // player-sheet animation above.
                                if (sharedCoverRoute(targetState.screen) || sharedCoverRoute(initialState.screen)) {
                                    if (sharedCoverRoute(targetState.screen)) {
                                        // Opening: the page's own content stays invisible
                                        // for the first ~60% of the container's 450ms
                                        // expansion, then fades in over 150ms with a
                                        // slight rise — so nothing is ever stretched or
                                        // half-printed over the list it came from.
                                        (fadeIn(tween(150, delayMillis = 270)) +
                                            slideInVertically(
                                                animationSpec = tween(150, delayMillis = 270),
                                                initialOffsetY = { it / 28 }
                                            )) togetherWith fadeOut(tween(120))
                                    } else {
                                        // Going back: the list fades straight in while
                                        // the detail page leaves early, which is the
                                        // entrance played in reverse.
                                        fadeIn(tween(220)) togetherWith fadeOut(tween(140))
                                    }
                                } else {
                                    val forward = screenOrder.indexOf(targetState.screen) >= screenOrder.indexOf(initialState.screen)
                                    pageTransitionTransform(pageTransitionPreset, forward)
                                }
                            },
                            label = "page_transition"
                        ) { visibleRoute ->
                            when (visibleRoute.screen) {
                                ComposeScreen.HOME -> HomeScreen(
                                    state = state,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this,
                                    openPlaylist = { id, cover ->
                                        controller.openPlaylist(id)
                                        openedPlaylist = id to cover
                                        navigateTo(ComposeRoute(ComposeScreen.PLAYLIST, id))
                                    },
                                    playRecommendation = { index -> controller.playRecommendation(index) },
                                    refresh = { controller.loadHome() },
                                    controller = controller,
                                    settings = settings
                                )
                                ComposeScreen.SEARCH -> SearchScreen(
                                    state = state,
                                    controller = controller,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this
                                ) { id ->
                                    if (id != 0L) {
                                        controller.openArtist(id)
                                        navigateTo(ComposeRoute(ComposeScreen.ARTIST, id))
                                    }
                                }
                                ComposeScreen.LIBRARY -> LibraryScreen(
                                    state = state,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this,
                                    openLogin = { loginOpen = true },
                                    openBiliFav = {
                                        navigateTo(ComposeRoute(ComposeScreen.BILI_FAV))
                                    },
                                    openPlaylist = { id, cover ->
                                    controller.openPlaylist(id)
                                    openedPlaylist = id to cover
                                    navigateTo(ComposeRoute(ComposeScreen.PLAYLIST, id))
                                    },
                                    controller = controller
                                )
                                ComposeScreen.LOCAL -> LocalScreen(state, controller)
                                ComposeScreen.QUEUE -> QueueScreen(state, controller, sleepMinutes, sleepRemaining, sleepArmed) { sleepMinutes = it }
                                ComposeScreen.SETTINGS -> SettingsScreen(settings, controller)
                                ComposeScreen.ACCOUNT -> AccountScreen(state, controller)
                                ComposeScreen.BILI_FAV -> BiliFavFoldersScreen(
                                    state = state,
                                    controller = controller,
                                    onOpen = { mediaId ->
                                        // The route only carries the id; the title rides
                                        // along separately so the content page can label
                                        // its header and its load call.
                                        biliFavDetailTitle = state.biliFavFolders
                                            .firstOrNull { it.mediaId == mediaId }
                                            ?.title.orEmpty()
                                        navigateTo(ComposeRoute(ComposeScreen.BILI_FAV_DETAIL, mediaId))
                                    }
                                )
                                ComposeScreen.BILI_FAV_DETAIL -> BiliFavItemsScreen(
                                    state = state,
                                    controller = controller,
                                    mediaId = visibleRoute.id
                                )
                                ComposeScreen.PLAYLIST -> PlaylistScreen(
                                    state = state,
                                    controller = controller,
                                    playlistId = visibleRoute.id,
                                    fallbackCoverPath = openedPlaylist
                                        ?.takeIf { it.first == visibleRoute.id }
                                        ?.second.orEmpty(),
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this
                                )
                                ComposeScreen.ALBUM -> AlbumScreen(
                                    state = state,
                                    controller = controller,
                                    albumId = visibleRoute.id,
                                    fallbackCoverPath = openedAlbum
                                        ?.takeIf { it.first == visibleRoute.id }
                                        ?.second.orEmpty(),
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this
                                )
                                ComposeScreen.ARTIST -> ArtistScreen(
                                    state = state,
                                    controller = controller,
                                    artistId = visibleRoute.id,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = this,
                                    openAlbum = { id, cover ->
                                        controller.openAlbum(id)
                                        openedAlbum = id to cover
                                        navigateTo(ComposeRoute(ComposeScreen.ALBUM, id))
                                    }
                                )
                                else -> Unit
                            }
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                // Scaffold already reserves the NavigationBar
                                // area. Keep an explicit visual gap so the
                                // MiniPlayer does not touch the MD3 bar.
                                .padding(top = 8.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedVisibility(
                                // The player strip belongs to the content
                                // screens; the settings page is a pure form and
                                // must not carry it.
                                visible = miniPlayerVisible && state.title.isNotBlank() &&
                                    screen != ComposeScreen.SETTINGS,
                                enter = slideInVertically { it } + fadeIn(tween(300)),
                                exit = slideOutVertically { it } + fadeOut(tween(220))
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    // Private FM is a separate action row at the
                                    // screen edge, above MiniPlayer—not a button
                                    // attached to or covering the player.
                                        Box(
                                            modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .padding(end = 8.dp)
                                    ) {
                                        PrivateFmFab(
                                            modifier = Modifier.align(Alignment.TopEnd),
                                            onClick = {
                                                if (state.loggedIn) {
                                                    controller.startPrivateFm()
                                                    navigateTo(ComposeRoute(ComposeScreen.LYRICS))
                                                } else {
                                                    loginOpen = true
                                                }
                                            }
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    MiniPlayer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                        state = state,
                                        onOpen = { navigateTo(ComposeRoute(ComposeScreen.LYRICS)) },
                                        onSeek = { controller.seek(it) },
                                        onToggle = { controller.toggle() },
                                        onPrevious = { controller.prev() },
                                        onNext = { controller.next() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        // Drawn last among the app's own layers: above every screen and the mini player
        // strip, below the login dialog. This is the app's only video node.
        VideoLayer(state = state, controller = controller)
        if (loginOpen) LoginDialog(controller, state) { loginOpen = false }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeTopBar(
    route: ComposeRoute,
    state: PlayerUiState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit
) {
    val haptic = LocalView.current
    val title = when (route.screen) {
        ComposeScreen.HOME -> "推荐"
        ComposeScreen.SEARCH -> "搜索"
        ComposeScreen.LIBRARY -> "我的"
        ComposeScreen.LOCAL -> "本地"
        ComposeScreen.QUEUE -> "播放队列"
        ComposeScreen.SETTINGS -> "设置"
        ComposeScreen.ACCOUNT -> "账户"
        ComposeScreen.PLAYLIST -> state.playlistTitle.ifBlank { "歌单" }
        ComposeScreen.ALBUM -> state.albumTitle.ifBlank { "专辑" }
        ComposeScreen.ARTIST -> state.artistName.ifBlank { "歌手" }
        else -> "QPlayer"
    }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        ),
        title = { Text(title) },
        navigationIcon = {
            if (canGoBack) {
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            }
        },
        actions = {
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onQueue() }) {
                Icon(Icons.Default.QueueMusic, contentDescription = "播放队列")
            }
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onSettings() }) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onAccount() }) {
                Icon(if (state.loggedIn) Icons.Default.AccountCircle else Icons.Default.Login, contentDescription = "账户")
            }
        }
    )
}

@Composable
private fun StandardBottomNav(
    modifier: Modifier = Modifier,
    screen: ComposeScreen,
    showLocalTab: Boolean,
    onScreen: (ComposeScreen) -> Unit,
    onSearch: () -> Unit
) {
    val haptic = LocalView.current
    val items = listOf(
        Triple(ComposeScreen.HOME, Icons.Default.Home, "主页"),
        Triple(ComposeScreen.LIBRARY, Icons.Default.LibraryMusic, "歌单"),
        Triple(ComposeScreen.LOCAL, Icons.Default.Folder, "本地"),
        Triple(ComposeScreen.SEARCH, Icons.Default.Search, "搜索")
    )
    NavigationBar(
        modifier = modifier.height(80.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        items.forEach { (target, icon, label) ->
            val enabled = target != ComposeScreen.LOCAL || showLocalTab
            NavigationBarItem(
                selected = screen == target,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    if (target == ComposeScreen.SEARCH) onSearch()
                    else if (screen != target) onScreen(target)
                },
                enabled = enabled,
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label
                    )
                },
                label = { Text(label, maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            )
        }
    }
}

@Composable
private fun PrivateFmFab(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = "私人漫游")
    }
}

@Composable
private fun rememberHapticAction(action: () -> Unit): () -> Unit {
    val view = LocalView.current
    val latestAction = rememberUpdatedState(action)
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); latestAction.value() }
    }
}

@Composable
private fun WindowGlassBackdrop(
    modifier: Modifier = Modifier,
    radius: Float = 34f,
    overlay: ComposeColor? = null
) {
    val glassOverlay = overlay ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f)
    // Keep this layer Compose-only. A BlurView/AndroidView backdrop can attach
    // to the Compose root recursively during route transitions and crash on
    // some Android 16 renderers. The caller adds the blurred colour source
    // underneath; this Monet glass layer remains stable on every API level.
    @Suppress("UNUSED_VARIABLE")
    val unusedRadius = radius
    Box(modifier.background(glassOverlay))
}

@Composable
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun MiniPlayer(
    modifier: Modifier = Modifier,
    state: PlayerUiState,
    onOpen: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val coverBitmap = rememberCoverBitmap(state.coverBytes, state.coverPath)
    val miniHaptic = LocalView.current
    val hapticOpen = rememberHapticAction(onOpen)
    val openInteraction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(interactionSource = openInteraction, indication = null) { hapticOpen() },
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 1.dp,
        shadowElevation = 7.dp,
        // The root stays transparent so only the backdrop is blurred; the
        // controls and album art remain crisp.
        color = ComposeColor.Transparent
    ) {
        Box(Modifier.fillMaxSize()) {
            // Safe Compose-only backdrop. The cover is intentionally subdued
            // and blurred as the colour source; the foreground controls stay
            // crisp and this path is safe during Activity route transitions.
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(34.dp)
                        .graphicsLayer { alpha = 0.78f }
                )
            }
            WindowGlassBackdrop(
                modifier = Modifier.fillMaxSize(),
                radius = 42f,
                overlay = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.76f)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f))
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .pointerInput(state.durationMs) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val angle = Math.toDegrees(
                                atan2(
                                    (offset.y - center.y).toDouble(),
                                    (offset.x - center.x).toDouble()
                                )
                            ).toFloat()
                            val fraction = ((angle + 90f + 360f) % 360f) / 360f
                            val duration = state.durationMs.coerceAtLeast(1L)
                            onSeek((duration * fraction).roundToInt().toLong())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // The ring is the music queue's progress. A B站 video is not part of it
                // and carries its own bar in full-screen, so the ring is left off while
                // one is playing.
                if (!state.biliPlaying) {
                    CircularCoverProgress(
                        state = state,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AnimatedContent(
                    targetState = state.trackKey to coverBitmap,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.9f, animationSpec = tween(240))) togetherWith
                            (fadeOut(tween(160)) + scaleOut(targetScale = 1.05f, animationSpec = tween(160)))
                    },
                    label = "mini_cover_transition"
                ) { (_, bitmap) ->
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "专辑封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.Album,
                                contentDescription = null,
                                modifier = Modifier.padding(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(7.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    state.title.ifBlank { "未播放" },
                    maxLines = 1,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 600
                    )
                )
                Text(
                    state.artist,
                    maxLines = 1,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(5.dp))
            val playCorner by animateDpAsState(
                targetValue = if (state.playing) 12.dp else 16.dp,
                animationSpec = tween(255),
                label = "mini_play_corner"
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(playCorner))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = { miniHaptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onToggle() }),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.playing,
                    transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                    label = "mini_play_icon"
                ) { playing ->
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(5.dp))
            if (!state.privateFmMode) {
                PixelMiniControl(
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    enabled = true,
                    onClick = { miniHaptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onPrevious() }
                )
                Spacer(Modifier.width(5.dp))
            }
            PixelMiniControl(
                icon = Icons.Default.SkipNext,
                contentDescription = "下一首",
                enabled = true,
                onClick = { miniHaptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onNext() }
            )
            }
        }
    }
}

@Composable
private fun CircularCoverProgress(
    state: PlayerUiState,
    modifier: Modifier = Modifier
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    val progress = (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val density = LocalDensity.current
    val trackColor = MaterialTheme.colorScheme.secondaryContainer
    val progressColor = MaterialTheme.colorScheme.primary
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(260, easing = LinearEasing),
        label = "cover_ring_progress"
    )
    val infinite = rememberInfiniteTransition(label = "cover_ring_loading")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "cover_ring_wave_phase"
    )
    val loadingRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "cover_ring_loading_rotation"
    )
    Canvas(modifier = modifier) {
        val strokeWidth = with(density) { 3.dp.toPx() }
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        // Keep a visible M3 Expressive separation at the active/inactive seam.
        val gap = 14f
        val start = -90f + gap / 2f
        val sweep = 360f - gap
        val progressSweep = (sweep * animatedProgress - gap / 2f).coerceAtLeast(0f)
        // Keep a short active wave visible from the first rendered frame of a
        // new playback. Its length is independent from total track duration.
        val playedSweep = if (state.playing) maxOf(progressSweep, 24f) else progressSweep
        val unplayedStart = start + playedSweep + gap
        val unplayedSweep = (start + sweep - unplayedStart).coerceAtLeast(0f)
        drawArc(
            color = trackColor,
            startAngle = unplayedStart,
            sweepAngle = unplayedSweep,
            useCenter = false,
            style = stroke
        )
        if (state.loading) {
            drawArc(
                color = progressColor,
                startAngle = loadingRotation - 90f,
                sweepAngle = 86f,
                useCenter = false,
                style = stroke
            )
        } else if (playedSweep > 0f) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) / 2f - strokeWidth / 2f
            val wavePath = Path()
            val steps = maxOf(12, (playedSweep * 2f).roundToInt())
            repeat(steps + 1) { index ->
                val fraction = index.toFloat() / steps.toFloat()
                val angle = Math.toRadians((start + playedSweep * fraction).toDouble())
                // Fixed wavelength: increasing progress adds waves instead of
                // stretching the existing wave, so the amplitude stays stable.
                val wave = sin(
                    wavePhase + (playedSweep * fraction / 30f) * 2f * PI.toFloat()
                ) * if (state.playing) 1.72f else 0.52f
                val pointRadius = radius + wave
                val point = Offset(
                    center.x + cos(angle).toFloat() * pointRadius,
                    center.y + sin(angle).toFloat() * pointRadius
                )
                if (index == 0) wavePath.moveTo(point.x, point.y)
                else wavePath.lineTo(point.x, point.y)
            }
            drawPath(
                path = wavePath,
                color = progressColor,
                style = stroke
            )
        }
    }
}

@Composable
private fun PixelMiniControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp)
        )
    }
}

private val coverBitmapCache = object : LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(48 * 1024) {
    override fun sizeOf(
        key: String,
        value: androidx.compose.ui.graphics.ImageBitmap
    ): Int = ((value.width.toLong() * value.height.toLong() * 4L) / 1024L)
        .coerceAtLeast(1L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private fun httpsCoverSource(source: String): String =
    if (source.startsWith("http://", ignoreCase = true)) "https://${source.substring(7)}" else source

private fun decodeRemoteCover(source: String): androidx.compose.ui.graphics.ImageBitmap? {
    val connection = try {
        URL(source).openConnection() as? HttpURLConnection
    } catch (_: Exception) {
        null
    } ?: return null
    return try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "QPlayer/1.3 Android")
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun rememberCoverBitmap(bytes: ByteArray?, path: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val remoteSource = path?.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }?.let(::httpsCoverSource)
    val key = remember(bytes, path) {
        bytes?.let { "bytes_${it.contentHashCode()}_${it.size}" }
            ?: remoteSource?.let { "url_$it" }
            ?: path?.takeIf { it.isNotBlank() }?.let { "path_$it" }
    } ?: return null

    var bitmap by remember(key) {
        mutableStateOf(synchronized(coverBitmapCache) { coverBitmapCache.get(key) })
    }

    LaunchedEffect(key) {
        if (bitmap == null) {
            val decoded = withContext(Dispatchers.IO) {
                val decoded = bytes?.let { b ->
                    try {
                        BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                } ?: remoteSource?.let(::decodeRemoteCover) ?: path?.let { p ->
                    try {
                        if (File(p).exists()) BitmapFactory.decodeFile(p)?.asImageBitmap() else null
                    } catch (_: Exception) { null }
                }
                decoded
            }
            if (decoded != null) {
                synchronized(coverBitmapCache) { coverBitmapCache.put(key, decoded) }
                bitmap = decoded
            }
        }
    }
    return bitmap
}

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
private fun HomeScreen(
    state: PlayerUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    openPlaylist: (Long, String) -> Unit,
    playRecommendation: (Int) -> Unit,
    refresh: () -> Unit,
    controller: PlayerController? = null,
    settings: SettingsCore? = null
) {
    var aiText by remember { mutableStateOf("") }
    var aiOpen by remember { mutableStateOf(false) }
    var aiPreferenceMode by remember { mutableStateOf(false) }
    var aiPreferenceCount by remember { mutableFloatStateOf(20f) }
    var aiMode by remember { mutableStateOf<Boolean?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf("") }
    var aiSongs by remember { mutableStateOf(emptyList<dev.t1m3.qplayer.netease.dto.NeteaseSong>()) }
    var aiProgress by remember { mutableStateOf("") }
    var aiSummary by remember { mutableStateOf("") }
    var aiDetails by remember { mutableStateOf("") }
    LaunchedEffect(aiOpen, controller) {
        while (aiOpen && controller != null) {
            controller.pump()
            aiLoading = controller.aiLoading.peek() == true
            aiError = controller.aiError.peek() ?: ""
            aiSongs = controller.aiSongs.peek() ?: emptyList()
            aiProgress = controller.aiProgress.peek() ?: ""
            aiSummary = controller.aiSummary.peek() ?: ""
            aiDetails = controller.aiDetails.peek() ?: ""
            delay(150)
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(if (state.userName.isBlank()) "你好" else "你好，${state.userName}", fontSize = 27.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(aiText, { aiText = it }, Modifier.weight(1f), placeholder = { Text("今天想听点什么呢？告诉你的ai吧！") }, singleLine = true, shape = RoundedCornerShape(18.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                if (aiText.isNotBlank()) {
                                    aiPreferenceMode = false; aiMode = null; aiLoading = false
                                    aiError = ""; aiSongs = emptyList(); aiProgress = ""
                                    aiSummary = ""; aiDetails = ""; aiOpen = true
                                }
                            },
                            onLongClick = {
                                aiPreferenceMode = true; aiMode = true; aiLoading = false
                                aiPreferenceCount = (settings?.intOf("aiTasteCount") ?: 20).coerceIn(1, 40).toFloat()
                                aiError = ""; aiSongs = emptyList(); aiProgress = ""
                                aiSummary = ""; aiDetails = ""; aiOpen = true
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.AutoAwesome, contentDescription = "打开 AI DJ") }
            }
        }
        item {
            SectionTitle("推荐歌单")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.recommendPlaylists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = {
                            leavingCoverKey = "playlist_card_${playlist.id}"
                            openPlaylist(playlist.id, playlist.coverThumbPath ?: playlist.coverUrl ?: "")
                        }
                    )
                }
            }
        }
        item { SectionTitle("每日推荐") }
        itemsIndexed(
            state.recommendations,
            key = { index, song -> "recommend_${song.id}_$index" },
            contentType = { _, _ -> "song" }
        ) { index, song ->
            SongRow(
                title = song.name ?: "未知歌曲",
                artist = song.artist ?: "未知歌手",
                coverPath = song.coverThumbPath ?: song.coverUrl,
                onClick = { playRecommendation(index) },
                onLongPressQueue = { controller?.enqueueNeteaseSong(song) }
            )
        }
        if (state.recommendPlaylists.isEmpty() && state.recommendations.isEmpty()) {
            item {
                // Same rule as the playlist/album/artist pages: while the fetch
                // is in flight the page waits with the expressive loader; once
                // it ends (success or failure) the empty state with its refresh
                // button takes over. Kept as an item instead of an early return
                // so the AI DJ dialog this screen owns is never unmounted.
                if (state.homeLoading) {
                    LoadingPlaceholder(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        text = "正在加载推荐歌单…"
                    )
                } else {
                    EmptyState("暂无推荐内容", "点击刷新后重新加载", refresh)
                }
            }
        }
    }
    if (aiOpen) AlertDialog(
        onDismissRequest = { aiOpen = false; aiPreferenceMode = false },
        title = { Text("AI DJ") },
        text = { Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!aiPreferenceMode) {
                Text("请选择生成方式")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = aiMode == false, onClick = { aiMode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("创建歌单") }
                    SegmentedButton(selected = aiMode == true, onClick = { aiMode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("生成播放列表") }
                }
            } else {
                Text("随机偏好推荐")
                Text("推荐数量：${aiPreferenceCount.toInt()} 首")
                Slider(
                    value = aiPreferenceCount,
                    onValueChange = { aiPreferenceCount = it },
                    valueRange = 1f..40f,
                    steps = 38
                )
                Button(onClick = {
                    aiLoading = true
                    controller?.generateAiPlaylist(
                        settings?.str("aiBaseUrl") ?: "", settings?.str("aiApiKey") ?: "",
                        settings?.str("aiModel") ?: "",
                        "严格根据提供的听歌样本分析偏好，随机生成多种类型的歌曲，必须返回不同风格的真实歌曲，不要重复样本。",
                        aiPreferenceCount.toInt().coerceIn(1, 40), true, false, false, "", "",
                        settings?.bool("aiForceKnowledge") == true, true
                    )
                }, enabled = !aiLoading) { Text("开始生成") }
            }
            if (!aiPreferenceMode && aiMode != null) Button(onClick = {
                controller?.generateAiPlaylist(
                    settings?.str("aiBaseUrl") ?: "", settings?.str("aiApiKey") ?: "",
                    settings?.str("aiModel") ?: "", aiText, extractAiCount(aiText),
                    aiMode == true, settings?.bool("aiExcludeLiked") == true,
                    false, "", "",
                    settings?.bool("aiForceKnowledge") == true, false
                )
            }, enabled = !aiLoading) { Text("开始生成") }
            if (aiLoading) {
                // While the AI works this is the only thing the dialog shows: the
                // contained expressive indicator, sized up. The old "正在让 AI
                // 分析…" progress lines and the "Mixing..." label are gone.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator(size = 72.dp, withContainer = true)
                }
            }
            if (aiError.isNotBlank()) Text(aiError, color = MaterialTheme.colorScheme.error)
            if (aiSummary.isNotBlank()) Text("AI 推荐说明：\n$aiSummary")
            if (aiDetails.isNotBlank()) {
                Text("AI 返回的推荐理由：")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    aiDetails.lines().forEach { line ->
                        val isSongHeader = line.isNotBlank() && !line.startsWith("  ")
                        Text(line, fontWeight = if (isSongHeader) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (settings?.bool("aiShowOutput") == true && aiError.isNotBlank()) {
                Text("AI 原始输出/错误信息：", fontWeight = FontWeight.Bold)
                Text(aiError, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
            }
        } }, confirmButton = {}
    )
}

private fun extractAiCount(request: String): Int {
    val match = Regex("(\\d{1,3})\\s*(首|首歌|songs?)?", RegexOption.IGNORE_CASE).find(request)
    return (match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10).coerceIn(1, 100)
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun SearchScreen(
    state: PlayerUiState,
    controller: PlayerController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    openPlaylist: (Long, String) -> Unit = { _, _ -> },
    openArtist: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var searchTab by remember { mutableIntStateOf(0) }   // 0 网易云 / 1 B站
    val listState = rememberLazyListState()
    val neteaseRows = if (searchTab == 0) state.searchRows else emptyList()
    val biliRows = if (searchTab == 1) state.biliSearchResults else emptyList()
    // A new query replaces the results, but the list keeps its old scroll offset —
    // which left the pinned artist card hidden above the fold until the user
    // scrolled up. Put the list back at the top whenever the query changes hands.
    LaunchedEffect(state.searchArtistId, state.searchRows.size) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }
    fun submitSearch(value: String = query) {
        if (value.isBlank()) return
        controller.search(value)
        controller.searchLocal(value)
        controller.searchCustom(value)
        // Best artist match for the same query, shown pinned above the songs.
        controller.searchArtist(value)
        controller.searchBili(value)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { value -> query = value; submitSearch(value) }, Modifier.weight(1f), label = { Text("搜索歌曲、专辑或歌手") }, singleLine = true)
            IconButton(onClick = {
                submitSearch()
            }) { Icon(Icons.Default.Search, contentDescription = "搜索") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchSourceTab("网易云", searchTab == 0, Modifier.weight(1f)) { searchTab = 0 }
            SearchSourceTab("B站", searchTab == 1, Modifier.weight(1f)) { searchTab = 1 }
        }
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (state.searchArtistId != 0L && searchTab == 0) {
                item(key = "search_artist_pinned") {
                    SearchArtistCard(
                        state = state,
                        onOpen = { openArtist(state.searchArtistId) },
                        onFollow = { controller.toggleSearchArtistFollow() }
                    )
                }
            }
            itemsIndexed(
                neteaseRows,
                key = { index, row -> "search_${row.kind}_${row.id}_$index" },
                contentType = { _, _ -> "song" }
            ) { index, row ->
                SongRow(
                    title = row.name ?: "未知歌曲",
                    artist = row.artist ?: row.kindLabel ?: "",
                    coverPath = row.coverThumbPath,
                    onClick = { controller.playSearchRow(index) },
                    onLongPressQueue = { controller.enqueueSearchRow(index) }
                )
            }
            itemsIndexed(
                biliRows,
                key = { index, video -> "bili_${video.bvid}_$index" },
                contentType = { _, _ -> "bili" }
            ) { _, video ->
                BiliVideoRow(video) { controller.playBiliCollection(video) }
            }
        }
    }
}

/** 两个搜索来源的切换按钮。 */
@Composable
private fun SearchSourceTab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一行 B 站搜索结果：左侧 16:9 缩略图（右下角时长角标），右侧标题 + UP 主 + 播放量，
 * 排列方式照 PiliPlusX 的列表。
 */
@Composable
private fun BiliVideoRow(
    video: dev.t1m3.qplayer.bili.BiliClient.BiliVideo,
    onClick: () -> Unit
) {
    val cover = rememberCoverBitmap(null, video.coverUrl)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(ShapeMedium)
            .clickable(onClick = onClick),
        shape = ShapeMedium,
        color = ComposeColor.Transparent
    ) {
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            Box(Modifier.width(140.dp).height(88.dp).clip(ShapeSmall).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                if (cover != null) {
                    Image(bitmap = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (video.durationText.isNotBlank()) {
                    Text(
                        video.durationText,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ComposeColor.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = ComposeColor.White,
                        fontSize = 11.sp
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    video.title.ifBlank { "无标题" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    video.author,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.playCount > 0) {
                    Text(
                        "播放 " + formatCount(video.playCount),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatCount(value: Long): String = when {
    value >= 100_000_000L -> String.format(java.util.Locale.ROOT, "%.1f亿", value / 100_000_000.0)
    value >= 10_000L -> String.format(java.util.Locale.ROOT, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

/**
 * The artist that matches the current query, pinned above the song results:
 * avatar on the left, name in the middle, follow button on the right. The button
 * reads the account's real follow state (see PlayerController.searchArtist) and
 * writes back through the NetEase artist sub/unsub endpoints.
 */
@Composable
private fun SearchArtistCard(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onFollow: () -> Unit
) {
    val avatar = rememberCoverBitmap(null, state.searchArtistCoverPath)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clickable { onOpen() }
,
        shape = ShapeLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = "歌手头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    state.searchArtistName.ifBlank { "歌手" },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "歌手",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.searchArtistFollowed) {
                OutlinedButton(onClick = onFollow) { Text("已关注") }
            } else {
                Button(onClick = onFollow) { Text("关注") }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryScreen(
    state: PlayerUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    openLogin: () -> Unit,
    openPlaylist: (Long, String) -> Unit,
    openBiliFav: () -> Unit,
    controller: PlayerController,
) {
    var createOpen by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    // A B站-only session still gets in: the 收藏夹 entry below is theirs to open, and
    // the netease list would simply come back empty.
    if (!state.loggedIn && !state.biliLoggedIn) {
        EmptyState("登录后查看你的歌单", "使用网易云账号登录", openLogin)
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "bili_fav_entry") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeMedium)
                    .clickable { openBiliFav() },
                shape = ShapeMedium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("B站收藏夹", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (state.biliLoggedIn) "查看并播放收藏的 B站视频"
                            else "登录 B站 后可查看",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "›",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("我的歌单")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { createOpen = true }) { Icon(Icons.Default.Add, "创建歌单") }
            }
        }
        items(
            state.myPlaylists,
            key = { playlist -> "playlist_${playlist.id}" },
            contentType = { "playlist" }
        ) { playlist ->
            PlaylistListRow(
                playlist = playlist,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = {
                    leavingCoverKey = "playlist_row_${playlist.id}"
                    openPlaylist(playlist.id, playlist.coverThumbPath ?: playlist.coverUrl ?: "")
                },
                onDelete = { controller.deletePlaylist(playlist.id) }
            )
        }
    }
    if (createOpen) AlertDialog(
        onDismissRequest = { createOpen = false },
        title = { Text("创建歌单") },
        text = { OutlinedTextField(createName, { createName = it }, label = { Text("歌单名称") }, singleLine = true) },
        confirmButton = { TextButton(enabled = createName.isNotBlank(), onClick = { controller.createPlaylist(createName); createName = ""; createOpen = false }) { Text("创建") } },
        dismissButton = { TextButton(onClick = { createOpen = false }) { Text("取消") } }
    )
}

@Composable
private fun LocalScreen(state: PlayerUiState, controller: PlayerController) {
    if (state.tracks.isEmpty()) {
        EmptyState("还没有本地音乐", "授权音乐权限后会自动扫描") { }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item { Text("本地音乐 · ${state.tracks.size}", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        itemsIndexed(
            state.tracks,
            key = { index, track -> "local_${track.contentUri ?: track.filePath ?: index}" },
            contentType = { _, _ -> "song" }
        ) { index, track ->
            SongRow(
                title = track.title ?: "未知歌曲",
                artist = track.artist ?: "未知歌手",
                coverBytes = track.coverBytes,
                coverPath = track.coverThumbPath ?: track.coverLocalPath,
                onClick = { controller.play(index) },
                onLongPressQueue = { controller.enqueueTrack(track) }
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun DraggableQueueList(
    state: PlayerUiState,
    controller: PlayerController,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    staggeredEntry: Boolean = false,
    header: @Composable () -> Unit = {},
    emptyContent: @Composable () -> Unit = {
        Text("播放队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
) {
    // One draggable queue list shared by the player sheet and the queue page.
    // Works identically for netease / local / custom-API tracks — the queue is a
    // flat List<Track>, and moveInQueue is source-agnostic.
    //
    // Reordering uses sh.calvin.reorderable (the same library PixelPlayer uses):
    // it owns gesture tracking, swap-while-scrolling and edge auto-scroll — all
    // the hand-rolled versions of those fought each other. The polled controller
    // state only refreshes every ~200ms, so swaps are mirrored into a local list
    // that renders immediately and is dropped once the poll catches up.
    val controllerQueue = state.queueTracks
    var localQueue by remember { mutableStateOf<List<Track>?>(null) }
    val queueList = localQueue ?: controllerQueue
    LaunchedEffect(controllerQueue, localQueue) {
        val mirror = localQueue ?: return@LaunchedEffect
        if (mirror.size == controllerQueue.size && mirror.indices.all { mirror[it] === controllerQueue[it] }) {
            localQueue = null
        }
    }

    // Stable per-song identity — NEVER the slot index. A positional key makes
    // every swap recreate the moved rows, which drops the reorderable gesture
    // mid-drag (the "moves one slot per grab" bug) and skips placement animations.
    fun songKeyOf(t: Track): String = when {
        t.neteaseId != 0L -> "nid_${t.neteaseId}"
        !t.filePath.isNullOrBlank() -> "fp_${t.filePath}"
        !t.contentUri.isNullOrBlank() -> "cu_${t.contentUri}"
        // bvid + cid identifies one part exactly. Without this a BILI queue falls
        // through to title+artist, and two parts sharing a title (a multi-P video
        // whose pages are unnamed) produce a duplicate key -- which Compose rejects
        // with an IllegalArgumentException while composing the queue list.
        !t.biliBvid.isNullOrBlank() -> "bv_${t.biliBvid}_${t.biliCid}"
        else -> "ti_${t.title}_${t.artist}"
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            // Keys are stable song ids; resolve them against the CURRENT list
            // (the mirror is already up to date from the previous move).
            val base = ArrayList(localQueue ?: controllerQueue)
            val fromIdx = base.indexOfFirst { songKeyOf(it) == from.key.toString() }
            val toIdx = base.indexOfFirst { songKeyOf(it) == to.key.toString() }
            if (fromIdx < 0 || toIdx < 0 || fromIdx == toIdx) return@rememberReorderableLazyListState
            // Public API: adjusts the playing index and republishes queueTracks;
            // the local mirror makes the swap render this frame.
            controller.moveInQueue(fromIdx, toIdx)
            base.add(toIdx, base.removeAt(fromIdx))
            localQueue = ArrayList(base)
        }
    )

    // Stagger window: the entry cascade only plays for a short moment right
    // after the sheet opens. Rows that enter composition LATER (scroll loading)
    // appear instantly — otherwise scrolling down makes each new row wait out
    // its own stagger delay before it "pops" in.
    var staggerOpen by remember { mutableStateOf(staggeredEntry) }
    LaunchedEffect(staggeredEntry) {
        if (staggeredEntry) {
            staggerOpen = true
            delay(1800L)
            staggerOpen = false
        } else {
            staggerOpen = false
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "queue_header") { header() }
        itemsIndexed(
            items = queueList,
            key = { _, tr -> songKeyOf(tr) }
        ) { index, track ->
            // Exact slot match against the controller's index — string
            // comparisons break when a title repeats.
            val isPlaying = state.queueIndex == index
            val liveIndex by rememberUpdatedState(index)

            // Staggered entry (sheet only), windowed by staggerOpen above.
            val enterStaggered = staggeredEntry && staggerOpen
            val rowEnter = remember(enterStaggered) { Animatable(if (enterStaggered) 0f else 1f) }
            LaunchedEffect(enterStaggered) {
                if (enterStaggered) {
                    delay(minOf(index * 30L, 600L))
                    rowEnter.animateTo(
                        1f,
                        tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    )
                } else {
                    rowEnter.snapTo(1f)
                }
            }

            // PixelPlayer QueuePlaylistSongItem styling: the row surface and its
            // album art morph their corner radius between the current song (60dp,
            // a "pill") and ordinary rows (22dp / 8dp). The current song is also
            // marked by primary-coloured bold text and an animated equalizer.
            val cornerRadius by animateDpAsState(
                targetValue = if (isPlaying) 60.dp else 22.dp,
                animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "queue_row_corner"
            )
            val albumCornerRadius by animateDpAsState(
                targetValue = if (isPlaying) 60.dp else 8.dp,
                animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "queue_album_corner"
            )
            val itemShape = RoundedCornerShape(cornerRadius)
            val albumShape = RoundedCornerShape(albumCornerRadius)

            ReorderableItem(
                state = reorderableState,
                key = songKeyOf(track),
                enabled = true,
                // The reorderable library handles item placement animation for
                // this Compose version; animateItemPlacement is not available
                // in the project's pinned foundation dependency.
                animateItemModifier = Modifier
            ) { isDragging ->
                val dragScale by animateFloatAsState(
                    targetValue = if (isDragging) 1.02f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "queue_drag_scale"
                )

            Surface(
                onClick = { controller.playQueueIndex(liveIndex) },
                shape = itemShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = if (isDragging) 4.dp else 1.dp,
                shadowElevation = if (isDragging) 4.dp else 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 100f else 1f)
                    .graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                        val enterOffset = (1f - rowEnter.value) * 64f
                        translationY = if (isDragging) 0f else enterOffset
                        alpha = rowEnter.value
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(6.dp))

                    // Track Cover Thumbnail — corners morph with the current-song state.
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = albumShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        val rowCover = rememberCoverBitmap(track.coverBytes, track.coverThumbPath ?: track.coverLocalPath)
                        if (rowCover != null) {
                            Image(
                                bitmap = rowCover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Album,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Song Title, Artist · Album
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title ?: "未知歌曲",
                            fontSize = 16.sp,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.artist ?: "未知歌手"}${if (!track.album.isNullOrBlank()) " · ${track.album}" else ""}",
                            fontSize = 14.sp,
                            color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    if (isPlaying) {
                        PlayingEqIcon(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(width = 18.dp, height = 16.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            isPlaying = state.playing
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Reorderable drag handle — the library drives the whole
                    // gesture (tracking, swap-on-scroll, edge auto-scroll).
                    // 2.2.0 exposes this as a ReorderableCollectionItemScope
                    // member (draggableHandle), callable inside ReorderableItem.
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "拖拽排序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(28.dp)
                            .draggableHandle(
                                enabled = true,
                                interactionSource = remember { MutableInteractionSource() },
                                onDragStarted = { },
                                onDragStopped = { }
                            )
                    )

                    // Remove Button
                    IconButton(
                        onClick = { controller.removeFromQueue(liveIndex) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            }
        }
        if (queueList.isEmpty()) {
            item(key = "queue_empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { emptyContent() }
            }
        }
    }
}

@Composable
private fun QueueScreen(state: PlayerUiState, controller: PlayerController, sleepMinutes: Int, sleepRemaining: Long, sleepArmed: Boolean, onMinutesChanged: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "播放队列 · ${state.queueTracks.size}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        DraggableQueueList(
            state = state,
            controller = controller,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            staggeredEntry = false,
            emptyContent = { EmptyState("播放队列为空", "从歌曲列表选择一首开始播放") { } }
        )
        SleepTimerControl(sleepMinutes, sleepRemaining, onMinutesChanged, sleepArmed)
    }
}

@Composable
private fun SleepTimerControl(minutes: Int, remaining: Long, onMinutesChanged: (Int) -> Unit, armed: Boolean = true) {
    val haptic = rememberHapticAction { }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            when {
                minutes == 0 -> "定时播放：未开启"
                !armed -> "定时播放：已设置 ${minutes} 分钟，滑动结束后开始计时…"
                else -> "定时播放：${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')} 后暂停"
            },
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = minutes.toFloat(),
            onValueChange = { value -> haptic(); onMinutesChanged(value.roundToInt().coerceIn(0, 120)) },
            valueRange = 0f..120f,
            steps = 119,
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
        Text(
            if (minutes == 0) "滑动设置 1–120 分钟"
            else if (!armed) "停手 ${SLEEP_SETTLE_MS / 1000} 秒后开始倒计时"
            else "已设置 ${minutes} 分钟，到时自动暂停播放",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlaylistScreen(
    state: PlayerUiState,
    controller: PlayerController,
    playlistId: Long,
    fallbackCoverPath: String = "",
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // The core flags an in-flight playlist fetch; until the first track lands
    // there is nothing to show, so the page waits with the expressive loader
    // instead of an empty "0 首歌曲" header (the QML shell does the same with
    // player.playlistLoading).
    val sharedMotion = tween<androidx.compose.ui.geometry.Rect>(
        durationMillis = SHARED_MOTION_MS,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            "playlist_container_$playlistId"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(0.dp))
                    )
                }
            )
    ) {
        // The card this page grows out of. Zero radius here against the card's
        // 16dp is what makes the corners flatten as the container expands.
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val coverBitmap = rememberCoverBitmap(
                    null,
                    state.playlistCoverPath.ifBlank { fallbackCoverPath }
                )
                Surface(
                    modifier = Modifier
                        .size(112.dp)
                        .then(
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(
                                        "playlist_cover_$playlistId"
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    
                                    clipInOverlayDuringTransition = OverlayClip(
                                        RoundedCornerShape(rememberSharedCoverTransitionRadius("playlist_cover_$playlistId", COVER_OVERLAY_CLIP, animatedVisibilityScope))
                                    )
                                )
                            }
                        ),
                    shape = ShapeLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap,
                            contentDescription = "歌单封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.padding(30.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(state.playlistTitle.ifBlank { "歌单" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("${state.playlistTracks.size} 首歌曲", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = { if (state.playlistTracks.isNotEmpty()) controller.playPlaylistTrack(0) }, modifier = Modifier.padding(vertical = 12.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("播放全部") }
        }
        if (state.playlistLoading && state.playlistTracks.isEmpty()
            && !sharedTransitionScope.isTransitionActive) {
            item {
                LoadingPlaceholder(
                    Modifier.fillMaxWidth().height(200.dp),
                    "正在加载歌单歌曲…"
                )
            }
        }
        itemsIndexed(
            state.playlistTracks,
            key = { index, song -> "playlist_song_${song.id}_$index" },
            contentType = { _, _ -> "song" }
        ) { index, song ->
            SongRow(
                title = song.name ?: "未知歌曲",
                artist = song.artist ?: "未知歌手",
                coverPath = song.coverThumbPath ?: song.coverUrl,
                onClick = { controller.playPlaylistTrack(index) },
                onLongPressQueue = { controller.enqueueNeteaseSong(song) }
            )
        }
    }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumScreen(
    state: PlayerUiState,
    controller: PlayerController,
    albumId: Long,
    fallbackCoverPath: String = "",
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // Material's decelerate curve (0.2, 0, 0, 1): leaves quickly and settles softly.
    // A linear tween reads as mechanical and a spring overshoots the container's
    // bounds, so this is the curve the container transform is specified with.
    val sharedMotion = tween<androidx.compose.ui.geometry.Rect>(
        durationMillis = SHARED_MOTION_MS,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            "album_container_$albumId"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        
                        // Square against the card's 16dp: each end carries its own
                        // shape, so nothing is re-cut mid-flight.
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(0.dp))
                    )
                }
            )
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val coverBitmap = rememberCoverBitmap(
                    null,
                    state.albumCoverPath.ifBlank { fallbackCoverPath }
                )
                Surface(
                    modifier = Modifier
                        .size(168.dp)
                        .then(
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(
                                        "album_cover_$albumId"
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    
                                    clipInOverlayDuringTransition = OverlayClip(
                                        RoundedCornerShape(rememberSharedCoverTransitionRadius("album_cover_$albumId", COVER_OVERLAY_CLIP, animatedVisibilityScope))
                                    )
                                )
                            }
                        ),
                    shape = ShapeLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap,
                            contentDescription = "专辑封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.padding(34.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text(state.albumTitle.ifBlank { "专辑" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (state.albumArtist.isNotBlank()) Text(state.albumArtist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.albumYear.isNotBlank()) Text(state.albumYear, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.albumTracks.size} 首歌曲", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = { if (state.albumTracks.isNotEmpty()) controller.playAlbumTrack(0) }, modifier = Modifier.padding(vertical = 12.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("播放全部")
            }
        }
        if (state.albumLoading && state.albumTracks.isEmpty()
            && !sharedTransitionScope.isTransitionActive) {
            item {
                LoadingPlaceholder(
                    Modifier.fillMaxWidth().height(200.dp),
                    "正在加载专辑歌曲…"
                )
            }
        }
        itemsIndexed(state.albumTracks, key = { index, song -> "${song.id}_$index" }) { index, song ->
            SongRow(
                title = song.name ?: "未知歌曲",
                artist = song.artist ?: "未知歌手",
                coverPath = song.coverThumbPath ?: song.coverUrl,
                onClick = { controller.playAlbumTrack(index) },
                onLongPressQueue = { controller.enqueueNeteaseSong(song) }
            )
        }
        if (!state.albumLoading && state.albumTracks.isEmpty()) {
            item { EmptyState("暂无专辑歌曲", "专辑内容加载失败或为空") { controller.openAlbum(state.openAlbumId) } }
        }
    }
    }
}

@Composable
private fun SettingsScreen(settings: SettingsCore, controller: PlayerController) {
    var category by remember { mutableStateOf(SettingsCatalog.APPEARANCE) }
    // SettingsCore isn't observable: a tap writes the store but nothing
    // recomposes this screen (its params never change), so controls appeared
    // dead until some unrelated recomposition landed. Bumping this counter on
    // every change forces the rows to re-read their values immediately.
    var revision by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            settings.categories().forEach { item ->
                if (item == SettingsCatalog.LOCAL || item == SettingsCatalog.ABOUT || item == SettingsCatalog.LYRIC || item == SettingsCatalog.PLAYBACK || item == SettingsCatalog.APPEARANCE || item == SettingsCatalog.PALETTE || item == SettingsCatalog.AI) {
                    if (item == category) Button(onClick = { category = item }) { Text(item) }
                    else OutlinedButton(onClick = { category = item }) { Text(item) }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(settings.rows(category), key = { it.key }) { spec ->
                SettingRow(spec, settings, controller, revision) { revision++ }
            }
        }
    }
}

@Composable
private fun SettingRow(
    spec: SettingSpec,
    settings: SettingsCore,
    controller: PlayerController,
    @Suppress("UNUSED_PARAMETER") revision: Int,
    onChanged: () -> Unit
) {
    if (spec.dependsOn.isNotBlank() && !settings.bool(spec.dependsOn)) return
    val type = spec.type
    val providerText = if (spec.provider.isNotBlank()) settings.info(spec.provider) else ""
    val description = providerText.ifBlank { spec.desc }
    val currentInt = settings.intOf(spec.key).coerceIn(spec.min, spec.max)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.title, fontWeight = FontWeight.Medium)
                    if (description.isNotBlank()) {
                        Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (type) {
                    SettingSpec.SWITCH -> Switch(settings.bool(spec.key), {
                        settings.setValue(spec.key, it)
                        onChanged()
                    })
                    SettingSpec.ACTION -> TextButton(onClick = {
                        settings.invoke(spec.action)
                        onChanged()
                    }) { Text(spec.button.ifBlank { "执行" }) }
                    SettingSpec.STEPPER -> {
                        IconButton(
                            onClick = { settings.bump(spec.key, -1); onChanged() },
                            enabled = currentInt > spec.min
                        ) { Icon(Icons.Default.Remove, contentDescription = "减少") }
                        Text(formatSettingValue(spec, currentInt), modifier = Modifier.sizeIn(minWidth = 52.dp), textAlign = TextAlign.Center)
                        IconButton(
                            onClick = { settings.bump(spec.key, 1); onChanged() },
                            enabled = currentInt < spec.max
                        ) { Icon(Icons.Default.Add, contentDescription = "增加") }
                    }
                    SettingSpec.SLIDER -> Text(formatSettingValue(spec, currentInt), fontSize = 12.sp)
                    SettingSpec.SEGMENTED, SettingSpec.RADIO, SettingSpec.DROPDOWN -> Unit
                    SettingSpec.TEXT, SettingSpec.PATH -> Unit
                    else -> Text(settings.str(spec.key).ifBlank { currentInt.toString() }, fontSize = 12.sp)
                }
            }
            when (type) {
                SettingSpec.SLIDER -> {
                    val steps = ((spec.max - spec.min) / spec.step - 1).coerceAtLeast(0)
                    Slider(
                        value = currentInt.toFloat(),
                        onValueChange = { value ->
                            val stepped = (spec.min + ((value - spec.min) / spec.step).roundToInt() * spec.step)
                                .coerceIn(spec.min, spec.max)
                            settings.setValue(spec.key, stepped)
                            onChanged()
                        },
                        valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        steps = steps
                    )
                }
                SettingSpec.SEGMENTED, SettingSpec.RADIO, SettingSpec.DROPDOWN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        spec.options.forEachIndexed { index, option ->
                            if (index == settings.intOf(spec.key)) {
                                Button(onClick = {}, enabled = false) { Text(option, maxLines = 1) }
                            } else {
                                OutlinedButton(onClick = {
                                    settings.setValue(spec.key, index)
                                    onChanged()
                                }) { Text(option, maxLines = 1) }
                            }
                        }
                    }
                }
                SettingSpec.TEXT, SettingSpec.PATH -> {
                    var value by remember(spec.key, revision) { mutableStateOf(settings.str(spec.key)) }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(spec.hint) },
                        readOnly = type == SettingSpec.PATH
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (type == SettingSpec.PATH) {
                            OutlinedButton(onClick = { settings.pickDirectory(spec.key); onChanged() }) {
                                Text("选择目录")
                            }
                        } else {
                            Button(onClick = { settings.setValue(spec.key, value); onChanged() }) {
                                Text(spec.button.ifBlank { "应用" })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSettingValue(spec: SettingSpec, value: Int): String {
    val shown = if (spec.scale == 1) value.toString()
    else String.format(java.util.Locale.US, "%.2f", value.toFloat() / spec.scale.toFloat())
    return shown + spec.unit
}

@Composable
private fun AccountScreen(state: PlayerUiState, controller: PlayerController) {
    var biliLoginOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(state.userName.ifBlank { "未登录" }, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("网易云音乐账户", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(28.dp))
        Divider(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))

        Text(
            if (state.biliLoggedIn) "已登录" else "未登录",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text("哔哩哔哩账户", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "用于搜索与播放 B 站视频（MV、现场版等）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        if (state.biliLoggedIn) {
            OutlinedButton(onClick = { controller.logoutBili() }) { Text("退出 B 站账号") }
        } else {
            Button(onClick = {
                biliLoginOpen = true
                controller.startBiliLogin()
            }) { Text("登录 B 站") }
        }
        if (state.biliError.isNotBlank() && state.biliQrStatus == 4) {
            Spacer(Modifier.height(8.dp))
            Text(state.biliError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }

    if (biliLoginOpen) {
        BiliLoginDialog(
            state = state,
            onDismiss = {
                biliLoginOpen = false
                controller.cancelBiliLogin()
            }
        )
    }
}

/** 扫码登录 B 站：显示 TV 登录二维码，文案跟随轮询状态。 */
@Composable
private fun BiliLoginDialog(state: PlayerUiState, onDismiss: () -> Unit) {
    val qrBitmap = remember(state.biliQrUrl) {
        if (state.biliQrUrl.isBlank()) null else runCatching {
            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                state.biliQrUrl,
                com.google.zxing.BarcodeFormat.QR_CODE,
                512, 512
            )
            val pixels = IntArray(matrix.width * matrix.height)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    pixels[y * matrix.width + x] = if (matrix.get(x, y)) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                }
            }
            val bitmap = android.graphics.Bitmap.createBitmap(
                matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            bitmap.asImageBitmap()
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录哔哩哔哩") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    state.biliQrStatus == 3 -> Text("登录成功")
                    state.biliQrStatus == 4 -> Text("二维码已过期，请重新打开", color = MaterialTheme.colorScheme.error)
                    state.biliQrStatus == 2 -> Text("已扫码，请在手机上确认")
                    qrBitmap != null -> {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "B站登录二维码",
                            modifier = Modifier.size(220.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("用哔哩哔哩 App 扫描二维码", fontSize = 13.sp)
                    }
                    else -> Text("正在获取二维码…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (state.biliQrStatus == 3) "完成" else "取消") }
        }
    )
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
private fun ArtistScreen(
    state: PlayerUiState,
    controller: PlayerController,
    artistId: Long,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    openAlbum: (Long, String) -> Unit
) {
    if (state.artistLoading && state.artistSongs.isEmpty() && state.artistAlbums.isEmpty()) {
        LoadingPlaceholder(Modifier.fillMaxSize(), "正在加载歌手作品…")
        return
    }

    // Material's decelerate curve (0.2, 0, 0, 1): leaves quickly and settles softly.
    // A linear tween reads as mechanical and a spring overshoots the container's
    // bounds, so this is the curve the container transform is specified with.
    val sharedMotion = tween<androidx.compose.ui.geometry.Rect>(
        durationMillis = SHARED_MOTION_MS,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    )
    val listState = rememberLazyListState()
    val heroBitmap = rememberCoverBitmap(
        null,
        state.artistHeaderPath.ifBlank { state.artistCoverPath }
    )
    // Monet again: the page keeps the cover-derived theme (the same palette every
    // other screen uses) instead of a colour sampled out of the artist's photo, and
    // the hero only has to blend into that theme's surface.
    val backdrop = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ArtistHero(
                state = state,
                backdrop = backdrop,
                heroBitmap = heroBitmap
            )
        }
        // Order asked for by the user: the ten best-known songs first, then the
        // discography, and the long biography last so it does not push either of
        // them below the fold. The core already hands the songs over in NetEase's
        // popularity order, so the first ten are the top ten.
        if (state.artistSongs.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("热门歌曲") } }
            itemsIndexed(
                state.artistSongs.take(ARTIST_TOP_SONG_COUNT),
                key = { index, song -> "artist_song_${song.id}_$index" }
            ) { index, song ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                SongRow(
                    title = song.name ?: "未知歌曲",
                    artist = song.artist ?: state.artistName,
                    coverPath = song.coverThumbPath ?: song.coverUrl,
                    onClick = { controller.playArtistSong(index) },
                    onLongPressQueue = { controller.enqueueNeteaseSong(song) }
                )
                }
            }
        }
        if (state.artistAlbums.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("专辑") } }
            items(
                state.artistAlbums,
                key = { album -> "artist_album_${album.id}" }
            ) { album ->
    // The card's own text is not part of the shared element: it has to be gone in
    // 80ms so it can never ghost underneath the detail page's text.
    val listTextAlpha by animateFloatAsState(
        targetValue = if (leavingCoverKey == "album_row_${album.id}") 0f else 1f,
        animationSpec = tween(80),
        label = "list_text_fade"
    )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                // The official container transform starts from a real container:
                // this card IS the element that grows into the album page, so it has
                // its own background and 16dp corners rather than being a bare row.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            leavingCoverKey = "album_row_${album.id}"
                            openAlbum(album.id, album.coverThumbPath ?: album.coverUrl ?: "")
                        }
                        .then(
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(
                                        "album_container_${album.id}"
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    // M3 expressive motion scheme — the same source the
                                    // framework uses, a spatial spring with an emphasised
                                    // curve instead of a linear tween.
                                    
                                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                    clipInOverlayDuringTransition = OverlayClip(
                                        RoundedCornerShape(16.dp)
                                    )
                                )
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = ComposeColor.Transparent
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val cover = rememberCoverBitmap(null, album.coverThumbPath ?: album.coverUrl)
                    Surface(
                        modifier = Modifier
                            .size(60.dp)
                            .then(
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(
                                            "album_cover_${album.id}"
                                        ),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        
                                        clipInOverlayDuringTransition = OverlayClip(
                                            RoundedCornerShape(rememberSharedCoverTransitionRadius("album_cover_${album.id}", COVER_OVERLAY_CLIP, animatedVisibilityScope))
                                        )
                                    )
                                }
                            ),
                        shape = ShapeMedium,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        if (cover != null) {
                            Image(
                                bitmap = cover,
                                contentDescription = "专辑封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Album, null, Modifier.padding(16.dp))
                        }
                    }
                    Column(Modifier.padding(start = 12.dp).graphicsLayer { alpha = listTextAlpha }) {
                        Text(album.name ?: "未命名专辑", fontWeight = FontWeight.Medium)
                        Text(
                            "${album.trackCount} 首歌曲",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                }
            }
        }
        }   // if (artistAlbums.isNotEmpty())
        if (!state.artistLoading && state.artistSongs.isEmpty() && state.artistAlbums.isEmpty()) {
            item { EmptyState("暂无歌手信息", "网易云未返回该歌手的公开资料") { controller.openArtist(state.openArtistId) } }
        }
        if (state.artistBriefDesc.isNotBlank()) {
            item { SectionTitle("歌手简介") }
            item {
                Text(
                    state.artistBriefDesc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

/**
 * The artist's hero band: their uploaded artwork blurred behind a gradient that
 * fades into the page's extracted colour, with the avatar and name sitting on the
 * left of that fused band — floating over the top of the song list below it.
 */
    }
@Composable
private fun ArtistHero(
    state: PlayerUiState,
    backdrop: ComposeColor,
    heroBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val avatar = rememberCoverBitmap(null, state.artistCoverPath)
    Box(modifier.fillMaxWidth().height(ARTIST_HERO_HEIGHT)) {
        if (heroBitmap != null) {
            Image(
                bitmap = heroBitmap,
                contentDescription = "歌手背景",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Only the artwork's tail is blurred, and only where it has to blend into
            // the page colour below — blurring the whole picture was what made it
            // look like a smudge rather than a photo.
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.22f)
                    .align(Alignment.BottomCenter)
                    .blur(24.dp)
            ) {
                Image(
                    bitmap = heroBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Top stays readable under the status bar, the middle keeps the artwork
        // intact, and the bottom melts into the page colour with no seam.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to ComposeColor.Black.copy(alpha = 0.40f),
                        0.18f to ComposeColor.Transparent,
                        0.55f to backdrop.copy(alpha = 0.55f),
                        0.85f to backdrop,
                        1.00f to backdrop
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(72.dp)
                    .border(2.dp, ComposeColor.White.copy(alpha = 0.45f), CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = "歌手头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    state.artistName.ifBlank { "歌手" },
                    color = ComposeColor.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${minOf(state.artistSongs.size, ARTIST_TOP_SONG_COUNT)} 首热门歌曲 · ${state.artistAlbums.size} 张专辑",
                    color = ComposeColor.White.copy(alpha = 0.72f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * MD3 Emphasized motion for the container transform, chosen by direction: the
 * expansion launches fast and settles with a long, soft damping tail
 * (Emphasized Decelerate); the collapse runs the same motion backwards, which is
 * shorter and steeper (Emphasized Accelerate).
 */
private val CoverExpandEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val CoverCollapseEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val COVER_EXPAND_MS = 450
private const val COVER_COLLAPSE_MS = 250

/** Enter or exit, decided by whether the shared bounds are growing. */
private fun coverBoundsTransform(
    initial: androidx.compose.ui.geometry.Rect,
    target: androidx.compose.ui.geometry.Rect
): androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.geometry.Rect> {
    val expanding = target.width * target.height >= initial.width * initial.height
    return tween(
        durationMillis = if (expanding) COVER_EXPAND_MS else COVER_COLLAPSE_MS,
        easing = if (expanding) CoverExpandEasing else CoverCollapseEasing
    )
}

private const val SHARED_COVER_RADIUS_CACHE_MAX = 256

/** Same corner-radius hand-over as legado-with-MD3's CoilBookCover: the source page
 *  records the cover's radius while it sits still, and the destination reads it back
 *  under the same key so both ends of the flight agree on the corners. */
private val sharedCoverRadiusCache =
    androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.unit.Dp>()

@Composable
private fun rememberSharedCoverTransitionRadius(
    sharedCoverKey: String?,
    radius: androidx.compose.ui.unit.Dp,
    animatedVisibilityScope: AnimatedVisibilityScope?
): androidx.compose.ui.unit.Dp {
    if (sharedCoverKey == null || animatedVisibilityScope == null) return radius
    val transition = animatedVisibilityScope.transition
    val startRadius = sharedCoverRadiusCache[sharedCoverKey] ?: radius
    val animatedRadiusValue by transition.animateFloat(label = "cover_corner_radius") { state ->
        if (state == androidx.compose.animation.EnterExitState.Visible) radius.value
        else startRadius.value
    }
    LaunchedEffect(sharedCoverKey, radius, transition.currentState, transition.targetState) {
        if (transition.currentState == androidx.compose.animation.EnterExitState.Visible &&
            transition.targetState == androidx.compose.animation.EnterExitState.Visible
        ) {
            sharedCoverRadiusCache[sharedCoverKey] = radius
            if (sharedCoverRadiusCache.size > SHARED_COVER_RADIUS_CACHE_MAX) {
                sharedCoverRadiusCache.keys.firstOrNull { it != sharedCoverKey }
                    ?.let(sharedCoverRadiusCache::remove)
            }
        }
    }
    return animatedRadiusValue.dp
}

/**
 * Key of the cover the user just tapped. Only that row's text fades out for the
 * flight — `isTransitionActive` is true for the whole page, so using it directly
 * blanked every row's text (and looked like the list "refreshing itself").
 */
private var leavingCoverKey by mutableStateOf<String?>(null)

/** Corner radius the flying cover is clipped to (ShapeLarge-ish). */
private val COVER_OVERLAY_CLIP = 20.dp

/** Height of the artist page's hero band (backdrop + avatar/name). */
private val ARTIST_HERO_HEIGHT = 200.dp

/**
 * Backdrop colour for the artist page: the average of the artwork's darker, less
 * saturated pixels — the job Palette's darkMutedSwatch would do — darkened further
 * so white text always reads on it. Null until there is artwork to read.
 */
private fun darkMutedBackdrop(bitmap: androidx.compose.ui.graphics.ImageBitmap?): ComposeColor? {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
    // A band through the middle of the artwork instead of the whole bitmap: same
    // tone, a quarter of the pixel-array allocation, and off the frame that opens
    // the page.
    val bandHeight = (bitmap.height / 4).coerceAtLeast(1)
    val pixels = bitmap.toPixelMap(
        startY = (bitmap.height - bandHeight) / 2,
        height = bandHeight
    )
    var sumR = 0L
    var sumG = 0L
    var sumB = 0L
    var count = 0
    var x = 0
    while (x < pixels.width) {
        var y = 0
        while (y < pixels.height) {
            val c = pixels[x, y]
            val maxC = maxOf(c.red, c.green, c.blue)
            val minC = minOf(c.red, c.green, c.blue)
            val sat = if (maxC <= 0f) 0f else (maxC - minC) / maxC
            if (sat < 0.85f && maxC < 0.95f) {
                sumR += (c.red * 255f).toLong()
                sumG += (c.green * 255f).toLong()
                sumB += (c.blue * 255f).toLong()
                count++
            }
            y += 16
        }
        x += 16
    }
    if (count == 0) return null
    return ComposeColor(
        red = (sumR / count / 255f * 0.55f).coerceIn(0f, 1f),
        green = (sumG / count / 255f * 0.55f).coerceIn(0f, 1f),
        blue = (sumB / count / 255f * 0.55f).coerceIn(0f, 1f)
    )
}

/** How many of an artist's best-known songs the artist page lists up front. */
private const val ARTIST_TOP_SONG_COUNT = 10



@Composable
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
private fun PlayerControlsSection(
    state: PlayerUiState,
    controller: PlayerController,
    isLiked: Boolean,
    onLikeToggle: () -> Unit,
    onLikeLongPress: () -> Unit = {}
) {
    val haptic = LocalView.current
    val previousEnabled = !state.privateFmMode
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.22f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heart_pulse_scale"
    )

    // --- Transport row (PixelPlayer AnimatedPlaybackControls style): three
    // pills that expand/compress on press — the tapped button widens (1.1x)
    // while the other two narrow (0.65x), then settle back. Play/pause corners
    // morph between "playing" (narrow) and "paused" (round, inviting a tap). ---
    var lastClicked by remember { mutableStateOf<Int>(0) } // 0 none, -1 prev, 1 next, 2 play
    fun weightFor(button: Int): Float = when (lastClicked) {
        button -> 1.1f
        0 -> 1f
        else -> 0.65f
    }
    val prevWeight by animateFloatAsState(
        targetValue = weightFor(-1),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "prev_weight"
    )
    val playWeight by animateFloatAsState(
        targetValue = weightFor(2),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "play_weight"
    )
    val nextWeight by animateFloatAsState(
        targetValue = weightFor(1),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "next_weight"
    )
    LaunchedEffect(lastClicked) {
        if (lastClicked != 0) {
            delay(if (lastClicked == 2) 220L else 600L)
            lastClicked = 0
        }
    }

    // IMPORTANT: everything below must live inside ONE root Column. A Composable
    // body may emit multiple elements, but they all get added DIRECTLY to the
    // caller's layout — the caller wraps us in a Box, so un-wrapped siblings
    // would stack on top of each other (the transport row and toggle row ended
    // up overlapping exactly like that).
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(prevWeight)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    if (previousEnabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
                .semantics { contentDescription = "上一首" }
                .clickable(
                    enabled = previousEnabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    lastClicked = -1
                    haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    controller.prev()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        val playCorner by animateDpAsState(
            targetValue = if (state.playing) 26.dp else 60.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "play_corner"
        )
        Box(
            Modifier
                .weight(playWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(playCorner))
                .background(MaterialTheme.colorScheme.primary)
                .semantics { contentDescription = "播放/暂停" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    lastClicked = 2
                    haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    controller.toggle()
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state.playing,
                transitionSpec = {
                    (scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(tween(140)))
                        .togetherWith(scaleOut(targetScale = 0.5f, animationSpec = tween(100)) + fadeOut(tween(100)))
                },
                label = "play_pause_icon_morph"
            ) { playing ->
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "播放/暂停",
                    Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Box(
            Modifier
                .weight(nextWeight)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .semantics { contentDescription = "下一首" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    lastClicked = 1
                    haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    controller.next()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SkipNext, "下一首", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }

    Spacer(Modifier.height(14.dp))

    // --- Bottom toggle row (PixelPlayer BottomToggleRow style): shuffle /
    // repeat / favorite as three equal segments on a shared pill container. ---
    // Hidden for video playback: these control a music queue that a video is not
    // part of. Collapsing the height plus the existing clip takes them off screen.
    var biliFavPickerOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(264.dp)
            // Shown for video playback too: 收藏 is meaningful there (it favourites the
            // B站 video), which is why the row is no longer collapsed away for a video.
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleSegment(
                modifier = Modifier.weight(1f),
                active = state.playMode == 1,
                activeColor = MaterialTheme.colorScheme.primary,
                activeContent = MaterialTheme.colorScheme.onPrimary,
                icon = Icons.Default.Shuffle,
                desc = "随机",
                enabled = !state.privateFmMode,
                onClick = { controller.setPlayMode(if (state.playMode == 1) 0 else 1) }
            )
            ToggleDivider()
            ToggleSegment(
                modifier = Modifier.weight(1f),
                active = state.playMode == 2,
                activeColor = MaterialTheme.colorScheme.secondary,
                activeContent = MaterialTheme.colorScheme.onSecondary,
                icon = if (state.playMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                desc = "循环",
                onClick = { controller.setPlayMode(if (state.playMode == 2) 0 else 2) }
            )
            ToggleDivider()
            ToggleSegment(
                modifier = Modifier.weight(1f),
                active = isLiked,
                activeColor = MaterialTheme.colorScheme.tertiary,
                activeContent = MaterialTheme.colorScheme.onTertiary,
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                desc = "收藏",
                // 视频收藏进 B站 收藏夹（弹框 → setBiliFavs），歌曲仍走原来的喜欢。
                onClick = { if (state.biliPlaying) biliFavPickerOpen = true else onLikeToggle() },
                onLongClick = onLikeLongPress,
                iconScale = heartScale
            )
        }
        if (biliFavPickerOpen) {
            BiliFavPickerDialog(
                state = state,
                controller = controller,
                bvid = state.queueTracks.getOrNull(state.queueIndex)?.biliBvid.orEmpty(),
                onDismiss = { biliFavPickerOpen = false }
            )
        }
    }

    } // Column root
}

@Composable
private fun ToggleSegment(
    modifier: Modifier,
    active: Boolean,
    activeColor: ComposeColor,
    activeContent: ComposeColor,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
    iconScale: Float = 1f,
    enabled: Boolean = true
    ,onLongClick: () -> Unit = {}
) {
    val hapticView = LocalView.current
    // The row owns the outer capsule. Only an active segment gets its own
    // capsule, leaving the inactive segments visually separated but flat.
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            active -> activeColor
            else -> ComposeColor.Transparent
        },
        animationSpec = tween(200),
        label = "toggle_segment_container"
    )
    val content by animateColorAsState(
        targetValue = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else if (active) activeContent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "toggle_segment_content"
    )
    Box(
        modifier
            .height(48.dp)
            // The clip shape stays a pill for the whole lifetime. Snapping it
            // to RectangleShape on deactivate made the outgoing fill become a
            // square first and only then fade away, which read as a rectangle
            // flashing across the segment. Only the fill colour animates now.
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .semantics { contentDescription = desc }
            .combinedClickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { hapticView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onClick() },
                onLongClick = { hapticView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onLongClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            desc,
            Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = content
        )
    }
}

@Composable
private fun ToggleDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    )
}

// PixelPlayer PlayingEqIcon — a three-bar equalizer drawn on Canvas. Bars breathe
// with continuous sine phase + a slow "wander" so the pattern doesn't repeat for
// ~12s, and collapse to dots when paused. Matches the current-song indicator used
// in their queue rows.
@Composable
private fun PlayingEqIcon(
    modifier: Modifier = Modifier,
    color: ComposeColor,
    isPlaying: Boolean = true,
    bars: Int = 3,
    minHeightFraction: Float = 0.28f,
    maxHeightFraction: Float = 1.0f,
    gapFraction: Float = 0.30f
) {
    val fullRotation = (2f * PI).toFloat()
    val phaseAnim = remember { Animatable(0f) }
    val wanderAnim = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            val start = ((phaseAnim.value % fullRotation) + fullRotation) % fullRotation
            phaseAnim.snapTo(start)
            phaseAnim.animateTo(start + fullRotation, tween(3600, easing = LinearEasing))
        }
    }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            val start = ((wanderAnim.value % fullRotation) + fullRotation) % fullRotation
            wanderAnim.snapTo(start)
            wanderAnim.animateTo(start + fullRotation, tween(12000, easing = LinearEasing))
        }
    }

    // 1 = full bars, 0 = dots (morphs smoothly on play/pause).
    val activity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "eq_activity"
    )
    val speeds = remember(bars) { List(bars) { (it + 1).toFloat() } }
    val shifts = remember(bars) { List(bars) { i -> i * 0.9f } }

    Canvas(modifier) {
        val phase = phaseAnim.value
        val wander = wanderAnim.value
        val w = size.width
        val h = size.height
        val tentativeBarW = w / (bars + (bars - 1) * (1f + gapFraction))
        val gap = tentativeBarW * gapFraction
        val barW = tentativeBarW
        val corner = CornerRadius(barW / 2f, barW / 2f)

        repeat(bars) { i ->
            val slowShift = 0.6f * sin(wander + i * 0.4f)
            val slowAmp = 0.85f + 0.15f * sin(wander * 0.5f + 1.1f + i * 0.3f)
            val v = (sin(phase * speeds[i] + shifts[i] + slowShift) * slowAmp + 1f) * 0.5f
            val eased = v * v * (3f - 2f * v)
            val fracBars = minHeightFraction + (maxHeightFraction - minHeightFraction) * eased
            val fracDots = 0.5f
            val frac = fracDots + (fracBars - fracDots) * activity
            val barH = h * frac
            val x = i * (barW + gap)
            val y = (h - barH) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = corner
            )
        }
    }
}

@OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
private fun PlayerDetailScreen(
    state: PlayerUiState,
    controller: PlayerController,
    openAlbum: (Long) -> Unit,
    openArtist: (Long) -> Unit,
    close: () -> Unit,
    closing: Boolean = false,
    sleepMinutes: Int = 0,
    sleepRemaining: Long = 0L,
    sleepArmed: Boolean = true,
    onSleepMinutesChanged: (Int) -> Unit = {}
) {
    var activeTab by remember(state.trackKey) { mutableIntStateOf(0) }
    // A bili video has no lyrics: never leave the page on the lyric tab while one
    // is playing.
    LaunchedEffect(state.biliPlaying) {
        if (state.biliPlaying) activeTab = 0
    }
    var queueSheetOpen by remember(state.trackKey) { mutableStateOf(false) }
    // Sharing: NetEase's open API hands out links, not WeChat/QQ share sheets (that
    // card flow is their own app's Tencent SDK integration), so the honest action is
    // to copy the song's canonical link.
    val shareContext = LocalContext.current
    val shareAction: () -> Unit = {
        val id = state.songId
        if (id == 0L) {
            android.widget.Toast.makeText(
                shareContext, "本地歌曲暂时无法生成分享链接",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            val link = "https://music.163.com/song?id=$id"
            val clipboard =
                shareContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("歌曲链接", link))
            android.widget.Toast.makeText(
                shareContext, "已复制分享链接",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val coverBitmap = rememberCoverBitmap(state.coverBytes, state.coverPath)
    val coverScale by animateFloatAsState(
        targetValue = if (state.playing) 1f else 0.95f,
        animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "detail_cover_scale"
    )
    val detailProgress = remember { Animatable(0f) }
    val coverExpand = detailProgress
    val metaEnter = detailProgress
    val controlsEnter = detailProgress

    BackHandler {
        if (queueSheetOpen) queueSheetOpen = false else close()
    }

    LaunchedEffect(closing) {
        detailProgress.animateTo(if (closing) 0f else 1f, tween(DETAIL_MOTION_MS, easing = FastOutSlowInEasing))
    }

    // Opening the queue is intentionally idempotent.  A new coroutine scope
    // cancels any in-flight closing transition before the sheet is made visible
    // again, so a tap on the queue button can never be swallowed by stale drag
    // state.
    fun openQueueSheet() {
        dragY = 0f
        queueSheetOpen = false
        queueSheetOpen = true
    }

    fun selectTab(tab: Int) { activeTab = tab.coerceIn(0, 1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(activeTab) {
                detectHorizontalDragGestures(
                    onDragStart = { dragX = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); dragX += amount },
                    onDragEnd = {
                        if (!queueSheetOpen && kotlin.math.abs(dragX) > 72f) {
                            selectTab(if (dragX < 0f) 1 else 0)
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f }
                )
            }
            .pointerInput(activeTab) {
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onVerticalDrag = { change, amount -> change.consume(); dragY += amount },
                    onDragEnd = {
                        if (!queueSheetOpen) {
                            when {
                                dragY < -72f -> openQueueSheet()
                                dragY > 72f -> close()
                            }
                        }
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f }
                )
            }
    ) {
        // Detail and navigation use the same MD3 surface container. Keeping a
        // single colour source avoids the full-screen detail page becoming a
        // different shade after repeated route transitions.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // This is the APK's single top row: back, centered tab switcher,
            // and queue. Keeping them in one row avoids the later duplicated
            // header that made the lyrics page look vertically displaced.
            PlayerDetailHeader(
                state = state,
                activeTab = activeTab,
                onTab = ::selectTab,
                onClose = close,
                onShare = shareAction,
                onQueue = ::openQueueSheet
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                            .togetherWith(fadeOut(tween(220, easing = androidx.compose.animation.core.FastOutLinearInEasing)))
                    },
                    label = "player_detail_tab"
                ) { tab ->
                    if (tab == 0) {
                        ApkDetailTab(
                            state = state,
                            controller = controller,
                            coverBitmap = coverBitmap,
                            coverExpand = coverExpand.value,
                            metaEnter = metaEnter.value,
                            controlsEnter = controlsEnter.value,
                            closing = closing,
                            openAlbum = openAlbum,
                            openArtist = openArtist,
                            onOpenLyrics = { selectTab(1) }
                            ,playlists = state.myPlaylists
                        )
                    } else {
                        ApkLyricTab(
                            state = state,
                            controller = controller,
                            coverBitmap = coverBitmap,
                            coverScale = coverScale,
                            openArtist = openArtist,
                            onShowDetail = { selectTab(0) }
                        )
                    }
                }
            }
        }

        // The root VideoLayer draws above this page's content, so it would float over
        // this sheet. Park it while the sheet is up; parking keeps the Surface alive, so
        // closing the sheet does not rebuild the picture.
        LaunchedEffect(queueSheetOpen) { videoSlotObscured = queueSheetOpen }
        AnimatedVisibility(
            visible = queueSheetOpen,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f),
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(tween(240)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutLinearInEasing)
            ) + fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { queueSheetOpen = false }
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.82f)
                        .navigationBarsPadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .height(36.dp)
                                .align(Alignment.CenterHorizontally)
                                .pointerInput(Unit) {
                                    var sheetDragY = 0f
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            sheetDragY += amount
                                        },
                                        onDragEnd = {
                                            if (sheetDragY > 56f) queueSheetOpen = false
                                            sheetDragY = 0f
                                        },
                                        onDragCancel = { sheetDragY = 0f }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(width = 36.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "播放队列 · ${state.queueTracks.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { queueSheetOpen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭播放队列")
                            }
                        }
                            SleepTimerControl(sleepMinutes, sleepRemaining, onSleepMinutesChanged, sleepArmed)
                        DraggableQueueList(
                            state = state,
                            controller = controller,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            staggeredEntry = false,
                            header = {},
                            emptyContent = {
                                EmptyState("播放队列为空", "从歌曲列表选择一首开始播放") { }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailHeader(
    state: PlayerUiState,
    activeTab: Int,
    onTab: (Int) -> Unit,
    onClose: () -> Unit,
    onQueue: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "返回",
                modifier = Modifier.size(24.dp)
            )
        }
        // A video has no lyrics, so there is nothing to switch to: the pill and its
        // two buttons are not composed at all while one plays.
        if (!state.biliPlaying) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailTabButton("播放详情", activeTab == 0) { onTab(0) }
                    DetailTabButton("歌词", activeTab == 1) { onTab(1) }
                }
            }
        }
        if (onShare != null) {
            IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Share, contentDescription = "分享", modifier = Modifier.size(24.dp))
            }
        }
        if (onQueue != null) {
            IconButton(onClick = onQueue, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.QueueMusic, contentDescription = "播放队列", modifier = Modifier.size(24.dp))
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun DetailTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(19.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else ComposeColor.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ApkDetailTab(
    state: PlayerUiState,
    controller: PlayerController,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    coverExpand: Float,
    metaEnter: Float,
    controlsEnter: Float,
    closing: Boolean,
    openAlbum: (Long) -> Unit,
    openArtist: (Long) -> Unit,
    onOpenLyrics: () -> Unit
    ,playlists: List<NeteasePlaylist> = emptyList()
) {
    var playlistDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Playing a video: it keeps its own shape instead of the square artwork
            // box, and tapping it is not a tab switch.
            if (state.biliPlaying) {
                // Framed to the source's own shape: a 4:3 video gets a 4:3 box instead
                // of being squeezed into a fixed 16:9 one. Only falls back to 16:9
                // until the stream has reported its size.
                val videoAspect = if (videoSourceHeight > 0) {
                    videoSourceWidth.toFloat() / videoSourceHeight.toFloat()
                } else {
                    16f / 9f
                }
                // Reserves the box and reports it; the picture itself is drawn by the
                // root VideoLayer, so the player keeps one Surface for the session.
                // Double-tap enters full-screen — a single tap is reserved for the
                // full-screen show/hide-controls toggle, so the mode change always
                // takes a deliberate gesture.
                VideoSlotReporter(
                    priority = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspect)
                        .clip(ShapeExtraLarge)
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { videoFullscreen = true })
                        }
                )
                DisposableEffect(Unit) {
                    onDispose {
                        videoFullscreen = false
                        videoSlotPriority = 0
                        videoSlotObscured = false
                        // Nothing wants the picture any more; without this the layer
                        // would keep drawing at this page's last box over whatever
                        // screen comes next.
                        videoSlotRect = null
                    }
                }
                return@BoxWithConstraints
            }
            val coverSize = minOf(maxWidth, maxHeight)
            ApkDetailCover(
                state = state,
                coverBitmap = coverBitmap,
                modifier = Modifier
                    .size(coverSize)
                    .graphicsLayer {
                        val expand = 0.15f + 0.85f * coverExpand
                        scaleX = expand
                        scaleY = expand
                        // The detail artwork enters from the same lower-left
                        // origin as the source MiniPlayer, and exits by
                        // following this exact transform in reverse.
                        translationX = (1f - coverExpand) * (-maxWidth.toPx() * 0.4f)
                        translationY = (1f - coverExpand) * (maxHeight.toPx() * 0.85f)
                        // During close the artwork must disappear on the same
                        // frame as the rest of the detail content. Without
                        // this, the 15% end-scale remains visible under the
                        // page fade and looks like the cover pauses at the end.
                        if (closing) alpha = coverExpand
                    },
                onClick = onOpenLyrics
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .graphicsLayer {
                    translationY = (1f - metaEnter) * 56f
                    alpha = metaEnter
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val title = state.title.ifBlank { "未在播放" }
            val titleSize = when {
                title.length > 48 -> 18.sp
                title.length > 30 -> 21.sp
                else -> 24.sp
            }
            Text(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clickable(
                        enabled = state.albumId != 0L,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { openAlbum(state.albumId) },
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            PlayingArtistLinks(
                state = state,
                openArtist = openArtist,
                fontSize = 13.sp,
                centered = true
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = (1f - controlsEnter) * 56f
                    alpha = controlsEnter
                }
        ) {
            Column {
                // Keep the progress track and transport controls on the same
                // animation channel so neither one lingers during close.
                QmlLyricProgress(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    loading = state.loading,
                    wavy = state.lyricProgressStyle == 0,
                    onSeek = controller::seek,
                    formatMs = ::formatPlayerTime,
                    modifier = Modifier.fillMaxWidth()
                )
                PlayerControlsSection(
                    state = state,
                    controller = controller,
                    isLiked = state.liked,
                    onLikeToggle = controller::toggleLike,
                    onLikeLongPress = { playlistDialog = true }
                )
            }
        }
    }
    if (playlistDialog) AlertDialog(
        onDismissRequest = { playlistDialog = false },
        title = { Text("添加到歌单") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Text(
                        playlist.name ?: "未命名歌单",
                        modifier = Modifier.fillMaxWidth().clickable {
                            controller.addToPlaylist(playlist.id, state.songId)
                            playlistDialog = false
                        }.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { playlistDialog = false }) { Text("取消") } }
    )
}

private fun formatPlayerTime(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** The Activity hosting a composition context, unwrapping the ContextWrapper layers
 *  LocalContext can carry (a themed wrapper, for instance). */
private fun activityOf(context: android.content.Context?): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Full-screen video: the transport bar and the exit gesture in a borderless,
 *  transparent dialog. The picture is NOT here — it is drawn by the window overlay
 *  the activity owns, which has been resized to the whole window, and shows through
 *  this dialog. A SurfaceView of its own would live in the dialog's separate window,
 *  so entering and leaving full-screen would destroy and recreate the surface and
 *  make the player rebuild the picture (the "replays a second" this replaces).
 *  The window is also taken to landscape with the system bars hidden while it is up
 *  — a 16:9 picture in a portrait window is otherwise squeezed, or boxed into a
 *  strip. */
@Composable
private fun BiliFullscreenControls(
    state: PlayerUiState,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onExit: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    // Drawn over the picture in the SAME window (see VideoLayer): a gesture layer plus
    // these controls is all there is, so nothing here can dim or cover the video. A
    // double tap leaves full-screen; a single tap only shows/hides the controls, so the
    // mode change always takes a deliberate gesture.
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { onExit() }
                )
            }
    ) {
        if (showControls) {
                // The transport bar doubles as the seek control, and carries the UP's
                // chapter starts as ticks on the track.
                QmlLyricProgress(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    loading = state.loading,
                    wavy = false,
                    onSeek = onSeek,
                    formatMs = ::formatPlayerTime,
                    chapterMarks = state.biliChapterMarks,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                        .size(56.dp),
                    shape = CircleShape,
                    color = ComposeColor.White.copy(alpha = 0.18f)
                ) {
                    Box(Modifier.clickable { onToggle() }, contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = ComposeColor.White
                        )
                    }
                }
            }
        }
}

/** A slot that wants the video picture: reports its box (in window coordinates) and
 *  draws nothing itself. Only one slot is live at a time — the mini player and the
 *  detail page are the two branches of the root's AnimatedContent, so they never
 *  coexist — and the highest one wins if a transition frame has both. */
@Composable
private fun VideoSlotReporter(priority: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.onGloballyPositioned {
            if (videoSlotPriority <= priority) {
                videoSlotPriority = priority
                videoSlotRect = it.boundsInWindow()
            }
        }
    )
}

/** The one place the B站 picture is drawn.
 *
 *  <p>Always composed, and afterwards only moved and resized, so the player keeps the
 *  same Surface for the whole session and is never made to rebuild the picture. A slot
 *  that wants the video reports its box ([videoSlotRect]); when nothing wants it, or a
 *  panel has to draw over it, the layer is parked just off-window — parked, not
 *  dropped, so the Surface stays alive.
 *
 *  <p>The full-screen controls are drawn here, over the picture, instead of in a
 *  dialog: a dialog is a second window, and the platform dims the window beneath it,
 *  which darkened the whole picture for as long as full-screen was up. */
@Composable
private fun VideoLayer(state: PlayerUiState, controller: PlayerController) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val activity = activityOf(LocalContext.current)
    val fullscreen = videoFullscreen && state.biliPlaying

    // Landscape + immersive with full-screen's lifetime. The activity declares
    // configChanges=orientation|screenSize, so this resizes in place rather than
    // recreating the composition and losing the playback state.
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            bars?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (fullscreen) {
                bars?.show(WindowInsetsCompat.Type.systemBars())
                activity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowW = with(density) { maxWidth.toPx() }
        val windowH = with(density) { maxHeight.toPx() }
        val slot = videoSlotRect
        val wanted = state.biliPlaying && !videoSlotObscured && (fullscreen || slot != null)
        val aspect = if (videoSourceHeight > 0) {
            videoSourceWidth.toFloat() / videoSourceHeight.toFloat()
        } else {
            16f / 9f
        }
        // Parked a window-width to the left: composed, sized, out of sight.
        val target = when {
            !wanted -> androidx.compose.ui.geometry.Rect(
                -(windowW + 64f), 0f, -(windowW + 63f), 1f
            )
            fullscreen -> {
                // The source's own shape, inside the window minus the same side padding
                // the rest of the app uses, centred.
                val pad = with(density) { 24.dp.toPx() }
                val availableW = (windowW - 2 * pad).coerceAtLeast(1f)
                val availableH = windowH.coerceAtLeast(1f)
                val pictureW = if (availableW / availableH > aspect) {
                    availableH * aspect
                } else {
                    availableW
                }
                val pictureH = pictureW / aspect
                androidx.compose.ui.geometry.Rect(
                    (windowW - pictureW) / 2f,
                    (windowH - pictureH) / 2f,
                    (windowW + pictureW) / 2f,
                    (windowH + pictureH) / 2f
                )
            }
            else -> slot ?: return@BoxWithConstraints
        }
        if (fullscreen) {
            // Matte for the letterbox bars, under the picture.
            Box(Modifier.fillMaxSize().background(ComposeColor.Black))
        }
        // The gesture belongs to the layer, not to the slot that reported the box: the
        // picture node sits above the page and would swallow a double tap aimed at the
        // box underneath it. In full-screen the controls layer handles the gestures, so
        // this one is only attached while the picture is inline.
        val videoModifier = Modifier
            .offset(
                x = with(density) { target.left.toDp() },
                y = with(density) { target.top.toDp() }
            )
            .size(
                with(density) { target.width.toDp() },
                with(density) { target.height.toDp() }
            )
        Box(
            modifier = if (fullscreen) {
                videoModifier
            } else {
                videoModifier.pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { videoFullscreen = true })
                }
            }
        ) {
            // The picture keeps the surrounding UI's shape while it is inline, and goes
            // edge to edge in full-screen: the radius is 0 there, which is the "no clip"
            // case of the same platform outline (see BiliVideoSurface).
            BiliVideoSurface(
                modifier = Modifier.fillMaxSize(),
                cornerRadiusPx = if (fullscreen) 0f
                else with(density) { SHAPE_EXTRA_LARGE_DP.dp.toPx() }
            )
        }
        if (fullscreen) {
            BiliFullscreenControls(
                state = state,
                onToggle = { controller.toggle() },
                onSeek = { controller.seek(it) },
                onExit = { videoFullscreen = false }
            )
        }
    }
}

/** Title of the folder the content page was opened for. Kept beside the route rather
 *  than inside it: the route only carries a Long id, and the title is also what the
 *  core publishes for the page header once its items load. */
private var biliFavDetailTitle by mutableStateOf("")

/** 收藏夹多选框：列出用户的 B站收藏夹，已收藏的打勾；确定时**只提交差异**（新增的
 *  进 add、取消的进 del），所以点开不改、直接确定不会发出任何写请求。 */
@Composable
private fun BiliFavPickerDialog(
    state: PlayerUiState,
    controller: PlayerController,
    bvid: String,
    onDismiss: () -> Unit
) {
    // 打开时按这个视频拉一次（rid 变体）：既填充列表，也拿到每个夹的已收藏状态。
    LaunchedEffect(bvid) { controller.loadBiliFavFoldersForBvid(bvid) }
    // 进入时的状态来自接口的 containsItem；用户改动只体现在 selected 上。
    val original = state.biliFavFolders.filter { it.containsItem }.map { it.mediaId }.toSet()
    var selected by remember(original) { mutableStateOf(original) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏到 B站收藏夹") },
        text = {
            when {
                state.biliFavLoading -> Text("正在加载收藏夹…")
                state.biliFavFolders.isEmpty() ->
                    Text(state.biliFavError.ifBlank { "没有可用的收藏夹" })
                else -> LazyColumn {
                    itemsIndexed(state.biliFavFolders, key = { _, f -> f.mediaId }) { _, folder ->
                        val checked = folder.mediaId in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - folder.mediaId
                                    else selected + folder.mediaId
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (checked) "✓" else "　",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                folder.title.ifBlank { "未命名收藏夹" },
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                Modifier
                    .clickable(enabled = bvid.isNotBlank()) {
                        val add = selected.filter { it !in original }
                        val del = original.filter { it !in selected }
                        if (add.isNotEmpty() || del.isNotEmpty()) {
                            controller.setBiliFavs(bvid, add, del)
                        }
                        onDismiss()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) { Text("确定") }
        },
        dismissButton = {
            Box(
                Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) { Text("取消") }
        }
    )
}

/** B站收藏夹列表 — the 歌单 entry's destination.
 *
 *  An empty list has to be explained by [PlayerUiState.biliFavError]: the API answers
 *  `code:0` with `data:null` for a logged-out caller, so a bare empty list would read
 *  as "this account has no folders". */
@Composable
private fun BiliFavFoldersScreen(
    state: PlayerUiState,
    controller: PlayerController,
    onOpen: (Long) -> Unit
) {
    LaunchedEffect(Unit) { controller.loadBiliFavFolders(0L) }
    when {
        state.biliFavLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在加载收藏夹…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.biliFavFolders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                state.biliFavError.ifBlank { "这个账号还没有 B站收藏夹" },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.biliFavFolders, key = { _, f -> f.mediaId }) { _, folder ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(ShapeMedium)
                        .clickable { onOpen(folder.mediaId) },
                    shape = ShapeMedium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.title.ifBlank { "未命名收藏夹" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${folder.mediaCount} 个视频" +
                                    if (folder.privateFolder) " · 私密" else "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "›",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** One favourite folder's videos. Tapping any entry queues the WHOLE folder from that
 *  entry (see [PlayerController.playBiliFavItemAt]) — that is what "playing a folder"
 *  means here, and it needs no further network work because the folder was loaded in
 *  full on the way in. */
@Composable
private fun BiliFavItemsScreen(
    state: PlayerUiState,
    controller: PlayerController,
    mediaId: Long
) {
    LaunchedEffect(mediaId) { controller.loadBiliFavItems(mediaId, biliFavDetailTitle) }
    when {
        state.biliFavItemsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在加载收藏夹…", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.biliFavItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("这个收藏夹是空的", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(state.biliFavItems, key = { i, item -> "fav_${item.avid}_$i" }) { index, item ->
                val playing = state.queueIndex >= 0 &&
                    state.queueIndex < state.queueTracks.size &&
                    state.queueTracks[state.queueIndex].biliBvid == item.bvid
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(ShapeMedium)
                        .clickable { controller.playBiliFavItemAt(index) },
                    shape = ShapeMedium,
                    color = if (playing) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rememberCoverBitmap(null, item.coverUrl)?.let { art ->
                            Image(
                                bitmap = art,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(ShapeSmall)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title.ifBlank { item.bvid },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.author,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (item.durationSeconds > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatPlayerTime(item.durationSeconds * 1000L),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The video picture for B站 playback: a plain SurfaceView handed to the shared
 *  MediaPlayer, so the sound keeps coming from the same player.
 *
 *  <p>It lives in the Compose tree (not in a window overlay) so the app's own panels
 *  keep drawing over it — a sibling view added above the Compose content floated over
 *  the queue sheet. The picture is fitted inside whatever box the caller frames, and
 *  the caller sizes that box from the source's aspect ratio.
 *
 *  <p>[cornerRadiusPx] is the shape of that box, rounded on the platform side rather
 *  than by Compose: see the factory below for why a Compose clip cannot reach a
 *  SurfaceView's picture and what the outline is applied to instead. The inline
 *  preview passes the surrounding UI's 32dp; full-screen passes 0, which is the
 *  unclipped, edge-to-edge case. */
@Composable
private fun BiliVideoSurface(modifier: Modifier = Modifier, cornerRadiusPx: Float = 0f) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // The picture is a SurfaceView, so a Compose clip (or a plain
            // clipToOutline on the view that merely *contains* it) does nothing to it:
            // the picture is not drawn by this window at all. A SurfaceView with the
            // default Z order is composited BEHIND the window and shows through a hole
            // this view punches in it ("clear the window's pixels here"), so the shape
            // that matters is the shape of that hole. Setting an outline on the views
            // that draw the hole is the platform's own way of shaping it — the corner
            // regions then keep whatever the app painted there (this page's opaque
            // background), which is exactly the rounded box the surrounding UI uses.
            //
            // Both the SurfaceView and its holder carry the outline: which of the two
            // display lists the hole is recorded in is a platform detail, and a clip on
            // either of them covers it.
            val holder = android.widget.FrameLayout(ctx)
            val surface = android.view.SurfaceView(ctx)
            holder.outlineProvider = RoundRectOutlineProvider(cornerRadiusPx)
            surface.outlineProvider = RoundRectOutlineProvider(cornerRadiusPx)
            applyVideoCornerRadius(holder, cornerRadiusPx)
            applyVideoCornerRadius(surface, cornerRadiusPx)
            surface.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    videoBackend?.attachVideoSurface(holder.surface)
                }
                override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, hh: Int) {
                    videoBackend?.attachVideoSurface(h.surface)
                }
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    videoBackend?.detachVideoSurface(holder.surface)
                }
            })
            holder.addView(surface, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT))
            holder
        },
        update = { holder ->
            // The radius follows the box: 32dp inline, 0 (edge to edge, no clip) in
            // full-screen. The outline itself is rebuilt from the view's size, so a
            // resized box keeps its corners without any other help.
            for (i in 0 until holder.childCount) {
                applyVideoCornerRadius(holder.getChildAt(i), cornerRadiusPx)
            }
            applyVideoCornerRadius(holder, cornerRadiusPx)
        }
    )
}

/** A round-rect outline of one radius, in pixels. 0 means "no rounding", which is
 *  also the flag that turns the clip off entirely (see [applyVideoCornerRadius]). */
private class RoundRectOutlineProvider(private var radiusPx: Float) :
    android.view.ViewOutlineProvider() {
    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }

    fun radius(): Float = radiusPx

    fun setRadius(r: Float) {
        radiusPx = r
    }
}

/** Puts [radiusPx] on one view of the video node: the outline says what shape, the
 *  clip says whether it is applied, and `invalidateOutline` is what makes the
 *  platform rebuild the outline for the current size. */
private fun applyVideoCornerRadius(view: android.view.View, radiusPx: Float) {
    val provider = view.outlineProvider as? RoundRectOutlineProvider
    val clipped = radiusPx > 0f
    if (provider != null) {
        if (provider.radius() != radiusPx) {
            provider.setRadius(radiusPx)
            view.invalidateOutline()
        }
    } else {
        view.outlineProvider = RoundRectOutlineProvider(radiusPx)
    }
    if (view.clipToOutline != clipped) view.clipToOutline = clipped
}

@Composable
private fun ApkDetailCover(
    state: PlayerUiState,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (state.biliPlaying) {
        // B站视频：同一个 MediaPlayer 挂上这个 SurfaceView 就出画，音画同一时钟。
        BiliVideoSurface(
            modifier = modifier.clip(ShapeExtraLarge).clickable { onClick() },
            cornerRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                SHAPE_EXTRA_LARGE_DP.dp.toPx()
            }
        )
        return
    }
    Surface(
        modifier = modifier
            .clip(ShapeExtraLarge)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = ShapeExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Crossfade(
                targetState = state.trackKey to coverBitmap,
                animationSpec = tween(250),
                label = "detail_cover_track_transition"
            ) { (_, bitmap) ->
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }
        }
    }
}

@Composable
private fun ApkLyricTab(
    state: PlayerUiState,
    controller: PlayerController,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    coverScale: Float,
    openArtist: (Long) -> Unit,
    onShowDetail: () -> Unit
) {
    // No lyric data must never block entering this tab. The artwork used to
    // simply sit there while the asynchronous lyric fetch was still in flight;
    // that wait is now the expressive loading animation. Once the list arrives
    // the same layout animates into the compact artwork + lyric column used by
    // the original APK. If nothing arrives within the grace period the song has
    // no lyrics, and the artwork view becomes that final state — not a spinner
    // that never ends.
    // The core reports when the fetch for this track has finished — including
    // the "this track has no lyrics at all" result — and it now retries a
    // transient empty answer itself. So while it says loading, the page simply
    // keeps showing the animation, however long that takes: no timeout, because
    // a timer can only ever be wrong in both directions (it used to drop back to
    // the artwork while the lyrics were still on their way).
    val pane = when {
        state.lyrics.isNotEmpty() -> LyricPane.LINES
        state.lyricsLoading -> LyricPane.LOADING
        else -> LyricPane.COVER
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedContent(
            targetState = pane,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = {
                if (targetState == LyricPane.COVER) {
                    (scaleIn(initialScale = 0.92f, animationSpec = tween(360)) + fadeIn(tween(260)))
                        .togetherWith(scaleOut(targetScale = 1.06f, animationSpec = tween(260)) + fadeOut(tween(180)))
                } else {
                    (slideInVertically(initialOffsetY = { -it / 6 }, animationSpec = tween(420)) + fadeIn(tween(300)))
                        .togetherWith(slideOutVertically(targetOffsetY = { it / 6 }, animationSpec = tween(260)) + fadeOut(tween(180)))
                }
            },
            label = "lyrics_cover_layout"
        ) { visibleLyricPane ->
            if (visibleLyricPane == LyricPane.LOADING) {
                LoadingPlaceholder(Modifier.fillMaxSize(), "正在加载歌词…")
            } else if (visibleLyricPane == LyricPane.COVER) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // The artwork used to sit here whenever there was no lyric
                    // list to show, which read as a stalled page. The MD loading
                    // animation takes its place; the title below stays so the page
                    // still says what is playing.
                    ExpressiveLoadingIndicator(size = 96.dp, withContainer = true)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.title.ifBlank { "未在播放" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        PlayingArtistLinks(state, openArtist, 13.sp, centered = true)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QmlLyricCover(
                            state = state,
                            coverBitmap = coverBitmap,
                            modifier = Modifier.size(56.dp),
                            fixedSize = 56.dp,
                            onTap = onShowDetail
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = state.title.ifBlank { "未在播放" },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            PlayingArtistLinks(state, openArtist, 13.sp, centered = false)
                        }
                    }
                    QmlLyricColumnRestored(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        onLineClick = { controller.seek(it) }
                    )
                }
            }
        }
        QmlLyricTransport(
            state = state,
            controller = controller,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            formatMs = ::formatPlayerTime
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun QmlLyricPage(
    state: PlayerUiState,
    controller: PlayerController,
    openArtist: (Long) -> Unit,
    close: () -> Unit,
    showCloseButton: Boolean = false
) {
    // This is the portrait structure of shared-qml/components/LyricOverlay.qml.
    // It is mounted by PlayerDetailScreen's Lyrics tab. Keep its chrome and
    // cover/lyrics behavior independent from the restored playback-detail tab.
    var offsetPanelOpen by remember { mutableStateOf(false) }
    var offsetValue by remember(state.trackKey, state.lyricOffsetMs) {
        mutableFloatStateOf(state.lyricOffsetMs.toFloat())
    }
    val coverOnly = state.lyricsCoverOnly || state.coverModeManual
    val coverBitmap = rememberCoverBitmap(state.coverBytes, state.coverPath)

    fun formatMs(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        QmlLyricBackdrop(
            state = state,
            coverBitmap = coverBitmap,
            modifier = Modifier.fillMaxSize()
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                QmlLyricLandscapeChrome(
                    state = state,
                    controller = controller,
                    coverBitmap = coverBitmap,
                    openArtist = openArtist,
                    close = close,
                    showCloseButton = false,
                    offsetPanelOpen = offsetPanelOpen,
                    onOffsetPanelOpenChange = { offsetPanelOpen = it },
                    offsetValue = offsetValue,
                    onOffsetValueChange = { offsetValue = it },
                    modifier = Modifier.fillMaxSize(),
                    formatMs = ::formatMs
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
            // QML top row: expand_more, image (when lyrics exist), sync.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 6.dp, end = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCloseButton) {
                        IconButton(
                            onClick = close,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "关闭歌词",
                                tint = ComposeColor.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (!coverOnly) {
                        IconButton(
                            onClick = { controller.setCoverMode(true) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Album,
                                contentDescription = "显示封面",
                                tint = ComposeColor.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            offsetPanelOpen = !offsetPanelOpen
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "歌词偏移",
                            tint = ComposeColor.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = offsetPanelOpen,
                    enter = fadeIn(tween(160)) + scaleIn(
                        initialScale = 0.9f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                        animationSpec = tween(220)
                    ),
                    exit = fadeOut(tween(120)) + scaleOut(
                        targetScale = 0.9f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                        animationSpec = tween(150)
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = 48.dp)
                        .zIndex(10f)
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .fillMaxWidth(0.86f),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp
                    ) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("歌词偏移", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (offsetValue > 0f) "+${offsetValue.toInt()} ms"
                                    else "${offsetValue.toInt()} ms",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "仅对当前歌曲生效 · 负值提前，正值延后",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Slider(
                                value = offsetValue.coerceIn(-5000f, 5000f),
                                onValueChange = {
                                    offsetValue = (it / 50f).roundToInt() * 50f
                                },
                                valueRange = -5000f..5000f,
                                steps = 199,
                                onValueChangeFinished = {
                                    controller.setLyricOffset(offsetValue.toInt())
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("提前 5 秒", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("延后 5 秒", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = {
                                    offsetValue = 0f
                                    controller.resetLyricOffset()
                                },
                                enabled = offsetValue != 0f,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("重置为 0")
                            }
                        }
                    }
                }
            }

            // QML title band. The title is not an album button in the original
            // overlay; only the artist hit area is wired to the original artist route.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.title.ifBlank { "未在播放" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 1000
                        ),
                    color = ComposeColor.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artist.ifBlank { "未知歌手" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (state.artistId != 0L) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { openArtist(state.artistId) }
                            } else Modifier
                        )
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 1000
                        ),
                    color = ComposeColor.White.copy(alpha = 0.70f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = coverOnly,
                    animationSpec = tween(250),
                    label = "qml_lyric_cover_mode"
                ) { showCover ->
                    if (showCover) {
                        QmlLyricCover(
                            state = state,
                            coverBitmap = coverBitmap,
                            modifier = Modifier.fillMaxSize(),
                            onTap = {
                                if (!state.lyricsCoverOnly) controller.setCoverMode(false)
                            }
                        )
                    } else {
                        QmlLyricColumnRestored(
                            state = state,
                            modifier = Modifier.fillMaxSize(),
                            onLineClick = { controller.seek(it) }
                        )
                    }
                }
                // Soften the cover/title-to-lyrics seam: the upper edge is
                // opaque and increasingly transparent downward, preventing a
                // lyric glyph from being cut by a hard rectangular boundary.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
                                    ComposeColor.Transparent
                                )
                            )
                        )
                )
            }

            // QML transport is fixed to the bottom band, with 28dp side margins.
                    QmlLyricTransport(
                        state = state,
                        controller = controller,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(horizontal = 28.dp),
                        formatMs = ::formatMs
                    )
                }
            }
        }
    }
}

@Composable
private fun QmlLyricBackdrop(
    state: PlayerUiState,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // The setting is deliberately not an animation switch anymore:
    // true = blurred artwork, false = solid Monet/MD3 colour. There is no
    // moving orb layer in either mode.
    val useCover = state.lyricCoverBackground
    // The scheme itself is regenerated from coverSeed. This is therefore one
    // cover-derived Monet colour, not an artwork layer or a multi-colour wash.
    val solidColor = if (state.dark) scheme.surfaceContainerHighest else scheme.primaryContainer
    // Same stack as MiniPlayer, scaled up to the page: a softly blurred artwork
    // at low alpha, then one strong translucent surface panel plus two faint
    // Monet tints on top. The full-screen version previously kept only a 10–18%
    // scrim, so the raw cover colour came through at almost full saturation and
    // looked garish; this reuses the muted MiniPlayer balance instead.
    Box(modifier = modifier.background(scheme.surfaceContainerHigh)) {
        if (useCover && coverBitmap != null) {
            Crossfade(
                targetState = state.trackKey to coverBitmap,
                animationSpec = tween(420),
                label = "qml_cover_background"
            ) { (_, bitmap) ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(34.dp)
                        .graphicsLayer { alpha = 0.78f }
                )
            }
        } else {
            // No cover pixels are rendered when "封面背景" is disabled: the
            // artwork layer is replaced by its own flat Monet colour so the
            // glass panel below still has something to sit on.
            Box(Modifier.fillMaxSize().background(solidColor))
        }
        WindowGlassBackdrop(
            modifier = Modifier.fillMaxSize(),
            radius = 42f,
            overlay = scheme.surfaceContainerHigh.copy(alpha = 0.76f)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.primaryContainer.copy(alpha = 0.22f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.primary.copy(alpha = 0.08f))
        )
    }
}

@Composable
private fun QmlLyricCover(
    state: PlayerUiState,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    fixedSize: androidx.compose.ui.unit.Dp? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val available = minOf(maxWidth - 96.dp, maxHeight - 80.dp).coerceAtLeast(160.dp)
        val coverSize = fixedSize ?: available.coerceAtMost(420.dp)
        Box(
            modifier = Modifier
                .size(coverSize)
                .align(Alignment.Center)
                .clip(RoundedCornerShape((coverSize * 0.06f).coerceAtMost(24.dp)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap
                ),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = state.trackKey to coverBitmap,
                animationSpec = tween(250),
                label = "qml_cover_transition"
            ) { (_, bitmap) ->
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = ComposeColor.White.copy(alpha = 0.70f)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun QmlLyricLandscapeChrome(
    state: PlayerUiState,
    controller: PlayerController,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    openArtist: (Long) -> Unit,
    close: () -> Unit,
    showCloseButton: Boolean = false,
    offsetPanelOpen: Boolean,
    onOffsetPanelOpenChange: (Boolean) -> Unit,
    offsetValue: Float,
    onOffsetValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    formatMs: (Long) -> String
) {
    val coverOnly = state.lyricsCoverOnly || state.coverModeManual

    BoxWithConstraints(modifier = modifier) {
        val regionWidth = if (coverOnly) maxWidth else maxWidth / 2
        val targetCoverSize = minOf(
            regionWidth - 96.dp,
            maxHeight - 248.dp,
            360.dp
        ).coerceAtLeast(120.dp)
        val coverSize by animateDpAsState(
            targetValue = targetCoverSize,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = 180f
            ),
            label = "qml_landscape_cover_size"
        )
        val centerX = regionWidth / 2
        val columnHeight = coverSize + 196.dp

        if (!coverOnly) {
            QmlLyricColumnRestored(
                state = state,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(maxWidth / 2)
                    .align(Alignment.CenterEnd)
            )
        }

        Column(
            modifier = Modifier
                .width(coverSize)
                .height(columnHeight)
                .align(Alignment.CenterStart)
                .offset(x = centerX - coverSize / 2)
        ) {
            QmlLyricCover(
                state = state,
                coverBitmap = coverBitmap,
                modifier = Modifier.size(coverSize),
                fixedSize = coverSize,
                onTap = { if (!state.lyricsCoverOnly) controller.setCoverMode(false) }
            )
            Text(
                text = state.title.ifBlank { "未在播放" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 1000
                    ),
                color = ComposeColor.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.artist.ifBlank { "未知歌手" },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .then(
                        if (state.artistId != 0L) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { openArtist(state.artistId) }
                        } else Modifier
                    )
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 1000
                    ),
                color = ComposeColor.White.copy(alpha = 0.70f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val transportEnabled = !state.privateFmMode
                IconButton(
                    onClick = { controller.cyclePlayMode() },
                    enabled = transportEnabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        when (state.playMode) {
                            1 -> Icons.Default.Shuffle
                            2 -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "播放模式",
                        tint = if (state.playMode == 0) ComposeColor.White.copy(alpha = 0.60f)
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { controller.prev() },
                    enabled = transportEnabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "上一首",
                        tint = ComposeColor.White.copy(alpha = if (transportEnabled) 1f else 0.38f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                FilledIconButton(
                    onClick = { controller.toggle() },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { controller.next() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = ComposeColor.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = { controller.toggleLike() },
                    enabled = state.likeable,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (state.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (state.liked) ComposeColor(0xFFFF5277)
                        else ComposeColor.White.copy(alpha = 0.60f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp, start = 6.dp, end = 6.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showCloseButton) {
                    IconButton(onClick = close, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "关闭歌词",
                            tint = ComposeColor.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }
                Spacer(Modifier.weight(1f))
                if (!coverOnly) {
                    IconButton(
                        onClick = { controller.setCoverMode(true) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Album,
                            contentDescription = "显示封面",
                            tint = ComposeColor.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { onOffsetPanelOpenChange(!offsetPanelOpen) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "歌词偏移",
                        tint = ComposeColor.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = offsetPanelOpen,
                enter = fadeIn(tween(160)) + scaleIn(
                    initialScale = 0.9f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                    animationSpec = tween(220)
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f),
                    animationSpec = tween(150)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 48.dp)
                    .zIndex(10f)
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(0.86f),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("歌词偏移", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (offsetValue > 0f) "+${offsetValue.toInt()} ms"
                                else "${offsetValue.toInt()} ms",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "仅对当前歌曲生效 · 负值提前，正值延后",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Slider(
                            value = offsetValue.coerceIn(-5000f, 5000f),
                            onValueChange = {
                                onOffsetValueChange((it / 50f).roundToInt() * 50f)
                            },
                            valueRange = -5000f..5000f,
                            steps = 199,
                            onValueChangeFinished = {
                                controller.setLyricOffset(offsetValue.toInt())
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("提前 5 秒", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("延后 5 秒", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = {
                                onOffsetValueChange(0f)
                                controller.resetLyricOffset()
                            },
                            enabled = offsetValue != 0f,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("重置为 0")
                        }
                    }
                }
            }
        }
    }
}

/*
 * Snapshot-compatible lyric renderer.
 *
 * This follows the renderer recovered from qplayer-debug.apk (version 1.3.0,
 * versionCode 67): the list is anchored at 35% of the viewport and is moved
 * by one persistent spring driven by the current lyric index. Keeping the
 * animation in one coroutine is important; separate per-frame list and row
 * animations were the source of the vertical twitch in the later rewrite.
 */

/** Horizontal inset kept on both sides of every lyric row. */
private val ROW_H_PADDING = 8.dp

/** Gap between the intro indicators and the first lyric line. */
private val INTRO_BLOB_GAP = 22.dp

/** Intro indicator container size, relative to the lyric font size. */
private const val INTRO_BLOB_SIZE_SCALE = 1.6f

/** Fraction of the lyric viewport the active line is anchored at (see centring). */
private const val INTRO_LYRIC_ANCHOR = 0.35f

/** Only fill the blank space when the intro is long enough to divide in three. */
private const val INTRO_BLOB_MIN_MS = 2_000L

/** The third intro beat always gets this much time, hence this much colour. */
private const val INTRO_LAST_BEAT_MS = 2_000L

/** Slight lift of the line that is currently being sung (弹簧动效). */
private const val LYRIC_WORD_LIFT_DP = -2.5f

/**
 * Type-size candidates for the strict lyric fit, largest first. The first one
 * whose line count fits the measured width in two lines wins; the last one is
 * used as a floor so a pathological line can never shrink to nothing.
 */
private val LYRIC_FIT_FACTORS = floatArrayOf(1f, 0.92f, 0.84f, 0.76f, 0.68f)

/**
 * Shared ripple bookkeeping for the lyric column. It only tracks *which line is
 * focused* and bumps [pulse] when that line really changes — never on playback
 * progress ticks, which would restart the wave every poll interval and make the
 * whole column twitch.
 */
/** Distance the list must travel for [item]'s centre to land on the 35% anchor. */
private fun lyricAnchorDelta(listState: LazyListState, item: LazyListItemInfo): Float {
    val info = listState.layoutInfo
    val anchor = info.viewportStartOffset +
        (info.viewportEndOffset - info.viewportStartOffset) * 0.35f
    return item.offset + item.size / 2f - anchor
}

/**
 * Centres the focused lyric line exactly once. [animationSpec] null means
 * "place it now", used right after a track change. This is the only thing that
 * ever moves the list, so the centring, the ripple and the per-word colouring
 * stay three independent systems.
 */
private suspend fun LazyListState.centerLyricLine(
    index: Int,
    animationSpec: androidx.compose.animation.core.AnimationSpec<Float>?
) {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visible == null) {
        animateScrollToItem(index)
        withFrameNanos { }
        val placed = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val delta = lyricAnchorDelta(this, placed)
        if (kotlin.math.abs(delta) < 0.5f) return
        if (animationSpec == null) scrollBy(delta) else animateScrollBy(delta, animationSpec)
    } else {
        val delta = lyricAnchorDelta(this, visible)
        if (kotlin.math.abs(delta) < 0.5f) return
        if (animationSpec == null) scrollBy(delta) else animateScrollBy(delta, animationSpec)
    }
}

@Composable
private fun QmlLyricColumnRestored(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onLineClick: (Long) -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier) {
        val centerPadding = (maxHeight * 0.42f).coerceAtLeast(32.dp)
        val listState = rememberLazyListState()

        // Strictly measured text column: the row keeps ROW_H_PADDING on each
        // side, and the *widest* row (the enlarged current line, 1.12x) must
        // still end inside the screen. Every row therefore lays its text out in
        // contentWidth / maxRowScale, which is the real, measured budget — no
        // line can be clipped by the window edge any more.
        val rowContentWidth =
            (maxWidth - ROW_H_PADDING * 2).coerceAtLeast(1.dp)
        val maxRowScale = if (state.lyricScale) 1.12f else 1f
        val lyricTextWidth = rowContentWidth / maxRowScale

        // Centring is a separate system: it runs exactly once per focused line
        // (and once per track/lyric load) and then stops. Nothing re-centres on
        // playback ticks, and the ripple never asks for another scroll, so the
        // two can never pull the column in opposite directions. 弹簧动效 picks
        // the physics of this single glide (see the setting's own description).
        val lyricScrollSpec: androidx.compose.animation.core.AnimationSpec<Float> =
            if (state.lyricSpring) {
                spring(dampingRatio = 0.85f, stiffness = 90f)
            } else {
                tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            }

        LaunchedEffect(state.trackKey, state.lyricsRevision, state.lyrics.size) {
            withFrameNanos { }
            var initialIndex = state.lyricIndex
            if (initialIndex !in state.lyrics.indices) {
                initialIndex = state.lyrics.indexOfFirst { it.startMs() >= 0L }
            }
            if (initialIndex !in state.lyrics.indices) return@LaunchedEffect
            listState.scrollToItem(initialIndex)
            withFrameNanos { }
            listState.centerLyricLine(initialIndex, animationSpec = null)
        }

        LaunchedEffect(state.lyricIndex) {
            val index = state.lyricIndex
            if (index !in state.lyrics.indices) return@LaunchedEffect
            listState.centerLyricLine(index, lyricScrollSpec)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(vertical = centerPadding),
            verticalArrangement = Arrangement.spacedBy(
                (state.lyricLineSpacing.coerceIn(100, 250) * 24f / 200f)
                    .dp.coerceAtLeast(10.dp)
            ),
            horizontalAlignment = Alignment.Start
        ) {
            itemsIndexed(
                items = state.lyrics,
                key = { index, line -> "apk_lyric_${line.startMs()}_$index" }
            ) { index, line ->
                LegacyQmlLyricRow(
                    state = state,
                    line = line,
                    index = index,
                    textWidth = lyricTextWidth,
                    onClick = { onLineClick(line.startMs()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.lyrics.isEmpty()) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "暂无歌词",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- Intro blobs -----------------------------------------------------
        // A long intro leaves the area above the first line blank. Three
        // contained expressive indicators (the Widget.Material3.LoadingIndicator
        // .Contained look) sit there, on the same left edge as the lyric text so
        // they read as the line that is about to start, just above the first line
        // with a small gap. The intro is split into three equal beats and they
        // take turns: only the current beat animates, which is also what keeps
        // them in step — running them together left their shapes out of phase.
        // A beat that has finished freezes as a very light grey dot to show it is
        // done, the next one starts, and everything holds still while playback is
        // paused. Size follows the lyric font-size setting, colours come from the
        // Monet scheme, and the row clears out once the first line arrives.
        val firstLine = state.lyrics.firstOrNull()
        val firstLineStartMs = firstLine?.startMs() ?: 0L
        val introMs = firstLineStartMs.coerceAtLeast(0L)
        val introActive = state.lyrics.isNotEmpty() &&
            introMs >= INTRO_BLOB_MIN_MS &&
            state.lyricPositionMs < firstLineStartMs
        val introAlpha by animateFloatAsState(
            targetValue = if (introActive) 1f else 0f,
            animationSpec = tween(280),
            label = "intro_blob_alpha"
        )
        if (introAlpha > 0.01f) {
            val introScheme = MaterialTheme.colorScheme
            val introColors = listOf(
                introScheme.primary,
                introScheme.tertiary,
                introScheme.secondary
            )
            // Not-played beats are the faint grey; a beat that has played through
            // takes its Monet colour. So the row fills up from grey to colour as
            // the intro runs out.
            val introIdleColor = introScheme.onSurface.copy(alpha = 0.12f)
            // Which beat is being played. The last one is always given at least
            // INTRO_LAST_BEAT_MS so it is properly lit before the lyrics start:
            // splitting the intro into plain thirds left it with a fraction of a
            // second on a short intro, and it never turned colour at all before
            // the first line arrived. The two earlier beats share the rest.
            val introThirdWindow = (introMs / 3).coerceAtLeast(INTRO_LAST_BEAT_MS)
            val introThirdStart = (introMs - introThirdWindow).coerceAtLeast(0L)
            val introSecondStart = introThirdStart / 2
            val introBeat = when {
                introMs <= 0L -> 0
                state.lyricPositionMs >= introThirdStart -> 2
                state.lyricPositionMs >= introSecondStart -> 1
                else -> 0
            }
            val density = LocalDensity.current
            // [size] is the container box, so this is a good deal larger than the
            // lyric text; it still tracks the font-size setting.
            val blobSize = with(density) {
                (state.lyricFontSize.coerceIn(14, 40).toFloat() * INTRO_BLOB_SIZE_SCALE)
                    .sp.toDp()
            }
            // Where the first line will be: the lyric column anchors the active
            // row's centre at 35% of its height, so the first line's top is there
            // minus half a row. Deriving it from the settings instead of the live
            // layout keeps the gap stable even before the list has measured.
            val firstRowHeight = run {
                val textHeight = with(density) {
                    (blobSize.value / INTRO_BLOB_SIZE_SCALE * 0.92f).sp.toDp()
                }
                var height = textHeight * state.lyricLineSpacing.coerceIn(100, 250) / 100f + 12.dp
                if (!firstLine?.romaji.isNullOrBlank()) height += 15.dp
                if (!firstLine?.translation.isNullOrBlank()) height += 15.dp
                height
            }
            val blobTopPadding = (
                maxHeight * INTRO_LYRIC_ANCHOR -
                    firstRowHeight / 2 -
                    INTRO_BLOB_GAP -
                    blobSize
                ).coerceAtLeast(0.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = ROW_H_PADDING, top = blobTopPadding)
                    .graphicsLayer { alpha = introAlpha },
                horizontalArrangement = Arrangement.spacedBy(blobSize * 0.25f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    // All three are composed together, so their animations start
                    // in the same frame and their shapes never drift apart. The
                    // intro's thirds are marked by colour instead: a beat that has
                    // not been played is the faint grey, one that has been played
                    // through takes its own Monet colour.
                    Box(Modifier.size(blobSize)) {
                        ExpressiveLoadingIndicator(
                            size = blobSize,
                            color = if (index < introBeat) introColors[index] else introIdleColor,
                            withContainer = true,
                            running = state.playing
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyQmlLyricRow(
    state: PlayerUiState,
    line: LyricLine,
    index: Int,
    textWidth: Dp,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val active = state.lyricIndex == index
    val distance = kotlin.math.abs(index - state.lyricIndex)
    // A size emphasis always slides the glyphs sideways, because the text is
    // left-aligned and therefore grows away from its own left edge. Keeping the
    // range small and the transition short is what keeps that from reading as a
    // tremble while the column scrolls; a slow spring left the whole visible
    // block creeping sideways for a third of a second.
    val targetScale = when {
        !state.lyricScale -> 1f
        active -> 1.10f
        distance == 1 -> 0.94f
        else -> 0.90f
    }
    val rowAnimation: androidx.compose.animation.core.AnimationSpec<Float> =
        tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = rowAnimation,
        label = "lyric_scale"
    )
    val targetAlpha = when {
        active -> 1f
        distance == 1 -> 0.68f
        distance == 2 -> 0.42f
        else -> 0.22f
    }
    val alphaAnimation: androidx.compose.animation.core.AnimationSpec<Float> =
        if (state.lyricSpring) spring<Float>() else tween<Float>(240)
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = alphaAnimation,
        label = "lyric_alpha"
    )

    // Ripple is disabled; keep this row layout-free so lyric changes cannot
    // introduce a second vertical motion.
    val scheme = MaterialTheme.colorScheme
    val normalColor = if (state.lyricMd3Color) {
        scheme.onSurfaceVariant
    } else {
        ComposeColor.White.copy(alpha = 0.46f)
    }
    val activeColor = if (state.lyricMd3Color) scheme.onSurface else ComposeColor.White
    val sungColor = if (state.lyricMd3Color) scheme.primary else ComposeColor.White
    val colorTarget = if (active) activeColor else normalColor
    val colorAnimation: androidx.compose.animation.core.AnimationSpec<ComposeColor> =
        if (state.lyricSpring) spring<ComposeColor>() else tween<ComposeColor>(240)
    val color by animateColorAsState(
        targetValue = colorTarget,
        animationSpec = colorAnimation,
        label = "lyric_color"
    )

    val baseSize = (state.lyricFontSize * 0.92f).coerceIn(14f, 36f)
    // Strict fit: textWidth is the measured screen width divided by the largest
    // row scale, so two lines in that budget always end inside the window. A
    // line that would still need more than two lines steps its type size down
    // (never below 68% of the configured size, so it stays legible). The value
    // depends only on the line and the measured width, so it is stable per line
    // and never animates or jitters.
    val textMeasurer = rememberTextMeasurer()
    val sourceText = line.text().trim()
    val textBudgetPx = with(LocalDensity.current) { textWidth.roundToPx() }
    val fitFactor = remember(sourceText, baseSize, textBudgetPx, textMeasurer) {
        if (sourceText.isEmpty() || textBudgetPx <= 0) {
            1f
        } else {
            var chosen = LYRIC_FIT_FACTORS.last()
            for (factor in LYRIC_FIT_FACTORS) {
                val measured = textMeasurer.measure(
                    text = sourceText,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = (baseSize * factor).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlexBold
                    ),
                    constraints = Constraints(maxWidth = textBudgetPx)
                )
                if (measured.lineCount <= 2) {
                    chosen = factor
                    break
                }
            }
            chosen
        }
    }
    val fittedSize = baseSize * fitFactor
    val lineHeight = (
        fittedSize * state.lyricLineSpacing.coerceIn(100, 250) / 100f
    ).coerceAtLeast(fittedSize * 1.10f)
    val nextLineStart = state.lyrics.getOrNull(index + 1)?.startMs()
        ?: (line.startMs() + 960L)
    val units = remember(line, nextLineStart) { expandLyricUnits(line, nextLineStart) }
    // One interpolation drives the whole line. Animating every character separately
    // meant dozens of animation states waking per frame, each one rebuilding the
    // annotated string and re-laying the text out — the visible stutter while a line
    // is being sung. A single animated clock position is enough: each character's
    // progress is then just arithmetic.
    val lineWindowStart = units.firstOrNull()?.startMs ?: line.startMs()
    val lineWindowEnd = units.lastOrNull()?.endMs ?: (lineWindowStart + 1L)
    val animatedLyricPos = if (active) {
        val animated by animateFloatAsState(
            targetValue = state.lyricPositionMs
                .coerceIn(lineWindowStart, lineWindowEnd)
                .toFloat(),
            animationSpec = tween(110, easing = LinearEasing),
            label = "lyric_sweep"
        )
        animated
    } else {
        state.lyricPositionMs.toFloat()
    }
    val wordProgress = ArrayList<Float>(units.size)
    units.forEach { unit ->
        val span = (unit.endMs - unit.startMs).coerceAtLeast(1L).toFloat()
        wordProgress += ((animatedLyricPos - unit.startMs) / span).coerceIn(0f, 1f)
    }
    val text = LegacyQmlLyricAnnotatedText(
        units = units,
        progress = wordProgress,
        active = active,
        normalColor = normalColor,
        sungColor = sungColor
    )
    val blurRadius = if (state.lyricEdgeBlur && distance > 0) {
        (distance * 1.8f).coerceAtMost(10f).dp
    } else 0.dp
    val shadow = when {
        state.lyricGlow && active -> Shadow(
            color = scheme.primary.copy(alpha = 0.72f),
            offset = Offset(0f, 1f),
            blurRadius = 12f
        )
        state.lyricShadow -> Shadow(
            color = ComposeColor.Black.copy(alpha = 0.35f),
            offset = Offset(0f, 2f),
            blurRadius = 4f
        )
        else -> null
    }
    val particleProgress = if (wordProgress.isNotEmpty()) {
        wordProgress.average().toFloat()
    } else {
        ((state.lyricPositionMs - line.startMs()).toFloat() /
            (line.endMs() - line.startMs()).coerceAtLeast(1L).toFloat())
    }.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            // The padding must sit OUTSIDE the scaled layer. Inside it, every
            // row scaled its own inset as well (the 1.12x line started 9dp in
            // instead of 8dp, the 0.85x ones at 6.8dp), so the left edges never
            // lined up. Outside, all rows share one exact left edge.
            .padding(start = ROW_H_PADDING, end = ROW_H_PADDING, top = 12.dp)
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationY = 0f
            }
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
        horizontalAlignment = Alignment.Start
    ) {
        Box(Modifier.width(textWidth)) {
            Text(
                text = text,
                color = ComposeColor.Unspecified,
                textAlign = TextAlign.Start,
                fontSize = fittedSize.sp,
                lineHeight = lineHeight.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansFlexBold,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                style = androidx.compose.ui.text.TextStyle(shadow = shadow),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.lyricParticles && active) {
                LyricParticleField(
                    progress = particleProgress,
                    color = MaterialTheme.colorScheme.primary,
                    text = line.text().trim(),
                    modifier = Modifier
                        .matchParentSize()
                )
            }
        }
        if (!line.romaji.isNullOrBlank()) {
            Text(
                text = line.romaji.trim(),
                modifier = Modifier
                    .width(textWidth)
                    .padding(top = 3.dp),
                color = color.copy(alpha = 0.72f),
                textAlign = TextAlign.Start,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansFlexBold
            )
        }
        if (!line.translation.isNullOrBlank()) {
            Text(
                text = line.translation.trim(),
                modifier = Modifier
                    .width(textWidth)
                    .padding(top = 2.dp),
                color = color.copy(alpha = 0.68f),
                textAlign = TextAlign.Start,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansFlexBold
            )
        }
    }
}

@Composable
private fun LyricParticleField(
    progress: Float,
    color: ComposeColor,
    text: String,
    modifier: Modifier = Modifier
) {
    val phase by rememberInfiniteTransition(label = "lyric_particles").animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "lyric_particle_phase"
    )
    Canvas(modifier) {
        val count = 24
        // Keep the convergence point inside the measured text box. At 1.0,
        // placing it at the right edge would clip the entire last cluster and
        // make the feature appear disabled on short lines.
        val edge = 12f * density
        val glyphCount = text.trim().length.coerceAtLeast(1)
        val glyphIndex = (progress * (glyphCount - 1)).coerceIn(0f, (glyphCount - 1).toFloat())
        val glyphWidth = (size.width / glyphCount.toFloat()).coerceIn(7f * density, 28f * density)
        val glyphCenter = (size.width * ((glyphIndex + 0.5f) / glyphCount.toFloat()))
            .coerceIn(edge, size.width - edge)
        repeat(count) { i ->
            val seed = i * 37.0f
            // Keep particles close to the lyric box instead of floating far away.
            val spread = (5f + (i % 4) * 2f) * density
            val orbit = phase + seed
            val slot = (i * 17 % count) / (count - 1f).coerceAtLeast(1f) - 0.5f
            val targetX = (glyphCenter + slot * glyphWidth * 0.82f)
                .coerceIn(edge, size.width - edge)
            val convergence = (0.25f + 0.75f * progress).coerceIn(0.25f, 1f)
            val x = targetX + sin(orbit) * spread * (1f - convergence)
            val y = (size.height / 2f + cos(orbit * 1.31f) * spread * 0.32f * (1f - convergence))
                .coerceIn(2f * density, size.height - 2f * density)
            // The glow is intentionally visible even at the beginning of a
            // line; previously the sub-pixel dots and low alpha made the
            // enabled setting indistinguishable from no effect.
            val alpha = (0.32f + 0.58f * progress).coerceIn(0f, 0.92f)
            drawCircle(
                color = color.copy(alpha = alpha * 0.20f),
                radius = (3.8f + (i % 3) * 0.8f) * density,
                center = Offset(x, y)
            )
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = (1.35f + (i % 3) * 0.45f) * density,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * One highlightable character and the slice of the line's timing it owns. Lyric
 * sources hand out units of different sizes — a Chinese character in one song, a
 * whole English word in the next — and the sweep used to be as coarse as
 * whatever the source provided, which is why some songs scrolled a letter at a
 * time and others a word at a time.
 */
private class LyricUnit(val text: String, val startMs: Long, val endMs: Long)

/**
 * Flattens a line into per-character units so the highlight always sweeps
 * character by character (letter by letter in Latin text) no matter how the
 * source timed it. Each timed unit keeps its own window and divides it evenly
 * between its characters; [nextLineStartMs] is the window for units that carry
 * no duration of their own, which is how plain LRC lines are timed.
 */
private fun expandLyricUnits(line: LyricLine, nextLineStartMs: Long): List<LyricUnit> {
    val lineStart = line.startMs()
    val lineEnd = nextLineStartMs.coerceAtLeast(lineStart + 1L)
    val syllables = line.syllables
    if (syllables.isEmpty()) {
        return spreadLyricUnits(line.text().trim(), lineStart, lineEnd)
    }
    val units = ArrayList<LyricUnit>(line.text().length + 4)
    syllables.forEach { syllable ->
        val text = syllable.text ?: return@forEach
        if (text.isEmpty()) return@forEach
        val timed = syllable.durationMs > 0L
        units += spreadLyricUnits(
            text = text,
            startMs = if (timed) syllable.startMs else lineStart,
            endMs = if (timed) syllable.startMs + syllable.durationMs else lineEnd
        )
    }
    return units
}

/** One [LyricUnit] per character, sharing [startMs]..[endMs] evenly. */
private fun spreadLyricUnits(text: String, startMs: Long, endMs: Long): List<LyricUnit> {
    if (text.isEmpty()) return emptyList()
    val span = (endMs - startMs).coerceAtLeast(1L)
    val pieces = ArrayList<String>(text.length)
    var index = 0
    while (index < text.length) {
        // Keep surrogate pairs (emoji) whole instead of tearing them in half.
        val width = if (index + 1 < text.length &&
            text[index].isHighSurrogate() && text[index + 1].isLowSurrogate()
        ) 2 else 1
        pieces += text.substring(index, index + width)
        index += width
    }
    return pieces.mapIndexed { position, piece ->
        LyricUnit(
            text = piece,
            startMs = startMs + span * position / pieces.size,
            endMs = startMs + span * (position + 1) / pieces.size
        )
    }
}

@Composable
private fun LegacyQmlLyricAnnotatedText(
    units: List<LyricUnit>,
    progress: List<Float>,
    active: Boolean,
    normalColor: ComposeColor,
    sungColor: ComposeColor
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        if (units.isEmpty()) return@buildAnnotatedString
        if (!active) {
            // Nothing on this row is changing, so one span is enough — and one span
            // keeps Text's layout cache hit instead of re-laying the line out.
            withStyle(SpanStyle(color = normalColor)) {
                units.forEach { append(it.text) }
            }
            return@buildAnnotatedString
        }
        // Only the characters straddling the sweep are mid-colour; everything before
        // them is sung and everything after is untouched, so those collapse into one
        // span each: three spans per row instead of one per character, which is what
        // was making the text re-lay-out every frame.
        val bucketOf: (Float) -> Int = { p -> if (p >= 0.999f) 1 else if (p <= 0.001f) 0 else 2 }
        var index = 0
        while (index < units.size) {
            val startProgress = progress.getOrNull(index) ?: 0f
            val bucket = bucketOf(startProgress)
            if (bucket == 2) {
                withStyle(SpanStyle(color = lyricMixColor(normalColor, sungColor, startProgress))) {
                    append(units[index].text)
                }
                index++
            } else {
                var end = index
                while (end + 1 < units.size && bucketOf(progress.getOrNull(end + 1) ?: 0f) == bucket) end++
                val color = if (bucket == 1) sungColor else normalColor
                withStyle(SpanStyle(color = color)) {
                    for (i in index..end) append(units[i].text)
                }
                index = end + 1
            }
        }
    }
}

@Composable
private fun QmlLyricColumnRestoredExperimental(
    state: PlayerUiState,
    modifier: Modifier = Modifier
) {
    val prepared = remember(state.lyrics, state.lyricsRevision, state.lyricLinearAnim) {
        LyricTimeline.prepare(state.lyrics, state.lyricLinearAnim)
    }
    val lines = prepared.lines
    val listState = rememberLazyListState()
    val livePosition by rememberUpdatedState(state.lyricPositionMs)
    var smoothPositionMs by remember(state.trackKey) {
        mutableLongStateOf(state.lyricPositionMs)
    }

    // The original QPlayer page uses one stable playback clock for both the
    // highlight and the scroll.  Keep this lightweight frame interpolation,
    // but do not feed it into layout measurements or per-character transforms.
    LaunchedEffect(state.trackKey, state.playing, state.lyricsRevision, lines.size) {
        var observed = livePosition
        var base = observed
        var baseFrame = 0L
        while (true) {
            val frame = withFrameNanos { it }
            val latest = livePosition
            if (latest != observed) {
                observed = latest
                base = latest
                baseFrame = frame
            }
            if (baseFrame == 0L) baseFrame = frame
            smoothPositionMs = if (state.playing) {
                base + ((frame - baseFrame) / 1_000_000L).coerceAtLeast(0L)
            } else base
        }
    }

    val activeGroup = lyricVisualGroupIndex(prepared.groups, smoothPositionMs)
    val targetLine = prepared.groups.getOrNull(activeGroup)?.from
        ?.takeIf { it in lines.indices }
        ?: lyricIndexForPosition(lines, smoothPositionMs)

    // A single target animation is the Compose equivalent of the old renderer's
    // scroll spring. It is restarted only when the lyric group changes, never on
    // every clock tick, so a paused line cannot visibly jump or reset.
    LaunchedEffect(state.trackKey, state.lyricsRevision, targetLine, lines.size) {
        if (targetLine !in lines.indices) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == targetLine }
        if (visible == null) {
            listState.scrollToItem(targetLine)
            withFrameNanos { }
        }
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == targetLine } ?: return@LaunchedEffect
        val info = listState.layoutInfo
        val anchor = info.viewportStartOffset +
            (info.viewportEndOffset - info.viewportStartOffset) * 0.35f
        listState.animateScrollBy(
            item.offset + item.size / 2f - anchor,
            animationSpec = tween(
                durationMillis = 420,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    BoxWithConstraints(modifier = modifier) {
        val topPadding = (maxHeight * 0.35f).coerceAtLeast(32.dp)
        val bottomPadding = (maxHeight * 0.65f).coerceAtLeast(48.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "restored_lyric_${line.startMs()}_$index" }
            ) { index, line ->
                val groupIndex = prepared.lineToGroup.getOrNull(index) ?: -1
                val group = prepared.groups.getOrNull(groupIndex)
                val focused = groupIndex == activeGroup
                val active = if (group == null) 0f else lyricActiveK(
                    smoothPositionMs, group.startMs, group.endMs
                )
                val distance = kotlin.math.abs(index - targetLine)
                val main = !LyricTimeline.isBackground(line.vocalChannel)
                val textColor = if (state.lyricMd3Color) {
                    if (focused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    ComposeColor.White.copy(alpha = if (focused) 1f else 0.42f)
                }
                val alpha = if (main) {
                    if (focused) 1f else (0.55f - distance * 0.06f).coerceAtLeast(0.22f)
                } else (0.26f + active * 0.42f)
                val baseSize = state.lyricFontSize.coerceIn(14, 40).toFloat() *
                    if (main) 1f else 0.72f
                val alignRight = line.vocalChannel == LyricLine.VocalChannel.DUET_RIGHT ||
                    line.vocalChannel == LyricLine.VocalChannel.BACKGROUND_RIGHT
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { this.alpha = alpha }
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
                ) {
                    Text(
                        text = line.text().trim(),
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor,
                        fontSize = baseSize.sp,
                        lineHeight = (baseSize * 1.22f).sp,
                        fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = GoogleSansFlexBold,
                        textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Clip
                    )
                    if (!line.romaji.isNullOrBlank()) {
                        Text(
                            text = line.romaji!!.trim(),
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor.copy(alpha = 0.72f),
                            fontSize = (baseSize * 0.5f).sp,
                            maxLines = 2,
                            textAlign = if (alignRight) TextAlign.End else TextAlign.Start
                        )
                    }
                    if (!line.translation.isNullOrBlank()) {
                        Text(
                            text = line.translation!!.trim(),
                            modifier = Modifier.fillMaxWidth(),
                            color = textColor.copy(alpha = 0.72f),
                            fontSize = (baseSize * 0.5f).sp,
                            maxLines = 2,
                            textAlign = if (alignRight) TextAlign.End else TextAlign.Start
                        )
                    }
                }
            }
            if (lines.isEmpty()) {
                item {
                    Text(
                        "暂无歌词",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QmlLyricColumn(
    state: PlayerUiState,
    modifier: Modifier = Modifier
) {
    val prepared = remember(state.lyrics, state.lyricsRevision, state.lyricLinearAnim) {
        LyricTimeline.prepare(state.lyrics, state.lyricLinearAnim)
    }
    val lines = prepared.lines
    val listState = rememberLazyListState()
    val livePosition by rememberUpdatedState(state.lyricPositionMs)
    var smoothPositionMs by remember(state.trackKey) {
        mutableLongStateOf(state.lyricPositionMs)
    }
    // The controller publishes a coarse UI snapshot. QPlayer's renderer uses
    // the audio clock every frame, so interpolate between snapshots once and
    // feed the same time to scroll, fill, and lift. This avoids independent
    // per-word animations fighting the list layout.
    LaunchedEffect(state.trackKey, state.playing, state.lyricsRevision, lines.size) {
        var lastObserved = livePosition
        var basePosition = lastObserved
        var baseFrameNs = 0L
        while (true) {
            val frameNs = withFrameNanos { it }
            val latest = livePosition
            if (latest != lastObserved) {
                lastObserved = latest
                basePosition = latest
                baseFrameNs = frameNs
            }
            if (baseFrameNs == 0L) baseFrameNs = frameNs
            val elapsedMs = if (state.playing) {
                ((frameNs - baseFrameNs) / 1_000_000L).coerceAtLeast(0L)
            } else 0L
            smoothPositionMs = (basePosition + elapsedMs).coerceAtLeast(0L)
        }
    }
    val currentIndex = lyricIndexForPosition(lines, smoothPositionMs)
    // Follow the prepared timeline's active group instead of the newest row.
    // Duet/background rows can start inside the same group; using the raw row
    // index made LazyColumn retarget by one item while the group was still
    // singing, which looked like a vertical twitch during word playback.
    val currentGroupIndex = lyricVisualGroupIndex(prepared.groups, smoothPositionMs)
    val activeLineIndex = prepared.groups.getOrNull(currentGroupIndex)?.from
        ?.takeIf { it in lines.indices } ?: currentIndex
    val liveActiveLineIndex by rememberUpdatedState(activeLineIndex)
    val liveCurrentGroupIndex by rememberUpdatedState(currentGroupIndex)
    val liveSpring by rememberUpdatedState(state.lyricSpring)
    var rippleElapsedMs by remember(state.trackKey, state.lyricsRevision) {
        mutableLongStateOf(900L)
    }
    var rippleDistancePx by remember(state.trackKey, state.lyricsRevision) {
        mutableFloatStateOf(0f)
    }
    var rippleDirection by remember(state.trackKey, state.lyricsRevision) {
        mutableIntStateOf(1)
    }

    BoxWithConstraints(modifier = modifier) {
        val topPadding = (maxHeight * 0.35f).coerceAtLeast(32.dp)
        val bottomPadding = (maxHeight * 0.65f).coerceAtLeast(48.dp)

        // One persistent spring follows the QML/Skia anchor. It is intentionally
        // not restarted for every lyric tick, which keeps the line transition
        // continuous and also handles lyrics arriving after playback started.
        LaunchedEffect(state.trackKey, state.lyricsRevision, lines.size) {
            if (lines.isEmpty()) return@LaunchedEffect

            suspend fun centerItem(index: Int) {
                val item = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == index } ?: return
                val info = listState.layoutInfo
                val anchor = info.viewportStartOffset +
                    (info.viewportEndOffset - info.viewportStartOffset) * 0.35f
                listState.scroll {
                    scrollBy(item.offset + item.size / 2f - anchor)
                }
            }

            val initial = liveActiveLineIndex.takeIf { it in lines.indices } ?: 0
            listState.scrollToItem(initial)
            withFrameNanos { }
            centerItem(initial)

            var velocity = 0f
            var lastFrameNs = 0L
            var previousTarget = initial
            var rippleStartNs = 0L
            while (true) {
                val frameNs = withFrameNanos { it }
                if (lastFrameNs == 0L) {
                    lastFrameNs = frameNs
                    continue
                }
                val dt = ((frameNs - lastFrameNs) / 1_000_000_000f)
                    .coerceIn(0.008f, 0.05f)
                lastFrameNs = frameNs
                val target = liveActiveLineIndex.takeIf { it in lines.indices } ?: previousTarget
                if (target != previousTarget) {
                    if (liveSpring) {
                        val oldItem = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == previousTarget }
                        val newItem = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == target }
                        rippleDistancePx = if (oldItem != null && newItem != null) {
                            kotlin.math.abs(
                                (newItem.offset + newItem.size / 2f) -
                                    (oldItem.offset + oldItem.size / 2f)
                            ).coerceIn(0f, 420f)
                        } else 0f
                        rippleDirection = if (target > previousTarget) 1 else -1
                        rippleStartNs = frameNs
                    } else {
                        rippleDistancePx = 0f
                        rippleStartNs = 0L
                    }
                    previousTarget = target
                }
                rippleElapsedMs = if (rippleStartNs == 0L) {
                    900L
                } else {
                    ((frameNs - rippleStartNs) / 1_000_000L).coerceIn(0L, 900L)
                }
                if (listState.isScrollInProgress) {
                    velocity = 0f
                    continue
                }
                var item = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == target }
                if (item == null) {
                    // A target can briefly be outside the composed window after a
                    // seek or a fast line change. Never call scrollToItem here:
                    // that instant reposition was the visible "jump, then
                    // recenter" at lyric boundaries. Bring the target in from
                    // the nearest composed edge and let the same spring continue
                    // on the next frame. This keeps the handoff continuous while
                    // still recovering from a large discontinuity.
                    val visible = listState.layoutInfo.visibleItemsInfo
                    val edge = if (target < visible.firstOrNull()?.index ?: target) {
                        visible.firstOrNull()
                    } else {
                        visible.lastOrNull()
                    }
                    if (edge == null) {
                        velocity = 0f
                        continue
                    }
                    val info = listState.layoutInfo
                    val anchor = info.viewportStartOffset +
                        (info.viewportEndOffset - info.viewportStartOffset) * 0.35f
                    val edgeError = edge.offset + edge.size / 2f - anchor
                    val bridgeError = if (target < edge.index) {
                        edgeError - edge.size.toFloat()
                    } else {
                        edgeError + edge.size.toFloat()
                    }
                    listState.dispatchRawDelta(
                        bridgeError.coerceIn(-160f, 160f)
                    )
                    velocity = 0f
                    continue
                }
                val info = listState.layoutInfo
                val anchor = info.viewportStartOffset +
                    (info.viewportEndOffset - info.viewportStartOffset) * 0.35f
                val error = item.offset + item.size / 2f - anchor
                val stiffness = 65f
                val damping = if (liveSpring) 11f else 14f
                velocity += (error * stiffness - velocity * damping) * dt
                if (kotlin.math.abs(error) < 0.35f && kotlin.math.abs(velocity) < 1f) {
                    velocity = 0f
                } else {
                    // Avoid a suspend scroll mutation on every frame; this
                    // leaves the list's layout pass free to present the wave.
                    listState.dispatchRawDelta(velocity * dt)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "qml_lyric_${line.startMs()}_$index" }
            ) { index, line ->
                val groupIndex = prepared.lineToGroup.getOrNull(index) ?: -1
                val group = prepared.groups.getOrNull(groupIndex)
                val groupStart = group?.startMs ?: line.startMs()
                val groupEnd = group?.endMs ?: line.endMs()
                QmlLyricRow(
                    state = state,
                    line = line,
                    index = index,
                    groupStartMs = groupStart,
                    groupEndMs = groupEnd,
                    focused = groupIndex >= 0 && groupIndex == liveCurrentGroupIndex,
                    animatePerToken = prepared.animatablePerToken,
                    positionMs = smoothPositionMs,
                    focusIndex = activeLineIndex,
                    rippleElapsedMs = rippleElapsedMs,
                    rippleDistancePx = rippleDistancePx,
                    rippleDirection = rippleDirection,
                    springEnabled = state.lyricSpring,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QmlLyricRow(
    state: PlayerUiState,
    line: LyricLine,
    index: Int,
    groupStartMs: Long,
    groupEndMs: Long,
    focused: Boolean,
    animatePerToken: Boolean,
    positionMs: Long,
    focusIndex: Int,
    rippleElapsedMs: Long,
    rippleDistancePx: Float,
    rippleDirection: Int,
    springEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isBackground = LyricTimeline.isBackground(line.vocalChannel)
    // This is already the original QPlayer time-domain curve. A second
    // animateFloatAsState restarted on every clock sample and made the column
    // twitch during word playback.
    val distance = kotlin.math.abs(index - focusIndex)
    val activeK = lyricActiveK(positionMs, groupStartMs, groupEndMs)
    val alphaTarget = if (isBackground) {
        0.18f + 0.52f * activeK
    } else {
        0.22f + 0.78f * activeK
    }
    val alpha = if (!isBackground && distance in 1..2) {
        maxOf(alphaTarget, 0.30f)
    } else alphaTarget
    val scaleTarget = if (isBackground) {
        lyricBackgroundScale(positionMs, groupStartMs, groupEndMs)
    } else if (state.lyricScale) {
        0.97f + 0.17f * activeK
    } else {
        1f
    }
    val scale = scaleTarget
    val textScale = if (!isBackground && state.lyricScale) {
        0.97f + 0.17f * activeK
    } else 1f
    // Ripple is disabled for now; keep the parameters in the renderer API so
    // the original QML timing code can be restored without changing callers.
    val rippleOffset = 0f

    val baseSize = state.lyricFontSize.coerceIn(14, 40).toFloat() *
        if (isBackground) 0.70f else 1f
    val requestedLineHeight = baseSize * state.lyricLineSpacing.coerceIn(100, 250) / 100f
    // Keep a stable line box while the active row changes font size. Without a
    // minimum box, a 100% line-spacing setting lets Text remeasure on every
    // syllable tick, which feeds a tiny height change back into LazyColumn and
    // produces the visible vertical twitch.
    val lineHeight = if (isBackground) {
        requestedLineHeight
    } else {
        maxOf(requestedLineHeight, baseSize * 1.20f)
    }
    val mainVisualLines = if (line.text().trim().length > 18) 2 else 1
    val rowHeight = with(LocalDensity.current) {
        lineHeight.sp.toDp() * mainVisualLines.toFloat() +
            (if (!line.romaji.isNullOrBlank()) (baseSize * 0.55f).sp.toDp() else 0.dp) +
            (if (!line.translation.isNullOrBlank()) (baseSize * 0.55f).sp.toDp() else 0.dp)
    }
    val rightAligned = line.vocalChannel == LyricLine.VocalChannel.DUET_RIGHT ||
        line.vocalChannel == LyricLine.VocalChannel.BACKGROUND_RIGHT
    val textAlign = if (rightAligned) TextAlign.End else TextAlign.Start
    val lyricWeight = when (state.lyricFontWeight.coerceIn(0, 3)) {
        0 -> FontWeight.Thin
        1 -> FontWeight.Light
        2 -> FontWeight.Normal
        else -> FontWeight.Medium
    }

    val wordProgress = ArrayList<Float>(line.syllables.size)
    line.syllables.forEachIndexed { syllableIndex, syllable ->
        val end = (syllable.startMs + syllable.durationMs)
            .coerceAtLeast(syllable.startMs + 1L)
        val target = when {
            positionMs <= syllable.startMs -> 0f
            positionMs >= end -> 1f
            else -> (positionMs - syllable.startMs).toFloat() /
                (end - syllable.startMs).toFloat()
        }
        wordProgress += if (animatePerToken) target.coerceIn(0f, 1f) else 1f
    }

    // Resolve theme colors outside remember. MaterialTheme is a composable
    // read and cannot be called from remember's non-composable calculation.
    val unplayedColor = if (state.lyricMd3Color) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else ComposeColor.White.copy(alpha = 0.46f)
    val playedColor = if (state.lyricMd3Color) {
        MaterialTheme.colorScheme.primary
    } else ComposeColor.White
    // The original renderer moves a narrow color edge through the shaped glyphs.
    // Compose's TextStyle brush is laid out against the whole Text box, which
    // makes the edge wrong after wrapping and can recolor several Chinese
    // characters at once. Build one span per timed character instead. Each span
    // is continuously interpolated from the idle color to the played color, so
    // a syllable still sweeps left-to-right even when the source groups several
    // characters into one timed token.
    val text = androidx.compose.runtime.remember(
        line,
        wordProgress,
        focused,
        state.lyricLinearAnim,
        state.lyricMd3Color,
        unplayedColor,
        playedColor
    ) {
        buildAnnotatedString {
            if (line.syllables.isEmpty()) return@buildAnnotatedString
            line.syllables.forEachIndexed { syllableIndex, syllable ->
                val progress = wordProgress.getOrNull(syllableIndex) ?: 0f
                val value = syllable.text
                val count = value.codePointCount(0, value.length).coerceAtLeast(1)
                var charOffset = 0
                repeat(count) { charIndex ->
                    val nextOffset = value.offsetByCodePoints(charOffset, 1)
                    val charProgress = if (!animatePerToken || !focused) {
                        1f
                    } else {
                        // Do not make the character itself jump from idle to lit.
                        // Distribute a timed token across its code points, matching
                        // the old renderer's continuous sweep position.
                        val start = charIndex.toFloat() / count.toFloat()
                        val end = (charIndex + 1).toFloat() / count.toFloat()
                        when {
                            progress <= start -> 0f
                            progress >= end -> 1f
                            else -> lyricSmoothstep((progress - start) / (end - start))
                        }
                    }
                    val color = lyricMixColor(unplayedColor, playedColor, charProgress)
                    withStyle(
                        SpanStyle(
                            color = color,
                        )
                    ) {
                        append(value.substring(charOffset, nextOffset))
                    }
                    charOffset = nextOffset
                }
            }
        }
    }
    val shadow = if (state.lyricShadow || (state.lyricGlow && focused)) {
        Shadow(
            color = ComposeColor.Black.copy(alpha = if (state.lyricShadow) 0.48f else 0.28f),
            offset = Offset(0f, 2f),
            blurRadius = if (state.lyricGlow && focused) 7f else 2.2f
        )
    } else null
    val edgeBlur = if (state.lyricEdgeBlur && distance > 0) {
        // Keep the immediately adjacent two rows legible while still giving
        // the focused line visual separation from the distant rows.
        (distance * 1.05f).coerceAtMost(4.5f).dp
    } else 0.dp

    Column(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                // Main lyrics grow by font size, not by scaling the whole row.
                // This preserves the left/right text anchors and keeps the row's
                // layout independent from the emphasis animation.
                scaleX = if (isBackground) scale else 1f
                scaleY = if (isBackground) scale else 1f
                translationY = 0f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    if (rightAligned) 1f else 0f,
                    0.5f
                )
            }
            .then(if (edgeBlur > 0.dp) Modifier.blur(edgeBlur) else Modifier)
            // Keep LazyColumn's item measurement independent from the live
            // syllable BaselineShift and the active font-size emphasis.
            .height(rowHeight)
            .padding(horizontal = 28.dp),
        horizontalAlignment = if (rightAligned) Alignment.End else Alignment.Start
    ) {
        // Particle lyrics also work for ordinary LRC lines. Older code gated
        // this on animatePerToken, so songs without syllable timestamps never
        // showed anything even when the setting was enabled. Overlay the
        // particles on the measured text area so they remain visible around
        // the focused line instead of occupying a separate 12dp strip.
        val particleProgress = if (wordProgress.isNotEmpty()) {
            wordProgress.average().toFloat()
        } else {
            ((positionMs - groupStartMs).toFloat() /
                (groupEndMs - groupStartMs).coerceAtLeast(1L).toFloat())
        }.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth()) {
            Text(
                text = text,
                textAlign = textAlign,
                fontSize = (baseSize * textScale).sp,
                lineHeight = lineHeight.sp,
                fontWeight = lyricWeight,
                fontFamily = GoogleSansFlexBold,
                color = playedColor,
                maxLines = mainVisualLines,
                overflow = TextOverflow.Clip,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = shadow
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.lyricParticles && focused) {
                LyricParticleField(
                    progress = particleProgress,
                    color = if (state.lyricMd3Color) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    text = line.text().trim(),
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        if (!line.romaji.isNullOrBlank()) {
            Text(
                text = line.romaji.trim(),
                textAlign = textAlign,
                fontSize = (baseSize * 0.5f).sp,
                lineHeight = (baseSize * 0.55f).sp,
                fontWeight = lyricWeight,
                fontFamily = GoogleSansFlexBold,
                color = (if (state.lyricMd3Color) MaterialTheme.colorScheme.onSurfaceVariant
                else ComposeColor.White).copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!line.translation.isNullOrBlank()) {
            Text(
                text = line.translation.trim(),
                textAlign = textAlign,
                fontSize = (baseSize * 0.5f).sp,
                lineHeight = (baseSize * 0.55f).sp,
                fontWeight = lyricWeight,
                fontFamily = GoogleSansFlexBold,
                color = (if (state.lyricMd3Color) MaterialTheme.colorScheme.onSurfaceVariant
                else ComposeColor.White).copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun qmlLyricLift(
    positionMs: Long,
    startMs: Long,
    durationMs: Long,
    spring: Boolean
): Float {
    val elapsedMs = (positionMs - startMs).coerceAtLeast(0L)
    if (elapsedMs <= 0L) return 0f

    if (!spring) {
        // LyricRowRenderer's non-spring path: cubic ease-out with a one-second
        // minimum, so short Chinese syllables do not snap upward.
        val duration = maxOf(1000L, durationMs)
        val progress = (elapsedMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val remaining = 1f - progress
        return 1f - remaining * remaining * remaining
    }

    // This is LyricRowRenderer.liftSpring(), slowed slightly for Compose's text
    // rasterizer. The pixel amplitude is applied at BaselineShift, not here.
    val elapsed = elapsedMs / 1000.0 * 0.72
    val omega0 = 3.7416574
    val zeta = 0.935414
    val damped = zeta * omega0
    val frequency = omega0 * kotlin.math.sqrt(1.0 - zeta * zeta)
    val envelope = kotlin.math.exp(-damped * elapsed)
    val value = 1.0 - envelope * (
        kotlin.math.cos(frequency * elapsed) +
            (damped / frequency) * kotlin.math.sin(frequency * elapsed)
        )
    return value.coerceIn(0.0, 1.0).toFloat()
}

private fun lyricMixColor(from: ComposeColor, to: ComposeColor, progress: Float): ComposeColor {
    val p = progress.coerceIn(0f, 1f)
    return ComposeColor(
        red = from.red + (to.red - from.red) * p,
        green = from.green + (to.green - from.green) * p,
        blue = from.blue + (to.blue - from.blue) * p,
        alpha = from.alpha + (to.alpha - from.alpha) * p
    )
}

@Composable
private fun QmlLyricTransport(
    state: PlayerUiState,
    controller: PlayerController,
    modifier: Modifier = Modifier,
    formatMs: (Long) -> String
) {
    val haptic = LocalView.current
    Column(modifier = modifier) {
        Spacer(Modifier.height(18.dp))
        QmlLyricProgress(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            loading = state.loading,
            wavy = state.lyricProgressStyle == 0,
            onSeek = controller::seek,
            formatMs = formatMs,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); controller.prev() }) {
                Icon(Icons.Default.SkipPrevious, "上一首")
            }
            FilledIconButton(
                onClick = { haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); controller.toggle() },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "播放/暂停",
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); controller.next() }) {
                Icon(Icons.Default.SkipNext, "下一首")
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun QmlLyricProgress(
    positionMs: Long,
    durationMs: Long,
    loading: Boolean,
    wavy: Boolean,
    onSeek: (Long) -> Unit,
    formatMs: (Long) -> String,
    chapterMarks: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    val duration = durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    val current = if (dragging) dragPosition else positionMs.coerceIn(0L, duration)
    val rawProgress = (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(180, easing = LinearEasing),
        label = "md3_progress_position"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val progressColor = MaterialTheme.colorScheme.primary
    val progressHandleColor = MaterialTheme.colorScheme.primary
    val chapterMarkColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.widthIn(max = 600.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(duration, wavy) {
                    fun seekAt(x: Float) {
                        val fraction = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((fraction * duration).toLong())
                    }
                    detectTapGestures { offset -> seekAt(offset.x) }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragPosition = ((offset.x / size.width.toFloat())
                                .coerceIn(0f, 1f) * duration).toLong()
                        },
                        onDragEnd = {
                            onSeek(dragPosition)
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragPosition = ((change.position.x / size.width.toFloat())
                                .coerceIn(0f, 1f) * duration).toLong()
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                if (wavy) {
                    val amplitude = if (dragging) 4.dp.toPx() else 2.5.dp.toPx()
                    val stroke = Stroke(
                        width = if (dragging) 5.dp.toPx() else 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    val thumbGap = 20.dp.toPx()
                    val thumbX = (size.width * progress).coerceIn(
                        thumbGap / 2f,
                        size.width - thumbGap / 2f
                    )
                    val activeEnd = thumbX - thumbGap / 2f
                    val inactiveStart = thumbX + thumbGap / 2f
                    var x = 0f
                    // Original APK: fixed two-cycle waveform. Progress changes
                    // its colour boundary, never stretches the wave itself. The
                    // handle has the same MD3 gap as the straight track.
                    while (x < size.width) {
                        val nextX = (x + 2f).coerceAtMost(size.width)
                        val y1 = centerY + sin((x / size.width) * (4f * PI).toFloat()) * amplitude
                        val y2 = centerY + sin((nextX / size.width) * (4f * PI).toFloat()) * amplitude
                        if (nextX <= activeEnd) {
                            drawLine(progressColor, Offset(x, y1), Offset(nextX, y2), stroke.width, StrokeCap.Round)
                        } else if (x >= inactiveStart) {
                            drawLine(trackColor, Offset(x, y1), Offset(nextX, y2), stroke.width, StrokeCap.Round)
                        }
                        x = nextX
                    }
                    if (!loading) {
                        val handleWidth = if (dragging) 5.dp.toPx() else 4.dp.toPx()
                        val handleHeight = if (dragging) 28.dp.toPx() else 22.dp.toPx()
                        drawRoundRect(
                            color = progressHandleColor,
                            topLeft = Offset(thumbX - handleWidth / 2f, centerY - handleHeight / 2f),
                            size = Size(handleWidth, handleHeight),
                            cornerRadius = CornerRadius(handleWidth / 2f, handleWidth / 2f)
                        )
                    }
                } else {
                    val trackHeight = if (dragging) 14.dp.toPx() else 10.dp.toPx()
                    val radius = trackHeight / 2f
                    val thumbGap = 20.dp.toPx()
                    val thumbX = (size.width * progress).coerceIn(
                        4.dp.toPx() + thumbGap / 2f,
                        size.width - 4.dp.toPx() - thumbGap / 2f
                    )
                    val activeEnd = (thumbX - thumbGap / 2f).coerceAtLeast(0f)
                    val inactiveStart = (thumbX + thumbGap / 2f).coerceAtMost(size.width)
                    if (activeEnd > 0f) drawRoundRect(
                        color = progressColor,
                        topLeft = Offset(0f, centerY - radius),
                        size = Size(activeEnd, trackHeight),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    if (inactiveStart < size.width) drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(inactiveStart, centerY - radius),
                        size = Size(size.width - inactiveStart, trackHeight),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    val handleWidth = if (dragging) 5.dp.toPx() else 4.dp.toPx()
                    val handleHeight = if (dragging) 28.dp.toPx() else 22.dp.toPx()
                    drawRoundRect(
                        color = progressHandleColor,
                        topLeft = Offset(thumbX - handleWidth / 2f, centerY - handleHeight / 2f),
                        size = Size(handleWidth, handleHeight),
                        cornerRadius = CornerRadius(handleWidth / 2f, handleWidth / 2f)
                    )
                }
                // UP chapter starts as ticks on the track (fractions of the duration,
                // empty unless the caller supplied them). Drawn last so they stay
                // readable where the filled part or the thumb runs through them.
                chapterMarks.forEach { mark ->
                    if (mark > 0f && mark < 1f) {
                        val tickWidth = 1.5.dp.toPx()
                        val tickHeight = 14.dp.toPx()
                        drawRoundRect(
                            color = chapterMarkColor,
                            topLeft = Offset(
                                (size.width * mark).coerceIn(0f, size.width - tickWidth),
                                centerY - tickHeight / 2f
                            ),
                            size = Size(tickWidth, tickHeight),
                            cornerRadius = CornerRadius(tickWidth / 2f, tickWidth / 2f)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatMs(current),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatMs(duration),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun LoginDialog(controller: PlayerController, state: PlayerUiState, close: () -> Unit) {
    var loginMode by remember { mutableIntStateOf(0) }
    var cookieText by remember { mutableStateOf("") }
    val initialSuccessRevision = remember { state.webLoginSuccessRevision }

    LaunchedEffect(Unit) {
        controller.clearWebLoginError()
    }
    LaunchedEffect(loginMode) {
        controller.clearWebLoginError()
        if (loginMode == 0) {
            controller.startQrLogin()
            while (true) {
                delay(800L)
                controller.pollQrLogin()
            }
        } else {
            controller.cancelWebLogin()
        }
    }
    LaunchedEffect(state.loggedIn, state.webLoginSuccessRevision) {
        if (state.loggedIn || state.webLoginSuccessRevision > initialSuccessRevision) close()
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.webLoginBusy) {
                controller.cancelWebLogin()
                close()
            }
        },
        icon = { Icon(Icons.Default.Login, contentDescription = null) },
        title = { Text("登录网易云音乐") },
        text = {
            Column(
                modifier = Modifier.heightIn(min = 260.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (loginMode == 0) Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("扫码")
                    } else OutlinedButton(onClick = { loginMode = 0 }, modifier = Modifier.weight(1f)) {
                        Text("扫码")
                    }
                    if (loginMode == 1) Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("网页登录")
                    } else OutlinedButton(
                        onClick = { loginMode = 1 },
                        enabled = state.webLoginAvailable,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("网页登录")
                    }
                    if (loginMode == 2) Button(onClick = {}, modifier = Modifier.weight(1f)) {
                        Text("Cookie")
                    } else OutlinedButton(onClick = { loginMode = 2 }, modifier = Modifier.weight(1f)) {
                        Text("Cookie")
                    }
                }

                when (loginMode) {
                    0 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(ComposeColor.White, ShapeMedium),
                            contentAlignment = Alignment.Center
                        ) {
                            val matrix = state.qrImage
                            if (matrix.isEmpty() || state.qrStatus == 0) {
                                CircularProgressIndicator()
                            } else {
                                Canvas(Modifier.size(200.dp)) {
                                    val matrixSize = matrix.size
                                    if (matrixSize > 0) {
                                        val cell = size.width / matrixSize
                                        matrix.forEachIndexed { y, row ->
                                            row.forEachIndexed { x, dark ->
                                                if (dark) {
                                                    drawRect(
                                                        color = ComposeColor.Black,
                                                        topLeft = Offset(x * cell, y * cell),
                                                        size = Size(cell + 0.5f, cell + 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            when (state.qrStatus) {
                                0 -> "正在获取二维码…"
                                802 -> "已扫码，请在手机上确认"
                                803 -> "登录成功"
                                800 -> "二维码已过期，正在刷新…"
                                else -> "请用网易云音乐 App 扫码"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    1 -> {
                        Text(
                            "将在网易云官网登录，成功后会自动读取并验证 Cookie。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.webLoginError.isNotBlank()) {
                            Text(state.webLoginError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> {
                        Text(
                            "粘贴网易云 music.163.com 的 Cookie 请求头。成功后会加密保存。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = cookieText,
                            onValueChange = { cookieText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Cookie 请求头") },
                            minLines = 3,
                            maxLines = 5
                        )
                        if (state.webLoginError.isNotBlank()) {
                            Text(state.webLoginError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (loginMode) {
                0 -> TextButton(onClick = { controller.startQrLogin() }) { Text("刷新二维码") }
                1 -> Button(
                    onClick = { controller.startWebLogin() },
                    enabled = !state.webLoginBusy
                ) { Text(if (state.webLoginBusy) "正在等待登录…" else "打开网易云官网") }
                else -> Button(
                    onClick = { controller.submitCookieLogin(cookieText) },
                    enabled = cookieText.isNotBlank() && !state.webLoginBusy
                ) { Text(if (state.webLoginBusy) "正在验证…" else "验证并登录") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    controller.cancelWebLogin()
                    close()
                },
                enabled = !state.webLoginBusy
            ) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlaylistCard(
    playlist: NeteasePlaylist,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val sharedMotion = tween<androidx.compose.ui.geometry.Rect>(
        durationMillis = SHARED_MOTION_MS,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    )
    val coverBitmap = rememberCoverBitmap(null, playlist.coverThumbPath ?: playlist.coverUrl)
    // The card's text is not part of the shared element, so it disappears fast
    // instead of lingering半透明 under the detail page's own text.
    val listTextAlpha by animateFloatAsState(
        targetValue = if (leavingCoverKey == "playlist_card_${playlist.id}") 0f else 1f,
        animationSpec = tween(80),
        label = "card_text_fade"
    )
    Card(
        Modifier
            .width(164.dp)
            .clickable { onClick() }
            .then(
                with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            "playlist_container_${playlist.id}"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
                    )
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ComposeColor.Transparent)
    ) {
        Column(Modifier.graphicsLayer { alpha = listTextAlpha }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .then(
                        with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(
                                    "playlist_cover_${playlist.id}"
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                
                                clipInOverlayDuringTransition = OverlayClip(
                                    RoundedCornerShape(rememberSharedCoverTransitionRadius("playlist_cover_${playlist.id}", COVER_OVERLAY_CLIP, animatedVisibilityScope))
                                )
                            )
                        }
                    ),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = "歌单封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.padding(54.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(playlist.name ?: "未命名歌单", modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp), maxLines = 2, fontWeight = FontWeight.Medium)
            Text("${playlist.trackCount} 首歌曲", modifier = Modifier.padding(10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)
private fun PlaylistListRow(
    playlist: NeteasePlaylist,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val sharedMotion = tween<androidx.compose.ui.geometry.Rect>(
        durationMillis = SHARED_MOTION_MS,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    )
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // The card's own text is not part of the shared element: it has to be gone in
    // 80ms so it can never ghost underneath the detail page's text.
    val listTextAlpha by animateFloatAsState(
        targetValue = if (leavingCoverKey == "playlist_row_${playlist.id}") 0f else 1f,
        animationSpec = tween(80),
        label = "list_text_fade"
    )

    val coverBitmap = rememberCoverBitmap(null, playlist.coverThumbPath ?: playlist.coverUrl)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                with(sharedTransitionScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            "playlist_container_${playlist.id}"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp))
                    )
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = ComposeColor.Transparent
    ) {
    Box {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { if (playlist.owned) menu = true }).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .then(
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                "playlist_cover_${playlist.id}"
                            ),
                            animatedVisibilityScope = animatedVisibilityScope,
                            
                        )
                    }
                ),
            shape = ShapeMedium,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = "歌单封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Album, null, Modifier.padding(16.dp))
            }
        }
        Column(Modifier.padding(start = 12.dp).graphicsLayer { alpha = listTextAlpha }) { Text(playlist.name ?: "未命名歌单", fontWeight = FontWeight.Medium); Text("${playlist.trackCount} 首歌曲", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("删除歌单") }, onClick = { menu = false; confirmDelete = true })
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除歌单？") },
        text = { Text("将删除“${playlist.name ?: "未命名歌单"}”，此操作不可撤销。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
    )
    }
}
    }

@Composable
private fun SongRow(
    title: String,
    artist: String,
    coverBytes: ByteArray? = null,
    coverPath: String? = null,
    onClick: () -> Unit,
    onLongPressQueue: (() -> Unit)? = null
) {
    val coverBitmap = rememberCoverBitmap(coverBytes, coverPath)
    val interactionSource = remember { MutableInteractionSource() }
    var menu by remember { mutableStateOf(false) }
    // No plate of its own: the row must not paint a rounded rectangle over the
    // page. The only rectangle is the press highlight, and it is inset so the text
    // keeps whitespace on both sides.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(ShapeMedium)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick,
                    onLongClick = { if (onLongPressQueue != null) menu = true }
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Surface(Modifier.size(48.dp), shape = ShapeSmall, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = "专辑封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Album, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(title, maxLines = 1); Text(artist, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("添加到播放队列") },
                onClick = { menu = false; onLongPressQueue?.invoke() }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }

/**
 * Which of the three states the lyric page body is showing. LOADING is driven by
 * the core's `lyricsLoading` flag, not by a timer: it lasts exactly as long as
 * the fetch does, and the core retries a transient empty answer itself.
 */
private enum class LyricPane { LOADING, COVER, LINES }

/**
 * Material 3 Expressive loading indicator — the framework's own
 * `LoadingIndicator` / `ContainedLoadingIndicator` from material3, i.e. exactly
 * what the `com.google.android.material.loadingindicator.LoadingIndicator` view
 * tag with `Widget.Material3.LoadingIndicator.Contained` renders, including the
 * official shape sequence. Both components draw themselves to fill whatever size
 * they are handed, so [size] is the container box for the contained variant and
 * the shape box for the plain one.
 *
 * [running] = false freezes the indicator on a plain blob instead of animating;
 * [startDelayMs] holds that same blob before the animation begins.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: ComposeColor = MaterialTheme.colorScheme.primary,
    containerColor: ComposeColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    withContainer: Boolean = false,
    running: Boolean = true,
    startDelayMs: Long = 0L
) {
    if (!running) return
    var started by remember(startDelayMs) { mutableStateOf(startDelayMs <= 0L) }
    LaunchedEffect(startDelayMs) {
        if (!started) {
            delay(startDelayMs)
            started = true
        }
    }
    if (!started || !running) {
        // Frozen. Used both before a delayed start and whenever [running] is
        // false, so pausing playback stops the animation on a plain blob instead
        // of leaving it spinning. The inner circle keeps the official contained
        // ratio (the shape is ~0.79 of its container) so the freeze looks like a
        // frame of the real animation.
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            if (withContainer) {
                Box(Modifier.size(size).clip(CircleShape).background(containerColor))
            }
            Box(
                Modifier
                    .size(size * if (withContainer) 0.79f else 0.52f)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        return
    }
    if (withContainer) {
        ContainedLoadingIndicator(
            modifier = modifier.size(size),
            containerColor = containerColor,
            indicatorColor = color
        )
    } else {
        LoadingIndicator(
            modifier = modifier.size(size),
            color = color
        )
    }
}

/**
 * Centred [ExpressiveLoadingIndicator] for pages whose content has not arrived
 * yet. Playlist/album/artist pages show this instead of an empty list, matching
 * what the QML shell does with `player.playlistLoading` & friends.
 */
@Composable
private fun LoadingPlaceholder(
    modifier: Modifier = Modifier,
    text: String = "",
    size: Dp = 56.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveLoadingIndicator(size = size, withContainer = true)
        if (text.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, action: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp)); Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = action) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("刷新") }
    }
}

// Preview uses the same UI composables as the Activity. Only the data and actions
// are static, so Android Studio does not need to construct a PlayerController.
@Preview(
    name = "QPlayer 主页面",
    showBackground = true,
    showSystemUi = false,
    widthDp = 412,
    heightDp = 892
)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun QPlayerHomePreview() {
    val state = remember { previewPlayerState() }
    MaterialTheme(colorScheme = rememberQPlayerColorScheme(state.coverSeed, dark = false, enabled = true)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ComposeTopBar(
                    route = ComposeRoute(ComposeScreen.HOME),
                    state = state,
                    canGoBack = false,
                    onBack = {},
                    onQueue = {},
                    onSettings = {},
                    onAccount = {}
                )
            },
            bottomBar = {
                StandardBottomNav(
                    modifier = Modifier.fillMaxWidth(),
                    screen = ComposeScreen.HOME,
                    showLocalTab = true,
                    onScreen = {},
                    onSearch = {}
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                // A preview hosts the same layout, so it needs the two scopes the
                // page composables now take.
                SharedTransitionLayout {
                    AnimatedVisibility(visible = true) {
                        HomeScreen(
                            state = state,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            openPlaylist = { _, _ -> },
                            playRecommendation = {},
                            refresh = {}
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MiniPlayer(
                        modifier = Modifier.fillMaxWidth(),
                        state = state,
                        onOpen = {},
                        onSeek = {},
                        onToggle = {},
                        onPrevious = {},
                        onNext = {}
                    )
                }
            }
        }
    }
}

private fun previewPlayerState(): PlayerUiState {
    val playlists = listOf("每日推荐", "私人雷达").mapIndexed { index, title ->
        NeteasePlaylist().apply {
            id = index + 1L
            name = title
            trackCount = 24 + index * 8
        }
    }
    val songs = (1..3).map { index ->
        NeteaseSong().apply {
            id = index.toLong()
            name = "示例歌曲 $index"
            artist = "示例歌手"
            album = "示例专辑"
        }
    }
    return PlayerUiState(
        title = "示例歌曲",
        artist = "示例歌手",
        album = "示例专辑",
        playing = true,
        positionMs = 42_000,
        durationMs = 215_000,
        coverSeed = "#2d6a75",
        recommendPlaylists = playlists,
        recommendations = songs
    )
}
