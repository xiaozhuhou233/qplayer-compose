package dev.t1m3.qplayer.audio;

/**
 * The gain shape an overlapping transition ramps along. Two tracks summed at
 * full gain are not a crossfade — they are just loud; the curve is what turns
 * one level going down and another going up into a seam that does not pump.
 *
 * <p>{@code t} is the progress of the overlap, 0 at its start and 1 at the swap
 * point. The two gains are applied to the outgoing and the incoming player
 * respectively; they are not required to sum to 1 (that is the point).
 */
public enum FadeCurve {

    /**
     * Straight lines: {@code 1-t} and {@code t}. What P1 shipped. Two
     * uncorrelated tracks each at half gain produce about half the power of one
     * at full gain, so the middle of the overlap dips — for equal-power content
     * that is 3 dB, which is the first thing to listen for when a long crossfade
     * "drops out" in the middle.
     */
    LINEAR("线性") {
        @Override public float outGain(float t) { return 1f - t; }
        @Override public float inGain(float t) { return t; }
    },

    /**
     * Quarter-circle pair: {@code cos} down, {@code sin} up. Their squares sum to
     * one, so the total power is constant across the whole overlap — the standard
     * "equal power" crossfade, which sounds level where LINEAR sounds like a dip.
     * The cost is that the two tracks briefly sum above either one alone, which is
     * exactly what a crossfade is supposed to do.
     */
    EQUAL_POWER("等功率") {
        @Override public float outGain(float t) { return (float) Math.cos(t * Math.PI / 2.0); }
        @Override public float inGain(float t) { return (float) Math.sin(t * Math.PI / 2.0); }
    },

