// per-device pick of which free model backs POST /api/rewrite (settings/../data/remote/
// RewriteClient.kt). null = "use whatever the server calls default" -- picking the row that IS
// the server default (see ui/profile/ProfileScreen.kt's RewriteModelCard) clears back to null
// rather than pinning that key, so it keeps tracking the server if the default ever changes.
// Shares theme_prefs (settings/ThemePref.kt's themeDataStore) instead of opening a second small
// DataStore file for one more setting. Also caches the last successful GET /api/models response
// body verbatim so Profile can still show the officer's saved pick's label while offline.
package bd.sicip.qavisit.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val MODEL_KEY = stringPreferencesKey("rewrite_model_key")
private val MODELS_CACHE_KEY = stringPreferencesKey("rewrite_models_cache")

class RewriteModelPrefs(private val context: Context) {
    val selectedKey: Flow<String?> = context.themeDataStore.data.map { prefs -> prefs[MODEL_KEY] }

    suspend fun setSelectedKey(key: String?) {
        context.themeDataStore.edit { prefs ->
            if (key == null) prefs.remove(MODEL_KEY) else prefs[MODEL_KEY] = key
        }
    }

    val cachedModelsJson: Flow<String?> = context.themeDataStore.data.map { prefs -> prefs[MODELS_CACHE_KEY] }

    suspend fun cacheModelsJson(json: String) {
        context.themeDataStore.edit { it[MODELS_CACHE_KEY] = json }
    }
}
