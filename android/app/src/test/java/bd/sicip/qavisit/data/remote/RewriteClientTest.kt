// pure-function tests only -- no network, matches the plan's "unit-test JSON building/parsing +
// status mapping" (see RewriteClient.kt's buildRewriteRequestBody/parseRewriteResponseText/
// rewriteErrorMessage, all internal/pure).
package bd.sicip.qavisit.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RewriteClientTest {
    @Test fun request_body_has_fixed_text_and_label_keys() {
        val body = buildRewriteRequestBody("Attendance was low today.", "remarks")
        assertEquals("""{"text":"Attendance was low today.","label":"remarks"}""", body)
    }

    @Test fun request_body_escapes_quotes_and_newlines() {
        val body = buildRewriteRequestBody("line one\nsaid \"ok\"", "feedback")
        assertEquals("""{"text":"line one\nsaid \"ok\"","label":"feedback"}""", body)
    }

    @Test fun request_body_includes_model_key_when_given() {
        val body = buildRewriteRequestBody("Attendance was low today.", "remarks", "fast")
        assertEquals("""{"text":"Attendance was low today.","label":"remarks","model":"fast"}""", body)
    }

    @Test fun request_body_omits_model_key_when_null() {
        val body = buildRewriteRequestBody("Attendance was low today.", "remarks", null)
        assertEquals("""{"text":"Attendance was low today.","label":"remarks"}""", body)
    }

    @Test fun response_text_parsed_out_of_200_body() {
        assertEquals("Attendance was low today.", parseRewriteResponseText("""{"text":"Attendance was low today."}"""))
    }

    @Test fun models_response_parsed_out_of_200_body() {
        val parsed = parseModelsResponse(
            """{"default":"fast","models":[{"key":"fast","id":"@cf/meta/llama-3.3-70b-instruct-fp8-fast","label":"Fast","note":"Quick, good default"},{"key":"careful","id":"@cf/other/model","label":"Careful"}]}""",
        )
        assertEquals("fast", parsed.default)
        assertEquals(2, parsed.models.size)
        assertEquals(RewriteModel("fast", "@cf/meta/llama-3.3-70b-instruct-fp8-fast", "Fast", "Quick, good default"), parsed.models[0])
        assertEquals(RewriteModel("careful", "@cf/other/model", "Careful", null), parsed.models[1])
    }

    @Test fun unauthorized_maps_to_session_expired() {
        assertEquals("Session expired — sign in again", rewriteErrorMessage(401))
    }

    @Test fun too_long_maps_to_char_limit_message() {
        assertEquals(REWRITE_TOO_LONG_MESSAGE, rewriteErrorMessage(413))
    }

    @Test fun quota_maps_to_daily_limit_message() {
        assertEquals("Daily limit reached — try again tomorrow", rewriteErrorMessage(429))
    }

    @Test fun ai_failure_and_any_other_status_map_to_generic_message() {
        assertEquals("Could not improve wording — try again", rewriteErrorMessage(502))
        assertEquals("Could not improve wording — try again", rewriteErrorMessage(400))
        assertEquals("Could not improve wording — try again", rewriteErrorMessage(500))
    }
}
