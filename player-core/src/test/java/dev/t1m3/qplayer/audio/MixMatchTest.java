package dev.t1m3.qplayer.audio;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The pair judgement ({@link MixMatch}) and the arithmetic under it: the tempo
 * ratio, its clamp, the key shift and the Camelot rule. Pure functions over
 * hand-built profiles, so every case here is a statement about the decision
 * rather than about the estimator — the estimator has its own test.
 */
public class MixMatchTest {

    private static final long OVERLAP = TransitionPlan.OVERLAP_MATCHED_MS;

    // --- tempo ---------------------------------------------------------------

    @Test
    public void theRatioPutsTheIncomingTrackOnTheOutgoingTracksGrid() {
        // A is the grid to hold. B is faster, so it has to be slowed: 120 into 126 is
        // 120/126 = 0.95238, and B's period becomes A's exactly.
        BeatProfile a = beat(120d, 0.9f);
        BeatProfile b = beat(126d, 0.9f);
        double ratio = MixMatch.speedFor(a, b);
        System.out.println(String.format(Locale.US, "A=%.1f B=%.1f -> speed x%.5f", a.bpm(), b.bpm(), ratio));
        assertEquals(120d / 126d, ratio, 1e-9d);
        assertEquals(a.periodMs(), b.periodMs() / ratio, 1e-6d);
        assertTrue(MixMatch.withinClamp(ratio));
        MixMatch match = MixMatch.between(a, b, OVERLAP);
        assertTrue(match.suitable());
        assertEquals(ratio, match.speed(), 1e-9d);

        // The other way round: B is slower, so it is sped up.
        double up = MixMatch.speedFor(b, a);
        System.out.println(String.format(Locale.US, "A=%.1f B=%.1f -> speed x%.5f", b.bpm(), a.bpm(), up));
        assertEquals(1d / ratio, up, 1e-9d);
    }

    @Test
    public void theClampIsEightPercentAndBeyondItNothingIsStretched() {
        // Exactly on the edge: 128 -> 138.24 BPM is a 8% stretch and is allowed.
        assertTrue(MixMatch.withinClamp(1.08d));
        assertTrue(MixMatch.withinClamp(0.92d));
        assertFalse(MixMatch.withinClamp(1.0801d));
        assertFalse(MixMatch.withinClamp(0.9199d));

        // Inside the clamp.
        MixMatch inside = MixMatch.between(beat(120d, 0.9f), beat(128d, 0.9f), OVERLAP);
        System.out.println("120 vs 128: " + inside);
        assertTrue(inside.suitable());
        assertEquals(120d / 128d, inside.speed(), 1e-9d);

        // Outside it: the pair is not stretched at all, and says why.
        MixMatch outside = MixMatch.between(beat(120d, 0.9f), beat(140d, 0.9f), OVERLAP);
        System.out.println("120 vs 140: " + outside);
        assertFalse(outside.suitable());
        assertTrue(outside.note(), outside.note().contains("too far apart"));
        assertEquals("no speed may be reported for a refused pair", 1d, outside.speed(), 1e-9d);

        // 87 vs 174 BPM — the half/double relationship a real library is full of — is
        // NOT reinterpreted: a 2x "stretch" is far outside the clamp, and the honest
        // answer for two tracks that far apart is the fade the app already had.
        MixMatch doubleTempo = MixMatch.between(beat(87d, 0.9f), beat(174d, 0.9f), OVERLAP);
        System.out.println("87 vs 174: " + doubleTempo);
        assertFalse(doubleTempo.suitable());
    }

    @Test
    public void aPairAlreadyOnTheSameGridIsNotStretchedAtAll() {
        // 120.0 and 120.4 hold together over ten seconds on their own (the drift is a
        // fraction of a beat), so pulling the tempo would be an artefact bought for
        // nothing.
        MixMatch match = MixMatch.between(beat(120d, 0.9f), beat(120.4d, 0.9f), OVERLAP);
        assertTrue(match.suitable());
        assertEquals(1d, match.speed(), 1e-9d);
        assertTrue(match.note(), match.note().contains("tempo already holds"));
    }

    @Test
    public void aGridThatIsNotTrustworthyIsNeverMixed() {
        MixMatch weak = MixMatch.between(beat(120d, 0.9f), beat(121d, 0.2f), OVERLAP);
        assertFalse(weak.suitable());
        assertTrue(weak.note(), weak.note().contains("not trustworthy"));
        assertFalse(MixMatch.between(null, beat(120d, 0.9f), OVERLAP).suitable());
    }

