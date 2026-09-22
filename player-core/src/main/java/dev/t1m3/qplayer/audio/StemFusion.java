package dev.t1m3.qplayer.audio;

import java.util.Locale;

/**
 * The fusion: both tracks' backgrounds combined into ONE passage, three bars long, written into
 * the incoming track's own file — round 18's replacement for the fade a two-deck blend is.
 *
 * <p><b>What it is for.</b> A blend of two records reads as a fade in the middle whatever the
 * gain curves do, because the outgoing track's <em>rhythm</em> leaves with it (the low end is
 * handed over part way through, and its body ramps away over the next seconds). The fusion stops
 * fading altogether: the outgoing track's own bars continue inside the incoming's file, and each
 * of its elements is removed by an <b>80 ms splice on a bar line</b> — never by a level ramp over
 * a whole track — while the incoming's own elements arrive on the same lines, one bar apart.
 * Nothing audible about the transition therefore sits on a fade curve.
 *
 * <pre>
 * | file window        | A drums | A bass | A other | B drums | B bass | B other |
 * | [entry, swap)      | unity   | unity  | -12 dB  | 0       | 0      | silence -> unity |
 * | [swap, bass)       | 0       | unity  | -12 dB  | unity   | 0      | unity           |
 * | [bass, fusionEnd)  | 0       | 0      | 0       | unity   | unity  | unity           |
 * </pre>
 *
 * <p>Every `unity`/`0` pair above is one {@link #CUT_MS} equal-power splice on the bar line, and
 * each one is counted and reported ({@link Report#splices}). <b>The one exception is the
 * incoming's own bed ({@code other}), which fades in over {@link #BED_FADE_BARS} bar — one whole
 * bar of its own grid, equal-power, from {@code entry} to unity.</b> The user asked for exactly
 * that after hearing the first prototype (「新歌的进入请用淡入效果」), and it is also what keeps the
 * level the listener is at continuous while the outgoing track's voice leaves: a bed arriving over
 * the same bar the voice departs is what masks it. The incoming's drums and low end still swap
 * <em>by cut</em> on their own lines — that is the hand-over's signature, and a faded drum swap
 * would smear the pulse. Outside the window the file is exactly what it is without a fusion: the
 * incoming's backing at unity under its own vocal gate. The outgoing's <b>vocal row is never
 * carried</b> — the whole point of the window is that neither track sings over the other.
 *
 * <p><b>The second half of the junction's contract is the peak.</b> The outgoing's own master
 * already peaks at about full scale, so any added bed is over it — a static divisor over the head
 * is not acceptable (measured on real material: −4.55 dB over the whole head, which the listener
 * heard as a 卡顿 at the junction). The head therefore carries a peak limiter ({@link
 * DjEdit.Limiter}: 1 ms attack, 150 ms release, no makeup gain), and the render measures its own
 * <b>step</b>: the fusion's first {@link #STEP_WINDOW_MS} RMS against the outgoing master's last
 * {@link #STEP_WINDOW_MS}, which has to be within {@link #JUNCTION_STEP_MAX_DB}. A fusion that
 * cannot sit at the outgoing's own level is refused rather than played as a drop.
 *
 * <p><b>Where the material comes from.</b> The outgoing deck is cut on a bar line of its own grid
 * ({@code junctionMs}), so the passage the file carries is the music that <em>would have played
 * next</em> — decoded when the outgoing deck dies and carried forward inside the incoming's file,
 * resampled by {@code speed} so that playback at {@code speed} returns it to the outgoing track's
 * own tempo and pitch (the same bargain {@link StemBridge} makes for its two bars of bass).
 *
 * <p>Pure arithmetic on separated stems: no platform, no files, no model — so what a render will
 * do is a unit test, and the acceptance numbers ({@link #measure}) are the same arithmetic the
 * render reports. This is the round-17 {@link StemBridge} pattern applied to the whole backing
 * rather than to the bass alone; it reuses that class's measurement primitives
 * ({@link StemBridge#levelDb}, {@link StemBridge#frameLevelsDb}, {@link StemBridge#median},
 * {@link StemBridge#alignmentAtZeroLag}) so "quiet", "present" and "the voice" mean the same
 * thing in both places.
 *
 * <p><b>Why the spliced gesture is not {@link StemGesture}'s.</b> {@code StemGesture} carries the
 * ported Folia grammar — a <em>takeover</em> scored by listening rounds, whose cut is
 * {@link StemGesture#CUT_SEC} = 500 ms and whose shapes are the harness's own. The fusion's
 * gesture is a different thing (three fixed bars, 80 ms splices, per-row silence) and its shape is
 * fixed by the spec, so it is written here; what is reused is {@link StemGesture#Stem} (the model's
 * row order), {@link StemGesture#barLines} (via the caller's own bar-line computation) and
 * {@link StemGesture#CUT_SEC}'s sibling idea of an equal-power splice.
 */
public final class StemFusion {

    /** How many bars of the incoming's grid the fusion lasts — the window the whole table lives
     *  in. Three, because the table has three states (both backings, the drums swapped, the low
     *  end swapped) and each one needs a bar to be heard as a state rather than as an edit. */
    public static final int FUSION_BARS = 3;

    /** How long each unity/0 change takes. 80 ms: long enough not to click, short enough that the
     *  change is heard as an edit on the line rather than as a fade — the whole point of the
     *  round. Deliberately NOT {@link StemGesture#CUT_SEC} (500 ms), which is Folia's cut. */
    public static final long CUT_MS = 80L;

    /** The most of the outgoing track that may be separated for one fusion, ms — the separation
     *  window, which is the material the carry needs ({@link #sourceSpanMs}) plus the lead the
     *  take's own alignment wants. 12 s, and it is the clause a slow track fails: at a 3 s bar of
     *  the incoming's grid the window is already 8 s, so the bound bites around a 50 BPM pair. */
    public static final long FUSION_TAIL_MAX_MS = 12_000L;

    /** The worst tempo error the two grids may have and still be fused, as a fraction of the
     *  incoming's beat <em>as heard</em>: {@code |aPeriod - bPeriod/speed| / (bPeriod/speed)}.
     *
     *  <p>This is the clause that makes "cut on the bar line" mean something. Without the lock the
     *  two grids slide against each other by one beat-period error per beat, so every bar-line cut
     *  lands off the beat — measured on the prototype: a 2:1 pair at a 2% error slides 63 ms by
     *  {@code swapMs} and 188 ms by {@code fusionEndMs}, which is a whole off-beat edit. 2% is the
     *  number the measurement supports; a pair that does not lock gets <b>no fusion</b> and today's
     *  edit, however tempting its material looks. */
    public static final double LOCK_TOLERANCE = 0.02d;

    /** The incoming's own backing outside the fusion window: unity. The edit is the master there,
     *  so the bed it plays over is the track as it is. */
    public static final double BED_DB = 0d;

    /** The outgoing's melodic row inside the fusion: -9 dB. It is the one carried row that is not
     *  an element of the groove, so it sits under the two backings instead of competing with
     *  them — and it is the first thing dropped if it turns out to be the outgoing's voice.
     *
     *  <p>⚠️ -9 and not -12 (round 3). The listening report is what put it back: on the pair the
     *  user heard, the junction's own bar is a pad-only breakdown of the outgoing track, so the
     *  melodic row is the <em>only</em> thing the fusion carries there, and taking another 3 dB off
     *  it put ~3 dB more into the junction's step — half of the 4 dB of that step which the table
     *  itself owned. The junction search below is what stops that situation arising at all (it
     *  prefers a bar line where the groove is playing); this is what the level should be when the
     *  melodic row does have to carry a passage on its own. */
    public static final double A_OTHER_DB = -9d;

    /**
     * The most the fusion's outgoing-side material may be lifted, dB (round 3).
     *
     * <p><b>Why a make-up gain exists at all.</b> The fusion deliberately removes the outgoing
     * track's <em>voice</em> — that is the user's rule for the whole transition (「避免过渡时出现人声」)
     * — and on a passage where the voice was the energy, the instrumental that is left is quieter
     * than the level the listener was just hearing. Removing the voice must not be heard as the
     * <em>music</em> dropping: a step of several dB at a bar line is exactly the 卡顿 the user
     * reported, and it is the level the listener is on, not the material, that the ear notices.
     * So the renderer measures the step it already reports ({@link #STEP_WINDOW_MS} of the fusion
     * against {@link #STEP_WINDOW_MS} of the outgoing's own master) and lifts <b>the outgoing's
     * carried rows only</b> by that difference — never the incoming's rows, and never anything
     * outside the fusion window — before the head's limiter, so what the limiter holds is the
     * passage at the level it is actually played at.
     *
     * <p>Bounded because the number is measured on material, not designed: an unbounded make-up
     * would let a fusion whose junction is nearly silent be amplified into a passage that is
     * nothing like either track. {@link #makeupDb} clamps it, and the acceptance then re-checks
     * the step clause on the result as it always did — a fusion whose junction needs more than
     * this is refused rather than played as a drop.
     */
    public static final double MAKEUP_MAX_DB = 8d;

    /**
     * The make-up gain one measured junction step asks for, dB: the reduction the step measured,
     * and nothing when the fusion is already at (or above) the outgoing's own level.
     *
     * <p>Only ever a lift: a fusion that arrives <em>louder</em> than the level the listener was
     * at is a swell, not a drop, and the answer to it is the level table (or the limiter), not a
     * reciprocal gain that would pull the outgoing's own material down. Clamped to
     * {@link #MAKEUP_MAX_DB}.
     *
     * @param stepDb the measured step ({@link Report#junctionStepDb}), or NaN when it could not be
     *               measured — which asks for no make-up, because there is nothing to answer
     */
    public static double makeupDb(double stepDb) {
        if (Double.isNaN(stepDb) || stepDb >= 0d) return 0d;
        return Math.min(MAKEUP_MAX_DB, -stepDb);
    }

    /** The outgoing's carried rows with a uniform gain on them — the make-up gain, applied before
     *  the head is rendered (and therefore before its limiter). Every row in {@code carried} is
     *  the outgoing's (see {@link #carry}), so a uniform gain here is A's rows and only A's. */
    public static float[][][] applyMakeup(float[][][] carried, double makeupDb) {
        if (carried == null || makeupDb == 0d) return carried;
        double gain = linear(makeupDb);
        float[][][] out = new float[carried.length][][];
        for (int row = 0; row < carried.length; row++) {
            if (carried[row] == null) continue;
            out[row] = new float[carried[row].length][];
            for (int ch = 0; ch < carried[row].length; ch++) {
                if (carried[row][ch] == null) continue;
                out[row][ch] = new float[carried[row][ch].length];
                for (int i = 0; i < carried[row][ch].length; i++) {
                    out[row][ch][i] = (float) (gain * carried[row][ch][i]);
                }
            }
        }
        return out;
    }

    /** The incoming's own melodic bed inside the fusion: unity, which is where it arrives.
     *  Its drums and bass are silent until their own swaps, so the bed is what the incoming
     *  contributes during the first two bars — and it is the thing that keeps the listener's sense
     *  of level continuous while the outgoing track's voice leaves. */
    public static final double B_BED_DB = 0d;
    
    /** How long the incoming's bed takes to arrive, in bars of the incoming's own grid: one —
     *  the bar the outgoing's drums leave on. Equal-power (the arriving side of a splice), from
     *  {@link Plan#entryMs} to unity. */
    public static final int BED_FADE_BARS = 1;

    /** The window the junction's step is measured over, ms, and the most it may be, dB.
     *
     *  <p>500 ms and not 100: the deck-level changeover ({@code FadeCurve.FUSION}'s 300 ms
     *  equal-gain hand-over between the outgoing deck and this file) is part of what the listener
     *  hears, so the measurement has to contain it. 3.5 dB is what the measurement of the real
     *  prototype's junction leaves room for: a clamped-at-the-bus head measured −3.06 dB there
     *  (and clipped 1352 samples), a statically divided one −6.80 dB, so the limiter has to land
     *  inside 3.5 and the clause is what refuses a pairing it cannot hold. */
    public static final long STEP_WINDOW_MS = 500L;
    public static final double JUNCTION_STEP_MAX_DB = 3.5d;

    /** The window the "the carry is still there at its own last cut" clause is measured over, ms:
     *  the stretch of the outgoing's material immediately before {@code bassMs}. A take that ended
     *  early (the round-18 defect the material window's derivation fixes) shows up as silence
     *  exactly here, where every other level clause is measured over a span long enough to average
     *  it away. */
    public static final long CARRY_END_WINDOW_MS = 500L;

    /** Beats to a bar — the same assumption every bar-line use in this codebase makes. */
    public static final int BEATS_PER_BAR = DjEdit.BEATS_PER_BAR;

    /** How far the outgoing deck is cut back from the end of its file before the bar line is
     *  chosen: {@code aDur - CUT_BACK_MS - blendMs}. 250 ms is the boundary's own tail guard
     *  (the ramp has to finish inside the track), so the junction is the last bar line that can
     *  still carry a whole blend of tail behind it. */
    public static final long CUT_BACK_MS = 250L;

