package dev.daniel.minihub.core

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Keeps the stack trace of the last crash in the app's private storage, so it can be shown
 * (and copied) on the next start without adb. The normal crash handling still runs afterwards.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, previous))
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    private class Handler(
        private val app: Context,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file(app).writeText("${LocalDateTime.now()} on thread ${t.name}\n$sw")
            }
            previous?.uncaughtException(t, e)
        }
    }
}
