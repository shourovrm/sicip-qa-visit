// mirrors public.trips. one journey, owns travel legs, active -> finished.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "officer_id") val officerId: String,
    val status: String = "active",
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "finished_at") val finishedAt: String? = null,
    @ColumnInfo(name = "informed_officer_id") val informedOfficerId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val submitted: Boolean = false,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(row: Trip)

    @Query("UPDATE trips SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: String)

    @Query("SELECT * FROM trips WHERE officer_id = :officerId AND status = 'active' AND deleted = 0 LIMIT 1")
    suspend fun activeTrip(officerId: String): Trip?

    // Home screen: reactive so a background sync write recomposes the hero card in place.
    @Query("SELECT * FROM trips WHERE officer_id = :officerId AND status = 'active' AND deleted = 0 LIMIT 1")
    fun activeTripFlow(officerId: String): Flow<Trip?>

    // Team screen: every officer's active trip in one query, reactive so a sync pull recomposes the status list.
    @Query("SELECT * FROM trips WHERE status = 'active' AND deleted = 0")
    fun activeTripsFlow(): Flow<List<Trip>>

    // TA/DA bill picker: own finished, not-yet-submitted trips to batch onto a bill, newest first.
    @Query("SELECT * FROM trips WHERE officer_id = :officerId AND status = 'finished' AND submitted = 0 AND deleted = 0 ORDER BY started_at DESC")
    suspend fun finishedUnsubmittedByOfficer(officerId: String): List<Trip>

    // trips already batched onto a submitted bill -- history view.
    @Query("SELECT * FROM trips WHERE officer_id = :officerId AND status = 'finished' AND submitted = 1 AND deleted = 0 ORDER BY started_at DESC")
    suspend fun finishedSubmittedByOfficer(officerId: String): List<Trip>

    @Query("UPDATE trips SET submitted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun markSubmitted(id: String, now: String)

    // sync needs this to check "already had this row?" and "is it locally dirty?" before overwriting.
    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun byId(id: String): Trip?

    @Query("SELECT * FROM trips WHERE dirty = 1")
    suspend fun dirtyRows(): List<Trip>

    // conditional on the pushed snapshot's updated_at -- if the row was edited again while the
    // upsert was in flight, its updated_at has already moved on and this clear is a no-op, so
    // the fresh edit stays dirty instead of being clobbered by the next pull.
    @Query("UPDATE trips SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: String, updatedAt: String)

    @Transaction
    suspend fun clearDirty(snapshots: List<Pair<String, String>>) {
        snapshots.forEach { (id, updatedAt) -> clearDirtyIfUnchanged(id, updatedAt) }
    }

    @Query("SELECT MAX(updated_at) FROM trips")
    suspend fun maxUpdatedAt(): String?

    // hard-delete reconciliation: candidates for retraction (never an unpushed local edit).
    @Query("SELECT id FROM trips WHERE dirty = 0")
    suspend fun nonDirtyIds(): List<String>

    @Query("DELETE FROM trips WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
