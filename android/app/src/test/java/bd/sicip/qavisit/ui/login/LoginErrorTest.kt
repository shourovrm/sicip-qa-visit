// friendly error text mapping; pure function, no compose involved.
package bd.sicip.qavisit.ui.login

import bd.sicip.qavisit.data.remote.SupabaseException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class LoginErrorTest {
    @Test fun bad_credentials_on_http_400() {
        assertEquals("Wrong email or password", loginErrorMessage(SupabaseException(400, "invalid_grant")))
    }

    @Test fun network_failure_on_io_exception() {
        assertEquals("No connection — try again", loginErrorMessage(IOException("timeout")))
    }

    @Test fun other_http_errors_get_generic_message() {
        assertEquals("Something went wrong — try again", loginErrorMessage(SupabaseException(500, "boom")))
    }
}
