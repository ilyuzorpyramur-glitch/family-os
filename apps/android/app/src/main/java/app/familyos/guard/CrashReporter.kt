package app.familyos.guard

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ловит необработанные исключения, сохраняет отчёт на устройстве
 * и отправляет его на сервер при следующем запуске.
 */
object CrashReporter {

    private const val FILE = "crash-last.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = "time=$stamp\nthread=${thread.name}\nmessage=${error::class.java.name}: ${error.message}\n\n$stack"
        File(context.filesDir, FILE).writeText(report.take(16_000))
    }

    fun pending(context: Context): String? {
        val file = File(context.filesDir, FILE)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
