package com.kiaraen.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.kiaraen.app.core.AppContainer
import com.kiaraen.app.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KiaraApplication : Application(), Configuration.Provider {

    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        installCrashReporter()
        super.onCreate()
        // session storage needs the app context BEFORE any client access
        com.kiaraen.app.data.remote.SupabaseProvider.init(this)

        // Nothing heavy on the main thread: weak phones show the first frame
        // sooner while Room, the Supabase client and the content pack warm up.
        appScope.launch(Dispatchers.IO) {
            runCatching { container.warmUp() }
                .onFailure { Log.e(TAG, "warm-up failed (app stays usable)", it) }
            // bundled content pack → the app is fully usable offline from first launch
            runCatching { container.bootstrap() }
                .onFailure { Log.e(TAG, "content bootstrap failed (app stays usable)", it) }
        }
        // periodic catch-up sync (network-constrained; one-shots handle writes).
        // WorkManager opens its own database on first use → also off-main.
        // NEVER allowed to kill the app at launch — sync is optional by design.
        appScope.launch(Dispatchers.IO) {
            runCatching {
                SyncWorker.enqueuePeriodic(this@KiaraApplication)
                SyncWorker.enqueueOneShot(this@KiaraApplication, pull = true)
            }.onFailure { Log.e(TAG, "WorkManager init failed (app stays usable)", it) }
        }
    }

    /**
     * Field debugging without adb: any uncaught exception is appended to
     * files/last_crash.txt before the system handler runs, and the «من» tab
     * offers a share button while that file exists — the user can forward the
     * exact stack trace with two taps. Logs never contain tokens or passwords.
     */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                File(filesDir, "last_crash.txt").writeText(
                    buildString {
                        appendLine("time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                        appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("android: ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
                        appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        appendLine("thread: ${thread.name}")
                        appendLine(Log.getStackTraceString(throwable))
                    },
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Convenience for the profile tab: readable crash report, if one exists. */
    fun lastCrashReport(): String? =
        runCatching { File(filesDir, "last_crash.txt").takeIf { it.exists() }?.readText() }.getOrNull()

    /** Called after the user shares/reports the crash to clear the flag. */
    fun clearCrashReport() {
        runCatching { File(filesDir, "last_crash.txt").delete() }
    }

    companion object {
        private const val TAG = "KiaraApp"

        /** Static access for the profile tab (application context is a singleton). */
        @Volatile var instance: KiaraApplication? = null
            private set
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        instance = this
    }
}