    /** The lead on either side of the junction target that the separated material carries, ms —
     *  the slack the take's own alignment wants: the junction is a bar line of the outgoing's own
     *  grid within ±1 bar of the target and cannot be known before that grid is measured, so the
     *  window a render separates has to be allowed to start before the target and to run past the
     *  passage's own end. 1000 ms is more than one beat of any grid the cost bound admits (a bar
     *  of at most 2.8 s has beats of at most 700 ms), so the junction's own bar is always inside
     *  the material — which is what the "is its voice quiet here" preference is asked about. */
    public static final long A_TAIL_SLACK_MS = 1_000L;

    /**
     * How much of the outgoing track's OWN file the carry needs, ms.
     *
     * <p>Derived from the gesture and not from a bar count: the table keeps A's material in the
     * file over {@code [entryMs, bassMs + CUT_MS]} — the last cut is the low end's, and the splice
     * that makes it is {@link #CUT_MS} long — which is {@code 2*bBar} of the incoming's file, i.e.
     * {@code 2*bBar/speed} ms of the outgoing's own (the deck plays the file {@code speed} times as
     * fast, so material stretched by {@code speed} comes out at the outgoing's tempo). Rounding up
     * so the splice's own tail is covered.
     *
     * <p>⚠️ The spec used to say {@code 3*aBar}, on the assumption that the lock holds. On a pair
     * whose grids are not in a 1:1 relation that is not the same number: measured on real material
     * it was 1.8 s <em>short</em> on one pairing — A's bed would have died on a hard cut the table
     * never names, which is exactly the unplanned edit this transition may not have — and 2 s too
     * generous on another. {@code bassMs} is the instant that matters, so it is what this is
     * computed from; {@link #materialWindowMs} adds the lead, and the acceptance asserts the
     * carried rows are still there right up to {@code bassMs}.
     */
    public static long sourceSpanMs(double bBeatMs, double speed) {
        if (!(bBeatMs > 0d)) return 0L;
        double ratio = speed > 0d ? speed : 1d;
        return (long) Math.ceil((2d * bBeatMs * BEATS_PER_BAR + CUT_MS) / ratio);
    }

    /** The smallest window one render has to SEPARATE for a fusion, ms: {@link #sourceSpanMs}'s
     *  material plus {@link #A_TAIL_SLACK_MS} of lead on either side of the target, which is what
     *  the take's alignment needs. The plan's own window adds the junction search's band on top of
     *  it (see {@link #JUNCTION_SEARCH_BARS}) and is capped at {@link #FUSION_TAIL_MAX_MS}; the
     *  validity clause refuses a pair whose carry alone does not fit inside that cap, because no
     *  window could hold it however narrow the search. */
    public static long materialWindowMs(double bBeatMs, double speed) {
        return sourceSpanMs(bBeatMs, speed) + 2L * A_TAIL_SLACK_MS;
    }

    /** How far the two grids are apart, as a fraction of the incoming's beat <em>as heard</em>:
     *  {@code |aPeriod - bPeriod/speed| / (bPeriod/speed)}. 0 when the tempo lock is exact. */
    public static double lockError(double aBeatMs, double bBeatMs, double speed) {
        double heardB = bBeatMs / (speed > 0d ? speed : 1d);
        if (!(heardB > 0d)) return Double.MAX_VALUE;
        return Math.abs(aBeatMs - heardB) / heardB;
    }

    /** Whether the two grids are actually locked, and the bar lines will not slide against each
     *  other over the passage's three bars: {@link #LOCK_TOLERANCE}. */
    public static boolean locked(double aBeatMs, double bBeatMs, double speed) {
        return lockError(aBeatMs, bBeatMs, speed) <= LOCK_TOLERANCE;
    }

    /** How much past the passage the decode for the junction estimate reaches, ms: the estimate
     *  is over the mix's low band and wants a little material on either side of the grid it is
     *  looking for, which is cheap (a decode) where the separation is not. */
    public static final long A_TAIL_LEAD_MS = 1_000L;

    /** The worst phase match the fusion still accepts, ms. Past it the entry falls back to the
     *  first bar line at or after the content start rather than being placed on a match that is
     *  not one. */
    public static final long PHASE_MATCH_MS = 120L;

    /** How far either side of the junction target the search may look, in bars of the outgoing's
     *  own grid (round 3: it was ±1 bar, i.e. "the nearest line, with a ±1 s preference").
     *
     *  <p>Widened because of what the user heard: on the pair that was listened to, the nearest
     *  line to the target is a pad-only breakdown of the outgoing track (its own drums −40 dBFS,
     *  its low end −83) — nothing of that track's groove is there to carry, so the fusion's first
     *  bar could only ever be its pad and the incoming's bed, several dB under the level the
     *  listener was on. Two bars either way is what it takes to reach back into a bar where the
     *  outgoing was still playing.
     *
     *  <p>A junction this far from the target changes the ramp by that much — the ramp is
     *  {@code aDur − 250 − junctionMs} — which is acceptable and is logged; and the band is paid
     *  for out of {@link #FUSION_TAIL_MAX_MS}, so a slow track's search is narrower rather than
     *  its separation being longer. */
    public static final int JUNCTION_SEARCH_BARS = 2;

    /** The floor a bar's own low end has to be at, dBFS, for that bar to count as carrying the
     *  outgoing track's groove — the median of the 25 ms frames of its separated drums + bass over
     *  a whole beat.
     *
     *  <p>−25 dBFS, and the number is measured rather than chosen: on the pair the user listened
     *  to, the four bar lines within the search band read <b>−14.2 / −16.9 / −20.0</b> where the
     *  track's groove is playing, <b>−29.6 / −35.7 / −41.5</b> at the junction the ear caught (a
     *  pad-only breakdown), and −60.7 / −79.8 / −82.9 two bars later (silence). The floor sits in
     *  the 10 dB gap between "playing" and "the tail of something that has stopped": a first
     *  attempt at −45 dBFS called the breakdown's tail "playing" and so never reordered anything,
     *  which is the trap this constant's own doc is here to keep a later reader out of. */
    public static final double GROOVE_FLOOR_DBFS = -25d;

    /** How far above the window's own median a frame has to be to count as an <b>attack</b> in the
     *  pulse clause, dB.
     *
     *  <p>⚠️ Round 4 measured this clause on a pair a real device refused — {@code 1460801818 ->
     *  34364062}, whose only failing clause was this one ("the passage's own pulse has a 1676.0ms
     *  hole with a 419.0ms period") — and the verdict was <b>right</b>, which is why the clause is
     *  unchanged. The numbers, from the device's own audio, per beat of the outgoing's separated
     *  rows over the passage the table carries: its drums peak at <b>−28.2 / −24.6 dBFS</b> on the
     *  first two beats (42 dB above their own median, −70.6) and then read <b>−71.7 / −74.7 /
     *  −65.1 / −69.8 / −71.6 / −66.5 / −66.4</b> for the rest — the drums hit twice and stop; its
     *  bass is at the floor for the whole passage (−70.5 peak, −79.7 median); the incoming's own
     *  drums are at −62 dBFS in its intro and its bass is a sustained line (−13 dBFS with per-beat
     *  peaks 0.5–2 dB above their own medians). So the passage has two beats of rhythm and then
     *  ~2 s with none in either backing, which is exactly the hazard this clause exists for (an
     *  outgoing whose rhythm has stopped), and the 5-beat hole it reports is the material's.
     *
     *  <p>What the calibration <em>did</em> show is that the floor is measured against the whole
     *  window's mixture, so a sparse outgoing bar is judged against the incoming's louder later
     *  material. Under a per-source floor (each source's rhythm rows against their own median,
     *  either counting as an attack) the same material still leaves a 2-beat hole — 841 ms against
     *  the 673 ms this clause allows — so the verdict is the same either way and the change was not
     *  made. A reader changing this should start from those two numbers. */
    public static final double PULSE_ATTACK_DB = 6d;

    /** The floor a decoded window's low band has to be above for its downbeat to be estimated at
     *  all, dBFS. Under it there is nothing for the estimate to find, and the four candidate
     *  phases score the same nothing. */
    public static final double LOW_BAND_FLOOR_DBFS = -60d;

    /** How far below the material it was taken from a carried row may sit, dB. The same 3 dB
     *  {@link StemBridge} uses, so "the carry is the material" means one thing in both places. */
    public static final double CARRY_TOLERANCE_DB = StemBridge.CARRY_TOLERANCE_DB;

    /** How much of the outgoing's own vocal stem a carried row may be, at zero lag: above this
     *  what was carried is the outgoing's voice, which is the one thing no passage may contain. */
    public static final double VOICE_CARRY_LIMIT = StemBridge.VOICE_CARRY_LIMIT;

    /** The floor a stem row has to be under to count as absent, dBFS — {@link
     *  DjEdit#SILENT_FRAME_DBFS} again, so "silent" means the same everywhere. */
    public static final double SILENT_DBFS = DjEdit.SILENT_FRAME_DBFS;

    /** The longest run of clamped samples the fusion may contain, ms: a splice may land on a
     *  peak, but a passage that is clipping for longer than this is a passage mixed too loud. */
    public static final double CLIP_RUN_MAX_MS = 2d;

    /** The largest share of the fusion window's sample pairs the clamp may have touched. */
    public static final double CLIP_SHARE_MAX = 0.01d;

    private StemFusion() {}

    // --- the outgoing's voice, where the junction is chosen -------------------

    /** Whether the outgoing track's voice is quiet over some whole beat at a position — the test
     *  the junction preference is written in ("the outgoing's voice should stop between phrases
     *  rather than mid-word"). */
    public interface VocalQuiet {
        /** True when a whole beat of {@code beatMs} inside {@code [atMs - beatMs, atMs + beatMs)}
         *  has the outgoing's voice at the floor. */
        boolean quietBeatAround(long atMs, double beatMs);
    }

    /** No measurement: nothing is preferred, the nearest bar line wins. */
    public static final VocalQuiet NO_VOICE_MEASUREMENT = (atMs, beatMs) -> false;

    /**
     * Whether the outgoing track's own <b>groove</b> — its separated drums and low end — is
     * playing around a position: the junction preference that decides whether a bar line is one
     * the fusion can carry the outgoing track's rhythm out of.
     *
     * <p>The pair the user heard is the reason this exists: its nearest bar line to the target is
     * a breakdown, so the fusion carried a pad where a groove should have been and the junction
     * measured −7 dB. A junction on a bar with a groove is the whole difference between "the
     * outgoing track hands over mid-song" and "the outgoing track's music stops and something
     * else starts".
     */
    public interface Groove {
        /** True when a whole beat of {@code beatMs} inside
         *  {@code [atMs - beatMs, atMs + 2*beatMs)} has the outgoing's drums + low end above the
         *  floor. */
        boolean presentAround(long atMs, double beatMs);
    }

    /** No measurement: nothing is preferred, and the quiet-vocal preference (then the nearest
     *  line) decides — which is what the junction search did before round 3. */
    public static final Groove NO_GROOVE_MEASUREMENT = (atMs, beatMs) -> false;

    /**
     * A {@link Groove} over the outgoing's separated drums and bass: the per-frame levels of their
     * sum, in the stem's own timeline — the same instrument {@link #quietnessOf} uses on the vocal
     * row, asked about the low end instead of the voice.
     *
     * <p>Both rows are summed rather than judged separately: a bar is one the fusion can carry the
     * outgoing's rhythm out of if the kick <em>or</em> the bass line is there (a breakdown often
     * keeps one), and a bar where neither is playing is the case this is looking for. The measure
     * is the <em>median</em> frame of a whole beat rather than its mean or its peak: the peak of a
     * kick-less bar can still be an attack from something else, and the mean of a sparse kick
     * pattern is the level between the kicks.
     *
     * @param drums     the outgoing's separated drum stem, {@code [channel][sample]}
     * @param bass      the outgoing's separated low end
     * @param rate      their sample rate
     * @param startMs   where those stems start in the outgoing track's own file, ms
     * @param floorDbfs the level a beat of the two rows has to sit above
     */
    public static Groove grooveOf(float[][] drums, float[][] bass, int rate, long startMs,
                                  double floorDbfs) {
        float[][] kit = add(drums, bass);
        if (kit == null || kit.length == 0 || kit[0] == null) return NO_GROOVE_MEASUREMENT;
        double[] levels = StemBridge.frameLevelsDb(kit, rate, kit[0].length / (double) rate);
        int frameMs = Math.max(1, (int) Math.round(StemBridge.FRAME_SEC * 1000d));
        return (atMs, beatMs) -> {
            if (levels.length == 0 || !(beatMs > 0d)) return false;
            int frames = (int) Math.max(1L, Math.round(beatMs / frameMs));
            // The beat that ends at `at`, the one that starts at it, and the one after it: a
            // groove is heard across a bar line, and a bar whose own first beat is a rest still
            // has the rest of itself.
            for (long start = atMs - Math.round(beatMs); start <= atMs + Math.round(beatMs);
                 start += frameMs) {
                int from = (int) Math.round((start - startMs) / (double) frameMs);
                if (from < 0 || from + frames > levels.length) continue;
                double[] beat = new double[frames];
                System.arraycopy(levels, from, beat, 0, frames);
                if (StemBridge.median(beat) > floorDbfs) return true;
            }
            return false;
        };
    }

