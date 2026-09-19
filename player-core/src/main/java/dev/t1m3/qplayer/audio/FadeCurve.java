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
    };

    private final String label;

    FadeCurve(String label) {
        this.label = label;
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
