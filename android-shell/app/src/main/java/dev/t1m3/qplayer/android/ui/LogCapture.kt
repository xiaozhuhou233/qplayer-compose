// Ⓜ 2026-10-02: the listener's log system（「在设置里添加一个日志开关，打开即可记录
// 程序从开始跑到退出的所有日志……再加一个导出开关……要求日志记录所有报错和警告」）.
//
// One session file per enabled run, everything the shared Logger emits (all
// levels — info, success, warnings, errors, exceptions) plus an uncaught-crash
// hook, appended line by line with timestamps. The file lives in the app's
// private logs/ directory; the 关于 page's export action copies the newest one
// into the system Downloads (MediaStore on 29+, app-external below) and reports
// where it landed.
package dev.t1m3.qplayer.android.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.t1m3.qplayer.util.Logger
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object LogCapture {

    private const val TS = "yyyyMMdd-HHmmss"
    private var writer: BufferedWriter? = null
    private var sessionFile: File? = null
    private var priorHandler: Thread.UncaughtExceptionHandler? = null
    private var hookInstalled = false

    val active: Boolean get() = writer != null

    /** Idempotent. Starting wraps the installed Logger sink in a tee; stopping
     *  unwraps back to it and closes the session file. */
    @Synchronized
    fun setEnabled(context: Context, on: Boolean) {
        if (on == active) return
        if (on) start(context) else stop()
    }

    private fun start(context: Context) {
        val dir = File(context.filesDir, "logs").apply { if (!isDirectory) mkdirs() }
        val stamp = SimpleDateFormat(TS, Locale.US).format(Date())
        val f = File(dir, "session-$stamp.log")
        try {
            val w = BufferedWriter(FileWriter(f, true))
            w.write("=== qplayer session $stamp ===")
            w.newLine()
            writer = w
            sessionFile = f
            Logger.setSink(TeeSink(Logger.getSink(), w))
            installCrashHook()
            Logger.warn("log capture started -> {}", f.absolutePath)
        } catch (failure: Exception) {
            Logger.warn("log capture could not start ({}): {}", f.absolutePath, failure.toString())
        }
    }

    private fun stop() {
        try {
            Logger.warn("log capture stopped")
        } catch (_: Exception) {
        }
        Logger.setSink(null)
        runCatching { writer?.flush(); writer?.close() }
        writer = null
        if (hookInstalled) {
            Thread.setDefaultUncaughtExceptionHandler(priorHandler)
            hookInstalled = false
            priorHandler = null
        }
    }

    private fun installCrashHook() {
        if (hookInstalled) return
        priorHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeLine("E|CRASH on ${thread.name}: ${throwable.javaClass.name}: ${throwable.message}")
            writeLine(throwable.stackTraceToString())
            try {
                writer?.flush()
            } catch (_: Exception) {
            }
            priorHandler?.uncaughtException(thread, throwable)
        }
        hookInstalled = true
    }

    @Synchronized
    private fun writeLine(line: String) {
        val w = writer ?: return
        try {
            w.write(line)
            w.newLine()
        } catch (_: Exception) {
        }
    }

    /** Copy the newest session file out. Returns where it landed, or null when
     *  there is nothing to export. */
    @Synchronized
    fun export(context: Context): String? {
        val src = newestSession(context) ?: return null
        val stamp = SimpleDateFormat(TS, Locale.US).format(Date())
        val name = "qplayer-log-${src.nameWithoutExtension.takeLast(15)}-$stamp.log"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                }
                val uri = context.contentResolver
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                "下载/qplayer-logs/$name"
            } else {
                val dir = Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val out = File(dir, name)
                src.copyTo(out, overwrite = true)
                out.absolutePath
            }
        } catch (failure: Exception) {
            Logger.warn("log export failed: {}", failure.toString())
            null
        }
    }

    private fun newestSession(context: Context): File? =
        File(context.filesDir, "logs").listFiles { f -> f.name.startsWith("session-") }
            ?.maxByOrNull { it.lastModified() }

    /** Forwards to the backend that was installed before the capture started and
     *  appends the same line to the session file. */
    private class TeeSink(
        private val prior: Logger.Sink,
        private val sinkWriter: BufferedWriter,
    ) : Logger.Sink {
        private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

        override fun log(level: String, message: String) {
            prior.log(level, message)
            writeLine("${stamp.format(Date())} $level $message")
        }

        override fun exception(message: String, t: Throwable) {
            prior.exception(message, t)
            writeLine("${stamp.format(Date())} E $message")
            writeLine(t.stackTraceToString())
        }
    }
}
