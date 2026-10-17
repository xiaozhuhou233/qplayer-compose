package dev.t1m3.qplayer.android.ui

fun main() {
    val stamp = GlassSampleStamp(1L, 10, 20, 600, 120)
    val policy = GlassSamplingPolicy()
    check(policy.shouldSample(0, stamp))
    policy.completed(0, stamp, true)
    check(!policy.shouldSample(GLASS_SAMPLE_ACTIVE_MS - 1, stamp.copy(revision = 2)))
    check(policy.shouldSample(GLASS_SAMPLE_ACTIVE_MS, stamp.copy(revision = 2)))
    check(!policy.shouldSample(GLASS_SAMPLE_IDLE_MS - 1, stamp))
    check(policy.shouldSample(GLASS_SAMPLE_IDLE_MS, stamp))
    check(policy.shouldSample(GLASS_SAMPLE_ACTIVE_MS, stamp.copy(x = 40)))
    check(policy.shouldSample(GLASS_SAMPLE_ACTIVE_MS, stamp.copy(width = 300)))
    policy.completed(GLASS_SAMPLE_IDLE_MS, stamp, false)
    check(!policy.shouldSample(GLASS_SAMPLE_IDLE_MS + 999, stamp.copy(revision = 3)))
    check(policy.shouldSample(GLASS_SAMPLE_IDLE_MS + 1000, stamp.copy(revision = 3)))

    val perPage = GlassSamplingPolicy(perPage = true)
    check(perPage.shouldSample(0, stamp))
    perPage.completed(0, stamp, true)
    // Long dwell, scrolling and dock collapse keep the page's single sample.
    check(!perPage.shouldSample(60_000, stamp))
    check(!perPage.shouldSample(60_000, stamp.copy(x = 40, width = 300)))
    check(perPage.shouldSample(60_000, stamp.copy(revision = 2)))
    perPage.completed(60_000, stamp.copy(revision = 2), false)
    check(!perPage.shouldSample(60_999, stamp.copy(revision = 2)))
    check(perPage.shouldSample(61_000, stamp.copy(revision = 2)))
    perPage.completed(61_000, stamp.copy(revision = 2), true)
    check(!perPage.shouldSample(120_000, stamp.copy(revision = 2)))
    check(perPage.shouldSample(120_000, stamp.copy(revision = 3)))

    fun samplesOverTenSeconds(moving: Boolean): Int {
        val cadence = GlassSamplingPolicy()
        var samples = 0
        for (time in 0L until 10_000L step GLASS_SAMPLE_POLL_MS) {
            val frame = stamp.copy(revision = if (moving) time else 1L)
            if (cadence.shouldSample(time, frame)) {
                samples++
                cadence.completed(time, frame, true)
            }
        }
        return samples
    }
    fun expectedPollCount(interval: Long): Int {
        val polledInterval = ((interval + GLASS_SAMPLE_POLL_MS - 1) / GLASS_SAMPLE_POLL_MS) * GLASS_SAMPLE_POLL_MS
        return ((9999L / polledInterval) + 1).toInt()
    }
    check(samplesOverTenSeconds(false) == expectedPollCount(GLASS_SAMPLE_IDLE_MS))
    check(samplesOverTenSeconds(true) == expectedPollCount(GLASS_SAMPLE_ACTIVE_MS))
    // Actual APK sampling additionally awaits its full 1000ms luminance tween.
    val sequential = GlassSamplingPolicy()
    var samples = 0
    var now = 0L
    while (now < 10_000L) {
        val moving = stamp.copy(revision = now)
        if (sequential.shouldSample(now, moving)) {
            samples++
            sequential.completed(now, moving, true)
            now += GLASS_COLOR_DURATION_MS
        } else now += GLASS_SAMPLE_POLL_MS
    }
    check(samples == 10)
    check(glassReadbackGapMs(0f) == 16L)
    check(glassReadbackGapMs(16f) == 40L)
    check(glassReadbackGapMs(100f) == 64L)
    check(glassReadbackGapMs(Float.NaN) == 64L)
    println("PASS: sampling policy boundaries, geometry changes, failure backoff, shared cost budget; APK 1000ms sequential cadence.")
}
