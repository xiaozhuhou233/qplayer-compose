package dev.t1m3.qplayer.audio;

import dev.t1m3.qplayer.model.Track;

/**
 * Everything a {@link TransitionChooser} may look at when deciding how one track
 * should give way to the next: the two tracks' metadata, how much of the
 * outgoing track is left, and whether each side can be streamed on a second
 * player at all.
 *
 * <p>Deliberately limited to what the app already knows before any audio is
 * analysed. There are no BPM, key or onset fields here because no platform API
 * provides them and nothing in qplayer estimates them yet ({@code Track} carries
 * no such fields either) — so a chooser cannot ask for them, and a chooser that
 * never sees them cannot pretend to have used them. An AI implementation plugged
 * in later gets exactly this and is expected to decide from the same facts a
 * person reading the queue would have: titles, artists, albums, lengths, what is
 * left of the current track.
 *
 * <p>Immutable: built by the controller per boundary and handed to the chooser.
 */
public final class TransitionContext {

    private final Track outgoing;
    private final Track incoming;
    private final long remainingMs;
    private final long outgoingDurationMs;
    private final boolean outgoingStreamable;
    private final boolean incomingStreamable;

    public TransitionContext(Track outgoing, Track incoming, long remainingMs,
                             long outgoingDurationMs, boolean outgoingStreamable,
                             boolean incomingStreamable) {
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.remainingMs = Math.max(0L, remainingMs);
        this.outgoingDurationMs = Math.max(0L, outgoingDurationMs);
        this.outgoingStreamable = outgoingStreamable;
        this.incomingStreamable = incomingStreamable;
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

    @Override
    public String toString() {
        return "TransitionContext{" + outgoing + " -> " + incoming
                + ", remaining=" + remainingMs + "ms"
                + ", streamable=" + outgoingStreamable + "/" + incomingStreamable + "}";
    }
}
