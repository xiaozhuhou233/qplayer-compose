package dev.t1m3.qplayer.audio;

import java.util.Locale;

/**
 * What the incoming player has to be configured with for a blend: the two things
 * that make two tracks sound like they belong together, and the moment the low end
 * hands over.
 *
 * <ol>
 *   <li><b>Tempo.</b> The rate the incoming track is played at so its beat lands
 *       where the outgoing track's beat is. Applied by the backend with a
 *       pitch-preserving time stretch ({@code MediaPlayer.setPlaybackParams}), on
 *       the incoming player only, and always small ({@link #MAX_SPEED_STEP}).</li>
 *   <li><b>Pitch.</b> The number of semitones the incoming track is transposed to
 *       sit in the outgoing track's harmony, at most {@link #MAX_SEMITONES}.</li>
 *   <li><b>The low-end hand-over</b> ({@link #bassSwapAtMs()}): the incoming track's
 *       bass stays cut until the moment the outgoing track gives it up, so the
 *       loudest and most correlated part of the two never doubles.</li>
 * </ol>
 *
 * <p>⚠️ <b>All of it is a naturaliser, never a precondition.</b> Nothing in this
 * type can refuse a blend, and neither can the {@link MixNaturaliser} that fills
 * it: a pair whose tempos are nowhere near each other, or whose keys share nothing,
 * is overlapped exactly as it would have been without any of this — with the ratio
 * left at 1.0 and the semitones at 0, and with the log naming which measurement
 * said so. The one thing that is never done is a large correction: at ±20% a
 * time-stretch drops the transients that make a beat sound like a beat, and a
 * transposition past two semitones is heard as "this record is playing in the wrong
 * key" (see {@link #MAX_SPEED_STEP}, {@link #MAX_SEMITONES}).
 *
 * <p>Immutable and tiny: it is built once per boundary, on the render pump.
 */
public final class IncomingMix {

    /** Nothing to do: no stretch, no transposition, no low-end hand-over. */
    public static final IncomingMix IDENTITY = new IncomingMix(-1L, 1d, 0, null);

    /**
     * The most the incoming track's tempo may be moved, as a fraction of its own.
     *
     * <p>Eight percent is where a DJ's own pitch fader usually stops being
     * transparent, and it is also where the platform's phase-vocoder time-stretch
     * (Sonic) stops being safe: the further a transient has to be smeared the less
     * of it survives, so past this the correction costs more in artefacts than the
     * alignment buys. Beyond it the pair is <em>not</em> stretched at all — and the
     * blend still happens, which is the whole point of calling this a naturaliser.
     */
    public static final double MAX_SPEED_STEP = 0.08d;

    /**
     * The most the incoming track's key may be moved, semitones, in either
     * direction.
     *
     * <p>Two semitones is the whole distance over which a pitch shift stays
     * inaudible as an effect: further than that and a listener who knows the track
     * hears it transposed, so the record becomes the artefact. It is also enough to
     * reach every key a DJ would harmonically mix with (same key, relative
     * major/minor, both fifths), which is all this is for.
     */
    public static final int MAX_SEMITONES = 2;

    /** Nothing to do: no low-end hand-over. */
    public static IncomingMix bassSwapAt(long ms) {
        return ms < 0L ? IDENTITY : new IncomingMix(ms, 1d, 0, null);
    }

    /**
     * A blend's full instruction: where the low end hands over ({@code -1} for "no
     * hand-over"), the speed the incoming track runs at, and the semitones it is
     * transposed by (both "nothing" when the pair measured that way — see
     * {@link MixNaturaliser}).
     *
     * @param note what the naturaliser measured, for the boundary's log line; null
     *             when there is nothing to say (a mix built by hand, {@link #IDENTITY}).
     */
    public static IncomingMix of(long bassSwapAtMs, double speed, int semitones, String note) {
        double s = speed > 0d ? speed : 1d;
        int n = Math.max(-MAX_SEMITONES, Math.min(MAX_SEMITONES, semitones));
        if (bassSwapAtMs < 0L && s == 1d && n == 0) {
            return note == null ? IDENTITY : new IncomingMix(-1L, 1d, 0, note);
        }
        return new IncomingMix(bassSwapAtMs, s, n, note);
    }

    private final long bassSwapAtMs;
    private final double speed;
    private final int semitones;
    private final String note;

    private IncomingMix(long bassSwapAtMs, double speed, int semitones, String note) {
        this.bassSwapAtMs = bassSwapAtMs;
        this.speed = speed;
        this.semitones = semitones;
        this.note = note;
    }

    /** Whether this asks for nothing at all. */
    public boolean isIdentity() {
        return bassSwapAtMs < 0L && speed == 1d && semitones == 0;
    }

    /** When, ms into the overlap, the low end hands over from the outgoing track to
     *  the incoming one; -1 for no hand-over. */
    public long bassSwapAtMs() {
        return bassSwapAtMs;
    }

    /** The speed the incoming track is played at (1.0 = its own). */
    public double speed() {
        return speed;
    }

    /** The key shift applied to the incoming track, semitones (0 = none). */
    public int semitones() {
        return semitones;
    }

    /** The pitch ratio {@link #semitones()} means on the platform's own scale —
     *  what {@code setPlaybackParams} takes. */
    public double pitch() {
        return Math.pow(2d, semitones / 12d);
    }

    /** Whether the incoming track is played at a different tempo (which is also what
     *  turns the ramp's wall-clock length into that track's own milliseconds — see
     *  {@code PlayerController}'s handoff). */
    public boolean hasTempo() {
        return speed != 1d;
    }

    /** What the naturaliser measured, for the log; null when there is nothing. */
    public String note() {
        return note;
    }

    /**
     * Whether the backend really applied this mix's tempo and pitch. Used on the
     * read-back after the prepare: a platform that ignored the parameters leaves a
     * plan that assumed a stretched track describing a track that is not stretched,
     * and the handoff arithmetic for that boundary would then be off by the speed
     * factor.
     */
    public boolean sameTempoAndPitch(IncomingMix other) {
        if (other == null) return false;
        return Math.abs(other.speed - speed) < 1e-4d && other.semitones == semitones;
    }

    @Override
    public String toString() {
        if (isIdentity()) return "IncomingMix{none}";
        StringBuilder sb = new StringBuilder("IncomingMix{");
        if (hasTempo()) sb.append(String.format(Locale.US, "x%.4f", speed));
        if (semitones != 0) {
            if (sb.length() > "IncomingMix{".length()) sb.append(", ");
            sb.append(String.format(Locale.US, "%+d semitone%s", semitones,
                    Math.abs(semitones) == 1 ? "" : "s"));
        }
        if (bassSwapAtMs >= 0L) {
            if (sb.length() > "IncomingMix{".length()) sb.append(", ");
            sb.append("bassSwap=").append(bassSwapAtMs).append("ms");
        }
        return sb.append('}').toString();
    }
}
