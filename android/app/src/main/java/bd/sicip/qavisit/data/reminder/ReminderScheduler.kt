// schedules the daily reminder check via WorkManager -- same reasoning as SyncWorker: periodic
// work already handles doze/retry, no need for a hand-rolled AlarmManager.
package bd.sicip.qavisit.data.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "visit_reminders"

// how long to wait from `now` until the next occurrence of hour:minute local time. if `now` is
// already at or past today's target, rolls over to tomorrow (covers the midnight-wrap case too:
// e.g. now = 23:50 with target 07:30 lands on tomorrow, not a negative/huge duration).
fun initialDelayTo(hour: Int, minute: Int, now: LocalDateTime): Duration {
    var target = now.toLocalDate().atTime(hour, minute)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return Duration.between(now, target)
}

object ReminderScheduler {
    fun schedule(context: Context) {
        val delay = initialDelayTo(7, 30, LocalDateTime.now())
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
