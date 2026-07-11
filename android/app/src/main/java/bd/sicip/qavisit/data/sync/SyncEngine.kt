// offline-first sync: push our dirty rows, then pull everyone else's. LWW comes for free --
// the server's updated_at trigger stamps every push, so our own pull-back always looks newer
// than what we had. the only wrinkle is a row we edited locally but haven't pushed yet (or
// whose push just failed): shouldApplyRemote keeps that local edit until it's safely pushed.
package bd.sicip.qavisit.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.remote.SupabaseClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant

// ---- pure decision rules, tested without any Android/network glue ----

// a locally-dirty row means "I have an unpushed edit" -- never let an incoming remote row
// clobber it. once the edit is pushed, dirty flips false and the next pull applies freely.
fun shouldApplyRemote(localDirty: Boolean): Boolean = !localDirty

// iso-8601 timestamps (same format, same offset) sort the same lexicographically as
// chronologically, so "newest" is just string max. equal timestamps compare equal, no
// special case needed. relies on postgrest always rendering timestamptz with a fixed-width
// +00:00 offset -- a mixed offset format would break the lexicographic ordering.
fun advanceWatermark(current: String, updatedAts: List<String>): String =
    (updatedAts + current).max()

data class SyncResult(
    val pushed: Map<String, Int> = emptyMap(),
    val pulled: Map<String, Int> = emptyMap(),
    val error: String? = null,
)

private const val NOTIFICATION_CHANNEL_ID = "informs"

// postgrest caps a bare select at 1000 rows -- anything past that was silently getting
// dropped, and the watermark then jumped past it forever. page through with limit/offset
// until a short page proves we've drained the table.
private const val PAGE_SIZE = 1000

