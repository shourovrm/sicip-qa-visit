// mirrors public.activities. timestamped note during a trip or visit.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
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

    @Query("SELECT * FROM activities WHERE dirty = 1")
    suspend fun dirtyRows(): List<Activity>

    @Query("UPDATE activities SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT MAX(updated_at) FROM activities")
    suspend fun maxUpdatedAt(): String?
}
