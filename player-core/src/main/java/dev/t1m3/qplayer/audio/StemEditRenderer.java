package dev.t1m3.qplayer.audio;

import dev.t1m3.qplayer.model.Track;

import java.util.function.BooleanSupplier;

/**
 * The host's stem renderer: the thing that can turn one track's audio file into a
 * {@link DjEdit} file, i.e. the same track with its vocals taken out of the first
 * {@code removalMs} and given back on a bar line.
 *
 * <p>A seam, like {@link SilenceProfiler} and {@link BeatProfiler}: the controller knows
 * <em>when</em> an edit is worth making and <em>where</em> one already exists, and nothing
 * about decoders, ONNX runs or muxers. Without a renderer installed, every path here is
 * exactly today's — the split of "the model is present and verified" that decides whether
 * the feature exists at all lives in the host, because only the host knows where its own
 * private storage is ({@link StemModel} describes what such a file has to be).
 *
 * <p><b>Nothing here may block.</b> {@link #render} starts work and returns; a missing,
 * late or failed render is the normal case and has to be indistinguishable from a build
 * that has no stem code in it at all: the boundary simply blends the way it always did.
 */
public interface StemEditRenderer {

    /** Whether this host can render at all — a model file that
     *  {@link StemModel#recognise} accepted is present. Asked once per session for the
     *  log line; a false answer means the stem path is never entered. */
    boolean available();

    /**
     * Starts one render and reports what it produced, or {@code null} when nothing was written.
     *
     * <p>Called from the preload lane, minutes before the boundary the edit is for; the
     * implementation decides how to run it (this process or another one) but must not perform it
     * on the caller's thread and must abandon it as soon as {@link Request#stillWanted} says so.
     *
     * <p>A file already at the request's own name is not a reason to render: the caller checks
     * that first. A render that fails part way must delete its partial output — the caller
     * treats the presence of a file as "this is a finished edit".
     *
     * <p>⚠️ The path of the finished edit is the renderer's to choose, and the numbers it baked
     * into the audio are part of the name (see {@link Result}). That is deliberate: the boundary
     * that plays the edit has to place its low-end hand-over at the bridge's own start and its
     * pitch rule's deadline before the outgoing track's voice comes back, and both of those are
     * things only the render knows — the stems are gone by the time the boundary runs.
     */
    Result render(Request request);

    /** What one render produced. The renderer names the file, and the name carries the two
     *  times the boundary needs; {@link #path} and the parsed fields are the same facts. */
    final class Result {
        /** The finished edit. */
        public final String path;
        /** Where the bridge starts in the incoming track's own file, ms, or -1 for no bridge. */
        public final long bridgeStartMs;
        /** Where the incoming track's vocals are back at unity, ms into its own file, or -1
         *  when the render could not say. */
        public final long vocalReturnEndMs;
        /** The renderer's own account of what it did, for the caller's log line. */
        public final String note;

        public Result(String path, long bridgeStartMs, long vocalReturnEndMs, String note) {
            this.path = path;
            this.bridgeStartMs = bridgeStartMs;
            this.vocalReturnEndMs = vocalReturnEndMs;
            this.note = note == null ? "" : note;
        }

        /** The suffix the file name carries — the same facts as the fields above, in the form
         *  a later process can read them back from a directory listing. */
        public static String suffixOf(long bridgeStartMs, long vocalReturnEndMs) {
            StringBuilder sb = new StringBuilder();
            if (bridgeStartMs >= 0L) sb.append("-b").append(bridgeStartMs);
            if (vocalReturnEndMs >= 0L) sb.append("-v").append(vocalReturnEndMs);
            return sb.toString();
        }

        /** An edit that exists but whose name carried no numbers (a plain round-12 edit, or one
         *  found by key alone): no bridge, and the vocal return is unknown. */
        public static Result unknown(String path) {
            return new Result(path, -1L, -1L, "no bridge times in the file name");
        }
    }

