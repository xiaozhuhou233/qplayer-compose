package dev.t1m3.qplayer.android.md3eui

import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import dev.t1m3.qplayer.android.library.AndroidMetadataReader
import dev.t1m3.qplayer.android.library.AndroidLibraryScanner
import dev.t1m3.qplayer.android.graphics.AndroidColorExtractor
import dev.t1m3.qplayer.android.playback.AndroidAudioBackend
import dev.t1m3.qplayer.android.playback.PlaybackService
import dev.t1m3.qplayer.android.settings.PrefsSettingsStore
import dev.t1m3.qplayer.bridge.PlayerController
import dev.t1m3.qplayer.bili.BiliClient
import dev.t1m3.qplayer.netease.NeteaseClient
import dev.t1m3.qplayer.netease.dto.NeteasePlaylist
import dev.t1m3.qplayer.netease.dto.NeteaseSong
import dev.t1m3.qplayer.netease.dto.NeteaseAlbum
import dev.t1m3.qplayer.netease.dto.NeteaseArtist
import dev.t1m3.qplayer.model.Track
import dev.t1m3.qplayer.lyric.LyricLine
import dev.t1m3.qplayer.settings.SettingsCatalog
import dev.t1m3.qplayer.settings.SettingsCore
import java.util.concurrent.Executors

internal data class HomeState(
    val loading: Boolean = true,
    val error: String = "",
    val daily: List<NeteaseSong> = emptyList(),
    val sections: List<NeteaseClient.HomeSongSection> = emptyList(),
    val playlists: List<NeteasePlaylist> = emptyList(),
    val playlistSections: List<NeteaseClient.HomePlaylistSection> = emptyList(),
    val albums: List<NeteaseAlbum> = emptyList(),
    val loggedIn: Boolean = false,
    val userName: String = "",
    val loginBusy: Boolean = false,
    val loginError: String = "",
)

internal data class NeteaseLoginState(
    val qrImage: List<List<Boolean>> = emptyList(),
    val qrStatus: Int = 0,
    val busy: Boolean = false,
    val error: String = "",
    val successRevision: Long = 0L,
)

internal data class LyricState(
    val lines: List<LyricLine> = emptyList(),
    val revision: Long = 0,
    val loading: Boolean = false,
    val coverOnly: Boolean = true,
    val coverModeManual: Boolean = false,
    val offsetMs: Int = 0,
    val index: Int = -1,
)

@Stable
internal class PlaybackClock {
    var position by mutableLongStateOf(0L)
    var sampledAtNanos by mutableLongStateOf(0L)
}

internal data class PlaybackState(
    val title: String = "还没有播放歌曲",
    val artist: String = "从推荐中选一首，开始听歌",
    val cover: String = "",
    val coverSeed: String = "",
    val playing: Boolean = false,
    val loading: Boolean = false,
    val hasTrack: Boolean = false,
    val queueSize: Int = 0,
    val duration: Long = 0,
    val songId: Long = 0,
    val liked: Boolean = false,
    val likeable: Boolean = false,
    val playMode: Int = 0,
    val queueIndex: Int = -1,
    val privateFmMode: Boolean = false,
    val playbackRevision: Long = 0,
    val seekRevision: Long = 0,
    val clock: PlaybackClock = PlaybackClock(),
) {
    val position: Long get() = clock.position
    val sampledAtNanos: Long get() = clock.sampledAtNanos
}

internal data class SearchState(
    val query: String = "",
    val mode: String = "song",
    val songs: List<NeteaseSong> = emptyList(),
    val albums: List<NeteaseAlbum> = emptyList(),
    val artists: List<NeteaseArtist> = emptyList(),
    val local: List<Track> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val history: List<String> = emptyList(),
    val hot: List<String> = emptyList(),
    val videos: List<BiliClient.BiliVideo> = emptyList(),
    val videoError: String = "",
)

internal data class PlaylistState(
    val mine: List<NeteasePlaylist> = emptyList(),
    val title: String = "",
    val cover: String = "",
    val tracks: List<NeteaseSong> = emptyList(),
    val loading: Boolean = false,
    val subscribed: Boolean = false,
    val owned: Boolean = false,
    val loggedIn: Boolean = false,
)

internal data class LocalState(
    val tracks: List<Track> = emptyList(),
    val scanning: Boolean = false,
    val error: String = "",
    val permissionGranted: Boolean = false,
)

