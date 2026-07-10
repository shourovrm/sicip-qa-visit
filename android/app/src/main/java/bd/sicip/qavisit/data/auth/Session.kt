// logged-in session: tokens + expiry, persisted in datastore so login survives restarts.
package bd.sicip.qavisit.data.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import bd.sicip.qavisit.data.remote.SupabaseClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long, // epoch seconds
    val userId: String,
    val email: String,
)

// treat token as expired 60s before its real expiry, so an in-flight request doesn't
// get cut off mid-call.
private const val SKEW_SECONDS = 60L

fun Session.valid(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
    nowEpochSeconds < expiresAt - SKEW_SECONDS

private val Context.sessionDataStore by preferencesDataStore(name = "session_prefs")
private val ACCESS_TOKEN = stringPreferencesKey("access_token")
private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
private val EXPIRES_AT = longPreferencesKey("expires_at")
private val USER_ID = stringPreferencesKey("user_id")
private val EMAIL = stringPreferencesKey("email")

private fun Preferences.toSession(): Session? {
    val access = this[ACCESS_TOKEN] ?: return null
    val refresh = this[REFRESH_TOKEN] ?: return null
    val expires = this[EXPIRES_AT] ?: return null
    val userId = this[USER_ID] ?: return null
    val email = this[EMAIL] ?: return null
    return Session(access, refresh, expires, userId, email)
}

class SessionStore(private val context: Context) {
    val session: Flow<Session?> = context.sessionDataStore.data.map { it.toSession() }

    suspend fun current(): Session? = session.first()

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { p ->
            p[ACCESS_TOKEN] = session.accessToken
            p[REFRESH_TOKEN] = session.refreshToken
            p[EXPIRES_AT] = session.expiresAt
            p[USER_ID] = session.userId
            p[EMAIL] = session.email
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    // returns a session with a still-fresh access token, refreshing it first if it's
    // near expiry. if the refresh itself fails (refresh token revoked/expired), the
    // stored session is cleared so the app falls back to the login screen.
    suspend fun ensureFresh(client: SupabaseClient): Session? {
        val current = current() ?: return null
        if (current.valid()) return current
        return try {
            val r = client.refresh(current.refreshToken)
            val fresh = Session(r.accessToken, r.refreshToken, r.expiresAt, r.userId, current.email)
            save(fresh)
            fresh
        } catch (e: Exception) {
            clear()
            null
        }
    }
}