    /**
     * The staged, DJ-shaped hand-over: the incoming track arrives <em>early and
     * low</em> (a bed under the outgoing one, not a mirror of its decay), the
     * outgoing track <em>holds</em> near its own level for most of the window and
     * then leaves over a short tail at the end instead of decaying across the whole
     * of it. This is the shape that makes a long overlap read as a mix rather than
     * as a fade.
     *
     * <p><b>Why the symmetric curves read as a fade.</b> With {@code cos}/{@code sin}
     * the two levels are only comparable while {@code t} is near the middle: the
     * gains cross at half a period and the stretch where the listener can hear
     * <em>two</em> tracks is about 41% of the window (measured by
     * {@link #bothAudibleMs(long, float)}), split evenly on both sides of the
     * middle. A fifteen-second ramp therefore spends the first third fading one
     * track up and the last third fading the other one out, which the ear reads as
     * "the song changed" — the complaint this curve answers.
     *
     * <p>The two sides are deliberately not the same function of the same exponent.
     * The outgoing one is the equal-power cosine of {@code t^2.6} <em>multiplied by a
     * release window</em> (see {@link #OUT_RELEASE_FRACTION}): flat for most of the
     * window (within 1 dB until {@code t}=0.6, -2.7 dB at three quarters), then
     * leaving over an explicit exit instead of over its exponent's own tail. The
     * incoming one is the sine of {@code t^0.55}: it reaches half its level within
     * the first seventh of the overlap and is a real bed from there on, and it keeps
     * the last stretch to itself as the outgoing track goes. The pair therefore sits
     * within 6 dB of each other for about two thirds of the overlap — <em>both tracks
     * audible at once</em>, which is what "sounds like a mix" means — at the cost of
     * summing about 2.3 dB above a single track in the middle (two tracks at 0.9 each
     * have 1.6 of the power of one, so a correlated pair can peak higher; the low-end
     * hand-over is what keeps the loudest, most correlated part — the bass — from
     * ever doubling).
     *
     * <p>The exponents are the whole tuning: raising the outgoing's makes its hold
     * longer and its exit shorter, lowering the incoming's makes its bed arrive
     * earlier. They were chosen so that the both-audible share is about 72% against
     * the symmetric pair's 41% while the mid-overlap power bump stays near 2.3 dB
     * ({@code FadeCurveTest} measures both, and the class's own doc carries the
     * measurement each exponent was picked from).
     */
    DJ_BLEND("DJ 式") {
        /** How much of the outgoing track's ramp is spent holding: the cosine is taken of
         *  {@code t} raised to this, so a bigger exponent means a longer hold — and, before
         *  {@link #OUT_RELEASE_FRACTION} existed, a shorter, later exit as well.
         *
         *  <p><b>2.6 since round 12</b> (it was 1.8), because the complaint about the long
         *  blend was still "it fades": the outgoing track's rhythm was leaving while the
         *  incoming track's was only arriving. At three quarters of the ramp the outgoing
         *  track is 1.8 dB louder than it used to be and at nine tenths it is 2.7 dB
         *  louder, for 0.3 dB more summed power in the middle and a both-audible share of
         *  72% against 67%.
         *
         *  <p><b>The exponent no longer shapes the exit.</b> Round 16 gave the exit its own
         *  shape ({@link #OUT_RELEASE_FRACTION}) after the growth of this number turned the
         *  hold into a cliff: the outgoing track sits within 3 dB of its own level until
         *  three quarters of the ramp and then carries the whole 8.7 dB to silence in the
         *  last tenth, which the user heard as 「上一首歌戛然而止」 — the previous song cut
         *  off. The two jobs are now separate on purpose: this exponent says how long the
         *  outgoing track holds, the window below says how it leaves. The hold is unchanged
         *  at 2.6 — it is the shape the earlier round tuned and nothing about it was wrong. */
        private static final double OUT_HOLD_EXPONENT = 2.6d;

        /**
         * The release window: the last quarter of the ramp is where the outgoing track
         * leaves, whatever the hold exponent is doing.
         *
         * <p>Until {@code t = 1 - OUT_RELEASE_FRACTION} the gain is the hold shape alone;
         * from there it is the hold shape multiplied by a raised cosine, which reaches
         * exactly zero at {@code t}=1 <em>with zero slope</em> — the level falls away
         * smoothly instead of plunging. The previous form (the hold shape's own tail, no
         * window) left the outgoing at -2.7 dB three quarters of the way through and at
         * -8.7 dB at nine tenths: held at its own level and then gone inside a second and a
         * half, which is the "the song stopped" the window answers.
         *
         * <p>Measured over a 15 s ramp ({@code TailTable} in the round-16 harness — the
         * same arithmetic, on this curve's own incoming exponent):
         *
         * <pre>
         * shape               out@75   out@85   out@90   out@95   out@97   both6dB         peak
         * no window (r12–15)   -2.7dB   -5.8dB   -8.7dB  -14.2dB  -18.5dB  10808ms (72%)   2.34dB
         * window 25%           -2.7dB   -9.4dB  -17.9dB  -34.6dB  -47.6dB  10226ms (68%)   2.34dB
         * </pre>
         *
         * <p>The hold is identical; everything the window changes is in the exit, where the
         * outgoing track is now established 5.7 dB lower at nine tenths and inaudible by
         * 95% instead of still being at -14 dB. What it costs is 582 ms of the
         * both-audible window (the 6 dB line is reached earlier because the outgoing track
         * is allowed to fall sooner) — 68% of the ramp against 72%, both far above the
         * 41% a symmetric ramp gives, and the price of an exit that is heard as a fade.
         *
         * <p>The window is not tied to the ramp's length in ms: it is a share of it, so a
         * 30 s blend gets a 7.5 s exit and an 8 s one gets 2 s, and the promotion — which
         * happens when {@code t} reaches 1 — always lands after the exit is over rather
         * than in the middle of it.
         */
        private static final double OUT_RELEASE_FRACTION = 0.25d;

        /**
         * The stretch at the very end of the ramp where this shape is at <b>exactly</b>
         * zero — the guarantee that releasing the outgoing player cannot be heard as a cut.
         *
         * <p>The ramp promotes (and the backend releases the outgoing player) on the first
         * tick at {@code t >= 1}, which can be up to one {@code RAMP_TICK_MS} after the
         * ramp's end and therefore after the last gain write of a player that is being torn
         * down microseconds later. The window above already asks for silence at {@code t}=1,
         * but "silence at exactly one instant" is a weaker promise than it looks: a tick
         * delayed by a stalled main thread, or a release reached by any path that is not
         * the promotion, would tear the player down at whatever the previous tick set. So
         * the last 1% of the ramp is not a fade but a held zero: the outgoing track has
         * been at exactly zero for at least ({@code OUT_SILENT_TAIL} × ramp − one tick)
         * before anything can release it — 118 ms of a 15 s ramp, 168 ms of a 30 s one.
         *
         * <p>1% is invisible as a discontinuity: the window is already at -51 dB when the
         * held zero begins, on the far side of {@link #INAUDIBLE_DB}. The backend logs that
         * assertion at every release (see {@code AndroidAudioBackend}'s release line), so a
         * future shape that lost this property would say so instead of only being heard. */
        private static final double OUT_SILENT_TAIL = 0.01d;

        /** How fast the incoming track's bed arrives: the sine of {@code t} raised
         *  to this, so a smaller exponent means a level reached sooner. */
        private static final double IN_BED_EXPONENT = 0.55d;

        @Override public float outGain(float t) {
            double x = clamp(t);
            // Held at zero for the last OUT_SILENT_TAIL of the ramp: anything that releases
            // this player from here on is releasing something that is not making a sound.
            if (x >= 1d - OUT_SILENT_TAIL) return 0f;
            double hold = Math.cos(Math.pow(x, OUT_HOLD_EXPONENT) * Math.PI / 2.0);
            if (x <= 1d - OUT_RELEASE_FRACTION) return (float) hold;
            // The exit: a raised cosine over the last quarter, so the level lands on zero
            // smoothly rather than arriving there at the hold shape's own steep tail.
            double u = (x - (1d - OUT_RELEASE_FRACTION)) / OUT_RELEASE_FRACTION;
            double window = 0.5d * (1d + Math.cos(Math.PI * u));
            return (float) (hold * window);
        }

        @Override public float inGain(float t) {
            return (float) Math.sin(Math.pow(clamp(t), IN_BED_EXPONENT) * Math.PI / 2.0);
        }

        @Override public float outSilentTail() {
            return (float) OUT_SILENT_TAIL;
        }
    };

