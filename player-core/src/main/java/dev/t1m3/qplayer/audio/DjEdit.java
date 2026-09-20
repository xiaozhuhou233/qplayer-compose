package dev.t1m3.qplayer.audio;

/**
 * The DJ edit made of ONE deck: the incoming track with its vocals taken out of the
 * blend window and handed back on a bar line.
 *
 * <p>This is the whole arithmetic of that edit, platform-free, so what a render will do
 * is checkable in plain Java — and so the two decisions that matter ("does the incoming
 * head sing at all", "where does the voice come back") are made from the same numbers a
 * test can call.
 *
 * <p>Why one deck instead of Folia's two-deck graph: the crossover still happens the way
 * it always did, as two players ramping against each other ({@link FadeCurve.DJ_BLEND}),
 * and the outgoing track is untouched (stripping it would mean switching its audio
 * source mid-playback, which is the mechanism behind the P0 and the replay incidents of
 * earlier rounds). So the only thing this edit changes is what the <em>incoming</em>
 * deck contributes: its backing, and then its vocals, from one file — which is what
 * lets the incoming player open a single source and never change it, before or after
 * the promotion.
 *
 * <p>The gesture does not reuse {@link StemGesture#incomingCurves}: those curves describe
 * a <em>takeover</em> (the incoming pad arrives early, its drums switch on the bar line,
 * its bass a bar later, its voice last). Here the opposite is wanted — the whole backing
 * from the very first sample of the blend, and only the voice held back — so the edit
 * has its own shape, built from the same primitives ({@link StemGesture#rise} over a
 * {@link StemGesture#CUT_SEC} ramp, and {@link StemGesture#barLines} for where it lands).
 */
public final class DjEdit {

    /** The ramp the vocals come back over. Folia's own vocal entrance width — long
     *  enough that the return is a lift rather than a click, short enough to read as
     *  the downbeat it ends on. */
    public static final double RETURN_RAMP_SEC = StemGesture.CUT_SEC;

    /** {@link #RETURN_RAMP_SEC} in the milliseconds the rest of the transition machinery
     *  works in. It is what the pitch rule subtracts from the removal window to find the
     *  beginning of the voice rather than the end of its ramp (see
     *  {@code MixNaturaliser.VOCAL_PITCH_MARGIN_MS} and the controller's pitch rule): the
     *  rule is about the voice <em>arriving</em>, which is when the gain leaves zero. */
    public static final long RETURN_RAMP_MS = Math.round(RETURN_RAMP_SEC * 1000d);

    /** A 25 ms frame of the vocal stem this far below full scale counts as "nothing
     *  singing there" (the same floor the round-7 stem measurements used). */
    public static final double SILENT_FRAME_DBFS = -50;

    /** The frame level that 90% of the removal window sits below. Reported as evidence
     *  (round 7 used the same shape of number); the verdict itself is
     *  {@link #SINGING_FRAME_SHARE}. */
    public static final double NO_VOCALS_P90_DBFS = -45;

    /** The share of the window's 25 ms frames that have to be above
     *  {@link #SILENT_FRAME_DBFS} for the window to count as singing.
     *
     * <p>Deliberately conservative at the low end and blind at the high end. A real
     * sung line — even a sparse rap or a sung intro — puts tens of percent of the
     * window's frames above the floor (round 7 measured 56% above it for a section that
     * had no lyrics at all), while a htdemucs vocal stem on an instrumental intro is
     * near-digital-silence and measures ~0%. A stray word or breath worth a few percent
     * of the window is treated as nothing singing: it would contribute almost nothing to
     * the blend, and the cost of being wrong is only that the incoming keeps its vocals —
     * which is exactly today's blend, not a broken one. */
    public static final double SINGING_FRAME_SHARE = 0.05;

    public static final double FRAME_SEC = 0.025;

    /** Beats to a bar for {@link StemGesture#barLines}. There is no structure pass on
     *  this platform (the platform has no such API), so four is the assumption every
     *  other bar-line use in this codebase makes as well. */
    public static final int BEATS_PER_BAR = 4;

