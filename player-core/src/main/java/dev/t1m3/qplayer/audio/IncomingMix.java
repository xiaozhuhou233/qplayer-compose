package dev.t1m3.qplayer.audio;

/**
 * What the incoming player has to be configured with for a mix: the speed it
 * runs at (pitch-preserving — the platform's own time-stretch, and applied to the
 * incoming track only, never to the one that is already playing), the pitch ratio
 * it is transposed by, and the moment the low end should hand over.
 *
 * <p>This is the platform half of {@link MixMatch} and is deliberately a separate
 * object: {@code MixMatch} is a judgement about two tracks (it holds beat grids
 * and keys), while this is an instruction about one player (it holds numbers a
 * media API takes). The backend never sees a {@link BeatProfile}, and the
 * decision never sees a session id.
 *
 * <p>Immutable. {@link #IDENTITY} is the "nothing to do" answer, and every
 * backend is free to refuse the instruction — a platform that cannot stretch or
 * cannot attach an effect must answer so rather than play something else.
 */
public final class IncomingMix {

    /** Nothing to apply: the incoming plays at its own tempo and pitch. */
    public static final IncomingMix IDENTITY = new IncomingMix(1d, 1d, -1L);

    private final double speed;
    private final double pitch;
    private final long bassSwapAtMs;

    private IncomingMix(double speed, double pitch, long bassSwapAtMs) {
        this.speed = speed;
        this.pitch = pitch;
        this.bassSwapAtMs = bassSwapAtMs;
    }

    /**
     * @param speed        how much faster (or slower) the incoming track plays,
     *                     1.0 = untouched; pitch-preserving, so the beats move and
     *                     the notes do not
     * @param pitch        the frequency ratio the incoming track is transposed by
     *                     (2^(semitones/12)), independent of the speed
     * @param bassSwapAtMs when, ms into the overlap, the low end hands over from the
     *                     outgoing track to the incoming one; -1 for no hand-over
     *                     (or no way to do it)
     */
    public static IncomingMix of(double speed, double pitch, long bassSwapAtMs) {
        return new IncomingMix(speed, pitch, bassSwapAtMs);
    }

    /** Whether this asks for nothing at all. */
    public boolean isIdentity() {
        return Math.abs(speed - 1d) < 1e-6d && Math.abs(pitch - 1d) < 1e-6d && bassSwapAtMs < 0L;
    }

    /**
     * Whether {@code other} asks for the same tempo and pitch. The low end is not
     * compared: it is best-effort, so a backend that applied the speed and the pitch
     * but had no equalizer has done everything this is asked about.
     */
    public boolean sameTempoAndPitch(IncomingMix other) {
        if (other == null) return false;
        return Math.abs(speed - other.speed) < 1e-3d && Math.abs(pitch - other.pitch) < 1e-3d;
    }

    public double speed() {
        return speed;
    }

    public double pitch() {
        return pitch;
    }

    public long bassSwapAtMs() {
        return bassSwapAtMs;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US, "IncomingMix{x%.4f, pitch x%.4f, bassSwap=%dms}",
                speed, pitch, bassSwapAtMs);
    }
}
