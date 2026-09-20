package dev.t1m3.qplayer.android.playback;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import dev.t1m3.qplayer.audio.AudioBackend;
import dev.t1m3.qplayer.audio.FadeCurve;
import dev.t1m3.qplayer.audio.IncomingMix;
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

    // --- Overlapping crossfade (two players) -------------------------------
    // A second MediaPlayer, prepared and already rolling at volume 0, lets the
    // outgoing track be ramped down while the incoming one ramps up instead of the
    // hard cut between them. It is deliberately NOT "the player": every other
    // method here keeps meaning the audible track, and the video surface never
    // reaches the incoming player — a picture belongs to whatever is on screen,
    // which stays the outgoing track until the ramp has actually finished.

    /** The prepared second player; null unless a transition is in flight. */
    private MediaPlayer incomingPlayer;
    private String incomingSource;
    private boolean incomingPrepared;
    private long incomingSeekMs;
    /** Whether the incoming player was prepared rolling (the overlap case) or
     *  parked at its seek offset until the ramp starts it (SILENCE_TRIM, where the
     *  play head has to stay exactly where it was put while the measurements are
     *  still arriving). */
    private boolean incomingStartMuted = true;
    /** True once the incoming player is actually rolling — for a parked one, only
     *  from the moment {@link #beginCrossfade} starts it. */
    private boolean incomingRolling;
    /** How far the incoming player had played when it was last dropped while it was
     *  rolling. That is the part of the next track a listener has already heard, and
     *  the controller resumes the track from it when the transition is given up
     *  instead of completing (see {@link AudioBackend#droppedIncomingPosition}).
     *  Cleared when a new incoming is prepared: it belongs to the boundary that was
     *  in flight, not to the next one. */
    private long droppedIncomingMs = -1L;
    /** True while the two players are ramped against each other. Blocks
     *  {@link #applyVolume()}: a controller fade tick pushes the user volume
     *  through it, and the last thing such a fade writes is a target of 0 — which
     *  would then be the gain the promoted (audible) player inherits. */
    private boolean crossfading;
    /** The gain shape the running ramp follows (see FadeCurve). */
    private FadeCurve rampCurve = FadeCurve.LINEAR;
    /** Invalidates queued ramp samples: every ramp start/cancel bumps it. */
    private long rampGeneration;
    private long rampStartNs;
    private long rampDurationNs;
    private Runnable onCrossfadeComplete;
    private Runnable onCrossfadeAbandoned;
    /** The ramp runs on the main looper's clock rather than on a frame callback:
     *  a Handler tick still fires with the screen off (the outgoing player holds a
     *  partial wake lock), while a frame-driven ramp would stall exactly when the
     *  overlap matters — right at the end of a track that is finishing in the dark. */
    private final Handler rampHandler = new Handler(Looper.getMainLooper());
    /** ~30 Hz. A gain ramp has nothing to gain from the display's frame rate. */
    private static final long RAMP_TICK_MS = 32L;

    // --- The one instruction the incoming player gets (the bass swap) ---------
    // ⚠️ There is no tempo and no pitch here any more, and there is no
    // setPlaybackParams call anywhere in this file. An earlier build stretched the
    // incoming track onto the outgoing track's grid and transposed it into its key
    // (see AI_HANDOFF §7, "no tempo/key"); both are gone, so what a mix can ask for
    // is exactly one thing — the low end changing hands part way through the overlap
    // — and it is asked for with the prepare and fired by the ramp. Nothing else
    // touches the incoming player and nothing touches the audible one.
    //
    // Removing the stretch also removed its side effect: setPlaybackParams STARTS a
    // player that is merely prepared, which is how the parked incoming (the one the
    // plan placed at its own content start) ended up rolling silently through the
    // whole arm window and being promoted ~9 s into the next track while the overlap
    // had only played ~10 s of it — the listener never heard the first nine seconds.
    // onIncomingPrepared now asserts the parked state instead of trusting it.

    /** What the incoming player was prepared FOR, and what it actually got. The
     *  request is applied once, when the player has prepared (see
     *  prepareIncoming/onIncomingPrepared) and reported through {@link #incomingMix()}
     *  so the caller never has to assume anything took. */
    private IncomingMix requestedMix = IncomingMix.IDENTITY;
    private IncomingMix appliedMix;
    /** When the low end hands over, ms into the ramp (from the applied mix), and
     *  whether it has. A one-shot: the swap happens once, on a beat, and never
     *  again for this overlap. */
    private long bassSwapAtMs = -1L;
    private boolean bassSwapped;
    /** The two players' low-end equalizers, when a mix asked for the hand-over and
     *  the device let us have them. Attached before the ramp (nothing is allocated
     *  while two tracks are being mixed) and released at the promotion, on an abort,
     *  and on release. */
    private Equalizer equalizerOut;
    private Equalizer equalizerIn;
    /** Everything below this counts as "the bass" for the hand-over, Hz. The lowest
     *  band of a device's equalizer is usually 60-120 Hz wide, and the kick's body
     *  and a bassline's fundamental are what has to move. */
    private static final double BASS_SWAP_HZ = 200d;
    /** How far a parked incoming player's play head may sit from the offset it was
     *  put at before that counts as "it moved". Only the cheap check in
     *  parkIncoming; a platform that started the player is caught by isPlaying()
     *  whatever its clock says. */
    private static final long PARK_TOLERANCE_MS = 100L;

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
            // The requested offset is part of the line on purpose: "the track started
            // from its beginning again" is a claim about this number, and without it
            // in the log a re-open at 0 looks exactly like a re-open at 40s.
            Logger.info("MediaPlayer: setDataSource + prepareAsync (startMs={})", pendingSeekMs);
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
        // Logged because a pause is the one thing that can end playback without any
        // other trace — "why did it stop by itself?" is otherwise unanswerable, and
        // the position says whether the stop happened where the listener was.
        Logger.info("MediaPlayer: pause at {}ms{}", position(),
                crossfading || incomingPlayer != null ? " (with a transition in flight)" : "");
        wantPlay = false;
        // Pausing in the middle of an overlap would leave the incoming player
        // audible while the track it is replacing goes quiet. The transition is
        // dropped instead (the outgoing player is restored first), so the pause
        // still means "everything stops" and the caller's ordinary switch is
        // still available for the end of the track.
        abortCrossfade(true);
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    public synchronized void resume() {
        Logger.info("MediaPlayer: resume at {}ms", position());
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
        Logger.info("MediaPlayer: seek to {}ms", target);
        // A seek says the user is staying in THIS track, so an overlap that has
        // already started pulling the incoming one up no longer describes what is
        // about to happen at the end of the song: drop it and fade nothing.
        if (crossfading || incomingPlayer != null) abortCrossfade(true);
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
        // While the two players are ramped against each other the gain belongs to
        // the ramp: a controller fade (its fade-out runs at the same end-of-track
        // boundary that started this crossfade) would otherwise write its own
        // value — 0 — into the audible player and hand that silence to the track
        // the promotion promotes.
        if (crossfading) return;
        if (player != null && prepared) {
            float effective = baseGain();
            player.setVolume(effective, effective);
        }
    }

    /** The gain the audible player should sit at: the user's volume, ducked when
     *  focus was lost transiently. Both players are ramped against this. */
    private float baseGain() {
        return ducked ? volume * 0.3f : volume;
    }

    private static float clampGain(float gain) {
        return Math.max(0f, Math.min(1f, gain));
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

    // --- Transitions between two players -------------------------------------

    @Override
    public synchronized boolean prepareIncoming(String src, long startMs) {
        return prepareIncoming(src, startMs, true);
    }

    /** Prepare {@code src} as the NEXT track on a second player: seeked to
     *  {@code startMs}, prepared, and — when {@code startMuted} is true — already
     *  playing at volume 0, so the ramp in {@link #beginCrossfade} fades in
     *  something that is really rolling instead of paying for a cold start at the
     *  exact moment the two songs have to overlap.
     *
     *  <p>With {@code startMuted} false the player is parked instead: seeked to
     *  {@code startMs} and silent, but not started, so its play head stays exactly
     *  there until the ramp starts it. A trim places the next track's first audible
     *  sample at the outgoing track's content end, and a player that has been
     *  rolling for the seconds spent waiting would be that far into the song by
     *  then — the one thing that placement cannot tolerate.
     *
     *  <p>Nothing here touches the audible player, its source, its surface or the
     *  audio focus: focus is per-backend and the active track already holds it, so
     *  requesting it again would be a second, pointless request for the very same
     *  playback. The configured listener set is the same one {@link #play} uses, so
     *  the bili CDN headers still apply on the setDataSource path.
     *
     *  @return false when the source cannot even be opened. The caller then has no
     *          incoming track and has to keep the ordinary switch. */
    @Override
    public synchronized boolean prepareIncoming(String src, long startMs, boolean startMuted) {
        return prepareIncoming(src, startMs, startMuted, IncomingMix.IDENTITY);
    }

    /** Prepare {@code src} as the NEXT track on a second player: seeked to
     *  {@code startMs}, prepared, and — when {@code startMuted} is true — already
     *  playing at volume 0, so the ramp in {@link #beginCrossfade} fades in
     *  something that is really rolling instead of paying for a cold start at the
     *  exact moment the two songs have to overlap.
     *
     *  <p>With {@code startMuted} false the player is parked instead: seeked to
     *  {@code startMs} and silent, but not started, so its play head stays exactly
     *  there until the ramp starts it. A trim places the next track's first audible
     *  sample at the outgoing track's content end, and a player that has been
     *  rolling for the seconds spent waiting would be that far into the song by
     *  then — the one thing that placement cannot tolerate.
     *
     *  <p>Nothing here touches the audible player, its source, its surface or the
     *  audio focus: focus is per-backend and the active track already holds it, so
     *  requesting it again would be a second, pointless request for the very same
     *  playback. The configured listener set is the same one {@link #play} uses, so
     *  the bili CDN headers still apply on the setDataSource path.
     *
     *  @return false when the source cannot even be opened. The caller then has no
     *          incoming track and has to keep the ordinary switch. A mix is never a
     *          reason to answer false: what it actually applied is reported through
     *          {@link #incomingMix()}. */
    @Override
    public synchronized boolean prepareIncoming(String src, long startMs, boolean startMuted,
                                                IncomingMix mix) {
        if (src == null || src.isEmpty()) return false;
        cancelIncoming();
        // A previous boundary's incoming may have been dropped while it was already
        // audible; that reading has now been superseded by this new one.
        droppedIncomingMs = -1L;
        incomingSource = src;
        incomingSeekMs = Math.max(0L, startMs);
        incomingStartMuted = startMuted;
        incomingRolling = false;
        // The mix is remembered, not applied here: the player is only in a state that
        // accepts playback parameters once it has prepared, which is the callback
        // below. A platform that refuses them is reported rather than guessed at.
        requestedMix = mix != null ? mix : IncomingMix.IDENTITY;
        MediaPlayer mp = new MediaPlayer();
        mp.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mp.setOnPreparedListener(this::onIncomingPrepared);
        // Reused on purpose: this player is not "the player" yet, so a completion
        // arriving before the swap is ignored by that method's identity check, and
        // after the swap the same listener is the right one to have.
        mp.setOnCompletionListener(this::onCompleted);
        mp.setOnErrorListener(this::onIncomingError);
        incomingPlayer = mp;
        try {
            Logger.info("MediaPlayer: incoming setDataSource + prepareAsync (startMuted={}{})",
                    startMuted, requestedMix.isIdentity() ? "" : ", " + requestedMix);
            setDataSource(mp, src);
            mp.prepareAsync();
        } catch (IOException | IllegalStateException | IllegalArgumentException
                 | SecurityException e) {
            Logger.error("MediaPlayer incoming setDataSource failed: {}", e.toString());
            releaseIncoming();
            return false;
        }
        return true;
    }

    /** The incoming source is ready: silence it before its first sample is decoded,
     *  put it at the requested offset, apply the mix it was prepared for, and — for
     *  an overlap — get it rolling, so the ramp fades in a stream that is already at
     *  the right place in the song. A parked incoming simply stays there. */
    private synchronized void onIncomingPrepared(MediaPlayer prepared) {
        if (incomingPlayer != prepared) return;
        incomingPrepared = true;
        try {
            prepared.setVolume(0f, 0f);
        } catch (Throwable ignored) { }
        if (incomingSeekMs > 0L) {
            prepared.seekTo(incomingSeekMs, MediaPlayer.SEEK_CLOSEST);
        }
        // The one instruction this boundary has for the incoming player: the low end
        // hands over part way through the overlap. Attached here, before anything is
        // audible, because the equalizer is allocated while a single track is playing.
        applyRequestedMix(prepared);
        if (incomingStartMuted && wantPlay) {
            // ROLLING (unused by the controller today, kept for a trim-style caller):
            // the player runs silently from here so the ramp fades in a moving stream.
            try {
                prepared.start();
                incomingRolling = true;
            } catch (Throwable ignored) { }
        } else {
            // ⚠️ THE INVARIANT. A parked incoming must not have moved: its offset is the
            // sample the overlap is supposed to make audible FIRST, and the ramp is
            // supposed to be the first moment any of it is heard — anything that starts
            // it earlier silently eats that much of the next track (measured on the
            // device: promoted at 19769ms while the overlap had only played 10385ms, so
            // the listener never heard the first nine seconds of the incoming track).
            // Nothing here starts it — no tempo and no pitch are applied any more, so
            // there is no setPlaybackParams whose implicit start could (see the class
            // section above) — but the platform is not trusted to keep that promise for
            // free: whatever state the player is in, it leaves here parked, and if it
            // had started itself this puts it back and says so in the log.
            parkIncoming(prepared);
        }
        Logger.info("MediaPlayer: incoming prepared {} duration={}ms",
                incomingStartMuted ? "and rolling muted"
                        : ("parked at " + incomingSeekMs + "ms, nothing of it heard yet"),
                prepared.getDuration());
    }

    /**
     * Put a prepared incoming player that must not be running back at its own offset.
     *
     * <p>See {@link #onIncomingPrepared} for why this matters: the parked player's
     * offset is where the overlap is supposed to begin making it audible, so a player
     * that has been rolling since it was prepared has already skipped that much of the
     * next track by the time the ramp reaches it. Pausing and re-seeking is exactly
     * the operation the whole parked lane exists to make unnecessary, so it is not
     * expected to fire — it is here so that the invariant holds on a platform that
     * starts a prepared player for reasons of its own, rather than being assumed.
     */
    private void parkIncoming(MediaPlayer prepared) {
        long at = -1L;
        boolean wasRolling = false;
        try {
            wasRolling = prepared.isPlaying();
        } catch (Throwable ignored) { }
        try {
            at = prepared.getCurrentPosition();
        } catch (Throwable ignored) { }
        if (wasRolling) {
            try {
                prepared.pause();
            } catch (Throwable ignored) { }
        }
        if (wasRolling || (at >= 0L && Math.abs(at - incomingSeekMs) > PARK_TOLERANCE_MS)) {
            try {
                prepared.seekTo(Math.max(0L, incomingSeekMs), MediaPlayer.SEEK_CLOSEST);
                Logger.warn("MediaPlayer: the parked incoming had started itself (at {}ms, asked for"
                        + " {}ms); paused it and put it back, so the ramp starts it at exactly its"
                        + " own content start and the listener hears the next track from there",
                        at, incomingSeekMs);
            } catch (Throwable e) {
                Logger.warn("MediaPlayer: could not re-park the incoming at {}ms: {}",
                        incomingSeekMs, e.toString());
            }
        }
        incomingRolling = false;
    }

    private synchronized boolean onIncomingError(MediaPlayer failed, int what, int extra) {
        if (incomingPlayer != failed) return true;
        Logger.error("MediaPlayer incoming error: what={} extra={}", what, extra);
        // A transition that lost its incoming track must not be left standing
        // half-applied: drop the player, put the audible one back at its normal
        // level, and tell the caller so the ordinary track switch can take over.
        abortCrossfade(true);
        return true;
    }

    @Override
    public synchronized boolean beginCrossfade(long ms) {
        return beginCrossfade(ms, FadeCurve.LINEAR);
    }

    /**
     * Give the just-prepared incoming player the one thing a blend asks of it: the
     * low end changes hands part way through the overlap.
     *
     * <p>The hand-over is an {@code Equalizer} on each player's own audio session
     * ({@code getAudioSessionId()}): the incoming's bass bands start cut, so the
     * outgoing track keeps the foundation of the mix to itself, and the two swap at
     * {@link IncomingMix#bassSwapAtMs()}. Attached here, before the ramp, so nothing
     * is allocated while two tracks are sounding — and released at the promotion.
     *
     * <p>What took is recorded in {@link #appliedMix} and reported through
     * {@link #incomingMix()} rather than assumed, so a caller could always check. It
     * is best-effort by design: a device with no usable effects plays the same
     * overlap without the hand-over, which is a smaller loss than refusing the blend.
     * ⚠️ Nothing here touches the tempo or the pitch — no {@code setPlaybackParams} is
     * called anywhere in this class (see the section above the mix fields).
     */
    private void applyRequestedMix(MediaPlayer in) {
        IncomingMix mix = requestedMix;
        if (mix == null || mix.isIdentity()) return;
        appliedMix = mix;
        bassSwapAtMs = mix.bassSwapAtMs();
        attachBassSwap(mix.bassSwapAtMs() >= 0L);
    }

    @Override
    public synchronized IncomingMix incomingMix() {
        // Null while the prepare is still in flight: the mix is applied the moment the
        // player has prepared (see onIncomingPrepared), so before that the honest
        // answer is "not known yet" rather than "nothing was applied".
        if (incomingPlayer == null || !incomingPrepared) return null;
        return appliedMix != null ? appliedMix : IncomingMix.IDENTITY;
    }


    // --- The low-end hand-over (platform Equalizer) ---------------------------

    /** Build the two equalizers and cut the incoming track's low end, so the
     *  outgoing track owns the bass for the first part of the overlap. */
    private void attachBassSwap(boolean wanted) {
        if (!wanted) return;
        equalizerIn = attachEqualizer(incomingPlayer, "incoming");
        equalizerOut = attachEqualizer(player, "outgoing");
        short[] inBands = lowBands(equalizerIn);
        if (inBands.length == 0) {
            // No band below the corner frequency on this device: there is nothing to
            // hand over, so the whole mechanism stands down rather than pretending.
            Logger.info("MediaPlayer: bass swap off (this device's equalizer has no band"
                    + " below {}Hz)", (int) BASS_SWAP_HZ);
            releaseEqualizers();
            bassSwapAtMs = -1L;
            return;
        }
        setLowBands(equalizerIn, inBands, minLevel(equalizerIn));
        Logger.info("MediaPlayer: bass swap armed: the incoming player's low end ({} band(s)"
                + " below {}Hz) starts cut, the hand-over is {}ms into the overlap",
                inBands.length, (int) BASS_SWAP_HZ, bassSwapAtMs);
    }

    private static Equalizer attachEqualizer(MediaPlayer mp, String who) {
        if (mp == null) return null;
        try {
            int session = mp.getAudioSessionId();
            if (session == 0) {
                Logger.warn("MediaPlayer: no audio session for the {} player, no bass swap", who);
                return null;
            }
            Equalizer eq = new Equalizer(0, session);
            eq.setEnabled(true);
            return eq;
        } catch (Throwable e) {
            // Reported, not hidden: on a device where the effect framework refuses
            // the session, the mix is still a tempo/key mix and only loses its bass.
            Logger.warn("MediaPlayer: could not attach an equalizer to the {} player ({}),"
                    + " mixing without the bass swap", who, e.toString());
            return null;
        }
    }

    /** One player's low end, or an empty array when the device's equalizer has no
     *  band below {@link #BASS_SWAP_HZ}. */
    private static short[] lowBands(Equalizer eq) {
        if (eq == null) return new short[0];
        try {
            short count = eq.getNumberOfBands();
            short[] out = new short[count];
            int found = 0;
            for (short b = 0; b < count; b++) {
                int centerMilliHz = eq.getCenterFreq(b);
                if (centerMilliHz > 0 && centerMilliHz / 1000d < BASS_SWAP_HZ) out[found++] = b;
            }
            short[] exact = new short[found];
            System.arraycopy(out, 0, exact, 0, found);
            return exact;
        } catch (Throwable e) {
            Logger.warn("MediaPlayer: equalizer band query failed: {}", e.toString());
            return new short[0];
        }
    }

    /** The device's own lower gain limit, mB — a full cut rather than a polite one,
     *  because a hand-over that is not heard is not a hand-over. */
    private static short minLevel(Equalizer eq) {
        try {
            return eq.getBandLevelRange()[0];
        } catch (Throwable e) {
            return (short) -1500;
        }
    }

    private static void setLowBands(Equalizer eq, short[] bands, short level) {
        if (eq == null || bands.length == 0) return;
        try {
            for (short b : bands) eq.setBandLevel(b, level);
        } catch (Throwable e) {
            Logger.warn("MediaPlayer: equalizer level write failed: {}", e.toString());
        }
    }

    /** The moment a mix was armed for: give the incoming back its low end and take
     *  the outgoing's away. Runs once, from the ramp, on a beat of the outgoing
     *  track — which is a beat of the incoming one too, because the incoming is
     *  being played at the outgoing's tempo by then. */
    private void bassSwapNow() {
        if (bassSwapped || equalizerIn == null) return;
        bassSwapped = true;
        setLowBands(equalizerIn, lowBands(equalizerIn), (short) 0);
        setLowBands(equalizerOut, lowBands(equalizerOut), minLevel(equalizerOut));
        Logger.info("MediaPlayer: bass swap done: the incoming track owns the low end now");
    }

    private void releaseEqualizers() {
        equalizerIn = releaseOneEffect(equalizerIn);
        equalizerOut = releaseOneEffect(equalizerOut);
        bassSwapped = false;
    }

    /** An effect that is not released keeps its engine (and the audio session it was
     *  attached to) alive, and the platform logs it as a leak. */
    private static Equalizer releaseOneEffect(Equalizer eq) {
        if (eq == null) return null;
        try {
            eq.setEnabled(false);
            eq.release();
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Ramp the audible player down and the incoming one up over {@code ms} along
     *  {@code curve}, then promote the incoming player and release the outgoing
     *  one.
     *
     *  @return false when there is no prepared incoming player, or the audible one
     *          is not in a state that can be swapped out. Playback is then left
     *          exactly as it was: promoting an unprepared player would leave the
     *          app silent with the queue already advanced, which is strictly worse
     *          than not transitioning at all. */
    @Override
    public synchronized boolean beginCrossfade(long ms, FadeCurve curve) {
        if (crossfading || incomingPlayer == null || !incomingPrepared) return false;
        if (player == null || !prepared || !wantPlay) return false;
        crossfading = true;
        rampCurve = curve != null ? curve : FadeCurve.LINEAR;
        long generation = ++rampGeneration;
        rampStartNs = System.nanoTime();
        rampDurationNs = Math.max(1L, ms) * 1000000L;
        // The hand-over has to land inside the overlap that is really being run, and
        // the ramp can be shorter than the plan's overlap (a boundary that arrived
        // early caps it, and a refused mix shortens it back to the plan's own
        // length). A swap scheduled after the ramp ends would never fire, and the
        // incoming track would be promoted with its low end still cut — a quarter of
        // the mix left permanently in place. So it is pulled back to just before the
        // promotion, where its effect at worst folds into the promotion itself.
        if (bassSwapAtMs >= 0L) {
            long latest = Math.max(0L, ms - 200L);
            if (bassSwapAtMs > latest) {
                Logger.info("MediaPlayer: bass swap moved from {}ms to {}ms (the ramp is {}ms)",
                        bassSwapAtMs, latest, ms);
                bassSwapAtMs = latest;
            }
        }
        // ⚠️ A parked incoming starts HERE, and only here: this is the moment its
        // offset was computed for, and the first moment any of the next track is
        // allowed to be audible. Everything the listener hears of it during the overlap
        // is `offset .. offset + ms` — nothing skipped, nothing replayed — which is the
        // invariant the controller's handoff and the promotion arithmetic both rest on.
        if (!incomingRolling) {
            try {
                incomingPlayer.start();
                incomingRolling = true;
            } catch (Throwable e) {
                Logger.warn("MediaPlayer: parked incoming refused to start: {}", e.toString());
            }
        }
        Logger.info("MediaPlayer: crossfade begin over {}ms ({}{}{})", ms, rampCurve,
                appliedMix != null ? ", " + appliedMix : "",
                incomingRolling ? ", the incoming starts now at its offset" : "");
        rampStep(generation);
        return true;
    }

    /** One gain sample of the running ramp. Applies the very first sample
     *  immediately (so the swap never starts from a stale volume), then schedules
     *  itself on the main looper. */
    private void rampStep(long generation) {
        boolean promoted = false;
        boolean abandoned = false;
        boolean stop = false;
        synchronized (this) {
            if (!crossfading || generation != rampGeneration) return;
            MediaPlayer out = player;
            MediaPlayer in = incomingPlayer;
            if (out == null || in == null) {
                // Released from under the ramp (release() / a new play()): there is
                // nothing left to fade, and the caller has already moved on.
                cancelRamp();
                stop = true;
            } else {
                long elapsed = System.nanoTime() - rampStartNs;
                float t = elapsed >= rampDurationNs
                        ? 1f : (float) elapsed / (float) rampDurationNs;
                float base = baseGain();
                // The curve decides how the two levels cross: LINEAR is the plain
                // 1-t / t pair, EQUAL_POWER the cos/sin pair whose sum of squares
                // stays at one (no dip in the middle of the overlap).
                float outGain = base * clampGain(rampCurve.outGain(t));
                float inGain = base * clampGain(rampCurve.inGain(t));
                try {
                    out.setVolume(outGain, outGain);
                    in.setVolume(inGain, inGain);
                } catch (Throwable ignored) { }
                // The low end changes hands once, on the beat the controller picked
                // (a beat of both tracks, since the incoming is running at the
                // outgoing's tempo). A level write, so it costs nothing to do it here
                // rather than on its own clock.
                if (bassSwapAtMs >= 0L && !bassSwapped && elapsed >= bassSwapAtMs * 1000000L) {
                    bassSwapNow();
                }
                if (t >= 1f) {
                    if (promoteIncoming(out, in)) {
                        promoted = true;
                    } else {
                        // Refused BEFORE anything was released: drop the incoming
                        // player, put the outgoing track back at its normal level
                        // (the ramp's last sample left it at silence) and tell the
                        // caller, which reaches the boundary through the ordinary
                        // switch. This is the only way a ramp can end — either the
                        // swap, or the outgoing track still playing.
                        cancelIncoming();
                        abandoned = true;
                    }
                }
            }
        }
        if (promoted) {
            // Outside the lock: the controller republishes the now-playing track
            // and immediately calls back into this backend.
            fire(onCrossfadeComplete);
            return;
        }
        if (abandoned) {
            fire(onCrossfadeAbandoned);
            return;
        }
        if (stop) return;
        try {
            rampHandler.postDelayed(() -> rampStep(generation), RAMP_TICK_MS);
        } catch (Throwable ignored) { }
    }

    /** The ramp finished: the incoming player becomes THE player, and the outgoing
     *  one goes away. Called with the lock held, and every field it leaves behind has
     *  to mean the same thing the ordinary prepare path leaves behind, or the next
     *  {@code position()}/{@code duration()}/listener call describes the track that
     *  just went away.
     *
     *  <p>Two things about the ORDER are load bearing:
     *
     *  <ol>
     *  <li>The promoted instance is put in the state {@link #play} leaves behind —
     *  its own listeners, the shared flags pointing at it, nothing pending — and only
     *  then is the OUTGOING player torn down. Nothing here touches a shared flag
     *  after the swap, because after the swap those flags describe the audible
     *  track.</li>
     *  <li>The video surface is handed over LAST, and only to a source that has a
     *  picture, once the outgoing player has already released it. A surface belongs
     *  to one player per media service, and
     *  {@code MediaPlayerService::Client::setVideoSurfaceTexture} answers a failed
     *  connect by RESETTING the player it was asked to attach — attaching the
     *  surface the outgoing player still holds therefore destroys the player that
     *  just took over, leaving a silent player that can never complete and a queue
     *  that never advances. The ordinary path has the same rule: play() releases the
     *  old player before onPrepared attaches the surface to the new one.</li>
     *  </ol>
     *
     *  @return true when the incoming player took over. False means it is not in a
     *          state that can be promoted — in which case nothing at all has been
     *          released and the caller keeps the outgoing track, so a promotion can
     *          never end with nothing playing. */
    private boolean promoteIncoming(MediaPlayer out, MediaPlayer in) {
        if (out == null || in == null) return false;
        // A player the platform has already reset (or one whose start() was refused)
        // is not playing and cannot be promoted: it would be silent, would never fire
        // a completion and could never advance the queue. Checked before the outgoing
        // player is touched, so the currently audible track is still there to keep
        // playing.
        boolean rolling;
        try {
            rolling = in.isPlaying();
        } catch (Throwable e) {
            rolling = false;
        }
        if (!rolling) {
            Logger.warn("MediaPlayer: incoming player is not playing at the end of the ramp,"
                    + " keeping the outgoing track");
            return false;
        }
        // Read before the shared state is rewritten below, and before the player is
        // released: together with the ramp's length this is how much of the incoming
        // track the overlap has already played out loud.
        long startedAtMs = incomingSeekMs;
        crossfading = false;
        player = in;
        source = incomingSource;
        prepared = true;
        // The incoming player was seeked before it started; the play head now
        // belongs to it and nothing is pending for it.
        pendingSeekMs = 0L;
        wantPlay = true;
        incomingPlayer = null;
        incomingSource = null;
        incomingPrepared = false;
        incomingSeekMs = 0L;
        incomingRolling = false;
        incomingStartMuted = true;
        // Until here the promoted player carried the incoming-source listeners, and
        // its error one only ever meant "the transition died". It IS the player now,
        // so it has to be recognised by the same guards play() installs, or a
        // completion would not advance the queue and an error would never reach the
        // controller's recovery.
        in.setOnPreparedListener(this::onPrepared);
        in.setOnErrorListener(this::onPlayerError);
        in.setOnCompletionListener(p -> onCompleted(p));
        // The promoted player's live position, logged at the swap itself: this is the
        // number the incoming track has already played (silently, then ramped up) and
        // exactly where it has to keep going. A value near the ramp's start instead of
        // its end means the parked player never actually advanced.
        long promotedAt;
        try {
            promotedAt = in.getCurrentPosition();
        } catch (Throwable e) {
            promotedAt = -1L;
        }
        // Two numbers, because what they measure is what the listener has been given of
        // the next track: the promoted player's own clock, and the overlap's count (the
        // offset it was started at plus the ramp). The player is started BY the ramp and
        // is never seeked afterwards, so both describe the same span, and the difference
        // between them is the player's own start latency — measured on the device at
        // 123-362ms across local-cache sources. The player's clock is the honest one
        // (that is the audio it emitted); the overlap's count is the upper bound the
        // controller's reported position is taken from.
        //
        // The "nothing skipped" half of the invariant is the same pair of facts: because
        // nothing starts the incoming before the ramp, `offset .. offset + ramp` is all
        // of the next track that has ever been audible, and the listener got it from its
        // very start — nothing was skipped before the ramp and nothing is replayed after
        // it, because nothing on this path ever seeks.
        long rampPlayedMs = Math.max(0L, rampDurationNs / 1000000L);
        long overlapPlayedMs = startedAtMs + rampPlayedMs;
        Logger.info("MediaPlayer: transition promoted, releasing the outgoing player"
                        + " (the incoming is at {}ms; the overlap heard {}ms = its start {}ms +"
                        + " the {}ms ramp{}{})",
                promotedAt, overlapPlayedMs, startedAtMs, rampPlayedMs,
                appliedMix != null ? " " + appliedMix : "",
                promotedAt >= 0L && promotedAt + 200L < overlapPlayedMs
                        ? " -- its own clock is " + (overlapPlayedMs - promotedAt)
                        + "ms behind the ramp's wall clock (start latency, not a skipped part:"
                        + " nothing is seeked here)"
                        : "");
        // ONLY the outgoing player, and only its own listeners: every field above
        // already belongs to the promoted instance.
        releaseOne(out);
        // The equalizers go with the players they were attached to. The promoted
        // track's one is flat by now (the hand-over restored its low end) and the
        // outgoing track is gone, so this changes nothing that can be heard — it is
        // here so the effect engines and the sessions behind them are not leaked.
        releaseEqualizers();
        // ⚠️ No tempo or pitch to undo: the promoted track has been running at its own
        // speed and pitch the whole time (nothing in this class ever calls
        // setPlaybackParams — see the mix section above), so there is no ease-back and
        // nothing about the next boundary's grid is thrown off by this one.
        appliedMix = null;
        requestedMix = IncomingMix.IDENTITY;
        bassSwapAtMs = -1L;
        // The picture, if there is one, belongs to whatever is audible NOW — and only
        // a source that has a picture needs it. The queue is free by the time this
        // runs because the outgoing player released it a line earlier; an audio-only
        // promotion never touches the surface at all.
        if (videoSurface != null && isBiliCdn(source)) {
            try {
                in.setSurface(videoSurface);
            } catch (Throwable ignored) { }
        }
        // Settle exactly on the user's level: the ramp's last sample was already
        // there, and this re-applies the duck factor if focus was lost mid-ramp.
        applyVolume();
        return true;
    }

    /** Abandon the prepared incoming player and put the audible one back at its
     *  normal level — the caller is dropping the overlap, not switching tracks.
     *  Safe to call when no transition exists. */
    public synchronized void cancelIncoming() {
        cancelRamp();
        releaseIncoming();
        applyVolume();
    }

    /** Stop a running ramp without touching either player. A ramp sample already
     *  queued on the main looper finds itself out of generation and does nothing. */
    private void cancelRamp() {
        rampGeneration++;
        crossfading = false;
    }

    private void releaseIncoming() {
        // How far it had got, before the player is gone: a rolling incoming that is
        // dropped mid-overlap is the case where the next track has already been
        // partly heard, and the caller has to resume it from there rather than from
        // its beginning. A parked one that never started contributes nothing. The
        // speed it was running at is what turns the ramp's wall-clock length into
        // that track's own milliseconds (see promoteIncoming).
        if (incomingPlayer != null && incomingRolling) {
            try {
                droppedIncomingMs = Math.max(0L,
                        Math.round(incomingPlayer.getCurrentPosition()));
            } catch (Throwable ignored) { }
        }
        releaseOne(incomingPlayer);
        incomingPlayer = null;
        incomingSource = null;
        incomingPrepared = false;
        incomingSeekMs = 0L;
        incomingRolling = false;
        incomingStartMuted = true;
        // Both equalizers, not just the incoming player's: the hand-over may already
        // have taken the audible track's low end away, and a mix that is dropped
        // half way through must not leave the track the listener is still hearing
        // with its bass cut out. Releasing an effect restores the signal it was
        // shaping, so this IS the restore.
        releaseEqualizers();
        appliedMix = null;
        requestedMix = IncomingMix.IDENTITY;
        bassSwapAtMs = -1L;
    }

    @Override
    public synchronized long incomingPosition() {
        if (incomingPlayer != null && incomingRolling) {
            try {
                return Math.max(0L, incomingPlayer.getCurrentPosition());
            } catch (Throwable ignored) { }
        }
        return -1L;
    }

    @Override
    public synchronized long droppedIncomingPosition() {
        return droppedIncomingMs;
    }

    /** A transition that cannot be carried through: drop the incoming player,
     *  restore the audible one, and tell the caller (unless it is the caller that
     *  decided this) so it can fall back to the ordinary track switch. */
    private void abortCrossfade(boolean notify) {
        boolean wasTransitioning = crossfading || incomingPlayer != null;
        cancelIncoming();
        if (notify && wasTransitioning) fire(onCrossfadeAbandoned);
    }

    /** Called once the promoted track is the audible one (on the main thread), so
     *  the controller can republish title/artist/cover/queue slot as if it had
     *  started that track itself. */
    public synchronized void setOnCrossfadeComplete(Runnable callback) {
        onCrossfadeComplete = callback;
    }

    /** Called when a transition the caller armed could not continue — the incoming
     *  player errored, or playback was paused/seeked/focus-lost mid-overlap. The
     *  audible player is back at its normal level and the caller must fall back. */
    public synchronized void setOnCrossfadeAbandoned(Runnable callback) {
        onCrossfadeAbandoned = callback;
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
        // Focus is the one external event that can stop playback without the user
        // touching anything mid-transition (it also drops the overlap, see below), so
        // every case is named in the log — a bare "it paused by itself" report is
        // otherwise undiagnosable.
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                Logger.info("MediaPlayer: audio focus lost (permanently) at {}ms", position());
                // Another app took over for good: pause, don't auto-resume.
                resumeOnGain = false;
                // Audio focus is lost to another app, so nothing here may keep
                // playing — including the incoming player of a running overlap.
                abortCrossfade(true);
                if (player != null && prepared && player.isPlaying()) {
                    player.pause();
                    wantPlay = false;
                    fire(onPaused);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Logger.info("MediaPlayer: audio focus lost (transient) at {}ms", position());
                // Call / brief interruption: pause and remember to resume on regain.
                abortCrossfade(true);
                if (player != null && prepared && player.isPlaying()) {
                    player.pause();
                    wantPlay = false;
                    resumeOnGain = true;
                    fire(onPaused);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Logger.info("MediaPlayer: audio focus lost (duck) at {}ms", position());
                // Pause when another app starts media; do not mix two players.
                abortCrossfade(true);
                if (player != null && prepared && player.isPlaying()) {
                    player.pause();
                    wantPlay = false;
                    resumeOnGain = false;
                    fire(onPaused);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                Logger.info("MediaPlayer: audio focus gained at {}ms (ducked={}, resumeOnGain={})",
                        position(), ducked, resumeOnGain);
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
        // A ramp in flight belongs to the source being replaced, and so does the
        // player it is ramping toward.
        cancelRamp();
        releaseOne(player);
        player = null;
        prepared = false;
        releaseIncoming();
        // The next stream reports its own picture size; until then the UI must not
        // keep framing the box to the previous video's shape.
        videoWidth = 0;
        videoHeight = 0;
        VideoSizeListener sizeListener = videoSizeListener;
        if (sizeListener != null) sizeListener.onVideoSize(0, 0);
    }

    /** Detach the listeners first so callbacks queued by reset/release cannot act
     *  on a subsequently assigned player through the backend's shared fields. */
    private static void releaseOne(MediaPlayer mp) {
        if (mp == null) return;
        try {
            mp.setOnPreparedListener(null);
            mp.setOnCompletionListener(null);
            mp.setOnErrorListener(null);
            mp.reset();
            mp.release();
        } catch (Throwable ignored) {
        }
    }
}
