package com.example.diceroller

import java.io.File

/**
 * Writes the last crash to disk before handing off to the platform handler,
 * so even without a remote reporter you can diagnose a crash from logcat or a
 * bug report, and detect crash-loops on next startup.
 */
class CrashGuard(private val filesDir: File) : Thread.UncaughtExceptionHandler {

    private val systemHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, error: Throwable) {
        runCatching {
            File(filesDir, "last_crash.txt").writeText(
                "${System.currentTimeMillis()} on ${thread.name}\n${error.stackTraceToString()}"
            )
        }
        systemHandler?.uncaughtException(thread, error)
    }

    companion object {
        /** Call once at startup — returns and deletes the previous crash, if any. */
        fun consumePrevious(filesDir: File): String? {
            val f = File(filesDir, "last_crash.txt")
            val text = if (f.exists()) runCatching { f.readText() }.getOrNull() else null
            f.delete()
            return text
        }
    }
}