internal data class ArtistDetailState(
    val name: String = "",
    val cover: String = "",
    val description: String = "",
    val songs: List<NeteaseSong> = emptyList(),
    val albums: List<NeteaseAlbum> = emptyList(),
    val loading: Boolean = false,
)

internal data class AlbumDetailState(
    val name: String = "",
    val cover: String = "",
    val artistName: String = "",
    val tracks: List<NeteaseSong> = emptyList(),
    val loading: Boolean = false,
)

/** Process-owned playback; neither backend nor service callbacks retain an Activity. */
internal class Md3eRuntime private constructor(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    val audioBackend = AndroidAudioBackend(app)
    val controller = PlayerController(audioBackend, AndroidMetadataReader(app), NeteaseClient.INSTANCE)
    val settings = SettingsCore()
    private val scanner = AndroidLibraryScanner(app.contentResolver, AndroidMetadataReader(app))
    private val scanWorker = Executors.newSingleThreadExecutor()
    private val playbackClock = PlaybackClock()
    var home by mutableStateOf(HomeState())
        private set
    var neteaseLogin by mutableStateOf(NeteaseLoginState())
        private set
    var playback by mutableStateOf(PlaybackState())
        private set
    var lyricState by mutableStateOf(LyricState())
        private set
    var settingsRevision by mutableStateOf(0)
        private set
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var search by mutableStateOf(SearchState())
        private set
    var playlists by mutableStateOf(PlaylistState())
        private set
    var local by mutableStateOf(LocalState())
        private set
    var artistDetail by mutableStateOf(ArtistDetailState())
        private set
    var albumDetail by mutableStateOf(AlbumDetailState())
        private set
    var unblockEnabled by mutableStateOf(true)
        private set
    var notice by mutableStateOf<String?>(null)
    var bili by mutableStateOf(BiliState())
        private set
    val videoSlots = androidx.compose.runtime.mutableStateMapOf<Int, androidx.compose.ui.geometry.Rect>()
    var videoFullscreen by mutableStateOf(false)
    var videoVisible by mutableStateOf(true)
    var videoWidth by mutableStateOf(0)
        private set
    var videoHeight by mutableStateOf(0)
        private set
    private val biliCookies = java.io.File(app.filesDir, "bili_cookies.txt")
    private var lastBiliLoggedIn = false
    private var visibleHosts = 0
    private var loadedHotSearches = false
    private var lastLoggedIn = false

    init {
        controller.setMainExecutor { handler.post(it) }
        controller.setColorExtractor(AndroidColorExtractor())
        runCatching { biliCookies.takeIf { it.exists() }?.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let(controller::restoreBiliSession)
        audioBackend.setVideoSizeListener { width, height -> handler.post {
            videoWidth = width
            videoHeight = height
        } }
        settings.attach(controller)
        settings.registerAction("clearCache", controller::clearDiskCache)
        settings.registerAction("checkUpdate", controller::checkForUpdateManual)
        settings.registerAction("openRepo") {
            controller.openExternalUrl("https://github.com/TIMER-err/qplayer")
        }
        settings.registerInfo("version") { "v${controller.appVersion.peek()}" }
        settings.registerInfo("cacheUsage") { "${controller.cacheSizeMB.peek()} MB" }
        settings.load(PrefsSettingsStore(app), SettingsCatalog.ANDROID)
        unblockEnabled = settings.bool("unblock")
        attachService()
        controller.preloadHome()
    }

    private val tick = object : Runnable {
        override fun run() {
            controller.pump()
            home = HomeState(
                loading = controller.homeLoading.peek(),
                error = controller.homeFeedError.peek(),
                daily = controller.recommendations.peek(),
                sections = controller.homeSongSections.peek(),
                playlists = controller.recommendPlaylists.peek(),
                playlistSections = controller.homePlaylistSections.peek(),
                albums = controller.homeAlbumRecommendations.peek(),
                loggedIn = controller.loggedIn.peek(),
                userName = controller.userName.peek(),
                loginBusy = controller.webLoginBusy.peek(),
                loginError = controller.webLoginError.peek(),
            )
            neteaseLogin = NeteaseLoginState(
                qrImage = controller.qrImage.peek(),
                qrStatus = controller.qrStatus.peek(),
                busy = controller.webLoginBusy.peek(),
                error = controller.qrLoginError.peek(),
                successRevision = controller.webLoginSuccessRevision.peek(),
            )
            val loggedIn = controller.loggedIn.peek()
            if (loggedIn && !lastLoggedIn) controller.loadMyPlaylists()
            lastLoggedIn = loggedIn
            val hasTrack = controller.currentTrack() != null
            val songId = controller.currentTrack()?.neteaseId ?: 0L
            playbackClock.position = controller.mediaSessionPosition().coerceAtLeast(0)
            playbackClock.sampledAtNanos = System.nanoTime()
            val nextPlayback = PlaybackState(
                controller.title.peek().ifBlank { "还没有播放歌曲" },
                controller.artist.peek().ifBlank { if (hasTrack) "未知歌手" else "从推荐中选一首，开始听歌" },
                controller.coverPath.peek().ifBlank { controller.coverUrl.peek() },
                controller.coverSeed.peek(),
                controller.isPlaying(), controller.loading.peek(),
                hasTrack, controller.queueTracks.peek().size,
                controller.durationMs.peek().coerceAtLeast(0),
                songId, controller.currentLiked.peek(), songId != 0L && loggedIn,
                controller.playMode.peek(), controller.index.peek(),
                controller.privateFmActive.peek() == true,
                controller.playbackRevision(), controller.seekRevision(), playbackClock,
            )
            // The clock is observable on its own. Avoid invalidating every
            // playback consumer at the sampling cadence when no metadata or
            // control state changed; this keeps the 100 ms pump off the main
            // composition path while lyric/progress draw code still sees time.
            if (nextPlayback != playback) playback = nextPlayback
            lyricState = LyricState(
                lines = controller.lyrics.peek(),
                revision = controller.lyricsRevision.peek(),
                loading = controller.lyricsLoading.peek(),
                coverOnly = controller.lyricsCoverOnly.peek(),
                coverModeManual = controller.coverModeManual.peek(),
                offsetMs = controller.lyricOffsetMs.peek(),
                index = controller.lyricIndex.peek(),
            )
            queue = controller.queueTracks.peek()
            bili = BiliState(
                loggedIn = controller.biliLoggedIn.peek(),
                playing = controller.biliPlaying.peek(),
                bvid = controller.currentTrack()?.biliBvid.orEmpty(),
                qrUrl = controller.biliQrUrl.peek(), qrStatus = controller.biliQrStatus.peek(),
                error = controller.biliError.peek(),
                chapters = controller.biliChapterMarks.peek(),
                folders = controller.biliFavFolders.peek(), foldersLoading = controller.biliFavLoading.peek(),
                favError = controller.biliFavError.peek(), items = controller.biliFavItems.peek(),
                itemsLoading = controller.biliFavItemsLoading.peek(), folderTitle = controller.biliFavItemsTitle.peek(),
            )
            if (bili.loggedIn != lastBiliLoggedIn) {
                lastBiliLoggedIn = bili.loggedIn
                runCatching { biliCookies.writeText(controller.biliClient().cookieHeader()) }
            }
            if (!bili.playing) videoFullscreen = false
            search = search.copy(
                mode = search.mode, songs = controller.searchResults.peek(),
                albums = controller.searchAlbumResults.peek(), artists = controller.searchArtistResults.peek(),
                local = controller.localSearchResults.peek(), loading = controller.searchLoading.peek(),
                hasMore = controller.searchHasMore.peek(), history = controller.searchHistory.peek(),
                hot = controller.hotSearches.peek(), videos = controller.biliSearchResults.peek(),
                videoError = controller.biliError.peek(),
            )
            if (search.mode == "bili") search = search.copy(loading = controller.biliSearchLoading.peek(), hasMore = false)
            playlists = PlaylistState(
                controller.myPlaylists.peek(), controller.playlistTitle.peek(),
                controller.playlistCoverPath.peek(), controller.playlistTracks.peek(),
                controller.playlistLoading.peek(), controller.playlistSubscribed.peek(),
                controller.playlistOwned.peek(), loggedIn,
            )
            local = local.copy(tracks = controller.tracks.peek())
            artistDetail = ArtistDetailState(
                controller.artistName.peek(), controller.artistCoverPath.peek(),
                controller.artistBriefDesc.peek(), controller.artistSongs.peek(),
                controller.artistAlbums.peek(), controller.artistLoading.peek(),
            )
            albumDetail = AlbumDetailState(
                controller.albumName.peek(), controller.albumCoverPath.peek(),
                controller.albumArtistName.peek(), controller.albumTracks.peek(),
                controller.albumLoading.peek(),
            )
            controller.toast.peek().takeIf { it.isNotBlank() }?.let {
                notice = it
                controller.toast.set("")
            }
            // Drain core UI work in the background while the playback service owns the
            // session. No frame loop or Activity reference is needed for audio to advance.
            if (visibleHosts > 0 || PlaybackService.isRunning() || controller.isPlaying()) {
                // Match the legacy lyric clock's 100 ms foreground samples.
                handler.postDelayed(this, if (visibleHosts > 0 && controller.isPlaying()) 100 else 250)
            }
        }
    }

    private fun attachService() {
        PlaybackService.attachController(controller) {
            if (controller.isPlaying()) startService()
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app,
                Intent(app, PlaybackService::class.java).setAction(PlaybackService.ACTION_REFRESH))
        } catch (_: IllegalStateException) {
            notice = "后台播放服务未能启动，请返回应用重试"
        } catch (_: SecurityException) {
            notice = "系统未允许启动播放服务，请返回应用重试"
        }
    }

    fun onVisible() {
        visibleHosts++
        attachService()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun onHidden() {
        visibleHosts = (visibleHosts - 1).coerceAtLeast(0)
        controller.saveSessionState()
    }

    fun play(action: PlayerController.() -> Unit) {
        // Promote while the user's tap is in the foreground, before network resolution.
        startService()
        controller.action()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun toggle() = play { toggle() }
    fun previous() = play { prev() }
    fun next() = play { next() }

    fun updateUnblockEnabled(enabled: Boolean) {
        updateSetting("unblock", enabled)
    }

    fun updateSetting(key: String, value: Any) {
        settings.put(key, value)
        unblockEnabled = settings.bool("unblock")
        settingsRevision++
    }

    fun invokeSetting(action: String) {
        settings.invoke(action)
        settingsRevision++
    }

    fun loadHotSearches() {
        if (!loadedHotSearches) {
            loadedHotSearches = true
            controller.loadHotSearches()
        }
    }

    fun search(query: String, mode: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val selectedMode = mode.takeIf { it == "album" || it == "artist" || it == "bili" } ?: "song"
        if (selectedMode != "bili" && controller.searchMode.peek() != selectedMode) controller.setSearchMode(selectedMode)
        search = search.copy(query = trimmed, mode = selectedMode)
        controller.addSearchHistory(trimmed)
        when (selectedMode) {
            "bili" -> controller.searchBili(trimmed)
            "album" -> controller.searchAlbums(trimmed)
            "artist" -> controller.searchArtists(trimmed)
            else -> {
                controller.searchLocal(trimmed)
                controller.search(trimmed)
            }
        }
    }

    fun loadMoreSearch() = controller.loadMoreSearch()
    fun loadMyPlaylists() = controller.loadMyPlaylists()
    fun openPlaylist(id: Long) = controller.openPlaylist(id)
    fun openArtist(id: Long) = controller.openArtist(id)
    fun openAlbum(id: Long) = controller.openAlbum(id)

    fun setLocalPermission(granted: Boolean) {
        local = local.copy(permissionGranted = granted,
            error = if (granted) "" else "需要媒体读取权限才能查看本地音乐")
    }

    fun scanLocal() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(app, permission) != PackageManager.PERMISSION_GRANTED) {
            setLocalPermission(false)
            return
        }
        if (local.scanning) return
        local = local.copy(scanning = true, error = "", permissionGranted = true)
        scanWorker.execute {
            try {
                val tracks = scanner.scan()
                handler.post {
                    controller.scanTracks(tracks)
                    controller.pump()
                    local = local.copy(scanning = false, tracks = tracks)
                }
            } catch (error: Exception) {
                handler.post { local = local.copy(scanning = false, error = "本地音乐扫描失败，请重试") }
            }
        }
    }

    companion object {
        private var instance: Md3eRuntime? = null
        fun get(context: Context): Md3eRuntime = instance ?: Md3eRuntime(context).also { instance = it }
    }
}
