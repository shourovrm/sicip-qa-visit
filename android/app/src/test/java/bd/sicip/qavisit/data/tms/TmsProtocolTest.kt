// JWT exp parsing, response classification, and the silent re-login retry policy.
package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

private fun jwt(payload: String): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return "${encoder.encodeToString("{}".toByteArray())}.${encoder.encodeToString(payload.toByteArray())}.sig"
}

class TmsProtocolTest {
    @Test
    fun `jwt exp is read from the payload`() {
        assertEquals(1780000000L, jwtExpiry(jwt("""{"iat":1779913600,"exp":1780000000}""")))
    }

    @Test
    fun `bad jwt gives null`() {
        assertNull(jwtExpiry("not-a-token"))
        assertNull(jwtExpiry(jwt("""{"iat":1}""")))
    }

    private fun classify(code: Int, body: String): Throwable? =
        runCatching { unwrapTmsResponse(code, body, "batch/list") }.exceptionOrNull()

    @Test
    fun `success returns data`() {
        assertEquals("[]", unwrapTmsResponse(200, """{"status":"success","data":[]}""", "x").toString())
    }

    @Test
    fun `404 and 405 are api changes`() {
        assertEquals("http_404", (classify(404, "") as TmsApiChangeException).kind)
        assertEquals("http_405", (classify(405, "") as TmsApiChangeException).kind)
    }

    @Test
    fun `non json is an api change`() {
        val error = classify(200, "<html>") as TmsApiChangeException
        assertEquals("not_json", error.kind)
        assertEquals("batch/list", error.endpoint)
    }

    @Test
    fun `missing status or data is a shape change`() {
        assertEquals("shape", (classify(200, """{"data":[]}""") as TmsApiChangeException).kind)
        assertEquals("shape", (classify(200, """{"status":"success"}""") as TmsApiChangeException).kind)
    }

    @Test
    fun `5xx is transient and 401 is unauthorized`() {
        assertTrue(classify(503, "") is TmsTransientException)
        assertTrue(classify(401, "") is TmsUnauthorizedException)
    }

    @Test
    fun `status error carries TMS message`() {
        val error = classify(200, """{"status":"error","message":"UserName or Password Not Match!"}""")
        assertEquals("UserName or Password Not Match!", (error as TmsMessageException).message)
    }

    // ---- auth retry policy ----

    private class FakeVault(var credentials: TmsCredentials? = TmsCredentials("u", "p")) : TmsCredentialStore {
        override suspend fun save(credentials: TmsCredentials) { this.credentials = credentials }
        override suspend fun load() = credentials
        override suspend fun clear() { credentials = null }
    }

    private class ScriptedTransport(private val replies: MutableList<() -> TmsRawResponse>) : TmsTransport {
        var calls = 0
        override suspend fun get(path: String, bearerToken: String) = error("unused")
        override suspend fun postForm(path: String, fields: Map<String, String>): TmsRawResponse {
            calls++
            return replies.removeFirst()()
        }
    }

    private val goodToken = jwt("""{"exp":4102444800}""")
    private val success = TmsRawResponse(200, """{"status":"success","data":{"token":"$goodToken","user_info":{"name":"Rina"}}}""")
    private val serverDown = TmsRawResponse(503, "")
    private val wrongPassword = TmsRawResponse(200, """{"status":"error","message":"UserName or Password Not Match!"}""")

    @Test
    fun `transient failures retry after 5 s then 30 s`() = runBlocking {
        val sleeps = mutableListOf<Long>()
        val transport = ScriptedTransport(mutableListOf({ serverDown }, { serverDown }, { success }))
        val auth = TmsAuth(FakeVault(), transport, { 1_000L }, { sleeps += it })
        assertEquals(goodToken, auth.bearer())
        assertEquals(listOf(5_000L, 30_000L), sleeps)
        assertEquals(TmsAuthState.LoggedIn("Rina", 4102444800L), auth.state.value)
    }

    @Test
    fun `display name is user_info employee name, else username`() = runBlocking {
        val live = TmsRawResponse(
            200,
            """{"status":"success","data":{"token":"$goodToken","user_info":{"id":1,"email":"a@b.c","username":"rina01","employee":{"name":"Rina Akter"}}}}""",
        )
        val auth = TmsAuth(FakeVault(), ScriptedTransport(mutableListOf({ live })), { 1_000L }, { })
        auth.bearer()
        assertEquals(TmsAuthState.LoggedIn("Rina Akter", 4102444800L), auth.state.value)

        val noEmployee = TmsRawResponse(
            200,
            """{"status":"success","data":{"token":"$goodToken","user_info":{"id":1,"username":"rina01"}}}""",
        )
        val second = TmsAuth(FakeVault(), ScriptedTransport(mutableListOf({ noEmployee })), { 1_000L }, { })
        second.bearer()
        assertEquals(TmsAuthState.LoggedIn("rina01", 4102444800L), second.state.value)
    }

