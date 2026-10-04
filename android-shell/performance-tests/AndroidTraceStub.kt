package android.os

/** Only platform tracing is replaced for the host-JVM Compose regression. */
@Suppress("UNUSED_PARAMETER")
object Trace {
    @JvmStatic fun beginSection(name: String) = Unit
    @JvmStatic fun endSection() = Unit
    @JvmStatic fun isEnabled(): Boolean = false
}