    /**
     * When the vocals go back in: the removal window, and the ramp that ends at the end
     * of that window (or at the first bar line after it).
     *
     * <p>{@code returnStart} is inside the removal window by {@link #RETURN_RAMP_SEC},
     * which is the "gradually approach the vocals" half of the feature: the last half
     * second of the blend lifts the voice back to unity and lands on the bar line
     * rather than switching it on.
     */
    public static final class Plan {
        /** Where the vocals start coming back, seconds into the window. */
        public final double returnStartSec;
        /** Where they are back at unity — a bar line of the incoming track. */
        public final double returnEndSec;
        /** True when {@link #returnEndSec} is an actual bar line rather than the plain
         *  end of the removal window (no grid, or no measurement). */
        public final boolean onBarLine;

        Plan(double returnStartSec, double returnEndSec, boolean onBarLine) {
            this.returnStartSec = returnStartSec;
            this.returnEndSec = returnEndSec;
            this.onBarLine = onBarLine;
        }

        /** The vocal gain at {@code t} seconds into the window: 0 through the blend,
         *  then the ramp, then 1 for the rest of the track. */
        public double vocalGainAt(double t) {
            if (t <= returnStartSec) return 0;
            if (t >= returnEndSec) return 1;
            return StemGesture.rise(returnStartSec, returnEndSec, t);
        }

        /** One line for the log: the window and when the voice comes back. */
        public String describe() {
            return String.format(java.util.Locale.US,
                    "vocals out for %.2fs, back over %.2fs ending at %.2fs (%s)",
                    returnStartSec, returnEndSec - returnStartSec, returnEndSec,
                    onBarLine ? "a bar line of the incoming track" : "the end of the window");
        }
    }

    /**
     * The plan for one window.
     *
     * @param removalSec    how long the incoming track plays without its vocals — the
     *                      blend the user asked for (see the 过渡时长 setting)
     * @param returnEndSec  where the vocals are back at unity, in seconds into the
     *                      window: the first bar line at or after {@code removalSec}
     *                      when the incoming track's grid is known, otherwise
     *                      {@code removalSec} itself. Anything earlier than
     *                      {@code removalSec} is ignored (a return inside the blend is
     *                      not what this edit is for) — see {@link #firstBarAtOrAfter}.
     */
    public static Plan plan(double removalSec, double returnEndSec) {
        double removal = Math.max(0.05, removalSec);
        double end = returnEndSec;
        boolean onBar = true;
        if (!(end >= removal) || Double.isNaN(end)) {
            end = removal;
            onBar = false;
        }
        // The ramp must never start before the window does: a bar line one hop after
        // the blend on a very short grid would put returnStart at or below zero, and a
        // ramp from zero is the only thing that keeps the voice out of the blend.
        double start = Math.max(0, end - RETURN_RAMP_SEC);
        return new Plan(start, end, onBar);
    }

    /**
     * The first bar line at or after {@code atSec}, given the lines {@link
     * StemGesture#barLines} produced for the window, or NaN when there is none.
     *
     * <p>Bar lines come from a downbeat offset plus {@code beatsPerBar} beats, and never
     * from the beat grid's own phase: our {@link BeatProfile} carries the phase of the
     * beat grid ("every beat is firstBeatMs + k * period"), which is a different
     * question — a bar line spaced a bar apart from the beat phase is wrong three times
     * in four (see {@link StemGesture#barLines}). The offset itself is estimated from the
     * separated bass stem's low end ({@link StemGesture#downbeatOffsetSec}), which is the
     * documented extension this platform has instead of a structure pass.
     */
    public static double firstBarAtOrAfter(double[] barLines, double atSec) {
        if (barLines == null) return Double.NaN;
        double best = Double.NaN;
        for (double at : barLines) {
            if (at >= atSec && (Double.isNaN(best) || at < best)) best = at;
        }
        return best;
    }

