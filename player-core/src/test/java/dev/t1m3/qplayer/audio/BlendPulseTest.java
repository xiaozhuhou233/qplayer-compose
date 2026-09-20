package dev.t1m3.qplayer.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The beat-continuity instrument against material whose answer is known.
 *
 * <p>Everything here is synthetic on purpose, and every claim is one the instrument can
 * actually support: a longer hold must leave the outgoing track higher, holding it there must
 * cost power, the hand-over's instant must be reported exactly, the incoming track's
 * establishment must be ordered against it, and a low end that is handed to a deck which is
 * not there must show as a hole. What the instrument deliberately does <em>not</em> claim to
 * see is the beat itself — see the class note for the three attempts that failed on real
 * material.
 */
public class BlendPulseTest {

    private static final int RATE = 11_025;
    /** 120 BPM: half a second a beat, which is this library's middle. */
    private static final double PERIOD_MS = 500d;
    private static final double PERIOD_SEC = PERIOD_MS / 1000d;
    private static final long RAMP_MS = 15_000L;
    /** Where the kicks sit in the material, seconds. */
    private static final double PHASE_SEC = 0.25d;

    /** A kick track: one decaying low sine per beat, plus a click. */
    private static float[] kicks(double seconds, double phaseSec, double hz) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        int period = Math.max(1, (int) (PERIOD_SEC * RATE));
        int phase = (int) (phaseSec * RATE);
        for (int beat = phase; beat < n; beat += period) {
            int len = Math.min(n - beat, (int) (0.12 * RATE));
            for (int i = 0; i < len; i++) {
                double t = i / (double) RATE;
                double env = Math.exp(-t / 0.025);
                double body = Math.sin(2 * Math.PI * hz * t);
                double click = i == 0 ? 0.5 : (i == 1 ? -0.2 : 0);
                out[beat + i] += (float) (0.9 * env * body + click);
            }
        }
        return out;
    }

    /** A continuous bed with no attacks at all: a noise wash under a slow swell. */
    private static float[] bed(double seconds) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        java.util.Random rnd = new java.util.Random(11);
        double y = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            y += 0.35 * (rnd.nextDouble() * 2 - 1);
            y *= 0.72;
            out[i] = (float) (y * 0.55 * (1d + 0.20 * Math.sin(2 * Math.PI * 0.06 * t)));
        }
        return out;
    }

    /** The ordinary incoming track: the same bed with a kick of its own on the same grid, and
     *  the same 60 Hz body as the outgoing track's so that a hole would mean the arrangement
     *  holed rather than that the two tracks' bass levels differ. */
    private static float[] bedWithKicks(double seconds) {
        float[] out = bed(seconds);
        float[] k = kicks(seconds, PHASE_SEC, 60d);
        for (int i = 0; i < out.length; i++) out[i] += k[i];
        return out;
    }

    private static float[] tail(float[] full) {
        return java.util.Arrays.copyOfRange(full, full.length - (int) (RAMP_MS * RATE / 1000L),
                full.length);
    }

    private static float[] head(float[] full) {
        return java.util.Arrays.copyOf(full, (int) (RAMP_MS * RATE / 1000L));
    }

    @Test
    public void aLongerHoldKeepsTheOutgoingTrackUpLongerAndCostsPower() {
        float[] a = tail(kicks(20d, PHASE_SEC, 60d));
        float[] b = head(bedWithKicks(20d));
        long swap = 7_500L;
        // The shipped curve is 2.6; the comparison runs a shorter hold (what round 11
        // shipped) against it and against a longer one, so the direction is pinned from both
        // sides rather than against a copy of the shipped exponents.
        BlendPulse.Result shortHold = BlendPulse.measure(a, b, RATE, RAMP_MS,
                1.8d, 0.55d, swap, PERIOD_MS);
        BlendPulse.Result shipped = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, swap, PERIOD_MS);
        BlendPulse.Result longer = BlendPulse.measure(a, b, RATE, RAMP_MS,
                3.2d, 0.55d, swap, PERIOD_MS);
        System.out.println("hold 1.80  (round 11): " + shortHold.describe());
        System.out.println("DJ_BLEND   (2.60): " + shipped.describe());
        System.out.println("hold 3.20: " + longer.describe());
        assertTrue("a longer hold must leave the outgoing track higher at three quarters: "
                        + shortHold.outgoingAt75PctDb + " -> " + shipped.outgoingAt75PctDb,
                shipped.outgoingAt75PctDb > shortHold.outgoingAt75PctDb);
        assertTrue("and longer still must not go backwards: " + shipped.outgoingAt75PctDb
                        + " -> " + longer.outgoingAt75PctDb,
                longer.outgoingAt75PctDb >= shipped.outgoingAt75PctDb);
        assertTrue("holding the outgoing track up longer must show up as more summed power: "
                        + shortHold.maxPowerDb + " -> " + longer.maxPowerDb,
                longer.maxPowerDb > shortHold.maxPowerDb);
    }

    @Test
    public void theHandOverIsReportedAsTheExactMomentTheOutgoingKickGoes() {
        float[] a = tail(kicks(20d, PHASE_SEC, 60d));
        float[] b = head(bedWithKicks(20d));
        BlendPulse.Result swapped = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, 7_500L, PERIOD_MS);
        BlendPulse.Result late = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, 9_000L, PERIOD_MS);
        BlendPulse.Result kept = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, -1L, PERIOD_MS);
        System.out.println("hand-over at 7500ms: " + swapped.describe());
        System.out.println("hand-over at 9000ms: " + late.describe());
        System.out.println("no hand-over:        " + kept.describe());
        assertEquals("the hand-over's own instant is reported exactly", 0.5d,
                swapped.outgoingKickEndsAt, 1e-9d);
        assertEquals("a later hand-over hands over later", 0.6d, late.outgoingKickEndsAt, 1e-9d);
        assertEquals("and no hand-over means the outgoing track keeps its low end",
                1d, kept.outgoingKickEndsAt, 1e-9d);
        assertTrue("the incoming track must be established before the hand-over: "
                        + swapped.incomingEstablishedAt + " vs " + swapped.outgoingKickEndsAt,
                swapped.incomingEstablishedAt <= swapped.outgoingKickEndsAt);
        assertTrue("even with a hand-over as late as three fifths of the ramp",
                late.incomingEstablishedAt <= late.outgoingKickEndsAt);
    }

    @Test
    public void anEmptyIncomingLowEndShowsAsAHoleInTheSum() {
        // The failure the ordering rule is about, made visible: the incoming track's low end
        // is far quieter than the outgoing track's, so the moment the hand-over takes the
        // outgoing track's away the summed low band drops — that is the hole.
        float[] a = tail(kicks(20d, PHASE_SEC, 60d));
        float[] b = head(bed(20d));
        for (int i = 0; i < b.length; i++) {
            b[i] *= 0.05f;
        }
        BlendPulse.Result r = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, 7_500L, PERIOD_MS);
        System.out.println("thin incoming low end: " + r.describe());
        assertTrue("the low end must be reported as holing: " + r.lowHoleDb + "dB",
                r.lowHoleDb < -6d);
    }

    @Test
    public void aMatchedHandOverLeavesNoHole() {
        // The same arrangement with the incoming track carrying its own kick at the outgoing
        // track's level: the low end changes hands without the bottom dropping.
        float[] a = tail(kicks(20d, PHASE_SEC, 60d));
        float[] b = head(bedWithKicks(20d));
        BlendPulse.Result r = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, 7_500L, PERIOD_MS);
        System.out.println("matched hand-over: " + r.describe());
        assertTrue("the low end must not be reported as holing: " + r.lowHoleDb + "dB",
                r.lowHoleDb > -1d);
        assertTrue("and its last slice must be at the window's own peak",
                Math.abs(r.lowLevelDb[r.lowLevelDb.length - 1]) < 1d);
    }

    @Test
    public void theStagedShapeLeavesTheOutgoingTrackHigherThanTheSymmetricOne() {
        float[] a = tail(kicks(20d, PHASE_SEC, 60d));
        float[] b = head(bedWithKicks(20d));
        BlendPulse.Result symmetric = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.EQUAL_POWER, 7_500L, PERIOD_MS);
        BlendPulse.Result staged = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, 7_500L, PERIOD_MS);
        System.out.println("equal power: " + symmetric.describe());
        System.out.println("dj blend:    " + staged.describe());
        assertTrue("the staged shape is the one that keeps the outgoing track up: "
                        + symmetric.outgoingAt75PctDb + " -> " + staged.outgoingAt75PctDb,
                staged.outgoingAt75PctDb > symmetric.outgoingAt75PctDb);
        assertTrue("and it holds both tracks audible for longer, which is the arithmetic"
                        + " number the curve owns: "
                        + FadeCurve.EQUAL_POWER.bothAudibleText(RAMP_MS)
                        + " vs " + FadeCurve.DJ_BLEND.bothAudibleText(RAMP_MS),
                FadeCurve.DJ_BLEND.bothAudibleMs(RAMP_MS)
                        > FadeCurve.EQUAL_POWER.bothAudibleMs(RAMP_MS));
    }

    @Test
    public void withoutAGridOnlyTheLevelsAreReported() {
        float[] a = tail(kicks(20d, PHASE_SEC, 60d));
        float[] b = head(bedWithKicks(20d));
        BlendPulse.Result r = BlendPulse.measure(a, b, RATE, RAMP_MS,
                FadeCurve.DJ_BLEND, 7_500L);
        System.out.println("no grid: " + r.describe());
        assertTrue("the result says it had no grid", !r.hasGrid);
        assertTrue("but the levels are still there", r.lowLevelDb.length > 0);
    }
}
