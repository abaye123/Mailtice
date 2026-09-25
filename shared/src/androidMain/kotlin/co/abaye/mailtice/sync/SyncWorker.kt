package co.abaye.mailtice.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.abaye.mailtice.di.AppGraphHolder
import co.abaye.mailtice.platform.bindAndroidContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "mail-sync"

/** WorkManager caps a worker at 10 minutes; stop well before so the next run is not penalised. */
private const val WORK_BUDGET_MS = 8 * 60_000L

/** One background round over every account, then notifications. Same engine as the open app. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        bindAndroidContext(applicationContext)
        val graph = AppGraphHolder.graph
        val settings = graph.store.load().settings
        withTimeoutOrNull(WORK_BUDGET_MS) {
            graph.sync.syncAllOnce().forEach { graph.notifications.announce(it, settings) }
        }
        return Result.success()
    }
}

object BackgroundSync {
    /** Every 15 minutes (the Android minimum), only with a network. KEEP: rescheduling is idempotent. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
