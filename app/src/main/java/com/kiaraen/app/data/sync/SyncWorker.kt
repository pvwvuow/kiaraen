package com.kiaraen.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kiaraen.app.KiaraApplication
import java.util.concurrent.TimeUnit

/**
 * WorkManager wrapper around SyncEngine.
 *   * network-constrained + exponential backoff (30s → 1h)
 *   * unique work names → no duplicate concurrent syncs
 *   * survives process death: WorkManager re-enqueues failed pushes
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pull = inputData.getBoolean(KEY_PULL, false)
        return try {
            // building the container touches Room/DataStore/Supabase → inside try
            val engine = (applicationContext as? KiaraApplication)?.container?.syncEngine
                ?: return Result.failure()
            if (engine.syncAll(pullOnStart = pull)) Result.success() else Result.retry()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "sync failed, will retry", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val KEY_PULL = "pull"
        private const val ONE_SHOT = "kiara-sync-once"
        private const val PERIODIC = "kiara-sync-periodic"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Queue one sync soon (after any local write or app start). */
        fun enqueueOneShot(context: Context, pull: Boolean = false) {
            val req = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(androidx.work.Data.Builder().putBoolean(KEY_PULL, pull).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, req)
        }

        /** Safety net: periodic catch-up even if one-shots got lost. */
        fun enqueuePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
