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
 * <p>Nothing here can affect playback: it runs on the controller's beat worker,
 * opens its own extractor and codec, and answers null for every problem (unknown
 * container, no audio track, a decoder that will not start, float or 8-bit PCM,
 * deadline, I/O). A boundary with no grid is simply not aligned.
 */
public final class AndroidBeatProfiler implements BeatProfiler {

    /** How much audio is decoded, ms. */
    private static final long WINDOW_MS = 30_000L;

    /** The whole probe gives up here. The window is 30 s of audio, which this
     *  decodes in a fraction of a second locally and in a few seconds from a CDN;
     *  past this the answer would arrive after the boundary that wanted it. */
    private static final long DEADLINE_MS = 8_000L;

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
        // durationMsHint is deliberately unused: a grid is measured from the head of
        // the track, so its length adds nothing (see BeatProfiler.probe).
        if (source == null || source.isEmpty()) return null;
        long deadlineNs = System.nanoTime() + DEADLINE_MS * 1_000_000L;
        Mono sink = new Mono();
        int result = decode(source, deadlineNs, sink);
        if (result == DECODE_FAILED || !sink.sawFormat || sink.count <= 0) {
            Logger.info("beat probe: unusable for {}", shortSource(source));
            return null;
        }
        // A pass that hit the window limit or ran out of time is still usable: what
        // was decoded is a contiguous run from the track's start, and 8+ seconds of
        // it hold enough beats at any tempo in range. Only a pass with (almost)
        // nothing in it is a failure — and that is what MIN_USABLE_MS checks.
        long analysedMs = (long) (sink.count * 1000d / sink.rate());
        if (analysedMs < MIN_USABLE_MS) {
            Logger.info("beat probe: only {}ms decoded for {}", analysedMs, shortSource(source));
            return null;
        }
        BeatProfile p = BeatAnalysis.analyse(sink.samples(), sink.rate(), sink.originMs);
        if (p == null) {
            Logger.info("beat probe: no grid in {}ms of {}", analysedMs, shortSource(source));
            return null;
        }
        // The key, from the same window and the same samples: one decode answers both
        // of the questions a mix asks (where the beats are, and what key the music is
        // in). Measured only when a grid was found, which is deliberate — the key is
        // only ever used to transpose a track into another one's key during an
        // overlap, and a track with no grid cannot be aligned into an overlap in the
        // first place, so a profile for one would be a decode nobody reads.
        KeyProfile key = KeyAnalysis.analyse(sink.samples(), sink.rate());
        return key != null ? p.withKey(key) : p;
    }

    // --- decode -------------------------------------------------------------

    private static final int DECODE_FAILED = 0;
    /** The pass ran to the end of the stream, or to the window, or out of time —
     *  all of which leave usable audio behind (see the caller). */
    private static final int DECODE_OK = 1;

    private int decode(String source, long deadlineNs, Mono sink) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        boolean produced = false;
        boolean inputDone = false;
        boolean outputDone = false;
        boolean stopped = false;
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
            // No seek: the grid's phase is measured from the file's own start, so
            // the decode has to start there too.

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outputDone && !stopped) {
                if (System.nanoTime() > deadlineNs) {
                    Logger.info("beat probe: deadline hit");
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
     * <p>The samples handed to the estimator are the *first* ones of the track, and
     * {@link #originMs} carries the file time of the first of them, so the returned
     * phase is an offset into the track rather than into the decode.
     */
    private static final class Mono {
        boolean sawFormat;
        int count;
        long originMs;
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
            int capacity = (int) (sampleRate / decimation * (WINDOW_MS / 1000d)) + 1024;
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
