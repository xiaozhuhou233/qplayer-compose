package dev.t1m3.qplayer.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The fusion's arithmetic, and the acceptance the round is measured against: the anchors where the
 * spec puts them, the three-state table with its 80 ms splices, the carried passage at the outgoing
 * track's own tempo, the outgoing's voice left behind, and the pulse holding through the seam.
 *
 * <p>All of it on synthetic stems: the numbers here are the same numbers the render reports, which
 * is the whole reason the planner and the measurement are pure Java (see {@link StemFusion}).
 */
public class StemFusionTest {

    private static final int RATE = 44_100;

    // --- the anchors ---------------------------------------------------------

    /** A grid: bar lines every {@code barMs} from {@code fromMs}, {@code count} of them. */
    private static double[] bars(double barMs, double fromMs, int count) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) out[i] = fromMs + i * barMs;
        return out;
    }

    private static StemFusion.Input input(long aDurMs, long blendMs, long removalMs,
                                          double aBeatMs, double bBeatMs, double[] aBars,
                                          double[] bBars) {
        return new StemFusion.Input(aDurMs, blendMs, removalMs, removalMs - blendMs,
                aBeatMs, 0d, bBeatMs, 0d, 1d, aBars, bBars, StemFusion.NO_VOICE_MEASUREMENT);
    }

    @Test
    public void theAnchorsAreWhereTheSpecPutsThem() {
        // A and B both on a 500 ms beat (120 BPM), so a bar is 2000 ms and the tempo lock is exact
        // (speed 1 — the pair's grids already hold, the ordinary case).
        StemFusion.Plan plan = StemFusion.plan(input(240_000L, 20_000L, 35_000L, 500d, 500d,
                bars(2000d, 0d, 120), bars(2000d, 0d, 120)));
        assertTrue(plan.reason, plan.valid);
        // The target is aDur - 250 - blendMs = 219750; the nearest line of A's grid is 220000.
        assertEquals(220_000L, plan.junctionMs);
        // The content start is removal - blend = 15000; the first B bar line at or after it is
        // 16000, and (the spec's own rule) the deck starts there.
        assertEquals(15_000L, 15_000L);
        assertEquals(16_000L, plan.entryMs);
        assertEquals(18_000L, plan.swapMs);
        assertEquals(20_000L, plan.bassMs);
        assertEquals(22_000L, plan.fusionEndMs);
        assertEquals(6_000L, plan.windowMs);
        // ⚠️ The material is drawn from bassMs, not from a bar count: the table keeps A's rows in
        // the file over [entry, bassMs + CUT_MS] = 2 bars + one splice, i.e. 4080 ms of A's own
        // file at speed 1. (The spec's old 3*aBar = 6000 was 2 s generous here and 1.8 s short on
        // an unlocked pair; see StemFusionTest.theFourRealPairsVerdicts.)
        assertEquals(4_080L, plan.sourceSpanMs);
        assertEquals(220_000L, plan.sourceFromMs);
        // The material window is that passage plus the junction search's own band (round 3: ±2 bars
        // of the outgoing's grid, narrowed to what the separation budget has left) and the lead the
        // take's alignment wants on either side of the target.
        assertEquals(2_960L, plan.searchBandMs);
        assertEquals(215_790L, plan.materialFromMs);
        assertEquals(12_000L, plan.materialWindowMs);
        assertEquals(250L, plan.junctionShiftMs);
        assertTrue(plan.materialWindowMs <= StemFusion.FUSION_TAIL_MAX_MS);
        assertTrue("the fusion stays inside the vocal-free window", plan.fusionEndMs <= 35_000L);
        assertTrue(plan.phaseMatched);
        assertEquals(0d, plan.phaseErrorMs, 1e-9);
        assertEquals(0d, plan.lockError, 1e-9);
        assertEquals(6, plan.splices());
        // No measurement was handed in, so neither preference moved the junction.
        assertFalse(plan.grooveAtJunction);
        assertFalse(plan.quietAtJunction);
        // The incoming's bed arrives over one bar of its own grid — the bar the outgoing's drums
        // leave on, so the two are heard as one event.
        assertEquals(plan.swapMs, plan.bedFadeMs);
        assertEquals(2_000L, plan.bedFadeMs - plan.entryMs);
    }

    @Test
    public void aJunctionWhereTheOutgoingIsNotSingingIsPreferred() {
        // Two lines inside the search band and inside the material window: 219000 (nearest, 750
        // away) and 220500 (750 the other way). The outgoing's voice is measured quiet in the
        // second one's own bar only, so the preference moves the junction onto it -- the spec's
        // "stop between phrases rather than mid-word" -- and the deck is cut 750 ms later than
        // distance alone would put it.
        double[] aBars = {219_000d, 220_500d};
        StemFusion.Input in = new StemFusion.Input(240_000L, 20_000L, 35_000L, 15_000L,
                500d, 0d, 500d, 0d, 1d, aBars, bars(2000d, 0d, 120),
                (atMs, beatMs) -> atMs == 220_500L);
        StemFusion.Plan quiet = StemFusion.plan(in);
        assertTrue(quiet.reason, quiet.valid);
        assertEquals(220_500L, quiet.junctionMs);
        assertTrue(quiet.quietAtJunction);
        assertEquals(750L, quiet.junctionShiftMs);

        // No measurement at all: the nearest line wins, which is the preference declining.
        StemFusion.Plan plain = StemFusion.plan(input(240_000L, 20_000L, 35_000L, 500d, 500d,
                aBars, bars(2000d, 0d, 120)));
        assertTrue(plain.reason, plain.valid);
        assertEquals(219_000L, plain.junctionMs);
        assertFalse(plain.quietAtJunction);

        // A line outside the search band is not a candidate at all, even though the preference
        // would have taken it: 224000 is 4250 ms from the target, past the 3960 ms the band and
        // the take's lead reach.
        StemFusion.Plan tooFar = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 500d, 0d, 1d, new double[]{219_000d, 224_000d},
                bars(2000d, 0d, 120), (atMs, beatMs) -> atMs == 224_000L));
        assertTrue(tooFar.reason, tooFar.valid);
        assertEquals(219_000L, tooFar.junctionMs);
        assertFalse(tooFar.quietAtJunction);
    }

    /**
     * ⚠️ Round 3's junction preference, and the reason it exists: the pair the user listened to had
     * its nearest bar line on a pad-only breakdown of the outgoing track, so the fusion carried a
     * pad where a groove should have been and the junction stepped −7 dB. A bar where the outgoing's
     * own drums or low end are playing wins over a nearer one (and over a quieter one) — the fusion
     * then carries the outgoing track's rhythm out of the handover instead of its pad.
     */
    @Test
    public void aJunctionWhereTheOutgoingGrooveIsPlayingWins() {
        double[] aBars = {218_000d, 220_000d};
        // The nearest line (220000, 250 ms away) is quiet but has no groove; 218000 is 1750 ms
        // away, loud in the low end, and its voice is not quiet. The groove decides.
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 500d, 0d, 1d, aBars, bars(2000d, 0d, 120),
                (atMs, beatMs) -> atMs == 220_000L, (atMs, beatMs) -> atMs == 218_000d));
        assertTrue(plan.reason, plan.valid);
        assertEquals(218_000L, plan.junctionMs);
        assertTrue(plan.grooveAtJunction);
        assertFalse("the groove beats the quieter line", plan.quietAtJunction);
        assertEquals(-1_750L, plan.junctionShiftMs);

        // Among two lines that both have the groove, the nearer one wins.
        StemFusion.Plan both = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 500d, 0d, 1d, aBars, bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT, (atMs, beatMs) -> true));
        assertTrue(both.reason, both.valid);
        assertEquals(220_000L, both.junctionMs);
        assertTrue(both.grooveAtJunction);

        // And with no groove anywhere the search falls back to today's choice: the nearest line
        // (the quiet one, when one is quiet), which is what the make-up gain below then answers.
        StemFusion.Plan none = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 500d, 0d, 1d, aBars, bars(2000d, 0d, 120),
                (atMs, beatMs) -> atMs == 220_000L));
        assertEquals(220_000L, none.junctionMs);
        assertTrue(none.quietAtJunction);
        assertFalse(none.grooveAtJunction);
    }

    @Test
    public void theJunctionTheDeckIsCutOnIsAlwaysALineOfTheOutgoingGrid() {
        // Every line of every grid is a candidate only within +/-1 bar of the target, and the one
        // chosen is always a line of the grid itself: the boundary cuts the live deck at this
        // position, and a position between two lines is a beat nobody measured.
        double[] aBars = bars(2000d, 125d, 120);
        StemFusion.Plan plan = StemFusion.plan(input(240_000L, 20_000L, 35_000L, 500d, 500d,
                aBars, bars(2000d, 0d, 120)));
        assertTrue(plan.reason, plan.valid);
        boolean onGrid = false;
        for (double at : aBars) if (Math.round(at) == plan.junctionMs) onGrid = true;
        assertTrue("the junction is one of the outgoing track's own bar lines", onGrid);
        // target 219750 +/- 2000: the nearest line of this grid is 220125 (375 away), where the
        // plain 2000 ms grid's would have been 220000.
        assertEquals(220_125L, plan.junctionMs);
    }

    @Test
    public void thePhaseMatchIsReportedAndTheFallbackIsTheSameLine() {
        // The candidates are whole bars of the incoming's grid, so they share a beat phase: the
        // choice among them cannot change the phase, and the rule degenerates to "the first bar
        // line at or after the content start" -- which is the spec's own fallback. What the
        // measurement is still good for is being *reported*: this grid's downbeat sits a quarter
        // beat off the outgoing's, so the seam is a quarter of a beat out and says so.
        double[] bBars = bars(2000d, 250d, 120);
        double[] aBars = bars(2000d, 0d, 120);
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 500d, 0d, 1d, aBars, bBars,
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(plan.reason, plan.valid);
        assertEquals(16_250L, plan.entryMs);
        // A's phase at the junction (220000) is 0; B's at the entry (16250) is 250 ms into its own
        // bar -- half a beat out, which is as much as a whole-bar candidate can be while still
        // being the second half of the bar rather than the first.
        assertEquals(250d, plan.phaseErrorMs, 1e-9);
        assertTrue("a 250 ms match is not a match", !plan.phaseMatched);
        assertEquals(plan.entryMs, 16_250L);
    }

    @Test
    public void thePhaseDistanceIsCircular() {
        // 10 ms and 490 ms in a 500 ms period are 20 ms apart, not 480: the difference of two
        // phases is a distance on a circle, and the plain subtraction is not one.
        assertEquals(20d, StemFusion.phaseDistance(10d, 490d, 500d), 1e-9);
        assertEquals(480d, Math.abs(10d - 490d), 1e-9);
        assertEquals(0d, StemFusion.phaseDistance(0d, 500d, 500d), 1e-9);
        assertEquals(50d, StemFusion.phaseDistance(100d, 50d, 500d), 1e-9);
    }

    @Test
    public void everyValidityClauseRefusesTheFusionOnItsOwn() {
        // A grid, B grid, a known blend, the tempo lock, the cost bound, a junction inside the
        // outgoing's file, a known content start, the fusion inside the vocal-free window -- plus
        // the two the renderer owns (the outgoing's file has to hold the passage, and the window
        // that gets separated has to fit the cost bound). Each one on its own.
        assertTrue(StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L, 15_000L,
                500d, 0d, 500d, 0d, 1d, new double[0], bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("outgoing track"));
        assertTrue(StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L, 15_000L,
                500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), null,
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("incoming track"));
        assertTrue(StemFusion.plan(new StemFusion.Input(240_000L, 0L, 35_000L, 15_000L,
                500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("blend length"));
        // ⚠️ The tempo lock, which is the clause the prototype's 2:1 pair fails: a 1000 ms beat
        // against a 500 ms one played at x1 is 100% out, and no material can make that fusable --
        // the bar lines slide against each other and every cut lands off the beat.
        StemFusion.Plan unlocked = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 1000d, 0d, 500d, 0d, 1d, bars(4000d, 0d, 60), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertFalse(unlocked.reason, unlocked.valid);
        assertTrue(unlocked.reason, unlocked.reason.contains("do not lock"));
        assertEquals(1d, unlocked.lockError, 1e-9);
        // ...and it is checked before anything is decoded: the same pair is refused up front.
        assertNotNull(StemFusion.refusal(240_000L, 20_000L, 35_000L, 15_000L, 1000d, 500d, 1d));
        assertTrue(StemFusion.refusal(240_000L, 20_000L, 35_000L, 15_000L, 1000d, 500d, 1d)
                .contains("do not lock"));
        assertFalse(StemFusion.possible(240_000L, 20_000L, 35_000L, 15_000L, 1000d, 500d, 1d));
        // The cost bound, on a pair that DOES lock: two 1500 ms beats are bars of 6 s, so the
        // passage is 12 s of the incoming's file and the material window 14 s of A's.
        assertTrue(StemFusion.plan(new StemFusion.Input(400_000L, 20_000L, 80_000L, 15_000L,
                1500d, 0d, 1500d, 0d, 1d, bars(6000d, 0d, 60), bars(6000d, 0d, 60),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("one render may separate"));
        assertTrue(StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L, -1L,
                500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("own start is not known"));
        // The outgoing deck is cut at the junction, so material past the end of its file does not
        // exist: a short blend and a long passage is a fusion that would run off the end of A.
        assertTrue(StemFusion.plan(input(100_000L, 4_000L, 20_000L, 500d, 500d,
                bars(2000d, 0d, 60), bars(2000d, 0d, 60))).reason.contains("file ends"));
        // And the whole fusion has to stay inside the window the incoming's vocals are out for.
        StemFusion.Plan late = StemFusion.plan(input(200_000L, 5_000L, 9_500L, 500d, 500d,
                bars(2000d, 0d, 120), bars(2000d, 0d, 120)));
        assertFalse(late.reason, late.valid);
        assertTrue(late.reason, late.reason.contains("past the 9500ms"));
        assertTrue(StemFusion.possible(240_000L, 20_000L, 35_000L, 15_000L, 500d, 500d, 1d));
        assertNull(StemFusion.refusal(240_000L, 20_000L, 35_000L, 15_000L, 500d, 500d, 1d));
    }

    /**
     * The four pairings the round-18 prototype measured on real material, with the numbers the
     * harness's own scan produced (`D:\qplayer-dev\harness\fusion\{grids,qpair-scan}.json`):
     * the grids, the blend and the content start are the harness's, and the verdict is what this
     * planner makes of them.
     *
     * <p>Only one of the four locks. The other three are refused by the lock clause and <em>by
     * nothing else</em> — their material is not the question, their grids are — which is the point
     * of the clause: a bar-line cut on a pair whose grids slide is an off-beat edit.
     */
    @Test
    public void theFourRealPairsVerdicts() {
        // owa -> paradise: 476.6 ms against 937.6 ms (a ratio of 1.9672, i.e. an octave apart, and
        // the controller does not stretch it because neither grid is trustworthy). 49.2% out. The
        // prototype rendered this one — and the user heard its junction.
        assertLocked(false, 476.598639d, 937.55102d, 1d, 0.4917d, "audio_owa", "audio_paradise");
        // obsessed -> owa: 838.8 ms against 476.6 ms (ratio 0.5682). 76.0% out.
        assertLocked(false, 838.77551d, 476.598639d, 1d, 0.7599d, "audio_obsessed", "audio_owa");
        // owa -> audio1: 476.6 ms against 459.6 ms — related (within 8%), and the grids are close,
        // but x1.0 is what the controller applies (A's own grid is not trustworthy, so nothing is
        // stretched) and 3.7% is past the 2% the lock allows.
        assertLocked(false, 476.598639d, 459.637188d, 1d, 0.0369d, "audio_owa", "audio1");
        // huai -> owa: 472.6 ms against 476.6 ms — 0.84% out, locked, and the one pairing of the
        // four that a fusion can be built on. Its anchors are the prototype's own.
        assertLocked(true, 472.60771d, 476.598639d, 1d, 0.0084d, "audio_huai", "audio_owa");

        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(187_241L, 15_000L, 15_000L,
                0L, 472.60771d, 210d, 476.598639d, 12d, 1d,
                bars(1890.43084d, 210d, 100), bars(1906.394556d, 12d, 100),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(plan.reason, plan.valid);
        // The prototype's own anchors, to the millisecond: the junction is the bar line 248 ms
        // after the target, the deck starts on B's first bar line (12 ms), and all of A is gone
        // 5.7 s into B's file — inside the 15 s the vocals are out for.
        assertEquals(171_991L, Math.round(187_241d - 250d - 15_000d));
        assertEquals(172_239L, plan.junctionMs);
        assertEquals(12L, plan.entryMs);
        assertEquals(1_918L, plan.swapMs);
        assertEquals(3_824L, plan.bassMs);
        assertEquals(5_731L, plan.fusionEndMs);
        assertEquals(5_719L, plan.windowMs);
        // The carry's own span: 2 bars of B's grid (3812.8 ms) + one splice = 3892.8 -> 3893 ms of
        // A's file. The spec's old 3*aBar would have been 5671 ms — 1.8 s more than the gesture
        // needs here — and on a pair whose bars are not equal it would have been short.
        assertEquals(3_893L, plan.sourceSpanMs);
        // Round 3's search band: ±2 bars of A's grid (3780.9 ms) narrowed to the 6107 ms the
        // separation budget has left after the carry, i.e. 3053 ms each way.
        assertEquals(3_053L, plan.searchBandMs);
        assertEquals(171_991L - 3_053L - StemFusion.A_TAIL_SLACK_MS, plan.materialFromMs);
        assertEquals(11_999L, plan.materialWindowMs);
        assertTrue(plan.describe(), plan.materialWindowMs <= StemFusion.FUSION_TAIL_MAX_MS);
        assertEquals(248L, plan.junctionShiftMs);
        // And the whole thing is a bar chart of B's grid: three bars of 1906.4 ms.
        assertEquals(Math.round(3d * 1906.394556d), plan.windowMs);
    }

    /** A pair's lock verdict, named the way the log names it. */
    private static void assertLocked(boolean expected, double aBeatMs, double bBeatMs, double speed,
                                     double expectedError, String a, String b) {
        boolean locked = StemFusion.locked(aBeatMs, bBeatMs, speed);
        assertEquals(a + " -> " + b + ": lock error " + StemFusion.lockError(aBeatMs, bBeatMs, speed)
                + " against the " + StemFusion.LOCK_TOLERANCE + " tolerance",
                expected, locked);
        assertEquals(a + " -> " + b, expectedError, StemFusion.lockError(aBeatMs, bBeatMs, speed),
                Math.max(1e-4d, expectedError * 0.02d));
        String refusal = StemFusion.refusal(200_000L, 15_000L, 15_000L, 0L, aBeatMs, bBeatMs, speed);
        if (expected) {
            assertNull(a + " -> " + b + ": " + refusal, refusal);
        } else {
            assertNotNull(a + " -> " + b + " should be refused", refusal);
            assertTrue(a + " -> " + b + ": " + refusal, refusal.contains("do not lock"));
        }
    }

    // --- the table -----------------------------------------------------------

    private static StemFusion.Plan fixture() {
        StemFusion.Plan plan = StemFusion.plan(input(240_000L, 20_000L, 35_000L, 500d, 500d,
                bars(2000d, 0d, 120), bars(2000d, 0d, 120)));
        assertTrue(plan.reason, plan.valid);
        return plan;
    }

    private static double db(double ratio) {
        return 20 * Math.log10(ratio);
    }

    @Test
    public void theTableIsThreeStatesWithEightyMillisecondSplices() {
        StemFusion.Plan plan = fixture();
        long entry = plan.entryMs;
        long swap = plan.swapMs;
        long bass = plan.bassMs;
        long end = plan.fusionEndMs;
        double unity = 1d;
        double aOther = Math.pow(10d, StemFusion.A_OTHER_DB / 20d);

        // [entry, swap): both backings' grooves, the outgoing's melodic row 12 dB down. The
        // incoming's bed is NOT there yet — it is the table's one arrival that is ramped, and it
        // is measured on its own below.
        assertEquals(unity, StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS, entry), 1e-9);
        assertEquals(unity, StemFusion.gainAt(plan, true, StemGesture.Stem.BASS, entry), 1e-9);
        assertEquals(aOther, StemFusion.gainAt(plan, true, StemGesture.Stem.OTHER, entry), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, entry), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.BASS, entry), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER, entry), 1e-9);

        // The splice at swapMs: half way through it each side is at equal power, and it is over
        // by swapMs + CUT_MS -- 80 ms, not a fade, and the two rows cross on the same line.
        assertEquals(unity, StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS, swap - 1), 1e-9);
        assertEquals(Math.sin(Math.PI / 4), StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS,
                swap + StemFusion.CUT_MS / 2), 1e-9);
        assertEquals(Math.cos(Math.PI / 4), StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS,
                swap + StemFusion.CUT_MS / 2), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS, swap
                + StemFusion.CUT_MS), 1e-9);
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, swap
                + StemFusion.CUT_MS), 1e-9);
        assertEquals(unity, StemFusion.gainAt(plan, true, StemGesture.Stem.BASS, swap + 1L), 1e-9);

        // [bass, fusionEnd): the low end changes hands and the outgoing's material is gone.
        assertEquals(0d, StemFusion.gainAt(plan, true, StemGesture.Stem.BASS, bass
                + StemFusion.CUT_MS), 1e-9);
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.BASS, bass
                + StemFusion.CUT_MS), 1e-9);
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER, bass
                + StemFusion.CUT_MS), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, true, StemGesture.Stem.OTHER, bass
                + StemFusion.CUT_MS), 1e-9);

        // Outside the window: today's content (the incoming's own backing at unity) and nothing
        // of the outgoing's.
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, entry - 1L), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, true, StemGesture.Stem.BASS, entry - 1L), 1e-9);
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER, end), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS, end), 1e-9);
    }

    /**
     * ⚠️ Round 18's other half of the junction: the incoming's bed arrives over {@link
     * StemFusion#BED_FADE_BARS} bar, equal-power, and it is the one ramp in the table. The user
     * asked for it (「新歌的进入请用淡入效果」) and it is what keeps the level continuous while the
     * outgoing's voice leaves: a bed arriving by cut on the same bar the voice departs is a step
     * where the ear is listening for continuity.
     */
    @Test
    public void theIncomingBedFadesInOverItsFirstBar() {
        StemFusion.Plan plan = fixture();
        long entry = plan.entryMs;
        long swap = plan.swapMs;
        long fade = plan.bedFadeMs - entry;
        assertEquals("one bar of the incoming's grid", Math.round(plan.bBarMs), fade);
        assertEquals(entry + fade, plan.bedFadeMs);
        // Silent at the entry, unity from the fade's end on, and equal-power in between: half way
        // up the bar each side of the hand-over is at sin(pi/4).
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER, entry), 1e-9);
        assertEquals(Math.sin(Math.PI / 4), StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER,
                entry + fade / 2), 1e-9);
        assertEquals(1d, StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER,
                entry + fade - 1), 1e-5);
        assertEquals(1d, StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER, plan.bassMs), 1e-9);
        // Monotone over the whole fade, and it never overshoots the unity it arrives at.
        double previous = -1d;
        for (long at = entry; at <= entry + fade; at += 7L) {
            double g = StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER, at);
            assertTrue("gain " + g + " at " + at, g >= previous - 1e-12 && g <= 1d + 1e-12);
            previous = g;
        }
        // The incoming's drums are NOT ramped like the bed: they are at exactly zero until their
        // own splice begins on the bar line the bed has just arrived on, and then arrive in 80 ms.
        // A faded drum swap would smear the pulse, which is the hand-over's signature.
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS,
                entry + fade / 2), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, swap - 1L), 1e-9);
        assertEquals(Math.sin(Math.PI / 4), StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS,
                swap + StemFusion.CUT_MS / 2), 1e-9);
    }

    @Test
    public void theOutgoingVoiceIsNeverInTheTable() {
        StemFusion.Plan plan = fixture();
        for (long at = plan.entryMs; at <= plan.fusionEndMs; at += 100L) {
            assertEquals("the outgoing's vocal row is never carried",
                    0d, StemFusion.gainAt(plan, true, StemGesture.Stem.VOCALS, at), 1e-9);
        }
        // And the incoming's own voice is the edit's own gate, untouched by the fusion.
        DjEdit.Plan edit = DjEdit.plan(30d, 33d);
        for (int i = 0; i < 40; i++) {
            double t = i * 1d;
            assertEquals(edit.vocalGainAt(t),
                    StemFusion.incomingGains(plan, edit).gainOf(StemGesture.Stem.VOCALS, t), 1e-12);
        }
    }

    // --- the material --------------------------------------------------------

    /** A synthetic stem: a decaying burst at every beat, silence between them. */
    private static float[][] bursts(double beatSec, double windowSec, double hz, double amp) {
        int frames = (int) Math.round(windowSec * RATE);
        float[][] out = new float[2][frames];
        int period = (int) Math.round(beatSec * RATE);
        int burst = (int) Math.round(0.18d * RATE);
        for (int at = 0; at + burst < frames; at += period) {
            for (int i = 0; i < burst; i++) {
                double decay = Math.exp(-i / (0.06d * RATE));
                float v = (float) (amp * decay * Math.sin(2 * Math.PI * hz * i / RATE));
                out[0][at + i] += v;
                out[1][at + i] += v;
            }
        }
        return out;
    }

    /** A tone, for a pad or a voice. */
    private static float[][] tone(double windowSec, double hz, double amp) {
        int frames = (int) Math.round(windowSec * RATE);
        float[][] out = new float[2][frames];
        for (int i = 0; i < frames; i++) {
            float v = (float) (amp * Math.sin(2 * Math.PI * hz * i / RATE));
            out[0][i] = v;
            out[1][i] = v;
        }
        return out;
    }

    private static float[][][] stems(float[][] drums, float[][] bass, float[][] other,
                                     float[][] vocals) {
        float[][][] out = new float[4][][];
        out[StemGesture.Stem.DRUMS.row()] = drums;
        out[StemGesture.Stem.BASS.row()] = bass;
        out[StemGesture.Stem.OTHER.row()] = other;
        out[StemGesture.Stem.VOCALS.row()] = vocals;
        return out;
    }

    private static int countBursts(float[] pcm, int frame, int from, int length) {
        int count = 0;
        boolean quiet = true;
        for (int i = 0; i + frame < length && from + i + frame < pcm.length; i += frame) {
            double peak = 0;
            for (int j = 0; j < frame; j++) peak = Math.max(peak, Math.abs(pcm[from + i + j]));
            if (peak > 0.25d && quiet) count++;
            quiet = peak <= 0.02d;
        }
        return count;
    }

    @Test
    public void thePassageIsStretchedByTheSpeedSoPlaybackReturnsItToTheOutgoingTempo() {
        // 0.5s beats of the outgoing track; the incoming deck plays the file 25% fast, so the
        // passage has to be 25% longer on the way in to be heard at the outgoing's own tempo.
        double beatSec = 0.5d;
        double speed = 1.25d;
        StemFusion.Plan plan = fixture();
        float[][][] a = stems(bursts(beatSec, 30d, 1800d, 0.6d), bursts(beatSec, 30d, 55d, 0.6d),
                tone(30d, 220d, 0.1d), tone(30d, 440d, 0.2d));
        // The separated window starts 1000 ms before the junction, as the plan says.
        float[][][] carried = StemFusion.carry(a, RATE, plan.materialFromMs, plan, speed, true);
        assertNotNull(carried[StemGesture.Stem.DRUMS.row()]);
        assertNotNull(carried[StemGesture.Stem.BASS.row()]);
        assertNotNull(carried[StemGesture.Stem.OTHER.row()]);
        assertNull("the outgoing's voice is never carried",
                carried[StemGesture.Stem.VOCALS.row()]);
        int frames = (int) Math.round(plan.windowMs * RATE / 1000d);
        assertEquals(frames, carried[StemGesture.Stem.DRUMS.row()][0].length);
        // In the file the 0.5 s beats are 0.625 s apart; heard at x1.25 they are 0.5 s apart again.
        // The material runs to the last cut's own splice (bassMs + CUT), so the audible part is the
        // take's own span stretched by the speed — the rest of the window is silence the table's
        // own gains are zero on.
        int audibleFrames = (int) Math.round(plan.sourceSpanMs * speed * RATE / 1000d);
        int inFile = countBursts(carried[StemGesture.Stem.DRUMS.row()][0],
                (int) Math.round(0.01d * RATE), 0, audibleFrames);
        assertEquals(plan.sourceSpanMs / (beatSec * 1000d), inFile, 1.0d);

        // And with no tempo applied the carry is sample-exact, starting exactly at the junction:
        // the first sample of the passage is A's own sample at the junction.
        float[][][] flat = StemFusion.carry(a, RATE, plan.materialFromMs, plan, 1d, true);
        int takeAt = (int) Math.round((plan.sourceFromMs - plan.materialFromMs) * RATE / 1000d);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a[StemGesture.Stem.BASS.row()][0][takeAt + i],
                    flat[StemGesture.Stem.BASS.row()][0][i], 1e-7f);
        }
    }

    @Test
    public void aMelodicCarryThatIsTheOutgoingVoiceIsCaught() {
        double windowSec = 30d;
        float[][] voice = tone(windowSec, 440d, 0.3d);
        float[][] melodyThatIsTheVoice = tone(windowSec, 440d, 0.29d);
        double same = StemFusion.voiceAlignment(melodyThatIsTheVoice, voice, RATE, 0L, 8_000L);
        assertTrue("a melodic row that is the voice aligns with it: " + same,
                same > StemFusion.VOICE_CARRY_LIMIT);
        // An unrelated pad does not: this is the measurement that decides dropping the melodic
        // carry rather than bailing out of the fusion altogether.
        double unrelated = StemFusion.voiceAlignment(tone(windowSec, 110d, 0.3d), voice, RATE,
                0L, 8_000L);
        assertTrue("an unrelated pad is not the voice: " + unrelated,
                unrelated < StemFusion.VOICE_CARRY_LIMIT);
    }

    // --- the acceptance ------------------------------------------------------

    /** One whole synthetic fusion: the outgoing's material carried into the window, the incoming's
     *  own rows under the table, rendered by {@link DjEdit#renderHead} exactly as the render does —
     *  including, when {@code limiter} is true, the head's own peak limiter.
     *
     *  <p>The outgoing's own master (the level the junction is judged against) is built the same way
     *  the renderer builds it: the sum of all four of A's rows over the {@link
     *  StemFusion#STEP_WINDOW_MS} before the junction, which is the last 500 ms of the
     *  {@link StemFusion#A_TAIL_SLACK_MS} of lead the material window carries. */
    private static StemFusion.Report run(StemFusion.Plan plan, double speed, boolean withMelody,
                                         float[][][] aStems, float[][][] bStems, double aBeatSec,
                                         double bBeatSec, boolean limiter) {
        return render(plan, speed, withMelody, aStems, bStems, aBeatSec, bBeatSec, limiter).report;
    }

    /** One rendered fusion: the report, the head that was rendered (the step's own subject) and
     *  the limiter that was in it — the three things a test about the junction needs together. */
    private static final class Ran {
        final StemFusion.Report report;
        final float[][] head;
        final DjEdit.Limiter limiter;

        Ran(StemFusion.Report report, float[][] head, DjEdit.Limiter limiter) {
            this.report = report;
            this.head = head;
            this.limiter = limiter;
        }
    }

    /**
     * Round 3's two passes, exactly as the renderer makes them: one render with no make-up, the
     * step it measures turned into a make-up gain on the outgoing's carried rows
     * ({@link StemFusion#makeupDb}), and the head rendered again with it.
     */
    private static Ran[] renderWithMakeup(StemFusion.Plan plan, double speed, boolean withMelody,
                                          float[][][] aStems, float[][][] bStems, double aBeatSec,
                                          double bBeatSec, boolean limiter) {
        Ran first = render(plan, speed, withMelody, aStems, bStems, aBeatSec, bBeatSec, limiter);
        double makeup = StemFusion.makeupDb(
                first.report.stepMeasured ? first.report.junctionStepDb : Double.NaN);
        if (!(makeup > 0d)) return new Ran[]{first, first};
        Ran lifted = render(plan, speed, withMelody, aStems, bStems, aBeatSec, bBeatSec, limiter,
                null, makeup);
        return new Ran[]{first, lifted};
    }

    private static Ran render(StemFusion.Plan plan, double speed, boolean withMelody,
                              float[][][] aStems, float[][][] bStems, double aBeatSec,
                              double bBeatSec, boolean limiter0) {
        return render(plan, speed, withMelody, aStems, bStems, aBeatSec, bBeatSec, limiter0, null,
                0d);
    }

    /**
     * The same render with the material it is MEASURED against given separately (the outgoing's
     * own stems as its file holds them, which is what a take that ended early has to be caught
     * against — see {@link #aCarryThatEndsBeforeItsOwnLastCutIsCaught}) and with the make-up gain
     * already applied to the carried rows.
     */
    private static Ran render(StemFusion.Plan plan, double speed, boolean withMelody,
                              float[][][] aStems, float[][][] bStems, double aBeatSec,
                              double bBeatSec, boolean limiter0, float[][][] reference,
                              double makeupDb) {
        float[][][] ref = reference != null ? reference : aStems;
        int frames = (int) Math.round(plan.windowMs * RATE / 1000d);
        float[][][] taken = StemFusion.carry(aStems, RATE, plan.materialFromMs, plan, speed,
                withMelody);
        // The material the file ADDS: the same rows with the table's own gains on them, and the
        // make-up gain the junction's own step asked for (A's rows only — see applyMakeup).
        float[][][] carried = StemFusion.applyMakeup(
                StemFusion.gate(taken, plan, withMelody, RATE), makeupDb);
        float[][][] incoming = new float[4][][];
        for (StemGesture.Stem stem : StemGesture.Stem.ALL) {
            incoming[stem.row()] = sliceFrames(bStems[stem.row()], 0, frames);
        }
        float[][][] carriedSource = new float[4][][];
        for (StemGesture.Stem stem : StemGesture.Stem.ALL) {
            carriedSource[stem.row()] = sliceFrames(ref[stem.row()],
                    (int) Math.round((plan.sourceFromMs - plan.materialFromMs) * RATE / 1000d),
                    (int) Math.round(plan.sourceSpanMs * RATE / 1000d));
        }
        DjEdit.Plan edit = DjEdit.plan(plan.fusionEndMs / 1000d, plan.fusionEndMs / 1000d + 2d);
        int startFrame = (int) Math.round(plan.entryMs * RATE / 1000d);
        // ⚠️ The guard watches the FUSION WINDOW of the head, not its beginning: the file's
        // timeline starts at 0 and the fusion starts at entryMs, so a guard at 0 would be counting
        // the clamps of today's content (the incoming's own backing) instead of the passage's.
        DjEdit.ClipGuard guard = new DjEdit.ClipGuard(startFrame, startFrame + frames);
        float[][][] padded = new float[4][][];
        for (StemGesture.Stem stem : StemGesture.Stem.ALL) {
            padded[stem.row()] = sliceFrames(bStems[stem.row()], 0,
                    frames + (int) Math.round(plan.entryMs * RATE / 1000d));
        }
        DjEdit.Limiter limiter = limiter0 ? new DjEdit.Limiter(RATE) : null;
        float[][] rendered = DjEdit.renderHead(padded, RATE, frames / (double) RATE, edit,
                new int[1], StemFusion.incomingGains(plan, edit), carried, startFrame, null, null,
                0, guard, limiter);
        float[][][] incomingVocals = new float[4][][];
        incomingVocals[StemGesture.Stem.VOCALS.row()] = sliceFrames(
                DjEdit.scaleVocals(bStems[StemGesture.Stem.VOCALS.row()], RATE, edit,
                        (plan.entryMs + plan.windowMs) / 1000d),
                0, frames);
        // The head as rendered, over the window (the step's other half), and the outgoing's own
        // master just before the junction.
        float[][] head = sliceFrames(rendered, startFrame, frames);
        int masterFrames = (int) Math.round(StemFusion.STEP_WINDOW_MS * RATE / 1000d);
        int masterFrom = (int) Math.round((plan.sourceFromMs - plan.materialFromMs
                - StemFusion.STEP_WINDOW_MS) * RATE / 1000d);
        float[][] master = new float[2][masterFrames];
        for (StemGesture.Stem stem : StemGesture.Stem.ALL) {
            float[][] row = sliceFrames(ref[stem.row()], masterFrom, masterFrames);
            for (int ch = 0; ch < master.length; ch++) {
                for (int i = 0; i < masterFrames; i++) master[ch][i] += row[ch][i];
            }
        }
        StemFusion.Material material = new StemFusion.Material(RATE, frames, speed, makeupDb,
                carried, carriedSource, incoming, incomingVocals,
                sliceFrames(ref[StemGesture.Stem.VOCALS.row()],
                        (int) Math.round((plan.sourceFromMs - plan.materialFromMs) * RATE / 1000d),
                        (int) Math.round(plan.sourceSpanMs * RATE / 1000d)),
                head, master);
        assertNotNull(rendered);
        return new Ran(StemFusion.measure(plan, edit, material, withMelody, aBeatSec, bBeatSec,
                guard), head, limiter);
    }

    private static void println(String format, Object... args) {
        System.out.println(String.format(java.util.Locale.US, format, args));
    }

    private static float[][] sliceFrames(float[][] pcm, int from, int frames) {
        float[][] out = new float[pcm.length][frames];
        for (int ch = 0; ch < pcm.length; ch++) {
            int copy = Math.max(0, Math.min(frames, pcm[ch].length - from));
            if (copy > 0) System.arraycopy(pcm[ch], from, out[ch], 0, copy);
        }
        return out;
    }

    private static float[][][] cleanOutgoing(double windowSec) {
        return stems(bursts(0.5d, windowSec, 1800d, 0.5d), bursts(0.5d, windowSec, 55d, 0.5d),
                tone(windowSec, 220d, 0.08d), tone(windowSec, 440d, 0.15d));
    }

    private static float[][][] cleanIncoming(double windowSec) {
        return stems(bursts(0.5d, windowSec, 1600d, 0.45d), bursts(0.5d, windowSec, 60d, 0.45d),
                tone(windowSec, 330d, 0.1d), tone(windowSec, 520d, 0.15d));
    }

    @Test
    public void aFusionOfTwoBackingsIsAcceptable() {
        StemFusion.Plan plan = fixture();
        StemFusion.Report report = run(plan, 1d, true, cleanOutgoing(30d), cleanIncoming(30d),
                0.5d, 0.5d, true);
        assertTrue(report.describe(), report.acceptable);
        // The levels are the table's: the carried rows at the level of the material they came
        // from, and the incoming's own drums at the floor before their swap and present after.
        assertEquals(report.sourceDrumsDb, report.carriedDrumsDb, 3d);
        assertEquals(report.sourceBassDb, report.carriedBassDb, 3d);
        assertEquals(StemFusion.A_OTHER_DB, report.carriedMelodyDb - report.sourceMelodyDb, 3d);
        // ...and the low end is measured where it matters, the last 500 ms before its own cut:
        // the clause a take that ended early would fail.
        assertEquals(report.sourceBassEndDb, report.carriedBassEndDb, 3d);
        assertTrue(report.describe(), report.incomingDrumsBeforeDb <= StemFusion.SILENT_DBFS);
        assertTrue(report.describe(), report.incomingDrumsAfterDb > StemFusion.SILENT_DBFS);
        assertTrue(report.describe(), report.incomingBassBeforeDb <= StemFusion.SILENT_DBFS);
        assertTrue(report.describe(), report.incomingBassAfterDb > StemFusion.SILENT_DBFS);
        // The user's own rule, measured: nothing sings inside the fusion window — not the
        // incoming's voice (the edit's gate holds it at zero there) and not the outgoing's (its
        // vocal row is never carried at all).
        assertTrue(report.describe(), report.incomingVocalDb <= StemFusion.SILENT_DBFS);
        // No voice came along, the pitch is untouched (speed 1), and the passage's own rhythm
        // rows carry a beat on every beat of both grids.
        assertTrue(report.describe(), report.voiceAlignmentDrums < StemFusion.VOICE_CARRY_LIMIT);
        assertTrue(report.describe(), report.voiceAlignmentBass < StemFusion.VOICE_CARRY_LIMIT);
        assertEquals(0d, report.pitchOffsetSemitones, 1e-9);
        assertTrue(report.describe(), report.beatsWithAttack >= report.beats - 1);
        assertTrue(report.describe(), report.clippedShare <= StemFusion.CLIP_SHARE_MAX);
        // The junction: the fusion's first 500 ms sits at the outgoing master's own level, which
        // is the clause the round's whole gesture is measured by.
        assertTrue(report.describe(), report.stepMeasured);
        assertTrue(report.describe(),
                Math.abs(report.junctionStepDb) <= StemFusion.JUNCTION_STEP_MAX_DB);
    }

    @Test
    public void aCarryThatIsSilentIsCaught() {
        // The outgoing's ending has no rhythm in it: the passage's own pulse stops, which is the
        // hazard the bridge's measurement exists for and the reason this one does too.
        StemFusion.Plan plan = fixture();
        float[][][] quiet = cleanOutgoing(30d);
        quiet[StemGesture.Stem.DRUMS.row()] = new float[2][quiet[0][0].length];
        quiet[StemGesture.Stem.BASS.row()] = new float[2][quiet[0][0].length];
        StemFusion.Report report = run(plan, 1d, true, quiet, cleanIncoming(30d), 0.5d, 0.5d,
                true);
        assertFalse(report.describe(), report.acceptable);
        assertTrue(report.failures, report.failures.contains("silent")
                || report.failures.contains("hole"));
    }

    @Test
    public void aCarryThatIsTheOutgoingVoiceIsCaught() {
        StemFusion.Plan plan = fixture();
        float[][][] voiceCarried = cleanOutgoing(30d);
        // What was carried is the outgoing's voice (a loud tone that IS the vocal stem).
        voiceCarried[StemGesture.Stem.DRUMS.row()] = tone(30d, 440d, 0.4d);
        voiceCarried[StemGesture.Stem.VOCALS.row()] = tone(30d, 440d, 0.4d);
        StemFusion.Report report = run(plan, 1d, true, voiceCarried, cleanIncoming(30d), 0.5d,
                0.5d, true);
        assertFalse(report.describe(), report.acceptable);
        assertTrue(report.failures, report.failures.contains("voice"));
    }

    /**
     * ⚠️ The clause the round-18 prototype's junction failed on real material, and the reason
     * {@link DjEdit.Limiter} exists: the outgoing's own master already peaks at about full scale,
     * so a bed under it is over — and a static divisor over the whole head (what the prototype
     * did, measured at −4.55 dB of the head, −6.80 dB of step) pulls the music down with the
     * peaks, which the listener heard as a 卡顿.
     *
     * <p>Synthetic full-scale outgoing tail + bed, measured three ways on the same material:
     * <ul>
     *   <li>limited (what the render does): the level the listener was already at, held, with the
     *       clamp left as a last resort — the step is inside the clause;</li>
     *   <li>clamped at the bus (no limiter at all): the peaks are cut off, so the step stays small
     *       too, but tens of thousands of samples are at full scale, which is the distortion the
     *       limiter is there to avoid;</li>
     *   <li>divided by the head's own peak (the prototype's static guard): the step the user
     *       heard, and worse than the limiter's by the divisor's full amount.</li>
     * </ul>
     */
    @Test
    public void theLimiterHoldsAFullScaleOutgoingTailUnderABed() {
        StemFusion.Plan plan = fixture();
        // A's own rows: a loud body (a sustained low tone) and a kick line that reach full scale
        // together — the master is normalised to 1.0 below, which is what "already at full scale"
        // means — over a pad and a line of voice that are quiet (the junction the spec prefers, a
        // voice between phrases).
        float[][][] a = normalise(stems(tone(30d, 60d, 0.75d), bursts(0.5d, 30d, 55d, 0.6d),
                tone(30d, 220d, 0.1d), tone(30d, 440d, 0.05d)), 1.0d);
        // B's bed, at the level the table arrives at (unity) and loud enough that the two backings
        // together are well over full scale — the case the round is about.
        float[][][] b = stems(bursts(0.5d, 30d, 1600d, 0.4d), bursts(0.5d, 30d, 60d, 0.4d),
                tone(30d, 330d, 0.6d), tone(30d, 520d, 0.05d));

        Ran limited = render(plan, 1d, true, a, b, 0.5d, 0.5d, true);
        Ran clamped = render(plan, 1d, true, a, b, 0.5d, 0.5d, false);
        println("%s", limited.report.describe());
        println("%s", clamped.report.describe());
        println("%s", limited.limiter.describe());
        assertTrue(limited.report.describe(), limited.report.stepMeasured);
        assertTrue("the limited step must be inside the clause: " + limited.report.junctionStepDb,
                Math.abs(limited.report.junctionStepDb) <= StemFusion.JUNCTION_STEP_MAX_DB);
        // The limiter is what keeps the passage off full scale: without it the same material
        // clamps a great many samples, with it only the attack's own 1 ms is left as a last resort.
        assertTrue("clamping must be the last resort: " + limited.report.clippedPairs
                        + " clipped pairs with the limiter, " + clamped.report.clippedPairs
                        + " without",
                clamped.report.clippedPairs > 10 * Math.max(1, limited.report.clippedPairs));
        assertTrue(clamped.report.describe(), clamped.report.clippedPairs > 1000);
        assertTrue(limited.report.describe(),
                limited.report.clippedShare <= StemFusion.CLIP_SHARE_MAX);
        assertTrue(limited.report.describe(),
                limited.report.longestClipRunMs <= StemFusion.CLIP_RUN_MAX_MS);
        assertTrue("the limiter held something: " + limited.limiter.describe(),
                limited.limiter.heldFrames > 0);
        // The counterfactual the design was chosen against: the first prototype's static guard —
        // the whole head divided by its own peak. The clamp has already cut that peak to 1.0 in
        // the render without a limiter, so what is visible is the reduction the limiter's own
        // deepest hold implies: the peak it was holding was AT LEAST that much over full scale,
        // and a divisor spends that amount on every frame of the window where the limiter takes it
        // only while the peak is there. (The prototype's own pair measured −4.55 dB of head,
        // −6.80 dB of step, which is the 卡顿 the clause exists to refuse.)
        double divisorCostAtLeast = 20d * Math.log10(1d / limited.limiter.deepestGain);
        println("a static divisor would cost at least %.2f dB on every frame; the limiter's deepest"
                        + " hold was %.2f dB on the frames that needed it",
                divisorCostAtLeast, limited.limiter.deepestReductionDb());
        assertTrue("the synthetic passage is well over full scale, so the divisor's cost is real: "
                        + divisorCostAtLeast + " dB", divisorCostAtLeast > 2d);
        assertTrue("a divisor's cost is the limiter's deepest hold or more: " + divisorCostAtLeast
                        + " against " + limited.limiter.deepestReductionDb(),
                divisorCostAtLeast >= limited.limiter.deepestReductionDb() - 1e-9);
        assertTrue("...and the limiter does not hold that on every frame: "
                        + limited.limiter.heldFrames + " of " + limited.limiter.frames,
                limited.limiter.heldFrames < limited.limiter.frames);
    }

    /**
     * ⚠️ Round 3's make-up gain, on the case that produced it: a passage where what the fusion
     * carries is quiet relative to the outgoing track's own last half second — because the outgoing
     * track's <em>voice</em> was the energy there, and the fusion removes the voice. The junction
     * must not be heard as a drop: the renderer measures the step and lifts the outgoing's carried
     * rows by it, bounded by {@link StemFusion#MAKEUP_MAX_DB}.
     *
     * <p>What is asserted is the shape of the mechanism, not just that the number improves: the
     * make-up is exactly the step that was measured (clamped), it touches the outgoing's rows and
     * nothing else — the stretch of the head where the outgoing's material is already gone is
     * bit-identical between the two renders — and the clause is re-checked on the result.
     */
    @Test
    public void aQuietCarryIsLiftedByTheStepItMeasured() {
        StemFusion.Plan plan = fixture();
        // A's own rows: a lead voice over a backing that is real but quieter — roughly a 2:1
        // power ratio between what the fusion must throw away (the voice, and most of the melodic
        // row) and what it can carry. That is the passage where the make-up is needed: the fusion
        // arrives a handful of dB under the level the listener was on, which is the 卡顿 the round
        // is about.
        float[][][] a = stems(bursts(0.5d, 30d, 1800d, 0.35d), bursts(0.5d, 30d, 55d, 0.35d),
                tone(30d, 220d, 0.1d), tone(30d, 440d, 0.2d));
        Ran[] passes = renderWithMakeup(plan, 1d, true, a, cleanIncoming(30d), 0.5d, 0.5d, true);
        Ran before = passes[0];
        Ran after = passes[1];
        println("no make-up: %s", before.report.describe());
        println("with make-up: %s", after.report.describe());
        assertTrue(before.report.describe(), before.report.stepMeasured);
        assertTrue("the un-lifted junction is a drop: " + before.report.junctionStepDb,
                before.report.junctionStepDb < -StemFusion.JUNCTION_STEP_MAX_DB);
        double applied = StemFusion.makeupDb(before.report.junctionStepDb);
        assertTrue("the make-up is the measured reduction: " + applied,
                applied > 0d && applied <= StemFusion.MAKEUP_MAX_DB);
        assertEquals("the make-up the render applied", applied, after.report.makeupDb, 1e-9);
        // The lifted junction is closer to the listener's own level — and inside the clause.
        assertTrue("the step after the make-up: " + after.report.junctionStepDb,
                after.report.junctionStepDb > before.report.junctionStepDb);
        assertTrue(after.report.describe(),
                Math.abs(after.report.junctionStepDb) <= StemFusion.JUNCTION_STEP_MAX_DB);
        assertTrue("lifted " + applied + " dB, so most of the loss is recovered: "
                        + before.report.junctionStepDb + " -> " + after.report.junctionStepDb,
                after.report.junctionStepDb >= before.report.junctionStepDb / 2d);
        // The outgoing's rows really are louder in the file, and the incoming's are untouched: the
        // carried drums, which are the loudest carried row while they are on, grew by the make-up.
        double growth = after.report.carriedDrumsDb - before.report.carriedDrumsDb;
        assertEquals("the carried rows were lifted by the make-up", applied, growth, 0.05d);
        // B's rows and everything after the outgoing's material: identical, sample for sample.
        // Measured with the limiter out of the chain, because a limiter carries state: the two
        // renders have different peak histories, so its gain is (correctly) a hair different in
        // the made-up one even where the material is the same. What is being asserted is the
        // material: the make-up scales the outgoing's carried rows and nothing else.
        Ran[] bare = renderWithMakeup(plan, 1d, true, a, cleanIncoming(30d), 0.5d, 0.5d, false);
        int last = after.head[0].length;
        int from = (int) Math.round((plan.bassMs + StemFusion.CUT_MS - plan.entryMs) * RATE / 1000d);
        for (int ch = 0; ch < bare[1].head.length; ch++) {
            for (int i = from; i < last; i++) {
                assertEquals("the make-up must not touch B's rows (sample " + i + ")",
                        bare[0].head[ch][i], bare[1].head[ch][i], 0.0f);
            }
        }
        // And the shape of the make-up itself: a uniform gain on the rows it is given.
        float[][][] carried = new float[StemGesture.Stem.ALL.length][][];
        carried[StemGesture.Stem.DRUMS.row()] = tone(1.0, 100d, 0.5d);
        float[][][] liftedRows = StemFusion.applyMakeup(carried, applied);
        double factor = Math.pow(10d, applied / 20d);
        for (int ch = 0; ch < 2; ch++) {
            for (int i = 0; i < carried[0][ch].length; i += 97) {
                assertEquals("applyMakeup is a uniform gain",
                        factor * carried[0][ch][i], liftedRows[0][ch][i], 1e-6f);
            }
        }
        assertNull("a row that was not carried stays absent",
                liftedRows[StemGesture.Stem.VOCALS.row()]);
    }

    /**
     * A passage that needs more than {@link StemFusion#MAKEUP_MAX_DB} is refused: the clamp is
     * there so a junction that is nothing like the outgoing track cannot be amplified into one,
     * and the acceptance clause is what says so.
     */
    @Test
    public void aPassageThatNeedsMoreThanTheMakeUpIsRefused() {
        StemFusion.Plan plan = fixture();
        // 15 dB down or worse: the clamp (8 dB) cannot reach the outgoing's level, and the step
        // clause is what stops the fusion being written as a drop.
        float[][][] a = stems(bursts(0.5d, 30d, 1800d, 0.04d), bursts(0.5d, 30d, 55d, 0.04d),
                tone(30d, 220d, 0.03d), tone(30d, 440d, 0.6d));
        Ran[] passes = renderWithMakeup(plan, 1d, true, a, cleanIncoming(30d), 0.5d, 0.5d, true);
        println("%s", passes[1].report.describe());
        assertEquals(StemFusion.MAKEUP_MAX_DB, passes[1].report.makeupDb, 1e-9);
        assertFalse(passes[1].report.describe(), passes[1].report.acceptable);
        assertTrue(passes[1].report.failures, passes[1].report.failures.contains("sits")
                && passes[1].report.failures.contains("the most is "
                        + StemFusion.JUNCTION_STEP_MAX_DB));
    }

    /** The same stems scaled so that the sum of their rows — the master — peaks at {@code peak}. */
    private static float[][][] normalise(float[][][] aStems, double peak) {
        double worst = 0d;
        int frames = Integer.MAX_VALUE;
        for (StemGesture.Stem stem : StemGesture.Stem.ALL) {
            frames = Math.min(frames, aStems[stem.row()][0].length);
        }
        frames = Math.min(frames, (int) Math.round(8d * RATE));
        for (int i = 0; i < frames; i++) {
            double sum = 0;
            for (StemGesture.Stem stem : StemGesture.Stem.ALL) sum += aStems[stem.row()][0][i];
            worst = Math.max(worst, Math.abs(sum));
        }
        double scale = worst > 0d ? peak / worst : 1d;
        float[][][] out = new float[aStems.length][][];
        for (int row = 0; row < aStems.length; row++) {
            if (aStems[row] == null) continue;
            out[row] = new float[aStems[row].length][];
            for (int ch = 0; ch < aStems[row].length; ch++) {
                out[row][ch] = new float[aStems[row][ch].length];
                for (int i = 0; i < aStems[row][ch].length; i++) {
                    out[row][ch][i] = (float) (scale * aStems[row][ch][i]);
                }
            }
        }
        return out;
    }

    /**
     * ⚠️ The material window's own clause: the take has to be there up to {@code bassMs}. A take
     * one bar short (round 18's {@code 3*aBar} on a pair whose bars are not equal) leaves the last
     * stretch of the carried low end silent while the table still has it on — a hard cut the
     * gesture never names — and the measurement has to say so rather than average it away.
     */
    @Test
    public void aCarryThatEndsBeforeItsOwnLastCutIsCaught() {
        // A slow outgoing track: a 1 s beat, a 4 s bar, and the fusion's window is three bars of
        // B's grid = 6 s. The material the gesture needs is (2*2000 + 80) = 4080 ms of A's file,
        // so the report is measured against a source window of that length — but what is carried
        // is only the first 3000 ms of it, as a short separation would give.
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 1000d, 0d, 500d, 0d, 1d, bars(4000d, 0d, 60), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertFalse(plan.describe(), plan.valid);
        // The same pair, locked (both grids 1 s beats) is a real fusion: the bass must be in the
        // file right up to the 2 s bar line, and it is.
        StemFusion.Plan locked = StemFusion.plan(new StemFusion.Input(400_000L, 20_000L, 35_000L,
                15_000L, 1000d, 0d, 1000d, 0d, 1d, bars(4000d, 0d, 100), bars(4000d, 0d, 100),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(locked.describe(), locked.valid);
        assertEquals(8_080L, locked.sourceSpanMs);
        // The low end is cut at bassMs = entry + 8 s, and the material it is cut from is the
        // 8 s of A's own file from the junction — 8080 ms including the splice's own tail.
        assertEquals(8_000L, locked.bassMs - locked.entryMs);

        float[][][] a = cleanOutgoing(30d);
        Ran full = render(locked, 1d, true, a, cleanIncoming(30d), 1d, 1d, true);
        println("%s", full.report.describe());
        assertTrue(full.report.describe(), full.report.stepMeasured);
        assertTrue(full.report.describe(), full.report.acceptable);
        // Now the same render with the take cut short: the last bar of the carried low end is
        // gone while the table still has it on, and the outgoing's own file still has the material
        // the take was supposed to come from.
        float[][][] short_ = truncateRows(a, locked, 0.5d);
        Ran clipped = render(locked, 1d, true, short_, cleanIncoming(30d), 1d, 1d, true, a, 0d);
        println("%s", clipped.report.describe());
        assertFalse(clipped.report.describe(), clipped.report.acceptable);
        assertTrue(clipped.report.failures,
                clipped.report.failures.contains("up to its own cut")
                        || clipped.report.failures.contains("low end is not there"));
        // The clause is measurable rather than vacuous: with the full take it is satisfied.
        assertTrue(full.report.describe(),
                full.report.carriedBassEndDb > full.report.sourceBassEndDb
                        - StemFusion.CARRY_TOLERANCE_DB);
    }

    /** The same stems with every row's material past {@code fraction} of the take's own span
     *  replaced by silence — a separation that stopped early. */
    private static float[][][] truncateRows(float[][][] aStems, StemFusion.Plan plan,
                                            double fraction) {
        float[][][] out = new float[aStems.length][][];
        for (int row = 0; row < aStems.length; row++) {
            if (aStems[row] == null) continue;
            out[row] = new float[aStems[row].length][];
            for (int ch = 0; ch < aStems[row].length; ch++) {
                out[row][ch] = aStems[row][ch].clone();
                int from = (int) Math.round((plan.sourceFromMs - plan.materialFromMs
                        + fraction * plan.sourceSpanMs) * RATE / 1000d);
                for (int i = Math.max(0, from); i < out[row][ch].length; i++) out[row][ch][i] = 0f;
            }
        }
        return out;
    }

    /**
     * A passage mixed too loud is refused — and the clause that catches it is the junction's step,
     * which is the honest answer now that the head carries a limiter: the limiter holds the peaks
     * (so the "no long clipping" clauses are the last resort, not the mechanism), and what is heard
     * instead is the level being pulled well below the one the listener was at. The clamp clauses
     * stay as the backstop they always were.
     */
    @Test
    public void aPassageMixedTooLoudIsRefused() {
        StemFusion.Plan plan = fixture();
        float[][][] loud = stems(bursts(0.5d, 30d, 1800d, 0.95d), bursts(0.5d, 30d, 55d, 0.95d),
                tone(30d, 220d, 0.5d), tone(30d, 440d, 0.1d));
        float[][][] loudIn = stems(bursts(0.5d, 30d, 1600d, 0.95d), bursts(0.5d, 30d, 60d, 0.95d),
                tone(30d, 330d, 0.5d), tone(30d, 520d, 0.1d));
        // No limiter in this one: the hand-over's own raw sum, which is over full scale.
        StemFusion.Report report = run(plan, 1d, true, loud, loudIn, 0.5d, 0.5d, false);
        println("%s", report.describe());
        assertFalse(report.describe(), report.acceptable);
        assertTrue(report.failures, report.failures.contains("sits")
                || report.failures.contains("clamp") || report.failures.contains("clipped"));
        assertTrue(report.clippedPairs > 0);
        // The step clause is what a listener would hear, so it is the one that HAS to be there.
        assertTrue(report.describe(), report.stepMeasured
                && Math.abs(report.junctionStepDb) > StemFusion.JUNCTION_STEP_MAX_DB);
    }

    @Test
    public void aFusionWithNoGridAnywhereCarriesNothing() {
        StemFusion.Plan plan = fixture();
        assertNull(StemFusion.carry(cleanOutgoing(30d), RATE, plan.materialFromMs,
                StemFusion.plan(input(240_000L, 0L, 35_000L, 500d, 500d, bars(2000d, 0d, 120),
                        bars(2000d, 0d, 120))), 1d, true)[0]);
    }

    @Test
    public void theOutgoingGridIsFoundFromTheLowEndOfItsOwnAudio() {
        // The downbeat estimate, on the mix's own low band: a kick on every beat at 120 BPM with
        // the bar's first one louder, on a beat grid whose phase is 125 ms into the file. The
        // beat phase alone can never say WHERE the bar starts (it is one of four answers and the
        // spec's own note says the wrong three are the ordinary case), which is why this is
        // measured from the low end.
        double beatSec = 0.5d;
        double windowSec = 12d;
        long fromMs = 200_000L;
        double beatPhaseMs = 125d;
        int frames = (int) Math.round(windowSec * RATE);
        int period = (int) Math.round(beatSec * RATE);
        int burst = (int) Math.round(0.15d * RATE);
        float[][] mix = new float[2][frames];
        int first = (int) Math.round((beatPhaseMs - fromMs) / 1000d * RATE);
        int beats = 0;
        for (int beat = 0; ; beat++) {
            int at = first + beat * period;
            if (at >= frames) break;
            if (at + burst <= 0) continue;
            beats++;
            double amp = beat % 4 == 0 ? 0.9d : 0.35d;
            for (int i = 0; i < burst; i++) {
                if (at + i < 0 || at + i >= frames) continue;
                double decay = Math.exp(-i / (0.05d * RATE));
                float v = (float) (amp * decay * Math.sin(2 * Math.PI * 80d * i / RATE));
                mix[0][at + i] += v;
                mix[1][at + i] += v;
            }
        }
        assertTrue(beats > 20);
        double[] grid = StemFusion.barLinesOfLowBand(mix, RATE, fromMs, beatSec * 1000d,
                beatPhaseMs);
        assertTrue("the low end gave a grid at all", grid.length > 1);
        // Every line is a bar line of the grid the kick plays: the first beat of every four, at
        // the beat phase, i.e. fromMs + 125 + k*2000.
        for (double at : grid) {
            double into = ((at - fromMs - beatPhaseMs) % 2000d + 2000d) % 2000d;
            assertTrue("a line off the outgoing grid: " + at, into < 25d || into > 1975d);
        }
        // And a grid with no low end at all gives nothing rather than a guess.
        assertEquals(0, StemFusion.barLinesOfLowBand(new float[2][frames], RATE, fromMs,
                beatSec * 1000d, beatPhaseMs).length);
    }
}
