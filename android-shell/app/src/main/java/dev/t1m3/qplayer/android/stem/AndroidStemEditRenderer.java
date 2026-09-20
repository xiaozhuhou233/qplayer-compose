package dev.t1m3.qplayer.android.stem;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import dev.t1m3.qplayer.audio.DjEdit;
import dev.t1m3.qplayer.audio.StemEditRenderer;
import dev.t1m3.qplayer.audio.StemGesture;
import dev.t1m3.qplayer.audio.StemModel;
import dev.t1m3.qplayer.util.Logger;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The host's stem renderer: it turns one track's audio file into a
 * {@link dev.t1m3.qplayer.audio.DjEdit} file — the same track, at the same length, with
 * its vocals taken out of the first N milliseconds and handed back on a bar line.
 *
 * <p>Everything this class does was measured before it was written (AI_HANDOFF §7, rounds
 * 6-7): the quarter model at 44.1 kHz, a CPU session with the arena allocator off and four
 * intra-op threads, the window chunked at the model's own segment length with a
 * quarter-segment overlap and a triangular weight, and {@code other} derived by
 * subtraction so the four stems sum back to the master exactly — which is what lets a
 * window with its vocals removed equal the backing, sample for sample.
 *
 * <p><b>Where it runs.</b> Never on a playback path. The controller asks for a render from
 * the preload lane, minutes before the boundary it is for, and this method does the work on
 * that same single-threaded lane: a separation costs 15-35 s of CPU on the reference device
 * and ~860 MB of peak memory, which is affordable with minutes of margin and impossible
 * inside a boundary's lead. {@link StemEditRenderer.Request#stillWanted} is polled between
 * chunks, so a queue that moves on abandons the work instead of finishing it.
 *
 * <p><b>Where the file goes.</b> A file that exists is a finished edit; every failure path
 * deletes what it wrote, so the caller's whole test is "is the file there". Nothing partial
 * ever survives to be played.
 *
 * <p><b>What it is not.</b> It is not a gate on anything. No model, a digest that does not
 * match, a decode that fails, a refused muxer, a cancelled render — each of those returns
 * false, the caller logs it, and the boundary blends the plain stream exactly as it did
 * before this feature existed.
 */
public final class AndroidStemEditRenderer implements StemEditRenderer {

    /** Where the user puts the model: the app's own private storage, alone in a directory
     *  of its own. The line a missing model writes names the directory and the
     *  {@code adb push} that fills it, because model delivery is deliberately not
     *  implemented (a 98 MB download must never be the default). */
    public static final String MODELS_DIR = "models";

    /** The AAC bitrate the edit is encoded at. High, because this file is not only the
     *  blend window: after the promotion it is the track the listener keeps hearing, so
     *  the generation loss of a second encode has to not be the loudest thing about this
     *  path. */
    private static final int BITRATE = 192_000;

    /** The most frames one encoder input buffer takes. */
    private static final int ENCODE_CHUNK_FRAMES = 4096;

    /** How far past the removal window the separated head reaches: the vocals return on
     *  the first bar line at or after it, and a bar is four beats — so the window has to
     *  cover a whole bar of the incoming track at its own tempo, plus a second of slack.
     *  Capped, because a very slow track must not turn a 15 s blend into a 40 s
     *  separation. */
    private static final long RETURN_SPAN_MAX_MS = 8_000L;

    /** Below this a written edit is not a recording (the same "did the file come out
     *  plausibly" check the audio pre-cache makes, at a lower bar because this is a
     *  re-encode). */
    private static final long MIN_EDIT_BYTES = 100_000L;

    private final File modelsDir;
    private final Object modelLock = new Object();
    private volatile StemModel.Candidate model;
    private volatile boolean modelChecked;
    private volatile boolean inertLogged;

    public AndroidStemEditRenderer(Context context) {
        this.modelsDir = new File(context.getFilesDir(), MODELS_DIR);
    }

    // --- the model ----------------------------------------------------------

    @Override
    public boolean available() {
        return model() != null;
    }