    // --- key ------------------------------------------------------------------

    @Test
    public void aConfidentKeyPairInTheSameKeyIsNotShifted() {
        MixMatch match = MixMatch.between(
                beat(120d, 0.9f, key(0, true, 0.8f)),
                beat(120d, 0.9f, key(0, true, 0.8f)), OVERLAP);
        assertTrue(match.suitable());
        assertEquals(0, match.semitones());
        assertEquals(1d, match.pitch(), 1e-9d);
        System.out.println("C major / C major: " + match.note());
    }

    @Test
    public void aClashingKeyIsShiftedOntoTheOutgoingTracksKey() {
        // C major against D major: one semitone down (or two) puts D's profile on C's,
        // and D major is a whole tone above C, so the best shift is -2.
        MixMatch match = MixMatch.between(
                beat(120d, 0.9f, key(0, true, 0.9f)),
                beat(120d, 0.9f, key(2, true, 0.9f)), OVERLAP);
        System.out.println("C major / D major: " + match.note());
        assertTrue(match.note(), match.suitable());
        assertTrue("expected a downward shift, got " + match.semitones() + " (" + match.note() + ")",
                match.semitones() < 0);
        assertTrue(Math.abs(match.semitones()) <= MixMatch.MAX_SEMITONES);
        assertEquals(Math.pow(2d, match.semitones() / 12d), match.pitch(), 1e-9d);
    }

    @Test
    public void aShiftThatIsAllowedButNotWorthMakingLeavesTheTrackAlone() {
        // The profiles are measurements from this app's own library — White Iverson
        // (66.1BPM, G major) and The Other Side Of Paradise (64.0BPM, B minor) — and
        // the case they make is the one the shift decision used to get wrong: the
        // tempo is 1.033x, inside the clamp; the profiles are 0.18 apart, well under
        // MAX_KEY_DISTANCE; and the only shift the wheel allows between them (-2)
        // moves them to 0.17. Refusing here says "these keys clash", which is false —
        // 0.18 is this class's own statement that they can be heard together. The
        // answer is to apply nothing.
        KeyProfile postMalone = new KeyProfile(7, true, 0.94f, new double[]{
                0.0778, 0.0489, 0.1167, 0.0353, 0.0923, 0.0378, 0.0543, 0.1431, 0.0627, 0.1536, 0.0541, 0.1236});
        KeyProfile glassAnimals = new KeyProfile(11, false, 1.0f, new double[]{
                0.0759, 0.0702, 0.1157, 0.0504, 0.0703, 0.0483, 0.1197, 0.0705, 0.0428, 0.0870, 0.0987, 0.1504});
        MixMatch match = MixMatch.between(
                new BeatProfile(66.1d, 639L, 0.43f, postMalone),
                new BeatProfile(64.0d, 294L, 0.75f, glassAnimals), OVERLAP);
        System.out.println("White Iverson -> The Other Side Of Paradise: " + match.note());
        assertTrue(match.note(), match.suitable());
        assertEquals("a shift that does not help must not be applied", 0, match.semitones());
        assertTrue(match.note(), match.note().contains("nothing is applied"));
    }

    @Test
    public void keysThatCannotBeBroughtTogetherAreRefused() {
        // Two keys a tritone apart, each fully confident: no shift inside ±2 helps, and
        // the improvement is nowhere near the threshold either.
        MixMatch match = MixMatch.between(
                beat(120d, 0.9f, key(0, true, 0.95f)),
                beat(120d, 0.9f, key(6, true, 0.95f)), OVERLAP);
        System.out.println("C major / F# major: " + match.note());
        assertFalse(match.suitable());
        assertTrue(match.note(), match.note().contains("keys clash"));
    }

    @Test
    public void aMissingOrUnconfidentKeyIsRefusedRatherThanGuessed() {
        // A grid with no key estimate beside it (an older cache, a track the key
        // estimator found nothing tonal in): the pair is not mixed, because there is
        // nothing to say whether the keys clash.
        BeatProfile keyless = new BeatProfile(120d, 0L, 0.9f);
        MixMatch noKey = MixMatch.between(keyless, keyless, OVERLAP);
        assertFalse(noKey.suitable());
        assertTrue(noKey.note(), noKey.note().contains("no key measured"));

        MixMatch weakKey = MixMatch.between(beat(120d, 0.9f, key(0, true, 0.9f)),
                beat(120d, 0.9f, key(0, true, 0.1f)), OVERLAP);
        assertFalse(weakKey.suitable());
        assertTrue(weakKey.note(), weakKey.note().contains("key not trustworthy"));
    }

