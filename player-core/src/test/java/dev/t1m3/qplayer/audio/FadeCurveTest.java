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
 * so — about 66% of the window against the symmetric pair's 41%, for about 2 dB of
 * extra summed power in the middle.
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
        // The staged one holds both tracks for about three quarters of it, which is the
        // whole point: it is not a fade with a long overlap, it is a mix. (72% at the
        // round-12 exponents, 67% before them.)
        assertTrue("the DJ shape should be both-audible for a large share of the overlap,"
                        + " was " + dj + "ms of " + LONG_MS + "ms",
                dj >= 10_500L && dj <= 11_500L);
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
        // ... and its exit is a short tail rather than a decay across the window. The hold
        // was lengthened in round 12 (the exponent 1.8 -> 2.6), which moved this boundary
        // later on purpose: at nine tenths of the ramp the outgoing track is now at 0.37
        // (-8.7 dB) where it used to be at 0.27 (-11.4 dB), and the exit is the last
        // twelfth instead of the last tenth. The pair of assertions is what pins that: the
        // track is still plainly there at 0.9 and gone by 0.98.
        assertTrue("the outgoing track should be on its way out at nine tenths, was "
                        + FadeCurve.DJ_BLEND.outGain(0.9f),
                FadeCurve.DJ_BLEND.outGain(0.9f) <= 0.40f);
        assertTrue("the outgoing track should be nearly silent right before the end, was "
                        + FadeCurve.DJ_BLEND.outGain(0.98f),
                FadeCurve.DJ_BLEND.outGain(0.98f) <= 0.12f);
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
