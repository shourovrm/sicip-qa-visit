// per-table sync bookkeeping: how far we've pulled, plus diagnostics for a future status UI.
package bd.sicip.qavisit.data.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncDataStore by preferencesDataStore(name = "sync_prefs")

// default watermark for a table never pulled before. lexicographically smaller than any
// real timestamptz string, so "updated_at=gt.<this>" matches every row on the first pull.
const val EPOCH_WATERMARK = "1970-01-01T00:00:00.000Z"

private val LAST_SYNC_AT = stringPreferencesKey("last_sync_at")
private val LAST_ERROR = stringPreferencesKey("last_error")
private fun watermarkKey(table: String) = stringPreferencesKey("watermark_$table")

class SyncStateStore(private val context: Context) {
    suspend fun watermark(table: String): String =
        context.syncDataStore.data.first()[watermarkKey(table)] ?: EPOCH_WATERMARK

    suspend fun setWatermark(table: String, value: String) {
        context.syncDataStore.edit { it[watermarkKey(table)] = value }
    }

    val lastSyncAt: Flow<String?> = context.syncDataStore.data.map { it[LAST_SYNC_AT] }
    val lastError: Flow<String?> = context.syncDataStore.data.map { it[LAST_ERROR] }

    suspend fun recordSuccess(nowIso: String) {
        context.syncDataStore.edit {
            it[LAST_SYNC_AT] = nowIso
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordError(message: String) {
        context.syncDataStore.edit { it[LAST_ERROR] = message }
    }
}
