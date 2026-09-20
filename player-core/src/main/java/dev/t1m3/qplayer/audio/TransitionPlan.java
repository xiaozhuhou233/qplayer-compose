package dev.t1m3.qplayer.audio;

import dev.t1m3.qplayer.model.Track;

import java.nio.ByteBuffer;

/**
 * One boundary's complete answer: <em>which</em> {@link TransitionKind}, <em>how
 * long</em> the overlap is, and (when whoever decided said so) along which
 * {@link FadeCurve}.
 *
 * <p>{@link TransitionKind} alone describes a shape, not a length: it says
 * "overlap the two tracks", not "overlap them for four seconds". The overlap is
 * itself a decision — the same pair sounds like a seam at 4 s and like a mix at
 * 15 s — so it travels with the kind instead of being a constant of it, and the
 * chooser is free to pick it.
 *
 * <p>Immutable, and cheap to build: a chooser answers with one of these on the
 * render thread, so nothing here may block.
 *
 * <p>{@link TransitionChooser#choose} still returns a bare
 * {@link TransitionKind} — this class is what that answer widens into
 * ({@link #of(TransitionKind)}) — so an existing chooser keeps working and simply
 * gets the default overlap.
 */
public final class TransitionPlan {

    /** A short overlap: audible as an overlap, short enough that a track whose own
     *  tail is part of the arrangement still reads as a seam. */
    public static final long OVERLAP_SHORT_MS = 4_000L;

    /** The middle tier. Since the target length was raised it is what a boundary
     *  gets when something about the pair argues for less than the ordinary
     *  {@link #OVERLAP_LONG_MS} — and it is still long enough to be heard as a mix
     *  rather than as a seam. */
    public static final long OVERLAP_MEDIUM_MS = 8_000L;

    /** <b>The length an ordinary suitable pair is blended over</b>, and therefore
     *  the default for {@link TransitionKind#CROSSFADE} — two ordinary streams with
     *  room ahead of them are the case the whole feature exists for, and fifteen
     *  seconds of equal-power overlap is the length a listener hears as a mix.
     *  Shorter than this and the boundary is heard as a fade (which is the
     *  complaint this length answers); much longer and two tracks that do not go
     *  together are simply both loud at once. */
    public static final long OVERLAP_LONG_MS = 15_000L;

    /** The longest blend the app will start, and the length an outgoing track whose
     *  ending is measured to be <em>plain</em> is given: with nothing happening in
     *  the last twenty seconds there is nothing for the overlap to clash with, so
     *  the blend may begin at the start of that plain stretch instead of at the
     *  nominal length (see {@code SilenceProfile.plainTailMs} and the controller's
     *  plain-ending rule). Only ever chosen on that measurement. */
    public static final long OVERLAP_EXTENDED_MS = 20_000L;

    /** The overlap a kind gets when whoever decides does not name one. Only the
     *  overlapping kinds have a real choice: {@link TransitionKind#QUICK_FADE} is
     *  defined as ~1 s, and the other kinds ramp over a fixed, short time of their
     *  own ({@link TransitionKind#SILENCE_TRIM}), or not at all. */
    public static long defaultOverlapMs(TransitionKind kind) {
        if (kind == null) return 0L;
        if (kind == TransitionKind.CROSSFADE) return OVERLAP_LONG_MS;
        return kind.overlapMs();
    }

    /** The overlap tier a length names, for the prompt and the log ("short",
     *  "medium", "long", or the raw number when it is none of them). */
    public static String overlapLabel(long overlapMs) {
        if (overlapMs <= 0L) return "none";
        if (overlapMs <= OVERLAP_SHORT_MS) return "short";
        if (overlapMs <= OVERLAP_MEDIUM_MS) return "medium";
        return "long";
    }

    /** "short 4000ms" — one token per log line, for both parts. */
    public static String overlapText(long overlapMs) {
        if (overlapMs <= 0L) return "none";
        return overlapLabel(overlapMs) + " " + overlapMs + "ms";
    }
    /** The plan a bare {@link TransitionKind} answer widens into: the kind's
     *  default overlap, no curve preference, no source label. */
    public static TransitionPlan of(TransitionKind kind) {
        return of(kind, defaultOverlapMs(kind), null, null);
    }

    public static TransitionPlan of(TransitionKind kind, long overlapMs, FadeCurve curve,
                                    String decidedBy) {
        return new TransitionPlan(kind == null ? TransitionKind.CUT : kind,
                Math.max(0L, overlapMs), curve, decidedBy);
    }

    private final TransitionKind kind;
    private final long overlapMs;
    /** The curve whoever decided asked for, or null for "no preference — use the
     *  configured one" (see {@link #curveOr}). */
    private final FadeCurve curve;
    /** Who answered, for the log ("自动 rule" / "AI cached" / …); null when the
     *  plan was never labelled. */
    private final String decidedBy;

    private TransitionPlan(TransitionKind kind, long overlapMs, FadeCurve curve, String decidedBy) {
        this.kind = kind;
        this.overlapMs = overlapMs;
        this.curve = curve;
        this.decidedBy = decidedBy;
    }

    public TransitionKind kind() {
        return kind;
    }

    /** The overlap this plan wants, ms. Meaningless (0) for the kinds that do not
     *  overlap; the controller checks the kind first. */
    public long overlapMs() {
        return overlapMs;
    }

