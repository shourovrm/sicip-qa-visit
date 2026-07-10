// mirrors public.visits. one institute engagement; category scored in domain/Scoring.kt.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

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

    @Query("SELECT * FROM visits WHERE dirty = 1")
    suspend fun dirtyRows(): List<Visit>

    @Query("UPDATE visits SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT MAX(updated_at) FROM visits")
    suspend fun maxUpdatedAt(): String?
}
