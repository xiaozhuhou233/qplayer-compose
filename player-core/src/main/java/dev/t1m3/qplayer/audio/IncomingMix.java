package dev.t1m3.qplayer.audio;

/**
 * What the incoming player has to be configured with for a blend: <em>when the low
 * end hands over</em> from the outgoing track to the incoming one.
 *
 * <p>That is now the only thing this type can say, and deliberately so. An earlier
 * round also carried a tempo ratio and a pitch ratio for the pair — the incoming
 * track stretched onto the outgoing track's beat grid and transposed into its key,
 * applied with {@code MediaPlayer.setPlaybackParams}. That whole lane is off: the
 * app never stretches a tempo and never shifts a pitch (see {@code AI_HANDOFF.md}
 * §7, "no tempo/key"), so the instruction the controller hands the backend cannot
 * ask for either, and this file is where a reader can see that no such instruction
 * exists any more. A backend with no equalizer simply plays the overlap without the
 * hand-over — which is the one part that was ever best-effort.
 *
 * <p>Immutable and tiny: it is built once per boundary, on the render pump.
 */
public final class IncomingMix {

    /** Nothing to do: no low-end hand-over. */
    public static final IncomingMix IDENTITY = new IncomingMix(-1L);

    /** A mix whose only instruction is the low-end hand-over {@code ms} into the
     *  overlap; -1 for "no hand-over" (or no way to do it on this device). */
    public static IncomingMix bassSwapAt(long ms) {
        return ms < 0L ? IDENTITY : new IncomingMix(ms);
    }

    private final long bassSwapAtMs;

    private IncomingMix(long bassSwapAtMs) {
        this.bassSwapAtMs = bassSwapAtMs;
    }

    /** Whether this asks for nothing at all. */
    public boolean isIdentity() {
        return bassSwapAtMs < 0L;
    }

    /** When, ms into the overlap, the low end hands over from the outgoing track to
     *  the incoming one; -1 for no hand-over. */
    public long bassSwapAtMs() {
        return bassSwapAtMs;
    }

    @Override
    public String toString() {
        return isIdentity() ? "IncomingMix{none}"
                : "IncomingMix{bassSwap=" + bassSwapAtMs + "ms}";
    }
}
