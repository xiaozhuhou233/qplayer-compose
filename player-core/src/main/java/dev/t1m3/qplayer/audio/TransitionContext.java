package dev.t1m3.qplayer.audio;

import dev.t1m3.qplayer.model.Track;

/**
 * Everything a {@link TransitionChooser} may look at when deciding how one track
 * should give way to the next: the two tracks' metadata, how much of the
 * outgoing track is left, and whether each side can be streamed on a second
 * player at all.
 *
 * <p>Deliberately limited to facts, not to analysis. There are no BPM, key or onset
 * fields here because a chooser cannot ask for something the app does not have:
 * nothing in qplayer estimates those for the boundary's decision (the beat grids
 * are consulted by the controller alone, after the kind is chosen), and a chooser
 * that never sees them cannot pretend to have used them. An AI implementation
 * plugged in later gets exactly this and is expected to decide from the same facts
 * a person reading the queue would have: titles, artists, albums, lengths, what is
 * left of the current track — plus the two silence measurements below, which are
 * the one analysis that does reach the decision, because a trim's whole
 * justification is a number the app has already measured for another reason.
 *
 * <p>Immutable: built by the controller per boundary and handed to the chooser.
 */
public final class TransitionContext {

    /** {@link #outgoingTailSilenceMs()} / {@link #incomingHeadSilenceMs()} when the
     *  measurement has not been made (or this host has no profiler at all). */
    public static final long SILENCE_UNKNOWN = -1L;

    private final Track outgoing;
    private final Track incoming;
    private final long remainingMs;
    private final long outgoingDurationMs;
    private final boolean outgoingStreamable;
    private final boolean incomingStreamable;
    private final long outgoingTailSilenceMs;
    private final long incomingHeadSilenceMs;

    /** The context a chooser sees: metadata plus "either side can be streamed on a
     *  second player", with both silence measurements unknown. */
    public TransitionContext(Track outgoing, Track incoming, long remainingMs,
                             long outgoingDurationMs, boolean outgoingStreamable,
                             boolean incomingStreamable) {
        this(outgoing, incoming, remainingMs, outgoingDurationMs, outgoingStreamable,
                incomingStreamable, SILENCE_UNKNOWN, SILENCE_UNKNOWN);
    }

    /**
     * Same, with what has already been measured about the two ends.
     *
     * <p>{@code outgoingTailSilenceMs} / {@code incomingHeadSilenceMs} are the
     * silence measurements the controller already keeps per track ({@code
     * SilenceProfile.headMs()}/{@code tailMs()}), or {@link #SILENCE_UNKNOWN}. They
     * are here for one rule only: a seam that skips a measured silence is
     * demonstrably better than an overlap that ramps music into it, and that is the
     * one case where {@link TransitionKind#SILENCE_TRIM} is still chosen over the
     * default {@link TransitionKind#CROSSFADE}. Measured at the decision, never
     * waited for: an unknown measurement is simply not evidence.
     */
    public TransitionContext(Track outgoing, Track incoming, long remainingMs,
                             long outgoingDurationMs, boolean outgoingStreamable,
                             boolean incomingStreamable,
                             long outgoingTailSilenceMs, long incomingHeadSilenceMs) {
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.remainingMs = Math.max(0L, remainingMs);
        this.outgoingDurationMs = Math.max(0L, outgoingDurationMs);
        this.outgoingStreamable = outgoingStreamable;
        this.incomingStreamable = incomingStreamable;
        this.outgoingTailSilenceMs = outgoingTailSilenceMs;
        this.incomingHeadSilenceMs = incomingHeadSilenceMs;
    }

    /** The track that is playing now. Never null in a boundary the controller
     *  asks about, but a chooser must still tolerate a null {@link #incoming()}. */
    public Track outgoing() {
        return outgoing;
    }

    /** The track the queue would advance to. */
    public Track incoming() {
        return incoming;
    }

    /** How much of the outgoing track is left to play, ms — the whole budget a
     *  transition has to fit into. */
    public long remainingMs() {
        return remainingMs;
    }

    /** The outgoing track's length, ms: its metadata when it has any, otherwise
     *  the live player's own duration. Not an estimate — one of the two is
     *  authoritative for the track that is playing. */
    public long outgoingDurationMs() {
        return outgoingDurationMs;
    }

    /** The incoming track's length, ms, from its metadata only (0 = unknown). */
    public long incomingDurationMs() {
        return incoming != null ? Math.max(0L, incoming.durationMs) : 0L;
    }

    /** Whether the outgoing side is an ordinary stream. A BILI link is short lived
     *  and its picture belongs to the active player; a local file would need a
     *  second decoder for no real gain — neither can be overlapped. */
    public boolean outgoingStreamable() {
        return outgoingStreamable;
    }

    /** Same for the incoming side. */
    public boolean incomingStreamable() {
        return incomingStreamable;
    }

    /** True when both lengths are known, which is the precondition for any claim
     *  about how much of either track a transition would consume. */
    public boolean hasBothLengths() {
        return outgoingDurationMs > 0L && incomingDurationMs() > 0L;
    }

    /** How much silence the outgoing track is measured to trail, ms, or
     *  {@link #SILENCE_UNKNOWN}. */
    public long outgoingTailSilenceMs() {
        return outgoingTailSilenceMs;
    }

    /** How much silence the incoming track is measured to lead with, ms, or
     *  {@link #SILENCE_UNKNOWN}. */
    public long incomingHeadSilenceMs() {
        return incomingHeadSilenceMs;
    }

    @Override
    public String toString() {
        return "TransitionContext{" + outgoing + " -> " + incoming
                + ", remaining=" + remainingMs + "ms"
                + ", streamable=" + outgoingStreamable + "/" + incomingStreamable + "}";
    }
}