    /**
     * The verified model, or null. Looked up once per process: the check hashes ~98 MB
     * (~1 s), so it happens on the preload lane the first time a render is asked for and
     * never again. A feature that silently switched weights mid-session would be worse
     * than one that needs a restart.
     */
    private StemModel.Candidate model() {
        if (modelChecked) return model;
        synchronized (modelLock) {
            if (modelChecked) return model;
            for (StemModel.Candidate candidate : StemModel.candidates()) {
                File file = new File(modelsDir, candidate.fileName);
                if (!file.isFile()) continue;
                long bytes = file.length();
                String digest = StemModel.sha256(file);
                StemModel.Candidate accepted =
                        StemModel.recognise(candidate.fileName, bytes, digest);
                if (accepted != null) {
                    model = accepted;
                    modelChecked = true;
                    Logger.info("transition: stem DJ edits are ON — {} verified in {} ({} bytes,"
                                    + " sha256 {}); the separation runs on {} CPU threads with the"
                                    + " arena allocator off, off the playback path",
                            accepted.fileName, modelsDir.getAbsolutePath(), bytes,
                            accepted.sha256, StemModel.INTRA_OP_THREADS);
                    return model;
                }
                Logger.warn("transition: {} is present but does not match the manifest ({} bytes,"
                                + " sha256 {}; expected {}) — refusing it, so the stem path stays"
                                + " inert rather than feeding audio through weights this build"
                                + " cannot identify",
                        file.getAbsolutePath(), bytes, digest, candidate.sha256);
            }
            modelChecked = true;
            model = null;
            return null;
        }
    }

    /** The one line a missing model writes per process: what was looked for, where, and
     *  the command that puts it there — since model delivery is not implemented, this line
     *  <em>is</em> the delivery path. */
    private void logInertReason() {
        if (inertLogged) return;
        inertLogged = true;
        Logger.info("transition: stem DJ edits are OFF — no verified model in {}. The blend is"
                        + " exactly what it is without this feature. To try it (the only delivery"
                        + " path there is; hf-mirror.com is reachable from this device):"
                        + " adb push htdemucs-quarter.onnx /sdcard/ && adb shell run-as"
                        + " dev.t1m3.qplayer.debug cp /sdcard/htdemucs-quarter.onnx files/models/"
                        + " — that file name, {} bytes, sha256 {}; {}",
                modelsDir.getAbsolutePath(), StemModel.QUARTER.bytes, StemModel.QUARTER.sha256,
                StemModel.manifest());
    }

    // --- the render ---------------------------------------------------------

    @Override
    public boolean render(Request request) {
        StemModel.Candidate weights = model();
        if (weights == null) {
            logInertReason();
            return false;
        }
        File out = new File(request.outPath);
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        long startedAt = System.currentTimeMillis();
        try {
            if (run(weights, request, out, startedAt)) return true;
            deleteQuietly(out);
            return false;
        } catch (Throwable e) {
            // Every failure is this one: the boundary blends the plain stream. Logged with
            // the fact that it happened at all, because "the render failed" and "the render
            // was never asked for" are different things to know about this feature.
            Logger.warn("transition: DJ edit for {} failed ({}); the boundary blends the plain"
                    + " stream", request.title(), e.toString());
            deleteQuietly(out);
            return false;
        }
    }

