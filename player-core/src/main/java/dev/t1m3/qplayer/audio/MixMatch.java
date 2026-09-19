package dev.t1m3.qplayer.audio;

import java.util.Locale;

/**
 * Whether two tracks <em>suit each other</em>, and if so what has to be done to
 * the incoming one for them to be mixed rather than merely overlapped: how much
 * its tempo must be pulled onto the outgoing track's grid, and how many semitones
 * its key must move to sit on the outgoing track's harmony.
 *
 * <p>This is the judgement the whole "beyond a fade" phase is built on. Aligning
 * two grids (see {@link BeatProfile#snapOverlapMs}) only says <em>when</em> to
 * put the beats together; it cannot help a pair whose beats never meet, and it
 * says nothing at all about the harmony. Slowing the incoming track by a few
 * percent (<em>pitch preserving</em> — a platform capability, see
 * {@code AndroidAudioBackend}) makes the grids literally the same grid, and
 * transposing it by a semitone or two puts it in the outgoing track's key, which
 * is what makes a long overlap sound like a mix instead of two songs.
 *
 * <p>Four conditions, all of them required, in the order the code checks them —
 * every one is a way for the pair to be "not suitable", and a pair that fails any
 * of them gets exactly the behaviour the app had before any of this existed:
 * <ol>
 *   <li><b>Both grids exist and are trustworthy.</b> Without them there is
 *       nothing to pull anything onto.</li>
 *   <li><b>The tempo difference is inside the clamp</b> ({@link #MAX_SPEED_STEP},
 *       8%). A pair that is already close enough to hold together for the whole
 *       overlap is left alone — a 0.3% stretch is an artefact bought for
 *       nothing. A pair outside the clamp is <em>not</em> stretched: at ±20% the
 *       time-stretch drops the transients that make a beat sound like a beat, so
 *       the honest answer for "these two do not share a tempo" is the plain fade
 *       the app already had.</li>
 *   <li><b>The keys are close enough, or a shift of at most
 *       {@link #MAX_SEMITONES} semitones makes them so</b> — measured as the
 *       distance between the two pitch-class profiles after the shift
 *       ({@link KeyProfile#distance}), because a key <em>name</em> is the one
 *       thing real music is ambiguous about (a loop of Am-F-C-G is C major and A
 *       minor at once). A shift also has to be sanctioned by a whole-key
 *       relationship ({@link KeyProfile#camelotCompatible}) unless it improves
 *       the profile distance by a wide margin, so a mis-estimated key cannot
 *       detune a track.</li>
 *   <li><b>Enough time left</b> for the length a mix is heard over. Checked by
 *       the caller, which is the only side that knows what is left of the track.</li>
 * </ol>
 *
 * <p>Immutable, and cheap (twelve-bin arithmetic): it is built once per boundary,
 * inside the same decision that picks the transition kind.
 */
public final class MixMatch {

    /**
     * The most the incoming track's tempo may be moved, as a fraction of its own.
     *
     * <p>Eight percent is where a DJ's own pitch fader usually stops being
     * transparent and it is also where this phase's arithmetic stops being safe:
     * the platform's time-stretch is a phase vocoder (Sonic), and the further a
     * transient has to be smeared the less of it survives. At ±8% a kick still
     * reads as a kick; past that the correction costs more in artefacts than the
     * alignment buys. Beyond it the pair is not stretched at all — see the class
     * comment.
     */
    public static final double MAX_SPEED_STEP = 0.08d;

    /**
     * The most the incoming track's key may be moved, semitones, in either
     * direction.
     *
     * <p>Two semitones is the whole distance over which a pitch shift stays
     * inaudible as an effect: further than that and a listener who knows the
     * track hears it transposed — the record is the artefact then, not the mix.
     * It is also enough to reach every key a DJ would harmonically mix with
     * (same key, relative major/minor, both fifths), which is all this is for.
     */
    public static final int MAX_SEMITONES = 2;

    /**
     * How much the profile distance has to improve for a shift to be worth making
     * without a whole-key relationship behind it ({@link KeyProfile#camelotCompatible}).
     *
     * <p>Two songs in the same key still measure a distance of a few tenths —
     * different arrangement, different instrumentation — so "closer" alone proves
     * nothing. A shift has to move the profiles by this much of their whole mass
     * (and, below, by a quarter of the distance that was there) before it is taken
     * as evidence about the key rather than about the two mixes.
     */
    private static final double MIN_KEY_IMPROVEMENT = 0.05d;

    /** And it has to be a real improvement, not a marginal one: the shifted pair
     *  must be at most this fraction of the unshifted distance. */
    private static final double MAX_IMPROVEMENT_RATIO = 0.75d;