    /** How far apart the two tracks may be, dB, and still count as "both audible"
     *  for the measurement below. Six decibels is the usual "clearly quieter but
     *  plainly present" line: quieter than that and a track is heard as part of the
     *  other one's background rather than as a track of its own. */
    public static final float BOTH_AUDIBLE_DB = 6f;

    /**
     * The level, dB below its own full scale, at which a track is treated as silent —
     * the floor a player may be released at.
     *
     * <p>Releasing a player discards whatever is still buffered in it, so a release is a
     * hard cut by construction; the only thing that makes it inaudible is the gain the
     * track was at. -60 dB is a thousandth of full scale, far below the noise floor of
     * anything this app plays and below the smallest step of the 16-bit output path, so a
     * track released at or under it is not "very quiet", it is not there. The backend
     * asserts this at every promotion and logs the numbers it checked, because "the ramp
     * should have faded it out" is exactly the assumption that produced the reported
     * 「上一首歌戛然而止」: the gain a player is released at is the one thing about a blend
     * that cannot be heard afterwards, only seen in the log.
     */
    public static final float INAUDIBLE_DB = -60f;

    /** A gain as dB, with a floor instead of negative infinity: {@link #INAUDIBLE_DB} for
     *  anything at or below it, so the release assertion can print a number. */
    public static double gainDb(float gain) {
        if (gain <= 0f) return INAUDIBLE_DB;
        return 20d * Math.log10(gain);
    }