    @Test
    public void theSameKeyAndItsRelativeAreOneCamelotNumber() {
        // 8A is A minor, 8B its relative major C, 9A the fifth above (E minor).
        assertEquals(8, KeyProfile.camelotNumber(9, false));
        assertEquals(8, KeyProfile.camelotNumber(0, true));
        assertEquals(9, KeyProfile.camelotNumber(4, false));
        assertEquals(7, KeyProfile.camelotNumber(2, false));
        assertEquals("8A", KeyProfile.camelotLabel(9, false));
        assertEquals("8B", KeyProfile.camelotLabel(0, true));
        // 12A is C# minor and must not come out as 0A.
        assertEquals(12, KeyProfile.camelotNumber(1, false));

        assertTrue(key(9, false, 0.9f).camelotCompatible(key(0, true, 0.9f), 0));   // Am / C
        assertTrue(key(9, false, 0.9f).camelotCompatible(key(4, false, 0.9f), 0));  // Am / Em
        assertTrue(key(9, false, 0.9f).camelotCompatible(key(4, false, 0.9f), -2)); // Em down 2 to Dm, 7A
        assertFalse(key(9, false, 0.9f).camelotCompatible(key(10, false, 0.9f), 0)); // Am / A#m
    }

    // --- the platform instruction ---------------------------------------------

    @Test
    public void theInstructionCarriesWhatThePlatformNeeds() {
        IncomingMix mix = IncomingMix.of(0.952381d, Math.pow(2d, -1d / 12d), 5000L);
        assertEquals(0.952381d, mix.speed(), 1e-9d);
        assertEquals(0.943874d, mix.pitch(), 1e-5d);
        assertEquals(5000L, mix.bassSwapAtMs());
        assertFalse(mix.isIdentity());
        assertTrue(mix.sameTempoAndPitch(IncomingMix.of(0.952381d, 0.943874d, -1L)));
        assertFalse(mix.sameTempoAndPitch(IncomingMix.IDENTITY));
        assertTrue(IncomingMix.IDENTITY.isIdentity());
    }

    // --- the claim the tempo match makes: the beats then COINCIDE -------------

