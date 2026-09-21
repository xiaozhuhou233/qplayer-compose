package dev.t1m3.qplayer.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The key blend's arithmetic: what a boundary plans, and the properties the plan has to have
 * for the thing to be the effect the user asked for rather than a scatter of pitch steps.
 *
 * <p>The load-bearing ones are the parallel-move invariants (the two decks' shifts differ by
 * the key distance at EVERY step, the incoming one ends at its own pitch, the outgoing one ends
 * a whole key away) and the grid property (every instant on the incoming track's own beat
 * grid, the last one at or before the rule's deadline).
 */
public class KeyGlideTest {

    /** 128 BPM: 468.75 ms a beat, 1875 ms a bar. */
    private static BeatProfile grid128() {
        return new BeatProfile(128d, 0L, 0.9f);
    }

    /** A confident 174 BPM grid whose first beat is not at zero — the ordinary case, where
     *  "on the grid" and "at a nice round time" are not the same thing. */
    private static BeatProfile grid174() {
        return new BeatProfile(174d, 137L, 0.8f);
    }

    @Test
    public void aSemitoneOverATwelveSecondWindowIsALadderOnTheBeatGrid() {
        // entry 0, deadline 12000ms of the incoming's file: a 12s modulation section. The grid
        // is read the way the grid itself reads it — whole milliseconds, the ROUNDED period —
        // because that is the arithmetic every other beat in this codebase is placed by.
        long period = Math.max(1L, Math.round(grid128().periodMs()));
        long deadline = grid128().beatAtOrBefore(12_000L);
        KeyGlide g = KeyGlide.plan(1, 0L, deadline, grid128());
        assertTrue(g.isGliding());
        assertTrue("at most MAX_STEPS writes per deck, got " + g.steps(),
                g.steps() <= KeyGlide.MAX_STEPS);
        assertTrue("at least MIN_STEPS", g.steps() >= KeyGlide.MIN_STEPS);
        assertEquals("the last step is the rule's deadline (the last beat at or before it)",
                deadline, g.atFileMs(g.steps() - 1));
        for (int i = 0; i < g.steps(); i++) {
            if (i > 0) assertTrue("instants rise", g.atFileMs(i) > g.atFileMs(i - 1));
            assertEquals("every step's instant is on the incoming track's own beat grid",
                    0L, (g.atFileMs(i) - grid128().firstBeatMs()) % period);
        }
        assertEquals("the incoming deck ends at its own pitch", 0d,
                g.incomingSemitones(g.steps() - 1), 1e-9);
        assertEquals("the outgoing deck ends a whole key into the incoming track's key",
                -1d, g.outgoingSemitones(g.steps() - 1), 1e-9);
        for (int i = 0; i < g.steps(); i++) {
            assertEquals("the two decks are the key distance apart at every step, which is"
                            + " what keeps them in one key while the tonality travels",
                    1d, g.incomingSemitones(i) - g.outgoingSemitones(i), 1e-9);
            assertTrue("the incoming deck's shift only ever falls towards zero",
                    g.incomingSemitones(i) >= -1e-9);
            assertTrue("the outgoing deck's shift only ever grows towards the incoming key",
                    g.outgoingSemitones(i) <= 1e-9);
        }
    }

    @Test
    public void theStepSizeIsSmallEnoughToBeAModulationAndNotASecondSnap() {
        KeyGlide one = KeyGlide.plan(1, 0L, 12_000L, grid128());
        for (int i = 1; i < one.steps(); i++) {
            double moved = Math.abs(one.incomingSemitones(i - 1) - one.incomingSemitones(i));
            assertTrue("a step is a fraction of a semitone, not a jump: " + moved,
                    moved <= 0.5d + 1e-9);
        }
        KeyGlide two = KeyGlide.plan(2, 0L, 12_000L, grid128());
        assertTrue("two semitones travel in at least as many steps as one",
                two.steps() >= one.steps());
        for (int i = 1; i < two.steps(); i++) {
            double moved = Math.abs(two.incomingSemitones(i - 1) - two.incomingSemitones(i));
            assertTrue("two semitones still move in halves at worst: " + moved,
                    moved <= 0.5d + 1e-9);
        }
    }

    @Test
    public void bothCurvesSpendTheWholeKeyDistanceOverTheSection() {
        for (int d = -2; d <= 2; d++) {
            if (d == 0) continue;
            KeyGlide g = KeyGlide.plan(d, 0L, 12_000L, grid128());
            assertTrue("d=" + d, g.isGliding());
            // What the decks hold before the first step is `d` on the incoming side (written by
            // the prepare) and 0 on the outgoing one: the ladder alone therefore has to travel
            // the whole distance, and it does so in steps of |d|/steps.
            assertEquals("the incoming deck's whole travel is the key distance", d,
                    g.semitones(), 1e-9);
            double perStep = Math.abs(d) / (double) g.steps();
            double first = Math.abs(g.incomingSemitones(0) - d);
            assertEquals("the first step moves one step's worth", perStep, first, 1e-9);
        }
    }

    @Test
    public void aWindowTooShortToHoldASectionGetsTheSingleStepInstead() {
        // Two beats at 128 BPM is the shortest section this class will glide in; one and a half
        // is not a section, so the boundary keeps round 13's single step and says why.
        KeyGlide g = KeyGlide.plan(1, 0L, 600L, grid128());
        assertFalse(g.isGliding());
        assertNotNull(g.note());
        assertTrue("the reason names the window it measured: " + g.note(),
                g.note().contains("one step it always did"));
    }

    @Test
    public void aGridThatIsNotTrustworthyIsNotUsedForSteppingOn() {
        BeatProfile weak = new BeatProfile(128d, 0L, 0.10f);
        KeyGlide g = KeyGlide.plan(1, 0L, 12_000L, weak);
        assertFalse(g.isGliding());
        assertTrue(g.note(), g.note().contains("not trustworthy"));
        assertFalse("no grid at all is the same answer", KeyGlide.plan(1, 0L, 12_000L, null)
                .isGliding());
    }

    @Test
    public void aPairThatIsNotTransposedHasNothingToGlide() {
        KeyGlide g = KeyGlide.plan(0, 0L, 12_000L, grid128());
        assertFalse(g.isGliding());
        assertTrue(g.note(), g.note().contains("not transposed"));
    }

    @Test
    public void theFirstStepNeverLandsBeforeTheDeckStarts() {
        // A section whose entry is well inside the file: nothing may be scheduled behind the
        // deck's own start, or the first write would be a step the listener never heard planned.
        KeyGlide g = KeyGlide.plan(1, 8_000L, 20_000L, grid128());
        assertTrue(g.isGliding());
        assertTrue("first instant " + g.atFileMs(0) + " is at or after the entry " + 8_000L,
                g.atFileMs(0) >= 8_000L);
        assertEquals(12_000L, g.sectionFileMs());
    }

    @Test
    public void anOffGridBeatPhaseIsRespectedRatherThanRoundedAway() {
        long period = Math.max(1L, Math.round(grid174().periodMs()));
        KeyGlide g = KeyGlide.plan(1, 0L, grid174().beatAtOrBefore(11_000L), grid174());
        assertTrue(g.isGliding());
        for (int i = 0; i < g.steps(); i++) {
            assertEquals("step " + i + " on the 174BPM grid (phase 137ms)",
                    0L, (g.atFileMs(i) - 137L) % period);
        }
    }

    @Test
    public void theDescriptionCarriesTheSectionAndBothCurves() {
        KeyGlide g = KeyGlide.plan(1, 0L, 12_000L, grid128());
        String note = g.describe();
        assertTrue(note, note.contains("the key glides +1 semitone"));
        assertTrue(note, note.contains("12000ms of the incoming track's own file"));
        assertTrue(note, note.contains("writes on EACH deck"));
        assertTrue("both curves are named", note.contains("-> 0") && note.contains("0 -> -1.00"));
    }

    @Test
    public void theOldEaseBackRequirementIsNoLongerPartOfTheRule() {
        // Round 13 shipped a single write but left the rule asking for six seconds of ease-back
        // room, which silently refused a transposition to every pair with less than eight
        // seconds of vocals-out window. The gate is now what the rule really needs: the margin
        // has to fall inside the blend.
        assertTrue(MixNaturaliser.pitchFitsBeforeVocals(7_000L, 15_000L));
        assertTrue(MixNaturaliser.pitchFitsBeforeVocals(2_500L, 4_000L));
        assertFalse("a vocal that arrives inside the margin is refused",
                MixNaturaliser.pitchFitsBeforeVocals(1_999L, 15_000L));
        assertFalse("the master's own vocals are in the blend's first sample: refused",
                MixNaturaliser.pitchFitsBeforeVocals(0L, 15_000L));
        assertFalse("a deadline past the end of the blend is refused",
                MixNaturaliser.pitchFitsBeforeVocals(20_000L, 15_000L));
    }
}