    private boolean run(StemModel.Candidate weights, Request request, File out, long startedAt)
            throws Exception {
        Format format = probe(request.sourcePath);
        if (format == null) {
            Logger.warn("transition: DJ edit for {}: no audio track in {}", request.title(),
                    request.sourcePath);
            return false;
        }
        long removalMs = request.removalMs;
        long spanMs = returnSpanMs(request.beatPeriodMs);
        long headMs = removalMs + spanMs;
        Logger.info("transition: rendering the DJ edit for {} — vocals out for the first {}ms,"
                        + " back on a bar line inside the next {}ms; source {}Hz, window {}ms from"
                        + " the file's own start (so the file's timeline is the track's timeline)",
                request.title(), removalMs, spanMs, format.rate, headMs);

        // 1. The head, decoded from the file's start. Starting at zero is what keeps every
        //    offset the transition machinery already computes — the content start, the
        //    beat entry, the ramp's handoff, the published position — meaning the same
        //    thing on this file as on the stream it replaces.
        long headFrames = headMs * format.rate / 1000L;
        final float[][] head = new float[2][(int) headFrames];
        long[] decoded = new long[1];
        boolean[] cancelled = new boolean[1];
        boolean completed = decode(request.sourcePath, 2, head[0].length, request.stillWanted,
                cancelled, (pcm, frames) -> {
                    for (int ch = 0; ch < head.length && ch < pcm.length; ch++) {
                        System.arraycopy(pcm[ch], 0, head[ch], (int) decoded[0], frames);
                    }
                    decoded[0] += frames;
                });
        if (cancelled[0]) {
            Logger.info("transition: DJ edit for {} cancelled while decoding the head (the queue"
                    + " moved on)", request.title());
            return false;
        }
        if (!completed || decoded[0] < format.rate / 4L) {
            Logger.warn("transition: DJ edit for {}: the head decoded to only {} frames; nothing"
                    + " to render", request.title(), decoded[0]);
            return false;
        }
        // The decode fills a buffer sized for the whole window and reports how much of it is
        // real; the trimmed copy is a second array rather than a reassignment, because the
        // sink above captures the first one (and because a `final` local cannot be
        // reassigned — this is the line the first build of this class failed on).
        final float[][] headWindow = trim(head, (int) decoded[0]);

        // 2. The separation, at the rate and on the grid the model was trained for.
        float[][] headForModel = resample(headWindow, format.rate, StemModel.MODEL_RATE);
        long separateStart = System.currentTimeMillis();
        float[][][] stems = separate(weights, headForModel, request.stillWanted);
        if (stems == null) {
            Logger.info("transition: DJ edit for {} cancelled during the separation",
                    request.title());
            return false;
        }
        long separateMs = System.currentTimeMillis() - separateStart;

        // 3. The user's clause: if that head has no vocals in it there is nothing to strip,
        //    so the whole stem path is skipped and the incoming plays its own stream. The
        //    saving is everything after this point — the body decode, the encode, the mux
        //    and the file — which is most of the second half of a render.
        double removalSec = removalMs / 1000.0;
        DjEdit.Presence presence = DjEdit.presence(stems[StemGesture.Stem.VOCALS.row()],
                StemModel.MODEL_RATE, removalSec);
        if (!presence.sings) {
            Logger.info("transition: DJ edit for {} not needed — its first {}ms is measured to"
                            + " have no vocals in it, so there is nothing to strip and the"
                            + " incoming plays its own stream: {}",
                    request.title(), removalMs, presence.describe());
            return false;
        }
        Logger.info("transition: DJ edit for {}: its first {}ms does sing — {}", request.title(),
                removalMs, presence.describe());

        // 4. Where the voice comes back: the first bar line of the incoming track at or
        //    after the removal window. The offset is estimated from the separated BASS
        //    stem's low end — the documented extension this platform has instead of a
        //    structure pass — and never from the beat grid's phase, which is a different
        //    question and wrong three times in four.
        double windowSec = headForModel[0].length / (double) StemModel.MODEL_RATE;
        DjEdit.Plan plan = editPlan(stems, request, windowSec, removalSec);

        // 5. The render.
        int[] clipped = new int[1];
        float[][] edited = DjEdit.renderHead(stems, StemModel.MODEL_RATE, windowSec, plan, clipped);
        float[][] editedForSource = resample(edited, StemModel.MODEL_RATE, format.rate);
        Logger.info("transition: DJ edit for {}: {}; {} of {} samples clamped; separation took"
                        + " {}ms",
                request.title(), plan.describe(), clipped[0],
                edited[0].length * edited.length, separateMs);

        // 6. One file: the edited head, then the rest of the track decoded straight through.
        //    This is what makes the incoming deck able to open a single source and keep it —
        //    before the blend, through the promotion, and to the end of the track.
        long encodeStart = System.currentTimeMillis();
        AacFileWriter writer = new AacFileWriter(out, format.rate, 2);
        boolean wrote = false;
        try {
            writer.start();
            writer.write(editedForSource);
            if (!decodeBody(request.sourcePath, editedForSource[0].length, writer,
                    request.stillWanted)) {
                Logger.info("transition: DJ edit for {} cancelled while writing the body (the"
                        + " queue moved on)", request.title());
                return false;
            }
            writer.finish();
            wrote = true;
        } finally {
            if (!wrote) {
                writer.abort();
                deleteQuietly(out);
            }
        }
        long bytes = out.length();
        if (bytes < MIN_EDIT_BYTES) {
            Logger.warn("transition: DJ edit for {} came out at {} bytes, which is not a"
                    + " recording; dropped it", request.title(), bytes);
            deleteQuietly(out);
            return false;
        }
        Logger.info("transition: DJ edit for {} written: {} ({}KB; {}ms of encode, {}ms of render"
                        + " in all) — the boundary plays this file on the incoming deck when it"
                        + " gets there",
                request.title(), out.getAbsolutePath(), bytes / 1024L,
                System.currentTimeMillis() - encodeStart,
                System.currentTimeMillis() - startedAt);
        return true;
    }

