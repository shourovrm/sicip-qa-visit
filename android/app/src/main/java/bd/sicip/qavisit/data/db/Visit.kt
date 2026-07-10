// mirrors public.visits. one institute engagement; category scored in domain/Scoring.kt.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "visits")
data class Visit(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "officer_id") val officerId: String,
    @ColumnInfo(name = "trip_id") val tripId: String? = null,
    val institute: String,
    val association: String,
    val district: String,
    @ColumnInfo(name = "dhaka_metro") val dhakaMetro: Boolean? = null,
    val purpose: String,
    @ColumnInfo(name = "ref_no") val refNo: String? = null,
    @ColumnInfo(name = "ref_date") val refDate: String? = null,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    val category: String = "N/A",
    @ColumnInfo(name = "category_override") val categoryOverride: Boolean = false,
    @ColumnInfo(name = "is_additional") val isAdditional: Boolean = false,
    val status: String = "scheduled",
    val remarks: String? = null,
    val source: String = "app",
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Dao
interface VisitDao {
    @Upsert
    suspend fun upsert(row: Visit)

    @Query("UPDATE visits SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: String)

    @Query("SELECT * FROM visits WHERE officer_id = :officerId AND deleted = 0 ORDER BY start_date DESC")
    suspend fun byOfficer(officerId: String): List<Visit>

    @Query("SELECT * FROM visits WHERE deleted = 0 ORDER BY start_date DESC")
    suspend fun all(): List<Visit>

    // trip detail: every visit attached to one trip (primary + any ad-hoc adds).
    @Query("SELECT * FROM visits WHERE trip_id = :tripId AND deleted = 0 ORDER BY start_date")
    suspend fun byTrip(tripId: String): List<Visit>

    // Home screen: reactive so a background sync write recomposes the summary cards in place.
    @Query("SELECT * FROM visits WHERE officer_id = :officerId AND deleted = 0 ORDER BY start_date DESC")
    fun byOfficerFlow(officerId: String): Flow<List<Visit>>

    @Query("SELECT * FROM visits WHERE deleted = 0 ORDER BY start_date DESC")
    fun allFlow(): Flow<List<Visit>>

    @Query("SELECT * FROM visits WHERE trip_id = :tripId AND deleted = 0 ORDER BY start_date")
    fun byTripFlow(tripId: String): Flow<List<Visit>>

    // sync needs this to check "already had this row?" and "is it locally dirty?" before overwriting.
    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun byId(id: String): Visit?

    @Query("SELECT * FROM visits WHERE dirty = 1")
    suspend fun dirtyRows(): List<Visit>

    // conditional on the pushed snapshot's updated_at -- if the row was edited again while the
    // upsert was in flight, its updated_at has already moved on and this clear is a no-op, so
    // the fresh edit stays dirty instead of being clobbered by the next pull.
    @Query("UPDATE visits SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: String, updatedAt: String)

    @Transaction
    suspend fun clearDirty(snapshots: List<Pair<String, String>>) {
        snapshots.forEach { (id, updatedAt) -> clearDirtyIfUnchanged(id, updatedAt) }
    }

    @Query("SELECT MAX(updated_at) FROM visits")
    suspend fun maxUpdatedAt(): String?

    // office orders (ref_no) are shared across officers, not per-officer -- so this looks
    // across every officer's visits, not just the caller's.
    @Query("SELECT DISTINCT ref_no FROM visits WHERE ref_no IS NOT NULL AND ref_no != '' ORDER BY ref_no")
    suspend fun distinctRefs(): List<String>

    // most recent ref_date recorded against a given ref_no, for prefilling a new visit that
    // reuses an existing office order.
    @Query("SELECT ref_date FROM visits WHERE ref_no = :refNo AND ref_date IS NOT NULL ORDER BY updated_at DESC LIMIT 1")
    suspend fun refDateFor(refNo: String): String?

    // institutes are shared/synced across officers -- global autosuggest, same reasoning as
    // distinctRefs above.
    @Query("SELECT DISTINCT institute FROM visits WHERE deleted = 0 AND institute != '' ORDER BY institute")
    suspend fun distinctInstitutes(): List<String>
}
