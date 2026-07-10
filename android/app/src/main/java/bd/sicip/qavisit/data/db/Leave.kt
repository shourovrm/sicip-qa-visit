// mirrors public.leaves. feeds Team status page (leaves overlapping today).
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "leaves")
data class Leave(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "officer_id") val officerId: String,
    val type: String,
    val reason: String? = null,
    @ColumnInfo(name = "informed_officer_id") val informedOfficerId: String? = null,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    val status: String = "scheduled",
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Dao
interface LeaveDao {
    @Upsert
    suspend fun upsert(row: Leave)

    @Query("UPDATE leaves SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: String)

    // covers "today" and isn't cancelled -> drives Team status page
    @Query(
        "SELECT * FROM leaves WHERE deleted = 0 AND status != 'cancelled' " +
            "AND start_date <= :today AND end_date >= :today"
    )
    suspend fun overlapping(today: String): List<Leave>

    @Query("SELECT * FROM leaves WHERE officer_id = :officerId AND deleted = 0 ORDER BY start_date DESC")
    suspend fun byOfficer(officerId: String): List<Leave>

    // sync needs this to check "already had this row?" and "is it locally dirty?" before overwriting.
    @Query("SELECT * FROM leaves WHERE id = :id")
    suspend fun byId(id: String): Leave?

    @Query("SELECT * FROM leaves WHERE dirty = 1")
    suspend fun dirtyRows(): List<Leave>

    @Query("UPDATE leaves SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT MAX(updated_at) FROM leaves")
    suspend fun maxUpdatedAt(): String?
}
