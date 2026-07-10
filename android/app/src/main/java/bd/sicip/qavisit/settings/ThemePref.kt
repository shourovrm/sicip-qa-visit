// persists the user's light/dark/system pick. one key, one file.
package bd.sicip.qavisit.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bd.sicip.qavisit.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")
private val MODE_KEY = stringPreferencesKey("theme_mode")

class ThemePrefs(private val context: Context) {
    val mode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        prefs[MODE_KEY]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun set(mode: ThemeMode) {
        context.themeDataStore.edit { it[MODE_KEY] = mode.name }
    }
}
