// day-before/day-of visit reminder: one local notification per day summarizing the signed-in
// officer's own scheduled visits starting today or tomorrow. no server round-trip -- reads
// whatever synced into the local db already.
package bd.sicip.qavisit.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import bd.sicip.qavisit.MainActivity
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val NOTIFICATION_CHANNEL_ID = "reminders"

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = SessionStore(applicationContext).current() ?: return@withContext Result.success()
        val db = AppDb.get(applicationContext)
        val visits = db.visitDao().byOfficer(session.userId)
        val today = LocalDate.now()
        val lines = reminderLines(visits, today)
        if (lines.isNotEmpty()) notify(today, lines)
        Result.success()
    }

    private fun notify(today: LocalDate, lines: List<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Visit reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // ponytail: system icon, swap for app icon when we have one
            .setContentTitle(if (lines.size == 1) "Upcoming visit" else "Upcoming visits")
            .setContentText(lines.first())
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
        if (lines.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
            lines.forEach { inbox.addLine(it) }
            builder.setStyle(inbox)
        }
        try {
            // stable per calendar day (see reminderNotificationId) so a same-day re-run (worker retried, or the periodic
            // job firing again) updates this notification in place instead of duplicating it.
            NotificationManagerCompat.from(applicationContext).notify(reminderNotificationId(today), builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted yet -- the reminder is just skipped, not fatal.
        }
    }

    // tap -> open the app (brings the running task forward instead of stacking a second copy)
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

fun reminderNotificationId(day: LocalDate): Int = day.hashCode()

// tour started -> today's reminder is stale, drop it from the tray
fun cancelTodaysReminder(context: Context) {
    NotificationManagerCompat.from(context).cancel(reminderNotificationId(LocalDate.now()))
}
