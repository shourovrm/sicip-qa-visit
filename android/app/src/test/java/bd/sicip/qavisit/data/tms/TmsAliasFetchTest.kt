// alias master list is optional: transient or API-change failures fall back to an empty list.
package bd.sicip.qavisit.data.tms

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class TmsAliasFetchTest {
    private val token = Base64.getUrlEncoder().withoutPadding().let {
        "${it.encodeToString("{}".toByteArray())}.${it.encodeToString("""{"exp":4102444800}""".toByteArray())}.sig"
    }

    private class Vault : TmsCredentialStore {
        override suspend fun save(credentials: TmsCredentials) {}
        override suspend fun load() = TmsCredentials("u", "p")
        override suspend fun clear() {}
    }

    private fun apiAnswering(getReply: () -> TmsRawResponse): TmsApi {
        val transport = object : TmsTransport {
            override suspend fun get(path: String, bearerToken: String) = getReply()
            override suspend fun postForm(path: String, fields: Map<String, String>) =
                TmsRawResponse(200, """{"status":"success","data":{"token":"$token"}}""")
        }
        return TmsApi(TmsAuth(Vault(), transport, { 1_000L }, { }), transport)
    }

    @Test
    fun `alias list is returned when TMS answers`() = runBlocking {
        val api = apiAnswering { TmsRawResponse(200, """{"status":"success","data":[{"id":1,"name":"X"}]}""") }
        assertEquals(1, fetchAliasList(api).size)
    }

    @Test
    fun `transient failure falls back to empty`() = runBlocking {
        val api = apiAnswering { TmsRawResponse(503, "") }
        assertTrue(fetchAliasList(api).isEmpty())
    }

    @Test
    fun `api change falls back to empty`() = runBlocking {
        val api = apiAnswering { TmsRawResponse(404, "") }
        assertTrue(fetchAliasList(api).isEmpty())
    }
}
