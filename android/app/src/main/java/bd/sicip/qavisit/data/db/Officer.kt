// mirrors public.officers. no deleted col upstream (admin uses `active`), so no soft-delete here.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "officers")
data class Officer(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val role: String,
    val active: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val dirty: Boolean = false,
)

@Dao
interface OfficerDao {
    @Upsert
    suspend fun upsert(row: Officer)

    @Query("SELECT * FROM officers ORDER BY name")
    suspend fun all(): List<Officer>

    // sync uses this to look up the informing officer's name for the "informed you" notice.
    @Query("SELECT * FROM officers WHERE id = :id")
    suspend fun byId(id: String): Officer?

    @Query("SELECT * FROM officers WHERE dirty = 1")
    suspend fun dirtyRows(): List<Officer>

    @Query("UPDATE officers SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("SELECT MAX(updated_at) FROM officers")
    suspend fun maxUpdatedAt(): String?
}
