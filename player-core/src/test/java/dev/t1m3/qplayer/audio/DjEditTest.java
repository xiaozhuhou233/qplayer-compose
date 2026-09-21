package dev.t1m3.qplayer.audio;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The DJ edit ({@link DjEdit}) as arithmetic: the vocal-return shape, the "does this
 * window sing at all" verdict, and the render itself — all as numbers, printed.
 *
 * <p>Pure Java, no model and no device: the stems are hand-built, so a failure here is a
 * statement about the decision rather than about htdemucs. What the separation does to
 * real music is measured on the device instead (see AI_HANDOFF §7), and the two halves
 * are deliberately separate — this file is the part that must hold whatever the model
 * returns.
 */
public class DjEditTest {

    private static final int RATE = 44_100;

    private static void println(String format, Object... args) {
        System.out.println(String.format(Locale.US, format, args));
    }

    /** A sine at {@code amplitude}, both channels, {@code seconds} long. */
    private static float[] tone(double seconds, double amplitude, double hz) {
        int n = (int) Math.round(seconds * RATE);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (float) (amplitude * Math.sin(2 * Math.PI * hz * i / RATE));
        }
        return out;
    }

    private static float[] zeros(double seconds) {
        return new float[(int) Math.round(seconds * RATE)];
    }

    /** Four stems of the same length: [drums, bass, other, vocals]. */
    private static float[][][] stems(float[] drums, float[] bass, float[] other, float[] vocals) {
        return new float[][][]{
                {drums, drums}, {bass, bass}, {other, other}, {vocals, vocals}};
    }

    // --- when the vocals come back -------------------------------------------

    @Test
    public void theReturnLandsOnTheBarLineAfterTheBlend() {
        // 15s of backing only, and the incoming track's next bar line is 1.2s later.
        DjEdit.Plan plan = DjEdit.plan(15.0, 16.2);
        println("plan: %s", plan.describe());
        assertEquals(15.7, plan.returnStartSec, 1e-9);
        assertEquals(16.2, plan.returnEndSec, 1e-9);
        assertTrue(plan.onBarLine);
        assertEquals(0.0, plan.vocalGainAt(0), 1e-9);
        assertEquals(0.0, plan.vocalGainAt(14.0), 1e-9);
        assertEquals(0.0, plan.vocalGainAt(15.7), 1e-9);
        assertEquals(1.0, plan.vocalGainAt(16.2), 1e-9);
        assertEquals(1.0, plan.vocalGainAt(300.0), 1e-9);
        // The ramp is a rise, never a step: every sample in it is between the two ends.
        double previous = -1;
        for (double t = 15.7 + 0.001; t < 16.2; t += 0.01) {
            double g = plan.vocalGainAt(t);
            assertTrue("gain " + g + " at " + t, g >= previous && g > 0 && g < 1);
            previous = g;
        }
        println("the ramp spans %.2fs..%.2fs and is monotone inside it",
                plan.returnStartSec, plan.returnEndSec);
    }

    @Test
    public void aReturnInsideTheBlendIsNotUsedAndTheMarginMovesItPastTheEnd() {
        // ⚠️ Round 17 changed this from "the window itself is the floor" to "the window plus
        // the margin is": a return ON the blend's end still puts the last half second of the
        // lift inside the blend, which is the "人声混合得很乱" the user reported. So the floor is
        // `blend + RETURN_RAMP` (one ramp, so the lift starts at the first sample after the
        // blend) plus another half second of the backing alone — 1.0s for the shipped ramp.
        DjEdit.Plan plan = DjEdit.plan(15.0, 9.0);
        println("plan (bar line before the window): %s", plan.describe());
        assertEquals(15.0 + DjEdit.VOCAL_RETURN_MARGIN_SEC, plan.returnEndSec, 1e-9);
        assertFalse(plan.onBarLine);
        // The whole blend is at exactly zero, and so is the margin after it.
        assertEquals(0.0, plan.vocalGainAt(14.5), 1e-9);
        assertEquals(0.0, plan.vocalGainAt(15.0), 1e-9);
        assertEquals(0.0, plan.vocalGainAt(plan.returnStartSec), 1e-9);
        assertTrue("the lift must start after the blend ends, at " + plan.returnStartSec,
                plan.returnStartSec >= 15.0);
        assertEquals(1.0, plan.vocalGainAt(plan.returnEndSec), 1e-9);
    }

    @Test
    public void noGridMeansTheReturnIsTheWindowPlusTheMargin() {
        DjEdit.Plan plan = DjEdit.plan(12.0, Double.NaN);
        println("plan (no grid): %s", plan.describe());
        assertFalse(plan.onBarLine);
        assertEquals(12.0 + DjEdit.VOCAL_RETURN_MARGIN_SEC, plan.returnEndSec, 1e-9);
        assertEquals(plan.returnEndSec - DjEdit.RETURN_RAMP_SEC, plan.returnStartSec, 1e-9);
        // The rule is the invariant, however short the window is: the voice is at exactly zero
        // for the whole blend (and for the margin after it).
        for (double blend : new double[] {0.2d, 1.0d, 4.0d, 15.0d, 30.0d}) {
            DjEdit.Plan p = DjEdit.plan(blend, Double.NaN);
            assertEquals("a " + blend + "s blend must start the lift after the blend",
                    blend + DjEdit.VOCAL_RETURN_MARGIN_SEC - DjEdit.RETURN_RAMP_SEC,
                    p.returnStartSec, 1e-9);
            assertTrue("the lift may not begin inside the blend (" + blend + "s): "
                            + p.returnStartSec, p.returnStartSec >= blend);
            assertEquals("and the whole blend is silent in the vocal stem", 0.0,
                    p.vocalGainAt(blend), 1e-9);
        }
    }

    @Test
    public void theFirstBarLineAtOrAfterTheWindowWins() {
        double[] bars = {0.0, 2.0, 4.0, 6.0};
        assertEquals(4.0, DjEdit.firstBarAtOrAfter(bars, 3.5), 1e-9);
        assertEquals(4.0, DjEdit.firstBarAtOrAfter(bars, 4.0), 1e-9);
        assertTrue(Double.isNaN(DjEdit.firstBarAtOrAfter(bars, 6.1)));
        assertTrue(Double.isNaN(DjEdit.firstBarAtOrAfter(new double[0], 0.0)));
        assertTrue(Double.isNaN(DjEdit.firstBarAtOrAfter(null, 0.0)));
        println("bar lines %s: first at or after 3.5s is 4.0s; after 6.0s there is none",
                java.util.Arrays.toString(bars));
    }

    // --- does the window sing? ----------------------------------------------

    @Test
    public void anInstrumentalHeadIsMeasuredAsNotSinging() {
        // htdemucs's vocal stem on an intro is near-digital-silence: -90 dBFS noise.
        float[] quiet = tone(15.0, 1e-4, 220);
        DjEdit.Presence p = DjEdit.presence(new float[][]{quiet, quiet}, RATE, 15.0);
        println("instrumental head: %s -> sings=%s", p.describe(), p.sings);
        assertFalse(p.sings);
        assertTrue("p90 " + p.p90Db, p.p90Db < DjEdit.NO_VOCALS_P90_DBFS);
        assertEquals(1.0, p.silentShare, 1e-9);
    }

    @Test
    public void aSustainedWordCountsAsSingingAndAStrayOneDoesNot() {
        // The verdict is "how much of this window has voice in it". A sung phrase is
        // tens of percent of the frames; a blip of a few percent is treated as nothing,
        // which is the honest reading of "these regions have no vocals" — and being wrong
        // that way only leaves the incoming's own vocals in place, which is today's blend.
        float[] sustained = zeros(15.0);
        float[] phrase = tone(1.5, 0.25, 300);
        System.arraycopy(phrase, 0, sustained, (int) (13.0 * RATE), phrase.length);
        DjEdit.Presence sung = DjEdit.presence(new float[][]{sustained, sustained}, RATE, 15.0);
        println("1.5s of voice of 15s: %s -> sings=%s", sung.describe(), sung.sings);
        assertTrue(sung.sings);

        float[] blip = zeros(15.0);
        float[] word = tone(0.2, 0.25, 300);
        System.arraycopy(word, 0, blip, (int) (14.0 * RATE), word.length);
        DjEdit.Presence stray = DjEdit.presence(new float[][]{blip, blip}, RATE, 15.0);
        println("0.2s of voice of 15s: %s -> sings=%s", stray.describe(), stray.sings);
        assertFalse(stray.sings);
    }

    @Test
    public void aSungWindowIsSeveralTensOfDbAboveTheBar() {
        float[] sung = tone(15.0, 0.2, 220);
        DjEdit.Presence p = DjEdit.presence(new float[][]{sung, sung}, RATE, 15.0);
        println("sung head: %s -> sings=%s", p.describe(), p.sings);
        assertTrue(p.sings);
        assertTrue("p90 " + p.p90Db, p.p90Db > -20);
    }

    @Test
    public void anEmptyWindowClaimsNothing() {
        DjEdit.Presence p = DjEdit.presence(new float[2][0], RATE, 15.0);
        println("empty window: %s -> sings=%s", p.describe(), p.sings);
        assertFalse(p.sings);
        assertEquals(0, p.frames);
    }

    // --- the render ---------------------------------------------------------

    @Test
    public void theBlendWindowIsBackingOnlyAndTheTailIsTheMaster() {
        // Amplitudes chosen to sum below full scale, so the comparison is about the
        // vocal curve and not about the clamp (which has its own test below).
        float[] drums = tone(20.0, 0.10, 90);
        float[] bass = tone(20.0, 0.15, 60);
        float[] other = tone(20.0, 0.08, 440);
        float[] vocals = tone(20.0, 0.20, 700);
        float[][][] s = stems(drums, bass, other, vocals);
        DjEdit.Plan plan = DjEdit.plan(15.0, 15.0);
        int[] clipped = new int[1];
        float[][] head = DjEdit.renderHead(s, RATE, 20.0, plan, clipped);

        // Inside the removal window the render is the backing sum, exactly: the vocal
        // stem contributes nothing, so the difference is bit-zero. (The window checked
        // is up to the START of the return ramp, which is the last half second of it.)
        double worst = 0;
        for (int i = 0; i < (int) (plan.returnStartSec * RATE); i++) {
            double backing = drums[i] + bass[i] + other[i];
            worst = Math.max(worst, Math.abs(head[0][i] - backing));
        }
        // Float rounding of the same sum, nothing else.
        assertEquals(0.0, worst, 1e-6);
        // After the return the render is the master, exactly.
        double worstTail = 0;
        for (int i = 16 * RATE; i < 20 * RATE; i++) {
            double master = drums[i] + bass[i] + other[i] + vocals[i];
            worstTail = Math.max(worstTail, Math.abs(head[0][i] - master));
        }
        assertEquals(0.0, worstTail, 1e-6);
        println("render: the first 15.00s differ from the backing sum by %.1e (bit-exact), "
                        + "16.00-20.00s from the master by %.1e; %d samples clamped",
                worst, worstTail, clipped[0]);
        assertEquals(0, clipped[0]);
    }

    @Test
    public void theVocalLevelUnderTheCurveIsMeasurableAndTheSuppressionIsTotal() {
        float[] vocals = tone(20.0, 0.5, 700);
        DjEdit.Plan plan = DjEdit.plan(15.0, 15.4);
        float[][] before = new float[][]{vocals, vocals};
        float[][] after = DjEdit.scaleVocals(before, RATE, plan, 20.0);
        // The three parts the claim is about, measured separately: the window before the
        // ramp starts, the tail after the return ends, and the voice as it was.
        DjEdit.Presence original = DjEdit.presence(
                DjEdit.slice(before, RATE, 0, plan.returnStartSec), RATE, plan.returnStartSec);
        DjEdit.Presence removed = DjEdit.presence(
                DjEdit.slice(after, RATE, 0, plan.returnStartSec), RATE, plan.returnStartSec);
        DjEdit.Presence returned = DjEdit.presence(
                DjEdit.slice(after, RATE, plan.returnEndSec, 20.0), RATE,
                20.0 - plan.returnEndSec);
        println("voice: original    %s", original.describe());
        println("voice: under the edit curve %s", removed.describe());
        println("voice: after the return   %s", returned.describe());
        assertTrue(original.sings);
        assertFalse("the removal window must measure as no vocals", removed.sings);
        assertTrue("the tail must measure as vocals back", returned.sings);
        assertEquals(original.medianDb, returned.medianDb, 0.01);
        double suppression = original.medianDb - removed.medianDb;
        println("suppression inside the blend: %.0f dB below the same voice at unity",
                suppression);
        assertTrue(suppression > 60);
    }

    @Test
    public void aClippingMasterIsCountedRatherThanHidden() {
        // Three stems that sum past full scale: the edit is the master and the master is
        // over, so the only honest answer is the clamp plus the count.
        float[] loud = tone(2.0, 0.8, 100);
        float[][][] s = stems(loud, loud, loud, zeros(2.0));
        int[] clipped = new int[1];
        float[][] head = DjEdit.renderHead(s, RATE, 2.0, DjEdit.plan(1.0, 1.0), clipped);
        println("%d samples of the 2s window were past full scale and clamped", clipped[0]);
        assertTrue(clipped[0] > 0);
        for (float v : head[0]) assertTrue(Math.abs(v) <= 1.0f);
    }
}
