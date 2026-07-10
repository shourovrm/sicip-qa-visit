// mirrors public.travel_legs. one itinerary row per bill; night_stay/food_day editable defaults.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "travel_legs")
data class TravelLeg(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "dep_date") val depDate: String,
    @ColumnInfo(name = "dep_time") val depTime: String,
    @ColumnInfo(name = "dep_place") val depPlace: String,
    @ColumnInfo(name = "arr_date") val arrDate: String,
    @ColumnInfo(name = "arr_time") val arrTime: String,
    @ColumnInfo(name = "arr_place") val arrPlace: String,
    val mode: String,
    @ColumnInfo(name = "class") val travelClass: String? = null, // "class" is a kotlin keyword
    val fare: Double = 0.0,
    @ColumnInfo(name = "night_stay") val nightStay: Int = 0,
    @ColumnInfo(name = "food_day") val foodDay: Double = 0.0,
    val remarks: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Dao
interface TravelLegDao {
    @Upsert
    suspend fun upsert(row: TravelLeg)

    @Query("UPDATE travel_legs SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: String)

    @Query("SELECT * FROM travel_legs WHERE trip_id = :tripId AND deleted = 0 ORDER BY dep_date, dep_time")
    suspend fun byTrip(tripId: String): List<TravelLeg>

    @Query("SELECT * FROM travel_legs WHERE dirty = 1")
    suspend fun dirtyRows(): List<TravelLeg>

    @Query("UPDATE travel_legs SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT MAX(updated_at) FROM travel_legs")
    suspend fun maxUpdatedAt(): String?
}