    /** One render: what to read, what to write, and how long the vocals stay out. */
    final class Request {
        /** The audio file to read — the incoming track's own cache file, so a render
         *  costs no network. */
        public final String sourcePath;
        /** Where the finished edit goes, WITHOUT its suffix or extension: the renderer appends
         *  both, because the suffix is what it learns during the render (see {@link Result}). */
        public final String outBasePath;
        /** The incoming track, for the log lines. */
        public final Track track;
        /** How long the incoming track plays with its vocals removed: the user's own
         *  blend length (the 过渡时长 setting), ms. */
        public final long removalMs;
        /** The incoming track's beat period, ms, or 0 when no grid was measured — then
         *  there are no bar lines and the vocals come back at the end of the window. */
        public final double beatPeriodMs;
        /** The incoming track's beat-grid phase in the file, ms (only ever used as the
         *  candidate set a downbeat offset is estimated from, never as the bar line
         *  itself). */
        public final double beatPhaseMs;
        /**
         * The OUTGOING track's own audio file, or null — the bridge's other half.
         *
         * <p>{@code StemBridge} carries the outgoing track's low end forward inside the
         * incoming's file, and the only way to have it is to separate the outgoing track's tail
         * too. Null (the track was streamed rather than cached, or there is no outgoing track at
         * all) means no bridge: the edit is the round-12 edit, the vocals come back on a bar
         * line, and the boundary blends exactly as it did before this existed.
         */
        public final String outgoingSourcePath;
        /** The outgoing track's beat period, ms (0 = no grid, and then no bar lines for the
         *  carried passage either). */
        public final double outgoingBeatPeriodMs;
        /** The outgoing track's beat-grid phase in the file, ms. */
        public final double outgoingBeatPhaseMs;
        /**
         * The ratio the incoming deck will play at — {@code MixNaturaliser.speed()} for this
         * pair, i.e. what the tempo lock pulls the incoming track by.
         *
         * <p>Needed because everything inside the edit is heard at that ratio, so the carried
         * bass has to be stretched by it to be heard at the outgoing track's own tempo. It is
         * measured at request time (both profiles are cached by then) and the boundary logs what
         * it was; a pair whose grids hold has 1.0 and the carry is sample-exact.
         */
        public final double speed;
        /** Polled during the render; false means the queue moved on and the work is
         *  to be thrown away. Never null. */
        public final BooleanSupplier stillWanted;

        public Request(String sourcePath, String outBasePath, Track track, long removalMs,
                       double beatPeriodMs, double beatPhaseMs, BooleanSupplier stillWanted) {
            this(sourcePath, outBasePath, track, removalMs, beatPeriodMs, beatPhaseMs,
                    null, 0d, 0d, 1d, stillWanted);
        }

        public Request(String sourcePath, String outBasePath, Track track, long removalMs,
                       double beatPeriodMs, double beatPhaseMs, String outgoingSourcePath,
                       double outgoingBeatPeriodMs, double outgoingBeatPhaseMs, double speed,
                       BooleanSupplier stillWanted) {
            this.sourcePath = sourcePath;
            this.outBasePath = outBasePath;
            this.track = track;
            this.removalMs = removalMs;
            this.beatPeriodMs = beatPeriodMs;
            this.beatPhaseMs = beatPhaseMs;
            this.outgoingSourcePath = outgoingSourcePath;
            this.outgoingBeatPeriodMs = outgoingBeatPeriodMs;
            this.outgoingBeatPhaseMs = outgoingBeatPhaseMs;
            this.speed = speed > 0d ? speed : 1d;
            this.stillWanted = stillWanted != null ? stillWanted : () -> true;
        }

        /** Whether this request can carry a bridge at all (the outgoing side is available). */
        public boolean canBridge() {
            return outgoingSourcePath != null && !outgoingSourcePath.isEmpty();
        }

        public String title() {
            return track != null && track.title != null ? track.title : "?";
        }
    }
}