class SyncEngine(
    private val context: Context,
    private val db: AppDb = AppDb.get(context),
    private val client: SupabaseClient = SupabaseClient(),
    private val sessionStore: SessionStore = SessionStore(context),
    private val syncState: SyncStateStore = SyncStateStore(context),
) {
    // push every dirty table, then pull every table. any failure stops the rest of the
    // cycle (next scheduled run picks up where this one left off) -- the error is carried
    // in the result, never thrown, so a worker/caller doesn't need a try/catch of its own.
    suspend fun syncAll(): SyncResult {
        val session = sessionStore.ensureFresh(client) ?: return SyncResult(error = "not logged in")
        val token = session.accessToken
        val pushed = mutableMapOf<String, Int>()
        val pulled = mutableMapOf<String, Int>()
        return try {
            pushed["trips"] = pushTrips(token)
            pushed["visits"] = pushVisits(token)
            pushed["travel_legs"] = pushTravelLegs(token)
            pushed["activities"] = pushActivities(token)
            pushed["leaves"] = pushLeaves(token)
            pushed["bills"] = pushBills(token)

            pulled["officers"] = pullOfficers(token)
            pulled["trips"] = pullTrips(token, session.userId)
            pulled["visits"] = pullVisits(token)
            pulled["travel_legs"] = pullTravelLegs(token)
            pulled["activities"] = pullActivities(token)
            pulled["leaves"] = pullLeaves(token)
            pulled["bills"] = pullBills(token)

            syncState.recordSuccess(Instant.now().toString())
            SyncResult(pushed, pulled)
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            syncState.recordError(message)
            SyncResult(pushed, pulled, error = message)
        }
    }

    // ---- push: dirty rows out, clear the flag only once the server has them ----

    private suspend fun pushTrips(token: String): Int {
        val dao = db.tripDao()
        val dirty = dao.dirtyRows()
        if (dirty.isEmpty()) return 0
        client.upsert("trips", JsonArray(dirty.map { it.toJson() }), token)
        dao.clearDirty(dirty.map { it.id to it.updatedAt })
        return dirty.size
    }

    private suspend fun pushVisits(token: String): Int {
        val dao = db.visitDao()
        val dirty = dao.dirtyRows()
        if (dirty.isEmpty()) return 0
        client.upsert("visits", JsonArray(dirty.map { it.toJson() }), token)
        dao.clearDirty(dirty.map { it.id to it.updatedAt })
        return dirty.size
    }

    private suspend fun pushTravelLegs(token: String): Int {
        val dao = db.travelLegDao()
        val dirty = dao.dirtyRows()
        if (dirty.isEmpty()) return 0
        client.upsert("travel_legs", JsonArray(dirty.map { it.toJson() }), token)
        dao.clearDirty(dirty.map { it.id to it.updatedAt })
        return dirty.size
    }

    private suspend fun pushActivities(token: String): Int {
        val dao = db.activityDao()
        val dirty = dao.dirtyRows()
        if (dirty.isEmpty()) return 0
        client.upsert("activities", JsonArray(dirty.map { it.toJson() }), token)
        dao.clearDirty(dirty.map { it.id to it.updatedAt })
        return dirty.size
    }

    private suspend fun pushLeaves(token: String): Int {
        val dao = db.leaveDao()
        val dirty = dao.dirtyRows()
        if (dirty.isEmpty()) return 0
        client.upsert("leaves", JsonArray(dirty.map { it.toJson() }), token)
        dao.clearDirty(dirty.map { it.id to it.updatedAt })
        return dirty.size
    }

    private suspend fun pushBills(token: String): Int {
        val dao = db.billDao()
        val dirty = dao.dirtyRows()
        if (dirty.isEmpty()) return 0
        client.upsert("bills", JsonArray(dirty.map { it.toJson() }), token)
        dao.clearDirty(dirty.map { it.id to it.updatedAt })
        return dirty.size
    }

    // ---- pull: rows newer than our watermark in, skipping ones we have unpushed edits for ----

    // fetches every row past `watermark`, not just the first 1000 -- pages with limit/offset
    // until a short page proves the table's drained. same watermark for every page in the pass,
    // so ties (bulk-imported rows sharing one updated_at) all land together within this pass;
    // the only residual gap is a row updated to exactly that timestamp *after* the pass already
    // read it, which server-side moddatetime makes negligible.
    private suspend fun pullPages(table: String, watermark: String, token: String): List<JsonElement> {
        val all = mutableListOf<JsonElement>()
        var offset = 0
        while (true) {
            val page = client.select(
                table,
                mapOf(
                    "updated_at" to "gt.$watermark",
                    "order" to "updated_at.asc",
                    "limit" to "$PAGE_SIZE",
                    "offset" to "$offset",
                ),
                token,
            )
            all.addAll(page)
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return all
    }

    private suspend fun pullOfficers(token: String): Int {
        val dao = db.officerDao()
        val watermark = syncState.watermark("officers")
        val rows = pullPages("officers", watermark, token)
        if (rows.isEmpty()) return 0
        val remoteRows = rows.map { it.jsonObject.toOfficer() }
        // officers = pull-only, no local edits ever compete with the server here.
        remoteRows.forEach { dao.upsert(it) }
        syncState.setWatermark("officers", advanceWatermark(watermark, remoteRows.map { it.updatedAt }))
        return remoteRows.size
    }

    private suspend fun pullTrips(token: String, myUserId: String): Int {
        val dao = db.tripDao()
        val officerDao = db.officerDao()
        val watermark = syncState.watermark("trips")
        val rows = pullPages("trips", watermark, token)
        if (rows.isEmpty()) return 0
        val remoteRows = rows.map { it.jsonObject.toTrip() }
        var applied = 0
        for (remote in remoteRows) {
            val local = dao.byId(remote.id)
            val isNewTrip = local == null
            if (shouldApplyRemote(localDirty = local?.dirty ?: false)) {
                dao.upsert(remote)
                applied++
            }
            // a colleague informed *me* of a brand-new trip of theirs -- post a local notice.
            // skip on the very first pull (watermark still at epoch): every existing trip looks
            // "brand-new" then, which would fire a notification per historical inform at once.
            if (watermark != EPOCH_WATERMARK &&
                isNewTrip && remote.informedOfficerId == myUserId && remote.officerId != myUserId
            ) {
                val officerName = officerDao.byId(remote.officerId)?.name ?: "An officer"
                notifyInformed(remote.id, officerName)
            }
        }
        syncState.setWatermark("trips", advanceWatermark(watermark, remoteRows.map { it.updatedAt }))
        return applied
    }

    private suspend fun pullVisits(token: String): Int {
        val dao = db.visitDao()
        val watermark = syncState.watermark("visits")
        val rows = pullPages("visits", watermark, token)
        if (rows.isEmpty()) return 0
        val remoteRows = rows.map { it.jsonObject.toVisit() }
        var applied = 0
        for (remote in remoteRows) {
            val local = dao.byId(remote.id)
            if (shouldApplyRemote(localDirty = local?.dirty ?: false)) {
                dao.upsert(remote)
                applied++
            }
        }
        syncState.setWatermark("visits", advanceWatermark(watermark, remoteRows.map { it.updatedAt }))
        return applied
    }

    private suspend fun pullTravelLegs(token: String): Int {
        val dao = db.travelLegDao()
        val watermark = syncState.watermark("travel_legs")
        val rows = pullPages("travel_legs", watermark, token)
        if (rows.isEmpty()) return 0
        val remoteRows = rows.map { it.jsonObject.toTravelLeg() }
        var applied = 0
        for (remote in remoteRows) {
            val local = dao.byId(remote.id)
            if (shouldApplyRemote(localDirty = local?.dirty ?: false)) {
                dao.upsert(remote)
                applied++
            }
        }
        syncState.setWatermark("travel_legs", advanceWatermark(watermark, remoteRows.map { it.updatedAt }))
        return applied
    }

    private suspend fun pullActivities(token: String): Int {
        val dao = db.activityDao()
        val watermark = syncState.watermark("activities")
        val rows = pullPages("activities", watermark, token)
        if (rows.isEmpty()) return 0
        val remoteRows = rows.map { it.jsonObject.toActivity() }
        var applied = 0
        for (remote in remoteRows) {
            val local = dao.byId(remote.id)
            if (shouldApplyRemote(localDirty = local?.dirty ?: false)) {
                dao.upsert(remote)
                applied++
            }
        }
        syncState.setWatermark("activities", advanceWatermark(watermark, remoteRows.map { it.updatedAt }))
        return applied
    }

    private suspend fun pullLeaves(token: String): Int {
        val dao = db.leaveDao()
        val watermark = syncState.watermark("leaves")
        val rows = pullPages("leaves", watermark, token)
        if (rows.isEmpty()) return 0
        val remoteRows = rows.map { it.jsonObject.toLeave() }
        var applied = 0
        for (remote in remoteRows) {
            val local = dao.byId(remote.id)
            if (shouldApplyRemote(localDirty = local?.dirty ?: false)) {
                dao.upsert(remote)
                applied++
            }
        }
        syncState.setWatermark("leaves", advanceWatermark(watermark, remoteRows.map { it.updatedAt }))
        return applied
    }

    // RLS scopes bills to officer_id = auth.uid() (or admin), so a plain watermark pull already
    // only ever sees our own rows -- same pattern as visits/travel_legs, no extra filter needed.
    private suspend fun pullBills(token: String): Int {
        val dao = db.billDao()
        val watermark = syncState.watermark("bills")
        val rows = pullPages("bills", watermark, token)
        if (rows.isEmpty()) return 0
        val remoteRows = rows.map { it.jsonObject.toBill() }
        var applied = 0
        for (remote in remoteRows) {
            val local = dao.byId(remote.id)
            if (shouldApplyRemote(localDirty = local?.dirty ?: false)) {
                dao.upsert(remote)
                applied++
            }
        }
        syncState.setWatermark("bills", advanceWatermark(watermark, remoteRows.map { it.updatedAt }))
        return applied
    }

    // local notice: "<name> started a tour — informed you". POST_NOTIFICATIONS is declared
    // in the manifest but the runtime permission prompt is wired up by the shell-work task --
    // until it's granted, notify() throws SecurityException on API 33+; swallow it, a missed
    // notification must never fail the whole sync.
    private fun notifyInformed(tripId: String, officerName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Trip informs",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // ponytail: system icon, swap for app icon when we have one
            .setContentTitle("Tour started")
            .setContentText("$officerName started a tour — informed you")
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(tripId.hashCode(), notification)
        } catch (e: SecurityException) {
            // permission not granted yet -- sync itself already succeeded, just skip the notice.
        }
    }
}
