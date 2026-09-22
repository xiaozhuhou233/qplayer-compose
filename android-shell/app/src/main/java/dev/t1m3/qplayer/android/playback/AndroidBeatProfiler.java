package dev.t1m3.qplayer.android.playback;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import dev.t1m3.qplayer.audio.BeatAnalysis;
import dev.t1m3.qplayer.audio.BeatProfile;
import dev.t1m3.qplayer.audio.BeatProfiler;
import dev.t1m3.qplayer.audio.KeyAnalysis;
import dev.t1m3.qplayer.audio.KeyProfile;
import dev.t1m3.qplayer.util.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link BeatProfiler} over {@code MediaExtractor} + {@code MediaCodec} — the only
 * decoders this app has, and both are platform classes, so measuring a track's
 * tempo costs no dependency (see AI_HANDOFF §六).
 *
 * <p>This class does the platform half only: open the file, decode a bounded
 * window, downmix to mono and downsample. The estimation itself is
 * {@link BeatAnalysis} — plain Java, deliberately, because that is the part that
 * can be wrong and it can then be checked on a desktop JVM against signals whose
 * answer is known (synthetic click tracks with a set tempo and a set first beat)
 * instead of judged by whether a number looks plausible on a phone.
 *
 * <p>Bounded on purpose, in both directions:
 * <ul>
 *   <li>audio: at most {@link #WINDOW_MS} from the head of the track, downsampled
 *       to about {@link #TARGET_RATE}. A tempo is a property of a passage, not of
 *       a whole track — thirty seconds contain 27 beats even at 55 BPM — so
 *       reading further would only spend another download per track.</li>
 *   <li>time: the whole probe gives up after {@link #DEADLINE_MS}. Like the
 *       silence profiler, the answer is only worth having if it arrives before the
 *       boundary it would serve.</li>
 * </ul>
 *
 * <p><b>⚠️ The ladder of windows, and why one window is not enough.</b> A single 30 s
 * window at the head used to be the whole measurement, and on this library it refuses two
 * kinds of track that a shorter one reads clearly:
 *
 * <ul>
 *   <li><b>A sparse beat</b> ({@code BeatAnalysis.windowAgreement} only arms its 3-segment
 *       agreement test once the window is at least 18 s, and a track whose onset pattern
 *       repeats every one and a half beats fails that test): 《Hurt You》548785556 reads
 *       <em>no grid at all</em> from the head 30 s, while a 15 s window at the same place
 *       reads 642 ms of phase at confidence 0.71 — the window that should have been the
 *       safest is the worst choice there.</li>
 *   <li><b>A starved pass</b>: the deadline is wall clock, and on a device that is busy
 *       (rendering DJ edits, for one) it can cut the decode after four or five seconds of
 *       audio. What came out was still analysed and cached, so 《Lose My Mind》2700280437 sat
 *       in the cache at confidence 0.19 — under {@link BeatProfile#MIN_CONFIDENCE}, which is
 *       the one number everything downstream gates on, and the cache made it permanent.</li>
 * </ul>
 *
 * <p>So the probe now tries {@link #ATTEMPTS}: the head 30 s first (the measurement every
 * boundary has used since P4, unchanged for every track it works for), then a 15 s window at
 * the head, then a 15 s window {@link #LATE_START_MS} into the file. It stops at the first
 * window whose grid reaches {@link BeatProfile#MIN_CONFIDENCE}, logs every window it tried
 * with what it read, and — the rule that matters for the second failure above — <b>never
 * answers with a pass its own deadline truncated</b>: a truncated pass is retried, and if
 * every window is truncated the track is left unmeasured (the controller then caches nothing
 * and a later play probes again) rather than storing a starved grid that would be believed
 * forever. A window that completed and still reads weak is not a starvation artefact: it is a
 * track without a credible beat, and it is returned as it always was, which keeps the key
 * measurement it carries (the key comes from the same decode and gated on nothing).
 *
 * <p><b>Phase semantics do not change with the window.</b> The sink takes its origin from the
 * <em>first decoded buffer's own presentation timestamp</em> ({@link Mono#originMs}), and the
 * estimator adds that origin before folding the phase into one period
 * ({@code BeatAnalysis.analyse}), so a reported first beat is a position in the FILE whatever
 * window it was measured from — including the late one, whose seek lands on a sync point
 * rather than on the exact millisecond asked for: the samples carry their own timestamps, so a
 * seek that lands early labels itself. Two windows of the same track therefore describe the
 * same family of beat positions (the grid is {@code firstBeat + k*period}), which is why the
 * later window is a valid measurement of the track's own grid and not a different one. The
 * window that answered is logged, and the head is preferred: the ladder only moves later when
 * the head has nothing usable to say.
 *
 * <p>Nothing here can affect playback: it runs on the controller's beat worker,
 * opens its own extractor and codec, and answers null for every problem (unknown
 * container, no audio track, a decoder that will not start, float or 8-bit PCM,
 * deadline, I/O). A boundary with no grid is simply not aligned.
 */
public final class AndroidBeatProfiler implements BeatProfiler {

    /** How much audio is decoded by the first window, ms. */
    private static final long WINDOW_MS = 30_000L;

    /** The window every retry uses, ms. Short enough that
     *  {@code BeatAnalysis.windowAgreement}'s 3-segment test is not armed (it needs 18 s) —
     *  which is the point: the segments are what refuses a sparse beat — and long enough that
     *  a slow track still holds a dozen beats. */
    private static final long SHORT_WINDOW_MS = 15_000L;

    /** Where the last window starts, ms: a track's intro is often the least representative
     *  part of it (a solo, a pad, a spoken line), and the body is where the groove is. The
     *  sweep that chose this point measured 15 s at +30 s reading 0.84 on a track whose head
     *  windows read 0.19 (starved) and 0.43 (complete). */
    private static final long LATE_START_MS = 30_000L;

    /** The whole probe gives up here, per window. The window is 30 s of audio, which this
     *  decodes in a fraction of a second locally and in a few seconds from a CDN;
     *  past this the answer would arrive after the boundary that wanted it. */
    private static final long DEADLINE_MS = 8_000L;

    /** What each retry may spend, ms. A retry decodes half the audio of the first window, so
     *  it gets half the budget; the whole ladder is therefore bounded at
     *  {@code DEADLINE_MS + 2 * RETRY_DEADLINE_MS} = 16 s of wall clock, and only in the case
     *  where the first window produced nothing usable (a track the ladder is not going to
     *  rescue costs exactly one window, as it always did). */
    private static final long RETRY_DEADLINE_MS = 4_000L;

    /**
     * The windows one probe tries, in order. Each is a start in the file (ms), a length, and a
     * deadline; the first whose grid reaches {@link BeatProfile#MIN_CONFIDENCE} wins, and the
     * probe stops there.
     */
    private static final Attempt[] ATTEMPTS = {
            new Attempt(0L, WINDOW_MS, DEADLINE_MS, "head 30s"),
            new Attempt(0L, SHORT_WINDOW_MS, RETRY_DEADLINE_MS, "head 15s"),
            new Attempt(LATE_START_MS, SHORT_WINDOW_MS, RETRY_DEADLINE_MS, "body 15s"),
    };

    /** One window of the ladder. */
    private static final class Attempt {
        final long startMs;
        final long windowMs;
        final long deadlineMs;
        /** How the log names it ("head 30s"). */
        final String what;

        Attempt(long startMs, long windowMs, long deadlineMs, String what) {
            this.startMs = startMs;
            this.windowMs = windowMs;
            this.deadlineMs = deadlineMs;
            this.what = what;
        }

        /** Whether a file of {@code durationMs} (0 = unknown) can hold this window at all: a
         *  start past the end is skipped rather than decoded into nothing. The known-length
         *  case is the only reason this probe reads the length hint. */
        boolean fitsIn(long durationMs) {
            return startMs == 0L || durationMs <= 0L || startMs + MIN_USABLE_MS <= durationMs;
        }
    }

    /** The rate the mono signal is decimated to before the analysis. A beat lives
     *  under 10 Hz; 11 kHz is more than two orders of magnitude above anything the
     *  onset envelope looks at, and it keeps a 30 s window at a quarter of a
     *  million doubles — a couple of megabytes, which is nothing for a worker that
     *  runs once per track and holds nothing else. */
    private static final double TARGET_RATE = 11_025d;

    /** The least audio worth analysing, ms: the estimator refuses anything shorter
     *  itself, and stopping the decode before this point only wastes the work. */
    private static final long MIN_USABLE_MS = 8_000L;

    private final Context appContext;

    public AndroidBeatProfiler(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    @Override
    public BeatProfile probe(String source, long durationMsHint) {
        if (source == null || source.isEmpty()) return null;
        String shortName = shortSource(source);
        BeatProfile winner = null;
        Mono winnerMono = null;
        // A grid from a window that COMPLETED and still reads below the gate: kept as the
        // answer of last resort (it is a track without a credible beat, not a starvation
        // artefact), but only after every window has had its turn.
        BeatProfile weak = null;
        Mono weakMono = null;
        long weakAnalysedMs = 0L;
        for (Attempt attempt : ATTEMPTS) {
            if (!attempt.fitsIn(durationMsHint)) {
                Logger.info("beat probe: skipping the {} window for {} — the file is {}ms long",
                        attempt.what, shortName, durationMsHint);
                continue;
            }
            Pass pass = one(source, attempt);
            if (pass == null) continue;
            Logger.info("beat probe: {} window for {} analysed {}ms{} -> {}", attempt.what,
                    shortName, pass.analysedMs,
                    pass.truncated ? " (cut short by its deadline)" : "",
                    pass.profile == null ? "no grid" : pass.profile.label());
            if (pass.profile == null) continue;
            if (pass.profile.trustworthy()) {
                winner = pass.profile;
                winnerMono = pass.mono;
                Logger.info("beat probe: the {} window answered for {} ({})", attempt.what,
                        shortName, winner.label());
                break;
            }
            if (!pass.truncated && (weak == null
                    || pass.profile.confidence() > weak.confidence())) {
                weak = pass.profile;
                weakMono = pass.mono;
                weakAnalysedMs = pass.analysedMs;
            }
            // Under the gate and not from a truncated pass: the next window gets a turn,
            // because on this library a sparse beat is exactly the case one window refuses
            // and another reads (see the class doc).
        }
        if (winner == null) {
            if (weak == null) {
                // Every window was empty, truncated, or decoded too little: the track stays
                // unmeasured. That is the point of the rule — a starved pass must not become a
                // cached grid, or the one number everything gates on is wrong for good (the
                // controller caches only what it is handed, so nothing is stored and a later
                // play probes again once the device is less busy).
                Logger.info("beat probe: no usable window for {} — left unmeasured rather than"
                        + " caching a starved grid", shortName);
                return null;
            }
            winner = weak;
            winnerMono = weakMono;
            Logger.info("beat probe: every window for {} read below {} — keeping {} from a window"
                            + " that completed ({}ms analysed), which also carries the measured"
                            + " key",
                    shortName, BeatProfile.MIN_CONFIDENCE, winner.label(), weakAnalysedMs);
        }
        // The key, from the winning window's own samples: one decode answers both of the
        // questions a mix asks (where the beats are, and what key the music is in). Measured
        // only when a grid was found, which is deliberate — the key is only ever used to
        // transpose a track into another one's key during an overlap, and a track with no
        // grid cannot be aligned into an overlap in the first place, so a profile for one
        // would be a decode nobody reads.
        KeyProfile key = winnerMono != null
                ? KeyAnalysis.analyse(winnerMono.samples(), winnerMono.rate()) : null;
        return key != null ? winner.withKey(key) : winner;
    }

    /** One window's outcome: the grid it produced (null when the estimator found none), how
     *  much audio it got to analyse, whether its own deadline cut the decode short, and the
     *  decoded window itself (kept for the key of whichever window wins). */
    private static final class Pass {
        final BeatProfile profile;
        final long analysedMs;
        final boolean truncated;
        final Mono mono;

        Pass(BeatProfile profile, long analysedMs, boolean truncated, Mono mono) {
            this.profile = profile;
            this.analysedMs = analysedMs;
            this.truncated = truncated;
            this.mono = mono;
        }
    }

    /** Decode and analyse one window, or null when this window has nothing to offer (a decode
     *  that failed, no format, no audio, less than {@link #MIN_USABLE_MS} of it). */
    private Pass one(String source, Attempt attempt) {
        Mono sink = new Mono(attempt.windowMs);
        long deadlineNs = System.nanoTime() + attempt.deadlineMs * 1_000_000L;
        int result = decode(source, deadlineNs, sink, attempt.startMs, attempt.what);
        if (result == DECODE_FAILED || !sink.sawFormat || sink.count <= 0) return null;
        long analysedMs = (long) (sink.count * 1000d / sink.rate());
        if (analysedMs < MIN_USABLE_MS) {
            Logger.info("beat probe: only {}ms decoded in the {} window", analysedMs,
                    attempt.what);
            return null;
        }
        BeatProfile p = BeatAnalysis.analyse(sink.samples(), sink.rate(), sink.originMs);
        return new Pass(p, analysedMs, result == DECODE_TRUNCATED, sink);
    }

    // --- decode -------------------------------------------------------------

    private static final int DECODE_FAILED = 0;
    /** The pass ran to the end of the stream or filled its window: everything the window
     *  asked for is there. */
    private static final int DECODE_OK = 1;
    /** The pass ran out of time before its window filled: what is there is a strict prefix of
     *  what was asked for, and a grid measured from it may be an artefact of the starvation
     *  rather than of the music (see the class doc — this is the flag the ladder gates on). */
    private static final int DECODE_TRUNCATED = 2;

    /**
     * Decode one window of {@code source} into {@code sink}.
     *
     * @param startMs where the window starts, ms. 0 is the file's head and needs no seek; a
     *                later start seeks to the sync point at or before it, and the sink then
     *                takes its origin from the first buffer that actually comes out — so the
     *                phase the estimator reports stays a position in the file even when the
     *                seek lands short of the request (see the class doc on phase semantics).
     */
    private int decode(String source, long deadlineNs, Mono sink, long startMs, String what) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        boolean produced = false;
        boolean inputDone = false;
        boolean outputDone = false;
        boolean stopped = false;
        boolean outOfTime = false;
        try {
            if (isRemote(source)) {
                // Same path the player uses for the bili CDN; a transition never
                // touches a bili stream, but going through it costs nothing and
                // keeps this honest about remote sources.
                extractor.setDataSource(appContext, Uri.parse(source), null);
            } else {
                extractor.setDataSource(source);
            }
            int track = selectAudioTrack(extractor);
            if (track < 0) return DECODE_FAILED;
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime == null || !mime.startsWith("audio/")) return DECODE_FAILED;
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();
            if (startMs > 0L) {
                // To the sync point at or before the requested start: a decode cannot begin
                // anywhere else, and the sink's origin is taken from what comes out (not from
                // what was asked for), so a short landing is labelled correctly rather than
                // shifting the grid by the difference.
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                Logger.info("beat probe: {} window decoding from {}ms", what, startMs);
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outputDone && !stopped) {
                if (System.nanoTime() > deadlineNs) {
                    // Out of time with the window unfilled: the caller has to know, because a
                    // grid from this much audio may be an artefact of the starvation (see
                    // DECODE_TRUNCATED).
                    outOfTime = true;
                    Logger.info("beat probe: {} window hit its deadline", what);
                    break;
                }
                if (!inputDone) {
                    int inIndex = codec.dequeueInputBuffer(10_000L);
                    if (inIndex >= 0) {
                        ByteBuffer in = codec.getInputBuffer(inIndex);
                        int size = in != null ? extractor.readSampleData(in, 0) : -1;
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(info, 10_000L);
                if (outIndex >= 0) {
                    ByteBuffer out = codec.getOutputBuffer(outIndex);
                    if (out != null && info.size > 0) {
                        produced = true;
                        if (!sink.pcm(out, info.size, info.presentationTimeUs)) stopped = true;
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!sink.format(codec.getOutputFormat())) stopped = true;
                }
            }
            if (outOfTime) return produced ? DECODE_TRUNCATED : DECODE_FAILED;
            return produced ? DECODE_OK : DECODE_FAILED;
        } catch (Throwable e) {
            Logger.warn("beat probe decode failed: {}", e.toString());
            return DECODE_FAILED;
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Throwable ignored) { }
                try { codec.release(); } catch (Throwable ignored) { }
            }
            try { extractor.release(); } catch (Throwable ignored) { }
        }
    }

    /** First audio track in the container, or -1. */
    private static int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private static boolean isRemote(String source) {
        return source.startsWith("http://") || source.startsWith("https://");
    }

    /** A short form of the source for logs (a CDN url carries tokens that would
     *  otherwise fill the debug panel). */
    private static String shortSource(String source) {
        int slash = source.lastIndexOf('/');
        String tail = slash >= 0 ? source.substring(slash + 1) : source;
        return tail.length() > 48 ? tail.substring(0, 48) : tail;
    }

    // --- the mono window ----------------------------------------------------

    /**
     * Decoded PCM in, mono samples out: every channel's average, decimated to about
     * {@link #TARGET_RATE} by box-averaging whole groups (which is also the
     * anti-alias filter the analysis needs — a decimated signal that still contains
     * 5 kHz content would put hat noise into the onset envelope).
     *
     * <p>The samples handed to the estimator are the first ones of the window, and
     * {@link #originMs} carries the file time of the first of them, so the returned
     * phase is a position in the track whatever window it was measured from — the
     * file's head, or {@link #LATE_START_MS} into it (see the class doc).
     */
    private static final class Mono {
        boolean sawFormat;
        int count;
        long originMs;
        /** How much audio this sink holds, ms: the window it was made for, which fixes the
         *  buffer and therefore what "the window is full" means. */
        private final long windowMs;
        private double[] out;
        private int rate;
        private int channels;
        private int decim = 1;
        private double accumulator;
        private int accumulatorFrames;
        /** Whether {@link #originMs} has been taken from a buffer yet. A flag rather
         *  than "originMs != 0", because the first decoded buffer's timestamp IS
         *  zero: testing the value instead silently took the *second* buffer's
         *  timestamp as the track's start, which put the whole grid one buffer late
         *  (measured: +23 ms on a synthetic track, the length of a 4 KB buffer). */
        private boolean originSet;

        Mono(long windowMs) {
            this.windowMs = windowMs;
        }

        /** Whether the decoded rate and channel count are usable; false stops the
         *  pass (the caller then has no grid, as with every other problem). */
        boolean format(MediaFormat f) {
            // 16-bit PCM only, like the silence profiler: a decoder handing back
            // float samples would need a different reader, and no transition is
            // worth that complication.
            if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                Integer encoding = null;
                try { encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING); }
                catch (Throwable ignored) { }
                if (encoding != null && encoding != AudioFormat.ENCODING_PCM_16BIT) return false;
            }
            int sampleRate = 0;
            int channelCount = 0;
            try { sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); } catch (Throwable ignored) { }
            try { channelCount = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT); } catch (Throwable ignored) { }
            if (sampleRate <= 0 || channelCount <= 0) return false;
            int decimation = Math.max(1, (int) Math.round(sampleRate / TARGET_RATE));
            int capacity = (int) (sampleRate / decimation * (windowMs / 1000d)) + 1024;
            rate = sampleRate / decimation;
            channels = channelCount;
            decim = decimation;
            out = new double[capacity];
            sawFormat = true;
            return true;
        }

        int rate() {
            return rate > 0 ? rate : (int) TARGET_RATE;
        }

        double[] samples() {
            double[] exact = new double[count];
            System.arraycopy(out, 0, exact, 0, count);
            return exact;
        }

        /** @return false once the window is full (that IS the answer). */
        boolean pcm(ByteBuffer data, int size, long ptsUs) {
            if (out == null) return false;
            if (!originSet) {
                originSet = true;
                originMs = ptsUs / 1000L;
            }
            data.order(ByteOrder.LITTLE_ENDIAN);
            int base = data.position();
            // One step per *frame*, not per 16-bit slot: stepping by slot reads
            // overlapping windows (the right channel of one frame with the left of the
            // next) and emits one sample per decim *slots* while the rate below says
            // decim *frames* — which halves every tempo on a stereo source. The
            // analysis trusts the rate it is handed, and this is the only place that
            // rate is established. (Measured before the fix: a 128 BPM click track
            // came back at 64.)
            int frameBytes = channels * 2;
            for (int at = base; at + frameBytes <= base + size; at += frameBytes) {
                double sum = 0d;
                for (int c = 0; c < channels; c++) sum += data.getShort(at + c * 2);
                accumulator += sum / channels;
                if (++accumulatorFrames < decim) continue;
                if (count >= out.length) return false;
                out[count++] = accumulator / decim;
                accumulator = 0d;
                accumulatorFrames = 0;
            }
            return count < out.length;
        }
    }
}