    /**
     * A {@link VocalQuiet} over a separated vocal row: the per-frame levels of the stem, in the
     * stem's own timeline.
     *
     * @param vocals    the outgoing's separated vocal stem, {@code [channel][sample]}
     * @param rate      its sample rate
     * @param startMs   where that stem starts in the outgoing track's own file, ms
     * @param floorDbfs the level a frame has to be at or below to count as the voice being quiet
     */
    public static VocalQuiet quietnessOf(float[][] vocals, int rate, long startMs,
                                         double floorDbfs) {
        double[] levels = StemBridge.frameLevelsDb(vocals, rate,
                vocals != null && vocals.length > 0 && vocals[0] != null
                        ? vocals[0].length / (double) rate : 0d);
        int frameMs = Math.max(1, (int) Math.round(StemBridge.FRAME_SEC * 1000d));
        return (atMs, beatMs) -> {
            if (levels.length == 0 || !(beatMs > 0d)) return false;
            long beats = Math.max(1L, Math.round(beatMs / frameMs));
            // The beat that ends at `at` covers [at - beatMs, at); the one that starts at it
            // covers [at, at + beatMs). Both are tried: the cut is heard on both sides of itself
            // (the outgoing deck's last moment before it, the carried passage after it), and the
            // spec asks for "at least one beat" of quiet.
            for (long start = atMs - Math.round(beatMs); start <= atMs; start += frameMs) {
                int from = (int) Math.round((start - startMs) / (double) frameMs);
                if (from < 0 || from + beats > levels.length) continue;
                boolean quiet = true;
                for (int f = 0; f < beats; f++) {
                    if (levels[from + f] > floorDbfs) {
                        quiet = false;
                        break;
                    }
                }
                if (quiet) return true;
            }
            return false;
        };
    }

    /**
     * Whether the outgoing's melodic row, over the passage the fusion would carry, IS the
     * outgoing's voice — the measurement behind dropping the melodic carry.
     *
     * <p>Measured as the correlation with the separated vocal row at zero lag over the very
     * material that would be carried, not as a level: the outgoing track's voice is all over the
     * bars the material comes from, so a level says nothing, while a melodic row that is the
     * voice aligns with it almost exactly. The same instrument and the same limit {@link
     * StemBridge} uses for its carried bass ({@link StemBridge#VOICE_CARRY_LIMIT}).
     */
    public static double voiceAlignment(float[][] outgoingOther, float[][] outgoingVocals,
                                        int rate, long fromMs, long spanMs) {
        int from = (int) Math.max(0L, Math.round(fromMs * rate / 1000d));
        int frames = (int) Math.round(spanMs * rate / 1000d);
        float[][] other = slice(outgoingOther, from, frames);
        float[][] vocals = slice(outgoingVocals, from, frames);
        return Math.abs(StemBridge.alignmentAtZeroLag(other, vocals, rate, spanMs / 1000d, null));
    }

    // --- the plan -------------------------------------------------------------

    /** Everything the planner needs. The two bar-line arrays are ABSOLUTE file times (ms) of
     *  each track's own grid, computed by the caller from separated material — never the beat
     *  grid's phase, which is a different question and wrong three times in four
     *  ({@link StemGesture#barLines}). */
    public static final class Input {
        /** The outgoing track's file duration, probed by the renderer. */
        public final long aDurMs;
        /** The boundary's blend length at request time. */
        public final long blendMs;
        /** How long the incoming plays with its vocals out (the user's blend plus the head the
         *  deck skips before it). */
        public final long removalMs;
        /** Where the incoming deck would start playing without a fusion. */
        public final long contentStartMs;
        public final double aBeatMs;
        public final double aPhaseMs;
        public final double bBeatMs;
        public final double bPhaseMs;
        /** The ratio the incoming deck plays the file at. */
        public final double speed;
        /** The outgoing's bar lines, absolute ms, from its separated material. */
        public final double[] aBarLinesMs;
        /** The incoming's bar lines, absolute ms, from its separated head. */
        public final double[] bBarLinesMs;
        /** The outgoing's separated voice, for the junction preference; never null. */
        public final VocalQuiet quiet;
        /** The outgoing's separated drums + low end, for the junction preference; never null. */
        public final Groove groove;

        public Input(long aDurMs, long blendMs, long removalMs, long contentStartMs,
                     double aBeatMs, double aPhaseMs, double bBeatMs, double bPhaseMs, double speed,
                     double[] aBarLinesMs, double[] bBarLinesMs, VocalQuiet quiet) {
            this(aDurMs, blendMs, removalMs, contentStartMs, aBeatMs, aPhaseMs, bBeatMs, bPhaseMs,
                    speed, aBarLinesMs, bBarLinesMs, quiet, NO_GROOVE_MEASUREMENT);
        }

        public Input(long aDurMs, long blendMs, long removalMs, long contentStartMs,
                     double aBeatMs, double aPhaseMs, double bBeatMs, double bPhaseMs, double speed,
                     double[] aBarLinesMs, double[] bBarLinesMs, VocalQuiet quiet, Groove groove) {
            this.aDurMs = aDurMs;
            this.blendMs = blendMs;
            this.removalMs = removalMs;
            this.contentStartMs = contentStartMs;
            this.aBeatMs = aBeatMs;
            this.aPhaseMs = aPhaseMs;
            this.bBeatMs = bBeatMs;
            this.bPhaseMs = bPhaseMs;
            this.speed = speed > 0d ? speed : 1d;
            this.aBarLinesMs = aBarLinesMs;
            this.bBarLinesMs = bBarLinesMs;
            this.quiet = quiet == null ? NO_VOICE_MEASUREMENT : quiet;
            this.groove = groove == null ? NO_GROOVE_MEASUREMENT : groove;
        }
    }

    /** What a fusion would be, and whether it fits: the anchors the render bakes into the file
     *  name, plus where the outgoing's tail has to be separated for it. */
    public static final class Plan {
        /** True when every validity clause held — otherwise nothing about this plan may be used
         *  and the render is exactly today's edit. */
        public final boolean valid;
        /** Why not, for the log. Empty when {@link #valid}. */
        public final String reason;

        /** One bar of each grid, ms. */
        public final double aBarMs;
        public final double bBarMs;

        /** The outgoing's bar line its deck is cut on, its own file ms. */
        public final long junctionMs;
        /** The incoming's bar line its deck starts playing on — where the fusion begins, its own
         *  file ms. */
        public final long entryMs;
        /** {@code entryMs + 1*bBar}: the outgoing's drums out and the incoming's in. */
        public final long swapMs;
        /** {@code entryMs + 2*bBar}: the low end changes hands. */
        public final long bassMs;
        /** {@code entryMs + 3*bBar}: the last of the outgoing's material is gone. */
        public final long fusionEndMs;
        /** Where the incoming's own bed has arrived at unity: {@code entryMs + bBar}, which is
         *  {@link #swapMs} — the bar the outgoing's drums leave on. */
        public final long bedFadeMs;

        /** The fusion window's length in the file, ms ({@code 3*bBar}). */
        public final long windowMs;
        /** The outgoing's own timeline the carried passage covers, ms
         *  ({@code 2*bBar/speed + CUT_MS/speed}), rounded up: the material A's rows occupy in the
         *  file, from the junction to its last cut. */
        public final long sourceSpanMs;

        /** Where the outgoing's material is taken from in its own file (= {@link #junctionMs}). */
        public final long sourceFromMs;
        /** Where the outgoing's tail has to be separated from, ms, and how much of it: the search
         *  band's own lead on either side of the target plus the whole passage. In the plan's own
         *  timeline this is the window for one {@code separateTail} call. */
        public final long materialFromMs;
        public final long materialWindowMs;
        /** How far either side of the target the junction was searched, ms: {@link
         *  #JUNCTION_SEARCH_BARS} bars of the outgoing's grid, narrowed to what
         *  {@link #FUSION_TAIL_MAX_MS} has left after the carry's own material. */
        public final long searchBandMs;
        /** {@code junctionMs} minus the target ({@code aDur − 250 − blendMs}), ms: negative when
         *  the deck is cut earlier than distance alone would put it, which lengthens the ramp by
         *  the same amount. Reported, never a requirement. */
        public final long junctionShiftMs;

        /** The ratio the incoming deck plays this file at — and the ratio the outgoing's carried
         *  material was stretched by on the way in, which is the same number by construction (see
         *  {@link #carried}): playback at this ratio returns the carry to the outgoing track's own
         *  tempo and pitch.
         *
         *  <p>A field because it is what a log line has to quote, and quoting something else is
         *  how the round-3 line came to print a ratio of 1.4649 for a pair whose deck played at
         *  x1.0: {@code windowMs / sourceSpanMs} is <em>three bars of the incoming's grid over two
         *  bars plus a splice of the outgoing's</em>, a bar-count ratio that is above 1 on every
         *  pair and means nothing. */
        public final double speed;

        /** How far the two grids are apart as heard, as a fraction of the incoming's beat
         *  ({@link #lockError}); at most {@link #LOCK_TOLERANCE} in every valid plan. */
        public final double lockError;

        /** The junction's own bar was at least a beat without the outgoing's voice. */
        public final boolean quietAtJunction;
        /** The junction's own bar had the outgoing's drums or low end playing — the preference that
         *  decides whether the fusion carries a groove or a pad. */
        public final boolean grooveAtJunction;
        /** The entry was the phase-matched bar line rather than the fallback. */
        public final boolean phaseMatched;
        /** The worst wall-clock phase difference at the seam, ms. */
        public final double phaseErrorMs;

        Plan(boolean valid, String reason, double aBarMs, double bBarMs, long junctionMs,
             long entryMs, long swapMs, long bassMs, long fusionEndMs, long windowMs,
             long sourceSpanMs, long materialFromMs, long materialWindowMs, long searchBandMs,
             long junctionShiftMs, double lockError, double speed, boolean quietAtJunction,
             boolean grooveAtJunction, boolean phaseMatched, double phaseErrorMs) {
            this.valid = valid;
            this.reason = reason == null ? "" : reason;
            this.aBarMs = aBarMs;
            this.bBarMs = bBarMs;
            this.junctionMs = junctionMs;
            this.entryMs = entryMs;
            this.swapMs = swapMs;
            this.bassMs = bassMs;
            this.fusionEndMs = fusionEndMs;
            this.bedFadeMs = valid ? swapMs : -1L;
            this.windowMs = windowMs;
            this.sourceSpanMs = sourceSpanMs;
            this.sourceFromMs = junctionMs;
            this.materialFromMs = materialFromMs;
            this.materialWindowMs = materialWindowMs;
            this.searchBandMs = searchBandMs;
            this.junctionShiftMs = junctionShiftMs;
            this.lockError = lockError;
            this.speed = speed > 0d ? speed : 1d;
            this.quietAtJunction = quietAtJunction;
            this.grooveAtJunction = grooveAtJunction;
            this.phaseMatched = phaseMatched;
            this.phaseErrorMs = phaseErrorMs;
        }

        /** How many 80 ms splices the table contains: three at {@link #swapMs} (the outgoing's
         *  drums out and the incoming's in, one pair) and three at {@link #bassMs}. The incoming's
         *  bed is deliberately NOT one of them — it is the one arrival the design ramps, over
         *  {@code BED_FADE_BARS} bar, and it is counted separately in {@link #describe()}. */
        public int splices() {
            return 2 * 3;
        }

        /** The evidence, in one line. */
        public String describe() {
            if (!valid) return "no fusion (" + reason + ")";
            return String.format(Locale.US,
                    "fusion: A cut on its bar line at %dms (%+dms from the distance alone; the"
                            + " search covered +/-%dms), B starts at %dms (%.0f ms of wall-clock"
                            + " phase difference %s, the grids locked to %.2f%%); drums swap at %dms,"
                            + " low end at %dms, all A gone at %dms (%dms = %d bars of %.0fms); B's"
                            + " bed fades in over one bar, from %dms to unity at %dms; the"
                            + " junction's bar is %s and %s; the deck plays this file at x%.4f and"
                            + " the outgoing's carry was stretched by that same x%.4f inside it, so"
                            + " %dms of A taken from %dms fills %.0fms of the file's %dms window,"
                            + " separated over %dms from %dms",
                    junctionMs, junctionShiftMs, searchBandMs, entryMs, phaseErrorMs,
                    phaseMatched ? "matched to A's grid" : "NOT matched (the plain bar line)",
                    lockError * 100d, swapMs, bassMs, fusionEndMs, windowMs, FUSION_BARS, bBarMs,
                    entryMs, bedFadeMs,
                    quietAtJunction ? "voice-free for a beat" : "not measured voice-free",
                    grooveAtJunction ? "carrying the outgoing's groove"
                            : "NOT measured carrying a groove",
                    speed, speed, sourceSpanMs, sourceFromMs, sourceSpanMs * speed, windowMs,
                    materialWindowMs, materialFromMs);
        }
    }

