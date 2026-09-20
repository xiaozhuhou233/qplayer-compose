package dev.t1m3.qplayer.audio;

/**
 * The gain shape an overlapping transition ramps along. Two tracks summed at
 * full gain are not a crossfade — they are just loud; the curve is what turns
 * one level going down and another going up into a seam that does not pump.
 *
 * <p>{@code t} is the progress of the overlap, 0 at its start and 1 at the swap
 * point. The two gains are applied to the outgoing and the incoming player
 * respectively; they are not required to sum to 1 (that is the point).
 */
public enum FadeCurve {

    /**
     * Straight lines: {@code 1-t} and {@code t}. What P1 shipped. Two
     * uncorrelated tracks each at half gain produce about half the power of one
     * at full gain, so the middle of the overlap dips — for equal-power content
     * that is 3 dB, which is the first thing to listen for when a long crossfade
     * "drops out" in the middle.
     */
    LINEAR("线性") {
        @Override public float outGain(float t) { return 1f - t; }
        @Override public float inGain(float t) { return t; }
    },

    /**
     * Quarter-circle pair: {@code cos} down, {@code sin} up. Their squares sum to
     * one, so the total power is constant across the whole overlap — the standard
     * "equal power" crossfade, which sounds level where LINEAR sounds like a dip.
     * The cost is that the two tracks briefly sum above either one alone, which is
     * exactly what a crossfade is supposed to do.
     */
    EQUAL_POWER("等功率") {
        @Override public float outGain(float t) { return (float) Math.cos(t * Math.PI / 2.0); }
        @Override public float inGain(float t) { return (float) Math.sin(t * Math.PI / 2.0); }
    },

    /**
     * The staged, DJ-shaped hand-over: the incoming track arrives <em>early and
     * low</em> (a bed under the outgoing one, not a mirror of its decay), the
     * outgoing track <em>holds</em> near its own level for most of the window and
     * then leaves over a short tail at the end instead of decaying across the whole
     * of it. This is the shape that makes a long overlap read as a mix rather than
     * as a fade.
     *
     * <p><b>Why the symmetric curves read as a fade.</b> With {@code cos}/{@code sin}
     * the two levels are only comparable while {@code t} is near the middle: the
     * gains cross at half a period and the stretch where the listener can hear
     * <em>two</em> tracks is about 41% of the window (measured by
     * {@link #bothAudibleMs(long, float)}), split evenly on both sides of the
     * middle. A fifteen-second ramp therefore spends the first third fading one
     * track up and the last third fading the other one out, which the ear reads as
     * "the song changed" — the complaint this curve answers.
     *
     * <p>The two sides are deliberately not the same function of the same exponent.
     * The outgoing one is the equal-power cosine of {@code t^1.8}: flat for the
     * first half (within 1.3 dB until {@code t}=0.55), then falling fast, so its
     * exit happens over roughly the last quarter rather than across the window. The
     * incoming one is the sine of {@code t^0.55}: it reaches half its level within
     * the first seventh of the overlap and is a real bed from there on, and it keeps
     * the last stretch to itself as the outgoing track goes. The pair therefore sits
     * within 6 dB of each other for about two thirds of the overlap — <em>both
     * tracks audible at once</em>, which is what "sounds like a mix" means — at the
     * cost of summing about 2 dB above a single track in the middle (two tracks at
     * 0.9 each have 1.6 of the power of one, so a correlated pair can peak higher;
     * the low-end hand-over is what keeps the loudest, most correlated part — the
     * bass — from ever doubling).
     *
     * <p>The exponents are the whole tuning: raising the outgoing's makes its hold
     * longer and its exit shorter, lowering the incoming's makes its bed arrive
     * earlier. They were chosen so that the both-audible share is about 66% against
     * the symmetric pair's 41% while the mid-overlap power bump stays near 2 dB
     * ({@code FadeCurveTest} measures both).
     */
    DJ_BLEND("DJ 式") {
        /** How much of the outgoing track's ramp is spent holding: the cosine is
         *  taken of {@code t} raised to this, so a bigger exponent means a longer
         *  hold and a shorter, later exit. */
        private static final double OUT_HOLD_EXPONENT = 1.8d;
        /** How fast the incoming track's bed arrives: the sine of {@code t} raised
         *  to this, so a smaller exponent means a level reached sooner. */
        private static final double IN_BED_EXPONENT = 0.55d;

        @Override public float outGain(float t) {
            return (float) Math.cos(Math.pow(clamp(t), OUT_HOLD_EXPONENT) * Math.PI / 2.0);
        }

        @Override public float inGain(float t) {
            return (float) Math.sin(Math.pow(clamp(t), IN_BED_EXPONENT) * Math.PI / 2.0);
        }
    };

