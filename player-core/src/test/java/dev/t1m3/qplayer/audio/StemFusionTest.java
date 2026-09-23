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
        // ⚠️ Round 6's eleventh pass: the junction is the outgoing's OWN FILE END less the length of
        // the deck-level hand-over — 240 000 - 2 000 = 238 000 — whatever the grids say. The search
        // no longer chooses it and nothing shifts it (`junctionShiftMs` is zero by construction).
        assertEquals(240_000L - StemFusion.JUNCTION_XFADE_MS, plan.junctionMs);
        assertEquals(0L, plan.junctionShiftMs);
        assertEquals(0L, plan.searchBandMs);
        assertEquals(0L, plan.searchBackMs);
        // The content start is removal - blend = 15000; the first B bar line at or after it is
        // 16000, and (the spec's own rule) the deck starts there.
        assertEquals(15_000L, 15_000L);
        assertEquals(16_000L, plan.entryMs);
        // ⚠️ The A-side instants are ALL A's own file end (round 6's eleventh pass): the hold and
        // both fades measure zero, because what replaces the recede is the file's copy of A's last
        // JUNCTION_XFADE_MS and the deck-level hand-over that crossfades it against A's live deck.
        // The table is the same table with the spans at zero, so all four instants coincide.
        assertEquals(18_000L, plan.holdEndMs);
        assertEquals(18_000L, plan.swapMs);
        assertEquals(18_000L, plan.bassMs);
        assertEquals(18_000L, plan.drumsEndMs);
        assertEquals(18_000L, plan.lowEndEndMs);
        // ...and B's drums and low end arrive over one step ENDING there (decision (1): the pulse is
        // continuous across the hand-over; with the old `entry + two steps` the file had a 2 360 ms
        // window with no rhythm in either backing, which the acceptance's pulse clause refuses).
        assertEquals(16_000L, plan.arriveStartMs);
        assertEquals(18_000L, plan.arriveEndMs);
        // The window is the gesture's own span (4 steps of the incoming's grid = FUSION_BARS): the
        // arrival's reach is 2 000 ms and the bed's bar is 2 000, so the gesture is the widest.
        assertEquals(8_000L, plan.windowMs);
        assertEquals(24_000L, plan.fusionEndMs);
        // ⚠️ The take is A's own ENDING, not a passage cut out of A: it starts exactly at the
        // junction — A's last JUNCTION_XFADE_MS — and runs to aDur, so its span is the copy's own
        // length. (Not `junction - entry/speed`: that lead would put the file's entry instant
        // entry/speed BEFORE the junction, so the hand-over would crossfade A's live deck against a
        // copy of A from a second earlier instead of against itself.)
        assertEquals(StemFusion.JUNCTION_XFADE_MS, plan.sourceSpanMs);
        assertEquals(2_000L, plan.sourceSpanMs);
        assertEquals(plan.junctionMs, plan.sourceFromMs);
        assertEquals(240_000L, plan.sourceFromMs + plan.sourceSpanMs);
        assertEquals(plan.sourceFromMs - StemFusion.A_TAIL_SLACK_MS, plan.materialFromMs);
        assertEquals("the separation window is the copy plus the two leads",
                plan.sourceSpanMs + 2L * StemFusion.A_TAIL_SLACK_MS, plan.materialWindowMs);
        // ⚠️ The cost bound is untouched and is the same arithmetic the pre-decode gate uses (the
        // GESTURE's shape, not the take): `sourceSpanMs(500, 1)` is still four steps plus the tail.
        assertEquals(StemFusion.sourceSpanMs(500d, 1d), 8_080L);
        assertTrue("the fusion stays inside the vocal-free window", plan.fusionEndMs <= 35_000L);
        assertTrue(plan.phaseMatched);
        assertEquals(0d, plan.phaseErrorMs, 1e-9);
        assertEquals(0d, plan.lockError, 1e-9);
        assertEquals("a fusion splices nothing any more", 0, plan.splices());
        assertEquals("[the hold, the drums' fade, the low end's fade]", 3, plan.recedeMs().length);
        assertEquals("the hold is the copy's own length and the two fades are zero",
                StemFusion.JUNCTION_XFADE_MS, plan.recedeMs()[0]);
        assertEquals(0L, plan.recedeMs()[1]);
        assertEquals(0L, plan.recedeMs()[2]);
        // No measurement was handed in, so neither preference moved the junction.
        assertFalse(plan.grooveAtJunction);
        assertFalse(plan.quietAtJunction);
        // ⚠️ And the quiet-passage clause's report is TRUE by construction now (round 6's eleventh
        // pass): the refusal is suppressed for the placed junction, so a plan that exists always
        // says the junction is at the body level — whether or not a body was measured at all. The
        // measurement it would have used is still read, at the junction, for the report.
        assertTrue(plan.bodyLevelAtJunction);
        // The incoming's bed arrives over one bar of its own grid, which on this fixture is the same
        // instant A's rows end on.
        assertEquals(18_000L, plan.bedFadeMs);
        assertEquals(2_000L, plan.bedFadeMs - plan.entryMs);
    }

    @Test
    public void thePlacementIgnoresTheJunctionPreferences() {
        // ⚠️ Rewritten from `aJunctionWhereTheOutgoingIsNotSingingIsPreferred` (round 6's eleventh
        // pass): the junction is A's own file end and there is no search, so neither of round 3's
        // preferences — the voice between phrases, the groove playing — has anything to choose.
        // The measurement is still read (it is in the report at the junction), and a pair whose
        // ending is a fade is exactly the pair the user asked to hear in full.
        double[] aBars = {219_000d, 220_500d};
        StemFusion.Input in = new StemFusion.Input(240_000L, 20_000L, 35_000L, 15_000L,
                500d, 0d, 500d, 0d, 1d, aBars, bars(2000d, 0d, 120),
                (atMs, beatMs) -> atMs == 220_500L);
        StemFusion.Plan quiet = StemFusion.plan(in);
        assertTrue(quiet.reason, quiet.valid);
        assertEquals("the junction is aDur less the hand-over, not a line of A's grid",
                238_000L, quiet.junctionMs);
        assertEquals(0L, quiet.junctionShiftMs);
        assertFalse("and the preference that would have moved it is not consulted",
                quiet.quietAtJunction);

        // No measurement at all: the same junction, because the measurement was never the question.
        StemFusion.Plan plain = StemFusion.plan(input(240_000L, 20_000L, 35_000L, 500d, 500d,
                aBars, bars(2000d, 0d, 120)));
        assertTrue(plain.reason, plain.valid);
        assertEquals(238_000L, plain.junctionMs);
        assertFalse(plain.quietAtJunction);

        // And a line outside the old search band is not a clause either: the search's own refusals
        // ("no line of the outgoing's grid is inside …") are not this pair's business any more. The
        // 224 000 line was past the band then; the plan now ignores the grid's lines entirely.
        StemFusion.Plan tooFar = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 500d, 0d, 1d, new double[]{219_000d, 224_000d},
                bars(2000d, 0d, 120), (atMs, beatMs) -> atMs == 224_000L));
        assertTrue(tooFar.reason, tooFar.valid);
        assertEquals(238_000L, tooFar.junctionMs);
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
    public void thePlacementIgnoresTheGroovePreference() {
        // ⚠️ Rewritten from `aJunctionWhereTheOutgoingGrooveIsPlayingWins` (round 6's eleventh
        // pass). Round 3's groove preference — carry the outgoing's rhythm out of the hand-over
        // rather than its pad — chose between lines of A's grid; there is no choice now, so the
        // groove measurement is read at the junction for the report and moves nothing. All three
        // fixtures below (a groove on one line, a groove everywhere, no groove at all) are the
        // same plan's junction.
        double[] aBars = {218_000d, 220_000d};
        double beat = 200d;
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, beat, 0d, beat, 0d, 1d, aBars, bars(800d, 0d, 300),
                (atMs, beatMs) -> atMs == 220_000L, (atMs, beatMs) -> atMs == 218_000d));
        assertTrue(plan.reason, plan.valid);
        assertEquals(238_000L, plan.junctionMs);
        assertEquals(0L, plan.junctionShiftMs);
        assertFalse("the groove cannot move the junction any more", plan.grooveAtJunction);
        assertFalse(plan.quietAtJunction);

        StemFusion.Plan both = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, beat, 0d, beat, 0d, 1d, aBars, bars(800d, 0d, 300),
                StemFusion.NO_VOICE_MEASUREMENT, (atMs, beatMs) -> true));
        assertTrue(both.reason, both.valid);
        assertEquals(238_000L, both.junctionMs);

        StemFusion.Plan none = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, beat, 0d, beat, 0d, 1d, aBars, bars(800d, 0d, 300),
                (atMs, beatMs) -> atMs == 220_000L));
        assertTrue(none.reason, none.valid);
        assertEquals(238_000L, none.junctionMs);
        assertFalse(none.quietAtJunction);
        assertFalse(none.grooveAtJunction);
    }

    @Test
    public void theJunctionTheDeckIsCutOnIsAlwaysALineOfTheOutgoingGrid() {
        // ⚠️ Rewritten from the search's own invariant (round 6's eleventh pass): the junction used
        // to be a line of the outgoing's grid because the search chose among those lines. It is A's
        // own file end now, and the invariant that matters is the one the deck obeys: the cut is
        // JUNCTION_XFADE_MS before the last sample of A's file, whatever the grid's phase.
        double[] aBars = bars(2000d, 125d, 120);
        StemFusion.Plan plan = StemFusion.plan(input(240_000L, 20_000L, 35_000L, 500d, 500d,
                aBars, bars(2000d, 0d, 120)));
        assertTrue(plan.reason, plan.valid);
        assertEquals(240_000L - StemFusion.JUNCTION_XFADE_MS, plan.junctionMs);
        // The take still ends on A's last sample, which is what "in full" means: the material's
        // source span reaches aDur exactly, and the window the render separates covers it with
        // A_TAIL_SLACK_MS of lead on either side.
        assertEquals(240_000L, plan.sourceFromMs + StemFusion.JUNCTION_XFADE_MS);
        assertEquals(plan.sourceSpanMs + 2L * StemFusion.A_TAIL_SLACK_MS, plan.materialWindowMs);
        assertTrue("and the separated window reaches A's last sample",
                plan.materialFromMs + plan.materialWindowMs
                        >= plan.sourceFromMs + plan.sourceSpanMs);
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
        // ⚠️ Round 6: the incoming's measured LINES are not a clause any more — only its beat grid
        // is. A null (or empty) array is a head decode that stopped early or a downbeat estimate that
        // found nothing, and the entry falls back to lines built from the beat grid itself (see
        // StemFusionEntryWindowTest for the device pair that made this a bug rather than a
        // nicety). What still refuses, and refuses by name, is a track with no grid at all.
        StemFusion.Plan headless = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), null,
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(headless.describe(), headless.valid);
        assertTrue("and the plan says its entry came from the beat grid", headless.entryFromBeatGrid);
        assertTrue(StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L, 15_000L,
                500d, 0d, 0d, 0d, 1d, bars(2000d, 0d, 120), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("incoming track"));
        assertTrue(StemFusion.plan(new StemFusion.Input(240_000L, 0L, 35_000L, 15_000L,
                500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("blend length"));
        // ⚠️ Round 5: a 1000 ms against a 500 ms beat is a 2:1 RELATION now, so nothing refuses it
        // — the deck-level lock is 100% out (asserted below, it is not the gate any more) but the
        // two BAR grids coincide: one bar of the outgoing's grid is two of the incoming's, so every
        // swap still lands on a line of both, which is all the gesture needs.
        StemFusion.Plan twoToOne = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 1000d, 0d, 500d, 0d, 1d, bars(4000d, 0d, 60), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(twoToOne.describe(), twoToOne.valid);
        assertEquals(2, twoToOne.relation.p);
        assertEquals(1, twoToOne.relation.q);
        assertEquals("one step is two bars of the incoming's grid", 4_000d, twoToOne.barStepMs, 1e-6);
        assertEquals("the deck itself is not locked", 1d, StemFusion.lockError(1000d, 500d, 1d),
                1e-9);
        // ...and it is checked before anything is decoded, where it also passes.
        assertNull(StemFusion.refusal(240_000L, 20_000L, 35_000L, 15_000L, 1000d, 500d, 1d));
        assertTrue(StemFusion.possible(240_000L, 20_000L, 35_000L, 15_000L, 1000d, 500d, 1d));
        // A pair in no relation at all (1.2 is 20% from 1 and from 3:2) still refuses nothing: it
        // is the SLAM.
        assertNull(StemFusion.refusal(240_000L, 20_000L, 35_000L, 15_000L, 600d, 500d, 1d));
        // The cost bound, on a pair that DOES lock: two 1500 ms beats are bars of 6 s, so the
        // passage is 12 s of the incoming's file and the material window 14 s of A's.
        assertTrue(StemFusion.plan(new StemFusion.Input(400_000L, 20_000L, 80_000L, 15_000L,
                1500d, 0d, 1500d, 0d, 1d, bars(6000d, 0d, 60), bars(6000d, 0d, 60),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("one render may separate"));
        assertTrue(StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L, -1L,
                500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT)).reason.contains("own start is not known"));
        // ⚠️ Retired in round 6's eleventh pass: the "the outgoing's file ends" case. It existed
        // because a junction chosen by distance could sit near enough to A's end that the passage
        // ran off it; the junction IS A's end now, the take runs to it by construction, and the only
        // way this fixture could still fail is through the vocal window below — so a case that
        // cannot fail on its own clause is not a case. The clause the fixture was really about is
        // asserted, on the plan's own numbers, in theJunctionTheDeckIsCutOnIsAlwaysALineOfTheOutgoingGrid.
        assertTrue("a short file is only refused for a reason it can still have",
                StemFusion.plan(input(100_000L, 4_000L, 20_000L, 500d, 500d,
                        bars(2000d, 0d, 60), bars(2000d, 0d, 60))).reason
                        .contains("the incoming's vocals are out for"));
        // And the whole fusion has to stay inside the window the incoming's vocals are out for.
        // ⚠️ The blend has to be long enough for the carried rows to exist in A's file at all
        // (round 6's gesture takes 8080 ms of it from the junction on, i.e. a blend of about
        // 8.8 s at a 500 ms beat — the measured real pairs' blends are 15 s), so a fixture that is
        // meant to fail on the VOCAL window has to clear that clause: 9 s of blend does, and 9.5 s
        // of removal leaves the fusion's 8 s from a 2 s entry 10 s, half a second too late.
        StemFusion.Plan late = StemFusion.plan(input(200_000L, 9_000L, 9_500L, 500d, 500d,
                bars(2000d, 0d, 120), bars(2000d, 0d, 120)));
        assertFalse(late.reason, late.valid);
        assertTrue(late.reason, late.reason.contains("past the 9500ms"));
        assertTrue(StemFusion.possible(240_000L, 20_000L, 35_000L, 15_000L, 500d, 500d, 1d));
        assertNull(StemFusion.refusal(240_000L, 20_000L, 35_000L, 15_000L, 500d, 500d, 1d));
    }

    /** A pair in no relation at all: 600 ms against 500 ms is 20% from 1 and 40% from 3:2, so
     *  {@link StemFusion#relationOf} answers null and the plan is a SLAM. */
    private static StemFusion.Plan slamFixture() {
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 600d, 0d, 500d, 0d, 1d, bars(2400d, 0d, 120), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(plan.reason, plan.valid);
        assertTrue(plan.describe(), plan.slam);
        return plan;
    }

    /**
     * ⚠️ <b>A slam's own acceptance, end to end.</b> Nothing in this file ever measured a slam before
     * round 6's tenth pass, which is why two arithmetic defects shipped: the arrival instants came
     * from the FUSION shape's two-step hold while a slam's window is one step plus its splice, so the
     * "after their swap" span began past the window's end, held no samples, and answered −240 dBFS —
     * and every slam on the device was refused with "the incoming's own drums never arrive
     * (-240.0 dBFS after their swap)" while the same slice, read at the slam's own instants, holds the
     * incoming's real kit at −10.3 dBFS. This test fails exactly that way without the fix.
     */
    @Test
    public void aSlamPassesItsOwnAcceptance() {
        StemFusion.Plan plan = slamFixture();
        StemFusion.Report report = run(plan, 1d, true, cleanOutgoing(30d), cleanIncoming(30d),
                0.5d, 0.5d, true);
        println("%s", report.describe());
        assertTrue(report.describe(), report.acceptable);
        assertFalse("the refusal the device saw: " + report.failures,
                report.failures.contains("never arrive"));
        // The arrival span IS empty on a slam — the incoming's rows arrive on the line that ends the
        // file's window, and the body's own audio continues there — so the measurement reports
        // "unknown" rather than the floor, and the clause declines instead of refusing.
        assertTrue("an empty span is not digital silence: " + report.incomingDrumsAfterDb,
                Double.isNaN(report.incomingDrumsAfterDb));
        assertTrue("nor the low end's: " + report.incomingBassAfterDb,
                Double.isNaN(report.incomingBassAfterDb));
        // The rows it CAN measure are the carried ones, and they are the table's.
        assertEquals(report.sourceDrumsDb, report.carriedDrumsDb, 3d);
        assertEquals(report.sourceBassDb, report.carriedBassDb, 3d);
        assertEquals(StemFusion.A_OTHER_DB, report.carriedMelodyDb - report.sourceMelodyDb, 3d);
        // The pulse clause is skipped for a slam by design (its window is one bar and its gesture is a
        // cut), which is why the report says 0 of 0 beats rather than 0 of N.
        assertEquals(0, report.beats);
        assertTrue(report.describe(), report.stepMeasured);
        assertTrue(report.describe(),
                Math.abs(report.junctionStepDb) <= StemFusion.JUNCTION_STEP_MAX_DB);
    }

    /**
     * ⚠️ And the slam's splice RUNS. Its rows fall from unity to the floor over {@link
     * StemFusion#CUT_MS} from the line — an equal-power hand-over, the arrival rising as the
     * departure falls — which needs the window to contain it: `fusionEndMs` is the END of the splice,
     * not the line, so `gainAt`'s own guard lets the splice happen. Before the fix the window ended ON
     * the line, the guard sent [swap, swap + CUT) to the floor, and the outgoing's rows went unity →
     * exactly 0 in one sample: the instantaneous cut the user ruled out (「不要让它戛然而止」),
     * measured on the device's own render as a 0.3227 jump against the signal's own 99.9th percentile
     * of 0.2648 (1.2x) where the spliced line reads 0.0913 (0.3x).
     */
    @Test
    public void theSlamSpliceRunsPastItsOwnLine() {
        StemFusion.Plan plan = slamFixture();
        long swap = plan.swapMs;
        assertEquals("the window contains the splice: fusionEnd is its END",
                StemFusion.CUT_MS, plan.fusionEndMs - swap);
        assertEquals(swap, plan.holdEndMs);
        assertEquals("the incoming arrives over the same splice",
                swap + StemFusion.CUT_MS, plan.arriveEndMs);
        assertEquals("and A's rows are at unity up to the line",
                1d, StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS, swap), 1e-9);
        assertEquals("half way through the splice they are at cos(pi/4)",
                Math.cos(Math.PI / 4),
                StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS,
                        swap + StemFusion.CUT_MS / 2), 1e-6);
        assertEquals("and at the floor where it ends", 0d,
                StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS, swap + StemFusion.CUT_MS),
                1e-9);
        assertEquals("the arriving side mirrors it", 0d,
                StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, swap), 1e-9);
        assertEquals(Math.sin(Math.PI / 4),
                StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS,
                        swap + StemFusion.CUT_MS / 2), 1e-6);
        assertEquals("and is at unity where it ends", 1d,
                StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, swap + StemFusion.CUT_MS),
                1e-9);
        // Monotone on both sides, so no element comes back and the sum only falls.
        for (StemGesture.Stem row : new StemGesture.Stem[]{StemGesture.Stem.DRUMS,
                StemGesture.Stem.BASS}) {
            double previous = Double.MAX_VALUE;
            for (long at = plan.entryMs; at <= plan.fusionEndMs; at += 5L) {
                double g = StemFusion.gainAt(plan, true, row, at);
                assertTrue(row + " must never rise again", g <= previous + 1e-9);
                previous = g;
            }
            double rising = -1d;
            for (long at = plan.entryMs; at <= plan.fusionEndMs; at += 5L) {
                double g = StemFusion.gainAt(plan, false, row, at);
                assertTrue(row + " must never fall again", g >= rising - 1e-9);
                rising = g;
            }
        }
    }

    /**
     * ⚠️ <b>The incoming side of the gesture, pinned as numbers.</b> The pending placement change
     * (the junction moves to the outgoing's own file end, so A's own ending is heard instead of a
     * passage cut out of it) touches the A side only: how long the file's copy of A is, and where
     * A's rows stop. B's side — the bed's rise, the drums' and low end's arrival, the window and
     * where the body takes over — is the SAME search and the SAME table it is today, so these
     * numbers must not move: a change to one of them is a change to the incoming track's part of the
     * gesture, which the user has not asked for and did not approve.
     *
     * <p>They are literals on purpose. If a later change makes one of them fail, that change is
     * either the placement (and then B's side has moved with it, which is the bug this pins) or a
     * deliberate new gesture (and then the user's approval is what has to be checked, not this test).
     */
    @Test
    public void theIncomingSidesLinesArePinnedForThePlacementChange() {
        StemFusion.Plan plan = fixture();
        assertEquals("the deck still starts on the incoming's own bar line", 16_000L, plan.entryMs);
        assertEquals("B's bed still rises from the entry over one bar of ITS grid", 18_000L,
                plan.bedFadeMs);
        // ⚠️ The arrival is where the placement put it (round 6's eleventh pass — decision (1)):
        // one step, ENDING on A's own last sample. This is the one thing in the incoming's part of
        // the gesture that moved, and it moved because the alternative was a 2 360 ms window with no
        // rhythm in either backing (`entry + two steps`), which the acceptance's pulse clause
        // refuses. The numbers are literals on purpose: if a later change makes one fail, that change
        // is either the placement (and then B's side has moved with it, which is the bug this pins)
        // or a deliberate new gesture (and then the user's approval is what has to be checked).
        assertEquals("its drums and low end arrive over one step ending at A's ending",
                16_000L, plan.arriveStartMs);
        assertEquals("which is A's own last sample", 18_000L, plan.arriveEndMs);
        assertEquals(plan.holdEndMs, plan.arriveEndMs);
        assertEquals("the arrival's own length is still one step", Math.round(plan.barStepMs),
                plan.arriveEndMs - plan.arriveStartMs);
        assertEquals("the window is still the table's own span", 8_000L, plan.windowMs);
        assertEquals("and B's rows are at unity where the body's own audio takes over",
                plan.entryMs + plan.windowMs, plan.fusionEndMs);
    }

    /**
     * The four pairings the round-18 prototype measured on real material, with the numbers the
     * harness's own scan produced (`D:\qplayer-dev\harness\fusion\{grids,qpair-scan}.json`):
     * the grids, the blend and the content start are the harness's, and the verdict is what this
     * planner makes of them.
     *
     * <p>Round 5 lives in the first three of them: two fuse as relations (their bar grids coincide)
     * and one as the SLAM the user asked for, which is the whole point of the relation family — the
     * old candidate clause refused every pair whose beat periods are not equal, and the measurement
     * of the 1260-pair library showed that is the same set as the pairs that fade today. The
     * fourth keeps round 18's bargain exactly, bit for bit.
     */
    @Test
    public void theFourRealPairsVerdicts() {
        // ⚠️ Round 5: three of these four now fuse — that is the whole point of the relation
        // family. The old clause refused every pair whose beat periods are not equal, which the
        // measurement of the 1260-pair library showed is the same set as the pairs that fade today
        // (87.6% of all pairs were refused by it alone).
        //
        // owa -> paradise: 476.6 ms against 937.6 ms — a ratio of 1.9672, 1.67% from the 2:1
        // relation, so the swaps land on one bar of paradise's grid (two of owa's). The prototype
        // rendered this pair (and the user heard its junction); under round 5 it is a FUSION.
        assertRelation(1, 2, 476.598639d, 937.55102d, 1d, 0.0167d, "audio_owa", "audio_paradise");
        // ⚠️ Round 6's cost bound, with the numbers, on that same pair: its step is one bar of the
        // incoming's grid (3750.2 ms) and its carry is read at 0.9836, so A's rows need
        // ceil((4*3750.2 + 80)/0.9836) = 15 333 ms of A's own file and the separation window
        // 17 333 ms with the two leads — over the unison cap (12 000, which refused it) and inside
        // the relative one (20 000, which is why the two caps differ).
        assertEquals("the passage A's rows need from A", 15_333L,
                StemFusion.sourceSpanMs(937.55102d, 0.98359d));
        assertEquals("and the window one render separates, with both leads",
                17_333L, StemFusion.sourceSpanMs(937.55102d, 0.98359d) + 2L * 1_000L);
        assertTrue("which is over the unison cap", 17_333L > StemFusion.FUSION_TAIL_MAX_MS);
        assertTrue("and inside the relative one", 17_333L <= StemFusion.RELATIVE_TAIL_MAX_MS);
        // obsessed -> owa: 838.8 ms against 476.6 ms — 13.6% from the nearest relation (1/2), i.e.
        // in none, so it is the SLAM the user asked for: one bar of the incoming's grid, no beat
        // matching, every element changing hands on the same line.
        assertSlam(838.77551d, 476.598639d, "audio_obsessed", "audio_owa");
        // owa -> audio1: 476.6 ms against 459.6 ms — 3.69% from the unison, inside
        // RELATION_TOLERANCE and outside LOCK_TOLERANCE, so it is a 1:1 RELATIVE whose residual the
        // carry's stretch absorbs (the deck itself plays at x1.0).
        assertRelation(1, 1, 476.598639d, 459.637188d, 1d, 0.0369d, "audio_owa", "audio1");
        // huai -> owa: 472.6 ms against 476.6 ms — 0.84% out, inside LOCK_TOLERANCE, which is the
        // case that keeps round 18's bargain exactly. Its anchors are the prototype's own.
        assertLocked(true, 472.60771d, 476.598639d, 1d, 0.0084d, "audio_huai", "audio_owa");

        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(187_241L, 15_000L, 15_000L,
                0L, 472.60771d, 210d, 476.598639d, 12d, 1d,
                bars(1890.43084d, 210d, 100), bars(1906.394556d, 12d, 100),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(plan.reason, plan.valid);
        // ⚠️ The placement, on the round-18 prototype's own pair (round 6's eleventh pass): the
        // junction is A's own file end less the hand-over — 187 241 - 2 000 = 185 241 — and the deck
        // still starts on B's first bar line (12 ms). The prototype's own 172 239 junction was a line
        // 248 ms past its target; nothing chooses it any more.
        assertEquals(185_241L, plan.junctionMs);
        assertEquals(0L, plan.junctionShiftMs);
        assertEquals(12L, plan.entryMs);
        long step = Math.round(476.598639d * 4d);
        // A's rows are at unity to its own last sample: 12 + JUNCTION_XFADE_MS = 2 012.
        assertEquals("A's rows end at A's own file end", 12L + StemFusion.JUNCTION_XFADE_MS,
                plan.holdEndMs);
        assertEquals("and that is where every A-side instant is", plan.holdEndMs, plan.swapMs);
        assertEquals(plan.holdEndMs, plan.bassMs);
        assertEquals(plan.holdEndMs, plan.drumsEndMs);
        assertEquals(plan.holdEndMs, plan.lowEndEndMs);
        // B's arrival is one step of B's grid ending there.
        assertEquals("the arrival is one step of the incoming's grid", step,
                plan.arriveEndMs - plan.arriveStartMs);
        assertEquals(plan.holdEndMs, plan.arriveEndMs);
        assertEquals(Math.round(4d * 1906.394556d), plan.windowMs);
        // The take is A's own ending: JUNCTION_XFADE_MS of A's file, from 187 241 - 2 000 = 185 241 to
        // the end, and the window the render separates is that plus A_TAIL_SLACK_MS on either side —
        // a fifth of what the four-step passage used to need.
        assertEquals(2_000L, plan.sourceSpanMs);
        assertEquals(185_241L, plan.sourceFromMs);
        assertEquals(187_241L, plan.sourceFromMs + plan.sourceSpanMs);
        assertEquals(plan.sourceSpanMs + 2L * StemFusion.A_TAIL_SLACK_MS, plan.materialWindowMs);
        assertEquals(plan.sourceFromMs - StemFusion.A_TAIL_SLACK_MS, plan.materialFromMs);
        // The search's own two numbers are zero: there is no band and nothing shifts.
        assertEquals(0L, plan.searchBandMs);
        assertEquals(0L, plan.searchBackMs);
    }

    /**
     * ⚠️ Round 5's quiet-passage clause, as the placement leaves it (round 6's eleventh pass).
     *
     * <p>The clause used to refuse a junction whose passage was inside the outgoing's own fade,
     * because the search had alternatives to try instead. With the junction placed at A's own file
     * end there is no alternative to try, and a track whose last seconds are its own fade is exactly
     * the ending the user asked to hear — so the REFUSAL is suppressed for the placed junction and
     * the measurement stays: it is read at the junction and reported (`Plan#bodyLevelAtJunction` is
     * true, and the describe line says what was measured against the body). This test is what pins
     * that: the same material that used to be refused now plans, in both variants.
     */
    @Test
    public void aPassageInsideAFadeIsNotRefusedForThePlacedJunction() {
        // A's bar lines every 800 ms from 100 000, the body measurement refusing everything from
        // 218 000 on (a fade 30 dB down) and the junction at 238 000 — deep in it.
        double[] aBars = bars(800d, 100_000d, 200);
        StemFusion.BodyLevel body = new StemFusion.BodyLevel() {
            @Override
            public double bodyDb() {
                return -8d;
            }

            @Override
            public double passageDropDb(long atMs, long passageMs) {
                // The fade: everything from 218 000 on is 30 dB down, everything before it is 1 dB
                // under the body (so within QUIET_PASSAGE_DB).
                return atMs >= 218_000L ? 30d : 1d;
            }
        };
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 200d, 0d, 200d, 0d, 1d, aBars, bars(800d, 0d, 300),
                StemFusion.NO_VOICE_MEASUREMENT, StemFusion.NO_GROOVE_MEASUREMENT, body, -1L));
        assertTrue(plan.reason, plan.valid);
        assertEquals("the junction is A's own ending, fade or not: " + plan.junctionMs,
                238_000L, plan.junctionMs);
        assertTrue("and the report still says what the clause measured",
                plan.bodyLevelAtJunction);
        assertTrue(plan.describe(), plan.describe().contains("no body-level clause"));

        // The same material with no body-level line ANYWHERE — the case the refusal used to name —
        // is also a plan now. The suppression is for the placed junction, and the placed junction is
        // the only junction there is.
        StemFusion.BodyLevel alwaysFading = new StemFusion.BodyLevel() {
            @Override
            public double bodyDb() {
                return -8d;
            }

            @Override
            public double passageDropDb(long atMs, long passageMs) {
                return 30d;
            }
        };
        StemFusion.Plan fading = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 200d, 0d, 200d, 0d, 1d, aBars, bars(800d, 0d, 300),
                StemFusion.NO_VOICE_MEASUREMENT, StemFusion.NO_GROOVE_MEASUREMENT, alwaysFading,
                -1L));
        assertTrue(fading.describe(), fading.valid);
        assertEquals(238_000L, fading.junctionMs);
        assertFalse("the refusal's own message is gone from this plan",
                fading.reason.contains("below the track's own body"));
    }

    /**
     * ⚠️ Round 5's intro skip (「直接词接词」): the incoming track's voice arrives 12 s in (a rap
     * track's vocal-free intro, measured on the device's {@code 34364062}: its first sustained
     * vocal second is at 12 125 ms), so the deck starts at the common line a runway before that
     * voice instead of at the content start — and its voice arrives right after the outgoing's,
     * with the dead intro never played. When the voice is there from the start, nothing moves.
     */
    @Test
    public void aDeadIntroIsSkippedAndAVoiceFromTheStartIsNot() {
        double[] bBars = bars(2000d, 0d, 120);
        StemFusion.Plan skipped = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 40_000L,
                0L, 500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), bBars,
                StemFusion.NO_VOICE_MEASUREMENT, StemFusion.NO_GROOVE_MEASUREMENT,
                StemFusion.NO_BODY_MEASUREMENT, 12_125L));
        assertTrue(skipped.reason, skipped.valid);
        assertEquals("the line a runway before the voice (12 000) is the deck's start", 10_000L,
                skipped.entryMs);
        assertEquals("and 2 000 ms of intro were skipped", 2_000L, skipped.skippedIntroMs);
        assertEquals(12_125L, skipped.firstVocalMs);
        assertTrue(plan(skipped), skipped.fusionEndMs <= 40_000L);
        assertTrue(skipped.describe(), skipped.describe().contains("skipping 2000ms of its intro"));

        // The voice from the start: the entry is where it always was.
        StemFusion.Plan immediate = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 40_000L,
                0L, 500d, 0d, 500d, 0d, 1d, bars(2000d, 0d, 120), bBars,
                StemFusion.NO_VOICE_MEASUREMENT, StemFusion.NO_GROOVE_MEASUREMENT,
                StemFusion.NO_BODY_MEASUREMENT, 200L));
        assertTrue(immediate.reason, immediate.valid);
        assertEquals(0L, immediate.entryMs);
        assertEquals(0L, immediate.skippedIntroMs);
    }

    /** A plan's own evidence, for a message. */
    private static String plan(StemFusion.Plan p) {
        return p.describe();
    }

    /** A pair's relation verdict: {@code p:q} within tolerance, and the plan that comes of it. */
    private static void assertRelation(int p, int q, double aBeatMs, double bBeatMs, double speed,
                                       double expectedError, String a, String b) {
        StemFusion.Relation relation = StemFusion.relationOf(aBeatMs, bBeatMs, speed);
        assertNotNull(a + " -> " + b + " should be a relation", relation);
        assertEquals(p, relation.p);
        assertEquals(q, relation.q);
        assertEquals(a + " -> " + b, expectedError, relation.error, 0.001d);
        assertFalse("a relative pair more than LOCK_TOLERANCE out is not the unison case",
                relation.locked() && expectedError > StemFusion.LOCK_TOLERANCE);
        assertNull("the pre-decode clause accepts a relation too: "
                        + StemFusion.refusal(200_000L, 15_000L, 15_000L, 0L, aBeatMs, bBeatMs, speed),
                StemFusion.refusal(200_000L, 15_000L, 15_000L, 0L, aBeatMs, bBeatMs, speed));
    }

    /** A pair in no relation fuses as a SLAM: one step of the table, the swaps on one line. */
    private static void assertSlam(double aBeatMs, double bBeatMs, String a, String b) {
        assertNull(a + " -> " + b + " should be in no relation",
                StemFusion.relationOf(aBeatMs, bBeatMs, 1d));
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(200_000L, 15_000L, 20_000L, 0L,
                aBeatMs, 0d, bBeatMs, 0d, 1d, bars(aBeatMs * 4, 0d, 200), bars(bBeatMs * 4, 0d, 400),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(plan.reason, plan.valid);
        assertTrue("it is a SLAM", plan.slam);
        // ⚠️ One bar of the incoming's own grid PLUS the splice — but on a placed junction the
        // window is the WIDEST of the three the placement's rule names (round 6's eleventh pass): the
        // gesture's own span (1 906 ms + 80), the arrival's reach (`aEnds + CUT - entry` = 2 080 ms,
        // because the copy of A's ending is JUNCTION_XFADE_MS long) and the bed's bar (1 906). The
        // arrival is the widest, and it is the one that matters: a window that ended 14 ms short of
        // the copy truncated A's ending.
        assertEquals("the copy of A's ending plus its de-click splice",
                Math.max(StemFusion.JUNCTION_XFADE_MS, Math.round(bBeatMs * 4)) + StemFusion.CUT_MS,
                plan.windowMs);
        assertEquals("every element changes hands on the same line", plan.swapMs, plan.bassMs);
        assertEquals("and all of A is gone where the splice ends", plan.swapMs + StemFusion.CUT_MS,
                plan.fusionEndMs);
        assertEquals("the incoming's arrival is that same splice", plan.swapMs,
                plan.arriveStartMs);
        assertEquals(plan.swapMs + StemFusion.CUT_MS, plan.arriveEndMs);
        assertEquals("and the carry's stretch is the deck's own ratio", 1d, plan.stretch, 1e-9);
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
    public void theOutgoingRecedesAndTheIncomingRises() {
        // ⚠️ Rewritten for the placement (round 6's eleventh pass). The round-6 shape was a RECEDE —
        // a hold at unity and two fades — and its reason was that A must not be force-stopped
        // (「不要让它戛然而止，可以加淡出」). The placement satisfies the same requirement by other means: A's
        // rows are at unity to its own last sample, the fade is the boundary's deck-level hand-over,
        // and the file's copy of A's ending is what that hand-over plays. So the A-side spans measure
        // ZERO and the table is the same table with them at zero; B's side is the same rise it was,
        // moved to meet A's last sample.
        StemFusion.Plan plan = fixture();
        long entry = plan.entryMs;
        long step = Math.round(plan.barStepMs);
        long holdEnd = plan.holdEndMs;
        long aEnds = entry + StemFusion.JUNCTION_XFADE_MS;
        double unity = 1d;
        double aOther = Math.pow(10d, StemFusion.A_OTHER_DB / 20d);
        assertEquals("A's rows end at A's own file end", aEnds, holdEnd);
        assertEquals("and the take runs to A's last sample", 240_000L,
                plan.sourceFromMs + plan.sourceSpanMs);
        assertEquals("the drums' fade is zero", 0L, plan.drumsEndMs - holdEnd);
        assertEquals("and so is the low end's", 0L, plan.lowEndEndMs - holdEnd);
        assertEquals("nothing is spliced in a fusion any more", 0, plan.splices());
        assertEquals(3, plan.recedeMs().length);
        assertEquals("the recede's own report is the copy and two zeros",
                StemFusion.JUNCTION_XFADE_MS, plan.recedeMs()[0]);
        assertEquals(0L, plan.recedeMs()[1]);
        assertEquals(0L, plan.recedeMs()[2]);

        // Unity to the last sample: the file is continuous with the outgoing's live deck for the
        // WHOLE of the deck-level hand-over, which is what makes the two sides of that hand-over the
        // same music.
        for (StemGesture.Stem row : new StemGesture.Stem[]{StemGesture.Stem.DRUMS,
                StemGesture.Stem.BASS, StemGesture.Stem.OTHER}) {
            assertEquals(row + " at the entry", row == StemGesture.Stem.OTHER ? aOther : unity,
                    StemFusion.gainAt(plan, true, row, entry), 1e-9);
            assertEquals(row + " still at unity at A's last sample",
                    row == StemGesture.Stem.OTHER ? aOther : unity,
                    StemFusion.gainAt(plan, true, row, holdEnd), 1e-9);
        }
        // ...and at the floor immediately after it: there is no second fade to measure inside the
        // file, because the fade the user asked for is the hand-over's own.
        for (StemGesture.Stem row : new StemGesture.Stem[]{StemGesture.Stem.DRUMS,
                StemGesture.Stem.BASS, StemGesture.Stem.OTHER}) {
            assertEquals(row + " one sample past A's end", 0d,
                    StemFusion.gainAt(plan, true, row, holdEnd + 1L), 1e-9);
        }
        // Monotone: no element comes back, ever.
        for (StemGesture.Stem row : new StemGesture.Stem[]{StemGesture.Stem.DRUMS,
                StemGesture.Stem.BASS, StemGesture.Stem.OTHER}) {
            double previous = Double.MAX_VALUE;
            for (long at = entry; at <= plan.fusionEndMs; at += 13L) {
                double g = StemFusion.gainAt(plan, true, row, at);
                assertTrue(row + " must never rise again (" + g + " at " + at + ")", g <= previous + 1e-9);
                previous = g;
            }
        }
        // B's arrival: a rise over one step ENDING on A's last sample, so the pulse is continuous
        // across the hand-over (the 2 360 ms hole the old lines left is what the pulse clause
        // refused).
        long arriveStart = plan.arriveStartMs;
        long arriveEnd = plan.arriveEndMs;
        assertEquals("B's drums and low end start arriving one step before A's ending",
                holdEnd - step, arriveStart);
        assertEquals("and reach unity on A's last sample", holdEnd, arriveEnd);
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, arriveEnd), 1e-9);
        assertEquals(Math.sin(Math.PI / 4), StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS,
                (arriveStart + arriveEnd) / 2), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, arriveStart), 1e-9);

        // Outside the window: today's content (the incoming's own backing at unity) and nothing of
        // the outgoing's.
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, entry - 1L), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, true, StemGesture.Stem.BASS, entry - 1L), 1e-9);
        assertEquals(unity, StemFusion.gainAt(plan, false, StemGesture.Stem.OTHER, plan.fusionEndMs), 1e-9);
        assertEquals(0d, StemFusion.gainAt(plan, true, StemGesture.Stem.DRUMS, plan.fusionEndMs), 1e-9);
    }

    /** How long a row takes to get within 6 dB of its own level (a rise) or of the floor (a fall),
     *  ms — the measurement the round-6 shapes are chosen by. */
    private static long reachMs(StemFusion.Plan plan, boolean fromOutgoing, StemGesture.Stem row,
                                long fromMs, double target, double tol) {
        for (long at = fromMs; at <= plan.fusionEndMs; at += 5L) {
            if (Math.abs(StemFusion.gainAt(plan, fromOutgoing, row, at) - target) <= tol) {
                return at - fromMs;
            }
        }
        return -1L;
    }

    @Test
    public void theRecedeAndArrivalTimesAreTheOnesTheDesignNames() {
        StemFusion.Plan plan = fixture();
        long step = Math.round(plan.barStepMs);
        // ⚠️ Rewritten for the placement (round 6's eleventh pass). There is no A-side fade inside
        // the file any more, so the two measurements that were about it are gone: what is left is
        // B's rise — an equal-power rise is a sine, so its -6 dB point comes after ONE THIRD of its
        // step — and the overlap between that rise and A's rows at unity.
        long arriveSix = reachMs(plan, false, StemGesture.Stem.DRUMS, plan.arriveStartMs, 1d, 0.5d);
        println("B's drums reach -6 dB %dms into their 1-step rise (one step is %dms)",
                arriveSix, step);
        assertEquals(Math.round(1d / 3d * step), arriveSix, 15L);
        // ⚠️ The coexistence, re-derived for the placement: B's rise runs over the whole step
        // BEFORE A's last sample, so the two backings are both present for that whole step — the
        // span in which B is rising under A's own rows at unity. The TABLE's own 6 dB overlap is
        // shorter now (the sine's -6 dB point, 2/3 of a step, meets A's last sample), because the
        // old geometry gave the rise a second step of A's unity to climb inside. The half the
        // requirement is about — that the fade does not dominate, i.e. the incoming is there while
        // the outgoing leaves — is unchanged: A's rows are at unity for the rise's whole span, and
        // the audible instrument reads the same or more (fusion/coexist.py, round 6's ninth pass:
        // 1750 ms in all on the device's material).
        long from = plan.arriveStartMs + arriveSix;
        long to = plan.holdEndMs;
        println("the table's own gain overlap (both backings within 6 dB of their own level): %dms"
                        + " (one step is %dms); the span A's rows are at unity under the rise is a"
                        + " whole step", to - from, step);
        assertEquals("the sine's -6 dB meets A's last sample two thirds of a step in",
                Math.round(2d / 3d * step), to - from, 15L);
        assertEquals("and the rise is a whole step of coexistence", step,
                plan.arriveEndMs - plan.arriveStartMs);
        assertFalse("the old geometry's -6 dB overlap was one and a third steps: " + (to - from),
                to - from > step);
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
        // ⚠️ Round 6: the incoming's drums and low end are not cut in either — they RISE, over one
        // step, starting one step BEFORE A's rows end (which is the same instant the bed starts
        // from), so both backings are near their own level together for the step that matters. Zero
        // exactly at the rise's start, cos/sin-exact in the middle, unity from its end on — and its
        // end is now A's last sample rather than two steps past the entry.
        long arriveStart = plan.arriveStartMs;
        long arriveEnd = plan.arriveEndMs;
        assertEquals("the rise starts where the file's window does", entry, arriveStart);
        assertEquals("it is one step long", Math.round(plan.barStepMs),
                arriveEnd - arriveStart);
        assertEquals("and it ends on A's last sample", plan.holdEndMs, arriveEnd);
        assertEquals(0d, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, arriveStart), 1e-9);
        assertEquals(Math.sin(Math.PI / 4), StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS,
                (arriveStart + arriveEnd) / 2L), 1e-9);
        assertEquals(1d, StemFusion.gainAt(plan, false, StemGesture.Stem.DRUMS, arriveEnd), 1e-9);
        assertEquals(1d, StemFusion.gainAt(plan, false, StemGesture.Stem.BASS, arriveEnd), 1e-9);
        assertEquals("A's low end is still at unity where B's drums top out — the copy's own last"
                        + " sample is where the deck-level hand-over ends", 1d,
                StemFusion.gainAt(plan, true, StemGesture.Stem.BASS, arriveEnd), 1e-9);
    }

    /**
     * ⚠️ Round 4's log fix, pinned: the line used to print {@code windowMs / sourceSpanMs} as "played
     * back at x…" — three bars of the incoming's grid over two bars plus a splice of the outgoing's,
     * a bar-count ratio that is above 1 on every pair. On the device's pair it printed
     * <b>x1.4649</b> for a deck that played the file at <b>x1.0000</b>. Both numbers are the deck's
     * ratio now, and this pins that they are.
     */
    @Test
    public void theDescribeReportsTheDecksRatioAndNotABarCountRatio() {
        // ⚠️ A 4% bar mismatch with the deck at x1.0 is no longer a plan at all — it is the
        // incoherence `plan` refuses (see theDecksRatioAndTheCarrysRatioMustAgree) — so the pair
        // that exercises the describe line is the COHERENT one: B's beat is 4% longer than A's and
        // the deck plays the file 4% faster, which is what returns the carry to the outgoing's own
        // tempo and pitch.
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 500d, 0d, 520d, 0d, 1.04d, bars(2000d, 0d, 120), bars(2080d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(plan.reason, plan.valid);
        assertEquals("the deck's own ratio", 1.04d, plan.speed, 1e-9);
        assertEquals("and the carry's — the same number, which is the point of the clause",
                1.04d, plan.stretch, 1e-9);
        assertEquals(0.0385d, plan.relation.error, 0.001d);
        assertTrue(plan.describe(), plan.describe().contains("x1.0400"));
        assertFalse("the bar-count ratio must not be in the line: " + plan.describe(),
                plan.describe().contains("x1.83"));
        // ⚠️ The identity the take now has instead of "the table's own span": the take is A's own
        // ending, so played at the deck's ratio it fills EXACTLY the file's [entry, entry +
        // JUNCTION_XFADE_MS*speed) — the copy of A's last two seconds, which is the span the
        // deck-level hand-over crossfades against A's live deck.
        assertEquals(Math.round(StemFusion.JUNCTION_XFADE_MS * plan.speed),
                plan.sourceSpanMs * plan.stretch, 2.0d);
        assertEquals(StemFusion.JUNCTION_XFADE_MS, plan.sourceSpanMs);
    }

    /**
     * ⚠️ The coupling, pinned: the deck's ratio and the carry's ratio are ONE decision, and a pair
     * whose deck will not take the ratio the carry needs is not a fusion either way — at the bar
     * ratio the outgoing's material is played at a tempo that is not its own, at the deck's ratio
     * its bar boundaries slide against the incoming's beats. The device's own AGUDO -> Lose My Mind
     * is the case: its bar ratio is 0.9622 while a profile the deck may not stretch leaves the deck
     * at 1.0 (3.93% apart), and both numbers are in the refusal.
     */
    @Test
    public void theDecksRatioAndTheCarrysRatioMustAgree() {
        double aBeat = 566.4d;
        double bBeat = 545d;
        double barRatio = bBeat / aBeat;
        StemFusion.Plan incoherent = StemFusion.plan(new StemFusion.Input(96_816L, 15_000L, 15_240L,
                240L, aBeat, 0d, bBeat, 355d, 1d, bars(2_265.6d, 1_185.6d, 60),
                bars(2_180d, 900d, 9), StemFusion.NO_VOICE_MEASUREMENT));
        assertFalse(incoherent.describe(), incoherent.valid);
        assertTrue(incoherent.reason, incoherent.reason.contains("x1.0000"));
        assertTrue("the carry's own ratio is named too: " + incoherent.reason,
                incoherent.reason.contains(String.format(java.util.Locale.US, "x%.4f", barRatio)));
        assertTrue("and how far apart they are: " + incoherent.reason,
                incoherent.reason.contains(String.format(java.util.Locale.US, "%.2f%%",
                        100d * (1d / barRatio - 1d))));
        assertTrue("and what the answer is: " + incoherent.reason,
                incoherent.reason.contains("not a fusion"));
        // The same pair with the deck at the carry's ratio (the device's coherent render) plans, and
        // the carry is read at exactly the deck's ratio.
        StemFusion.Plan coherent = StemFusion.plan(new StemFusion.Input(96_816L, 15_000L, 15_240L,
                240L, aBeat, 0d, bBeat, 355d, barRatio, bars(2_265.6d, 1_185.6d, 60),
                bars(2_180d, 900d, 9), StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(coherent.describe(), coherent.valid);
        assertEquals(barRatio, coherent.stretch, 1e-9);
        assertEquals(barRatio, coherent.speed, 1e-9);
    }

    /**
     * The fallback a track with no low end needs (round 4): {@code 1410815174}, from the device run,
     * reads a loud tenth of −68.3 dBFS on its separated head's bass, so a downbeat estimate on it is
     * a coin flip; its bar lines come from the beat grid instead. The beats are then the measured
     * ones and the bar grouping is the guess.
     */
    @Test
    public void aTrackWithNoLowEndGetsItsBarLinesFromItsBeatGrid() {
        double[] grid = StemFusion.barLinesOfBeatGrid(416.734694d, 45d, 0d, 18_800d);
        assertEquals(12, grid.length);
        assertEquals(45d, grid[0], 1e-6);
        assertEquals(45d + 4 * 416.734694d, grid[1], 1e-6);
        // Whatever the phase, a two-bar window holds a line: that is what the entry search needs,
        // and what its refusal ("no bar line of the incoming track inside its own two-bar window")
        // must not be able to say when the track has a grid.
        for (double phase = 0d; phase < 1666.9d; phase += 137d) {
            double[] lines = StemFusion.barLinesOfBeatGrid(416.734694d, phase, 40d, 3334d);
            boolean inside = false;
            for (double at : lines) if (at >= 40d && at < 40d + 3334d) inside = true;
            assertTrue("a line inside [40, 3374) for a phase of " + phase, inside);
        }
        assertEquals(0, StemFusion.barLinesOfBeatGrid(0d, 0d, 0d, 1000d).length);
        assertEquals(0, StemFusion.barLinesOfBeatGrid(400d, 0d, 0d, 0d).length);
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
        // ⚠️ Counted over the window's unity stretch (the hold), not over the whole take: the take's
        // last steps are the fade, which takes the bursts below the counting threshold — that is the
        // round-6 gesture working, not a defect.
        int unityFrames = (int) Math.round((plan.holdEndMs - plan.entryMs) * RATE / 1000d);
        int inFile = countBursts(carried[StemGesture.Stem.DRUMS.row()][0],
                (int) Math.round(0.01d * RATE), 0, unityFrames);
        assertEquals((plan.holdEndMs - plan.entryMs) / (beatSec * 1000d * speed), inFile, 1.0d);

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
        // ⚠️ The incoming's own "before the arrival" span is EMPTY on a placed junction (round 6's
        // eleventh pass): its drums and low end arrive over the step that ends on A's last sample, so
        // there is no stretch of the file before them to measure — the span reads "unknown" (NaN),
        // exactly as it does on a slam, and the clause declines rather than refusing. After the
        // arrival the rows are there for the rest of the window, which is what is measured.
        assertTrue("an empty span is unknown, not silence: " + report.incomingDrumsBeforeDb,
                Double.isNaN(report.incomingDrumsBeforeDb));
        assertTrue(report.describe(), report.incomingDrumsAfterDb > StemFusion.SILENT_DBFS);
        assertTrue("nor is the low end's: " + report.incomingBassBeforeDb,
                Double.isNaN(report.incomingBassBeforeDb));
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
        // ⚠️ The first sample the make-up cannot touch is the one AFTER A's last sample: the table's
        // own boundary is inclusive (`fade` returns its `from` level at `fromMs`), so the sample AT
        // A's last sample still carries A's rows at unity and is legitimately lifted. (Round 6's
        // eleventh pass moved that boundary onto the copy's own last sample, which is where this
        // region's start comes from.)
        int from = (int) Math.round((plan.holdEndMs - plan.entryMs) * RATE / 1000d) + 1;
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
        // A slow outgoing track: a 1 s beat, a 4 s bar. The material the gesture needs is the
        // window (see the assertion below), so the report is measured against a source window of
        // that length — but what is carried is only part of it, as a short separation would give.
        // ⚠️ Round 5: a 1000 ms against a 500 ms beat is a 2:1 relation, so this is a plan now (the
        // point of the test is the material its carry needs, not the refusal it used to be).
        StemFusion.Plan plan = StemFusion.plan(new StemFusion.Input(240_000L, 20_000L, 35_000L,
                15_000L, 1000d, 0d, 500d, 0d, 1d, bars(4000d, 0d, 60), bars(2000d, 0d, 120),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(plan.describe(), plan.valid);
        assertEquals(2, plan.relation.p);
        assertEquals(1, plan.relation.q);
        // The same pair, locked (both grids 1 s beats) is a real fusion: the bass must be in the
        // file right up to the 2 s bar line, and it is.
        StemFusion.Plan locked = StemFusion.plan(new StemFusion.Input(400_000L, 20_000L, 35_000L,
                15_000L, 1000d, 0d, 1000d, 0d, 1d, bars(4000d, 0d, 100), bars(4000d, 0d, 100),
                StemFusion.NO_VOICE_MEASUREMENT));
        assertTrue(locked.describe(), locked.valid);
        // ⚠️ Rewritten for the placement (round 6's eleventh pass): the A-side spans are zero on
        // every pair now — a 4 s step included — so what this pair's numbers pin is the WINDOW and
        // the take: the window is still the shape's own 2 steps (a 4 s step cannot afford the
        // design's 4, see shapeFor) and the take is A's own ending (JUNCTION_XFADE_MS + the deck's
        // start offset), which is what the render separates.
        assertEquals("A's rows end at A's own ending", 2_000L, locked.holdEndMs - locked.entryMs);
        assertEquals("and its fades measure zero", 0L, locked.lowEndEndMs - locked.holdEndMs);
        assertEquals("the take is A's own last JUNCTION_XFADE_MS", StemFusion.JUNCTION_XFADE_MS,
                locked.sourceSpanMs);
        assertEquals(8_000L, locked.fusionEndMs - locked.entryMs);

        float[][][] a = cleanOutgoing(30d);
        Ran full = render(locked, 1d, true, a, cleanIncoming(30d), 1d, 1d, true);
        println("%s", full.report.describe());
        assertTrue(full.report.describe(), full.report.stepMeasured);
        assertTrue(full.report.describe(), full.report.acceptable);
        // Now the same render with the take cut short: the second half of A's material is gone, so
        // the outgoing's low end reaches the middle of the gesture and then fades SILENCE — the
        // round-18 defect in its round-6 costume, which is what the fade's own clause catches (a
        // clause on the level before the gesture takes over cannot see inside the fade, because
        // there the row is falling by the table's own design).
        float[][][] short_ = truncateRows(a, locked, 0.5d);
        Ran clipped = render(locked, 1d, true, short_, cleanIncoming(30d), 1d, 1d, true, a, 0d);
        println("%s", clipped.report.describe());
        assertFalse(clipped.report.describe(), clipped.report.acceptable);
        assertTrue(clipped.report.failures,
                clipped.report.failures.contains("fades a hole")
                        || clipped.report.failures.contains("low end is not there"));
        // The clause is measurable rather than vacuous: with the full take it is satisfied.
        assertTrue(full.report.describe(),
                full.report.carriedBassEndDb > full.report.sourceBassEndDb
                        - StemFusion.CARRY_TOLERANCE_DB);
        // ⚠️ And the fade's own clause is SKIPPED BY DESIGN on a placed junction (round 6's eleventh
        // pass): it measured the outgoing's rows inside the recede's fade, and there is no fade inside
        // the file any more — the table's spans are zero and the fade the listener hears is the
        // boundary's deck-level hand-over. The measurement reports NaN ("not measured") rather than a
        // level, which is what the clause treats as unknown instead of as a floor. What stands in its
        // place is the clause above — the take has to be there right up to the junction — which is the
        // same defect (a separation that stopped early, or a take that ended in the wrong place) seen
        // where it now lives.
        assertTrue("the fade's own measurement is skipped on a placed junction: "
                        + full.report.carriedBassFadeDb,
                Double.isNaN(full.report.carriedBassFadeDb));
        assertTrue(Double.isNaN(full.report.sourceBassFadeDb));
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
