// session expiry math only (valid()); the datastore-backed store itself needs an
// android context and is compile-checked, not unit-tested (no robolectric here — yagni).
package bd.sicip.qavisit.data.auth

import bd.sicip.qavisit.data.remote.SupabaseException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private fun session(expiresAt: Long) = Session(
        accessToken = "a",
        refreshToken = "r",
        expiresAt = expiresAt,
        userId = "u1",
        email = "x@sicip.bd",
    )

    @Test fun valid_well_before_expiry() {
        assertTrue(session(expiresAt = 1_000).valid(nowEpochSeconds = 100))
    }

    @Test fun invalid_past_expiry() {
        assertFalse(session(expiresAt = 1_000).valid(nowEpochSeconds = 1_001))
    }

    @Test fun invalid_inside_60s_skew_window() {
        // 30s left on the clock still counts as expired: refresh should kick in early
        assertFalse(session(expiresAt = 1_000).valid(nowEpochSeconds = 970))
    }

    @Test fun valid_just_outside_skew_window() {
        assertTrue(session(expiresAt = 1_000).valid(nowEpochSeconds = 939))
    }

    // offline officers must not be logged out by a dead network
    @Test fun network_failure_keeps_session() {
        assertFalse(isRefreshRejected(java.net.SocketTimeoutException("timeout")))
        assertFalse(isRefreshRejected(java.io.IOException("no route")))
    }

    @Test fun server_error_and_rate_limit_keep_session() {
        assertFalse(isRefreshRejected(SupabaseException(503, "")))
        assertFalse(isRefreshRejected(SupabaseException(429, "")))
    }

    @Test fun refused_refresh_token_clears_session() {
        val body = """{"code":400,"error_code":"refresh_token_already_used","msg":"Invalid Refresh Token: Already Used"}"""
        assertTrue(isRefreshRejected(SupabaseException(400, body)))
        assertTrue(isRefreshRejected(SupabaseException(403, "")))
    }
}
