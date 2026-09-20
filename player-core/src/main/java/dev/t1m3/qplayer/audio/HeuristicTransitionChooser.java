package dev.t1m3.qplayer.audio;

import dev.t1m3.qplayer.model.Track;

/**
 * The default {@link TransitionChooser}: a fixed, explainable rule set over
 * metadata, no network and no randomness, so the same two tracks always get the
 * same transition and every choice can be argued for in one sentence.
 *
 * <p>The rules are ordered from "cannot be performed" to "what the pair itself
 * suggests", and every one of them may only return a kind the boundary can
 * actually perform — an answer that would need a second player for a BILI link
 * or a local file is downgraded here, not discovered later.
 *
 * <p>⚠️ <b>The default answer is an overlap.</b> Only four things are allowed to
 * refuse one, and all four are physical (a side that cannot go on a second player,
 * a boundary that has already arrived, a length nobody knows, and — for the shape
 * of the overlap, not for whether there is one — a track that is too short for it).
 * Nothing about tempo, key or a beat grid can refuse anything here: those only
 * decide whether the overlap can additionally be <em>aligned</em>, which is the
 * controller's business after this answer is given. An earlier round answered
 * {@link TransitionKind#SILENCE_TRIM} (a 250 ms seam) for every pair of long
 * tracks, which is the least audible transition there is — and because that is the
 * answer an ordinary library of long tracks got for every boundary, the whole
 * feature was heard as "nothing happened".
 *
 * <p>{@link TransitionKind#SILENCE_TRIM} is now chosen only on evidence: when the
 * outgoing track has actually been <em>measured</em> to trail more than
 * {@link #TRIM_TAIL_MIN_MS} of silence. There is nothing to overlap there — an
 * eight-second ramp would spend most of itself fading music into silence — so butting
 * the two contents together is demonstrably better, and the measurement that says so
 * is already in hand. Without that measurement the answer is the plain overlap.
 *
 * <p>To be replaced by an AI implementation later through
 * {@code PlayerController.setTransitionChooser(...)}; this class stays as the
 * fallback and as the definition of "sensible defaults" that implementation has
 * to beat.
 */
public final class HeuristicTransitionChooser implements TransitionChooser {

    /** Below this a track counts as short: a fifteen-second overlap is a real
     *  fraction of it,
     *  and a short track's tail is usually the whole arrangement rather than an
     *  outro to talk over. Short pairs still overlap — just briefly. */
    public static final long SHORT_TRACK_MS = 90_000L;

    /** With less than this left, no transition fits: the overlap would have to
     *  start in the past, and the ordinary cut is what the listener is about to
     *  hear anyway. */
    public static final long MIN_REMAINING_MS = 2_000L;

    /**
     * How much measured trailing silence makes a trim the better answer.
     *
     * <p>A trim's whole case is the outgoing track's dead air: the seam is placed at
     * the end of its <em>content</em>, so the silence is never played and (unlike any
     * overlap) the incoming track's own head silence is skipped too. Below this the
     * tail is short enough that overlapping it is the thing that sounds like a mix,
     * so the default overlap wins; above it, an eight-second ramp would be seven
     * seconds of fading nothing.
     *
     * <p>Measured, never assumed: the value comes from the silence profiler's cache
     * ({@link TransitionContext#outgoingTailSilenceMs()}), and an unmeasured track
     * simply does not get the trim. 1200 ms is comfortably past what a normal
     * master's own decay or reverb tail looks like to a -46 dBFS detector (the
     * threshold the profiler uses), so a track only qualifies when its file really
     * does run on after the music.
     */
    public static final long TRIM_TAIL_MIN_MS = 1_200L;

