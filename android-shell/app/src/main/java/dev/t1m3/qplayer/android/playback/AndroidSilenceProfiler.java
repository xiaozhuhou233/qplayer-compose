package dev.t1m3.qplayer.android.playback;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import dev.t1m3.qplayer.audio.SilenceProfile;
import dev.t1m3.qplayer.audio.SilenceProfiler;
import dev.t1m3.qplayer.util.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link SilenceProfiler} over {@code MediaExtractor} + {@code MediaCodec} — the
 * only decoders this app has, and both are platform classes, so measuring what a
 * track starts and ends with costs no dependency (see AI_HANDOFF §六).
 *
 * <p>Bounded on purpose, in both directions:
 * <ul>
 *   <li>audio: at most {@link #WINDOW_MS} from either end. A track whose first
 *       ten seconds are quiet is not "silent for ten seconds", it is quiet music,
 *       and reading further would spend a download per track on a number the
 *       transition has no use for. The tail pass reads a longer window
 *       ({@link #TAIL_WINDOW_MS}) because the same decode also has to answer "how
 *       much of this ending is plain" for a blend that may start earlier — but
 *       only the last {@link #WINDOW_MS} of it can ever be reported as silence.</li>
 *   <li>time: the whole probe gives up after {@link #DEADLINE_MS}. The answer is
 *       only worth having if it arrives before the boundary it would serve, so a
 *       slow decode is abandoned rather than waited on.</li>
 * </ul>
 *
 * <p>⚠️ The plain-ending half of the answer is deliberately computed from the block
 * envelope this pass already collects — no second decode and no new analysis pass.
 * It is a shape measurement (does the ending still attack, is anything singing in
 * the vocal band, is it quieter than the body of the window), and it is the only
 * evidence the app has for starting a blend before the nominal overlap.
 *
 * <p>Nothing here can affect playback: it runs on the controller's probe worker,
 * opens its own extractor/codec, and answers null for every problem (unknown
 * container, no audio track, a decoder that will not start, float PCM, deadline,
 * I/O). A boundary with no measurement does not trim — it never waits.
 */
public final class AndroidSilenceProfiler implements SilenceProfiler {

    /** How much audio either end is scanned for, ms. */
    private static final long WINDOW_MS = 10_000L;

    /** RMS below this counts as silence: about -46 dBFS at 16-bit full scale.
     *  Low enough that tape hiss and a faded-out outro count as silence, high
     *  enough that a deliberately quiet passage in the music does not. */
    private static final double SILENCE_RMS = 0.005d;

    /** One analysed block. 20 ms is longer than a 50 Hz cycle (so a block's RMS
     *  means something) and much shorter than any gap worth trimming. */
    private static final long BLOCK_MS = 20L;

    /** How much of the tail is read to describe the ending: the plain ending a
     *  blend may start inside can be as long as the longest overlap the app will
     *  ask for, and the measurement is what says where it begins. */
    private static final long TAIL_WINDOW_MS = 20_000L;

    /** The whole probe (both ends, including opening the source) gives up here.
     *  Sized for the tail pass's own window (see TAIL_WINDOW_MS): it is twice the
     *  audio the trim alone needed, it runs once per track on the probe worker, and
     *  it is cached — but it still has to be worth having, so it is bounded. */
    private static final long DEADLINE_MS = 6_000L;

    /** The vocal band, as a cheap two-pole band share: a one-pole high-pass at the
     *  bottom of it and a one-pole low-pass at the top. Coarse on purpose — it is
     *  not a voice detector, it is the difference between an ending that still has
     *  a mid-range voice in it and one that only has a bass line, a kick and a pad
     *  (see SilenceProfile.PLAIN_PRESENT_MAX). */
    private static final double VOCAL_BAND_LOW_HZ = 250d;
    private static final double VOCAL_BAND_HIGH_HZ = 3_500d;

    private final Context appContext;

    public AndroidSilenceProfiler(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    @Override
    public SilenceProfile probe(String source, long durationMsHint) {
        if (source == null || source.isEmpty()) return null;
        long deadlineNs = System.nanoTime() + DEADLINE_MS * 1_000_000L;
        try {
            // The head pass may stop early (that IS its answer): the first block
            // with signal in it, or the end of the window. A head read only part
            // way is still a lower bound on the real head silence, and a lower
            // bound errs toward starting the incoming track earlier — a slightly
            // long gap, never an overlap.
            HeadSink head = new HeadSink();
            int headResult = decode(source, 0L, deadlineNs, head);
            if (headResult == DECODE_FAILED || !head.sawFormat) {
                Logger.info("silence probe: head pass unusable for {}", shortSource(source));
                return null;
            }
            long duration = durationMsHint;
            if (duration <= 0L) duration = head.containerDurationMs;
            if (duration <= 0L) {
                // Without a length there is no "end" to seek to, and the tail is
                // half of what a trim needs.
                Logger.info("silence probe: no duration for {}", shortSource(source));
                return null;
            }
            // The tail pass must reach the end of the stream: a run of quiet blocks
            // collected part way through the file is a quiet passage in the middle
            // of the song, and mistaking one for the trailing silence would move the
            // seam into the music. Hence DECODE_END, not "usable". The window is the
            // longer TAIL_WINDOW_MS because the same pass also describes the ending
            // (how much of it is plain), and that answer is only meaningful measured
            // back from the file's own end.
            TailSink tail = new TailSink();
            int tailResult = decode(source, Math.max(0L, duration - TAIL_WINDOW_MS), deadlineNs, tail);
            if (tailResult != DECODE_END || !tail.sawFormat) {
                Logger.info("silence probe: tail pass unusable for {} ({})",
                        shortSource(source), tailResult);
                return null;
            }
            SilenceProfile profile = new SilenceProfile(head.headMs(), tail.tailMs(),
                    tail.plainTailMs(), tail.attacks());
            Logger.info("silence probe for {}: {}", shortSource(source), profile);
            Logger.info("silence probe tail for {}: {}", shortSource(source), tail.facts());
            return profile;
        } catch (Throwable e) {
            Logger.warn("silence probe failed for {}: {}", shortSource(source), e.toString());
            return null;
        }
    }

    // --- decode -------------------------------------------------------------

    /** Receives decoded PCM. Every method returns false to stop the pass — either
     *  because it has what it needs or because it cannot use what it is being
     *  given. */
    private interface Sink {
        boolean format(MediaFormat outputFormat);
        boolean pcm(ByteBuffer data, int size);
    }

    /** Decode {@code source} from {@code fromMs}, feeding {@code sink}, bounded by
     *  {@code deadlineNs}. */
    private static final int DECODE_FAILED = 0;
    /** The pass ended without reaching the end of the stream: the deadline, or the
     *  sink deciding it had what it needed. */
    private static final int DECODE_STOPPED = 1;
    /** The pass decoded through to the end of the stream. */
    private static final int DECODE_END = 2;

    private int decode(String source, long fromMs, long deadlineNs, Sink sink) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        boolean produced = false;
        boolean inputDone = false;
        boolean outputDone = false;
        boolean stoppedBySink = false;
        try {
            if (isRemote(source)) {
                // The (Context, Uri, headers) overload is also what the player uses
                // for the bili CDN; a transition never touches a bili stream, but
                // going through the same path costs nothing and keeps this honest
                // about remote sources.
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
            extractor.seekTo(Math.max(0L, fromMs) * 1000L,
                    fromMs > 0L ? MediaExtractor.SEEK_TO_PREVIOUS_SYNC
                                : MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outputDone && !stoppedBySink) {
                if (System.nanoTime() > deadlineNs) {
                    Logger.info("silence probe: deadline hit");
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
                        if (!sink.pcm(out, info.size)) stoppedBySink = true;
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!sink.format(codec.getOutputFormat())) stoppedBySink = true;
                }
            }
            if (!produced) return DECODE_FAILED;
            return outputDone ? DECODE_END : DECODE_STOPPED;
        } catch (Throwable e) {
            Logger.warn("silence probe decode failed: {}", e.toString());
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

    /** A short form of the source for logs: the last path segment, since a CDN url
     *  carries tokens that would otherwise fill the debug panel. */
    private static String shortSource(String source) {
        int slash = source.lastIndexOf('/');
        String tail = slash >= 0 ? source.substring(slash + 1) : source;
        return tail.length() > 48 ? tail.substring(0, 48) : tail;
    }

    // --- sinks --------------------------------------------------------------

    /** Shared block arithmetic: PCM bytes in, one RMS decision per {@link #BLOCK_MS}. */
    private abstract static class BlockSink implements Sink {
        boolean sawFormat;
        private int perBlock = 1;
        private double sumSquares;
        private int samples;
        long containerDurationMs;
        /** Whether the block envelope also needs the vocal-band share (the tail
         *  pass does; the head pass stops at the first sound and needs no shape). */
        private boolean wantsPresence;
        // The two one-pole sections behind that share, and their state. A single
        // state across the interleaved channels is deliberate: the share is a coarse
        // "is a voice in here" reading, and both channels carry the same music.
        private double hpOut;
        private double hpPrev;
        private double hpCoeff = 1d;
        private double lpOut;
        private double lpCoeff = 1d;
        private double bandSumSquares;

        @Override
        public boolean format(MediaFormat f) {
            // 16-bit PCM only: a decoder handing back float samples would need a
            // different reader, and no transition is worth that complication.
            if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                Integer encoding = null;
                try { encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING); }
                catch (Throwable ignored) { }
                if (encoding != null && encoding != AudioFormat.ENCODING_PCM_16BIT) return false;
            }
            int rate = 0;
            int channels = 0;
            try { rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); } catch (Throwable ignored) { }
            try { channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT); } catch (Throwable ignored) { }
            if (rate <= 0 || channels <= 0) return false;
            if (f.containsKey(MediaFormat.KEY_DURATION)) {
                try {
                    containerDurationMs = f.getLong(MediaFormat.KEY_DURATION) / 1000L;
                } catch (Throwable ignored) { }
            }
            // Samples (not frames) per block: the RMS below runs over the
            // interleaved stream, which is what a listener hears summed.
            perBlock = Math.max(1, (int) (rate * (long) channels * BLOCK_MS / 1000L));
            double fs = rate;
            hpCoeff = 1d / (1d + 2d * Math.PI * VOCAL_BAND_LOW_HZ / fs);
            lpCoeff = 1d - Math.exp(-2d * Math.PI * VOCAL_BAND_HIGH_HZ / fs);
            sawFormat = true;
            return true;
        }

        /** Ask this sink for the vocal-band share of every block. */
        void wantPresence() {
            wantsPresence = true;
        }

        @Override
        public boolean pcm(ByteBuffer data, int size) {
            data.order(ByteOrder.LITTLE_ENDIAN);
            int base = data.position();
            for (int i = 0; i + 1 < size; i += 2) {
                short sample = data.getShort(base + i);
                double x = (double) sample / 32768d;
                sumSquares += x * x;
                if (wantsPresence) {
                    // High-pass, then low-pass: what is left is the band a voice
                    // lives in (see VOCAL_BAND_*).
                    hpOut = hpCoeff * (hpOut + x - hpPrev);
                    hpPrev = x;
                    lpOut += lpCoeff * (hpOut - lpOut);
                    bandSumSquares += lpOut * lpOut;
                }
                if (++samples < perBlock) continue;
                double rms = Math.sqrt(sumSquares / samples);
                double presence = wantsPresence && sumSquares > 0d
                        ? Math.sqrt(bandSumSquares / sumSquares) : -1d;
                sumSquares = 0d;
                bandSumSquares = 0d;
                samples = 0;
                if (!block(rms, presence)) return false;
            }
            return true;
        }

        /** One completed block (linear RMS, and the vocal-band share or -1);
         *  false stops the pass. */
        abstract boolean block(double rms, double presence);
    }

    /** Counts leading silent blocks and stops at the first one with signal in it. */
    private static final class HeadSink extends BlockSink {
        private long silentBlocks;

        @Override
        boolean block(double rms, double presence) {
            if (rms > SILENCE_RMS) return false;   // content starts here
            silentBlocks++;
            // The whole window is silent: report the window. Anything beyond it is
            // (deliberately) not looked at, and reporting the cap is the honest
            // reading of it.
            return silentBlocks * BLOCK_MS < WINDOW_MS;
        }

        long headMs() {
            return Math.min(silentBlocks * BLOCK_MS, WINDOW_MS);
        }
    }

    /** Runs to the end of the stream, remembering the trailing run of silent
     *  blocks (O(1) memory, since only the current run matters) and — for the plain
     *  ending — the envelope of the whole window, which is a second of doubles. */
    private static final class TailSink extends BlockSink {
        /** The window is TAIL_WINDOW_MS; the seek lands on a sync frame and may
         *  start a little before it, so collect a little more and keep the end. */
        private static final int WINDOW_BLOCKS = (int) (TAIL_WINDOW_MS / BLOCK_MS);
        private static final int CAPACITY = WINDOW_BLOCKS + 200;
        private final double[] levels = new double[CAPACITY];
        private final double[] presence = new double[CAPACITY];
        private int blocks;
        private long runMs;
        private long totalMs;

        TailSink() {
            wantPresence();
        }

        @Override
        boolean block(double rms, double presenceShare) {
            if (rms > SILENCE_RMS) {
                runMs = 0L;
            } else {
                runMs += BLOCK_MS;
            }
            if (blocks < CAPACITY) {
                levels[blocks] = rms;
                presence[blocks] = presenceShare;
                blocks++;
            }
            // The seek lands on a sync frame, so the pass starts a little before
            // the requested window; bound it so a long file cannot turn into a long
            // decode. The slack is generous (5 s) because the rest of the pass is
            // bounded by DEADLINE_MS anyway, while the cost of stopping early is the
            // whole measurement: a track whose reported length is a few seconds short
            // of its own audio used to answer "unusable" for both ends at once.
            totalMs += BLOCK_MS;
            return totalMs < TAIL_WINDOW_MS + 5000L;
        }

        long tailMs() {
            return Math.min(runMs, WINDOW_MS);
        }

        /** Only the last WINDOW_BLOCKS blocks describe the ending: anything the
         *  sync-frame seek pulled in from before the window is not part of it. */
        private double[] windowOf(double[] all) {
            int from = Math.max(0, blocks - WINDOW_BLOCKS);
            return java.util.Arrays.copyOfRange(all, from, blocks);
        }

        long plainTailMs() {
            if (blocks == 0) return 0L;
            return SilenceProfile.plainTailMsOf(windowOf(levels), windowOf(presence), BLOCK_MS);
        }

        int attacks() {
            return blocks == 0 ? 0 : SilenceProfile.attacksOf(windowOf(levels));
        }

        /**
         * The ingredients of {@link #plainTailMs()}, for the log: a "plain ending"
         * that measures as zero has to be explainable — which test refused it, and
         * with what number — or the rule can only be tuned by guessing.
         */
        String facts() {
            if (blocks == 0) return "no blocks";
            double[] lv = windowOf(levels);
            double[] pr = windowOf(presence);
            double body = percentile(lv, 0.9d);
            double cap = body * SilenceProfile.PLAIN_LEVEL_SHARE;
            int attackFree = 0;
            for (int i = lv.length - 1; i >= 0; i--) {
                if (i + 1 < lv.length && lv[i + 1] > lv[i] * SilenceProfile.ATTACK_RATIO
                        && lv[i + 1] > SilenceProfile.ATTACK_FLOOR) {
                    break;
                }
                attackFree++;
            }
            int quiet = 0;
            for (int i = lv.length - 1; i >= 0 && lv[i] <= cap; i--) quiet++;
            int voiceFree = 0;
            for (int i = lv.length - 1; i >= 0; i--) {
                if (i < pr.length && pr[i] > SilenceProfile.PLAIN_PRESENT_MAX) break;
                voiceFree++;
            }
            return String.format(java.util.Locale.US,
                    "window=%dms body(p90)=%.1fdBFS cap=%.1fdBFS end=%.1fdBFS last2s=%.1fdBFS"
                            + " attacks=%d attackFree=%dms quiet=%dms voiceFree=%dms"
                            + " presence(last2s median)=%.2f",
                    blocks * BLOCK_MS, db(body), db(cap), db(lv[lv.length - 1]),
                    db(medianLast(lv, 2000L)), attacks(), attackFree * BLOCK_MS,
                    quiet * BLOCK_MS, voiceFree * BLOCK_MS,
                    medianLast(pr, 2000L));
        }

        private static double medianLast(double[] values, long ms) {
            int n = Math.max(1, (int) Math.min(values.length, ms / BLOCK_MS));
            double[] tail = java.util.Arrays.copyOfRange(values, values.length - n, values.length);
            return percentile(tail, 0.5d);
        }

        private static double percentile(double[] values, double p) {
            double[] sorted = values.clone();
            java.util.Arrays.sort(sorted);
            int i = (int) Math.round(p * (sorted.length - 1));
            return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
        }

        private static double db(double linear) {
            return linear <= 0d ? -120d : 20d * Math.log10(linear);
        }
    }
}