    /**
     * The largest profile distance two keys may have and still be mixed.
     *
     * <p>Above this the two tracks have so little in common harmonically that
     * there is no key shift within {@link #MAX_SEMITONES} that helps — the
     * distance after the best shift is still this large — and a pair like that is
     * not one to hold together for fifteen seconds at all. Calibrated on
     * synthetic material: two tracks in the same key land at 0.15-0.35, a fifth
     * apart 0.25-0.45, and unrelated keys 0.6-0.9 (see {@code MixMatchTest}).
     */
    private static final double MAX_KEY_DISTANCE = 0.55d;

    /** The answer for a pair that must not be mixed; the reason is logged. */
    public static MixMatch refused(String reason) {
        return new MixMatch(false, 1d, 0, reason);
    }

    /**
     * Judge one boundary. {@code overlapMs} is the length the pair would actually
     * be mixed over (the caller knows the plan and what is left of the track), and
     * it matters: two tempos that hold together for four seconds may not for
     * fifteen, and the answer must be about the mix that is really going to happen.
     */
    public static MixMatch between(BeatProfile a, BeatProfile b, long overlapMs) {
        if (a == null || b == null) return refused("no grid for " + (a == null ? "A" : "B"));
        if (!a.trustworthy() || !b.trustworthy()) {
            BeatProfile weak = !a.trustworthy() ? a : b;
            return refused("grid not trustworthy (" + weak.confidenceText() + " < "
                    + BeatProfile.MIN_CONFIDENCE
                    + (weak.prominenceText() != null ? ", " + weak.prominenceText() : "") + ")");
        }
        double ratio = speedFor(a, b);
        double speed = 1d;
        String tempoNote;
        if (BeatProfile.gridsCompatible(a, b, overlapMs)) {
            // Already the same grid: pulling the tempo would be a change bought for
            // nothing, so the mix runs at 1.0 and only the key (and the length) is
            // this phase's contribution. The ratio is still named — one line per
            // boundary has to say what tempo the incoming track ran at, and "1.0000"
            // is an answer, not an omission.
            tempoNote = "speed x1.0000 on B (tempo already holds: "
                    + Math.round(BeatProfile.gridDriftMs(a, b, overlapMs))
                    + "ms of drift over " + overlapMs + "ms)";
        } else if (withinClamp(ratio)) {
            speed = ratio;
            tempoNote = String.format(Locale.US,
                    "speed x%.4f on B (%.1f->%.1fBPM, %+.1f%%)",
                    speed, b.bpm(), b.bpm() * speed, (speed - 1d) * 100d);
        } else {
            return refused(String.format(Locale.US,
                    "tempos too far apart: %.1f vs %.1fBPM needs x%.3f, the clamp is x%.2f",
                    a.bpm(), b.bpm(), ratio, 1d + MAX_SPEED_STEP));
        }

        KeyProfile ka = a.key();
        KeyProfile kb = b.key();
        if (ka == null || kb == null) return refused("no key measured for " + (ka == null ? "A" : "B"));
        if (!ka.trustworthy() || !kb.trustworthy()) {
            KeyProfile weak = !ka.trustworthy() ? ka : kb;
            return refused("key not trustworthy (" + weak.label() + ")");
        }
        double[] chromaA = ka.chroma();
        double[] chromaB = kb.chroma();
        double atZero = KeyProfile.distance(chromaA, chromaB);
        int bestShift = 0;
        double bestDistance = atZero;
        for (int n = 0; n <= MAX_SEMITONES; n++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                if (n == 0 && sign < 0) continue;
                int shift = n * sign;
                double distance = KeyProfile.distance(chromaA, KeyProfile.rotated(chromaB, shift));
                if (!sanctioned(ka, kb, shift, distance, atZero)) continue;
                if (distance < bestDistance - 1e-9d) {
                    bestDistance = distance;
                    bestShift = shift;
                }
            }
        }
        // A shift is applied when it is both allowed (a whole-key relationship, or an
        // improvement too large to be about the two arrangements) and worth making
        // (a real move of the profiles). A shift that was allowed but does not clear
        // the second bar is NOT a clash — the profiles are already closer than
        // MAX_KEY_DISTANCE, which is this class's own statement that the two keys can
        // be heard together — so the answer is the one the no-shift branch gives:
        // leave the incoming track alone. Refusing there would turn "a shift would
        // not help" into "these keys clash", a different and often false claim; it
        // was measured refusing a pair 0.18 apart whose tempo was inside the clamp,
        // i.e. a pair every threshold in here calls suitable.
        int allowedShift = bestShift;
        double allowedDistance = bestDistance;
        boolean worthShifting = bestShift != 0
                && atZero - bestDistance >= MIN_KEY_IMPROVEMENT
                && bestDistance <= MAX_KEY_DISTANCE
                && sanctionedByImprovement(bestDistance, atZero);
        String keyNote;
        if (!worthShifting) {
            if (atZero > MAX_KEY_DISTANCE) {
                return refused(allowedShift == 0
                        ? String.format(Locale.US,
                                "keys clash (chroma distance %.2f, no shift within ±%d helps)",
                                atZero, MAX_SEMITONES)
                        : String.format(Locale.US,
                                "keys clash (chroma distance %.2f; the best shift, %+d, only reaches %.2f)",
                                atZero, allowedShift, allowedDistance));
            }
            // The shift the returned answer carries is none, so the object and the line
            // agree: a caller reads semitones()/pitch() and applies nothing.
            bestShift = 0;
            bestDistance = atZero;
            // Same reasoning as the tempo above: the shift that is applied is named
            // even when it is none. When one was allowed but would not have moved the
            // profiles, the line says so, because that is the difference between "no
            // shift was ever available" and "the shift was not worth making".
            keyNote = "pitch x1.0000 on B (0 semitones: keys already sit together, "
                    + ka.label() + " / " + kb.label()
                    + ", chroma distance " + String.format(Locale.US, "%.2f", atZero)
                    + (allowedShift == 0 ? "" : String.format(Locale.US,
                            "; %+d was allowed but only reaches %.2f, so nothing is applied",
                            allowedShift, allowedDistance))
                    + ")";
        } else {
            KeyProfile moved = kb.shifted(bestShift);
            keyNote = String.format(Locale.US,
                    "pitch x%.4f on B (%+d semitone%s: %s (%s) -> %s (%s), chroma distance %.2f->%.2f)",
                    Math.pow(2d, bestShift / 12d), bestShift, Math.abs(bestShift) == 1 ? "" : "s",
                    kb.label(), KeyProfile.camelotLabel(kb.tonic(), kb.major()),
                    moved.label(), KeyProfile.camelotLabel(moved.tonic(), moved.major()),
                    atZero, bestDistance);
        }
        return new MixMatch(true, speed, bestShift, tempoNote + "; " + keyNote);
    }

    /**
     * Whether a shift is allowed to be considered at all: either the two keys are
     * whole-key neighbours after it (the rule a DJ mixes by, and the one that
     * cannot be fooled by a profile that merely got closer), or the profiles came
     * so much closer that the shift is evidence about the key rather than about
     * the two arrangements.
     */
    private static boolean sanctioned(KeyProfile a, KeyProfile b, int semitones,
                                      double distance, double atZero) {
        if (a.camelotCompatible(b, semitones)) return true;
        return sanctionedByImprovement(distance, atZero);
    }

    private static boolean sanctionedByImprovement(double distance, double atZero) {
        return distance <= atZero * MAX_IMPROVEMENT_RATIO
                && atZero - distance >= MIN_KEY_IMPROVEMENT;
    }

    /** The tempo ratio that puts the incoming track's beat where the outgoing
     *  track's beat is: B is played {@code ratio} times as fast, so its period
     *  becomes {@code periodB / ratio}, and that is periodA exactly when
     *  {@code ratio = periodB / periodA}. */
    public static double speedFor(BeatProfile a, BeatProfile b) {
        if (a == null || b == null) return 1d;
        double periodA = a.periodMs();
        if (!(periodA > 0d)) return 1d;
        return b.periodMs() / periodA;
    }

    /** Whether a ratio is small enough to be transparent ({@link #MAX_SPEED_STEP}). */
    public static boolean withinClamp(double ratio) {
        return Math.abs(ratio - 1d) <= MAX_SPEED_STEP + 1e-9d;
    }

    private final boolean suitable;
    private final double speed;
    private final int semitones;
    private final String note;

    private MixMatch(boolean suitable, double speed, int semitones, String note) {
        this.suitable = suitable;
        this.speed = speed;
        this.semitones = semitones;
        this.note = note == null ? "" : note;
    }

    /** Whether this pair is one to mix properly (tempo pulled onto the outgoing
     *  grid and key brought into line). False means every part of this phase stays
     *  out of the way and the boundary behaves as it did before it existed. */
    public boolean suitable() {
        return suitable;
    }

    /** The speed the incoming track is played at (1.0 = untouched). */
    public double speed() {
        return speed;
    }

    /** The key shift applied to the incoming track, semitones (0 = untouched). */
    public int semitones() {
        return semitones;
    }

    /** The pitch ratio {@link #semitones()} means on the platform's own scale. */
    public double pitch() {
        return Math.pow(2d, semitones / 12d);
    }

    /** One fragment for the boundary's single log line — what was done to the
     *  incoming track, or why nothing was. */
    public String note() {
        return suitable ? "mix: " + note : "mix: off (" + note + ")";
    }

    @Override
    public String toString() {
        return suitable
                ? "MixMatch{x" + String.format(Locale.US, "%.4f", speed)
                + ", " + semitones + " semitones}"
                : "MixMatch{none: " + note + "}";
    }
}