    /** The plan for the window: the return ends on the first bar line at or after the
     *  removal window, and without a grid it ends with the window itself. */
    private DjEdit.Plan editPlan(float[][][] stems, Request request, double windowSec,
                                 double removalSec) {
        if (!(request.beatPeriodMs > 0)) {
            Logger.info("transition: DJ edit for {}: no beat grid for this track, so there are no"
                            + " bar lines to land on; the vocals return at the end of the"
                            + " {}ms window", request.title(), request.removalMs);
            return DjEdit.plan(removalSec, Double.NaN);
        }
        float[] lowBand = StemGesture.envelopeOf(
                new float[][]{stems[StemGesture.Stem.BASS.row()][0],
                        stems[StemGesture.Stem.BASS.row()][1]},
                StemModel.MODEL_RATE, StemGesture.CELL_SEC);
        double beatSec = request.beatPeriodMs / 1000.0;
        double beatPhaseSec = request.beatPhaseMs / 1000.0;
        double downbeat = StemGesture.downbeatOffsetSec(lowBand, 0, StemGesture.CELL_SEC,
                beatPhaseSec, beatSec, DjEdit.BEATS_PER_BAR);
        if (Double.isNaN(downbeat)) {
            Logger.info("transition: DJ edit for {}: the low end gave no downbeat offset, so the"
                            + " vocals return at the end of the {}ms window rather than on a line"
                            + " nobody measured", request.title(), request.removalMs);
            return DjEdit.plan(removalSec, Double.NaN);
        }
        double bpm = 60_000.0 / request.beatPeriodMs;
        double[] bars = StemGesture.barLines(bpm, downbeat, DjEdit.BEATS_PER_BAR, 0, windowSec);
        double at = DjEdit.firstBarAtOrAfter(bars, removalSec);
        Logger.info("transition: DJ edit for {}: {}BPM, downbeat offset estimated from the bass"
                        + " stem, {} bar lines inside the {}ms window; vocals return {}",
                request.title(), String.format(java.util.Locale.US, "%.1f", bpm), bars.length,
                headMsOf(request), Double.isNaN(at)
                        ? "at the end of the window (no bar line in it)"
                        : "at " + String.format(java.util.Locale.US, "%.3f", at) + "s (a bar line)");
        return DjEdit.plan(removalSec, at);
    }

    /** The separated head's length in ms, for the log line above: the removal window plus
     *  one bar of this track and a second of slack, capped. */
    private static long headMsOf(StemEditRenderer.Request request) {
        return request.removalMs + returnSpanMs(request.beatPeriodMs);
    }

