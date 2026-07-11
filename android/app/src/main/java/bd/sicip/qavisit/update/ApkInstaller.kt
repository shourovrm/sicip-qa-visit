// one-tap update: DownloadManager fetches the apk, then we hand it straight to the
// package installer via content:// uri (no FileProvider needed -- DownloadManager grants read).
package bd.sicip.qavisit.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

// ponytail: in-memory guard is enough -- app-scoped, dies with process same as the download would stall.
private var activeDownloadUrl: String? = null

fun downloadAndInstall(context: Context, url: String, versionName: String) {
    if (activeDownloadUrl == url) {
        Toast.makeText(context, "Downloading…", Toast.LENGTH_SHORT).show()
        return
    }
    activeDownloadUrl = url

    // app context: download outlives whatever Activity/Compose scope kicked it off.
    val appContext = context.applicationContext
    val downloadManager = appContext.getSystemService<DownloadManager>() ?: return
    val request = DownloadManager.Request(Uri.parse(url))
        .setMimeType("application/vnd.android.package-archive")
        .setTitle("SICIP QA Visit v$versionName")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(appContext, null, "update-$versionName.apk")
    val downloadId = downloadManager.enqueue(request)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (finishedId != downloadId) return
            activeDownloadUrl = null
            ctx.unregisterReceiver(this)

            val apkUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
    ContextCompat.registerReceiver(
        appContext,
        receiver,
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        ContextCompat.RECEIVER_EXPORTED, // system broadcast -- API 33+ requires an explicit flag
    )
}