    private static Plan invalid(String reason, double aBarMs, double bBarMs) {
        return invalid(reason, aBarMs, bBarMs, Double.NaN);
    }

    private static Plan invalid(String reason, double aBarMs, double bBarMs, double lockError) {
        return new Plan(false, reason, aBarMs, bBarMs, -1L, -1L, -1L, -1L, -1L, 0L, 0L, 0L, 0L, 0L,
                0L, lockError, 1d, false, false, false, Double.NaN);
    }

    /**
     * Whether a fusion is worth decoding the outgoing track's tail for, and where to look.
     *
     * <p>The plan's own anchors need the outgoing's bar lines, and those come from its own audio
     * (the low-end extension every bar-line use in this codebase has instead of a structure pass).
     * What they do <em>not</em> need is the separated stems: the mix's own low band answers the
     * question for a decode, and a decode costs a fraction of a separation. Getting the junction
     * this way is what keeps the window that <em>is</em> separated — the expensive half — at
     * {@code sourceSpan + A_TAIL_SLACK_MS}, the size the spec's validity clause is written for,
     * instead of growing it by a bar of uncertainty for every pair.
     *
     * <p>A false answer means the fusion is not attempted at all and the render is today's edit.
     */
    public static boolean possible(long aDurMs, long blendMs, long removalMs, long contentStartMs,
                                   double aBeatMs, double bBeatMs, double speed) {
        return refusal(aDurMs, blendMs, removalMs, contentStartMs, aBeatMs, bBeatMs, speed) == null;
    }

    /**
     * The clause a fusion is refused by before anything is decoded, or null when one is worth
     * decoding the outgoing track's tail for.
     *
     * <p>The reason is the log's, and it names the clause: "the two grids cannot carry one" is not
     * something a later reader can act on, where "the two grids do not lock (49.2% out where the
     * tolerance is 2%)" is.
     *
     * <p>Every clause here is decidable from the request alone, which is which ones they are: the
     * two grids, the tempo lock, the cost bound on the window that would be separated, and the
     * outgoing file being long enough for a junction to exist in it. The ones that need the
     * incoming's own start or its content (where the deck begins, whether the whole fusion fits
     * inside the vocal-free window) are {@link #plan}'s.
     */
    public static String refusal(long aDurMs, long blendMs, long removalMs, long contentStartMs,
                                 double aBeatMs, double bBeatMs, double speed) {
        if (!(aBeatMs > 0d)) return "the outgoing track has no beat grid";
        if (!(bBeatMs > 0d)) return "the incoming track has no beat grid";
        if (!(speed > 0d)) return "the ratio the incoming deck plays at is not known";
        if (!(aDurMs > 0d)) return "the outgoing track's length could not be read";
        if (!(blendMs > 0L)) return "the boundary's blend length is not known";
        if (contentStartMs < 0L) return "the incoming deck's own start is not known";
        if (!locked(aBeatMs, bBeatMs, speed)) {
            return lockReason(aBeatMs, bBeatMs, speed);
        }
        long window = materialWindowMs(bBeatMs, speed);
        if (!(window <= FUSION_TAIL_MAX_MS)) {
            return String.format(Locale.US,
                    "the passage is %.1fms of the incoming's file (%d bars of %.1fms), so the"
                            + " material A's rows need from A is %dms — over the %.0fms one render"
                            + " may separate before the junction search's own band is paid for",
                    2d * bBeatMs * BEATS_PER_BAR, FUSION_BARS, bBeatMs * BEATS_PER_BAR, window,
                    (double) FUSION_TAIL_MAX_MS);
        }
        if (!(Math.round(aDurMs - CUT_BACK_MS - blendMs) > 0L)) {
            return String.format(Locale.US,
                    "the outgoing track ends %dms in, so no bar line of it is a whole blend back"
                            + " from its own end",
                    Math.max(0L, Math.round(aDurMs - CUT_BACK_MS)));
        }
        return null;
    }

    /**
     * A track's own bar lines from its <b>beat grid</b>: {@code phase + k*beatsPerBar*period},
     * absolute ms, over {@code [fromMs, fromMs + windowMs]}.
     *
     * <p>The fallback for a track whose <em>separated low end</em> has nothing to measure a downbeat
     * from — a real one arrived with a device run: {@code 1410815174}'s head has a loud tenth of
     * <b>−68.3 dBFS</b> on its separated bass (under the {@link #LOW_BAND_FLOOR_DBFS} floor this
     * class's own estimate uses), i.e. its first 18 s have no low end at all, so a downbeat
     * estimated from it would be one of four coin flips and every bar line the renderer places from
     * it is a guess dressed as a measurement.
     *
     * <p>The bar <em>grouping</em> is still a guess here ({@link StemGesture#barLines}'s own note:
     * a bar line spaced a bar apart from the beat phase is wrong three times in four) — but the
     * grid's phase and period are <em>measured</em>, so the lines land on the track's beats, which
     * is what the fusion's cuts have to land on (the phase match to the outgoing's grid is asked
     * about the beat phase, not about the bar). A caller that has a measured downbeat should use
     * it; this is what a caller does when it has not.
     */
    public static double[] barLinesOfBeatGrid(double beatMs, double phaseMs, double fromMs,
                                              double windowMs) {
        if (!(beatMs > 0d) || !(windowMs > 0d)) return new double[0];
        double barMs = beatMs * BEATS_PER_BAR;
        double at = phaseMs + Math.floor((fromMs - phaseMs) / barMs) * barMs;
        while (at < fromMs) at += barMs;
        java.util.ArrayList<Double> lines = new java.util.ArrayList<>();
        for (; at <= fromMs + windowMs; at += barMs) lines.add(at);
        double[] out = new double[lines.size()];
        for (int i = 0; i < out.length; i++) out[i] = lines.get(i);
        return out;
    }

    /**
     * The window of the outgoing track to decode for the junction estimate, ms:
     * {@code {fromMs, windowMs}}. Wide enough that whichever bar line the junction turns out to
     * be — the search covers {@link #JUNCTION_SEARCH_BARS} bars either way from the target — the
     * passage that starts there is inside the decoded material. A decode is cheap where a
     * separation is not, so this errs wide.
     */
    public static long[] probeWindow(long aDurMs, double aBeatMs, double bBeatMs, double speed,
                                     long blendMs) {
        double aBar = aBeatMs * BEATS_PER_BAR;
        double band = JUNCTION_SEARCH_BARS * aBar;
        long span = sourceSpanMs(bBeatMs, speed);
        long target = Math.round(aDurMs - CUT_BACK_MS - blendMs);
        long from = Math.max(0L, Math.round(target - band - A_TAIL_SLACK_MS));
        long to = Math.min(aDurMs, Math.round(target + band + span + A_TAIL_LEAD_MS));
        return new long[]{from, Math.max(0L, to - from)};
    }

    /**
     * The window of the outgoing track that has to be SEPARATED for one fusion, ms:
     * {@code {fromMs, windowMs}} — {@link #A_TAIL_SLACK_MS} of lead on either side of the target
     * plus the whole passage. The lead is what the two uncertainties need: the take has to start
     * exactly on the junction, and the junction is a bar line within ±1 bar of the target, so a
     * window that began at the target could miss the material the junction needs by up to a bar.
     * By {@link #plan}'s own cost clause this is at most {@link #FUSION_TAIL_MAX_MS}.
     */
    public static long[] materialWindow(Plan plan) {
        if (plan == null || !plan.valid) return new long[]{0L, 0L};
        return new long[]{plan.materialFromMs, plan.materialWindowMs};
    }

    /**
     * The outgoing track's bar lines, ABSOLUTE ms, from the low band of its <em>decoded</em>
     * audio — the same estimator the rest of this codebase uses ({@link
     * StemGesture#downbeatOffsetSec} over a low-end envelope, then {@link StemGesture#barLines}),
     * fed with the mix's own low end rather than with a separated bass stem.
     *
     * <p>Fed that way for one reason: the separated stems are what this estimate decides the
     * window for. Decoding the outgoing's tail is cheap (a tenth of a second of CPU for the
     * fifteen seconds of audio this looks at), separating it is not (seconds), and the separation
     * has to be told <em>where</em> to run — so the question "where are the outgoing track's bar
     * lines" has to be answerable from a decode. A one-pole low pass is what stands in for the
     * model here; the kick and the bass are what a downbeat is audible on and they are what is
     * under the filter. A heuristic, like every downbeat estimate on this platform, and it is used
     * for exactly one thing: which line of the outgoing's grid its deck is cut on.
     *
     * @param decoded the outgoing's decoded audio over at least a few bars, {@code [ch][sample]}
     * @param rate    its sample rate
     * @param fromMs  where that decoded material starts in the outgoing's own file, ms
     */
    public static double[] barLinesOfLowBand(float[][] decoded, int rate, long fromMs,
                                             double beatMs, double phaseMs) {
        if (decoded == null || decoded.length == 0 || decoded[0] == null || !(beatMs > 0d)) {
            return new double[0];
        }
        float[] envelope = lowBandEnvelope(decoded, rate);
        if (envelope.length < StemGesture.SUSTAIN_CELLS * 2) return new double[0];
        // A window with no low end gives no downbeat: the estimator would otherwise pick one of
        // the four candidate phases on the strength of nothing, and a junction on a coin flip is
        // worse than no fusion (which is today's edit). Asked of the window's loudest tenth rather
        // than of its median: on an ordinary record the kicks are sparse, and a median of the low
        // band is the level between them.
        if (loudTenthDb(envelope) < LOW_BAND_FLOOR_DBFS) return new double[0];
        double fromSec = fromMs / 1000d;
        double windowSec = decoded[0].length / (double) rate;
        Double downbeat = StemGesture.downbeatOffsetSec(envelope, fromSec, StemGesture.CELL_SEC,
                phaseMs / 1000d, beatMs / 1000d, BEATS_PER_BAR);
        if (downbeat == null || Double.isNaN(downbeat)) return new double[0];
        double[] relative = StemGesture.barLines(60d / (beatMs / 1000d), downbeat, BEATS_PER_BAR,
                fromSec, windowSec);
        double[] out = new double[relative.length];
        for (int i = 0; i < out.length; i++) out[i] = fromMs + relative[i] * 1000d;
        return out;
    }

