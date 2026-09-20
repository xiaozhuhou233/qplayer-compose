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
     * Starts one render, or returns false having done nothing. Called from the preload
     * lane, minutes before the boundary the edit is for; the implementation decides how
     * to run it (this process or another one) but must not perform it on the caller's
     * thread and must abandon it as soon as {@link Request#stillWanted} says so.
     *
     * <p>A file already at {@link Request#outPath} is not a reason to render: the caller
     * checks that first. A render that fails part way must delete its partial output —
     * the caller treats the presence of the file as "this is a finished edit".
     */
    boolean render(Request request);

    /** One render: what to read, what to write, and how long the vocals stay out. */
    final class Request {
        /** The audio file to read — the incoming track's own cache file, so a render
         *  costs no network. */
        public final String sourcePath;
        /** Where the finished edit goes. The caller owns the name (it is the key a
         *  boundary looks the edit up by). */
        public final String outPath;
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
        /** Polled during the render; false means the queue moved on and the work is to
         *  be thrown away. Never null. */
        public final BooleanSupplier stillWanted;

        public Request(String sourcePath, String outPath, Track track, long removalMs,
                       double beatPeriodMs, double beatPhaseMs, BooleanSupplier stillWanted) {
            this.sourcePath = sourcePath;
            this.outPath = outPath;
            this.track = track;
            this.removalMs = removalMs;
            this.beatPeriodMs = beatPeriodMs;
            this.beatPhaseMs = beatPhaseMs;
            this.stillWanted = stillWanted != null ? stillWanted : () -> true;
        }

        public String title() {
            return track != null && track.title != null ? track.title : "?";
        }
    }
}