    /** How far past the removal window the head has to reach for the return to land on a
     *  bar line: one bar of the incoming track at its own tempo, plus a second. */
    private static long returnSpanMs(double beatPeriodMs) {
        if (!(beatPeriodMs > 0)) return 1_500L;
        long bar = Math.round(beatPeriodMs * DjEdit.BEATS_PER_BAR);
        return Math.min(RETURN_SPAN_MAX_MS, bar + 1_000L);
    }

    // --- decode / resample --------------------------------------------------

    /** What the source file holds. */
    private static final class Format {
        int rate;
        String mime;
    }

    private Format probe(String path) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                Format out = new Format();
                out.mime = mime;
                out.rate = f.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                        ? f.getInteger(MediaFormat.KEY_SAMPLE_RATE) : StemModel.MODEL_RATE;
                if (out.rate <= 0) out.rate = StemModel.MODEL_RATE;
                return out;
            }
        } catch (Throwable e) {
            Logger.warn("transition: DJ edit: cannot read {} ({})", path, e.toString());
        } finally {
            extractor.release();
        }
        return null;
    }

    /** The body of the track: everything from the file's start except the first
     *  {@code skipFrames} frames, which the rendered head already covered. Streamed to the
     *  encoder rather than held — a five-minute song is a hundred megabytes of float. */
    private boolean decodeBody(String path, int skipFrames, AacFileWriter writer,
                               BooleanSupplier wanted) throws Exception {
        long[] seen = new long[1];
        boolean[] cancelled = new boolean[1];
        boolean completed = decode(path, 2, -1, wanted, cancelled, (pcm, frames) -> {
            int from = 0;
            if (seen[0] < skipFrames) {
                // A block that straddles the splice: only the part past it is the body.
                from = (int) Math.min(frames, skipFrames - seen[0]);
            }
            seen[0] += frames;
            if (from < frames) writer.write(slice(pcm, from, frames - from));
        });
        return completed && !cancelled[0];
    }

    /** One block of decoded audio, both channels interleaved-free. */
    private interface Frames {
        void onBlock(float[][] pcm, int frames);
    }

    /**
     * Decodes a source file from its start, handing blocks to {@code sink} up to
     * {@code maxFrames} (or to the end of the file when negative).
     *
     * @return true when the file was decoded to its end (or to the limit), false when it
     *         was cut short; {@code cancelled} says whether that was the caller's doing.
     */
    private boolean decode(String path, int channels, long maxFrames, BooleanSupplier wanted,
                           boolean[] cancelled, Frames sink) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(path);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    fmt = f;
                    break;
                }
            }
            if (track < 0) return false;
            extractor.selectTrack(track);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            int sourceChannels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? Math.max(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) : channels;
            long decoded = 0;
            boolean inputDone = false, outputDone = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] bytes = new byte[0];
            int blocks = 0;
            while (!outputDone) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10_000);
                    if (in >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(in);
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = codec.dequeueOutputBuffer(info, 10_000);
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        ByteBuffer out = codec.getOutputBuffer(outIndex);
                        out.position(info.offset);
                        out.limit(info.offset + info.size);
                        if (bytes.length < info.size) bytes = new byte[info.size];
                        out.get(bytes, 0, info.size);
                        int frames = info.size / (2 * sourceChannels);
                        // Only as many frames as the caller still wants: a block that
                        // straddles the head's end must not run past it.
                        int take = frames;
                        if (maxFrames >= 0) take = (int) Math.min(frames, maxFrames - decoded);
                        if (take > 0) {
                            float[][] pcm = new float[channels][take];
                            for (int i = 0; i < take; i++) {
                                for (int ch = 0; ch < channels; ch++) {
                                    int at = (i * sourceChannels
                                            + Math.min(ch, sourceChannels - 1)) * 2;
                                    short s = (short) ((bytes[at] & 0xFF) | (bytes[at + 1] << 8));
                                    pcm[ch][i] = s / 32768f;
                                }
                            }
                            sink.onBlock(pcm, take);
                            decoded += take;
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    if (maxFrames >= 0 && decoded >= maxFrames) return true;
                    if (++blocks % 16 == 0 && wanted != null && !wanted.getAsBoolean()) {
                        cancelled[0] = true;
                        return false;
                    }
                }
            }
            return true;
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Throwable ignored) { }
                try { codec.release(); } catch (Throwable ignored) { }
            }
            extractor.release();
        }
    }

    /** Linear resampling — the same instrument the harness used, and for the same reason:
     *  the model is trained for 44.1 kHz and this library holds 48 kHz files too, where a
     *  window fed at the wrong rate is the same music at the wrong tempo, in the wrong
     *  place, with every time in the plan landing somewhere else. Only the head window is
     *  ever resampled; the body of the track keeps its own rate. */
    static float[][] resample(float[][] pcm, int fromRate, int toRate) {
        if (fromRate == toRate || pcm.length == 0 || pcm[0].length == 0) return pcm;
        int frames = pcm[0].length;
        int target = (int) Math.floor(frames * (double) toRate / fromRate);
        float[][] out = new float[pcm.length][target];
        double step = fromRate / (double) toRate;
        for (int i = 0; i < target; i++) {
            double at = i * step;
            int lo = (int) at;
            int hi = Math.min(lo + 1, frames - 1);
            double frac = at - lo;
            for (int ch = 0; ch < pcm.length; ch++) {
                out[ch][i] = (float) (pcm[ch][lo] * (1 - frac) + pcm[ch][hi] * frac);
            }
        }
        return out;
    }

    private static float[][] trim(float[][] pcm, int frames) {
        float[][] out = new float[pcm.length][];
        for (int ch = 0; ch < pcm.length; ch++) {
            out[ch] = java.util.Arrays.copyOf(pcm[ch], Math.min(frames, pcm[ch].length));
        }
        return out;
    }

    private static float[][] slice(float[][] pcm, int from, int frames) {
        float[][] out = new float[pcm.length][];
        for (int ch = 0; ch < pcm.length; ch++) {
            out[ch] = java.util.Arrays.copyOfRange(pcm[ch], from, from + frames);
        }
        return out;
    }

    // --- the separation -----------------------------------------------------

    /**
     * Runs the model over the window, chunked at the segment length the graph itself
     * declares, with a quarter-segment overlap and a triangular weight — the geometry the
     * reference implementation uses and the harness reproduced, whose four rows sum back to
     * the mix (the model's own rows: -32 dB, which is why {@code other} is derived by
     * subtraction below, making the sum exact).
     *
     * <p>Returns null when the caller asked to stop. The session and the environment are
     * closed here rather than kept: this is a rare, expensive operation, and holding a
     * session alive would hold its arenas with it.
     */
    private float[][][] separate(StemModel.Candidate weights, float[][] pcm,
                                 BooleanSupplier wanted) throws Exception {
        int total = pcm[0].length;
        int channels = pcm.length;
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        OrtSession session = null;
        try {
            options.setCPUArenaAllocator(StemModel.CPU_ARENA_ALLOCATOR);
            options.setIntraOpNumThreads(StemModel.INTRA_OP_THREADS);
            long createdAt = System.currentTimeMillis();
            session = env.createSession(new File(modelsDir, weights.fileName).getAbsolutePath(),
                    options);
            String inputName = null;
            long segment = -1;
            for (Map.Entry<String, NodeInfo> e : session.getInputInfo().entrySet()) {
                TensorInfo tensor = (TensorInfo) e.getValue().getInfo();
                inputName = e.getKey();
                long[] shape = tensor.getShape();
                segment = shape[shape.length - 1];
            }
            if (inputName == null || segment <= 0) {
                Logger.warn("transition: DJ edit: the model declares no usable input; refusing it");
                return null;
            }
            if (weights.segmentSamples > 0 && segment != weights.segmentSamples) {
                Logger.warn("transition: DJ edit: the graph declares a {} sample segment where the"
                                + " manifest says {} — using the graph's, which is what the"
                                + " chunking indexes with", segment, weights.segmentSamples);
            }
            Logger.info("transition: DJ edit: session ready in {}ms, segment {} samples ({}ms),"
                            + " {} threads, CPU arena {}",
                    System.currentTimeMillis() - createdAt, segment,
                    Math.round(segment * 1000.0 / StemModel.MODEL_RATE),
                    StemModel.INTRA_OP_THREADS, StemModel.CPU_ARENA_ALLOCATOR ? "on" : "off");

            int overlap = (int) (segment / 4);
            int stride = (int) (segment - overlap);
            int chunks = Math.max(1, (int) Math.ceil(total / (double) stride));
            float[] window = window((int) segment);
            float[][][] stems = new float[4][channels][total];
            float[] weight = new float[total];
            for (int c = 0; c < chunks; c++) {
                if (wanted != null && !wanted.getAsBoolean()) return null;
                int start = c * stride;
                int n = Math.min((int) segment, total - start);
                if (n <= 0) break;
                FloatBuffer feed = ByteBuffer.allocateDirect((int) segment * channels * 4)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer();
                for (int ch = 0; ch < channels; ch++) {
                    for (int i = 0; i < n; i++) {
                        feed.put(ch * (int) segment + i, pcm[ch][start + i]);
                    }
                }
                OnnxTensor input = OnnxTensor.createTensor(env, feed,
                        new long[]{1, channels, segment});
                Map<String, OnnxTensor> feeds = new HashMap<String, OnnxTensor>();
                feeds.put(inputName, input);
                OrtSession.Result result = session.run(feeds);
                try {
                    FloatBuffer output = ((OnnxTensor) result.get(0)).getFloatBuffer();
                    for (int s = 0; s < 4; s++) {
                        for (int ch = 0; ch < channels; ch++) {
                            int base = ((s * channels) + ch) * (int) segment;
                            for (int i = 0; i < n; i++) {
                                stems[s][ch][start + i] += output.get(base + i) * window[i];
                            }
                        }
                    }
                    for (int i = 0; i < n; i++) weight[start + i] += window[i];
                } finally {
                    result.close();
                    input.close();
                }
            }
            for (int s = 0; s < 4; s++) {
                for (int ch = 0; ch < channels; ch++) {
                    for (int i = 0; i < total; i++) {
                        stems[s][ch][i] = weight[i] > 1e-8f ? stems[s][ch][i] / weight[i] : 0f;
                    }
                }
            }
            // `other` by subtraction, the reference's own step: the model's four rows sum
            // back to the mix to -32 dB, which would leave a whisper of the voice in a
            // window that is supposed to be backing only. Deriving the fourth makes the sum
            // exact, so "vocals removed" means the backing and nothing else.
            int otherRow = StemGesture.Stem.OTHER.row();
            int vocalsRow = StemGesture.Stem.VOCALS.row();
            for (int ch = 0; ch < channels; ch++) {
                for (int i = 0; i < total; i++) {
                    stems[otherRow][ch][i] = pcm[ch][i] - stems[0][ch][i] - stems[1][ch][i]
                            - stems[vocalsRow][ch][i];
                }
            }
            return stems;
        } finally {
            if (session != null) try { session.close(); } catch (Throwable ignored) { }
            try { options.close(); } catch (Throwable ignored) { }
        }
    }

    /** The triangular weight the reference uses: unity everywhere except a quarter of the
     *  segment at each end, so overlapping segments cross-fade. */
    private static float[] window(int segment) {
        int overlap = segment / 4;
        float[] win = new float[segment];
        java.util.Arrays.fill(win, 1f);
        for (int i = 0; i < overlap; i++) {
            float ramp = overlap <= 1 ? 1f : i / (float) (overlap - 1);
            win[i] = ramp;
            win[segment - overlap + i] = 1f - ramp;
        }
        return win;
    }

    private static void deleteQuietly(File file) {
        try {
            if (file != null && file.isFile()) file.delete();
        } catch (Throwable ignored) { }
    }

    // --- the container ------------------------------------------------------

    /**
     * AAC in an MP4, written with the platform's own encoder and muxer: no new dependency,
     * and the result is a file {@code MediaPlayer} opens like any other — which is the
     * requirement, because this file is played by the ordinary second player.
     *
     * <p>Samples go out as they are encoded, from a 16-bit interleaved view, with the
     * presentation time computed from the frame counter rather than from the source's
     * timestamps: the edit is assembled into one continuous timeline (the rendered head,
     * then the body of the same file), so the counter <em>is</em> the timeline.
     */
    static final class AacFileWriter {
        private final File out;
        private final int rate;
        private final int channels;
        private MediaCodec codec;
        private MediaMuxer muxer;
        private int track = -1;
        private boolean started;
        private boolean ended;
        private long framesWritten;

        /** What the last drain saw, so a write that has nothing to do is not a failure. */
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        AacFileWriter(File out, int rate, int channels) {
            this.out = out;
            this.rate = rate;
            this.channels = Math.max(1, Math.min(2, channels));
        }

        void start() throws Exception {
            MediaFormat format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            muxer = new MediaMuxer(out.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }

        /** Feeds one block of float PCM ({@code [channel][frame]}), draining as it goes so
         *  the encoder never runs out of output room. */
        void write(float[][] pcm) {
            int frames = pcm[0].length;
            int at = 0;
            while (at < frames) {
                int in = codec.dequeueInputBuffer(20_000);
                if (in < 0) {
                    drain(20_000);
                    continue;
                }
                ByteBuffer buffer = codec.getInputBuffer(in);
                buffer.clear();
                int capacity = Math.max(256, buffer.capacity() / (2 * channels));
                int count = Math.min(Math.min(capacity, ENCODE_CHUNK_FRAMES), frames - at);
                for (int i = 0; i < count; i++) {
                    for (int ch = 0; ch < channels; ch++) {
                        float v = pcm[ch][at + i];
                        buffer.putShort((short) Math.round(
                                Math.max(-1f, Math.min(1f, v)) * 32767f));
                    }
                }
                codec.queueInputBuffer(in, 0, count * 2 * channels,
                        framesWritten * 1_000_000L / rate, 0);
                framesWritten += count;
                at += count;
                drain(0);
            }
        }

        /** Signals end of stream and drains everything the encoder still holds. */
        void finish() {
            int in = codec.dequeueInputBuffer(100_000);
            if (in >= 0) {
                codec.queueInputBuffer(in, 0, 0, framesWritten * 1_000_000L / rate,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            long deadline = System.currentTimeMillis() + 20_000L;
            while (!ended && System.currentTimeMillis() < deadline) {
                drain(50_000);
            }
            release();
        }

        /**
         * Moves whatever the encoder has ready to the muxer, and starts the muxer on the
         * output format change (the AAC track format the muxer needs, including the
         * AudioSpecificConfig the config buffer carries).
         */
        private void drain(long timeoutUs) {
            while (true) {
                int index;
                try {
                    index = codec.dequeueOutputBuffer(info, timeoutUs);
                } catch (Throwable e) {
                    ended = true;
                    return;
                }
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return;
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (track < 0) {
                        track = muxer.addTrack(codec.getOutputFormat());
                        muxer.start();
                        started = true;
                    }
                    continue;
                }
                if (index < 0) return;
                try {
                    boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (info.size > 0 && !config && started) {
                        muxer.writeSampleData(track, codec.getOutputBuffer(index), info);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        ended = true;
                        return;
                    }
                } finally {
                    codec.releaseOutputBuffer(index, false);
                }
                timeoutUs = 0;                       // keep draining, never block again
            }
        }

        void abort() {
            try { if (started) muxer.stop(); } catch (Throwable ignored) { }
            release();
        }

        private void release() {
            try { if (muxer != null) muxer.release(); } catch (Throwable ignored) { }
            muxer = null;
            try { if (codec != null) codec.stop(); } catch (Throwable ignored) { }
            try { if (codec != null) codec.release(); } catch (Throwable ignored) { }
            codec = null;
        }
    }
}
