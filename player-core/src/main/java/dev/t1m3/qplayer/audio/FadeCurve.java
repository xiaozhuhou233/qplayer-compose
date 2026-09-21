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
     * The staged, DJ-shaped hand-over: the outgoing track is <em>taken down to a low bed
     * early</em> and then <em>leaves</em> it part way through, and the incoming track
     * arrives as a bed under it and grows to its own level by the end of the window.
     * This is the shape that makes a long overlap read as a mix rather than as a fade —
     * and, since round 17, the one that keeps the outgoing track's (unremovable) vocals
     * from being the loudest thing in the blend.
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
     * <p><b>What round 17 changed, and why.</b> Rounds 12–16 held the outgoing track
     * within 3 dB of its own level until three quarters of the window. That maximised
     * the both-audible share (68%) — and it was the wrong target, because the reason
     * the listener hears "two songs fighting" is not that both are audible, it is that
     * both are <em>loud</em>, and the outgoing track's vocals cannot be taken out of it
     * (see {@link #OUT_BED_DB}). The shape is now written in <b>decibels</b>, in three
     * eased stretches: down into a −10 dB bed over the first three tenths, held there
     * until the middle, and gone (six decibels under {@link #INAUDIBLE_DB}) by 82%.
     *
     * <p>Measured over a 15 s ramp ({@code FadeCurveTest} prints the same table; the
     * "before" column is what rounds 12–16 shipped):
     *
     * <pre>
     *                     t=0.25   t=0.50   t=0.75   t=0.90   -30dB at   -60dB at   both6dB
     * before (cos t^2.6)   -0.0dB   -0.3dB   -2.7dB  -17.9dB   14.10s     14.72s       68%
     * after  (this shape)  -9.3dB  -10.0dB  -59.6dB  -66.0dB    9.46s     11.28s       17%
     * </pre>
     *
     * <p>So the outgoing track's vocals are 10 dB down within the first quarter of the
     * blend, 30 dB down by the start of its last third and gone 3.7 s before the blend
     * ends — against "at its own level until three quarters, then gone in a second". The
     * both-audible share falls from 68% to 17%, and that is the point rather than a
     * regression: the earlier number described two tracks at their own level at once.
     *
     * <p>The incoming track is the sine of {@code t^0.45} (see {@link #IN_BED_EXPONENT}):
     * it reaches half its level within the first tenth of the window, is a real bed from
     * there on, and owns the last stretch alone. The two are summed, so the blend's own
     * loudness dips about 1.9 dB at a fifth of the ramp (the outgoing track leaving faster
     * than the incoming one arrives) and is back at one track's level by two thirds of it;
     * the peak every earlier version had — up to +2.3 dB in the middle — is gone (+0.2 dB,
     * a dip in the middle instead).
     */
    DJ_BLEND("DJ 式") {
        /**
         * The level the outgoing track is brought down to, dB below its own full scale, for
         * the body of the blend — the "淡一点，要不抢了" half of the shape.
         *
         * <p>The staged shape used to hold the outgoing track at its own level for most of
         * the window and spend the whole exit at the end (rounds 12–16, {@code cos(t^2.6)}).
         * The measurement that ended it is a listening one: with the outgoing track at unity
         * and the incoming one arriving underneath it, the outgoing track's <em>vocals</em> —
         * which this app cannot remove, because stripping them would mean switching the
         * audible deck's source mid-playback, the mechanism behind the P0 ("playback died
         * after a transition") and the replay incidents — stay the loudest thing in the room
         * for the whole blend, and two sets of vocals at once is the "人声混合得很乱" the
         * user reported as a serious problem. Nothing about the material can fix that from
         * inside the deck; the only lever is level.
         *
         * <p>Ten decibels is a third of the amplitude and about half the loudness: the
         * outgoing track is still plainly there as the bed the incoming one is built over —
         * which the key blend needs, since its ladder moves <em>both</em> decks — while its
         * vocals sit 10 dB under the incoming track's own material instead of on top of it.
         * The cost is measured, not hidden: the two tracks are within 6 dB of each other for
         * about a sixth of the ramp instead of two thirds
         * ({@link #bothAudibleMs(long)} — see the class's table), because a track that is
         * deliberately 10 dB down is not "both tracks at the same level". That is the point
         * of the change: the earlier rounds' 68% was two full-level tracks at once.
         */
        private static final double OUT_BED_DB = -10d;

        /**
         * How much of the ramp is spent stepping down into {@link #OUT_BED_DB}: the first
         * three tenths, with zero slope at both ends so the step is a fade rather than a
         * duck.
         *
         * <p>Not shorter, because a fast drop to the bed is heard as the current song being
         * turned down; not longer, because the whole point is that the outgoing track stops
         * competing early. Three tenths of a 15 s ramp is 4.5 s, which is a bar or two at
         * this library's tempos — the length of an ordinary musical gesture.
         */
        private static final double OUT_BED_ARRIVE = 0.30d;

        /**
         * Where the exit starts, as a share of the ramp: the middle. Everything before it is
         * the bed; everything after it is the outgoing track leaving.
         *
         * <p>This is the "更早" half of the requirement. The round-16 shape began its exit at
         * three quarters of the ramp, and — because it was working from −2.7 dB rather than
         * from the bed — only crossed −30 dB at 94% of the window. This one crosses it at
         * 63% (measured on a 15 s ramp: 9.5 s against 14.1 s) and is gone by 82% rather than
         * at 97%, so the last stretch of the blend belongs to the incoming track alone. Its
         * own vocals are out for that whole stretch (see {@code DjEdit}), so what the
         * listener keeps hearing is the incoming track's backing — and then, after the blend
         * has ended, its voice.
         */
        private static final double OUT_EXIT_START = 0.50d;

        /**
         * Where the exit is over, as a share of the ramp: the level has reached
         * {@link #OUT_EXIT_FLOOR_DB} here and stays there until the held zero.
         *
         * <p>0.82 rather than the round-16 shape's ~0.97: the exit is the same shape but
         * moved forward as a whole, which is what "faster" means where it can be measured —
         * the time the outgoing track spends above −30 dB of its own level. The peak slope
         * is unchanged (a raised cosine over 0.32 of the ramp covering 56 dB: about 17 dB/s
         * on a 15 s blend), so the exit sounds like the round-16 one, just earlier.
         */
        private static final double OUT_EXIT_FLOOR = 0.82d;

        /**
         * The level the exit reaches, dB below full scale: six decibels under
         * {@link #INAUDIBLE_DB}, so "the outgoing track was released at or below the floor"
         * is never a question of the last bit of a logarithm.
         *
         * <p>It is not zero: the shape's promise of an exactly-zero stretch is
         * {@link #OUT_SILENT_TAIL}, which begins later, and a written gain of −66 dB is the
         * same nothing but keeps the trajectory expressible as a number in the log.
         */
        private static final double OUT_EXIT_FLOOR_DB = -66d;

        /**
         * The stretch at the very end of the ramp where this shape is at <b>exactly</b>
         * zero — the guarantee that releasing the outgoing player cannot be heard as a cut.
         *
         * <p>The ramp promotes (and the backend releases the outgoing player) on the first
         * tick at {@code t >= 1}, which can be up to one {@code RAMP_TICK_MS} after the
         * ramp's end and therefore after the last gain write of a player that is being torn
         * down microseconds later. The exit above already asks for −66 dB from 82% on, but
         * "silence at exactly one instant" is a weaker promise than it looks: a tick delayed
         * by a stalled main thread, or a release reached by any path that is not the
         * promotion, would tear the player down at whatever the previous tick set. So the
         * last 1% of the ramp is not a fade but a held zero: the outgoing track has been at
         * exactly zero for at least ({@code OUT_SILENT_TAIL} × ramp − one tick) before
         * anything can release it — 118 ms of a 15 s ramp, 168 ms of a 30 s one.
         *
         * <p>1% is invisible as a discontinuity: the shape is already at −66 dB when the
         * held zero begins, on the far side of {@link #INAUDIBLE_DB}. The backend logs that
         * assertion at every release (see {@code AndroidAudioBackend}'s release line), so a
         * future shape that lost this property would say so instead of only being heard. */
        private static final double OUT_SILENT_TAIL = 0.01d;

        /** How fast the incoming track's bed arrives: the sine of {@code t} raised
         *  to this, so a smaller exponent means a level reached sooner.
         *
         *  <p>0.45 since round 17 (it was 0.55). With the outgoing track now down at
         *  {@link #OUT_BED_DB} for the body of the blend, the incoming track is the only
         *  track at its own level, and the earlier it gets there the smaller the trough
         *  between the two: the summed power dips about 1.9 dB at a fifth of the ramp with
         *  0.45 against about 2.6 dB with 0.55. It also puts the incoming track's backing in
         *  place sooner, which is the other half of "让上一首淡一点，让下一首铺进来". */
        private static final double IN_BED_EXPONENT = 0.45d;

        @Override public float outGain(float t) {
            double x = clamp(t);
            // Held at zero for the last OUT_SILENT_TAIL of the ramp: anything that releases
            // this player from here on is releasing something that is not making a sound.
            if (x >= 1d - OUT_SILENT_TAIL) return 0f;
            return (float) Math.pow(10d, outLevelDb(x) / 20d);
        }

        /**
         * The outgoing track's own level at {@code t}, dB — the shape is defined in dB and
         * converted to a gain, and that is deliberate: what the requirement is written in,
         * what the boundary's log line reports and what "monotone" has to mean are all
         * decibels. A shape defined as a gain has a dB curve whose slope runs away wherever
         * the gain approaches zero, which is how the round-16 exit managed to be both
         * monotone in gain and audible as a cliff.
         *
         * <p>Three stretches, each eased with a raised cosine so there is no step in level
         * <em>or</em> in slope at either end of it: down into the bed, the bed, and the
         * exit. Never increasing — the test walks it at 1/1000 resolution.
         */
        private double outLevelDb(double t) {
            if (t < OUT_BED_ARRIVE) return OUT_BED_DB * ease(t / OUT_BED_ARRIVE);
            if (t <= OUT_EXIT_START) return OUT_BED_DB;
            double u = Math.min(1d, (t - OUT_EXIT_START) / (OUT_EXIT_FLOOR - OUT_EXIT_START));
            return OUT_BED_DB + (OUT_EXIT_FLOOR_DB - OUT_BED_DB) * ease(u);
        }

        /** A raised cosine over 0..1: 0 at both ends, 1 in the middle, zero slope at both
         *  ends, so anything built from it joins its neighbours smoothly. */
        private double ease(double u) {
            return 0.5d * (1d - Math.cos(Math.PI * clamp((float) u)));
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