    @Test
    fun `three transient failures end in Failed`() {
        val transport = ScriptedTransport(mutableListOf({ serverDown }, { serverDown }, { serverDown }))
        val auth = TmsAuth(FakeVault(), transport, { 1_000L }, { })
        assertThrows(TmsTransientException::class.java) { runBlocking { auth.bearer() } }
        assertTrue(auth.state.value is TmsAuthState.Failed)
        assertEquals(3, transport.calls)
    }

    @Test
    fun `wrong password fails at once and is not resent`() {
        val transport = ScriptedTransport(mutableListOf({ wrongPassword }))
        val auth = TmsAuth(FakeVault(), transport, { 1_000L }, { })
        assertThrows(TmsMessageException::class.java) { runBlocking { auth.bearer() } }
        assertThrows(TmsLoggedOutException::class.java) { runBlocking { auth.bearer() } }
        assertEquals(1, transport.calls)
        assertEquals(TmsAuthState.Failed("UserName or Password Not Match!", credentialsStored = true), auth.state.value)
    }

    // card bug: a failed background re-login must not drop the officer back to the login form
    @Test
    fun `failed silent relogin keeps stored credentials signed in`() {
        val transport = ScriptedTransport(mutableListOf({ serverDown }, { serverDown }, { serverDown }))
        val auth = TmsAuth(FakeVault(), transport, { 1_000L }, { })
        assertThrows(TmsTransientException::class.java) { runBlocking { auth.bearer() } }
        assertEquals(true, (auth.state.value as TmsAuthState.Failed).credentialsStored)
    }

    @Test
    fun `restore does not overwrite a live session`() = runBlocking {
        val auth = TmsAuth(FakeVault(), ScriptedTransport(mutableListOf({ success })), { 1_000L }, { })
        assertEquals(TmsLoginResult.Success, auth.login("u", "p"))
        auth.restore()
        assertEquals(TmsAuthState.LoggedIn("Rina", 4102444800L), auth.state.value)
    }

    @Test
    fun `restore shows stored credentials as signed in`() = runBlocking {
        val auth = TmsAuth(FakeVault(), ScriptedTransport(mutableListOf()), { 1_000L }, { })
        auth.restore()
        assertEquals(TmsAuthState.LoggedIn("u", 0), auth.state.value)
    }

    @Test
    fun `retry resends rejected credentials once`() = runBlocking {
        val transport = ScriptedTransport(mutableListOf({ wrongPassword }, { success }))
        val auth = TmsAuth(FakeVault(), transport, { 1_000L }, { })
        runCatching { auth.bearer() }
        auth.retry()
        assertEquals(TmsAuthState.LoggedIn("Rina", 4102444800L), auth.state.value)
        assertEquals(2, transport.calls)
    }

    @Test
    fun `offline shows no banner and does not retry`() {
        val transport = ScriptedTransport(mutableListOf({ throw TmsTransientException("no route", offline = true) }))
        val auth = TmsAuth(FakeVault(), transport, { 1_000L }, { })
        assertThrows(TmsTransientException::class.java) { runBlocking { auth.bearer() } }
        assertEquals(TmsAuthState.LoggedOut, auth.state.value)
        assertEquals(1, transport.calls)
    }

    @Test
    fun `login saves to the vault only on success`() = runBlocking {
        val vault = FakeVault(credentials = null)
        val bad = TmsAuth(vault, ScriptedTransport(mutableListOf({ wrongPassword })), { 1_000L }, { })
        assertEquals(TmsLoginResult.Failure("UserName or Password Not Match!"), bad.login("u", "x"))
        assertNull(vault.credentials)
        val good = TmsAuth(vault, ScriptedTransport(mutableListOf({ success })), { 1_000L }, { })
        assertEquals(TmsLoginResult.Success, good.login("u", "p"))
        assertEquals(TmsCredentials("u", "p"), vault.credentials)
    }

    @Test
    fun `token with under 10 minutes left is replaced`() = runBlocking {
        val nearExpiry = jwt("""{"exp":1500}""")
        val fresh = TmsRawResponse(200, """{"status":"success","data":{"token":"$nearExpiry"}}""")
        val transport = ScriptedTransport(mutableListOf({ fresh }, { success }))
        val auth = TmsAuth(FakeVault(), transport, { 1_000L }, { })
        assertEquals(nearExpiry, auth.bearer()) // 500 s left -> next call logs in again
        assertEquals(goodToken, auth.bearer())
        assertEquals(2, transport.calls)
    }
}
