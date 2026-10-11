package com.lexi.wordbook

import android.app.Application
import com.lexi.wordbook.ai.AiConfigRepository
import com.lexi.wordbook.ai.ChatRepository
import com.lexi.wordbook.data.PreferencesRepository
import com.lexi.wordbook.data.WordRepository
import com.lexi.wordbook.speech.SpeechPlayer

/** Manual dependency container — keeps the app free of DI frameworks. */
class AppContainer(application: Application) {
    val words: WordRepository = WordRepository(application)
    val preferences: PreferencesRepository = PreferencesRepository(application)
    val speech: SpeechPlayer = SpeechPlayer(application)
    val aiConfig: AiConfigRepository = AiConfigRepository(application)
    val chat: ChatRepository = ChatRepository(application)
}

class WordBookApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        installCrashLogger()
    }

    /**
     * Records the last uncaught exception to a file so a crash can be diagnosed from the device
     * without a cable attached (设置 → 关于 → 复制崩溃日志).
     *
     * The handler re-throws after writing, so the system crash dialog is unchanged.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    append("time: ").append(java.util.Date()).append('\n')
                    append("thread: ").append(thread.name).append('\n')
                    append("app: ").append(BuildConfig.VERSION_NAME)
                        .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                    append("device: ").append(android.os.Build.MANUFACTURER).append(' ')
                        .append(android.os.Build.MODEL).append(' ')
                        .append("API ").append(android.os.Build.VERSION.SDK_INT).append('\n')
                    append("---\n")
                    append(android.util.Log.getStackTraceString(error))
                }
                java.io.File(filesDir, "last-crash.txt").writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        /** Path of the crash report written by [installCrashLogger]. */
        const val CRASH_FILE = "last-crash.txt"

        /** Reads the last crash report, or null when the app has not crashed. */
        fun readLastCrash(application: Application): String? =
            runCatching {
                java.io.File(application.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText()
            }.getOrNull()

        fun clearLastCrash(application: Application) {
            runCatching { java.io.File(application.filesDir, CRASH_FILE).delete() }
        }
    }
}
