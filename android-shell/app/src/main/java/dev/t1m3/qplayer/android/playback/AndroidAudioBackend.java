package dev.t1m3.qplayer.android.playback;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.util.Logger;

import java.io.IOException;

/**
 * {@link AudioBackend} over {@code android.media.MediaPlayer}. MediaPlayer
 * decodes local files and {@code http(s)} streams natively (mp3/flac/m4a/ogg),
 * so netease CDN urls and local tracks share one path — no SPI decoders, no
 * {@code javax.sound} (which Android lacks).
 *
 * <p>Prepared asynchronously: {@link #play} kicks off {@code prepareAsync} and
 * starts on the prepared callback, honoring the requested start offset.
 */
public final class AndroidAudioBackend implements AudioBackend {

    private MediaPlayer player;
    private String source;
    private long pendingSeekMs;
    private boolean wantPlay;
    private float volume = 0.8f;
    private boolean prepared;
    private Runnable onComplete;
    private Runnable onStarted;
    private Runnable onPaused;
    private Runnable onResumed;
    private Runnable onError;

    // Audio focus: pause on loss (call / other player), duck on transient-can-duck,
    // resume on regain when the loss was transient.
    private final AudioManager audioManager;
    private final Context appContext;
    private AudioFocusRequest focusRequest;
    private boolean hasFocus;
    private boolean resumeOnGain;
    private boolean ducked;

    public AndroidAudioBackend(Context ctx) {
        appContext = ctx.getApplicationContext();
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public synchronized void play(String src, long startMs) {
        if (src == null || src.isEmpty()) return;
        releasePlayer();
        source = src;
        pendingSeekMs = Math.max(0L, startMs);
        wantPlay = true;
        prepared = false;
        requestFocus();

        MediaPlayer mp = new MediaPlayer();
        // Network-backed tracks still need the CPU while the screen is off. Without
        // MediaPlayer's partial wake lock some OEMs suspend the streaming/decoder
        // path, report a MEDIA_ERROR_* and make PlayerController skip to the next
        // track even though the current song has not actually reached its end.
        mp.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mp.setOnPreparedListener(this::onPrepared);
        mp.setOnCompletionListener(p -> onCompleted(p));
        mp.setOnErrorListener(this::onPlayerError);
        mp.setOnVideoSizeChangedListener(this::onVideoSizeChanged);
        player = mp;
        try {
            Logger.info("MediaPlayer: setDataSource + prepareAsync");
            setDataSource(mp, src);
            mp.prepareAsync();
        } catch (IOException | IllegalStateException | IllegalArgumentException
                 | SecurityException e) {
            // IllegalArgumentException/SecurityException come from the (Context, Uri,
            // headers) overload used for the bili CDN, and from a malformed string
            // source; both are unchecked, so without them here they escape play()
            // and — on the resolve path, which calls it from a deferred main-thread
            // runnable — become an uncaught exception instead of a skipped track.
            Logger.error("MediaPlayer setDataSource failed: {}", e.toString());
            releasePlayer();
        }
    }

    /** A {@code content://} URI must be opened via the {@code (Context, Uri)}
     *  overload — the {@code String} overload has no calling context to resolve it
     *  and prepareAsync then fails async with extra=MEDIA_ERROR_SYSTEM (0x80000000).
     *  http(s) urls and plain file paths take the string overload. */
    private android.view.Surface videoSurface;
    /** Source picture size as the player last reported it; 0 until a stream reports
     *  one. The UI frames the video box from this so a 4:3 source keeps its 4:3 (and
     *  full-screen fits rather than crops it). */
    private volatile int videoWidth;
    private volatile int videoHeight;
    private volatile VideoSizeListener videoSizeListener;

    /** Told the source picture size whenever it becomes known. Fires on the media
     *  thread — hop to the main one before touching UI state. */
    public interface VideoSizeListener {
        void onVideoSize(int width, int height);
    }

    public void setVideoSizeListener(VideoSizeListener l) {
        videoSizeListener = l;
    }

    /** Source picture width/height, or 0 until the stream reports them. */
    public int videoWidth() {
        return videoWidth;
    }

    public int videoHeight() {
        return videoHeight;
    }

    /** Bilibili playback is video: the shell hands the SurfaceView's surface to the
     *  very same MediaPlayer that owns the audio, so picture and sound share one
     *  clock instead of being two decoders that have to be kept in sync.
     *
     *  <p>A recomposed or resized SurfaceView reports the SAME surface again, and
     *  re-attaching it makes the platform re-render the picture — which reads as the
     *  video replaying its last moment. So the player is only ever touched on a real
     *  change. The picture is always fitted into the surface (the player's default
     *  scaling mode), never cropped: the source's own aspect ratio is what the UI
     *  frames, so a 4:3 video has to keep its 4:3. */
    public synchronized void attachVideoSurface(android.view.Surface surface) {
        if (surface == videoSurface) return;
        videoSurface = surface;
        MediaPlayer mp = player;
        if (mp == null) return;
        try {
            mp.setSurface(surface);
        } catch (Throwable ignored) { }
    }

    /** Hand the surface back only if it is still the live one. A surface being torn
     *  down must never blank a newer one that has already taken over: the outgoing
     *  SurfaceView's {@code surfaceDestroyed} can arrive after the incoming one's
     *  {@code surfaceCreated}, and a blind detach then left the player rendering
     *  nowhere until the next attach — the picture appearing to jump back. */
    public synchronized void detachVideoSurface(android.view.Surface surface) {
        if (surface == null || videoSurface != surface) return;
        videoSurface = null;
        MediaPlayer mp = player;
        if (mp != null) {
            try {
                mp.setSurface(null);
            } catch (Throwable ignored) { }
        }
    }

    private void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if (width <= 0 || height <= 0) return;
        videoWidth = width;
        videoHeight = height;
        VideoSizeListener l = videoSizeListener;
        if (l != null) l.onVideoSize(width, height);
    }

