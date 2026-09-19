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
 *       transition has no use for.</li>
 *   <li>time: the whole probe gives up after {@link #DEADLINE_MS}. The answer is
 *       only worth having if it arrives before the boundary it would serve, so a
 *       slow decode is abandoned rather than waited on.</li>
 * </ul>
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

    /** The whole probe (both ends, including opening the source) gives up here. */
    private static final long DEADLINE_MS = 4_000L;

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
            // seam into the music. Hence DECODE_END, not "usable".
            TailSink tail = new TailSink();
            int tailResult = decode(source, Math.max(0L, duration - WINDOW_MS), deadlineNs, tail);
            if (tailResult != DECODE_END || !tail.sawFormat) {
                Logger.info("silence probe: tail pass unusable for {} ({})",
                        shortSource(source), tailResult);
                return null;
            }
            return new SilenceProfile(head.headMs(), tail.tailMs());
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
            sawFormat = true;
            return true;
        }

        @Override
        public boolean pcm(ByteBuffer data, int size) {
            data.order(ByteOrder.LITTLE_ENDIAN);
            int base = data.position();
            for (int i = 0; i + 1 < size; i += 2) {
                short sample = data.getShort(base + i);
                sumSquares += (double) sample * sample;
                if (++samples < perBlock) continue;
                double rms = Math.sqrt(sumSquares / samples) / 32768d;
                sumSquares = 0d;
                samples = 0;
                if (!block(rms)) return false;
            }
            return true;
        }

        /** One completed block; false stops the pass. */
        abstract boolean block(double rms);
    }

    /** Counts leading silent blocks and stops at the first one with signal in it. */
    private static final class HeadSink extends BlockSink {
        private long silentBlocks;

        @Override
        boolean block(double rms) {
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
     *  blocks — O(1) memory, since only the current run matters. */
    private static final class TailSink extends BlockSink {
        private long runMs;
        private long totalMs;

        @Override
        boolean block(double rms) {
            if (rms > SILENCE_RMS) {
                runMs = 0L;
            } else {
                runMs += BLOCK_MS;
            }
            // The seek lands on a sync frame, so the pass starts a little before
            // the requested window; bound it so a long file cannot turn into a long
            // decode.
            totalMs += BLOCK_MS;
            return totalMs < WINDOW_MS + 2000L;
        }

        long tailMs() {
            return Math.min(runMs, WINDOW_MS);
        }
    }
}
