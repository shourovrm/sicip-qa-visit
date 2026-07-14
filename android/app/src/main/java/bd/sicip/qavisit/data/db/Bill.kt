// mirrors public.bills. one row per submitted TA/DA bill: a frozen JSON snapshot (see
// domain/BillSnapshot.kt) of the trips/legs/totals as they were the moment the officer hit
// "Submit bill" -- once written, this row (and the trips it references) is never edited again.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "officer_id") val officerId: String,
    @ColumnInfo(name = "bill_date") val billDate: String,
    val data: String, // frozen snapshot JSON (domain/BillSnapshot.kt), real jsonb on the server
    val net: Double,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Dao
interface BillDao {
    @Upsert
    suspend fun upsert(row: Bill)

    // Previous bills tab: own submitted bills, newest first.
    @Query("SELECT * FROM bills WHERE officer_id = :officerId AND deleted = 0 ORDER BY bill_date DESC")
    suspend fun byOfficer(officerId: String): List<Bill>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun byId(id: String): Bill?

    @Query("UPDATE bills SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: String)

    // sync needs this to check "already had this row?" and "is it locally dirty?" before overwriting.
    @Query("SELECT * FROM bills WHERE dirty = 1")
    suspend fun dirtyRows(): List<Bill>

    // conditional on the pushed snapshot's updated_at -- if the row was edited again while the
    // upsert was in flight, its updated_at has already moved on and this clear is a no-op, so
    // the fresh edit stays dirty instead of being clobbered by the next pull.
    @Query("UPDATE bills SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: String, updatedAt: String)

    @Transaction
    suspend fun clearDirty(snapshots: List<Pair<String, String>>) {
        snapshots.forEach { (id, updatedAt) -> clearDirtyIfUnchanged(id, updatedAt) }
    }

    @Query("SELECT MAX(updated_at) FROM bills")
    suspend fun maxUpdatedAt(): String?

    // hard-delete reconciliation: candidates for retraction (never an unpushed local edit).
    @Query("SELECT id FROM bills WHERE dirty = 0")
    suspend fun nonDirtyIds(): List<String>

    @Query("DELETE FROM bills WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