    // --- does the incoming head sing at all? --------------------------------

    /** What the vocal stem measures over a window, and the verdict taken from it. */
    public static final class Presence {
        /** Frame levels, dBFS, of the two-channel vocal stem. */
        public final double medianDb;
        public final double p90Db;
        public final double peakDb;
        /** Share of frames at or below {@link #SILENT_FRAME_DBFS}. */
        public final double silentShare;
        /** The verdict: the window has real singing in it. */
        public final boolean sings;
        /** How many 25 ms frames the numbers came from. */
        public final int frames;

        Presence(double medianDb, double p90Db, double peakDb, double silentShare,
                 boolean sings, int frames) {
            this.medianDb = medianDb;
            this.p90Db = p90Db;
            this.peakDb = peakDb;
            this.silentShare = silentShare;
            this.sings = sings;
            this.frames = frames;
        }

        /** The evidence, for the log line that records the decision. */
        public String describe() {
            return String.format(java.util.Locale.US,
                    "the vocal stem of the first window measures median %.1f, p90 %.1f, "
                            + "peak %.1f dBFS (%d frames of %dms, %.0f%% at or below %.0f dBFS,"
                            + " %.0f%% above it)",
                    medianDb, p90Db, peakDb, frames, (int) (FRAME_SEC * 1000),
                    silentShare * 100.0, SILENT_FRAME_DBFS, (1 - silentShare) * 100.0);
        }
    }

    /**
     * Measures a separated vocal stem over the first {@code windowSec} seconds.
     *
     * <p>The verdict is the share of frames above {@link #SILENT_FRAME_DBFS} — "how much
     * of this window has voice in it" — rather than a mean or a peak: the mean averages a
     * mostly instrumental window's one word away, and the peak would give the whole
     * window to a single click. See {@link #SINGING_FRAME_SHARE} for why the bar is where
     * it is.
     */
    public static Presence presence(float[][] vocals, int sampleRate, double windowSec) {
        double[] levels = frameLevelsDb(vocals, sampleRate, windowSec);
        if (levels.length == 0) {
            // Nothing to measure: no claim of singing, and the caller skips the stem
            // path — which is the safe direction (today's blend, no edit).
            return new Presence(-240, -240, -240, 1, false, 0);
        }
        double[] sorted = levels.clone();
        java.util.Arrays.sort(sorted);
        double peak = 0;
        int limit = (int) Math.round(sampleRate * Math.max(0, windowSec));
        for (int ch = 0; ch < vocals.length; ch++) {
            int end = Math.min(limit, vocals[ch].length);
            for (int i = 0; i < end; i++) {
                double a = Math.abs(vocals[ch][i]);
                if (a > peak) peak = a;
            }
        }
        int silent = 0;
        for (double level : levels) if (level <= SILENT_FRAME_DBFS) silent++;
        double silentShare = silent / (double) levels.length;
        return new Presence(percentile(sorted, 0.50), percentile(sorted, 0.90), db(peak),
                silentShare, (1 - silentShare) >= SINGING_FRAME_SHARE, levels.length);
    }

    /** Per-frame level of a stereo signal, dBFS, over the first {@code windowSec}. */
    static double[] frameLevelsDb(float[][] pcm, int sampleRate, double windowSec) {
        if (pcm == null || pcm.length == 0 || pcm[0] == null) return new double[0];
        int frameLen = Math.max(1, (int) Math.round(sampleRate * FRAME_SEC));
        int total = Math.min(Math.min(pcm[0].length, pcm.length > 1 ? pcm[1].length : pcm[0].length),
                (int) Math.round(sampleRate * Math.max(0, windowSec)));
        int count = total / frameLen;
        double[] out = new double[count];
        for (int f = 0; f < count; f++) {
            double energy = 0;
            for (int ch = 0; ch < pcm.length; ch++) {
                float[] c = pcm[ch];
                for (int i = f * frameLen; i < (f + 1) * frameLen; i++) {
                    energy += c[i] * c[i];
                }
            }
            double rms = Math.sqrt(energy / (frameLen * (double) pcm.length));
            out[f] = db(rms);
        }
        return out;
    }

