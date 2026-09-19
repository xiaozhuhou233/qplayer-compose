package dev.t1m3.qplayer.audio;

import java.nio.ByteBuffer;

/**
 * Where a track's audible content begins and ends, in milliseconds from the file
 * boundaries: {@code headMs} of leading silence and {@code tailMs} of trailing
 * silence, both measured from decoded PCM by a {@link SilenceProfiler}.
 *
 * <p>Used by {@link TransitionKind#SILENCE_TRIM}, and cached per track (see
 * {@code DiskCache}'s silence sub-cache) because the measurement costs a decode
 * while the answer never changes for an unchanged file.
 */
public final class SilenceProfile {

    private final long headMs;
    private final long tailMs;

    public SilenceProfile(long headMs, long tailMs) {
        this.headMs = Math.max(0L, headMs);
        this.tailMs = Math.max(0L, tailMs);
    }

    /** Silence before the first audible sample. */
    public long headMs() {
        return headMs;
    }

    /** Silence after the last audible sample. */
    public long tailMs() {
        return tailMs;
    }

    /** Compact form for the disk cache: two big-endian ints. */
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putInt((int) headMs);
        buf.putInt((int) tailMs);
        return buf.array();
    }

    /** Inverse of {@link #toBytes()}; null when the bytes are not one (a cache
     *  file from an older layout, a truncated write). */
    public static SilenceProfile fromBytes(byte[] data) {
        if (data == null || data.length != 8) return null;
        ByteBuffer buf = ByteBuffer.wrap(data);
        return new SilenceProfile(buf.getInt(), buf.getInt());
    }

    @Override
    public String toString() {
        return "SilenceProfile{head=" + headMs + "ms, tail=" + tailMs + "ms}";
    }
}
