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
import dev.t1m3.qplayer.audio.StemBridge;
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

    /** How far past the removal window the separated head reaches: the vocals are at zero
     *  for the whole blend and return on the first bar line at or after
     *  {@code blend end + }{@link DjEdit#VOCAL_RETURN_MARGIN_SEC} — so the window has to
     *  cover the margin plus a whole bar of the incoming track at its own tempo, plus a
     *  second of slack. Capped, because a very slow track must not turn a 15 s blend into a
     *  40 s separation. */
    private static final long RETURN_SPAN_MAX_MS = 10_000L;

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
     *
     * <p>The lane runs at the lowest priority the process has and the caller sits behind
     * the controller's startup gate, so this hash — the one piece of startup work that is
     * pure CPU over a large file — never lands inside the first frames. How long it took
     * is in the line below, so a slow device can be told apart from a cold disk.
     */
    private StemModel.Candidate model() {
        if (modelChecked) return model;
        synchronized (modelLock) {
            if (modelChecked) return model;
            for (StemModel.Candidate candidate : StemModel.candidates()) {
                File file = new File(modelsDir, candidate.fileName);
                if (!file.isFile()) continue;
                long bytes = file.length();
                long hashStartedAt = System.nanoTime();
                String digest = StemModel.sha256(file);
                long hashMs = (System.nanoTime() - hashStartedAt) / 1_000_000L;
                StemModel.Candidate accepted =
                        StemModel.recognise(candidate.fileName, bytes, digest);
                if (accepted != null) {
                    model = accepted;
                    modelChecked = true;
                    Logger.info("transition: stem DJ edits are ON — {} verified in {} ({} bytes,"
                                    + " sha256 {} hashed in {}ms); the separation runs on {} CPU"
                                    + " threads with the arena allocator off, off the playback path",
                            accepted.fileName, modelsDir.getAbsolutePath(), bytes,
                            accepted.sha256, hashMs, StemModel.INTRA_OP_THREADS);
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
    public StemEditRenderer.Result render(Request request) {
        StemModel.Candidate weights = model();
        if (weights == null) {
            logInertReason();
            return null;
        }
        // The finished file's name is built inside run(): the two numbers the boundary needs are
        // what this render learns (see StemEditRenderer.Result). `out` is the path a render
        // without a bridge would take, and the one every failure path cleans up.
        File out = new File(request.outBasePath + ".m4a");
        File parent = out.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        long startedAt = System.currentTimeMillis();
        try {
            StemEditRenderer.Result result = run(weights, request, out, startedAt);
            if (result != null) return result;
            deleteQuietly(out);
            return null;
        } catch (Throwable e) {
            // Every failure is this one: the boundary blends the plain stream (and, with no
            // bridge, plays the round-12 edit if one exists). Logged with the fact that it
            // happened at all, because "the render failed" and "the render was never asked for"
            // are different things to know about this feature.
            Logger.warn("transition: DJ edit for {} failed ({}); the boundary blends the plain"
                    + " stream", request.title(), e.toString());
            deleteQuietly(out);
            return null;
        }
    }

    private StemEditRenderer.Result run(StemModel.Candidate weights, Request request, File out,
                                        long startedAt) throws Exception {
        Format format = probe(request.sourcePath);
        if (format == null) {
            Logger.warn("transition: DJ edit for {}: no audio track in {}", request.title(),
                    request.sourcePath);
            return null;
        }
        long removalMs = request.removalMs;
        long spanMs = returnSpanMs(request.beatPeriodMs);
        long headMs = removalMs + spanMs;
        Logger.info("transition: rendering the DJ edit for {} — vocals at exactly zero for the"
                        + " first {}ms (the whole blend), back on a bar line inside the next {}ms"
                        + " (never earlier than {}ms after the blend ends); source {}Hz, window"
                        + " {}ms from the file's own start (so the file's timeline is the track's"
                        + " timeline)",
                request.title(), removalMs, spanMs, DjEdit.VOCAL_RETURN_MARGIN_MS, format.rate,
                headMs);

        // 1. The head, decoded from the file's start. Starting at zero is what keeps every
        //    offset the transition machinery already computes — the content start, the
        //    beat entry, the ramp's handoff, the published position — meaning the same
        //    thing on this file as on the stream it replaces.
        long headFrames = headMs * format.rate / 1000L;
        final float[][] head = new float[2][(int) headFrames];
        long[] decoded = new long[1];
        boolean[] cancelled = new boolean[1];
        boolean completed = decode(request.sourcePath, 2, 0L, head[0].length, request.stillWanted,
                cancelled, (pcm, frames) -> {
                    for (int ch = 0; ch < head.length && ch < pcm.length; ch++) {
                        System.arraycopy(pcm[ch], 0, head[ch], (int) decoded[0], frames);
                    }
                    decoded[0] += frames;
                });
        if (cancelled[0]) {
            Logger.info("transition: DJ edit for {} cancelled while decoding the head (the queue"
                    + " moved on)", request.title());
            return null;
        }
        if (!completed || decoded[0] < format.rate / 4L) {
            Logger.warn("transition: DJ edit for {}: the head decoded to only {} frames; nothing"
                    + " to render", request.title(), decoded[0]);
            return null;
        }
        final float[][] headWindow = trim(head, (int) decoded[0]);

        // 2. The separation, at the rate and on the grid the model was trained for.
        float[][] headForModel = resample(headWindow, format.rate, StemModel.MODEL_RATE);
        long separateStart = System.currentTimeMillis();
        float[][][] stems = separate(weights, headForModel, request.stillWanted);
        if (stems == null) {
            Logger.info("transition: DJ edit for {} cancelled during the separation",
                    request.title());
            return null;
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
            return null;
        }
        Logger.info("transition: DJ edit for {}: its first {}ms does sing — {}", request.title(),
                removalMs, presence.describe());

        // 4. The incoming's grid and where the voice comes back: the first bar line at or after
        //    the removal window. The offset is estimated from the separated BASS stem's low end
        //    — the documented extension this platform has instead of a structure pass — and
        //    never from the beat grid's phase, which is a different question and wrong three
        //    times in four.
        double windowSec = headForModel[0].length / (double) StemModel.MODEL_RATE;
        double beatSecIn = request.beatPeriodMs > 0 ? request.beatPeriodMs / 1000.0 : 0d;
        double[] inBars = barLinesOf(stems, StemModel.MODEL_RATE, beatSecIn,
                request.beatPhaseMs / 1000.0, 0d, windowSec, request.title());
        DjEdit.Plan plan = editPlan(inBars, request, windowSec, removalSec, headMs);

        // 5. The bridge: the outgoing track's low end, carried forward inside this same file.
        //    Everything about it is measured before it is written — see StemBridge, and the
        //    acceptance block below.
        StemBridge.Plan bridge = null;
        float[][] layer = null;
        StemBridge.Report bridgeReport = null;
        float[][] bridgeSource = null;
        float[][] outgoingVocals = null;
        long bridgeStartMs = -1L;
        if (request.canBridge()) {
            bridge = planBridge(request, inBars, beatSecIn, removalSec, windowSec);
            if (bridge != null && bridge.fits) {
                long tailMs = bridgeTailWindowMs(request.outgoingBeatPeriodMs);
                double durationMs = probeDurationMs(request.outgoingSourcePath);
                if (durationMs > 0d) {
                    long fromMs = Math.max(0L, Math.round(durationMs - tailMs));
                    float[][][] tail = separateTail(weights, request, format, fromMs,
                            Math.round(tailMs), cancelled);
                    if (tail != null && !cancelled[0]) {
                        double tailSec = tail[0][0].length / (double) StemModel.MODEL_RATE;
                        double outBeatSec = request.outgoingBeatPeriodMs > 0
                                ? request.outgoingBeatPeriodMs / 1000.0 : 0d;
                        double[] outBars = barLinesOf(tail, StemModel.MODEL_RATE, outBeatSec,
                                request.outgoingBeatPhaseMs / 1000.0, fromMs / 1000.0, tailSec,
                                "the outgoing tail");
                        double barSec = outBeatSec > 0 ? outBeatSec * StemBridge.BEATS_PER_BAR : 0d;
                        double fromSec = StemBridge.sourceFromSec(outBars, barSec);
                        layer = StemBridge.layer(
                                tail[StemGesture.Stem.BASS.row()], StemModel.MODEL_RATE, bridge,
                                request.speed, fromSec);
                        if (layer != null) {
                            bridgeStartMs = Math.round(bridge.startSec * 1000d);
                            // The material the layer was taken from, at the same length: the
                            // comb test and the level check are against a single copy of it.
                            bridgeSource = copyOf(tail[StemGesture.Stem.BASS.row()],
                                    (int) Math.round(fromSec * StemModel.MODEL_RATE),
                                    layer[0].length);
                            outgoingVocals = slice(tail[StemGesture.Stem.VOCALS.row()],
                                    (int) Math.round(fromSec * StemModel.MODEL_RATE),
                                    layer[0].length);
                            Logger.info("transition: DJ edit for {}: {} — carried from {}s of the"
                                            + " outgoing tail ({} bars on its own grid), played"
                                            + " back at x{}; the low end hands over at the bridge's"
                                            + " own start, so nothing the outgoing deck is still"
                                            + " playing is duplicated",
                                    request.title(), bridge.describe(),
                                    String.format(java.util.Locale.US, "%.3f", fromSec),
                                    StemBridge.BARS + StemBridge.SOURCE_SLACK_BARS,
                                    speedText(request.speed));
                        } else {
                            Logger.info("transition: DJ edit for {}: {} — no layer was built",
                                    request.title(), bridge.describe());
                        }
                    } else {
                        Logger.info("transition: DJ edit for {} cancelled while separating the"
                                + " outgoing tail", request.title());
                        return null;
                    }
                } else {
                    Logger.info("transition: DJ edit for {}: the outgoing track's length could not"
                            + " be read, so no tail window could be placed; no bridge",
                            request.title());
                }
            } else if (bridge != null) {
                Logger.info("transition: DJ edit for {}: {} — the boundary blends the outgoing's"
                        + " low end away the ordinary way", request.title(), bridge.describe());
            }
        }

        // 6. The render.
        int[] clipped = new int[1];
        float[][] edited = DjEdit.renderHead(stems, StemModel.MODEL_RATE, windowSec, plan, clipped,
                layer, layer == null ? 0 : (int) Math.round(bridge.startSec * StemModel.MODEL_RATE));
        // The acceptance numbers, measured on the material that went into the file: the carried
        // layer's level against its source, the two contributions at the designed times, the
        // vocals, the comb test, and the pulse. See StemBridge.Report.
        if (layer != null) {
            float[][] incomingVocals = vocalsUnderEdit(stems, plan, layer[0].length);
            bridgeReport = StemBridge.measure(layer, bridgeSource, edited, incomingVocals,
                    outgoingVocals, StemModel.MODEL_RATE, bridge, request.speed, beatSecIn,
                    request.outgoingBeatPeriodMs / 1000.0);
            Logger.info("transition: DJ edit for {} — {}", request.title(),
                    bridgeReport.describe());
            if (!bridgeReport.acceptable) {
                // ⚠️ The bridge is kept only if it passes its own acceptance measurement, and this
                // is the whole reason that measurement exists: the material it carries is chosen
                // by the outgoing track's own ending, and an ending whose last bars have no bass
                // in them (a fade, a spoken outro, a break) would put two bars of near-silence
                // under the incoming track's head — a hole in the mix, which is worse than the
                // fade the bridge was built to replace. So the layer is dropped and the edit is
                // the round-12 one: exactly the behaviour of a build that has no bridge in it,
                // with the measurement as the stated reason.
                Logger.warn("transition: DJ edit for {}: the bridge did not pass its own"
                                + " measurement, so it is NOT in this edit — the boundary plays"
                                + " the round-12 edit (vocals out, back on a bar line) and hands"
                                + " its low end over the way it always did. The measurement: {}",
                        request.title(), bridgeReport.describe());
                layer = null;
                bridgeReport = null;
                bridgeStartMs = -1L;
            }
        }
        float[][] editedForSource = resample(edited, StemModel.MODEL_RATE, format.rate);
        Logger.info("transition: DJ edit for {}: {}; {} of {} samples clamped; separation took"
                        + " {}ms",
                request.title(), plan.describe(), clipped[0],
                edited[0].length * edited.length, separateMs);

        // 7. One file: the edited head, then the rest of the track decoded straight through.
        //    This is what makes the incoming deck able to open a single source and keep it —
        //    before the blend, through the promotion, and to the end of the track.
        long encodeStart = System.currentTimeMillis();
        // The name carries the two times the boundary cannot recompute. The renderer owns the
        // name for that reason (StemEditRenderer.Result): the boundary looks a finished edit up
        // by key and reads the numbers back out of it.
        File named = new File(request.outBasePath
                + StemEditRenderer.Result.suffixOf(bridgeStartMs, Math.round(plan.returnEndSec * 1000d))
                + ".m4a");
        AacFileWriter writer = new AacFileWriter(named, format.rate, 2);
        boolean wrote = false;
        try {
            writer.start();
            writer.write(editedForSource);
            if (!decodeBody(request.sourcePath, editedForSource[0].length, writer,
                    request.stillWanted)) {
                Logger.info("transition: DJ edit for {} cancelled while writing the body (the"
                        + " queue moved on)", request.title());
                return null;
            }
            writer.finish();
            wrote = true;
        } finally {
            if (!wrote) {
                writer.abort();
                deleteQuietly(named);
                if (!out.equals(named)) deleteQuietly(out);
            }
        }
        long bytes = named.length();
        if (bytes < MIN_EDIT_BYTES) {
            Logger.warn("transition: DJ edit for {} came out at {} bytes, which is not a"
                    + " recording; dropped it", request.title(), bytes);
            deleteQuietly(named);
            return null;
        }
        Logger.info("transition: DJ edit for {} written: {} ({}KB; {}ms of encode, {}ms of render"
                        + " in all) — the boundary plays this file on the incoming deck when it"
                        + " gets there",
                request.title(), named.getAbsolutePath(), bytes / 1024L,
                System.currentTimeMillis() - encodeStart,
                System.currentTimeMillis() - startedAt);
        return new StemEditRenderer.Result(named.getAbsolutePath(), bridgeStartMs,
                Math.round(plan.returnEndSec * 1000d), bridgeReport != null ? bridgeReport.describe() : "");
    }

    /** The incoming's vocal stem under the edit's own gain curve, over the part of the window a
     *  bridge covers: what the bridge report's "no vocals" clause measures. */
    private static float[][] vocalsUnderEdit(float[][][] stems, DjEdit.Plan plan, int frames) {
        float[][] vocals = stems[StemGesture.Stem.VOCALS.row()];
        int limit = Math.min(frames, vocals[0].length);
        float[][] out = new float[vocals.length][limit];
        for (int i = 0; i < limit; i++) {
            double gain = plan.vocalGainAt(i / (double) StemModel.MODEL_RATE);
            for (int ch = 0; ch < vocals.length; ch++) {
                out[ch][i] = (float) (gain * vocals[ch][i]);
            }
        }
        return out;
    }

    /** A slice of one signal starting at {@code from}, {@code frames} long (silence past its
     *  end): the reference the carried layer is compared against. */
    private static float[][] copyOf(float[][] pcm, int from, int frames) {
        float[][] out = new float[pcm.length][frames];
        for (int ch = 0; ch < pcm.length; ch++) {
            int copy = Math.max(0, Math.min(frames, pcm[ch].length - from));
            if (copy > 0) System.arraycopy(pcm[ch], from, out[ch], 0, copy);
        }
        return out;
    }

    /** How much of the outgoing tail has to be separated for the bridge: its carried bars plus
     *  one of slack (so the take can be whole bars) and a second, capped the way the head's own
     *  return span is — a very slow track must not turn a render into a minute-long separation. */
    static long bridgeTailWindowMs(double beatPeriodMs) {
        if (!(beatPeriodMs > 0)) return 2_500L;
        long bars = Math.round(beatPeriodMs * StemBridge.BEATS_PER_BAR)
                * (StemBridge.BARS + StemBridge.SOURCE_SLACK_BARS + 1);
        return Math.min(BRIDGE_TAIL_MAX_MS, bars + 1_000L);
    }

    private static final long BRIDGE_TAIL_MAX_MS = 8_500L;

    /** The bridge's placement, from the outgoing's and the incoming's own bar grids — never the
     *  beat grid's phase (see {@link StemGesture#barLines}). */
    private StemBridge.Plan planBridge(Request request, double[] inBars, double beatSecIn,
                                       double removalSec, double windowSec) {
        double barSec = beatSecIn > 0 ? beatSecIn * StemBridge.BEATS_PER_BAR : 0d;
        StemBridge.Plan plan = StemBridge.plan(inBars, barSec, removalSec, windowSec);
        Logger.info("transition: DJ edit for {}: bridge plan — {}, from the incoming track's own"
                        + " {}-BPM grid and downbeat offset",
                request.title(), plan.describe(), Math.round(60d / Math.max(1e-6, beatSecIn)));
        return plan;
    }

    /** One track's bar lines over a window, from its separated bass stem's low end: the
     *  documented extension this platform has instead of a structure pass. Empty when the low
     *  end gave no offset — then there is no line for anything to land on and the callers say so
     *  rather than guessing one. */
    private static double[] barLinesOf(float[][][] stems, int rate, double beatSec,
                                       double beatPhaseSec, double fromSec, double windowSec,
                                       String who) {
        if (!(beatSec > 0)) {
            Logger.info("transition: DJ edit: no beat grid for {}, so there are no bar lines and"
                    + " nothing lands on one", who);
            return new double[0];
        }
        float[] lowBand = StemGesture.envelopeOf(
                new float[][]{stems[StemGesture.Stem.BASS.row()][0],
                        stems[StemGesture.Stem.BASS.row()][1]},
                rate, StemGesture.CELL_SEC);
        Double downbeat = StemGesture.downbeatOffsetSec(lowBand, fromSec, StemGesture.CELL_SEC,
                beatPhaseSec, beatSec, DjEdit.BEATS_PER_BAR);
        if (downbeat == null || Double.isNaN(downbeat)) {
            Logger.info("transition: DJ edit: the low end of {} gave no downbeat offset, so there"
                    + " is no bar grid for it", who);
            return new double[0];
        }
        double bpm = 60d / beatSec;
        double[] bars = StemGesture.barLines(bpm, downbeat, DjEdit.BEATS_PER_BAR, fromSec,
                windowSec);
        Logger.info("transition: DJ edit: {}BPM for {}, {} bar lines inside the {}ms window",
                String.format(java.util.Locale.US, "%.1f", bpm), who, bars.length,
                Math.round(windowSec * 1000d));
        return bars;
    }

    /** The plan for the window: the return ends on the first bar line at or after the removal
     *  window PLUS the margin the user's rule needs ({@link DjEdit#VOCAL_RETURN_MARGIN_SEC}), and
     *  without a grid it ends at that instant itself.
     *
     *  <p>⚠️ Round 17: the search starts at {@code removal + margin} rather than at the removal
     *  window. The margin is what keeps the voice at exactly zero for the whole blend — the old
     *  search could (and did, whenever the blend length was a whole number of bars) land the
     *  return exactly ON the blend's end, which put the last half second of the lift inside the
     *  blend. {@link DjEdit#plan} enforces the same floor, so a bar line the caller hands in
     *  earlier than that is ignored rather than honoured. */
    private DjEdit.Plan editPlan(double[] inBars, Request request, double windowSec,
                                 double removalSec, long headMs) {
        double earliest = removalSec + DjEdit.VOCAL_RETURN_MARGIN_SEC;
        if (inBars == null || inBars.length == 0) {
            Logger.info("transition: DJ edit for {}: no bar grid for this track, so the vocals"
                            + " return {}ms after the {}ms blend ends, at the margin's own"
                            + " instant (no bar line to land on)",
                    request.title(), DjEdit.VOCAL_RETURN_MARGIN_MS, request.removalMs);
            return DjEdit.plan(removalSec, Double.NaN);
        }
        double at = DjEdit.firstBarAtOrAfter(inBars, earliest);
        Logger.info("transition: DJ edit for {}: vocals return {}",
                request.title(), Double.isNaN(at)
                        ? "at the end of the window plus the " + DjEdit.VOCAL_RETURN_MARGIN_MS
                                + "ms margin (no bar line in it)"
                        : "at " + String.format(java.util.Locale.US, "%.3f", at) + "s (a bar line"
                                + " at or after the blend's end plus "
                                + DjEdit.VOCAL_RETURN_MARGIN_MS + "ms)");
        return DjEdit.plan(removalSec, at);
    }

    /** A track's file length in ms, or -1 when the container does not say. */
    private static double probeDurationMs(String path) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(path);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                if (f.containsKey(MediaFormat.KEY_DURATION)) {
                    return f.getLong(MediaFormat.KEY_DURATION) / 1000d;
                }
            }
        } catch (Throwable e) {
            Logger.warn("transition: DJ edit: cannot read the length of {} ({})", path,
                    e.toString());
        } finally {
            extractor.release();
        }
        return -1d;
    }

    private static String speedText(double speed) {
        return Math.abs(speed - 1d) < 1e-6d
                ? "1 (the pair's grids already hold, so the carry is sample-exact)"
                : String.format(java.util.Locale.US, "%.4f", speed);
    }

    /**
     * The outgoing track's tail, separated: the other half of the bridge. Decoded from an
     * offset (the extractor's own sync seek) and run through the same model and the same proven
     * configuration as the incoming's head, so the two windows are separated by the same
     * instrument — which is the only thing that makes "this bar of A against this bar of B" a
     * meaningful statement.
     */
    private float[][][] separateTail(StemModel.Candidate weights, Request request, Format format,
                                     long fromMs, long windowMs, boolean[] cancelled)
            throws Exception {
        long frames = windowMs * format.rate / 1000L;
        final float[][] tail = new float[2][(int) frames];
        long[] decoded = new long[1];
        boolean[] inner = new boolean[1];
        long started = System.currentTimeMillis();
        boolean completed = decode(request.outgoingSourcePath, 2, fromMs, tail[0].length,
                request.stillWanted, inner, (pcm, block) -> {
                    for (int ch = 0; ch < tail.length && ch < pcm.length; ch++) {
                        System.arraycopy(pcm[ch], 0, tail[ch], (int) decoded[0], block);
                    }
                    decoded[0] += block;
                });
        if (inner[0]) {
            cancelled[0] = true;
            return null;
        }
        if (!completed || decoded[0] < format.rate / 4L) {
            Logger.warn("transition: DJ edit: the outgoing tail decoded to only {} frames from"
                    + " {}ms; no bridge", decoded[0], fromMs);
            return null;
        }
        float[][] window = trim(tail, (int) decoded[0]);
        float[][] forModel = resample(window, format.rate, StemModel.MODEL_RATE);
        float[][][] stems = separate(weights, forModel, request.stillWanted);
        if (stems == null) return null;
        Logger.info("transition: DJ edit: the outgoing tail ({}ms from {}ms of its file)"
                        + " separated in {}ms", Math.round(decoded[0] * 1000L / format.rate),
                fromMs, System.currentTimeMillis() - started);
        return stems;
    }

    /** The separated head's length in ms, for the log line above: the removal window plus
     *  one bar of this track and a second of slack, capped. */
    private static long headMsOf(StemEditRenderer.Request request) {
        return request.removalMs + returnSpanMs(request.beatPeriodMs);
    }

    /** How far past the removal window the head has to reach for the return to land on a bar
     *  line: the round-17 margin (the voice stays at zero through the blend and starts coming
     *  back after it), one bar of the incoming track at its own tempo, plus a second of slack. */
    private static long returnSpanMs(double beatPeriodMs) {
        if (!(beatPeriodMs > 0)) return DjEdit.VOCAL_RETURN_MARGIN_MS + 1_500L;
        long bar = Math.round(beatPeriodMs * DjEdit.BEATS_PER_BAR);
        return Math.min(RETURN_SPAN_MAX_MS, DjEdit.VOCAL_RETURN_MARGIN_MS + bar + 1_000L);
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
        boolean completed = decode(path, 2, 0L, -1, wanted, cancelled, (pcm, frames) -> {
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
     * Decodes a source file, handing blocks to {@code sink} up to {@code maxFrames} (or to the
     * end of the file when negative), starting at {@code fromMs}.
     *
     * <p>{@code fromMs} is for the outgoing track's tail: the bridge's material is that track's
     * own ending, so the separation starts near the end of its file. The seek is the extractor's
     * own ({@code SEEK_TO_CLOSEST_SYNC}), so the window may start up to a GOP early — which
     * costs nothing, because the window is longer than what is taken from it and the take is
     * anchored on the separated material's own bar grid.
     *
     * @return true when the file was decoded to its end (or to the limit), false when it
     *         was cut short; {@code cancelled} says whether that was the caller's doing.
     */
    private boolean decode(String path, int channels, long fromMs, long maxFrames,
                           BooleanSupplier wanted, boolean[] cancelled, Frames sink)
            throws Exception {
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
            if (fromMs > 0L) extractor.seekTo(fromMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
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
