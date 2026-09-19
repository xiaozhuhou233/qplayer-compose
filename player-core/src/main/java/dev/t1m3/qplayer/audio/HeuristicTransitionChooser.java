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
 * <p>To be replaced by an AI implementation later through
 * {@code PlayerController.setTransitionChooser(...)}; this class stays as the
 * fallback and as the definition of "sensible defaults" that implementation has
 * to beat.
 */
public final class HeuristicTransitionChooser implements TransitionChooser {

    /** Below this a track counts as short: a 5 s overlap is a real fraction of it,
     *  and a short track's tail is usually the whole arrangement rather than an
     *  outro to talk over. */
    public static final long SHORT_TRACK_MS = 90_000L;

    /** Above this a track counts as long enough that its own tail and the next
     *  one's head are worth measuring rather than guessing at. */
    public static final long LONG_TRACK_MS = 150_000L;

    /** With less than this left, no transition fits: the overlap would have to
     *  start in the past, and the ordinary cut is what the listener is about to
     *  hear anyway. */
    public static final long MIN_REMAINING_MS = 2_000L;

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
        // 4. Either side short: fade, but briefly. A 5 s overlap would eat a
        //    noticeable fraction of a song that is only a minute or two long.
        if (ctx.outgoingDurationMs() < SHORT_TRACK_MS
                || ctx.incomingDurationMs() < SHORT_TRACK_MS) {
            return TransitionKind.QUICK_FADE;
        }
        // 5. Consecutive tracks of one album are mastered to run into each other:
        //    they are the one pair where an actual overlap is the expected sound,
        //    and where a seam would be the thing that sounds wrong.
        if (sameAlbum(ctx.outgoing(), ctx.incoming())) return TransitionKind.CROSSFADE;
        // 6. Two long, unrelated tracks: different records, often different
        //    masters and loudness. Mixing them is a DJ move this heuristic has no
        //    evidence for, so it does not make it — trimming the silence instead
        //    lets the two songs butt together, which is the most "correct" thing
        //    that needs no knowledge of tempo or key.
        if (ctx.outgoingDurationMs() >= LONG_TRACK_MS
                && ctx.incomingDurationMs() >= LONG_TRACK_MS) {
            return TransitionKind.SILENCE_TRIM;
        }
        // 7. Everything else — metadata that proves nothing either way. Fade out,
        //    then fade in: the two songs are never heard at the same time, so a
        //    wrong guess costs a slightly early fade rather than a clash.
        return TransitionKind.FADE_OUT_IN;
    }

    /** Same album when the ids agree (both known), or when the only thing known is
     *  the album's name and it matches. A missing album on either side means "not
     *  the same album" for this purpose: the rule exists to justify an overlap, so
     *  it has to be positive about it. */
    public static boolean sameAlbum(Track a, Track b) {
        if (a == null || b == null) return false;
        if (a.albumId != 0L && b.albumId != 0L) return a.albumId == b.albumId;
        String an = a.album != null ? a.album.trim() : "";
        String bn = b.album != null ? b.album.trim() : "";
        return !an.isEmpty() && an.equalsIgnoreCase(bn);
    }
}
