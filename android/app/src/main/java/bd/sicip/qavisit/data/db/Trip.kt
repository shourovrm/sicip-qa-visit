// mirrors public.trips. one journey, owns travel legs, active -> finished.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "officer_id") val officerId: String,
    val status: String = "active",
    @ColumnInfo(name = "started_at") val startedAt: String,
    @ColumnInfo(name = "finished_at") val finishedAt: String? = null,
    @ColumnInfo(name = "informed_officer_id") val informedOfficerId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
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

    @Query("SELECT * FROM trips WHERE dirty = 1")
    suspend fun dirtyRows(): List<Trip>

    @Query("UPDATE trips SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT MAX(updated_at) FROM trips")
    suspend fun maxUpdatedAt(): String?
}