    static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) return -240;
        int index = Math.min(sorted.length - 1,
                (int) Math.round(p * (sorted.length - 1)));
        return sorted[Math.max(0, index)];
    }

    static double db(double ratio) {
        return ratio > 1e-12 ? 20 * Math.log10(ratio) : -240;
    }

    // --- the render ---------------------------------------------------------

    /**
     * Renders the head of the incoming track: every stem at unity except the vocals,
     * which follow {@link Plan#vocalGainAt}.
     *
     * <p>No bus limiter, deliberately. {@link StemBlendRenderer#softLimit} exists because
     * two decks sum at one bus and two masters can exceed full scale together; here there
     * is one deck and the edit has to be the master — a limiter would shape the very
     * signal the "then the master continues" half of this feature promises to leave
     * alone. What replaces it is the hard clamp any 16-bit encoder needs, counted so the
     * log can say whether it ever fired.
     *
     * @param stems     the separated window, {@code [stemRow][channel][sample]}
     *                  ({@link StemGesture.Stem#row}), at {@code sampleRate}
     * @param clipped   single-element out: how many samples the clamp had to touch
     * @return {@code [channel][sample]}, the same length as the input stems
     */
    public static float[][] renderHead(float[][][] stems, int sampleRate, double windowSec,
                                       Plan plan, int[] clipped) {
        int channels = stems[0].length;
        int length = stems[0][0].length;
        float[][] out = new float[channels][length];
        int clippedCount = 0;
        StemGesture.Stem[] all = StemGesture.Stem.ALL;
        for (int i = 0; i < length; i++) {
            double t = i / (double) sampleRate;
            double vocalGain = plan.vocalGainAt(t);
            for (int ch = 0; ch < channels; ch++) {
                double sum = 0;
                for (StemGesture.Stem stem : all) {
                    double gain = stem == StemGesture.Stem.VOCALS ? vocalGain : 1.0;
                    if (gain != 0) sum += gain * stems[stem.row()][ch][i];
                }
                if (sum > 1.0) { sum = 1.0; clippedCount++; }
                else if (sum < -1.0) { sum = -1.0; clippedCount++; }
                out[ch][i] = (float) sum;
            }
        }
        if (clipped != null && clipped.length > 0) clipped[0] = clippedCount;
        return out;
    }

    /** The vocal stem under the edit's own gain curve — what the voice actually is in
     *  the rendered head, and therefore what the log measures "after" against. */
    public static float[][] scaleVocals(float[][] vocals, int sampleRate, Plan plan,
                                        double windowSec) {
        int channels = vocals.length;
        int length = vocals[0].length;
        float[][] out = new float[channels][length];
        for (int i = 0; i < length; i++) {
            double gain = plan.vocalGainAt(i / (double) sampleRate);
            for (int ch = 0; ch < channels; ch++) out[ch][i] = (float) (gain * vocals[ch][i]);
        }
        return out;
    }

    /**
     * A slice of a signal, so a measurement can be taken of one part of a window — the
     * blend without its return ramp, the tail after it — rather than of the whole window
     * at once. Clamped to what the signal actually has; empty when the range is.
     */
    public static float[][] slice(float[][] pcm, int sampleRate, double fromSec, double toSec) {
        int channels = pcm.length;
        int from = Math.max(0, (int) Math.round(fromSec * sampleRate));
        int to = Math.max(from, (int) Math.round(toSec * sampleRate));
        float[][] out = new float[channels][];
        for (int ch = 0; ch < channels; ch++) {
            int end = Math.min(to, pcm[ch].length);
            out[ch] = end > from ? java.util.Arrays.copyOfRange(pcm[ch], from, end)
                    : new float[0];
        }
        return out;
    }

    private DjEdit() {}
}
