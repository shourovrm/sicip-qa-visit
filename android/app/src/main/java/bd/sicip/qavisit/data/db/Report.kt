// mirrors public.reports (supabase/migrations/010_reports.sql). one surprise/monitoring
// report attached to one visit; `data` is the report's answers as a JSON string (see
// domain/report/ReportData.kt), shaped by shared/report-templates/<type>-v<version>.json.
//
// API for agent A2 (UI): byOfficerFlow(officerId) for the Reports tab list (own, non-deleted,
// newest first); byVisitFlow(visitId) to find/open the existing report for a visit -- one row
// per (visit, type) among non-deleted rows is a UI-enforced rule, not a DB constraint, so check
// this flow before creating a new report; byIdFlow(id) for the report hub/section screens;
// upsert(row) after every edit (autosave, see domain/report/ReportData.kt setters); softDelete
// for draft delete with confirm. Submitted reports are read-only -- UI must not call upsert on
// a submitted row except the one write that sets status="submitted" + submitted_at.
package bd.sicip.qavisit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "officer_id") val officerId: String,
    @ColumnInfo(name = "visit_id") val visitId: String,
    val type: String, // "surprise" | "monitoring" (monitoring template not shipped yet)
    @ColumnInfo(name = "template_version") val templateVersion: Int,
    val data: String, // report data jsonb, stored locally as TEXT -- see domain/report/ReportData.kt
    val status: String = "draft", // "draft" | "submitted"
    @ColumnInfo(name = "submitted_at") val submittedAt: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val dirty: Boolean = false,
)

@Dao
interface ReportDao {
    @Upsert
    suspend fun upsert(row: Report)

    // Reports tab list: own non-deleted reports, newest first.
    @Query("SELECT * FROM reports WHERE officer_id = :officerId AND deleted = 0 ORDER BY created_at DESC")
    fun byOfficerFlow(officerId: String): Flow<List<Report>>

    // "one report per (visit, type) among non-deleted rows" check: UI filters this by type
    // client-side before deciding new-vs-open (a visit could carry both a surprise and a
    // future monitoring report, each independently unique).
    @Query("SELECT * FROM reports WHERE visit_id = :visitId AND deleted = 0 ORDER BY created_at DESC")
    fun byVisitFlow(visitId: String): Flow<List<Report>>

    // report hub / section screens: reactive so autosave from one screen updates another
    // (e.g. progress bar) without an explicit refresh call.
    @Query("SELECT * FROM reports WHERE id = :id")
    fun byIdFlow(id: String): Flow<Report?>

    // sync needs this to check "already had this row?" and "is it locally dirty?" before overwriting.
    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun byId(id: String): Report?

    @Query("UPDATE reports SET deleted = 1, dirty = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: String)

    @Query("SELECT * FROM reports WHERE dirty = 1")
    suspend fun dirtyRows(): List<Report>

    // conditional on the pushed snapshot's updated_at -- if the row was edited again while the
    // upsert was in flight, its updated_at has already moved on and this clear is a no-op, so
    // the fresh edit stays dirty instead of being clobbered by the next pull.
    @Query("UPDATE reports SET dirty = 0 WHERE id = :id AND updated_at = :updatedAt")
    suspend fun clearDirtyIfUnchanged(id: String, updatedAt: String)

    @Transaction
    suspend fun clearDirty(snapshots: List<Pair<String, String>>) {
        snapshots.forEach { (id, updatedAt) -> clearDirtyIfUnchanged(id, updatedAt) }
    }

    @Query("SELECT MAX(updated_at) FROM reports")
    suspend fun maxUpdatedAt(): String?

    // hard-delete reconciliation: candidates for retraction (never an unpushed local edit).
    @Query("SELECT id FROM reports WHERE dirty = 0")
    suspend fun nonDirtyIds(): List<String>

    @Query("DELETE FROM reports WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
