// card bug root cause: the login form's coroutine scope dies the moment the state flips to LoggedIn
// (the form leaves the screen), which used to cancel vault.save() so nothing was stored.
package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class TmsLoginCancelTest {
    // like DataStore.edit: a suspension point before the write lands
    private class SlowVault : TmsCredentialStore {
        var credentials: TmsCredentials? = null
        override suspend fun save(credentials: TmsCredentials) {
            yield()
            this.credentials = credentials
        }
        override suspend fun load() = credentials
        override suspend fun clear() { credentials = null }
    }

    private val success = TmsRawResponse(
        200,
        """{"status":"success","data":{"token":"a.${java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":4102444800}""".toByteArray())}.c"}}""",
    )

    private class OneReply(private val reply: TmsRawResponse) : TmsTransport {
        override suspend fun get(path: String, bearerToken: String) = error("unused")
        override suspend fun postForm(path: String, fields: Map<String, String>) = reply
    }

    @Test
    fun `login still saves credentials when the form scope is cancelled on LoggedIn`() = runBlocking {
        val vault = SlowVault()
        val auth = TmsAuth(vault, OneReply(success), { 1_000L }, { })
        val login = launch { auth.login("u", "p") }
        // the card swaps the form for the signed-in view as soon as the state changes
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
            auth.state.collect { if (it is TmsAuthState.LoggedIn) login.cancelAndJoin() }
        }
        login.join()
        watcher.cancelAndJoin()
        assertEquals(TmsCredentials("u", "p"), vault.credentials)
    }
}