    /** The curve this plan asks for, or null for "whoever asks should use the
     *  configured one". */
    public FadeCurve curve() {
        return curve;
    }

    public String decidedBy() {
        return decidedBy;
    }

    /** The same plan with a different overlap — what the controller applies when
     *  the remaining time (or a short track) will not fit the one asked for. The
     *  label gains the reason, so the log says the long pick was cut down rather
     *  than leaving the honest-looking shorter ramp unexplained. */
    public TransitionPlan withOverlap(long ms, String because) {
        String label = decidedBy;
        if (because != null) {
            label = (label == null ? "" : label + "; ") + because;
        }
        return new TransitionPlan(kind, Math.max(0L, ms), curve, label);
    }

    /**
     * The curve to actually ramp along, given the curve the user configured.
     *
     * <p>A plan that named one wins. A plan that did not gets the configured curve
     * — except over a long overlap, where LINEAR is not a taste difference: the
     * straight-line pair leaves the summed power at half in the middle of the ramp,
     * an audible ~3 dB dip over the fifteen seconds this overlap was chosen for.
     * Answering "long" is therefore answered with {@link FadeCurve#EQUAL_POWER}
     * unless the decider explicitly asked for something else.
     */
    public FadeCurve curveOr(FadeCurve configured) {
        if (curve != null) return curve;
        if (overlapMs >= OVERLAP_LONG_MS) return FadeCurve.EQUAL_POWER;
        return configured != null ? configured : FadeCurve.LINEAR;
    }

    /** True when {@link #curveOr} overrode the configured curve — the caller logs
     *  this so a settings row that appears to be ignored says why. */
    public boolean curveOverrides(FadeCurve configured) {
        FadeCurve effective = curveOr(configured);
        FadeCurve requested = configured != null ? configured : FadeCurve.LINEAR;
        return curve == null && overlapMs >= OVERLAP_LONG_MS && effective != requested;
    }

    // --- the pair identity the cache is keyed by ----------------------------

    /** The key one track is remembered under: its song id where there is one, the
     *  custom source's own id next, the source string otherwise. The same
     *  convention the silence cache uses ({@code PlayerController.silenceKey} is
     *  this method), so both caches describe a track by the same name — a
     *  measurement and a decision about the same audio cannot disagree about which
     *  track they belong to. Null when the track has no stable identity at all
     *  (then nothing about it is cached). */
    public static String trackKey(Track t) {
        if (t == null) return null;
        if (t.neteaseId > 0L) return "n" + t.neteaseId;
        if (t.customId != null && !t.customId.isEmpty()) return "c" + t.customId;
        String p = t.playable();
        return (p != null && !p.isEmpty()) ? "s" + p : null;
    }

    /** The key a per-pair decision is cached under. Order matters (A into B is not
     *  B into A), so the two keys are joined in playback order. Null when either
     *  side has no identity. */
    public static String pairKey(Track outgoing, Track incoming) {
        String a = trackKey(outgoing);
        String b = trackKey(incoming);
        if (a == null || b == null) return null;
        return a + ">" + b;
    }

    // --- disk form ----------------------------------------------------------

    /** Version byte of {@link #toBytes()}: bump it when the layout changes, so an
     *  older cache file is ignored instead of misread (the readers below reject an
     *  unknown version rather than guessing a kind from a shifted byte). */
    private static final byte VERSION = 1;

    /** Curve byte for "no preference" ({@link #curve()} null). */
    private static final byte CURVE_NONE = 2;

    /** Eight bytes: version, kind ordinal, curve, a reserved byte, and the overlap
     *  in ms. The same shape as a {@link SilenceProfile} — a decision is a few
     *  enums, and a per-pair cache of them has no business being larger. */
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put(VERSION);
        buf.put((byte) kind.ordinal());
        buf.put(curve == null ? CURVE_NONE : (byte) curve.ordinal());
        buf.put((byte) 0);
        buf.putInt((int) Math.min(Integer.MAX_VALUE, overlapMs));
        return buf.array();
    }

    /** Inverse of {@link #toBytes()}. Null for anything that is not exactly one —
     *  a truncated write, a file from another layout, an ordinal this build does
     *  not have. A null answer means "no opinion", and the caller falls back to the
     *  heuristic: a cache file may never be the reason a transition is wrong. */
    public static TransitionPlan fromBytes(byte[] data, String decidedBy) {
        if (data == null || data.length != 8) return null;
        ByteBuffer buf = ByteBuffer.wrap(data);
        if (buf.get() != VERSION) return null;
        int kindOrdinal = buf.get() & 0xFF;
        int curveOrdinal = buf.get() & 0xFF;
        buf.get();                      // reserved
        int overlapMs = buf.getInt();
        TransitionKind[] kinds = TransitionKind.values();
        if (kindOrdinal >= kinds.length) return null;
        FadeCurve curve = null;
        if (curveOrdinal != CURVE_NONE) {
            FadeCurve[] curves = FadeCurve.values();
            if (curveOrdinal >= curves.length) return null;
            curve = curves[curveOrdinal];
        }
        if (overlapMs < 0) return null;
        return of(kinds[kindOrdinal], overlapMs, curve, decidedBy);
    }

    @Override
    public String toString() {
        return kind + "[" + overlapText(overlapMs)
                + (curve != null ? ", " + curve : "")
                + (decidedBy != null ? ", " + decidedBy : "") + "]";
    }
}
