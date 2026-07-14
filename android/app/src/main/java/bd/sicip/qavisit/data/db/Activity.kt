// mirrors public.activities. timestamped note during a trip or visit.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String? = null,
    @ColumnInfo(name = "visit_id") val visitId: String? = null,
    val at: String,
    val note: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Dao
interface ActivityDao {
    @Upsert
    suspend fun upsert(row: Activity)

    @Query("UPDATE activities SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: String)

    @Query("SELECT * FROM activities WHERE trip_id = :tripId AND deleted = 0 ORDER BY at")
    suspend fun byTrip(tripId: String): List<Activity>

    @Query("SELECT * FROM activities WHERE visit_id = :visitId AND deleted = 0 ORDER BY at")
    suspend fun byVisit(visitId: String): List<Activity>

    // sync needs this to check "already had this row?" and "is it locally dirty?" before overwriting.
    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun byId(id: String): Activity?

    @Query("SELECT * FROM activities WHERE dirty = 1")
    suspend fun dirtyRows(): List<Activity>

    // conditional on the pushed snapshot's updated_at -- if the row was edited again while the
    // upsert was in flight, its updated_at has already moved on and this clear is a no-op, so
    // the fresh edit stays dirty instead of being clobbered by the next pull.
    @Query("UPDATE activities SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: String, updatedAt: String)

    @Transaction
    suspend fun clearDirty(snapshots: List<Pair<String, String>>) {
        snapshots.forEach { (id, updatedAt) -> clearDirtyIfUnchanged(id, updatedAt) }
    }

    @Query("SELECT MAX(updated_at) FROM activities")
    suspend fun maxUpdatedAt(): String?

    // hard-delete reconciliation: candidates for retraction (never an unpushed local edit).
    @Query("SELECT id FROM activities WHERE dirty = 0")
    suspend fun nonDirtyIds(): List<String>

    @Query("DELETE FROM activities WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