    /** How far apart the two tracks may be, dB, and still count as "both audible"
     *  for the measurement below. Six decibels is the usual "clearly quieter but
     *  plainly present" line: quieter than that and a track is heard as part of the
     *  other one's background rather than as a track of its own. */
    public static final float BOTH_AUDIBLE_DB = 6f;

    private final String label;

    FadeCurve(String label) {
        this.label = label;
    }

    /** {@code t} forced into 0..1, so a caller measuring at, or past, the ends of
     *  the ramp still gets an answer instead of {@code NaN}. */
    private static double clamp(float t) {
        return t <= 0f ? 0d : (t >= 1f ? 1d : t);
    }

    /**
     * How much of a ramp of {@code rampMs} this curve spends with <b>both tracks
     * audible</b> — the two gains within {@link #BOTH_AUDIBLE_DB} of each other.
     *
     * <p>This is the measurable stand-in for "the blend sounds long enough": the
     * complaint a symmetric ramp earns is that the window where the listener can
     * hear two tracks is only about half of it (one track alone at each end is a
     * fade, not a mix), and unlike the overlap length itself this number is a
     * property of the curve. It is reported on the ramp's own log line so a
     * boundary's "feels longer" claim can be checked against a number instead of a
     * memory of the previous build.
     *
     * <p>Sampled rather than integrated in closed form: the two curves have no
     * common primitive once the exponents are in, and a few hundred samples put the
     * resolution far below anything a listener could hear.
     */
    public long bothAudibleMs(long rampMs, float withinDb) {
        if (rampMs <= 0L) return 0L;
        int steps = 400;
        // A gain ratio of `r` is `20*log10(r)` dB, so "within N dB" is a ratio of at
        // most 10^(N/20) — computed once, because the loop below only compares.
        double maxRatio = Math.pow(10d, Math.max(0f, withinDb) / 20d);
        int inside = 0;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;             // midpoint of each slice
            float out = Math.abs(outGain(t));
            float in = Math.abs(inGain(t));
            float loud = Math.max(out, in);
            float quiet = Math.min(out, in);
            if (loud <= 1e-6f) continue;              // neither is audible at all
            if (quiet <= 1e-6f) continue;
            if (loud / quiet <= maxRatio) inside++;
        }
        return Math.round((double) rampMs * inside / steps);
    }

    /** {@link #bothAudibleMs(long, float)} at the standard 6 dB. */
    public long bothAudibleMs(long rampMs) {
        return bothAudibleMs(rampMs, BOTH_AUDIBLE_DB);
    }

    /** "12200ms of the 15000ms ramp (81%)" — the fragment the ramp's log line
     *  carries, so the shape's one measurable property travels with every boundary. */
    public String bothAudibleText(long rampMs) {
        long ms = bothAudibleMs(rampMs);
        long pct = rampMs > 0L ? Math.round(100d * ms / rampMs) : 0L;
        return ms + "ms of the " + rampMs + "ms ramp (" + pct + "%)";
    }

    /** Chinese name for the settings UI. */
    public String label() {
        return label;
    }

    /** Gain applied to the outgoing track at overlap progress {@code t} (0–1). */
    public abstract float outGain(float t);

    /** Gain applied to the incoming track at overlap progress {@code t} (0–1). */
    public abstract float inGain(float t);
}
