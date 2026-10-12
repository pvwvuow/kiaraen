package com.kiaraen.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.kiaraen.app.core.AppContainer
import com.kiaraen.app.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KiaraApplication : Application(), Configuration.Provider {

    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // bundled content pack → the app is fully usable offline from first launch
        appScope.launch { container.bootstrap() }
        // periodic catch-up sync (network-constrained; one-shots handle writes)
        SyncWorker.enqueuePeriodic(this)
        SyncWorker.enqueueOneShot(this, pull = true)
        WorkManager.getInstance(this) // warm-up initialization
    }
}