    @Override
    public TransitionKind choose(TransitionContext ctx) {
        if (ctx == null) return TransitionKind.CUT;
        // 1. Both ends have to be ordinary streams. This is the one rule that is
        //    about capability rather than taste: BILI and LOCAL cannot be put on a
        //    second player (a short-lived link whose picture belongs to the active
        //    player; a filesystem decoder for no gain), so anything overlapping is
        //    off the table.
        if (!ctx.outgoingStreamable() || !ctx.incomingStreamable()) return TransitionKind.CUT;
        // 2. Too close to the end for any transition to be prepared in time.
        if (ctx.remainingMs() < MIN_REMAINING_MS) return TransitionKind.CUT;
        // 3. A length nobody knows is a length no transition can plan against —
        //    and an unknown length is exactly the track whose own completion
        //    callback is most likely to arrive while a ramp is still running. The
        //    historical hard cut is the only honest answer.
        if (!ctx.hasBothLengths()) return TransitionKind.CUT;
        // 4. Either side short: fade, but briefly. An 8 s overlap would eat a
        //    noticeable fraction of a song that is only a minute or two long.
        if (ctx.outgoingDurationMs() < SHORT_TRACK_MS
                || ctx.incomingDurationMs() < SHORT_TRACK_MS) {
            return TransitionKind.QUICK_FADE;
        }
        // 5. The outgoing track is measured to end in silence: there is nothing to
        //    overlap, so trim the dead air instead (see TRIM_TAIL_MIN_MS). This is
        //    the only case where a seam beats an overlap, and it is the only reason
        //    SILENCE_TRIM is ever the automatic answer.
        if (ctx.outgoingTailSilenceMs() >= TRIM_TAIL_MIN_MS) {
            return TransitionKind.SILENCE_TRIM;
        }
        // 6. Everything else: the plain overlap. Two ordinary streams with room
        //    ahead of them are exactly the case the whole feature exists for, and a
        //    cut here would be a blend given up for no reason at all. The length is
        //    the kind's default (fifteen seconds, the ordinary target — raised
        //    further by the controller when the outgoing track's own ending is
        //    measured to be plain) and the curve is named with it (see plan()): a
        //    symmetric ramp over a window this long spends most of itself with one
        //    track alone, which is heard as a fade rather than as a mix.
        return TransitionKind.CROSSFADE;
    }

    /**
     * The same answer with the curve a plain overlap wants, and with the reason this
     * pair got what it got (the chooser labels the branch it took, which is the one
     * thing a decision needs to be auditable from the log).
     *
     * <p>A CROSSFADE over its default (long, 15 s) overlap is long enough that a
     * symmetric pair — linear or equal power — is heard as a fade: the two levels are
     * only comparable near the middle of the window, so about two fifths of it has
     * both tracks audible and the rest is one track rising or the other one falling.
     * That branch therefore names {@link FadeCurve#DJ_BLEND}, the staged shape whose
     * measured both-audible share is about two thirds of the window (see
     * {@link FadeCurve#bothAudibleMs(long)}). A forced kind or an AI answer that names
     * its own curve still goes through untouched, and the plan carries whichever was
     * chosen so the boundary's log line prints it.
     */
    @Override
    public TransitionPlan plan(TransitionContext ctx) {
        TransitionKind kind = choose(ctx);
        if (kind == TransitionKind.CROSSFADE) {
            return TransitionPlan.of(kind, TransitionPlan.defaultOverlapMs(kind),
                    FadeCurve.DJ_BLEND,
                    "rule: two ordinary streams with room ahead, so the ordinary "
                            + (TransitionPlan.OVERLAP_LONG_MS / 1000L) + "s overlap"
                            + " (DJ 式: a symmetric ramp this long is heard as a fade — both"
                            + " tracks are only within 6dB of each other for about two fifths"
                            + " of it)");
        }
        if (kind == TransitionKind.SILENCE_TRIM) {
            return TransitionPlan.of(kind, TransitionPlan.defaultOverlapMs(kind), null,
                    "rule: the outgoing track is measured to end in "
                            + (ctx == null ? 0L : ctx.outgoingTailSilenceMs())
                            + "ms of silence (>= " + TRIM_TAIL_MIN_MS + "ms), so the seam is"
                            + " trimmed rather than ramped into it");
        }
        return TransitionPlan.of(kind, TransitionPlan.defaultOverlapMs(kind), null,
                "rule: " + cutOrShortReason(ctx, kind));
    }

    /** One clause naming why the first rules refused an overlap, for the log. */
    private static String cutOrShortReason(TransitionContext ctx, TransitionKind kind) {
        if (ctx == null) return "no context";
        if (!ctx.outgoingStreamable() || !ctx.incomingStreamable()) {
            return "one side cannot be streamed on a second player";
        }
        if (ctx.remainingMs() < MIN_REMAINING_MS) return "too late in the track";
        if (!ctx.hasBothLengths()) return "a length nobody knows";
        if (kind == TransitionKind.QUICK_FADE) return "one of the two tracks is under "
                + (SHORT_TRACK_MS / 1000L) + "s, so the overlap is kept short";
        return "no reason recorded";
    }
}
