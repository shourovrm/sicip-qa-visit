// background sync via WorkManager -- it already handles retry/backoff/doze scheduling, no
// need to hand-roll a foreground service or an AlarmManager loop for this.
package bd.sicip.qavisit.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val PERIODIC_WORK_NAME = "sync"
private const val ONE_SHOT_WORK_NAME = "sync-now"

private fun networkConstraints() = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val result = SyncEngine(applicationContext).syncAll()
        // retry lets WorkManager's own backoff handle transient network/server failures;
        // "not logged in" isn't transient so don't burn retries on it.
        if (result.error != null && result.error != "not logged in") Result.retry() else Result.success()
    }

    companion object {
        // 15 minutes is WorkManager's own floor for periodic work -- asking for less is a no-op.
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

// one-shot sync for pull-to-refresh / app-start, kept separate from the periodic job so
// enqueueing it doesn't disturb the periodic schedule (REPLACE only affects this work name).
object SyncNow {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_SHOT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
