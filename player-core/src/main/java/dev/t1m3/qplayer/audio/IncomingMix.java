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
 *       sit in the outgoing track's harmony, at most {@link #MAX_SEMITONES} — and only
 *       ever when the blend can afford to have it back in the track's own key before
 *       its vocals are heard ({@link #pitchIdentityAtFileMs()}).</li>
 *   <li><b>The low-end hand-over</b> ({@link #bassSwapAtMs()}): the incoming track's
 *       bass stays cut until the moment the outgoing track gives it up, so the
 *       loudest and most correlated part of the two never doubles.</li>
 * </ol>
 *
 * <p>⚠️ <b>The transposition and the tempo are not the same kind of thing, and this
 * round treats them differently on purpose.</b> The tempo is eased back after the
 * promotion, at the last moment it can be — pulling it back any earlier would take the
 * incoming track off the outgoing track's grid <em>while both are still sounding</em>,
 * which is the one thing the lock exists for. The transposition is pulled back much
 * earlier, before the incoming track's vocals arrive, because a sung note is absolute:
 * a vocal eased down two semitones is heard as a broken singer in a way a backing that
 * is a semitone high (against another backing that agrees with it) is not.
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
    public static final IncomingMix IDENTITY = new IncomingMix(-1L, 1d, 0, -1L, null);

    /**
     * How long every ease-back in this app is performed over: the mix's tempo and pitch
     * going back to the incoming track's own after the promotion, and the pitch's own
     * scheduled return before that track's vocals arrive ({@link #pitchIdentityAtFileMs}).
     *
     * <p>Six seconds is slower than any beat, and by the time the promotion's runs the two
     * tracks are no longer both sounding, so the glide itself is inaudible — which is what
     * makes it an ease-back rather than a correction. It is a constant here rather than in
     * the backend because the <em>caller</em> needs it: a transposition is only applied at
     * all when the blend can afford this much room (see
     * {@code MixNaturaliser.pitchFitsBeforeVocals}), and a caller working from a different
     * number than the backend ramps by would be scheduling something that does not
     * happen. {@code AndroidAudioBackend.PROMOTION_RESTORE_MS} is this value.
     */
    public static final long RESTORE_MS = 6_000L;

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
        return ms < 0L ? IDENTITY : new IncomingMix(ms, 1d, 0, -1L, null);
    }

    /**
     * A blend's full instruction: where the low end hands over ({@code -1} for "no
     * hand-over"), the speed the incoming track runs at, and the semitones it is
     * transposed by (both "nothing" when the pair measured that way — see
     * {@link MixNaturaliser}). No scheduled pitch return: the transposition, if there is
     * one, is undone by the promotion's ease-back like the tempo.
     *
     * @param note what the naturaliser measured, for the boundary's log line; null
     *             when there is nothing to say (a mix built by hand, {@link #IDENTITY}).
     */
    public static IncomingMix of(long bassSwapAtMs, double speed, int semitones, String note) {
        return of(bassSwapAtMs, speed, semitones, -1L, note);
    }

    /**
     * The same, with the transposition scheduled back to the track's own pitch
     * <em>before</em> its vocals arrive — the user's rule, and the only form a
     * transposition takes now: a transposed vocal must never be heard, so a pair whose
     * blend cannot afford the return gets no transposition at all rather than a
     * compressed one (see {@code PlayerController}'s pitch rule and
     * {@link MixNaturaliser#pitchFitsBeforeVocals}).
     *
     * @param pitchIdentityAtFileMs the position in the incoming track's own file the
     *                              pitch must be its own at — its own file, not the ramp's
     *                              wall clock, because a player's position is the clock its
     *                              vocals live on (the ramp's start lateness is the
     *                              device's, not the music's). {@code -1} for "no
     *                              schedule: the promotion's ease-back undoes it".
     */
    public static IncomingMix of(long bassSwapAtMs, double speed, int semitones,
                                 long pitchIdentityAtFileMs, String note) {
        double s = speed > 0d ? speed : 1d;
        int n = Math.max(-MAX_SEMITONES, Math.min(MAX_SEMITONES, semitones));
        // A schedule with nothing to ease back is not a thing: it is only ever set for a
        // transposition that is really applied.
        long back = n != 0 ? pitchIdentityAtFileMs : -1L;
        if (bassSwapAtMs < 0L && s == 1d && n == 0) {
            return note == null ? IDENTITY : new IncomingMix(-1L, 1d, 0, -1L, note);
        }
        return new IncomingMix(bassSwapAtMs, s, n, back, note);
    }

    private final long bassSwapAtMs;
    private final double speed;
    private final int semitones;
    private final long pitchIdentityAtFileMs;
    private final String note;

    private IncomingMix(long bassSwapAtMs, double speed, int semitones,
                        long pitchIdentityAtFileMs, String note) {
        this.bassSwapAtMs = bassSwapAtMs;
        this.speed = speed;
        this.semitones = semitones;
        this.pitchIdentityAtFileMs = pitchIdentityAtFileMs;
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

    /**
     * The position in the incoming track's own file the pitch must be its own at
     * ({@link #RESTORE_MS} before it, the ease-back begins), or {@code -1} when the
     * transposition is undone by the promotion's ease-back like the tempo.
     *
     * <p>This is what makes the user's rule mechanical rather than aspirational: the
     * backend drives the return from the player's own {@code getCurrentPosition()}, so
     * what is compared against the incoming track's vocal entry is the same clock that
     * clock is measured in — the device's start latency cannot eat into the margin.
     */
    public long pitchIdentityAtFileMs() {
        return pitchIdentityAtFileMs;
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
        if (pitchIdentityAtFileMs >= 0L) {
            if (sb.length() > "IncomingMix{".length()) sb.append(", ");
            sb.append("pitchIdentityAt=").append(pitchIdentityAtFileMs).append("ms");
        }
        return sb.append('}').toString();
    }
}