    private final String label;

    FadeCurve(String label) {
        this.label = label;
    }

    /** {@code t} forced into 0..1, so a caller measuring at, or past, the ends of
     *  the ramp still gets an answer instead of {@code NaN}. */
    private static double clamp(float t) {
        return t <= 0f ? 0d : (t >= 1f ? 1d : t);
    }

    /**
     * How much of a ramp of {@code rampMs} this curve spends with <b>both tracks
     * audible</b> — the two gains within {@link #BOTH_AUDIBLE_DB} of each other.
     *
     * <p>This is the measurable stand-in for "the blend sounds long enough": the
     * complaint a symmetric ramp earns is that the window where the listener can
     * hear two tracks is only about half of it (one track alone at each end is a
     * fade, not a mix), and unlike the overlap length itself this number is a
     * property of the curve. It is reported on the ramp's own log line so a
     * boundary's "feels longer" claim can be checked against a number instead of a
     * memory of the previous build.
     *
     * <p>Sampled rather than integrated in closed form: the two curves have no
     * common primitive once the exponents are in, and a few hundred samples put the
     * resolution far below anything a listener could hear.
     */
    public long bothAudibleMs(long rampMs, float withinDb) {
        if (rampMs <= 0L) return 0L;
        int steps = 400;
        // A gain ratio of `r` is `20*log10(r)` dB, so "within N dB" is a ratio of at
        // most 10^(N/20) — computed once, because the loop below only compares.
        double maxRatio = Math.pow(10d, Math.max(0f, withinDb) / 20d);
        int inside = 0;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5f) / steps;             // midpoint of each slice
            float out = Math.abs(outGain(t));
            float in = Math.abs(inGain(t));
            float loud = Math.max(out, in);
            float quiet = Math.min(out, in);
            if (loud <= 1e-6f) continue;              // neither is audible at all
            if (quiet <= 1e-6f) continue;
            if (loud / quiet <= maxRatio) inside++;
        }
        return Math.round((double) rampMs * inside / steps);
    }

    /** {@link #bothAudibleMs(long, float)} at the standard 6 dB. */
    public long bothAudibleMs(long rampMs) {
        return bothAudibleMs(rampMs, BOTH_AUDIBLE_DB);
    }

    /** "12200ms of the 15000ms ramp (81%)" — the fragment the ramp's log line
     *  carries, so the shape's one measurable property travels with every boundary. */
    public String bothAudibleText(long rampMs) {
        long ms = bothAudibleMs(rampMs);
        long pct = rampMs > 0L ? Math.round(100d * ms / rampMs) : 0L;
        return ms + "ms of the " + rampMs + "ms ramp (" + pct + "%)";
    }

    /** Chinese name for the settings UI. */
    public String label() {
        return label;
    }

    /** Gain applied to the outgoing track at overlap progress {@code t} (0–1). */
    public abstract float outGain(float t);

    /**
     * The share of the ramp, at its end, over which this shape holds the outgoing track
     * at <b>exactly</b> zero — the stretch a player may be released inside without that
     * release being a sound of its own.
     *
     * <p>Zero for the shapes that fade all the way to the end: their last gain write is
     * silence only at the instant the ramp finishes, which is a weaker promise but an
     * adequate one for a ramp that is a quarter of a second long and is not expected to
     * be anything but a clean edge. {@link #DJ_BLEND} is the shape that needs more than
     * that, being a multi-second exit a listener follows: see its
     * {@code OUT_SILENT_TAIL}, and the backend's release line, which prints this number
     * beside the gain it actually released at.
     */
    public float outSilentTail() {
        return 0f;
    }

    /** Gain applied to the incoming track at overlap progress {@code t} (0–1). */
    public abstract float inGain(float t);
}