    private void setDataSource(MediaPlayer mp, String src) throws IOException {
        if (src.startsWith("content://")) {
            mp.setDataSource(appContext, Uri.parse(src));
        } else if (isBiliCdn(src)) {
            // Bilibili's CDN answers 403 unless the request carries a bilibili
            // Referer, so the bare stream URL never plays — the reference client
            // sends these headers on every media request too.
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("Referer", "https://www.bilibili.com/");
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            mp.setDataSource(appContext, Uri.parse(src), headers);
        } else {
            mp.setDataSource(src);
        }
    }

    /** Hosts the bilibili streams are served from (upos mirrors + akamai). */
    private static boolean isBiliCdn(String url) {
        return url != null && (url.contains("bilivideo") || url.contains("bilibili")
                || url.contains("akamaized") || url.contains("biliapi"));
    }

    private synchronized void onPrepared(MediaPlayer preparedPlayer) {
        // releasePlayer() cannot prevent a callback that was already queued on the
        // media thread. Never let an old source start, publish onStarted, or apply its
        // state to the replacement MediaPlayer after a rapid track switch.
        if (player != preparedPlayer) return;
        // The surface may have been attached before prepare finished.
        if (videoSurface != null) {
            try { preparedPlayer.setSurface(videoSurface); } catch (Throwable ignored) { }
        }
        prepared = true;
        Logger.info("MediaPlayer: prepared, duration={}ms", preparedPlayer.getDuration());
        applyVolume();
        if (pendingSeekMs > 0L) {
            preparedPlayer.seekTo(pendingSeekMs, MediaPlayer.SEEK_CLOSEST);
        }
        if (wantPlay) {
            preparedPlayer.start();
            Runnable cb = onStarted;
            if (cb != null) cb.run();
        }
    }

    private synchronized void onCompleted(MediaPlayer completedPlayer) {
        if (player != completedPlayer) return;
        Logger.info("MediaPlayer: completed");
        fire(onComplete);
    }

    private synchronized boolean onPlayerError(MediaPlayer failedPlayer, int what, int extra) {
        if (player != failedPlayer) return true;
        Logger.error("MediaPlayer error: what={} extra={}", what, extra);
        fire(onError);
        // PlayerController's error path retries stale URLs once and advances when
        // that fails. Firing completion too would race a second auto-advance against
        // that recovery and could replace the retried track.
        return true;
    }