    /** The level the loudest tenth of a window's low-band cells sit above, dBFS — "is there a low
     *  end in this window at all", asked so that a sparse kick pattern does not read as silence.
     *  Public because the renderer asks the same question about the <em>incoming</em> track's head
     *  before it trusts a downbeat estimated from it (see {@link #barLinesOfBeatGrid}). */
    public static double loudTenthDb(float[] envelope) {
        if (envelope == null || envelope.length == 0) return -240d;
        float[] sorted = envelope.clone();
        java.util.Arrays.sort(sorted);
        return DjEdit.db(sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.9d))]);
    }

    /** One-pole low-passed cell RMS of a decoded window, at {@link StemGesture#CELL_SEC} cells:
     *  the low end a downbeat is audible on, without the model. */
    public static float[] lowBandEnvelope(float[][] pcm, int rate) {
        if (pcm == null || pcm.length == 0 || pcm[0] == null) return new float[0];
        int cell = Math.max(1, (int) Math.round(StemGesture.CELL_SEC * rate));
        int length = pcm[0].length;
        int cells = length / cell;
        if (cells <= 0) return new float[0];
        // 150 Hz, the top of what a kick and a bass line have to themselves on a pop record.
        double k = Math.min(1d, 2d * Math.PI * 150d / rate);
        float[] out = new float[cells];
        double state = 0d;
        for (int c = 0; c < cells; c++) {
            double energy = 0d;
            for (int i = c * cell; i < (c + 1) * cell; i++) {
                double frame = 0d;
                for (float[] channel : pcm) {
                    if (i < channel.length) frame += channel[i];
                }
                frame /= pcm.length;
                state += (frame - state) * k;
                energy += state * state;
            }
            out[c] = (float) Math.sqrt(energy / cell);
        }
        return out;
    }

    /**
     * The plan: the anchors, the validity verdict, and where to take the outgoing's material from.
     *
     * <p>The validity list is the spec's own, in its own order — a grid for each track, a known
     * blend, a passage that fits the cost bound, a junction inside the outgoing's file, a known
     * content start, and a whole fusion inside the vocal-free window — plus two the spec leaves
     * to the renderer, both of which only ever <em>refuse</em> a fusion:
     * <ul>
     *   <li>the outgoing's file has to contain the whole passage that would be carried — its deck
     *       is cut at the junction, so material past the end of its file does not exist; and</li>
     *   <li>the window that gets SEPARATED has to fit {@link #FUSION_TAIL_MAX_MS}. That window is
     *       the passage plus {@link #A_TAIL_SLACK_MS} of lead on either side, and the spec's own
     *       clause ({@code 3*aBar + 1000 <= FUSION_TAIL_MAX_MS}) is what that arithmetic comes to
     *       when the tempo lock makes the passage exactly three bars of the outgoing's grid. The
     *       two leads are why this clause, not the spec's, is the operative one: the junction is a
     *       bar line within ±1 bar of the target and cannot be known before the grid is measured,
     *       so a window that began at the target could miss the material the junction needs. The
     *       cost of that honesty is the tempo floor — a passage plus two leads is 11 s at a 3 s
     *       bar, i.e. about 80 BPM in 4/4, where the spec's clause alone would allow 72 BPM.</li>
     * </ul>
     */
    public static Plan plan(Input in) {
        double aBar = in.aBeatMs * BEATS_PER_BAR;
        double bBar = in.bBeatMs * BEATS_PER_BAR;
        double lock = lockError(in.aBeatMs, in.bBeatMs, in.speed);
        long target = Math.round(in.aDurMs - CUT_BACK_MS - in.blendMs);

        if (!(in.aBeatMs > 0d) || in.aBarLinesMs == null || in.aBarLinesMs.length == 0) {
            return invalid("no bar grid for the outgoing track", aBar, bBar);
        }
        if (!(in.bBeatMs > 0d) || in.bBarLinesMs == null || in.bBarLinesMs.length == 0) {
            return invalid("no bar grid for the incoming track", aBar, bBar);
        }
        if (!(in.blendMs > 0L)) {
            return invalid("the boundary's blend length is not known", aBar, bBar);
        }
        // The tempo lock, before anything else that could be true while the grids slide: see
        // LOCK_TOLERANCE. A pair that does not lock gets no fusion however good its material is.
        if (!(in.speed > 0d) || !locked(in.aBeatMs, in.bBeatMs, in.speed)) {
            return invalid(lockReason(in.aBeatMs, in.bBeatMs, in.speed), aBar, bBar, lock);
        }
        // The material the take needs, from `bassMs` and not from a bar count: see sourceSpanMs.
        long windowMs = Math.round(FUSION_BARS * bBar);
        long sourceSpan = sourceSpanMs(in.bBeatMs, in.speed);
        // The junction search's own band, paid for out of the separation budget: the search may
        // look JUNCTION_SEARCH_BARS bars either way, and the window has to hold the whole passage
        // from any line in that band that is still a candidate. A slow track (or a long passage)
        // gets a narrower band rather than a longer separation — the budget is the clause.
        long budget = FUSION_TAIL_MAX_MS - sourceSpan - 2L * A_TAIL_SLACK_MS;
        if (budget < 0L) {
            return invalid(String.format(Locale.US,
                    "the passage is %.1fms of the incoming's file and the material A's rows need"
                            + " from A is %dms, so the window to separate is at least %dms — over"
                            + " the %.0fms one render may separate",
                    2d * bBar, sourceSpan, sourceSpan + 2L * A_TAIL_SLACK_MS,
                    (double) FUSION_TAIL_MAX_MS), aBar, bBar, lock);
        }
        long searchBand = Math.min(Math.round(JUNCTION_SEARCH_BARS * aBar), budget / 2L);
        long materialFrom = Math.max(0L, target - searchBand - A_TAIL_SLACK_MS);
        long materialWindow = sourceSpan + 2L * searchBand + 2L * A_TAIL_SLACK_MS;
        if (in.contentStartMs < 0L) {
            return invalid("the incoming deck's own start is not known", aBar, bBar, lock);
        }

        // The junction: the bar line the deck is cut on, searched within ±JUNCTION_SEARCH_BARS
        // bars of the target and preferred in the order the design names — one where the outgoing's
        // own groove is playing (so the fusion carries a rhythm out of the handover rather than a
        // pad), then one where its voice is quiet for a beat (so the cut is not mid-word), then
        // simply the nearest line. A line whose passage does not fit the material window is not a
        // candidate at all, however good its own bar looks.
        boolean[] quiet = new boolean[1];
        boolean[] groove = new boolean[1];
        long junction = nearestBar(in.aBarLinesMs, target, aBar, materialFrom,
                materialFrom + materialWindow, sourceSpan, quiet, groove, in.quiet, in.groove);
        if (!(junction > 0L)) {
            return invalid(String.format(Locale.US,
                    "no line of the outgoing's grid is inside the %dms the passage can be taken"
                            + " from (the search covers %dms either side of %d outside it)",
                    materialWindow, searchBand, target), aBar, bBar, lock);
        }
        if (junction + sourceSpan > in.aDurMs) {
            return invalid(String.format(Locale.US,
                    "the outgoing file ends %dms into the %dms the fusion's carried rows need from"
                            + " it (they are cut on the bar line at %d of its own file and run to"
                            + " its own low end's last cut)",
                    in.aDurMs - junction, sourceSpan, junction), aBar, bBar, lock);
        }

        // The entry: the incoming's bar line in its own two-bar window whose beat phase, played
        // back at `speed`, lands closest to the outgoing's beat phase at the junction.
        long[] entryChoice = entry(in.bBarLinesMs, in.contentStartMs, bBar, junction, in.aBeatMs,
                in.aPhaseMs, in.bBeatMs, in.bPhaseMs, in.speed);
        long entry = entryChoice[0];
        if (entry < 0L) {
            return invalid("no bar line of the incoming track inside its own two-bar window",
                    aBar, bBar, lock);
        }
        long fusionEnd = entry + windowMs;
        if (fusionEnd > in.removalMs) {
            return invalid(String.format(Locale.US,
                    "the fusion would run %dms past the %dms the incoming's vocals are out for",
                    fusionEnd - in.removalMs, in.removalMs), aBar, bBar, lock);
        }
        return new Plan(true, "", aBar, bBar, junction, entry, entry + Math.round(bBar),
                entry + 2L * Math.round(bBar), fusionEnd, windowMs, sourceSpan, materialFrom,
                materialWindow, searchBand, junction - target, lock, in.speed, quiet[0],
                groove[0], entryChoice[1] == 1L, entryChoice[2] / 1000d);
    }

    /** The lock clause's own words, so {@link #refusal} and {@link #plan} refuse a pair for the
     *  same stated reason. */
    private static String lockReason(double aBeatMs, double bBeatMs, double speed) {
        return String.format(Locale.US,
                "the two grids do not lock: the outgoing's beat is %.1fms and the incoming's"
                        + " %.1fms played at x%.4f is %.1fms as heard, %.1f%% out where the"
                        + " tolerance is %.0f%% — the bar lines would slide against each other and"
                        + " every cut would land off the beat",
                aBeatMs, bBeatMs, speed, bBeatMs / speed, lockError(aBeatMs, bBeatMs, speed) * 100d,
                LOCK_TOLERANCE * 100d);
    }

    /**
     * The bar line the deck is cut on: the best of the lines within
     * {@link #JUNCTION_SEARCH_BARS} bars of the target, ranked by what the bar itself offers.
     *
     * <p>The ranking is the design's, in its own order: a bar where the outgoing's <b>groove</b> is
     * playing wins over one where it is not (the fusion carries the outgoing's rhythm out of the
     * handover — the pair the user heard had no groove at the nearest line and the junction
     * measured −7 dB); among lines of equal groove, one whose <b>voice is quiet</b> for a beat wins
     * (the cut should land between phrases rather than mid-word); and among those, the nearest to
     * the target. A line whose passage falls outside {@code [fitsFrom, fitsTo]} is not a candidate
     * at all — that is the material the render will have — and neither is one outside the band.
     *
     * @param quietOut   single-element out: whether the chosen line had a quiet beat
     * @param grooveOut  single-element out: whether the chosen line had the groove playing
     */
    private static long nearestBar(double[] bars, long target, double aBarMs, long fitsFrom,
                                   long fitsTo, long spanMs, boolean[] quietOut, boolean[] grooveOut,
                                   VocalQuiet quiet, Groove groove) {
        if (quietOut != null && quietOut.length > 0) quietOut[0] = false;
        if (grooveOut != null && grooveOut.length > 0) grooveOut[0] = false;
        long best = -1L;
        int bestRank = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (double bar : bars) {
            long at = Math.round(bar);
            if (Math.abs(at - target) > JUNCTION_SEARCH_BARS * aBarMs + 1e-6d) continue;
            if (at < fitsFrom || at + spanMs > fitsTo) continue;
            boolean hasGroove = groove.presentAround(at, aBarMs / BEATS_PER_BAR);
            boolean isQuiet = quiet.quietBeatAround(at, aBarMs / BEATS_PER_BAR);
            int rank = hasGroove ? 0 : (isQuiet ? 1 : 2);
            double distance = Math.abs(at - target);
            if (rank < bestRank || (rank == bestRank && distance < bestDistance)) {
                best = at;
                bestRank = rank;
                bestDistance = distance;
            }
        }
        if (quietOut != null && quietOut.length > 0) quietOut[0] = best >= 0L && bestRank == 1;
        if (grooveOut != null && grooveOut.length > 0) grooveOut[0] = best >= 0L && bestRank == 0;
        return best;
    }

    /**
     * The incoming's bar line the deck starts on, and how good the phase match is.
     *
     * <p>Both phases are read as the wall-clock time from the nearest beat of their own grid, and
     * they are compared as a <em>circular</em> distance: they are phases of a periodic grid, and
     * the plain difference of two phases 10 ms and 490 ms apart in a 500 ms period is 480 ms by
     * subtraction and 20 ms by what it means. That distinction only shows at all when the tempo
     * lock is off (with {@code speed = periodB/periodA} every bar line of the incoming's grid
     * has the same phase, so every candidate ties and the first one wins either way).
     *
     * @return {@code {entryMs, phaseMatched ? 1 : 0, phaseErrorUs}} — entryMs is -1 when there is
     *         no bar line to place the deck on
     */
    private static long[] entry(double[] bBars, long contentStartMs, double bBarMs, long junctionMs,
                                double aBeatMs, double aPhaseMs, double bBeatMs, double bPhaseMs,
                                double speed) {
        long first = -1L;
        long best = -1L;
        double bestError = Double.MAX_VALUE;
        double phaseA = phaseWall(junctionMs - aPhaseMs, aBeatMs);
        for (double bar : bBars) {
            long at = Math.round(bar);
            if (at < contentStartMs || at >= contentStartMs + Math.round(2d * bBarMs)) continue;
            if (first < 0L || at < first) first = at;
            double phaseB = phaseWall(at - bPhaseMs, bBeatMs) / speed;
            double error = phaseDistance(phaseA, phaseB, aBeatMs);
            if (error < bestError) {
                bestError = error;
                best = at;
            }
        }
        if (best < 0L) return new long[]{-1L, 0L, 0L};
        if (bestError > PHASE_MATCH_MS) {
            // The fallback is the fallback, and it is logged as one: a match that is not a match
            // would put the two grids together by a number nobody measured.
            return new long[]{first, 0L, Math.round(bestError * 1000d)};
        }
        return new long[]{best, 1L, Math.round(bestError * 1000d)};
    }

    /** The distance from the nearest beat of a grid, ms, in {@code [0, period)}. */
    static double phaseWall(double deltaMs, double periodMs) {
        if (!(periodMs > 0d)) return 0d;
        double at = deltaMs % periodMs;
        return at < 0d ? at + periodMs : at;
    }

    /** The circular distance between two phases of the same period, ms. */
    static double phaseDistance(double a, double b, double periodMs) {
        double raw = Math.abs(a - b);
        if (!(periodMs > 0d)) return raw;
        return Math.min(raw, periodMs - raw);
    }

    // --- the gesture ----------------------------------------------------------

    /** Amplitude of {@code db} dBFS. */
    static double linear(double db) {
        return Math.pow(10d, db / 20d);
    }

    /**
     * One of the table's changes: the gain at {@code tMs} of a row that is at {@code before}
     * before {@code atMs} and {@code after} from {@code atMs + CUT_MS} on.
     *
     * <p>Equal-power where it can be (0→1 is a sine, 1→0 its cosine), and the same 80 ms shape
     * between two non-zero levels. Every change in the table goes through here, which is what
     * makes "the splices are counted and reported" a fact rather than a hope.
     */
    static double splice(double tMs, long atMs, double before, double after) {
        return splice(tMs, atMs, CUT_MS, before, after);
    }

    /**
     * The same change over an arbitrary duration — the two shapes the table uses, in one place:
     * the {@link #CUT_MS} splice (an edit on the bar line) and the incoming's bed arriving over
     * {@link #BED_FADE_BARS} bar (the one deliberately ramped arrival, {@link #bedGain}).
     *
     * <p>Equal-power in both directions, which is what makes the two sides of a hand-over sum to a
     * constant power rather than to a dip: the arriving row is a sine over the width, the leaving
     * one its cosine.
     */
    static double splice(double tMs, long atMs, double widthMs, double before, double after) {
        if (tMs < atMs) return before;
        double frac = widthMs > 0d ? (tMs - atMs) / widthMs : 1d;
        if (frac >= 1d) return after;
        double shaped;
        if (before == 0d) shaped = after * Math.sin(frac * Math.PI / 2d);
        else if (after == 0d) shaped = before * Math.cos(frac * Math.PI / 2d);
        else shaped = before + (after - before) * Math.sin(frac * Math.PI / 2d);
        return shaped;
    }

    /**
     * The incoming's own bed at a position in the file: silent at {@link Plan#entryMs}, arriving
     * equal-power over {@link #BED_FADE_BARS} bar of its own grid, then at {@link #B_BED_DB}
     * (unity) for the rest of the window.
     *
     * <p>This is the table's one fade, and it is the user's own request (「新歌的进入请用淡入效果」):
     * the bed is what the incoming contributes while the outgoing's voice leaves, and a bed that
     * arrived by cut would put a step where the ear is listening for continuity. The bar it takes
     * is the bar the outgoing's drums leave on, so the two events are heard as one.
     */
    static double bedGain(Plan plan, double fileMs) {
        if (plan == null || !plan.valid) return linear(B_BED_DB);
        double width = plan.bedFadeMs - plan.entryMs;
        if (!(width > 0d)) return linear(B_BED_DB);
        return splice(fileMs, plan.entryMs, width, 0d, linear(B_BED_DB));
    }

    /**
     * The gain of one row, one of the two sources, at a position in the file — the table.
     *
     * <p>Outside the fusion window every one of the incoming's own rows is at {@link #BED_DB}
     * (unity — today's content, the file being the master there) and every carried row is silent.
     */
    public static double gainAt(Plan plan, boolean fromOutgoing, StemGesture.Stem row,
                                double fileMs) {
        if (plan == null || !plan.valid) return fromOutgoing ? 0d : linear(BED_DB);
        if (fileMs < plan.entryMs || fileMs >= plan.fusionEndMs) {
            return fromOutgoing ? 0d : linear(BED_DB);
        }
        double other = linear(A_OTHER_DB);
        double unity = linear(BED_DB);
        switch (row) {
            case DRUMS:
                return fromOutgoing
                        ? splice(fileMs, plan.swapMs, unity, 0d)
                        : splice(fileMs, plan.swapMs, 0d, unity);
            case BASS:
                return fromOutgoing
                        ? splice(fileMs, plan.bassMs, unity, 0d)
                        : splice(fileMs, plan.bassMs, 0d, unity);
            case OTHER:
                // A's melodic row leaves by the same 80 ms cut as its low end; B's bed arrives
                // over a whole bar instead — the one ramp in the table (see bedGain).
                return fromOutgoing ? splice(fileMs, plan.bassMs, other, 0d) : bedGain(plan, fileMs);
            default:
                // The outgoing's vocal row is never carried, and the incoming's is the edit's own
                // gate — see incomingGains.
                return fromOutgoing ? 0d : unity;
        }
    }

    /** The incoming's own rows: the fusion's table, and today's vocal gate on the voice. */
    public static DjEdit.RowGain incomingGains(Plan plan, DjEdit.Plan edit) {
        return (row, atSec) -> fileGain(plan, edit, false, row, true, atSec * 1000d);
    }

    /** The carried rows' own gains over the same timeline. */
    public static DjEdit.RowGain carriedGains(Plan plan, boolean withMelody) {
        return (row, atSec) -> fileGain(plan, null, true, row, withMelody, atSec * 1000d);
    }

    /**
     * The table evaluated on the FILE's own timeline — the one definition both the render and the
     * measurement use, because both of them need "what is this row at this moment" and there is no
     * second answer.
     *
     * @param edit the edit's vocal plan, for the incoming's voice; null is only ever passed for a
     *             carried row, whose voice is silent by construction
     */
    private static double fileGain(Plan plan, DjEdit.Plan edit, boolean fromOutgoing,
                                   StemGesture.Stem row, boolean withMelody, double fileMs) {
        if (fromOutgoing) {
            if (row == StemGesture.Stem.VOCALS) return 0d;
            if (row == StemGesture.Stem.OTHER && !withMelody) return 0d;
            return gainAt(plan, true, row, fileMs);
        }
        if (row == StemGesture.Stem.VOCALS) {
            return edit != null ? edit.vocalGainAt(fileMs / 1000d) : 0d;
        }
        return gainAt(plan, false, row, fileMs);
    }

    /**
     * The material the fusion carries: the outgoing's {@code drums}, {@code bass} and (unless it
     * has been dropped) {@code other} rows, taken from its own file at the junction, pre-lengthened
     * by {@code speed} and clipped to the fusion window.
     *
     * <p>Pre-lengthening is the whole reason the passage keeps the outgoing track's tempo: the
     * incoming deck plays this file {@code speed} times as fast, so material stretched by exactly
     * {@code speed} on the way in comes out at its own tempo and its own pitch — the same bargain
     * {@link StemBridge} makes for its carried bass, and exact when {@code speed == 1} (a pair
     * whose grids already hold, which is the ordinary case).
     *
     * <p>The outgoing's vocal row is never returned: {@code vocals} is absent from the result
     * whatever the caller asks for. What comes back is the material <em>as taken</em>; the table's
     * gains go on it with {@link #gate}, which is what the render mixes in and what the
     * measurement measures.
     *
     * @param outgoingStems  the outgoing's separated stems over the window that was decoded,
     *                       {@code [row][channel][sample]}
     * @param stemsStartMs   where that separation starts in the outgoing's own file, ms
     * @return {@code [row][channel][windowFrames]}, with absent rows empty
     */
    public static float[][][] carry(float[][][] outgoingStems, int rate, long stemsStartMs,
                                    Plan plan, double speed, boolean withMelody) {
        int rows = StemGesture.Stem.ALL.length;
        float[][][] out = new float[rows][][];
        if (plan == null || !plan.valid || outgoingStems == null) return out;
        int frames = (int) Math.round(plan.windowMs * rate / 1000d);
        int from = (int) Math.round((plan.sourceFromMs - stemsStartMs) * rate / 1000d);
        int span = (int) Math.round(plan.sourceSpanMs * rate / 1000d);
        for (StemGesture.Stem row : StemGesture.Stem.ALL) {
            if (row == StemGesture.Stem.VOCALS) continue;
            if (row == StemGesture.Stem.OTHER && !withMelody) continue;
            float[][] source = outgoingStems[row.row()];
            if (source == null || source.length == 0) continue;
            out[row.row()] = carried(source, from, span, speed, frames);
        }
        return out;
    }

    /**
     * One row's carried material: {@code span} samples of the source from {@code from},
     * pre-lengthened by {@code ratio} and clipped to {@code frames} (the window's own length).
     *
     * <p>The interpolator is {@link StemBridge}'s ({@code AndroidStemEditRenderer.resample}'s
     * relative), with the sense the words demand: the source is <em>read</em> {@code 1/ratio}
     * samples per output sample, so {@code ratio > 1} makes the material longer and its pitch
     * lower — and playing it back {@code ratio} times faster makes both exactly what they were.
     * {@link StemBridge#stretch} is the same arithmetic on the legacy bridge's carry, and the two
     * are pinned together by the tests.
     *
     * <p>The read stops at {@code from + span}, not at the end of the source array: {@code span}
     * is the material A's rows actually occupy in the file ({@link Plan#sourceSpanMs}), so nothing
     * from past its own last cut can leak into the passage — the table's gains would zero it
     * anyway, and a take that reached for it would be a take nobody measured.
     */
    static float[][] carried(float[][] source, int from, int span, double ratio, int frames) {
        int channels = source.length;
        float[][] out = new float[channels][frames];
        double step = ratio > 0d ? 1d / ratio : 1d;
        for (int ch = 0; ch < channels; ch++) {
            float[] src = source[ch];
            if (src == null) continue;
            int end = Math.max(0, Math.min(src.length, from + span));
            for (int i = 0; i < frames; i++) {
                double at = from + i * step;
                int lo = (int) at;
                if (lo >= end || lo < 0) break;
                int hi = Math.min(lo + 1, end - 1);
                double frac = at - lo;
                out[ch][i] = (float) (src[lo] * (1d - frac) + src[hi] * frac);
            }
        }
        return out;
    }

    /** A slice of one signal starting at {@code from}, {@code frames} long (silence past its
     *  end): the reference a carried row is measured against. */
    static float[][] slice(float[][] pcm, int from, int frames) {
        if (pcm == null) return null;
        float[][] out = new float[pcm.length][frames];
        for (int ch = 0; ch < pcm.length; ch++) {
            float[] src = pcm[ch];
            int copy = src == null ? 0
                    : Math.max(0, Math.min(frames, src.length - Math.max(0, from)));
            if (copy > 0) System.arraycopy(src, Math.max(0, from), out[ch], 0, copy);
        }
        return out;
    }

    /** Two signals summed; the shorter one is silence past its end. */
    static float[][] add(float[][] a, float[][] b) {
        return a == null ? b : b == null ? a : StemBridge.add(a, b);
    }

    // --- the acceptance measurement -------------------------------------------

    /** The material one fusion's acceptance is measured on: what went into the file and what it
     *  was taken from. Every array is over the fusion window ({@link Material#frames} samples at
     *  {@link Material#rate}) unless its own note says otherwise. */
    public static final class Material {
        public final int rate;
        /** The fusion window's length, samples. */
        public final int frames;
        /** The ratio the incoming deck plays this file at — the ratio the carried material is
         *  pre-lengthened by, so {@code 1/speed} is how source samples map to file frames. */
        public final double speed;
        /** The make-up gain already applied to the outgoing's carried rows, dB (0 when none was
         *  needed). It is part of how the material was placed, so the row clauses are measured
         *  against the source lifted by it — see {@link StemFusion#makeupDb}. */
        public final double makeupDb;
        /** The outgoing's carried rows AS PLACED (the table applied — see {@link StemFusion#gate}):
         *  {@code [row][channel][frames]}. Rows that were not carried are empty. */
        public final float[][][] carried;
        /** The same span of the outgoing's own stems, unstretched and ungated — the reference
         *  every level clause is against. */
        public final float[][][] carriedSource;
        /** The incoming's head rows over the window, ungated. */
        public final float[][][] incoming;
        /** The incoming's vocal stem under the edit's own gate over the window. */
        public final float[][][] incomingVocals;
        /** The outgoing's separated vocal stem over the carried span — what must NOT have come
         *  along. */
        public final float[][] outgoingVocals;
        /** The head AS RENDERED (the limiter's own output), over the window: the only thing the
         *  listener's first {@link #STEP_WINDOW_MS} actually is. Null when the caller has no
         *  rendered head — and then the junction's step cannot be asserted and is refused. */
        public final float[][] head;
        /** The OUTGOING's own master over the {@link #STEP_WINDOW_MS} before
         *  {@link Plan#junctionMs} of its own file — the level the junction has to sit at. Null
         *  when the caller has not decoded it, with the same consequence as {@link #head}. */
        public final float[][] outgoingMaster;

        public Material(int rate, int frames, double speed, double makeupDb, float[][][] carried,
                        float[][][] carriedSource, float[][][] incoming,
                        float[][][] incomingVocals, float[][] outgoingVocals, float[][] head,
                        float[][] outgoingMaster) {
            this.rate = rate;
            this.frames = frames;
            this.speed = speed > 0d ? speed : 1d;
            this.makeupDb = makeupDb;
            this.carried = carried;
            this.carriedSource = carriedSource;
            this.incoming = incoming;
            this.incomingVocals = incomingVocals;
            this.outgoingVocals = outgoingVocals;
            this.head = head;
            this.outgoingMaster = outgoingMaster;
        }

        public Material(int rate, int frames, double speed, float[][][] carried,
                        float[][][] carriedSource, float[][][] incoming,
                        float[][][] incomingVocals, float[][] outgoingVocals, float[][] head,
                        float[][] outgoingMaster) {
            this(rate, frames, speed, 0d, carried, carriedSource, incoming, incomingVocals,
                    outgoingVocals, head, outgoingMaster);
        }

        /** The material without a rendered head or the outgoing's own master: every clause below
         *  is measured, and the junction's step is refused for want of a measurement (see
         *  {@link #head}). */
        public Material(int rate, int frames, float[][][] carried, float[][][] carriedSource,
                        float[][][] incoming, float[][][] incomingVocals,
                        float[][] outgoingVocals) {
            this(rate, frames, 1d, 0d, carried, carriedSource, incoming, incomingVocals,
                    outgoingVocals, null, null);
        }
    }

    /** What one fusion measured, and the verdict taken from it — the same shape as
     *  {@link StemBridge.Report}, because it is the same kind of statement. */
    public static final class Report {
        /** dBFS of each carried row as it will be heard, inside the part of the window where the
         *  row is on. */
        public double carriedDrumsDb;
        public double carriedBassDb;
        public double carriedMelodyDb;
        /** dBFS of the material each row was taken from — the level the carry preserves. */
        public double sourceDrumsDb;
        public double sourceBassDb;
        public double sourceMelodyDb;
        /** How much of the outgoing's vocal stem each carried row is, at zero lag. Never carried,
         *  so these must stay at the floor; the melodic row is the one that is dropped rather
         *  than fatal when it is not. */
        public double voiceAlignmentDrums;
        public double voiceAlignmentBass;
        public double voiceAlignmentMelody;
        /** dBFS of the incoming's own drums before their swap (must be at the floor) and inside
         *  the window after it (must be present). */
        public double incomingDrumsBeforeDb;
        public double incomingDrumsAfterDb;
        /** The same for the low end, which swaps a bar later. */
        public double incomingBassBeforeDb;
        public double incomingBassAfterDb;
        /** dBFS of the incoming's voice under the edit's own gate, over the whole fusion window:
         *  the user's own rule, measured rather than assumed. Must be at the floor. */
        public double incomingVocalDb;
        /** dBFS of the carried low end over the {@link #CARRY_END_WINDOW_MS} before its own cut,
         *  and of the material it was taken from over the same span of the outgoing's own file:
         *  the measurement that says the carry is still there <em>up to {@code bassMs}</em> — which
         *  is the instant the take's length is derived from. */
        public double carriedBassEndDb;
        public double sourceBassEndDb;
        /** dBFS of the outgoing's own master over the {@link #STEP_WINDOW_MS} before the junction,
         *  and of the rendered head's first {@link #STEP_WINDOW_MS}: the junction's step is the
         *  difference, and it has to be inside {@link #JUNCTION_STEP_MAX_DB}. */
        public double outgoingMasterDb;
        public double fusionHeadDb;
        public double junctionStepDb;
        /** The make-up gain that was applied to the outgoing's carried rows, dB (0 when none) —
         *  the reduction the step measured on the render BEFORE it, lifted and clamped by
         *  {@link StemFusion#makeupDb}. Reported so the level the passage sits at is explainable
         *  from the log: this is the app answering the removal of the outgoing's voice. */
        public double makeupDb;
        /** True when both sides of the step were actually measured (both arrays were handed in). */
        public boolean stepMeasured;
        /** The pitch the carried material sits at INSIDE the file, semitones (0 when no tempo was
         *  applied): the plain resample's own artefact. The deck plays the file at exactly that
         *  ratio, so what the listener hears is the outgoing track's own pitch again. */
        public double pitchOffsetSemitones;
        /** How many (channel, sample) pairs the clamp touched inside the window, and the longest
         *  run of frames it was at full scale for. */
        public int clippedPairs;
        /** How many pairs the window itself holds, for the share. */
        public int pairs;
        public double clippedShare;
        public double longestClipRunMs;
        /** Beats of the slower of the two grids that carry an attack in the passage's own rhythm
         *  rows (both backings' drums and bass), over all of them; the gap is the pulse's
         *  continuity through the splices. */
        public int beatsWithAttack;
        public int beats;
        public double longestGapMs;
        public double judgedPeriodMs;
        /** The outgoing's melodic carry was dropped because it was its voice. */
        public boolean melodyDropped;
        /** True when every clause of the acceptance holds. */
        public boolean acceptable;
        /** Which clause did not, when one did not. */
        public String failures = "";

        /** The evidence, in one line. */
        public String describe() {
            return String.format(Locale.US,
                    "fusion measured: carried drums %.1f / bass %.1f / melodic %.1f dBFS against"
                            + " their sources %.1f / %.1f / %.1f; the low end still there at its own"
                            + " cut (%.1f vs %.1f dBFS in the last %.0fms); the outgoing's voice in"
                            + " them: %.2f / %.2f / %.2f; the incoming's own drums %.1f dBFS before"
                            + " their swap and %.1f after, its low end %.1f and %.1f, its voice %.1f"
                            + " (the floor is %.0f); the junction: the outgoing master's last %.0fms"
                            + " %.1f dBFS against the fusion's first %.0fms %.1f dBFS = %+.2f dB"
                            + " (the most is %.1f); pitch inside the file %+.2f semitones; the"
                            + " outgoing's rows lifted %+.2f dB to sit there; clipped %d"
                            + " of %d samples (%.2f%%, longest run %.0fms); pulse: %d of %d beats"
                            + " carry an attack, longest gap %.0fms of a %.0fms period%s",
                    carriedDrumsDb, carriedBassDb, carriedMelodyDb, sourceDrumsDb, sourceBassDb,
                    sourceMelodyDb, carriedBassEndDb, sourceBassEndDb,
                    (double) CARRY_END_WINDOW_MS, voiceAlignmentDrums, voiceAlignmentBass,
                    voiceAlignmentMelody, incomingDrumsBeforeDb, incomingDrumsAfterDb,
                    incomingBassBeforeDb, incomingBassAfterDb, incomingVocalDb, SILENT_DBFS,
                    (double) STEP_WINDOW_MS, outgoingMasterDb, (double) STEP_WINDOW_MS,
                    fusionHeadDb, junctionStepDb, JUNCTION_STEP_MAX_DB, pitchOffsetSemitones,
                    makeupDb, clippedPairs, pairs, clippedShare, longestClipRunMs, beatsWithAttack,
                    beats, longestGapMs, judgedPeriodMs,
                    melodyDropped ? "; the melodic carry was dropped (it measured as the outgoing's"
                            + " voice)" : "")
                    + (acceptable ? "" : " -- NOT ACCEPTABLE: " + failures);
        }
    }

    /**
     * Measure one rendered fusion.
     *
     * <p>The clauses, and why each of them is the one that matters here:
     * <ol>
     *   <li><b>The carried rows are there and are the material they claim.</b> Each row, inside
     *       the part of the window where the table has it on, must measure within {@link
     *       #CARRY_TOLERANCE_DB} of its own source level under the table's gain — no more than
     *       3 dB below it (a carry that lost its groove) and no more than 3 dB above the row's
     *       own source (a carry summed twice, or the wrong row placed). The carried low end is
     *       additionally asked about the {@link #CARRY_END_WINDOW_MS} <em>right before its own
     *       cut</em>: a take that is shorter than the gesture needs goes silent exactly there
     *       (that was round 18's defect — A's bed dying on a hard cut the table never names), and
     *       a clause measured over the whole of [entry, bassMs) would average it away.</li>
     *   <li><b>The outgoing's vocal content did not come along.</b> Each carried row is compared
     *       with the outgoing's separated vocal stem at zero lag: a row that is that voice is
     *       fatal for the drums and the bass and <em>drops the melodic carry</em> for {@code
     *       other} — the design's stated degradation, which keeps the groove and loses the
     *       pad.</li>
     *   <li><b>The incoming's drums are silent before their swap and present after</b> (and the
     *       same for its low end a bar later) — the table's own claim, measured rather than
     *       asserted.</li>
     *   <li><b>The junction sits at the outgoing's own level.</b> The rendered head's first {@link
     *       #STEP_WINDOW_MS} against the outgoing master's last {@link #STEP_WINDOW_MS}, within
     *       {@link #JUNCTION_STEP_MAX_DB}. This is the clause the round-18 prototype's junction
     *       failed on real material (the static divisor's −4.10 dB, which the listener heard as a
     *       卡顿), and the reason the head carries a limiter and the bed arrives over a bar.</li>
     *   <li><b>No long clipping.</b> The sum of two backings can exceed full scale; a rare
     *       clamped peak is a splice landing on one, but a run or a share past the limits means
     *       the passage is mixed too loud to be the master.</li>
     *   <li><b>The pulse holds.</b> Attacks on the slower grid, judged on the passage's own
     *       rhythm rows (both backings' drums and bass, as the table places them) rather than on
     *       the whole mix: the melodic beds are deliberately present the whole time and would
     *       hide a hole in the groove. This is the clause that catches an outgoing ending with no
     *       rhythm in it — a fade, a break, a spoken outro — which is the same hazard {@link
     *       StemBridge#measure} exists for.</li>
     * </ol>
     *
     * @param plan   the fusion
     * @param edit   the edit's own vocal plan, for the incoming's gate
     * @param guard  the clamp count over the window (see {@link DjEdit.ClipGuard}), or null
     */
    public static Report measure(Plan plan, DjEdit.Plan edit, Material m, boolean withMelody,
                                 double beatSecA, double beatSecB, DjEdit.ClipGuard guard) {
        Report r = new Report();
        r.melodyDropped = !withMelody;
        if (plan == null || !plan.valid || m == null) {
            r.acceptable = false;
            r.failures = "there is no fusion to measure";
            return r;
        }
        int rate = m.rate;
        long swapIn = plan.swapMs - plan.entryMs;
        long bassIn = plan.bassMs - plan.entryMs;
        double windowSec = m.frames / (double) rate;

        r.carriedDrumsDb = StemBridge.levelDb(carried(m, StemGesture.Stem.DRUMS.row()), rate,
                swapIn / 1000d);
        r.carriedBassDb = StemBridge.levelDb(carried(m, StemGesture.Stem.BASS.row()), rate,
                bassIn / 1000d);
        r.sourceDrumsDb = StemBridge.levelDb(source(m, StemGesture.Stem.DRUMS.row()), rate,
                swapIn / 1000d);
        r.sourceBassDb = StemBridge.levelDb(source(m, StemGesture.Stem.BASS.row()), rate,
                bassIn / 1000d);
        // ⚠️ The pitch the carried material sits at INSIDE the file is the resample's own ratio
        // (`speed`), not `windowMs/sourceSpanMs`: the span the take needs is drawn from bassMs, so
        // the two are not the same number (see sourceSpanMs). The deck plays the file at the same
        // ratio, which is what hands the listener the outgoing track's own pitch again.
        r.pitchOffsetSemitones = Math.abs(m.speed - 1d) > 1e-9d
                ? 12d * (Math.log(m.speed) / Math.log(2d)) : 0d;

        // The carry right up to its own last cut: the stretch of the outgoing's material
        // immediately before bassMs, against the same stretch of its own file. This is the clause
        // a take that is too short fails, and it fails here rather than nowhere (see
        // CARRY_END_WINDOW_MS).
        int endFrom = (int) Math.round(framewise(Math.max(0L, bassIn - CARRY_END_WINDOW_MS), rate));
        int endTo = (int) Math.round(framewise(bassIn, rate));
        int endFrames = Math.max(0, endTo - endFrom);
        float[][] bassEnd = slice(carried(m, StemGesture.Stem.BASS.row()), endFrom, endFrames);
        float[][] bassEndSource = slice(source(m, StemGesture.Stem.BASS.row()),
                (int) Math.round(endFrom / m.speed), (int) Math.round(endFrames / m.speed));
        r.carriedBassEndDb = StemBridge.levelDb(bassEnd, rate, endFrames / (double) rate);
        r.sourceBassEndDb = StemBridge.levelDb(bassEndSource, rate,
                Math.round(endFrames / m.speed) / (double) rate);

        // The junction's step: the level the listener was already at (the outgoing's own master,
        // its last STEP_WINDOW_MS) against the level the fusion arrives at (the rendered head's
        // first STEP_WINDOW_MS). Measured on the head AS RENDERED — the limiter included — because
        // that is what is in the file.
        int stepFrames = (int) Math.round(framewise(STEP_WINDOW_MS, rate));
        int headFrames = Math.min(stepFrames, m.frames);
        int masterFrames = m.outgoingMaster == null || m.outgoingMaster.length == 0
                ? 0 : Math.min(stepFrames, m.outgoingMaster[0] == null ? 0
                        : m.outgoingMaster[0].length);
        if (m.head != null && m.head[0] != null && masterFrames > 0 && headFrames > 0) {
            float[][] headStart = slice(m.head, 0, headFrames);
            r.fusionHeadDb = StemBridge.levelDb(headStart, rate, headFrames / (double) rate);
            r.outgoingMasterDb = StemBridge.levelDb(m.outgoingMaster, rate,
                    masterFrames / (double) rate);
            r.junctionStepDb = r.fusionHeadDb - r.outgoingMasterDb;
            r.stepMeasured = true;
        }

        // The voice question is asked over the part of the window where the row is audible: a row
        // that is silent for the second half of the passage would otherwise "align" less with the
        // voice than the material it is made of does.
        r.voiceAlignmentDrums = Math.abs(StemBridge.alignmentAtZeroLag(
                carried(m, StemGesture.Stem.DRUMS.row()), m.outgoingVocals, rate,
                swapIn / 1000d, null));
        r.voiceAlignmentBass = Math.abs(StemBridge.alignmentAtZeroLag(
                carried(m, StemGesture.Stem.BASS.row()), m.outgoingVocals, rate,
                bassIn / 1000d, null));

        float[][] melody = carried(m, StemGesture.Stem.OTHER.row());
        boolean hasMelody = withMelody && melody.length > 0 && melody[0] != null;
        if (hasMelody) {
            r.carriedMelodyDb = StemBridge.levelDb(melody, rate, bassIn / 1000d);
            r.sourceMelodyDb = StemBridge.levelDb(source(m, StemGesture.Stem.OTHER.row()), rate,
                    bassIn / 1000d);
            r.voiceAlignmentMelody = Math.abs(StemBridge.alignmentAtZeroLag(melody,
                    m.outgoingVocals, rate, bassIn / 1000d, null));
        }

        // The incoming's own rows, as the table places them.
        float[][] inDrums = gated(m.incoming, StemGesture.Stem.DRUMS, plan, edit, rate, m.frames);
        float[][] inBass = gated(m.incoming, StemGesture.Stem.BASS, plan, edit, rate, m.frames);
        r.incomingDrumsBeforeDb = StemBridge.levelDb(inDrums, rate, swapIn / 1000d);
        r.incomingDrumsAfterDb = StemBridge.levelDb(slice(inDrums, (int) framewise(swapIn, rate),
                m.frames - (int) framewise(swapIn, rate)), rate,
                (m.frames - framewise(swapIn, rate)) / (double) rate);
        r.incomingBassBeforeDb = StemBridge.levelDb(inBass, rate, bassIn / 1000d);
        r.incomingBassAfterDb = StemBridge.levelDb(slice(inBass, (int) framewise(bassIn, rate),
                m.frames - (int) framewise(bassIn, rate)), rate,
                (m.frames - framewise(bassIn, rate)) / (double) rate);
        r.incomingVocalDb = StemBridge.levelDb(m.incomingVocals != null
                && StemGesture.Stem.VOCALS.row() < m.incomingVocals.length
                ? m.incomingVocals[StemGesture.Stem.VOCALS.row()] : new float[0][], rate,
                windowSec);

        // The pulse, on the passage's own rhythm rows: what the file adds of the outgoing's
        // groove, plus the incoming's own under the table.
        float[][] rhythm = add(add(carried(m, StemGesture.Stem.DRUMS.row()),
                        carried(m, StemGesture.Stem.BASS.row())),
                add(inDrums, inBass));
        double period = Math.max(beatSecA > 0d ? beatSecA : 0d, beatSecB > 0d ? beatSecB : 0d);
        r.judgedPeriodMs = period * 1000d;
        if (period > 0d) {
            double[] levels = StemBridge.frameLevelsDb(rhythm, rate, windowSec);
            double median = StemBridge.median(levels);
            int perBeat = Math.max(1, (int) Math.round(period / StemBridge.FRAME_SEC));
            r.beats = Math.max(1, levels.length / perBeat);
            int attacks = 0;
            double longest = 0d;
            double run = 0d;
            for (int b = 0; b < r.beats; b++) {
                boolean attack = false;
                for (int f = b * perBeat; f < Math.min(levels.length, (b + 1) * perBeat); f++) {
                    if (levels[f] > median + PULSE_ATTACK_DB) {
                        attack = true;
                        break;
                    }
                }
                if (attack) {
                    attacks++;
                    run = 0d;
                } else {
                    run += period;
                    longest = Math.max(longest, run);
                }
            }
            r.beatsWithAttack = attacks;
            r.longestGapMs = longest * 1000d;
        }

        if (guard != null) {
            r.clippedPairs = guard.clipped;
            r.pairs = guard.pairs;
            r.clippedShare = guard.share();
            r.longestClipRunMs = guard.longestRunMs();
        }

        StringBuilder bad = new StringBuilder();
        r.makeupDb = m.makeupDb;
        if (!(r.carriedDrumsDb > -60d)) bad.append("the carried drums are silent; ");
        if (!(r.carriedBassDb > -60d)) bad.append("the carried bass is silent; ");
        rowClause(bad, "drums", r.carriedDrumsDb, r.sourceDrumsDb, 0d, m.makeupDb);
        rowClause(bad, "bass", r.carriedBassDb, r.sourceBassDb, 0d, m.makeupDb);
        if (!(r.carriedBassEndDb > r.sourceBassEndDb + m.makeupDb - CARRY_TOLERANCE_DB)) {
            bad.append("the carried low end is not there up to its own cut (")
                    .append(fmt(r.carriedBassEndDb)).append(" dBFS over the last ")
                    .append(CARRY_END_WINDOW_MS).append("ms before it, against ")
                    .append(fmt(r.sourceBassEndDb)).append(" in the material it was taken from")
                    .append(m.makeupDb == 0d ? "" : ", lifted " + fmt(m.makeupDb) + " dB")
                    .append("); ");
        }
        if (hasMelody) rowClause(bad, "the melodic carry", r.carriedMelodyDb, r.sourceMelodyDb,
                A_OTHER_DB, m.makeupDb);
        if (r.voiceAlignmentDrums > VOICE_CARRY_LIMIT) {
            bad.append("the carried drums are the outgoing's voice (they align ")
                    .append(fmt(r.voiceAlignmentDrums)).append(" with its vocal stem); ");
        }
        if (r.voiceAlignmentBass > VOICE_CARRY_LIMIT) {
            bad.append("the carried bass is the outgoing's voice (it aligns ")
                    .append(fmt(r.voiceAlignmentBass)).append(" with its vocal stem); ");
        }
        if (hasMelody && r.voiceAlignmentMelody > VOICE_CARRY_LIMIT) {
            bad.append("the melodic carry is the outgoing's voice (it aligns ")
                    .append(fmt(r.voiceAlignmentMelody)).append(" with its vocal stem); ");
        }
        if (r.incomingDrumsBeforeDb > SILENT_DBFS) {
            bad.append("the incoming's own drums are not silent before their swap (")
                    .append(fmt(r.incomingDrumsBeforeDb)).append(" dBFS); ");
        }
        if (!(r.incomingDrumsAfterDb > SILENT_DBFS)) {
            bad.append("the incoming's own drums never arrive (")
                    .append(fmt(r.incomingDrumsAfterDb)).append(" dBFS after their swap); ");
        }
        if (r.incomingBassBeforeDb > SILENT_DBFS) {
            bad.append("the incoming's own low end is not silent before its swap (")
                    .append(fmt(r.incomingBassBeforeDb)).append(" dBFS); ");
        }
        if (!(r.incomingBassAfterDb > SILENT_DBFS)) {
            bad.append("the incoming's own low end never arrives (")
                    .append(fmt(r.incomingBassAfterDb)).append(" dBFS after its swap); ");
        }
        if (r.incomingVocalDb > SILENT_DBFS) {
            bad.append("the incoming's voice is in the window (")
                    .append(fmt(r.incomingVocalDb)).append(" dBFS under the edit's own gate); ");
        }
        // ⚠️ The junction's step. This is the clause the round-18 prototype failed on real
        // material (measured −4.10 dB where the static divisor it used held the head down), and
        // the reason the head carries a limiter: a fusion that cannot sit at the level the
        // listener is already at is a drop, and a drop is what the whole round exists to remove.
        if (!r.stepMeasured) {
            bad.append("the junction's step could not be measured (the outgoing master's last ")
                    .append(STEP_WINDOW_MS).append("ms and the fusion's first ")
                    .append(STEP_WINDOW_MS).append("ms were both needed); ");
        } else if (Math.abs(r.junctionStepDb) > JUNCTION_STEP_MAX_DB) {
            bad.append("the fusion's first ").append(STEP_WINDOW_MS).append("ms sits ")
                    .append(String.format(Locale.US, "%+.1f", r.junctionStepDb))
                    .append(" dB from the outgoing master's last ").append(STEP_WINDOW_MS)
                    .append("ms (").append(fmt(r.fusionHeadDb)).append(" vs ")
                    .append(fmt(r.outgoingMasterDb)).append(" dBFS, the most is ")
                    .append(fmt(JUNCTION_STEP_MAX_DB)).append(" dB); ");
        }
        if (r.longestClipRunMs > CLIP_RUN_MAX_MS) {
            bad.append("the passage clips for ").append(fmt(r.longestClipRunMs))
                    .append("ms at a stretch; ");
        }
        if (r.clippedShare > CLIP_SHARE_MAX) {
            bad.append(fmt(r.clippedShare * 100d)).append("% of its samples are clamped; ");
        }
        if (period > 0d && r.longestGapMs > 1.6d * r.judgedPeriodMs) {
            bad.append("the passage's own pulse has a ").append(fmt(r.longestGapMs))
                    .append("ms hole with a ").append(fmt(r.judgedPeriodMs)).append("ms period; ");
        }
        r.acceptable = bad.length() == 0;
        r.failures = bad.toString();
        return r;
    }

    /** One row's level clause: not more than {@link #CARRY_TOLERANCE_DB} below where the table
     *  (and the make-up gain, if one was applied) puts it, and never above the material it was
     *  taken from by more than the make-up plus that tolerance. */
    private static void rowClause(StringBuilder bad, String what, double carriedDb, double sourceDb,
                                  double designedDb, double makeupDb) {
        double placed = sourceDb + designedDb + makeupDb;
        if (!(carriedDb > placed - CARRY_TOLERANCE_DB)) {
            bad.append("the ").append(what).append(" is more than ")
                    .append(fmt(CARRY_TOLERANCE_DB)).append(" dB below where the table puts it (")
                    .append(fmt(carriedDb)).append(" vs ").append(fmt(placed))
                    .append(" dBFS); ");
        }
        if (carriedDb > sourceDb + makeupDb + CARRY_TOLERANCE_DB) {
            bad.append("the ").append(what).append(" is more than ")
                    .append(fmt(CARRY_TOLERANCE_DB)).append(" dB above the material it was taken"
                            + " from (").append(fmt(carriedDb)).append(" vs ")
                    .append(fmt(sourceDb + makeupDb)).append(" dBFS); ");
        }
    }

    private static double framewise(long ms, int rate) {
        return ms * rate / 1000d;
    }

    private static float[][] carried(Material m, int row) {
        return m.carried != null && row < m.carried.length && m.carried[row] != null
                ? m.carried[row] : new float[0][];
    }

    private static float[][] source(Material m, int row) {
        return m.carriedSource != null && row < m.carriedSource.length
                && m.carriedSource[row] != null ? m.carriedSource[row] : new float[0][];
    }

    /** One of the incoming's rows with the fusion's table applied — what the row actually is in
     *  the file. The material is window-relative, and the table is not: the window starts at
     *  {@code entryMs} of the file. */
    private static float[][] gated(float[][][] stems, StemGesture.Stem row, Plan plan,
                                   DjEdit.Plan edit, int rate, int frames) {
        float[][] source = stems != null && row.row() < stems.length ? stems[row.row()] : null;
        float[][] out = new float[source == null ? 2 : source.length][frames];
        if (source == null) return out;
        for (int i = 0; i < frames; i++) {
            double fileMs = plan.entryMs + i * 1000d / rate;
            double g = fileGain(plan, edit, false, row, true, fileMs);
            if (g == 0d) continue;
            for (int ch = 0; ch < source.length; ch++) {
                if (i < source[ch].length) out[ch][i] = (float) (g * source[ch][i]);
            }
        }
        return out;
    }

    /**
     * The carried material with the table's own gains applied — what the file ADDS, row by row.
     *
     * <p>{@link #carry} returns the outgoing's material as it was taken; this is the same material
     * as the table places it (the outgoing's drums silent from the drums' swap on, its melodic row
     * at {@link #A_OTHER_DB} until the low end changes hands, and so on). The render mixes in what
     * this returns, and the measurement measures what this returns, so "the row's level" means the
     * same thing in both places.
     */
    public static float[][][] gate(float[][][] carried, Plan plan, boolean withMelody, int rate) {
        float[][][] out = new float[carried == null ? 0 : carried.length][][];
        if (carried == null) return out;
        for (int row = 0; row < carried.length; row++) {
            if (carried[row] == null || carried[row].length == 0) continue;
            StemGesture.Stem stem = stemOf(row);
            out[row] = stem == null ? null : gated(carried[row], plan, rate, carried[row][0].length,
                    stem, withMelody);
        }
        return out;
    }

    /** The stem of a model row index, or null for a row index the model does not have. */
    private static StemGesture.Stem stemOf(int row) {
        for (StemGesture.Stem stem : StemGesture.Stem.ALL) {
            if (stem.row() == row) return stem;
        }
        return null;
    }

    /** One carried row with its own gain applied, on the file's own timeline (the material is
     *  window-relative and the window starts at {@code entryMs}). */
    private static float[][] gated(float[][] row, Plan plan, int rate, int frames,
                                   StemGesture.Stem stem, boolean withMelody) {
        float[][] out = new float[row.length][frames];
        for (int i = 0; i < frames; i++) {
            double fileMs = plan.entryMs + i * 1000d / rate;
            double g = fileGain(plan, null, true, stem, withMelody, fileMs);
            if (g == 0d) continue;
            for (int ch = 0; ch < row.length; ch++) {
                if (i < row[ch].length) out[ch][i] = (float) (g * row[ch][i]);
            }
        }
        return out;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
