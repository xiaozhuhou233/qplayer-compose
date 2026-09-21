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

    /**
     * What the two tracks' own measured material says about setting them on top of each other:
     * whether their keys were measured to fight, whether their tempos can be brought onto one
     * grid, and the measurement behind both.
     *
     * <p>⚠️ <b>Round 17 widens what a chooser may look at, and this is the one addition.</b> The
     * class note above used to say there are no BPM or key fields here "because a chooser cannot
     * ask for something the app does not have" — true when the only analysis was the silence
     * profiler. It has not been true since round 11: every track the app plays is measured for a
     * beat grid and a key ({@code BeatProfiler}/{@code BeatAnalysis}), the results are cached per
     * track, and the controller reads them at the boundary anyway. What was missing was a
     * <em>verdict</em> over the pair, and that is what this is: not the two tracks' numbers, but
     * the two answers a chooser's rule needs — "do their keys clash?" and "can their tempos be
     * locked together?" — each of which is a decision the naturaliser already makes for every
     * boundary.
     *
     * <p>It exists because of what the user asked for: several transition kinds actually being
     * used, chosen by evidence rather than by habit. {@link HeuristicTransitionChooser} answers
     * {@link TransitionKind#FADE_OUT_IN} — play the outgoing track out, then start the next one,
     * nothing mixed at all — for a pair whose material <em>overlaps badly</em>, and that phrase
     * is defined here ({@link #overlapsBadly()}) so the rule and its evidence cannot drift apart.
     */
    public static final class PairFit {

        /** No beat or key measurement for one of the two tracks: nothing is claimed about the
         *  pair, and a rule that wants evidence does not fire. */
        public static final PairFit UNMEASURED = new PairFit(false, false, false, false, 0d, "",
                "neither track's beat grid and key have been measured");

        private final boolean keysMeasured;
        private final boolean keysClash;
        private final boolean temposMeasured;
        private final boolean temposLockable;
        private final double keyDistance;
        private final String note;
        private final String what;

        /**
         * @param keysMeasured   both keys were measured and are trustworthy
         * @param keysClash      measured, and nothing within ±{@link IncomingMix#MAX_SEMITONES}
         *                       brings them together (see {@link MixNaturaliser.Keys#clash()})
         * @param temposMeasured both beat grids exist and are trustworthy
         * @param temposLockable the two grids already hold over the blend, or pulling the
         *                       incoming track's tempo is inside {@link
         *                       IncomingMix#MAX_SPEED_STEP}
         * @param keyDistance    the profile distance as the keys stand (0 when unmeasured)
         * @param what           what was measured, for the log line, never null
         * @param note           the sentence the reason text is built from, never null
         */
        public PairFit(boolean keysMeasured, boolean keysClash, boolean temposMeasured,
                       boolean temposLockable, double keyDistance, String what, String note) {
            this.keysMeasured = keysMeasured;
            this.keysClash = keysClash;
            this.temposMeasured = temposMeasured;
            this.temposLockable = temposLockable;
            this.keyDistance = keyDistance;
            this.note = note == null ? "" : note;
            this.what = what == null ? "" : what;
        }

        /** The keys could be judged and they were measured to fight: the pair would be in two
         *  keys at once for the whole blend. */
        public boolean keysClash() {
            return keysMeasured && keysClash;
        }

        /** Both grids were judged and pulling the incoming track's tempo onto the outgoing
         *  track's would need more than the clamp allows — the two rhythms cannot be held
         *  together, so a blend would be two tempos at once. */
        public boolean rhythmsClash() {
            return temposMeasured && !temposLockable;
        }

        /**
         * The evidence-based answer to "would these two be better one after the other?": their
         * keys fight, or their tempos cannot be locked together (or both). Deliberately <em>not</em>
         * "any measurable difference" — a pair that is merely in different keys the naturaliser
         * can move, or whose grids it can pull, overlaps perfectly well, and answering a
         * sequential fade for every such pair would be the "nothing happened" regression of the
         * earlier rounds in a new coat.
         */
        public boolean overlapsBadly() {
            return keysClash() || rhythmsClash();
        }

        /** The measurement behind {@link #overlapsBadly()}, for the log line. Never null. */
        public String describe() {
            return note;
        }

        /** What was measured, in one clause. Never null. */
        public String what() {
            return what;
        }

        @Override
        public String toString() {
            return "PairFit{" + what + "}";
        }
    }

    private final Track outgoing;
    private final Track incoming;
    private final long remainingMs;
    private final long outgoingDurationMs;
    private final boolean outgoingStreamable;
    private final boolean incomingStreamable;
    private final long outgoingTailSilenceMs;
    private final long incomingHeadSilenceMs;
    private final PairFit pairFit;

    /** The context a chooser sees: metadata plus "either side can be streamed on a
     *  second player", with both silence measurements unknown and no pair measurement. */
    public TransitionContext(Track outgoing, Track incoming, long remainingMs,
                             long outgoingDurationMs, boolean outgoingStreamable,
                             boolean incomingStreamable) {
        this(outgoing, incoming, remainingMs, outgoingDurationMs, outgoingStreamable,
                incomingStreamable, SILENCE_UNKNOWN, SILENCE_UNKNOWN, PairFit.UNMEASURED);
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
        this(outgoing, incoming, remainingMs, outgoingDurationMs, outgoingStreamable,
                incomingStreamable, outgoingTailSilenceMs, incomingHeadSilenceMs,
                PairFit.UNMEASURED);
    }

    /**
     * The full context: the two ends' silence, and the pair's own measurement
     * ({@link PairFit}) — see that class for why the second one exists and what it may and may
     * not be used for.
     */
    public TransitionContext(Track outgoing, Track incoming, long remainingMs,
                             long outgoingDurationMs, boolean outgoingStreamable,
                             boolean incomingStreamable,
                             long outgoingTailSilenceMs, long incomingHeadSilenceMs,
                             PairFit pairFit) {
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.remainingMs = Math.max(0L, remainingMs);
        this.outgoingDurationMs = Math.max(0L, outgoingDurationMs);
        this.outgoingStreamable = outgoingStreamable;
        this.incomingStreamable = incomingStreamable;
        this.outgoingTailSilenceMs = outgoingTailSilenceMs;
        this.incomingHeadSilenceMs = incomingHeadSilenceMs;
        this.pairFit = pairFit == null ? PairFit.UNMEASURED : pairFit;
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

    /** The pair's own measurement, never null — see {@link PairFit}. */
    public PairFit pairFit() {
        return pairFit;
    }

    @Override
    public String toString() {
        return "TransitionContext{" + outgoing + " -> " + incoming
                + ", remaining=" + remainingMs + "ms"
                + ", streamable=" + outgoingStreamable + "/" + incomingStreamable
                + ", " + pairFit + "}";
    }
}