    @Override
    public synchronized void pause() {
        wantPlay = false;
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    public synchronized void resume() {
        wantPlay = true;
        requestFocus();
        if (player != null && prepared) {
            player.start();
        }
    }

    @Override
    public synchronized boolean isPlaying() {
        return player != null && prepared && player.isPlaying();
    }

    @Override
    public synchronized void seek(long ms) {
        long target = Math.max(0L, ms);
        if (player != null && prepared) {
            // SEEK_CLOSEST lands on the exact frame instead of the previous sync frame:
            // a basic seekTo undershoots by up to a keyframe interval, which made a
            // lyric tap (and the progress bar) land just before the target so the
            // PREVIOUS line highlighted.
            player.seekTo(target, MediaPlayer.SEEK_CLOSEST);
        } else {
            pendingSeekMs = target;
        }
    }

    @Override
    public synchronized long position() {
        if (player != null && prepared) {
            try {
                return player.getCurrentPosition();
            } catch (IllegalStateException e) {
                return 0L;
            }
        }
        return 0L;
    }

    @Override
    public synchronized long duration() {
        if (player != null && prepared) {
            try {
                int d = player.getDuration();
                return d > 0 ? d : 0L;
            } catch (IllegalStateException e) {
                return 0L;
            }
        }
        return 0L;
    }

    @Override
    public synchronized void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        applyVolume();
    }

    private void applyVolume() {
        if (player != null && prepared) {
            float effective = ducked ? volume * 0.3f : volume;
            player.setVolume(effective, effective);
        }
    }

    @Override
    public synchronized void setOnStarted(Runnable callback) {
        this.onStarted = callback;
    }

    @Override
    public synchronized void setOnPaused(Runnable callback) {
        this.onPaused = callback;
    }

    @Override
    public synchronized void setOnResumed(Runnable callback) {
        this.onResumed = callback;
    }

    @Override
    public synchronized void setOnError(Runnable callback) {
        this.onError = callback;
    }

    @Override
    public synchronized void setOnComplete(Runnable callback) {
        this.onComplete = callback;
    }

    @Override
    public synchronized void release() {
        releasePlayer();
        abandonFocus();
    }

    // --- Audio focus ------------------------------------------------------

    private void requestFocus() {
        if (hasFocus || audioManager == null) return;
        AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setOnAudioFocusChangeListener(this::onFocusChange)
                .setWillPauseWhenDucked(false)
                .build();
        focusRequest = req;
        hasFocus = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
        }
        focusRequest = null;
        hasFocus = false;
        ducked = false;
        resumeOnGain = false;
    }

    private synchronized void onFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // Another app took over for good: pause, don't auto-resume.
                resumeOnGain = false;
                if (player != null && prepared && player.isPlaying()) {
                    player.pause();
                    wantPlay = false;
                    fire(onPaused);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Call / brief interruption: pause and remember to resume on regain.
                if (player != null && prepared && player.isPlaying()) {
                    player.pause();
                    wantPlay = false;
                    resumeOnGain = true;
                    fire(onPaused);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Pause when another app starts media; do not mix two players.
                if (player != null && prepared && player.isPlaying()) {
                    player.pause();
                    wantPlay = false;
                    resumeOnGain = false;
                    fire(onPaused);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (ducked) {
                    ducked = false;
                    applyVolume();
                }
                if (resumeOnGain) {
                    resumeOnGain = false;
                    if (player != null && prepared) {
                        player.start();
                        wantPlay = true;
                        fire(onResumed);
                    }
                }
                break;
            default:
                break;
        }
    }

    private static void fire(Runnable r) {
        if (r != null) r.run();
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                // Detach first so callbacks queued by reset/release cannot act on a
                // subsequently assigned player through the backend's shared fields.
                player.setOnPreparedListener(null);
                player.setOnCompletionListener(null);
                player.setOnErrorListener(null);
                player.reset();
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        prepared = false;
        // The next stream reports its own picture size; until then the UI must not
        // keep framing the box to the previous video's shape.
        videoWidth = 0;
        videoHeight = 0;
        VideoSizeListener sizeListener = videoSizeListener;
        if (sizeListener != null) sizeListener.onVideoSize(0, 0);
    }
}
