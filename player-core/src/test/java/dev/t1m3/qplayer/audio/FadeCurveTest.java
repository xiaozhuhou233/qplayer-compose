package dev.t1m3.qplayer.audio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The shapes an overlap's gains can follow, and the one property of them that can
 * be measured rather than argued about: how much of the overlap has <em>both</em>
 * tracks audible at once.
 *
 * <p>The complaint that produced {@link FadeCurve#DJ_BLEND} was "it still sounds
 * like a fade-in/fade-out rather than a mix", and the reason is arithmetic: with the
 * symmetric pair (linear or equal power) the two gains are only comparable near the
 * middle of the window, so one track is alone at each end. A listener hears that as
 * a song changing. The staged shape is the answer, and these numbers are what says
 * so — about 68% of the window against the symmetric pair's 41%, for about 2.3 dB of
 * extra summed power in the middle.
 *
 * <p>Round 16's half of the same story is the other end of the shape: the staged
 * shape held the outgoing track at its own level for so long that its exit became a
 * cliff, which the user heard as the previous song being cut off. The exit now has
 * its own window, and what says so is the second group of assertions here: the level
 * at nine tenths, the inaudibility at 95%, the monotone descent, and the held zero
 * the promotion releases the outgoing player inside.
 */
public class FadeCurveTest {

    /** The ordinary overlap: what every target in this test is measured against. */
    private static final long LONG_MS = 15_000L;

    @Test
    public void theDjShapeKeepsBothTracksAudibleThroughMostOfTheOverlap() {
        long symmetric = FadeCurve.EQUAL_POWER.bothAudibleMs(LONG_MS);
        long dj = FadeCurve.DJ_BLEND.bothAudibleMs(LONG_MS);
        // The symmetric pair's share is the "feels short" defect written as a number:
        // gains within 6 dB of each other only where cos and sin are within a factor
        // of two, i.e. t in about [0.30, 0.70].
        assertTrue("the symmetric ramp should be both-audible for about two fifths of"
                        + " the overlap, was " + symmetric + "ms of " + LONG_MS + "ms",
                symmetric >= 5_800L && symmetric <= 6_500L);
        // The staged one holds both tracks for about two thirds of it, which is the
        // whole point: it is not a fade with a long overlap, it is a mix. (72% at the
        // round-12 exponents; 68% since round 16 gave the exit its own window, because a
        // longer hold and a longer exit cannot both be had and the exit was the complaint.)
        assertTrue("the DJ shape should be both-audible for a large share of the overlap,"
                        + " was " + dj + "ms of " + LONG_MS + "ms",
                dj >= 10_100L && dj <= 11_500L);
        assertTrue("the DJ shape should be at least half again the symmetric one",
                dj >= symmetric * 3L / 2L);
    }

    @Test
    public void theIncomingTrackArrivesEarlyAndTheOutgoingTrackHoldsAndThenLeaves() {
        // The incoming bed is there from the first seventh of the window: this is the
        // "comes in early and low" half, and it is what stops the incoming track from
        // being a mirror of the outgoing track's decay.
        assertTrue("the incoming bed should be up within the first 15%",
                FadeCurve.DJ_BLEND.inGain(0.15f) >= 0.40f);
        assertTrue("the incoming track should be clearly present by the middle",
                FadeCurve.DJ_BLEND.inGain(0.5f) >= 0.75f);
        // The outgoing track is still at its own level through the first half — that
        // is the "holds up longer" half, and it is the difference between a blend and
        // a fade that starts immediately.
        assertTrue("the outgoing track should still be within ~1.5dB at the middle",
                FadeCurve.DJ_BLEND.outGain(0.5f) >= 0.84f);
        // ... and its exit is a tail confined to the end rather than a decay across the whole
        // window, which is what the hold exponent buys. The level at three quarters is the
        // number to keep: the exit starts after it, so no shape change may make the outgoing
        // track leave earlier than this without saying so here.
        assertTrue("the outgoing track should still be within ~3dB at three quarters, was "
                        + db(FadeCurve.DJ_BLEND.outGain(0.75f)),
                FadeCurve.DJ_BLEND.outGain(0.75f) >= 0.70f);
        // The exit itself (round 16): the complaint it answers is 「上一首歌戛然而止」 — the
        // previous song cut off — and the shape that caused it held the outgoing track at
        // -2.7 dB at three quarters and -8.7 dB at nine tenths and then dropped it to
        // silence inside the last tenth: at its own level, then gone. The window changes
        // exactly that: from nine tenths on the outgoing track is already 9 dB further down
        // than it was (0.13 = -17.9 dB against 0.37 = -8.7 dB), by 95% it is inaudible
        // (-34.6 dB against -14.2 dB), and the last second of the blend is spent at a level
        // that is no longer part of the mix rather than carrying most of the drop.
        assertTrue("the outgoing track should be well down at nine tenths, was "
                        + db(FadeCurve.DJ_BLEND.outGain(0.9f)),
                FadeCurve.DJ_BLEND.outGain(0.9f) <= 0.15f);
        assertTrue("the outgoing track should be inaudible at 95%, was "
                        + db(FadeCurve.DJ_BLEND.outGain(0.95f)),
                FadeCurve.gainDb(FadeCurve.DJ_BLEND.outGain(0.95f)) <= -30f);
    }

    @Test
    public void theDjShapeEndsInAHeldZeroSoTheReleaseCannotBeHeardAsACut() {
        // A released player drops whatever is still buffered in it, so the gain at the
        // instant of the release IS the sound of the release: the promotion runs on the
        // first tick at t >= 1, up to one ramp tick after the ramp's end, and the outgoing
        // player must have been silent for a stretch before that instant and not merely at
        // it. The curve's own guarantee is the last OUT_SILENT_TAIL of the ramp held at
        // exactly zero, which is what this pins — with the discontinuity at its head on the
        // far side of the inaudible floor, i.e. the step into the held zero is silent too.
        assertTrue("the ramp's end must be silent", FadeCurve.DJ_BLEND.outGain(1f) == 0f);
        assertTrue("... and held there, not just reached",
                FadeCurve.DJ_BLEND.outGain(0.995f) == 0f && FadeCurve.DJ_BLEND.outGain(0.99f) == 0f);
        // Where the held zero starts, the window was already below the floor: 4.5 ms of a
        // 15 s ramp is spent between -60 dB and -infinity, which is not a fade any more.
        float atHead = FadeCurve.DJ_BLEND.outGain(0.9899f);
        assertTrue("the step into the held zero must itself be inaudible, was "
                        + db(atHead), FadeCurve.gainDb(atHead) <= FadeCurve.INAUDIBLE_DB);
        // No tick of the outgoing track's exit may end up louder than the one before it:
        // a tail that rose anywhere would be a level bump inside the fade.
        float previous = FadeCurve.DJ_BLEND.outGain(0.5f);
        for (int i = 501; i <= 1000; i++) {
            float gain = FadeCurve.DJ_BLEND.outGain(i / 1000f);
            assertTrue("the outgoing track's exit must not rise (t=" + (i / 1000f) + ": "
                            + previous + " -> " + gain + ")", gain <= previous + 1e-6f);
            previous = gain;
        }
        // And the level the promotion's assertion checks is the one the curve promises: the
        // floor is a real floor, not "anything below one".
        assertTrue("the inaudible floor must be an order of magnitude below anything audible",
                FadeCurve.INAUDIBLE_DB <= -60f);
        assertEquals(FadeCurve.INAUDIBLE_DB, (float) FadeCurve.gainDb(0f), 1e-4f);
        // The held stretch the backend prints on its release line is the curve's own
        // number, and it must be the truth about this shape: every sample inside it is
        // exactly zero, the sample just before it is already inaudible, and the shapes
        // that do not claim one do not have one.
        float tail = FadeCurve.DJ_BLEND.outSilentTail();
        assertTrue("the DJ shape holds a stretch of exactly zero at its end, was " + tail,
                tail > 0.005f && tail < 0.05f);
        for (int i = 0; i <= 100; i++) {
            float t = (1f - tail) + tail * i / 100f;
            assertEquals("t=" + t + " is inside the held zero", 0f, FadeCurve.DJ_BLEND.outGain(t), 0f);
        }
        assertTrue("the step into the held zero is inaudible, was "
                        + db(FadeCurve.DJ_BLEND.outGain(1f - tail - 0.001f)),
                FadeCurve.gainDb(FadeCurve.DJ_BLEND.outGain(1f - tail - 0.001f))
                        <= FadeCurve.INAUDIBLE_DB);
        assertEquals("the shapes that fade to the end promise no held stretch",
                0f, FadeCurve.EQUAL_POWER.outSilentTail(), 0f);
        assertEquals(0f, FadeCurve.LINEAR.outSilentTail(), 0f);
    }

    /** A gain as dB, for an assertion message that says how far off it was. */
    private static String db(float gain) {
        return String.format(java.util.Locale.US, "%.1f dB", FadeCurve.gainDb(gain));
    }

    @Test
    public void bothTracksAreAudibleAtOnceAcrossTheMiddleOfTheDjShape() {
        // Stated as the listener hears it rather than as a formula: at a fifth, a
        // half and four fifths of the window the two gains are within 6 dB.
        for (float t : new float[] {0.2f, 0.35f, 0.5f, 0.65f, 0.8f}) {
            float out = FadeCurve.DJ_BLEND.outGain(t);
            float in = FadeCurve.DJ_BLEND.inGain(t);
            float louder = Math.max(out, in);
            float quieter = Math.min(out, in);
            double db = 20d * Math.log10(louder / quieter);
            assertTrue("at t=" + t + " the two should be within 6dB (out=" + out + ", in="
                    + in + ", " + String.format(java.util.Locale.US, "%.1f", db) + "dB)",
                    db <= 6d + 1e-6);
        }
    }

    @Test
    public void everyCurveStartsSilentOnTheIncomingSideAndEndsOnIt() {
        for (FadeCurve curve : FadeCurve.values()) {
            assertTrue(curve + " should start with the incoming track silent",
                    curve.inGain(0f) <= 0.02f);
            assertTrue(curve + " should start with the outgoing track at full level",
                    curve.outGain(0f) >= 0.98f);
            assertTrue(curve + " should end with the incoming track at full level",
                    curve.inGain(1f) >= 0.98f);
            assertTrue(curve + " should end with the outgoing track silent",
                    curve.outGain(1f) <= 0.02f);
        }
    }

    @Test
    public void theSummedPowerNeverLiftsTheBlendMoreThanAboutTwoAndAHalfDb() {
        // Two tracks at once are louder than one, and that is the point — but a shape
        // that summed to, say, +6 dB would be heard as a volume jump rather than as a
        // mix, and would risk clipping on material that is already mastered loud. The
        // staged shape's bump is measured here so a future tweak to its exponents
        // cannot quietly make the blend louder.
        double worst = 0d;
        for (int i = 0; i <= 1000; i++) {
            float t = i / 1000f;
            double out = FadeCurve.DJ_BLEND.outGain(t);
            double in = FadeCurve.DJ_BLEND.inGain(t);
            worst = Math.max(worst, out * out + in * in);
        }
        double bumpDb = 10d * Math.log10(worst);
        assertTrue("the summed power should stay under +2.5dB, was "
                + String.format(java.util.Locale.US, "%.2f", bumpDb) + "dB", bumpDb <= 2.5d);
        assertTrue("... and it should actually sum to more than one track (both audible)",
                bumpDb >= 1.0d);
    }

    @Test
    public void aBlendLongEnoughToBeHeardAsAMixIsGivenTheDjShape() {
        TransitionPlan named = TransitionPlan.of(TransitionKind.CROSSFADE,
                TransitionPlan.OVERLAP_LONG_MS, null, "test");
        assertEquals(FadeCurve.DJ_BLEND, named.curveOr(FadeCurve.EQUAL_POWER));
        // A plan that named its own curve still wins, so the AI or the chooser can
        // overrule the shape.
        TransitionPlan explicit = TransitionPlan.of(TransitionKind.CROSSFADE,
                TransitionPlan.OVERLAP_LONG_MS, FadeCurve.EQUAL_POWER, "test");
        assertEquals(FadeCurve.EQUAL_POWER, explicit.curveOr(FadeCurve.DJ_BLEND));
        // A quick fade (one second) has no middle to hold, so the settings decide.
        TransitionPlan quick = TransitionPlan.of(TransitionKind.QUICK_FADE,
                TransitionKind.QUICK_FADE.overlapMs(), null, "test");
        assertEquals(FadeCurve.LINEAR, quick.curveOr(FadeCurve.LINEAR));
    }

    @Test
    public void aRampOfNothingHasNothingBothAudible() {
        assertEquals(0L, FadeCurve.DJ_BLEND.bothAudibleMs(0L));
        assertEquals(0L, FadeCurve.EQUAL_POWER.bothAudibleMs(-5L));
    }
}
