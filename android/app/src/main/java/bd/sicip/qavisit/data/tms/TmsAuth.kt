// TMS session: token from POST auth/login, credentials in the vault. exposes state for the UI
// (Failed drives the top banner). retry policy for silent re-login:
//   network/timeout/5xx -> retry after 5 s then 30 s; status:"error" -> fail at once, and stop
//   resending the known-bad password until the officer logs in again; offline -> no state change.
package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

sealed interface TmsAuthState {
    data object LoggedOut : TmsAuthState

    // expiresAt = epoch seconds; 0 until a login succeeded in this process (creds stored, token not yet fetched)
    data class LoggedIn(val displayName: String, val expiresAt: Long) : TmsAuthState
    data class Failed(val message: String) : TmsAuthState
}

sealed interface TmsLoginResult {
    data object Success : TmsLoginResult
    data class Failure(val message: String) : TmsLoginResult
}

private const val MIN_TOKEN_LIFE_SECONDS = 600L
private val RETRY_DELAYS_MS = listOf(5_000L, 30_000L)
private val DISPLAY_NAME_KEYS = listOf("username", "full_name", "name", "user_name")

class TmsAuth(
    private val vault: TmsCredentialStore,
    private val transport: TmsTransport = TmsHttp(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Mutex()
    private var token: String? = null
    private var expiresAt = 0L
    private var displayName = ""
    private var credentialsRejected = false

    private val _state = MutableStateFlow<TmsAuthState>(TmsAuthState.LoggedOut)
    val state: StateFlow<TmsAuthState> = _state

    // call once at app start: shows LoggedIn when credentials are stored (token fetched lazily).
    suspend fun restore() = lock.withLock {
        val credentials = vault.load() ?: return@withLock
        displayName = credentials.username
        _state.value = TmsAuthState.LoggedIn(displayName, 0)
    }

    // verifies against TMS first; the vault is written only on success.
    suspend fun login(username: String, password: String): TmsLoginResult = lock.withLock {
        try {
            attemptLogin(TmsCredentials(username, password))
            vault.save(TmsCredentials(username, password))
            credentialsRejected = false
            TmsLoginResult.Success
        } catch (e: TmsException) {
            TmsLoginResult.Failure(e.message ?: "Login failed")
        }
    }

    suspend fun logout() = lock.withLock {
        vault.clear()
        token = null
        expiresAt = 0
        credentialsRejected = false
        _state.value = TmsAuthState.LoggedOut
    }

    // token with >= 10 min left, else silent re-login. throws TmsException when none is possible.
    suspend fun bearer(): String = lock.withLock {
        val current = token
        if (current != null && expiresAt - nowSeconds() >= MIN_TOKEN_LIFE_SECONDS) return@withLock current
        silentLogin()
    }

    // after a 401: drop the token and log in again once.
    suspend fun reloginAfterUnauthorized(): String = lock.withLock {
        token = null
        silentLogin()
    }

    private suspend fun silentLogin(): String {
        if (credentialsRejected) throw TmsLoggedOutException("TMS credentials were rejected")
        val credentials = vault.load() ?: throw TmsLoggedOutException()
        var retriesDone = 0
        while (true) {
            try {
                return attemptLogin(credentials)
            } catch (e: TmsMessageException) {
                credentialsRejected = true
                _state.value = TmsAuthState.Failed(e.message ?: "Login failed")
                throw e
            } catch (e: TmsTransientException) {
                if (e.offline) throw e // wait for the network, no banner
                if (retriesDone >= RETRY_DELAYS_MS.size) {
                    _state.value = TmsAuthState.Failed(e.message ?: "no connection")
                    throw e
                }
                sleep(RETRY_DELAYS_MS[retriesDone++])
            }
        }
    }

    // one POST auth/login; updates token + state on success, throws a TmsException otherwise.
    private suspend fun attemptLogin(credentials: TmsCredentials): String {
        val response = transport.postForm(
            "auth/login",
            mapOf("username" to credentials.username, "password" to credentials.password, "user_ip" to ""),
        )
        val data = try {
            unwrapTmsResponse(response.code, response.body, "auth/login")
        } catch (e: TmsUnauthorizedException) {
            throw TmsMessageException("UserName or Password Not Match!")
        } as? JsonObject ?: throw shapeError("auth/login", "data: expected object")
        val newToken = (data["token"] as? JsonPrimitive)?.contentOrNull
            ?: throw shapeError("auth/login", "token: missing")
        token = newToken
        expiresAt = jwtExpiry(newToken) ?: (nowSeconds() + 12 * 3600) // unreadable exp: assume half the 24 h life
        displayName = displayNameOf(data, credentials.username)
        _state.value = TmsAuthState.LoggedIn(displayName, expiresAt)
        return newToken
    }

    // live TMS puts the person's name at user_info.employee.name; older shapes/keys are the fallback.
    private fun displayNameOf(data: JsonObject, fallback: String): String {
        val info = data["user_info"] as? JsonObject ?: return fallback
        val employee = info["employee"] as? JsonObject
        val employeeName = (employee?.get("name") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return employeeName ?: DISPLAY_NAME_KEYS.firstNotNullOfOrNull { key ->
            (info[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        } ?: fallback
    }
}