    /**
     * A stretched incoming track does not merely start on one of its own beats —
     * its whole grid lands on the outgoing track's grid, so the two cannot drift
     * apart over the overlap.
     *
     * <p>This is the arithmetic the phase claim rests on, done in the open: the
     * incoming track plays from one of its own beats ({@code entry}) at speed
     * {@code r = periodB / periodA}, so at wall-clock time {@code t} after the ramp
     * starts it is {@code entry + r*t} into its own file, and its k-th beat therefore
     * falls at {@code t = k * periodB / r = k * periodA} — the outgoing track's beats,
     * which are {@code periodA} apart from the ramp start by construction
     * ({@link BeatProfile#snapOverlapMs}). Measured below across the clamp.
     */
    @Test
    public void aStretchedIncomingTrackLandsOnTheOutgoingTracksBeats() {
        long duration = 200_000L;
        double worstDriftMs = 0d;
        int checked = 0;
        int stretched = 0;
        int leftAlone = 0;
        for (double bpmA : new double[]{90d, 100d, 120d, 128d, 140d, 174d}) {
            for (double ratio : new double[]{-0.075d, -0.03d, 0.02d, 0.075d}) {
                BeatProfile a = beat(bpmA, 0.9f);
                // The clamp bounds the stretch that is APPLIED (the incoming track's
                // own speed), so B is built from the ratio and not from a tempo
                // difference: those are not the same number, and a -7.5% difference in
                // BPM is a +8.1% stretch, which is outside the clamp.
                BeatProfile b = beat(bpmA / (1d + ratio), 0.9f);
                assertEquals("this pair must be inside the clamp",
                        ratio, MixMatch.speedFor(a, b) - 1d, 1e-9d);
                MixMatch match = MixMatch.between(a, b, OVERLAP);
                assertTrue("expected a mix for A=" + a.bpm() + " B=" + b.bpm() + ": " + match,
                        match.suitable());
                double r = match.speed();
                long periodA = Math.round(a.periodMs());
                // The overlap a matched mix would use, from the same arithmetic the
                // controller runs: ten seconds of the outgoing track, snapped to a
                // whole number of its beats.
                long overlap = a.snapOverlapMs(OVERLAP, duration, 250L);
                long rampStart = duration - 250L - overlap;
                long offset = Math.floorMod(rampStart - a.firstBeatMs(), periodA);
                assertEquals("the ramp must start on a beat of A", 0L, offset);
                // The incoming track starts on one of B's beats...
                long entry = b.beatAtOrAfter(0L);
                assertEquals("the entry must be a beat of B", 0L,
                        Math.floorMod(entry - b.firstBeatMs(),
                                Math.max(1L, Math.round(b.periodMs()))));
                // ... and every beat of it after that lands on one of A's.
                //
                // Two branches, and both are correct behaviour: a pair already close
                // enough to hold together is NOT stretched (its beats then lean on each
                // other within the half-beat tolerance that was always the alignment's
                // rule), while a pair that needed the stretch lands exactly — that is
                // the claim this test exists for.
                int beats = (int) Math.round((double) overlap / periodA);
                double pairWorst = 0d;
                for (int k = 0; k <= beats; k++) {
                    double wallMs = (entry + k * b.periodMs() - entry) / r;
                    pairWorst = Math.max(pairWorst, Math.abs(wallMs - k * a.periodMs()));
                    checked++;
                }
                if (r != 1d) {
                    worstDriftMs = Math.max(worstDriftMs, pairWorst);
                    stretched++;
                } else {
                    assertTrue("A=" + a.bpm() + " B=" + b.bpm() + " was left alone but is not"
                                    + " compatible",
                            BeatProfile.gridsCompatible(a, b, overlap));
                    assertTrue("A=" + a.bpm() + " B=" + b.bpm() + " drifts " + pairWorst
                                    + "ms while unstretched",
                            pairWorst <= b.periodMs() / 2d + 1d);
                    leftAlone++;
                }
            }
        }
        System.out.println(String.format(Locale.US,
                "stretched grids: %d pairs stretched, %d left alone; %d beat instants checked,"
                        + " worst offset on a stretched pair %.4fms",
                stretched, leftAlone, checked, worstDriftMs));
        assertTrue("no pair was stretched, the test proves nothing", stretched > 0);
        assertTrue("beats drifted " + worstDriftMs + "ms", worstDriftMs < 1d);

        // The contrast that makes the point: the same pairs WITHOUT the stretch slide
        // apart by far more than half a beat over ten seconds, which is exactly why
        // P4 used to skip the alignment rather than hold it.
        BeatProfile a = beat(120d, 0.9f);
        BeatProfile b = beat(120d * 1.06d, 0.9f);
        long drift = BeatProfile.gridDriftMs(a, b, OVERLAP);
        System.out.println(String.format(Locale.US,
                "120 vs 127.2 BPM over %dms un-stretched: %dms of drift (half a beat of B is %dms)",
                OVERLAP, drift, Math.round(b.periodMs() / 2d)));
        assertFalse(BeatProfile.gridsCompatible(a, b, OVERLAP));
    }

    // --- helpers --------------------------------------------------------------

    /** A profile with a grid and a confident key in C major, which is what every
     *  real one has: the tempo cases below are about the tempo, so they hand the
     *  judgement a pair whose keys cannot be the reason for anything. */
    private static BeatProfile beat(double bpm, float confidence) {
        return new BeatProfile(bpm, 0L, confidence, key(0, true, 0.9f));
    }

    private static BeatProfile beat(double bpm, float confidence, KeyProfile key) {
        return new BeatProfile(bpm, 0L, confidence, key);
    }

    /** A key profile that IS that key: the tonic, its third, fifth and octave carry
     *  the weight, which is what a real triad's profile looks like after the
     *  estimator's fold. */
    private static KeyProfile key(int tonic, boolean major, float strength) {
        double[] chroma = new double[12];
        chroma[0] = 1d;
        chroma[major ? 4 : 3] = 0.7d;
        chroma[7] = 0.6d;
        chroma[major ? 11 : 10] = 0.25d;
        // A little weight on the other side of the circle so the metric has
        // something to work with (a pure triad is a degenerate profile).
        chroma[2] = 0.1d;
        chroma[5] = 0.1d;
        double[] rotated = KeyProfile.rotated(chroma, tonic);
        return new KeyProfile(tonic, major, strength, rotated);
    }
}
